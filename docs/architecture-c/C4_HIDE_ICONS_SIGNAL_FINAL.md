# C4 — HideIconsSignal Architecture C Final Completion / Freeze Artifact

**Repository:** `tomthenpc/customiuizer-a14`  
**Branch:** `devin/a14-architecture-c-r14.20.0`  
**Evidence classification:** `LOCAL_EXECUTION_EVIDENCE_ONLY`

**Frozen SHAs:**

- **C4 target-selection freeze:** `e559df4d3381a8627641072eeed8f4dec1036aee`
- **C4 A0 preflight freeze:** `2dd28afe7bb73a7b9cb6046239a55530a15d1776`
- **C4 B1 production freeze:** `a9fa99557b93dceccd79a6c58b9fe8d048a7f371`
- **C4 B2 consolidation freeze:** `dc0cc79209ba8e71087346d392f0815cd41e6968`

This document is a final freeze artifact. It records the already-audited C4 result. It is not a new technical design. No production changes occurred after `a9fa995...`.

---

## 1. Final architecture chain

```text
Cold Resolve
  └─ StatusBarIconVisibilityResolver.resolve(classLoader)
      └─ StatusBarIconVisibilityAbi? (null → complete legacy)

Frozen ABI
  ├─ statusBarMobileViewResolutionRootClass
  ├─ mobileIconStateResolutionRootClass
  ├─ mState Field
  ├─ wifiAvailable Field (primitive boolean only)
  ├─ subId Field (int / Integer)
  ├─ visible Field
  ├─ roaming Field
  ├─ volte Field
  └─ speechHd Field

Existing Typed Config / Publication
  ├─ StatusBarIconVisibilityRuntimeState
  ├─ AtomicReference<StatusBarIconVisibilitySnapshot?>
  └─ currentOrBuildStatusBarIconVisibilitySnapshot()

Immutable Effect
  └─ StatusBarIconVisibilityEffect(abi, snapshotProvider)
      └─ hook-local captured val

Thin Hook
  ├─ HideIconsSignalHook
  ├─ ModuleHelper.hookAllMethods("...StatusBarMobileView", "applyMobileState", stateHook)
  └─ ModuleHelper.hookAllMethods("...StatusBarMobileView", "updateState", stateHook)

Hot Execute
  ├─ getArg(0)
  ├─ getMember().name
  ├─ getThisObject()
  ├─ isFastEligible(thisObject, mobileIconState)
  ├─ select processFast OR processLegacy
  ├─ callback oracle inside selected path
  └─ original SystemUI method proceeds
```

---

## 2. Frozen facts

| Fact | Frozen value |
|---|---|
| `HOOK_SURFACE` | `hookAllMethods` for both declared method-name surfaces (`applyMobileState`, `updateState`) |
| `REAL_METHOD_OVERLOAD_SET` | `NOT_PROVEN` |
| `ZERO_ARG_OVERLOAD_GETARG0_BEHAVIOR` | `NOT_PROVEN` |
| `CALLBACK_ARG0` | callback assumes `arg0` exists and treats it as `mobileIconState` |
| `STATUS_BAR_MOBILE_VIEW_ROOT` | named `com.android.systemui.statusbar.StatusBarMobileView` class, resolved at hook install |
| `MOBILE_ICON_STATE_ROOT` | cold-derived conservatively from declared hook/member ABI or `mState` fallback |
| `REAL_HYPEROS_MOBILE_ICON_STATE_ROOT` | `NOT_PROVEN` |
| `FAST_ELIGIBILITY` | exact runtime-class equality against **both** frozen resolution roots |
| `RUNTIME_CLASS_MISMATCH` | complete pre-fast LEGACY fallback |
| `INHERITED_FIELD_ON_EXACT_ROOT` | FAST allowed |
| `SUBCLASS_SHADOWING` | preserved through runtime-class-first LEGACY lookup |
| `WIFI_AVAILABLE_FAST_ABI` | primitive `boolean` only (`Field.type == Boolean.TYPE`) |
| `SUB_ID_FAST_ABI` | `int` / `Integer`; `Field.get` then Kotlin `as Int` |
| `WRITES` | `boolean` / `Boolean` via frozen `Field.set` |
| `EFFECT_OWNERSHIP` | hook-local immutable capture; no process-global mutable `Effect` |
| `CONFIG_PUBLICATION` | existing `StatusBarIconVisibilityRuntimeState` / `AtomicReference` snapshot |
| `SUBSCRIPTION_MANAGER` | unchanged and outside C4 optimization scope |

