package com.mqsim.controller;

import com.mqsim.model.ResponseMapping;
import com.mqsim.repository.ResponseMappingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Sort;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/admin/mappings")
@Validated
public class MappingController {

    private static final Logger logger = LoggerFactory.getLogger(MappingController.class);

    private final ResponseMappingRepository responseMappingRepository;

    @Autowired
    public MappingController(ResponseMappingRepository responseMappingRepository) {
        this.responseMappingRepository = responseMappingRepository;
    }

    @GetMapping
    public List<ResponseMapping> getAllMappings() {
        return responseMappingRepository.findAllByOrderByQueueNameAscPriorityAsc(Sort.by("queueName").ascending()
                    .and(Sort.by("priority").ascending()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResponseMapping> getMapping(@PathVariable String id) {
        Optional<ResponseMapping> mapping = responseMappingRepository.findById(id);
        return mapping.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/queue/{queueName}")
    public List<ResponseMapping> getMappingsByQueue(@PathVariable String queueName) {
        return responseMappingRepository.findByQueueName(queueName);
    }

    @PostMapping
    public ResponseEntity<?> createMapping(@Valid @RequestBody ResponseMapping mapping) {
        try {
            ResponseMapping savedMapping = responseMappingRepository.save(mapping);
            logger.info("Created response mapping: {} for queue: {}", savedMapping.getId(), savedMapping.getQueueName());
            return ResponseEntity.ok(savedMapping);
        } catch (Exception e) {
            logger.error("Error creating response mapping", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Failed to create mapping: " + e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateMapping(@PathVariable String id, @Valid @RequestBody ResponseMapping mapping) {
        try {
            if (!responseMappingRepository.existsById(id)) {
                return ResponseEntity.notFound().build();
            }

            mapping.setId(id);
            ResponseMapping updatedMapping = responseMappingRepository.save(mapping);
            logger.info("Updated response mapping: {} for queue: {}", updatedMapping.getId(), updatedMapping.getQueueName());
            return ResponseEntity.ok(updatedMapping);
        } catch (Exception e) {
            logger.error("Error updating response mapping", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Failed to update mapping: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMapping(@PathVariable String id) {
        try {
            Optional<ResponseMapping> mapping = responseMappingRepository.findById(id);
            if (mapping.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            ResponseMapping mappingToDelete = mapping.get();
            mappingToDelete.setDeleted(true);
            responseMappingRepository.save(mappingToDelete);
            logger.info("Soft deleted response mapping: {} for queue: {}", mappingToDelete.getId(), mappingToDelete.getQueueName());
            return ResponseEntity.ok(Map.of("message", "Mapping deleted successfully"));
        } catch (Exception e) {
            logger.error("Error deleting response mapping", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Failed to delete mapping: " + e.getMessage()));
        }
    }

    @PostMapping("/import")
    public ResponseEntity<?> importMappings(@RequestBody List<ResponseMapping> mappings) {
        try {
            List<ResponseMapping> savedMappings = responseMappingRepository.saveAll(mappings);
            logger.info("Imported {} response mappings", savedMappings.size());
            return ResponseEntity.ok(Map.of(
                "message", "Mappings imported successfully",
                "count", savedMappings.size()
            ));
        } catch (Exception e) {
            logger.error("Error importing mappings", e);
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Failed to import mappings: " + e.getMessage()));
        }
    }

    @GetMapping("/export")
    public List<ResponseMapping> exportMappings() {
        return responseMappingRepository.findAll();
    }
}