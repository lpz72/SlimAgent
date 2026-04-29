package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.rag.MilvusSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RerankService {

    private static final Logger logger = LoggerFactory.getLogger(RerankService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;

    @Value("${rag.rerank.enabled:true}")
    private boolean enabled;

    @Value("${rag.rerank.model:gte-rerank-v2}")
    private String model;

    @Value("${rag.rerank.url:https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank}")
    private String url;

    @Value("${rag.rerank.top-k:${rag.top-k:5}}")
    private int defaultTopK;

    public RerankService(
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper,
            @Value("${rag.rerank.timeout-ms:15000}") int timeoutMs) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(timeoutMs))
                .setReadTimeout(Duration.ofMillis(timeoutMs))
                .build();
        this.objectMapper = objectMapper;
    }

    public List<MilvusSearchResult> rerank(String query, List<MilvusSearchResult> candidates) {
        return rerank(query, candidates, defaultTopK);
    }

    public List<MilvusSearchResult> rerank(String query, List<MilvusSearchResult> candidates, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        int resultSize = topK <= 0 ? candidates.size() : Math.min(topK, candidates.size());
        if (!enabled || candidates.size() <= 1) {
            return candidates.stream().limit(resultSize).toList();
        }

        List<MilvusSearchResult> rerankableCandidates = candidates.stream()
                .filter(candidate -> candidate.getContent() != null && !candidate.getContent().isBlank())
                .toList();
        if (rerankableCandidates.size() <= 1) {
            return candidates.stream().limit(resultSize).toList();
        }
        int rerankTopN = Math.min(resultSize, rerankableCandidates.size());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            List<String> documents = rerankableCandidates.stream()
                    .map(MilvusSearchResult::getContent)
                    .toList();

            Map<String, Object> input = new HashMap<>();
            input.put("query", query);
            input.put("documents", documents);

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("return_documents", false);
            parameters.put("top_n", rerankTopN);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("input", input);
            requestBody.put("parameters", parameters);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    url,
                    new HttpEntity<>(requestBody, headers),
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                logger.warn("重排序调用失败，状态码={}，降级使用当前排序", response.getStatusCode());
                return candidates.stream().limit(resultSize).toList();
            }

            JsonNode outputResults = objectMapper.readTree(response.getBody()).path("output").path("results");
            if (!outputResults.isArray() || outputResults.isEmpty()) {
                logger.warn("重排序返回结果为空，降级使用当前排序");
                return candidates.stream().limit(resultSize).toList();
            }

            List<MilvusSearchResult> rerankedResults = new ArrayList<>();
            for (JsonNode item : outputResults) {
                int index = item.path("index").asInt(-1);
                if (index < 0 || index >= rerankableCandidates.size()) {
                    continue;
                }

                MilvusSearchResult result = rerankableCandidates.get(index);
                result.setScore((float) item.path("relevance_score").asDouble(result.getScore()));
                rerankedResults.add(result);
            }

            if (rerankedResults.isEmpty()) {
                return candidates.stream().limit(resultSize).toList();
            }

            rerankedResults.sort((left, right) -> Float.compare(right.getScore(), left.getScore()));
            logger.info("重排序完成，模型={}，输入候选数={}，输出结果数={}",
                    model, rerankableCandidates.size(), rerankedResults.size());
            return rerankedResults.stream().limit(resultSize).toList();

        } catch (Exception e) {
            logger.warn("重排序调用异常，降级使用当前排序：{}", e.getMessage());
            return candidates.stream().limit(resultSize).toList();
        }
    }
}
