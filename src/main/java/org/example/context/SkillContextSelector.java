package org.example.context;

import org.example.service.SkillRegistryService;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

@Component
public class SkillContextSelector {
    private final SkillRegistryService registry;
    public SkillContextSelector(SkillRegistryService registry) { this.registry = registry; }

    public List<SelectedSkill> select(String input) {
        String query = input.toLowerCase(Locale.ROOT);
        List<SelectedSkill> skills = new ArrayList<>();
        if (query.matches("(?s).*(paper-reading|精读|论文卡片|结构化总结|论文总结|reading card).*")) {
            add(skills, "paper-reading");
        }
        if (query.matches("(?s).*(experiment-reproduction|复现|实验计划|消融|reproduc|ablation).*")) {
            add(skills, "experiment-reproduction");
        }
        return skills;
    }

    private void add(List<SelectedSkill> skills, String name) {
        skills.add(new SelectedSkill(name, registry.getSkillMarkdown(name)));
    }
    public record SelectedSkill(String name, String markdown) { }
}
