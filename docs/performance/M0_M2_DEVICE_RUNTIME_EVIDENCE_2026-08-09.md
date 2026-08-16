# A14 M0 / M2 实机运行证据（2026-08-09）

## 结论

本轮在同一台 `fuxi`、同一份 preference 和同一签名下，对安装前版本
`8e2dcbd3` 与优化版本 `8fa962ee` 做了覆盖安装、重启和 A/B 采样。

- 模块冷启动中位数为旧版 `161 ms`、优化版 `158 ms`；两版卡顿帧比例都约为
  `4%`，没有设置 UI 回归，也没有足以支持 M3 重构的收益证据。
- 可比的稳定空闲窗口中，SystemUI 与 Launcher 两版都接近 `0%` CPU；
  `system_server` 的 `2.0%` 与 `2.3%` 属于同级噪声。
- PSS 在同一优化 APK 的两次重启间也有很大波动；不得把单次正负变化写成内存
  收益或回归。
- M2.1 Launcher 与 M2.2 锁屏充电信息已执行真实功能路径并通过；M2.3 的
  SecurityCenter Hook 安装和全局侧边栏通过，但设备固定应用已满，ROM 禁用了
  `DockAppEditActivity` 的“+”入口，因此该内部 worker 路径保持
  `DEVICE_RUNTIME_PARTIAL`。
- 最终设备已恢复优化 APK，设备端 APK SHA-256 与本地构建一致；测试期间临时修改
  的系统设置已恢复。

这份结果是运行证据，不把构建、静态测试或单次观测扩大解释成长期功耗和内存收益。

## 测试对象

| 项目 | 值 |
| --- | --- |
| 设备 | Xiaomi `fuxi_global` / `2211133G` |
| ROM | HyperOS `V816.0.7.0.UMCTWXM` |
| Android | Android 14 / SDK 34 |
| 模块包名 | `tv.withaibuild.customiuizer.r14` |
| 版本 | `r14.18.2` / versionCode `195` |
| 安装前修订 | `8e2dcbd349bcf4d6bb9c22e05807c5b016e2c378` |
| 优化修订 | `8fa962eecc9d20b4ede5bb14febb2a68f4cfa18e` |
| 安装前 APK SHA-256 | `77F868590C631271251991EDEBF066919460E2F1DA955EFDC10271207EAF3E77` |
| 优化 APK SHA-256 | `835FEA72B0ED4F8E1334071C50792E1BFFD9BEAA2A0F25A4AEBFDBD7B40BD348` |
| 签名证书 SHA-256 | `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70` |

`8e2dcbd3..8fa962ee` 的生产改动只有 M1.2 与 M2.1-M2.3 四个优化提交；文档提交
不改变运行行为。

## 方法

1. 使用相同证书执行 `adb install -r -d`，不清除应用数据和 preference。
2. 每版覆盖安装后重启，等待约 5 分钟，并在负载回落后采样。
3. 对 SystemUI、Launcher、`system_server` 连续采集 5 次 `dumpsys meminfo`，报告
   PSS 中位数；线程数来自 `ps -T`。
4. 使用 `top -b -n 11 -d 1`，丢弃首个累计样本后统计 10 个 1 秒窗口。
5. 手机解锁后，每次 `am force-stop` 模块进程，再通过 `am start -W` 连续执行 5 次
   COLD 启动；每轮同时读取 `dumpsys gfxinfo`。
6. 对 M2 三处分别执行最近任务/横向手势、锁屏充电显示和全局侧边栏操作，并核对
   PID、LSPosed HookSummary 与崩溃日志。

设备为锁定、`ro.debuggable=0` 的生产环境，shell 无 root；SELinux 不允许读取目标
进程 `/proc/<pid>/fd`，所以本轮没有伪造 FD 数据。

## 设置 UI 冷启动

| 指标 | 安装前 `8e2dcbd3` | 优化版 `8fa962ee` |
| --- | ---: | ---: |
| 5 次 TotalTime | `222, 164, 159, 161, 156 ms` | `161, 158, 165, 157, 152 ms` |
| 中位数 | `161 ms` | `158 ms` |
| 合计 janky / total frames | `10 / 252` (`3.97%`) | `10 / 249` (`4.02%`) |

优化版中位数只快 `3 ms`（约 `1.9%`），低于设备级噪声。两版都稳定进入
`MainActivity`，没有启动异常；本轮不据此启动 M3 设置树重构。

优化版模块主页面的一次截图显示“暂未连接到 LSPosed 服务”提示，但同一时段目标
进程的 LSPosed 日志持续证明模块已注入。这项提示不影响本轮 Hook 运行证据，也不在
M1/M2 变更范围内；需要时应作为独立问题诊断。

## 空闲 CPU

以下窗口都在开机约 5 分钟、系统负载回落后取得：

| 进程 | 优化版首次稳定窗口 avg / peak | 安装前受控窗口 avg / peak |
| --- | ---: | ---: |
| SystemUI | `0.0% / 0%` | `0.0% / 0%` |
| Launcher | `0.0% / 0%` | `0.0% / 0%` |
| `system_server` | `2.0% / 5%` | `2.3% / 8%` |

