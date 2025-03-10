package com.enterprise.ai.core.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 消息总线
 * 负责MCP协议中的消息路由和分发，支持异步消息处理和订阅机制
 */
@Component
public class MessageBus {
    private static final Logger logger = LoggerFactory.getLogger(MessageBus.class);

    // 配置参数
    private final int maxQueueSize;
    private final int numWorkers;

    // 使用Kafka作为消息队列（也可使用内存队列或RabbitMQ等）
    private final KafkaTemplate<String, Message> kafkaTemplate;
    private final String mcpTopic = "mcp-messages";

    // 订阅者注册表
    private final Map<String, List<Consumer<Message>>> subscribers = new ConcurrentHashMap<>();
    private final Map<String, List<String>> topicSubscriptions = new ConcurrentHashMap<>();

    // 消息处理线程池
    private final ExecutorService executorService;

    // 运行状态标志
    private volatile boolean isRunning = true;

    /**
     * 消息总线构造函数
     */
    public MessageBus(KafkaTemplate<String, Message> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.maxQueueSize = 1000; // 从配置中读取
        this.numWorkers = 3; // 从配置中读取
        
        // 创建线程池
        this.executorService = Executors.newFixedThreadPool(numWorkers);
        
        logger.info("MessageBus initialized with {} workers", numWorkers);
    }

    /**
     * 发布消息到总线
     */
    public boolean publish(Message message) {
        if (!isRunning) {
            logger.warn("MessageBus is not running, cannot publish message");
            return false;
        }
        
        try {
            // 发送消息到Kafka
            kafkaTemplate.send(mcpTopic, message.getMessageId(), message)
                    .whenComplete((SendResult<String, Message> result, Throwable ex) -> {
                        if (ex != null) {
                            logger.error("Error sending message to Kafka: {}", ex.getMessage());
                        } else {
                            logger.debug("Message {} sent to Kafka topic {}", 
                                    message.getMessageId(), mcpTopic);
                        }
                    });
            
            // 直接处理消息（对于本地订阅者）
            processMessageLocally(message);
            
            return true;
        } catch (Exception e) {
            logger.error("Error publishing message: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 处理消息（本地订阅者）
     */
    private void processMessageLocally(Message message) {
        // 确定消息的接收者和回调函数
        List<Consumer<Message>> callbacks = new ArrayList<>();
        
        // 直接接收者的回调
        if (subscribers.containsKey(message.getReceiverId())) {
            callbacks.addAll(subscribers.get(message.getReceiverId()));
        }
        
        // 广播消息
        if ("broadcast".equals(message.getReceiverId())) {
            subscribers.values().forEach(callbacks::addAll);
        }
        
        // 主题订阅
        if (message.getContent() != null && message.getContent().containsKey("topic")) {
            String topic = (String) message.getContent().get("topic");
            if (topicSubscriptions.containsKey(topic)) {
                for (String agentId : topicSubscriptions.get(topic)) {
                    if (subscribers.containsKey(agentId)) {
                        callbacks.addAll(subscribers.get(agentId));
                    }
                }
            }
        }
        
        // 如果没有接收者，记录警告
        if (callbacks.isEmpty()) {
            logger.warn("No subscribers found for message {} to {}", 
                    message.getMessageId(), message.getReceiverId());
            return;
        }
        
        // 通过线程池处理回调
        for (Consumer<Message> callback : callbacks) {
            executorService.submit(() -> {
                try {
                    callback.accept(message);
                } catch (Exception e) {
                    logger.error("Error in message callback: {}", e.getMessage(), e);
                }
            });
        }
    }

    /**
     * 订阅Agent ID的消息
     */
    public boolean subscribe(String agentId, Consumer<Message> callback) {
        if (!subscribers.containsKey(agentId)) {
            subscribers.put(agentId, Collections.synchronizedList(new ArrayList<>()));
        }
        
        subscribers.get(agentId).add(callback);
        logger.info("Agent {} subscribed to messages", agentId);
        return true;
    }

    /**
     * 取消订阅Agent ID的消息
     */
    public boolean unsubscribe(String agentId, Consumer<Message> callback) {
        if (!subscribers.containsKey(agentId)) {
            logger.warn("Agent {} not found in subscribers", agentId);
            return false;
        }
        
        if (callback != null) {
            // 移除特定回调
            boolean removed = subscribers.get(agentId).remove(callback);
            if (removed) {
                logger.info("Callback removed from agent {} subscriptions", agentId);
            } else {
                logger.warn("Callback not found in agent {} subscriptions", agentId);
                return false;
            }
        } else {
            // 移除所有回调
            subscribers.remove(agentId);
            logger.info("All subscriptions removed for agent {}", agentId);
        }
        
        // 从主题订阅中移除
        for (List<String> agents : topicSubscriptions.values()) {
            agents.remove(agentId);
        }
        
        return true;
    }

    /**
     * 订阅特定主题的消息
     */
    public boolean subscribeToTopic(String agentId, String topic) {
        if (!topicSubscriptions.containsKey(topic)) {
            topicSubscriptions.put(topic, Collections.synchronizedList(new ArrayList<>()));
        }
        
        if (!topicSubscriptions.get(topic).contains(agentId)) {
            topicSubscriptions.get(topic).add(agentId);
            logger.info("Agent {} subscribed to topic {}", agentId, topic);
            return true;
        }
        
        return false;
    }

    /**
     * 取消订阅特定主题的消息
     */
    public boolean unsubscribeFromTopic(String agentId, String topic) {
        if (!topicSubscriptions.containsKey(topic)) {
            logger.warn("Topic {} not found in subscriptions", topic);
            return false;
        }
        
        if (topicSubscriptions.get(topic).contains(agentId)) {
            topicSubscriptions.get(topic).remove(agentId);
            logger.info("Agent {} unsubscribed from topic {}", agentId, topic);
            return true;
        }
        
        logger.warn("Agent {} not subscribed to topic {}", agentId, topic);
        return false;
    }

    /**
     * 关闭消息总线
     */
    public void shutdown() {
        logger.info("Shutting down MessageBus...");
        isRunning = false;
        
        // 关闭线程池
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("MessageBus shutdown complete");
    }

    /**
     * 获取队列状态信息
     */
    public Map<String, Object> getQueueStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("subscribers_count", subscribers.size());
        status.put("topics_count", topicSubscriptions.size());
        status.put("is_running", isRunning);
        
        return status;
    }
} 