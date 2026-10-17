# 优化、升级与清理门禁

行为、兼容性和稳定性优先。成熟热路径基础设施没有目标 ROM 证据时保持现状；完整验证流程见 [DEVELOPMENT.md](DEVELOPMENT.md)。

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

Host JVM 分配、独立 ART probe、R8 后 APK 与真实进程 PSS / CPU / 帧率是不同口径，不能相互替代。性能收益不稳定或只见于未混淆实验代码时，不宣称运行时提速；是否保留升级还应依据兼容性、新能力与维护成本，而不是仅以性能决定。

配置镜像必须检查 `commit()` 结果。Service SDK 会先更新本地缓存再提交 Binder，失败后的空差异不能当作送达确认；已有一次重试不扩大为轮询，提示保留到新连接的完整同步。

## 静态预算

```powershell
python tools/check_hotpath_alloc_budget.py --check
python tools/hook_body_prefmap_scan.py --check
```

第一项识别 `MethodHook` 中的集合、File、反射、Parcel 等可疑模式；上限见 [HOTPATH_ALLOC_BASELINE.json](../tools/HOTPATH_ALLOC_BASELINE.json)，只能下降。第二项禁止新增回调内 `MainModule.mPrefs` 读取，基线为零。二者是源码回归门禁，不是实际运行时分配次数，也不覆盖所有回调类型。

热路径新增逻辑避免重复反射、磁盘 I/O、DexKit、同步 Binder、Regex 编译、无界容器和日志洪泛。使用已有不可变快照、原子或有界状态；不要为微优化引入框架或后台任务。

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
- 备份对照覆盖最大允许 payload、严格格式 / CRC、输入所有权与可变集合；避免只测正常小文件。
- 资源表压缩比较完整 AAPT2 dump，并在 Android 14 上检查资源名、ID、类型、值、密度、语言、day/night 与缺失项。隔离 compact 效果时，先对 R8 转回 binary 的资源包执行 `aapt2 optimize --shorten-resource-paths`，再对该输出执行 `aapt2 optimize --enable-compact-entries`；比较两份完整 dump，避免把路径缩短也计为 compact 收益。当前处理依赖固定 AGP 的内部 `OptimizeResourcesTask`，声明输入标记并在签名之前处理该任务自己的输出；公开 artifact API 没有等效的 optimized resource package 入口，继续保留。升级 AGP 必须复核无缓存双构建、mapping、资源 dump、UP-TO-DATE / FROM-CACHE 输出和最终 APK 对齐；参考 [AAPT2](https://developer.android.com/tools/aapt2) 与 [Android 14 资源格式](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/libs/androidfw/include/androidfw/ResourceTypes.h)。
- 当前缓存、monitor、FeatureRegistry 和 StrongToast 基础设施保持已有边界；“可以缓存”或静态模式命中不足以构成缺陷。

单次审计的旧分支、测试总数、试验包大小和中间 PASS 不作为当前状态文档；发行变化写 CHANGELOG，取证方法和仍有效约束保存在本页及 [兼容矩阵](../COMPATIBILITY.md)。
