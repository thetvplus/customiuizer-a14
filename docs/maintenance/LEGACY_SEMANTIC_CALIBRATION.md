# Legacy Feature Semantic Calibration

Upstream semantic reference: [MonwF/customiuizer v24.10.12](https://github.com/MonwF/customiuizer/releases/tag/v24.10.12) (`d8f162653c413674f36a7d8d2b05cbd8543cf40c`; tags `v24.03.14`–`v24.10.12` share this commit).

This manifest is the regression contract for mechanical-migration drift. Compiling hooks is not the same as preserving user-feature semantics.

Compare unit: one user feature (preference → option → installer gate → process → hook → producer → formatter → placement → updater lifecycle).

Do not treat current production code as source of truth.

## Batch 1 gate

| Field | Value |
| --- | --- |
| BATCH_1_GATE | PASS |
| LEGACY_SEMANTIC_CALIBRATION_STAGE | COMPLETE |
| CONFIRMED_SEMANTIC_DRIFT | 2 |
| SEMANTIC_DRIFT_FIXED | 2 |
| ROM_COMPATIBILITY_GAPS | 2 |
| DEAD_FEATURES | 0 |
| REMOVED_BY_PRODUCT | 58 |
| NO_LIGHT_OPTION_3 | INTENTIONAL_A14_PRODUCT_DIVERGENCE |
| NETSPEED_FORMATSPEED | ROM_EVIDENCE_HOLD |
| LOCKSCREEN_TIMEOUT | ROM_EVIDENCE_HOLD |

Confirmed Batch 1 code changes (internal audit; not all are user-facing release notes):

1. `system_nolightuponcharges` option **2** charging wake suppression (POWER / PLUGGED / RAPID / WIRELESS)
2. Battery details “only while charging” when `sBatteryStatus` is unavailable

`NO_LIGHT_OPTION_3 = INTENTIONAL_A14_PRODUCT_DIVERGENCE`

A14 option 3 is **not** an upstream semantic restoration. Upstream option 3 blocked POWER/PLUGGED. A14 option 3 is native screen wake + charge animation suppressed. Do not count that redesign as `SEMANTIC_DRIFT`.

## Inventory snapshot

| Metric | Count |
| --- | --- |
| A14 preference keys | 642 |
| Upstream preference keys | 635 |
| Shared keys | 561 |
| A14-only keys (new / renamed) | 81 |
| Upstream-only keys | 74 |
| XML default mismatches (including omitted `defaultValue="false"`) | 28 |
| Real XML default mismatches | 4 |
| Array entry mismatches | 1 (`lightups` labels; A14 option-3 wording is product, not upstream restore) |
| SeekBar range mismatches | 7 |

Regenerate the raw key diff with:

```bash
python tools/legacy_semantic_compare.py --upstream /path/to/MonwF-customiuizer
```

## Classification legend

| Result | Meaning |
| --- | --- |
| SEMANTIC_MATCH | Current architecture preserves upstream user contract |
| SEMANTIC_DRIFT | Visible legacy feature still exists; behavior diverged without documented intent |
| INTENTIONAL_DIVERGENCE | A14 product change (HyperOS 1, changelog, or explicit redesign) |
| INTENTIONAL_A14_PRODUCT_DIVERGENCE | Named A14 product marker. `NO_LIGHT_OPTION_3` uses this; it is not an upstream restoration |
| ROM_COMPATIBILITY_GAP | Semantics known; HyperOS 1 target missing or unproven |
| DEAD_FEATURE | Current A14 UI/pref still exists, and runtime is unreachable |
| REMOVED_BY_PRODUCT | Upstream-only feature deliberately dropped from the A14 product |
| UNREACHABLE_OPTION | UI/pref exists but installer/runtime never applies it |
| INSUFFICIENT_EVIDENCE | Cannot tell INTENTIONAL vs bug |

`DEAD_FEATURE` is not used for prefs that A14 already removed from the UI.

## Frozen A14 surfaces (do not reopen here)

Dynamic Island, StrongToast geometry, Backup V2, USB default purpose, matched restart, search architecture, lazy pages, API 101/102 foundation, FeatureInstallRegistry, CurrentPreferenceContract, SettingsMemoryTrim.

## High-confidence corrective batch 1 (applied)

### No screen light up on charge

- Feature: `system_nolightuponcharges`
- A14 product contract (authoritative; differs from MonwF upstream option 3):
  - `1` = stock
  - `2` = do not wake on charging + do not show charge animation
  - `3` = native screen wake + do not show charge animation (`NO_LIGHT_OPTION_3 = INTENTIONAL_A14_PRODUCT_DIVERGENCE`)
- Actual charging drift fixed: option **2** omitted `POWER` / incomplete wake-reason suppression
- system_server installer: `option == 2` only. Option 3 must not depend on RAPID_CHARGE/WIRELESS_CHARGE existing on a ROM
- `shouldSkipChargeWake`: option 2 blocks POWER, PLUGGED*, RAPID_CHARGE, WIRELESS_CHARGE, WIRELESS_RAPID_CHARGE; option 1 and 3 block none. Callback-read so a live 2→3 change without process restart stops blocking wake
- SystemUI installer stays `option > 1`; `MiuiChargeController.shouldShowChargeAnim = false` for both 2 and 3
- UI: English “Light up screen without animation”; Chinese “点亮但不显示动画”. Do not restore upstream “Only events without animation.”
- Tests: `NoLightUpOnChargeContractTest`
- Result: option-2 wake suppression = SEMANTIC_DRIFT → fixed; option 3 = INTENTIONAL_A14_PRODUCT_DIVERGENCE, not SEMANTIC_DRIFT

### Battery details “show when charging only”

- Feature: `system_statusbar_batterytempandcurrent_incharge`
- Upstream contract: if ChargeUtils was resolved and `sBatteryStatus` is missing, hide the reading
- Previous A14: missing status showed the reading anyway
- Fix: `DeviceInfoChargeVisibility.shouldShowWhenInChargeOnly`; resolve `com.miui.charge.ChargeUtils` then `com.android.keyguard.charge.ChargeUtils`
- Missing ChargeUtils class still skips the filter (show), matching upstream
- When ChargeUtils exists but `sBatteryStatus` is `NOT_EXIST_SYMBOL`, keep the resolved class. Clearing it fail-opens the next tick (`chargeUtilsClass ?: return true`) and shows the reading
- Tests: `DeviceInfoChargeVisibilityTest`
- Result: SEMANTIC_DRIFT → fixed

## Status bar

| Feature | Result | Notes |
| --- | --- | --- |
| Device temperature CPU/battery sources | INTENTIONAL_DIVERGENCE | r14.20.2: per-mode sources, dynamic thermal zone |
| Device temperature dual-row left/right | SEMANTIC_MATCH | Dual-row left consumer restored in r14.20.2 |
| Temperature/battery fontsize default 0 | INTENTIONAL_DIVERGENCE | r14.20.2 keep-native-if-it-fits |
| Monitor install gate (temp OR battery) | SEMANTIC_MATCH | |
| Battery content enum 1–5 | SEMANTIC_MATCH | |
| Clock position `> 1 && !dualrows` | SEMANTIC_MATCH | |
| Hide status bar icons | SEMANTIC_MATCH | |
| Dual-row layout | SEMANTIC_MATCH | A14 adds left-ratio |
| Digital signal | NEW_A14_FEATURE | |
| Detailed netspeed style 2 | SEMANTIC_MATCH | TX/RX detailed |
| Netspeed style 3 | INTENTIONAL_DIVERGENCE | A14 label is single-row, not upstream fake-dual-row |
| Hide low / hide B/s on style 1 | ROM_COMPATIBILITY_GAP | Upstream `formatSpeed`; A14 ROM method unproven. HOLD |
| Netspeed/sound/DND at-right | REMOVED_BY_PRODUCT | Prefs removed from A14 UI |
| Status bar height default 11 | INTENTIONAL_DIVERGENCE | HyperOS 1 baseline vs upstream 19 |

### Network speed style 1 + hide-low / hide-B/s truth table

| style | low | secunit | Upstream hook | Current A14 |
| --- | --- | --- | --- | --- |
| 1 | off | off | none | none |
| 1 | on | * | `FormatNetworkSpeedHook` | none (HOLD) |
| 1 | * | on | `FormatNetworkSpeedHook` | none (HOLD) |
| 2 | * | * | `DetailedNetSpeedHook` | `DetailedNetSpeedHook` |
| fake dual-row | * | * | `FormatNetworkSpeed` + `network_speed_suffix` | A14 style 3 is single-row layout, not this path |

Do not guess an A14 `formatSpeed` target until ROM evidence exists.

## Control center / system / notifications

| Feature | Result | Notes |
| --- | --- | --- |
| Lock screen timeout `system_lstimeout` | ROM_COMPATIBILITY_GAP | Resource `config_lockScreenDisplayTimeout` only; upstream also hooked `applyUserActivityTimeout`. HOLD without A14 ROM evidence |
| CC plugin volume bundle | INTENTIONAL_DIVERGENCE | Split into current CC plugin + dedicated features |
| Folder blur disable | INTENTIONAL_DIVERGENCE | A14 extension of opacity gate |

Do not guess an A14 lockscreen timeout runtime target until ROM evidence exists.

## REMOVED_BY_PRODUCT

Upstream-only feature prefs with no A14 UI. These are product removals, not dead reachable options. Examples:

- `system_securecontrolcenter`
- `system_volumesteps`
- `system_compactnotif`
- `system_separatevolume` / `system_separatevolume_slider`
- `system_hidemoreicon`
- `system_betterpopups_swipedown`
- `system_messagingstylelines`
- `system_ccgridrows` / `system_qsgridcolumns` / `system_qsnolabels`
- `system_statusbar_netspeed_atright` / `_sound_atright` / `_dnd_atright`

Count: **58** feature keys. About/link prefs (`donate`, `github`, `xda`, …) are excluded. Renamed keys (`system_detailednetspeed` → `system_detailednetspeed_style`, `system_defaultusb` → `system_usb_default_function`) are not counted here.

## Launcher

Core gesture `action != 1` gates, folder columns, recents blur, freeform split, icon scale, and title fontsize match upstream thresholds. Dock titles use a hook instead of resource override (INTENTIONAL_DIVERGENCE; no A14 evidence to restore both).

## Intentional XML default / range changes

- `system_statusbar_*_fontsize` 16 → 0 (native size)
- `system_statusbarheight` 19 → 11
- Several vertical-offset max 16 → 20
- Most `default_drift` hits are omitted `android:defaultValue="false"` vs explicit `false` (same boolean default)

## Product decisions still open

None that block this batch. HOLD items above need ROM evidence, not a product call.

## Test coverage for this gate

- `NoLightUpOnChargeContractTest`
- `DeviceInfoChargeVisibilityTest`
- Existing DeviceInfo / netspeed / feature-wiring tests remain the match baseline
