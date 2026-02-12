package com.mqsim.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration for JSONPath message field extraction and matching in MQ Simulator
 */
public class JsonPathMatchingRule {
    
    @JsonProperty("fieldName")
    private String fieldName;           // Descriptive name for the field
    
    @JsonProperty("jsonPath")
    private String jsonPath;            // JSONPath expression to extract value
    
    @JsonProperty("expectedValue")
    private String expectedValue;       // Value to compare against
    
    @JsonProperty("ignoreCase")
    private boolean ignoreCase = false; // Case-insensitive comparison
    
    @JsonProperty("trimValue")
    private boolean trimValue = true;   // Trim extracted value
    
    @JsonProperty("description")
    private String description;         // Human-readable description
    
    // Constructors
    public JsonPathMatchingRule() {}
    
    public JsonPathMatchingRule(String fieldName, String jsonPath, String expectedValue) {
        this.fieldName = fieldName;
        this.jsonPath = jsonPath;
        this.expectedValue = expectedValue;
    }
    
    public JsonPathMatchingRule(String fieldName, String jsonPath, String expectedValue, 
                               boolean ignoreCase, boolean trimValue) {
        this(fieldName, jsonPath, expectedValue);
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
    
    public String getJsonPath() {
        return jsonPath;
    }
    
    public void setJsonPath(String jsonPath) {
        this.jsonPath = jsonPath;
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
    public boolean isValidJsonPath() {
        return jsonPath != null && !jsonPath.trim().isEmpty();
    }
    
    @Override
    public String toString() {
        return String.format("JsonPathMatchingRule{fieldName='%s', jsonPath='%s', " +
                "expectedValue='%s', ignoreCase=%s, trimValue=%s, description='%s'}", 
                fieldName, jsonPath, expectedValue, ignoreCase, trimValue, description);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        JsonPathMatchingRule that = (JsonPathMatchingRule) o;
        
        if (ignoreCase != that.ignoreCase) return false;
        if (trimValue != that.trimValue) return false;
        if (fieldName != null ? !fieldName.equals(that.fieldName) : that.fieldName != null) return false;
        if (jsonPath != null ? !jsonPath.equals(that.jsonPath) : that.jsonPath != null) return false;
        if (expectedValue != null ? !expectedValue.equals(that.expectedValue) : that.expectedValue != null) return false;
        return description != null ? description.equals(that.description) : that.description == null;
    }
    
    @Override
    public int hashCode() {
        int result = fieldName != null ? fieldName.hashCode() : 0;
        result = 31 * result + (jsonPath != null ? jsonPath.hashCode() : 0);
        result = 31 * result + (expectedValue != null ? expectedValue.hashCode() : 0);
        result = 31 * result + (ignoreCase ? 1 : 0);
        result = 31 * result + (trimValue ? 1 : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        return result;
    }
}