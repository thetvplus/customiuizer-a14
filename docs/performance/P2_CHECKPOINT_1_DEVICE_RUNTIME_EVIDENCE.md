# PERF-A14-P2-CHECKPOINT-1 — Device runtime baseline and B1/B2 impact verification

## Environment

| Item | Value |
|---|---|
| Host OS | Windows |
| ADB executable | `C:\Android\platform-tools\adb.exe` |
| ADB devices output | `List of devices attached` (empty) |
| Device available | **No** |
| Git branch | `devin/a14-performance-optimization` |
| Local HEAD | `6ac5807bf64775d7240e9d9fc767b0563db378ce` |
| Remote HEAD | `6ac5807bf64775d7240e9d9fc767b0563db378ce` |
| Work tree | clean |

**Status marker**: `DEVICE_CHECKPOINT_BLOCKED_NO_DEVICE`

No Android / HyperOS device was connected to the build host when the checkpoint was
attempted. Because the checkpoint contract requires real-device PSS/USS/CPU evidence,
no runtime measurements could be collected and no APK was installed.

## What was blocked

The following sections of PERF-A14-P2-CHECKPOINT-1 cannot be completed without a device:

- Test APK installation and signature comparison.
- Disabled baseline / User profile / Network-speed stress profile measurements.
- Memory snapshots (`dumpsys meminfo`) for module, SystemUI and `system_server`.
- CPU and allocation profiling under B1/B2.
- B1/B2 functional correctness matrix (single, combined, runtime change, restart).
- Shared observer lifecycle verification at runtime.
- `FIX-A14-LOCKSCREEN-CHARGING-TEXT-SIZE-BOOT-APPLICATION` cold-boot evidence.

No values were fabricated. All blocked sections are documented below with the
planned methodology so they can be executed immediately once a device is available.

## Planned measurement methodology (for execution with device)

### 1. APK selection

Use the `app:assembleRelease` APK built at `6ac5807b`.

- Compute SHA-256 of the release APK.
- Compare signing certificate digest with the currently installed module APK using
  `keytool -printcert -jarfile` or `apksigner verify --print-certs`.
- If the digest matches, install by `adb install -r -d app-release-unsigned.apk` or by
  pushing and invoking package manager.
- If the digest does **not** match, stop and mark `DEVICE_INSTALL_BLOCKED_SIGNATURE_MISMATCH`;
  do not uninstall the user's existing module.

### 2. Test profiles

#### A. Disabled baseline

- Module enabled in LSPosed but all features turned off.
- Keeps the module loaded in SystemUI to measure the base cost of the hook
  loader and any passive infrastructure.

#### B. User profile

- Record the user's real toggles; do not enable experimental or unrelated features.
- Document which `FeatureId`s are active.

#### C. Network-speed stress

- Enable only:
  - `system_netspeed` or equivalent normal status-bar network speed (B0)
  - `system_detailednetspeed_style` / B1 network-speed text style
  - `system_detailednetspeed_*` / B2 detailed network-speed format
  - Keep the default refresh interval; do not force a faster interval.
- Disable all other modules / features.

### 3. Stabilization

For each profile:

- Apply configuration.
- `adb shell am restart` or full device reboot.
- Wait 60 s after `sys.boot_completed` is true.
- Keep screen on, brightness fixed, same network, no user input.
- Record `utc` timestamp, screen state and charging state.
- Repeat 5 times per profile and use the median.

### 4. Metrics to collect

```bash
adb shell dumpsys meminfo <module-pkg>
adb shell dumpsys meminfo com.android.systemui
adb shell dumpsys meminfo system_server
adb shell ps -T -A
adb shell ls /proc/<pid>/fd | wc -l
```

Extract:

- Module PSS / USS / Private Dirty
- SystemUI PSS / USS
- `system_server` PSS
- Java Heap, Native Heap, Graphics, Code, Stack, Ashmem / Other mmap
- Thread count
- FD count

For CPU:

- Collect 10-minute `top -p <systemui-pid> -d 1000` or Perfetto trace.
- Report average and peak CPU.
- If available, use simpleperf / Android Studio profiler for allocation samples.

### 5. B1/B2 functional matrix

Test each row, confirm the network-speed text renders correctly and no SystemUI crash
or repeated restart:

| # | Configuration |
|---|---|
| 1 | B1 on, B2 off |
| 2 | B1 off, B2 on |
| 3 | B1 on, B2 on |
| 4 | B1 off, B2 off |
| 5 | Change B1 style while SystemUI is running |
| 6 | Change B2 detailed format while SystemUI is running |
| 7 | Change B1 first, then B2 |
| 8 | Change B2 first, then B1 |
| 9 | Restart SystemUI only |
| 10 | Full device reboot |

### 6. Charging text size boot application

For `FIX-A14-LOCKSCREEN-CHARGING-TEXT-SIZE-BOOT-APPLICATION`:

- Set a non-default charging text size.
- Cold boot 3 times, capture the rendered size after boot.
- Manual SystemUI restart 3 times.
- Turn feature off, cold boot 1 time.
- Verify:
  - size takes effect without requiring a fixed delay
  - no cumulative scaling
  - no duplicate observer registration
  - no SystemUI error logs (`adb logcat -d | grep -iE "customiuizer|systemui"`).

---

## B3 static audit — Status-bar icon visibility preference snapshot feasibility

Because no device was available, the blocked checkpoint time is used to complete the
B3 static audit defined in PERF-A14-P2-CHECKPOINT-1 section 10. No B3 production code
was implemented.

### Call chain and pre-optimization read counts

#### `checkSlot(slotName: String?)` → `HideIconsHook.before`

```text
StatusBarIconControllerImpl.setIconVisibility(String slotName, boolean visible)
  -> HideIconsHook.before
     -> if (checkSlot(slotName)) param.getArgs()[1] = false
        -> MainModule.mPrefs.getBoolean("system_statusbaricons_headset")    [1]
        -> MainModule.mPrefs.getBoolean("system_statusbaricons_sound")      [2]
        -> MainModule.mPrefs.getBoolean("system_statusbaricons_dnd")        [3]
        -> MainModule.mPrefs.getBoolean("system_statusbaricons_alarm")      [4]
        -> MainModule.mPrefs.getBoolean("system_statusbaricons_profile")    [5]
        -> MainModule.mPrefs.getBoolean("system_statusbaricons_vpn")        [6]
        -> MainModule.mPrefs.getBoolean("system_statusbaricons_airplane")   [7]
        -> MainModule.mPrefs.getBoolean("system_statusbaricons_nfc")        [8]
        -> MainModule.mPrefs.getBoolean("system_statusbaricons_secondspace")[9]
        -> MainModule.mPrefs.getBoolean("system_statusbaricons_gps")        [10]
        -> MainModule.mPrefs.getBoolean("system_statusbaricons_wifi")       [11]
        -> MainModule.mPrefs.getBoolean("system_statusbaricons_hotspot")    [12]
        -> MainModule.mPrefs.getBoolean("system_statusbaricons_nosims")     [13]
        -> MainModule.mPrefs.getBoolean("system_statusbaricons_btbattery")  [14]
        -> MainModule.mPrefs.getBoolean("system_statusbaricons_ble_unlock") [15]
        -> MainModule.mPrefs.getBoolean("system_statusbaricons_bluetoothicn")[16]
        -> MainModule.mPrefs.getBoolean("system_statusbaricons_volte")      [17]
```

**Pre-optimization per call**: up to 17 `mPrefs.getBoolean` reads.

#### `HideIconsSignalHook.before`

```text
StatusBarMobileView.applyMobileState(mobileIconState) / updateState(mobileIconState)
  -> HideIconsSignalHook.before
     -> MainModule.mPrefs.getBoolean("system_statusbaricons_signal")              [1]
     -> MainModule.mPrefs.getBoolean("system_statusbaricons_signal_wificonnected") [2]
     -> MainModule.mPrefs.getBoolean("system_statusbaricons_sim1")                 [3]
     -> MainModule.mPrefs.getBoolean("system_statusbaricons_sim2")                 [4]
     -> MainModule.mPrefs.getBoolean("system_statusbaricons_sim_nodata")           [5]
     -> MainModule.mPrefs.getBoolean("system_statusbaricons_roaming")              [6]
     -> MainModule.mPrefs.getBoolean("system_statusbaricons_volte")                [7]
```

