# PERF-A14-P1-SLICE-B3 — Status-bar icon visibility hot-path snapshot

## Scope

This slice optimizes the three status-bar icon-visibility hide paths:

- `StatusBarIconControllerImpl.setIconVisibility(slot, visible)` → `HideIconsHook.before` → `checkSlot(slotName)`
- `StatusBarMobileView.applyMobileState(mobileIconState)` / `updateState(mobileIconState)` → `HideIconsSignalHook.before`
- `CommandQueue.setIcon(slotName, icon)` → `HideIconsFromSystemManager.before`

Only the `system_statusbaricons_*` preferences directly used by these three
paths are included. No other status-bar feature, layout, icon creation, or
CommandQueue scheduling was changed.

## Device evidence status

`DEVICE_CHECKPOINT_BLOCKED_NO_DEVICE`

No Android / HyperOS device was available during this slice, so no real-device
CPU, PSS, USS, thread or FD measurements are reported. All "修改后" numbers below
are derived from source and unit tests, not from measured runtime data.

## Pre-optimization call chains and per-callback read counts

### 1. `checkSlot(slotName)` → `HideIconsHook.before`

| Step | Detail |
|---|---|
| Hook target | `com.android.systemui.statusbar.phone.StatusBarIconControllerImpl.setIconVisibility(String, boolean)` |
| Callback param | `slotName: String?` at `args[0]`; `visible: Boolean` at `args[1]` |
| Short-circuit | Left-to-right `\|\|`; stops at first matching slot. Worst case walks all 17 slots. Typical ~1–8 reads depending on slot name. |
| Slot source | Fixed framework slot names. No `startsWith`, `contains`, `lowercase` or regex. String equality only. |
| PrefMap reads (worst per single callback) | **17** |
| Actual keys used | `system_statusbaricons_headset`, `sound`, `dnd`, `alarm`, `profile`, `vpn`, `airplane`, `nfc`, `secondspace`, `gps`, `wifi`, `hotspot`, `nosims`, `btbattery`, `ble_unlock`, `bluetoothicn`, `volte` |
| Runtime config change support | Yes — next `setIconVisibility` callback re-reads `mPrefs`. No forced icon refresh. |
| Natural re-invocation | SystemUI calls `setIconVisibility` whenever an icon's visibility is recalculated. |

### 2. `HideIconsSignalHook.before`

| Step | Detail |
|---|---|
| Hook targets | `com.android.systemui.statusbar.StatusBarMobileView.applyMobileState(Object)` and `updateState(Object)` |
| Callback params | `mobileIconState` object; `mState` from `thisObject`; `param.getMember().name` |
| Short-circuit | Exits early if `shouldUpdate` is false. Otherwise 4 independent condition groups. |
| Slot / state source | `subId` and `slotId` from `SubscriptionManager`; `wifiAvailable` boolean from `mobileIconState`. No string slot. No `startsWith`/`contains`/`lowercase`/regex. |
| PrefMap reads (worst per single callback) | **7** |
| Actual keys used | `system_statusbaricons_signal`, `signal_wificonnected`, `sim1`, `sim2`, `sim_nodata`, `roaming`, `volte` |
| Runtime config change support | Yes — next `applyMobileState`/`updateState` callback re-reads `mPrefs`. No forced signal refresh. |
| Natural re-invocation | Called on mobile signal state changes. |

### 3. `HideIconsFromSystemManager.before`

| Step | Detail |
|---|---|
| Hook target | `com.android.systemui.statusbar.CommandQueue.setIcon(String, StatusBarIcon)` |
| Callback param | `slotName: String` at `arg(0)`; `StatusBarIcon` at `arg(1)` |
| Short-circuit | Left-to-right `\|\|`. Worst case 5 equality checks. |
| Slot source | Fixed system-manager slot names: `stealth`, `mute`, `speakerphone`, `call_record`, `wireless_headset`. String equality only. |
| PrefMap reads (worst per single callback) | **5** |
| Actual keys used | `system_statusbaricons_privacy`, `mute`, `speaker`, `record`, `wireless_headset` |
| Runtime config change support | Yes — next `CommandQueue.setIcon` callback re-reads `mPrefs`. No forced refresh. |
| Natural re-invocation | Called when the system manager sets one of its managed icons. |

### Independent path summary

