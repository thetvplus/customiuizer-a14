# PERF-A14-P0 — Runtime Baseline and Disabled-Feature Zero-Cost Audit

**Branch:** `devin/a14-performance-optimization`  
**Baseline branch:** `origin/main`  
**Baseline SHA:** `0751caf21fcc627c9298630ea3ed57189ac0071d`  
**Generated:** 2026-08-06  
**Tools used:** `extract_process_matrix.py`, `audit_hook_ownership.py`, `source_hazard_scan.py`, `check-invariants.py`, `audit-feature-semantics.py`, `grep` static scans.

## 1. Branch and verification status

- New branch created and pushed: `devin/a14-performance-optimization`.
- Local HEAD and remote HEAD both at `0751caf21fcc627c9298630ea3ed57189ac0071d`.
- Working tree clean at branch creation.
- Local gates passed before commit:
  - `python tools/check-invariants.py` — 212 files, no violations.
  - `python tools/audit-feature-semantics.py --validate` — passed.
  - `python tools/audit_hook_ownership.py --check` — 767 hook call sites, classification valid.
  - `python tools/source_hazard_scan.py --scope production` — 1028 reviewed findings, 0 new.

## 2. Feature and process baseline

`tools/extract_process_matrix.py` generated `docs/rom-intelligence/A14_PROCESS_MATRIX.*`.

| Metric | Value |
|---|---|
| Total registered features | 245 |
| By target package | SYSTEM_UI 96, LAUNCHER 56, SYSTEM_SERVER 50, SYSTEM_PACKAGE 35, ANY 8 |
| By install phase | PACKAGE_READY 148, SYSTEM_SERVER_STARTING 50, APPLICATION_ATTACHED 47 |
| By installer | SystemUiFeatures 96, SystemServerFeatures 50, LauncherPostAttachFeatures 43, LauncherPackageReadyFeatures 12, SecurityCenterFeatures 16, others 28 |

Scope list: 13 packages (`android`, `com.android.incallui`, `com.android.settings`, `com.android.systemui`, `com.miui.gallery`, `com.miui.guardprovider`, `com.miui.home`, `com.miui.miwallpaper`, `com.miui.packageinstaller`, `com.miui.powerkeeper`, `com.miui.screenshot`, `com.miui.securitycenter`, `system`).

## 3. Hook inventory

`tools/audit_hook_ownership.py` scanned `app/src/main/java`.

| Category | Hook call sites |
|---|---|
| REGISTRY_FEATURE | 731 |
| INSTALLER_INFRASTRUCTURE | 25 |
| API_BRIDGE | 9 |
| RESOURCE_INFRASTRUCTURE | 2 |
| **Total** | **767** |

`tools/hook_surface_probe.py` baseline would be `docs/audit/HOOK_SURFACE_BASELINE.json` if frozen.  This P0 run did not create it; the canonical surface is the 767 call sites above.

## 4. Disabled-feature zero-cost audit

The runtime contract in `FeatureSpec`, `LazyFeatureSpec` and `FeatureInstallRegistry` is:

```text
installAll() → for each spec of matching target/phase:
                 spec.isEnabled(prefs) == false → SKIPPED (no create, no FeatureDefinition)
                 spec.isEnabled(prefs) == true  → create() → install()
```

This means a disabled feature does **not** allocate its `FeatureDefinition`, hook objects, `BroadcastReceiver`, `ContentObserver`, listeners or context holders.  `LazyFeatureSpec` only holds metadata and two lambdas.

However, disabled features are **not completely free**:

1. The installer still iterates every registered spec for the target/phase and calls `isEnabled`.  For `com.android.systemui` this is 96 `isEnabled` evaluations at `PACKAGE_READY`; for `system_server` it is 50 at `SYSTEM_SERVER_STARTING`.
2. Each `isEnabled` does one or more `PrefMap` map lookups.  Some conditions check 2–3 preference keys in one expression.
3. `Kotlin object` singletons such as `WeatherDataController` are only loaded when the feature’s `installHook()` actually references them, so they are not initialized for disabled features.
4. `MainModule.mPrefs` itself is bootstrapped once per process and kept up to date by one `OnSharedPreferenceChangeListener`; this cost is per-process, not per-feature.

Verdict: the current architecture correctly provides **zero business-object cost** for disabled features, but still pays a small **install-time evaluation tax** of ~1–3 map lookups per feature.  That is an acceptable cold-path cost.

## 5. RemotePreferences / PrefMap hot-path cost

`mPrefs.get*` matches across `app/src/main/java`: **530**.

Top source files by `mPrefs.get*` call count:

| File | Calls |
|---|---|
| `mods/SystemUIStatusBarHooks.kt` | 127 |
| `mods/SystemUIControlCenterHooks.kt` | 69 |
| `mods/SystemClockHooks.kt` | 48 |
| `mods/Controls.kt` | 26 |
| `mods/SystemUILockScreenHooks.kt` | 22 |
| `utils/AudioVisualizer.kt` | 21 |
| `mods/utils/DeviceInfoMonitor.kt` | 19 |

`PrefMap` is an atomic-reference-backed immutable snapshot with a `ConcurrentHashMap` parsed-int cache.  Each `get*()` is a map lookup; `getStringAsInt` also hits the parsed-int cache.  The heaviest hot path is `SystemClockHooks.initClockStyle`, which reads 10+ preference values on every clock update tick.  This is the most obvious single hot-path candidate.

## 6. Thread, Handler, Executor, Timer, Receiver, Observer, Listener

