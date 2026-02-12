package com.mqsim.controller;

import com.mqsim.service.DynamicListenerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class RefreshController {

    private static final Logger logger = LoggerFactory.getLogger(RefreshController.class);

    private final DynamicListenerService dynamicListenerService;

    @Autowired
    public RefreshController(DynamicListenerService dynamicListenerService) {
        this.dynamicListenerService = dynamicListenerService;
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh() {
        try {
            dynamicListenerService.refreshListeners();
            Map<String, String> listenerStatus = dynamicListenerService.getListenerStatus();

            return ResponseEntity.ok(Map.of(
                "message", "Listeners refreshed successfully",
                "activeListeners", listenerStatus.size(),
                "listenerStatus", listenerStatus
            ));
        } catch (Exception e) {
            logger.error("Error refreshing listeners", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Failed to refresh listeners: " + e.getMessage()));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, String> listenerStatus = dynamicListenerService.getListenerStatus();
        return ResponseEntity.ok(Map.of(
            "activeListeners", listenerStatus.size(),
            "listenerStatus", listenerStatus
        ));
    }
}
