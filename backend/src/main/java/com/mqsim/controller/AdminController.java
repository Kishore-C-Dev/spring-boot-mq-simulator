package com.mqsim.controller;

import com.mqsim.model.UserProfile;
import com.mqsim.model.Namespace;
import com.mqsim.repository.UserProfileRepository;
import com.mqsim.repository.NamespaceRepository;
import com.mqsim.service.SessionManager;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final UserProfileRepository userProfileRepository;
    private final NamespaceRepository namespaceRepository;
    private final SessionManager sessionManager;

    @Autowired
    public AdminController(UserProfileRepository userProfileRepository,
                          NamespaceRepository namespaceRepository,
                          SessionManager sessionManager) {
        this.userProfileRepository = userProfileRepository;
        this.namespaceRepository = namespaceRepository;
        this.sessionManager = sessionManager;
    }

    private boolean isAdminUser(HttpSession session) {
        if (!sessionManager.isAuthenticated(session)) {
            return false;
        }
        UserProfile currentUser = sessionManager.getCurrentUser(session);
        if (currentUser != null) {
            System.out.println("DEBUG: Current user: " + currentUser.getUserId() + ", role: " + currentUser.getRole());
        }
        return currentUser != null && "admin".equals(currentUser.getRole());
    }

    @GetMapping("/debug/user")
    public ResponseEntity<?> debugUser(HttpSession session) {
        if (!sessionManager.isAuthenticated(session)) {
            return ResponseEntity.ok(Map.of("authenticated", false));
        }
        UserProfile currentUser = sessionManager.getCurrentUser(session);
        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", true);
        response.put("userId", currentUser != null ? currentUser.getUserId() : null);
        response.put("role", currentUser != null ? currentUser.getRole() : null);
        response.put("namespaces", currentUser != null ? currentUser.getNamespaces() : null);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users")
    public ResponseEntity<?> getUsers(HttpSession session) {
        if (!isAdminUser(session)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        List<UserProfile> users = userProfileRepository.findAll();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<?> getUser(@PathVariable String userId, HttpSession session) {
        if (!isAdminUser(session)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        Optional<UserProfile> user = userProfileRepository.findByUserId(userId);
        if (user.isPresent()) {
            return ResponseEntity.ok(user.get());
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> userData, HttpSession session) {
        if (!isAdminUser(session)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        try {
            String userId = (String) userData.get("userId");
            String password = (String) userData.get("password");
            String role = (String) userData.get("role");
            @SuppressWarnings("unchecked")
            List<String> namespaces = (List<String>) userData.get("namespaces");

            if (userId == null || password == null || role == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields"));
            }

            if (userProfileRepository.findByUserId(userId).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "User already exists"));
            }

            UserProfile newUser = new UserProfile();
            newUser.setUserId(userId);
            newUser.setPasswordHash(hashPassword(password));
            newUser.setRole(role);
            newUser.setNamespaces(namespaces != null ? namespaces : List.of());
            newUser.setCreatedAt(Instant.now());

            UserProfile savedUser = userProfileRepository.save(newUser);
            return ResponseEntity.ok(savedUser);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid request data"));
        }
    }

    @PutMapping("/users/{userId}")
    public ResponseEntity<?> updateUser(@PathVariable String userId, @RequestBody Map<String, Object> userData, HttpSession session) {
        if (!isAdminUser(session)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        try {
            Optional<UserProfile> existingUserOpt = userProfileRepository.findByUserId(userId);
            if (!existingUserOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            UserProfile existingUser = existingUserOpt.get();

            String password = (String) userData.get("password");
            String role = (String) userData.get("role");
            @SuppressWarnings("unchecked")
            List<String> namespaces = (List<String>) userData.get("namespaces");

            if (password != null && !password.trim().isEmpty()) {
                existingUser.setPasswordHash(hashPassword(password));
            }
            if (role != null) {
                existingUser.setRole(role);
            }
            if (namespaces != null) {
                existingUser.setNamespaces(namespaces);
            }

            UserProfile updatedUser = userProfileRepository.save(existingUser);
            return ResponseEntity.ok(updatedUser);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid request data"));
        }
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable String userId, HttpSession session) {
        if (!isAdminUser(session)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        Optional<UserProfile> user = userProfileRepository.findByUserId(userId);
        if (!user.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        if ("admin".equals(userId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete admin user"));
        }

        userProfileRepository.delete(user.get());
        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
    }

    @GetMapping("/namespaces")
    public ResponseEntity<?> getNamespaces(HttpSession session) {
        if (!isAdminUser(session)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        List<Namespace> namespaces = namespaceRepository.findAll();
        return ResponseEntity.ok(namespaces);
    }

    @GetMapping("/namespaces/{namespaceName}")
    public ResponseEntity<?> getNamespace(@PathVariable String namespaceName, HttpSession session) {
        if (!isAdminUser(session)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        Optional<Namespace> namespace = namespaceRepository.findByName(namespaceName);
        if (namespace.isPresent()) {
            return ResponseEntity.ok(namespace.get());
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/namespaces")
    public ResponseEntity<?> createNamespace(@RequestBody Map<String, Object> namespaceData, HttpSession session) {
        if (!isAdminUser(session)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        try {
            String name = (String) namespaceData.get("name");
            String description = (String) namespaceData.get("description");

            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Namespace name is required"));
            }

            if (namespaceRepository.findByName(name).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Namespace already exists"));
            }

            Namespace newNamespace = new Namespace();
            newNamespace.setName(name);
            newNamespace.setDescription(description);
            newNamespace.setCreatedAt(Instant.now());

            Namespace savedNamespace = namespaceRepository.save(newNamespace);
            return ResponseEntity.ok(savedNamespace);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid request data"));
        }
    }

    @PutMapping("/namespaces/{namespaceName}")
    public ResponseEntity<?> updateNamespace(@PathVariable String namespaceName, @RequestBody Map<String, Object> namespaceData, HttpSession session) {
        if (!isAdminUser(session)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        try {
            Optional<Namespace> existingNamespaceOpt = namespaceRepository.findByName(namespaceName);
            if (!existingNamespaceOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            Namespace existingNamespace = existingNamespaceOpt.get();

            String description = (String) namespaceData.get("description");

            if (description != null) {
                existingNamespace.setDescription(description);
            }

            Namespace updatedNamespace = namespaceRepository.save(existingNamespace);
            return ResponseEntity.ok(updatedNamespace);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid request data"));
        }
    }

    @DeleteMapping("/namespaces/{namespaceName}")
    public ResponseEntity<?> deleteNamespace(@PathVariable String namespaceName, HttpSession session) {
        if (!isAdminUser(session)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin access required"));
        }

        Optional<Namespace> namespace = namespaceRepository.findByName(namespaceName);
        if (!namespace.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        if ("default".equals(namespaceName)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cannot delete default namespace"));
        }

        namespaceRepository.delete(namespace.get());
        return ResponseEntity.ok(Map.of("message", "Namespace deleted successfully"));
    }

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
}