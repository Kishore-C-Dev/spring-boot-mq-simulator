package com.mqsim.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Table(name = "queue_configs", indexes = {
    @Index(name = "idx_queue_configs_namespace_queue", columnList = "namespace, queueName"),
    @Index(name = "idx_queue_configs_queue_name", columnList = "queueName")
})
@EntityListeners(AuditingEntityListener.class)
public class QueueConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank(message = "Namespace is required")
    @Column(nullable = false)
    private String namespace;

    @NotBlank(message = "Queue name is required")
    @Column(nullable = false)
    private String queueName;

    @NotBlank(message = "Concurrency is required")
    @Pattern(regexp = "\\d+(-\\d+)?", message = "Concurrency must be in format '2' or '2-5'")
    @Column(nullable = false)
    private String concurrency;

    @NotNull(message = "Enabled status is required")
    @Column(nullable = false)
    private Boolean enabled;

    @LastModifiedDate
    private Instant updatedAt;

    private Instant createdAt;

    @Column(nullable = false)
    private boolean deleted = false;

    public QueueConfig() {
        this.createdAt = Instant.now();
        this.deleted = false;
    }

    public QueueConfig(String namespace, String queueName, String concurrency, Boolean enabled) {
        this();
        this.namespace = namespace;
        this.queueName = queueName;
        this.concurrency = concurrency;
        this.enabled = enabled;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }

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

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

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
