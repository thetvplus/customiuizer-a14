# A14 当前精修状态

本页只维护当前决定、未决问题与证据入口。任务认领和交接用 [AGENTS](../../AGENTS.md#git-与协作) 及 GitHub，不在这里复制分支数据库或开发流水账。

工程执行策略仅在 A14 试点，分级与验证选择统一在 [TESTING](../TESTING.md#执行分级)；配置审计、测量和未验证范围见[试点证据](A14_EXECUTION_PILOT.md)。本专项不修改业务功能或正式发布工作流，执行规则和验证路径的首轮验收已完成；整体任务效率仍保留试点证据边界。

任务启动与续接采用一次初始化、同一聊天持续继承上下文、按风险增量核验；重新发现仅用于新任务、实际写入冲突或上下文失效。main 合并、推送、发布及破坏性操作保留必要的实时来源和安全检查，不重跑任务发现；规则与技能入口已对齐，未新增状态管理机制或声称未经测量的耗时收益。

已授权开发成果在必要验证后默认提交并推送任务工作分支，以远端完整 SHA 读回确认保存；main / Tag / Release 仍需相应授权。代理优先级、有限重试和网络故障恢复统一维护在 [Git 协作规范](../DEVELOPMENT.md#本地-git-网络与失败恢复)，本机代理配置仅留在仓库 Git 元数据中。

## r14.25.6 设备试用

首轮精修以 `0d6775f0316589c82fd9db46a707c9e5c5f79b95` 为源码基线，设备原版为 r14.25.5 / 254；本地正式签名 Release 已构建并覆盖安装为 r14.25.6 / 255。该首轮 APK 源码 `df1889453f1519900d6f53e467699cef79eb0264`，SHA-256 `c64b4d2ba452bd9dbd4d19dde9c6fce92fb8e3fb24183c09104e29b03f25fcdd`。后续系统活动候选的当前安装状态见下节；公开发布与 main 合并不在本任务内。

当前决定：三形态共享一个固定画布和有限动画时钟，横向中心连续形变，关闭窗口原生移动动画。相近尺寸仅交换内容；自动收起短 / 长基准 160 / 218 dp，展开媒体另按内容及物理可用宽度放宽，小岛自动基准 100 dp。通知中心展开时收为静止小岛并停采集 / 关闭热区，锁屏仍走原生安全隐藏。短上滑先取消点击，再判收起距离；小岛点按无隐式动作，手势可独立配置。明确同会话缓冲保持静止旧内容，实际播放后提交；含糊暂停最多等待 30 秒，重复状态不能续期。消息只留单个当前槽，不增加历史。

小窗按可确认的包 / 用户 / UID 反选来源，独立音乐、允许通知和反馈保持原规则；同 UID 多音轨稳定保留一条。Duo 自动尺寸加 0.5 dp，按原生行高平衡可见字形 / 环体中心。Dock 与岛共用缓存 G4 模型，精度计算留在缓存生成阶段；Android Path 的有限曲段仍是像素绘制近似。“系统提示”统一为原入口：原生 / 匹配状态栏 / 动态岛在同页选择，岛专用选项按样式显示；媒体、应用消息、系统反馈、尺寸、手势进入二级页。原媒体动作键保持，小岛另配点按及五类手势；只留最后一个已验证媒体身份，用户主动操作时才查询同一会话，不存曲目历史。首次新增来源涉及多个宿主，提示已改为重启手机；本次已主动重载相关宿主。

完整主体源码 `bffaaaad555ba95cac8f64a3216b187e61d77f5b`：3539 项 JVM 测试零失败（2 项既定跳过），Debug lint、Develop / Release R8 与 fatal lint 通过。最终 `df188945` 只改 10 种语言的重启提示，应用 Kotlin / Java、构建和测试源码均无差异；补验 28 项原生页面导航、Debug lint、两种压缩构建及 fatal lint 通过。最终两个 APK 的 compileOnly 定义 / 设置入口直接引用为零，setId / getId 各一次、replaceHook 为零，对应各自 mapping 的 HookInstallation 成员；正式历史证书、完整来源、arm64 / SDK 34、无新增录音权限及 16 KiB 对齐通过。672 项工具测试通过（5 项既定跳过）；当前进程矩阵两份输出确定性、11 / 11 必需独立 mutation 通过，无门禁或清理错误。

实机媒体与动画：AyuGram 的一条 USAGE_MEDIA 音轨曾报告三个媒体会话，其中两个同歌 PLAYING；旧唯一会话门禁由此回退，不能归因通知权限。已实测正 session VisualizerEffect 启用，模块 APK 不申请录音权限。选中辅助音源前须核对原生通知键及 PLAYING；早期未选中的等待录像无效，不能作为缓冲证据。纠正后录屏证实 BUFFERING 超过 30 秒保留同一媒体窗口，实际播放后交接一次，下滑暂停进入小岛，左滑恢复同一媒体。最终组合录屏覆盖真实播放器打开中断、独立音乐 + 原生 PiP + 消息、长按展开、复制提示交接和通知中心往返；全程单个窗口，通知中心期间热区为空。短转长段的 48 个录屏采样帧，黑色岛体宽度 466→964 px，水平中心 539.5–540 px；只证明该段实际像素运动，不等同全部场景帧率保证。

原生打断：Launcher SHA-256 为 `a6546d51d9220039ed7ac143de249e9014202cfafeafd715237eb49f8f5a3f7b`。`FastLaunchWindowElement.animTo` 的 override 绕过基础入口，原目标是默认消失矩形；新增两入口按实际声明类分派一次，override 调用 super 时不重复改写或确认，保留单任务 / 包 / 用户 / 全屏 / 非 PiP 验证。七组 CLOSE_TO_HOME 均指向岛目标 `(302,25)-(778,135)`，含快速启动 override；最早打断仍可暂候播放器状态，不能表述为全部 OS 状态百分之百保证。最终组合录屏再次确认打开约 0.58 秒后上滑，原生目标接受且同一媒体窗口恢复。

消息接管：目标 ROM 的 NotificationEntry 构造函数先将 mSensitive 设为 true，heads-up 绑定早于敏感列表计算；旧读取因此误拒正常消息。已改为按当前原生 public-mode / 应用锁策略读取，在提交前及原生隐私更新时重验；策略不可用则保留原生提醒。录屏确认小窗视频播放时，独立音乐持续、允许消息接管同一岛并展开，之后返回音乐。接管仍只消费系统已经准许的标准提醒；通知关闭时不会推断或读取应用内部正文。

低电量：实机弹窗由精确的 `com.miui.securitycenter.remote` 电源进程负责，既有普通 secondary 排除曾跳过它。该进程现在只安装原低电量设置策略，提前返回，不接入安全中心主 UI 或 GenericApp 功能。其警告读取可先于 SystemUI 可见确认；使用一次 handlerless ContentObserver 等待确认信号，上限 64 ms，结束立即解除，超时 / 中断 / 失败保留原生警告，不阻塞 SystemUI 或动画帧。原“隐藏低电量警告”关闭时，14 / 9 / 4% 三档录屏均显示岛且无原生底部窗口；关闭低电量上岛后原生警告恢复，确认标记为空。模拟已 reset，不清除用户电池历史。

Dock：实机确认 ROM 壁纸捕获忽略 sourceCrop，且资源 DisplayMetrics 可取到应用区域；已按缓存的 maximumWindowMetrics 校验完整显示区域后在 GPU 裁切。日志取得 `951×383` 纹理，来源 `1080×2400`，上限 `960×384`；光学法线使用懒生成的 32 KiB RGBA_F16 数据。静态材质有限跟随后停钟，隐藏 / 省电时释放采样，单份在途请求；回退顺序为折射、可用的原生磨砂、纯色 G4 面板。录屏已确认省电面板及恢复折射；用户全局禁用窗口模糊，本机该轮直接回退面板，不能写成原生磨砂实机通过。

原生设置与边界：应用选择搜索区 `[145,364]-[1034,412]` 在列表滚动后不移动，列表从 y469 开始；键盘展开仅列表底边从 y2354 收到 y1526。自动居中模式横向偏移 -4 / 0 / +4 dp，触摸区域中心对应 529 / 540 / 551 px，同一窗口连续移动，已恢复原值。动作 / 应用选择保留原生依赖与生命周期，较大范围列表性能审计仍留到后续专项。

资源测量：本机 Visualizer 采集上限实测 20 Hz，显示约 30 Hz 局部插值；数值稳定时停插值重绘，隐藏、锁屏与通知中心停采集，不设置全局刷新率。波形实现未变的 `dfb96388` 同一媒体源 2 / 0 / 2 模式各约 30 秒，SystemUI CPU 时间为 10.606 / 0.265 / 10.304 tick/s（CLK_TCK=100，即约一个核心的 10.6 / 0.27 / 10.3%）；另一轮锁屏为 0.099 tick/s。短期采样主要在绘制 / 渲染线程；实时波形有持续成本，不能据此宣称整机续航或内存改善，整进程 RSS 波动也不能归因模块。

证据仍在仓库外 `island-r14256-20261025`：最终 APK / mapping / API 查询、完整主体与最终页面 JUnit、组合与安装版录屏、低电量开关对照、Dock 材质回退、应用选择与横向偏移录像、同源 CPU 对照及清理读回。测试音源已卸载并从名单移除；原勿扰、亮屏时长、Duo 两项临时展示与手动低电量隐藏设置已恢复，电量模拟及自建采集进程 / 设备临时文件已清理。日常试用继续保留自动尺寸、100 dp 小岛、实时波形和液态玻璃，用户现场调整的垂直偏移保留。

外部参考按 A14 生命周期及单窗口约束筛选；时长 / 弹幅属于本项目参数，未引入完整 UI / 动画库。

| 参考 | 当前采用与边界 |
| --- | --- |
| [Apple HIG](https://developer.apple.com/design/human-interface-guidelines/live-activities)、[WWDC23](https://developer.apple.com/videos/play/wwdc2023/10194/)、[WWDC26](https://developer.apple.com/videos/play/wwdc2026/223/) | 紧凑 / 展开共享来源、连续速度、克制弹性与最少必要信息；A14 横屏保持原有隐藏合同 |
| [HyperIsland](https://github.com/1812z/HyperIsland) `b25f8992` | 参考采样 / 绘制分离及静音停止原则；不采用全局 session 0、新 ROM Hook 或定时读取配置 |
| [DynamicIslandUtilities](https://github.com/theblixguy/DynamicIslandUtilities) `124fdbd1`、[SwiftUI Demo](https://github.com/stolyarenkoks/Dynamic-Island-SwiftUI-Demo) `90691b4e` | 参考开孔锚点与形态切换；UIKit / SwiftUI 代码不移植 |
| [expressive-cutout](https://github.com/EvanKoe/expressive-cutout) `c93d68e0`、[dynamic-landing](https://github.com/bring-shrubbery/dynamic-landing) `d0050ee8` | 参考中断延续与活动身份原则；不引入 Compose、跨进程心跳及桌面平台所有权层 |
| Status Trio `9bf2cca8`、QWEA0 Liquid Glass Android | 标记光学高度与已有 MIT 折射模型局部重实现；来源和许可保留在原功能文档及随包许可 |
| 用户本地 AyuGram `vanilla-AyuGram-full-20261005.apk`，SHA-256 `2ce48d69be32a6374c0168b27a0f063612ce82be0c5f9ebb7a31c5952d12f883` | DexSQL 核对 `LiquidGlassEffect` 的 Snell 折射与 `GlassEngine` 的位置 / 源变化失效逻辑。本项目沿用自身 G4 法线和有界壁纸纹理，不带入上游渲染树或 APK 资源 |

证据保留仓库外 `customiuizer-a14-evidence/island-r14256-20261025/`；最终门禁、APK / 证书与设备读回完成后更新本节。不把短时 USB 采样解释为长期耗电改善。


## 系统活动接入

本阶段起始候选为 `b278da4408c365d5499a7945c1d03a2f6e7b0cdf`，r14.25.6 / 255，APK SHA-256 `f2e2759fb21de9efa46a21d7968d443e7eca20ce3ef1104bbbda24020249a365`，已读回安装文件摘要；后续实际安装见下节。Release / Develop 的签名、来源、R8 / API 102 边界和 mapping 核对通过。八类来源已选中，时钟 / 录音作用域经框架原生 CLI 追加并读回，时钟加载记录确认 12 个 Hook 安装、无缺失 / 失败。阶段结束由用户接手手动测试，临时界面测试 APK 已卸载，测试音量及通知 AppOp 恢复。

设备边界：秒表的坐标和原生无障碍点击均未启动，隐藏岛、临时去掉本模块时钟作用域的对照仍可复现，尚未把此项记作岛接入成功或认定为新 Hook 回归。系统公开计时 Intent 可启动原生 20 秒计时服务，但该路径的岛内容 / 交接尚未完成录屏确认；窗口存在不能证明内容接入。录音、隐私、真实通话和短信 / 日历提醒的设备组合仍待验，不能将以下源码实现、Host 或静态合同等同于全部实机通过。

2026-10-26 已重新核对旧候选：当前源码已实现统一“系统活动”页，活动来源以八个原生开关铺开，共用原有集合，默认不选；即时反馈保留原分类。媒体、消息通知继续使用原专用页。所有来源沿用同一个岛窗口、形态、交接动画与安全 owner，不新增权限、后台服务、周期扫描或消息历史。

| 活动 | 当前来源及边界 |
| --- | --- |
| 录音 | 原生 RecorderService 状态 / 时长边沿；以语义字串及完整签名定位，桥接只在安装时存在并释放。状态 1 录制、2 暂停；不取文件名、文件、音频。无需通知，但需要时钟 / 录音对应作用域与源进程重启 |
| 隐私 | AppOpsControllerImpl 已过滤活动 / 最近访问的只读快照；事件触发、IO 合并读取，避免主线程原生断言。下游隐私图标隐藏设置保留；多个活动时在同岛保留区显示指示，不抢媒体 |
| 通话 | 受保护 PHONE_STATE 事件和 SystemUI 既有授权下的 Telecom / Telephony 聚合状态；只显示来电 / 通话及返回原生通话界面，不读号码或自动拨号 / 接听 |
| 闹钟 | 时钟原生有效性 / 用户空间检查之后的响铃入口与服务结束；不把“下次闹钟变更”当响铃，不替换声音、全屏或稍后提醒 |
| 倒计时 | 当前 TimerService 模型变更，使用原生截止时间、暂停剩余值。归零与响铃最多三秒交接，早取消立即退回；不读旧 DAO 备份推断存活 |
| 秒表 | 自身三个原生偏好键的变更，合并当前事件循环读取；当前版本普通秒表没有持续通知，原生通知只用于很长运行里程碑，不能作为普通来源 |
| 短信 / 日历 | 只接原生最终允许的标准提醒，保留免打扰、应用锁 / 敏感、当前用户、前台、排除及原生点击。日历兼容无 category 的四个原生提醒通道；关闭通知时不读取正文或数据库补数据 |

原生合同样本为当前设备的 HyperOS 1 / Android 14 SystemUI、时钟 15.15.0、录音和日历 APK，使用 DexSQL 核对具体方法及调用路径。原候选 PrivacyItemController / OngoingCallController 订阅没有直接套用：本 ROM 部分注销入口被内联，秒表和部分通话候选又依赖通知，不能仅凭方法声明决定接入。

每类只保留一个当前标量状态；临时消息 / 反馈之后，优先通话、闹钟、倒计时、录音、秒表、隐私，再回媒体 / 小岛。同身份不会重入场；可见计时交给系统 Chronometer，暂停 / 脱离停止更新。两个源进程用匿名 Binder 存活令牌通知结束，不轮询进程；旧死亡事件、序号和 owner nonce 不能清除新来源。安装前先核对完整合同，部分安装失败撤销已装 Hook。细节见[系统活动合同](../features/island/README.md#系统活动)。

Host 完整 3,548 项回归无失败、2 项跳过；后续日历通道与存活令牌补充专项回归通过。全量工具 672 项中的三个资源 / 文档失败已修正并通过 51 项关联复验；生成链最后 25 项通过。确定性两份输出与必需独立 mutation 11/11 通过。上述为 Host / 静态 ROM 证据，设备双态、进程终止、同岛交接和录屏仍在本轮验收中；实机结果完成后更新本节。证据入口为仓库外 `customiuizer-a14-evidence/system-activities-20261026/`，不将短时采样解释为长期耗电改善。

相册删除等操作结果仍只复用已经完成的标准 Toast；应用内部 Snackbar / 自绘弹窗的成功、取消、撤销 / 恢复合同尚未专项接入，不把本轮系统活动能力写成已覆盖所有应用内弹窗。

## 本轮反馈修正与页面复核

已安装试用源码 `664ff9ab07dad6a4a895dacdc96acfdb4fe1a382` 修复屏幕坐标与岛体变形坐标混用的上滑判断；每次原生 CLOSE_TO_HOME 使用独立返回编号，新打开、触摸、配置失效与停用淘汰旧确认。48 次无新 DOWN 的原生返回及旧回调交错已有 Host 回归。该版实际短上滑录屏未打开播放器，但主屏幕中心页连续 20 次打开中断测试中，第 7、19 次结束仍停在小岛，没有恢复媒体；不能标为百分之百修复。一次目标失效诊断不足以证明根因，尚待进一步复核原生任务 / 前台与 PiP 准备门禁的时序，不放宽单目标、用户、隐私或 PiP 校验。

录音机 5.0.17.8 原生兼容全局版在本机安装于用户应用区，旧 SYSTEM / UPDATED_SYSTEM_APP 限制导致录音来源未订阅，表现为只有隐私提示且点击打开隐私设置。现按显式选择的原生包及实际 UID 订阅；峰值接口通过唯一调用关系与完整签名冷定位，可选失败不阻止计时。实时档复用五条波形，后台可见录制最多约 15 Hz 标量读取、最多 30 Hz 平滑绘制，其他档位不采样。通话、时钟、录音共用新增「上滑回岛」控制，真实通话不主动拨号测试，保留待验。

Dock 自动圆角不再被固定面板半径压小，按设备原生圆角及浅面板边界计算，手动 / 全宽与 G4 模型保留；短岛自动基准降至 160 dp，长岛和小岛参数不变。「灵动额头样式」明确对应 HyperOS 顶部系统胶囊，系统活动来源与即时反馈同层展开。

全页面使用既有原生页面导航回归遍历 4 大分类、34 业务页及 30 独立子页；专项覆盖窄屏中文、横屏夜间、重建、搜索、应用列表搜索与滚动分离，以及来源开关共享原集合。已导出 74 个页面 / 场景并复核，来源开关保持语义顺序。简版地图由真实 XML 再展开一级分组，详细地图、来源条件和语义目录同步，保留有效设置键。Host 完整 3,560 项零失败、2 项既定跳过；页面最终 28 项和工具受影响复验通过，确定性两输出与独立 mutation 11/11 通过。Develop / 正式签名 Release 构建及 fatal lint、同产物 R8 / API 102、来源及证书检查通过。实际安装 APK SHA-256 `96c7d8f558aa28b43d9974b1f6d707b04c5ad7eb0a1a96bc5bd3121648d1bb00` 已从手机读回，版本仍为 r14.25.6 / 255。

用户接手后已清理本轮自建录屏 / 界面快照和采集环境；没有留下 logcat / profiler 采集流，电池模拟已关闭。保留用户应用、原生服务及当前功能设置，未在接手后触控、锁屏或再次安装。后续仅静态开发，不依赖手机仍连接。证据在仓库外 `customiuizer-a14-evidence/return-dock-pages-20261026/`，主要入口为 `installed.json`、`continuous-return.json`、两段原始 MP4 和 `cleanup.json`；正常片段的原尺寸 PNG、联系图及无声剪辑另导出为素材包，不以精选片段掩盖上述两次漏接。

静态续作源码 `506667687dba17ba04932afdabc5220e9e6f8e82`：新增同一 controller 所有的动画决策组件，所有来源继续复用同一有限时钟；小岛接入、反馈后恢复与首次进场不再取决于原生「上滑回岛」开关，同一活动更新不重播。按用户反馈将音乐返回末段收束降至加强版的 75%，内容缩放由 8% 减为 4%；相近尺寸保持无额外回弹。录音的原生峰值改为固定低增益并保留 15% 顶部余量，采样及停止规则不变。自动尺寸下，正文较多的通知、较长的系统状态和含暂停详情的计时 / 录音可展开，长岛尺寸不变；暂停 / 恢复可跟随状态调整，用户手动展开或收起优先。完整 3,566 项零失败、2 项既定跳过，Debug lint 及全量工具 673 项通过（5 项既定跳过）。未签名 Develop 压缩构建、fatal lint、该 APK 的来源 / R8 mapping / API 102 边界、SDK 34 / arm64 与 16 KiB 对齐通过，APK SHA-256 `15609f26d1daca88c4a828d023a51e22a50ee4ffe96c10eb93d86d91413138a7`。这批源码尚未安装，动效、实际录音电平及来源组合仍待实机验收；新证据以同目录 `static-` 开头，与手机现有试用版分开保存。

DexSQL 对同一设备录音机 5.0.17.8 的复核确认：原生来电静音功能临时修改 / 恢复铃声模式，并发送「录音中，静音模式已开启」Toast；不代表真实来电，也不调整麦克风增益。当前不屏蔽录音期间全部铃声广播。自动切换的因果标记、原生 Toast 与模块反馈合并仍待专项核实，真实来电行为保留系统处理；证据为 `recorder-mute-native.json`。

## 最近任务堆叠调研

[AOSPStackingRecent](https://github.com/C70246247/AOSPStackingRecent/tree/89aa34d9da59b1bbd119251a718e19080c5e24fa) 目标为 AOSP Launcher3 / Pixel，不是本机 MiuiHome；该源树未见许可证，暂不复制代码。其 `RecentsHook` 在绘制链刷新偏好、反射读写额外字段并嵌套遍历卡片，README 也保留动画缺陷待办。只参考重叠、缩放和连续消除的交互目标，不引入该热路径。

当前设备 MiuiHome 的 DexSQL 核对确认：`TaskStackViewLayoutStyle.create` 仅值 1 选择横向，其余回纵向；两者分别使用 Horizontal / Vertical 算法与滚动器，不能把较新 ROM 的布局值直接移植。横向 `getTaskViewTransform(int,float,TaskViewTransform)` 已复用传入对象写 scale、alpha、rect 和可见性，可作为低开销局部适配候选；还需核对命中范围、层叠顺序、划除、分屏、小窗及原生应用窗口交接，不能只改 scale / translation 后宣称可用。首版建议仅适配横排，纵排保持原生。

可参考的有许可算法来自 AOSP 官方镜像的 [TaskStackLayoutAlgorithm](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-9.0.0_r1/packages/SystemUI/src/com/android/systemui/recents/views/TaskStackLayoutAlgorithm.java) 与 [TaskStackLowRamLayoutAlgorithm](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-9.0.0_r1/packages/SystemUI/src/com/android/systemui/recents/views/lowram/TaskStackLowRamLayoutAlgorithm.java)，均有 Apache 2.0 文件声明。参考几何按布局变化更新、复用输出对象及有界可见区的方式；旧 AOSP SystemUI 的整套滚动 / 渲染代码不直接移植到本机桌面。

交互方向参考 [Apple App Switcher](https://support.apple.com/en-ie/guide/iphone/iph1a1f981ad/ios)；[vivo 社区的 OriginOS 堆叠介绍](https://bbs.vivo.com.cn/newbbs/thread/38911045)只作为层次与视觉参考，不视为公开实现。设置归属建议将卡片外观及堆叠放在「桌面 → 最近任务」，保留现有系统级任务策略的正确宿主；迁移须保持设置键、搜索、旧入口与备份。本轮按“先找资料”完成调研，尚未加入堆叠渲染器或移动现有最近任务页。

## 事实来源

| 范围 | 权威入口 |
| --- | --- |
| 平台、API 与运行契约 | [COMPATIBILITY](../../COMPATIBILITY.md)、[ARCHITECTURE](../../ARCHITECTURE.md) |
| 功能与进程 | [Feature catalog](../../app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature)、[矩阵 JSON](../rom-intelligence/A14_PROCESS_MATRIX.json) / [CSV](../rom-intelligence/A14_PROCESS_MATRIX.csv) |
| 页面、偏好与搜索 | `app/src/main/res/xml/prefs_*.xml`、构建生成目录、[语义目录](../../feature-semantics/a14.json)；记录数不是已验收功能数 |
| 当前行为 | [页面功能地图](../FEATURE_MAP.md)、[遗留语义](LEGACY_SEMANTIC_CALIBRATION.md)、[退役决定](A14_FEATURE_RETIREMENTS.md) |
| ROM / DEX | [DexSQL](../rom-intelligence/DEXSQL.md)、[SQL 查询集](../../tools/dexsql/README.md)、[M03 合同](../rom-intelligence/M03_NOTIFICATION_CONTRACT.md) |
| 发布 | [RELEASE](../RELEASE.md)、实际 GitHub Release；Changelog 保存发行事实 |

r14.25.5 的 versionCode 253 测试候选曾以原应用身份及历史正式证书覆盖安装，用户确认该轮已测场景通过。当前正式源码版本为 r14.25.5 / 254，累计说明从 r14.24.1 对比；后续动画与 Hook 参数读取的源码整合不扩大 253 的实机验收范围。旧 r14.25.5 / 250 已撤回且草稿清除，不能晋升或作为新修复的验收依据。发行规则见 [发布与恢复](../RELEASE.md)，公开状态以[源码发行](https://github.com/thetvplus/customiuizer-a14/releases)、[Xposed 发行](https://github.com/Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14/releases)为准。

### 当前实机差异验收

当前本地构建另扩展高度 32–64 dp、短宽 96–320 dp、长宽 120–400 dp。自动宽度与默认尺寸保留；用户明确选择手动宽模式可让时间、电池移动或隐藏，仍受屏幕 / 开孔安全边界约束。文字、封面、波形和触摸几何同步，小图标遮挡判定外扩 4 dp。253 上的 PiP 开关两态、音乐快速返回（当前页有 / 无应用图标）与手动尺寸联动由用户操作验收并反馈全部通过；不沿用 252 的失败结论。

动态岛动画源码专项保留上述 253 实机行为；短长形态使用同一有限时长时钟，视觉内容仅保留一个待交接的最新值，取消 / 分离时清理。过渡绘制边界按当前几何有界扩展，仅归属一个窗口并随窗口移除释放，不增加逐帧窗口布局。收缩保留当前可见宽高，位置重定向延续当前物理起点。手动模式按随高度匹配的自然短形态阈值分类，再使用对应手动宽度；调小短宽不再反向触发长形态，长宽不窄于短宽及物理安全约束不变。本专项只验证定向 Host 数值与契约，不包含设备外观、帧率或性能结论。

实机安装 APK 对应源码 `5f6a39345e7df75287efe8d7b19b3c451e3d7388`，SHA-256 为 `3dbac3b04584dc9a57890393e513850e5e08bc958fb817bc5968cd4b197799fd`。已核对应用身份、设置及作用域保留、模块启用与完整重启恢复；本轮未清除用户数据。完整 Host 门禁 3454 项（2 项跳过）无失败，工具测试原有 664 项中的文档摘要失配已修正并通过相关 11 项复验；正式 R8 / D8、签名及安装包一致性通过。详细结果保留在仓库外；收口不重复等价验证。

既有设备验收基于 HyperOS 1 / Android 14 目标样本，继承已通过的 23 组有效差异场景及未改渲染代码的性能证据。隔离 Debug 仅承担补充生命周期试验；正式验收使用原应用身份、历史证书和保留数据的覆盖安装，最终发布二进制另行记录签名与来源检查；设备验收仍按实际已测源码范围报告。原始证据保留在仓库外；APK、主机门禁、ROM 证据、设备结果与最终清理分别记录。

| 确认问题 | 当前结果与证据边界 |
| --- | --- |
| 设置说明时有时无 | `81c6be5` 从当前 Preference 绑定 / 清空说明，消除回收行残留；Host 覆盖空值、provider、样式、计数与自定义说明，实机滚动复用通过 |
| 状态栏双击 PiP 动作未安装 | `81c6be5` 补齐中 / 左 / 右入口。实机中间单独配置收起得到 0→2→0；左右入口完整隐藏 / 恢复、收起 / 恢复通过，隐藏时播放器仍为 PLAYING，触摸窗口撤销 |
| 音乐返回图标与浮动副本 | 撤回 `f7ec211` 的完整动画通过结论：旧证据只证明结束后恢复，快速上滑录像确认浮动图标被送到顶部且桌面图标延后补回。当前实现从 `findClosingAnimTarget` 的原生无图标分支前移接入，不查找、快照或隐藏桌面图标；保留原生应用窗口弹簧及回调。已选择图标的迟到路径和自动 PiP 不接管；常规返回已由用户确认正常，快速打开立即返回偶发漏接通过原生打开动画及选中播放器前台边沿预取 / 续发有效快照修正，不延长五秒有效期、不等待或轮询。253 当前页有 / 无图标的快速返回由用户反馈通过，额外取消与复杂组合仍按下方矩阵保留边界 |
| PiP 进入期间半次闪现 | 最新 Shell 标量快照增加绘制前门禁，未显示的入场直接取消；已显示的内容沿既有退场动画结束，锁屏保持立即清理。合并拒绝帧处理并随 owner 释放，不增加逐帧 Binder / I/O / 反射或轮询。用户确认 252 两态仍有半次 / 一点五次入场；45 秒人工记录的 8 个循环证明普通媒体约 765 ms 入场，视频播放器自动 PiP 约 866 ms 才申请。当前以回桌面边沿的一次有界任务查询提前识别声明的自动 PiP，等进入稳定后再按开关放行；原生 TRANSIT_PIP 请求继续提供确认 / 取消，单个 token / deadline 随 native 状态、取消、owner 替换或五秒兜底释放。关闭开关允许媒体与 PiP 共存，不接管视频窗口；专项 Host 覆盖，253 原证书安装后用户确认开关两态正常 |
| 横屏仍显示动态岛 | `e3659942` 给附着 owner 绑定 root 窗口宽度监听，读取当前旋转门禁并复用有代际 / 取消的几何刷新。真实 2400×1080、资源配置未回调场景从可见变为隐藏，回竖屏恢复；Host 验证无配置回调、相同宽度不查 metrics、dispose 移除监听 |
| 第三方旧高度资源 | `ebaa031` 的系统原生尺寸覆盖对齐第三方应用的 104→133 px 布局，不扩应用作用域；43 / 46 dp 替换保持一组文件，默认值自动撤销且文件为零，模块禁用完整重启回原高度，活动卸载自动删除本模块定义与文件。四项资源共用固定 ID，持久化 enabled 始终 false；原生重启状态计算负责不加载模块后的安全回退 |
| 高度偏好缓存与回收 | `1983146`：已提交设置通知抵达系统进程但仍读旧缓存，改为身份校验后从非公开、仅系统 UID 可读入口读取一个已确认高度，随同一次原子快照发布；不动框架缓存或重建监听。旧文件只在全局无引用且属于本模块时经原生接口回收，限制不确定失败后的新建和待回收数量 |
| 应用自有小窗 | `fde05c5`：原生 Session 可见集合 + 显隐 / 死亡事件，按用户及默认屏幕过滤，有界扫描 / 快照，不留窗口引用、不轮询、不注入应用。应用自有 TYPE_APPLICATION_OVERLAY 可见时 DND 岛隐藏，关闭后恢复；新 owner 拒绝其它 owner 回复和旧序列，鲜活全量事件可补足丢失的首份回复 |

补测通过的范围包括位置三态及安全钳制、数值精确输入与进程重启持久化、通知栏 / 控制中心 / 锁屏门禁、原生 PiP 和系统 freeform 整岛隐藏、PiP 隐藏跨锁屏保持及手动恢复、实时波形锁屏释放 / 解锁重建。PiP 退出后的 NOT_VISIBLE / NOT_TOUCHABLE 原生记录不能当作活动触摸窗口；搜索导航返回按现有契约清空查询，不能把“保留查询”的错误预期算作缺陷。

历史延续性横向覆盖 r14.24 Dock 普通 / 全宽模式、应用 / 最近任务返回与解锁几何，r14.22～r14.23 Duo 充电标识 / 百分比及已有 Wi-Fi / 单卡组合，r14.21～r14.25 搜索定位与四类深层页面返回。没有穷举历史全部功能；双卡数据切换、蓝牙附件、多用户、真实新消息接管与撤回等仍待独立设备场景。

性能继承同一渲染代码的有效对照：实时波形 SystemUI 约 8.47～8.83% 单核、SurfaceFlinger 约 11.9%，轻量约 8.35% / 12.1%，关闭波形约 0.19% / 0.06%。主要开销来自持续绘制 / 合成；短期 PSS 波动不能证明泄漏或内存收益。本批未改渲染循环、不重复长采样；新增锁屏采集释放 / 可见媒体恢复断言。最终 exact HEAD 的完整门禁、Host / 工具测试、正式 R8 / D8 APK 及设备结果分别核验；旧候选的通过数不能覆盖新改动，也不等同于全部 ROM 或全部功能实机通过。

全局高度与应用自有悬浮窗已接入当前实现，功能开关遵守完整重启语义；普通 Activity 内的迷你 View 不冒充系统窗口或原生 PiP。聊天图片预览的已有可靠采样为原生 FULLSCREEN 显隐，Insets 保持 133 px，未捕获旧高度回退；保留应用全屏行为，不宣称消除全部转场跳动。真实新通知的接管 / 更新 / 撤回、快速 PiP 替换 / 旋转、相机 / 图库预览视觉、蓝牙、双卡、多用户及长期内存 / 功耗仍待专项设备覆盖。

正式交付沿用原身份、历史证书和同二进制来源检查。仅在双仓新版全部验收成功后清理被替代的 r14.25.x 页面与标签；原始 Git 提交和必要来源证据保留，操作记录不写入本页。

## 已整合的当前工作

| 范围 | 当前实现与入口 | 验证边界 |
| --- | --- | --- |
| 共享偏好发布 | 启动、单键与批量刷新共用发布锁，完整快照先于就绪状态；读取失败保留有效值，确认删除才移除，观察者在锁外分发；保留已提交高度优先级和安装型开关冻结 | 原实现的过早就绪、新值覆盖及读取失败误删已由可控线程交错复现；新增五项 Host 回归及既有高度默认恢复、单次安装、岛生命周期回归通过，不扩大原有 253 实机验收范围 |
| Hook 参数读取 | before / after 单项读取共用 `getArg`，迁移状态栏、控制中心、锁屏、电池、截图、Dock、Duo 与岛 / PiP 的 36 处读取；实际参数改写保留共享可变数组与原调用语义 | Deep 的 Full / 工具、加强后的链语义定向回归、未签名 Develop / fatal lint 及 R8 / API 边界复核通过；四参数 Host 对照每次少 32 B，方法与证据见[优化门禁](../OPTIMIZATION_GATES.md#保留的测量方法与维护决定)。没有新增常驻状态，不将该结果外推为设备性能或扩大既有实机验收 |
| 维护与隐私 | `MaintenanceJournal` 在特权操作前可靠保存精确原状态；严格解码、回读、补偿及私有恢复记录排除备份传输 | 故障注入已覆盖；进程中断和实际设备恢复待验 |
| 通知 M03 | 点击事务限定原生 owner；Heads-up 延迟任务、通知着色和 Count / Fold 保留原生生命周期；退役 system_server 全局频道保护绕过 | 源码 / CN-TW 静态证据和回归已齐，实际频道、认证、列表绘制待验 |
| 共享手势 M05 / M07 | `447aeb1` 只释放本 runtime 的令牌，结束事件的 effect 异常也释放；不再全局清除状态栏正在进行的手势 | owner / 异常回归已纳入 |
| 波形 | `7bb9a0b`、`4f3ef70` 保留真实幅度、弱高频与衰减，默认轻量，实时保持实验开关和身份 / 失败释放 | 当前 ROM 有短期性能对照、模式释放及锁屏释放 / 解锁恢复；长期内存与耗电仍无结论 |
| 音乐直接上岛 | 显式选中 App、标准全屏 CLOSE_TO_HOME、native 实际采用且运行时才确认；在目标选择阶段使用原生无图标分支，仅收拢应用窗口 | 默认关闭；253 当前页有 / 无图标的快速 Home 已由用户分别验收；额外取消与复杂组合见下表，其它 ROM 私有接口待验 |
| 通知与媒体安全 | `0ab7a975`：独立 Keyguard 门禁、Row / Sbn / binding 事务、首份在途媒体读取失效；普通媒体返回保留 750 ms | 实机已验媒体、系统反馈和锁屏组合；真实新消息的接管 / 更新 / 撤回尚未补齐 |
| 状态栏 / Insets | `0a872d9` 静态证据，`451dd52` 修复父窗口尺寸更新、最新原生基线、属性所有权、detach / 重附着和退化 frame | Host 缺陷已修复；当前聊天图片预览样本经用户澄清为原生全屏显隐，保留该行为；额外 Insets / 旋转组合仍待验 |
| 模糊 / 系统栏颜色 | 保留窗口半透明请求、清除失效 ActionBar 色；可选元数据失败仍执行原方法；Dock 采样异常按 generation 失效 | CONFIRMED 源码 / Host 缺陷已修复；相机 / 图库发灰待设备关联 |
| PiP / 小窗 / 手势 | 原生 PiP 状态和身份约束媒体岛；可选整岛隐藏；全局 / 音乐手势增加完整隐藏和原生边缘收起 | 当前 ROM 已验三种双击入口、输入撤销 / 恢复、隐藏时播放、跨锁屏及原生 freeform；应用自有 Overlay 已补验；253 普通 PiP 开关两态由用户验收，取消 / 快速替换和多显示屏仍有边界 |
| Launcher 音乐返回图标 | 原生无图标目标避免借用桌面图标，迟到状态拒绝接管已选择的图标；原生完成 / 取消策略保持 | 旧版错误浮动副本及末端恢复的不足保留在证据中；253 当前页有 / 无图标快速返回已由用户验收，额外取消和复杂组合仍按下表核验 |
| Dock / 发布 | Dock 合同保持；在 `3e0709f` 两公开资产、签名 / 证明及历史恢复上，补入 `0bbc1b6` 的创建 / 上传后草稿标题与说明核验 | 篡改须在公开前失败；历史标题只在原候选恢复时接受 |
| 正式文档 / CI | 已按真实 r14.25.3 发行修复下载仓库文档；正式源码基线 / 完整 diff 进入既有审核，双端验收后同步文档，原恢复入口支持 docs-only；CI buffer 与 Actions 文件输出隔离 | source-only / 预发行、幂等 / 并发 / 未知响应和失败重试有回归；正式状态以双仓实际资产为准 |

### 通知与媒体

- Keyguard 的 showing / going-away / fading / bouncer / requested / upcoming / dreaming 与状态栏回调不是同一状态。接管前后重新校验，未知或异常拒绝呈现；复用 native Callback，随 safety owner 释放。
- 只有当前通知、Row、entry、binding 及成功插入的窗口都有效时才提交。未提交失败恢复自己的 lease / bit / binding；提交后的 native unbind 失败不重放提醒。旧同 key 回调不能清新 Sbn，同毫秒更新也失效旧点击。
- `unbindHeadsUpView` 的 flag 4 表示内容 freeable / rebind，不能推断普通或锁屏通知被删除。NMS 记录、Pipeline Entry / Row、隐私过滤和真实绘制必须分别观察。
- 直接 App / Home 切换会失效在途首份媒体读取；native owner 丢失时清媒体。普通路径保留 750 ms，可选 native-return 只在有效确认后使用其单次短延迟，不能让旧快照复活。

### 状态栏几何

每个 View 的 Binding 持有最新观察到的原生值与模块最后写入值；外部后续写入不被释放逻辑覆盖。成功执行的原生 `PhoneStatusBarView.updateStatusBarHeight()V` 也捕获新基线，覆盖“新原生值恰好等于模块上次写入值”的边界。

父窗口监听随实际 attach 绑定，失效 / 换窗口先释放旧 owner。位移合并原生与用户值后统一裁定，不累加；默认配置不清空未持有的 translationY / gravity。`InsetsSource.setFrame` 两个重载的非正宽高原样 proceed，有效 client-hidden frame 仍按原合同调整。H2 / H3 / DecorInsets 缓存、FeatureRegistry 与动态岛所有权不变。

Host 观测确认父窗口 129→60、原生高度 80→模块 129→ROM 96、位移 7 加用户 ±5 / 0、退化 frame 等缺陷；测试驱动完整布局及真实父事件，另核对相同输入不重复写 LP。这些是模型输入与写入计数，不是设备轨迹或性能收益。

### 模糊、系统栏颜色与 PiP

**CONFIRMED：** `PhoneWindow.setStatusBarColor` 原 Hook 把半透明色清为透明，或用该 Window 缓存的 ActionBar 色覆盖它。TW A14 `DecorView.calculateBarColor(IIIIIIZ)I` 表明：透明色和半透明色走不同的原生 Contrast Scrim 分支；Host 实际调用同名平台方法也复现该差异。现在布局完成、显式颜色请求和 Toolbar 背景更新均保留窗口的透明 / 半透明颜色；非纯色背景移除旧缓存，普通不透明色仍按原功能匹配。可选 Toolbar 字段异常经既有安全边界隔离，原方法恰好执行一次。不改导航栏、Contrast、Dim、Appearance、Insets 或应用名单。

此缺陷只作用于已启用 `system_statusbarcolor` 且在 `system_statusbarcolor_apps` 中的应用，不能据此归因全部上下栏发灰。TW 图库 `PhotoPageFragment` 自行切换系统栏、导航颜色、夜间 / 多窗口状态；相机入口的实际窗口与转场配置仍须设备关联。窗口模糊原生禁用后的应用回退、原生遮罩与模块冲突分别记录，不能全局强制透明或绕过原生对比度保护。

| 检查范围 | 当前源码结论 |
| --- | --- |
| Window Blur / Blur Behind | 模块只向 native disabled 原因做 OR；不改系统设置、窗口 blur radius 或原生节能 / 温控 / tunnel 状态。Host 覆盖开关恢复、最新系统设置及 Controller 替换；保留既有安装状态 |
| 通知中心 / 控制中心 | 偏好快照、目标弱引用与 ThreadLocal scope 保持；finally 退出作用域，不把局部倍率扩成全局窗口策略 |
| 音量面板 | 原方法先恢复 native Dim / focus；关闭时仅释放模块持有的 collapsed blur，沿用单份配置快照和插件生命周期 |
| 文件夹 / 最近任务 | Folder owner 与 recents session 分流，默认值返回 native；会话结束清除一次性比例，不写全局 blur setting |
| Dock 磨砂 / 玻璃 | 单 owner、单在途采样、单 texture / shader；隐藏与 detach 停止调度，HardwareBuffer 关闭，旧 Bitmap 回收。迟到异常与迟到帧同样按 epoch 失效，不让旧壁纸失败永久退役新 lens |
| 动态岛局部模糊 / 避让 | RenderEffect 仅作用于模块 snapshot；至多 1 MiB 图像、6 个效果级别，取消 / detach 清理；不逐帧创建效果或改原生 View 的 RenderEffect |
| 锁屏封面 / 通知着色 / 隐私缩略图 | 保留现有串行封面处理、generation 拒旧、按字节缓存、原图所有权及停止释放；通知 Row 颜色与隐私缩略图策略独立于窗口模糊 |

**CONFIRMED（源码 / Host）：** `foregroundChanged()` 的包名与 MediaSession 不能区分普通 Home 与 PiP。CN / TW / 历史实机 SystemUI 原件的相关方法体一致：swipe-PiP 先进入 `PipTransitionState` 3，再由 `PipBoundsState.setBoundsStateForEntry` 提供组件 / UID，Pinned task 出现为 1、完成为 4、退出为 5、取消 / 退出结束为 0；退出后 `mTaskInfo` 仍可能保留旧任务。现在通过同一 native organizer 的事件发布有 generation 的标量身份，状态 3 不复用旧任务；同包 / 用户 / 默认屏幕的进入和可见 PiP 阻止媒体岛准备 / 入场。渲染提交前读取已发布快照，覆盖 Shell 事件已发生、Main 回调尚未到达的间隙。取消或退出恢复原有 750 ms 规则，不增加延迟、轮询或播放器包名特判。Host 覆盖准备后取消、重建、快速前台切换、旧 generation、不同用户 / 播放器及普通 Home。

默认关闭的 `system_island_hide_in_floating_window` 是安装快照，完整重启手机后生效；普通偏好刷新和 View 重建不会提前应用。打开后只要默认屏幕存在 PiP 或当前用户的系统小窗就隐藏整岛，包含已收起 / 完整隐藏的 PiP；后台事件不重复排队，通知 / Toast 保留原生回退。系统小窗在 attach 和 native 模式事件时串行查询一次，迟到结果按用户 / generation 作废，无持续轮询。音乐直接上岛发布器随抑制关闭，旧提交失效，恢复后新事件仍可使用。关闭整岛开关后，允许同一视频或其它后台音乐的媒体岛与可见、隐藏、收起的 PiP 共存；自动 PiP 的视频窗口仍保持原生转场，不参与音乐返回窗口收拢。

**DEVICE_REQUIRED：** 仅检查已发布状态的旧候选未通过快速 PiP 验收，不能沿用其“已修复”结论。当前 ROM `PipTransition.handleRequest → augmentRequest` 的 type 10 请求先于 organizer 状态，现已结合 Home 边沿预识别门禁；253 开关两态的普通快速返回由用户验收。额外取消、任务替换及全部播放器转场仍需分别验证。

### 全局动作与 PiP 控制

已有动作编号、配置和默认手势保持。新增 33（完整隐藏 / 恢复）与 34（边缘收起 / 恢复），接入全局动作和音乐岛五项手势，未加入锁屏动作。仅配置相应动作时安装 native 合同，首次配置需重启 SystemUI；关闭音乐岛不影响全局入口。

| 路径 | 已核实的合同与边界 |
| --- | --- |
| 原生边缘收起 | 调用 ROM PiP accessibility 中按名称解析的 stash / unstash 动作；沿用原生 bounds、动画、协调器和 stashed 状态，不模拟手势或复制坐标 |
| 完整隐藏 | 当前任务 token / Surface、稳定状态、解锁和默认屏幕约束；原生菜单以类型 0 关闭，单事务更新 native leash 的 alpha 与 Surface hidden policy，同时销毁 input consumer / dispose receiver。恢复走原生注册并检查 input token；释放自身 hidden flag 时仍保留原生锁屏 alpha，失败补偿，原生动画第一笔事务 / 生命周期变化前归还控制 |
| 任务 / owner 变化 | 单个 lease、单份在途命令；Shell executor 执行前重验 owner / 任务 / Surface，旧 owner 不能修改新任务；无媒体 transport、移除任务、常驻服务或轮询 |
| 截图临时隐藏 | 截图结束不会揭开手势持有的隐藏；截图期间拒绝新的窗口动作。既有截图 receiver 改为借用最新 organizer 弱引用，不滞留首次 owner；交易及时关闭，重复出现不重复注册 |
| 全局动作审查 | 确认 SystemUI / system_server 路由、发送者边界、启动快照、动作矩阵与 ROM 原生入口；模块资源只在需要提示时读取。小窗贴边的既有 200 ms 组合和滚动到顶的合成输入仍需目标操作时间线，未据猜测改写 |

当前 ROM 已实测完整隐藏 / 输入撤销与恢复、触摸穿透、锁屏后保持隐藏并手动恢复、退出后的无活动触摸窗口，以及原生 PiP / freeform 的整岛抑制。实际隐藏样本为 PLAYING，不能据此保证播放器全部策略；PiP 隐藏期间旋转、快速替换、转场逐帧连续性和所有手势组合仍需补齐。不能将静态合同命中称为“完美贴合 ROM”。

输入依据分层：目标 ROM `InputMonitor` 将 PiP input consumer 放在独立 display-area 层，不能只靠 task alpha 隐藏；A14 [SurfaceFlinger Layer](https://android.googlesource.com/platform/frameworks/native/+/refs/tags/android-14.0.0_r1/services/surfaceflinger/Layer.cpp) 的 inherited hidden policy / input visibility 说明为何还需隐藏父 Surface。DEX / AOSP 源码不是目标 ROM native 二进制或设备触摸验收。

### 外部算法参考

外部参考样本的身份、版本、哈希及来源对照保留在仓库外证据，公开文档只保留可迁移的算法结论。仅分析相关 DEX 与资源，不把 APK 文件名视为源码版本证明；未建立样本与公开源码的逐文件对应，不复制第三方代码或 shader。

| 取证入口 | 可借鉴性与当前决定 |
| --- | --- |
| 单 shader 玻璃效果 | 圆角 SDF、差分法线和折射背景像素，使用单个 RuntimeShader；模糊由独立来源提供。Dock 已有真实壁纸采样和折射，保留现有效果 / 裁切 / 材质；不引入外部参数或更换 shader |
| 未变参数去重 | 借鉴未变参数跳过写入的思路，Dock 自有实现使用精确值比较，保留亚像素变化；每 owner 两份固定 13 浮点缓冲，shader 清除即失效，无全局缓存。Host 统计重复绘制的 7 次 uniform 写入降到 0，重建重新写入 |
| 显示列表及 Bitmap 代际 | 尺寸 / 内容 hash / Bitmap generation 联合拒绝重录；适合可准确获知源变化的同进程 View。壁纸是跨进程合成来源，Bitmap generation 不能证明壁纸没变；不据此停采样或增加全树 hash |
| 有限动画时钟 | 目标不变不重启动画，有限时钟只在转场中请求重绘。岛已有携带速度的临界阻尼、generation 和停止释放，保留既有机制，不替换成会丢速度的通用插值器 |

可量化的 Host 改进包括未改色时参数副本由每次 1 份降为 0，改色时才复制；相同 Toolbar 颜色不再重复写，以及上述 Dock uniform 写入计数。没有 CPU / GPU、PSS、帧率或耗电结论。ADB 只检查一次：`SKIPPED: no available ADB device`。

### Launcher 图标所有权

**已确认隐藏与恢复机制，尚未确认持续消失根因。** 图标随音乐岛消失 / 恢复是用户观察，没有同一过渡的 View、绑定和动画时间线，不能证明因果，也未排除父级裁切、Drawable、重绑定或其他模块。

历史 Launcher：`com.miui.home` / `RELEASE-4.39.30.8604-08262248`，SHA-256 `a6546d51d9220039ed7ac143de249e9014202cfafeafd715237eb49f8f5a3f7b`；CN / TW 参考样本与历史实机原件同哈希。历史 SystemUI：`5d8f2fe0b65d8a1a947b4280f8053b524f8c5de73f48a74f8792d415ae76e513`。这不是当前手机 APK / split 或配置读回。

| 路径 | 已核实的 native 所有权 |
| --- | --- |
| 打开 / 返回 | `WindowAnimParamsProvider` 的开 / 关 listener 修改目标内部图像 alpha；`ShortcutIcon.onBackAnimStart()` 另有 INVISIBLE 路径，需分开采样 |
| 正常完成 | `WindowElement.onFinishCompleted()` / native end → `resetFloatingIcon(false)` → 当前目标 `showIcon()` → recycle |
| 普通取消 | `WindowElement` cancel listener 调用 `recycle(false)` → `FloatingIconView2.finishImmediately()` 恢复；其自身空 end 不是丢失清理的证据 |
| Shell / merge | Shell cancel 和 merge 仍有后续 owner；新动画仍运行时不能提前恢复图标 |
| 换目标 | `initTargetView` / `replaceFloatingIconViewContent` 先清旧目标再交接；Looper 同线程立即执行，不因出现 lambda 就判断竞态 |
| 待证候选 | 跨 Looper 排队期间换 target / 同 View 换绑，尚无真实动画 / binding 身份证据，不新增 generation 层或恢复 Hook |

旧启动动画开关改变原生注册选择；FixAnim 改 spring stiffness；图标缩放与 native floating 落点使用不同坐标口径。这些可指导 A/B，不能单凭静态差异归因持续隐藏。Dock 装饰没有写 Workspace 图标 alpha / visibility；原生焦点通知 IPC 可选择合成状态栏 target，模块动态岛未调用该协议，仍须采样真实目标类。

若将来确需恢复保护，必须依托当前可见 Workspace、同一 ItemIcon / 绑定和 native 动画身份，确认无 floating / back / drag / folder / edit owner 后才恢复。禁止全局扫描、固定延迟、周期刷新或抢回新动画仍持有的图标。

## 待实机验收

发行整合阶段没有可用设备；当前夜间补测的已验范围见上节。下表保留仍需补齐的矩阵与扩展场景，不表示各行全部未测或全部通过。记录真实 ROM fingerprint、APK / split、安装 revision 和配置，保存设置，串行做单变量对照，结束恢复并读回；缺少设备样本不能标为 PASS。

| 优先级 / 范围 | 最小验收闭环 |
| --- | --- |
| P0 通知 | 接管开 / 关：完全解锁、锁屏、AOD、接管中锁屏与立即解锁；敏感 / 应用锁、DND、更新撤回重发；分别读 NMS、Entry / Row 和绘制；列表点击 / 挑战 / 展开 / 清除 |
| P0 模糊 / 系统栏颜色 | 相机→预览与图库直开预览分别对照窗口模糊、状态栏着色及名单；关联 native blur enabled、窗口 / Decor、上下栏原色、Contrast / Dim / Appearance / Insets 与进退转场，核对无模块覆盖及原生状态恢复 |
| P1 PiP / 媒体岛 | 播放→上滑 PiP、取消、展开退出、关闭及普通 Home；关联 task / pinned / Shell 状态、前台包名、会话 token、reveal deadline、窗口准备 / 取消 / 首帧，确认一次入场且未误抑制其他播放 |
| PiP 动作 / 整岛开关 | 全局 / 音乐手势各自完整隐藏与边缘收起→恢复；点击原位置应落到背后窗口；菜单显示、锁屏、旋转、退出 / 换任务、快速连续手势；整岛开 / 关和音乐直接上岛组合，保持播放且全局恢复入口可用 |
| P1 Launcher 图标 | Workspace 直开播放器→Home，岛关 / 开、播放 / 暂停、正常 / 快速返回；消失后持续留 Home 再自然移除岛；随后才扩到入口、旧动画、FixAnim、缩放、Dock 和文件夹 |
| P1 状态栏 / Insets | 聊天图片 / 视频全屏进退，补相册、网页 / 视频、旋转、导航与岛展示；T0–T5 记录 server frame、client Insets、native 高度和实际内容位置，不以 Host 修复直接归因应用 |
| 原生音乐返回 | 选中 / 未选中播放器、关闭 / 简化、手势中断、快速切换、横屏 / 分屏 / 小窗、会话 / 用户替换；实际终点、浮动图标恢复及一次确认；普通路径继续 750 ms |
| 波形 / 输入 | 当前用户 UID / 唯一会话和音轨、权限 / AppOps、蓝牙 / 远程 / offload、隐藏 / 暂停 / 切会话释放；真实挖孔、字号 / 密度、慢短上滑与取消、窗口外原生下拉 |
| Dock / 音量 / 维护 | 连续拖拽、编辑 / 文件夹 / 最近任务、壁纸 / 深色、材质与裁切；插件重建、无障碍超时、振动 / 高音量确认；维护进程中断及准确恢复 |

图标诊断至少关联 ItemIcon / binding、内部图像和父链的 alpha / visibility / clip / Drawable，以及 WindowElement / floating / mAnim、target、running / merge / finish。若需临时诊断，仅 Debug、指定目标和离散事件使用有界单调时间日志，取证后移除；不每帧记录或采集私有标题。

性能另做同设备、同负载 A/B：CPU / GPU、线程、PSS / heap、帧时间、分配和日志频率。实时波形与轻量、Dock 材质分别测，不将静态预算、Host 数值或旧 APK 结果换算成当前设备收益。

## 下一步

优先完成上表的通知安全、Launcher 图标与状态栏设备闭环；复用已完成源码批次，不重新逐开关扫描。M00 / M01 目录校准、M02 声音振动、M03 通知、M05 / M07 手势、M06 Dock、M08 / M10 维护和设置已有回归；M04 锁屏 / 应用锁、M09 其他宿主的未覆盖行为按具体 owner 与证据另立任务，不凭旧计划推广重构。

保留 PrefMap 原子快照、PreferenceBootstrap、Feature 注册、ProcessRouter、ResourceHooks、按 ClassLoader 有界反射缓存及既有 owner scope。封面合并、缓存扩容、Baseline / Startup Profile 等只是候选，需具体复现或测量和独立回退单位；当前没有全面改造依据。

## 证据恢复

[整合检查点的完整记录](https://github.com/thetvplus/customiuizer-a14/blob/c9843589416efdbeba2d0f6d651c1c9e136e9fef/docs/maintenance/A14_REFINEMENT_ROADMAP.md)保留历史 ROM / 安全合同、完整目标描述符、模型失败、样本身份、既有设备边界和各批回退提交。需要原文时用 `git show c9843589416efdbeba2d0f6d651c1c9e136e9fef:docs/maintenance/A14_REFINEMENT_ROADMAP.md`；不把它重新复制成阶段文档。

原始 ROM / 查询 / decode / 日志 / mapping 仍在本机忽略的 `.di-evidence/` 或仓库外，包括 `m03-20261023`、`island-native-compat-20261024`、`launcher-icon-ownership-20261024`、`statusbar-fullscreen-20261024`、`blur-system-20261024` 和状态栏工作树的 `statusbar-fullscreen-fix-20261024`。它们未上传，重做结论时须重新核对工具、样本和安装状态；公共定位合同继续维护在 M03 文档及现有 SQL。
