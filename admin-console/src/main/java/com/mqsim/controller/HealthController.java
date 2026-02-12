package com.mqsim.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.HashMap;

@RestController
public class HealthController {

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "admin-console");
        health.put("timestamp", System.currentTimeMillis());
        return health;
    }

    @GetMapping("/ready")
    public Map<String, Object> ready() {
        Map<String, Object> readiness = new HashMap<>();
        readiness.put("status", "READY");
        readiness.put("service", "admin-console");
        return readiness;
    }
}
