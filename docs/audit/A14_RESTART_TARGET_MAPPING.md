# A14 P3-A — Restart Target Evidence Map / Implementation Preflight

## Base

```text
BASE SHA = 4c5e7c5f997e026a4d6b2d4551a700d026624a0b
```

## Scope

本阶段只建立 preference -> restart target 数据。禁止 production implementation。

- 使用 `feature-semantics/a14.json` 作为辅助候选（非权威）。
- 结合 `LazyFeatureSpec` / `FeatureDefinition` / `ProcessRouter` 进行 package 反推。
- 对个别 key 做了源码级人工复核/override。
- 不修改 `feature-semantics` 生成器，不修改 production 代码。

## Existing restart architecture

当前 `PreferenceFragmentBase.kt` 提供两个底层原语：

1. `restartTarget(packageName, successRes, failureRes)`：root shell 执行 `am force-stop <package>`。
2. `restartTargetProcess(processName, successRes, failureRes)`：root shell 执行 `pidof <process>` 后 `kill -9`。

当前 `menu_mods.xml` 有：

- `restartlauncher`
- `restartsystemui`
- `restartsecuritycenter`
- `softreboot`

## Current page-name hardcoding inventory

```text
CategorySelector.kt:
  pref_key_system  -> activeMenus = 'systemui'
  pref_key_launcher -> activeMenus = 'launcher'

System.kt:
  pref_key_system_cat_recents  -> activeMenus = 'launcher'
  pref_key_system_cat_statusbar/lockscreen/qs/drawer -> activeMenus = 'systemui'
```

这些属于 `PAGE-NAME HARDCODE`，P3-B 必须替换。

## Target enum proposal

P3-A 自动重启目标只允许：

- `NONE`
- `LAUNCHER`
- `SYSTEMUI`
- `SECURITY_CENTER`

内部可记录但不得执行：

- `EXCLUDED_SYSTEM`（system_server / android / 需要 reboot / 无 proven primitive）
- `UNKNOWN`（证据不足）

## Page resolution model

- `CURRENT_PAGE_RESOLUTION = DIRECT_ONLY`：只看当前页面直接 setting。
- 不递归整个 subtree。
- 不包含 category key、navigation-only `PreferenceEx`、hidden/unsupported 项。
- app list / blacklist 等仅影响未来 attach 的 key 视为 `NONE`。

## Coverage summary

- `TOTAL_FUNCTIONAL_PREFERENCES = 455`
- `NONE = 62`
- `LAUNCHER = 51`
- `SYSTEMUI = 256`
- `SECURITY_CENTER = 17`
- `MULTI_TARGET_PAGES = 3`
- `MULTI_TARGET_PREFS (on multi-target pages) = 164`
- `EXCLUDED_SYSTEM = 35`
- `UNKNOWN = 34`

## Per-page executable target union

| page | executable targets | all classifications |
|---|---|---|
| prefs_controls.xml | LAUNCHER, SYSTEMUI | EXCLUDED_SYSTEM, LAUNCHER, NONE, SYSTEMUI, UNKNOWN |
| prefs_launcher.xml | LAUNCHER, SECURITY_CENTER | LAUNCHER, SECURITY_CENTER |
| prefs_main.xml |  | UNKNOWN |
| prefs_system.xml | LAUNCHER, SECURITY_CENTER, SYSTEMUI | EXCLUDED_SYSTEM, LAUNCHER, NONE, SECURITY_CENTER, SYSTEMUI, UNKNOWN |
| prefs_system_alarmonlock.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_albumartonlock.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_autobrightness.xml |  | EXCLUDED_SYSTEM, NONE |
| prefs_system_batteryindicator.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_charginginfo.xml | SYSTEMUI | NONE, SYSTEMUI |
| prefs_system_controlcenter_clock.xml | SYSTEMUI | NONE, SYSTEMUI, UNKNOWN |
| prefs_system_controlcenter_themestyle.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_detailednetspeed.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_hideicons.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_lockscreenshortcuts.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_noscreenlock.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_screenshot.xml |  | NONE, UNKNOWN |
| prefs_system_secureqs.xml | SYSTEMUI | NONE, SYSTEMUI |
| prefs_system_statusbar_batterystyle.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_statusbar_batterytempandcurrent.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_statusbar_clock.xml | SYSTEMUI | NONE, SYSTEMUI |
| prefs_system_statusbar_mobilesignal.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_statusbar_righticons.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_statusbar_showdevicetemperature.xml | SYSTEMUI | SYSTEMUI, UNKNOWN |
| prefs_system_statusbarcontrols.xml | SYSTEMUI | SYSTEMUI |
| prefs_system_vibration_amp.xml |  | EXCLUDED_SYSTEM, UNKNOWN |
| prefs_system_visualizer.xml | SYSTEMUI | SYSTEMUI |
| prefs_various.xml | SECURITY_CENTER | EXCLUDED_SYSTEM, NONE, SECURITY_CENTER, UNKNOWN |
| prefs_various_calluibright.xml |  | NONE, UNKNOWN |

## SecurityCenter keys

Count: 17

```text
system_applock_scramblepin
system_hidelowbatwarn
various_appdetails
various_appsort
various_disable_dock_suggest
various_disable_freeform_suggest_blacklist
various_disable_reset_recents_privacy_blur
various_disableapp
various_enable_expand_sidebar
various_hide_report_ondetails
various_privacyapps_column_nums4
various_replace_defaultopen_with_openbydefault
various_restrictapp
various_show_battery_temperature
various_skip_interceptperm
various_skip_securityscan
various_swipe_expand_sidebar
```

## Launcher keys (count: 51)

```text
controls_fsg_coverage
controls_fsg_horiz
controls_fsg_swipeandstop_disablevibrate
controls_fsg_width
launcher_closedrawer
launcher_closefolders
launcher_darkershadow
launcher_disable_log
launcher_disable_wallpaperscale
launcher_dock_bottommargin
launcher_dock_height
launcher_dock_topmargin
launcher_docktitles
launcher_fixanim
launcher_fixlaunch
launcher_folder_cols
launcher_folderblur_disable
launcher_folderblur_opacity
launcher_folderspace
launcher_hideseekpoints
launcher_hideseekpoints_edit
launcher_hidetitles
launcher_horizmargin
launcher_horizwidgetmargin
launcher_iconscale
launcher_indicator_topmargin
launcher_indicatorheight
launcher_infinitescroll
launcher_noclockhide
launcher_nounlockanim
launcher_nowidgetonly
launcher_nozoomanim
launcher_oldlaunchanim
launcher_privacyapps_gest
launcher_renameapps
launcher_sensorportrait
launcher_titlefontsize
launcher_titletopmargin
launcher_topmargin
launcher_unlockgrids
launcher_unlockhotseat
launcher_wallpaper_colormode
system_fw_splitscreen
system_hidefromrecents
system_hidefromrecents_apps
system_recents_blur
system_recents_card_style
system_recents_disable_wallpaperscale
system_recents_hide_statusbar
system_removecleaner
... and 1 more
```

## SystemUI keys (count: 256)

```text
controls_hidenavbar_whenscreenshot
controls_navbarmargin
controls_nonavbar
controls_volumedowndt_torch
controls_volumemedia_vibrate
controls_volumemedia_vibrate_ignore
system_4gtolte
system_albumartonlock
system_albumartonlock_blur
system_albumartonlock_gray
system_albumartonlock_scale
system_allownotiffloat
system_allownotifonkeyguard
system_allrotations2
system_applock_timeout
system_batteryindicator
system_betterpopups_allowfloat
system_betterpopups_allowfloat_apps
system_betterpopups_autoclose_expanded
system_betterpopups_center
system_betterpopups_delay
system_betterpopups_disablewhenmute
system_betterpopups_nohide
system_calendar_app
system_cc_btandtorch_ascard
system_cc_card_enabled_color
system_cc_card_enabled_color_custom
system_cc_card_enabled_iconcolor_custom
system_cc_card_enabled_primary_textcolor
system_cc_card_enabled_secondary_textcolor
system_cc_clock_centeralign
system_cc_clock_fontsize
system_cc_clock_verticaloffset
system_cc_clocktweak
system_cc_collapse_after_clicked
system_cc_enable_style_switch
system_cc_floatingtimetile
system_cc_fpstile
system_cc_freeform_when_longclick
system_cc_hide_edit
system_cc_hide_profile_monitoring
system_cc_hideoperator_delimiter
system_cc_show_stepcount
system_cc_slider_color_enable
system_cc_slider_icon_color
system_cc_slider_progress_color
system_cc_switch_qsandnotification
system_cc_tile_enabled_color
system_cc_tile_enabled_color_custom
system_cc_tile_enabled_iconcolor_custom
system_cc_tile_roundedrect
system_cc_volume_showpct
system_ccgridcolumns
system_chargeanimtime
system_charginginfo
system_charginginfo_current
system_charginginfo_temp
system_charginginfo_voltage
system_charginginfo_wattage
system_clock_app
system_colorizenotifs
system_colorizenotifs_apps
system_detailednetspeed_align
system_detailednetspeed_icon
system_detailednetspeed_low
system_detailednetspeed_lowlevel
system_detailednetspeed_secunit
system_detailednetspeed_style
system_disableanynotif
system_drawer_blur
system_drawer_date_fontsize
system_drawer_remove_emptynotify
system_drawer_removeshortcut
system_dttosleep
system_epm
system_expandheadups
system_expandheadups_apps
system_expandnotifs
system_expandnotifs_apps
system_fivegtile
... and 176 more
```

## EXCLUDED_SYSTEM keys (count: 35)

```text
controls_fingerprintfailure
controls_fingerprintscreen
controls_fingerprintsuccess
controls_fingerprintwake
controls_powerflash
controls_volumemedia_down
controls_volumemedia_up
system_apksign
system_applock
system_applock_skip
system_autobrightness
system_autobrightness_reset_when_screenoff
system_cleanopenwith
system_cleanshare
system_clearalltasks
system_dimtime
system_disableintegrity
system_downgrade
system_firstpress
system_force_darken_allapps
system_forceclose
system_hideproxywarn
system_ignorecalls
system_lockscreen_disable_strongauth_72h
system_lswallpaper
system_noducking
system_orientationlock
system_other_wallpaper_scale
system_remove_startactconfirm
system_removesecure
system_screenanim_duration
system_securelock
system_vibration_amp
various_allow_untrusted_touch
various_disable_access_devicelogs
```

## UNKNOWN keys (count: 34)

```text
controls_nonavbar_fix_inputmethod
controls_volumecursor
controls_volumecursor_reverse
miuizer_settingsiconpos
system_blocktoasts
system_cc_hidedate
system_drawer_date_centeralign
system_drawer_hidedate
system_gallery_screenshots_path
system_nooverscroll
system_qs_force_systemfonts
system_screenshot
system_statusbar_showdevicetemperature_content
system_statusbar_showdevicetemperature_hideunit
system_statusbar_showdevicetemperature_reverseorder
system_statusbar_showdevicetemperature_singlerow
system_statusbarheight
system_toasttime
system_vibration
system_vibration_amp_notif
system_vibration_amp_other
system_vibration_amp_ringer
system_wifipassword
various_alarmcompat
various_answerinheadup
various_calluibright
various_disable_defraud_apps_detect
various_gboardpadding_land
various_gboardpadding_port
various_installappinfo
various_installer_purify
various_miuiinstaller
various_persist_batteryoptimization
various_showcallui
```

## Multi-target page examples

- `prefs_controls.xml`
- `prefs_launcher.xml`
- `prefs_system.xml`

## Old individual menu plan

