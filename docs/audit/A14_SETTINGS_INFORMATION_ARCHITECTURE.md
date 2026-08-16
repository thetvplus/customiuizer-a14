# A14 P2 — Settings Information Architecture / Grouping / Summary Cleanup

## Base

```text
BASE SHA (corrective) = f9bee6adf1f56ae18b342417a18730eaac40bd2d
```

## Scope

P2 only touches user-facing preference XML layout, category titles and redundant
summaries.  No production Java/Kotlin changes, no feature IDs, no preference
key/default/entry/dependency/fragment/intent changes, no cross-page migrations.

## Production user-facing page inventory

通过实际枚举 `app/src/main/res/xml/prefs_*.xml` 得到 **29** 个生产用户可见页面：

```text
prefs_controls.xml
prefs_launcher.xml
prefs_main.xml
prefs_system.xml
prefs_system_alarmonlock.xml
prefs_system_albumartonlock.xml
prefs_system_autobrightness.xml
prefs_system_batteryindicator.xml
prefs_system_charginginfo.xml
prefs_system_controlcenter_clock.xml
prefs_system_controlcenter_themestyle.xml
prefs_system_detailednetspeed.xml
prefs_system_hideicons.xml
prefs_system_lockscreenshortcuts.xml
prefs_system_noscreenlock.xml
prefs_system_screenshot.xml
prefs_system_secureqs.xml
prefs_system_statusbarcontrols.xml
prefs_system_statusbar_batterystyle.xml
prefs_system_statusbar_batterytempandcurrent.xml
prefs_system_statusbar_clock.xml
prefs_system_statusbar_mobilesignal.xml
prefs_system_statusbar_righticons.xml
prefs_system_statusbar_showdevicetemperature.xml
prefs_system_vibration_amp.xml
prefs_system_visualizer.xml
prefs_various.xml
prefs_various_calluibright.xml
prefs_various_hiddenfeatures.xml
```

分类（不重复计数）：

| 类别 | 数量 | 文件 |
|---|---|---|
| TOP_SELECTOR | 1 | `prefs_main.xml` |
| CANONICAL_ROOT | 4 | `prefs_controls.xml`, `prefs_launcher.xml`, `prefs_system.xml`, `prefs_various.xml` |
| MANUAL_SECONDARY | 24 | 所有 `prefs_system_*.xml` 中除 `prefs_system.xml` 外的 22 个；以及 `prefs_various_calluibright.xml`、`prefs_various_hiddenfeatures.xml` |

| `prefs_main.xml` | `prefs_system.xml`, `prefs_launcher.xml`, `prefs_controls.xml`, `prefs_various.xml` | `prefs_system_alarmonlock.xml`, `prefs_system_albumartonlock.xml`, `prefs_system_autobrightness.xml`, `prefs_system_batteryindicator.xml`, `prefs_system_charginginfo.xml`, `prefs_system_controlcenter_clock.xml`, `prefs_system_controlcenter_themestyle.xml`, `prefs_system_detailednetspeed.xml`, `prefs_system_hideicons.xml`, `prefs_system_lockscreenshortcuts.xml`, `prefs_system_noscreenlock.xml`, `prefs_system_screenshot.xml`, `prefs_system_secureqs.xml`, `prefs_system_statusbar_batterystyle.xml`, `prefs_system_statusbar_batterytempandcurrent.xml`, `prefs_system_statusbar_clock.xml`, `prefs_system_statusbar_mobilesignal.xml`, `prefs_system_statusbar_righticons.xml`, `prefs_system_statusbar_showdevicetemperature.xml`, `prefs_system_statusbarcontrols.xml`, `prefs_system_vibration_amp.xml`, `prefs_system_visualizer.xml`, `prefs_various_calluibright.xml`, `prefs_various_hiddenfeatures.xml` | `prefs_various_calluibright.xml`（二级入口）, `prefs_various_hiddenfeatures.xml` |

各类别说明：

- `prefs_main.xml`：顶层入口，2 个 category（Mods / Settings）。
- 4 canonical root pages：经 `tools/generate_preference_artifacts.py` 生成 lazy category 拆页与 search breadcrumb。
- 24 manual secondary pages：`System.kt` / `Various.kt` 中通过 `openSubFragment` 直接打开的独立页；`prefs_various_calluibright.xml` 和 `prefs_various_hiddenfeatures.xml` 也直接由 `prefs_various.xml` 中的 `PreferenceEx` 入口打开，同属 manual secondary。
- 不存在 `shortcuts.xml` 或单独的 tertiary 分类；相关文件未计入。

