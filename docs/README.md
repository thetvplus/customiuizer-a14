# 维护文档

当前源码、构建配置与实际调用关系是实现事实基准。上游资料用于解释行为与依赖契约；历史审计和 ROM 样本不能覆盖当前实现或证明新 APK 已通过实机验收。

| 内容 | 入口 |
| --- | --- |
| 共同工程规则 | [AGENTS.md](../AGENTS.md) |
| 环境、构建与日常开发 | [DEVELOPMENT.md](DEVELOPMENT.md) |
| 本地门禁与 CI | [TESTING.md](TESTING.md) |
| 签名、双仓库与发布验收 | [RELEASE.md](RELEASE.md) |
| 运行时结构 | [ARCHITECTURE.md](../ARCHITECTURE.md) |
| 支持范围与待确认 ROM 行为 | [COMPATIBILITY.md](../COMPATIBILITY.md) |
| Duo 使用规则 | [DUO_STATUS_BAR.md](DUO_STATUS_BAR.md) |
| 性能、体积与移除证据 | [OPTIMIZATION_GATES.md](OPTIMIZATION_GATES.md) |
| Java / Kotlin 调用边界 | [JAVA_BOUNDARY_ALLOWLIST.md](JAVA_BOUNDARY_ALLOWLIST.md) |
| 遗留功能语义契约 | [LEGACY_SEMANTIC_CALIBRATION.md](maintenance/LEGACY_SEMANTIC_CALIBRATION.md) |
| 生成进程矩阵与 ROM 取证 | [rom-intelligence](rom-intelligence/README.md) |

版本记录只保留已发行版本之间的最终变化，中文见 [CHANGELOG_CN.md](../CHANGELOG_CN.md)，英文见 [CHANGELOG.md](../CHANGELOG.md)。过程审计、调试版本与逐次试验保留在 Git 历史或仓库外的本地证据中。