最终恢复优化版后，USB 接口曾断开重连；后续 CPU 复测在 10 秒窗口内从 Doze 自动
切换为 Awake，三个进程同步出现尖峰，因此该批数据被排除。稳定窗口只支持“没有可见
空闲 CPU 回归”，不足以量化三个调用栈数组分配消除后的微小收益。

## PSS 可重复性

单位为 KB，均为 5 次采样中位数：

| 进程 | 优化版首次稳定窗口 | 安装前受控窗口 | 优化版最终确认窗口 |
| --- | ---: | ---: | ---: |
| SystemUI | `185405` | `197065` | `208777` |
| Launcher | `251824` | `199760` | `192068` |
| `system_server` | `361392` | `367395` | `474472` |

三个窗口的负载/温度分别约为 `0.52 / 30.5°C`、`0.49 / 32.2°C` 和
`0.77 / 32.2°C`。同一优化 APK 两次重启间的变化已经大于部分旧/新差值，而且本轮
没有修改 `system_server` 生产路径，它却出现最大差异。这证明单次 PSS 受 ROM 缓存、
服务启动和页面状态影响，不能据此宣称内存改善或退化。

## M2 功能证据

### M2.1 Launcher `force_fsg_nav_bar`

- 优化修订以 `[8fa962ee]` 注入 `com.miui.home`。
- HookSummary：`onPackageReady installed=4`、`post-attach installed=12`，所有失败、
  缺类、缺成员、DexKit 和 preference 计数均为 `0`。
- 重启 Launcher 后连续执行 5 轮最近任务、横向滑动和 Home；PID 从测试开始到结束
  均为 `21864`，没有 FATAL、ANR 或进程重启。
- ROM 日志确认 `updateFsgWindowVisibilityState()` 在测试中实际执行。

结论：`DEVICE_RUNTIME_PASS`。

### M2.2 锁屏充电信息

- 设备处于 AC 充电、100% 电量时执行熄屏/唤醒。
- 锁屏真实显示模块附加的 `0.19 A · 4.5 V · 0.9 W · 30 °C`，证明
  `ChargeUtils.getChargingHintText()` 的 Keyguard 目标路径已执行。
- SystemUI PID 在操作前后保持 `5827`，没有 FATAL、ANR 或进程重启。

结论：`DEVICE_RUNTIME_PASS`。

### M2.3 SecurityCenter Dock 建议

- 优化修订以 `[8fa962ee]` 注入 `com.miui.securitycenter`。
- HookSummary：`onPackageReady installed=29`，所有失败、缺类、缺成员、DexKit 和
  preference 计数均为 `0`。
- `com.miui.dock.ACTION_DOCK_SETTINGS` 可启动，全局侧边栏可展开并连续滚动；主进程
  PID 保持 `23667`，没有 FATAL 或 ANR。
- `DockAppEditActivity` 为 `exported=false`。设备固定应用已达到 ROM 上限，侧边栏底部
  “+”呈禁用态；为保护用户列表，本轮没有删除固定应用来强行打开内部 editor worker。
- 测试中临时开启的“全部场景中开启”已恢复为原来的关闭状态。

结论：Hook 安装与外层功能为 `DEVICE_RUNTIME_PASS`，内部白名单 worker 为
`DEVICE_RUNTIME_PARTIAL`。

## 构建与最终设备状态

- 使用 `officialRelease=true` 构建同证书 Release APK；APK 为非 debuggable、
  `minSdk=34`、`targetSdk=34`、arm64-v8a，zipalign 验证通过。
- APK Signature Scheme v2 验证通过；设备覆盖安装前后证书一致。
- `META-INF/xposed`、scope、`java_init.list=xb0` 与 DEX 中 `xb0` 入口均已核对。
- `python tools/verify.py fast --changed` 通过；显式使用仓库 JDK 25 后，
  `python tools/verify.py full` 的静态规则、Debug 编译、全部单元测试和 lint 均通过。
- 最终设备端 APK SHA-256 为
  `835FEA72B0ED4F8E1334071C50792E1BFFD9BEAA2A0F25A4AEBFDBD7B40BD348`。
- 最终 LSPosed 日志确认 `[8fa962ee]`；Settings `installed=8`、SecurityCenter
  `installed=29`，均为 `0` 失败。
- 最终 `sys.boot_completed=1`，`stay_on_while_plugged_in=0`；设备端测试临时 XML 已
  删除，未改变模块 preference 和侧边栏固定应用列表。

## 决策

1. M1.2 与 M2.1-M2.3 保留：静态合同通过，实机未发现行为回归。
2. 不宣称 PSS、长期功耗或空闲 CPU 的可量化收益。
3. M3 设置 UI 按需创建暂不实施：`158 ms` 冷启动与约 `4%` janky frames 没有证明
   当前 Preference 树是设备瓶颈。
4. M4 Feature 表、状态容器、R8 和巨型 Hook 单例继续保持证据门槛，不做理论优化。
5. M2.3 若以后需要完整闭环，应在不破坏用户固定列表的设备状态下进入
   `DockAppEditActivity`，或在用户主动释放一个固定槽位后补测；不得用删除用户数据
   换取测试通过。

相关执行计划见 [A14_OPTIMIZATION_PLAN.md](A14_OPTIMIZATION_PLAN.md)。
