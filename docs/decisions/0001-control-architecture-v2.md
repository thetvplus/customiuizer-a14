# ADR-0001：A14 使用单任务闭环并删除旧控制文档

## 状态

Accepted

## 决策

删除旧 Runtime Hardening、Review、Implement、Audit 和固定 HEAD 文档体系，不归档。
采用单任务合同、集中验证和最终 diff 审查。

## 原因

旧体系在安全收益递减后仍持续扩大流程成本，导致功能、缺陷和优化无法正常推进。

## 结果

- ChatGPT 负责设计与最终审查；
- Devin 负责实现、验证和构建；
- API 101/102、生命周期、热路径和 JVM 边界继续由代码门禁保护；
- ROADMAP 恢复为简单优先级；
- Git 历史是旧文档唯一回退来源。
