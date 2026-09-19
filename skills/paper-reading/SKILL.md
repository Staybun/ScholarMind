---
name: paper-reading
description: Use this skill when ScholarMind needs to read, summarize, or explain uploaded papers with evidence. It structures paper understanding around problem, motivation, method, contribution, experiments, results, limitations, and citations from retrieved chunks.
---

# Paper Reading Skill

## Purpose

Turn retrieved paper evidence into a stable research-reading output. Use this skill for paper Q&A, paper cards, method summaries, contribution summaries, experiment/result extraction, limitation analysis, and evidence-backed explanations.

## Required Evidence

Before producing claims, retrieve evidence with `queryPaperKnowledge`. Each important claim should include at least one source marker:

- Paper title
- Section or inferred section
- Page range when available
- File name when page is unavailable

If evidence is missing, say `当前论文知识库证据不足` and list what needs to be uploaded or retrieved.

## Workflow

1. Identify the user's research question.
2. Search paper evidence with 2-5 focused queries.
3. Merge duplicate chunks from the same section.
4. Extract the paper's problem, motivation, method, experiments, results, and limitations.
5. Answer using concise claims tied to evidence.
6. Separate facts from inference.

## Output Template

Use `templates/paper-reading-card.md` when the user asks for a paper card or structured reading notes.

For normal Q&A, use this compact format:

```markdown
## 结论
[Direct answer.]

## 依据
- [Claim]（来源：[paper title]，[section]，[page/file]）

## 方法/实验要点
- [Method or experiment point with source.]

## 局限性
- [Limitation or uncertainty.]
```

## Guardrails

- Do not invent datasets, metrics, baselines, hyperparameters, or conclusions.
- Do not cite a paper title unless it appears in retrieved evidence.
- When evidence conflicts, mention the conflict and show both sources.
- Prefer concrete paper language over broad generic explanation.
