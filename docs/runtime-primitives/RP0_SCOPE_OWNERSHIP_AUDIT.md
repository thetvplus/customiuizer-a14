# RP0 — A14 Runtime Primitives Scope / Duplication / Ownership Audit

## Base / START GATE

| Item | Value |
|------|-------|
| Source freeze | `e926ce9591b4de42867f0f37c1c4d2a4999c2a7a` |
| Current audited HEAD | `e316827e7a2ac097d5269490017cf5afa1eb9877` |
| Target branch | `devin/a14-runtime-primitives-r14.20.0` |
| Branch created from | exact SHA (not from a branch name) |
| Reset / rebase / amend / cherry-pick / merge / force-push | none |
| Local HEAD at START GATE | `e316827e7a2ac097d5269490017cf5afa1eb9877` |
| Remote HEAD at START GATE | `e316827e7a2ac097d5269490017cf5afa1eb9877` |
| Merge-base with source freeze | `e926ce9591b4de42867f0f37c1c4d2a4999c2a7a` |
| Worktree at START GATE | clean |

## Architecture C termination boundary

Architecture C documentation and implementation are frozen at the source freeze. This RP0 audit does not modify, reopen, or extend any Architecture C document, contract, or production target. The only permitted file is `docs/runtime-primitives/RP0_SCOPE_OWNERSHIP_AUDIT.md`.

```text
ARCHITECTURE_C_REOPEN = NO
```

## Runtime Primitives goals

The RP0 gate is purely analytical. Its goal is to determine, from production control flow, whether a shared runtime primitive for owner-keyed registration/ownership (tentatively referred to as `OwnedSlot` in prior discussions) is justified by at least two production sites that agree on all of the following:

- ownership identity and keying,
- stale-owner behavior and detection,
- replace behavior for same-owner re-registration,
- release behavior and idempotency,
- double-release and same-owner reclaim behavior,
- thread model and publication safety,
- registration boundary (framework, lifecycle, or work token),
- retention policy (weak vs strong, application context vs view/context/controller),
- failure / fatal semantics.

## Non-goals

- Implement a runtime primitive.
- Freeze a name, API, or contract for `OwnedSlot`.
- Modify production code, tests, or Architecture C documentation.
- Start RP1.
- Force the roadmap to continue if no shared primitive is justified.

## Authorization state

```text
RP1_AUTHORIZATION = NO
RP1_STARTED = false
PRODUCTION_STARTED = false
ARCHITECTURE_C_REOPEN = NO
```

## Production ownership inventory

The audit inspected the following control-flow sites. Classifications are derived from executable semantics, not keyword counts.

| # | Site | File | Classification |
|---|------|------|----------------|
| 1 | `StatusBarDisplayRegistry` + `OwnedRegistrations` | `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/StatusBarDisplayRegistry.kt` | `OWNER_GENERATION` + `REATTACHABLE_VIEW` |
| 2 | `ReceiverRegistry` owned receiver path | `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ReceiverRegistry.kt` | `MULTI_OWNER_REGISTRATION` |
| 3 | `ReceiverRegistry` process-key replacement path | `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/ReceiverRegistry.kt` | `PROCESS_KEY_REPLACEMENT` |
| 4 | `DeviceInfoMonitor` / `DeviceInfoMonitorState` | `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/DeviceInfoMonitor.kt`, `DeviceInfoMonitorState.kt` | `WORK_GENERATION` |
| 5 | `AudioVisualizer` lifecycle / generation | `app/src/main/java/tv/withaibuild/customiuizer/utils/AudioVisualizer.kt` | `WORK_GENERATION` + `SIMPLE_WEAK_OWNER` |
| 6 | `SystemClockHooks.SecondTicker` | `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemClockHooks.kt` | `SIMPLE_WEAK_OWNER` |
| 7 | `CustomTextIconTintRoute` + `DarkTintRegistrationState` | `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/CustomTextIconTintRoute.kt`, `DarkTintRegistrationState.kt` | `REATTACHABLE_VIEW` |
| 8 | `LockScreenAlbumArtController` request generation | `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/LockScreenAlbumArtController.kt` | `WORK_GENERATION` |
| 9 | `SystemUIMonitorAndTileHooks` ContentObserver replacement | `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIMonitorAndTileHooks.kt` | `PROCESS_KEY_REPLACEMENT` |
| 10 | `WeatherDataController` | `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/WeatherDataController.kt` | `SIMPLE_WEAK_OWNER` |

