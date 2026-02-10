package com.mqsim.controller;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.Connection;
import org.springframework.beans.factory.annotation.Autowired;
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

        // Check IBM MQ connection if available
        if (connectionFactory != null) {
            try (Connection connection = connectionFactory.createConnection()) {
                health.put("ibmmq", "UP");
            } catch (Exception e) {
                health.put("ibmmq", "DOWN");
                health.put("ibmmqError", e.getMessage());
            }
        } else {
            health.put("ibmmq", "DISABLED");
        }

        return health;
    }

    @GetMapping("/ready")
    public Map<String, Object> ready() {
        Map<String, Object> readiness = new HashMap<>();

        if (connectionFactory != null) {
            try (Connection connection = connectionFactory.createConnection()) {
                readiness.put("status", "READY");
            } catch (Exception e) {
                readiness.put("status", "NOT_READY");
                readiness.put("reason", "IBM MQ connection failed: " + e.getMessage());
            }
        } else {
            readiness.put("status", "READY");
            readiness.put("note", "IBM MQ disabled");
        }

        return readiness;
    }
}
