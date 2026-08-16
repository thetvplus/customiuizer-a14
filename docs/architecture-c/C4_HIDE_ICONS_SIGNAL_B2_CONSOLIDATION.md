# C4-B2 — HideIconsSignal Architecture C Consolidation / Final Code Gate

**Repository:** `tomthenpc/customiuizer-a14`

**Branch:** `devin/a14-architecture-c-r14.20.0`

**C4 target-selection freeze:** `e559df4d3381a8627641072eeed8f4dec1036aee`

**C4 A0 preflight freeze:** `2dd28afe7bb73a7b9cb6046239a55530a15d1776`

**C4 B1 production/final freeze:** `a9fa99557b93dceccd79a6c58b9fe8d048a7f371`

**B2 start gate:** `a9fa99557b93dceccd79a6c58b9fe8d048a7f371`

**Evidence classification:** `LOCAL_EXECUTION_EVIDENCE_ONLY`

---

## A. Frozen inputs

| Input | SHA | Scope |
|---|---|---|
| C4 target selection | `e559df4d3381a8627641072eeed8f4dec1036aee` | `SystemUIStatusBarHooks.HideIconsSignalHook`, `StatusBarMobileView.applyMobileState`, `StatusBarMobileView.updateState` |
| C4 A0 preflight | `2dd28afe7bb73a7b9cb6046239a55530a15d1776` | contract text: resolution roots, primitive `wifiAvailable`, exact-root FAST, no-fallback-after-fast, etc. |
| C4 B1 production/final | `a9fa99557b93dceccd79a6c58b9fe8d048a7f371` | `StatusBarIconVisibilityResolver`, `StatusBarIconVisibilityAbi`, `StatusBarIconVisibilityEffect`, `SystemUIStatusBarHooks.HideIconsSignalHook` wiring |
| Current branch | `devin/a14-architecture-c-r14.20.0` | consistent local/remote/merge-base at `a9fa995...` |

---

## B. Final production chain

```text
HideIconsSignalHook(lpparam)
  ├─ ensureStatusBarIconVisibilityRuntimeState()
  ├─ StatusBarIconVisibilityResolver.resolve(lpparam.classLoader)
  │   └─ StatusBarIconVisibilityAbi?
  ├─ StatusBarIconVisibilityEffect(abi) { currentOrBuildStatusBarIconVisibilitySnapshot() }
  ├─ stateHook = MethodHook { before(param) -> effect.before(param) }
  ├─ ModuleHelper.hookAllMethods(StatusBarMobileView, "applyMobileState", stateHook)
  └─ ModuleHelper.hookAllMethods(StatusBarMobileView, "updateState", stateHook)

MethodHook.before(param)
  └─ Effect.before(param)
      ├─ getArg(0)              -> mobileIconState
      ├─ getMember().name       -> methodName
      ├─ getThisObject()        -> thisObject  (mode eligibility, not a field access)
      └─ process(thisObject, mobileIconState, methodName)
          ├─ isFastEligible(thisObject, mobileIconState)
          │   ├─ abi != null
          │   ├─ thisObject != null
          │   ├─ mobileIconState != null
          │   ├─ thisObject.javaClass === abi.statusBarMobileViewResolutionRootClass
          │   └─ mobileIconState.javaClass === abi.mobileIconStateResolutionRootClass
          │
          ├─ if useFast:  processFast(thisObject, mobileIconState, methodName)
          │   └─ oracle may early return (mState != null, visible == false, ...)
          └─ else:        processLegacy(thisObject, mobileIconState, methodName)
              └─ oracle may early return (mState != null, visible == false, ...)

processFast / processLegacy oracle
  ├─ if !updateState: read mState
  ├─ if mState != null (non-updateState only): early return
  ├─ snapshot = currentOrBuildStatusBarIconVisibilitySnapshot()
  ├─ wifiAvailable
  ├─ subId
  ├─ SubscriptionManager.getActiveDataSubscriptionId()
  ├─ SubscriptionManager.getSlotIndex(subId)
  ├─ computeSignalIconHiding(...)
  ├─ visible write; if visible == false: early return
  ├─ roaming write
  ├─ volte write
  └─ speechHd write
```

