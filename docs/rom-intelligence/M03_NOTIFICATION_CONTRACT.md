# M03 通知链路：ROM 精校交接

本页承接[精修路线图 M03](../maintenance/A14_REFINEMENT_ROADMAP.md#m03-notifications-20261023)，只保存定位合同与验证边界；完成状态、代码历史和取舍仍在路线图原页。查询入口为 [notification_m03_contract.sql](../../tools/dexsql/notification_m03_contract.sql)。

## 证据与输入

2026-10-23 使用本机 DexSQL **0.1.1**，读取已解包 fuxi CN/TW HyperOS 1 / Android 14 样本；没有连接 ADB 设备。目录和历史镜像 provenance 不代表当前设备安装版本。原件、完整查询回执、Smali、测试日志及后续 R8 mapping 放在忽略目录 `.di-evidence/m03-20261023/` 或仓库外，不提交厂商样本。

最终 SQL 在 SystemUI / Settings / framework / services 数据集返回 96 个声明、108 条调用和 44 个字段声明；在 miui JAR 数据集返回 32 个声明、2 条调用。所有结果均读到 `next_offset=null`。这些是静态引用行数，包含多个 invoke，**不是执行次数**。17 对关键方法共 34 个完整 decode、36 页文本，CN/TW 文本相同；另补两区 NMS 更新与 setFold 的完整 decode。地区配置和实际路径仍可能不同。方法索引只在对应数据集内有效，交接使用下述描述符和哈希。

| 相对镜像路径 | CN SHA-256 | TW SHA-256 |
| --- | --- | --- |
| `system_ext/priv-app/MiuiSystemUI/MiuiSystemUI.apk` | `2bf4f7e56eeda694e8f7de71c0acda2cae892f9847c6d6a747a52cfe5381e42b` | `66386ad63878da129041fab7ae334d313257703515e4c9a179cb092292b32dc1` |
| `system_ext/priv-app/Settings/Settings.apk` | `1e15b82df17fee1700fc47b8cd592bf7d4a6fc3994b9207f189fca64dd47efdb` | `ba451ae3b0c70041f294c33b86939d4c211b82278b5a01dc991eac2cc5db5c02` |
| `system/system/framework/framework.jar` | `cdfca3dd220865fc5252e3af938c1fb4254c840678e386ce8216a2f41a9aaeb5` | `8586618b92c2892a9c6be1cfd28871d9b39655162ff97a4b12f8d966d14895e9` |
| `system/system/framework/services.jar` | `e9552126b93b1b8e5931e0fdac8ab166ca1c98c29d877fde6ac23773cba78dd0` | `9341c22bf12f98d12607af3b93ef7c597931309a32e388c3bdc394854840db75` |
| `system_ext/framework/miui-services.jar` | `be409f51114cfb676ab5c1210e77f566e4a0a228bcbd6d7d09d3c0ee913fd5e2` | `be409f51114cfb676ab5c1210e77f566e4a0a228bcbd6d7d09d3c0ee913fd5e2` |
| `system_ext/framework/miui-framework.jar` | `d0968f31916043947987e399a1b6a6abbf15dbb360e0198001f30b00cb35764c` | `38d7a74c9bf9b7039d30d26c52a56e8dbe9443640bd6d222414ae8727cf97428` |

## M03.01：原生点击和小窗选项

完整入口：

```text
Lcom/android/systemui/statusbar/phone/StatusBarNotificationActivityStarter;->onNotificationClicked(Lcom/android/systemui/statusbar/notification/collection/NotificationEntry;Lcom/android/systemui/statusbar/notification/row/ExpandableNotificationRow;)V
Lcom/android/systemui/statusbar/phone/StatusBarNotificationActivityStarter$1;->onDismiss()Z
Lcom/android/systemui/statusbar/phone/StatusBarNotificationActivityStarter$$ExternalSyntheticLambda3;->run()V
Landroid/util/MiuiMultiWindowUtils;->getActivityOptions(Landroid/content/Context;Ljava/lang/String;)Landroid/app/ActivityOptions;
Lcom/android/systemui/statusbar/phone/StatusBarNotificationActivityStarter$$ExternalSyntheticLambda4;->startPendingIntent(Landroid/view/RemoteAnimationAdapter;)I
```

上述成员分别属于 SystemUI / miui-framework；模块从 SystemUI 的 `lpparam.classLoader` 解析，boot / MIUI 类由父加载器提供，实际加载器仍需设备日志确认。入口先处理远程输入，再创建原生解锁动作。延迟 `run()` 持有 `f$0=starter`、`f$1=entry`、`f$2=row`、`f$3=PendingIntent`、`f$6=overLockscreen`；工作资料挑战与 bubble 分支在请求小窗选项之前执行。

旧 `clickNotifyOptions` 没有通知 / 线程所有权，在点击返回后继续等待任何七参 PendingIntent 发送；无发送、异常、解锁延迟和另一条通知均可污染它。新实现只包围原生 `run()`，在其**两参选项入口**匹配 Context 和 creator 包并消费一次；`finally` 清理，重入普通事务遮蔽外层。原生已给出选项则保留，非目标不计算额外选项；不 Hook 全局 PendingIntent，不自行重试或增加第二次发送。

锁屏、全屏意图、跨用户 / 工作资料、非 Activity PendingIntent 保持原生；当前 entry / Sbn / Row 已失效时不附加选项。原生游戏和小窗栈限制仍在其后执行；动画发送分支 `Lambda4` 没有新增 Hook。菜单显式小窗保留自己的原有发送入口，但先核对活跃通知、用户和锁屏，拒绝非 Activity / 空选项，成功发送前禁用本次按钮，取消失败才恢复按钮。动态岛仍使用原生 Row 点击，补查 `mRemoved` 与弱引用 entry 身份。

system_server 的真实定义在 **miui-services.jar**，不是凭 `services.jar` 的零结果判缺失：

```text
Lcom/android/server/wm/ActivityStarterImpl;->checkStartActivityByFreeForm(Landroid/app/IApplicationThread;Landroid/content/pm/ActivityInfo;Landroid/content/Intent;Ljava/lang/String;ZILjava/lang/String;Lcom/android/server/wm/SafeActivityOptions;)Lcom/android/server/wm/SafeActivityOptions;
Lcom/android/server/wm/ActivityStarterImpl;->startActivityByFreeForm(Landroid/content/pm/ActivityInfo;Landroid/content/Intent;)Z
Lcom/miui/server/SecurityManagerService$LocalService;->checkGameBoosterPayPassAsUser(Ljava/lang/String;Landroid/content/Intent;I)Z
```

最后一项是前项私有布局判断的直接调用目标。模块保留 SecurityManagerService 原结果，只在对应 ActivityStarter 调用内判断分享 / 磁贴的小窗规则，不再使用进程级 caller 或改写 SecurityManager 方法。磁贴旧广播保持非导出接收器，收紧到包、完整 component、用户、SystemUI caller、单次消费和 5 秒期限；没有计时线程。非 Activity 通知不再通过“下一包名”旁路传递小窗状态。

后续查询：按完整描述符确认合成类字段类型及其原生构造调用者；decode `run()` 的工作挑战、bubble、游戏限制和两个发送尾部；在同版样本检查合成类名是否漂移。零 / 多候选时只记录缺口，不添加宽泛备用 Hook。设备需记录实际 starter / options Hook 各一次，连续不同通知、普通 / 小窗并发、锁屏解锁、资料挑战、次用户及取消后的行为。

## M03.02：Heads-up 和提醒通道

```text
Lcom/android/systemui/statusbar/notification/row/ExpandableNotificationRow;->setHeadsUp(Z)V
Lcom/android/systemui/statusbar/notification/row/ExpandableNotificationRow;->setEntry(Lcom/android/systemui/statusbar/notification/collection/NotificationEntry;)V
Lcom/android/systemui/statusbar/notification/row/ExpandableNotificationRow;->setRemoved()V
Lcom/android/systemui/statusbar/phone/HeadsUpManagerPhone$HeadsUpEntryPhone;->isSticky()Z
Lcom/android/systemui/statusbar/phone/HeadsUpManagerPhone$HeadsUpEntryPhone;->updateEntry(Z)V
Lcom/android/systemui/statusbar/phone/HeadsUpManagerPhone$HeadsUpEntryPhone;->calculateFinishTime()J
Lcom/android/systemui/statusbar/phone/StatusBarNotificationPresenter;->onExpandClicked(Lcom/android/systemui/statusbar/notification/collection/NotificationEntry;Z)V
Lcom/android/systemui/statusbar/notification/interruption/NotificationInterruptStateProviderImpl;->canAlertHeadsUpCommon(Lcom/android/systemui/statusbar/notification/collection/NotificationEntry;Z)Z
Lcom/android/systemui/statusbar/notification/policy/MiuiAlertManager$$ExternalSyntheticLambda0;->run()V
Landroid/app/INotificationManager$Stub$Proxy;->buzzBeepBlinkForNotification(Ljava/lang/String;I)V
```

展开任务由 Row 关联，弱持有 Row / entry / Sbn，每个 Row 至多一个 60ms 任务；移除、换绑、退出 Heads-up、detach 和运行结束清理。执行时重新检查身份、当前配置、锁屏、用户拖动、`mPrivateLayout.mRemoteInputVisible` 和原生展开状态。远程输入为空文本时也受保护，不能仅用草稿是否为空判断。

自动收起只放宽“已展开且 pinned”导致的 stickiness，保留远程输入、全屏意图和 guts 条件。原生 `updateEntry(false)` / reset / 输入切换继续拥有唯一移除 Runnable。超时使用原生 postTime、最短展示时间、floatTime、无障碍建议和 extended 时间，不再强制展开后固定 4500/10000ms；这项时序需设备验收。

`canAlertAwakeCommon` 同时参与 bubble 判断，旧静音 Hook 会影响它；现在仅拦 Heads-up 通道。原生提醒后台任务把振动 / 声音 / 灯光编码为位 1 / 2 / 4；旧整段跳过 `buzzBeepBlink` 也丢掉灯光。新实现限定在原生后台任务、同一 Binder proxy 和 notification key，亮屏只清位 1 / 2，保留其他位。SystemUI API proxy 与原生 Binder 权限检查保留。

待精校：CN/TW 的输入激活 / 结束、旧 alert entry 复用和 reset、无障碍超时、锁屏与旋转，及动态岛成功 / 拒绝接管时的原生 Heads-up 数量。静态相同签名不能证明没有重复展示。

2026-10-24 接管边界补充见[精修路线图第 16 节](../maintenance/A14_REFINEMENT_ROADMAP.md#16-2026-10-24动态岛锁屏通知与前后台所有权专项)。
复用已提取实机 SystemUI（SHA-256 `5d8f2fe0b65d8a1a947b4280f8053b524f8c5de73f48a74f8792d415ae76e513`）与 CN/TW 参考样本：
`KeyguardNotificationVisibilityProviderImpl.isLockedOrLocking()` 读取 `KeyguardStateControllerImpl.mShowing` 和 `StatusBarStateControllerImpl.mUpcomingState`，不能仅以 `mState=0` 推断解锁。
动态岛沿用 `Dependency.get(KeyguardStateController.class)` 和 native Callback，在现有 owner 的连接 / 释放边界注册 / 移除，事件边界拒绝 Showing、GoingAway、FadingAway、Bouncer、LaunchTransitionFadingAway、KeyguardRequested、Dozing / Dreaming 及未知状态。
`onBindFinished(NotificationEntry)` 的原生尾部设置 Heads-up / alert 状态、`interruption` 并移除本次 binding；模块只在当前通知 / Row / binding 仍有效且窗口插入成功后提交。
未提交失败回滚自己的通知 lease 和中间状态；提交后的 `unbindHeadsUpView` 失败不重放原生提醒。该方法将 **flag 4** 标为 freeable 后请求 rebind，不能凭它断言普通 / 锁屏 Row 被删除。
锁屏列表另经 `KeyguardCoordinator` / `KeyguardNotificationVisibilityProviderImpl` 的隐私、用户和静默过滤；本次未更改这些决策，也未实机验证列表绘制。补充声明、字段写入和 Dependency 来源查询已纳入 [island_activity_contract.sql](../../tools/dexsql/island_activity_contract.sql)。

## M03.03：界面控制与 NMS 保护

```text
Lmiui/util/NotificationFilterHelper;->isNotificationForcedEnabled(Landroid/content/Context;Ljava/lang/String;)Z
Lmiui/util/NotificationFilterHelper;->isNotificationForcedEnabled(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)Z
Lmiui/util/NotificationFilterHelper;->isNotificationForcedFor(Landroid/content/Context;Ljava/lang/String;)Z
Landroid/app/NotificationChannel;->isBlockable()Z
Landroid/app/NotificationChannel;->setBlockable(Z)V
Landroid/app/NotificationChannel;->isImportanceLockedByCriticalDeviceFunction()Z
Lcom/android/settings/notification/app/NotificationPreferenceController;->isAppBlockable()Z
Lcom/android/settings/notification/app/NotificationPreferenceController;->isChannelBlockable(Landroid/app/NotificationChannel;)Z
Lcom/android/settings/notification/app/NotificationPreferenceController;->isChannelConfigurable(Landroid/app/NotificationChannel;)Z
Lcom/android/settings/notification/app/NotificationPreferenceController;->isChannelGroupBlockable(Landroid/app/NotificationChannelGroup;)Z
Lcom/android/server/notification/PreferencesHelper;->createNotificationChannel(Ljava/lang/String;ILandroid/app/NotificationChannel;ZZIZ)Z
Lcom/android/server/notification/PreferencesHelper;->updateNotificationChannel(Ljava/lang/String;ILandroid/app/NotificationChannel;ZIZ)V
```

FilterHelper 定义在 miui-framework.jar；Settings 四项是本地界面判断，但读取 `systemApp`、`lockedImportance` 和频道属性。`NotificationChannel` getter/setter 属 boot framework，在 system_server 全局替换会直接影响 services.jar 内 PreferencesHelper 的创建、更新和关键频道判断，因此不只是“显示开关”。

两区 updateNotificationChannel 的实际分支为“旧频道 critical-locked、不可 block 且 importance 非零时，恢复旧 importance”。全局 `isBlockable=true` 会跳过这个保护分支；这是本轮退役的具体安全边界证据，不是已发生设备事件的报告。

本轮退役 system_server 功能 ID 38，保留编号空位。删除 NotificationChannel 全局覆盖、forced-enabled 集合替换、`NotificationSettingsManager.USE_WHITE_LISTS=false` 和 Settings 全 true / 强制 enable。旧键 `system_disableanynotif` 保留且默认关闭，只在 Settings / SystemUI 的三个 MIUI 判断中放宽**当前用户、普通应用 UID、非系统 / 非更新系统应用**。无目标包 / 用户证据时保留原判断；不改通知运行时权限、AppOps、设备管理员或 NMS 数据。

Settings 两种 `ChannelNotificationSettings.setupChannelDefaultPrefs()V` / `updateDependents(Z)V` 与公开频道设置 Intent 路线继续保留。重要性只接受 1..4；新增关键设备频道锁与用户复核，原后端失败隔离保留。SystemUI 图标重要性只影响一次 `updateStatusBarIcons()` 的展示输入，`finally` 恢复 canonical list，不改 NMS 或新原生列表。

待精校：从上述 NMS 调用者 decode 关键设备频道 / 管理员锁分支，实机读回频道 importance、userLockedFields、关键锁和实际授权，不能只看文字或后端返回。**升级首次验收需重启设备清除旧 system_server Hook；已经持久化的旧频道 / 授权状态不自动回写为猜测的默认值。**

## M03.04：数量和折叠规则

```text
Lcom/android/systemui/statusbar/notification/collection/coordinator/CountLimitCoordinator;->attach(Lcom/android/systemui/statusbar/notification/collection/NotifPipeline;)V
Lcom/android/systemui/statusbar/notification/collection/coordinator/CountLimitCoordinator$$ExternalSyntheticLambda0;->onViewBound(Lcom/android/systemui/statusbar/notification/collection/NotificationEntry;)V
Lcom/android/systemui/statusbar/notification/collection/coordinator/FoldCoordinator;->attach(Lcom/android/systemui/statusbar/notification/collection/NotifPipeline;)V
Lcom/android/systemui/statusbar/notification/collection/coordinator/FoldCoordinator$foldFilter$1;->shouldFilterOut(Lcom/android/systemui/statusbar/notification/collection/NotificationEntry;J)Z
Lcom/android/systemui/statusbar/notification/collection/coordinator/FoldCoordinator;->scheduleHistoryNotification(Lcom/android/systemui/statusbar/notification/collection/NotificationEntry;)V
Lcom/android/systemui/statusbar/notification/collection/coordinator/FoldCoordinator$collectionListener$1;->onEntryAdded(Lcom/android/systemui/statusbar/notification/collection/NotificationEntry;)V
Lcom/android/systemui/statusbar/notification/NotificationUtil;->setFold(Lcom/android/systemui/statusbar/notification/collection/NotificationEntry;Z)V
Lcom/android/systemui/statusbar/notification/NotificationUtil;->shouldSuppressFold()Z
```

Count attach 设置 Pipeline 并注册 BindEventManager listener；只有该 listener 的 `onViewBound` 做数量淘汰，包括国际版的独立数量分支。现在保留 attach，仅停用这条淘汰回调。

Fold attach 设置 Pipeline、初始化云白名单、注册 collection / filter / 折叠入口 / shade / 用户 / 配置 / ContentObserver，并初始化 section header。现在保留整个 attach；只停用 fold filter 和历史折叠调度，保留原生 shouldSuppressFold 门禁。`onEntryUpdated` **不检查该门禁就会调度历史折叠**，故仅改 shouldSuppressFold 不够。新增通知用原生 `setFold(false)` 同步 entry 与 `miui.unimportant`，该 setter 自带 shouldIgnoreEntry 保护。移除监听仍取消原生旧任务；没有模块轮询或替代 Pipeline。

地区说明：shouldSuppressFold 仍包含 `IS_INTERNATIONAL_BUILD` / `MIUI_LITE_V2`，17 对相同 Smali 不等于地区配置相同。其媒体 / 通知标题 attach-detach 调用者、原生入口状态、重建和重新启用后的恢复须逐区验证；不把一次静态扫描标为实机闭环。

## 设备与性能验收清单

在 API 102 环境确认安装日志，保存实际 ROM / APK 身份，再在开关关闭的基线与启用场景各跑一轮：

| 场景 | 必须观察 / 读回 |
| --- | --- |
| 多条通知、交替点击、普通与小窗并发、取消 / 重入 | 一个用户动作至多一次实际启动，无其他 PendingIntent 继承；广播 / Service 通知正常 native 启动 |
| 锁屏、解锁、资料单独挑战、次用户 | 原生认证和用户一致，拒绝跨资料附加小窗选项 |
| 撤回、换 Row、同 key 更新、Heads-up 展开时取消 | 无过期点击；原生移除任务没有触及复用后的 entry |
| 远程输入、空输入、guts、全屏通知 | 不抢焦点、不误收起；超时遵守无障碍与 extended 配置 |
| 动态岛允许 / 拒绝接管 | 仅一种浮动展示，普通通知中心记录和 native click 保留，撤回后入口失效 |
| 通知权限 / 频道 / 工作资料 / 管理员 | 普通目标可管理，关键锁不绕过，实际频道及授权读回匹配 |
| 横竖屏、字体、语言、主题、SystemUI 重建 | 居中尺寸刷新，普通 / 媒体 / 自定义 RemoteViews / 摘要背景正确，配置无串用 |
| 秒针熄屏创建和关屏、全部 M03 关闭后重建 | 秒针 OFF 时无运行任务；没有 M03 新增业务 Hook / listener / 模块任务，频道历史数据单独核查 |

性能需分别记录同负载下 SystemUI callback 次数、线程、PSS / Java heap、CPU、主线程帧时间 / GPU 与日志频率。当前只有源码推导和 Host JVM / Robolectric 回归；没有 ART 性能、设备 CPU / GPU、帧率、PSS 或功耗结果。保留原生 Coordinator 初始化会恢复必要监听开销，不能宣称所有常驻成本都下降。
