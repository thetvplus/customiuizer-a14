---
name: a14-safe-implementation
description: Implement and validate user-requested code, Hook, UI, build or ROM compatibility changes in thetvplus/customiuizer-a14. Prioritize correctness, stability, compatibility and minimal overhead. Not for review-only or release tasks.
argument-hint: "<task>"
---

# A14 Safe Implementation

Implement the requested change with minimal complexity, regression risk and resource overhead.

## Authority

- Read root `AGENTS.md` first. It governs architecture, toolchains, compatibility and verification.
- Follow the current authorized task. Do not expand its scope without necessity.
- Reuse existing architecture and conventions. Avoid duplicate rules, speculative refactoring and unnecessary abstractions.

## Workflow

1. Inspect `git status`, record the exact HEAD SHA and identify affected code and contracts.
2. For implementation, reuse an authorized isolated task branch or create one from an exact SHA. Preserve unrelated worktree changes.
3. Trace the actual behavior and failure path before editing. Use relevant source, tests, logs and ROM evidence.
4. Make the smallest coherent change. Update affected tests and contracts when necessary.
5. Review the final diff for correctness, compatibility, lifecycle, performance and unintended changes.

## Engineering Boundaries

- Preserve existing Hook behavior, timing, call chains and failure semantics.
- Keep disabled features inactive and lifecycle ownership explicit.
- Avoid allocations, blocking operations, repeated reflection and unnecessary observers in hot paths.
- Prefer bounded state, minimal dependencies and straightforward control flow.
- Do not redesign PrefMap, ResourceHooks, FeatureRegistry, ProcessRouter, PreferenceBootstrap or Dynamic Island visuals unless explicitly authorized.
- Use DexSQL for relevant ROM / DEX / Hook compatibility investigations, not routine edits.
- Do not claim device compatibility or performance gains without appropriate evidence.

## Git Safety

- `main` is the stable production branch.
- Never force-push, rewrite public history, destructively reset or discard unrelated changes.
- Push, merge, tag and release require explicit task authorization.
- Do not introduce unrelated formatting, dependencies or structural changes.

## Verification

- During tracked production edits, before committing:
  `python tools/verify.py fast --changed`
- For already committed changes or uncertain change detection, use `fast` without `--changed`.
- Run focused tests where appropriate.
- Before completing production changes, run `python tools/verify.py full` once on the final source state, or reuse valid verification for the exact same state and scope.
- Run Python tool tests when `tools/` changes.
- Check the final diff with `git diff --check`.
- Documentation-only changes require relevant static checks, not the entire Gradle suite.
- APK, R8, ROM and device validation are separate when the task requires them.
- Report failures and limitations accurately. Never bypass tests or weaken assertions to manufacture a pass.

## Completion

Report only:
- Changes made and affected scope.
- Verification performed and actual results.
- Remaining risks, blockers or required device checks.

Keep execution focused and output concise.
