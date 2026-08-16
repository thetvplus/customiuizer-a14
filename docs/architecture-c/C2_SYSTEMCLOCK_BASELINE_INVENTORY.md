# C2  - Architecture C SystemClock Fact Audit

**Repository:** `tomthenpc/customiuizer-a14`
**Branch:** `devin/a14-architecture-c-r14.20.0`
**Scope:** `SystemClockHooks.kt` H1 (`MiuiClock` / `MiuiStatusBarClock.updateTime`) and H2 (`SecondTicker.run`) only
**Non-scope:** `CCClockTweakHook`, `CCClockCenterAlignHook`, `FakeStatusBarClockController`, weather internals, NetSpeed

> C2-A0 is a docs-only artifact. No production code is implemented in this commit.

---

## C2.1 Identity

- **C2 base:** `13b8927bc2be6c998fefab6febff489a8c0d0acc`
- **C1 final audited code/doc:** `6dcd00e8f7a367d8706242fe345b0114f7fb3481`
- **C1 oracle (r14.18.8):** `2c4efeafc8655855b824b72ecbf6106641b04a8e`
- **C1 device gate:** `C1_DEVICE_GATE_ENVIRONMENT_BLOCKED` (deferred, not a product failure)
- **C2 target feature:** `StatusBarClockTweak` (`SystemClockHooks.StatusBarClockTweakHook`)

---

## C2.2 Why SystemClock

`SystemClock` was chosen to prove that Architecture C is reusable in a subsystem that is materially different from `StatusBarHeight`:

| Dimension | StatusBarHeight (C1) | SystemClock (C2) |
|-----------|----------------------|------------------|
| Process | `system_server` | `com.android.systemui` |
| Lifecycle | InsetsSource frame / WindowState identity | periodic second tick / controller-view lifecycle |
| Hot unit | geometry rewrite per InsetsSource frame | text formatting per second, style apply per change |
| Config driver | single int `system_statusbarheight` | many string/boolean clock style preferences |
| Existing good state | `StatusBarHeightConfig` snapshot | `ClockStyleSnapshot` + `SecondTicker` + `ThreadLocal` builders |

C2 must **preserve** the existing clock optimizations and **remove the remaining runtime reflection / generic Xposed access from the per-second path**.

Existing good properties that C2 must not destroy:

- `ClockStyleSnapshot` is an immutable data class.
- `buildClockText` and `initClockStyle` public overloads read **no** `mPrefs`.
- `ThreadLocal<StringBuilder>` (`clockFormatBuilder`, `clockTextBuilder`) removes string churn.
- `SecondTicker` uses a `WeakReference<controller>` and `MAX_CLOCK_LISTENERS` in the style-refresh path.
- `showStatusBarSeconds` / `showCCSeconds` are pre-computed into the snapshot.

---

## C2.3 H1  - updateTime Current Graph

Hook installed by `SystemClockHooks.StatusBarClockTweakHook`:

```text
ModuleHelper.findAndHookMethod(
    "com.android.systemui.statusbar.views.MiuiClock",
    lpparam.classLoader,
    "updateTime",
    updateTimeHook
)
ModuleHelper.findAndHookMethod(
    "com.android.systemui.statusbar.views.MiuiStatusBarClock",
    lpparam.classLoader,
    "updateTime",
    updateTimeHook
)
```

`updateTimeHook` is a `MethodHook(XposedInterface.PRIORITY_HIGHEST)`.

### H1 exact control flow

```text
1. clock = chain.thisObject as TextView

2. clockName = ModuleHelper.getViewInfo(clock, "clockName") as String?

3. Hidden-date / hidden-clock short-circuit:
   if ("ccDate"   == clockName && hideDateView)   clock.text = ""; return null
   if ("drawerDate" == clockName && hideDrawerDate) clock.text = ""; return null
   if ("clock"    == clockName && hideStatusbarClock) clock.text = ""; return null

4. snapshot = currentClockStyleSnapshot()
              ?: ensureClockStyleSnapshot(clock.context.resources)

5. mMiuiStatusBarClockController =
      XposedHelpers.getObjectField(clock, "mMiuiStatusBarClockController")

6. mCalendar =
      XposedHelpers.getObjectField(mMiuiStatusBarClockController, "mCalendar")

7. timeFmt = buildClockText(
       clockName,
       snapshot,
       WeatherDataController.weatherInfo,
       statusbarClockTweak,
       ccClockTweak,
   )

8. if (timeFmt != null):
   a. formatSb = clockFormatBuilder.get()    // ThreadLocal
   b. textSb   = clockTextBuilder.get()      // ThreadLocal
   c. formatSb.setLength(0); formatSb.append(timeFmt)
   d. textSb.setLength(0)
   e. XposedHelpers.callMethod(
          mCalendar, "format",
          clock.context, textSb, formatSb)
   f. clock.text = textSb.toString()
   g. skipped = true; result = null; throwable = null

9. if (skipped) return XposedHelpers.throwOrReturn(throwable, result)
   result = chain.proceed()

10. return XposedHelpers.throwOrReturn(throwable, result)
```

### H1 fallback / identity cases