`OLD_INDIVIDUAL_MENU_PLAN = REMOVE_FROM_ORDINARY_PAGES`：
- 在普通二级页面不再单独显示 `restartlauncher` / `restartsystemui` / `restartsecuritycenter`。
- 新增 `restartmatched`，仅在当前页可执行目标非空时显示。
- `softreboot` 保持独立，不加入自动匹配集合。

## Matched restart action

`MATCHED_RESTART_ACTION = YES`：
- Title：`重启相关组件` / `Restart affected components`。
- 单页统一显示一次。

## Multi-target failure model

`MULTI_TARGET_FAILURE_MODEL = ATTEMPT_ALL + AGGREGATE`：
- 顺序执行所有匹配 target。
- 独立 failure isolation。
- 最终只 Toast 一次聚合结果。

## P3-B implementation proposal

1. 新增 `RestartTarget` enum：`NONE`, `LAUNCHER`, `SYSTEMUI`, `SECURITY_CENTER`, `EXCLUDED_SYSTEM`, `UNKNOWN`。
2. 新增 `PreferenceRestartTargetRegistry`（checked-in mapping 或从 feature-semantics 生成后 hardcode），运行时不解析 JSON/不扫描 XML/不做磁盘 I/O。
3. 页面打开时遍历当前 `PreferenceScreen` canonical keys，in-memory lookup，set union，过滤 `NONE` / `EXCLUDED_SYSTEM` / `UNKNOWN`。
4. `restartmatched` 可见条件：union 中 executable target 非空。
5. 执行：Launcher / SystemUI / SecurityCenter 分别调用已有 proven primitive；多 target 顺序执行并聚合结果。
6. soft reboot 保持原范围，不自动触发。

## P3-B safe to implement

`P3_B_SAFE_TO_IMPLEMENT = NO`

原因：仍有 34 个 `UNKNOWN` preference、35 个 `EXCLUDED_SYSTEM`、以及多包 master（Media/Common/Generic/Input/Phone/PackageInstaller/Settings 等）无法安全纳入自动匹配集合。必须先解决这些 key 的 package 证据或 UI 处理方案（如不纳入 matched action）。

## Evidence table (selected)

| page | canonical key | target | package / evidence |
|---|---|---|---|
| prefs_controls.xml | controls_fingerprintscreen | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_controls.xml | controls_fingerprintwake | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_controls.xml | controls_fingerprintfailure | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_controls.xml | controls_fingerprintsuccess | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt |
| prefs_controls.xml | controls_fingerprintsuccess_ignore | NONE | android / app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt |
| prefs_controls.xml | controls_powerflash | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_controls.xml | controls_powerflash_delay | NONE | android / app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt |
| prefs_controls.xml | controls_volumecursor | UNKNOWN | UNKNOWN / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/InputMethodFeatures.kt |
| prefs_controls.xml | controls_volumecursor_reverse | UNKNOWN | UNKNOWN / app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt |
| prefs_controls.xml | controls_volumecursor_apps | NONE | UNKNOWN / app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt |
| prefs_controls.xml | controls_volumedowndt_torch | SYSTEMUI | UNKNOWN / app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt |
| prefs_controls.xml | controls_mediaplayer_apps | NONE | UNKNOWN / app/src/main/java/tv/withaibuild/customiuizer/MainModule.java |
| prefs_controls.xml | controls_volumemedia_up | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/MainModule.java |
| prefs_controls.xml | controls_volumemedia_down | EXCLUDED_SYSTEM | UNKNOWN / app/src/main/java/tv/withaibuild/customiuizer/MainModule.java |
| prefs_controls.xml | controls_volumemedia_vibrate | SYSTEMUI | android / app/src/main/java/tv/withaibuild/customiuizer/mods/GlobalActions.kt |
| prefs_controls.xml | controls_volumemedia_vibrate_ignore | SYSTEMUI | android / app/src/main/java/tv/withaibuild/customiuizer/mods/GlobalActions.kt |
| prefs_controls.xml | controls_nonavbar | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/InputMethodFeatures.kt |
| prefs_controls.xml | controls_nonavbar_fix_inputmethod | UNKNOWN | UNKNOWN / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/InputMethodFeatures.kt |
| prefs_controls.xml | controls_hidenavbar_whenscreenshot | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_controls.xml | controls_navbarmargin | SYSTEMUI | UNKNOWN / app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt |
| prefs_controls.xml | controls_fsg_horiz | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_controls.xml | controls_fsg_horiz_apps | NONE | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt |
| prefs_controls.xml | controls_fsg_coverage | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt |
| prefs_controls.xml | controls_fsg_width | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt |
| prefs_controls.xml | controls_fsg_swipeandstop_disablevibrate | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt |
| prefs_launcher.xml | launcher_closefolders | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt |
| prefs_launcher.xml | launcher_folderblur_opacity | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_folderblur_disable | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_folder_cols | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt |
| prefs_launcher.xml | launcher_folderspace | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt |
| prefs_launcher.xml | launcher_hidetitles | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_docktitles | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPackageReadyFeatures.kt |
| prefs_launcher.xml | launcher_titlefontsize | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt |
| prefs_launcher.xml | launcher_titletopmargin | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt |
| prefs_launcher.xml | launcher_darkershadow | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_renameapps | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_privacyapps_gest | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt |
| prefs_launcher.xml | various_privacyapps_column_nums4 | SECURITY_CENTER | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SecurityCenterFeatures.kt |
| prefs_launcher.xml | launcher_disable_log | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPackageReadyFeatures.kt |
| prefs_launcher.xml | launcher_fixanim | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_fixlaunch | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_infinitescroll | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_noclockhide | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_nowidgetonly | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | system_resizablewidgets | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_nounlockanim | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_disable_wallpaperscale | LAUNCHER | android / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt |
| prefs_launcher.xml | launcher_wallpaper_colormode | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherAnimationHooks.kt |
| prefs_launcher.xml | launcher_nozoomanim | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_oldlaunchanim | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_sensorportrait | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_hideseekpoints | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_hideseekpoints_edit | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt |
| prefs_launcher.xml | launcher_indicatorheight | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt |
| prefs_launcher.xml | launcher_indicator_topmargin | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt |
| prefs_launcher.xml | launcher_unlockgrids | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPackageReadyFeatures.kt |
| prefs_launcher.xml | launcher_closedrawer | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt |
| prefs_launcher.xml | launcher_horizmargin | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt |
| prefs_launcher.xml | launcher_topmargin | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt |
| prefs_launcher.xml | launcher_unlockhotseat | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_launcher.xml | launcher_dock_height | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt |
| prefs_launcher.xml | launcher_dock_topmargin | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt |
| prefs_launcher.xml | launcher_dock_bottommargin | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt |
| prefs_launcher.xml | launcher_horizwidgetmargin | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt |
| prefs_launcher.xml | launcher_iconscale | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherIconHooks.kt |
| prefs_main.xml | miuizer_settingsiconpos | UNKNOWN | android / app/src/main/java/tv/withaibuild/customiuizer/mods/GlobalActions.kt |
| prefs_system.xml | system_allrotations2 | SYSTEMUI | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt |
| prefs_system.xml | system_orientationlock | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_autobrightness_reset_when_screenoff | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_nolightuponcharges | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_dimtime | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt |
| prefs_system.xml | system_lstimeout | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiResourceBootstrap.kt |
| prefs_system.xml | system_chargeanimtime | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt |
| prefs_system.xml | system_screenanim_duration | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemDisplayHooks.kt |
| prefs_system.xml | system_nosafevolume | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_noducking | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_firstpress | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_cc_volume_showpct | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_volumetimer | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_volumebar_blur_mtk | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_volume_mode_button_colors | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_volumedialogdelay_collapsed | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_volumedialogdelay_expanded | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_ignorecalls | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_ignorecalls_apps | NONE | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt |
| prefs_system.xml | system_nosilentvibrate | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_vibration | UNKNOWN | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt |
| prefs_system.xml | system_vibration_apps | NONE | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt |
| prefs_system.xml | system_toasttime | UNKNOWN | android / app/src/main/java/tv/withaibuild/customiuizer/mods/System.kt |
| prefs_system.xml | system_blocktoasts | UNKNOWN | android / app/src/main/java/tv/withaibuild/customiuizer/mods/System.kt |
| prefs_system.xml | system_blocktoasts_apps | NONE | android / app/src/main/java/tv/withaibuild/customiuizer/mods/System.kt |
| prefs_system.xml | system_networkindicator_wifi | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_statusbaricons_wifistandard | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemStatusBarIconHooks.kt |
| prefs_system.xml | system_statusbar_dualrows | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt |
| prefs_system.xml | system_statusbar_dualrows_clock_span2rows | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt |
| prefs_system.xml | system_statusbar_dualrows_left_ratio | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt |
| prefs_system.xml | system_statusbar_dualrows_firstrow_horizmargin | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt |
| prefs_system.xml | system_statusbar_dualrows_firstrow_horizmargin_left | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt |
| prefs_system.xml | system_statusbar_dualrows_firstrow_horizmargin_right | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt |
| prefs_system.xml | system_statusbarheight | UNKNOWN | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/CommonPackageFeatures.kt |
| prefs_system.xml | system_strong_toast_mode | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_strong_toast_position | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_strong_toast_bottom_offset | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_statusbar_iconsize | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiResourceBootstrap.kt |
| prefs_system.xml | system_statusbar_topmargin | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_statusbar_topmargin_val | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiResourceBootstrap.kt |
| prefs_system.xml | system_statusbar_topmargin_unset_lockscreen | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_statusbar_horizmargin | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_statusbar_horizmargin_left | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt |
| prefs_system.xml | system_statusbar_horizmargin_right | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt |
| prefs_system.xml | system_hidestatusbar_whenscreenshot | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_maxsbicons | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt |
| prefs_system.xml | system_statusbarcolor | SYSTEMUI | UNKNOWN / app/src/main/java/tv/withaibuild/customiuizer/MainModule.java |
| prefs_system.xml | system_statusbarcolor_apps | NONE | UNKNOWN / app/src/main/java/tv/withaibuild/customiuizer/MainModule.java |
| prefs_system.xml | system_removedismiss | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_drawer_removeshortcut | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_drawer_remove_emptynotify | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_drawer_hidedate | UNKNOWN | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemClockHooks.kt |
| prefs_system.xml | system_drawer_dateformat | NONE | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemClockHooks.kt |
| prefs_system.xml | system_drawer_date_centeralign | UNKNOWN | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemClockHooks.kt |
| prefs_system.xml | system_drawer_date_fontsize | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiResourceBootstrap.kt |
| prefs_system.xml | system_shortcut_app | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt |
| prefs_system.xml | system_clock_app | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt |
| prefs_system.xml | system_calendar_app | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt |
| prefs_system.xml | system_drawer_blur | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_notifafterunlock | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_allownotifonkeyguard | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_allownotiffloat | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_disableanynotif | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SettingsFeatures.kt |
| prefs_system.xml | system_notifimportance | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SettingsFeatures.kt |
| prefs_system.xml | system_notif_disable_fold | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_morenotif | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_colorizenotifs | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemColorizeNotificationHooks.kt |
| prefs_system.xml | system_colorizenotifs_apps | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemColorizeNotificationHooks.kt |
| prefs_system.xml | system_notify_openinfw | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_notify_openinfw_in_whitelist | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUINotificationHooks.kt |
| prefs_system.xml | system_notify_openinfw_apps | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUINotificationHooks.kt |
| prefs_system.xml | system_mutevisiblenotif | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_notifrowmenu | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_expandnotifs | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/notificationautoexpand/NotificationAutoExpandEffect.kt |
| prefs_system.xml | system_expandnotifs_apps | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/notificationautoexpand/NotificationAutoExpandEffect.kt |
| prefs_system.xml | system_minimalnotifview | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_notifchannelsettings | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_fivegtile | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIMonitorAndTileHooks.kt |
| prefs_system.xml | system_cc_floatingtimetile | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIMonitorAndTileHooks.kt |
| prefs_system.xml | system_cc_fpstile | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIMonitorAndTileHooks.kt |
| prefs_system.xml | system_cc_hidedate | UNKNOWN | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemClockHooks.kt |
| prefs_system.xml | system_cc_dateformat | NONE | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemClockHooks.kt |
| prefs_system.xml | system_qs_hideoperator | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_cc_hideoperator_delimiter | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_cc_show_stepcount | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_cc_collapse_after_clicked | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_cc_freeform_when_longclick | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt |
| prefs_system.xml | system_cc_hide_edit | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_cc_hide_profile_monitoring | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_ccgridcolumns | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_showpct | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_showpct_top | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIControlCenterHooks.kt |
| prefs_system.xml | system_cc_enable_style_switch | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/SystemUiResourceBootstrap.kt |
| prefs_system.xml | system_cc_switch_qsandnotification | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_qshaptics | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt |
| prefs_system.xml | system_qshaptics_ignore | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemAudioHooks.kt |
| prefs_system.xml | system_recents_disable_wallpaperscale | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_system.xml | system_recents_hide_statusbar | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_system.xml | system_recents_card_style | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/Launcher.kt |
| prefs_system.xml | system_removecleaner | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_system.xml | system_clearalltasks | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_hidefromrecents | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_system.xml | system_hidefromrecents_apps | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/Launcher.kt |
| prefs_system.xml | system_recents_blur | LAUNCHER | com.miui.home / app/src/main/java/tv/withaibuild/customiuizer/mods/Launcher.kt |
| prefs_system.xml | system_betterpopups_center | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_expandheadups | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt |
| prefs_system.xml | system_expandheadups_apps | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt |
| prefs_system.xml | system_betterpopups_autoclose_expanded | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_betterpopups_allowfloat | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_betterpopups_allowfloat_apps | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt |
| prefs_system.xml | system_betterpopups_disablewhenmute | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_betterpopups_nohide | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_betterpopups_delay | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemNotificationHooks.kt |
| prefs_system.xml | system_fw_noblacklist | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_fw_splitscreen | LAUNCHER | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/LauncherPostAttachFeatures.kt |
| prefs_system.xml | system_fw_forcein_actionsend | SYSTEMUI | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt |
| prefs_system.xml | system_fw_forcein_actionsend_in_whitelist | SYSTEMUI | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt |
| prefs_system.xml | system_fw_forcein_actionsend_apps | SYSTEMUI | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemWindowHooks.kt |
| prefs_system.xml | system_applock_scramblepin | SECURITY_CENTER | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SecurityCenterFeatures.kt |
| prefs_system.xml | system_applock | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_applock_timeout | SYSTEMUI | android / app/src/main/java/tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt |
| prefs_system.xml | system_applock_skip | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_hidelsstatusbar | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_hidelsclock | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_lockscreen_hidezenmode | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_lockscreen_disable_edit | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_hidelshint | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_nosos | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_lswallpaper | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_taptounlock | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_dttosleep | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_securelock | EXCLUDED_SYSTEM | android / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt |
| prefs_system.xml | system_scramblepin | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |
| prefs_system.xml | system_nopassword | SYSTEMUI | com.android.systemui / app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemUiFeatures.kt |

