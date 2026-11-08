# A14 项目技能

| 任务 | 入口 |
| --- | --- |
| 实施代码、工具或工程规则变更 | [安全实施](a14-safe-implementation/SKILL.md) |
| 只读审查指定 diff | [独立审查](a14-independent-review/SKILL.md) |

保留 25 项：实施与独立审查；Android / Perfetto 性能、ANR、R8、Gradle、Kotlin 并发；Android 安全与测试、静态分析、故障定位和规则编写。Compose 仅保留性能、状态生命周期和现有 UI 测试参考。

暂停保留视觉设计、组件 / 主题、动效工艺、设计系统、跨平台专用及重复审查资料。需要时从 Git 历史中的[来源清单](upstream-sources.json)恢复对应固定版本，不预装整套工具。Android Profiler 用于录制与专项分析，Perfetto 负责现有设备验证的离线查询；其运行清单、SQL、脚本和许可证一并保留。

第三方技能按问题加载；来源、固定提交、安装路径及本地修订见来源清单和 Git diff。不安装上游启动钩子，不在 Agent 启动时扫描 ROM。目录可发现不代表工具已安装或适用于当前 JDK / Kotlin。

[AGENTS](../../AGENTS.md)和项目合同优先于外部建议。技能及其脚本、参考和许可证仅用于开发，不进入 Android 源码集；不另建技能索引。