| Case | Current behavior |
|------|------------------|
| `clockName == null` | No hidden-date match; `buildClockText(...)` returns `null` (first `when` branch is `clockName == null -> return null` in `buildClockText`? No, `buildClockText` checks `if (clockName == null) return null` then `when`); falls through to `chain.proceed()` original. |
| `clockName` is one of the hidden names with matching hide flag | `clock.text = ""`, original **not** called. |
| `timeFmt == null` | Original `chain.proceed()` is called. |
| Snapshot is `null` on first `updateTime` | `ensureClockStyleSnapshot(...)` builds from `MainModule.mPrefs`. This is the only path where H1 can currently read `mPrefs`. |
| `XposedHelpers.callMethod(mCalendar, "format", ...)` throws | `throwOrReturn` rethrows; original is **not** called. |
| Any reflection failure in H1 | Outer `catch (t: Throwable)` stores it and `throwOrReturn` rethrows. Original is not called. No logging. |
| `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError` | Caught by the outer `Throwable` catch and rethrown by `throwOrReturn`. |

### H1 remaining generic operations

| Operation | Type | Why it is in the per-tick path |
|-----------|------|--------------------------------|
| `ModuleHelper.getViewInfo(clock, "clockName")` | D  - string-keyed view tag map | Identifies which logical clock this view is. |
| `XposedHelpers.getObjectField(clock, "mMiuiStatusBarClockController")` | A  - cold-resolvable field | Reads the clock's controller reference. |
| `XposedHelpers.getObjectField(controller, "mCalendar")` | A  - cold-resolvable field | Reads the calendar object for formatting. |
| `XposedHelpers.callMethod(calendar, "format", ...)` | B  - cold-resolvable method | Performs the actual formatted text generation. |

No `mPrefs` read in steady state; the only hot `mPrefs` exposure is `currentClockStyleSnapshot() ?: ensureClockStyleSnapshot(...)`, which is only executed if the snapshot has not yet been built (cold/one-time).

---

## C2.4 H2  - SecondTicker.run Current Graph

`SecondTicker` is a private inner class of `SystemClockHooks`. One instance is stored as an additional-instance field on `MiuiStatusBarClockController`.

### H2 exact control flow

```text
1. if (!running) return

2. controller = clockControllerRef.get()
   if (controller == null) { dispose(); return }

3. ModuleHelper.guarded {
4.   calendar = XposedHelpers.getObjectField(controller, "mCalendar")
5.   XposedHelpers.callMethod(
          calendar, "setTimeInMillis",
          java.lang.System.currentTimeMillis())
6.   XposedHelpers.setObjectField(
          controller, "mIs24",
          DateFormat.is24HourFormat(context))
7.   clockListeners =
          XposedHelpers.getObjectField(controller, "mClockListeners")
          as ArrayList<Any>
8.   for (listener in clockListeners) {
        clock = listener as View
        if (ModuleHelper.getViewInfo(clock, "showSeconds") != null) {
            XposedHelpers.callMethod(clock, "updateTime")
        }
      }
   }

9. scheduleNextTick()
```

### H2 remaining generic operations

| Operation | Type | Why it is in the per-second path |
|-----------|------|----------------------------------|
| `XposedHelpers.getObjectField(controller, "mCalendar")` | A  - cold-resolvable field | Gets calendar for `setTimeInMillis`. |
| `XposedHelpers.callMethod(calendar, "setTimeInMillis", ...)` | B  - cold-resolvable method | Advances calendar time. |
| `XposedHelpers.setObjectField(controller, "mIs24", ...)` | A  - cold-resolvable field | Syncs 24-hour user setting. |
| `XposedHelpers.getObjectField(controller, "mClockListeners")` | A  - cold-resolvable field | Gets the list of clocks to refresh. |
| `ModuleHelper.getViewInfo(clock, "showSeconds")` | D  - string-keyed view tag map | Filters which clocks need per-second refresh. |
| `XposedHelpers.callMethod(clock, "updateTime")` | B  - cold-resolvable method | Triggers the hooked `updateTime` for custom format. |

### H2 ownership / lifecycle

```text
MiuiStatusBarClockController constructor (main thread)
  -> StatusBarClockTweakHook scheduleHook.intercept()
       -> initSecondTicker(thisObject, statusbarClockTweak, ccClockTweak)
            -> SecondTicker(controller, mContext, showStatusBarSeconds, showCCSeconds)
            -> setAdditionalInstanceField(controller, "secondTicker", ticker)
            -> ticker.start()
                 -> ScreenStateController.addListener(context, this)
                 -> scheduleNextTick()

Preference change (remote-prefs listener thread)
  -> observer.onChange(key)
  -> handler.post { ... initSecondTicker(controller, ...) ... }

TIME_SET broadcast
  -> clockTimeSetReceiver.onReceive
  -> ModuleHelper.guarded { initSecondTicker(owner, ...) }

Screen off
  -> onScreenStateChanged(false)
  -> stop()

Screen on
  -> onScreenStateChanged(true)
  -> start()  (if controller ref is null -> dispose())

Controller GC
  -> next run() sees null controller
  -> dispose()
```

### H2 strong / weak references

| Reference | Kind | Notes |
|-----------|------|-------|
| `clockControllerRef` | `WeakReference<Any>` | Ticker does not pin the controller. |
| `SecondTicker` in `additionalFields` | strong value, weak key | `XposedHelpers` global map holds the ticker strongly; weak key is the controller. |
| `ScreenStateController.listeners` | strong | Holds the `SecondTicker` while registered. |
| `SecondTicker.context` | strong | The controller's `mContext`; used for `DateFormat.is24HourFormat` and `Handler` looper. |
| `SecondTicker.handler` | strong | `Handler(context.mainLooper)`. |
| `SecondTicker` (this) in `handler.postDelayed(this, delay)` | strong via `Message.callback` | Removed by `handler.removeCallbacks(this)` in `stop()`. |
| `mClockListeners` list | held by controller | SecondTicker reads it each tick but does not retain it. |

