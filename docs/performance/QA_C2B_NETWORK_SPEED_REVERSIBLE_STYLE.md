# QA-A14-C2B — NetworkSpeed reversible styling

## Scope

Reversible, per-view style handling for the SystemUI `NetworkSpeedView` (B1)
in `SystemUIStatusBarHooks`.  This covers the `setNetworkSpeed` per-tick path,
`onFinishInflate`, the `TextView.setTextAppearance` after-hook, and the optional
"use clock style" bootstrap path.  It does **not** cover other status-bar
features, clock styling, or icon visibility.

## Call chains

### `setNetworkSpeed` per tick (hot path)

```
NetworkSpeedView.setNetworkSpeed hook
  -> setNetworkSpeedText(speedView, "...")
       -> currentOrBuildNetSpeedTextStyleSnapshot()
       -> applyNetSpeedTextStyle(speedView, snapshot, typefaceOnly = false)
            -> getNetSpeedOriginalStyleState(speedView, numberText, unitText)
            -> apply custom style or restore original fields
            -> if (layoutParamsReady) set snapshot id on view
```

- The first call with a given `snapshot.id` performs the full style work.
- Subsequent calls with the same `snapshot.id` are no-ops (idempotency).
- `MainModule.mPrefs` is **not** read on the hot path.

### `onFinishInflate` (cold path)

```
NetworkSpeedView.onFinishInflate hook
  -> onNetworkSpeedViewInflated(speedView)
       -> create typeface state tags on number/unit TextViews
       -> if (useClockStyle) {
              styleId = speedView.resources.getIdentifier("TextAppearance.StatusBar.Clock", ...)
              if (styleId != 0) numberText.setTextAppearance(styleId)
              if (styleId != 0 && speedStyle == 1) unitText?.setTextAppearance(styleId)
          }
       -> applyNetSpeedTextStyle(speedView, snapshot, typefaceOnly = false)
```

- The full NetworkSpeed style is always applied after the optional clock-style
text appearance, so the captured baseline may be the clock-styled appearance but
the final visible state is the custom NetworkSpeed style.

### `TextView.setTextAppearance` after-hook

```
TextView.setTextAppearance hook
  -> onNetworkSpeedTextAppearanceChanged(textView)
       -> state = textView.getTag(netspeedTypefaceStateTag)
       -> parent = textView.parent as? LinearLayout
       -> state.base = textView.typeface
       -> ensureNetSpeedTypeface(textView, snapshot.bold)
       -> remove full-style snapshot id from parent
       -> original-style tag on parent remains
```

- This path only restores the network-speed typeface / fake-bold state.
- It deliberately does **not** re-apply size, padding, gravity, or layout.
- It invalidates the cached full-style snapshot id so the next full apply
  re-applies the custom NetworkSpeed style, while the original baseline stays
  captured for the lifetime of the NetworkSpeedView.

## Original style capture

`getNetSpeedOriginalStyleState(speedView, numberText, unitText)` is called once
per `NetworkSpeedView` instance, on the first full `applyNetSpeedTextStyle` that
reaches the layout-block.

- Before capture, `applyNetSpeedTextStyle` returns immediately if
  `numberText.layoutParams == null`.  No guessed `LinearLayout.LayoutParams` is
  created, no original state is stored, and the full snapshot id is not written.
  The next call with the same snapshot id will retry once real LayoutParams are
  attached.
- The original state is stored as a view tag on `speedView` keyed by
  `ResourceHooks.getFakeResId("netspeed_original_style_state")`.
- The state is an immutable `NetSpeedOriginalStyleState` snapshot.
- It holds no strong `View`, `Context`, `Resources`, `Activity`, `Controller`,
  `LayoutParams`, or parent references.
- Captured fields include:
  - `speedView`: `translationY`, `paddingStart`, `paddingTop`, `paddingEnd`,
    `paddingBottom`.
  - `numberText`: `textSize`, `gravity`, `textAlignment`, `isSingleLine`,
    `maxLines`, `lineSpacingExtra`, `lineSpacingMultiplier`, and all
    `LinearLayout.LayoutParams` fields (width, height, weight, gravity,
    left/right/top/bottom margins, margin start/end).
  - `unitText`: `visibility`, `textSize`, `textAlignment`.

## Snapshot completed-ID timing

`applyNetSpeedTextStyle` writes the snapshot id to `speedView` only when:

1. The current `numberText.layoutParams` is a `LinearLayout.LayoutParams` (or
   null) when layout params need to change (`singleOrDual` or `fixedWidth > 10`).
2. No setter or `LayoutParams` operation threw a non-fatal exception.

If a setter throws, the exception is logged and the snapshot id is **not**
recorded. The next call with the same `snapshot.id` will retry.  A successful
call advances the id; subsequent calls with the same id are no-ops.

## LayoutParams handling rules

- A new `LinearLayout.LayoutParams` is created only when the current params are
  `LinearLayout.LayoutParams` or null and a layout change is required.
- The new instance copies the original `weight`, `gravity`, and margins.
- `width` is set to the dp2px-converted `fixedWidth` when `fixedWidth > 10`;
  otherwise the original width is preserved.
