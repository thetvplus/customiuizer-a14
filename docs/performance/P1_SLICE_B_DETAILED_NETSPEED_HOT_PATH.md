# PERF-A14-P1-SLICE-B2 — Detailed network speed `updateText` hot-path preference elimination

## Scope

Only the `DetailedNetSpeedHook.updateText.before` hot path and its helper
`humanReadableByteCount` in `SystemUIStatusBarHooks`. No other status-bar features,
no sampling math, no Handler period, no `checkSlot`, no icon-hiding logic, no global
preference architecture changes, and no visual semantic changes.

## Call chain (pre-optimization)

### `DetailedNetSpeedHook.updateText.before`

```text
NetworkSpeedController.updateText(String[] text)
  -> DetailedNetSpeedHook.updateText.before
     -> MainModule.mPrefs.getBoolean("system_detailednetspeed_low")
     -> MainModule.mPrefs.getInt("system_detailednetspeed_lowlevel", 1) * 1024
     -> MainModule.mPrefs.getStringAsInt("system_detailednetspeed_style", 1)
     -> if style == 2:
          MainModule.mPrefs.getStringAsInt("system_detailednetspeed_icon", 2)
     -> humanReadableByteCount(mContext, rxSpeed)
        -> ModuleHelper.getModuleRes(mContext)
        -> MainModule.mPrefs.getBoolean("system_detailednetspeed_secunit")
        -> modRes.getString(R.string.Bs)
        -> modRes.getString(R.string.speedunits)
        -> formatNetSpeedValue(bytes / 1024.0f)
        -> return "$number$pre$unitSuffix"
     -> if style == 2:
          humanReadableByteCount(mContext, txSpeed)  // same mPrefs + resources read again
     -> build strArr[2]
     -> param.getArgs()[0] = strArr
```

This runs every time `NetworkSpeedController.updateText` is invoked, i.e. every
network-speed refresh tick (typically once per second or per configured interval).

### `humanReadableByteCount` pre-optimization

- 1 `MainModule.mPrefs.getBoolean("system_detailednetspeed_secunit")` per call.
- `ModuleHelper.getModuleRes(mContext)` per call.
- `modRes.getString(R.string.Bs)` and `modRes.getString(R.string.speedunits)` per call.
- One new `String` for `unitSuffix`.
- One new `String` for the final formatted text.

## `mPrefs.get*` count by hot call site (pre-optimization)

| Call site | `mPrefs.get*` per hot call | Notes |
|---|---|---|
| `updateText.before` (style != 2) | 4 | `low`, `lowlevel`, `style`, and `secunit` |
| `updateText.before` (style == 2) | 6 | `low`, `lowlevel`, `style`, `icon`, and two `secunit` reads (one for tx, one for rx) |
| `humanReadableByteCount` | 1 | `system_detailednetspeed_secunit` |

## Object allocation per `updateText` (pre-optimization)

- `arrayOfNulls<String>(2)`
- `txarrow`, `rxarrow` strings (style 2 only)
- 1 or 2 `humanReadableByteCount` result strings
- 1 or 2 `+ arrow` concatenated strings
- 1 final `tx\nrx` or `rx` string for `strArr[0]`
- `strArr[1] = ""` uses existing literal
- `ModuleHelper.getModuleRes(mContext)` returns a cached `Resources` (no hot allocation).

## Optimization target

1. `DetailedNetSpeedHook.updateText.before` must read `MainModule.mPrefs` **0** times after
   the first tick of a stable snapshot.
2. `humanReadableByteCount` must not read `MainModule.mPrefs`.
3. The detailed format configuration (low threshold, style, icon, unit) is parsed once and
   cached in an immutable `DetailedNetSpeedFormatSnapshot`.
4. No change to speed values, byte conversion, threshold boundaries, rounding, unit text,
   arrow text, tx/rx ordering, or single-line / dual-line layout.
5. No new Xposed Hook, thread, HandlerThread, Timer, polling, or global Receiver.
6. No strong reference to a View, Context, or controller in the snapshot or observer.
7. SystemUI process rebuild re-initializes the snapshot correctly.

## Post-optimization call chain

### `DetailedNetSpeedHook.updateText.before`

```text
NetworkSpeedController.updateText(String[] text)
  -> DetailedNetSpeedHook.updateText.before
     -> context = XposedHelpers.getObjectField(controller, "mContext")
     -> modRes = ModuleHelper.getModuleRes(context)          // cached, no mPrefs read
     -> snapshot = currentOrBuildDetailedNetSpeedFormatSnapshot()  // 5 reads only on first call / invalidation
     -> formatDetailedNetSpeedText(txSpeed, rxSpeed, snapshot, modRes)
        -> arrow strings (style 2 only)
        -> humanReadableByteCount(rxSpeed, snapshot, modRes)
        -> if style == 2: humanReadableByteCount(txSpeed, snapshot, modRes)
        -> return arrayOf<String?>(text, "")
     -> param.getArgs()[0] = formatted
```