Static match counts in `app/src/main/java`:

| Pattern class | Matches |
|---|---|
| `registerReceiver` / `registerContentObserver` / `registerOnSharedPreferenceChangeListener` / `add*Listener` / `set*Listener` | 241 |
| `Handler(` / `HandlerThread` / `Executors.` / `Thread(` / `Timer(` / `CoroutineScope` / `GlobalScope` / `runBlocking` | 43 |

Most receiver registrations are wrapped by `ModuleHelper.replaceModuleRegistration()` or `ReceiverRegistry`, which track owner and provide release paths.  A minority register inside hook callbacks (`SystemClockHooks`, `SystemUILockScreenHooks`, `SystemUIMonitorAndTileHooks`, `SystemUIScreenshotHooks`, `Various`) and rely on `ModuleHelper.replaceModuleRegistration()` for unregistration.

## 7. Reflection, ClassLoader and DexKit

- `Class.forName` / `getDeclaredMethod` / `getMethod` / `getField`: 41 matches.
- DexKit / DexKitBridge references: 20 matches.
- `MainModule.loadDexKit()` loads `libdexkit.so` on demand for the few features that still need DexKit.

Most reflection is in install-time class resolution or one-shot helpers.  A file-level P1 audit is needed to confirm no reflection is performed inside per-frame or per-tick hook callbacks.

## 8. Static strong Android owner / memory baseline

`tools/source_hazard_scan.py` baseline holds 1028 reviewed findings:

| Rule | Count | Top file |
|---|---|---|
| `CATCH_THROWABLE_NO_FATAL` | 730 | `mods/Various.kt` 70, `mods/SystemLockScreenHooks.kt` 61 |
| `STATIC_STRONG_ANDROID_OWNER` | 223 | `mods/SystemUIStatusBarHooks.kt` 34, `mods/SystemUIControlCenterHooks.kt` 22 |
| `EMPTY_CATCH` | 44 | (spread) |
| `PRINT_STACK_TRACE` | 30 | `utils/Helpers.kt` 9, `PreferenceFragmentBase.kt` 3 |
| `NATIVE_LOAD` | 1 | `MainModule.java` (DexKit) |

Notes:
- `STATIC_STRONG_ANDROID_OWNER` is intentionally broad and matches many local `val` view/context variables, not only static fields.  The high counts in `SystemUIStatusBarHooks` and `SystemUIControlCenterHooks` justify a focused manual review.
- `CATCH_THROWABLE_NO_FATAL` catches `Throwable` without rethrowing `OutOfMemoryError`/`ThreadDeath`.  `FeatureInstallRegistry` and several places already call `FatalErrors.rethrowIfFatal` / `FatalErrors.unwrapAndRethrowIfFatal`; many `catch (t: Throwable) { XposedHelpers.log(t) }` blocks do not, which violates the `OutOfMemoryError` must not be swallowed rule.

## 9. UI startup / layout / overdraw

No device instrumentation yet.  Theoretical cost centers:

- `MainActivity` + `PreferenceFragmentBase` inflate a large preference tree from XML.
- `MainFragment` / `SubFragmentWithSearch` build large `RecyclerView` / `PreferenceScreen` lists.
- `SystemUIStatusBarHooks` touches `StatusBar`, `Keyguard`, `ControlCenter` views at runtime; `initClockStyle` is called per tick.

## 10. Device measurement protocol (DEVICE_EVIDENCE_PENDING)

All real-world measurements below are pending.  This is the protocol to be run on a HyperOS 1 / Android 14 device with LSPosed:

1. **PSS / USS baseline**: `adb shell dumpsys meminfo <package>` before and after enabling the module, for `com.android.systemui`, `com.miui.home`, `system`.
2. **CPU / start time**: `adb shell am start -W -n tv.withaibuild.customiuizer.r14/.MainActivity` and `dumpsys activity procstats` over 1 minute.
3. **Frame time**: `adb shell dumpsys gfxinfo com.android.systemui` and `adb logcat -s Choreographer:I` while toggling high-frequency status bar features (clock seconds, network speed).
4. **Battery / idle drain**: `adb shell dumpsys batterystats --checkin` or `Battery Historian` after 1 hour idle with module enabled vs disabled.
5. **Hook installation time**: LSPosed verbose log at process start to measure `MainModule.onPackageReady` latency.
6. **RemotePreferences churn**: LSPosed log / custom counter for `OnSharedPreferenceChangeListener` callback frequency and `getAll()` size.

## 11. P0 optimization candidates

| # | Slice | Yield | Risk | Verification |
|---|---|---|---|---|
| A | `SystemClockHooks.initClockStyle` — cache or reduce `mPrefs` reads per tick | High (per-second hot path) | Medium (preference change invalidation) | Profiler / frame time |
| B | `SystemUIStatusBarHooks` — review 127 `mPrefs.get*` calls and lift install-time constants | High | Medium | Static scan + device metrics |
| C | `utils/Helpers.kt` — replace 9 `printStackTrace()` with controlled logging | Low | Very low | `source_hazard_scan.py` |
| D | `CATCH_THROWABLE_NO_FATAL` in top 2 files — rethrow `VirtualMachineError`/`ThreadDeath` correctly | Medium | Medium (may expose hidden failures) | Unit tests + process stability |

Recommended next step: **Slice A or Slice B** after the first device baseline run.

## 12. Sign-off

- P0 static baseline established.
- No code optimized yet; no behavior changed.
- EXACT_LOCK remains on `devin/a14-performance-optimization`.
