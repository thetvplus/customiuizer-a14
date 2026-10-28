# 音乐手势与操作

[返回动态岛说明](README.md)。以下为当前工作分支源码行为；主机、ROM 静态证据与设备验收分别记录。

## 配置与动作

入口为「系统 → 系统提示 → 手势与返回」。音乐与持续活动的上滑回岛也在此配置；媒体是否接入仍由音乐页控制。五项配置复用 MultiAction 编辑器、确认按钮及现有 payload；MUSIC 追加在枚举末尾，既有动作整数和编辑器索引保持。

| 手势 | 默认 | 配置键 |
| --- | --- | --- |
| 左滑 | 下一曲 | `system_island_music_left` |
| 右滑 | 上一曲 | `system_island_music_right` |
| 下滑 | 无；可配置为媒体动作 | `system_island_music_down` |
| 双击 | 无 | `system_island_music_double` |
| 长按 | 无 | `system_island_music_long` |
| 上滑 | 一次关闭当前来源 | 无编辑项 |
| 单击 | 打开当前播放器 | 仅配置双击时等待平台确认 |

`system_island_media_swipe` 默认开启，兼容新安装和升级；关闭只禁止左右滑动产生上一曲 / 下一曲，左右明确映射的音量等其他动作保持可用。其他手势独立配置。旧手势键继续退役。新动作使用 `_action`，自定义目标沿用 `_app`、`_app_user`、`_shortcut_intent`、`_activity` 等后缀，自动进入当前目录和备份 V2。

| 编号 | 执行 |
| --- | --- |
| `1` | 无 |
| `85` | 按当前真实 PLAYING / PAUSED 切换播放暂停 |
| `30 / 31` | 独立播放 / 暂停；新增动作编号，不是 KeyEvent 编码 |
| `88 / 87` | 上一曲 / 下一曲 |
| `18 / 19` | 当前会话音量增 / 减一级 |
| `17 / 32` | 已有音量面板入口 / 打开当前播放器（优先 sessionActivity） |
| `33 / 34` | 画中画完整隐藏 / 恢复，或 ROM 原生边缘收起 / 恢复；也可映射到全局手势 |
| `8 / 9 / 20` | 已配置应用 / 快捷方式 / Activity |
| `2 / 3` | 既有通知中心 / 快捷设置入口 |

五项自定义动作仅用于音乐；常驻小岛使用独立动作。媒体与全部系统持续活动统一短岛；下滑不展开，已存媒体下滑动作直接生效。所有活动岛一次上滑关闭当前来源；通知、Toast、系统反馈不额外暴露相同的动作编辑器。没有暴露重启、关机、强制结束应用、清理内存或全部系统开关，也没有尚未实现的 ROM 专属按钮。

两项 PiP 动作首次配置后需要重启 SystemUI，不发送暂停命令。完整隐藏同时释放输入区域，在原生窗口转场前恢复；锁屏、不稳定任务或截图期间不执行。“排除小窗媒体”按小窗来源反选，不再全局隐藏独立音乐和允许通知；PiP 的原生返回事务仍只保护对应视频。

## 输入、会话与生命周期

每个岛体 View 使用一份平台 GestureDetector，采用 ViewConfiguration 的双击 / 长按时间，不增加全局监听或五套调度器。每次触摸额外租用平台 VelocityTracker，取近期速度而非全程平均，释放、取消和失效时归还；连续拖动可以接管未完成的回弹，位置保持连续，释放速度通过阻力导数传给共享有限弹簧。未配置双击时单击立即响应；配置后双击与确认单击互斥。长按只执行一次，之后不提交单击或滑动。先用原始位移锁定上滑意图并取消点击，再判定是否收起；短上滑未达收起距离只回弹，没有 MOVE 的快速释放同样判定。拖动锁轴，满足方向和距离阈值才在释放时提交；多指、CANCEL、斜向无效输入取消候选。

内容、控制器、动作配置、来源应用、隐私、退场、解绑及 owner 更换使未提交输入和任务失效。单纯几何参数变更不重置已接收的媒体动作；位置 / 尺寸变更仍会取消需要重新布局的触摸候选。执行端检查实际可见媒体、前台 / 隐私、token、当前 Controller、action epoch、真实 PlaybackState 与能力位，使用同一 Controller.TransportControls，不退回全系统媒体键。不支持的动作静默结束。无障碍保留点击、收起和已配置动作菜单，使用相同门禁。

