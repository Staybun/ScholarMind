package org.example.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.service.SkillRegistryService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * ReAct tools for discovering and reading ScholarMind project skills.
 */
@Component
public class SkillTools {

    @Autowired
    private SkillRegistryService skillRegistryService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Tool(description = "List reusable ScholarMind Skills. Use this when deciding whether a high-frequency task should follow a skill workflow.")
    public String listScholarMindSkills() {
        try {
            return objectMapper.writeValueAsString(skillRegistryService.listSkills());
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }

    @Tool(description = "Read one ScholarMind Skill instruction by name, for example paper-reading or experiment-reproduction.")
    public String readScholarMindSkill(
            @ToolParam(description = "Skill name, for example paper-reading or experiment-reproduction") String skillName) {
        try {
            return skillRegistryService.getSkillMarkdown(skillName);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"" + e.getMessage().replace("\"", "'") + "\"}";
        }
    }
}
