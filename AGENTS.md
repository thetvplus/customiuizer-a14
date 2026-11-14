# 共同工程规则

**Judgment over Procedure：先解决正确的问题，消除无效工作，复用原生能力，做必要改动，然后收口。**

已注入且未变化的规则不重复读取。只打开任务涉及的源码和入口，不递归加载文档、历史或 ROM 数据。

## 工程判断与执行收敛

- 先明确触发、预期结果和完成条件。沿已知调用链检查输入、所有者、失败与释放；先做成本低、能区分根因的检查，有具体证据缺口才扩大检索。
- 对已授权、范围明确的工作自主执行，修复同根因、同调用链的相关缺陷；越权、破坏性操作或无法判断的重大风险才询问。不扩展为无关重构。
- 优先取消无效工作、复用已有对象与结果、增量更新。缓存、抽象、监听器和异常防御须对应真实需求；不为形式整洁重写成熟实现。
- **iOS 复刻先查本地逆向**：指定版本的索引、原生调用链、派生实现及资源是依据，公开资料只补缺口。未知项明确标注，不把参考版本当作 A14 合同。
- 验证选择、可信证据复用和停止条件只见 [TESTING](docs/TESTING.md#执行分级)。目标完成、完整 diff 已审、必要检查通过即收口；缺失验收标为待验，不重复全量测试或来源核验。

## 产品与取舍

- 仅支持 HyperOS 1 / Android 14 / SDK 34、`arm64-v8a`、libxposed API 102。身份和 SDK 以构建配置及静态门禁为准，支持边界见 [COMPATIBILITY](COMPATIBILITY.md)。
- **Performance-First**：在正确、安全、兼容及宿主稳定的前提下，优先响应速度、帧稳定性和持续运行成本，减少 CPU / GPU、内存、Binder、分配与唤醒。不要为代码短、APK 小或架构形式牺牲运行性能；保留核心功能、布局、手势和动画契约，非必要效果可让位于性能。
- 关键路径按 **Predictive Preparation + Lazy Activation + Incremental Execution** 实施，详见[优化门禁](docs/OPTIMIZATION_GATES.md#关键路径优化原则)。有界资源交换须说明收益、所有者、失效、释放和回退；设备提速、帧率或功耗结论必须实测。
- Hook 调用 / 异常、单次安装、ClassLoader、生命周期、G4 曲率及单一材质的权威合同在 [ARCHITECTURE](ARCHITECTURE.md#运行时不变量)。设置由 `app/settings` 生成 Compose；宿主 Hook 使用原生 View / Canvas，不加载 Compose。
- 生产源码不保留测试替身、开发采样或只剩测试调用的历史实现。诊断须源码集隔离，不能只靠 BuildConfig / R8；仓库资产、生产源码、APK 打包和运行初始化分别判断。删除前核对直接调用、反射字符串、Manifest / XML、生成器、JNI / R8 与外部入口，同时移除失去对象的测试 / mutation，保留有效行为回归。

## Git 与协作

- `master` 是唯一长期稳定版本线。未完成成果留在任务分支，已验证成果通过 Squash PR 整合。正式版以 1–2 个主线提交为目标，集成范围、标题与例外见 [主线提交与发行节奏](docs/DEVELOPMENT.md#主线提交与发行节奏)。
- 续接沿用当前聊天的分支、Worktree、授权及证据。首次写入、任务切换或发生冲突时，[核对任务归属](docs/DEVELOPMENT.md#git-与协作)。
- 授权开发任务完成必要验证后，默认 commit / push 对应工作分支，无需再次确认；推送后读回远端完整 SHA。该授权不包含 merge master、tag 或 Release，用户指定的只读 / 停止点优先。
- 禁止 force push、破坏性 reset、改写公开历史、无差别 clean 或覆盖未知修改。Review 不建平行分支；同一 Worktree 只有一个写入者，冲突范围先协调。远端不可用时报告协作状态未知。

## 验证、发布与证据

- 普通任务从 **Quick** 开始，按影响选择 **Standard / Deep**；正式准备、签名与发布走 **Release**。命令和收口条件只见 [TESTING](docs/TESTING.md#执行分级)，不删测试、降断言或吞异常制造通过。
- 构建固定 JDK 27；需要构建或排障时才查[环境](docs/DEVELOPMENT.md#环境)，不因工具不支持而降级。APK / R8、ROM 静态和实机证据分别报告。
- Debug / Develop 不冒充正式版。正式来源、历史签名、原候选恢复及双仓门禁只见 [RELEASE](docs/RELEASE.md)，验证分级不放宽这些要求。
- APK、密钥、密码、签名配置、ROM、DEX、trace、mapping、日志和 profiler 数据不入库。公开提交、Issue / PR 与文档只写抽象问题和必要结论，不披露个人账号、本机路径、凭据、网络 / 会话 / 设备标识、个人应用名单或操作记录；公开兼容接口及必要来源归属可保留。
- 当前决定、未决问题与必要证据入口只更新[原路线图](docs/maintenance/A14_REFINEMENT_ROADMAP.md)，不建任务数据库或流水账。全局性能续审先复用[成本审计结论](docs/maintenance/A14_REFINEMENT_ROADMAP.md#全局输入图像与资源成本审计2026-10-30)，只补新增调用链与证据。

## 按需入口

| 需要解决的问题 | 入口 |
| --- | --- |
| 功能 / 设置 / Hook 定位 | 先 `rg` 查键、Feature / Installer 和实际源码；页面导航用[简版地图](docs/FEATURE_MAP_SIMPLE.md)，键与条件用[详细地图](docs/FEATURE_MAP.md) |
| 关联摘要、大型 JSON | [TOOLS](docs/TOOLS.md#辅助数据按需查询)的筛选与分页；已知路径直接读 |
| ROM 私有接口、签名或 ClassLoader 不确定 | [DexSQL](docs/rom-intelligence/DEXSQL.md#离线-rom-知识库)：复用 Catalog，再 locate / search；来源变化或缓存缺失才 update / prepare。HyperOS 3 / dada 仅 REFERENCE_ONLY，不能提供 A14 ABI / 资源合同 |
| 构建环境 / 生成设置或矩阵 | [环境](docs/DEVELOPMENT.md#环境) / [构建与生成物](docs/DEVELOPMENT.md#构建与生成物) |
| 选测 / 真机行为与 Hook 取证 | [执行分级](docs/TESTING.md#执行分级) / [设备入口](docs/AGENTIC_VERIFICATION.md#设备入口) |
| Issue 收口 | [核对完成条件与验收证据](docs/DEVELOPMENT.md#issue-收口) |
| 实施 / 只读审查 / 专项工具 | [项目 Skills](.agents/skills/README.md) |
| 文档职责、更新与删除 | [DOCS_STYLE](docs/DOCS_STYLE.md)；总目录只维护在 [docs/README](docs/README.md) |