- `height` is set to `MATCH_PARENT` for dual-row styles (`speedStyle == 2` or
  `3`); otherwise the original height is preserved.
- `topMargin` and `bottomMargin` are forced to `0` for dual-row styles; other
  margins are preserved from the original state.
- If the current `LayoutParams` are not compatible with `LinearLayout.LayoutParams`
  and a layout change is required, the snapshot id is not recorded and the
  operation will be retried on the next call.

## Failure retry

```
applyNetSpeedTextStyle(speedView, snapshot)
  -> lastId != snapshot.id
  -> try:
       original = getNetSpeedOriginalStyleState(...)
       ... apply all setters ...
       if (layoutParamsReady) set lastId = snapshot.id
     catch (non-fatal t):
       log(t)
```

- A failed call does not advance `lastId`.
- The next call with the same `snapshot.id` will pass the idempotency check and
  retry.
- A successful call advances `lastId`; subsequent calls with the same id are
  no-ops.

## View tag lifecycle

- The original-style tag lives on the `speedView` instance.
- It is set once, during the first successful full-style apply.
- The snapshot id is stored through `XposedHelpers` additional instance fields
  (not a view tag), so it is garbage-collected with the view.
- The per-TextView `NetSpeedTypefaceState` tags are set by
  `onNetworkSpeedViewInflated` and updated by the typeface-only path.

## Style transitions and reversibility

Custom style values map to fixed `View` constants where applicable:

| Preference | Applied value |
|---|---|
| `system_detailednetspeed_align == 2` | `TEXT_ALIGNMENT_TEXT_START` |
| `system_detailednetspeed_align == 3` | `TEXT_ALIGNMENT_CENTER` |
| `system_detailednetspeed_align == 4` | `TEXT_ALIGNMENT_TEXT_END` |
| other / default | original captured `textAlignment` |
| `system_detailednetspeed_style == 2` or `3` | `Gravity.CENTER_VERTICAL \| Gravity.START`, `maxLines = 2`, custom line spacing |
| `system_detailednetspeed_style == 1` | original captured `gravity`, `singleLine`, `maxLines`, line spacing, unit visible |

When a different style snapshot is applied, `applyNetSpeedTextStyle` restores
each property from the captured original state before re-applying the new custom
values.  This makes the transformation fully reversible for the B1 network-speed
view without affecting other status-bar components.

## Use-clock-style path

When `system_netspeed_use_clock_style` is enabled:

- The framework clock-style text appearance is applied to the number and unit
  TextViews first.
- The full NetworkSpeed custom style is applied afterwards.
- The `setTextAppearance` after-hook then only restores the network-speed
  typeface and invalidates the full-style snapshot id.  The per-view
  `NetSpeedOriginalStyleState` is **not** cleared.

This ensures the captured baseline can be the clock-styled appearance while the
visible result is the user's NetworkSpeed configuration, and that later
`TextAppearance` callbacks do not replace the original baseline with the custom
NetworkSpeed style.

## Null LayoutParams guard

`applyNetSpeedTextStyle` defers the first full-style apply when
`numberText.layoutParams == null`:

- No original state is captured.
- No `LinearLayout.LayoutParams` is created from guessed values.
- No full-style setters are applied.
- The full snapshot id is not written and `viewInitedTag` is not set.
- The next call with the same `snapshot.id` retries once the framework has
  provided a real `LinearLayout.LayoutParams`.

This prevents a guessed `WRAP_CONTENT` / zero-margin baseline from replacing the
real stock baseline on early, not-yet-inflated views.

## Configuration / theme refresh

For future ROM or theme changes that update text size, fontScale, or padding via
a fresh `TextAppearance` callback, this implementation:

- Only refreshes the typeface baseline through `NetSpeedTypefaceState`.
- Keeps the `NetSpeedOriginalStyleState` once captured for the lifetime of the
  view.

Re-capturing the whole original state on every `TextAppearance` change is
considered a configuration-rebuild concern that requires device evidence.
Documented as `DEVICE / CONFIGURATION REBUILD VALIDATION PENDING`.

## Feature-boundary gating

- The network-speed observer (`netSpeedTextStyleObserver`) only rebuilds the
  snapshot when one of the relevant network-speed preference keys changes.
- `applyNetSpeedTextStyle` does not read `MainModule.mPrefs` directly; it uses
  the pre-built `NetSpeedTextStyleSnapshot`.
- The hot path (`setNetworkSpeed`) performs zero full-style setters after the
  first successful apply for a given snapshot id.

## Device verification pending

The following items can only be confirmed on a real HyperOS 1 / Android 14
device:

1. Network speed view reverts to the stock appearance when all custom style
   preferences are disabled or the master toggle is off.
2. Dual-row (`speedStyle == 2`) and single-unit-hidden (`speedStyle == 3`)
   layouts render and revert correctly across styles.
3. Fixed-width conversion uses the live `Resources` display metrics.
4. `setTextAppearance` after-hook does not cause the custom style to regress
   after the framework updates text appearance.
5. Preference changes at runtime refresh the style without a SystemUI restart.
6. View re-creation (rotation, theme change) re-applies the correct snapshot.
