# 音乐手势配置方案（待取证和实现）

[返回动态岛当前说明](README.md)

这一节是后续候选方案，当前代码只实现了左右切歌开关，并未提供以下五个独立配置入口。当前交接环境没有 DexSQL 连接或 ROM APK 原始输入，不能把已有 FUXI / CN / TW 记录当作本次重新扫描结果；后续由具备样本的桌面 Agent 继续取证和实现。建议先限定音乐活动，通知 / Toast / 系统终态保留原有单击与上滑行为，不为每种活动重复增加设置。

| 音乐岛手势 | 建议默认 | 可配置范围 |
| --- | --- | --- |
| 上滑 | 收起 / 隐藏当前岛 | 固定，继续走现有 dismiss；不是系统返回键 |
| 左滑 | 下一曲 | 无、播放 / 暂停、单独播放 / 暂停、上 / 下一曲、音量操作、自定义动作 |
| 右滑 | 上一曲 | 同上；左右总开关关闭时两侧均不执行 |
| 下滑 | 无 | 同上，可选系统音量面板 |
| 双击 | 无 | 同上，常用候选为播放 / 暂停 |
| 长按 | 无 | 同上，常用候选为系统音量面板；一次触发一次动作，不默认连发 |

保留当前单击立即打开播放器。只有实际启用双击动作时才等待系统双击超时再确认单击，双击不得同时打开播放器；拖动、多指、CANCEL、退场、内容身份变化、关闭及解绑都取消待确认单击 / 长按。旧媒体手势键继续退役，新配置用独立键，不改变已有备份默认行为。

动作接入应复用 `SubFragment.openMultiAction`、`MultiAction` 和 `GlobalActions.handleResolvedAction` 的现有编辑与自定义 payload；为音乐追加独立动作集合，枚举放末尾，不改既有编号。现有矩阵已有无 `1`、音量面板 `17`、音量加 / 减 `18/19`、播放 / 暂停 `85`、下一曲 / 上一曲 `87/88`，以及应用 `8`、快捷方式 `9`、Activity `20` 和系统开关 `10`。单独播放 / 暂停目前没有完整的通用矩阵分支，不能直接把 KeyEvent 常量填进去后声称可用；需增加明确的共享映射、名称和回归。

媒体操作必须在 `IslandActivityController` 内绑定当前选中 token，并核对实际可见音乐、前台 / 安全状态、session / action epoch 和播放器 action bits，使用同一 `MediaController.TransportControls` 的 `play` / `pause` / `skipToNext` / `skipToPrevious`；不要退回向全系统发媒体键，以免操作另一播放器。不支持的操作不执行。音量增减先区分本地 / 远程播放与固定音量，优先跟随当前 controller；系统音量面板及应用 / 快捷方式等走现有受限动作入口，失败局部结束，不另找宽泛反射 fallback。平台契约见 [TransportControls](https://developer.android.com/reference/android/media/session/MediaController.TransportControls) 与 [MediaController](https://developer.android.com/reference/android/media/session/MediaController)。

桌面取证复用 [DexSQL 指引](../../rom-intelligence/DEXSQL.md)、[现有音乐接入查询](../../../tools/dexsql/island_activity_contract.sql) 和新增的 [音乐手势查询](../../../tools/dexsql/island_music_gestures.sql)。输入为带哈希的 CN / TW SystemUI、对应插件和 boot framework；先查 schema / 定义来源，再核对 NotificationMediaManager 的选中会话、TransportControls 能力与既有音量面板调用链。FUXI 旧记录中的 `MiuiVolumeDialogImpl.showVolumeDialogH(I)` 仅作候选线索，不替代当前样本。保存版本、file_id、描述符、调用者、匹配数量和 Smali；缺输入或多候选保持 CANDIDATE，不新增猜测 Hook。

实现后把新键加入源 XML、语义目录和偏好备份，再用既有流程更新进程矩阵；没有新增 Hook 时不虚构安装点。验证须覆盖五手势独立映射与“无”、上滑固定、双击不兼发单击、拖动取消长按、左右总开关、配置变更取消旧操作、会话替换 / 不支持动作、通知抢占，以及本地 / 蓝牙 / 远程音量。所有者更换与暂存手势必须闭环释放，目标 HyperOS 实机另验收。
