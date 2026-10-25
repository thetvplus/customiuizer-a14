---
name: a14-safe-implementation
description: Implement A14 code, tooling or engineering-policy changes with checks matched to the affected contracts. Excludes read-only review and formal release execution.
---

# 安全实施

Use [AGENTS](../../../AGENTS.md) for shared boundaries and the relevant row of [TESTING](../../../docs/TESTING.md#执行分级) for Quick / Standard / Deep routing. Do not reload unchanged instructions or all linked documents.

1. Reuse the current task's base/head, worktree and ownership snapshot; refresh only when related state changes or before shared / remote writes. Trace the changed input, caller, failure path and owner, then start implementation.
2. Choose a coherent rollback unit. For runtime changes use the affected [architecture contract](../../../ARCHITECTURE.md#运行时不变量); for ROM uncertainty load the [DexSQL workflow](../../../docs/rom-intelligence/DEXSQL.md). Documentation and leaf tools do not require ROM discovery.
3. Implement the authorized result and meaningful regression coverage. Update affected imports, reflection / XML / R8 references and generated outputs together; avoid unrelated migration or cleanup.
4. Review the complete task diff, select necessary checks by impact and reuse trustworthy results under [evidence reuse](../../../docs/TESTING.md#证据复用). Do not append Full, all Python tests or APK builds solely because the task is ending. New failures or risks require diagnosis and affected revalidation.

Report the chosen level, actual verification and remaining risks / device checks. Update the existing refinement page only when current decisions or evidence change; do not add a task ledger.