## Changed pages

| Page | Old group | Final group | Decision | Why |
|---|---|---|---|---|
| `prefs_system.xml` “Other” | `pref_key_system_cat_other` 下 Wi-Fi / USB 分散 | 插入 `PreferenceCategoryEx @string/system_mods_connectivity` 作为 section header，Wi-Fi 与 USB 紧随其后 | B — section-divider grouping | USB 默认设置原来被挤在动画缩放之后，难以发现；Wi-Fi 与 USB 共享“连接”心智模型。使用 self-closing `PreferenceCategoryEx` 作为分隔标题，不改变 containment/dependency。 |
| `prefs_system.xml` | `pref_key_system_fivegtile` summary | summary 移除 | summary cleanup | 标题“5G tile”位于“控制中心”分组，summary 仅重复“Add 5G tile to control center”。 |
| `prefs_system.xml` | `pref_key_system_recents_blur` summary | summary 移除 | summary cleanup | 标题“Background blur”位于“Recent apps list”，且 SeekBar 格式已表达强度，summary 仅重复位置。 |
| `prefs_system_secureqs.xml` | one-item `PreferenceCategoryEx @string/settings` | 删除该 category，`pref_key_system_secureqs_keepopened` 直接作为 `PreferenceScreen` 子项并继承 `android:dependency` | D — 单一项 category 仅承担 dependency | 该 category 内仅有一个 child，且仅用于承载 `dependency`；将 `dependency` 直接移到 preference 上可等价保持行为，避免无意义分组。 |
| `prefs_system_statusbar_batterystyle.xml` | empty `PreferenceCategoryEx @string/settings` | 删除 | C — 空 category | 该 category 无 child、无独立功能，删除后页面更清晰。 |
| 14 manual secondary pages（见下表） | `PreferenceCategoryEx @string/settings` | 重命名为对应页面功能标题 | B — category 有多个相关子项，仅标题过于通用 | 这些页面都是单一主题页，原有 “Settings” 没有提供任何信息；重命名为页面标题可准确描述该组内容。 |

### 14 manual secondary page category renames

| File | Old title | New title | Dependency/dynamic preserved |
|---|---|---|---|
| `prefs_system_alarmonlock.xml` | `@string/settings` | `@string/system_lsalarm_title` | yes |
| `prefs_system_albumartonlock.xml` | `@string/settings` | `@string/system_albumartonlock_title` | yes |
| `prefs_system_autobrightness.xml` | `@string/settings` | `@string/system_autobrightness_title` | yes |
| `prefs_system_batteryindicator.xml` | `@string/settings` | `@string/system_batteryindicator_title` | yes |
| `prefs_system_charginginfo.xml` | `@string/settings` | `@string/system_charginginfo_title` | yes |
| `prefs_system_controlcenter_clock.xml` | `@string/settings` | `@string/system_statusbar_clocktweak_title` | yes |
| `prefs_system_lockscreenshortcuts.xml` | `@string/settings` | `@string/system_lockscreenshortcuts_title` | yes |
| `prefs_system_noscreenlock.xml` | `@string/settings` | `@string/system_noscreenlock_title` | yes |
| `prefs_system_screenshot.xml` | `@string/settings` | `@string/system_screenshot_title` | yes |
| `prefs_system_statusbar_batterytempandcurrent.xml` | `@string/settings` | `@string/system_statusbar_batterytempandcurrent_title` | yes |
| `prefs_system_statusbar_clock.xml` | `@string/settings` | `@string/system_statusbar_clocktweak_title` | yes |
| `prefs_system_statusbar_showdevicetemperature.xml` | `@string/settings` | `@string/system_statusbar_showdevicetemperature_title` | yes |
| `prefs_system_visualizer.xml` | `@string/settings` | `@string/system_visualizer_title` | yes |
| `prefs_various_calluibright.xml` | `@string/settings` | `@string/various_calluibright_title` | yes |

## Generic “Settings” group audit

