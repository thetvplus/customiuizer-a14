# C5 — VolumeDialogAutohideDelay Architecture C Phase Completion

**Repository:** `tomthenpc/customiuizer-a14`
**Branch:** `devin/a14-architecture-c-r14.20.0`
**C5 target-selection freeze:** `3c5cb8cca3cd08799097e534ffef2366a6504b59`
**C5-A0 preflight freeze:** `222af8f2cf2012a76e82427279db5954a7fbaf7c`
**C5 production freeze:** `c4b15a32197f78990f14abfa15fcd76a6402a4c5`
**C5 B1 final test corrective:** `0d7bac289d809eb7f2975e631d5de7ee2ffbc617`
**C5 B1 completion freeze:** `407d9844842f12271c989d8f7dd5cbe42870a920`
**C5 consolidation freeze:** `0fafdcf09f93cd1eb21fb047d17cb9452ea621d7`
**C5 final completion SHA:** *(established by independent audit)*

**Scope:** `SystemUIControlCenterHooks.VolumeDialogAutohideDelayHook` → `com.android.systemui.miui.volume.MiuiVolumeDialogImpl.computeTimeoutH` → volume-dialog auto-hide delay

**Status:** C5 COMPLETE. C1/C2/C3/C4/C5 are frozen. No C6 target is selected.

---

## 1. START GATE

| Check | Result | Evidence |
|---|---|---|
| Current branch | `devin/a14-architecture-c-r14.20.0` | `git branch --show-current` |
| Local HEAD | `0fafdcf09f93cd1eb21fb047d17cb9452ea621d7` | `git rev-parse HEAD` |
| Remote HEAD | `0fafdcf09f93cd1eb21fb047d17cb9452ea621d7` | `git rev-parse origin/devin/a14-architecture-c-r14.20.0` |
| Worktree | clean | `git status --short` empty |
| C5 production changed since freeze | `false` | `git diff --name-only c4b15a32197f78990f14abfa15fcd76a6402a4c5..HEAD -- app/src/main` empty |
| Tests changed since consolidation freeze | `false` | `git diff --name-only 0fafdcf09f93cd1eb21fb047d17cb9452ea621d7..HEAD -- app/src/test` empty |

START PASS. C5 final completion is authorized.

---

## 2. FREEZE CHAIN

```text
C5 target-selection freeze  → 3c5cb8cca3cd08799097e534ffef2366a6504b59
C5-A0 preflight freeze      → 222af8f2cf2012a76e82427279db5954a7fbaf7c
C5 production freeze        → c4b15a32197f78990f14abfa15fcd76a6402a4c5
C5 B1 final test corrective → 0d7bac289d809eb7f2975e631d5de7ee2ffbc617
C5 B1 completion freeze     → 407d9844842f12271c989d8f7dd5cbe42870a920
C5 consolidation freeze     → 0fafdcf09f93cd1eb21fb047d17cb9452ea621d7
C5 final completion SHA     → *(established by independent audit)*
```

C5 production is frozen at `c4b15a32197f78990f14abfa15fcd76a6402a4c5` and will not be reopened unless a concrete correctness, lifecycle/ownership, concurrency/publication, or real-device/runtime regression is demonstrated.

---

## 3. PRODUCTION NO-DIFF PROOF

Command:

```text
git diff --name-only c4b15a32197f78990f14abfa15fcd76a6402a4c5..HEAD -- app/src/main
```

Result: `empty`

Evidence class: `LOCAL_EXECUTION_EVIDENCE_ONLY`.

---

## 4. IMPLEMENTATION CHAIN (FROZEN)

