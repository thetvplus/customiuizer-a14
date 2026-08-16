# C1 StatusBarHeight Device Gate

Branch: `devin/a14-architecture-c-r14.20.0`

Base SHA / final audited code: `6dcd00e8f7a367d8706242fe345b0114f7fb3481`

Candidate SHA: `6dcd00e8f7a367d8706242fe345b0114f7fb3481`

Oracle source: `2c4efeafc8655855b824b72ecbf6106641b04a8e`

Production freeze: `7fb1ca5d7b631a2410e92eb41a571b62c3fc6de9`

## 1. Host tooling

Temporary host-side harness:

- `C:\Users\tv\Downloads\Peengeek\device-gate-temp\set-statusbar-height.ps1`
- No production source modified.

The script documents the real preference path and attempts UI fallback with
`uiautomator dump` / node-center taps. It does not use hard-coded coordinates.

## 2. Preference persistence / mirror path

| Item | Value |
|------|-------|
| UI XML key | `pref_key_system_statusbarheight` |
| Runtime PrefMap key | `system_statusbarheight` |
| Normalization point | `canonicalPreferenceKey` strips `pref_key_`; `AppHelper.prefixKey` re-adds it for local `SharedPreferences` |
| Persistent storage (app) | `/data/data/tv.withaibuild.customiuizer.r14/shared_prefs/customiuizer_prefs.xml` key `pref_key_system_statusbarheight` (int) |
| Persistent storage (module) | libxposed `RemotePreferences` name `customiuizer_prefs_remote` key `pref_key_system_statusbarheight` |
| Mirror mechanism | `XposedServiceManager` registers `OnSharedPreferenceChangeListener` on `AppHelper.appPrefs`; on change it writes to `RemotePreferences` via `edit().putInt(key, value).apply()`; on bind it runs `PrefsMirror.plan` to reconcile |
| Observer notification | In the hooked process `PreferenceBootstrap` listens on the remote `SharedPreferences`; on change it updates `PrefMap` and dispatches `canonicalPreferenceKey(key)` to `ModuleHelper.handlePreferenceChanged`, which reconfigures `StatusBarHeightConfig` |

Command-side mutation possible: **NO** (no exported broadcast/service/activity or writable provider for a single preference).

Root alone is **NOT sufficient**: even with root/app_process preference mutation, the Xposed hooks in `system_server` and `com.android.systemui` cannot execute `InsetsSource.setFrame`, `DisplayPolicy.layoutWindowLw`, `WindowState.setFrames`, or `DecorInsets.Info.update` unless an active Xposed/libxposed framework is actually loading CustoMIUIzer into those processes.

UI fallback possible: **PARTIAL** — `uiautomator dump` on this device does not expose the `PreferenceFragmentCompat` `RecyclerView` children, so text-based navigation cannot locate `状态栏高度`.

## 3. Device environment

| Property | Value |
|----------|-------|
| Device | fuxi |
| Model | 2211133G |
| ROM | Xiaomi/fuxi_global/fuxi:14/UKQ1.230804.001/V816.0.7.0.UMCTWXM:user/release-keys |
| Android | 14 |
| API | 34 |
| wm size | 1080x2400 |
| wm density | 440 (override 469) |
| SystemUI version | 20230316.0 |

LSPosed / libxposed framework: **NOT DETECTED**.

- `pm list packages` and `pm list packages -f` show no `org.lsposed.*` or LSPosed manager.
- `ps -A | grep -i lsposed` shows no daemon.
- `adb shell su` / `which su` / `adb root` fail; device is not rooted.

Installed Xposed modules (packages only, no framework observed):

- `package:com.github.tianma8023.xposed.smscode`
- `package:io.github.chsbuffer.revancedxposed`

## 4. APK provenance

### Oracle A (r14.18.8)

- Path: `C:\Users\tv\Downloads\Peengeek\release\r14.18.8\CustoMIUIzer-A14-r14.18.8.apk`
- Size: 3,369,838 bytes
- SHA256: `39FCAE4D9213A24192F79B31C1EF78F6955A5A41E6E1BE9839BC535B02ECA989`
- versionCode: `197`
- versionName: `r14.18.8`
- revision: `2c4efeaf`
- buildType: `release`
- signer: V2, `CN=CustoMIUIzer A14, OU=Release, O=tomthenpc, C=CN`
- signer SHA256: `c0eff2dc4e662717195490da78b12a984c6f2e6bd38acf4edad14d53e3d22e70`

### Candidate B (r14.20.0)

- Path: `app\build\outputs\apk\release\CustoMIUIzer-A14-r14.20.0.apk`
- Size: 3,369,838 bytes
- SHA256: `00DE3C83FE199F1E600BB6F4F08615842FE266DEDF2A859E08419796CF986CF5`
- versionCode: `198`
- versionName: `r14.20.0`
- revision: `6dcd00e8`
- buildType: `release`
- signer: V2, `CN=CustoMIUIzer A14, OU=Release, O=tomthenpc, C=CN`
- signer SHA256: `c0eff2dc4e662717195490da78b12a984c6f2e6bd38acf4edad14d53e3d22e70`
- V2: **true**

Signer parity: **PASS** (same certificate).

## 5. Gate status

`C1_DEVICE_GATE`: **BLOCKED_BY_ENVIRONMENT**

The C1 device A/B gate is **blocked before the behavior matrix can be executed**. This is a **device environment precondition failure**, not a test harness coordinate-guessing failure and not an Architecture C product regression.

- The target feature is an Xposed module that must run in `system_server` and `com.android.systemui`.
- There is **no verified active Xposed/libxposed framework environment** on the connected device capable of loading CustoMIUIzer into those processes.
- Without such a framework, no status-bar height behavior can be observed regardless of how the preference is set.

`C2_STARTED`: **false**

No production source modified.

## 6. Unblock paths

A. Use this device after a **verified active compatible Xposed framework** is installed/configured and CustoMIUIzer is actually loaded in the required scope (`system_server`, `com.android.systemui` at minimum).

B. Use **another device** that already has a verified working Xposed framework + CustoMIUIzer environment.

Prefer B if such a device already exists.

Rooting/modifying this device merely to make the test harness convenient is not requested.

## 7. Framework verification requirements

Before starting CASE 1, positive evidence is required. Package name greps and the absence of `su` are not authoritative.

At least one framework-level signal **and** one CustoMIUIzer-level signal are required, such as:

- Framework manager reports active.
- Framework/module logs show CustoMIUIzer load.
- libxposed service binds successfully.
- CustoMIUIzer reports Xposed service/framework attached.
- `system_server` logs contain expected one-time StatusBarHeight install diagnostics.

Only after framework activity is proven should preference mutation be solved. Manual UI preference changes are acceptable for the Device Gate if the normal UI is usable.

## 8. Stop condition

Until a verified framework-capable device is available:

- Stop Device Gate work.
- Preserve candidate and oracle APKs and signer parity evidence.
- Do not start C2.
