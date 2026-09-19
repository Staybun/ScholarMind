package org.example.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.dto.PaperDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Parses paper files into clean text that can be chunked and embedded.
 */
@Service
public class PaperDocumentParserService {

    private static final Logger logger = LoggerFactory.getLogger(PaperDocumentParserService.class);

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}&&[^\r\n\t]]");
    private static final Pattern MULTI_SPACE = Pattern.compile("[ \t\\x0B\f\r]+");
    private static final Pattern REPEATED_NEWLINES = Pattern.compile("\\n{3,}");
    private static final Pattern HYPHENATED_LINE_BREAK = Pattern.compile("(?i)([a-z])-\\n([a-z])");
    private static final Pattern PDF_LINE_BREAK = Pattern.compile("(?<![。！？.!?:;；：])\\n(?!\\n)");

    public PaperDocument parse(Path path) throws IOException {
        String fileName = path.getFileName().toString();
        String extension = getExtension(fileName);

        PaperDocument document = switch (extension) {
            case "pdf" -> parsePdf(path);
            case "txt", "md", "markdown" -> parseTextFile(path);
            default -> throw new IllegalArgumentException("不支持的论文文件格式: " + extension);
        };

        document.setFileName(fileName);
        document.setFilePath(path.toAbsolutePath().normalize().toString());
        document.setTitle(inferTitle(document));

        logger.info("论文解析完成: {}, 页数: {}, 清洗后长度: {}",
                fileName, document.getPageCount(), document.getContent().length());
        return document;
    }

    private PaperDocument parsePdf(Path path) throws IOException {
        PaperDocument document = new PaperDocument();
        StringBuilder content = new StringBuilder();

        try (PDDocument pdf = PDDocument.load(path.toFile())) {
            int pageCount = pdf.getNumberOfPages();
            document.setPageCount(pageCount);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            for (int page = 1; page <= pageCount; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);

                String pageText = cleanText(stripper.getText(pdf));
                if (pageText.isBlank()) {
                    continue;
                }

                PaperDocument.PageText pageTextDto = new PaperDocument.PageText();
                pageTextDto.setPageNumber(page);
                pageTextDto.setText(pageText);
                pageTextDto.setStartIndex(content.length());

                content.append("\n\n[Page ").append(page).append("]\n").append(pageText);

                pageTextDto.setEndIndex(content.length());
                document.getPages().add(pageTextDto);
            }
        }

        document.setContent(content.toString().trim());
        return document;
    }

    private PaperDocument parseTextFile(Path path) throws IOException {
        PaperDocument document = new PaperDocument();
        String content = cleanText(Files.readString(path));

        PaperDocument.PageText pageText = new PaperDocument.PageText();
        pageText.setPageNumber(1);
        pageText.setText(content);
        pageText.setStartIndex(0);
        pageText.setEndIndex(content.length());

        document.setPageCount(1);
        document.setContent(content);
        document.getPages().add(pageText);
        return document;
    }

    private String cleanText(String rawText) {
        if (rawText == null) {
            return "";
        }

        String text = rawText.replace("\r\n", "\n").replace('\r', '\n');
        text = CONTROL_CHARS.matcher(text).replaceAll(" ");
        text = HYPHENATED_LINE_BREAK.matcher(text).replaceAll("$1$2");
        text = PDF_LINE_BREAK.matcher(text).replaceAll(" ");
        text = MULTI_SPACE.matcher(text).replaceAll(" ");
        text = removeLikelyPageNoise(text);
        text = REPEATED_NEWLINES.matcher(text).replaceAll("\n\n");
        return text.trim();
    }

    private String removeLikelyPageNoise(String text) {
        StringBuilder cleaned = new StringBuilder(text.length());
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.matches("^\\d+$") || trimmed.matches("(?i)^page\\s+\\d+(\\s+of\\s+\\d+)?$")) {
                continue;
            }
            cleaned.append(trimmed).append('\n');
        }
        return cleaned.toString();
    }

    private String inferTitle(PaperDocument document) {
        if (document.getContent() == null || document.getContent().isBlank()) {
            return stripExtension(document.getFileName());
        }

        for (String line : document.getContent().split("\n")) {
            String candidate = line.trim();
            if (candidate.startsWith("[Page ") || candidate.length() < 8 || candidate.length() > 220) {
                continue;
            }
            String lower = candidate.toLowerCase(Locale.ROOT);
            if (lower.equals("abstract") || lower.equals("introduction") || lower.startsWith("arxiv:")) {
                continue;
            }
            return candidate;
        }

        return stripExtension(document.getFileName());
    }

    private String getExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index < 0 ? "" : fileName.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String stripExtension(String fileName) {
        if (fileName == null) {
            return "Untitled Paper";
        }
        int index = fileName.lastIndexOf('.');
        return index < 0 ? fileName : fileName.substring(0, index);
    }
}
