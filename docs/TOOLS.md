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
| Feature 路由、语义与所有权 | `catalog_contract_probe.py`、`audit-feature-semantics.py`、`audit_hook_ownership.py` |
| 大型辅助数据摘要、筛选与分页 | `query_aux_data.py`，直接读取原文件，见下方查询入口 |
| 当前源码进程矩阵 | `extract_process_matrix.py`，生成 [rom-intelligence](rom-intelligence/README.md) 下的 JSON / CSV 矩阵 |
| 发版准备、正式基线审查、同一 APK 发布 / 文档恢复 | `prepare_release.py`、`signed_release.py`、`release_docs.py`、`release_workflow_contract.py`，见 [发布流程](RELEASE.md) |
| 诊断构建及产物来源 | `build_debug_apk.py`、`build_revision.py`、`verify_apk_provenance.py` |
| APK 归因与预算比较 | `apk_size_report.py`；需要多变体、显式源码来源及 Markdown 报告时用 `apk_size_delta.py` |
| APK 内容与 R8 可重复性 | `apk_semantic_diff.py`，用于按需隔离双构建比较 |
| ROM 样本清单、smali 契约与目标差异 | `rom_inventory.py`、`rom-contract-scan.py`、`rom_target_diff.py` 及测试 fixtures |
| 继承功能语义对照 | `legacy_semantic_compare.py`，见 [语义契约](maintenance/LEGACY_SEMANTIC_CALIBRATION.md) |
| 离线运行日志分析 | `analyze_lsposed_log.py` |
| 可选 DEX 深查 | [DexSQL 查询集](../tools/dexsql/README.md)与[取证说明](rom-intelligence/DEXSQL.md) |
| Windows 正式签名预检 | `scripts/check-signing-config.ps1`、`scripts/test-check-signing-config.ps1` |

`feature-semantics/` 和 `rom-contracts/` 是当前校验输入；进程矩阵是源码生成物。ROM 原件、APK、mapping、日志、临时评估和设备原始证据放忽略目录或仓库外，不建立重复的状态报告或未接入工具的证据模板。

离线工具不因缺少 CI 调用就视为无用；移除前核对调用方、输入输出及替代入口。Python 测试保留真实工具行为、生成结果、资源与必要的运行时接入约束，避免重复实现 Kotlin 业务或仅检查测试方法名。

## 辅助数据按需查询

先查摘要，再按键、Feature、目标或源码筛选；不要默认加载完整 JSON。

```powershell
python tools/query_aux_data.py semantics
python tools/query_aux_data.py semantics --key pref_key_system_charginginfo_fontsize --full
python tools/query_aux_data.py semantics --confidence UNKNOWN --summary
python tools/query_aux_data.py matrix --target SYSTEM_UI --limit 5
python tools/query_aux_data.py rom --target launcher --full
python tools/query_aux_data.py hazards --source SystemUi --limit 5
```

无筛选时只输出计数；筛选后默认最多 10 条，可用 `--offset` / `--limit` 翻页，`nextOffset` 为下一页位置。`--fields` 选择原字段，`--full` 取当前页完整证据；`--summary` 只看筛选后计数。`--contains` 做子串检索，`--key` 同时识别存储键和运行时别名。查询只读原文件，不生成缓存、索引或第二份数据。文件由 Python 读取，Agent 上下文只接收查询结果。

| 数据集 | 权威输入与维护方式 | 证据边界 |
| --- | --- | --- |
| `semantics` | `feature-semantics/a14.json`；Schema 与 `audit-feature-semantics.py --validate` 校验 | 提取和人工校准并存，保留逐项 confidence / evidence；UNKNOWN 不是已支持或无需重启 |
| `matrix` | `extract_process_matrix.py` 生成 JSON / CSV，确定性门禁检查 | 静态 catalog / routing 视图；`featureIdName` / 整数 ID 与语义目录的 ID 不同 |
| `rom` | `rom-contracts/hyperos1-a14-core.json` 与同目录 Schema | 部分源码契约；coverage 按目标条目计数，optional 仍须真实样本取证 |
| `hazards` | `tools/SOURCE_HAZARD_BASELINE.json` | 已存基线，查询不重新扫描或接受新增风险 |

`audit-feature-semantics.py --init` 仅用于新目录；已有目录不会被覆盖。需要重新提取时用 `--output <新临时路径>` 生成候选，审查后合入逐项变化，保留排除理由与人工校准，不将候选当成新的事实来源。`--repo-root` 同时决定读取的源码、目录与 Schema。

云端只检查 ROM 契约结构可运行 `python tools/rom-contract-scan.py --contract rom-contracts/hyperos1-a14-core.json --validate-only`；不读取样本，不证明 ROM 兼容。带日期的历史采样维持在[原证据入口](rom-intelligence/README.md)，不批量迁移成当前已验证数据。
