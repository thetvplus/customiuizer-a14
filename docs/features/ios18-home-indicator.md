# iOS Home Indicator 与 A14 原生导航接入

固定参考为 iPhone17,1 / iOS 18.6.2 / 22G100。权威研究入口为私有 `home-indicator-22G100/README.md`，来源为该目录的纯代码 ZIP、`parameters.json`、`scenarios.json` 和 `verification.json`；生产只转写使用到的几何子集，不打包原始资源或未消费的策略代码。

## 证据与映射

| 原生证据 → 消费者 | Android 实现 | 差异与等级 |
| --- | --- | --- |
| MaterialKit `0x215347214` 默认高度 5 / 边距 8；`0x215342bf0` 宽度插值 | `HomeIndicatorGeometry` 转写宽度、居中及像素对齐算术 | 原生静态确认；Android 使用短边 402 的设计坐标，再乘实际短边像素 / 402。这是明确的比例映射，不是实测 UIScreen、pt=dp 或原机几何等价 |
| SpringBoard `0x1d8621bcc` / `0x1d86239ec` 基础 frame 与 delegate 额外间距 | 原生 handle 内构建一份 G4 共享几何的圆端 Path | 数学重建 + Android 近似；原生 View 小于目标宽度时限制到其宽度，不扩张触摸或窗口。无 iOS delegate 输入，不猜测键盘额外间距 |
| `SBHomeGrabberView → SBFHomeAffordanceView → MTLumaDodgePillView → MTPillView` | `NavigationHandle.onDraw(Canvas)V` 单次替换填充，使用原生 `mPaint` | Android 近似。专用 cornerContents、亮度滤镜和私有合成未迁移；沿用系统 LightBar 颜色，禁止后台截图取色 |
| 触摸唤回、隐藏原因、所有权与旋转双信号 | 完全沿用 Android 原生导航 View 的 visibility / alpha / 旋转与输入 | 原生静态合同已读；iOS 状态机没有安全的同构 Android 输入，未另建计时器或手势求解器。不能称原生动态确认 |

研究报告中的 786 个去重函数、编译及有限数值检查已核对；其 `iosRuntimeExecuted=false`、`androidCanvasExecuted=false`，不等于全场景画面已经验证。现阶段没有绑定 22G100 的原机动态证据。

## 接入与所有权

设置在“按键与手势 → 导航栏 → iOS 手势指示条”独立分组，组内为“启用”和说明；键为 `controls_ios_home_indicator`，默认关闭，重启 SystemUI 生效。需要系统全面屏手势、可见的原生 Handle，并关闭系统“隐藏全面屏指示条”和米客“隐藏手势提示线”；菜单名称可能随地区 ROM 不同。进入或返回页面时读取系统导航及隐藏开关，不轮询、不改写系统配置，不满足时保留勾选值但禁用入口。生成器保留 `controls_nonavbar` 的 `disableDependentsState=true`，AndroidX 处理依赖，Compose 消费同一个 Preference 的启用状态。仅 SDK 34 / arm64、SystemUI 主进程及完整实例签名通过后安装；设备、地区与构建号不作为通行证。设置进程只判断平台和系统导航选项，实际能力由宿主安装阶段解析；不满足合同只关闭这项绘制替换。

冷解析两个 Hook：`NavigationHandle.onDraw(Landroid/graphics/Canvas;)V` 与 `NavigationBarView.updateIcons(Landroid/content/res/Configuration;)V`。原生 `NavigationBar.setNavBarMode(I)V` 写入字段后调用后者并可能重建 handle，后者仅缓存 `mNavBarMode:I == 2`，前者只在主屏、精确 NavigationHandle 类及真实 NavigationBarView 祖先下工作。复用 `mPaint:Landroid/graphics/Paint;`，同时要求实例字段为 final；已查样本均只在构造器写入，宿主重建 / detach 后重新取得当前实例。可替换 Paint 的未知合同保留原生，不逐帧读取字段。不写原生 Paint、布局、alpha、visibility、Insets、焦点或输入。教程、独立控制中心 / 锁屏 handle、未知子类和旋转尺寸尚未对齐时保留原生。

每个 handle 惰性持有一份 Path 和尺寸缓存，尺寸变化才通过 WindowManager.maximumWindowMetrics 获取当前屏幕几何并重建（SystemUI Resources 在横屏可保留竖屏值）；颜色由原生 Paint 更新，静止时无帧调度。detach 解除自身 attach listener 和附加 owner；没有全局 View 表、悬浮窗口、Bitmap、RenderEffect、跨进程通信或独立动画时钟。完整 Hook 批次未提交或几何无法容纳时不跳过原生绘制。关闭并重启后没有自持原生属性需要归还。