### `humanReadableByteCount` (post-optimization)

```text
humanReadableByteCount(bytes, snapshot, modRes)
  -> unitSuffix = if (snapshot.hideSecUnit) "" else modRes.getString(R.string.Bs)
  -> f = bytes / 1024.0f
  -> if f > 999.0f { f /= 1024.0f; expIndex = 1 } else expIndex = 0
  -> pre = modRes.getString(R.string.speedunits)[expIndex]
  -> number = formatNetSpeedValue(f)
  -> StringBuilder().append(number).append(pre).append(unitSuffix).toString()
```

No `MainModule.mPrefs` access; no `Context` access; no `View` access.

### Snapshot refresh path

```text
PreferenceObserver.onChange(key)
  -> if key in netSpeedTextStyleRelevantKeys:
       currentNetSpeedTextStyleSnapshot.set(buildNetSpeedTextStyleSnapshot(...))   // B1
  -> if key in detailedNetSpeedFormatRelevantKeys:
       currentDetailedNetSpeedFormatSnapshot.set(null)   // invalidated
       // next updateText tick will rebuild with the controller's Resources
```

The same shared observer is registered with `SystemUIStatusBarHooks` as owner.
`DetailedNetSpeedHook` also calls `ModuleHelper.observePreferenceChange(..., SystemUIStatusBarHooks)`;
`PreferenceObserverRegistry` replaces any existing observer for the same owner and keeps only
one weak reference, so duplicate installation is impossible and the observer is never bound to
a View or controller.

## `DetailedNetSpeedFormatSnapshot` fields

| Field | Source key | Default | Purpose |
|---|---|---|---|
| `id` | generated | - | Monotonic snapshot id for identity / rebuild detection. |
| `hideLow` | `system_detailednetspeed_low` | `false` | Whether to hide speeds below the low threshold. |
| `lowLevelBytes` | `system_detailednetspeed_lowlevel` | `1 * 1024` | Low-level threshold in bytes per second. |
| `speedStyle` | `system_detailednetspeed_style` | `1` | `1` = single-line (rx only), `2` = dual-line (tx + rx). |
| `icons` | `system_detailednetspeed_icon` | `2` | Icon family for tx/rx arrows when `speedStyle == 2`. |
| `hideSecUnit` | `system_detailednetspeed_secunit` | `false` | Whether to omit the `/s` suffix. |

The snapshot stores only parsed preference values. It does not store a `View`, `Context`,
`Resources`, or controller. Module resource strings are read from the caller-supplied `modRes`
inside `humanReadableByteCount` so locale/configuration changes are naturally reflected when
`ModuleHelper.getModuleRes` returns a new `Resources` on the next build.

## Relevant preference keys

- `system_detailednetspeed_low`
- `system_detailednetspeed_lowlevel`
- `system_detailednetspeed_style`  (also B1 key)
- `system_detailednetspeed_icon`
- `system_detailednetspeed_secunit`

## `mPrefs.get*` count after optimization

| Call site | `mPrefs.get*` per hot call | Frequency |
|---|---|---|
| `updateText.before` | **0** | every tick |
| `humanReadableByteCount` | **0** | every call |
| `formatDetailedNetSpeedText` | **0** | every call |
| `buildDetailedNetSpeedFormatSnapshot` | 5 | cold path: first tick, or preference change |

## Object allocation after optimization

- `currentOrBuildDetailedNetSpeedFormatSnapshot()` returns an existing immutable snapshot
  (0 allocation) in the common case.
- `ModuleHelper.getModuleRes(context)` returns a cached `Resources` (0 allocation unless
  configuration changed).
- `formatDetailedNetSpeedText` creates:
  - one `Array<String?>(2)`
  - arrow strings when `speedStyle == 2`
  - 1 or 2 `humanReadableByteCount` result strings
  - 1 final `tx\nrx` or `rx` string
- `humanReadableByteCount` creates:
  - one `StringBuilder`
  - one final `String`
  - `modRes.getString` returns cached strings (no hot allocation)
- No `Pair`, `Triple`, `List`, `Formatter`, or unbounded cache is created.

## Observer lifecycle

