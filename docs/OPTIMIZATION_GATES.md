# 优化与清理门禁

行为、兼容性和稳定性优先。成熟热路径基础设施没有目标 ROM 证据时保持现状；完整验证流程见 [DEVELOPMENT.md](DEVELOPMENT.md)。

## 每项改动的证据

1. 标明路径与所有者：安装、偏好更新、UI 绑定还是高频 Hook。
2. 建立可复现的前后对照，说明输入、运行环境、采样、单位与噪声。
3. 保持参数、返回值、异常、`chain.proceed()` 时序/次数、安装一次状态及释放规则。
4. 选能识别错误行为的契约 / 回归测试，并记录完整门禁。
5. 明确回退单位、数据格式兼容性与仍未验证的设备范围。

Host JVM 分配、独立 ART probe、R8 后 APK 与真实进程 PSS / CPU / 帧率是不同口径，不能相互替代。收益不稳定或只见于未混淆实验代码时，保留现有实现。

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

保留用户旧备份解码、ROM ABI fallback、原生 unwind 信息和必要 consumer rules。转递库的类若已被 R8 清掉，排除该库未必降低 APK；Startup / ProfileInstaller 的 manifest 与 profile 输入也必须核查。保持 DEX / native 直接映射所需 ZIP 布局。

## 保留的测量方法与维护决定

- 应用列表以固定数据对照已选项稳定分组和每项状态读取次数；搜索以分配计数验证准备字段的效果，避免脆弱耗时阈值。元数据准备与四类适配器接入属于同一回退单位。
- 备份对照覆盖最大允许 payload、严格格式 / CRC、输入所有权与可变集合；避免只测正常小文件。
- 资源表压缩比较完整 AAPT2 dump，并在 Android 14 上检查资源名、ID、类型、值、密度、语言、day/night 与缺失项。当前处理依赖固定 AGP 的内部 `OptimizeResourcesTask`，升级 AGP 必须复核；参考 [AAPT2](https://developer.android.com/tools/aapt2) 与 [Android 14 资源格式](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/libs/androidfw/include/androidfw/ResourceTypes.h)。
- 2026-10-09 的 fuxi 独立 ART 对照未证明 DexKit 2.3.0 或更高 JVM 输出有稳定收益，当前保留 DexKit 2.2.0 与 JVM 17。原始采样留在仓库外，历史结论不替代后续版本评估。
- 当前缓存、monitor、FeatureRegistry 和 StrongToast 基础设施保持已有边界；“可以缓存”或静态模式命中不足以构成缺陷。

单次审计的旧分支、测试总数、试验包大小和中间 PASS 不作为当前状态文档；发行变化写 CHANGELOG，取证方法和仍有效约束保存在本页及 [兼容矩阵](../COMPATIBILITY.md)。
