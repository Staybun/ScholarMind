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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Safe read-only file tools for ScholarMind ReAct agents.
 */
@Component
public class FileSystemTools {

    private static final Logger logger = LoggerFactory.getLogger(FileSystemTools.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${scholarmind.workspace.root:.}")
    private String workspaceRoot;

    @Value("${scholarmind.workspace.max-read-chars:12000}")
    private int maxReadChars;

    @Value("${scholarmind.workspace.max-search-results:20}")
    private int maxSearchResults;

    @Tool(description = "List readable files in the ScholarMind workspace. " +
            "Use this before reading a file when the exact path is unknown. " +
            "This tool is read-only and skips build outputs, VCS metadata, vector database volumes, and IDE metadata.")
    public String listWorkspaceFiles(
            @ToolParam(description = "Optional keyword contained in the file path or file name. Leave empty to list common project files.")
            String keyword,
            @ToolParam(description = "Maximum number of file paths to return. Default 20, max 50.")
            Integer limit) {
        try {
            int actualLimit = normalizeLimit(limit, maxSearchResults, 50);
            String safeKeyword = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT).trim();
            List<String> files = new ArrayList<>();

            try (var stream = Files.walk(rootPath())) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> !shouldSkip(path))
                        .filter(path -> safeKeyword.isBlank()
                                || rootPath().relativize(path).toString().toLowerCase(Locale.ROOT).contains(safeKeyword))
                        .limit(actualLimit)
                        .forEach(path -> files.add(rootPath().relativize(path).toString().replace('\\', '/')));
            }

            return objectMapper.writeValueAsString(new FileListResult(true, files, null));
        } catch (Exception e) {
            logger.error("listWorkspaceFiles failed", e);
            return error("listWorkspaceFiles failed: " + e.getMessage());
        }
    }

    @Tool(description = "Read a text file from the ScholarMind workspace by relative path. " +
            "Use this for source code, configuration, Markdown notes, extracted text files, or uploaded text papers. " +
            "For PDF content questions, prefer queryPaperKnowledge because PDFs are indexed after parsing.")
    public String readWorkspaceFile(
            @ToolParam(description = "Relative file path under the project workspace, for example pom.xml or src/main/resources/application.yml.")
            String relativePath,
            @ToolParam(description = "Maximum characters to read. Default uses configured limit; max 50000.")
            Integer maxChars) {
        try {
            Path path = resolveSafePath(relativePath);
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                return error("File not found: " + relativePath);
            }
            if (isLikelyBinary(path)) {
                return error("Refusing to read likely binary file. Use paper retrieval tools for PDF content.");
            }

            int actualMaxChars = normalizeLimit(maxChars, maxReadChars, 50000);
            String content = Files.readString(path, StandardCharsets.UTF_8);
            boolean truncated = content.length() > actualMaxChars;
            if (truncated) {
                content = content.substring(0, actualMaxChars);
            }

            FileReadResult result = new FileReadResult();
            result.setSuccess(true);
            result.setPath(rootPath().relativize(path).toString().replace('\\', '/'));
            result.setTruncated(truncated);
            result.setContent(content);
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            logger.error("readWorkspaceFile failed", e);
            return error("readWorkspaceFile failed: " + e.getMessage());
        }
    }

    private Path rootPath() {
        return Paths.get(workspaceRoot).toAbsolutePath().normalize();
    }

    private Path resolveSafePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath cannot be blank");
        }
        Path root = rootPath();
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Path is outside workspace: " + relativePath);
        }
        return resolved;
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

    private boolean isLikelyBinary(Path path) throws IOException {
        String lowerName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".pdf")
                || lowerName.endsWith(".png")
                || lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".gif")
                || lowerName.endsWith(".jar")
                || lowerName.endsWith(".class")) {
            return true;
        }
        byte[] bytes = Files.readAllBytes(path);
        int length = Math.min(bytes.length, 2048);
        for (int i = 0; i < length; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private int normalizeLimit(Integer requested, int defaultValue, int hardMax) {
        if (requested == null || requested <= 0) {
            return Math.min(defaultValue, hardMax);
        }
        return Math.min(requested, hardMax);
    }

    private String error(String message) {
        try {
            return objectMapper.writeValueAsString(new FileListResult(false, List.of(), message));
        } catch (Exception ignored) {
            return "{\"success\":false,\"error\":\"" + message.replace("\"", "'") + "\"}";
        }
    }

    @Getter
    @Setter
    private static class FileListResult {
        private boolean success;
        private List<String> files;
        private String error;

        FileListResult(boolean success, List<String> files, String error) {
            this.success = success;
            this.files = files;
            this.error = error;
        }
    }

    @Getter
    @Setter
    private static class FileReadResult {
        private boolean success;
        private String path;
        private boolean truncated;
        private String content;
    }
}
