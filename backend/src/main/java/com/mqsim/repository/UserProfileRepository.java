package com.mqsim.repository;

import com.mqsim.model.UserProfile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserProfileRepository extends MongoRepository<UserProfile, String> {
    
    /**
     * Find user by userId (username)
     */
    Optional<UserProfile> findByUserId(String userId);
    
    /**
     * Find user by email
     */
    Optional<UserProfile> findByEmail(String email);
    
    /**
     * Find all active users
     */
    @Query("{'active': true, 'deleted': false}")
    List<UserProfile> findAllActiveUsers();
    
    /**
     * Find users by namespace
     */
    @Query("{'namespaces': ?0, 'active': true, 'deleted': false}")
    List<UserProfile> findByNamespace(String namespace);
    
    /**
     * Check if userId exists (for unique validation)
     */
    boolean existsByUserId(String userId);
    
    /**
     * Check if email exists (for unique validation)
     */
    boolean existsByEmail(String email);
    
    /**
     * Find users with any of the given namespaces
     */
    @Query("{'namespaces': {'$in': ?0}, 'active': true, 'deleted': false}")
    List<UserProfile> findByNamespacesIn(List<String> namespaces);
}