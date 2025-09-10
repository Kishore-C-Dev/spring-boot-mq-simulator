package com.mqsim.service;

import com.mqsim.model.ResponseMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@ConditionalOnProperty(name = "mq.enabled", havingValue = "true", matchIfMissing = false)
public class MessageMatchingService {

    private static final Logger logger = LoggerFactory.getLogger(MessageMatchingService.class);

    public boolean matches(Message message, ResponseMapping mapping) {
        try {
            // Check correlation ID pattern
            if (!matchesCorrelationId(message, mapping)) {
                return false;
            }

            // Check header patterns
            if (!matchesHeaders(message, mapping)) {
                return false;
            }

            // Check body pattern
            if (!matchesBody(message, mapping)) {
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
            return true; // No pattern means match all
        }

        try {
            String correlationId = message.getMessageProperties().getCorrelationId();
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
            return true; // No header requirements means match all
        }

        try {
            Map<String, Object> messageHeaders = message.getMessageProperties().getHeaders();

            for (Map.Entry<String, String> requiredHeader : requiredHeaders.entrySet()) {
                String headerName = requiredHeader.getKey();
                String expectedValue = requiredHeader.getValue();

                Object actualValue = messageHeaders.get(headerName);
                if (actualValue == null) {
                    logger.debug("Required header '{}' not found in message", headerName);
                    return false;
                }

                if (!expectedValue.equals(actualValue.toString())) {
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
            return true; // No pattern means match all
        }

        try {
            byte[] body = message.getBody();
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
}