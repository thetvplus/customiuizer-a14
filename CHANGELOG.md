# Changelog

English | [简体中文](CHANGELOG_CN.md)

## r14.20.8 — 2026-08-19

Targeting HyperOS 1 / Android 14 (SDK 34), `arm64-v8a`, libxposed API 101/102.

### Fixes

- Some system-level settings now take effect immediately after being configured for the first time.
- Some global actions no longer need an extra restart after being configured for the first time.
- Corrected the display names of the Hotspot, Do not disturb and Dark mode actions.
- Corrected the display names of the Play/Pause, Next and Previous media actions.
- Improved the runtime availability of global actions in the system service and the system UI.

### Improvements

- Improved runtime synchronization of action settings and host lifecycle handling.

### Artifact Information

- APK: `CustoMIUIzer-A14-r14.20.8.apk`
- versionCode / versionName: `205 / r14.20.8`

---

## r14.20.7 — 2026-08-19

Targeting HyperOS 1 / Android 14 (SDK 34), `arm64-v8a`, libxposed API 101/102.

### Fixes

- Opening Launch App / Launch Shortcut / Launch Activity from an Extended Action page no longer crashes.
- The MultiAction Toggle action works again.
- Lock-screen Extended Action now shows Toggle aligned with its stored value.

### Stability

- Launcher's ten gesture actions now have mapping and execution contract checks.
- MultiAction action, toggle, and array contracts now reject advertised IDs without handlers and label/value mismatches.

### Artifact Information

- APK: `CustoMIUIzer-A14-r14.20.7.apk`
- versionCode / versionName: `204 / r14.20.7`

---

## r14.20.6 — 2026-08-18

Targeting HyperOS 1 / Android 14 (SDK 34), `arm64-v8a`, libxposed API 101/102.

### Fixes

- Launcher gestures and other MultiAction pages now keep the selected action after save and reopen.
- Launcher → Gestures “restart related components” now restarts both the launcher and SystemUI.

### Performance and Stability

- Home-screen gesture hot path reuses preallocated command lists.
- Some GlobalActions reflection methods are cached.
- PrefMap reads in `MethodHook` bodies are frozen at zero and guarded by a scanner ceiling. Preference changes refresh snapshots without reinstalling hooks.

### Artifact Information

- APK: `CustoMIUIzer-A14-r14.20.6.apk`
- versionCode / versionName: `203 / r14.20.6`

---

## r14.20.5 — 2026-08-18

Targeting HyperOS 1 / Android 14 (SDK 34), `arm64-v8a`, libxposed API 101/102.

### Status Bar

- Temperature can use separate CPU and battery sources, with better thermal-zone parsing.
- In dual-row mode, temperature moves left when “show on the right” is off.
- Text shrinks when space is tight; vertical offset stays within the status-bar window.

### Launcher and Recents

- Folder blur stays off while dragging icons inside a folder.
- Disable wallpaper scale now clamps actual zoom calls, including recents and app transitions.
- Launcher and Recents toggles have clearer labels; either one disables transition zoom, and only the launcher toggle also disables unlock zoom and recents dim.
- Recents blur at 0% applies on gesture enter and is independent of window-level blur disable.

### Fixes

- Dynamic Island upward recall is more reliable.
- Device info still refreshes without a network-speed controller slot.

### Artifact Information

- APK: `CustoMIUIzer-A14-r14.20.5.apk`
- versionCode / versionName: `202 / r14.20.5`

---

## r14.20.0 — 2026-08-17

Targeting HyperOS 1 / Android 14 (SDK 34), `arm64-v8a`, and libxposed API 101/102.

### New Features

- Dynamic Island mode for the HyperOS status capsule: the ROM forehead is reshaped in place, aligned to the camera cutout, and can be fine-tuned with a signed vertical offset. Custom status-bar height is no longer a hard clip. Status-bar contents fade with platform alpha while the island is visible, consecutive events do not flash the icons back, and the ROM animation is kept.
- USB default purpose: follow system default, charge only, file transfer (MTP), or photo transfer (PTP).
- Recents cards can hide app names.
- The keyboard dismiss button can be hidden in gesture navigation.
- Folder background blur and window-level blur (volume panel, power menu, and similar surfaces) can be disabled.
- Volume-panel Do Not Disturb / mute shortcuts can be hidden, and mode-button colors can be customized.
- System location and notification permission prompts can be dismissed without granting access.
- Exclusive options: disable Xiaomi updater services with exact restore, clear update state, disable MIUI daemon, trim daemon network components, disable Xiaomi analytics, trim Security Center marketing components, and remove the antivirus entry.

### Settings and Interface

- The home page is regrouped into Mods and Settings; the interface language moved from About to home Settings.
- About is now a dedicated page with author and project information.
- Secondary categories, search, and long text continue to follow feature semantics, with clearer soft-reboot notes.

### Backup and Restore

- Backups use a typed V2 format and can still restore older backups.
- Restore validates structure, sanitizes app selections, rolls back a failed commit, and reconciles language / launcher-icon state.
- Only settings still valid in this version are backed up and restored. Removed features and unrecognized old keys are ignored as compatibility cleanup, not treated as a corrupt backup. The old “disable status capsule” switch migrates to the new presentation mode.

