# 音乐手势与操作

[返回动态岛说明](README.md)。以下为 r14.25.2 源码行为；主机、ROM 静态证据与设备验收分别记录。

## 配置与动作

入口为「系统 → 动态岛 → 音乐与媒体」。五项配置复用 MultiAction 编辑器、确认按钮及现有 payload；MUSIC 追加在枚举末尾，既有动作整数和编辑器索引保持。

| 手势 | 默认 | 配置键 |
| --- | --- | --- |
| 左滑 | 下一曲 | `system_island_music_left` |
| 右滑 | 上一曲 | `system_island_music_right` |
| 下滑 | 无 | `system_island_music_down` |
| 双击 | 无 | `system_island_music_double` |
| 长按 | 无 | `system_island_music_long` |
| 上滑 | 固定收起当前岛，不是 Android Back | 无编辑项 |
| 单击 | 打开当前播放器 | 仅配置双击时等待平台确认 |

`system_island_media_swipe` 默认开启，兼容新安装和升级；关闭只禁止左右滑动产生上一曲 / 下一曲，左右明确映射的音量等其他动作保持可用。其他手势独立配置。旧手势键继续退役。新动作使用 `_action`，自定义目标沿用 `_app`、`_app_user`、`_shortcut_intent`、`_activity` 等后缀，自动进入当前目录和备份 V2。

| 编号 | 执行 |
| --- | --- |
| `1` | 无 |
| `85` | 按当前真实 PLAYING / PAUSED 切换播放暂停 |
| `30 / 31` | 独立播放 / 暂停；新增动作编号，不是 KeyEvent 编码 |
| `88 / 87` | 上一曲 / 下一曲 |
| `18 / 19` | 当前会话音量增 / 减一级 |
| `17 / 32` | 已有音量面板入口 / 当前播放器 sessionActivity |
| `8 / 9 / 20` | 已配置应用 / 快捷方式 / Activity |
| `2 / 3` | 既有通知中心 / 快捷设置入口 |

配置仅用于音乐。通知、Toast、系统反馈保持原有点击和上滑行为。没有暴露重启、关机、强制结束应用、清理内存或全部系统开关，也没有尚未实现的 ROM 专属按钮。

## 输入、会话与生命周期

每个岛体 View 使用一份平台 GestureDetector，采用 ViewConfiguration 的双击 / 长按时间，不增加全局监听或五套调度器。未配置双击时单击立即响应；配置后双击与确认单击互斥。长按只执行一次，之后不提交单击或滑动。拖动锁轴，满足方向和距离阈值才在释放时提交；多指、CANCEL、斜向无效输入取消候选。

内容、控制器、配置、来源应用、隐私、退场、解绑及 owner 更换使未提交输入和任务失效。执行端检查实际可见媒体、前台 / 隐私、token、当前 Controller、action epoch、真实 PlaybackState 与能力位，使用同一 Controller.TransportControls，不退回全系统媒体键。不支持的动作静默结束。无障碍保留点击、收起和已配置动作菜单，使用相同门禁。

音量通过 MediaController.adjustVolume 跟随当前会话。本地（含蓝牙媒体路由）要求媒体用途且设备非固定音量；远程跟随其相对 / 绝对调节能力，固定音量忽略。每次完成手势只调节一次，不在 MOVE 中连续修改。实际蓝牙、投放与固定音量设备仍待验收。平台契约：[MediaController](https://developer.android.com/reference/android/media/session/MediaController)、[TransportControls](https://developer.android.com/reference/android/media/session/MediaController.TransportControls)。

## 暂停可见性

Controller 从现有 IslandWindowPresentation 的预绘制取得展示证据；attach / PREPARED 不代表实际显示。未显示窗口直接移除，避免退场动画本身制造闪现。同一身份从 EXITING 重入也重新经过 PREPARED → VISIBLE。已显示活动暂停约两秒退出；重复 PAUSED、metadata、前台变化不续期，进入来源应用消费旧暂停期。

离开播放器先读取新快照，避免前台变化先于暂停读取时显示旧 PLAYING。未显示的新播放活动即使沿用旧 session，也不能凭历史展示记录建立暂停岛；停止、失效、替换和关闭媒体清理会话及延迟任务。

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

这些是主机 / Robolectric / ROM 静态证据。目标 HyperOS 触摸手感、TalkBack、蓝牙 / 远程音量、物理开孔、横竖屏、SystemUI 重建、通知工作资料和帧时间 / 功耗仍待实际设备验证。新增 ROM 专属动作及更多系统开关继续作为候选，需样本哈希、唯一目标、调用 / 字段和 ClassLoader 证据后单独开发；不把本次静态结果标为实机 PASS。
