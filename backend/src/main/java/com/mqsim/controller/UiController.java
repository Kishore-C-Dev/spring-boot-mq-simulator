package com.mqsim.controller;

import com.mqsim.model.QueueConfig;
import com.mqsim.model.ResponseMapping;
import com.mqsim.model.UserProfile;
import com.mqsim.model.Namespace;
import com.mqsim.repository.QueueConfigRepository;
import com.mqsim.repository.ResponseMappingRepository;
import com.mqsim.repository.NamespaceRepository;
import com.mqsim.service.DynamicListenerService;
import com.mqsim.service.SessionManager;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

@Controller
public class UiController {

    private final QueueConfigRepository queueConfigRepository;
    private final ResponseMappingRepository responseMappingRepository;
    private final DynamicListenerService dynamicListenerService;
    private final SessionManager sessionManager;
    private final NamespaceRepository namespaceRepository;

    @Autowired
    public UiController(QueueConfigRepository queueConfigRepository,
                       ResponseMappingRepository responseMappingRepository,
                       @Autowired(required = false) DynamicListenerService dynamicListenerService,
                       SessionManager sessionManager,
                       NamespaceRepository namespaceRepository) {
        this.queueConfigRepository = queueConfigRepository;
        this.responseMappingRepository = responseMappingRepository;
        this.dynamicListenerService = dynamicListenerService;
        this.sessionManager = sessionManager;
        this.namespaceRepository = namespaceRepository;
    }

    @GetMapping("/")
    public String redirectToUi(HttpSession session) {
        // Check if user is authenticated
        if (!sessionManager.isAuthenticated(session)) {
            return "redirect:/login";
        }
        return "redirect:/ui";
    }

    @GetMapping("/ui")
    public String dashboard(HttpSession session, Model model) {
        // Check authentication
        if (!sessionManager.isAuthenticated(session)) {
            return "redirect:/login";
        }
        
        UserProfile currentUser = sessionManager.getCurrentUser(session);
        String currentNamespace = sessionManager.getCurrentNamespace(session);
        
        // If user has no namespace access, redirect to login
        if (currentNamespace == null) {
            return "redirect:/login";
        }
        
        // Get namespace-specific data
        List<QueueConfig> queues = queueConfigRepository.findByNamespaceOrderByQueueNameAsc(currentNamespace);
        List<ResponseMapping> mappings = responseMappingRepository.findByNamespaceOrderByQueueNameAscPriorityAsc(currentNamespace);
        Map<String, String> listenerStatus = dynamicListenerService != null ? 
            dynamicListenerService.getListenerStatus() : 
            Map.of();
        
        // Get user's available namespaces for switcher
        List<Namespace> availableNamespaces = namespaceRepository.findByNameIn(currentUser.getNamespaces());

        model.addAttribute("queues", queues);
        model.addAttribute("mappings", mappings);
        model.addAttribute("listenerStatus", listenerStatus);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("currentNamespace", currentNamespace);
        model.addAttribute("availableNamespaces", availableNamespaces);
        
        return "dashboard";
    }
}