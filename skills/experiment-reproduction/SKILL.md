---
name: experiment-reproduction
description: Use this skill when ScholarMind needs to turn paper methods and experiment sections into a reproducible experiment plan. It separates paper-stated facts from assumptions and creates implementation, data, metrics, and risk checklists.
---

# Experiment Reproduction Skill

## Purpose

Convert paper evidence into an actionable reproduction plan. Use this skill for experiment planning, implementation breakdown, dataset preparation, environment setup, metric selection, ablation planning, and reproduction risk analysis.

## Required Evidence

Use `queryPaperKnowledge` before planning. If source code or local project files matter, use `searchCodeRepository` or `readWorkspaceFile`.

Mark every important item as one of:

- `论文明确给出`
- `从证据推断`
- `需要用户确认`

## Workflow

1. Define reproduction target and expected output.
2. Extract method components from paper evidence.
3. Extract datasets, preprocessing, baselines, metrics, and hyperparameters when available.
4. Map the method into implementation modules.
5. Create a runnable experiment sequence.
6. Add validation checks and ablations.
7. List missing information and risks.

## Output Template

Use `templates/reproduction-plan.md` for structured reproduction plans.

## Guardrails

- Do not invent exact dataset versions, scores, hyperparameters, or hardware requirements.
- If code is not available, say so and propose module-level implementation steps.
- Keep assumptions explicit and easy to replace.
- Prefer small first-run experiments before full-scale reproduction.
