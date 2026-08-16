# C0 — Architecture C Status Bar Height Fact Audit

**Repository:** `tomthenpc/customiuizer-a14`  
**Oracle SHA:** `2c4efeafc8655855b824b72ecbf6106641b04a8e`  
**Branch:** `devin/a14-architecture-c-r14.20.0`  
**Generated:** 2026-08-10  
**Scope:** `SystemStatusBarInsetsHooks.kt` and its direct dependencies only  

> C0 does not implement Architecture C production code.  This document is a docs-only artifact.

---

## C0.1 Git Boundary

```text
local branch:  devin/a14-architecture-c-r14.20.0
merge-base:    2c4efeafc8655855b824b72ecbf6106641b04a8e
origin heads:
  aff0449b...  refs/heads/devin/a14-optimization-r14.18.8
  2c4efeaf...  refs/heads/devin/a14-production-quality-r14.18.8
  3f9d6ba6...  refs/heads/main
```

- Branch ancestry is strictly from the r14.18.8 oracle `2c4efeaf...`.
- No production/test/tool/baseline files are modified in this docs-only commit.

---

## C0.2 Hook Table

| # | Target class | Target method | Process | Hook phase | Callback frequency | Purpose |
|---|--------------|---------------|---------|------------|--------------------|---------|
| H1 | `android.view.InsetsSource` | `setFrame(Rect)` / `setFrame(int,int,int,int)` | system_server | `SYSTEM_SERVER_STARTING` | Per InsetsSource frame change (very high) | Rewrite the status-bar source frame bottom to `originalTop + configuredPx`. |
| H2 | `com.android.server.wm.DisplayPolicy` | `layoutWindowLw` | system_server | `SYSTEM_SERVER_STARTING` | Per window layout (high) | Set `WindowManager.LayoutParams.height = configuredPx` for the status bar window before the original layout runs. |
| H3 | `com.android.server.wm.WindowState` | `setFrames(ClientWindowFrames, ...)` | system_server | `SYSTEM_SERVER_STARTING` | Per WindowState frame commit (high) | Fallback: rewrite `ClientWindowFrames.frame.bottom` to `top + configuredPx` if the ROM layout did not honour `mAttrs.height`. |
| H4 | `com.android.server.wm.DisplayPolicy$DecorInsets$Info` | `update(DisplayContent, int, int, int)` | system_server | `SYSTEM_SERVER_STARTING` | Per rotation/config display frame build (medium) | Expand `mNonDecorInsets.top` and `mNonDecorFrame.top` so app bounds follow the taller status bar. |
| H5 | Framework dimen resources (`android` + target pkg) | `status_bar_height_default`, `status_bar_height`, `status_bar_height_portrait`, `status_bar_height_landscape` | any eligible package | `PACKAGE_READY` | Once per eligible package at package load | Replace framework dimen resources so SystemUI and other packages read the configured dp height. No observer; no live preference update. |

### Hook wiring and install-time topology contract

```text
MainModule.handleLoadPackage
  -> SystemServerInstaller.install(lpparam)
       -> FeatureInstallRegistry.installAll(SYSTEM_SERVER, SYSTEM_SERVER_STARTING, mPrefs)
            -> StatusBarHeightInsetsFeature (created only if isEnabled(prefs))
                 -> if enabled: SystemStatusBarInsetsHooks.StatusBarInsetsHeightHook(lpparam)
                      -> install InsetsSource / DisplayPolicy / WindowState / DecorInsets.Info hooks
                      -> register statusBarHeightObserver with PreferenceObserverRegistry
                 -> if disabled: SKIPPED; no hooks; no observer

MainModule.handleLoadPackage
  -> CommonPackageFeatures.hasEnabledFeature(mPrefs, pkg)
       -> if any CommonPackageFeatures enabled for pkg, build CommonPackageFeatures registry
            -> StatusBarHeightFeature (created only if isEnabled(prefs))
                 -> if enabled: System.StatusBarHeightHook(lpparam)
                      -> StatusBarHeightConfig.configure(...)
                      -> 4 x ModuleHelper.replacePkgAndFrameworkValue(...)
                 -> if disabled: SKIPPED; no resource replacement
```

### Install-time topology contract

```text
A. initial enabled (system_statusbarheight > 11):
   - InsetsSource / DisplayPolicy / WindowState / DecorInsets.Info hooks installed.
   - statusBarHeightObserver registered in PreferenceObserverRegistry.
   - live preference change works: custom -> custom, custom -> disabled, disabled -> enabled.

B. initial disabled (system_statusbarheight <= 11) — system_server Insets topology:
   - StatusBarHeightInsetsFeature SKIPPED by FeatureInstallRegistry.
   - No InsetsSource / DisplayPolicy / WindowState / DecorInsets.Info hooks installed.
   - statusBarHeightObserver NOT registered.
   - disabled -> enabled cannot be activated live.
   - Reactivation requires system_server process restart (or device reboot) so that
     SystemServerInstaller -> FeatureInstallRegistry re-runs and creates StatusBarHeightInsetsFeature.
   - SystemUI process restart alone does NOT reinstall the system_server StatusBarHeightInsets topology.

C. initial disabled (system_statusbarheight <= 11) — PACKAGE_READY resource topology:
   - StatusBarHeightFeature SKIPPED by CommonPackageFeatures.
   - No framework dimen resource replacement for the package.
   - disabled -> enabled cannot be activated live for that package.
   - Reactivation requires the corresponding package/process to reload and re-enter PACKAGE_READY,
     e.g. a SystemUI resource change requires a SystemUI process reload.
```

This is the current r14.18.8 oracle behavior.  Any change to a permanent live topology (e.g. installing hooks+observer even when initially disabled) is a **semantic change**, not a parity refactor, and must be proposed and tested separately.

---

## C0.3 ABI Table

### InsetsSource ABI

| # | Class | Field / Method | Constant | Category | How resolved | Where cached | Fallback |
|---|-------|----------------|----------|----------|--------------|--------------|----------|
| A1 | `android.view.InsetsSource` | `setFrame(Rect)` and `setFrame(int,int,int,int)` | — | install-cold | `getDeclaredMethods().filter { name == "setFrame" }` at install | Not cached as Method (Xposed `hookAllMethods` keeps its own handles) | If neither overload exists, log and return; do not install hook. |
| A2 | `android.view.InsetsSource` | `getType()` | — | hot | `hasMethod()` reflection check at install | Not cached as Method; callback uses `typeField` or `XposedHelpers.callMethod` | If `getType()` fails, callback returns `TYPE_UNRESOLVED` and proceeds. |
| A3 | `android.view.InsetsSource` | `mType` | — | hot | `resolveIntField(insetsSourceClass, "mType")` at install | `SetFrameCallback.typeField` (Field, install-time fixed) | If `typeField == null` at install, callback falls back to `XposedHelpers.callMethod(source, "getType")` on every type read.  The field does **not** self-install inside the callback. |
| A4 | `android.view.InsetsSource` | `getId()` | — | warm (diagnostic) | `hasMethod()` at install | `SetFrameCallback.hasGetId` (Boolean) | Diagnostic source id uses `null` when false. |
| A5 | `android.view.InsetsState` | `ITYPE_STATUS_BAR` / `ITYPE_NAVIGATION_BAR` / `ITYPE_DISPLAY_CUTOUT` | static int | install-cold | `getStaticInt()` at install | Stored in `InsetsSourceAbi` only | If any missing, legacy encoding is rejected. |
| A6 | `android.view.WindowInsets.Type` | `statusBars()`, `navigationBars()`, `displayCutout()` | public masks | install-cold | `safePublicType()` at install | Stored in `InsetsSourceAbi` only | If missing, modern encoding is rejected. |

