# 2026-09-06 通知功能 Review 与维护

## 范围与基线

- 输入：用户提供的 `评估Review.txt`，并明确授权评估后建立新分支实施安全更新与性能优化。
- 基线：`7fe35b0dd94c41bbf0f91b9f0be4662f79c7fa70`，远端 `main`，r14.21.2 / versionCode 209。
- 工作分支：`codex/review-notification-safety-20260906`。
- 实现提交：`80bc42cd611d4f0252a0cccaf306605c7a774894`。
- 实机：小米 13 / fuxi，SDK 34，HyperOS OS1.0，`V816.0.7.0.UMCTWXM`，root ADB。
- ROM 指纹：`Xiaomi/fuxi_global/fuxi:14/UKQ1.230804.001/V816.0.7.0.UMCTWXM:user/release-keys`。
- 实机原 APK provenance：`buildType=release`、`revision=7fe35b0d`、`versionName=r14.21.2`、`versionCode=209`。

本次不改变正式版本号、偏好键、菜单分组、Feature/PrefMap/ResourceHooks 基础设施或 API 101/102 边界。开发验证包与正式发布分别记录。未授权推送 main、合并主线、打标签或正式发布。

## 完成度（2026-09-06 续）

CodeX/Devin 完成了实现提交和改前实机对照，没有收口。本续做静态门禁，不改生产 Hook。

| 项 | 状态 |
|---|---|
| 频道入口 / 重要性 / 扩展菜单实现 | 已提交 `80bc42cd` |
| 针对性单测 `SystemNotificationReviewTest` | 已有，已复跑通过 |
| 改前实机（fuxi r14.21.2） | 已做，见下方校正表 |
| `verify.py full` 的静态段 | PrefMap=0、热路径上限 13、invariants、eol、source cleanliness 通过 |
| `compileDebugKotlin` + `testDebugUnitTest` + `lintDebug` | 通过。`testDebugUnitTest` 2398 / fail 0 / error 0 / skip 2 |
| `audit-feature-semantics.py` 在本机 Python 3.10 | 失败。f-string 反斜杠是 3.12 语法，CI 用 3.12，main 已存在，与本分支无关 |
| GitHub Actions / PR | 无。该分支 push 不触发 Fast CI（只听 `main` 和 PR） |
| 签名开发包 | 未做。未授权正式构建 |
| **改后实机验收** | **未做。这是真正缺的一半** |

## 对输入报告的校正

| 项目 | 实机与样本证据 | 判断与处理 |
|---|---|---|
| 频道设置安装点 | `MiuiNotificationMenuRow.createMenuViews(Z)V` 存在，`onClickInfoItem` 不存在；原版开启功能并重启 SystemUI 后，点击“更多设置”成功进入 Shell command 频道 | 原报告的 P0 整体失效未复现。不照搬上游方法名。保留本 ROM 的图标监听器入口，改用公开频道 Intent，并预解析所需成员 |
| 通知重要性 | `BaseNotificationSettings` 和新旧两个 `ChannelNotificationSettings` 均存在；公开 Intent 打开 `.app` 实现 | 原报告的“类不存在”不成立，但只挂旧实现确有功能缺口。补齐两个实际实现的初始化与保存 |
| 重要性持久化 | 公开频道页从“高”改为“中”后，界面显示“中”；通知系统仍记录 `mImportance=3`，`mUserLockedFields=0` | 已复现界面与真实值不一致。必须以通知系统读回值及重新打开页面验收 |
| 扩展通知菜单 | 六个动作均成功安装；当前 1080 px / 464 dpi 下“浮窗”图标只显示 `[977,733][1023,878]`，前五个图标宽 145 px | 方法签名没有失配。重复加倍左右间距导致最右侧动作被裁切，恢复 ROM 的单倍边距 |
| 极简通知视图 | 实机 SystemUI DEX 无 `com.android.systemui.statusbar.phone.StatusBar`，现有 Hook 仍依赖其 `updateNotification` | 原报告的目标过期判断有样本支持。本次不猜测现代通知管线的等价行为，不移植或删除此功能；原设备开关为关闭 |
| 热路径 13 处 | 仓库静态扫描命中 13 处，包含反射、Parcel、File 和集合模式 | 这是静态模式数量，不是分配次数、耗时或已复现缺陷。本次不修改无性能证据的成熟基础设施 |

本 ROM 实际保留 `NotificationEntry.mSbn`、`ExpandedNotification.mAppUid`、`com.android.systemui.statusbar.notification.NotificationUtil.isHybrid(StatusBarNotification)` 和 `ModalController.animExitModal(long, boolean, String, boolean)`。这些名称不能仅根据另一版本的上游源码判定为错误。

用户反馈「打开频道设置开启后失效」在 fuxi + 杀 SystemUI 后**未复现**。更像 `CAPTURED_AT_INSTALL` 未重启，而不是拦截点装不上。本分支仍把 Intent 从 `SubSettings` + 无 `.app` fragment 换成公开 `ACTION_CHANNEL_NOTIFICATION_SETTINGS`，这是加固，不是复活死 Hook。改后路由必须再在实机确认一次。

## 修改的运行契约

### 频道入口

- 安装时解析类、字段和方法；菜单构建回调只在原方法成功后绑定图标监听器。
- 点击时读取当前通知，不在安装对象中保留 Activity、View、通知频道或 Context。
- 使用 `Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS`、`EXTRA_APP_PACKAGE` 与 `EXTRA_CHANNEL_ID`，Intent 限定到系统设置包。
- 按通知的应用 UID 选择用户，不使用 `CLEAR_TASK` 清除已有设置任务。
- 缺失/空白/默认频道和 Hybrid 通知继续走原监听器；启动失败也交回原监听器。
- 启动成功后的模态收起失败只隔离报错，不能再次打开原生设置页。