Additional `ReceiverRegistry` consumers (not re-audited in detail here) include `BatteryIndicator.kt` (`RECEIVER_KEY`), `Launcher.kt` / `LauncherFolderHooks.kt` (`secretCodeReceiver`, `fetchAppConfigReceiver`), `SystemStatusBarIconHooks.kt` (`alarmTimeReceiver`), `SystemClockHooks.kt` (`clockTimeSetReceiver`), and `SystemUILockScreenHooks.kt` (`keyguardTorchObserver`). These are already served by the existing `ReceiverRegistry` owned or process-key paths.

## Site-by-site evidence

### 1. `StatusBarDisplayRegistry` + `OwnedRegistrations`

`StatusBarDisplayRegistry` has two containers:

- `byDisplay: MutableMap<Int, StatusBarDisplayState<O, R>>` — strong map keyed by `displayId`. Each `displayId` holds **one** current owner generation at a time.
- `pendingByOwner: WeakIdentityMap<O, StatusBarDisplayState<O, R>>` — weak-key identity map. Each pending owner is its own identity key; multiple pending owners may coexist, but each is under a distinct owner identity.

`StatusBarDisplayState` holds a `WeakReference<O>` called `generation` (the status-bar view), an optional `WeakReference<R>` for the second-row container, and an `OwnedRegistrations<O>` list for per-generation cleanup.

- `getOrCreatePending(owner)` (`StatusBarDisplayRegistry.kt:59-70`) refreshes the `WeakReference` for the same owner instance and returns the existing state. A new, distinct owner gets a new pending state.
- `bind(owner, displayId)` (`StatusBarDisplayRegistry.kt:77-100`) migrates pending state. If the display already has a state owned by the **same** owner instance, that state is kept (`StatusBarDisplayRegistry.kt:84-87`). If the display has a state owned by a different (dead) generation, `existing.registrations.cleanupAll()` is called and the display bucket is replaced (`StatusBarDisplayRegistry.kt:88-91`).
- `detach(owner)` (`StatusBarDisplayRegistry.kt:114-133`) removes the exact owner instance from pending and from every display it owns, then releases each state.
- `prune()` (`StatusBarDisplayRegistry.kt:163-178`) removes display states whose generation is gone and whose registration list is empty, after calling `cleanupAll()`, and expunges cleared pending keys.

`OwnedRegistrations` is a list of `(owner, cleanup)` entries, each with a `WeakReference` to the owner and a `consumed` atomic flag. It supports `register`, `cleanupWhere`, `cleanupAll`, and an idempotent `RegistrationHandle.cleanupNow()`.

The cleanup runner (`OwnedRegistrations.kt:126-137`) catches `Throwable`, calls `FatalErrors.unwrapAndRethrowIfFatal(t)`, then logs the remainder. `FatalErrors.unwrapAndRethrowIfFatal` rethrows `OutOfMemoryError`, `ThreadDeath`, `VirtualMachineError`, and unwrapped fatal causes from `InvocationTargetException` / `ExecutionException` / `XposedHelpers.InvocationTargetError` (`FatalErrors.kt:22-52`). Non-fatal throwables are logged and the loop continues.

**Corrected classification:** `OWNER_GENERATION` + `REATTACHABLE_VIEW`. The `generation` field is the owner identity for a **single** display slot. Multiple displays may coexist, but each is under a different `displayId`. Multiple pending owners may coexist, but each is under a different identity key in `WeakIdentityMap`. A new different owner for the same display replaces the old generation. A same owner re-attaching reuses its existing state. This is not `MULTI_OWNER_REGISTRATION` under one display key.

### 2. `ReceiverRegistry` owned receiver path

