# PERF-A14-P1-SLICE-A — SystemClock hot-path preference and redundant-update elimination

## Scope

Only `SystemClockHooks` tick/update path. No other status-bar features, no global
preference architecture changes, no visual semantic changes.

## Call chain (pre-optimization)

### Per-second path

```
SecondTicker.run()                                 (every 1 s)
  DateFormat.is24HourFormat(context)
  XposedHelpers.setObjectField(clockController, "mIs24", ...)
  for each listener in mClockListeners:
    if ModuleHelper.getViewInfo(clock, "showSeconds") != null:
      XposedHelpers.callMethod(clock, "updateTime")

MiuiClock.updateTime / MiuiStatusBarClock.updateTime
  -> updateTimeHook.intercept()
     -> MainModule.mPrefs.getString("system_cc_clock_customformat", "")
     -> MainModule.mPrefs.getString("system_cc_dateformat", "")
     -> MainModule.mPrefs.getString("system_drawer_dateformat", "")
     -> MainModule.mPrefs.getString("system_statusbar_clock_customformat", "")
     -> MainModule.mPrefs.getBoolean("system_statusbar_clock_customformat_enable")
     -> MainModule.mPrefs.getBoolean("system_statusbar_clock_show_seconds")
     -> MainModule.mPrefs.getBoolean("system_statusbar_clock_24hour_format")
     -> MainModule.mPrefs.getBoolean("system_statusbar_clock_show_ampm")
     -> MainModule.mPrefs.getBoolean("system_statusbar_clock_leadingzero")
     -> HookUtils.getResId(...)
     -> mContext.getString(fmtResId)
     -> StringBuilder/format construction
     -> clock.text = ...
```

### Per-minute path

`MiuiClock.updateTime` is also invoked by the framework once per minute for
date/clock views. `updateTimeHook.intercept()` runs the same branch, so the same
`mPrefs.get*` list applies.

### View creation path

```
MiuiClock constructor hook
  -> setViewInfo(clockName)
  -> getShowSeconds() / getCCShowSeconds()
     -> MainModule.mPrefs.getBoolean("system_statusbar_clock_show_seconds")
     -> MainModule.mPrefs.getString("system_statusbar_clock_customformat", "")
     -> MainModule.mPrefs.getBoolean("system_statusbar_clock_customformat_enable")
     -> MainModule.mPrefs.getString("system_cc_clock_customformat", "")
  -> initClockStyle(clock, "ccClock")
     -> MainModule.mPrefs.get* (≈ 20 reads, see table)

MiuiStatusBarClockController constructor hook
  -> initSecondTicker(thisObject)
     -> getShowSeconds() / getCCShowSeconds() (same as above)

MiuiPhoneStatusBarView.onAttachedToWindow hook
  -> initClockStyle(mClock, "clock")
     -> MainModule.mPrefs.get* (≈ 20 reads)
```

### Configuration change path

`CCClockTweakHook` intercepts `MiuiNotificationHeaderView.updateResources`.
`MiuiPhoneStatusBarView.onAttachedToWindow` is invoked when the status bar view
is re-attached. Both currently re-read `mPrefs`.

## `mPrefs.get*` count by hot call site

| Call site | `mPrefs.get*` per call | Frequency |
|---|---|---|
| `updateTimeHook.intercept` (statusbar default) | 9 | every second / every minute |
| `updateTimeHook.intercept` (cc/date/drawer) | 1 | every minute |
| `initClockStyle` (statusbar clock) | 19 | view create / attach |
| `initClockStyle` (cc clock) | 2 | view create / attach |
| `getShowSeconds` | 3 | controller constructor, `TIME_SET` |
| `getCCShowSeconds` | 1 | controller constructor, `TIME_SET` |

## Preference keys used by `SystemClockHooks`

### Install-time constants (read once at package-ready, captured in closures)

- `system_statusbar_enable_weather_param`
- `system_statusbaricons_clock`
- `system_statusbar_clocktweak`
- `system_cc_clocktweak`
- `system_cc_hidedate`
- `system_drawer_hidedate`

These are evaluated by `StatusBarClockTweakHook` and `CCClockTweakHook` at install
time and stored in anonymous `MethodHook` closures. Runtime toggling of these
keys still requires a SystemUI restart to take full effect because the hook
registration itself is decided at package-ready.

### Runtime style / format keys (need snapshot refresh on change)