### WindowManager ABI

| # | Class | Field / Method | Constant | Category | How resolved | Where cached | Fallback |
|---|-------|----------------|----------|----------|--------------|--------------|----------|
| A7 | `com.android.server.wm.WindowState` | `mAttrs` | — | hot | `resolveDeclaredField()` at install; lazily self-installs on first use if class differs | `SystemStatusBarInsetsHooks.windowStateAttrsField` (volatile Field) | Falls back to `XposedHelpers.getObjectField(win, "mAttrs")` if class differs. |
| A8 | `android.view.WindowManager.LayoutParams` | `type` | `TYPE_STATUS_BAR = 2000` | hot | `resolveDeclaredField()` at install; lazily self-installs on first use if class differs | `SystemStatusBarInsetsHooks.layoutParamsTypeField` (volatile Field) | Falls back to `XposedHelpers.getIntField(attrs, "type")`. |
| A9 | `com.android.server.wm.WindowState` | `getFrame()` | — | hot | `getMethod("getFrame")` at install | `windowStateGetFrameMethod` (plain var Method) | Falls back to `XposedHelpers.callMethod(win, "getFrame")` and then `mWindowFrames.mFrame`. |
| A10 | `com.android.server.wm.WindowState` | `getDisplayMetrics()` | — | hot | `getMethod("getDisplayMetrics")` at install | `windowStateGetDisplayMetricsMethod` (plain var Method) | Falls back to `XposedHelpers.getObjectField(win, "mDisplayContent")` -> `XposedHelpers.callMethod(displayContent, "getDisplayMetrics")`. |
| A11 | `com.android.server.wm.WindowState` | `getDisplayId()` | — | hot | `getMethod("getDisplayId")` at install | `windowStateGetDisplayIdMethod` (plain var Method) | Falls back to `XposedHelpers.callMethod(win, "getDisplayId")`. |
| A12 | `com.android.server.wm.ClientWindowFrames` | `frame` | — | hot | `getField("frame")` via `resolveClientWindowFramesClass()` at install | `clientWindowFramesFrameField` (plain var) | Falls back to `XposedHelpers.getObjectField(clientFrames, "frame")`. |
| A13 | `com.android.server.wm.ClientWindowFrames` | `displayFrame`, `parentFrame` | — | install-cold | resolved at install | `clientWindowFramesDisplayFrameField`, `clientWindowFramesParentFrameField` | Not used by current hook (read only for completeness). |
| A14 | `com.android.server.wm.DisplayPolicy$DecorInsets$Info` | `mNonDecorInsets`, `mNonDecorFrame` | — | warm | `getDeclaredField()` at install | `decorInfoNonDecorInsetsField`, `decorInfoNonDecorFrameField` (plain vars) | If missing, `installDecorInsetsInfoHook` returns; feature works without it. |

### Additional framework ABI reached by helper graph

| # | Class | Field / Method | Constant | Category | How accessed | Cached? | Callback frequency | Fallback | C1 disposition |
|---|-------|----------------|----------|----------|--------------|---------|-------------------|----------|----------------|
| A15 | `android.view.WindowManager.LayoutParams` | `height` | — | hot | `XposedHelpers.setIntField(attrs, "height", configuredPx)` after `readWindowAttrs` | no | per layout/status bar | fail closed if attrs unreadable | pre-resolve `LayoutParams.height` Field and hold in Resolver. |
| A16 | `android.view.WindowManager.LayoutParams` | `packageName` | — | warm | `XposedHelpers.getObjectField(attrs, "packageName")` in fallback probe | no | only until `typeMatchObserved` | if unreadable, treat as non-status-bar | pre-resolve `LayoutParams.packageName` Field; remove fallback probe from steady-state. |
| A17 | `com.android.server.wm.WindowState` | `mDisplayContent` | — | warm | `XposedHelpers.getObjectField(win, "mDisplayContent")` in `tryGetWindowDisplayMetrics` fallback | no | when `getDisplayMetrics()` Method missing / returns null | then `XposedHelpers.callMethod(displayContent, "getDisplayMetrics")` | pre-resolve both `WindowState.getDisplayMetrics()` Method and `DisplayContent.getDisplayMetrics()` Method; fail closed if neither available. |
| A18 | `com.android.server.wm.WindowState` | `mWindowFrames` / `mFrame` | — | warm | `XposedHelpers.getObjectField(win, "mWindowFrames")` then `getObjectField(frames, "mFrame")` | no | fallback in `readWindowFrame` when `getFrame()` fails | Rect or null | pre-resolve `mWindowFrames` and `mFrame` Fields or `WindowState.getFrame()` Method; hold frozen. |
| A19 | `com.android.server.wm.WindowState` | `mWmService` | — | observer-cold | `XposedHelpers.getObjectField(win, "mWmService")` in `requestStatusBarTraversal` | no | on preference change only | if null, return | pre-resolve Field; this is cold/observer only. |
| A20 | `com.android.server.wm.WindowManagerService` | `mWindowPlacerLocked` | — | observer-cold | `XposedHelpers.getObjectField(wmService, "mWindowPlacerLocked")` | no | on preference change only | if null, return | pre-resolve Field. |
| A21 | `com.android.server.wm.WindowSurfacePlacer` | `requestTraversal()` | — | observer-cold | `XposedHelpers.callMethod(windowPlacer, "requestTraversal")` | no | on preference change only | log and wait for natural layout | pre-resolve Method. |
| A22 | `com.android.server.wm.DisplayContent` | `getDisplayPolicy()` | — | observer-cold | `XposedHelpers.callMethod(displayContent, "getDisplayPolicy")` in `invalidateDecorInsets` | no | on preference change only | if null, return | pre-resolve Method. |
| A23 | `com.android.server.wm.DisplayPolicy` | `mDecorInsets` | — | observer-cold | `XposedHelpers.getObjectField(displayPolicy, "mDecorInsets")` | no | on preference change only | if null, return | pre-resolve Field. |
| A24 | `com.android.server.wm.DisplayPolicy$DecorInsets` | `invalidate()` | — | observer-cold | `XposedHelpers.callMethod(decorInsets, "invalidate")` | no | on preference change only | log and continue | pre-resolve Method. |
| A25 | `com.android.server.wm.DisplayContent` | `getDisplayMetrics()` | — | warm | `XposedHelpers.callMethod(displayContent, "getDisplayMetrics")` in `configuredPxForDecorInfo` | no | per `DecorInsets.Info.update` | fallback to `StatusBarHeightConfig.configuredPx` | pre-resolve Method. |

### ABI resolution rules for C1

- **Pre-resolve at install:** all members that can be discovered from the install `ClassLoader` (A1-A14, A15-A18 fields/methods on framework classes) must be resolved once and frozen in `StatusBarHeightAbi`.
- **Late-freeze allowed:** A19-A25 are only reached by the observer/refresh path and by `DecorInsets.Info.update`.  They may be resolved on first real object appearance and then frozen.  Steady-state rediscovery is forbidden.
- **Fail closed:** if a required member cannot be resolved, the feature must skip the relevant hook or return `chain.proceed()` without modifying framework state.

---

## C0.4 Runtime State Table

