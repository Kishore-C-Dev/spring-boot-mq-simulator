package com.mqsim.repository;

import com.mqsim.model.ResponseMapping;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResponseMappingRepository extends JpaRepository<ResponseMapping, String> {

    // Namespace-aware queries with soft delete filter
    @Query("SELECT r FROM ResponseMapping r WHERE r.namespace = :namespace AND r.queueName = :queueName AND r.enabled = true AND r.deleted = false")
    List<ResponseMapping> findByNamespaceAndQueueNameAndEnabledTrueOrderByPriorityAsc(@Param("namespace") String namespace, @Param("queueName") String queueName, Sort sort);

    @Query("SELECT r FROM ResponseMapping r WHERE r.namespace = :namespace AND r.queueName = :queueName AND r.deleted = false")
    List<ResponseMapping> findByNamespaceAndQueueName(@Param("namespace") String namespace, @Param("queueName") String queueName);

    @Query("SELECT r FROM ResponseMapping r WHERE r.namespace = :namespace AND r.deleted = false")
    List<ResponseMapping> findByNamespaceOrderByQueueNameAscPriorityAsc(@Param("namespace") String namespace, Sort sort);

    @Query("SELECT r FROM ResponseMapping r WHERE r.namespace = :namespace AND r.deleted = false")
    List<ResponseMapping> findByNamespace(@Param("namespace") String namespace);

    @Query("SELECT COUNT(r) FROM ResponseMapping r WHERE r.namespace = :namespace AND r.queueName = :queueName AND r.deleted = false")
    long countByNamespaceAndQueueName(@Param("namespace") String namespace, @Param("queueName") String queueName);

    // Legacy queries (for backward compatibility and migration) with soft delete filter
    @Query("SELECT r FROM ResponseMapping r WHERE r.queueName = :queueName AND r.enabled = true AND r.deleted = false")
    List<ResponseMapping> findByQueueNameAndEnabledTrueOrderByPriorityAsc(@Param("queueName") String queueName, Sort sort);

    @Query("SELECT r FROM ResponseMapping r WHERE r.queueName = :queueName AND r.deleted = false")
    List<ResponseMapping> findByQueueName(@Param("queueName") String queueName);

    @Query("SELECT r FROM ResponseMapping r WHERE r.deleted = false")
    List<ResponseMapping> findAllByOrderByQueueNameAscPriorityAsc(Sort sort);

    @Query("SELECT COUNT(r) FROM ResponseMapping r WHERE r.queueName = :queueName AND r.deleted = false")
    long countByQueueName(@Param("queueName") String queueName);

    // Additional soft delete query methods
    @Query("SELECT r FROM ResponseMapping r WHERE r.deleted = false")
    List<ResponseMapping> findAllNonDeleted();

    @Query("SELECT r FROM ResponseMapping r WHERE r.deleted = false")
    List<ResponseMapping> findAllNonDeleted(Sort sort);
}
