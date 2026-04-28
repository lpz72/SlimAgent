package org.example.service;

import jakarta.annotation.Resource;
import org.example.model.rag.MilvusSearchResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 意图识别服务
 */
@Service
public class IntentService {

    @Resource
    private RagService ragService;


    private final ChatClient chatClient;

    public IntentService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }


    /**
     * 流式通用响应
     *
     * @param query
     * @return
     */
    public Flux<Object> GeneralIntentStream(List<Map<String, String>> history, String query) {
        String prompt = buildPrompt(history, query);
        return chatClient.prompt(prompt).stream().content().map(s -> s);
    }

    /**
     * 非流式通用响应
     *
     * @param query
     * @return
     */
    public String GeneralIntent(List<Map<String, String>> history, String query) {
        String prompt = buildPrompt(history, query);
        return chatClient.prompt(prompt).call().content();
    }


    /**
     * 流式 RAG 检索响应，不带历史消息
     *
     * @param query
     * @return
     */
    public Flux<Object> RagIntentStream(String query) {
        return Flux.create(t -> {
            ragService.queryStream(query, new RagService.StreamCallback() {

                @Override
                public void onSearchResults(List<MilvusSearchResult> results) {

                }

                @Override
                public void onReasoningChunk(String chunk) {

                }

                @Override
                public void onContentChunk(String chunk) {
                    t.next(chunk);
                }

                @Override
                public void onComplete(String fullContent, String fullReasoning) {
                    t.complete();
                }

                @Override
                public void onError(Exception e) {
                    t.error(e);
                }

            });
        });

    }

    /**
     * 非流式 RAG 检索响应，不带历史消息
     *
     * @param query
     * @return
     */
    public String RagIntent(String query) {
        return ragService.query(query);
    }

    public String buildPrompt(List<Map<String, String>> history, String query) {

        StringBuilder systemPromptBuilder = new StringBuilder();
        systemPromptBuilder.append("用户: ").append(query).append("\n");
        if (!history.isEmpty()) {
            systemPromptBuilder.append("--- 短期对话历史 ---\n");
            for (Map<String, String> msg : history) {
                String role = msg.get("role");
                String content = msg.get("content");
                if ("user".equals(role)) {
                    systemPromptBuilder.append("用户: ").append(content).append("\n");
                } else if ("assistant".equals(role)) {
                    systemPromptBuilder.append("助手: ").append(content).append("\n");
                }
            }
            systemPromptBuilder.append("--- 短期对话历史结束 ---\n\n");
        }
        return systemPromptBuilder.toString();
    }

}
