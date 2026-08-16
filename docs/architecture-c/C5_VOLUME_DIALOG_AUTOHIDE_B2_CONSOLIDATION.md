# C5-B2 — VolumeDialogAutohideDelay Architecture C Consolidation / Freeze Hardening

**Repository:** `tomthenpc/customiuizer-a14`
**Branch:** `devin/a14-architecture-c-r14.20.0`
**C5 target-selection freeze SHA:** `3c5cb8cca3cd08799097e534ffef2366a6504b59`
**C5-A0 freeze SHA:** `222af8f2cf2012a76e82427279db5954a7fbaf7c`
**C5 production freeze SHA:** `c4b15a32197f78990f14abfa15fcd76a6402a4c5`
**C5 B1 final test corrective SHA:** `0d7bac289d809eb7f2975e631d5de7ee2ffbc617`
**C5 B1 completion freeze / B2 base SHA:** `407d9844842f12271c989d8f7dd5cbe42870a920`
**Scope:** `SystemUIControlCenterHooks.VolumeDialogAutohideDelayHook` → `com.android.systemui.miui.volume.MiuiVolumeDialogImpl.computeTimeoutH`
**Type:** Consolidation / freeze hardening. No production or test changes.

---

## 1. START GATE

| Check | Result | Evidence |
|---|---|---|
| Current branch | `devin/a14-architecture-c-r14.20.0` | `git branch --show-current` |
| Local HEAD | `407d9844842f12271c989d8f7dd5cbe42870a920` | `git rev-parse HEAD` |
| Remote HEAD | `407d9844842f12271c989d8f7dd5cbe42870a920` | `git rev-parse origin/devin/a14-architecture-c-r14.20.0` |
| Merge-base | `407d9844842f12271c989d8f7dd5cbe42870a920` | `git merge-base HEAD origin/devin/a14-architecture-c-r14.20.0` |
| Worktree | clean | `git status --short` empty |
| C1/C2/C3/C4 production changed | `false` | no modifications in those phases |
| C5 production changed since `c4b15a32...` | `false` | `git diff --name-only c4b15a32197f78990f14abfa15fcd76a6402a4c5..HEAD -- app/src/main` returned empty |

START PASS. B2 is authorized.

---

## 2. FREEZE CHAIN

```text
C5 target-selection freeze      → 3c5cb8cca3cd08799097e534ffef2366a6504b59
C5-A0 preflight freeze          → 222af8f2cf2012a76e82427279db5954a7fbaf7c
B1 original implementation      → 5a9c61c26f6fa1eb69cba47b23d9039a26264021
B1 corrective production/tests  → c4b15a32197f78990f14abfa15fcd76a6402a4c5
B1 final test corrective        → 0d7bac289d809eb7f2975e631d5de7ee2ffbc617
B1 completion freeze / B2 base  → 407d9844842f12271c989d8f7dd5cbe42870a920
```

All C5 production code is frozen at `c4b15a32197f78990f14abfa15fcd76a6402a4c5`.
B2 only adds the consolidation document and a small non-SHA correction to the B1 document.

---

## 3. PRODUCTION NO-DIFF PROOF

The C5 production package has not changed since the B1 corrective production freeze.

Command:

```text
git diff --name-only c4b15a32197f78990f14abfa15fcd76a6402a4c5..HEAD -- app/src/main
```

Result:

```text
<empty>
```

Evidence class: `LOCAL_EXECUTION_EVIDENCE_ONLY`.

---

## 4. ARCHITECTURE C CHAIN

