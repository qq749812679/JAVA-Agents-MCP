package com.enterprise.ai.core.mcp;

import com.enterprise.ai.core.mcp.enums.TaskStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 任务类
 * 表示MCP协议中的任务，可被分配给Agent执行
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Task {
    private String taskId;
    private String description;
    private String creatorId;
    private TaskStatus status;
    private String assignedAgentId;
    private LocalDateTime createdAt;
    private LocalDateTime deadline;
    private int priority;
    private Map<String, Object> metadata;
    private Map<String, Object> result;

    /**
     * 默认构造函数
     */
    public Task() {
        this.taskId = UUID.randomUUID().toString();
        this.status = TaskStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.priority = 1;
        this.metadata = new HashMap<>();
    }

    /**
     * 基本参数构造函数
     */
    public Task(String description, String creatorId) {
        this.taskId = UUID.randomUUID().toString();
        this.description = description;
        this.creatorId = creatorId;
        this.status = TaskStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.priority = 1;
        this.metadata = new HashMap<>();
    }

    /**
     * 完整参数构造函数
     */
    public Task(String description, String creatorId, int priority, Map<String, Object> metadata) {
        this.taskId = UUID.randomUUID().toString();
        this.description = description;
        this.creatorId = creatorId;
        this.status = TaskStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.priority = priority;
        this.metadata = metadata != null ? metadata : new HashMap<>();
    }

    // Getters and Setters
    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public String getAssignedAgentId() {
        return assignedAgentId;
    }

    public void setAssignedAgentId(String assignedAgentId) {
        this.assignedAgentId = assignedAgentId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDateTime deadline) {
        this.deadline = deadline;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }
} 