`isFastEligible` returning `false` **never** causes an early callback return. It only selects `processLegacy`. Early returns happen only inside the selected path for normal callback-oracle reasons (`mState != null`, `visible == false`).

Source files:

- `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt:2532-2544`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/statusbariconvisibility/StatusBarIconVisibilityEffect.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/statusbariconvisibility/StatusBarIconVisibilityResolver.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/statusbariconvisibility/StatusBarIconVisibilityAbi.kt`

---

## C. Hook-surface audit

- `ModuleHelper.hookAllMethods` is preserved for **both** method names.
- `hookAllMethods` installs on all declared methods named `applyMobileState` and `updateState` in the named class; it does not filter overloads by signature.
- The callback receives `BeforeHookCallback` and immediately calls `getArg(0)`, treating the argument at index 0 as the `MobileIconState` instance.
- The callback therefore assumes `arg(0)` exists and is the intended state object; it does not branch on overload signature, but it does depend on `getArg(0)` behavior.

Declared facts:

| Statement | Classification |
|---|---|
| `applyMobileState` is hooked by `hookAllMethods` (source inspection) | STRUCTURAL |
| `updateState` is hooked by `hookAllMethods` (source inspection) | STRUCTURAL |
| `REAL_METHOD_OVERLOAD_SET = NOT_PROVEN` | NOT_PROVEN |
| `ZERO_ARG_OVERLOAD_GETARG0_BEHAVIOR = NOT_PROVEN` | NOT_PROVEN |

No real HyperOS method signatures are inferred.

---

## D. Callback oracle

Frozen accessor/execution order in `StatusBarIconVisibilityEffect.before`:

1. `param.getArg(0)` — `mobileIconState`
2. `param.getMember().name` — `methodName`
3. `param.getThisObject()` — `thisObject` (mode eligibility only, not a field access)
4. `process(thisObject, mobileIconState, methodName)`
5. `isFastEligible(thisObject, mobileIconState)`
6. If eligible: `processFast(...)`; otherwise: `processLegacy(...)`
7. Inside the selected path, if `methodName != "updateState"`: read `mState`
8. If `mState != null` and not `updateState`: early return
9. Build or use snapshot
10. Read `wifiAvailable`
11. Read `subId`
12. `SubscriptionManager.getActiveDataSubscriptionId()`
13. `SubscriptionManager.getSlotIndex(subId)`
14. `computeSignalIconHiding(...)`
15. Write `visible`; if `visible == false`: early return
16. Write `roaming`
17. Write `volte`
18. Write `speechHd`

The `getThisObject()` call is in addition to the original semantic oracle: it is needed for the exact-root FAST eligibility check and does not replace or precede `getArg(0)`. `getThisObject` returns the existing receiver reference; it is not a field access.

`isFastEligible` short-circuits on `&&`, so the number of identity comparisons evaluated depends on runtime values:

| Condition | Equality comparisons evaluated |
|---|---|
| `abi == null` | 0 |
| `thisObject == null` | 0 |
| `thisObject != null && mobileIconState == null` | 0 |
| Both non-null, `thisObject.javaClass !== root` | 1 |
| Both non-null, `thisObject` exact, `mobileIconState` mismatch | 2 |
| Both non-null, both exact | 2 → FAST |

---

## E. Resolver audit

### StatusBarMobileView root

Resolved by name at hook install:

```text
STATUS_BAR_MOBILE_VIEW_CLASS = "com.android.systemui.statusbar.StatusBarMobileView"
```

Source: `app/src/main/java/tv/withaibuild/customiuizer/mods/statusbariconvisibility/StatusBarIconVisibilityResolver.kt`

### MobileIconState root derivation

1. Inspect declared `applyMobileState` and `updateState` methods.
2. Accept only single-parameter, non-primitive, non-`java.lang.Object` candidates.
3. Collect distinct parameter types.
4. If the distinct set size is `1`: use that type as `mobileIconStateResolutionRootClass`.
5. Otherwise inspect `mState.type`:
   - must be concrete, non-primitive, non-`Object`;
   - if the candidate set is empty: `mState` type may provide the root;
   - if the candidate set is ambiguous: `mState` type may provide the root only if it exactly matches one of the candidate types;
   - otherwise: expected miss.
6. Any safe root found is then field-validated; missing or incompatible fields cause `null` ABI.

| Statement | Classification |
|---|---|
| Resolver derives `MobileIconState` root from hook member ABI | RUNTIME_TESTED_COMPONENT |
| `REAL_HYPEROS_MOBILE_ICON_STATE_ROOT = NOT_PROVEN` | NOT_PROVEN |

---

## F. Frozen field ABI

### StatusBarMobileView

| Field | Type expectation | Source |
|---|---|---|
| `mState` | `Object` or concrete `MobileIconState` | root-first superclass traversal; inherited allowed on exact root |

### MobileIconState

| Field | Constraint | Source |
|---|---|---|
| `wifiAvailable` | primitive `boolean` (`Field.type == Boolean.TYPE`) only; `Boolean.class` rejected | `StatusBarIconVisibilityResolver.resolvePrimitiveBooleanField` |
| `subId` | `int` / `Integer`; `Field.get(...)` then `as Int` | `StatusBarIconVisibilityResolver.resolveIntOrIntegerField` |
| `visible` | `boolean` / `Boolean` writable | `StatusBarIconVisibilityResolver.resolveBooleanWritableField` |
| `roaming` | `boolean` / `Boolean` writable | same |
| `volte` | `boolean` / `Boolean` writable | same |
| `speechHd` | `boolean` / `Boolean` writable | same |

FAST field access:

- `mState`: `Field.get(thisObject)`
- `wifiAvailable`: `Field.getBoolean(mobileIconState)`
- `subId`: `Field.get(mobileIconState) as Int`
- writes: `Field.set(mobileIconState, value)`

---

## G. FAST eligibility

Exact runtime-class equality for both receivers:

```kotlin
thisObject.javaClass === abi.statusBarMobileViewResolutionRootClass
mobileIconState.javaClass === abi.mobileIconStateResolutionRootClass
```

Any mismatch — strict subclass, superclass, unrelated, or any other `!==` — triggers complete pre-fast LEGACY fallback. This happens before the first fast field operation (`Field.get` on `mState` in non-`updateState` case).

Inherited resolved `Field` on exact root is allowed because the runtime class equals the resolution root, even when `field.declaringClass !== resolutionRootClass`.

---

## H. Shadowing proof

- The ABI `mStateField` is the first `mState` field found by root-first superclass traversal starting at the resolution root.
- When `thisObject` is exactly the resolution root, `Field.get(thisObject)` on that field reads the same memory as `XposedHelpers.getObjectField(thisObject, "mState")` because the runtime class is the root and the field is either declared on the root or inherited from a superclass.
- When `thisObject` is a strict subclass, the runtime class differs from the root. Exact-root FAST is ineligible, so the callback uses complete LEGACY `XposedHelpers.getObjectField`. `XposedHelpers` starts its lookup at the runtime class, so a shadowed `mState` in a subclass is preserved.
- A superclass or unrelated runtime class also fails exact equality, so legacy `XposedHelpers` lookup runs from that runtime class.

Therefore exact-root FAST is equivalent to runtime-class-first legacy lookup only when `thisObject.javaClass === root`; any mismatch falls back before FAST begins.

---

## I. FAST failure semantics

Once FAST begins (`Field.get` on `mState`, `Field.getBoolean` on `wifiAvailable`, `Field.get` on `subId`, or the `as Int` cast), fallback to `XposedHelpers` is forbidden.

| Failure | Semantics |
|---|---|
| `IllegalAccessException` from a `Field.*` call | log, wrap in `IllegalAccessError`, propagate |
| `IllegalArgumentException` from a `Field.*` call | propagate to `MethodHook` boundary; no retry |
| `ClassCastException` from `subId` cast | propagate; no retry |
| Ordinary later failure | stop remaining callback mutations |
| Earlier successful mutations | remain |
| `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError` | propagate immediately, not converted to fallback |

---

## J. Resolver failure semantics

| Condition | Result |
|---|---|
| Class not found | expected miss; `null` ABI → LEGACY |
| Required field missing | `null` ABI → LEGACY |
| `wifiAvailable` not primitive boolean | `null` ABI → LEGACY |
| Root derivation fails / ambiguous | `null` ABI → LEGACY |
| Ordinary nonfatal `Throwable` during resolution | log, `null` ABI; no fatal propagation |
| `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError` | propagate immediately |

Class-not-found is an *expected miss*, not proof of general ordinary-`Throwable` handling. The dedicated `ClassLoader` throwing `RuntimeException` test is the evidence for ordinary-`Throwable` fallback.

---

## K. Config / publication

`StatusBarIconVisibilityRuntimeState` remains unchanged and is reused:

- `AtomicReference<StatusBarIconVisibilitySnapshot?>` for the published snapshot.
- `AtomicLong` for snapshot `id` generation.
- Preference observer registered in `ensureStatusBarIconVisibilityRuntimeState`.
- `currentOrBuildStatusBarIconVisibilitySnapshot()` is the only snapshot publication path used by the effect.

No second publication mechanism was introduced.

| Statement | Classification |
|---|---|
| Snapshot/publication mechanism reused | RUNTIME_TESTED_COMPONENT |
| Preference observer callback | NOT_RUNTIME_TESTED_CALLBACK |

---

## L. Lifecycle / ownership

- `StatusBarIconVisibilityAbi` is immutable (`val` fields only).
- `StatusBarIconVisibilityEffect` is immutable (`val abi`, `val snapshotProvider`); all declared fields are `final`.
- The `Effect` instance is created as a local `val` inside `HideIconsSignalHook` and captured by the `stateHook` object.
- No `View`, `Context`, `Activity`, `StatusBarMobileView`, or `MobileIconState` instance is retained by the ABI or Effect.
- No per-runtime-class ABI `Map` cache exists.
- No coroutine, `Flow`, or manager abstraction was introduced.
- `SystemUIStatusBarHooks` has no `StatusBarIconVisibilityEffect` field.

---

## M. Hot-path audit

### Steady-state models

**LEGACY steady-state field access:**

```text
runtime receiver Class
  -> XposedHelpers fieldCache lookup for that Class
  -> field-name lookup
  -> cached Field
  -> Field.get / getBoolean / set
