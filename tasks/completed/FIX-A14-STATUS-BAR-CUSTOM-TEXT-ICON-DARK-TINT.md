# FIX-A14-STATUS-BAR-CUSTOM-TEXT-ICON-DARK-TINT

- Platform: HyperOS 1 / Android 14 / SDK 34
- Status: Done
- Priority: P0
- Owner: Devin
- Reviewer: ChatGPT / human
- Related version: A14
- Cross-repo task: no

## 背景
type 91/92 自定义文字图标在左侧状态栏使用一段时间后，可能出现文字变黑并在深色背景下不可见；右侧布局没有观察到同类问题。重启桌面或 SystemUI 后可能暂时恢复。

## 目标
修复 type 91/92 自定义文字图标在左侧状态栏的 dark tint 失效问题，使其正确跟随 SystemUI 当前的 tint / darkIntensity / light/dark icon mode，同时保持右侧路径不变。

## 主要代码范围
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/DeviceInfoMonitor.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/DarkTintRegistrationState.kt`（待创建）
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/StatusBarDisplayRegistry.kt`（参考/复用）
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/OwnedRegistrations.kt`（参考/复用）

## 调用链
完整跟踪：

```text
Preference
→ ConfigSnapshot.batteryAtLeft/tempAtLeft/batteryAtRight/tempAtRight
→ NetworkSpeedController
→ StatusBarIconController slot
→ IconManager / MiuiLightDarkIconManager
→ addHolder
→ createStatusbarTextIcon
→ View attach
→ DarkIconDispatcher registration
→ initial tint application
→ onDarkChanged
→ wallpaper/theme/shade/keyguard transition
→ View detach/recreate
→ receiver unregister
```

需分别区分左侧和右侧的 IconManager、ViewGroup 与 DarkIconDispatcher。

## 追踪到的真实失败路径

### 左侧路径
1. `StatusBarIconsPositionAdjustHook` 构造 `MiuiPhoneStatusBarView` 时，左侧容器与 `StatusBarIconController$DarkIconManager`（或 MIUI 变体）被建立并加入 `StatusBarIconController`。
2. `StatusBarIconList` 构造函数把 `battery_info`/`device_temp` slot 名插入到左侧 icon list。
3. `StatusBarIconController` 添加 icon 时调用 `IconManager.addHolder`。
4. `DeviceInfoMonitor.interceptAddHolder` 是 `addHolder` 的 `before` hook，返回自定义的 `NetworkSpeedView`（`R.layout.statusbar_text_icon`）并 `returnAndSkip`，跳过了 ROM 原本在 `addHolder` 内部执行的 `onIconAdded` / `DarkIconDispatcher.addDarkReceiver` 等生命周期。
5. 自定义 View 被 `addView` 到左侧 `MiuiStatusIconContainer`，因此文本能显示，但 **没有向 `DarkIconDispatcher` 注册 `DarkReceiver`**，也不会被主动同步当前 dark intensity / tint。
6. 壁纸/主题/锁屏/横竖屏/编辑模式等事件触发 `onDarkChanged` 时，左侧文本不会更新颜色；在深色背景下，SystemUI 其他图标反白，而自定义文本仍保持黑色，最终“变黑不可见”。

### 右侧路径
1. `DualRowsStatusbarHook.onFinishInflate` 中，当 `batteryAtRight`/`tempAtRight` 开启时，直接用 `createStatusbarTextIcon` 创建 View。
2. View 被 `addView` 到 `secondRight` 后，代码显式调用 `DarkIconDispatcher.addDarkReceiver(iconView)`，并通过 `state.registrations` 在 generation 替换时 `removeDarkReceiver`。
3. 右侧 View 能正常接收 `onDarkChanged`，颜色随 dark mode 变化，因此未观察到同类问题。

### 缺失不变量
1. `returnAndSkip(iconView)` 后必须补充 `DarkIconDispatcher.addDarkReceiver`，否则左侧缺少 dark tint 生命周期。
2. 每个自定义 View 每个 generation 只能注册一次 dark receiver。
3. View `onDetachedFromWindow`（包括状态栏重建、generation 替换、功能停止）时必须 `removeDarkReceiver`。
4. 注册时必须立即同步当前 tint，不能等下一次 `onDarkChanged`。
5. 不能写死白色/黑色；必须跟随 `DarkIconDispatcher` 的 `tint`/`darkIntensity`/`tintArea`。

## 重点不变量
1. type 91/92 自定义 View 必须兼容 `DarkIconDispatcher.DarkReceiver` 注册。
2. `returnAndSkip(iconView)` 后，外层 `MiuiLightDarkIconManager.onIconAdded` 仍需注册 dark receiver；如被绕过，应在明确 owner 下补充。
3. 左侧和右侧的注册路径不得混用。
4. 新 View 注册后立即获得当前 tint，不等待下一次主题事件。
5. View detach、状态栏 generation 替换或功能停止时释放注册。
6. 旧 View 不得继续收到 dark callback。
7. 同一 View 每个 generation 最多注册一次 dark receiver。
8. 非 91/92 holder 完全放行。
9. 不得通过写死白色/黑色颜色值解决。

## 实现约束
- 所有 `catch (Throwable)` 必须先调用 `FatalErrors.unwrapAndRethrowIfFatal(t)`。
- 不得吞掉 `OutOfMemoryError`、`ThreadDeath` 或 `VirtualMachineError`。
- 热路径不得新增反射扫描、同步 Binder 或高频对象分配。
- 不建立与 `StatusBarDisplayRegistry`、`OwnedRegistrations` 互不协调的第二套生命周期系统。
- 日志仅在安装、注册、替换和释放冷路径输出。

## 测试要求
- 左侧自定义 View 注册 dark receiver
- 右侧既有路径不重复注册
- 同一 View 重复 addHolder 不重复注册
- 新 generation 替换旧 generation
- 旧 View detach 后释放
- 新 View 获得当前 tint
- 非 91/92 holder 完全放行
- 普通反射异常安全降级
- fatal error 原样抛出

## 验证
- `python tools/verify.py fast --changed`
- `.\gradlew.bat :app:testDebugUnitTest`
- `python tools/verify.py full`
- `git diff --check`
- `.\gradlew.bat :app:assembleDebug`

## 完成记录
- Base SHA: 5d1a6f639c4daed94c301c8742e8903fc5791de0
- Engineering commit: 4e8d7016a8ff35bedaa1b1292a953c32031562cf
- R2: beb4417a9422d6a9fc14bff8cce80e1e9e35b870
- R3 corrective engineering commit: (to be recorded after push)

## R3 lifecycle corrective closure

### 实机日志结论
- 日志目录：`C:\Users\tv\Downloads\Peengeek\LSPosed_log\r14\r14.16.1-debug\Vector-logs-release-20260805-154107`
- 注入：
  - `tv.withaibuild.customiuizer.r14` 成功加载于 `system` / `com.android.systemui` / `com.miui.home` / `com.android.settings`。
  - `HookSummary stage=onSystemServerStarting process=system installed=41 classMissing=0 memberMissing=0 failed=0`
  - `HookSummary stage=onPackageReady process=com.android.systemui installed=48`
  - `HookSummary stage=post-init process=com.android.systemui installed=49 failed=0`
- 稳定性：采集窗口内没有 SystemUI FATAL、ANR、死亡或重启。
- 路径未触发：日志未出现 `CustomTextIconTintRoute`、`DeviceInfoMonitor`、`battery_info`、`device_temp` 关键字，因此本次日志只能说明模块稳定加载，不能直接证明 dark tint 已修复。
- 设备信息：
  - `ro.build.fingerprint = Xiaomi/fuxi_global/fuxi:14/UKQ1.230804.001/V816.0.7.0.UMCTWXM:user/release-keys`
  - `ro.build.version.sdk = 34`
  - `ro.build.version.release = 14`
- 已知 backlog（不纳入本次）：
  - `Failed to hook onDetachedFromWindow method in miui.systemui.controlcenter.windowview.ControlCenterWindowViewImpl`
- 本次实机状态标记：
  - `BOOT_LOG_VERIFIED`
  - `SYSTEMUI_STABILITY_OBSERVED`
  - `DARK_TINT_PATH_NOT_EXERCISED`

### 修正范围
1. `CustomTextIconTintRoute` 拆分 `attach/register`、`detach/unregister`、`terminal dispose` 三个阶段。
2. 注册失败后不保留强引用：tracking 在 terminal dispose 时无条件移除，listener 无论注册是否成功均可移除。
3. 支持 `attach → detach → reattach` 生命周期，普通 detach 不破坏 listener；terminal dispose 后不再注册。
4. 右侧恢复 `StatusBarDisplayRegistry` generation owner：`CustomTextIconTintRoute.register` 返回幂等 `DarkTintRegistrationHandle`，右侧继续 `state.registrations.register(sbView) { handle.release("generation-replaced") }`。
5. Fake dispatcher 立即模拟初始 tint callback，测试覆盖 initial tint、reattach、generation replacement、失败后的清理。
