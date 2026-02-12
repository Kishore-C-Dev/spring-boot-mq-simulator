package com.mqsim.repository;

import com.mqsim.model.QueueConfig;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QueueConfigRepository extends JpaRepository<QueueConfig, String> {

    // Namespace-aware queries with soft delete filter
    @Query("SELECT q FROM QueueConfig q WHERE q.namespace = :namespace AND q.enabled = true AND q.deleted = false")
    List<QueueConfig> findByNamespaceAndEnabledTrue(@Param("namespace") String namespace);

    @Query("SELECT q FROM QueueConfig q WHERE q.namespace = :namespace AND q.queueName = :queueName AND q.deleted = false")
    Optional<QueueConfig> findByNamespaceAndQueueName(@Param("namespace") String namespace, @Param("queueName") String queueName);

    @Query("SELECT CASE WHEN COUNT(q) > 0 THEN true ELSE false END FROM QueueConfig q WHERE q.namespace = :namespace AND q.queueName = :queueName AND q.deleted = false")
    boolean existsByNamespaceAndQueueName(@Param("namespace") String namespace, @Param("queueName") String queueName);

    @Query("SELECT q FROM QueueConfig q WHERE q.namespace = :namespace AND q.deleted = false")
    List<QueueConfig> findByNamespaceOrderByQueueNameAsc(@Param("namespace") String namespace, Sort sort);

    @Query("SELECT q FROM QueueConfig q WHERE q.namespace = :namespace AND q.deleted = false")
    List<QueueConfig> findByNamespace(@Param("namespace") String namespace);

    // Legacy queries (for backward compatibility and migration) with soft delete filter
    @Query("SELECT q FROM QueueConfig q WHERE q.enabled = true AND q.deleted = false")
    List<QueueConfig> findByEnabledTrue();

    @Query("SELECT q FROM QueueConfig q WHERE q.queueName = :queueName AND q.deleted = false")
    Optional<QueueConfig> findByQueueName(@Param("queueName") String queueName);

    @Query("SELECT CASE WHEN COUNT(q) > 0 THEN true ELSE false END FROM QueueConfig q WHERE q.queueName = :queueName AND q.deleted = false")
    boolean existsByQueueName(@Param("queueName") String queueName);

    @Query("SELECT q FROM QueueConfig q WHERE q.deleted = false")
    List<QueueConfig> findAllByOrderByQueueNameAsc(Sort sort);

    // Additional soft delete query methods
    @Query("SELECT q FROM QueueConfig q WHERE q.deleted = false")
    List<QueueConfig> findAllNonDeleted();

    @Query("SELECT q FROM QueueConfig q WHERE q.deleted = false")
    List<QueueConfig> findAllNonDeleted(Sort sort);
}
