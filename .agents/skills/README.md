# A14 项目技能

| 任务 | 入口 |
| --- | --- |
| 实施代码、工具、文档或工程规则变更 | [安全实施](a14-safe-implementation/SKILL.md) |
| 只读审查指定 diff 的缺陷与回归 | [独立审查](a14-independent-review/SKILL.md) |

专项任务按已知风险调用对应技能。分析已有 trace 用 Perfetto，录制 Android 性能数据用 Android Profiler。执行 Gradle 用 gradle-run，修改构建配置用 gradle-build。修改 AGENTS 或 Skills 用 writing-for-agents。第三方技能的触发条件见各自的 `SKILL.md`，固定来源、本地修订和安装位置见[来源清单](upstream-sources.json)及 Git diff。

执行脚本前核对所需工具与项目版本。[工程规则](../../AGENTS.md)及项目合同优先于第三方建议。技能、脚本、参考和许可证属于开发资产。
