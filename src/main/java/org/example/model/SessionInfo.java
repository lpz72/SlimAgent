package org.example.model;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.Data;
import org.example.mapper.FatLossUserProfileMapper;
import org.example.model.compress.MemoryItem;
import org.example.model.compress.MemoryResult;
import org.example.model.compress.UserProfile;
import org.example.model.entity.FatLossUserProfile;
import org.example.service.AuthService;
import org.example.service.UserMemoryVectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.beans.BeanUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 会话信息
 * 管理单个会话的历史消息，支持自动清理和线程安全
 */
@Data
public class SessionInfo {

    private static final Logger logger = LoggerFactory.getLogger(SessionInfo.class);
    // 会话ID
    private final String sessionId;
    // 存储历史消息
    private final List<Message> messageHistory;
    // 创建时间
    public final long createTime;
    // 读写锁，确保线程安全
    private final ReentrantLock lock;

    // 最大历史消息窗口大小（成对计算：用户消息+AI回复=1对）
    public static final int MAX_WINDOW_SIZE = 6;

    // 长期记忆最大长度
    private final int maxLangMemoryLength = 5000;

    // 长期记忆最大轮数
    private final int maxLangMemoryRounds = 2;

    // 当前长期记忆的长度
    private int currentLangMemoryLength = 0;

    // 长期会话历史消息
    public List<Message> longHistory = new ArrayList<>();

    // 历史对话摘要
    public String summaryText;


    public SessionInfo(String sessionId) {
        this.sessionId = sessionId;
        this.messageHistory = new ArrayList<>();
        this.createTime = System.currentTimeMillis();
        this.lock = new ReentrantLock();
    }

