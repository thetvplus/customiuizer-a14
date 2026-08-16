# C6 — Notification Auto-Expand Architecture C Target Selection (Freeze)

**Repository:** `tomthenpc/customiuizer-a14`  
**Branch:** `devin/a14-architecture-c-r14.20.0`  
**C6 selection base SHA:** `e98a1566abd93e2160f57372e9c89d29c6652779`  
**Evidence classification:** `LOCAL_EXECUTION_EVIDENCE_ONLY`

This is a documentation-only target-selection freeze.  
**C6_A0_NOT_STARTED**  
**C6_PRODUCTION_NOT_STARTED**

---

## 0. START GATE

| Check | Result | Evidence |
|---|---|---|
| Current branch | `devin/a14-architecture-c-r14.20.0` | `git branch --show-current` |
| Local HEAD | `e98a1566abd93e2160f57372e9c89d29c6652779` | `git rev-parse HEAD` |
| Remote HEAD | `e98a1566abd93e2160f57372e9c89d29c6652779` | `git rev-parse origin/devin/a14-architecture-c-r14.20.0` |
| Merge-base | `e98a1566abd93e2160f57372e9c89d29c6652779` | `git merge-base HEAD origin/devin/a14-architecture-c-r14.20.0` |
| Worktree | clean | `git status --short` returned empty |
| C1/C2/C3/C4/C5 reopened | `false` | no resolver/ABI/effect/hook changes in this commit |
| C6-A0 preflight started | `false` | no ABI preflight document or production code created |
| C6 production started | `false` | no resolver/ABI/snapshot/runtime/effect/hook class created |

START PASS.

---

## 1. SELECTION SUMMARY

The Architecture C Gatekeeper selected the following target.  This document only freezes target-selection facts; it does not start implementation.

```text
TARGET_HOOK:      SystemNotificationHooks.ExpandNotificationsHook
TARGET_ROM_CLASS: com.android.systemui.statusbar.notification.row.ExpandableNotificationRow
TARGET_METHOD:    setFeedbackIcon
TARGET_DOMAIN:    notification auto-expand
SELECTION_BASE:   e98a1566abd93e2160f57372e9c89d29c6652779
```

The exact base source confirms the hook exists and targets `ExpandableNotificationRow.setFeedbackIcon`.  No source fact contradicts the Gatekeeper selection.

---

## 2. INDEPENDENT SOURCE EVIDENCE — SELECTED TARGET

### 2.1 Exact legacy callback source

`app/src/main/java/tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt:42-70`

```kotlin
    @JvmStatic
    fun ExpandNotificationsHook(lpparam: PackageReadyParam) {
        val feedbackMethod = "setFeedbackIcon"
        ModuleHelper.hookAllMethods("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow", lpparam.classLoader, feedbackMethod, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                val thisObject = chain.thisObject
                try {

                    val mOnKeyguard = XposedHelpers.getBooleanField(thisObject, "mOnKeyguard")
                    if (!mOnKeyguard) {
                        val notification = XposedHelpers.getObjectField(XposedHelpers.callMethod(thisObject, "getEntry"), "mSbn")
                        val pkgName = XposedHelpers.callMethod(notification, "getPackageName") as String
                        val opt = Integer.parseInt(MainModule.mPrefs.getString("system_expandnotifs", "1") ?: "1")
                        val isSelected = MainModule.mPrefs.getStringSet("system_expandnotifs_apps")?.contains(pkgName) ?: false
                        if ((opt == 2 && !isSelected) || (opt == 3 && isSelected))
                            XposedHelpers.callMethod(thisObject, "setSystemExpanded", true)
                    }

                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }
```

### 2.2 Structural observations from the exact source

