package com.enterprise.ai.core.mcp.enums;

/**
 * 消息类型枚举
 * 定义MCP协议中的各种消息类型
 */
public enum MessageType {
    TASK_REQUEST("task_request"),       // 任务请求
    TASK_ASSIGNMENT("task_assignment"), // 任务分配
    TASK_UPDATE("task_update"),         // 任务状态更新
    TASK_RESULT("task_result"),         // 任务结果
    AGENT_REGISTRATION("agent_registration"), // Agent注册
    AGENT_STATUS("agent_status"),       // Agent状态更新
    SYSTEM_NOTIFICATION("system_notification"); // 系统通知
    
    private final String value;
    
    MessageType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
} 