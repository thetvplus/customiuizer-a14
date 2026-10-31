# 文档目录

当前源码、构建配置与实际调用关系是实现事实基准。上游资料用于解释行为与依赖契约；历史审计和 ROM 样本不能覆盖当前实现或证明新 APK 已通过实机验收。

| 内容 | 入口 |
| --- | --- |
| 共同工程规则 | [共同工程规则](../AGENTS.md) |
| 安全问题与私密报告 | [安全政策](../SECURITY.md) |
| 当前页面、功能、设置键与菜单 | [页面与功能地图](FEATURE_MAP.md) |
| 页面结构快速浏览（同源生成） | [字符树简版](FEATURE_MAP_SIMPLE.md) |
| 长期文档职责、更新与删除标准 | [文档规范](DOCS_STYLE.md) |
| 环境、构建与日常开发 | [开发与构建](DEVELOPMENT.md) |
| 本地门禁与 CI | [测试与验证](TESTING.md) |
| A14 执行分级试点与测量边界 | [执行策略试点](maintenance/A14_EXECUTION_PILOT.md) |
| 签名、双仓库与发布验收 | [发布与恢复](RELEASE.md) |
| 运行时结构、API 102 与生命周期 | [架构](../ARCHITECTURE.md) |
| 支持范围与待确认 ROM 行为 | [兼容矩阵](../COMPATIBILITY.md) |
| Duo 使用规则 | [Duo 三合一图标](features/duo-status-bar.md) |
| 动态岛、应用反馈与媒体 | [动态岛](features/island/README.md) |
| 音乐动作、会话及输入合同 | [音乐手势](features/island/MUSIC_GESTURES.md) |
| Dock、材质与模糊 | [Dock](features/dock/README.md) |
| 性能、体积与移除证据 | [优化门禁](OPTIMIZATION_GATES.md) |
| 开发与诊断工具 | [开发与诊断工具](TOOLS.md) |
| Java / Kotlin 调用边界 | [Java 调用边界](JAVA_BOUNDARY_ALLOWLIST.md) |
| 遗留功能语义契约 | [遗留语义契约](maintenance/LEGACY_SEMANTIC_CALIBRATION.md) |
| A14 功能退役与旧偏好清理 | [功能退役与偏好清理](maintenance/A14_FEATURE_RETIREMENTS.md) |
| 当前候选审计、有效更新与待验收 | [当前精修状态](maintenance/A14_REFINEMENT_ROADMAP.md#全局-rc-审计与候选收口) |
| 进程矩阵生成与 ROM 证据边界 | [进程矩阵与 ROM 证据](rom-intelligence/README.md) |
| DexSQL 方法、来源与局限 | [DexSQL 取证方法](rom-intelligence/DEXSQL.md) |
| 通知事务、Heads-up、频道和 Count/Fold | [M03 ROM 合同](rom-intelligence/M03_NOTIFICATION_CONTRACT.md) |
| 历史 fuxi 样本与 ABI 来源 | [2026-08-14 样本](rom-intelligence/FUXI_HYPEROS1_AI_HOOK_CORPUS_2026-08-14.md) |
| 历史 fuxi 运行观察 | [2026-08-14 采样](rom-intelligence/FUXI_HYPEROS1_RUNTIME_AUDIT_2026-08-14.md) |
| 安全实施 / 独立审查 | [实施 Skill](../.agents/skills/a14-safe-implementation/SKILL.md) / [审查 Skill](../.agents/skills/a14-independent-review/SKILL.md) |

未发布条目在准备发行时归入对应版本，历史发行记录保留最终变化；中文见 [CHANGELOG.md](../CHANGELOG.md)，英文见 [CHANGELOG_EN.md](../CHANGELOG_EN.md)。过程审计、调试版本与逐次试验保留在 Git 历史或仓库外的本地证据中。
