package com.enterprise.ai.core.mcp;

import com.enterprise.ai.core.mcp.enums.AgentCapability;
import com.enterprise.ai.core.mcp.enums.MessageType;
import com.enterprise.ai.core.mcp.enums.TaskStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * MCP控制器
 * 多Agent系统的中央协调组件，负责Agent注册、任务分配和消息路由
 */
@Component
public class Controller {
    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    // 注册的Agent字典 {agent_id: agent_info}
    private final Map<String, Map<String, Object>> agents = new ConcurrentHashMap<>();
    // 任务字典 {task_id: Task}
    private final Map<String, Task> tasks = new ConcurrentHashMap<>();
    // 消息历史记录
    private final List<Message> messageHistory = Collections.synchronizedList(new ArrayList<>());
    // 消息处理器注册表 {message_type: [handler_functions]}
    private final Map<MessageType, List<Consumer<Message>>> messageHandlers = new ConcurrentHashMap<>();
    // 能力路由表 {capability: [agent_ids]}
    private final Map<AgentCapability, List<String>> capabilityRouting = new ConcurrentHashMap<>();

    @Autowired
    private MessageBus messageBus;

    /**
     * 控制器构造函数
     */
    public Controller() {
        // 初始化消息处理器Map
        for (MessageType type : MessageType.values()) {
            messageHandlers.put(type, Collections.synchronizedList(new ArrayList<>()));
        }
        
        // 初始化能力路由表
        for (AgentCapability capability : AgentCapability.values()) {
            capabilityRouting.put(capability, Collections.synchronizedList(new ArrayList<>()));
        }
        
        logger.info("MCP Controller initialized");
    }

    /**
     * 注册新Agent到系统
     */
    public boolean registerAgent(String agentId, String name, List<AgentCapability> capabilities, 
                                Map<String, Object> metadata) {
        if (agents.containsKey(agentId)) {
            logger.warn("Agent {} already registered", agentId);
            return false;
        }
        
        Map<String, Object> agentInfo = new HashMap<>();
        agentInfo.put("id", agentId);
        agentInfo.put("name", name);
        agentInfo.put("capabilities", capabilities);
        agentInfo.put("status", "active");
        agentInfo.put("registered_at", LocalDateTime.now());
        agentInfo.put("last_active", LocalDateTime.now());
        agentInfo.put("metadata", metadata != null ? metadata : new HashMap<>());
        
        agents.put(agentId, agentInfo);
        
        // 更新能力路由表
        for (AgentCapability capability : capabilities) {
            capabilityRouting.get(capability).add(agentId);
        }
        
        logger.info("Agent {} ({}) registered with capabilities: {}", 
                agentId, name, capabilities.stream().map(AgentCapability::getValue).collect(Collectors.toList()));
        return true;
    }

    /**
     * 从系统中注销Agent
     */
    public boolean unregisterAgent(String agentId) {
        if (!agents.containsKey(agentId)) {
            logger.warn("Agent {} not found", agentId);
            return false;
        }
        
        Map<String, Object> agentInfo = agents.get(agentId);
        
        // 从能力路由表中移除
        @SuppressWarnings("unchecked")
        List<AgentCapability> capabilities = (List<AgentCapability>) agentInfo.get("capabilities");
        if (capabilities != null) {
            for (AgentCapability capability : capabilities) {
                capabilityRouting.get(capability).remove(agentId);
            }
        }
        
        // 移除Agent
        String agentName = (String) agentInfo.get("name");
        agents.remove(agentId);
        
        logger.info("Agent {} ({}) unregistered", agentId, agentName);
        return true;
    }

    /**
     * 创建新任务并返回任务ID
     */
    public String createTask(String description, String creatorId, List<AgentCapability> requiredCapabilities,
                            int priority, LocalDateTime deadline, Map<String, Object> metadata) {
        // 处理metadata
        Map<String, Object> taskMetadata = new HashMap<>();
        if (metadata != null) {
            taskMetadata.putAll(metadata);
        }
        
        // 确保能力要求被记录在metadata中
        List<String> requiredCapabilitiesValues = requiredCapabilities.stream()
                .map(AgentCapability::getValue)
                .collect(Collectors.toList());
        taskMetadata.put("required_capabilities", requiredCapabilitiesValues);
        
        // 创建任务
        Task task = new Task(description, creatorId, priority, taskMetadata);
        if (deadline != null) {
            task.setDeadline(deadline);
        }
        
        tasks.put(task.getTaskId(), task);
        logger.info("Task {} created: {}", task.getTaskId(), description.substring(0, Math.min(50, description.length())) + "...");
        
        // 尝试分配任务
        assignTask(task.getTaskId(), requiredCapabilities);
        
        return task.getTaskId();
    }