`ReceiverRegistry.registerOwnedReceiver` creates a `WeakOwnerReceiver` and an `OwnedReceiver(ownerRef, contextRef, receiver)`, then atomically updates `ownedReceivers: ConcurrentHashMap<String, CopyOnWriteArrayList<OwnedReceiver>>` (`ReceiverRegistry.kt:396-469`).

For a given key it:

- keeps live registrations belonging to a *different* owner,
- removes stale owners (GC-ed) and the previous registration for the *same* owner,
- unregisters the displaced receivers outside the compute block,
- self-unregisters if it loses a race.

`WeakOwnerReceiver.onReceive` (`ReceiverRegistry.kt:316-347`) checks `ownerRef.get()` and, if the owner is gone, unregisters itself.

**Classification:** `MULTI_OWNER_REGISTRATION`. Several owners may legitimately coexist under the same key. Same-owner replacement is supported. This is already an existing, tested primitive.

### 3. `ReceiverRegistry` process-key replacement path

`ReceiverRegistry.registerModuleReceiver` uses `moduleReceivers: ConcurrentHashMap<String, ModuleReceiverRegistration>` with a monotonic `moduleReceiverGeneration` (`ReceiverRegistry.kt:70-158`). For a single process key it atomically installs a new registration, unregisters the previous receiver, self-unregisters on concurrent replacement, and queues failed unregisters in a bounded stale queue.

The registration and cleanup paths have different fatal semantics:

- **Registration** (`ReceiverRegistry.kt:127-157`): `OutOfMemoryError` is explicitly rethrown (`catch (oom: OutOfMemoryError) { throw oom }`). Any other `Throwable` is logged via `XposedHelpers.log`, the map entry is rolled back, and `registerModuleReceiver` returns `false`.
- **Unregistration cleanup** (`ReceiverRegistry.kt:189-196`): `releaseModuleRegistration` wraps `context.unregisterReceiver(receiver)` in `try/catch (Throwable)`, returns `false` on any failure, and **does not** rethrow `OutOfMemoryError`, `ThreadDeath`, or `VirtualMachineError`.
- **Process-key cleanup** (`ReceiverRegistry.kt:555-562`): `runModuleCleanup` wraps `reg.cleanup.run()` in `try/catch (Throwable)`, returns `false` on any failure, and **does not** rethrow fatal errors.

`ReceiverRegistry.replaceModuleRegistration` is the non-receiver form for content observers and listeners (`ReceiverRegistry.kt:525-553`). It records a cleanup under a process key and runs the previous cleanup.

**Classification:** `PROCESS_KEY_REPLACEMENT`. The generation is a monotonic token used for self-race detection, not an owner identity. The process-key cleanup paths currently swallow `Throwable` without fatal propagation. In addition, cleanup is not an exact-once operation: `replaceModuleRegistration` installs the new `ModuleRegistration`, calls `runModuleCleanup(previous)`, and, if the cleanup throws, places the previous registration in `staleModuleRegistrations` for a later retry. `retryStaleModuleRegistrations` may invoke `runModuleCleanup` again, and bounded-queue eviction may also attempt cleanup. Therefore `PROCESS_KEY_CLEANUP_EXACT_ONCE` is `false`; successful cleanup normally runs once, but failed cleanup can be retried, and double-cleanup safety depends on the idempotency of the caller-supplied `Runnable`. Main consumers: `SystemUIMonitorAndTileHooks` (5G / floating-time tile observers), `SystemUILockScreenHooks` (torch observer), and `Various.kt` (next-alarm observer).

### 4. `DeviceInfoMonitor` / `DeviceInfoMonitorState`

`DeviceInfoMonitor` hooks the `NetworkSpeedController` constructor. For each controller it creates `activeMainHandler` and `activeBgHandler` that capture a `myId` from `DeviceInfoMonitorState.startNewGeneration()` (`DeviceInfoMonitor.kt:435-448` and `DeviceInfoMonitorState.kt:45-52`).

`DeviceInfoMonitorState` stores `activeBgHandlerId` and `activeMainHandlerId` and provides `isActiveBg`, `isActiveMain`. Each `IconUpdate` carries its `generation` (`DeviceInfoMonitor.kt:95-99`). Handlers drop messages whose id does not match the current generation (`DeviceInfoMonitor.kt:453-456` and `DeviceInfoMonitorState.kt:57-69`).

