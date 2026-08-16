# PERF-A14-P1-SLICE-B — NetworkSpeedView `setNetworkSpeed` hot-path preference and redundant-update elimination

## Scope

Only the `NetworkSpeedView.setNetworkSpeed` → `SystemUIStatusBarHooks.applyNetSpeedTextStyle()`
hot path in `SystemUIStatusBarHooks`. No other status-bar features, no global preference
architecture changes, no visual semantic changes.

## Call chain (pre-optimization)

### Per-second / per-interval path

```text
NetworkSpeedController / NetworkSpeedView.setNetworkSpeed(number, unit, visible)
  -> NetSpeedStyleHook.setNetworkSpeed.afterHook
     -> MainModule.mPrefs.getStringAsInt("system_detailednetspeed_style", 1)
     -> MainModule.mPrefs.getBoolean("system_netspeed_boldfont")
     -> MainModule.mPrefs.getInt("system_netspeed_fontsize", 13)
     -> MainModule.mPrefs.getInt("system_netspeed_fixedcontent_width", 10)
     -> MainModule.mPrefs.getInt("system_netspeed_leftmargin", 0)
     -> MainModule.mPrefs.getInt("system_netspeed_rightmargin", 0)
     -> MainModule.mPrefs.getInt("system_netspeed_verticaloffset", 8)
     -> MainModule.mPrefs.getStringAsInt("system_detailednetspeed_align", 1)
     -> MainModule.mPrefs.getInt("system_netspeed_rowspacing", 100)
     -> XposedHelpers.getObjectField(speedView, "mNetworkSpeedNumberText")
     -> XposedHelpers.getObjectField(speedView, "mNetworkSpeedUnitText")
     -> setTextSize, setTypeface, setTextAppearance, layoutParams, padding, translationY
     -> speedView.setNetworkSpeed(...) (original call)
```

This ran on every network-speed refresh, typically once per second (or per configured
interval).

### View creation / attach path

```text
NetworkSpeedView.onFinishInflate()
  -> NetSpeedStyleHook.onFinishInflate.afterHook
     -> MainModule.mPrefs.getBoolean("system_netspeed_use_clock_style")
     -> if true: setTextAppearance from system style
     -> if false: same 9 mPrefs reads + full applyNetSpeedTextStyle()
```

### setTextAppearance / configuration change path

```text
TextView.setTextAppearance(resId)
  -> NetSpeedStyleHook.setTextAppearance.afterHook
     -> same 9 mPrefs reads + full applyNetSpeedTextStyle()
```

## `mPrefs.get*` count by hot call site

| Call site | `mPrefs.get*` per call | Frequency |
|---|---|---|
| `setNetworkSpeed` per-interval path | 9 | every tick (≈ 1 s) |
| `onFinishInflate` view attach | 9 (plus `system_netspeed_use_clock_style`) | view create / inflate |
| `setTextAppearance` hook | 9 | style change / config change |

## Preference keys used by the snapshot

### Runtime style keys (snapshot refreshes on change)

- `system_detailednetspeed_style`
- `system_netspeed_boldfont`
- `system_netspeed_fontsize`
- `system_netspeed_fixedcontent_width`
- `system_netspeed_leftmargin`
- `system_netspeed_rightmargin`
- `system_netspeed_verticaloffset`
- `system_detailednetspeed_align`
- `system_netspeed_rowspacing`

### Cold-path / view-attach only

- `system_netspeed_use_clock_style` — only read in `onFinishInflate` to decide whether to
  apply the system clock text appearance instead of the custom snapshot.

## Hot path View setters / allocations (pre-optimization)

`applyNetSpeedTextStyle` per tick:

- `getObjectField` for `mNetworkSpeedNumberText` and `mNetworkSpeedUnitText`
- `setTextSize`, `setTypeface` (number and unit)
- `setGravity`, `textAlignment`
- `layoutParams` read/assign for number and unit
- `setPaddingRelative`, `translationY`
- `setSingleLine`, `setMaxLines`, `setLineSpacing` (speed style 2 only)

## Optimization target

1. `setNetworkSpeed` per-tick path must read `MainModule.mPrefs` **0** times after the first
   tick of a stable snapshot.
2. `applyNetSpeedTextStyle` must read `MainModule.mPrefs` **0** times.
3. Snapshot is built at most once per pref-set and updated only when a relevant key changes.
4. Per-tick redundant View setters are eliminated: the same `NetSpeedTextStyleSnapshot.id`
   applied to the same `speedView` with `typefaceOnly = false` returns early.
5. The text update (`setNetworkSpeed` itself) is never blocked or reordered by the style
   snapshot; `applyNetSpeedTextStyle` does not call `setText()`.
