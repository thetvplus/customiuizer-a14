# C4-A0 — HideIconsSignal Architecture C Preflight

**Repository:** `tomthenpc/customiuizer-a14`  
**Branch:** `devin/a14-architecture-c-r14.20.0`  
**Base / Freeze SHA:** `e559df4d3381a8627641072eeed8f4dec1036aee`  
**Scope:** `SystemUIStatusBarHooks.HideIconsSignalHook` / `StatusBarMobileView.applyMobileState` / `StatusBarMobileView.updateState`  
**Type:** docs-only A0 preflight — no production, no test, no Resolver/ABI/Effect classes created.

---

## 0. START GATE

| Check | Result |
|---|---|
| Current branch | `devin/a14-architecture-c-r14.20.0` |
| Local HEAD | `e559df4d3381a8627641072eeed8f4dec1036aee` |
| Remote HEAD | `e559df4d3381a8627641072eeed8f4dec1036aee` |
| Merge-base against `e559df4d...` | `e559df4d3381a8627641072eeed8f4dec1036aee` (HEAD itself) |
| Worktree | clean (`git status --short` = empty) |
| C1/C2/C3 production changed | `false` |
| C4 production started | `false` (no `StatusBarIconVisibilityResolver` / `Abi` / `Effect`; no `mods/statusbariconvisibility/` package) |

START PASS.

---

## 1. EXACT HOOK SURFACE

### 1.1 Current installation

`HideIconsSignalHook` uses `ModuleHelper.hookAllMethods` for both target methods:

- `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt:2530-2565`

```kotlin
ModuleHelper.hookAllMethods(
    "com.android.systemui.statusbar.StatusBarMobileView",
    lpparam.classLoader,
    "applyMobileState",
    stateHook
)
ModuleHelper.hookAllMethods(
    "com.android.systemui.statusbar.StatusBarMobileView",
    lpparam.classLoader,
    "updateState",
    stateHook
)
```

`ModuleHelper.hookAllMethods(String, ClassLoader?, String, MethodHook)` delegates to `XposedHelpers.hookAllMethods(Class<?>, String, MethodHook)`, which uses `hookClass.getDeclaredMethods()` and matches by method name only:

- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt:301-316`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java:894-900`

### 1.2 Frozen hook-surface contract

- `hookAllMethods` only considers methods **declared in the named class**. Inherited methods with the same name are **not** hooked by this call.
- All declared overloads named `applyMobileState` are hooked with the same `stateHook` callback.
- All declared overloads named `updateState` are hooked with the same `stateHook` callback.
- The single `before` callback assumes **arg0 exists** and is the `mobileIconState` object.
- `ZERO_ARG_OVERLOAD_GETARG0_BEHAVIOR = NOT_PROVEN`. The repository only proves that `BeforeHookCallback.getArg(0)` delegates to `chain.getArg(0)`; the real libxposed behavior for an invalid or missing argument index is not proven here.

### 1.3 Overload evidence

| Item | Evidence |
|---|---|
| `REAL_METHOD_OVERLOAD_SET` | **NOT_PROVEN**. `StatusBarIconVisibilityHotPathTest` calls `HideIconsSignalHook(fakePackageReadyParam())` in `setUp`, but the fake `PackageReadyParam` uses the system classloader, so `StatusBarMobileView` class resolution fails and no real methods are hooked. The test does not verify overload count or signatures. |

---

## 2. EXACT LEGACY CALLBACK ORACLE

Source: `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt:2530-2565`.

### 2.1 Frozen execution order

```text
1. mobileIconState = arg0

2. shouldUpdate = (member.name == "updateState")

3. if !shouldUpdate:
       mState = thisObject.mState  (XposedHelpers.getObjectField)
       shouldUpdate = (mState == null)

4. if !shouldUpdate:
       return  // adapter will call chain.proceed() with original args

5. snapshot = currentOrBuildStatusBarIconVisibilitySnapshot()

6. wifiAvailable = mobileIconState.wifiAvailable  (getBooleanField)

7. subId = mobileIconState.subId  (getObjectField, cast to Int)

8. dataSubId = SubscriptionManager.getActiveDataSubscriptionId()

9. slotId = SubscriptionManager.getSlotIndex(subId)

10. result = computeSignalIconHiding(wifiAvailable, subId, dataSubId, slotId, snapshot)

11. if result.visible == false:
        mobileIconState.visible = false
        return

12. if result.roaming != null:
        mobileIconState.roaming = false

13. if result.volte != null:
        mobileIconState.volte = false
        mobileIconState.speechHd = false
```

