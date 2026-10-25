# 共同工程规则

所有 Agent 共用本文件。已注入且未变化的内容不重复读取；只打开任务涉及的入口和文件，不递归加载文档、全仓历史或 ROM 数据。

## 产品与取舍

- 仅支持 HyperOS 1 / Android 14 / SDK 34、`arm64-v8a`、libxposed API 102。应用身份与 SDK 等确定值由构建配置及静态门禁维护，边界见 [COMPATIBILITY](COMPATIBILITY.md)。A14 版本、签名和发布独立。
- 优先级：正确性 / 安全 / A14 兼容 > 宿主稳定与生命周期 > 功能与交互 / 动画 > CPU / GPU / 长期内存 > 可维护性 > 体积与形式简洁。
- 优先复用既有机制，允许有证据的局部升级、成熟依赖和有界资源交换；说明收益、成本、所有者、失效 / 释放与回退，不以代码行数或最低内存作目标。无测量不宣称性能收益。
- 保持 Hook 调用 / 异常语义、单次安装、生命周期及设置进程与 compileOnly API 的 R8 边界；运行时合同只维护在 [ARCHITECTURE](ARCHITECTURE.md#运行时不变量)。保留 Dynamic Island / Dock 视觉与布局契约。

## Git 与协作

- `main` 是唯一长期稳定线；普通任务可 commit，push / merge main / tag / Release 须在当前任务授权内。已授权的修改、必要检查和失败修复直接执行，不重复确认；授权在同一任务续接中保留但不扩大范围，仅在确有未授权风险或无法安全判断时询问。当前任务的只读或停止点优先。
- 禁止 force push、破坏性 reset、改写公开历史、无差别 clean 或覆盖未知修改；Review 不建平行分支。
- 同一聊天的后续消息默认延续当前任务，直接继承已确认的上下文、分支、Worktree、归属及验证状态。首次启动才检查任务归属、相关聊天、Issue / PR 及 Git 状态；仅创建新任务、实际写入冲突或上下文失效时重新发现。追加指令、状态询问及上下文压缩本身不重启流程。
- 新聊天优先继续已有任务；独立任务原则上一个 Issue / 分支，从确认的 exact SHA 建分支。合并 main、推送、发布及破坏性操作只增量核对必要的实时 Git、安全和来源，不重做任务发现。认领、刷新与清理细则只见 [DEVELOPMENT](docs/DEVELOPMENT.md#git-与协作)。
- 多 Agent 不得同时写同一 Worktree；冲突范围先停写协调。GitHub 不可访问时标明协作状态未知。Agent 自报不能代替 exact diff、实际门禁或远端读回。

## 工具链与验证

- 普通任务默认 **Quick**；按实际影响自动升为 **Standard / Deep**，正式准备 / 签名 / 发布走 **Release**，不要求用户选择模式。分级、命令、证据复用和阶段预算唯一维护在 [TESTING](docs/TESTING.md#执行分级)，只查相应行与受影响的回归。
- 必要检查由受影响契约决定，不能用模式、文件数或预算免除。相同源码、环境和验证范围已有可信通过结果就复用；提交或文字报告本身不触发重跑。失败、新改动或风险变化才补验。
- 构建固定 JDK 25，不为环境错误降级；环境见 [DEVELOPMENT](docs/DEVELOPMENT.md#环境)。不删测试、降断言或吞异常制造通过。完成已授权结果和必要检查后收口，报告实际验证、未决风险及提交 / 推送状态；APK / R8、ROM 和设备证据分开，设备不可用保持待验。

## 签名、发布与证据

- Debug / Develop 不冒充正式版；正式签名与发布仅在任务授权内使用既有 [RELEASE](docs/RELEASE.md) 工作流。分级不放宽正式发行、原候选恢复、签名或双仓门禁，不重建稳定发布系统。
- APK、密钥、密码、本地签名配置、ROM、DEX、trace、mapping、日志和 profiler 数据不得入库；保留忽略目录及仓库外证据。
- 当前决定、未决问题和证据入口更新[原路线图](docs/maintenance/A14_REFINEMENT_ROADMAP.md)，不另建任务数据库或流水账。缺陷要有具体触发与可复核证据。

## 按需入口

| 任务 | 权威入口 |
| --- | --- |
| 功能入口 / 文档治理 | [功能结构](docs/FEATURE_MAP_SIMPLE.md) / [设置键](docs/FEATURE_MAP.md) / [文档规范](docs/DOCS_STYLE.md) |
| 环境、生成与构建 | [开发与构建](docs/DEVELOPMENT.md) |
| 分级、必要检查、复用与停止条件 | [测试与验证](docs/TESTING.md#执行分级) |
| 大型辅助 JSON / 机读数据 | [摘要、筛选与分页](docs/TOOLS.md#辅助数据按需查询)，先查询再按需读原记录 |
| 路由、API 与所有权 | [架构](ARCHITECTURE.md) |
| 平台与 ROM 边界 | [兼容矩阵](COMPATIBILITY.md) |
| ROM 目标 / 最终 DEX 需要取证时 | [DexSQL](docs/rom-intelligence/DEXSQL.md)，不因读取或普通文档任务启动 |
| 测量与资源预算 | [优化门禁](docs/OPTIMIZATION_GATES.md) |
| 安全实施 / 只读独立审查 | [.agents/skills](.agents/skills) 中的两个 SKILL.md |