6. No new Xposed Hook, thread, HandlerThread, Timer, polling, or global Receiver.
7. Preference observer is process-scoped and owner-bound to `SystemUIStatusBarHooks`.

## Post-optimization call chain

### Per-interval / per-tick path

```text
NetworkSpeedView.setNetworkSpeed(number, unit, visible)
  -> NetSpeedStyleHook.setNetworkSpeed.afterHook
     -> currentOrBuildNetSpeedTextStyleSnapshot()   (one volatile read, 9 reads only on first call)
     -> applyNetSpeedTextStyle(speedView, snapshot, typefaceOnly = false)
        -> XposedHelpers.getAdditionalInstanceField(speedView, NETSPEED_LAST_FULL_STYLE_SNAPSHOT_ID)
        -> if lastFullId == snapshot.id: return   // 0 setter calls; text update proceeds unchanged
        -> otherwise full style apply and set lastFullId = snapshot.id
```

`setNetworkSpeed` always asks for a full style. `applyNetSpeedTextStyle` short-circuits when the
same `NetSpeedTextStyleSnapshot.id` has already been fully applied to the same view, so the common
per-tick path performs zero full-style setters and never touches the text content.

### View attach / config change path

```text
NetworkSpeedView.onFinishInflate()
  -> NetSpeedStyleHook.onFinishInflate.afterHook
     -> currentOrBuildNetSpeedTextStyleSnapshot()
     -> if useClockStyle == false: applyNetSpeedTextStyle(speedView, snapshot, typefaceOnly = false)
        -> full style apply (text size, layout params, padding, translationY, typeface)
        -> set lastFullId = snapshot.id
     -> if useClockStyle == true:
        -> numberText.setTextAppearance(styleId)
        -> TextView.setTextAppearance.afterHook
           -> applyNetSpeedTextStyle(parent, snapshot, typefaceOnly = true)
              -> only restore typeface / fake-bold state
              -> set lastFullId = snapshot.id
```

```text
TextView.setTextAppearance(resId)
  -> NetSpeedStyleHook.setTextAppearance.afterHook
     -> applyNetSpeedTextStyle(parent, snapshot, typefaceOnly = true)
        -> only restore typeface / fake-bold state
        -> set lastFullId = snapshot.id
```

### Snapshot refresh path

```text
PreferenceObserver.onChange(key)
  -> if key in NETSPEED_TEXT_STYLE_PREFERENCE_KEYS:
       currentNetSpeedTextStyleSnapshot.set(null)
       currentOrBuildNetSpeedTextStyleSnapshot()   // 9 reads, cold path
```

The observer is registered with `SystemUIStatusBarHooks` as the owner. The next
`setNetworkSpeed` tick picks up the new snapshot.

## `applyNetSpeedTextStyle` `typefaceOnly` semantics

| Caller | `typefaceOnly` value | Reason | What it touches |
|---|---|---|---|
| `setNetworkSpeed` after-hook | `false` | The framework has just updated the text. The hook asks for a full style; `applyNetSpeedTextStyle` short-circuits if the view already has the current `snapshot.id` as `lastFullId`, so the common per-second case does **0** full setters. | Short-circuits, or full size/layout/gravity/padding/translationY/typeface when the view or snapshot is new. |
| `onFinishInflate` with `useClockStyle == false` | `false` | The view is brand new and needs a complete custom style. | Full style, then sets `lastFullId` and `viewInitedTag`. |
| `TextView.setTextAppearance` after-hook | `true` | The framework (or the clock-style `onFinishInflate` path) has just applied a system text appearance, which set size, color, padding and layout. We must only restore the network-speed-specific typeface and `fakeBold` state without undoing the text appearance. | Only `ensureNetSpeedTypeface`. Does **not** call `setTextSize`, `setPadding`, `setGravity`, `setTextAlignment`, `setTranslationY`, `setSingleLine`, `setMaxLines`, `setLineSpacing` or `setLayoutParams`. |

The additional instance field `NETSPEED_LAST_FULL_STYLE_SNAPSHOT_ID` stores the id of the last
snapshot that was fully applied to a `speedView`. `typefaceOnly = false` returns immediately when
this id equals the incoming `snapshot.id`; `typefaceOnly = true` never returns early because it is
called after a text-appearance change and must ensure the typeface is current.

## Preference observer lifecycle review

`netSpeedTextStyleObserver` is a single process-scoped, owner-bound observer:

- **Owner**: `SystemUIStatusBarHooks` (the module singleton). The observer is not registered against
  a `Context`, `Activity` or `View`, so it cannot hold a strong reference to a short-lived object.
- **Lifecycle**: `ModuleHelper.observePreferenceChange(..., SystemUIStatusBarHooks)` registers once
  per process. The registry stores a weak reference and drops it when the owner is collected.
- **What it holds**: a `PreferenceObserver` instance. It stores no `View`, `Context`, `Fragment` or
  controller.
