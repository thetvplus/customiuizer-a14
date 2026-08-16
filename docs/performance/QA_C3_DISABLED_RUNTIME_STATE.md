# QA-A14-C3 — Disabled B1/B2/B3 runtime-state gating

## Scope

Eliminate eager allocation of `AtomicReference`, `AtomicLong`, `Set`, and
`PreferenceObserver` objects for disabled SystemUI status-bar features in
`SystemUIStatusBarHooks`:

- **B1** `NetSpeedStyleHook` — network-speed text styling.
- **B2** `DetailedNetSpeedHook` — detailed network-speed text format.
- **B3** `HideIconsSignalHook`, `HideIconsHook`, `HideIconsFromSystemManager` —
  status-bar icon visibility.

The features are installed from `SystemUiBootstrapCoordinator.setupStatusBar()`
only when their install-time toggles are on. Before this change, the object
`SystemUIStatusBarHooks` initialized all of the above eagerly in `<clinit>`,
regardless of whether any of the features would be installed.

## Design

### Holders

```
SystemUIStatusBarHooks (singleton)
  ├─ netSpeedRuntimeState: NetSpeedRuntimeState?        // null until B1 or B2 installed
  │    ├─ styleState: NetSpeedStyleRuntimeState?        // null until B1 installed
  │    ├─ detailedState: DetailedNetSpeedRuntimeState?  // null until B2 installed
  │    └─ observer: ModuleHelper.PreferenceObserver     // shared by B1 and B2
  └─ iconVisibilityRuntimeState: StatusBarIconVisibilityRuntimeState?  // null until B3 installed
       ├─ currentSnapshot
       ├─ idGenerator
       ├─ relevantKeys
       └─ observer
```

- B1 and B2 share a single `NetSpeedRuntimeState` and a single
  `PreferenceObserver`. The observer is registered once on first use and owned
  by the `SystemUIStatusBarHooks` singleton.
- B3 has its own `StatusBarIconVisibilityRuntimeState`. Its observer is owned by
  the holder instance itself, so it is distinct from the B1/B2 owner.
- All `AtomicReference`, `AtomicLong`, `Set`, and `PreferenceObserver` instances
  live inside these holders. No eager top-level instances remain.

### Install-time gating

```
NetSpeedStyleHook(lpparam)
  -> ensureNetSpeedRuntimeState().ensureStyleState()

DetailedNetSpeedHook(lpparam)
  -> ensureNetSpeedRuntimeState().ensureDetailedState()

HideIconsSignalHook / HideIconsHook / HideIconsFromSystemManager
  -> ensureStatusBarIconVisibilityRuntimeState()
```

`ensure*` functions create the holder and register its observer exactly once.
`PreferenceObserverRegistry` deduplicates by owner, so repeated hook calls in
`setupStatusBar()` do not create duplicate observers.

### Hot-path builders

The package-visible builder functions remain available for tests but now require
an installed substate:

```kotlin
internal fun buildNetSpeedTextStyleSnapshot(prefs: PrefMap): NetSpeedTextStyleSnapshot
// delegates to the two-arg private builder and the installed styleState.idGenerator
```

The actual hot paths (`currentOrBuild*`, the observers, and the B1/B2 shared
observer) call the zero-allocation two-arg builders directly:

```kotlin
private fun buildNetSpeedTextStyleSnapshot(prefs: PrefMap, idGenerator: AtomicLong)
```

### B1 view-tag lifecycle

The fake-resource tags that used to be eager top-level `val`s are now fields on
`NetSpeedStyleRuntimeState`:

- `numberViewTag`
- `unitViewTag`
- `typefaceStateTag`
- `originalStyleStateTag`

These tags are created only when B1 is installed, but the fake-resource IDs are
still produced by `ResourceHooks.getFakeResId(...)` and remain stable across
process restarts, so per-view state survives correctly.

### Observer behavior preservation

- The B1/B2 shared observer still filters by `styleState.relevantKeys` and
  `detailedState.relevantKeys`. A relevant style key rebuilds the B1 snapshot; a
  relevant detailed-format key invalidates the B2 snapshot (B2 needs a `Context`
  to read module resources, so it is rebuilt lazily on the next `updateText` tick).
- The B3 observer still filters by `relevantKeys` and rebuilds the
  `StatusBarIconVisibilitySnapshot` atomically.

## Removed eager state

The following top-level fields and the `StatusBarIconVisibilityObserverOwner`
object have been removed:

- `currentNetSpeedTextStyleSnapshot`, `netSpeedSnapshotIdGenerator`,
  `netSpeedTextStyleRelevantKeys`, `netSpeedTextStyleObserver`
- `currentDetailedNetSpeedFormatSnapshot`,
  `detailedNetSpeedFormatSnapshotIdGenerator`,
  `detailedNetSpeedFormatRelevantKeys`
- `currentStatusBarIconVisibilitySnapshot`,
  `statusBarIconVisibilitySnapshotIdGenerator`,
  `statusBarIconVisibilityRelevantKeys`, `statusBarIconVisibilityObserver`,
  `StatusBarIconVisibilityObserverOwner`
- `netspeedNumberViewTag`, `netspeedUnitViewTag`, `netspeedTypefaceStateTag`,
  `netspeedOriginalStyleStateTag`

## Tests

- `SystemUIStatusBarDisabledStateTest` — verifies that all four disabled
  baselines keep `netSpeedRuntimeState` and `iconVisibilityRuntimeState` null,
  that the old top-level fields no longer exist, and that installing B1, B2, or
  B3 creates only the required substate.
- `SystemUIStatusBarHotPathTest` — covers B1 hot-path semantics, snapshot
  idempotency, observer key filtering, and view-style reversibility.
- `DetailedNetSpeedHotPathTest` — covers B2 snapshot building, low-speed hiding,
  icon modes, and B2 snapshot invalidation through the shared observer.
- `StatusBarIconVisibilityHotPathTest` — covers B3 snapshot building, all
  `checkSlot` / `computeSignalIconHiding` / `shouldHideSystemManagerIcon` paths,
  and the independence of the B3 owner.

## Verification

```powershell
python tools\verify.py fast --tests SystemUIStatusBarDisabledStateTest SystemUIStatusBarHotPathTest DetailedNetSpeedHotPathTest StatusBarIconVisibilityHotPathTest
python tools\verify.py full
```

## Device verification pending

The disabled-state gating itself is compile-time and unit-testable. The
following can only be confirmed on a real HyperOS 1 / Android 14 device:

1. SystemUI process startup with all B1/B2/B3 toggles off performs no
   preference-observer registration for these features.
2. Enabling B1, B2, or B3 at runtime (after a SystemUI restart) installs the
   correct hook and state holder exactly once.
3. Disabling B1/B2/B3, restarting SystemUI, then re-enabling them re-creates
   fresh holders without leaking the old observer or snapshot state.
