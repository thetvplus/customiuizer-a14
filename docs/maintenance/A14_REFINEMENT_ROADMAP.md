# A14 全量功能地图与分批精修

这是 A14 功能精修、DexSQL 取证、页面调整和安全退役的长期工作入口，[工程规则](../../AGENTS.md) 统一链接到本页。方案起点为本地提交 `3981f85`，日期为 2026-10-19。后续直接更新本页的任务与结论，不按轮次新增审计报告。源码变化后先校准地图，历史勾选不能作为新代码的验收结果。

当前状态：**M01.01–M01.08 / M02.01–M02.08、M06.01–M06.05 Dock、M05.01 Toast、M05.02 动态岛与 M10.02–M10.03 公共滑杆 已完成本地验证，均待实机。** Dock 已按图标中心对称限幅，新增有壁纸合成层采样依据的 SDF 透镜；实际捕获与光学效果仍待目标设备确认。振动强度已接入 A14 原生缩放链，高音量警告已按每次原生确认修复；下一批按风险优先推进维护与隐私恢复事务。音量面板超时、百分比与材质恢复成果继续复用，记录见第 8 节。按共享状态闭环合批，M01 随实际修复同步校准，不再逐条偏好或小分支单独收口。全量设置界面与信息架构调整见第 9 节；不据此批量修改 Hook。此前各批按当时授权提交工作分支；2026-10-22 当前任务另获明确授权：完成 M05.04 后将版本提升为 r14.25.1 / 246，推送开发分支、合并 main，使用 Actions 正式候选的同一 APK 发布双仓库。实施、主机 / ROM / APK 与设备验收仍分别记录。

## 1. 范围与事实来源

覆盖四个业务入口：系统、桌面、按键与手势、应用与维护（沿用原四个入口键）；同时覆盖模块设置、搜索、动作编辑器、应用选择器、备份恢复、作用域和重启提示这些贯穿流程。

| 地图层 | 规划起点 | 事实来源与用途 |
| --- | --- | --- |
| 用户页面 | 34 个业务页面、25 个独立子页面 | [偏好源 XML](../../app/src/main/res/xml)；下表覆盖全部页面，不把页面数当成功能数 |
| 安装功能 | 251 个稳定 Feature ID；进程矩阵提取 250 个 FeatureSpec | [FeatureIds.kt](../../app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/FeatureIds.kt)、[完整功能与进程矩阵](../rom-intelligence/A14_PROCESS_MATRIX.md)；差额为基础 `package_permissions`，不作为孤立功能删除 |
| 偏好与源码线索 | 771 条语义目录记录 | [feature-semantics/a14.json](../../feature-semantics/a14.json)；包含导航、派生键及推断信息，不能当作 771 项已验证功能 |
| 实际注册和行为 | 当前实现优先 | [Feature 安装器](../../app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature)、[运行时架构](../../ARCHITECTURE.md)、[页面解析器](../../app/src/main/java/tv/withaibuild/customiuizer/utils/PreferenceResourceResolver.kt) |
| ROM 与 DEX | fuxi 国行、台湾版 HyperOS 1 / A14 | [DexSQL 工作流](../rom-intelligence/DEXSQL.md)、[现有 SQL](../../tools/dexsql/README.md)；定义和调用证据与实机结果分别记录 |
| 既有产品边界 | 继续有效 | [兼容范围](../../COMPATIBILITY.md)、[遗留语义](LEGACY_SEMANTIC_CALIBRATION.md)、[已批准退役](A14_FEATURE_RETIREMENTS.md) |

这些数量是起点快照。功能以稳定 Feature ID 和偏好键识别；同一功能的字号、颜色、应用名单等属于附属设置，不拆成独立的“已完成项”。资源替换、动作桥接和无独立 UI 的安装路径也要进入复核，不能只扫描可见开关。

M05.01 增量：新增稳定 ID `256 / systemui_toast_presentation`，语义目录增加两个键至 773 条。2026-10-21 复核补齐其进程矩阵登记，三份生成文件仅新增该行，当前 251 个 FeatureSpec / 252 个稳定 ID（仍包含基础 `package_permissions`）；增加真实注册集合与矩阵的对应回归，避免后续新功能遗漏。

本次 Dock / Toast 体验修正：删除冗余背景收缩键，新增 Toast 位置、顶部间距和居中偏移，目录为 775 条；稳定 Feature ID 不变。

M01 建立的追踪关系为：**页面 / 偏好键 → Feature ID 或设置端动作 → 包 / 进程 / 安装阶段 / ClassLoader → Hook 与资源目标 → 更新、关闭、释放和回退 → 验证证据**。复用上表已有目录，不另建一套功能注册表或通用解析器。

## 2. 页面和结构调整方案

本轮已按新授权实施四个入口内的信息架构调整。目录标题和页内标题都是不可点击的分组，不增加导航层级。主分类标题位于卡片外，小分类位于卡片内；隐藏的页面包装器继续承担依赖与控制器选择。短页保留单卡，禁止为凑数量机械拆分。

| 范围 | 当前结构 |
| --- | --- |
| 首页 | 系统、桌面、按键与手势、应用与维护；模块设置优先显示语言和桌面图标，保留系统设置入口位置。重启操作收敛到首页同一子菜单 |
| 系统 | 显示声音、系统界面、安全与高级设置三组；状态栏、通知、控制中心按用途分组；音量模糊回归声音页，高级设置按分享、截图、连接、应用、外观、动画和系统行为组织 |
| 桌面 | 布局与外观、交互、兼容性；布局、标题、Dock、文件夹相邻，隐藏应用与手势保持独立入口；错误修复与外观分开 |
| 按键与手势 | 实体按键与系统导航两组；键盘光标、返回手势区域、触发动作与振动规则就近排列 |
| 应用与维护 | 应用、通话与输入、系统工具；安装器、手机管家、应用管理归组，维护与隐私单列，保留维护操作的风险说明、确认与恢复行为 |
| 贯穿流程 | 四类源 XML 使用相同结构生成目录、懒加载页面和搜索索引；搜索按真实祖先分组生成面包屑，退出子组后不沿用旧标题；动作编辑保留确认，使用原生下拉尺寸适配 |

跨页面相似功能先核对语义。例如桌面与最近任务的壁纸缩放并非完全等价，不能因为共用部分实现就合并开关；状态栏时钟与控制中心时钟、原生设置快捷入口与实际 Hook 也要分别识别。

软件结构调整以具体功能闭环为默认范围：安装器负责目标与启用条件，Hook 负责窄范围拦截，Controller 负责状态与释放，设置端负责可编辑条件。架构演进、局部重构和依赖按 [AGENTS.md](../../AGENTS.md) 的收益、证据、风险和回退规则选择；不因增加类或有界内存而拒绝改进，也不以现代化名义全面重写成熟基础设施。

### 工程与验证策略

统一工程规则、实现 / 审查 Skill、兼容矩阵、Java 边界与优化门禁遵循稳定优先、体验驱动、性能实证。API 102、SDK 34、Hook / ABI / ClassLoader、致命错误、生命周期、备份及发布契约持续有效。

按当前源码完成以下取舍；音频可视化按用户追加要求只做小修，不扩展为绘制或框架重构：

| 方向 / 当前实现 | 判断与本轮处理 | 成本、证据与后续验证 / 回退 |
| --- | --- | --- |
| 状态：`PrefMap` 已用 `AtomicReference` 发布整份快照，`PreferenceBootstrap` 已有双快照与逐键更新；所有者监听在远程偏好线程分发 | 保留现有机制；将可视化的偏好处理转入其 Main scope，避免监听线程修改 Paint / Animator。无需 StateFlow | 无新依赖或常驻状态；Robolectric 调度回归确认线程切换与销毁丢弃。其他控制器逐项审查，不把 Hook 安装开关改成即时生效 |
| 绘制：Dock 已有原生 BackdropBlur、按事件合并几何更新、缓存 G4 路径与材质回退；可视化已有单个可停驻 Choreographer 回调 | 保留。未发现支持替换为 RenderNode / RenderEffect 的实测瓶颈；不改变 Dock 圆角、边距、材质和布局 | 候选需 fuxi 上的帧时间、GPU / Graphics、编辑 / 文件夹 / 最近任务与动态壁纸取证。按材质或几何单元回退；无新依赖 |
| 缓存：`ReflectionCache` 按 ClassLoader 限 4 × 64；封面处理有按字节计算的两帧 LRU、尺寸键与 512² 模糊中间图上限 | 保留容量。可变 Bitmap 的内容失效与封面重复请求合并作为候选，先复现再决定 | 扩容前测命中、重复生成、GC 和 SystemUI PSS；说明 Bitmap 保留与释放。候选局限封面 Controller / policy，不建立全局缓存框架 |
| 调度：天气 / 计步已有单次在途任务、Mutex、屏幕与 owner 取消；可视化切换周期参数会为隐藏 View 启动任务，清空封面 / 切换模式未取消旧取色 | 小修可视化：隐藏或离开模式时取消；显示后按当前模式恢复；清空封面取消旧取色；同一来源取消后可重试。周期限制沿用 UI 的 3–60 秒，避免异常旧值形成忙循环 | 复用现有 scope / Job，无新线程池、缓存或依赖。可控 Main 调度直接验证任务数与取消，不能换算 CPU / GPU / PSS。`AudioVisualizer.kt` 与其异步回归为同一回退单元 |
| 语言 / 构建：Kotlin 2.4.21、JDK / Java / Kotlin 输出 25、AGP 9.4.1 及既有 R8 / 资源收缩均已使用 | 保留版本和三项默认 Java ABI 边界；本轮不迁移语言。项目无自有 Baseline / Startup Profile；候选优先为设置 App 采集启动 / 导航路径 | [Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview) 与 Startup 的 ART 预编译 / DEX 布局用途不同；无法据此推断注入宿主 Hook 收益。先设备 A/B 测启动 / 导航，再单独决定插件或输入，独立回退 |

