# C2 — SystemClock Architecture C Consolidation Audit

**Repository:** `tomthenpc/customiuizer-a14`
**Branch:** `devin/a14-architecture-c-r14.20.0`
**Scope:** Final integration audit of the C2 SystemClock Architecture C slice (`SystemClockHooks.kt`, `ClockEffect*`, `ClockResolver`, `ClockAbi`, tests).
**Consolidation Base SHA:** `92d717ec7d88df4bcb6d8b38819abd1cd0e8eeac`
**Status:** No production code modified during this consolidation. Only documentation and test naming/comments adjusted.

> This document records the state after C2-B1, C2-B2 H1, C2-B3-A0, and C2-B3 H2 are complete. C3 has not started.

---

## 1. Freeze / Ancestry

Independent verification performed at consolidation start:

| Checkpoint | SHA | Description |
|------------|-----|-------------|
| C2-B1 Clock Core | `4d71cb1606e6b025ac6a069ac16a406796edd037` | `fix(a14): finalize deterministic clock core` — frozen `ClockAbi`/`ClockResolver`/`ClockEffect` core. |
| C2-B2 H1 updateTime migration | `6baa0ba629072919cdb4353f478efa45e22bb7c9` | `fix(a14): publish cold clock effect before hot path` — H1 `updateTime` hooked to frozen effect. |
| C2-B3-A0 H2 preflight | `02d9855d87a69a2e377ccf3579974b4215a6540e` | `docs(a14): close null publication contract for H2 preflight` — audit-only H2 preflight. |
| C2-B3 H2 migration | `50cb8cd70e63954ee178ae62dd956d7db97854a1` | `C2-B3 H2: migrate SecondTicker to frozen ClockEffect publication path.` |
| C2-B3 corrective | `92d717ec7d88df4bcb6d8b38819abd1cd0e8eeac` | `C2-B3 corrective: gate late-clock seconds tags and restore init list cast.` |

```text
$ git rev-parse HEAD
9d609e33dc631b73b0805d1714763c1a586717be
$ git status --short
(empty)
```

The consolidation documentation and test naming were committed on top of the frozen C2 production checkpoint `92d717ec...`. Final SHA `9d609e33...` is two commits ahead of `92d717ec...` (consolidation document, then a whitespace cleanup). No production files were modified in either commit. Worktree is clean.

---

## 2. Final H1 Graph

### 2.1 Entry point

`StatusBarClockTweakHook` (`app/src/main/java/tv/withaibuild/customiuizer/mods/SystemClockHooks.kt:874`) constructs one shared `ClockEffectPublication` at install time:

```kotlin
val clockEffectPublication = if (statusbarClockTweak || ccClockTweak) {
    ClockResolver.resolveCore(lpparam.classLoader)?.let(::ClockEffectPublication)
} else {
    null
}
```

- `ClockResolver.resolveCore` runs once per class loader during install. It performs `findClassIfExists`, `resolveField`, `resolveNoArgMethod`, and `resolveCalendarFromDeclaredType`. No per-call reflection.
- `ClockEffectPublication` holds only the immutable `ClockAbi` and the published `ClockEffect`.

It then installs `updateTimeHook` on `MiuiClock.updateTime` and `MiuiStatusBarClock.updateTime` (`SystemClockHooks.kt:1041`).

### 2.2 `updateTimeHook.intercept` hot path (steady-state)

```text
1. clock = chain.thisObject as TextView
2. clockName = ModuleHelper.getViewInfo(clock, "clockName")
3. hidden path (ccDate/drawerDate/clock + hide flags) -> clock.text = ""; return without proceed
4. snapshot = currentClockStyleSnapshot() ?: ensureClockStyleSnapshot(clock.context.resources)
5. timeFmt = buildClockText(clockName, snapshot, WeatherDataController.weatherInfo, statusbarClockTweak, ccClockTweak)
6. if timeFmt == null -> skipped = true
7. publication = clockEffectPublication
   effect = publication?.currentEffect() ?: publication?.resolveForClock(clock, clock.context.javaClass)
8. if effect == null -> skipped = true
9. controller = effect.readController(clock); if null -> skipped = true
10. calendar = effect.readCalendar(controller); if null -> skipped = true
11. formatSb = clockFormatBuilder.get()!!; reset
    textSb = clockTextBuilder.get()!!; reset
12. formatted = effect.format(calendar, clock.context, textSb, formatSb)
    if !formatted -> skipped = true
13. clock.text = textSb.toString()
14. if skipped -> result = chain.proceed()
15. return XposedHelpers.throwOrReturn(throwable, result)
```

