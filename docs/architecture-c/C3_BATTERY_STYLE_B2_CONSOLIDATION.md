# C3-B2 — Battery Style Architecture C Consolidation / Final Code Gate

## Frozen inputs

| Item | SHA / state |
|---|---|
| A0 preflight | `bad4250394db3a478df4e6ff2ea6509d517d30f7` |
| B1 production freeze | `0f56f7f91acecc92debcfcf933a9b14ddd4775cf` |
| Consolidation base | `0f56f7f91acecc92debcfcf933a9b14ddd4775cf` |
| Branch | `devin/a14-architecture-c-r14.20.0` |

## Start gate result

```text
git branch --show-current          # devin/a14-architecture-c-r14.20.0
git rev-parse HEAD                 # 0f56f7f91acecc92debcfcf933a9b14ddd4775cf
git rev-parse origin/...           # 0f56f7f91acecc92debcfcf933a9b14ddd4775cf
git status --short                 # clean
```

All gates passed. No production changes were required during B2.

## Production architecture chain (verified)

```text
StatusBarStyleBatteryIconHook
  → installBatteryStyleSnapshot()
  → BatteryStyleResolver.resolve(lpparam.classLoader)
  → BatteryStyleAbi?
  → BatteryStyleEffect(abi)
  → captured local val effect
  → after(AfterHookCallback)
    → batteryStyle volatile snapshot read
    → getOrCreateBatteryViewState(owner)
    → reconcileBatteryView(parent, style, state, effect)
      → captureBatteryBaseline(parent, effect)
      → matchesBaseline(parent, baseline, effect)
      → matchesTarget(parent, baseline, style, effect)
      → restoreBatteryBaseline(parent, baseline, effect)
      → applyBatteryStyle(parent, baseline, style, effect)
        → BatteryStyleEffect.readDigitView/PercentView/MarkView
          → FAST: Field.get(owner) as? TextView
          → LEGACY_FALLBACK: XposedHelpers.getObjectField(owner, exactName) as? TextView
```

### Architecture constraints verified

- No `MainModule.mPrefs` hot read inside the `after` callback; only the volatile `batteryStyle` snapshot is read.
- No runtime field-name discovery on the FAST path; FAST uses frozen `java.lang.reflect.Field`.
- No mutable global Effect publication.
- No per-runtime-class ABI cache, `Map<Class, Abi>`, `AtomicReference`, generation counters, generic Manager, `LateAbi`, coroutine, or `Flow`.

## FAST path audit

### FAST eligibility

```kotlin
fun useFastPath(parent: ViewGroup): Boolean {
    val a = abi ?: return false
    return parent.javaClass === a.resolutionRootClass
}
```

- Eligibility is exact reference equality `===`.
- No subclasses qualify.

### FAST access

```kotlin
private fun readFast(field: Field, parent: ViewGroup): TextView? {
    return try {
        field.get(parent) as? TextView
    } catch (e: IllegalAccessException) {
        XposedHelpers.log(e)
        throw IllegalAccessError(e.message)
    } catch (e: IllegalArgumentException) {
        throw e
    }
}
```

- FAST child reads are `Field.get(parent)` only.
- No recurring `XposedHelpers.getObjectField`, `findField`, or `findFieldIfExists` on the hot path.
- **Removed:** recurring generic field-name/cache lookup.
- **Retained:** frozen `java.lang.reflect.Field.get`.

## Fallback audit

### Fallback trigger

- `abi == null` (cold resolver miss).
- `parent.javaClass !== abi.resolutionRootClass` (runtime subclass).

### Fallback behavior

```kotlin
private fun readLegacy(parent: ViewGroup, fieldName: String): TextView? =
    XposedHelpers.getObjectField(parent, fieldName) as? TextView
```

- Uses `XposedHelpers.getObjectField` on the runtime owner.
- Preserves runtime-owner hierarchy precedence.
- Covers:
  - subclass field shadowing;
  - subclass-only field;
  - declared `View`/`Object` field with runtime `TextView` value;
  - runtime non-`TextView` → `as? TextView` no-op;
  - runtime `null` → `as? TextView` no-op.

- Fallback is a deliberate compatibility path, not a migration failure.

## Callback / Effect ownership audit

### No mutable global Effect

- `SystemUIBatteryHooks` has no `batteryStyleEffect` field.
- Only an immutable private fallback:
  ```kotlin
  private val FALLBACK_BATTERY_STYLE_EFFECT = BatteryStyleEffect(null)
  ```

