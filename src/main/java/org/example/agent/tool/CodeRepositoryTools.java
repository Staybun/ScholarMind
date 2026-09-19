package org.example.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Read-only repository search tools for code-aware ReAct answers.
 */
@Component
public class CodeRepositoryTools {

    private static final Logger logger = LoggerFactory.getLogger(CodeRepositoryTools.class);

    private static final Set<String> SEARCHABLE_EXTENSIONS = Set.of(
            "java", "xml", "yml", "yaml", "properties", "js", "html", "css", "md", "txt", "json"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${scholarmind.workspace.root:.}")
    private String workspaceRoot;

    @Value("${scholarmind.workspace.max-search-results:20}")
    private int maxSearchResults;

    @Tool(description = "Search the local ScholarMind code repository for code, configuration, class names, API paths, or tool implementations. " +
            "Use this when the user asks about project code, repository structure, implementation details, APIs, config, or wants code-grounded answers.")
    public String searchCodeRepository(
            @ToolParam(description = "Plain text keyword or phrase to search for, such as class name, method name, endpoint path, or config key.")
            String query,
            @ToolParam(description = "Optional file extension filter, for example java, yml, js, xml. Leave empty to search common text/code files.")
            String extension,
            @ToolParam(description = "Maximum matching lines to return. Default 20, max 80.")
            Integer limit) {
        try {
            if (query == null || query.trim().isEmpty()) {
                return error("query cannot be blank");
            }

            String needle = query.toLowerCase(Locale.ROOT).trim();
            String extFilter = extension == null ? "" : extension.replace(".", "").toLowerCase(Locale.ROOT).trim();
            int actualLimit = normalizeLimit(limit, maxSearchResults, 80);
            List<SearchHit> hits = new ArrayList<>();
            Path root = rootPath();

            try (var stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> !shouldSkip(path))
                        .filter(path -> isSearchable(path, extFilter))
                        .forEach(path -> searchFile(root, path, needle, hits, actualLimit));
            }

            SearchResult result = new SearchResult();
            result.setSuccess(true);
            result.setQuery(query);
            result.setHits(hits);
            result.setTruncated(hits.size() >= actualLimit);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            logger.error("searchCodeRepository failed", e);
            return error("searchCodeRepository failed: " + e.getMessage());
        }
    }

    private void searchFile(Path root, Path path, String needle, List<SearchHit> hits, int limit) {
        if (hits.size() >= limit) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size() && hits.size() < limit; i++) {
                String line = lines.get(i);
                if (line.toLowerCase(Locale.ROOT).contains(needle)) {
                    SearchHit hit = new SearchHit();
                    hit.setPath(root.relativize(path).toString().replace('\\', '/'));
                    hit.setLineNumber(i + 1);
                    hit.setLine(line.trim());
                    hits.add(hit);
                }
            }
        } catch (Exception ignored) {
            // Ignore files that cannot be decoded as UTF-8.
        }
    }

    private boolean isSearchable(Path path, String extensionFilter) {
        String fileName = path.getFileName().toString();
        int index = fileName.lastIndexOf('.');
        String extension = index < 0 ? "" : fileName.substring(index + 1).toLowerCase(Locale.ROOT);
        if (!extensionFilter.isBlank()) {
            return extension.equals(extensionFilter);
        }
        return SEARCHABLE_EXTENSIONS.contains(extension);
    }

    private boolean shouldSkip(Path path) {
        String normalized = rootPath().relativize(path).toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.startsWith(".git/")
                || normalized.startsWith(".idea/")
                || normalized.startsWith("target/")
                || normalized.startsWith("volumes/")
                || normalized.contains("/target/")
                || normalized.contains("/node_modules/");
    }

    private Path rootPath() {
        return Paths.get(workspaceRoot).toAbsolutePath().normalize();
    }

    private int normalizeLimit(Integer requested, int defaultValue, int hardMax) {
        if (requested == null || requested <= 0) {
            return Math.min(defaultValue, hardMax);
        }
        return Math.min(requested, hardMax);
    }

    private String error(String message) {
        try {
            SearchResult result = new SearchResult();
            result.setSuccess(false);
            result.setError(message);
            result.setHits(List.of());
            return objectMapper.writeValueAsString(result);
        } catch (Exception ignored) {
            return "{\"success\":false,\"error\":\"" + message.replace("\"", "'") + "\"}";
        }
    }

    @Getter
    @Setter
    private static class SearchResult {
        private boolean success;
        private String query;
        private boolean truncated;
        private List<SearchHit> hits;
        private String error;
    }

    @Getter
    @Setter
    private static class SearchHit {
        private String path;
        private int lineNumber;
        private String line;
    }
}