- **One shared observer**: `netSpeedTextStyleObserver` in `SystemUIStatusBarHooks`.
- **Owner**: `SystemUIStatusBarHooks` module singleton — not a `View`, `Activity`, or controller.
- **Registered by**: `NetSpeedStyleHook` and `DetailedNetSpeedHook` both call
  `ModuleHelper.observePreferenceChange(netSpeedTextStyleObserver, SystemUIStatusBarHooks)`.
  The registry replaces any existing observer for the same owner, so only one active observer
  reference exists per process.
- **Weak reference**: `PreferenceObserverRegistry` stores a `WeakReference`; the owner can be
  garbage-collected on SystemUI rebuild.
- **Key filtering**: `onChange(key)` returns immediately for keys not in B1 or B2 relevant sets.
- **B1 key changes**: rebuilds `NetSpeedTextStyleSnapshot` and atomically publishes it.
- **B2 key changes**: sets `currentDetailedNetSpeedFormatSnapshot` to `null`; the next
  `updateText` tick rebuilds it with the current controller `Resources`.

## Format equivalence tests

`DetailedNetSpeedHotPathTest` covers:

- `0 B/s`
- sub-KB values (0.5 K, 0.9 K)
- KB boundary (1.0 K, 999 K)
- MB boundary (1.0 M, 999 M)
- GB / single-division preservation (2 GB still shows as 2048 M, matching old logic)
- `Long.MAX_VALUE`
- negative input fallback
- `hideSecUnit = true`
- single-line rx-only mode
- dual-line tx+rx mode
- `hideLow` with single-line rx below threshold
- `hideLow` with dual-line one side below threshold
- icon mode 3 (chess pieces)
- default and custom snapshots

## Hot-path / lifecycle tests

- `buildDetailedNetSpeedFormatSnapshot_readsAllRelevantKeysOnce` — 5 mPrefs reads on cold build.
- `humanReadableByteCount_100Calls_zeroPrefReads` — 100 calls, 0 mPrefs reads.
- `formatDetailedNetSpeedText_100Calls_zeroPrefReads` — 100 calls, 0 mPrefs reads.
- `netSpeedTextStyleObserver_irrelevantKey_keepsDetailedSnapshot` — unrelated keys do not
  invalidate the B2 snapshot.
- `netSpeedTextStyleObserver_relevantKey_invalidatesDetailedSnapshot` — B2 key change sets
  the snapshot to `null`; the next `buildDetailedNetSpeedFormatSnapshot` reads all 5 keys once.
- `buildDetailedNetSpeedFormatSnapshot_atomicPublication` — repeated builds produce new `id`
  but identical logical fields.

## Source hazard

Baseline refresh was required because the B2 code insertion shifted line numbers and changed
one local variable name in `DetailedNetSpeedHook.updateText` (`val mContext` -> `val context`).

| Metric | Value |
|---|---|
| Pre-refresh baseline | 1014 reviewed findings |
| Post-refresh baseline | 1013 reviewed findings |
| Net change | -1 |
| `--strict-all` after refresh | 0 new findings |

Classification:

- **Removed**: 1 `STATIC_STRONG_ANDROID_OWNER` finding for the old `val mContext =` in the
  refactored `updateText` hook (renamed to `context`).
- **Moved / refreshed**: existing historical findings, including the defensive
  `catch (t: Throwable)` in `humanReadableByteCount` and the `val mContext =` in
  `handleMessage`, shifted to new line numbers because of the code added above them.
- **Added**: no new `Throwable` catch, static Android owner, empty catch, or `printStackTrace`
  patterns were introduced by B2. The snapshot, observer, and formatting helpers do not catch
  `Throwable` or hold `Context`/`View` strong references.

## Status

`ENGINEERING_COMPLETE_DEVICE_EVIDENCE_PENDING`

Engineering verification is complete:

- `DetailedNetSpeedFormatSnapshot` removes all `MainModule.mPrefs` reads from the
  per-tick `updateText` and `humanReadableByteCount` hot path.
- `humanReadableByteCount` is a pure function of the rate, snapshot and module `Resources`;
  it no longer reads `mPrefs`, `Context`, `View`, or reflection.
- The existing owner-bound preference observer is reused; `DetailedNetSpeedHook` registers it
  as well, and the registry deduplicates by owner.
- `python tools/verify.py full`, `python tools/source_hazard_scan.py --strict-all`,
  `python tools/audit-feature-semantics.py --validate` and `git diff --check` all pass.
- `gradlew :app:testDebugUnitTest` passes for both `SystemUIStatusBarHotPathTest` (B1)
  and `DetailedNetSpeedHotPathTest` (B2).

Device-level PSS / USS / CPU measurements remain pending per the protocol in
`P0_RUNTIME_BASELINE_AND_AUDIT_PROTOCOL.md`.
