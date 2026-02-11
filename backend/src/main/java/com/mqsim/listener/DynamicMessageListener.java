package com.mqsim.listener;

import com.mqsim.model.ResponseMapping;
import com.mqsim.repository.ResponseMappingRepository;
import com.mqsim.service.MessageMatchingService;
import com.mqsim.service.ResponseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.Sort;

import jakarta.jms.Destination;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "mq.enabled", havingValue = "true", matchIfMissing = false)
public class DynamicMessageListener implements MessageListener {

    private static final Logger logger = LoggerFactory.getLogger(DynamicMessageListener.class);

    private final ResponseMappingRepository responseMappingRepository;
    private final MessageMatchingService messageMatchingService;
    private final ResponseService responseService;

    @Autowired
    public DynamicMessageListener(ResponseMappingRepository responseMappingRepository,
                                MessageMatchingService messageMatchingService,
                                ResponseService responseService) {
        this.responseMappingRepository = responseMappingRepository;
        this.messageMatchingService = messageMatchingService;
        this.responseService = responseService;
    }

    @Override
    public void onMessage(Message message) {
        try {
            String queueName = getQueueName(message);
            String correlationId = getCorrelationId(message);

            logger.info("Received message on queue: {} with correlationID: {}", queueName, correlationId);

            // Find response mappings for this queue
            List<ResponseMapping> mappings = responseMappingRepository.findByQueueNameAndEnabledTrueOrderByPriorityAsc(queueName,Sort.by(Sort.Direction.ASC, "priority"));

            if (mappings.isEmpty()) {
                logger.warn("No response mappings found for queue: {}", queueName);
                return;
            }

            // Find first matching response mapping
            Optional<ResponseMapping> matchingMapping = mappings.stream()
                    .filter(mapping -> messageMatchingService.matches(message, mapping))
                    .findFirst();

            if (matchingMapping.isPresent()) {
                ResponseMapping mapping = matchingMapping.get();
                logger.info("Found matching response mapping with priority: {} for correlationID: {}",
                           mapping.getPriority(), correlationId);

                // Send response
                responseService.sendResponse(message, mapping);
            } else {
                logger.warn("No matching response mapping found for correlationID: {} on queue: {}",
                           correlationId, queueName);
            }

        } catch (Exception e) {
            logger.error("Error processing message", e);
        }
    }

    private String getQueueName(Message message) {
        try {
            Destination destination = message.getJMSDestination();
            if (destination != null) {
                return extractQueueName(destination.toString());
            }
            return "unknown";
        } catch (Exception e) {
            logger.debug("Could not get queue name from message", e);
            return "unknown";
        }
    }

    /**
     * Extract plain queue name from IBM MQ JMS destination string.
     * Handles formats like "queue://QM1/QUEUE.NAME" and "queue:///QUEUE.NAME"
     */
    private String extractQueueName(String destinationStr) {
        if (destinationStr == null) {
            return "unknown";
        }
        if (destinationStr.startsWith("queue://")) {
            String afterScheme = destinationStr.substring("queue://".length());
            int slashIdx = afterScheme.indexOf('/');
            if (slashIdx >= 0) {
                return afterScheme.substring(slashIdx + 1);
            }
        }
        return destinationStr;
    }

    private String getCorrelationId(Message message) {
        try {
            String correlationId = message.getJMSCorrelationID();
            return correlationId != null ? correlationId : "none";
        } catch (Exception e) {
            logger.debug("Could not get correlation ID from message", e);
            return "none";
        }
    }
}
