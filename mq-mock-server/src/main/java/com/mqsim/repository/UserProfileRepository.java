package com.mqsim.repository;

import com.mqsim.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, String> {

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
    @Query("SELECT u FROM UserProfile u WHERE u.active = true AND u.deleted = false")
    List<UserProfile> findAllActiveUsers();

    /**
     * Find users by namespace - checks if JSONB array contains the value
     */
    @Query(value = "SELECT * FROM user_profiles u WHERE u.namespaces @> to_jsonb(:namespace\\:\\:text) AND u.active = true AND u.deleted = false", nativeQuery = true)
    List<UserProfile> findByNamespace(@Param("namespace") String namespace);

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
    @Query(value = "SELECT DISTINCT u.* FROM user_profiles u WHERE EXISTS (SELECT 1 FROM jsonb_array_elements_text(u.namespaces) AS ns WHERE ns IN (:namespaces)) AND u.active = true AND u.deleted = false", nativeQuery = true)
    List<UserProfile> findByNamespacesIn(@Param("namespaces") List<String> namespaces);
}
