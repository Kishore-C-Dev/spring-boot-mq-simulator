package com.mqsim.controller;

import com.mqsim.model.QueueConfig;
import com.mqsim.repository.QueueConfigRepository;
import com.mqsim.service.DynamicListenerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Sort;

@RestController
@RequestMapping("/admin/queues")
@Validated
public class QueueController {

    private static final Logger logger = LoggerFactory.getLogger(QueueController.class);

    private final QueueConfigRepository queueConfigRepository;
    private final DynamicListenerService dynamicListenerService;

    @Autowired
    public QueueController(QueueConfigRepository queueConfigRepository, 
                          @Autowired(required = false) DynamicListenerService dynamicListenerService) {
        this.queueConfigRepository = queueConfigRepository;
        this.dynamicListenerService = dynamicListenerService;
    }

    @GetMapping
    public List<QueueConfig> getAllQueues() {
        return queueConfigRepository.findAllByOrderByQueueNameAsc( Sort.by(Sort.Direction.ASC, "queueName"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<QueueConfig> getQueue(@PathVariable String id) {
        Optional<QueueConfig> queueConfig = queueConfigRepository.findById(id);
        return queueConfig.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createQueue(@Valid @RequestBody QueueConfig queueConfig) {
        try {
            logger.debug("Received queue creation request: {}", queueConfig.toString());

            // Ensure ID is null so MongoDB can generate it
            queueConfig.setId(null);

            // Check for duplicate queue name
            if (queueConfigRepository.existsByQueueName(queueConfig.getQueueName())) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Queue name already exists: " + queueConfig.getQueueName()));
            }

            QueueConfig savedQueue = queueConfigRepository.save(queueConfig);
            logger.info("Created queue configuration: {} with ID: {}", savedQueue.getQueueName(), savedQueue.getId());
            logger.debug("Saved queue object: {}", savedQueue.toString());

            // Refresh listeners if the new queue is enabled
            if (Boolean.TRUE.equals(savedQueue.getEnabled()) && dynamicListenerService != null) {
                dynamicListenerService.refreshListeners();
            }

            return ResponseEntity.ok(savedQueue);
        } catch (Exception e) {
            logger.error("Error creating queue configuration", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Failed to create queue: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateQueue(@PathVariable String id, @Valid @RequestBody QueueConfig queueConfig) {
        try {
            if (!queueConfigRepository.existsById(id)) {
                return ResponseEntity.notFound().build();
            }

            queueConfig.setId(id);
            QueueConfig updatedQueue = queueConfigRepository.save(queueConfig);
            logger.info("Updated queue configuration: {}", updatedQueue.getQueueName());

            // Refresh listeners after update
            if (dynamicListenerService != null) {
                dynamicListenerService.refreshListeners();
            }

            return ResponseEntity.ok(updatedQueue);
        } catch (Exception e) {
            logger.error("Error updating queue configuration", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Failed to update queue: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteQueue(@PathVariable String id) {
        try {
            Optional<QueueConfig> queueConfig = queueConfigRepository.findById(id);
            if (queueConfig.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            QueueConfig configToDelete = queueConfig.get();
            configToDelete.setDeleted(true);
            queueConfigRepository.save(configToDelete);
            logger.info("Soft deleted queue configuration: {}", configToDelete.getQueueName());

            // Refresh listeners after deletion
            if (dynamicListenerService != null) {
                dynamicListenerService.refreshListeners();
            }

            return ResponseEntity.ok(Map.of("message", "Queue deleted successfully"));
        } catch (Exception e) {
            logger.error("Error deleting queue configuration", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Failed to delete queue: " + e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshListeners() {
        try {
            if (dynamicListenerService == null) {
                return ResponseEntity.ok(Map.of(
                    "message", "MQ not enabled - no listeners to refresh",
                    "activeListeners", 0,
                    "listenerStatus", Map.of()
                ));
            }
            
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
    public ResponseEntity<Map<String, Object>> getListenerStatus() {
        if (dynamicListenerService == null) {
            return ResponseEntity.ok(Map.of(
                "activeListeners", 0,
                "listenerStatus", Map.of(),
                "message", "MQ not enabled"
            ));
        }
        
        Map<String, String> listenerStatus = dynamicListenerService.getListenerStatus();
        return ResponseEntity.ok(Map.of(
            "activeListeners", listenerStatus.size(),
            "listenerStatus", listenerStatus
        ));
    }
}