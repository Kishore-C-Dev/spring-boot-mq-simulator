package com.mqsim.service;

import com.mqsim.model.ResponseMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import jakarta.jms.BytesMessage;
import jakarta.jms.Destination;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import java.util.Base64;
import java.util.Enumeration;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "mq.enabled", havingValue = "true", matchIfMissing = false)
public class ResponseService {

    private static final Logger logger = LoggerFactory.getLogger(ResponseService.class);

    private final JmsTemplate jmsTemplate;
    private final Random random = new Random();

    @Autowired
    private MainframeMessageProcessor mainframeMessageProcessor;

    @Autowired
    private XPathMessageProcessor xPathMessageProcessor;

    @Autowired
    private JsonPathMessageProcessor jsonPathMessageProcessor;

    @Value("${mq.default.reply.queue:sim.reply.default}")
    private String defaultReplyQueue;

    public ResponseService(JmsTemplate jmsTemplate) {
        this.jmsTemplate = jmsTemplate;
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

            // Send response via JMS
            jmsTemplate.send(replyQueue, session -> {
                // Create message body based on response type
                byte[] body = createResponseBody(originalMessage, mapping);

                BytesMessage responseMessage = session.createBytesMessage();
                responseMessage.writeBytes(body);

                // Set correlation ID
                String corrId = determineCorrelationId(originalMessage, mapping.getResponse());
                if (corrId != null && !corrId.trim().isEmpty()) {
                    responseMessage.setJMSCorrelationID(corrId);
                }

                // Set message ID if configured
                String messageId = determineMessageId(mapping.getResponse());
                if (messageId != null && !messageId.trim().isEmpty()) {
                    responseMessage.setStringProperty("CustomMessageId", messageId);
                }

                // Set legacy fixed headers (backward compatibility)
                if (mapping.getResponse().getHeaders() != null) {
                    for (Map.Entry<String, String> header : mapping.getResponse().getHeaders().entrySet()) {
                        responseMessage.setStringProperty(header.getKey(), header.getValue());
                    }
                }

                // Set configurable headers
                if (mapping.getResponse().getHeaderConfigs() != null) {
                    for (Map.Entry<String, ResponseMapping.HeaderConfig> entry : mapping.getResponse().getHeaderConfigs().entrySet()) {
                        String headerName = entry.getKey();
                        ResponseMapping.HeaderConfig config = entry.getValue();

                        String headerValue = resolveHeaderValue(originalMessage, config);
                        if (headerValue != null) {
                            responseMessage.setStringProperty(headerName, headerValue);
                        }
                    }
                }

                // Set MQ-specific headers
                if (mapping.getResponse().getMqHeaders() != null) {
                    for (Map.Entry<String, String> entry : mapping.getResponse().getMqHeaders().entrySet()) {
                        String headerName = entry.getKey();
                        String headerValue = entry.getValue();
                        if (headerValue != null && !headerValue.trim().isEmpty()) {
                            responseMessage.setStringProperty(headerName, headerValue);
                        }
                    }
                }

                // Copy ReplyToQMgr from original message if present
                copyProperty(originalMessage, responseMessage, "ReplyToQMgr");

                // Set content type as a property
                String contentType = getContentType(mapping);
                if (contentType != null) {
                    responseMessage.setStringProperty("ContentType", contentType);
                }

                return responseMessage;
            });

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
            // Priority 1: Check standard JMS reply-to destination
            Destination replyTo = originalMessage.getJMSReplyTo();
            if (replyTo != null) {
                String replyToStr = replyTo.toString();
                if (replyToStr.contains("///")) {
                    replyToStr = replyToStr.substring(replyToStr.lastIndexOf("///") + 3);
                }
                if (isValidQueueName(replyToStr)) {
                    logger.debug("Using JMS reply-to destination: {}", replyToStr);
                    return replyToStr.trim();
                }
            }

            // Priority 2: Check common reply-to header variations (case-insensitive)
            String[] replyToHeaders = {
                "ReplyToQ", "ReplyTo", "reply-to", "REPLY_TO",
                "ReplyQueue", "reply-queue", "REPLY_QUEUE",
                "ResponseQueue", "response-queue", "RESPONSE_QUEUE",
                "JMSReplyTo", "jms-reply-to", "JMS_REPLY_TO"
            };

            for (String headerName : replyToHeaders) {
                String headerValue = getPropertyCaseInsensitive(originalMessage, headerName);
                if (headerValue != null && isValidQueueName(headerValue)) {
                    logger.debug("Using reply-to header '{}': {}", headerName, headerValue);
                    return headerValue.trim();
                }
            }

            // Priority 3: Use configured default reply queue
            logger.debug("No reply-to queue found in message, using default: {}", defaultReplyQueue);
            return defaultReplyQueue;

        } catch (Exception e) {
            logger.warn("Error determining reply queue, using default: {}", defaultReplyQueue, e);
            return defaultReplyQueue;
        }
    }

    private String getPropertyCaseInsensitive(Message message, String targetKey) {
        try {
            // First try exact match
            String value = message.getStringProperty(targetKey);
            if (value != null) {
                return value;
            }

            // Then try case-insensitive match
            Enumeration<?> names = message.getPropertyNames();
            while (names.hasMoreElements()) {
                String name = names.nextElement().toString();
                if (targetKey.equalsIgnoreCase(name)) {
                    return message.getStringProperty(name);
                }
            }
        } catch (Exception e) {
            logger.debug("Error getting property {}: {}", targetKey, e.getMessage());
        }
        return null;
    }

    private boolean isValidQueueName(String queueName) {
        return queueName != null && !queueName.trim().isEmpty();
    }

    private byte[] createResponseBody(Message originalMessage, ResponseMapping mapping) {
        try {
            if (mapping.getResponse().getType() == ResponseMapping.ResponseConfig.ResponseType.XML) {
                String responseBody = determineXmlResponse(originalMessage, mapping);
                return responseBody.getBytes();
            } else if (mapping.getResponse().getType() == ResponseMapping.ResponseConfig.ResponseType.JSON) {
                String responseBody = determineJsonResponse(originalMessage, mapping);
                return responseBody.getBytes();
            } else if (mapping.getResponse().getType() == ResponseMapping.ResponseConfig.ResponseType.MAINFRAME) {
                String base64Response = determineMainframeResponse(originalMessage, mapping);

                try {
                    String cleanBase64 = cleanBase64String(base64Response);
                    byte[] decoded = Base64.getDecoder().decode(cleanBase64);
                    logger.debug("Successfully decoded Base64 mainframe response, length: {} bytes", decoded.length);
                    return decoded;
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid Base64 in mainframe response, treating as string: {}", e.getMessage());
                    String charset = mapping.getResponse().getMainframeCharset();

                    if (charset != null && !charset.trim().isEmpty()) {
                        try {
                            return base64Response.getBytes(charset);
                        } catch (Exception ex) {
                            logger.warn("Failed to encode mainframe response with charset {}, using UTF-8", charset, ex);
                            return base64Response.getBytes();
                        }
                    } else {
                        return base64Response.getBytes();
                    }
                }
            } else {
                return "Unknown response type".getBytes();
            }
        } catch (Exception e) {
            logger.error("Error creating response body", e);
            throw new RuntimeException("Failed to create response body", e);
        }
    }

    private String getContentType(ResponseMapping mapping) {
        if (mapping.getResponse().getType() == ResponseMapping.ResponseConfig.ResponseType.XML) {
            return "application/xml";
        } else if (mapping.getResponse().getType() == ResponseMapping.ResponseConfig.ResponseType.JSON) {
            return "application/json";
        } else if (mapping.getResponse().getType() == ResponseMapping.ResponseConfig.ResponseType.MAINFRAME) {
            return "application/octet-stream";
        }
        return "text/plain";
    }

    private String resolveHeaderValue(Message originalMessage, ResponseMapping.HeaderConfig config) {
        try {
            switch (config.getSource()) {
                case FIXED:
                    return config.getFixedValue();

                case COPY_FROM_REQUEST:
                    String requestHeaderName = config.getRequestHeaderName();
                    if (requestHeaderName != null && !requestHeaderName.trim().isEmpty()) {
                        return originalMessage.getStringProperty(requestHeaderName);
                    }
                    return null;

                case COPY_MESSAGE_ID:
                    return originalMessage.getJMSMessageID();

                case GENERATE_NEW_ID:
                    return UUID.randomUUID().toString();

                case CUSTOM_MESSAGE_ID:
                    return config.getFixedValue();

                default:
                    logger.warn("Unknown header source: {}", config.getSource());
                    return null;
            }
        } catch (Exception e) {
            logger.debug("Error resolving header value for config: {}", config, e);
            return null;
        }
    }

    private void copyProperty(Message source, Message target, String propertyName) {
        try {
            String value = source.getStringProperty(propertyName);
            if (value != null) {
                target.setStringProperty(propertyName, value);
            }
        } catch (Exception e) {
            logger.debug("Could not copy property {}: {}", propertyName, e.getMessage());
        }
    }

    private String getCorrelationId(Message message) {
        try {
            return message.getJMSCorrelationID();
        } catch (Exception e) {
            return null;
        }
    }

    private String getCorrelationIdSafely(Message message) {
        try {
            String id = message.getJMSCorrelationID();
            return id != null ? id : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private byte[] getMessageBody(Message message) {
        try {
            if (message instanceof BytesMessage) {
                BytesMessage bytesMessage = (BytesMessage) message;
                long bodyLength = bytesMessage.getBodyLength();
                byte[] body = new byte[(int) bodyLength];
                bytesMessage.readBytes(body);
                return body;
            } else if (message instanceof TextMessage) {
                return ((TextMessage) message).getText().getBytes();
            }
            return message.getBody(byte[].class);
        } catch (Exception e) {
            logger.debug("Error getting message body", e);
            return new byte[0];
        }
    }

    private String determineXmlResponse(Message originalMessage, ResponseMapping mapping) {
        try {
            if (mapping.getMatch().getXpathRules() == null || mapping.getMatch().getXpathRules().isEmpty()) {
                logger.debug("No XPath rules configured, returning default XML response");
                if (mapping.getResponse().getXmlSuccessBody() != null) {
                    return mapping.getResponse().getXmlSuccessBody();
                } else if (mapping.getResponse().getXmlBody() != null) {
                    return mapping.getResponse().getXmlBody();
                } else {
                    return "<response>OK</response>";
                }
            }

            byte[] messageBody = getMessageBody(originalMessage);
            boolean matches = xPathMessageProcessor.processMessage(
                messageBody,
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
            if (mapping.getMatch().getJsonPathRules() == null || mapping.getMatch().getJsonPathRules().isEmpty()) {
                logger.debug("No JSONPath rules configured, returning default JSON response");
                if (mapping.getResponse().getJsonSuccessBody() != null) {
                    return mapping.getResponse().getJsonSuccessBody();
                } else if (mapping.getResponse().getJsonBody() != null) {
                    return mapping.getResponse().getJsonBody();
                } else {
                    return "{\"status\":\"OK\"}";
                }
            }

            byte[] messageBody = getMessageBody(originalMessage);
            boolean matches = jsonPathMessageProcessor.processMessage(
                messageBody,
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
            byte[] messageBody = getMessageBody(originalMessage);
            boolean matches = mainframeMessageProcessor.processMessage(
                messageBody,
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

    private String cleanBase64String(String base64String) {
        if (base64String == null) {
            return "";
        }

        String cleaned = base64String.replaceAll("\\s+", "");

        logger.debug("Base64 cleaning: original length={}, cleaned length={}",
                    base64String.length(), cleaned.length());

        if (!base64String.equals(cleaned)) {
            logger.debug("Cleaned Base64 string: removed whitespace/newlines");
        }

        return cleaned;
    }

    private String determineCorrelationId(Message originalMessage, ResponseMapping.ResponseConfig responseConfig) {
        try {
            if (responseConfig.getOverrideCorrelationId() != null && !responseConfig.getOverrideCorrelationId().trim().isEmpty()) {
                return responseConfig.getOverrideCorrelationId();
            }

            if (responseConfig.getCorrelationIdConfig() != null) {
                switch (responseConfig.getCorrelationIdConfig()) {
                    case USE_REQUEST_CORRELATION_ID:
                        return originalMessage.getJMSCorrelationID();

                    case USE_REQUEST_MESSAGE_ID:
                        return originalMessage.getJMSMessageID();

                    case GENERATE_NEW:
                        return UUID.randomUUID().toString();

                    case CUSTOM_VALUE:
                        return responseConfig.getCustomCorrelationId();

                    default:
                        logger.warn("Unknown correlation ID config: {}", responseConfig.getCorrelationIdConfig());
                        return originalMessage.getJMSCorrelationID();
                }
            }

            return originalMessage.getJMSCorrelationID();
        } catch (Exception e) {
            logger.debug("Error determining correlation ID, using default", e);
            try {
                return originalMessage.getJMSCorrelationID();
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private String determineMessageId(ResponseMapping.ResponseConfig responseConfig) {
        try {
            if (responseConfig.getMessageIdConfig() != null) {
                switch (responseConfig.getMessageIdConfig()) {
                    case DONT_SET:
                        return null;

                    case CUSTOM_VALUE:
                        return responseConfig.getCustomMessageId();

                    default:
                        logger.warn("Unknown message ID config: {}", responseConfig.getMessageIdConfig());
                        return null;
                }
            }

            return null;
        } catch (Exception e) {
            logger.debug("Error determining message ID", e);
            return null;
        }
    }
}