### 2.2 Frozen ordering rules

| Rule | Contract |
|---|---|
| `visible=false` early return | If `result.visible == false`, write `visible` and **immediately return**; no `roaming`/`volte`/`speechHd` writes. |
| `roaming` before `volte` | `roaming` is written before `volte` and `speechHd`. |
| `speechHd` only with `volte` | `speechHd` is written **only** when `result.volte != null`. |
| `applyMobileState` eligibility | For `applyMobileState`, the callback reads `thisObject.mState`. Mutation happens **only if `mState == null`**. If `mState` is non-null, the callback returns before any field writes. |
| `updateState` eligibility | For `updateState`, `shouldUpdate` is `true` from the start; the callback always mutates (subject to the visibility short-circuit). |
| Snapshot read timing | Snapshot is read **after** the eligibility (`shouldUpdate`) check. If the callback returns early, no snapshot is built/retrieved. |
| Chain proceed | `MethodHook` adapter calls `chain.proceed()` after `before` returns unless `returnAndSkip` or `throwAndSkip` is used. `HideIconsSignalHook` uses neither. |

### 2.3 Data-flow contract

- `mobileIconState` is **not** replaced; only its primitive fields are written.
- The original SystemUI method sees the mutated values after `chain.proceed()`.
- No new `MobileIconState` instance is allocated or substituted by the hook.

---

## 3. LEGACY FIELD ABI

`XposedHelpers.findField` starts at the **runtime class** of the object and recurses upward, so subclass-declared fields take precedence over superclass fields.

### 3.1 `StatusBarMobileView.mState` — READ

| Attribute | Contract / Evidence |
|---|---|
| Access site | `SystemUIStatusBarHooks.kt:2538` |
| Lookup starting runtime class | `thisObject.javaClass` |
| Declaring owner | `REAL_HYPEROS_FIELD_OWNER = NOT_PROVEN` |
| Expected type | `Object` (current mobile-state instance, same type as arg0, or `null`) |
| Access semantics | Object read; compared with `null` |
| Null behavior | `mState == null` → `shouldUpdate = true`; `mState != null` → `shouldUpdate = false`, return early |
| Missing-field behavior | `NoSuchFieldError` → caught by `MethodHook.beforeHook`, no mutation |

### 3.2 `MobileIconState.wifiAvailable` — READ

| Attribute | Contract / Evidence |
|---|---|
| Access site | `SystemUIStatusBarHooks.kt:2544` |
| Lookup starting runtime class | `mobileIconState.javaClass` |
| Declaring owner | `REAL_HYPEROS_FIELD_OWNER = NOT_PROVEN` |
| `REAL_HYPEROS_wifiAvailable_FIELD_TYPE` | **NOT_PROVEN**. The repository does not prove whether the actual HyperOS field is primitive `boolean` or `Boolean` object. |
| Legacy access | `XposedHelpers.getBooleanField(mobileIconState, "wifiAvailable")` → `Field.getBoolean(obj)`. |
| FAST resolver requirement | `wifiAvailableField.type === Boolean.TYPE`. If `wifiAvailableField.type !== Boolean.TYPE` (including `Boolean.class`), the `Resolver` must treat this as an **expected miss**, the ABI is unavailable, and the callback must use the complete legacy `XposedHelpers.getBooleanField` path. |
| Expected frozen fast-path semantics | **primitive `boolean` `Field.getBoolean(obj)` semantics only**. `java.lang.Boolean` wrapper fields are **not** declared FAST-compatible. |
| Null behavior | `mobileIconState == null` causes NPE in `findField`; caught by `MethodHook.beforeHook`. |
| Wrong-type behavior (legacy) | `IllegalArgumentException` if the legacy `Field.getBoolean` cannot read the actual field; caught by `MethodHook.beforeHook`. |
| Wrong-type behavior (FAST) | Must not occur if the `Resolver` enforces `Boolean.TYPE`. If it does, the runtime `IllegalArgumentException` is non-fatal and handled by `MethodHook.beforeHook`; do not fall back after fast execution begins. |
| Missing-field behavior | `NoSuchFieldError`; caught, no mutation. |

