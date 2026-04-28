package org.example.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.example.controller.ChatController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

/**
 * 查询改写服务
 */
@Service
public class ReWriteQueryService {

    private static final Logger logger = LoggerFactory.getLogger(ReWriteQueryService.class);

    private final ChatClient chatClient;

    public ReWriteQueryService(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
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
        String result = chatClient.prompt(prompt.replace("{query}", query)).call().content();
        logger.info("改写结果：{}", result);
        return result;
    }

}
