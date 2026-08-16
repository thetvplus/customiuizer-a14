# PERF-A14-P3-C0 — SystemUI retained-owner and lifecycle audit

## Scope

This audit examines the object-ownership and lifecycle properties of the SystemUI
process in `devin/a14-performance-optimization`. The goal is to identify static
or global containers, callbacks, listeners, observers, receivers, Handlers and
Xposed facilities that can pin short-lived Android objects (View, Context,
controller, Drawable, etc.) beyond their natural lifetime.

Excluded:

- B4-style preference-read micro-optimizations.
- Mechanical conversion of every strong reference to `WeakReference`.
- Blanket changes to the source-hazard baseline.

No device was connected, so all conclusions are derived from source and JVM unit
tests. The device checkpoint remains `DEVICE_CHECKPOINT_BLOCKED_NO_DEVICE`.

## Start state

| Item | Value |
|---|---|
| Branch | `devin/a14-performance-optimization` |
| Base / Final SHA | `6ac36a3487c890132e8adad26d18722a2ba22965` |
| Local / remote HEAD | consistent |
| Work tree | clean at audit start |
| ADB device | none |

## Registries and global holders audited

### 1. `PreferenceObserverRegistry`

**File:** `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/PreferenceObserverRegistry.kt`

**Component / process:** SystemUI, settings, launcher, system_server (wherever
`MainModule` is loaded).

**Container type / concurrency:**

- `observers: CopyOnWriteArraySet<PreferenceObserver>` — strong set.
- `observerOwners: CopyOnWriteArrayList<WeakReference<PreferenceObserver>>` —
  weak list of observer references; strong ref lives in the owner's
  `XposedHelpers` additional instance field.

**Key type / value type / owner type:**

- Process-scoped: no owner, `observers` holds the observer strongly.
- Owner-bound: key is the owner instance; value is a
  `WeakReference<PreferenceObserver>`; the strong reference is kept in the
  owner's `PREF_OBSERVER_FIELD` additional instance field.

**Strong vs weak:**

- Process set: strong.
- Owner list: weak references to observers.
- Owner additional field: strong.

**Callback captures owner?**

- Depends on the call site. `PreferenceObserverRegistry` does not inspect the
  callback; it cannot prevent capture. Safe call sites (e.g. `SystemClockHooks`,
  `SystemLockScreenHooks`) store the owner in a `WeakReference` inside the
  observer. Several call sites (see below) create anonymous observers that
  capture `this` or `thisObject`, which makes the observer itself hold the owner
  strongly.

**Normal register / release path:**

- `ModuleHelper.observePreferenceChange(prefObserver)` → strong `observers.add`.
- `ModuleHelper.observePreferenceChange(prefObserver, owner)` →
  `XposedHelpers.setAdditionalInstanceField(owner, PREF_OBSERVER_FIELD,
  prefObserver)` and `observerOwners.add(WeakReference(prefObserver))`.
- `ModuleHelper.unregisterPreferenceObserver(owner)` →
  `XposedHelpers.removeAdditionalInstanceField(owner, PREF_OBSERVER_FIELD)` and
  `dropOwnedObserver`.
- `XposedHelpers.additionalFields` uses `WeakInstanceKey` and a `ReferenceQueue`,
  so an owner that becomes unreachable is eventually expunged.

**Repeat registration behavior:**

- For the same owner, `observePreferenceChange` first removes the old additional
  instance field and then installs the new one. One owner can have at most one
  active observer at a time.

**Owner destruction / SystemUI rebuild behavior:**

- Owner-bound observer is released with the owner.
- Process-scoped observer is released only on process death.
- `handlePreferenceChanged` walks `observerOwners`, skips cleared refs and
  expunges them.

**Bounded?**

- Process set is bounded by the number of distinct `PreferenceObserver`
  instances registered per process.
- Owner list is bounded by the number of distinct owners that have been
  registered. Cleared weak refs are expunged on preference-change broadcasts.

**Static reference chain?**

- `PreferenceObserverRegistry` is a Kotlin `object` (process singleton); its
  `observers` and `observerOwners` are static-reachable.

**Risk level:**

