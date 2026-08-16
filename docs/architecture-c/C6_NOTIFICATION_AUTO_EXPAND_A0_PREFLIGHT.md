# C6-A0 — Notification Auto-Expand Architecture C Preflight

**Repository:** `tomthenpc/customiuizer-a14`
**Branch:** `devin/a14-architecture-c-r14.20.0`
**C6 target-selection freeze SHA:** `8001f972e194bd388341c45f7064175cbcb27560`
**Scope:** `SystemNotificationHooks.ExpandNotificationsHook` → `com.android.systemui.statusbar.notification.row.ExpandableNotificationRow.setFeedbackIcon` → notification auto-expand
**Type:** docs-only A0 preflight — no production, no test, no Resolver/ABI/Effect/Snapshot/Hook production classes created.

---

## 0. START GATE

| Check | Result | Evidence |
|---|---|---|
| Current branch | `devin/a14-architecture-c-r14.20.0` | `git branch --show-current` |
| Local HEAD | `8001f972e194bd388341c45f7064175cbcb27560` | `git rev-parse HEAD` |
| Remote HEAD | `8001f972e194bd388341c45f7064175cbcb27560` | `git rev-parse origin/devin/a14-architecture-c-r14.20.0` |
| Merge-base (HEAD, origin/HEAD) | `8001f972e194bd388341c45f7064175cbcb27560` | `git merge-base HEAD origin/devin/a14-architecture-c-r14.20.0` |
| Worktree | clean | `git status --short` empty |
| C6 target-selection doc changed | `false` | no edits to `C6_TARGET_SELECTION.md` |
| C1-C5 changed | `false` | no edits |

START PASS.

---

## 1. EXACT LEGACY CALLBACK ORACLE

### 1.1 Source

`app/src/main/java/tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt:43-69`

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

### 1.2 Frozen execution order

```text
1. thisObject = chain.thisObject
2. mOnKeyguard = XposedHelpers.getBooleanField(thisObject, "mOnKeyguard")
3. if !mOnKeyguard:
    a. entry      = XposedHelpers.callMethod(thisObject, "getEntry")
    b. notification = XposedHelpers.getObjectField(entry, "mSbn")
    c. pkgName    = XposedHelpers.callMethod(notification, "getPackageName") as String
    d. opt        = Integer.parseInt(MainModule.mPrefs.getString("system_expandnotifs", "1") ?: "1")
    e. isSelected = MainModule.mPrefs.getStringSet("system_expandnotifs_apps")?.contains(pkgName) ?: false
    f. if (opt == 2 && !isSelected) || (opt == 3 && isSelected):
          XposedHelpers.callMethod(thisObject, "setSystemExpanded", true)
4. result = chain.proceed()
5. return XposedHelpers.throwOrReturn(throwable, result)
```

### 1.3 Frozen ordering rules

| Rule | Contract |
|---|---|
| `thisObject` source | `chain.thisObject` is read once and is the `ExpandableNotificationRow` instance on which `setFeedbackIcon` was called. |
| `mOnKeyguard` gate | The rest of the custom work is skipped when `mOnKeyguard == true`. |
| Sequential reflection | `getEntry` → `getObjectField(mSbn)` → `callMethod(getPackageName)` must happen in this order. |
| Preference read position | Both `getString` and `getStringSet` are read after the package name is known and before the selection predicate is evaluated. |
| Mutation condition | `setSystemExpanded(true)` is called only when the selection predicate is true. |
| `chain.proceed()` timing | `chain.proceed()` is called **after** all custom work, including `setSystemExpanded` when applicable. |
| `Throwable` capture scope | One `try { ... }` wraps lines 49-62, including all custom work and `chain.proceed()`. |
| `throwOrReturn` | If `throwable != null` it is rethrown; otherwise `result` is returned. No fatal filtering. |
| No retry / no return constant | The callback does **not** use `returnAndSkip` or `throwAndSkip`; it always reaches `chain.proceed()` unless an exception occurs first. |

### 1.4 Data-flow contract

- `thisObject` is read from `chain.thisObject` and is never retained beyond the callback frame.
- `mOnKeyguard` is treated as a `boolean` value by the legacy source (`getBooleanField`).
- `entry` is a temporary object returned by `getEntry`; it is not retained.
- `notification` is the value of the `mSbn` field on `entry`; it is not retained.
- `pkgName` is a temporary `String` derived from `getPackageName`; it is used only for the set membership test.
- `opt` is a primitive `int` parsed from the preference string.
- `isSelected` is a primitive `boolean`.
- `setSystemExpanded(true)` is the only custom ROM-side mutation.
- No `View`, `Context`, `Activity`, `Window`, or row instance is retained.

---

## 2. METHODHOOK BOUNDARY SEMANTICS

Source: `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/HookerClassHelper.kt:113-201`.

This callback overrides `MethodHook.intercept(chain: XposedInterface.Chain)`, not `beforeHook`. Therefore the outer `beforeHook` catch-and-log boundary (`HookerClassHelper.kt:203-215`) does **not** apply. The callback is fully responsible for its own `try/catch` and `chain.proceed()` management.

| Aspect | Exact behavior | Evidence |
|---|---|---|
| `chain.thisObject` | Returns the target row instance for the hooked `setFeedbackIcon` call. | `XposedInterface.Chain` contract, used at `SystemNotificationHooks.kt:49`. |
| `chain.proceed()` | Calls the next hook or the original `setFeedbackIcon`. | `HookerClassHelper.kt:179-182`. |
| Exception capture | A single `catch (t: Throwable)` at lines 63-66 captures any `Throwable` from custom work or `chain.proceed()`. | `SystemNotificationHooks.kt:63-66`. |
| Fatal propagation | `OutOfMemoryError`, `ThreadDeath`, and `VirtualMachineError` are caught by `catch (t: Throwable)` and then rethrown by `throwOrReturn`. They are **not** swallowed. | `SystemNotificationHooks.kt:63-67`, `XposedHelpers.java:109-112`. |
| Custom-work failure | If any statement inside `try` throws before `chain.proceed()` is reached, `chain.proceed()` is **not** called. | Control-flow analysis of `SystemNotificationHooks.kt:50-62`. |
| Original failure | If `chain.proceed()` throws, the exception is captured in the same `catch` and rethrown. | `SystemNotificationHooks.kt:62-67`. |

The callback is **not** a simple `beforeHook`; it is a custom `intercept` callback that fully controls whether and when the original `setFeedbackIcon` body executes.

---

## 3. HELPER CONTRACTS

All helper contract analysis is based on the current project implementation. No memory-based assumptions are used.

### A. `XposedHelpers.findField(Class<?> clazz, String fieldName)`

Source: `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java:515-533, 556-572`.

