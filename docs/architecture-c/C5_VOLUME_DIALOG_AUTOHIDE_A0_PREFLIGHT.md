# C5-A0 — VolumeDialogAutohideDelay Architecture C Preflight

**Repository:** `tomthenpc/customiuizer-a14`  
**Branch:** `devin/a14-architecture-c-r14.20.0`  
**C5 target-selection freeze SHA:** `3c5cb8cca3cd08799097e534ffef2366a6504b59`  
**Scope:** `SystemUIControlCenterHooks.VolumeDialogAutohideDelayHook` → `com.android.systemui.miui.volume.MiuiVolumeDialogImpl.computeTimeoutH`  
**Type:** docs-only A0 preflight — no production, no test, no Resolver/ABI/Effect classes created.

---

## 0. START GATE

| Check | Result | Evidence |
|---|---|---|
| Current branch | `devin/a14-architecture-c-r14.20.0` | `git branch --show-current` |
| Local HEAD | `69eb63d01e8c6549523567789b0110308147f49e` | `git rev-parse HEAD` |
| Remote HEAD | `69eb63d01e8c6549523567789b0110308147f49e` | `git rev-parse origin/devin/a14-architecture-c-r14.20.0` |
| Merge-base against `4c27065a...` | `69eb63d01e8c6549523567789b0110308147f49e` | `git merge-base HEAD origin/devin/a14-architecture-c-r14.20.0` |
| Worktree | clean | `git status --short` empty |
| C1/C2/C3/C4 production changed | `false` | no modifications in those phases |
| C5 production started | `false` | no Resolver/ABI/Effect/Hook production files created |

START PASS.

---

## 1. EXACT LEGACY CALLBACK ORACLE

Source: `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt:121-145`.

```kotlin
fun VolumeDialogAutohideDelayHook(classLoader: ClassLoader) {
    ModuleHelper.findAndHookMethod("com.android.systemui.miui.volume.MiuiVolumeDialogImpl", classLoader, "computeTimeoutH", object : MethodHook() {
        override fun before(param: BeforeHookCallback) {
            val mHovering = XposedHelpers.getBooleanField(param.getThisObject(), "mHovering")
            if (mHovering) { param.returnAndSkip(16000); return }
            val mSafetyWarning = try {
                XposedHelpers.getObjectField(param.getThisObject(), "mIsSafetyShowing") as Boolean
            } catch (e: Throwable) {
                FatalErrors.rethrowIfFatal(e)
                XposedHelpers.getObjectField(param.getThisObject(), "mSafetyWarning") as Boolean
            }
            if (mSafetyWarning) {
                val opt = MainModule.mPrefs.getInt("system_volumedialogdelay_expanded", 0)
                param.returnAndSkip(if (opt > 0) opt else 5000)
                return
            }
            val mExpanded = XposedHelpers.getBooleanField(param.getThisObject(), "mExpanded")
            val opt = MainModule.mPrefs.getInt(if (mExpanded) "system_volumedialogdelay_expanded" else "system_volumedialogdelay_collapsed", 0)
            if (opt > 0) param.returnAndSkip(opt)
        }
    })
}
```

### 1.1 Frozen execution order

```text
1. thisObject = param.getThisObject()

2. mHovering = XposedHelpers.getBooleanField(thisObject, "mHovering")

3. if mHovering == true:
       param.returnAndSkip(16000)
       return

4. otherwise:
       try:
           mSafetyWarning = XposedHelpers.getObjectField(thisObject, "mIsSafetyShowing") as Boolean
       catch (e: Throwable):
           FatalErrors.rethrowIfFatal(e)
           mSafetyWarning = XposedHelpers.getObjectField(thisObject, "mSafetyWarning") as Boolean

5. if mSafetyWarning == true:
       opt = MainModule.mPrefs.getInt("system_volumedialogdelay_expanded", 0)
       param.returnAndSkip(opt > 0 ? opt : 5000)
       return

6. otherwise:
       mExpanded = XposedHelpers.getBooleanField(thisObject, "mExpanded")
       key = mExpanded ? "system_volumedialogdelay_expanded" : "system_volumedialogdelay_collapsed"
       opt = MainModule.mPrefs.getInt(key, 0)
       if opt > 0:
           param.returnAndSkip(opt)
           return

7. otherwise fall through — original computeTimeoutH executes.
```

### 1.2 Frozen ordering rules

| Rule | Contract |
|---|---|
| `mHovering` short-circuit | If `mHovering == true`, return `16000` immediately; no safety-warning field access and no preference read. |
| Safety-warning short-circuit | If `mSafetyWarning == true`, read only `system_volumedialogdelay_expanded`; do not read `mExpanded`. |
| Safety fallback | Any non-fatal `Throwable` from the first alias `mIsSafetyShowing` triggers the second alias `mSafetyWarning`. Fatal errors (`OutOfMemoryError`, `ThreadDeath`, `VirtualMachineError`) propagate. |
| `mExpanded` branch | Only reached when `mHovering == false` and `mSafetyWarning == false`. The expanded/collapsed key is a string branch inside a single `getInt` call. |
| `opt <= 0` fallthrough | If the selected preference value is `0` or missing, do not call `returnAndSkip`; let the original method run. |
| No ROM mutation | The callback never writes a ROM field, never calls a ROM method, never mutates `thisObject`. |

### 1.3 Data-flow contract

- `thisObject` is read once and never stored beyond the callback frame.
- `mHovering` and `mExpanded` are read through `XposedHelpers.getBooleanField` (primitive `boolean` semantics).
- The safety-warning value is read through `XposedHelpers.getObjectField(...) as Boolean` (object `Boolean` semantics, with `null`/wrong-type cast as ordinary failures).
- Preference values are read through `MainModule.mPrefs.getInt(key, 0)`.
- The `returnAndSkip` value is a Kotlin `Int`; its compatibility with the real ROM method return type is `NOT_PROVEN`.

---

## 2. METHODHOOK BOUNDARY SEMANTICS

Source: `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/HookerClassHelper.kt:23-84, 167-215`.

### 2.1 `BeforeHookCallback` state

