# A14 当前精修状态

本页只维护仍有效的决定、未决问题和必要证据入口；已完成批次与原始历史由 Git 保留。工程约束以 [AGENTS](../../AGENTS.md)、[架构](../../ARCHITECTURE.md)、[兼容矩阵](../../COMPATIBILITY.md)和[测试分级](../TESTING.md)为准。

## 当前维护边界

- 当前有效开发成果包含 API 契约精校与安全修复；不回退旧分支恢复已替代实现。正式版仍通过原签名 / 双仓流程发布，工作分支与未签名 Develop 不冒充正式版。
- 设置使用 Compose，宿主 Hook 保持原生 View / Canvas；保留 API 102 安装 / 原调用次数 / 异常及所有权、Dynamic Island / Dock 几何、G4 曲率和单一材质合同。
- 退役手工语义镜像及其专属审计 / mutation。源码 Feature catalog、路由与进程矩阵、偏好合同、生产隔离、安全检查及实际行为回归继续维护；功能地图与进程矩阵仍有消费者，暂保留生成文件。
- DexSQL、ROM Catalog、SQL 查询与有效缓存完整保留。先查实际源码，只有 ROM 私有接口、Hook 签名或 ClassLoader 不确定时才按需取证；不因任务启动重扫 ROM 或初始化大型索引。

## 待实机验收

- 安全修复后的原生截图开始 / 完成、可见性恢复及授权操作尚待设备收发验收。已确认原生截图应用不共享身份，采用其实际持有的 READ_FRAME_BUFFER 权限；电量预览采用模块签名权限，各自随 owner 释放。
- POCO 文件夹模糊保留两种已确认的原生 ABI；深浅色、壁纸、主题、省电和动态切换矩阵尚未全部实测，不宣称全部 POCO ROM 兼容。
- 输入法返回键、主屏导航、应用管理菜单及跨用户回退以兼容矩阵和实际回归为准；其他机型 / 地区或自行绘制导航按钮仍需对应样本验证。
- 通知首次建窗的代表性主线程 doFrame 为 27.6–33.2ms，尚未通过干预完成归因；继续关注媒体 / 通知交接、快速打断、异常结束与安全窗口，不能仅凭 Host 或录屏 FPS 宣称设备帧率改善。

## 仓库安全待办

- 私密漏洞报告、Dependabot Alerts / Security Updates、Secret Scanning 与 Push Protection 已启用。安全政策、仅安全更新配置及独立手动依赖图工作流需经正常审核进入默认分支后生效 / 首次验证；本次开发整合不合并 main。
- 项目控制的构建 / 测试 Guava 已使用修复版，生产运行时不含完整 Guava。Gradle 9.8.2 内置 Guava 33.4.6-jre 仍在 CVE-2026-102554 公告范围，尚无更新稳定 Gradle；保留 Host 风险，不修改发行包内部文件或固定摘要。
- CodeQL 尚无真实 Kotlin / Java 扫描结果。上游当前支持范围未覆盖本项目 JDK 27 / Kotlin 2.4.21；不降 JDK、不以 none 模式跳过 Kotlin。支持后再做独立 manual build、提取质量和模型验证。
- 历史签名原件、必要发行恢复资产及私有证据继续保留在仓库外；不公开设备标识、日志、ROM、trace、mapping、密钥或完整利用细节。

## 全局输入、图像与资源成本审计（2026-10-30）

八类成本的续审基准如下。A 为确定性工作，B 须先测量，C 为低收益 / 高风险或证据不足；该表不是全部功能均已实机验收的声明。只按新增调用链和证据补查，调用次数、Host 分配及设备 CPU / GPU / 功耗分别报告。

