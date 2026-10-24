# AGENTS.md — 米客 A14

所有 Agent 共用本文件。开始只读本文件与任务相关入口，不递归加载全部文档。

## 产品与取舍

- 仅支持 HyperOS 1 / Android 14 / SDK 34、`arm64-v8a`。
- `applicationId=tv.withaibuild.customiuizer.r14`；`minSdk=34`、`targetSdk=34`。
- 最低 libxposed API 102；不维护 API 101 或 Android 13 / 15 / 16 路径。
- 保留设置进程与 compileOnly Hook API 的 R8 类加载边界；A14 版本、签名和发布独立。
- 优先级：正确性 / 安全 / A14 兼容 > 宿主稳定与生命周期 > 功能与交互 / 动画 > CPU / GPU / 长期内存 > 可维护性 > 体积与形式简洁。
- 按实际收益、证据和回退成本选择技术；允许成熟依赖、有界缓存、受控异步和局部重构，不以最少代码、类、依赖或绝对最低内存为目标。
- 资源增长必须有界，说明所有者、容量、失效、释放、成本与验证；无测量不得声称性能收益。升级可为安全、兼容、新 API 或维护收益，不强求速度 / 体积收益。
- 优先复用既有机制；PrefMap、ResourceHooks、FeatureRegistry、ProcessRouter、PreferenceBootstrap 的变更须有具体问题、受影响契约及专项回归。保留 Dynamic Island / Dock 视觉与布局契约；不默认迁移 Compose、DI、StateFlow 或全量 Kotlin。

## Git 与协作

- `main` 是唯一长期稳定线；普通任务可 commit，push / merge main / tag / Release 须在当前任务授权内。
- 禁止 force push、破坏性 reset、改写公开历史、无差别 clean 或覆盖未知修改；Review 不建平行分支。
- 独立任务原则上对应一个 GitHub Issue 和一个工作分支；新聊天优先继续已有任务，一个聊天不等于一个新分支。需建分支时从确认的 exact SHA 创建。
- 开始核对 `git status --short --branch`、HEAD、`git worktree list`、相关 Issue / 开放 PR 及其真实远端 SHA；用现有 Git / `gh`，不维护第二套任务数据库。
- 在 Issue 认领中写明分支、HEAD、负责文件 / 共享状态和下一步；写入前再核对归属。Issue 认领不是原子锁；竞争或共享所有权冲突时停止冲突范围的写入，先协调。
- 多 Agent 不得同时写同一 Worktree；只读审查可以共享。GitHub 不可访问时标明协作状态未知，不默认无人工作。
- 交接只需 `Issue / Branch / HEAD SHA / Changes / Verification / Remaining`；标明哪些提交已推送，不复制聊天记录。
- 分支清理前确认成果等价纳入、HEAD 未变化、无开放 PR / 活动 Worktree / 协作依赖，并有可恢复来源；先远端保存成果，再按授权逐支删除。
- Agent 自报不能代替 exact diff、客观门禁及真实远端读回。

## Hook 与运行时

- 按进程路由，Feature 同进程只安装一次；偏好变化不能重置已安装状态。
- 关闭功能不创建业务 Hook、Receiver、Observer 或任务；安装型开关遵守原有重启语义。
- 保持 Hook 时序、参数改写、返回 / 异常语义与 `chain.proceed()` 次数。
- 回调最外层使用既有安全边界；普通异常局部隔离，不吞 `OutOfMemoryError`、`ThreadDeath`、`VirtualMachineError`。
- Receiver / Observer / listener / controller 绑定所有者，并有替换、迟到失效与释放闭环；静态字段不强持有 Activity、View 或短生命周期 controller。
- 反射、DexKit、磁盘 I/O、同步 Binder 放冷路径；热路径读准备好的不可变 / 原子状态。缓存有界且按 ClassLoader 隔离。

## 工具链与验证

- 构建运行时与 Java toolchain 为 JDK 25；`JAVA_HOME` 指向根目录，不指向 `bin`，不为环境错误降至 17。
- Java / Kotlin 输出同步为 25，经 R8/D8 生成 SDK 34 可运行 DEX；语言 / API、JVM 输出及 Android API 可用性分别验证。
- 默认保留 Java：`MainModule.java`、`XposedHelpers.java`、`MemberUtilsX.java`；语言迁移小批量、行为等价，与功能变化分开。
- 开发：`python tools/verify.py fast --changed`；已提交范围不能被 changed 覆盖时使用 `fast`。
- 收口：`python tools/verify.py full`、`python -m unittest discover -s tools/tests -p "test_*.py"`、`git diff --check`。
- 同批变更先审 exact head 和已有 CI，整合后统一收口；相同源码与范围复用通过结果，仅新改动、失败或未解决问题补验。纯文档做对应静态检查；工具改动跑工具测试。
- 失败在原任务修复，不删测试、降断言或吞异常。APK / R8、ROM 静态证据和设备验收分别报告；设备不可用保持待验。

## 签名、发布与证据

- Debug / Develop 不冒充正式版；正式构建须获明确授权且仓库外签名有效。版本名与双语 Changelog 同步，versionCode 单调增加。
- 普通 A14 CI 不接触正式密钥。正式签名只走原生 ref / exact SHA、受 Required reviewer 保护的 `release-signing` 和独立 A14 Signed Build；工作流不硬编码本机路径。
- 自动发布要求 exact main CI、新版本及双语记录齐备，并经过签名确认；手动默认 `both`，可选 `source`，仅试用选 `actions-only`。先验收源码端再同步 Xposed。
- 候选晋升 / 失败恢复须匹配版本和原 run ID 的明确授权；不重建、覆盖已发布资产或移动历史标签。完整安全合同只维护在 [RELEASE](docs/RELEASE.md)。
- APK、密钥、密码、本地签名配置、ROM、DEX、trace、mapping、日志和 profiler 数据不得入库；保留忽略目录及仓库外证据。
- 精修状态、未决问题和历史证据入口更新[原路线图](docs/maintenance/A14_REFINEMENT_ROADMAP.md)，不另建流水账。
- ROM / Hook / 最终 DEX 深查按 [DexSQL](docs/rom-intelligence/DEXSQL.md) 和 `tools/dexsql/`；核对已安装 schema、样本哈希、定义来源、同产物 mapping，仅相关开发任务按需使用，不进 APK / 默认 CI。
- 缺陷要有具体触发与可复核证据；静态扫描、Host JVM、ART、APK 和设备口径分开。候选不能凭“可 cache”推广，ROM 行为仍需目标样本及实机。

## 按需入口

| 任务 | 权威入口 |
| --- | --- |
| 环境、生成与构建 | [DEVELOPMENT](docs/DEVELOPMENT.md) |
| 测试职责与 CI | [TESTING](docs/TESTING.md) |
| 路由、API 与所有权 | [ARCHITECTURE](ARCHITECTURE.md) |
| 平台与 ROM 边界 | [COMPATIBILITY](COMPATIBILITY.md) |
| 测量与资源预算 | [OPTIMIZATION_GATES](docs/OPTIMIZATION_GATES.md) |
| 安全实施 / 只读独立审查 | [.agents/skills](.agents/skills) 中的两个 SKILL.md |
