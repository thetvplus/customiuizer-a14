---
name: a14-safe-implementation
description: 实施 A14 代码、工具、文档、工程规则或仓库维护任务，包括审查后有授权修正的任务。指定 diff 的只读审查使用 independent-review，正式发行遵循 RELEASE。
---

# 安全实施

沿用当前聊天已确认的分支、授权、所有权和证据；[AGENTS](../../../AGENTS.md)已在上下文时不重复读取。

1. 明确输入、预期结果和完成条件。代码 / 工具沿实际调用检查所有者、失败与释放；文档核对权威来源、入口和消费者；仓库清理先核对真实远端与保留依赖。
2. 按涉及的合同实施改动。运行时行为核对[运行时不变量](../../../ARCHITECTURE.md#运行时不变量)，关键路径核对[优化原则](../../../docs/OPTIMIZATION_GATES.md#关键路径优化原则)，ROM 声明、方法签名或 ClassLoader 有疑问时查 [DexSQL](../../../docs/rom-intelligence/DEXSQL.md)。文档按[文档规范](../../../docs/DOCS_STYLE.md)整理，分支和 Actions 清理走[工程入口](../../../AGENTS.md#git-与协作)。
3. 审阅完整 diff，按[执行分级](../../../docs/TESTING.md#执行分级)验证受影响的合同。自动选测和设备验收走[自主验证](../../../docs/AGENTIC_VERIFICATION.md)，明确每项证据覆盖的输入和行为。
4. 目标完成且必要检查通过后，按[提交、推送与确认](../../../docs/DEVELOPMENT.md#提交推送与确认)交付任务分支。远端清理按实际读回报告结果。当前决定变化时更新[原路线图](../../../docs/maintenance/A14_REFINEMENT_ROADMAP.md)。
