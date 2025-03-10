package com.enterprise.ai.core.langgraph;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LangGraph状态容器
 * 用于在LangGraph执行过程中维护和传递状态
 */
public class LangGraphState {
    
    // 值存储
    private final Map<String, Object> values;
    
    // 执行历史
    private final Map<String, Object> history;
    
    /**
     * 默认构造函数
     */
    public LangGraphState() {
        this.values = new ConcurrentHashMap<>();
        this.history = new ConcurrentHashMap<>();
    }
    
    /**
     * 带初始值的构造函数
     */
    public LangGraphState(Map<String, Object> initialValues) {
        this.values = new ConcurrentHashMap<>(initialValues);
        this.history = new ConcurrentHashMap<>();
    }
    
    /**
     * 设置值
     */
    public LangGraphState set(String key, Object value) {
        values.put(key, value);
        
        // 记录到历史中
        if (!history.containsKey(key)) {
            history.put(key, new ConcurrentHashMap<Integer, Object>());
        }
        
        @SuppressWarnings("unchecked")
        Map<Integer, Object> keyHistory = (Map<Integer, Object>) history.get(key);
        keyHistory.put(keyHistory.size(), value);
        
        return this;
    }
    
    /**
     * 获取值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) values.get(key);
    }
    
    /**
     * 获取值，不存在时返回默认值
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, T defaultValue) {
        return (T) values.getOrDefault(key, defaultValue);
    }
    
    /**
     * 检查是否包含键
     */
    public boolean has(String key) {
        return values.containsKey(key);
    }
    
    /**
     * 移除键
     */
    public LangGraphState remove(String key) {
        values.remove(key);
        return this;
    }
    
    /**
     * 获取键的历史值
     */
    @SuppressWarnings("unchecked")
    public <T> Map<Integer, T> getHistory(String key) {
        if (!history.containsKey(key)) {
            return new HashMap<>();
        }
        return (Map<Integer, T>) history.get(key);
    }
    
    /**
     * 获取最后一个历史值
     */
    @SuppressWarnings("unchecked")
    public <T> T getLastHistoryValue(String key) {
        if (!history.containsKey(key)) {
            return null;
        }
        
        Map<Integer, Object> keyHistory = (Map<Integer, Object>) history.get(key);
        if (keyHistory.isEmpty()) {
            return null;
        }
        
        Integer lastKey = keyHistory.keySet().stream().max(Integer::compare).orElse(null);
        return lastKey != null ? (T) keyHistory.get(lastKey) : null;
    }
    
    /**
     * 获取所有值的副本
     */
    public Map<String, Object> getAllValues() {
        return new HashMap<>(values);
    }
    
    /**
     * 清除所有值
     */
    public LangGraphState clear() {
        values.clear();
        history.clear();
        return this;
    }
    
    /**
     * 合并另一个状态
     */
    public LangGraphState merge(LangGraphState other) {
        values.putAll(other.values);
        
        // 合并历史
        for (Map.Entry<String, Object> entry : other.history.entrySet()) {
            if (!history.containsKey(entry.getKey())) {
                history.put(entry.getKey(), entry.getValue());
            } else {
                @SuppressWarnings("unchecked")
                Map<Integer, Object> thisKeyHistory = (Map<Integer, Object>) history.get(entry.getKey());
                @SuppressWarnings("unchecked")
                Map<Integer, Object> otherKeyHistory = (Map<Integer, Object>) entry.getValue();
                
                // 计算偏移量
                int offset = thisKeyHistory.size();
                
                // 添加另一个状态的历史，使用偏移过的索引
                for (Map.Entry<Integer, Object> historyEntry : otherKeyHistory.entrySet()) {
                    thisKeyHistory.put(offset + historyEntry.getKey(), historyEntry.getValue());
                }
            }
        }
        
        return this;
    }
    
    /**
     * 创建当前状态的快照
     */
    public LangGraphState snapshot() {
        LangGraphState snapshot = new LangGraphState();
        
        // 复制值
        snapshot.values.putAll(this.values);
        
        // 复制历史
        for (Map.Entry<String, Object> entry : this.history.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<Integer, Object> historyMap = (Map<Integer, Object>) entry.getValue();
            Map<Integer, Object> historyMapCopy = new ConcurrentHashMap<>(historyMap);
            snapshot.history.put(entry.getKey(), historyMapCopy);
        }
        
        return snapshot;
    }
    
    @Override
    public String toString() {
        return "LangGraphState{" +
                "values=" + values +
                ", historySize=" + history.size() +
                '}';
    }
} 