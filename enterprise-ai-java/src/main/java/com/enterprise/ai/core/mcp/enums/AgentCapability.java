package com.enterprise.ai.core.mcp.enums;

/**
 * Agent能力枚举
 * 定义MCP协议中Agent可能具备的各种能力
 */
public enum AgentCapability {
    TEXT_PROCESSING("text_processing"),   // 文本处理
    IMAGE_PROCESSING("image_processing"), // 图像处理
    AUDIO_PROCESSING("audio_processing"), // 音频处理
    CODE_GENERATION("code_generation"),   // 代码生成
    DATA_ANALYSIS("data_analysis"),       // 数据分析
    REASONING("reasoning");               // 推理能力
    
    private final String value;
    
    AgentCapability(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
} 