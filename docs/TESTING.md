# 测试

## 本地入口

环境与收口命令见 [DEVELOPMENT.md](DEVELOPMENT.md)。

| 层 | 入口 | 证明范围 |
| --- | --- | --- |
| 静态契约 | `tools/verify.py`、`tools/check-invariants.py` | SDK / API、偏好、Feature、生命周期和热路径源码规则 |
| Python 工具 | `python -m unittest discover -s tools/tests -p "test_*.py"` | 工具行为、源码/资源契约、ROM matrix、CI 配置 |
| Android JVM | `testDebugUnitTest` | 行为、契约与回归；不运行真实 ROM Hook |
| 设置界面模型 | `PreferenceNavigationUiTest`，由上述 JVM 任务执行 | 在 Android 14 模型中运行实际 FragmentManager、偏好 XML、动画与返回流程 |
| 独立缺陷注入 | `tools/brutal_test_runner.py` | 注入缺陷是否被真实独立门禁拒绝 |
| CI APK | `a14-ci.yml` | 一次未签名 develop、R8 输出与 fatal lint |
| 正式候选 | `a14-signed-build.yml` | 同 SHA 门禁、一次正式 Release、签名 / 元数据 / 来源证明 |
| 实机 | 指定 APK / ROM 下的操作与日志 | 实际安装、Hook、UI、生命周期和性能，限于已测场景 |

静态命中数、用例数或一次绿构建不代表已知问题数量与全设备兼容性。保留行为、备份 V2、偏好、所有权、热路径、Dynamic Island、API 边界与 issue 回归测试；只有无 production subject、完全重复或锁死错误实现细节才可删除测试。

Python 套件验证工具、生成器、资源及必要的源码边界；Kotlin 行为由 JVM / Robolectric 测试直接执行。避免另写只检查 Kotlin 测试文件或方法名的门禁，也不以固定旧提交和不断增长的例外表约束当前设置。保留独立缺陷注入及 Hook 安装、所有权和类加载接入检查。

设置界面回归使用仅供测试的 Robolectric，覆盖首页四个分类及其设置页、关于页返回、关闭动画、取消返回手势和 Activity 重建。断言页面留在屏幕内、XML 加载成功且列表有可见选项；不仅检查 Fragment 存在。测试中的 `miui.os.Build` 仅提供控制选项可见性的 ROM 标志，不模拟 HyperOS 框架或 Hook。

历史设置白屏可在此模型中复现：旧动画创建时修改透明度和水平位置，使 Fragment 1.9 跳过入场动画，页面停在屏幕外。动画创建阶段必须保留框架正在准备的可见性状态。模型回归通过不能标为 HyperOS 实机验收；Fragment、动画或导航改动仍需目标设备检查。

设置 Activity 保留升级前的完成手势后返回行为，通过 Fragment 的公开控制入口关闭升级默认引入的预测预览。在 1.9.1 模型中，取消预览会保留部分水平位移；直接使用平台 AnimatorSet 反向播放能回到原位，失败发生在 Fragment 的取消流程。重新启用预测预览前，必须验证这一回归及目标 HyperOS 手势，而不是只测试普通返回。

## 分阶段验证与 PR 合并

- 先按 PR 的 exact head 审查 diff、官方变更与已有 CI；只补尚缺的证据。
- 同批相关 PR 整合后统一运行一次完整本地门禁和必要 APK 构建。运行库变化按影响做一次实机定向验收，不逐 PR 重跑全套。
- 源码未变且相同范围已通过时复用结果；只有新修改、失败或未解决问题才追加相关检查。纯文档变更只做对应静态检查。
- 正式 APK 在发布前完成本地签名、元数据与必要实机验收；发布后一次比对双仓 tag、notes、latest 和 asset 大小 / digest。可信 digest 一致时无需再次下载；缺失、异常或用户要求时再补下载核验。

## CI 分工

普通 [a14-ci.yml](../.github/workflows/a14-ci.yml) 在 main push、PR、`r14.*` tag 和手动触发；main 的纯 Markdown 推送不启动构建，tag 验证不受路径过滤影响。[ci_scope.py](../tools/ci_scope.py) 根据完整 diff 选择追加工具检查。不安排定时构建或依赖自动更新任务。

- 同一个 job 执行源码契约 / Python 工具测试、`verify.py full`、一次 develop 和 fatal lint；没有第二次 APK 构建或重复准备环境。
- 工具、CI、catalog、matrix 改动及 tag / 手动执行还检查矩阵确定性与必需独立缺陷注入。
- 所有成功的普通 CI 都保留唯一 develop APK 与 mapping；不再按 scope 隐藏下载产物，也不追加第二次构建。R8 输出始终检查。专门排查构建可重复性时可手动运行两次隔离构建及 `apk_semantic_diff.py`；不加入日常 CI。
- main / PR 新提交取消同一分支的旧执行，tag 验证正常完成。诊断保留 7 天，检查产物保留 30 天；清理历史运行时保留正式发行的成功验证记录。

