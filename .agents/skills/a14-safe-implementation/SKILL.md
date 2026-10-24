---
name: a14-safe-implementation
description: Implement and validate code, Hook, UI, build or ROM compatibility changes in thetvplus/customiuizer-a14. Not for review-only or release tasks.
---

# A14 Safe Implementation

Use root [AGENTS.md](../../../AGENTS.md) for shared policy, task ownership, toolchain and verification. Read linked specialist documents only for the affected contract.

1. Record the exact base/head and inspect local changes, the existing task branch and relevant GitHub ownership before writing.
2. Trace the real caller, failure path and state owner. For ROM behavior, resolve the declaration and ClassLoader from identified samples; use the existing [DexSQL workflow](../../../docs/rom-intelligence/DEXSQL.md).
3. Choose a coherent rollback unit. Explain the concrete benefit, costs and affected contracts; for caches or async work identify capacity, invalidation, concurrency, stale-result rejection and release. Reuse established mechanisms where suitable.
4. Implement the authorized outcome and meaningful regression coverage. Keep functional changes separate from language migration or broad file moves; update affected imports, reflection / XML / R8 references, catalog and generated outputs together.
5. Review the complete diff and run the root verification gates appropriate to its scope. Use `fast` without `--changed` for committed changes; reuse results only for the same source and scope. Keep APK, ROM and device evidence separate.

Report changes, actual verification and remaining risks / device checks. Update the existing refinement page only when its current state or evidence changes; do not add another task ledger or process report.