### Fixes

- Volume percentage is placed below the live status-bar bottom and follows custom status-bar height in real time.
- The status-bar digital-signal font-size slider now defaults to the system value.

### Stability and Compatibility

- The module now uses a static Xposed scope and only exposes currently supported Hook targets. After enabling it, confirm that LSPosed scope includes `system`, the launcher, and the other required apps.
- Features install by process and preference, with tighter lifecycle, failure boundaries, and runtime status-bar height synchronization.

### Performance

- Status-bar, battery, clock, icon, and notification hot paths do less repeated resolution and allocation. Disabled features no longer create unrelated Hooks.

### Artifact Information

- APK: `CustoMIUIzer-A14-r14.20.0.apk`
- versionCode / versionName: `198 / r14.20.0`

---

## r14.18.6 — 2026-08-09

Targeting HyperOS 1 / Android 14 (SDK 34), `arm64-v8a`, and libxposed API 101/102.

### Settings and Interaction

- Settings no longer create the complete Preference tree at once. Category pages are generated and loaded on demand, while a build-generated search index preserves global search without initializing unrelated pages.
- Click-animation residue from the parent page is removed when opening a subpage. The Various section is also reorganized into clearer categories and entry points with less duplicated hierarchy.
- A race that could briefly report the module as inactive during rapid app close/restart is fixed. The UI now remains in a waiting state while the LSPosed service connection is still in progress.
- Input-method entry points use ROM-neutral wording, while portrait and landscape bottom-padding options that only affect Gboard remain explicitly labeled.

### Performance and Lifecycle

- Full-screen Launcher gestures, lock-screen charging hints, and Security Center dock callbacks no longer rescan caller stacks on frequent paths. Bounded process-local caller state replaces repeated stack, string, and allocation work.
- AudioVisualizer and battery-indicator observers now have explicit owner, replacement, stale-state, and release paths, preventing duplicate registration, callbacks from obsolete instances, and retention of short-lived Views or controllers.
- Feature setup timing and installation counts are restricted to development builds for cold-start diagnosis; release hot paths do not carry the metrics overhead.
- Overbroad R8 keep rules are narrowed while preserving Xposed entry points, reflection, and resource contracts, reducing unnecessary retained code and APK size.

### Compatibility and Fixes

- Imported app-selection settings now discard packages that are uninstalled, disabled, or no longer resolvable. Invalid apps are neither selected nor counted, so summary counts match the actual selector contents.
- Custom status-bar height now updates WindowInsets and app-window geometry instead of moving icons alone. A full reboot is required after changing the fixed height.
- HyperOS status-capsule controls cover charging, silent mode, and Do Not Disturb. Hide mode is device-verified; match-height mode applies after a full reboot, with corner-radius synchronization reserved for a later update.

### Verification

- Static rules, invariants, Python tests, Android JVM tests, compilation, lint, and official Release artifact checks passed.
- Testing on fuxi / HyperOS 1 confirms hide mode and post-reboot height matching; the remaining known limitation is the unmatched corner radius in match-height mode.

### Artifact Information

- APK: `CustoMIUIzer-A14-r14.18.6.apk`
- versionCode / versionName: `196 / r14.18.6`

---

## r14.18.2 — 2026-08-08

Targeting HyperOS 1 / Android 14 (SDK 34), `arm64-v8a`, and libxposed API 101/102.

### Core Changes

- Improved compatibility and fail-closed behavior for heads-up notification and lockscreen hooks on HyperOS / Android 14.
- Fixed charging-info font size being reset after lockscreen indication updates.
- Simplified several SystemUI / system_server lifecycle hooks by using native after callbacks and removing redundant state handling.
- Strengthened fatal-error propagation during hook installation so fatal failures are not treated as ordinary install failures.

### Verification Status

- Feature-semantics validation, source-hazard scanning, invariant checks, Python tool tests, Android JVM unit tests, lint, and R8 analysis passed.
- The signed Release APK passed version, certificate, v2 signing, zip alignment, SDK, ABI, debuggable, and Xposed entry checks.
- The release candidate was installed and passed device smoke validation.

### Artifact Information

- APK: `CustoMIUIzer-A14-r14.18.2.apk`
- Size: `3468849` bytes
- SHA-256: `77F868590C631271251991EDEBF066919460E2F1DA955EFDC10271207EAF3E77`
- versionCode / versionName: `195 / r14.18.2`

---

## r14.18.1 — 2026-08-07

Targeting HyperOS 1 / Android 14 (SDK 34), `arm64-v8a`, and libxposed API 101/102.

### Core Changes

- Build toolchain upgrade: JDK 25, Gradle 9.6.1, AGP 9.3.1; Gradle Daemon JVM criteria pinned to Java 25.
- Java source/target remain 17; Android Java compiler output remains 17 while Gradle and the compiler toolchain run on 25.
- `.idea` metadata hygiene: moved local/generated IDE state (Gradle IDE model, deployment targets, repository mirrors, migration state, inspection profile) out of Git tracking, preserving shared code styles, compiler target hint, encoding, and VCS mapping.
- Fixed a fail-open in `SystemLockScreenHooks` where a failed `handleIncomingUser` resolution fell back to user 0; the hook now returns the original method result instead of continuing CustoMIUIzer wallpaper post-processing.