    /**
     * 获取历史消息（线程安全）
     * 返回副本以避免并发修改
     */
    public List<Map<String, String>> getHistory() {
        lock.lock();
        try {
            List<Map<String, String>> list = new ArrayList<>();
            for (Message message : messageHistory) {
                Map<String, String> map = new HashMap<>();
                map.put("role", SummaryCompressor.formatRole(message.getMessageType()));
                map.put("content", message.getText());
                list.add(map);
            }
            return list;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 清空历史消息
     */
    public void clearHistory() {
        lock.lock();
        try {
            messageHistory.clear();
            logger.info("会话 {} 历史消息已清空", sessionId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取当前消息对数
     */
    public int getMessagePairCount() {
        lock.lock();
        try {
            return messageHistory.size() / 2;
        } finally {
            lock.unlock();
        }
    }



    /**
     * 对话摘要压缩器（内部类）
     * <p>
     * 将一段历史对话消息通过 LLM 总结为简洁的摘要文本，
     * 用于在不完全丢失历史信息的前提下大幅减少上下文 token 占用。
     */
    public static class SummaryCompressor {

        private static final String SUMMARIZE_PROMPT = """
                你是一个“用户长期记忆抽取与对话摘要专家”。
                
                你的任务是从多轮对话中：
                1. 提取用户结构化信息
                2. 生成用户画像（profileSummary）
                3. 抽取长期记忆（带重要性评分）
                4. 对筛选后的记忆进行高质量摘要压缩
                
                要求：
                - 输出必须是严格 JSON
                - 不允许输出解释或额外文本
                - 所有内容必须基于对话，不允许编造
                请分析以下对话，并完成三个任务：
                
                ========================
                【任务1：结构化信息提取】
                ========================
                
                提取以下字段：
                
                - gender: MALE / FEMALE / OTHER
                - age: 整数
                - heightCm: 身高（cm）
                - weightKg: 当前体重（kg）
                - targetWeightKg: 目标体重（kg）
                - activityLevel: 枚举：
                  SEDENTARY / LIGHT / MODERATE / ACTIVE / VERY_ACTIVE
                - dietPreference: 饮食偏好
                - healthNotes: 健康备注
                
                规则：
                - 仅提取明确提及的信息
                - 禁止推测
                - 未提及返回 null
                
                
                ========================
                【任务2：用户画像生成】
                ========================
                
                基于结构化信息 + 对话内容，生成 profileSummary：
                
                要求：
                - 1~3句话
                - 描述用户身体情况、目标、饮食或运动习惯
                - 使用总结性表达，不要逐字段复述
                - 不允许推测
                
                
                ========================
                【任务3：长期记忆抽取 + 打分 + 摘要】
                ========================
                
                执行流程：
                
                Step1：提取候选记忆 \s
                Step2：对每条记忆打分（0~1） \s
                Step3：过滤 score < {{score_threshold}} 的记忆 \s
                Step4：对保留的记忆进行摘要压缩 \s
                
                ---
                
                【评分标准】
                - 0.9~1.0：关键事实（体重、目标、重大变化）
                - 0.7~0.9：重要习惯或行为变化
                - 0.5~0.7：一般信息
                - <0.5：无长期价值（必须丢弃）
                
                ---
                
                【摘要规则（重点优化）】
                
                对最终保留的记忆进行摘要时，必须遵守：
                
                1. 保留关键信息：
                   - 用户核心需求
                   - 重要目标或变化（体重/饮食/运动）
                   - 关键行为或决策
                   - 阶段性结论
                
                2. 去除冗余：
                   - 删除寒暄（如“谢谢”“好的”）
                   - 删除重复内容
                   - 删除中间推理过程
                
                3. 表达方式：
                   - 使用第三人称（如“用户表示…”）
                   - 语言客观、简洁
                
                4. 长度控制：
                   - 每条摘要为“一句话”
                   - 信息密度高
                
                5. 多条记忆去重：
                   - 相似内容合并
                   - 信息冲突时保留最新状态
                
                ---
                
                ========================
                【输入】
                ========================
                
                【已有摘要】（可为空）：
                {{existing_summary}}
                
                【新增对话】：
                {{conversation_text}}
                
                ---
                
                【摘要融合规则（新增关键优化）】
                
                如果存在已有摘要：
                
                - 必须将“已有摘要 + 新对话”进行融合
                - 不允许简单拼接
                - 必须重新组织为一组去重后的记忆
                - 保留最新状态（例如体重更新）
                
                
                ========================
                【输出格式（必须严格遵守）】
                ========================
                
                {
                  "user_profile": {
                    "gender": "MALE | FEMALE | OTHER | null",
                    "age": "integer | null",
                    "heightCm": "number | null",
                    "weightKg": "number | null",
                    "targetWeightKg": "number | null",
                    "activityLevel": "SEDENTARY | LIGHT | MODERATE | ACTIVE | VERY_ACTIVE | null",
                    "dietPreference": "string | null",
                    "healthNotes": "string | null",
                    "profileSummary": "string | null"
                  },
                  "memories": [
                    {
                      "memory": "string",
                      "score": 0.0
                    }
                  ],
                  "summary": "string"
                }
                
                """;

        /**
         * 将消息列表压缩为摘要文本
         *
         * @param chatClient         用于调用 LLM 的 ChatClient
         * @param messagesToCompress 需要压缩的消息列表
         * @param existingSummary    已有的摘要（可为 null，首次压缩时无历史摘要）
         * @return 压缩后的摘要文本
         */
        public static String compress(ChatClient chatClient, List<Message> messagesToCompress, String existingSummary) {
            if (messagesToCompress == null || messagesToCompress.isEmpty()) {
                return existingSummary;
            }

            String prompt = SUMMARIZE_PROMPT.replace("{{score_threshold}}", "0.70");

            if (existingSummary != null && !existingSummary.isBlank()) {
                prompt = prompt.replace("{{existing_summary}}", existingSummary);
            }


            StringBuilder newText = new StringBuilder();
            for (Message message : messagesToCompress) {
                String role = formatRole(message.getMessageType());
                String content = message.getText();
                if (content != null && !content.isBlank()) {
                    newText.append(role).append(": ").append(content).append("\n");
                }
            }
            prompt = prompt.replace("{{conversation_text}}", newText.toString());


            try {
                String summary = chatClient.prompt(prompt)
                        .call()
                        .content();
                return (summary != null && !summary.isBlank()) ? summary : existingSummary;
            } catch (Exception exception) {
                System.err.println("[SummaryCompressor] 摘要压缩失败: " + exception.getMessage());
                return existingSummary;
            }
        }

        private static String formatRole(MessageType messageType) {
            return switch (messageType) {
                case USER -> "user";
                case ASSISTANT -> "assistant";
                case SYSTEM -> "system";
                case TOOL -> "tool";
            };
        }
    }
}
