# 测试与验证

工程入口见 [AGENTS](../AGENTS.md)，环境与变体见 [DEVELOPMENT](DEVELOPMENT.md)。本页是本地验证选择与证据复用的唯一规则来源；既有 CI 和正式发行门禁保持独立。

## 执行分级

遵循 [工程判断与执行收敛](../AGENTS.md#工程判断与执行收敛)，普通任务从 Quick 开始，根据真实调用、数据、进程和构建影响选择深度。级别表示风险与验证深度，不是整套命令清单；每项检查须能回答当前改动的具体风险。用户已限定的审查、只读、发布授权与停止点优先，正式发行门禁不变。

| 级别 | 适用范围与必要检查 | 收口条件 |
| --- | --- | --- |
| **Quick** | 文档：`python tools/check_docs.py`；导航 / 功能地图输入变化时加 `python tools/generate_feature_map.py --check`。叶子 Python 工具：受影响的 `tools/tests/test_*.py` 和相关消费者 / 静态契约。局部产品修复：`python tools/verify.py fast --changed --tests <TestClassName>`；无对应行为测试时用 `fast --changed` 编译并补充能识别实际缺陷的验证。纯名称 / 文案调整检查相关资源与引用，无打包疑问时无需 APK。 | 目标完成、完整 diff 已审、受影响的必要检查通过，再做 `git diff --check`；不因收口自动追加 Full / 全量 Python / APK。 |
| **Standard** | 跨模块迭代、页面行为或生成链变化：`python tools/verify.py fast --tests <TestClassName> <RelatedTestClassName>` 加关联回归；工具 / 生成器跨多个消费者时运行相关工具测试，范围无法可靠界定时运行全量工具测试。资源打包、Manifest 或变体行为变化时做必要构建。 | 调用方、生成 / 持久化输入及失败路径均有覆盖；构建不能代替行为回归。 |
| **Deep** | 复杂 Hook、共享状态、并发、安全 / 兼容及构建边界变化：验证实际受影响的调用、所有权、异常和释放路径。公共运行边界变化或定向证据无法界定影响时执行 `python tools/verify.py full`；工具链整体受影响才运行全量工具测试。涉及打包 / R8 / 类加载时运行 `:app:assembleDevelop :app:lintVitalDevelop` 及相应输出 / API 检查；涉及工具 / CI / catalog / matrix 时补对应的确定性与独立 mutation。ROM 行为或设备性能结论须有对应目标样本与实机证据。 | 受影响契约验证完整，回退范围明确；“性能”“Hook”等关键词本身不触发全量审计。设备不可用则保留待验，不把 Host / 构建通过写成 ROM 或设备通过。 |
| **Release** | 正式版本准备、签名、候选晋升、双仓发布 / 恢复：使用既有 [RELEASE](RELEASE.md) 工作流及其 exact main CI、版本、双语记录、历史证书、来源和原候选约束。 | 按该工作流的收据与双端读回验收；不得用 Quick / Standard 或本地通过替代正式门禁。 |

修改 `.github/`、发布工具、验证器、基线、契约扫描器、catalog / matrix、共享生成器或依赖配置时，按影响至少 Standard；影响门禁可信度、签名、产物或公共运行边界则 Deep，并读取受影响的正式发布合同。工具文件不因扩展名为 Python 就归 Quick。发布流程的代码维护不自动授权签名或公开发行。

按实际受影响契约确定范围，不能仅按文件数、耗时或缓存降级。公共状态 / ROM / R8 存在具体疑问时做能消除该疑问的检查，仍无法界定影响才扩大验证；不能以泛化的可能风险触发全套命令，也不能省略已证实必要的检查。

`fast --changed` / `--staged` 只覆盖未提交 / 暂存范围；已提交且尚无有效验证的改动用不带 changed 的模式，审阅范围仍是完整任务 diff。提交不使已有结果失效。普通生产源码的 `fast --changed` 默认只编译，需要行为回归必须显式选类。`verify.py` 不构建 APK、不连设备、不执行 R8，相应检查按影响另做或复用可信结果。

工具测试入口为 `python -m unittest discover -s tools/tests -p "test_<affected>.py"`；影响全工具链时用 `-p "test_*.py"`。不要把通配占位符当成已执行范围；最终结果记录实际测试类 / 文件及执行结果。失败在原任务修复，不通过删测试、降断言或吞异常制造通过。

源码合同测试复用 `src/test` 的 `testsupport.repositorySource`，从 Gradle 工作目录向上定位仓库；缺源码必须失败，不通过 ClassLoader URL 或 Assume 跳过。`test_rom_inventory.py` 在临时目录用既有 JDK / SDK 工具生成小 APK / DEX，使用仓库 JAR 验证 class 读取，不依赖历史安装包、厂商 ROM 或预先运行应用构建；固定 SDK 与 `ci_install_android_sdk.sh` 对齐。样本不进入 main 或 Git。

## 证据复用

可信记录包括被验证输入、命令 / 范围、相关环境与配置、实际退出结果及测试报告；可以来自本轮日志或同范围成功 CI，不另建长期缓存数据库。续接只更新发生变化的部分，按完整任务 diff 选择检查。

相同输入、环境与检查范围的有效结果直接复用；文档或提交信息变化时根据 diff 判断相关输入是否改变，无需重算 APK 摘要或重建来源证明。HEAD、分支名、测试数量、旧 XML 或 Gradle UP-TO-DATE 单独不足以证明通过；相关未提交修改仍须补验。

只对新增改动、失败检查、环境变化或未解决风险补验；一次集成后统一收口，不为 commit、PR 描述、最终报告或多 Agent 自报重复执行。失败后的成功记录须来自修复后的实际输入。不同范围的结果只证明交集，合并提交的新相互作用仍须覆盖。CI 要核对真实 head SHA / conclusion；发行另遵循 RELEASE，不能把来源等价推断用于替代其 exact SHA 门禁。

## 任务路由与收敛

任务 / 分支发现与续接只见 [DEVELOPMENT](DEVELOPMENT.md#git-与协作)。已知路径直接读，设置导航与大型数据按 [AGENTS 的入口](../AGENTS.md#按需入口)查询，不递归加载资料。

- **开始**：明确触发、预期行为、实际调用 / owner、回退单位和能识别缺陷的检查后实施，不等无关审计。
- **扩大范围**：仅当 ROM 声明、签名、ClassLoader 或实际调用存在具体疑问时补取证；已有定向证据不能界定影响才回退既有 Full。工具可用不构成全套启动理由。
- **失败**：先诊断，只在输入 / 环境变化或明确瞬时错误后有界重试；新风险补相关回归。Release 使用原工作流的超时与恢复规则。
- **结束**：授权目标完成、完整 diff 已审且必要检查通过后交付；实际模式、结果和未完成的必要验收分别说明，不因模式名称宣告通过或追加无关全量检查。

## 验证职责

Agent 按完整任务差异选择现有门禁、设备重放与各层待验边界，使用[自主验证入口](AGENTIC_VERIFICATION.md)。它不替代本页分级或正式发行门禁。

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

不以测试数量、静态命中或一次构建代替兼容结论。保留行为、备份 V2、偏好、所有权、热路径、API 边界和 issue 回归；只有无 production subject、完全重复或锁死错误细节的测试才可评估移除。Python 不重复实现 Kotlin 业务，也不以测试方法名匹配代替执行。共享 fixture 只读，写入和故障注入使用独立目录，清理在创建后立即注册；注入先确认干净基线及实际应用，再识别预期断言失败。

生成器确定性比较实际 Compose Kotlin、偏好目录、页面 ID 与搜索索引，要求产物非空；分组模型回归不能冒充最终文件验证。资源检查读取设置 JSON 和平台 XML，解析失败、缺失引用与翻译均须失败；同一次检查复用已解析输入，不设跨测试缓存。

## 关键回归

- 天气需求回归 `WeatherFormatDemandTest` / `WeatherRefreshLifecycleTest` 覆盖四种生效格式、隐藏日期、CC 格式既有规则、无需求零订阅 / 查询、关闭取消发布、灭屏恢复和 owner 替换；复用 `SystemClockHotPathTest` 的格式 / 秒钟能力回归。天气 owner 静态门禁同时识别两参数与需求参数调用，错误 owner 和漏掉 OOM 隔离仍须检出，不以更新合法调用形态放宽所有权 / fatal 合同。
- 输入成本回归 `StatusBarTouchCostTest` / `GestureBrightnessReadCostTest`：原生单次分发与异常、无整组参数读取、多指后加入亮度、无效控制中心起点、普通亮度读取失败与 fatal；复用既有 GestureMachine 状态 / 所有权压力回归。图标键与搜索字段由 `AppListMetadataTest` 检查；其中加载器接入部分是源码断言，不能当作 PM 调用次数或设备性能证据。
- 设置界面运行实际 FragmentManager、JSON 生成模型、Compose、动画和返回流程，覆盖四类页面、关于页、动画关闭、取消返回和 Activity 重建；断言页面可见且有可见选项。测试 `miui.os.Build` 只控制选项，不模拟 HyperOS。
- Fragment 动画创建时保留框架正在准备的位置 / alpha，否则可停在屏外。Activity 继续通过 Fragment 公开入口关闭预测预览；重新启用前须验证取消预览遗留位移及目标 HyperOS 手势，不能只看普通返回。
- Dock 覆盖 4→5→4、1 / 3–6 图标、子布局边界不变、拖拽结束 / 取消、文件夹 / 占位、空 Dock / 重填、空闲帧不读图标、尺寸控制独立；真实动画合成另需设备。
- Dock / Toast 的固定种子轨迹分别覆盖 owner 重建、0–6 图标、旋转、安全区、文字和字号极值；保留状态机长序列与释放断言。当前种子与步数以测试源为准。
- 取消路径包括滑动中的临时亮度与长按中断。ClassLoader 隔离须用同名类在一个加载器缺失、另一个可用的返回值证明；共享伪单例不能证明隔离。
- `DeviceInfoMonitorLifecycleTest` 检查单队列、灭屏 / 关闭、宿主替换、迟到发布与注册失败回滚；`PowerKeyLifecycleTest` 检查快速重按、重复 DOWN、亮屏 / 取消及原生异常；`MultiWindowEnableLifecycleTest` 检查完成释放和迟到任务归属。使用真实 Handler 队列，不能换算设备 CPU、内存或功耗。
- Hook 单参数读取由 `MethodHookBoundaryTest` 覆盖 before / after、空值、原方法后的值、共享改写数组、跳过与异常恢复；只读路径不取整组参数、不调用 `proceed(args)`。`HookArgumentAllocationTest` 用真实适配器比较旧数组读取与单参数读取，Host 计数只证明适配器分配差异，不代表 ART / JNI、设备 CPU 或帧率。
- 振动检查 native 一次、usage 分流、默认 / 异常回退、fatal 和跨午夜；预制效果及真实触感单独验收。
- 偏好发布回归用可控线程交错覆盖启动 / 批量快照与监听更新，安装门禁须等待完整发布，读取失败须保留有效值；同时保留已提交高度覆盖旧缓存、真实删除 / 默认恢复、Feature 单次安装和悬浮窗策略跨刷新冻结的关联回归。
- 通知、媒体返回、状态栏几何与 Insets 的当前待验项集中在[路线图](maintenance/A14_REFINEMENT_ROADMAP.md#待实机验收)，不重复维护设备结果。

## CI

普通 CI 在 main push、PR、`r14.*` tag 与手动触发；main 纯 Markdown 跳过构建，tag 不受路径过滤影响。[ci_scope.py](../tools/ci_scope.py)按完整 diff 选择额外工具门禁，不安排定时构建或依赖自动更新。

同一 job 执行 Python / 源码契约、Full、一次 Develop 和 fatal lint；所有成功运行保留唯一 Develop APK 与 mapping，R8 输出始终检查。工具 / CI / catalog / matrix、tag 和手动运行另做矩阵确定性与必需独立 mutation。需要诊断可重复性时才做两次隔离构建与 `apk_semantic_diff.py`，不加入日常 CI。

CodeQL Default setup 保留 Python / Actions。默认构建不能准备本项目 JDK 27 时，用手动 [Kotlin CodeQL](../.github/workflows/a14-codeql-kotlin.yml)：复用现有 JDK / SDK / Gradle，只编译生产 Kotlin / Java，分析后由 `check_codeql_extraction.py` 核对全部已跟踪主源码的成功提取及非零 Kotlin 代码指标。SARIF 保留 7 天，暂不重复上传 Security；Java/Kotlin 的持续告警接入须在主线迁移 Advanced setup 后再停用 Default，不能提前留下扫描空档。提取失败与安全发现分别报告，不以零告警证明绝对安全，也不增加正式发行门禁。

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

## 本轮设备刷新率取证

60 / 120 Hz 以采样时的实际 display mode / render frame rate 及 FrameTimeline 为准，不能用设置请求值代替。2026-11-06 的 A14 MISettings 定向反汇编确认：SDK 33 起 `user_refresh_rate` 读写位于 Settings.Secure；对应分支还消费 `miui_refresh_rate`，仅写同名 System 键不会落实本机 60 Hz。取证前分别保存原值，采样结束恢复；此为本轮样本的测量环境说明，不作为所有 ROM 的通用写入配方。GPU memory 数据源不等于 GPU render stages，未提供后者的设备不能据 UI / RenderThread 时长宣称 GPU 用时或功耗收益。
