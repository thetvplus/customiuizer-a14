# C2-B3-A0 — SecondTicker H2 Publication/Lifecycle Preflight

**Repository:** `tomthenpc/customiuizer-a14`
**Branch:** `devin/a14-architecture-c-r14.20.0`
**Scope:** `SystemClockHooks.kt` SecondTicker (`H2`) pre-flight audit only
**Base SHA:** `6baa0ba629072919cdb4353f478efa45e22bb7c9`

> This is a docs-only artifact. No production code is implemented in this phase.

---

## 1. Exact H2 Graph (Current)

`SecondTicker` is a private inner class in `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemClockHooks.kt` (lines 739–814). `StatusBarClockTweakHook` installs the controller constructor hook (`scheduleHook`) at line 855; `initSecondTicker` is at lines 673–724.

Current `SecondTicker.run` (lines 786–807):

```text
1. if (!running) return
2. val controller = clockControllerRef.get()
   if (controller == null) { dispose(); return }
3. ModuleHelper.guarded {
4.   val calendar = XposedHelpers.getObjectField(controller, "mCalendar")
5.   XposedHelpers.callMethod(calendar, "setTimeInMillis", System.currentTimeMillis())
6.   XposedHelpers.setObjectField(controller, "mIs24", DateFormat.is24HourFormat(context))
7.   @Suppress("UNCHECKED_CAST")
   val clockListeners = XposedHelpers.getObjectField(controller, "mClockListeners") as ArrayList<Any>
8.   for (listener in clockListeners) {
        val clock = listener as View
        if (ModuleHelper.getViewInfo(clock, "showSeconds") != null) {
            XposedHelpers.callMethod(clock, "updateTime")
        }
      }
9. }
10. scheduleNextTick()
```

Current `initSecondTicker` (lines 673–724):

```text
1. val mContext = XposedHelpers.getObjectField(clockController, "mContext") as Context
2. val snapshot = currentClockStyleSnapshot() ?: ensureClockStyleSnapshot(mContext.resources)
3. val effectiveStatusBarSeconds = statusbarClockTweak && snapshot.showStatusBarSeconds
4. val effectiveCcSeconds = ccClockTweak && snapshot.showCCSeconds
5. val clockListeners = XposedHelpers.getObjectField(clockController, "mClockListeners") as? ArrayList<Any>
   if (clockListeners != null) {
       for (listener in clockListeners) {
           val clock = listener as? View ?: continue
           val clockName = ModuleHelper.getViewInfo(clock, "clockName") as? String ?: continue
           val showSeconds = when (clockName) { ... }
           if (showSeconds) { ModuleHelper.setViewInfo(clock, "showSeconds", true) }
           else { ModuleHelper.setViewInfo(clock, "showSeconds", null) }
       }
   }
6. val previousTicker = XposedHelpers.getAdditionalInstanceField(clockController, "secondTicker") as SecondTicker?
7. val needsTicker = effectiveStatusBarSeconds || effectiveCcSeconds
8. if (needsTicker) { create/reuse ticker; setAdditionalInstanceField(...); ticker.start() }
   else { dispose previous; remove field }
```

`StatusBarClockTweakHook` currently builds `ClockEffectPublication` at lines 1005–1009, **after** `scheduleHook` is defined. `scheduleHook` therefore cannot pass the publication to `initSecondTicker` today.

---

## 2. ClockEffectPublication Handoff Options

Three options were evaluated for sharing the existing local `ClockEffectPublication` across `scheduleHook`, `initSecondTicker` and `SecondTicker`:

| Option | Mechanism | Verdict |
|--------|-----------|---------|
| A — object-level `var` on `SystemClockHooks` | Strong singleton field holding the publication | Rejected. `SystemClockHooks` is an `object`; a top-level `var` would be static/global state. The question explicitly rules out static/global Android owner state, and a singleton publication field is unnecessary when the same value can be passed through the call chain. |
| B — make `ClockEffectPublication` a parameter of `initSecondTicker` and `SecondTicker` | Move the local `clockEffectPublication` declaration to before `scheduleHook`; pass it into `initSecondTicker(..., publication)` and into `SecondTicker(..., publication)` | **Selected.** Keeps the publication local to the `StatusBarClockTweakHook` install scope, does not create global state, and only `SecondTicker` (per-controller) strongly retains it. |
| C — look up the publication via `XposedHelpers.getAdditionalInstanceField(controller, ...)` at runtime | Store the publication as an additional instance field on the controller | Rejected. It adds an extra map access per tick and couples H2 to Xposed state; passing the reference directly is simpler and cheaper. |

