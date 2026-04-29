package org.example.service;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import org.example.constant.MilvusConstants;
import org.example.model.rag.MilvusSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class VectorSearchService {

    private static final Logger logger = LoggerFactory.getLogger(VectorSearchService.class);

    @Autowired
    private MilvusServiceClient milvusClient;

    @Autowired
    private VectorEmbeddingService embeddingService;

    @Autowired
    private RerankService rerankService;

    public List<MilvusSearchResult> searchSimilarDocuments(String query, int topK) {
        return searchSimilarDocuments(query, topK, true);
    }

    public List<MilvusSearchResult> searchSimilarDocuments(String query, int topK, boolean applyRerank) {
        try {
            logger.info("开始向量检索，查询={}，返回数量={}", query, topK);

            List<Float> queryVector = embeddingService.generateQueryVector(query);
            logger.debug("查询向量生成完成，维度={}", queryVector.size());

            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withVectorFieldName("vector")
                    .withVectors(Collections.singletonList(queryVector))
                    .withTopK(topK)
                    .withMetricType(io.milvus.param.MetricType.L2)
                    .withOutFields(List.of("id", "content", "metadata"))
                    .withParams("{\"nprobe\":10}")
                    .build();

            R<SearchResults> searchResponse = milvusClient.search(searchParam);
            if (searchResponse.getStatus() != 0) {
                throw new RuntimeException("向量检索失败：" + searchResponse.getMessage());
            }

            SearchResultsWrapper wrapper = new SearchResultsWrapper(searchResponse.getData().getResults());
            List<MilvusSearchResult> results = new ArrayList<>();
            for (int i = 0; i < wrapper.getRowRecords(0).size(); i++) {
                MilvusSearchResult result = new MilvusSearchResult();
                result.setId((String) wrapper.getIDScore(0).get(i).get("id"));
                result.setContent((String) wrapper.getFieldData("content", 0).get(i));
                result.setScore(wrapper.getIDScore(0).get(i).getScore());

                Object metadataObj = wrapper.getFieldData("metadata", 0).get(i);
                if (metadataObj != null) {
                    result.setMetadata(metadataObj.toString());
                }

                results.add(result);
            }

            if (!applyRerank) {
                logger.info("向量检索完成，跳过重排序，结果数={}", results.size());
                return results;
            }

            List<MilvusSearchResult> rerankedResults = rerankService.rerank(query, results);
            logger.info("向量检索完成，原始结果数={}，重排序结果数={}",
                    results.size(), rerankedResults.size());
            return rerankedResults;

        } catch (Exception e) {
            logger.error("向量检索失败", e);
            throw new RuntimeException("向量检索失败：" + e.getMessage(), e);
        }
    }
}
