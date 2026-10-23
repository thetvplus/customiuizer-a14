# A14 功能退役与偏好清理

本页记录 2026-10-11 批准的产品减法。目标证据来自 Xiaomi 13 / fuxi、HyperOS `V816.0.7.0.UMCTWXM`、Android 14 / SDK 34；下线现有实现不等于证明系统能力已从全部 HyperOS 1 ROM 消失。完整审计及 ROM 原件保留在仓库外。

## 退役范围

下表键省略存储前缀 `pref_key_`。节点、专用翻译、语义条目及相关安装路径同步退出当前目录。

| 功能 | 键 | 原因 |
| --- | --- | --- |
| 浮动通知不自动隐藏 | `system_betterpopups_nohide` | 必需旧类和方法无定义，无备用路径 |
| 浮动通知自动隐藏延迟 | `system_betterpopups_delay` | 必需旧字段不存在，无替代安装路径 |
| 简约折叠视图 | `system_minimalnotifview` | 唯一 Hook 的旧 StatusBar 类不存在 |
| 桌面去除启动动画 | `launcher_nozoomanim` | 两个目标方法不存在 |
| 通知中心快捷应用 | `system_shortcut_app`、`system_shortcut_app_user` | 独立替换分支的目标方法不存在 |
| 录屏隐藏状态栏 | `system_hidestatusbar_whenscreenrecord` | 入口已隐藏，没有生产读取或 Feature |
| 磁贴动态颜色 | `system_cc_tile_enabled_color_usemonet` | 入口已隐藏，没有生产读取 |
| 锁屏图标左移 | `system_statusbar_icons_atleft_onkeyguard` | 入口已隐藏，没有生产读取 |
| 内存统计 | `various_memorystats` | 入口已隐藏，没有点击处理 |
| 使用统计 | `various_appusagestats` | 入口已隐藏，没有点击处理 |
| 通知记录 | `various_aospnotiflog` | 入口已隐藏，没有点击处理 |

磁贴背景色和图标颜色直接依赖可见的 `system_cc_tile_enabled_color` 开关。截图隐藏状态栏、普通图标排列、运行中的服务、电池优化及 AOSP 通知入口保留。通知中心日历 / 时钟替换、其他浮动通知和通知频道设置保留；其他 Feature ID 的数值不重新编号。

## 局部裁剪与 ROM 条件

- “修复动画”只移除旧 RectFSpringAnim 字段分支，保留 `SpringAnimator.getSpringForce(FFF)` 和功能入口。
- 锁屏音乐封面移除失效的 `clearCurrentMediaNotification` 辅助 Hook，保留主更新路径和有效清理契约。
- 网速隐藏低速、阈值、隐藏 B/s 在原生样式下禁用编辑，保留存储值；详细样式 2 / 3 恢复编辑。切换样式涉及 Hook 的安装 / 退出时仍需重启 SystemUI；已安装详细 Hook 的格式子值通过观察者更新。
- 国际版隐藏远程检测拦截入口并禁止安装该 Feature；国内版保留原查询和实现。2026-10-11 Global GuardProvider APK SHA-256 为 `1b767096d88e61b51e1f5db928eb963071ba851f47da0f20965ce262e31edeb6`，生产 DexKit 查询未命中。该样本的 user 0 已卸载状态没有改变，也不作为国内版验收证据。

## 升级与备份

`CurrentPreferenceContract.CONTRACT_REVISION = 2`，使已完成 revision 1 的用户再次执行一次性清理。上述键及快捷应用派生键进入弃用集合，旧备份恢复时过滤，旧值不能重新进入当前偏好目录。有效设置的值保持原样。

StrongToast 旧隐藏开关仍先迁移为现代 mode=2，再执行弃用判定；旧位置键保持弃用，不恢复 Bottom 入口，不删除受限旧备份解码器。当前框架边界统一遵循 [兼容矩阵](../../COMPATIBILITY.md) 的 API 102 基线。

## 2026-10-23 产品复核

本轮复用已完成退役，不重复删除或重编号。M03 已移除全局通知频道保护绕过和失效 system_server 安装身份，保留旧偏好与备份兼容；目标与具体保护分支见 [M03 ROM 合同](../rom-intelligence/M03_NOTIFICATION_CONTRACT.md)。这属于已实施的安全收口，不能据此宣称所有 ROM 的通知能力都失效。

新增评估候选仅为动态岛的实验性实时波形：当前路径依赖隐藏的媒体 UID / 音轨身份、录音权限和唯一活动会话，权限拒绝、offload 或身份不明时只能退回轻量模式。它的维护成本有明确实现依据，但相对轻量模式的设备收益与资源对照仍缺失，故保留试验开关和失败释放，不删除功能、不提升为默认。详见[动态岛说明](../features/island/README.md)与路线图设备记录。只有实际使用价值和可重复测量支持时才继续投入。

未使用资源、依赖和工具须结合最终 R8 usage / mapping、Manifest、XML、反射字符串、稳定 Feature ID 与备份格式复核；本轮没有仅按引用数删除类、资源或依赖。Global GuardProvider 的 user 0 未安装状态也不构成删除国内版实现的授权或依据。

## 验证边界

验证应覆盖退役键的源 XML、搜索、生成目录、备份恢复与 revision 1 升级，磁贴颜色依赖，日历 / 时钟保留和网速样式切换。Fast / Full、Python 工具测试与 diff 检查属于源码和构建证据；最终 APK 的目标设备操作与日志另行记录，不能由本页推断全部功能已通过实机验收。