## 场景策略

显隐动效复核：22G100 的 `HomeIndicatorMotion.chooseTransition` 选择已证 hide 0.6s / unhide 0.25s 线性默认配置，ownership 切换各为 0.25s，但这些是原生 style / presence 消费者，不能覆盖任意系统窗口隐藏。A14 `ButtonDispatcher.setAlpha(FZ)V` 直接更新原生 View；`NavigationBarTransitions.applyLightsOut(ZZ)V` 的 1500ms / 250ms 属于整组按钮到 0.6+darkIntensity/10 / 1 的变暗与恢复，并非同构的消失 / 出现。全屏、IME 与 Deck 窗口交接继续消费系统已有透明度和窗口动画，不将变暗强套成 iOS hide、不延迟原生隐藏、不新增代理图层或第二个动画器。Deck 的原生四轴弹簧映射另见其运动合同。

以下是逐项审查后的策略，不是逐项实机通过记录。

| 场景 | 显隐、交接与回退 |
| --- | --- |
| 桌面、文件夹、编辑、最近任务、应用打开 / 返回及 Deck 入退场 | 原导航栏 owner 决定显隐和透明度；模块不读取任务列表或修改 Surface，不因导航条变化重算 Deck |
| 普通 / edge-to-edge / 透明栏应用、系统设置、权限及系统对话框 | 在真实导航 handle 内替换填充；应用导航背景与原生对比度策略保持有效，不能保证所有背景都成为透明 |
| 全屏视频、游戏、沉浸界面 | 原生隐藏即不绘制；系统触摸唤回仍由原生负责，不强制常显 |
| 输入法、PiP、分屏 | 原生导航布局、返回键、Insets 与窗口策略优先；不足容纳目标高度或不同宿主时原生回退，不用键盘高度猜原生偏移 |
| 锁屏、解锁、通知中心、控制中心、电源菜单、AOD、受保护或未知界面 | 仅已经准入的普通导航 handle 可接管；独立宿主保留原生，不跨宿主强制显示 |
| 竖屏、横屏、180°、旋转中断、密度变化 | 宿主尺寸与当前显示宽度一致才消费新几何；复用原生旋转和重建，不新增旋转时钟 |
| 深浅色、亮度、壁纸、省电、系统动画关闭 | 原生 Paint / alpha / 策略继续生效；无持续取色、呼吸或浮动动画 |
| 导航模式切换、关闭、SystemUI 重启 | 模式非 2 时原生绘制；重建后惰性接管。关闭需重启 SystemUI；Home / Back / Recents 输入从未被接管 |

## ROM 静态合同

[查询集](../../tools/dexsql/home_indicator_contract.sql)已针对 fuxi CN `V816.0.19.0.UMCCNXM`、TW `V816.0.8.0.UMCTWXM`、munch ID `V816.0.8.0.ULMIDXM`、duchamp CN `V816.0.18.0.UNLCNXM` 及设备 TW `V816.0.7.0.UMCTWXM` 执行。完整定义均在各自 SystemUI `classes.dex`；原件、哈希、SQL 输出和 Smali 私有留存。NavigationHandle 直接继承 View，绘制只调用 View.onDraw 再以 mPaint 画圆角矩形；原生 `setDarkIntensity(F)V`、`setDefaultColor(I)V`、`setAlpha(F)V` 保留。duchamp 构造资源不同，不以整个类同哈希推断一致。

追加核对 xaga IN `V816.0.9.0.ULOINXM`：精确绘制、Paint、模式字段、`NavigationBar.setNavBarMode(I)V → updateIcons(Configuration)V` 及 `QuickStepContract.isGesturalMode(I)Z` 合同一致，后者明确以 2 判定手势。原先未准入 xaga，因此保留原生而不生效；早期实现仅准入这个确切构建；当前低风险绘制已改为完整能力合同，不再逐构建添加白名单。

未知构建只有通过完整 ABI、精确实例 / 父容器、主屏、手势模式及有效几何时才替换当前填充；它不改导航输入、窗口或原生 Paint。相同合同共享实现，不新增兼容轮询、日志或线程。以上样本是行为语义的证据来源，并非强制设备名单。

这些是 ARTIFACT / DEX_STATIC 证据；各 ROM 的实机覆盖、性能和完整场景仍单独验收。当前结果及未决项统一见[路线图](../maintenance/A14_REFINEMENT_ROADMAP.md#ios-1862-融合2026-11-06)。