...（剩余行见 `feature-semantics/a14.json` 与源码）

## P3-A2 — Restart Mapping Semantic Corrective

### Base and gate history

```text
BASE SHA = 106b302bed5bbbf4c7bfb947dcce3a745bac48bb
FINAL SHA = 181ef379
REMOTE HEAD = 106b302bed5bbbf4c7bfb947dcce3a745bac48bb
PRODUCTION CHANGE = NO
P3_A1_GATE = HOLD
ROOT_CAUSE = HOST_OWNERSHIP_CONFLATED_WITH_RESTART_REQUIREMENT
```

### Corrective methodology

1. Reclassify by `VALUE_READ_MODE`: `CALLBACK_READ`, `OBSERVER_PUSH`, `INSTALL_TIME_GATE`, `INSTALL_TIME_CAPTURE`, `RESOURCE_INIT`, `APP_UI_ONLY`.
2. Parse all 16 `*Features.kt` files and build a master map from 223 `LazyFeatureSpec` entries.
3. Use `feature-semantics/a14.json` only as supporting evidence; resolve conflicts with actual source files.
4. Treat `*Hooks.kt` constructor / `init*` / `setup*` functions as install-time captures; lowercase callbacks as live reads.
5. Use page master host as a safe default for sub-options whose source evidence is missing (fail-open to `CALLBACK_READ` => `NONE`).
6. Record the host package from the value-read site, not the XML page or file name.

### Required counts

```text
TOTAL_FUNCTIONAL_PREFERENCES = 496
NONE = 234
LAUNCHER = 47
SYSTEMUI = 137
SECURITY_CENTER = 17
EXCLUDED_SYSTEM = 44
UNSUPPORTED_OTHER = 17
UNKNOWN = 0
COUNT_SUM_CHECK = PASS
CALLBACK_READ_COUNT = 196
OBSERVER_PUSH_COUNT = 38
INSTALL_TIME_GATE_COUNT = 212
INSTALL_TIME_CAPTURE_COUNT = 45
P3_A1_FALSE_POSITIVES = 21
```
```text
SUM = 234+47+137+17+44+17+0 = 496
```

### P3-A1 false-positive regression list

Found 21 false positives where P3-A1 assigned an executable target and P3-A2 assigns a non-executable one (or the host was misidentified).

| canonical key | old target | new target | value read mode | host package | why |
|---|---|---|---|---|---|
| controls_fsg_swipeandstop_disablevibrate | LAUNCHER | NONE | CALLBACK_READ | com.miui.home | preference is read in a callback, not at feature install |
| controls_volumedowndt_torch | SYSTEMUI | NONE | CALLBACK_READ | UNKNOWN | preference is read in a callback, not at feature install |
| controls_volumemedia_vibrate | SYSTEMUI | NONE | CALLBACK_READ | android | preference is read in a callback, not at feature install |
| controls_volumemedia_vibrate_ignore | SYSTEMUI | NONE | CALLBACK_READ | android | preference is read in a callback, not at feature install |
| launcher_folderspace | LAUNCHER | NONE | CALLBACK_READ | com.miui.home | preference is read in a callback, not at feature install |
| launcher_hideseekpoints_edit | LAUNCHER | NONE | CALLBACK_READ | com.miui.home | preference is read in a callback, not at feature install |
| system_albumartonlock_blur | SYSTEMUI | NONE | CALLBACK_READ | com.android.systemui | preference is read inside a live hook callback |
| system_albumartonlock_gray | SYSTEMUI | NONE | CALLBACK_READ | com.android.systemui | preference is read inside a live hook callback |
| system_albumartonlock_scale | SYSTEMUI | NONE | CALLBACK_READ | com.android.systemui | preference is read inside a live hook callback |
| system_allrotations2 | SYSTEMUI | EXCLUDED_SYSTEM | INSTALL_TIME_GATE | android | master is SystemServerFeatures / AndroidPackageFeatures (android) |
| system_applock_timeout | SYSTEMUI | EXCLUDED_SYSTEM | INSTALL_TIME_GATE | android | AppLockTimeoutFeature is a BaseSystemServerFeature; requires system-server restart, not SystemUI |
| system_cc_clock_verticaloffset | SYSTEMUI | NONE | CALLBACK_READ | com.android.systemui | value read is CALLBACK_READ, not install-time on host |
| system_charginginfo_current | SYSTEMUI | NONE | CALLBACK_READ | com.android.systemui | read on every callback in SystemLockScreenHooks.buildChargingInfoDetails |
| system_charginginfo_temp | SYSTEMUI | NONE | CALLBACK_READ | com.android.systemui | read on every callback in SystemLockScreenHooks.buildChargingInfoDetails |
| system_charginginfo_voltage | SYSTEMUI | NONE | CALLBACK_READ | com.android.systemui | read on every callback in SystemLockScreenHooks.buildChargingInfoDetails |
| system_charginginfo_wattage | SYSTEMUI | NONE | CALLBACK_READ | com.android.systemui | read on every callback in SystemLockScreenHooks.buildChargingInfoDetails |
| system_detailednetspeed_align | SYSTEMUI | NONE | CALLBACK_READ | com.android.systemui | preference is read inside a live hook callback |
| system_detailednetspeed_icon | SYSTEMUI | NONE | CALLBACK_READ | com.android.systemui | preference is read inside a live hook callback |
| system_detailednetspeed_low | SYSTEMUI | NONE | CALLBACK_READ | com.android.systemui | preference is read inside a live hook callback |
| system_detailednetspeed_lowlevel | SYSTEMUI | NONE | CALLBACK_READ | com.android.systemui | preference is read inside a live hook callback |
| system_detailednetspeed_secunit | SYSTEMUI | NONE | CALLBACK_READ | com.android.systemui | preference is read inside a live hook callback |

### P3-A2 special cases (required exact values)

```text
system_charginginfo_current = CALLBACK_READ / com.android.systemui / NONE
system_charginginfo_voltage = CALLBACK_READ / com.android.systemui / NONE
system_charginginfo_wattage = CALLBACK_READ / com.android.systemui / NONE
system_charginginfo_temp = CALLBACK_READ / com.android.systemui / NONE
system_applock_timeout = INSTALL_TIME_GATE / android / EXCLUDED_SYSTEM
system_usb_default_function = CALLBACK_READ / android / NONE
```

### Selected evidence table

