package org.example.service;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.embeddings.TextEmbeddingOutput;
import com.alibaba.dashscope.embeddings.TextEmbeddingResultItem;
import com.alibaba.dashscope.exception.NoApiKeyException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 向量嵌入服务
 * 使用阿里云 DashScope Text Embedding API
 */
@Service
public class VectorEmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(VectorEmbeddingService.class);

    @Value("${dashscope.api.key}")
    private String apiKey;

    @Value("${dashscope.embedding.model}")
    private String model;

    private TextEmbedding textEmbedding;

    @PostConstruct
    public void init() {
        textEmbedding = new TextEmbedding();
    }

    private void validateCredentials() {
        if (apiKey == null || apiKey.isBlank() || "your-api-key-here".equals(apiKey)) {
            throw new IllegalStateException("请配置 DASHSCOPE_API_KEY 后使用向量功能");
        }
    }

    /**
     * 生成向量嵌入
     * 调用阿里云 DashScope Text Embedding API
     * 
     * @param content 文本内容
     * @return 向量嵌入（浮点数列表）
     */
    public List<Float> generateEmbedding(String content) {
        try {
            if (content == null || content.trim().isEmpty()) {
                logger.warn("内容为空，无法生成向量");
                throw new IllegalArgumentException("内容不能为空");
            }

            logger.debug("开始生成向量嵌入, 内容长度: {} 字符", content.length());
            
            validateCredentials();

            // 构建请求参数
            TextEmbeddingParam param = TextEmbeddingParam
                    .builder()
                    .apiKey(apiKey)
                    .model(model)
                    .texts(Collections.singletonList(content))
                    .build();

            // 调用 API
            TextEmbeddingResult result = textEmbedding.call(param);

            // 检查结果
            List<Float> floatEmbedding = getFloats(result);

            logger.info("成功生成向量嵌入, 内容长度: {} 字符, 向量维度: {}", 
                content.length(), floatEmbedding.size());

            return floatEmbedding;

        } catch (NoApiKeyException e) {
            logger.error("API Key 未设置或无效", e);
            throw new RuntimeException("API Key 未设置，请配置 dashscope.api.key", e);
        } catch (Exception e) {
            logger.error("生成向量嵌入失败, 内容长度: {}", content != null ? content.length() : 0, e);
            throw new RuntimeException("生成向量嵌入失败: " + e.getMessage(), e);
        }
    }

    @NotNull
    private static List<Float> getFloats(TextEmbeddingResult result) {
        if (result == null || result.getOutput() == null || result.getOutput().getEmbeddings() == null) {
            throw new RuntimeException("DashScope API 返回空结果");
        }

        TextEmbeddingOutput output = result.getOutput();
        List<TextEmbeddingResultItem> embeddings = output.getEmbeddings();

        if (embeddings.isEmpty()) {
            throw new RuntimeException("DashScope API 返回空向量列表");
        }

        // 获取第一个文本的向量
        List<Double> embeddingDoubles = embeddings.get(0).getEmbedding();

        // 转换为 List<Float>
        List<Float> floatEmbedding = new ArrayList<>(embeddingDoubles.size());
        for (Double value : embeddingDoubles) {
            floatEmbedding.add(value.floatValue());
        }
        return floatEmbedding;
    }

    /**
     * 批量生成向量嵌入
     * 
     * @param contents 文本内容列表
     * @return 向量嵌入列表
     */
    public List<List<Float>> generateEmbeddings(List<String> contents) {
        try {
            if (contents == null || contents.isEmpty()) {
                logger.warn("内容列表为空，无法生成向量");
                return Collections.emptyList();
            }

            logger.info("开始批量生成向量嵌入, 数量: {}", contents.size());
            
            validateCredentials();

            // 构建请求参数 - 批量输入
            TextEmbeddingParam param = TextEmbeddingParam
                    .builder()
                    .apiKey(apiKey)
                    .model(model)
                    .texts(contents)
                    .build();

            // 调用 API
            TextEmbeddingResult result = textEmbedding.call(param);

            // 检查结果
            if (result == null || result.getOutput() == null || result.getOutput().getEmbeddings() == null) {
                throw new RuntimeException("批量 DashScope API 返回空结果");
            }

            List<TextEmbeddingResultItem> embeddingItems = result.getOutput().getEmbeddings();
            
            if (embeddingItems.isEmpty()) {
                throw new RuntimeException("批量 DashScope API 返回空向量列表");
            }

            // 转换结果
            List<List<Float>> embeddings = new ArrayList<>();
            for (TextEmbeddingResultItem item : embeddingItems) {
                List<Double> embeddingDoubles = item.getEmbedding();
                List<Float> embedding = new ArrayList<>(embeddingDoubles.size());
                for (Double value : embeddingDoubles) {
                    embedding.add(value.floatValue());
                }
                embeddings.add(embedding);
            }

            logger.info("成功批量生成向量嵌入, 数量: {}, 维度: {}", 
                embeddings.size(), 
                embeddings.isEmpty() ? 0 : embeddings.get(0).size());

            return embeddings;

        } catch (NoApiKeyException e) {
            logger.error("批量调用时 API Key 未设置或无效", e);
            throw new RuntimeException("API Key 未设置，请配置 dashscope.api.key", e);
        } catch (Exception e) {
            logger.error("批量生成向量嵌入失败", e);
            throw new RuntimeException("批量生成向量嵌入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成查询向量
     * 
     * @param query 查询文本
     * @return 向量嵌入
     */
    public List<Float> generateQueryVector(String query) {
        return generateEmbedding(query);
    }

    /**
     * 计算两个向量的余弦相似度
     * 
     * @param vector1 向量1
     * @param vector2 向量2
     * @return 余弦相似度 [-1, 1]
     */
    public float calculateCosineSimilarity(List<Float> vector1, List<Float> vector2) {
        if (vector1.size() != vector2.size()) {
            throw new IllegalArgumentException("向量维度不匹配");
        }

        float dotProduct = 0.0f;
        float norm1 = 0.0f;
        float norm2 = 0.0f;

        for (int i = 0; i < vector1.size(); i++) {
            dotProduct += vector1.get(i) * vector2.get(i);
            norm1 += vector1.get(i) * vector1.get(i);
            norm2 += vector2.get(i) * vector2.get(i);
        }

        return dotProduct / (float) (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
