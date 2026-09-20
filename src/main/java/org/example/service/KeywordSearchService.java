package org.example.service;

import jakarta.annotation.PostConstruct;
import org.example.dto.DocumentChunk;
import org.example.dto.PaperDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * In-process BM25 index for uploaded paper chunks. The index is rebuilt from
 * the upload directory on startup and updated after each successful upload.
 */
@Service
public class KeywordSearchService {

    private static final Logger logger = LoggerFactory.getLogger(KeywordSearchService.class);
    private static final double K1 = 1.2;
    private static final double B = 0.75;

    private final PaperDocumentParserService parser;
    private final DocumentChunkService chunkService;
    private final String uploadPath;
    private final Map<String, IndexedChunk> documents = new HashMap<>();
    private final Map<String, Integer> documentFrequency = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private volatile double averageDocumentLength = 1.0;

    public KeywordSearchService(PaperDocumentParserService parser,
                                DocumentChunkService chunkService,
                                @Value("${file.upload.path}") String uploadPath) {
        this.parser = parser;
        this.chunkService = chunkService;
        this.uploadPath = uploadPath;
    }

    @PostConstruct
    public void rebuildFromUploadDirectory() {
        Path directory = Paths.get(uploadPath).normalize();
        File[] files = directory.toFile().listFiles((ignored, name) -> supported(name));
        if (files == null || files.length == 0) {
            logger.info("BM25 index starts empty; no uploaded papers found in {}", directory);
            return;
        }

        int indexed = 0;
        for (File file : files) {
            try {
                PaperDocument paper = parser.parse(file.toPath());
                replaceDocument(file.toPath().toString(), chunkService.chunkPaper(paper));
                indexed++;
            } catch (Exception error) {
                logger.warn("Skipping paper during BM25 index rebuild: {}", file.getName(), error);
            }
        }
        logger.info("BM25 index rebuilt from {} uploaded papers with {} chunks", indexed, size());
    }

    public void replaceDocument(String filePath, List<DocumentChunk> chunks) {
        String source = normalizeSource(filePath);
        lock.writeLock().lock();
        try {
            documents.entrySet().removeIf(entry -> entry.getValue().source().equals(source));
            for (DocumentChunk chunk : chunks) {
                String id = stableChunkId(source, chunk.getChunkIndex());
                List<String> tokens = AcademicTextTokenizer.tokenize(chunk.getContent());
                if (!tokens.isEmpty()) {
                    documents.put(id, IndexedChunk.from(id, source, chunk, tokens));
                }
            }
            rebuildStatistics();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<KeywordHit> search(String query, int limit) {
        List<String> queryTokens = AcademicTextTokenizer.tokenize(query);
        if (queryTokens.isEmpty() || limit <= 0) {
            return List.of();
        }

        lock.readLock().lock();
        try {
            int documentCount = documents.size();
            if (documentCount == 0) {
                return List.of();
            }

            List<KeywordHit> hits = new ArrayList<>();
            for (IndexedChunk document : documents.values()) {
                double score = bm25(queryTokens, document, documentCount);
                if (score > 0) {
                    hits.add(new KeywordHit(document.toSearchResult(), score));
                }
            }
            hits.sort(Comparator.comparingDouble(KeywordHit::score).reversed());
            return hits.subList(0, Math.min(limit, hits.size()));
        } finally {
            lock.readLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return documents.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    private double bm25(List<String> queryTokens, IndexedChunk document, int documentCount) {
        double score = 0;
        Set<String> distinctQueryTokens = new HashSet<>(queryTokens);
        for (String token : distinctQueryTokens) {
            int termFrequency = document.termFrequency().getOrDefault(token, 0);
            if (termFrequency == 0) {
                continue;
            }
            int frequency = documentFrequency.getOrDefault(token, 0);
            double inverseDocumentFrequency = Math.log(1 + (documentCount - frequency + 0.5) / (frequency + 0.5));
            double denominator = termFrequency + K1 * (1 - B + B * document.length() / averageDocumentLength);
            score += inverseDocumentFrequency * (termFrequency * (K1 + 1)) / denominator;
        }
        return score;
    }

    private void rebuildStatistics() {
        documentFrequency.clear();
        long totalLength = 0;
        for (IndexedChunk document : documents.values()) {
            totalLength += document.length();
            for (String term : document.termFrequency().keySet()) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }
        averageDocumentLength = documents.isEmpty() ? 1.0 : Math.max(1.0, (double) totalLength / documents.size());
    }

    static String stableChunkId(String source, int chunkIndex) {
        return UUID.nameUUIDFromBytes((source + "_" + chunkIndex).getBytes(StandardCharsets.UTF_8)).toString();
    }

    static String normalizeSource(String filePath) {
        return Paths.get(filePath).normalize().toString().replace(File.separatorChar, '/');
    }

    private boolean supported(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".pdf") || lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown");
    }

    public record KeywordHit(VectorSearchService.SearchResult result, double score) {
    }

    private record IndexedChunk(String id, String source, String content, String documentTitle,
                                String sourceFileName, String sectionTitle, int pageStart, int pageEnd,
                                int chunkIndex, Map<String, Integer> termFrequency, int length) {
        private static IndexedChunk from(String id, String source, DocumentChunk chunk, List<String> tokens) {
            Map<String, Integer> frequencies = new HashMap<>();
            tokens.forEach(token -> frequencies.merge(token, 1, Integer::sum));
            return new IndexedChunk(id, source, chunk.getContent(), chunk.getDocumentTitle(), chunk.getSourceFileName(),
                    chunk.getTitle(), chunk.getPageStart(), chunk.getPageEnd(), chunk.getChunkIndex(), frequencies, tokens.size());
        }

        private VectorSearchService.SearchResult toSearchResult() {
            VectorSearchService.SearchResult result = new VectorSearchService.SearchResult();
            result.setId(id);
            result.setContent(content);
            result.setDocumentTitle(documentTitle);
            result.setSourceFileName(sourceFileName);
            result.setSectionTitle(sectionTitle);
            result.setPageStart(pageStart);
            result.setPageEnd(pageEnd);
            result.setChunkIndex(chunkIndex);
            result.setMetadata("{\"_source\":\"" + source.replace("\"", "\\\"") + "\"}");
            return result;
        }
    }
}
