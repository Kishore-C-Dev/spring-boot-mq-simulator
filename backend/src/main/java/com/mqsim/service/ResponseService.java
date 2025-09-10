package com.mqsim.service;

import com.mqsim.model.ResponseMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Map;
import java.util.Random;

@Service
@ConditionalOnProperty(name = "mq.enabled", havingValue = "true", matchIfMissing = false)
public class ResponseService {

    private static final Logger logger = LoggerFactory.getLogger(ResponseService.class);
    
    private final RabbitTemplate rabbitTemplate;
    private final Random random = new Random();
    
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
            
            // Create response message
            Message responseMessage = createResponseMessage(originalMessage, mapping);
            
            // Send response
            rabbitTemplate.send(replyQueue, responseMessage);
            
            String correlationId = getCorrelationId(originalMessage);
            logger.info("Response sent to {} for correlationID: {}", replyQueue, correlationId);

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
            // Check for reply-to in message properties
            String replyTo = originalMessage.getMessageProperties().getReplyTo();
            if (replyTo != null && !replyTo.trim().isEmpty()) {
                return replyTo.trim();
            }
            
            // Check for custom ReplyToQ header
            Map<String, Object> headers = originalMessage.getMessageProperties().getHeaders();
            Object replyToQ = headers.get("ReplyToQ");
            if (replyToQ != null && !replyToQ.toString().trim().isEmpty()) {
                return replyToQ.toString().trim();
            }
            
            // Use default reply queue
            return defaultReplyQueue;

        } catch (Exception e) {
            logger.warn("Error determining reply queue, using default: {}", defaultReplyQueue, e);
            return defaultReplyQueue;
        }
    }

    private Message createResponseMessage(Message originalMessage, ResponseMapping mapping) {
        try {
            MessageProperties properties = new MessageProperties();
            
            // Set correlation ID
            String correlationId = mapping.getResponse().getOverrideCorrelationId();
            if (correlationId == null || correlationId.trim().isEmpty()) {
                correlationId = getCorrelationId(originalMessage);
            }
            properties.setCorrelationId(correlationId);

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

            // Copy some properties from original message
            copyHeader(originalMessage, properties, "ReplyToQMgr");
            
            // Create message body based on response type
            byte[] body;
            if (mapping.getResponse().getType() == ResponseMapping.ResponseConfig.ResponseType.XML) {
                // XML response
                body = mapping.getResponse().getXmlBody().getBytes();
                properties.setContentType("application/xml");
            } else {
                // MF (EBCDIC) response
                body = Base64.getDecoder().decode(mapping.getResponse().getMfBodyBase64());
                properties.setContentType("application/octet-stream");
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
}