**Classification:** `WORK_GENERATION`. The `generation` is a handler/work token, not an owner identity. There is one `DeviceInfoMonitor` singleton; the generation is primarily used to ignore in-flight messages from a previous handler pair.

### 5. `AudioVisualizer` lifecycle / generation

`AudioVisualizer` is a `View` that owns a `Visualizer`, a `viewScope`, and a `visualizerGeneration` counter (`AudioVisualizer.kt:65`).

- `checkStateChanged()` increments `visualizerGeneration` and launches `linkVisualizer(generation)` (`AudioVisualizer.kt:817-820`).
- `linkVisualizer` checks the generation under a mutex and drops the candidate if `generation != visualizerGeneration` (`AudioVisualizer.kt:669-684`).
- `onDetachedFromWindow()` calls `dispose()` (`AudioVisualizer.kt:557-589`).
- The preference observer holds a `WeakReference<AudioVisualizer>` (`AudioVisualizer.kt:264-270`).

**Classification:** `WORK_GENERATION` + `SIMPLE_WEAK_OWNER`. The `visualizerGeneration` is a per-start work token. The View itself is the owner and uses `View` lifecycle for disposal.

### 6. `SystemClockHooks.SecondTicker`

`SecondTicker` (`SystemClockHooks.kt:748-844`) is created per `MiuiStatusBarClockController` instance. It holds a `WeakReference<Any>` to the controller (`SystemClockHooks.kt:755`), a `running` flag, a `screenStateRegistered` flag, and a `Handler`.

`initSecondTicker` disposes the previous ticker and stores a new one as an additional instance field on the controller if the effective seconds flags changed (`SystemClockHooks.kt:712-733`). It also uses `ModuleHelper.registerOwnedReceiver` for `TIME_SET`, with the controller as owner (`SystemClockHooks.kt:912-920`).

**Classification:** `SIMPLE_WEAK_OWNER`. The ticker is an owner-bound observer with a `WeakReference` to the controller. Same-owner replacement is explicit. It does not re-attach and is not a keyed framework registration.

### 7. `CustomTextIconTintRoute` + `DarkTintRegistrationState`

`CustomTextIconTintRoute` registers module-created text icons (type 91/92) as dark-receiver owners. It is a per-View registry with a three-phase lifecycle:

- `attach/register` — add the View as a dark receiver,
- `detach/unregister` — remove the receiver, but keep the `OnAttachStateChangeListener` so re-attach can re-register,
- `terminal dispose` — remove the listener and tracking.

`DarkTintRegistrationState` (`DarkTintRegistrationState.kt:17-118`) enforces the state machine with `registered`, `released`, `disposed` booleans, `canRegister()`, idempotent `register()`, `release()`, and `dispose()`.

`CustomTextIconTintRoute.register` finds any existing registration for the same view and, if it is not active, disposes it before creating a new one (`CustomTextIconTintRoute.kt:55-73`).

**Classification:** `REATTACHABLE_VIEW`. Single-owner, view-attached registration with a re-attach path. No second production site shares the same `attach/release/dispose` + re-attach semantics.

### 8. `LockScreenAlbumArtController` request generation

`LockScreenAlbumArtController` uses `requestGeneration: AtomicLong` (`LockScreenAlbumArtController.kt:95`). Each `generate` call increments the generation, cancels the previous coroutine job, and starts a new one (`LockScreenAlbumArtController.kt:262-285`). The worker checks `isCurrent(generation)` between expensive stages and before publishing (`LockScreenAlbumArtController.kt:288-289`).

**Classification:** `WORK_GENERATION`. The generation identifies the latest work request. There is no owner object or key.

### 9. `SystemUIMonitorAndTileHooks` ContentObserver replacement

The 5G and floating-time tiles create `ContentObserver`s inside `handleSetListening`. On `mListening == true` they register the observer and call `ModuleHelper.replaceModuleRegistration("custom_5G_tile_listener") { resolver.unregisterContentObserver(contentObserver) }` (`SystemUIMonitorAndTileHooks.kt:117` and `:140`). On `mListening == false` they call `replaceModuleRegistration` with an empty cleanup, which runs the previous cleanup.

