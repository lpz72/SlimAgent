package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.Getter;
import lombok.Setter;
import org.example.constant.MilvusConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量搜索服务
 * 负责从 Milvus 中搜索相似向量
 */
@Service
public class VectorSearchService {

    private static final Logger logger = LoggerFactory.getLogger(VectorSearchService.class);

    @Autowired
    private MilvusServiceClient milvusClient;

    @Autowired
    private VectorEmbeddingService embeddingService;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${dashscope.api.key}")
    private String apiKey;

    @Value("${rag.rerank.enabled:true}")
    private boolean rerankEnabled;

    @Value("${rag.rerank.model:gte-rerank-v2}")
    private String rerankModel;

    @Value("${rag.rerank.url:https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank}")
    private String rerankUrl;

    @Value("${rag.rerank.top-k}")
    private int topK;

    public VectorSearchService(
            RestTemplateBuilder restTemplateBuilder,
            ObjectMapper objectMapper,
            @Value("${rag.rerank.timeout-ms:15000}") int rerankTimeoutMs) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(rerankTimeoutMs))
                .setReadTimeout(Duration.ofMillis(rerankTimeoutMs))
                .build();
        this.objectMapper = objectMapper;
    }

    /**
     * 搜索相似文档
     * 
     * @param query 查询文本
     * @param topK 返回最相似的K个结果
     * @return 搜索结果列表
     */
    public List<SearchResult> searchSimilarDocuments(String query, int topK) {
        try {
            logger.info("开始搜索相似文档, 查询: {}, topK: {}", query, topK);

            // 1. 将查询文本向量化
            List<Float> queryVector = embeddingService.generateQueryVector(query);
            logger.debug("查询向量生成成功, 维度: {}", queryVector.size());

            // 2. 构建搜索参数
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withVectorFieldName("vector")
                    .withVectors(Collections.singletonList(queryVector))
                    .withTopK(topK)
                    .withMetricType(io.milvus.param.MetricType.L2)
                    .withOutFields(List.of("id", "content", "metadata"))
                    .withParams("{\"nprobe\":10}")
                    .build();

            // 3. 执行搜索
            R<SearchResults> searchResponse = milvusClient.search(searchParam);

            if (searchResponse.getStatus() != 0) {
                throw new RuntimeException("向量搜索失败: " + searchResponse.getMessage());
            }

            // 4. 解析搜索结果
            SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResponse.getData().getResults());
            List<SearchResult> results = new ArrayList<>();

            for (int i = 0; i < wrapper.getRowRecords(0).size(); i++) {
                SearchResult result = new SearchResult();
                result.setId((String) wrapper.getIDScore(0).get(i).get("id"));
                result.setContent((String) wrapper.getFieldData("content", 0).get(i));
                result.setScore(wrapper.getIDScore(0).get(i).getScore());
                
                // 解析 metadata
                Object metadataObj = wrapper.getFieldData("metadata", 0).get(i);
                if (metadataObj != null) {
                    result.setMetadata(metadataObj.toString());
                }
                
                results.add(result);
            }

            // 重排序，返回top-3
            List<SearchResult> rerankedResults = rerankResults(query, results);
            logger.info("搜索完成, 找到 {} 个相似文档, 重排序后返回 {} 个文档", results.size(), rerankedResults.size());
            return rerankedResults;

        } catch (Exception e) {
            logger.error("搜索相似文档失败", e);
            throw new RuntimeException("搜索失败: " + e.getMessage(), e);
        }
    }

    private List<SearchResult> rerankResults(String query, List<SearchResult> results) {
        if (!rerankEnabled || results.size() <= 1) {
            return results;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            List<String> documents = results.stream()
                    .map(SearchResult::getContent)
                    .toList();

            Map<String, Object> input = new HashMap<>();
            input.put("query", query);
            input.put("documents", documents);

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("return_documents", false);
            parameters.put("top_n", topK);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", rerankModel);
            requestBody.put("input", input);
            requestBody.put("parameters", parameters);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    rerankUrl,
                    new HttpEntity<>(requestBody, headers),
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                logger.warn("重排序模型调用失败，HTTP 状态码: {}，降级使用向量排序", response.getStatusCode());
                return results;
            }

            JsonNode outputResults = objectMapper.readTree(response.getBody()).path("output").path("results");
            if (!outputResults.isArray() || outputResults.isEmpty()) {
                logger.warn("重排序模型返回结果为空，降级使用向量排序");
                return results;
            }

            List<SearchResult> rerankedResults = new ArrayList<>();
            for (JsonNode item : outputResults) {
                int index = item.path("index").asInt(-1);
                if (index < 0 || index >= results.size()) {
                    continue;
                }

                SearchResult result = results.get(index);
                result.setScore((float) item.path("relevance_score").asDouble(result.getScore()));
                rerankedResults.add(result);
            }

            if (rerankedResults.isEmpty()) {
                return results;
            }

            rerankedResults.sort(Comparator.comparingDouble(SearchResult::getScore).reversed());
            logger.info("重排序完成，模型: {}, 输入文档数: {}, 输出文档数: {}",
                    rerankModel, results.size(), rerankedResults.size());
            return rerankedResults;

        } catch (Exception e) {
            logger.warn("重排序模型调用异常，降级使用向量排序: {}", e.getMessage());
            return results;
        }
    }

    /**
     * 搜索结果类
     */
    @Setter
    @Getter
    public static class SearchResult {
        private String id;
        private String content;
        private float score;
        private String metadata;

    }
}