- `system_statusbar_clock_customformat_enable`
- `system_statusbar_clock_customformat`
- `system_statusbar_clock_fontsize`
- `system_statusbar_clock_align`
- `system_statusbar_clock_bold`
- `system_statusbar_clock_leftmargin`
- `system_statusbar_clock_rightmargin`
- `system_statusbar_clock_verticaloffset`
- `system_statusbar_clock_chip`
- `system_statusbar_clock_chip_usemonet`
- `system_statusbar_clock_chip_customtextcolor`
- `system_statusbar_clock_chip_startcolor`
- `system_statusbar_clock_chip_endcolor`
- `system_statusbar_clock_chip_textcolor`
- `system_statusbar_clock_chip_orientation_vertical`
- `system_statusbar_clock_chip_horizpadding`
- `system_statusbar_clock_chip_verticalpadding`
- `system_statusbar_clock_chip_radius`
- `system_statusbar_clock_fixedcontent_width`
- `system_statusbar_clock_show_seconds`
- `system_statusbar_clock_24hour_format`
- `system_statusbar_clock_show_ampm`
- `system_statusbar_clock_leadingzero`
- `system_cc_clock_customformat_enable`
- `system_cc_clock_customformat`
- `system_cc_dateformat`
- `system_drawer_dateformat`

## Hot path View setters / allocations (pre-optimization)

`updateTimeHook.intercept` per tick:

- `StringBuilder.setLength(0)` / `append` (format + text)
- `String.substring` / concatenation (insert `:ss`, replace hour token)
- `String.replace("tq", ...)` (if weather enabled)
- `XposedHelpers.callMethod(mCalendar, "format", ...)`
- `clock.text = ...`

`initClockStyle` per view attach:

- `setTextSize`, `setLineSpacing`, `textAlignment`, `typeface`, `translationY`
- `layoutParams` read/assign
- `setTextColor`, `background = GradientDrawable()`
- `GradientDrawable` + `setPadding` + `cornerRadius`
- `setSingleLine`, `maxLines`

All color/Drawable/LayoutParams work is only inside `initClockStyle`, which is
not part of the per-second tick.

## Optimization target

1. `updateTimeHook.intercept` must read `MainModule.mPrefs` **0** times per tick.
2. `initClockStyle` must read `MainModule.mPrefs` **0** times per view attach once
   a `ClockStyleSnapshot` is available.
3. Snapshot is built at most once per `Configuration`/pref-set and updated only
   when a relevant key changes or a new `Configuration` is observed.
4. No new Xposed Hook, no new thread, no new HandlerThread, no new Timer, no
   polling, no new permanent global Receiver.
5. Preference observer is bound to `MiuiStatusBarClockController` and released
   when the controller is garbage collected.

## Post-optimization call chain

### Per-second / per-minute path

```
SecondTicker.run()
  for each listener in mClockListeners:
    if getViewInfo(clock, "showSeconds") != null:
      XposedHelpers.callMethod(clock, "updateTime")

updateTimeHook.intercept()
  -> currentClockStyleSnapshot()          (one volatile read, no mPrefs)
  -> buildClockText(clockName, snapshot, ...)
     -> snapshot.statusbarCustomFormat / statusbarDefaultFormat / ...
     -> snapshot.enableWeatherParam (no mPrefs)
     -> snapshot.* only (all booleans / strings)
  -> mCalendar.format(...)
  -> clock.text = ...
```

`buildClockText` and `initClockStyle` do **not** contain `MainModule.mPrefs`
references; the only `mPrefs` calls in `SystemClockHooks` are in
`buildClockStyleSnapshot` (cold path) and at package-ready time for the
install-time flags.

### Snapshot refresh path

```
MiuiStatusBarClockController.<init>
  -> buildClockStyleSnapshot(MainModule.mPrefs, mContext.resources)  (once)
  -> initSecondTicker(...)
  -> observePreferenceChange(observer, thisObject)                    (owner-bound)

PreferenceObserver.onChange
  -> if key in CLOCK_STYLE_PREFERENCE_KEYS
     -> handler.post { ... }
     -> refreshClockStyleSnapshot(res)
     -> re-apply initClockStyle to clock / ccClock listeners
     -> initSecondTicker(controller)
```

### `mPrefs.get*` count after optimization

| Call site | `mPrefs.get*` per hot call | Frequency |
|---|---|---|
| `updateTimeHook.intercept` | **0** | every second / every minute |
| `initClockStyle` | **0** | view create / attach (uses snapshot) |
| `initSecondTicker` | **0** | controller constructor, `TIME_SET`, preference observer |
| `buildClockStyleSnapshot` | 24 | cold path only |

## Evidence

- `SystemClockHotPathTest.buildClockText_100Ticks_noPrefReads` runs 100 ticks
  through `buildClockText` without any `MainModule.mPrefs` reference.