| Path | Worst reads per single callback | Unique keys in path | Expected 100-callback saving |
|---|---|---:|---:|
| `checkSlot()` | 17 | 17 | 1700 |
| `HideIconsSignalHook.before` | 7 | 7 | 700 |
| `HideIconsFromSystemManager.before` | 5 | 5 | 500 |
| **Combined across three independent 100-callback runs** | — | **28** distinct | **2900** |

The three paths are independent. They do **not** each read 29 keys on every
callback. The 29 count is the size of the union of distinct keys (28; `volte`
overlaps between `checkSlot` and `HideIconsSignalHook`).

### Key overlap between paths

| Key | Paths using it |
|---|---|
| `system_statusbaricons_volte` | `checkSlot` and `HideIconsSignalHook` |
| All other 27 keys | Exactly one path each |

## Snapshot: `StatusBarIconVisibilitySnapshot`

`StatusBarIconVisibilitySnapshot` is an immutable `data class` with 28 `Boolean`
fields, one `Long` identity `id`, and no `View`, `Context`, `Resources` or
controller references.

Fields map one-to-one to the `system_statusbaricons_*` keys used by the three
hot paths:

- `hideHeadset`, `hideSound`, `hideDnd`, `hideAlarm`, `hideProfile`, `hideVpn`,
  `hideAirplane`, `hideNfc`, `hideSecondSpace`, `hideGps`, `hideWifi`,
  `hideHotspot`, `hideNoSims`, `hideBtBattery`, `hideBleUnlock`,
  `hideBluetoothIcn`, `hideVolte`
- `hideSignal`, `hideSignalWifiConnected`, `hideSim1`, `hideSim2`,
  `hideSimNoData`, `hideRoaming`
- `hidePrivacy`, `hideMute`, `hideSpeaker`, `hideRecord`, `hideWirelessHeadset`

The `buildStatusBarIconVisibilitySnapshot(prefs)` cold path reads each of the 28
keys once. The snapshot is published atomically through
`currentStatusBarIconVisibilitySnapshot` (`AtomicReference`).

## Hot-path mapping

### `checkSlot(slotName, snapshot)`

Replaced the 17 `mPrefs.getBoolean` chain with:

```kotlin
return when (slotName) {
    "headset" -> snapshot.hideHeadset
    "volume" -> snapshot.hideSound
    "zen" -> snapshot.hideDnd
    "alarm_clock" -> snapshot.hideAlarm
    "managed_profile" -> snapshot.hideProfile
    "vpn" -> snapshot.hideVpn
    "airplane" -> snapshot.hideAirplane
    "nfc" -> snapshot.hideNfc
    "second_space" -> snapshot.hideSecondSpace
    "location" -> snapshot.hideGps
    "wifi" -> snapshot.hideWifi
    "hotspot" -> snapshot.hideHotspot
    "no_sim" -> snapshot.hideNoSims
    "bluetooth_handsfree_battery" -> snapshot.hideBtBattery
    "ble_unlock_mode" -> snapshot.hideBleUnlock
    "bluetooth" -> snapshot.hideBluetoothIcn
    "hd" -> snapshot.hideVolte
    else -> false
}
```

- **0 `mPrefs` reads per callback**.
- No `Map`, `Set`, `List`, `Pair` or regex.
- No `lowercase()` or string modification.
- Unknown / null slot returns `false`, preserving original behavior.
- The original defensive `try/catch (Throwable)` was removed because the
  snapshot-only function cannot throw on null or unknown slots. This reduces
  rather than adds risk.

### `shouldHideSystemManagerIcon(slotName, snapshot)`

```kotlin
return when (slotName) {
    "stealth" -> snapshot.hidePrivacy
    "mute" -> snapshot.hideMute
    "speakerphone" -> snapshot.hideSpeaker
    "call_record" -> snapshot.hideRecord
    "wireless_headset" -> snapshot.hideWirelessHeadset
    else -> false
}
```

- **0 `mPrefs` reads per callback**.
- Same allocation/collection guarantees as `checkSlot`.

### `computeSignalIconHiding(wifiAvailable, subId, dataSubId, slotId, snapshot)`

Pure function returning a small `SignalIconHidingResult(visible, roaming, volte, speechHd)`:

```kotlin
if (snapshot.hideSignal) {
    if (!snapshot.hideSignalWifiConnected || wifiAvailable) {
        return SignalIconHidingResult(visible = false)
    }
}
if ((snapshot.hideSim1 && slotId == 0)
    || (snapshot.hideSim2 && slotId == 1)
    || (snapshot.hideSimNoData && subId != dataSubId)
) {
    return SignalIconHidingResult(visible = false)
}
return SignalIconHidingResult(
    roaming = if (snapshot.hideRoaming) false else null,
    volte = if (snapshot.hideVolte) false else null,
    speechHd = if (snapshot.hideVolte) false else null,
)
```

