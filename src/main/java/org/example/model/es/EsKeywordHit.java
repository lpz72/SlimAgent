package org.example.model.es;

import lombok.Data;

/**
 * ES BM25 关键词召回结果
 */
@Data
public class EsKeywordHit {

    /**
     * ES 文档 ID，与 Milvus id 保持一致
     */
    private String chunkId;

    /**
     * BM25 召回排名，从 1 开始
     */
    private Integer rank;

    /**
     * ES 原始 _score，仅用于日志调试，不参与融合
     */
    private Double score;
}