### Hook captures local Effect

```kotlin
fun StatusBarStyleBatteryIconHook(lpparam: PackageReadyParam) {
    installBatteryStyleSnapshot()
    val abi = BatteryStyleResolver.resolve(lpparam.classLoader)
    val effect = BatteryStyleEffect(abi)
    ModuleHelper.findAndHookMethod("...MiuiBatteryMeterView", ...) {
        override fun after(param: AfterHookCallback) {
            ...
            reconcileBatteryView(batteryView, style, state, effect)
        }
    }
}
```

- `effect` is a captured local `val`.
- `reconcileBatteryView` is called with the captured effect.
- All helpers inside `reconcileBatteryView` use the same effect.

### Classification

| Evidence | Classification |
|---|---|
| Source showing `val effect` and `reconcileBatteryView(..., effect)` | `STRUCTURAL` |
| `BatteryArchitectureCTest.noMutableGlobalBatteryStyleEffect` (reflection) | `STRUCTURAL` |
| `BatteryArchitectureCTest.reconcileBatteryView_usesSuppliedEffect` | `RUNTIME_TESTED_COMPONENT` |
| Real SystemUI callback publication / thread | `NOT_PROVEN` |

## BatteryStyle publication audit

- `BatteryStyle` is an ordinary `internal class` with all `val` fields.
- No `equals`/`hashCode` override; identity is the default Kotlin `Any.equals`.
- `@Volatile internal var batteryStyle: BatteryStyle?` unchanged.
- Preference observer publishes a new `BatteryStyle` instance on each relevant preference change.
- `state.appliedStyle != style` is identity comparison, so a new instance forces re-apply.
- No structural equality introduced.

## View state / ownership audit

- `BatteryViewState` is per-view mutable state stored via `customiuizer_battery_view_state` additional-instance field.
- Weak identity key storage remains unchanged.
- State is neither proven thread-safe nor proven single-thread confined.

| Property | Classification |
|---|---|
| Callback thread | `NOT_PROVEN` |
| Single-thread confinement | `NOT_PROVEN` |
| Concurrent access | `NOT_PROVEN` |

## Exact behavior oracle regression check

`reconcileBatteryView` control flow (unchanged from A0 / B1 oracle):

```text
children changed? → recapture
state.appliedStyle == null && !matchesBaseline? → recapture
else → keep baseline
default style? →
  state.appliedStyle != null || !matchesBaseline? → restore
  appliedStyle = null
custom style? →
  state.appliedStyle != style || !matchesTarget? → apply
  appliedStyle = style
```

### Kotlin boolean short-circuit

- Custom-style branch: `state.appliedStyle != style || !matchesTarget(...)`
  - When `state.appliedStyle !== style` (new identity), `matchesTarget` is **not** executed.
  - Field reads in this case come only from the `applyBatteryStyle` path.
- Default-style branch: `state.appliedStyle != null || !matchesBaseline(...)`
  - When `state.appliedStyle` is non-null (previous custom style not restored), `matchesBaseline` is **not** executed.
  - Field reads in this case come only from the `restoreBatteryBaseline` path.

- Baseline recapture logic unchanged.
- View mutation semantics (text size, bold, padding, vertical offset, battery4 margin, swap, restore, child order) unchanged.

## Failure semantics audit

### Resolver

- Expected missing class/field:
  - `XposedHelpers.log("BatteryStyleResolver: <reason>; using legacy fallback")`
  - returns `null`
  - after hook remains installed
- Broad `Throwable`:
  - `FatalErrors.unwrapAndRethrowIfFatal(t)` first
  - `XposedHelpers.log(t)`
  - returns `null`
- Fatal categories (`OutOfMemoryError`, `ThreadDeath`, `VirtualMachineError`) are not swallowed.

### FAST Field.get

- `IllegalAccessException` → log → `IllegalAccessError`.
- `IllegalArgumentException` → rethrow.

### Runtime value

- `null` or non-`TextView` runtime value → `as? TextView` → `null` → no-op/false/null.

No silent semantic narrowing detected.

## Test inventory and classification