| page | canonical key | value read mode | host package | restart target | evidence |
|---|---|---|---|---|---|
| prefs_controls.xml | controls_fsg_swipeandstop_disablevibrate | CALLBACK_READ | com.miui.home | NONE | CALLBACK_PREF_READ: callback in app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherGestureHooks.kt |
| prefs_controls.xml | controls_nonavbar | INSTALL_TIME_GATE | com.android.systemui,com.miui.home | SYSTEMUI | LAZY_FEATURE_MASTER: master in LauncherPostAttachFeatures.kt, SystemUiFeatures.kt |
| prefs_controls.xml | controls_volumedowndt_torch | CALLBACK_READ | UNKNOWN | NONE | CALLBACK_PREF_READ: callback in app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt |
| prefs_controls.xml | controls_volumemedia_vibrate | CALLBACK_READ | android | NONE | CALLBACK_PREF_READ: callback in app/src/main/java/tv/withaibuild/customiuizer/mods/GlobalActions.kt |
| prefs_controls.xml | controls_volumemedia_vibrate_ignore | CALLBACK_READ | android | NONE | CALLBACK_PREF_READ: callback in app/src/main/java/tv/withaibuild/customiuizer/mods/GlobalActions.kt |
| prefs_launcher.xml | launcher_folderspace | CALLBACK_READ | com.miui.home | NONE | CALLBACK_PREF_READ: callback in app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherFolderHooks.kt |
| prefs_launcher.xml | launcher_hideseekpoints_edit | CALLBACK_READ | com.miui.home | NONE | CALLBACK_PREF_READ: callback in app/src/main/java/tv/withaibuild/customiuizer/mods/LauncherLayoutHooks.kt |
| prefs_system_albumartonlock.xml | system_albumartonlock_blur | CALLBACK_READ | com.android.systemui | NONE | CALLBACK_PREF_READ: callback in app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt |
| prefs_system_albumartonlock.xml | system_albumartonlock_gray | CALLBACK_READ | com.android.systemui | NONE | CALLBACK_PREF_READ: callback in app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt |
| prefs_system_albumartonlock.xml | system_albumartonlock_scale | CALLBACK_READ | com.android.systemui | NONE | CALLBACK_PREF_READ: callback in app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUILockScreenHooks.kt |
| prefs_system.xml | system_allrotations2 | INSTALL_TIME_GATE | android | EXCLUDED_SYSTEM | LAZY_FEATURE_MASTER: master in AndroidPackageFeatures.kt, SystemServerFeatures.kt |
| prefs_system.xml | system_applock_timeout | INSTALL_TIME_GATE | android | EXCLUDED_SYSTEM | LAZY_FEATURE_MASTER: SystemServerFeatures AppLockTimeoutFeature |
| prefs_system_controlcenter_clock.xml | system_cc_clock_fontsize | INSTALL_TIME_CAPTURE | com.android.systemui | SYSTEMUI | HOOK_INITIALIZER:  |
| prefs_system_controlcenter_clock.xml | system_cc_clock_verticaloffset | CALLBACK_READ | com.android.systemui | NONE | CALLBACK_PREF_READ: callback in app/src/main/java/tv/withaibuild/customiuizer/mods/SystemClockHooks.kt |
| prefs_system_charginginfo.xml | system_charginginfo_current | CALLBACK_READ | com.android.systemui | NONE | CALLBACK_PREF_READ: buildChargingInfoDetails |
| prefs_system_charginginfo.xml | system_charginginfo_temp | CALLBACK_READ | com.android.systemui | NONE | CALLBACK_PREF_READ: buildChargingInfoDetails |
| prefs_system_charginginfo.xml | system_charginginfo_voltage | CALLBACK_READ | com.android.systemui | NONE | CALLBACK_PREF_READ: buildChargingInfoDetails |
| prefs_system_charginginfo.xml | system_charginginfo_wattage | CALLBACK_READ | com.android.systemui | NONE | CALLBACK_PREF_READ: buildChargingInfoDetails |
| prefs_system_detailednetspeed.xml | system_detailednetspeed_align | CALLBACK_READ | com.android.systemui | NONE | CALLBACK_PREF_READ: callback in app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt |
| prefs_system_detailednetspeed.xml | system_detailednetspeed_icon | CALLBACK_READ | com.android.systemui | NONE | CALLBACK_PREF_READ: callback in app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt |
| prefs_system_detailednetspeed.xml | system_detailednetspeed_low | CALLBACK_READ | com.android.systemui | NONE | CALLBACK_PREF_READ: callback in app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt |
| prefs_system_detailednetspeed.xml | system_detailednetspeed_lowlevel | CALLBACK_READ | com.android.systemui | NONE | CALLBACK_PREF_READ: callback in app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt |
| prefs_system_detailednetspeed.xml | system_detailednetspeed_secunit | CALLBACK_READ | com.android.systemui | NONE | CALLBACK_PREF_READ: callback in app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt |
| prefs_system.xml | system_statusbarcolor | INSTALL_TIME_GATE | __GENERIC__ | UNSUPPORTED_OTHER | LAZY_FEATURE_MASTER: master in GenericAppFeatures.kt |
| prefs_system.xml | system_usb_default_function | CALLBACK_READ | android | NONE | CALLBACK_PREF_READ: UsbDefaultFunctionFeature enabled={true}; value read on USB default-application event |

### Page resolution

```text
PAGE_RESOLUTION_COMPLETE = 19
PAGE_RESOLUTION_PARTIAL_SAFE = 8
PAGE_RESOLUTION_BLOCKED = 0
```

| page | executable targets | all targets | confidence |
|---|---|---|---|
| prefs_controls.xml | LAUNCHER, SYSTEMUI | EXCLUDED_SYSTEM, LAUNCHER, NONE, SYSTEMUI, UNSUPPORTED_OTHER | PARTIAL_SAFE |
| prefs_launcher.xml | LAUNCHER, SECURITY_CENTER | LAUNCHER, NONE, SECURITY_CENTER | COMPLETE |
| prefs_system.xml | LAUNCHER, SECURITY_CENTER, SYSTEMUI | EXCLUDED_SYSTEM, LAUNCHER, NONE, SECURITY_CENTER, SYSTEMUI, UNSUPPORTED_OTHER | PARTIAL_SAFE |
| prefs_system_alarmonlock.xml | SYSTEMUI | NONE, SYSTEMUI | COMPLETE |
| prefs_system_albumartonlock.xml | SYSTEMUI | NONE, SYSTEMUI | COMPLETE |
| prefs_system_autobrightness.xml |  | EXCLUDED_SYSTEM, NONE | PARTIAL_SAFE |
| prefs_system_batteryindicator.xml | SYSTEMUI | NONE, SYSTEMUI | COMPLETE |
| prefs_system_charginginfo.xml | SYSTEMUI | NONE, SYSTEMUI | COMPLETE |
| prefs_system_controlcenter_clock.xml | SYSTEMUI | NONE, SYSTEMUI | COMPLETE |
| prefs_system_controlcenter_themestyle.xml | SYSTEMUI | SYSTEMUI | COMPLETE |
| prefs_system_detailednetspeed.xml | SYSTEMUI | NONE, SYSTEMUI | COMPLETE |
| prefs_system_hideicons.xml | SYSTEMUI | EXCLUDED_SYSTEM, NONE, SYSTEMUI | PARTIAL_SAFE |
| prefs_system_lockscreenshortcuts.xml | SYSTEMUI | NONE, SYSTEMUI | COMPLETE |
| prefs_system_noscreenlock.xml | SYSTEMUI | NONE, SYSTEMUI | COMPLETE |
| prefs_system_screenshot.xml |  | NONE, UNSUPPORTED_OTHER | PARTIAL_SAFE |
| prefs_system_secureqs.xml | SYSTEMUI | NONE, SYSTEMUI | COMPLETE |
| prefs_system_statusbar_batterystyle.xml | SYSTEMUI | NONE, SYSTEMUI | COMPLETE |
| prefs_system_statusbar_batterytempandcurrent.xml | SYSTEMUI | NONE, SYSTEMUI | COMPLETE |
| prefs_system_statusbar_clock.xml | SYSTEMUI | NONE, SYSTEMUI | COMPLETE |
| prefs_system_statusbar_mobilesignal.xml | SYSTEMUI | NONE, SYSTEMUI | COMPLETE |
| prefs_system_statusbar_righticons.xml | SYSTEMUI | NONE, SYSTEMUI | COMPLETE |
| prefs_system_statusbar_showdevicetemperature.xml | SYSTEMUI | NONE, SYSTEMUI | COMPLETE |
| prefs_system_statusbarcontrols.xml | SYSTEMUI | NONE, SYSTEMUI | COMPLETE |
| prefs_system_vibration_amp.xml |  | EXCLUDED_SYSTEM, NONE | PARTIAL_SAFE |
| prefs_system_visualizer.xml | SYSTEMUI | NONE, SYSTEMUI | COMPLETE |
| prefs_various.xml | SECURITY_CENTER | EXCLUDED_SYSTEM, NONE, SECURITY_CENTER, UNSUPPORTED_OTHER | PARTIAL_SAFE |
| prefs_various_calluibright.xml |  | NONE, UNSUPPORTED_OTHER | PARTIAL_SAFE |

### P3-B safety gate

```text
P3_B_SAFE_TO_IMPLEMENT = NO
```

- `UNKNOWN` target count is now `0` and no page is `BLOCKED`.
- However, a meaningful set of `SYSTEMUI` / `LAUNCHER` mappings still relies on `MANUAL_SOURCE_REVIEW` or `PAGE_MASTER_DEFAULT` heuristics.
- P3-B implementation is therefore NOT authorized in this task; it requires a second pass to confirm every executable mapping against its source function.

### P3-B matched-restart design constraints (future work)

1. Direct-only: inspect settings directly contained by the current page; no recursive subtree.
2. Exclude category keys, navigation-only `PreferenceEx`, hidden/unsupported entries, app blacklists that only affect future attach.
3. Union executable targets in `{NONE, LAUNCHER, SYSTEMUI, SECURITY_CENTER}`.
4. Do NOT execute `EXCLUDED_SYSTEM`, `UNSUPPORTED_OTHER`, or `UNKNOWN` targets.
5. Do NOT include live settings, system reboot, soft reboot, or system-server reboot in the automatic set.
6. Present one `重启相关组件` action per page; aggregate results; fail closed.

### Verification commands

- `fast --changed`
- `diff --check`
- `git status`

### Source of truth for full mapping

The machine-readable full per-preference mapping is available at:

```text
C:\Users\tv\AppData\Local\Temp\restart_lifecycle_mapping.json
```

This file is a temporary audit artifact and is not part of the repository.

## P3-A3: Executable restart target audit

### Base and gate history

- Repository: `tv.withaibuild.customiuizer.r14` A14 tree
- Base SHA: `548bebe211e082c87051786a71045c632ea46c2e`
- Audit scope: all `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/*Features.kt`
- XML scope: all `app/src/main/res/xml/prefs_*.xml` functional widgets only
- Semantics source: `feature-semantics/a14.json`

P3-A2 under-counted multi-host master keys because it looked only at each feature's declared
`preferenceKey`. P3-A3 now resolves every `LazyFeatureSpec.enabled` expression to its underlying
`evaluateEnabled` / `isEnabledCondition`, extracts all `prefs.getBoolean/getInt/getString/getStringAsInt`
string literal keys, and follows one level of qualified helper calls such as
`SystemUIControlCenterHooks.hasControlCenterModifications()` and `StatusBarHeightConfig.isEnabled(prefs)`.

### Corrective methodology

1. **Feature parsing**: every `LazyFeatureSpec(...)` block is extracted from `*Features.kt` to obtain
   `preferenceKey`, `target`, `phase`, and the `enabled` lambda.
2. **Enabled resolution**: the lambda body (e.g. `ClassName.evaluateEnabled(prefs)`) is resolved to the
   matching `companion object fun evaluateEnabled` or `override fun isEnabledCondition`. Only qualified
   `ClassName.methodName(...)` calls are followed one level; unqualified function calls are ignored.
3. **Target mapping**:
   - `FeatureTarget.SYSTEM_UI` -> `SYSTEMUI`
   - `FeatureTarget.LAUNCHER` -> `LAUNCHER`
   - `FeatureTarget.SYSTEM_PACKAGE` -> `SECURITYCENTER` only when the source file is
     `SecurityCenterFeatures.kt` (the enum is overloaded across package-ready installers; other
     package-ready hosts such as PackageInstaller/Phone/InputMethod/Media are treated as
     `UNSUPPORTED_OTHER`).
4. **Key aggregation**: for every feature, the declared `preferenceKey` plus every key read in its
   enabled condition contributes the feature's restart target. A canonical key may therefore map to
   multiple targets.
5. **XML functional keys**: only `CheckBoxPreferenceEx`, `SwitchPreferenceEx`, `ListPreferenceEx`,
   `EditTextPreferenceEx`, `ColorPreferenceEx`, `MultiSelectListPreferenceEx`, and `SeekBarPreference`
   are collected. `PreferenceEx` and `PreferenceCategoryEx` are ignored. The `pref_key_` prefix is
   stripped to match feature keys.
6. **Non-executable classification**: for XML keys without a feature-derived executable target, the
   `feature-semantics/a14.json` entry is used. Hardcoded overrides for the charging-info current/
   voltage/wattage/temp, `system_applock_timeout`, and `system_usb_default_function` are applied.
   Missing evidence is never defaulted to executable; it is marked `UNKNOWN_EVIDENCE` or
   `NO_EXECUTABLE_MAPPING`.

### Required counts