- **0 `mPrefs` reads per callback**.
- Reflection (`XposedHelpers.getObjectField`/`setObjectField`) remains in the
  outer hook where it is unavoidable; the decision logic is pure.

## Preference observer and owner

A new, dedicated owner token was introduced:

```kotlin
private object StatusBarIconVisibilityObserverOwner
```

- It is **not** `SystemUIStatusBarHooks` (the B1/B2 observer owner).
- It is not a `View`, `Context`, `Resources` or controller.
- It is a process-lifecycle singleton that naturally releases with the process.

The observer:

```kotlin
private val statusBarIconVisibilityObserver = object : ModuleHelper.PreferenceObserver {
    override fun onChange(key: String?) {
        if (key != null && key !in statusBarIconVisibilityRelevantKeys) return
        val built = buildStatusBarIconVisibilitySnapshot(MainModule.mPrefs)
        currentStatusBarIconVisibilitySnapshot.set(built)
    }
}
```

- Listens only to the 28 `statusBarIconVisibilityRelevantKeys`.
- Builds a full new snapshot and atomically publishes it.
- Does **not** refresh any icon state; the next framework callback picks up the
  new values, identical to the pre-optimization behavior.
- Registered from each of `HideIconsHook`, `HideIconsSignalHook` and
  `HideIconsFromSystemManager`. `PreferenceObserverRegistry` deduplicates by
  owner, so at most one B3 observer is active per SystemUI process.

### B1/B2 / B3 owner isolation

| Observer | Owner |
|---|---|
| `netSpeedTextStyleObserver` (B1/B2) | `SystemUIStatusBarHooks` |
| `statusBarIconVisibilityObserver` (B3) | `StatusBarIconVisibilityObserverOwner` |

Because the owners are different, the B3 registration cannot overwrite the
B1/B2 registration, and vice versa. `PreferenceObserverRegistry` keeps both
independently.

### Configuration update behavior

- Relevant key change: `buildStatusBarIconVisibilitySnapshot` is called once,
  producing a complete new snapshot with a new `id`; `currentStatusBarIconVisibilitySnapshot.set(built)`.
- Irrelevant key change: returns early; existing snapshot unchanged.
- Concurrent hot-path callbacks observe either the old snapshot or the new
  snapshot through `AtomicReference.get()`.

## Lifecycle and enablement

The three hooks are installed only when their respective `FeatureSpec`
`evaluateEnabled` returns `true`:

- `HideIconsFeature` controls `HideIconsHook` / `checkSlot`
- `HideIconsSignalFeature` controls `HideIconsSignalHook`
- `HideIconsFromSystemManagerFeature` controls `HideIconsFromSystemManager`

The snapshot and observer are created only when at least one of those hooks is
installed, because each hook calls
`ModuleHelper.observePreferenceChange(statusBarIconVisibilityObserver, StatusBarIconVisibilityObserverOwner)`.
If no hide-icon feature is enabled, no snapshot is built and no observer is
registered.

When a user disables all hide-icon toggles after installation, the hook remains
installed (Xposed hooks are not uninstalled at runtime) but the snapshot becomes
all-false. This keeps the hot path cheap and avoids a complex uninstall state
machine.

## Read counts before and after

| Path | Before (worst per single callback) | After (per single callback) |
|---|---|---:|
| `checkSlot()` | 17 `mPrefs.getBoolean` | 0 |
| `HideIconsSignalHook.before` | 7 `mPrefs.getBoolean` | 0 |
| `HideIconsFromSystemManager.before` | 5 `mPrefs.getBoolean` | 0 |

Cold-path reads allowed:

- Initial snapshot build: 28 `mPrefs.getBoolean` calls (one per unique key).
- On relevant preference change: 28 `mPrefs.getBoolean` calls to rebuild.

## Object allocation

Per hot callback:

- **No `Map`, `Set`, `List`, `Pair` or regex**.
- **No `String` allocation** beyond the slot name supplied by the framework.
- **No per-icon / per-View snapshot**.
- `computeSignalIconHiding` returns a small `SignalIconHidingResult` (4 nullable
  `Boolean` fields). This is a single allocation per mobile-signal callback,
  comparable to or smaller than the removed 7 `mPrefs.getBoolean` calls.
