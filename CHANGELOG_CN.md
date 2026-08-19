# Changelog

[English](CHANGELOG.md) | 简体中文

## r14.20.8 — 2026-08-19

面向 HyperOS 1 / Android 14（SDK 34）、`arm64-v8a`、libxposed API 101/102。

### 修复

- 修复部分系统级设置在首次配置后无法立即生效的问题。
- 修复部分全局动作首次配置后需要额外重启才能工作的情况。
- 修复热点、勿扰模式和深色模式动作的显示名称。
- 修复播放/暂停、上一首和下一首媒体动作的显示名称。
- 改进全局动作在系统服务与系统界面中的运行时可用性。

### 改进

- 改进动作配置的运行时同步与宿主生命周期处理。

### 产物信息

- APK：`CustoMIUIzer-A14-r14.20.8.apk`
- versionCode / versionName：`205 / r14.20.8`

---

## r14.20.7 — 2026-08-19

面向 HyperOS 1 / Android 14（SDK 34）、`arm64-v8a`、libxposed API 101/102。

### 修复

- 修复扩展动作页面进入「启动应用 / 启动快捷方式 / 启动活动」选择页面时可能闪退的问题。
- 修复 MultiAction「切换功能」动作失效的问题。
- 修复锁屏扩展动作「切换功能」显示项与内部值不一致的问题。

### 稳定性

- 完善启动器 10 项手势动作的映射与执行契约校验。
- 完善 MultiAction action / toggle / array contract，防止 UI 暴露无执行端动作或 label/value 再次错位。

### 产物信息

- APK：`CustoMIUIzer-A14-r14.20.7.apk`
- versionCode / versionName：`204 / r14.20.7`

---

## r14.20.6 — 2026-08-18

面向 HyperOS 1 / Android 14（SDK 34）、`arm64-v8a`、libxposed API 101/102。

### 修复

- 启动器手势以及导航栏、状态栏、锁屏、启动等 MultiAction 页面，选择动作后可以保存，重新进入仍保持设置。
- 启动器 → 手势页的「重启相关组件」会同时重启桌面和系统界面。

### 性能与稳定性

- 桌面手势热路径复用预分配命令列表。
- 全局动作部分反射方法改为缓存。
- Hook 体内 PrefMap 读取已清零并由扫描上限保护；偏好变化刷新快照，不再重装 Hook。

### 产物信息

- APK：`CustoMIUIzer-A14-r14.20.6.apk`
- versionCode / versionName：`203 / r14.20.6`

---

## r14.20.5 — 2026-08-18

面向 HyperOS 1 / Android 14（SDK 34）、`arm64-v8a`、libxposed API 101/102。

### 状态栏

- 温度支持 CPU / 电池分源，thermal zone 解析更兼容。
- 双排关闭「显示在右侧」时，温度改到左侧。
- 空间不足时字号自动缩小；内容可垂直微调，且不超出状态栏窗口。

### 桌面与最近任务

- 关闭文件夹模糊后，拖动图标不再闪默认模糊。
- 禁用壁纸缩放改为拦截实际 zoom 调用，最近任务与应用过渡均生效。
- 启动器、最近任务两处开关文案已区分；任开即可关过渡缩放，仅启动器开关还关解锁缩放与最近任务压暗。
- 最近任务模糊 0% 对手势进入生效，与窗口级模糊开关无关。

### 修复

- 动态岛上滑收回更稳定。
- 无网速控制器时，设备温度等信息仍可正常刷新。

### 产物信息

- APK：`CustoMIUIzer-A14-r14.20.5.apk`
- versionCode / versionName：`202 / r14.20.5`

---

## r14.20.0 — 2026-08-17

面向 HyperOS 1 / Android 14（SDK 34）、`arm64-v8a` 与 libxposed API 101/102。

### 新增功能

