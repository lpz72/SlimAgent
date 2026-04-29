package org.example.service;

import org.example.model.es.EsKeywordHit;
import org.example.model.rag.MilvusSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
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

    @Autowired
    private RerankService rerankService;

    @Value("${rag.hybrid.enabled:true}")
    private boolean enabled;

    @Value("${rag.hybrid.vector-candidate-k:20}")
    private int vectorCandidateK;

    @Value("${rag.hybrid.es-candidate-k:20}")
    private int esCandidateK;

    @Value("${rag.hybrid.fusion-top-k:20}")
    private int fusionTopK;

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

        int finalTopK = Math.max(1, topK);
        int vectorK = Math.max(finalTopK, vectorCandidateK);
        int esK = Math.max(finalTopK, esCandidateK);
        int rerankInputK = Math.max(finalTopK, fusionTopK);

        List<MilvusSearchResult> vectorResults =
                vectorSearchService.searchSimilarDocuments(query, vectorK, false);
        List<EsKeywordHit> keywordHits = esKeywordSearchService.searchKeyword(query, esK);

        Map<String, HybridCandidate> candidates = new LinkedHashMap<>();
        mergeVectorCandidates(candidates, vectorResults);
        mergeEsCandidates(candidates, keywordHits);

        List<MilvusSearchResult> fusedResults = candidates.values().stream()
                .peek(this::calculateRrfScore)
                .filter(candidate -> candidate.content != null && !candidate.content.isBlank())
                .sorted(Comparator.comparingDouble(HybridCandidate::getFusedScore).reversed())
                .limit(rerankInputK)
                .map(HybridCandidate::toMilvusSearchResult)
                .toList();

        if (fusedResults.isEmpty()) {
            logger.info("混合 RAG 未找到可用候选，向量结果数={}，ES 结果数={}",
                    vectorResults.size(), keywordHits.size());
            return List.of();
        }

        List<MilvusSearchResult> rerankedResults = rerankService.rerank(query, fusedResults, finalTopK);
        logger.info("混合 RAG 检索完成，向量结果数={}，ES 结果数={}，合并候选数={}，"
                        + "融合候选数={}，最终结果数={}",
                vectorResults.size(), keywordHits.size(), candidates.size(),
                fusedResults.size(), rerankedResults.size());
        return rerankedResults;
    }

    private void mergeVectorCandidates(
            Map<String, HybridCandidate> candidates,
            List<MilvusSearchResult> vectorResults) {
        for (int i = 0; i < vectorResults.size(); i++) {
            MilvusSearchResult result = vectorResults.get(i);
            if (result.getId() == null || result.getId().isBlank()) {
                continue;
            }

            HybridCandidate candidate = candidates.computeIfAbsent(
                    result.getId(),
                    HybridCandidate::new
            );
            candidate.content = result.getContent();
            candidate.metadata = result.getMetadata();
            candidate.vectorRank = i + 1;
        }
    }

    private void mergeEsCandidates(
            Map<String, HybridCandidate> candidates,
            List<EsKeywordHit> keywordHits) {
        for (int i = 0; i < keywordHits.size(); i++) {
            EsKeywordHit hit = keywordHits.get(i);
            if (hit.getChunkId() == null || hit.getChunkId().isBlank()) {
                continue;
            }

            HybridCandidate candidate = candidates.computeIfAbsent(
                    hit.getChunkId(),
                    HybridCandidate::new
            );
            if (candidate.content == null || candidate.content.isBlank()) {
                candidate.content = hit.getContent();
            }
            candidate.esRank = hit.getRank() == null ? i + 1 : hit.getRank();
        }
    }

    private void calculateRrfScore(HybridCandidate candidate) {
        double score = 0.0;
        if (candidate.vectorRank != null) {
            score += vectorWeight / (rrfK + candidate.vectorRank);
        }
        if (candidate.esRank != null) {
            score += esWeight / (rrfK + candidate.esRank);
        }
        candidate.fusedScore = score;
    }

    private static class HybridCandidate {

        private final String chunkId;
        private String content;
        private String metadata;
        private Integer vectorRank;
        private Integer esRank;
        private double fusedScore;

        private HybridCandidate(String chunkId) {
            this.chunkId = chunkId;
        }

        private double getFusedScore() {
            return fusedScore;
        }

        private MilvusSearchResult toMilvusSearchResult() {
            MilvusSearchResult result = new MilvusSearchResult();
            result.setId(chunkId);
            result.setContent(content);
            result.setMetadata(metadata);
            result.setScore((float) fusedScore);
            return result;
        }
    }
}