**Pre-optimization per call**: up to 7 `mPrefs.getBoolean` reads.

#### `HideIconsFromSystemManager.before`

```text
CommandQueue.setIcon(String slotName, StatusBarIcon icon)
  -> HideIconsFromSystemManager.before
     -> MainModule.mPrefs.getBoolean("system_statusbaricons_privacy")             [1]
     -> MainModule.mPrefs.getBoolean("system_statusbaricons_mute")                [2]
     -> MainModule.mPrefs.getBoolean("system_statusbaricons_speaker")             [3]
     -> MainModule.mPrefs.getBoolean("system_statusbaricons_record")              [4]
     -> MainModule.mPrefs.getBoolean("system_statusbaricons_wireless_headset")    [5]
```

**Pre-optimization per call**: up to 5 `mPrefs.getBoolean` reads.

### Do the three paths share the same hide-icon configuration?

Not exactly. They are independent features but live under the same
`system_statusbaricons_*` preference namespace:

| Hook | Relevant `system_statusbaricons_*` keys |
|---|---|
| `checkSlot` | headset, sound, dnd, alarm, profile, vpn, airplane, nfc, secondspace, gps, wifi, hotspot, nosims, btbattery, ble_unlock, bluetoothicn, volte |
| `HideIconsSignalHook` | signal, signal_wificonnected, sim1, sim2, sim_nodata, roaming, volte |
| `HideIconsFromSystemManager` | privacy, mute, speaker, record, wireless_headset |

There is a small overlap (`volte`) but each hook checks its own subset.
Because all keys share the same prefix and all three hooks are installed by
`SystemUIStatusBarHooks`, a single `StatusBarIconVisibilitySnapshot` containing all
relevant booleans is feasible and is the recommended design.

### Can the state be expressed as an immutable bitmask / Boolean snapshot?

**Yes.** Two practical options:

1. **Boolean data class** — one `Boolean` field per preference. Easy to read,
   easy to review, no bit-shift errors, and the Kotlin compiler can still generate
   efficient field access.
2. **Integer bitmask** — pack all ~29 booleans into an `Int` (or `Long`) bit field.
   The smallest and fastest at runtime, but mapping slot names to bit positions
   requires either a `when` block or a precomputed `Map<String, Int>`. A `when`
   block is preferred over a per-call `Map` lookup.

Either option eliminates all per-call `mPrefs` reads.

### Slot name mapping

`checkSlot` uses 17 fixed string slot names:

`headset`, `volume`, `zen`, `alarm_clock`, `managed_profile`, `vpn`, `airplane`,
`nfc`, `second_space`, `location`, `wifi`, `hotspot`, `no_sim`,
`bluetooth_handsfree_battery`, `ble_unlock_mode`, `bluetooth`, `hd`.

These are a fixed set; they do not depend on runtime strings or localization.

`HideIconsFromSystemManager` uses 5 fixed string slot names:
`stealth`, `mute`, `speakerphone`, `call_record`, `wireless_headset`.

`HideIconsSignalHook` does not use a string slot at all. It uses the mobile
subscription ID and slot index from `SubscriptionManager`, plus boolean fields in
`MobileIconState`. A snapshot can provide the booleans directly.

### Fixed vs. dynamic slots

- **Fixed**: all string slots in `checkSlot` and `HideIconsFromSystemManager`.
- **Dynamic**: none in B3. `HideIconsSignalHook` derives logic from
  `SubscriptionManager` IDs, but the preference keys are still fixed.

### Will the framework naturally re-invoke the hooks after a preference change?

Yes and no:

- `checkSlot` is inside `setIconVisibility`. SystemUI re-calls `setIconVisibility`
  when an icon's visibility is recalculated (e.g., on `CommandQueue` updates). The
  next icon update will pick up the new snapshot.
- `HideIconsSignalHook` is inside `applyMobileState` / `updateState`, which are
  called when the mobile signal state changes. The next signal update will pick up
  the new snapshot.
- `HideIconsFromSystemManager` is inside `CommandQueue.setIcon`, called when the
  system sets one of the managed icons.

None of the hooks are currently triggered by preference changes alone; they only
run when the framework re-evaluates the affected state. This is the existing
behavior and it should be preserved in B3 to avoid forcing a full status-bar
refresh from a module observer.

