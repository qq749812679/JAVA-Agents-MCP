package com.enterprise.ai.agent;

import com.enterprise.ai.core.mcp.Controller;
import com.enterprise.ai.core.mcp.enums.AgentCapability;
import com.enterprise.ai.rag.Retriever;
import com.enterprise.ai.rag.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 文本处理Agent
 * 专门处理文本相关任务，集成RAG检索能力和文本处理能力
 */
@Component
public class TextAgent extends BaseAgent {
    private static final Logger logger = LoggerFactory.getLogger(TextAgent.class);
    
    private final Retriever retriever;
    private final LLMService llmService; // 假设有一个LLM服务接口
    
    private final int defaultRagK;
    private final boolean useHybridSearchByDefault;
    
    /**
     * 构造函数
     */
    @Autowired
    public TextAgent(Controller controller, Retriever retriever, LLMService llmService) {
        super("TextProcessor", 
              Arrays.asList(AgentCapability.TEXT_PROCESSING, AgentCapability.REASONING), 
              controller);
        
        this.retriever = retriever;
        this.llmService = llmService;
        
        // 从配置加载这些参数
        this.defaultRagK = 5;
        this.useHybridSearchByDefault = true;
        
        logger.info("TextAgent initialized with RAG capabilities");
    }
    
    /**
     * 执行任务的主要实现
     */
    @Override
    protected Map<String, Object> executeTask(Map<String, Object> taskInfo) {
        try {
            String taskType = getTaskType(taskInfo);
            logger.info("TextAgent processing task of type: {}", taskType);
            
            switch (taskType) {
                case "qa":
                    return handleQaTask(taskInfo);
                case "summarization":
                    return handleSummarizationTask(taskInfo);
                case "text_analysis":
                    return handleAnalysisTask(taskInfo);
                default:
                    logger.warn("Unknown task type: {}", taskType);
                    Map<String, Object> errorResult = new HashMap<>();
                    errorResult.put("error", "Unsupported task type: " + taskType);
                    return errorResult;
            }
        } catch (Exception e) {
            logger.error("Error executing task: {}", e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }
    
    /**
     * 处理问答任务
     */
    private Map<String, Object> handleQaTask(Map<String, Object> taskInfo) {
        // 提取问题
        String question = (String) taskInfo.get("description");
        
        // 提取参数
        Map<String, Object> metadata = getMetadata(taskInfo);
        int ragK = getIntParam(metadata, "rag_k", defaultRagK);
        boolean hybridSearch = getBooleanParam(metadata, "hybrid_search", useHybridSearchByDefault);
        Map<String, Object> filters = getMapParam(metadata, "filters");
        
        // 执行RAG检索
        List<VectorStore.SearchResult> searchResults = retriever.query(
                question, 
                ragK, 
                filters, 
                null, 
                hybridSearch, 
                null
        );
        
        // 准备上下文
        String context = prepareContext(searchResults);
        
        // 准备源引用
        List<Map<String, Object>> sources = prepareSources(searchResults);
        
        // 构建提示
        String prompt = buildQaPrompt(question, context);
        
        // 调用LLM
        String answer = llmService.generateText(prompt);
        
        // 构建结果
        Map<String, Object> result = new HashMap<>();
        result.put("answer", answer);
        result.put("sources", sources);
        
        logger.info("QA task completed for question: {}", question);
        return result;
    }
    
    /**
     * 处理摘要任务
     */
    private Map<String, Object> handleSummarizationTask(Map<String, Object> taskInfo) {
        Map<String, Object> metadata = getMetadata(taskInfo);
        
        // 获取文本内容（直接提供或通过文档ID获取）
        String text = (String) metadata.get("text");
        String documentId = (String) metadata.get("document_id");
        
        if (text == null && documentId != null) {
            // 如果没有直接提供文本但提供了文档ID，尝试通过ID查找文档
            Map<String, Object> filter = new HashMap<>();
            filter.put("document_id", documentId);
            
            List<VectorStore.SearchResult> results = retriever.query(
                    "", // 空查询，仅使用过滤器
                    1,
                    filter,
                    null,
                    false,
                    null
            );
            
            if (!results.isEmpty()) {
                text = results.get(0).getContent();
            }
        }
        
        if (text == null || text.trim().isEmpty()) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "No text provided for summarization");
            errorResult.put("summary", "");
            return errorResult;
        }
        
        // 获取最大长度参数
        int maxLength = getIntParam(metadata, "max_length", 200);
        
        // 构建提示
        String prompt = buildSummarizationPrompt(text, maxLength);
        
        // 调用LLM
        String summary = llmService.generateText(prompt);
        
        // 构建结果
        Map<String, Object> result = new HashMap<>();
        result.put("summary", summary);
        
        logger.info("Summarization task completed, generated summary of length: {}", summary.length());
        return result;
    }
    
    /**
     * 处理文本分析任务
     */
    private Map<String, Object> handleAnalysisTask(Map<String, Object> taskInfo) {
        Map<String, Object> metadata = getMetadata(taskInfo);
        
        // 获取文本和分析类型
        String text = (String) metadata.get("text");
        String analysisType = (String) metadata.getOrDefault("analysis_type", "sentiment");
        
        if (text == null || text.trim().isEmpty()) {
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "No text provided for analysis");
            errorResult.put("result", Collections.emptyMap());
            return errorResult;
        }
        
