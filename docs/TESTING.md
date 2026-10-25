# 测试与验证

命令和复用规则以 [AGENTS.md](../AGENTS.md#工具链与验证) 为准；环境与变体见 [DEVELOPMENT](DEVELOPMENT.md)。

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

- 设置界面运行实际 FragmentManager、XML、动画和返回流程，覆盖四类页面、关于页、动画关闭、取消返回和 Activity 重建；断言页面可见且有可见选项。测试 `miui.os.Build` 只控制选项，不模拟 HyperOS。
- Fragment 动画创建时保留框架正在准备的位置 / alpha，否则可停在屏外。Activity 继续通过 Fragment 公开入口关闭预测预览；重新启用前须验证取消预览遗留位移及目标 HyperOS 手势，不能只看普通返回。
- Dock 覆盖 4→5→4、1 / 3–6 图标、子布局边界不变、拖拽结束 / 取消、文件夹 / 占位、空 Dock / 重填、空闲帧不读图标、尺寸控制独立；真实动画合成另需设备。
- Dock / Toast 的固定种子轨迹分别覆盖 owner 重建、0–6 图标、旋转、安全区、文字和字号极值；保留状态机长序列与释放断言。当前种子与步数以测试源为准。
- 取消路径包括滑动中的临时亮度与长按中断。ClassLoader 隔离须用同名类在一个加载器缺失、另一个可用的返回值证明；共享伪单例不能证明隔离。
- 振动检查 native 一次、usage 分流、默认 / 异常回退、fatal 和跨午夜；预制效果及真实触感单独验收。
- 偏好发布回归用可控线程交错覆盖启动 / 批量快照与监听更新，安装门禁须等待完整发布，读取失败须保留有效值；同时保留已提交高度覆盖旧缓存、真实删除 / 默认恢复、Feature 单次安装和悬浮窗策略跨刷新冻结的关联回归。
- 通知、媒体返回、状态栏几何与 Insets 的当前待验项集中在[路线图](maintenance/A14_REFINEMENT_ROADMAP.md#待实机验收)，不重复维护设备结果。

## CI

普通 CI 在 main push、PR、`r14.*` tag 与手动触发；main 纯 Markdown 跳过构建，tag 不受路径过滤影响。[ci_scope.py](../tools/ci_scope.py)按完整 diff 选择额外工具门禁，不安排定时构建或依赖自动更新。

同一 job 执行 Python / 源码契约、Full、一次 Develop 和 fatal lint；所有成功运行保留唯一 Develop APK 与 mapping，R8 输出始终检查。工具 / CI / catalog / matrix、tag 和手动运行另做矩阵确定性与必需独立 mutation。需要诊断可重复性时才做两次隔离构建与 `apk_semantic_diff.py`，不加入日常 CI。

main / PR 新提交取消旧运行，tag 正常完成；诊断 7 天，检查产物 30 天，历史清理保留正式发行的成功验证记录。正式自动发布、同 SHA CI 复用、Environment 审核、顺序双端发布和原候选恢复只维护在 [RELEASE](RELEASE.md)，普通 CI 永不接触正式密钥。

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

SDK 安装与 Gradle 输入一致；宿主 classpath 与 APK runtime 分开审查。依赖变化按影响检查解析图、编译与 DEX 输出、R8 / API 隔离、签名及必要设备路径；不把 JVM 25 当作 Android API 可用性证明。

发布故障回归覆盖 workflow / event / 仓库 / attempt / SHA、缺审核、确认期间 main 前进、错误证书 / 凭据、篡改、版本 / 双语记录、草稿资产、预发行 Latest 与双端部分失败。只允许 Validate / Sign 成功而 Publish 失败的原 attempt 恢复；测试模拟服务器不能证明远端 Secrets / 权限或设备通过。

发布工具测试覆盖原生 main / exact CI、零人工审批的 main-only 环境、指定正式目标与试用隔离、累计说明 / 安全两基线、无条数上限的双语内容、Artifact ID / 归档摘要及安全解压、原候选失败恢复、顺序双仓资产和下载文档幂等性。它们使用模拟 API，不触发正式签名或远端公开；远端环境和实际清理另以 API 读回核实，完整规则见 [发布与恢复](RELEASE.md)。
