# A14 文档规则

## 长期文档

- `AGENTS.md`：执行规则；
- `PROJECT.md`：产品与平台边界；
- `ARCHITECTURE.md`：稳定运行时结构；
- `WORKFLOW.md`：单任务生命周期；
- `ROADMAP.md`：优先级；
- `COMPATIBILITY.md`：平台与 API 兼容；
- `DOCUMENTATION.md`：本规则。

## docs/

只保留：

- 当前构建与验证规则；
- 当前控制架构；
- 少量 ADR。

禁止重新创建：

- Runtime Hardening 状态长文；
- Review/Implement 报告；
- 阶段 checkpoint；
- 固定 HEAD 清单；
- 重复不变量说明；
- Git 历史流水账。

真正可自动验证的不变量应写进测试或工具，不应再写多份文字门禁。
