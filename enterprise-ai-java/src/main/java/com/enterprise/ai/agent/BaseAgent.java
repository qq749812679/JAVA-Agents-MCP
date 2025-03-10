package com.enterprise.ai.agent;

import com.enterprise.ai.core.mcp.Controller;
import com.enterprise.ai.core.mcp.Message;
import com.enterprise.ai.core.mcp.enums.AgentCapability;
import com.enterprise.ai.core.mcp.enums.MessageType;
import com.enterprise.ai.core.mcp.enums.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Agent基类
 * 所有特定领域Agent的基础实现，提供与MCP协议交互的通用功能
 */
public abstract class BaseAgent {
    private static final Logger logger = LoggerFactory.getLogger(BaseAgent.class);

    protected final String agentId;
    protected final String name;
    protected final List<AgentCapability> capabilities;
    protected final Controller controller;
    protected final Map<String, Object> metadata;
    protected String state;
    protected Map<String, Object> currentTask;
    protected final Map<MessageType, List<Consumer<Message>>> messageHandlers = new ConcurrentHashMap<>();

    /**
     * 基本构造函数
     */
    public BaseAgent(String name, List<AgentCapability> capabilities, Controller controller) {
        this(name, capabilities, controller, UUID.randomUUID().toString(), null);
    }

    /**
     * 完整构造函数
     */
    public BaseAgent(String name, List<AgentCapability> capabilities, 
                    Controller controller, String agentId, Map<String, Object> metadata) {
        this.agentId = agentId;
        this.name = name;
        this.capabilities = new ArrayList<>(capabilities);
        this.controller = controller;
        this.metadata = metadata != null ? metadata : new HashMap<>();
        this.state = "initialized";
        
        // 自动注册到控制器
        registerWithController();
        
        // 注册默认消息处理器
        registerDefaultHandlers();
        
        logger.info("Agent '{}' ({}) initialized with capabilities: {}", 
                name, agentId, capabilities.stream().map(AgentCapability::getValue).toList());
    }

    /**
     * 注册当前Agent到MCP控制器
     */
    private void registerWithController() {
        if (controller != null) {
            boolean success = controller.registerAgent(
                    agentId,
                    name,
                    capabilities,
                    metadata
            );
            if (success) {
                state = "active";
                logger.info("Agent '{}' successfully registered with controller", name);
            } else {
                logger.warn("Failed to register agent '{}' with controller", name);
            }
        }
    }

    /**
     * 注册默认的消息处理器
     */
    private void registerDefaultHandlers() {
        registerMessageHandler(MessageType.TASK_ASSIGNMENT, this::handleTaskAssignment);
        registerMessageHandler(MessageType.SYSTEM_NOTIFICATION, this::handleSystemNotification);
    }

    /**
     * 注册消息处理器
     */
    public void registerMessageHandler(MessageType messageType, Consumer<Message> handler) {
        if (!messageHandlers.containsKey(messageType)) {
            messageHandlers.put(messageType, Collections.synchronizedList(new ArrayList<>()));
        }
        
        messageHandlers.get(messageType).add(handler);
        logger.debug("Agent '{}' registered handler for message type: {}", name, messageType.getValue());
    }

    /**
     * 处理收到的消息
     */
    public boolean processMessage(Message message) {
        if (!agentId.equals(message.getReceiverId()) && !"broadcast".equals(message.getReceiverId())) {
            logger.warn("Agent '{}' received message intended for {}", name, message.getReceiverId());
            return false;
        }
        
        logger.info("Agent '{}' processing message: {} of type {}", 
                name, message.getMessageId(), message.getType().getValue());
        
        // 调用相应类型的消息处理器
        if (messageHandlers.containsKey(message.getType())) {
            for (Consumer<Message> handler : messageHandlers.get(message.getType())) {
                try {
                    handler.accept(message);
                } catch (Exception e) {
                    logger.error("Error handling message {}: {}", message.getMessageId(), e.getMessage(), e);
                }
            }
        } else {
            logger.warn("No handler registered for message type: {}", message.getType().getValue());
        }
        
        return true;
    }