### Verification Boundary

- This Release-only build did not run `python tools/verify.py full`, because that mode triggers `assembleDebug`.
- Feature-semantics validation, source-hazard scanning, invariant checks, `git diff --check`, and JVM unit tests were run and passed.
- Release APK is R8-minified, resource-shrunk, and officially signed.
- This release is not claimed as fully `DEVICE_VERIFIED`; the no-reboot status-bar-height sequence awaits device verification.

### Known Major

- `SystemNotificationHooks` notification menu falls back to user 0 when `UserHandle.getUserId` resolution fails, which may open app info or force stop the wrong user.
- `Various.kt` AppInfo app launch falls back to user 0 when `UserHandle.getUserId` resolution fails, which may launch the app for the wrong user.

---

## r14.18.0 — 2026-08-06

Targeting HyperOS 1 / Android 14 (SDK 34), `arm64-v8a`, and libxposed API 101/102.

### Core Changes

- Added adjustable lock-screen charging text size; the default keeps the system text size and changes apply after restarting SystemUI.
- Hardened charging-info initialization and hot paths. Disabled details skip unnecessary work, reducing duplicate installation, invalid reads, and fallback overhead.
- Fixed a possible SystemUI crash when status-bar battery or temperature information is enabled, and hardened stale Handlers, detached Views, ROM field fallbacks, and custom-icon creation.
- Fixed left-side custom status-bar text icons becoming invisible on dark backgrounds by completing tint registration, initial synchronization, recreation, and release lifecycles.
- Added status-bar height synchronization with WindowInsets and the SystemUI window, including runtime application and restoration of the system height when disabled; no-reboot fuxi switching still awaits device verification.
- Hardened status-bar and control-center gesture, View, callback, and ClassLoader lifecycles to reduce duplicate effects, state conflicts, and stale-object retention.
- Optimized process routing, feature install deduplication, and disabled-feature initialization. Ordinary failures remain isolated while fatal errors continue to propagate.
- Added Git revision and APK provenance records, with feature semantics, Python gates, unit tests, and lint integrated into the unified verification flow.

### Verification Status

- `python tools/verify.py full`, feature-semantics validation, source-hazard scanning, CI portability checks, and the full Python suite pass.
- All 405 Python tool tests pass, together with Android JVM unit tests and `lintDebug`.
- The no-reboot `44 → 40 → 12 → 44 → disabled` status-bar-height sequence has not yet been run on fuxi, so this release is not claimed as fully `DEVICE_VERIFIED`.

## r14.16.1 — 2026-08-01

`versionCode 192`, targeting HyperOS 1 / Android 14 (SDK 34), `arm64-v8a`, and libxposed API 101/102.

### Core Changes

- Split SystemUI, Launcher, `system_server`, and regular-app entry points into process-routed installers. Stable feature IDs, explicit install states, and lazy definitions ensure one installation per process, while disabled features skip unrelated registration and business-object creation.
- Fixed concurrent early-preference bootstrap and empty-snapshot semantics. Failed installs no longer leave active definitions behind, and preference updates cannot reset installed Hooks to an uninstalled state.
- Made ReflectionCache bounded and isolated per ClassLoader. ResourceHooks now preserve real Hook results and concurrent installation semantics while reducing boxing, arrays, and name parsing on hit paths.
- Unified owner, replacement, active/stale, and release lifecycles for Receiver and Observer registrations. Stale weather, step counter, album-art, battery-indicator, and percentage-overlay state is released instead of retaining Contexts, Views, or intermediate Bitmaps.
- Ordinary failures remain isolated across shared Hook, Java/Kotlin, and logging boundaries, while `OutOfMemoryError` is always rethrown instead of being disguised as a compatibility failure.
- Reduced temporary objects and repeated work in network-speed sampling and formatting, charging hints, navigation-icon reloads, battery-indicator updates, and pass-through Hook argument paths.
- Preference switches now show the target checked state immediately before the existing persistence, disabled-state, and restart-requirement logic runs, improving feedback for rapid taps without changing final preference semantics.
- Module-load logs now include the version and short Git SHA. API 102 stable Hook ID support remains isolated as `READY_NOT_WIRED` and is not connected to production paths.

### Verification Boundary

- The release commit passes `python tools/verify.py full`, covering runtime invariants, Debug Kotlin/Java compilation, unit tests, and `lintDebug`.
- The formal Release APK uses the A14-specific certificate; version, SHA-256, signature, zip alignment, `debuggable=false`, and Xposed metadata are recorded in the GitHub Release.
- The existing Xiaomi 13 / HyperOS 1 baseline found no module-attributable P0/P1, duplicate-install, or stuck-installing issue. The new runtime and UI changes in this release have not completed per-feature device behavior verification and are not claimed as fully `DEVICE_VERIFIED`.

---