| Field | Value |
|-------|-------|
| TOTAL_FUNCTIONAL_PREFERENCES | 514 |
| EXECUTABLE_UNIQUE_KEYS | 193 |
| LAUNCHER_TARGET_REFERENCES | 61 |
| SYSTEMUI_TARGET_REFERENCES | 119 |
| SECURITYCENTER_TARGET_REFERENCES | 16 |
| MULTI_EXECUTABLE_TARGET_KEYS | 3 |
| NO_EXECUTABLE_MAPPING | 115 |
| UNKNOWN_EVIDENCE | 0 |
| P3_B_SAFE_TO_IMPLEMENT | NO |

### MULTI_HOST_MASTER_KEYS (3)

- `controls_fsg_assist_left_action` -> targets ['LAUNCHER', 'SYSTEMUI']
  - LauncherPostAttachFeatures.kt / Launcher Assist Gesture Action (FeatureTarget.LAUNCHER -> LAUNCHER)
  - SystemUiFeatures.kt / Assist Gesture Action (FeatureTarget.SYSTEM_UI -> SYSTEMUI)
- `controls_fsg_assist_right_action` -> targets ['LAUNCHER', 'SYSTEMUI']
  - LauncherPostAttachFeatures.kt / Launcher Assist Gesture Action (FeatureTarget.LAUNCHER -> LAUNCHER)
  - SystemUiFeatures.kt / Assist Gesture Action (FeatureTarget.SYSTEM_UI -> SYSTEMUI)
- `controls_nonavbar` -> targets ['LAUNCHER', 'SYSTEMUI']
  - InputMethodFeatures.kt / Input Method Fix Bottom Margin (FeatureTarget.ANY)
  - LauncherPostAttachFeatures.kt / Launcher Hide Nav Bar (FeatureTarget.LAUNCHER -> LAUNCHER)
  - SystemUiFeatures.kt / Hide Nav Bar (FeatureTarget.SYSTEM_UI -> SYSTEMUI)
  - SystemUiFeatures.kt / Hide Nav Bar Before Screenshot (FeatureTarget.SYSTEM_UI -> SYSTEMUI)

### EXECUTABLE_LAUNCHER_KEYS

`controls_fsg_assist_left_action`, `controls_fsg_assist_right_action`, `controls_fsg_coverage`
`controls_fsg_horiz`, `controls_fsg_swipeandstop_action`, `controls_fsg_width`
`controls_nonavbar`, `launcher_closedrawer`, `launcher_closefolders`, `launcher_darkershadow`
`launcher_disable_log`, `launcher_disable_wallpaperscale`, `launcher_dock_bottommargin`
`launcher_dock_height`, `launcher_dock_topmargin`, `launcher_docktitles`
`launcher_doubletap_action`, `launcher_fixanim`, `launcher_fixlaunch`, `launcher_folder_cols`
`launcher_folderblur_disable`, `launcher_folderblur_opacity`, `launcher_hideseekpoints`
`launcher_hidetitles`, `launcher_horizmargin`, `launcher_horizwidgetmargin`, `launcher_iconscale`
`launcher_indicator_topmargin`, `launcher_indicatorheight`, `launcher_infinitescroll`
`launcher_noclockhide`, `launcher_nounlockanim`, `launcher_nowidgetonly`, `launcher_nozoomanim`
`launcher_oldlaunchanim`, `launcher_pinch_action`, `launcher_privacyapps_gest`
`launcher_renameapps`, `launcher_sensorportrait`, `launcher_shake_action`
`launcher_spread_action`, `launcher_swipedown2_action`, `launcher_swipedown_action`
`launcher_swipeleft_action`, `launcher_swiperight_action`, `launcher_swipeup2_action`
`launcher_swipeup_action`, `launcher_titlefontsize`, `launcher_titletopmargin`
`launcher_topmargin`, `launcher_unlockgrids`, `launcher_unlockhotseat`
`launcher_wallpaper_colormode`, `system_fw_splitscreen`, `system_hidefromrecents`
`system_recents_blur`, `system_recents_card_style`, `system_recents_disable_wallpaperscale`
`system_recents_hide_statusbar`, `system_removecleaner`, `system_resizablewidgets`

### EXECUTABLE_SYSTEMUI_KEYS

`controls_fsg_assist_left_action`, `controls_fsg_assist_right_action`
`controls_hidenavbar_whenscreenshot`, `controls_navbarleft_action`, `controls_nonavbar`
`controls_volumecursor`, `system_4gtolte`, `system_albumartonlock`, `system_allownotiffloat`
`system_allownotifonkeyguard`, `system_batteryindicator`, `system_betterpopups_allowfloat`
`system_betterpopups_autoclose_expanded`, `system_betterpopups_center`
`system_betterpopups_delay`, `system_betterpopups_disablewhenmute`, `system_betterpopups_nohide`
`system_cc_btandtorch_ascard`, `system_cc_card_enabled_color`, `system_cc_clock_centeralign`
`system_cc_clocktweak`, `system_cc_collapse_after_clicked`, `system_cc_floatingtimetile`
`system_cc_fpstile`, `system_cc_freeform_when_longclick`, `system_cc_hide_edit`
`system_cc_hide_profile_monitoring`, `system_cc_hideoperator_delimiter`
`system_cc_show_stepcount`, `system_cc_slider_color_enable`, `system_cc_switch_qsandnotification`
`system_cc_tile_enabled_color`, `system_cc_tile_roundedrect`, `system_cc_volume_showpct`
`system_ccgridcolumns`, `system_chargeanimtime`, `system_charginginfo`, `system_colorizenotifs`
`system_detailednetspeed_style`, `system_disableanynotif`, `system_drawer_blur`
`system_drawer_remove_emptynotify`, `system_drawer_removeshortcut`, `system_dttosleep`
`system_epm`, `system_expandheadups`, `system_expandnotifs`, `system_fivegtile`
`system_fw_noblacklist`, `system_hidelsclock`, `system_hidelshint`, `system_hidelsstatusbar`
`system_hidestatusbar_whenscreenshot`, `system_lockscreen_disable_edit`
`system_lockscreen_hidezenmode`, `system_lockscreenshortcuts`, `system_ls_force_systemfonts`
`system_lsalarm`, `system_maxsbicons`, `system_minimalnotifview`, `system_mobiletypeicon`
`system_morenotif`, `system_mutevisiblenotif`, `system_netspeedinterval`
`system_networkindicator_wifi`, `system_nolightuponcharges`, `system_nopassword`
`system_nosafevolume`, `system_noscreenlock_act`, `system_nosilentvibrate`, `system_nosos`
`system_notif_disable_fold`, `system_notifafterunlock`, `system_notifchannelsettings`
`system_notifimportance`, `system_notifrowmenu`, `system_notify_openinfw`
`system_qs_disable_fakeclock_anim`, `system_qs_force_systemfonts`, `system_qs_hideoperator`
`system_qshaptics`, `system_removedismiss`, `system_scramblepin`, `system_screenshot_overlay`
`system_secureqs`, `system_shortcut_app`, `system_showpct`, `system_statusbar_alarm_atright`
`system_statusbar_batterystyle`, `system_statusbar_batterytempandcurrent`
`system_statusbar_clock_position`, `system_statusbar_clocktweak`, `system_statusbar_dualrows`
`system_statusbar_dualsimin2rows`, `system_statusbar_horizmargin`
`system_statusbar_mobile_digital_signal`, `system_statusbar_mobiletype_single`
`system_statusbar_topmargin`, `system_statusbarcontrols`, `system_statusbaricons_alarm`
`system_statusbaricons_battery1`, `system_statusbaricons_battery3`
`system_statusbaricons_privacy`, `system_statusbaricons_privacy_prompt`
`system_statusbaricons_signal`, `system_statusbaricons_vowifi`, `system_statusbaricons_wifi`
`system_statusbaricons_wifistandard`, `system_strong_toast_mode`, `system_taptounlock`
`system_visualizer`, `system_volume_mode_button_colors`, `system_volumebar_blur_mtk`
`system_volumeblur_collapsed`, `system_volumeblur_expanded`, `system_volumedialogdelay_collapsed`
`system_volumedialogdelay_expanded`, `system_volumetimer`, `various_showcallui`

### EXECUTABLE_SECURITYCENTER_KEYS

`system_applock_scramblepin`, `system_hidelowbatwarn`, `various_appdetails`, `various_appsort`, `various_disable_dock_suggest`, `various_disable_freeform_suggest_blacklist`, `various_disable_reset_recents_privacy_blur`, `various_disableapp`, `various_enable_expand_sidebar`, `various_hide_report_ondetails`, `various_privacyapps_column_nums4`, `various_replace_defaultopen_with_openbydefault`, `various_restrictapp`, `various_show_battery_temperature`, `various_skip_interceptperm`, `various_skip_securityscan`

### P3_A2_FALSE_NEGATIVE_MULTI_TARGET

- `controls_nonavbar`
  - old set: `['SYSTEMUI']`
  - correct set: `['LAUNCHER', 'SYSTEMUI']`
  - why: P3-A2 only used the master preferenceKey of HideNavBarFeature in SystemUiFeatures.kt (target SYSTEM_UI). P3-A3 resolves the enabled conditions of every LazyFeatureSpec and finds LauncherHideNavBarFeature in LauncherPostAttachFeatures.kt also gating controls_nonavbar (target LAUNCHER), plus HideNavBarBeforeScreenshotFeature reading it.

### P3-A2 gate status (carried forward)

```text
P3_A2_SHA = 548bebe211e082c87051786a71045c632ea46c2e
P3_A2_GATE = HOLD
P3_A2_CALLBACK_READ_CORRECTIVE = PASS
P3_A2_SYSTEM_SERVER_CORRECTIVE = PASS
```

P3-A3 blockers resolved:

```text
P3_A2_BLOCKER_1 = MISSING_EVIDENCE_DEFAULTED_TO_NONE
P3_A2_BLOCKER_2 = SINGLE_TARGET_MODEL_LOSES_MULTI_HOST_REQUIREMENTS
```

### P3-A3 model decisions

```text
PAGE_MASTER_FALLBACK = REMOVED
MISSING_EVIDENCE_DEFAULT = NO_EXECUTABLE_MAPPING
RUNTIME_REGISTRY_MODEL = KEY_TO_SET
CONTROLS_NONAVBAR_TARGETS = {LAUNCHER, SYSTEMUI}
EXECUTABLE_MAPPING_EVIDENCE = POSITIVE_ONLY
```

`PAGE_MASTER_FALLBACK` is **REMOVED**: no XML page, category, key prefix, source filename, page master, or same-page sibling is used as restart evidence. Missing direct value-read or install evidence is recorded as `NO_EXECUTABLE_MAPPING` or `UNKNOWN_EVIDENCE`.

`MISSING_EVIDENCE_DEFAULT` is `NO_EXECUTABLE_MAPPING`, not `NONE`. A runtime registry lookup miss returns an empty `Set<RestartTarget>` (fail-closed).

`RUNTIME_REGISTRY_MODEL` is `KEY_TO_SET`: `PreferenceRestartTargetRegistry.targetsFor(canonicalKey)` returns `Set<RestartTarget>`.

`EXECUTABLE_MAPPING_EVIDENCE` is `POSITIVE_ONLY`:

- Each `LazyFeatureSpec` `preferenceKey` maps to its declared `FeatureTarget` when the target is one of `SYSTEM_UI`, `LAUNCHER`, or `SecurityCenterFeatures.SYSTEM_PACKAGE`.
- Each key read inside the resolved `evaluateEnabled` / `isEnabledCondition` of a feature contributes that feature's target (dependency-derived install gate).
- `controls_nonavbar` is the canonical multi-host example: it independently gates `HideNavBarFeature` (SystemUI), `LauncherHideNavBarFeature` (Launcher), and is a dependency for `HideNavBarBeforeScreenshotFeature` (SystemUI) and `InputMethodFixBottomMarginFeature` (unsupported ANY).
- Hardcoded overrides preserve P3-A2-confirmed cases: charging-info sub-keys (`NONE_LIVE`), `system_applock_timeout` (`EXCLUDED_SYSTEM`), `system_usb_default_function` (`NONE_LIVE`).

