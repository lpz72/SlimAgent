package org.example.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.model.dto.SessionInfoResponse;
import org.example.model.entity.FatLossChatMessage;
import org.example.model.entity.FatLossChatSession;
import org.example.mapper.FatLossChatMessageMapper;
import org.example.mapper.FatLossChatSessionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ConversationPersistenceService {
    @Autowired
    private FatLossChatSessionMapper sessionMapper;

    @Autowired
    private FatLossChatMessageMapper messageMapper;

    @Autowired
    private UserMemoryVectorService userMemoryVectorService;

    /**
     * 确保会话存在
     * @param userId
     * @param sessionId
     * @param question
     */
    public void ensureSession(Long userId, String sessionId, String question) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        FatLossChatSession existing = sessionMapper.selectOne(new LambdaQueryWrapper<FatLossChatSession>()
                .eq(FatLossChatSession::getSessionId, sessionId));
        if (existing != null) {
            existing.setUpdatedAt(LocalDateTime.now());
            sessionMapper.updateById(existing);
            return;
        }
        FatLossChatSession session = new FatLossChatSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setTitle(buildTitle(question));
        LocalDateTime now = LocalDateTime.now();
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session.setIsDeleted(0);
        sessionMapper.insert(session);
    }

    /**
     * 保存每轮对话
     * @param userId
     * @param sessionId
     * @param question
     * @param answer
     */
    public void saveRound(Long userId, String sessionId, String question, String answer) {
        ensureSession(userId, sessionId, question);
        saveMessage(userId, sessionId, "user", question, null, null);
        saveMessage(userId, sessionId, "assistant", answer, null, null);
    }
    /**
     * 构建长期记忆上下文
     * @param userId
     * @param query
     * @return
     */

    public String buildLongTermMemoryContext(Long userId, String query) {
        List<UserMemoryVectorService.MemorySearchResult> memories = userMemoryVectorService.searchMemories(userId, query, 5);
        if (memories == null || memories.isEmpty()) {
            return "暂无长期记忆。";
        }
        StringBuilder builder = new StringBuilder("长期记忆（来自用户独立 Milvus Collection）：");
        for (UserMemoryVectorService.MemorySearchResult memory : memories) {
            builder.append("\n- ").append(memory.content());
        }
        return builder.toString();
    }
    /**
     * 保存每轮对话
     * @param userId
     * @param sessionId
     * @param role
     * @param content
     * @param importanceScore
     * @param summary
     */

    private void saveMessage(Long userId, String sessionId, String role, String content, BigDecimal importanceScore, String summary) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        FatLossChatMessage message = new FatLossChatMessage();
        message.setUserId(userId);
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setImportanceScore(importanceScore);
        message.setSummary(summary);
        message.setCreatedAt(LocalDateTime.now());
        message.setIsDeleted(0);
        messageMapper.insert(message);
    }

    /**
     * 删除指定会话及其历史消息。
     */
    public boolean deleteSession(Long userId, String sessionId) {
        if (userId == null || !StringUtils.hasText(sessionId)) {
            return false;
        }

        FatLossChatSession session = sessionMapper.selectOne(new LambdaQueryWrapper<FatLossChatSession>()
                .eq(FatLossChatSession::getUserId, userId)
                .eq(FatLossChatSession::getSessionId, sessionId));
        if (session == null) {
            return false;
        }

        messageMapper.delete(new LambdaQueryWrapper<FatLossChatMessage>()
                .eq(FatLossChatMessage::getUserId, userId)
                .eq(FatLossChatMessage::getSessionId, sessionId));
        sessionMapper.deleteById(session.getId());
        return true;
    }

    /**
     * 获取当前用户的会话列表，只返回列表展示所需字段。
     */
    public List<SessionInfoResponse.SessionSummary> getSessionSummaries(Long userId) {
        if (userId == null) {
            return List.of();
        }

        List<FatLossChatSession> sessions = sessionMapper.selectList(new LambdaQueryWrapper<FatLossChatSession>()
                .eq(FatLossChatSession::getUserId, userId)
                .orderByDesc(FatLossChatSession::getUpdatedAt)
                .orderByDesc(FatLossChatSession::getId));

        return sessions.stream()
                .map(this::toSessionSummary)
                .toList();
    }

    private SessionInfoResponse.SessionSummary toSessionSummary(FatLossChatSession session) {
        SessionInfoResponse.SessionSummary summary = new SessionInfoResponse.SessionSummary();
        summary.setSessionId(session.getSessionId());
        summary.setTitle(session.getTitle());
        summary.setCreateTime(session.getCreatedAt());
        summary.setUpdateTime(session.getUpdatedAt());
        return summary;
    }

    /**
     * 根据用户和会话 ID 获取数据库中的会话详情。
     */
    public SessionInfoResponse getSessionInfo(Long userId, String sessionId) {
        if (userId == null || !StringUtils.hasText(sessionId)) {
            return null;
        }

        FatLossChatSession session = sessionMapper.selectOne(new LambdaQueryWrapper<FatLossChatSession>()
                .eq(FatLossChatSession::getUserId, userId)
                .eq(FatLossChatSession::getSessionId, sessionId));
        if (session == null) {
            return null;
        }

        List<FatLossChatMessage> messages = messageMapper.selectList(new LambdaQueryWrapper<FatLossChatMessage>()
                .eq(FatLossChatMessage::getUserId, userId)
                .eq(FatLossChatMessage::getSessionId, sessionId)
                .orderByAsc(FatLossChatMessage::getCreatedAt)
                .orderByAsc(FatLossChatMessage::getId));

        List<SessionInfoResponse.MessageInfo> messageInfos = messages.stream()
                .map(this::toMessageInfo)
                .toList();

        SessionInfoResponse response = new SessionInfoResponse();
        response.setSessionId(session.getSessionId());
        response.setUserId(session.getUserId());
        response.setTitle(session.getTitle());
        response.setCreateTime(session.getCreatedAt());
        response.setUpdateTime(session.getUpdatedAt());
        response.setMessages(messageInfos);
        response.setMessagePairCount((int) messages.stream()
                .filter(message -> "user".equals(message.getRole()))
                .count());
        return response;
    }

    private SessionInfoResponse.MessageInfo toMessageInfo(FatLossChatMessage message) {
        SessionInfoResponse.MessageInfo messageInfo = new SessionInfoResponse.MessageInfo();
        messageInfo.setId(message.getId());
        messageInfo.setRole(message.getRole());
        messageInfo.setContent(message.getContent());
        messageInfo.setCreatedAt(message.getCreatedAt());
        return messageInfo;
    }

    /**
     * 构建会话标题
     * @param question
     * @return
     */
    private String buildTitle(String question) {
        if (!StringUtils.hasText(question)) {
            return "新对话";
        }
        String title = question.trim().replaceAll("\\s+", " ");
        return title.length() > 50 ? title.substring(0, 50) + "..." : title;
    }

}