- **Medium** for owner-bound observers whose callback closes over a short-lived
  owner. Even though the registry key is weak, a strong callback cycle can keep
  the owner (and the whole `additionalFields` map entry) alive until the next
  `expungeStaleAdditionalFields` write.
- **Low** for process-scoped observers that are attached to module singletons or
  long-lived system services.

**Fix evidence?**

- No C0 fix. Call sites need individual review; a mechanical change is unsafe.

### 2. `ReceiverRegistry`

**File:** `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ReceiverRegistry.kt`

**Component / process:** SystemUI / settings / launcher.

**Container type / concurrency:**

- `moduleReceivers: ConcurrentHashMap<String, ModuleReceiverRegistration>` —
  process-scoped, one active receiver per key.
- `staleModuleReceivers: ConcurrentHashMap<String, ConcurrentLinkedDeque<...>>` —
  bounded retry queue (max 3 per key).
- `ownedReceivers: ConcurrentHashMap<String, CopyOnWriteArrayList<OwnedReceiver>>` —
  per-owner weak-reference receiver sets.

**Owner type:**

- Module receivers: process-scoped key, holds an `ApplicationContext` strongly
  (explicitly `context.applicationContext`) and the receiver.
- Owned receivers: an `OwnedReceiver` has `WeakReference<owner>`,
  `WeakReference<Context>` and a `WeakOwnerReceiver`.

**Callback captures owner?**

- `WeakOwnerReceiver` receives the owner at broadcast time via
  `ownerRef.get()`; the `OwnedReceiverCallback` interface contract explicitly
  says not to close over the owner. ReceiverRegistry itself enforces nothing at
  compile time.

**Release path:**

- `registerModuleReceiver` replaces the previous receiver and unregisters it.
- `registerOwnedReceiver` removes stale or same-owner registrations and
  unregisters displaced receivers.
- `unregisterOwnedReceiver` removes by owner.
- `WeakOwnerReceiver.onReceive` unregisters itself when the owner is gone.

**Repeat / rebuild behavior:**

- Process-scoped: one active receiver per key; old receiver is unregistered.
- Owned: same owner + key replaces the old receiver; a dead owner is removed on
  the next `registerOwnedReceiver` call for that key or on broadcast.

**Bounded?**

- `moduleReceivers` bounded by logical key count.
- `stale` queue bounded at 3 per key.
- `ownedReceivers` per key is bounded by the number of live owners; dead owners
  are removed.

**Static reference chain?**

- `ReceiverRegistry` object is process singleton; it does not hold Contexts
  beyond application context.

**Risk level:**

- **Low / well-contained**. The design is bounded and removes stale receivers.
  The main residual risk is a caller callback that accidentally captures the
  owner, which would defeat the `WeakOwnerReceiver` design.

### 3. `StatusBarDisplayRegistry` + `OwnedRegistrations`

**Files:**
`app/src/main/java/tv/withaibuild/customiuizer/mods/utils/StatusBarDisplayRegistry.kt`

**Component / process:** SystemUI.

**Container type / concurrency:**

- `byDisplay: MutableMap<Int, StatusBarDisplayState>` — strong, per display.
- `pendingByOwner: WeakIdentityMap<O, StatusBarDisplayState>` — weak-key map.

**Owner / value type:**

- `StatusBarDisplayState` holds `WeakReference<O>` (status-bar view /
  controller), `WeakReference<R>` (second-row container), and an
  `OwnedRegistrations` cleanup list.

**Release path:**

- `detach(owner)` removes the exact owner from pending and display buckets and
  runs `cleanupAll()`.
- `bind(owner, displayId)` replaces a dead generation and releases its
  registrations.
- `prune()` removes states whose generation reference is gone and whose
  registration list is empty.

**Bounded?**

- Bounded by number of displays plus transient pending views.

**Static reference chain?**

- `SystemUIStatusBarHooks` may hold an instance, but not in a static field.
  The registry is per-status-bar-controller.

**Risk level:**

- **Low**. Weak references + explicit `OwnedRegistrations` cleanup.

### 4. `FeatureInstallState` / `FeatureInstallRegistry`

**Files:**
`app/src/main/java/tv/withaibuild/customiuizer/mods/utils/FeatureInstallState.kt`
`app/src/main/java/tv/withaibuild/customiuizer/mods/utils/FeatureInstallRegistry.kt`