- The hook is installed with `ModuleHelper.hookAllMethods` on `com.android.systemui.statusbar.notification.row.ExpandableNotificationRow` and method name `setFeedbackIcon`; **no explicit parameter types are supplied**.
- The callback is an `intercept(chain)`.
- `mOnKeyguard` is read first; when `true`, no further work is performed.
- Non-keyguard path performs `getEntry()` → `mSbn` field read → `getPackageName()`.
- Preference reads in the non-keyguard path are `MainModule.mPrefs.getString("system_expandnotifs", "1")` and `MainModule.mPrefs.getStringSet("system_expandnotifs_apps")`.
- The mode value is parsed with `Integer.parseInt(...)`.
- The selection predicate is `(opt == 2 && !isSelected) || (opt == 3 && isSelected)`.
- When the predicate is true, `XposedHelpers.callMethod(thisObject, "setSystemExpanded", true)` is invoked.
- `chain.proceed()` is called **after** the conditional custom work.
- The single `try` block wraps all custom work **and** `chain.proceed()`.
- The single `catch (t: Throwable)` captures `throwable` and sets `result = null`.
- `return XposedHelpers.throwOrReturn(throwable, result)` rethrows any captured throwable, otherwise returns the original result.
- **There is no `FatalErrors.rethrowIfFatal` call inside this callback.**

---

## 3. TARGET-SELECTION FACTS TO FREEZE

### A. Callback frequency

| Fact | Value | Evidence |
|---|---|---|
| Real callback frequency for `ExpandableNotificationRow.setFeedbackIcon` | `NOT_PROVEN` | No real-device timing or GitHub CI runtime trace is available. |

The method name `setFeedbackIcon` does **not** prove a frequency, a per-frame rate, or a per-notification call guarantee.

### B. Per-callback preference reads

| Path | Count | Keys | Evidence |
|---|---|---|---|
| `mOnKeyguard == true` | 0 | — | `SystemNotificationHooks.kt:52-53` short-circuits before any preference read. |
| `mOnKeyguard == false` | 2 | `system_expandnotifs`, `system_expandnotifs_apps` | `SystemNotificationHooks.kt:56-57`. |

The mode is read with `getString(..., "1")` and then `Integer.parseInt` is applied; the app set is read with `getStringSet(...)`.

### C. Reflection / helper attempts

Each helper attempt is a `XposedHelpers` cache-map lookup plus the corresponding `Field.get*` / `Method.invoke` work.

| Short-circuit condition | Attempted helpers | Evidence |
|---|---|---|
| Always | `getBooleanField(thisObject, "mOnKeyguard")` | `SystemNotificationHooks.kt:52` |
| `!mOnKeyguard` | `callMethod(thisObject, "getEntry")` | `SystemNotificationHooks.kt:54` |
| `!mOnKeyguard` | `getObjectField(<entry>, "mSbn")` | `SystemNotificationHooks.kt:54` |
| `!mOnKeyguard` | `callMethod(notification, "getPackageName")` | `SystemNotificationHooks.kt:55` |
| Predicate true | `callMethod(thisObject, "setSystemExpanded", true)` | `SystemNotificationHooks.kt:59` |

**Per-callback helper attempt count = 1–5**, conditional on the keyguard guard and the selection predicate.  It is **not** a fixed 5.

### D. Structurally proven allocation

| Fact | Value | Evidence |
|---|---|---|
| `STRUCTURALLY_PROVEN_PER_CALLBACK_ALLOCATION` | `none` | The callback source does not contain a `new`, `arrayOf`, `ArrayList`, `HashSet`, `Runnable`, `StringBuilder`, or other explicit allocation. |
| `PrefMap.getString` return object allocation | `NOT_PROVEN` | `PrefMap.getString` returns the stored `String` or the default; it does not copy the string, but whether the stored value itself is a new object is outside this callback. |
| `PrefMap.getStringSet` return set allocation | `NOT_PROVEN` | `PrefMap.getStringSet` returns the stored `Set<String>` or `Collections.emptySet()`; it does not create a copy. |
| `Integer.parseInt` object allocation | `NOT_PROVEN` | `Integer.parseInt` returns a primitive `int`; any boxing is performed by the Kotlin/JVM runtime, not by the callback source. |
| Reflection boxing / vararg array for `setSystemExpanded(true)` | `NOT_PROVEN` | The literal `true` is passed to a Java varargs `callMethod`; the resulting `Object[]` / `Boolean` boxing is a JVM call-site detail that is not structurally proven from this file alone. |

