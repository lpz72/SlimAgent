package org.example.agent.tool;

import jakarta.annotation.Resource;
import org.example.service.HybridRagSearchService;
import org.example.service.VectorIndexService;
import org.example.service.VectorSearchService;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 集中注册所有工具
 */
@Configuration
public class ToolRegistration {

    @Resource
    private HybridRagSearchService hybridRagSearchService;


    @Bean
    public ToolCallback[] localTools() {
        DateTimeTools dateTimeTools = new DateTimeTools();
        FatLossCalculatorTools fatLossCalculatorTools = new FatLossCalculatorTools();
        InternalDocsTools rag = new InternalDocsTools(hybridRagSearchService);
        return ToolCallbacks.from(
                dateTimeTools,
                fatLossCalculatorTools,
                rag
        );
    }
}