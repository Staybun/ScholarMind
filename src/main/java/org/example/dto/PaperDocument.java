package org.example.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed paper text and lightweight metadata for RAG indexing.
 */
@Getter
@Setter
public class PaperDocument {

    private String fileName;
    private String filePath;
    private String title;
    private String content;
    private int pageCount;
    private List<PageText> pages = new ArrayList<>();

    @Getter
    @Setter
    public static class PageText {
        private int pageNumber;
        private String text;
        private int startIndex;
        private int endIndex;
    }
}
