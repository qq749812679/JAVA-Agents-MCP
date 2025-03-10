package com.enterprise.ai.rag;

import java.util.List;
import java.util.Map;

/**
 * 向量存储接口
 * 定义了RAG系统中向量数据库的操作标准
 */
public interface VectorStore {
    
    /**
     * 添加文档到向量数据库
     * 
     * @param documents 文档列表，每个文档是文本内容
     * @param metadatas 元数据列表，与documents一一对应
     * @param namespace 命名空间（可选）
     * @return 是否添加成功
     */
    boolean addDocuments(List<String> documents, List<Map<String, Object>> metadatas, String namespace);
    
    /**
     * 简化版添加文档方法
     */
    default boolean addDocuments(List<String> documents, List<Map<String, Object>> metadatas) {
        return addDocuments(documents, metadatas, null);
    }
    
    /**
     * 根据ID删除文档
     * 
     * @param ids 文档ID列表
     * @param namespace 命名空间（可选）
     * @return 是否删除成功
     */
    boolean deleteDocuments(List<String> ids, String namespace);
    
    /**
     * 简化版删除文档方法
     */
    default boolean deleteDocuments(List<String> ids) {
        return deleteDocuments(ids, null);
    }
    
    /**
     * 根据过滤条件删除文档
     * 
     * @param filter 过滤条件
     * @param namespace 命名空间（可选）
     * @return 是否删除成功
     */
    boolean deleteDocumentsByFilter(Map<String, Object> filter, String namespace);
    
    /**
     * 简化版过滤删除方法
     */
    default boolean deleteDocumentsByFilter(Map<String, Object> filter) {
        return deleteDocumentsByFilter(filter, null);
    }
    
    /**
     * 相似度搜索
     * 
     * @param query 查询文本
     * @param topK 返回结果数量
     * @param filter 过滤条件（可选）
     * @param namespace 命名空间（可选）
     * @return 搜索结果，包含文档内容、相似度分数和元数据
     */
    List<SearchResult> similaritySearch(String query, int topK, Map<String, Object> filter, String namespace);
    
    /**
     * 简化版相似度搜索方法
     */
    default List<SearchResult> similaritySearch(String query, int topK) {
        return similaritySearch(query, topK, null, null);
    }
    
    /**
     * 混合搜索（向量+关键词）
     * 
     * @param query 查询文本
     * @param topK 返回结果数量
     * @param filter 过滤条件（可选）
     * @param namespace 命名空间（可选）
     * @param alpha 混合权重，0表示纯向量搜索，1表示纯BM25搜索
     * @return 搜索结果列表
     */
    List<SearchResult> hybridSearch(String query, int topK, Map<String, Object> filter, String namespace, float alpha);
    
    /**
     * 简化版混合搜索方法
     */
    default List<SearchResult> hybridSearch(String query, int topK, float alpha) {
        return hybridSearch(query, topK, null, null, alpha);
    }
    
    /**
     * 获取存储统计信息
     * 
     * @return 统计信息
     */
    Map<String, Object> getStats();
    
    /**
     * 搜索结果类
     * 表示向量搜索的结果项
     */
    class SearchResult {
        private String documentId;
        private String content;
        private double score;
        private Map<String, Object> metadata;
        
        public SearchResult(String documentId, String content, double score, Map<String, Object> metadata) {
            this.documentId = documentId;
            this.content = content;
            this.score = score;
            this.metadata = metadata;
        }
        
        // Getters
        public String getDocumentId() {
            return documentId;
        }
        
        public String getContent() {
            return content;
        }
        
        public double getScore() {
            return score;
        }
        
        public Map<String, Object> getMetadata() {
            return metadata;
        }
    }
} 