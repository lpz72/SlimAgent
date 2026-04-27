package org.example.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 查询改写服务
 */
@Service
public class ReWriteQueryService {

    private final ChatClient chatClient;

    public ReWriteQueryService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String reWrite(String query) {
        String prompt = """
                你是一个查询改写助手。
                
                请将用户的问题改写为一个更清晰、完整、适合信息检索的查询。
                
                要求：
                1. 保持原始语义，不要改变用户意图
                2. 将口语化表达改为正式表达
                3. 补全不完整的信息（如有必要）
                4. 去除歧义，使表达更明确
                5. 不要添加无关信息
                
                【用户问题】
                {query}
                
                【输出要求】
                只输出1条改写后的查询，不要解释。
                """;
        return chatClient.prompt(prompt.replace("{query}", query)).call().content();
    }

}
