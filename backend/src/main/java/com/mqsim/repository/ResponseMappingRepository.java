package com.mqsim.repository;

import com.mqsim.model.ResponseMapping;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResponseMappingRepository extends MongoRepository<ResponseMapping, String> {
    
    // Namespace-aware queries with soft delete filter
    @Query("{ 'namespace': ?0, 'queueName': ?1, 'enabled': true, 'deleted': { $ne: true } }")
    List<ResponseMapping> findByNamespaceAndQueueNameAndEnabledTrueOrderByPriorityAsc(String namespace, String queueName, Sort sort);

    @Query("{ 'namespace': ?0, 'queueName': ?1, 'deleted': { $ne: true } }")
    List<ResponseMapping> findByNamespaceAndQueueName(String namespace, String queueName);

    @Query("{ 'namespace': ?0, 'deleted': { $ne: true } }")
    List<ResponseMapping> findByNamespaceOrderByQueueNameAscPriorityAsc(String namespace, Sort sort);

    @Query("{ 'namespace': ?0, 'deleted': { $ne: true } }")
    List<ResponseMapping> findByNamespace(String namespace);

    @Query(value = "{ 'namespace': ?0, 'queueName': ?1, 'deleted': { $ne: true } }", count = true)
    long countByNamespaceAndQueueName(String namespace, String queueName);
    
    // Legacy queries (for backward compatibility and migration) with soft delete filter
    @Query("{ 'queueName': ?0, 'enabled': true, 'deleted': { $ne: true } }")
    List<ResponseMapping> findByQueueNameAndEnabledTrueOrderByPriorityAsc(String queueName, Sort sort);

    @Query("{ 'queueName': ?0, 'deleted': { $ne: true } }")
    List<ResponseMapping> findByQueueName(String queueName);

    @Query("{ 'deleted': { $ne: true } }")
    List<ResponseMapping> findAllByOrderByQueueNameAscPriorityAsc(Sort sort);

    @Query(value = "{ 'queueName': ?0, 'deleted': { $ne: true } }", count = true)
    long countByQueueName(String queueName);

    // Additional soft delete query methods
    @Query("{ 'deleted': { $ne: true } }")
    List<ResponseMapping> findAllNonDeleted();

    @Query("{ 'deleted': { $ne: true } }")
    List<ResponseMapping> findAllNonDeleted(Sort sort);
}