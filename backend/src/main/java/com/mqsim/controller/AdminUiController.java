package com.mqsim.controller;

import com.mqsim.model.UserProfile;
import com.mqsim.model.Namespace;
import com.mqsim.repository.UserProfileRepository;
import com.mqsim.repository.NamespaceRepository;
import com.mqsim.service.SessionManager;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminUiController {

    private final UserProfileRepository userProfileRepository;
    private final NamespaceRepository namespaceRepository;
    private final SessionManager sessionManager;

    @Autowired
    public AdminUiController(UserProfileRepository userProfileRepository,
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
        return currentUser != null && "admin".equals(currentUser.getRole());
    }

    @GetMapping("/ui")
    public String adminDashboard(HttpSession session, Model model) {
        // Check authentication and admin role
        if (!isAdminUser(session)) {
            return "redirect:/login";
        }

        UserProfile currentUser = sessionManager.getCurrentUser(session);
        String currentNamespace = sessionManager.getCurrentNamespace(session);

        // Get all users and namespaces for admin
        List<UserProfile> users = userProfileRepository.findAll();
        List<Namespace> namespaces = namespaceRepository.findAll();
        List<Namespace> availableNamespaces = namespaceRepository.findByNameIn(currentUser.getNamespaces());

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("currentNamespace", currentNamespace);
        model.addAttribute("availableNamespaces", availableNamespaces);
        model.addAttribute("users", users);
        model.addAttribute("namespaces", namespaces);

        return "admin";
    }
}