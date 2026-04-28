package org.example.model.rag;

import lombok.Data;

/**
* 搜索结果类
*/
@Data
public class MilvusSearchResult {
    private String id;
    private String content;
    /**
     * 向量相似度分数
     */
    private float score;
    private String metadata;

}