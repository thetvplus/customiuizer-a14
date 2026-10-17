# A14 架构

## 入口与路由

```text
libxposed → MainModule
  → PreferenceBootstrap：远程偏好 → 进程内快照
  → ProcessRouter：包名 / 进程名 → ProcessScope
  → Installer → FeatureSpec / LazyFeatureSpec
  → FeatureInstallRegistry → ROM resolver → Hook / Controller
```

`MainModule.onModuleLoaded` 初始化 API 能力和偏好来源，记录版本、versionCode 与 Git revision。`onSystemServerStarting` 和首个包的 `onPackageReady` 分别安装对应入口；SDK 不等于 34 时不进入业务安装路径。

Installer 按进程与开关选择 Feature。`FeatureInstallRegistry` 使用进程内稳定 Feature ID 和安装状态，偏好更新不会重置已安装 Hook。首次偏好未就绪时，system_server 延后决定受偏好控制的功能；首个已加载快照补装未安装项。SystemUI 的 Context 初始化由 `SystemUiBootstrapCoordinator` 协调，重启时间只作诊断，不跳过整个目录。

基础初始化、资源准备和动作通信入口可常驻；可选业务 Hook、Receiver、Observer 与任务由对应开关控制。安装时读取的开关需要重启目标进程，支持运行期更新的功能通过快照或所有者注册更新状态，不能把所有设置都解释为即时生效。

## ROM 与 API 边界

- API 101 是最低基线；`Api102HookBridge` 与 `Api102ScopeRequester` 隔离可选 API 102 类型和调用。
- 宿主 SystemUI 与动态插件使用各自的 ClassLoader；安装时解析 ROM 类、方法、资源及签名，缺失时停止对应功能或保留原生行为。
- 灵动额头使用 ROM StrongToast 原地调整，加上 `DynamicIslandStatusBarFade`，没有独立模块窗口 Host。
- 反射缓存按 ClassLoader 隔离且有界；成熟 Hook 基础设施的修改需要目标 ROM 证据。

## 设置与持久化

`res/xml/prefs_*.xml` 是设置源。构建任务 `generatePreferenceArtifacts` 生成分页面资源、搜索索引和当前偏好目录，输出在 `app/build/generated/`，不要直接编辑或提交生成物。搜索保留页面控制器、目标键与导航元数据；设置恢复由当前偏好契约校准。

备份使用有大小限制、严格解码及 CRC 校验的 V2 格式。受限旧格式解码器用于既有用户数据兼容，不能按“旧代码”直接删除。具体语义约束见 [遗留功能契约](docs/maintenance/LEGACY_SEMANTIC_CALIBRATION.md)。

## 生命周期与热路径

注册绑定进程或实例所有者，并提供替换、失效与释放路径。短生命周期 Activity、View 和控制器不能由静态字段强持有。Hook 参数改写、时序、返回值和 `chain.proceed()` 次数属于行为契约；普通异常局部隔离，致命 JVM 错误继续抛出。

热路径只读已准备好的状态，新增逻辑避免重复反射、DexKit、磁盘 I/O、同步 Binder、临时集合和日志洪泛。静态预算用于防止回归，不等于全项目实际分配量或性能验收，见 [优化门禁](docs/OPTIMIZATION_GATES.md)。

`MainModule.java`、`XposedHelpers.java`、`MemberUtilsX.java` 保留 Java，Installer 的 Kotlin `object` 通过 `@JvmStatic` 保持入口 ABI，详见 [Java 边界清单](docs/JAVA_BOUNDARY_ALLOWLIST.md)。