```kotlin
class BeforeHookCallback internal constructor(private val chain: XposedInterface.Chain) {
    internal var skipped = false
    internal var result: Any? = null
    internal var throwable: Throwable? = null

    fun returnAndSkip(returnValue: Any?) {
        skipped = true
        result = returnValue
        throwable = null
    }

    fun throwAndSkip(throwable: Throwable) {
        skipped = true
        result = null
        this.throwable = throwable
    }
}
```

**Frozen contract:**

- `BeforeHookCallback.returnAndSkip(value)`:
  - `skipped = true`
  - `result = value`
  - `throwable = null`
- `BeforeHookCallback.throwAndSkip(throwable)`:
  - `skipped = true`
  - `result = null`
  - `throwable = throwable`
- `BeforeHookCallback.getThisObject()` is lazy and cached.

### 2.2 `MethodHook.intercept`

`MethodHook.intercept` (`HookerClassHelper.kt:167-201`):

1. Builds `BeforeHookCallback` from `XposedInterface.Chain`.
2. Calls `beforeHook(before)`.
3. If `before.skipped == false`:
   - calls `chain.proceed()` (or `chain.proceed(before.getArgs())` if args were materialized).
   - captures any `Throwable` from the original method as `throwable`.
4. If an `afterHook` is declared, runs it and may override `result`/`throwable`.
5. If `throwable != null`, throws it; otherwise returns `result`.

`returnAndSkip` therefore bypasses `chain.proceed()` and the value in `before.result` is returned to the libxposed chain.

### 2.3 `MethodHook.beforeHook` failure contract

`HookerClassHelper.kt:203-215`:

```kotlin
override fun beforeHook(callback: BeforeHookCallback) {
    try {
        before(callback)
    } catch (oom: OutOfMemoryError) {
        throw oom
    } catch (td: ThreadDeath) {
        throw td
    } catch (vme: VirtualMachineError) {
        throw vme
    } catch (t: Throwable) {
        XposedHelpers.log(t)
    }
}
```

**Frozen contract:**

| Throw category | MethodHook behavior | Result |
|---|---|---|
| `OutOfMemoryError` | rethrow | fatal, propagates out of `intercept` |
| `ThreadDeath` | rethrow | fatal, propagates |
| `VirtualMachineError` | rethrow | fatal, propagates |
| Any other `Throwable` | `XposedHelpers.log(t)`, swallow | `before.skipped` remains `false`; `MethodHook.intercept` then calls `chain.proceed()`; original method executes. |

Therefore:

- If the `before` callback throws an ordinary `Throwable` **before** `returnAndSkip`, the original `computeTimeoutH` proceeds.
- If the `before` callback calls `returnAndSkip` and then returns, the original method is skipped and the supplied `Int` is returned.
- Fatal errors must not be swallowed and must not trigger fallbacks.

---

## 3. REAL `computeTimeoutH` METHOD ABI

### 3.1 Repository-proven hook surface

`ModuleHelper.findAndHookMethod` (`ModuleHelper.kt:115-116`) delegates to `XposedHelpers.findAndHookMethod` (`XposedHelpers.java:601-608`), which calls `findMethodExact` (`XposedHelpers.java:689-706`):

```java
public static Method findMethodExact(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
    ...
    Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);
    methodCache.put(key, method);
    return method;
}
```

The `findMethodExact` cache key is `MemberCacheKey.Method(clazz, methodName, parameterTypes, true)`. It does **not** include the return type.

The production call is:

```kotlin
ModuleHelper.findAndHookMethod(
    "com.android.systemui.miui.volume.MiuiVolumeDialogImpl",
    classLoader,
    "computeTimeoutH",
    callback
)
```

`getParameterClasses` (`XposedHelpers.java:918-943`) receives only the `MethodHook` callback as the trailing argument; parameter count is `0`, and `EMPTY_CLASS_ARRAY` is used. Therefore the lookup is by class + name + zero parameter types.

### 3.2 Frozen method-surface contract

```text
HOOK_TARGET_CLASS     = com.android.systemui.miui.volume.MiuiVolumeDialogImpl
HOOK_TARGET_NAME      = computeTimeoutH
HOOK_TARGET_PARAMETER_SHAPE = zero explicit parameter types supplied to findAndHookMethod
LEGACY_HOOK_RESULT_VALUE    = module supplies Kotlin Int values through param.returnAndSkip(Int)
```

### 3.3 Unknowns

| Item | Contract |
|---|---|
| `REAL_COMPUTE_TIMEOUT_H_RETURN_TYPE` | `NOT_PROVEN`. The repository does not inspect the real ROM method descriptor. `findAndHookMethod` matches by name and parameter types only. |
| `REAL_COMPUTE_TIMEOUT_H_OVERLOAD_SET` | `NOT_PROVEN`. The repository only requests a method named `computeTimeoutH` with zero explicit parameter types. Whether multiple such methods exist in `MiuiVolumeDialogImpl` or its hierarchy cannot be determined from source. |

### 3.4 A0 decision: `DOES_B1_REQUIRE_RETURN_TYPE_KNOWLEDGE`

**Decision:** `NO`.

Justification:

- The module already calls `param.returnAndSkip(Int)` without any return-type check.
- The libxposed `Chain` accepts an `Any?` return value through `BeforeHookCallback.returnAndSkip`.
- The existing hook installation surface is `findAndHookMethod(class, name, callback)` with zero parameter types; adding a return-type assumption would introduce a new dependency not present in the legacy code.
- If the real method is absent or has an incompatible parameter signature, `findAndHookMethod` already fails at install time, and the hook is not installed.
- If the real method exists with zero parameters but an unexpected return type, the legacy callback would exhibit the same `returnAndSkip(Int)` behavior.

Constraint: B1 must not add any return-type check or method overload filtering. The resolver only needs to resolve the `Method` by the same surface (class + name + zero parameter types) for exact-root eligibility; it does not need to validate `Method.returnType`.

---

## 4. FIELD ABI — `mHovering` / `mExpanded`

### 4.1 Legacy field semantics

`XposedHelpers.getBooleanField` (`XposedHelpers.java:1385-1395`):

```java
public static boolean getBooleanField(Object obj, String fieldName) {
    try {
        return findField(obj.getClass(), fieldName).getBoolean(obj);
    } catch (IllegalAccessException e) {
        XposedHelpers.log(e);
        throw new IllegalAccessError(e.getMessage());
    } catch (IllegalArgumentException e) {
        throw e;
    }
}
```

