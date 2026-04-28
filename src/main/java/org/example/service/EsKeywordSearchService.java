package org.example.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import org.example.model.es.EsChunkDocument;
import org.example.model.es.EsKeywordHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EsKeywordSearchService {

    private static final Logger logger = LoggerFactory.getLogger(EsKeywordSearchService.class);

    private final ElasticsearchClient elasticsearchClient;

    @Value("${rag.es.enabled:true}")
    private boolean enabled;

    @Value("${rag.es.index:rag_chunks}")
    private String indexName;

    public EsKeywordSearchService(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    public List<EsKeywordHit> searchKeyword(String query, int topK) {
        if (!enabled || query == null || query.isBlank() || topK <= 0) {
            return List.of();
        }

        try {
            SearchResponse<EsChunkDocument> response = elasticsearchClient.search(search -> search
                            .index(indexName)
                            .size(topK)
                            .source(source -> source.filter(filter -> filter.includes("chunkId")))
                            .query(queryBuilder -> queryBuilder.match(match -> match
                                    .field("content")
                                    .query(query))),
                    EsChunkDocument.class
            );

            List<EsKeywordHit> results = new ArrayList<>();
            int rank = 1;
            for (Hit<EsChunkDocument> hitNode : response.hits().hits()) {
                EsChunkDocument document = hitNode.source();
                String chunkId = document == null ? null : document.getChunkId();
                if (chunkId == null || chunkId.isBlank()) {
                    chunkId = hitNode.id();
                }
                if (chunkId == null || chunkId.isBlank()) {
                    continue;
                }

                EsKeywordHit hit = new EsKeywordHit();
                hit.setChunkId(chunkId);
                hit.setRank(rank++);
                hit.setScore(hitNode.score());
                results.add(hit);
            }

            logger.info("ES 关键字搜索 完成, query={}, hits={}", query, results.size());
            return results;

        } catch (Exception e) {
            logger.warn("ES 关键字搜索 失败，: {}", e.getMessage());
            return List.of();
        }
    }

    public void indexChunk(EsChunkDocument document) {
        if (!enabled || document == null || document.getChunkId() == null || document.getChunkId().isBlank()) {
            return;
        }

        try {
            elasticsearchClient.index(index -> index
                    .index(indexName)
                    .id(document.getChunkId())
                    .document(document));
            logger.info("插入 ES chunk 成功, chunkId={}", document.getChunkId());
        } catch (Exception e) {
            logger.warn("插入 ES chunk 失败, chunkId={}: {}", document.getChunkId(), e.getMessage());
        }
    }

    public void deleteChunk(String chunkId) {
        if (!enabled || chunkId == null || chunkId.isBlank()) {
            return;
        }

        try {
            elasticsearchClient.delete(delete -> delete
                    .index(indexName)
                    .id(chunkId));
        } catch (Exception e) {
            logger.warn("删除 ES chunk 失败, chunkId={}: {}", chunkId, e.getMessage());
        }
    }
}