- `SystemClockHotPathTest.source_noMainModulePrefsInBuildClockTextOrInitClockStyle`
  statically asserts neither `buildClockText` nor `initClockStyle` contain
  `MainModule.mPrefs`.
- `SystemClockHotPathTest.initClockStyle_idempotentSameSnapshot` and
  `initClockStyle_reapplyWithDifferentSnapshot` verify per-view style
  idempotency and correct re-application when the snapshot changes.
- `SystemClockHotPathTest.buildClockStyleSnapshot_defaultStatusbarFormat` and
  `buildClockText_minuteMode_doesNotShowSeconds` cover 24-hour, AM/PM,
  seconds, and leading-zero paths.

## Charging-info observer lifecycle review

The existing `PreferenceObserver` in `SystemLockScreenHooks` for the lock-screen
charging info font size was reviewed against the five lifecycle questions:

1. **Initial snapshot availability**: the observer fires on every preference
   change and is also triggered by `handlePreferenceChanged` when the bootstrap
   finishes loading the snapshot. If the snapshot is not yet ready, `getInt`
   returns the default and the callback is a no-op; it will re-apply the correct
   value as soon as the real value arrives.
2. **Actual value changes**: `onChange` re-runs `applyChargingInfoStyle`, so
   increases and decreases both converge to the target font size (no cumulative
   scaling because `setTextSize` is replaced, not added).
3. **Owner-bound disposal**: the observer is registered with owner = the
   `KeyguardIndicationTextView` instance. `PreferenceObserverRegistry` holds a
   weak reference; the strong reference lives in the view's additional instance
   field. When the view is garbage collected the observer is released.
4. **Duplicate registration**: `PreferenceObserverRegistry.observePreferenceChange`
   with an owner first removes any existing observer for that owner, so a
   re-created view will replace its old observer instead of accumulating.
5. **Unbounded accumulation**: no static list of observers is kept for this
   feature; the process-scoped set only contains the weak reference, which is
   dropped when the view dies, and each view gets at most one observer.

The charging-info implementation is sound and was not changed.

## SecondTicker lifecycle verification

`SystemClockHooks.initSecondTicker` now stores a single `SecondTicker` per
`MiuiStatusBarClockController`, reuses it when the seconds configuration does not
change, and disposes it when seconds are disabled. The ticker:

- holds the controller through a `WeakReference` so the controller can be garbage
  collected without being pinned by the static `ScreenStateController` listener list;
- does not hold any `View` strongly;
- is removed from `ScreenStateController` when `dispose()` is called, when the
  controller is collected (via `start()` / `stop()` weak-ref checks), or when the
  seconds snapshot turns off.

Evidence:

- `SystemClockHotPathTest.secondTicker_enablingSecondsCreatesOneActiveTicker` — a
  seconds-on snapshot installs exactly one ticker and one `ScreenStateController`
  listener.
- `SystemClockHotPathTest.secondTicker_repeatedSameSeconds_doesNotRestartTicker` —
  re-invoking `initSecondTicker` with the same seconds flags keeps the same ticker
  instance and does not add duplicate listeners.
- `SystemClockHotPathTest.secondTicker_disablingSecondsDisposesAndRemovesTicker` —
  a seconds-off snapshot disposes the old ticker and leaves no listeners behind.
- `SystemClockHotPathTest.secondTicker_onThenOffThenOn_createsNewTickerDoesNotStack`
  — cycling seconds off and on creates a fresh ticker and never stacks listeners.
- `SystemClockHotPathTest.secondTicker_holdsControllerWeakly` — after the only
  external references to the controller are dropped and GC runs, the controller is
  collected while the ticker (still held by `ScreenStateController`) remains; the
  weak ref does not keep the controller alive.
- `SystemClockHotPathTest.secondTicker_tickerDoesNotHoldViewOrControllerStrongly` —
  reflective inspection confirms the `clockControllerRef` field is a `WeakReference`
  and the `SecondTicker` class has no strongly held `View` fields.

## Status

`ENGINEERING_COMPLETE_DEVICE_EVIDENCE_PENDING`

Engineering verification is complete:

- `ClockStyleSnapshot` eliminates `MainModule.mPrefs` reads from `buildClockText`
  and `initClockStyle`.
- `SecondTicker` lifecycle has owner-bound disposal, deduplication, and weak
  references satisfying the A14 runtime constraints.
- `python tools/verify.py full`, `gradlew :app:assembleRelease`, and
  `python tools/source_hazard_scan.py` all pass.

Device-level PSS / USS / CPU measurements remain pending per the protocol in
`P0_RUNTIME_BASELINE_AND_AUDIT_PROTOCOL.md`.