### Does B3 need to actively refresh current icon state?

**No, not for the hot path.** The current module does not refresh icons when a
hide-icon preference changes; it relies on the next framework callback. Changing
this would add a cold-path / lifecycle action that is outside the B3 scope.

A preference observer may still need to invalidate the snapshot so the next
framework callback sees fresh data, but it should not call back into SystemUI to
force a refresh.

### Can the observer be a single Feature-level observer?

Yes. A single `PreferenceObserver` owned by `SystemUIStatusBarHooks` can listen to
the union of B3 keys and invalidate `currentStatusBarIconVisibilitySnapshot`.

- The `netSpeedTextStyleObserver` is already used for B1/B2 and should **not** be
  overloaded with B3 keys, because the lifetimes and rebuild costs are unrelated.
- A new `statusBarIconVisibilityObserver` registered with `SystemUIStatusBarHooks`
  is the clean design. `PreferenceObserverRegistry` deduplicates by owner, so
  installing it from each of the three `HideIcons*` hooks is safe if each hook
  calls `ModuleHelper.observePreferenceChange(statusBarIconVisibilityObserver, SystemUIStatusBarHooks)`.

### Can snapshot/observer be skipped when all features are off?

Yes. The `evaluateEnabled` functions already decide whether each hook is installed.
If none of the `HideIcons*` features are enabled, none of the hooks run and there
is no need to build the snapshot or register the observer.

The snapshot should be built lazily on the first hot-path call or on the first
observer callback, and only if at least one B3 feature is active.

### Real call frequency of `checkSlot`

`checkSlot` is invoked inside `StatusBarIconControllerImpl.setIconVisibility`,
which is called by the framework every time any status-bar icon's visibility
needs to be determined. The exact call count is device- and state-dependent, but
the conservative assumption is **once per icon per status-bar update**, making it
comparable to or more frequent than the B1/B2 once-per-second network-speed tick.

`HideIconsSignalHook` is invoked on every `StatusBarMobileView` state change
(signal strength, data connection, Wi-Fi availability). This is typically several
times per second when the device is moving or the network is changing.

`HideIconsFromSystemManager` is invoked when the system manager sets one of its
five managed icons (privacy, mute, speaker, record, wireless headset). Frequency
is low but the hook still performs 5 `mPrefs` reads each call.

### Estimated PrefMap read elimination for 100 consecutive calls

| Hook | Worst-case reads per single callback | Estimated reads per 100 callbacks |
|---|---|---|
| `checkSlot` | 17 | 1700 |
| `HideIconsSignalHook` | 7 | 700 |
| `HideIconsFromSystemManager` | 5 | 500 |
| **Combined across three independent 100-call runs** | **—** | **2900** |

With a shared immutable snapshot, all three hooks become **0 `mPrefs` reads per
hot callback**. The only `mPrefs` reads are the one-time snapshot build (~29
unique keys) and a single rebuild on any B3 preference change.

### Would a snapshot increase String / Set / Map / Pair / hash overhead?

A well-designed snapshot would **not** allocate per call:

- **No `Set`/`HashSet`**: use `when (slotName)` or individual `Boolean` reads.
- **No `Map` in hot path**: a `when` on string constants is compiled to an
  efficient lookup and does not allocate per call.
- **No `Pair`/`Triple`**: the hook returns `Boolean` directly.
- **No `String` beyond the slot name already supplied by the framework**.
- **No unbounded cache**: the snapshot is a single immutable object.

A bitmask snapshot would be even cheaper: one `Int` field and a `when` branch.

### Is `when` / bitmask lower overhead than a `HashSet`?

Yes. `when` over fixed string constants in Kotlin compiles to a hash-based
`StringSwitch` generated once by the compiler; no `HashSet` is allocated at
runtime and no `hashCode` is recomputed per call beyond the intrinsic string
switch. A bitmask adds only an integer `and` / `shl` operation, which is cheaper
still.

A `HashSet<String>` would require constructing and populating the set at
initialization and a hash lookup per call; this is slower and creates a long-lived
object.

### Can install phase and hot path be cleanly separated?

Yes:

- **Install / observer**: determine whether any B3 feature is enabled; if so,
  register the preference observer. The observer only invalidates the snapshot.
