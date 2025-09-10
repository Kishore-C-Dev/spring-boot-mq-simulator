package com.mqsim.controller;

import com.mqsim.model.QueueConfig;
import com.mqsim.model.ResponseMapping;
import com.mqsim.repository.QueueConfigRepository;
import com.mqsim.repository.ResponseMappingRepository;
import com.mqsim.service.DynamicListenerService;
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

    @Autowired
    public UiController(QueueConfigRepository queueConfigRepository,
                       ResponseMappingRepository responseMappingRepository,
                       @Autowired(required = false) DynamicListenerService dynamicListenerService) {
        this.queueConfigRepository = queueConfigRepository;
        this.responseMappingRepository = responseMappingRepository;
        this.dynamicListenerService = dynamicListenerService;
    }

    @GetMapping("/")
    public String redirectToUi() {
        return "redirect:/ui";
    }

    @GetMapping("/ui")
    public String dashboard(Model model) {
        List<QueueConfig> queues = queueConfigRepository.findAllByOrderByQueueNameAsc();
        List<ResponseMapping> mappings = responseMappingRepository.findAllByOrderByQueueNameAscPriorityAsc();
        Map<String, String> listenerStatus = dynamicListenerService != null ? 
            dynamicListenerService.getListenerStatus() : 
            Map.of();

        model.addAttribute("queues", queues);
        model.addAttribute("mappings", mappings);
        model.addAttribute("listenerStatus", listenerStatus);
        
        return "dashboard";
    }
}