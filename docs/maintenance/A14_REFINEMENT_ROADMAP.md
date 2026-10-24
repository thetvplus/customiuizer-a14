# A14 当前精修状态

本页只维护当前决定、未决问题与证据入口。任务认领和交接用 [AGENTS](../../AGENTS.md#git-与协作) 及 GitHub，不在这里复制分支数据库或开发流水账。

## 事实来源

| 范围 | 权威入口 |
| --- | --- |
| 平台、API 与运行契约 | [COMPATIBILITY](../../COMPATIBILITY.md)、[ARCHITECTURE](../../ARCHITECTURE.md) |
| 功能与进程 | [Feature catalog](../../app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature)、[矩阵 JSON](../rom-intelligence/A14_PROCESS_MATRIX.json) / [CSV](../rom-intelligence/A14_PROCESS_MATRIX.csv) |
| 页面、偏好与搜索 | `app/src/main/res/xml/prefs_*.xml`、构建生成目录、[语义目录](../../feature-semantics/a14.json)；记录数不是已验收功能数 |
| 当前行为 | [功能目录](../features/README.md)、[遗留语义](LEGACY_SEMANTIC_CALIBRATION.md)、[退役决定](A14_FEATURE_RETIREMENTS.md) |
| ROM / DEX | [DexSQL](../rom-intelligence/DEXSQL.md)、[SQL 查询集](../../tools/dexsql/README.md)、[M03 合同](../rom-intelligence/M03_NOTIFICATION_CONTRACT.md) |
| 发布 | [RELEASE](../RELEASE.md)、实际 GitHub Release；Changelog 保存发行事实 |

生产 main 基线为 `3e0709f3234ed942d906487370547ec0136c7531`，源码版本保持 r14.25.3 / 248；同名正式版已发布。2026-10-24 的后续成果统一在 `codex/a14-consolidated-20261024`，尚未合并 main 或形成新发行。

整合安全检查点为 `c9843589416efdbeba2d0f6d651c1c9e136e9fef`：已通过 Fast 专项、Full、工具测试及差异检查并保存远端。它保留所有原分支中的有效源码、测试、查询和详细证据记录；最终 HEAD / CI 从 Git / GitHub 读取，不另维护副本。

## 已整合的当前工作

| 范围 | 当前实现与入口 | 验证边界 |
| --- | --- | --- |
| 维护与隐私 | `MaintenanceJournal` 在特权操作前可靠保存精确原状态；严格解码、回读、补偿及私有恢复记录排除备份传输 | 故障注入已覆盖；进程中断和实际设备恢复待验 |
| 通知 M03 | 点击事务限定原生 owner；Heads-up 延迟任务、通知着色和 Count / Fold 保留原生生命周期；退役 system_server 全局频道保护绕过 | 源码 / CN-TW 静态证据和回归已齐，实际频道、认证、列表绘制待验 |
| 共享手势 M05 / M07 | `447aeb1` 只释放本 runtime 的令牌，结束事件的 effect 异常也释放；不再全局清除状态栏正在进行的手势 | owner / 异常回归已纳入 |
| 波形 | `7bb9a0b`、`4f3ef70` 保留真实幅度、弱高频与衰减，默认轻量，实时保持实验开关和身份 / 失败释放 | 历史有限波形观察不能替代当前 APK 的路由 / 性能验收 |
| 音乐直接上岛 | `1688e7a`、`dbefba6`、`5a1d1d4`：显式选中 App、标准全屏 CLOSE_TO_HOME、native 实际采用且运行时才确认 | 默认关闭；原生窗口轨迹、图标恢复及 ART 私有接口待验 |
| 通知与媒体安全 | `0ab7a975`：独立 Keyguard 门禁、Row / Sbn / binding 事务、首份在途媒体读取失效；普通媒体返回保留 750 ms | 复现与修复回归已纳入；本批设备未验收 |
| 状态栏 / Insets | `0a872d9` 静态证据，`451dd52` 修复父窗口尺寸更新、最新原生基线、属性所有权、detach / 重附着和退化 frame | Host 缺陷已修复；不能据此宣称微信实机回弹已消失 |
| Launcher 图标 | `5f9845c` 保存 native 隐藏 / 恢复机制和查询；未增加推测性恢复 Hook | 持续消失仍是 P1 待实机问题，见下文 |
| Dock / 发布 | 既有 Dock 布局、原生动画及材质合同保持；发布使用 `3e0709f` 的两公开资产、签名 / 来源证明与历史恢复合同 | 不回退主线新实现；此次整合不发布 |

### 通知与媒体

- Keyguard 的 showing / going-away / fading / bouncer / requested / upcoming / dreaming 与状态栏回调不是同一状态。接管前后重新校验，未知或异常拒绝呈现；复用 native Callback，随 safety owner 释放。
- 只有当前通知、Row、entry、binding 及成功插入的窗口都有效时才提交。未提交失败恢复自己的 lease / bit / binding；提交后的 native unbind 失败不重放提醒。旧同 key 回调不能清新 Sbn，同毫秒更新也失效旧点击。
- `unbindHeadsUpView` 的 flag 4 表示内容 freeable / rebind，不能推断普通或锁屏通知被删除。NMS 记录、Pipeline Entry / Row、隐私过滤和真实绘制必须分别观察。
- 直接 App / Home 切换会失效在途首份媒体读取；native owner 丢失时清媒体。普通路径保留 750 ms，可选 native-return 只在有效确认后使用其单次短延迟，不能让旧快照复活。

### 状态栏几何

每个 View 的 Binding 持有最新观察到的原生值与模块最后写入值；外部后续写入不被释放逻辑覆盖。成功执行的原生 `PhoneStatusBarView.updateStatusBarHeight()V` 也捕获新基线，覆盖“新原生值恰好等于模块上次写入值”的边界。

父窗口监听随实际 attach 绑定，失效 / 换窗口先释放旧 owner。位移合并原生与用户值后统一裁定，不累加；默认配置不清空未持有的 translationY / gravity。`InsetsSource.setFrame` 两个重载的非正宽高原样 proceed，有效 client-hidden frame 仍按原合同调整。H2 / H3 / DecorInsets 缓存、FeatureRegistry 与动态岛所有权不变。

Host 观测确认父窗口 129→60、原生高度 80→模块 129→ROM 96、位移 7 加用户 ±5 / 0、退化 frame 等缺陷；测试驱动完整布局及真实父事件，另核对相同输入不重复写 LP。这些是模型输入与写入计数，不是设备轨迹或性能收益。

### Launcher 图标所有权

**已确认隐藏与恢复机制，尚未确认持续消失根因。** 图标随音乐岛消失 / 恢复是用户观察，没有同一过渡的 View、绑定和动画时间线，不能证明因果，也未排除父级裁切、Drawable、重绑定或其他模块。

历史 Launcher：`com.miui.home` / `RELEASE-4.39.30.8604-08262248`，SHA-256 `a6546d51d9220039ed7ac143de249e9014202cfafeafd715237eb49f8f5a3f7b`；CN / TW 参考样本与历史实机原件同哈希。历史 SystemUI：`5d8f2fe0b65d8a1a947b4280f8053b524f8c5de73f48a74f8792d415ae76e513`。这不是当前手机 APK / split 或配置读回。

| 路径 | 已核实的 native 所有权 |
| --- | --- |
| 打开 / 返回 | `WindowAnimParamsProvider` 的开 / 关 listener 修改目标内部图像 alpha；`ShortcutIcon.onBackAnimStart()` 另有 INVISIBLE 路径，需分开采样 |
| 正常完成 | `WindowElement.onFinishCompleted()` / native end → `resetFloatingIcon(false)` → 当前目标 `showIcon()` → recycle |
| 普通取消 | `WindowElement` cancel listener 调用 `recycle(false)` → `FloatingIconView2.finishImmediately()` 恢复；其自身空 end 不是丢失清理的证据 |
| Shell / merge | Shell cancel 和 merge 仍有后续 owner；新动画仍运行时不能提前恢复图标 |
| 换目标 | `initTargetView` / `replaceFloatingIconViewContent` 先清旧目标再交接；Looper 同线程立即执行，不因出现 lambda 就判断竞态 |
| 待证候选 | 跨 Looper 排队期间换 target / 同 View 换绑，尚无真实动画 / binding 身份证据，不新增 generation 层或恢复 Hook |

旧启动动画开关改变原生注册选择；FixAnim 改 spring stiffness；图标缩放与 native floating 落点使用不同坐标口径。这些可指导 A/B，不能单凭静态差异归因持续隐藏。Dock 装饰没有写 Workspace 图标 alpha / visibility；原生焦点通知 IPC 可选择合成状态栏 target，模块动态岛未调用该协议，仍须采样真实目标类。

若将来确需恢复保护，必须依托当前可见 Workspace、同一 ItemIcon / 绑定和 native 动画身份，确认无 floating / back / drag / folder / edit owner 后才恢复。禁止全局扫描、固定延迟、周期刷新或抢回新动画仍持有的图标。

## 待实机验收

本次整合未连接、安装、改设置或重启设备；下列内容保持待验。先记录真实 ROM fingerprint、APK / split、安装 revision 和配置，保存设置，串行做单变量对照，结束恢复并读回。缺少设备不能标为 PASS。

| 优先级 / 范围 | 最小验收闭环 |
| --- | --- |
| P0 通知 | 接管开 / 关：完全解锁、锁屏、AOD、接管中锁屏与立即解锁；敏感 / 应用锁、DND、更新撤回重发；分别读 NMS、Entry / Row 和绘制；列表点击 / 挑战 / 展开 / 清除 |
| P1 Launcher 图标 | Workspace 直开播放器→Home，岛关 / 开、播放 / 暂停、正常 / 快速返回；消失后持续留 Home 再自然移除岛；随后才扩到入口、旧动画、FixAnim、缩放、Dock 和文件夹 |
| P1 状态栏 / Insets | 微信图片 / 视频全屏进退，补相册、网页 / 视频、旋转、导航与岛展示；T0–T5 记录 server frame、client Insets、native 高度和实际内容位置，不以 Host 修复直接归因微信 |
| 原生音乐返回 | 选中 / 未选中播放器、关闭 / 简化、手势中断、快速切换、横屏 / 分屏 / 小窗、会话 / 用户替换；实际终点、浮动图标恢复及一次确认；普通路径继续 750 ms |
| 波形 / 输入 | 当前用户 UID / 唯一会话和音轨、权限 / AppOps、蓝牙 / 远程 / offload、隐藏 / 暂停 / 切会话释放；真实挖孔、字号 / 密度、慢短上滑与取消、窗口外原生下拉 |
| Dock / 音量 / 维护 | 连续拖拽、编辑 / 文件夹 / 最近任务、壁纸 / 深色、材质与裁切；插件重建、无障碍超时、振动 / 高音量确认；维护进程中断及准确恢复 |

图标诊断至少关联 ItemIcon / binding、内部图像和父链的 alpha / visibility / clip / Drawable，以及 WindowElement / floating / mAnim、target、running / merge / finish。若需临时诊断，仅 Debug、指定目标和离散事件使用有界单调时间日志，取证后移除；不每帧记录或采集私有标题。

性能另做同设备、同负载 A/B：CPU / GPU、线程、PSS / heap、帧时间、分配和日志频率。实时波形与轻量、Dock 材质分别测，不将静态预算、Host 数值或旧 APK 结果换算成当前设备收益。

## 下一步

优先完成上表的通知安全、Launcher 图标与状态栏设备闭环；复用已完成源码批次，不重新逐开关扫描。M00 / M01 目录校准、M02 声音振动、M03 通知、M05 / M07 手势、M06 Dock、M08 / M10 维护和设置已有回归；M04 锁屏 / 应用锁、M09 其他宿主的未覆盖行为按具体 owner 与证据另立任务，不凭旧计划推广重构。

保留 PrefMap 原子快照、PreferenceBootstrap、Feature 注册、ProcessRouter、ResourceHooks、按 ClassLoader 有界反射缓存及既有 owner scope。封面合并、缓存扩容、Baseline / Startup Profile 等只是候选，需具体复现或测量和独立回退单位；当前没有全面改造依据。

## 证据恢复

[整合检查点的完整记录](https://github.com/thetvplus/customiuizer-a14/blob/c9843589416efdbeba2d0f6d651c1c9e136e9fef/docs/maintenance/A14_REFINEMENT_ROADMAP.md)保留历史 ROM / 安全合同、完整目标描述符、模型失败、样本身份、既有设备边界和各批回退提交。需要原文时用 `git show c9843589416efdbeba2d0f6d651c1c9e136e9fef:docs/maintenance/A14_REFINEMENT_ROADMAP.md`；不把它重新复制成阶段文档。

原始 ROM / 查询 / decode / 日志 / mapping 仍在本机忽略的 `.di-evidence/` 或仓库外，包括 `m03-20261023`、`island-native-compat-20261024`、`launcher-icon-ownership-20261024`、`statusbar-fullscreen-20261024` 和状态栏工作树的 `statusbar-fullscreen-fix-20261024`。它们未上传，重做结论时须重新核对工具、样本和安装状态；公共定位合同继续维护在 M03 文档及现有 SQL。
