package com.mqsim.repository;

import com.mqsim.model.QueueConfig;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QueueConfigRepository extends MongoRepository<QueueConfig, String> {

    // Namespace-aware queries with soft delete filter
    @Query("{ 'namespace': ?0, 'enabled': true, 'deleted': { $ne: true } }")
    List<QueueConfig> findByNamespaceAndEnabledTrue(String namespace);

    @Query("{ 'namespace': ?0, 'queueName': ?1, 'deleted': { $ne: true } }")
    Optional<QueueConfig> findByNamespaceAndQueueName(String namespace, String queueName);

    @Query(value = "{ 'namespace': ?0, 'queueName': ?1, 'deleted': { $ne: true } }", exists = true)
    boolean existsByNamespaceAndQueueName(String namespace, String queueName);

    @Query("{ 'namespace': ?0, 'deleted': { $ne: true } }")
    List<QueueConfig> findByNamespaceOrderByQueueNameAsc(String namespace, Sort sort);

    @Query("{ 'namespace': ?0, 'deleted': { $ne: true } }")
    List<QueueConfig> findByNamespace(String namespace);

    // Legacy queries (for backward compatibility and migration) with soft delete filter
    @Query("{ 'enabled': true, 'deleted': { $ne: true } }")
    List<QueueConfig> findByEnabledTrue();

    @Query("{ 'queueName': ?0, 'deleted': { $ne: true } }")
    Optional<QueueConfig> findByQueueName(String queueName);

    @Query(value = "{ 'queueName': ?0, 'deleted': { $ne: true } }", exists = true)
    boolean existsByQueueName(String queueName);

    @Query("{ 'deleted': { $ne: true } }")
    List<QueueConfig> findAllByOrderByQueueNameAsc(Sort sort);

    // Additional soft delete query methods
    @Query("{ 'deleted': { $ne: true } }")
    List<QueueConfig> findAllNonDeleted();

    @Query("{ 'deleted': { $ne: true } }")
    List<QueueConfig> findAllNonDeleted(Sort sort);
}