**Proposed handoff flow:**

```text
StatusBarClockTweakHook
  ├─ move val clockEffectPublication = ClockResolver.resolveCore(...)?.let(::ClockEffectPublication)
  │   to before scheduleHook
  ├─ scheduleHook.intercept
  │     ├─ initSecondTicker(thisObject, statusbarClockTweak, ccClockTweak, clockEffectPublication)
  │     └─ TIME_SET receiver: initSecondTicker(owner, statusbarClockTweak, ccClockTweak, clockEffectPublication)
  ├─ preference observer: initSecondTicker(controller, statusbarClockTweak, ccClockTweak, clockEffectPublication)
  └─ updateTimeHook: unchanged usage of clockEffectPublication

initSecondTicker(..., publication: ClockEffectPublication?)
  └─ val ticker = SecondTicker(controller, mContext, ..., publication)

SecondTicker(..., private val publication: ClockEffectPublication?)
```

`SecondTicker` will strongly retain only `publication`, which itself retains only `ClockAbi` / `ClockEffect` / frozen `Field`/`Method`/`Class` metadata. No `Context`, `View`, controller or calendar instance is retained by the publication.

### `publication == null` contract

`publication` is nullable because `ClockResolver.resolveCore(...)?.let(::ClockEffectPublication)` may return `null` if the Architecture C Clock core cannot resolve. When `publication == null`, H2 must fail closed:

- Do **not** create or start a `SecondTicker`.
- Do **not** fall back to legacy/generic `XposedHelpers` reflection.
- Do **not** perform per-tick resolver or member discovery.
- Existing H1 behavior remains unchanged: H1 can still proceed original when its Architecture C effect is unavailable, because H1 has an original callback to fall through.

The `initSecondTicker` handoff contract is:

```text
if (needsTicker && publication != null) {
    create/reuse/start SecondTicker
} else {
    dispose/remove any existing SecondTicker
}
```

`showSeconds` metadata must not advertise an active custom seconds ticker when no ticker can run. If `publication == null`, `initSecondTicker` must not set `showSeconds` to `true` and must clear any existing `showSeconds` tags (or leave them null) for the clocks it processes. The minimum behavior-compatible implementation is to treat `publication == null` the same as `needsTicker == false` for the purpose of tag assignment and ticker creation.

---

## 3. Controller / Listener Lifecycle Facts

All observations below are derived from the current source, `ClockResolver`, and the existing `SystemClockHotPathTest` fakes. Real HyperOS/SystemUI lifecycle evidence is **NOT_PROVEN**.

### FACT

- `ClockResolver.resolveControllerClass` requires the `mClockListeners` `Field` to exist for `ControllerCapability`; a missing field makes the whole ABI `null`.
- Current `initSecondTicker` defensively accepts a `null` / non-`ArrayList` value with `as? ArrayList<Any>` (line 685) and simply skips the tag loop.
- Current `SecondTicker.run` re-reads `mClockListeners` every tick.
- The current test fakes initialize `mClockListeners` as an `ArrayList`, but this does **not** prove real SystemUI timing.

### NOT_PROVEN

- Real `MiuiStatusBarClockController` guarantees a non-null `mClockListeners` immediately after construction.
- Exact timing at which real clocks are inserted into `mClockListeners`.
- Whether `mClockListeners` can be mutated during a single `SecondTicker.run` traversal.

### DESIGN REQUIREMENT

- `initSecondTicker` must not require an eligible real clock.
- An empty or unavailable listener state must fail closed and allow a later tick.
- Later listener availability must allow calibration without retaining a `View`.

**Conclusion:** An eligible real clock is **not guaranteed** at `initSecondTicker` time. H2 cannot depend on calibration completing inside `initSecondTicker`.

---

## 4. Cold-Complete H2 Flow

When `ClockAbi.calendarCold != null`, `ClockEffectPublication` constructs the `ClockEffect` at publication construction time:

```kotlin
effect = abi.calendarCold?.let { ClockEffect(abi, it) }
```

Therefore `publication.currentEffect() != null` before the `SecondTicker` is created. The per-second hot path becomes:

