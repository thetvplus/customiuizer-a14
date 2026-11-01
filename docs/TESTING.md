# 测试与验证

工程入口见 [AGENTS](../AGENTS.md)，环境与变体见 [DEVELOPMENT](DEVELOPMENT.md)。本页是本地验证选择与证据复用的唯一规则来源；既有 CI 和正式发行门禁保持独立。

## 执行分级

普通任务从 Quick 开始，根据真实调用、数据、进程和构建影响自动升级；模式不需要用户确认，也不等于 Codex 模型或 reasoning effort 设置。用户已限定的审查、只读、发布授权与停止点优先。

| 级别 | 适用范围与必要检查 | 收口条件 |
| --- | --- | --- |
| **Quick** | 文档：`python tools/check_docs.py`、`python tools/generate_feature_map.py --check`。叶子 Python 工具：受影响的 `tools/tests/test_*.py` 和相关消费者 / 静态契约。局部产品修复：`python tools/verify.py fast --changed --tests <TestClassName>`；无对应行为测试时用 `fast --changed` 编译并补充能识别实际缺陷的验证。 | 目标完成、完整 diff 已审、受影响的必要检查通过，再做 `git diff --check`；不因收口自动追加 Full / 全量 Python / APK。 |
| **Standard** | 跨模块迭代、页面 / 资源 / 生成链变化：`python tools/verify.py fast --tests <TestClassName> <RelatedTestClassName>` 加关联回归；工具 / 生成器的影响跨越多个消费者时运行相关工具测试，范围无法可靠界定时运行全量工具测试。资源打包、Manifest 或变体受影响时做必要构建。 | 调用方、生成 / 持久化输入及失败路径均有覆盖；构建不能代替行为回归。 |
| **Deep** | 架构、复杂 Hook、并发 / 所有权、性能 / 兼容问题、依赖 / JDK / R8 或共享安装 / 偏好 / 路由边界：`python tools/verify.py full`、全量工具测试，及 `:app:assembleDevelop :app:lintVitalDevelop`、R8 输出与 API / 类加载检查；涉及工具 / CI / catalog / matrix 时补对应的确定性与独立 mutation。ROM 行为或设备性能结论须有目标样本及相应实机验证。 | 完整相关验证及回退范围明确；相同输入的门禁可按下节复用，设备不可用则保留待验，不把 Host / 构建通过写成 ROM 或设备通过。 |
| **Release** | 正式版本准备、签名、候选晋升、双仓发布 / 恢复：使用既有 [RELEASE](RELEASE.md) 工作流及其 exact main CI、版本、双语记录、历史证书、来源和原候选约束。 | 按该工作流的收据与双端读回验收；不得用 Quick / Standard 或本地通过替代正式门禁。 |

修改 `.github/`、发布工具、验证器、基线、契约扫描器、catalog / matrix、共享生成器或依赖配置时，按影响至少 Standard；影响门禁可信度、签名、产物或公共运行边界则 Deep，并读取受影响的正式发布合同。工具文件不因扩展名为 Python 就归 Quick。发布流程的代码维护不自动授权签名或公开发行。

可以根据证据降低深度，例如跨文件重命名实际只影响文档；说明未触及哪些原定高风险契约及保留了哪些检查。不能仅按行数、文件数、耗时或已有缓存降级。混合改动按最高实际风险定级，检查覆盖各受影响契约的并集；无法排除公共状态 / ROM / R8 风险时先升级，已证实的受影响检查不能取消。

`fast --changed` / `--staged` 只覆盖未提交 / 暂存范围，不能证明已提交分支完整通过；已提交改动用不带 changed 的模式，并按 exact base/head 审完整任务 diff。普通生产源码的 `fast --changed` 默认只编译，不运行行为测试；需要行为回归必须显式选类。`verify.py` 不构建 APK、不连设备、不执行 R8，Deep 所需的相应检查另做或复用可信结果。

工具测试入口为 `python -m unittest discover -s tools/tests -p "test_<affected>.py"`；影响全工具链时用 `-p "test_*.py"`。不要把通配占位符当成已执行范围；最终结果记录实际测试类 / 文件及执行结果。失败在原任务修复，不通过删测试、降断言或吞异常制造通过。

源码合同测试复用 `src/test` 的 `testsupport.repositorySource`，从 Gradle 工作目录向上定位仓库；缺源码必须失败，不通过 ClassLoader URL 或 Assume 跳过。`test_rom_inventory.py` 在临时目录用既有 JDK / SDK 工具生成小 APK / DEX，使用仓库 JAR 验证 class 读取，不依赖历史安装包、厂商 ROM 或预先运行应用构建；固定 SDK 与 `ci_install_android_sdk.sh` 对齐。样本不进入 main 或 Git。

