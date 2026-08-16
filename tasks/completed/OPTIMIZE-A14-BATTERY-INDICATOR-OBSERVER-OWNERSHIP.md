# OPTIMIZE-A14-BATTERY-INDICATOR-OBSERVER-OWNERSHIP

- Platform: A14
- Status: Done
- Priority: P0
- Owner: Codex
- Reviewer: ChatGPT / human
- Related version: A14
- Cross-repo task: no

## 目标

让 `BatteryIndicator` 的 owner-bound preference observer 不再强持有所属 View；即使 ROM
生命周期漏掉 `onDetachedFromWindow()`，进程级 additional-field 存储也不能通过 observer
反向保留旧 View。

## 当前问题

`BatteryIndicator.init()` 注册的匿名 observer 直接访问 `mTesting`、`viewScope`、
`updateParameters()` 和 `update()`，因此 observer 会捕获外部 `BatteryIndicator`。正常
detach 会精确解绑，但 additional-field value 与弱 owner key 之间仍可能形成
`map -> observer -> View` 保留链。

## 允许修改

- `app/src/main/java/tv/withaibuild/customiuizer/utils/BatteryIndicator.kt`
- `app/src/test/java/tv/withaibuild/customiuizer/utils/BatteryIndicatorLifecycleContractTest.kt`
- 本任务记录与 `docs/performance/A14_OPTIMIZATION_PLAN.md` 的执行状态

## 必须保持

- Receiver 继续使用 `ReceiverRegistry` 提供的 owner 参数，不改注册协议；
- preference key 过滤、`mTesting` 条件、协程调度和 UI 更新顺序不变；
- `onDetachedFromWindow()` 的 callback 移除、observer/receiver 解绑和 scope 取消顺序不变；
- 不修改 `XposedHelpers.java`、`PreferenceObserverRegistry` 或其他 View；
- 不吞掉 `OutOfMemoryError`，不改变 API 101/102 边界。

## 实现要求

- observer 只通过 `WeakReference<BatteryIndicator>` 保存 owner；
- owner 已回收时直接返回；
- 保留显式 unregister 作为正常生命周期路径；
- 增加针对编译后 observer 字段所有权的回归测试。

## 非目标

- 不修改用户明确暂缓的 `AudioVisualizer`；
- 不处理三处调用栈扫描、设置 UI、Feature 安装表或 R8；
- 不创建 APK、Tag、Release 或 PR。

## 验收标准

- [x] observer 不含 `BatteryIndicator` 强引用字段
- [x] observer 含唯一的 owner `WeakReference`
- [x] 原有 BatteryIndicator 生命周期合同测试通过
- [x] 针对性测试、fast、full 和 `git diff --check` 通过
- [x] 最终 diff 已审查，工作区无未解释改动

## 验证

```powershell
python tools/verify.py fast --tests BatteryIndicatorLifecycleContractTest
python tools/verify.py fast --changed
python tools/verify.py full
git diff --check
```

## 完成记录

- Base SHA: `3eb1d6dd2d4efdafefd74d4f3d3492e777307c1f`
- Final SHA: 当前分支任务完成提交（精确 SHA 见最终报告）
- Commits: `perf(a14): weaken BatteryIndicator observer ownership`
- Behavior changed: preference observer 只通过 `WeakReference` 保存 View owner；用户可见行为不变
- Verification: targeted、`fast --changed`、`full`、source hazard 与 `git diff --check` PASS
- Device evidence: NOT RUN；本任务仅有静态、JVM、编译和 lint 证据
- Known limits: 未采集真实 SystemUI heap retained-owner 数据；`AudioVisualizer` 由 owner 明确暂缓