### E. Lifecycle / ownership

| Fact | Value | Evidence |
|---|---|---|
| Callback owner | The current `ExpandableNotificationRow` instance (`chain.thisObject`) | `SystemNotificationHooks.kt:49` |
| Retained references | None | No `setAdditionalInstanceField`, no per-row cache, no per-view cache, no retained `View`, `Context`, `Activity`, or `ExpandableNotificationRow`. |
| Strong reference held after callback | `false` | The callback only borrows `thisObject` and the transient `notification` / `pkgName` references. |

Architecture C **MUST NOT** introduce per-row cache, per-view cache, retained views, generation registry, runtime owner registry, manager layer, `Flow`, or `coroutine` scope for this feature.

### F. Mutation contract

| Fact | Value | Evidence |
|---|---|---|
| Observable mutation | `XposedHelpers.callMethod(thisObject, "setSystemExpanded", true)` | `SystemNotificationHooks.kt:59` |
| Mutation condition | `(opt == 2 && !isSelected) || (opt == 3 && isSelected)` | `SystemNotificationHooks.kt:58` |
| Mutation timing | Before `chain.proceed()` | Custom work precedes `chain.proceed()` on line 62. |

The `setSystemExpanded(true)` call is an observable, state-changing ROM method call.  Architecture C **MUST** preserve the exact condition and the exact timing (before the original `setFeedbackIcon` body runs).  It **MUST NOT** optimize this into an asynchronous, deferred, or earlier invocation.

### G. Failure / fatal semantics

| Aspect | Exact behavior | Evidence |
|---|---|---|
| Custom work vs. `chain.proceed()` order | Custom work (including `setSystemExpanded` when applicable) runs first; then `chain.proceed()` runs. | `SystemNotificationHooks.kt:52-62`. |
| Throwable capture scope | One `try { ... }` wraps lines 51–62, which include all custom work **and** `chain.proceed()`. | `SystemNotificationHooks.kt:50-66`. |
| Helper / preference / `setSystemExpanded` failure | If any custom work throws, control jumps to `catch`; `chain.proceed()` is **not** called; `throwable` is set; the captured throwable is rethrown by `throwOrReturn`. | `SystemNotificationHooks.kt:63-67`. |
| Original method failure | If `chain.proceed()` throws, the exception is captured in the same `catch` block and rethrown by `throwOrReturn`. | `SystemNotificationHooks.kt:62-67`. |
| `FatalErrors.rethrowIfFatal` call | `NOT_PRESENT` in this callback. | Source inspection of `SystemNotificationHooks.kt:42-70`. |
| Fatal error propagation | `throwOrReturn(Throwable, Object)` rethrows any non-null throwable, including `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError`, because it is inside the single `catch (t: Throwable)` block. | `XposedHelpers.java:109-112`. |
| Original method execution on custom-work failure | `chain.proceed()` is **not** called when custom work throws before it is reached, because the single `try` block is exited to `catch`.  The original `setFeedbackIcon` therefore does not execute in that control path. | Source control-flow analysis of `SystemNotificationHooks.kt:50-67`. |
| Partial mutation inside `setSystemExpanded(true)` | `NOT_PROVEN` whether `setSystemExpanded(true)` may partially mutate ROM state before itself throwing.  If it does, that partial mutation is not rolled back by the legacy callback. | The legacy callback has no rollback logic. |

**Failure-semantic freeze:**

- A frozen ABI / FAST path **MUST NOT** silently drop an original `Throwable` from `chain.proceed()`.
- A frozen ABI / FAST path **MUST NOT** call `setSystemExpanded(true)` twice in a single callback.
- A frozen ABI / FAST path **MUST** handle fatal errors (at minimum `OutOfMemoryError`, `ThreadDeath`, `VirtualMachineError`) at least as safely as the legacy path.

### H. Behavior oracle

The exact predicate and fall-through behavior are:

1. Read `mOnKeyguard` from `thisObject`.
2. If `mOnKeyguard` is `true`, fall through to `chain.proceed()` with no other action.
3. If `mOnKeyguard` is `false`:
   1. `getEntry()` on `thisObject`.
   2. Read `mSbn` from the entry.
   3. `getPackageName()` on `mSbn`.
   4. `opt = Integer.parseInt(MainModule.mPrefs.getString("system_expandnotifs", "1"))`.
   5. `isSelected = MainModule.mPrefs.getStringSet("system_expandnotifs_apps").contains(pkgName)` (or `false` if missing).
   6. If `(opt == 2 && !isSelected) || (opt == 3 && isSelected)`, call `setSystemExpanded(true)` on `thisObject`.
4. Call `chain.proceed()`.
5. Return the result of `chain.proceed()` or rethrow any captured `Throwable`.

Mode semantics:

- `opt == 2`: auto-expand all notifications **except** the selected apps (blacklist semantics).
- `opt == 3`: auto-expand only the selected apps (whitelist semantics).
- `opt == 1` (or any other value): no expansion from this callback.

Architecture C **MUST NOT** change:

- keyguard bypass behavior,
- mode semantics,
- whitelist / selected-app semantics,
- `setSystemExpanded(true)` condition,
- `chain.proceed()` ordering.

---

## 4. A0-ONLY QUESTIONS — DOCUMENT, DO NOT SOLVE

The following ABI questions must be resolved in C6-A0 before any production code is written.  They are listed here as `NOT_PROVEN` target-selection blockers.

| ABI item | Status | Why it must be proven in A0 |
|---|---|---|
| `ExpandableNotificationRow` exact declaration / root class | `NOT_PROVEN` | The hook uses the string class name `com.android.systemui.statusbar.notification.row.ExpandableNotificationRow`; whether this is the exact runtime declaration, a subclass root, or has subclasses is unknown. |
| `mOnKeyguard` declaration root | `NOT_PROVEN` | Field may be declared in `ExpandableNotificationRow` or a superclass. |
| `mOnKeyguard` primitive vs. reference type | `NOT_PROVEN` | Legacy code uses `XposedHelpers.getBooleanField`, which is a primitive-boolean reflective accessor using `Field.getBoolean`. The actual ROM field type (`boolean` primitive, `Boolean` object, or another type) and whether `Field.getBoolean` can read it are `NOT_PROVEN`; A0 must prove the real declaration. |
| `mOnKeyguard` shadowing / subclass behavior | `NOT_PROVEN` | Subclasses may shadow or re-declare the field. |
| `getEntry` declaration root | `NOT_PROVEN` | Method may be declared in `ExpandableNotificationRow` or a superclass. |
| `getEntry` parameter shape | `NOT_PROVEN` | Legacy call uses zero arguments; overloads may exist. |
| `getEntry` return type | `NOT_PROVEN` | Legacy code reads `mSbn` from the returned object; the exact return class is unknown. |
| `getEntry` overload set | `NOT_PROVEN` | `hookAllMethods` / `callMethod` may resolve overloads. |
| Returned entry object's `mSbn` | `NOT_PROVEN` | Runtime / declaration class of the entry and the `mSbn` field type are unknown. |
| `mSbn` field type | `NOT_PROVEN` | Expected to be `StatusBarNotification` or similar, but not proven. |
| `mSbn` shadowing behavior | `NOT_PROVEN` | Subclasses or different ROM builds may place `mSbn` differently. |
| `getPackageName` declaration root | `NOT_PROVEN` | Method belongs to the `mSbn` object class, not `ExpandableNotificationRow`. |
| `getPackageName` parameter shape | `NOT_PROVEN` | Legacy call uses zero arguments; overloads may exist. |
| `getPackageName` return type | `NOT_PROVEN` | Legacy code casts to `String`; the method may return `String` or `CharSequence`. |
| `getPackageName` overload set | `NOT_PROVEN` | Multiple overloads may exist. |
| `setSystemExpanded` legacy invocation arity | `STRUCTURAL` | The legacy callback calls `XposedHelpers.callMethod(thisObject, "setSystemExpanded", true)`, so exactly one argument is supplied. |
| `setSystemExpanded` declaration root | `NOT_PROVEN` | Method may be declared in `ExpandableNotificationRow` or a superclass. |
| `setSystemExpanded` parameter shape | `NOT_PROVEN` | The legacy call supplies exactly one argument. The exact one-argument type (`boolean` primitive, `Boolean` reference, or another compatible one-argument type) is `NOT_PROVEN`. The legacy callback does **not** prove any additional required parameters. |
| `setSystemExpanded` primitive vs. reference parameter | `NOT_PROVEN` | The Kotlin `true` literal is boxed to `java.lang.Boolean` for the Java varargs `callMethod`; the real method parameter may be `boolean` primitive, `Boolean` reference, or another compatible one-argument type. |
| `setSystemExpanded` return type | `NOT_PROVEN` | Legacy code ignores the return value. |
| `setSystemExpanded` overload set | `NOT_PROVEN` | Legacy invokes `XposedHelpers.callMethod(thisObject, "setSystemExpanded", true)`. `callMethod` uses `findMethodBestMatch` and may resolve among compatible one-argument overloads. The exact ROM overload set is `NOT_PROVEN`. |
| `setFeedbackIcon` parameter shape | `NOT_PROVEN` | `ModuleHelper.hookAllMethods` is called with only the method name. |
| `setFeedbackIcon` return type | `NOT_PROVEN` | Required to know what `chain.proceed()` returns. |
| `setFeedbackIcon` overload set | `NOT_PROVEN` | `hookAllMethods` may install on multiple overloads. |

