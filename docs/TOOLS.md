# 开发与诊断工具

日常入口见 [开发说明](DEVELOPMENT.md)，验证职责见 [TESTING.md](TESTING.md)。Python 工具集中在 `tools/`，回归在 `tools/tests/`；Windows 外部签名预检及其回归保留在 `scripts/`。有参数的 CLI 以其 `--help` 为准；`extract_process_matrix.py` 直接运行即可生成矩阵。

| 用途 | 入口 |
| --- | --- |
| 本地编译、测试与源码门禁 | `verify.py`、`check-invariants.py`、`eol_check.py` |
| 偏好、致命错误与源码边界 | `check_observer_key_contract.py`、`check_main_source_cleanliness.py`、`source_hazard_scan.py` |
| Hook 热路径预算 | `hook_body_prefmap_scan.py`、`check_hotpath_alloc_budget.py`，分别读取同目录的预算 JSON |
| 设置分页、搜索与偏好目录生成 | `generate_preference_artifacts.py`，由 Gradle 调用 |
| 当前页面功能地图、文档质量 | `generate_feature_map.py` / `--check`（显式运行，不进 APK 构建）、`check_docs.py`（本地链接 / 锚点 / 图片） |
| CI 范围、安全与发布元数据 | `ci_scope.py`、`ci_contract_scan.py`、`check_ci_portability.py`、`check_release_metadata.py`、`ci_install_android_sdk.sh` |
| 独立缺陷注入、确定性及无写入检查 | `brutal_test_runner.py`、`brutal_test_config.json`；同前缀的 policy / protocol / mutator / apply-check 模块由 runner 使用 |
| 大型辅助数据摘要、筛选与分页 | `query_aux_data.py`，直接读取原文件，见下方查询入口 |
| 当前源码进程矩阵 | `extract_process_matrix.py`，生成 [rom-intelligence](rom-intelligence/README.md) 下的 JSON / CSV 矩阵 |
| Feature catalog 与 Hook 所有权 | `catalog_contract_probe.py`、`audit_hook_ownership.py` |
| 发版准备、正式基线审查、同一 APK 发布 / 文档恢复 | `prepare_release.py`、`signed_release.py`、`release_docs.py`、`release_workflow_contract.py`，见 [发布流程](RELEASE.md) |
| 诊断构建及产物来源 | `build_debug_apk.py`、`build_revision.py`、`verify_apk_provenance.py` |
| APK 归因与预算比较 | `apk_size_report.py`；需要多变体、显式源码来源及 Markdown 报告时用 `apk_size_delta.py` |
| APK 内容与 R8 可重复性 | `apk_semantic_diff.py`，用于按需隔离双构建比较 |
| ROM 样本清单、smali 契约与目标差异 | `rom_inventory.py`、`rom-contract-scan.py`、`rom_target_diff.py` 及测试 fixtures |
| 离线运行日志分析 | `analyze_lsposed_log.py` |
| ROM 持久清单与精确检索 | `rom_catalog.py` 的 `status / locate / search / compare`；[缓存、更新与测量](rom-intelligence/DEXSQL.md#离线-rom-知识库)，复杂 SQL 复用[官方查询集](../tools/dexsql/README.md) |
| Windows 正式签名预检 | `scripts/check-signing-config.ps1`、`scripts/test-check-signing-config.ps1` |

`rom-contracts/` 是当前校验输入；进程矩阵是源码生成物。ROM 原件、APK、mapping、日志、临时评估和设备原始证据放忽略目录或仓库外，不建立重复的状态报告或未接入工具的证据模板。

`rom_inventory.py` 的 APK Manifest 复用 `rom_catalog.manifest_attributes` 的离线读取，JAR ABI 继续直接读取 class；不依赖可选 pyaxmlparser，不启动无效 javap 或无人使用的 JADX 路径。只有 Manifest 读取失败时可使用已有 apkanalyzer 回退。最终 APK 的 DEX / Smali 取证仍使用官方 DexSQL。

离线工具不因缺少 CI 调用就视为无用；移除前核对调用方、输入输出及替代入口。Python 测试保留真实工具行为、生成结果、资源与必要的运行时接入约束，避免重复实现 Kotlin 业务或仅检查测试方法名。

## 辅助数据按需查询

先查摘要，再按键、Feature、目标或源码筛选；不要默认加载完整 JSON。

```powershell
python tools/query_aux_data.py matrix --target SYSTEM_UI --limit 5
python tools/query_aux_data.py rom --target launcher --full
python tools/query_aux_data.py hazards --source SystemUi --limit 5
python tools/query_aux_data.py context --key pref_key_system_strong_toast_mode
python tools/query_aux_data.py context --contains LauncherMusicReturnHooks --device munch
python tools/query_aux_data.py context --contains 动态岛 --fields feature,entrypoints,rom
python tools/query_aux_data.py context --key launcher_dock_background --full
```

无筛选时只输出计数；原数据集筛选后默认最多 10 条，`context` 默认 3 条摘要，可用 `--offset` / `--limit` 翻页，`nextOffset` 为下一页位置。`--fields` 精确选择字段，`--full` 取当前页详细证据；`--summary` 只看筛选后计数。`--contains` 做子串检索，`--key` 同时识别存储键和运行时别名。原数据集只读文件；文件由 Python 读取，Agent 上下文只接收结果。

| 数据集 | 权威输入与维护方式 | 证据边界 |
| --- | --- | --- |
| `matrix` | `extract_process_matrix.py` 生成 JSON / CSV，确定性门禁检查 | 静态 catalog / routing 视图；`featureIdName` / 整数 ID 与源码 Feature ID 对应 |
| `rom` | `rom-contracts/hyperos1-a14-core.json` 与同目录 Schema | 部分源码契约；coverage 按目标条目计数，optional 仍须真实样本取证 |
| `hazards` | `tools/SOURCE_HAZARD_BASELINE.json` | 已存基线，查询不重新扫描或接受新增风险 |
| `context` | Process Matrix、当前源码与 XML、既有 ROM Catalog；`agent_context.py` 仅为此入口的实现模块 | 原设置键精确关联，次级键与词法依赖标为 CANDIDATE；引用实际源码行号，不把候选调用图或 DEX 当实机证据 |

`context` 默认读取当前工作区；`--repo-root` 可指定最新有效源码，不能默认为远端 main。
它在既有外部 Catalog SQLite 中按工作区、文件状态及提取器哈希增量保存源码事实，不另建数据库 / MCP。
只枚举 Git 的相关源码文件名并检查状态；热查询不重读未变化的源码，不扫描 ROM。新文件、未提交修改、删除和切换源码会使相应事实失效。
源码 / XML 是事实来源，Matrix 只提供路由视图；`matrixStale` 或 `matrixWarning` 出现时先核对并运行原生成器。
来源可疑或存在保留文件状态的替换时，显式 `context --refresh` 重读源码事实，不扫描 ROM。
`--full` 的 source / callers / dependencies / classLoader / verification 给出生命周期、异常分支和相关测试入口；词法关系未经过编译器解析，必须阅读对应方法，不能自动生成 Hook 或宣称完整调用图。
没有实机验证时保持未测；测试候选不能替代按 [TESTING](TESTING.md#执行分级)选择的必要检查。

ROM 默认只选 fuxi CN 主基准，`--device munch` 或 `--region TW` 按元数据选一个目标；不自动查询整套矩阵。
组件包名与声明进程不一致会标 `processAdmission=NOT_ESTABLISHED`；找到 POCO 类不证明生产路由已接入。
仅在目标已收敛时添加 `--dex-class <完整描述符>` 或 `--dex-method <完整方法描述符>`，可用 `--dex-calls callers/callees` 追加直接 invoke 证据；返回文件哈希与 DEX 来源。
其他情况复用结果里的现有 SQL 入口，再通过官方 DexSQL MCP / Smali 按需深入。`context` 始终不接受跨版本参考输入；显式设计研究只能走[隔离入口](rom-intelligence/DEXSQL.md#跨版本参考隔离)。


云端只检查 ROM 契约结构可运行 `python tools/rom-contract-scan.py --contract rom-contracts/hyperos1-a14-core.json --validate-only`；不读取样本，不证明 ROM 兼容。实际取证入口见 [ROM 检索说明](rom-intelligence/README.md)，旧采样不作为当前已验证数据。
