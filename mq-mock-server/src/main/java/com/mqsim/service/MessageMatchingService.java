package com.mqsim.service;

import com.mqsim.model.ResponseMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.jms.BytesMessage;
import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class MessageMatchingService {

    private static final Logger logger = LoggerFactory.getLogger(MessageMatchingService.class);

    @Autowired
    private MainframeMessageProcessor mainframeMessageProcessor;

    @Autowired
    private XPathMessageProcessor xPathMessageProcessor;

    @Autowired
    private JsonPathMessageProcessor jsonPathMessageProcessor;

    public boolean matches(Message message, ResponseMapping mapping) {
        try {
            if (!matchesCorrelationId(message, mapping)) {
                return false;
            }

            if (!matchesHeaders(message, mapping)) {
                return false;
            }

            if (!matchesBody(message, mapping)) {
                return false;
            }

            if (!matchesContentSpecific(message, mapping)) {
                return false;
            }

            return true;

        } catch (Exception e) {
            logger.error("Error matching message against mapping", e);
            return false;
        }
    }

    private boolean matchesCorrelationId(Message message, ResponseMapping mapping) {
        String pattern = mapping.getMatch().getCorrelationId();
        if (pattern == null || pattern.trim().isEmpty()) {
            return true;
        }

        try {
            String correlationId = message.getJMSCorrelationID();
            if (correlationId == null) {
                return false;
            }

            Pattern compiledPattern = Pattern.compile(pattern);
            boolean matches = compiledPattern.matcher(correlationId).matches();

            logger.debug("Correlation ID pattern '{}' {} correlation ID '{}'",
                        pattern, matches ? "matches" : "does not match", correlationId);

            return matches;

        } catch (Exception e) {
            logger.warn("Error matching correlation ID pattern: {}", pattern, e);
            return false;
        }
    }

    private boolean matchesHeaders(Message message, ResponseMapping mapping) {
        Map<String, String> requiredHeaders = mapping.getMatch().getHeaders();
        if (requiredHeaders == null || requiredHeaders.isEmpty()) {
            return true;
        }

        try {
            for (Map.Entry<String, String> requiredHeader : requiredHeaders.entrySet()) {
                String headerName = requiredHeader.getKey();
                String expectedValue = requiredHeader.getValue();

                String actualValue = message.getStringProperty(headerName);
                if (actualValue == null) {
                    logger.debug("Required header '{}' not found in message", headerName);
                    return false;
                }

                if (!expectedValue.equals(actualValue)) {
                    logger.debug("Header '{}' value '{}' does not match expected '{}'",
                                headerName, actualValue, expectedValue);
                    return false;
                }
            }

            return true;

        } catch (Exception e) {
            logger.warn("Error matching headers", e);
            return false;
        }
    }

    private boolean matchesBody(Message message, ResponseMapping mapping) {
        String pattern = mapping.getMatch().getBodyRegex();
        if (pattern == null || pattern.trim().isEmpty()) {
            return true;
        }

        try {
            byte[] body = getMessageBody(message);
            if (body == null || body.length == 0) {
                return false;
            }

            String bodyText = new String(body, StandardCharsets.UTF_8);
            Pattern compiledPattern = Pattern.compile(pattern, Pattern.DOTALL);
            boolean matches = compiledPattern.matcher(bodyText).find();

            logger.debug("Body pattern '{}' {} message body",
                        pattern, matches ? "matches" : "does not match");

            return matches;

        } catch (Exception e) {
            logger.warn("Error matching body pattern: {}", pattern, e);
            return false;
        }
    }

    private boolean matchesContentSpecific(Message message, ResponseMapping mapping) {
        try {
            ResponseMapping.ResponseConfig.ResponseType responseType = mapping.getResponse().getType();
            byte[] messageBytes = getMessageBody(message);

            if (messageBytes == null || messageBytes.length == 0) {
                logger.debug("Message body is null or empty for content-specific matching");
                return true;
            }

            switch (responseType) {
                case XML:
                    return matchesXPath(messageBytes, mapping);
                case JSON:
                    return matchesJsonPath(messageBytes, mapping);
                case MAINFRAME:
                    return matchesMainframe(messageBytes, mapping);
                default:
                    return true;
            }

        } catch (Exception e) {
            logger.error("Error during content-specific message matching", e);
            return false;
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

    private boolean matchesXPath(byte[] messageBytes, ResponseMapping mapping) {
        try {
            logger.debug("Processing XPath message matching");

            boolean matches = xPathMessageProcessor.processMessage(
                messageBytes,
                mapping.getMatch().getXpathRules()
            );

            logger.debug("XPath message matching result: {}", matches);
            return matches;

        } catch (Exception e) {
            logger.error("Error during XPath message matching", e);
            return false;
        }
    }

    private boolean matchesJsonPath(byte[] messageBytes, ResponseMapping mapping) {
        try {
            logger.debug("Processing JSONPath message matching");

            boolean matches = jsonPathMessageProcessor.processMessage(
                messageBytes,
                mapping.getMatch().getJsonPathRules()
            );

            logger.debug("JSONPath message matching result: {}", matches);
            return matches;

        } catch (Exception e) {
            logger.error("Error during JSONPath message matching", e);
            return false;
        }
    }

    private boolean matchesMainframe(byte[] messageBytes, ResponseMapping mapping) {
        try {
            logger.debug("Processing mainframe message matching");

            String charset = mapping.getMatch().getCharset();

            boolean matches = mainframeMessageProcessor.processMessage(
                messageBytes,
                mapping.getMatch().getMainframeRules(),
                charset
            );

            logger.debug("Mainframe message matching result: {}", matches);
            return matches;

        } catch (Exception e) {
            logger.error("Error during mainframe message matching", e);
            return false;
        }
    }
}