### 3.3 `MobileIconState.subId` — READ

| Attribute | Contract / Evidence |
|---|---|
| Access site | `SystemUIStatusBarHooks.kt:2545` |
| Lookup starting runtime class | `mobileIconState.javaClass` |
| Declaring owner | `REAL_HYPEROS_FIELD_OWNER = NOT_PROVEN` |
| Expected type | `int` (autoboxed to `Integer` by `Field.get`) or `Integer` |
| Access semantics | `getObjectField` + `as Int` |
| Wrong-type behavior | `ClassCastException` from `as Int` if value is not `Integer`; caught |
| Missing-field behavior | `NoSuchFieldError`; caught |

### 3.4 `MobileIconState.visible` / `roaming` / `volte` / `speechHd` — WRITE

| Attribute | Contract / Evidence |
|---|---|
| Access sites | `SystemUIStatusBarHooks.kt:2551, 2555, 2558, 2559` |
| Lookup starting runtime class | `mobileIconState.javaClass` |
| Declaring owner | `REAL_HYPEROS_FIELD_OWNER = NOT_PROVEN` for each |
| Expected type | `boolean` or `Boolean` |
| Access semantics | `XposedHelpers.setObjectField(obj, name, Boolean.FALSE)`; `Boolean.valueOf(false)` returns `Boolean.FALSE` singleton (no allocation) |
| Partial-mutation | If a write succeeds and a later write throws, earlier writes remain. |
| Missing-field / wrong-type | `NoSuchFieldError` / `IllegalArgumentException`; caught |

### 3.5 Field ABI summary

| Field | RW | Expected type |
|---|---|---|
| `StatusBarMobileView.mState` | R | `Object` (state or `null`) |
| `MobileIconState.wifiAvailable` | R | primitive `boolean` only; FAST requires `Field.type === Boolean.TYPE` (`REAL_HYPEROS_wifiAvailable_FIELD_TYPE = NOT_PROVEN`) |
| `MobileIconState.subId` | R | `int` / `Integer` |
| `MobileIconState.visible` | W | `boolean` / `Boolean` |
| `MobileIconState.roaming` | W | `boolean` / `Boolean` |
| `MobileIconState.volte` | W | `boolean` / `Boolean` |
| `MobileIconState.speechHd` | W | `boolean` / `Boolean` |

---

## 4. SUBCLASS / SHADOWING CONTRACT

### 4.1 Legacy lookup

`XposedHelpers.findFieldRecursiveImpl` starts at `obj.getClass()` and walks upward:

- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java:556-572`

This means a subclass-declared field hides a same-named superclass field for the same instance.

### 4.2 Fast-path eligibility

The future `Resolver` resolves two independent **resolution roots**:

```text
STATUS_BAR_MOBILE_VIEW_RESOLUTION_ROOT = the Class<?> used to resolve mState
                                         and the applyMobileState / updateState Method(s)

MOBILE_ICON_STATE_RESOLUTION_ROOT      = the Class<?> used to resolve wifiAvailable,
                                         subId, visible, roaming, volte, speechHd
```

```text
FAST_PATH_ELIGIBILITY = exact runtime class match against the resolution root

For the StatusBarMobileView receiver:
    thisObject.javaClass === abi.statusBarMobileViewResolutionRootClass

For the mobileIconState receiver:
    mobileIconState.javaClass === abi.mobileIconStateResolutionRootClass
