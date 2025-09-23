package com.mqsim.service;

import com.mqsim.model.MainframeMatchingRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Service for processing mainframe messages as byte streams in MQ Simulator
 * Handles conversion to strings and field extraction with substring operations
 */
@Service
public class MainframeMessageProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(MainframeMessageProcessor.class);
    
    /**
     * Convert byte array to string using specified charset
     */
    public String convertBytesToString(byte[] messageBytes, String charsetName) {
        try {
            Charset charset = charsetName != null ? Charset.forName(charsetName) : StandardCharsets.UTF_8;
            String result = new String(messageBytes, charset);
            logger.debug("Converted {} bytes to string using charset {}: {}", 
                messageBytes.length, charset.name(), truncateForLog(result));
            return result;
        } catch (Exception e) {
            logger.error("Failed to convert bytes to string with charset {}: {}", charsetName, e.getMessage());
            // Fallback to UTF-8
            return new String(messageBytes, StandardCharsets.UTF_8);
        }
    }
    
    /**
     * Convert byte array to string using UTF-8 (default)
     */
    public String convertBytesToString(byte[] messageBytes) {
        return convertBytesToString(messageBytes, null);
    }
    
    /**
     * Extract field value from message using start and end indices
     */
    public String extractField(String message, int startIndex, int endIndex) {
        if (message == null) {
            logger.warn("Cannot extract field from null message");
            return null;
        }
        
        if (startIndex < 0 || endIndex < 0 || startIndex >= message.length()) {
            logger.warn("Invalid indices for field extraction: start={}, end={}, messageLength={}", 
                startIndex, endIndex, message.length());
            return null;
        }
        
        // Ensure endIndex doesn't exceed message length
        int actualEndIndex = Math.min(endIndex, message.length());
        
        if (startIndex >= actualEndIndex) {
            logger.warn("Start index {} is greater than or equal to end index {}", startIndex, actualEndIndex);
            return null;
        }
        
        try {
            String extracted = message.substring(startIndex, actualEndIndex);
            logger.debug("Extracted field from indices {}-{}: '{}'", startIndex, actualEndIndex, extracted);
            return extracted;
        } catch (Exception e) {
            logger.error("Failed to extract field from indices {}-{}: {}", startIndex, actualEndIndex, e.getMessage());
            return null;
        }
    }
    
    /**
     * Compare extracted field value with expected value (exact match)
     */
    public boolean compareFieldValue(String extractedValue, String expectedValue, boolean ignoreCase) {
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
        
        logger.debug("Field comparison result: '{}' {} '{}' = {}", 
            extractedValue, ignoreCase ? "equalsIgnoreCase" : "equals", expectedValue, matches);
        
        return matches;
    }
    
    /**
     * Compare extracted field value with regex pattern
     */
    public boolean compareFieldValueRegex(String extractedValue, String regexPattern, boolean ignoreCase) {
        if (extractedValue == null) {
            logger.debug("Regex comparison failed: extracted value is null");
            return false;
        }
        
        if (regexPattern == null || regexPattern.trim().isEmpty()) {
            logger.debug("Regex comparison failed: regex pattern is null or empty");
            return false;
        }
        
        try {
            Pattern pattern;
            if (ignoreCase) {
                pattern = Pattern.compile(regexPattern, Pattern.CASE_INSENSITIVE);
            } else {
                pattern = Pattern.compile(regexPattern);
            }
            
            boolean matches = pattern.matcher(extractedValue).matches();
            
            logger.debug("Regex comparison result: '{}' {} pattern '{}' = {}", 
                extractedValue, ignoreCase ? "matches (ignoreCase)" : "matches", regexPattern, matches);
            
            return matches;
            
        } catch (PatternSyntaxException e) {
            logger.error("Invalid regex pattern '{}': {}", regexPattern, e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Error during regex matching: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Process mainframe message and check if it matches the given rules
     */
    public boolean processMessage(byte[] messageBytes, List<MainframeMatchingRule> rules, String charset) {
        if (messageBytes == null || messageBytes.length == 0) {
            logger.warn("Cannot process null or empty message bytes");
            return false;
        }
        
        if (rules == null || rules.isEmpty()) {
            logger.debug("No matching rules provided, returning true");
            return true;
        }
        
        // Convert bytes to string
        String message = convertBytesToString(messageBytes, charset);
        
        // Check all rules (AND logic)
        for (MainframeMatchingRule rule : rules) {
            if (!checkRule(message, rule)) {
                logger.debug("Rule failed: {}", rule);
                return false;
            }
        }
        
        logger.debug("All {} rules matched successfully", rules.size());
        return true;
    }
    
    /**
     * Check if a single rule matches the message
     */
    private boolean checkRule(String message, MainframeMatchingRule rule) {
        try {
            // Extract field value
            String extractedValue = extractField(message, rule.getStartIndex(), rule.getEndIndex());
            
            if (extractedValue == null) {
                return false;
            }
            
            // Trim if configured
            if (rule.isTrimValue()) {
                extractedValue = extractedValue.trim();
            }
            
            // Choose matching type
            if (rule.getMatchType() == MainframeMatchingRule.MatchType.REGEX) {
                // Use regex pattern matching
                return compareFieldValueRegex(extractedValue, rule.getRegexPattern(), rule.isIgnoreCase());
            } else {
                // Use exact matching (default)
                return compareFieldValue(extractedValue, rule.getExpectedValue(), rule.isIgnoreCase());
            }
            
        } catch (Exception e) {
            logger.error("Error checking rule {}: {}", rule, e.getMessage());
            return false;
        }
    }
    
    /**
     * Utility method to truncate strings for logging
     */
    private String truncateForLog(String value) {
        if (value == null) return "null";
        if (value.length() <= 100) return value;
        return value.substring(0, 100) + "... (truncated)";
    }
    
    /**
     * Get message info for debugging
     */
    public MessageInfo getMessageInfo(byte[] messageBytes, String charset) {
        if (messageBytes == null) {
            return new MessageInfo(0, null, null);
        }
        
        String message = convertBytesToString(messageBytes, charset);
        return new MessageInfo(messageBytes.length, message, charset);
    }
    
    /**
     * Helper class for message debugging information
     */
    public static class MessageInfo {
        private final int byteLength;
        private final String stringContent;
        private final String charset;
        
        public MessageInfo(int byteLength, String stringContent, String charset) {
            this.byteLength = byteLength;
            this.stringContent = stringContent;
            this.charset = charset;
        }
        
        public int getByteLength() { return byteLength; }
        public String getStringContent() { return stringContent; }
        public String getCharset() { return charset; }
        
        @Override
        public String toString() {
            return String.format("MessageInfo{byteLength=%d, charset='%s', content='%s'}", 
                byteLength, charset, stringContent != null && stringContent.length() > 50 ? 
                stringContent.substring(0, 50) + "..." : stringContent);
        }
    }
}