---

## 3. Callback entry order

1. `param.getArg(0)`
2. `param.getMember().name`
3. `param.getThisObject()`
4. `isFastEligible()`
5. Select `processFast` or `processLegacy`

Inside selected path:

6. If non-`updateState`: read `mState`
7. If `mState != null` (non-`updateState`): early return
8. snapshot
9. `wifiAvailable`
10. `subId`
11. `SubscriptionManager.getActiveDataSubscriptionId()`
12. `SubscriptionManager.getSlotIndex(subId)`
13. `computeSignalIconHiding`
14. `visible` write + early return if `visible == false`
15. `roaming`
16. `volte`
17. `speechHd`

`isFastEligible == false` is **not** an early return. It selects complete `processLegacy`. Early returns inside the oracle (`mState != null`, `visible == false`) remain unchanged.

---

## 4. Resolver expected-miss model

An unresolved class, unresolved safe `MobileIconState` root, missing required field, or incompatible required field type produces a `null` ABI. The hook then runs through the complete legacy callback path.

Resolver ordinary nonfatal `Throwable` (not fatal) is logged and also produces a `null` ABI, falling back to legacy.

Resolver fatal `Throwable` (`OutOfMemoryError`, `ThreadDeath`, `VirtualMachineError`) propagates without conversion to fallback.

---

## 5. FAST execution failure semantics

Once FAST begins (`Field.get` on `mState`, `Field.getBoolean` on `wifiAvailable`, `Field.get` on `subId`, or the `as Int` cast):

- NO legacy retry.
- `IllegalAccessException` → log → `IllegalAccessError` → outer `MethodHook` boundary.
- `IllegalArgumentException` → propagate → no retry.
- `subId` `ClassCastException` → propagate → no retry.
- A later failure after earlier writes stops remaining mutations; earlier successful mutations remain.
- Fatal errors propagate immediately.

---

## 6. Structurally proven hot-path model

**LEGACY steady-state:**

```text
runtime receiver Class
  -> XposedHelpers fieldCache lookup
  -> field-name lookup
  -> cached Field
  -> Field.get / getBoolean / set
```

**LEGACY cache-miss only:**

```text
findFieldRecursiveImpl
  -> getDeclaredField
  -> superclass traversal
  -> setAccessible
  -> cache Field
```

**FAST exact-root:**

```text
short-circuit eligibility
  -> frozen Field.get / getBoolean / set
```

C4 removes on FAST-eligible callbacks only the recurring `XposedHelpers` helper + runtime-class/name field-cache lookup. It does **not** eliminate `Field` access, reflection, allocations, `SubscriptionManager` calls, or per-callback recursive field resolution.

No claims are made for zero allocation, measured speedup, known callback frequency, `SubscriptionManager` optimization, or real-device performance.

---

## 7. Ownership and lifecycle freeze

- `StatusBarIconVisibilityAbi` is immutable.
- `StatusBarIconVisibilityEffect` is immutable.
- `Effect` is created once per `HideIconsSignalHook` installation.
- `Effect` is captured by the local `stateHook`.
- No mutable process-global `Effect` exists.
- No `View`, `Context`, `Activity`, `StatusBarMobileView`, or `MobileIconState` instance is retained by ABI or `Effect`.
- No per-runtime-class ABI `Map`/cache exists.
- No generation manager, coroutine, `Flow`, or second preference/snapshot publication mechanism was introduced.
- Existing observer/publication remains unchanged.

---

## 8. Production scope

C4 production is frozen to these files at `a9fa995...`:

```text
app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt
app/src/main/java/tv/withaibuild/customiuizer/mods/statusbariconvisibility/StatusBarIconVisibilityAbi.kt
app/src/main/java/tv/withaibuild/customiuizer/mods/statusbariconvisibility/StatusBarIconVisibilityResolver.kt
app/src/main/java/tv/withaibuild/customiuizer/mods/statusbariconvisibility/StatusBarIconVisibilityEffect.kt
```