| # | Name | Type | Owner | Thread | Publication | Lifetime | Hot/Cold |
|---|------|------|-------|--------|-------------|----------|----------|
| S1 | `typeInfo` | `InsetsTypeInfo?` | `SystemStatusBarInsetsHooks` singleton | LSPosed init thread | plain var, written once after cold install | process | cold |
| S2 | `hookInstalled` | `Boolean` | `SystemStatusBarInsetsHooks` | LSPosed init thread | plain var | process | cold |
| S3 | `statusBarWindowRef` | `WeakReference<Any>?` | `SystemStatusBarInsetsHooks` | WMS layout / observer threads | `@Volatile` | until GC | hot (read in `requestStatusBarTraversal`) |
| S4 | `statusBarWindows` | `Array<WeakReference<Any>>` | `SystemStatusBarInsetsHooks` | WMS layout thread | `@Volatile`, rebuilt under `synchronized(this)` | bounded by `MAX_TRACKED_DISPLAYS` (4) | hot (identity fast path) |
| S5 | `typeMatchObserved` | `Boolean` | `SystemStatusBarInsetsHooks` | WMS layout thread | `@Volatile` | process | hot (gates fallback probe) |
| S6 | `fallbackProbeBudget` | `AtomicInteger` | `SystemStatusBarInsetsHooks` | WMS layout thread | atomic | process | warm (only until first type match) |
| S7 | `windowStateAttrsField` | `Field?` | `SystemStatusBarInsetsHooks` | WMS layout thread | `@Volatile` | process | hot (cached, self-installs) |
| S8 | `layoutParamsTypeField` | `Field?` | `SystemStatusBarInsetsHooks` | WMS layout thread | `@Volatile` | process | hot |
| S9 | `windowStateClass` | `Class<*>?` | `SystemStatusBarInsetsHooks` | LSPosed init / layout | `@Volatile` | process | hot (allocation-free type test) |
| S10 | `lastRefreshGeneration` | `AtomicLong` | `SystemStatusBarInsetsHooks` | observer / WMS thread | atomic | process | hot (coalesces traversal requests) |
| S11 | `clientWindowFramesClass` / `clientWindowFramesFrameField` | `Class<*>?` / `Field?` | `SystemStatusBarInsetsHooks` | LSPosed init / `setFrames` | plain vars, written at install | process | hot |
| S12 | `windowStateGetFrameMethod` / `getDisplayMetricsMethod` / `getDisplayIdMethod` | `Method?` | `SystemStatusBarInsetsHooks` | layout / `setFrames` | **plain var**, not `@Volatile` | process | hot |
| S13 | `decorInfoNonDecorInsetsField` / `decorInfoNonDecorFrameField` | `Field?` | `SystemStatusBarInsetsHooks` | install / `DecorInsets.Info.update` | **plain var**, not `@Volatile` | process | warm |
| S14 | `loggedCritical` / `loggedRejection` / `loggedLiveKeys` | `LinkedHashSet<String>` | `SystemStatusBarInsetsHooks` | any thread | `synchronized(set)` | bounded (16 each) | warm (only on first-hit logs) |
| S15 | `rejectionLoggingExhausted` | `Boolean` | `SystemStatusBarInsetsHooks` | any thread | `@Volatile` | process | warm |
| S16 | `layoutLogStamps` / `windowFrameLogStamps` / `clientFrameLogStamps` | `AtomicLongArray` (size 4) | `SystemStatusBarInsetsHooks` | WMS layout thread | atomic | process | hot (generation gating) |
| S17 | `statusSourceLogStamp` / `reflectionFailureLogStamp` / `invalidShapeLogStamp` | `AtomicLong` | `SystemStatusBarInsetsHooks` | any thread | atomic | process | hot (per-generation once) |
| S18 | `StatusBarHeightConfig.rawPreferenceDp` | `Int` | `StatusBarHeightConfig` object | preference observer thread | `@Volatile` | process | hot (log strings) |
| S19 | `StatusBarHeightConfig.enabled` | `Boolean` | `StatusBarHeightConfig` object | preference observer thread | `@Volatile` | process | hot |
| S20 | `StatusBarHeightConfig.configuredDp` | `Int` | `StatusBarHeightConfig` object | preference observer thread | `@Volatile` | process | hot |
| S21 | `StatusBarHeightConfig.configuredPx` | `Int` | `StatusBarHeightConfig` object | preference observer thread | `@Volatile` | process | hot |
| S22 | `StatusBarHeightConfig.densityDpi` / `density` | `Int` / `Float` | `StatusBarHeightConfig` object | preference observer / layout | `@Volatile` | process | hot / warm |
| S23 | `StatusBarHeightConfig.generation` | `AtomicLong` | `StatusBarHeightConfig` object | preference observer | atomic | process | hot (log gating) |
| S24 | `StatusBarHeightInsetsFeature` instance | `BaseSystemServerFeature` | `FeatureInstallRegistry` / GC | LSPosed init thread | local var | install only | cold |

### Notes

- `windowStateGetFrameMethod`, `getDisplayMetricsMethod`, `getDisplayIdMethod`, `decorInfoNonDecorInsetsField`, `decorInfoNonDecorFrameField`, and `clientWindowFrames*` are **plain `var`**, not `@Volatile`.  They are written once at install and then read on the WMS thread.  In the current architecture this is safe because the write happens-before the first hook invocation through Xposed registration, but C1 should make the frozen ABI explicit and immutable.
- All `WeakReference` are to `WindowState` instances, not to Activities/Views, and the owning array is `@Volatile`.  This does not create a strong owner cycle.
- `statusBarWindowRef` is a single WeakReference used to request a traversal on the most recently laid-out status bar.  It is reset on `resetForTest()` only.
- `lastRefreshGeneration` is an `AtomicLong`, but `getAndSet` is used to coalesce duplicate requests.  This is correct but means the publication is the atomic itself, not an immutable snapshot.
- **Current `StatusBarHeightConfig` is six separate `@Volatile` primitive fields plus an `AtomicLong`**.  `currentState()` creates a new `State` data object on every call.  A hot path that reads multiple config fields in sequence can theoretically observe a mixed generation/state window because there is no single snapshot reference.  **This is a primary C1 migration target:** one immutable `State` snapshot published through a single `@Volatile` reference or `AtomicReference`.

---

## C0.5 Config Table

| Preference key | Default | Reader | Observer | Null-key behavior | Live update | Hot-path access |
|----------------|---------|--------|----------|-------------------|-------------|-----------------|
| `system_statusbarheight` | 11 (sentinel) | `StatusBarHeightConfig.resolveHeightDp()` / `isEnabled()` | `statusBarHeightObserver` registered via `PreferenceObserverRegistry` | Observer `onChange(key)` returns immediately for `key != null && key != PREF_KEY`.  `key == null` means full invalidation: `StatusBarHeightConfig.reconfigure(MainModule.mPrefs)` re-reads the current `PrefMap` snapshot for `system_statusbarheight`.  **This does NOT automatically restore default 11**; it re-reads whatever value is currently in the snapshot.  `PrefMap.getInt(key, DEFAULT_SENTINEL)` returns 11 only when the key is absent from the map. | `reconfigure(MainModule.mPrefs)` on observer; bumps `generation` only on effective change.  Requires the feature to have been installed at process start (initial enabled). | `StatusBarHeightConfig.enabled` / `configuredPx` / `configuredDp` volatile fields only. No `PrefMap` in callbacks. |

- Sentinel 11 means disabled; the configured dp still resolves to 27 (framework default) for resource replacement.
- Any value > 11 enables the feature.
- Density is cached at `configure()` time.  `reconfigure()` preserves the cached density and re-computes px from it.
- `recomputePx(metrics)` is called from `onLayoutWindowLw` only for `displayId == 0` and only when `metrics.densityDpi != StatusBarHeightConfig.densityDpi`.
- **Live update is topology-limited:** preference observer is registered only when the feature is installed at process start.  See C0.2 install-time topology contract.