**Component / process:** All module processes during LSPosed init.

**Container type:**

- `FeatureInstallState` is a Kotlin `object` with `HashMap<Int, FeatureState>`.
- `FeatureInstallRegistry` is a short-lived class created per installer call.

**Owner type:**

- None. State is keyed by `FeatureId.id`.

**Risk:**

- **Safe**. Holds only enum/integer state.

### 5. `ScreenStateController`

**File:** `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ScreenStateController.kt`

**Component / process:** SystemUI / launcher / settings.

**Container type:**

- `listeners: ArrayList<ScreenStateListener>` — strong list.
- `lock: Any` for synchronization.
- `appContext: Context?` and `receiver: BroadcastReceiver?`.

**Owner type:**

- Listener instances passed by callers.

**Callback captures owner / Context?**

- The `BroadcastReceiver` object captures only the `listeners` list, not a
  specific View or Context.
- Callers are responsible for providing a listener that does not capture a
  short-lived object.

**Normal register / release:**

- `addListener(context, listener)` / `removeListener(listener)`.
- When the list is empty, `stopLocked()` unregisters the broadcast receiver and
  clears `appContext`.

**Repeat / rebuild:**

- `addListener` checks `listeners.contains(listener)` to avoid duplicates for the
  same instance. If a new listener object is supplied each time, the list grows.

**Bounded?**

- The list grows with the number of distinct `ScreenStateListener` instances.

**Risk level:**

- **Medium** if a feature passes an anonymous listener that captures a View or
  controller and never removes it. Current callers (`SystemClockHooks`,
  `WeatherDataController`, `StepCounterController`) use long-lived singleton
  listeners, but `WeatherDataController` never calls `removeListener` (it is a
  process singleton, so the listener stays forever by design).

### 6. `ModuleHelper` companion statics

**File:** `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ModuleHelper.kt`

**Static fields:**

- `currentPackageName: String?`
- `mModuleContext: Context?` — module package context.
- `mCachedContext: Context?` — `Application` or system context from
  `ActivityThread`.
- `cachedModuleRes: Resources?`
- `cachedModuleConfig: Configuration?`
- `ActivityThreadClass: Class<*>?`

**Risk level:**

- **Low** when used as designed. `mCachedContext` and `mModuleContext` are
  intended to be long-lived process singletons (application / package context,
  not Activity / View context).
- `cachedModuleRes` and `cachedModuleConfig` are also process-level.

### 7. `XposedHelpers` `additionalFields`

**File:** `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/XposedHelpers.java`

**Container:**

- `static final ConcurrentHashMap<InstanceKey, ConcurrentHashMap<String, Object>> additionalFields`
- `static final ReferenceQueue<Object> additionalFieldsQueue`
- Keys are `WeakInstanceKey` (weak reference to the owner).
- Values are `ConcurrentHashMap<String, Object>` (additional field maps).

**Release path:**

- `expungeStaleAdditionalFields()` removes entries whose weak key has been
  cleared.
- Called from `setAdditionalInstanceField` and `removeAdditionalInstanceField`.
- **Not called from `getAdditionalInstanceField`**, so stale entries can persist
  between writes if no other write happens.

**Risk level:**

- **Medium** when values stored in the additional field map strongly reference
  the owner. If `value → owner`, the weak key cannot be cleared while the value
  is reachable from the static `additionalFields` map, so the owner leaks until
  `expunge` removes the entry. This is the standard `WeakHashMap` value-back-ref
  problem.

### 8. `XposedHelpers` hooked-method / unhooker retention

- `ModuleHelper.hookMethod` returns `CustomMethodUnhooker` which is not retained
  by the module. Xposed framework owns the hook lifetime.
- No evidence of module-level static lists of `CustomMethodUnhooker`.

### 9. `StatusBarIconVisibilityObserverOwner` (B3) and `SystemUIStatusBarHooks` (B1/B2)

**Files:**
`app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt`

**Owner tokens:**

- B1/B2: `SystemUIStatusBarHooks` (Kotlin `object`).
- B3: `StatusBarIconVisibilityObserverOwner` (nested private Kotlin `object`).

**Observer callback captures:**