    /**
     * 根据能力要求分配任务给合适的Agent
     */
    public boolean assignTask(String taskId, List<AgentCapability> requiredCapabilities) {
        if (!tasks.containsKey(taskId)) {
            logger.error("Task {} not found", taskId);
            return false;
        }
        
        Task task = tasks.get(taskId);
        
        // 寻找满足所有能力要求的Agent
        List<String> suitableAgents = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : agents.entrySet()) {
            String agentId = entry.getKey();
            Map<String, Object> agentInfo = entry.getValue();
            
            @SuppressWarnings("unchecked")
            List<AgentCapability> agentCapabilities = (List<AgentCapability>) agentInfo.get("capabilities");
            
            if (agentCapabilities != null && agentCapabilities.containsAll(requiredCapabilities)) {
                suitableAgents.add(agentId);
            }
        }
        
        if (suitableAgents.isEmpty()) {
            logger.warn("No suitable agent found for task {}", taskId);
            return false;
        }
        
        // 简单策略：选择第一个合适的Agent
        // 实际系统中可以实现更复杂的分配策略（负载均衡、专业性匹配等）
        String selectedAgentId = suitableAgents.get(0);
        
        // 更新任务状态
        task.setStatus(TaskStatus.ASSIGNED);
        task.setAssignedAgentId(selectedAgentId);
        
        // 发送任务分配消息
        Map<String, Object> content = new HashMap<>();
        content.put("task_id", taskId);
        content.put("description", task.getDescription());
        content.put("metadata", task.getMetadata());
        
        sendMessage("controller", selectedAgentId, content, MessageType.TASK_ASSIGNMENT);
        
        logger.info("Task {} assigned to agent {}", taskId, selectedAgentId);
        return true;
    }

    /**
     * 发送消息并触发相应的处理器
     */
    public String sendMessage(String senderId, String receiverId, Map<String, Object> content,
                             MessageType messageType) {
        Message message = new Message(senderId, receiverId, content, messageType);
        
        // 记录消息历史
        messageHistory.add(message);
        
        // 通过消息总线发送消息
        if (messageBus != null) {
            messageBus.publish(message);
        }
        
        // 触发消息处理器
        if (messageHandlers.containsKey(messageType)) {
            for (Consumer<Message> handler : messageHandlers.get(messageType)) {
                try {
                    handler.accept(message);
                } catch (Exception e) {
                    logger.error("Error handling message {}: {}", message.getMessageId(), e.getMessage(), e);
                }
            }
        }
        
        logger.debug("Message {} sent from {} to {}", message.getMessageId(), senderId, receiverId);
        return message.getMessageId();
    }

    /**
     * 注册消息处理器
     */
    public void registerMessageHandler(MessageType messageType, Consumer<Message> handler) {
        if (messageHandlers.containsKey(messageType)) {
            messageHandlers.get(messageType).add(handler);
            logger.debug("Registered new handler for message type {}", messageType.getValue());
        }
    }

    /**
     * 更新任务状态
     */
    public boolean updateTaskStatus(String taskId, TaskStatus status, Map<String, Object> result) {
        if (!tasks.containsKey(taskId)) {
            logger.error("Task {} not found", taskId);
            return false;
        }
        
        Task task = tasks.get(taskId);
        task.setStatus(status);
        
        if (result != null) {
            task.setResult(result);
        }
        
        // 通知任务创建者
        if (status == TaskStatus.COMPLETED || status == TaskStatus.FAILED) {
            Map<String, Object> content = new HashMap<>();
            content.put("task_id", taskId);
            content.put("status", status.getValue());
            content.put("result", task.getResult());
            
            sendMessage("controller", task.getCreatorId(), content, MessageType.TASK_RESULT);
        }
        
        logger.info("Task {} status updated to {}", taskId, status.getValue());
        return true;
    }

    /**
     * 根据能力获取Agent列表
     */
    public List<String> getAgentsByCapability(AgentCapability capability) {
        return new ArrayList<>(capabilityRouting.getOrDefault(capability, Collections.emptyList()));
    }

    /**
     * 获取任务状态
     */
    public TaskStatus getTaskStatus(String taskId) {
        return tasks.containsKey(taskId) ? tasks.get(taskId).getStatus() : null;
    }

    /**
     * 获取系统状态概览
     */
    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("agents_count", agents.size());
        status.put("active_agents", agents.values().stream()
                .filter(a -> "active".equals(a.get("status")))
                .count());
        status.put("tasks_count", tasks.size());
        status.put("pending_tasks", tasks.values().stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING)
                .count());
        status.put("in_progress_tasks", tasks.values().stream()
                .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS)
                .count());
        status.put("completed_tasks", tasks.values().stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED)
                .count());
        status.put("failed_tasks", tasks.values().stream()
                .filter(t -> t.getStatus() == TaskStatus.FAILED)
                .count());
        status.put("messages_count", messageHistory.size());
        
        return status;
    }

    // Getters for internal objects (for service access)
    public Map<String, Task> getTasks() {
        return Collections.unmodifiableMap(tasks);
    }

    public Map<String, Map<String, Object>> getAgents() {
        return Collections.unmodifiableMap(agents);
    }
} 