---

## C0.6 Behavior Table

| Scenario | Expected behavior | Current oracle evidence |
|----------|-------------------|-------------------------|
| disabled (11) | `chain.proceed()` immediately; no status-bar discovery; only known status bar gets original height restored | `onLayoutWindowLw` returns `chain.proceed()` after `StatusBarHeightConfig.enabled` check; disabled path restores via `restoreStatusBarWindowHeight` if known. |
| enabled target | `WindowState.mAttrs.height` set before layout; `ClientWindowFrames.frame.bottom` adjusted; InsetsSource frame bottom adjusted | H2 sets attrs.height, H3 rewrites frame bottom, H1 rewrites source frame. |
| non-target (app window) | `isStatusBarWindow()` returns false; `chain.proceed()` untouched | `isStatusBarWindow` returns false for non-STATUS_BAR type and non-fallback package/toString. |
| unknown WindowState (not yet known, not status type) | discovery via `mAttrs.type` fallback; if `typeMatchObserved == true` and type != status, early reject; otherwise bounded packageName/toString probe | `isStatusBarWindow` first checks `isKnownStatusBarWindow`, then `readAttrsType`, then `typeMatchObserved` short-circuit, then `fallbackProbeBudget` probe. |
| density unchanged | uses cached `configuredPx` | `onLayoutWindowLw` only calls `recomputePx` when `metrics.densityDpi != StatusBarHeightConfig.densityDpi`. |
| density changed | `recomputePx(metrics)` updates `StatusBarHeightConfig.configuredPx` and `densityDpi` | `recomputePx` is synchronized and bumps generation only on effective change. |
| display change (secondary display) | local px computed from the display's metrics without mutating global config | `onLayoutWindowLw` uses `StatusBarHeightConfig.configuredPxFor(configuredDp, metrics)` for `displayId != 0`. `onSetFrames` also uses local metrics for non-zero display. |
| frame unchanged | `chain.proceed()` with original args, no Rect allocation | `SetFrameCallback.adjustArgs` returns `null` when `newBottom == oldBottom`; `onSetFrames` returns `chain.proceed()` when bottom unchanged. |
| config live update | If system_server Insets hooks+observer were installed at process start: preference observer calls `reconfigure()` then `requestStatusBarTraversal()`.  If the Insets feature was skipped at process start, live activation requires a **system_server restart / reboot**. | `statusBarHeightObserver.onChange` filters to `PREF_KEY` (and accepts `null`), calls `reconfigure`, and if `change.changed` calls `requestStatusBarTraversal`.  Observer is registered only when the system_server Insets feature is enabled at install. |
| null-key invalidation | `key == null` is treated as full invalidation; `reconfigure(MainModule.mPrefs)` re-reads current snapshot.  It does **not** force default 11. | `statusBarHeightObserver.onChange`: `if (key != null && key != PREF_KEY) return`.  Then `reconfigure(MainModule.mPrefs)`. |
| preference absent | `PrefMap.getInt(key, DEFAULT_SENTINEL)` returns 11; feature disabled. | `PrefMap.getInt` default handling. |
| fallback ABI (legacy InsetsState) | `selectTypeEncoding` picks `LEGACY_INTERNAL` if one-int constructor, no modern constructor, getType, and both legacy status/nav constants exist | `resolveInsetsSourceAbi` and `selectTypeEncoding` implement this. |
| partial ABI (missing nav/cutout) | missing types filled with -1; status type is sufficient for the hook | `InsetsTypeInfo` constructor uses `takeIf { it.isResolvedType() } ?: -1` for nav/cutout. |
| original RuntimeException | propagates through `chain.proceed()` without suppression | `onLayoutWindowLw` / `onSetFrames` / `onDecorInsetsInfoUpdate` do not catch exceptions from `chain.proceed()`. |
| OOM/fatal | propagates; `FatalErrors.unwrapAndRethrowIfFatal(t)` in all `catch (Throwable)` blocks before logging | Verified by code and unit tests. |
| nested/reentry | `hookInstalled` guard and `FeatureInstallState` ensure one install per process; callback does not re-enter itself | `StatusBarInsetsHeightHook` returns immediately if `hookInstalled`. `FeatureInstallRegistry` checks `FeatureInstallState.beginInstall`. |
| initial disabled -> enabled at runtime (system_server Insets) | **Not live-activatable.**  `StatusBarHeightInsetsFeature` was skipped at system_server start; no Insets hooks, no observer.  Requires **system_server process restart or device reboot**.  SystemUI restart alone is insufficient. | `FeatureInstallRegistry.isEnabled` gate before `create()`.  `SystemServerInstaller.install` runs once per system_server process.  See install-time topology contract. |
| initial disabled -> enabled at runtime (PACKAGE_READY resource) | **Not live-activatable for the package.**  `StatusBarHeightFeature` was skipped when the package loaded; no resource replacement.  Requires the **corresponding package/process to reload and re-enter PACKAGE_READY**, e.g. SystemUI resource change requires SystemUI process reload. | `CommonPackageFeatures.all` and `FeatureInstallRegistry` check `isEnabled` before creating the feature.  No observer. |
| PACKAGE_READY resource replacement | Executed once per eligible package at `PackageReadyParam` load when `StatusBarHeightFeature.isEnabled(prefs)` is true.  No preference observer; no live update for resource path. | `System.StatusBarHeightHook` calls `StatusBarHeightConfig.configure()` and 4 `replacePkgAndFrameworkValue()` calls.  `CommonPackageFeatures` only registers the feature if enabled. |

---

## C0.7 Fatal / Fallback Table

| # | Location | Original chain / module reflection | Fatal behavior | Recoverable fallback |
|---|----------|-----------------------------------|----------------|----------------------|
| F1 | `StatusBarInsetsHeightHook()` | class/method reflection at install | OOM/VirtualMachineError/ThreadDeath rethrown via `FatalErrors.unwrapAndRethrowIfFatal`; other exceptions logged and `return` (feature fails closed) | If `InsetsSource` class or `setFrame` missing, return without installing hooks. |
| F2 | `installDisplayPolicyHook()` | `findClassIfExists` for `DisplayPolicy` | class not found -> log and return; no crash | Hook not installed; InsetsSource still works. |
| F3 | `installWindowStateHook()` | `findClassIfExists` for `WindowState`, `resolveClientWindowFramesClass()` | class not found or ClientWindowFrames missing -> log and return | setFrames fallback not installed; layout hook still works. |
| F4 | `installDecorInsetsInfoHook()` | `findClassIfExists` + declared method + fields | class/method/fields missing -> log and return | Decor insets adjustment not installed; core height still works. |
| F5 | `SetFrameCallback.intercept()` | `readSourceType()` / `adjustArgs()` | Fatal rethrown before log; ordinary Throwable logged and `chain.proceed()` | If type read fails, `TYPE_UNRESOLVED` and proceed. If arg rewrite throws, log and `chain.proceed()`. |
| F6 | `onLayoutWindowLw()` | `tryGetWindowDisplayMetrics()` / `getDisplayId()` / `applyStatusBarWindowHeight()` | `FatalErrors.unwrapAndRethrowIfFatal` before fallbacks; exceptions caught and `chain.proceed()` | If metrics or display id unreadable, return `chain.proceed()`. |
| F7 | `onSetFrames()` | `readClientWindowFrame()` / `getDisplayId()` | Fatal rethrown; exceptions caught and `chain.proceed()` | If ClientWindowFrames mismatch or frame unreadable, `chain.proceed()`. |
| F8 | `onDecorInsetsInfoUpdate()` | `decorInfoNonDecorInsetsField.get()` etc. | Fatal rethrown; other caught and result returned | If fields unavailable, `return result`. |
| F9 | `isStatusBarWindow()` | `readWindowAttrs()` / `readAttrsType()` / packageName/toString | Fatal rethrown; `Throwable` returns `false` | If attrs unreadable, treated as non-status-bar. |
| F10 | `requestStatusBarTraversal()` | reflection to `mDisplayContent`, `mWmService`, `mWindowPlacerLocked`, `requestTraversal` | Fatal rethrown; others logged but no crash | If traversal unavailable, feature waits for natural layout. |

