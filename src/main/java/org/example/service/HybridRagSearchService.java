package org.example.service;

import org.example.model.es.EsKeywordHit;
import org.example.model.rag.MilvusSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HybridRagSearchService {

    private static final Logger logger = LoggerFactory.getLogger(HybridRagSearchService.class);

    @Autowired
    private VectorSearchService vectorSearchService;

    @Autowired
    private EsKeywordSearchService esKeywordSearchService;

    @Value("${rag.hybrid.enabled:true}")
    private boolean enabled;

    @Value("${rag.hybrid.candidate-multiplier:4}")
    private int candidateMultiplier;

    @Value("${rag.hybrid.rrf-k:60}")
    private int rrfK;

    @Value("${rag.hybrid.vector-weight:1.0}")
    private double vectorWeight;

    @Value("${rag.hybrid.es-weight:1.0}")
    private double esWeight;

    public List<MilvusSearchResult> search(String query, int topK) {
        if (!enabled) {
            return vectorSearchService.searchSimilarDocuments(query, topK);
        }

        int candidateK = Math.max(topK, topK * Math.max(1, candidateMultiplier));
        List<MilvusSearchResult> vectorResults =
                vectorSearchService.searchSimilarDocuments(query, candidateK, false);
        List<EsKeywordHit> keywordHits = esKeywordSearchService.searchKeyword(query, candidateK);

        if (vectorResults.isEmpty() || keywordHits.isEmpty()) {
            List<MilvusSearchResult> fallback = vectorResults.isEmpty()
                    ? vectorResults
                    : vectorResults.stream().limit(topK).toList();
            logger.info("混合 RAG 召回, vectorResults={}, keywordHits={}",
                    vectorResults.size(), keywordHits.size());
            return fallback;
        }

        Map<String, MilvusSearchResult> resultById = new LinkedHashMap<>();
        Map<String, Double> rrfScores = new LinkedHashMap<>();

        for (int i = 0; i < vectorResults.size(); i++) {
            MilvusSearchResult result = vectorResults.get(i);
            if (result.getId() == null || resultById.containsKey(result.getId())) {
                continue;
            }
            resultById.put(result.getId(), result);
            addRrfScore(rrfScores, result.getId(), i + 1, vectorWeight);
        }

        for (EsKeywordHit hit : keywordHits) {
            if (hit.getChunkId() == null || !resultById.containsKey(hit.getChunkId())) {
                continue;
            }
            int rank = hit.getRank() == null ? keywordHits.indexOf(hit) + 1 : hit.getRank();
            addRrfScore(rrfScores, hit.getChunkId(), rank, esWeight);
        }

        List<MilvusSearchResult> fusedResults = new ArrayList<>(resultById.values());
        fusedResults.sort((left, right) -> Double.compare(
                rrfScores.getOrDefault(right.getId(), 0.0),
                rrfScores.getOrDefault(left.getId(), 0.0)
        ));

        List<MilvusSearchResult> topResults = fusedResults.stream().limit(topK).toList();
        for (MilvusSearchResult result : topResults) {
            result.setScore(rrfScores.getOrDefault(result.getId(), 0.0).floatValue());
        }

        logger.info("混合 RAG 搜索完成, vectorResults={}, keywordHits={}, fusedResults={}",
                vectorResults.size(), keywordHits.size(), topResults.size());
        return topResults;
    }

    private void addRrfScore(Map<String, Double> scores, String chunkId, int rank, double weight) {
        double score = weight / (rrfK + rank);
        scores.merge(chunkId, score, Double::sum);
    }
}