Strong view retention: **NO** (ticker does not hold the clock views).
Strong controller retention: **NO** (WeakReference).
Context retention: **YES** (`SecondTicker.context` is the controller's `mContext`, held until `dispose()` / GC).

### H2 `mClockListeners` bound

`MAX_CLOCK_LISTENERS = 64` is defined and used **only** in the preference-observer style-refresh loop:

```kotlin
val count = minOf(clockListeners.size, MAX_CLOCK_LISTENERS)
for (i in 0 until count) { ... }
```

In `SecondTicker.run` and `initSecondTicker`, the loop is **unbounded**:

```kotlin
for (listener in clockListeners) { ... }
```

Current classification: **UNBOUNDED** in H2. A C2 implementation should consider whether the bound should also be enforced in the per-second ticker and, if so, how to handle listeners beyond the bound.

---

## C2.5 Periodic Generic Operations Table

### H1 `updateTime`

| # | Operation | Class | Classification | Rationale |
|---|-----------|-------|----------------|-----------|
| H1-O1 | `ModuleHelper.getViewInfo(clock, "clockName")` | `View` tag map | D  - lifecycle state / view tag | Logical clock identity is already stored per view. |
| H1-O2 | `XposedHelpers.getObjectField(clock, "mMiuiStatusBarClockController")` | `MiuiClock` / `MiuiStatusBarClock` | A  - cold-resolvable field | Controller is an object field on the clock. |
| H1-O3 | `XposedHelpers.getObjectField(controller, "mCalendar")` | `MiuiStatusBarClockController` | A  - cold-resolvable field | Calendar is an object field on the controller. |
| H1-O4 | `XposedHelpers.callMethod(calendar, "format", ctx, sb, sb)` | calendar class (declared type from `mCalendar` field; runtime `javaClass` fallback documented) | B  - cold-resolvable method | The only formatting call. |

### H2 `SecondTicker.run`

| # | Operation | Class | Classification | Rationale |
|---|-----------|-------|----------------|-----------|
| H2-O1 | `XposedHelpers.getObjectField(controller, "mCalendar")` | `MiuiStatusBarClockController` | A  - cold-resolvable field | Calendar object. |
| H2-O2 | `XposedHelpers.callMethod(calendar, "setTimeInMillis", time)` | calendar class | B  - cold-resolvable method | Advances calendar. |
| H2-O3 | `XposedHelpers.setObjectField(controller, "mIs24", is24)` | `MiuiStatusBarClockController` | A  - cold-resolvable field | Keeps 24h flag current for original code path. |
| H2-O4 | `XposedHelpers.getObjectField(controller, "mClockListeners")` | `MiuiStatusBarClockController` | A  - cold-resolvable field | Clock listener list. |
| H2-O5 | `ModuleHelper.getViewInfo(clock, "showSeconds")` | `View` tag map | D  - lifecycle state / view tag | Per-second filter. |
| H2-O6 | `XposedHelpers.callMethod(clock, "updateTime")` | `MiuiClock` / `MiuiStatusBarClock` | B  - cold-resolvable method | Re-enters the H1 hook. |

All operations are A, B, or D. There are **no E** (must remain dynamic) operations in the periodic path if class/method/field names can be resolved once at install time.

---

## C2.6 Cold Class / Member Inventory

### Classes involved

| Source-level name | Usage | Resolution source |
|-------------------|-------|-------------------|
| `com.android.systemui.statusbar.policy.MiuiStatusBarClockController` | Constructor hook; owner of `mContext`, `mCalendar`, `mClockListeners`, `mIs24` | `ModuleHelper.hookAllConstructors(...)` at install |
| `com.android.systemui.statusbar.views.MiuiClock` | `updateTime` hook target; has `mMiuiStatusBarClockController` | `ModuleHelper.findAndHookMethod(...)` at install |
| `com.android.systemui.statusbar.views.MiuiStatusBarClock` | `updateTime` hook target (possibly a subclass) | `ModuleHelper.findAndHookMethod(...)` at install |
| calendar class | `setTimeInMillis` / `format` target | **primary: `mCalendar` declared field type**; runtime `calendarObject.javaClass` fallback only for constructor-time calibration |

### Primary and fallback `mCalendar` type authority

Do **not** hardcode `java.util.Calendar` or a guessed Xiaomi calendar class.

**Primary cold declared-type authority:**

```text
calendarField = MiuiStatusBarClockController.getDeclaredField("mCalendar")
calendarDeclaredClass = calendarField.type
```

The `format` and `setTimeInMillis` methods are first resolved deterministically on `calendarDeclaredClass` (and its superclasses).

**Constructor-time / first-real-calendar fallback:**

The legacy oracle resolves `XposedHelpers.callMethod` on the **runtime class** of the calendar object (`obj.getClass()`). If deterministic cold resolution against `calendarDeclaredClass` is incomplete, a narrowly scoped, one-time fallback may use:

```text
calendarObject = calendarField.get(controller)
resolvedClass = calendarObject.javaClass
```

The result must be published as an immutable, final method reference. No per-tick lookup is allowed.

This fallback is allowed **only** for calendar capability resolution. Do not build a generic late-ABI system.

### Superclass relationships

- `MiuiStatusBarClock` may extend `MiuiClock` (same package, similar name pattern in HyperOS 1). If so, hooking both may mean the inherited `updateTime` is hooked once per class; the second hook on the subclass is a no-op or an override hook.
- `MiuiClock` likely extends Android `TextView`.
- `MiuiStatusBarClockController` is an independent controller class.

---

## C2.7 Candidate Frozen ABI

A C2 Architecture C implementation would resolve the following members once at install time and store them in an ABI object.

### Clock target capabilities

```text
ClockTargetCapability (one per resolved hook target):
  targetClass: Class<*>
    - MiuiClock class
    - MiuiStatusBarClock class
  controllerField: Field
    owner: targetClass
    name: "mMiuiStatusBarClockController"
  updateTimeMethod: Method
    owner: targetClass
    name: "updateTime"
    parameter count: 0
```

Do **not** assume `MiuiStatusBarClock` inherits the same `mMiuiStatusBarClockController` or `updateTime` declaration from `MiuiClock`. Shared members may point to the same `Field` / `Method` only when inheritance proves that safe. The resolver validates per target.

### Core periodic controller ABI

```text
controllerClass: Class<*>
  name: "com.android.systemui.statusbar.policy.MiuiStatusBarClockController"
calendarField: Field
  name: "mCalendar"
clockListenersField: Field
  name: "mClockListeners"
is24Field: Field
  name: "mIs24"
```

`mContext` is **not** part of the H1/H2 core periodic ABI. It is read on the lifecycle/cold path (`initSecondTicker`) and stored inside `SecondTicker`; it is not required by the per-tick `ClockEffect` graph.

### Calendar ABI

```text
calendarClass: Class<*>
  primary source: controller.calendarField.type
  fallback: calendarObject.javaClass at constructor time (immutable result)
setTimeInMillisMethod: Method
  name: "setTimeInMillis"
  arity: 1
  parameter: long primitive (preferred)
  return type: not an oracle matching condition; documented compatibility
formatMethod: Method
  name: "format"
  arity: 3
  actual arguments: Context, StringBuilder, StringBuilder
  formal parameters: must be assignable from the actual argument types
  selection: deterministic best fit; ambiguity is fail-closed
```

### Notes

- `mClockListeners` is currently cast to `ArrayList<Any>`. A frozen resolver should read it as `List<*>` and iterate by index to avoid `Iterator` allocation, unless evidence proves it is always `ArrayList` and index iteration is safe.
- `mContext` is only used on the lifecycle/cold `initSecondTicker` path; it is deliberately excluded from the core periodic ABI.
- Only members used by H1/H2 are retained; no `mBigTime`, no `fakeStatusBarClock`, no `useLeft`.

---

## C2.8 Deterministic Method Resolution

### `calendar.setTimeInMillis`

Requirements:

- Method name: `setTimeInMillis`.
- Parameter count: 1.
- Parameter type: primitive `long` (preferred semantic target).
- Return type: **not** an oracle matching condition; `XposedHelpers.findMethodBestMatch` does not use return type.
- Selection: exact primitive-`long` match first; deterministic compatible resolution if multiple overloads exist; ambiguity is fail-closed.

Compatibility note: the current call passes a boxed `Long` via `XposedHelpers.callMethod(calendar, "setTimeInMillis", System.currentTimeMillis())`. The frozen method should resolve the `setTimeInMillis(long)` overload; `Method.invoke` will still box the `long` at the invocation boundary.

### `calendar.format`

Requirements:

- Method name: `format`.
- Parameter count: 3.
- Actual argument types: `Context`, `StringBuilder`, `StringBuilder`.
- Formal parameter types: must accept the actual argument types; for `StringBuilder` arguments, formal types assignable **from** `StringBuilder` are valid (e.g. `StringBuilder`, `Appendable`, `CharSequence`, `Object`).
- The first argument: `Context` is the cold-known lower-information type. If the ROM declares a more-specific `Context` subtype (or overloads), the constructor-time real-object fallback may use the actual context class.
- Return type: **not** an oracle matching condition; `XposedHelpers.findMethodBestMatch` does not use return type. The current code discards the return; the method is expected to write the formatted string into the first `StringBuilder` and use the second as the pattern.
- Selection rule: deterministic best fit against the actual argument types; no reflection-iteration `firstOrNull`; ambiguity is fail-closed.

Incompatible formal types (e.g. a `StringBuffer` parameter) must be rejected because a `StringBuilder` argument cannot be passed to a `StringBuffer` formal parameter.

### `clock.updateTime`

Requirements:

- Method name: `updateTime`.
- Parameter count: 0.
- Return type: **not** an oracle matching condition.
- Resolve per `ClockTargetCapability`; produce one `updateTimeMethod` for each resolved hook target.

### Resolution authority summary

| Capability | Primary authority | Constructor-time fallback |
|------------|-------------------|---------------------------|
| Calendar `setTimeInMillis` / `format` | `calendarField.type` | `calendarObject.javaClass` |
| Clock `updateTime` / `mMiuiStatusBarClockController` | target class | none required |


---

## C2.9 `Method.invoke` Allocation Analysis

Freezing a `Method` removes runtime lookup/discovery, but `Method.invoke` still has an invocation boundary.

| Method | Args | Primitive boxing | Source-visible `Object[]` | Internal `Method.invoke` allocation |
|--------|------|------------------|---------------------------|-------------------------------------|
| `calendar.setTimeInMillis` | 1 long | `Long` for time value (not in `Long` cache range) | `Object[1]` per tick | likely a native invocation array |
| `calendar.format` | 3 objects | none | `Object[3]` per tick | likely a native invocation array |
| `clock.updateTime` | 0 | none | `Object[0]` (can use `XposedHelpers.EMPTY_OBJECT_ARRAY` or equivalent) | likely a native invocation array (smallest) |

### Allocation summary

- **H1**: `format`  - `Object[3]` per call.
- **H2**: `setTimeInMillis`  - `Object[1]` + `Long` per tick; `format` (transitively via `callMethod(updateTime)`)  - `Object[0]` if no-arg invocation is used; `mClockListeners` iteration  - `Iterator` per tick (Kotlin `for` over `List`/`ArrayList`).
- Reflection lookup is cached after the first call, so it is not part of the steady-state allocation.

### What frozen `Method` does and does not remove

Freezing a `Method` removes:

- runtime method lookup,
- `MemberCacheKey` construction,
- best-match discovery and class hierarchy scanning.

It does **not** prove:

- zero `Object[]` allocation at `Method.invoke` (Kotlin/Java varargs create the array),
- zero primitive boxing at `Method.invoke` (`setTimeInMillis(long)` still boxes `Long`).

A Java arity helper that simply calls `Method.invoke(receiver, arg1, ...)` internally does **not** eliminate these allocations; it only moves them into the helper.

Truly different invocation mechanisms (`MethodHandle.invokeExact`, a generated/direct bridge, or ART-internal accessors) are separate compatibility-sensitive options. They are recorded but not implemented in C2-A0.

---

## C2.10 `ClockStyleSnapshot` Publication

### Current state

```text
@Volatile private var clockStyleSnapshot: ClockStyleSnapshot? = null
private val clockSnapshotId = AtomicLong(0L)

fun currentClockStyleSnapshot(): ClockStyleSnapshot? = clockStyleSnapshot

private fun ensureClockStyleSnapshot(res: Resources): ClockStyleSnapshot {
    val current = clockStyleSnapshot
    if (current != null && current.configuration == res.configuration) {
        return current
    }
    val newSnapshot = buildClockStyleSnapshot(MainModule.mPrefs, res)
    clockStyleSnapshot = newSnapshot
    return newSnapshot
}

private fun refreshClockStyleSnapshot(res: Resources): ClockStyleSnapshot {
    val newSnapshot = buildClockStyleSnapshot(MainModule.mPrefs, res)
    clockStyleSnapshot = newSnapshot
    return newSnapshot
}
```

### Facts

- The publication is the volatile reference, not the snapshot object itself:

```kotlin
@Volatile
private var clockStyleSnapshot: ClockStyleSnapshot? = null
```

  `ClockStyleSnapshot` is an **immutable data class**; the `@Volatile` annotation applies to the `clockStyleSnapshot` reference.

- `configuration` is stored as a `Configuration(res.configuration)` **copy**.
- `current.configuration == res.configuration` is structural equality (`Configuration` overrides `equals`).
- A new snapshot always gets a new `id` from `clockSnapshotId.incrementAndGet()`.
- Identical preferences and configuration still create a new `id` if `refreshClockStyleSnapshot` is called.
- The `current ?: ensure(...)` pattern in H1 `updateTime` and the `initSecondTicker` lifecycle/setup path means `mPrefs` is only read when `clockStyleSnapshot` is `null`.
- The private `initClockStyle(mClock, clockName)` overload always calls `ensureClockStyleSnapshot`; because `ensure` short-circuits via structural equality, this does **not** necessarily rebuild in steady state.

### H1 `current ?: ensure(...)` question

Current code:

```kotlin
val snapshot = currentClockStyleSnapshot()
    ?: ensureClockStyleSnapshot(clock.context.resources)
```

Options for C2:

1. **Retain `current ?: ensure(...)`**: safe because `ensure` is only reached when `current` is `null`. It protects the first `updateTime` if the snapshot was not yet built.
2. **Change to `current ?: chain.proceed()`**: eliminates any `mPrefs` read fallback from the hot path, but means the first `updateTime` call before the controller/clock constructor runs will not apply a custom format. This could diverge from oracle behavior if `updateTime` is invoked before the constructor cold path builds the snapshot.

**A0 safe migration choice**: retain `current ?: ensure(...)` and ensure the cold constructor path (`MiuiStatusBarClockController` constructor, `MiuiClock` constructor, `MiuiPhoneStatusBarView.onAttachedToWindow`) builds the snapshot before any H1 `updateTime` or H2 ticker starts. This matches the current design intent.

---

## C2.11 Remaining `MainModule.mPrefs` Reads in `SystemClockHooks`

### Classification

| Location | Pref keys read | Classification |
|----------|----------------|----------------|
| `StatusBarClockTweakHook` install | `system_statusbar_enable_weather_param`, `system_statusbaricons_clock`, `system_statusbar_clocktweak`, `system_cc_clocktweak`, `system_cc_hidedate`, `system_drawer_hidedate` | install cold |
| `buildClockStyleSnapshot` | all `CLOCK_STYLE_PREFERENCE_KEYS` | snapshot build (cold unless reached from `ensure`) |
| `ensureClockStyleSnapshot` | transitively `buildClockStyleSnapshot` | snapshot build, only reached from `current ?: ensure` if null, or from `initClockStyle` private overload if no match |
| `refreshClockStyleSnapshot` (observer) | all `CLOCK_STYLE_PREFERENCE_KEYS` | preference observer (cold-ish, per change) |
| `CCClockTweakHook` install + `ccClockHook.intercept` | `system_cc_clock_fontsize`, `system_cc_clocktweak`, `system_qs_force_systemfonts`, `system_cc_clock_verticaloffset` | install cold + `updateResources` (out of C2 H1/H2 scope) |
| `CCClockCenterAlignHook` | `system_cc_clock_centeralign`, `system_drawer_hidedate`, `system_drawer_date_centeralign` | install + `updateResources` (out of scope) |
| `FakeStatusBarClockController.initState` hook | none directly; uses `useLeft` field | out of scope |

### Verdict

The **steady per-tick H1 path does not read `mPrefs`** as long as `clockStyleSnapshot` is non-null, and the **H2 `SecondTicker.run` path reads neither `mPrefs` nor `ClockStyleSnapshot`**. The only theoretical hot `mPrefs` read is the H1 `updateTime` fallback `currentClockStyleSnapshot() ?: ensureClockStyleSnapshot(...)` (and the lifecycle `initSecondTicker` setup). C2 must preserve the invariant that the snapshot is always built before the first tick.

---

## C2.12 SecondTicker Ownership / Context / Screen-Off Semantics

### Ownership graph

```text
SystemUI main thread
  -> MiuiStatusBarClockController constructor
       -> setAdditionalInstanceField(controller, "secondTicker", ticker)
       -> ScreenStateController.addListener(context, ticker)

SecondTicker
  -> WeakReference(controller)
  -> Context (mContext)
  -> Handler(mainLooper)
  -> running: Boolean
  -> screenStateRegistered: Boolean

ScreenStateController (object, process scope)
  -> ArrayList<ScreenStateListener> (strong)
  -> appContext: Context? (strong while listeners exist)
  -> BroadcastReceiver (strong while listeners exist)

PreferenceObserverRegistry
  -> observerOwners: CopyOnWriteArrayList<WeakReference<PreferenceObserver>>
  -> owned observer stored in XposedHelpers additional instance field on controller
```

### Context ownership

- `SecondTicker.context` is the controller's `mContext`.
- It is used for:
  - `DateFormat.is24HourFormat(context)` every second.
  - `context.mainLooper` to build the `Handler`.
- `SecondTicker.context` is used for `DateFormat.is24HourFormat(context)` and `context.mainLooper` during `Handler` creation. H1 formatting separately uses `clock.context`; converting `SecondTicker.context` to `applicationContext` would not directly change the formatter's `clock.context` `Resources` / `Locale`.
- **A0 does not change Context ownership.** A C2 implementation should only change it if a concrete lifecycle problem is demonstrated.

### Screen-off / start / stop

- `ScreenStateController.addListener` is called in `SecondTicker.start()`.
- `start()` is called from `initSecondTicker` and `onScreenStateChanged(true)`.
- `stop()` is called from `onScreenStateChanged(false)`.
- `dispose()` is called when the controller is GCed, when `needsTicker` becomes false, or when `start()` sees a null controller.
- `stop()` removes pending `Handler` callbacks but **does not** remove the listener unless the controller ref is null.
- `scheduleNextTick()` posts with `delay = 1000L - System.currentTimeMillis() % 1000L`, aligning to the next wall-clock second.

---

## C2.13 `clockName` / `showSeconds` Metadata Mechanism

### Current implementation

`ModuleHelper.getViewInfo` / `setViewInfo`:

```kotlin
private val viewInfoTag = ResourceHooks.getFakeResId("view_info_tag")

fun getViewInfo(view: View?, key: String): Any? {
    val info = view.getTag(viewInfoTag) as? HashMap<String, Any?>
    return info?.get(key)
}

fun setViewInfo(view: View?, key: String, value: Any?) {
    val info = view.getTag(viewInfoTag) as? HashMap<String, Any?>
        ?: HashMap<String, Any?>().also { view?.setTag(viewInfoTag, it) }
    info[key] = value
}
```

- Backend: a single `HashMap<String, Any?>` stored as a `View` tag under a fake resource id.
- `clockName` values: `"clock"`, `"ccClock"`, `"ccDate"`, `"drawerDate"`.
- `showSeconds` values: `true` or `null` (treated as absent).
- H1 reads `clockName` once per `updateTime`.
- H2 reads `showSeconds` once per listener per tick.

### Lifecycle and allocation assessment

- The `HashMap` is created on first `setViewInfo` for a view.
- `HashMap.get` / `put` are cheap but still a map lookup and an object entry per view.
- `showSeconds` is set to `null` for clocks that do not show seconds, so the map retains an entry.

### Typed `View.setTag` alternative

Switching to dedicated `View.setTag(fakeResId, value)` / `View.getTag(fakeResId)` keyed-tag calls would remove:

- the module-level `HashMap<String, Any?>`,
- the string-key lookup,
- the per-view `HashMap` entries for `clockName` / `showSeconds`.

It is not an unkeyed `View.mTag` access; Android keyed-tag storage is still a backing store that may be created/reused. This is recorded as a C2 option but **not** implemented in A0; no measured-performance claim is made.

---

## C2.14 Listener Bound

`MAX_CLOCK_LISTENERS = 64` is used in **only one** place:

```kotlin
val count = minOf(clockListeners.size, MAX_CLOCK_LISTENERS)
for (i in 0 until count) { ... }
```

This is the **preference-observer style-refresh path** (`handler.post { ... }` in `scheduleHook.intercept`).

It is **not** used in:

- `initSecondTicker` tag loop (`for (listener in clockListeners)`).
- `SecondTicker.run` update loop (`for (listener in clockListeners)`).

A0 fact: the per-second tick is **UNBOUNDED** today.

C2 design note: any bound in `SecondTicker.run` must define an explicit fallback for clocks beyond the bound (e.g., they are not updated every second and rely on the minute update path).

---

## C2.15 Fatal / NonFatal Matrix

### Module boundaries used

| Boundary | Behavior |
|----------|----------|
| `XposedHelpers.throwOrReturn(t, result)` | If `t != null`, throws `t`; otherwise returns `result`. |
| `ModuleHelper.guarded { ... }` | Rethrows `OutOfMemoryError`, `ThreadDeath`, `VirtualMachineError`. Logs all other `Throwable`. |
| `FatalErrors.unwrapAndRethrowIfFatal(t)` | Unwraps `InvocationTargetException` / `ExecutionException` / `InvocationTargetError` up to 4 levels; rethrows if any cause is fatal. Returns original throwable for logging. |

### H1 `updateTime` fatal matrix

| Failure | Outcome | Original `chain.proceed()` called? | `clock.text` set? | Logged? |
|---------|---------|------------------------------------|-------------------|---------|
| `getViewInfo` returns null (`clockName == null`) | `buildClockText` returns `null`; falls through to `chain.proceed()` | YES | by original | no |
| `clockName` is one of the hidden names with matching hide flag | `clock.text = ""`; returns `null` | NO | `""` | no |
| `snapshot == null` then `ensure` builds OK | normal custom format path | NO if timeFmt non-null | formatted text | no |
| `getObjectField(clock, "mMiuiStatusBarClockController")` fails | outer `catch`; `throwOrReturn` rethrows | NO | not set | no (XposedHelpers may log some reflection errors internally) |
| `getObjectField(controller, "mCalendar")` fails | outer `catch`; rethrows | NO | not set | no |
| `callMethod(calendar, "format", ...)` throws `InvocationTargetError` | outer `catch`; rethrows | NO | not set | no |
| `buildClockText` returns null | `chain.proceed()` | YES | by original | n/a |
| `timeFmt` non-null, format succeeds | return null | NO | formatted text | no |
| `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError` thrown directly in hook body | outer `catch`; `throwOrReturn` rethrows the same direct object | NO | not set | no |
| Any other `RuntimeException` / `Error` in the try block | outer `catch`; rethrows | NO | not set | no |

H1 does **not** swallow exceptions. It is a strict pass-through hook.

#### Wrapped fatal identity in H1

If `calendar.format` internally throws a fatal `OutOfMemoryError`, `ThreadDeath`, or `VirtualMachineError`:

1. `Method.invoke` wraps it as `InvocationTargetException`.
2. `XposedHelpers.callMethod` converts it to `XposedHelpers.InvocationTargetError(cause)`.
3. The H1 outer `catch (t: Throwable)` stores the **wrapper**.
4. `throwOrReturn` rethrows the `InvocationTargetError` wrapper, not the original fatal cause.

This is a **latent fatal-boundary issue**: the current H1 oracle does **not** restore wrapped fatal identity. C2 may choose to correct it by unwrapping via `FatalErrors.unwrapAndRethrowIfFatal`; if corrected, this must be reported as an intentional correctness change with exact-identity tests.

### H2 `SecondTicker.run` fatal matrix

The entire body is wrapped in `ModuleHelper.guarded { ... }`. `guarded` is **not** `FatalErrors.unwrapAndRethrowIfFatal`; it does **not** unwrap `InvocationTargetError`.

| Failure | Outcome | Ticker rescheduled? | Loop continues? | Logged? |
|---------|---------|---------------------|-----------------|---------|
| `clockControllerRef.get() == null` | `dispose(); return` | NO | n/a | no |
| `getObjectField(controller, "mCalendar")` fails (`NoSuchFieldError`) | `guarded` logs, returns | YES (scheduleNextTick after guarded) | NO  - loop aborts | yes |
| `callMethod(calendar, "setTimeInMillis", ...)` fails (`InvocationTargetError`, `NoSuchMethodError`) | `guarded` logs, returns | YES | NO | yes |
| `setObjectField(controller, "mIs24", ...)` fails | `guarded` logs, returns | YES | NO | yes |
| `getObjectField(controller, "mClockListeners")` / `as ArrayList` fails | `guarded` logs, returns | YES | NO | yes |
| `listener as View` fails (`ClassCastException`) inside the for-loop | `guarded` logs, returns | YES | NO | yes |
| `callMethod(clock, "updateTime")` fails | `guarded` logs, returns | YES | NO | yes |
| `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError` inside guarded | `guarded` rethrows | NO (run() throws, scheduleNextTick not reached) | NO | no (rethrown) |
| `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError` wrapped in `InvocationTargetError` (cause) | `guarded` sees `InvocationTargetError` as a non-fatal `Error`; logs and returns | YES | NO | yes (`LEGACY_WRAPPED_FATAL_BUG`) |

Important: because the **entire** `guarded` block is one try/catch, a failure for one clock aborts the rest of the listener list for that tick. The ticker itself is rescheduled for non-fatal errors.

#### `LEGACY_WRAPPED_FATAL_BUG`

`CallbackGuard.guarded` only recognizes direct `OutOfMemoryError`, `ThreadDeath`, and `VirtualMachineError`. It does **not** unwrap `InvocationTargetError`. Therefore, a fatal thrown by `calendar.setTimeInMillis` or `clock.updateTime` and wrapped by `XposedHelpers.callMethod` is currently:

- logged and swallowed,
- aborting the rest of that tick,
- rescheduling the ticker.

C2 may intentionally correct this later by wrapping frozen `Method.invoke` calls with `FatalErrors.unwrapAndRethrowIfFatal`. If corrected, it must be reported as an intentional correctness change from the oracle, with exact-identity tests.

---

## C2.16 Proposed Minimal Architecture C Shape

### Files under `mods/clock/`

```text
mods/clock/ClockAbi.kt
mods/clock/ClockResolver.kt
mods/clock/ClockEffect.kt
```

No `ClockRuntime.kt` unless evidence shows a runtime object is useful beyond `SecondTicker`.

### Responsibilities

| File | Responsibility |
|------|----------------|
| `ClockAbi.kt` | Immutable data class holding the frozen `Class` / `Field` / `Method` references resolved once per process. |
| `ClockResolver.kt` | Cold resolution: uses `lpparam.classLoader` and the `mCalendar` declared field type (with a constructor-time `calendarObject.javaClass` fallback) to resolve `MiuiStatusBarClockController`, `MiuiClock`, `MiuiStatusBarClock`, and the calendar class members. Returns `ClockAbi`. |
| `ClockEffect.kt` | Typed effect execution: uses `ClockAbi` to read `mCalendar`, call `setTimeInMillis` / `format` / `updateTime`, update `mIs24`, and iterate `mClockListeners`. Hides `XposedHelpers` from the hot path. |
| `SystemClockHooks.kt` (existing) | Remains the thin facade. Installs hooks, handles preference observer, calls `ClockResolver` to build `ClockAbi`, and passes it to `ClockEffect`. Keeps `SecondTicker`, `ClockStyleSnapshot`, and `ThreadLocal` builders. |

### Design principles carried from C1

- **Cold Resolve**  - `ClockResolver`
- **Frozen ABI**  - `ClockAbi`
- **Typed Config**  - existing `ClockStyleSnapshot`
- **Lifecycle Runtime**  - existing `SecondTicker`
- **Effect**  - `ClockEffect`
- **Thin Hook**  - `updateTime` hook and `SecondTicker` stay tiny; all reflection is delegated to `ClockEffect`.

### What C2 does NOT create

- No `ClockKernel`.
- No `ClockManager`.
- No `ClockProcessRuntime`.
- No generic shared abstraction extracted from C1 `StatusBarHeight*`.

---

## C2.17 Explicit Non-Scope

The following are **not** part of C2 H1/H2 and are **not** refactored in C2:

- `CCClockTweakHook` and `CCClockCenterAlignHook` (QS header / drawer clock).
- `FakeStatusBarClockController.initState` hook.
- `MiuiPhoneStatusBarView.onAttachedToWindow` style hook (cold path, may use ABI but not a per-tick concern).
- `MiuiClock.onDarkChanged` hook.
- `WeatherDataController` internals.
- `SystemUIStatusBarHooks` NetSpeed B1/B2.
- Any production file under `mods/statusbarheight/`.

---

## C2.18 Method-Resolution Allocation / Java Bridge Option

A0 records but does not implement:

- `ClockEffect` in Kotlin calling `Method.invoke` will still allocate the `Object[]` and the `Long` box for `setTimeInMillis`.
- A Java-arity bridge that calls `Method.invoke` internally does **not** eliminate these allocations; it only moves the varargs array / boxing into the helper.
- To truly avoid invocation allocation, use `MethodHandle.invokeExact`, a generated/direct bridge, or an ART-internal accessor. These are separate compatibility-sensitive options.
- This is recorded but not implemented in C2 B1; default strategy is freeze `Method` first, then measure/inspect.

---

## C2.19 Device Evidence

C1 device A/B is **DEFERRED / BLOCKED_BY_ENVIRONMENT**. It does not block C2-A0.

- No Xposed/libxposed framework was verified on the C1 test device.
- Device evidence is deferred until a verified framework-capable device is available.
- C2 code implementation may proceed independently.

---

## C2.20 A0 Checklist

- [x] H1 `updateTime` graph recorded.
- [x] H2 `SecondTicker.run` graph recorded.
- [x] Periodic generic operations table built (A/B/D, no E).
- [x] Cold class/member inventory recorded (calendar class from `Field.type`; runtime `javaClass` fallback documented).
- [x] Candidate frozen ABI designed.
- [x] Deterministic method-resolution requirements recorded.
- [x] `Method.invoke` allocation concern analyzed.
- [x] `ClockStyleSnapshot` publication and `mPrefs` read classification recorded.
- [x] SecondTicker ownership / context / retention graph recorded.
- [x] `clockName` / `showSeconds` metadata mechanism recorded.
- [x] Listener bound documented (UNBOUNDED in H2).
- [x] Fatal matrix documented.
- [x] Proposed minimal Architecture C shape recorded.
- [x] Explicit non-scope recorded.
- [x] No production code changed.
