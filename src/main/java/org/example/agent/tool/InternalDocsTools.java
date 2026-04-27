package org.example.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.service.VectorSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 内部文档查询工具
 * 使用 RAG (Retrieval-Augmented Generation) 从内部知识库检索相关文档
 */
@Component
public class InternalDocsTools {
    
    private static final Logger logger = LoggerFactory.getLogger(InternalDocsTools.class);
    
    /** 工具名常量，用于动态构建提示词 */
    public static final String TOOL_QUERY_INTERNAL_DOCS = "queryInternalDocs";
    
    private final VectorSearchService vectorSearchService;
    
    @Value("${rag.top-k:3}")
    private int topK = 5; // 默认值
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 构造函数注入依赖
     * Spring 会自动注入 VectorSearchService
     */
    @Autowired
    public InternalDocsTools(VectorSearchService vectorSearchService) {
        this.vectorSearchService = vectorSearchService;
    }
    
    /**
     * 查询内部文档工具
     *
     * @param query 搜索查询，描述您要查找的信息
     * @return JSON 格式的搜索结果，包含相关文档内容、相似度分数和元数据
     */
    @Tool(description = "Use this tool to retrieve relevant knowledge from the knowledge base using RAG (Retrieval-Augmented Generation). " +
            "It is suitable for questions that require factual information, domain knowledge, or external references (e.g., diet, fitness, health, or technical concepts). " +
            "Call this tool when the answer cannot be fully derived from the current conversation or user profile. " +
            "Do NOT use this tool for casual conversation, emotional support, or questions that can be answered directly without external knowledge. " +
            "Always base your final answer on the retrieved results and avoid making up information.")
    public String queryInternalDocs(
            @ToolParam(description = "A clear and complete search query used to retrieve relevant knowledge")
            String query) {

        try {
            // 向量检索
            List<VectorSearchService.SearchResult> searchResults =
                    vectorSearchService.searchSimilarDocuments(query, topK);

            if (searchResults == null || searchResults.isEmpty()) {
                return "{\"status\": \"no_results\", \"message\": \"No relevant knowledge found.\"}";
            }

            // 转 JSON
            return objectMapper.writeValueAsString(searchResults);

        } catch (Exception e) {
            logger.error("[工具错误] queryKnowledgeBase 执行失败", e);
            return String.format("{\"status\": \"error\", \"message\": \"RAG query failed: %s\"}",
                    e.getMessage());
        }
    }
}
