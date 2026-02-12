package com.mqsim.service;

import com.mqsim.model.ResponseMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

import com.ibm.mq.jakarta.jms.MQQueue;
import com.ibm.msg.client.jakarta.wmq.WMQConstants;

import jakarta.jms.BytesMessage;
import jakarta.jms.Destination;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Enumeration;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

@Service
public class ResponseService {

    private static final Logger logger = LoggerFactory.getLogger(ResponseService.class);

    private static final Set<String> INT_MQMD_PROPERTIES = Set.of(
        "CodedCharsetId", "CCSID", "Encoding", "Persistence", "MsgType", "Expiry",
        "Priority", "Report", "MsgFlags"
    );

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
            int delayMs = calculateDelay(mapping.getDelay());
            if (delayMs > 0) {
                logger.debug("Applying delay of {}ms before sending response", delayMs);
                Thread.sleep(delayMs);
            }

            String replyQueue = getReplyQueue(originalMessage);
            String correlationId = getCorrelationId(originalMessage);

            MQQueue mqDestination = new MQQueue(replyQueue);
            mqDestination.setTargetClient(WMQConstants.WMQ_CLIENT_NONJMS_MQ);

            jmsTemplate.send(mqDestination, session -> {
                byte[] body = createResponseBody(originalMessage, mapping);

                BytesMessage responseMessage = session.createBytesMessage();
                responseMessage.writeBytes(body);

                String corrId = determineCorrelationId(originalMessage, mapping.getResponse());
                if (corrId != null && !corrId.trim().isEmpty()) {
                    responseMessage.setJMSCorrelationID(corrId);
                }

                String messageId = determineMessageId(mapping.getResponse());
                if (messageId != null && !messageId.trim().isEmpty()) {
                    responseMessage.setStringProperty("CustomMessageId", messageId);
                }

                if (mapping.getResponse().getHeaders() != null) {
                    for (Map.Entry<String, String> header : mapping.getResponse().getHeaders().entrySet()) {
                        responseMessage.setStringProperty(header.getKey(), header.getValue());
                    }
                }

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

                setMqHeaders(responseMessage, mapping.getResponse().getMqHeaders());

                if (mapping.getResponse().getType() == ResponseMapping.ResponseConfig.ResponseType.MAINFRAME) {
                    String charset = mapping.getResponse().getMainframeCharset();
                    if (charset != null && !charset.trim().isEmpty()) {
                        int ccsid = charsetToCcsid(charset);
                        if (ccsid > 0) {
                            responseMessage.setIntProperty("JMS_IBM_Character_Set", ccsid);
                            logger.debug("Set MQMD CCSID to {} from mainframeCharset '{}'", ccsid, charset);
                        }
                    }
                }

                copyProperty(originalMessage, responseMessage, "ReplyToQMgr");

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

    private void setMqHeaders(Message responseMessage, Map<String, String> mqHeaders) throws jakarta.jms.JMSException {
        if (mqHeaders == null) {
            return;
        }

        for (Map.Entry<String, String> entry : mqHeaders.entrySet()) {
            String headerName = entry.getKey();
            String headerValue = entry.getValue();
            if (headerValue == null || headerValue.trim().isEmpty()) {
                continue;
            }

            switch (headerName) {
                case "CodedCharsetId":
                case "CCSID":
                    responseMessage.setIntProperty("JMS_IBM_Character_Set", Integer.parseInt(headerValue));
                    logger.debug("Set MQMD CodedCharSetId (JMS_IBM_Character_Set) = {}", headerValue);
                    break;
                case "Encoding":
                    responseMessage.setIntProperty("JMS_IBM_Encoding", Integer.parseInt(headerValue));
                    logger.debug("Set MQMD Encoding (JMS_IBM_Encoding) = {}", headerValue);
                    break;
                case "FORMAT":
                case "Format":
                    responseMessage.setStringProperty("JMS_IBM_Format", headerValue);
                    logger.debug("Set MQMD Format (JMS_IBM_Format) = {}", headerValue);
                    break;
                case "Persistence":
                    responseMessage.setIntProperty("JMS_IBM_MsgType", Integer.parseInt(headerValue));
                    break;
                case "Report":
                    responseMessage.setIntProperty("JMS_IBM_Report", Integer.parseInt(headerValue));
                    break;
                case "MsgType":
                    responseMessage.setIntProperty("JMS_IBM_MsgType", Integer.parseInt(headerValue));
                    break;
                default:
                    responseMessage.setStringProperty(headerName, headerValue);
                    break;
            }
        }
    }

    private int charsetToCcsid(String charset) {
        if (charset == null) {
            return -1;
        }
        switch (charset.toUpperCase()) {
            case "CP500":
            case "IBM500":
            case "EBCDIC-CP-BE":
                return 500;
            case "CP037":
            case "IBM037":
                return 37;
            case "CP1047":
            case "IBM1047":
                return 1047;
            case "CP273":
            case "IBM273":
                return 273;
            case "CP277":
            case "IBM277":
                return 277;
            case "CP278":
            case "IBM278":
                return 278;
            case "CP280":
            case "IBM280":
                return 280;
            case "CP284":
            case "IBM284":
                return 284;
            case "CP285":
            case "IBM285":
                return 285;
            case "CP297":
            case "IBM297":
                return 297;
            case "UTF-8":
            case "UTF8":
                return 1208;
            case "ISO-8859-1":
            case "ISO8859_1":
            case "LATIN1":
                return 819;
            case "US-ASCII":
            case "ASCII":
                return 437;
            default:
                logger.warn("Unknown charset '{}' — cannot map to CCSID, skipping", charset);
                return -1;
        }
    }

    private int calculateDelay(ResponseMapping.DelayConfig delayConfig) {
        if (delayConfig == null) {
            return 0;
        }
        if (delayConfig.getMode() == ResponseMapping.DelayConfig.DelayMode.FIXED) {
            return delayConfig.getFixedMs() != null ? delayConfig.getFixedMs() : 0;
        } else {
            int min = delayConfig.getVariableMinMs() != null ? delayConfig.getVariableMinMs() : 100;
            int max = delayConfig.getVariableMaxMs() != null ? delayConfig.getVariableMaxMs() : 500;
            if (min >= max) {
                return min;
            }
            return min + random.nextInt(max - min + 1);
        }
    }

    private String getReplyQueue(Message originalMessage) {
        try {
            Destination replyTo = originalMessage.getJMSReplyTo();
            if (replyTo != null) {
                String replyToStr = extractQueueName(replyTo.toString());
                if (isValidQueueName(replyToStr)) {
                    logger.debug("Using JMS reply-to destination: {}", replyToStr);
                    return replyToStr.trim();
                }
            }

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

            logger.debug("No reply-to queue found in message, using default: {}", defaultReplyQueue);
            return defaultReplyQueue;

        } catch (Exception e) {
            logger.warn("Error determining reply queue, using default: {}", defaultReplyQueue, e);
            return defaultReplyQueue;
        }
    }

    private String getPropertyCaseInsensitive(Message message, String targetKey) {
        try {
            String value = message.getStringProperty(targetKey);
            if (value != null) {
                return value;
            }

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

    private String extractQueueName(String destinationStr) {
        if (destinationStr == null) {
            return null;
        }
        if (destinationStr.startsWith("queue://")) {
            String afterScheme = destinationStr.substring("queue://".length());
            int slashIdx = afterScheme.indexOf('/');
            if (slashIdx >= 0) {
                return afterScheme.substring(slashIdx + 1);
            }
        }
        return destinationStr;
    }

    private byte[] createResponseBody(Message originalMessage, ResponseMapping mapping) {
        try {
            if (mapping.getResponse().getType() == ResponseMapping.ResponseConfig.ResponseType.XML) {
                String responseBody = determineXmlResponse(originalMessage, mapping);
                return responseBody.getBytes(StandardCharsets.UTF_8);
            } else if (mapping.getResponse().getType() == ResponseMapping.ResponseConfig.ResponseType.JSON) {
                String responseBody = determineJsonResponse(originalMessage, mapping);
                return responseBody.getBytes(StandardCharsets.UTF_8);
            } else if (mapping.getResponse().getType() == ResponseMapping.ResponseConfig.ResponseType.MAINFRAME) {
                String responseStr = determineMainframeResponse(originalMessage, mapping);
                String charset = mapping.getResponse().getMainframeCharset();

                try {
                    String cleanBase64 = cleanBase64String(responseStr);
                    byte[] decoded = Base64.getDecoder().decode(cleanBase64);
                    logger.debug("Decoded Base64 mainframe response, length: {} bytes", decoded.length);
                    return decoded;
                } catch (IllegalArgumentException e) {
                    logger.debug("Response is not Base64, encoding as text with charset '{}'",
                                charset != null ? charset : "UTF-8");
                    Charset cs = resolveCharset(charset);
                    return responseStr.getBytes(cs);
                }
            } else {
                return "Unknown response type".getBytes(StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            logger.error("Error creating response body", e);
            throw new RuntimeException("Failed to create response body", e);
        }
    }

    private Charset resolveCharset(String charsetName) {
        if (charsetName == null || charsetName.trim().isEmpty()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(charsetName);
        } catch (Exception e) {
            logger.warn("Unknown charset '{}', falling back to UTF-8", charsetName);
            return StandardCharsets.UTF_8;
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
                return ((TextMessage) message).getText().getBytes(StandardCharsets.UTF_8);
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
