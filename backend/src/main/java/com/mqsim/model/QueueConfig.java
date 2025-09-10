package com.mqsim.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

@Document(collection = "queues")
public class QueueConfig {
    
    @Id
    private String id;
    
    @NotBlank(message = "Queue name is required")
    @Indexed(unique = true)
    private String queueName;
    
    @NotBlank(message = "Concurrency is required")
    @Pattern(regexp = "\\d+(-\\d+)?", message = "Concurrency must be in format '2' or '2-5'")
    private String concurrency;
    
    @NotNull(message = "Enabled status is required")
    private Boolean enabled;
    
    @LastModifiedDate
    private Instant updatedAt;
    
    private Instant createdAt;
    
    public QueueConfig() {
        this.createdAt = Instant.now();
    }
    
    public QueueConfig(String queueName, String concurrency, Boolean enabled) {
        this();
        this.queueName = queueName;
        this.concurrency = concurrency;
        this.enabled = enabled;
    }
    
    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getQueueName() { return queueName; }
    public void setQueueName(String queueName) { this.queueName = queueName; }
    
    public String getConcurrency() { return concurrency; }
    public void setConcurrency(String concurrency) { this.concurrency = concurrency; }
    
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    @Override
    public String toString() {
        return "QueueConfig{" +
                "id='" + id + '\'' +
                ", queueName='" + queueName + '\'' +
                ", concurrency='" + concurrency + '\'' +
                ", enabled=" + enabled +
                ", updatedAt=" + updatedAt +
                '}';
    }
}