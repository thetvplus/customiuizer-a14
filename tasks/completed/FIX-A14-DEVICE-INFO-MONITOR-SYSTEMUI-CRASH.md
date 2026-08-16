# FIX-A14-DEVICE-INFO-MONITOR-SYSTEMUI-CRASH

- Platform: A14
- Status: Completed (R1 corrective closure)
- Priority: P0
- Owner: Devin
- Reviewer: ChatGPT / human
- Related version: A14
- Cross-repo task: no

## R1 修正目标

1. 所有新增/修改的 `catch (Throwable)` 边界必须先调用 `FatalErrors.unwrapAndRethrowIfFatal(t)`，不得只重抛 `OutOfMemoryError`。
2. 封闭 `publishReadings()` 中 `activeMainHandler` 的 generation TOCTOU：`IconUpdate` 必须携带 generation，主线程 Handler 同时校验 message generation 与自身 handler id。
3. 统一 `NetworkSpeedView` 类路由：提取 `resolveNetworkSpeedViewClassName(classLoader)`，探测、`getSlot` hook 与相关逻辑复用同一结果。
4. 修复 `addHolder` 边界：`mType` 读取、自定义 type 处理全程处于 fatal-aware 边界；非 91/92 放行原方法；失败时安全 `returnAndSkip(null)`。
5. 修复 `findIconManagerGroup()` 候选字段 fallback：逐个尝试 `mGroup`/`mIcons`/`iconGroup`，普通缺失继续下一个，fatal error 立即抛出。

## R1 修正内容

1. **fatal error 边界**
   - `DeviceInfoMonitor.kt`、`DeviceInfoFormatter.kt`、`SystemUIStatusBarHooks.kt` 中所有本轮新增/修改的 `catch (Throwable)` 先调用 `FatalErrors.unwrapAndRethrowIfFatal(t)`，再执行兼容降级。
   - `DeviceInfoMonitor.kt` 的 monitor 热路径保留显式 `catch (oom: OutOfMemoryError) { throw oom }` 以满足 `device-info-monitor-hot-path` 不变式，同时 `catch (t: Throwable)` 内部仍然调用 `FatalErrors.unwrapAndRethrowIfFatal(t)`，避免只重抛 OOM。

2. **generation TOCTOU 封闭**
   - `IconUpdate` 增加 `generation` 字段。
   - `DeviceInfoMonitorState` 接管 `battery` / `temp` 去重状态，提供 `shouldPublish` 与 `commitPublished`。
   - `startNewGeneration()` / `stop()` 重置去重状态，避免新 generation 因旧状态被错误去重。
   - 主线程 Handler 同时检查 `update.generation == myId` 与 `monitorState.isActiveMain(myId)`，旧消息不修改当前状态。
   - `publishReadings()` 不再直接修改 `batteryState`/`tempState`，只通过 `activeMainHandler` 投递携带 generation 的消息。

3. **NetworkSpeedView 路由统一**
   - 提取 `resolveNetworkSpeedViewClassName(classLoader)`，按 AOSP → MIUI 顺序探测并复用结果。
   - `hookNetworkSpeedView` 和 `isStatusbarTextIconSupported` 使用同一解析结果，避免“探测 MIUI、hook AOSP”的分叉。
   - 探测逻辑可注入 `probe` 函数，支持单元测试。

4. **addHolder 边界加固**
   - `interceptAddHolder` 先安全读取 `mType`；无法确定 type 时放行 ROM 原方法。
   - 确认是 91/92 后进入完整 fatal-aware `try/catch`；任意非 fatal 失败 `returnAndSkip(null)`。
   - 外层 `catch` 也调用 `FatalErrors.unwrapAndRethrowIfFatal(t)`，再 `returnAndSkip(null)`。

5. **候选字段解析**
   - 提取 `FieldCandidateResolver`，逐个尝试 `mGroup`/`mIcons`/`iconGroup`。
   - 普通 `NoSuchFieldError` 继续下一个候选；fatal error 立即抛出；找到 `ViewGroup` 立即返回。

## R1 新增/修改测试

- `FatalErrorsTest.kt`：补充 `ThreadDeath`、`VirtualMachineError`、`InvocationTargetException(ThreadDeath)`、`InvocationTargetException(VirtualMachineError)`、`ExecutionException(ThreadDeath)` 不被吞掉的测试。
- `DeviceInfoMonitorStateTest.kt`：补充 generation 切换、旧消息污染、去重状态重置、`commitPublished`/`shouldPublish` 边界。
- `NetworkSpeedViewResolverTest.kt`：测试 AOSP/MIUI 路由选择、均不存在、fatal 传播。
- `FieldCandidateResolverTest.kt`：测试首个字段缺失时继续尝试第二、第三候选。

## 目标

修复或系统性封闭启用 `system_statusbar_batterytempandcurrent` 和 `system_statusbar_showdevicetemperature` 后可能导致 SystemUI 崩溃的问题。

## 主要代码范围

- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/DeviceInfoMonitor.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStatusBarHooks.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/StatusBarDisplayRegistry.kt`（已审计，无代码改动，现有生成清理机制被复用）

## 调用链

完整跟踪：

```text
Feature 安装
→ DeviceInfoMonitor.hook
→ NetworkSpeedController constructor hooks
→ 自定义 icon slot 创建
→ IconManager.addHolder
→ createStatusbarTextIcon
→ registerStatusbarTextIcon
→ 后台温度/电流读取
→ main Handler
→ updateStatusbarTextIcons
→ View 更新、替换和销毁
```

## 找到的具体崩溃路径与封闭措施

1. **NetworkSpeedController 多次构造导致旧 Handler 继续更新**
   - 新 `DeviceInfoMonitorState` 管理 handler generation；每个 `NetworkSpeedController` 创建时 `startNewGeneration()`，旧 handler 在 `handleMessage` 开头检查 `isActive(id)`，若已过期则移除消息并返回。
   - `doMonitorTick` 在读取、发布、调度前多次校验 generation，拒绝旧 generation 更新。

2. **旧 View 在 statusbar 重建/横竖屏/主题切换后继续更新**
   - `SystemUIStatusBarHooks.updateStatusbarTextIcons` 现在跳过 `!isAttachedToWindow` 的 View 并将其从 `statusbarTextIcons` 移除；
   - `setNetworkSpeed` / `setVisibilityByController` 调用失败时回退到直接设置 `View.visibility` 与 `network_speed_number` tag TextView 的 text，避免直接崩溃。

3. **`IconManager.addHolder` hook 中异常导致原方法带着自定义 type 91/92 继续执行**
   - 原 `addHolder` 对未知 type 返回 null，`onIconAdded` 会 NPE/ClassCastException。
   - 新 `interceptAddHolder` 对自定义 type 91/92 做完整 try/catch；任何反射/ROM 兼容失败都 `returnAndSkip(null)` 并记录日志，不再把异常抛给 SystemUI。
   - `hook` 时先探测 `NetworkSpeedView` 类；类不存在则直接跳过功能安装，避免后续自定义 slot 触发崩溃。

4. **`createStatusbarTextIcon`  inflated 失败或子 View 缺失导致 NPE**
   - 添加非空检查：`mNumber` / `mUnit` 不存在时跳过 `setObjectField`。
   - `getIconTextView` 改为可空，并在 `initStatusbarTextIcon` 中处理缺失情况。

5. **sysfs / ROM 反射异常可能逃逸**
   - `DeviceInfoFormatter` 提取为纯函数，I/O 与解析错误隔离并返回 `null`/fallback；
   - `readBatteryProps` / `readCpuTemp` / `shouldShowBatteryInfo` 继续抛出 OOM，其他 Throwable 吞掉；
   - `consecutiveFailCount` 改为 `AtomicInteger`，避免多线程竞争。

6. **Receiver 与 Context 生命周期**
   - `startScreenReceiverLocked` / `stopScreenReceiverLocked` 维持单次注册/释放闭环；
   - `stopMonitoring` 清理 handler、receiver、context 并重置 `DeviceInfoMonitorState`。

## 新增测试

- `DeviceInfoFormatterTest.kt`：格式化、内容选项、单位隐藏、单双排、反转、malformed/空值。
- `DeviceInfoMonitorStateTest.kt`：handler generation 单调递增、旧 generation 拒绝、stop 后 inactive、失败回退 delay。
- `DeviceInfoMonitorBoundaryTest.kt`：sysfs 读取失败、空/异常 properties 安全退化、parse overflow。

## 验证

- `python tools/verify.py fast --changed`：PASS
- `python tools/verify.py full`：PASS
- `.\gradlew.bat :app:testDebugUnitTest`：PASS
- `git diff --check`：PASS
- `.\gradlew.bat :app:assembleDebug`：PASS

## 完成记录

- Base SHA: 9282443705799fbde6accfccdb8da16193aeefdb
- Original engineering commit: 4e126c7b9981b5b642c19c6188e90b81305e817b
- Corrective engineering commit: 0a270d9cdceacbf1ab822c37aaa83e1a3e220ee0
- Current remote branch HEAD: 8e00c5a9573ab27c3013f53148cfa4437c275f0e
- Total commits in this corrective pass: 1
- Behavior changed:
  - `DeviceInfoMonitor` handler 按 generation 失效，主线程消息携带 generation 并双检；
  - `addHolder` 对自定义 type 91/92 增加完整 fatal-aware 边界与 null fallback；
  - `NetworkSpeedView` 类路由统一，探测、`getSlot` hook 和相关逻辑复用同一解析结果；
  - `findIconManagerGroup` 候选字段 `mGroup`/`mIcons`/`iconGroup` 逐个尝试并加 fatal 传播；
  - `DeviceInfoFormatter` / `DeviceInfoMonitor` / `SystemUIStatusBarHooks` 的 `catch (Throwable)` 先调用 `FatalErrors.unwrapAndRethrowIfFatal(t)`；
  - `DeviceInfoMonitorState` 接管去重状态并在 generation 切换/停止时重置；
  - 新增 `FieldCandidateResolver` 与 `NetworkSpeedViewResolver` 测试。
- Verification: PASS
- Debug APK: `app/build/outputs/apk/debug/CustoMIUIzer-A14-r14.16.1-debug.apk`
- APK SHA-256: 3A4482C0C4637D687D4BFBDC8B51DB6FE543DAE01ABFC2FC2A7BE488CC7E4A8D
- APK signature type: Debug
- Device evidence: 无实机日志；静态/单元/构建验证通过。
- Known limits:
  - `StatusBarDisplayRegistry` 未改动，已通过现有测试验证生成隔离；
  - 实机 `addHolder` 真实参数签名、HyperOS 1 具体 `NetworkSpeedView` 字段/方法、自定义 type 被 StatusBarIconController 处理路径仍需实机日志确认；
  - 若 `NetworkSpeedView` 类名或布局资源完全不可用，功能会静默跳过。
