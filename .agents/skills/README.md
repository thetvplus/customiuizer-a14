# A14 项目技能

保留三个 A14 自有入口和 89 个第三方入口；第三方由 272 个精简至 89 个。来源、固定提交、原始目录和安装目录见[来源清单](upstream-sources.json)，本地修订由 Git diff 记录。

保留 Android / Kotlin / Compose、安全分析（CodeQL、Semgrep 等）、Perfetto、性能诊断、代码审查与同根因分析。移除无关语言与区块链、重复审查、通用工作流和未使用的热重载；扫描器确实引用的 Kotlin 配套规则仍保留。有效技能的参考、脚本、共享 agents 和许可证随消费者保留，未安装上游插件启动钩子。

入口继续使用已被当前 Codex 宿主发现的 `.agents/skills` 路径与上游层级。所有保留入口具备唯一 `name` 和非空 `description`，来源清单与目录一一对应；无需建立新索引或启动时扫描 ROM。目录可发现不代表每个技能会自动触发，也不代表 CodeQL 等外部工具已安装或适用于当前 JDK / Kotlin。

本地修订仅包括激活名称去重、失效资源链接修正和已退役目录的引用清理。Material 3 Expressive 只保留文字参考，未捆绑视觉媒体。文档链接检查与 Kotlin 规则扫描器自检通过；不逐个运行第三方工具。

项目 [AGENTS](../../AGENTS.md)、[A14 安全实施](a14-safe-implementation/SKILL.md)、[独立审查](a14-independent-review/SKILL.md)和[原生界面工艺](a14-native-craft/SKILL.md)继续约束全部第三方建议。资源仅用于开发，不进入 Android 源码集。
