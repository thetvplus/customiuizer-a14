# QA-A14-C2A — SystemClock reversible styling

## Scope

Reversible, per-view style handling for the SystemUI clock in `SystemClockHooks`.
This covers the status-bar clock (`clock`), the control-center clock (`ccClock`),
the preference-refresh path, and the `onDarkChanged` callback. It does **not**
cover other status-bar features, network speed, or icon visibility.

## Call chains

### Status-bar clock

```
MiuiPhoneStatusBarView.onAttachedToWindow hook
  -> initClockStyle(mClock, "clock")
       -> getOrCaptureOriginalStyle(mClock)
       -> applyClockStyle(mClock, "clock", snapshot, original)
       -> if (applied) set snapshot id on view

PreferenceObserver.onChange (relevant clock key)
  -> refreshClockStyleSnapshot(res)
  -> for each clock / ccClock listener:
       if (shouldRefreshClockStyle(clockName, statusbarClockTweak, ccClockTweak)) {
           initClockStyle(listener, clockName, freshSnapshot)
       }
       XposedHelpers.callMethod(listener, "updateTime")
  -> initSecondTicker(controller, statusbarClockTweak, ccClockTweak)
```

### Control-center clock

```
MiuiClock constructor hook
  -> setViewInfo(clockName, "ccClock")
  -> initClockStyle(clock, "ccClock", snapshot)
       -> getOrCaptureOriginalStyle(clock)
       -> applyClockStyle(clock, "ccClock", snapshot, original)
            -> only line spacing / single-line / max-lines are touched
```

Status-bar-only style properties (`textSize`, `typeface`, `textColor`,
`textAlignment`, `translationY`, `background`, `LayoutParams`) are **not**
applied to `ccClock`.

### onDarkChanged

```
MiuiClock.onDarkChanged hook (installed when statusbarClockTweak is true)
  -> shouldSuppressDarkChange(clockName)
       -> clockName == "clock"?
       -> currentClockStyleSnapshot()?.statusbarChip == true
          && (statusbarChipUseMonet || statusbarChipCustomTextColor)
  -> if (suppress) return null else chain.proceed()
```

The dark callback **must not** read `PrefMap`. It uses the cached
`ClockStyleSnapshot`, which is refreshed by the preference observer.

## Original style capture

`getOrCaptureOriginalStyle(view)` is called once per view, on the first
status-bar `initClockStyle` call that has a non-null `LayoutParams` and reaches
`applyClockStyle`.

- The original state is stored as a view tag keyed by
  `ResourceHooks.getFakeResId("clock_original_style_state")`.
- The state is an immutable `ClockOriginalStyleState` snapshot.
- It holds no strong `View`, `Context`, `Resources`, `Activity`, `Controller`, or
  `LayoutParams` references.
- Captured fields: `textSizePx`, `typeface`, `textColors`, `textAlignment`,
  `translationY`, `background`, `isSingleLine`, `maxLines`, `lineSpacingExtra`,
  `lineSpacingMultiplier`, `layoutParamsWidth`, `layoutParamsHeight`,
  `layoutParamsGravity`, and all four margins.

## Snapshot completed-ID timing

`initClockStyle` writes the snapshot id to the view only when:

1. `applyClockStyle` returns `true` (no required layout operation was skipped).
2. No setter or background/chip builder threw a non-fatal exception.

If a setter throws, the exception is logged and `initClockStyle` returns without
recording the snapshot id. The next call with the same snapshot id will retry.
The same applies when a required `LayoutParams` operation cannot be completed
(e.g. the view has no layout params, or the current `LayoutParams` type does not
support the requested margin/gravity).

## LayoutParams handling rules

`applyClockStyle` treats `LayoutParams` by type and availability:

- If `mClock.layoutParams == null` and any layout-affecting option is active
  (`chip`, horizontal margin, fixed width), the snapshot is **not** marked
  complete.
- Width and height are set on the existing `LayoutParams` instance.
- `gravity` is set only on `LinearLayout.LayoutParams`.
- Margins are set only on `ViewGroup.MarginLayoutParams`.
- If a required `gravity` or margin cannot be applied, the snapshot is **not**
  marked complete, but the parts that can be applied are still written to the
  existing `LayoutParams`.
