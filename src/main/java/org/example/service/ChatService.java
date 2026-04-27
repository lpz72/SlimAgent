package org.example.service;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 聊天服务
 * 封装 ReactAgent 对话的公共逻辑，包括模型创建、系统提示词构建、Agent 配置等
 */
@Service
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);


    @Resource
    // 获取mcp server和本地提供的工具
    private ToolCallback[] toolCallback;


    @Value("${spring.ai.dashscope.api-key}")
    private String dashScopeApiKey;

    @Value("${chat.model}")
    private String model;
    /**
     * 创建 DashScope API 实例
     */
    public DashScopeApi createDashScopeApi() {
        return DashScopeApi.builder()
                .apiKey(dashScopeApiKey)
                .build();
    }

    /**
     * 创建 ChatModel
     * @param temperature 控制随机性 (0.0-1.0)
     * @param maxToken 最大输出长度
     * @param topP 核采样参数
     */
    public DashScopeChatModel createChatModel(DashScopeApi dashScopeApi, double temperature, int maxToken, double topP) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder()
                        .model(model)
                        .temperature(temperature)
                        .maxToken(maxToken)
                        .topP(topP)
                        .build())
                .build();
    }

    /**
     * 创建标准对话 ChatModel（默认参数）
     */
    public DashScopeChatModel createStandardChatModel(DashScopeApi dashScopeApi) {
        return createChatModel(dashScopeApi, 0.7, 2000, 0.9);
    }

    /**
     * 构建系统提示词（包含历史消息）
     * @param history 历史消息列表
     * @return 完整的系统提示词
     */
    public String buildSystemPrompt(List<Map<String, String>> history) {
        return buildSystemPrompt(history, null, null);
    }

    /**
     * 构建智能减脂 Agent 系统提示词（包含短期历史、结构化资料和长期记忆）
     */
    public String buildSystemPrompt(List<Map<String, String>> history, String structuredUserContext, String longTermMemoryContext) {
        StringBuilder systemPromptBuilder = new StringBuilder();
        
        systemPromptBuilder.append("你是一个专业、谨慎、可执行的智能减脂 Agent，基于 ReAct 思路决定是否调用工具。\n");
        systemPromptBuilder.append("你的核心任务是帮助用户进行科学减脂，包括医学常识检索、热量与营养估算、运动消耗估算、饮食与训练建议。\n");
        systemPromptBuilder.append("如果问题涉及减脂、营养、体重管理、运动、健康禁忌或医学知识，优先使用 queryInternalDocs 检索知识库；普通闲聊可以直接回答。\n");
        systemPromptBuilder.append("当需要 BMI、热量、蛋白质或运动消耗估算时，使用 calculateBmi、calculateNutritionTarget 或 calculateExerciseCalories 工具。\n");
        systemPromptBuilder.append("涉及到时间问题，使用 getCurrentDateTime 工具获取当前时间\n\n");
        systemPromptBuilder.append("当用户资料缺失导致无法给出个性化方案时，先说明缺失项并给出可执行的通用建议。\n");
        systemPromptBuilder.append("涉及疾病、药物、孕产、进食障碍、极端节食等高风险情况时，必须建议咨询医生或注册营养师。\n");
        systemPromptBuilder.append("回答应结构清晰、语气鼓励，不做绝对医疗诊断，不推荐极端低热量饮食。\n\n");


        if (structuredUserContext != null && !structuredUserContext.isBlank()) {
            systemPromptBuilder.append("--- 用户结构化信息 ---\n").append(structuredUserContext).append("\n--- 用户结构化信息结束 ---\n\n");
        }
        if (longTermMemoryContext != null && !longTermMemoryContext.isBlank()) {
            systemPromptBuilder.append("--- 长期记忆 ---\n").append(longTermMemoryContext).append("\n--- 长期记忆结束 ---\n\n");
        }
        
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
        
        systemPromptBuilder.append("请基于以上上下文回答用户的新问题。若使用了估算结果，请说明估算前提。边界不确定时先澄清。 ");
        
        return systemPromptBuilder.toString();
    }



    /**
     * 记录可用工具列表：mcp服务提供的工具
     */
    public void logAvailableTools() {
        logger.info("可用工具列表:");
        for (ToolCallback toolCallback : toolCallback) {
            logger.info(">>> {}", toolCallback.getToolDefinition().name());
        }
    }

    /**
     * 创建 ReactAgent
     * @param chatModel 聊天模型
     * @param systemPrompt 系统提示词
     * @return 配置好的 ReactAgent
     */
    public ReactAgent createReactAgent(DashScopeChatModel chatModel, String systemPrompt) {
        return ReactAgent.builder()
                .name("intelligent_assistant")
                .model(chatModel)
                .systemPrompt(systemPrompt)
//                .methodTools(buildMethodToolsArray())
                .tools(toolCallback)
                .build();
    }

    /**
     * 执行 ReactAgent 对话（非流式）
     * @param agent ReactAgent 实例
     * @param question 用户问题
     * @return AI 回复
     */
    public String executeChat(ReactAgent agent, String question) throws GraphRunnerException {
        logger.info("执行 ReactAgent.call() - 自动处理工具调用");
        var response = agent.call(question);
        String answer = response.getText();
        logger.info("ReactAgent 对话完成，答案长度: {}", answer.length());
        return answer;
    }
}
