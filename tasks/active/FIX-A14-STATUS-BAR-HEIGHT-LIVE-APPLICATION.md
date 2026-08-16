# FIX-A14-STATUS-BAR-HEIGHT-LIVE-APPLICATION

- Platform: A14
- Status: Engineering complete | Device validation deferred | Release waiver approved by owner | PARKED — NOT RUN / ENVIRONMENT BLOCKED
- Priority: P0
- Owner: Devin
- Reviewer: ChatGPT / human
- Related version: A14
- Cross-repo task: no

## 目标

让状态栏高度修改后实时、可靠地应用到 SystemUI 状态栏窗口、WindowManager 布局和 `statusBars` Insets，而不需要完整重启手机。

## 当前唯一剩余门禁

- `fuxi 44 -> 40 -> 12 -> 44 -> disabled` 不重启实机验证尚未执行。
- 原因：当前环境没有 A14/fuxi 设备。
- 状态：NOT RUN / ENVIRONMENT BLOCKED。
- 不是 TEST FAIL。
- 任务继续留在 `tasks/active`。
- Release gate waived for r14.18.0 by project owner on 2026-08-06. Device validation remains pending and this task stays active.

## R3 已修复的工程问题

- 非 status bar WindowState 热路径污染：非 status bar 窗口在 `metrics`、`displayId`、`recomputePx`、`diagnostics`、`WeakReference` 之前立即返回。
- `custom -> disabled` 不恢复原始高度：`custom -> disabled` 请求一次安全 `requestTraversal` 并在下一次 `layoutWindowLw` 中恢复真实原始 `mAttrs.height`。
- `performSurfacePlacement fallback`：删除所有直接 `performSurfacePlacement` fallback；`requestTraversal` 不可用时安全等待自然 layout。

## 实机状态

NOT RUN / ENVIRONMENT BLOCKED

R3 修正已通过本地静态、单元测试与 Debug 构建验证，但当前会话未连接 `fuxi` 物理设备，无法执行 `44 -> 40 -> 12 -> 44 -> disabled` 实机验证。本任务保留在 `tasks/active` 作为 parked 项，待 fuxi 连接后继续验证；在实机验证通过前不得重新进入 `tasks/completed`。

## R3 修正要求

1. 非 status bar 窗口在 `metrics`、`displayId`、`recomputePx`、`diagnostics`、`WeakReference` 之前立即返回。
2. `custom -> disabled` 请求一次安全 `requestTraversal` 并在下一次 `layoutWindowLw` 中恢复真实原始 `mAttrs.height`。
3. 删除所有直接 `performSurfacePlacement` fallback；`requestTraversal` 不可用时安全等待自然 layout。
4. 不创建替代锁、线程、Executor、CoroutineScope 或轮询。
5. 多 display 不得反复污染全局 `density/generation`；非主屏 status bar 使用本地 `configuredPxFor` 计算。
6. `chain.proceed` 恰好一次。

## 提交

- Engineering SHA: `48e63d40` (原始 R2 engineering commit)
- R3 corrective SHA: `93a7394` (R3 修正 engineering commit)
- Previous closure SHA: `d333a0f5` (被撤销的错误 closure)
- Reopen / Closure SHA: 当前 branch HEAD（即把本记录移回 `tasks/active` 的 reopen/closure commit，见 git log）

注意：不把 `48e63d40` 标为 `Final SHA`。

## 验证

以下为本任务本地可执行的静态、单测与构建验证，不是实机行为验证：

- `python tools/verify.py fast --changed` — PASS
- `python tools/verify.py fast --tests "*StatusBarHeight*"` — PASS
- `python tools/verify.py fast --tests "*StatusBarInsets*"` — PASS
- `python tools/verify.py full` — PASS
- `git diff --check` — PASS
- `.\gradlew.bat :app:assembleDebug` — PASS

## 构建产物

- 本次 R4.1 不生成任何 APK。
- 历史 Debug APK（R3 修正工程验证产物，保留为诊断 provenance）：
  - APK: `app/build/outputs/apk/debug/CustoMIUIzer-A14-r14.16.1-debug.apk`
  - APK SHA-256: `E464FAB2C5631A772627D71BD1BE6CA6B0ECAF48FCBD5E99E5078AB6EDADA99A`
  - Build revision: `93a7394` (R3 corrective)
  - Signature: Debug
  - 类型：diagnostic build，不是 release candidate；不安装、不作为 develop、不作为 release candidate。