- 灵动额头新增动态岛模式：直接在系统原有顶部胶囊上变形，按挖孔 / 摄像头区域定位，并可用有符号垂直微调对齐。自定义状态栏高度不再作为硬裁切边界；岛出现时状态栏内容以系统透明度平滑淡出，连续事件不会闪回，并保留系统原生动画。
- USB 默认用途可选：跟随系统默认、仅限充电、传输文件（MTP）、传输照片（PTP）。
- 最近任务可隐藏卡片上的应用名称。
- 全面屏手势下可隐藏键盘关闭按钮。
- 可关闭桌面文件夹背景模糊，以及窗口级模糊（音量面板、电源菜单等）。
- 音量面板可隐藏勿扰 / 静音快捷按钮，并自定义模式按钮颜色。
- 可自动关闭系统位置权限、通知权限弹窗（不授权）。
- 独占功能：停用小米更新服务并可精确恢复、清除更新状态、停用 MIUI daemon、裁剪 daemon 网络组件、关闭小米统计、裁剪手机管家营销组件、移除杀毒入口。

### 设置与界面

- 首页按「模块 / 设置」重新分组；界面语言从关于页移到首页设置。
- 关于页改为独立页面，并补充作者与项目信息。
- 二级分类、搜索与长文本继续按功能语义整理；软重启项的说明更清晰。

### 备份与恢复

- 备份改为 V2 类型化格式，仍可恢复旧版备份。
- 恢复过程包含结构校验、应用选择清理、提交失败回滚，以及语言 / 桌面图标对账。
- 当前版本只备份和恢复仍然有效的设置；已删除功能与无法识别的旧项会被忽略，不会当成备份损坏。旧的「关闭灵动额头」开关会迁移到新的显示模式。

### 修复

- 音量百分比显示在当前实际状态栏下方，并随自定义状态栏高度使用实时几何。
- 状态栏数字信号字号滑块默认改回系统默认。

### 稳定性与兼容性

- 模块改为静态作用域，只暴露当前实际支持的 Hook 目标；启用后请确认 LSPosed 作用域包含 `system`、桌面等必要应用。
- 运行期按进程与功能开关安装，完善生命周期、失败边界与状态栏高度运行时同步。

### 性能优化

- 状态栏、电池、时钟、图标与通知等热路径减少重复解析与临时分配；关闭的功能不再创建无关 Hook。

### 产物信息

- APK：`CustoMIUIzer-A14-r14.20.0.apk`
- versionCode / versionName：`198 / r14.20.0`

---

## r14.18.6 — 2026-08-09

面向 HyperOS 1 / Android 14（SDK 34）、`arm64-v8a` 与 libxposed API 101/102。

### 设置与交互

- 设置页面不再一次创建整棵 Preference 树，改为按分类生成页面并按需加载；搜索索引由构建工具生成，既保留全局搜索能力，也减少首次进入和无关页面的初始化开销。
- 修复一级页面进入二级页面时仍能看到上一级点击动画残留的问题；同时重新整理“杂项”的分类、入口与功能归属，减少重复层级和语义含混。
- 修复应用快速关闭、重新启动时偶发显示“未激活”的竞态。LSPosed 服务尚在连接时，界面保持等待状态，不再过早给出错误结论。
- 输入法入口改用适用于不同地区 ROM 的通用命名；实际仅作用于 Gboard 的竖屏/横屏底部间距选项继续明确标注适用范围。

### 性能与生命周期

- 桌面全面屏手势、锁屏充电提示与手机管家 Dock 不再在高频回调中重复扫描调用栈，改用有界、按进程保存的调用来源状态，降低临时对象、字符串处理与重复计算。
- AudioVisualizer 与电池指示器 Observer 统一补齐所有者、替换、失效和释放流程，避免重复注册、旧实例继续回调或短生命周期 View/Controller 被长期持有。
- Feature 初始化耗时与安装数量仅在开发构建记录，用于定位冷启动开销；正式版不在热路径增加统计负担。
- 收窄过宽的 R8 保留规则，在保持 Xposed 入口、反射与资源契约的前提下减少无效保留内容和 APK 体积。

### 兼容性与功能修复

