package com.mqsim.repository;

import com.mqsim.model.Namespace;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NamespaceRepository extends MongoRepository<Namespace, String> {
    
    /**
     * Find namespace by name
     */
    Optional<Namespace> findByName(String name);
    
    /**
     * Find all active namespaces
     */
    @Query("{'active': true, 'deleted': false}")
    List<Namespace> findAllActiveNamespaces();
    
    /**
     * Find namespaces by owner
     */
    @Query("{'owner': ?0, 'active': true, 'deleted': false}")
    List<Namespace> findByOwner(String owner);
    
    /**
     * Find namespaces where user is a member
     */
    @Query("{'members': ?0, 'active': true, 'deleted': false}")
    List<Namespace> findByMember(String userId);
    
    /**
     * Check if namespace name exists
     */
    boolean existsByName(String name);
    
    /**
     * Find namespaces by names (for bulk operations)
     */
    @Query("{'name': {'$in': ?0}, 'active': true, 'deleted': false}")
    List<Namespace> findByNameIn(List<String> names);
}