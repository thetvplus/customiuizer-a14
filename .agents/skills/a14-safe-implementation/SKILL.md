---
name: a14-safe-implementation
description: Implement and validate user-requested code, Hook, UI, build or ROM compatibility changes in thetvplus/customiuizer-a14. Use evidence to balance stability, experience, resource costs and maintainability. Not for review-only or release tasks.
---

# A14 Safe Implementation

Implement the requested outcome under the priorities in root `AGENTS.md`. Choose technology by demonstrated benefit, cost and recoverability.

## Authority

- Read root `AGENTS.md` first. It governs architecture, toolchains, compatibility and verification.
- Follow the current authorized task. Do not expand its scope without necessity.
- Use `AGENTS.md` as the common policy entrypoint. Reuse existing mechanisms where suitable; evidence-backed local refactoring, abstractions and mature dependencies are valid when they improve the authorized outcome.

## Workflow

1. Inspect `git status`, record the exact HEAD SHA and identify affected code and contracts.
2. For implementation, reuse an authorized isolated task branch or create one from an exact SHA. Preserve unrelated worktree changes.
3. Trace the actual behavior and failure path before editing. Use relevant source, tests, logs and ROM evidence.
4. Choose a coherent rollback unit. State the expected benefit, resource costs, dependency need and validation method; update affected tests and contracts. Do not equate the fewest lines or lowest memory use with the best solution.
5. Review the final diff for correctness, compatibility, lifecycle, performance and unintended changes.

## Engineering Boundaries

- Preserve existing Hook behavior, timing, call chains and failure semantics.
- Keep disabled features inactive and lifecycle ownership explicit.
- Avoid unnecessary high-frequency allocations; keep blocking I/O, synchronous Binder, DexKit and repeated reflection off hot paths. Static matches are regression clues, not runtime measurements.
- Bounded caches and controlled asynchronous work may trade memory for a verifiable benefit. Identify owner, capacity, invalidation, concurrency and release paths; prefer the existing coroutine mechanisms over new pools.
- Apply the architecture and visual-contract rules in `AGENTS.md`. Changes to mature infrastructure need evidence and focused contract regression coverage, not a blanket ban or a full rewrite.
- Measure according to the claimed benefit and host process: SystemUI, Launcher, system_server and settings have different costs. Keep static, Host JVM, ART, APK and device evidence separate; use `docs/OPTIMIZATION_GATES.md` for budgets and measurement.
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
