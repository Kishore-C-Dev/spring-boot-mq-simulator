package com.mqsim.service;

import com.mqsim.model.ResponseMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "mq.enabled", havingValue = "true", matchIfMissing = false)
public class ResponseService {

    private static final Logger logger = LoggerFactory.getLogger(ResponseService.class);
    
    private final RabbitTemplate rabbitTemplate;
    private final Random random = new Random();
    
    @Autowired
    private MainframeMessageProcessor mainframeMessageProcessor;
    
    @Autowired
    private XPathMessageProcessor xPathMessageProcessor;
    
    @Autowired
    private JsonPathMessageProcessor jsonPathMessageProcessor;
    
    @Value("${mq.default.reply.queue:sim.reply.default}")
    private String defaultReplyQueue;

    public ResponseService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendResponse(Message originalMessage, ResponseMapping mapping) {
        try {
            // Calculate delay
            int delayMs = calculateDelay(mapping.getDelay());
            if (delayMs > 0) {
                logger.debug("Applying delay of {}ms before sending response", delayMs);
                Thread.sleep(delayMs);
            }

            // Determine reply destination
            String replyQueue = getReplyQueue(originalMessage);
            String correlationId = getCorrelationId(originalMessage);
            
            // Create response message
            Message responseMessage = createResponseMessage(originalMessage, mapping);
            
            // Send response
            rabbitTemplate.send(replyQueue, responseMessage);
            
            logger.info("Response sent to queue '{}' for correlationID: {} (mapping: {}, responseType: {})", 
                       replyQueue, correlationId, mapping.getId(), mapping.getResponse().getType());

        } catch (Exception e) {
            String correlationId = getCorrelationIdSafely(originalMessage);
            logger.error("Error sending response for correlationID: {}", correlationId, e);
        }
    }

    private int calculateDelay(ResponseMapping.DelayConfig delayConfig) {
        if (delayConfig.getMode() == ResponseMapping.DelayConfig.DelayMode.FIXED) {
            return delayConfig.getFixedMs() != null ? delayConfig.getFixedMs() : 0;
        } else {
            int min = delayConfig.getVariableMinMs() != null ? delayConfig.getVariableMinMs() : 100;
            int max = delayConfig.getVariableMaxMs() != null ? delayConfig.getVariableMaxMs() : 500;
            return min + random.nextInt(max - min + 1);
        }
    }

    private String getReplyQueue(Message originalMessage) {
        try {
            MessageProperties messageProperties = originalMessage.getMessageProperties();
            Map<String, Object> headers = messageProperties.getHeaders();
            
            // Priority 1: Check standard AMQP reply-to property
            String replyTo = messageProperties.getReplyTo();
            if (isValidQueueName(replyTo)) {
                logger.debug("Using standard reply-to property: {}", replyTo);
                return replyTo.trim();
            }
            
            // Priority 2: Check common reply-to header variations (case-insensitive)
            String[] replyToHeaders = {
                "ReplyToQ", "ReplyTo", "reply-to", "REPLY_TO", 
                "ReplyQueue", "reply-queue", "REPLY_QUEUE",
                "ResponseQueue", "response-queue", "RESPONSE_QUEUE",
                "JMSReplyTo", "jms-reply-to", "JMS_REPLY_TO"
            };
            
            for (String headerName : replyToHeaders) {
                Object headerValue = getHeaderCaseInsensitive(headers, headerName);
                if (headerValue != null && isValidQueueName(headerValue.toString())) {
                    logger.debug("Using reply-to header '{}': {}", headerName, headerValue);
                    return headerValue.toString().trim();
                }
            }
            
            // Priority 4: Use configured default reply queue
            logger.debug("No reply-to queue found in message, using default: {}", defaultReplyQueue);
            return defaultReplyQueue;

        } catch (Exception e) {
            logger.warn("Error determining reply queue, using default: {}", defaultReplyQueue, e);
            return defaultReplyQueue;
        }
    }
    