        // 构建提示
        String prompt = buildAnalysisPrompt(text, analysisType);
        
        // 调用LLM
        String analysisResult = llmService.generateText(prompt);
        
        // 解析结果（假设返回JSON格式）
        Map<String, Object> parsedResult = parseJsonResult(analysisResult);
        
        // 构建结果
        Map<String, Object> result = new HashMap<>();
        result.put("analysis_type", analysisType);
        result.put("result", parsedResult);
        
        logger.info("Text analysis task completed with type: {}", analysisType);
        return result;
    }
    
    /**
     * 从任务信息中获取任务类型
     */
    private String getTaskType(Map<String, Object> taskInfo) {
        Map<String, Object> metadata = getMetadata(taskInfo);
        return (String) metadata.getOrDefault("task_type", "qa");
    }
    
    /**
     * 从任务信息中获取元数据
     */
    private Map<String, Object> getMetadata(Map<String, Object> taskInfo) {
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) taskInfo.getOrDefault("metadata", Collections.emptyMap());
        return metadata != null ? metadata : Collections.emptyMap();
    }
    
    /**
     * 从检索结果准备上下文文本
     */
    private String prepareContext(List<VectorStore.SearchResult> searchResults) {
        if (searchResults.isEmpty()) {
            return "没有找到相关信息。";
        }
        
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < searchResults.size(); i++) {
            context.append("[").append(i + 1).append("] ")
                   .append(searchResults.get(i).getContent())
                   .append("\n\n");
        }
        
        return context.toString();
    }
    
    /**
     * 从检索结果准备源引用
     */
    private List<Map<String, Object>> prepareSources(List<VectorStore.SearchResult> searchResults) {
        List<Map<String, Object>> sources = new ArrayList<>();
        
        for (VectorStore.SearchResult result : searchResults) {
            sources.add(result.getMetadata());
        }
        
        return sources;
    }
    
    /**
     * 构建问答提示
     */
    private String buildQaPrompt(String question, String context) {
        return "基于以下上下文信息回答问题:\n\n" +
               "上下文: " + context + "\n\n" +
               "问题: " + question + "\n\n" +
               "回答:";
    }
    
    /**
     * 构建摘要提示
     */
    private String buildSummarizationPrompt(String text, int maxLength) {
        return "请为以下文本创建一个简明扼要的摘要，最多" + maxLength + "个字：\n\n" +
               text + "\n\n" +
               "摘要:";
    }
    
    /**
     * 构建分析提示
     */
    private String buildAnalysisPrompt(String text, String analysisType) {
        switch (analysisType) {
            case "sentiment":
                return "请对以下文本进行情感分析，并给出以下信息：\n" +
                       "1. 总体情感（积极、中性或消极）\n" +
                       "2. 情感强度（1-5，其中5表示最强烈）\n" +
                       "3. 突出文本中的关键情感词汇\n" +
                       "4. 简短的情感分析结论\n\n" +
                       "文本：" + text + "\n\n" +
                       "请以JSON格式返回结果，包含以下字段：sentiment, intensity, key_words, conclusion";
                
            case "key_points":
                return "请从以下文本中提取最重要的要点：\n\n" +
                       text + "\n\n" +
                       "列出5-7个最重要的要点，并为每个要点提供简短说明。" +
                       "请以JSON格式返回结果，包含一个名为'key_points'的数组，每个数组项应有'point'和'explanation'字段。";
                
            case "entity":
                return "请从以下文本中识别所有重要实体（人物、组织、地点、日期等）：\n\n" +
                       text + "\n\n" +
                       "对于每个实体，提供其类型和在文本中的重要性。" +
                       "请以JSON格式返回结果，包含一个名为'entities'的数组，每个数组项应有'entity'、'type'和'importance'字段。";
                
            default:
                return "请分析以下文本：\n\n" + text + "\n\n请提供详细分析。";
        }
    }
    
    /**
     * 解析JSON结果（简化实现）
     */
    private Map<String, Object> parseJsonResult(String jsonText) {
        // 实际实现中应使用JSON库如Jackson或Gson
        // 这里简化处理
        try {
            // TODO: 使用JSON库解析，例如：
            // return objectMapper.readValue(jsonText, Map.class);
            
            // 简化实现，直接返回原始文本
            Map<String, Object> result = new HashMap<>();
            result.put("raw_result", jsonText);
            return result;
        } catch (Exception e) {
            logger.error("Error parsing JSON result: {}", e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "Failed to parse result");
            errorResult.put("raw_text", jsonText);
            return errorResult;
        }
    }
    
    /**
     * 获取整型参数
     */
    private int getIntParam(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    /**
     * 获取布尔参数
     */
    private boolean getBooleanParam(Map<String, Object> params, String key, boolean defaultValue) {
        Object value = params.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }
    
    /**
     * 获取Map参数
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getMapParam(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }
    
    /**
     * LLM服务接口（实际项目中应该有一个单独的实现）
     */
    public interface LLMService {
        String generateText(String prompt);
    }
} 