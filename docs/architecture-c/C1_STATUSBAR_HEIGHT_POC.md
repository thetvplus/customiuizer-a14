# C1 StatusBarHeight Architecture-C PoC

## 1. Identity

| Item | Value |
|------|-------|
| Branch | `devin/a14-architecture-c-r14.20.0` |
| C0 baseline SHA | `7eb129cd7b58e1113a0713b5149978afff5087d9` |
| Behavior oracle SHA | `2c4efeafc8655855b824b72ecbf6106641b04a8e` |
| C1 production-code candidate SHA | `7fb1ca5d7b631a2410e92eb41a571b62c3fc6de9` |
| versionCode | `198` |
| versionName | `r14.20.0` |
| JDK | `25.0.4` (OpenJDK) |

## 2. Scope

C1 is a focused proof-of-concept for the `SystemStatusBarInsets` / `StatusBarHeight` subsystem only. It does **not** cover:

- Clock / NetSpeed / other status bar tweaks
- ProcessKernel / global ProcessConfig
- Feature plan generation
- Policy-wide splits beyond what was necessary for the frozen ABI
- Java → Kotlin wholesale migration
- Rust or native code
- Performance speculative cleanup

The goal was to migrate the status-bar height path from per-callback dynamic reflection to a single cold-resolved immutable ABI while preserving behavior and fatal-error identity.

## 3. Before (r14.18.8 / oracle)

The r14.18.8 path performed per-callback reflection and mixed `Config` reads:

- H1 `InsetsSource.setFrame` — preferred a cached `mType` `Field.getInt`; when that field was unavailable, the callback fell back to a generic `XposedHelpers.callMethod(source, "getType")`.
- H2 `DisplayPolicy.layoutWindowLw` — `WindowState` attrs/type/height/packageName, display metrics and display id were discovered per layout.
- H3 `WindowState.setFrames` — the declared methods of `WindowState.setFrames` were scanned; the first candidate whose first parameter's simple name was `ClientWindowFrames` was selected. Metrics and display id used generic `XposedHelpers`.
- H4 `DecorInsets.Info.update` — `nonDecorInsets`/`nonDecorFrame` and `DisplayContent.getDisplayMetrics()` were generic.
- Refresh / preference-change — `WindowState.mDisplayContent`/`mWmService`, `DisplayPolicy.mDecorInsets`, `WindowSurfacePlacer.requestTraversal()` were looked up per traversal.

This mixed discovery with hot execution, making behavior ROM-order dependent and error handling non-uniform.

## 4. After architecture

```text
Cold Resolver
  → one immutable StatusBarHeightAbi (Insets, WindowManager, Decor, Refresh)

Then:

H1:
  captures InsetsSourceCapability directly

H2/H3/H4/Refresh:
  use StatusBarHeightEffect wrapping the aggregate ABI

Config:
  single immutable State publication

Runtime:
  bounded weak owner identity state
```

This distinction is intentional: H1 keeps the hottest boundary minimal.

No runtime reflection discovery, no generic `XposedHelpers` member access in the hot graph (N3 `additional-instance` API is the only retained `XposedHelpers` state API).

## 5. Resolved ABI

Resolved once at install time by `StatusBarHeightResolver.resolveCore(classLoader)`.

### 5.1 Insets

| Member | Type | Notes |
|--------|------|-------|
| `android.view.InsetsSource` | `Class` | source class |
| `mType` | `Field` | preferred type reader |
| `getType()` | `Method` | fallback type reader |
| `getId()` | `Method` | source id reader; rare generation-gated diagnostic |
| `getFrame()` | `Method` | resolved ABI, not part of H1 steady-state mutation |
| `setFrameOneArg` | `Boolean` | `true` if `setFrame(Rect)` overload exists |
| `setFrameFourArg` | `Boolean` | `true` if `setFrame(int,int,int,int)` overload exists |
| Type constants (status bar / nav / cutout) | `int` | normalized to public or legacy encoding |

The hook is registered by method name `setFrame`; the callback rewrites arguments before `proceed()`. If the one-argument overload is available and the source needs to change, the callback allocates one `Rect` copy and one argument array. If the four-argument overload is available, it allocates one argument array.

### 5.2 WindowManager