---

## 5. EXPECTED ARCHITECTURE C DIRECTION — DESIGN CONSTRAINT ONLY

If A0 proves the ABI feasible, the expected direction is:

```text
Cold Resolve → Frozen ABI → Typed Config / Snapshot → Runtime / Lifecycle → Effect → Thin Hook → Hot Execute
```

The typed config / snapshot for this feature must stay small:

- `mode`: an `Int` (parsed value of `system_expandnotifs`).
- `apps`: an immutable, copy-owned `Set<String>` (value of `system_expandnotifs_apps`).

This target-selection freeze **does not** authorize introducing:

- manager hierarchy,
- `Flow`,
- coroutine scope,
- runtime ABI map,
- per-row / per-view cache,
- generation registry,
- unnecessary abstraction layers.

If preference publication is needed in a later stage, the publication semantics (observer thread, snapshot replacement vs. per-key CAS, copy-on-write of the `Set<String>`) must be proven in that stage.  Target selection does **not** pre-select `AtomicReference`, `volatile`, or `Lock`; those are A0/B1 decisions.

---

## 6. EXPECTED FAST / LEGACY SHAPE

This section is a feasibility / constraint analysis only; it is not an implementation.

| Scenario | Expected behavior |
|---|---|
| Exact root known and ABI resolvable | Use `exact-root FAST`. |
| Subclass or unexpected runtime shape | Use `COMPLETE LEGACY` callback. |
| ABI unavailable at install time | Use `COMPLETE LEGACY` callback or make an install-time fallback decision. |
| `NO FAST` eligibility discovered after FAST started | **MUST NOT** fall back to legacy after `setSystemExpanded(true)` has already been called.  A `FAST` path that begins and then discovers an ineligible condition has already performed an observable mutation; re-running the legacy callback would call `setSystemExpanded(true)` a second time.  Therefore the fallback gate must be **before** any mutation. |

This constraint is written into the target-selection freeze to prevent a double `setSystemExpanded(true)` mutation.

---

## 7. RUNNER-UP COMPARISON

The following candidates were re-inspected at the exact base SHA.  Each is compared against the selected target using the same structural criteria.  No production code was changed.

### 7.1 Runner-up source locations