### 2.3 H1 proof classification

| Claim | Evidence | Classification |
|-------|----------|----------------|
| `chain.proceed()` called at most once | Only one call site inside `if (skipped)` (`SystemClockHooks.kt:1109`) | STRUCTURAL |
| Hidden clock path correct | `clock.text = ""` + early return; never reaches `chain.proceed()` (`SystemClockHooks.kt:1054-1055`) | STRUCTURAL + runtime (`SystemClockHotPathTest`) |
| `timeFmt == null` proceeds original | `skipped = true` -> `chain.proceed()` (`SystemClockHooks.kt:1067-1069`, `1108-1110`) | STRUCTURAL |
| Ordinary Architecture C failure proceeds original | `effect == null`, `controller == null`, `calendar == null`, `formatted == false` all set `skipped = true` (`SystemClockHooks.kt:1076-1096`) | STRUCTURAL |
| Fatal propagates exact identity | H1 `updateTimeHook` catches `Throwable` and calls `FatalErrors.unwrapAndRethrowIfFatal(t)`, rethrowing `OutOfMemoryError`/`ThreadDeath`/`VirtualMachineError` before storing `throwable` (`SystemClockHooks.kt:1103-1105`; `FatalErrors.kt:38-51`) | STRUCTURAL for the H1 hook control flow. Supporting component evidence: `ClockEffect`/`FatalErrors` wrapped-fatal exact identity is RUNTIME_TESTED by `secondTickerH2_wrappedFatalFromCalendarSetTimeInMillis_preservesExactIdentity`. H1 `updateTimeHook` fatal propagation itself is **NOT runtime-injected**. |
| Cold-complete H1 has no runtime calibration | H1 `updateTimeHook` calls `publication?.currentEffect()` first and only falls through to `resolveForClock` when the published effect is null (`SystemClockHooks.kt:1070-1075`). When `ClockAbi.calendarCold` is non-null, `currentEffect()` returns a non-null `ClockEffect` and `resolveForClock` is not reached. | STRUCTURAL H1 control flow. Supporting component evidence: the shared `ClockEffectPublication` component behavior is RUNTIME_TESTED by `secondTickerH2_coldComplete_doesNotUseRuntimeCalibration`. The H1 `updateTimeHook` callback itself is **NOT RUNTIME_TESTED** for this claim. |
| Steady-state H1 has no generic Xposed member lookup | `updateTimeHook` uses only frozen `effect.*` methods; no `XposedHelpers.getObjectField`/`callMethod`/`setObjectField` in the hot path (`SystemClockHooks.kt:1041-1112`) | STRUCTURAL |
| No `MainModule.mPrefs` read in H1 hot path | `mPrefs` is read only in `StatusBarClockTweakHook` install; `updateTimeHook` uses captured booleans and snapshot | STRUCTURAL |
| No strong Android owner in `ClockEffectPublication` | `ClockAbi`/`ClockEffect` store only `Class`/`Field`/`Method`; `ClockEffectPublication` stores `ClockAbi` + volatile `ClockEffect?` + `AtomicInteger` (`ClockEffectPublication.kt:14-21`; `ClockAbi.kt:12-58`; `ClockEffect.kt:16-19`) | STRUCTURAL |

### 2.4 H1 remaining notes

- `resolveForClock` in the `updateTime` hook is a slow path. If `ClockAbi.calendarCold` is present, it is never reached. If `calendarCold` is missing, a one-time runtime calibration is performed on the first eligible clock and the resulting `ClockEffect` is cached.
- Source-visible H1 allocation on the successful path:
  - `buildClockText` normally reuses a `String` from the `ClockStyleSnapshot`; if weather replacement is enabled and `"tq"` is present, `String.replace(...)` creates a replacement `String`.
  - `effect.format(...)` uses `Method.invoke` with three `Object` arguments, which allocates an `Object[]` per call.
  - `clockFormatBuilder` and `clockTextBuilder` are `ThreadLocal` `StringBuilder`s reused each call, but they only avoid allocating mutable builders, not the final output.
  - `clock.text = textSb.toString()` creates the final output `String` on a successful custom-clock update.
  - Not claimed: zero allocation.
