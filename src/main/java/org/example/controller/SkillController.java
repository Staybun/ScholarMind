package org.example.controller;

import org.example.service.SkillRegistryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Skill inspection APIs.
 */
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    @Autowired
    private SkillRegistryService skillRegistryService;

    @GetMapping
    public ResponseEntity<List<SkillRegistryService.SkillInfo>> listSkills() {
        return ResponseEntity.ok(skillRegistryService.listSkills());
    }

    @GetMapping(value = "/{skillName}", produces = MediaType.TEXT_MARKDOWN_VALUE)
    public ResponseEntity<String> getSkill(@PathVariable String skillName) {
        return ResponseEntity.ok(skillRegistryService.getSkillMarkdown(skillName));
    }
}
