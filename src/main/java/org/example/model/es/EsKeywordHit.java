package org.example.model.es;

import lombok.Data;

@Data
public class EsKeywordHit {

    /**
     * ES 文档 ID，与 Milvus 分片 ID 保持一致。
     */
    private String chunkId;

    /**
     * ES 来源字段中的分片内容，用于让仅被 ES 命中的文档进入混合检索候选集。
     */
    private String content;

    /**
     * BM25 召回排名，从 1 开始。
     */
    private Integer rank;

    /**
     * ES 原始 _score，仅用于日志和诊断。
     */
    private Double score;
}
