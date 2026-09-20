package org.example.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Combines dense retrieval and BM25 with reciprocal-rank fusion, then reranks the fused candidates. */
@Service
public class HybridSearchService {

    private static final Logger logger = LoggerFactory.getLogger(HybridSearchService.class);

    private final VectorSearchService vectorSearchService;
    private final KeywordSearchService keywordSearchService;
    private final boolean enabled;
    private final int candidateTopK;
    private final int rrfK;
    private final int rerankTopK;
    private final double rrfWeight;

    public HybridSearchService(VectorSearchService vectorSearchService,
                               KeywordSearchService keywordSearchService,
                               @Value("${rag.hybrid.enabled:true}") boolean enabled,
                               @Value("${rag.hybrid.candidate-top-k:20}") int candidateTopK,
                               @Value("${rag.hybrid.rrf-k:60}") int rrfK,
                               @Value("${rag.hybrid.rerank-top-k:12}") int rerankTopK,
                               @Value("${rag.hybrid.rerank-rrf-weight:0.7}") double rrfWeight) {
        this.vectorSearchService = vectorSearchService;
        this.keywordSearchService = keywordSearchService;
        this.enabled = enabled;
        this.candidateTopK = candidateTopK;
        this.rrfK = rrfK;
        this.rerankTopK = rerankTopK;
        this.rrfWeight = rrfWeight;
    }

    public List<VectorSearchService.SearchResult> search(String query, int topK) {
        if (!enabled) {
            return vectorSearchService.searchSimilarDocuments(query, topK);
        }
        int candidates = Math.max(Math.max(topK, candidateTopK), 1);
        Map<String, Candidate> merged = new LinkedHashMap<>();

        try {
            List<VectorSearchService.SearchResult> dense = vectorSearchService.searchSimilarDocuments(query, candidates);
            for (int index = 0; index < dense.size(); index++) {
                VectorSearchService.SearchResult item = dense.get(index);
                Candidate candidate = merged.computeIfAbsent(item.getId(), ignored -> new Candidate(item));
                candidate.denseRank = index + 1;
                candidate.denseScore = item.getScore();
                candidate.rrfScore += 1.0 / (rrfK + candidate.denseRank);
            }
        } catch (Exception error) {
            // A keyword result can still answer a query if the embedding provider or vector store is unavailable.
            logger.warn("Dense retrieval unavailable; using BM25-only results: {}", error.getMessage());
        }

        List<KeywordSearchService.KeywordHit> keywords = keywordSearchService.search(query, candidates);
        for (int index = 0; index < keywords.size(); index++) {
            KeywordSearchService.KeywordHit hit = keywords.get(index);
            VectorSearchService.SearchResult item = hit.result();
            Candidate candidate = merged.computeIfAbsent(item.getId(), ignored -> new Candidate(item));
            candidate.keywordRank = index + 1;
            candidate.keywordScore = hit.score();
            candidate.rrfScore += 1.0 / (rrfK + candidate.keywordRank);
        }

        List<Candidate> ranked = new ArrayList<>(merged.values());
        ranked.sort(Comparator.comparingDouble((Candidate candidate) -> candidate.rrfScore).reversed());
        int rerankLimit = Math.min(Math.max(rerankTopK, topK), ranked.size());
        for (int index = 0; index < rerankLimit; index++) {
            Candidate candidate = ranked.get(index);
            candidate.rerankScore = rrfWeight * candidate.rrfScore
                    + (1 - rrfWeight) * lexicalRelevance(query, candidate.result.getContent());
        }
        ranked.subList(0, rerankLimit).sort(Comparator.comparingDouble((Candidate candidate) -> candidate.rerankScore).reversed());

        return ranked.stream().limit(topK).map(Candidate::toSearchResult).toList();
    }

    private double lexicalRelevance(String query, String content) {
        Set<String> queryTokens = AcademicTextTokenizer.uniqueTokens(query);
        if (queryTokens.isEmpty()) {
            return 0;
        }
        Set<String> contentTokens = AcademicTextTokenizer.uniqueTokens(content);
        long matched = queryTokens.stream().filter(contentTokens::contains).count();
        double coverage = (double) matched / queryTokens.size();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String normalizedContent = content == null ? "" : content.toLowerCase(Locale.ROOT);
        return Math.min(1.0, coverage + (!normalizedQuery.isBlank() && normalizedContent.contains(normalizedQuery) ? 0.15 : 0));
    }

    private static final class Candidate {
        private final VectorSearchService.SearchResult result;
        private int denseRank;
        private int keywordRank;
        private double denseScore;
        private double keywordScore;
        private double rrfScore;
        private double rerankScore;

        private Candidate(VectorSearchService.SearchResult result) {
            this.result = result;
        }

        private VectorSearchService.SearchResult toSearchResult() {
            result.setDenseRank(denseRank == 0 ? null : denseRank);
            result.setKeywordRank(keywordRank == 0 ? null : keywordRank);
            result.setDenseScore(denseRank == 0 ? null : denseScore);
            result.setKeywordScore(keywordRank == 0 ? null : keywordScore);
            result.setFusionScore(rrfScore);
            result.setRerankScore(rerankScore);
            result.setRetrievalSources(denseRank > 0 && keywordRank > 0 ? List.of("DENSE", "BM25")
                    : denseRank > 0 ? List.of("DENSE") : List.of("BM25"));
            result.setScore((float) rerankScore);
            return result;
        }
    }
}