---

## C0.8 Test Audit

| # | Test file | Type | What it actually executes | What it does NOT prove |
|---|-----------|------|---------------------------|------------------------|
| T1 | `StatusBarHeightConfigTest` | structural / behavioral | dp/px conversion, enable semantics, reconfigure, recompute, generation bumping, sentinel handling. | Does not exercise the real `PrefMap`/`RemotePreferences` update thread or SystemUI/system_server lifecycle. |
| T2 | `StatusBarInsetsGeometryTest` | structural / behavioral | pure `computeStatusBarFrameBottom`, `computeNonDecorTop`, `computeNonDecorFrameTop` for fuxi/default densities. | Does not prove real WindowState/InsetsSource geometry on a ROM. |
| T3 | `StatusBarInsetsDecisionTest` | behavioral / structural | `SetFrameCallback.intercept` with fake `XposedInterface.Chain` and fake `InsetsSource`; type encoding, one/four arg overloads, disabled passthrough, reflection failure, original exception propagation, OOM propagation, logging bounds. | Does not prove real `android.view.InsetsSource` ABI selection on a device; does not prove DisplayPolicy/WindowState paths. |
| T4 | `StatusBarInsetsResolverTest` | structural | `selectTypeEncoding()` logic for MODERN_PUBLIC / LEGACY_INTERNAL / UNSUPPORTED and sentinel normalization. | Does not prove actual reflection results on a ROM. |
| T5 | `StatusBarWindowStateHotPathTest` | behavioral | `onLayoutWindowLw` and `onSetFrames` with fake WindowState; disabled/known/unknown paths, fatal propagation, single `proceed` calls. | Fakes `WindowState` with direct `mAttrs` fields; does not prove real WMS class loading or field layout. |
| T6 | `StatusBarHeightLiveTest` | behavioral | Combined `onLayoutWindowLw` + `onSetFrames` + `requestStatusBarTraversal` with fake `WindowState` and `ClientWindowFrames`; secondary display, density change, preference reconfigure, coalescing. | Does not prove real Handler/Looper or `WindowSurfacePlacer` traversal on a device. |
| T7 | `StatusBarInsetsRoutingTest` | structural | `SystemServerFeatures` and `CommonPackageFeatures` register the correct `FeatureId`, target, phase, and preference key.  Verifies the `isEnabled` predicate returns true/false for the correct preference values. | Does **not** prove that `FeatureInstallRegistry` skips installing hooks and observer when the feature is initially disabled.  Does not prove install-time topology. |

### Verdict

- The tests cover the contract surface of the current implementation well for a JVM test harness.
- No test exercises the real `system_server` class loader, real `WindowSurfacePlacer`, or real `RemotePreferences` listener thread.
- No test proves the **initial-enabled vs initial-disabled topology** (whether hooks and observer are present/absent based on install-time preference).
- No allocation/profiling test exists for the InsetsSource hot path.

---

## C0.9 Hot-path Helper Graph

### H1: `InsetsSource.setFrame`

```text
SetFrameCallback.intercept(chain)
  -> StatusBarHeightConfig.enabled   (volatile Boolean read)
  -> statusBarType == TYPE_UNRESOLVED?   (local Int compare)
  -> StatusBarHeightConfig.configuredPx  (volatile Int read)
  -> readSourceType(source)
       -> typeField?.getInt(source)      (cached Field read, if typeField != null)
       -> or XposedHelpers.callMethod(source, "getType")
            -> findMethodBestMatch(...)  (cached in XposedHelpers.noArgMethodCache)
            -> Method.invoke
  -> adjustArgs(chain, ...)
       -> chain.getArg(0)
       -> computeStatusBarFrameBottom(...)  (pure primitive arithmetic)
       -> copyRect(firstArg)             (only if changed; allocates one Rect)
       -> arrayOf(adjusted)              (only if changed; allocates one Array<Any?>)
  -> chain.proceed() / chain.proceed(adjusted)
```

Hot-path cost when no status bar source:
- 1 volatile Boolean read
- 2-3 Int compares
- 1 volatile Int read
- 1 Int field read (preferred cached `mType`) **OR** 1 cached method invoke (if `typeField == null` at install)
- 1 Int compare
- `chain.proceed()`

No `PrefMap`, no per-call `PrefMap`/String key lookup, no steady-state reflection discovery, no temporary WeakReference, no HashMap, no Pair/Triple, no per-call diagnostic String allocation once the generation-stamp gate has fired.

**H1 mType path (two distinct steady-state branches):**

1. **Preferred mType Field path:** `typeField` is fixed at `SetFrameCallback` construction time.  If it is non-null, the steady-state callback does `Field.getInt(source)` — no generic `XposedHelpers` method-cache lookup/invoke, no String key lookup, no new reflection discovery.

2. **Fallback `getType()` path:** If `typeField == null` at install, the callback executes `XposedHelpers.callMethod(source, "getType")` on **every** call.  `XposedHelpers` internally performs a `findMethodBestMatch` cache lookup (cached in `noArgMethodCache`) and then `Method.invoke`.  This is still a generic cached method lookup/invoke path, not the desired Architecture C hot path.  No new reflection discovery on cache hit, but still more dynamic than a direct `Field.getInt`.

C1 target: Resolver should guarantee a frozen `mType` Field or a frozen `getType()` Method/Invoker; steady-state callback must not call generic `XposedHelpers.callMethod`.

### H2: `DisplayPolicy.layoutWindowLw`

```text
onLayoutWindowLw(chain)
  -> chain.getArg(0)
  -> isWindowState(win)                (cached Class.isInstance or name compare)
  -> StatusBarHeightConfig.enabled      (volatile Boolean)
  -> isKnownStatusBarWindow(win)        (WeakReference array scan, max 4)
  -> isStatusBarWindow(win)
       -> isKnownStatusBarWindow        (second scan if unknown)
       -> readWindowAttrs(win)          (cached Field or fallback)
       -> readAttrsType(attrs)          (cached Field or fallback)
       -> typeMatchObserved / fallbackProbeBudget (AtomicInteger only until first match)
       -> rememberStatusBarWindow(win)  (synchronized rebuild of Array<WeakReference>, allocates ArrayList once per new status bar)
  -> statusBarWindowRef = WeakReference(win)  (only on first match / mismatch)
  -> tryGetWindowDisplayMetrics(win)    (cached Method invoke or fallback)
  -> getDisplayId(win)                  (cached Method invoke or fallback)
  -> recomputePx(metrics)               (only on displayId==0 and density changed; synchronized)
  -> StatusBarHeightConfig.configuredPx (volatile Int)
  -> claimLiveLogStamp(...)             (AtomicLongArray read/update)
  -> applyStatusBarWindowHeight(win, configuredPx)
       -> readWindowAttrs(win)
       -> XposedHelpers.getAdditionalInstanceField(win, ORIGINAL_STATUS_BAR_HEIGHT_KEY)
       -> XposedHelpers.setAdditionalInstanceField(win, ..., currentHeight)   (first hit only)
       -> XposedHelpers.setIntField(attrs, "height", configuredPx)
  -> chain.proceed()
  -> readWindowFrame(win)               (cached Method invoke or fallback)
  -> claimLiveLogStamp(...)             (log window frame, once per generation/display)
```

