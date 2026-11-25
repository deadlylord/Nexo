---
name: explore-codebase
description: "Trigger: explore, investigar codebase, mapear archivos, buscar dependencias. Read-only codebase exploration returning concise technical summaries."
license: Apache-2.0
metadata:
  author: "deadlylord"
  version: "1.0"
---

## Activation Contract

Use this skill when:
- You need to inspect 4 or more files, trace cross-module call chains, or map dependencies/symbols across the repository.
- You want to gather technical context without cluttering the main orchestrator context.

Do NOT spawn a subagent for 1–3 already-known files (read them directly in the main thread to avoid overhead).

## Hard Rules

- NEVER create, edit, or delete any file, and NEVER run mutating commands.
- Read only the files and line ranges strictly needed to answer the question.
- Prefer invoking Antigravity's built-in `research` subagent with `Model: "flash"` (or `"flash_lite"` for simple symbol/path lookups) to isolate raw search output from the main thread.
- Keep the final summary to at most 10–15 concise lines so the orchestrator context stays lean.

## Decision Gates

| Scope | Action |
|-------|--------|
| 1–3 known files or single symbol lookup | Read directly in main thread (skip subagent) |
| Simple cross-repo path/symbol grep | Delegate to `research` subagent (`Model: "flash_lite"`) |
| Multi-file architecture, state flow, or bug root-cause trace | Delegate to `research` subagent (`Model: "flash"`) |

## Execution Steps

1. Formulate a targeted research prompt specifying the exact question, suspected modules (use the map in `GEMINI.md`), and required output shape.
2. Search with narrow patterns (`rg` / targeted line slices) before reading full files.
3. Extract exact file paths, function/class/interface names, line ranges, and key state transitions or edge cases.
4. Compress findings into a structured technical brief without pasting large code blocks.

## Output Contract

Return:
- **Exact paths & symbols**: `path/to/File.kt` + symbol names and line ranges (`#L10-L45`).
- **Technical summary** (max 10–15 lines): how the flow works, dependencies, and relevant constraints/edge cases.
- **Open gaps** (if any): only if a critical file or runtime detail could not be verified.
