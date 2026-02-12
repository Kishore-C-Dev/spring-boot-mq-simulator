package com.mqsim.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MockServerNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(MockServerNotificationService.class);

    private final List<String> mockServerUrls;
    private final HttpClient httpClient;

    public MockServerNotificationService(
            @Value("${mqsim.mock-server.urls:}") String urls) {
        if (urls != null && !urls.isBlank()) {
            this.mockServerUrls = List.of(urls.split(","));
        } else {
            this.mockServerUrls = Collections.emptyList();
        }
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Async
    public void notifyRefreshAsync() {
        for (String url : mockServerUrls) {
            try {
                String endpoint = url.trim() + "/refresh";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .timeout(Duration.ofSeconds(10))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                logger.info("Notified mock server {} to refresh, status: {}", url.trim(), response.statusCode());
            } catch (Exception e) {
                logger.warn("Failed to notify mock server {} to refresh: {}", url.trim(), e.getMessage());
            }
        }
    }

    public Map<String, Object> refreshAllServers() {
        Map<String, Object> results = new HashMap<>();
        for (String url : mockServerUrls) {
            try {
                String endpoint = url.trim() + "/refresh";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .timeout(Duration.ofSeconds(10))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                results.put(url.trim(), Map.of("status", response.statusCode(), "body", response.body()));
            } catch (Exception e) {
                results.put(url.trim(), Map.of("status", "error", "message", e.getMessage()));
            }
        }
        return results;
    }

    public Map<String, Object> getStatusFromAllServers() {
        Map<String, Object> results = new HashMap<>();
        for (String url : mockServerUrls) {
            try {
                String endpoint = url.trim() + "/status";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .GET()
                        .timeout(Duration.ofSeconds(10))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                results.put(url.trim(), Map.of("status", response.statusCode(), "body", response.body()));
            } catch (Exception e) {
                results.put(url.trim(), Map.of("status", "error", "message", e.getMessage()));
            }
        }
        return results;
    }

    public Map<String, String> getListenerStatus() {
        Map<String, String> combinedStatus = new HashMap<>();
        for (String url : mockServerUrls) {
            try {
                String endpoint = url.trim() + "/status";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .GET()
                        .timeout(Duration.ofSeconds(5))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    combinedStatus.put(url.trim(), "Connected - " + response.body());
                } else {
                    combinedStatus.put(url.trim(), "Error: HTTP " + response.statusCode());
                }
            } catch (Exception e) {
                combinedStatus.put(url.trim(), "Unreachable: " + e.getMessage());
            }
        }
        return combinedStatus;
    }
}
