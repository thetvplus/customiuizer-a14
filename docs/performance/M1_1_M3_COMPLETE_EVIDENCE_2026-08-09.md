# M1.1 与 M3 完成证据（2026-08-09）

## 范围

- Base SHA：`bc24aad6203f0cb6adfa4351d4510b70223081dc`
- M1.1 commit：`d8b6ddb5`（AudioVisualizer observer 弱 owner）
- M3 commit：`ec271051`（全分类懒加载与构建期搜索索引）
- 平台边界：HyperOS 1 / Android 14 / SDK 34 / arm64-v8a / libxposed API 101 基线

## M1.1：AudioVisualizer observer

`AudioVisualizerPreferenceObserver` 只持有一个 `WeakReference<AudioVisualizer>`，回调取得
owner 后才进入原有 preference 更新逻辑。注册仍使用
`ModuleHelper.observePreferenceChange(preferenceObserver, this)`，`dispose()` 仍通过 owner
执行 `unregisterPreferenceObserver(this)`，detach、Visualizer 释放、动画和协程取消顺序未改。

`AudioVisualizerLifecycleContractTest` 通过反射验证 observer 不含强 owner 字段，并检查注册与
注销仍绑定同一个 View owner。回调异常边界使用 `ModuleHelper.guarded`，非致命异常局部隔离，
`OutOfMemoryError`、`ThreadDeath` 与 `VirtualMachineError` 继续传播。

## M3.1：四域全分类懒加载

canonical 来源保持为：

- `prefs_system.xml`：14 个分类，完整资源 210 个 XML 元素；
- `prefs_launcher.xml`：6 个分类，完整资源 59 个 XML 元素；
- `prefs_controls.xml`：5 个分类，完整资源 45 个 XML 元素；
- `prefs_various.xml`：原结构生成“通用 + 5 个分组”，完整资源 35 个 XML 元素。

构建任务 `generatePreferenceArtifacts` 生成 31 个懒加载页与 4 个分类壳。分类页为 4–34 个
XML 元素，平均 12.2 个；最大页是系统“其他”（34），状态栏为 33。旧的手写分类壳和
状态栏副本已经删除，所有生成结果以四份 canonical XML 为唯一来源。

生成器合同验证：

- 系统、桌面和控制的 25 个原生分类逐节点保持标签、属性、顺序和子树一致；
- “其他”的 6 个生成页覆盖每一个真实 Preference 且不重复；
- 31 个页面中的每个 `android:dependency` 目标都留在同一页；
- 所有实际 preference key、默认值和持久化数据格式不变；“其他”新增 key 仅用于页面路由。

## M3.2：构建期搜索索引

运行时 `Helpers.getAllMods()` 只解析生成的 `mod_search_index.xml`，不再遍历四份功能 XML。
索引包含 303 条可搜索项、31 个路由分组和 16 个面包屑段；标题和面包屑仍以资源引用保存，
由设备当前语言解析。

生成索引为 37,567 bytes，四份 canonical XML 合计为 89,566 bytes，比例 41.9%。测试逐项
对比旧解析语义中的标题资源、key、分类、面包屑、显示序号和直达子页，并证明同一输入重复
生成时所有 36 个 XML 文件字节一致。

## 静态、测试与构建证据

- `python tools/verify.py fast --changed`：通过；
- `python -m compileall tools`：通过；
- `python -m unittest discover -s tools/tests -p "test_*.py"`：428 项通过，5 项按设计跳过；
- `python tools/verify.py full`：通过，包括 220 文件 invariant、JVM 测试和 Debug lint；
- 干净正式构建：`:app:clean :app:assembleRelease` 通过，生成任务从空 build 目录执行；
- R8、资源收缩、Release lint vital 与 v2 签名验证：通过；
- `git diff --check`：通过。

正式 APK：

- 路径：`app/build/outputs/apk/release/CustoMIUIzer-A14-r14.18.2.apk`
- 大小：3,513,502 bytes
- SHA-256：`cc644884b8822b3fad21fdc0291f594972b6c0bbf9497efa6b57d1f4e466e0ff`
- applicationId：`tv.withaibuild.customiuizer.r14`
- minSdk / targetSdk：34 / 34
- ABI：`arm64-v8a`
- 签名：v2，证书 SHA-256
  `c0eff2dc4e662717195490da78b12a984c6f2e6bd38acf4edad14d53e3d22e70`
- 内置 provenance：`revision=ec271051`、`versionName=r14.18.2`、`buildType=release`

## 实机状态

目标设备：`fuxi` / Android 14 / SDK 34，ADB serial `74b02de3`。

- `adb install -r -d`：`Success`；版本仍为 `r14.18.2` / versionCode 195；
- 设备 `/data/app/.../base.apk` SHA-256 为
  `cc644884b8822b3fad21fdc0291f594972b6c0bbf9497efa6b57d1f4e466e0ff`，与本地产物一致；
- 系统分类壳显示 14 类并成功打开“屏幕”；
- 启动器分类壳显示 6 类并成功打开“手势”；
- 控制分类壳显示 5 类并成功打开“音量键”；
- 杂项分类壳显示 6 类并成功打开“杂项/通用”；
- 中文搜索 `Toast` 成功直达系统 Toast 页；临时使用应用级 `en-US` 后，`Purify` 成功直达
  “Various / Unlock package installer features”，返回时搜索状态清除；应用语言随后恢复为空列表，
  即继续跟随系统中文；
- `AudioVisualizer` 设置页可正常打开；设备当前“启用模块”为关闭状态，本轮未改用户配置、未重启
  SystemUI，因此 M1.1 的设备行为状态为 `CONFIGURATION_DISABLED_NOT_EXERCISED`，ownership 修复
  以反射合同和 JVM 门禁为证据；
- 应用 PID 范围内未发现 `FATAL EXCEPTION`、资源/XML 解析或 `IllegalStateException`；
- 本轮导航后的单次 `gfxinfo` 快照为 214 帧、6 janky（2.80%）、P90 9 ms、P95 14 ms、
  P99 42 ms。该数据只证明本次回归可用，不作为 M3 收益 A/B。

M3 分类与搜索导航状态：`DEVICE_RUNTIME_PASS`。