**Classification:** `PROCESS_KEY_REPLACEMENT`. Single process-scoped slot per key; cleanup replaces the previous cleanup.

### 10. `WeatherDataController`

`WeatherDataController` is an `object` that:

- keeps a `WeakReference<Any>` to the current clock controller (`WeatherDataController.kt:142`),
- holds `Context` only as `context.applicationContext`,
- registers one `TIME_TICK` receiver while the screen is on,
- is a `ScreenStateController.ScreenStateListener` (`WeatherDataController.kt:40`).

`initContext` cancels the old coroutine scope, unregisters the old tick receiver, and re-registers with the new context (`WeatherDataController.kt:132-164`).

**Classification:** `SIMPLE_WEAK_OWNER`. The controller is the owner, but the object is a process singleton with no replace/re-attach semantics.

## Candidate comparison

| Property | 1 StatusBarDisplay | 2 OwnedReceivers | 3 ModuleReceiver/Reg | 4 DeviceInfoMonitor | 5 AudioVisualizer | 6 SecondTicker | 7 DarkTint | 8 AlbumArt | 9 TileObserver | 10 Weather |
|---|---|---|---|---|---|---|---|---|---|---|
| Owner identity is the key | yes (per display / per pending identity) | yes (per owner) | no (process key) | no (work token) | yes (View) | yes (controller) | yes (View) | no | no | yes (controller) |
| Multiple owners may coexist under the same key | **no** (one generation per `displayId`; pending owners are separate identity keys) | yes (per key list) | no | — | no | no | no | no | no | no |
| Same-owner replacement / reuse | yes (same owner reuses same display state; pending identity key refreshes weak ref) | yes (same owner/key replaces previous receiver) | yes | no | no | yes | yes (re-register disposes old) | no (work token) | yes (cleanup) | no (singleton) |
| Re-attach without full dispose | yes (same owner re-attaches to same display; pending same-owner) | no | no | no | no | no | yes (view attach/detach) | no | no | no |
| Release is exact-once | yes (OwnedRegistrations) | yes (active flag) | no (state tracks install; failed cleanup retried; bounded queue may evict/retry) | — | yes (dispose) | yes (dispose) | yes (state machine) | yes (cancel job) | yes (cleanup) | yes (unregister) |
| Double-release is harmless | yes | yes | caller-dependent / not guaranteed | — | yes | yes | yes | yes | yes | yes |
| Failure/fatal semantics | `OwnedRegistrations` logs non-fatal, rethrows OOM/VME/ThreadDeath via `FatalErrors` | `WeakOwnerReceiver` logs; bounded stale queue | registration: OOM rethrown; cleanup / runModuleCleanup: catch Throwable, no fatal propagation | `CallbackGuard.guarded` / `FatalErrors` | `XposedHelpers.log` / `FatalErrors` | `CallbackGuard.guarded` | `FatalErrors` | `XposedHelpers.log`; OOM rethrown | `CallbackGuard.guarded` | `XposedHelpers.log`; OOM rethrown |
| Thread model | main thread only | concurrent (CHM/COW) | concurrent (CHM/AtomicReference) | main/bg handlers | main + IO coroutines | main handler | main thread (View callbacks) | Default dispatcher (limited parallelism 1) | main thread | main/IO coroutines |
| Existing primitive already covers | `WeakIdentityMap` + `OwnedRegistrations` + `StatusBarDisplayRegistry` | `ReceiverRegistry` owned path | `ReceiverRegistry` process-key path | `DeviceInfoMonitorState` | View scope + `Visualizer` | `SecondTicker` + `ScreenStateController` | `CustomTextIconTintRoute` + `DarkTintRegistrationState` | single coroutine + atomic generation | `ReceiverRegistry.replaceModuleRegistration` | `WeakReference` + `ScreenStateController` |

## Negative evidence / abstraction risks

The corrected comparison reinforces the negative evidence:

