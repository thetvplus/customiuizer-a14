# DexSQL 开发查询入口

用于开发期 APK / DEX 取证，不参与 Gradle、APK runtime 或默认 CI。
安装、MCP 操作、证据边界及实测结果见 [DexSQL 指引](../../docs/rom-intelligence/DEXSQL.md)。

每个 `.sql` 文件是一组可直接传给官方 CLI 或 MCP `query.sql` 的只读语句：

| 文件 | 输入 | 用途 |
| --- | --- | --- |
| `definition_sources.sql` | 宿主、插件 / split、必要 boot framework | 统一记录 DEX 定义来源；跨目标比较使用完整描述符和样本哈希，不比较局部索引。 |
| `apk_hook_api_definitions.sql` | 最终 R8 模块 APK | 应为零行：检查 compileOnly Hook API 未被打包，Service SDK 不在禁止范围。 |
| `apk_settings_api_refs.sql` | 最终 R8 模块 APK | 检查 Manifest 入口的直接 Hook API 指令引用；结合 mapping、类加载回归及设备启动复核间接路径。 |
| `screenshot_candidates.sql` | MIUIScreenshot APK | 当前 DexKit 子串、排除包和第 5 个参数约束；生产入口要求唯一候选。 |
| `screenshot_callers.sql` | MIUIScreenshot APK | 字符串目标的直接调用者；仍需用 candidates 查询筛选、用 decode 复核。 |
| `guardprovider_candidates.sql` | GuardProvider APK | 同一 DEX、同一方法同时使用两个子串；零结果只约束输入样本。 |
| `background_services_contract.sql` | SecurityCenter / PowerKeeper / GuardProvider / MiuiDaemon / AnalyticsCore | 任务入口与 Job / Alarm / WakeLock 调用；需完整读取 Manifest、decode 分支 / 周期 / 用户开关，调用存在不证明执行或耗电。 |
| `notification_m03_contract.sql` | CN/TW SystemUI、Settings、framework / services 及 miui-framework / miui-services | 通知启动事务、Heads-up、频道 / NMS 与 Count/Fold 的定义、调用和 owner 字段；须完整 decode，交接目标和实机矩阵见 [M03 精校](../../docs/rom-intelligence/M03_NOTIFICATION_CONTRACT.md)。 |
| `island_input_audio_contract.sql` | SystemUI 与 boot framework.jar | 动态岛内部 Insets 监听、Token UID 与音轨会话接口声明；授予权限、AppOps、路由与隐藏 API 实际可用性仍须实机。 |
| `volume_contract.sql` | SystemUI 宿主 + plugin APK | 查询按钮、计时器、超时 / 无障碍、窗口模糊及百分比 owner 声明；宿主亮度与插件音量分别确认来源。宽度资源 ID、默认材质和关闭时序须结合 Smali。 |
| `launcher_dock_contract.sql` | Launcher、必要时加入 SystemUI / framework | Dock 显示、编辑 / 拖拽、几何与材质，以及原图标 / FloatingIconView2 / Remote Transition 的声明和调用；decode 核对实际状态 owner、完成 / 取消与恢复，不另行推进动画或强制显示图标。 |
| `launcher_unlock_contract.sql` | Launcher | HotSeats 解锁参与者、V12 原生准备 / 弹簧 / 重置与属性更新；背景交由同一原生控制器，不能把编辑 AnimationSet 当作解锁链。 |
| `navigation_gestures_contract.sql` | Launcher | 返回区域初始化 / setSize、侧滑停顿、快速切换和输入声明；核对无参初始化、字段重置及配置变化调用者。 |
| `statusbar_layout_refresh.sql` | SystemUI | Insets / 开孔、布局重建、网络订阅与电池回调声明；保留各 owner 的失效与刷新边界。 |
| `statusbar_gestures_contract.sql` | SystemUI | PhoneStatusBarView 的拦截、触摸、handleEvent 与原生通知栏分发；独立于 Launcher 导航链。 |
| `toast_presentation_contract.sql` | SystemUI 与 boot framework.jar | 普通 Toast 的窗口插入 / 回收、旋转重排、安全区与原生 StrongToast 文本模型；decode 核对 text_bitmap 分支、展示门禁、队列和 finishToken，不能只凭方法存在判定兼容。 |
| `island_activity_contract.sql` | 双区域 SystemUI 与 boot framework.jar | 原生主播放器、媒体 / 状态栏 / Keyguard 回调及通知接管字段；decode 复核独立锁屏门禁、bind 提交、Heads-up 内容释放与列表可见性的边界。 |
| `island_music_gestures.sql` | 双区域 SystemUI、plugin 与 boot framework.jar | 待实现音乐手势的 TransportControls / 音量能力和既有音量面板声明、选中会话回调；当前未执行，不据候选查询自动增加 Hook。 |
| `statusbar_height_contract.sql` | 双区域 services.jar 与 framework.jar | InsetsSource、WindowState、DecorInsets 缓存及应用 Insets 消费链；区分挖孔安全区、旧 API 与应用缓存，不据此扩大全局 Hook。 |
| `audio_focus_contract.sql` | 两份适用 ROM 的 services.jar | 声音页焦点 / 首次音量按键的精确声明、状态字段和定义来源；结合 decode 核对语义。 |
| `audio_focus_calls.sql` | 与上述相同的 Dataset | 焦点失去 / 恢复 / 释放与原生栈操作的直接调用；核对放弃焦点和 Binder 死亡路径。 |
| `safe_volume_contract.sql` | 两份适用 ROM 的 SystemUI 宿主 + framework.jar + services.jar | 高音量警告工作线程、owner / 原生门禁、确认接口及服务端重新限幅声明；decode 核对消息 14 与独立 CSD 消息 17，不混入音量插件加载器。 |
| `safe_volume_calls.sql` | 与上述相同的 Dataset | 原生确认、重新限幅、警告分发与待处理音量命令回放的直接调用；消息合并、权限、失败回退和按键是否消耗仍须结合 Smali 与实机。 |
| `audio_haptics_contract.sql` | 两份适用 ROM 的 services.jar + framework.jar | 按应用振动的 uid / deviceId / opPkg 签名、原生缩放调用及 Effect / Segment 实现；核对时长、重复与预制效果，不因旧目标缺失直接退役。 |
| `bootstrap_contract.sql` | SystemUI 宿主及插件 APK | 查询既有初始化 / 插件创建的声明与来源；boot framework 缺失仍是 UNKNOWN。 |
| `api102_bridge_calls.sql` | 最终 R8 模块 APK | 统一统计 setId / getId / replaceHook 的 invoke；结合 mapping 核对原生安装策略及禁止替换，替代旧 setId 单项查询。 |
| `api102_hook_ids.sql` | 最终 R8 模块 APK | 找到三个固定 ID 的指令使用位置；需结合 mapping 和设备回读。 |

修改生产匹配条件或 API 102 入口时同步审查对应查询。查询结果是 ARTIFACT 证据，
不能直接生成生产 Hook、推断实际 ClassLoader 或标记设备功能验收。
SQL 保留 `file_id` 连接键与确定排序；不要将字符串子串搜索改成精确相等。
R8 类合并 / 内联时同时查成员映射；输出类名不能单独证明原安装器归属。

ROM 定位依次运行来源查询、对应功能查询及直接调用者查询，再 decode 唯一候选；
目标差异交给已有 ROM contracts / `rom_target_diff.py`，不另建匹配引擎。
构建产物复核运行来源、两个 `apk_*` 边界查询及 `api102_*` 查询，保存同产物 mapping 的哈希。
这些查询共用官方 CLI 的一次多 SQL 调用或 MCP 数据集，只在相关开发 / 适配任务运行。
