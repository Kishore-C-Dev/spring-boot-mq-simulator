package com.mqsim.repository;

import com.mqsim.model.QueueConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QueueConfigRepository extends MongoRepository<QueueConfig, String> {
    
    // Namespace-aware queries
    List<QueueConfig> findByNamespaceAndEnabledTrue(String namespace);
    
    Optional<QueueConfig> findByNamespaceAndQueueName(String namespace, String queueName);
    
    boolean existsByNamespaceAndQueueName(String namespace, String queueName);
    
    List<QueueConfig> findByNamespaceOrderByQueueNameAsc(String namespace);
    
    List<QueueConfig> findByNamespace(String namespace);
    
    // Legacy queries (for backward compatibility and migration)
    List<QueueConfig> findByEnabledTrue();
    
    Optional<QueueConfig> findByQueueName(String queueName);
    
    boolean existsByQueueName(String queueName);
    
    List<QueueConfig> findAllByOrderByQueueNameAsc();
}