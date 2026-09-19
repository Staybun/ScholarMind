package org.example.service;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads project-local ScholarMind skills from the skills directory.
 */
@Service
public class SkillRegistryService {

    private static final Logger logger = LoggerFactory.getLogger(SkillRegistryService.class);

    private static final Pattern FRONT_MATTER_PATTERN = Pattern.compile("^---\\s*\\R(.*?)\\R---\\s*\\R?", Pattern.DOTALL);
    private static final Pattern NAME_PATTERN = Pattern.compile("(?m)^name:\\s*(.+?)\\s*$");
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("(?m)^description:\\s*(.+?)\\s*$");

    @Value("${scholarmind.skills.root:./skills}")
    private String skillsRoot;

    public List<SkillInfo> listSkills() {
        Path root = rootPath();
        if (!Files.isDirectory(root)) {
            logger.warn("Skills root does not exist: {}", root);
            return List.of();
        }

        List<SkillInfo> skills = new ArrayList<>();
        try (var stream = Files.list(root)) {
            stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> readSkillInfo(path).ifPresent(skills::add));
        } catch (IOException e) {
            logger.error("Failed to list skills from {}", root, e);
        }
        return skills;
    }

    public String getSkillMarkdown(String skillName) {
        Path skillPath = resolveSkillPath(skillName).resolve("SKILL.md");
        if (!Files.isRegularFile(skillPath)) {
            throw new IllegalArgumentException("Skill not found: " + skillName);
        }
        try {
            return Files.readString(skillPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read skill: " + skillName, e);
        }
    }

    public String buildPromptSummary() {
        List<SkillInfo> skills = listSkills();
        if (skills.isEmpty()) {
            return "当前没有可用 Skills。";
        }

        StringBuilder builder = new StringBuilder("可用 Skills：\n");
        for (SkillInfo skill : skills) {
            builder.append("- ")
                    .append(skill.getName())
                    .append(": ")
                    .append(skill.getDescription())
                    .append("\n");
        }
        return builder.toString();
    }

    private java.util.Optional<SkillInfo> readSkillInfo(Path skillDirectory) {
        Path skillFile = skillDirectory.resolve("SKILL.md");
        if (!Files.isRegularFile(skillFile)) {
            return java.util.Optional.empty();
        }

        try {
            String content = Files.readString(skillFile, StandardCharsets.UTF_8);
            String frontMatter = "";
            Matcher frontMatterMatcher = FRONT_MATTER_PATTERN.matcher(content);
            if (frontMatterMatcher.find()) {
                frontMatter = frontMatterMatcher.group(1);
            }

            String fallbackName = skillDirectory.getFileName().toString();
            SkillInfo info = new SkillInfo();
            info.setName(extract(frontMatter, NAME_PATTERN, fallbackName));
            info.setDescription(extract(frontMatter, DESCRIPTION_PATTERN, ""));
            info.setPath(rootPath().relativize(skillFile).toString().replace('\\', '/'));
            return java.util.Optional.of(info);
        } catch (IOException e) {
            logger.warn("Failed to read skill file: {}", skillFile, e);
            return java.util.Optional.empty();
        }
    }

    private String extract(String text, Pattern pattern, String fallback) {
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return fallback;
    }

    private Path resolveSkillPath(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            throw new IllegalArgumentException("skillName cannot be blank");
        }
        Path root = rootPath();
        Path resolved = root.resolve(skillName).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Invalid skill name: " + skillName);
        }
        return resolved;
    }

    private Path rootPath() {
        return Paths.get(skillsRoot).toAbsolutePath().normalize();
    }

    @Getter
    @Setter
    public static class SkillInfo {
        private String name;
        private String description;
        private String path;
    }
}