Concerns:
- `isStatusBarWindow` for an unknown window does reflection to `mAttrs` and `type`.  After `typeMatchObserved == true`, only `readAttrsType` is done for unknown windows; the expensive packageName/toString probe stops.
- `rememberStatusBarWindow` uses `synchronized(this)` and `ArrayList<WeakReference<Any>>` allocation.  This is a warm path (only when a new status bar WindowState appears).
- `applyStatusBarWindowHeight` uses `XposedHelpers.getAdditionalInstanceField` / `setAdditionalInstanceField` on the **WindowState instance** (not a static map) for the original height.  This is the intended Xposed mechanism but is a per-layout additional-field read.
- `readWindowAttrs` and `readAttrsType` **lazily self-install** the cached `Field` on first use.  The first hit for an unknown WindowState pays reflection discovery; subsequent hits use the cached Field.

### H3: `WindowState.setFrames`

```text
onSetFrames(chain)
  -> StatusBarHeightConfig.enabled      (volatile Boolean)
  -> chain.thisObject
  -> isKnownStatusBarWindow(win)        (WeakReference array scan, max 4)
  -> chain.getArg(0)
  -> clientFramesClassMismatch(...)     (Class.isInstance or simpleName compare)
  -> getDisplayId(win)                  (cached Method invoke or fallback)
  -> StatusBarHeightConfig.configuredPx (volatile Int) or configuredPxFor(...) for non-zero display
  -> readClientWindowFrame(clientFrames) (cached Field get or XposedHelpers fallback)
  -> computeStatusBarFrameBottom(...)    (pure primitive arithmetic)
  -> if (changed) frame.bottom = newBottom
  -> chain.proceed()
```

No allocation on the steady-state hot path unless the frame bottom actually changes.

### H4: `DecorInsets.Info.update`

```text
onDecorInsetsInfoUpdate(chain)
  -> chain.proceed()                    (original must run first)
  -> StatusBarHeightConfig.enabled      (volatile Boolean)
  -> args[0] null check, args[1] Int check
  -> decorInfoNonDecorInsetsField.get(info)   (cached Field)
  -> decorInfoNonDecorFrameField.get(info)    (cached Field)
  -> configuredPxForDecorInfo(args[0])   (DisplayContent -> getDisplayMetrics or fallback)
       -> StatusBarHeightConfig.currentState()   (allocates new State object)
       -> if displayContent != null:
            XposedHelpers.callMethod(displayContent, "getDisplayMetrics") as? DisplayMetrics
  -> computeNonDecorTop / computeNonDecorFrameTop
  -> if changed, mutate the two Rects
  -> logLive (once per generation/rotation)
```

**H4 allocation facts:**
- `configuredPxForDecorInfo` calls `StatusBarHeightConfig.currentState()`, which allocates a new `State` data object on **every** `DecorInsets.Info.update` invocation.
- If `displayContent != null`, it calls `XposedHelpers.callMethod(displayContent, "getDisplayMetrics")`, which is a generic dynamic helper path (XposedHelpers method cache + `Method.invoke`).
- Therefore H4 is **not zero-allocation** today.

C1 targets for H4:
- Eliminate `currentState()` per-call allocation by reading a frozen `Abi`/`Config` directly.
- Pre-resolve `DisplayContent.getDisplayMetrics()` Method and cache the `DisplayMetrics` or use a frozen `DisplayMetricsProvider`.

### Helper allocation summary

| Hot path | Steady-state allocation | First-hit / cold allocation |
|----------|-------------------------|----------------------------|
| H1 InsetsSource.setFrame | `chain.proceed()` only if unchanged; one `Rect` + one `Array<Any?>` if changed and only for status bar source. | `typeField` is fixed at install; no self-install.  Generic `getType()` fallback cost on every call if `typeField == null`. |
| H2 layoutWindowLw | `WeakReference(win)` only when a new status bar is seen; `ArrayList` + new `Array<WeakReference>` during `rememberStatusBarWindow` for a new bar; per-layout `additionalInstanceField` read. | `windowStateAttrsField` / `layoutParamsTypeField` self-install on first `mAttrs` read for an unknown class. |
| H3 setFrames | None (only mutates `Rect.bottom` if changed). | `clientWindowFramesClass` resolved at install. |
| H4 decorInfoUpdate | **One `StatusBarHeightConfig.State` data object per call** via `currentState()`.  Generic `DisplayContent.getDisplayMetrics()` dynamic call if displayContent != null. | `decorInfo*` fields resolved at install. |

---

## C0.10 Existing Evidence

| # | Evidence | Status |
|---|----------|--------|
| E1 | StatusBarHeight device evidence (fuxi / Xiaomi 13) | NO BASELINE EVIDENCE.  `tasks/completed/FEATURE-A14-STATUS-BAR-HEIGHT-INSETS.md` states `NOT DEVICE_VERIFIED` for R3/R4.  The r14.18.8 release APK smoke test is claimed in the Architecture C start doc but no device log is in this repo. |
| E2 | SystemUI/system_server memory evidence | NO BASELINE EVIDENCE.  `docs/performance/P0_RUNTIME_BASELINE_AND_AUDIT_PROTOCOL.md` provides process-level counts but no per-feature memory measurement for StatusBarHeightInsets. |
| E3 | Feature catalog evidence | `docs/performance/P0_RUNTIME_BASELINE_AND_AUDIT_PROTOCOL.md` lists 245 registered features, 50 system_server, 8 ANY.  `StatusBarHeightInsetsFeature` is one of the 50 `SYSTEM_SERVER_STARTING` features. |
| E4 | R8 evidence | `docs/performance/M4_3_R8_KEEP_NARROWING_2026-08-09.md` documents keep-rule narrowing but does not explicitly cover `SystemStatusBarInsetsHooks` or `StatusBarHeightConfig`.  No r14.18.8 R8 mapping for these classes is in the repo. |
| E5 | Provenance | `app/build.gradle.kts` `BuildConfig.BUILD_REVISION` is driven by `buildRevision` property; r14.18.8 release was built from a detached worktree with SHA `2c4efeaf` and versionCode 197.  The release APK SHA-256 is `39FCAE4D9213A24192F79B31C1EF78F6955A5A41E6E1BE9839BC535B02ECA989` per the Architecture C start doc. |

---

## C0.11 C1 Architecture Split Proposal

The conceptual model for C1 must be at least:

```text
StatusBarHeightAbi           (frozen ABI description from Resolver)
StatusBarHeightResolver      (cold/late resolve; outputs Abi)
StatusBarHeightConfig        (immutable snapshot; volatile publish)
StatusBarHeightRuntime       (bounded, owner-safe runtime state)
StatusBarHeightPolicy        (pure-ish decisions)
StatusBarHeightEffect        (ABI access and side effects on framework objects)
StatusBarHeightHooks         (thin Xposed chain/arg/proceed shells)
```

Physical files are **not** required to be exactly 7.  The split must change the runtime contract, not just move code.

### Proposed future homes for existing members

