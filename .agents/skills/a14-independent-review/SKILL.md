---
name: independent-review
description: Independently review a specified Git diff for actionable defects, regressions, security, performance and compatibility risks. Read-only; do not implement fixes or approve the author's work.
---

# 独立审查

Read applicable repository instructions; in this project, shared contracts are in [AGENTS.md](../../../AGENTS.md). Review the specified exact base/head, with surrounding callers and tests only as needed.

## 审查边界

- Remain read-only: no source, test, configuration, Git or worktree mutations; no commits, merges or pushes.
- Do not rely on the author's conclusions as proof or approve work on the author's behalf. Claim executed checks only when their results are verified.
- Report defects introduced or materially worsened by the diff, not unrelated audits, stylistic preferences or speculative optimization.

## 审查方法

Trace changed behavior through callers, state ownership, lifecycle and realistic failure paths. Check correctness, security / data integrity, crashes, races, leaks, platform compatibility and critical missing regression coverage.

For architecture, scheduling, caches or drawing changes, examine benefit, resource bounds, invalidation, cancellation, stale results and rollback scope. More classes, memory, dependencies or APK bytes alone do not establish a defect. Distinguish absent measurements from a confirmed regression; Host JVM results cannot prove ART, PSS or frame-time gains. Safety, compatibility and maintenance can justify a change without performance gains.

## 审查结论

List confirmed actionable findings in severity order (P0 critical, P1 high, P2 medium, P3 low). Give a concise title, precise file / line, trigger, impact, supporting evidence and minimal correction. Avoid duplicates and invented reproduction or test results.

If none are confirmed, state “No confirmed findings in the reviewed scope.” Then identify material unverified risks or testing gaps. Final approval remains outside this review.
