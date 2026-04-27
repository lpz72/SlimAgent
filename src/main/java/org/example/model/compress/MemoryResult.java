package org.example.model.compress;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 记忆压缩结果
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MemoryResult {
    private UserProfile user_profile;
    private List<MemoryItem> memories;
    private String summary;
}