- `buildClockText` does **not** read `MainModule.mPrefs`; it uses the supplied `ClockStyleSnapshot`, `weatherInfo`, and captured feature flags.

---

## 3. Final H2 Graph

### 3.1 `SecondTicker.run` cold-complete path

```text
1. if (!running) return
2. controller = clockControllerRef.get(); if null -> dispose(); return
3. ModuleHelper.guarded {
4.   effect = publication.currentEffect()
5.   if effect == null {
        effect = calibrateEffect(controller)
        if effect == null -> return@guarded
      }
6.   runWithEffect(effect, controller)
   }
7. scheduleNextTick()
```

`runWithEffect`:

```text
1. calendar = effect.readCalendar(controller); if null -> return
2. if !effect.setTimeInMillis(calendar, System.currentTimeMillis()) -> return
3. if !effect.writeIs24(controller, DateFormat.is24HourFormat(context)) -> return
4. clockListeners = effect.readClockListeners(controller) as ArrayList<Any>
5. for (listener in clockListeners) {
      clock = listener as View
      if ModuleHelper.getViewInfo(clock, "showSeconds") == null -> continue
      if !effect.invokeUpdateTime(clock) -> return
   }
```

### 3.2 `SecondTicker.run` cold-incomplete path

```text
1. publication.currentEffect() == null
2. calibrateEffect(controller) called inside guarded
3. publication.readClockListeners(controller) as ArrayList<Any>
4. iterate listeners; first eligible clock with showSeconds != null:
      effect = publication.resolveForClock(clock, clock.context?.javaClass ?: context.javaClass)
      if effect != null -> return it
5. if no eligible or resolve fails -> return null; tick aborts
6. next tick uses published effect via currentEffect()
```

`resolveForClock` is `synchronized` and uses `failedTargetMask` to avoid retrying a failed target. A failed target for one clock does not poison a different target class (`1 shl targetIndex` bits are independent).

### 3.3 H2 proof classification

| Claim | Evidence | Classification |
|-------|----------|----------------|
| No generic Xposed member lookup in `SecondTicker` | Source-structural guard `secondTickerH2_sourceStructural_hotPathHasNoForbiddenReflection` passes; class body contains no `XposedHelpers.getObjectField`/`setObjectField`/`callMethod`, `resolveCore`, `findClass`, `MainModule.mPrefs`, `MAX_CLOCK_LISTENERS`, `Sequence`, `Flow` | STRUCTURAL (the JUnit reads source text and asserts substrings) |
| No runtime `findClass`/member enumeration | Uses only frozen `Class`/`Field`/`Method` from `ClockAbi` | STRUCTURAL |
| No `MainModule.mPrefs` in `SecondTicker` | `SecondTicker` uses captured `publication`, `context`, and frozen effect | STRUCTURAL |
| No `MAX_CLOCK_LISTENERS` in `SecondTicker` | Source-structural guard; `SecondTicker.run` iterates the full `ArrayList` | STRUCTURAL |
| No List/index-loop relaxation | `for (listener in clockListeners)` over `ArrayList`; source contains no indexed `for` or `List.get` loop | STRUCTURAL (the JUnit reads source text and asserts substrings) |
| Non-`View` listener entry hard-cast aborts the tick | `listener as View` throws `ClassCastException` for a non-`View` element; caught by `ModuleHelper.guarded` | RUNTIME (`secondTickerH2_listenerSemantics_nonViewEntryAbortsTick`) |
| No coroutine/Flow | Source-structural guard | STRUCTURAL |
| No strong View/controller/calendar retention in `SecondTicker` | `clockControllerRef` is `WeakReference`; fields are `Context`, `Boolean`, `ClockEffectPublication`; `SecondTicker` has no View or calendar fields (`secondTickerH2_ownership_controllerWeakAndNoViewOrCalendarFields`) | STRUCTURAL + RUNTIME (field inspection) |
| `ClockEffectPublication` is non-null at ticker construction | `initSecondTicker` passes `publication` to `SecondTicker` constructor only when `publication != null` (`SystemClockHooks.kt:716`, `726`) | STRUCTURAL |

### 3.4 H2 remaining notes

