package com.mqsim.repository;

import com.mqsim.model.QueueConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QueueConfigRepository extends MongoRepository<QueueConfig, String> {
    
    List<QueueConfig> findByEnabledTrue();
    
    Optional<QueueConfig> findByQueueName(String queueName);
    
    boolean existsByQueueName(String queueName);
    
    List<QueueConfig> findAllByOrderByQueueNameAsc();
}