- 应用选择类设置在导入备份后会统一清理已卸载、已禁用或无法匹配的包名。无效应用不再保持勾选，也不再计入外层摘要，列表内外数量保持一致。
- 修复部分应用中自定义状态栏高度只移动图标、未改变实际状态栏与内容区域的问题；WindowInsets 和应用窗口几何会同步设定值。固定状态栏高度修改后需完整重启生效。
- 新增灵动额头显示控制，覆盖充电、静音、勿扰等系统场景。隐藏模式已通过实机验证；匹配状态栏高度模式在完整重启后生效，圆角尺寸同步留待后续适配。

### 验证状态

- 统一静态规则、不变量、Python 测试、Android JVM 测试、编译、lint 与正式 Release 产物检查均通过。
- fuxi / HyperOS 1 实机已验证灵动额头隐藏模式及重启后的高度匹配；当前已知限制仅为匹配高度模式的圆角尺寸尚未同步。

### 产物信息

- APK：`CustoMIUIzer-A14-r14.18.6.apk`
- versionCode / versionName：`196 / r14.18.6`

---

## r14.18.2 — 2026-08-08

面向 HyperOS 1 / Android 14（SDK 34）、`arm64-v8a` 与 libxposed API 101/102。

### 核心变化

- 提升通知弹窗和锁屏 Hook 在 HyperOS / Android 14 下的兼容性与失败保护。
- 修复锁屏提示更新后充电信息字号被系统样式重置的问题。
- 简化部分 SystemUI / system_server 生命周期 Hook，改用原生 after 回调并减少冗余状态处理。
- 强化 Hook 安装致命异常传播，避免将致命错误误判为普通安装失败。

### 验证状态

- 功能语义校验、源码风险扫描、不变量检查、Python 工具测试、Android JVM 单元测试、lint 与 R8 分析均通过。
- 正式签名 APK 已通过版本、证书、v2 签名、zipalign、SDK、ABI、debuggable 与 Xposed 入口检查。
- Release Candidate 已完成安装并通过实机冒烟验证。

### 产物信息

- APK：`CustoMIUIzer-A14-r14.18.2.apk`
- 大小：`3468849` bytes
- SHA-256：`77F868590C631271251991EDEBF066919460E2F1DA955EFDC10271207EAF3E77`
- versionCode / versionName：`195 / r14.18.2`

---

## r14.18.1 — 2026-08-07

面向 HyperOS 1 / Android 14（SDK 34）、`arm64-v8a` 与 libxposed API 101/102。

### 核心变化

- 构建工具链升级：JDK 25、Gradle 9.6.1、AGP 9.3.1；Gradle Daemon 固定使用 Java 25 作为 JVM criteria。
- Java source/target 保持 17；Android Java 编译输出仍为 17，Gradle 与 compiler toolchain 使用 25。
- 整理 `.idea` 元数据归属：将 Gradle IDE 模型、部署目标、仓库镜像、迁移状态、inspection profile 等本地/生成文件移出 Git tracking，保持共享 code styles、compiler target hint、编码和 VCS 映射。
- 修复 `SystemLockScreenHooks` 中壁纸 `handleIncomingUser` 解析失败时错误回退到 user 0 的 fail-open 行为；失败后直接返回原方法结果，不再继续 CustoMIUIzer 壁纸后处理。

### 验证边界

- 本次 Release-only 验收构建未执行 `python tools/verify.py full`，因为该模式会触发 `assembleDebug`。
- 已执行并通过功能语义校验、源码风险扫描、不变量检查、`git diff --check` 及 JVM 单元测试。
- Release APK 完成 R8 压缩、资源收缩和正式签名。
- 本版本不标记为全面 `DEVICE_VERIFIED`；状态栏高度无重启序列等待实机验证。

### 已知 Major

- `SystemNotificationHooks` 通知菜单中 `UserHandle.getUserId` 解析失败时回退 user 0，可能错 user 打开应用详情或 force stop。
- `Various.kt` AppInfo 启动应用时 `UserHandle.getUserId` 解析失败时回退 user 0，可能错 user 启动应用。

---

## r14.18.0 — 2026-08-06

