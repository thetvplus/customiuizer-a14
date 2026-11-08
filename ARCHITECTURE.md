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
- POCO 的 `com.mi.android.globallauncher` 仍是 GENERIC_APP；只有该包主进程且明确启用文件夹图标模糊或 Deck 时，才复用 Application.attach 安装对应的独立 Feature。不得把整个 Launcher catalog 扩至 POCO；MIUI 的 Launcher 路由不变。
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
| Launcher Dock Hook / 装饰 / 材质 | HotSeats 所有权及背景，见 [Dock](docs/features/dock/README.md) |
| `mods/recents` | 卡片几何、命中、窗口票据及原生运动参数，见 [Deck](docs/features/ios18-recents-reference.md) |
| `mods/utils` | 跨功能 Hook、安全、ClassLoader 缓存及生命周期机制 |
| `utils`、`prefs`、`subs` | 设置界面、持久化、页面控制器 |
| `app/src/test` | 按对应功能包组织 JVM / Android 模型回归 |
| `tools`、`tools/tests`、`.github/workflows` | 生成器、契约工具及 CI / 发布，见[工具索引](docs/TOOLS.md) |

动态岛的单窗口、来源 / 安全 / 会话租约、媒体返回、计时与采集合同只维护在[动态岛说明](docs/features/island/README.md)和[音乐手势](docs/features/island/MUSIC_GESTURES.md)。Dock 的弱 HotSeats owner、全宽装饰、原生材质及挂载期间的事件监听见 [Dock](docs/features/dock/README.md)；Duo 的几何与状态映射见 [Duo](docs/features/duo-status-bar.md)。iOS 静态参数与 Android 映射的区别见[运动参考](docs/features/ios18-motion-reference.md)。

状态栏几何 Binding 跟随实际 View / Window，保留最新原生基线，仅恢复自己仍持有的属性；配置和 detach 不改写其他 owner 的新值。变更不得扩大到通用 View setter 或全局窗口缓存。

Deck 与 iOS 音量的必需 Hook 共用 PreparedHookBatch 的完整提交门禁；准备或安装失败不得提前接管 UI，卸载失败的包装保持失活。View 接管构造失败先回滚，独立释放不得被前一项异常阻断。Deck Card 持有自建缩放动画，原生任务变化先失效冻结几何；原生 spring 的 end / cancel 为正常完成入口，开始后仍保留有限兜底期限，不接管原生 finish。

## 生产源码与开发资产

生产路径仅保留实际功能、失败隔离及有界故障诊断。安装器直接构建阶段 Catalog、注册并交由 FeatureInstallRegistry 安装，不嵌入性能采样。临时测量使用宿主工具及外部 trace；长期需要的调试实现放独立源码集，不能依赖 Release R8 消除。JVM / Robolectric 与替身归 `src/test`，开发脚本归 `tools` / `scripts`，不加入 main sources、assets 或运行依赖图。`PrefsProvider` 的 `test/*` 和五个样本资产是用户“打开方式”功能，属于生产资源。

FeatureSpec 的元数据 / 惰性工厂、FeatureDefinition 的安装行为和 Registry 的安装状态各有实际职责；不合并成预先创建所有业务对象的通用注册表。XposedHelpers 是模块内部辅助面，保留实际消费者与匹配 / 异常语义，不恢复无人使用的上游 API 全集。

## 设置、生成与持久化

Wi-Fi 属性租约只在读回确认后结束恢复；失败保留每属性一份 original / last 依据，未知读取暂停，不据 null 推断用户覆盖。可读值偏离 last 时撤销所有权，用户切换直接丢弃旧用户租约。禁用后只保留有待恢复的单个 runtime，网络回调与属性观察者均注销、网络集合清空；已有生命周期事件或显式设置刷新提供恢复机会，不自排重试。网络溢出维持未知，必须重新完整枚举并确认快照，不能仅因记录变少猜测身份。应用窗口功能只准备各自启用的 API，共用实际共同需要的 Activity / WM 元数据；单项准备 / 安装失败不撤回另一项成功能力，致命异常仍清理并传播。


