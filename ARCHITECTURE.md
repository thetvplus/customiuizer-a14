# A14 架构

工程取向与基础约束见 [AGENTS](AGENTS.md#产品与取舍)，产品范围见 [COMPATIBILITY](COMPATIBILITY.md)。本页维护运行时合同、实际路由、API 边界和状态归属。

## 运行时不变量

- 按进程路由；Feature 同进程只安装一次，偏好变化不重置安装状态。关闭功能不创建业务 Hook、Receiver、Observer 或任务；安装型选项保留重启语义。
- 保持 Hook 时序、参数改写、返回 / 异常语义及 `chain.proceed()` 次数。回调最外层用既有安全边界，普通异常局部隔离，不吞 `OutOfMemoryError`、`ThreadDeath`、`VirtualMachineError`。
- Receiver / Observer / listener / controller 绑定所有者，覆盖替换、迟到失效和释放；静态字段不强持有 Activity、View 或短生命周期 controller。
- 反射、DexKit、磁盘 I/O 和同步 Binder 留在冷路径；热路径读准备好的不可变 / 原子状态。缓存有界且按 ClassLoader 隔离。资源增长说明容量、失效、释放、成本和验证，测量规则见 [优化门禁](docs/OPTIMIZATION_GATES.md)。
- 按[关键路径优化原则](docs/OPTIMIZATION_GATES.md#关键路径优化原则)准备稳定依赖、按需激活业务并增量提交状态。准备限于依赖已就绪的安全生命周期，不提前安装 Hook 或把临时未就绪缓存为永久缺失；异步发布核验当前 owner / generation。复用不得跳过当前安全 / 会话核验、独立事件或释放；配置、尺寸、字体 / 主题、来源或 owner 变化使对应结果失效，原有视觉、弹簧速度与交互语义保持。
- PrefMap、ResourceHooks、FeatureRegistry、ProcessRouter、PreferenceBootstrap 的变化须有具体问题、受影响契约和专项回归。保留 Dynamic Island / Dock 视觉与布局合同；设置界面采用 Compose，宿主 Hook 保持原生 View / Canvas；不默认引入 DI、StateFlow 或全量语言迁移。语言迁移小批量、行为等价，与功能变化分开。
- 项目自绘圆角复用共享 G4 曲率模型，保留现有视觉比例；单位曲线按需缓存，逐帧不重新积分或创建效果对象。原生组件遵循系统自身几何；材质接管每帧仅保留一个可见填充，回退不叠加另一种曲率轮廓。

## 产品职责

| 级别 | 范围 | 维护方向 |
| --- | --- | --- |
| CORE | 通知、动态岛、Dock、状态栏与手势 | 触摸、切换、动画、原生恢复与资源释放 |
| MAINTAIN | 声音 / 振动、Toast、控制中心辅助、设置 / 搜索 / 备份、有限维护工具 | 安全安装、可靠恢复，不扩张停用范围 |
| RETIRE_CANDIDATE | 有目标 ROM 失效、原生重复或所有权无法闭合证据的具体实现 | 在[退役页](docs/maintenance/A14_FEATURE_RETIREMENTS.md)逐项裁定；引用数不构成删除依据 |

当前精修状态与证据归[路线图](docs/maintenance/A14_REFINEMENT_ROADMAP.md)，性能口径归[优化门禁](docs/OPTIMIZATION_GATES.md)。

## 入口与进程路由

| 层 | 职责 |
| --- | --- |
| `MainModule` | libxposed 生命周期、偏好来源与进程入口 |
| `PreferenceBootstrap` / `ProcessRouter` | 发布快照，按包名与进程选择 scope |
| Installer / FeatureSpec | 按 scope、阶段与开关选择功能 |
| `FeatureInstallRegistry` | 稳定 Feature ID、单次安装与所有权 |
| resolver / Hook / Controller | 冷路径 ROM 解析、宿主调用与可释放状态 |

`onModuleLoaded` 初始化偏好并记录版本 / revision；`onSystemServerStarting` 和首包 `onPackageReady` 安装对应入口。SDK 非 34、非首包或不可安装 scope 在业务初始化前返回。首次偏好未就绪时 system_server 延后决定受控功能，首个快照只补装未安装项；偏好更新不重置注册表。SystemUI Context 由 `SystemUiBootstrapCoordinator` 协调，重启时间仅作诊断，不跳过目录。

基础初始化、资源准备和动作通信入口可常驻；可选业务由开关控制。安装条件与运行期快照分开，具体重启语义属于各功能合同。

偏好启动、单键和批量刷新在同一发布锁下读取并更新快照，完整发布后才公开就绪状态；观察者在锁外分发。读取异常保留最后有效值，确认键已删除才移除。system_server 的已提交高度覆盖仍优先于框架缓存；应用行为和 Wi-Fi 自动设置的 13 个固定键复用 non-exported、仅 system UID 的 Provider 读取已确认远端镜像，在同一次发布后通知观察者。单键仅作类型读取，批量一次原子快照，删除显式恢复默认，失败保留原快照；不接受广播值、不暴露完整偏好、不新增监听或轮询。新增这两组设置键须同步窄白名单及回归，不能直接依赖已知陈旧的框架缓存；不改变 Feature 单次安装或安装型选项的重启语义。

进程矩阵只生成 [JSON](docs/rom-intelligence/A14_PROCESS_MATRIX.json) / [CSV](docs/rom-intelligence/A14_PROCESS_MATRIX.csv)，来源是 catalog、路由和 scope。以下非表格化边界由源码和回归维护：

- SystemUI、Settings、PermissionController 只接受各自主进程。SecurityCenter 的常规功能也只在主进程安装；精确的 `com.miui.securitycenter.remote` 电源进程只安装已有低电量策略，提前返回，不安装主进程 UI 或 GenericApp 功能；其它 secondary / bootaware 继续拒绝。fused location 与 networkstack 进程拒绝。
- `miui.systemui.plugin` 不单独加入 scope；SystemUI 从 `PluginFactory.createPlugin` 取得插件 ClassLoader，安装 / 释放由插件 runtime 持有。
- 键盘包必须在静态 scope；`ProcessRouter` 交给 InputMethodInstaller，scope 外无回调。Launcher 还可按用户选择安装 GenericApp 的 post-attach 功能。
- POCO 的 `com.mi.android.globallauncher` 仍是 GENERIC_APP；只有该包主进程且明确启用文件夹图标模糊时，才复用 Application.attach 安装单独 Feature。不得把整个 Launcher catalog 扩至 POCO；MIUI 的 Launcher 路由不变。
- `StatusBarHeightFeature` 虽为 ANY，仍限资源包集合和高度开关；窗口几何归 system_server 的 Insets Feature。`AlarmCompatFeature` 另受应用名单控制。
- SystemUI bootstrap 与 Launcher 在安装派发前维护 ReflectionCache 安全生命周期；GuardProvider / Media 按需加载并关闭 DexKit，目标始终用实际加载器解析。

## API 102 安装与 Service

元数据固定 `minApiVersion=102`、`targetApiVersion=102`、`staticScope=true`。`HookInstallation` 统一原生 HookBuilder 安装、优先级与 PASSTHROUGH；`HookerClassHelper` 保持参数、提前返回、原方法异常和 after 恢复，fatal 传播。

before / after 只读单参数统一使用 `getArg(index)`；尚未物化参数时直接读取当前 Chain，已有可变数组时读取该数组，after 因而能看到 before 的参数改写。只有实际改写参数或确实需要整组数组时才调用 `getArgs()`；只读 before 保留无参数数组的 `chain.proceed()`，after 不为读取单项复制整组参数。不改变原方法调用次数、回调顺序或异常恢复。

| 基础设施安装点 | 固定 ID |
| --- | --- |
| `SystemUIInitializer.init(boolean)` | `systemui.init` |
| `PluginFactory.createPlugin` 全部声明重载 | `systemui.plugin.create` |
| 已选进程的 `Application.attach(Context)` | `application.attach` |

ID 按模块和 executable 隔离，可跨重载复用，不能代替单次安装状态。setId 失败停止本次安装，intercept 不重试；回读相等才记 `<id> (confirmed)`，普通回读失败仅诊断并保留 handle，fatal 传播。不启用自动热重载、replaceHook 或 detach；这需要独立的旧代任务、JNI、监听和 ClassLoader 交接设计。

`XposedServiceManager` 每次选择只读一次 scope Binder 快照，只请求未授权包；批准由框架决定。普通异常返回不可用，不自动重试；异步提示只持有 Application Context。Service 交付受签名权限保护，偏好镜像检查真实 commit，保留每连接一次重试。

R8 保留入口、Hooker、JNI、ServiceLoader 及设置进程与 compileOnly API 的类加载边界。最终 APK 用[同产物 mapping / DexSQL](docs/rom-intelligence/DEXSQL.md)复核 setId / getId、禁用替换 / 热重载及入口调用；指令数量不能证明实际安装次数。框架确认仍需设备日志。

## 功能所有权与源码位置

| 位置 / owner | 责任 |
| --- | --- |
| `MainModule`、`installers`、`mods/utils/feature` | 入口、路由、稳定 ID 和安装目录 |
| `mods/island` | `DynamicIslandActivities` ROM 接入；Controller 持有安全 / 会话 / 租约，View / Motion / Sizing 呈现；输入容器只管 touchableRegion |
| `mods/duostatusbar`、`clock`、`battery` | 独立功能状态与 View |
| `mods/vpn` | system_server 原生应用 / VPN 事件、单目标连接所有权；设置只读桥不传输 VPN 凭据 |
| Launcher Dock Hook / 装饰 / 采样 / 材质 | HotSeats 所有权及背景；共享模糊仍在公共层 |
| `mods/utils` | 跨功能 Hook、安全、ClassLoader 缓存及生命周期机制 |
| `utils`、`prefs`、`subs` | 设置界面、持久化、页面控制器 |
| `app/src/test` | 按对应功能包组织 JVM / Android 模型回归 |
| `tools`、`tools/tests`、`.github/workflows` | 生成器、契约工具及 CI / 发布，见[工具索引](docs/TOOLS.md) |

动态岛的单窗口展示、状态栏占用及新旧事件隔离由上述 owner 负责；通用 StrongToast 快照与安全层不复制。完整布局、输入、租约、媒体返回及波形合同以[功能说明](docs/features/island/README.md)为准。

波形显示与采集时钟分离：可见显示使用 View.postOnAnimation 跟随屏幕帧，不用低频定时器限制观感，也不修改全局刷新率。音乐 FFT 仍受 ROM 上限，录音峰值仍按有界间隔读取，不能跟随显示帧增加 Binder 调用；沿用可见性 / 来源租约与稳定停帧、隐藏 / detach 释放，不新增常驻线程或监听器。轻量波形仅在已附着、实际可见且播放时注册原生动画缩放通知，关闭动画后保持静止，重新开启可恢复；隐藏、暂停、切入实测或 detach 即注销，通知不建立独立系统观察器或轮询。设置中的应用行为、Wi-Fi 与 VPN 自动规则集中在“系统 → 应用与自动化”，保留原稳定键与独立功能控制器，搜索、惰性页面和真实导航同步。

除既有加载会话的持有合同外，动态岛媒体卡片因闹钟等短暂中断消失时，只有原生 manager 仍存活、系统活动会话列表再次确认当前已选会话的相同 token / 包名，才保留既有回调。真实 PLAYING 继续显示；PAUSED 仍按原两秒可见期限隐藏，仅给同一回调最多 30 秒恢复机会，重复卡片 / 元数据快照不续期，未恢复则释放。复核只在原生卡片缺口事件执行，期限复用既有 expiry；不轮询、不从旧 PLAYING 复活会话，manager 丢失、会话销毁、安全失效和 STOPPED / ERROR 仍清理。

原生返回的共享进度表示已接受返回的整体行程，网格后缘压缩仅用于网格几何；可选捕获不得推迟接收岛体的反馈。原生进度确认接入 / 完成，接收岛体宽高取同一初始状态并按已有有限时钟的已用时间推进，不能由页面速度压缩完整形变。首次小形态及已有完整岛体都从可见状态接续；完成从当前尺寸 / 速度收敛，不另建常驻动画或逐帧 Binder。

动态岛来源元数据归 IslandLiveKind，配置集合及正选 / 排除规则归不可变 IslandOptions 快照；异步读取不观察原集合后续突变。小窗身份只筛对应媒体槽，不负责全局窗口隐藏。Controller 仍分别核验通知提交、原生窗口返回和安全生命周期；媒体返回取消不得清除其他活动的内容接入进度，绘制继续共用同一有限时钟。具体入口与资源边界见[来源规则](docs/features/island/README.md#来源规则与复用)。

持续系统活动仍由同一个岛 controller 选择，每类最多一个当前状态。时钟 / 录音生产者只在明确作用域与来源开启时安装，跨进程状态先验证原生发送者再读载荷；每个生产包最多一个匿名存活令牌，死亡、替换与 detach 释放旧所有者，不轮询进程或保留活动历史。系统原生提醒与权限状态不由岛改写。

TimerService 销毁先撤销匹配的当前 owner，再发布剩余状态；应用进程仍存活不构成服务继续计时的依据。到期三秒交接与正在响铃的提醒独立保留，旧服务销毁不能撤销新 owner。Launcher 返回主 Hook 组先完整解析，任一安装失败停止并逆序撤回；可选吸入网格独立回退，不能留下只有目标覆盖或只有 owner 的半安装。

录音可附加一个仅 SystemUI UID 可读的峰值令牌，接口只返回标量，不提供命令或音频。可见后台录音由单个 owner 限速读取，所有隐藏 / 暂停 / 配置失效取消；前台录音 UI 保留其原生峰值读取。音乐与系统活动共用 Launcher 返回所有权和单窗口形变，每次原生提交独立标识，旧尝试在新打开 / 触摸 / 停用后失效。

Dock 默认背景属于 HotSeats；全宽底栏是窗口根底层独立装饰，以弱 owner 跟随其布局、可见性、原生动画与解绑，不松开祖先裁切、不移动图标、不接受触摸。关闭选项回到原背景合同，见 [Dock](docs/features/dock/README.md)。 Dock 仅保留纯色面板与磨砂玻璃，旧轻量玻璃取值归一为面板；复用宿主原生模糊，失败使用简单半透明填充。不维护壁纸捕获、折射 Shader、额外描边、模糊 / 省电监听或采样任务。

状态栏几何 Binding 跟随实际 View / Window，保留最新原生基线，仅恢复自己仍持有的属性；配置和 detach 不改写其他 owner 的新值。变更不得扩大到通用 View setter 或全局窗口缓存。

## 生产源码与开发资产

生产路径仅保留实际功能、失败隔离及有界故障诊断。安装器直接构建阶段 Catalog、注册并交由 FeatureInstallRegistry 安装，不嵌入性能采样。临时测量使用宿主工具及外部 trace；长期需要的调试实现放独立源码集，不能依赖 Release R8 消除。JVM / Robolectric 与替身归 `src/test`，开发脚本归 `tools` / `scripts`，不加入 main sources、assets 或运行依赖图。`PrefsProvider` 的 `test/*` 和五个样本资产是用户“打开方式”功能，属于生产资源。

FeatureSpec 的元数据 / 惰性工厂、FeatureDefinition 的安装行为和 Registry 的安装状态各有实际职责；不合并成预先创建所有业务对象的通用注册表。XposedHelpers 是模块内部辅助面，保留实际消费者与匹配 / 异常语义，不恢复无人使用的上游 API 全集。工程精简依据与产物证据见[原路线图](docs/maintenance/A14_REFINEMENT_ROADMAP.md#当前维护边界)。

## 设置、生成与持久化

Wi-Fi 属性租约只在读回确认后结束恢复；失败保留每属性一份 original / last 依据，未知读取暂停，不据 null 推断用户覆盖。可读值偏离 last 时撤销所有权，用户切换直接丢弃旧用户租约。禁用后只保留有待恢复的单个 runtime，网络回调与属性观察者均注销、网络集合清空；已有生命周期事件或显式设置刷新提供恢复机会，不自排重试。网络溢出维持未知，必须重新完整枚举并确认快照，不能仅因记录变少猜测身份。应用窗口功能只准备各自启用的 API，共用实际共同需要的 Activity / WM 元数据；单项准备 / 安装失败不撤回另一项成功能力，致命异常仍清理并传播。


VPN 自动开关仅在首次启用后安装系统 Hook，之后复用偏好观察者及系统 BackgroundThread。仅跟踪主用户已触发的最近任务；按 taskId / 包名 / 原生 Task 身份去重和移除，不监听进程退出，后台回收不构成任务关闭。同一目标更新保留当前会话；变更目标保留仍有效的触发任务。启动前已有、始终开启或 lockdown 连接不认领；仅认领请求后 20 秒内出现的匹配目标会话，原生 Connection / VpnRunner 身份变化即放弃。最后任务移除后按 0–30 秒（默认 3 秒）执行一次交接；延迟修改计入已等待时间，重开取消，自动关闭重新启用则重新计时。独立自动关闭开关及其最新值优先，断开前核对原生会话身份。关闭功能 / 更换目标时，一条原期限内的启动票据处理迟到连接，阻止新旧异步启动重叠；关闭自动断开则保留连接。用户切换不继承主用户所有权。最多 256 个触发应用、128 个任务 / 应用记录，溢出保守保留连接；只用一个下一次唤醒，无轮询、force-stop、always-on 改写或新线程。系统侧仅已存 IKEv2，第三方依赖系统启动或公开外部控制入口。

`app/settings/prefs_*.json` 是设置定义的唯一来源；`generatePreferenceArtifacts` 生成按需 Kotlin 页面工厂、非 UI 的紧凑搜索索引和当前偏好目录到 `app/build/generated/`。应用自有布局和菜单不保留 XML 副本；Manifest、值资源、向量、快捷方式及备份规则仍使用平台资源格式。Compose 仅属于设置进程，Dynamic Island / Dock 和宿主状态栏仍由原生 View / Canvas / ROM View 构造。

AndroidX Preference 只承载已有持久化、依赖与控制器行为，Compose 绘制分组、选择器和编辑页，不创建 Preference RecyclerView。工厂先完成模型树，再在正确 manager 根下暂绑依赖并解除初始绑定；页面 onAttached 只建立一次实际监听，detach 解除。模型观察者只递增变化行，层级或分组改变才重建可见序列。编辑草稿由对应编辑器保存，取消 / detach 不遍历 View 或提交临时值；禁用、隐藏或移除设置会关闭旧编辑器。可见列表的图标解码复用现有 LRU，在有界 IO 并发中执行，单图边长最多 256 px，不恢复旧适配器、独立常驻线程池或 XML 绑定。搜索保留控制器、目标键和导航元数据；恢复按当前偏好契约校准，生成输入与 AGP 依赖见 [DEVELOPMENT](docs/DEVELOPMENT.md#构建与生成物)。当前页面与稳定键导航见[功能地图](docs/FEATURE_MAP.md)，其独立生成不改变运行时产物。

设置行的名称、底部说明和右侧当前值各有职责：说明不被当前值或数量替换，复用时分别绑定 / 清空；没有右侧值的行不创建隐藏值控件。分隔线只能位于完整功能之间，不能拆开功能与其说明；组前说明不单独画线。分组语义在模型可见序列变化时生成，绘制不遍历偏好树。公共显示选项独立于单一内容来源，入口移动须同步普通导航、搜索、依赖与功能地图。

备份 V2 使用大小限制、严格解码与 CRC；受限旧格式解码保留既有用户兼容。维护恢复记录使用精确原状态和可恢复事务，不进入备份传输。语义约束见[遗留合同](docs/maintenance/LEGACY_SEMANTIC_CALIBRATION.md)。目录重排不能改变偏好键或稳定 Feature ID。

`MainModule.java`、`XposedHelpers.java`、`MemberUtilsX.java` 保留 Java；Kotlin Installer 的 `@JvmStatic` 保持入口 ABI，见 [Java 边界](docs/JAVA_BOUNDARY_ALLOWLIST.md)。