- **On change**: for any key in `NETSPEED_TEXT_STYLE_PREFERENCE_KEYS` it sets
  `currentNetSpeedTextStyleSnapshot` to `null`. The next call to `currentOrBuildNetSpeedTextStyleSnapshot()`
  rebuilds the snapshot, atomically publishes it, and the next `setNetworkSpeed` tick applies it.
- **No polling, no thread, no HandlerThread, no global BroadcastReceiver**.

## `mPrefs.get*` count after optimization

| Call site | `mPrefs.get*` per hot call | Frequency |
|---|---|---|
| `setNetworkSpeed` per tick (view inited, same snapshot) | **0** | every tick |
| `applyNetSpeedTextStyle` | **0** | every tick / attach |
| `buildNetSpeedTextStyleSnapshot` | 9 | cold path: first tick, view attach, or preference change |
| `onFinishInflate` `system_netspeed_use_clock_style` | 1 | view attach only |

## Evidence

- `SystemUIStatusBarHotPathTest.buildNetSpeedTextStyleSnapshot_readsAllNineRelevantKeysAndProducesEquivalentValues`
  confirms the 9 relevant keys are read exactly once and the snapshot carries the same
  semantics as the old direct `mPrefs.get*` calls.
- `SystemUIStatusBarHotPathTest.applyNetSpeedTextStyle_100HotCalls_withSnapshot_doesNotReadPrefs`
  runs 100 per-tick style applies against a counting `PrefMap` and records 0 reads.
- `SystemUIStatusBarHotPathTest.applyNetSpeedTextStyle_100HotCalls_sameViewSameSnapshot_skipsAllSetters`
  verifies that all layout/text-size setters are applied once and skipped on the next 100
  identical ticks.
- `SystemUIStatusBarHotPathTest.applyNetSpeedTextStyle_doesNotTriggerTextChange` proves the
  hot path does not call `setText()` and therefore cannot suppress the network-speed value
  update.
- `SystemUIStatusBarHotPathTest.netSpeedTextStyleObserver_relevantKey_rebuildsSnapshotOnce`
  and `netSpeedTextStyleObserver_irrelevantKey_doesNotRebuildSnapshot` cover the
  owner-bound preference observer lifecycle.
- `SystemUIStatusBarHotPathTest.applyNetSpeedTextStyle_usesCurrentResourcesForPixelConversion`
  confirms `dp -> px` conversion uses the View's current `Resources.getDisplayMetrics()`
  rather than any cached or stale density.
- `SystemUIStatusBarHotPathTest.applyNetSpeedTextStyle_invalidValues_fallsBackSafely` checks
  invalid style/font/position values complete without crashing.
- `SystemUIStatusBarHotPathTest.setNetworkSpeedSimulation_100Ticks_zeroFullSetters_textStillUpdates`
  simulates 100 per-second `setNetworkSpeed` ticks: the first tick applies the full style once,
  the remaining 99 perform zero full-style setters, and the text content is still updated 100
  times.
- `SystemUIStatusBarHotPathTest.setTextAppearanceSimulation_typefaceOnlyRestoresTypefaceWithoutFullSetters`
  confirms the `setTextAppearance` after-hook uses `typefaceOnly = true` and only restores the
  typeface, leaving size, padding, gravity, translationY and layout untouched.
- `SystemUIStatusBarHotPathTest.applyNetSpeedTextStyle_fullSameViewSameSnapshot_secondCall_zeroSetters`
  verifies the `typefaceOnly = false` short-circuit: same view, same snapshot, the second call
  performs zero setters.
- `SystemUIStatusBarHotPathTest.callback_typefaceOnly_values` records the exact `typefaceOnly`
  argument used by each production callback path.

## Status

`ENGINEERING_COMPLETE_DEVICE_EVIDENCE_PENDING`

Engineering verification is complete:

- `NetSpeedTextStyleSnapshot` eliminates `MainModule.mPrefs` reads from the per-tick
  `applyNetSpeedTextStyle` path.
- View setter idempotency is enforced by the per-View `NETSPEED_LAST_FULL_STYLE_SNAPSHOT_ID`
  additional instance field.
- `typefaceOnly = true` for the `TextView.setTextAppearance` after-hook only restores the
  typeface and fake-bold state, never re-applying layout, padding, size or gravity.
- The preference observer has owner-bound registration, deduplication, and no strong View
  references.
- `python tools/verify.py full`, `python tools/source_hazard_scan.py --strict-all`, and
  `gradlew :app:testDebugUnitTest --tests SystemUIStatusBarHotPathTest` all pass.

Device-level PSS / USS / CPU measurements remain pending per the protocol in
`P0_RUNTIME_BASELINE_AND_AUDIT_PROTOCOL.md`.