`XposedHelpers.findField` starts at `obj.getClass()` and recurses upward (`XposedHelpers.java:556-572`), so subclass-declared fields take precedence.

### 4.2 FAST field contract

For `mHovering` and `mExpanded`:

| Attribute | Contract |
|---|---|
| Resolution start | `com.android.systemui.miui.volume.MiuiVolumeDialogImpl` at install time. |
| Runtime eligibility | `thisObject != null && thisObject.javaClass === VolumeDialogAutohideDelayAbi.resolutionRootClass`. |
| FAST type requirement | `field.type === Boolean.TYPE` (primitive `boolean`). Any `Boolean` (wrapper), `Object`, or other type is **not** FAST-compatible. |
| FAST access | `Field.getBoolean(thisObject)`. This is the exact JVM call the legacy path makes after `findField`. |
| Field shadowing | Exact-root check prevents subclasses from being FAST; legacy is used for any `thisObject.javaClass` that is not the resolution root. |
| Inheritance | A primitive `boolean` field declared in a superclass of `MiuiVolumeDialogImpl` is allowed on the resolution root, because the exact-root check makes the hierarchy position unambiguous. |
| `IllegalAccessException` | Wrap as `IllegalAccessError` (non-fatal `Error`, same as legacy `XposedHelpers.getBooleanField`), which `MethodHook.beforeHook` logs and allows the original method to proceed. |
| `IllegalArgumentException` | Must not occur if the resolver enforced `Boolean.TYPE`; if it does, the `Effect` must not retry through `XposedHelpers`; let `MethodHook.beforeHook` handle it. |

### 4.3 Resolver mismatch contract

If the resolver cannot find a field, finds a non-primitive `boolean` field, or any other incompatibility for `mHovering` or `mExpanded`:

- `Resolver` returns `null` ABI.
- `Effect` uses the complete legacy `XposedHelpers.getBooleanField` path.
- No partial FAST operation is attempted.

---

## 5. SAFETY ALIAS — PRIMARY A0 BLOCKER

### 5.1 Current legacy contract

```kotlin
val mSafetyWarning = try {
    XposedHelpers.getObjectField(thisObject, "mIsSafetyShowing") as Boolean
} catch (e: Throwable) {
    FatalErrors.rethrowIfFatal(e)
    XposedHelpers.getObjectField(thisObject, "mSafetyWarning") as Boolean
}
```

The fallback to `mSafetyWarning` is triggered by **any non-fatal `Throwable`** from the first alias, including but not limited to:

- `NoSuchFieldError` from missing field
- `IllegalAccessError` from `IllegalAccessException` wrapper
- `IllegalArgumentException` from `Field.get`
- `ClassCastException` from `as Boolean` on a wrong-type or `null` value
- `NullPointerException` from `null as Boolean`
- any other non-fatal `Throwable`

Fatal errors (`OutOfMemoryError`, `ThreadDeath`, `VirtualMachineError`) are rethrown by `FatalErrors.rethrowIfFatal` and must **not** fall through to the second alias.

### 5.2 Strategy comparison

| Strategy | Description |
|---|---|
| **A — Keep safety alias legacy** | FAST only for `mHovering` and `mExpanded`. The safety-warning read remains the exact existing `try/catch` `XposedHelpers.getObjectField` block. |
| **B — Frozen safety alias pair** | Resolver freezes both `mIsSafetyShowing` and `mSafetyWarning` `Field`s; `Effect` reproduces the ordinary-failure → second-alias contract using direct `Field.get` calls. |
| **C — Hybrid** | Some operations frozen, others fall back to legacy; exact split must be specified. |

### 5.3 A0 strategy evaluation

| Criterion | A | B | C |
|---|---|---|---|
| Correctness | Highest. The exact legacy code path is preserved. | Medium. Must prove equivalence for every ordinary failure mode. | Depends on exact split. |
| Failure-semantic compatibility | Highest. No emulation. | Low–Medium. Emulating `Field.get` failure + fallback for `IllegalArgumentException`, `ClassCastException`, `null`, missing field, and fatal propagation is complex and fragile. | Depends on split. |
| Lifecycle | Same. No per-instance state. | Same if carefully limited. | Same. |
| Publication/concurrency | Same. | Same. | Same. |
| Hot-path lookup reduction | Lower for safety alias (1–2 `XposedHelpers` calls remain). | Potentially removes `XposedHelpers` cache lookup for safety alias if equivalence can be proven. | Partial. |

### 5.4 A0 strategy decision

**Selected strategy: A — Keep safety alias legacy.**

Reason: the priority for C5 is correctness and failure-semantic compatibility over hot-path lookup reduction. The ordinary-failure contract of the safety alias (`mIsSafetyShowing` → `mSafetyWarning`) cannot be cleanly emulated with frozen `Field` objects without either (a) retaining a `try/catch` on the hot path, which negates much of the gain, or (b) changing behavior for `null`, wrong-type, and `IllegalArgumentException` cases. A partially optimized, behavior-obvious path is preferable to a clever failure-emulation layer.

### 5.5 Legacy safety alias execution in the Architecture C chain

Under Strategy A:

- The `Resolver` does **not** resolve `mIsSafetyShowing` or `mSafetyWarning` for the FAST ABI.
- The `Effect` calls `XposedHelpers.getObjectField(thisObject, "mIsSafetyShowing") as Boolean` inside the exact same `try/catch(Throwable)` block as the legacy code.
- `FatalErrors.rethrowIfFatal` is called before attempting the fallback.
- This preserves the exact runtime-class-first lookup, `findField` cache behavior, and fatal/ordinary failure split.

FAST eligibility (`thisObject.javaClass === resolutionRootClass`) is still checked **before** any FAST `mHovering` read. If the class mismatches, the entire callback uses the complete legacy path, including the safety alias.

---

## 6. COMPLETE LEGACY FALLBACK BOUNDARY

### 6.1 Conditions requiring complete legacy fallback

The `Effect` must decide whether to use FAST or complete legacy **before** any FAST field access. The complete legacy path is required when:

