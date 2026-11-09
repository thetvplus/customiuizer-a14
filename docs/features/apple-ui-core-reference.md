# Apple 界面核心链路与 A14 接入

2026-11-08，覆盖指定机型的五条原生核心链。原始函数、构建清单和小型资源私有留存；本页只保存双端对照及接入合同。2026-11-09 已接入中央 HUD 与五圆点；全部 11 链的当前差异见[原路线图](../maintenance/A14_REFINEMENT_ROADMAP.md#11-条研究链与消费者差异2026-11-09)。

| 功能 | 原件身份 | 核心链与 A14 消费者 |
| --- | --- | --- |
| 灵动岛 | iPhone 16 Pro / iPhone17,1 / 18.6.2 / 22G100 | 注册、偏好顺序、mitosis、几何、退出 → `DynamicIslandActivities` / `IslandMotionCoordinator` |
| Duo 三合一 | iPhone Duo / iPhone19,4 / 27.2 / 24B91020 | 聚合更新、model、hosting、Compact/Expanded body、环与弧点布局 → `DuoStatusBarHooks` / `DuoStatusBarView` |
| 全覆盖 Dock | iPhone SE 3 / iPhone14,6 / 18.6.2 / 22G100 | 设备/显示上下文、screenType、配置、metrics、材质/layout → `LauncherDockBackground` / `DockFullWidthContainer` |
| 中央音量指示 | iPhone 7 Plus / iPhone9,2 / 12.4.1 / 16G102 | 有效音量/分类准入、HUD view、居中 window、分段进度、退出 → MIUI 音量插件；`IosVolumeStyle` 可选中央样式 / `ClassicVolumePlacement`，旧侧边默认值保留 |
| 五圆点信号 | iPhone 7 Plus / iPhone9,2 / 10.3.3 / 14G60 | raw/bars/RSSI、模板选择、图形和宽度 → SystemUI mobile slot；`ClassicSignalHooks` 可选五圆点，默认关闭 |

Duo 的“最新正式版”固定为本日核对的 27.2，依据 [Apple 正式发布表](https://support.apple.com/en-us/100100) 与该机型固件清单，最终构建以原始 BuildManifest 为准。7 Plus 取对应大版本最后适用的 10.3.3 / 12.4.1；不使用其他机型的 10.3.4 / 12.5.x。

选定核心共 266 个完整函数、98,164 字节原始代码。边界、逐指令地址和字节、直接分支目标、原始资源区间、来源摘要及读取入口已校验。静态链闭合止于功能消费者，外部 SwiftUI/UIKit 渲染及设备验收另行区分。

## 既有功能的接入边界

- 灵动岛的基础配置、覆盖值、milestone 与时延分别保留，沿用来源仲裁、窗口和有限动画时钟；参数及推断边界见[运动参考](ios18-motion-reference.md)。
- Duo 的同版 Swift witness 已追到实际 body / layout。原始逻辑资源、字形与 Android 绘制映射见 [Duo](duo-status-bar.md#原生核心来源)。耳机短提示、默认数据卡量化和字体替代仍属 A14 策略。
- SE3 的设备枚举、尺寸表和配置分支已恢复；全宽背景与图标/输入所有权见 [Dock](dock/README.md#se3-原生参考)。原生点数与 Android dp 分别适配。

## iOS12 中央音量指示

`VolumeControl` 接受有效变化并检查分类准入，构造 `SBVolumeHUDView`，由 `SBHUDController` 的专有 window/content 居中、旋转和退出。`SBHUDView` 初始 155×155 点、圆角 9、16 段进度、每段 1/16。`_presentVolumeHUDWithMode:volume:` 内部 mode==3 选 3 秒，其余 1.5 秒；未确认枚举生产源，不命名为某个音量类别，也不推广为所有 HUD 调用的退出时间。

6 份选定 CSI 和 6 份 PNG 完整。mute/speaker 及 RTL 四份使用 CoreUI wrapper v5 的 GA8 deepmap，已按原负载解码；文件名中的 `.pdf` 不代表通用 PDF。原始字节保留私有；APK 使用按原图 alpha 轮廓校准的有限矢量，保留扬声器 77×63、铃声 69×80 点比例和 RTL 变体，仅替换已识别的原生资源身份。

中央模式在既有音量插件 ClassLoader、dialog owner 内互斥选择展示。继续消费原生 stream、路由、按键 flags、安全音量、监听、无障碍和 dismiss；用当次窗口 metrics 居中并更新触控/可见区域，处理横屏、锁屏、配置变化和销毁。不能只把侧条平移到中间，也不新增第二套音量控制器或退出定时器。155 点、材质和动画须作 Android 映射，原生 UIKit 合成不跨平台执行。

## iOS10 五圆点信号

`UIStatusBarSignalStrengthItemView` 分别持有 raw signal、bars 和 RSSI；普通图形经 `%d_Bars` 请求 `imageWithShadowNamed:`，RSSI 是独立分支。Black / LockScreen 两套 0–5 级共 12 份圆点模板已提取，另保留 8 份同 catalog 的 Wi-Fi 旁证。普通图片框为 34×20 点，锁屏约 42⅓×24 点；图片框、墨迹、@3x 资源像素和 7 Plus 最终物理像素不得混用。

五圆点模式复用 SystemUI 自身 mobile slot、订阅回调及根 View，与 Duo、iOS18 普通图标保持单一绘制所有者。明确 Android 0–4 / 0–5 输入到五点的量化，保留无服务、飞行模式、有效 0 格、双卡、dot/hidden、暗色区域和无障碍；关闭后恢复原生绑定。Duo 是四点弧布局，不共用这一量化。几何在绑定/尺寸变化时准备，稳定状态不解析路径、不轮询。

## 验证与剩余边界

原始字节与可读 ASM 是依据。新 PAuth_LR 指令已完整解码；旧 Ghidra 抽样的截断 C 继续标记为不可用，不通过改写指令制造成功。小型资源保留原 CSI 与派生输出的独立摘要，BVG 二进制矢量不冒充 XML SVG。

本次完成核心提取、资源和当前 A14 源码对照。新模式的全部 ROM/场景、完整 Apple 弹簧/合成器、最终字体 trait、实时显示缩放、像素一致性与设备性能未由静态证据验收；Dock 既有偶发闪现维持原未决状态。当前决定与后续入口只维护在[原路线图](../maintenance/A14_REFINEMENT_ROADMAP.md#apple-界面核心接入2026-11-08)。
