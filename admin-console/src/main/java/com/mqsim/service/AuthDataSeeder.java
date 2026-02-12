package com.mqsim.service;

import com.mqsim.model.UserProfile;
import com.mqsim.model.Namespace;
import com.mqsim.repository.UserProfileRepository;
import com.mqsim.repository.NamespaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Seeds initial authentication data for MQ Simulator
 */
@Service
public class AuthDataSeeder {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthDataSeeder.class);
    
    @Autowired
    private UserProfileRepository userProfileRepository;
    
    @Autowired
    private NamespaceRepository namespaceRepository;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private NamespaceService namespaceService;
    
    @EventListener(ApplicationReadyEvent.class)
    public void seedAuthData() {
        seedNamespaces();
        seedUsers();
    }
    
    private void seedNamespaces() {
        // Check if default namespace exists
        if (!namespaceRepository.existsByName("default")) {
            logger.info("Seeding default namespace...");
            
            Namespace defaultNamespace = new Namespace("default", "Default Workspace", "admin");
            defaultNamespace.setDescription("Default namespace for initial setup");
            namespaceRepository.save(defaultNamespace);
            
            logger.info("Created default namespace");
        }
        
        // Check if demo namespace exists
        if (!namespaceRepository.existsByName("demo")) {
            logger.info("Seeding demo namespace...");
            
            Namespace demoNamespace = new Namespace("demo", "Demo Environment", "admin");
            demoNamespace.setDescription("Demo namespace for testing and examples");
            namespaceRepository.save(demoNamespace);
            
            logger.info("Created demo namespace");
        }
    }
    
    private void seedUsers() {
        // Update existing users with missing roles
        fixExistingUserRoles();

        // Check if admin user exists
        if (!userProfileRepository.existsByUserId("admin")) {
            logger.info("Seeding admin user...");
            
            try {
                UserProfile admin = userService.createUser(
                    "admin", 
                    "admin@mqsim.local", 
                    "System", 
                    "Administrator", 
                    "admin123"
                );
                
                // Add admin to both namespaces and set role
                admin.addNamespace("default");
                admin.addNamespace("demo");
                admin.setDefaultNamespace("default");
                admin.setRole("admin");  // Set admin role
                userProfileRepository.save(admin);
                
                // Update namespace memberships
                namespaceRepository.findByName("default").ifPresent(ns -> {
                    ns.addMember("admin");
                    namespaceRepository.save(ns);
                });
                
                namespaceRepository.findByName("demo").ifPresent(ns -> {
                    ns.addMember("admin");
                    namespaceRepository.save(ns);
                });
                
                logger.info("Created admin user with access to default and demo namespaces");
                
            } catch (Exception e) {
                logger.error("Error creating admin user", e);
            }
        }
        
        // Check if demo user exists
        if (!userProfileRepository.existsByUserId("demo")) {
            logger.info("Seeding demo user...");
            
            try {
                UserProfile demo = userService.createUser(
                    "demo", 
                    "demo@mqsim.local", 
                    "Demo", 
                    "User", 
                    "demo123"
                );
                
                // Add demo user to demo namespace only and set role
                demo.addNamespace("demo");
                demo.setDefaultNamespace("demo");
                demo.setRole("user");  // Set user role
                userProfileRepository.save(demo);
                
                // Update namespace membership
                namespaceRepository.findByName("demo").ifPresent(ns -> {
                    ns.addMember("demo");
                    namespaceRepository.save(ns);
                });
                
                logger.info("Created demo user with access to demo namespace");
                
            } catch (Exception e) {
                logger.error("Error creating demo user", e);
            }
        }
        
        logger.info("Authentication data seeding completed");
        logger.info("Default login credentials:");
        logger.info("  Admin: admin / admin123 (access: default, demo)");
        logger.info("  Demo:  demo / demo123   (access: demo)");
    }

    private void fixExistingUserRoles() {
        try {
            // Fix admin user role if missing
            userProfileRepository.findByUserId("admin").ifPresent(admin -> {
                if (admin.getRole() == null || admin.getRole().isEmpty()) {
                    logger.info("Fixing admin user role...");
                    admin.setRole("admin");
                    userProfileRepository.save(admin);
                    logger.info("Updated admin user role to 'admin'");
                }
            });

            // Fix demo user role if missing
            userProfileRepository.findByUserId("demo").ifPresent(demo -> {
                if (demo.getRole() == null || demo.getRole().isEmpty()) {
                    logger.info("Fixing demo user role...");
                    demo.setRole("user");
                    userProfileRepository.save(demo);
                    logger.info("Updated demo user role to 'user'");
                }
            });

        } catch (Exception e) {
            logger.error("Error fixing existing user roles", e);
        }
    }
}