- `VolumeDialogAutohideDelayAbi` is `null` (resolver returned null at install time).
- `thisObject == null`.
- `thisObject.javaClass !== VolumeDialogAutohideDelayAbi.resolutionRootClass`.
- `mHovering` field is missing or not primitive `boolean` (resolver already rejected, but re-check at runtime if needed).
- `mExpanded` field is missing or not primitive `boolean`.
- The `VolumeDialogAutohideDelaySnapshot` is `null` (unavailable or refresh failed).

### 6.2 No retry after FAST begins

Once the `Effect` has performed a FAST `Field.getBoolean(thisObject, mHovering)`:

- Do **not** retry `mHovering` through `XposedHelpers.getBooleanField`.
- Do **not** fall back to the legacy path for `mHovering` failures.
- An ordinary `IllegalAccessException` (mapped to `IllegalAccessError`) or `IllegalArgumentException` terminates the `before` callback; `MethodHook.beforeHook` logs and allows the original method to proceed.
- Fatal errors propagate immediately and must not be caught for fallback.

This is the same contract as C4: fallback is allowed **before** fast execution, never after.

### 6.3 Safety alias exception

Under Strategy A, the safety alias itself remains the complete legacy `XposedHelpers` block. Because the safety alias is **not** a FAST operation, it is not subject to the "no retry after FAST begins" rule. However, the safety alias block must be executed **after** the FAST `mHovering` read succeeds and only if `mHovering == false`.

If the safety alias block throws an ordinary `Throwable` and falls through to `mSafetyWarning`, and then the second alias also throws, the `MethodHook.beforeHook` outer contract swallows it and the original method proceeds.

---

## 7. SNAPSHOT / CONFIG PUBLICATION

### 7.1 Proposed snapshot

```kotlin
data class VolumeDialogAutohideDelaySnapshot(
    val expanded: Int,
    val collapsed: Int
)
```

### 7.2 Relevant preference keys

- `system_volumedialogdelay_expanded`
- `system_volumedialogdelay_collapsed`

### 7.3 Frozen publication and construction contract

| Attribute | Contract |
|---|---|
| Publication primitive | `SNAPSHOT_PUBLICATION = AtomicReference<VolumeDialogAutohideDelaySnapshot?>` with initial value `null`. No `@Volatile` alternative is left open. |
| Initial construction | At `VolumeDialogAutohideDelayRuntimeState` creation: register the process-scoped `PreferenceObserver`, then perform the initial refresh **outside** `computeTimeoutH`. `snapshotRef` starts as `null`; the first refresh sets it. The callback never triggers a lazy build. |
| Snapshot construction | Build exactly one `VolumeDialogAutohideDelaySnapshot` from **one** `PrefMap` generation: `val source = MainModule.mPrefs.getAll()`; `val expanded = source[EXPANDED_KEY] as? Int ?: 0`; `val collapsed = source[COLLAPSED_KEY] as? Int ?: 0`. Two independent `MainModule.mPrefs.getInt` calls are forbidden because each typed getter reads the current `PrefMap` snapshot independently and may observe different generations. |
| Refresh serialization | A single private `refreshLock` (e.g. `private val refreshLock = Any()`) is owned by `VolumeDialogAutohideDelayRuntimeState`. Both the initial refresh and `PreferenceObserver.onChange` synchronize on `refreshLock` while rebuilding and publishing. The `computeTimeoutH` callback never acquires `refreshLock`; it only calls `snapshotRef.get()`. |
| Refresh function | `refreshSnapshot()`: `try { source = MainModule.mPrefs.getAll(); snapshot = build from source; snapshotRef.set(snapshot) } catch (t: Throwable) { snapshotRef.set(null); FatalErrors.rethrowIfFatal(t); XposedHelpers.log(t) }`. The existing snapshot is cleared **before** an ordinary failed refresh is allowed to return. |
| Observer registration | Process-scoped `PreferenceObserver` registered through `ModuleHelper.observePreferenceChange(observer)` without a short-lived owner. |
| Relevant-key filter | `observer.onChange(key)` returns early if `key != null && key !in relevantKeys`; `null` triggers a full rebuild. |
| Key == null behavior | `null` key rebuilds the snapshot. |
| Thread independence | `CALLBACK_THREAD = NOT_PROVEN`; publication must be safe regardless of observer or callback thread. `AtomicReference.set`/`get` is safe. |
| Effect read | `val snapshot = snapshotRef.get()` once per callback. If `null`, execute `COMPLETE_LEGACY` before any FAST field access. |

### 7.4 Evidence

| Item | Classification | Source / note |
|---|---|---|
| `PrefMap.getAll` returns a single generation-consistent `Map` | `STRUCTURAL` | `PrefMap.kt:118-119` — reads `snapshot.get()` once and wraps it unmodifiable. |
| `PrefMap` typed getters each independently read `snapshot.get()` | `STRUCTURAL` | `PrefMap.kt:27-31`, `120-123` — `getValue` calls `currentSnapshot()` per typed getter. |
| `PrefMap.getInt` returns `defaultValue` on type mismatch | `STRUCTURAL` | `PrefMap.kt:120-123` — uses `value as? Int ?: defaultValue`. This does **not** prove `getInt` can never throw for other failure modes. |
| `MainModule.mPrefs` is a non-null `public static final PrefMap` | `STRUCTURAL` | `MainModule.java:47`. It is not a valid snapshot-build failure case. |
| `PrefMap` snapshot publication is `AtomicReference` | `STRUCTURAL` | `PrefMap.kt:25-48`. |
| `PreferenceObserverRegistry` is process-scoped and isolates observer failures | `STRUCTURAL` | `PreferenceObserverRegistry.kt:58-168`. |
| `PreferenceBootstrap.onPreferenceChanged` catches `Throwable` and logs | `STRUCTURAL` | `PreferenceBootstrap.kt:265-291`. |
| `PreferenceBootstrap` uses `ModuleHelper::handlePreferenceChanged` as `changeDispatcher` | `STRUCTURAL` | `PreferenceBootstrap.kt:33`, `79`, `287`. |
| Real preference observer callback execution | `NOT_RUNTIME_TESTED_CALLBACK` | no real callback timing/thread evidence. |

---

## 8. SNAPSHOT FAILURE SEMANTICS

### 8.1 Failure timing

Moving preference reads from the callback to a background/observer-time refresh changes when a failure is observed. If `refreshSnapshot()` throws, the failure must not produce a synthetic timeout or a stale snapshot.

