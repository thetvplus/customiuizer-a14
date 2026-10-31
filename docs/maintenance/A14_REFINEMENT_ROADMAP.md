# A14 当前精修状态

本页只维护当前决定、未决问题与证据入口。任务认领和交接用 [AGENTS](../../AGENTS.md#git-与协作) 及 GitHub，不在这里复制分支数据库或开发流水账。

## 编译警告与 A14 API 治理（2026-10-31）

基线为本地干净工作树 `3e562a158bb7b86c1c1747c218d60982be21ad92`，与当时远端 main 一致；任务在独立工作分支实施，不合并或发布。读取同 SHA 的完整 [普通 CI 日志](https://github.com/thetvplus/customiuizer-a14/actions/runs/41170226245) 与 [签名工作流日志](https://github.com/thetvplus/customiuizer-a14/actions/runs/41171169774)，没有触发新 CI。普通 CI 的 Develop 编译可见 537 条 Kotlin 诊断，按文件与消息归并为 161 组、99 种消息模板；生成设置代码的冗余转换占 168 条，Hook 回调参数名占 159 条。旧验证器截断了成功的 Debug 输出，签名运行复用了原产物，因此这些数目不代表全部 Debug / Java / lint 警告。

| 分类 | 判断与处理 |
| --- | --- |
| A：实际风险 | 认证不再用临时密钥生成推断解锁，不吞致命异常；透明主题 Activity 改用已有 ComponentActivity。恢复文档去掉外部卷状态前置条件；语言变化时使用两个事务实际重建设置 View。11 处反射 / Java 空值边界显式核验，保留原有失败隔离与宿主锁所有权。 |
| B：低风险收益 | Wi-Fi 移除 15 秒循环，读取缓存、被动接收系统扫描广播，仅在缓存超过 30 秒的页面进入 / 状态恢复或手动刷新时请求一次；等待不重叠、12 秒结束，暂停注销并取消等待。移除 168 处生成代码冗余转换、6 条未来 Kotlin 编译错误诊断、两个恒真的旧 SDK 分支、四个无效 alias 属性、无消费者的 bootstrap 包装和存储辅助函数。 |
| C：合理保留 | A14 系统凭据确认 Intent、按需 startScan 与单次 connectionInfo、ROM 广播 NetworkInfo、系统进程无线开关 / 任务查询、目标 Fragment 的内部返回协议、框架要求的 Drawable.getOpacity 和既有菜单 / 生命周期桥。私有接口、权限与进程 Context 抑制以现有 owner 为依据；移除八处不必要的弃用抑制和已调用 super 的 MissingSuperCall 抑制，没有整文件弃用抑制。 |
| D：工具链 | SDK 安装器弃用提示与 Service SDK 测试的 Java 弃用提示保留。测试允许 Conscrypt 所需的 JNI native access；final AtomicReference 更新其内容，故障测试在 test 源码集替换观察者行为，不改写 final 字段或放开 final-field-mutation。 |

备份 / 恢复和认证使用既有 Activity Result 注册机制，恢复文档保留原 ACTION、OPENABLE 与精确 MIME 类型；快捷方式的旧返回协议继续保留。Parcelable 使用 SDK 33 已提供的类型参数重载，外部页面动画改在启动时传入原生 ActivityOptions。没有引入运行依赖或跨版本辅助框架。[系统扫描结果广播](https://developer.android.com/develop/connectivity/wifi/wifi-scan)可复用其他来源的完整扫描；[凭据确认](https://developer.android.com/reference/android/app/KeyguardManager#createConfirmDeviceCredentialIntent(java.lang.CharSequence,%20java.lang.CharSequence))在 A14 继续使用原系统流程，暂不增加 BiometricPrompt 的取消与重建所有权。

保留 AGP 9.5 的 OptimizeResourcesTask 后处理：紧凑编码在 R8 的 proto→binary 转换后需要重新应用，当前公开 artifact API 没有等效的优化后资源表入口。APK 级公开变换会扩大解包、重打包和签名前顺序责任；本轮不重建这一流程。同一实际资源包的 AAPT2 往返验证：proto→binary 的 resources.arsc 为 1,096,628 字节，compact 后为 920,464 字节，与实际优化输出一致；差值 176,164 字节只证明资源表编码收益，不代表 APK 总体积或设备性能。该内部 API 耦合仍须随 AGP 升级复核，不能声称已消除升级风险。

成功构建的既有输出现在按文件 / 消息归并，保留首个定位与次数，汇总区的未来编译错误、缺失类及未来 JDK 拒绝行为标为 REVIEW；合理弃用不使构建失败，不重复执行 Gradle。该轻量观察基线不接受高风险豁免，也不是全量告警失败门禁。首次完整 Debug lint 有 1420 条 Warning / 31 类，主要是 UnusedResources 1084、UseKtx 112、DiscouragedApi 65、PrivateApi 41；不按这些命中批量删除资源、迁移语法或替换 ROM 接口。

验证采用 Deep：JDK 27 的 Full 通过，4216 项应用回归零失败 / 零跳过；后续 103 项关联验证中的文档筛选差异已修复并定向复验，13 项新增 / 故障用例通过，导入 / 导出精确 MIME、OPENABLE、取消与重建再次通过。743 项工具回归范围的两项失败修复后，73 项相关工具复验通过，另有 25 项架构 / R8 / 地图检查通过；确定性与 11/11 必需独立 mutation 通过。完整 Debug lint 更新为 1411 条 Warning / 27 类、零错误，四类无效属性 / 旧 SDK 分支 / 缺翻译 / 无效 detach-attach 命中已清零；不表示其他 lint 均已治理。本地 Develop 首次增量编译可见 159 条诊断、69 个文件 / 消息组，增量范围与 CI 的 537 条不同，不能据此推断总计消除了 378 条。最终未签名 Develop 的 R8 / fatal lint 通过，mapping、usage 与 configuration 产物齐全。官方 DexSQL 核对最终 APK：Hook API 定义、Manifest 设置入口直接 API 引用、JUnit / Robolectric 定义均为零；setId / getId 各一个 invoke 指令，同产物方法映射仍归属 HookInstallation，replaceHook 为零，三个既有固定 ID 均保留。R8 合并后的容器类名不能代替调用归属，也不能由指令数推断设备安装次数。当前没有可用设备，真实 HyperOS 1 的凭据解锁、Wi-Fi 扫描节流 / 权限恢复、输入法和外部页面动画保持待验。只确认移除周期请求和重复工作，不宣称设备 CPU / GPU、内存、帧率或功耗的数值提升。原始日志与产物仅本地留存。

### 收尾与同类治理

沿用治理提交 `50331952ede53d905f796f47e5fc2bfce382e9e7`，不回退到较旧主线。`r14.26.7 / 267` 按以下范围收口，发行继续使用 exact main CI 与既有双仓自动流程；公开状态以两端 Release 为准。

- **请求与可见性：** Wi-Fi 以单调时钟记录实际请求尝试，自动 / 手动最短间隔分别为 30 / 10 秒，拒绝与 12 秒等待超时不清除冷却。蓝牙移除 15 秒循环，按页面进入、受保护系统事件和手动操作读取宿主缓存，单次等待有限；跨页面只复用地址 / 名称结果，不持有设备、Context 或 View。天气、计步、媒体、动态岛、Dock 与状态栏既有可见性 / 来源租约、系统 tick 及有限交接保持。
- **Hook 所有权：** 横向定位八处回调内安装点。前台监控的两个方法移出实例构造；音频屏幕回调、闹钟匿名回调、控制中心 span lookup 和亮度监听器按实际目标类及方法分别复用既有安装状态。另将 SystemUI 基础初始化收归解析后的目标类，Application.attach 收归进程内唯一 boot 方法；功能目录仍复用 FeatureInstallState。控制中心依据 A14 分发器明确的左右适配器绑定每个新左侧实例，亮度回调使用当前实例的弱所有者。实机发现列数重排按 primitive boolean 查找 nullable Boolean 方法，导致安装遗漏、左面板移除后按钮不可见；按 ROM 的 boxed 签名修正，没有扩大方法匹配。
- **文件任务与失败：** 备份筛选、编码、打开、写入与 flush 移至 IO；与恢复共用单次操作入口。取消 UI 所有者后完成已开始的文件收尾并丢弃界面结果；编码失败仍关闭输出，恢复前置查询失败仍关闭输入。保留 Activity Result、MIME、SAF、原格式和恢复事务。
- **配置与返回：** 保留低频完整 View 重建，在 Fragment 状态已保存时延后，使用无状态丢失的同步事务。编辑器通过稳定键和保存状态恢复草稿，搜索索引在局部完成后一次发布，替代任务不再覆盖界面。文本和数值输入统一滚动完整表单，修正横屏键盘使固定标题 / 按钮占满可用高度的问题；不增加窗口监听器。权限成功后续接原列表 / 当前网络操作，添加当前网络的待处理意图可跨重建恢复；认证仅恢复明确待处理的系统请求。蓝牙 Parcelable 与应用隐私配置的容器及元素类型分别核验。
- **存储边界：** 只将本地存储提供方的树 ID 映射到实际存储卷，保留目录名中的冒号并拒绝越界 / 未知提供方。真实截图进程的 MediaProvider 调用确认仅接受 DCIM / Pictures 图片目录，故在准备阶段拒绝其他顶层目录并保留原生保存位置；不把 system UID 的更宽权限外推给截图宿主。使用卷内相对路径，交由 MediaStore 创建子目录；只更新宿主原本携带的直接路径，不新增 `_data`。格式名称和 MIME 一致，原生异常不重试。
- **验证与限度：** JDK 27 的全量应用范围执行 4,227 项；两个新增测试输入问题修正后定向通过，后续编辑器所有者 / 保存状态、控制中心重建及截图失败语义回归通过。工具范围 744 项的地图 / 翻译两项问题修复并复验，正式目标相关 87 项通过。最终 Develop、R8、fatal lint 与完整 Debug lint 通过，Debug lint 仍为零错误 / 1,411 条告警；成功摘要最多显示 80 组并明确省略数量，失败输出不截断。APK 不含 Hook API / JUnit / Robolectric / 临时验收程序定义，固定 ID 与原安装策略保留。

Android 14 / HyperOS 1 的实机请求计数已确认：四次手动刷新产生一次原生请求，四次快速重入不追加请求，可见等待 35 秒无周期请求，缓存窗口后的重入请求一次，离开后无续发。扫描结果由系统 UID 1000 发送，现有 `RECEIVER_NOT_EXPORTED` 可接收；非特权发送受保护动作被拒绝，故不扩大导出边界。蓝牙 40 秒内请求 / 响应各一次，等待结束，离开后接收器释放。前台监控 Host 回归中五次宿主构造仍为三个 Hook；三次分发器构造均只调整实际左侧；SystemUI 初始化 ID 的同进程实机回读由两次降为一次，五列按钮恢复。数值草稿跨语言 / 深浅色变化保留，横屏键盘下输入与取消可达，回到竖屏仍保留草稿；搜索返回后重新展开为空。上述过渡与终态有私有录屏核对。

系统文档选择器完成临时导出 / 导入，再次导出与原备份逐字节一致；损坏输入反馈失败，偏好条目零变化。实际截图完成 Pictures 子目录写入，扩展名与 MIME 均为 PNG；不支持的保存值回退原生 DCIM 后成功写入。目录选择完成有效树 URI 到卷路径的回传；无效目录返回和拒绝后重新授予的完整交互不再追加实机演练，相关数据边界与权限重建仍有 Host 回归。系统认证进入原生确认窗口，取消和 PIN 成功均退出包装页；服务异常消失、可移除卷及超大备份的帧级成本未实机展开。测试设置快照已恢复，正式发布附件已覆盖安装并核对摘要；测试程序、临时文件、原始录屏 / 日志、采集流及构建缓存已按授权清理，设备锁屏并停止 ADB 服务。保留汇总验证结论、原始发行恢复资产及既有独立工作区，不因清理重复构建或实机测试。上述次数、线程与文件结果不外推为设备 CPU、GPU、PSS、帧率或功耗提升。

## 项目历史与 Actions 隐私整理（2026-10-31）

经明确授权，开发过程提交合并到九个保留正式版的匿名源码快照，当前维护成果统一收口。每个快照的 app、Gradle 及构建输入与原对应版本逐项一致；署名与许可文件保留。旧引用、发布资料和必要日志仅私有备份，不放回公开仓库。

历史签名 APK 和原始 metadata 保持原样；四个现代发行以固定摘要和原 app 树绑定匿名快照，原构建标识与快照 SHA 分开说明。后续发布与下载文档直接复用现有流程，不增加新的签名通道。提交及自动文档更新统一使用公开 noreply 身份。

当前文件和快照已清理本机证据路径与个人设备操作细节，已知私有作者邮箱未出现在快照文件中。发行恢复证据已归档，旧 Actions 已撤下，新主线 exact CI 与 r14.26.7 双仓自动发布均通过，Issue #107 已结案；保留当前及可信基线的验证 / 签名运行，不按失败状态批量删除，不新增定时清理。GitHub PR 引用、缓存及外部克隆的清除仍需所有者配合官方支持，不能宣称全平台零残留。

## r14.26.5：文本运动、原生反馈与 Dock 收敛（2026-10-31）

本轮在 r14.26.3 有效实现上增量修改，累计发布说明仍从 r14.25.9 起算。r14.26.5 / 265 已完成双仓发行与同一生产实现的实机验收。r14.26.3 的两端旧页面、附件与标签已按授权撤下并读回 404，独立展示章节已合并，下载文档已同步；源码历史保留。正式 APK 未作最终安装复验，后续 ADB 测试已按明确要求结束。

- 恢复 Compose 原生边缘拉伸。问题范围是运动中字形 / 字距微调，统一使用官方 `TextMotion.Animated` 稳定运动时的字形度量，不增加硬件图层或自定义文字绘制；观感以实机录屏为准。
- 单选 / 多选采用 Material 3 `bodyLarge`（16sp）、主文字颜色与至少 48dp 触摸区域，跟随系统字体缩放；保留标题回退、取消和持久化语义。
- 页面暂停与菜单层级离场时解除原生 Ripple 节点，阻止已结束的按压状态随离场内容移动；页面恢复时使用主题原生反馈，无每行状态管理、计时器或常驻工作。
- 移除 Dock 轻量玻璃及专用渐变、绘制、模糊 / 省电监听，旧值 3 回退到面板。保留纯色面板 / 磨砂玻璃、API 102 Chain、祖先动画跟随及原生模糊透明度恢复。删除无生产调用的 G4 路径构造与退役测试，保留动态岛使用的曲率公式。
- 应用选择器空查询直接展示已准备数据，避免无意义的后台过滤派发；设置摘要每次失效只读取一次已有值。不将源码调用减少等同设备 CPU / GPU 或功耗收益。

相关 Host 共 125 项通过，覆盖选框文本、旧材质迁移、原生拉伸、页面 Ripple 生命周期、Dock 动画与动态岛几何；137 项关联发布工具、资源与 schema 回归通过。Develop / R8、fatal lint、历史证书、16 KiB 对齐和嵌入版本来源通过；最终 DEX 未打包 Hook API 或测试定义，设置入口无直接 Hook API 引用。

实机框架为 API 102。搜索展开 / 返回 / 再进入、菜单层级与取消、深浅色、200% 字号及手动输入通过；大字号输入操作底部距 IME 127px。录屏核对页面 / 菜单离场反馈与原生边缘拉伸，本次抽查未见未点击行变深或异常字距跳变，保留正常拉伸形变。纯色面板与磨砂玻璃均可启动桌面，每个初始化阶段只记录一次，未见缺目标、安装失败或偏好不可用；测试前后偏好完全一致。

同脚本暖滚动旧正式版 228 帧 / 1 帧卡顿，新版 Develop 259 帧 / 1 帧卡顿，两者 P95 均为 7ms；原生拉伸恢复后帧数不同，不能据此认定提速。单次冷启动 173→205ms、PSS 82,255→83,856 KiB，条件未控制到可做性能归因，不宣称启动或内存降低。源码确定减少空查询派发及已删除材质的监听 / 绘制；设备 CPU / GPU / 功耗未测。未新增运行依赖。归档构建记录 与归档构建记录均一次通过，734 项工具回归通过。正式源码 `2037b6da9195fbf406113ded89698a3812be81d2`，APK 5,979,909 字节、4,783 个 DEX 类，SHA-256 `37d0a445199083f4ff56ab3562c0200c5ba4db03dda3d2abbef90b5dc05581ca`；比 r14.26.3 少 340 字节，体积基本持平。双端 Latest、标签来源、Notes 与两附件摘要 / 大小一致，下载文档读回一致。正式包历史证书、16 KiB 对齐、SDK / ABI、来源及最终 DEX 边界已独立验收。

正式包最终安装前设备连接不可用；后续 ADB 测试已按明确要求结束，不再作为待办。设备最后确认保留已验收的 Develop，设置已恢复并锁屏；不能描述成正式 APK 已安装，也未确认本轮手机临时文件清理。旧 r14.26.3 下架与文档同步已完成。原始证据仅私有留存。

参考：[官方文字运动](https://developer.android.com/develop/ui/compose/animation/quick-guide)、[Telegram 原生导航交接](https://github.com/DrKLO/Telegram/blob/master/TMessagesProj/src/main/java/org/telegram/ui/ActionBar/ActionBarLayout.java)。只借鉴输入与离场反馈边界，不引入第三方导航框架。

## 原生能力与界面一致性续审（2026-10-31）

本节记录当时的 Compose 收敛续审，现由上节 r14.26.5 接续。当时正式版为 **r14.26.3 / 263**，累计说明基线仍为 r14.25.9。新版已完成双仓与实机验收；被替代的 r14.26.1 页面、附件与标签已按授权从两端删除，独立展示章节已合并，源码历史及更早正式版保留。

设置页恢复原有中性背景与文字色，原生按钮、开关和滑块使用 Material 3 动态取色。状态栏与导航栏在创建 / 配置变化时同步页面背景，不增加监听或持续工作。菜单沿用全局主题与字号，重启子菜单采用短淡入淡出与尺寸过渡；各层、各条目使用独立组合身份，并禁用正在退出的条目，避免按压反馈与操作残留。当时关闭平台边缘拉伸；该取舍已由 r14.26.5 撤销，当前保留原生拉伸并处理文字运动度量。

首页搜索展开时独占页面，隐藏主页内容与旧顶栏；输入节点首次布局后再请求焦点，使用真实 IME 可见状态释放焦点，避免高度变化误判。设备进一步发现旧主窗口默认选择 adjustPan 且根布局消费边距，使搜索收不到 IME 状态；主窗口显式使用 adjustResize，并移除多余的根布局消费，由现有系统窗口处理外框、Compose 接收输入边距，不增加键盘观察器。结果标题与路径按查询词高亮，结果与高亮仅随输入 / 索引或主题变化计算。选择弹窗标题缺省时统一回退到设置项标题；数值输入首次全选，输入弹窗在 IME 与安全区之外增加留白。系统目录为“应用与自动化”补齐“应用”分组，保留全部设置键与导航归属。

设备框架原生状态接口已确认实际 API 为 102。对照官方 [Chain](https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.Chain.html)、[HookHandle](https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.HookHandle.html)、[Invoker](https://libxposed.github.io/api/io/github/libxposed/api/XposedInterface.Invoker.html) 与 [Service](https://github.com/libxposed/service) 合同，仅将 Dock 的 measure / layout 两个成功后装饰回调改为直接 Chain：原方法只执行一次，原结果、原异常与致命异常边界保留，省去通用 before / after 包装和参数数组副本。受控 Host 回归验证原生调用次数与失败语义；尚无设备分配、CPU / GPU、帧时间或功耗对照，不宣称数值性能收益。

已有高频直接 Chain、冷路径缓存 Invoker、固定 Hook ID 与组回滚、原生 Service / RemotePreferences 保持。通用回调仍服务于参数修改、提前返回、异常恢复与优先级；反射匹配、偏好镜像确认与限定调用方的缓存桥仍有实际消费者和安全责任。普通配置不重新挂载 Hook，不引入 replaceHook、全局 Hot Reload、detach、轮询或常驻线程：当前没有更换实现或结束生命周期回调的需求，Invoker.ORIGIN 也不能等价替换需要保留 Hook 链语义的普通反射调用。

本轮相关 Host 回归、154 项生成与发布工具回归通过；窗口边距调整后又完成完整设置导航套件和边距所有权回归，无跳过。主窗口采用官方混合布局的边距传递：外框只预留系统栏 / 开孔空间，两个 ComposeView 保留原始边距向下分发，页面统一处理 IME，避免同级工具栏截走输入状态。JDK 27 的 Develop R8 / fatal lint、历史证书、16 KiB 对齐与内嵌来源通过。最终试用源码为 `81706a74af6153f5514102546faf98cdd93bebd4`，APK 5,980,294 字节、4,783 个 DEX 类，SHA-256 为 `993d09d9fe7a8d2a3b41b6108ad1fac6989c0147a518c0c05cc9d01b2ba796f3`。未新增依赖；官方 DexSQL 确认 Hook API 定义、设置入口直接 API 引用和测试框架定义均为空，setId / getId 指令仍归属原安装器，replaceHook 为零。产物指令数不代表安装次数。

API 102 实机完成 12 轮搜索展开 / 返回，其中三轮包含后台与恢复；IME 返回释放焦点，第二次返回收起搜索，结果导航返回不残留输入。菜单展开、返回与重开录屏未见错误按压反馈，宿主未被误重启；中性深浅色、系统栏、200% 字号、弹窗标题与目录分组通过。数值自动聚焦、全选替换和取消通过，大字号下操作按钮底部到键盘顶部为 127 px。到列表底部继续上滑的录屏中文字保持形状与位置。桌面重新加载试用代码后完成三次解锁，录屏中的 Dock 恢复正常，两个安装阶段各输出一次汇总，Develop 成功诊断项为 54，目标缺失 / 安装失败 / 偏好不可用均为零；未变更的系统界面和系统服务进程保持原 PID。这些汇总不是两个布局回调的设备调用计数。

独立于录屏的一次暖机列表样本为 227 帧、1 帧 jank（0.44%），帧耗时中位与 P99 均为 7 ms，PSS 约 78.4 MiB；单次启动 182 ms。此前样本为 258 帧、零 jank，输入 / 帧数不同且没有重复对照，不据此宣称速度、内存、CPU / GPU 或功耗改善。可以确认的变化是边缘拉伸移除和两个热回调绕过包装；长时重组次数、持续成本与无障碍专项保持未测。模块偏好快照一致，字号、主题与息屏时间恢复，VPN 保持连接，临时手机文件和录制进程已清理并锁屏。原始材料仅私有留存。

完整 归档构建记录 已通过，包括 734 项工具测试、全量应用回归、源码门禁、lint 和 Develop 压缩构建。此前应用测试发现选择器窗口夹具与后台加载结果竞争：直接替换显示状态后，原加载结果可能覆盖样本。夹具改为提供固定应用输入，推进暂停的主线程并有界等待实际渲染，保留两种窗口高度、滚动到远端行、搜索栏固定位置与有效列表高度的全部断言；47 项设置导航回归和后续完整 CI 通过。生产加载、设置页与 Hook 实现未变，复用先前实机矩阵。

正式版由归档构建记录从 `91827e3e758540d104dad99ed6d989abb451d196` 构建一次，Release / R8、fatal lint、平台身份、历史证书、16 KiB 对齐及来源通过。[源码发行](https://github.com/thetvplus/customiuizer-a14/releases/tag/r14.26.3)和[下载发行](https://github.com/Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14/releases/tag/263-r14.26.3)均为稳定 Latest，标题、说明、标签来源及两份附件大小 / 摘要读回一致。正式 APK 5,980,249 字节、4,783 个 DEX 类，SHA-256 为 `e135a9c13e8708ce391f399158712ea6c7b61fd7b9b9271c5943bc16eb5f4f2b`；比此前 r14.26.1 正式版增加 98,785 字节（约 96.5 KiB），未新增依赖。最终 DEX 的 Hook API / 测试定义、设置入口直接 API 引用与 replaceHook 均为零；未取得远端 Release mapping，不由混淆容器名推断安装归属。

设备已覆盖安装同一正式附件并读回相同摘要，搜索、关键词高亮、返回、菜单和三次解锁再次通过录屏复验。桌面日志确认正式源码标记，`application.attach` ID 回读 confirmed 一次，两阶段汇总各一次、缺失 / 失败 / 偏好不可用为零；Release 紧凑成功事件为 58。Develop 按诊断键去重，Release 只维护成功事件计数，两者口径不同，不能用 54 与 58 推断实际 Hook 数量变化。两个布局 Hook 的单次安装 / 原生调用仍由行为回归约束，设备回调频率与分配未测。偏好不变、系统设置恢复、VPN 连接及手机清理锁屏再次确认；双仓旧版页面与标签删除后均读回 404，下载文档由既有生成器同步，正式源码与历史提交保留。

随后按授权深度清理测试环境：先校验归档，再移除手机端 73 份历史采集与 DEX / VDEX 测试文件（约 63.5 MiB），功能所需的 VPN 辅助程序保留。停止已确认闲置的本轮 Gradle / Kotlin 编译进程，清除本地生成输出、项目缓存、测试副本和临时媒体依赖，共 26,669 份文件、约 1.10 GiB；工具链、依赖下载缓存、签名材料与正式 APK 保留。必要证据及同产物 mapping 已压缩归档到仓库外，忽略目录保留本地归档索引。清理后正式安装包摘要与偏好再次确认一致，无测试包、采集进程、ADB forward / reverse 或调试覆盖残留；VPN 正常，手机锁屏。生产源码与已发布 APK 未变，不重新构建或重复 CI。

## Compose 原生组件与搜索收敛（2026-10-31）

本节记录较早的试用阶段，当前实现与正式验收以上节为准。

首页搜索恢复为工具栏按钮按需展开，退出、结果跳转和分类导航后收起；IME 收回、页面停止和销毁时释放输入焦点。状态恢复只保存展开状态与查询文字，首帧后清掉框架恢复的旧焦点；不以延时轮询维持状态。首页与应用选择器共用 Material 3 输入、搜索 IME 动作和清空按钮，保留选择器的固定搜索位置与增量图标加载。

设置主题改用系统 Material 3 动态色，保留原生深浅色配对与容器层级；顶栏使用官方 TopAppBar。设置值移到标题 / 说明下方，取消右侧固定宽度截断，分组标题共用既有组件，导航箭头使用可随 RTL 镜像的向量。三点菜单恢复分级重启入口，沿用旧 XML 的 10 dp 圆角、8 dp 阴影、16 sp 正文和中性背景；不重新引入 XML 菜单或第三方 UI 库。

已核对迁移边界：无应用自有布局 / 偏好 XML，AndroidX Preference 仍有持久化与依赖通知消费者，AppCompat / Palette / 反射依赖仍有生产用途。移除四份无引用搜索行背景、重复查询副本、失效焦点字段及旧搜索状态机；旧状态机测试随生产对象移除，由实际 Compose 输入、返回、导航、后台和重建回归覆盖。平台配置、宿主 Hook 资源和 View / Canvas 保留。

实机进一步发现数值弹窗的操作按钮被键盘覆盖。输入型 Material 3 弹窗显式采用 WindowInsets，处理系统安全区与 IME 留白，数值 / 文本长内容可滚动；自动聚焦归属弹窗自身组合并在首帧布局后执行。未改变解析、校验、提交或取消持久化语义。独立的主机键盘弹窗实验在未改动原实现上也进入虚拟窗口布局循环，未计作通过或设备证据；原始对照仅私有留存，真实键盘几何由设备验收。

本轮按 Standard 验证，复用未受影响结果并补验新增焦点与编辑路径：七套件共 76 个用例均已有通过结果，无跳过；文档、生成地图和版本一致性检查通过。最终源码为 `e571df9924374cc4125b73c391179a0d3196cbd8`，JDK 27 的 Develop R8 / fatal lint、内嵌来源、历史证书及 16 KiB 对齐通过。最终本地 Develop APK 为 5,914,725 字节、4,709 个 DEX 类，SHA-256 为 `e1d4c2dadd85027c8baf85d0fa65b589ed77e10814f9c3f7aa8cdf93a772914a`；设备安装包已按同一摘要读回。未新增依赖，压缩 mapping 无 JUnit、Robolectric 或 Compose UI 测试框架。此前正式 Release 为 5,881,464 字节；变体不同，不把差额作为同变体体积收益。

Android 14 实机完成搜索展开 / 收起、IME 返回失焦、结果导航返回、菜单分级与取消、深浅色、200% 字号、设置值排版、数值编辑聚焦与取消验收；最终候选的页面过渡、菜单和键盘过程均有私有录屏，已检查过渡中间帧和终态。输入弹窗按钮在大字号和键盘同时出现时可见且可点击。测试前后模块偏好快照一致，系统字号、主题与息屏时间恢复，VPN 保持连接；临时采集与手机文件已清理并锁屏。手机保留经验证的本地 Develop，仅为试用构建，未合并 main 或正式发行。

独立于录屏的单次暖机设置列表滚动样本为 258 帧，系统 jank 计数 0；内存单次 PSS 约 77.7 MiB。没有同条件改前基线，不据此宣称响应、分配、CPU / GPU、功耗或持续运行改善。减少闲置输入节点和重复状态是可确认的工作量变化；长时性能、重组次数与 TalkBack / 物理键盘专项仍未测量。

## 设置界面与工具链迁移（2026-10-31）

本节记录已由 r14.26.3 替代的 **r14.26.1 / 261** 迁移验收，累计说明继续从已公开的 r14.25.9 起算。未公开的 r14.25.11 候选不晋升、不创建历史发行。完整设置界面 Compose 迁移、布局一致性评估与 Dock 解锁材质修复已完成当时必要验收；有效变化已累计到上节的当前正式版。

构建使用 JDK 27，稳定版 Kotlin 2.4.21 的最高字节码目标为 26；经确认采用 Java / Kotlin 26，不引入测试版编译器。Gradle 9.8.2、AGP 9.5.0、Palette 1.1.0 已纳入本轮升级；compileSdk 已为 37，minSdk / targetSdk 保持 34。升级不扩展运行支持范围，也不代表性能收益。

迁移保留设置键、默认值、依赖关系、权限与保存语义，统一搜索、选择器、编辑页和顶层导航；原生 Hook 内的 View / Canvas 不移入设置端 Compose 生命周期。页面按功能归属分组，主开关、适用范围与附加参数就近展示，隐藏或禁用条件须有一致语义。应用自有布局、菜单与 39 份偏好 XML 已移除，以 settings JSON 生成模型并由 Compose 绘制。相关页面 / 编辑 / 依赖绑定回归已迁移；4,211 项 Host 测试、734 项工具测试均通过且无跳过，最终 Full 门禁与 Debug / Develop 致命 lint 通过。源码隔离、两项确定性输出和 11 项必需独立 mutation 全部通过；没有自检冒充独立检出或清理失败。实机完成首页、自动化入口、搜索路由、数值输入聚焦 / 取消、横屏与大字号、深色模式及波形停用 / 恢复 / 释放验收。正式版由 exact main CI 后的正式签名流程生成，双仓资产及下载文档均已读回核验。

更早的公开正式实现同样可复现 Dock 解锁后模糊丢失。A14 原生 `BackgroundBlurDrawable.setAlpha` 写入私有透明度，却继承恒返 255 的 `Drawable.getAlpha`；隐藏时写零后，旧实现据错误读回值跳过恢复 255。现按材质 Drawable 身份记录本 owner 的最后写入，隐藏 / 重现仍沿用原生窗口事件，detach 清引用；无新增捕获、线程或轮询。回归在旧算法失败，修复后的 Dock 三套件通过；开发源码集隔离诊断及三次解锁录屏确认原生透明度恢复，固定无图标区域的亮度边缘指标与新启动状态接近。诊断不进入正式构建；完整 Compose 压缩候选又完成三轮解锁后录屏复验，收敛后的无图标区域与启动状态接近。所有临时模糊策略均恢复并重新加载；不据此宣称设备帧时间、CPU / GPU 或功耗收益。

JDK 27 / 字节码 26 下，Dock 与动态岛控制器 208 项组合回归连续两次通过，未删除或跳过断言。控制器测试只为应用自身波形调度提供有界虚拟帧适配；原生 Choreographer / 窗口初始化与独立波形 vsync 回归保持原路径。编译、Host 回归不代替 Dock 修复后的设备验收。

压缩候选的实际边界检查：应用自有布局 XML 与偏好 XML 均为 0；39 份 JSON 定义只在构建时生成模型。Develop APK 为 5,881,509 字节、4,661 个 DEX 类，迁移前本地 Release 候选为 4,453,285 字节、2,839 类；变体不同，不据此宣称体积或运行性能改善。最终正式 Release 为 5,881,464 字节、4,661 个 DEX 类；本地构建、实机安装与两仓附件 SHA-256 均为 `603c5764d314be4c33c0d29b79e8adbd8f3318607d593aaf03c8752d8a1b3670`。Compose 增加设置端运行库，旧适配器与布局绑定则移除；宿主源码仍不引用 Compose。Release runtime 依赖图无 JUnit、Robolectric、Compose UI 测试或协程测试依赖，压缩 mapping 无测试框架 / Dock 诊断入口；最终 DEX 的 compileOnly Hook API 定义与设置入口直接 API 引用均为空。

波形录屏仅证明可见工作量与状态：轻量模式约 121 次柱形变化 / 秒，实时模式约 107 次 / 秒；系统动画关闭后停止，开启后恢复；测试退出时 Visualizer 数量由 1 回到 0。编码帧率、可见柱形变化、Host 调用计数均不替代设备 CPU / GPU、实际帧时间、内存或功耗对照；这些持续成本仍未完成全面测量。证据仅保留仓库外或忽略目录，不公开设备、应用列表、网络身份、操作细节或凭据。

正式构建源码为 `a92310cc20503b2f0ec0663f503ce4af62bc1e22`。已完整快进合并已验证的三条工作分支，整合树一致，93 个已删除路径无物理残留；本地及远端仅保留 main。当前任务的独立诊断 Worktree、Frida / Node 临时环境、测试应用、采集与转发均已清理；临时系统配置恢复，保留正常服务并锁屏。四个既有 detached 工作区属于原有开发资产，本轮未覆盖或删除；证据保留在仓库外。


## 波形流畅度与新增功能入口反馈（2026-10-30）

有效波形与入口调整累计纳入 r14.26.1，不发行旧候选。Dock 问题与最新验收状态以上一节为准；正式发行状态以双仓实际资产读回为准。

波形显示使用 View.postOnAnimation，音乐采样请求恢复为最多 30 Hz、录音间隔恢复为 67 ms，继续遵守 ROM 上限、可见性和来源租约。轻量波形仅在可见播放期间接收原生动画缩放通知，隐藏 / 暂停 / 切实测 / detach 即释放；实测稳定或静音后停帧。原生动画重新开启后的恢复已通过回归与录屏。受控录屏中的柱高变化频率约由 17.56 提高到 107.00 次 / 秒；这是呈现变化统计，不代表稳定帧率、CPU / GPU 耗时或功耗收益。

应用行为、Wi-Fi 与 VPN 自动规则集中在系统页首个惰性目录“应用与自动化”，三个控制器和稳定键保留，旧位置去重，搜索、备份及生成地图同步。应用行为与 Wi-Fi 自动设置的 13 个固定键通过既有 non-exported、仅 system UID 的 Provider 读取已确认镜像，解决部分框架缓存旧值的问题；单键类型读取、批量原子快照、删除默认及失败保留原快照，不增加轮询。

相关 Host 回归和工具检查通过；补充缓存桥 7 套件 / 59 项通过。设备证据覆盖波形、设置入口、应用行为规则、Wi-Fi 所有权及 Dock 编辑 / 拖动返回，但此前未充分覆盖解锁材质恢复，不能标作全场景通过。完整 CI 中还发现间接构造波形的生命周期测试使用自动推进时钟，正在修正测试夹具并补验。受控帧时间、CPU / GPU、内存 / 功耗和长期稳定性仍无完整对照。

公开记录仅保留抽象场景和汇总证据，不披露个人设备、应用、网络、账户、路径或实际操作记录。原始设备材料不入库；38 条内部 Issues 已关闭并删除且逐条读回确认不可访问，第三方反馈未删除。已完成的实施不保留开放任务壳，已替代路径不复活；未决 Dock 回归和缺少设备对照的项目继续在本页维护，不把隐私删除解释为问题已解决。

## r14.25.11 本地正式 APK（2026-10-30）

用户在第二轮性能实施完成后授权本地生成最新正式 APK 并升版至 r14.25.11。承接 `codex/performance-first-round2` 已推送实施 `d69b854f6f3b363c2be1fda4d1fd9eefb6a2b346`；刷新远端后确认 main / 整合分支没有新增遗漏成果。两仓当前公开正式版均为 r14.25.9 / 258、来源 `f18a112cac62f1db63a0bbda3958681f33df483b`，无更高预发行或草稿。

源码目标调整为 **r14.25.11 / 260**，说明从 r14.25.9 累计，双语 README / Changelog、Gradle 与 release-policy 同步；未改生产算法、依赖、SDK 或签名实现。只在任务分支准备与本地构建，不合并 main、不打 tag、不启动 Actions 正式签名或双仓发行。复用已通过的 149 项受影响 Host 回归与 35 项工具检查，补版本 / 双语记录、Release / R8、fatal lint 及最终 APK 来源 / 身份 / 签名检查。

本地正式签名已完成：使用用户提供的仓库外 keystore.properties，签名前导出证书核对历史指纹 `c0eff2dc4e662717195490da78b12a984c6f2e6bd38acf4edad14d53e3d22e70`，最终 APK 的 V2 签名与同一证书再次核验通过。密钥、密码和本地签名配置始终留在仓库外；未新增签名实现或证书，未把先前未签名产物作为正式交付。

升版实施来源为 `fa6914ba5d4fe03550e037f71b65c868236185be`，最终签名 APK 的内嵌来源为 `19a6d858f00ea25048085b1a296b50f8c82c8311`，二者只差构建证据文档。本地 JDK 25 / 既有 SDK 工具下正式 assembleRelease、lintVitalRelease 与 R8 通过；版本检查、文档 / 地图、CI 合同扫描通过；版本一致性 10 项、准备工具 9 项、签名 / 发布合同 87 项回归通过。正式目标测试同步到 11 / 260，保留历史累计说明基线与旧候选拒绝断言，没有放宽签名或发布边界。应用行为未改，复用前述 Host 结果。

交付文件为 CustoMIUIzer-A14-r14.25.11.apk，4,468,521 字节、2,837 个最终 DEX 类，SHA-256 `b59e8fb2823a6e90e4ea6cab549a78ab5dbe4e0282c3116c513387f281bf3e8a`。包身份、r14.25.11 / 260、min / target 34、仅 arm64-v8a、不可调试、16 KB zipalign、V2 签名、历史证书与内嵌完整 sourceCommit 均核验通过；ZIP 完整性及构建原件 / 交付副本摘要一致。官方 DexSQL 的 Hook API 定义、设置入口直接 API 引用、测试框架 / testsupport 定义查询均为空；直接引用结果不替代传递类加载证据。原始日志在忽略目录 `.di-evidence/local-r14.25.11/`，APK、摘要、来源 JSON 与同产物 R8 输出保存在仓库外 `customiuizer-a14-evidence/local-r14.25.11-19a6d858/`。这只完成本地正式签名交付，双仓公开版仍为 r14.25.9，设备功能、观感及性能验收仍待完成。

## Performance-First 第二轮：算法减负与恢复正确性（2026-10-30）

相关工程记录（以现行源码与回归为准）。从已推送且工作树干净的 `codex/a14-consolidated` exact SHA `67fc8a5c11a73d9ad78c9e22aa3b824f533bc51d` 创建 `codex/performance-first-round2`；基线已领先 main `863305ebd56351687b2e0c54313b7247e7020373` 30 个提交，并非此前审查时的 28。承接已整合成果，不合并未经核验分支、不改正式版本号、不发布或合并 main。按用户限定范围只做受影响回归、源码门禁与最小 Debug 构建，不启动完整 CI 或 Release / R8 重建。

### 当前决定与实现

| 范围 | 决定与实际实现 | 保留的合同与边界 |
| --- | --- | --- |
| Dock 装饰 | 直接删除 DockGlassBackdrop、DockWallpaperCapture、DockGlassOptics；LauncherDockBackground 只复用已有 BackdropBlurRelativeLayout，失败使用简单半透明面板与缓存渐变。删除捕获调度、动态壁纸追踪、纹理 / Shader / generation / 有限重试整条路径，不另建静态采样系统 | 原材质值 3 与设置键不迁移，十种语言改为轻量玻璃及真实回退说明。保留 DockCornerGeometry、图标边界、编辑 / 拖动 / 解锁原生动画及透明度跟随；原生材质失败与 detach 释放模糊 / 省电监听，失败后不逐帧重装。外观不再精确折射壁纸，这是本轮允许的装饰精度取舍 |
| Wi-Fi 状态租约 | WifiSettingLease 恢复必须得到目标读回才清所有权；失败或暂时未知读数保留每属性一组 original / last。场景直接切换失败保留此前确认的原值；DND 只有确认写成功才认领原生铃声副作用 | 已观察到手动覆盖立即撤销恢复权，未知状态不发写命令；用户切换强制丢弃旧用户所有权。普通重复网络事件不触发重复恢复，无无限重试或轮询 |
| Wi-Fi 关闭 / 恢复机会 | Coordinator 与 Runtime 关闭后仅在仍有待恢复属性时保留一个停止的运行对象；解除网络回调和属性观察，只复用既有生命周期事件 / 显式设置刷新尝试恢复。重新启用沿用同一原值，注册失败清除已注册资源 | 不新增线程、监听器或定时恢复任务；没有待恢复状态就释放对象与生命周期接收器。权限撤销暂停恢复，后续明确机会重新读回，不跨用户恢复 |
| 网络 overflow | WifiNetworkTracker.beginSnapshot 清掉旧记录与 overflow，但保持未知；只有原生完整枚举结束且不再溢出才确认 snapshotReady。Runtime 重建前同时清 raw 去重表，不沿用不完整旧集合，删除失去生产调用方的 networks 列表转换 | 最大 16 条；丢失事件不能自行解除隔离，身份被隐去 / 缺能力 / 仍超限继续未知。只由现有网络事件或显式身份刷新触发完整快照，不轮询、不猜身份 |
| 应用窗口行为 | AppWindowBehaviorHooks 先判断开关，再准备共用必要接口；旋转与亮度 / 拦截各自准备和安装。普通 ROM API 缺失仅影响本组，致命异常仍回滚已装 Hook 并抛出；同一个 WM 的偏好变化只请求一次 traversal | 不新增通用反射框架；原生 proceed 一次、brightness finally 恢复、锁 / 用户 / 显示与多窗口限定保留。共用反射元数据只准备一次，不用两套全量接口换隔离 |
| 动态岛遮挡 | IslandStatusBarCompact 生产算法不改。已有唯一 Geometry 节点和脏标记能跳过未变化的完整遮挡查询；补实测调用计数回归固定共享祖先去重 | RenderNode 变换与祖先滚动 / 裁切可在无 layout 时变化，因此保留每帧必要的轻量几何核验，不增加监听器或缓存系统 |

删除五个仅验证已退役捕获 / 折射算法的测试类和孤立的 RuntimeShader 测试 shadow；保留并调整原生材质、失败回退、轮廓、编辑 / 解锁、全宽、Hook 安装及生命周期行为回归。测试源码仍在 src/test，未新增生产测试接口、依赖、模块、Manifest 或 Gradle 配置。架构合同、Dock 文档与原生界面技能入口已同步，后续不得恢复旧采样路径来补装饰精度。

### 可证明的工作量变化

| 项目 | 基线源码 / Host 计数 | 本轮结果 |
| --- | --- | --- |
| 自定义 Dock 动态壁纸捕获 | 可见且需要刷新时最短间隔 67 ms，理论最多约 14.93 次 / 秒；静态壁纸原本已有稳定停采及约 200 ms 收敛 | 模块自定义捕获、采样定时任务与请求均为 0；动画期间和稳定后同样为 0 |
| 模块壁纸图像资源 | 单张最多 196,608 像素、任一边不超过 768；一张保留纹理、最多一个在途捕获及替换时 BitmapShader 输入 | 上述自定义 Bitmap 分配、保留纹理、Shader 输入更新与异步交接为 0；无旧纹理释放路径需要继续维护。不表示 Launcher 原生模糊没有 GPU / 图形内存成本 |
| 窗口接口准备 | WindowAccess 总是解析 4 类 / 8 字段 / 6 方法 | 仅旋转 2 / 4 / 5，仅亮度 4 / 7 / 4；同时启用仍为 4 / 8 / 6。此为直接反射目标数量，均不含共用 BackgroundThread 获取，不是设备耗时 |
| 动态岛静止帧 | 既有实现 120 次稳定 PreDraw 不重做完整遮挡查询 | 继续为 0 次完整查询；12 个叶节点共用祖先在 120 帧只读该祖先矩阵 120 次，不是 1,440 次。生产代码未改，不重复宣称优化收益 |
| 恢复失败后的重复网络通知 | 失败即丢恢复权，无法正确恢复 | 测试中重复相同退出通知 100 次不增加写入，只有明确状态机会重新核验；手动覆盖或用户切换终止待恢复权 |

这里的零捕获 / 零自定义纹理来自整条生产路径删除与源码门禁，不是设备采样数据。尚无帧时间、GPU、内存或功耗对照，不以删行数、Host 耗时、APK 体积代替运行时性能证据。

### 验证与剩余范围

原始日志 / XML 在本地忽略目录 `.di-evidence/performance-round2/`。JDK 25、SDK 34 下分批验证以下 16 个受影响套件；各次 XML 统计按套件去重，共 149 项，0 失败 / 错误 / 跳过：

- verify.py fast：WifiSettingLeaseTest、WifiAutomationCoordinatorTest、WifiNetworkTrackerTest、AppWindowAccessIsolationTest、AppBehaviorPolicyTest、DockBackgroundTest、DockFullWidthTest、DockLiquidGlassTest、DockHookInstallationTest、DockMaterialContourTest、DockNativeAnimationTest、SurfaceCostBudgetTest、IslandStatusBarCompactTest。
- 补充 verify.py fast：WifiAutomaticSettingsTest、DockBackgroundGeometryTest、DockBackgroundFeatureTest、IslandStatusBarCompactTest；另补 AppWindowAccessIsolationTest / AppBehaviorPolicyTest，真实安装流程同时启用两组、故意缺少另一组 ROM API，仍只安装有效 Hook，并保留一个偏好观察器。
- 偏好资源、生成产物、当前架构三组 Python 回归 35 项通过；源码 / 偏好 / 热路径 / 语义静态门禁、文档链接与功能地图检查通过。assembleDebug 通过；十种语言标签与生成资源编译有效。没有复用旧采样单元测试冒充当前材质测试。

仍需 HyperOS 1 / A14 定向实测：轻量玻璃的 Google / MIUI 原生材质与无模糊回退外观，Dock 编辑 / 拖动 / 解锁 / detach 恢复，锁屏与动态壁纸切换；Wi-Fi 系统写入失败、权限撤销 / 恢复、用户切换的实际原生事件；应用窗口两组功能各自缺接口的设备行为。按同一交互脚本对比实际帧时间、CPU / GPU、Binder、内存与功耗，不能把源码捕获次数推断为设备总开销比例。

当前没有证据要求再给动态岛增加状态层，或为 Dock 新建图像系统。回退应按 Dock 材质路线、Wi-Fi 恢复合同、overflow 重建、应用接口隔离各自的代码与回归成组处理；不要单独恢复捕获调度器。下文较早的 Dock 捕获、AGSL 与 uniform 数值记录为历史证据，其算法和待优化项已由本节退役决定取代，不再是当前生产合同。


## 工程精简与生产隔离（2026-10-30）

相关工程记录（以现行源码与回归为准），当前分支 `codex/a14-consolidated`（承接已清理的 `codex/repository-simplification`）；有效基线 `fbf25c247137edd0cc75f19fdb86f37860a6d918`，不是较旧的 main。首次工作树干净、基线已推送，保留 Wi-Fi、应用行为及此前八类成本审计成果。本轮 Deep，不更改正式版本号，不签名、发布或合并 main。宿主原始证据保存在忽略目录 `.di-evidence/repository-simplification/`，APK / mapping / 外部源码 / DexSQL 缓存不入库。

### 分支收拢与剩余范围

按用户后续授权，把所有有效分支成果收拢到 `codex/a14-consolidated`，保留 main 稳定线。收拢前 31 个本地分支和 26 个实际远端分支引用的 exact SHA 全部为 `77b9d3c80722a72222a733759a35f325f5bc3eca` 的祖先，因此无需重复 merge、冲突处理或重新引入已替代实现。先推送新分支并独立读回，再原子删除 25 个远端任务分支；30 个旧本地分支均经祖先复核后用普通安全删除清理，无 force push、历史改写或 main 合并。

四个仍存在的旧 Worktree 均无未提交 / 未跟踪修改，已脱离旧分支并固定在各自原 SHA，文件与本地忽略资产保留；两个路径已不存在的过期注册，在确认其 HEAD 也被新分支包含后清理。删除前保存完整 Git bundle 与引用 / Worktree SHA 清单到 `.di-evidence/repository-simplification/branch-consolidation/`。该目录仅为本地可恢复证据，不作为新任务数据库。当前仅保留 main 与整合分支，tags 不变。

分支收拢时只变更文档与 Git 引用，并复用首批 Deep / R8 证据；随后按用户“静态能处理的处理一下”继续实施。下表为静态续处理后的当前状态，不等同于全仓每项复杂度均已穷尽审查。

| 优先度 / 未完成项 | 具体证据与下一步 |
| --- | --- |
| 已处理 · 电池源码合同与共用测试入口 | 修复 BatteryArchitectureCTest 的 ClassLoader URL 定位失败，缺源码改为失败；检查实际方法调用而非注释中的同名文字，并要求冻结 Field.get。21 个现有测试类与电池测试共用 src/test 的 testsupport.repositorySource，新增缺源码失败回归；不把测试入口放入 main |
| 已处理 · 94 个未使用资源 | 逐项核对 main / 生成资源 / XML、动态资源获取与原 Release resources.txt；全部无消费者且收缩报告明确不可达。移除 93 个字符串、1 个尺寸及各语言共 931 条定义，覆盖 11 个文件；UnusedResources 94 → 0，lint warnings 478 → 384，没有删除真实功能资产 |
| 已处理 · 五个 ROM inventory 样本跳过 | 不再绑定 tmp-apk 中的 r14.13.0 历史安装包；测试用已有 JDK 25 / D8 / AAPT2 临时编译一个类、两个方法、一个字段的 APK，实际检查 DEX、包信息、SDK、去重、JSON / CSV 和缺工具回退；JAR / compileOnly stub 使用仓库已有构建输入，缺失不再跳过。样本不入库 |
| 已处理 · 重复 / 无效的清单基础设施 | rom_inventory 复用 rom_catalog.manifest_attributes 读取 Manifest 根与 uses-sdk，保留现有 APK / JAR / DEX ABI 元数据职责；移除可选 pyaxmlparser 依赖、无人调用的 JADX 方法，以及同时传 -public / -p、没有类名而必定失败的 javap 调用。JAR 直接 class 解析保留；测试约束不启动该无效外部进程 |
| 已处理 · 仅剩测试使用的旧配置查询 | hasConfiguredGlobalActions 没有生产调用方，原 R8 usage 已移除该方法。删除此 main API，既有缓存就绪 / 并发 / bootstrap 测试改为调用真实生产 hasConfiguredActionCode，Robolectric 默认空配置仍逐动作 / toggle 验证；不删除有效测试 |
| 已复核保留 · 生产边界与工具职责 | XposedServiceManager 是设置进程的连接 / 偏好发布 owner，ShakeManager 跟随 Launcher resume / pause 注册释放，不能按 Manager 名字合并；Helpers / HookUtils 隔离设置初始化与宿主叶子能力，Feature Spec / Definition / Registry 分离惰性创建与安装状态。APK 单产物归因与跨变体报告的输入 / 来源职责不同；签名脚本、双语发行文档及实际 Open With 资产继续保留。未发现已跟踪 APK / DEX / trace / log / 临时缓存可删 |
| 待设备 · 生命周期与性能验收 | 未进行本轮目标 A14 / HyperOS 1 实机对照、启动 / 交互延迟、CPU / GPU、分配 / PSS、帧时间及功耗测量；Host、R8 与 APK 不能代替这些证据。未实施没有运行证据的大型行为重构 |
| 保留范围说明 · 更广泛审查与社区研究 | 本轮沿具体静态缺陷清理，不宣称所有历史 / 所有功能包均已穷尽审查。指定八仓库参考仍有效；没有新可落地候选时，不为扩大改动量做泛化社区调研或强行合并有效边界 |

静态续处理基线 `89f0efd1988f455733815d51703429bef2b3748f`；本轮证据保存在 `.di-evidence/repository-simplification/static-followup/`，按 Deep 验证。全量 JVM 已验证 4,267 项、0 失败 / 错误 / 跳过；随后删除旧配置查询并对四类真实消费者补验 13 项，全部通过。全量工具 728 项全部通过、0 跳过；本轮同步校准旧本地化测试到真实 XML 使用的 prefs_group_connectivity，保留全语言要求。Develop / fatal lint 与未签名 Release 构建通过。以实施提交 `062ebe19d0a3372c70f7a531eb8f8d0fed0da896` 的固定快照复验：两个矩阵输出确定性通过；11 项必需 mutation 全部被独立门禁检出，0 存活 / gate 错误 / 无法验证 / 清理错误。后续仅更新文档证据，复用已验证的应用、工具和测试输入。应用依赖、版本、Hook 签名与交互不变，不签名、发布或合并 main。

静态续处理 APK 对照（同一 JDK 25 / SDK / 依赖）：4,484,537 → 4,480,961 字节（减少 3,576），最终 DEX 类数仍为 2,843。新 APK SHA-256 为 `f44d3e415e2c3f07cd0a6da681e786f45470a4b518d47a8d42d9c986ace7255f`。比较产物来自本轮基线加完整实施 diff，revision 标签随基线更新；不是正式候选，也不把全部字节差异归因于单个删除项。

ZIP 条目集合无增删；资源编号重新分配，因此二进制 Manifest、87 个 XML 及 DEX 等字节不同。用各自 AAPT2 资源表解析引用后，Manifest 属性完全一致，全部 2,976 个已打包资源的名称、各配置和值完全一致；原来 94 个候选本就不在可达输出中。native、功能样本与许可资产内容不变，两个变化的 assets 是构建来源和基线 profile。官方 DexSQL 未检出测试框架 / testsupport / compileOnly API 定义、安装采样调用或设置入口到 Hook API 的直接引用；Release runtime 依赖树与基线一致。主功能、偏好、Hook 精度及动作交互未变；不存在可据此声称的实机响应 / CPU / GPU / 功耗收益。

### 决策与实施

| 候选 | 证据、收益与取舍 | 决定 / 验证边界 |
| --- | --- | --- |
| main 中的安装性能采样 | 三个安装器、四个阶段复制时钟 / ART 分配读取与日志参数；Release 虽被 R8 删除，main 和 Develop 仍维护该实验 | 删除 FeatureInstallMetrics 及调用，直接注册原 Catalog；安装顺序、惰性工厂、失败隔离和补装不变。开发测量使用外部 Trace / Simpleperf，不增加变体桥接抽象 |
| 旧 XposedHelpers API 全集 | 26 个方法在 main、test、XML 中无消费者；核对全仓字符串、入口、R8 usage，属于内部辅助面而非外部 SDK | 删除参数索引 / 类数组包装、无人使用的基本类型字段访问和资产流转换；保留 overload 匹配、ClassUtils / MemberUtilsX、缓存与异常语义。编译和现有反射回归验证真实消费者；逐方法对照确认保留的 126 个方法体未改动，ABI 测试固定真实重载签名而非最少方法数 |
| ShadeExpansionTracker | main 没有调用方，仅自身单元测试创建；无 Manifest / XML / 反射入口，基线 R8 usage 含整个类 | 删除无生产对象的类、自身测试及两个重复且无独立 gate 的可执行 mutation；既有 74 项覆盖合同保留两条明确退役说明，11 个必需独立缺陷门禁不变。实际手势状态机 / Hook 行为回归保留 |
| provenance 三套重复注册 | 字段与任务声明重复，Release 多一个完整 sourceCommit；消费依赖与各变体目录已有实际合同 | 共用 registerBuildProvenance，保留原任务名、目录及 Release 专属字段；实际生成与合并 assets 回归 |
| 模块、目录和接口 | 现有单 app；Installer 负责进程入口，Spec 负责惰性创建，Registry 负责状态；拆分减少宿主错误初始化 | 保留有效边界，不机械合并成大类；不新增模块、源码层级或通用管理器。根目录无已跟踪 APK / ROM / trace 可删，忽略的本地证据不清空 |
| 第三方依赖 | Commons 参与反射重载选择，DexKit 参与 ROM 定位，Palette / Preference / AppCompat / 协程均有生产消费者；Fragment KTX 是传递版本对齐约束 | 不重写成熟库或删除仍有用依赖；compileOnly framework / API 与 testImplementation 边界保留，实际解析图和 APK 复核 |

仅移除三个已失去实现对象的测试类（采样算术、采样接线、旧阈值类），不删除或弱化有效业务断言。main 洁净门禁补拦 Robolectric、协程测试运行库和开发采样；BuildConfig 条件不再被当作源码集隔离。同步 AGENTS、ARCHITECTURE、开发与测量规范，防止恢复历史通用 API 和采样接线。

### 外部真实源码与适用性

本轮按以下 exact commit 读取构建、目录树、初始化 / 配置或生命周期源码，并记录提交时间 / 消息；不以仓库规模、国籍或 Star 判断。表中是局部工程取舍，不是对整个项目的质量排名，未复制其代码或依赖。

| 来源 / exact commit | 真实实现与 A14 取舍 |
| --- | --- |
| [Telegram / 3de37c8](https://github.com/DrKLO/Telegram/blob/3de37c8cfb039452be7398909461da6288babf15/TMessagesProj/src/main/java/org/telegram/messenger/ApplicationLoader.java) | postInitApplication 有单次初始化门；settings 分离应用分发和 AppTests。借鉴明确入口与幂等，保留 A14 的分进程安装；不照搬账号初始化和完整客户端依赖 |
| [Telegram X / d1ebf3f](https://github.com/TGX-Android/Telegram-X/blob/d1ebf3fef5bc302a2df25f268e7ef8c4beefe84d/app/src/main/java/org/thunderdog/challegram/telegram/TdlibListeners.java) | 分类 listener 的 subscribe / unsubscribe 成对，settings 显式 extension bridge 与 tests 模块。A14 复用现有 owner / release 合同，不新增总线，也不照搬其复杂 flavor / SDK 矩阵 |
| [Nekogram / 199abde](https://github.com/Nekogram/Nekogram/blob/199abde0f100d92cee8d925337561633e6e11186/TMessagesProj/src/main/java/tw/nekomimi/nekogram/NekoConfig.java) | 定制配置集中于独立包，loadConfig 受锁与状态门约束，昂贵支持检查异步。借鉴边界清晰；静态 SharedPreferences / 全量载入不适合 A14 多宿主惰性安装。该提交修复错误线程上的数据库操作，也说明直接实现仍需线程合同 |
| [AyuGram / 7013145](https://github.com/AyuGram/AyuGram4A/blob/7013145676d36d82ee13c02a89f72097b7490dcd/TMessagesProj/src/main/java/com/radolyn/ayugram/AyuConfig.java) | 所指定仓库默认分支停在 2023 年；配置独立包、configLoaded 避免重复加载可参考，静态初始化整组偏好不引入 A14；不把旧快照当当前 Android 工程标准 |
| [NekoX / 3581a10](https://github.com/NekoX-Dev/NekoX/blob/3581a10f8ecaa0f6d42d7a77442742caf95c6a13/TMessagesProj/src/main/java/org/telegram/messenger/NotificationCenter.java) | observer 修改有线程约束，View attach / detach 绑定订阅释放；借鉴生命周期与所有权，A14 现有注册释放足够，不增加全局事件中心 |
| [Kotatsu / 34f6e52](https://github.com/KotatsuApp/Kotatsu/blob/34f6e5232bf7f0bcdf8f3fbddb986697518e82a4/app/src/debug/kotlin/org/koitharu/kotatsu/KotatsuApp.kt) | debug KotatsuApp 才安装 StrictMode / LeakCanary，release 同名入口仅继承 BaseApp；这是清楚的源码集隔离。A14 删除已完成的采样实验；未来长期调试实现按此原则隔离，不引入其 Hilt / 网络 / reader 基础设施 |
| [MaterialFiles / 93c3339](https://github.com/zhanghai/MaterialFiles/blob/93c333960aa897836ba3f215a19d0cb0de494db1/app/src/main/java/me/zhanghai/android/files/storage/LanSmbServerListLiveData.kt) | 单 app 按功能包组织；扫描以旧数据增量发布、取消旧任务、dispatcher use 释放。借鉴明确 owner 与增量结果，不复制 SMB 扫描的 60 线程预算，不能因目录浅就认定适合 Hook 宿主 |
| [ReVanced Manager / 2a88257](https://github.com/ReVanced/revanced-manager/blob/2a88257b71d35c9f141907ffea67c87e51bbb0db/app/src/main/java/app/revanced/manager/domain/worker/WorkerRepository.kt) | app / api 有独立边界，hidden API stub 为 compileOnly；唯一任务 REPLACE 和输入 claim 后移除体现一次交接。其全局输入 map、Koin / WorkManager 不适用于 A14 现有短生命周期宿主，不为形式引入 |

构建历史交叉检查也覆盖八仓库各自的三条相关提交：例如 Telegram X [aa687c67](https://github.com/TGX-Android/Telegram-X/commit/aa687c67c06fa3c1abd8c1b5c78d536d7cc5fb00) 明确引入独立测试基础设施；ReVanced [3613e4d7](https://github.com/ReVanced/revanced-manager/commit/3613e4d752a1efc86137817d6bf859ffd1b71eac) 撤回过早移除的 Release 构建 workaround。前者支持测试边界，后者说明历史路径必须证明失效才删，不能只凭简短或陈旧判断。原始路径与提交摘要保存在外部证据。

### 生产隔离结论与产物对照

A = 仅仓库、不参与 APK；B = main 中存在、无运行入口；C = Release 实际打包；D = 生产实际运行或初始化。四层必须分别取证，静态引用缺失不能单独证明没有反射。

| 对象 | A / B / C / D 证据 |
| --- | --- |
| tools、scripts、ROM 契约、测试替身与回归 | A：构建任务可调用生成器，但脚本本身不成为 APK 内容；src/test 与 testImplementation 不属于生产源码 / 依赖。正式输出以 Gradle 解析图、合并 Manifest、ZIP 与 DEX 检查为准 |
| 旧阈值类与 26 个辅助方法 | B：无生产 / 资源 / 反射消费者；基线 R8 usage 证实移除；C、D 无证据。删除前逐项复核，不仅按文件名搜索 |
| 安装采样 | main 存在，Develop 初始化四阶段执行；Release 常量折叠后 DexSQL 未见 ART 分配采样调用，usage 含采样类。不是 Release C / D 污染；本轮同时清除 main 维护负担与 Develop 工作 |
| test0–test4 资产、PrefsProvider test/* | C 且在用户触发“打开方式”验证时 D：真实用户功能，保留授权 URI、可 seek 资产与 MIME 处理，不因名称 test 删除 |
| HookDiagnostics / 故障日志 | C / D：实际失败隔离与有界诊断，有生产用途；保留，不扩展采样、常驻监听或轮询 |
| framework.jar / libxposed API、测试运行库 | compileOnly / testImplementation；基线最终 DEX 未定义 libxposed API、JUnit / Robolectric / Mockito / MockK / 协程测试类。设置 Manifest 入口到 API 的直接指令引用为空；另由现有类加载回归覆盖传递边界 |

首批精简对照：前后均为 JDK 25.0.4.1、同一环境与 revision `fbf25c24` 的未签名 Release；after 是基线加本任务完整 diff，比较用产物不是正式发布候选。final DEX 类数含第三方库，不等同 A14 自有类数量。

| 指标 | 修改前 | 修改后 |
| --- | --- | --- |
| APK 字节 | 4,484,537 | 4,484,537 |
| 最终 DEX 类数 | 2,843 | 2,843 |
| APK SHA-256 | `04e1d93237ff6a26acbf11b8a4c1d65193990be7dcab3ba2bad55468f6e56821` | `4d371e0ff805019a9c35391c0e36cb944b6a31ea324ca2a8b1be7cf67bf800d5` |
| ZIP 条目集合 / 合并 Manifest / 资源 / 普通资产 / native | 原基线 | 集合一致，内容逐项一致；native 仍仅 arm64-v8a/libdexkit.so |
| 实际变化条目 | — | 仅 classes.dex 与 assets/dexopt/baseline.prof；不是逐字节等同 APK |
| Release runtime 依赖树 | 原基线 | 完全一致，无依赖新增或删除 |

官方 DexSQL 对两份实际 APK 均未检出测试框架 / libxposed API 定义、ART 安装采样调用，设置 Manifest 入口到 API 的直接指令引用为空；保留 R8 mapping / usage / configuration 与类加载回归，直接引用查询不冒充传递加载证明。Gradle 实际 sourceSets 与 Release / Develop / debugUnitTest 解析图分离，生产运行图无测试库、framework.jar 或 libxposed API；三变体合并 provenance 与消费依赖回归通过。没有新增常驻对象、监听器、线程或业务初始化。

首批精简（静态续处理前）的 Deep 已执行 `verify.py full`（4,266 项 JVM：0 失败 / 错误，2 跳过；Debug lint 0 errors / 478 warnings）、全量工具测试（725 项：通过，5 跳过）、`:app:assembleDevelop :app:lintVitalDevelop :app:assembleRelease`、文档链接 / 功能地图及完整 diff 检查。独立门禁以实施提交 `da6f01e` 的固定源码快照运行：两个 process matrix 输出确定性通过；11 项必需 mutation 全部应用并由独立语义门禁检出，0 存活 / 无法验证 / gate 错误 / 清理错误。最后的文档证据提交不改变应用、构建、工具或测试输入，复用上述通过记录。

删除项已被基线 R8 清除，因此没有 Release APK 体积或运行收益声明。Develop 每阶段移除 4 次时钟读取、4 次 ART 统计读取及 1 条日志（四阶段分布于三个宿主）；这是调用链减少，不是实测 CPU / 时延收益。设备延迟、CPU / GPU、帧时间和功耗仍待实机，不以 Host 测试推断。

其余取舍：成熟依赖、单 app 模块、进程入口、Feature 元数据 / 惰性行为 / 状态职责、有效行为测试及有界失败诊断继续保留。未发现足以支持继续合并这些边界或删除备份兼容路径的证据；不把目录层级、历史名称或文件数当作修改理由。后续只对新增调用链与实测成本推进，不重新恢复已退役类与采样设施。

## Wi-Fi 自动设置（2026-10-30）

名称为“Wi-Fi 自动设置 / Wi-Fi automatic settings”，入口“系统 → 其他 → 网络连接”。复用系统 Feature（265）、原有 system_server Context 交接、带发送者身份校验的动作通道、设置搜索与备份；首次启用安装仍需重启。最多 32 条规则，每条有名称、独立开关、SSID、可选 BSSID、优先级与四个默认“不修改”的属性。匹配顺序为优先级数值降序 → 精确 BSSID → 原列表顺序；编辑器可手动输入或选择已配置 / 当前连接的网络，不复用会扫描的旧 WiFiList。

- 识别使用一个 `ConnectivityManager.registerNetworkCallback`，请求底层 Wi-Fi，规则需要 VPN 时同一个回调同时观察 VPN；移除默认 NOT_VPN 约束，不依赖默认网络。启动 / 注册需求改变时读取一次当前网络，定位或设置页权限返回时按事件重读，不轮询。回调对相同 SSID / BSSID 去重，单个 500ms 期限合并连接抖动；同 SSID 漫游仍匹配同一规则时不重新读取 / 设置属性。初始快照未完成、UNKNOWN_SSID、身份冲突或容量溢出均为“不确定”，暂停命令；只有确认的断开才作为退出，不把尚未收到回调当成断网。
- 识别要求模块获准精确位置的始终访问、附近 Wi-Fi 权限并开启定位，不借 system_server 权限绕过用户选择；设置页快捷添加仅前台读取当前连接。系统事件触发的执行前重新核验权限，设置页返回通过已有可信动作通道请求新身份快照。勿扰每次读取 / 设置还单独检查模块包的通知策略访问授权，撤销后放弃无法证实的所有权，不强制恢复。
- 勿扰只调用原生 `NotificationManager.setInterruptionFilter`，不写 `Policy` 或编辑自动规则对象；只读 `ZenModeConfig`，发现已启用自动规则或带条件的定时手动勿扰即跳过，避免原生 filter setter 暂停用户自动规则或丢失倒计时。铃声用 `AudioManager.get/setRingerModeInternal`，旋转用 `Settings.System.ACCELEROMETER_ROTATION`。三个属性各自保存原值、模块最后确认值、场景 ID 与手动否决。先读取并核验所有相关属性，再按勿扰 → 铃声 → 旋转交接；前项写入后重新核验后项，DND 原生铃声副作用保留最早快照，不误判为手动覆盖。A14 原生 delegate 会切换勿扰的铃声请求安全跳过，勿扰策略优先；不改 VolumePolicy 来强行实现冲突组合。
- 家庭到公司直接比较新目标，不先全量恢复；相同目标不写入，新场景不管理的属性只在当前值仍等于模块确认值时恢复。手动变化后，该属性在整个连续匹配网络期间被否决；彻底离开后才重新武装。关闭功能 / 无动作规则时核验并释放仍拥有的状态；切换用户只放弃当前用户的旧记录。状态快照仅在进程内，重启 / system_server 重建重新读取当前网络与状态，不用旧磁盘快照恢复；因此不能保证跨重启恢复到上个进程曾记录的初值。
- VPN 复用 `VpnAutoSwitchAccess` 的快照、授权、原生 runner / connection token 和精确 stop 校验。应用自动 VPN（包括停用后仍在退役的启动请求）优先，Wi-Fi 放弃竞争；始终开启 / 锁定、未知状态或外部替换会话均不改。可靠静默启停仅开放已有系统 IKEv2 profile 路径；其他客户端已在目标状态则不重复执行，缺少可靠且可恢复的静默控制合同则跳过，不打开客户端 UI、不尝试通用 VpnService 启动、不调用 Shell。已有应用自动旋转白名单不写全局旋转值，继续作为局部例外；按应用媒体音量不控制铃声模式，二者不竞争。

业务成本：新增业务 Hook 0、服务 0、专用线程 0、扫描 / 轮询 0。存在有效规则时，一个网络回调、一个生命周期 / 系统状态 Receiver，以及按实际动作注册的一个 ContentObserver（0–2 个 URI）；全部关闭后释放这些业务注册，保留已安装 Feature 的一个冷配置观察者。复用原 BackgroundThread 与注册清理机制，旧网络回调通过 owner 身份失效；原生身份最多 16 个、标量租约 3 个、VPN 租约 1 个。Host 用 `WifiAutomationCoordinatorTest` 验证无规则零属性 / VPN 读取、1000 次同规则事件不增加读取与写入、相同目标交接零重复写入；这是调用行为证据，未测 Android Binder、常驻内存、延迟、CPU / GPU 或功耗，不宣称设备收益。

证据入口：`WifiAutomaticSettingsTest`（权限外的身份 / 匹配 / 编解码与备份合同）、`WifiNetworkTrackerTest`（VPN 底层网络、未知与初始快照）、`WifiAutomationCoordinatorTest`（独立所有权 / 手动优先 / 原生副作用 / 故障 / 用户切换 / 重建 / VPN），以及 `PreferenceNavigationUiTest` 的实际页面编辑。fuxi CN A14 framework.jar 的策略授权、内部铃声方法和 services.jar 的 `mVolumePolicy` / 原生 RingerModeDelegate decode 已取证，fuxi TW 策略授权方法定义一致；原件及日志在仓库外 `forA14-ROM-index/wifiauto-*` / `wifi-ringer-native.txt`。

Deep 验证范围：Full 4270 项的未受影响通过结果复用，修正异步对话框测试调度后，四个相关类 60 项回归通过；原生 Zen 配置保护加入后，`WifiAutomaticSettingsTest` 的 4 项（含实际 SDK 34 ZenModeConfig 自动 / 定时规则）通过。工具 724 项通过（5 项跳过），本地化 15 项、资源 11 项在最后文案更新后补验通过。最终 Develop / R8、fatal lint 和 Debug lint 通过；宿主专用非 SDK 反射的 lint 例外仅限两个 system_server 类，接口缺失仍安全跳过，不放宽全局门禁。官方 DexSQL 检查最终 APK 无 Hook API 定义、Manifest 设置入口无直接引用，且本 APK mapping 的 `System_WifiAutomatic → an1`、配置 / Options / Rule → `xu1 / vu1 / wu1` 的类型 / 调用 / 字段均无直接 Hook API 引用。APK SHA-256 `50a8ab6d9c7aaf296ba709b1e23533f62e92904b1a9f3c688fae9c528dbc82d2`；为本地未签名 Develop，不能当作正式发布或实机证据。

待实机：Android 14 / HyperOS 的网络回调及 Location 授权实际裁剪、500ms 短断连交接、勿扰与原有自动规则 / ROM 铃声策略交互、IKEv2 的启动 / 断开读回时序、权限撤销、重启 / SS 重建以及关闭后的原生清理。双 Wi-Fi 同时连接且身份不同按不确定处理；没有原生值变化的同值手动重设无法凭状态事件区分。模块框架停用不提供进程内退出回调，不能承诺停用 / 重启时恢复旧快照。当前无连接的 ADB 设备，静态 ROM / Host / APK 证据均不代替设备验收。

## 应用行为轻量规则（2026-10-30）

设置入口为“系统 → 屏幕 → 应用行为”，四项默认关闭，首次启用需重启设备。复用 system_server Feature 单次安装、偏好发布、现有应用多选器、原生编辑器及设置搜索 / 备份目录；不扩展 GenericApp scope，不向每个应用进程安装 Hook。

- 旋转：`ActivityRecord.getOrientation(int)` 的原生结果仅对白名单转换 USER / UNSPECIFIED 为对应 SENSOR，保留显式方向锁、BEHIND 与原生忽略值。传感器启停、应用离开与视频方向切换继续由 WM 管理，不写全局旋转配置。
- 亮度：两项共用 `RootWindowContainer.handleNotObscuredLocked(WindowState, boolean, boolean)`，只在 WM 锁内、原生读取窗口请求期间改写 `screenBrightness`，`finally` 无条件恢复应用原值。优先级为精确 Activity 例外 → 用户固定值 / 跟随系统 → 应用请求上限 / 忽略 → 原生请求；不写系统亮度 / 自动亮度模式。主用户、默认屏幕、全屏应用之外保留原生行为。
- 音量：`FocusRequester.handleFocusGainFromRequest / handleFocusGain / handleFocusLoss / release` 加 `AudioService.sendVolumeUpdate`，以长期 MEDIA / GAME 焦点界定使用场景；前台切换本身不改媒体音量。手动按键 / 音量 UI 变化覆盖待执行恢复；焦点死亡、旧 owner 迟到释放与关闭使旧任务失效。当前有其他媒体播放、通话、固定音量或用户不匹配时放弃恢复；焦点交接中其他媒体尚未停止仅允许一次 250ms 延后核验。
- 成本：全关不安装这 7 个业务 Hook 或 2 个配置观察者；全开无新 Receiver、服务、传感器订阅、音频采样、轮询或专用线程。窗口路径只查不可变配置、缓存字段 / 方法并在目标变化时短暂改写字段；音量仅在焦点 / 手动音量事件查询路由与播放配置，复用系统 BackgroundThread，最多一个合并持久化期限。音量记忆按应用与输出路由类型隔离，最多 256 项，写入主用户 Settings.Secure 独立键，不进入模块备份。

Host 证据入口为 `AppBehaviorPolicyTest`、`VolumeMemoryTest`、`SystemServerFeaturesWiringTest`、`SearchPreferencePageTest` 和 `PreferenceNavigationUiTest`；设备 CPU / GPU / 功耗、实际常驻内存与交互延迟尚未测量，不宣称性能提升数字。ROM 静态证据：fuxi CN services.jar `e9552126…` 的窗口 / 焦点 / 音量方法 decode 和字段定义，fuxi TW 方向 / 焦点签名及 munch ID 亮度入口对比；原始输出在仓库外 `forA14-ROM-index/appbehavior-*`。精确入口缺失由既有 Feature 错误隔离，窗口 / 音量 Hook 组安装失败回滚组内已装项。

Deep 验证范围：Full 的 4245 项 JVM 范围复用未受影响通过结果，首次启用分类 / 页面数量修正后的相关类及新增 13 项窗口 / 音量回归补验通过；`CurrentPreferenceContractTest` 验证亮度值与场景例外通过原备份校验。724 项工具范围复用未受影响结果，本地化 15 项、资源 11 项和生成器 14 项补验通过；分页 / 矩阵确定性、文档与功能地图检查通过，必需独立故障注入 11/11 检出且临时 Worktree 清理通过。最终源码的 Develop / R8 / fatal lint 通过，Debug lint 通过；官方 DexSQL 复核最终 APK 无 Hook API 定义、设置入口及新增设置页 / 配置类无直接 Hook API 类型引用（同产物 mapping：`System_AppBehavior → hm1`、`AppBehaviorPolicy → v3`），APK SHA-256 `746376eb44447a56161e8a91e2d31ca165609912ea825c430dbe2aaa185958b6`。仅为本地未签名 Develop，不是正式候选或设备验收。

待实机：快速切换 / 视频全屏方向锁、分屏 / PiP / 小窗、后台音乐焦点交接、静音与蓝牙路由、安全音量、进程死亡 / 重建、扫码 / 相机 / 支付码 Activity 例外与关闭恢复。音量不覆盖无长期媒体焦点的应用、不按每副同类型蓝牙设备单独记忆；切换设备沿用系统设备音量，直到下一次焦点获得。亮度仅约束标准窗口请求，不接管 ROM 专有 HBM / 阳光屏。当前没有 ADB 设备，不把 Host 或静态 ROM 证据写为设备验收。

四项按各自作用域控制，仅共享配置表达与现有基础设施；当前无需引入统一自动化执行器。精确 Activity 例外仍是高级文本设置，未来若有实际使用反馈可复用 ActivitySelector 做入口改善，无需扩张运行时。

工程执行策略仅在 A14 试点，分级与验证选择统一在 [TESTING](../TESTING.md#执行分级)；配置审计、测量和未验证范围见[试点证据](A14_EXECUTION_PILOT.md)。执行规则和验证路径的首轮验收保留在试点证据中；业务候选与本次结论以下方当前审计为准，历史试点不替代新候选验收。

工程取向统一为 [Performance-First（性能优先）](../../AGENTS.md#产品与取舍)：正确性、安全、A14 兼容及宿主稳定是基础约束，取舍优先响应速度、帧稳定性、关键路径与持续运行成本，不再采用严格的线性排名。实施和量化依据统一见[优化门禁](../OPTIMIZATION_GATES.md#关键路径优化原则)；保留核心功能与既有交互合同，不可见 / 被遮挡 / 不活跃时停止无效工作，新增非必要视觉与动画可以让位于性能。政策变更本身不代表已有实现取得新的设备性能收益，具体结果按下方各批证据判断。

任务启动与续接采用一次初始化、同一聊天持续继承上下文、按风险增量核验；重新发现仅用于新任务、实际写入冲突或上下文失效。main 合并、推送、发布及破坏性操作保留必要的实时来源和安全检查，不重跑任务发现；规则与技能入口已对齐，未新增状态管理机制或声称未经测量的耗时收益。

已授权开发成果在必要验证后默认提交并推送任务工作分支，以远端完整 SHA 读回确认保存；main / Tag / Release 仍需相应授权。代理优先级、有限重试和网络故障恢复统一维护在 [Git 协作规范](../DEVELOPMENT.md#本地-git-网络与失败恢复)，本机代理配置仅留在仓库 Git 元数据中。

## 全局输入、图像与资源成本审计（2026-10-30）

以实际最新有效实现 `25ee8b58aefa6aed35b8bcd731cf2d83a2a38898` 为基线；真实远端 main 为 `863305ebd56351687b2e0c54313b7247e7020373`。本地全部分支均已纳入基线，有效 Worktree 无未提交修改，无遗漏未推送成果；失效 Worktree 注册未做清理。实施归 相关工程记录（以现行源码与回归为准）。覆盖八类源码入口及跨进程所有权筛查，重点调用链逐项复核；这不是所有功能均完成设备性能验收的声明。此前 Dock / 岛 / G4 / 遮挡优化保留，仅复核交叉成本，未重新实施；未使用 HyperOS 3 证据。

等级 A 为可直接消除的确定性工作；B 须先做对照实验；C 为低收益、高风险或证据不足。P1 / P2 / P3 表示处理优先度，不表示已测用户延迟。每行保留调用链、触发、证据、方案与回归边界。

| 等级 / 优先度 / 范围 | 当前成本与证据 | 决定、风险与回归 |
| --- | --- | --- |
| **A / P1，已实施：SystemUI 输入包装** | `StatusBarGesturesHook → statusBarHook.before → getArgs → getArg → getMember`；原拦截入口读整组参数后直接返回，不改变手势状态、消费或业务效果；原触摸入口的只读 `getArgs` 让 API 102 适配器复制数组并走 `proceed(args)`。每个原生事件均触发 | 删除无业务作用的拦截 Hook，权威触摸入口用 `getArg(0)`，不再读 member / 整组参数；仍在原生分发前观察。Host 120 次触摸的整组读取与重传各 **120→0**，native 仍 120 次。覆盖原生结果 / 异常、观察失败隔离、fatal 与事件字段；attach / detach、仲裁器和状态机不变。无新缓存 / 所有者 / 线程 |
| **A / P1，已实施：状态栏与控制中心 DOWN** | `GestureMachine.dispatch → readBrightness → prepared Method.invoke → DisplayManager.getBrightness` 原来每个准备完成的 DOWN 都读取，即使只有音量 / 双击，或控制中心起点超出状态栏 | 在可开始且任一单指 / 多指动作可能调亮度时才读；120 次非亮度会话、120 次无效起点分别 **120→0** 次反射亮度调用。亮度配置仍在 DOWN 读取一次；后续加指可能切到亮度，不能仅依据 DOWN 时的手指数量跳过。保留首个 MOVE 起始值、失败回退、fatal、取消与配置快照；不声称 Host 调用次数就是设备 Binder 次数 |
| **A / P2，已实施：设置应用图标** | `AppDataAdapter / 三类选择器适配器 → BitmapCachedLoader.loadBitmap → getApplicationInfo → getApplicationIcon(String)`；冷缓存每个应用图标再次按包名请求图标，Android 14 String 入口内部再次查询元数据 | 使用本次 `ApplicationInfo` 的原生图标重载，保留 disabled 标志并加入 String 入口的 `GET_SHARED_LIBRARY_FILES`，不缓存包元数据。Host 120 个冷图标的 String 请求 **120→0**，每项一次元数据读取、一次 info 图标加载；暖缓存两者均为零。活动图标优先、应用回退、像素生成、队列、弱目标、缓存容量及交叉淡入不变。缺失 / icon=0 回退和共享库标志有回归；PM 自身缓存会影响实际 Binder 成本 |
| **B / P1：通知调色批处理** | `handle3thThemeColor → AppIconsManager.getAppIconBitmap → NotificationPaletteCache.resolve → WallpaperColors.fromBitmap → Monet ColorScheme`。已有每 Notification 单项弱 Bitmap + generation / uid / pkg / dark 缓存；同通知重复 inflation 已复用，新通知仍各自计算。Row 当前 entry 查 palette，原生 tint=0 恢复保留 | 暂不加共享缓存。对同应用批量新通知、同对象重绑、主题 / 用户切换及同 Bitmap 像素更新，先测图标身份命中、主线程 / 后台归属、调色次数与锁等待；有真实命中后再比较安装 owner 内小容量弱图像缓存。需覆盖并行 inflation、替换 / 失败发布、Row 复用、原生颜色；不能用合成同 Bitmap 的命中率代替 ROM 命中率 |
| **A / P1，已实施：无天气格式时停止订阅 / 查询** | `SystemClockHooks → buildClockText / needsWeatherData → WeatherDataController`；天气开关启用后，原来无论四类有效格式是否使用 `tq` 都注册分钟广播并查询。基线在同一执行体仅接入需求参数的对照中，120 次无需求请求仍取用 ContentResolver / 尝试查询 120 次；关闭后仍有订阅、在途结果仍发布，各有反例 | 复用实际格式选择、隐藏及功能开关，在初始化和现有偏好发布时计算需求，不进入逐帧 / 分钟热路径。无需求时分钟与天气专属屏幕订阅为零，120 次请求的 ContentResolver 取用 / 查询尝试 **120→0**；取消在途发布，亮屏重新启用刷新一次，灭屏等亮屏，重复启用无重复工作。合并天气与时钟构造 Hook（两者同时启用时由两个回调变为一个）；保留初始化 1.8 秒延迟、弱 controller、application Context、跨 scope 查询互斥和缓存字符串。新增状态仅两个 primitive 标志，无新线程 / 轮询 / 缓存。四类格式、CC 原有格式规则、关闭 / 恢复、屏幕和替换回归已覆盖 |
| **B / P2：仍有天气格式但失效 / 不可见 owner** | `weatherInfo` 由四类时钟共享；弱 updateTarget 失效不等于所有时钟停止消费。当前有格式需求且亮屏时仍按原节奏查询。`StepCounterController` 已有 View attach / detach、最后 owner 注销与屏幕取消 | 仍需核实所有实际 View 消费者和 controller 替换 / 销毁时序，确认没有输出后再加明确释放入口；不以 GC 或弱引用为空推断可停用。通知跨对象调色、偏好传播等 B 项保持原取证要求 |
| **B / P2：偏好传播** | `StatusBarGesturesHook.statusBarObserver → configPublisher.publish → GestureConfigResolver.resolve` 对每个偏好通知都解析十个手势字段；其他功能已有按键过滤 / 不可变快照。频率是设置事件，不是每帧 | 先比较单键 / 批量设置的解析与发布次数，再决定精确键过滤 / 可合并发布是否值得。不得合并业务广播、丢失 null 全刷新或改变手势 DOWN 冻结语义；没有设置风暴样本，暂缓 |
| **B / P2：设置列表 / 搜索 / ROM 页面** | `Helpers.getInstalledApps / getLaunchableApps → AppData.prepareForList → 四类 Adapter.ItemFilter`；已有预备小写字段、稳定分组、直接采用工作线程结果、图标有界 LRU + 单在途 + 2–4 可超时线程。过滤仍按每次查询 O(n)，`notifyDataSetChanged` 是 BaseAdapter / ListView 协议；ROM 辅助数据为离线工具，不进入 APK | 不为几百行引入数据库或把 ListView 当 RecyclerView 强改。用固定几百行 + 扩大集比较查询序列、键盘滚动与 PM 时间；仅在确有瓶颈后实验前缀增量搜索。需覆盖查询缩短、Locale / 选项 / 用户改变及陈旧结果隔离 |
| **C / P2：全局内存与生命周期** | 筛查静态 Context / View / Bitmap、Handler / Job、owned preference observers、Hook / ClassLoader 缓存。`ReflectionCache.LoaderState` 有界且 loader 隔离，`XposedHelpers` 成员缓存仍可保留 Class / loader；控制中心旧 lease 失效与 runtime 清理；锁屏封面 owner / generation / 引用释放已实现 | 没有 HPROF dominator、loader churn 或持续增长证据，不机械 WeakReference 化。Launcher 重命名 observer 显式 onDestroy 注销；天气剩余工作单列 B。先做重复重建 / 插件重载后的 retain-chain、PSS / 线程数对照，再改实际保留链；禁止据静态强引用数报告泄漏修复 |
| **C / P2：ROM 能力 / 异常路径** | Launcher 手势设置快照、Controls 几何 / 音频快照、通知 Monet 成员安装期解析、锁屏充电 caller scopes、Feature 单次安装与 loader lease 均已有冷 / 热分离。锁屏 `TapToUnlock` 在 MOVE 前放行；Launcher 前台包查询只在 DOWN，后续编辑 / occupied-cell 查询只在成功双击 | 不删除有 owner / 动态来源用途的查询，不重建 resolver。尚存局部反射与 Throwable 边界，但无目标 A14 重复失败热点，不能仅凭 catch 数量删除或永久负缓存。图标 loader 既有 Throwable 容错是独立失败语义问题，不借性能改动顺手重写。需目标缺失成员 / 错误频率及原生回退证据 |
| **B / P1：资源、通知 / 媒体与 GPU 剩余成本** | Launcher 图标 / 文件夹 blur、锁屏封面、AudioVisualizer、控制中心材质与通知颜色；`getIdentifier` 多在安装 / inflate，通知标题 ID 已惰性保存；封面已有尺寸 / 参数 / generation 失效、单在途 / 取消发布。可见图片处理、文件夹原生 blur、媒体波形仍可能昂贵 | 先采主线程 / RenderThread / GPU 与图像分配；不能因资源名查询命中就建立全局缓存。保留原生材质、G4、封面裁切和波形合同。GPU、动画与视觉改动必须配当前 APK 的设备录屏，本轮没有修改视觉或动画 |

**轻量成本预算与取证。** 不新增采样设施、监控服务、后台线程或持久化数据库。以下是同场景比较的验收预算，非实测 CPU / 内存数字；运行时按需取现有 Perfetto、Simpleperf 和 ADB（后续工程精简已移除 develop `FeatureInstallMetrics`），其余范围见[测量门禁](../OPTIMIZATION_GATES.md#轻量测量)。

| 进程 / 模块 | 可验证成本预算与场景 |
| --- | --- |
| SystemUI 输入 | 每事件最多一次原生分发，观察不复制 / 重传参数；非亮度配置 DOWN 零亮度查询，可能亮度的有效 DOWN 仍一次。固定触摸轨迹采 UI 线程调度 / CPU、Java 分配与 GC、Binder slice；亮屏 / 锁屏 / 控制中心 / 多指各自对照 |
| SystemUI 通知 / 媒体 / 状态监听 | 每通知相同图像输入最多一次调色；变化输入必须失效。批量通知记录调色 / inflation 次数、主线程时间、Bitmap / Graphics、RenderThread / GPU；屏灭任务取消与 listener、线程 / Job、provider 查询数分别验证。天气无消费者问题仍未关闭 |
| Launcher 输入 / 图标 / 文件夹 | 不符合手势条件及时放行；保留当前编辑 / 目标状态核验。冷启动与快速滑动 / 文件夹开合 / 重建分别比较反射、PM / Binder、分配 / GC、帧时间及 blur GPU。既有 Dock / 岛逐帧几何、动态壁纸 / 波形成本沿用下节待验，不重复修改 |
| system_server Controls / 事件 | 按键 / VPN / 偏好更新为事件驱动，Feature 单次安装；复核无目标功能时无业务 Hook / receiver / job。分别采按键、前台任务切换和屏灭 idle 的线程、Binder / 锁等待、CPU；不取消安全 / 会话核验换取次数减少 |
| 设置进程列表 / 搜索 / 图标 | 冷图标每项一次显式应用元数据读取；暖缓存零 PM；保留 512 KiB–8 MiB 既有图标 LRU、128 队列与空闲线程退出。固定列表 / 搜索 / 返回重建，记录 PM、分配 / GC、主线程帧与 idle 线程。无新增驻留状态 |

图标入口依据为 Android 14 的 [ApplicationPackageManager](https://github.com/aosp-mirror/platform_frameworks_base/blob/android14-release/core/java/android/app/ApplicationPackageManager.java)：String 重载再取元数据，info 重载继续走 `ApplicationInfo.loadIcon`；这不是 HyperOS 私有 API 或实机证据。

仓库外证据入口：`customiuizer-a14-evidence/global-runtime-audit-20261030/`；保留基线 / 最终 diff、源码身份、前后 JUnit 与验证日志。旧实现先做等价回调提取再执行同一成本断言，8 项中 4 项出现预期反例，分别为 120 次冗余参数读取、非亮度读取、无效起点读取及包名图标请求；其他行为基线通过。Deep Full 为 464 套件 / 4,224 项，零失败 / 错误、2 项既定跳过，包含最终新增的 fatal 与共享库标志回归；全量工具覆盖并集 723 项、5 项既定跳过（文档锚点尚在写入时的一项失败，在文档完成后补验相关 13 项全部通过）；Develop / R8、fatal lint 通过，官方 DexSQL 核对 31 个映射设置入口 / 辅助类无直接 Hook API 引用、compileOnly API 未打包，API 102、SDK 34 / arm64 与 16 KiB 对齐通过。三项可各自回退（实现 + 对应行为回归），没有设置键 / 数据格式 / 持久缓存变化。

天气需求续接基线为 `1dab7a72c26a2b7f15466d05e08e586b4046b8f5`，继续同一 Issue / 分支；证据位于仓库外 `customiuizer-a14-evidence/weather-demand-20261030/`。旧执行体保留查询行为，仅给对照接入需求参数，8 项中 3 项复现无需求查询、未释放订阅及失效发布；修后针对性 4 套件 / 95 项通过。Deep Full 465 套件 / 4,231 项，零失败 / 错误、2 项既定跳过；全量工具 724 项通过、5 项既定跳过，矩阵两个输出的确定性通过；Develop / R8 与 fatal lint、官方 DexSQL 最终 DEX API 隔离（31 个映射设置入口 / 辅助类）、SDK / ABI / 对齐检查通过；生命周期门禁仅扩展合法第三参数形态，仍以错误 owner 和缺失 fatal 的反例验证，未放宽所有权或异常要求。天气实现、时钟接入、行为回归与对应门禁为同一回退单位；无设置键 / 数据格式 / 视觉变化。

本轮无 ADB 设备连接。Host 结果仅证明调用 / 参数数组路径和行为，不能推导 ART 实际分配、用户等待毫秒、CPU / GPU、功耗或续航。仍需 HyperOS 1 / Android 14 同设备、同开关 / 亮度 / 刷新率 / 应用集的基线与修后采样；ROM 与 APK 证据分别保存。

后续最值得投入的三个方向：① 通知批量调色的线程 / 图像身份 / 锁等待取证，再判断小容量跨通知复用；② 仍有天气格式需求时的实际消费者生命周期，继续消除确认无输出的 provider / 回调工作；③ 设置冷列表与 Launcher 手势的 Perfetto / PM / 分配对照，确定真实主线程和 Binder 热点。现有岛几何、波形及 Dock GPU 设备归因继续保留原优先级，不以本轮 Host 次数宣称已经解决。

## 持续运行成本与渲染管线审查（2026-10-30）

本轮从实际最新有效代码 `68ae75fa0eec6c20b810ef6946e72889bc33e438` 继续，真实远端 main 为 `863305ebd56351687b2e0c54313b7247e7020373`，相差 15 个已纳入的有效提交；工作区干净，相关聊天空闲，无未推送改动。实施归 相关工程记录（以现行源码与回归为准）。审查覆盖 SystemUI、Launcher、system_server、媒体入口及全项目消息 / 锁 / 广播的静态筛查；不把入口覆盖、调用数量或旧版 trace 当作当前全功能性能验收。未使用 Dada / HyperOS 3 作为 A14 合同。

| 优先级 / 路径 | 原有成本、当前决定与剩余工作 |
| --- | --- |
| P0 · `IslandStatusBarCompact` | PreDraw → `refreshGeometry` 保留原生几何变化检测；此前 `applyHiddenAlpha` 无条件遍历所有候选叶子。现按遮挡变化维护弱 View 的实际遮挡集合，只对该集合读写 Alpha；进入遮挡读取最新原生值，退出 / 移除 / detach 仅恢复仍归自己持有的值。同帧原生 Alpha 提交优先。过渡捕获与是否需要过渡的判断也只读遮挡集合，不维护清晰节点的 Alpha |
| P0 · 几何监听保留 | `Geometry.changed()` 仍逐个读取 Matrix、bounds、clip、visibility、scroll、padding；变化后仍重扫遮挡并刷新自身位移基线。原生 RenderNode 属性动画、祖先裁切和窗口位移可绕过 layout；移除 PreDraw 或稳定后退出将漏掉恢复，未找到覆盖这些变化的更窄原生事件。因此不新增轮询或粗略稳定计数，也不宣称本轮消除了几何扫描 |
| P1 · Dock 编辑动画 owner | `AnimationSet.getTransformation` → 原调用 → 全局附加字段表查询，原来每个无关 AnimationSet 都支付查询。现绑定 / 释放时发布局部不可变快照，每项仅弱持有 AnimationSet 与装饰 View；帧回调只读取快照。空集合直接返回，不读取 receiver / 参数，不访问附加字段表、不加锁；有 owner 时只按实例身份匹配，原生返回 / 异常 / 末帧时序不变。替换、清除 / 解绑时删项，变更时顺便清除死亡弱引用；存量随当前绑定而定，无历史记录或后台清理任务 |
| P1 · Dock 全局 Hook 与 PreDraw 保留 | Hook 分发本身尚存在；full-width、Google blur 和玻璃仍需跟随祖先原生动画，每个实际宿主帧同步矩阵 / 可见性 / 合成透明度。窄 Hook 的临时安装 / 解除涉及末帧、失败与多个 owner 交接，当前没有设备录屏证明，暂不引入反复 Hook 安装或另一套常驻监听。现有无材质、零透明度及隐藏退出路径继续使用原取消逻辑 |
| P1 · GPU / 材质 | `DockGlassOptics` 的平坦区域直接透射已由基线实现并有 Host 像素对照，本轮不重复改 Shader。边缘折射 / 光照仍较复杂；玻璃与原生 fallback 保持单可见填充。`DockGlassBackdrop` 保留单 RuntimeShader、每次新纹理一次 BitmapShader、uniform 比较、静态 viewport 复用、单在途捕获与有限恢复；绘制 / PreDraw 的矩阵准备、动态壁纸采集和输入 shader 替换仍有成本，尚无设备 GPU 排序依据 |
| P1 · G4 / 吸入捕获 | G4 单位曲线惰性缓存，形变网格使用 owner 的复用数组与预计算表；`LauncherWindowAbsorption` 单在途有界捕获，安全 / HDR / 保护内容保留原生回退，HardwareBuffer 在 wrap 后关闭，替换 / 取消回收纹理。网格绘制 / 上传和 Surface 捕获仍需目标设备数据，未用 CPU 推断 GPU，也未降低精度或修改视觉参数 |
| P2 · 安装 / 启动 | `FeatureInstallRegistry` 先检查开关 / 安装状态，再 `spec.create().install()`，保留单次安装和失败语义；SystemUI / Launcher 仍冷建阶段 Catalog，system_server 复用 registry。当时的 `FeatureInstallMetrics`（后续工程精简已移除）仅统计 Catalog 构造 / 注册的耗时与 ART 分配，**没有逐 Feature install 的测量**；不得据此给出 Feature 耗时排名。暂不改 Registry 或生产监控，下一步可在 Develop 的现有入口短时抓 Trace / Simpleperf 来定位，再决定是否需要局部安装分段 |
| P2 · 反射 / ClassLoader | `XposedHelpers` 的成员与类缓存强持有 Class / loader，包括按 loader 的负缓存；当前没有 loader churn、对象保留链或随时间增长证据。插件 lease 失效不等于缓存或框架 Hook 已释放 loader。保留高性能缓存，不以 WeakReference 替换猜测泄漏；需重复插件重载的 HPROF / PSS 对照后再判断缓存与框架各自保留责任 |
| P3 · Binder / 锁 / 唤醒 | 全项目筛查继续追到媒体会话复核、录音峰值、封面单在途 / 发布、Duo 网速有界投递及 system_server 安装 / VPN 事件。音轨与会话查询有来源 / 权限职责，已有缓存锁多为短状态 / 发布段；没有新 trace 能证明主线程阻塞热点，不按调用数量删除安全查询。保留现有广播合并、隐藏租约释放、有限 deadline 与原生失败回退；当前无法给出锁等待或 Binder 延迟排名 |

两项源码修改可分别回退，不改变设置 / 数据格式、A14 ROM 成员签名、视觉参数和 API 102 边界；未新增轮询、线程、Binder 或业务唤醒。新增资源仅为现有 compact owner 内至多 128 项的遮挡引用集合，以及 Dock 当前绑定的不可变弱引用快照；detach / 清除时释放。Dock 只在绑定 / 释放的冷路径分配快照并加锁，不在帧回调更新集合。没有设备内存数据，不宣称净内存减少。

修前冻结行为反例：相同测试输入 67 项中 3 项失败，未遮挡图标在 120 次稳定 PreDraw 中有 240 次 Alpha 读取，无 Dock owner 的 120 次动画原调用仍有 120 次 receiver 读取；Dock 编辑 owner 存在全局附加字段表。修后及完整验证结果以本轮仓库外 `customiuizer-a14-evidence/sustained-runtime-20261030/` 保存的源码补丁、JUnit 和日志为准。上述计数是 Host 工作量，不能换算为 ART 分配、CPU 时间、GPU 时间、帧率或续航。

本轮按 Deep 验证：相同冻结输入的针对性 7 套件 / 106 项通过，未遮挡 Alpha 和空闲 Dock receiver 读取均为零；原生 Alpha、祖先变换 / clip、分支替换、过渡中断 / 末帧、隐藏恢复及 detach 有行为覆盖。Full 实跑 461 套件 / 4,212 项，零失败 / 错误、2 项既定跳过；再补多个编辑 owner 的隔离与最后释放回到空闲的反例，最终两套件 / 68 项通过。生产源码哈希未变，复用 Full 的未受影响结果并按用例替换最终针对性结果，并集 4,213 项，不将重叠执行相加。全量工具 723 项通过、5 项既定跳过；JDK 25 / Debug lint、Develop / R8 与 fatal lint、文档 / 地图及完整 diff 检查通过。同一未签名 APK（SHA-256 `cb699b01443029e6deee4c158b184fbbe6908ef9eaeaaf866470418fe1f0f17c`）由官方 DexSQL 复核 29 个映射设置入口 / 辅助类的直接 Hook API 引用为零、未打包 compileOnly API 定义、原 `setId/getId` 各一处、`replaceHook` 为零，SDK 34 / arm64 / 16 KiB 对齐通过。APK 工程 revision 为基线 `68ae75fa`，本轮输入由外部补丁 / 哈希关联并保留 APK 与同产物 mapping，未把脏工作区构建冒充 exact commit 产物。

**设备验收缺口：** 当前 ADB 无连接设备，上一轮明确暂时无法连接；未安装、重启或签发正式包。适用范围仍仅 HyperOS 1 / Android 14 / SDK 34 / arm64-v8a / libxposed 102。所有下列场景的当前源码 CPU、分配 / GC、帧时间、调度、Binder、PSS 与设备支持的 GPU 指标均待验，旧 trace 仅用于选题；动画 / 视觉相关恢复行为仍须实机录屏，Host native graphics 不能代替。

| 待验场景 | 复用的测量与验收重点 |
| --- | --- |
| 空闲且岛关闭；长时间计时；音乐叠计时 | 同设备 / ROM / 构建 / 亮度 / 刷新率配对，分别比较关闭、开启与遮挡位置；Perfetto / Simpleperf 区分主线程、RenderThread 和采集线程；关闭路径确认没有新增监听 / 唤醒 |
| 快速点击 / 切页；Dock 编辑、展开 / 收起、解锁 | 单独录屏检查原生透明度、遮挡恢复、末帧 / 中断和重建；测量区间另抓 FrameTimeline / 调度，不把录屏负载混入 CPU 对照 |
| 静态 / 动态壁纸上的玻璃 Dock 长期显示 | 固定壁纸及页面，玻璃 / 原生材质配对；已有静态采样为零、动态最多约 14.93 Hz 的预算继续复用；GPU 仅报告设备实际支持的计数 / 时间，RenderThread CPU 不代表 GPU |
| 锁屏封面加载 / 切换 / 反复进入；SystemUI / Launcher 启动重建 | 复用封面单在途 / 回收与外部 trace（历史 FeaturePerf 日志已停止采集），分别记录分配 / GC / PSS、冷启动 catalog 与线程阻塞；插件重载另抓 loader 保留证据，不用一次 PSS 推断持续增长 |

下一轮前三个投入方向：① 同构建计时 / 音乐 / 快速切换的 Perfetto 与录屏，确认几何扫描和波形各自占比；② Dock 动态壁纸的捕获 / GPU 对照及编辑末帧录屏，再决定临时 Hook 或材质简化是否值得；③ Develop 启动 / 重建和插件重载，补逐安装热点与 loader 保留链证据，再决定缓存 / Catalog 改动。

**同聊天静态续接：Dock 参数提交与透明度重复遍历。** 从 `320ce950272fcf3a19f00b08a479f90960ea4b31` 继续，同一 相关工程记录（以现行源码与回归为准） / 分支。动态岛的逐帧几何检测仍有原生属性动画 / clip / 窗口移动的职责，未找到可以安全停用的完整事件替代；本批转向已经能证明的 Dock 重复工作，不新增监听或扩大缓存。

| 路径 / 修改 | 修前反例与修后结果 |
| --- | --- |
| `DockGlassBackdrop.updateLensUniforms` | 原有缓存只能跳过全部 13 个标量都相同的帧；任一值变化便提交全部 7 组 uniform。现复用原有两个固定缓冲，逐组按 `toBits()` 比较，保持与 `contentEquals` 一致的 signed zero / 精确浮点语义；首次使用或替换 Shader 仍完整提交。120 帧、每帧 0.125 px 的水平移动：Host uniform 写入 840 → 121（sampleX 120 次、恒定 motion 首次 1 次）；半径单独变化时新增写入 7 → 1。停稳、density / origin、亚像素变化和 Shader 重建分别有断言；AGSL 数学、像素采样、纹理预算和路径不变 |
| `LauncherDockBackground` 同步透明度 | 原 PreDraw 在玻璃采集、full-width 材质可见性和 Google blur opacity 中重复沿祖先链求值。现先完成 full-width 变换，再在**当前同步调用内**复用一个 primitive opacity；只有玻璃 / 全宽原生材质 / 已启用 Google blur 需要时才读取。`draw` 与原生编辑回调各自重新读取，生命周期入口保留按需现算，不跨帧缓存。原生材质夹具中 120 次稳定 PreDraw 的同一祖先 Alpha 读取 360 → 120；PreDraw 后将祖先 Alpha 从 0.5 改到 0.4，实际 draw 正确提交 92，并覆盖零透明度停用 / 恢复 |

此续接按受影响合同采用 Standard：仅增量提交已有值并复用同步调用内的结果，没有新增 / 改动 Hook 安装、ROM 成员、跨线程 / owner 状态、Shader 算法、资源打包或 R8 边界。修前 / 修后使用相同冻结测试哈希：修前 31 项中 3 项失败，修后 12 套件 / 116 项零失败 / 错误 / 跳过；覆盖 Dock 几何、全宽、原生编辑末帧与取消、透明恢复、静态复用、纹理生命周期、材质轮廓和 Host native pixels。源码门禁 / Debug 编译、文档 / 地图与完整 diff 检查通过。原 Deep / 工具 / APK 证据只复用未受影响的合同，本批未重新全量 CI 或生成 APK，不将旧 APK 作为本批产物。

本批证据为仓库外 `customiuizer-a14-evidence/dock-incremental-sync-20261030/` 的冻结测试、JUnit、源码哈希及补丁。新增计数 map / 参数记录仅存在于测试 shadow，生产没有新缓存、数组、线程、Binder 或任务；两项源码修改可分别回退，不改变设置 / 数据格式与既有回退。上述数字是 Host 方法调用 / native shader shadow 写入次数，不能作为 ART 分配、实机 CPU / GPU、帧时间或功耗收益。当前 HyperOS 1 / Android 14 的设备录屏与前后测量仍待验，前述三个设备取证方向保持优先。

## 高收益筛选与 iOS 机制参考（2026-10-30）

本轮以最新有效分支 `b7e79c591a2f611a7a492ac4148e26dcb2f03728` 为基线，已核对干净工作区、Worktree 与真实远端，未用较旧 main 代替；实施见 相关工程记录（以现行源码与回归为准）。按当前 Hook / system_server、时钟、媒体、动态岛和 Dock 入口筛选，不将入口审查称为全功能设备验收。

| 优先级 / 成本来源 | 当前决定 |
| --- | --- |
| 优先落地：秒钟 ticker 每秒对隐藏 / 脱离的时钟更新时间、读取制式并刷新文字 | 使用原生 `View.isShown`，只在首个可见且启用秒钟的 listener 前准备共享时间，每轮一次；隐藏 listener 不校准、不刷新。原整秒 ticker、亮灭屏、弱 owner、配置和异常边界保留，复显在下一整秒恢复；不增加缓存、监听或任务 |
| 静态续接已落地：Dock 平面内部仍执行完整折射及边缘光照 | 足够远离轮廓时直接按原坐标透射；只保留一次纹理采样与原自适应色调，跳过整段边缘光学，按面积、运算路径和色差上界验收，不以设备连接为前置条件 |
| 后续大头：可见波形持续采集 / 绘制、动态壁纸采集及玻璃 GPU 成本 | 已有休眠、静态复用、有限恢复和最新预算削减；本轮不重复降频或叠加材质机制。最值得补的是同场景设备 CPU / 帧时间对照，以确定应替换哪条路径 |
| 暂缓：媒体会话安全查询、跨进程 owner 复核及共享 Hook 再抽象 | 当前查询有来源变化 / 安全失效职责，未找到足以支持跨事件复用的稳定依据；不为取消必要核验引入缓存、IPC 协议或调度层。既有字段预解析、参数复制和单次安装修正继续复用 |

参考已读取的 [Crescendo 私有声明](https://github.com/Yves000/Crescendo/blob/main/CRPrivate.h)、[动态岛](https://github.com/Yves000/Crescendo/blob/main/hooks/CRHooksDynamicIsland.x)与[输入 Hook](https://github.com/Yves000/Crescendo/blob/main/hooks/CRHooksSpringBoard.x)：复用原生 owner / 控件，可见性约束更新，布局期只做必要增量，输入占有及时释放。[SpringBoard 17 声明](https://github.com/MTACS/iOS-17-Runtime-Headers/tree/main/PrivateFrameworks/SpringBoard.framework)中的 Controller、FluidAnimator、SceneElement 分别体现窗口 / 抑制归属、过渡 actions / completion、激活 / 可见性 / invalidation 分离；这是声明层的设计启发，不能证明内部算法或收益。指定 Limneos 16.3 入口未能读取，使用现成 17 头文件作有限参考，不下载固件、不将其转成 A14 ABI。[VisibleIsland](https://github.com/ethxnn88/VisibleIsland/blob/main/Tweak.xm)复用系统岛窗口，但包含布局期重复原调用与机型读取，本轮不移植该路径，也不新增触摸 IPC。A14 继续沿用自己的单窗口、有限动画与安全合同。

冻结行为测试的修前 / 修后证据保存在仓库外 `customiuizer-a14-evidence/high-yield-20261030/`：120 次隐藏 tick 的 Calendar 更新时间调用由 120 次降至零，4 项新增反例修前均失败、修后通过；混合可见性、复显和共享时间每轮一次有独立断言。此为 Host 调用次数，不代表设备 CPU / Binder / 功耗测量；原有亮屏一秒唤醒仍保留，不宣称完全取消 ticker。实际 HyperOS 控制中心显隐、隐藏后首帧秒数和主题 / 用户切换待设备验收。

本轮按性能问题执行 Deep：相同测试源码哈希的修前 81 项中 4 项失败，修后 81 项通过；最终 Full 实跑 461 套件 / 4,209 项，零失败 / 错误、2 项既定跳过；全量工具 723 项通过、5 项既定跳过，JDK 25 / Debug lint、文档 / 地图与完整 diff 检查通过。未签名 Develop 的 R8 / fatal lint、SDK 34 / arm64 / API 102 / 16 KiB 和工程来源检查通过；官方 DexSQL 在同产物映射的 ticker 中确认两处 `View.isShown` 调用，29 个映射设置入口 / 辅助类的直接 Hook API 引用为零，未打包 compileOnly API 定义，原 setId / getId 各一次，replaceHook 为零。APK SHA-256 为 `94d1f2527fef7e55fdf7d4bcd04abe430d7e3ae037d1443c0623b28b5d3dea42`，工程 revision `b7e79c59` 对应基线，完整本轮输入由仓库外补丁 / 哈希关联。回退单位为本轮源码改动；未签名、未操作设备、未合并 main 或发布。

静态续接 相关工程记录（以现行源码与回归为准） 从 `71aea531a3fb231d11a20fec09a0907b538a6af9` 继续。按用户明确的静态验收范围，不等待设备测量才能处理可证明的无效计算。当前波形请求最多 10 Hz、自驱插值最多 20 Hz，隐藏休眠与稳定值停帧已有实现；Dock 动态采集间隔至少 67 ms（最多约 14.93 Hz），单纹理最多 196,608 像素（按 RGBA8 像素量折合 0.75 MiB，不代表全部驻留图形内存），理论捕获输入上限约 293.44 万像素 / 秒，静态稳定后额外采样定时任务为零。本轮保持这些预算，不重复降频；优先取消每次实际绘制中平面内部的整段边缘光学。

令面板宽高为 W/H、圆角 R、内部阈值 S=`max(bevel, 6 × max(2 × density, 1))`，适用连续面积为 `(W−2S)₊ × (H−2S)₊ − (4−π) × (R−S)₊²`。density=1 的 360×48 / 70 / 96 dp、圆角 16 / 22 / 30 dp 示例分别有 29.47% / 33.15% / 48.47% 面积适用；每个适用像素跳过 2 次显式 sqrt、1 次 refract、2 次 pow、3 次 exp，仍只采样一次。此处 t=1，折射偏移严格为零；六个 glow 长度和物理像素下限约束被省略的光照，在归一化 SDR 的实数模型中，每通道差异上界约 0.0001383，即 0.0353 个 8 位通道级。面积不含抗锯齿边缘，运算数不代表编译后指令数，GPU 分支策略与耗时不由本模型推出。

该续接为单个 AGSL 字符串的纯数学分支；未修改 Hook、owner、采样 / 释放、安全、资源打包或 R8 边界，依受影响契约采用 Standard 的 Debug 编译、源码门禁和 6 个关联套件，共 47 项零失败 / 错误 / 跳过。冻结像素测试修前 4 项中新增 1 项失败，同测试哈希修后通过；原生 HWUI 对照覆盖五种高度 / density、窄条、彩色高频纹理及明暗输入，快速 / 完整路径差异不超过 1 个 8 位通道级，alpha 相同。原有亮 / 暗 / 细节壁纸预览的 Liquid 列相对修前已留存图像，RGB 逐像素差异为零，另有一次同 fixture 验证；这只证明 Host 像素。文档 / 功能地图和完整 diff 检查通过。证据为仓库外 `customiuizer-a14-evidence/dock-interior-static-20261030/` 的静态模型、冻结测试、JUnit 和像素差分；旧 Deep / APK 结果只覆盖原未变更的合同，此续接未生成新 APK 或宣称设备 GPU / 功耗收益。回退单位为此续接源码改动。

## 全覆盖入口与逐批深入（2026-10-30）

从已公开 r14.25.9 的 main `f60af6e12e52c044c4081e6e3483a451f948fea2` 继续；全覆盖入口始于 相关工程记录（以现行源码与回归为准），当前接续 相关工程记录（以现行源码与回归为准），分支 `codex/refinement-host-failure-boundaries`。先把全部功能域的当前入口、已有证据和未覆盖行为对齐，再按可独立回退的具体缺陷深入。下表的覆盖指目录 / owner / 已有回归可追溯，不表示每个开关或所有 ROM 已完成行为和设备验收；设置与安装全集继续使用功能地图、Feature catalog 和过程矩阵，不新建另一份功能数据库。

| 功能域 | 当前覆盖入口及已有证据 | 下一层深入 / 待验 |
| --- | --- | --- |
| M00 / M01 目录与注册 | 功能地图、Feature catalog、ProcessRouter 与各宿主 Features；256 项过程矩阵由整合源码生成 | 安装全集与设置来源一致性；新增入口随源码更新，不以目录通过代替 ROM 行为 |
| M02 声音与振动 | 音量键、波形、录音量程、媒体格式及声音设置的既有回归 | 音源切换、振动时段与长期设备成本；波形测量只保留原设备范围 |
| M03 通知与状态栏 | M03 合同、通知安全、媒体事务、状态栏 Insets 与着色；第二行网速有界投递及多显示屏当前 owner、DUO / 岛波形关闭动画终态、录音弱尾音静音及同源读取恢复已补 Host 回归 | 真实消息接管 / 撤回、频道、认证、旋转与不同 ROM 合成；网速积压时的实际帧时间 / 唤醒成本待测 |
| M04 锁屏与应用锁 | `SystemLockScreenHooks` / `SystemUILockScreenHooks`；PIN、充电样式 / 重复布局、封面与应用锁计时已有专项反例和回归 | 原生认证失败边界已补齐；信任来源 / 用户归属 / 认证分类、闹钟与解锁手势继续深入，充电实际样式 / 帧时间、主题与息屏另做设备闭环 |
| M05 / M07 手势与动作 | 共享手势 owner、异常释放、音量键、全面屏导航、全局动作及 PiP | 取消、快速交接、边缘冲突与多显示屏组合；保留原生触发语义 |
| M06 桌面与 Dock | Launcher 几何 / 图标所有权、POCO ABI、Dock 采样与材质、动态岛返回的既有专项 | DOCK 透明暂停 / 有界恢复及静态纹理复用已补回归；POCO 7601 实际合成、壁纸变换、快速手势和帧时间仍待设备 |
| M08 / M10 维护与设置 | MaintenanceJournal、偏好原子发布、恢复、页面绑定、自动值 / 数值输入与设置页地图 | 真实进程中断恢复、备份 / 重置交接及无障碍；不重复改造已通过机制 |
| M09 应用管理与安全中心 | `SettingsFeatures` / `SecurityCenterFeatures` / `PackageInstallerFeatures`；详情、排序、核心包保护、安装器、侧边栏及扫描范围已有回归 | 安装器 CN / TW 缺失边界、侧边栏重建、原生智能充电刷新；本批先修电池温度失败边界 |
| M09 电话与输入法 | `PhoneFeatures` / `InputMethodFeatures`；来电显示 / 亮度 / 悬浮接听、音量光标、底部边距与 Gboard 属性入口均已定位 | 来电显示 / 亮度原生失败、悬浮接听失败回退与夜间边界已补专项；Gboard 横竖屏值 / 设置恢复与输入法插件重建已补 Host 回归；输入法外层结果已按完整安装反馈；真实布局和其他 Unit 安装入口继续待验 |
| M09 其他进程 | `PowerKeeperFeatures` / `GuardProviderFeatures` / `MediaFeatures` / `GenericAppFeatures` 及 system_server 的 VPN / 闹钟 / 日志入口 | GuardProvider 无唯一目标 / 异常 / 空 Hook 结果已补齐；截图的完整准备 / 激活与动态格式已补回归；ContentValues / MIME / 图片头、壁纸 / 包 / 用户边界继续深入，VPN 需实机 |

本批 M09 修正：电池温度 `handleMessage`、来电显示 `startUi` 与来电亮度 `onCreate` 的原生调用移至模块 UI 处理的异常隔离之外；原生恰好调用一次，失败立即传播原异常，不读取未完成 owner、不继续修改界面。成功后的温度、显示 / 取消通知与亮度行为保持。安装实际绑定可直接行为验证的回调工厂；共享日志已有致命异常传播能力，沿用而未另造隔离层。没有新 Hook、观察者、线程、任务、快照、ROM 签名或资源变更，按本批 diff 独立回退。

Standard：原有语义的 8 项回调测试中，原生普通 / 致命失败后读取 owner 的两项断言失败，其他控制通过；修正后保留这些断言，另加真实温度刷新和安装绑定两项，10 项全部通过。关联 6 套件共 46 项、零失败 / 错误 / 跳过，JDK 25 编译与源码 / 热路径 / 偏好门禁通过；Feature catalog、语义、运行时约束、地图与偏好资源工具共 76 项通过，文档 / 地图 / 完整 diff 检查通过。证据在仓库外 `customiuizer-a14-evidence/host-failure-before.txt`、`host-failure-before.xml`、`host-failure-after.txt`、相关 JUnit 和 `host-failure-tools.txt`。这次是局部 after 回调修正，未复跑或宣称新的全量 Deep / APK / R8；没有签名、安装、发布或性能结论，真实来电与智能充电界面的 ROM 设备表现继续待验。

本轮接续 相关工程记录（以现行源码与回归为准），base 为上批已推送 `ff87cb476f105d3b030b2a59d2cf2f24302daaa6`，保留同一分支及单一写入者。M04 的 `handleKeyguardDone` / `onFingerprintAuthenticated` 仅在原生成功返回后更新现有认证标志；原生普通 / 致命失败保留原异常、原标志和待处理内部标记，不再让失败满足“先认证一次”的既有条件。成功路径和原有认证方法分类保持，不把此修正写成整套信任解锁、多用户或强认证策略已验收。

M09 悬浮接听只把前台信息作为可选准备：读取失败仍原样执行原生接听一次，真正原生异常不被回退重试；致命准备错误按共享边界传播。只在已确认前台为非桌面且原调用要求显示界面时改写第四参数，其余参数保留，其他路径不复制整组参数。来电亮度使用单份当前设置快照和本地分钟比较，遵循既有振动时段的 `相关工程记录（以现行源码与回归为准），base `7cf4ab85e0ca40bcf7f4aff958edcd66ed37fc83`）：A14 CN 的 miui-framework `InputMethodServiceInjector.addMiuiBottomView` 会从 `com.miui.phrase` 取得插件 Context 并更新 `sClassLoader`；官方 DexSQL 同时确认 MIUIFrequentPhrase 3.5.4 的 `updateGestureLineEnable(Context):void` 为静态方法，写入原生静态 boolean 字段。沿用这些现有目标，不扩展签名或 ROM 范围。原来的回调在原生失败后继续准备插件，并在解析 / 安装前就置为已完成；现在原生成功后才按实际工具 Class 复用 `HookInstallStateMachine`，预检可写静态 boolean、静态 void 方法及类初始化，取得 Hook 后才写标志并提交。缺类 / 空 Hook / 普通失败留待下一次成功的原生创建，致命错误传播并复位安装状态；重入不重复安装，重建只重置已有标志，同名但不同加载器的类独立安装。宿主类缺失的原有诊断保留。

每个实际工具 Class 一份辅助状态，存在现有弱 Class 存储中，不持有 ClassLoader / View；Hook 按框架的原生类生命周期持有，没有新增全局强加载器集合、轮询、监听、线程或延迟任务。安装后首次写字段异常会撤销已取得的 Hook。插件替换的实际释放与长期成本未测，不宣称性能收益。外层 `hookAllMethods` 的共享 Unit 返回语义未在本批重构，其 Feature 结果仍需另作有具体安装点的审查。

GuardProvider 仍用原排除包 / 双字符串 / 唯一目标匹配；无唯一目标返回 `FAILED_PERMANENT`，桥缺失、查询 / 解析错误或未取得 Hook 返回 `FAILED_TRANSIENT`，真正安装成功才返回 `INSTALLED`，Feature 原样传递结果并保留 finally 关闭桥。直接 / 包装致命错误不再被吞掉；目标回调仍每次返回新的可修改空 ArrayList。Gboard 只提取受测回调，原属性及原生异常语义保持，已覆盖独立横 / 竖屏 1 / 100、默认 0、无关属性、更新 / 恢复及仅横屏值启用；没有新增属性重载或扩大输入法包范围。

本批 Deep：冻结源码的 22 项旧行为测试有 13 项失败（输入法 7、GuardProvider 6），相同测试哈希修后全部通过；追加未知 ABI、只读字段、桥缺失、安装绑定与缺宿主诊断控制。最终完整实跑 449 套件 / 4,132 项零失败 / 错误，2 项既定跳过，JDK 25 编译及 Debug lint 通过；最终全量工具 712 项零失败、5 项既定跳过。原工具运行中的唯一失败为 GuardProvider Feature 变更后地图来源哈希未更新；生成地图后仅详细地图校验头变化，4 类 / 689 个入口正文和 256 项过程矩阵保持。相关 65 项另行通过，两份输出确定性通过；提交冻结后的 Feature 语义 / 过程矩阵独立 mutation 以同目录收据为准。

未签名 Develop 的 R8 / fatal lint、min / target SDK 34、arm64、API 102 静态作用域、工程 revision 和 ZIP 16 KiB 通过；最终 DEX 无 compileOnly API 定义，29 个映射设置入口 / 辅助类直接 API 引用为零，setId / getId 各一次归属同产物成员映射中的共享 HookInstallation，replaceHook 为零。APK 4,391,026 字节，SHA-256 `d979e261a327186ebb45ca1ef8a367100b82a64c5564489765304ba4ff9ebfcf`；工程 revision 为本批 base `7cf4ab85`，完整变更由输入哈希和补丁关联。回退单位为本批源码提交；官方 Smali、失败 / 成功 JUnit、全量日志、mapping 与 `apk-receipt.json` 保存在仓库外 `customiuizer-a14-evidence/ime-guard-20261030/`。本批无设备操作、签名、main 合并或公开发行；Gboard 实际取值、底部栏首次布局 / 旋转 / 重建、插件更新释放、GuardProvider 实际目标及设备成本继续待验。

截图 / 输入法外层批（相关工程记录（以现行源码与回归为准），base `794964ec3d7d2f4fdeb3ae37b3caf908f834e575`）：截图旧入口先安装文件名 / 路径 Hook，随后才查保存方法；后续缺失或失败可能留下半套改写并报告成功，保存回调还捕获了安装时格式。现在先一次准备全部 SDK / 实际保存入口，沿用原排除包、日志字符串、至少七参数与第五参数 CompressFormat 的唯一匹配；CN / TW A14 的 MIUIScreenshot 1.4.113 / 1.4.122 官方查询均唯一命中同一完整签名。现有桥生命周期内 JPEG 也准备保存入口，支持页面原有动态格式选择；保存回调读当前格式。各回调的原有路径、质量、缩略图守卫、参数顺序与原生结果 / 异常语义保留；无唯一目标永久失败，桥 / 准备异常或 Hook 缺失按实际结果返回，Feature 原样传递并 finally 关闭桥。未知私有 ABI 保持原生；相比旧 JPEG 分支增加一次启用期查询和一个现有保存入口 Hook，不增加未启用资源或运行期扫描。

`PreparedHookBatch` 仅供截图与输入法外层两个调用点，复用现有 Hook 句柄和 FeatureRegistry。每批一次尝试，每个回调保留原优先级及常量返回语义；一份 volatile 激活标志在所有句柄到齐后提交，安装期间和失败批次保持原生透传。失败逆序尝试释放所有已取得句柄；普通释放失败返回永久失败，原致命错误在完成清理尝试后继续传播，失败实例永不重新激活。每原生 Hook 多一个轻薄回调和一次标志读取，状态随原生回调持有，不保存 Activity / View / Bitmap，不持久保留安装列表，不新增线程、轮询或计时器，不宣称 CPU / 内存净收益。输入法先核对宿主、全部既有 addMiuiBottomView 重载和静态 ClassLoader 字段，完整安装才报告成功，保留缺类 / 缺入口诊断；插件的延迟激活仍复用上一批机制。全局 Unit 门面和其他安装点按各自合同继续审查。

本批 Deep：同两份测试源码哈希下，修前 18 项中 14 项失败，修后通过；新增批次优先级、空常量、逆序回收、失败不复活、普通回收失败和直接 / 包装致命错误控制。Full 实跑 452 套件 / 4,157 项，唯一失败是旧源码检查仍查 changeFormatHook；保留原有透传前置断言，改查实际工厂并增加安装绑定，完整 6 关联套件 / 42 项重新通过。同产品输入的原始通过结果保留，去重验收为 452 / 4,157、零失败 / 错误、2 项既定跳过，原失败与替换 JUnit 分开留存；Debug 编译 / lint、源码 / 偏好 / 热路径检查通过。

生成过程还复现了显式 install() 被误标为默认基类的缺口；3 项冻结抽取测试修前均失败，修后通过，保留 installHook 支持并让显式 install 控制入口证据。重建 256 行矩阵后只补齐 9 条安装实现记录，全部 ID / 开关 / phase / target / 准入 / 拒绝 / routing / scope 保持；地图仅来源校验头变化。最终工具 715 项零失败、5 项既定跳过，相关 40 项和两输出确定性通过；此抽取变更的必需独立 mutation 在提交冻结输入上按原配置验收，不改正式 main CI / 签名 / 发布门禁。

未签名 Develop 的 R8 / fatal lint、min / target SDK 34、arm64、API 102 静态作用域、工程 revision 与 ZIP 16 KiB 通过；最终 DEX 无 compileOnly API 定义，29 个映射设置入口 / 辅助类直接 API 引用为零，setId / getId 各一次由同产物成员映射归属 HookInstallation，replaceHook 为零。APK 4,391,026 字节，SHA-256 `0d64073e4244075ff0ca54112e2d5a7b333823b2c6ead189cfedd075f41ae494`；revision `794964ec` 对应本批 base，完整输入由补丁 / 哈希关联。回退单位为本批源码提交；证据在仓库外 `customiuizer-a14-evidence/screenshot-install-20261030/`，包含修前 / Full / 替换报告、抽取反例、ROM 唯一候选、工具 / 确定性 / mutation 收据及同 APK mapping。没有设备操作、签名、main 合并或公开发行；原生保存、跨设置变更的单次截图事务、URI / MIME / 文件头、路径权限与用户 / 输入法重建仍待深入及同 APK 设备验收。

Performance-First 网速批（相关工程记录（以现行源码与回归为准），base `4fe39c19a79c52d0ae5b7e3f7b43bd3df3188441`）：第二行网速旧回调每次发布都新建并投递一个主线程任务，主线程积压时会顺序提交已经过时的副本。改在原 dispatcher 内维护一份待投递 payload 和一个复用 Runnable；新序号替换待处理值，旧 / 重复序号不回退。投递中再更新只排一次后续主线程轮次，不在当前轮循环追赶；真正投递时才取现有多显示屏 registry 快照并沿用附着 / generation / secondRow 检查。原生 setter 恰好调用一次，失败不读未完成状态或产生副本任务；异步 UI 反射沿用共享 guarded，致命错误继续传播。

范围与成本：沿用原生发布频率和已有 main Handler，不改采样、原生事件、隐藏数值锁存、文字 / 显隐 setter 或第二行视觉。CN A14 原生 `NetworkSpeedView` 已有文字相等及显隐比较守卫，故不增加另一份文字缓存。仅在既有 Hook 安装时按需建立进程所有者的投递器；每个来源事件仍生成原 payload，常态新增短锁同步，UI / 反射不持锁。容量为一个待处理值和一个待执行任务；消费、拒绝排队或异常均清除待处理数据，未启用时不激活投递器，不捕获 View / owner，不新增 Hook、观察者、线程、轮询、计时器或全局调度层。Host 受控积压 30 次的主线程任务从 30 降到 1，修后一次读取当前显示屏快照；逐次提交 / 消费的 100 次控制仍为 100 次投递。该计数不证明设备 CPU、帧率、电量或常态耗时净收益。

同两份新增回归源码及既有 dispatcher 回归，共 17 项修前 7 项失败，修后全部通过；覆盖连续积压、常态交付、旧序号、投递中重入 / 跨线程生产、初次及后续排队拒绝、排队异常、普通 / 致命投递失败、替换 / 迟到 detach 与原生失败边界。静态检查保留原直接 post 支持，并核对有界队列的 main looper、实际 post、guarded dispatch、回调绑定和不捕获过期状态；相关工具 51 项通过。完整 Deep 实跑 454 套件 / 4,170 项，零失败 / 错误、2 项既定跳过；JDK 25 编译 / Debug lint、全量工具 721 项（5 项既定跳过）、文档 / 地图检查通过。冻结源码 `31150ce96271065b8f13f1804ee85c37e352a0da` 上两输出确定性与 11/11 必需独立 mutation 通过，Gate / Cleanup errors 均为零。

未签名 Develop 的 R8 / fatal lint、min / target SDK 34、arm64、API 102 静态作用域及 ZIP 16 KiB 通过；最终 DEX 无 compileOnly API 定义，29 个映射设置入口 / 辅助类直接 API 引用为零，setId / getId 各一次由同产物成员映射归属 HookInstallation，replaceHook 为零。同产物 mapping / 官方 Smali 保留有界投递及实际绑定回调；APK 4,391,026 字节，SHA-256 `dc136f16bc0a39d10a9e8990b640a98e292b0c4b828a657058f901a952ad2cd4`，工程 revision `4fe39c19` 对应 base，验证输入哈希与上述源码提交关联。仓库外入口为 `customiuizer-a14-evidence/network-speed-performance-20261030/`，包含修前 / 修后 / Full JUnit、原生 A14 证据、工具 / 确定性 / mutation 收据及保存的同 APK / mapping。回退为上述源码提交；#92 按源码及回归范围收口。未操作设备、签名、合并 main 或公开发行；真实 ROM 主题 / 多显示屏重建、流量显隐和积压帧时间仍待同 APK 设备验收。

Performance-First 充电样式批（相关工程记录（以现行源码与回归为准），base `93dbb2c785ee3ea55e397b952b4632d1394021e9`）：既有 `setNextIndication` after 和偏好刷新每次调用单 / 多行 setter，即使样式已经正确仍重复请求布局。沿用原 owner、原始样式字段、Hook 和偏好观察者，改用当前 View 的单行标志、有效行数上下限、横向滚动及 transformation 比较；全部满足原 setter 的结果才跳过。后续原生修改任一依赖会再次执行 setter，像素高度模式也能由行数 getter 的 -1 识别；多行模式保留原生最小行数。字号仍走原 SP / PX 与系统缩放路径，不另加换算或字号缓存。

[Android 14 TextView](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-14.0.0_r1/core/java/android/widget/TextView.java#L11063) 的单行 setter 同时协调输入标志及可编辑文本的自动长度过滤器，因此这些视图始终保留原 setter；普通展示文本才使用完整属性比较。CN A14 官方 Smali 确认 `KeyguardIndicationTextView` 直接继承 TextView、无单行 setter 重写；同一 ROM APK 身份与本批原生证据保留在仓库外。没有新增缓存、持有者、监听、任务、线程、Hook 或计时器；额外成本仅为当前原生属性读取，不增加保留内存及释放路径。原生提示调用 / 文本更新、成功后样式修复、原生失败传播和关闭 / 恢复语义保持。

冻结新增行为测试及原有字体 / View 生命周期回归共 40 项，修前 5 项失败、修后全过；相关实际 5 套件 / 73 项零失败 / 错误 / 跳过。Android 34 Host 受控 100 次稳定多行 / 原单行样式，setter 和 requestLayout 均从 100 降到 0；实际 indication 回调的 100 次原生调用及文字变化仍全部保留，模块额外 setter / 布局请求同样为零。另覆盖原生行数 / 像素高度 / 滚动 / 变换修复、原最小行数、可编辑长度过滤器、输入标志、独立 owner 和关闭恢复。该计数仅证明 Host 重复提交 / 布局请求减少，不等于设备布局耗时、帧率、CPU 或功耗收益。

Deep：JDK 25、Debug lint、完整 455 套件 / 4,180 项零失败 / 错误、2 项既定跳过。原工具合同以字符串禁用 maxLines，误拒绝读取当前原生边界；修正为拒绝赋值、复合赋值、前 / 后增减及 Java setter，保留 SP / 数值 / 唯一安装 / 原生 after 等原有约束，并增加读取正例与写入反例。工具验收 723 项、5 项既定跳过（全量通过，补充前置增减反例后完整相关 13 项再次通过；其他输入未变，原失败 / 全量 / 补验记录分别保留）。冻结源码 `ff8ffd43e10d0b62080894082a06f6392d9e0562` 上两输出确定性及 11/11 必需独立 mutation 通过，Gate / Cleanup errors 均为零。

未签名 Develop 的 R8 / fatal lint、min / target SDK 34、arm64、API 102 静态作用域、ZIP 16 KiB 及工程来源通过；最终 DEX 的完整属性守卫、可编辑 / 输入类型回退和唯一 native setter 已按同产物 mapping / 官方 Smali 核对。compileOnly API 定义为零，29 个映射设置入口 / 辅助类直接 API 引用为零；setId / getId 各一次归属映射后的 HookInstallation，replaceHook 为零。APK 4,391,026 字节，SHA-256 `e010ed47402881c476767647327294526e32a1461b7d478a44cccd601d7db133`，工程 revision `93dbb2c7` 对应 base，输入哈希关联上述冻结源码。仓库外入口为 `customiuizer-a14-evidence/charging-style-performance-20261030/`，包含冻结回归、前后计数、Full JUnit、AOSP / A14 原生身份、工具 / 确定性 / mutation 收据及保存的同 APK / mapping。回退为上述源码提交，#93 按源码 / 回归范围收口；未操作设备、正式签名、合并 main 或公开发行。真实 ROM 字号 / 多行表现、主题和配置切换、充电刷新与设备帧时间仍待同 APK 设备验收。

DOCK / DUO / 动态岛暂停交接批（相关工程记录（以现行源码与回归为准），base `dda6e26325cb3f574cf2029a351a5c64211ca647`）：DOCK 零 alpha 暂停原来会取消重试唤醒并清掉待处理标记，恢复时原生矩阵未变便不再补采；在途结果返回还会重新安排不可见唤醒。现在仅在既有 backdrop 增加 rendering 与 retryAt 两个标量，沿用同一回调及一个在途请求；透明期间保留一张纹理，结果只完成有限材质交接。恢复按原 64 / 160 / 400 ms 绝对时刻及剩余次数继续，不提前跳过 backoff、不重置耗尽预算；真正隐藏、来源替换、省电、失败退役及 release 清除旧期限并沿用原回收。采样仍只含壁纸，默认显示屏、容量、刷新率和原生材质单一所有权保持。

用户追加要求静态壁纸尽量少采并保留效果。本批继续复用整张有界纹理，移动只更新映射，最后约 200 ms 保留一次合成器缩放收敛补采；稳定、无映射变化的短暂透明恢复均不重新采集。Host 专项实走 120 帧移动、300 帧稳定绘制、100 次透明恢复，合计 2 次采集（首帧 + 最后补采），稳定 / 恢复新增采集与定时唤醒均为零，玻璃可准备且使用同一纹理，真正隐藏后释放。恢复预算耗尽后 100 次透明往返仍共 4 次尝试、零新增唤醒；不以每帧采集、提高上限或保留隐藏缓存换取效果。该计数不是设备帧率 / CPU / 电量或实际玻璃观感结论。

DUO 只在仍有过渡时复核原生动画开关；途中关闭则下一次真实绘制提交当前中心图案及最终过渡透明度，不再请求后续过渡帧，正常 180 ms 及原有快速变化期限保持。动态岛的实测五柱在关闭动画时直接同步既有 targets / energies，增益、静音及插值中关闭都交付最新值；未变化样本不重绘，不启动插值或轻量模拟时钟。正常插值、波形采样租约与速率、源事件、G4 / Dock 原生圆角矩形、布局和手势保持；没有新增 Hook、监听、线程、回调种类或全局状态。

三份冻结回归共 13 项修前 8 项失败、修后全部通过；关联 9 套件 / 60 项零失败 / 错误 / 跳过，另增上述静态纹理复用控制。Deep 全新实跑 459 套件 / 4,194 项，零失败 / 错误、2 项既定跳过；JDK 25 编译 / Debug lint、工具全量 723 项（5 项既定跳过）、文档 / 地图检查均通过。当前改动集中于三个既有呈现 owner，Hook 声明、路由、生成器及验证器实现保持，原生 ABI 和各来源授权合同沿用已有证据。

未签名 Develop 的 R8 / fatal lint、min / target SDK 34、arm64、API 102 静态作用域、工程来源及 ZIP 16 KiB 通过。按同 APK mapping / 官方 Smali 核对 DOCK 的 rendering / retryAt 与 DUO / 波形实际绘制方法中的动画开关守卫；compileOnly API 定义为零，29 个映射设置入口 / 辅助类直接 API 引用为零，setId / getId 各一次归属 HookInstallation，replaceHook 为零。APK 4,391,026 字节，SHA-256 `daf02f00cbd9fac2d64f1d4bc34f1ce7be5f09e5927578984a218895a44f7957`，工程 revision `dda6e263` 对应本批 base，冻结源码 / 测试哈希与本批提交关联。证据入口为仓库外 `customiuizer-a14-evidence/surface-pause-performance-20261030/`，含前后回归、静态采样计数、Full JUnit、工具 / 构建收据、最终 Smali 和保存的同 APK / mapping。回退单位为本批源码提交，#94 按源码及回归范围收口；未操作设备、正式签名、合并 main 或公开发行。真实 DOCK / DUO / 岛的材质、动画开关切换和设备帧时间仍待同 APK 验收。

动态岛录音包络批（相关工程记录（以现行源码与回归为准），base `0919c9878b6d9e1a7298aa1acc521b33718bb919`）：真实峰值从较强信号降为约 0.005 的稳定弱尾音，再变为零时，0.006 显示差值过滤会让五个旧值一直残留；读取暂时不可用时虽输出新零帧，内部 bars 却仍保存原值，显式恢复同一 Binder 且电平未变便无法重新提交。现在只让真实零值绕过差值过滤，按原五样本顺序排空；读取失败的现有终止分支清空原 bars 并输出它，使保留数据与界面一致。普通遮挡仍保留包络，保留原 owner 标记以确保之后显式 stop 仍清除一次；没有新增字段、缓存、任务、监听、Hook、权限或恢复轮询。

本批按 Standard 验证现有标量更新规则：owner / epoch、线程、取消、Binder 协议与身份、67 ms 目标周期、峰值格式、量化门槛和打包 / R8 边界保持；DOCK 低采样及 DUO 源码未改，上一批证据保留原范围。冻结新增回归及原 Meter 回归共 8 项，修前 2 项失败，修后连同原生峰值格式、来源 owner、波形增益 / 插值 / 可见性等完整 7 套件 / 31 项零失败 / 错误 / 跳过；JDK 25 编译、源码 / 偏好 / 热路径 / 语义、文档和地图检查通过。Host 弱尾音归零保留 5 次有限包络提交，之后 100 次电平读取零新增提交；读取失败后不自行重试，遮挡停止读取，显式恢复只启动原有一次当前租约。该计数不声明设备速度或功耗收益。

证据在仓库外 `customiuizer-a14-evidence/recorder-envelope-recovery-20261030/`，包含冻结源码哈希、修前 / 修后 JUnit、Standard 日志、提交关联与实际计数。回退为本批源码提交，#95 按源码 / 回归范围收口。本批未重跑全量 Deep、构建新 APK / R8、操作设备、签名、合并 main 或公开发行；上一批 APK 保留原来源，不充当本批实现的产物。真实低音量录音、读数暂时失效后的同源恢复和同 APK 设备表现仍待验。

Performance-First DOCK / 动态岛预算批（相关工程记录（以现行源码与回归为准），base `2e22460a6308e008119f0500de10556816706751`）：用户明确允许降低难以察觉的装饰精度与采样频率，优先处理较大的重复采集、整页纹理和逐帧网格工作。DOCK 动态壁纸间隔 34 → 67 ms，像素预算 393,216 → 196,608、边长上限 1024 → 768；频率与像素预算乘积约降至四分之一，不等同设备总开销下降比例。玻璃边缘取消亚像素 RGB 色散，纹理读取 3 → 1，主要折射与高光保留。静态壁纸仍复用整张纹理及真实映射，稳定后不轮询；首次及最后约 200 ms 收敛补采、透明恢复的剩余重试期限和有限预算保持。

页面吸入单次捕获预算约 210 万 → 524,288 像素，宽上限请求 768、结果允许 1 像素取整余量，按四字节像素估算单纹理约 8 → 2 MiB、取消结果加当前捕获约 16 → 4 MiB（不含驱动对齐）。网格 48 → 16 行，98 → 34 顶点、96 → 32 三角形，缓存表随之缩小；曲率查表时间分辨率不变，保留原生进度、前后缘轨迹、窄口与不透明度交接。岛体端帽每侧 G4 32 → 16 段，沿用同一曲率族及物理端帽。FFT 请求 30 → 10 Hz 并服从更低 ROM 上限，录音 67 → 100 ms，五柱插值 33 → 50 ms；这些装饰频率不降低岛体手势或原生窗口响应频率。

沿用现有捕获 worker、当前可见租约、缓存 / 在途上界及释放所有者，不新增设置、线程、监听、预热或常驻采集。默认上岛仍不捕获页面；安全 / HDR / protected 拒绝、等价首帧提交、隐藏原生窗口、取消回退、迟到结果与 deadline 合同保持。预算变化按本批整体回退；设备实际材质、快速手势 / 吸入观感、帧时间及耗电待同 APK 验收，不能把 Host / 预算计数写成实机性能收益。

Deep：等价抽取原预算常量后，冻结新增预算回归 6 项在旧预算上全部失败，修改后保持同一测试哈希并全部通过；原生 Visualizer 安装另验 10 Hz 请求和更低 ROM 上限。既有测试仅按新周期推进虚拟时钟并更新已授权的 G4 段数合同，保留透明恢复、有限重试、静态零轮询、波形隐藏 / 静音、网格无折叠与不透明度交接等断言。完整新跑 461 套件 / 4,205 项，零失败 / 错误、2 项既定跳过；JDK 25 编译 / Debug lint、全量工具 723 项（5 项既定跳过）通过。前后原生 HWUI 玻璃预览保留主要折射与高光，对比仅覆盖去色散的 Shader，不代表降低捕获分辨率后的设备观感。

未签名 Develop 的 R8 / fatal lint、min / target SDK 34、arm64、API 102 静态作用域、工程来源与 ZIP 16 KiB 通过。官方 DexSQL 结合同 APK mapping / Smali 核对降低后的捕获上限、单次玻璃纹理读取、G4 控制点、16 行网格表和波形周期；compileOnly API 定义为零，29 个映射设置入口 / 辅助类直接 API 引用为零，setId / getId 各一次归属 HookInstallation，replaceHook 为零。APK 4,391,026 字节，SHA-256 `33b6b47dde95648f286d6ab9fe8fb7cd5d346d88e056a3c17a254152923c8a6c`，工程 revision `2e22460a` 对应 base，完整应用输入由冻结哈希关联。文档 / 地图及完整 diff 检查通过；证据入口为仓库外 `customiuizer-a14-evidence/surface-major-cost-20261030/`，含前后预算回归、Full JUnit、工具 / 构建、前后玻璃预览、同 APK / mapping / Smali 与来源收据。#96 按源码及回归范围收口；未操作设备、正式签名、合并 main 或公开发行，设备待办继续开放。

Issue 清理按实际验收范围：相关工程记录（以现行源码与回归为准） 的最小集成检索闭环已验证、推送并进入上述 main，已关闭；相关工程记录（以现行源码与回归为准） 至 相关工程记录（以现行源码与回归为准）、相关工程记录（以现行源码与回归为准）、相关工程记录（以现行源码与回归为准）、相关工程记录（以现行源码与回归为准）、相关工程记录（以现行源码与回归为准）、相关工程记录（以现行源码与回归为准） 已按各批源码及回归验收关闭，设备范围继续留在本页。#69–#84 中仍有明确设备验收待办的项目保留开放，旧候选的源码 / APK / 实机证据仍各自独立，不因发布成功扩大验收结论。

## 全分支整合与 VPN 入口

r14.25.9 / 258 已由既有自动化流程正式发布：归档构建记录 与归档构建记录 全部成功。正式源码为 `f18a112cac62f1db63a0bbda3958681f33df483b`；[源码 Release](https://github.com/thetvplus/customiuizer-a14/releases/tag/r14.25.9) 与[Xposed Release](https://github.com/Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14/releases/tag/258-r14.25.9) 使用同一 APK / metadata，均为稳定 Latest。APK 4,395,077 字节，SHA-256 `ca953506bcc06f01a4d5b0ca169d524eb9134d15d346c1ceb86d0101175e64b1`；历史证书、SDK 34 / arm64、来源、R8 与资产收据按发布工作流验收，下载文档同步完成。外部证据入口为 `customiuizer-a14-evidence/release-r14-25-9-20261029/`。后续完成记录仅修改文档，不改变正式 APK 来源。POCO 实际外观、VPN 客户端行为与长期设备成本仍待验。

2026-10-29 用户追加授权正式发布 r14.25.9 / 258，取代下文整合阶段仅本地交付的停止点。准备分支 `codex/release-r14-25-9` 从 main `0de45adc66b7ab5abeab30cda922d8ce04e3588d` 建立；全部现有本地 / 远端分支头已在 main 祖先链，活动 Worktree 无未提交修改。r14.25.8 未公开，按既有准备工具将其有效双语记录累计至 r14.25.9，保留 r14.25.7 及更早公开历史；版本号改为 258。应用逻辑沿用既有整合验证，本次仍必须取得新 exact main CI，随后由原自动化签名及双仓发布流程验收。POCO 实际外观、VPN 客户端连接及长期设备成本仍待实机；不扩大已有验证结论。最终公开状态以两仓 Release / metadata 和 Actions 收据为准。

2026-10-29 用户明确授权合并所有有效分支。整合分支 `codex/integrate-a14-branches` 从远端 main `f3530a3812d2c8da1b898a23eacfbe362866701a` 建立，纳入累计精修 / POCO `564dc02493b57dc319c4ce486f2d405c95605beb`、VPN `a1733da11049020493d1bdedae6cfb936fce40b7`（[PR #74](https://github.com/thetvplus/customiuizer-a14/pull/74)）与 ROM 检索 `349642d749365671d13282eb8eef86e7ad5fb119`。历史动态岛分支的应用树与已公开 main 完全一致，旧说明分支只差后续图片宽度调整，已记录原历史父提交并保留较新的公开说明；没有重新引入被后续实现替换的旧行为。整合后 21 个本地分支头均在祖先链，其他 Worktree 保持原样；实际远端来源与后续 main 读回保存在仓库外 `customiuizer-a14-evidence/integrate-branches-20261029/`。

**功能入口：系统 → 高级设置 → VPN 自动开关。** 此前本聊天交付的 POCO 专项 APK 不含独立 VPN 分支，本次整合包将同时包含 VPN、POCO 与累计精修。首次启用 VPN 功能需重启设备，之后名单、目标和断开延迟可实时调整。源码 / ROM context 查询已反查该设置至 system_server 的实际 Feature / Hook；生成的 256 项过程矩阵及页面地图已从合并源码重建，保留 source owner 和原进程边界。

整合期发现 ROM 工具的可执行文件名使用平台分支，未通过现有 CI 可移植性门禁；改为标准解释器配置提供的本机扩展名，保留显式路径优先和最新版本顺序，并新增 Windows / Linux 文件名规则测试。未放宽检查器或替换测试。应用已执行全新 Deep：442 套件 / 4,074 项零失败 / 错误、2 项既定跳过，含 VPN、POCO、偏好批量发布与设置页面；本次不是套件复用统计。工具执行 712 项通过、5 项既定跳过，7 条 hermetic 命令未修改工作树；两份输出确定性及 11/11 必要独立 mutation 通过。同源码本地 Develop / R8 / fatal lint、整合分支 归档构建记录 均通过。CI 为原生 workflow_dispatch 的整合分支运行，不作为 main 正式发行 CI。

本次 main 合并权限不扩展到公开发行。已启用的 main CI 成功会触发自动签名 / 发布，因此以整合分支完成验证、main 提交保留现有 CI-skip 标记；不改工作流、环境、凭据或既有发布门禁。整合分支 CI 不充当正式发行所需的 exact main CI。已从冻结源码 `d409a667cf7312066e8c3d6ab60cbfb90278bfb6` 生成本地 r14.25.8 / 257 正式签名 Release，4,395,077 字节，SHA-256 `e5bc83e305e35220f01ca98581bf7342902c8fed20bfbf8982ed9b9089727bcf`。历史证书 / V2、完整 sourceCommit、SDK 34 / arm64 / API 102、不可调试、ZIP / ELF 16 KiB、同产物 21 个映射设置 / 辅助类的直接 API 隔离及 POCO 冷准备链通过；两个 PIN 回调边界仍在。交付到本机下载目录 `CustoMIUIzer-A14-r14.25.8-d409a667/CustoMIUIzer-A14-r14.25.8-integrated.apk`，复制后摘要一致；候选 / mapping 与 `artifact-receipt.json` 在上述外部证据目录。最终 main 与签名源码只允许交付文档差异，以 `final-receipt.json` 的远端完整 SHA 读回为准。设备安装、VPN / POCO 实际效果与成本仍待验。

## VPN 自动开关

**决定：** 系统 → 高级设置 → VPN 自动开关。单选一个客户端或一项系统 IKEv2 配置，触发应用可多选。任一选中应用打开就触发，已有连接不重复启动；只在全部已触发的最近任务移除后开始倒计时，返回桌面、临时切换和系统回收进程均保持连接。自动关闭独立控制，延迟为 0–30 秒、每秒一级、默认 3 秒，0 秒立即断开；修改延迟计入已等待时间，重开取消，重新开启自动关闭从新时刻计时。手动原有连接始终保留，手动断开后需所有触发任务结束并重新打开才恢复自动连接。首次启用需重启，之后规则可实时调整。

**Review 修复：** 原记录按单个 taskId 覆盖不同触发包，共享任务再修改名单可能提前关闭；现在按 taskId / 包名 / 原生 Task 身份维护，任务 ID 重用后的旧移除也不能删掉新任务。原配置对象比较会丢弃名单未变时的排队退出事件，监听线程较晚投递的旧快照也可能覆盖新设置；现在应用时读取最新完整偏好，退出按原生任务身份校验。同一目标保留当前会话，换目标保留仍有效的触发任务。原来连接中关闭功能 / 更换目标会丢掉尚未落地的请求；现在最多一条原 20 秒期限内的启动票据负责收尾，新启动等待其结束，快速关闭再开启同一目标可复用请求。总开关和自动关闭同时修改时以最新自动关闭值为准，关闭自动断开后也不会由旧清理请求误关连接。

**所有权与成本：** 仅主用户；不接管原有、always-on 或 lockdown VPN，不改写系统常驻设置、不 force-stop、不新增应用注入。取消进程退出 Hook，只复用原生应用恢复、最近任务移除、VPN 状态事件及系统 BackgroundThread。256 个配置应用、128 个任务 / 应用记录，溢出保守处理；一个下一次唤醒，待连接票据最多一个且不延长期限，没有轮询、心跳或重复重试。断开前在系统锁内核对 Connection / VpnRunner 身份，系统配置 / 网络读取先于锁内提交；第三方需已配置并授权。应用启动不等待 VPN，不提供首包阻断。没有设备 CPU / 内存测量，不声明性能收益。

**验证与来源：** 同任务覆盖缺陷已由旧实现的行为测试失败确认。本轮 Deep：JDK 25 编译、Debug lint、3,925 项 Host 测试零失败 / 错误、2 项既定跳过，包含 32 项任务 / 延迟策略及 13 项实际指令协调回归；设置页实走 0 / 30 秒持久化与独立开关联动。工具 679 项通过、5 项既定跳过，2 个生成输出确定性和 11/11 必要独立 mutation 通过。日志在 `.di-evidence/vpn-auto-switch/review-*.txt`。CN / TW A14 已有原始签名、字段和关键方法体证据继续适用于保留的原生入口，不把静态证据写成实机通过。对照 [VpnAutoSwitch](https://github.com/yaoiwi/VpnAutoSwitch/tree/350de9c55655de25843554fead4c73c8f378f318) 的生命周期 / 手动关闭保护，并核对 [Android VPN](https://developer.android.com/develop/connectivity/vpn) 与 [WireGuard 的显式连接控制](https://github.com/WireGuard/wireguard-android/blob/master/ui/src/main/java/com/wireguard/android/model/TunnelManager.kt)；融合相应交互原则，不复制常驻检查机制，也不宣称所有客户端都支持通用启动。

**产物：** 本轮 Develop / R8、fatal lint、16 KiB 对齐与来源检查通过；SDK 34 / arm64 / API 102，Hook API 定义与同产物 mapping 对应 18 个设置入口 / 辅助类的直接 API 引用均为零。未签名验证 APK 4,358,258 字节，SHA-256 `4dfe5c32b1df404fec296a23979e2590dac17c58b3dcc1e41ab4814fac80d637`。工程 revision 为 base `baea19ac`，应用 / 测试 / 语义输入由 `review-verified-source.patch` 及 `review-apk-checks.json` 固定，非正式签名 / 发布，不为文档更新重建同一源码产物。

**待验：** 尚未操作或安装设备。系统 IKEv2、各客户端后台启动 / 原生撤销、网络恢复、多窗口、用户切换和实际资源成本仍待实机。第三方异步启动没有通用确认协议，启动窗口内同客户端的并发手动连接仍有归因边界。

## 全局 RC 审计与候选收口

本轮审计采用双仓 r14.25.5 / 254、源码 `df83993f6ac7633044a59794e32ff8718c0dd4cf` 作安全对照；当前公开稳定版已为下述 r14.25.7。审计接续的有效 HEAD 为 `090184096f8f1c21aafaad1c0ee2b2433c1f2af3`，已含动态岛 `4e690103` 与 Dock 最新审查修复，没有待合入的专项源码。独立 ROM Catalog Worktree 只作只读工具 / 证据来源，不混入应用候选。原有七份暂存原生技能文件保持原样并排除在候选提交之外。对照范围是稳定版到候选的完整有效差异，已有专项源码与可信记录按输入哈希复用，新增集成修复补验。

**当前状态：r14.25.7 已双仓正式发布；ROM 验收结论仍为 CONDITIONAL。** 2026-10-28 的签名发布运行 `40695177459` 已成功，公开源码为 `fe92d1a9cba6541566f87cae853777e943c760e7`；两仓 APK SHA-256 均为 `fb91a947bed17074b7fe16ce3862a13a7c93314bcfb7bda20e49b53114dc24b0`，元数据摘要亦一致，原 Tag、来源与历史证书保持。没有已知未修复 P0；单台小米 13 已留存秒表 / 音乐返回、揉动与退出的实际录屏，用户已认可当前动画。横屏、POCO 模糊、其他 ROM 与最终发行包的完整跨模块设备验收仍未完成，发行成功不等于全部 ROM 已通过。本地试用包与原候选证据保持各自身份；发布后整理只更新文档和公开说明，按用户要求跳过 CI，不重建、替换或重新签名 APK。

当前已发布版本为 **r14.25.7 / 256**，直接累计 r14.25.5 后仍生效的变化；r14.25.6 的既有本地试用证据保留原身份，不晋升旧包。本节是当前结论，后续专题中的旧 SHA / APK 及当时通过范围按原输入保留。

当前长 / 短岛是同一当前来源的内容尺寸选择：音乐与全部持续系统活动统一短岛，通知按独立正文选择长短；直接入场、一次上滑退出，下滑不展开；常驻小岛只作可选空闲占位。早期“先收短再退场”“下滑展开”和“音乐手动详情”交互已撤销，旧回归及试用记录只保留各自历史输入，不列入本候选有效更新。

用户随后要求持续活动统一短岛、横屏也正常上岛并加强安全区。已按当前显示窗口重读几何，横屏用普通内容过渡、清理旧原生返回等待；宽度及弹性帧上限 240 dp，左右至少 48 dp 且原生侧边安全边界再留 16 dp。竖屏规则及保存值保持；旧 ae56e91 候选的本地签名构建已停止，未作为最终试用包交付。这次运行时变化另做完整 Deep，旧完整结果不覆盖新横屏行为。

短岛 / 横屏的最终实现已复验：428 套件 / 3,869 项 Host 测试零失败 / 错误、2 项既定跳过，Debug lint 通过；679 项工具测试通过、5 项既定跳过。修前五个行为反例失败，修后覆盖六类活动、左右挖孔、两个横屏方向、锁屏、竖屏恢复、自动 / 手动宽度、三种对齐、逐帧 240 dp 上限与迟到的竖屏返回映射释放。没有新增 Hook、监听器、后台计时器或预热任务；真实横屏截图 / 录屏、触感与帧时间仍待最终同 APK 设备验收。

### 动态岛全量视觉平衡

用户在本地 `be6316f7515a0e6fc6383d384de4478609edd78c` 试用后反馈整体太快、回弹太小。确认上一轮同时压缩了原生页面响应和岛体前 28% 准备段，额外宽度又受 8 dp 封顶；即使调整曲线，完整岛体也缺少可读的回弹幅度。该候选的 .9 倍节奏、前 8% 窄口及前段脉冲由本节替代，下节保留原输入的证据。

| 评估范围 | 本轮处理与边界 |
| --- | --- |
| 普通入场、长短形态、退场 | 入场 420 ms、普通形态至多 400 ms、次级交接至多 440 ms、退场 320 ms。普通中心首次入场允许有界弹性，顶部样式保持纵向。直接进入当前形态，同尺寸不追加大幅弹跳，保留一次上滑退出与静止小岛 |
| 吸入、首次 / 再次返回 | 页面名义响应约 633–648 ms；接收岛体以自己的 760 ms 已用时间完成接纳，仍共用唯一有限时钟，进度只确认原生接入 / 完成。立即轻微收紧，再完整撑开 / 挤压；迟到通道从当前状态收敛，快页面不截断、慢页面不重放 |
| 弹性与揉动 | 额外宽度最多为本体 12% 且不超过 20 dp，并取两侧安全空间；吸入外撑准备约 9%、12–18 dp，松手 400 ms。位置 / 速度连续、反向接管、摄像头遮罩及横屏总宽 240 dp 保持 |
| 内容与排版 | 封面在吸入行程约 28–70% 渐显，减少展开后空槽。内容淡入和文字揭示随形态放缓。原生均衡断行消除短中文正文最后只剩一个字的尾行；不新增字体、连字符或每帧排版 |
| 静态视觉与可访问性 | 复核音乐、加载、通话、闹钟、倒计时、秒表、录音、隐私、消息、静止小岛、横屏及中 / 英 / RTL、1.6 倍字体的原生渲染；保留 G4、原色阶、图文物理尺寸及关闭动画策略。明暗背景图像仅证明当前 View / 不透明回退，真实 ROM 玻璃与壁纸合成仍待验 |

资料采用 Android 弹簧动画与 Apple Motion / WWDC18 的连续响应和可打断原则，链接见动态岛功能页；数值由本项目渲染校准，不套用外部平台实现。原生 Skia 评估以 60 / 90 / 120 Hz、420 / 650 / 900 ms 页面和首次 / 已有岛体组成的 18 组检查独立节奏、峰值、边界及终止释放。160 dp 目标的主机对照：旧峰值 165.69 dp / 183 ms，新峰值 172.42 dp / 466 ms；这是模型几何，不是设备帧率或手感结论。

本轮最终 Deep：429 套件 / 3,877 项 Host 测试零失败 / 错误、2 项既定跳过，Debug lint 通过；完整输入相同的 679 项工具测试（5 项既定跳过）、11 项独立 mutation 与矩阵确定性复用既有可信结果。终帧三分之一像素以内的原断言保留并扩展到首次弹性入场及新时长。证据位于仓库外 `customiuizer-a14-evidence/island-visual-balance-20261028/`，包括修前反例、前后逐帧图 / JSON、内容矩阵与最终同提交构建 / 签名收据。没有新增 Hook、窗口、捕获、动画器或常驻调度。Host 校准后已接入 Android 14 / arm64 设备并获安装、Root 与录屏授权。最终同一 APK 的实际命中、动画录屏、性能采样及恢复 / 锁屏读回以本目录设备证据和 final-receipt 为准，不能用 Host 图像替代。跨 ROM 与公开晋升条件继续独立，发布结论保持 CONDITIONAL。

### 连续来源交接与发布准备

保留用户已认可的视觉参数，仅修正可复现的 A → B → A 交接：B 尚未显示时，旧代码仍把 A 排队淡出后重新绑定，重复停止 / 启动时钟、文字测量和几何准备。现在在内容、图标身份和录音状态完全一致时取消未显示的 B，复用 A 的现有 View，从当前透明度恢复；正在进行的封面翻转仍由原流程收尾。动作、当前来源校验、IO 启动与可取消任务不变，没有增加防抖丢弃、缓存、监听或后台调度。收益限于该分支省去重复绑定，未测量 CPU / GPU / 帧时间改善，不宣称整机性能提升。

修前行为反例失败，修后覆盖 12 次快速往返、当前透明度接续与旧动画完成回调；关联 168 项回归零失败 / 错误，Debug lint 通过。沿用相同输入的上一轮完整 Deep / 工具证据，不因文档或提交重跑全部验证。证据继续位于仓库外 `customiuizer-a14-evidence/island-visual-balance-20261028/` 的 `hotpath-*`；新提交的 Release / R8、设备压力录屏与合并后 main CI 结果不得由这些 Host 结果代替，按用户停止点保留待验。

发布后[简版说明](../releases/r14.25.7.md)以七项用户重点和一张暖纸色图呈现，完整二十三项变化仍保留在原双语 Changelog 及发行源码 Tag 中。演示使用生产 View / 网格和通用页面经代码渲染，不含用户桌面、通知或应用内容，不作为 ROM 录屏或帧率证据。图片托管在仓库文档中，两仓仍只保留原 APK 和元数据两项发行附件；精简公开文案保留原始来源与校验信息，不改写发行 Tag，亦不重跑原候选发布来覆盖这次编辑。

### 吸入首帧与揉搓接续

接续上轮已交付的 `cb82a73fcf430ccf5f0d17df4e742ff1e56f6679`，仍为 r14.25.7 / 256。用户反馈首次打开后的返回较自然，从岛打开再返回却像迟吞；源码确认已有完整岛体的宽度起终点相同，旧反馈又等后缘完全走完后才开始，首次的小形态生长掩盖了这个缺口。现统一以已接受原生返回的整体进度驱动宽高，捕获尚在准备时也同步反馈；先收紧、再撑开 / 挤压，完成从当前状态阻尼收敛。吸入落点独立缩小并下移，默认原生落点与节奏保持。此前专题中的“完整吞并后才外弹”、750–810 ms 和共用 84% / 86% 吸入落点属于被替代的历史输入。

定向对照 `e1b5966`、`03fb76b`、`aa568c1` 以来的手势实现，沿用有界阻力、当前形状接管、原生近期速度与有限欠阻尼回弹。后续曾将横向基础形变由 10% 缩至 4%，却保留 4 dp 平移；本轮将基础拉伸调为 8%、横向伴随柔性挤压，并撤去附加平移。松手速度由相同位移函数的导数转换，边界处不注入继续越界的外向速度；反向揉动、挖孔遮罩及横屏宽度上限保持。参数集中在既有 IslandMotion 内，不增加设置项、Hook、监听、捕获次数、动画器或后台循环。

修前的真实 View / SharedMemory 行为反例已复现“吸入过程中岛体无纵向反馈”；修后同时覆盖首次与重入、60 / 90 / 120 Hz、缩小下移的完整窄口、安全宽度耗尽、终止帧连续性及左右揉动 / 反向接管。最终完整 Deep 为 428 套件 / 3,875 项 Host 回归零失败 / 错误、2 项既定跳过，Debug lint 通过。工具、发布链、catalog / matrix 和生成器输入逐项保持，复用上轮 679 项工具测试（5 项既定跳过）、11 项独立 mutation 与确定性结果。验证、最终同提交 APK / R8 / 签名与远端完整 SHA 收据保留在仓库外 `customiuizer-a14-evidence/absorption-intake-20261028/`；新版本的真实录屏、手感和设备性能仍待验，不以 Host 几何或构建通过代替。

### 已留存更新的评估

| 留存成果 | 本候选决定 |
| --- | --- |
| 动态岛、Dock 与早期动画 / 性能分支成果 | 4e690103、09018409 及旧 Worktree 的 b6eac396 / 013d5894 均已是当前祖先；保留已核实修复及失败反例，不重复 cherry-pick 或恢复旧代码 |
| r14.25.6 本地签名试用包与设备记录 | 仅证明各自 exact 输入和已测场景；故障包及后续已替代候选不晋升，不将旧录屏 / 短采样扩为最终 APK 通过 |
| ROM Catalog / Agent context 工具分支 | 349642d 为独立离线工具成果，尚非当前祖先；本轮复用已存在索引 / 原始样本作只读取证，不把工具分支整体混入产品 RC，也不借其自报证明应用通过 |
| 七份原生界面技能暂存文件 | 工程指导资料，与本次运行修复不同；保持原字节与暂存状态，不打包、不据此宣称视觉或设备验收 |
| 延后 / 撤销方案 | 不恢复故障时钟递归、POCO 全局身份伪装、无来源的宽泛 Hook、无限采样重试、未经证明的总几何缓存或额外常驻调度；有效用户变化已并入双语候选记录 |

### 问题与修正

| 级别 / 状态 | 触发、证据与影响 | 候选处理 |
| --- | --- | --- |
| P0 · 既有修复纳入 | 早期候选在共享进度回调内同步 end，设备 mapping / 堆栈和实际 A14 ValueAnimator 回归确认递归栈溢出；原生媒体卡片缺口还曾在 Main 同步枚举会话 | 保留终止前解绑自身更新回调、既有可取消 IO 快照及安全发布；不以旧 APK 的实机结果代替本候选验收，详见原动态岛证据段 |
| P1 · POCO 接入修复 | POCO 包不在静态 scope，且 ProcessRouter / LauncherPostAttachFeature 只把 com.miui.home 送入 Launcher catalog；此前“共用 Launcher 目标包含 POCO”的说明不成立 | 仅新增 com.mi.android.globallauncher 的主进程、显式开关入口。复用 Application.attach 与唯一的新 Feature ID 261，仍走 GENERIC_APP，绝不扩大整个 Launcher catalog；路由及矩阵回归覆盖关闭、子进程与其他桌面 |
| P1 · 计时 owner 修复 | TimerService.onDestroy 的真实 DEX 不清 mTimer；模块先 snapshot 再清 owner，随后无 owner 的 snapshot 又不撤销 TIMER，进程存活时残留计时状态。修前行为回归确认销毁后仍发布旧计时 | 先清当前 Service owner，再发布无计时状态；过期旧 owner 不影响新 owner。保留到期三秒交接和已经响铃的提醒，覆盖运行、暂停、旧 owner 及提醒衔接 |
| P1 · 返回主 Hook 组修复 | 目标覆盖成功后，attach / detach / touch / animTo 安装返回 null 原先被忽略；可能只创建 owner 却缺少取消、释放或动画配套。此前可选吸入网格的回滚不覆盖主组 | 主组安装失败即停止并逆序撤回全部新增 handle；fatal 保持身份且继续清理。可选吸入网格仍独立回退。逐位置拒绝及 fatal 清理回归验证，未修改动画参数或原调用次数 |
| P1 · 发布准备修复 | 源码已为 r14.25.6 / 255，但 release-policy 仍只允许 r14.25.5 / 254；近期条目在 Unreleased 中，正式 Notes 生成器不会收录 | 下一目标明确为 r14.25.7 / 256，说明与安全基线为 r14.25.5；保留旧版累计基线。双语候选章节收纳有效变化及本次修复，历史记录不改写；工作流、签名和 main 门禁不放宽 |
| P2 · 已有 Dock / 岛修复纳入 | 屏外采样自续、透明全宽帧监听、相同内容重复提交、旧 alpha / key 租约、斜滑仲裁与 attach listener 反向持有 | 复用上一轮完整源码哈希、Host 反例与修复证据；本轮只复核共享接入及失败回退，不二次改造几何、材质或帧时钟 |
| P2 · 保留的成本风险 | 全局 Animation.applyTransformation 入口与有视觉模式的 PreDraw 仍需跟随原生帧；文件夹局部门禁仍读取预解析反射成员 | 无新的窄回调或设备净收益证据，不猜测移除、不新增轮询或预热；不把静态预算、Host 次数当作设备 CPU / GPU、帧时间或续航收益 |

### Hook 精准度与 ROM 边界

完整功能路由继续使用既有 [255 项进程矩阵](../rom-intelligence/A14_PROCESS_MATRIX.csv)，逐项保留 ID、包 / scope、阶段、开关与 installHook。它证明 SOURCE 路由，不代表 255 项均已取得 ROM 或运行时证据。当前 Hook / 反射调用点归属扫描、完整功能语义、类加载 / 异常边界与 Host 回归交叉校验；未命中样本的项保持 UNKNOWN，不批量标成兼容。以下为高风险集成层的人工审查矩阵，完整描述符、静态标志、定义来源及调用者保留在外部证据目录。

| 功能 / owner | 实际包、加载器与阶段 | 精确合同、安装 / 回退与证据边界 |
| --- | --- | --- |
| 基础安装 | system_server 启动；SystemUI 主进程 package-ready；Application.attach 后按功能选择 | 固定 ID systemui.init / systemui.plugin.create / application.attach；同 executable 的 ID 回读不替代 Feature 单次安装。PASSTHROUGH、原异常与 proceed 次数保持；R8 / settings 类加载边界单独检查 |
| SystemUI / 插件 | com.android.systemui 主加载器；PluginFactory 返回的实际插件加载器 | 插件不单独加入静态 scope；init / createPlugin 声明及 ClassLoader 分离沿用已有证据。状态栏、通知与控制中心不共用可变 View owner |
| 动态岛媒体 / 通知 | SystemUI；NotificationMediaManager / Heads-up / Keyguard 原生 owner | 核验 token、用户、UID、会话、安全状态与 generation；卡片缺口 IO 结果不得复活过期来源。新快照保留逻辑租约，通知接管失败保留原生；已有专题 ARTIFACT / Host 证据复用，最终运行命中待验 |
| 原生时钟 / 录音来源 | com.android.deskclock 主进程；com.android.soundrecorder:remote；各自真实加载器，package-ready 安装 | 时钟只 Hook 已声明服务和状态入口；录音按语义字符串与完整签名唯一定位，DexKit 总是关闭。先校验框架发送 UID / 包再解包，PendingIntent creator 与存活 Binder 受限；服务、进程死亡和 SystemUI 关闭分别释放，不新增音频权限 |
| Launcher 返回 / 吸入 | 仅 com.miui.home 的 post-attach | WindowElement.animTo(Object)、NavStubView 原生目标 / touch / attach / detach、可用 Home owner 全部预解析并按组安装；可选网格另有回滚。POCO 样本缺 WindowElement，维持原生返回，不声称吸入支持 |
| Dock | com.miui.home post-attach；HotSeats / 内容 / 原生动画 owner | 已核实 CN / TW 及 munch 声明不等于给 POCO 安装 Dock；容量 666 仍参与布局解析和拖放容量语义，不替换。材质只有一个可见填充、采样 generation / 有限重试与 detach 释放保持 |
| MIUI 文件夹图标 | com.miui.home post-attach | Feature 260；BlurUtilities.isBlurSupported()Z 与八个文件夹入口；先核验精确参数、返回、静态性、继承和两个 Consumer / lambda ABI，最后安装门禁，失败逆序回滚 |
| POCO 文件夹图标 | 仅 com.mi.android.globallauncher 主进程，原应用加载器，post-attach | Feature 261；4.39 与已核验的 5.39.40 补接原生 ShortcutMenuLayer 采样，分别保留原文件夹范围 / isFolderBlurSupported(boolean)、默认图标策略及系统模糊条件。原生颜色、半径与清理保留，偏黑混合层只用于图标底材；不安装 Dock、返回吸入或展开背景 Feature |
| 控制中心 / 锁屏 / 输入法键 | SystemUI 宿主加载器；各自 Feature 开关 | 四份 SystemUI 样本均核实 QSTileImpl 的 click / longClick / secondaryClick(View)、NavigationBarView 及 ButtonDispatcher 返回、KeyguardBottomAreaInjector 图标更新、shouldShowChargeAnim()Z；锁后重新核验展开请求，弱 owner 观察者不续持旧 View |
| 状态栏 / 日期 / Duo | SystemUI 主加载器；各自可见 View / controller | 四份 SystemUI 的日期 / Clock ABI 和资源取证沿用相同输入记录；日期单独启用也准备格式能力，秒钟任务仍按可见需求运行。Insets / 避让分别恢复当前所有者的原生基线 |
| system_server 输入 / 安全 | boot / services 实际加载器，SYSTEM_SERVER_STARTING | 音量策略、分享过滤、应用锁等按原用户 / token / 锁和失败语义核验；新增代码不扩大 Handler / View 通用 Hook。未变化且已有可信 ROM 合同复用，未覆盖厂商组合保持待验 |

### POCO 文件夹模糊完整性

反馈时间线已按用户澄清区分：设备 / Launcher 截图提供于发布前，「安装最新版后开启仍无效果」是后续反馈。截图确认 POCO F4、HyperOS `1.0.8.0.ULMIDXM`、Android 14，Launcher 为 `RELEASE-5.39.40.11739-10131736`；截图本身不证明新版安装、Hook 命中或渲染结果。当前任务在独立工作分支准备 `r14.25.8 / 257`，仅授权本地正式签名交付，不自动公开发行。

旧证据继续保留：fuxi CN / TW MiuiHome `RELEASE-4.39.30.8604-08262248`（SHA-256 `a6546d51d9220039ed7ac143de249e9014202cfafeafd715237eb49f8f5a3f7b`）和 munch POCO `RELEASE-4.39.14.7603-03051601`（`1b1eb43b4279ab6b6e7ec1712c74b7e7e45c5e00cee4df497aa6bf4b55ab81f6`）仍走原有八个入口的线程范围，MIUI 行为不变。新增下载并验明小米签名的 POCO `RELEASE-5.39.40.11641-08181627`（`597d175f1d21a111c2f1cbc2a0a7094fd601a9d21e4c40bd9176f5865047d891`）与截图对应的 `RELEASE-5.39.40.11739-10131736`（`bb6e171396af80aa70523d1677fd44caa1cfb9040c507b2a61b8b710ae1c2c6f`）。两份 APK 的包名、SDK 34 与 arm64 身份已核对；来源和原始样本仅保留在仓库外 `customiuizer-a14-evidence/poco-folder-feedback-20261028/`，不据此扩展 HyperOS / Android 范围。

**已证实的安装缺陷**：5.39.40 删除了旧 `onWallpaperColorChanged` Consumer / lambda，原安装器在解析第六个入口时抛错，整组 Hook 不会安装；FeatureRegistry 会记录安装失败，并非已命中后的静默无效果。其实际调用链还从 `isBlurSupported()Z` 改为 `isFolderBlurSupported(Z)Z`，模糊设置器由 View + 5 个 int 改为 View + 10 个 int。原生开关仍来自硬件属性和 Secure.background_blur_enable；新版默认文件夹条件按 1×1 / 2×2 分别判断，不能用旧 isDefaultIcon 近似替代。

新版只接管原生文件夹门禁，硬件 / 系统开启后调用原生 `DeviceConfig.isUseDefaultFolderIcon(boolean)`，其它 `isBlurSupported()` 用途保持原生。post-attach 用真实 Context 冷读完整版本用于诊断，按完整接口形态分流，不设版本白名单；其它版本（例如 5.39.39）只有全部门禁、参数、材质及生命周期预检通过才安装，未取得其 APK 不列为已验证。未知 ABI 在注册业务 Hook 前失败并保留原生背景，不采用猜测式兜底。能力值只在安装与原生 setBackgroundBlurEnabled 发布时读取，主题策略不冻结；不新增系统属性写入、采样、监听或后台任务。完整预检后，对 DexSQL 核实的 20 个门禁直接调用者及一个系统状态发布调用者在安装期去内联，覆盖构造、背景回调、绘制及原生图标克隆 / 过渡，防止声明存在却绕过 Hook；系统状态发布入口分别兼容旧 onCreate_aroundBody0 与新 onCreateBeforeSetCreatedFlag。三个 Hook 最后启用门禁，部分失败逆序回滚。去内联和 Hook 的设备 CPU / 帧成本尚未测量，不作性能收益结论。

偏黑材质与模糊能力分开处理：仅原生设置器的目标 View 属于 FolderIcon 时，将第一层 dark / light RGB 改黑，保留 ROM 的两套 alpha、混合模式、第二层色调、圆角 / 裁切和动画模式；不新增 Drawable 或材质 owner，不把不透明黑底当模糊。原生门禁也供图标在拖拽 / 展开中的克隆和透明度策略读取，沿用其原生逻辑；不改动画时长、几何或展开后的面板底色。展开桌面背景属于另一个 Feature 及 BlurUtils.fastBlurWhenOpenOrCloseFolder / fastBlur / getLauncherBlur，POCO 本次不安装该 Feature。

诊断分层：原有模块版本 / revision 加载标记 → `FolderIconBlur installed profile=...` → 每种尺寸和门禁结果一次 → 首次原生材质请求返回。`native-render-returned ... visible=unverified` 只证明调用返回，不能证明系统合成器实际显示模糊；普通背景覆盖、深浅模式、亮暗壁纸、系统模糊 / 省电切换、重复进出、拖拽和实际黑色融合仍需在最终 APK 设备复测。当前支持结论仅限上述具体 ABI / 样本，不能声称整个 5.39.40 系列或用户设备已修复。

2026-10-29 按「仅复用 ROM 原生模糊」收窄后续方案。哈希一致的 munch / Android 14 框架与 POCO `5.39.40.11739` 原件确认：`BlurUtilities.setContainerBlur / setBlurRadius` 经反射调用 `View.setMiBackgroundBlurMode / setMiBackgroundBlurRadius`，再进入 `RenderNode` 的原生背景模糊；该 ROM 的 `libhwui.so` 含对应 JNI 名称。框架另有已挂载 View 的系统 `BackgroundBlurDrawable` 入口，均无需外部模糊库。OS3 的增强 / 渐变接口在该 A14 框架缺失，不移植其库或 JNI。该次取证时模块仍由宿主执行原生文件夹材质，仅调整既有门禁与选中混合色；尚未另行启用模糊容器或采样半径。接口 / 原生能力不满足时保留宿主表现，不设计截图、滤镜或额外背景 owner。本轮仅核对原件与记录决定，功能源码和 APK 未改，实际设备合成效果未验；输入摘要、精确描述符和 Smali 在仓库外 `customiuizer-a14-evidence/poco-native-blur-20261029/`。

2026-10-29 收到 POCO `RELEASE-4.39.14.7601-12051054` 仍无模糊、旧版主观更流畅的新反馈。本轮按此前文件夹图标底板范围核对能力，不把打开文件夹后的整屏背景混入。此前已验证的 `4.39.30.8604` 是 MIUI 桌面样本，不作为要求 POCO 用户寻找 / 更换版本的依据；旧版流畅反馈也不等于已测量其模糊成本。精确 7601 原件下载未成功（TLS / 下载链接 / 限流），不能以 munch 的 7603 代替该版本的兼容结论。

新增确认的静态缺口：哈希匹配的 POCO 7603 与 11739 中，`Utilities.isMiuiLauncher()` 均直接返回 false；`ShortcutMenuLayer.onLayout` 经原生 `isBlurSupported()` 判断才调用 `setContainerBlur`，修前模块的文件夹范围 / 新版独立门禁没有接管这条容器路径。两版 `setFolderIconBlur` 只设置图标的原生 View 混合、颜色与圆角，不设置背景模糊模式或采样半径。因此即使图标门禁已放开，仍可能没有底层采样容器，不能把设置器正常返回当成可见模糊。两版容器都使用 `shortcut_menu_layer_blur_radius`（样本默认 50 dp）、模式 2 与跨窗口采样；各自 `onDetachedFromWindow` 已调用 `clearAllBlur`，后者清除混合、背景模式、半径和跨窗口状态。对应完整描述符、Smali、资源及输入 / 源码摘要在仓库外 `customiuizer-a14-evidence/poco-blur-container-20261029/container-gap-proof.json`。

上述能力取证时尚未改变功能源码或 APK；用户随后明确优先范围为桌面文件夹图标底板，已从 `c548a915fb7e23b51c41a829f7eadf2e0996407c` 接续 相关工程记录（以现行源码与回归为准） / `codex/poco-folder-container` 实施原生容器补接。保留硬件、系统开启与原生默认图标策略，按完整 ABI 预检 4.39 及两份 5.39.40 容器、资源与清理接口后才安装；不全局伪装 MIUI，不加入截图、第三方模糊库或独立材质 / 动画 owner，不安装打开文件夹后的背景 Feature。原生挂载且硬件加速、尺寸有效的当前 ShortcutMenuLayer 在布局前准备模式 2 / 跨窗口采样，使用实际 View 资源半径；旧接口保留深浅混合色及原生多选时的 contain-below 设置。Android 14 原件中的 setPassWindowBlurEnabled 返回 boolean，按真实声明预检，不误按原工具调用中丢弃返回值的 void 假定。

新增状态只保留一个当前 owner 的弱引用、原生输入标量及最多一个用于非主线程状态发布的主线程刷新；没有计时器、帧监听、Bitmap 或常驻捕获。半径、深浅 / 多选输入不变时复用准备结果，原生清理使缓存失效；状态关闭清理自己准备的容器，重开请求自然布局，临时未挂载 / 尺寸未就绪可随布局重试。旧 owner 留给自己的原生 detach 清理，迟到的布局 / 清理不能释放新 owner。原生布局 / 状态发布 / 清理失败仍保留调用次数和异常 / fatal 边界；部分写入失败停止新增门禁和黑色材质改写，保留清理责任并允许后续恢复。全部 Hook 同组回滚、门禁最后启用；4.39 的八个已验证范围与状态发布调用者补安装期去内联，MIUI 默认调用仍不安装容器或新增 Hook；安装失败只撤销已安装 Hook，已完成的去内联不由模块反向恢复，设备成本未测量。

27 项新回归全部通过；同一测试哈希下移除容器 Hook 接入后 21 项失败，均为实际断言失败而非编译 / 工具错误。清理失败后的标量缓存恢复反例另取得修前 1 项失败、修后通过。10 套件 / 102 项关联回归、74 项相关工具、源码 / 热路径门禁、Debug lint、未签名 Develop / R8 与 fatal lint 通过；Deep 按输入复用并替换受影响套件后的完整证据为 440 套件 / 4,027 项零失败 / 错误、2 项既定跳过，工具保持 679 项 / 5 项既定跳过，重叠不相加。过程矩阵仅同步实际 attach Context 参数，255 项路由 / 开关不变；两份输出确定性及受影响的 process-matrix 独立 mutation 1/1 通过。独立 DEX 声明解析与 DexSQL 复核 7603 / 11641 / 11739 及匹配的 Android 14 框架；同 APK / mapping 核验 API 102、设置入口隔离和冷容器准备链。源码 / 测试摘要、原生证据、故障反例与回退范围保留在仓库外 `customiuizer-a14-evidence/poco-folder-container-20261029/`；回退单位为容器、两个安装配置、Context 接入、矩阵投影与关联回归。

依用户此前的本地正式签名交付授权，已从冻结源码 `d917c36d4e95988936c3c44aaf1c7373978b6d59` 构建包含容器补接的 r14.25.8 / 257 Release。JDK 25、Release R8 / fatal lint、同 APK / mapping 的冷容器准备及 API 102 边界、完整内嵌 sourceCommit、SDK 34 / arm64、不可调试、ZIP / ELF 16 KiB、V2 签名与历史证书均通过。APK 4,331,369 字节，SHA-256 `60223ac0acda31bd7ef7c84d86d45d81ef59b88c8742601399d548d63b285bf3`，已交付本机下载目录 `CustoMIUIzer-A14-r14.25.8-d917c36d/` 并读回摘要；候选 / mapping 在上述外部证据的 `candidate-d917c36d/`，收据为 `artifact-receipt.json`。精确 7601 原件、反馈手机的模块 revision / FolderIconBlur 日志、实际合成、主题 / 旋转 / 多选与设备成本仍待同 APK 验收；native-container-requested 只证明原生请求返回，不证明可见模糊或性能提升。未操作设备、合并 main、打 Tag 或公开发行。

### 持续活动首次入场闪动

用户补充的触发为「刚上岛时闪一下」。Host 已复现两条相关路径：运行中的秒表 / 倒计时紧接收到原生时间基准校正时，Content 比较把它当成整体替换，重新淡出；liveToast 同时重新读取同一来源的应用图标，使本来等价的状态更新再次改变 Drawable 身份。新增两项回归在修改前均实际失败。

仅同一个 IslandLiveKey（类型、UID、session）且时钟方向、运行状态与布局均相同的时间校正，复用当前入场时钟与透明度并更新原生 Chronometer；暂停、方向 / 来源 / 会话切换仍按原交接。仅复用当前 liveContent owner 的图标，不新增缓存表；来源结束、会话 / 包名变化及配置变化失效。既有弹簧、曲线、时长、单一窗口、动作和销毁语义保持。此为可复核的代码 / Host 缺陷，尚不能替代用户设备的闪动录屏复测；本轮证据与本地 APK 收据统一留在仓库外 `customiuizer-a14-evidence/poco-folder-feedback-20261028/`。

### 锁屏充电提示恢复与原生失败隔离

2026-10-29 继续精修，沿 `fe45d930` 保留上一轮 POCO / 时钟修复，本批由 相关工程记录（以现行源码与回归为准） 跟踪，源码冻结为 `30bbaa1025ca89704d884078763d7bfc519d5761`。保持 r14.25.8 / 257 的本地候选身份，既有签名包仍对应原提交，不能当成本次修复的试用结果。

已注册偏好监听原先直接忽略空键批量通知，充电提示的 owner 观察者也只接收三个单键，完整快照恢复后会保留旧字号 / 换行。现将该输入交给已有完整读取、原子发布与锁外分发路径，视图在自己的 UI 线程读最新偏好并恢复 / 重用原生样式；单键仍增量读取，初始两份快照仍静默，读取失败不删除有效配置、不分发伪恢复。没有新增监听、轮询、计时器或业务预热。

原生 `onFinishInflate` 或 `setNextIndication` 失败时，不再追加样式写入；构造失败不注册新观察者、不保存未完成视图的基线。原生调用仍各一次，原异常与 fatal 传播保持。两个回调抽出为实际安装使用的工厂，真实 Android 14 TextView 回归覆盖批量清空 / 替换、完整监听链、排队后的最新快照、正常重设外观及两条失败路径；不改解锁、应用锁策略或既有原生外形。

新增 13 项行为回归，修前 9 项失败，修后全部通过；定向 153 项及完整 Deep 的 432 套件 / 3,902 项 Host 回归零失败 / 错误、2 项既定跳过，Debug lint 通过。全量工具执行 679 项，唯一旧回调位置检查随工厂抽取改为同时核对安装绑定与原生 after 回调，保留全部原断言；相关 11 项复验通过，其他 668 项结果（含 5 项既定跳过）按相同输入复用，另以断开绑定 / 删除 after 两个内存反例确认该检查仍检出故障。

同源码未签名 Develop 的 R8 / fatal lint、工程 revision、SDK 34 / arm64 / API 102 与 ZIP / ELF 16 KiB 对齐通过。最终 DEX 的 compileOnly API 定义及八个设置入口直接 API 指令引用均为零，setId / getId 各一处，经同产物 mapping 对应共享 HookInstallation，replaceHook 为零。APK 4,310,934 字节，SHA-256 `09b2e6f1f8e297c8b9913543e9c461664762da40ad879db215f410013d5358a7`；完整源码 SHA、实际日志、输入摘要和收据保留于仓库外 `customiuizer-a14-evidence/lockscreen-charging-lifecycle-20261028/`。本批未签名、未安装、未公开发行；没有连接设备，同 APK 的恢复设置、充电提示与字体 / 主题切换仍待实机验收。

### PIN 乱序的原生失败与完整布局门禁

2026-10-29 从 `7a019a851cb795c28fea7abe045c4663af984df0` 接续 相关工程记录（以现行源码与回归为准） / `codex/pin-scramble-boundary`。旧回调捕获原生 onFinishInflate 异常后仍读取并重排键盘，最后才重抛；完整数字缺失、重复引用、后续行缺失或 mViews 与原生父子关系不符时，也可能先清空行再失败。此次先将原回调提取为实际安装使用的工厂，规范化回调体与原源码核对相同，再取得旧行为反例；不是仅测试未接入的辅助函数。

原生调用保持一次，异常立即原样传出，不再读取或改写失败的键盘。成功返回后，先核验矩阵范围、十个非空且互异的原数字 View、全部行与实际槽位，再执行原有 shuffle / 重排；准备不完整时保留原生结果和已有子 View。十个原 View 的点击、底行固定控制区与原生返回值均保留，不改认证 / 解锁策略、设置键、安装点或 Hook 数量。既有 XposedHelpers.log 已负责直接 / 包装致命错误的重抛，本轮复用并保留测试，不重复引入异常机制。新增 IdentityHashMap 最多十个临时键和三行局部引用，限功能开启后的原生 inflate 回调，结束释放，不增加常驻缓存 / 监听 / 任务。

fuxi CN `V816.0.19.0.UMCCNXM` 与 TW `V816.0.8.0.UMCTWXM` 的本地 SystemUI 原件分别为 SHA-256 `2bf4f7e56eeda694e8f7de71c0acda2cae892f9847c6d6a747a52cfe5381e42b` / `66386ad63878da129041fab7ae334d313257703515e4c9a179cb092292b32dc1`，系统属性均为 Android 14 / SDK 34。DexSQL 确认原安装方法定义和相同 mViews 初始化；编译布局前三行各为三枚 NumPadKey，底行为 Space、key0、控制容器，符合新门禁。它们是这两份样本的静态合同，不替代用户手机、其他主题或 ROM 的键盘输入验收。

12 项真实 Android View / 回调测试中旧逻辑 6 项失败，同一测试文件哈希下修后全部通过；包括成功重排后十个原数字仍各响应一次点击、固定控件保持、原生异常不读 View、完整性失败不移除子 View，以及既有直接 / 包装 fatal 与普通模块错误边界。11 套件 / 133 项关联回归、64 项相关工具检查、源码 / 热路径门禁通过。按 Deep 复用未变化输入，去重替换后的完整证据为 438 套件 / 3,988 项零失败 / 错误、2 项既定跳过；不是本轮重跑完整测试。工具范围沿用 679 项 / 5 项既定跳过，本轮 64 项替换相应交集，重叠不相加。回退单位是 PIN 回调、安装工厂、关联回归与本节；证据入口为仓库外 `customiuizer-a14-evidence/pin-scramble-boundary-20261029/`。

最终未签名 Develop / R8、Debug / fatal lint 通过；同 APK 的 Hook API 定义和设置入口直接 API 指令为空，setId / getId 各一条并经同产物 mapping 归属共享 HookInstallation，replaceHook 为零。实际 PIN 回调在 R8 产物中保留，一条 native proceed 指令位于模块 catch 范围之外；这是 DEX 静态证据，主机的调用计数另由行为回归证明。ADB 无设备，未签名、安装 / 重启、合并 main、打 tag 或公开发行。真实 PIN 输入、主题 / 字号 / 横竖屏、无障碍顺序与触感仍待同 APK 设备验收，不宣称性能收益。

### 应用锁 PIN 的完整准备与原生失败边界

2026-10-29 从 `d5fcf67c70b4c1d5b549a013c6f5fdbe844583b1` 接续 相关工程记录（以现行源码与回归为准） / `codex/applock-pin-boundary`。手机管家的应用锁使用独立数字键盘；旧实现收集一行就清空一行，后续行缺失、类型变化或额外控件可令前面行已被清空，原构造失败也仍会进入重排。先将原回调等价提取为实际安装使用的工厂，再取得旧行为反例。

原构造仍调用一次，失败立即原样传出，不读取未完成 owner。成功后先确认前四行各三枚非空且互异的原 View、父子归属与十枚数字完整，再执行原有 shuffle 和重排；准备不完整就保留原生界面。保留底行两侧控件、数字点击与四行之外的原生页脚，不修改认证策略、设置键或安装范围。准备仅使用四行引用、十枚数字及最多十二项临时身份表，回调结束释放，没有常驻缓存、监听或任务；既有 logger 的直接 / 包装 fatal 传播继续复用。

fuxi CN / TW 的 Android 14 / SDK 34 手机管家原件 SHA-256 分别为 `284a4929cbee030a9addb37c21b5d7221b7d9ab986f3085d001409ec0c739a38` / `d42e36c0a0a8224dc7c8e03759d54f01c8b92ea56ec97b62ff0fa6860ce38824`。DexSQL 确认两者均只有一个 Context / AttributeSet 构造入口；构造经初始化方法创建四行，各经行填充方法添加三枚原生按键，数字依次为 1–9，底行是 11 / 0 / 10。未发现重复构造委托，不增加无依据的重排标记。静态样本证明这些布局与入口，真实输入与其他 ROM 仍待设备验收。

12 项实际 Android View / 已安装回调测试中旧逻辑 6 项失败，同一测试哈希下修后全部通过；12 套件 / 145 项关联回归、64 项相关工具检查、源码 / 热路径门禁、Debug lint、未签名 Develop / R8 与 fatal lint 通过。同 APK 的 compileOnly API 定义和设置入口直接 API 指令为空，setId / getId 各一次且映射至共享 HookInstallation，两个 PIN 回调的 native proceed 均位于模块 catch 范围之外。Deep 按未变输入复用后的去重证据为 439 套件 / 4,000 项零失败 / 错误、2 项既定跳过；本轮没有重跑全量。工具沿用 679 项 / 5 项既定跳过，64 项新结果替换相应交集。回退单位是应用锁回调、安装工厂、关联回归与本节；证据入口为仓库外 `customiuizer-a14-evidence/applock-pin-boundary-20261029/`。

依用户追加授权，已从最终冻结源码 `c40275ea9b05b829612df655ec3802017e6668dc` 生成本地 r14.25.8 / 257 正式签名 Release，累计包含此前已接续的精修。JDK 25、Release R8 / fatal lint、内嵌完整 sourceCommit、SDK 34 / arm64 / API 102、不可调试、ZIP / ELF 16 KiB、V2 签名与历史证书均通过；同产物 API / mapping 和两个 PIN 回调失败边界再次验收。APK 为 4,331,369 字节，SHA-256 `db43e8e4895b1474951a18ae74f23e63ca07604966a008798a0b1886ee71f161`；已复制到本机下载目录 `CustoMIUIzer-A14-r14.25.8-c40275ea/` 并读回摘要，原候选与 mapping 保存在上述外部证据目录的 `candidate-c40275ea/`，收据为 `artifact-receipt.json`。本次仅本地签名交付，未安装、合并 main、打 Tag 或公开发行；应用锁真实输入、主题 / 字号 / 横竖屏、无障碍顺序与触感保留待验，不宣称设备性能收益。

### 锁屏专辑封面居中与裁切

2026-10-29 接续 相关工程记录（以现行源码与回归为准），本批仅修正既有 `drawAlbumArt` 的矩阵顺序：先缩放，再按目标像素坐标平移。旧实现将已计算的居中偏移再次乘以缩放比例，放大封面可移出可见区，缩小封面则偏向一侧。默认 / 覆盖继续使用 CENTER_CROP，适应继续完整居中留白；没有改动模糊流水线、缓存、异步发布或 View 所有权。

Quick 验证新增 11 项 Android 14 原生 Canvas / Bitmap 像素回归，修前 10 项失败，修后与既有预算 / 代际 / 释放回归合计 3 套件 / 20 项零失败 / 错误、零跳过。覆盖横 / 竖封面、放大 / 缩小、两种裁切入口、方形封面铺满竖屏、单位缩放，以及灰度切回彩色和源位图不被修改；已目检目标尺寸下的留白与中部裁切。源码合同门禁通过，日志、同一测试输入摘要、JUnit 和修前 / 后 PNG 保留于仓库外 `customiuizer-a14-evidence/lockscreen-album-art-centering-20261029/`。本批没有重建 APK / R8 或签名，上一批 Deep 证据只在未变化范围复用；新代码的真实 HyperOS 锁屏合成与完整同 APK 验收仍待设备验证。

### 锁屏专辑封面输入失效与息屏交接

2026-10-29 从 `4a2685a8cbc29da8c7a2d764dd8dc56346349ad3` 继续 相关工程记录（以现行源码与回归为准） / `codex/lockscreen-art-inputs`，保留前轮 Dock 与动态岛成果。旧入口仅比较 Bitmap 对象身份，播放器复用同一位图时，灰度 / 裁切 / 模糊参数变化和原位像素更新会被忽略；从另一首歌回到已修改的原 Bitmap 时，旧缓存键又会命中旧像素。另有可控并发反例：旧可见任务已完成绘制、仍在提取颜色时，息屏收到新封面只暂存、不更新 generation，旧任务随后覆盖新来源对应的空结果。

入口现在先比较已保存的像素 generation 与全部渲染参数，再更新 pending 输入；缓存键也包含 Bitmap generation。同对象同像素同参数的 30 次重复通知不重启 worker、不替换结果。新输入在息屏 / AOD 暂存前也作废并取消旧 generation，恢复时仍由既有单消费者处理最新输入。不增加 Bitmap 副本、缓存容量、监听、Hook、后台轮询或并行执行；仅增加 controller 保存的一个 Int 与每个既有缓存键的一个 Int。两目标帧字节预算、既有居中 / 裁切、颜色广播与 View 释放规则保持，旧缓存由原预算及 clear 释放。回退单位为此 controller、关联行为回归与本节。

新增 8 项真实入口 / Android 14 像素和可控 worker 交接回归，旧源码 6 项失败；同一测试文件哈希下修后全部通过，7 套件 / 68 项关联回归零失败 / 错误 / 跳过，27 项工具运行时合同通过。按 Deep 复用输入未变化的完整基线，替换本轮及前轮 Dock 的对应套件后，覆盖 437 套件 / 3,956 项零失败 / 错误、2 项既定跳过；这是证据复用，不是本轮重新运行完整测试。未变化工具范围沿用原 679 项 / 5 项既定跳过，本轮 27 项替换其交集，重叠不相加。日志、修前 / 后 JUnit、同源测试哈希与后续产物收据入口为仓库外 `customiuizer-a14-evidence/lockscreen-art-inputs-20261029/`。

最终未签名 Develop / R8、Debug / fatal lint 与本轮源码 / 热路径门禁通过；同 APK 的 Hook API 定义和设置入口直接 API 指令为空，setId / getId 各一条且由同产物 mapping 归属共享 HookInstallation，replaceHook 为零。Bitmap.getGenerationId 的两条新入口分别映射到实际 controller 与处理协程，未被 R8 脱离调用链。APK 身份由外部输入哈希及收据关联，不冒充正式签名候选。ADB 无连接，未签名 / 安装 / 重启或公开发行；真实 HyperOS 锁屏合成、参数变化后的显示与息屏切歌交接仍待同 APK 实机复测，未测量设备性能或耗电收益。

### 锁屏专辑封面在途合并与息屏暂停

2026-10-29 从 `8adeb71cb5f12880e7e17df59ed2760bda9ce232` 接续 相关工程记录（以现行源码与回归为准） / `codex/lockscreen-art-flight`。旧 applyTo 在结果尚未发布时每次取消并重建相同请求；原生 AOD 生效既不使旧任务失效，也没有阻止 View 绑定重启处理 / 重显成品；亮屏对已有正确尺寸成品还会重复取缓存并提取颜色。七个新增行为用例中上述五项在旧代码实际失败，真实尺寸变化与原生亮屏早于共享屏幕广播两项为保留行为。

在途请求复用既有 CacheKey 的来源、像素 generation、参数与请求尺寸，仅相同且仍 active 的 job 合并；已失败 / 完成的任务不形成永久阻塞，尺寸或输入变化仍取消旧代。额外状态限 controller 一份纯标量请求键，无 Bitmap / Context / View 强引用，替换、clear 和原生息屏时清空；比较会临时构造请求键，不宣称零分配。取消与 generation 失效集中在同一入口，旧任务无结束回调改写新请求。原生息屏停止旧任务，View 绑定返回 false 并释放自有 Drawable，保留待恢复输入与现有两帧字节缓存；亮屏复用尺寸匹配的成品，缺结果时仍由原生入口恢复处理。共享 ScreenState 的额外息屏判断只保留在媒体入口，避免把原生亮屏恢复阻塞在可能迟到的共享广播之后；没有新增监听 / 计时器 / 线程，显示尺寸读取继续留在原工作线程。

同一测试文件哈希下修后全部通过，7 套件 / 75 项关联回归与 27 项工具运行时合同零失败。连续 30 次 View 绑定保持同一个 job 和 generation，尺寸变化仍发布正确 64×48 结果；这些是主机行为计数，不是 CPU / 帧率测量。Deep 按输入身份复用未变化范围，去重替换后的完整证据覆盖 437 套件 / 3,963 项零失败 / 错误、2 项既定跳过；不是本轮重跑完整测试。未变化工具范围复用 679 项 / 5 项既定跳过，本轮 27 项替换其交集，重叠不相加。回退单位为本次 controller、关联回归与本节；证据位于仓库外 `customiuizer-a14-evidence/lockscreen-art-flight-20261029/`。

最终未签名 Develop / R8、Debug / fatal lint、源码 / 热路径门禁及文档 / 功能地图通过。同 APK 的 Hook API 定义和设置入口直接 API 指令为空，setId / getId 各一条并经同产物 mapping 归属共享 HookInstallation，replaceHook 为零；产物由外部源码哈希与收据关联。ADB 无设备，未签名、安装 / 重启、合并 main 或公开发行；实际锁屏息屏 / 亮屏、尺寸变化和材质合成，以及帧时间、CPU / GPU、长期资源与耗电均待同 APK 设备验收。

### 锁屏封面清理与旧 View 的图片引用

2026-10-29 从 `9e7337dc84accc44bf2fde563c6d43f56b057017` 接续 相关工程记录（以现行源码与回归为准） / `codex/lockscreen-art-release`，此处 release 指资源释放，不是正式发行。尺寸键与已有 View 尺寸门禁保持。旧 clear 只取消任务、清 pending / 缓存，未自行清掉模块的两份附加静态 source / result 字段及当前 View 的模块 Drawable；直接清理后原生亮屏仍可从残留 source 重建。新 View 接管时，尚未挂载的旧 View 也没有 detach 来释放其附加 Drawable 引用。

clear 现沿既有 UI 调用链集中释放静态 source / result、当前自有 Drawable、缓存、pending 和弱 owner 入口；不回收宿主原 Bitmap，也不新增回调或线程。View 替换只提前释放尚未挂载的旧消费者，仍在窗口中的原生出场 View 保留图片到自己的 detach；宿主已替换的背景按 Drawable 身份保护，只有模块附加引用被移除。迟到的旧 detach 只处理自身，不能清理新消费者。不增加跨 View 注册表或 generation 层，保留原生交接、两帧字节预算、单次在途合并与 AOD 恢复。回退单位为本次 controller、关联行为回归与本节。

最终九个新增用例中六项在旧生产源码失败；加入真实 Activity / Window 挂载控制后，用同一最终测试文件重新核对旧行为，再在修后通过。原生已挂载 View 保留到 detach、迟到 detach 不伤新 View，以及清理后新元数据仍可正常接入三项为保留行为。相关 7 套件 / 84 项与 27 项工具运行时合同零失败 / 错误 / 跳过；移除的是可复核对象引用，没有测试即时 GC、PSS 或耗电。按 Deep 复用未变化输入，去重替换后的完整证据覆盖 437 套件 / 3,972 项零失败 / 错误、2 项既定跳过；不是本轮重跑完整测试。未变化工具范围复用 679 项 / 5 项既定跳过，本轮 27 项替换交集，重叠不相加。证据位于仓库外 `customiuizer-a14-evidence/lockscreen-art-release-20261029/`，包括最终原生交接边界下的修前 / 后日志、同源测试哈希和 JUnit。

最终未签名 Develop / R8、Debug / fatal lint、源码 / 热路径门禁及文档 / 功能地图通过。同 APK 的 Hook API 定义和设置入口直接 API 指令为空，setId / getId 各一条并由同产物 mapping 归属共享 HookInstallation，replaceHook 为零；产物与最终源码由外部哈希收据关联。ADB 无设备，未签名、安装 / 重启、合并 main、打 tag 或公开发行。实际锁屏主题切换、出入场合成、旋转与长期内存 / 帧时间 / CPU / GPU / 耗电仍待同 APK 设备验收；不把引用断言当作即时回收或 PSS 收益。

### 锁屏封面发布前的最终尺寸复核

2026-10-29 从 `4e416c9d101a365b5cdce19afe75aa05a9fd7125` 接续 相关工程记录（以现行源码与回归为准） / `codex/lockscreen-art-publication`。既有 applyTo 尺寸门禁能阻止旧尺寸图片直接绑定，但任务处理期间 View 完成布局且未再次绑定时，提交只检查请求 generation，仍会先写入旧尺寸静态成品并发刷新广播，再由绑定入口拒绝并补做。主机确认的是过期发布，没有据此声称真实旧尺寸已上屏或出现闪烁。

Main 提交前现读取当前弱 View 的最终正尺寸；不匹配时跳过旧成品 / 对应颜色广播，并由既有单消费者按当前尺寸准备，原 generation / 取消机制继续抑制迟到任务。未测量或无消费者时仍允许既有默认显示尺寸预备；尺寸改动后又回到原值时复用原任务。不增加尺寸监听、轮询、窗口、图片副本、缓存容量或动画器，实际默认显示信息仍在工作线程读取。旧尺寸绘制 / 取色可能已经完成，本次拒绝其发布，不把已发生的 CPU 工作算作消除；真实布局持续变化时只按完成时看到的当前尺寸衔接。回退单位为本次发布前尺寸复核、关联回归与本节。

四个新增真实入口 / 广播记录用例中，原 View 变为 64×48、未测量替换 View 随后成为 48×64 两项在旧代码都发布了 32×32；同一测试文件哈希下修后仅发布各自最终尺寸。回到 32×32 及持续未测量时的预备为保留控制。相关 7 套件 / 88 项和 27 项工具运行时合同通过；Deep 复用未变化输入，去重替换后的完整证据覆盖 437 套件 / 3,976 项零失败 / 错误、2 项既定跳过，不是本轮重跑完整测试。未变化工具范围复用 679 项 / 5 项既定跳过，本轮 27 项替换交集，重叠不相加。证据位于仓库外 `customiuizer-a14-evidence/lockscreen-art-publication-20261029/`，包含修前 / 后广播尺寸、冻结测试哈希、JUnit 及后续产物收据。

最终未签名 Develop / R8、Debug / fatal lint、源码 / 热路径门禁及文档 / 功能地图通过。同 APK 的 Hook API 定义和设置入口直接 API 指令为空，setId / getId 各一条并由同产物 mapping 归属共享 HookInstallation，replaceHook 为零；产物由外部输入哈希与收据关联。ADB 无设备，未签名、安装 / 重启、合并 main、打 tag 或公开发行。真实旋转、主题 / 视图重建、原生颜色与合成、帧时间和长期资源仍待同 APK 设备验收，不宣称性能或耗电收益。

### 默认 / 吸入返回成本与液体接触

2026-10-29 接续 相关工程记录（以现行源码与回归为准），从 `20ccd608dffb60c13d53221e6e0c972cfea202b5` 保留前轮 POCO / 时钟、充电生命周期与专辑居中修复。核对两种 Home 返回共用任务 / 用户 / owner、单一 CLOSE_TO_HOME、有效几何和七轨弹簧参数检查；非有限 / 非正输入不留下部分改写或租约。吸入额外保留截图权限、安全 / HDR / protected、硬件 Surface、等价帧提交和原生隐藏门禁；默认不承担捕获工作。默认 84% 宽 / 86% 高与吸入独立落点、1.2 倍原生响应比例保持。

默认 Session 不再分配吸入专用网格、颜色、位置数组、Path / Paint、RectF 与交接状态；仅两个大数组的原始载荷为 1,176 字节 / 次。实际吸入 Session 持有唯一 Mesh，结束沿原 owner 释放，不新增 View / 窗口 / 计时器。首次真正捕获前在已有 worker 按需准备 6,860 字节的只读浮点表，由 Launcher ClassLoader 持有，不引用窗口或图像；每帧 49 次逐行幂运算改为插值，前缘颜色由 49 行缩为 8 行，重复 draw 复用同一裁切 Path。捕获数量、像素上界、四字节进度协议及提交 / 取消合同保持。这些是分配和运算事实，尚非设备 CPU / GPU 或续航测量。

按两个参考项目的局部融合与连续形态，在同一 G4 填充中加入最多岛高 7.5% 的下缘液面；默认 / 吸入共用现有原生进度，不移植滤镜或代码 / 素材。融合限于原窗口，挖孔保护仅允许消耗对称高度余量的一半；简化 / 动画关闭、无进度、结束和取消回到原轮廓。系统 Outline 查询保留当前形变，触摸与绘制继续共用路径。参考入口和当前行为见[动态岛动画](../features/island/README.md#动画实现与验收)。

初始六项实际分配 / 原生 Canvas 回归中三项在修前失败，另补最小挖孔余量的先失败用例；修后相关 15 项通过。覆盖默认零吸入绘制资源、唯一有界网格、60 / 90 / 120 Hz 下同一时钟、简化 / 取消恢复、Outline 查询、Path 重用及前缘颜色所有权。查表对照原幂函数遍历 1,001 个进度与全部 49 行，20,000 px 的最大准入模型中误差不超过 0.75 px，行序不翻折。该最大模型用于验证数值上界，不是设备屏幕、性能或观感证据。

Deep 完整回归 434 套件 / 3,924 项零失败 / 错误、2 项既定跳过，Debug lint 通过；全量工具 679 项通过、5 项既定跳过。证据保留于仓库外 `customiuizer-a14-evidence/island-return-fusion-20261029/`，包括修前失败、原生轮廓 PNG、固定参考提交和实际门禁日志。最终同 APK 的页面 / 岛体融合、快速接续、挖孔及左右锚定、真实帧时间、CPU / GPU、PSS 和耗电仍待实机；不把 Host 模型或未签名构建当作设备通过。

源码冻结 `924e138760d91d84b178236b2f46776a6b58a16b` 的未签名 Develop / R8 / fatal lint、工程 revision、SDK 34 / arm64 / API 102 与 ZIP / ELF 16 KiB 对齐通过。最终 DEX 的 compileOnly API 定义及八个设置入口直接 API 指令引用均为零，setId / getId 各一处，经同产物 mapping 对应共享 HookInstallation，replaceHook 为零。R8 后查表仍为独立 holder，捕获协程保留初始化读取，Session / Mesh / Launcher 类初始化未引入其访问。APK 4,310,930 字节，SHA-256 `7926a9f1fa3089cabcdddbf916ab9ac7af4cded6e72005b321e8a1da7a800935`；完整来源、APK / mapping、SQL 与收据保留在上述目录的 `candidate-924e1387/` 及 `artifact-receipt.json`。该 Develop 候选未签名、未安装、未公开发行，既有 r14.25.8 试用包不代表本批设备结果。

本地正式 Release 按后续授权固定源码 `0ace9b6993d1065533ce2293f7bef61c8317fe50`、版本 r14.25.8 / 257，使用既有历史证书；APK 4,314,985 字节，SHA-256 `676e2c9ad03bcb9680a5271f44e37edc3f1e9eee9efa8c02ce781cb94ee6f2d6`。应用、测试及构建输入与上述 Deep 相同，仅路线图文字变化，复用其行为 / 工具结果；独立 Release / R8 / fatal lint、V2 签名与历史证书、内嵌完整 sourceCommit、平台 / API 边界及 ZIP / ELF 16 KiB 对齐均通过，Release mapping / DEX 再次核对独立查表 holder 与 worker 初始化。APK、mapping、签名和来源收据保留于仓库外 `customiuizer-a14-evidence/local-release-0ace9b69-20261029/`；未安装、未公开发行，设备验收与性能测量保持待验。

### 默认回弹恢复、液面增强与不可见网格

相关工程记录（以现行源码与回归为准） 的后续反馈确认默认上岛观感缺少回弹。源码核对发现：默认宽度承接仍取原生页面进度，完成条件又把不存在吸入 response 当成已收敛；快页面可压缩并提前结束岛体。此前回归只对吸入断言延续收敛，默认提前结束被保留为旧预期；参数检查共用不能证明岛体运动一致。现默认也由已有 View 时钟的已用时间推进，并在正常页面完成后等待其 528–540 ms 承接结束。取消、无效通道仍立即终止，原生窗口落点 / 速度 / 阻尼保持，不新增时钟、弹簧对象或捕获资源。

液面深度由初轮 7.5% 增至最多岛高 12%，接触范围加宽，仍在同一 G4 填充和窗口内；挖孔限幅与轮廓共用深度常量，最小相机余量、简化与取消恢复保持。吸入完成等价帧交接后，只在保守包围盒满足既有完全隐去条件时跳过 49 行顶点更新；透明网格同时跳过颜色、裁切和绘制。可见尾部、进度反向或重新露出均完整重算。没有增加裁切层、材质 owner、数组或计时器，不按原生进度百分比猜测遮蔽。

三项新增回归修前均实际失败：默认快页面提前结束、液面深度及零透明度仍绘制；修后连同关联回归共 7 套件 / 57 项通过。实际 A14 View 覆盖首次 / 已有岛体、120 / 260 ms 页面完成与 60 / 90 / 120 Hz；补充 123 组偏心释放 / 末段进度核对保守遮蔽不漏绘，并验证隐藏顶点不改写、重新可见恢复及一次完成。最初无位图 Canvas 的 Host 探针触发 Robolectric native 崩溃，已修正为位图支持的调用探针；只以修正后的正常执行记录作为先失败证据，不当作产品崩溃。证据、JUnit 与原生轮廓 PNG 保留于仓库外 `customiuizer-a14-evidence/island-rebound-occlusion-20261029/`；实际设备观感及资源收益仍待同 APK 验证。

完整 Deep 再验 434 套件 / 3,929 项零失败 / 错误、2 项既定跳过，Debug lint 通过；本轮全量工具 679 项通过、5 项既定跳过，工具 / 配置与消费者未变化的结果复用，补充迟到通道后完整源码门禁与 Host / lint 再次通过。迟到默认通道的新用例在首轮修正后发现重播风险并先失败，现两种效果均从已呈现状态以约 420 ms 收敛，不再重播接纳；正常进度的默认回弹与取消边界保持上述断言。APK / R8 与设备验收分别记录，不把原生 Canvas 调用探针或顶点不改写断言当作设备 GPU / 耗电收益。

宽度边界补验又复现了默认分支的第二项运动差异：外撑余量为零时，向内回缩也被乘成零。现正负承接分别限幅，宽度已满仍可安全回缩；最小胶囊不被压小，接续速度取实际显示的附加位移。首次 / 已有岛体、两种页面完成速度、三种刷新率与有 / 无外撑余量的 24 组轨迹均覆盖，另补相机最小种子回归；此补验先失败、修后 6 套件 / 54 项行为回归通过。先前 `4a2b2602` 构建只保留为中间证据，不能冒充最终修正包。

最终产品输入的完整检查执行 434 套件 / 3,930 项，唯一失败为原倒计时测试把墙钟 deadline 写成当前毫秒、又要求稍后读取仍为零的竞态。该测试改为推进到已发布单调时钟的精确完成边界，原有断言全部保留并增加边界断言，生产倒计时逻辑未改；该类 3 项及 Debug lint 补验通过。其余 433 套件 / 3,927 项完整结果按相同生产输入复用，汇总零失败 / 错误、2 项既定跳过；原失败 XML 与按实际执行来源合成的验收汇总分别保留，不伪称另一次全量执行。

最终源码 `4736e7ccf40490c9f33db9a787dbaebde98ea7d5` 的未签名 Develop 与本地正式签名 Release 均通过 R8 / fatal lint、各自 revision / 来源、SDK 34 / arm64 / API 102、ZIP / ELF 16 KiB 及最终 DEX / 同产物 mapping 边界；查表保持独立 holder 并由捕获 worker 初始化。Release r14.25.8 / 257 使用历史证书与 V2 签名，4,314,985 字节，SHA-256 `db9f7464585f9f58551c59d8ad3b3e6d0b0930435400ea28eae10cd8020261d7`；候选及收据位于上述证据目录的 `release/candidate-4736e7cc/` 与 `release/artifact-receipt.json`。复制后的 APK 摘要再次匹配，未安装、未公开发行；同 APK 的回弹、液面融合、遮蔽收拢和设备性能仍待用户复测。

### 候选与证据

全局集成修复阶段 Deep 完成 428 套件 / 3,865 项 Host 回归，零失败 / 错误、2 项既定跳过，Debug lint 通过；全量工具 679 项通过、5 项既定跳过。随后按用户要求升为 r14.25.7 / 256，应用差异仅两条版本声明；业务实现和行为测试不变，复用其完整结果，补验新版本的入口编译、路由、112 项发布工具回归与文档 / 元数据。后续持续活动短岛 / 横屏的完整复验、最终 APK / R8、独立 mutation 和签名只以最新冻结提交后的实际收据为准，不把已执行范围扩大到未完成检查。

Deep 验证覆盖完整 Host / Debug lint、全量工具、当前矩阵确定性与必需独立 mutation，以及唯一候选的未签名 Develop / R8 / fatal lint、SDK 34 / arm64 / API 102、设置进程边界、同产物 mapping、来源与 16 KiB 对齐。最终 RC 完整 SHA、实际退出结果、JUnit 汇总、APK / mapping 摘要和远端读回统一保留在仓库外 `customiuizer-a14-evidence/rc-global-audit-20261028/`；本段不把计划范围写成执行通过结果。七份原暂存技能文件的 EOL 问题以外部 HEAD 索引隔离验证，本次源码正常纳入；不覆盖原修改。

已核对公开稳定版 metadata 的历史证书 `c0eff2dc4e662717195490da78b12a984c6f2e6bd38acf4edad14d53e3d22e70` 与既有策略。用户追加授权从最终冻结提交生成本地正式签名 Release APK 供自行试用；仅使用仓库外既有配置，核验同产物来源、V2 签名与证书，不输出或提交密钥 / 密码。远端三个工作流启用，签名 / 发布环境均只允许 main branch、没有人工等待规则；没有修改环境或派发发行。公开晋升仍需 exact main CI、正式签名与双端收据；本地签名包不代表已经公开发行或完成实机验收。

ADB 无连接；没有安装、重启或新录屏。设备验收与帧时间、CPU / GPU、PSS、功耗均未完成。保留前轮 Host 可复核操作次数：屏外 120 帧捕获 120→0、两图标 120 帧叶坐标入口 720→0、重复透明度提交 120→0；这些输入未受本轮修复改变，不能外推为设备性能收益。最终同 APK 的活动 / 通知 / 媒体交接、Home / PiP、锁屏、旋转、手势取消与文件夹矩阵是正式晋升前的必要条件。

## 源码与 ROM 证据检索

Agent 的设置键 / 功能 / Hook 反查使用[原辅助查询入口的 context 数据集](../TOOLS.md#辅助数据按需查询)，
复用 Feature Semantics、Process Matrix、当前源码 / XML 和[原 ROM Catalog](../rom-intelligence/DEXSQL.md#离线-rom-知识库)。
块体 `installHook()` 已修正提取，动态岛的两个安装 owner 不再误记为默认安装；语义置信度、源码事实、词法候选和 DEX 静态证据分开。
已知精确源码路径仍直接查源码，context 用于未知入口及上下游关系，不替代必要验证或编译器调用图。

四份 A14 分区保留 2,208 个文件位置、1,441 份内容和 23 份核心 DexSQL 缓存；九个无效归档位置保持 UNKNOWN。
实际 duchamp 目录仍为原拼写，以 ODM device 确认身份。新增 dada 的 SDK 36 / HyperOS 3 已分类为 `REFERENCE_ONLY`，
默认查询、准备和结果复用从工具层隔离，只有显式设计研究可访问；单独的 SystemUI 参考缓存不作为 A14 兼容合同。
来源身份变化和跨版本同哈希均有拒绝 / 隔离回归；POCO 组件命中不提升为已经接入生产进程路由。

三个固定问题的热缓存回放（每组交替三次，中位数；包含命令进程启动，未清 OS 文件缓存）：

| 场景 | 完整流程毫秒：原有分步 → context | 命令数 | 输出 Token 计数：原有分步 → context |
| --- | --- | --- | --- |
| 动态岛 / SystemUI | 638 → 470 | 6 → 1 | 2733 → 1584 |
| Xiaomi / POCO 动画 | 623 → 652 | 6 → 2 | 2357 → 1065 |
| Dock 模糊 / 生命周期 | 646 → 454 | 6 → 1 | 2460 → 1146 |

Source facts / 结果缓存为空时完整流程约 1.7–2 秒；热查询复用 897 份源码 / 资源事实，所测 DEX 查询均复用结果，无重新解析 ROM。
输出计数使用固定 `o200k_base`，不是实际模型 Token / 账单；命令数不是实际 Agent 工具轮次。
每场景五项已知 feature / installer / process / owner / 入口事实共 15/15 命中，不能外推为完整 Hook 调用图准确率。
原有回放预先知道正确 owner / 文件，首次输出源码约 123–130 毫秒，context 约 454–472 毫秒，未获首路径速度收益；
形成有效修改方案的时间、真实 Agent 无效检索 / 重复读取及实际上下文总量未测。Launcher 耗时无稳定收益，不扩建专用索引或额外预热。
Standard 的相关回归、最终生成确定性及独立故障验证按本轮实际源码执行；回放、原始结果和凭据只留在外部
`forA14-ROM-index/evidence/integrated-agent-20261028/`（`replay.py`、`benchmark.json`）。
本轮继承本地有效源码后只改基础设施；没有 APK / R8、CI、正式签名、发布或设备验收变化。


## 松手衔接、Dock 连续性与文件夹背景

2026-10-29 按「桌面多为固定壁纸，不要求很实时」先做 Dock 只读评估；该评估时尚未改算法，后续已实施本页的静态停止移动后补采，其他候选仍保留评估状态。当时静态稳定态已经停采样；可优化的是 Dock 矩阵 / 尺寸变化触发的 450 ms 跟随采样、外观回调无条件清空纹理 / Shader，以及 Shader 失败后当前 owner 永久退役。建议保留同一壁纸、用户与视口下仍有效的缓存，仅更新绘制坐标，并在原生动作稳定约 150–300 ms 后合并补采一次；颜色变化与真实壁纸替换分别失效，准备新帧期间仅复用同源有效帧。保持单纹理、一个在途请求及真实隐藏 / detach 的释放边界。进一步评估平坦中心的 Shader 快速分支，避免全区域都计算折射和高光；GPU / API 真不支持时仍原生回退，不能靠降频掩盖。实际回退原因尚未由设备日志确认，不宣称性能收益；源码摘要、现有测试与候选优化范围在仓库外 `customiuizer-a14-evidence/dock-static-evaluation-20261029/assessment.json`。

媒体默认短岛，系统持续活动默认按内容测量的长岛；一次上滑关闭当前来源，下滑不展开，媒体下滑直接使用已存动作。通用展开选项已移除，旧值不再参与运行时。短反馈按实际字形、字号和摄像头两侧通道寻找够用宽度；中文“已删除”不再无条件占满短岛基准，长文字仍受物理 / 配置上限限制。中、繁中、英、德、阿拉伯、日文和 1 / 1.3 / 2 倍字号已有 Host 覆盖。

持续活动与音乐复用同一 controller 的激活次序：普通倒计时、录音、秒表和媒体仅在真实开始、恢复或确认 Home 接入时争取最新展示，重复元数据不续次序；通知 / 短反馈、通话与响铃继续优先。音乐暂停仍按既有两秒可见期限结束，随后在原窗口交接给有效活动，不先完整退场。每类仍只有一个当前槽及一个次序标量，不保存队列或活动历史。前台 / 等待 Home 的播放器准备目标不受当前绘制资格限制，也不能被后台时钟的目标顶替；安全、会话、主动关闭及前台来源门禁分别保留。

上滑与经典 Home 保留同一原生 CLOSE_TO_HOME 确认，两种上岛均选择无图标窗口目标；默认使用原生窗口，吸入才捕获一次冻结纹理。吸入接管前后持续跟随同一原生窗口的当前矩形，仅在已提交并隐藏 leash 后推进形变，不再冻结空间位置后从零位移起步。接入点保持水平居中，纵向 90% 仅为偏好，按原生内侧落点、完整窄口高度和抗锯齿余量向岛内限幅；全网格进入岛内后平滑让出纹理，显露实时岛色。透明 / secure / HDR、迟到捕获、取消和自然完成的原生回退及释放保持。

岛体在页面进入期间先准备接收形态，页面完全收完后才执行现有 260 ms、权重限幅的内容承接曲线；与普通内容切换复用同一函数和有限时钟，不再按后缘 90% 提前外弹，也不加第二个动画器。原生页面进度完成不代表岛体已经静止：已挂载岛体的后续几何变化继续继承当前宽高、枢轴及可见位移，不能因完成标记直接清零变换。第二活动沿同一状态栏窗口变化，屏幕 top 的模型回归与实际设备观感分别验收。

参考 AyuGram 上游 `7013145676d36d82ee13c02a89f72097b7490dcd` 的当前位移接续与速度采样思路，具体出处见[动态岛说明](../features/island/README.md#动画实现与验收)。继续复用原生弹簧与本项目网格，缓存纹理保持一次捕获，48 行网格共用一次后缘进度计算；没有逐帧 IPC、新轮询、常驻采样或新动画库。静态路径及主机位移检查不等于设备 CPU / GPU、帧时间或功耗收益。


本轮反馈修复对应源码 `974e16fa0fc4fa7fa787534c6e2ccff5a27582b3`。Deep 的完整 `verify.py full`、Debug lint、源码 / 热路径 / 生命周期门禁通过：422 个套件、3,830 项 Host 测试零失败 / 错误，2 项既定跳过；677 项工具测试通过，5 项既定跳过。旧实现的冻结尾部、收完前外弹、完成标记清零可见位移三项由真实行为回归检出；另覆盖音乐晚于时钟启动、暂停接续、前台播放器目标及延迟 Home 确认期间旧活动不退场。60 / 90 / 120 Hz 仅为主机曲线采样，不作为设备 FPS。

同源码的未签名 Develop R8 / fatal lint、SDK 34 / arm64 / 非调试、ZIP / ELF 16 KiB、工程 revision 来源及 API 102 元数据均通过。同 APK 的 compileOnly Hook API 定义与八个设置入口直接 API 引用为零；setId / getId 各一，replaceHook 为零，已按该 APK 的成员 mapping 对应到共享安装 / 回读策略。验证 APK SHA-256 为 `1f90a7a90ea63c95fed4563bd7d91ff1b15558b7e390d7d84c54ddcd902b62d2`；只作静态验证，未签名、未安装或发布。证据保留在仓库外 `customiuizer-a14-evidence/island-feedback-20261028/`，包含旧行为失败、完整 JUnit、工具日志及 `unsigned-develop-974e16fa/` 的 APK / SQL / mapping / 收据。既有七份暂存工艺 Skill 文件在验证期间仅临时规范工作副本换行，随后恢复原始字节与暂存状态，未纳入本轮提交。ADB 未连接，真实吸入观感、黑点融合、快速多活动接续、CPU / GPU 与功耗仍待目标设备验证。

Dock 的固定扩边取样在更大捏合位移下失去覆盖，已由修前失败回归复现。本轮改用完整壁纸合成视口、每张最多 393,216 像素 / 最长边 1,024，仍为一张持有纹理及一个在途请求；以局部采样分辨率交换连续运动覆盖，不采集应用 / 桌面内容。零 alpha 过渡只暂停采样，真正隐藏才释放，保留原生图标与解锁参与者。默认材质、折射和取样真实性继续各自负责，未宣称设备性能收益。

新增「桌面 → 文件夹 → 文件夹图标背景模糊」，默认关闭，启停后重启桌面；名称与安装目标共用 MIUI / POCO。munch 的 MiLauncherGlobal 已含 FolderIcon.onLayout / 原生 BlurUtilities.setFolderIconBlur，原路径受产品名判断限制。仅在已核实的文件夹准备 / 绘制范围启用原生能力，保留硬件支持、系统背景模糊开启及默认图标要求；没有改全局身份 / 系统设置，没有新采样、材质或动画 owner。全部入口先解析、范围 Hook 安装成功后才开门，失败回滚；嵌套、异常和其他线程不泄漏范围。CN / TW 与 munch 样本及 Host 结果不等于 POCO 实机模糊验收。

前轮衔接修复的证据在仓库外 `customiuizer-a14-evidence/island-continuity-20261028/`。该基线 Deep 的完整 `verify.py full`、Debug lint 和源码门禁通过；422 个套件、3,822 项 Host 测试零失败 / 错误，2 项既定跳过。全量工具 677 项通过、5 项既定跳过。修前失败、修后针对性与完整 JUnit 分别保留；60 / 90 / 120 Hz 的主机采样只验证曲线和生命周期，不证明设备帧率。主体形变参数及原生窗口飞行合同保持，接入点与前缘透明度按最新反馈调整。

前轮 Dock / 文件夹 ROM 证据与候选在 `island-motion-20261028/`，设备基线在 `island-device-20261028/`。候选缺少对应实机录屏，松手细微闪动、动态接入点融合、吞并后回弹及音乐 / 秒表快速轮换仍待验，Dock 捏合 / 解锁与 POCO 模糊沿用待验状态。该历史批次的设备证据不完整，不能据此扩展验收或环境恢复结论；原始使用状态与操作记录不公开。

未改动的 Dock / POCO ROM 合同复用前轮证据：MIUI 类内 lambda / POCO Consumer 两个壁纸回调 ABI、独立 DEX 解析器与 DexSQL 核对的 MIUI 13 / POCO 12 个必需声明均无缺失；两个矩阵输出确定性及 11 / 11 必需独立 mutation 通过，无存活、门禁或清理错误。前轮完整 Host 与工具回归已重新执行，矩阵 / 工具输入未改，不因收口重跑该组 mutation。

源码 `c02dedeb5f9f25ff8e407be72c7e60c352051c80` 已生成原证书签名的本地 Develop 试用包 `CustoMIUIzer-A14-r14.25.6-c02ded-develop.apk`，4,298,634 字节，SHA-256 `9447927dee48a7df84c665b94630c9b036d2cb9e0cbb24815f792a715277403e`，已复制到本机下载目录的 `CustoMIUIzer-A14-r14.25.6-c02dedeb/`，旧包保留。R8、fatal lint、SDK 34 / arm64 / 非调试、V2 历史证书、ZIP / ELF 16 KiB 与工程 revision 来源通过；同 APK 的 Hook API 定义及八个设置入口直接 API 引用为零，setId / getId 各一、replaceHook 为零，成员级 mapping 归属共享 HookInstallation 策略。APK、mapping、SQL、完整 JUnit 与最终收据保留在当前衔接证据的 `candidate-c02dedeb/` 及其上级目录；Develop 内嵌八位 revision，完整源码 SHA 由外部收据关联，不冒充公开正式发行。本轮没有手机安装或录屏，没有新建手机测试负载；本地检查进程和临时索引在收尾时释放，必要证据保留。

上轮完整 Host、Debug lint、677 项工具及签名 / R8 门禁曾通过，但缺少共享进度终止帧的行为覆盖。本地包 `1ce847ab` 在实机触发 SystemUI `StackOverflowError`：自身帧回调内调用 `ValueAnimator.end()`，A14 同步发出终止帧，重复进入同一回调。使用该 APK 的 mapping 已对齐设备堆栈，故障包已撤回，手机先回退升级前的 `03fb76b7` 正式包并保留设置；回退在用户补充“先修代码”之前已完成。

终止帧修复只在结束前解绑本次时钟自身的更新回调，再执行正常结束和资源释放；不加异步完成任务，也不吞栈溢出。实际 A14 ValueAnimator 与 SharedMemory 回归在旧代码复现同一栈溢出，修复后正常完成、取消进度、单次结束、迟到帧及映射释放通过。此前五类 154 项回归、Debug lint、文档与功能地图检查通过；后续完整验证及本地候选状态以上述最新收据为准。故障包、retrace、修前失败 / 修后通过的 JUnit 与日志保留在仓库外 `customiuizer-a14-evidence/island-home-20261027/`；候选尚无实机动画或 CPU / GPU 通过结论。

A14 独立 ART 进度探针完成 1000 次映射生命周期、121000 组读写，文件句柄 54→54；它不代表真实返回的 CPU / GPU 或帧率。用户已取消 Develop 覆盖试用与真实千次返回压力测试，本轮不作动画观感、整机功耗或 GPU 成本通过结论。POCO MiLauncherGlobal 使用不同包名和直接 RectFSpringAnim 路径，缺少 WindowElement；维持原生返回，POCO 吸入仍待适配和设备验收。参考项目的运动规律与转译边界见[动态岛说明](../features/island/README.md#动画实现与验收)。

## 桌面、锁屏、安装器与系统事件边界

本轮继续按各二级页的实际设置入口、安装条件和高频回调核对，优先修复默认恢复、原生失败、用户归属和重复工作；不以挂载数量、缓存进程或代码行数推断省电。未开启功能继续由原有 Feature 条件排除；没有增加轮询、保活进程或包信息的长期缓存。

| 范围 | 本轮决定与边界 |
| --- | --- |
| 桌面布局 | 组件横向间距 0 恢复原生尺寸和 padding，1–41 保留既有 dp 映射；保护 packed 高度及正宽度。读取签名参数数目代替复制整组参数，原生失败不再被空 padding 覆盖。网格、图标缩放、壁纸、页面指示器和抽屉仍沿用各自入口及已有生命周期 |
| 应用标题 | 字号 5、上边距 0 保留本次原生创建 / 重算的默认结果；自定义值限 XML 范围。字号 / 边距更新复用改名表，单项删除只更新该键；批量恢复同步还原标题。标签和同目标的多个图标均在主线程更新，销毁页面不执行旧更新；原生创建失败不注册观察者或继续改标题 |
| 锁屏 | 手电状态观察者只弱持有原生 owner，仍由既有单份注册机制替换 / 解除；选项关闭与自发事件不刷新图标。原生底部区域构造 / 图标更新失败时不继续写界面。壁纸、专辑图、闹钟、充电信息及认证策略保留现有隔离，不新增后台拉取 |
| 应用锁 | CN / TW 实现均已将计时移入 AccessControlImpl，旧服务外壳的 checkAccessControlPassLocked 不存在。改为只跟随三处原生新时间戳之后的 scheduleForMaskObserver：确认当前线程已持有原生用户锁，仅调整实际包 / 用户的现有记录；不用保存或遍历整张记录，也不固定 user 0。线程临时范围只匹配原生 Handler、包 / 用户 token 和 60 秒延迟，同步延长已有隐私刷新任务；其他线程 / Handler / token / 延迟保持原生，结束立即移除范围，原生异常回滚本次时间偏移。默认一分钟不调整，新配置在下一次原生续期时应用，不额外撤销已开始的原生计时。两项安装完整预检，后项失败撤回前项；跳过指定活动只扩展成功的原生 false 结果，不覆盖原生异常 |
| 高级设置 | 分享 / 打开方式过滤只读原 Intent、不复制整组参数，名单为空或动作不属于该功能时提前结束。同次查询按包复用决定，仅当“移除原应用但分身可能保留”时查询分身；首次删除才复制结果，保持顺序与原生对象，失败不留下半过滤列表。正反选、MIME、原应用 / 分身的现有规则保留；USB、截图、动画及明确启用的签名 / 完整性选项保持各自范围，没有借优化扩展授权 |
| 应用安装器 | 信息增强在安装期预检类型和反射方法，绑定完成后只更新描述与对应大小项；保留父布局、其他子控件及原生文本，缺元数据或原生失败不执行局部写入。用唯一的泛型 bridge 绑定入口代替“第一个 void(holder)”猜测，避免命中仅绘制图标的辅助方法。CN 样本有该视图，TW GlobalPackageInstaller 样本没有该私有视图，后者保持原生；允许系统应用更新与净化功能仍由原有独立开关控制 |
| 应用管理 | 复核本聊天前批的五项入口：ApplicationInfo 副本、详情字段、举报按钮、排序和状态菜单；沿用已实现的包 / UID / Activity 用户复核与核心应用保护。没有新增状态缓存、后台扫描或自动应用变更 |
| 屏幕 | 熄屏自动亮度判断改为每次调用的局部状态，嵌套 / 不同控制器不能互相覆盖，原生失败不发额外消息；亮屏、仅报告事件不读取额外 owner。渐暗比例 0 保留本次原生配置，非默认值限 1–99%，先核对两字段再写；旋转、充电与屏幕动画仍由原有选项分流 |
| 最近任务 | 隐藏选中应用只扩展成功的原生 false 结果，保留 topActivity / baseIntent 回退和名单快照；原生异常不变成移除批准。清理全部仍执行原生一次，失败不改成成功；标题隐藏只在原生布局成功后执行。模糊与壁纸缩放继续使用原有默认恢复 / 会话隔离 |
| 控制中心 | 安全磁贴解锁后重开最多保留一个 800 ms 回调，后一次请求使前一次失效；重复解锁完成不重复排队，执行前重验锁屏和当前磁贴 / 保持展开选项。仅惰性持有一个应用 Context / Handler；插件、网格、卡片、滑杆及手势继续由原有宿主 / 插件安装和 owner 机制维护 |

资源交换：每次名单过滤有一个限于当前候选包数的临时决定表，首次删除才有结果副本；安装器只持有反射元数据，不保存 APK / Activity / holder。应用锁为同步原生隐私刷新时间增加 Handler 三参数 postDelayed 观察点，范围外仅查询线程临时状态，不读参数或改结果；这项固定回调成本需与取消整表复制一起在设备上评估，尚不声明 CPU、内存或续航净收益。控制中心无常驻时钟，最多一个原本就存在的延迟重开任务。

ROM 取证为 fuxi HyperOS 1 / A14 的 CN、TW miui-services.jar：相关五个原生实现 Smali 一致，三处调度调用均在原生用户锁内；服务外壳先完成原有权限 / 身份路径。安装器样本仅证明 CN 私有绑定合同和 TW 缺失边界。查询、完整方法、样本哈希及 Host / APK 结果存放仓库外 `customiuizer-a14-evidence/island-handoff-20261026/passive-features/`。本批按 Deep 检查：Full 的 411 个套件、3,718 项测试零失败 / 错误、2 项既定跳过，Debug lint、源码 / 热路径 / 偏好 / 生命周期门禁和文档 / 功能地图通过；全量工具 677 项通过、5 项既定跳过。真实应用锁计时与隐私刷新、多用户切换、安装器布局 / 无障碍、控制中心重锁和桌面快速重建仍待设备验收，不能由 Host 或 ROM 静态样本代替。

应用源码 `effb1a3ba8ec828490b5fc01ae49fa9e19a8f82e` 的未签名 Develop R8 / fatal lint、工程 revision 来源校验、min / target SDK 34、arm64 和 16 KiB 对齐均通过。最终 DEX 的 compileOnly API 定义及设置 Manifest 入口直接 API 指令引用为零，setId / getId 各一次、replaceHook 为零；同产物 mapping 将调用对应到既有 HookInstallation 安装 / 回读策略，源类加载回归通过，无新增录音权限。APK 为 `CustoMIUIzer-A14-r14.25.6-effb1a-develop-unsigned.apk`，4,254,182 字节，SHA-256 `1612af357e20b65a3a4ef88ad0daecf2d3d69bf6d46defc27fe43bc02e7f2b16`；完整提交 SHA 与工程包内 8 位 revision 在仓库外收据关联。该候选未签名、未安装，不作为正式发布或设备验收结果。

依用户本地自用测试授权，已从 `cac717e36cefd5b42d56f40bc5b6ecb039d9462a` 构建 r14.25.6 / 255 正式签名 Release，应用 / 构建输入与上述完整验证实现一致。JDK 25、Release R8 / fatal lint、完整 sourceCommit、不可调试、SDK 34 / arm64、16 KiB 对齐、API 102 及同产物 mapping 检查通过；V2 签名与历史证书一致。APK 4,258,233 字节，SHA-256 `6fbe09d53614eb2ce2034ebebb488f237b38eb5462e06218ed410cf56c6ddd7e`，交付到本机下载目录 `CustoMIUIzer-A14-r14.25.6-cac717e3/`；签名 / 产物证据在仓库外同专项的 `local-release-cac717e3/`。本次仅本地构建交付，未安装、未打 tag 或公开发行，设备待验项继续保留。

## 通知中心与控制中心日期

自定义日期单独启用时也准备共享时钟格式化能力，并沿当前原生 controller 的弱所有者偏好观察刷新；不再依赖状态栏或控制中心的“时钟自定义”开关。改写格式及清空恢复继续使用既有快照与原生回退；隐藏日期、默认配置不为未使用的格式化器解析能力。日期本身不启动秒级时钟，不启用已关闭时钟的样式或遗留秒数选项，Feature 的首次安装与重启语义不变。

已核对 duchan 国行、fuxi 国行 / 台版及 POCO munch 印尼版的 SystemUI 样本：MiuiClock、ControlCenterDateView 继承、controller 字段和 Calendar 三参数格式化接口满足当前窄契约。没有将 POCO Launcher 的差异扩散到 SystemUI，也没有新增宽泛匹配或备用 Hook。静态样本不代替实机；日期格式往返、横屏 / 旧版通知栏及 POCO 实际效果仍待验。修前回归和本轮查询 / 原始 Smali 留在仓库外 `customiuizer-a14-evidence/notification-date-20261028/`。

Deep：原激活条件下两项行为回归复现失败；修复后的完整 Host 为 419 个套件、3,792 项测试，零失败 / 错误、2 项既定跳过，包含日期独立格式化、不可用回退和日期不启动秒级时钟的新增回归。Debug lint、源码契约、文档和功能地图通过；工具 677 项通过、5 项既定跳过。未签名 Develop / R8 与 fatal lint、SDK 34 / arm64 / 16 KiB 对齐和工程 revision 来源通过。最终 DEX 无 Hook API 定义，八个设置入口的直接 API 指令、继承及成员签名引用为零；setId / getId 各一次，按同产物 mapping 归属共享 HookInstallation，replaceHook 为零。APK SHA-256 `b3f45970617939c77489a9d835f9442e33f3c64a0d5993e743371152ad4e5144`；完整源码身份、JUnit、APK / mapping 和查询留在同一仓库外证据目录。本批未签名或安装手机包，原有七个暂存技能文件保留；相关 CRLF 新增文件未混入任务验证索引或提交。

随后依用户本地试用请求，从 `97bcfd3dabbfe1f5b90ffceba51b439414bcddd7` 构建历史证书签名的 Release `r14.25.6`（255），4,296,069 字节，SHA-256 `869b1ae419d137286f47719af134cce2c21d951e49cd1921bd145a3ce715cad3`，交付至本机下载目录 `CustoMIUIzer-A14-r14.25.6-97bcfd3d/`。应用 / 测试 / 构建输入沿用上述已验证实现；本次 Release / R8、fatal lint、完整 sourceCommit、SDK 34 / arm64 / API 102、16 KiB 对齐、V2 历史单签名与同产物 API / mapping 检查通过，交付文件 hash 读回一致。APK / mapping、签名报告与 `local-apk-receipt.json` 保留在同一证据的 `local-release-97bcfd3d/`。用户自行安装，本轮未操作设备、打 tag 或公开发行；日期与 POCO 实机待验项继续保留。

## 动态岛可见输出驱动执行

2026-10-29 从 `eae9380433f06df0b20bcdec040299d8f5363ead` 的有效回岛融合实现继续，首次核对相关分支、有效 Worktree 与远端；没有遗漏相关未提交或未推送修改。波形使用已有 presentation 预绘制入口检查实际 alpha、空间、祖先裁剪与显隐；稳定不可见时取消帧、FFT effect / 音轨订阅和录音 Binder 读取，保留来源身份与最后有效电平。有限形态动画允许采样准备，隐藏期间不调度波形帧；复显延续现有时间基准，新录音来源独立清零，迟到 FFT 仍受 generation 隔离。没有增加监听器、轮询线程或渲染层。

G4 最终按用户选择采用 **32 段/侧**，保留原曲率积分、接触液面与所有弹簧参数；同时直接生成局部坐标，免去完成 Path 后的矩阵变换，无接触深度的融合值变化复用原路径。相对原 64 段，65,537 点同参数采样在 256 px 岛高的最大距离约 0.001784 px（16 段约 0.032763 px）；首段接缝切线偏差分别约 0.0643° / 0.6400°，原 64 段约 0.00513°，这些是现有有限三次曲线近似的接缝指标，不宣称严格 G4 样条。Android 原生路径对照覆盖 75 组高 32–256 px、宽 48–400 px 及接触 0 / 0.5 / 1：最大轮廓距离 0.001772 px、浮点边界差约 0.0000153 px。静态对照未见明显形状变化，但抗锯齿并非逐像素一致（累计 3,566 个像素变化、213 个半灰阈值翻转、单通道最大差 63 / 255）；动画视觉一致性仍需实机录屏，不能以同参数位置误差或静态图代替。

状态栏沿用逐个原生 View 的避让及 alpha / translation 所有权；有界几何快照覆盖共享祖先的矩阵、布局、padding、scroll、裁剪和显隐，仅变化时完整遮挡扫描与重新定位时间 / 电量。快照按现有布局收集复用、结束租约清空；预绘制仍检查原生变换与 alpha，不靠布局事件猜测动画终态。主机计数回归中，连续 120 次稳定预绘制的图标完整遮挡查询为零，连续隐藏不新增 Binder 读取，恢复不重复注册采样租约；两端端帽的实际 cubicTo 提交为 64 次，接触液面仍只加 2 次。这些是工作次数证据，不换算 CPU、GPU 或耗电收益，快照自身的设备净收益仍待测量。

验证按 Deep，复用降段及末次小修前的完整主机 435 套件 / 3,935 项零失败、2 项既定跳过及 Debug lint、679 项工具测试通过 / 5 项既定跳过；新增差异按范围补验：32 段的轮廓、融合、连续切换、窗口 / 触摸与生命周期 236 项产品回归通过，独立原生轮廓探针通过，末次录音清理的录音 / 生命周期 136 项通过，重叠范围不相加。最终源码的 Develop / R8、fatal lint 与同产物 API / 设置类加载边界独立核验，不触发远端 CI。证据与可复现的 64 / 32 段原生对照探针保存在仓库外 `customiuizer-a14-evidence/island-visible-output-20261029/`。本轮 ADB 无设备，未操作设备；音乐暂停恢复、锁屏 / 通知中心、横屏 / 挖孔、快速手势的动画录屏及实际帧时间、长期资源和耗电均保持待验。可见性 / 采样、G4 几何、状态栏扫描按三个独立提交回退，不合并 main 或发布版本。

## 动态岛高频交互与并发活动

2026-10-29 以最新有效 `fa7843f7e386902c3b571a16d944cbb02a6125a4` 接续。修前 Host 复现同一播放器未进前台时 30 次点击提交 30 次启动、30 次前台更新逐次完整渲染。原 actionMutex 只串行执行，取消 actionJob 不会撤销已发送的 PendingIntent；本轮由 Controller 持有最多 16 个目标（包、用户、会话 / 活动身份）的短期提交记录。发送返回后立即记账，不因任务取消、内容切换或窗口隐藏而撤销；未执行 / 失败只释放自身预约，确认进入目标前台后再离开即可重开，未确认的已提交请求在 2.5 秒后由下一次请求惰性过期。不同目标不共用点击限速，dispose 全部清空，不新增清理计时器。通话入口只有实际调用 Telecom 成功才登记。

原生时钟的主线程 dirty 合并、Publisher 相等去重、媒体 readGeneration / 单一读取任务、固定活动槽和优先级、View 的 pendingContent 与 A→B→A 复用、单一有限动画时钟及过期 Animator 身份保护均已存在，继续使用。新增容量一的 Main Handler 呈现请求，普通 live / 媒体快照和前台更新只提交最新状态；状态、暂停恢复边沿和返回准备仍即时处理。通知来源进入前台时即时消费旧通知，避免 App→Home 被合并后复活；紧急活动、通知 / Toast 接管确认、原生返回、安全与 PiP 路径保持同步，并消费已排队呈现。dispose 删除待交付回调。

同一时钟身份的更新不再因为新快照对象而取消有效点击。运行中时钟只修正确有变化的单调基准；不停止 / 重启 Chronometer、不重绑图标 / 内容几何，已归零但基准修正为未来的倒计时按需恢复。媒体短岛的纯元数据变化只更新内容快照、无障碍说明及确有变化的隐藏副标题，不重绑图标。View 对尺寸、camera gap / offset、最大宽度、对齐和内容 / 配置失效做比较，无变化不重算子布局；当前动画变换仍刷新轮廓并复用原 shape 缓存。内容、录音模式、字体 / 配置或真实几何变化继续更新。

FFT 与录音 Consumer 已有相同 Session / Binder 的启动去重、幂等停止、保留最后帧及 generation 校验；可见性与有限动画准备租约沿用上一专题。没有证据支持额外延迟释放、保活采样器、重写状态仲裁或再建动画控制器，因此保留现状。采样 / 监听在覆盖、暂停、裁剪、隐藏、功能关闭和 dispose 后的计数由既有回归补验，不宣称减少本已去重的采样器创建。

验证与原始反例保存于仓库外 `customiuizer-a14-evidence/island-interaction-20261029/`。针对性 191 项及最终动态岛 / Launcher 677 项 Host 回归通过，覆盖全部 23 个直接使用该 View 的测试类，以及时钟叠音乐 / 30 次点击、取消后提交保留、超时 / 前台往返重试、不同目标 A→B→A、原生计时修正、通知、PiP、动画中断及采样启停 / 释放。完整覆盖为 436 套件 / 3,944 项、零未决失败、2 项既定跳过：保留 Full 未受影响的结果，以最终补验逐测试替换受影响结果，不将重叠项相加；Full 首次发现的隐藏副标题和摄像头遮罩两项回归已修正，原始失败报告保留。最终源码 Develop / R8、fatal lint 与 Debug lint 通过；同 APK 无 Hook API 定义、设置入口无直接 API 引用，setId / getId 各一处且 mapping 对应原 HookInstallation，replaceHook 为零。当前相关源码契约 46 项通过；工具 / 构建 / CI 输入未改，其余复用上一轮 679 项工具测试基线（5 跳过）。新调度的夹具显式交付 Main 队列与取消后的协程释放，不降低原断言。当前 ADB 无设备，30 次实际触摸录屏、时钟覆盖后连续性、紧急抢占、Perfetto CPU / 帧时间 / 主线程耗时与长期资源仍待同 APK 设备验证；Host 工作次数不能换算设备性能收益。

## 动态岛来源与过渡精修

当前源码把来源状态（小岛 / 媒体 / 通知 / 系统活动 / 即时反馈）与小 / 短 / 长形态分开管理，共享协调器按已提交的来源、身份与形态决定内容交接。通知从当前音乐岛连续扩展，自动 / 手动高度沿用同一入口；通知关闭直接回有效系统活动 / 媒体，否则回常驻小岛或退出。展开媒体先收回同一短岛，再次上滑才抑制媒体。原通用的先缩短再退场已移除，顶部退出固定释放时宽高，清除尺寸残余速度，只向上移动。

自动展开长岛在 40 dp 高时改为 280–320 dp，居中两边至少 24 dp，系统圆角内缘优先；最多 8 dp 的弹性余量继续受真实安全区约束。媒体与已允许系统活动的 native 返回窗口共用 92% 宽落点，中心与高度保持，既有窗口所有权 / 取消 / 安全门禁不变。没有新增动画器、轮询或消息历史；行为细节维护在[动态岛说明](../features/island/README.md#动画实现与验收)。本轮回退单位为上述来源 / 动画 / 几何与关联回归，不包含 Dock、签名或公开发行。

新回归在基线 `0b0dc0aa02729c067c8f0cbbce1b295d30124d2d` 复现通知 / 媒体交接漏判、长岛边距不足及 native 飞入等宽三项失败；另用主机真实绘制复现长转短封面被内边距裁成 16 / 24 px，已修正并保留像素回归。高度采样不再让摄像头种子撑大已选高度；宽度限制同时用于居中位置与横向偏移，不能仅限制总宽。阶段源码 `9bfd8af0c6a4a7b4fd98dea5ac07ffd99150bb54` 的 Deep 完整 Host 3,608 项零失败、2 项既定跳过及 Debug lint 通过，全量工具 677 项通过、5 项既定跳过；未签名 Develop / fatal lint、同产物来源、R8 / API 102 / 设置类加载边界、SDK 34 与 16 KiB 对齐通过，APK SHA-256 `b5ffc2eae9b1b89d65588c7d147942bcb1bc237fdf6b96943a7155aeff1fdaf8`。证据留在仓库外 `customiuizer-a14-evidence/island-handoff-20261026/`。当前 ADB 无设备，未覆盖安装或进行真实动画录屏，所有触感 / 合成帧率和新窄落点仍待实机验收。

## 录音量程、Dock 轮廓与短上滑

追加截图反馈的录音顶高有静态原因：缓存的录音机 5.0.17.8（SHA-256 `c648d023556c7dd6b09a4a9fbcaa113fd34a53c8afdcc8377142ed889bc01bab`）高品质 WAV 使用 24 位有符号峰值，旧桥接先截为 16 位上限。当前先读取原生录音对象的格式并换算，再进入公共 16 位桥接；普通 WAV / MP3 和 M4A 保留各自原生峰值来源。MP3 75 / 100 / 192 kbps 的设置位于编码阶段，不作为电平量程。Service / 引擎 / 原生录音对象换代会拒绝旧绑定，不读 PCM 或文件、不增加录音权限。完整路径和原始 Smali 留在同一仓库外证据的 `recorder-format-contract.json`，不能代替当前设备各格式和音质的录屏验收。

按用户最终选择，Dock 磨砂、玻璃、自绘着色及降级外轮廓统一为原生圆角矩形。玻璃保留壁纸透射、折射、色散与高光，解析距离 / 法线改为匹配该圆角，移除旧 G4 法线纹理；G4 内缩装饰及动态岛胶囊保持各自既有职责。像素回归先在旧轮廓检出差异，再验证匹配原生边界及玻璃仍折射真实纹理。

上滑旧逻辑在小岛内约 6 px 的慢短释放仍不收起；当前意图与提交分别按 DOWN 时实际岛高计算，提前取消点击，UP 补判无 MOVE，保留锁轴、指针、取消及一次提交。只改本岛输入，没有修改全局上滑或系统 touch slop。

缓存评估只落实为原生录音引擎字段元数据的单 Class 槽：不持有 Service / 录音对象 / 电平，类变化替换，当前录音对象和格式仍在绑定时重新核验。主机 32 次同类型绑定只解析一次，切换类型重新解析；不据此声称设备 CPU / GPU、内存或电池改善。岛轮廓 / 文字效果、Dock 几何 / 壁纸纹理 / uniform 现有有界缓存继续复用，不增加保活进程、常驻采样或无界消息缓存。

追加性能要求已按本聊天范围复核：改进还包括列表稳定单次分组、去掉光标功能对 SystemUI 前台监控的依赖、按键路径改读当前输入目标及配置快照、搜索定位复用框架动画倍率、无关闹钟设置查询不复制参数，以及移除 Dock 旧 32 KiB 法线纹理。按项目已有 Telegram 参考，只在目标变化和可见采集租约内工作，保留停止重绘 / 采集、隐藏及省电回退；没有把更多缓存进程作为省电方案，也不引入其整套渲染框架。操作数、元数据解析次数和删除的纹理容量有代码 / Host 证据，整机耗电、进程 PSS 与页面帧时间仍未测量。

## 自动值、页面切换与数值输入一致性

核对位置尺寸 XML、运行时默认、旧配置迁移及系统几何输入。自动岛高改用原生开孔的实际竖向尺寸，避免宽开孔把岛高推大；状态栏、物理圆角、内容和手动配置继续按同一规则限幅。XML 默认与运行时配置的对照覆盖自动模式及明确手动 40 dp；系统三项动画倍率仍从当前系统设置读取，Dock 自动半径保留桌面面板资源、底部物理圆角上限及有界缺失回退。

页面 ObjectAnimator 原先先乘系统倍率，Android 又应用一次，导致非 1 倍时实际时长偏离设置。现只传基础时长，由系统应用一次；进入动画的可见性继续由 FragmentManager 管理。搜索定位的非动画延迟使用系统已维护的 ValueAnimator 倍率，避免临时同步查询设置。数值输入仍由 WindowManager 按实际键盘与系统栏避让，去掉额外上移 24 dp；校验、恢复默认、一次保存和业务监听顺序不变。旧实现的倍率和输入框偏移各被一项行为回归检出。

应用列表正选 / 反选排序复用已有稳定分组：每行一次成员判断，保留组内顺序、默认项、多用户和排除优先规则；每次发布重算，不缓存会过期的选择结果。临时空间限于本次发布的行数，沿用既有图标及应用列表缓存的失效 / trim 规则。来源允许 / 排除继续读取配置阶段的集合快照，没有为了优化拓宽空名单、增加进程保活或常驻任务。设备未连接，尚无页面帧时间、PSS 或电池 A/B 结论。

同类算法审查从正反选扩至以下实际调用链；低频、有界且正确的逻辑保留，不为统一形式引入全局缓存或合并不同业务的名单语义。

| 路径 | 核对与决定 |
| --- | --- |
| 应用选择、过滤和多用户 | 多选 / 黑白名单 / 单选复用单次稳定分组，空名单、默认行、切换选择和过滤往返有适配器行为回归。1,000 行同输入对照为 9,228 → 1,000 次状态判断；只证明 Host 操作数，非设备耗电测量 |
| 通知允许 / 排除及自动展开 | 岛读取配置集合快照，排除先行，普通空允许名单不拓宽；原生短信 / 日历保留各自类别规则。自动展开已在配置变化时复制集合，回调只做成员查询，维持旧参数解析和异常时序 |
| 小窗、PiP 与来源交叉 | 包、UID、用户和原生状态按标量 / 集合交叉，保持独立音乐不被其他小窗错误排除；不缓存会过期的任务 / 授权结果，不引入全局输入扫描 |
| 媒体及系统活动候选 | 音轨选择为一次遍历并稳定保留有效会话；系统活动为 EnumMap 固定八类线性优先级选择，不逐次排序。原生会话 / 音轨列表只在采集租约及原生变化边界获取，未改为按绘制帧查询 |
| 作用域、恢复选择与进程路由 | 授权集合每次操作只取一次，缺失项 / 已安装包以集合交叉；恢复和选择清理为冷路径。进程路由已有精确表和小型固定前缀，Feature 安装保持单次，未增加轮询或跨 ClassLoader 状态 |

本轮完整源码 `70964ff4081aa8357a4bc6afd707764b7e806604` 的 Deep Host 为 396 个套件、3,625 项测试，零失败 / 错误、2 项既定跳过，Debug lint 通过。全量工具 677 项通过、5 项既定跳过；新增页面输入后补验文档生成、EOL 和运行时合同工具 50 项通过。源码门禁、文档链接及生成指纹通过。未签名 Develop R8 / fatal lint、同产物来源、SDK 34、arm64 及 16 KiB 对齐通过；最终 DEX 没有 compileOnly Hook API 定义，设置 Manifest 入口没有直接 API 指令引用，既有类加载回归通过。setId / getId 各一次，同产物 mapping 对应 HookInstallation.intercept / confirmStableHookId，replaceHook 为零；没有新增录音权限。APK SHA-256 为 `e926a1533b4ed0d027954fb97a8bdff398ceadd6de0504cd623c0a54e1ed2307`，mapping / usage / configuration 与边界查询保留在证据子目录 `70964ff4/`。证据入口为仓库外 `customiuizer-a14-evidence/island-handoff-20261026/`。当前无设备连接，录音格式矩阵、动画 / 手势、键盘高度变化及页面帧时间仍待实机验收。

## 媒体岛的格式与真实电平

追加格式审查确认媒体不直接读取 WAV / M4A / MP3 / FLAC / Opus 文件；SystemUI Visualizer 返回的始终是转换后的 8 位频谱，原始 16 / 24 / 32 位 PCM、压缩码率与录音桥接不能混算。依 [Android Visualizer 合同](https://developer.android.com/reference/android/media/audiofx/Visualizer)，旧 NORMALIZED 模式会按内容放大，现改为 AS_PLAYED 并核验设置返回值及模式读回；原生不支持则释放，沿用轻量回退。音量减小或静音时柱高相应降低，不把源格式标签用作增益，也不读取文件来推断实际解码品质。

原生采集率高于 102.4 kHz 时请求 1024 点频谱，普通采集率仍为 512 点，始终服从原生容量；避免 176.4 / 192 kHz 下 512 点无法观测第一段 200 Hz 频率。只在采集创建时查询一次，不增加逐帧 Binder、解码器或 PCM 缓存；五个标量及既有静止停钟 / 隐藏释放继续保留。高采集率下增加的 FFT 工作限于该租约，设备 CPU 与功耗成本待测，不宣称本项提速或省电。

新原生后端回归先在 `447c31b7` 复现三项缺口：仍开启归一化、高采集率低频分辨率不足、忽略原生配置失败；失败 XML / 日志在同一仓库外证据 `media-format-before-contract.*`。补齐模式读回失败释放、原生容量上限、8–192 kHz / 256–1024 点静音及幅度对照、换轨首帧静音和变采集率不重启等回归。包含此前按键 / 导航 / 应用管理的 Deep 完整 Host 为 406 个套件、3,691 项测试，零失败 / 错误、2 项既定跳过；Debug lint、源码门禁、文档及生成指纹通过，全量工具 677 项通过、5 项既定跳过。播放器的真实编码 / 位深、扬声器 / 有线 / 蓝牙路由、offload 与受保护音频仍待实机，不将 Host FFT 输入当作文件解码测试。

当前完整候选源码 `b707dd80a73afb1b5cc70b060e7bccbbc2c8c1ff` 的两份矩阵输出确定性及 11 / 11 必需独立 mutation 再验通过，无遗留、门禁或清理错误。未签名 Develop R8 / fatal lint、同产物来源、SDK 34 / arm64 与 16 KiB 对齐通过；最终 DEX 无 compileOnly API 定义，设置 Manifest 入口直接 API 指令引用为零，既有类加载回归通过。setId / getId 各一次，对应同产物 mapping 的 HookInstallation.intercept / confirmStableHookId，replaceHook 为零，无新增录音权限。APK 为 `CustoMIUIzer-A14-r14.25.6-b707dd-develop-unsigned.apk`，SHA-256 `34396a6932d0e1f81591021d5348f4ed09f48433a61991aa5c87df2593a2d96c`；APK、mapping / usage / configuration、JUnit、来源、对齐及边界查询保留在仓库外同目录 `b707dd80/`，对应完整日志为 `media-format-*`。收口只更新文档，不改应用 / 构建 / 工具输入；本聊天未正式签名、安装、重启或发布。

## 功能边界与输入法收回键

按宿主稳定、隐私、当前所有者与异常语义优先核对共享路由 / 安装门禁及高风险调用链，保持每个开关只操作其目标。没有重新调整全局输入、导航高度、系统 Insets、通知解锁策略或其他来源的开关。

| 优先级 | 路径与处理 |
| --- | --- |
| P0 | 通知隐私、锁屏 / 应用锁、用户边界与接管提交：复核原生策略不可用保留原通知、呈现期间策略变化回滚、旧 Row / Entry 点击失效及只消费当前绑定。复用并补验对应行为回归；上表的锁屏 / AOD / 应用锁实机闭环仍是最高设备优先项 |
| P0 | SystemUI 原生失败和当前所有者：隐藏 IME 收回键已收紧为原生成功后才处理当前可见的返回按钮，未知接收者不改，原生异常原样传播且不访问按钮；原生 INVISIBLE / GONE 不覆盖。原生 / 模块 fatal 传播、返回值和单次 proceed 保留 |
| P1 | Launcher 图标归属、状态栏颜色 / Insets 与 PiP 交接：已有 owner / 租约边界和关联回归保留；新实现没有跨功能 setter 或恢复动作。设备未连接，原路线图的图标、全屏进退及连续中断漏接不记作已闭环 |
| P2 | 列表结构迁移、缓存扩容及持续资源优化：已有本轮有界缓存和单次分组；继续以具体测量决定，不先扩充后台进程或重构共享基础设施 |

IME 功能已抽出独立原生访问合同，只安装一个导航图标更新 Hook；字段和方法在安装前完成类型、实例成员和返回类型检查。可在同一所有者最多八层基类内定位，不增加全局 View / ButtonDispatcher.setVisibility Hook，不读取或改写键盘内容，不干涉输入法切换、全局返回或系统上滑。原生更新继续拥有恢复、旋转和重建；功能不缓存 View / 按钮，也没有额外定时器。原生异常后仍访问按钮、原生已隐藏仍写入的旧行为已被回归复现；同时补齐基类合同解析。

对 fuxi CN `V816.0.19.0.UMCCNXM`（SystemUI SHA-256 `2bf4f7e56eeda694e8f7de71c0acda2cae892f9847c6d6a747a52cfe5381e42b`）和 TW `V816.0.8.0.UMCTWXM`（`66386ad63878da129041fab7ae334d313257703515e4c9a179cb092292b32dc1`）完成只读 DexSQL 复核。`mNavigationIconHints` / `mNavBarMode` 均为实例 int；updateNavButtonIcons / getBackButton / getVisibility / setVisibility 完整签名一致，两区图标更新 Smali 相同，QuickStepContract.isGesturalMode 均比较模式 2。两区原生流程自身包含地区和 IME 绘制分流，保持其判断。静态证据不扩展为全部 ROM 或输入法自绘导航兼容结论；完整来源、查询、Smali 及失败回归在同一证据目录 `ime-*` 文件中。Host、静态检查及压缩产物收据见下节，实机仍待验。

## 音量键、全面屏导航与应用管理边界

本批沿现有进程和 Feature 路由审查音量键整组、全面屏导航 / 导航栏、应用管理及系统兼容选项。优先处理会取消宿主工作、把半组按键交给不同处理方、误用用户身份或让可选功能阻止原生行为的问题；不重构其他共享基础设施，不扩大 A14 支持范围。

| 范围 | 当前处理与保留边界 |
| --- | --- |
| 音量键控制光标 | 以当前 EditorInfo / InputConnection 判断输入目标，沿用应用黑名单与方向设置，不再同步查询前台包。每个输入法实例仅两个按键租约，包和方向在首次按下时确定，连接弱引用；仅接受成功发送的 DPAD down，配对处理物理 UP。键盘关闭、配置变化或换编辑器后不移到新目标，仍消费自己已接管的释放；孤立 UP、未接管连发、无编辑器 / 原始按键输入保持原生。直接输入事件沿用软键盘标志，不读取文本 |
| 光标与 SystemUI 分离 | 移除仅因光标开关而安装前台监控的条件，通话界面功能仍按自己的条件安装；生成矩阵只减少这一依赖，253 个 Feature 的身份与目标进程保持。未增加输入法作用域或权限，沿用现有输入法识别和用户授权 |
| 息屏音量长按 | 删除清空 PhoneWindowManager 全 Handler 消息的行为，只取消自有单个回调；回调取得后释放引用，旧任务不能清理新任务。按键码 / 设备 / downTime 使用不可变标量身份，延迟任务不保留可回收 KeyEvent；未配置方向、孤立释放、虚拟事件和非音量事件不接管，重复按下不重启计时。亮屏 / 已知电源组合和取消按键停止自有任务，短按 WakeLock 在 finally 释放；生产安装前完整核对 policy 方法和四字段 |
| 双击音量下 / 媒体反馈 | 双击音量下相机门禁仍仅在手电选项启用时覆盖，电源双击保持原分流。MediaPlayer.pause 先完成原生调用，成功后才发布可选暂停提示；流类型合同缺失仅跳过提示，可选读取 / 发送失败不阻止播放操作 |
| 全面屏返回 / 侧滑停顿 | 继续按原生旋转、分屏和每次初始化基线缩放，默认 60% / 100% 不重复作用；运行时值与 XML 的 20–95% 高度、100–400% 宽度边界一致。角落动作只接受原生触发类型及明确左右方向；侧滑停顿只在原生成功且方向为左右时执行，原生失败不取消返回或启动额外动作 |
| 导航栏 | 两区样本确认 OverviewProxyService$6.setWindowState(III) 与唯一三参数 createNavigationBar。直接安装这两个预检方法，避免构造时从回调列表末尾猜测对象或重复安装。只为主屏导航窗口准备原生手势线标志，只隐藏主屏导航栏；状态栏、外接显示和未知参数继续原生。保留原生读取该标志之前的准备时序，第二个安装失败撤回第一个 |
| 全限制 / 应用状态控制 | 限制页只复制标准 ApplicationInfo 并更改副本标志，原对象和未知厂商子类保留，避免污染其他读取方的系统应用判定。状态菜单绑定包名 / UID，校验实际 Activity 用户；不匹配时仅禁用该自定义项，确认执行前复核当前详情和 PackageManager UID，保留核心应用保护、原生权限与状态读回。销毁 / 重绑的旧页面不能执行旧目标 |
| 其他应用管理项 | 举报按钮、附加详情和初始排序仍限各自原生页面 / 列表；跨用户详情打开沿用既有 UID 校验。未新增自动停用、批量应用变更或权限绕过，不以缓存优化改变应用实际状态 |
| 系统与兼容性 | 日志确认沿用拒绝申请，拒绝接口不匹配则回到原生流程；不吞原生失败，fatal 保留。闹钟兼容只重写选中进程的精确旧设置键，无关查询不复制参数；系统侧格式化源仅用于 user 0 的选中调用者，其他用户及原生失败保留原结果。允许不受信任触摸的既有 WindowState 规则、电池优化持久化的 PowerKeeper 范围、国际版远程检测禁用与手动隐藏设置入口保持原合同 |

资源交换限于每 IME 两个轻量按键身份、每音量长按一个回调 / 标量身份和安装期反射元数据；没有新增轮询、后台进程、消息历史或活跃输入连接强缓存。回退单位为上述按键 / 导航、应用元信息 / 状态目标和兼容回退，保留既有设置格式。主机回归覆盖配对、取消、丢失释放、连接 / 页面替换、独立 Handler 消息、跨用户、原生异常和未知方向；本批 Deep 完整 Host 为 405 个套件、3,683 项测试，零失败 / 错误、2 项既定跳过，Debug lint 及源码门禁通过。全量工具 677 项通过、5 项既定跳过。ROM 证据为同目录 `navigation-cn-tw-contract.json` 与前述 IME 两区样本，不等于其他机型、输入法覆盖或设备动作验收。

按键 / 导航及维护源码 `447c31b7f7b5b069a1d079aba9c0648b694c6937` 的两份矩阵输出确定性、11 / 11 必需独立 mutation 均通过，无遗留 / 清理错误；未签名 Develop R8 / fatal lint、来源、SDK 34 / arm64 及 16 KiB 对齐通过。最终 DEX 的 compileOnly API 定义和设置 Manifest 入口直接 API 指令引用为零，类加载回归通过；setId / getId 各一次、replaceHook 为零，对应同产物 mapping 的 HookInstallation 成员，无新增录音权限。该 APK SHA-256 为 `e63a01c71883717f56320e5b8c778174431f213b182209a0f198decce51d6e2a`，证据在仓库外同目录 `447c31b7/`；含媒体追加修正的当前候选收据见前节。

本聊天先后追加的动态岛动画 / 来源、录音与媒体格式量程、Dock 外轮廓和玻璃材质、小范围上滑、自动值、页面切换、数值编辑、名单及交叉算法、功能边界、音量键整组、全面屏导航 / 导航栏、应用管理和系统兼容性均已有实现或明确保留结论。本次不将静态核对替代设备闭环：录音 WAV / M4A / MP3 各档、媒体格式 / 音频路由、连续音乐与通知交接、短上滑手感、键盘和多用户 / 外接屏操作仍需当前候选实测；旧路线图的锁屏隐私、Launcher 图标归属及快速中断漏接保持原待验优先级。

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

原生合同样本为HyperOS 1 / Android 14 合同样本 SystemUI、时钟 15.15.0、录音和日历 APK，使用 DexSQL 核对具体方法及调用路径。原候选 PrivacyItemController / OngoingCallController 订阅没有直接套用：本 ROM 部分注销入口被内联，秒表和部分通话候选又依赖通知，不能仅凭方法声明决定接入。

每类只保留一个当前标量状态；临时消息 / 反馈之后，优先通话、闹钟、倒计时、录音、秒表、隐私，再回媒体 / 小岛。同身份不会重入场；可见计时交给系统 Chronometer，暂停 / 脱离停止更新。两个源进程用匿名 Binder 存活令牌通知结束，不轮询进程；旧死亡事件、序号和 owner nonce 不能清除新来源。安装前先核对完整合同，部分安装失败撤销已装 Hook。细节见[系统活动合同](../features/island/README.md#系统活动)。

Host 完整 3,548 项回归无失败、2 项跳过；后续日历通道与存活令牌补充专项回归通过。全量工具 672 项中的三个资源 / 文档失败已修正并通过 51 项关联复验；生成链最后 25 项通过。确定性两份输出与必需独立 mutation 11/11 通过。上述为 Host / 静态 ROM 证据，设备双态、进程终止、同岛交接和录屏仍在本轮验收中；实机结果完成后更新本节。证据入口为仓库外 `customiuizer-a14-evidence/system-activities-20261026/`，不将短时采样解释为长期耗电改善。

相册删除等操作结果仍只复用已经完成的标准 Toast；应用内部 Snackbar / 自绘弹窗的成功、取消、撤销 / 恢复合同尚未专项接入，不把本轮系统活动能力写成已覆盖所有应用内弹窗。

## 本轮反馈修正与页面复核

已安装试用源码 `664ff9ab07dad6a4a895dacdc96acfdb4fe1a382` 修复屏幕坐标与岛体变形坐标混用的上滑判断；每次原生 CLOSE_TO_HOME 使用独立返回编号，新打开、触摸、配置失效与停用淘汰旧确认。48 次无新 DOWN 的原生返回及旧回调交错已有 Host 回归。该版实际短上滑录屏未打开播放器，但主屏幕中心页连续 20 次打开中断测试中，第 7、19 次结束仍停在小岛，没有恢复媒体；不能标为百分之百修复。一次目标失效诊断不足以证明根因，尚待进一步复核原生任务 / 前台与 PiP 准备门禁的时序，不放宽单目标、用户、隐私或 PiP 校验。

录音机 5.0.17.8 原生兼容全局版在本机安装于用户应用区，旧 SYSTEM / UPDATED_SYSTEM_APP 限制导致录音来源未订阅，表现为只有隐私提示且点击打开隐私设置。现按显式选择的原生包及实际 UID 订阅；峰值接口通过唯一调用关系与完整签名冷定位，可选失败不阻止计时。实时档复用五条波形，后台可见录制最多约 15 Hz 标量读取、最多 30 Hz 平滑绘制，其他档位不采样。通话、时钟、录音共用新增「上滑回岛」控制，真实通话不主动拨号测试，保留待验。

Dock 自动圆角已按用户磨砂截图反馈恢复 main 的面板比例，现有材质效果、手动 / 全宽和 G4 模型保留；短岛自动基准降至 160 dp，长岛和小岛参数不变。「灵动额头样式」明确对应 HyperOS 顶部系统胶囊，系统活动来源与即时反馈同层展开。

全页面使用既有原生页面导航回归遍历 4 大分类、34 业务页及 30 独立子页；专项覆盖窄屏中文、横屏夜间、重建、搜索、应用列表搜索与滚动分离，以及来源开关共享原集合。已导出 74 个页面 / 场景并复核，来源开关保持语义顺序。简版地图由真实 XML 再展开一级分组，详细地图、来源条件和语义目录同步，保留有效设置键。Host 完整 3,560 项零失败、2 项既定跳过；页面最终 28 项和工具受影响复验通过，确定性两输出与独立 mutation 11/11 通过。Develop / 正式签名 Release 构建及 fatal lint、同产物 R8 / API 102、来源及证书检查通过。实际安装 APK SHA-256 `96c7d8f558aa28b43d9974b1f6d707b04c5ad7eb0a1a96bc5bd3121648d1bb00` 已从手机读回，版本仍为 r14.25.6 / 255。

用户接手后已清理本轮自建录屏 / 界面快照和采集环境；没有留下 logcat / profiler 采集流，电池模拟已关闭。保留用户应用、原生服务及当前功能设置，未在接手后触控、锁屏或再次安装。后续仅静态开发，不依赖手机仍连接。证据在仓库外 `customiuizer-a14-evidence/return-dock-pages-20261026/`，主要入口为 `installed.json`、`continuous-return.json`、两段原始 MP4 和 `cleanup.json`；正常片段的原尺寸 PNG、联系图及无声剪辑另导出为素材包，不以精选片段掩盖上述两次漏接。

静态续作源码 `506667687dba17ba04932afdabc5220e9e6f8e82`：新增同一 controller 所有的动画决策组件，所有来源继续复用同一有限时钟；小岛接入、反馈后恢复与首次进场不再取决于原生「上滑回岛」开关，同一活动更新不重播。按用户反馈将音乐返回末段收束降至加强版的 75%，内容缩放由 8% 减为 4%；相近尺寸保持无额外回弹。录音的原生峰值改为固定低增益并保留 15% 顶部余量，采样及停止规则不变。自动尺寸下，正文较多的通知、较长的系统状态和含暂停详情的计时 / 录音可展开，长岛尺寸不变；暂停 / 恢复可跟随状态调整，用户手动展开或收起优先。完整 3,566 项零失败、2 项既定跳过，Debug lint 及全量工具 673 项通过（5 项既定跳过）。未签名 Develop 压缩构建、fatal lint、该 APK 的来源 / R8 mapping / API 102 边界、SDK 34 / arm64 与 16 KiB 对齐通过，APK SHA-256 `15609f26d1daca88c4a828d023a51e22a50ee4ffe96c10eb93d86d91413138a7`。这批源码尚未安装，动效、实际录音电平及来源组合仍待实机验收；新证据以同目录 `static-` 开头，与手机现有试用版分开保存。

DexSQL 对同一设备录音机 5.0.17.8 的复核确认：原生来电静音功能临时修改 / 恢复铃声模式，并发送「录音中，静音模式已开启」Toast；不代表真实来电，也不调整麦克风增益。当前不屏蔽录音期间全部铃声广播。自动切换的因果标记、原生 Toast 与模块反馈合并仍待专项核实，真实来电行为保留系统处理；证据为 `recorder-mute-native.json`。

Dock 磨砂圆角回归：已通过代理读回远端 main `0d6775f0316589c82fd9db46a707c9e5c5f79b95`。main 按原生桌面面板半径、半高的 64% 及物理圆角取最小值；此前 `b3f9a7ee` 改为屏幕优先、半高的 92%，并跳过面板尺度，浅 Dock 因此趋近胶囊。现恢复 main 的自动尺寸策略，并将它收回 DockCornerGeometry，通用 G4 只负责曲线。三种材质原本已共用同一半径入口，继续沿用；原生磨砂接口和自绘 / 折射路径接受相同结果，材质降级不更改半径，不加额外裁切层。

圆角修正源码 `9c47e6648098b2a3d93f61c2803642f3f4a1af21`：Standard 的 78 项 Dock 回归及 3 项共享岛体曲线回归、静态合同与文档检查通过；未签名 Develop 压缩构建、fatal lint、同产物来源 / R8 mapping / API 102、SDK 34 / arm64 与 16 KiB 对齐通过，APK SHA-256 `012df357efb62d71c69141ac28dc9e5301ba1f24461b2ee25132b383f21fb4a4`。源码比较确认半径函数与上述 main 一致，共享曲线主体及材质渲染器未改。尚未安装或进行设备材质切换录屏；专项日志前缀为 `dock-main-radius-`。

## 来源一致性与复用

本轮审查覆盖动态岛来源准入、浮窗反选、窗口返回、配置集合和动画取消联动；Dock 继续使用上节统一圆角入口，未再次改动材质或曲线。通知和启用来源保持正选，排除优先；小窗只反选有原生身份依据的媒体。持续活动保留现有有界 EnumMap 扫描，不为复用增加全局调度器或应用补集。

两条回归先在旧源码复现：排除 PiP 媒体会移除常驻小岛，独立录音的窗口返回发布器也会被关闭。现将排除移至媒体槽选择，保留同一窗口供小岛、通知和系统活动接续；仅剩被排除媒体时仍释放返回发布器。媒体返回失效只取消原生媒体接入，不再清除其他来源的交接进度。实际前台边沿统一刷新已选媒体 / 系统活动的返回快照，不增加定时请求。

IslandOptions 统一通知与返回准入，并复制应用集合，避免原地更新改变旧异步快照；设置时生成已启用且未排除的媒体返回集合。IslandLiveKind 统一状态来源和可返回窗口包，桥接与作用域继续按原来的四类生产活动、两个源包及通话窗口边界工作；PiP 复用包 / 用户 / 屏幕身份，可见性和收边仍按用途分别判定。原生方法、权限、协议、安装型开关与安全 / 任务门禁保持既有合同。

源码 `76d7e203ff3ab4e5fc2e83042a0de586533c010e`：Deep 的完整 3,573 项回归零失败（2 项既定跳过）、Debug lint 和全量工具 673 项通过（5 项既定跳过）；两条旧行为的失败证据及修正后回归均留存。未签名 Develop 压缩构建、fatal lint、该 APK 的来源 / R8 mapping / API 102、SDK 34 / arm64 与 16 KiB 对齐通过，APK SHA-256 `73280da417cafd081d65273bb0ee02a50dee8f46cd2b4def0b03e701ea162cc7`。证据在同一仓库外目录，验证日志前缀 `island-audit-`；尚未安装，设备与动画仍待验。此次主机修正不代表此前连续中断的 2 / 20 实机漏接已闭环，也不形成设备性能 / 续航结论。

## Hook 与持续工作审查

本轮为静态续作，手机测试环境已清理，未重新连接设备或启动监控。审查区分原生事件、有限动画及持续采样：被动接入只有在减少实际唤醒 / 查询 / 绘制时才可能省电，不能以挂载更深、组件原生或回调形式直接推断收益。此前同源 2 / 0 / 2 波形短期 CPU 对照仅用于确定优先级，不足以证明新版本耗电增加。

关键路径长期准则已整合到 AGENTS、安全实施 Skill、运行时合同及[优化门禁](../OPTIMIZATION_GATES.md#关键路径优化原则)，不另建文档或管理器。当前续审沿岛体、触摸、有限动画、媒体准备、Hook / 反射、Binder、布局 / 绘制及 Dock 的实际调用与 owner 检查；优先复用已有准备、快照、失效和释放机制，不扩大预热或常驻资源。

| 路径 | 当前决定与依据 |
| --- | --- |
| 媒体波形 | 已使用 AudioPlaybackCallback 与 Visualizer FFT 回调，不增加 AudioRecord / AudioFlinger 原始音频 Hook。确认媒体展开后子 View 隐藏而采集租约仍在；改由目标内容布局决定需求，长岛无波形即释放 effect / 监听，已有五柱帧只供短暂交接，恢复短岛取得一次租约。可见短岛的采样 / 绘制频率不变 |
| 录音峰值 | 继续仅在可见录制、后台来源及实时档下最多约 15 Hz 读取原生标量。设备缓存的录音机 5.0.17.8 存在 MediaRecorder 峰值与 AudioRecord 读取两条引擎，未证实安全的共用被动幅度回调；不把挂到 PCM 循环当成优化，不加录音权限或读取 PCM |
| 岛体 / 状态栏 | 保留一份有限动画时钟、约 30 Hz 局部波形插值、稳定停止及安全绘制门禁。相同几何不再向 Chronometer 重复写相同行数，真实单 / 双行变化仍请求布局；直接比较现有属性，不新增缓存。状态栏已有相同 owner / bounds 不重复避让的保护；计时复用 Chronometer，通知 / 媒体 / 隐私等按原生状态事件更新 |
| Dock | 原生编辑帧求值之后，无有效 Dock owner 即返回原结果，避免读无关 Transformation；不提前或重复推进原生动画。静态壁纸为事件失效与最后一次移动约 200 ms 后单次补采，动态壁纸最多 30 Hz；沿用隐藏 / 省电释放、三材质回退和统一圆角 |
| 温度 / 电流 / 网速 | 当前温度 / 电流监控只在相关功能启用时工作，息屏停钟、同值不重复发布，读取失败有有界退避；网速沿用原生 controller 的采样 / 消息节奏。广播不能提供全部现有数值及精度，保留必要采样，不用停更换取表面上的低回调数 |
| 手势 / 传感器 / 常规 Hook | 状态栏手势在 attach 准备依赖、detach 清理，触摸读取已发布配置；摇动监听随 Launcher resume / pause 注册释放，不下沉为全局输入扫描。手电 WakeLock 有特定功能条件、十分钟上限及释放路径，未证实泄漏，不凭静态疑虑改写 system_server 语义。安装、绑定、配置、通知和系统状态路径继续使用现有局部 Hook 与快照 |
| 准备 / 反射 / Binder | 媒体图标与单份至多 128 px 封面在工作线程准备，按会话 / 配置 epoch 失效，迟到结果不得发布；音频监听随采集租约释放。ReflectionCache 保留按 ClassLoader 的有界缓存与安全生命周期重试，录音标量 Binder 仍仅在可见租约限速读取。状态栏触摸的整组参数判空、共享 Hook 首次 after 检测保留为测量候选；未验证净成本前不扩张共享适配器或预热 |
| 设置与绘制组件 | 设置框架为 AndroidX Preference / AppCompat，列表和输入使用原生 View；自有岛体与五柱使用当前 Canvas / 缓存几何，特有折射沿用有限 Shader。复用系统能力须同时核对失效与降级，不能以增加通用 View 层级、换动画库或下沉私有接口推断更省电 |

动态岛相同行数回归在旧代码先失败：连续 120 次相同几何确认产生 120 次额外文字布局请求，修正后为零；高度改变与内容形态改变仍及时切换单 / 双行。JDK 25 下 Standard 范围的 8 个关联套件 / 52 项全部通过，含最新原生返回结束、文字、手势和触摸窗口回归，源码契约与文档检查通过。既有未提交技能的换行问题只从本任务验证索引隔离，文件与原暂存内容均保留。反例、通过 XML、验证日志与源码身份在仓库外 `customiuizer-a14-evidence/critical-path-20261028/`。这只证明所测模型的请求次数与行为；未构建 APK、重跑全量 CI 或做设备性能 / 视觉验收。

状态提交续审仅修改岛 View：遮罩按现有半径 / 形状比较后更新，无障碍描述复用已绑定内容与播放状态，配置刷新单独重建本地化文案，待切换文字仍立即可读。旧代码的 120 次相同几何产生 240 次遮罩失效、120 次相同绑定读取 120 次播放文案，修正后均为零；真实尺寸、头像形状、播放状态和语言变化保持提交。Standard 的 9 个关联套件 / 176 项、源码契约和文档检查通过，修前失败 / 修后 XML、Android 14 setter 字节码与输入身份在仓库外 `customiuizer-a14-evidence/critical-path-round2-20261028/`。ImageView 已有同 Drawable 保护，手势监听 setter 为简单赋值；Controller 期限集合位于事件渲染路径，缺少净成本证据，保留原调度与独立事件处理。Home / Dock 首帧纹理、Shader、采集租约、ReflectionCache 与 Publisher 沿用已有准备 / 去重 / 释放，不新增缓存或监听。未构建 APK 或做设备测量，不把调用次数换算为首帧耗时、帧率或功耗收益。

文字提交沿用 TextView 当前值比较：120 次紧凑曲名更新对相同隐藏文字的 240 次重复提交降为零，真正的作者、展开标题与暂停时钟仍更新。移除 `prefersLong` 未读取的宽度参数和没有生产消费者的旧 `MEDIA_RETURN_DELAY` 常量；两项返回回归改为确认后立即断言挂载，已在删除前的源码通过，未修改真实调度或时长。展开期限仍服务于长反馈，内容动效字段仍参与缩放；Chronometer 格式 setter 经 Android 14 字节码复核后保留。Standard 的 8 个关联套件 / 178 项、源码与文档检查通过；修前失败、修后 XML、引用核对与源码身份在仓库外 `customiuizer-a14-evidence/critical-path-round3-20261028/`。这是一处有证据的重复工作修正与两处无效接口清理，不代表设备性能或 APK 体积收益。

Toast 首次量宽在一次同步搜索内按需准备同一 TextPaint，未增加字段或常驻缓存；无可用文字空间时不准备，命中已有宽度缓存时不重新排版。旧实现一次搜索向 Android 14 主机模型的排版探测提供 8 个不同画笔，现仅 1 个；字体 / 形态变化后的新查询仍使用新画笔。测试仅记录画笔身份，排版继续执行该模型的 StaticLayout；多语言、大字号与单 / 双行的最小可容纳宽度、状态文案及反馈接续均通过。Standard 的 5 个关联套件 / 135 项、源码与文档检查通过，修前失败 / 修后 XML、实际 Builder 委托及输入身份在仓库外 `customiuizer-a14-evidence/critical-path-round4-20261028/`。弹簧预计算、逐帧几何缓存与既有失效 / 释放机制保留原实现；没有设备首帧、帧时间或功耗测量。

既有两项回归均先在旧源码失败：媒体长岛的隐藏采集未释放、无 Dock owner 的 120 次回调仍读参数 120 次。修正后验证长→短恢复只重新采集一次、重复元数据不重启、同一窗口不重建；无 owner 的原生调用仍为 120 次而参数读取为零。有 owner、原生失败传播及隐藏 / 锁屏 / 省电释放沿用关联回归。这是主机行为计数，不是 ART 分配、设备 CPU 或续航测量。

源码 `06044aa04a21f41be9ad72a051f78b52db8a0930`：Deep 的完整 3,575 项回归零失败（2 项既定跳过）、Debug lint 和全量工具 673 项通过（5 项既定跳过）；未签名 Develop 压缩构建、fatal lint、同 APK 的来源 / R8 mapping / API 102、SDK 34 / arm64 与 16 KiB 对齐通过，APK SHA-256 `852e3c7851f55af71c6f51e201c2d79300b2d6f2b9f2ecb5e63747c7424dc534`。证据位于既有仓库外目录，前缀 `power-audit-`，包括旧行为失败 / 修正后 XML、完整验证与缓存录音机 DexSQL 结果。未安装，动画仍须实机录屏，同设备同负载的采集 / 绘制 / CPU / GPU / 帧时间 / 电量对照仍待验。此前连续中断返回的 2 / 20 漏接未被本轮主机检查闭环。

## Dock 静态壁纸停止移动后补采

2026-10-29 从最新有效 `5e06c1f3efda22eed8dafcacdace91af0d36e235` 接续，专项为 相关工程记录（以现行源码与回归为准） / `codex/dock-static-backdrop`。前轮用户表示壁纸一般固定、不要求太实时，Dock 当时只完成评估。此次确认旧实现在连续 Matrix 变化和其后 450 ms 的原生绘制中仍可重复请求整屏壁纸，且原生绘制停止后缺少明确的最终补采；不据此认定用户此前所有磨砂回退均由该路径引起。

静态壁纸移动时沿用同一完整视口纹理，只更新真实坐标；最后一次矩阵 / 尺寸变化约 200 ms 后由现有单一回调补采一次，在途结果不能擦除新的移动截止时间。首帧及真正的壁纸替换仍按原速率门禁请求新像素，真实来源替换清掉旧 Bitmap / Shader，不复用身份未知的旧壁纸。合成器缩放可能在移动期间暂时显示旧采样，这是降低实时性换取少采样的明确代价；最后补采能否覆盖各 ROM 壁纸动画的终态仍待实机。保留动态壁纸最多 30 Hz、单纹理 393,216 像素 / 最长边 1,024、一个在途请求、有限恢复及隐藏 / 省电 / detach 释放；不增监听、Hook、缓存或第二时钟，不改光学 / 外轮廓。回退单位是 DockGlassBackdrop 调度、关联回归与两份既有说明。

验证按 Deep 复用未变化输入：四个新增 Android 14 行为反例在旧源码均失败，四段测试与失败时输入逐字节一致，修后全部通过；整组 Dock 13 套件 / 113 项零失败 / 错误。与上轮已核验完整基线按测试身份去重、替换受影响套件后，共 436 套件 / 3,948 项零失败 / 错误、2 项既定跳过；不是本轮重新运行全部测试。连续 120 次变换的模型中，移动期间只保留首次采样、停止后补采一次；旧实现第二次采样已在第 2 个后续运动帧替换纹理。保留动态刷新、坏帧有限恢复、旧代结果、真实来源清理、零 alpha 暂停、隐藏 / 省电 / detach 的行为断言；既有三处采样时序预期改为明确的单次补采及原速率门禁断言。

本轮源码 / 热路径 / 生命周期门禁、Debug lint、Develop / R8 / fatal lint 与文档 / 功能地图通过。工具、构建、生成器及规则输入未变化，复用可信的 679 项工具结果 / 5 项既定跳过；同 APK 的 compileOnly Hook API 定义、设置入口直接 API 指令查询为空，setId / getId 各一条并由同产物 mapping 归属共享 HookInstallation，replaceHook 为零。证据入口为仓库外 `customiuizer-a14-evidence/dock-static-backdrop-20261029/`；未签名验证 APK 的源码由外部输入哈希关联，不冒充正式签名候选。ADB 无设备，未安装 / 重启或采集录屏；真实磨砂回退原因、各 ROM 合成器终态、材质观感及 CPU / GPU / 帧时间 / 长期资源仍待同 APK 设备验收，不宣称耗电收益。

## Dock 全链路审查与资源边界

2026-10-28 接续同一工作分支 `codex/island-r14-25-6`，基准为本地 / 真实远端一致的 `4e69010308c3088819d2191551f7dbc0d0f9307f`，已包含 `7099c1f` 后的岛体成果。另一个 ROM Catalog Worktree 没有未提交 Dock 成果；两个失效的 detached Worktree 注册未清理。原有七份暂存原生工艺 Skill 原样保留，不纳入本任务。审查覆盖 HotSeats 安装、内容 / 拖放布局、编辑 / 解锁参与者、全宽继承、三种材质、Shader / 壁纸采集、手势、独立高度 / 边距 / 标题 / 容量、Feature 门禁及设置页。

### 问题、修正与回退

暂无已证实 P0；以下 P1 有明确源码触发路径，P2 为稳定状态的重复工作。没有据已有测试通过降低审查强度。

| 级别 / 路径 | 触发与根因 | 实施、失效 / 回退及证据 |
| --- | --- | --- |
| P1 · DockGlassBackdrop | 装饰仍为 visible、整块被原生矩阵移出屏幕时，accept 的未覆盖分支设 sourceDirty 并请求重绘，绕过有限恢复，形成自身反馈 | request 在已确认的完整视口内先核验可见覆盖；无可见像素不捕获。未覆盖完成帧使用相同有限恢复预算，真实 Matrix / 来源变化仍允许恢复，已有有效纹理保留。修前 120 个 40 ms 屏外帧捕获 120 次，修后为零，回屏立即取一帧；不改变分辨率、30 Hz 上限或折射光学 |
| P1 · 壁纸来源策略 | liveWallpaper 是 attach 时冻结的值，静态与动态切换后继续使用旧刷新策略 | 原生 appearance 事件重新读取 WallpaperInfo，并与纹理 generation 一起失效；读取失败保留旧策略。类型变化清掉旧 tick，静态稳定后无周期捕获；没有逐帧 Binder 查询、新 Receiver 或轮询 |
| P1 · Hook 安装 | 每个 findAndHookMethod 的失败返回被忽略；例如全局编辑帧 Hook 已安装，Launcher 绑定入口失败后仍留下部分回调 | 先解析全部现有精确声明，再按组安装，最后才安装 updateContent 激活入口；任一失败停止并逆序撤销已得句柄，fatal 保持身份且继续清理其余句柄。沿用 FeatureRegistry 单次安装；不增猜测式 fallback / 私有 ABI |
| P1 · 横滑仲裁与触摸失败 | 原横滑只检验横向距离 / 速度；detector 创建或处理异常又会跳过 chain.proceed，妨碍原生触摸 | 位移与速度都须横向占优，保留原有距离 / 速度阈值与左右动作；可选观察的普通异常隔离，原生 dispatch 仍恰好一次，原生返回及异常不变。纵向占优 / 等轴斜滑、真实左右横滑和 detector / native fatal 有回归 |
| P1 · 全宽首次 attach 所有权 | 未 attach 的 host 以自身为弱 key，将捕获 host 的 listener 存入全局额外字段；value 反向强持有 key，未出现 attach 时无法回收 | listener 只从回调参数取得 FrameLayout，不捕获 host；仍保留重复 attach 标记和正常激活 / detach 清理。修前捕获字段反例实际失败，修后还核验首次 attach 正常替换为 DockBackgroundView owner；不增加新监听或弱引用注册表 |
| P2 · 透明全宽与重复提交 | opacity=0 仍因 fullWidth 永久保留 PreDraw；全宽可见材质每个宿主绘制重复提交相同 drawable alpha / visibility，并重复同步 opacity | 透明背景只处理布局事件，稳定后取消 PreDraw；有视觉的全宽、Google blur / lens 保留必要原生帧同步。状态相同不重复提交，恢复透明到可见仍刷新原生 region bounds；未增加动画器 / 缓存 / 帧时钟 |

以上回退单位为 Dock 装饰 / 采集与横滑各自的生产文件及关联回归；恢复旧实现不改偏好、持久化、图标位置 / 大小 / 顺序 / 点击和拖拽区域、原生动画曲线或圆角契约。

### 保留设计与证据边界

- `AnimationSet.getTransformation` 的实际设备频率未知。CN / TW 的 `showBottomAreaEditPanelIfNeed(View, boolean)` 使用原生 Animation 并直接 startAnimation，没有可代替已求值矩阵的窄逐帧 Launcher 回调；两份目标中没有 Launcher 自定义 getTransformation 声明。保留先原调用、后弱 owner、最后读单项参数的路径；无 owner 的 120 次调用仍保留 120 次原生执行、零 Transformation 参数读取，不改原生调用次数 / 返回 / 异常 / 终帧。缺少 ART 对照，不增加另一套全局 owner 计数或动画系统。
- 完整视口纹理覆盖 Dock 几何变化，理论上几何只需 uniform；但原生编辑 / Home / 解锁也可能同时驱动合成器壁纸缩放。目标样本还存在 SuperDraglayer 异步 setWallpaperOffsets，POCO 另有 WallpaperZoomManager，事件并不等同于颜色变化。该轮尚无完整 compositor 变换信号及设备逐帧对照，保留 Matrix 触发和当时的 450 ms 有界跟随；后续按用户允许降低实时性的方向改为本页的停止移动后单次补采，不把“静态壁纸”误作“合成器静止”。材质半径 / uniform、纹理来源和视口各自沿用现有 owner。
- `getHotseatMaxCount=666` 在 MIUI / POCO 的已查直接调用者均包括 HotSeatsList.isFull、HotSeatsScreenViewContent.isSeatsFull / pushDragItem、BaseAutoInstallsLayout.get 和 OthersInRomConfigParser.get。它会参与初始布局资源名 / 配置选择，作用域确实较宽；未见按 666 直接申请数组的证据。缩窄需要保留恰好达到原生容量时的替换 / 文件夹 / 插入语义，以及既有超容量数据；目前没有实机 / 数据证据支持安全变更，因此保留并列为容量压力待验，不声称已修复容量风险。
- 三材质共用 DockCornerGeometry 原生圆角矩形与 DockBackgroundGeometry；背景是无触摸装饰。全宽容器继承 HotSeats 至共同 root 的变换，原生解锁属性仍由独立 child 持有；不松开图标祖先裁切。有视觉的 fullWidth / Google region / lens 必须观察父属性，现有事件不足以完全解除监听；监听本身不创建空闲帧循环。
- BitmapShader / RuntimeShader 仍各由单装饰持有，单张最多 393,216 像素 / 最长边 1,024、单个在途请求；HardwareBuffer 在 finally 关闭，worker 结果按 generation 拒旧，隐藏 / 省电 / detach 释放资源。零 alpha 原生过渡只暂停捕获。Shader 不可用 / 真正 GPU 失败沿原生材质及纯色回退，重建装饰重新尝试；系统全局模糊不由 Dock 重新启用。未发现需要新常驻线程、监控或缓存框架的证据。

ROM ARTIFACT：DexSQL 0.1.1 对 fuxi CN / TW 相同 MiuiHome `RELEASE-4.39.30.8604-08262248`（SHA-256 `a6546d51d9220039ed7ac143de249e9014202cfafeafd715237eb49f8f5a3f7b`）及 munch MiLauncherGlobal `RELEASE-4.39.14.7603-03051601`（`1b1eb43b4279ab6b6e7ec1712c74b7e7e45c5e00cee4df497aa6bf4b55ab81f6`）保存声明 / 调用者；CN 关键 Smali 核验编辑启动、容量、壁纸偏移和 native material detach 委托。定义与调用者不证明设备执行频率或 ClassLoader 运行成功。

### 验证与待实机对照

修前四项反例在旧生产代码实际失败；续审的未 attach host 反向持有反例也实际失败，修后 16 个关联套件 / 122 项通过，另有 Hook 各阶段失败回滚、fatal 清理、来源策略切换与原生触摸失败回归。最终 Deep 完整 Host 为 426 套件 / 3,858 项，零失败 / 错误、2 项既定跳过，Debug lint 与源码门禁通过；677 项工具测试通过、5 项既定跳过。原生所有权续修后重新覆盖完整 Host，最终新增反例已执行通过。未签名 Develop / R8 / fatal lint 与同产物 API 102 元数据、compileOnly 定义零泄漏、八个设置入口直接 Hook API 引用为零、setId / getId 成员 mapping、replaceHook 为零、SDK 34 / arm64 / 非调试及 ZIP / ELF 16 KiB 对齐检查通过。APK SHA-256 `02b11141eab37137331f92772310ab2bec700a3cf96ece7df6a67d79be9c1cd1`，嵌入的工程 revision 为基准 `4e690103`，完整源码哈希绑定已验证工作副本与最终提交，不作正式发行来源声明。既有暂存 Skill 的 CRLF 不属于本任务；验证使用从 HEAD 建立、包含本次新测试的独立 Git index，原暂存字节和真实 index 保留。

该批次缺少同 APK 的受控设备对照。屏外捕获次数与重复提交次数只是 Host 操作计数；CPU / ART 调用成本、GPU / RenderThread 纹理替换、帧时间 / 丢帧、Graphics / PSS / 长期内存及功耗均没有本轮前后设备数据。设备对照须固定 APK / ROM、刷新率、亮度、温度与操作轨迹，分别采集背景关 / 开、原生高斯 / 原生玻璃 / 液态、静态 / 动态壁纸、普通 / 全宽，覆盖静止、编辑 / 捏合 / 解锁、快速反向 / 中断、Home / 最近任务 / 文件夹 / 拖放，以及熄屏 / 亮屏、省电 / 恢复、Launcher 重建。动效必须录屏逐帧核对位置、速度、尺寸、alpha 和材质连续性；Perfetto / gfxinfo、CPU、GPU、PSS / Graphics 和功耗分别报告，不由 Host 数字换算。证据集中在仓库外 `customiuizer-a14-evidence/dock-full-review-20261028/`，不保存 ROM / APK / mapping / trace 入库。

## 动态岛全链路审查与关键路径优化

2026-10-28 沿 `codex/island-r14-25-6` 的真实本地 / 远端基准 `7099c1fcb5c34b9eb3fb444cbbfcdd3aa13ace61` 续审，而非回到 `main` 或较旧的 `6559cdf`。核对来源事件、ActivityState 仲裁、FormState 形态、MotionCoordinator 决策、几何 / View、WindowPresentation 首帧提交及反向释放，并检查通知、系统反馈、媒体、充电、勿扰、隐私、录音、计时、PiP、浮窗和排除规则的交接。保留各层实际职责、单窗口 / 单有限时钟、既有弹簧与手势参数；没有新增功能、调度器、缓存层、轮询或预热任务。

| 级别 / 路径 | 原触发与频率 | 完成的修正、机制收益与失效条件 |
| --- | --- | --- |
| P0 / `IslandActivityController.refreshFromNative` | 原生卡片临时消失时，每次合并后的媒体事件仍在 Main 读取播放状态、调用 `getActiveSessions(null)`；取消后续快照也无法避免已经发生的同步查询 | 将有效性检查和回退 metadata 读取移入现有 IO 快照 / Mutex；保留原生选中顺序、精确 token / 包、loading 与有限暂停期限。沿用 readGeneration / Job，发布另核对用户和 manager owner；新选择、用户切换、锁屏、停用和 dispose 淘汰迟到结果。收益是移除这段 Main 同步 Binder 工作，未测设备延迟 |
| P1 / `IslandWindowPresentation.prepare` | 同一持续活动的新快照生成值相同的新 key；原先按对象引用判定不同，每次更新都会释放可见租约、状态栏占用并停止采集 | 按逻辑身份判断，保留原可见租约；真实新活动及 EXITING 后恢复仍重新等待首帧。与状态、形态和动画层的身份语义一致，不合并独立通知 / Toast 租约 |
| P1 / `IslandStatusBarCompact.updateOcclusion` | 岛体在场的每次 PreDraw / 位置更新，对每个叶图标重复读取同一窗口的屏幕偏移 | 每次检查读取一组 host 屏幕 / 窗口偏移，继续逐图标读取当前裁切后边界。两图标 120 帧的 Host 叶坐标方法进入次数从 720 降为零；宿主偏移仍每次重读，原生矩阵、滚动、窗口移动及布局替换继续失效，无跨帧坐标缓存 |
| P1 / `IslandStatusBarCompact.positionKept` | 宽岛每帧重复提交时钟 / 电量 alpha=0；隐藏期间的原生新 alpha 又被覆盖，恢复时仍写入最初值 | 相同 alpha 不提交；抑制期间观察到原生非零值就更新既有恢复值，仍只恢复自身持有的零值。120 帧重复提交从 120 降为零，原生 .7→隐藏→.4 的恢复保留 .4；位移和父容器裁切原有所有权不变 |
| P1 / `LauncherMusicReturnHooks` 可选吸入安装 | update 或任一结束 Hook 安装失败后，功能虽不激活，已经成功安装的帧 / 结束回调仍留在原生调用链 | 本地安装组遇失败即停止并按逆序撤销已取得句柄；只有完整成功才允许 owner 激活。普通异常沿原生回退，清理继续尝试其余句柄，原始 fatal 身份及附带清理错误保留。仅安装期持有有界句柄列表，无新运行期注册表；不改变私有 ABI、原生调用或完成 / 取消时序 |

原生事件 Hook 仍在所属 A14 ClassLoader 冷解析；通知接管 / 隐私配对、PiP 与原生活动生产者已有失败回滚，本轮保留。未增加新 ROM 定位或宽泛猜测 Hook，也未以其他 Android / HyperOS 样本证明 A14。两份 PreDraw 分属岛窗口准入与原生状态栏避让，不能按同帧重复工作直接合并；PiP 拒绝后沿用一个合并的 Main 重检并隐藏 / 重新选择。原生图标属性动画不一定触发布局，故保留每帧相交检查。继续保留终止帧先移除更新监听再 end、旧时钟身份检查、exitGeneration、内容单槽及 SharedMemory / Bitmap / Visualizer / Controller 的原释放路径。

未实施 P2：为 render 的事件级几何 / 文字测量再建总缓存、按“无布局变化”跳过原生图标检测、合并来源状态管理器、统一缩短动效，以及扩大核心 Hook 安装重构。现有有界扫描和文字准备已有复用；缺少净成本测量，或会扩大失效、首帧安全和原生时序风险。没有以少量源码或更多测试作为收益。

五项旧行为回归分别检出 Main 同步查询、取消前已执行查询、逐叶偏移查询、相同 alpha 提交及等值 key 释放租约；修正后另覆盖闹钟卡片间隙、暂停恢复、真正的新来源、用户切换、dispose、原生透明度更新及可选 Hook 逐阶段失败 / fatal 清理。JDK 25 的 Deep full、Debug lint 与源码 / 生命周期 / 热路径门禁通过：424 个套件、3,846 项 Host 测试零失败 / 错误，2 项既定跳过；677 项工具测试通过，5 项既定跳过。工具测试首轮误继承临时验证索引而中断，已在清除该变量后完整重跑通过；该轮不计成功。验证索引仅排除原有七份暂存 Skill 的既存换行问题，原工作副本字节和暂存 patch 均原样保留。

同源码内容的未签名 Develop / R8 / fatal lint 通过；DexSQL 0.1.1 对该 APK 的 compileOnly 定义、八个设置入口直接 API 引用均为零，setId / getId 各一并核对同产物成员 mapping，replaceHook 为零。SDK 34、arm64、API 102 静态元数据、非调试和 ZIP / ELF 16 KiB 对齐通过。工程包嵌入基准 revision `7099c1fc`，仓库外收据按完整源码哈希绑定本任务工作副本，不作正式发行来源声明。APK SHA-256 `e6a076f664d5089009e5a49cee21b2794d2d3b1cbc4af892c76949dc8500c192`。反例 / 通过 JUnit、日志、输入哈希、APK / SQL / mapping 和收据保存在仓库外 `customiuizer-a14-evidence/island-full-review-20261028/`；回退单位为本轮四处生产文件及对应回归，不涉及既有暂存 Skill、Dock 或发布系统。

ADB 无连接，未安装、签名或执行设备操作。连续活动与通知 / 媒体交接、闹钟抢占及暂停恢复、PiP、锁屏、旋转、Home 快进出、上滑关闭、动画中断和真实资源清理仍待目标 HyperOS 1 / Android 14 设备验收；动画须录屏，性能须同设备 / 刷新率 / 场景做前后对照。本轮操作次数与快照边界证据不代表设备帧率、CPU / GPU、PSS、功耗或视觉通过。

## 原生组件取舍

本轮将设置 App 的组件与 SystemUI / Launcher 内的持续功能分开审查。设置页采用 AndroidX PreferenceFragmentCompat / RecyclerView、AppCompat 对话框和 Android View，并非自行实现整套控件，也不是直接加载系统设置 APK 的私有页面。保留官方推荐的 [AndroidX Preference](https://developer.android.com/develop/ui/views/components/settings)，不为「更底层」迁回已废弃的 android.preference；ROM 私有能力继续限于已有目标证据、宿主内使用及有回退的接入。

| 部分 | 当前决定与收益边界 |
| --- | --- |
| 设置开关、输入、对话框与列表 | 保留框架控件和既有生命周期；分组装饰使用同一 RecyclerView 的 ItemDecoration、缓存几何及 adapter 事件，不增加常驻布局循环。收益侧重正确的输入 / 无障碍 / 释放与复用，未测量提速或耗电 |
| 应用选择与搜索 | 原有四种应用适配器已经使用 ListView 的 convertView 和系统 Filter。搜索入口收敛为 [Filterable](https://developer.android.com/reference/android/widget/Filterable)，不再枚举四个具体类；原生 ArrayAdapter 及 HeaderViewListAdapter 的过滤也可复用。已有选择、排序、用户、黑白名单和图标规则仍归适配器管理；空 Filter 或已销毁视图安全无操作，不新增自建线程 / 队列 / 缓存 |
| 图标 | 继续从 PackageManager 取原生图标，保留现有异步加载、同键合并、128 项有界队列和字节缓存。工作线程闲置十五秒可退出；直接在绑定时同步取图标会扩大 UI 路径的工作，暂不替换 |
| 手势与拖动排序 | 岛体已使用 GestureDetector / ViewConfiguration；业务仲裁仍需自己的小状态模型。排序页当前为 ListView 上的行快照 / 位移，候选是 RecyclerView + [ItemTouchHelper](https://developer.android.com/reference/androidx/recyclerview/widget/ItemTouchHelper)，需完整保留排序、取消和触摸语义并录屏；未实施迁移、未声称收益 |
| Wi-Fi / 蓝牙页 | 当前为 ScrollView 内两段 ListViewEx，按全内容高度测量；大数据量下可评估带分组的单一可回收列表。此项是源码结构风险，仍需长列表计数、布局和滚动验收，不凭小样本将其认定为日常耗电来源 |
| 动态岛 / Dock | 自绘已调用系统 Canvas、ValueAnimator、Chronometer、原生材质及 Shader；保留一实例、有限动画、按需采集和缓存路径。系统接口不等于无 CPU / GPU 成本；减少无效 invalidate / requestLayout、层级和不可见工作比单纯替换控件更有依据，见 [Android 自定义 View 优化](https://developer.android.com/develop/ui/views/layout/custom-views/optimizing-view?hl=en) |

设置组件的改造主要影响打开、搜索、滚动和拖动期间的工作量；日常续航仍需优先核对宿主内持续采集、绘制及不可见时的停止。此次只实施搜索接口收敛，按 Standard 验证，未改控件外观、尺寸、动画、持久化、权限、Hook 或构建依赖。主机功能测试用于验证查询 / 清空、原生表头包装及空 Filter；它们不证明 ART 分配、设备帧时间或电量改善。专项证据留在既有仓库外目录，前缀 `native-components-`；未操作手机。

旧入口在有效回归中未过滤普通原生适配器或系统表头包装后的适配器，预期两项而仍返回三项，失败证据为 `native-components-red-valid-filter.xml`。修正后搜索入口 / 视图释放、四类应用列表元数据、排序与搜索分配的五类关联回归共 17 项零失败、零跳过，静态合同与文档链接检查通过；未改工具、依赖或宿主源码，不重复此前无变化范围的完整 / R8 检查。当前验证证明接口分发和既有列表语义，不形成设备性能或续航结论。

## 消息长岛策略

旧自动规则要求正文至少十二字或标题加正文至少二十字，且手动尺寸不会自动展开，导致短中文消息缺少正文空间。现按内容角色统一选择：已准入且含独立正文的接管通知默认长岛，自动 / 手动尺寸均适用；只有标题、空白正文、与标题重复的正文仍为短岛。不增加开关，不改变原生 Heads-up、权限 / 隐私、允许名单与点击准入；短信和日历等经同一通知入口接入的正文也沿用此规则。

内容选择与尺寸配置分开：自动仍受现有长岛宽度及安全边界限制，手动使用用户的长岛宽度；首次直接进入目标长形态，不把短岛作为中间状态；已有小岛 / 媒体从实际尺寸直接形变到长岛，通知后恢复媒体仍复用原窗口和有限时钟。保留现有两行省略显示，不加滚动、正文历史或后台读取。同一租约手动收起后，原生重复绑定 / 普通状态刷新不会重新展开，也不续展示时间。短反馈、长系统状态及持续活动继续原有规则。

顶边入场原先在长岛目标下仍取短岛宽度起步；逐帧回归复现目标 300 却从 160 开始。现改为顶边按最终宽度入场；中心仍沿挖孔种子直接到最终目标，两者不设置短岛停靠或第二段入场。当前可见实例继续从实际几何和速度重定向，沿用原有有限时钟与时长。长岛上滑关闭的收短 / 退场规则保留。

旧源码的三项消息策略失败及一项入场宽度失败已留存，覆盖短正文、原生消息实际长岛 / 返回同一媒体窗口、手动尺寸及两种入场的同一时钟；修正后完整 3,578 项回归零失败（2 项既定跳过）、Debug lint 及文档链接检查通过。工具链未变，复用同轮全量工具 673 项通过结果（5 项既定跳过）；证据前缀 `notification-long-` 与 `direct-long-`。主机逐帧断言不等同实机录像或帧率；此次仅静态续作，未重新安装或操作手机。

最终源码 `2aa7ff057d6324af6d5d6764748f761ee858ec3a` 的未签名 Develop 压缩构建及 fatal lint 通过；同一 APK 的来源、R8 mapping / API 102 边界、SDK 34 / arm64、无新增录音权限与 16 KiB 对齐通过，SHA-256 `7fec11be1a261ba94ce7f67233dd452565c6ae58167b109145a9dea70f88be96`。后续收口只补本段证据，未改变应用、构建或测试源码；设备动效与耗电仍按上述待验范围报告。

## 设置页面与共用行绑定

全量入口复核发现三个共用问题：右侧数量 / 当前值曾隐藏下方说明；匿名说明被当成独立功能行而在文字前画分隔线；警告行的标题色可能留到复用后的普通行。现由共用绑定入口分别更新标题、说明和值，清除旧行内容并恢复普通 / 禁用颜色；右侧值仅在需要时创建。分组按说明的归属决定分隔线，介绍文字不单独切行，角色沿用 adapter 通知失效的缓存，不在每帧遍历设置树。

动态岛主页统一为展示方式、内容、外观、交互。小窗媒体排除归媒体页，常驻小岛归交互，展开详情归手势；公共波形和进出动效进入“波形与动效”，录音可在媒体关闭时独立使用此配置。活动来源展开为原生开关时保留整组说明。31 个独立设置子页与生成目录共用被动的进入箭头，搜索路由随规范 XML 生成，原有 685 个叶子键、类型和默认值保留；细节与下一层功能入口见两份生成的功能地图。中文样式名保留“灵动额头样式”，避免混淆原生系统提示的来源。

此轮按 Deep 核验共用偏好绑定、缓存装饰、目录生成和原生导航；有效失败回归覆盖说明被隐藏、无用值控件、分隔线与警告颜色复用。主机原生渲染覆盖四个主分类和独立子页，检查窄屏中文、深色 / 横屏与动态依赖；它不等同设备截图、动画录屏或耗电测量。证据继续存于仓库外 `customiuizer-a14-evidence/return-dock-pages-20261026/`，前缀 `settings-review-`、`settings-community-`。设备保持用户日常试用状态，连续打开打断偶发未回岛仍按原待验事项保留。

完整 3,588 项 JVM 回归零失败、2 项既定跳过，Debug lint 通过；676 项工具回归通过、5 项既定跳过，后续一行群链接格式与 Issues 移除再通过 102 项发布 / 文档关联回归。当前生成链两次输出的 40 个文件逐字节一致，其中 39 份 XML；旧叶子键的类型、默认值和枚举合同完整保留。关于页新增原生点击验证，确认展示链接正确、进入页面不自动打开外部应用、一次点按只产生一个跳转。

应用 / 构建源码 `ebb36a815b601c322763c36de8a6d7b20aeb52bf` 的未签名 Develop 压缩构建和 fatal lint 通过，APK SHA-256 `d309ec612fd3434f94080167ff9b985f3cc7d49715f6e623d580cf5e2737013a`；同一 APK 的来源、R8 mapping / API 102 边界、单 DEX、SDK 34 / arm64、无录音权限及 16 KiB 对齐通过。进程矩阵两份输出确定性、11 / 11 必需独立 mutation 通过，无门禁或清理错误。后续只补文档地图生成器：关于页目录从原生布局读取，双语标签变更可跟随生成，13 项关联回归及文档链接检查通过；应用 / 构建输入未变，不重建 APK。

按用户授权另从最新源码 `ba9fcc7041fb825b09496f891f8128eb00f51389` 构建本地日常试用的正式签名 Release：r14.25.6 / 255，APK SHA-256 `f83108096e614e759254a3678a1ddae2b0633914bceaa1836dc20bff18cb81f3`。复用上述未变化范围的代码回归，补验 Release 压缩构建、fatal lint、同 APK 来源 / R8 / API 边界、SDK 34 / arm64、单 DEX、无录音权限、16 KiB 对齐及唯一历史正式 V2 签名，均通过。APK 与校验文件已置于本机下载目录，完整收据、mapping 和检查留在仓库外 `customiuizer-a14-evidence/daily-release-20261026-ba9fcc70/`；未操作设备或创建公开发行，日常安装与实机反馈由用户进行。

## 交流入口与发布文档

公开交流统一为 [Telegram 群组](https://t.me/customiuizerA14)。两仓 About 简介已精简为“米客 A14｜CustoMIUIzer A14｜HyperOS 1 / Android 14”，主页指向群组；当前源码与下载仓库双语 README 已移除 Issues 反馈入口，工程记录的 Issue 引用保留为文字。只对两仓最新 r14.25.5 发布说明末尾附一行普通群链接，不增加大标题；原正文、版本、标签、来源和全部附件的身份 / 摘要读回一致，其他发布未改动。

后续入口以 `tools/release-policy.json` 为单一来源：应用 BuildConfig / 原生关于页、新发布说明和下载 README 生成器共用链接；旧原候选恢复可保留无群链接的历史正文，后续核对仍严格绑定已接受的正文、标题、来源和附件。关于页将项目 / 群组与赞赏分组，只有用户点按时才打开链接。源码发布链变更保存在当前工作分支，纳入后续版本；本次公开文档调整未发布新 APK。公开修改前后读回保存在同一仓库外目录的 `community-` 证据中。

## 最近任务堆叠调研

[AOSPStackingRecent](https://github.com/C70246247/AOSPStackingRecent/tree/89aa34d9da59b1bbd119251a718e19080c5e24fa) 目标为 AOSP Launcher3 / Pixel，不是本机 MiuiHome；该源树未见许可证，暂不复制代码。其 `RecentsHook` 在绘制链刷新偏好、反射读写额外字段并嵌套遍历卡片，README 也保留动画缺陷待办。只参考重叠、缩放和连续消除的交互目标，不引入该热路径。

A14 Launcher 样本的 DexSQL 核对确认：`TaskStackViewLayoutStyle.create` 仅值 1 选择横向，其余回纵向；两者分别使用 Horizontal / Vertical 算法与滚动器，不能把较新 ROM 的布局值直接移植。横向 `getTaskViewTransform(int,float,TaskViewTransform)` 已复用传入对象写 scale、alpha、rect 和可见性，可作为低开销局部适配候选；还需核对命中范围、层叠顺序、划除、分屏、小窗及原生应用窗口交接，不能只改 scale / translation 后宣称可用。首版建议仅适配横排，纵排保持原生。

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
| 应用自有小窗 | `fde05c5`：原生 Session 可见集合 + 显隐 / 死亡事件，按用户及默认屏幕过滤，有界扫描 / 快照，不留窗口引用、不轮询、不注入应用。旧整岛隐藏曾有设备证据；当前按可见 TYPE_APPLICATION_OVERLAY 的 UID 反选媒体，独立活动与小岛继续显示，组合仍待新源码实机验证。新 owner 拒绝其它 owner 回复和旧序列，鲜活全量事件可补足丢失的首份回复 |

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
| PiP / 小窗 / 手势 | 原生 PiP 状态和身份约束媒体岛；可选按身份排除小窗媒体来源；全局 / 音乐手势增加完整隐藏和原生边缘收起 | 当前 ROM 已验三种双击入口、输入撤销 / 恢复、隐藏时播放、跨锁屏及原生 freeform；应用自有 Overlay 已补验；253 普通 PiP 开关两态为历史验收，来源反选的新源码另验，取消 / 快速替换和多显示屏仍有边界 |
| Launcher 音乐返回图标 | 原生无图标目标避免借用桌面图标，迟到状态拒绝接管已选择的图标；原生完成 / 取消策略保持 | 旧版错误浮动副本及末端恢复的不足保留在证据中；253 当前页有 / 无图标快速返回已由用户验收，额外取消和复杂组合仍按下表核验 |
| Dock / 发布 | Dock 合同保持；在 `3e0709f` 两公开资产、签名 / 证明及历史恢复上，补入 `0bbc1b6` 的创建 / 上传后草稿标题与说明核验 | 篡改须在公开前失败；历史标题只在原候选恢复时接受 |
| 正式文档 / CI | 已按真实 r14.25.3 发行修复下载仓库文档；正式源码基线 / 完整 diff 进入既有审核，双端验收后同步文档，原恢复入口支持 docs-only；CI buffer 与 Actions 文件输出隔离 | source-only / 预发行、幂等 / 并发 / 未知响应和失败重试有回归；正式状态以双仓实际资产为准 |

### 通知与媒体

- 通知接管先确认原生隐私更新监听安装成功，再安装 heads-up 接管；监听缺失 / 安装失败时继续原生通知。后项失败撤回本次监听，平台撤回失败也不启用接管；普通错误沿既有诊断隔离，安装与清理同时致命失败时保留原始异常身份并附加清理错误。不新增重试、全局状态、轮询或私有接口匹配，Feature 的单次安装与重启语义保持。
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

默认关闭的 `system_island_hide_in_floating_window` 保留旧设置键，当前含义为「小窗来源排除」；它是安装快照，完整重启手机后生效，普通偏好刷新和 View 重建不会提前应用。开启后只反选可确认属于当前用户 / 默认屏幕 PiP、系统小窗或应用悬浮窗的媒体，不全局隐藏岛；独立音乐、允许通知、系统活动、普通反馈和小岛继续各自的准入与原窗口交接。系统小窗在 attach 和 native 模式事件时串行查询一次，迟到结果按用户 / generation 作废，无持续轮询。返回发布器保留独立活动，只剩被排除媒体时释放；新前台边沿继续核验快照。关闭来源排除后允许媒体与 PiP 共存，自动 PiP 的视频窗口仍保持原生转场，不参与音乐返回窗口收拢。

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
| 岛来源与长短过渡 | 音乐与秒表连续点击互换，确认无 750 ms 重入等待；上滑退出当前来源回有效活动 / 媒体；自动 / 手动高度、系统圆角与极长正文；默认 native 84% 宽 / 86% 高与独立缩小下移的吸入落点、吸入首段收紧 / 撑开和纵向反馈、两种返回的液面接触及最小挖孔余量、左右锚定揉动、前缘轻微透明度、完成连续收敛及快速接续必须录屏确认 |
| 波形 / 输入 | 当前用户 UID / 唯一会话和音轨、权限 / AppOps、蓝牙 / 远程 / offload、隐藏 / 暂停 / 切会话释放；真实挖孔、字号 / 密度、慢短上滑与取消、窗口外原生下拉 |
| Dock / 音量 / 维护 | 连续拖拽、编辑 / 文件夹 / 最近任务、壁纸 / 深色、材质与裁切；插件重建、无障碍超时、振动 / 高音量确认；维护进程中断及准确恢复 |

图标诊断至少关联 ItemIcon / binding、内部图像和父链的 alpha / visibility / clip / Drawable，以及 WindowElement / floating / mAnim、target、running / merge / finish。若需临时诊断，仅 Debug、指定目标和离散事件使用有界单调时间日志，取证后移除；不每帧记录或采集私有标题。

性能另做同设备、同负载 A/B：CPU / GPU、线程、PSS / heap、帧时间、分配和日志频率。实时波形与轻量、Dock 材质分别测，不将静态预算、Host 数值或旧 APK 结果换算成当前设备收益。

## r14.25.6 手机反馈跟进

沿 Issue #63 和 `codex/island-r14-25-6` 继续，基线为 `5cb61b7e`；源码改动尚不等同已安装 APK。仓库外证据入口为 `私有证据归档`。验证范围为 HyperOS 1 / Android 14，设备身份与原始状态仅私有保留。

- 原生活动在来源前台时不登岛，返回目标独立有效；已有岛接入增加等尺寸承接，迟到确认不重弹。普通交接 300 ms、取消 250 ms、二次形态上限 380 ms；长岛按实测内容从短岛宽起增长。上滑点击否决覆盖短斜滑、历史采样与回落，不改全局导航。
- 原生窗口目标改为岛体 84% 宽 / 86% 高且终点不透明；选中窗口增加有界硬件纹理网格，复用 ROM 进度与 finish / cancel。最多约 8 MiB、1200 ms，所有者与释放、静态纹理及失败回退见动态岛功能页。Apple HIG 仅作连续反馈关系参考，不把候选当作 Apple 原算法或已验证性能收益。
- 录音前台保留静音提示，离开后录音接管，收集中的同来源反馈不迟到抢占；后台录音机 Toast 继续原生位置。独立铃声变化和真实来电不屏蔽。已补 pending / visible / late lease 行为回归，ROM 自动静音仍待候选录屏。
- 公共实时显示增益 100–800%、默认 200%，录音峰值采用去噪后的固定平方根响应，不改录音 / 播放音量或自动开启采样。输入法验证样本证明其常规按键分发绕过原 superclass hook，改接框架 InputMethodSession 分发，保留一次完成和按下 / 抬起配对。
- Dock 编辑材质保留 native 终帧，宿主清动画后才释放变换。有效对照 私有采集证据 在排除本模块 SystemUI 作用域后仍出现控制中心菜单项与通知页叠影，尚未排除其他模块，不能据此宣称纯 ROM 缺陷。临时作用域已恢复。另在恢复原生解锁动画后录制 私有采集证据，旧版 Dock 材质与图标一起进入；测试设置已恢复。

已通过完整 `verify.py full`（JDK 25，Debug 编译、全量 Host 与 lint）及最后网格裁切的定向复验。全量工具 677 项中的新设置语言与生成指纹两处失败已修复，对应 15 项及 13 项复验通过，5 项既定跳过；同一源码 `54760df80133503bbb68704d753f4a8f9a500d81` 的 Develop / R8、fatal lint、16 KiB 对齐及 APK 来源检查通过；同产物 DexSQL 核验 Hook API 定义为零，manifest 设置入口及本次共用元数据没有 Hook API 直接调用 / 类型指令。未签名候选 SHA-256 为 `149811a5c9d982318bc84653cc60389dd5cda47dcdf9351aba0437ff3e916481`，4,272,610 字节，mapping 与结果保存在同一证据的 `candidate-54760df8/`。已获当前任务的本机签名和覆盖安装授权，并合入后续设置精校候选；按用户最后要求，安装、环境恢复及重启完成后停止，由用户自行测试。新版动画、手势、输入法和波形没有新增实机通过结论。原始视频与命令事件分开保留，动画最终结论只记录实际候选录屏；不把 Host 或旧版基线作为新版实机通过。 本次测试秒表已归零、临时录音及输入测试应用已移除，ADB 采集及手机临时文件已结束 / 清理；媒体音量、息屏时间及模块设置恢复初始快照，VPN 保持连接并锁屏。

## 设置项精校

沿当前工作分支继续，范围为系统高级设置、手机管家、应用管理、桌面兼容与修复，以及充电亮屏设置。设置键 / XML 依赖、40 条页面对应进程路由及充电选项的两个进程入口已核对；安装样本及 DexSQL 原始合同位于仓库外 `私有证据归档`，不将静态合同写成逐项实机通过。

- 扩展电源菜单仍使用 A14 系统备用菜单，说明明确该外形切换及 SystemUI 重启条件。只在全部入口安装成功后绕过已核实的单个 GlobalActionsPlugin；按钮标题按线程 / 菜单作用域准备并读回确认，两项完整准备后才加入。当前菜单持有短期身份，旧菜单确认失效，取消或重复点击不执行重启；原生按钮保持原调用。只测试确认门禁，不执行 Fastboot / Recovery 重启。
- 应用管理排序仅在已核实读取排序键的 ManageFragment 安装一次，初始值写入 Bundle 副本，保留原生恢复的用户选择；独立 AppFragment 首页不接管。附加详情直接随当前 AMAppInformationFragment 创建，只把弱页面 / 屏幕引用和包 / UID 绑定到新增条目；删除全局最近页面 / 包信息及逐页重复安装，旧条目不能操作新目标，保留 64 位版本号。启用应用采用显式 ENABLED，按实际 enabled 状态读回，保留 UID 与既有核心包保护。
- 桌面支持的 SpringAnimator 使用时长比例的平方反比调整刚度，保留原生阻尼与参数顺序，消除接近 1 的突变；默认比例直接原调用，不物化参数。应用信息入口读取明确 UserHandle 身份，未知元数据保留原生调用。统计开关的说明范围收紧为桌面使用统计。
- 充电模式保持 1 默认、2 抑制已核实的充电唤醒并隐藏动画、3 只隐藏动画。已安装的 SystemUI 回调读取推送快照，回到默认后恢复原生动画判断；system_server 仍仅在模式 2 启动时安装，首次启用或从模式 3 切至 2 需要设备重启。应按模式语义区分行为，不能把模式 3 的亮屏判为模式 2 失效。
- 高级设置中的“禁用系统完整性检查”按实际范围更名为“允许旧版 APK 签名方案”；强制深色文字明确为用户应用开关，温度文字明确为智能充电页。共享菜单、打开方式、截图、USB、应用安装 / 安全与侧边栏等原有开关、排除、权限及生命周期合同保留，未扩大停用或接管范围。

历史设备候选仅具备当时记载的包身份与部分功能证据，不能扩展为后续实现的验收结果。个人使用状态和原始设备记录不公开；当前有效结论以上方待验范围为准。

## 2026-10-27 设备反馈续修

本轮以已安装 Dev 的后续反馈为输入，继续 Issue #63 与原工作分支，源码起点为 `66c2ce99`。没有 ADB，不进行安装、重启或设备验收；证据入口为仓库外 `customiuizer-a14-evidence/island-consistency-20261027/`。共享机制继续由 FormState、MotionCoordinator、View 的有限时钟及原生窗口 owner 分工持有。

验证与产物：`e1b5966d` 应用输入的 Deep 为 416 套件 / 3,770 测试，零失败 / 错误、两项既有跳过；工具 677 项，五项既有跳过。其后只补 Dock 预算边界，`b17b29cc` 由五个关联套件 / 三十项测试与全套静态契约覆盖，其余应用 / 构建 / 工具输入保持。最终源码的 Develop R8、fatal lint、Debug lint、SDK 34 / arm64、ZIP / ELF 16 KiB、API 102 及同 APK 的成员映射核验通过。候选为未签名 Develop，4,278,302 字节；APK、mapping、完整 JUnit、反例与收据留在上述证据目录，不以构建或模型请求计数代替设备效果。

本地 Release：本次明确授权后，从 `9591f8fa3c345d0ae0aafd41cb9382c595c34793` 生成 r14.25.6 / 255，保留历史正式证书。Release R8 / fatal lint、V2 单一历史签名、完整 sourceCommit、SDK 34 / arm64、ZIP / ELF 16 KiB 及同 APK 的 API 102 / 成员映射均通过。APK 为 4,282,353 字节，SHA-256 `a4ca5097f22afbc269288a97797e62016a43ad05a61012551ecc44e2e5d47db2`；交付本地下载目录，证据位于上述目录的 `local-release-9591f8fa/`。仅本地交付，不代表已公开发行或完成设备验收。

正式版安装后的新反馈以 `d80fdb82` 为源码起点，无 ADB；本轮证据在 `customiuizer-a14-evidence/island-followup-20261027/`。用户确认的 #1 是底部手势条回桌面：同一 Toast 的几何查询会把长岛字号 12.22 px 重设为 13 px，已有旧代码失败证据；现将字体刷新限于实际配置变化，重复查询、配置刷新与同内容返回保持排版。长岛将封面 / 头像及曲名 / 发送者放左侧，独立歌手 / 正文靠右，挖孔两侧各自截断；计时仍交给 Chronometer，高度不足不强塞两行。菜单统一为「Toast 反馈」，沿用原设置键。

吸入改为从已接受返回的首个有效原生帧计进度，网格从真实接管位置开始，不用松手前的全屏进度拒绝长距离滑动；迟到、透明、安全及生命周期门禁继续保留。两种上岛共用 84% 宽 / 86% 高落点、保留起始 alpha 并保持终点不透明，吸入自身前段不透明度提高至约 96%，约 62% 后淡出。近期时间加权速度包含原生合并历史，200 ms 旧输入失效；默认承接为 500–540 ms，吸入为 750–810 ms。仅已接受的原生 Width 响应使用有限归一，七轨保留原比例、阻尼、速度；复用 ROM 的 getModifyResponse 应用一次原生时长倍率，旧租约不能覆盖新设置。挖孔不再完全冻结揉搓的横移 / 纵向压缩，使用有限权重和真实覆盖边界。

Dock 中段取样范围不足的旧行为已由先失败回归复现。现预留上下各两倍面板高度的真实壁纸移动范围，仍是单张 1024×384 上限和一个在途请求；只要求屏幕内可见区域被覆盖，维持实际屏幕映射，不拉伸旧边缘。原有坏帧有限重试、Shader 失败回退、隐藏 / 替换释放保留。Host 的捏合中段、连续输入、字体与布局结果只能证明所覆盖模型，吸入观感、默认交接、真实中段闪烁、长期 PSS / 帧时间 / 耗电仍待手机复测。

本轮验证：完整 Host 为 416 套件 / 3,780 项，零失败 / 错误、两项既有跳过；工具 677 项通过、五项既有跳过。最后的左右文字显现边界由 5 个关联套件 / 163 项重新验证并补查 Debug lint、全套静态契约；纯元数据过渡逐帧核对可见字形不得穿过挖孔或左右分界。字号重置与 Dock 中段的旧代码失败记录、完整及专项 JUnit、主机渲染图与指定 Launcher 合同均在本轮仓库外证据目录。

本轮本地正式版：按用户追加授权，从 `03fb76b770eec4d5de79bffa67b2e7fb2fdb08ed` 构建 r14.25.6 / 255，Release R8 / fatal lint、历史证书单一 V2 签名、SDK 34 / arm64、ZIP / ELF 16 KiB、完整 sourceCommit 与同 APK 的 API 102 / 成员 mapping 均通过。APK 4,282,225 字节，SHA-256 `6856d781f56488ba968b5d98c20dc4db1933f5aacf1e3b9b997ae8f661faf719`，交付本地 `Downloads/CustoMIUIzer-A14-r14.25.6-03fb76b7/`；收据、mapping、签名及 API 检查在本轮证据目录。没有 ADB、安装、公开发行、tag 或 main 合并；手机触感及中段材质表现继续待验。

历史交互说明：以下手动展开 / 五秒收短、两次上滑退出和统一下滑展开方案已被当前直接长 / 短岛及一次上滑退出替代，保留当时证据不作为当前功能合同。

上一轮修正：有效拖动接管未结束动画，平台近期速度经同一阻力导数进入共享弹簧；手动展开在交互结束约五秒后收短，无障碍超时优先，元数据不续期。长岛主 / 次信息分行，重复内容隐藏，普通操作反馈入场前验证一至两行完整布局。系统活动保留六类原生来源并使用静态多选，短信 / 日历独立入口与特殊准入已移除；消息只服从显式应用名单及共同原生安全门禁。上滑回岛集中于手势页；宽度、高度组对称命名，应用 Toast 说明与系统状态 / 通知区分。

上一轮吸入采用约 18% 较慢的原生弹簧响应，保持阻尼、速度与结束语义。参数调整由一个弱租约恢复，只恢复仍属于当前调整的原值，旧完成不能影响新窗口。Dock 的尺寸 / 裁剪坏帧按已有有限恢复处理，不再直接永久停用镜片；Shader 失败仍保留原生回退。以上是源码与离线目标合同，不等同已确认真实相册触发源、玻璃全部回退原因、触感或长期设备资源表现。 连续坏帧额外覆盖静态与动态壁纸：旧裁剪不覆盖新位置时，绘制及周期刷新不能补充重试预算。Host 的六十次绘制轨迹由 61 次请求收敛为首次有效帧加四次有限坏帧尝试，隐藏后释放纹理；这是模型计数，不是设备耗电或 CPU 测量。

上一轮候选上岛效果命名为「默认」与「吸入」，当时默认只采用 r14.25.5 的 native 窗口目标、圆角和原生 alpha；本轮按较晚候选重新融合，其他近期功能不回退。吸入沿同一次 ROM 时钟保留页面纵向体积，上缘提前进入、尾部持续压缩，后半程平滑降低透明度。所有来源统一长岛先收短、再次上滑再退出；长岛目标至少比短岛宽 24 dp，并服从物理安全边界。越界 Q 弹使用 260 ms 的有限欠阻尼响应与近期输入速度继承，其他展开与退场时钟不作统一缩短。

消息通知优先于即时反馈、普通 Toast、持续活动和媒体；新增反馈不能覆盖当前消息。闹钟导致原生媒体卡片暂时缺失时，复核当前操作系统会话后保留仍真实播放的同一音乐；若为同一会话的短暂 PAUSED，岛按原两秒期限隐藏，仅保留原回调最多 30 秒等恢复，重复快照不续期。旧 token、失去 manager、STOPPED / ERROR、销毁及安全失效继续清理。

fuxi 实机当前实时档、增益 450%、媒体音量 11/150。相同 session 的独立五秒标量采集对比：AS_PLAYED 为 72 帧、零有效 FFT 频点；NORMALIZED 为 72 帧、7,980 个有效频点、最大功率 25,600。候选改用原生归一化采集，移除弱音乐的显示前置门槛；录音去除开方预放大，公共增益采用平滑压缩。450% 偏高但不修改用户现有设置，实际波形对比仍须在候选上验收。

玻璃的独立壁纸 compositor 查询连续五次返回 270×600 的有效非 secure / 非 HDR 帧，证明本机接口可用，不证明所有变换阶段均覆盖 Dock。候选对暂时空帧 / 捕获异常给予当前可见代内 64 / 160 / 400 ms 有界恢复；真正 Shader / 纹理失败仍安全回退，隐藏 / 替换释放旧资源。被动工作复核只改已证实的不合适路径：录音 Consumer 不再因隐藏 / stop 而触发 lazy 构造，归一化和卡片缺口按事件更新；不扩张常驻任务或宣称未经测量的 CPU / 续航收益。

设备基线录屏、原设置、FFT / 壁纸标量证据与本轮验证入口保留在仓库外 `customiuizer-a14-evidence/island-feedback-20261027/`。新吸入、原生标准交接、Q 弹、真实闹钟停止、通知抢占及玻璃变换恢复的候选设备验收分别记录，不以 Host 或旧版基线代替。

追加校准：统一下滑展开开启时，音乐下滑也展开，原播放 / 暂停仍保留为可配置动作；设置用原生反向依赖禁用冲突编辑器，保留原值。普通删除 / 保存反馈在短岛不足时先预检长岛，一次进入能完整容纳的长岛，结束恢复音乐，避免统一短宽后把可显示的完成提示直接退回原生。

对照 r14.25.5 的同一 560 ms 承接时钟，旧正向反弹峰约 397.3 ms，前一候选对称曲线为 434 ms；最新曲线前移到 336 ms、504 ms 时反弹归零。幅度在入场前按安全空间限幅，避免截平峰顶；普通内容切换保留零起始导数以支持连续反向。吸入上沿在前 14% 内锚定窄入口，页面自身不透明度先降至约 88%、末尾约 5%，始终乘 native alpha。一次提交回调不再随每帧重新注册和作废，串行捕获防止快速重入叠加 native 捕获工作。这些是源码 / 模型结论，未代替真机快速手势录屏。

历史设备候选仅具备当时记载的包身份与部分功能证据，不能扩展为后续实现的验收结果。个人使用状态和原始设备记录不公开；当前有效结论以上方待验范围为准。

时钟已由用户明确为：文件夹内启动时钟，秒表 / 计时运行时直接上滑回桌面；计时岛存在，仅缺页面上岛动画。当轮配置已含 alarm / timer / stopwatch 与 live_return。CN / TW 与历史实机 Launcher APK 同为 `a6546d51d9220039ed7ac143de249e9014202cfafeafd715237eb49f8f5a3f7b`：`startAppToHomeAnim2` 在构造窗口参数前统一调用 `findClosingAnimTarget`，随后才解析 shortcut / 文件夹图标；本模块在该入口先选择有效岛目标。因此样本中“图标在文件夹”本身没有绕过上岛入口，仍不能排除实际手势时序 / 快照或交接准入漏触发。取证保留于同目录 `clock-folder-rom.json`；未改动文件夹图标所有权，根因及文件夹内 / 外快速手势录屏仍待下一设备窗口。

最新源码的完整 Host / Debug lint 为 415 个套件、3,760 项，零失败 / 错误，2 项既定跳过，包含暂停卡片缺口的恢复、停止和不可续期释放回归。此前玻璃 20 项、上岛 / 窗口归属 25 项定向结果亦通过，并已纳入此次完整验证。首次接管仅接受足够早且尚接近不透明的原生页面，避免半透明层叠亮；淡化从实际接管后开始，迟到结果保留连续 native 返回。全量工具 677 项、5 项既定跳过复用本轮同工具源码的成功结果，新增偏好 / 依赖、语义验证和文档生成另行通过。

完整追加修正的源码 `22852a7dc2260ac710f93148569b25aec6be2f24` 已生成历史证书签名的 Develop 工程候选，4,280,906 字节，SHA-256 `e913609b75492a58246e2665cf340154743a4bafd5beaecccccd5ee0e348665b`，保留于同一证据目录的 `candidate-22852a7d/`。R8、Develop fatal lint、签名 V2、16 KiB 对齐、工程 revision 来源和 SDK 34 / arm64 / API 102 静态作用域均通过。最终 APK 的 Hook API 定义、八个设置进程入口直接 API 指令引用及 replaceHook 为零；setId / getId 各一条，已用同产物 mapping 归属共享 HookInstallation 策略。APK / mapping / SQL、完整 Host 汇总及 cleanup 收据分别保留；工程 APK 的完整源码 SHA 由外部 receipt 关联，未宣称其内嵌 Release 来源字段。该完整候选尚未安装，动画 / 时钟文件夹 / 真实闹钟恢复仍待实机录屏与事件核对，不据 Host 或产物门禁扩大设备验收。

## 下一步

按上方全覆盖入口表逐批深入，下一批优先 M04 信任来源 / 用户归属 / 原生认证分类，以及 M09 截图 ContentValues 失败回退 / MIME 与文件头一致性、其他 Unit 安装入口的实际结果；通知安全、Launcher 图标与状态栏设备闭环独立保留。复用已完成源码批次，不重新逐开关扫描。M00 / M01 目录校准、M02 声音振动、M03 通知、M05 / M07 手势、M06 Dock、M08 / M10 维护和设置已有回归；M04 锁屏 / 应用锁、M09 其他宿主的未覆盖行为按具体 owner 与证据另立任务，不凭旧计划推广重构。

保留 PrefMap 原子快照、PreferenceBootstrap、Feature 注册、ProcessRouter、ResourceHooks、按 ClassLoader 有界反射缓存及既有 owner scope。封面合并、缓存扩容、Baseline / Startup Profile 等只是候选，需具体复现或测量和独立回退单位；当前没有全面改造依据。

## 证据恢复

[整合检查点的完整记录](https://github.com/thetvplus/customiuizer-a14/blob/c9843589416efdbeba2d0f6d651c1c9e136e9fef/docs/maintenance/A14_REFINEMENT_ROADMAP.md)保留历史 ROM / 安全合同、完整目标描述符、模型失败、样本身份、既有设备边界和各批回退提交。需要原文时用 `git show c9843589416efdbeba2d0f6d651c1c9e136e9fef:docs/maintenance/A14_REFINEMENT_ROADMAP.md`；不把它重新复制成阶段文档。

原始 ROM / 查询 / decode / 日志 / mapping 仍在本机忽略的 `.di-evidence/` 或仓库外，包括 `m03-20261023`、`island-native-compat-20261024`、`launcher-icon-ownership-20261024`、`statusbar-fullscreen-20261024`、`blur-system-20261024` 和状态栏工作树的 `statusbar-fullscreen-fix-20261024`。它们未上传，重做结论时须重新核对工具、样本和安装状态；公共定位合同继续维护在 M03 文档及现有 SQL。