```text
mods/statusbarheight/
├ StatusBarHeightAbi.kt              (InsetsSourceAbi, InsetsTypeInfo, InsetsTypeEncoding, RawTypeInfo)
├ StatusBarHeightResolver.kt         (resolveInsetsSourceAbi, resolveWindowManagerAbi,
│                                      resolveClientWindowFramesClass, resolveDeclaredField,
│                                      resolveIntField, selectTypeEncoding, resolvePublicTypes,
│                                      resolveLegacyTypes, getStaticInt, hasMethod, safePublicType)
├ StatusBarHeightConfig.kt           (immutable State snapshot + AtomicReference / volatile ref)
├ StatusBarHeightRuntime.kt          (statusBarWindowRef, statusBarWindows, typeMatchObserved,
│                                      fallbackProbeBudget, lastRefreshGeneration, log sets/stamps)
├ StatusBarHeightPolicy.kt           (computeStatusBarFrameBottom, computeNonDecorTop,
│                                      computeNonDecorFrameTop, isStatusBarWindow, isKnownStatusBarWindow)
├ StatusBarHeightEffect.kt           (readWindowAttrs, readAttrsType, readClientWindowFrame,
│                                      getDisplayId, getDisplayMetrics, tryGetWindowDisplayMetrics,
│                                      readWindowFrame, applyStatusBarWindowHeight,
│                                      restoreStatusBarWindowHeight, requestStatusBarTraversal,
│                                      invalidateDecorInsets)
└ StatusBarHeightHooks.kt            (StatusBarInsetsHeightHook, install* methods,
│                                      SetFrameCallback, onLayoutWindowLw, onSetFrames,
│                                      onDecorInsetsInfoUpdate)
```

### Member-to-layer mapping

| Current member | Proposed layer | Reason |
|----------------|----------------|--------|
| `InsetsSourceAbi`, `InsetsTypeInfo`, `InsetsTypeEncoding`, `RawTypeInfo` | `StatusBarHeightAbi` | Frozen ABI description, no behavior. |
| `resolveInsetsSourceAbi`, `resolveWindowManagerAbi`, `resolveClientWindowFramesClass`, `selectTypeEncoding`, `resolvePublicTypes`, `resolveLegacyTypes`, `getStaticInt`, `hasMethod`, `safePublicType` | `StatusBarHeightResolver` | Cold-path reflection and ABI selection only. |
| `StatusBarHeightConfig` | `StatusBarHeightConfig` (keep) | Already a process config; C1 should make it an immutable `State` snapshot published through a single `@Volatile` reference or `AtomicReference` instead of six separate volatile fields. |
| `enabled`, `configuredDp`, `configuredPx`, `densityDpi`, `density`, `generation`, `rawPreferenceDp` | `StatusBarHeightConfig.State` | Belong to the immutable snapshot.  `generation` is an `AtomicLong` used for log gating — it can stay with Config or move to Runtime. |
| `statusBarWindowRef`, `statusBarWindows`, `typeMatchObserved`, `fallbackProbeBudget`, `lastRefreshGeneration`, logged sets/stamps | `StatusBarHeightRuntime` | Process-scoped, bounded, owner-safe runtime state. |
| `computeStatusBarFrameBottom`, `computeNonDecorTop`, `computeNonDecorFrameTop` | `StatusBarHeightPolicy` | Pure, allocation-free geometry decisions. |
| `isStatusBarWindow`, `isKnownStatusBarWindow`, `rememberStatusBarWindow` | `StatusBarHeightPolicy` (identity matching) + `StatusBarHeightRuntime` (state) | Policy decides identity; Runtime holds the remembered WeakReferences.  `rememberStatusBarWindow` mutates Runtime state, so it should be a Policy-driven Runtime update, not a Policy side effect on framework objects. |
| `readWindowAttrs`, `readAttrsType`, `readClientWindowFrame`, `tryGetWindowDisplayMetrics`, `getDisplayId`, `readWindowFrame` | `StatusBarHeightEffect` or `StatusBarHeightResolver` (if pre-resolved invokers) | These are ABI access/effect helpers.  The Resolver should pre-resolve Field/Method references; the Effect layer invokes them on live objects. |
| `applyStatusBarWindowHeight`, `restoreStatusBarWindowHeight`, `requestStatusBarTraversal`, `invalidateDecorInsets` | `StatusBarHeightEffect` | Side effects on framework objects, cold or observer-only. |
| `StatusBarInsetsHeightHook`, `installDisplayPolicyHook`, `installWindowStateHook`, `installDecorInsetsInfoHook` | `StatusBarHeightHooks` | Xposed wiring, cold only. |
| `SetFrameCallback` | `StatusBarHeightHooks` | Thin hot hook. |
| `onLayoutWindowLw`, `onSetFrames`, `onDecorInsetsInfoUpdate` | `StatusBarHeightHooks` | Thin hot hook shells; should call Policy for decisions and Effect for ABI access/mutation. |

### Important: this must not become "one big file split into many files"

For C1 to be a valid Architecture C PoC, the split must change the runtime contract:

1. **Resolver must run only at install** and produce a frozen `StatusBarHeightAbi` object.  Late-freeze is allowed for A19-A25 but must be "resolve once, then freeze".
2. **Config must become an immutable snapshot** published through one `@Volatile` reference (or `AtomicReference<State>`), replacing six separate volatile fields.
3. **Runtime state must be explicitly bounded** and owned; no process singleton strongly referencing a `WindowState`.
4. **Policy must be pure-ish**; geometry decisions must not allocate.
5. **Effect must isolate ABI access and side effects**; no per-call generic `XposedHelpers.callMethod` on steady-state hot paths.
6. **Hooks must be thin**; no per-call reflection discovery, no per-call `PrefMap`/String lookup, no per-call WeakReference creation.

---

## C0.12 C1 Explicit No-Regression Targets

The first Architecture C implementation must freeze or eliminate the following steady-state regressions without introducing behavior changes:

| # | Target | Current state | C1 requirement |
|---|--------|---------------|----------------|
| N1 | H1 generic `getType()` fallback | If `mType` Field cannot be pre-resolved, every `SetFrameCallback.intercept` calls `XposedHelpers.callMethod(source, "getType")`. | Pre-resolve `mType` Field or `getType()` Method/Invoker at install; fail closed if neither is available. |
| N2 | H2 `mAttrs`/`type` lazy self-resolution | `readWindowAttrs`/`readAttrsType` fall back to `XposedHelpers.getObjectField`/`getIntField` on the first unknown class, then cache the Field. | Resolver pre-resolves `WindowState.mAttrs` and `WindowManager.LayoutParams.type` fields; no lazy discovery in hot path. |
| N3 | H2 per-layout `additionalInstanceField` lookup for original height | Every layout reads the original height from the WindowState's additional instance field. | Design an owner-safe way to store original height; if additional-instance field is kept, pre-resolve the Xposed map access.  Replacing it with a `WeakHashMap<WindowState, Int>` in Runtime would reintroduce owner retention and must be justified. |
| N4 | H2 `getDisplayMetrics`/`getDisplayId` generic fallback | `tryGetWindowDisplayMetrics` and `getDisplayId` fall back to `XposedHelpers.callMethod` if the cached Method is null. | Pre-resolve both Methods; fallback only as fail-closed. |
| N5 | H4 `currentState()` allocation | `configuredPxForDecorInfo` allocates a new `State` data object on every `DecorInsets.Info.update`. | Read from an immutable Config snapshot directly; no per-call `currentState()`. |
| N6 | H4 generic `DisplayContent.getDisplayMetrics()` | `configuredPxForDecorInfo` uses `XposedHelpers.callMethod` when displayContent != null. | Pre-resolve `DisplayContent.getDisplayMetrics()` Method at install/late-freeze. |