### 8.2 Frozen snapshot-availability contract

```text
snapshot: VolumeDialogAutohideDelaySnapshot?

successful build  -> snapshot = non-null
ordinary failure  -> snapshot = null (unavailable)
fatal failure     -> clear snapshot, then propagate from C5 refresh boundary
```

The `refreshSnapshot()` contract:

```text
try:
    source = MainModule.mPrefs.getAll()
    snapshot = build from source
    snapshotRef.set(snapshot)
catch (t: Throwable):
    snapshotRef.set(null)
    FatalErrors.rethrowIfFatal(t)
    XposedHelpers.log(t)
```

The existing snapshot is cleared **before** the function returns on any `Throwable`, including a fatal. For an ordinary `Throwable`, the function returns after logging and setting `null`. For a fatal `Throwable`, `rethrowIfFatal` rethrows after `snapshotRef.set(null)`. The further propagation through `PreferenceObserverRegistry` and `PreferenceBootstrap` is documented in the nested boundary contract below.

### 8.3 Hot-path behavior

```text
thisObject = param.getThisObject()

if VolumeDialogAutohideDelayAbi == null:
    COMPLETE_LEGACY()

if thisObject == null:
    COMPLETE_LEGACY()

if thisObject.javaClass !== abi.resolutionRootClass:
    COMPLETE_LEGACY()

val snapshot = snapshotRef.get()
if snapshot == null:
    COMPLETE_LEGACY()

// FAST path begins
val mHovering = mHoveringField.getBoolean(thisObject)
...
```

`COMPLETE_LEGACY()` runs the exact original callback oracle. No preference read is attempted on the FAST path; the snapshot is the only source of the two `Int`s.

### 8.4 Nested fatal boundary contract

The call chain from a remote preference change to the C5 observer is:

```text
PreferenceBootstrap.onPreferenceChanged
    → changeDispatcher(canonicalKey)
    → ModuleHelper.handlePreferenceChanged
    → PreferenceObserverRegistry.handlePreferenceChanged
        → try: C5 PreferenceObserver.onChange
            → synchronized(refreshLock): refreshSnapshot()
        catch: rethrow OOM / ThreadDeath / VirtualMachineError / LinkageError
               log ordinary Throwable
        catch: OutOfMemoryError (registry top-level)
        catch: Throwable → rethrowFatalObserverError(t); XposedHelpers.log(t)
    catch: Throwable → XposedHelpers.log(t) (PreferenceBootstrap outer catch)
```

Frozen boundary statements:

| Boundary | Statement |
|---|---|
| `C5_REFRESH_FATAL` | The C5 `refreshSnapshot()` function clears `snapshotRef` and then rethrows `OutOfMemoryError`, `ThreadDeath`, or `VirtualMachineError` via `FatalErrors.rethrowIfFatal(t)`. This is the C5 refresh boundary. |
| `REGISTRY_FATAL` | `PreferenceObserverRegistry.rethrowFatalObserverError` rethrows `OutOfMemoryError`, `ThreadDeath`, `VirtualMachineError`, and `LinkageError`. (`PreferenceObserverRegistry.kt:113-128`) |
| `REMOTE_LISTENER_OUTER_BOUNDARY` | `PreferenceBootstrap.onPreferenceChanged` currently wraps its body, including `changeDispatcher(...)`, in `catch (t: Throwable) { XposedHelpers.log(t) }`. (`PreferenceBootstrap.kt:289-291`) |
| `REAL_END_TO_END_FATAL_ESCAPE` | `NOT_PROVEN`. The A0 document does not claim that a C5 refresh fatal necessarily escapes the entire remote preference listener stack, because `PreferenceBootstrap` may log it. |

### 8.5 Rationale

- `MainModule.mPrefs` is a `public static final PrefMap` (`MainModule.java:47`); it is not a valid ordinary failure case.
- `PrefMap.getInt` returns `defaultValue` on type mismatch (`PrefMap.kt:120-123`), but `refreshSnapshot()` still needs a general `Throwable` boundary because other runtime failures are possible.
- By clearing the snapshot before returning on an ordinary failure, a failed refresh cannot leave a stale non-null snapshot behind. The next callback sees `null` and runs the complete legacy oracle, which is behavior-compatible because the legacy oracle would have thrown and been swallowed if the preference read had failed at callback time.
- A callback racing with initial refresh may observe `snapshot == null` and run complete legacy. This is safe and does not require blocking `computeTimeoutH` waiting for initialization.

---

## 9. PREFERENCE OBSERVER EVIDENCE

Source: `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/PreferenceObserverRegistry.kt:28-168`.

### 9.1 Frozen observer contract

- `PreferenceObserver` interface: `fun onChange(key: String?)`.
- `PreferenceObserverRegistry` holds process-scoped observers in a `CopyOnWriteArraySet`.
- `handlePreferenceChanged(key)` fans the change to all observers.
- Observer failures are isolated: one observer's ordinary `Throwable` does not prevent others from running.
- Fatal errors and `LinkageError` are propagated; ordinary `Exception`s and non-fatal `Error`s are logged.
- The delivered `key` is the source-level short form (prefix `pref_key_` removed by `canonicalPreferenceKey`).

### 9.2 Ownership

The C5 observer must be process-lifetime only:

- No `View`.
- No `Context`.
- No `Activity`.
- No `MiuiVolumeDialogImpl` instance.

The observer is owned by the `VolumeDialogAutohideDelayRuntimeState` (a process-scoped singleton). It may be registered with `ModuleHelper.observePreferenceChange(observer)` without a short-lived owner, consistent with `SystemUIControlCenterHooks.kt:187` (`ModuleHelper.observePreferenceChange(volumeBlurPreferenceObserver)`).

### 9.3 Evidence