| Test | Classification | Notes |
|---|---|---|
| `BatteryArchitectureCTest.exactTargetClass_selectsFastPath_andReturnsSameValueAsLegacy` | `RUNTIME_TESTED_COMPONENT` | FAST == legacy value |
| `BatteryArchitectureCTest.runtimeSubclass_doesNotSelectFastPath` | `RUNTIME_TESTED_COMPONENT` | subclass fallback |
| `BatteryArchitectureCTest.runtimeSubclassShadowing_fallbackPreservesRuntimeOwnerPrecedence` | `RUNTIME_TESTED_COMPONENT` | shadow field precedence |
| `BatteryArchitectureCTest.subclassOnlyField_legacyFallbackSucceeds` | `RUNTIME_TESTED_COMPONENT` | subclass-only field |
| `BatteryArchitectureCTest.fieldDeclaredAsSupertype_runtimeTextView_supported` | `RUNTIME_TESTED_COMPONENT` | declared `View`/`Object` |
| `BatteryArchitectureCTest.nonTextViewFieldValue_safeCastNoOp` | `RUNTIME_TESTED_COMPONENT` | safe-cast no-op |
| `BatteryArchitectureCTest.nullFieldValue_safeCastNoOp` | `RUNTIME_TESTED_COMPONENT` | null no-op |
| `BatteryArchitectureCTest.resolver_returnsFrozenAbiForTargetClassWithAllFields` | `RUNTIME_TESTED_COMPONENT` | resolver success |
| `BatteryArchitectureCTest.resolver_returnsNullWhenTargetClassMissesField` | `RUNTIME_TESTED_COMPONENT` | field miss fallback |
| `BatteryArchitectureCTest.resolverMissingClass_returnsNullForFallback` | `RUNTIME_TESTED_COMPONENT` | class miss -> null ABI / fallback |
| `BatteryArchitectureCTest.resolverMissingDigitField_*` / `Percent` / `Mark` | `RUNTIME_TESTED_COMPONENT` | per-field miss -> null ABI / fallback |
| Resolver ordinary-failure diagnostic emission | `STRUCTURAL` | `BatteryStyleResolver.missing(...)` calls `XposedHelpers.log(...)` (source inspection) |
| `BatteryArchitectureCTest.resolverFatal_propaatesImmediately` | `RUNTIME_TESTED_COMPONENT` | fatal boundary |
| `BatteryArchitectureCTest.batteryStyleIdentityRefresh_forcesReApply` | `RUNTIME_TESTED_COMPONENT` | identity equality |
| `BatteryArchitectureCTest.childReplacement_recapturesBaseline` | `RUNTIME_TESTED_COMPONENT` | child replacement |
| `BatteryArchitectureCTest.swapIsIdempotent` | `RUNTIME_TESTED_COMPONENT` | swap idempotence |
| `BatteryArchitectureCTest.battery4RightMarginRoutedToPercent` | `RUNTIME_TESTED_COMPONENT` | battery4 routing |
| `BatteryArchitectureCTest.noMutableGlobalBatteryStyleEffect` | `STRUCTURAL` | reflection design invariant |
| `BatteryArchitectureCTest.reconcileBatteryView_usesSuppliedEffect` | `RUNTIME_TESTED_COMPONENT` | explicit effect propagation |
| `BatteryArchitectureCTest.fastPathSourceDoesNotContainXposedGetObjectField` | `STRUCTURAL` | source inspection |
| `BatteryArchitectureCTest.hookCallbackCapturesLocalEffectSource` | `STRUCTURAL` | source inspection |
| `BatteryViewStateTest` (existing) | `RUNTIME_TESTED_COMPONENT` | regression coverage, uses fallback defaults |
| `BatteryChildReorderBehaviorTest` (existing) | `RUNTIME_TESTED_COMPONENT` | child reorder |
| `BatteryStyleSnapshotTest` (existing) | `RUNTIME_TESTED_COMPONENT` | `readBatteryStyle()` mapping; observer callback `NOT_RUNTIME_TESTED_CALLBACK` |

Remaining `NOT_PROVEN` / `NOT_RUNTIME_TESTED_CALLBACK`:

| Item | Classification |
|---|---|
| Real `MiuiBatteryMeterView.updateAll` after callback | `NOT_RUNTIME_TESTED_CALLBACK` |
| Real HyperOS / SystemUI runtime | `NOT_PROVEN` |
| Callback thread | `NOT_PROVEN` |
| `BatteryViewState` single-thread confinement | `NOT_PROVEN` |
| Concurrent access | `NOT_PROVEN` |
| Resolver diagnostic log emission | `STRUCTURAL` |

## Allocation audit