面向 HyperOS 1 / Android 14（SDK 34）、`arm64-v8a` 与 libxposed API 101/102。

### 核心变化

- 锁屏充电信息新增字号调节；默认保持系统字号，重启 SystemUI 后生效。
- 加固锁屏充电信息初始化与热路径；关闭详情时跳过无效调用，减少重复安装、无效读取和异常回退开销。
- 修复启用状态栏电池或温度信息时可能导致的 SystemUI 崩溃，并加固旧 Handler、过期 View、ROM 字段兼容和自定义图标创建路径。
- 修复左侧状态栏自定义文字图标在深色背景下不可见，补齐 tint 注册、初始同步、重建和释放生命周期。
- 新增状态栏高度与 WindowInsets、SystemUI 窗口同步，支持运行时应用及禁用后恢复系统高度；fuxi 无重启切换仍待实机验证。
- 加固状态栏和控制中心手势、View、回调及 ClassLoader 生命周期，减少重复触发、状态冲突和过期对象残留。
- 优化进程路由、Feature 安装去重和关闭功能的初始化路径；普通异常保持隔离，致命错误继续传播。
- 构建产物增加 Git revision 与 provenance 记录，功能语义清单、Python 门禁、单元测试和 lint 纳入统一验证。

### 验证状态

- `python tools/verify.py full`、功能语义校验、源码风险扫描、CI 可移植性检查及 Python 全量测试均通过。
- Python 工具测试共 405 项通过，Android JVM 单元测试与 `lintDebug` 通过。
- 状态栏高度的 `44 → 40 → 12 → 44 → disabled` 无重启实机验证尚未执行，本版本不标记为全面 `DEVICE_VERIFIED`。

## r14.16.1 — 2026-08-01

`versionCode 192`，面向 HyperOS 1 / Android 14（SDK 34）、`arm64-v8a` 与 libxposed API 101/102。

### 核心变化

- 将 SystemUI、Launcher、`system_server` 及普通应用入口拆分为按进程路由的 installer，并用稳定 Feature ID、明确安装状态和惰性 Feature 定义保证同一进程只安装一次；关闭功能跳过无关 Feature 注册和业务对象创建。
- 修复 early preference 快照的并发与空快照语义；安装失败不会残留活动定义，偏好变化不会把已安装 Hook 重置为未安装。
- ReflectionCache 改为按 ClassLoader 隔离的有界状态，ResourceHooks 修复真实 Hook 结果与并发安装语义，并削减命中路径的装箱、数组和名称解析。
- Receiver / Observer 注册统一所有者、替换、active/stale 和释放闭环；释放过期天气、计步、专辑封面、电量指示器和百分比覆盖层，避免长期持有 Context、View 或中间 Bitmap。
- 所有共享 Hook、Java/Kotlin 和日志边界均保持普通异常隔离，同时明确继续抛出 `OutOfMemoryError`，避免把内存耗尽伪装为普通兼容失败。
- 优化网速采样与格式化、充电提示、导航图标重载、电量指示更新及透传 Hook 参数路径，减少 SystemUI 高频回调中的临时对象和重复工作。
- 设置开关在点击后立即显示目标状态，再执行原有持久化、禁用态和重启要求逻辑，改善连续点击时的可见反馈，不改变最终偏好语义。
- 模块加载日志加入版本和短 Git SHA；API 102 稳定 Hook ID 能力仍为隔离的 `READY_NOT_WIRED`，未接入生产路径。

### 验证边界

- 发布提交通过 `python tools/verify.py full`：运行期不变量、Debug Kotlin/Java 编译、单元测试和 `lintDebug`。
- 正式 Release APK 使用 A14 专用证书，产物的版本、SHA-256、签名、zipalign、`debuggable=false` 与 Xposed 元数据在 GitHub Release 中记录。
- 既有 Xiaomi 13 / HyperOS 1 基线未发现模块导致的 P0/P1、重复安装或持续安装异常；本版本新增的运行期与界面变化尚未完成全部功能逐项实机行为验证，不标记为全面 `DEVICE_VERIFIED`。

---