C4 did **not** reopen or change:

- C1 production
- C2 production
- C3 production
- Drawer blur
- `DetailedNetSpeed`
- `NetSpeedStyle`
- `SubscriptionManager` behavior
- global preference architecture

---

## 9. Evidence matrix

| Claim | Classification |
|---|---|
| Resolver component behavior | `RUNTIME_TESTED_COMPONENT` |
| Effect component behavior | `RUNTIME_TESTED_COMPONENT` |
| Hook wiring (resolve once, Effect local, hookAllMethods both names) | `STRUCTURAL` |
| `processFast` contains no `XposedHelpers` field accessors | `STRUCTURAL` |
| No process-global `StatusBarIconVisibilityEffect` | `STRUCTURAL` |
| `StatusBarIconVisibilityHotPathTest` | `RUNTIME_TESTED_COMPONENT` |
| Local `python tools/verify.py full` execution | `LOCAL_EXECUTION_EVIDENCE_ONLY` |
| Real HyperOS `applyMobileState` callback | `NOT_RUNTIME_TESTED_CALLBACK` |
| Real HyperOS `updateState` callback | `NOT_RUNTIME_TESTED_CALLBACK` |
| Preference observer callback | `NOT_RUNTIME_TESTED_CALLBACK` |
| Real HyperOS field owner / type / root | `NOT_PROVEN` |
| Callback thread | `NOT_PROVEN` |
| Real callback frequency | `NOT_PROVEN` |
| Actual FAST-hit rate on target ROM | `NOT_PROVEN` |
| Real-device performance | `NOT_PROVEN` |

All local execution remains `LOCAL_EXECUTION_EVIDENCE_ONLY` and is not promoted to GitHub CI or real-device evidence.

---

## 10. Accepted evidence debt

The following are accepted evidence debt, not current production blockers:

```text
REAL_APPLY_MOBILE_STATE_CALLBACK = NOT_RUNTIME_TESTED_CALLBACK
REAL_UPDATE_STATE_CALLBACK       = NOT_RUNTIME_TESTED_CALLBACK
PREFERENCE_OBSERVER_CALLBACK     = NOT_RUNTIME_TESTED_CALLBACK
REAL_HYPEROS_FIELD_ABI           = NOT_PROVEN
CALLBACK_THREAD                  = NOT_PROVEN
CALLBACK_FREQUENCY               = NOT_PROVEN
REAL_FAST_HIT_RATE               = NOT_PROVEN
REAL_DEVICE_PERFORMANCE          = NOT_PROVEN
```

These debts must not be used to reopen C4 production without a concrete correctness regression or new real-device evidence demonstrating an actual issue.

---

## 11. Final-state block

```text
C4_COMPLETE = true
C4_SELECTED_TARGET = SystemUIStatusBarHooks.HideIconsSignalHook
                     -> StatusBarMobileView.applyMobileState
                     -> StatusBarMobileView.updateState
                     -> mobile-signal visibility/state field-access domain

C4_TARGET_SELECTION_FREEZE = e559df4d3381a8627641072eeed8f4dec1036aee
C4_A0_PREFLIGHT_FREEZE     = 2dd28afe7bb73a7b9cb6046239a55530a15d1776
C4_PRODUCTION_FREEZE       = a9fa99557b93dceccd79a6c58b9fe8d048a7f371
C4_CONSOLIDATION_FREEZE    = dc0cc79209ba8e71087346d392f0815cd41e6968

C4_DEVICE_GATE = DEFERRED_EVIDENCE_DEBT

C1_PRODUCTION_FROZEN = true
C2_PRODUCTION_FROZEN = true
C3_PRODUCTION_FROZEN = true
C4_PRODUCTION_FROZEN = true
```

No production changes occurred after `a9fa99557b93dceccd79a6c58b9fe8d048a7f371`.

C4_HIDE_ICONS_SIGNAL_FINAL_COMPLETION_READY_FOR_INDEPENDENT_AUDIT