```text
Cold Resolve
    → VolumeDialogAutohideDelayResolver.resolve(classLoader)
        → VolumeDialogAutohideDelayAbi?

Frozen ABI
    → resolutionRootClass
    → mHovering Field (primitive boolean)
    → mExpanded Field (primitive boolean)

Typed Snapshot
    → VolumeDialogAutohideDelayRuntimeState()
        → AtomicReference<VolumeDialogAutohideDelaySnapshot?> (initial null)
        → private val refreshLock = Any()
        → PreferenceObserver
        → initial refresh outside computeTimeoutH

Immutable Effect
    → VolumeDialogAutohideDelayEffect(abi, snapshotRef)

Thin Hook
    → VolumeDialogAutohideDelayHook.install(classLoader)
        → ModuleHelper.findAndHookMethod(
               "com.android.systemui.miui.volume.MiuiVolumeDialogImpl",
               classLoader,
               "computeTimeoutH",
               hook
           )

Mode Select
    → val a = abi
    → val snapshot = snapshotRef.get()   // exactly one per callback
    → a != null
    → thisObject != null
    → thisObject.javaClass === a.resolutionRootClass
    → snapshot != null

Hot Execute
    → processFast(thisObject, a, snapshot, param)
    → mHovering = a.mHoveringField.getBoolean(thisObject)
    → if mHovering: returnAndSkip(16000)
    → mSafetyWarning = LEGACY_SAFETY_ALIAS_READ(thisObject)
    → if mSafetyWarning:
        opt = snapshot.expanded
        returnAndSkip(opt > 0 ? opt : 5000)
    → mExpanded = a.mExpandedField.getBoolean(thisObject)
    → opt = if (mExpanded) snapshot.expanded else snapshot.collapsed
    → if opt > 0: returnAndSkip(opt)
    → otherwise fall through to original computeTimeoutH

Complete Legacy
    → complete original callback oracle using XposedHelpers and MainModule.mPrefs
```

---

## 5. RESOLVER (FROZEN)

- Zero-explicit-parameter `computeTimeoutH` surface.
- No return-type filtering.
- Exact resolution root: `com.android.systemui.miui.volume.MiuiVolumeDialogImpl`.
- `mHovering` and `mExpanded` are primitive `boolean` `Field`s.
- Inherited primitive-boolean fields are allowed.
- Wrapper `java.lang.Boolean` fields reject FAST ABI.
- Ordinary miss returns `null` ABI.
- Fatal errors (`OutOfMemoryError`, `ThreadDeath`, `VirtualMachineError`) propagate.

---

## 6. ABI (FROZEN)

- Immutable.
- No runtime instance retention.
- No `ClassLoader` retention.
- Safety alias fields (`mIsSafetyShowing`, `mSafetyWarning`) are intentionally **not** in the ABI.

---

## 7. RUNTIME STATE (FROZEN)

- Process-scoped singleton.
- `installLock` serializes install.
- One retained candidate.
- Observer registration idempotent.
- Initial refresh before `installed = true`.
- `installed = true` is the final publication step.
- Fatal initial-refresh retry reuses the same candidate.
- No detached fallback state.
- `refreshLock` serializes all refreshes.
- `refreshSource()` captured inside `refreshLock`.
- Snapshot built from one `MainModule.mPrefs.getAll()` `Map`.
- Failure clears snapshot.

---

## 8. SNAPSHOT AND PUBLICATION (FROZEN)

```kotlin
data class VolumeDialogAutohideDelaySnapshot(
    val expanded: Int,
    val collapsed: Int,
)
```

- `AtomicReference<VolumeDialogAutohideDelaySnapshot?>` publication.
- Initial value: `null`.
- Built from a single captured `PrefMap` generation.
- No two independent `getInt` calls.
- Captured `Snapshot` is immutable and used for the entire callback.

---

## 9. EFFECT (FROZEN)

- `thisObject` is read once and is callback-local.
- `snapshotRef.get()` is called exactly once per callback.
- Exact-root FAST eligibility:
  ```text
  abi != null
  thisObject != null
  thisObject.javaClass === abi.resolutionRootClass
  capturedSnapshot != null
  ```
