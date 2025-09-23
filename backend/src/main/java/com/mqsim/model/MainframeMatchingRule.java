package com.mqsim.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for mainframe message field extraction and matching in MQ Simulator
 */
public class MainframeMatchingRule {
    
    @JsonProperty("fieldName")
    private String fieldName;           // Descriptive name for the field
    
    @JsonProperty("startIndex")
    private int startIndex;             // Start position in the message (0-based)
    
    @JsonProperty("endIndex")
    private int endIndex;               // End position in the message (exclusive)
    
    @JsonProperty("expectedValue")
    private String expectedValue;       // Value to compare against
    
    @JsonProperty("ignoreCase")
    private boolean ignoreCase = false; // Case-insensitive comparison
    
    @JsonProperty("trimValue")
    private boolean trimValue = true;   // Trim extracted value
    
    @JsonProperty("description")
    private String description;         // Human-readable description
    
    @JsonProperty("matchType")
    private MatchType matchType = MatchType.EXACT; // Type of matching to perform
    
    @JsonProperty("regexPattern")
    private String regexPattern;        // Regex pattern for pattern matching
    
    public enum MatchType {
        EXACT,      // Exact string matching (default)
        REGEX       // Regular expression pattern matching
    }
    
    // Constructors
    public MainframeMatchingRule() {}
    
    public MainframeMatchingRule(String fieldName, int startIndex, int endIndex, String expectedValue) {
        this.fieldName = fieldName;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.expectedValue = expectedValue;
    }
    
    public MainframeMatchingRule(String fieldName, int startIndex, int endIndex, String expectedValue, 
                                boolean ignoreCase, boolean trimValue) {
        this(fieldName, startIndex, endIndex, expectedValue);
        this.ignoreCase = ignoreCase;
        this.trimValue = trimValue;
    }
    
    public MainframeMatchingRule(String fieldName, int startIndex, int endIndex, MatchType matchType, 
                                String pattern, boolean ignoreCase, boolean trimValue) {
        this.fieldName = fieldName;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.matchType = matchType;
        this.ignoreCase = ignoreCase;
        this.trimValue = trimValue;
        
        if (matchType == MatchType.EXACT) {
            this.expectedValue = pattern;
        } else {
            this.regexPattern = pattern;
        }
    }
    
    // Getters and Setters
    public String getFieldName() {
        return fieldName;
    }
    
    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }
    
    public int getStartIndex() {
        return startIndex;
    }
    
    public void setStartIndex(int startIndex) {
        this.startIndex = startIndex;
    }
    
    public int getEndIndex() {
        return endIndex;
    }
    
    public void setEndIndex(int endIndex) {
        this.endIndex = endIndex;
    }
    
    public String getExpectedValue() {
        return expectedValue;
    }
    
    public void setExpectedValue(String expectedValue) {
        this.expectedValue = expectedValue;
    }
    
    public boolean isIgnoreCase() {
        return ignoreCase;
    }
    
    public void setIgnoreCase(boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
    }
    
    public boolean isTrimValue() {
        return trimValue;
    }
    
    public void setTrimValue(boolean trimValue) {
        this.trimValue = trimValue;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public MatchType getMatchType() {
        return matchType;
    }
    
    public void setMatchType(MatchType matchType) {
        this.matchType = matchType;
    }
    
    public String getRegexPattern() {
        return regexPattern;
    }
    
    public void setRegexPattern(String regexPattern) {
        this.regexPattern = regexPattern;
    }
    
    // Utility methods
    public int getFieldLength() {
        return endIndex - startIndex;
    }
    
    public boolean isValidIndices() {
        return startIndex >= 0 && endIndex > startIndex;
    }
    
    @Override
    public String toString() {
        return String.format("MainframeMatchingRule{fieldName='%s', startIndex=%d, endIndex=%d, " +
                "matchType=%s, expectedValue='%s', regexPattern='%s', ignoreCase=%s, trimValue=%s, description='%s'}", 
                fieldName, startIndex, endIndex, matchType, expectedValue, regexPattern, ignoreCase, trimValue, description);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        MainframeMatchingRule that = (MainframeMatchingRule) o;
        
        if (startIndex != that.startIndex) return false;
        if (endIndex != that.endIndex) return false;
        if (ignoreCase != that.ignoreCase) return false;
        if (trimValue != that.trimValue) return false;
        if (fieldName != null ? !fieldName.equals(that.fieldName) : that.fieldName != null) return false;
        if (expectedValue != null ? !expectedValue.equals(that.expectedValue) : that.expectedValue != null) return false;
        if (matchType != that.matchType) return false;
        if (regexPattern != null ? !regexPattern.equals(that.regexPattern) : that.regexPattern != null) return false;
        return description != null ? description.equals(that.description) : that.description == null;
    }
    
    @Override
    public int hashCode() {
        int result = fieldName != null ? fieldName.hashCode() : 0;
        result = 31 * result + startIndex;
        result = 31 * result + endIndex;
        result = 31 * result + (expectedValue != null ? expectedValue.hashCode() : 0);
        result = 31 * result + (matchType != null ? matchType.hashCode() : 0);
        result = 31 * result + (regexPattern != null ? regexPattern.hashCode() : 0);
        result = 31 * result + (ignoreCase ? 1 : 0);
        result = 31 * result + (trimValue ? 1 : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        return result;
    }
}