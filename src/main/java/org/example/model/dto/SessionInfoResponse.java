package org.example.model.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话信息响应
 */
@Data
public class SessionInfoResponse {

    private String sessionId;
    private Long userId;
    private String title;
    private int messagePairCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private List<MessageInfo> messages;

    @Data
    public static class SessionSummary {
        private String sessionId;
        private String title;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class MessageInfo {
        private Long id;
        private String role;
        private String content;
        private LocalDateTime createdAt;
    }
}