- Captured immutable `Snapshot` is used for the entire FAST invocation.
- `mHovering` / `mExpanded` use direct frozen `Field.getBoolean`.
- Strategy A safety alias remains legacy `XposedHelpers`.
- No FAST → COMPLETE LEGACY retry after FAST starts.

### 9.1 FAST oracle

- `mHovering` true → `returnAndSkip(16000)`.
- Otherwise legacy safety alias.
- Safety true → `snapshot.expanded > 0 ? snapshot.expanded : 5000`.
- Safety false → `mExpanded` `Field.getBoolean`; select `snapshot.expanded` or `snapshot.collapsed`; `returnAndSkip` only if `opt > 0`; otherwise original `computeTimeoutH` proceeds.

### 9.2 COMPLETE LEGACY oracle

Preserved exactly as the original pre-C5 hook:

```kotlin
val mHovering = XposedHelpers.getBooleanField(thisObject, "mHovering")
if (mHovering) { param.returnAndSkip(16000); return }

val mSafetyWarning = try {
    XposedHelpers.getObjectField(thisObject, "mIsSafetyShowing") as Boolean
} catch (e: Throwable) {
    FatalErrors.rethrowIfFatal(e)
    XposedHelpers.getObjectField(thisObject, "mSafetyWarning") as Boolean
}

if (mSafetyWarning) {
    val opt = MainModule.mPrefs.getInt("system_volumedialogdelay_expanded", 0)
    param.returnAndSkip(if (opt > 0) opt else 5000)
    return
}

val mExpanded = XposedHelpers.getBooleanField(thisObject, "mExpanded")
val opt = MainModule.mPrefs.getInt(
    if (mExpanded) "system_volumedialogdelay_expanded" else "system_volumedialogdelay_collapsed",
    0,
)
if (opt > 0) param.returnAndSkip(opt)
```

No C5 snapshot is used in COMPLETE LEGACY.

---

## 10. HOOK (FROZEN)

- Existing zero-explicit-parameter `ModuleHelper.findAndHookMethod` surface.
- No `hookAllMethods`.
- No return-type assumption.

---

## 11. FAILURE / FATAL MATRIX (FROZEN)

| Boundary | Input | Output |
|---|---|---|
| Resolver ordinary | missing method / field / wrapper `Boolean` | `null` ABI → COMPLETE LEGACY |
| Resolver fatal | `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError` | propagate |
| FAST `IllegalAccessException` | `Field.getBoolean` | `IllegalAccessError`, no LEGACY retry |
| FAST `IllegalArgumentException` | wrong receiver | propagate to `MethodHook` boundary, no LEGACY retry |
| FAST other ordinary `Throwable` | reflection etc. | `MethodHook.beforeHook` logs/swallow; original proceeds if not skipped |
| FAST fatal | `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError` | propagate |
| Safety primary ordinary | `mIsSafetyShowing` missing / cast fail | fallback to `mSafetyWarning` |
| Safety primary fatal | `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError` | no fallback, propagate |
| Refresh ordinary | `refreshSource()` throws | `snapshotRef = null`, log, future callbacks use COMPLETE LEGACY |
| Refresh fatal | `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError` | `snapshotRef = null`, rethrow from C5 refresh boundary |
| Real end-to-end fatal escape | real ROM execution | `NOT_PROVEN` |

---

## 12. OWNERSHIP / LIFECYCLE (FROZEN)

| Asset | Owner | Lifetime |
|---|---|---|
| `VolumeDialogAutohideDelayAbi` | cold install | process lifetime, immutable |
| `VolumeDialogAutohideDelaySnapshot` | `AtomicReference` in `RuntimeState` | replaced on refresh, immutable value |
| `VolumeDialogAutohideDelayRuntimeState` | process singleton | process lifetime |
| `VolumeDialogAutohideDelayEffect` | hook-local instance | hook lifetime |
| `MiuiVolumeDialogImpl` instance | `thisObject` in callback | callback lifetime only |

