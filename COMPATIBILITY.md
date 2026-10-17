# A14 兼容矩阵

## 支持范围

| 项目 | 契约 |
| --- | --- |
| 系统 | HyperOS 1 / Android 14；运行入口要求 SDK 34 |
| APK | `tv.withaibuild.customiuizer.r14`，minSdk / targetSdk 34，`arm64-v8a` |
| API 101 | 最低运行基线，必经签名与初始化不依赖 API 102 专属类型 |
| API 102 | 能力探测后使用隔离桥接；动态作用域仍需框架授权 |
| 其他平台 | Android 13 / 15 / 16、其他 MIUI / HyperOS 大版本不支持 |

较新的 compileSdk 或构建工具不会扩展运行支持范围。跨版本移植需要独立任务和 API、ROM、生命周期、资源差异证据，不维护逐文件上游 parity。

`staticScope=true`。框架需启用模块并授予目标包作用域；四类普通应用选择（状态栏颜色、去除滚动边缘效果、媒体按键、闹钟兼容）在 API 102 请求动态作用域。API 101 需手动配置。PermissionController 同时识别 AOSP 与 Google 包名，模块不能安全识别应用自绘授权说明页。

## 证据与限制

| 证据 | 能证明的范围 |
| --- | --- |
| STATIC / SOURCE | 当前代码、配置与契约符合检查 |
| BUILD | 指定提交编译、测试、lint 或 APK 检查通过 |
| ARTIFACT | 指定哈希 ROM 样本中存在类、字段或方法签名 |
| LOG / DEVICE / RUNTIME | 指定 APK、设备、ROM 与操作下的实际路径或行为 |
| CANDIDATE | 待取证猜测，不能作为生产兼容合同 |

已有样本来自 Xiaomi 13 / fuxi、`V816.0.7.0.UMCTWXM`、Android 14。样本哈希和插件 ClassLoader 边界保存在 [ROM 资料](docs/rom-intelligence/README.md)。这些历史资料不证明当前安装 APK、其他机型或全部功能已经验收。API 102 框架的实机结果也不能当作 API 101 框架验收。

## 仍需专项取证

- 极简通知视图仍引用旧 `StatusBar.updateNotification`；历史 fuxi SystemUI 样本未提供该类。功能有 UI 与安装调用链，保留现状，不能凭样本缺失直接移植或删除。
- 网速原生样式的隐藏低速 / `B/s` 行为尚无可靠 A14 `formatSpeed` 目标；详细网速走已有独立路径。
- 锁屏超时当前改资源 `config_lockScreenDisplayTimeout`，是否还需要运行期方法取决于目标 ROM；不猜测上游方法。
- Global GuardProvider 的现有 DexKit 查询在历史目标样本中无匹配；查询通过执行不代表功能命中。

涉及通知的实机验收应检查实际频道路由、重要性持久化（`mImportance` / `mUserLockedFields`）、六项扩展菜单完整可点，以及默认频道 / Hybrid 回退。状态栏和设置页改动应覆盖重附着、锁屏、配置切换、搜索导航、列表滚动与返回重进。报告具体 APK revision、开关、ROM、操作和日志；缺失项保持未验证。
