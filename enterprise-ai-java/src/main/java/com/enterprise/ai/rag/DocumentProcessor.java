package com.enterprise.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 文档处理器
 * 负责文档的加载、分块和预处理
 */
@Component
public class DocumentProcessor {
    private static final Logger logger = LoggerFactory.getLogger(DocumentProcessor.class);
    
    private final int defaultChunkSize;
    private final int defaultChunkOverlap;
    private final Pattern splitPattern;
    
    /**
     * 文档处理器构造函数
     */
    public DocumentProcessor() {
        // 从配置加载这些参数
        this.defaultChunkSize = 1000;
        this.defaultChunkOverlap = 200;
        
        // 使用段落、句子等作为分割点的正则表达式
        this.splitPattern = Pattern.compile("(?<=\\n\\n)|(?<=\\.\\s)|(?<=\\!\\s)|(?<=\\?\\s)");
        
        logger.info("DocumentProcessor initialized with chunk size={}, overlap={}", 
                defaultChunkSize, defaultChunkOverlap);
    }
    
    /**
     * 将文本分割成更小的块
     */
    public List<String> splitText(String text) {
        return splitText(text, defaultChunkSize, defaultChunkOverlap);
    }
    
    /**
     * 自定义参数的文本分割
     */
    public List<String> splitText(String text, int chunkSize, int chunkOverlap) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> chunks = new ArrayList<>();
        
        try {
            // 首先按自然分隔符（如段落、句子）分割文本
            String[] segments = splitPattern.split(text);
            
            StringBuilder currentChunk = new StringBuilder();
            
            for (String segment : segments) {
                // 如果添加这个段落会使块超过最大大小，先保存当前块
                if (currentChunk.length() + segment.length() > chunkSize && currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    
                    // 保留前一块末尾的overlap部分内容
                    int overlapStart = Math.max(0, currentChunk.length() - chunkOverlap);
                    if (overlapStart < currentChunk.length()) {
                        currentChunk = new StringBuilder(currentChunk.substring(overlapStart));
                    } else {
                        currentChunk = new StringBuilder();
                    }
                }
                
                // 添加当前段落
                currentChunk.append(segment);
            }
            
            // 添加最后一个块
            if (currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
            }
            
            logger.debug("Split text into {} chunks", chunks.size());
            return chunks;
            
        } catch (Exception e) {
            logger.error("Error splitting text: {}", e.getMessage(), e);
            
            // 出错时，使用简单的固定大小分割
            chunks.clear();
            for (int i = 0; i < text.length(); i += chunkSize - chunkOverlap) {
                int end = Math.min(i + chunkSize, text.length());
                chunks.add(text.substring(i, end));
            }
            
            logger.debug("Fallback: Split text into {} chunks", chunks.size());
            return chunks;
        }
    }
    
    /**
     * 从文件加载文档内容
     */
    public String loadFromFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        
        if (!Files.exists(path)) {
            throw new IOException("File not found: " + filePath);
        }
        
        String content;
        String fileExtension = getFileExtension(filePath).toLowerCase();
        
        switch (fileExtension) {
            case "txt":
            case "md":
            case "java":
            case "py":
            case "js":
            case "html":
            case "xml":
            case "json":
            case "csv":
                // 文本文件直接读取
                content = Files.readString(path);
                break;
                
            case "pdf":
                // PDF文件需要特殊处理（此处简化）
                // 实际实现中应使用PDF库如Apache PDFBox
                content = extractTextFromPdf(path);
                break;
                
            case "docx":
            case "doc":
                // Word文档需要特殊处理
                // 实际实现中应使用库如Apache POI
                content = extractTextFromWord(path);
                break;
                
            default:
                throw new IOException("Unsupported file type: " + fileExtension);
        }
        
        logger.info("Loaded document from file: {}, content size: {} chars", filePath, content.length());
        return content;
    }
    
    /**
     * 提取PDF文件文本（示例实现）
     */
    private String extractTextFromPdf(Path path) throws IOException {
        // 这里应使用PDFBox或iText等库
        // 简化示例，实际项目中需要实现
        logger.warn("PDF extraction is a placeholder. Implement with PDFBox or similar library.");
        return "PDF_CONTENT_PLACEHOLDER";
    }
    
    /**
     * 提取Word文档文本（示例实现）
     */
    private String extractTextFromWord(Path path) throws IOException {
        // 这里应使用Apache POI等库
        // 简化示例，实际项目中需要实现
        logger.warn("Word extraction is a placeholder. Implement with Apache POI or similar library.");
        return "WORD_CONTENT_PLACEHOLDER";
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String filePath) {
        int lastDotIndex = filePath.lastIndexOf('.');
        if (lastDotIndex > 0) {
            return filePath.substring(lastDotIndex + 1);
        }
        return "";
    }
    
    /**
     * 清理和规范化文本
     */
    public String cleanText(String text) {
        if (text == null) {
            return "";
        }
        
        // 移除多余空白
        String cleaned = text.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("\\n\\s*\\n+", "\n\n");
        
        return cleaned;
    }
} 