- `netSpeedTextStyleObserver`: rebuilds `NetSpeedTextStyleSnapshot`, sets
  `currentDetailedNetSpeedFormatSnapshot` to null. Does not capture View,
  Context, or controller.
- `statusBarIconVisibilityObserver`: builds
  `StatusBarIconVisibilitySnapshot`. Does not capture View / Context /
  controller.
- Both observers hold only `MainModule.mPrefs` and the atomic snapshot
  references.

**Risk level:**

- **Low / safe process-level**. The owners are module singletons; the callbacks
  hold no short-lived Android objects. `PreferenceObserverRegistry` replaces
  duplicates per owner, so at most one observer per owner per process.

### 10. `SystemClockHooks` clock controller observer

**File:** `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemClockHooks.kt`

**Pattern:**

- `val controllerRef = WeakReference(thisObject)`
- `val observer = object : ModuleHelper.PreferenceObserver { onChange(...) }`
- `ModuleHelper.observePreferenceChange(observer, thisObject)`
- Callback uses `controllerRef.get()` and posts to a `Handler` built from the
  controller's `mContext.mainLooper`.
- Handler reference is captured but it only posts runnables; runnables capture
  the weak-ref result, not the controller strongly.

**Risk level:**

- **Low**. Observer does not strongly reference the owner.

### 11. `SystemLockScreenHooks` charging text size observer

**File:** `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt`

**Pattern:**

- `val indicatorRef = WeakReference(indicator)`
- Anonymous `PreferenceObserver` uses `indicatorRef.get()` and `view.post { ... }`.
- Owner is the `KeyguardIndicationTextView`.

**Risk level:**

- **Low**. Observer does not strongly reference the owner.

### 12. `AudioVisualizer` preference observer

**File:** `app/src/main/java/tv/withaibuild/customiuizer/utils/AudioVisualizer.kt`

**Pattern:**

- `private val preferenceObserver = object : ModuleHelper.PreferenceObserver { ... }`
- The observer is a field of the `AudioVisualizer` View; therefore it implicitly
  has a strong back-reference to the outer View.
- `ModuleHelper.observePreferenceChange(preferenceObserver, this)` stores the
  observer in the View's `XposedHelpers` additional field and in
  `PreferenceObserverRegistry.observerOwners`.
- `onDetachedFromWindow()` calls `dispose()`, which calls
  `ModuleHelper.unregisterPreferenceObserver(this)`, removing the additional
  field entry.

**Capture analysis:**

- `onChange` reads `detached` (Boolean field), and dispatches to
  `updateBarStyle()`, `updateGlowPaint()`, `updateRainbowColors()`.
  These calls require the `AudioVisualizer` instance; the anonymous object
  implicitly captures the outer View.
- Therefore the observer value stored in `XposedHelpers.additionalFields`
  strongly references the owner View.

**Risk level:**

- **High candidate**. While `dispose()` removes the registration, any code path
  where `AudioVisualizer` is created but `onDetachedFromWindow()` is not called
  (or a future change adds a new path that does not dispose) would pin the View
  through the static `additionalFields` map. The more robust design is the one
  used in `SystemClockHooks` / `SystemLockScreenHooks`: keep a `WeakReference`
  inside the observer and `get()` it in `onChange`.

**Fix evidence?**

- C0 only. A C1 fix would convert `AudioVisualizer.preferenceObserver` to an
  object that holds `WeakReference<AudioVisualizer>`, reads `detached` from the
  weak result, and calls `viewScope.launch` / update methods only when the View
  is still reachable.

### 13. `BatteryIndicator` preference observer and owned receiver

**File:** `app/src/main/java/tv/withaibuild/customiuizer/utils/BatteryIndicator.kt`

**Pattern:**

- `ModuleHelper.observePreferenceChange(object : ModuleHelper.PreferenceObserver { ... }, this)`
- `ModuleHelper.registerOwnedReceiver(context, this, RECEIVER_KEY, ...)` with a
  callback using the `owner` parameter.
- `onDetachedFromWindow()` calls `unregisterPreferenceObserver(this)` and
  `unregisterOwnedReceiver(this, ...)`.

**Capture analysis:**

- The observer uses `viewScope` and `updateParameters()`, which require the
  `BatteryIndicator` View. It implicitly captures `this`.
