package org.example.service;

import org.example.dto.DocumentChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class KeywordSearchServiceTest {

    @Test
    void ranksExactAcademicTermsWithBm25AndReplacesOldPaperChunks() {
        KeywordSearchService service = new KeywordSearchService(mock(PaperDocumentParserService.class),
                mock(DocumentChunkService.class), "missing-directory");
        service.replaceDocument("papers/attention.pdf", List.of(chunk("attention.pdf", "Transformer attention improves token representations.")));
        service.replaceDocument("papers/baseline.pdf", List.of(chunk("baseline.pdf", "A convolutional baseline is trained on images.")));

        var hits = service.search("transformer attention", 5);

        assertEquals("attention.pdf", hits.get(0).result().getSourceFileName());
        assertTrue(hits.get(0).score() > 0);

        service.replaceDocument("papers/attention.pdf", List.of(chunk("attention.pdf", "Replaced paper content about retrieval.")));
        assertTrue(service.search("transformer attention", 5).isEmpty());
    }

    private DocumentChunk chunk(String sourceFileName, String content) {
        DocumentChunk chunk = new DocumentChunk(content, 0, content.length(), 0);
        chunk.setDocumentTitle("Test Paper");
        chunk.setSourceFileName(sourceFileName);
        chunk.setTitle("Abstract");
        chunk.setPageStart(1);
        chunk.setPageEnd(1);
        return chunk;
    }
}