No retained `View`, `Window`, `Context`, `Activity`, or `MiuiVolumeDialogImpl` instance.
No unbounded registration.
No duplicate C5 `PreferenceObserver` after successful install.

---

## 13. CONCURRENCY (FROZEN)

- `CALLBACK_THREAD` is `NOT_PROVEN`.
- `AtomicReference` makes snapshot publication thread-independent.
- Callback path: no `refreshLock`, no `installLock`, no snapshot rebuild.
- `refreshLock` serializes refresh.
- `installLock` serializes first install.

---

## 14. HOT-PATH COST (FROZEN)

| Metric | Value |
|---|---|
| `ELIGIBLE_FAST_CALLBACK_PREFMAP_READS` | `0` |
| `COMPLETE_LEGACY_CALLBACK_PREFMAP_READS` | `0–1` |
| `ALL_CALLBACKS_PREFMAP_READS_REMOVED` | `false` |
| `FAST_SNAPSHOT_REF_READS` | `1` `AtomicReference.get` per invocation |
| `FAST mHovering/mExpanded` | direct frozen `Field.getBoolean` |
| Safety alias | `XposedHelpers` 1–2 attempts where applicable |

No claims of fixed percentage speedup, real callback frequency, real allocation count, or real HyperOS field type.

---

## 15. EVIDENCE MATRIX