| Candidate | Primary hook surface | Source location |
|---|---|---|
| `MaxNotificationIconsHook` | `findAndHookMethod` on `com.android.systemui.statusbar.phone.NotificationIconContainer.resetViewStates` | `SystemNotificationHooks.kt:512-536` |
| `BetterPopupsAllowFloatHook` | `findAndHookMethod` on `com.android.systemui.statusbar.notification.row.MiuiExpandableNotificationRow.updateMiniWindowBar` | `SystemWindowHooks.kt:232-259` |
| `NotificationImportanceHook` | `findAndHookMethod` on `com.android.systemui.statusbar.phone.NotificationIconAreaController.updateStatusBarIcons` | `SystemUINotificationHooks.kt:118-136` |
| `QSHapticHook` | `findAndHookMethod` on `com.android.systemui.qs.tileimpl.QSTileImpl.click(View)` | `SystemAudioHooks.kt:37-69` |
| `DrawerBlurRatioHook` | `findAndHookMethod` on `NotificationShadeDepthController$updateBlurCallback$1.doFrame`, `BlurUtilsExt.applyBlur`, `ControlPanelWindowManager.setBlurRatio` | `SystemDisplayHooks.kt:292-312` |

### 7.2 Comparison matrix

| Attribute | **Selected** `ExpandNotificationsHook` | `MaxNotificationIconsHook` | `BetterPopupsAllowFloatHook` | `NotificationImportanceHook` | `QSHapticHook` | `DrawerBlurRatioHook` |
|---|---|---|---|---|---|---|
| **Callback frequency** | `NOT_PROVEN` | `NOT_PROVEN` | `NOT_PROVEN` | `NOT_PROVEN` | `NOT_PROVEN` | `NOT_PROVEN` |
| **Per-callback preference reads** | 0 if keyguard, else 2 (`getString` + `getStringSet`) | 1 (`getStringAsInt`) | 2 (`getStringSet` × 2) | 0 in callback | 2 (`getBoolean` + `getStringAsInt`) | 0 in callback (volatile `drawerBlurModifierPct`) |
| **Reflection / helper attempts (conditional)** | 1–5: `mOnKeyguard` always; `getEntry`, `mSbn`, `getPackageName` if not keyguard; `setSystemExpanded` if predicate true | 1–3: `mMaxStaticIcons` read always; `mMaxStaticIcons` / `mMaxIconsOnLockscreen` write if value differs | 4+ helper lookups + `List.contains/add/remove`: `getMiniWindowTargetPkg`, `getMAppMiniWindowManager`, `notificationSettingsManager`, `mAllowNotificationSlide` | `getObjectField` `mNotificationEntries`; per-item `getRepresentativeEntry`, `getImportance`; conditional `setObjectField` `mNotificationEntries` | `callMethod getState`, `getIntField state`, `getObjectField mContext` | 3 surfaces; `doFrame` uses `getAdditionalInstanceField` / `WeakReference.get` / `DrawerBlurScope` ThreadLocal; `applyBlur` / `setBlurRatio` use `getArgsArray` and arg mutation |
| **Structurally proven per-callback allocation** | `none` | `none` | `none` in the hook; `List.add/remove` may resize the underlying `ArrayList` at the ROM implementation level | one `ArrayList<Any>` allocation when `mNotificationEntries.isNotEmpty()`; the `arrayList.size != mNotificationEntries.size` guard only controls whether the filtered list is written back to `mNotificationEntries` | `none` in the hook; `HookUtils.perform*Vibration` allocation not proven | `ThreadLocal` `State` per thread (created once); `WeakReference` on cache miss; `getArgsArray` `Object[]` per `applyBlur`/`setBlurRatio` callback |
| **Lifecycle / ownership risk** | Low: borrows current row, no retained refs | Low: borrows `NotificationIconContainer` | Low–medium: mutates `mAllowNotificationSlide` list on the ROM object | Low: borrows `NotificationIconAreaController` and its list | Low: borrows `QSTileImpl` and `Context` | Medium: `WeakReference` target cache and `ThreadLocal` scope must be preserved; target may be re-discovered |
| **Mutation risk** | Calls `setSystemExpanded(true)` conditionally | Writes `mMaxStaticIcons` and `mMaxIconsOnLockscreen` on every `resetViewStates` when value differs | Adds/removes package names from `mAllowNotificationSlide` | Creates a filtered `ArrayList<Any>` when `mNotificationEntries` is non-empty, then replaces `mNotificationEntries` only when `arrayList.size != mNotificationEntries.size` | Calls external vibration helper (no ROM field write, but observable side effect) | Mutates blur ratio argument only; no ROM field write |
| **Failure-semantic complexity** | Medium: custom work and `chain.proceed()` share one `try`; custom-work failure skips original; side effect cannot be rolled back after `setSystemExpanded` | Medium: field writes before `chain.proceed`; if write fails, original skipped and rethrown; if second write fails after first, partial state remains | Medium: list mutation before `chain.proceed`; add/remove side effects persist if `chain.proceed()` throws | Medium: `before` callback with no local catch; reflection error propagates and original method is not explicitly guarded | Low: original `chain.proceed()` in its own `try`; custom work in a second `try` that logs and swallows exceptions | Medium: `doFrame` uses `try/finally` for `DrawerBlurScope`; `findBlurUtilsExt` swallows non-fatal `Throwable` via `FatalErrors.rethrowIfFatal`; arg mutation occurs before `chain.proceed(args)` |
| **ABI stability** | Medium: single class, but requires method ABI for `getEntry`, `getPackageName`, `setSystemExpanded` and `Set<String>` snapshot | High: single class, two int fields; only `resetViewStates` overload set is unknown | Medium: single class, but multiple method/field lookups and `List<String>` field; two `Set<String>` snapshots | Medium: single class, but `getRepresentativeEntry` / `getImportance` / `mNotificationEntries` and conditional `ArrayList` creation | High: single class, `click(View)`, `mContext`, `getState` / `state` | Low–medium: multiple target classes; `BlurUtilsExt` discovered dynamically by field-name and type fallback; no stable name-known field |
| **Expected Architecture C benefit** | Remove up to 2 preference reads and up to 5 reflective helper attempts per callback; small immutable `Int + Set<String>` snapshot | Replace `getStringAsInt` with snapshot and field helpers with frozen `Field` reads/writes; benefit is smaller because the hook already writes fields | Remove 2 `getStringSet` and several helper calls; list mutation remains, so marginal benefit is limited | Reduce helper calls; but the `ArrayList<Any>` allocation on every non-empty callback and the list filtering are the dominant costs, so marginal benefit is limited | Small: only a few helper calls; external vibration helper dominates | Limited: `drawerBlurModifierPct` already uses a volatile snapshot and `DrawerBlurScope` already uses `ThreadLocal` / `WeakReference`; frozen ABI cannot fully replace the dynamic `BlurUtilsExt` discovery and target lifecycle |
| **Selection outcome** | **Selected** | Rejected: field writes on every callback, smaller marginal benefit | Rejected: list mutation and two `Set` snapshots, more complex lifecycle | Rejected: `ArrayList<Any>` allocated whenever `mNotificationEntries` is non-empty, plus a larger surface | Rejected: small feature, limited Architecture C reduction | Rejected: already has preference snapshot / volatile value / `ThreadLocal` / `WeakReference`; marginal benefit not enough |