- **Search root:** the supplied `clazz`.
- **Superclass traversal:** `findFieldRecursiveImpl` first calls `clazz.getDeclaredField(fieldName)`. If not found, it walks `clazz.getSuperclass()` up to but not including `Object`, calling `getDeclaredField` on each superclass.
- **Runtime-class-first / subclass-first semantics:** the lookup starts at the supplied class and moves upward. If a subclass and a superclass both declare a field with the same name, the subclass field wins.
- **Private fields in superclasses:** for fields, `findFieldRecursiveImpl` does **not** skip private fields of superclasses; it calls `getDeclaredField` on every class in the hierarchy until `Object` is reached.
- **Accessibility:** the returned `Field` is `setAccessible(true)`.
- **Cache:** class-scoped `ConcurrentHashMap` in `fieldCache`, keyed by `clazz` and `fieldName`.
- **Miss behavior:** `NoSuchFieldError` with key `clazz.getName() + "#" + fieldName`.

### B. `XposedHelpers.getBooleanField(Object obj, String fieldName)`

Source: `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java:1385-1395`.

- Calls `findField(obj.getClass(), fieldName).getBoolean(obj)`.
- `IllegalAccessException` is logged and rethrown as `IllegalAccessError(e.getMessage())`.
- `IllegalArgumentException` is rethrown as-is.
- `Field.getBoolean` is a JDK primitive `boolean` accessor. The JDK documents it as reading an instance `boolean` field. For the FAST `mOnKeyguard` contract, the resolver must require `field.getType() == Boolean.TYPE`; any other type, including `Boolean.class`, must be rejected so that FAST semantics match the legacy `Field.getBoolean` path.

### C. `XposedHelpers.getObjectField(Object obj, String fieldName)`

Source: `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java:1362-1372`.

- Calls `findField(obj.getClass(), fieldName).get(obj)`.
- `IllegalAccessException` is logged and rethrown as `IllegalAccessError(e.getMessage())`.
- `IllegalArgumentException` is rethrown as-is.
- Returns `Object`; the caller is responsible for casts.
- `findField` starts at the **runtime class** of `obj`, so the resolved field may be declared in `obj.getClass()` or a superclass.

### D. `XposedHelpers.callMethod(Object obj, String methodName)`

Source: `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java:1788-1800, 771-787`.

- Resolves `findMethodBestMatch(obj.getClass(), methodName)`.
- Zero-argument resolution:
  - First tries `clazz.getDeclaredMethod(methodName, EMPTY_CLASS_ARRAY)`.
  - If exact not found, scans `clazz` and superclasses for any method whose name matches and whose parameter types are assignable from `EMPTY_CLASS_ARRAY` (i.e., zero parameters).
  - `considerPrivateMethods` is `true` for the starting class and `false` for superclasses; private methods of superclasses are skipped.
  - `bestMatch` is selected with `MemberUtilsX.compareMethodFit`.
- `Method.invoke(obj, EMPTY_OBJECT_ARRAY)`.
- Exception mapping:
  - `IllegalAccessException` → `IllegalAccessError(e.getMessage())` (after logging).
  - `IllegalArgumentException` → rethrown as-is.
  - `InvocationTargetException` → `InvocationTargetError(e.getCause())`.
- Miss behavior: `NoSuchMethodError`.

### E. `XposedHelpers.callMethod(Object obj, String methodName, Object... args)`

Source: `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java:1802-1814, 789-839, 847-849, 905-912`.

- `getParameterTypes(args)` builds a `Class<?>[]` from `args[i].getClass()`, with `null` for `null` arguments.
- For the legacy call `callMethod(thisObject, "setSystemExpanded", true)`:
  - The Kotlin `true` literal is boxed to `java.lang.Boolean` when passed to the Java varargs parameter.
  - `getParameterTypes` therefore returns `new Class<?>[] { Boolean.class }`.
- `findMethodBestMatch(obj.getClass(), "setSystemExpanded", Boolean.class)`:
  - First tries `findMethodExact(obj.getClass(), "setSystemExpanded", Boolean.class)`.
  - If exact not found, scans the class and superclasses for methods named `setSystemExpanded` whose parameter types are assignable from `[Boolean.class]` with `autoboxing = true`.
  - `ClassUtils.isAssignable(..., true)` treats `Boolean.class` as assignable to `boolean` (unboxing) and to `Boolean` (identity).
  - `MemberUtilsX.compareMethodFit` selects the best match. If both `setSystemExpanded(boolean)` and `setSystemExpanded(Boolean)` exist, the formal `Boolean` parameter is likely chosen because it requires fewer conversions from the actual `Boolean.class` argument.
- `Method.invoke(obj, true)`; the `Boolean` argument is passed as `Boolean.TRUE`.
- Exception mapping is identical to the no-arg variant.

### F. `XposedHelpers.throwOrReturn(Throwable throwable, Object result)`

Source: `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java:109-112`.

```java
public static Object throwOrReturn(Throwable throwable, Object result) throws Throwable {
    if (throwable != null) throw throwable;
    return result;
}
```

- Non-null `Throwable` is rethrown.
- Otherwise `result` is returned.
- No hidden fatal filtering.
- Because the callback catches `Throwable`, `throwOrReturn` can rethrow `OutOfMemoryError`, `ThreadDeath`, and `VirtualMachineError`.

---

## 4. LEGACY HOOK INSTALLATION

### 4.1 Exact installation path

Source: `ModuleHelper.kt:300-352`, `XposedHelpers.java:894-899`, `XposedHelpers.java:447-455`.

```kotlin
ModuleHelper.hookAllMethods(
    "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow",
    lpparam.classLoader,
    "setFeedbackIcon",
    callback
)
```

- `ModuleHelper.hookAllMethods(className, classLoader, methodName, callback)` calls `XposedHelpers.findClassIfExists(className, classLoader)`.
- `findClassIfExists` first uses the supplied `ClassLoader`, then falls back to the application `ClassLoader` if the class is not found.
- If the class is found, `XposedHelpers.hookAllMethods(hookClass, methodName, callback)` iterates `hookClass.getDeclaredMethods()` and installs the callback on every method whose name is `setFeedbackIcon`.

### 4.2 Answered questions

| Question | Answer |
|---|---|
| Exact class resolution semantics | Class name string resolved through `findClassIfExists`, with `ClassLoader` cache and application `ClassLoader` fallback. If `findClassIfExists` returns `null`, `ModuleHelper.hookAllMethods` logs, records `TARGET_CLASS_MISSING`, and returns; no callback is installed. |
| Does `hookAllMethods` hook all overloads? | It hooks **all declared overloads** of `setFeedbackIcon` in `ExpandableNotificationRow`. Inherited `setFeedbackIcon` methods are **not** considered. |
| Does FAST need `setFeedbackIcon` parameter shape? | **No.** The callback never reads `setFeedbackIcon` arguments. The original method still runs through `chain.proceed()` with its original arguments. |
| Does FAST need `setFeedbackIcon` return type? | **No.** The callback returns the result of `chain.proceed()` or rethrows. The return type of `setFeedbackIcon` is not used for any custom decision. |
| Can the callback keep the original `hookAllMethods` surface? | **Yes.** Because arguments and return type are ignored, there is no correctness reason to replace `hookAllMethods` with an exact `Method`. |
| Any reason to change to an exact `Method`? | **No.** An exact `Method` would not add correctness or measurable benefit; it would risk missing overloads that the legacy path intentionally handles. |

### 4.3 A0 decision

