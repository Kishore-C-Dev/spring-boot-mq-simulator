package com.mqsim.controller;

import com.mqsim.model.UserProfile;
import com.mqsim.model.Namespace;
import com.mqsim.service.UserService;
import com.mqsim.service.SessionManager;
import com.mqsim.repository.NamespaceRepository;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Controller
public class AuthController {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private SessionManager sessionManager;
    
    @Autowired
    private NamespaceRepository namespaceRepository;
    
    /**
     * Show login page
     */
    @GetMapping("/login")
    public String loginPage(HttpSession session, Model model) {
        // If already authenticated, redirect to dashboard
        if (sessionManager.isAuthenticated(session)) {
            return "redirect:/ui";
        }
        return "login";
    }
    
    /**
     * Process login form
     */
    @PostMapping("/login")
    public String authenticate(@RequestParam String userId, 
                             @RequestParam String password,
                             HttpSession session,
                             RedirectAttributes redirectAttributes) {
        
        Optional<UserProfile> userOpt = userService.authenticate(userId, password);
        
        if (userOpt.isPresent()) {
            UserProfile user = userOpt.get();
            sessionManager.setCurrentUser(session, user);
            
            logger.info("Successful login: {} with namespace {}", 
                       userId, sessionManager.getCurrentNamespace(session));
            
            return "redirect:/ui";
        } else {
            redirectAttributes.addFlashAttribute("error", "Invalid username or password");
            return "redirect:/login";
        }
    }
    
    /**
     * Logout (GET)
     */
    @GetMapping("/logout")
    public String logoutGet(HttpSession session, RedirectAttributes redirectAttributes) {
        sessionManager.clearSession(session);
        redirectAttributes.addFlashAttribute("message", "You have been logged out successfully");
        return "redirect:/login";
    }

    /**
     * Logout (POST)
     */
    @PostMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes redirectAttributes) {
        sessionManager.clearSession(session);
        redirectAttributes.addFlashAttribute("message", "You have been logged out successfully");
        return "redirect:/login";
    }
    
    /**
     * Switch namespace
     */
    @PostMapping("/switch-namespace")
    public String switchNamespace(@RequestParam String namespace,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        
        if (sessionManager.hasNamespaceAccess(session, namespace)) {
            sessionManager.setCurrentNamespace(session, namespace);
            redirectAttributes.addFlashAttribute("message", "Switched to namespace: " + namespace);
        } else {
            redirectAttributes.addFlashAttribute("error", "Access denied to namespace: " + namespace);
        }
        
        return "redirect:/ui";
    }
    
    /**
     * Show user registration page (for demo/admin)
     */
    @GetMapping("/register")
    public String registerPage(Model model) {
        List<Namespace> namespaces = namespaceRepository.findAllActiveNamespaces();
        model.addAttribute("namespaces", namespaces);
        return "register";
    }
    
    /**
     * Process user registration
     */
    @PostMapping("/register")
    public String register(@RequestParam String userId,
                         @RequestParam String email,
                         @RequestParam String firstName,
                         @RequestParam String lastName,
                         @RequestParam String password,
                         @RequestParam String confirmPassword,
                         @RequestParam(required = false) String namespace,
                         RedirectAttributes redirectAttributes) {
        
        try {
            if (!password.equals(confirmPassword)) {
                redirectAttributes.addFlashAttribute("error", "Passwords do not match");
                return "redirect:/register";
            }
            
            if (password.length() < 6) {
                redirectAttributes.addFlashAttribute("error", "Password must be at least 6 characters");
                return "redirect:/register";
            }
            
            UserProfile user;
            if (namespace != null && !namespace.trim().isEmpty()) {
                user = userService.createUser(userId, email, firstName, lastName, password, namespace);
            } else {
                user = userService.createUser(userId, email, firstName, lastName, password);
            }
            
            redirectAttributes.addFlashAttribute("message", 
                "User " + userId + " created successfully. Please login.");
            
            return "redirect:/login";
            
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/register";
        } catch (Exception e) {
            logger.error("Error creating user: {}", userId, e);
            redirectAttributes.addFlashAttribute("error", "Error creating user. Please try again.");
            return "redirect:/register";
        }
    }
}