| Member | Type | Notes |
|--------|------|-------|
| `com.android.server.wm.WindowState` | `Class` | owner class |
| `mAttrs` | `Field` | `WindowManager.LayoutParams` |
| `mDisplayContent` | `Field` | H2/H3 `WindowState` metrics fallback; Refresh `DisplayContent` traversal/invalidation access |
| `mWmService` | `Field` | for refresh traversal |
| `mWindowFrames` | `Field` | for H2 frame |
| `getFrame()` | `Method` | H2 frame access |
| `getDisplayMetrics()` | `Method` | H2/H3 metrics |
| `getDisplayId()` | `Method` | default vs secondary display |
| `ClientWindowFrames.frame` | `Field` | H3 frame mutation |
| `WindowManager.LayoutParams.type` / `height` / `packageName` | `Field` | owner identification / mutation |
| `com.android.server.wm.DisplayPolicy` | `Class` | H2 `layoutWindowLw` hook capability; Refresh `mDecorInsets` invalidation chain |

### 5.3 Decor insets

| Member | Type | Notes |
|--------|------|-------|
| `DisplayPolicy$DecorInsets$Info` | `Class` | H4 info class |
| `update(DisplayContent, int, int, int)` | `Method` | H4 callback method |
| `mNonDecorInsets` | `Field` | H4 top inset mutation |
| `mNonDecorFrame` | `Field` | H4 frame mutation |
| `DisplayContent.getDisplayMetrics()` | `Method` | H4 secondary-display metrics |

### 5.4 Refresh

| Member | Type | Notes |
|--------|------|-------|
| `WindowManagerService.mWindowPlacerLocked` | `Field` | traversal owner |
| `WindowSurfacePlacer.requestTraversal()` | `Method` | traversal request |
| `DisplayContent.getDisplayPolicy()` | `Method` | policy reader for invalidation |
| `DisplayPolicy.mDecorInsets` | `Field` | decor insets reader |
| `DecorInsets.invalidate()` | `Method` | rotation cache invalidation |

All four capabilities live in the single immutable `StatusBarHeightAbi` aggregate. There is no second `@Volatile` ABI, no `LateAbiSlot`, and no late-freeze path.

## 6. Config publication

`StatusBarHeightConfig` exposes a single authoritative state:

```kotlin
@Volatile
private var state: State = ...
```

`State` is an immutable `data class`. `currentState()` returns the existing reference:

```text
no explicit allocation of a new snapshot on the hot path
```

`configure` / `reconfigure` / `recomputePx` are the only mutation sites. They are `synchronized` and:

- bump `generation` exactly by one on an effective change;
- publish one new `State` reference;
- leave the old reference untouched.

Compatibility getters (`enabled`, `configuredPx`, `configuredDp`) still exist but are not used by the H1-H4 functional hot graph.

## 7. Runtime ownership graph

`StatusBarHeightRuntime` is an `internal class` held by the facade, not a singleton. It does not own `WindowState` strongly.

```text
Runtime
  ├─ @Volatile WeakReference<Any>? latestKnownStatusBar
  ├─ @Volatile Array<WeakReference<Any>?> knownOwners (length MAX_TRACKED = 4)
  ├─ typeMatchObserved: Boolean
  ├─ fallbackProbeBudget: AtomicInteger(4096)
  └─ lastRefreshGeneration: AtomicLong(-1)
```

- `isKnownStatusBar` and `markLatestIfKnown` do a volatile acquire + bounded `<= 4` identity compares, no lock, no allocation.
- `rememberStatusBar` is rare, synchronized, compacts dead refs, evicts oldest at capacity, and allocates one `WeakReference`.
- `latestKnownStatusBar` is a retained `WeakReference` reused for refresh.

No strong Android owner is retained in the subsystem.

## 8. H1 graph

```text
SetFrameCallback(captured InsetsSourceCapability)
  ├─ one StatusBarHeightConfig.currentState()
  ├─ disabled/type/px cheap rejects
  ├─ frozen mType Field.getInt
     OR frozen getType Method.invoke
  ├─ status source only:
  │    read/validate args
  ├─ unchanged:
  │    chain.proceed()
  └─ changed:
       ├─ Rect overload: one Rect copy + one argument array
       ├─ four-int overload: one argument array
       ├─ chain.proceed(adjusted)
       └─ original exception propagates

Rare generation-gated diagnostic:
  └─ frozen getId Method.invoke
```

