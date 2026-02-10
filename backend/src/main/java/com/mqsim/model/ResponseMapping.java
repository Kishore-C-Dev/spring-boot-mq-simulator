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
import java.util.List;

@Document(collection = "mappings")
@CompoundIndex(def = "{'namespace': 1, 'queueName': 1, 'priority': -1}")
public class ResponseMapping {
    
    @Id
    private String id;
    
    @NotBlank(message = "Namespace is required")
    private String namespace;
    
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

    private boolean deleted = false;

    public ResponseMapping() {
        this.createdAt = Instant.now();
        this.enabled = true;
        this.priority = 5;
        this.deleted = false;
    }
    
    public ResponseMapping(String namespace) {
        this();
        this.namespace = namespace;
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }
    
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

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    // Inner classes
    public static class MatchCriteria {
        private String correlationId;
        private Map<String, String> headers;
        private String bodyRegex;
        
        // Content-based matching rules
        private List<XPathMatchingRule> xpathRules;      // XPath matching for XML messages
        private List<JsonPathMatchingRule> jsonPathRules; // JSONPath matching for JSON messages
        
        // Mainframe-specific matching
        private List<MainframeMatchingRule> mainframeRules;
        private String charset; // Character encoding for mainframe messages
        
        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
        
        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> headers) { this.headers = headers; }
        
        public String getBodyRegex() { return bodyRegex; }
        public void setBodyRegex(String bodyRegex) { this.bodyRegex = bodyRegex; }
        
        public List<XPathMatchingRule> getXpathRules() { return xpathRules; }
        public void setXpathRules(List<XPathMatchingRule> xpathRules) { this.xpathRules = xpathRules; }
        
        public List<JsonPathMatchingRule> getJsonPathRules() { return jsonPathRules; }
        public void setJsonPathRules(List<JsonPathMatchingRule> jsonPathRules) { this.jsonPathRules = jsonPathRules; }
        
        public List<MainframeMatchingRule> getMainframeRules() { return mainframeRules; }
        public void setMainframeRules(List<MainframeMatchingRule> mainframeRules) { this.mainframeRules = mainframeRules; }
        
        public String getCharset() { return charset; }
        public void setCharset(String charset) { this.charset = charset; }
    }
    
    public static class ResponseConfig {
        public enum ResponseType { XML, JSON, MAINFRAME }
        public enum CorrelationIdConfig { USE_REQUEST_CORRELATION_ID, USE_REQUEST_MESSAGE_ID, GENERATE_NEW, CUSTOM_VALUE }
        public enum MessageIdConfig { DONT_SET, CUSTOM_VALUE }

        @NotNull(message = "Response type is required")
        private ResponseType type;

        private String xmlBody;
        private String jsonBody;
        private Map<String, String> headers; // Legacy fixed headers for backward compatibility
        private Map<String, HeaderConfig> headerConfigs; // New configurable headers
        private String overrideCorrelationId;

        // Enhanced correlation ID management
        private CorrelationIdConfig correlationIdConfig;
        private String customCorrelationId;

        // Message ID management
        private MessageIdConfig messageIdConfig;
        private String customMessageId;

        // MQ-specific headers
        private Map<String, String> mqHeaders;
        
        // XML-specific response configuration
        private String xmlSuccessBody;           // Success response body for XML
        private String xmlErrorBody;             // Error response body for XML matching failures
        
        // JSON-specific response configuration  
        private String jsonSuccessBody;          // Success response body for JSON
        private String jsonErrorBody;            // Error response body for JSON matching failures
        
        // Mainframe-specific response configuration
        private String mainframeSuccessBody;     // Success response body
        private String mainframeErrorBody;       // Error response body for matching failures
        private String mainframeCharset;         // Character encoding for mainframe response
        
        public ResponseType getType() { return type; }
        public void setType(ResponseType type) { this.type = type; }
        
        public String getXmlBody() { return xmlBody; }
        public void setXmlBody(String xmlBody) { this.xmlBody = xmlBody; }
        
        public String getJsonBody() { return jsonBody; }
        public void setJsonBody(String jsonBody) { this.jsonBody = jsonBody; }
        
        // XML Success/Error getters and setters
        public String getXmlSuccessBody() { return xmlSuccessBody; }
        public void setXmlSuccessBody(String xmlSuccessBody) { this.xmlSuccessBody = xmlSuccessBody; }
        
        public String getXmlErrorBody() { return xmlErrorBody; }
        public void setXmlErrorBody(String xmlErrorBody) { this.xmlErrorBody = xmlErrorBody; }
        
        // JSON Success/Error getters and setters
        public String getJsonSuccessBody() { return jsonSuccessBody; }
        public void setJsonSuccessBody(String jsonSuccessBody) { this.jsonSuccessBody = jsonSuccessBody; }
        
        public String getJsonErrorBody() { return jsonErrorBody; }
        public void setJsonErrorBody(String jsonErrorBody) { this.jsonErrorBody = jsonErrorBody; }
        
        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> headers) { this.headers = headers; }
        
        public Map<String, HeaderConfig> getHeaderConfigs() { return headerConfigs; }
        public void setHeaderConfigs(Map<String, HeaderConfig> headerConfigs) { this.headerConfigs = headerConfigs; }
        
        public String getOverrideCorrelationId() { return overrideCorrelationId; }
        public void setOverrideCorrelationId(String overrideCorrelationId) { this.overrideCorrelationId = overrideCorrelationId; }
        
        // Mainframe getters and setters
        public String getMainframeSuccessBody() { return mainframeSuccessBody; }
        public void setMainframeSuccessBody(String mainframeSuccessBody) { this.mainframeSuccessBody = mainframeSuccessBody; }
        
        public String getMainframeErrorBody() { return mainframeErrorBody; }
        public void setMainframeErrorBody(String mainframeErrorBody) { this.mainframeErrorBody = mainframeErrorBody; }
        
        public String getMainframeCharset() { return mainframeCharset; }
        public void setMainframeCharset(String mainframeCharset) { this.mainframeCharset = mainframeCharset; }

        // Enhanced correlation ID getters and setters
        public CorrelationIdConfig getCorrelationIdConfig() { return correlationIdConfig; }
        public void setCorrelationIdConfig(CorrelationIdConfig correlationIdConfig) { this.correlationIdConfig = correlationIdConfig; }

        public String getCustomCorrelationId() { return customCorrelationId; }
        public void setCustomCorrelationId(String customCorrelationId) { this.customCorrelationId = customCorrelationId; }

        // Message ID getters and setters
        public MessageIdConfig getMessageIdConfig() { return messageIdConfig; }
        public void setMessageIdConfig(MessageIdConfig messageIdConfig) { this.messageIdConfig = messageIdConfig; }

        public String getCustomMessageId() { return customMessageId; }
        public void setCustomMessageId(String customMessageId) { this.customMessageId = customMessageId; }

        // MQ headers getters and setters
        public Map<String, String> getMqHeaders() { return mqHeaders; }
        public void setMqHeaders(Map<String, String> mqHeaders) { this.mqHeaders = mqHeaders; }
    }
    
    public static class HeaderConfig {
        public enum HeaderSource { FIXED, COPY_FROM_REQUEST, COPY_MESSAGE_ID, GENERATE_NEW_ID, CUSTOM_MESSAGE_ID }

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