- `checkSlot` and `shouldHideSystemManagerIcon` return a primitive `Boolean`; no
  per-call allocation.
- Snapshot object created only on install or on a relevant preference change.

## Slot mapping

`checkSlot` and `HideIconsFromSystemManager` use `when` over fixed string
constants. Kotlin compiles this to a dispatch on `slotName.hashCode` plus
explicit equality (a switch-like structure), not a per-call `HashMap` lookup.

`HideIconsSignalHook` does not use a string slot. It uses `slotId`/`subId` and
snapshot booleans directly.

All slots are fixed. No dynamic string matching, no `startsWith`, no `contains`,
no `lowercase`, no runtime regex, no per-call `Set`/`Map`.

## Test evidence

`StatusBarIconVisibilityHotPathTest.kt` covers:

- Snapshot build reads all 28 unique keys once.
- `checkSlot` 100 consecutive calls with 0 `PrefMap` reads.
- `shouldHideSystemManagerIcon` 100 consecutive calls with 0 `PrefMap` reads.
- `computeSignalIconHiding` 100 consecutive calls with 0 `PrefMap` reads.
- All three paths together 100 calls each with combined 0 `PrefMap` reads.
- All known fixed slots and unknown / null / empty slots.
- `computeSignalIconHiding` for `hideSignal`, `hideSignalWifiConnected`, `sim1`,
  `sim2`, `simNoData`, `roaming`, `volte` combinations.
- Snapshot default all-false, individual key, and combined key builds.
- Relevant key rebuilds snapshot once; irrelevant key does not.
- Snapshot atomic publication.
- B3 owner is not `SystemUIStatusBarHooks`.
- Registering B3 observer does not overwrite B1/B2 observer.
- B1/B2/B3 observers coexist and respond to their own key sets.
- SystemUI "rebuild" path (reset + build) produces a fresh snapshot.
- All-false default snapshot.

All tests pass via `gradlew :app:testDebugUnitTest`.

## Source hazard

| Metric | Value |
|---|---|
| Pre-B3 baseline | 1013 reviewed findings |
| Post-B3 baseline | 1012 reviewed findings |
| Net change | -1 |
| `--strict-all` after refresh | 0 new findings |

Classification:

- **Removed**: 1 `CATCH_THROWABLE_NO_FATAL` finding from the old `checkSlot`
  defensive `try/catch (Throwable)`. The snapshot-only `checkSlot` no longer
  needs it because `when` on a nullable `String?` safely returns `false` for
  unknown or null slots.
- **Moved / refreshed**: the existing `STATIC_STRONG_ANDROID_OWNER`,
  `CATCH_THROWABLE_NO_FATAL` and `EMPTY_CATCH` findings shifted to new line
  numbers because B3 added the snapshot data class and state fields above them.
- **Added**: no new `Throwable` catch, empty catch, `printStackTrace`,
  unbounded collection, or `View`/`Context` strong reference.

The new dedicated owner token `StatusBarIconVisibilityObserverOwner` is a plain
Kotlin `object`, not an Android `Context` / `View` / `Activity` / `controller`,
so it is not flagged as `STATIC_STRONG_ANDROID_OWNER`.

## Verification performed

- `gradlew.bat --no-daemon :app:compileDebugKotlin` — BUILD SUCCESSFUL
- `gradlew.bat --no-daemon :app:assembleDebug` — BUILD SUCCESSFUL
- `gradlew.bat --no-daemon :app:assembleRelease` — BUILD SUCCESSFUL (R8 + lintVitalRelease passed)
- `gradlew.bat --no-daemon :app:testDebugUnitTest` — BUILD SUCCESSFUL
- `python tools/verify.py full` — passed
- `python tools/audit-feature-semantics.py --validate` — Validation passed
- `python tools/source_hazard_scan.py --write-baseline` — Wrote 1012 findings
- `python tools/source_hazard_scan.py --strict-all` — passed, 0 new findings
- `git diff --check` — clean

## Verification not performed

- Real-device measurements (blocked by `DEVICE_CHECKPOINT_BLOCKED_NO_DEVICE`).

## Status

`ENGINEERING_COMPLETE_DEVICE_EVIDENCE_PENDING`

`DEVICE_CHECKPOINT_BLOCKED_NO_DEVICE`

Engineering implementation is complete and unit tests pass. Real-device
performance evidence is pending a connected device.