| Phase | Count | List |
|---|---|---|
| Before | `15` | `prefs_main.xml` `@string/settings_title`; `prefs_system_alarmonlock.xml`, `prefs_system_albumartonlock.xml`, `prefs_system_autobrightness.xml`, `prefs_system_batteryindicator.xml`, `prefs_system_charginginfo.xml`, `prefs_system_controlcenter_clock.xml`, `prefs_system_lockscreenshortcuts.xml`, `prefs_system_noscreenlock.xml`, `prefs_system_screenshot.xml`, `prefs_system_statusbar_batterytempandcurrent.xml`, `prefs_system_statusbar_clock.xml`, `prefs_system_statusbar_showdevicetemperature.xml`, `prefs_system_visualizer.xml`, `prefs_various_calluibright.xml` `@string/settings` |
| After | `1` | `prefs_main.xml` `@string/settings_title` |
| Retained | `1` | `prefs_main.xml` “Settings”：该组包含 CustoMIUIzer 自身设置项（设置图标位置、语言、启动器图标），在该页面内“Settings”是最准确且唯一的顶层分组名称，因此保留。 |

## New groups / renamed groups

| Group title key | Coverage | Location |
|---|---|---|
| `system_mods_connectivity` | default + 9 locales | `prefs_system.xml` 中 `pref_key_system_cat_other` 下的 section header |
| 14 个重命名 category title | default + 9 locales | 14 个 manual secondary pages |

## Unchanged pages and reasons

| Page(s) | Reason |
|---|---|
| `prefs_main.xml` | 顶层 “Mods” / “Settings” 已经语义清晰；保留 “Settings” 见上表。 |
| `prefs_controls.xml` | Fingerprint / Power / Volume / Navbar / FSG 顶层分组清晰；嵌套的 “Vibration” / “Actions” 为空 section header，用于在 category 内做功能分区，语义合理。 |
| `prefs_launcher.xml` | Folders / App titles / Hidden apps / Gestures / Bug fixes / Other 已经语义清晰。 |
| `prefs_various.xml` | 7 个分组标题由 `tools/generate_preference_artifacts.py` 的 `VARIOUS_GROUPS` 显式绑定，修改标题/顺序需要同步改动生成器，超出 P2 资源-only 范围。 |
| `prefs_system_detailednetspeed.xml`, `prefs_system_hideicons.xml`, `prefs_system_statusbar_mobilesignal.xml`, `prefs_system_statusbarcontrols.xml`, `prefs_system_vibration_amp.xml`, `prefs_system_visualizer.xml` 等 | 已有明确 master switch + 单一功能分组或具体子分组（Time period / Vibration intensity / Adjustment sensitivity / Actions），无需改动。 |
| `prefs_system_statusbar_righticons.xml` | 三个分组 “Always show icons in statusbar” / “Move some icons to the left” / “Move some icons to the second row” 语义明确，符合 section-divider 模式。 |

## Empty / section-divider categories retained

| Page | Title | Reason |
|---|---|---|
| `prefs_controls.xml` | Vibration, Actions (×2) | 在父 category 内部做语义分区；保留。 |
| `prefs_system.xml` | Rotation, Backlight, Bug fixes, Additional functionality, Security, Connectivity, Screenshots, Volume dialog background blur, Animation scale | 在 `pref_key_system_cat_*` 大分组内部做语义分区；Connectivity 本次新增，其余保持。 |
| `prefs_various.xml` | 7 个顶层 header | 生成器 partition 边界；保留。 |

## Removed redundant summaries

| Page | Preference key | Removed summary rationale |
|---|---|---|
| `prefs_system.xml` | `pref_key_system_fivegtile` | Title 与分组已表达功能。 |
| `prefs_system.xml` | `pref_key_system_recents_blur` | Title、分组与 SeekBar 格式已表达功能。 |

## Retained important summaries

- `pref_key_system_noscreenlock`：标题 “Disable screen lock”，summary 说明不影响应用和指纹安全。
- `pref_key_system_credentials`：标题 “Unlock credentials”，summary 说明启动器图标行为与安全边界。
- `pref_key_system_drawer_blur`：标题 “Background blur”，summary 说明通知抽屉范围。
- `pref_key_controls_fsg_horiz`：标题 “Horizontal gestures”，summary 说明同时启用导航栏。

## Preference contract preservation

