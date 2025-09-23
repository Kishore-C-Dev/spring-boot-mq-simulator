package com.mqsim.service;

import com.mqsim.model.UserProfile;
import com.mqsim.model.Namespace;
import com.mqsim.repository.UserProfileRepository;
import com.mqsim.repository.NamespaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    
    @Autowired
    private UserProfileRepository userProfileRepository;
    
    @Autowired
    private NamespaceRepository namespaceRepository;
    
    /**
     * Authenticate user with userId and password
     */
    public Optional<UserProfile> authenticate(String userId, String password) {
        try {
            Optional<UserProfile> userOpt = userProfileRepository.findByUserId(userId);
            
            if (userOpt.isPresent()) {
                UserProfile user = userOpt.get();
                
                if (!user.isActive() || user.isDeleted()) {
                    logger.warn("Login attempt for inactive/deleted user: {}", userId);
                    return Optional.empty();
                }
                
                String hashedPassword = hashPassword(password);
                if (hashedPassword.equals(user.getPasswordHash())) {
                    // Update last login time
                    user.setLastLogin(Instant.now());
                    userProfileRepository.save(user);
                    
                    logger.info("Successful login for user: {}", userId);
                    return Optional.of(user);
                } else {
                    logger.warn("Invalid password for user: {}", userId);
                }
            } else {
                logger.warn("Login attempt for non-existent user: {}", userId);
            }
            
        } catch (Exception e) {
            logger.error("Error during authentication for user: {}", userId, e);
        }
        
        return Optional.empty();
    }
    
    /**
     * Create a new user
     */
    public UserProfile createUser(String userId, String email, String firstName, String lastName, String password) {
        // Check if user already exists
        if (userProfileRepository.existsByUserId(userId)) {
            throw new IllegalArgumentException("User ID already exists: " + userId);
        }
        
        if (userProfileRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already exists: " + email);
        }
        
        UserProfile user = new UserProfile(userId, email, firstName, lastName);
        user.setPasswordHash(hashPassword(password));
        
        UserProfile savedUser = userProfileRepository.save(user);
        logger.info("Created new user: {}", userId);
        
        return savedUser;
    }
    
    /**
     * Create user and assign to namespace
     */
    public UserProfile createUser(String userId, String email, String firstName, String lastName, 
                                 String password, String namespaceName) {
        UserProfile user = createUser(userId, email, firstName, lastName, password);
        
        // Add to namespace if it exists
        Optional<Namespace> namespaceOpt = namespaceRepository.findByName(namespaceName);
        if (namespaceOpt.isPresent()) {
            Namespace namespace = namespaceOpt.get();
            user.addNamespace(namespaceName);
            namespace.addMember(userId);
            
            userProfileRepository.save(user);
            namespaceRepository.save(namespace);
            
            logger.info("Added user {} to namespace {}", userId, namespaceName);
        }
        
        return user;
    }
    
    /**
     * Update user profile
     */
    public UserProfile updateUser(UserProfile user) {
        UserProfile savedUser = userProfileRepository.save(user);
        logger.info("Updated user profile: {}", user.getUserId());
        return savedUser;
    }
    
    /**
     * Change user password
     */
    public boolean changePassword(String userId, String oldPassword, String newPassword) {
        Optional<UserProfile> userOpt = authenticate(userId, oldPassword);
        if (userOpt.isPresent()) {
            UserProfile user = userOpt.get();
            user.setPasswordHash(hashPassword(newPassword));
            userProfileRepository.save(user);
            logger.info("Password changed for user: {}", userId);
            return true;
        }
        return false;
    }
    
    /**
     * Add user to namespace
     */
    public boolean addUserToNamespace(String userId, String namespaceName) {
        Optional<UserProfile> userOpt = userProfileRepository.findByUserId(userId);
        Optional<Namespace> namespaceOpt = namespaceRepository.findByName(namespaceName);
        
        if (userOpt.isPresent() && namespaceOpt.isPresent()) {
            UserProfile user = userOpt.get();
            Namespace namespace = namespaceOpt.get();
            
            user.addNamespace(namespaceName);
            namespace.addMember(userId);
            
            userProfileRepository.save(user);
            namespaceRepository.save(namespace);
            
            logger.info("Added user {} to namespace {}", userId, namespaceName);
            return true;
        }
        
        return false;
    }
    
    /**
     * Remove user from namespace
     */
    public boolean removeUserFromNamespace(String userId, String namespaceName) {
        Optional<UserProfile> userOpt = userProfileRepository.findByUserId(userId);
        Optional<Namespace> namespaceOpt = namespaceRepository.findByName(namespaceName);
        
        if (userOpt.isPresent() && namespaceOpt.isPresent()) {
            UserProfile user = userOpt.get();
            Namespace namespace = namespaceOpt.get();
            
            user.removeNamespace(namespaceName);
            namespace.removeMember(userId);
            
            userProfileRepository.save(user);
            namespaceRepository.save(namespace);
            
            logger.info("Removed user {} from namespace {}", userId, namespaceName);
            return true;
        }
        
        return false;
    }
    
    /**
     * Get all users in a namespace
     */
    public List<UserProfile> getUsersInNamespace(String namespaceName) {
        return userProfileRepository.findByNamespace(namespaceName);
    }
    
    /**
     * Hash password using SHA-256
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashedBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error hashing password", e);
        }
    }
    
    /**
     * Find user by ID
     */
    public Optional<UserProfile> findByUserId(String userId) {
        return userProfileRepository.findByUserId(userId);
    }
    
    /**
     * Find user by email
     */
    public Optional<UserProfile> findByEmail(String email) {
        return userProfileRepository.findByEmail(email);
    }
    
    /**
     * Get all active users
     */
    public List<UserProfile> getAllActiveUsers() {
        return userProfileRepository.findAllActiveUsers();
    }
    
    /**
     * Deactivate user (soft delete)
     */
    public boolean deactivateUser(String userId) {
        Optional<UserProfile> userOpt = userProfileRepository.findByUserId(userId);
        if (userOpt.isPresent()) {
            UserProfile user = userOpt.get();
            user.setActive(false);
            userProfileRepository.save(user);
            logger.info("Deactivated user: {}", userId);
            return true;
        }
        return false;
    }
}