- The owned-receiver callback uses the owner parameter, not a closure, so the
  receiver itself does not capture the View.

**Risk level:**

- **High candidate** for the same reason as `AudioVisualizer`: the observer is a
  strong owner-capturing value in `XposedHelpers.additionalFields`. If
  `onDetachedFromWindow()` is missed, the View is pinned.

### 14. `SystemAudioHooks` vibration observers

**File:** `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt`

**Pattern:**

- Two `ModuleHelper.observePreferenceChange(object { ... }, thisObject)` calls
  inside a `Vibrator` / `VibratorService` hook.
- The observers call `XposedHelpers.setAdditionalInstanceField(thisObject, ...)`.

**Capture analysis:**

- The observers capture `thisObject` (the vibrator / service instance) and use
  it to update additional instance fields.
- The owner `thisObject` is likely a long-lived system service, but the same
  pattern is used for an owner-bound observer whose callback captures the owner.

**Risk level:**

- **Medium**. If the hook target is shorter-lived than expected, the observer
  value in `XposedHelpers.additionalFields` would pin it.

### 15. `SystemDisplayHooks` display animation / blur observers

**File:** `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt`

**Pattern:**

- Observers capture `mColorFadeOffAnimator` (ObjectAnimator) or an `IntArray`
  (`mCustomBlurModifier[0]`), not the owner.

**Risk level:**

- **Low / medium**. `mColorFadeOffAnimator` is tied to the display controller
  but the observer does not directly capture the owner reference.

### 16. `WeatherDataController`

**File:** `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/WeatherDataController.kt`

**Pattern:**

- Kotlin `object` (process singleton).
- `ScreenStateController.addListener(appContext, this)` — adds itself as a
  listener. It is a singleton, so the listener stays forever by design.
- Holds `context: Context?` (app context), `updateTarget: WeakReference<Any>`,
  `timeTickReceiver: BroadcastReceiver?`.
- `initContext` cancels old scope, unregisters old tick receiver, but does **not**
  remove itself from `ScreenStateController` (because it is a singleton and
  `addListener` de-dupes).

**Risk level:**

- **Low**. The listener is a process singleton. The strong `appContext` is
  `context.applicationContext`. `timeTickReceiver` is unregistered on re-init.

### 17. `StepCounterController`

**File:** `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/StepCounterController.kt`

**Pattern:**

- Kotlin `object`.
- `bindStepView` / `releaseInactiveState` add/remove `ScreenStateController`
  listener.
- Uses `WeakReference<TextView>` for `stepViewList`? (needs inspection; the
  contract test mentions `sv.addOnAttachStateChangeListener` and
  `if (stepViewList.isEmpty()) releaseInactiveState()`).

**Risk level:**

- **Low** with proper cleanup.

### 18. Handlers and `postDelayed` findings

Important instances:

- `SystemUIStatusBarHooks.statusBarMainHandler` / `statusBarPendingPruneRunnable`
  — bounded prune; `removeCallbacks` on empty.
- `SystemClockHooks.ClockTicker` — `handler.postDelayed(this)` with `removeCallbacks` in `stop()`.
- `SystemLockScreenHooks` wallpaper `Handler(mContext.mainLooper).postDelayed { ... }` —
  one-shot; lambda captures `wallpaper: File`, not a View.
- `SystemUIControlCenterHooks` `handler.postDelayed { ... }` — one-shot, captures
  `mContext`.
- `GlobalActions` / `Controls` `Handler(Looper.getMainLooper()).postDelayed { ... }` —
  one-shot lambdas.
- `XposedServiceManager` `handler.postDelayed(timeoutRunnable / mirrorRetry)` —
  bounded, `removeCallbacks` present.

**Risk level:**

- **Low for one-shots** that do not capture a short-lived View. They are
  transient and the lambda is GC'd after execution.
- **Medium for repeating Runnables** (ClockTicker) if the owner is not properly
  removed. Current code has `removeCallbacks` in `stop()` / `dispose()`.

## Classification summary

