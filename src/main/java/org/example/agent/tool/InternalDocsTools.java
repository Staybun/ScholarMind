package org.example.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.service.HybridSearchService;
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
 * 论文知识库查询工具
 * 使用 RAG 从 ScholarMind 论文知识库检索相关论文片段
 */
@Component
public class InternalDocsTools {
    
    private static final Logger logger = LoggerFactory.getLogger(InternalDocsTools.class);
    
    /** 工具名常量，用于动态构建提示词 */
    public static final String TOOL_QUERY_INTERNAL_DOCS = "queryInternalDocs";
    public static final String TOOL_QUERY_PAPER_KNOWLEDGE = "queryPaperKnowledge";
    
    private final HybridSearchService hybridSearchService;
    
    @Value("${rag.top-k:3}")
    private int topK = 3; // 默认值
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 构造函数注入依赖
     * Spring 会自动注入 HybridSearchService
     */
    @Autowired
    public InternalDocsTools(HybridSearchService hybridSearchService) {
        this.hybridSearchService = hybridSearchService;
    }
    
    /**
     * 查询论文知识库工具
     *
     * @param query 搜索查询，描述要查找的论文内容、方法、实验或结论
     * @return JSON 格式的搜索结果，包含相关论文片段、相似度距离和元数据
     */
    @Tool(description = "Search the ScholarMind paper knowledge base. " +
            "Use this tool when the user asks about uploaded papers, paper methods, experiments, datasets, metrics, " +
            "limitations, related work, or asks for evidence-backed answers from papers. " +
            "Returns TopK paper chunks with title, file name, section, page range, chunk index, distance score, and content.")
    public String queryPaperKnowledge(
            @ToolParam(description = "Search query describing the paper content, method, experiment, dataset, metric, or conclusion to retrieve")
            String query) {
        

        try {
            // 使用向量搜索服务检索相关文档
            List<VectorSearchService.SearchResult> searchResults = 
                    hybridSearchService.search(query, topK);
            
            if (searchResults.isEmpty()) {
                return "{\"status\": \"no_results\", \"message\": \"No relevant paper chunks found in ScholarMind knowledge base.\"}";
            }
            
            // 将搜索结果转换为 JSON 格式
            String resultJson = objectMapper.writeValueAsString(searchResults);
            

            return resultJson;
            
        } catch (Exception e) {
            logger.error("[工具错误] queryInternalDocs 执行失败", e);
            return String.format("{\"status\": \"error\", \"message\": \"Failed to query paper knowledge base: %s\"}",
                    e.getMessage());
        }
    }

    @Tool(description = "Compatibility alias for queryPaperKnowledge. Search uploaded papers in the ScholarMind knowledge base.")
    public String queryInternalDocs(
            @ToolParam(description = "Search query describing the paper content to retrieve") String query) {
        return queryPaperKnowledge(query);
    }
}