- One `currentState()` per callback.
- `StatusBarHeightEffect` is **not** read by the H1 production callback.
- `getFrame()` is **not** used by the H1 mutation graph.
- No `PrefMap`, `MainModule.mPrefs`, or `StatusBarHeightResolver` in the hot path.
- No dynamic `getType()` fallback; the type reader is frozen at install.

## 9. H2 graph

```text
LayoutWindowCallback(captured Effect)
  └─ onLayoutWindowLw(chain, effect)
       ├─ win = chain.getArg(0)
       ├─ effect.isWindowState(win)
       ├─ one StatusBarHeightConfig.currentState()
       ├─ disabled:
       │    Runtime.markLatestIfKnown
       │    known  → restore original height
       │    unknown → proceed
       └─ enabled:
            ├─ facade isStatusBarWindow(win, effect)
            │    Runtime.markLatestIfKnown
            │    OR frozen attrs/type fields
            │    OR bounded packageName/toString fallback
            │    Runtime.rememberStatusBar on discovery
            ├─ frozen metrics
            ├─ frozen displayId
            ├─ local/default configuredPx
            ├─ N3: XposedHelpers.getAdditionalInstanceField / setAdditionalInstanceField (only for originalHeight)
            ├─ frozen height Field mutation
            └─ chain.proceed()
```

- One `currentState()` per callback.
- All `WindowState` / `LayoutParams` / `Display` members are frozen fields/methods.
- `isStatusBarWindow` does not perform runtime member discovery; it uses the frozen ABI and a bounded `toString/packageName` fallback probe.
- The methods `effect.isStatusBarWindow` and `effect.rememberStatusBar` do not exist; `isStatusBarWindow` is a facade helper and `rememberStatusBar` lives on `StatusBarHeightRuntime`.

## 10. H3 graph

```text
SetFramesCallback(captured Effect)
  └─ onSetFrames(chain, effect)
       ├─ one StatusBarHeightConfig.currentState()
       ├─ win = chain.thisObject
       ├─ Runtime.isKnownStatusBar(win)
       ├─ clientFrames = arg0
       ├─ effect.isClientWindowFrames(clientFrames)
       ├─ effect.readDisplayId(win)
       ├─ displayId == 0:
       │    configuredPx = config.configuredPx (no DisplayMetrics read)
       ├─ displayId != 0 (including unresolved -1):
       │    effect.readWindowDisplayMetrics(win)
       │    if unavailable or nonfatal failure:
       │         fail closed → chain.proceed original unchanged
       │    if available:
       │         configuredPxFor(config.configuredDp, metrics)
       ├─ configuredPx validity check
       ├─ effect.readClientWindowFrame(clientFrames)
       ├─ primitive frame.bottom mutation
       └─ chain.proceed()        // exactly once
```

- One `currentState()` per callback.
- `ClientWindowFrames` is resolved once at cold time, not by iteration at runtime.
- `effect.readClientWindowFrame` occurs **after** `displayId`, optional metrics, and `configuredPx` resolution.
- H3 metrics failure does **not** fall back to the global configured px; it proceeds unchanged.

## 11. H4 graph

```text
DecorInsetsUpdateCallback
  └─ onDecorInsetsInfoUpdate(chain, effect)
       ├─ chain.proceed()        // first
       ├─ one StatusBarHeightConfig.currentState()
       ├─ effect.isDecorInsetsInfo(target)
       ├─ effect.readNonDecorInsets(info)
       ├─ effect.readNonDecorFrame(info)
       ├─ effect.readDisplayContentMetrics(displayContent)  frozen Method
       │    if unavailable or nonfatal failure:
       │         use entry config.configuredPx
       ├─ computeNonDecorTop / computeNonDecorFrameTop     primitive
       ├─ mutation of top
       └─ return result
```

- `chain.proceed()` is called exactly once and before any mutation.
- One `currentState()` after `proceed()`.
- No `Map<String,Member>` or `Pair/Triple` hot decision.

## 12. Refresh graph

