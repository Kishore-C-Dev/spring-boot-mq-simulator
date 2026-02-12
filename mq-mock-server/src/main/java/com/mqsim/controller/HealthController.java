package com.mqsim.controller;

import com.mqsim.config.NamespaceConfig;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Connection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.HashMap;

@RestController
public class HealthController {

    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    private NamespaceConfig namespaceConfig;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "mq-mock-server");
        health.put("namespace", namespaceConfig.getNamespace());
        health.put("timestamp", System.currentTimeMillis());

        try (Connection connection = connectionFactory.createConnection()) {
            health.put("ibmmq", "UP");
        } catch (Exception e) {
            health.put("ibmmq", "DOWN");
            health.put("ibmmqError", e.getMessage());
        }

        return health;
    }

    @GetMapping("/ready")
    public Map<String, Object> ready() {
        Map<String, Object> readiness = new HashMap<>();
        readiness.put("namespace", namespaceConfig.getNamespace());

        try (Connection connection = connectionFactory.createConnection()) {
            readiness.put("status", "READY");
        } catch (Exception e) {
            readiness.put("status", "NOT_READY");
            readiness.put("reason", "IBM MQ connection failed: " + e.getMessage());
        }

        return readiness;
    }
}
