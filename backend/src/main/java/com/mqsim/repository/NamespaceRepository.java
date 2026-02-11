package com.mqsim.repository;

import com.mqsim.model.Namespace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NamespaceRepository extends JpaRepository<Namespace, String> {

    /**
     * Find namespace by name
     */
    Optional<Namespace> findByName(String name);

    /**
     * Find all active namespaces
     */
    @Query("SELECT n FROM Namespace n WHERE n.active = true AND n.deleted = false")
    List<Namespace> findAllActiveNamespaces();

    /**
     * Find namespaces by owner
     */
    @Query("SELECT n FROM Namespace n WHERE n.owner = :owner AND n.active = true AND n.deleted = false")
    List<Namespace> findByOwner(@Param("owner") String owner);

    /**
     * Find namespaces where user is a member - checks if JSONB array contains the value
     */
    @Query(value = "SELECT * FROM namespaces n WHERE n.members @> to_jsonb(:userId\\:\\:text) AND n.active = true AND n.deleted = false", nativeQuery = true)
    List<Namespace> findByMember(@Param("userId") String userId);

    /**
     * Check if namespace name exists
     */
    boolean existsByName(String name);

    /**
     * Find namespaces by names (for bulk operations)
     */
    @Query("SELECT n FROM Namespace n WHERE n.name IN :names AND n.active = true AND n.deleted = false")
    List<Namespace> findByNameIn(@Param("names") List<String> names);
}