独立 [正式构建与自动发布](RELEASE.md#actions-构建与自动发布) 在 main 的 A14 CI 成功后检查新版本和双语记录，进入 release-signing 的一次人工确认；同 SHA 的完整 CI 直接复用。手动入口默认 both，也可选择 source、actions-only 或预发行。正式发布要求 main 的成功 CI，普通 PR 或旧 SHA 的绿灯不能替代。主仓库公开资产核验通过后，才用同一 APK 顺序发布 Xposed；两个入口共用发布锁。

发布回归覆盖上游 workflow / event / repository / attempt / SHA、审核配置缺失、确认期间 main 前进、缺失凭据、错误证书、APK / provenance 篡改、版本与 Changelog 准备、草稿资产错误和预发行不设 Latest。第二端失败的恢复仅接受同一候选，核验并跳过已成功版本、只补同源草稿的缺失资产。失败 run 只有 Validate / Sign 已成功而 Publish 失败时才允许恢复；签名失败、取消和变更后的 attempt 仍拒绝。测试模拟 GitHub 服务器与部分上传故障，不等于已验证远端 Secrets / Environment 或真实发布权限。普通 CI 的签名泄漏 mutation 继续保留。

Dock 定向回归覆盖 4 → 5 → 4、1 / 3–6 个图标、容器边界不变的子布局、拖拽结束 / 取消、文件夹与占位、空 Dock 和重新填充、空闲帧不读图标，以及高度 / 间距 / 圆角独立。使用原生事件顺序和 Android 14 渲染模型；目标 ROM 上还需连续拖拽、重排与动画验收。

独立 mutation 的名单与数量以 [brutal_test_config.json](../tools/brutal_test_config.json) 为准。`ACTIVE_INDEPENDENT`、`BLOCKED_NO_INDEPENDENT_GATE` 与 `MUTATOR_STALE` 必须分开报告；self-detection 与未覆盖项不算独立通过。

## 按需行为与故障验证

`verify.py fast / full` 共用同一组源码检查，范围及失败语义保持一致。开发中按改动选择测试类，已有同范围结果可复用；无需为测试工具改动重建 APK。

```powershell
python tools/verify.py fast --changed --tests DockBackgroundTest SystemUIToastPresentationTest SystemAudioFocusChainTest
python tools/brutal_test_runner.py --config tools/brutal_test_config.json mutate --behavior-only
```

行为变异只在需要时执行，普通 CI 的 `--required-only` 仍运行原有 11 项静态安全门禁。四项新增独立门禁分别注入取消误提交、去重失效、跨 ClassLoader 缓存污染、插件运行时释放遗漏；复用实际状态机、动作门禁、反射缓存与 owner 测试，不为这些缺陷新增生产状态或改写基础设施。变异从**已提交 HEAD** 建临时 worktree；相同测试命令先跑一次干净基线，失败即停止该组，不能把原有失败算成检出。

底层 `python tools/verify.py behavior --tests <完整测试类名>` 不运行源码扫描，不接收变异名称。强制重跑选定 JVM 任务并检查新生成的 JUnit：0 为执行通过，1 仅限实际测试失败，2 为整类未执行，3 为编译、工具、环境、陈旧报告或矛盾结果。只有 1 可计独立检出；保留失败测试摘要。临时 worktree 只复制 `sdk.dir`，不复制签名配置。

取消回归必须包含滑动中临时亮度与长按中断；只验证 DOWN 后 CANCEL 回到 IDLE 无法发现误提交。ClassLoader 隔离用同名类在一个加载器中缺失、另一个中可用的实际返回值验证；两侧共用同一伪单例会误绿。临时工作区校验目录和 Git 注册均已移除，Windows 文件锁仅有界重试；异常原样传播，清理失败仍使整次任务失败，并记录清理前的门禁结果。

Dock 用种子 `0xA14 / 0xD0C`、各 32 步复放 0–6 图标、拖拽提交 / 取消及 owner 重建，检查拖拽期间尺寸稳定、提交后匹配有效布局且图标不被改动。Toast 用 `0xA14 / 0x70A5`、各 48 步交替横竖屏、安全区、文本 / 字号、定位极值与 View 重建，检查完整内容位于当前安全区。随机烟雾测试从 2000 步缩为 160 步并补释放断言；既有三种子、3 万步的状态机行为模型保留。

这些测试不模拟真实插件加载、Binder、ROM 动画合成或硬件振幅能力。振动回归验证 native 调用一次、usage 分流、默认 / 异常回退、致命错误传播及跨午夜时段；预制效果与实机触感单独验收。

## 依赖与构建变更

Actions 固定完整 commit SHA；JDK 下载保留 `force-download: true` 与 `verify-signature: true`，wrapper 保留 SHA-256。CI 契约使用固定 PyYAML 解析实际 YAML，允许合法别名、引号、内联与多行语法。普通 CI 永禁签名；受保护流程仅对已识别、完整满足契约的签名步骤开放，其他字段继续扫描，不以文件名整体豁免。平台对齐、最小权限、原生 exact SHA、清理、签名后证明和验证后上传均受检查。

SDK package 与 Gradle 的 compileSdk / buildToolsVersion 必须一致，具体安装参数以 [ci_install_android_sdk.sh](../tools/ci_install_android_sdk.sh) 为准。主机构建 classpath 与 APK runtime 依赖分开审查；固定版本和安全约束保存在构建配置，避免在文档复制易失效清单。

依赖升级由维护任务按需处理，查看官方变更、实际依赖图与最终 APK：AGP 变动还核查内部资源压缩任务、AAPT2 输出、R8 mapping、fatal lint 和无缓存重复构建；运行库还需目标 ROM 验证。不能把转递冲突选择等同于 APK 重复类，也不能把已被 R8 去除的依赖推算为额外体积收益。

依赖校验见 [Gradle 官方说明](https://docs.gradle.org/9.6.1/userguide/dependency_verification.html)，编译器 / D8 / R8 要求见 [Android 官方说明](https://developer.android.com/build/kotlin-support)。允许以新版本 / API 的兼容性探索作为升级理由，无需先证明性能收益；仍须核对这些范围并执行相应回归，版本提高本身不是验证通过的证据。

## 实机边界

历史样本索引和未确认行为见 [COMPATIBILITY.md](../COMPATIBILITY.md)。记录 APK revision、设备 / ROM、开关、操作、日志与前后对照；重复稳定的设备连接和实际操作才可标为 DEVICE。主机 JVM 或独立 ART probe 应注明隔离环境，不能当作完整应用的帧率、内存或功耗改善。

轻量采集仅按需使用，不加入普通 CI：

```powershell
# 15 秒内手动操作 Dock、Toast、控制中心或音量面板
python tools/adb_stress.py --scenario observe --observe-seconds 15
# 固定种子、有限次数的桌面 / 最近任务 / 返回导航
python tools/adb_stress.py --scenario navigation --seed 0xA14 --steps 24
```

多设备用 `--serial` 指定；工具先确认稳定连接，不安装 APK、不清理数据或日志、不使用 root。输出默认在忽略目录 `.di-evidence/adb-stress/`：操作接受数、前后 PID / PSS / 线程、原始帧与 CPU 快照、从设备起始时间筛选的 crash / events 日志。未知指标为 null，保留真实 0；断连或无设备为 `UNAVAILABLE`，退出 2，不算通过。`COLLECTED` 仅表示采集完成，不能证明操作体验、性能或没有崩溃 / ANR；日志缓冲区、权限和 ROM 支持仍有限制。原始日志可能含个人信息，不提交入库。2026-10-21 本机无设备，真实 ADB 操作及性能尚未验证。

## r14.25.2 动态岛回归范围

五项音乐映射、单击 / 双击、长按 / 拖动、多指 / CANCEL、无障碍及配置失效使用 `IslandMusicGestureTest` 的实际 View；动作能力、音量与位置使用 `IslandMusicActionsTest`。`IslandActivityLifecycleTest` 的窗口替身会将 View 真正附着至 Android 14 模型并触发预绘制，区分 PREPARED / VISIBLE；未显示的暂停、停止及延迟时序逐帧检查无意外窗口与状态栏占用。真实布局推进模型帧时钟，期限测试锚定原事件 deadline，不把布局耗时另算新保留期。

设置动作编辑、默认摘要、320dp 中文布局、分类监听和通知允许名单分别由导航、偏好、系统活动和 Controller 回归覆盖。此范围没有真实 HyperOS Hook、蓝牙 / 投放硬件或功耗测量；设备待验项见[音乐手势](features/island/MUSIC_GESTURES.md)。

## 动态岛后续专项回归

`IslandRegressionReproductionTest` 原样在 d6f47fd 基线复现四项失败，在修复分支验证：错误回中、缺 MOVE 的短上滑、模式 4 反馈迁移与旧备份恢复。播放器缺失入口另在原基线 Controller 复现。`IslandEdgeGeometryTest` 覆盖实际摄像头宽度、密度、长短 / 手动宽度、偏移与纵向避让；`IslandTouchWindowTest` 使用真实边缘起点和不同 DPI，并通过 A14 内部 Insets 回调验证生产窗口收到的触摸区域。状态栏回归使用屏幕坐标核对左右及下移后的逐图标恢复。

`IslandPlayerLaunchTest` 覆盖成功一次、缺失 / 取消入口、旧会话、无应用及安全失败；Controller 实际 View 端到端覆盖单击和下滑映射。`IslandAudioSpectrumTest` 与 `AudioFftBandsTest` 分离验证真实 FFT 数学和受控 Backend 生命周期；Controller 测试验证可见性、暂停、遮挡、关闭及释放。正常采集测试使用注入的音频替身，不证明 HyperOS 的权限、驱动或蓝牙 / 远程路由已通过。配置生成工具检查五组、十反馈、五手势、真实搜索目标和模式单选；升级 / 备份测试保留旧键和值。