```text
Cold Resolve
    → VolumeDialogAutohideDelayResolver.resolve(classLoader)
        → VolumeDialogAutohideDelayAbi?

Frozen ABI
    → resolutionRootClass
    → mHovering Field (primitive boolean)
    → mExpanded Field (primitive boolean)

Typed Config / Snapshot
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

Mode Select (before any FAST field access)
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

## 5. LEGACY ORACLE (FREEZE)

The `VolumeDialogAutohideDelayEffect.processLegacy` oracle is behavior-equivalent to the original pre-C5 hook:

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

No C5 snapshot may be read in `COMPLETE_LEGACY`.
`COMPLETE_LEGACY_CALLBACK_PREFMAP_READS = 0–1`.

---

## 6. FAST ORACLE (FREEZE)

Eligibility:

```text
abi != null
thisObject != null
thisObject.javaClass === abi.resolutionRootClass
capturedSnapshot != null
```

Execution:

1. `mHoveringField.getBoolean(thisObject)`
2. If `mHovering` is `true`:
   - `returnAndSkip(16000)`
   - no safety read
   - no `mExpanded` read
3. If `mHovering` is `false`:
   - exact legacy safety alias (Strategy A)
4. If safety is `true`:
   - `opt = capturedSnapshot.expanded`
   - `returnAndSkip(opt > 0 ? opt : 5000)`
   - no `mExpanded` read
5. If safety is `false`:
   - `mExpandedField.getBoolean(thisObject)`
   - `opt = if (mExpanded) capturedSnapshot.expanded else capturedSnapshot.collapsed`
   - if `opt > 0`: `returnAndSkip(opt)`
   - else: original `computeTimeoutH` proceeds

`ELIGIBLE_FAST_CALLBACK_PREFMAP_READS = 0`.
`FAST_SNAPSHOT_REF_READS = 1` per callback.
`FAST mHovering/mExpanded` use direct frozen `Field.getBoolean`.
Safety alias still uses `XposedHelpers` with 1–2 attempts where applicable.

---

## 7. SINGLETON / PUBLICATION INVARIANTS (FREEZE)

`VolumeDialogAutohideDelayRuntimeState` is a process-scoped singleton:

- `installLock` serializes all initial publications.
- `instance` is established before `installed = true`.
- Observer registration is idempotent per candidate.
- Initial refresh is performed before `installed = true`.
- `installed = true` is the **last** successful publication step.
- `installed` is `@Volatile`.
- No detached fallback `RuntimeDialogAutohideDelayRuntimeState()` is ever returned.
- If initial refresh throws a fatal error, `installed` remains `false`, the same candidate is retained, and a later `install()` can retry without duplicating the observer.
- An install caller that sees `installed == true` receives the unique initialized instance via `checkNotNull(instance)`.

---

## 8. REFRESH SERIALIZATION (FREEZE)

- All refreshes serialize on `refreshLock`.
- `refreshSource()` is captured **after** acquiring `refreshLock`.
- Snapshot is built from a single captured `MainModule.mPrefs.getAll()` `Map`.
- Failed refresh clears `snapshotRef` to `null`.
- The callback path never acquires `refreshLock` and never rebuilds the snapshot.
- A refresh waiting behind `refreshLock` reads the latest `PrefMap` generation when it finally acquires the lock.

---

## 9. FAILURE / FATAL MATRIX (FREEZE)

| Boundary | Input | Output |
|---|---|---|
| Resolver ordinary failure | missing method / field / wrapper `Boolean` | `null` ABI → complete legacy |
| Resolver fatal | `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError` | propagate |
| FAST `IllegalAccessException` | `Field.getBoolean` | `IllegalAccessError`, no legacy retry |
| FAST `IllegalArgumentException` | wrong receiver | propagate to `MethodHook.beforeHook`, no legacy retry |
| FAST other ordinary `Throwable` | reflection etc. | `MethodHook.beforeHook` logs; original `computeTimeoutH` proceeds if no `returnAndSkip` |
| FAST fatal | `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError` | propagate |
| Safety alias first ordinary failure | `mIsSafetyShowing` missing / cast fail | fallback to `mSafetyWarning` |
| Safety alias first fatal | `OutOfMemoryError` etc. | no fallback, propagate |
| Refresh ordinary failure | `refreshSource()` throws | `snapshotRef = null`, log, future callbacks complete legacy |
| Refresh fatal | `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError` | `snapshotRef = null`, rethrow from C5 refresh boundary |
| `REAL_END_TO_END_FATAL_ESCAPE` | real ROM execution | `NOT_PROVEN` |

---

## 10. OWNERSHIP / LIFECYCLE (FREEZE)

| Asset | Owner | Lifetime |
|---|---|---|
| `VolumeDialogAutohideDelayAbi` | cold install | process lifetime, immutable after creation |
| `VolumeDialogAutohideDelaySnapshot` | `AtomicReference` in `RuntimeState` | replaced on each refresh, immutable value |
| `VolumeDialogAutohideDelayRuntimeState` | process singleton | process lifetime |
| `VolumeDialogAutohideDelayEffect` | hook-local instance | as long as the hook is installed |
| `MiuiVolumeDialogImpl` instance | callback `thisObject` | callback-local only |

No retained `View`, `Window`, `Context`, `Activity`, `MiuiVolumeDialogImpl` instance, or per-dialog map/cache.
No unbounded registration.
No duplicate C5 `PreferenceObserver` after successful install.

---

## 11. HOT-PATH COST (FREEZE)

| Metric | Value |
|---|---|
| `ELIGIBLE_FAST_CALLBACK_PREFMAP_READS` | `0` |
| `COMPLETE_LEGACY_CALLBACK_PREFMAP_READS` | `0–1` |
| `ALL_CALLBACKS_PREFMAP_READS_REMOVED` | `false` |
| `FAST_SNAPSHOT_REF_READS` | `1` `AtomicReference.get` per callback |
| `FAST mHovering/mExpanded` | direct `Field.getBoolean` from frozen ABI |
| Safety alias | `XposedHelpers` 1–2 attempts where applicable |

No claims of fixed speedup or allocation savings are made.

---

## 12. TEST / EVIDENCE MATRIX

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
| Deterministic concurrent install fixture | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayRuntimeStateTest` |
| Deterministic refresh-lock fixture | `RUNTIME_TESTED_COMPONENT` | `VolumeDialogAutohideDelayRuntimeStateTest` |
| C5 production no-diff since `c4b15a32...` | `LOCAL_EXECUTION_EVIDENCE_ONLY` | `git diff --name-only c4b15a32197f78990f14abfa15fcd76a6402a4c5..HEAD -- app/src/main` |
| Real `computeTimeoutH` return type | `NOT_PROVEN` | no real ROM/device/binary evidence |
| Real `computeTimeoutH` overload set | `NOT_PROVEN` | only zero parameter types supplied to hook |
| Real `computeTimeoutH` callback thread | `NOT_PROVEN` | source does not prove the invoking thread |
| Real `computeTimeoutH` callback frequency | `NOT_PROVEN` | no real-device timing |
| Real HyperOS field type for `mHovering` | `NOT_PROVEN` | resolver enforces `Boolean.TYPE`; any other type is a miss |
| Real HyperOS field type for `mExpanded` | `NOT_PROVEN` | resolver enforces `Boolean.TYPE`; any other type is a miss |
| Real safety-field prevalence (`mIsSafetyShowing` vs `mSafetyWarning`) | `NOT_PROVEN` | both field names are candidates |
| Real preference observer callback execution | `NOT_RUNTIME_TESTED_CALLBACK` | no real callback timing/thread evidence |
| Real `computeTimeoutH` end-to-end fatal escape | `NOT_PROVEN` | no real ROM evidence |
| Start gate / validation commands | `LOCAL_EXECUTION_EVIDENCE_ONLY` | executed locally during B2 consolidation |
| Focused unit test results | `LOCAL_EXECUTION_EVIDENCE_ONLY` | executed with `python tools/verify.py` and focused `gradlew` commands locally |