### 7.3 Why `DrawerBlurRatioHook` was not selected

`DrawerBlurRatioHook` already has the Architecture C-like optimizations that other candidates lack:

- A **preference snapshot**: `drawerBlurModifierPct` is a `@Volatile` `Int` updated by a `PreferenceObserver` (`SystemDisplayHooks.kt:164-171`).
- A **per-thread bounded scope**: `DrawerBlurScope` uses a `ThreadLocal<State>` with explicit `enter` / `exit` and `finally` cleanup (`SystemDisplayHooks.kt:124-158`).
- A **weakly referenced target cache**: `resolveDrawerBlurTargetRef` stores a `WeakReference` in an additional instance field and re-discovers the `BlurUtilsExt` target only on cache miss (`SystemDisplayHooks.kt:209-229`).

Because the existing implementation already uses a volatile snapshot, `ThreadLocal` isolation, and `WeakReference` caching, a frozen-ABI migration would not meaningfully reduce steady-state cost.  The `BlurUtilsExt` target is discovered dynamically (candidate field names, declared-field scan, hierarchy scan), so a frozen ABI cannot directly replace that discovery without re-owning the target lifecycle.  The marginal Architecture C benefit is therefore lower than the selected `ExpandNotificationsHook`.

---

## 8. EVIDENCE CLASSIFICATION

