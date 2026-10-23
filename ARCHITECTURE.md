# A14 架构

## 产品方向与取舍

米客 A14 是原生融合、响应迅速、低额外开销且动画可靠的 HyperOS 1 增强工具。范围保持 Android 14 / SDK 34、arm64-v8a 和 libxposed API 102；不以增加功能数量为目标。原生能力拥有行为和生命周期，模块只改变用户明确选择的部分。

| 级别 | 当前范围 | 长期投入 |
| --- | --- | --- |
| CORE | 通知交互、动态岛、Dock、状态栏与手势 | 精修触摸准确性、状态切换、动画、原生恢复与资源释放；保留既有视觉合同 |
| MAINTAIN | 声音 / 振动、Toast、控制中心辅助项、设置 / 搜索 / 备份、有限维护工具 | 稳定与安全优先；安装条件明确，恢复记录可靠，不扩张系统停用范围 |
| RETIRE_CANDIDATE | 有目标 ROM 失效证据、重复原生能力或所有权成本无法闭合的具体实现 | 逐项登记于[现有退役页](docs/maintenance/A14_FEATURE_RETIREMENTS.md)，先停无效运行再评估删除；静态引用计数不构成删除依据 |

关闭功能不安装业务 Hook、监听或任务；启用后的状态与资源须有所有者、容量、失效和释放路径。禁止以降低动画质量宣称优化，不凭静态扫描宣称功耗或帧率收益。新能力、依赖和抽象须对应实际用途及可回退收益，避免为形式统一搬迁文件。源码、Host JVM、ROM 静态证据、最终 APK 和真机测量分别验收。

工作顺序和设备证据继续归[精修路线图](docs/maintenance/A14_REFINEMENT_ROADMAP.md)，发布门禁归[发布文档](docs/RELEASE.md)，不建立平行管理体系。

## 入口与路由

| 层 | 主要职责 |
| --- | --- |
| libxposed / MainModule | 进程生命周期、偏好来源及模块入口 |
| PreferenceBootstrap / ProcessRouter | 发布进程快照，按包名和进程选择 scope |
| Installer / FeatureSpec | 按进程和偏好选择功能 |
| FeatureInstallRegistry | 稳定 Feature ID、单次安装及所有权 |
| ROM resolver / Hook / Controller | 冷路径解析、宿主调用与可释放的运行状态 |


`MainModule.onModuleLoaded` 初始化偏好来源，记录版本、versionCode 与 Git revision。`onSystemServerStarting` 和首个包的 `onPackageReady` 分别安装对应入口；SDK 不等于 34 时不进入业务安装路径。

Installer 按进程与开关选择 Feature。`FeatureInstallRegistry` 使用进程内稳定 Feature ID 和安装状态，偏好更新不会重置已安装 Hook。首次偏好未就绪时，system_server 延后决定受偏好控制的功能；首个已加载快照补装未安装项。SystemUI 的 Context 初始化由 `SystemUiBootstrapCoordinator` 协调，重启时间只作诊断，不跳过整个目录。

基础初始化、资源准备和动作通信入口可常驻；可选业务 Hook、Receiver、Observer 与任务由对应开关控制。安装时读取的开关需要重启目标进程，支持运行期更新的功能通过快照或所有者注册更新状态，不能把所有设置都解释为即时生效。

## ROM 与 API 边界

- API 102 是最低基线；`HookInstallation` 统一原生安装策略，`XposedServiceManager` 管理 Service 与动态作用域。保留设置进程与 compileOnly Hook API 的类加载边界。
- 宿主 SystemUI 与动态插件使用各自的 ClassLoader；安装时解析 ROM 类、方法、资源及签名，缺失时停止对应功能或保留原生行为。
- 系统默认与匹配高度模式由 ROM StrongToast 管理原生窗口；动态岛在其统一入口抑制原生额头，使用 `mods/island` 内单一 Controller / View；同一窗口的有界输入容器只负责实际 touchableRegion，不拥有媒体状态或新增窗口。短时活动、媒体、系统终态与通知共享展示门控和身份租约，避免双窗口、旧回调复活与隐私泄漏。
- 动态岛尺寸、运动、遮挡、暂停退场及输入规则统一见 [当前行为](docs/features/island/README.md)。当前只有短岛 / 长岛；音乐左右切歌开关与五手势独立配置已实现，具体行为见 [音乐交互](docs/features/island/MUSIC_GESTURES.md)。主线程 owner、session epoch、generation、容量一状态和有界图像归对应控制器，重建、解绑、切用户和关闭均释放。实验性实时波形复用共享 FFT 数学，只在目标会话可确认且可见播放时按需持有一个 Visualizer；默认轻量动画。
- 公共 StrongToast 快照、通用 Hook / 偏好 / 安全边界继续在共享层；动态岛功能包通过明确导入调用，不复制这些状态或另建轮询服务。
- Dock 默认背景继续归属原生 HotSeats；「全宽底栏」独立装饰挂在窗口根布局底层，以弱引用跟随 HotSeats 的布局、可见性、原生动画透明度和解绑。它不松开原生祖先裁切、不移动图标、不接受触摸；关闭该选项即可回到原有背景合同。
- 反射缓存按 ClassLoader 隔离且有界；成熟 Hook 基础设施的修改按 [AGENTS.md](AGENTS.md) 提供相应契约与回归证据，ROM 行为另需目标样本与实机验证。

