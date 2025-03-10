package com.enterprise.ai.core.mcp.enums;

/**
 * 任务状态枚举
 * 定义MCP协议中任务的各种状态
 */
public enum TaskStatus {
    PENDING("pending"),         // 等待中
    ASSIGNED("assigned"),       // 已分配
    IN_PROGRESS("in_progress"), // 进行中
    COMPLETED("completed"),     // 已完成
    FAILED("failed");           // 失败
    
    private final String value;
    
    TaskStatus(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
} 