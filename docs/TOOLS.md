# 开发与诊断工具

日常入口见 [开发说明](DEVELOPMENT.md)，验证职责见 [TESTING.md](TESTING.md)。Python 工具集中在 `tools/`，回归在 `tools/tests/`；Windows 外部签名预检及其回归保留在 `scripts/`。各 CLI 的参数以 `--help` 为准。

| 用途 | 入口 |
| --- | --- |
| 本地编译、测试与源码门禁 | `verify.py`、`check-invariants.py`、`eol_check.py` |
| 偏好、致命错误与源码边界 | `check_observer_key_contract.py`、`check_main_source_cleanliness.py`、`source_hazard_scan.py` |
| Hook 热路径预算 | `hook_body_prefmap_scan.py`、`check_hotpath_alloc_budget.py`，分别读取同目录的预算 JSON |
| 设置分页、搜索与偏好目录生成 | `generate_preference_artifacts.py`，由 Gradle 调用 |
| CI 范围、安全与发布元数据 | `ci_scope.py`、`ci_contract_scan.py`、`check_ci_portability.py`、`check_release_metadata.py`、`ci_install_android_sdk.sh` |
| 独立缺陷注入、确定性及无写入检查 | `brutal_test_runner.py`、`brutal_test_config.json`；同前缀的 policy / protocol / mutator / apply-check 模块由 runner 使用 |
| Feature 路由、语义与所有权 | `catalog_contract_probe.py`、`audit-feature-semantics.py`、`audit_hook_ownership.py` |
| 当前源码进程矩阵 | `extract_process_matrix.py`，生成 [rom-intelligence](rom-intelligence/README.md) 下的矩阵及例外表 |
| 诊断构建及产物来源 | `build_debug_apk.py`、`build_revision.py`、`verify_apk_provenance.py` |
| APK 归因与预算比较 | `apk_size_report.py`；需要多变体、显式源码来源及 Markdown 报告时用 `apk_size_delta.py` |
| APK 内容与 R8 可重复性 | `apk_semantic_diff.py`，用于 Full CI 双构建 |
| ROM 样本清单、smali 契约与目标差异 | `rom_inventory.py`、`rom-contract-scan.py`、`rom_target_diff.py` 及测试 fixtures |
| 继承功能语义对照 | `legacy_semantic_compare.py`，见 [语义契约](maintenance/LEGACY_SEMANTIC_CALIBRATION.md) |
| 离线运行日志分析 | `analyze_lsposed_log.py` |
| 可选 DEX 深查 | [DexSQL 查询集](../tools/dexsql/README.md)与[取证说明](rom-intelligence/DEXSQL.md) |
| Windows 正式签名预检 | `scripts/check-signing-config.ps1`、`scripts/test-check-signing-config.ps1` |

`feature-semantics/` 和 `rom-contracts/` 是当前校验输入；进程矩阵是源码生成物。ROM 原件、APK、mapping、日志、临时评估和设备原始证据放忽略目录或仓库外，不建立重复的状态报告或未接入工具的证据模板。

离线工具不因缺少 CI 调用就视为无用；移除前核对调用方、输入输出及替代入口。Python 测试保留真实工具行为、生成结果、资源与必要的运行时接入约束，避免重复实现 Kotlin 业务或仅检查测试方法名。