```text
PreferenceObserver.onChange(key)
  └─ if key == StatusBarHeightConfig.PREF_KEY (null triggers reconfigure)
       ├─ StatusBarHeightConfig.reconfigure(prefs)
       ├─ if changed → requestStatusBarTraversal()
            ├─ effect.readWindowDisplayContent(win)   frozen Field
            ├─ effect.readDisplayPolicy(displayContent)  frozen Method
            ├─ effect.readDecorInsets(displayPolicy)     frozen Field
            ├─ effect.invalidateDecorInsets(decorInsets) frozen Method
            ├─ effect.readWindowManagerService(win)      frozen Field
            ├─ effect.readWindowPlacer(wmService)        frozen Field
            ├─ generation coalesce (AtomicLong)
            └─ effect.requestTraversal(windowPlacer)     frozen Method
```

- Refresh member resolution is `INSTALL_COLD`; no `LateAbiSlot`/`LateAbiState`.
- Invalidation runs before the generation coalesce check.
- Same-generation second call invalidates again but does not re-request traversal.
- `RuntimeException` during invalidation is fail-closed and traversal still runs; `OutOfMemoryError` propagates with same identity and traversal is skipped.
- No `performSurfacePlacement()` fallback.

## 13. N1-N6 final table

| ID | Item | Status |
|----|------|--------|
| N1 | H1 generic `getType` | `ELIMINATED` |
| N2 | H2 lazy/generic `WindowState` member discovery | `ELIMINATED` |
| N3 | per-layout `additional-instance` originalHeight | `RETAINED_FOR_LIFECYCLE_SAFETY` |
| N4 | H2/H3 metrics/displayId generic fallback | `ELIMINATED` |
| N5 | H4 currentState allocation / mixed Config reads | `ELIMINATED` |
| N6 | H4 generic `DisplayContent.getDisplayMetrics` | `ELIMINATED` |

### N3 rationale

`XposedHelpers.getAdditionalInstanceField` and `setAdditionalInstanceField` are still used in the H2 path to remember the original `WindowManager.LayoutParams.height`. C1 does not have enough device/lifecycle evidence to prove that moving the OEM `originalHeight` into a bounded weak `Runtime` structure is equivalent across `WindowState` recreation, multi-display, restore, and disable/enable transitions. Therefore the additional-instance API is retained intentionally.

## 14. Fallback behavior

### H2

- Metrics failure or unavailable display metrics → fail closed → proceed with original `layoutWindowLw` before any height mutation.

### H3

- Secondary display (`displayId != 0`) metrics failure or unavailable frozen `WindowState` metrics → fail closed → proceed with original `setFrames` before any frame mutation.
- `displayId == 0` uses `config.configuredPx`; no `DisplayMetrics` read.

### H4

- `DisplayContent.getDisplayMetrics()` failure or unavailable → preserve oracle fallback → use the entry `config.configuredPx` and continue geometry update.

### Refresh

- Nonfatal frozen member failure in any step → that operation returns `null`/`false` and the refresh graph fail-closes accordingly.
- `RuntimeException` during invalidation does not block traversal.
- `OutOfMemoryError` propagates with the same identity.

### Status-bar owner discovery

- `Runtime.typeMatchObserved` is the runtime authority for whether `TYPE_STATUS_BAR` has been observed.
- Before `typeMatchObserved == true`, unknown owners may use the bounded `packageName` + `toString()` fallback.
- `fallbackProbeBudget` starts at `4096`.
- Once `typeMatchObserved == true`, unknown non-status-bar windows do not run the package-name/`toString()` fallback.
- Once the budget is exhausted, the expensive fallback stops entirely.

### Refresh partial capability

- Traversal and decor invalidation are independent frozen capabilities.
- Missing traversal capability does not make invalidation unavailable.
- Missing invalidation capability does not make traversal unavailable.

### Fatal

- `OutOfMemoryError`, `VirtualMachineError`, `ThreadDeath` are rethrown with the same identity; non-fatal errors are logged and return `null`/`false`.

## 15. Topology

### 15.1 Initial disabled

`StatusBarInsetsHeightHook` is skipped when the feature is initially disabled in `system_server`. It is a one-shot install in `system_server`; runtime preference changes to `enabled` do **not** retroactively install the hook. A `system_server` restart / device reboot is required to re-enter the install path.

### 15.2 Observer null key

The preference observer returns early when `key != null && key != PREF_KEY`. A `null` key (clear/reset broadcast) triggers a full `reconfigure` and invalidation.