| Item | Classification | Source / note |
|---|---|---|
| `PreferenceObserver` interface and registry | `STRUCTURAL` | `PreferenceObserverRegistry.kt:28-34`. |
| `canonicalPreferenceKey` removes `pref_key_` prefix | `STRUCTURAL` | `PreferenceKeys.kt:14-18`. |
| `PreferenceObserverRegistry` rethrows OOM / ThreadDeath / VME / LinkageError | `STRUCTURAL` | `PreferenceObserverRegistry.kt:143-148`, `113-128`. |
| `PreferenceObserverRegistry` logs ordinary observer failures | `STRUCTURAL` | `PreferenceObserverRegistry.kt:145-148`, `162-165`. |
| `PreferenceBootstrap.onPreferenceChanged` wraps `changeDispatcher` in `catch (Throwable)` and logs | `STRUCTURAL` | `PreferenceBootstrap.kt:265-291`. |
| `PreferenceBootstrap` uses `ModuleHelper::handlePreferenceChanged` as the dispatcher | `STRUCTURAL` | `PreferenceBootstrap.kt:79`, `287`. |
| Real observer callback execution | `NOT_RUNTIME_TESTED_CALLBACK` | no real callback timing/thread evidence. |

---

## 10. PROPOSED ARCHITECTURE C CHAIN

```text
Cold Resolve
    → VolumeDialogAutohideDelayResolver.resolve(classLoader)
        → VolumeDialogAutohideDelayAbi?

Typed Config
    → VolumeDialogAutohideDelayRuntimeState
        → VolumeDialogAutohideDelaySnapshot?
        → AtomicReference<VolumeDialogAutohideDelaySnapshot?> (initial null)
        → private val refreshLock = Any()
        → PreferenceObserver
        → initial refresh outside computeTimeoutH

Immutable Effect
    → VolumeDialogAutohideDelayEffect(abi, snapshotRef)
        hook-local val

Thin Hook
    → VolumeDialogAutohideDelayHook(classLoader)
        → ModuleHelper.findAndHookMethod(
               "com.android.systemui.miui.volume.MiuiVolumeDialogImpl",
               classLoader,
               "computeTimeoutH",
               effect
           )

Mode Select (before any FAST field access)
    → abi != null
    → thisObject != null
    → thisObject.javaClass === abi.resolutionRootClass
    → snapshotRef.get() != null

Hot Execute
    → mHovering = mHoveringField.getBoolean(thisObject)
    → if mHovering: returnAndSkip(16000)
    → mSafetyWarning = LEGACY_SAFETY_ALIAS_READ(thisObject)
        (Strategy A: exact existing XposedHelpers try/catch block)
    → if mSafetyWarning:
        opt = snapshot.expanded
        returnAndSkip(opt > 0 ? opt : 5000)
    → mExpanded = mExpandedField.getBoolean(thisObject)
    → opt = if (mExpanded) snapshot.expanded else snapshot.collapsed
    → if opt > 0: returnAndSkip(opt)
    → otherwise fall through

Legacy
    → complete original callback oracle
```

No managers, coroutine scopes, `Flow`, per-view caches, runtime ABI maps, generation registries, or extra abstraction layers are introduced.

---

## 11. OWNERSHIP

| Artifact | Ownership | Content |
|---|---|---|
| `VolumeDialogAutohideDelayAbi` | Install-time immutable. | `resolutionRootClass: Class<*>`; `mHovering: Field`; `mExpanded: Field`; all set `accessible`. |
| `VolumeDialogAutohideDelaySnapshot` | Immutable published value. | `expanded: Int`; `collapsed: Int`. |
| `VolumeDialogAutohideDelayRuntimeState` | Process-scoped singleton. | `AtomicReference<VolumeDialogAutohideDelaySnapshot?>`; `private val refreshLock = Any()`; `PreferenceObserver`. No `View`/`Context`/`Activity`/`MiuiVolumeDialogImpl`. |
| `VolumeDialogAutohideDelayEffect` | Hook-local `val` captured by the installed `MethodHook`. | Holds `abi` and the `AtomicReference<VolumeDialogAutohideDelaySnapshot?>`. No per-instance mutable state. |
| `MiuiVolumeDialogImpl` instance | Callback-local only. | Never retained by `Effect`, `RuntimeState`, or `Abi`. |

No per-instance runtime cache is required. The `Effect` may be a single instance installed as the hook callback; it captures `abi` and the snapshot reference.

---

## 12. HOT-PATH COST MODEL

### 12.1 Frozen target-selection facts (preserved)

| Fact | Value |
|---|---|
| `PREFERENCE_READS_PER_CALLBACK` | `0–1` |
| `XPOSED_FIELD_HELPER_ATTEMPTS_PER_CALLBACK` (legacy) | `1–4` |
| `STRUCTURALLY_PROVEN_PER_CALLBACK_ALLOCATION` | `none` |
| `REFLECTION_BOXING_ALLOCATION` | `NOT_PROVEN` |
| `REAL_CALLBACK_FREQUENCY` | `NOT_PROVEN` |

### 12.2 Expected Architecture C reduction under Strategy A

| Branch | Legacy operations | Strategy A FAST operations |
|---|---|---|
| `mHovering == true` | 1 `XposedHelpers.getBooleanField`; 0 pref reads | 1 `Field.getBoolean`; 0 pref reads; no `XposedHelpers` cache lookup. |
| `mHovering == false && safety == true` | `mHovering` + `mIsSafetyShowing` (or fallback `mSafetyWarning`) + pref read | `mHovering` `Field.getBoolean`; safety alias remains `XposedHelpers` try/catch (1–2 attempts); 1 snapshot read (no `PrefMap` call). |
| `mHovering == false && safety == false` | `mHovering` + `mIsSafetyShowing` (or fallback `mSafetyWarning`) + `mExpanded` + pref read | `mHovering` `Field.getBoolean`; safety alias legacy; `mExpanded` `Field.getBoolean`; 1 snapshot read. |

Conditional reduction summary:

- **Preference reads:**
  - `ELIGIBLE_FAST_CALLBACK_PREFMAP_READS = 0` — eligible FAST callbacks do not call `MainModule.mPrefs`; they consume the already-published snapshot.
  - `COMPLETE_LEGACY_CALLBACK_PREFMAP_READS = 0–1` — complete-legacy callbacks preserve the original conditional `MainModule.mPrefs.getInt(key, 0)` call.
  - `ALL_CALLBACKS_PREFMAP_READS_REMOVED = false` — C5 removes preference reads only from the FAST execution path, not from every possible callback invocation, because `snapshot == null` and other legacy conditions still execute the original oracle.