| 等级 / 优先度 / 范围 | 当前成本与证据 | 决定、风险与回归 |
| --- | --- | --- |
| **A / P1，已实施：SystemUI 输入包装** | `StatusBarGesturesHook → statusBarHook.before → getArgs → getArg → getMember`；原拦截入口读整组参数后直接返回，不改变手势状态、消费或业务效果；原触摸入口的只读 `getArgs` 让 API 102 适配器复制数组并走 `proceed(args)`。每个原生事件均触发 | 删除无业务作用的拦截 Hook，权威触摸入口用 `getArg(0)`，不再读 member / 整组参数；仍在原生分发前观察。Host 120 次触摸的整组读取与重传各 **120→0**，native 仍 120 次。覆盖原生结果 / 异常、观察失败隔离、fatal 与事件字段；attach / detach、仲裁器和状态机不变。无新缓存 / 所有者 / 线程 |
| **A / P1，已实施：状态栏与控制中心 DOWN** | `GestureMachine.dispatch → readBrightness → prepared Method.invoke → DisplayManager.getBrightness` 原来每个准备完成的 DOWN 都读取，即使只有音量 / 双击，或控制中心起点超出状态栏 | 在可开始且任一单指 / 多指动作可能调亮度时才读；120 次非亮度会话、120 次无效起点分别 **120→0** 次反射亮度调用。亮度配置仍在 DOWN 读取一次；后续加指可能切到亮度，不能仅依据 DOWN 时的手指数量跳过。保留首个 MOVE 起始值、失败回退、fatal、取消与配置快照；不声称 Host 调用次数就是设备 Binder 次数 |
| **A / P2，已实施：设置应用图标** | `AppDataAdapter / 三类选择器适配器 → BitmapCachedLoader.loadBitmap → getApplicationInfo → getApplicationIcon(String)`；冷缓存每个应用图标再次按包名请求图标，Android 14 String 入口内部再次查询元数据 | 使用本次 `ApplicationInfo` 的原生图标重载，保留 disabled 标志并加入 String 入口的 `GET_SHARED_LIBRARY_FILES`，不缓存包元数据。Host 120 个冷图标的 String 请求 **120→0**，每项一次元数据读取、一次 info 图标加载；暖缓存两者均为零。活动图标优先、应用回退、像素生成、队列、弱目标、缓存容量及交叉淡入不变。缺失 / icon=0 回退和共享库标志有回归；PM 自身缓存会影响实际 Binder 成本 |
| **B / P1：通知调色批处理** | `handle3thThemeColor → AppIconsManager.getAppIconBitmap → NotificationPaletteCache.resolve → WallpaperColors.fromBitmap → Monet ColorScheme`。已有每 Notification 单项弱 Bitmap + generation / uid / pkg / dark 缓存；同通知重复 inflation 已复用，新通知仍各自计算。Row 当前 entry 查 palette，原生 tint=0 恢复保留 | 暂不加共享缓存。对同应用批量新通知、同对象重绑、主题 / 用户切换及同 Bitmap 像素更新，先测图标身份命中、主线程 / 后台归属、调色次数与锁等待；有真实命中后再比较安装 owner 内小容量弱图像缓存。需覆盖并行 inflation、替换 / 失败发布、Row 复用、原生颜色；不能用合成同 Bitmap 的命中率代替 ROM 命中率 |
| **A / P1，已实施：无天气格式时停止订阅 / 查询** | `SystemClockHooks → buildClockText / needsWeatherData → WeatherDataController`；天气开关启用后，原来无论四类有效格式是否使用 `tq` 都注册分钟广播并查询。基线在同一执行体仅接入需求参数的对照中，120 次无需求请求仍取用 ContentResolver / 尝试查询 120 次；关闭后仍有订阅、在途结果仍发布，各有反例 | 复用实际格式选择、隐藏及功能开关，在初始化和现有偏好发布时计算需求，不进入逐帧 / 分钟热路径。无需求时分钟与天气专属屏幕订阅为零，120 次请求的 ContentResolver 取用 / 查询尝试 **120→0**；取消在途发布，亮屏重新启用刷新一次，灭屏等亮屏，重复启用无重复工作。合并天气与时钟构造 Hook（两者同时启用时由两个回调变为一个）；保留初始化 1.8 秒延迟、弱 controller、application Context、跨 scope 查询互斥和缓存字符串。新增状态仅两个 primitive 标志，无新线程 / 轮询 / 缓存。四类格式、CC 原有格式规则、关闭 / 恢复、屏幕和替换回归已覆盖 |
| **B / P2：仍有天气格式但失效 / 不可见 owner** | `weatherInfo` 由四类时钟共享；弱 updateTarget 失效不等于所有时钟停止消费。当前有格式需求且亮屏时仍按原节奏查询。`StepCounterController` 已有 View attach / detach、最后 owner 注销与屏幕取消 | 仍需核实所有实际 View 消费者和 controller 替换 / 销毁时序，确认没有输出后再加明确释放入口；不以 GC 或弱引用为空推断可停用。通知跨对象调色、偏好传播等 B 项保持原取证要求 |
| **B / P2：偏好传播** | `StatusBarGesturesHook.statusBarObserver → configPublisher.publish → GestureConfigResolver.resolve` 对每个偏好通知都解析十个手势字段；其他功能已有按键过滤 / 不可变快照。频率是设置事件，不是每帧 | 先比较单键 / 批量设置的解析与发布次数，再决定精确键过滤 / 可合并发布是否值得。不得合并业务广播、丢失 null 全刷新或改变手势 DOWN 冻结语义；没有设置风暴样本，暂缓 |
| **B / P2：设置列表 / 搜索 / ROM 页面** | `Helpers.getInstalledApps / getLaunchableApps → AppData.prepareForList → 四类 Adapter.ItemFilter`；已有预备小写字段、稳定分组、直接采用工作线程结果、图标有界 LRU + 单在途 + 2–4 可超时线程。过滤仍按每次查询 O(n)，`notifyDataSetChanged` 是 BaseAdapter / ListView 协议；ROM 辅助数据为离线工具，不进入 APK | 不为几百行引入数据库或把 ListView 当 RecyclerView 强改。用固定几百行 + 扩大集比较查询序列、键盘滚动与 PM 时间；仅在确有瓶颈后实验前缀增量搜索。需覆盖查询缩短、Locale / 选项 / 用户改变及陈旧结果隔离 |
| **C / P2：全局内存与生命周期** | 筛查静态 Context / View / Bitmap、Handler / Job、owned preference observers、Hook / ClassLoader 缓存。`ReflectionCache.LoaderState` 有界且 loader 隔离，`XposedHelpers` 成员缓存仍可保留 Class / loader；控制中心旧 lease 失效与 runtime 清理；锁屏封面 owner / generation / 引用释放已实现 | 没有 HPROF dominator、loader churn 或持续增长证据，不机械 WeakReference 化。Launcher 重命名 observer 显式 onDestroy 注销；天气剩余工作单列 B。先做重复重建 / 插件重载后的 retain-chain、PSS / 线程数对照，再改实际保留链；禁止据静态强引用数报告泄漏修复 |
| **C / P2：ROM 能力 / 异常路径** | Launcher 手势设置快照、Controls 几何 / 音频快照、通知 Monet 成员安装期解析、锁屏充电 caller scopes、Feature 单次安装与 loader lease 均已有冷 / 热分离。锁屏 `TapToUnlock` 在 MOVE 前放行；Launcher 前台包查询只在 DOWN，后续编辑 / occupied-cell 查询只在成功双击 | 不删除有 owner / 动态来源用途的查询，不重建 resolver。尚存局部反射与 Throwable 边界，但无目标 A14 重复失败热点，不能仅凭 catch 数量删除或永久负缓存。图标 loader 既有 Throwable 容错是独立失败语义问题，不借性能改动顺手重写。需目标缺失成员 / 错误频率及原生回退证据 |
| **B / P1：资源、通知 / 媒体与 GPU 剩余成本** | Launcher 图标 / 文件夹 blur、锁屏封面、AudioVisualizer、控制中心材质与通知颜色；`getIdentifier` 多在安装 / inflate，通知标题 ID 已惰性保存；封面已有尺寸 / 参数 / generation 失效、单在途 / 取消发布。可见图片处理、文件夹原生 blur、媒体波形仍可能昂贵 | 先采主线程 / RenderThread / GPU 与图像分配；不能因资源名查询命中就建立全局缓存。保留原生材质、G4、封面裁切和波形合同。GPU、动画与视觉改动必须配当前 APK 的设备录屏，本轮没有修改视觉或动画 |

## 验证与证据

有效安全实现的 JDK 27 Full 为 4,255 项应用测试零失败 / 错误 / 跳过，Develop / R8 / fatal lint 与最终 APK 边界通过；Debug Lint 为零错误 / 1,411 条既有告警。这些结果只在应用、构建与验证范围等价时复用，不冒充新设备验收或完整 CodeQL 扫描。

私有证据入口为仓库外 `warning-contracts-20261031/`、`security-20261031/` 和 `island-fps-20261031/`；原始数据不入库。当前资料清理、分支整合与 Skill 精简按独立提交记录，必要验证随实际消费者变化选择，不重复运行全量 CI。