This document uses the allowed classification values exactly:

| Evidence | Classification | Source / Note |
|---|---|---|
| Hook surface, target class name, method name, field names, preference keys, source line ranges | `STRUCTURAL` | `SystemNotificationHooks.kt`, `SystemWindowHooks.kt`, `SystemAudioHooks.kt`, `SystemUINotificationHooks.kt`, `SystemDisplayHooks.kt`, `PrefMap.kt`, `XposedHelpers.java` |
| Real runtime frequency of `setFeedbackIcon` or any candidate callback | `NOT_RUNTIME_TESTED_CALLBACK` | No real-device timing or CI runtime trace |
| Real callback execution of `ExpandNotificationsHook` on a device | `NOT_RUNTIME_TESTED_CALLBACK` | No runtime execution evidence |
| Git start gate, diff checks, status checks | `LOCAL_EXECUTION_EVIDENCE_ONLY` | Commands run on local working tree |

No combined, decorated, or custom classification values are used.  `NONE` is **not** used as an evidence classification.

```text
GITHUB_CI_STATUS = NONE
GITHUB_WORKFLOW_RUNS = NONE
```

---

## 9. VALIDATION

| Command | Result | Evidence |
|---|---|---|
| `git diff --check` | pass | exit code 0, no output |
| `git status --short` | clean except for new doc | only `?? docs/architecture-c/C6_TARGET_SELECTION.md` |
| `git diff --name-only 1e23ab90607a49aaeaa63bf2ef12e49ca7fa63da..HEAD` | `docs/architecture-c/C6_TARGET_SELECTION.md` only | post-commit check confirms only this corrective document changed |
| `git diff -- app/src/main` | empty | exit code 0, no output |
| `git diff -- app/src/test` | empty | exit code 0, no output |
| `git diff 1e23ab90607a49aaeaa63bf2ef12e49ca7fa63da..HEAD -- app/src/main` | empty | no production source changes |
| `git diff 1e23ab90607a49aaeaa63bf2ef12e49ca7fa63da..HEAD -- app/src/test` | empty | no test source changes |

All validation results are `LOCAL_EXECUTION_EVIDENCE_ONLY`.

This is a documentation-only target-selection freeze.  No Android compilation or test run is required by the C6 authorization boundary.

---

## 10. SUBMISSION FIELDS

| Field | Value |
|---|---|
| Base SHA (original C6 selection) | `e98a1566abd93e2160f57372e9c89d29c6652779` |
| Corrective base SHA | `1e23ab90607a49aaeaa63bf2ef12e49ca7fa63da` |
| Final SHA | *(to be recorded after corrective commit and push)* |
| Branch | `devin/a14-architecture-c-r14.20.0` |
| Changed files | `docs/architecture-c/C6_TARGET_SELECTION.md` |
| Production changed | `false` |
| Tests changed | `false` |
| C1 changed | `false` |
| C2 changed | `false` |
| C3 changed | `false` |
| C4 changed | `false` |
| C5 changed | `false` |

---

C6_TARGET_SELECTION_FREEZE_READY_FOR_INDEPENDENT_AUDIT

C6_SELECTED_TARGET = `SystemNotificationHooks.ExpandNotificationsHook / com.android.systemui.statusbar.notification.row.ExpandableNotificationRow.setFeedbackIcon / notification auto-expand`

C6_SELECTION_BASE = `e98a1566abd93e2160f57372e9c89d29c6652779`

C6_A0_NOT_STARTED

C6_PRODUCTION_NOT_STARTED