- **Cold path / build**: `buildStatusBarIconVisibilitySnapshot(prefs)` reads all
  relevant keys once and returns the immutable snapshot.
- **Hot path**: `checkSlot(slotName, snapshot)`, `HideIconsSignalHook(snapshot)`,
  `HideIconsFromSystemManager(slotName, snapshot)` do zero `mPrefs` reads.

This is the same pattern as B1 (`NetSpeedTextStyleSnapshot`) and B2
(`DetailedNetSpeedFormatSnapshot`).

### Recommended B3 design

- **Snapshot**: `StatusBarIconVisibilitySnapshot` with one `Boolean` per relevant
  key (or an `Int` bitmask). Include `id` and an `isAnyEnabled`-style flag if
  needed.
- **Build function**: `buildStatusBarIconVisibilitySnapshot(prefs)` reads the
  ~29 `system_statusbaricons_*` keys once and pre-computes any derived state.
- **Observer**: a new `statusBarIconVisibilityObserver` registered with a
  dedicated owner token (e.g. `StatusBarIconVisibilityObserverOwner`), not with
  `SystemUIStatusBarHooks`, listening to all B3 keys; on change it builds and
  atomically publishes the new snapshot.
- **Hot-path refactors**:
  - `checkSlot(slotName, snapshot)` -> `when (slotName)` over fixed slots.
  - `HideIconsSignalHook(snapshot)` -> read `snapshot.signal`,
    `snapshot.signalWifiConnected`, `snapshot.sim1`, etc.
  - `HideIconsFromSystemManager(slotName, snapshot)` -> `when (slotName)` over
    `stealth`, `mute`, `speakerphone`, `call_record`, `wireless_headset`.
- **No new Xposed Hook, no thread, no Handler, no Timer, no global Receiver, no
  View/Context/Resources strong reference in the snapshot, no unbounded cache**.

### Estimated savings summary

| Metric | Current (per 100 calls, worst) | After B3 snapshot |
|---|---|---|
| `checkSlot` mPrefs reads | 1700 | 0 |
| `HideIconsSignalHook` mPrefs reads | 700 | 0 |
| `HideIconsFromSystemManager` mPrefs reads | 500 | 0 |
| Hot-path allocations | up to 17 / 7 / 5 `mPrefs.getBoolean` calls per callback, plus the existing `Throwable` catch in `checkSlot` | 0 `mPrefs` reads per callback; only cheap snapshot field reads |

The B3 audit concludes that a single immutable snapshot is feasible and would
eliminate the per-callback `mPrefs.getBoolean` reads in all three hot paths
(17 + 7 + 5 distinct keys, 28–29 unique keys total). Across three independent
100-callback stress runs the combined worst-case saving is 2900 `mPrefs`
reads, with minimal runtime overhead and no new lifecycle complexity.

## Measurement limitations

- No ADB device was present; all runtime claims in this checkpoint are derived
  from source-code analysis, not from measured device data.
- CPU, PSS, USS, thread and FD numbers cannot be reported.
- The B1/B2 performance impact relative to baseline cannot be quantified without
  a controlled A/B installation on a real device.

## Next priority recommendation

Without device evidence, the only responsible recommendation is to first complete
P2-CHECKPOINT-1 with a connected device and collect the disabled / user /
network-speed-stress measurements. Once the data is available, apply the decision
matrix from PERF-A14-P2-CHECKPOINT-1 section 12:

- If SystemUI memory is dominated by static caches / listeners, audit lifecycle.
- If CPU improves and memory is flat, move to static strong reference / observer / receiver cleanup.
- If network speed still consumes significant CPU, consider B3 or module resource optimization.
- If disabled baseline has large overhead, audit feature spec traversal and module boot cost.

B3 (`StatusBarIconVisibilitySnapshot`) is a candidate for the third case
(network-speed hot path reduced but status-bar icon paths still high) or for a
quick follow-up to B2 because its implementation is small and isolated.

## Status

`DEVICE_CHECKPOINT_BLOCKED_NO_DEVICE`

- Git branch / HEAD / work tree verified.
- ADB `devices` returned no attached device.
- No APK installed, no measurements taken, no values fabricated.
- B3 static audit completed.
- Lock-screen charging text size boot verification blocked pending device.