1. **No two sites share the full set of required properties.** `StatusBarDisplayRegistry` and `ReceiverRegistry.ownedReceivers` are both owner-identity structures, but one is one-generation-per-display plus a weak-identity pending map; the other is a multi-owner list per key. They do not agree on "multiple owners under the same key."
2. **Several sites represent work, not owner identity.** `DeviceInfoMonitorState`, `AudioVisualizer.visualizerGeneration`, and `LockScreenAlbumArtController.requestGeneration` use monotonic tokens to discard stale work. Conflating these with owner-keyed slots would be a category error.
3. **Plain `WeakReference` + explicit lifecycle is already simpler in several cases.** `SecondTicker`, `WeatherDataController`, and `AudioVisualizer` only need a weak owner reference and an explicit `dispose`. A generalized slot would add indirection.
4. **Existing registries already solve the problem.** `ReceiverRegistry` covers multi-owner weak receivers and process-key replacement. `StatusBarDisplayRegistry` + `OwnedRegistrations` + `WeakIdentityMap` covers per-display / pending owner state. `CustomTextIconTintRoute` + `DarkTintRegistrationState` covers the attach/detach/terminal-dispose view lifecycle. `PreferenceObserverRegistry` covers owner-bound observers. A new shared primitive would duplicate these tested abstractions.
5. **Corrected StatusBarDisplay semantics removes the closest structural similarity to a generic multi-owner slot.** Because `byDisplay` is one owner generation per `displayId`, the registry is better described as an owner-generation / re-attachable-view pattern than as multi-owner registration. It does not justify a separate `OwnedSlot` primitive.

## OwnedSlot justification or rejection

After re-evaluation, no second production site agrees with any first site on ownership, stale-owner behavior, replace behavior, release behavior, double-release behavior, same-owner reclaim behavior, thread model, publication model, registration boundary, retention policy, and failure/fatal semantics. The corrected `ReceiverRegistry` process-key cleanup does not guarantee exact-once or idempotent cleanup (`PROCESS_KEY_CLEANUP_EXACT_ONCE = false`), the cleanup paths swallow `Throwable`, and the structural stale-callback risk in `SystemUIMonitorAndTileHooks` are distinct issues; they do not create a second semantically-compatible `OwnedSlot` use.

```text
OWNEDSLOT_CANDIDATE_JUSTIFIED = false
RP0_RESULT = NO_SHARED_PRIMITIVE_JUSTIFIED
CONTRACT_HYPOTHESIS = NONE
FIRST_PROOF_TARGET = NONE
RP1_AUTHORIZATION = NO
```

## Lifecycle risks

- **Stale-owner risk:** If the owner is GC-ed but the registration/observer is not released, the framework callback may still fire against a dead object. Most audited sites use `WeakReference` to the owner and either self-unregister (`WeakOwnerReceiver`), expunge (`WeakIdentityMap`), or check `isActive` before acting (`SecondTicker`, `DeviceInfoMonitorState`).
- **Stale-callback risk (structural):** `SystemUIMonitorAndTileHooks` is a negative exception. The `ContentObserver` created in `handleSetListening` (`SystemUIMonitorAndTileHooks.kt:110-117`, `:134-140`) closes over the hook `param` and its callback calls `param.getThisObject()`. There is no `WeakReference` owner guard, no `generation`/`isCurrent` guard, and `replaceModuleRegistration` unregisters the old observer when `mListening` becomes `false` but does not invalidate an `onChange` callback already queued on the `Handler`. No runtime evidence of a queued-callback bug was collected.

