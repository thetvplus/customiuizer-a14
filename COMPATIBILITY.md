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

- Dock 背景默认关闭，设置位于桌面的 Dock 页，旧有高度与上下边距键保持兼容。两份 fuxi 样本的
  `MiuiHome.apk` 相同（`RELEASE-4.39.30.8604-08262248`）；安装点为 `HotSeats.updateContent()`
  与 `onWallpaperColorChanged()`，装饰由 Hotseat 持有，跟随其原生动画和可见性。
  自动材质复用宿主 `BackdropBlurRelativeLayout` 的模糊、混色及生命周期；系统模糊仅接受圆角矩形，
  此时保留原生圆角。轻透材质及缺少原生组件时使用 G4 曲率模型的缓存近似路径。
  设置更改需要重启桌面；动画、文件夹、最近任务、编辑模式、深色模式和动态壁纸仍须实机验收。

- 已下线缺少有效 A14 安装路径的浮动通知不自动隐藏、自动隐藏延迟、简约折叠视图、桌面去除启动动画及通知中心快捷应用；保留日历 / 时钟替换和其他通知功能。六个已隐藏入口也从配置目录移除，清理范围和升级规则见 [功能退役说明](docs/maintenance/A14_FEATURE_RETIREMENTS.md)。
- “修复动画”保留 `SpringAnimator.getSpringForce(FFF)` 路径，移除依赖旧 RectFSpringAnim 字段的分支；不猜测新动画 Bundle 的语义。
- 网速隐藏低速、低速阈值和隐藏 `B/s` 仅用于详细样式 2 / 3。原生样式下设置控件禁用，原值保留；改回详细样式后恢复。切换样式涉及 Hook 的安装 / 退出时仍需重启 SystemUI；已安装详细 Hook 的格式子值通过观察者更新。刷新间隔、文字样式等独立能力保留。
- 国际版 ROM 隐藏“扫描时禁止应用远程检测”，即使旧备份启用也不安装该 Feature。2026-10-11 对 fuxi / `V816.0.7.0.UMCTWXM` 的 Global GuardProvider APK（SHA-256 `1b767096d88e61b51e1f5db928eb963071ba851f47da0f20965ce262e31edeb6`）重跑生产 DexKit 查询无命中。该结果不证明国内版目标不存在；国内版实现保留，仍需查询命中与行为验证。

## 仍需专项取证

- 锁屏超时当前改资源 `config_lockScreenDisplayTimeout`，是否还需要运行期方法取决于目标 ROM；不猜测上游方法。
- 单个 fuxi 样本的缺失目标不代表全部 HyperOS 1 机型均移除同一系统能力；当前下线属于 A14 产品范围取舍，恢复需要新的完整安装链和实机证据。

涉及通知的实机验收应检查实际频道路由、重要性持久化（`mImportance` / `mUserLockedFields`）、六项扩展菜单完整可点，以及默认频道 / Hybrid 回退。状态栏和设置页改动应覆盖重附着、锁屏、配置切换、搜索导航、列表滚动与返回重进。报告具体 APK revision、开关、ROM、操作和日志；缺失项保持未验证。