可视化修改未更改 Hook 目标、安装条件、`chain.proceed()`、设置键、备份或 Dock；同步取消不能中断已经运行的 Palette CPU 计算，但已取消结果经过 `withContext` 和当前来源 / 模式检查后不再发布，下一次可见时按当前封面重试。没有增加常驻对象或缓存容量；实际 PSS、CPU、GPU 与帧时间仍待设备测量。取消语义参考 [Kotlin 协程文档](https://kotlinlang.org/docs/cancellation-and-timeouts.html)。

证据复用既有 `FeatureInstallMetrics` / `HookDiagnostics`，本轮仅增加主机可控调度与门禁回归，不增加监控服务。设备验收按 [优化门禁](../OPTIMIZATION_GATES.md#轻量测量) 的五个场景执行：同 ROM / 开关分别记录 SystemUI 安装日志、Launcher 启动 / Dock 帧轨迹、连续切歌 / AOD / 封面清空、可视化模式切换 / 隐藏 / 重建，以及设置传播；对照线程 / 任务、分配 / GC、PSS / Graphics。当前 ADB 无设备，均不标记实机通过。原始测试、构建、mapping 与 APK 对照留在忽略的 `.di-evidence/engineering-modernization-20261021/`，不提交产物。

用户追加重点后的权重如下；这是工程优先级，未测量的 CPU / GPU / PSS 不填推测百分比：

| 顺序 / 权重 | 范围与当前证据 | 本轮处理及下一步 |
| --- | --- | --- |
| 1 / 最高：系统状态可恢复、安全与持久化正确性 | **维护与隐私**。`Various` 与 system_server bridge 已有身份校验、系统包 / 组件白名单、数量限制、回读与普通异常回滚。组件 / 包 / 更新服务恢复数组使用 `mapNotNull`，坏值可能被删除后与名称重新对齐；原状态还在系统操作成功回调后才 `apply()` 落盘，存在中断窗口 | 本轮将两处数组读取统一为有界、全量严格解码；拒绝坏值、非法状态、空名称和过量输入，保持有效旧格式。下一闭环优先补“变更前可靠保存原状态 → 操作 / 回读 → 完成标记 / 恢复”的进程中断与写入失败故障注入。后者目前为源码时序风险，未声称目标设备已复现；不贸然扩大停用清单 |
| 2 / 高：常驻 SystemUI 的稳定与更新成本 | **状态栏**。图标、布局和网速已有原子快照 / 定向键监听；`DeviceInfoMonitor` 已有 generation、相同文本去重、2 秒基础节拍、失败退避至 60 秒及 PowerManager 熄屏检查；Duo 按 owner / display 管理几何及原生可见性恢复 | 保留已验证机制。优先验收熄屏 / 亮屏、快速调参、双卡数据订阅切换、字体 / 密度 / 横竖屏与锁屏重建；核对过期消息、重复布局及温度读取次数。只有轨迹证明瓶颈后才增加事件合并或缓存，不凭“每次分配”重做基础设施 |
| 3 / 高：插件生命周期与安全交互 | **控制中心**。`ControlCenterPluginRuntime` 已对同 loader 幂等、旧 lease 失效、partial failure 与替换 loader 做回归；安全磁贴保留锁屏鉴权；计步已有单次在途查询和视图释放 | 优先验收插件替换 / 面板重建、锁屏三种磁贴点击、计步重附着及亮度百分比 owner 恢复；布局 / 颜色 / 圆角与大模糊优化后排。未有设备或唯一 ROM 证据时，不猜测新 Hook / 资源目标 |
| 4 / 低：附加视觉效果 | **音频可视化、Dock 新效果、额外 GPU 技术** | 可视化只保留上述任务启停小修；本轮不继续加效果、替换绘制或引入新框架。复用已经完成的 Dock / Toast / 音量外观成果 |

维护恢复解码位于 `MaintenanceStateCodec.kt`，仅设置 App 冷路径使用；无新增依赖、后台任务、缓存或宿主 Hook。它和 `Various` 两处接入 / 回归构成独立回退单元。严拒无效恢复数据不会把未知值当成默认启用，也不改变有效备份键与格式；已有丢失的原状态不能据此推断或重建。

## 3. 全量页面地图

表中键省略 `pref_key_`，选项数包含链接和附属设置。每行都是复核覆盖范围，**不是整页已经兼容或已通过实机的声明**。名称、顺序和计数已按当前中文资源与源 XML 更新（2026-10-23，34 个业务页面 / 342 个带键选项）；稳定键和 Hook 语义不变。

### 业务页面（34）

| 当前页面 / 稳定键 | 选项数 | 当前功能范围 | 工作包 |
| --- | --- | --- | --- |
| 系统 → 屏幕<br>`system_cat_screen` | 9 | 所有方向的屏幕旋转；方向锁定；息屏时重置已手动调整的亮度；自动亮度范围；充电时不点亮屏幕；暗屏时长；锁屏屏幕超时；充电动画显示时间；熄屏动画时长 | M10 |
| 系统 → 声音<br>`system_cat_audio` | 18 | 禁用高音量警告；通知时保持音乐音量；首次按键即调节媒体音量；忽略通话；已选应用；音乐可视化；显示声音百分比；扩展计时器；隐藏静音快捷按钮；隐藏勿扰快捷按钮；静音与勿扰快捷按钮颜色；背景色；图标颜色；折叠时自动隐藏；展开时自动隐藏；联发科设备音量面板模糊；折叠对话框；展开对话框 | M10 |
| 系统 → 振动<br>`system_cat_vibration` | 4 | 禁用测试性振动；振动强度；阻止振动；黑白名单 | M10 |
| 系统 → 状态栏<br>`system_cat_statusbar` | 32 | 隐藏图标；图标位置调整；移动网络信号图标设置；隐藏 Wi-Fi 网络活动指示器；显示Wi-Fi标准；图标大小；通知图标限制；时钟设置；网速相关设置；显示电池信息；电量图标样式；Duo 三合一图标；电池条指示器；显示设备温度；双排状态栏；时钟跨双排显示；全面屏设备下左侧宽度占比；第一排水平边距；左侧间距；右侧间距；状态栏高度；垂直偏移；状态栏上边距；上边距大小；不修改锁屏状态栏；状态栏水平边距；左侧间距；右侧间距；手势控制；截图时隐藏状态栏；背景色同步；已选应用 | M10 |
| 系统 → 动态岛<br>`system_cat_island` | 33 | 原生提示模式；安全尺寸与左 / 中 / 右定位；进出动画；逐类系统反馈；通知接管与允许 / 排除名单；音乐、波形、左右切歌及五项动作；应用排除 | M05.04 / r14.25.2 |
| 系统 → 通知中心<br>`system_cat_drawer` | 11 | 隐藏清除按钮；隐藏通知设置图标；隐藏暂无通知消息提示；背景模糊；时钟设置；隐藏日期；自定义日期格式；居中显示；字体大小；时钟应用；日历应用 | M10 |
| 系统 → 控制中心<br>`system_cat_qs` | 20 | 主题样式配置；5G开关；时间悬浮窗开关；刷新频率开关；隐藏编辑按钮；隐藏安全提示；隐藏日期；自定义日期格式；隐藏运营商名称；隐藏运营商名称之间的分隔线；显示步数；列数；显示亮度百分比；百分比面板的上边距；点击磁贴后自动收起；长按磁贴时以小窗打开；强制开启控制中心样式切换；对调控制中心与通知中心；磁贴振动；系统触感关闭时仍振动 | M10 |
| 系统 → 通知<br>`system_cat_notifications` | 17 | 保留通知；允许在锁屏上显示；允许作为悬浮通知；允许修改任何应用的通知；开放通知的重要程度；禁止通知折叠为历史通知；移除每个应用只显示10条通知的限制；着色通知卡片；黑白名单；点击通知时以小窗打开；启用白名单；已选应用；亮屏时静音；扩展通知菜单；展开通知；黑白名单；打开频道设置 | M10 |
| 系统 → 浮动通知<br>`system_cat_betterpopups` | 7 | 居中显示；展开通知；黑白名单；自动隐藏展开的通知；允许浮窗中打开；黑白名单；静音时禁止浮动通知 | M10 |
| 系统 → Toast<br>`system_cat_toasts` | 7 | Toast 位置；顶部间距；垂直偏移；底部间距；显示时长；阻止 Toast；黑白名单 | M10 |
| 系统 → 最近任务<br>`system_cat_recents` | 8 | 最近任务中禁用壁纸缩放；隐藏状态栏；隐藏应用名称；背景模糊；移除清理按钮；清空所有任务；隐藏最近；已选应用 | M10 |
| 系统 → 浮动窗口与分屏<br>`system_cat_floatingwindows` | 5 | 禁用黑名单；多窗口；应用间流转以小窗打开；启用白名单；已选应用 | M10 |
| 系统 → 锁屏<br>`system_cat_lockscreen` | 20 | 隐藏状态栏；隐藏顶部区域；隐藏勿扰模式通知；禁止长按开启编辑；隐藏解锁提示；移除紧急呼叫；将壁纸应用到任意应用；点击解锁；双击熄屏；显示充电数据；显示下次闹钟；自定义画报和相机动作；专辑封面设为壁纸；增强屏幕锁；加固快速设置；乱序PIN；开机免密；取消每72小时执行一次强认证的要求；禁用屏幕锁；解锁证书 | M10 |
| 系统 → 应用锁<br>`system_cat_applock` | 6 | 乱序PIN；不严格的应用锁；应用锁超时；锁定应用列表；跳过应用锁定；已选中活动 | M10 |
| 系统 → 高级设置<br>`system_cat_other` | 29 | 清理共享菜单；已选应用；测试菜单；清理打开方式菜单；已选应用；测试菜单；截图配置；截图时隐藏叠加层；相册截图目录；查看已保存Wi-Fi密码；USB 默认用途；允许降级；禁用签名认证；禁用系统完整性检查；禁用安全内容保护；移除启动应用确认；阻止应用强制关闭；已选应用；允许所有应用强制开启深色模式；关闭窗口级模糊；禁用弹性滚动；已选应用；壁纸缩放级别；窗口；过渡；值动画；扩展电源菜单；隐藏低电量警告；隐藏禁用触摸的警告 | M10 |
| 桌面 → 桌面布局<br>`launcher_cat_other` | 19 | 解锁桌面布局；桌面网格水平边距；桌面网格顶部边距；图标缩放；反向竖屏支持；自由调整小部件；小部件水平间距；禁用仅小部件页面；无限滚动；启动应用后关闭抽屉；状态栏时钟始终显示；动态页面指示器；仅在编辑模式中显示；页面指示器高度；页面指示器顶部边距；去除解锁动画；使用旧的启动动画；禁用壁纸缩放；强制桌面壁纸颜色模式 | M10 |
| 桌面 → 应用标题<br>`launcher_cat_titles` | 7 | 隐藏标题；在底栏显示标题；自定义应用标题；修改标题；字体大小；附加顶部边距；加深阴影 | M10 |
| 桌面 → Dock<br>`launcher_cat_dock` | 11 | Dock 背景；全宽底栏；材质；不透明度；背景尺寸微调；圆角；左右间距；Dock 显示更多图标；Dock 高度；Dock 顶部边距；Dock 底部边距 | M10 |
| 桌面 → 文件夹<br>`launcher_cat_folders` | 6 | 自动关闭文件夹；背景模糊；关闭文件夹背景模糊；文件夹排列数量；使用整个文件夹的宽度；减少边距 | M10 |
| 桌面 → 手势<br>`launcher_cat_gestures` | 10 | 下滑动作(单指)；上滑动作(单指)；下滑动作(双指)；上滑动作(双指)；双击动作；双指捏合；双指展开；左滑动作；右滑动作；摇动动作 | M10 |
| 桌面 → 隐藏应用<br>`launcher_cat_privacyapps` | 3 | 隐藏应用列表；禁止通过手势进入隐藏应用文件夹；隐藏应用视图调整为4列 | M10 |
| 桌面 → 兼容与修复<br>`launcher_cat_bugfixes` | 3 | 禁用日志和统计；修复动画；修复应用信息 | M10 |
| 按键与手势 → 电源键<br>`controls_cat_power` | 3 | 双击动作；快速手电筒；加长快速手电筒延迟 | M10 |
| 按键与手势 → 音量键<br>`controls_cat_volume` | 9 | 控制输入法光标；反转光标方向；黑名单；双击音量下键开启手电筒；媒体播放器应用(需加入作用域)；长按音量上键动作；长按音量下键动作；长按振动；系统触感关闭时仍振动 | M10 |
| 按键与手势 → 指纹键<br>`controls_cat_fingerprint` | 5 | 失败后打开屏幕；需要实际指纹；失败时不振动；成功时振动；系统触感关闭时仍振动 | M10 |
| 按键与手势 → 全面屏导航手势<br>`controls_cat_fsg` | 8 | 水平手势；黑名单；返回手势区域高度；返回手势区域宽度；屏幕左下角上划动作；屏幕右下角上划动作；屏幕两侧向内滑动并停顿；禁止振动 | M10 |
| 按键与手势 → 导航栏<br>`controls_cat_navbar` | 12 | 隐藏手势提示线；截图时隐藏导航栏或手势提示线；附加边距；修复输入法全面屏优化；隐藏输入法收回按键；长按返回键动作；长按 Home 键动作；长按菜单键动作；左键动作；长按左键动作；右键动作；长按右键动作 | M10 |
| 应用与维护 → 应用安装器<br>`various_cat_package_installer` | 3 | 允许其更新系统应用；显示安装包信息；净化安装过程 | M10 |
| 应用与维护 → 手机管家<br>`various_cat_security_center` | 9 | 跳过开启特殊权限的倒计时；默认打开；跳过安全体检；电池页面显示温度值；禁止重置模糊预览图列表；禁用侧边栏应用推荐黑名单；侧边栏不显示推荐应用；隐藏侧边栏；保留侧滑展开 | M10 |
| 应用与维护 → 应用管理<br>`various_cat_settings` | 5 | 全限制；应用状态控制；隐藏应用信息页面的举报按钮；显示附加应用详情；已安装应用的初始排列顺序 | M10 |
| 应用与维护 → 通话<br>`various_cat_calls` | 3 | 从浮动通知接听电话时不进入全屏(除桌面)；显示通话界面；通话时屏幕亮度 | M10 |
| 应用与维护 → 输入法设置<br>`various_cat_gboard` | 2 | Gboard 竖屏底部填充；Gboard 横屏底部填充 | M10 |
| 应用与维护 → 系统与兼容性<br>`various_cat_general` | 7 | 隐藏特性；关闭访问设备日志确认；允许不受信任的触摸操作；将电池优化设置持久化；扫描时禁止应用远程检测；闹钟兼容性；已选应用 | M10 |
| 应用与维护 → 维护与隐私<br>`various_cat_exclusive` | 9 | 停用小米系统更新服务；清除澎湃 OS 更新提醒；强制停用系统质量服务；精简 Daemon 云控与上传；停用小米分析与系统广告；精简手机管家营销与统计；移除手机管家病毒扫描与提醒；关闭通知权限授权弹窗；关闭定位权限授权弹窗 | M10 |

### 独立子页面（25）

子页保持实际入口与控制器绑定，不因分组丢失专用设置逻辑。下表中的选项数量同样不等于独立 Hook 数。

| 当前入口 | 源 XML | 控制器 | 选项数 |
| --- | --- | --- | --- |
| 系统 → 屏幕 → 自动亮度范围 | [prefs_system_autobrightness](../../app/src/main/res/xml/prefs_system_autobrightness.xml) | `System_AutoBrightness` | 5 |
| 系统 → 声音 → 音乐可视化 | [prefs_system_visualizer](../../app/src/main/res/xml/prefs_system_visualizer.xml) | `System_Visualizer` | 12 |
| 系统 → 振动 → 振动强度 | [prefs_system_vibration_amp](../../app/src/main/res/xml/prefs_system_vibration_amp.xml) | `System_VibrationAmp` | 6 |
| 系统 → 状态栏 → 网速相关设置 | [prefs_system_detailednetspeed](../../app/src/main/res/xml/prefs_system_detailednetspeed.xml) | `System` | 16 |
| 系统 → 状态栏 → 显示电池信息 | [prefs_system_statusbar_batterytempandcurrent](../../app/src/main/res/xml/prefs_system_statusbar_batterytempandcurrent.xml) | `System` | 17 |
| 系统 → 状态栏 → 显示设备温度 | [prefs_system_statusbar_showdevicetemperature](../../app/src/main/res/xml/prefs_system_statusbar_showdevicetemperature.xml) | `System` | 13 |
| 系统 → 状态栏 → 电量图标样式 | [prefs_system_statusbar_batterystyle](../../app/src/main/res/xml/prefs_system_statusbar_batterystyle.xml) | `System` | 9 |
| 系统 → 状态栏 → Duo 三合一图标 | [prefs_system_statusbar_duo](../../app/src/main/res/xml/prefs_system_statusbar_duo.xml) | `System` | 14 |
| 系统 → 状态栏 → 移动网络信号图标设置 | [prefs_system_statusbar_mobilesignal](../../app/src/main/res/xml/prefs_system_statusbar_mobilesignal.xml) | `System` | 27 |
| 系统 → 状态栏 → 隐藏图标 | [prefs_system_hideicons](../../app/src/main/res/xml/prefs_system_hideicons.xml) | `System` | 37 |
| 系统 → 状态栏 → 图标位置调整 | [prefs_system_statusbar_righticons](../../app/src/main/res/xml/prefs_system_statusbar_righticons.xml) | `System` | 16 |
| 系统 → 状态栏 → 时钟设置 | [prefs_system_statusbar_clock](../../app/src/main/res/xml/prefs_system_statusbar_clock.xml) | `System` | 25 |
| 系统 → 状态栏 → 电池条指示器 | [prefs_system_batteryindicator](../../app/src/main/res/xml/prefs_system_batteryindicator.xml) | `System_BatteryIndicator` | 16 |
| 系统 → 状态栏 → 手势控制 | [prefs_system_statusbarcontrols](../../app/src/main/res/xml/prefs_system_statusbarcontrols.xml) | `System` | 11 |
| 系统 → 通知中心 → 时钟设置 | [prefs_system_controlcenter_clock](../../app/src/main/res/xml/prefs_system_controlcenter_clock.xml) | `System` | 7 |
| 系统 → 控制中心 → 主题样式配置 | [prefs_system_controlcenter_themestyle](../../app/src/main/res/xml/prefs_system_controlcenter_themestyle.xml) | `System` | 13 |
| 系统 → 锁屏 → 禁用屏幕锁 | [prefs_system_noscreenlock](../../app/src/main/res/xml/prefs_system_noscreenlock.xml) | `System_NoScreenLock` | 6 |
| 系统 → 锁屏 → 自定义画报和相机动作 | [prefs_system_lockscreenshortcuts](../../app/src/main/res/xml/prefs_system_lockscreenshortcuts.xml) | `System` | 5 |
| 系统 → 锁屏 → 专辑封面设为壁纸 | [prefs_system_albumartonlock](../../app/src/main/res/xml/prefs_system_albumartonlock.xml) | `SubFragment` | 4 |
| 系统 → 锁屏 → 显示充电数据 | [prefs_system_charginginfo](../../app/src/main/res/xml/prefs_system_charginginfo.xml) | `SubFragment` | 7 |
| 系统 → 锁屏 → 显示下次闹钟 | [prefs_system_alarmonlock](../../app/src/main/res/xml/prefs_system_alarmonlock.xml) | `SubFragment` | 3 |
| 系统 → 锁屏 → 加固快速设置 | [prefs_system_secureqs](../../app/src/main/res/xml/prefs_system_secureqs.xml) | `SubFragment` | 11 |
| 系统 → 高级设置 → 截图配置 | [prefs_system_screenshot](../../app/src/main/res/xml/prefs_system_screenshot.xml) | `System_ScreenshotConfig` | 5 |
| 应用与维护 → 通话 → 通话时屏幕亮度 | [prefs_various_calluibright](../../app/src/main/res/xml/prefs_various_calluibright.xml) | `Various_CallUIBright` | 6 |
| 应用与维护 → 系统与兼容性 → 隐藏特性 | [prefs_various_hiddenfeatures](../../app/src/main/res/xml/prefs_various_hiddenfeatures.xml) | `Various_HiddenFeatures` | 3 |

## 4. 分批任务队列

以**共享状态闭环**代替固定“3–5 项功能”额度。M00–M10 保留为覆盖标签，同一执行批可同时完成 M01 目录校准、功能修复、局部减法及验证；不把附属参数、一个缺字段分支或一次资源回退各拆一批。

合批使用现有目录和代码，不新增任务数据库或调度框架：

1. **线性分桶**：以宿主、安装阶段、实际 ClassLoader、安装入口预分组，再按原生 owner / 状态 / 释放链的真实依赖连边；同一设置下的材质、几何、显隐和恢复一起处理。2026-10-20 对现有 771 条目录的一次分桶得到 567 条有宿主及入口线索的记录、150 个候选桶，其余 204 条随所属闭环补证；这是取证入口压缩，不能宣称只剩 150 项任务。`UNKNOWN` 和通用 `evaluateEnabled` / `before` / `after` 不能自动作为合并依据，插件与宿主的加载器边界仍分别核实。
2. **依赖图收敛**：共享可变状态和释放路径的连通部分优先合并；仅同页或同包的无关功能不强行连边。前置关系先处理，实机缺失只挂起该边，其他独立部分继续。已正确的实现直接记“保留并复用回归”，不为了进度新建测试或重构。
3. **贪心选择覆盖**：真实用户缺陷、安全及生命周期风险优先；同风险下优先选择能以同一份 ROM、调用链及测试覆盖更多未校准关系的批次，避免同一计时器反复开取证和完整验证。跨加载器的关联功能可同批研究，分别实现与提交；不因此改成跨进程共享状态。
4. **验证取并集**：开发只跑本批受影响测试；整合最终源码后统一一次 `full`。已有相同源码 / 范围结果直接复用，只有新改动、失败或未解决问题追加检查。纯目录校准只跑对应静态合同，不逐条触发完整 CI、R8、SHA 或工具全测。

压缩后的近期执行顺序如下；发现实际缺陷时可调整优先级，保留每项原有偏好和关闭语义：

| 执行闭环 | 覆盖标签 | 一次合并处理的内容 |
| --- | --- | --- |
| 维护与隐私恢复事务 | M08 / M10 | 最高优先：严格解码小修已实施；继续补原状态提前可靠保存、失败 / 进程中断恢复，再考虑扩大精简清单 |
| 状态栏常驻状态与刷新 | M05 | 复用快照、Duo 与温度监测回归；先补熄屏 / 调参 / 双卡 / 重建的更新次数与生命周期证据 |
| 控制中心插件与安全交互 | M05 / M07 | 复用同 loader 幂等、替换 / 部分失败、计步生命周期；先安全磁贴、owner 与面板重建，再外观 |
| Dock 场景与材质恢复（本地完成） | M01 / M06.01 | 复用六项背景偏好、文件夹三条弹簧、几何恢复及导航区避让结论；保留实机待验收场景 |
| Toast 与原生额头交接（本地完成） | M01 / M05.01 | 复用定位、原生文本槽、门禁及提示回收；保留实机待验收场景 |
| 音量面板剩余行为（本地完成） | M01 / M02.05 | 复用超时无障碍策略、百分比所有者、默认材质恢复与面板重建结论；保留实机待验收场景 |
| 音频服务剩余限制与振动策略 | M01 / M02 | M01.06 已校准按应用振动，M01.07 已修复原生强度缩放，M01.08 已修复高音量警告的确认 / 失败回退；预制效果 / 硬件支持待实机；按实际 owner 分组 |
| 启动器布局与状态交接 | M01 / M06 | 文件夹 / 标题、布局 / 小部件、最近任务 / 动画按共享 owner 合并；复用本批 Dock 场景边界，不机械逐开关精修 |

| 工作包 | 状态 | 小批切分与优先目标 | 页面调整 |
| --- | --- | --- | --- |
| M00 既有修复 | 本地完成，待实机 | 声音通话回调、标题创建重载、桌面双击、侧边栏和温度生命周期、键盘设置联动 | 声音 / 导航栏 / 标题 / 手势 / 启动器其他分组；手机管家顺序整理 |
| M01 目录校准 | 八批校准；后续随闭环合批 | 复用 M01.01–M01.08；目录、重启及关闭语义随所属修复批一次校准，不单独遍历目录 | 固定当前页与拟调整页的键映射；不先批量搬页面 |
| M02 声音与振动 | 焦点、计时、面板、振动与高音量警告八批收口 | 复用 M02.01–M02.08；可视化单独处理；预制效果与硬件能力保留实机边界 | 行为、音量面板、可视化各自连续，名单与开关相邻 |
| M03 通知链路 | 待核查 | 通知限制与权限；通知中心时钟 / 日期 / 模糊；浮动通知和展开 / 小窗；逐批检查相互干扰 | 通知规则、外观、交互分组，清除重复入口说明 |
| M04 锁屏与应用锁 | 待核查 | 认证与解锁策略；应用锁超时与名单；锁屏媒体 / 充电 / 闹钟；安全相关回退优先 | 显示与交互、安全、媒体与数据分开，限制条件明确 |
| M05 状态栏与控制中心 | 部分静态复核 | 复用 `8c922a5` 的有效结论；后续分为信号与双卡、时钟与图标、Duo、电池与温度、磁贴与插件生命周期等小批 | 主开关、尺寸与外观、附加行为分层；保留 Duo 不含下载进度的边界 |
| M06 启动器 | M06.05 图标中心、对称尺寸与采样透镜已本地验证 | 复用 Dock 和 `3981f85`；待实机确认采样 / 材质 / 导航区与动画同一闭环，再按实际共享 owner 合并布局、小部件和动画交接 | 保留原生图标、拖拽与动画坐标 |
| M07 按键与导航 | 部分静态复核 | 返回 / Home / 菜单长按；电源与音量键；手势区域和动作；系统与输入法两侧的配合 | 显示、键盘、动作分组；作用域和重启条件不含糊 |
| M08 手机管家与维护 | 部分静态复核 | 权限倒计时 / 默认打开；侧边栏与推荐；安全扫描 / 温度 / 预览隐私；维护组件停用与恢复分别核查 | 维护工具与常用手机管家设置分离；国行 / 国际差异按证据处理 |
| M09 其他应用与系统项 | 待核查 | 安装器、Settings、相册 / 截图、通话、输入法、PowerKeeper、GuardProvider；系统其他按宿主分小批 | 减少“其他”兜底，先分组后迁移；无关应用不初始化功能 |
| M10 贯穿流程与收口 | 设置界面本地收口，实机待验 | 搜索、动作编辑、选择器、导入恢复、语言、重启提示；核对未覆盖 ID 和资源路径 | 完成地图校准，清理有证据的重复层次；不以零警告或减少行数为目标 |

工作包按风险推进，不要求机械串行。M01.01 / M02.01 从“系统 → 声音”的音频焦点与音量原生行为三个功能开始；后续复用其结论，转入同页不同宿主的小批。若实机或 ROM 输入阻塞，记录边界后转做独立项。M05–M08 已有部分成果，禁止从零重复全量核查。

### M01.01 / M02.01：声音原生链路

本批已补齐下列三项的页面、偏好、安装条件和测试，并复核同一 `system_server` 音频链路。三个开关及 `system_ignorecalls_apps` 仍在“系统 → 声音”：源 `prefs_system.xml` 的 `system_cat_audio` → 生成页 `prefs_system_audio` → `System` 子页面；名单依赖 `system_ignorecalls` 并使用既有应用选择器。均无页面迁移、动作桥接或资源替换。

三个 Feature 均由 `SystemServerFeatures` 在 `SYSTEM_SERVER_STARTING` 注册，仅首次完整偏好快照决定安装；关闭时不创建本功能 Hook。主开关启停需重启设备，不因偏好变化重置安装状态。应用名单通过既有快照更新焦点判定；通话事件只在注册时过滤，名单变化后目标应用须重新注册监听。本轮已移除声音页重启菜单，并为这三个主开关明确标注完整重启条件；首页组件重启仍不能代替设备重启。

| 稳定 Feature ID / 偏好键 | 定义与调用证据 | 决定 |
| --- | --- | --- |
| `NoDuckingFeatureId` / `system_noducking` | `FocusRequester.handleFocusLoss(ILcom/android/server/audio/FocusRequester;Z)V` 先更新 loss / notified，再调用 `frameworkHandleFocusLoss(ILcom/android/server/audio/FocusRequester;Z)Z`；`handleFocusGain(I)V` 恢复状态和播放器 | **修复**：将拦截缩至 framework 分支；仅 CAN_DUCK 且有 winner 时返回已处理，保留外层状态 / 策略通知及恢复；其他失焦与未知 winner 走原生 |
| `FirstVolumePressFeatureId` / `system_firstpress` | `AudioService$VolumeController.suppressAdjustment(IIZ)Z` 更新原生长按时限，并检查媒体流、静音和 `mController` | **保留行为**：仍先执行一次原方法，只覆盖媒体流、非静音且 controller 存在的结果；提取测试入口，覆盖回退与异常 |
| `NoCallInterruptionFeatureId` / `system_ignorecalls` | `AudioService.requestAudioFocus(Landroid/media/AudioAttributes;ILandroid/os/IBinder;Landroid/media/IAudioFocusDispatcher;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ILandroid/media/audiopolicy/IAudioPolicyCallback;I)I` → `MediaFocusControl` 原生栈；放弃 / Binder 死亡会移除并释放 owner | **修复**：删除 `audioFocusPkg` 历史请求缓存，在原生 `mAudioFocusLock` 下读取当前栈顶；延迟 / 外部策略等待请求不当作已获焦点；外部策略、多焦点、已处于通话或未知状态保留原生；不改 M00 通话事件过滤 |

本批只适配 `android / system_server` 的 boot/system_server ClassLoader，字段句柄在该安装的冷路径解析；不新增所有者缓存、后台任务、依赖或公共基础设施。音频焦点字段解析失败仅回退该适配，通话注册过滤仍独立安装。安全音量、插件计时与外观放入后续不同宿主的小批。

### M01.02 / M02.02：音量插件的计时、颜色与隐藏

四项功能仍位于“系统 → 声音 / 音量面板”，颜色的背景与图标值为附属设置，共校准六条目录。它们共用稳定 `ControlCenterPluginFeatureId`（`control_center_plugin`），通过 `ControlCenterPluginHook` → 既有插件加载器提取与绑定 → `initControlCenter` 安装在 `com.android.systemui` 内的 `miui.systemui.plugin` ClassLoader；不虚构新的独立 Feature ID。相同加载器仍由现有运行时防重复安装。

| 偏好键 | 定义 / 状态闭环 | 决定 |
| --- | --- | --- |
| `system_volumetimer` | 资源引导将原生 5 档替换为 11 档（至 12 小时）；插件的 `initTimerString()V`、`TimerItem.getTimePos(I)F` 和 `updateDrawables()V` 配合标签与绘制；原生 `TimerSeekBar` 从属性指向的数组读取秒数 | **局部修复**：绘制用的 `mCurrentSegment:I` 临时值改为每次调用持有，`finally` 恢复，覆盖重入和致命异常退出；仅恢复自己的临时 0，不覆盖更新后的段位；负值未知状态保留原生 |
| `system_volume_mode_button_colors`、`system_volume_mode_button_background_color`、`system_volume_mode_button_icon_color` | `RingerButtonHelper.updateState()V` 使用已有 `mState:Z`；快照、原生 blend 输入、填色 / filter 所有权和弱引用保持既有实现 | **保留**：复用已有修复与回归；首次启用或原先只安装隐藏路径时需重启 SystemUI，完整颜色 Hook 已安装后的颜色值在下次原生更新生效，不能把附属值误标为仅 UI |
| `system_volume_hide_mute_shortcut`、`system_volume_hide_dnd_shortcut` | 通过 `mIsZen` 区分两个按钮，记录原生可见性；共享分隔线任一隐藏时隐藏、容器仅两个都隐藏时隐藏 | **保留**：已安装共享 Hook 后在下次原生状态更新应用快照并恢复模块拥有的可见性；首次从全关闭启用需重启 SystemUI，不强制显示 ROM 本来隐藏的视图 |

计时宽度字段已由构造器与 `getTimePos` 的 Smali 证实为 **dimen 资源 ID**，不是像素，继续按当前资源配置读取尺寸。该窄修复没有改变 11 档秒数、标签内容、段位避让阈值、进度坐标或横竖屏资源选择；未凭字段类型猜测重写布局。两个恢复字段在插件安装冷路径解析，不新增静态 owner、缓存、Observer、Receiver 或任务；关闭功能时不安装计时 Hook。每次原方法仍恰执行一次，普通异常保持原样；反射 / 异步包装的致命错误仍通过既有 `FatalErrors` 解包并传播，同时 finally 恢复临时状态。原始结果和测试复现留在第 8 节引用的忽略目录。

### M01.05 / M02.05：音量面板超时、百分比与默认材质

合并“系统 → 声音”的折叠 / 展开隐藏时间、声音百分比、MTK 材质门禁与“系统 → 其他”的折叠 / 展开模糊；页面和默认值保持原状。六条设置仍共用 `ControlCenterPluginFeatureId / control_center_plugin`，在 `PACKAGE_READY` 通过实际 `miui.systemui.plugin` ClassLoader 安装。共享百分比控件的亮度侧同时校准一条目录，属于宿主 `BrightnessPctFeatureId / brightness_pct`，不混用加载器。

| 设置 / 闭环 | 定义与语义证据 | 决定 |
| --- | --- | --- |
| `system_volumedialogdelay_collapsed / expanded` | `computeTimeoutH()I` 对悬停、警告和普通控件分别调用原生 `AccessibilityManager.getRecommendedTimeoutMillis`；警告 flags 为 6，其余为 4 | **修复**：自定义毫秒数仍经原生无障碍推荐；保留悬停 16 秒和警告默认 5 秒。冷路径解析 manager 字段，未知 ABI 不安装本功能；默认 0 或普通适配失败交回原方法 |
| `system_cc_volume_showpct`；亮度 `system_showpct` | 插件 listener 的 `this$0` 指向当前 dialog；`getImpliedLevel(SeekBar,Int)` 使用当前最大值与浮点换算。宿主亮度有独立 onStart / onStop / onChanged；`dismissH` 在动画或勿扰弹窗期间可延后关闭 | **修复**：去掉跨面板 / 音流共用的进度去重；按来源与弱 owner 绑定唯一 overlay，替换 / 脱离窗口时释放。相同进度重开仍刷新，未知最大值不显示空面板；只在原生接受关闭后移除；旧回调不能改动或关闭另一面板 |
| `system_volumeblur_collapsed / expanded` | `updateDialogWindowH(Z)V` 原生恢复 dim / focus，展开调用 `startBlurAnim(FFI)V`；折叠不主动清理既有模糊。实际展示发生在 `showVolumeDialogH(I)V`，可晚于 `showH` | **修复**：0 保留该状态的原生材质；窗口更新或实际展示应用快照，恢复默认时只释放本功能曾写入的折叠模糊。窗口附加布尔标记不强持有 Window，关闭动画仍沿用原生 |
| `system_volumebar_blur_mtk` | 插件定义 `Util.isSupportBlurS()Z`，现有 Hook 仅放行原生能力门禁 | **保留**：两份 fuxi 样本不能代替 MTK 实机，不凭平台差异退役或重写 |

首次启用延迟 / 模糊需重启 SystemUI；已安装后通过既有 observer 更新，分别在下一次原生计时、窗口更新或展示读取。值 0 恢复原生语义，不卸载已安装 Hook。百分比与 MTK 布尔开关仍在安装时捕获，启停均需重启 SystemUI；全关时不创建这些业务 Hook 或 observer。既有 Resolver / Effect / RuntimeState、插件防重、计时 / 颜色 / 隐藏及公共 Hook 边界继续复用，没有新任务、定时器、依赖或公共框架。

### M01.08 / M02.08：高音量警告与原生确认

`140 / hide_safe_volume_dlg`、`system_nosafevolume` 保留“系统 → 声音”入口、默认关闭、备份键与安装条件。仍由 `SystemUiFeatures` 在 `PACKAGE_READY` 按启动快照安装一次，关闭时不创建本功能 Hook 或 observer。实际目标为 `com.android.systemui` 宿主的 `VolumeDialogControllerImpl$W`，不是 `miui.systemui.plugin`；设置端明确启停后重启 SystemUI，已安装状态不随偏好更新重置。

旧实现只在 `VolumeUI.start` 后确认一次，同时永久写入 `mShowSafetyWarning=false`。两份 ROM 的 `SoundDoseHelper.onCheckMusicActive` 可重新启用限制，配置变化也可重算状态；一旦重新限幅或启动确认请求失败，原生弹窗仍被关闭。直接执行旧匿名 Hook 的两项主机回归均复现这一缺口。

现改为在原生 worker 处理消息 **14 / SHOW_SAFETY_WARNING** 时读取当前 owner、原生显示门禁和 AudioManager，调用原生 `disableSafeMediaVolume`；仅确认正常返回后抑制本次警告。服务 / 权限或 owner 读取普通异常回到原方法一次，致命错误按既有边界传播；不再写原生显示门禁。原生 VC 的消息合并、其他消息、独立 **17 / SHOW_CSD_WARNING**、服务端权限检查及待处理音量命令回放均保留。音量键原生已消耗的一次增量不会由模块猜测、补发或重复；需实机检查越过阈值时的按键手感。

四个成员句柄在安装冷路径按该 ClassLoader 解析，每次从 worker 读取当前 owner 和 AudioManager，不缓存 controller。其他消息只检查消息类型后调用原方法，不复制参数数组或查找成员；同步确认仅在低频警告事件执行。没有新增常驻任务、线程、observer、容器缓存或依赖；未测量 CPU、PSS 或帧时间。Hook、行为回归、该键目录及重启提示为同一回退单元，不修改公共 Hook / PrefMap 基础设施。

关闭并重启 SystemUI 后恢复原生警告分发，但此前原生确认的累计播放 / 持久化状态仍归 `SoundDoseHelper` 管理；不声称重启会立即清除确认或重新限幅，也不主动写系统设置。无功能退役、稳定 ID / 清理 revision / 正式版本变化。SQL、验证结果与设备边界见第 8 节。

## 5. DexSQL 复核与修复闭环

沿用 [DexSQL 指引](../rom-intelligence/DEXSQL.md)，这里只记录任务需要交付的证据：

1. 复用已记录的 ROM 来源、版本和样本身份；国行 / 台湾版 Launcher 和插件的已知同源样本复用分析。已确认身份不重复算 SHA。样本缺失或 JAR 无 DEX 明确记录，不能推断接口不存在。
2. 同一批相关 APK / JAR 共用 Dataset；批量 SQL、命名临时查询、`compact: true` 和明确的行 / 字节限额优先。先查声明来源和完整签名，再查调用者、字段与接口实现，最后按需解码 Smali。Manifest / Resources 用于核对组件、区域开关、资源替换及布局目标。
3. 区分 boot framework、宿主、动态插件与应用 ClassLoader；检查重载、继承、反射、混淆和实际安装阶段。名字命中不代表 Hook 正确，引用记录不代表本包有定义，零结果不代表功能退役。
4. 核对参数、返回值、异常、`proceed` 次数、原生状态更新、重入与并发；检查 View / Controller 重建、解绑、任务取消、重复注册和失败回滚。关闭状态不得新建业务 Hook、Receiver、Observer 或任务。
5. 只修复有证据的问题。普通异常保留原生行为，致命错误传播；不新增宽泛 fallback、轮询、全量堆栈扫描或推测性缓存。涉及热路径只优化当前功能，避免改动成熟公共基础设施。
6. 涉及最终 DEX / R8 边界时按需复核同产物 mapping 与 ABI；构建、静态 ROM 和设备结果分开。DexSQL 不进入 APK、Gradle 默认任务或 CI；持久化缓存仅在跨会话确有收益时使用，批次结束释放 Dataset。

每个实际启动的小批在本页添加一条记录，最少写：`批次 ID / Feature ID 与键 / 当前与目标页面 / 包与进程 / ROM 输入身份引用 / 定义及签名 / 调用链结论 / 修复或保留决定 / 本地验证 / 待实机项 / 本地提交`。原始 SQL、Smali、ROM、mapping、日志留在忽略目录；本页只保留可恢复任务所需的结论与证据位置，不复制长日志。

## 6. 失效项的安全移除

区分四类结果：**目标已变但功能可修复、旧分支已失效、当前产品实现整体不可达、证据不足**。优先修复可适配功能；只裁掉无用分支时保留有效主路径。单份 ROM 无定义、一次 DexKit 未命中、模拟测试失败或无人实机验收均不能单独作为整项删除依据。

候选达到以下条件后，在所属小批内移除，不积累到最后一次大清理：

- 当前生产路径确实引用该项；目标范围、输入是否完整、继承 / 插件 / framework / 反射来源已查清。
- 已核对两份适用 ROM 以及有效替代路径。若仅有区域差异，限制对应路径或保留兼容实现，不据此删除另一侧有效功能。
- 局部修复不能恢复当前产品承诺，或该分支在受支持产品路径中已不可达；记录决定的证据和影响。没有证据的项标记“受限保留”。
- 同步清理入口、点击处理、Feature 注册、业务读取、Hook / Controller、专用资源和生成目录；共享资源只删确定无引用的部分。稳定 Feature ID 不重新编号。
- 有持久化键退出时沿用现有偏好弃用、目录和备份过滤机制；必要时按既有合同推进清理 revision，验证升级和旧备份恢复，不删除受限旧备份解码器。旧值不能让退役功能复活。
- 关闭、恢复、升级、搜索和其余功能有对应检查，本地门禁通过后提交；保留可追溯的本地提交，不偷偷扩大删除范围。

候选与决定统一在下面追踪；已正式退役的长期产品事实继续更新 [退役说明](A14_FEATURE_RETIREMENTS.md)，本页链接该结论，不维护第二份退役清单。

| 候选 | 当前证据 | 决定 / 下一步 |
| --- | --- | --- |
| 旧通话通知方法、Record.events 位掩码路径 | 两份 A14 ROM 的通知签名和记录结构已变化 | `3981f85` 已移除旧调用，改为注册时过滤对应事件；保留用户功能 |
| `audioFocusPkg` 最近请求缓存与整段 `handleFocusLoss` 拦截 | 延迟 / 外部策略等待请求并非实际 owner；放弃与 Binder 死亡后缓存未清；整段跳过 loss / notified 更新 | M02.01 改用原生焦点栈和 framework 窄分支；保留两个用户功能，无偏好退役或 revision 变化 |
| 音量计时器 Hook 共享 `prevSeg` 与依赖 after 恢复临时段位 | 主机回归复现不同 helper / 同实例重入互相覆盖，致命错误跳过 after；原生 `updateDrawables` 读取而不写入 `mCurrentSegment` | M02.02 改为调用内临时值与 finally 恢复，保留当前有效绘制路径；不改公共 Hook 适配器 |
| `VibratorService.doVibratorOn` 与无振幅支持时的时长缩放 | 双区域旧入口无声明；A14 owner 与 framework 的原生 Effect 缩放链已核实 | M02.07 接入 `VibrationScaler.scale(VibrationEffect, Int)`；保留强度调节，不修改时长或伪造预制效果支持 |
| 侧边栏全类触摸 / Drawable.draw Hook 与错误 Runnable 强转 | 延迟逻辑存在确定类型错误；全类影响范围超过单个侧边栏 | `3981f85` 改用当前 View 的监听器与背景，解绑取消任务；保留展开能力 |
| Launcher 的 `allowedSlidingUpToStartGolbalSearch`、`getTitleShadowColor(Int)` 旧名 | 当前两份 Launcher 中无对应声明，但源码包含备用或不同包名分支 | **受限保留**；核实分支可达性和完整兼容范围后再决定，不能据此下线手势或标题阴影功能 |

## 7. 状态与每批完成条件

复核记录分别填写“静态证据”和“运行证据”，不使用含糊的整体 PASS：

- 任务：待核查 → 取证中 → 修复 / 保留 / 移除候选 → 本地完成。
- 静态证据：缺输入 / 无法确定 / 定义与调用吻合 / 定义存在但语义不符。
- 运行证据：未实机 / 已复现 / 待验收 / 对指定设备与场景验收通过。

实施中运行受影响测试和 `fast --changed`；同一收口批最终源状态统一跑一次 `full` 与 diff 检查。只有工具目录改动才追加 Python 工具测试，资源 / 搜索生成器受影响时做对应合同检查。R8、AAPT2、Native 和资源收缩只在相关范围做必要构建，不反复完整 CI、双 clean、签名或 SHA 核验。

每批提交前确认：修复有依据；设置键 / 默认值 / 单位 / 依赖和备份处理一致；搜索与返回正常；正确重启对象；关闭与失败回退正常；没有跨实例或 ClassLoader 状态污染；热路径无新增无界工作。CPU、内存和流畅度收益只在测量后报告，代码缩短或静态预算不能代替实机性能。

缺少设备时仍可完成有边界的本地修复，但必须留下具体待验收场景，不声称全 ROM 兼容。一次只收口当前高价值小批，不为填满地图扩大改动；版本与发布策略变化须有明确任务授权。

## 8. r14.25.1 最终实现与验证

本节仅记录相对正式版 r14.24.1 的最终差异；实现细节见对应源码与 [动态岛说明](../features/island/README.md)。

| 范围 | 保留实现 |
| --- | --- |
| 系统设置 | 系统、桌面、按键与手势、应用与维护维持四个入口；卡片内外分组、搜索面包屑、动作编辑与滑杆精确输入遵循真实页面层级。动态岛在系统界面组紧跟状态栏，路由键及偏好编码保持 |
| 声音与振动 | A14 原生振动缩放、音频焦点所有权、高音量确认、音量计时器文案 / 坐标、面板超时和背景释放 |
| 启动器与 Dock | 图标中心对称尺寸限幅、全宽底栏、原生材质及有界壁纸采样玻璃，几何与原生交互坐标保持一致 |
| 运行稳定性 | 可视化任务随可见性取消、维护恢复快照异常处理、Hook 进程矩阵登记、原生异常与致命错误边界 |

### M05.04：动态岛

- **A / 充电与系统反馈**：动态岛模式在 StrongToastControl 统一入口屏蔽额头窗口。独立 MiuiChargeController / MiuiChargeAnimationView 由 ROM 管理，不压缩充电视频，不增加电池监听。系统默认及匹配状态栏高度的原生模式保留。目标 TW ROM 为 V816.0.7.0.UMCTWXM；静态资源证据与设备验收分别报告。
- **B / 所有权与状态栏**：音乐、十类系统终态、短时提示和接管通知共用 IslandActivityController 模块窗口、IslandActivityView 与有限动效时钟。展示需有效布局、attach、非透明和允许显示；事件令牌与 generation 成对释放。仅处理与实际动画矩阵 / 屏幕边界相交的单个状态图标；清晰图标不变，未知布局不隐藏整条状态栏。控制中心期间只暂存最新有效状态，从原事件起最多 6 秒，锁屏、过期及销毁丢弃。
- **C / 两态与交互**：SHORT / LONG 共用高度及端帽几何，受摄像头、时钟、电量及 Duo 安全区约束。左右切歌、上滑隐藏、封面 / 原生图标和明确成功状态精简固定启用；播放器动作能力、隐私与会话归属继续校验。升级与备份遵循当前有效偏好契约，不重置无关设置。
- **D / 动画**：“上下”保持宽高、沿顶部进入和退出；“中心”从首帧固定高度和纵向位置，只横向伸展 / 收窄及淡入淡出。进入 / 宽度变化 / 回位 / 退出为 360 / 320 / 280 / 300 ms，第二活动为 440 ms 分层交接。中断保留位置 / 速度，安全边界制动，内容维持物理比例。
- **E / Dock**：跨窗口模糊禁用取模块与系统配置的 OR；Launcher 私有材质、壁纸采样 Shader 和状态栏临时 RenderEffect 分开管理。采样有界、失败退役，替换 / 解绑释放。动态壁纸、采样不可用及完整设备矩阵未覆盖，不据主机或静态证据宣称设备性能收益。
- **F / 范围与成本**：动态岛相关生产源文件 4→19（新增 17，删除 2），五种 Hook 注册入口机械调用点 6→15；均相对 r14.24.1，不代表实际安装数量或性能收益。系统图标限定十类状态：4 个 ROM 原生提示及 21 个 SystemUI 备用资源名；只读挂载资源，不加载插件代码或持有控制器。图标冷路径至多 96×96 临时探测，约 72 KiB，固定 24 dp 单元内约 20 dp 可见图形；状态栏仅保留至多 128 个当前叶图标，树遍历限制 256 节点 / 32 层，离树记录及时恢复并移除。
- **G / 验证边界**：最终应用源码 full、fast --changed、JDK 25 编译及 lint 通过；330 suite、3135 JVM，0 失败 / 错误、2 跳过。Python 工具测试 556 项，5 跳过。独立审查无确定缺陷。行为回归覆盖中心 / 上下进出及中断、根矩阵与跨窗坐标、150 次图标替换、原生提示资源优先级和生命周期。按任务约定不对本次最终候选继续实机；正式构建独立完成 R8、fatal lint、APK 签名 / 来源检查。不将主机帧图当作实机 FPS。
- **H / 发行与回退**：公开提交只包含最终结果，既有稳定历史保留。原始开发历史与未提交补丁保存在本地，不进入发行记录。回退使用正式发行 tag 或对最终提交执行 revert，不移动历史 tag。同一已核验 Actions APK 发布双仓库，versionCode 不复用不同 binary。


## 9. 2026-10-23 工作分支校准（历史阶段）

- 自动发布：工作分支与合并后的 main 分别核验；main CI 成功且新版本 / 双语记录齐备后请求一次确认，默认先发布源码仓库、验收后同步同一 APK 到 Xposed。支持手动触发、仅候选、预发行和同源失败恢复；Changelog 与版本准备、命名、权限和验收规则统一到 [发布流程](../RELEASE.md)。普通 CI / PR 不签名、不发布。
- 音乐：新增默认开启的 `system_island_media_swipe`；关闭立即禁用两方向切歌并取消待执行操作，点击打开与上滑隐藏保持。暂停保留期只由实际显示的音乐岛建立；播放器内暂停、延迟入场与 Toast 占窗后返回桌面均不新建暂停岛，重复快照不续期。
- 设置与文字：核对 34 个业务页面及 25 个独立子页，当前业务页共有 342 个带键选项；无重复设置键或跨页面悬空依赖。动态岛按显示、动画、显示内容、音乐与媒体、应用分组；短岛 / 长岛称谓在十种语言中对齐，中文 Dock、Wi-Fi、Home 及黑白名单等文案统一。设置控制器只保留当前有效动态岛键。
- 文档：页面地图按源 XML 与中文资源对齐；架构移除旧展开态、旧手势总开关与旧系统反馈队列描述，区分当前源码和 r14.25.1 已发布事实。旧版验证数字只属于第 8 节的历史源码，不作为本分支通过依据。
- 后续音乐手势：下 / 左 / 右滑、双击和长按的独立配置完成候选动作、默认值、已有矩阵编号、所有权与回归设计；上滑固定隐藏。当前无 DexSQL 连接 / ROM APK，新增查询交给桌面执行，五手势配置尚未实现；具体交接见 [动态岛方案](../features/island/MUSIC_GESTURES.md)。
- 待实机：播放器内暂停后立即回桌面、播放后不足 750 ms 即暂停、Toast 占窗时暂停、正常可见暂停两秒退场、重新播放入场，以及切歌开关快速切换。主机模型 / CI 通过不能替代目标 HyperOS ROM 的设备验收。


## 10. 交接与下一步

当前代码和设置说明以 [功能目录](../features/README.md) 为入口，运行结构见 [架构](../../ARCHITECTURE.md)。动态岛实现与测试已按 island 包归档；稳定 Feature ID 和历史偏好编码沿用。第 9 节记录的是五手势实施之前的历史阶段，不能作为当前功能缺失结论；当前结果见下表和第 11 节。发布的支持场景、确认点及恢复规则统一维护在 [RELEASE](../RELEASE.md)，不另建第二套流程文档。

| 待完成部分 | 所需条件 | 继续入口与验收 |
| --- | --- | --- |
| 远端发布环境 | 仓库管理员配置 Required reviewer、显式分支限制、历史签名 Secrets，以及限定 Xposed 仓库 Contents / Workflows write 的现有凭据 | [一次性配置](../RELEASE.md#一次性配置)；代码 / 模拟测试不能证明远端配置有效 |
| r14.25.2 / 247 | 本轮用户已明确授权完成后正式发布；仍需最终源码门禁和有效签名 / 发布入口 | 不重复准备旧版本；核验 exact main CI、签名和双仓库同一资产，实际发行状态单独读回 |
| 音乐手势独立配置 | 已在 `56619f9` 实现，并有 CN / TW 静态证据与主机测试 | [方案与动作矩阵](../features/island/MUSIC_GESTURES.md)；下一步是目标 ROM 的当前播放器、蓝牙 / 投放音量和取消交互验收 |
| 目标 ROM 实机 | 明确 ROM 版本、同一 APK、安装权限及日志 | 验收首次启用、切歌开关、播放器内暂停快速回桌面、Toast 抢占、恢复播放、重建 / 锁屏；Dock / Duo 沿用各自待验收范围 |
| GitHub 绿色 Verified 标签 | 已登记且由维护者持有的可验证 Git 签名身份 | [认证区别](../RELEASE.md#github-认证标记)；APK 来源证明已接通，不能当作标签签名，不能改写历史 tag |

完成授权范围的分支 / main 门禁并交付 Actions 链接后，Agent 结束，不建立持续监控。真实发布由维护者确认和 Actions 后续门禁管理；失败按原候选恢复。APK、ROM 原件、签名材料和原始测量留在仓库外。

### r14.25.2：音乐活动、配置与显示证据闭环

从实时远端 main `2b321c6a27e16538cb983d3cedc5c36da33f5eda` 创建 `codex/r14.25.2-refinement`，复用 M05.04 的单窗口、StrongToast 统一抑制及状态栏相交避让。上一轮只授权本地实现；本轮用户已明确要求完成独立复核和专项修复后发布 r14.25.2，不重发 r14.25.1。

- 暂停资格改用现有 presentation 的实际预绘制证据，未显示窗口不执行退出动画；离开播放器先取新快照。控制中心内新播放再暂停不能借用旧活动的历史展示证据。同身份退场重入重新取得可见性与避让所有权。
- 五项音乐手势复用 MultiAction，追加 MUSIC 枚举及独立播放 / 暂停 / 打开播放器编号，不改旧动作；统一解析与 TransportControls 执行、能力位、当前会话和 action epoch。关闭横向切歌仍允许音量等明确配置；输入与任务随配置、抢占、前台 / 隐私及释放失效。平台单击 / 双击 / 长按与无障碍共用入口。
- 高度保留 38–44 / 默认 40 dp，自动宽度温和联动，字体 / 图标分别处理。左 / 中 / 右及横向偏移经过 Insets 和开孔限制，仍按真实显示边界避让。大型 StrongToast 充电素材继续抑制，独立系统充电动画保持系统管理，无新增充电 Hook。
- 十类系统反馈按需注册并在关闭时清除旧事件；新安装默认静默，原来已启用岛的用户迁移保留旧反馈。既有 AudioManager / 磁贴因果标记也改由窗口所有者按需租用，仅相关类别同时启用时安装，最多六个句柄，关闭 / 替换 / detach 释放，弱引用及 live 门禁隔离旧回调。通知接管增加默认空允许名单，排除名单优先；未接管通知、原通知中心条目、排序与原生清除行为维持。
- 设置保持显示、动画、显示内容、音乐与媒体、应用五组，十种维护语言补齐新资源。扫描设置源 XML 与动作 / 备份契约；现有 Hook 的安装重启边界保留，分类、定位和手势在已安装接入内即时更新。复核 Dock 单张采样 / 单次在途 / 隐藏释放及 Duo 订阅身份校验，未发现本批必须改写的成熟路径，没有宣称设备节电或帧率收益。
- DexSQL 0.1.1 对 CN V816.0.19.0.UMCCNXM、TW V816.0.8.0.UMCTWXM 的 SystemUI、插件、framework 实际查询与 Smali 复核；样本摘要、定义来源、匹配数及待取证能力见[音乐手势](../features/island/MUSIC_GESTURES.md)。不新增未知 ROM Hook，不以静态证据代替运行时结论。
- 发布审计发现服务器 release-signing 当前缺少 Required reviewer。Actions 的自动 / 手动入口均要求审核保护，签名前重查；本轮未调整服务器保护、Secrets 或分支策略。该缺口仍阻挡 Actions 正式签名；本次另有明确发行授权，使用仓库保留的本地正式构建 / 双仓库验收入口，不伪称具有 GitHub 构建来源证明。

回退单位：媒体可见性与 presentation 重入；音乐动作与 View / 编辑器；位置与尺寸；分类监听及迁移；通知允许名单；发布审核门禁。没有新增依赖、线程池、全局输入监听或第二套窗口。每个检测器 / Job / 注册由现有 View 或 Controller 持有，关闭 / 替换 / detach 取消，反馈为容量一，图片和 Dock 既有上限不变。

验证使用既有 fast / full、Python 工具、Android JVM / Robolectric、Lint、资源及 R8 / D8 构建门禁。具体执行输出保留在忽略的 `.di-evidence/r14.25.2/`；最终结果以本次验证命令退出状态为准。目标 HyperOS 的实机触摸、暂停回桌面、DND 收起控制中心、充电动画、开孔位置、通知工作资料、蓝牙 / 远程音量、Dock / Duo 连续使用与 CPU / GPU / PSS / 帧时间仍待设备验收。

## 11. 2026-10-23 独立复核、Dock 动画与 M05 / M07

### A. 上一轮实际成果复核

审查基线为本地 `56619f9a63249303b5726393fdeb3e31934c0879`，相对实时 `origin/main = 2b321c6a27e16538cb983d3cedc5c36da33f5eda` 的一个未推送提交（56 文件，1951 行新增 / 181 行删除）。开始时工作区干净；PR #45 已整合至该 main，其 main CI `39878924053` 成功，但不覆盖本地新提交。上一轮未签名 APK 的摘要为 `135b62f0d722963325de933313f3ea00676600f1484c5b3c89c0233e18789d5f`，仅是旧候选证据，本轮不作为正式资产使用。

以下 VERIFIED 仅指主机 / 静态范围。旧日志报告 3167 项 JVM、591 项 Python 工具测试及未签名 R8 构建；本轮最终门禁重新执行，不以该汇总自证通过。没有发现该 diff 引入的已确认 P0–P2 运行缺陷；第 10 节把已完成五手势写作未实现是已确认文档失配，现已校准。Dock 与返回区域宽度是本轮独立范围内确认的既有缺陷。

| 审查项 | 结论 | 可复核实现与边界 |
| --- | --- | --- |
| 1 原生额头互斥 | IMPLEMENTED / 主机覆盖 | StrongToast 统一抑制与单一 IslandActivityController 窗口；不是第二套额头叠加 |
| 2 暂停快速回桌面 | IMPLEMENTED / 主机覆盖 | presentation 预绘制证据、foregroundBaseline 和原事件暂停期限；实际 View attach，区分未显示与已显示 |
| 3 左右切歌开关 | IMPLEMENTED / 主机覆盖 | 关闭时解析器阻止横向切歌，Controller epoch 与 View cancelInputs 同步取消旧动作 |
| 4 五项独立音乐动作 | IMPLEMENTED / 主机覆盖 | IslandMusicActions、MultiAction.MUSIC、TransportControls / 会话音量；历史整数编码未重排 |
| 5 输入仲裁 | IMPLEMENTED / 主机覆盖 | GestureDetector 与锁定滑动、多指 / CANCEL、长按后 UP、无障碍共用动作入口 |
| 6 会话与旧动作取消 | IMPLEMENTED / 主机覆盖 | 选中控制器身份、sessionEpoch、readGeneration、actionEpoch、单一 actionJob / Mutex |
| 7 高宽与字体 | IMPLEMENTED / 主机覆盖 | IslandSizing 与 View.contentHeight；38–44 dp，自动宽度温和联动，字体 / 图标分开缩放 |
| 8 三向定位与偏移 | IMPLEMENTED / 主机覆盖 | IslandPosition 物理安全区和开孔间隙；空间不够时不强行展示 |
| 9 状态栏避让 | IMPLEMENTED / 主机覆盖 | 实际预绘制矩阵相交图标，令牌成对释放；未知布局不隐藏整条状态栏 |
| 10 逐类系统反馈 | IMPLEMENTED / 主机覆盖 | 十类独立键、初次采样不提示、容量一终态；升级迁移不重启已关闭类别 |
| 11 按需监听 | IMPLEMENTED / 主机覆盖 | enabledKinds 限定 Receiver / Observer / Camera；最多六个因果标记句柄，关闭 / 替换释放 |
| 12 通知允许名单 | IMPLEMENTED / 主机覆盖 | 默认空允许名单、排除优先、前台 / 用户过滤；沿用原生 row 点击与清除语义 |
| 13 充电与勿扰 | PARTIAL / DEFERRED | StrongToast 充电素材继续抑制，独立系统充电动画由 ROM 管理；勿扰级联与控制中心暂存有主机覆盖，真实收起时序待设备 |
| 14 设置 / 默认 / 恢复 | IMPLEMENTED / 主机覆盖 | 五组设置、默认左右动作与反馈迁移、部分新备份不自动补开类别，既有备份键保留 |
| 15 多语言与说明 | IMPLEMENTED / 静态覆盖 | 十种维护语言新增资源已补齐；中文布局、摘要和导航测试；人工语言审校不等于 XML / Lint 检查 |
| 16 双仓库与签名 | 上一版 VERIFIED；本版发行单列 | r14.25.1 两端公开且 APK 均为 3962521 bytes、SHA-256 `bfd3c6b08d709071edee0cafbdfb53d77db8dbfd2e2003cf6eac659956f35092`；两 tag 同指 `3e8683daa70f6849674e0864ee8edd9fe9d3b3fe`。本轮开始时 r14.25.2 无远端 tag / Release |

所有权维持一个窗口 Controller、一个选中媒体会话和有界任务；旧会话、配置与隐私变化均失效旧工作。没有新增无界缓存、第二个全局输入监听、线程池或重复窗口。动作中的 Binder 在现有 IO 调度执行，致命错误由既有边界传播；不能据主机测试宣称 ART、CPU、GPU、PSS 或功耗改善。

### B. Dock 已确认根因与修复

- **P2 / 原生解锁参与者遗漏**：ROM 的 `HotSeats.getUserPresentAnimationChildList()Ljava/util/List;` 只转交 `mContent` 子项。`UserPresentAnimationCompatComplex.operateAllPresentAnimationRelatedViews` 对其执行准备、展示与重置，V12 使用原生 SpringAnimator 更新 alpha / translation / scale；HotSeats 同级的普通背景和根容器全宽背景都未参与。现仅复制原列表并附加当前背景，由同一个 ROM 控制器驱动；不改图标列表原件、事件或监听器。
- **P2 / 全宽跨层级只同步 alpha**：原 `onPreDraw` 忽略 HotSeats 与中间父级的 matrix，并把父级 alpha 写在背景本身，可能覆盖原生解锁属性。现由 `DockFullWidthContainer` 承担继承变换，背景自身保留原生属性；普通模式直接继承原 HotSeats。几何取消只重置确由宽度缓动持有的 scale，避免覆盖解锁帧。
- 以根容器布局坐标为目标，`C = hostToRoot × inverse(hostLayoutToRoot)`；逐级组合 layout、scroll、既有 View matrix，编辑时插入原生已计算的 legacy matrix。共享 root 不重复计算，无求逆抵消误差；背景本地解锁变换仍由原生持有。View 坐标 helper 不包含独立 RenderNode animation matrix，因此该容器为壁纸采样补全实际 global matrix。Android 14 的 [RenderNode JNI](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r1/libs/hwui/jni/android_graphics_RenderNode.cpp) 区分这两项；目标 framework 的 View / RenderNode 声明与 Smali 已复核。
- `AnimationSet.getTransformation()` 原 Hook 仍只调用一次原链，读取返回的 Transformation，不求第二次进度。相同矩阵不重复设置；不新增定时器、延时补丁、常驻帧循环或反复重建。全宽增加一个无交互 owner 容器和固定矩阵；HotSeats detach 时移除容器并释放背景原有采样 / 材质 / observer。
- 回归在 `DockNativeAnimationTest` 逐进度检查 0 → 0.2 → 0.65 → 0.4 → 1 → 0 → 1 的 alpha / 位移 / 缩放，覆盖普通 / 全宽、几何刷新、父级变换、legacy 矩阵中断、Google 模糊区域关闭和恢复；复用 DockBackground / FullWidth / GlassBackdrop / Geometry 等材质、编辑、拖拽与释放回归。这是主机模型证据，不能称为真实 ROM 逐帧验收。

### C. M05 / M07 的实际 DexSQL 结果

工具实测为 DexSQL 0.1.1；先读取已安装文档与 schema，再复用三个数据集。只使用 definition 视图确认声明，调用关系按同数据集的 file_id 连接。原始查询、分页 Smali 和输入记录保存在忽略的 `.di-evidence/r14.25.2-review/`。来源均为本机 `forA14-ROM-ext` 的 fuxi HyperOS 1 / SDK 34 样本；静态定义来源不能证明实际 ClassLoader 或 Hook 命中。

| 输入 | TW V816.0.8.0.UMCTWXM SHA-256 | CN V816.0.19.0.UMCCNXM SHA-256 |
| --- | --- | --- |
| product/priv-app/MiuiHome/MiuiHome.apk | `a6546d51d9220039ed7ac143de249e9014202cfafeafd715237eb49f8f5a3f7b` | 同一 binary；不是独立适配证据 |
| system_ext/priv-app/MiuiSystemUI/MiuiSystemUI.apk | `66386ad63878da129041fab7ae334d313257703515e4c9a179cb092292b32dc1` | `2bf4f7e56eeda694e8f7de71c0acda2cae892f9847c6d6a747a52cfe5381e42b` |
| system/system/framework/framework.jar | `8586618b92c2892a9c6be1cfd28871d9b39655162ff97a4b12f8d966d14895e9` | `cdfca3dd220865fc5252e3af938c1fb4254c840678e386ce8216a2f41a9aaeb5` |
| system/system/framework/services.jar | `9341c22bf12f98d12607af3b93ef7c597931309a32e388c3bdc394854840db75` | `e9552126b93b1b8e5931e0fdac8ab166ca1c98c29d877fde6ac23773cba78dd0` |

MiuiHome Manifest 为 `com.miui.home` / `RELEASE-4.39.30.8604-08262248` / 439308604。下表 file_id 仅在本批相应数据集有效：Launcher 的 TW / CN classes2 为 1 / 3；SystemUI classes 为 0 / 4；framework 中 View / InsetsSource 为 3 / 8，services 的 DisplayPolicy 为 12 / 15。样本不同则重新查询，不能跨数据集拼接索引。

| 专项与查询 | 实际数量和关键证据 | 决策 |
| --- | --- | --- |
| Dock 原链与 `launcher_unlock_contract.sql` | 20 个声明 / 36 条去重 invoke；每份 Launcher 唯一 HotSeats / HotSeatsScreenViewContent 返回列表，V12 setViewPrepareInfo 对 View 写 alpha / scale / translation；resetView 恢复五项属性 | 按 B 接入原生参与者，保留原有编辑链；复用 launcher_dock_contract 的 72 条定义结果 |
| M07 `navigation_gestures_contract.sql` | 24 个声明、4 条初始化调用、4 个字段；每份唯一 `GestureStubView.initScreenSizeAndDensity()V`，`(I)V` 为零。Smali 写 mGestureStubSize / mGestureStubDefaultSize；调用者 initGestureStubSize 与 onConfigurationChanged | **P2 已修复** Controls 的错误整数参数注册。原调用成功后缩放新原生字段；测试重复三次不累乘、setSize 默认不二次缩放、100% 恢复、普通 / 致命原生失败不吞掉 |
| M05 `statusbar_layout_refresh.sql` | 32 个声明；InsetsProvider 当前旋转方法返回 `android.util.Pair`，由 DisplayCutout / MaxBounds / rotation 和原生有界 Insets 缓存计算；Miui 状态栏 attach / detach、cutout / 图标容器、网络订阅与电池回调均有唯一声明 | 保留已完成高度、Insets、Duo、网络快照与温度 owner；未确认需要新增的 Hook，不改原生缓存或绕开开孔 |
| M05 `statusbar_gestures_contract.sql` | 16 个声明 / 10 条去重 invoke；PhoneStatusBarView.onTouchEvent 依次 updateBarInteracting、handleEvent，再按结果交 Gefingerpoken；拦截路径独立 | 模块仅 TOUCH 派发 GestureMachine，attach prepare / detach clear；原生面板分发保持。CANCEL、仲裁与 EffectExecutor 复用现有回归，不与 Launcher 导航合并 |
| M05 既有 `statusbar_height_contract.sql` / definition_sources | 30 个声明 / 22 条去重 invoke；framework 和 services 各自保留定义来源，Insets 合并链与 DecorInsets 失效分开 | 沿用先前有证据的架构 C；本批不扩大应用 / framework 全局 Hook |

桌面单双指滑动、双击、长按与侧滑停顿继续沿用现有 detector / owner 和动作矩阵；当前没有足够证据改写原生快速切换、Home 长按或振动链。导航区域宽度初始化发生在冷路径，本次没有新增高频反射。状态栏与动态岛分属独立触摸窗口；真实开孔触摸边缘、通知栏 / 控制中心竞争、远程过渡和 Launcher 取消时序仍为 **UNVERIFIABLE / DEVICE**，不以声明存在标为兼容通过。

下一批优先：目标 fuxi 的 Dock 锁屏 / 快速解锁 / 中断与关闭开关对照，然后 M05 双卡默认数据切换、锁屏状态栏重建和字体 / 密度变化，最后 M07 返回区域宽高、侧滑停顿 / 快切取消、底部上滑与原生面板冲突。先取同 APK 的 Hook 日志与逐帧 / owner 证据，再决定候选修复，不继续堆叠猜测性 Hook。

本地收口：`verify.py fast --changed`（含 66 项 Dock / 手势专项）、`verify.py full`（编译、全套 JVM / Robolectric 与 Lint，2m16s）、591 项 Python 工具测试（5 skipped）以及 `git diff --check` 均退出 0。补看 ROM setSize Smali 后，测试替身同步写两个真实字段，追加相关两项行为回归退出 0；生产源码未变，复用完整门禁。exact main CI、最终 APK 和双仓库结果另以相应运行 / 发行记录读回。本地历史签名配置预检有效；正式 APK 仍须在干净 main 上构建并核验证书与来源。

ADB 当前无设备；所有实机功能和 CPU / GPU / PSS / 帧时间仍未验证。回退单位为 Dock 原生动画接入（含全宽容器）与返回区域初始化，未改变 PrefMap、FeatureRegistry、ResourceHooks、ProcessRouter 或 PreferenceBootstrap。