Constraint: C1 does **not** require blindly deleting all fallback.  Fallback must become "cold/late resolve -> capability -> frozen hot path" or "fail closed".

---

## C0.13 Risk Register

| # | Risk | Current evidence | Architecture C risk | Planned guard |
|---|------|------------------|---------------------|---------------|
| R1 | P0 behavior parity | Unit tests pass on JVM; `NOT DEVICE_VERIFIED` | Split could change InsetsSource / WindowState interaction order, especially if Policy/Hooks are extracted separately. | Lock the exact `chain.proceed()` count and arg-rewrite order in C1 tests before changing code. |
| R2 | P0 system_server crash | Fatal errors propagate; `catch (Throwable)` are bounded | A regression in Resolver could install a callback with `TYPE_UNRESOLVED` that does extra reflection per call, or a Policy error could suppress fatal. | Keep `FatalErrors.unwrapAndRethrowIfFatal` at every boundary; never add `catch` that swallows OOM. |
| R3 | P1 lifecycle | `WeakReference` to `WindowState`, `additionalInstanceField` for original height | Refactor could accidentally turn `WeakReference` into strong reference or forget to clear `statusBarWindows`.  Replacing additional-instance field with a static map could leak WindowState. | Code review every field in the new `Runtime` class for strong/weak semantics.  Any new owner state must use WeakReference or additional-instance field with explicit cleanup. |
| R4 | P1 concurrency | `@Volatile` fields + `AtomicLongArray`; `reconfigure` is `synchronized` | Publishing six separate volatiles is not atomic; a multi-field update can be observed partially.  `currentState()` creates a new object on every call. | Move `StatusBarHeightConfig` to a single immutable `State` snapshot with one `@Volatile` reference or `AtomicReference`. |
| R5 | P1 hot regression | H1/H2/H4 hot path analysis shows minimal but non-zero allocation | C1 abstraction could add interface dispatch, `Pair`, `data class` copies, or per-call reflection. | Audit helper graph after C1; require 0 allocation on unchanged steady-state paths.  Eliminate H4 `currentState()` and H4 generic `callMethod`. |
| R6 | P1 ROM ABI fallback | Resolver supports MODERN_PUBLIC, LEGACY_INTERNAL, UNSUPPORTED | Refactor could drop a fallback branch or change `selectTypeEncoding` sentinel rules. | Preserve `StatusBarInsetsResolverTest` assertions and add C1 ABI structural tests. |
| R7 | P2 cold-start regression | Feature catalog has 50 system_server specs; StatusBarHeight is one | Splitting into 5-6 files does not change cold cost if object count stays the same; but adding a big `StatusBarHeightAbi` data class may increase `clinit` and retained heap. | Measure install-time allocation and R8 shrink; do not add unnecessary fields. |
| R8 | P0 install-time topology | Feature is skipped when disabled at process start; system_server Insets live activation requires system_server restart/reboot; PACKAGE_READY resource live activation requires corresponding package/process reload. | C1 refactor could accidentally always install hooks+observer (semantic change disguised as refactor) or break enabled->disabled live update.  It could also conflate the two topologies. | Add C1 tests for initial-enabled and initial-disabled topology for both system_server Insets and PACKAGE_READY resource paths.  Document exactly which process must restart for each.  Never install hooks+observer when disabled unless an explicit separate semantic change is approved. |

---

## C0.14 C1 Validation Plan

### Behavior

- Re-run and extend `StatusBarInsetsDecisionTest` and `StatusBarHeightLiveTest` to cover disabled, enabled target, non-target, unknown WindowState, target identity, density unchanged/changed, config live update, null preference, fallback ABI, partial ABI, original RuntimeException, OOM/fatal, and lifecycle cleanup.

### Install-time topology (new)

- `StatusBarHeightInsetsFeature` must be `SKIPPED` when `system_statusbarheight <= 11`.
- When skipped, `PreferenceObserverRegistry` must not contain `statusBarHeightObserver`.
- When enabled at process start, all Insets hooks and `statusBarHeightObserver` must both be installed.
- Changing system_server Insets preference from disabled to enabled at runtime must **not** install hooks unless a separate semantic change is approved.  Reactivation requires system_server process restart or device reboot.
- `StatusBarHeightFeature` must be `SKIPPED` for a package when `system_statusbarheight <= 11`.
- When skipped, no `replacePkgAndFrameworkValue` calls are made for that package.
- Changing PACKAGE_READY resource preference from disabled to enabled at runtime must **not** update resources unless a separate semantic change is approved.  Reactivation requires the corresponding package/process to reload and re-enter `PACKAGE_READY`.

### Structural

- Core hot ABI (A1-A18) must be resolved and frozen before the corresponding hot callback reaches steady-state execution.  Hot callbacks must not perform `getDeclaredField`/`getDeclaredMethod` discovery or generic resolver re-entry.
- Late ABI (A19-A25) may be resolved on first real object / owner appearance, safely published, then frozen and reused.  Steady-state rediscovery is forbidden.
- `StatusBarHeightResolver` must not claim to pre-resolve A19-A25 at process entry if those members are only reachable after a real framework object is created.
- Verify that `StatusBarHeightConfig` publishes an immutable `State` snapshot through a single reference.
- Verify that `StatusBarHeightRuntime` does not contain strong `WindowState` references.

### Concurrency

- Test that preference changes publish the new `State` snapshot atomically and that the hot path never observes a mixed old/new config.
- Test that `statusBarWindows` updates do not cause the hot path to skip a known status bar or observe a stale array.

### Fatal

- For every new boundary (Resolver, Config, Runtime, Policy, Effect, Hooks), verify `FatalErrors.unwrapAndRethrowIfFatal` is called first in any `catch (Throwable)`.
- Verify `OutOfMemoryError` / `ThreadDeath` / `VirtualMachineError` propagate through `chain.proceed()` without modification.

### Device A/B

- A: r14.18.8 oracle APK (`BUILD_REVISION=2c4efeaf`).
- B: Architecture C C1 PoC APK (new `BUILD_REVISION`).
- Same device, same ROM, same Xposed environment.
- Verify: SystemUI restart, reboot, status bar height, fullscreen, rotation, notification shade, lockscreen, launcher, freeform, IME, screen on/off, SystemUI/system_server crash/ANR, PSS / Java Heap / Native Heap, no new frame/layout regression.

### Rollback rule

- C1 must be a single feature slice; any regression in StatusBarHeight must be rollback-able by reverting the single commit.
- No changes to CommonPackageFeatures, SystemServerFeatures, or FeatureId registries should be required.

---

## C0.15 Verification Status

```text
python tools/verify.py fast --changed
```

- `git diff --check`: passed.
- Python static gates (`check-invariants --changed`, `audit-feature-semantics --validate`, `check_observer_key_contract`) passed.
- Gradle / Android unit tests: **blocked** by missing JDK 25 toolchain on this Windows environment.  No code change is required.

### C1 toolchain readiness

```text
C1_TOOLCHAIN_READY = false
```

C1 implementation **MUST NOT** begin until the baseline targeted JVM tests can actually run in the current Devin environment.  The missing JDK 25 toolchain is an environment blocker, not a code blocker.

---

## C0.16 Final Statement

```text
C0_CORRECTIVE_READY_FOR_INDEPENDENT_AUDIT
```

This C0-R corrective is a docs-only artifact.  No production, test, tool, or baseline files were modified.  C1 implementation must not begin until:

1. This C0-R is independently audited.
2. The JDK 25 toolchain is available and the targeted tests can run.