```text
PREFERENCE_KEYS_CHANGED = 0
DEFAULT_VALUES_CHANGED = 0
ENTRY_VALUES_CHANGED = 0
PREFERENCES_MOVED_ACROSS_PAGES = 0
PRODUCTION_JAVA_KOTLIN_CHANGED = NO
RECENTS_HIDE_APP_NAME_CONTRACT = PRESERVED
USB_PREFERENCE_CONTRACT = PRESERVED
```

## Category contract preservation

- 14 个重命名 category 全部保留了 `android:dependency`、`miuizer:dynamic`、`miuizer:indentLevel` 等 functional attributes。
- `prefs_system_secureqs.xml` 删除单一项 category 时，将原 category 的 `android:dependency` 等价转移到 child preference。
- `prefs_system_statusbar_batterystyle.xml` 删除空 category 不影响其它 preference 的 dependency/dynamic。
- `prefs_system.xml` 新增 `system_mods_connectivity` section header 不绑定任何 preference 的 containment/dependency。

## Runtime parent/category audit

针对本次改动涉及的 category/preference 搜索运行时 `findPreference`、`getParent`、`PreferenceGroup`、`removePreference`、`addPreference` 引用：

- `pref_key_system_usb_default_function`：仅在 `SystemUsbDefaultHooks.kt` 通过 `MainModule.mPrefs` 读取，无 UI parent 查找。
- `pref_key_system_wifipassword`：无运行时 parent/category 引用。
- `pref_key_system_secureqs_keepopened`：无运行时 parent/category 引用。
- `pref_key_system_statusbar_batterystyle` 及其 children：无运行时 parent/category 引用。
- 14 个重命名 category 均无运行时 key 引用，仅作为渲染标题。

## Structural contract test

`tools/tests/test_p2_settings_information_architecture.py` 覆盖：

- 所有 keyed preference 的全属性比较（key/title/defaultValue/entries/entryValues/dependency/fragment/persistent/所有 miuizer:*/app:*）。
- 仅允许已批准的 summary 删除：`pref_key_system_fivegtile`、`pref_key_system_recents_blur`。
- 所有 `PreferenceCategoryEx` 的 attribute 比较；允许已批准的 title 重命名；允许已批准的空/单一项 category 删除/新增。
- 无跨页 key 迁移。
- 新增/重命名 category title 在所有 10 个 locale 中存在。
- 所有 category title `@string/...` 引用在 `values/strings.xml` 中可解析。
- USB 与 Recents 隐藏应用名称契约检查。

## Locale coverage

新增/重用的 category title 字符串：

- `system_mods_connectivity`
- `system_lsalarm_title`
- `system_albumartonlock_title`
- `system_autobrightness_title`
- `system_batteryindicator_title`
- `system_charginginfo_title`
- `system_statusbar_clocktweak_title`
- `system_lockscreenshortcuts_title`
- `system_noscreenlock_title`
- `system_screenshot_title`
- `system_statusbar_batterytempandcurrent_title`
- `system_statusbar_showdevicetemperature_title`
- `system_visualizer_title`
- `various_calluibright_title`

全部覆盖：

- `values`
- `values-zh-rCN`
- `values-zh-rTW`
- `values-cs-rCZ`
- `values-es-rES`
- `values-ja-rJP`
- `values-pt-rBR`
- `values-ru-rRU`
- `values-tr-rTR`
- `values-vi-rVN`

## P2 / P5 边界

P2 完成项：

- 全部 production user-facing preference XML 的 IA audit 与分类；
- generic “Settings” category 清理/重命名；
- 空 category 删除；
- 单一项 category 在能等价迁移 dependency 时删除；
- 已确认冗余 summary 清理；
- 结构 contract 测试硬化；
- category title 本地化覆盖与引用解析验证；
- audit 文档。

P5 后续可负责项（不再承担 P2 未完成项）：

- long-language clipping 实测；
- search runtime rendering / jump accuracy；
- lazy-loading 一致性；
- unsupported item search visibility；
- 全量翻译一致性微调。

## Validation

```text
python -m unittest tools/tests/test_p2_settings_information_architecture.py
python tools/verify.py fast --changed
python tools/verify.py full
python tools/audit-feature-semantics.py --validate
git diff --check
```

All PASS。

## Device acceptance

```text
DEVICE_ACCEPTANCE = NOT_REQUIRED_FOR_P2_STRUCTURE
```

P2 为资源/IA 改动，实机 runtime 接受在最终签名 APK 阶段统一验证。