音量通过 MediaController.adjustVolume 跟随当前会话。本地（含蓝牙媒体路由）要求媒体用途且设备非固定音量；远程跟随其相对 / 绝对调节能力，固定音量忽略。每次完成手势只调节一次，不在 MOVE 中连续修改。实际蓝牙、投放与固定音量设备仍待验收。平台契约：[MediaController](https://developer.android.com/reference/android/media/session/MediaController)、[TransportControls](https://developer.android.com/reference/android/media/session/MediaController.TransportControls)。

## 打开当前播放器

单击和映射为 `32` 的下滑等手势共用同一事务。执行时重新读取当前选中 MediaController 的 sessionActivity；只发送同一 UID / 包的 Activity PendingIntent。缺失、非 Activity 或明确取消后，才评估 API 33+ 的 `PackageManager.getLaunchIntentSenderForPackage`。一个成功入口立即结束，不再发第二个请求，也不发送全系统广播或媒体键。

A14 MediaSession.Token 的 UID 与当前前台用户必须匹配；包入口只在 Context 所属用户相同且包 UID 一致、应用启用、锁屏允许时使用。工作资料、匿名 UID、旧 Controller / epoch、取消中的动作和不存在的入口安全失败。跨用户 Context 的包入口不尝试绕过限制；有效且同用户的会话 PendingIntent 仍可保留系统自己的启动行为。失败日志只保留固定原因，每个 owner 最多八类，不包含包名、标题或 Intent 数据。

平台依据：[PackageManager.getLaunchIntentSenderForPackage](https://developer.android.com/reference/android/content/pm/PackageManager#getLaunchIntentSenderForPackage(java.lang.String))、[AOSP A14 MediaSession.Token UID](https://android.googlesource.com/platform/frameworks/base/+/android-14.0.0_r1/media/java/android/media/session/MediaSession.java)。主机测试对 Robolectric 4.17 尚未实现的包启动 Binder 使用替身；设备上的任务栈、后台启动限制与各播放器 PendingIntent 仍须验收。

## 暂停可见性

Controller 从现有 IslandWindowPresentation 的预绘制取得展示证据；attach / PREPARED 不代表实际显示。未显示窗口直接移除，避免退场动画本身制造闪现。同一身份从 EXITING 重入也重新经过 PREPARED → VISIBLE。只有本次真正展示的播放活动才可在暂停后保留约两秒；重复 PAUSED、metadata、前台变化不续期，进入来源应用消费旧暂停期，回 Home 不重开。来源 App 内、入场延迟内、遮挡期间的新播放或其他提示占窗时暂停，均不建立暂停岛；真实恢复沿用普通入场延迟。

离开播放器先读取新快照，避免前台变化先于暂停读取时显示旧 PLAYING。未显示的新播放活动即使沿用旧 session，也不能凭历史展示记录建立暂停岛；回调绑定当前会话及 controller，停止、隐私遮挡、失效、替换 / 销毁和关闭媒体清理会话及延迟任务。

## CN / TW 静态取证（2026-10-23）

实际使用 DexSQL 0.1.1 MCP，先核对 tables / methods schema，再运行[音乐手势查询](../../../tools/dexsql/island_music_gestures.sql)、字段 / 调用查询和分页 Smali。样本来自用户工作区已有解包目录，版本来自 build.prop，不代表已连接设备。

| 输入 | SHA-256 |
| --- | --- |
| CN V816.0.19.0.UMCCNXM / SystemUI | `2bf4f7e56eeda694e8f7de71c0acda2cae892f9847c6d6a747a52cfe5381e42b` |
| TW V816.0.8.0.UMCTWXM / SystemUI | `66386ad63878da129041fab7ae334d313257703515e4c9a179cb092292b32dc1` |
| CN / 插件 | `3dafd9e068ebee7e88344ae1c7d146c7e2d41e79b5c52b7736cd3e58be0cc999` |
| TW / 插件（与 CN 同摘要） | `3dafd9e068ebee7e88344ae1c7d146c7e2d41e79b5c52b7736cd3e58be0cc999` |
| CN / framework.jar | `cdfca3dd220865fc5252e3af938c1fb4254c840678e386ce8216a2f41a9aaeb5` |
| TW / framework.jar | `8586618b92c2892a9c6be1cfd28871d9b39655162ff97a4b12f8d966d14895e9` |

各自查询得到 19 个媒体 / SDK 方法声明、17 个音量相关字段、3 条选中媒体回调调用关系。各数据集 file_id 仅局部有效：SystemUI 主媒体管理器位于 0、VolumeDialogComponent 位于 1；插件 MiuiVolumeDialogImpl 位于 4、VolumeDialogPlugin 位于 6；framework 媒体 API 位于 8。

选中链由 NotificationMediaManager.findAndUpdateMediaNotifications()V 的 Smali 复核：检查媒体通知 token / PLAYING，以 controlsSameSession 比较，替换时注销旧回调、设置 mMediaController、注册新回调。TransportControls 和 adjustVolume 调用各自 ISessionController Binder。音量面板链为 CentralSurfacesImpl.mVolumeComponent → VolumeDialogComponent.mDialog → VolumeDialogPlugin.mVolumeDialogImpl → MiuiVolumeDialogImpl.mHandler；确认 showVolumeDialogH(I)V 定义及指令。复用既有入口，没有新增猜测 Hook。原始结果留在忽略的 `.di-evidence/r14.25.2/`，ROM 原件不入库。

## 验证边界与后续

`IslandMusicActionsTest` 验证默认、开关、能力位、音量策略和几何；`IslandMusicGestureTest` 在 Robolectric SDK 34 执行 View 的单击 / 双击、长按 / 滑动、取消、无操作及无障碍。`IslandActivityLifecycleTest` 检查 PREPARED、入场延迟、暂停 / 停止、来源应用、metadata、旧回调、抢占、会话更换和关闭的完整时序与状态栏占用。`IslandWindowPresentationTest` 覆盖同身份退场重入，设置导航、动作矩阵和备份另有回归。

后续整合仅保留以上 2026-10-23 CN / TW 取证，没有重跑或扩充该批 ROM 结论。音频 UID / session 隐藏 API 只由 AOSP A14 源码确认；SystemUI 权限和实际音频路由仍保留实验性状态。

这些是主机 / Robolectric / 既有 ROM 静态证据。目标 HyperOS 触摸手感、TalkBack、蓝牙 / 远程音量、物理开孔、横竖屏、SystemUI 重建、通知工作资料和帧时间 / 功耗仍待实际设备验证。新增 ROM 专属动作及更多系统开关继续作为候选，需样本哈希、唯一目标、调用 / 字段和 ClassLoader 证据后单独开发；不把本次静态结果标为实机 PASS。