```text
SecondTicker.run
  ├─ if (!running) return
  ├─ val controller = clockControllerRef.get() ?: dispose(); return
  ├─ ModuleHelper.guarded {
  │     val effect = publication.currentEffect() ?: return@guarded
  │     val calendar = effect.readCalendar(controller) ?: return@guarded
  │     if (!effect.setTimeInMillis(calendar, now)) return@guarded
  │     if (!effect.writeIs24(controller, is24)) return@guarded
  │     @Suppress("UNCHECKED_CAST")
  │     val clockListeners = effect.readClockListeners(controller) as ArrayList<Any>
  │     for (listener in clockListeners) {
  │         val clock = listener as View
  │         if (ModuleHelper.getViewInfo(clock, "showSeconds") == null) continue
  │         if (!effect.invokeUpdateTime(clock)) return@guarded
  │     }
  │  }
  └─ scheduleNextTick()
```

This preserves the legacy traversal semantics: `ArrayList<Any>` cast, `Iterator` allocation, `listener as View` cast, and fail-fast `ConcurrentModificationException`. The `Iterator` allocation is recorded as an explicit later optimization debt; see section 10.

**No additional capability is missing for the cold-complete case.** Existing `ClockEffect` methods cover `readCalendar`, `setTimeInMillis`, `writeIs24`, `readClockListeners` and `invokeUpdateTime`.

---

## 5. Cold-Incomplete H2 Flow

When `ClockAbi.calendarCold == null`, `publication.currentEffect()` is `null` at `SecondTicker` construction. H2 must not fall back to generic `XposedHelpers` reflection every second.

The cold-incomplete flow is:

```text
SecondTicker.run
  ├─ ... controller non-null ...
  ├─ var effect = publication.currentEffect()
  ├─ ModuleHelper.guarded {
  │     if (effect == null) {
  │         // Bounded per-target calibration; unresolved listener discovery may recur per tick.
  │         @Suppress("UNCHECKED_CAST")
  │         val clockListeners = publication.readClockListeners(controller) as ArrayList<Any>
  │         for (listener in clockListeners) {
  │             val clock = listener as View
  │             if (ModuleHelper.getViewInfo(clock, "showSeconds") == null) continue
  │             effect = publication.resolveForClock(clock, clock.context.javaClass)
  │             if (effect != null) break
  │         }
  │     }
  │     if (effect == null) return@guarded
  │
  │     // Now run the cold-complete graph from section 4.
  │     val calendar = effect.readCalendar(controller) ?: return@guarded
  │     ...
  │  }
  └─ scheduleNextTick()
```

Key points:

- The slow path is triggered only while `currentEffect()` is `null`.
- The calibration target is a real clock `View` taken from `mClockListeners`; it is **not** strongly retained.
- `publication.resolveForClock` uses the frozen `ClockAbi` (target classes, controller field, calendar field) and, if necessary, `ClockResolver.resolveCalendarFromRuntime`.
- `resolveForClock` has a per-target-class `failedTargetMask`; runtime member discovery does **not** recur for a target already marked failed.
- Unresolved listener discovery may recur per tick until an eligible target appears; the `failedTargetMask` only bounds the expensive calendar resolution for a known-bad target.

**Missing publication capability:** `ClockEffectPublication` does not currently expose a way to read `mClockListeners` before an `ClockEffect` exists. The aggregate `ClockAbi` already contains `ControllerCapability.clockListenersField`, so the publication can be extended with an internal `readClockListeners(controller)` helper (or expose its `abi`) without splitting the ABI.

---

## 6. Empty-Listener Lifecycle Case

If `mClockListeners` is empty when the ticker starts (or remains empty on a later tick), the ticker still runs because `needsTicker` is independent of the listener count. This section assumes `publication != null`; the `publication == null` case is handled by the null contract in section 2.

Behavior:

- In the cold-incomplete `effect == null` case, listener enumeration comes from the publication-level frozen controller-field helper, i.e. `publication.readClockListeners(controller)`, not from a `ClockEffect`.
- `publication.readClockListeners(controller)` returns an empty `List<*>.
- If `effect == null` and the list is empty, no calibration can occur; the `guarded` block returns and `scheduleNextTick()` runs.
- No generic `XposedHelpers` fallback is performed.
- The calendar and `mIs24` are **not** updated until at least one eligible real clock appears.

This is acceptable because:

- No clock view is visible while the listener list is empty, so there is no user-visible text to update.
- Once a real clock is added, the next tick (or the `MiuiClock` constructor `updateTime` hook) resolves the calendar and normal operation resumes.

This is an intentional deviation from the legacy generic path, which would call `setTimeInMillis` / `setObjectField` every second regardless of whether a real clock existed.

---

## 7. Proposed Bounded Calibration Strategy

1. **Pre-warm in `initSecondTicker`** (opportunistic):
   - If `publication?.currentEffect() == null` and `mClockListeners` is non-empty, scan listeners for the first `View` with a known `clockName`.
   - Call `publication.resolveForClock(clock, clock.context.javaClass)`.
   - Stop on first success.
2. **Runtime calibration in `SecondTicker.run`** (per-tick bounded fallback):
   - If `publication.currentEffect()` is still `null`, during the listener loop attempt `resolveForClock` on the first `View` that has a `showSeconds` tag.
   - The `failedTargetMask` in `ClockEffectPublication` prevents repeated runtime member discovery for a target class already known to be bad; unresolved listener discovery may recur per tick until an eligible target appears.
3. **No permanent generic fallback.**
4. **No strong clock retention.** The listener reference is local to the loop.

This is **Option A + Option B** from the task brief, not Option C (ABI split). No ABI split is required (see section 11).

---

## 8. Ordinary Failure Semantics

Current legacy behavior inside `ModuleHelper.guarded`:

- Any non-fatal reflection failure aborts the rest of the tick.
- The failure is logged by `CallbackGuard.guarded`.
- `scheduleNextTick()` still runs.

Target Architecture C behavior for H2:

- Each `ClockEffect` operation returns a sentinel (`null` / `false`) on non-fatal failure.
- `SecondTicker.run` checks the return value and, on failure, executes `return@guarded`.
- `scheduleNextTick()` still runs.
- **No per-tick logging is added for frozen Effect method failures.** `ClockEffect` methods already catch non-fatal `Throwable`, call `FatalErrors.unwrapAndRethrowIfFatal`, and return the sentinel. The `guarded` wrapper therefore receives no exception for those failures and does not log.

The legacy listener-traversal failures (malformed `mClockListeners` / non-`ArrayList`, `listener as View` cast failure, `ConcurrentModificationException`) are still thrown by the `ArrayList`/`Iterator`/`as View` code and are caught/logged by `guarded`; `scheduleNextTick()` still runs. This preserves fail-fast traversal behavior by default.

This removes the per-second log spam that the generic `XposedHelpers` method-lookup path produced, while preserving the key semantic: **abort current tick, schedule next tick**.

Mapping table:

| Legacy failure | Legacy outcome | Architecture C outcome |
|----------------|----------------|------------------------|
| `getObjectField(controller, "mCalendar")` fails | `guarded` logs, returns, schedules next | `effect.readCalendar(controller)` returns `null`; `return@guarded`; schedules next |
| `callMethod(calendar, "setTimeInMillis", ...)` fails | `guarded` logs, returns, schedules next | `effect.setTimeInMillis(...)` returns `false`; `return@guarded`; schedules next |
| `setObjectField(controller, "mIs24", ...)` fails | `guarded` logs, returns, schedules next | `effect.writeIs24(...)` returns `false`; `return@guarded`; schedules next |
| `getObjectField(controller, "mClockListeners")` fails | `guarded` logs, returns, schedules next | `effect.readClockListeners(...)` returns `null`; `return@guarded`; schedules next |
| `callMethod(clock, "updateTime")` fails | `guarded` logs, returns, schedules next | `effect.invokeUpdateTime(clock)` returns `false`; `return@guarded`; schedules next |

---

## 9. Fatal Semantics

Current legacy behavior:

- Direct `OutOfMemoryError` / `VirtualMachineError` / `ThreadDeath` inside the `guarded` block is rethrown by `CallbackGuard.guarded`; `scheduleNextTick()` is **not** reached.
- A fatal thrown by the invoked method is wrapped by Java reflection as `InvocationTargetException`, then re-wrapped by `XposedHelpers.callMethod` as `XposedHelpers.InvocationTargetError`.
- `CallbackGuard.guarded` does **not** unwrap `InvocationTargetError`; it treats it as a non-fatal `Error`, logs it, and schedules the next tick. This is the documented `LEGACY_WRAPPED_FATAL_BUG`.

Architecture C target:

- `ClockEffect` methods invoke frozen `Field`/`Method` objects and wrap every call with `FatalErrors.unwrapAndRethrowIfFatal(t)`.
- `InvocationTargetException` and `XposedHelpers.InvocationTargetError` are unwrapped up to 4 levels; if the cause is a fatal `Error`, the **exact original fatal** is rethrown.
- `CallbackGuard.guarded` then sees the original fatal and rethrows it; `scheduleNextTick()` is **not** reached.

This fixes the wrapped-fatal bug while preserving the exact original fatal identity.

---

## 10. Listener Iteration Semantics

Default B3 iteration (legacy-compatible):

```kotlin
@Suppress("UNCHECKED_CAST")
val clockListeners = effect.readClockListeners(controller) as ArrayList<Any>
for (listener in clockListeners) {
    val clock = listener as View
    if (ModuleHelper.getViewInfo(clock, "showSeconds") == null) continue
    if (!effect.invokeUpdateTime(clock)) return@guarded
}
```

This preserves the legacy semantics:

- `mClockListeners` is cast to `ArrayList<Any>` (matches `XposedHelpers.getObjectField(...)` `as ArrayList<Any>`).
- `for (listener in clockListeners)` uses the `ArrayList` `Iterator`.
- `listener as View` throws `ClassCastException` for non-`View` entries; `guarded` catches and logs.
- Unbounded; no `MAX_CLOCK_LISTENERS`.
- `ConcurrentModificationException` fail-fast is preserved.

The `Iterator` allocation is recorded as an explicit later optimization debt. It is out of scope for B3 because:

- There is no repository evidence that `mClockListeners` cannot be mutated during a single `SecondTicker.run` traversal.
- An index/while loop would not detect concurrent modification and would change the fail-fast contract.
- `as? View ?: continue` would silently skip non-View entries instead of aborting the tick.

If future device/lifecycle evidence proves mutation cannot occur during a traversal, the index/while version must advance the index before any `continue` (e.g., a `for (i in 0 until size)` construct where the Kotlin compiler increments `i` at the loop header). That is a separate, measured optimization.

---

## 11. Ownership Graph

```text
MiuiStatusBarClockController constructor
  ├─ XposedHelpers additional-instance field: SecondTicker (strong while controller alive)
  └─ SecondTicker
       ├─ WeakReference<Any> clockControllerRef
       ├─ Context context (controller's mContext; unchanged)
       ├─ Handler handler (context.mainLooper; unchanged)
       ├─ Boolean running / screenStateRegistered (unchanged)
       └─ ClockEffectPublication publication (new; strong)
            ├─ ClockAbi (Class/Field/Method metadata only)
            │     ├─ ControllerCapability
            │     ├─ Array<ClockTargetCapability>
            │     └─ CalendarCapability?
            ├─ ClockEffect? (Class/Field/Method metadata + CalendarCapability)
            └─ AtomicInteger failedTargetMask

ScreenStateController (process singleton)
  └─ strong reference to SecondTicker while screenStateRegistered
```

Adding `publication` to `SecondTicker` does **not** create:

- Strong `View` retention.
- Strong controller retention (`clockControllerRef` remains `WeakReference`).
- Strong calendar instance retention (`ClockEffect` only holds the resolved `CalendarCapability`, not a calendar object).
- Strong `Resources` retention (resources are still accessed transiently via `context` only).

---

## 12. Exact B3 Implementation Scope

B3 is the implementation of the H2 `SecondTicker` Architecture C migration. Its scope is:

- Move the local `ClockEffectPublication` construction in `StatusBarClockTweakHook` to before `scheduleHook`.
- Add `publication: ClockEffectPublication?` to `initSecondTicker` and to `SecondTicker`.
- Update the three call sites in `SystemClockHooks.kt` (`scheduleHook`, `TIME_SET` receiver, preference observer) to pass the publication.
- Implement the `publication == null` fail-closed contract in `initSecondTicker`: if `needsTicker && publication != null`, create/reuse/start the `SecondTicker`; otherwise dispose/remove any existing one and do not set `showSeconds` tags. Do not fall back to legacy/generic `XposedHelpers` reflection or per-tick member discovery.
- Implement the `SecondTicker.run` graph from section 4 (cold-complete) and section 5 (cold-incomplete calibration).
- Add a `ClockEffectPublication.readClockListeners(controller)` helper (or equivalent access to `ControllerCapability.clockListenersField`) so `SecondTicker` can enumerate listeners before an `ClockEffect` is published.
- Replace the generic `XposedHelpers.getObjectField` / `callMethod` / `setObjectField` calls in `SecondTicker.run` with frozen `ClockEffect` methods.
- Preserve the legacy `ArrayList<Any>` cast and `for` (Iterator) listener traversal; keep it unbounded and use `listener as View`. Document the `Iterator` allocation as an explicit later optimization debt.
- Keep `ModuleHelper.guarded` as the outer boundary; rely on `ClockEffect` sentinel returns for ordinary failures and on `FatalErrors.unwrapAndRethrowIfFatal` for fatal identity.
- Preserve the existing scheduling, screen-on/screen-off, `WeakReference` and disposal semantics exactly.

**Explicit non-scope (must not change in B3):**

- `MAX_CLOCK_LISTENERS = 64` is **not** introduced into `SecondTicker`; the loop remains unbounded.
- `clockName` / `showSeconds` `ViewInfo` metadata migration is out of scope; `ModuleHelper.getViewInfo(clock, "showSeconds")` is retained as-is.
- `ClockStyleSnapshot` is not modified.
- `NetSpeed` is not modified.
- `CCClockTweakHook`, `CCClockCenterAlignHook`, `FakeStatusBarClockController` and weather internals are not modified.
- C3 is not started.
- No ABI split (see section 13).

---

## 13. Clock ABI Split Required?

**NO.**

Reason: The aggregate `ClockAbi` already separates concerns:

```kotlin
internal data class ClockAbi(
    val controller: ControllerCapability,
    val targets: Array<ClockTargetCapability>,
    val calendarCold: CalendarCapability?,
)
```

`ControllerCapability` (calendar field, clock-listeners field, `mIs24` field) is resolved independently of `CalendarCapability` and is always present when the ABI is built. H2 needs to read `mClockListeners` before the calendar is resolved, but this does not require a new `ClockEffect` split; it only requires a publication-level helper that uses the already-resolved `controller.clockListenersField`.

If H2 later needs to read the controller / listeners before `ClockEffect` exists, the cleanest path is:

- `ClockEffectPublication` exposes an internal `readClockListeners(controller): List<*>?` using `abi.controller.clockListenersField.get(controller)`.

This keeps `ClockEffect` focused on the calendar-dependent hot path and does not introduce a separate `ClockControllerEffect` or `ClockRuntime` abstraction.

---

## 14. B3 Implementation-Test Debt

Before the B3 production implementation can be considered complete, the following tests must be added or updated. They are **not** currently proven or implemented.

### H2 calibration

- `SecondTicker` can run the cold-complete path using `publication.currentEffect()` without triggering runtime calendar calibration.
- `SecondTicker` with an empty `mClockListeners` state schedules the next tick without performing generic `XposedHelpers` reflection.
- A later eligible real clock inserted into `mClockListeners` can publish the `ClockEffect`.
- A failed target class does not repeat runtime calendar resolution on subsequent ticks.

### H2 listener traversal

- Listener traversal remains unbounded (`MAX_CLOCK_LISTENERS = 64` is not used in `SecondTicker.run`).
- Malformed listener behavior matches the selected legacy-compatible policy (`ArrayList` cast, `as View`, `Iterator` fail-fast).

### H2 failure semantics

- An ordinary `ClockEffect` failure aborts the rest of the tick and the next schedule is still possible.
- A direct fatal error is rethrown with exact identity and `scheduleNextTick()` is not reached.
- A fatal wrapped in `InvocationTargetException` / `XposedHelpers.InvocationTargetError` is unwrapped and rethrown with exact identity; `scheduleNextTick()` is not reached.

### H2 ownership

- `SecondTicker` does not strongly retain a `View`, the controller, or a calendar instance through the new `publication` handoff.

### Nullable publication

- `publication == null` does not create/start a permanent no-op `SecondTicker`.
- `publication == null` does not execute a generic `XposedHelpers` reflection fallback.
- Nullable-publication fail-closed behavior leaves no stale active `SecondTicker`.
- Cold-incomplete empty listener enumeration uses the publication-level frozen controller-field helper (`publication.readClockListeners`).

---

C2_B3_A0_H2_PREFLIGHT_READY_FOR_INDEPENDENT_AUDIT