- `resolveForClock` performs a one-time runtime calendar calibration only if `ClockAbi.calendarCold` is missing. Once an effect is published, every subsequent tick uses `currentEffect()`.
- `calibrateEffect` and `runWithEffect` both use `as ArrayList<Any>`: `calibrateEffect` casts `publication.readClockListeners` and `runWithEffect` casts `effect.readClockListeners`. This is the intentionally fail-fast legacy policy for the ticker.
- `clock.context?.javaClass ?: context.javaClass` is a defensive null-fallback. On real Android `clock.context` is non-null, so the fallback to the controller's `mContext` class is not exercised. The fallback only matters in the stub `android.jar` unit-test environment where `View.getContext()` returns null.

---

## 4. Null-Publication Lifecycle Matrix

| Scenario | Condition | showSeconds | Ticker | Evidence |
|----------|-----------|-------------|--------|----------|
| A | `publication != null` and seconds enabled | Allowed (`true` for eligible clocks) | Allowed (`initSecondTicker` creates `SecondTicker`) | RUNTIME (`SystemClockHotPathTest.secondTickerH2_coldComplete_publicationAvailableBeforeTick` etc.) |
| B | `publication != null` and seconds disabled | Cleared (`setViewInfo(clock, "showSeconds", null)`) | Disposed/removed (`needsTicker == false` -> `previousTicker?.dispose()` and no new ticker) | RUNTIME (`secondTickerH2_nullPublication_*` family) |
| C | `publication == null` and existing clocks | Cleared (`showSeconds = false` for every listener) | No new ticker; `previousTicker?.dispose()`; `secondTicker` field removed | RUNTIME (`secondTickerH2_nullPublication_disposesExistingTickerAndRemovesField`, `secondTickerH2_nullPublication_clearsStaleShowSeconds`) |
| D | `publication == null` and clock constructed after initial init | Constructor hook guards `showSeconds=true` with `clockEffectPublication != null`; late clock cannot get `showSeconds=true` | Remains absent because `initSecondTicker(..., null)` does not create a ticker | STRUCTURAL (`secondTickerH2_STRUCTURAL_constructorShowSecondsGatedByPublication`) + RUNTIME state check (`secondTickerH2_nullPublication_lateClockDoesNotReceiveShowSeconds` — runtime of post-init controller state, not of the actual constructor hook) |
| E | `publication` stays null | No periodic generic fallback; no resolver retry timer; `SecondTicker` never constructed | No periodic activity | STRUCTURAL (no `SecondTicker` construction path with `publication == null`; `initSecondTicker` returns before creating ticker when `publication == null`) |

### Distinguishing evidence types

- Runtime behavior for A/B/C is exercised directly in `SystemClockHotPathTest`.
- D has two pieces: a source-structural proof that the constructor hook source will not set `showSeconds=true` when `clockEffectPublication == null`, and a runtime test that the post-init controller/listener state does not carry an active `showSeconds` tag. The runtime test does **not** invoke the actual `MiuiClock` constructor hook; that is a structural-only claim.
- E is a structural/lifecycle claim: `initSecondTicker` with `publication == null` takes the `else` branch, disposes any previous ticker, clears tags, and returns. No code path can create a `SecondTicker` with a null publication.

---

## 5. Listener Compatibility Matrix

### 5.1 `initSecondTicker` (lifecycle / metadata)

```kotlin
@Suppress("UNCHECKED_CAST")
val clockListeners = if (publication != null) {
    publication.readClockListeners(clockController) as? ArrayList<Any>
} else {
    XposedHelpers.getObjectField(clockController, "mClockListeners") as? ArrayList<Any>
}
```

- Frozen `Field` access when `publication != null`.
- `as? ArrayList<Any>` — non-`ArrayList` values skip the tag loop, matching the legacy `XposedHelpers.getObjectField(..., "mClockListeners") as? ArrayList<Any>` contract.
- RUNTIME proof: `secondTickerH2_initNonArrayListListeners_skipsTagTraversal`.

### 5.2 `SecondTicker.run` (periodic hot path)

```kotlin
@Suppress("UNCHECKED_CAST")
val clockListeners = effect.readClockListeners(controller) as ArrayList<Any>
for (listener in clockListeners) {
    val clock = listener as View
    ...
}
```

- Hard `as ArrayList<Any>` — a non-`ArrayList` list will throw `ClassCastException`, which is caught by `ModuleHelper.guarded` and logged. This is fail-fast and matches the legacy assumption that `mClockListeners` is an `ArrayList`.
- `Iterator` traversal over `ArrayList`.
- `listener as View` — legacy cast; non-`View` entries throw and abort the tick.
- Unbounded — no `MAX_CLOCK_LISTENERS` cap.
- RUNTIME proof: `secondTickerH2_listenerSemantics_*` family.

