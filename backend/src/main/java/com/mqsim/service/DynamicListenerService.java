package com.mqsim.service;

import com.mqsim.listener.DynamicMessageListener;
import com.mqsim.model.QueueConfig;
import com.mqsim.repository.QueueConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.jms.ConnectionFactory;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "mq.enabled", havingValue = "true", matchIfMissing = false)
public class DynamicListenerService {

    private static final Logger logger = LoggerFactory.getLogger(DynamicListenerService.class);

    private final QueueConfigRepository queueConfigRepository;
    private final ConnectionFactory connectionFactory;
    private final DynamicMessageListener messageListener;

    private final Map<String, DefaultMessageListenerContainer> activeListeners = new ConcurrentHashMap<>();

    @Autowired
    public DynamicListenerService(QueueConfigRepository queueConfigRepository,
                                ConnectionFactory connectionFactory,
                                DynamicMessageListener messageListener) {
        this.queueConfigRepository = queueConfigRepository;
        this.connectionFactory = connectionFactory;
        this.messageListener = messageListener;
    }

    @PostConstruct
    public void initializeListeners() {
        logger.info("Initializing dynamic IBM MQ listeners...");
        refreshListeners();
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down all IBM MQ listeners...");
        activeListeners.values().forEach(container -> {
            try {
                container.stop();
                container.destroy();
            } catch (Exception e) {
                logger.warn("Error stopping listener container", e);
            }
        });
        activeListeners.clear();
    }

    public synchronized void refreshListeners() {
        logger.info("Refreshing IBM MQ listeners based on queue configurations...");

        // Stop all current listeners
        stopAllListeners();

        // Start listeners for enabled queues
        List<QueueConfig> enabledQueues = queueConfigRepository.findByEnabledTrue();
        logger.info("Found {} enabled queue configurations", enabledQueues.size());

        for (QueueConfig queueConfig : enabledQueues) {
            try {
                startListenerForQueue(queueConfig);
            } catch (Exception e) {
                logger.error("Failed to start listener for queue: {}", queueConfig.getQueueName(), e);
            }
        }

        logger.info("Listener refresh completed. Active listeners: {}", activeListeners.size());
    }

    private void stopAllListeners() {
        activeListeners.values().forEach(container -> {
            try {
                container.stop();
                container.destroy();
                logger.debug("Stopped listener container for queue");
            } catch (Exception e) {
                logger.warn("Error stopping listener container", e);
            }
        });
        activeListeners.clear();
    }

    private void startListenerForQueue(QueueConfig queueConfig) {
        String queueName = queueConfig.getQueueName();
        String concurrency = queueConfig.getConcurrency();

        // Validate queue name for IBM MQ
        if (!isValidMqQueueName(queueName)) {
            logger.warn("Skipping queue '{}' - invalid IBM MQ queue name (allowed: A-Z, a-z, 0-9, '.', '/', '_', '%', max 48 chars)", queueName);
            return;
        }

        logger.info("Starting listener for queue: {} with concurrency: {}", queueName, concurrency);

        // Parse concurrency (format: "min-max" or "fixed")
        int consumers = 1;

        if (concurrency.contains("-")) {
            String[] parts = concurrency.split("-");
            consumers = Integer.parseInt(parts[0]);
        } else {
            consumers = Integer.parseInt(concurrency);
        }

        // Create JMS listener container
        DefaultMessageListenerContainer container = new DefaultMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setDestinationName(queueName);
        container.setMessageListener(messageListener);
        container.setConcurrentConsumers(consumers);
        container.setAutoStartup(true);

        // Initialize and start
        container.afterPropertiesSet();
        container.start();

        // Store reference
        activeListeners.put(queueName, container);

        logger.info("Successfully started listener for queue: {} (concurrency: {})",
                   queueName, consumers);
    }

    private boolean isValidMqQueueName(String queueName) {
        if (queueName == null || queueName.trim().isEmpty() || queueName.length() > 48) {
            return false;
        }
        return queueName.matches("[A-Za-z0-9._/%]+");
    }

    public Map<String, String> getListenerStatus() {
        Map<String, String> status = new ConcurrentHashMap<>();
        activeListeners.forEach((queueName, container) -> {
            boolean running = container.isRunning();
            int activeConsumerCount = container.getConcurrentConsumers();
            status.put(queueName, String.format("Running: %s, Active Consumers: %d", running, activeConsumerCount));
        });
        return status;
    }

    public boolean isListenerActive(String queueName) {
        DefaultMessageListenerContainer container = activeListeners.get(queueName);
        return container != null && container.isRunning();
    }
}