### 重要性

- 新旧两个频道类分别处理自身声明的方法；继承别名不会重复安装同一个方法。
- 在安装时解析偏好类型、字段、初始化方法、更新方法和代理接口。
- 只有 importance 偏好需要改写 `setPrefVisible` 的参数数组；其他偏好直接使用 `chain.proceed()`。
- 监听器只接受 UI 提供的 1 至 4 档；无效输入不更新系统频道。
- 普通保存异常返回拒绝，致命错误及其反射包装继续抛出。
- 代理的 `equals`、`hashCode`、`toString` 遵循对象契约，不执行频道写入。
- 监听器由偏好对象持有，未新增静态 UI 引用、Receiver、Observer、定时器或后台线程。

### 扩展菜单

- 构造函数按完整签名在安装时解析，不在每次菜单构建时枚举构造函数数组。
- 复用 ROM 的每侧 margin，消除扩展菜单对 margin 的重复加倍。
- 原菜单构建失败时直接传播原异常，不执行追加动作；构造与点击的反射异常显式保留致命错误传播。

## 静态续审（不改代码）

实现方向与 fuxi 证据一致，没有发现必须立刻改掉的回归。下面几条不影响合并前的静态结论，但改后实机要盯：

1. **公开 Intent 是行为变化。** 原路径是 `SubSettings` + `ChannelNotificationSettings` fragment。新路径是 `setPackage("com.android.settings")` 的公开频道 Action。改前实机证明旧路径能打开频道页；改后是否仍进 `.app` 页、是否丢掉 `miui.targetPkg` extra，只能实机答。
2. **`View.getListenerInfo` / `mOnClickListener` 是 hidden API。** 安装期解析，找不到则整个频道 Hook 装不上。fuxi 有这些成员。Hybrid/默认频道依赖捕获到的原 listener；捕获失败时这两个入口会变成空点击。
3. **`findMethodExact` 用 `getDeclaredMethod`，** 两个 `ChannelNotificationSettings` 不会把继承别名装两遍。这一点是对的。
4. **扩展菜单仍走 `animExitModal("OTHER")` 单参**，频道入口走四参。与改前一致，不是本提交引入。
5. **极简通知视图、菜单分组、热路径 13、版本号** 按合同未动。

## 性能与回退

| 边界 | 修改前 | 修改后 |
|---|---|---|
| 频道菜单构建 | 每次读取频道/SBN、查找 NotificationUtil 并执行 Hybrid 判断 | 成员安装时解析，频道/SBN/Hybrid 判断移到用户实际点击时 |
| 扩展菜单构造 | 每次访问 `constructors[0]`，创建构造函数数组且依赖枚举顺序 | 安装时按签名解析一次 |
| 重要性可见性 | 每次 `setPrefVisible` 都复制参数数组 | 只在 importance 参数确需改写时复制 |
| 全仓库 Hook-body PrefMap | 0 | 0 |
| 全仓库静态分配模式上限 | 13 | 13 |

ROM 原始 APK、DEX 方法清单、日志、截图、性能采样、原偏好备份及构建包仅保存在仓库外。方法清单来自实机 APK，而非仓库的编译 stub。

- SystemUI APK SHA-256：`5d8f2fe0b65d8a1a947b4280f8053b524f8c5de73f48a74f8792d415ae76e513`。
- 样本版本：SystemUI `20230316.0` / versionCode `202303160`；Settings `14` / versionCode `34`。
- 回退单位是本分支的通知实现提交；恢复设备时可覆盖安装原签名 APK，并还原本次触及的四个偏好值。
- 不把单轮 SystemUI 内存或帧统计解释为因果性能提升；其受通知、主题、其他已启用 Hook、启动预热和系统负载影响。

## 验证记录

- 针对性：`SystemNotificationReviewTest`、`SystemNotificationHooksTest`、`SettingsNotificationControlsContractTest` 通过。
- 全量单元测试：`testDebugUnitTest` 2398 tests, 0 fail, 0 error, 2 skipped。`lintDebug` 通过。JDK 25。
- 静态治理：PrefMap 天花板 0，热路径分配天花板 13，invariants / eol / source cleanliness 通过。
- `python tools/verify.py full` 在本机 Python 3.10 于 `audit-feature-semantics.py` 解析失败。原因是文件里 f-string 含 `\\`，需要 Python 3.12。CI workflow 固定 3.12。与本分支 diff 无关，不在本次修。
- 改后实机仍缺。合并 main 前至少要：

  1. 只开「打开频道设置」，杀 `com.android.systemui`。长按通知齿轮应进**该通知的频道页**，不是应用通知总页，也不应空白。
  2. dumpsys / 设置里确认 `mImportance` 与 `mUserLockedFields` 在改「高→中」后真正写回。
  3. 开「通知行菜单」，1080 px / 464 dpi 下第六项「浮窗」完整可见、可点。
  4. Hybrid / 默认频道仍走 ROM 原设置页。
  5. logcat 无 `Failed to hook createMenuViews`、无 `getListenerInfo` / `mOnClickListener` 安装失败。

接口依据：[Android Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS](https://developer.android.com/reference/android/provider/Settings#ACTION_CHANNEL_NOTIFICATION_SETTINGS)。此接口文档不代替 HyperOS 实机路由验证。

## 建议（仍不改代码）

1. 不要把 `80bc42cd` 合进 main，直到改后实机清单过完。
2. 不要发版、不要改 versionName、不要动 prefs XML / 菜单分组。
3. 要跑 CI：对 main 开 PR，或 `workflow_dispatch` Fast CI。
4. 极简通知视图保持现状，等专门授权再删。