- `LayoutParams` are never guessed or created when missing.

## Failure retry

```
initClockStyle(view, "clock", snapshot)
  -> lastId != snapshot.id
  -> try:
       original = getOrCaptureOriginalStyle(view)
       applied  = applyClockStyle(view, "clock", snapshot, original)
       if (applied) set lastId = snapshot.id
     catch (non-fatal t):
       log(t)
```

- A failed call does not advance `lastId`.
- The next call with the same `snapshot.id` will pass the idempotency check and
  retry.
- A successful call advances `lastId`; subsequent calls with the same id are
  no-ops.

## View tag lifecycle

- The original-style tag lives on the `TextView` instance.
- It is set once, during the first successful capture.
- The snapshot-id is stored through `XposedHelpers` additional instance fields
  (not a view tag), so it is garbage-collected with the view.
- `SecondTicker` is unchanged and holds only a `WeakReference` to the controller.

## Alignment semantics

`statusbarAlign` values map to fixed `View.TEXT_ALIGNMENT_*` constants:

| Preference value | Applied alignment |
|---|---|
| 2 | `TEXT_ALIGNMENT_TEXT_START` |
| 3 | `TEXT_ALIGNMENT_CENTER` |
| 4 | `TEXT_ALIGNMENT_TEXT_END` |
| other / default | original captured alignment |

## Chip and dark-mode interaction

When the status-bar clock has a chip with Monet or a custom text color,
`onDarkChanged` is suppressed for the `clock` view. This prevents the framework
from overriding the chip text color. When the chip is off or the snapshot is
not ready, `onDarkChanged` proceeds normally.

## Feature-boundary gating

Runtime style refresh is gated by the install-time feature switches:

```
shouldRefreshClockStyle("clock",  statusbarClockTweak, ccClockTweak) = statusbarClockTweak
shouldRefreshClockStyle("ccClock", statusbarClockTweak, ccClockTweak) = ccClockTweak
shouldRefreshClockStyle(else, ...)                                  = false
```

The `PreferenceObserver` uses `shouldRefreshClockStyle` before calling
`initClockStyle`. `updateTime()` is still called for every listener so the
framework's normal time update path is preserved. The master tweak toggle still
requires a SystemUI restart to take effect.

## Effective seconds flags

`initSecondTicker` receives `statusbarClockTweak` and `ccClockTweak`. The
effective seconds flags are:

```
effectiveStatusBarSeconds = statusbarClockTweak && snapshot.showStatusBarSeconds
effectiveCcSeconds        = ccClockTweak        && snapshot.showCCSeconds
```

`initSecondTicker` sets `showSeconds` view tags and creates/removes the
`SecondTicker` only from these effective flags. A disabled feature's stale
seconds preference can therefore never create a ticker or mark a view as needing
one-second updates. `SecondTicker` stores the effective flags, not the raw
snapshot values.

## Status-bar null LayoutParams defence

For the status-bar clock (`clockName == "clock"`):

- If `mClock.layoutParams == null`, `initClockStyle` returns immediately.
- No `ClockOriginalStyleState` is captured.
- No snapshot id is written.
- No text or layout setters are applied.
- Once a real `LayoutParams` instance is present, the same snapshot id retries,
  captures the real original layout, and applies the style fully.

This prevents the framework's later `LayoutParams` from being overwritten by a
guessed `WRAP_CONTENT / 0` original state.

## Device verification pending

The following items can only be confirmed on a real HyperOS 1 / Android 14
device:

1. Status-bar clock reverts to the stock appearance when all custom style
   preferences are disabled.
2. Chip background, padding, and corner radius render correctly across light
   and dark themes.
3. `onDarkChanged` suppression does not interfere with other `MiuiClock`
   instances (ccClock, date views).
4. Preference changes at runtime refresh the style without a SystemUI restart.
5. View attachment and re-attachment after rotation re-apply the correct
   snapshot.
6. `statusbarClockTweak` or `ccClockTweak` disabled at startup prevents stale
   seconds preferences from creating CPU work.
7. Status-bar clock `LayoutParams` null on first `onAttachedToWindow` and
   correct after retry.
