package org.example.model.dto;

import lombok.Data;

/**
 * 统一 SSE 流式消息格式
 * 适用于所有 SSE 流式返回模式的对话接口
 */
@Data
public class SseMessage {
    private String type;  // content: 内容块, error: 错误, done: 完成
    private String data;

    public static SseMessage content(String data) {
        SseMessage message = new SseMessage();
        message.setType("content");
        message.setData(data);
        return message;
    }

    public static SseMessage error(String errorMessage) {
        SseMessage message = new SseMessage();
        message.setType("error");
        message.setData(errorMessage);
        return message;
    }

    public static SseMessage done() {
        SseMessage message = new SseMessage();
        message.setType("done");
        message.setData(null);
        return message;
    }
}