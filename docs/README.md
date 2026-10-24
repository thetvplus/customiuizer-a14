# 维护文档

软件中文名称为「米客 A14」，英文名称为「CustoMIUIzer A14」。应用内关于页属于应用界面，按语言显示名称；中英组合展示名仅用于仓库主页和 GitHub About。

当前源码、构建配置与实际调用关系是实现事实基准。上游资料用于解释行为与依赖契约；历史审计和 ROM 样本不能覆盖当前实现或证明新 APK 已通过实机验收。

| 内容 | 入口 |
| --- | --- |
| 共同工程规则 | [AGENTS.md](../AGENTS.md) |
| 环境、构建与日常开发 | [DEVELOPMENT.md](DEVELOPMENT.md) |
| 本地门禁与 CI | [TESTING.md](TESTING.md) |
| 签名、双仓库与发布验收 | [RELEASE.md](RELEASE.md) |
| 运行时结构、API 102 与生命周期 | [ARCHITECTURE.md](../ARCHITECTURE.md) |
| 支持范围与待确认 ROM 行为 | [COMPATIBILITY.md](../COMPATIBILITY.md) |
| 功能文档总览 | [features](features/README.md) |
| Duo 使用规则 | [Duo 三合一图标](features/duo-status-bar.md) |
| 动态岛、应用反馈与媒体 | [动态岛](features/island/README.md) |
| Dock、材质与模糊 | [Dock](features/dock/README.md) |
| 性能、体积与移除证据 | [OPTIMIZATION_GATES.md](OPTIMIZATION_GATES.md) |
| 开发与诊断工具 | [TOOLS.md](TOOLS.md) |
| Java / Kotlin 调用边界 | [JAVA_BOUNDARY_ALLOWLIST.md](JAVA_BOUNDARY_ALLOWLIST.md) |
| 遗留功能语义契约 | [LEGACY_SEMANTIC_CALIBRATION.md](maintenance/LEGACY_SEMANTIC_CALIBRATION.md) |
| A14 功能退役与旧偏好清理 | [A14_FEATURE_RETIREMENTS.md](maintenance/A14_FEATURE_RETIREMENTS.md) |
| 当前精修、未决问题与证据恢复 | [A14_REFINEMENT_ROADMAP.md](maintenance/A14_REFINEMENT_ROADMAP.md) |
| 生成进程矩阵与 ROM 取证 | [rom-intelligence](rom-intelligence/README.md) |

未发布条目在准备发行时归入对应版本，历史发行记录保留最终变化；中文见 [CHANGELOG_CN.md](../CHANGELOG_CN.md)，英文见 [CHANGELOG.md](../CHANGELOG.md)。过程审计、调试版本与逐次试验保留在 Git 历史或仓库外的本地证据中。
