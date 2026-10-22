# AGENTS.md — 米客 A14｜CustoMIUIzer A14

共同工程规则，适用于 ChatGPT、Cursor、Devin、Codex 和其它 Agent。
不要为每个 Agent 复制一份几乎相同的文档。

## 1. 产品边界

- HyperOS 1 / Android 14 / SDK 34。
- `applicationId`：`tv.withaibuild.customiuizer.r14`
- `minSdk=34`、`targetSdk=34`、ABI `arm64-v8a`
- libxposed API 102 为最低运行基线；不维护 API 101 兼容路径。
- 保留设置进程与 compileOnly Hook API 的 R8 类加载边界。
- 不支持 Android 13、Android 15 或 Android 16。
- A14 独立版本、签名、APK、发布和兼容策略。

## 2. 优先级

```text
correctness / security / A14 compatibility
> Hook lifecycle / host stability
> feature quality / interaction latency / animation smoothness
> CPU / GPU / memory efficiency / long-running behavior
> maintainability / test coverage / architecture
> APK size / code size / formal simplicity
```

工程策略：稳定优先、体验驱动、性能实证、适度内存换性能、架构按需演进。
不再以“俄罗斯式极简”、代码最少、类最少、依赖最少、内存绝对最低或 APK 绝对最小为硬性目标。
允许为明确的功能、体验或维护收益采用现代 Java/Kotlin、Android 能力、有界缓存、受控异步及成熟依赖；记录收益、成本、所有权、失效与释放、验证和回退单位。资源增长必须有界，不接受重复计算、无法释放的对象或无意义的后台任务。

局部重构、抽象、框架和依赖按收益、证据、风险与可回滚性选择，不因增加类、代码或资源就自动拒绝。优先复用已有机制；改变 PrefMap、ResourceHooks、FeatureRegistry、ProcessRouter、PreferenceBootstrap 等成熟基础设施需要明确问题、受影响契约与专项回归，不为微优化全面重写。Compose、DI、StateFlow 等按具体需求评估，不作默认迁移目标。Dynamic Island、Dock 的既有视觉和布局契约须保持；全量 Java→Kotlin 迁移不在普通任务范围内。

安全升级与能力试验可以出于新 API、兼容性探索、安全修复、可维护性或开发工具整合，不要求先证明性能提升或 APK 变小。允许在当前授权的试验分支分阶段升级、验证和回退；仍须遵守产品、生命周期、API 隔离和验证边界。没有测量证据时不得宣称性能收益。

## 3. Git

- `main` 是唯一长期稳定生产线。
- 从 exact SHA 建工作分支，例如 `cursor/...` 或 `devin/...`。
- 禁止 force push、破坏性 reset、rebase 已公开历史、无差别 clean。
- 不为 Review 建平行分支。不覆盖未知工作区修改。
- 普通任务可以 commit；push / merge main / tag / release 必须由当前任务合同明确授权。
- 不相信 Agent 自报 PASS。客观门禁和 diff 审查才算数。

## 4. 运行时

- 无关进程不初始化无关 Feature。
- Feature 同一进程只安装一次。
- preference 变化不得把已安装 Hook 重置为未安装。
- 关闭功能不得创建业务 Hook、Receiver、Observer 或任务。
- Hook 时序、参数改写和 `chain.proceed()` 次数必须保持。
- 回调最外层使用项目既有安全边界；普通异常局部隔离。
- `OutOfMemoryError`、`ThreadDeath`、`VirtualMachineError` 不得吞掉。
- Receiver / Observer / listener / controller 必须绑定所有者，并有替换、失效、释放闭环。
- 不静态强持有 Activity、View 或短生命周期 controller。
- 反射、DexKit、磁盘 I/O 和同步 Binder 留在冷路径。
- 热路径只读已准备好的不可变或原子状态。
- 缓存有界、按 ClassLoader 隔离。

## 5. JVM

- 构建运行时为 JDK 25。`JAVA_HOME` 指向 JDK 根目录，不得指向 `bin`。
- Android Java / Kotlin 输出目标同步为 25；通过 R8/D8 生成 SDK 34 可运行的 DEX。语言/API 级别与 JVM 输出目标分别验证。
- 不得为了绕过环境错误降级到 JDK 17。
- 默认保留 Java：`MainModule.java`、`XposedHelpers.java`、`MemberUtilsX.java`。
- Java→Kotlin 必须小批量、行为等价，并与功能变化分开。

## 6. 验证

开发中：

```powershell
python tools/verify.py fast --changed
```

收口：

```powershell
python tools/verify.py full
python -m unittest discover -s tools/tests -p "test_*.py"
git diff --check
```

同批 PR 先审查 exact head 与已有 CI，整合后统一做一次上述收口验证及必要构建 / 实机检查。相同源码与范围已通过时复用结果；仅新修改、失败或未解决问题追加相关验证，不逐 PR 反复跑全套。纯文档变更只做对应静态检查。

工具目录改动时再跑 Python 工具测试。失败在原任务内修复。不得通过删测试、降断言或吞异常制造通过。

## 7. 构建与发布

- Debug / develop 构建不得冒充正式版。
- 正式 Release 仅在用户明确要求、仓库外签名配置有效时执行。
- 禁止提交 APK、keystore、密码或本地签名配置。
- GitHub Actions 不得硬编码本机路径。普通 A14 CI 永不接触正式密钥；仅原生 ref / exact SHA、受分支规则保护的 `release-signing` Environment 与独立 `A14 Signed Build` 可以正式签名。正式发布仍需独立明确授权，使用已验证的同一 APK，详见 `docs/RELEASE.md`。
- ROM 样本、trace、mapping、profiler 数据不得入库。
- 版本名与 `CHANGELOG` 必须同步；`versionCode` 必须单调增加。

## 8. ROM 证据

- 功能精修、DexSQL 取证和安全退役统一从 [A14 精修路线图](docs/maintenance/A14_REFINEMENT_ROADMAP.md) 继续；复用已完成批次，直接更新原页，不另建重复审计或管理体系。
- 静态扫描不能替代目标 HyperOS ROM 实机验证。
- 成熟热路径基础设施变更需与风险匹配的可复核证据、专项回归和独立回退单位；涉及 ROM 行为的结论仍须目标样本与实机验证。缺少必要证据的方案保留为候选，不凭静态扫描推广。
- 候选缺陷必须有具体触发条件和可复现证据；性能结论需内存 / 线程 / 日志 / CPU / GPU 或帧时间等相应测量。Host JVM、ART、最终 APK 和实机口径分别报告。
- “这里可以 cache” 不构成缺陷。
- ROM 适配、Hook 定位、目标差异与最终 DEX 复核优先使用 [DexSQL 指引](docs/rom-intelligence/DEXSQL.md) 与 `tools/dexsql/`，复用官方 CLI / MCP；先核实已安装 schema、样本哈希、定义来源和同产物 R8 mapping。仅在相关开发任务调用，不加入 APK 或默认 CI，结果不能代替实机验收。
