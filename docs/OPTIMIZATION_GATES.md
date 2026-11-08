# 优化、升级与清理门禁

工程取向见 [AGENTS](../AGENTS.md#产品与取舍)，验证范围与停止条件见 [TESTING](TESTING.md#执行分级)。本页维护优化方法、静态预算与测量口径；当前成本判断只见[原路线图](maintenance/A14_REFINEMENT_ROADMAP.md#全局输入图像与资源成本审计2026-10-30)。

## 关键路径优化原则

采用 **Predictive Preparation + Lazy Activation + Incremental Execution（提前准备、按需激活、增量执行）**。先判断能否取消整段无效工作或复用系统能力，再按真实频率、单次成本、持续占用和用户影响决定是否优化；没有收益依据或必须不断叠加机制时停止。

1. **提前准备**：在所属进程、实际 ClassLoader 和依赖已就绪的安全生命周期解析稳定成员；明确即将使用时准备轻量资源。不能提前安装 Hook、阻塞首帧或全量预载；临时未就绪保留既有重试边界，失败沿用原生回退。
2. **按需激活**：功能开启且满足实际条件才创建监听、采样、解码与任务。不可见、遮挡或不活跃时停止无效计算、采样和绘制，保留必要业务事件及有限交接；停用、替换、配置失效与 detach 按 owner 释放。不以预热增加永久监听、轮询或无界缓存。
3. **增量执行**：相关输入变化才重算或提交属性 / 布局 / 绘制。失效覆盖来源 / 版本、owner / ClassLoader、尺寸、字体、主题与配置，异步结果只交给仍有效的 owner。视觉相同不能跳过安全核验、独立事件、期限、原方法调用和释放。

实施前明确触发、频率、移出的工作、准备时机、所有者、容量 / 保留时间、失效 / 取消和回退单位。优先既有快照、租约与属性比较；保持核心功能、布局、动画物理和交互语义。有界内存、代码或成熟依赖可换取有证据的收益，按宿主进程分别评估。

## 改动与证据

- 保持参数、返回值、异常、`chain.proceed()` 时序 / 次数、单次安装及释放；回归应能识别错误行为。
- 记录前后输入、可复现操作和实际结果；只有提出性能结论时才要求对应采样、单位与噪声。
- 主机可证明调用 / 对象 / 任务次数、数值误差、取消和发布语义；Host JVM、独立 ART probe、最终 R8 APK、真实 PSS、CPU / GPU、帧率与功耗不能互相替代。
- 量化结果不支持优化目标时，简化或回退仅为优化新增的机制；未测范围保留待验。
- 安全、兼容、诊断或维护本身也能支持升级，不要求先证明提速；仍须明确成本、生命周期、回退和相应回归。

配置镜像须检查真实 `commit()` 结果。Service SDK 先更新本地缓存再提交 Binder，失败后的空差异不能当作送达；保留每连接一次的既有重试，不扩为轮询。

## 静态预算

```powershell
python tools/check_hotpath_alloc_budget.py --check
python tools/hook_body_prefmap_scan.py --check
```

前者识别 MethodHook 中集合、File、反射、Parcel 等模式；[总量及逐文件上限](../tools/HOTPATH_ALLOC_BASELINE.json)只能下降，缺失 / 非法基线失败，覆盖基线不允许抬高上限。后者禁止新增回调内 MainModule.mPrefs 读取，基线为零。二者是源码回归门禁，不是运行时分配计数，也不覆盖所有回调类型。

新增分配须提供具体位置 / 模式、频率、owner / 上限、净收益测量、行为回归与回退，再审查是否需要位置限定的例外；当前无可执行例外配置。不得整体抬高预算、挪用其他文件额度或关闭测试。危险 I/O、同步 Binder、热路径反射、无界缓存和生命周期保护不可豁免。

## 轻量测量

复用 HookDiagnostics 的有界故障诊断与 Android Trace / Perfetto、Simpleperf、分配 / GC、PSS / Native Heap / Graphics、帧时间工具。生产不保留开发采样或性能日志，长期诊断放独立源码集，不靠 R8 移除。

使用相同场景的基线 / 候选，记录必要的构建、APK、ROM、开关、实际刷新率、热状态、操作窗口、样本与噪声。原始数据私有保存；自动化 helper、截图和层级抓取不得混入模块性能采样。没有有效对照不判回归通过，差异小于噪声保持不可判定。设备入口与有界清理见[自主验证](AGENTIC_VERIFICATION.md#缺陷注入与性能闭环)。

| 目标 | 可复用的比较方法 |
| --- | --- |
| Hook 参数、输入与偏好 | 实际适配器 / 状态机的调用计数，包含参数改写、原生异常、fatal 和取消；关键断言放在被测回调外，避免异常隔离吞掉失败 |
| 设置列表 / 搜索 | 固定输入与扩大集比较稳定分组、状态读取、Row / 元数据复用和陈旧结果隔离，不设脆弱 Host 耗时阈值 |
| 备份 | 最大允许 payload、严格格式 / CRC、输入所有权、可变集合和旧格式兼容 |
| 图像 / 波形 / Dock | 需求切换、可见性、取得 / 释放、稳定帧不重复工作；设备再测 UI / RenderThread / GPU，租约数不换算 CPU 或续航 |
| AAPT2 资源压缩 | 先 shorten-resource-paths，再仅对其输出 enable-compact-entries；比较完整 dump 的名字、ID、类型、值、密度、语言和 day/night，避免混入路径缩短收益 |

历史 HookArgumentAllocationTest 的 JDK 25 四参数对照中 before / after 各少 32 B/次，只计适配器数组复制，不含 native List / JNI；不是 ART、PSS 或帧率证据。该实验源码、摘要和 JUnit 私有归档，不作为当前 JDK 27 的后测。

当前资源压缩依赖固定 AGP 的内部 OptimizeResourcesTask，在签名前处理自身输出并声明输入；公开 artifact API 尚无该工程已采用的等效入口。升级 AGP 时复核无缓存双构建、mapping、完整资源 dump、UP-TO-DATE / FROM-CACHE 和最终 APK 对齐。格式依据：[AAPT2](https://developer.android.com/tools/aapt2)、[Android 14 ResourceTypes](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/libs/androidfw/include/androidfw/ResourceTypes.h)。

## 删除与 APK 体积

删除前按 [AGENTS](../AGENTS.md#产品与取舍)检查动态入口及实际消费者，保留旧备份解码、ROM ABI fallback、native unwind 和必要 consumer rules；没有文本引用不足以证明无用。

最终体积比较使用 [apk_size_report.py](../tools/apk_size_report.py)，按 DEX、资源表 / 文件、native、Manifest 与 ZIP 条目归因：

```powershell
python tools/apk_size_report.py before.apk --out before.json
python tools/apk_size_report.py after.apk --out after.json
python tools/apk_size_report.py before.json --compare after.json --apk-budget-kb 100 --dex-budget-kb 50
```

预算按绝对差值判断，增加或减少超限都失败。多变体与显式源码来源使用 [apk_size_delta.py](../tools/apk_size_delta.py)，ZIP 内容可重复性使用 [apk_semantic_diff.py](../tools/apk_semantic_diff.py)。转递类已被 R8 清掉时，排除依赖未必缩小 APK；核对 Startup / ProfileInstaller 的 Manifest / profile 输入，保持 DEX / native 直接映射布局。
