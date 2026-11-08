---
name: a14-safe-implementation
description: Implement A14 code, tooling or engineering-policy changes with checks matched to the affected contracts. Excludes read-only review and formal release execution.
---

# 安全实施

沿用当前聊天已确认的分支、授权、所有权和证据；[AGENTS](../../../AGENTS.md)已在上下文时不重复读取。

1. 明确触发、预期结果和可回退单位，追踪实际调用、共享状态、失败及释放路径。已知路径直接读；关联不明才用 [context 查询](../../../docs/TOOLS.md#辅助数据按需查询)，ROM 接口不确定才用 [DexSQL](../../../docs/rom-intelligence/DEXSQL.md)。
2. 按受影响的[运行时合同](../../../ARCHITECTURE.md#运行时不变量)实施。关键路径采用[优化原则](../../../docs/OPTIMIZATION_GATES.md#关键路径优化原则)，收益按实际层级取证。同步相关调用、反射 / 资源引用、生成物及能识别缺陷的回归，避免无关迁移。
3. 审阅完整任务 diff，按 [TESTING](../../../docs/TESTING.md#执行分级)选必要检查并复用可信结果。需要自动选测或设备验收时，用[自主验证](../../../docs/AGENTIC_VERIFICATION.md)的 `tools/agent_verify.py plan --base <task-base>`，核对实际消费者和待验层；文档任务不要求设备、ROM 或 APK 构建。
4. 新失败先诊断，只补受影响验证。目标完成、完整 diff 已审且必要检查通过后，按 [Git 规则](../../../docs/DEVELOPMENT.md#提交推送与确认)提交、推送并读回；用户的只读 / 停止要求优先，不自动合并或发布。

交付说明实际验证及未决边界。源码 / 构建、安装、Hook 特有效果和可比性能分别判断；当前决定变化才更新原路线图，不增加任务流水账。