Keep the legacy `ModuleHelper.hookAllMethods` installation. Do not change it for Architecture C.

---

## 5. `mOnKeyguard` FIELD ABI

### 5.1 Legacy semantics

`XposedHelpers.getBooleanField(thisObject, "mOnKeyguard")` uses:

- `findField(thisObject.getClass(), "mOnKeyguard")` — runtime-class-first hierarchy traversal.
- `Field.getBoolean(obj)`.
- `IllegalAccessException` → `IllegalAccessError`.
- `IllegalArgumentException` rethrown as-is.

### 5.2 Unknowns

| Unknown | Status | Note |
|---|---|---|
| Declaration root | `NOT_PROVEN` | The field may be declared in `ExpandableNotificationRow` or a superclass. |
| Field type | `NOT_PROVEN` | It may be `boolean` primitive, `Boolean` reference, or another type. The legacy `getBooleanField` will fail at runtime if `Field.getBoolean` cannot convert the value. |
| Shadowing | `NOT_PROVEN` | A subclass could declare a field named `mOnKeyguard` with a different type. The legacy path would see the subclass field because `findField` is runtime-class-first. |

### 5.3 FAST field contract

A conservative FAST `mOnKeyguard` field can be frozen under the following contract:

- **Resolution root:** `ExpandableNotificationRow` resolved at install time.
- **Field resolution:** `XposedHelpers.findField(resolutionRoot, "mOnKeyguard")`.
- **Type check at install:**
  - If `field.getType() == Boolean.TYPE`: use `Field.getBoolean(obj)`.
  - If `field.getType()` is anything else, including `Boolean.class`: resolver returns `null` for this field, disabling the FAST path.
- **FAST eligibility:** `thisObject != null && thisObject.javaClass === resolutionRoot && mOnKeyguardField != null`.
- **Reason for exact-class check:** If `thisObject` is a subclass that shadows `mOnKeyguard`, the frozen field from `resolutionRoot` would read the superclass field while the legacy path would read the subclass field. This is a semantic mismatch. Requiring `thisObject.javaClass === resolutionRoot` eliminates this ambiguity.

### 5.4 Failure semantics

| Failure | Legacy | FAST | Equivalent? | Fallback before FAST? | Fallback after FAST? |
|---|---|---|---|---|---|
| Field missing | `NoSuchFieldError` thrown by `findField` | Resolver sees miss → no FAST | `NOT_APPLICABLE` (no FAST) | `COMPLETE_LEGACY` | `N/A` |
| Wrong field type | `IllegalArgumentException` at callback time | Resolver rejects at install → no FAST | `NOT_APPLICABLE` | `COMPLETE_LEGACY` | `N/A` |
| `IllegalAccessException` | `IllegalAccessError` | `IllegalAccessError` | `YES` (with same mapping) | `N/A` | `NO` |
| `IllegalArgumentException` at read | `IllegalArgumentException` | `IllegalArgumentException` | `YES` | `N/A` | `NO` |
| `thisObject` class mismatch | legacy path handles subclass first | exact-root guard selects `COMPLETE_LEGACY` | `YES` | `COMPLETE_LEGACY` | `NO` |

### 5.5 A0 disposition

`mOnKeyguard` is **feasible as a frozen FAST field** under the exact-root, primitive-boolean-only contract above. It is the least risky FAST member because it is a read, not a mutation, and its target object is the hooked `thisObject`.

---

## 6. `getEntry` METHOD ABI

### 6.1 Legacy semantics

`XposedHelpers.callMethod(thisObject, "getEntry")` uses:

- `findMethodBestMatch(thisObject.getClass(), "getEntry")`.
- Zero-arg method resolution: exact then best-match, with superclass traversal and private-superclass-method skipping.
- `Method.invoke(thisObject, EMPTY_OBJECT_ARRAY)`.
- `InvocationTargetException` → `InvocationTargetError(cause)`.
- `IllegalAccessException` → `IllegalAccessError(message)`.

### 6.2 Strategy comparison

| Strategy | Description | Risk / benefit |
|---|---|---|
| **G1 — Freeze `getEntry` Method** | Resolve a zero-arg `getEntry` Method from `resolutionRoot` at install. At callback, invoke it on `thisObject` using an Xposed-compatible wrapper that preserves `InvocationTargetException` and `IllegalAccessException` mapping. | Removes per-callback `findMethodBestMatch`. Requires an exact-root guard to avoid subclass overload ambiguity. Requires a small invocation wrapper to preserve legacy error types. |
| **G2 — Retain `XposedHelpers.callMethod`** | Keep the legacy call exactly as-is. | Simpler, no wrapper risk, but keeps one `findMethodBestMatch` per callback. |

### 6.3 Direct invocation wrapper contract

If G1 is used, the invocation wrapper must behave exactly like `callMethod` for the resolved `Method`:

```text
try:
    return method.invoke(thisObject, EMPTY_OBJECT_ARRAY)
catch IllegalAccessException e:
    XposedHelpers.log(e)
    throw new IllegalAccessError(e.getMessage())
catch IllegalArgumentException e:
    throw e
catch InvocationTargetException e:
    throw new InvocationTargetError(e.getCause())
```

- The wrapper does **not** catch `OutOfMemoryError`, `ThreadDeath`, or `VirtualMachineError`; they propagate as in `callMethod`.
- The `Method` is already `setAccessible(true)` because `findMethodBestMatch` sets it.

### 6.4 A0 disposition

**G1 is conditionally feasible.** If a single zero-arg `getEntry` can be resolved from `resolutionRoot` (including superclasses) and `thisObject.javaClass === resolutionRoot`, a direct `Method.invoke` with the wrapper above is behavior-equivalent to `XposedHelpers.callMethod`. The return type is not needed for ABI freezing; it is only used for the next `mSbn` lookup.

If the resolver cannot find an unambiguous zero-arg `getEntry`, or if the overload set is not provably single, the resolver returns `null` and the path falls back to `COMPLETE_LEGACY`.

---

## 7. `mSbn` FIELD ABI

### 7.1 Legacy semantics

`XposedHelpers.getObjectField(entry, "mSbn")` uses:

- `findField(entry.getClass(), "mSbn")` — runtime-class-first.
- `Field.get(entry)`.
- Returns `Object`; the caller is the one who casts/uses it.

### 7.2 Unknowns

| Unknown | Status | Note |
|---|---|---|
| `getEntry` declared return type | `NOT_PROVEN` | The exact class returned by `getEntry` is not known from the callback source. |
| Actual runtime entry class | `NOT_PROVEN` | The object returned may be an instance of the declared return type or a subclass. |
| `mSbn` declaring class | `NOT_PROVEN` | The field may be in the entry class or a superclass. |
| `mSbn` field type | `NOT_PROVEN` | Likely `StatusBarNotification` or a superclass, but not proven. |
| Subclass shadowing | `NOT_PROVEN` | A subclass of the entry class could shadow `mSbn`. The legacy path would see the shadow because it starts at `entry.getClass()`. |

### 7.3 Strategy comparison

