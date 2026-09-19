package org.example.memory.semantic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.*;
import io.milvus.param.collection.*;
import io.milvus.param.dml.*;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.ArrayList;

@Component
public class MilvusSemanticMemoryStore implements SemanticVectorStore {
    private final ObjectProvider<MilvusServiceClient> clients;
    private final ObjectMapper mapper;
    private final String collection;
    private final int dimension;
    private volatile boolean ready;

    public MilvusSemanticMemoryStore(ObjectProvider<MilvusServiceClient> clients, ObjectMapper mapper,
            @Value("${scholarmind.memory.semantic.collection:scholarmind_semantic_memories}") String collection,
            @Value("${scholarmind.memory.semantic.dimension:1024}") int dimension) {
        this.clients = clients;
        this.mapper = mapper;
        this.collection = collection;
        this.dimension = dimension;
    }

    public void upsert(String id, String scope, List<Float> vector) {
        MilvusServiceClient client = prepare(vector);
        check(client.upsert(UpsertParam.newBuilder().withCollectionName(collection).withFields(List.of(
                new InsertParam.Field("id", List.of(id)),
                new InsertParam.Field("memory_scope", List.of(scope)),
                new InsertParam.Field("vector", List.of(vector)))).build()));
    }

    public List<VectorHit> search(String scope, List<Float> vector, int topK) {
        MilvusServiceClient client = prepare(vector);
        var response = client.search(SearchParam.newBuilder().withCollectionName(collection)
                .withVectorFieldName("vector").withVectors(List.of(vector)).withMetricType(MetricType.COSINE)
                .withTopK(topK).withExpr("memory_scope == " + literal(scope))
                .withOutFields(List.of("id")).withParams("{\"ef\":64}").build());
        check(response);
        SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
        List<VectorHit> hits = new ArrayList<>();
        for (var hit : wrapper.getIDScore(0)) hits.add(new VectorHit((String) hit.get("id"), hit.getScore()));
        return hits;
    }

    public void delete(String id) {
        check(clients.getObject().delete(DeleteParam.newBuilder().withCollectionName(collection)
                .withExpr("id == " + literal(id)).build()));
    }

    private MilvusServiceClient prepare(List<Float> vector) {
        if (vector.size() != dimension) throw new IllegalArgumentException("Semantic embedding dimension mismatch: " + vector.size());
        MilvusServiceClient client = clients.getObject();
        if (!ready) initialize(client);
        return client;
    }

    private synchronized void initialize(MilvusServiceClient client) {
        if (ready) return;
        var exists = client.hasCollection(HasCollectionParam.newBuilder().withCollectionName(collection).build());
        check(exists);
        if (!Boolean.TRUE.equals(exists.getData())) {
            CollectionSchemaParam schema = CollectionSchemaParam.newBuilder().withEnableDynamicField(false)
                    .addFieldType(FieldType.newBuilder().withName("id").withDataType(DataType.VarChar)
                            .withMaxLength(36).withPrimaryKey(true).build())
                    .addFieldType(FieldType.newBuilder().withName("memory_scope").withDataType(DataType.VarChar)
                            .withMaxLength(512).build())
                    .addFieldType(FieldType.newBuilder().withName("vector").withDataType(DataType.FloatVector)
                            .withDimension(dimension).build()).build();
            check(client.createCollection(CreateCollectionParam.newBuilder().withCollectionName(collection)
                    .withSchema(schema).withShardsNum(1).build()));
        }
        check(client.createIndex(CreateIndexParam.newBuilder().withCollectionName(collection).withFieldName("vector")
                .withIndexType(IndexType.HNSW).withMetricType(MetricType.COSINE)
                .withExtraParam("{\"M\":16,\"efConstruction\":128}").withSyncMode(true).build()));
        check(client.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(collection).build()));
        ready = true;
    }

    private String literal(String value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid Milvus filter", e); }
    }
    private void check(R<?> response) {
        if (response.getStatus() != 0) throw new IllegalStateException("Semantic Milvus operation failed: " + response.getMessage());
    }
}
