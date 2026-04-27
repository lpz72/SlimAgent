package org.example.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import org.example.mapper.FatLossUserProfileMapper;
import org.example.model.SessionInfo;
import org.example.model.compress.MemoryItem;
import org.example.model.compress.MemoryResult;
import org.example.model.compress.UserProfile;
import org.example.model.entity.FatLossUserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 会话服务
 */
@Service
public class SessionInfoService {

    private static final Logger logger = LoggerFactory.getLogger(SessionInfoService.class);
    // 用于摘要压缩的 ChatClient（可为 null，为 null 时不启用摘要压缩）
    @Resource
    private ChatClient chatClient;

    @Resource
    private FatLossUserProfileMapper profileMapper;

    @Resource
    private AuthService authService;

    @Resource
    private UserMemoryVectorService userMemoryVectorService;

    /**
     * 创建会话信息
     *
     * @param sessionId
     * @return
     */
    public SessionInfo createSessionInfo(String sessionId) {
        return new SessionInfo(sessionId);
    }

    /**
     * 添加一对消息（用户问题 + AI回复）
     * 自动管理历史消息窗口大小
     */
    public void addMessage(SessionInfo sessionInfo, String userQuestion, String aiAnswer, HttpSession httpSession) {
        List<Message> history = sessionInfo.getLongHistory();
        String sessionId = sessionInfo.getSessionId();
        List<Message> messageHistory = sessionInfo.getMessageHistory();
        ReentrantLock lock = sessionInfo.getLock();


        lock.lock();
        try {

            // 自动判断并执行长期消息摘要
            compressIfNeeded(sessionInfo, httpSession);

            // 添加用户消息
            messageHistory.add(new UserMessage(userQuestion));
            history.add(new UserMessage(userQuestion));

            // 添加AI回复
            messageHistory.add(new AssistantMessage(aiAnswer));
            history.add(new AssistantMessage(aiAnswer));

            sessionInfo.setCurrentLangMemoryLength(sessionInfo.getCurrentLangMemoryLength() + userQuestion.length() + aiAnswer.length());

            // 自动清理：保持最多 MAX_WINDOW_SIZE 对消息
            // 每对消息包含2条记录（user + assistant）
            int maxMessages = SessionInfo.MAX_WINDOW_SIZE * 2;
            while (messageHistory.size() > maxMessages) {
                // 成对删除最旧的消息（删除前2条）
                messageHistory.remove(0); // 删除最旧的用户消息
                if (!messageHistory.isEmpty()) {
                    messageHistory.remove(0); // 删除对应的AI回复
                }
            }

            logger.debug("会话 {} 更新历史消息，当前消息对数: {}",
                    sessionId, messageHistory.size() / 2);

        } finally {
            lock.unlock();
        }
    }

    /**
     * 自动判断并执行摘要压缩
     * <p>
     * 当历史消息数超过阈值时，将较早的消息通过 LLM 总结为摘要，
     * 然后从 history 中移除已压缩的消息，仅保留最近的若干条。
     */
    private void compressIfNeeded(SessionInfo sessionInfo,HttpSession httpSession) {
        List<Message> longHistory = sessionInfo.getLongHistory();
        int maxLangMemoryRounds = sessionInfo.getMaxLangMemoryRounds();
        int currentLangMemoryLength = sessionInfo.getCurrentLangMemoryLength();
        int maxLangMemoryLength = sessionInfo.getMaxLangMemoryLength();
        if (chatClient == null || (longHistory.size() <= maxLangMemoryRounds * 2 && currentLangMemoryLength <= maxLangMemoryLength)) {
            return;
        }

        // 触发压缩，清空历史会话消息
        // 调用内部压缩器，获取结构化信息和摘要
        String newSummary = SessionInfo.SummaryCompressor.compress(chatClient, longHistory, sessionInfo.getSummaryText());
        if (newSummary != null && !newSummary.isBlank()) {

            logger.info("[ChatMemory] 摘要压缩完成，压缩了 " + longHistory.size()
                    + " 条消息");
            longHistory.clear();
            sessionInfo.setCurrentLangMemoryLength(0);

            // 处理响应
            MemoryResult result = JSONUtil.toBean(newSummary, MemoryResult.class);
            UserProfile userProfile = result.getUser_profile();
            // 更新用户结构化信息
            Long userId = authService.requireUserId(httpSession);
            FatLossUserProfile userProfileInDb = profileMapper.selectOne(new QueryWrapper<FatLossUserProfile>().eq("userId", userId));
            BeanUtils.copyProperties(userProfile, userProfileInDb);
            profileMapper.updateById(userProfileInDb);

            // 存储用户长期记忆
            List<MemoryItem> memories = result.getMemories();
            memories.forEach(memory -> userMemoryVectorService.saveMemory(userId, memory.getMemory(), BigDecimal.valueOf(memory.getScore()),sessionInfo.getSessionId()));
            sessionInfo.setSummaryText(result.getSummary());
        }
    }


}
