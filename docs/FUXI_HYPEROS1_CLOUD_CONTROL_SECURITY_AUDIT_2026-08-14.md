# fuxi HyperOS 1 云控与手机管家实机审计（2026-08-14）

## 结论

`com.miui.daemon` 不是单一的“云控服务”。本机 HyperOS 1.0.7.0.UMCTWXM 中，它同时包含：

- 云端性能配置同步：`CloudControlSyncService`、`CloudServerReceiver`；
- MQS 质量遥测和文件/事件/心跳上传；
- 本地性能和内存服务：`MiuiPerfService`、`MemCompactService`、`GcBoosterService`、`SysoptService`；
- 故障救援、碎片整理、图形和内存诊断服务。

因此，停用整个 Daemon 会同时失去联网遥测和部分本地优化能力。默认应优先使用组件级精简；整包强制停用只保留为明确知情的激进选项。

## 证据边界

本次证据来自已连接的 Xiaomi fuxi：

- ROM：HyperOS `OS1.0.7.0.UMCTWXM`，Android 14；
- APK 由 `adb shell pm path` 定位后，通过 `adb pull` 提取；
- 组件、权限和进程来自 APK manifest、DEX 包清单、`dumpsys package/activity` 与 `ps`；
- 这份清单只证明该 ROM 构建，不假设其他地区、版本或设备有相同类名；
- 设置页只操作当前 ROM 实际声明且命中代码内精确白名单的组件，未命中则显示不支持。

提取物放在系统临时目录 `%TEMP%\xiaomi-audit-apks`，没有进入源码、APK 或 Git。

## 组件分级

| 包 | 本机证据 | 风险判断 | 当前处理 |
| --- | --- | --- | --- |
| `com.miui.daemon` | 云控、MQS 上传、本地性能/内存/救援混装 | 不宜默认整包停用 | 只允许关闭 2 个云控入口和 3 个上传 Job；保留本地优化 |
| `com.miui.analytics` | OneTrack、Analytics、Appender、Event、Wakeup、Marketing 服务；本机 User 0 已卸载 | 独立遥测包 | 仅在当前用户实际安装时允许整包可逆停用 |
| `com.miui.msa.global` | 系统广告、开屏、锁屏广告、通知广告和广告远程配置；本机 User 0 已卸载 | 独立广告包 | 仅在当前用户实际安装时允许整包可逆停用 |
| `com.miui.securitycenter` | 权限、病毒扫描、防火墙、省电、反骚扰与第三方广告 SDK 混装 | 安全主链，不可整包停用 | 分别提供营销自启动精简和病毒扫描精确移除；不关闭整包或其他安全主链 |
| `com.miui.powerkeeper` | 电量、唤醒锁、刷新率、温控和 CloudUpdate/MiPush 混装 | 云端策略可能承载设备修正 | 不提供停用开关 |
| `com.xiaomi.joyose` | 游戏场景、预下载和云端 profile | 可能影响调度与游戏性能 | 不提供停用开关 |
| `com.miui.cloudservice` | 小米云同步、查找设备、密钥包与云配置 | 用户数据与防丢主链 | 不提供停用开关 |
| `com.xiaomi.xmsf` | 小米推送、通知和云端绑定 | 关闭会破坏应用推送 | 不提供停用开关 |
| `com.miui.cleaner` | 清理功能与多套广告/统计 SDK 混装 | 本机为独立清理器，组件多且版本差异大 | 本轮只记录，不纳入系统白名单 |
| `com.xiaomi.ugd` | `hasCode=false`，仅包含 Game Driver 元数据 | 不是用户数据采集 Daemon | 不处理 |

## 手机管家常驻进程

审计时 `com.miui.securitycenter.remote` 承载以下已运行服务：

- `PowerSaveService`；
- `AntiSpamService`；
- `RemoteService`；
- `FirewallService`；
- `TrafficManageService`。

这些服务把省电、反骚扰、防火墙与流量统计集中在同一远程进程。为了省内存而杀掉整个进程会破坏用户可见且安全相关的功能，本轮不采用。相比之下，第三方广告/统计 Provider 会在手机管家主进程初始化，适合做精确、可逆的冷启动精简。

## 新增设置的行为

入口：`杂项 > 独家功能`。

1. **精简 Daemon 云控与上传**：只处理实机核实并在 manifest 声明的五个组件；保存每个原始状态，关闭开关时逐项恢复。
2. **停用小米分析与系统广告**：只处理 `com.miui.analytics` 与 `com.miui.msa.global` 两个系统包；保存并恢复原始应用状态。
3. **精简手机管家营销自启动**：只处理七个第三方广告/统计启动组件；不触碰权限、病毒、防火墙、省电、网络和安装安全。
4. **移除手机管家病毒扫描**：只处理九个病毒扫描界面、三个扫描/更新服务、一个更新接收器和一个定时扫描 Job；保留手机管家整包、权限、防火墙、反骚扰、省电、网络和安装安全。关闭开关时逐项恢复启用前状态，因此 Thanox 已停用的组件不会被误开。

系统服务桥对包名、系统应用标志、manifest 声明、精确组件白名单、数量和状态值逐层校验。批量操作异常时回滚本次已修改项；不接受来自设置页的任意包或组件名。病毒扫描入口与定时任务的存在已由 fuxi 包清单核实，最终开关链路在 ADB 结束后完成，因此目前属于代码验证，不把组件清单证据扩大成实机行为结论。

## 后续实机观察建议

- 重启后比较 `ps -A -o NAME,RSS` 中手机管家主/远程进程、MSA、Analytics 的驻留情况；
- 用一天日常使用确认通知推送、云同步、查找设备、权限弹窗和安装安全不受影响；
- Daemon 精简后观察充电、游戏、高温、低内存和系统更新场景，确认本地性能服务仍存在；
- 其他 ROM 必须重新提取 APK 并核对清单，不能仅凭名称模糊匹配或静默扩大白名单。