```text
STALE_CALLBACK_RISK = STRUCTURAL
CALLBACK_RUNTIME_EVIDENCE = NOT_RUNTIME_TESTED_CALLBACK
```
- **Stale-work risk:** Handlers or coroutines may deliver results from a superseded work item. `DeviceInfoMonitorState`, `AudioVisualizer`, and `LockScreenAlbumArtController` use monotonic generation ids to drop stale work.
- **Registration cleanup ownership:** Each site has a clearly defined cleanup owner: `OwnedRegistrations.cleanupAll` for display state, `ReceiverRegistry` unregistration for receivers, `DarkTintRegistrationState.dispose` for dark-tint listeners, `SecondTicker.dispose` for the ticker, `viewScope.cancel` for `AudioVisualizer`, and explicit `unregisterTick` / `unregisterReceiver` for `WeatherDataController`.
- **Handler / Runnable retention:** `SecondTicker` keeps a `Handler` on the main looper. It removes callbacks in `stop()` and removes the `ScreenStateController` listener in `dispose()`. `DeviceInfoMonitor` uses handlers bound to the `NetworkSpeedController` background looper and removes messages when stopping.
- **View / Context / Controller retention:** Strong references to short-lived Android objects are avoided by `WeakReference`, `ApplicationContext` retention only, and `viewScope`/`coroutineScope` cancellation on detach. `ReceiverRegistry` module receivers intentionally retain `ApplicationContext` for safe unregistration.

## Concurrency / publication risks

- `StatusBarDisplayRegistry` is expected on the SystemUI main thread. `WeakIdentityMap` is single-threaded; it does not use concurrent collections.
- `ReceiverRegistry` uses `ConcurrentHashMap`, `CopyOnWriteArrayList`, and `AtomicReference` because it may be called from multiple threads (broadcast registration, hook callbacks).
- `DeviceInfoMonitorState` uses `volatile` handler ids and an `AtomicLong` generator; the `IconUpdate` generation is published via `Handler` messages.
- `AudioVisualizer` uses a `Mutex` for the visualizer hand-off and `volatile` flags.
- `LockScreenAlbumArtController` uses an `AtomicLong` generation and a `limitedParallelism(1)` dispatcher.
- A shared primitive that hides the thread model would be unsafe: a generic slot cannot force main-thread-only vs concurrent semantics on its callers.

## Retention risks

- `StatusBarDisplayRegistry` strongly retains `StatusBarDisplayState` values, but the owner (`generation`) and second-row (`secondRow`) are `WeakReference`s, so a never-bound or detached view can be GC-ed.
- `OwnedRegistrations` holds `WeakReference` to owners; the cleanup lambda may capture framework objects, but the owner itself is not pinned.
- `ReceiverRegistry` `ownedReceivers` list holds `WeakReference<Any>` to owners; `moduleReceivers` holds `ApplicationContext` strongly for unregistration.
- `WeatherDataController` holds `ApplicationContext` strongly (needed for `ContentProvider` queries) and `WeakReference<Any>` to the controller.
- A new generic primitive would need to replicate this per-site retention choice; a one-size-fits-all policy would either leak or crash.

## Hot-path implications

- `StatusBarDisplayRegistry` operations run on the main looper; `WeakIdentityMap` avoids allocation beyond the initial bucket creation.
- `OwnedRegistrations` cleanup uses a snapshot list to avoid concurrent modification and `AtomicBoolean` for exact-once. Cleanup is not on the per-frame hot path.
- `ReceiverRegistry` owned path uses `CopyOnWriteArrayList` per key; broadcast delivery checks `ownerRef.get()` inline.
- `SecondTicker` posts a single `Runnable` per second; it does not allocate per tick (reuses the same `Runnable`).
- `DeviceInfoMonitor` ticks on the `NetworkSpeedController` background looper and publishes via `Handler` to the main thread; the generation check is a volatile long comparison.
- `AudioVisualizer` uses `Choreographer` frame callbacks and a `Visualizer` data-capture listener; the generation check is inside a `Mutex`.
- A generic `OwnedSlot` abstraction could introduce allocation, indirection, and extra state objects per owner. The actual hot-path cost of such an abstraction has not been measured. The duplication that would justify it has not been demonstrated.

## Fatal cleanup semantics

The `OwnedRegistrations` cleanup loop catches `Throwable` and calls `FatalErrors.unwrapAndRethrowIfFatal(t)` before logging. The executable semantics are:

- `OutOfMemoryError`, `ThreadDeath`, and any `VirtualMachineError` are rethrown immediately.
- `InvocationTargetException`, `ExecutionException`, and `XposedHelpers.InvocationTargetError` are unwrapped up to depth 4; if a cause is fatal, it is rethrown.
- If no fatal cause is found, the original throwable is returned and logged via `XposedHelpers.log`.

