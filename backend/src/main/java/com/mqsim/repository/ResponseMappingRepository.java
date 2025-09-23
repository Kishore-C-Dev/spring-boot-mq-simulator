package com.mqsim.repository;

import com.mqsim.model.ResponseMapping;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResponseMappingRepository extends MongoRepository<ResponseMapping, String> {
    
    // Namespace-aware queries
    List<ResponseMapping> findByNamespaceAndQueueNameAndEnabledTrueOrderByPriorityAsc(String namespace, String queueName);
    
    List<ResponseMapping> findByNamespaceAndQueueName(String namespace, String queueName);
    
    List<ResponseMapping> findByNamespaceOrderByQueueNameAscPriorityAsc(String namespace);
    
    List<ResponseMapping> findByNamespace(String namespace);
    
    long countByNamespaceAndQueueName(String namespace, String queueName);
    
    // Legacy queries (for backward compatibility and migration)
    List<ResponseMapping> findByQueueNameAndEnabledTrueOrderByPriorityAsc(String queueName);
    
    List<ResponseMapping> findByQueueName(String queueName);
    
    List<ResponseMapping> findAllByOrderByQueueNameAscPriorityAsc();
    
    long countByQueueName(String queueName);
}