### 5.3 Preference observer refresh (H1 style refresh)

```kotlin
@Suppress("UNCHECKED_CAST")
val clockListeners = XposedHelpers.getObjectField(controller, "mClockListeners") as? ArrayList<Any>
if (clockListeners != null) {
    val count = minOf(clockListeners.size, MAX_CLOCK_LISTENERS)
    for (i in 0 until count) { ... }
}
```

- This is the **H1 style-refresh** path, not the H2 ticker. It remains capped by `MAX_CLOCK_LISTENERS` because it is style-only, time-insensitive, and intentionally bounded.
- This cap does **not** leak into `SecondTicker.run`.

### Verdict

The three listener paths are intentionally different and are not conflated in code or tests. The test `secondTickerH2_listenerSemantics_traversalUnboundedAndNoMaxClockListeners` (100 listeners) explicitly verifies that `SecondTicker` does not apply the `MAX_CLOCK_LISTENERS` cap.

---

## 6. Ordinary Failure Matrix (H2)

| Failure point | Effect on current tick | Evidence |
|---------------|------------------------|----------|
| `effect.readCalendar(controller)` returns null | `runWithEffect` returns immediately | STRUCTURAL (`if (calendar == null) return` at `SystemClockHooks.kt:827`) |
| `effect.setTimeInMillis(...)` returns false | `runWithEffect` returns; no `writeIs24`, no listener updates | RUNTIME (`secondTickerH2_failure_setTimeInMillisFailureAbortsRemainingTick`) |
| `effect.writeIs24(...)` returns false | `runWithEffect` returns; no listener updates | RUNTIME (`secondTickerH2_failure_writeIs24FailureAbortsListenerUpdates`) |
| `effect.readClockListeners(controller)` returns null or a non-`ArrayList` `List` | The hard `as ArrayList<Any>` cast fails inside `ModuleHelper.guarded`; the current tick aborts; no listener updates | STRUCTURAL (`SystemClockHooks.kt:831`). The exact Kotlin/JVM exception type is not important to the contract. |
| A listener is not a `View` (`listener as View` throws) | `ModuleHelper.guarded` catches `ClassCastException`; tick aborts; later listeners not updated | RUNTIME (`secondTickerH2_listenerSemantics_nonViewEntryAbortsTick`) |
| `effect.invokeUpdateTime(clock)` returns false | `runWithEffect` returns; later listeners not updated | RUNTIME (`secondTickerH2_failure_updateTimeFailureAbortsRemainingListeners`) |

After any ordinary failure:

- `ModuleHelper.guarded` catches the non-fatal exception or the method returns `false`.
- `run()` reaches `scheduleNextTick()` (unless a fatal escaped first).

Classification summary:

- Order-sensitive abort semantics: **STRUCTURALLY PROVEN** for `readCalendar` null-return and `readClockListeners` hard-cast-failure paths; **RUNTIME TESTED** for `setTimeInMillis`, `writeIs24`, non-`View` listener, and `invokeUpdateTime` failures.
- `scheduleNextTick()` after ordinary failure: **STRUCTURALLY PROVEN** by `secondTickerH2_STRUCTURAL_guardedAndCallbackGuardShape` (the `guarded` block closes before `scheduleNextTick()` is reached).

---

## 7. Fatal Matrix

### 7.1 Fatal categories handled by `FatalErrors`

`FatalErrors.unwrapAndRethrowIfFatal` (`FatalErrors.kt:38-51`) recognizes:

- `OutOfMemoryError`
- `ThreadDeath`
- Any `VirtualMachineError` (covers `StackOverflowError`, `InternalError`, etc.)

It also unwraps `InvocationTargetException`, `ExecutionException`, and `XposedHelpers.InvocationTargetError` up to `maxDepth = 4`, checking each wrapped cause for fatality.

### 7.2 H2 fatal path

In `SecondTicker.run`:

```kotlin
ModuleHelper.guarded {
    ...
}
scheduleNextTick()
```

`CallbackGuard.guarded` (`CallbackGuard.kt:23-30`) rethrows `OutOfMemoryError`/`ThreadDeath`/`VirtualMachineError` and logs everything else. If a fatal escapes the `guarded` block, `scheduleNextTick()` is not reached.

### 7.3 Test classification

