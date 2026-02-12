package com.mqsim.service;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.mqsim.model.JsonPathMatchingRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Service for processing JSON messages using JSONPath expressions in MQ Simulator
 */
@Service
public class JsonPathMessageProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(JsonPathMessageProcessor.class);
    
    /**
     * Process JSON message and check if it matches the given JSONPath rules
     */
    public boolean processMessage(byte[] messageBytes, List<JsonPathMatchingRule> rules) {
        if (messageBytes == null || messageBytes.length == 0) {
            logger.warn("Cannot process null or empty message bytes");
            return false;
        }
        
        if (rules == null || rules.isEmpty()) {
            logger.debug("No JSONPath rules provided, returning true");
            return true;
        }
        
        try {
            // Convert bytes to string
            String jsonMessage = new String(messageBytes, StandardCharsets.UTF_8);
            
            // Check all rules (AND logic)
            for (JsonPathMatchingRule rule : rules) {
                if (!checkJsonPathRule(jsonMessage, rule)) {
                    logger.debug("JSONPath rule failed: {}", rule);
                    return false;
                }
            }
            
            logger.debug("All {} JSONPath rules matched successfully", rules.size());
            return true;
            
        } catch (Exception e) {
            logger.error("Error processing JSON message with JSONPath rules", e);
            return false;
        }
    }
    
    /**
     * Check if a single JSONPath rule matches the message
     */
    private boolean checkJsonPathRule(String jsonMessage, JsonPathMatchingRule rule) {
        try {
            if (!rule.isValidJsonPath()) {
                logger.warn("Invalid JSONPath expression in rule: {}", rule);
                return false;
            }
            
            // Extract value using JSONPath
            Object value = JsonPath.read(jsonMessage, rule.getJsonPath());
            
            if (value == null) {
                logger.debug("JSONPath expression returned null: {}", rule.getJsonPath());
                return false;
            }
            
            // Convert value to string for comparison
            String extractedValue = value.toString();
            
            // Trim if configured
            if (rule.isTrimValue()) {
                extractedValue = extractedValue.trim();
            }
            
            // Compare with expected value
            return compareFieldValue(extractedValue, rule.getExpectedValue(), rule.isIgnoreCase());
            
        } catch (PathNotFoundException e) {
            logger.debug("JSONPath not found: {}", rule.getJsonPath());
            return false;
        } catch (Exception e) {
            logger.error("Error checking JSONPath rule {}: {}", rule, e.getMessage());
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
        
        logger.debug("JSONPath field comparison result: '{}' {} '{}' = {}", 
            extractedValue, ignoreCase ? "equalsIgnoreCase" : "equals", expectedValue, matches);
        
        return matches;
    }
    
    /**
     * Extract value using JSONPath expression for debugging
     */
    public Object extractValue(byte[] messageBytes, String jsonPathExpression) {
        try {
            String jsonMessage = new String(messageBytes, StandardCharsets.UTF_8);
            return JsonPath.read(jsonMessage, jsonPathExpression);
        } catch (Exception e) {
            logger.error("Error extracting value with JSONPath {}: {}", jsonPathExpression, e.getMessage());
            return null;
        }
    }
}