    /**
     * Get header value with case-insensitive key matching
     */
    private Object getHeaderCaseInsensitive(Map<String, Object> headers, String targetKey) {
        if (headers == null || targetKey == null) {
            return null;
        }
        
        // First try exact match
        Object value = headers.get(targetKey);
        if (value != null) {
            return value;
        }
        
        // Then try case-insensitive match
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            if (targetKey.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        return null;
    }
    
    /**
     * Validate that the queue name is not null, empty, or whitespace
     */
    private boolean isValidQueueName(String queueName) {
        return queueName != null && !queueName.trim().isEmpty();
    }

    private Message createResponseMessage(Message originalMessage, ResponseMapping mapping) {
        try {
            MessageProperties properties = new MessageProperties();
            
            // Set correlation ID using enhanced configuration
            String correlationId = determineCorrelationId(originalMessage, mapping.getResponse());
            if (correlationId != null && !correlationId.trim().isEmpty()) {
                properties.setCorrelationId(correlationId);
            }

            // Set message ID if configured
            String messageId = determineMessageId(mapping.getResponse());
            if (messageId != null && !messageId.trim().isEmpty()) {
                properties.setMessageId(messageId);
            }

            // Set legacy fixed headers (backward compatibility)
            if (mapping.getResponse().getHeaders() != null) {
                for (Map.Entry<String, String> header : mapping.getResponse().getHeaders().entrySet()) {
                    properties.setHeader(header.getKey(), header.getValue());
                }
            }

            // Set configurable headers
            if (mapping.getResponse().getHeaderConfigs() != null) {
                for (Map.Entry<String, ResponseMapping.HeaderConfig> entry : mapping.getResponse().getHeaderConfigs().entrySet()) {
                    String headerName = entry.getKey();
                    ResponseMapping.HeaderConfig config = entry.getValue();

                    String headerValue = resolveHeaderValue(originalMessage, config);
                    if (headerValue != null) {
                        properties.setHeader(headerName, headerValue);
                    }
                }
            }

            // Set MQ-specific headers
            if (mapping.getResponse().getMqHeaders() != null) {
                for (Map.Entry<String, String> entry : mapping.getResponse().getMqHeaders().entrySet()) {
                    String headerName = entry.getKey();
                    String headerValue = entry.getValue();
                    if (headerValue != null && !headerValue.trim().isEmpty()) {
                        properties.setHeader(headerName, headerValue);
                    }
                }
            }

            // Copy some properties from original message
            copyHeader(originalMessage, properties, "ReplyToQMgr");
            
            // Create message body based on response type
            byte[] body;
            if (mapping.getResponse().getType() == ResponseMapping.ResponseConfig.ResponseType.XML) {
                // XML response - determine success or error based on matching
                String responseBody = determineXmlResponse(originalMessage, mapping);
                body = responseBody.getBytes();
                properties.setContentType("application/xml");
            } else if (mapping.getResponse().getType() == ResponseMapping.ResponseConfig.ResponseType.JSON) {
                // JSON response - determine success or error based on matching
                String responseBody = determineJsonResponse(originalMessage, mapping);
                body = responseBody.getBytes();
                properties.setContentType("application/json");
            } else if (mapping.getResponse().getType() == ResponseMapping.ResponseConfig.ResponseType.MAINFRAME) {
                // Mainframe response - determine success or error based on matching
                String base64Response = determineMainframeResponse(originalMessage, mapping);
                
                try {
                    // Clean Base64 string: remove whitespace, newlines, and other invalid characters
                    String cleanBase64 = cleanBase64String(base64Response);
                    
                    // Decode Base64 to actual binary bytes for transmission
                    body = Base64.getDecoder().decode(cleanBase64);
                    logger.debug("Successfully decoded Base64 mainframe response, length: {} bytes", body.length);
                } catch (IllegalArgumentException e) {
                    // Fallback to string encoding if not valid Base64
                    logger.warn("Invalid Base64 in mainframe response, treating as string: {}", e.getMessage());
                    logger.debug("Original Base64 content: '{}'", base64Response);
                    String charset = mapping.getResponse().getMainframeCharset();
                    
                    if (charset != null && !charset.trim().isEmpty()) {
                        try {
                            body = base64Response.getBytes(charset);
                        } catch (Exception ex) {
                            logger.warn("Failed to encode mainframe response with charset {}, using UTF-8", charset, ex);
                            body = base64Response.getBytes();
                        }
                    } else {
                        body = base64Response.getBytes();
                    }
                }
                properties.setContentType("application/octet-stream");
            } else {
                // Default fallback
                body = "Unknown response type".getBytes();
                properties.setContentType("text/plain");
            }

            return MessageBuilder.withBody(body).andProperties(properties).build();
            
        } catch (Exception e) {
            logger.error("Error creating response message", e);
            throw new RuntimeException("Failed to create response message", e);
        }
    }

    private String resolveHeaderValue(Message originalMessage, ResponseMapping.HeaderConfig config) {
        try {
            switch (config.getSource()) {
                case FIXED:
                    return config.getFixedValue();

                case COPY_FROM_REQUEST:
                    String requestHeaderName = config.getRequestHeaderName();
                    if (requestHeaderName != null && !requestHeaderName.trim().isEmpty()) {
                        Object value = originalMessage.getMessageProperties().getHeaders().get(requestHeaderName);
                        return value != null ? value.toString() : null;
                    }
                    return null;

                case COPY_MESSAGE_ID:
                    String messageId = originalMessage.getMessageProperties().getMessageId();
                    return messageId != null ? messageId : null;

                case GENERATE_NEW_ID:
                    return UUID.randomUUID().toString();

                case CUSTOM_MESSAGE_ID:
                    return config.getFixedValue(); // Use fixedValue field for custom message ID

                default:
                    logger.warn("Unknown header source: {}", config.getSource());
                    return null;
            }
        } catch (Exception e) {
            logger.debug("Error resolving header value for config: {}", config, e);
            return null;
        }
    }

    private void copyHeader(Message source, MessageProperties target, String headerName) {
        try {
            Object value = source.getMessageProperties().getHeaders().get(headerName);
            if (value != null) {
                target.setHeader(headerName, value);
            }
        } catch (Exception e) {
            logger.debug("Could not copy header {}: {}", headerName, e.getMessage());
        }
    }

    private String getCorrelationId(Message message) {
        return message.getMessageProperties().getCorrelationId();
    }

    private String getCorrelationIdSafely(Message message) {
        try {
            return getCorrelationId(message);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String determineXmlResponse(Message originalMessage, ResponseMapping mapping) {
        try {
            // Check if XPath rules are configured
            if (mapping.getMatch().getXpathRules() == null || mapping.getMatch().getXpathRules().isEmpty()) {
                // No XPath rules - use default XML body or success body
                logger.debug("No XPath rules configured, returning default XML response");
                if (mapping.getResponse().getXmlSuccessBody() != null) {
                    return mapping.getResponse().getXmlSuccessBody();
                } else if (mapping.getResponse().getXmlBody() != null) {
                    return mapping.getResponse().getXmlBody();
                } else {
                    return "<response>OK</response>";
                }
            }
            
            // Check if the message matches the XPath rules to determine success or error response
            boolean matches = xPathMessageProcessor.processMessage(
                originalMessage.getBody(),
                mapping.getMatch().getXpathRules()
            );
            
            if (matches) {
                logger.debug("XML message matched XPath rules, returning success response");
                return mapping.getResponse().getXmlSuccessBody() != null ? 
                    mapping.getResponse().getXmlSuccessBody() : 
                    (mapping.getResponse().getXmlBody() != null ? mapping.getResponse().getXmlBody() : "<response>OK</response>");
            } else {
                logger.debug("XML message failed to match XPath rules, returning error response");
                return mapping.getResponse().getXmlErrorBody() != null ? 
                    mapping.getResponse().getXmlErrorBody() : "<error>XML XPath matching failed</error>";
            }
            
        } catch (Exception e) {
            logger.error("Error determining XML response, returning error response", e);
            return mapping.getResponse().getXmlErrorBody() != null ? 
                mapping.getResponse().getXmlErrorBody() : "<error>XML processing failed</error>";
        }
    }
    
    private String determineJsonResponse(Message originalMessage, ResponseMapping mapping) {
        try {
            // Check if JSONPath rules are configured
            if (mapping.getMatch().getJsonPathRules() == null || mapping.getMatch().getJsonPathRules().isEmpty()) {
                // No JSONPath rules - use default JSON body or success body
                logger.debug("No JSONPath rules configured, returning default JSON response");
                if (mapping.getResponse().getJsonSuccessBody() != null) {
                    return mapping.getResponse().getJsonSuccessBody();
                } else if (mapping.getResponse().getJsonBody() != null) {
                    return mapping.getResponse().getJsonBody();
                } else {
                    return "{\"status\":\"OK\"}";
                }
            }
            
            // Check if the message matches the JSONPath rules to determine success or error response
            boolean matches = jsonPathMessageProcessor.processMessage(
                originalMessage.getBody(),
                mapping.getMatch().getJsonPathRules()
            );
            
            if (matches) {
                logger.debug("JSON message matched JSONPath rules, returning success response");
                return mapping.getResponse().getJsonSuccessBody() != null ? 
                    mapping.getResponse().getJsonSuccessBody() : 
                    (mapping.getResponse().getJsonBody() != null ? mapping.getResponse().getJsonBody() : "{\"status\":\"OK\"}");
            } else {
                logger.debug("JSON message failed to match JSONPath rules, returning error response");
                return mapping.getResponse().getJsonErrorBody() != null ? 
                    mapping.getResponse().getJsonErrorBody() : "{\"error\":\"JSON JSONPath matching failed\"}";
            }
            
        } catch (Exception e) {
            logger.error("Error determining JSON response, returning error response", e);
            return mapping.getResponse().getJsonErrorBody() != null ? 
                mapping.getResponse().getJsonErrorBody() : "{\"error\":\"JSON processing failed\"}";
        }
    }

    private String determineMainframeResponse(Message originalMessage, ResponseMapping mapping) {
        try {
            // Check if the message matches the mainframe rules to determine success or error response
            boolean matches = mainframeMessageProcessor.processMessage(
                originalMessage.getBody(),
                mapping.getMatch().getMainframeRules(),
                mapping.getMatch().getCharset()
            );
            
            if (matches) {
                logger.debug("Mainframe message matched rules, returning success response");
                return mapping.getResponse().getMainframeSuccessBody() != null ? 
                    mapping.getResponse().getMainframeSuccessBody() : "";
            } else {
                logger.debug("Mainframe message failed to match rules, returning error response");
                return mapping.getResponse().getMainframeErrorBody() != null ? 
                    mapping.getResponse().getMainframeErrorBody() : "ERROR: Mainframe matching failed";
            }
            
        } catch (Exception e) {
            logger.error("Error determining mainframe response, returning error response", e);
            return mapping.getResponse().getMainframeErrorBody() != null ? 
                mapping.getResponse().getMainframeErrorBody() : "ERROR: Mainframe processing failed";
        }
    }
    
    /**
     * Clean Base64 string by removing whitespace, newlines, and other invalid characters
     */
    private String cleanBase64String(String base64String) {
        if (base64String == null) {
            return "";
        }
        
        // Remove all whitespace, newlines, carriage returns, and tabs
        String cleaned = base64String.replaceAll("\\s+", "");
        
        logger.debug("Base64 cleaning: original length={}, cleaned length={}", 
                    base64String.length(), cleaned.length());
        
        // Log if we removed characters for debugging
        if (!base64String.equals(cleaned)) {
            logger.debug("Cleaned Base64 string: removed whitespace/newlines");
        }
        
        return cleaned;
    }

    private String determineCorrelationId(Message originalMessage, ResponseMapping.ResponseConfig responseConfig) {
        try {
            // Check for legacy override first (backward compatibility)
            if (responseConfig.getOverrideCorrelationId() != null && !responseConfig.getOverrideCorrelationId().trim().isEmpty()) {
                return responseConfig.getOverrideCorrelationId();
            }

            // Use enhanced correlation ID configuration
            if (responseConfig.getCorrelationIdConfig() != null) {
                switch (responseConfig.getCorrelationIdConfig()) {
                    case USE_REQUEST_CORRELATION_ID:
                        return originalMessage.getMessageProperties().getCorrelationId();

                    case USE_REQUEST_MESSAGE_ID:
                        return originalMessage.getMessageProperties().getMessageId();

                    case GENERATE_NEW:
                        return UUID.randomUUID().toString();

                    case CUSTOM_VALUE:
                        return responseConfig.getCustomCorrelationId();

                    default:
                        logger.warn("Unknown correlation ID config: {}", responseConfig.getCorrelationIdConfig());
                        return originalMessage.getMessageProperties().getCorrelationId();
                }
            }

            // Default behavior: use request correlation ID
            return originalMessage.getMessageProperties().getCorrelationId();
        } catch (Exception e) {
            logger.debug("Error determining correlation ID, using default", e);
            return originalMessage.getMessageProperties().getCorrelationId();
        }
    }

    private String determineMessageId(ResponseMapping.ResponseConfig responseConfig) {
        try {
            if (responseConfig.getMessageIdConfig() != null) {
                switch (responseConfig.getMessageIdConfig()) {
                    case DONT_SET:
                        return null; // Don't set message ID

                    case CUSTOM_VALUE:
                        return responseConfig.getCustomMessageId();

                    default:
                        logger.warn("Unknown message ID config: {}", responseConfig.getMessageIdConfig());
                        return null;
                }
            }

            // Default behavior: don't set message ID
            return null;
        } catch (Exception e) {
            logger.debug("Error determining message ID", e);
            return null;
        }
    }
}