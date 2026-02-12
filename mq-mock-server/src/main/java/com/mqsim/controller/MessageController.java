package com.mqsim.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.jms.DeliveryMode;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/message")
public class MessageController {

    private static final Logger logger = LoggerFactory.getLogger(MessageController.class);

    @Autowired
    private JmsTemplate jmsTemplate;

    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendMessage(@RequestBody SendMessageRequest request) {
        Map<String, Object> response = new HashMap<>();

        try {
            if (request.getQueueName() == null || request.getQueueName().trim().isEmpty()) {
                response.put("status", "error");
                response.put("message", "queueName is required");
                return ResponseEntity.badRequest().body(response);
            }

            jmsTemplate.send(request.getQueueName(), session -> {
                jakarta.jms.TextMessage message = session.createTextMessage(
                    request.getBody() != null ? request.getBody() : ""
                );

                if (request.getCorrelationId() != null && !request.getCorrelationId().trim().isEmpty()) {
                    message.setJMSCorrelationID(request.getCorrelationId());
                }

                if (request.getReplyToQueue() != null && !request.getReplyToQueue().trim().isEmpty()) {
                    message.setJMSReplyTo(session.createQueue(request.getReplyToQueue()));
                }

                if (request.getPersistent() != null && request.getPersistent()) {
                    message.setJMSDeliveryMode(DeliveryMode.PERSISTENT);
                }

                if (request.getHeaders() != null) {
                    for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
                        message.setStringProperty(entry.getKey(), entry.getValue());
                    }
                }

                return message;
            });

            response.put("status", "success");
            response.put("queueName", request.getQueueName());
            response.put("correlationId", request.getCorrelationId());
            response.put("replyToQueue", request.getReplyToQueue());

            logger.info("Message sent to queue '{}' via REST API (correlationId: {}, replyTo: {})",
                       request.getQueueName(), request.getCorrelationId(), request.getReplyToQueue());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to send message to queue '{}'", request.getQueueName(), e);
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    public static class SendMessageRequest {
        private String queueName;
        private String body;
        private String correlationId;
        private String replyToQueue;
        private Boolean persistent;
        private Map<String, String> headers;

        public String getQueueName() { return queueName; }
        public void setQueueName(String queueName) { this.queueName = queueName; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        public String getCorrelationId() { return correlationId; }
        public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
        public String getReplyToQueue() { return replyToQueue; }
        public void setReplyToQueue(String replyToQueue) { this.replyToQueue = replyToQueue; }
        public Boolean getPersistent() { return persistent; }
        public void setPersistent(Boolean persistent) { this.persistent = persistent; }
        public Map<String, String> getHeaders() { return headers; }
        public void setHeaders(Map<String, String> headers) { this.headers = headers; }
    }
}