| Test | What it proves | Classification |
|------|----------------|----------------|
| `secondTickerH2_wrappedFatalFromCalendarSetTimeInMillis_preservesExactIdentity` | `Method.invoke` throws `InvocationTargetException` wrapping `OutOfMemoryError`; `ClockEffect` unwraps and rethrows with exact original identity | RUNTIME — **WRAPPED FATAL** |
| `secondTickerH2_STRUCTURAL_guardedAndCallbackGuardShape` | Source-structural: `SecondTicker.run` closes the `ModuleHelper.guarded { ... }` block immediately before `scheduleNextTick()`; `CallbackGuard.guarded` source rethrows `OutOfMemoryError`/`ThreadDeath`/`VirtualMachineError` | STRUCTURAL (the JUnit reads and parses source text) |
| Direct `OutOfMemoryError`/`ThreadDeath`/`VirtualMachineError` in H2 run | Not injected in the current unit harness; covered by the same `FatalErrors` logic and the structural guarded boundary | NOT PROVEN at runtime |

### 7.4 Verdict

- Exact identity for wrapped fatal through `Method.invoke` is **RUNTIME TESTED**.
- `scheduleNextTick` after fatal is **STRUCTURALLY PROVEN**.
- Direct fatal identity is **NOT RUNTIME PROVEN** but uses the same unwrapping/rethrow boundary.

---

## 8. Publication / Concurrency

`ClockEffectPublication` (`ClockEffectPublication.kt:14-152`):

| Property | Verdict | Evidence |
|----------|---------|----------|
| `effect` is `@Volatile` | Yes | Line 18 |
| `ClockEffect` is immutable enough for cross-callback reuse | Yes | `ClockEffect` holds only `ClockAbi` and `CalendarCapability` (both frozen metadata) (`ClockEffect.kt:17-19`) | STRUCTURAL |
| `currentEffect()` is a direct volatile read | Yes | Line 53 | STRUCTURAL |
| Synchronization only on unresolved slow path | Yes | `synchronized(this)` is inside `resolveForClock` and only entered when `currentEffect()` is null | STRUCTURAL |
| `failedTargetMask` is bounded per frozen target | Yes | `AtomicInteger` with `1 shl targetIndex`; target array size is fixed at install time | STRUCTURAL |
| Failed target does not poison a different valid target | Yes | Different bit per target; `secondTickerH2_coldIncomplete_failedSiblingDoesNotBlockDifferentValidTarget` | RUNTIME |
| No target resolution is repeated once effect published | Yes | `effect` is set once; subsequent `resolveForClock` calls return the published effect | STRUCTURAL + RUNTIME (`secondTickerH2_repeatedTicksReuseSameEffect`) |
| Publication holds metadata only | Yes | `ClockAbi` + `ClockEffect?` + `AtomicInteger` + `calibrationAttempts` | STRUCTURAL |
| No `Context`/`View`/`controller`/`calendar` object retained | Yes | `ClockAbi` holds `Class`/`Field`/`Method`; `ClockEffect` holds `CalendarCapability` (Class/Method); publication never stores instances | STRUCTURAL |

### `calibrationAttempts`

`internal var calibrationAttempts` (`ClockEffectPublication.kt:46`) is `internal` with a `private set`. It is incremented only inside `resolveForClock` and is read only by tests. **It is not read by any production hot path.**

---

## 9. Ownership Graph

```text
StatusBarClockTweakHook (install scope)
    └── clockEffectPublication: ClockEffectPublication
            ├── ClockAbi  (Class/Field/Method metadata)
            ├── volatile ClockEffect?
            └── AtomicInteger failedTargetMask

MiuiStatusBarClockController instance
    └── secondTicker: SecondTicker (via Xposed additional instance field)
            ├── WeakReference(clockController)
            ├── context: Context  (mContext, used for ScreenStateController + Handler + DateFormat)
            ├── publication: ClockEffectPublication
            ├── Handler (from context.mainLooper)
            └── running/screenStateRegistered flags
```

- Controller is `WeakReference`.
- `SecondTicker` holds `Context` (mContext) because `ScreenStateController.addListener(context, this)` and `Handler(context.mainLooper)` require it; this is the same `mContext` passed at construction and is not a `View` or controller.
- `ClockEffectPublication` does not hold `Context`, `View`, controller, or calendar.

---

## 10. Hot-Path Cost Inventory

### 10.1 H1 steady-state (`updateTimeHook`)