## 证据复用

首次初始化确定 base/head、脏工作区及受影响契约，续接只更新发生变化的部分，按完整任务 diff 选择检查；收到新消息本身不使证据失效。可信记录包括源码身份、命令 / 范围、环境与配置、实际退出结果和必要的新鲜测试报告；可以来自本轮日志或同范围成功 CI，不另建长期缓存数据库。

同 SHA、相同输入 / 环境 / 检查范围直接复用；只有文档或提交信息变化时，可先用 Git diff 证明应用源码、构建输入、所用验证器 / 测试和配置均未变，再复用原结果。不能只看 HEAD、分支名、测试数量、旧 XML 或 Gradle UP-TO-DATE。工作区有相关未提交修改时，旧 SHA 的通过结果不能覆盖它们。

只对新增改动、失败检查、环境变化或未解决风险补验；一次集成后统一收口，不为 commit、PR 描述、最终报告或多 Agent 自报重复执行。失败后的成功记录须来自修复后的实际输入。不同范围的结果只证明交集，合并提交的新相互作用仍须覆盖。CI 要核对真实 head SHA / conclusion；发行另遵循 RELEASE，不能把来源等价推断用于替代其 exact SHA 门禁。

## 任务路由与阶段预算

先使用当前聊天已经确认的上下文和目标文件；仅首次初始化或 [Git 与协作](DEVELOPMENT.md#git-与协作)规定的发现触发条件才检查任务入口。功能定位先按路径 / 符号搜索；需要页面导航才读简版地图，需要设置键 / 条件再查详细地图，大 JSON 用 [摘要与分页](TOOLS.md#辅助数据按需查询)。续接不重新枚举聊天、历史任务、全部 Issue / PR 或 Worktree，不重复读取未变化的文档、符号或命令；只对新增疑问补充检索。

- **进入实现**：明确触发、预期行为、实际调用 / 所有者、回退单位和能识别缺陷的检查后开始修改，不等无关审计结束。纯文档 / 工具任务按其真实输入和消费者判断。
- **按需工具**：ROM 声明、签名或 ClassLoader 不明确才用 [DexSQL](rom-intelligence/DEXSQL.md)，先核对已安装 schema、目标样本身份、定义来源及同产物 mapping；不进 APK / 默认 CI。ADB 用于已授权的具体设备验收，安装、重启和数据操作遵循当前授权。静态扫描用于受影响契约或调查明确风险，不因工具可用就全套启动。
- **发现预算**：Quick 通常不超过 3 批有目的的检索或 5 分钟；Standard 为 5 批 / 10 分钟，Deep 为 8 批 / 20 分钟。预算针对一次必要发现，续接不重置或重跑发现批次；没有新增疑问就直接执行。达到任一参考预算先检查重复读取、工具阻塞和范围扩张，保留必要调查并说明具体未解问题；预算不是质量豁免，也不要求例行向用户申请延期。
- **执行与重试**：先复用已获取的上下文和通过记录，独立读取与检查优先合批；写入、依赖步骤与授权串行。进展只报告新的结论、变化、阻塞或完成，不按消息 / 工具轮次重复状态。等待使用可恢复的会话，单次等待不超过 60 秒，状态未变就退避。失败先诊断，只有环境 / 输入变化或明确瞬时错误才有界重试；连续同因失败两次后先改变方法，不原样循环。Release 使用现有 workflow timeout 和恢复规则。
- **结束**：授权目标已实现、完整 diff 已审、必要检查通过或清楚标注待验边界时交付。说明实际模式、变更、验证与剩余风险；不在交付前附加无关清理、性能、部署或发布检查。缺少必要验收要明确未完成，不能靠阶段预算或模式名称宣告通过。


## 验证职责

| 层 | 入口 | 证明范围 |
| --- | --- | --- |
| 静态契约 | `verify.py`、`check-invariants.py` | SDK / API、偏好、Feature、生命周期、热路径及编码规则 |
| Python | `unittest discover -s tools/tests` | 工具、生成器、资源 / 源码接入、ROM matrix 和 CI 配置 |
| 文档 | `check_docs.py`、`generate_feature_map.py --check` | 本地链接 / 锚点 / 图片、实际设置源与地图一致；不证明外网或实机通过 |
| JVM / Robolectric | `testDebugUnitTest` | 实际状态、动作和 Android 14 UI 模型；不运行目标 ROM Hook |
| 独立缺陷注入 | `brutal_test_runner.py` | 注入缺陷是否被独立门禁检出 |
| 普通 CI | [a14-ci.yml](../.github/workflows/a14-ci.yml) | 同 SHA 门禁、一次未签名 Develop、R8 和 fatal lint |
| 正式候选 | [A14 Signed Build](RELEASE.md#actions-构建与自动发布) | 历史正式签名、来源 / Artifact 摘要、元数据及零人工审批发布契约 |
| 设备 | 指定 APK / ROM 下操作与日志 | 实际 Hook、UI、生命周期和性能，仅限已测场景 |

不以测试数量、静态命中或一次构建代替兼容结论。保留行为、备份 V2、偏好、所有权、热路径、API 边界和 issue 回归；只有无 production subject、完全重复或锁死错误细节的测试才可评估移除。Python 不重复实现 Kotlin 业务，也不以测试方法名匹配代替执行。

## 关键回归

- 天气需求回归 `WeatherFormatDemandTest` / `WeatherRefreshLifecycleTest` 覆盖四种生效格式、隐藏日期、CC 格式既有规则、无需求零订阅 / 查询、关闭取消发布、灭屏恢复和 owner 替换；复用 `SystemClockHotPathTest` 的格式 / 秒钟能力回归。天气 owner 静态门禁同时识别两参数与需求参数调用，错误 owner 和漏掉 OOM 隔离仍须检出，不以更新合法调用形态放宽所有权 / fatal 合同。
- 输入成本回归 `StatusBarTouchCostTest` / `GestureBrightnessReadCostTest`：原生单次分发与异常、无整组参数读取、多指后加入亮度、无效控制中心起点、普通亮度读取失败与 fatal；复用既有 GestureMachine 状态 / 所有权压力回归。图标 `BitmapCachedLoaderMetadataTest` 覆盖冷加载元数据复用、热缓存不访问 PM、缺失 / 无图标回退与共享库查询标志。均为 Host 次数和行为证据，设备交互仍须单独验收。
- 设置界面运行实际 FragmentManager、XML、动画和返回流程，覆盖四类页面、关于页、动画关闭、取消返回和 Activity 重建；断言页面可见且有可见选项。测试 `miui.os.Build` 只控制选项，不模拟 HyperOS。
- Fragment 动画创建时保留框架正在准备的位置 / alpha，否则可停在屏外。Activity 继续通过 Fragment 公开入口关闭预测预览；重新启用前须验证取消预览遗留位移及目标 HyperOS 手势，不能只看普通返回。
- Dock 覆盖 4→5→4、1 / 3–6 图标、子布局边界不变、拖拽结束 / 取消、文件夹 / 占位、空 Dock / 重填、空闲帧不读图标、尺寸控制独立；真实动画合成另需设备。
- Dock / Toast 的固定种子轨迹分别覆盖 owner 重建、0–6 图标、旋转、安全区、文字和字号极值；保留状态机长序列与释放断言。当前种子与步数以测试源为准。
- 取消路径包括滑动中的临时亮度与长按中断。ClassLoader 隔离须用同名类在一个加载器缺失、另一个可用的返回值证明；共享伪单例不能证明隔离。
- Hook 单参数读取由 `MethodHookBoundaryTest` 覆盖 before / after、空值、原方法后的值、共享改写数组、跳过与异常恢复；只读路径不取整组参数、不调用 `proceed(args)`。`HookArgumentAllocationTest` 用真实适配器比较旧数组读取与单参数读取，Host 计数只证明适配器分配差异，不代表 ART / JNI、设备 CPU 或帧率。
- 振动检查 native 一次、usage 分流、默认 / 异常回退、fatal 和跨午夜；预制效果及真实触感单独验收。
- 偏好发布回归用可控线程交错覆盖启动 / 批量快照与监听更新，安装门禁须等待完整发布，读取失败须保留有效值；同时保留已提交高度覆盖旧缓存、真实删除 / 默认恢复、Feature 单次安装和悬浮窗策略跨刷新冻结的关联回归。
- 通知、媒体返回、状态栏几何与 Insets 的当前待验项集中在[路线图](maintenance/A14_REFINEMENT_ROADMAP.md#待实机验收)，不重复维护设备结果。

## CI

普通 CI 在 main push、PR、`r14.*` tag 与手动触发；main 纯 Markdown 跳过构建，tag 不受路径过滤影响。[ci_scope.py](../tools/ci_scope.py)按完整 diff 选择额外工具门禁，不安排定时构建或依赖自动更新。

同一 job 执行 Python / 源码契约、Full、一次 Develop 和 fatal lint；所有成功运行保留唯一 Develop APK 与 mapping，R8 输出始终检查。工具 / CI / catalog / matrix、tag 和手动运行另做矩阵确定性与必需独立 mutation。需要诊断可重复性时才做两次隔离构建与 `apk_semantic_diff.py`，不加入日常 CI。

main / PR 新提交取消旧运行，tag 正常完成；诊断 7 天，检查产物 30 天，历史清理保留正式发行的成功验证记录。正式自动发布、同 SHA CI 复用、Environment main 限制、顺序双端发布和原候选恢复只维护在 [RELEASE](RELEASE.md)，普通 CI 永不接触正式密钥。

清理历史时先核对 Release 元数据、原 run / attempt 及候选资产；签名任务最终失败也可能留下可恢复候选，不能按 conclusion 批量删除。没有过期或确认失效的资产时沿用现有保留期，不新增定时清理工作流。

Python 的 CI 命令使用 unittest buffer：成功测试的模拟发布 / 故障输出被收起，测试数量、跳过和失败摘要保留；失败时仍显示该测试的 stdout / stderr。Hermetic 命令还清空继承的 Actions 摘要 / 输出 / 环境 / 路径 / 状态文件位置，避免模拟发布污染真实工作流；测试可显式设置自己的临时输出文件。日志中的模拟发布链接不代表真实发行。

`tools/tests/fixtures/rom-smali/` 的小型合成样本用于签名、替代类、多 DEX 与目标隔离回归，不是 ROM 原件或实机证据；保留原路径。源码风险、Hook 回调读取与分配预算各有不同门禁，不按数据体积合并或重新采样。`source_hazard_scan.py --write-baseline` 只允许修剪已有指纹并保留 disposition，拒绝创建已接受基线或接纳新指纹；新增风险须单独审查。

## 独立故障验证

[brutal_test_config.json](../tools/brutal_test_config.json)维护 mutation 清单、独立门禁及生成输出。`ACTIVE_INDEPENDENT`、`BLOCKED_NO_INDEPENDENT_GATE`、`MUTATOR_STALE` 分开报告；self-detection 与缺口不算独立检出。

```powershell
python tools/brutal_test_runner.py --config tools/brutal_test_config.json determinism
python tools/brutal_test_runner.py --config tools/brutal_test_config.json mutate --required-only
python tools/brutal_test_runner.py --config tools/brutal_test_config.json mutate --behavior-only
```

行为 mutation 按需执行，从已提交 HEAD 建临时 worktree，先运行相同命令的干净基线；基线失败不能计作检出。普通 CI 的 required-only 继续覆盖静态安全门禁，不以测试代码自身识别 mutation。

底层 `verify.py behavior --tests <完整类名>` 强制重跑 JVM 任务并检查新 JUnit：0 执行通过，1 仅实际测试失败，2 整类未执行，3 编译 / 工具 / 环境 / 陈旧或矛盾报告。只有 1 计独立检出并保留失败摘要；不接收 mutation 名称。

临时 worktree 只复制 `sdk.dir`，不复制签名。清理核对目录及 Git 注册均消失，Windows 锁仅有界重试；失败仍使任务失败，同时保留清理前结果。

## 构建与发布契约回归

Actions 固定完整 SHA；JDK 下载保留 force-download / verify-signature，wrapper 保留 SHA-256。PyYAML 解析真实 YAML，支持合法别名、引号、内联与多行；普通 CI 禁签名，保护流程仅对完整满足契约的已识别步骤开放，不按文件名整体豁免。

SDK 安装与 Gradle 输入一致；宿主 classpath 与 APK runtime 分开审查。依赖变化按影响检查解析图、编译与 DEX 输出、R8 / API 隔离、签名及必要设备路径；不把 JVM 26 当作 Android API 可用性证明。

发布故障回归覆盖 workflow / event / 仓库 / attempt / SHA、环境策略漂移、签名前 main 前进、错误证书 / 凭据、篡改、版本 / 双语记录、草稿资产、预发行 Latest 与双端部分失败。只允许 Validate / Sign 成功而 Publish 失败的原 attempt 恢复；测试模拟服务器不能证明远端 Secrets / 权限或设备通过。

发布工具测试覆盖原生 main / exact CI、零人工审批的 main-only 环境、指定正式目标与试用隔离、累计说明 / 安全两基线、无条数上限的双语内容、Artifact ID / 归档摘要及安全解压、原候选失败恢复、顺序双仓资产和下载文档幂等性。它们使用模拟 API，不触发正式签名或远端公开；远端环境和实际清理另以 API 读回核实，完整规则见 [发布与恢复](RELEASE.md)。