| Strategy | Description | Verdict |
|---|---|---|
| **S1 — Freeze `mSbn` Field** | Resolve `mSbn` from the declared return type of `getEntry` (or an inferred entry class). | **Rejected** for A0. It requires knowing the stable runtime entry class, or a per-row runtime-class check and fallback. That introduces the exact kind of dynamic per-instance cache risk the A0 forbids. |
| **S2 — Retain `XposedHelpers.getObjectField(entry, "mSbn")`** | Use the legacy helper on the actual `entry.getClass()` at every callback. | **Selected.** It preserves runtime-class-first semantics and requires no per-row cache or registry. |

### 7.4 A0 disposition

`mSbn` remains a **legacy `XposedHelpers.getObjectField`** call. It is not frozen in the FAST ABI.

---

## 8. `getPackageName` METHOD ABI

### 8.1 Legacy semantics

`XposedHelpers.callMethod(notification, "getPackageName") as String` uses:

- `findMethodBestMatch(notification.getClass(), "getPackageName")`.
- Zero-arg method resolution.
- `Method.invoke(notification, EMPTY_OBJECT_ARRAY)`.
- Returns `Object`, then cast to `String` by the callback.

### 8.2 Unknowns

| Unknown | Status | Note |
|---|---|---|
| `mSbn` actual runtime class | `NOT_PROVEN` | Could be `StatusBarNotification`, a subclass, or another compatible type. |
| `getPackageName` declaration root | `NOT_PROVEN` | Could be in the notification class or a superclass. |
| `getPackageName` overload set | `NOT_PROVEN` | Zero-arg is what the legacy uses, but other overloads may exist. |
| `getPackageName` return type | `NOT_PROVEN` | The callback casts to `String`; it could be `CharSequence`. |

### 8.3 Strategy comparison

| Strategy | Description | Verdict |
|---|---|---|
| **P1 — Freeze `getPackageName` Method** | Resolve `getPackageName` from a known class (e.g., the `mSbn` field type or a proven superclass). | **Rejected.** It requires a pinned notification class. If the actual `notification` object is a subclass with an overriding or overloaded `getPackageName`, the frozen Method would call the inherited or selected Method, not necessarily the same one `callMethod` would choose from `notification.getClass()`. Virtual dispatch handles overrides, but overload selection from a different root does not. |
| **P2 — Retain `XposedHelpers.callMethod(notification, "getPackageName")`** | Use the legacy helper on `notification.getClass()`. | **Selected.** It preserves the exact runtime overload selection and the same failure mapping. |

### 8.4 A0 disposition

`getPackageName` remains a **legacy `XposedHelpers.callMethod`** call. It is not frozen in the FAST ABI.

---

## 9. `setSystemExpanded` METHOD ABI

### 9.1 Legacy semantics

`XposedHelpers.callMethod(thisObject, "setSystemExpanded", true)` uses:

- `getParameterTypes(new Object[]{ true })` → `new Class<?>[] { Boolean.class }`.
- `findMethodBestMatch(thisObject.getClass(), "setSystemExpanded", Boolean.class)`.
- Exact-match first (`getDeclaredMethod` with `Boolean.class` on the runtime class), then best-match scan with autoboxing.
- `Method.invoke(thisObject, true)` — the `Boolean` argument is passed as `Boolean.TRUE` and unboxed if the method parameter is primitive `boolean`.
- `InvocationTargetException` → `InvocationTargetError(cause)`.
- `IllegalAccessException` → `IllegalAccessError(message)`.

### 9.2 Unknowns

| Unknown | Status | Note |
|---|---|---|
| Declaration root | `NOT_PROVEN` | The method may be in `ExpandableNotificationRow` or a superclass. |
| Parameter type | `NOT_PROVEN` | It may be `boolean` primitive, `Boolean` reference, or another compatible one-argument type. |
| Return type | `NOT_PROVEN` | The callback ignores the return. |
| Overload set | `NOT_PROVEN` | There may be multiple one-argument overloads; `findMethodBestMatch` will select the best match from `Boolean.class` actuals. |

### 9.3 Strategy comparison

| Strategy | Description | Verdict |
|---|---|---|
| **E1 — Freeze `setSystemExpanded` Method** | Resolve a one-argument `setSystemExpanded` Method from `resolutionRoot` using `findMethodBestMatch(resolutionRoot, "setSystemExpanded", Boolean.class)`. At callback, with `thisObject.javaClass === resolutionRoot`, invoke it with `true` using the Xposed-compatible wrapper. | **Selected conditionally.** It removes `findMethodBestMatch` per callback. The resolver uses the same selection algorithm as the legacy call when `thisObject.getClass() == resolutionRoot`. The wrapper preserves `InvocationTargetException` and `IllegalAccessException` mapping. |
| **E2 — Retain `XposedHelpers.callMethod(thisObject, "setSystemExpanded", true)`** | Keep the legacy call. | Simpler, no wrapper, but keeps one `findMethodBestMatch` and the `Object[]` / `Boolean` boxing per callback. |

### 9.4 Critical overload-selection note

A frozen `setSystemExpanded` Method must **not** use `findMethodExact(resolutionRoot, "setSystemExpanded", boolean.class)` unless it can be proven that the ROM method is exactly `void setSystemExpanded(boolean)`. The legacy call uses `findMethodBestMatch` with actual parameter type `Boolean.class`, which may select a `Boolean` formal overload over a `boolean` one if both exist.

The resolver must therefore use `XposedHelpers.findMethodBestMatch(resolutionRoot, "setSystemExpanded", Boolean.class)` to mimic the legacy selection. The resulting `Method` is the one the legacy path would select when `thisObject.getClass() == resolutionRoot`.

### 9.5 Direct invocation wrapper contract

Same as the `getEntry` wrapper, but with one `Object` argument:

```text
try:
    return method.invoke(thisObject, true)
catch IllegalAccessException e:
    XposedHelpers.log(e)
    throw new IllegalAccessError(e.getMessage())
catch IllegalArgumentException e:
    throw e
catch InvocationTargetException e:
    throw new InvocationTargetError(e.getCause())
```

### 9.6 A0 disposition

`setSystemExpanded` is **conditionally feasible as a frozen FAST Method** under the exact-root, `Boolean.class` best-match contract. Because it is the only custom ROM-side mutation, the resolver must fail-closed: if any ambiguity or missing method, `setSystemExpandedAbi = null` and the entire FAST path is unavailable.

---

## 10. STRATEGY COMPARISON

### 10.1 Strategy A — Full Frozen ABI

Frozen members:

- `mOnKeyguard` Field
- `getEntry` Method
- `mSbn` Field
- `getPackageName` Method
- `setSystemExpanded` Method

Problems:

- `mSbn` and `getPackageName` target objects whose runtime classes cannot be pinned to the resolution root. Freezing them requires either a per-row runtime-class cache or risky assumptions about the entry class and notification class. The A0 forbids the former and has no evidence for the latter.
- `getPackageName` overload selection depends on the actual `notification.getClass()`, which is `NOT_PROVEN`.
- `mSbn` shadowing depends on `entry.getClass()`, which is `NOT_PROVEN`.