| Operation | Cost | Classification |
|-----------|------|----------------|
| `ModuleHelper.getViewInfo` | Tag-map lookup | O(1), small |
| `currentClockStyleSnapshot()` | Volatile read of cached snapshot | O(1), no allocation |
| `buildClockText` | Selects an existing format `String` from `ClockStyleSnapshot`; if weather replacement enabled and `"tq"` present, `String.replace(...)` creates a new `String` output | No `ThreadLocal` here; may allocate a format `String` only when weather substitution occurs |
| `publication.currentEffect()` | Volatile read | O(1) |
| `effect.readController` / `effect.readCalendar` | `Field.get` | O(1); no source-visible temporary collection or object created by our code (JVM reflection internal allocation is not measured) |
| `clockFormatBuilder.get()` / `clockTextBuilder.get()` | `ThreadLocal.get` | O(1) amortized, `StringBuilder` reset in place |
| `effect.format(...)` | `Method.invoke(calendar, context, textSb, formatSb)` | `Object[]` for varargs allocated per call; `StringBuilder` content mutated in place; no per-char allocation beyond output |
| `clock.text = textSb.toString()` | `setText` + UI invalidation | Framework cost, unavoidable |

**NOT proven zero allocation.** `buildClockText` may allocate a weather-substituted `String`. `effect.format(...)` uses `Method.invoke` with three `Object` arguments, which allocates an `Object[]`. The `clockFormatBuilder` and `clockTextBuilder` `ThreadLocal` reuse happens after `buildClockText`, inside the `effect.format` path.

### 10.2 H2 steady-state (`SecondTicker.run`)

| Operation | Cost | Classification |
|-----------|------|----------------|
| `publication.currentEffect()` | Volatile read | O(1) |
| `effect.readCalendar` | `Field.get` | O(1); no source-visible temporary collection or object created by our code |
| `effect.setTimeInMillis(calendar, millis)` | `Method.invoke` with one `Long` arg | `Object[]` + `Long` boxing per tick; `setTimeInMillis` is a primitive `long` overload, so `Method.invoke` boxes it |
| `effect.writeIs24(controller, DateFormat.is24HourFormat(context))` | `DateFormat.is24HourFormat` + `Field.setBoolean` | `Field.setBoolean` is O(1) with no boxing. `DateFormat.is24HourFormat` is called every tick; its target HyperOS 1 implementation cost / Binder behavior is **NOT_PROVEN** by this harness |
| `effect.readClockListeners` | `Field.get` | O(1); no source-visible temporary collection or object created by our code |
| `as ArrayList<Any>` | Type cast | O(1) |
| `for (listener in clockListeners)` | `Iterator` over `ArrayList` | New `Iterator` object per tick (legacy compatibility debt) |
| `ModuleHelper.getViewInfo(clock, "showSeconds")` | Per-listener tag lookup | O(1) per listener |
| `effect.invokeUpdateTime(clock)` | `Method.invoke` with zero args | `Object[]` may be empty/cached; per listener |
| `handler.postDelayed(this, delay)` | `Handler` post | Message allocation (framework) |

**Deliberate retained debt:**

- `ArrayList` `Iterator` allocation per tick (`for` loop on `ArrayList` still creates an `Iterator`).
- `Method.invoke` argument boxing for `setTimeInMillis(long)`.
- `DateFormat.is24HourFormat(context)` per tick is retained from the original implementation. It is **not** a `MainModule.mPrefs` read. Its target-device implementation cost (e.g., `ContentResolver`/`Settings`/`Binder`) is **NOT_PROVEN** by this harness.

**Not claimed:** zero allocation, zero `DateFormat` cost, or zero `Iterator` cost.

---

## 11. Test Truthfulness Audit

All `SystemClockHotPathTest` H2 tests reviewed. Misleading or corrected items:

| Original / Current name | Issue | Action |
|-------------------------|-------|--------|
| `secondTickerH2_fatalFromCalendarSetTimeInMillis_preservesExactIdentity` | Name claimed a generic fatal; the test injects the fatal through `Method.invoke`, which wraps it in `InvocationTargetException` | Renamed to `secondTickerH2_wrappedFatalFromCalendarSetTimeInMillis_preservesExactIdentity` |
| `secondTickerH2_ownership_controllerRemainsWeakAndNoAndroidOwners` | Name implied "no Android owners"; `SecondTicker` does hold a `Context` (`mContext`) and the test does not inspect it | Renamed to `secondTickerH2_ownership_controllerWeakAndNoViewOrCalendarFields`; comment clarifies it checks controller is weak and no `View`/`calendar` instance fields |
| `secondTickerH2_nullPublication_lateClockDoesNotReceiveShowSeconds` | Runtime test adds a late listener manually; it does not invoke the actual `MiuiClock` constructor hook | Comment added explaining the test checks post-init controller state; constructor-hook guard is covered by `secondTickerH2_STRUCTURAL_constructorShowSecondsGatedByPublication` |
| `secondTickerH2_wrappedFatalFromCalendarSetTimeInMillis_preservesExactIdentity` | Assertion message said "direct fatal"; the test injects the fatal via `Method.invoke`, which wraps it in `InvocationTargetException` | Assertion message changed to "wrapped fatal must preserve exact original identity" |

Correctly named/retained structural tests (they read source text and assert substrings/order; they are **STRUCTURAL** even though the JUnit itself executes):

- `secondTickerH2_sourceStructural_hotPathHasNoForbiddenReflection`
- `secondTickerH2_STRUCTURAL_constructorShowSecondsGatedByPublication`
- `secondTickerH2_STRUCTURAL_guardedAndCallbackGuardShape`

These test names explicitly include `STRUCTURAL` and do not claim runtime behavior.

---

## 12. Remaining Debt (Explicit)

1. **Device runtime not proven** — Real HyperOS 1 / Android 14 `MiuiStatusBarClockController` and `MiuiClock` lifecycle timing is not exercised on a real device in this test harness. All `ClockResolver` unit tests use hand-rolled fakes.
2. **C1-style device evidence unavailable for C2** — No on-device Xposed logs or screenshots exist in the consolidation scope.
3. **`Iterator` allocation retained** — `SecondTicker.run` uses `for (listener in clockListeners)` over `ArrayList`, which allocates an `Iterator` per tick. This is accepted compatibility debt and not optimized in consolidation.
4. **`DateFormat.is24HourFormat(context)` per tick** — This is a system setting read on every tick, retained from the original implementation. It is not a `MainModule.mPrefs` read, but it is not zero cost.
5. **Constructor lifecycle proof is partially structural** — The late-clock test does not invoke the real `MiuiClock` constructor hook; the guard is proven by source-structural analysis.
6. **Direct fatal in H2 is not injected at runtime** — Wrapped-fatal exact identity is runtime-tested; direct `OutOfMemoryError`/`ThreadDeath`/`VirtualMachineError` is covered by the same `FatalErrors` boundary and structural `CallbackGuard` placement.
7. **Hot-path allocation not eliminated** — `Method.invoke` varargs arrays, boxed `Long` for `setTimeInMillis`, and `ArrayList` `Iterator` still allocate.

---

## 13. Final Verdict

- **H1 final verdict:** Architecture C `updateTime` hook correctly uses the frozen `ClockEffectPublication`, falls back to the original `updateTime` on cold/incomplete/ordinary failure, and does not perform generic Xposed member lookup in the steady-state per-update path.
- **H2 final verdict:** `SecondTicker.run` uses the frozen `ClockEffect` path, preserves legacy `ArrayList` + `Iterator` + `View` cast semantics, fails closed on null publication, and does not perform generic Xposed member lookup in the periodic hot path.
- **Null-publication verdict:** Fully fail-closed; late clocks are guarded in the constructor hook; no ticker is created; no generic H2 fallback; no resolver retry timer.
- **Listener compatibility verdict:** Three intentionally different policies (`initSecondTicker` soft `as? ArrayList`, `SecondTicker` hard `as ArrayList`, preference observer capped `MAX_CLOCK_LISTENERS`) are preserved and not conflated.
- **Failure/fatal verdict:** Ordinary failures abort the current tick in order and scheduling remains reachable; fatal errors are unwrapped and rethrown with exact identity, and `scheduleNextTick` is structurally unreachable after a fatal escapes `CallbackGuard`.
- **Ownership/publication verdict:** `ClockEffectPublication` holds only frozen metadata; `SecondTicker` uses a `WeakReference` controller and does not retain `View`/calendar instances.
- **Production changed during consolidation:** `false`
- **C2_CONSOLIDATION_READY_FOR_INDEPENDENT_AUDIT**

No production correctness defect found. No corrective production change performed. C3 not started.
