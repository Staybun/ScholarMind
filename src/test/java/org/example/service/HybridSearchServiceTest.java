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
        var semanticOnly = result("semantic", "A generic related section", 0.1f);
        var shared = result("shared", "Transformer attention is evaluated on a benchmark.", 0.2f);
        when(dense.searchSimilarDocuments("transformer attention", 20)).thenReturn(List.of(semanticOnly, shared));
        when(bm25.search("transformer attention", 20)).thenReturn(List.of(
                new KeywordSearchService.KeywordHit(shared, 7.0)));

        HybridSearchService service = new HybridSearchService(dense, bm25, true, 20, 60, 12, 0.7);
        var results = service.search("transformer attention", 2);

        assertEquals("shared", results.get(0).getId());
        assertEquals(List.of("DENSE", "BM25"), results.get(0).getRetrievalSources());
        assertTrue(results.get(0).getRerankScore() > results.get(1).getRerankScore());
    }

    @Test
    void returnsBm25ResultsWhenDenseRetrievalIsUnavailable() {
        VectorSearchService dense = mock(VectorSearchService.class);
        KeywordSearchService bm25 = mock(KeywordSearchService.class);
        var keyword = result("keyword", "BM25 retrieves exact terminology.", 0);
        when(dense.searchSimilarDocuments("bm25", 20)).thenThrow(new IllegalStateException("offline"));
        when(bm25.search("bm25", 20)).thenReturn(List.of(new KeywordSearchService.KeywordHit(keyword, 3.0)));

        var results = new HybridSearchService(dense, bm25, true, 20, 60, 12, 0.7).search("bm25", 1);

        assertEquals("keyword", results.get(0).getId());
        assertEquals(List.of("BM25"), results.get(0).getRetrievalSources());
    }

    private VectorSearchService.SearchResult result(String id, String content, float score) {
        VectorSearchService.SearchResult result = new VectorSearchService.SearchResult();
        result.setId(id);
        result.setContent(content);
        result.setScore(score);
        return result;
    }
}