- No recurring `Pair`, `Triple`, `List`, `Map`, `Sequence`, `Flow`, coroutine, or per-call child-view holder.
- `BatteryStyleEffect` created once at hook installation.
- `BatteryStyleAbi` created once at successful cold resolution.
- `BatteryBaseline` and `childIds` allocation unchanged from legacy path.

## Hot-path cost model (B1 vs legacy)

| Path | Cost per child read |
|---|---|
| Legacy (before C3) | Xposed field cache lookup + `Field.get` |
| FAST (exact class) | direct frozen `Field.get` only |
| Fallback (subclass / miss) | Xposed field lookup + `Field.get` |

### Field-read counts per updateAll callback

The number of field reads is unchanged; only the lookup overhead changes on the FAST path.

### Short-circuit oracle

For the custom-style branch:

```text
state.appliedStyle != style || !matchesTarget(...)
```

- If `state.appliedStyle !== style` (new identity or first application), `matchesTarget` is **not** executed due to Kotlin short-circuit `||`.

For the default-style branch:

```text
state.appliedStyle != null || !matchesBaseline(...)
```

- If `state.appliedStyle` is non-null (a custom style was previously applied), `matchesBaseline` is **not** executed.

### Field-read counts per updateAll callback

| Scenario | Field reads | Notes |
|---|---|---|
| FIRST_CUSTOM | 6 | `captureBatteryBaseline` 3 + `applyBatteryStyle` 3; `state.appliedStyle != style` short-circuits `matchesTarget` |
| FIRST_DEFAULT | 6 | `captureBatteryBaseline` 3 + `matchesBaseline` 3; fresh baseline usually matches, so no `restoreBatteryBaseline` |
| STEADY_SAME_CUSTOM_TARGET_MATCH | 3 | `matchesTarget` 3; `state.appliedStyle === style` so no re-apply |
| STEADY_SAME_CUSTOM_TARGET_DRIFT | 6 | `matchesTarget` 3 (false) + `applyBatteryStyle` 3 |
| PREFERENCE_REFRESH_NEW_STYLE_IDENTITY | 3 | `applyBatteryStyle` 3; `state.appliedStyle != style` is true, so `matchesTarget` is short-circuited |
| CUSTOM_TO_DEFAULT | 3 | `restoreBatteryBaseline` 3; `state.appliedStyle != null` is true, so `matchesBaseline` is short-circuited |
| STEADY_DEFAULT_UNCHANGED | 6 | Baseline-resolution `matchesBaseline` 3 + default branch `matchesBaseline` 3; both match, no `restoreBatteryBaseline` |
| STEADY_DEFAULT_SAME_CHILDREN_OEM_DRIFT | up to 9 | `matchesBaseline` 3 (false) → `captureBatteryBaseline` 3 → default branch `matchesBaseline` 3; no restore if the new baseline matches OEM defaults |
| CHILD_REPLACEMENT_SAME_CUSTOM_STYLE_IDENTITY | up to 9 | `captureBatteryBaseline` 3 + `matchesTarget` 3 + `applyBatteryStyle` 3; `apply` is skipped if the replaced children already match the target style |

No benchmark numbers claimed; this is a structural field-read count analysis.

## Scope audit

From `bad42503` (A0) to `0f56f7f9` (B1 production freeze), production changes are limited to:

- `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIBatteryHooks.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/battery/BatteryStyleAbi.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/battery/BatteryStyleResolver.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/battery/BatteryStyleEffect.kt`

B2 made no production changes; it added focused structural tests and this consolidation document only.

Unchanged:

- C1 production
- C2 production
- DetailedNetSpeed
- Drawer blur
- Status bar icon visibility
- `XposedHelpers.java`
- MainModule / preference architecture

## Validation

```text
git diff --check                        # passed
python tools/verify.py full             # passed
  - static rules passed
  - observer-key-contract: passed
  - check-invariants: 236 files, no violations
  - audit-feature-semantics: Validation passed
  - gradlew compileDebugKotlin compileDebugJavaWithJavac: ok
  - gradlew testDebugUnitTest: ok
  - gradlew lintDebug: ok
.\gradlew.bat :app:testDebugUnitTest    # local Gradle output: 1627 tests completed, all passed
```

All validation results are `LOCAL_EXECUTION_EVIDENCE_ONLY`. They do not prove real-device / HyperOS / Xposed runtime behavior.

## Final decision

No concrete correctness blockers found.

C3_B2_BATTERY_STYLE_FACTUAL_CORRECTIVE_READY_FOR_INDEPENDENT_AUDIT

STOP. Do not enter C3 final completion. Do not select the next target.
