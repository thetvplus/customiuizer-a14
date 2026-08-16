# C3 — Battery Style Architecture C Final Completion / Freeze

## Start gate

| Item | Value |
|---|---|
| Branch | `devin/a14-architecture-c-r14.20.0` |
| Local HEAD | `ae3dc9dcacc536c8df52a17fbcc330c264465579` |
| Remote HEAD | `ae3dc9dcacc536c8df52a17fbcc330c264465579` |
| Worktree | clean |

Start gate passed.

## Frozen C3 history

| Gate | SHA | Role |
|---|---|---|
| `C3_TARGET_SELECTION_FREEZE` | `b37596d7a9841c2980c3a38d93c4f8e190a2a92c` | Target chosen after C2 completion. |
| `C3_A0_PREFLIGHT_FREEZE` | `bad4250394db3a478df4e6ff2ea6509d517d30f7` | A0 preflight decisions frozen. |
| `C3_B1_PRODUCTION_FREEZE` | `0f56f7f91acecc92debcfcf933a9b14ddd4775cf` | Production implementation complete. No production changes after this point. |
| `C3_B2_CONSOLIDATION_FREEZE` | `ae3dc9dcacc536c8df52a17fbcc330c264465579` | Consolidation / final code gate passed. |

`C3_B1_PRODUCTION_FREEZE` is the production freeze. The current `ae3dc9dc` HEAD only adds consolidation documentation and does not alter the frozen production.

## Final Architecture C chain

```text
Cold Resolve
  → BatteryStyleResolver.resolve(ClassLoader?)
  → BatteryStyleAbi?
Frozen ABI
  → resolutionRootClass
  → mBatteryTextDigitView Field
  → mBatteryPercentView Field
  → mBatteryPercentMarkView Field
Existing Typed BatteryStyle Snapshot
  → @Volatile internal var batteryStyle: BatteryStyle?
Per-view Runtime State
  → BatteryViewState stored as customiuizer_battery_view_state
Minimal Effect
  → BatteryStyleEffect(abi) created once at hook installation
Thin after Hook
  → MiuiBatteryMeterView.updateAll after(AfterHookCallback)
Hot Execute
  → reconcileBatteryView(...) → effect-backed helpers
```

## Frozen design decisions

| Decision | Value |
|---|---|
| `CONFIG` | Existing `BatteryStyle` (ordinary class, all `val` fields) |
| `CONFIG_PUBLICATION` | `@Volatile` immutable snapshot, published once per preference change |
| `STYLE_EQUALITY` | Identity (`Any.equals` reference equality) |
| `ABI` | `resolutionRootClass` + three frozen `Field`s |
| `FAST_PATH_ELIGIBILITY` | Exact runtime class match: `parent.javaClass === abi.resolutionRootClass` |
| `FAST_ACCESS` | Direct frozen `Field.get(parent)` |
| `RUNTIME_SUBCLASS` | Legacy `XposedHelpers.getObjectField` fallback |
| `FIELD_SHADOWING` | Runtime-owner hierarchy precedence preserved |
| `EFFECT_OWNERSHIP` | Hook-local captured immutable reference; no mutable process-global effect |
| `CALLBACK_SHAPE` | `after(AfterHookCallback)` |

## Failure contract

### Resolver expected miss

1. Bounded diagnostic log: `BatteryStyleResolver: <reason>; using legacy fallback`.
2. Return `null` ABI.
3. Legacy fallback selected.
4. After hook remains installed.

### Resolver broad `Throwable`

1. `FatalErrors.unwrapAndRethrowIfFatal(t)` first.
2. Fatal errors (`OutOfMemoryError`, `ThreadDeath`, `VirtualMachineError`) propagate.
3. Ordinary exceptions are logged once and return `null`.

### FAST `Field.get`

- `IllegalAccessException` → log → `IllegalAccessError`.
- `IllegalArgumentException` → propagate.

### Runtime field value

- `null` or non-`TextView` → `as? TextView` → `null` → helper short-circuit / no-op.

## Frozen legacy behavior

The following remain unchanged from the pre-C3 oracle:

- `BatteryStyle` identity behavior.
- Preference refresh behavior (new instance → volatile publish → next callback identity mismatch).
- `BatteryViewState` per-view mutable state.
- Baseline capture and recapture logic.
- Child identity semantics.
- Text size, typeface, padding, vertical offset, mark vertical offset.
- `battery4` right-margin routing.
- Swap, restore, and `moveChildTo` idempotence.

This is an architecture refactor with frozen behavior preservation, not a feature rewrite.

## Hot-path cost model

### Per child read

| Path | Lookup cost | Field read |
|---|---|---|
| Legacy (pre-C3) | Xposed generic field cache / name lookup | `Field.get` |
| FAST (exact class) | none | frozen `Field.get` |
| Fallback (subclass / miss) | Xposed field lookup | `Field.get` |

### Field reads per `updateAll` callback

The number of `Field.get` calls is unchanged; only recurring lookup overhead is reduced on the FAST path.

