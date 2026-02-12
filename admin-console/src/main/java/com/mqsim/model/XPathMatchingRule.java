package com.mqsim.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for XPath message field extraction and matching in MQ Simulator
 */
public class XPathMatchingRule {
    
    @JsonProperty("fieldName")
    private String fieldName;           // Descriptive name for the field
    
    @JsonProperty("xpath")
    private String xpath;               // XPath expression to extract value
    
    @JsonProperty("expectedValue")
    private String expectedValue;       // Value to compare against
    
    @JsonProperty("ignoreCase")
    private boolean ignoreCase = false; // Case-insensitive comparison
    
    @JsonProperty("trimValue")
    private boolean trimValue = true;   // Trim extracted value
    
    @JsonProperty("description")
    private String description;         // Human-readable description
    
    // Constructors
    public XPathMatchingRule() {}
    
    public XPathMatchingRule(String fieldName, String xpath, String expectedValue) {
        this.fieldName = fieldName;
        this.xpath = xpath;
        this.expectedValue = expectedValue;
    }
    
    public XPathMatchingRule(String fieldName, String xpath, String expectedValue, 
                            boolean ignoreCase, boolean trimValue) {
        this(fieldName, xpath, expectedValue);
        this.ignoreCase = ignoreCase;
        this.trimValue = trimValue;
    }
    
    // Getters and Setters
    public String getFieldName() {
        return fieldName;
    }
    
    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }
    
    public String getXpath() {
        return xpath;
    }
    
    public void setXpath(String xpath) {
        this.xpath = xpath;
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
    
    // Utility methods
    public boolean isValidXPath() {
        return xpath != null && !xpath.trim().isEmpty();
    }
    
    @Override
    public String toString() {
        return String.format("XPathMatchingRule{fieldName='%s', xpath='%s', " +
                "expectedValue='%s', ignoreCase=%s, trimValue=%s, description='%s'}", 
                fieldName, xpath, expectedValue, ignoreCase, trimValue, description);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        XPathMatchingRule that = (XPathMatchingRule) o;
        
        if (ignoreCase != that.ignoreCase) return false;
        if (trimValue != that.trimValue) return false;
        if (fieldName != null ? !fieldName.equals(that.fieldName) : that.fieldName != null) return false;
        if (xpath != null ? !xpath.equals(that.xpath) : that.xpath != null) return false;
        if (expectedValue != null ? !expectedValue.equals(that.expectedValue) : that.expectedValue != null) return false;
        return description != null ? description.equals(that.description) : that.description == null;
    }
    
    @Override
    public int hashCode() {
        int result = fieldName != null ? fieldName.hashCode() : 0;
        result = 31 * result + (xpath != null ? xpath.hashCode() : 0);
        result = 31 * result + (expectedValue != null ? expectedValue.hashCode() : 0);
        result = 31 * result + (ignoreCase ? 1 : 0);
        result = 31 * result + (trimValue ? 1 : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        return result;
    }
}