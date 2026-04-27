package org.example.agent.intent;

import jakarta.annotation.Resource;
import org.example.agent.Intent;
import org.example.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 意图识别器
 * <p>
 * 在 Agent 处理用户输入之前，先通过 LLM 轻量级分类判断用户意图，
 * 当前支持区分是否需要查询 RAG 知识库。
 * <p>
 * 识别策略：使用 few-shot prompt 让 LLM 输出意图标签（RAG / GENERAL / AGENT），
 * 后续可扩展更多意图类型。
 */
@Component
public class IntentRecognizer {

    private static final Logger logger = LoggerFactory.getLogger(IntentRecognizer.class);
    private static final String INTENT_PROMPT_TEMPLATE = """
          你是一个意图分类器。请根据用户输入判断其意图类别。
        
          系统包含三类能力：
          1. RAG（通用知识问答）：减脂相关的通用知识，如饮食、运动、原理等，不涉及具体个人情况
          2. AGENT（个性化分析）：需要结合用户个人信息（如体重、身高、饮食记录等）进行计算、分析或制定计划；或者关于智能体自身的信息、能力、身份说明
          3. GENERAL（通用对话）：闲聊、问候、情绪表达或无关内容

          分类规则：
          - 如果是“通用减脂知识”，不依赖具体个人数据 → RAG
          - 如果涉及“我 / 我的 / 今天 / 每天 / 帮我算 / 帮我分析”等个性化需求 → AGENT
          - 如果是闲聊或无关内容 → GENERAL

          特别注意：
          - 只要需要“用户数据参与推理”，一律归为 AGENT
          - 即使问题是减脂相关，只要不涉及个人情况，归为 RAG

          示例：
          用户：减脂可以吃炸鸡吗？ → RAG
          用户：跑步和跳绳哪个更减脂？ → RAG

          用户：我70kg，每天该摄入多少热量？ → AGENT
          用户：帮我分析今天的饮食 → AGENT
          用户：我昨天吃多了怎么办 → AGENT

          用户：你好 → GENERAL
          用户：我今天心情不好 → GENERAL
          用户：帮我写代码 → GENERAL

          请只输出一个类别：RAG、AGENT 或 GENERAL，不要输出任何其他内容。

          用户输入：{{input}}
          """;

//    private final ChatClient chatClient;

    @Resource
    @Lazy
    private ChatModel chatModel;



    /**
     * 识别用户输入的意图
     *
     * @param userInput 用户输入文本
     * @return 识别出的意图
     */
    public Intent recognize(String userInput) {
        try {
            String prompt = INTENT_PROMPT_TEMPLATE.replace("{{input}}", userInput);
            String result = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();

            if (result != null && result.trim().toUpperCase().contains("RAG")) {
                logger.info("[意图识别] RAG — " + userInput);
                return Intent.RAG;
            } else if (result != null && result.trim().toUpperCase().contains("AGENT")) {
                logger.info("[意图识别] AGENT — " + userInput);
                return Intent.AGENT;
            }

            logger.info("[意图识别] GENERAL — " + userInput);
            return Intent.GENERAL;
        } catch (Exception exception) {
            logger.error("[意图识别] 识别失败，降级为 GENERAL: " + exception.getMessage());
            return Intent.GENERAL;
        }
    }
}
