package com.mqsim.controller;

import com.mqsim.model.QueueConfig;
import com.mqsim.model.ResponseMapping;
import com.mqsim.model.UserProfile;
import com.mqsim.model.Namespace;
import com.mqsim.repository.QueueConfigRepository;
import com.mqsim.repository.ResponseMappingRepository;
import com.mqsim.repository.NamespaceRepository;
import com.mqsim.service.MockServerNotificationService;
import com.mqsim.service.SessionManager;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@Controller
public class UiController {

    private static final Logger logger = LoggerFactory.getLogger(UiController.class);

    private final QueueConfigRepository queueConfigRepository;
    private final ResponseMappingRepository responseMappingRepository;
    private final MockServerNotificationService mockServerNotificationService;
    private final SessionManager sessionManager;
    private final NamespaceRepository namespaceRepository;

    @Autowired
    public UiController(QueueConfigRepository queueConfigRepository,
                       ResponseMappingRepository responseMappingRepository,
                       MockServerNotificationService mockServerNotificationService,
                       SessionManager sessionManager,
                       NamespaceRepository namespaceRepository) {
        this.queueConfigRepository = queueConfigRepository;
        this.responseMappingRepository = responseMappingRepository;
        this.mockServerNotificationService = mockServerNotificationService;
        this.sessionManager = sessionManager;
        this.namespaceRepository = namespaceRepository;
    }

    @GetMapping("/")
    public String redirectToUi(HttpSession session) {
        if (!sessionManager.isAuthenticated(session)) {
            return "redirect:/login";
        }
        return "redirect:/ui";
    }

    @GetMapping("/ui")
    public String dashboard(HttpSession session, Model model) {
        if (!sessionManager.isAuthenticated(session)) {
            return "redirect:/login";
        }

        UserProfile currentUser = sessionManager.getCurrentUser(session);
        String currentNamespace = sessionManager.getCurrentNamespace(session);

        if (currentNamespace == null) {
            return "redirect:/login";
        }

        List<QueueConfig> queues = queueConfigRepository.findByNamespaceOrderByQueueNameAsc(currentNamespace, Sort.by("queueName"));
        List<ResponseMapping> mappings = responseMappingRepository.findByNamespaceOrderByQueueNameAscPriorityAsc(currentNamespace, Sort.by("queueName", "priority"));

        logger.debug("Retrieved {} queues for namespace {}", queues.size(), currentNamespace);
        for (QueueConfig queue : queues) {
            logger.debug("Queue: id={}, queueName={}, namespace={}", queue.getId(), queue.getQueueName(), queue.getNamespace());
        }

        Map<String, String> listenerStatus = mockServerNotificationService.getListenerStatus();

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