VPN 自动开关仅在首次启用后安装系统 Hook，之后复用偏好观察者及系统 BackgroundThread。仅跟踪主用户已触发的最近任务；按 taskId / 包名 / 原生 Task 身份去重和移除，不监听进程退出，后台回收不构成任务关闭。同一目标更新保留当前会话；变更目标保留仍有效的触发任务。启动前已有、始终开启或 lockdown 连接不认领；仅认领请求后 20 秒内出现的匹配目标会话，原生 Connection / VpnRunner 身份变化即放弃。最后任务移除后按 0–30 秒（默认 3 秒）执行一次交接；延迟修改计入已等待时间，重开取消，自动关闭重新启用则重新计时。独立自动关闭开关及其最新值优先，断开前核对原生会话身份。关闭功能 / 更换目标时，一条原期限内的启动票据处理迟到连接，阻止新旧异步启动重叠；关闭自动断开则保留连接。用户切换不继承主用户所有权。最多 256 个触发应用、128 个任务 / 应用记录，溢出保守保留连接；只用一个下一次唤醒，无轮询、force-stop、always-on 改写或新线程。系统侧仅已存 IKEv2，第三方依赖系统启动或公开外部控制入口。

`app/settings/prefs_*.json` 是设置定义的唯一来源；`generatePreferenceArtifacts` 生成按需 Kotlin 页面工厂、非 UI 的紧凑搜索索引和当前偏好目录到 `app/build/generated/`。应用自有布局和菜单不保留 XML 副本；Manifest、值资源、向量、快捷方式及备份规则仍使用平台资源格式。Compose 仅属于设置进程，Dynamic Island / Dock 和宿主状态栏仍由原生 View / Canvas / ROM View 构造。

AndroidX Preference 只承载已有持久化、依赖与控制器行为，Compose 绘制分组、选择器和编辑页，不创建 Preference RecyclerView。工厂先完成模型树，再在正确 manager 根下暂绑依赖并解除初始绑定；页面 onAttached 只建立一次实际监听，detach 解除。模型观察者只递增变化行，层级或分组改变才重建可见序列。编辑草稿由对应编辑器保存，取消 / detach 不遍历 View 或提交临时值；禁用、隐藏或移除设置会关闭旧编辑器。可见列表的图标解码复用现有 LRU，在有界 IO 并发中执行，单图边长最多 256 px，不恢复旧适配器、独立常驻线程池或 XML 绑定。搜索保留控制器、目标键和导航元数据；恢复按当前偏好契约校准，生成输入与 AGP 依赖见 [DEVELOPMENT](docs/DEVELOPMENT.md#构建与生成物)。当前页面与稳定键导航见[功能地图](docs/FEATURE_MAP.md)，其独立生成不改变运行时产物。

设置行的名称、底部说明和右侧当前值各有职责：说明不被当前值或数量替换，复用时分别绑定 / 清空；没有右侧值的行不创建隐藏值控件。分隔线只能位于完整功能之间，不能拆开功能与其说明；组前说明不单独画线。分组语义在模型可见序列变化时生成，绘制不遍历偏好树。公共显示选项独立于单一内容来源，入口移动须同步普通导航、搜索、依赖与功能地图。

备份 V2 使用大小限制、严格解码与 CRC；受限旧格式解码保留既有用户兼容。维护恢复记录使用精确原状态和可恢复事务，不进入备份传输。语义约束见[遗留合同](docs/maintenance/LEGACY_SEMANTIC_CALIBRATION.md)。目录重排不能改变偏好键或稳定 Feature ID。

保留 Java 的文件及 Kotlin Installer 的 `@JvmStatic` 入口清单统一见 [Java 边界](docs/JAVA_BOUNDARY_ALLOWLIST.md)，不在本页维护副本。