`StatusBarDisplayRegistry.releaseState` and `StatusBarDisplayRegistry.detach` call `state.registrations.cleanupAll()`, so the same fatal propagation applies there: a fatal error during release can propagate out of the registry. This is the intended behavior for a fatal JVM condition.

`CallbackGuard.guarded` rethrows `OutOfMemoryError`, `ThreadDeath`, and `VirtualMachineError` and logs others. This is the boundary used by `View.OnAttachStateChangeListener`, `BroadcastReceiver.onReceive`, `ContentObserver.onChange`, and `Runnable.run` callbacks.

## Evidence classification

```text
OWNERSHIP_DUPLICATION_EVIDENCE = STRUCTURAL
STALE_CALLBACK_RISK = STRUCTURAL
CALLBACK_RUNTIME_EVIDENCE = NOT_RUNTIME_TESTED_CALLBACK
HOT_PATH_COST_EVIDENCE = NOT_PROVEN
PROCESS_KEY_CLEANUP_EXACT_ONCE = false
```

All claims about owner/registration semantics are derived from source structure and the existing unit tests in `app/src/test`. No device or live callback runtime evidence was collected; no runtime callback validation is claimed.

## GITHUB status / workflow state

```text
GITHUB_CI_STATUS = NONE
GITHUB_WORKFLOW_RUNS = NONE
```

`gh run list --repo tomthenpc/customiuizer-a14 --branch devin/a14-runtime-primitives-r14.20.0` returned no entries, and `gh api .../check-runs` returned `{"total_count":0,"check_runs":[]}` for the branch HEAD.

## END GATE

Post-corrective checks performed:

| Check | Expected | Result |
|-------|----------|--------|
| SOURCE_FREEZE_ANCESTOR | `true` | `true` |
| MERGE_BASE | `e926ce9591b4de42867f0f37c1c4d2a4999c2a7a` | `e926ce9591b4de42867f0f37c1c4d2a4999c2a7a` |
| CHANGED_FILE_SCOPE | `docs/runtime-primitives/RP0_SCOPE_OWNERSHIP_AUDIT.md` only | `docs/runtime-primitives/RP0_SCOPE_OWNERSHIP_AUDIT.md` only |
| Worktree | clean | clean |
| PRODUCTION_CHANGED | `false` | `false` |
| TESTS_CHANGED | `false` | `false` |
| ARCHITECTURE_C_DOCS_CHANGED | `false` | `false` |

```text
SOURCE_FREEZE_ANCESTOR = true
MERGE_BASE = e926ce9591b4de42867f0f37c1c4d2a4999c2a7a
CHANGED_FILE_SCOPE = docs/runtime-primitives/RP0_SCOPE_OWNERSHIP_AUDIT.md only
PRODUCTION_CHANGED = false
TESTS_CHANGED = false
ARCHITECTURE_C_DOCS_CHANGED = false
```

The exact local HEAD, remote HEAD, and 40-character final SHA are reported in the post-push RP0 return; they are not frozen inside the committed document.

## Contract state (frozen)

```text
RP0_RESULT = NO_SHARED_PRIMITIVE_JUSTIFIED
CONTRACT_HYPOTHESIS = NONE
FIRST_PROOF_TARGET = NONE
RP1_AUTHORIZATION = NO
RP1_STARTED = false
PRODUCTION_STARTED = false
ARCHITECTURE_C_REOPEN = NO
OWNERSHIP_DUPLICATION_EVIDENCE = STRUCTURAL
STALE_CALLBACK_RISK = STRUCTURAL
CALLBACK_RUNTIME_EVIDENCE = NOT_RUNTIME_TESTED_CALLBACK
HOT_PATH_COST_EVIDENCE = NOT_PROVEN
PROCESS_KEY_CLEANUP_EXACT_ONCE = false
GITHUB_CI_STATUS = NONE
GITHUB_WORKFLOW_RUNS = NONE
```

No production code, tests, Architecture C documentation, or runtime primitive were created or modified. Only this audit document was corrected.