Each classification cell contains one exact value only.
No fixture evidence is inflated into real ROM evidence.

---

## 13. REMAINING `NOT_PROVEN` / `NOT_RUNTIME_TESTED_CALLBACK`

- Real `computeTimeoutH` return type
- Real `computeTimeoutH` overload set
- Real `computeTimeoutH` callback thread
- Real `computeTimeoutH` callback frequency
- Real HyperOS primitive/wrapper `Boolean` field types for `mHovering` and `mExpanded`
- Real safety-field prevalence (`mIsSafetyShowing` vs `mSafetyWarning`)
- Real preference observer callback timing and thread
- Real end-to-end fatal error escape behavior

These can only be established by real ROM / device / binary evidence.

---

## 14. VALIDATION

| Command | Result | Evidence |
|---|---|---|
| `git diff --check` | pass | exit `0` |
| `python tools/check_document_contracts.py` | pass | exit `0` |
| `python tools/verify.py full` | pass | exit `0` |
| `gradlew testDebugUnitTest` | pass | exit `0` |
| `gradlew lintDebug` | pass | exit `0` |

All validation results are `LOCAL_EXECUTION_EVIDENCE_ONLY`.

---

## 15. SUBMISSION FIELDS

| Field | Value |
|---|---|
| C5 target-selection freeze SHA | `3c5cb8cca3cd08799097e534ffef2366a6504b59` |
| C5-A0 freeze SHA | `222af8f2cf2012a76e82427279db5954a7fbaf7c` |
| C5 production freeze SHA | `c4b15a32197f78990f14abfa15fcd76a6402a4c5` |
| C5 B1 final test corrective SHA | `0d7bac289d809eb7f2975e631d5de7ee2ffbc617` |
| C5 B1 completion freeze / B2 base SHA | `407d9844842f12271c989d8f7dd5cbe42870a920` |
| B2 consolidation commit SHA | *(established by independent audit)* |
| Branch | `devin/a14-architecture-c-r14.20.0` |
| Production changed | `false` |
| Tests changed | `false` |
| Docs changed | `true` |

---

C5_B2_CONSOLIDATION_READY_FOR_INDEPENDENT_AUDIT
