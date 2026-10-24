# A14 兼容矩阵

## 支持范围

| 项目 | 契约 |
| --- | --- |
| 系统 | HyperOS 1 / Android 14；运行入口要求 SDK 34 |
| APK | `tv.withaibuild.customiuizer.r14`，minSdk / targetSdk 34，`arm64-v8a` |
| Xposed | libxposed API 102 最低基线；API 101 不支持 |
| 其他平台 | Android 13 / 15 / 16、其他 MIUI / HyperOS 大版本不支持 |

较新的 compileSdk 或构建工具不会扩展运行支持范围。跨版本移植需要独立任务和 API、ROM、生命周期、资源差异证据，不维护逐文件上游 parity。

工程现代化遵循 [AGENTS.md](AGENTS.md)：允许有证据的局部技术升级和有界资源交换，以上产品范围、API / ClassLoader / R8、旧设置与备份兼容不随之放宽。语言语法、JVM 输出、D8/R8 转换和 Android 14 ART 可用 API 必须分别验证。

`staticScope=true`。框架需启用模块并授予目标包作用域；四类普通应用选择（状态栏颜色、去除滚动边缘效果、媒体按键、闹钟兼容）通过原生 Service 请求动态作用域，仍需框架授权。PermissionController 同时识别 AOSP 与 Google 包名，模块不能安全识别应用自绘授权说明页。

r14.23.3 起最低要求 API 102，直接使用原生 Hook 安装和 Service 能力；不维护 API 101 兼容路径。固定 ID、回调语义、类加载与生命周期边界见 [架构说明](ARCHITECTURE.md#api-102-安装与-service)。

## 证据与限制

| 证据 | 能证明的范围 |
| --- | --- |
| STATIC / SOURCE | 当前代码、配置与契约符合检查 |
| BUILD | 指定提交编译、测试、lint 或 APK 检查通过 |
| ARTIFACT | 指定哈希 ROM 样本中存在类、字段或方法签名 |
| LOG / DEVICE / RUNTIME | 指定 APK、设备、ROM 与操作下的实际路径或行为 |
| CANDIDATE | 待取证猜测，不能作为生产兼容合同 |

已有样本来自 Xiaomi 13 / fuxi、`V816.0.7.0.UMCTWXM`、Android 14。样本哈希和插件 ClassLoader 边界保存在 [ROM 资料](docs/rom-intelligence/README.md)。这些历史资料不证明当前安装 APK、其他机型或全部功能已经验收。其他框架实现仍需专项验收。

2026-10-18 新增两份 fuxi / SDK 34 离线样本：国行 `V816.0.19.0.UMCCNXM` 和台湾版
`V816.0.8.0.UMCTWXM`。SystemUI 初始化、插件创建、音量按钮、截图保存及截图相关视图生命周期的
14 个已选目标签名一致；插件 APK 与 `miui-services.jar` 的文件哈希相同。截图 APK 版本分别为
`RELEASE-1.4.113-06271450` 和 `RELEASE-1.4.122-10151355`，两者均使用
`miui.intent.TAKE_SCREENSHOT` / `IsFinished` 开始与结束广播。GuardProvider 的生产双字符串条件
在国行命中唯一 `List` 返回方法，在台湾版零命中，保留国际版禁用策略。两包同时存在地区与版本差异，
这些结果仅约束指定样本和已查目标，不代表整包或实机功能验收；原件、哈希、查询及 Smali 留在本地证据中。

## 当前功能边界

- Dock 的宿主安装点、两区相同 Launcher 样本与材质限制统一见 [Dock 说明](docs/features/dock/README.md)。这些静态结果不等于动画、编辑或动态壁纸已通过设备验收。

- 已批准的功能减法、旧偏好清理及升级兼容统一见[退役说明](docs/maintenance/A14_FEATURE_RETIREMENTS.md)；不能从单样本目标缺失外推全部 ROM 的系统能力。
- “修复动画”仅保留有证据的 SpringAnimator 路径，具体取舍见[遗留语义合同](docs/maintenance/LEGACY_SEMANTIC_CALIBRATION.md#需保留的行为)。
- 详细网速样式与原生样式的可编辑范围、存储保留及重启语义见[遗留语义合同](docs/maintenance/LEGACY_SEMANTIC_CALIBRATION.md#需保留的行为)。
- 国际版隐藏远程检测拦截且不安装该 Feature；国内版保留。旧 Global GuardProvider 查询与样本哈希见[退役说明](docs/maintenance/A14_FEATURE_RETIREMENTS.md#局部裁剪与-rom-条件)，CN / TW 新样本边界见上文；零命中不能证明其他地区目标不存在。

## 仍需专项取证

- 锁屏超时当前改资源 `config_lockScreenDisplayTimeout`，是否还需要运行期方法取决于目标 ROM；不猜测上游方法。
- 单个 fuxi 样本的缺失目标不代表全部 HyperOS 1 机型均移除同一系统能力；当前下线属于 A14 产品范围取舍，恢复需要新的完整安装链和实机证据。

涉及通知的实机验收应检查实际频道路由、重要性持久化（`mImportance` / `mUserLockedFields`）、六项扩展菜单完整可点，以及默认频道 / Hybrid 回退。状态栏和设置页改动应覆盖重附着、锁屏、配置切换、搜索导航、列表滚动与返回重进。报告具体 APK revision、开关、ROM、操作和日志；缺失项保持未验证。