- **Field-helper attempts for `mHovering` and `mExpanded`:** `XposedHelpers` cache map lookup and `Field` retrieval are removed; direct `Field.getBoolean` is used.
- **Safety alias:** remains on the `XposedHelpers` path. Its `1–2` attempts per applicable callback are **not** eliminated under Strategy A.

The actual per-callback savings are conditional and short-circuit dependent. No fixed operation count is claimed.

---

## 13. FUTURE B1 TEST PLAN

Tests are specified, not implemented, in A0.

### 13.1 Resolver

- exact root `MiuiVolumeDialogImpl` resolves `mHovering` and `mExpanded`.
- inherited `mHovering`/`mExpanded` from superclass resolves on root.
- subclass mismatch returns `null` ABI.
- `mHovering` wrapper `Boolean` field returns `null` ABI.
- `mExpanded` wrapper `Boolean` field returns `null` ABI.
- missing required field returns `null` ABI.
- ordinary resolver failure returns `null` ABI.
- fatal resolver failure propagates.

### 13.2 Safety alias

- `mIsSafetyShowing` true.
- `mIsSafetyShowing` false.
- `mIsSafetyShowing` ordinary failure → `mSafetyWarning` true.
- `mIsSafetyShowing` cast/value failure → `mSafetyWarning` false.
- `mIsSafetyShowing` null → `mSafetyWarning` fallback.
- fatal `OutOfMemoryError` from first alias → no fallback.
- `mSafetyWarning` ordinary failure after first alias → outer callback failure semantics.
- `mSafetyWarning` fatal → propagates.
- both fields absent → outer failure.
- subclass `thisObject` uses complete legacy path including safety alias.

### 13.3 Snapshot

- **MULTI_KEY_SOURCE_CONSISTENCY**: build one C5 snapshot from one `PrefMap.getAll()` source; prove `expanded` and `collapsed` belong to the same captured map; do not use two independent `getInt` calls.
- relevant key refresh.
- irrelevant key ignored.
- null-key full rebuild.
- **INITIALIZATION**: initial state `null`; a callback while `null` selects complete legacy; successful initial refresh publishes a non-null snapshot; no callback-time lazy build.
- **REFRESH SERIALIZATION**: initial refresh and observer refresh cannot stale-overwrite each other because both synchronize on the same `refreshLock`; the callback never acquires `refreshLock`.
- **REFRESH FAILURE**: ordinary refresh failure clears the previous snapshot (no stale snapshot retained); C5 fatal boundary clears the snapshot and then rethrows; `PreferenceObserverRegistry` and `PreferenceBootstrap` boundaries are tested only to the extent documented.
- immutable publication (`AtomicReference`).

### 13.4 Effect / oracle

- hovering → `16000`.
- safety true + expanded > 0.
- safety true + expanded <= 0 → `5000`.
- non-safety expanded > 0.
- non-safety collapsed > 0.
- `opt <= 0` → original fallthrough.
- exact-root FAST.
- class mismatch → complete legacy.
- snapshot unavailable → complete legacy.

### 13.5 Failure

- ordinary FAST `mHovering` failure reaches `MethodHook` semantics.
- fatal propagation.
- no illegal retry after `mHovering` FAST begins.
- `IllegalAccessException` maps to `IllegalAccessError`.

### 13.6 Observer

- irrelevant non-null key does not rebuild.
- relevant key rebuilds.
- null key rebuilds.
- C5 `PreferenceObserver.onChange` runs under `ModuleHelper.guarded`.

---

## 14. EVIDENCE / NOT PROVEN MATRIX

| Item | Classification | Source / note |
|---|---|---|
| `BeforeHookCallback.returnAndSkip` semantics | `STRUCTURAL` | `HookerClassHelper.kt:73-77`. |
| `MethodHook.intercept` proceed/skip/throw flow | `STRUCTURAL` | `HookerClassHelper.kt:167-201`. |
| `MethodHook.beforeHook` fatal/ordinary catch | `STRUCTURAL` | `HookerClassHelper.kt:203-215`, `FatalErrors.kt:14-28`. |
| `ModuleHelper.findAndHookMethod` → `XposedHelpers.findAndHookMethod` | `STRUCTURAL` | `ModuleHelper.kt:115-116`, `XposedHelpers.java:601-608`. |
| `findMethodExact` cache key (no return type) | `STRUCTURAL` | `XposedHelpers.java:689-706`. |
| `getParameterClasses` with zero parameter types | `STRUCTURAL` | `XposedHelpers.java:918-943`. |
| `XposedHelpers.getBooleanField` primitive semantics | `STRUCTURAL` | `XposedHelpers.java:1385-1395`. |
| `XposedHelpers.getObjectField` object semantics | `STRUCTURAL` | `XposedHelpers.java:1362-1372`. |
| `XposedHelpers.findField` runtime-class-first lookup | `STRUCTURAL` | `XposedHelpers.java:556-572`. |
| `PrefMap.getAll` returns a single generation-consistent `Map` | `STRUCTURAL` | `PrefMap.kt:118-119`. |
| `PrefMap` typed getters may observe different snapshots | `STRUCTURAL` | `PrefMap.kt:27-31`, `120-123`. |
| `PrefMap.getInt` returns `defaultValue` on type mismatch | `STRUCTURAL` | `PrefMap.kt:120-123`. |
| `PrefMap` `AtomicReference` publication | `STRUCTURAL` | `PrefMap.kt:25-48`. |
| `MainModule.mPrefs` is a non-null `public static final PrefMap` | `STRUCTURAL` | `MainModule.java:47`. |
| `PreferenceObserverRegistry` fan-out and failure isolation | `STRUCTURAL` | `PreferenceObserverRegistry.kt:58-168`. |
| `PreferenceObserverRegistry` rethrows OOM / ThreadDeath / VME / LinkageError | `STRUCTURAL` | `PreferenceObserverRegistry.kt:143-148`, `113-128`. |
| `PreferenceBootstrap.onPreferenceChanged` catches `Throwable` and logs | `STRUCTURAL` | `PreferenceBootstrap.kt:265-291`. |
| `REAL_COMPUTE_TIMEOUT_H_RETURN_TYPE` | `NOT_PROVEN` | return type not part of `findAndHookMethod` lookup. |
| `REAL_COMPUTE_TIMEOUT_H_OVERLOAD_SET` | `NOT_PROVEN` | only zero parameter types are supplied. |
| `REAL_HYPEROS_FIELD_TYPE_FOR_MHOVERING` | `NOT_PROVEN` | resolver enforces `Boolean.TYPE`; any other type is a miss. |
| `REAL_HYPEROS_FIELD_TYPE_FOR_MEXPANDED` | `NOT_PROVEN` | resolver enforces `Boolean.TYPE`; any other type is a miss. |
| `REAL_MISAFETYSHOWING_VS_MSAFETYWARNING_PREVALENCE` | `NOT_PROVEN` | both field names are candidates. |
| `CALLBACK_THREAD` for `computeTimeoutH` | `NOT_PROVEN` | source does not prove the invoking thread. |
| `REAL_CALLBACK_FREQUENCY` | `NOT_PROVEN` | no real-device timing. |
| Real preference observer callback execution | `NOT_RUNTIME_TESTED_CALLBACK` | no real callback timing/thread evidence. |
| Start gate / validation commands | `LOCAL_EXECUTION_EVIDENCE_ONLY` | executed locally during this A0 corrective. |