```

On a **cache MISS only**:

```text
findFieldRecursiveImpl
  -> getDeclaredField
  -> superclass traversal
  -> setAccessible
  -> cache Field
```

**FAST exact-root steady-state:**

```text
exact runtime-class eligibility (short-circuit &&, 0–2 identity comparisons)
  -> already frozen Field
  -> Field.get / getBoolean / set
```

### What the migration removes on FAST-eligible callbacks

The recurring `XposedHelpers` helper + runtime-class/name field-cache lookup is removed for FAST-eligible callbacks. `Field` access itself remains.

### What remains

- `Field.get` / `getBoolean` / `get` / `set` (still reflection, but on frozen fields).
- `XposedHelpers` runtime-class-first lookup for all LEGACY-eligible callbacks.
- `SubscriptionManager.getActiveDataSubscriptionId()` and `getSlotIndex(subId)` (unchanged, out of scope).

### No over-claim

No claim is made that:

- field access was eliminated,
- reflection was eliminated entirely,
- recursive `getDeclaredField` existed on every callback,
- `findFieldIfExists` ran per callback,
- allocation was eliminated,
- `SubscriptionManager` cost improved,
- callback frequency is known,
- real-device speedup was measured.

---

## N. Hot-path operation table

Class equality checks are `0–2` because `isFastEligible` uses short-circuit `&&`. The count is exactly `2` only when both `thisObject` and `mobileIconState` are non-null and both reach the runtime-class identity comparisons. It is `0` when `abi` is null, `thisObject` is null, or `mobileIconState` is null. It is `1` when `thisObject` is non-null and fails the first identity check (the second is not evaluated).

### 1. `applyMobileState`, `mState != null`

| Step | Operation |
|---|---|
| class checks | 0–2 identity checks (short-circuit) |
| mode | FAST or LEGACY |
| `mState` read | `Field.get` or `XposedHelpers.getObjectField` |
| `mState != null` | early return inside selected path |
| `SubscriptionManager` | not called |
| writes | none |

### 2. `applyMobileState`, `mState == null`, `visible=false`

| Step | Operation |
|---|---|
| class checks | 0–2 identity checks (2 for exact-root FAST) |
| `mState` | `Field.get` / `XposedHelpers.getObjectField`; null |
| snapshot | `currentOrBuildStatusBarIconVisibilitySnapshot()` |
| `wifiAvailable` | `Field.getBoolean` / `XposedHelpers.getBooleanField` |
| `subId` | `Field.get` + `as Int` / `XposedHelpers.getObjectField` + cast |
| `getActiveDataSubscriptionId` | 1 call |
| `getSlotIndex` | 1 call |
| `computeSignalIconHiding` | 1 call |
| `visible` | `Field.set(..., false)` / `XposedHelpers.setObjectField(..., false)`; early return |
| `roaming/volte/speechHd` | not reached |

### 3. `applyMobileState`, `mState == null`, roaming/volte mutation

Same as (2), plus:

| Step | Operation |
|---|---|
| `roaming` | `Field.set` / `XposedHelpers.setObjectField` |
| `volte` | `Field.set` / `XposedHelpers.setObjectField` |
| `speechHd` | `Field.set` / `XposedHelpers.setObjectField` |

### 4. `updateState`, `visible=false`

| Step | Operation |
|---|---|
| class checks | 0–2 identity checks (2 for exact-root FAST) |
| `mState` | skipped |
| snapshot / `wifiAvailable` / `subId` / `SubscriptionManager` / compute | as above |
| `visible=false` | write + early return |

### 5. `updateState`, roaming/volte mutation

| Step | Operation |
|---|---|
| class checks | 0–2 identity checks (2 for exact-root FAST) |
| `mState` | skipped |
| snapshot / reads / `SubscriptionManager` / compute | as above |
| `roaming` / `volte` / `speechHd` | written in order |

### 6. Runtime-class mismatch → LEGACY

| Step | Operation |
|---|---|
| class checks | 0–2 identity checks (short-circuit; 1 if `thisObject` fails first comparison, 0 if null/abi null) |
| all subsequent work | complete `XposedHelpers` legacy path from the runtime classes |

Short-circuit: the mode decision happens before any `Field.get` or `XposedHelpers` field access. `isFastEligible == false` selects `processLegacy`; it does **not** return early.

---

## O. Behavior compatibility

The following remain unchanged from the B1 production freeze:

- `applyMobileState` `mState` gating (non-`updateState` reads `mState` first).
- `updateState` skips the `mState` field read.
- Mode (FAST/LEGACY) is decided before the snapshot or any field access.
- Snapshot is read after mode selection and after the `mState` non-null short-circuit (for non-`updateState`).
- `visible=false` early return after the first write.
- `roaming` is written before `volte`.
- `speechHd` is written only inside the `volte` branch.
- The `mobileIconState` object itself is not replaced; only its fields are mutated.
- The original method proceeds after the `before` callback.
- Partial mutation semantics are preserved (earlier writes survive a later fast failure).
- `isFastEligible == false` falls back to the complete LEGACY path; it does not return early.

---

## P. Evidence matrix

| Claim / Component | Evidence classification |
|---|---|
| Resolver exact-root/inherited field resolution | RUNTIME_TESTED_COMPONENT |
| Resolver primitive `wifiAvailable` enforcement | RUNTIME_TESTED_COMPONENT |
| Resolver root derivation from hook member ABI | RUNTIME_TESTED_COMPONENT |
| Resolver ordinary-`Throwable` fallback | RUNTIME_TESTED_COMPONENT |
| Resolver fatal error propagation | RUNTIME_TESTED_COMPONENT |
| Effect exact-root FAST | RUNTIME_TESTED_COMPONENT |
| Effect LEGACY on class mismatch | RUNTIME_TESTED_COMPONENT |
| Effect shadowing preservation | RUNTIME_TESTED_COMPONENT |
| Effect failure mapping and partial mutation | RUNTIME_TESTED_COMPONENT |
| Effect immutable / no mutable process-global | STRUCTURAL |
| Hook wiring: ABI resolved once, Effect local, hookAllMethods both names | STRUCTURAL |
| `processFast` contains no `XposedHelpers` field accessors | STRUCTURAL |
| `SystemUIStatusBarHooks` has no `StatusBarIconVisibilityEffect` field | STRUCTURAL |
| `StatusBarIconVisibilityHotPathTest` | RUNTIME_TESTED_COMPONENT |
| `python tools/verify.py full` (compile, test, lint) | LOCAL_EXECUTION_EVIDENCE_ONLY |
| Real `applyMobileState` callback on HyperOS | NOT_RUNTIME_TESTED_CALLBACK |
| Real `updateState` callback on HyperOS | NOT_RUNTIME_TESTED_CALLBACK |
| Real HyperOS field owner / type / root | NOT_PROVEN |
| Callback thread | NOT_PROVEN |
| Real callback frequency | NOT_PROVEN |
| Preference observer callback | NOT_RUNTIME_TESTED_CALLBACK |

Fixture-driven `Resolver` / `Effect` tests are **not** classified as real callbacks.

---

## Q. Scope audit

### Files changed from C4 A0 freeze (`2dd28afe...`) to C4 B1 freeze (`a9fa995...`)

```text
app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt
app/src/main/java/tv/withaibuild/customiuizer/mods/statusbariconvisibility/StatusBarIconVisibilityAbi.kt
app/src/main/java/tv/withaibuild/customiuizer/mods/statusbariconvisibility/StatusBarIconVisibilityEffect.kt
app/src/main/java/tv/withaibuild/customiuizer/mods/statusbariconvisibility/StatusBarIconVisibilityResolver.kt
app/src/test/java/tv/withaibuild/customiuizer/mods/statusbariconvisibility/StatusBarIconVisibilityEffectTest.kt
app/src/test/java/tv/withaibuild/customiuizer/mods/statusbariconvisibility/StatusBarIconVisibilityFixtures.java
app/src/test/java/tv/withaibuild/customiuizer/mods/statusbariconvisibility/StatusBarIconVisibilityResolverTest.kt
```

### Confirmed unchanged

- C1 / C2 / C3 production files: unchanged.
- Drawer blur hooks: unchanged (`SystemUIStatusBarHooks.kt` modifications only inside `HideIconsSignalHook` and the new package imports).
- `SubscriptionManager` semantics: unchanged (same `getActiveDataSubscriptionId()` and `getSlotIndex(subId)` calls, same call order).
- C1/C2/C3 production frozen files: no modifications between `2dd28afe...` and `a9fa995...`.

### B2 production change expectation

```text
PRODUCTION_CHANGED_IN_B2 = false
```

B2 adds only:

- `docs/architecture-c/C4_HIDE_ICONS_SIGNAL_B2_CONSOLIDATION.md`
- `app/src/test/java/tv/withaibuild/customiuizer/mods/statusbariconvisibility/C4HideIconsSignalB2StructuralTest.kt`

---

## B2 structural test summary

`C4HideIconsSignalB2StructuralTest` covers:

1. `hideIconsSignalHook_wiringSourceInvariants`
   - Extracts the `HideIconsSignalHook` function body from `SystemUIStatusBarHooks.kt`.
   - Asserts the resolver is called exactly once with `lpparam.classLoader`.
   - Asserts the Effect is constructed exactly once as a local val.
   - Asserts `effect.before(param)` is present once inside this hook body.
   - Asserts `hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", lpparam.classLoader, "applyMobileState", stateHook)` is present exactly once.
   - Asserts the matching `updateState` `hookAllMethods` call is present exactly once.
2. `processFast_containsNoXposedHelpersFieldAccess` — extracts the `processFast` body and asserts it does not contain `XposedHelpers.getObjectField`, `getBooleanField`, `setObjectField`, `findField(`, or `findFieldIfExists`.
3. `noMutableProcessGlobalEffectField` — reflects over `SystemUIStatusBarHooks` and asserts it has no `StatusBarIconVisibilityEffect` field.

All structural invariants pass with `LOCAL_EXECUTION_EVIDENCE_ONLY`.

---

## STOP marker

C4-B2 is a consolidation / final code gate. No C4-B1 production re-design, no C5 target selection, and no C1/C2/C3 changes are performed.

C4_B2_FACTUAL_CORRECTIVE_READY_FOR_INDEPENDENT_AUDIT