**Verdict:** Too aggressive for A0. Rejected.

### 10.2 Strategy B — Conservative Partial FAST

Frozen members:

- `mOnKeyguard` Field (exact root, primitive `boolean` only)
- `getEntry` Method (exact root, zero-arg)
- `setSystemExpanded` Method (exact root, `Boolean.class` best-match)

Retained legacy:

- `mSbn` via `XposedHelpers.getObjectField(entry, "mSbn")`
- `getPackageName` via `XposedHelpers.callMethod(notification, "getPackageName")`

Benefits:

- Removes per-callback `findMethodBestMatch`/`findField` for the three members called on the hooked `thisObject`.
- Keeps dynamic helpers for objects whose runtime classes are not pinned to the resolution root.
- Preserves failure semantics with a small, local invocation wrapper for the two frozen methods.

Risks:

- The two frozen methods require an exact `thisObject.javaClass === resolutionRoot` guard. This may cause more `COMPLETE_LEGACY` callbacks on subclasses, but it guarantees behavior equivalence.
- The invocation wrapper must be kept in sync with `XposedHelpers.callMethod` error mapping.

**Verdict:** Feasible. This is the selected strategy.

### 10.3 Strategy C — Snapshot-Only / Minimal ABI

Frozen members:

- `mOnKeyguard` Field only (or none).

Retained legacy:

- All other reflection: `getEntry`, `mSbn`, `getPackageName`, `setSystemExpanded`.

Benefits:

- Maximum correctness safety.
- Minimal A0 surface.

Risks:

- Leaves most of the per-callback reflection overhead in place.
- Does not realize the full target-selection promise of reducing helper attempts.

**Verdict:** Feasible but too minimal. The snapshot publication mechanism is intentionally out of A0 scope, and the A0 does not need to commit to it. However, `mOnKeyguard`, `getEntry`, and `setSystemExpanded` can be frozen with low risk, so Strategy C is not selected.

---

## 11. SELECTED ABI STRATEGY

### 11.1 Final selection

| Item | Value |
|---|---|
| `C6_A0_SELECTED_ABI_STRATEGY` | `CONSERVATIVE_PARTIAL_FAST` (Strategy B) |
| `C6_A0_FROZEN_FAST_MEMBERS` | `mOnKeyguard` Field (primitive `boolean` only); `getEntry` Method (zero-arg); `setSystemExpanded` Method (one-arg, `Boolean.class` best-match) |
| `C6_A0_RETAINED_LEGACY_MEMBERS` | `mSbn` read via `XposedHelpers.getObjectField`; `getPackageName` call via `XposedHelpers.callMethod` |
| `C6_A0_EXACT_ROOT_POLICY` | `ExpandableNotificationRow` resolved at install time. At callback, `thisObject != null && thisObject.javaClass === resolutionRoot` is required before any FAST field or method access. |
| `C6_A0_FAST_TO_LEGACY_RETRY` | `FORBIDDEN_AFTER_FAST_BOUNDARY` |
| `C6_A0_COMPLETE_LEGACY_CONDITIONS` | ABI unavailable while hook target exists; `thisObject == null`; `thisObject.javaClass !== resolutionRoot`; any required FAST member missing/ambiguous; any other A0-rejected runtime shape. |

### 11.2 Resolver rules

1. Resolve the hook target class through a path compatible with `ModuleHelper.hookAllMethods` → `XposedHelpers.findClassIfExists(..., classLoader)` for `com.android.systemui.statusbar.notification.row.ExpandableNotificationRow`.
   - If the class is not found, `ModuleHelper.hookAllMethods` logs, records `TARGET_CLASS_MISSING`, and returns; **no `setFeedbackIcon` callback is installed**. FAST and `COMPLETE_LEGACY` dispatch are both `N/A`.
   - `XposedHelpers.findClass(...)` (throwing) must **not** be used for the install-time class probe, because that would convert the legacy log-and-return failure into a thrown install failure.
2. Only if the hook target class is found, the ABI resolver resolves `resolutionRoot` to the same class and proceeds to the FAST member checks below.
3. `mOnKeyguardField`:
   - `findField(resolutionRoot, "mOnKeyguard")`.
   - Inspect type. Accept only `Boolean.TYPE`; reject `Boolean.class` or any other type.
   - Non-primitive-boolean type → resolver miss.
4. `getEntryMethod`:
   - `findMethodBestMatch(resolutionRoot, "getEntry")` (zero arg).
   - If found and exactly one best match (no ambiguity), store it.
   - Miss or ambiguous → resolver miss.
5. `setSystemExpandedMethod`:
   - `findMethodBestMatch(resolutionRoot, "setSystemExpanded", Boolean.class)`.
   - If found and exactly one best match, store it.
   - If `setSystemExpanded(boolean)` is found, the `Method` parameter type is `boolean`; `Method.invoke` with `Boolean.TRUE` will unbox correctly.
   - If `setSystemExpanded(Boolean)` is found, the argument is passed as `Boolean.TRUE`.
   - Miss or ambiguous → resolver miss.
6. If any required FAST member is missing or incompatible, the resolver returns `null` ABI. Because the hook target class and `setFeedbackIcon` callback are already installed, the callback is still dispatched; the Effect selects `COMPLETE_LEGACY` before the FAST boundary.

### 11.3 Mode select (exact-root guard before any FAST work)

```text
if abi == null:
    COMPLETE_LEGACY()
if thisObject == null:
    COMPLETE_LEGACY()
if thisObject.javaClass !== abi.resolutionRootClass:
    COMPLETE_LEGACY()

// FAST boundary begins
mOnKeyguard = abi.readMOnKeyguard(thisObject)
if mOnKeyguard:
    result = chain.proceed()
    return result

entry = abi.getEntryMethod.invokeWithXposedSemantics(thisObject)
notification = XposedHelpers.getObjectField(entry, "mSbn")
pkgName = XposedHelpers.callMethod(notification, "getPackageName") as String

// preference values supplied by future snapshot (out of A0 scope)
opt = snapshot.expandMode
isSelected = snapshot.expandApps.contains(pkgName)

if (opt == 2 && !isSelected) || (opt == 3 && isSelected):
    abi.setSystemExpandedMethod.invokeWithXposedSemantics(thisObject, true)

result = chain.proceed()
return result
```

- `COMPLETE_LEGACY()` runs the exact original callback oracle.
- `invokeWithXposedSemantics` is the local wrapper that maps `InvocationTargetException` → `InvocationTargetError` and `IllegalAccessException` → `IllegalAccessError`, matching `XposedHelpers.callMethod`.

---

## 12. COMPLETE LEGACY FALLBACK BOUNDARY

### 12.1 Conditions

The Effect must run `COMPLETE_LEGACY` when any of the following is true:

- The `C6NotificationAutoExpandAbi` is `null` (resolver returned `null` while the hook target class exists and the callback is installed).
- `thisObject == null`.
- `thisObject.javaClass !== resolutionRoot`.
- The resolver could not resolve any required FAST member (`mOnKeyguard`, `getEntry`, or `setSystemExpanded`).

