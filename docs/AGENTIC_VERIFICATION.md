# Agent 自主验证

本入口复用[执行分级与证据复用](TESTING.md#执行分级)，不修改 Quick / Standard / Deep / Release、普通 CI 或正式发布条件。目标是按实际修改发现缺陷并保留可复核结果；计划、命令退出成功、无崩溃与截图本身均不是功能通过。

## 修改到验证

```powershell
python tools/agent_verify.py plan --base <任务起点提交>
python tools/agent_verify.py host --base <任务起点提交>
```

`plan` 合并起点至当前工作树的已提交、暂存、未暂存和未跟踪变化；不传 `--base` 只看当前未提交修改。按当前测试源解析 Deck / Island / Dock / 手势的 owner 回归，公共或未映射运行边界保守回到既有 Full。工具消费者运行现有 Python 回归；构建 / ROM / 设备 / 性能要求留在 `pending`，`host` 不执行它们。删除测试或找不到应有行为类不能静默通过。

这只是有界路由，不是完整依赖图。Agent 必须检查实际调用方、失败路径和所有者，补充受影响的跨功能场景；例如 Island 与 Deck 共用窗口交接时两组均需验证。修改不涉及对应输入时复用已有成功报告，按 TESTING 的输入、环境、范围和结果判断，无需新建缓存或证据数据库。空 diff 的 host 返回 2，不能冒充验证通过。

可选的紧凑 Gradle 输出复用现有 `gradle-run` Skill。建立工作流后，将其脚本绝对路径及工作流 ID 分别放入 `A14_GRADLE_RUNNER`、`A14_GRADLE_WORKFLOW`；`verify.py` 的所有 Gradle 调用（含 mutation worktree）进入该工作流，保留原 launcher、任务和退出码。任一变量缺失即失败，默认 CI 不设置这两项。最后通过原工作流 `finish` 释放日志。

## 能力审查与工具选择

| 能力 | 当前可复用 | 缺口与处理 |
| --- | --- | --- |
| Hook 静态可行性 | ROM catalog、matrix、官方 DexSQL 查询、PreparedHookBatch / API 102 Host 回归 | 精确 ABI、定义来源、原生调用及 ClassLoader 分开核对；沿用 [DexSQL](rom-intelligence/DEXSQL.md#与-api-102-的联合验证)，不建立新索引。不同 A14 ROM 逐样本取证。 |
| 运行安装 | 已有 HookDiagnostics、API 102 ID readback、LSPosed 诊断 | 候选 APK 相同不等于宿主已重启加载；记录实际宿主进程、模块 scope、安装结果和特有效果。汇总 installed 数不能归属某个 Hook。缺少日志访问或可观察效果时保留未验证。 |
| 行为与生命周期 | 实际 JVM / Robolectric、GestureMachine、owner 释放与原子安装回归 | 先以已有断言执行真实缺陷注入。外部 UI 工具无法验证代理异常传播、部分安装回滚或旧 owner 清理。 |
| 自动设备交互 | 新接入 agent-device 原生 `.ad` 重放与 JUnit；继续复用 ADB 连接 / 锁屏函数 | 固定离线 fixture，语义定位、等待、结果断言、零自动重试；不自建 UI 驱动。当前适配固定在实测版本 0.21.36，升级前验证命令合同与负例。 |
| Maestro MCP | 官方 CLI 自带 `maestro mcp`，可交互探索 / 生成原生 YAML | 可选替代驱动；不同时运行两个设备所有者。agent-device 仅支持 Maestro 子集，不能把任意 YAML 当作已兼容。 |
| UI Automator | agent-device Android snapshot helper 已使用 UI Automator 输出 | 暂无需要新增测试模块；只有多窗口、连续按住、原生节点或跨进程断言确实不足时，再用独立 instrumentation。保持在宿主和生产 APK 外。 |
| 性能 | 系统 Perfetto、现有 perfetto-performance-analysis Skill、ADB 有界采集 | UI 快照 / helper 会干扰采样；先通过场景，关闭自动化，再用相同操作窗口取基线 / 候选。缺少可比采样不能报告性能通过。 |

外部依据：[agent-device 安装](https://agent-device.dev/docs/installation)、[重放与 JUnit](https://agent-device.dev/docs/replay-e2e)、[helper / IME 限制](https://agent-device.dev/docs/known-limitations)、[Maestro MCP](https://docs.maestro.dev/get-started/maestro-mcp)、[UI Automator](https://developer.android.com/training/testing/other-components/ui-automator)。它们提供输入和观察能力；A14 的设置、Hook 效果与几何断言由本项目负责。

## 设备入口

准备已安装的 Node 22.12+、ADB 与固定版本 agent-device。可以将 `agent-device@0.21.36` 安装到忽略目录或仓库外，不修改全局客户端或把 npm 依赖加入 APK。入口使用其 `bin/agent-device.mjs`，不隐式下载。fixture 仅使用现有 SDK / JDK 27 构建，临时签名私钥随构建目录释放，无网络权限、常驻服务或采样。

```powershell
python tools/build_device_fixture.py --sdk <现有SDK根目录> --output .di-evidence/fixture-build
python tools/device_verify.py --apk <待核对的模块APK> --output .di-evidence/preflight
python tools/device_verify.py --run --scenario deck-open-return --agent-device <已安装bin/agent-device.mjs> --fixture-apk .di-evidence/fixture-build/fixture.apk --output .di-evidence/deck-run
```

输出目录必须不存在。多设备时显式指定 `--serial`。默认只读 preflight，返回 2 / `PREFLIGHT_ONLY`；`--apk` 比较已安装模块与候选字节，不安装或重签模块。实际安装、LSPosed scope、宿主重载沿用既有授权和操作，完成后重新核对。运行前须解锁，脚本不保存或处理锁屏密码。

`--run` 确认 Android 14 / HyperOS 1 / arm64、安装独立 fixture、授予其通知权限，执行场景并检查新 JUnit 有实际用例、无失败 / 跳过及宿主 PID 不变。返回 0 只表示 `SCENARIO_PASSED`；真实断言失败返回 1 / `FAILED`；环境、报告、连接、清理异常返回 2 / `PENDING`。`installation`、`hook_effect`、`performance` 各自记录，不因 UI 场景通过升级。

成功与失败均停止本次独立自动化 daemon，卸载本次新装 fixture / 已知 helper 并锁屏；已存在 fixture 拒绝覆盖，已有 helper 保留。未知新增包只报告待处理，绝不批量卸载。设备断连可能使清理无法完成，须检查本次 result 的 `cleanup_errors`。所有日志、JUnit、界面快照和设备身份仅留忽略目录；公开提交或 Issue 只写抽象结果。

## 可复用场景与断言边界

| 场景 | 自动断言 | 仍需特有验收 |
| --- | --- | --- |
| `fixture-smoke` | 固定离线页面真实可见，报告有执行 | 仅验证工具接通。 |
| `deck-open-return` | 测试任务进入最近任务、点击后返回正确页面，宿主不更换 | Deck 开关及特有堆叠几何、后排遮挡、首合成帧；横竖屏 / HOLD / 反向 / 取消及旋转恢复不能由单卡路径证明。 |
| `dock-overview-return` | 桌面 hotseat 可见，Overview 返回后仍可见，宿主不更换 | 节点 ID 属 HyperOS Launcher 样本合同，缺失即失败；背景残影、4→5→4、拖动取消与空 Dock 重填继续用现有 Host 回归及目标设备视觉验收。 |
| `island-notice` | 固定通知内容出现且在有界时间消失 | 开启岛体通知并允许 fixture 后执行；原生 Heads-up 也可能出现同文案，故该断言本身不证明岛体接管。自绘节点不可见时保留失败 / 待验，不能改成无断言截图。另核对岛体窗口 / 位置、点击路由、同键更新和释放。 |

Hook 效果验收须有禁用 / 启用对照或可归属的安装结果加特有状态；不能以原生功能同样能通过的场景宣称生效。复杂几何复用既有 `DeckContinuityTest` / `DeckSurfaceOcclusionTest`、`IslandActivityLifecycleTest` / `IslandAbsorptionContinuityTest`、`DockNativeAnimationTest` / `DockBackgroundGeometryTest`，真机视觉保留独立边界。手势 `CANCEL` 与插件 owner 更换首先运行现有 Gesture / RuntimeHolder 回归；脚本列表尚不包含通用 `gesture-cancel`，路由返回该项表示需要定向设备场景，不能假装自动执行。

## 缺陷注入与性能闭环

Mutation 继续使用原执行器的干净 HEAD 基线、新 JUnit、真实失败=1、环境错误不计 kill、worktree 释放规则。新增接入 CallbackGuard 致命异常传播、已就绪偏好不再远程读取，复用插件换 owner 归还手势令牌回归；三者共享相同命令的干净基线。只需运行对应 `--case`，或按实际范围用 `--behavior-only`。required-only 的十项正式 CI 门禁保持不变。配置状态与覆盖摘要必须一致，独立行为门禁可多于 required 最低值。

性能采样以系统工具为入口，例如在已经准备好且通过断言的场景中运行 `adb -s <目标> shell perfetto -o /data/local/tmp/<本次唯一文件>.perfetto-trace -t 15s sched freq idle am wm gfx view`，在另一个受控窗口执行同样手势，完成后 pull 并仅删除本次 trace。有限采样自然退出；异常时先确认本次 PID 再停止，不能 killall。不要在采样期间运行 hierarchy、截图、meminfo 或自动化 helper。

使用现有 Perfetto Skill 的 `perfetto_probe.py` 确认信号，再选 rendering / jank 或 multi-trace comparison 查询；不另写 SQL 引擎。比较条件包含设备、ROM、模块与宿主产物、设置、方向、刷新率、热状态、任务数量、预热、窗口及重复采样。基线与候选交替采样，区分 UI 输入至呈现、主线程工作、调度等待与 Surface 合成。缺表、空帧样本、窗口不等或噪声超过差异时为不可判定；`adb_stress.py` 的 PSS / gfxinfo 快照仅是观测。独立验证可在同一来源上重新运行既有行为门禁 / mutation，或由只读审查者核对 exact diff 与原始报告；不能把作者摘要当作独立执行证据。
