# A14 Process Exceptions (generated)

This file captures process-routing gaps, package/process mismatches, and targeted verification notes.

## Scope vs code

- Input method packages are routed by `MainModule.java` to `InputMethodInstaller`, but are **not** listed in `scope.list`. This means they will not receive the module unless the user adds them manually in LSPosed.
  - Verification: `WAITING_FOR_SAMPLE` (need LSPosed scope behavior with `staticScope=false`).
- `miui.systemui.plugin` is not in `scope.list`; the module stays in `com.android.systemui` and extracts the plugin `ClassLoader` from `PluginInstance$PluginFactory.createPlugin` at runtime.
  - Evidence: `SystemUIControlCenterHooks.kt` line 60-70 and `ControlCenterPluginHook`.

## Package / process confusion

- `MainModule.onPackageReady` relies on `lpparam.isFirstPackage()` and `lpparam.getPackageName()`; `processName` is only used for explicit denies.
- `com.android.settings` main process is allowed; `com.android.settings:remote` is explicitly refused.
- `com.miui.securitycenter` main process is allowed; `com.miui.securitycenter.bootaware` is explicitly refused.
- `com.android.location.fused` and packages starting with `com.android.networkstack` are refused unconditionally.
- `com.android.systemui` is the only package that triggers `ReflectionCache.onSafeLifecycle` and `SystemUIInitializer.init` post-init.
- `com.miui.home` triggers `LauncherInstaller` and, when selected, `GenericAppInstaller.installPostAttach`.

## Feature target `ANY`

- `StatusBarHeightFeature` and `AlarmCompatFeature` in `CommonPackageFeatures` use `FeatureTarget.ANY`.
- `StatusBarHeightFeature` is gated by `system_statusbarheight`; if enabled, `hasEnabledFeature()` returns true for every package, but `FeatureInstallState` is per-process so installation is idempotent per process.
- `AlarmCompatFeature` is additionally gated by `various_alarmcompat_apps`, so it only installs in the selected packages.

## ClassLoader and DexKit

- Most package-ready features use `lpparam.classLoader`.
- `GuardProviderInstaller` and `MediaInstaller` call `MainModule.loadDexKit()` on demand.
- `ControlCenterPluginHook` extracts the `miui.systemui.plugin` `ClassLoader` and caches it in `SystemUIControlCenterHooks.pluginLoader`.
- `ReflectionCache.onSafeLifecycle` is called for `com.android.systemui` and `com.miui.home` before installer dispatch.

## API 101/102 boundary

- `MainModule` is compiled against libxposed API 102 but the production `onPackageReady` / `onSystemServerStarting` paths use only API 101 public symbols.
- `XposedApiCapabilities.initialize(getApiVersion())` runs once per process but does not place API-102-only symbols on hot paths.
- `tools/check-invariants.py` blocks `setId`, `replaceHook`, `HotReloadingParam`, `HotReloadedParam` and `getApiVersion()` in callbacks.

