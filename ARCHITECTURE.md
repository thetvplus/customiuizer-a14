# A14 架构

## 入口与路由

```text
libxposed → MainModule
  → PreferenceBootstrap：远程偏好 → 进程内快照
  → ProcessRouter：包名 / 进程名 → ProcessScope
  → Installer → FeatureSpec / LazyFeatureSpec
  → FeatureInstallRegistry → ROM resolver → Hook / Controller
```

`MainModule.onModuleLoaded` 初始化偏好来源，记录版本、versionCode 与 Git revision。`onSystemServerStarting` 和首个包的 `onPackageReady` 分别安装对应入口；SDK 不等于 34 时不进入业务安装路径。

Installer 按进程与开关选择 Feature。`FeatureInstallRegistry` 使用进程内稳定 Feature ID 和安装状态，偏好更新不会重置已安装 Hook。首次偏好未就绪时，system_server 延后决定受偏好控制的功能；首个已加载快照补装未安装项。SystemUI 的 Context 初始化由 `SystemUiBootstrapCoordinator` 协调，重启时间只作诊断，不跳过整个目录。

基础初始化、资源准备和动作通信入口可常驻；可选业务 Hook、Receiver、Observer 与任务由对应开关控制。安装时读取的开关需要重启目标进程，支持运行期更新的功能通过快照或所有者注册更新状态，不能把所有设置都解释为即时生效。

## ROM 与 API 边界

- API 102 是最低基线；`HookInstallation` 统一原生安装策略，`XposedServiceManager` 管理 Service 与动态作用域。保留设置进程与 compileOnly Hook API 的类加载边界。
- 宿主 SystemUI 与动态插件使用各自的 ClassLoader；安装时解析 ROM 类、方法、资源及签名，缺失时停止对应功能或保留原生行为。
- 系统默认与匹配高度模式继续由 ROM StrongToast 管理原生窗口；动态岛模式在统一显示入口结束原生额头请求，全部活动使用 `IslandActivityController` 的一个按需模块窗口和 `IslandActivityView`。`IslandActivityState` 只有一个短时槽与一个原生选定的媒体槽；身份租约、generation 与过期时间防止旧回调关闭新活动。主线程容量一的会话隐藏身份可跨状态栏 View 重建保留，不持有 View / Activity / MediaController；新会话、销毁、用户切换或显式重启接入使其失效。封面与头像分别限定为一张 128 px 图像，媒体读取与通知头像读取分别串行；关闭和重建取消工作并释放窗口、监听与展示状态，不修改原生 Keyguard 状态。
- 只有 SHORT / 短岛与 LONG / 长岛，没有展开面板。两态共用 `IslandSizing`、摄像头安全区、38–44 dp 高度及胶囊端帽，长度改变中段；自动 / 手动宽度均受实际安全空间约束。模块动画共用一个有限 ValueAnimator，物理尺寸及内容比例补偿不逐帧修改窗口或执行 Binder；“上下”沿顶部进出，“中心”固定高度及纵向位置，只横向伸缩和淡入淡出。Surface 四边保留 80 dp 有界绘制外扩，逻辑触摸框不扩大。
- `IslandWindowPresentation` 在有效布局、attach、窗口可见、非透明及安全状态成立后才取得展示占用；`DynamicIslandStatusBarFade` 只隐藏与实际动画矩阵 / 屏幕边界相交的单个图标，不把未知布局退化为整条淡出。记录最多 128 个当前叶图标，解绑、取消和失效成对恢复。原生系统提示不再拥有第二套动态岛布局、动画或避让状态。
- 媒体播放等待 750 ms。已经显示的音乐岛暂停后总退场约 2 秒；播放器前台、待入场或其他提示占用窗口时的暂停，不创建新的暂停岛，重复快照不续时。原始回调先在 Main 记录暂停 / 恢复边沿，再合并 IO 快照；旧代回调以 session epoch 失效。左右切歌由默认开启的 `system_island_media_swipe` 控制，关闭会取消待执行 transport 操作；上滑隐藏和单击打开不受影响。通知中心临时遮挡保留原期限与真实边沿，锁屏等隐私遮挡释放会话。应用通知采用标准消息 / 图片内容契约，不扫描内部数据库；详情与头像归有期限的单槽持有。
- 前台包名从 ROM `MiuiTopActivityObserver` 的现有更新 / 通知方法和构造后读取已缓存字段，只保留包名与反射元数据，不持有 observer、不增加轮询或 Binder 查询；媒体及通知来源处于前台时隐藏对应活动，即时操作反馈除外。系统开关反馈由状态栏 owner 持有的 `IslandSystemActivities` 监听精确 Settings URI、受保护广播与背面闪光灯回调，先种基线再报告真实终态；移动数据默认 SIM observer 仅保留一个。`IslandSystemFeedback` 只有一个最新待显示槽，收集窗口 200 ms，从原事件起最多 6000 ms；控制中心展开时不绘制，收起后只展示仍有效的最新项，过期、锁屏、切用户及销毁丢弃。IO 串行、主线程提交、重建 / 用户切换 / 关闭均失效释放。
- 状态栏非必要分支采用系统 `ValueAnimator` 过渡；模糊仅作用于模块自有 `ViewOverlay`，不覆盖原生 `RenderEffect`。每次硬件过渡临时图像上限 1 MiB、六级效果，无逐帧截图；不支持或模块模糊关闭时使用 alpha，简化 / 系统动画关闭时即时完成。正常退出保留弱 helper 以支持快速反向，所有动画、图像与监听在完成 / 解绑 / 替换后释放。
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

## 设置与持久化

`res/xml/prefs_*.xml` 是设置源。构建任务 `generatePreferenceArtifacts` 生成分页面资源、搜索索引和当前偏好目录，输出在 `app/build/generated/`，不要直接编辑或提交生成物。搜索保留页面控制器、目标键与导航元数据；设置恢复由当前偏好契约校准。

备份使用有大小限制、严格解码及 CRC 校验的 V2 格式。受限旧格式解码器用于既有用户数据兼容，不能按“旧代码”直接删除。具体语义约束见 [遗留功能契约](docs/maintenance/LEGACY_SEMANTIC_CALIBRATION.md)。

## 生命周期与热路径

注册绑定进程或实例所有者，并提供替换、失效与释放路径。短生命周期 Activity、View 和控制器不能由静态字段强持有。Hook 参数改写、时序、返回值和 `chain.proceed()` 次数属于行为契约；普通异常局部隔离，致命 JVM 错误继续抛出。

热路径只读已准备好的状态，新增逻辑避免重复反射、DexKit、磁盘 I/O、同步 Binder、临时集合和日志洪泛。静态预算用于防止回归，不等于全项目实际分配量或性能验收，见 [优化门禁](docs/OPTIMIZATION_GATES.md)。

配置可采用不可变快照、原子状态或所有者绑定的定向监听；能安全运行期更新的参数不重新安装 Hook，安装条件变化仍遵循原有重启语义。有界缓存、事件合并、局部重构与成熟依赖按根规则评估净收益，不以最少代码或绝对最低内存作为架构目标。

`MainModule.java`、`XposedHelpers.java`、`MemberUtilsX.java` 保留 Java，Installer 的 Kotlin `object` 通过 `@JvmStatic` 保持入口 ABI，详见 [Java 边界清单](docs/JAVA_BOUNDARY_ALLOWLIST.md)。
