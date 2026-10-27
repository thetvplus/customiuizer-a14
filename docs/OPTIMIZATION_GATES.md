# 优化、升级与清理门禁

共同优先级以 [AGENTS.md](../AGENTS.md) 为准：稳定优先、体验驱动、性能实证、适度内存换性能、架构按需演进。成熟基础设施可按证据局部升级，风险与验证须匹配；ROM 行为仍须目标样本与实机验收。完整验证流程见 [DEVELOPMENT.md](DEVELOPMENT.md)。

## 关键路径优化原则

统一采用 **Predictive Preparation + Lazy Activation + Incremental Execution（提前准备、按需激活、增量执行）**。沿用户输入、状态提交、布局 / 绘制到首帧定位关键路径，优先交互响应、动画首帧和高频回调；依据实际频率、开销和可复核的重复工作决定是否修改，不为缓存、异步化或预计算本身增加机制。

1. **提前准备**：稳定类 / 成员与配置在所属进程、实际 ClassLoader 和依赖已就绪的安全生命周期解析；轻量资源在明确即将使用时准备，交互读取已有快照。准备不能提前安装 Hook、阻塞首帧或演变为启动时全量预载；临时未就绪保留既有重试边界，失败沿用原生 / 既有回退。
2. **按需激活**：功能开启且满足实际使用条件后才创建业务监听、采样、解码和任务，优先复用原生事件与既有 owner。隐藏、停用、会话替换、配置失效及 detach 按功能合同取消 / 释放；不得以预热增加永久监听、后台轮询、无界缓存或多余常驻内存。
3. **增量执行**：有效结果复用，只有相关输入变化才重算、提交属性或请求布局 / 绘制。缓存键和失效须覆盖真实依赖，如来源 / 版本、owner / ClassLoader、尺寸、字体、主题与配置；异步结果只交给仍有效的所有者。视觉状态相同不代表事件相同，安全核验、独立通知、时钟期限、原方法调用与释放不能被去重跳过。

