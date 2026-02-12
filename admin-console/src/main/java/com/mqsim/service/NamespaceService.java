package com.mqsim.service;

import com.mqsim.model.Namespace;
import com.mqsim.model.UserProfile;
import com.mqsim.repository.NamespaceRepository;
import com.mqsim.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class NamespaceService {
    
    private static final Logger logger = LoggerFactory.getLogger(NamespaceService.class);
    
    @Autowired
    private NamespaceRepository namespaceRepository;
    
    @Autowired
    private UserProfileRepository userProfileRepository;
    
    /**
     * Create a new namespace
     */
    public Namespace createNamespace(String name, String displayName, String description, String owner) {
        // Check if namespace already exists
        if (namespaceRepository.existsByName(name)) {
            throw new IllegalArgumentException("Namespace already exists: " + name);
        }
        
        // Validate name format (alphanumeric, dashes, underscores)
        if (!name.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("Namespace name can only contain letters, numbers, dashes, and underscores");
        }
        
        Namespace namespace = new Namespace(name, displayName, owner);
        namespace.setDescription(description);
        
        Namespace savedNamespace = namespaceRepository.save(namespace);
        
        // Add namespace to owner's profile
        Optional<UserProfile> ownerOpt = userProfileRepository.findByUserId(owner);
        if (ownerOpt.isPresent()) {
            UserProfile ownerUser = ownerOpt.get();
            ownerUser.addNamespace(name);
            userProfileRepository.save(ownerUser);
        }
        
        logger.info("Created namespace {} owned by {}", name, owner);
        return savedNamespace;
    }
    
    /**
     * Get namespace by name
     */
    public Optional<Namespace> findByName(String name) {
        return namespaceRepository.findByName(name);
    }
    
    /**
     * Get all active namespaces
     */
    public List<Namespace> getAllActiveNamespaces() {
        return namespaceRepository.findAllActiveNamespaces();
    }
    
    /**
     * Get namespaces owned by user
     */
    public List<Namespace> getNamespacesByOwner(String owner) {
        return namespaceRepository.findByOwner(owner);
    }
    
    /**
     * Get namespaces where user is a member
     */
    public List<Namespace> getNamespacesByMember(String userId) {
        return namespaceRepository.findByMember(userId);
    }
    
    /**
     * Add member to namespace
     */
    public boolean addMember(String namespaceName, String userId) {
        Optional<Namespace> namespaceOpt = namespaceRepository.findByName(namespaceName);
        Optional<UserProfile> userOpt = userProfileRepository.findByUserId(userId);
        
        if (namespaceOpt.isPresent() && userOpt.isPresent()) {
            Namespace namespace = namespaceOpt.get();
            UserProfile user = userOpt.get();
            
            namespace.addMember(userId);
            user.addNamespace(namespaceName);
            
            namespaceRepository.save(namespace);
            userProfileRepository.save(user);
            
            logger.info("Added {} to namespace {}", userId, namespaceName);
            return true;
        }
        
        return false;
    }
    
    /**
     * Remove member from namespace
     */
    public boolean removeMember(String namespaceName, String userId) {
        Optional<Namespace> namespaceOpt = namespaceRepository.findByName(namespaceName);
        Optional<UserProfile> userOpt = userProfileRepository.findByUserId(userId);
        
        if (namespaceOpt.isPresent() && userOpt.isPresent()) {
            Namespace namespace = namespaceOpt.get();
            UserProfile user = userOpt.get();
            
            // Don't allow removing the owner
            if (namespace.isOwner(userId)) {
                throw new IllegalArgumentException("Cannot remove namespace owner. Transfer ownership first.");
            }
            
            namespace.removeMember(userId);
            user.removeNamespace(namespaceName);
            
            namespaceRepository.save(namespace);
            userProfileRepository.save(user);
            
            logger.info("Removed {} from namespace {}", userId, namespaceName);
            return true;
        }
        
        return false;
    }
    
    /**
     * Update namespace
     */
    public Namespace updateNamespace(Namespace namespace) {
        Namespace savedNamespace = namespaceRepository.save(namespace);
        logger.info("Updated namespace: {}", namespace.getName());
        return savedNamespace;
    }
    
    /**
     * Transfer ownership of namespace
     */
    public boolean transferOwnership(String namespaceName, String newOwner) {
        Optional<Namespace> namespaceOpt = namespaceRepository.findByName(namespaceName);
        Optional<UserProfile> newOwnerOpt = userProfileRepository.findByUserId(newOwner);
        
        if (namespaceOpt.isPresent() && newOwnerOpt.isPresent()) {
            Namespace namespace = namespaceOpt.get();
            
            // Ensure new owner is a member
            if (!namespace.hasMember(newOwner)) {
                namespace.addMember(newOwner);
                UserProfile newOwnerUser = newOwnerOpt.get();
                newOwnerUser.addNamespace(namespaceName);
                userProfileRepository.save(newOwnerUser);
            }
            
            String oldOwner = namespace.getOwner();
            namespace.setOwner(newOwner);
            namespaceRepository.save(namespace);
            
            logger.info("Transferred ownership of namespace {} from {} to {}", namespaceName, oldOwner, newOwner);
            return true;
        }
        
        return false;
    }
    
    /**
     * Deactivate namespace (soft delete)
     */
    public boolean deactivateNamespace(String namespaceName) {
        Optional<Namespace> namespaceOpt = namespaceRepository.findByName(namespaceName);
        if (namespaceOpt.isPresent()) {
            Namespace namespace = namespaceOpt.get();
            namespace.setActive(false);
            namespaceRepository.save(namespace);
            
            // Remove namespace from all users
            List<UserProfile> users = userProfileRepository.findByNamespace(namespaceName);
            for (UserProfile user : users) {
                user.removeNamespace(namespaceName);
                userProfileRepository.save(user);
            }
            
            logger.info("Deactivated namespace: {}", namespaceName);
            return true;
        }
        return false;
    }
    
    /**
     * Check if namespace name is available
     */
    public boolean isNamespaceNameAvailable(String name) {
        return !namespaceRepository.existsByName(name);
    }
}