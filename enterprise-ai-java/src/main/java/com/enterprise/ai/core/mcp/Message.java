package com.enterprise.ai.core.mcp;

import com.enterprise.ai.core.mcp.enums.MessageType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * MCP消息类
 * 表示Agent之间传递的消息
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {
    private String messageId;
    private String senderId;
    private String receiverId;
    private Map<String, Object> content;
    private MessageType type;
    private LocalDateTime createdAt;

    /**
     * 默认构造函数
     */
    public Message() {
        this.messageId = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 完整参数构造函数
     */
    public Message(String senderId, String receiverId, Map<String, Object> content, MessageType type) {
        this.messageId = UUID.randomUUID().toString();
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.content = content;
        this.type = type;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public Map<String, Object> getContent() {
        return content;
    }

    public void setContent(Map<String, Object> content) {
        this.content = content;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Message{" +
                "messageId='" + messageId + '\'' +
                ", senderId='" + senderId + '\'' +
                ", receiverId='" + receiverId + '\'' +
                ", type=" + type +
                ", createdAt=" + createdAt +
                '}';
    }
} 