### P3-A2 false-negative multi-target regression

```text
P3_A2_FALSE_NEGATIVE_MULTI_TARGET = controls_nonavbar
OLD = {SYSTEMUI}
CORRECT = {LAUNCHER, SYSTEMUI}
WHY = same canonical preference independently gates LauncherHideNavBarFeature (target LAUNCHER) and HideNavBarFeature (target SYSTEM_UI); it is also a dependency for HideNavBarBeforeScreenshotFeature (target SYSTEM_UI)
```

### P3-B safety gate

```text
P3_B_SAFE_TO_IMPLEMENT = NO
```

- The executable positive allowlist is now `Set<RestartTarget>` based, with multi-host masters represented correctly.
- `UNKNOWN_EVIDENCE = 0`. `NO_EXECUTABLE_MAPPING = 115` keys are fail-closed at runtime; the P3-B registry returns an empty set for them.
- However, P3-B implementation is **not authorized** in this task (`P3_B_AUTHORIZATION = NO`). This document supplies the pre-flight registry only.

### P3-B matched-restart design constraints (future work)

1. Direct-only: inspect only settings directly contained by the current page.
2. Exclude `PreferenceCategory`, navigation-only `PreferenceEx`, hidden/unsupported, non-interactive, and app-blacklist entries.
3. `PreferenceRestartTargetRegistry.targetsFor(key)` returns a prebuilt `Set<RestartTarget>`.
4. The page action takes the union of executable targets from direct settings; never executes `EXCLUDED_SYSTEM`, `UNSUPPORTED_OTHER`, or `UNKNOWN_EVIDENCE` targets.
5. Do not include system reboot, soft reboot, or system-server reboot in the automatic set.
6. Present one "重启相关组件" action per page; aggregate results; fail closed (no executable target = no action or disabled action).
7. Registry miss returns `emptySet()`, so missing evidence cannot accidentally restart a process.

### Non-executable reason breakdown

| reason | count |
|---|---|
| NONE_LIVE | 43 |
| APP_UI_ONLY | 83 |
| EXCLUDED_SYSTEM | 69 |
| UNSUPPORTED_OTHER | 27 |
| NO_EXECUTABLE_MAPPING | 115 |
| UNKNOWN_EVIDENCE | 0 |

`NO_EXECUTABLE_MAPPING` means `feature-semantics/a14.json` points to a package or source that could be executable, but no `*Features.kt` `LazyFeatureSpec` or `FeatureDefinition` installs a feature gated by this key. The P3-B registry will fail-closed for these keys.

### Verification commands

```text
python tools/verify.py fast --changed
git diff --check
git status --short
```

### Source of truth for full mapping

The machine-readable full mapping is at:

```text
C:\Users\tv\AppData\Local\Temp\p3a3_registry.json
```

This file is a temporary audit artifact and is not part of the repository.

## P3-A4: Executable restart target audit

### Base and gate history

- Repository: `tv.withaibuild.customiuizer.r14` A14 tree
- Base SHA: `e88ff4ffca65d408e711f82bddfb8a6d552a79ca`
- Audit scope: all `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/*Features.kt`
- XML scope: all `app/src/main/res/xml/prefs_*.xml` functional widgets only
- Semantics source: `feature-semantics/a14.json`

P3-A4 builds on P3-A3. It still resolves every `LazyFeatureSpec.enabled` expression, but now it also
follows `ClassName.evaluateEnabled` / `isEnabledCondition` to detect **always-installed** features.
A feature whose resolved enabled body is just `true` does not use its declared `preferenceKey` as an
install gate. This removes `system_strong_toast_mode` from the executable SystemUI mapping while keeping
strong-toast runtime hooks live through `StrongToastRuntimeState`. `system_usb_default_function` is also
correctly recognized as runtime-handled (not a live install gate), and the SystemServer bridge features
(`AnimationScaleBridge`, `UpdaterServicesBridge`) are recorded as unconditional runtime passthroughs.

### Always-installed features

- `Animation Scale Bridge` (SystemServerFeatures.kt)
  - preferenceKey: `(none)`
  - FeatureTarget: `FeatureTarget.SYSTEM_SERVER`
  - why: `AnimationScaleBridgeFeature.isEnabledCondition = true`
  - runtime value path: `{ AnimationScaleBridgeFeature(lpparam) }`
  - executable target before: `(none)`
  - executable target after: `set()`
- `Updater Services Bridge` (SystemServerFeatures.kt)
  - preferenceKey: `(none)`
  - FeatureTarget: `FeatureTarget.SYSTEM_SERVER`
  - why: `UpdaterServicesBridgeFeature.isEnabledCondition = true`
  - runtime value path: `{ UpdaterServicesBridgeFeature(lpparam) }`
  - executable target before: `(none)`
  - executable target after: `set()`
- `Disable Window Blurs` (SystemServerFeatures.kt)
  - preferenceKey: `system_disable_window_blurs`
  - FeatureTarget: `FeatureTarget.SYSTEM_SERVER`
  - why: `DisableWindowBlursFeature.evaluateEnabled = true`
  - runtime value path: `SystemDisplayHooks.DisableWindowBlursHook(lpparam)`
  - executable target before: `(none)`
  - executable target after: `set()`
- `USB Default Function` (SystemServerFeatures.kt)
  - preferenceKey: `system_usb_default_function`
  - FeatureTarget: `FeatureTarget.SYSTEM_SERVER`
  - why: `UsbDefaultFunctionFeature.isEnabledCondition = true`
  - runtime value path: `SystemUsbDefaultHooks runtime handler`
  - executable target before: `(none)`
  - executable target after: `set()`
- `Strong Toast Presentation` (SystemUiFeatures.kt)
  - preferenceKey: `system_strong_toast_mode`
  - FeatureTarget: `FeatureTarget.SYSTEM_UI`
  - why: `StrongToastPresentationFeature.evaluateEnabled = true`
  - runtime value path: `StrongToastRuntimeState observer / passthrough`
  - executable target before: `{'SYSTEMUI'}`
  - executable target after: `set()`

### Corrective methodology

1. **Feature parsing**: every `LazyFeatureSpec(...)` block is extracted from `*Features.kt` to obtain
   `preferenceKey`, `target`, `phase`, `enabled` lambda, and `factory`.
2. **Always-installed detection**: the `enabled` lambda is resolved. If it is literally `{ true }`, or if
   it calls `ClassName.evaluateEnabled` / `isEnabledCondition` whose resolved body is just `true`, the
   feature is recorded as always-installed and its `preferenceKey` is **not** used as an executable
   install gate.
3. **Target mapping**:
   - `FeatureTarget.SYSTEM_UI` -> `SYSTEMUI`
   - `FeatureTarget.LAUNCHER` -> `LAUNCHER`
   - `FeatureTarget.SYSTEM_PACKAGE` -> `SECURITYCENTER` only when the source file is
     `SecurityCenterFeatures.kt` (the enum is overloaded across package-ready installers; other
     package-ready hosts such as PackageInstaller/Phone/InputMethod/Media are treated as
     `UNSUPPORTED_OTHER`).
4. **Key aggregation**: for every non-always-installed feature, the declared `preferenceKey` plus every
   key read in its enabled condition contributes the feature's restart target. A canonical key may
   therefore map to multiple targets.
5. **XML functional keys**: only `CheckBoxPreferenceEx`, `SwitchPreferenceEx`, `ListPreferenceEx`,
   `EditTextPreferenceEx`, `ColorPreferenceEx`, `MultiSelectListPreferenceEx`, and `SeekBarPreference`
   are collected. `PreferenceEx` and `PreferenceCategoryEx` are ignored. The `pref_key_` prefix is
   stripped to match feature keys.
6. **Non-executable classification**: for XML keys without a feature-derived executable target, the
   `feature-semantics/a14.json` entry is used. Hardcoded overrides for the charging-info current/
   voltage/wattage/temp, `system_applock_timeout`, `system_usb_default_function`, and the strong-toast
   mode/position/bottom-offset keys are applied. Missing evidence is never defaulted to executable; it is
   marked `UNKNOWN_EVIDENCE` or `NO_EXECUTABLE_MAPPING`.

### Required counts

| Field | Value |
|-------|-------|
| TOTAL_FUNCTIONAL_PREFERENCES | 514 |
| EXECUTABLE_UNIQUE_KEYS | 192 |
| EXECUTABLE_LAUNCHER_UNIQUE_KEYS | 61 |
| EXECUTABLE_SYSTEMUI_UNIQUE_KEYS | 118 |
| EXECUTABLE_SECURITYCENTER_UNIQUE_KEYS | 16 |
| LAUNCHER_TARGET_REFERENCES | 61 |
| SYSTEMUI_TARGET_REFERENCES | 118 |
| SECURITYCENTER_TARGET_REFERENCES | 16 |
| MULTI_EXECUTABLE_TARGET_KEYS | 3 (['controls_fsg_assist_left_action', 'controls_fsg_assist_right_action', 'controls_nonavbar']) |
| NO_EXECUTABLE_MAPPING | 113 |
| UNKNOWN_EVIDENCE | 0 |
| COUNT_CONSISTENCY | PASS |
| P3_B_SAFE_TO_IMPLEMENT | YES |

### MULTI_EXECUTABLE_TARGET_KEYS (3)

- `controls_fsg_assist_left_action` -> targets ['LAUNCHER', 'SYSTEMUI']
- `controls_fsg_assist_right_action` -> targets ['LAUNCHER', 'SYSTEMUI']
- `controls_nonavbar` -> targets ['LAUNCHER', 'SYSTEMUI']


### EXECUTABLE_LAUNCHER_KEYS

`controls_fsg_assist_left_action`, `controls_fsg_assist_right_action`, `controls_fsg_coverage`
`controls_fsg_horiz`, `controls_fsg_swipeandstop_action`, `controls_fsg_width`
`controls_nonavbar`, `launcher_closedrawer`, `launcher_closefolders`, `launcher_darkershadow`
`launcher_disable_log`, `launcher_disable_wallpaperscale`, `launcher_dock_bottommargin`
`launcher_dock_height`, `launcher_dock_topmargin`, `launcher_docktitles`
`launcher_doubletap_action`, `launcher_fixanim`, `launcher_fixlaunch`, `launcher_folder_cols`
`launcher_folderblur_disable`, `launcher_folderblur_opacity`, `launcher_hideseekpoints`
`launcher_hidetitles`, `launcher_horizmargin`, `launcher_horizwidgetmargin`, `launcher_iconscale`
`launcher_indicator_topmargin`, `launcher_indicatorheight`, `launcher_infinitescroll`
`launcher_noclockhide`, `launcher_nounlockanim`, `launcher_nowidgetonly`, `launcher_nozoomanim`
`launcher_oldlaunchanim`, `launcher_pinch_action`, `launcher_privacyapps_gest`
`launcher_renameapps`, `launcher_sensorportrait`, `launcher_shake_action`
`launcher_spread_action`, `launcher_swipedown2_action`, `launcher_swipedown_action`
`launcher_swipeleft_action`, `launcher_swiperight_action`, `launcher_swipeup2_action`
`launcher_swipeup_action`, `launcher_titlefontsize`, `launcher_titletopmargin`
`launcher_topmargin`, `launcher_unlockgrids`, `launcher_unlockhotseat`
`launcher_wallpaper_colormode`, `system_fw_splitscreen`, `system_hidefromrecents`
`system_recents_blur`, `system_recents_card_style`, `system_recents_disable_wallpaperscale`
`system_recents_hide_statusbar`, `system_removecleaner`, `system_resizablewidgets`

