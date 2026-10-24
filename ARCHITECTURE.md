# A14 架构

平台、优先级与公共运行规则见 [AGENTS](AGENTS.md)，产品范围见 [COMPATIBILITY](COMPATIBILITY.md)。本页维护实际路由、API 边界和状态归属。

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

进程矩阵只生成 [JSON](docs/rom-intelligence/A14_PROCESS_MATRIX.json) / [CSV](docs/rom-intelligence/A14_PROCESS_MATRIX.csv)，来源是 catalog、路由和 scope。以下非表格化边界由源码和回归维护：

- SystemUI、Settings、PermissionController、SecurityCenter 只接受各自主进程，拒绝 secondary / bootaware；fused location 与 networkstack 进程拒绝。
- `miui.systemui.plugin` 不单独加入 scope；SystemUI 从 `PluginFactory.createPlugin` 取得插件 ClassLoader，安装 / 释放由插件 runtime 持有。
- 键盘包必须在静态 scope；`ProcessRouter` 交给 InputMethodInstaller，scope 外无回调。Launcher 还可按用户选择安装 GenericApp 的 post-attach 功能。
- `StatusBarHeightFeature` 虽为 ANY，仍限资源包集合和高度开关；窗口几何归 system_server 的 Insets Feature。`AlarmCompatFeature` 另受应用名单控制。
- SystemUI bootstrap 与 Launcher 在安装派发前维护 ReflectionCache 安全生命周期；GuardProvider / Media 按需加载并关闭 DexKit，目标始终用实际加载器解析。

## API 102 安装与 Service

元数据固定 `minApiVersion=102`、`targetApiVersion=102`、`staticScope=true`。`HookInstallation` 统一原生 HookBuilder 安装、优先级与 PASSTHROUGH；`HookerClassHelper` 保持参数、提前返回、原方法异常和 after 恢复，fatal 传播。

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
| Launcher Dock Hook / 装饰 / 采样 / 材质 | HotSeats 所有权及背景；共享模糊仍在公共层 |
| `mods/utils` | 跨功能 Hook、安全、ClassLoader 缓存及生命周期机制 |
| `utils`、`prefs`、`subs` | 设置界面、持久化、页面控制器 |
| `app/src/test` | 按对应功能包组织 JVM / Android 模型回归 |
| `tools`、`tools/tests`、`.github/workflows` | 生成器、契约工具及 CI / 发布，见[工具索引](docs/TOOLS.md) |

动态岛的单窗口展示、状态栏占用及新旧事件隔离由上述 owner 负责；通用 StrongToast 快照与安全层不复制。完整布局、输入、租约、媒体返回及波形合同以[功能说明](docs/features/island/README.md)为准。

Dock 默认背景属于 HotSeats；全宽底栏是窗口根底层独立装饰，以弱 owner 跟随其布局、可见性、原生动画与解绑，不松开祖先裁切、不移动图标、不接受触摸。关闭选项回到原背景合同，见 [Dock](docs/features/dock/README.md)。

状态栏几何 Binding 跟随实际 View / Window，保留最新原生基线，仅恢复自己仍持有的属性；配置和 detach 不改写其他 owner 的新值。变更不得扩大到通用 View setter 或全局窗口缓存。

## 设置、生成与持久化

`res/xml/prefs_*.xml` 是页面源；`generatePreferenceArtifacts` 生成分页、搜索和当前偏好目录到 `app/build/generated/`。搜索保留控制器、目标键和导航元数据；恢复按当前偏好契约校准，生成输入与 AGP 依赖见 [DEVELOPMENT](docs/DEVELOPMENT.md#构建与生成物)。当前页面与稳定键导航见[功能地图](docs/FEATURE_MAP.md)，其独立生成不改变运行时产物。

备份 V2 使用大小限制、严格解码与 CRC；受限旧格式解码保留既有用户兼容。维护恢复记录使用精确原状态和可恢复事务，不进入备份传输。语义约束见[遗留合同](docs/maintenance/LEGACY_SEMANTIC_CALIBRATION.md)。目录重排不能改变偏好键或稳定 Feature ID。

`MainModule.java`、`XposedHelpers.java`、`MemberUtilsX.java` 保留 Java；Kotlin Installer 的 `@JvmStatic` 保持入口 ABI，见 [Java 边界](docs/JAVA_BOUNDARY_ALLOWLIST.md)。
