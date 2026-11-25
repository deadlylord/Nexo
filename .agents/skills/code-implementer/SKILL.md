---
name: code-implementer
description: "Trigger: implementer, implementar plan, ejecutar cambios multi-archivo. Execute an approved technical plan cleanly without placeholders."
license: Apache-2.0
metadata:
  author: "deadlylord"
  version: "1.0"
---

## Activation Contract

Use this skill when:
- A technical plan has been approved by the user (or explicitly delegated for a well-scoped multi-file change).
- Implementation spans 3+ substantial files or benefits from isolated/parallel execution via a write-enabled subagent.

For small, focused edits (1–2 known files), implement directly in the main thread to save tokens and latency.

## Hard Rules

- Execute the approved technical plan faithfully; do NOT redesign architecture or expand scope without asking the orchestrator.
- Follow existing project conventions (Kotlin, Jetpack Compose, `Prefs.kt` keys, Android manifest/permissions, minimal diff footprint).
- Apply **Ponytail** discipline: reuse existing helpers/platform APIs, write the smallest complete diff with zero unrequested abstractions or dependencies, and never cut validation, null/API guards, error handling, or accessibility.
- Write complete, production-ready code: NEVER leave `// TODO`, stub implementations, or truncated blocks.
- Preserve unrelated comments, docstrings, and formatting.
- When delegated to a subagent, define/invoke an `implementer` subagent with `enable_write_tools: true` using the model tier authorized in `GEMINI.md`.

## Decision Gates

| Task Complexity | Execution Mode & Model |
|-----------------|------------------------|
| Small fix (1–2 known files) | Main thread directly |
| Well-specified UI, prefs, wiring, or mechanical refactor (3+ files) | `implementer` subagent (`Model: "flash"`) |
| Delicate Android internals (`WindowManager`, `AccessibilityService`, Canvas, touch gestures, concurrency) | `implementer` subagent (`Model: "pro"` / `"inherit"`) or main thread |

## Execution Steps

1. Re-read the approved plan and verify target line ranges before editing.
2. Apply focused edits file by file using exact replacements; avoid rewriting entire files unnecessarily.
3. Verify imports, nullability, Android API-level guards (`Build.VERSION.SDK_INT`), and lifecycle cleanup (receivers, runnables, listeners).
4. Run build/lint verification when assigned, or hand back cleanly to the orchestrator for `./gradlew assembleDebug`.

## Output Contract

Return only:
- **Files created/modified**: exact paths with a 1-line summary of each change.
- **Dependencies / permissions / manifest changes**: list any added, or state `Ninguna`.
- **Verification / risks**: build status (if run) and any edge cases or assumptions to double-check.