### 15.3 PACKAGE_READY path

`System.StatusBarHeightHook` is the `PACKAGE_READY` resource path. It obtains the target process `Resources`, runs `StatusBarHeightConfig.configure(prefs, resources)`, reads the resulting `configuredDp`, and replaces the following dimen resources with that value:

- `status_bar_height_default`
- `status_bar_height`
- `status_bar_height_portrait`
- `status_bar_height_landscape`

It remains a separate process-local cold resource path and was not folded into the `system_server` Architecture-C runtime.

## 16. Fatal behavior matrix

| Boundary | Fatal contract | `chain.proceed()` count |
|----------|---------------|-------------------------|
| H1 pre-proceed reflection fatal | same identity | 0 |
| H1 original fatal | same identity | exactly 1 attempt |
| H2 pre-proceed reflection fatal | same identity | 0 |
| H2 original fatal | same identity | exactly 1 |
| H3 pre-proceed reflection fatal | same identity | 0 |
| H3 original fatal | same identity | exactly 1 |
| H4 original fatal | same identity; effect does not execute | exactly 1 |
| H4 post-proceed Effect fatal | same identity; `proceed()` already 1 | 1 |
| Refresh invalidation fatal | same identity; `requestTraversal` not executed | N/A |
| Refresh `requestTraversal` fatal | same identity | N/A |

`chain.proceed()` is not wrapped in `try/catch` except where the original oracle already did so.

## 17. Tests

The following suites were run and passed:

| Suite | Result |
|-------|--------|
| `StatusBarHeightArchitectureCAbiTest` | PASS |
| `StatusBarHeightArchitectureCH1Test` | PASS |
| `StatusBarHeightArchitectureCH2Test` | PASS |
| `StatusBarHeightArchitectureCH3Test` | PASS |
| `StatusBarHeightArchitectureCH4Test` | PASS |
| `StatusBarHeightArchitectureCRefreshTest` | PASS |
| `StatusBarHeightArchitectureCRuntimeTest` | PASS |
| `StatusBarHeightConfigTest` | PASS |
| `StatusBarInsetsGeometryTest` | PASS |
| `StatusBarInsetsDecisionTest` | PASS |
| `StatusBarInsetsResolverTest` | PASS |
| `StatusBarWindowStateHotPathTest` | PASS |
| `StatusBarHeightLiveTest` | PASS |
| `StatusBarInsetsRoutingTest` | PASS |

## 18. Verification

| Command | Result |
|---------|--------|
| `python tools/verify.py full` | PASS |
| `git diff --check` | PASS |
| `.\gradlew.bat :app:assembleDebug` | PASS |

### Source hazard scan

```text
python tools/source_hazard_scan.py --scope production --strict-all
  exit 1 (143 findings)

python tools/source_hazard_scan.py --scope production --strict-all \
  --path app/src/main/java/tv/withaibuild/customiuizer/mods/statusbarheight
  exit 0, 0 reviewed / 0 new

python tools/source_hazard_scan.py --scope production --strict-all \
  --path app/src/main/java/tv/withaibuild/customiuizer/mods/SystemStatusBarInsetsHooks.kt
  exit 0, 0 reviewed / 0 new
```

- The full `strict-all` run reports 143 historical findings in other subsystems; none are in `tv.withaibuild.customiuizer.mods.statusbarheight` or `SystemStatusBarInsetsHooks.kt`.
- Scoped re-runs for the C1 production subsystem and its facade returned **0 reviewed / 0 new**.
- `SOURCE_HAZARD_BASELINE.json` was not rewritten.
- **New C1 production hazards: 0**.

## 19. Deferred

- N3 `additional-instance` originalHeight retention is the only intentionally deferred lifecycle item.
- Device-level A/B, power, and real-ROM lifecycle evidence are not yet available.
- C2 / C3 follow-up (broader feature migration, generated plans, etc.) has not started.

## 20. Device Gate

| Gate | Status |
|------|--------|
| Device A/B / real-ROM verification | `PENDING` |

C1 is a code gate candidate. It is **not** a device pass. Do not ship a release build from this SHA without device verification.

---

C1_READY_FOR_INDEPENDENT_FINAL_CODE_AUDIT