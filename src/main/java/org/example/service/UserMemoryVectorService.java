package org.example.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.CollectionSchemaParam;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import org.example.constant.MilvusConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Milvus 存储用户长期记忆
 */
@Service
public class UserMemoryVectorService {
    private static final Logger logger = LoggerFactory.getLogger(UserMemoryVectorService.class);
    private static final String COLLECTION_PREFIX = "user_";

    @Autowired
    private MilvusServiceClient milvusClient;

    @Autowired
    private VectorEmbeddingService embeddingService;

    public void saveMemory(Long userId, String content, BigDecimal importanceScore, String sourceSessionId) {
        if (userId == null || !StringUtils.hasText(content)) {
            return;
        }
        String collectionName = getCollectionName(userId);
        ensureCollection(collectionName);
        loadCollection(collectionName);

        List<Float> vector = embeddingService.generateEmbedding(content);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("userId", userId);
//        metadata.put("memoryType", memoryType);
        metadata.put("importanceScore", importanceScore == null ? 0 : importanceScore.doubleValue());
        metadata.put("sourceSessionId", sourceSessionId);
        metadata.put("createdAt", LocalDateTime.now().toString());

        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field("id", Collections.singletonList(UUID.randomUUID().toString())));
        fields.add(new InsertParam.Field("content", Collections.singletonList(truncateContent(content))));
        fields.add(new InsertParam.Field("vector", Collections.singletonList(vector)));
        JsonObject metadataJson = new Gson().toJsonTree(metadata).getAsJsonObject();
        fields.add(new InsertParam.Field("metadata", Collections.singletonList(metadataJson)));

        InsertParam insertParam = InsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withFields(fields)
                .build();
        R<MutationResult> response = milvusClient.insert(insertParam);
        if (response.getStatus() != 0) {
            throw new RuntimeException("写入用户长期记忆到 Milvus 失败: " + response.getMessage());
        }
        logger.info("用户长期记忆已写入 Milvus collection={}, userId={}", collectionName, userId);
    }

    public List<MemorySearchResult> searchMemories(Long userId, String query, int topK) {
        if (userId == null || !StringUtils.hasText(query)) {
            return Collections.emptyList();
        }
        String collectionName = getCollectionName(userId);
        if (!collectionExists(collectionName)) {
            return Collections.emptyList();
        }
        loadCollection(collectionName);

        List<Float> queryVector = embeddingService.generateQueryVector(query);
        SearchParam searchParam = SearchParam.newBuilder()
                .withCollectionName(collectionName)
                .withVectorFieldName("vector")
                .withVectors(Collections.singletonList(queryVector))
                .withTopK(topK)
                .withMetricType(MetricType.L2)
                .withOutFields(List.of("id", "content", "metadata"))
                .withParams("{\"nprobe\":10}")
                .build();
        R<SearchResults> response = milvusClient.search(searchParam);
        if (response.getStatus() != 0) {
            throw new RuntimeException("检索用户长期记忆失败: " + response.getMessage());
        }

        SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
        List<MemorySearchResult> results = new ArrayList<>();
        for (int i = 0; i < wrapper.getRowRecords(0).size(); i++) {
            String id = (String) wrapper.getIDScore(0).get(i).get("id");
            String content = (String) wrapper.getFieldData("content", 0).get(i);
            Object metadataObj = wrapper.getFieldData("metadata", 0).get(i);
            float score = wrapper.getIDScore(0).get(i).getScore();
            results.add(new MemorySearchResult(id, content, score, metadataObj == null ? null : metadataObj.toString()));
        }
        return results;
    }

    private void ensureCollection(String collectionName) {
        if (collectionExists(collectionName)) {
            return;
        }

        FieldType idField = FieldType.newBuilder()
                .withName("id")
                .withDataType(DataType.VarChar)
                .withMaxLength(MilvusConstants.ID_MAX_LENGTH)
                .withPrimaryKey(true)
                .build();
        FieldType vectorField = FieldType.newBuilder()
                .withName("vector")
                .withDataType(DataType.FloatVector)
                .withDimension(MilvusConstants.VECTOR_DIM)
                .build();
        FieldType contentField = FieldType.newBuilder()
                .withName("content")
                .withDataType(DataType.VarChar)
                .withMaxLength(MilvusConstants.CONTENT_MAX_LENGTH)
                .build();
        FieldType metadataField = FieldType.newBuilder()
                .withName("metadata")
                .withDataType(DataType.JSON)
                .build();

        CollectionSchemaParam schema = CollectionSchemaParam.newBuilder()
                .withEnableDynamicField(false)
                .addFieldType(idField)
                .addFieldType(vectorField)
                .addFieldType(contentField)
                .addFieldType(metadataField)
                .build();
        CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withDescription("Long-term memory collection for user")
                .withSchema(schema)
                .withShardsNum(MilvusConstants.DEFAULT_SHARD_NUMBER)
                .build();
        R<RpcStatus> createResponse = milvusClient.createCollection(createParam);
        if (createResponse.getStatus() != 0) {
            throw new RuntimeException("创建用户长期记忆 Collection 失败: " + createResponse.getMessage());
        }

        CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName("vector")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.L2)
                .withExtraParam("{\"nlist\":128}")
                .withSyncMode(Boolean.FALSE)
                .build();
        R<RpcStatus> indexResponse = milvusClient.createIndex(indexParam);
        if (indexResponse.getStatus() != 0) {
            throw new RuntimeException("创建用户长期记忆向量索引失败: " + indexResponse.getMessage());
        }
        logger.info("已创建用户长期记忆 Milvus collection={}", collectionName);
    }

    private boolean collectionExists(String collectionName) {
        R<Boolean> response = milvusClient.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build());
        if (response.getStatus() != 0) {
            throw new RuntimeException("检查用户长期记忆 Collection 失败: " + response.getMessage());
        }
        return Boolean.TRUE.equals(response.getData());
    }

    private void loadCollection(String collectionName) {
        R<RpcStatus> response = milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build());
        if (response.getStatus() != 0 && response.getStatus() != 65535) {
            throw new RuntimeException("加载用户长期记忆 Collection 失败: " + response.getMessage());
        }
    }

    private String getCollectionName(Long userId) {
        return COLLECTION_PREFIX + userId;
    }

    private String truncateContent(String content) {
        if (content.length() <= MilvusConstants.CONTENT_MAX_LENGTH) {
            return content;
        }
        return content.substring(0, MilvusConstants.CONTENT_MAX_LENGTH);
    }

    public record MemorySearchResult(String id, String content, float score, String metadata) {
    }
}
