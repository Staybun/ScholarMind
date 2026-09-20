package org.example.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridSearchServiceTest {

    @Test
    void fusesDenseAndBm25CandidatesThenReranksTheCombinedList() {
        VectorSearchService dense = mock(VectorSearchService.class);
        KeywordSearchService bm25 = mock(KeywordSearchService.class);
        BgeRerankerService reranker = mock(BgeRerankerService.class);
        var semanticOnly = result("semantic", "A generic related section", 0.1f);
        var shared = result("shared", "Transformer attention is evaluated on a benchmark.", 0.2f);
        when(dense.searchSimilarDocuments("transformer attention", 20)).thenReturn(List.of(semanticOnly, shared));
        when(bm25.search("transformer attention", 20)).thenReturn(List.of(
                new KeywordSearchService.KeywordHit(shared, 7.0)));
        when(reranker.rerank("transformer attention", List.of(
                "Transformer attention is evaluated on a benchmark.", "A generic related section")))
                .thenReturn(List.of(new BgeRerankerService.RerankScore(0, 0.95),
                        new BgeRerankerService.RerankScore(1, 0.12)));

        HybridSearchService service = new HybridSearchService(dense, bm25, reranker, true, 20, 60, 12, 0.7);
        var results = service.search("transformer attention", 2);

        assertEquals("shared", results.get(0).getId());
        assertEquals(List.of("DENSE", "BM25"), results.get(0).getRetrievalSources());
        assertTrue(results.get(0).getRerankScore() > results.get(1).getRerankScore());
        assertEquals("BGE_RERANKER_V2_M3", results.get(0).getRerankStrategy());
    }

    @Test
    void returnsBm25ResultsWhenDenseRetrievalIsUnavailable() {
        VectorSearchService dense = mock(VectorSearchService.class);
        KeywordSearchService bm25 = mock(KeywordSearchService.class);
        BgeRerankerService reranker = mock(BgeRerankerService.class);
        var keyword = result("keyword", "BM25 retrieves exact terminology.", 0);
        when(dense.searchSimilarDocuments("bm25", 20)).thenThrow(new IllegalStateException("offline"));
        when(bm25.search("bm25", 20)).thenReturn(List.of(new KeywordSearchService.KeywordHit(keyword, 3.0)));
        when(reranker.rerank("bm25", List.of("BM25 retrieves exact terminology.")))
                .thenThrow(new BgeRerankerService.RerankUnavailableException("timeout"));

        var results = new HybridSearchService(dense, bm25, reranker, true, 20, 60, 12, 0.7).search("bm25", 1);

        assertEquals("keyword", results.get(0).getId());
        assertEquals(List.of("BM25"), results.get(0).getRetrievalSources());
        assertEquals("LOCAL_LEXICAL_FALLBACK", results.get(0).getRerankStrategy());
    }

    private VectorSearchService.SearchResult result(String id, String content, float score) {
        VectorSearchService.SearchResult result = new VectorSearchService.SearchResult();
        result.setId(id);
        result.setContent(content);
        result.setScore(score);
        return result;
    }
}
