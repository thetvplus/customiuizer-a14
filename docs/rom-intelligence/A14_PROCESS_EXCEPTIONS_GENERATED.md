# A14 Process Exceptions (generated)

This file captures process-routing gaps, package/process mismatches, and targeted verification notes.

## Scope vs code

- Recognized input method packages are routed through `ProcessRouter` to `InputMethodInstaller`; the supported keyboard packages are listed in `scope.list`. Packages outside the static scope receive no callbacks.
  - Verification: optional per-keyboard smoke on target ROM; not a default release gate.
- `miui.systemui.plugin` is not in `scope.list`; the module stays in `com.android.systemui` and extracts the plugin `ClassLoader` from `PluginInstance$PluginFactory.createPlugin` at runtime.
  - Evidence: `ControlCenterPluginHook` and `SystemUIControlCenterHooks.pluginLoader`.

## Package / process confusion

- `MainModule.onPackageReady` first requires `lpparam.isFirstPackage()`, then resolves package and process through `ProcessRouter`. `ProcessScope.isInstallable` rejects unsupported scopes before preference or feature initialization.
- SystemUI, Settings and PermissionController allow their main processes; their secondary processes are refused.
- SecurityCenter allows its main process; `bootaware` and other secondary processes are refused.
- `com.android.location.fused` and packages starting with `com.android.networkstack` are refused unconditionally.
- SystemUI uses `SystemUiBootstrapCoordinator` for its `SystemUIInitializer.init` hook; both SystemUI and Launcher call `ReflectionCache.onSafeLifecycle` before feature installer dispatch.
- `com.miui.home` triggers `LauncherInstaller` and, when selected, `GenericAppInstaller.installPostAttach`.

## Feature target `ANY`

- `StatusBarHeightFeature` and `AlarmCompatFeature` in `CommonPackageFeatures` use `FeatureTarget.ANY`.
- `StatusBarHeightFeature` requires `system_statusbarheight > 11` and a package in its `RESOURCE_PACKAGES` set (`android`, SystemUI or Launcher). Window geometry is owned by system_server's `StatusBarHeightInsetsFeature`.
- `AlarmCompatFeature` is additionally gated by `various_alarmcompat_apps`, so it only installs in the selected packages.

## ClassLoader and DexKit

- Most package-ready features use `lpparam.classLoader`.
- `GuardProviderInstaller` and `MediaInstaller` call `MainModule.loadDexKit()` on demand.
- `ControlCenterPluginHook` extracts the `miui.systemui.plugin` `ClassLoader` and caches it in `SystemUIControlCenterHooks.pluginLoader`.
- `ReflectionCache.onSafeLifecycle` is called by `SystemUiBootstrapCoordinator` and the Launcher branch of `MainModule`.

## API 101/102 boundary

- `MainModule` is compiled against libxposed API 102 but the production `onPackageReady` / `onSystemServerStarting` paths use only API 101 public symbols.
- `XposedApiCapabilities.initialize(getApiVersion())` runs once per process but does not place API-102-only symbols on hot paths.
- `tools/check-invariants.py` blocks `setId`, `replaceHook`, `HotReloadingParam`, `HotReloadedParam` and `getApiVersion()` in callbacks.