### EXECUTABLE_SYSTEMUI_KEYS

`controls_fsg_assist_left_action`, `controls_fsg_assist_right_action`
`controls_hidenavbar_whenscreenshot`, `controls_navbarleft_action`, `controls_nonavbar`
`controls_volumecursor`, `system_4gtolte`, `system_albumartonlock`, `system_allownotiffloat`
`system_allownotifonkeyguard`, `system_batteryindicator`, `system_betterpopups_allowfloat`
`system_betterpopups_autoclose_expanded`, `system_betterpopups_center`
`system_betterpopups_delay`, `system_betterpopups_disablewhenmute`, `system_betterpopups_nohide`
`system_cc_btandtorch_ascard`, `system_cc_card_enabled_color`, `system_cc_clock_centeralign`
`system_cc_clocktweak`, `system_cc_collapse_after_clicked`, `system_cc_floatingtimetile`
`system_cc_fpstile`, `system_cc_freeform_when_longclick`, `system_cc_hide_edit`
`system_cc_hide_profile_monitoring`, `system_cc_hideoperator_delimiter`
`system_cc_show_stepcount`, `system_cc_slider_color_enable`, `system_cc_switch_qsandnotification`
`system_cc_tile_enabled_color`, `system_cc_tile_roundedrect`, `system_cc_volume_showpct`
`system_ccgridcolumns`, `system_chargeanimtime`, `system_charginginfo`, `system_colorizenotifs`
`system_detailednetspeed_style`, `system_disableanynotif`, `system_drawer_blur`
`system_drawer_remove_emptynotify`, `system_drawer_removeshortcut`, `system_dttosleep`
`system_epm`, `system_expandheadups`, `system_expandnotifs`, `system_fivegtile`
`system_fw_noblacklist`, `system_hidelsclock`, `system_hidelshint`, `system_hidelsstatusbar`
`system_hidestatusbar_whenscreenshot`, `system_lockscreen_disable_edit`
`system_lockscreen_hidezenmode`, `system_lockscreenshortcuts`, `system_ls_force_systemfonts`
`system_lsalarm`, `system_maxsbicons`, `system_minimalnotifview`, `system_mobiletypeicon`
`system_morenotif`, `system_mutevisiblenotif`, `system_netspeedinterval`
`system_networkindicator_wifi`, `system_nolightuponcharges`, `system_nopassword`
`system_nosafevolume`, `system_noscreenlock_act`, `system_nosilentvibrate`, `system_nosos`
`system_notif_disable_fold`, `system_notifafterunlock`, `system_notifchannelsettings`
`system_notifimportance`, `system_notifrowmenu`, `system_notify_openinfw`
`system_qs_disable_fakeclock_anim`, `system_qs_force_systemfonts`, `system_qs_hideoperator`
`system_qshaptics`, `system_removedismiss`, `system_scramblepin`, `system_screenshot_overlay`
`system_secureqs`, `system_shortcut_app`, `system_showpct`, `system_statusbar_alarm_atright`
`system_statusbar_batterystyle`, `system_statusbar_batterytempandcurrent`
`system_statusbar_clock_position`, `system_statusbar_clocktweak`, `system_statusbar_dualrows`
`system_statusbar_dualsimin2rows`, `system_statusbar_horizmargin`
`system_statusbar_mobile_digital_signal`, `system_statusbar_mobiletype_single`
`system_statusbar_topmargin`, `system_statusbarcontrols`, `system_statusbaricons_alarm`
`system_statusbaricons_battery1`, `system_statusbaricons_battery3`
`system_statusbaricons_privacy`, `system_statusbaricons_privacy_prompt`
`system_statusbaricons_signal`, `system_statusbaricons_vowifi`, `system_statusbaricons_wifi`
`system_statusbaricons_wifistandard`, `system_taptounlock`, `system_visualizer`
`system_volume_mode_button_colors`, `system_volumebar_blur_mtk`, `system_volumeblur_collapsed`
`system_volumeblur_expanded`, `system_volumedialogdelay_collapsed`
`system_volumedialogdelay_expanded`, `system_volumetimer`, `various_showcallui`

### EXECUTABLE_SECURITYCENTER_KEYS

`system_applock_scramblepin`, `system_hidelowbatwarn`, `various_appdetails`, `various_appsort`, `various_disable_dock_suggest`, `various_disable_freeform_suggest_blacklist`, `various_disable_reset_recents_privacy_blur`, `various_disableapp`, `various_enable_expand_sidebar`, `various_hide_report_ondetails`, `various_privacyapps_column_nums4`, `various_replace_defaultopen_with_openbydefault`, `various_restrictapp`, `various_show_battery_temperature`, `various_skip_interceptperm`, `various_skip_securityscan`

### P3_A2_FALSE_NEGATIVE_MULTI_TARGET

- `controls_nonavbar`
  - old set: `['SYSTEMUI']`
  - correct set: `['LAUNCHER', 'SYSTEMUI']`
  - why: P3-A2 only used the master preferenceKey of HideNavBarFeature in SystemUiFeatures.kt (target SYSTEM_UI). P3-A3 resolves the enabled conditions of every LazyFeatureSpec and finds LauncherHideNavBarFeature in LauncherPostAttachFeatures.kt also gating controls_nonavbar (target LAUNCHER), plus HideNavBarBeforeScreenshotFeature reading it.

### P3-A3 gate status (carried forward)

```text
P3_A3_SHA = e88ff4ffca65d408e711f82bddfb8a6d552a79ca
P3_A3_GATE = HOLD
P3_A3_PUBLICATION_GATE = PASS
P3_A3_KEY_TO_SET_MODEL = PASS
P3_A3_MULTI_HOST_MODEL = PASS
P3_A3_FAIL_CLOSED_MODEL = PASS
```

P3-A4 blocker resolved:

```text
BLOCKER = DECLARED_PREFERENCE_KEY_TREATED_AS_INSTALL_GATE_FOR_ALWAYS_INSTALLED_FEATURE
```

### P3-A4 model correction

A `LazyFeatureSpec` declared `preferenceKey` is now treated as **feature identity only**, not as an automatic executable restart gate. The only keys that contribute to a feature's executable target are:

1. Positive value reads inside the resolved `enabled` / `isEnabled` / `evaluateEnabled` body (`prefs.getBoolean/getInt/getString/getStringAsInt`).
2. Concrete install-time captured values or resource reinit requirements.
3. Other restart-specific source evidence.

A feature is considered **always-installed** when its resolved enabled body is just `true`. Its declared `preferenceKey` (if any) is therefore a runtime/observer value, not an install gate, and does **not** produce an executable restart target.

### Always-installed features

| feature file | feature name | preferenceKey | FeatureTarget | why | runtime value path | executable target before | executable target after |
|---|---|---|---|---|---|---|---|
| SystemServerFeatures.kt | Animation Scale Bridge | (none) | FeatureTarget.SYSTEM_SERVER | AnimationScaleBridgeFeature.isEnabledCondition = true | { AnimationScaleBridgeFeature(lpparam) } | (none) | {} |
| SystemServerFeatures.kt | Updater Services Bridge | (none) | FeatureTarget.SYSTEM_SERVER | UpdaterServicesBridgeFeature.isEnabledCondition = true | { UpdaterServicesBridgeFeature(lpparam) } | (none) | {} |
| SystemServerFeatures.kt | Disable Window Blurs | system_disable_window_blurs | FeatureTarget.SYSTEM_SERVER | DisableWindowBlursFeature.evaluateEnabled = true | SystemDisplayHooks.DisableWindowBlursHook(lpparam) | (none) | {} |
| SystemServerFeatures.kt | USB Default Function | system_usb_default_function | FeatureTarget.SYSTEM_SERVER | UsbDefaultFunctionFeature.isEnabledCondition = true | SystemUsbDefaultHooks runtime handler | (none) | {} |
| SystemUiFeatures.kt | Strong Toast Presentation | system_strong_toast_mode | FeatureTarget.SYSTEM_UI | StrongToastPresentationFeature.evaluateEnabled = true | StrongToastRuntimeState observer / passthrough | {SYSTEMUI} | {} |

### ALWAYS_INSTALLED_KEYS_REMOVED_FROM_EXECUTABLE

```text
COUNT = 1
LIST = ['system_strong_toast_mode']
```

These keys were incorrectly granted executable restart targets in P3-A3 because the parser added every `LazyFeatureSpec.preferenceKey` regardless of whether the feature was always-installed.

### StrongToast and USB target confirmation

```text
STRONG_TOAST_MODE_TARGETS = set()
STRONG_TOAST_POSITION_TARGETS = set()
STRONG_TOAST_BOTTOM_OFFSET_TARGETS = set()
USB_DEFAULT_FUNCTION_TARGETS = {}
CONTROLS_NONAVBAR_TARGETS = {LAUNCHER, SYSTEMUI}
```

- `system_strong_toast_mode`, `system_strong_toast_position`, `system_strong_toast_bottom_offset`: `NONE_LIVE` (`StrongToastRuntimeState` observer + passthrough).
- `system_usb_default_function`: `NONE_LIVE` (runtime handler, P1 USB already confirmed).

### Count consistency

```text
COUNT_CONSISTENCY = PASS
EXECUTABLE_LAUNCHER_UNIQUE_KEYS + EXECUTABLE_SYSTEMUI_UNIQUE_KEYS + EXECUTABLE_SECURITYCENTER_UNIQUE_KEYS
- (MULTI_EXECUTABLE_TARGET_KEYS overlap)
= 61 + 118 + 16 - 3
= 192
```

### P3-B safety gate

```text
P3_B_SAFE_TO_IMPLEMENT = YES
P3_B_AUTHORIZATION = NO
```

- The executable positive allowlist is exact (positive feature evidence only), all multi-host masters are complete, the always-installed false positive `system_strong_toast_mode` is removed, and the registry is `KEY_TO_SET` with fail-closed misses.
- `UNKNOWN_EVIDENCE = 0`.
- `NO_EXECUTABLE_MAPPING = 113` keys fail-closed at runtime.
- P3-B implementation is **not authorized** in this task; this document supplies the pre-flight registry only.

### Verification commands

```text
python tools/verify.py fast --changed
git diff --check
git status --short
```

### Source of truth for full mapping

The machine-readable full mapping is at:

```text
C:\Users\tv\AppData\Local\Temp\p3a4_registry.json
```

This file is a temporary audit artifact and is not part of the repository.

## P3-B IMPLEMENTATION CANDIDATE

### Base

```text
BASE = 13f46ab3...
```

### Implementation summary

P3-B is implemented as a positive-allowlist, fail-closed matched-restart action.

- **Registry**: `PreferenceRestartTargetRegistry.kt` is the static source of truth.
  It maps canonical preference keys to `Set<RestartTarget>` and returns `emptySet()`
  on a miss.  Counts are 192 unique executable keys (LAUNCHER 61, SYSTEMUI 118,
  SECURITY_CENTER 16) and 3 multi-host keys (`controls_fsg_assist_left_action`,
  `controls_fsg_assist_right_action`, `controls_nonavbar`).

- **Resolver**: `PreferenceRestartTargetResolver.kt` provides two entry points:
  - `resolveForKeys(List<String?>)`: a pure lookup union for list-backed callers.
  - `resolvePreferenceScreen(PreferenceScreen)`: an in-memory, recursive walk of the
    current preference tree.  It only collects visible, enabled, functional leaves
    (CheckBox/Switch, List/DropDown, EditText, Color, MultiSelect, SeekBar).
    `PreferenceCategory` / `PreferenceCategoryEx`, navigation-only `PreferenceEx`,
    nested `PreferenceScreen` rows and non-functional leaves are ignored.  It does
    not cross into sub-fragments or intents.

