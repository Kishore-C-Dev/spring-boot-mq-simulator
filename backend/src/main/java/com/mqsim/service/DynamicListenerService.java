package com.mqsim.service;

import com.mqsim.listener.DynamicMessageListener;
import com.mqsim.model.QueueConfig;
import com.mqsim.repository.QueueConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@ConditionalOnProperty(name = "mq.enabled", havingValue = "true", matchIfMissing = false)
public class DynamicListenerService {

    private static final Logger logger = LoggerFactory.getLogger(DynamicListenerService.class);

    private final QueueConfigRepository queueConfigRepository;
    private final ConnectionFactory connectionFactory;
    private final RabbitAdmin rabbitAdmin;
    private final DynamicMessageListener messageListener;
    
    private final Map<String, SimpleMessageListenerContainer> activeListeners = new ConcurrentHashMap<>();

    @Autowired
    public DynamicListenerService(QueueConfigRepository queueConfigRepository, 
                                ConnectionFactory connectionFactory,
                                RabbitAdmin rabbitAdmin,
                                DynamicMessageListener messageListener) {
        this.queueConfigRepository = queueConfigRepository;
        this.connectionFactory = connectionFactory;
        this.rabbitAdmin = rabbitAdmin;
        this.messageListener = messageListener;
    }

    @PostConstruct
    public void initializeListeners() {
        logger.info("Initializing dynamic RabbitMQ listeners...");
        refreshListeners();
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down all RabbitMQ listeners...");
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
        logger.info("Refreshing RabbitMQ listeners based on queue configurations...");
        
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
        
        logger.info("Starting listener for queue: {} with concurrency: {}", queueName, concurrency);
        
        // Ensure queue exists
        Queue queue = QueueBuilder.durable(queueName).build();
        rabbitAdmin.declareQueue(queue);
        
        // Parse concurrency (format: "min-max" or "fixed")
        int minConsumers = 1;
        int maxConsumers = 1;
        
        if (concurrency.contains("-")) {
            String[] parts = concurrency.split("-");
            minConsumers = Integer.parseInt(parts[0]);
            maxConsumers = Integer.parseInt(parts[1]);
        } else {
            minConsumers = maxConsumers = Integer.parseInt(concurrency);
        }
        
        // Create listener container
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setQueueNames(queueName);
        container.setMessageListener(messageListener);
        container.setConcurrentConsumers(minConsumers);
        container.setMaxConcurrentConsumers(maxConsumers);
        container.setAutoStartup(true);
        
        // Configure container
        container.afterPropertiesSet();
        
        // Start container
        container.start();
        
        // Store reference
        activeListeners.put(queueName, container);
        
        logger.info("Successfully started listener for queue: {} (concurrency: {}-{})", 
                   queueName, minConsumers, maxConsumers);
    }

    public Map<String, String> getListenerStatus() {
        Map<String, String> status = new ConcurrentHashMap<>();
        activeListeners.forEach((queueName, container) -> {
            boolean running = container.isRunning();
            int activeConsumerCount = container.getActiveConsumerCount();
            status.put(queueName, String.format("Running: %s, Active Consumers: %d", running, activeConsumerCount));
        });
        return status;
    }

    public boolean isListenerActive(String queueName) {
        SimpleMessageListenerContainer container = activeListeners.get(queueName);
        return container != null && container.isRunning();
    }
}