实施前明确触发与调用频率、要移出的工作、准备时机、所有者、容量 / 保留时间、失效 / 取消、回退及回归入口；优先已有快照、租约与原生属性比较，能用局部条件解决就不新增缓存或调度层。保持现有功能、布局、视觉、动画物理与交互语义。主机测试可验证重复操作次数和变化后的恢复；设备耗时、分配、CPU / GPU、PSS 与功耗按下文[轻量测量](#轻量测量)分别取证，未测不宣称收益。

## 升级试验的依据

安全升级、新 API 探索和开发工具整合不要求先证明性能提升、APK 缩小或已有缺陷。
可以用分阶段兼容性验证判断是否保留升级；安全、可维护性、诊断与新能力本身也是合理目标。
缺少性能收益不构成自动拒绝理由，但产品边界、API 102 最低基线、生命周期、回归与回退单位仍需明确。

## 每项改动的证据

1. 标明路径与所有者：安装、偏好更新、UI 绑定还是高频 Hook。
2. 记录前后版本、输入和可复现验证；提出性能优化结论时再提供采样、单位与噪声。
3. 保持参数、返回值、异常、`chain.proceed()` 时序/次数、安装一次状态及释放规则。
4. 选能识别错误行为的契约 / 回归测试，并记录完整门禁。
5. 明确回退单位、数据格式兼容性与仍未验证的设备范围。

允许为明确收益增加有界内存、代码、类或成熟依赖。记录增长位置、容量、保留时长、所有者、失效 / 取消 / 释放机制以及实际换取的收益；按 system_server、SystemUI、Launcher 或设置 App 分开评估。优先复用现有协程、快照和原生绘制能力，不以技术新旧决定方案。

Host JVM 分配、独立 ART probe、R8 后 APK 与真实进程 PSS / CPU / 帧率是不同口径，不能相互替代。性能收益不稳定或只见于未混淆实验代码时，不宣称运行时提速；是否保留升级还应依据兼容性、新能力与维护成本，而不是仅以性能决定。

配置镜像必须检查 `commit()` 结果。Service SDK 会先更新本地缓存再提交 Binder，失败后的空差异不能当作送达确认；已有一次重试不扩大为轮询，提示保留到新连接的完整同步。

## 静态预算

```powershell
python tools/check_hotpath_alloc_budget.py --check
python tools/hook_body_prefmap_scan.py --check
```

第一项识别 `MethodHook` 中的集合、File、反射、Parcel 等可疑模式；默认总量与逐文件上限见 [HOTPATH_ALLOC_BASELINE.json](../tools/HOTPATH_ALLOC_BASELINE.json)，只能下降。`--write-baseline` 可为新报告创建基线，覆盖已有基线时拒绝抬高总量或任何文件上限；缺失 / 非法基线的检查失败。第二项禁止新增回调内 `MainModule.mPrefs` 读取，基线为零。二者是源码回归门禁，不是实际运行时分配次数，也不覆盖所有回调类型。

需要新增分配时，先说明具体文件 / 回调 / 模式、次数与适用条件、所有者及上限，提交能复核的前后测量、行为回归与回退单位，再审查是否值得设计位置限定的例外。没有净收益数据时保留原门禁；当前无可执行例外配置，不通过整体抬高基线、挪用其他文件预算或关闭测试批准例外。危险 I/O、同步 Binder、热路径反射、无界缓存与生命周期保护不在可豁免范围。

热路径新增逻辑避免重复反射、磁盘 I/O、DexKit、同步 Binder、Regex 编译、无界容器和日志洪泛。使用已有不可变快照、原子或有界状态；框架和异步调度必须服务于明确的功能或体验收益，不能制造持续无效任务。

## 轻量测量

复用 `FeatureInstallMetrics` 的 develop 安装耗时 / ART 分配计数和 `HookDiagnostics` 的有界安装诊断。局部异步、绘制和状态传播实验按需在 develop 抓取 Android Trace / Perfetto、分配 / GC、PSS / Native Heap / Graphics 和帧时间；不新增常驻监控服务，release 不采样也不写性能日志。记录构建 revision、APK 哈希、ROM、开关、操作、样本数与噪声；未测项写“未验证”。

优先场景：SystemUI 安装；Launcher 启动及 Dock 状态切换；锁屏封面生成；可视化运行 / 停止；设置更新传播。主机可控调度测试可证明任务次数、取消和结果发布语义，不能换算 CPU 百分比、PSS 或 GPU 帧率。设备无连接时保留同一场景的验收步骤，不把静态或 Robolectric 结果标为设备通过。

## 删除与 APK 体积

删除前同时检查调用链、XML / manifest、类名字符串、Hook / 反射 / JNI、生成器、R8 / consumer rules、测试和持久化格式。没有文本直接引用不足以证明动态入口无用。

比较最终 APK 的 DEX、资源表、资源文件、native、manifest 与 ZIP 条目；使用 [apk_size_report.py](../tools/apk_size_report.py)、[apk_size_delta.py](../tools/apk_size_delta.py) 和 [apk_semantic_diff.py](../tools/apk_semantic_diff.py) 的帮助参数，不用源文件长度代替发行体积。

单 APK 归因与轻量预算比较统一使用 `apk_size_report.py`：

```powershell
python tools/apk_size_report.py before.apk --out before.json
python tools/apk_size_report.py after.apk --out after.json
python tools/apk_size_report.py before.json --compare after.json --apk-budget-kb 100 --dex-budget-kb 50
```

预算按绝对差值判断：增加或减少超过预算都返回失败，预算内返回 0。需要多变体、显式源码 provenance 与 Markdown 报告时使用 `apk_size_delta.py`；ZIP 内容可重复性仍由 `apk_semantic_diff.py` 验证。工具入口见 [TOOLS.md](TOOLS.md)。

保留用户旧备份解码、ROM ABI fallback、原生 unwind 信息和必要 consumer rules。转递库的类若已被 R8 清掉，排除该库未必降低 APK；Startup / ProfileInstaller 的 manifest 与 profile 输入也必须核查。保持 DEX / native 直接映射所需 ZIP 布局。

## 保留的测量方法与维护决定

- 应用列表以固定数据对照已选项稳定分组和每项状态读取次数；搜索以分配计数验证准备字段的效果，避免脆弱耗时阈值。元数据准备与四类适配器接入属于同一回退单位。
- Hook 单参数读取复用 `BeforeHookCallback.getArg`，after 也委托同一入口；36 处现有单项读取改用该入口，条件改写仍在实际写入时才物化参数。`MethodHookBoundaryTest` 覆盖共享改写数组、空值、调用次数、跳过与异常恢复；关键结果在回调外断言，避免被普通异常隔离吞掉测试失败。helper、调用点与回归为同一回退单位，不新增缓存、线程、持久化格式或资源所有者。
- `HookArgumentAllocationTest` 在 JDK 25.0.4.1 上以真实适配器对照四参数、每组 100,000 次调用、五组中位数：before 为 72→40 B/次，after 为 104→72 B/次，均少 32 B。旧读取复用同一个 List，且两组均保留可观察的 callback，因此只计适配器参数数组复制差异，不含 native List / JNI 成本；不能推断 ART 分配、CPU、PSS、帧率或耗电。2026-10-25 对照基线为 `8d2a204`；源码哈希、JUnit 与验证日志在仓库外 `customiuizer-a14-evidence/runtime-performance-20261025/`，不另建测量系统。
- 备份对照覆盖最大允许 payload、严格格式 / CRC、输入所有权与可变集合；避免只测正常小文件。
- 资源表压缩比较完整 AAPT2 dump，并在 Android 14 上检查资源名、ID、类型、值、密度、语言、day/night 与缺失项。隔离 compact 效果时，先对 R8 转回 binary 的资源包执行 `aapt2 optimize --shorten-resource-paths`，再对该输出执行 `aapt2 optimize --enable-compact-entries`；比较两份完整 dump，避免把路径缩短也计为 compact 收益。当前处理依赖固定 AGP 的内部 `OptimizeResourcesTask`，声明输入标记并在签名之前处理该任务自己的输出；公开 artifact API 没有等效的 optimized resource package 入口，继续保留。升级 AGP 必须复核无缓存双构建、mapping、资源 dump、UP-TO-DATE / FROM-CACHE 输出和最终 APK 对齐；参考 [AAPT2](https://developer.android.com/tools/aapt2) 与 [Android 14 资源格式](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/libs/androidfw/include/androidfw/ResourceTypes.h)。
- 当前缓存、monitor、FeatureRegistry 和 StrongToast 基础设施保留有效契约；按根规则评估有证据的局部升级。“可以缓存”或静态模式命中不足以构成缺陷。
- 被动事件只在替代了无效唤醒、查询或绘制时才构成优化依据；更底层的 Hook、原生组件和回调本身不保证省电。Visualizer 已使用原生 FFT 回调，但仍须按目标布局管理采集租约；展开媒体长岛无波形时关闭 effect 与音轨监听，保留既有最后一帧供有限交接。主机回归验证短→长→短的取得 / 释放与重复元数据不重启，不据租约计数换算设备 CPU 或续航。Dock 通用动画在原调用之后先核验 owner；无 owner 的 120 次主机调用保持原生调用 120 次、参数读取由 120 次降至零，有 owner 与原生异常语义另有回归。这两项分别回退，不新增状态、定时器或持久化格式。
- 组件优先复用原生布局、输入、通知 / 媒体回调、Chronometer 和材质能力，特有五柱与岛体仍使用当前有界 Canvas / 缓存模型。避免为“原生”增加子 View 层级、全局输入 Hook 或音频读取线程的高频 Hook。参考 [Android 自定义 View 优化](https://developer.android.com/develop/ui/views/layout/custom-views/optimizing-view?hl=en)与 [Visualizer 生命周期](https://developer.android.com/reference/kotlin/android/media/audiofx/Visualizer)。借鉴 Telegram 的[目标变化才启动、转场结束停止 invalidate](https://raw.githubusercontent.com/DrKLO/Telegram/master/TMessagesProj/src/main/java/org/telegram/ui/Components/AnimatedFloat.java)及[按组件限制效果](https://raw.githubusercontent.com/DrKLO/Telegram/master/TMessagesProj/src/main/java/org/telegram/messenger/LiteMode.java)原则，不引入其完整动画 / 渲染器，也不覆盖现有保留速度的岛体弹簧。

单次审计的旧分支、测试总数、试验包大小和中间 PASS 不作为当前状态文档；发行变化写 CHANGELOG，取证方法和仍有效约束保存在本页及 [兼容矩阵](../COMPATIBILITY.md)。