If the hook target class itself is missing, `ModuleHelper.hookAllMethods` returns without installing the callback, so neither `COMPLETE_LEGACY` nor FAST dispatch is applicable.

### 12.2 No retry after FAST boundary

- The FAST boundary begins when the first FAST operation (`mOnKeyguard` read) is performed.
- After the boundary, the Effect must **not** fall back to `COMPLETE_LEGACY`.
- If `getEntry`, `mSbn`, `getPackageName`, `setSystemExpanded`, or `chain.proceed()` throws, the callback rethrows the captured `Throwable` through `throwOrReturn`.
- `setSystemExpanded(true)` is the only custom ROM-side mutation. It must not be called twice. Therefore, the exact-root guard and ABI-non-null check must happen **before** `setSystemExpanded` is invoked, and no fallback may occur after `setSystemExpanded` has been invoked.

### 12.3 Legacy fallback is the only fallback

The Effect has exactly two paths:

1. `COMPLETE_LEGACY` — selected before any FAST work.
2. FAST path — selected when all guards pass. No per-member retry or mid-path fallback.

There is no "part FAST, part legacy" retry soup.

---

## 13. FAILURE / FATAL MATRIX

For each row:

- `LEGACY behavior` = what the current callback does.
- `FAST behavior` = what the proposed FAST path does.
- `Equivalent?` = `YES`, `NO`, or `NOT_PROVEN`.
- `Fallback before FAST?` = whether `COMPLETE_LEGACY` may be used before the FAST boundary.
- `Fallback after FAST?` = `NO` for all rows, because the FAST boundary is one-way.

| Condition | LEGACY behavior | FAST behavior | Equivalent? | Fallback before FAST? | Fallback after FAST? |
|---|---|---|---|---|---|
| Hook target class missing | `ModuleHelper.hookAllMethods` logs, records `TARGET_CLASS_MISSING`, and returns; no `setFeedbackIcon` callback is installed | Same as legacy; no callback is installed | `YES` | `N/A` | `N/A` |
| ABI resolver miss (hook target exists) | The callback is installed and runs the legacy oracle | Resolver returns `null`; the Effect selects `COMPLETE_LEGACY` before the FAST boundary | `YES` | `COMPLETE_LEGACY` | `N/A` |
| `mOnKeyguard` field missing | `NoSuchFieldError` | Resolver miss → `null` ABI | `NOT_APPLICABLE` | `COMPLETE_LEGACY` | `N/A` |
| `mOnKeyguard` wrong type | Legacy `Field.getBoolean` on a non-`boolean` field produces `IllegalArgumentException` | Resolver type check rejects → `null` ABI | `NOT_APPLICABLE` | `COMPLETE_LEGACY` | `N/A` |
| `mOnKeyguard` `IllegalAccessException` | `IllegalAccessError` | `IllegalAccessError` | `YES` | `N/A` | `NO` |
| `mOnKeyguard` `IllegalArgumentException` | `IllegalArgumentException` | `IllegalArgumentException` | `YES` | `N/A` | `NO` |
| `thisObject` class mismatch | legacy handles runtime class first | exact-root guard → `COMPLETE_LEGACY` | `YES` | `COMPLETE_LEGACY` | `NO` |
| `getEntry` missing | `NoSuchMethodError` | Resolver miss → `null` ABI | `NOT_APPLICABLE` | `COMPLETE_LEGACY` | `N/A` |
| `getEntry` `IllegalAccessException` | `IllegalAccessError` | wrapper → `IllegalAccessError` | `YES` | `N/A` | `NO` |
| `getEntry` `IllegalArgumentException` | `IllegalArgumentException` | wrapper → `IllegalArgumentException` | `YES` | `N/A` | `NO` |
| `getEntry` target throws | `InvocationTargetError(cause)` | wrapper → `InvocationTargetError(cause)` | `YES` | `N/A` | `NO` |
| `entry == null` | `getObjectField(null, "mSbn")` → NPE | `getObjectField(null, "mSbn")` → NPE | `YES` | `N/A` | `NO` |
| `mSbn` missing | `NoSuchFieldError` | legacy `getObjectField` → `NoSuchFieldError` | `YES` | `N/A` | `NO` |
| `mSbn` access failure | `IllegalAccessError` / `IllegalArgumentException` | legacy `getObjectField` → same | `YES` | `N/A` | `NO` |
| `notification == null` | `callMethod(null, "getPackageName")` → NPE | legacy `callMethod(null, ...)` → NPE | `YES` | `N/A` | `NO` |
| `getPackageName` missing | `NoSuchMethodError` | legacy `callMethod` → `NoSuchMethodError` | `YES` | `N/A` | `NO` |
| `getPackageName` target throws | `InvocationTargetError(cause)` | legacy `callMethod` → `InvocationTargetError(cause)` | `YES` | `N/A` | `NO` |
| Cast to `String` fails | `ClassCastException` | `as String` cast → `ClassCastException` | `YES` | `N/A` | `NO` |
| `setSystemExpanded` missing | `NoSuchMethodError` | Resolver miss → `null` ABI | `NOT_APPLICABLE` | `COMPLETE_LEGACY` | `N/A` |
| `setSystemExpanded` `IllegalAccessException` | `IllegalAccessError` | wrapper → `IllegalAccessError` | `YES` | `N/A` | `NO` |
| `setSystemExpanded` `IllegalArgumentException` | `IllegalArgumentException` | wrapper → `IllegalArgumentException` | `YES` | `N/A` | `NO` |
| `setSystemExpanded` target throws | `InvocationTargetError(cause)` | wrapper → `InvocationTargetError(cause)` | `YES` | `N/A` | `NO` |
| `setSystemExpanded` partial mutation | Not rolled back by legacy | Not rolled back by FAST | `YES` | `N/A` | `NO` |
| `chain.proceed()` throws | captured and rethrown by `throwOrReturn` | captured and rethrown by `throwOrReturn` | `YES` | `N/A` | `NO` |
| `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError` | caught by callback `catch (t: Throwable)` and rethrown | same; wrapper and helpers do not catch them | `YES` | `N/A` | `NO` |

### 13.1 Fatal note

The FAST invocation wrapper and the retained `XposedHelpers` helpers must **not** introduce a catch block that swallows `OutOfMemoryError`, `ThreadDeath`, or `VirtualMachineError`. The current helpers do not; the wrapper must mimic that.

---

## 14. LIFECYCLE / OWNERSHIP

| Artifact | Owner | Content / scope |
|---|---|---|
| `C6NotificationAutoExpandAbi` (conceptual) | Install-time immutable. | `resolutionRootClass`, `mOnKeyguard` field + reader, `getEntry` Method, `setSystemExpanded` Method, all set accessible. |
| Future `C6NotificationAutoExpandEffect` (conceptual) | Hook-local `val` captured by the installed `MethodHook`. | Holds the ABI reference and the future snapshot/config source. No per-instance mutable state. No `View`/`Context`/`Activity`/row retention. |
| `ExpandableNotificationRow` instance | Callback-local only. | Borrowed through `chain.thisObject`; never retained. |
| `entry` object | Callback-local only. | Used for `mSbn` read; not retained. |
| `notification` object | Callback-local only. | Used for `getPackageName`; not retained. |
| Future snapshot/config | To be decided in B1; A0 does not select publication mechanism. | Will supply `opt` and `isSelected` values; no owner is decided here. |