    /**
     * 处理任务分配消息
     */
    private void handleTaskAssignment(Message message) {
        Map<String, Object> taskInfo = message.getContent();
        logger.info("Agent '{}' received task assignment: {}", name, taskInfo.get("task_id"));
        
        // 更新当前任务
        currentTask = taskInfo;
        
        // 开始执行任务
        try {
            // 首先通知控制器任务状态已更改为进行中
            if (controller != null) {
                controller.updateTaskStatus(
                        (String) taskInfo.get("task_id"),
                        TaskStatus.IN_PROGRESS,
                        null
                );
            }
            
            // 执行具体任务逻辑
            Map<String, Object> taskResult = executeTask(taskInfo);
            
            // 更新任务状态为已完成
            if (controller != null) {
                controller.updateTaskStatus(
                        (String) taskInfo.get("task_id"),
                        TaskStatus.COMPLETED,
                        taskResult
                );
            }
            
            logger.info("Agent '{}' completed task: {}", name, taskInfo.get("task_id"));
            
        } catch (Exception e) {
            logger.error("Agent '{}' failed to execute task {}: {}", 
                    name, taskInfo.get("task_id"), e.getMessage(), e);
            
            // 更新任务状态为失败
            if (controller != null) {
                Map<String, Object> errorResult = new HashMap<>();
                errorResult.put("error", e.getMessage());
                
                controller.updateTaskStatus(
                        (String) taskInfo.get("task_id"),
                        TaskStatus.FAILED,
                        errorResult
                );
            }
        }
        
        // 清除当前任务
        currentTask = null;
    }

    /**
     * 处理系统通知消息
     */
    private void handleSystemNotification(Message message) {
        Map<String, Object> notification = message.getContent();
        logger.info("Agent '{}' received system notification: {}", name, notification.get("type"));
        
        // 根据通知类型执行相应操作
        if ("shutdown".equals(notification.get("type"))) {
            state = "shutting_down";
            logger.info("Agent '{}' is shutting down...", name);
            cleanup();
        } else if ("pause".equals(notification.get("type"))) {
            state = "paused";
            logger.info("Agent '{}' is paused", name);
        } else if ("resume".equals(notification.get("type"))) {
            state = "active";
            logger.info("Agent '{}' is resumed", name);
        }
    }

    /**
     * 执行Agent清理操作
     */
    protected void cleanup() {
        // 从控制器注销
        if (controller != null) {
            controller.unregisterAgent(agentId);
        }
        
        state = "terminated";
        logger.info("Agent '{}' cleanup completed", name);
    }

    /**
     * 发送消息到其他Agent或控制器
     */
    protected String sendMessage(String receiverId, Map<String, Object> content, MessageType messageType) {
        if (controller == null) {
            logger.error("Agent '{}' cannot send message: no controller reference", name);
            return null;
        }
        
        String messageId = controller.sendMessage(
                agentId,
                receiverId,
                content,
                messageType
        );
        
        logger.debug("Agent '{}' sent message {} to {}", name, messageId, receiverId);
        return messageId;
    }

    /**
     * 创建新任务并提交给控制器
     */
    protected String createTask(String description, List<AgentCapability> requiredCapabilities, 
                              int priority, Map<String, Object> metadata) {
        if (controller == null) {
            logger.error("Agent '{}' cannot create task: no controller reference", name);
            return null;
        }
        
        String taskId = controller.createTask(
                description,
                agentId,
                requiredCapabilities,
                priority,
                null, // 无截止时间
                metadata
        );
        
        logger.info("Agent '{}' created task: {}", name, taskId);
        return taskId;
    }
    
    /**
     * 获取Agent状态
     */
    public String getState() {
        return state;
    }
    
    /**
     * 获取Agent ID
     */
    public String getAgentId() {
        return agentId;
    }
    
    /**
     * 获取Agent名称
     */
    public String getName() {
        return name;
    }
    
    /**
     * 获取Agent能力列表
     */
    public List<AgentCapability> getCapabilities() {
        return Collections.unmodifiableList(capabilities);
    }

    /**
     * 执行分配的任务 - 需要由子类实现
     */
    protected abstract Map<String, Object> executeTask(Map<String, Object> taskInfo);
} 