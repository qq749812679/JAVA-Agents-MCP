package com.enterprise.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * RAG检索器
 * 为Agent提供知识检索能力，整合向量数据库和文档处理
 */
@Component
public class Retriever {
    private static final Logger logger = LoggerFactory.getLogger(Retriever.class);
    
    private final VectorStore vectorStore;
    private final DocumentProcessor documentProcessor;
    
    private final int defaultTopK;
    private final float defaultAlpha;
    private final boolean useHybridSearchByDefault;
    
    /**
     * 检索器构造函数
     */
    @Autowired
    public Retriever(VectorStore vectorStore, DocumentProcessor documentProcessor) {
        this.vectorStore = vectorStore;
        this.documentProcessor = documentProcessor;
        
        // 从配置加载这些参数
        this.defaultTopK = 5;
        this.defaultAlpha = 0.5f;
        this.useHybridSearchByDefault = true;
        
        logger.info("RAG Retriever initialized");
    }
    
    /**
     * 添加文档到知识库
     */
    public boolean addDocument(String content, Map<String, Object> metadata, String namespace) {
        try {
            // 使用文档处理器处理文档
            List<String> chunks = documentProcessor.splitText(content);
            
            // 为每个块准备元数据
            List<Map<String, Object>> chunkMetadatas = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                Map<String, Object> chunkMetadata = new HashMap<>(metadata);
                chunkMetadata.put("chunk_index", i);
                chunkMetadata.put("total_chunks", chunks.size());
                
                // 如果没有文档ID，生成一个
                if (!chunkMetadata.containsKey("document_id")) {
                    chunkMetadata.put("document_id", UUID.randomUUID().toString());
                }
                
                chunkMetadatas.add(chunkMetadata);
            }
            
            // 添加到向量存储
            boolean success = vectorStore.addDocuments(chunks, chunkMetadatas, namespace);
            
            if (success) {
                logger.info("Document added to knowledge base: {} chunks", chunks.size());
            } else {
                logger.warn("Failed to add document to knowledge base");
            }
            
            return success;
        } catch (Exception e) {
            logger.error("Error adding document to knowledge base: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 简化版添加文档
     */
    public boolean addDocument(String content, Map<String, Object> metadata) {
        return addDocument(content, metadata, null);
    }
    
    /**
     * 批量添加文档
     */
    public boolean addDocuments(List<String> contents, List<Map<String, Object>> metadatas, String namespace) {
        if (contents == null || contents.isEmpty()) {
            logger.warn("Cannot add empty document list");
            return false;
        }
        
        boolean allSuccess = true;
        
        for (int i = 0; i < contents.size(); i++) {
            Map<String, Object> metadata = (metadatas != null && i < metadatas.size()) ? 
                    metadatas.get(i) : new HashMap<>();
            
            boolean success = addDocument(contents.get(i), metadata, namespace);
            if (!success) {
                allSuccess = false;
            }
        }
        
        return allSuccess;
    }
    
    /**
     * 简化版批量添加文档
     */
    public boolean addDocuments(List<String> contents, List<Map<String, Object>> metadatas) {
        return addDocuments(contents, metadatas, null);
    }
    
    /**
     * 从文件加载文档
     */
    public boolean loadFromFile(String filePath, Map<String, Object> metadata, String namespace) {
        try {
            // 使用文档处理器从文件加载文档
            String content = documentProcessor.loadFromFile(filePath);
            
            // 如果没有提供元数据，创建一个包含文件信息的元数据
            if (metadata == null) {
                metadata = new HashMap<>();
            }
            
            // 添加文件路径到元数据
            metadata.put("source", filePath);
            
            // 添加到知识库
            return addDocument(content, metadata, namespace);
        } catch (Exception e) {
            logger.error("Error loading document from file {}: {}", filePath, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 简化版从文件加载
     */
    public boolean loadFromFile(String filePath, Map<String, Object> metadata) {
        return loadFromFile(filePath, metadata, null);
    }
    
    /**
     * 基于查询检索相关内容
     */
    public List<VectorStore.SearchResult> query(String query, int topK, Map<String, Object> filter, 
                                              String namespace, boolean useHybridSearch, Float alpha) {
        try {
            if (useHybridSearch) {
                // 使用混合搜索
                float hybridAlpha = alpha != null ? alpha : defaultAlpha;
                return vectorStore.hybridSearch(query, topK, filter, namespace, hybridAlpha);
            } else {
                // 使用纯向量搜索
                return vectorStore.similaritySearch(query, topK, filter, namespace);
            }
        } catch (Exception e) {
            logger.error("Error during retrieval for query '{}': {}", query, e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 简化版查询方法
     */
    public List<VectorStore.SearchResult> query(String query) {
        return query(query, defaultTopK, null, null, useHybridSearchByDefault, null);
    }
    
    /**
     * 自定义参数查询方法
     */
    public List<VectorStore.SearchResult> query(String query, int topK, boolean useHybridSearch) {
        return query(query, topK, null, null, useHybridSearch, null);
    }
    
    /**
     * 删除文档
     */
    public boolean deleteDocuments(List<String> docIds, String namespace) {
        try {
            return vectorStore.deleteDocuments(docIds, namespace);
        } catch (Exception e) {
            logger.error("Error deleting documents: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 根据过滤条件删除文档
     */
    public boolean deleteDocumentsByFilter(Map<String, Object> filter, String namespace) {
        try {
            return vectorStore.deleteDocumentsByFilter(filter, namespace);
        } catch (Exception e) {
            logger.error("Error deleting documents by filter: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 获取RAG系统状态
     */
    public Map<String, Object> getStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            status.put("vector_store", vectorStore.getStats());
            status.put("default_top_k", defaultTopK);
            status.put("use_hybrid_search_by_default", useHybridSearchByDefault);
            status.put("default_hybrid_alpha", defaultAlpha);
            
            return status;
        } catch (Exception e) {
            logger.error("Error getting RAG system status: {}", e.getMessage(), e);
            return Collections.emptyMap();
        }
    }
} 