No `OwnedSlot`, `GenerationOwner`, `WeakReference` cache, per-row cache, per-view cache, generation registry, or lifecycle retention is introduced in A0.

---

## 15. HOT-PATH COST MODEL

### 15.1 Preserved target-selection facts

| Fact | Value |
|---|---|
| `PREFERENCE_READS_PER_CALLBACK` | 0 if `mOnKeyguard`, else 2 (`getString` + `getStringSet`) |
| `REFLECTION_HELPER_ATTEMPTS_PER_CALLBACK` (legacy) | 1–5 (see `SystemNotificationHooks.kt:52-59`) |
| `STRUCTURALLY_PROVEN_PER_CALLBACK_ALLOCATION` | `none` in the callback source |
| `REAL_CALLBACK_FREQUENCY` | `NOT_PROVEN` |

### 15.2 Expected Architecture C reduction under Strategy B

| Branch | Legacy operations | Strategy B FAST operations |
|---|---|---|
| `mOnKeyguard == true` | 1 `getBooleanField`, 0 pref reads | 1 `Field.getBoolean`, 0 pref reads |
| `mOnKeyguard == false, predicate false` | `getBooleanField` + `getEntry` + `getObjectField(mSbn)` + `getPackageName` + 2 pref reads | `Field` read + `Method.invoke(getEntry)` + `getObjectField(mSbn)` + `callMethod(getPackageName)` + snapshot reads |
| `mOnKeyguard == false, predicate true` | same + `setSystemExpanded` | same + `Method.invoke(setSystemExpanded)` |

Reduction:

- `mOnKeyguard`, `getEntry`, `setSystemExpanded` move from per-callback `findField`/`findMethodBestMatch` cache lookups to direct `Field.get` / `Method.invoke`.
- `mSbn` and `getPackageName` remain on the legacy path because their target object classes are not pinned.
- Preference reads move out of the callback into a future B1 snapshot; A0 does not define the publication mechanism.

No fixed per-callback operation count is claimed; actual savings are conditional on the FAST branch and the runtime shape.

---

## 16. FUTURE B1 TEST PLAN (specified, not implemented)

### 16.1 Resolver

- `ExpandableNotificationRow` resolves at install.
- `mOnKeyguard` field resolves with `Boolean.TYPE` only.
- Any other `mOnKeyguard` type, including `Boolean.class` → resolver returns `null`.
- Missing `mOnKeyguard` → resolver returns `null`.
- Zero-arg `getEntry` resolves from root or superclass.
- Missing or ambiguous `getEntry` → resolver returns `null`.
- One-arg `setSystemExpanded` resolves with `Boolean.class` best-match.
- Missing or ambiguous `setSystemExpanded` → resolver returns `null`.
- Resolver fatal failure propagates.

### 16.2 Exact-root guard

- `thisObject == null` → `COMPLETE_LEGACY`.
- `thisObject.javaClass === resolutionRoot` → FAST allowed.
- `thisObject.javaClass` is a subclass → `COMPLETE_LEGACY`.

### 16.3 Fast invocation wrapper

- `IllegalAccessException` → `IllegalAccessError`.
- `InvocationTargetException` → `InvocationTargetError(cause)`.
- `IllegalArgumentException` rethrown as-is.
- `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError` propagate through the wrapper.

### 16.4 Failure boundaries

- Custom work failure prevents `chain.proceed()`.
- `setSystemExpanded` failure prevents `chain.proceed()`.
- No fallback after `mOnKeyguard` read.
- `setSystemExpanded` not called twice.

### 16.5 Snapshot / Config

- The A0 does not decide `AtomicReference`, `Volatile`, `refreshLock`, `observer threading`, `ChangeCoalescer`, `Flow`, or `coroutine scope`.
- B1 will define how `system_expandnotifs` and `system_expandnotifs_apps` are published to the Effect without re-introducing per-row state.

---

## 17. EVIDENCE / NOT PROVEN MATRIX

| Item | Classification | Source / note |
|---|---|---|
| `ExpandNotificationsHook.intercept` callback order | `STRUCTURAL` | `SystemNotificationHooks.kt:43-69` |
| `chain.thisObject` and `chain.proceed()` semantics | `STRUCTURAL` | `HookerClassHelper.kt:167-201`, `SystemNotificationHooks.kt:49,62` |
| `MethodHook` broad `catch (t: Throwable)` and `throwOrReturn` | `STRUCTURAL` | `SystemNotificationHooks.kt:63-67`, `XposedHelpers.java:109-112` |
| `XposedHelpers.findField` runtime-class-first, superclass traversal, cache | `STRUCTURAL` | `XposedHelpers.java:515-572` |
| `XposedHelpers.getBooleanField` uses `Field.getBoolean` | `STRUCTURAL` | `XposedHelpers.java:1385-1395` |
| `XposedHelpers.getObjectField` uses `Field.get` with runtime class | `STRUCTURAL` | `XposedHelpers.java:1362-1372` |
| `XposedHelpers.callMethod` zero-arg uses `findMethodBestMatch` + `Method.invoke` | `STRUCTURAL` | `XposedHelpers.java:1788-1800, 771-787` |
| `XposedHelpers.callMethod` one-arg uses `getParameterTypes` + `findMethodBestMatch` | `STRUCTURAL` | `XposedHelpers.java:1802-1814, 847-849, 905-912` |
| `findMethodBestMatch` `ClassUtils.isAssignable(..., true)` autoboxing | `STRUCTURAL` | `XposedHelpers.java:815-818` |
| `findMethodBestMatch` skips private methods of superclasses | `STRUCTURAL` | `XposedHelpers.java:810-812` |
| `ModuleHelper.hookAllMethods` hooks declared methods by name only | `STRUCTURAL` | `ModuleHelper.kt:300-352`, `XposedHelpers.java:894-899` |
| `InvocationTargetError extends Error` | `STRUCTURAL` | `XposedHelpers.java:1903-1912` |
| `mOnKeyguard` field type | `NOT_PROVEN` | resolver must check `Field.getType()` |
| `mOnKeyguard` declaration root | `NOT_PROVEN` | may be in `ExpandableNotificationRow` or superclass |
| `getEntry` return type / runtime entry class | `NOT_PROVEN` | used only for next `mSbn` lookup; not frozen |
| `mSbn` declaring class and type | `NOT_PROVEN` | kept on legacy path |
| `getPackageName` declaration root and overload set | `NOT_PROVEN` | kept on legacy path |
| `setSystemExpanded` parameter type and overload set | `NOT_PROVEN` | resolver uses `findMethodBestMatch(resolutionRoot, "setSystemExpanded", Boolean.class)` to mimic legacy |
| `setSystemExpanded` return type | `NOT_PROVEN` | ignored by callback |
| `setFeedbackIcon` parameter shape | `NOT_PROVEN` | callback does not need it |
| `setFeedbackIcon` return type | `NOT_PROVEN` | callback does not need it |
| Real callback timing / thread / frequency | `NOT_RUNTIME_TESTED_CALLBACK` | no real device evidence |
| Start gate / validation commands | `LOCAL_EXECUTION_EVIDENCE_ONLY` | executed locally during this A0 preflight |