```

A resolved `Field` may legitimately be declared in a superclass of its resolution root (e.g., an inherited `mState` or `wifiAvailable` field). The `Resolver` may freeze such a `Field` even when:

```text
field.declaringClass !== resolutionRootClass
```

```text
LEGACY_FALLBACK_REQUIRED = true
```

Frozen behavior-preservation contract:

- **Exact runtime class == resolution root**: FAST allowed, including for fields inherited from a superclass.
- **Any runtime class != resolution root** (strict subclass, superclass, unrelated, or any other mismatch): complete legacy `XposedHelpers` fallback. Subclass field shadowing is the most important reason, but every mismatch must fall back.
- **Subclass field shadowing**: preserved by the fallback, because legacy `XposedHelpers.findField` starts at `obj.getClass()` and sees the shadowing field first.
- **Inherited field on exact resolution root**: FAST allowed, because the runtime class is exactly the root and the resolved `Field` points to the only matching field in the hierarchy.

### 4.3 Behavior-preservation proof

Legacy `XposedHelpers.findField(obj.javaClass, name)` returns the first declared field named `name` encountered when walking from `obj.javaClass` upward to `Object`. This gives the most-derived (subclass) field declaration precedence.

The `Resolver` resolves the same hierarchy starting from the **resolution root class** `R`. It returns the first matching `Field` found when walking from `R` upward; call this `F`. `F` may be declared in `R` itself or in a superclass of `R`.

**Case 1 — runtime class equals resolution root (`obj.javaClass === R`):**
There is no subclass below `R` that could shadow the field. `F` is the only matching field in the hierarchy starting at `R`. Using `F` directly on `obj` reads/writes the same field as the legacy lookup. FAST is safe.

**Case 2 — runtime class is a strict subclass of `R` (`S extends R`, `obj.javaClass === S`):**
A strict subclass may declare a field with the same name, shadowing the field found by the `Resolver`. Legacy `findField` would return the subclass field, while `F` (resolved from `R`) would return the superclass field. Therefore the FAST path must not be used; the `Effect` must fall back to the complete legacy `XposedHelpers` path, which preserves subclass shadowing.

**Case 3 — runtime class is not equal to the resolution root (`obj.javaClass !== R`):**
This covers a superclass of `R`, an unrelated class, a strict subclass of `R`, or any other mismatch. If the resolution root was chosen correctly, only `obj.javaClass === R` can happen for the hook target / parameter type, but the `Effect` must enforce the rule universally. Any mismatch means the frozen `Field` may resolve a different field in the hierarchy than the one the legacy runtime-class-first lookup would find. Therefore the FAST path must not be used; the `Effect` must fall back to the complete legacy `XposedHelpers` path before any fast operation begins.

```text
BEFORE_FAST_RUNTIME_CLASS_MISMATCH = LEGACY_FALLBACK
```

**Conclusion:** `FAST_PATH_ELIGIBILITY` is sound and behavior-preserving when defined as exact runtime-class equality with the resolution root. It does not require `obj.javaClass === field.declaringClass`; it allows inherited fields on the exact resolution root while correctly falling back for any runtime class that does not equal the resolution root.

---

## 5. FAILURE / FATAL CONTRACT

### 5.1 Outer `MethodHook.beforeHook` contract

- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/HookerClassHelper.kt:203-215`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/FatalErrors.kt:14-28`

Ordinary `Throwable` (including `Error` subtypes that are not `OutOfMemoryError`, `ThreadDeath`, or `VirtualMachineError`) is logged and swallowed; the adapter calls `chain.proceed()`. Fatal errors are rethrown immediately.

### 5.2 Fast-execute fallback boundary

The legacy path does **not** retry a different field-access path after a field access has already failed. The future Architecture C `Effect` must mirror this distinction.

#### 5.2.1 `FALLBACK_ALLOWED_BEFORE_FAST_EXECUTE`

The following conditions may cause the callback to select the **complete existing legacy `XposedHelpers` path**, as long as the decision is made before any fast `Field.get` / `Field.getBoolean` / `Field.set` / `as Int` operation begins:

- Resolver expected miss (class or field not resolvable at install time).
- Resolver ordinary non-fatal `Throwable` during install-time resolution.
- Receiver runtime class does not satisfy `FAST_PATH_ELIGIBILITY` (e.g., `thisObject.javaClass !== abi.statusBarMobileViewResolutionRootClass` or `mobileIconState.javaClass !== abi.mobileIconStateResolutionRootClass`).
- Any ABI incompatibility detected before the first fast field operation, including a `wifiAvailable` `Field` whose `type !== Boolean.TYPE`.

For these cases the `Effect` may use the complete legacy `XposedHelpers` path and the legacy `MethodHook.beforeHook` contract remains responsible for ordinary failures.

#### 5.2.2 `FALLBACK_FORBIDDEN_AFTER_FAST_EXECUTE_BEGINS`

Once the `Effect` has committed to the fast path and performed at least one fast operation, the following runtime failures must **not** be retried through `XposedHelpers`:

- direct `Field.get` `IllegalAccessException`
- direct `Field.getBoolean` `IllegalAccessException`
- direct `Field.set` `IllegalAccessException`
- direct `Field.get` / `Field.getBoolean` / `Field.set` `IllegalArgumentException`
- `subId as Int` `ClassCastException`
- any ordinary field-access or cast failure after one or more fast operations have started

For these failures the `Effect` must:

- **not** retry with `XposedHelpers.getObjectField` / `setObjectField` / `getBooleanField`;
- **not** continue later mutations;
- **preserve** any earlier successful partial mutations;
- **allow the failure to terminate this `before` callback**;
- let the existing `MethodHook.beforeHook` outer contract log the non-fatal exception and continue the original callback.

### 5.3 IllegalAccessException legacy-compatible mapping

`XposedHelpers.getObjectField` / `setObjectField` catch `IllegalAccessException`, log it, and rethrow `IllegalAccessError`. `IllegalAccessError` is an `Error` but is **not** one of the fatal `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError` categories, so `MethodHook.beforeHook` catches it, logs it, and the original callback proceeds.

This exact mapping must be preserved for any fast `Field.get` / `Field.getBoolean` / `Field.set` that throws `IllegalAccessException`:

```text
direct Field.get / Field.getBoolean / Field.set IllegalAccessException
    → wrap as IllegalAccessError
    → throw
    → MethodHook.beforeHook catches (non-fatal Error)
    → XposedHelpers.log(...)
    → original callback proceeds