---

## 15. SCOPE FREEZE

### 15.1 In scope

- `VolumeDialogAutohideDelayHook` callback oracle.
- `MiuiVolumeDialogImpl.computeTimeoutH` hook surface.
- Fields: `mHovering`, `mExpanded`.
- Safety alias: `mIsSafetyShowing` / `mSafetyWarning`.
- Preference keys: `system_volumedialogdelay_expanded`, `system_volumedialogdelay_collapsed`.
- Future `VolumeDialogAutohideDelaySnapshot`, `VolumeDialogAutohideDelayRuntimeState`, `VolumeDialogAutohideDelayAbi`, `VolumeDialogAutohideDelayEffect`, `VolumeDialogAutohideDelayResolver`.

### 15.2 Out of scope

- `BlurVolumeDialogBackgroundHook`.
- `DrawerBlurRatioHook`.
- `StatusBarDigitalSignalHook`.
- `ExpandNotificationsHook`.
- C1, C2, C3, C4 production and tests.
- Any other `MiuiVolumeDialogImpl` method.
- Changes to preference key semantics.

---

## 16. A0 OUTCOME

The preflight contract can be frozen as follows:

- The exact legacy callback oracle is documented and ordered.
- `MethodHook` boundary semantics are frozen: `returnAndSkip` sets skipped/result, fatal errors propagate, ordinary `before` callback failures let the original method proceed.
- The real `computeTimeoutH` method ABI is `NOT_PROVEN` for return type and overload set; `DOES_B1_REQUIRE_RETURN_TYPE_KNOWLEDGE = NO`.
- The `mHovering` and `mExpanded` FAST field contract is frozen as primitive `boolean` only, with exact-root eligibility and no fallback after FAST begins.
- The safety alias is the primary A0 blocker. **Strategy A (keep safety alias legacy)** is selected because it preserves the exact failure/fatal contract and avoids a fragile failure-emulation layer.
- Complete legacy fallback boundary is defined: fallback allowed only before any FAST field operation; exact-root check must precede FAST.
- Snapshot/config publication is frozen as `SNAPSHOT_PUBLICATION = AtomicReference<VolumeDialogAutohideDelaySnapshot?>` with initial value `null`. `null` means unavailable and forces `COMPLETE_LEGACY` before any FAST field access. No `@Volatile` alternative is left open.
- Snapshot construction is frozen to build from a single `PrefMap.getAll()` source; two independent `MainModule.mPrefs.getInt` calls are forbidden.
- Snapshot refresh is frozen to a single `refreshSnapshot()` function that clears `snapshotRef` before returning on any `Throwable`, with fatal rethrow after the clear.
- Refresh serialization is frozen to a private `refreshLock` owned by `VolumeDialogAutohideDelayRuntimeState`; the `computeTimeoutH` callback never acquires this lock.
- Initial construction is frozen to runtime-state creation: register observer, then perform initial refresh outside `computeTimeoutH`; the callback never triggers lazy snapshot construction.
- Snapshot failure boundaries are frozen: `C5_REFRESH_FATAL` (C5 `refreshSnapshot` clears and rethrows), `REGISTRY_FATAL` (`PreferenceObserverRegistry` rethrows OOM/ThreadDeath/VME/LinkageError), `REMOTE_LISTENER_OUTER_BOUNDARY` (`PreferenceBootstrap.onPreferenceChanged` catches `Throwable` and logs), and `REAL_END_TO_END_FATAL_ESCAPE` is `NOT_PROVEN`.
- Preference observer ownership is process-lifetime only, with no `View`/`Context`/`Activity`/`MiuiVolumeDialogImpl` retention.
- The future Architecture C chain is proposed without introducing extra abstraction layers.
- Hot-path cost is stated conditionally and honestly: `mHovering`/`mExpanded` move to FAST, preference reads move to a snapshot, and the safety alias remains legacy.

No production code, test code, `Resolver`, `ABI`, `Effect`, or `Hook` has been created or modified.

---

## 17. VALIDATION

| Command | Result | Evidence |
|---|---|---|
| `git diff --check` | pass | exit `0` |
| `python tools/check_document_contracts.py` | pass | exit `0` |
| `python tools/verify.py full` | **not run** | docs-only A0 preflight; no production or test changes. |

All validation results are `LOCAL_EXECUTION_EVIDENCE_ONLY`.

---

## 18. SUBMISSION FIELDS

| Field | Value |
|---|---|
| Base SHA | `69eb63d01e8c6549523567789b0110308147f49e` |
| Final SHA | *(to be recorded after commit and push)* |
| Branch | `devin/a14-architecture-c-r14.20.0` |
| Changed files | `docs/architecture-c/C5_VOLUME_DIALOG_AUTOHIDE_A0_PREFLIGHT.md` |
| Production changed | `false` |
| Tests changed | `false` |
| Docs changed | `true` |

---

C5_TARGET_SELECTION = PASS  
C5_TARGET_SELECTION_FREEZE = `3c5cb8cca3cd08799097e534ffef2366a6504b59`  
C5_B1_IMPLEMENTATION = NOT_STARTED  
C5_PRODUCTION_CHANGED = false  

C5_A0_PREFLIGHT_READY_FOR_INDEPENDENT_AUDIT