| Scenario | Field reads | Notes |
|---|---|---|
| `FIRST_CUSTOM` | 6 | `captureBatteryBaseline` 3 + `applyBatteryStyle` 3; `state.appliedStyle != style` short-circuits `matchesTarget` |
| `FIRST_DEFAULT` | 6 | `captureBatteryBaseline` 3 + `matchesBaseline` 3; fresh baseline usually matches, no `restoreBatteryBaseline` |
| `STEADY_SAME_CUSTOM_TARGET_MATCH` | 3 | `matchesTarget` 3; `state.appliedStyle === style` so no re-apply |
| `STEADY_SAME_CUSTOM_TARGET_DRIFT` | 6 | `matchesTarget` 3 (false) + `applyBatteryStyle` 3 |
| `PREFERENCE_REFRESH_NEW_STYLE_IDENTITY` | 3 | `applyBatteryStyle` 3; `matchesTarget` short-circuited |
| `CUSTOM_TO_DEFAULT` | 3 | `restoreBatteryBaseline` 3; `matchesBaseline` short-circuited |
| `STEADY_DEFAULT_UNCHANGED` | 6 | Baseline-resolution `matchesBaseline` 3 + default branch `matchesBaseline` 3 |
| `STEADY_DEFAULT_SAME_CHILDREN_OEM_DRIFT` | up to 9 | `matchesBaseline` 3 (false) → `captureBatteryBaseline` 3 → default branch `matchesBaseline` 3 |
| `CHILD_REPLACEMENT_SAME_CUSTOM_STYLE_IDENTITY` | up to 9 | `captureBatteryBaseline` 3 + `matchesTarget` 3 + `applyBatteryStyle` 3; `apply` skipped if target already matches |

No benchmark-backed percentage gains are claimed.

## Evidence matrix

| Evidence | Classification | Notes |
|---|---|---|
| `BatteryArchitectureCTest` resolver/effect/helper runtime assertions | `RUNTIME_TESTED_COMPONENT` | |
| `BatteryViewStateTest` / `BatteryChildReorderBehaviorTest` helper regression | `RUNTIME_TESTED_COMPONENT` | |
| `BatteryStyleSnapshotTest` `readBatteryStyle()` mapping | `RUNTIME_TESTED_COMPONENT` | Observer callback itself: `NOT_RUNTIME_TESTED_CALLBACK` |
| FAST path source absence of generic lookup | `STRUCTURAL` | source inspection |
| Hook-local effect wiring | `STRUCTURAL` | source inspection + reflection invariant test |
| No mutable global `BatteryStyleEffect` | `STRUCTURAL` | reflection design invariant |
| Resolver diagnostic log path | `STRUCTURAL` | source inspection |
| FAST `IllegalAccessException → IllegalAccessError` mapping | `STRUCTURAL` | not feasible to trigger in JVM tests |
| Real `MiuiBatteryMeterView.updateAll` after callback | `NOT_RUNTIME_TESTED_CALLBACK` | |
| Preference observer automatic callback | `NOT_RUNTIME_TESTED_CALLBACK` | not directly exercised |
| Real HyperOS / SystemUI runtime | `NOT_PROVEN` | |
| Callback thread | `NOT_PROVEN` | |
| `BatteryViewState` single-thread confinement | `NOT_PROVEN` | |
| Concurrent access | `NOT_PROVEN` | |

## Accepted evidence debt

C3 is accepted with the following device/runtime evidence debt:

- `REAL_UPDATEALL_CALLBACK = NOT_RUNTIME_TESTED_CALLBACK`
- `REAL_HYPEROS_SYSTEMUI = NOT_PROVEN`
- `CALLBACK_THREAD = NOT_PROVEN`
- `SINGLE_THREAD_CONFINEMENT = NOT_PROVEN`
- `CONCURRENT_ACCESS = NOT_PROVEN`
- `FAST_ILLEGAL_ACCESS_RUNTIME_INJECTION = NOT_RUNTIME_TESTED`

These debts are not current code-gate blockers and must not be used to justify further production changes.

## Scope freeze

C3 production scope is frozen to:

- `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIBatteryHooks.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/battery/BatteryStyleAbi.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/battery/BatteryStyleResolver.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/battery/BatteryStyleEffect.kt`

C3 did **not** modify:

- C1 production
- C2 production
- DetailedNetSpeed
- Drawer blur
- Status bar icon visibility
- `XposedHelpers.java`
- MainModule / preference architecture

## Validation

Because this phase is documentation-only:

```text
git diff --check    # passed
```

No production or test changes were made. Any previous `verify.py` or Gradle test results are `LOCAL_EXECUTION_EVIDENCE_ONLY` and do not prove real-device / HyperOS / Xposed runtime behavior.

## Final state

```text
C3_COMPLETE = true

C3_SELECTED_TARGET =
  SystemUIBatteryHooks.StatusBarStyleBatteryIconHook /
  com.android.systemui.statusbar.views.MiuiBatteryMeterView.updateAll /
  Battery style

C3_PRODUCTION_FREEZE = 0f56f7f91acecc92debcfcf933a9b14ddd4775cf
C3_CONSOLIDATION_FREEZE = ae3dc9dcacc536c8df52a17fbcc330c264465579
C3_DEVICE_GATE = DEFERRED_EVIDENCE_DEBT

C1_PRODUCTION_FROZEN = true
C2_PRODUCTION_FROZEN = true
C3_PRODUCTION_FROZEN = true
```

Battery Style production **MUST NOT** be reopened without a concrete correctness regression or new device evidence demonstrating a real issue.

C3_BATTERY_STYLE_FINAL_COMPLETION_READY_FOR_INDEPENDENT_AUDIT

STOP. Do not select the next Architecture C target. Do not start a new migration.