---

## 18. SCOPE FREEZE

### 18.1 In scope

- `ExpandNotificationsHook` callback oracle.
- `ExpandableNotificationRow.setFeedbackIcon` hook surface and installation semantics.
- ABI preflight for: `mOnKeyguard`, `getEntry`, `mSbn`, `getPackageName`, `setSystemExpanded`.
- Strategy A / B / C comparison.
- Failure/fatal matrix.
- Exact-root policy and `COMPLETE_LEGACY` boundary.

### 18.2 Out of scope

- Production implementation of `Resolver`, `Abi`, `Effect`, `Snapshot`, `RuntimeState`, or `Hook`.
- Preference observer / `AtomicReference` / `refreshLock` / `ChangeCoalescer` / `Flow` / coroutine scope decisions.
- C1-C5 production or documentation changes.
- `MaxNotificationIconsHook`, `BetterPopupsAllowFloatHook`, `NotificationImportanceHook`, `QSHapticHook`, `DrawerBlurRatioHook`.
- ROM binary / decompilation beyond source inspection.

---

## 19. A0 OUTCOME

| Item | Value |
|---|---|
| `C6_A0_FEASIBILITY` | `PASS` |
| `C6_A0_SELECTED_ABI_STRATEGY` | `CONSERVATIVE_PARTIAL_FAST` (Strategy B) |
| `C6_A0_FROZEN_FAST_MEMBERS` | `mOnKeyguard` Field (primitive `boolean` only); `getEntry` Method (zero-arg, Xposed-compatible wrapper); `setSystemExpanded` Method (one-arg, `Boolean.class` best-match, Xposed-compatible wrapper) |
| `C6_A0_RETAINED_LEGACY_MEMBERS` | `mSbn` via `XposedHelpers.getObjectField`; `getPackageName` via `XposedHelpers.callMethod` |
| `C6_A0_EXACT_ROOT_POLICY` | `ExpandableNotificationRow` resolved at install; callback requires `thisObject.javaClass === resolutionRoot` |
| `C6_A0_COMPLETE_LEGACY_CONDITIONS` | ABI unavailable while hook target exists; `thisObject == null`; class mismatch; any required FAST member missing/ambiguous; runtime shape not covered by the FAST contract |
| `C6_A0_FAST_TO_LEGACY_RETRY` | `FORBIDDEN_AFTER_FAST_BOUNDARY` |
| `C6_A0_RUNTIME_CALLBACK_EVIDENCE` | `NOT_RUNTIME_TESTED_CALLBACK` |
| `C6_A0_PRODUCTION_NOT_STARTED` | `true` |

The preflight freezes a conservative, fail-closed ABI contract:

- The exact legacy callback oracle is preserved.
- The `mOnKeyguard` field can be frozen with an exact-root, primitive `boolean` reader; `Boolean.class` is rejected.
- `getEntry` and `setSystemExpanded` can be frozen as direct `Method` invocations with a small Xposed-compatible error-mapping wrapper, because they are called on the exact root object.
- `mSbn` and `getPackageName` remain on the legacy `XposedHelpers` path because their runtime object classes cannot be pinned to the resolution root without per-row caches or unproven ROM assumptions.
- The hook installation surface (`ModuleHelper.hookAllMethods`) is not changed; it uses `findClassIfExists` and returns without installing a callback if the target class is missing.
- If the hook target class is missing, no `setFeedbackIcon` callback is installed and neither FAST nor `COMPLETE_LEGACY` dispatch applies.
- `COMPLETE_LEGACY` is the only fallback when the hook target class exists and the callback is installed; it is selected before any FAST work begins.
- No retry or fallback is allowed after the FAST boundary.
- No production, test, `Resolver`, `Abi`, `Effect`, `Snapshot`, `RuntimeState`, or `Hook` has been created or modified.

---

## 20. VALIDATION

| Command | Result | Evidence |
|---|---|---|
| `git diff --check` | pass | exit `0` |
| `git diff --name-only 8001f972e194bd388341c45f7064175cbcb27560..HEAD` | `docs/architecture-c/C6_NOTIFICATION_AUTO_EXPAND_A0_PREFLIGHT.md` only | local command |
| `git diff 8001f972e194bd388341c45f7064175cbcb27560..HEAD -- app/src/main` | empty | local command |
| `git diff 8001f972e194bd388341c45f7064175cbcb27560..HEAD -- app/src/test` | empty | local command |
| `git status --short` | `?? docs/architecture-c/C6_NOTIFICATION_AUTO_EXPAND_A0_PREFLIGHT.md` | pre-commit state |

All validation results are `LOCAL_EXECUTION_EVIDENCE_ONLY`.

---

## 21. SUBMISSION FIELDS

| Field | Value |
|---|---|
| `C6_A0_BASE` | `8001f972e194bd388341c45f7064175cbcb27560` |
| Branch | `devin/a14-architecture-c-r14.20.0` |
| Final SHA | *(to be recorded after commit and push)* |
| Changed files | `docs/architecture-c/C6_NOTIFICATION_AUTO_EXPAND_A0_PREFLIGHT.md` |
| Production changed | `false` |
| Tests changed | `false` |
| C1 changed | `false` |
| C2 changed | `false` |
| C3 changed | `false` |
| C4 changed | `false` |
| C5 changed | `false` |
| C6 target-selection doc changed | `false` |
| C6 A0 doc changed | `true` |
| `GITHUB_CI_STATUS` | `NONE` |
| `GITHUB_WORKFLOW_RUNS` | `NONE` |

---

```text
C6_A0_BASE = 8001f972e194bd388341c45f7064175cbcb27560
C6_A0_SELECTED_ABI_STRATEGY = CONSERVATIVE_PARTIAL_FAST
C6_A0_FROZEN_FAST_MEMBERS = mOnKeyguard Field (primitive boolean only); getEntry Method; setSystemExpanded Method
C6_A0_RETAINED_LEGACY_MEMBERS = mSbn getObjectField; getPackageName callMethod
C6_A0_EXACT_ROOT_POLICY = ExpandableNotificationRow resolved at install; exact thisObject class match required
C6_A0_COMPLETE_LEGACY_CONDITIONS = ABI unavailable while hook target exists; thisObject null; class mismatch; any required FAST member missing/ambiguous; unknown runtime shape
C6_A0_FAST_TO_LEGACY_RETRY = FORBIDDEN_AFTER_FAST_BOUNDARY
C6_A0_RUNTIME_CALLBACK_EVIDENCE = NOT_RUNTIME_TESTED_CALLBACK
C6_A0_PRODUCTION_NOT_STARTED = true
C6_A0_FEASIBILITY = PASS

C6_A0_READY_FOR_INDEPENDENT_AUDIT
C6_PRODUCTION_NOT_STARTED
```
