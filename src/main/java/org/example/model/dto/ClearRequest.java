package org.example.model.dto;

import lombok.Data;

/**
 * 清空会话请求
 */
@Data
public class ClearRequest {

    @com.fasterxml.jackson.annotation.JsonProperty(value = "Id")
    @com.fasterxml.jackson.annotation.JsonAlias({"id", "ID"})
    private String Id;
}
