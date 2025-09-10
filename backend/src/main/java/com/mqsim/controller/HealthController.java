package com.mqsim.controller;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.HashMap;

@RestController
public class HealthController {

    @Autowired(required = false)
    private ConnectionFactory connectionFactory;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        
        // Check RabbitMQ connection if available
        if (connectionFactory != null) {
            try {
                connectionFactory.createConnection().close();
                health.put("rabbitmq", "UP");
            } catch (Exception e) {
                health.put("rabbitmq", "DOWN");
                health.put("rabbitmqError", e.getMessage());
            }
        } else {
            health.put("rabbitmq", "DISABLED");
        }
        
        return health;
    }

    @GetMapping("/ready")
    public Map<String, Object> ready() {
        Map<String, Object> readiness = new HashMap<>();
        
        if (connectionFactory != null) {
            try {
                connectionFactory.createConnection().close();
                readiness.put("status", "READY");
            } catch (Exception e) {
                readiness.put("status", "NOT_READY");
                readiness.put("reason", "RabbitMQ connection failed: " + e.getMessage());
            }
        } else {
            readiness.put("status", "READY");
            readiness.put("note", "RabbitMQ disabled");
        }
        
        return readiness;
    }
}