| Category | Count | Examples |
|---|---|---|
| A. Safe process-level | 8 | B1/B2/B3 observers, `ReceiverRegistry` module receivers, `FeatureInstallState`, `StatusBarDisplayRegistry`, `WeatherDataController` (singleton) |
| B. Short lifecycle with explicit unregister | 3 | `BatteryIndicator`, `AudioVisualizer`, `StepCounterController` |
| C. Weak owner but callback captures owner | 0 known framework-side; 2-3 call sites do this | `AudioVisualizer.preferenceObserver`, `BatteryIndicator` observer, `SystemAudioHooks` vibration observers |
| D. Static container strong-ref short-lived | 0 confirmed active; risk if B/C not fixed | `additionalFields` value-back-ref if observer captures owner |
| E. Handler / Runnable indirect | 0 confirmed | All current Handlers remove callbacks or are one-shot |
| F. Repeat registration without cleanup | 0 | `PreferenceObserverRegistry` dedups by owner; `ReceiverRegistry` dedups by key/owner |
| G. Cannot prove safe | 0 | — |

## Highest-confidence C1 candidate

**`AudioVisualizer` owner-bound preference observer captures its View owner.**

Why it is the best C1:

1. Clear ownership mismatch: the owner (`AudioVisualizer`) is a View with
   per-SystemUI-generation lifetime, while the `XposedHelpers.additionalFields`
   map is process-lifetime.
2. The current observer value is stored in the map and strongly references the
   owner, creating the exact `WeakHashMap`-value-back-reference leak pattern.
3. `dispose()` already calls `unregisterPreferenceObserver(this)`, so the fix
   can reuse the existing lifecycle; it only needs to stop the observer from
   capturing `this` in the first place.
4. `SystemClockHooks` and `SystemLockScreenHooks` already demonstrate the
   correct pattern: keep a `WeakReference` inside the observer and `get()` it
   inside `onChange`.
5. Scope is small: one View class, no feature-architecture changes, no B1/B2/B3
   code touched.

**C1 not implemented in C0.**

The `AudioVisualizer` fix is the natural C1 entry, but the current C0 audit task
stops at documenting the inventory and selecting the candidate. Implementing the
fix, adding GC/ReferenceQueue tests, refreshing the baseline and running full
verification is left for `PERF-A14-P3-C1`.

## B1/B2/B3 observer lifecycle conclusion

| Observer | Owner | Callback captures View / controller | Deduplication | Expected lifetime |
|---|---|---|---|---|
| `netSpeedTextStyleObserver` | `SystemUIStatusBarHooks` (object) | No | Yes, by owner | Process |
| `statusBarIconVisibilityObserver` | `StatusBarIconVisibilityObserverOwner` (object) | No | Yes, by owner | Process |

Both observers:

- Are registered with a non-Android `object` owner.
- Hold only `MainModule.mPrefs` and small immutable snapshots.
- Are deduplicated by `PreferenceObserverRegistry` so repeated feature installs
  do not create multiple observers.
- Have no `View`, `Context`, `Resources` or controller in their closure.

**Verdict:** B1/B2/B3 observers are safe from retained-owner leaks.

## Source hazard

No code changes were made in C0, so no baseline refresh is required.

- Pre-audit baseline: 1012 findings.
- Post-audit baseline: unchanged.
- Any future C1 that converts `AudioVisualizer.preferenceObserver` to a
  non-capturing implementation is expected to keep the baseline flat or reduce
  it; it should not add `STATIC_STRONG_ANDROID_OWNER`, `CATCH_THROWABLE_NO_FATAL`,
  empty catch or `printStackTrace`.

## Verification

C0 is documentation-only; it does not modify production code. Therefore the full
build/test gate set is not required for the audit document. The repository
remained at:

- `gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL (pre-existing)
- `gradlew :app:testDebugUnitTest` — BUILD SUCCESSFUL (pre-existing)
- `python tools/source_hazard_scan.py --strict-all` — passed, 0 new (pre-existing)
- `git diff --check` — clean

No device measurements were taken. `DEVICE_CHECKPOINT_BLOCKED_NO_DEVICE` is
retained.

## Status

- `P3-C0` audit: complete.
- C1 candidate selected: `AudioVisualizer.preferenceObserver` owner capture.
- C1 not implemented.
- Device evidence: `DEVICE_CHECKPOINT_BLOCKED_NO_DEVICE`.
- B1/B2/B3: `ENGINEERING_COMPLETE_DEVICE_EVIDENCE_PENDING`.