## API 102 安装与 Service

`minApiVersion=102`、`targetApiVersion=102`、`staticScope=true`。`HookInstallation` 直接调用原生 HookBuilder，统一方法、构造器及基础设施 ID 安装；优先级和 `PASSTHROUGH` 模式保持一致。`HookerClassHelper` 继续承担参数改写、提前返回、原方法异常及 after 恢复，致命错误必须传播。

仅为三个既有基础设施安装点设置固定 ID：

| 安装点 | ID |
| --- | --- |
| SystemUIInitializer.init(boolean) | `systemui.init` |
| PluginFactory.createPlugin 的所有声明重载 | `systemui.plugin.create` |
| 已选择目标进程的 Application.attach(Context) | `application.attach` |

ID 由模块和 executable 隔离，同 ID 可用于不同重载，不能代替一次安装状态和所有者管理。设置 ID 失败时停止该次安装；`intercept` 永不重试。安装后回读 ID，相等才记录 `<id> (confirmed)`；普通回读失败只记诊断并保留 handle，致命错误传播。

不启用自动热重载、显式 replaceHook 或 detach；这些能力需要独立的旧代任务、JNI、Receiver / Observer、系统持有对象和 ClassLoader 交接设计。API 契约可查 [官方源码](https://repo.maven.apache.org/maven2/io/github/libxposed/api/102.0.0/api-102.0.0-sources.jar)。

`XposedServiceManager` 每次选择只读一次 scope Binder 快照，仅请求尚未授权的包；批准由框架决定。读取或请求的普通异常返回不可用，不自动重试，致命错误传播。异步失败提示只持有 Application Context。Service Binder 交付入口受模块签名权限保护。偏好镜像核对真实提交结果，保留每个连接的一次重试预算。

R8 保留 Hooker、入口、JNI 与设置进程的 compileOnly 类加载边界。最终 APK 核查 setId/getId、禁用热重载与替换、ServiceLoader 和入口；[DexSQL](docs/rom-intelligence/DEXSQL.md) 产物检查不能代替框架安装日志与设备验收。

## 源码与文档布局

| 位置 | 维护内容 |
| --- | --- |
| `MainModule`、`installers` | 入口与进程路由 |
| `mods/island` | 动态岛 ROM 接入、Controller、View、状态、运动和专用手势 |
| `mods/duostatusbar`、`clock`、`battery` 等 | 已拆分的独立功能单元 |
| `mods/utils`、`mods/utils/feature` | 跨功能的 Hook、安全边界、生命周期与 Feature 目录 |
| `utils`、`prefs`、`subs` | 设置进程、持久化与界面控制器 |
| `app/src/test/.../mods/island` 等 | 与功能包对应的 JVM / Android 模型回归 |
| [docs/features](docs/features/README.md) | 当前功能说明及明确标注的后续方案 |
| [docs/rom-intelligence](docs/rom-intelligence/README.md) | 生成矩阵、样本与 ROM 取证 |
| [docs/maintenance](docs/maintenance/A14_REFINEMENT_ROADMAP.md) | 精修状态、退役证据与交接 |
| `.github/workflows`、`tools` | 固定身份的 CI / 发布入口与可复用工具，见 [工具索引](docs/TOOLS.md) |

目录按所有权和调用关系拆分。Dock 仍由 Launcher Hook、装饰、采样、材质等协作，现有公共模糊状态由共享层维护；不把跨功能基础设施归入单个功能。路径调整须同步 import、测试、语义目录和生成矩阵，不能改变偏好键或稳定 Feature ID。

## 设置与持久化

`res/xml/prefs_*.xml` 是设置源。构建任务 `generatePreferenceArtifacts` 生成分页面资源、搜索索引和当前偏好目录，输出在 `app/build/generated/`，不要直接编辑或提交生成物。搜索保留页面控制器、目标键与导航元数据；设置恢复由当前偏好契约校准。

备份使用有大小限制、严格解码及 CRC 校验的 V2 格式。受限旧格式解码器用于既有用户数据兼容，不能按“旧代码”直接删除。具体语义约束见 [遗留功能契约](docs/maintenance/LEGACY_SEMANTIC_CALIBRATION.md)。

## 生命周期与热路径

注册绑定进程或实例所有者，并提供替换、失效与释放路径。短生命周期 Activity、View 和控制器不能由静态字段强持有。Hook 参数改写、时序、返回值和 `chain.proceed()` 次数属于行为契约；普通异常局部隔离，致命 JVM 错误继续抛出。

热路径只读已准备好的状态，新增逻辑避免重复反射、DexKit、磁盘 I/O、同步 Binder、临时集合和日志洪泛。静态预算用于防止回归，不等于全项目实际分配量或性能验收，见 [优化门禁](docs/OPTIMIZATION_GATES.md)。

配置可采用不可变快照、原子状态或所有者绑定的定向监听；能安全运行期更新的参数不重新安装 Hook，安装条件变化仍遵循原有重启语义。有界缓存、事件合并、局部重构与成熟依赖按根规则评估净收益，不以最少代码或绝对最低内存作为架构目标。

`MainModule.java`、`XposedHelpers.java`、`MemberUtilsX.java` 保留 Java，Installer 的 Kotlin `object` 通过 `@JvmStatic` 保持入口 ABI，详见 [Java 边界清单](docs/JAVA_BOUNDARY_ALLOWLIST.md)。