```

The `Field` objects resolved by the future `Resolver` must be `setAccessible(true)` at resolve time, which is the same safety net used by `XposedHelpers.findField`. If `IllegalAccessException` still occurs at runtime, the mapping above must still be applied.

### 5.4 Per-scenario contract

| Scenario | Fallback timing | Legacy behavior | Architecture C contract |
|---|---|---|---|
| Resolver expected miss (class/field not found at install) | **Before fast** | N/A | Return `null` ABI. `Effect` uses complete legacy `XposedHelpers` path. |
| Resolver unexpected ordinary `Throwable` | **Before fast** | N/A | Log once, return `null` ABI, `Effect` uses complete legacy path. |
| Receiver runtime class not eligible | **Before fast** | Legacy uses runtime-class-first lookup. | Use complete legacy `XposedHelpers` path. No fast operation begins. |
| `mState` missing / incompatible / `IllegalAccessException` / `IllegalArgumentException` | Before fast if Resolver detects; **forbidden after fast** | `NoSuchFieldError` / `IllegalArgumentException` caught; original proceeds. | If detected before fast, use legacy. If fast `Field.get` fails, do not retry, do not continue. No partial mutations to preserve for this read. |
| `wifiAvailable` `Field.type !== Boolean.TYPE` (including `Boolean.class`) | **Before fast** | `IllegalArgumentException` from `Field.getBoolean` caught. | `Resolver` must treat this as an expected **miss** and force the complete legacy `XposedHelpers.getBooleanField` path. FAST `Field.getBoolean` must never be invoked on a non-primitive `boolean` field. |
| `subId as Int` `ClassCastException` | **Forbidden after fast** | Caught; no mutation. | Use `Field.get(mobileIconState) as Int` to preserve `ClassCastException` semantics. Do not continue to `SubscriptionManager` calls or writes. No prior mutations. |
| `visible` / `roaming` / `volte` / `speechHd` write missing or `IllegalArgumentException` | **Forbidden after fast** | `NoSuchFieldError` / `IllegalArgumentException` caught; earlier successful writes remain. | Fast `Field.set` fails; do not retry, do not continue to later writes. Preserve any earlier successful partial mutations. |
| Fast `Field.get` / `Field.getBoolean` / `Field.set` `IllegalAccessException` | **Forbidden after fast** | `XposedHelpers` maps to `IllegalAccessError` (non-fatal `Error`). | Map `IllegalAccessException` to `IllegalAccessError` and throw; `MethodHook.beforeHook` logs and original proceeds. |
| Fatal `Throwable` (`OutOfMemoryError`, `ThreadDeath`, `VirtualMachineError`) | Any | Rethrown immediately. | Never catch; propagate. Never enter fallback. |

### 5.5 Partial-mutation contract

The legacy callback may leave `visible`, `roaming`, or `volte` mutated if a later write throws. The Architecture C `Effect` must not wrap writes in a transaction that rolls back earlier writes. Each write is an independent, non-atomic step, and a failure after fast execution begins must leave earlier successful writes intact.

---

## 6. CONFIG / PUBLICATION

The existing B3 snapshot/publication mechanism is reused.

### 6.1 Artifacts

- `StatusBarIconVisibilitySnapshot`: `SystemUIStatusBarHooks.kt:136-166`
- `StatusBarIconVisibilityRuntimeState`: `SystemUIStatusBarHooks.kt:262-310`
- `buildStatusBarIconVisibilitySnapshot` / `currentOrBuildStatusBarIconVisibilitySnapshot`: `SystemUIStatusBarHooks.kt:1974-2017`

### 6.2 Frozen contract

- **Relevant keys**: the 28 strings in `StatusBarIconVisibilityRuntimeState.relevantKeys`.
- **Rebuild behavior**: `observer.onChange(key)` returns if `key != null && key !in relevantKeys`; otherwise builds a new snapshot and `AtomicReference.set`s it.
- **Null-key behavior**: `null` triggers a full rebuild.
- **First snapshot**: lazy, on first `currentOrBuildStatusBarIconVisibilitySnapshot()` call.
- **Publication**: immutable `StatusBarIconVisibilitySnapshot` published through `AtomicReference<StatusBarIconVisibilitySnapshot?>`.
- **Invalidation**: in-flight callbacks continue with the snapshot they retrieved; only new callbacks see the updated snapshot.

### 6.3 Evidence

| Item | Evidence |
|---|---|
| `StatusBarIconVisibilitySnapshot` build | **RUNTIME_TESTED_COMPONENT** (`StatusBarIconVisibilityHotPathTest`) |
| `computeSignalIconHiding` | **RUNTIME_TESTED_COMPONENT** (`StatusBarIconVisibilityHotPathTest`) |
| `REAL preference observer auto-callback` | **NOT_RUNTIME_TESTED_CALLBACK** |

---

## 7. OWNERSHIP / LIFECYCLE

- `StatusBarIconVisibilityRuntimeState` is a process-scoped singleton holder.
- The preference observer is registered with the `created` runtime-state instance as owner: `SystemUIStatusBarHooks.kt:330-336`.
- The observer must not hold a `View`, `Context`, `Activity`, or short-lived controller.
- The future `Effect` should receive an **immutable captured reference** to the snapshot source, not create a new mutable process-global owner.
- `StatusBarMobileView` and `MobileIconState` instances are callback-local and must not be stored in static or long-lived effect state.

---

## 8. SUBSCRIPTION MANAGER — OUT OF SCOPE

The two calls remain unchanged and out of scope:

- `SystemUIStatusBarHooks.kt:2546-2547`

```kotlin
val dataSubId = SubscriptionManager.getActiveDataSubscriptionId()
val slotId = SubscriptionManager.getSlotIndex(subId)
```

- Do not cache.
- Do not resolve at install.
- Do not move thread.
- Do not reorder.

| Item | Evidence |
|---|---|
| `SUBSCRIPTION_MANAGER_INTERNAL_COST` | **NOT_PROVEN**. Whether these calls perform synchronous Binder IPC depends on the runtime and cannot be proven from the repository. |

---

## 9. TEST / EVIDENCE MATRIX

| Item | Evidence |
|---|---|
| `XposedHelpers` field-cache / additional-instance-field cost model | **STRUCTURAL** (`XposedHelpers.java:70-96`, `159-245`, `515-538`, `1360-1379`, `2010-2020`) |
| `MethodHook.beforeHook` failure / fatal contract | **STRUCTURAL** (`HookerClassHelper.kt:203-215`, `FatalErrors.kt:14-28`) |
| `ModuleHelper.hookAllMethods` → `XposedHelpers.hookAllMethods` | **STRUCTURAL** (`ModuleHelper.kt:301-316`, `XposedHelpers.java:894-900`) |
| `StatusBarIconVisibilitySnapshot` build & keys | **RUNTIME_TESTED_COMPONENT** |
| `computeSignalIconHiding` | **RUNTIME_TESTED_COMPONENT** |
| `StatusBarIconVisibilityRuntimeState` ownership | **STRUCTURAL** |
| `REAL_METHOD_OVERLOAD_SET` | **NOT_PROVEN** |
| `ZERO_ARG_OVERLOAD_GETARG0_BEHAVIOR` | **NOT_PROVEN** |
| `REAL_applyMobileState callback` | **NOT_RUNTIME_TESTED_CALLBACK** |
| `REAL_updateState callback` | **NOT_RUNTIME_TESTED_CALLBACK** |
| `REAL_HYPEROS_FIELD_OWNER` | **NOT_PROVEN** |
| `REAL_HYPEROS_FIELD_TYPE` | **NOT_PROVEN** |
| `CALLBACK_THREAD` | **NOT_PROVEN** |
| `REAL_CALLBACK_FREQUENCY` | **NOT_PROVEN** |
| `SUBSCRIPTION_MANAGER_INTERNAL_COST` | **NOT_PROVEN** |

---

## 10. SCOPE FREEZE

### 10.1 IN SCOPE

- `HideIconsSignalHook` callback oracle.
- `StatusBarMobileView.applyMobileState` hook.
- `StatusBarMobileView.updateState` hook.
- Fields: `mState`, `wifiAvailable`, `subId`, `visible`, `roaming`, `volte`, `speechHd`.
- Existing `StatusBarIconVisibilitySnapshot` and `StatusBarIconVisibilityRuntimeState`.
- Existing `computeSignalIconHiding` / `SignalIconHidingResult`.

### 10.2 OUT OF SCOPE

- `HideIconsHook` (`StatusBarIconControllerImpl.setIconVisibility`).
- `HideIconsFromSystemManager` (`CommandQueue.setIcon`).
- `DualRowSignalHook`, `MobileType`, and other signal hooks.
- Drawer blur (`doFrame`, `applyBlur`, `ControlPanelWindowManager.setBlurRatio`).
- `DetailedNetSpeed` / `NetSpeedStyle`.
- `SubscriptionManager` semantics or implementation.
- Preference key semantics changes.
- Forced icon refresh.
- C1, C2, C3 production and tests.

---

## 11. A0 OUTCOME

The preflight contract can be fully frozen:

- Hook surface, callback oracle, field ABI, and failure semantics are documented.
- A behavior-preserving fast-path contract (`exact runtime class match against the resolution root` + mandatory legacy fallback) is defined and justified.
- The fast-execute fallback boundary is frozen: fallback is allowed only before any fast `Field.get` / `Field.getBoolean` / `Field.set` / `as Int` operation; after fast execution begins, runtime field failures terminate the callback, preserve earlier partial mutations, and rely on `MethodHook.beforeHook` for log-and-continue.
- `IllegalAccessException` is mapped to `IllegalAccessError` exactly as the legacy `XposedHelpers` path does.
- `wifiAvailable` access semantics are frozen as primitive `boolean` `Field.getBoolean` semantics with the `Resolver` enforcing `wifiAvailableField.type === Boolean.TYPE`; `java.lang.Boolean` wrapper fields are not FAST-compatible.
- `ZERO_ARG_OVERLOAD_GETARG0_BEHAVIOR = NOT_PROVEN` and `REAL_METHOD_OVERLOAD_SET = NOT_PROVEN` are frozen without inferring `null`/NPE behavior.
- Config/publication and ownership are already in a reusable frozen state.
- `SubscriptionManager` is explicitly out of scope.
- All real-device/runtime evidence gaps are labeled `NOT_PROVEN` or `NOT_RUNTIME_TESTED_CALLBACK`.

No production code, test code, Resolver, ABI, or Effect has been created or modified.

C4_A0_PREFLIGHT_READY_FOR_INDEPENDENT_AUDIT
