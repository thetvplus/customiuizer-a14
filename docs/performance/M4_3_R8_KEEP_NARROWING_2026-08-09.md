# M4.3 R8 keep 收窄证据

## 结论

本任务删除了 `tv.withaibuild.customiuizer.mods.**` 下所有公共静态方法与公共字段的
全局 `-keepclassmembers`。该规则没有对应的模块自身动态类名入口，并直接把大量 Kotlin
`object`、`Companion`、闭包捕获字段和静态桥接方法列为 seed。

Xposed 模块入口、Hooker 回调、Android Manifest 组件和 `META-INF/xposed` 资源规则均保持
不变。Develop/R8 A/B 中 APK 缩小 163,840 bytes，DEX 缩小 173,924 bytes；没有修改 Hook
目标、安装顺序、Feature ID、preference 或 API 101/102 路径。

## 范围

- Base commit：`e6083bef6a71af36fb4a626ce48e546be179e1bf`
- 变更规则：仅删除 `mods.**` 的公共静态方法/公共字段全局保留；
- 保留规则：
  - `XposedModule` 子类及公开构造函数；
  - `XposedInterface.Hooker` 实现类及回调成员；
  - Manifest 中设置应用组件的名称；
  - `META-INF/xposed/java_init.list` 内容适配；
- 未改：Hooker 规则本身、Manifest、Xposed scope、运行时代码和设置数据。

生产源码的动态加载调用使用 ROM/目标进程类名；审计未发现把模块自身
`tv.withaibuild.customiuizer.mods.*` 类名传给 `Class.forName`、`loadClass`、
`findClass`、`findClassIfExists` 或 DexKit 的入口。普通 Java/Kotlin 直接引用继续由 R8
可达性图处理，不需要成员全保留。

## why-kept 证据

使用临时 `-whyareyoukeeping` 查询构建 Develop/R8，记录原因后删除查询：

| 查询 | R8 结果 |
|---|---|
| `MainModule` | 由 `XposedModule` 专用规则保留 |
| `SystemUIControlCenterHooks` | 由真实 Hooker 回调引用，Hooker 由专用规则保留 |
| `SystemClockHooks$StatusBarClockTweakHook$3` | 直接由 Hooker 专用规则保留 |
| `SystemUiFeatures` | `Nothing is keeping`，允许 R8 在内联后删除容器 |

这证明宽泛规则不是 Xposed 入口合同；真正的反射边界已经由更窄的规则覆盖。

## Develop/R8 A/B

两组使用同一源码、同一 JDK 25、同一 Develop 变体。候选组只删除目标 keep 规则。

| 指标 | 原规则 | 候选 | 差值 |
|---|---:|---:|---:|
| APK bytes | 3,509,491 | 3,345,651 | -163,840（-4.67%） |
| `classes.dex` bytes | 1,895,824 | 1,721,900 | -173,924（-9.17%） |
| 全部 seed 行 | 5,252 | 3,354 | -1,898 |
| `mods.*` seed 行 | 3,806 | 1,909 | -1,897 |
| `mods.*` 方法 seed | 2,247 | 1,110 | -1,137 |
| `mods.*` 字段 seed | 1,061 | 301 | -760 |

候选 APK 的 `java_init.list` 为 R8 名称 `w30`；mapping 将 `MainModule` 映射到同一名称，
且该名称的 DEX descriptor 确实存在。APK provenance 为工程 revision `e6083bef`、build type
`develop`、version `r14.18.2`、versionCode `195`。

## 合同和测试

- 新增 R8 合同测试，禁止重新引入 `mods.**` 全局成员保留，同时锁定 XposedModule 与
  Hooker 专用规则；
- 原测试先因宽泛规则存在而失败，删除规则后通过；
- brutal 变异从“删除宽泛规则”改为“重新引入宽泛规则”，避免测试基线绑定旧结构；
- Xposed/Hooker 边界没有允许优化、合并或改名方面的新放宽。

## 实机状态和剩余风险

初始记录本证据时 `adb devices -l` 没有在线设备，因此当时只声明
`STATIC_R8_PASS / DEVICE_CHECKPOINT_BLOCKED_NO_DEVICE`。后续已在 fuxi / Android 14 设备上
安装并核对签名 Develop revision `5d4878fe`，完成以下收口：

1. Launcher PID `5790 -> 32297`，`onPackageReady installed=4`、`post-attach installed=12`；
2. SystemUI PID `5756 -> 808`，`onPackageReady installed=51`、`post-init installed=52`；
3. system_server PID `2875 -> 2899`，`onSystemServerStarting installed=43`；
4. 上述 HookSummary 的缺类、缺成员、安装失败、DexKit 与 preference 错误均为 `0`；
5. 控制中心可展开并实际渲染，桌面基础滑动可用，烟测前后 SystemUI 与 Launcher PID 不变；
6. Settings 进程以同一 revision 加载，`onPackageReady installed=8` 且错误计数为 `0`。

因此 M4.3 最终状态更新为 `STATIC_R8_PASS / DEVICE_RUNTIME_PASS`。

Hooker 的全类 keep 仍然较宽，但它同时承担 libxposed 回调 ABI 和类合并隔离；没有新的
独立运行时证据前不继续收窄。Manifest 组件 `-keepnames` 也不并入本原子任务。