- **Menu behavior**: `PreferenceFragmentBase` adds `R.id.restartmatched`.  On
  non-Main fragments with `toolbarMenu`, the item is shown only when
  `resolvePreferenceScreen` returns a non-empty set.  The old individual
  `restartlauncher` / `restartsystemui` / `restartsecuritycenter` items are hidden
  on non-Main fragments; `MainFragment` keeps them.  `onPrepareOptionsMenu`
  re-resolves when preferences may have changed visible/enabled/dependency state.
  `MainFragment` search expand/collapse logic keeps `restartmatched` hidden.
  `CategorySelector` and `System` no longer use page-name `activeMenus` mappings.

- **Execution**: `PreferenceRestartTargetExecutor` performs a single root check,
  then attempts the matched targets in fixed order: `SECURITY_CENTER`, `LAUNCHER`,
  `SYSTEMUI`.  `SECURITY_CENTER` and `LAUNCHER` use `am force-stop` on their
  package; `SYSTEMUI` uses `pidof` followed by `kill -9`.  Every selected target is
  attempted; failures are isolated and aggregated.  One Toast is shown on the main
  thread with `restart_affected_components_done`, `_partial` or `_failed`.
  No soft reboot or system reboot is invoked.  The fragment guards against a
  destroyed activity before showing the Toast.

- **Soft-reboot exclusion**: the matched restart set is never mixed with
  `R.id.softreboot`.  Soft reboot remains a separate, user-initiated action.

### P3-B self assessment

```text
P3_B_SELF_ASSESSMENT = PASS_CANDIDATE
P3_B_FINAL_GATE = (not written)
```

This implementation is a candidate; the final gate is left for the authorized
reviewer to close.

## P3-B FINAL CORRECTIVE

### Base

```text
BASE = 1438e298c4d7268fa3b80af28c082d2953bda1bc
```

### Corrected items

- **Generic secondary-page menu reachability**: menu capability is now
centralised in `SubFragment.onCreate(...)` via `shouldEnablePreferenceToolbar(...)`.
Every page opened with `SettingsType.Preference` receives `toolbarMenu = true`,
so the matched restart item can be resolved and shown on Launcher, Controls,
Various, System, `CategorySelector`, standalone typed sub-fragments
(`System_Visualizer`, `System_BatteryIndicator`, etc.) and bare `SubFragment()`
standalone pages (charging info, alarm on lock, etc.).

- **Removed redundant page-specific activation**: `System.kt` and
`CategorySelector.kt` no longer set `toolbarMenu` based on `sub` or `cat`.
Menu capability is derived from the fragment contract, not page names.

- **Zero-allocation registry lookup**: `PreferenceRestartTargetRegistry`
now returns one of the pre-built constants (`LAUNCHER_ONLY`, `SYSTEMUI_ONLY`,
`SECURITY_CENTER_ONLY`, `LAUNCHER_AND_SYSTEMUI`, `EMPTY_TARGETS`).  The three
multi-host keys are tested first, so the overlap is resolved correctly.

- **Execution failure diagnostics**: `PreferenceRestartTargetExecutor` logs
`target`, `operation`, `command`, `exit` and `output` for every failed
`force-stop`, `pidof` and `kill -9` step.  Toast text stays short and aggregated.

- **Tests**: added `SubFragmentMenuPolicyTest` covering the menu policy,
`MainFragment` not being a `SubFragment`, and the structural reachability of all
relevant secondary preference pages.  Added a referential-identity test in
`PreferenceRestartTargetRegistryTest` to lock in the zero-allocation contract.

### P3-B self assessment

```text
P3_B_SELF_ASSESSMENT = PASS_CANDIDATE
P3_B_FINAL_GATE = (not written)
```

The final gate remains for the authorized reviewer.

---

## P3 Simplification — Page-Level Static Bitmask

Implemented on top of the P3-A4 / P3-B evidence, this simplification replaces the
preference-key registry and live preference-screen resolver with a single static
page-to-mask `when`.

### Design

- `RestartMask`: 3-bit int (`LAUNCHER=1`, `SYSTEMUI=2`, `SECURITY_CENTER=4`).
- `RestartPagePolicy.maskFor(contentResId, sub?)`: static table built from P3-A4
  executable mapping and the generated/source preference XML.
- `MatchedRestartExecutor.execute(mask)`: one root check, fixed order
  `SECURITY_CENTER -> LAUNCHER -> SYSTEMUI`, attempt all selected bits, no soft reboot.
- `PreferenceFragmentBase.matchedRestartMask()`: `SubFragment` overrides with the
  pre-computed page mask.
- `SubFragment` still enables `toolbarMenu` for all `SettingsType.Preference` pages,
  but `restartmatched` visibility is now `matchedRestartMask() != 0`.

### Removed

- `PreferenceRestartTargetRegistry.kt`
- `PreferenceRestartTargetResolver.kt`
- `RestartTarget.kt`
- `PreferenceRestartTargetExecutor.kt`
- `PreferenceRestartTargetRegistryTest.kt`
- `PreferenceRestartTargetResolverTest.kt`
- `PreferenceRestartTargetExecutionOrderTest.kt`

### Added / replaced

- `RestartPagePolicy.kt`
- `MatchedRestartExecutor.kt`
- `RestartPagePolicyTest.kt`
- `MatchedRestartExecutorTest.kt`

### Page mask table

| Page (contentResId) | Mask | Targets |
|---|---|---|
| `R.xml.mod_search_index` | `0` | NONE |
| `R.xml.prefs_controls` | `3` | LAUNCHER,SYSTEMUI |
| `R.xml.prefs_controls_cat` | `0` | NONE |
| `R.xml.prefs_controls_fingerprint` | `0` | NONE |
| `R.xml.prefs_controls_fsg` | `3` | LAUNCHER,SYSTEMUI |
| `R.xml.prefs_controls_navbar` | `3` | LAUNCHER,SYSTEMUI |
| `R.xml.prefs_controls_power` | `0` | NONE |
| `R.xml.prefs_controls_volume` | `2` | SYSTEMUI |
| `R.xml.prefs_launcher` | `5` | LAUNCHER,SECURITY_CENTER |
| `R.xml.prefs_launcher_bugfixes` | `1` | LAUNCHER |
| `R.xml.prefs_launcher_cat` | `0` | NONE |
| `R.xml.prefs_launcher_folders` | `1` | LAUNCHER |
| `R.xml.prefs_launcher_gestures` | `1` | LAUNCHER |
| `R.xml.prefs_launcher_other` | `1` | LAUNCHER |
| `R.xml.prefs_launcher_privacyapps` | `5` | LAUNCHER,SECURITY_CENTER |
| `R.xml.prefs_launcher_titles` | `1` | LAUNCHER |
| `R.xml.prefs_main` | `0` | NONE |
| `R.xml.prefs_system` | `7` | LAUNCHER,SYSTEMUI,SECURITY_CENTER |
| `R.xml.prefs_system_alarmonlock` | `2` | SYSTEMUI |
| `R.xml.prefs_system_albumartonlock` | `2` | SYSTEMUI |
| `R.xml.prefs_system_applock` | `4` | SECURITY_CENTER |
| `R.xml.prefs_system_audio` | `2` | SYSTEMUI |
| `R.xml.prefs_system_autobrightness` | `0` | NONE |
| `R.xml.prefs_system_batteryindicator` | `2` | SYSTEMUI |
| `R.xml.prefs_system_betterpopups` | `2` | SYSTEMUI |
| `R.xml.prefs_system_cat` | `0` | NONE |
| `R.xml.prefs_system_charginginfo` | `2` | SYSTEMUI |
| `R.xml.prefs_system_controlcenter_clock` | `2` | SYSTEMUI |
| `R.xml.prefs_system_controlcenter_themestyle` | `2` | SYSTEMUI |
| `R.xml.prefs_system_detailednetspeed` | `2` | SYSTEMUI |
| `R.xml.prefs_system_drawer` | `2` | SYSTEMUI |
| `R.xml.prefs_system_floatingwindows` | `3` | LAUNCHER,SYSTEMUI |
| `R.xml.prefs_system_hideicons` | `2` | SYSTEMUI |
| `R.xml.prefs_system_lockscreen` | `2` | SYSTEMUI |
| `R.xml.prefs_system_lockscreenshortcuts` | `2` | SYSTEMUI |
| `R.xml.prefs_system_noscreenlock` | `2` | SYSTEMUI |
| `R.xml.prefs_system_notifications` | `2` | SYSTEMUI |
| `R.xml.prefs_system_other` | `6` | SYSTEMUI,SECURITY_CENTER |
| `R.xml.prefs_system_qs` | `2` | SYSTEMUI |
| `R.xml.prefs_system_recents` | `1` | LAUNCHER |
| `R.xml.prefs_system_screen` | `2` | SYSTEMUI |
| `R.xml.prefs_system_screenshot` | `0` | NONE |
| `R.xml.prefs_system_secureqs` | `2` | SYSTEMUI |
| `R.xml.prefs_system_statusbar` | `2` | SYSTEMUI |
| `R.xml.prefs_system_statusbar_batterystyle` | `2` | SYSTEMUI |
| `R.xml.prefs_system_statusbar_batterytempandcurrent` | `2` | SYSTEMUI |
| `R.xml.prefs_system_statusbar_clock` | `2` | SYSTEMUI |
| `R.xml.prefs_system_statusbar_mobilesignal` | `2` | SYSTEMUI |
| `R.xml.prefs_system_statusbar_righticons` | `2` | SYSTEMUI |
| `R.xml.prefs_system_statusbar_showdevicetemperature` | `0` | NONE |
| `R.xml.prefs_system_statusbarcontrols` | `2` | SYSTEMUI |
| `R.xml.prefs_system_toasts` | `0` | NONE |
| `R.xml.prefs_system_vibration` | `2` | SYSTEMUI |
| `R.xml.prefs_system_vibration_amp` | `0` | NONE |
| `R.xml.prefs_system_visualizer` | `2` | SYSTEMUI |
| `R.xml.prefs_various` | `6` | SYSTEMUI,SECURITY_CENTER |
| `R.xml.prefs_various_calls` | `2` | SYSTEMUI |
| `R.xml.prefs_various_calluibright` | `0` | NONE |
| `R.xml.prefs_various_cat` | `0` | NONE |
| `R.xml.prefs_various_exclusive` | `0` | NONE |
| `R.xml.prefs_various_gboard` | `0` | NONE |
| `R.xml.prefs_various_general` | `0` | NONE |
| `R.xml.prefs_various_hiddenfeatures` | `0` | NONE |
| `R.xml.prefs_various_package_installer` | `0` | NONE |
| `R.xml.prefs_various_security_center` | `4` | SECURITY_CENTER |
| `R.xml.prefs_various_settings` | `4` | SECURITY_CENTER |

### P3 simplification self assessment

```text
P3_SIMPLIFICATION_SELF_ASSESSMENT = PASS_CANDIDATE
P3_COMPLEXITY_GATE = (not written)
P3_FINAL_GATE = (not written)
```

The P3 simplification is a candidate; the final gate remains for the authorized reviewer.

### Unknown / missing shared sub closure

For the shared resources `R.xml.prefs_system`, `R.xml.prefs_launcher`, `R.xml.prefs_controls`, and `R.xml.prefs_various`, an unknown or missing `sub` value resolves to `RestartMask.NONE` instead of a module-wide conservative union.

```text
UNKNOWN/MISSING SHARED SUB -> RestartMask.NONE
FAIL_CLOSED = YES
```
