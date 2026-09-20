package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls an OpenAI-compatible /rerank endpoint hosting BAAI/bge-reranker-v2-m3.
 */
@Service
public class BgeRerankerService {

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Duration timeout;

    public BgeRerankerService(@Value("${rag.hybrid.rerank.base-url:}") String baseUrl,
                              @Value("${rag.hybrid.rerank.api-key:}") String apiKey,
                              @Value("${rag.hybrid.rerank.model:BAAI/bge-reranker-v2-m3}") String model,
                              @Value("${rag.hybrid.rerank.timeout-ms:30000}") long timeoutMs) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build(), new ObjectMapper(),
                baseUrl, apiKey, model, Duration.ofMillis(timeoutMs));
    }

    BgeRerankerService(HttpClient client, ObjectMapper objectMapper, String baseUrl, String apiKey,
                       String model, Duration timeout) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.timeout = timeout;
    }

    public List<RerankScore> rerank(String query, List<String> documents) {
        if (baseUrl.isBlank()) {
            throw new RerankUnavailableException("BGE reranker endpoint is not configured");
        }
        if (documents.isEmpty()) {
            return List.of();
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("model", model);
            payload.put("query", query);
            payload.put("documents", documents);
            payload.put("return_documents", false);

            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/rerank"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)));
            if (!apiKey.isBlank()) {
                request.header("Authorization", "Bearer " + apiKey);
            }

            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RerankUnavailableException("BGE reranker returned HTTP " + response.statusCode());
            }
            return parseScores(response.body());
        } catch (RerankUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RerankUnavailableException("BGE reranker call failed: " + exception.getMessage(), exception);
        }
    }

    private List<RerankScore> parseScores(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode items = root.path("results");
            if (!items.isArray()) items = root.path("data");
            if (!items.isArray()) items = root.path("result").path("items");
            if (!items.isArray()) {
                throw new RerankUnavailableException("BGE reranker response has no result list");
            }
            List<RerankScore> scores = new ArrayList<>();
            for (JsonNode item : items) {
                if (!item.has("index")) continue;
                JsonNode score = item.has("relevance_score") ? item.get("relevance_score") : item.get("score");
                if (score != null && score.isNumber()) {
                    scores.add(new RerankScore(item.get("index").asInt(), score.asDouble()));
                }
            }
            if (scores.isEmpty()) {
                throw new RerankUnavailableException("BGE reranker response has no usable scores");
            }
            return scores;
        } catch (RerankUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RerankUnavailableException("Cannot parse BGE reranker response", exception);
        }
    }

    public record RerankScore(int index, double score) {
    }

    public static class RerankUnavailableException extends RuntimeException {
        public RerankUnavailableException(String message) { super(message); }
        public RerankUnavailableException(String message, Throwable cause) { super(message, cause); }
    }
}
