package org.example.model.dto;

import lombok.Data;

/**
 * 聊天请求
 */
@Data
public class ChatRequest {

    @com.fasterxml.jackson.annotation.JsonProperty(value = "Id")
    @com.fasterxml.jackson.annotation.JsonAlias({"id", "ID"})
    private String Id;

    @com.fasterxml.jackson.annotation.JsonProperty(value = "Question")
    @com.fasterxml.jackson.annotation.JsonAlias({"question", "QUESTION"})
    private String Question;

}