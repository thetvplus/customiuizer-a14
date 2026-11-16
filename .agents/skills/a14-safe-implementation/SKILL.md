---
name: a14-safe-implementation
description: 实施 A14 代码、工具、文档或工程规则变更，按受影响的合同选测。指定 diff 的只读审查使用 independent-review，正式发行遵循 RELEASE。
---

# 安全实施

沿用当前聊天已确认的分支、授权、所有权和证据；[AGENTS](../../../AGENTS.md)已在上下文时不重复读取。

1. 明确触发、预期结果和完成条件。代码 / 工具任务沿实际调用定位所有者、失败与释放；文档任务定位权威来源和受影响的消费者。只有 ROM 声明、签名或 ClassLoader 不确定时，才读取 [DexSQL 入口](../../../docs/rom-intelligence/DEXSQL.md)。
2. 实施最小可回退改动。改动运行时行为时核对[运行时合同](../../../ARCHITECTURE.md#运行时不变量)，关键路径检查[优化原则](../../../docs/OPTIMIZATION_GATES.md#关键路径优化原则)；改动文档时遵循[文档规范](../../../docs/DOCS_STYLE.md)。
3. 审阅完整 diff，按[执行分级](../../../docs/TESTING.md#执行分级)验证受影响的合同。自动选测和设备验收走[自主验证](../../../docs/AGENTIC_VERIFICATION.md)，明确每项证据覆盖的输入和行为。
4. 目标完成且必要检查通过后，按[提交、推送与确认](../../../docs/DEVELOPMENT.md#提交推送与确认)交付任务分支。报告验证结果和待验项，当前决定变化时更新[原路线图](../../../docs/maintenance/A14_REFINEMENT_ROADMAP.md)。