| Item | Classification | Source / note |
|---|---|---|
| `VolumeDialogAutohideDelayResolver.resolve` exact root | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayResolverTest` |
| `VolumeDialogAutohideDelayResolver.resolve` inherited fields | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayResolverTest` |
| `VolumeDialogAutohideDelayResolver` wrapper/primitive enforcement | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayResolverTest` |
| `VolumeDialogAutohideDelayResolver` no return-type filtering | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayResolverTest` |
| `VolumeDialogAutohideDelayResolver` fatal propagation | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayResolverTest` |
| `VolumeDialogAutohideDelayRuntimeState` initial `null` snapshot | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayRuntimeStateTest` |
| `VolumeDialogAutohideDelayRuntimeState` one `getAll` source | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayRuntimeStateTest` |
| `VolumeDialogAutohideDelayRuntimeState` refresh serialization | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayRuntimeStateTest` |
| `VolumeDialogAutohideDelayRuntimeState` refresh failure clears snapshot | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayRuntimeStateTest` |
| `VolumeDialogAutohideDelayRuntimeState` install singleton | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayRuntimeStateTest` |
| `VolumeDialogAutohideDelayRuntimeState` concurrent install publication | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayRuntimeStateTest` |
| `VolumeDialogAutohideDelayRuntimeState` fatal-refresh retry without duplicate observer | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayRuntimeStateTest` |
| `VolumeDialogAutohideDelayRuntimeState` installed published only after completion | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayRuntimeStateTest` |
| `VolumeDialogAutohideDelayRuntimeState` refresh behind lock captures latest generation | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayRuntimeStateTest` |
| `VolumeDialogAutohideDelayEffect` FAST oracle | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayEffectTest` |
| `VolumeDialogAutohideDelayEffect` LEGACY oracle | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayEffectTest` |
| `VolumeDialogAutohideDelayEffect` mode selection | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayEffectTest` |
| `VolumeDialogAutohideDelayEffect` safety alias | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayEffectTest` |
| `VolumeDialogAutohideDelayEffect` one `snapshotRef.get()` per callback | `STRUCTURAL` | `VolumeDialogAutohideDelayEffect.process` source contains exactly one `snapshotRef.get()` and passes the result directly to `processFast` |
| `VolumeDialogAutohideDelayEffect` `processFast` consumes the captured snapshot | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayEffectTest` |
| `VolumeDialogAutohideDelayEffect` `IllegalAccessException` mapping | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayEffectTest` |
| `VolumeDialogAutohideDelayEffect` `IllegalArgumentException` propagation | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayEffectTest` |
| C5 production no-diff since production freeze | `LOCAL_EXECUTION_EVIDENCE_ONLY` | `git diff --name-only c4b15a32197f78990f14abfa15fcd76a6402a4c5..HEAD -- app/src/main` |
| Real `computeTimeoutH` return type | `NOT_PROVEN` | no real ROM/device/binary evidence |
| Real `computeTimeoutH` overload set | `NOT_PROVEN` | only zero parameter types supplied to hook |
| Real `computeTimeoutH` callback thread | `NOT_PROVEN` | source does not prove the invoking thread |
| Real `computeTimeoutH` callback frequency | `NOT_PROVEN` | no real-device timing |
| Real HyperOS field type for `mHovering` | `NOT_PROVEN` | resolver enforces `Boolean.TYPE`; any other type is a miss |
| Real HyperOS field type for `mExpanded` | `NOT_PROVEN` | resolver enforces `Boolean.TYPE`; any other type is a miss |
| Real safety-field prevalence (`mIsSafetyShowing` vs `mSafetyWarning`) | `NOT_PROVEN` | both field names are candidates |
| Real preference observer callback execution | `NOT_RUNTIME_TESTED_CALLBACK` | no real callback timing/thread evidence |
| Real end-to-end fatal escape | `NOT_PROVEN` | no real ROM evidence |
| Start gate / validation commands | `LOCAL_EXECUTION_EVIDENCE_ONLY` | executed locally during final completion |

Each classification cell contains one exact value only.

---

## 16. COMPLETION STATE

- `C5_COMPLETE = true`
- `C5_PRODUCTION_FROZEN = true`
- `C1_COMPLETE = true`
- `C2_COMPLETE = true`
- `C3_COMPLETE = true`
- `C4_COMPLETE = true`
- `C5_COMPLETE = true`
- `C1_C2_C3_C4_C5_REOPEN = NO`
- `C6_SELECTED = false`

C5 may only reopen for:

- concrete correctness regression;
- concrete lifecycle/ownership regression;
- concrete concurrency/publication regression;
- new real-device/runtime evidence that contradicts a frozen assumption.

C5 will not be reopened for style cleanup, code-size cleanup, different abstraction preferences, speculative optimization, or unproven ROM assumptions.

---

## 17. VALIDATION

| Command | Result | Evidence |
|---|---|---|
| `git diff --check` | pass | exit `0` |
| `python tools/check_document_contracts.py` | pass | exit `0` |
| `python tools/verify.py full` | pass | exit `0` |
| `gradlew testDebugUnitTest` | pass | exit `0` |
| `gradlew lintDebug` | pass | exit `0` |

All validation results are `LOCAL_EXECUTION_EVIDENCE_ONLY`.

---

## 18. SUBMISSION FIELDS

| Field | Value |
|---|---|
| C5 target-selection freeze | `3c5cb8cca3cd08799097e534ffef2366a6504b59` |
| C5-A0 preflight freeze | `222af8f2cf2012a76e82427279db5954a7fbaf7c` |
| C5 production freeze | `c4b15a32197f78990f14abfa15fcd76a6402a4c5` |
| C5 B1 final test corrective | `0d7bac289d809eb7f2975e631d5de7ee2ffbc617` |
| C5 B1 completion freeze | `407d9844842f12271c989d8f7dd5cbe42870a920` |
| C5 consolidation freeze | `0fafdcf09f93cd1eb21fb047d17cb9452ea621d7` |
| C5 final completion SHA | *(established by independent audit)* |
| Branch | `devin/a14-architecture-c-r14.20.0` |
| Production changed | `false` |
| Tests changed | `false` |
| Docs changed | `true` |

---

C5_FINAL_COMPLETION_READY_FOR_INDEPENDENT_AUDIT
