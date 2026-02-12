package com.mqsim.service;

import com.mqsim.model.XPathMatchingRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Service for processing XML messages using XPath expressions in MQ Simulator
 */
@Service
public class XPathMessageProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(XPathMessageProcessor.class);
    
    private final DocumentBuilderFactory documentBuilderFactory;
    private final XPathFactory xPathFactory;
    
    public XPathMessageProcessor() {
        this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
        this.xPathFactory = XPathFactory.newInstance();
        
        // Configure document builder for security
        try {
            documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            documentBuilderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            documentBuilderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            documentBuilderFactory.setExpandEntityReferences(false);
        } catch (ParserConfigurationException e) {
            logger.warn("Could not configure XML security features", e);
        }
    }
    
    /**
     * Process XML message and check if it matches the given XPath rules
     */
    public boolean processMessage(byte[] messageBytes, List<XPathMatchingRule> rules) {
        if (messageBytes == null || messageBytes.length == 0) {
            logger.warn("Cannot process null or empty message bytes");
            return false;
        }
        
        if (rules == null || rules.isEmpty()) {
            logger.debug("No XPath rules provided, returning true");
            return true;
        }
        
        try {
            // Parse XML document
            Document document = parseXmlDocument(messageBytes);
            if (document == null) {
                return false;
            }
            
            // Check all rules (AND logic)
            for (XPathMatchingRule rule : rules) {
                if (!checkXPathRule(document, rule)) {
                    logger.debug("XPath rule failed: {}", rule);
                    return false;
                }
            }
            
            logger.debug("All {} XPath rules matched successfully", rules.size());
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing XML message with XPath rules", e);
            return false;
        }
    }
    
    /**
     * Parse XML document from byte array
     */
    private Document parseXmlDocument(byte[] messageBytes) {
        try {
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            ByteArrayInputStream inputStream = new ByteArrayInputStream(messageBytes);
            return builder.parse(inputStream);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            logger.error("Failed to parse XML document", e);
            return null;
        }
    }
    
    /**
     * Check if a single XPath rule matches the document
     */
    private boolean checkXPathRule(Document document, XPathMatchingRule rule) {
        try {
            if (!rule.isValidXPath()) {
                logger.warn("Invalid XPath expression in rule: {}", rule);
                return false;
            }
            
            XPath xpath = xPathFactory.newXPath();
            XPathExpression expression = xpath.compile(rule.getXpath());
            
            // Evaluate XPath and get result as string
            String extractedValue = (String) expression.evaluate(document, XPathConstants.STRING);
            
            if (extractedValue == null) {
                logger.debug("XPath expression returned null: {}", rule.getXpath());
                return false;
            }
            
            // Trim if configured
            if (rule.isTrimValue()) {
                extractedValue = extractedValue.trim();
            }
            
            // Compare with expected value
            return compareFieldValue(extractedValue, rule.getExpectedValue(), rule.isIgnoreCase());
            
        } catch (XPathExpressionException e) {
            logger.error("Error evaluating XPath expression {}: {}", rule.getXpath(), e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Error checking XPath rule {}: {}", rule, e.getMessage());
            return false;
        }
    }
    
    /**
     * Compare extracted field value with expected value
     */
    private boolean compareFieldValue(String extractedValue, String expectedValue, boolean ignoreCase) {
        if (extractedValue == null && expectedValue == null) {
            return true;
        }
        
        if (extractedValue == null || expectedValue == null) {
            logger.debug("Comparison failed: one value is null. Extracted: '{}', Expected: '{}'", 
                extractedValue, expectedValue);
            return false;
        }
        
        boolean matches;
        if (ignoreCase) {
            matches = extractedValue.equalsIgnoreCase(expectedValue);
        } else {
            matches = extractedValue.equals(expectedValue);
        }
        
        logger.debug("XPath field comparison result: '{}' {} '{}' = {}", 
            extractedValue, ignoreCase ? "equalsIgnoreCase" : "equals", expectedValue, matches);
        
        return matches;
    }
    
    /**
     * Extract value using XPath expression for debugging
     */
    public String extractValue(byte[] messageBytes, String xpathExpression) {
        try {
            Document document = parseXmlDocument(messageBytes);
            if (document == null) {
                return null;
            }
            
            XPath xpath = xPathFactory.newXPath();
            XPathExpression expression = xpath.compile(xpathExpression);
            return (String) expression.evaluate(document, XPathConstants.STRING);
            
        } catch (Exception e) {
            logger.error("Error extracting value with XPath {}: {}", xpathExpression, e.getMessage());
            return null;
        }
    }
}