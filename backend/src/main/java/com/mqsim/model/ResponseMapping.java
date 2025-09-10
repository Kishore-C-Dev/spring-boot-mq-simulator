package com.mqsim.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.Instant;
import java.util.Map;

@Document(collection = "mappings")
@CompoundIndex(def = "{'queueName': 1, 'priority': -1}")
public class ResponseMapping {
    
    @Id
    private String id;
    
    @NotBlank(message = "Queue name is required")
    private String queueName;
    
    @NotNull(message = "Match criteria is required")
    private MatchCriteria match;
    
    @NotNull(message = "Response configuration is required")
    private ResponseConfig response;
    
    @NotNull(message = "Delay configuration is required")
    private DelayConfig delay;
    
    @NotNull(message = "Enabled status is required")
    private Boolean enabled;
    
    @Min(value = 1, message = "Priority must be between 1 and 10")
    @Max(value = 10, message = "Priority must be between 1 and 10")
    private Integer priority;
    
    @LastModifiedDate
    private Instant updatedAt;
    
    private Instant createdAt;
    
    public ResponseMapping() {
        this.createdAt = Instant.now();
        this.enabled = true;
        this.priority = 5;
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getQueueName() { return queueName; }
    public void setQueueName(String queueName) { this.queueName = queueName; }
    
    public MatchCriteria getMatch() { return match; }
    public void setMatch(MatchCriteria match) { this.match = match; }
    
    public ResponseConfig getResponse() { return response; }
    public void setResponse(ResponseConfig response) { this.response = response; }
    
    public DelayConfig getDelay() { return delay; }
    public void setDelay(DelayConfig delay) { this.delay = delay; }
    
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    // Inner classes
    public static class MatchCriteria {
        private String correlationId;
        private Map<String, String> headers;
        private String bodyRegex;
        
        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
        
        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> headers) { this.headers = headers; }
        
        public String getBodyRegex() { return bodyRegex; }
        public void setBodyRegex(String bodyRegex) { this.bodyRegex = bodyRegex; }
    }
    
    public static class ResponseConfig {
        public enum ResponseType { XML, MF }
        
        @NotNull(message = "Response type is required")
        private ResponseType type;
        
        private String xmlBody;
        private String mfBodyBase64;
        private Map<String, String> headers; // Legacy fixed headers for backward compatibility
        private Map<String, HeaderConfig> headerConfigs; // New configurable headers
        private String overrideCorrelationId;
        
        public ResponseType getType() { return type; }
        public void setType(ResponseType type) { this.type = type; }
        
        public String getXmlBody() { return xmlBody; }
        public void setXmlBody(String xmlBody) { this.xmlBody = xmlBody; }
        
        public String getMfBodyBase64() { return mfBodyBase64; }
        public void setMfBodyBase64(String mfBodyBase64) { this.mfBodyBase64 = mfBodyBase64; }
        
        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> headers) { this.headers = headers; }
        
        public Map<String, HeaderConfig> getHeaderConfigs() { return headerConfigs; }
        public void setHeaderConfigs(Map<String, HeaderConfig> headerConfigs) { this.headerConfigs = headerConfigs; }
        
        public String getOverrideCorrelationId() { return overrideCorrelationId; }
        public void setOverrideCorrelationId(String overrideCorrelationId) { this.overrideCorrelationId = overrideCorrelationId; }
    }
    
    public static class HeaderConfig {
        public enum HeaderSource { FIXED, COPY_FROM_REQUEST }
        
        @NotNull(message = "Header source is required")
        private HeaderSource source;
        
        private String fixedValue;
        private String requestHeaderName;
        
        public HeaderConfig() {}
        
        public HeaderConfig(HeaderSource source, String fixedValue, String requestHeaderName) {
            this.source = source;
            this.fixedValue = fixedValue;
            this.requestHeaderName = requestHeaderName;
        }
        
        public HeaderSource getSource() { return source; }
        public void setSource(HeaderSource source) { this.source = source; }
        
        public String getFixedValue() { return fixedValue; }
        public void setFixedValue(String fixedValue) { this.fixedValue = fixedValue; }
        
        public String getRequestHeaderName() { return requestHeaderName; }
        public void setRequestHeaderName(String requestHeaderName) { this.requestHeaderName = requestHeaderName; }
    }
    
    public static class DelayConfig {
        public enum DelayMode { FIXED, VARIABLE }
        
        @NotNull(message = "Delay mode is required")
        private DelayMode mode;
        
        private Integer fixedMs;
        private Integer variableMinMs;
        private Integer variableMaxMs;
        
        public DelayMode getMode() { return mode; }
        public void setMode(DelayMode mode) { this.mode = mode; }
        
        public Integer getFixedMs() { return fixedMs; }
        public void setFixedMs(Integer fixedMs) { this.fixedMs = fixedMs; }
        
        public Integer getVariableMinMs() { return variableMinMs; }
        public void setVariableMinMs(Integer variableMinMs) { this.variableMinMs = variableMinMs; }
        
        public Integer getVariableMaxMs() { return variableMaxMs; }
        public void setVariableMaxMs(Integer variableMaxMs) { this.variableMaxMs = variableMaxMs; }
    }
    
    @Override
    public String toString() {
        return "ResponseMapping{" +
                "id='" + id + '\'' +
                ", queueName='" + queueName + '\'' +
                ", priority=" + priority +
                ", enabled=" + enabled +
                ", updatedAt=" + updatedAt +
                '}';
    }
}