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
| Full CI | `a14-ci.yml` 的 Full job | 无缓存 develop 双构建、APK 内容、R8 mapping 与 fatal lint |
| 实机 | 指定 APK / ROM 下的操作与日志 | 实际安装、Hook、UI、生命周期和性能，限于已测场景 |

静态命中数、用例数或一次绿构建不代表已知问题数量与全设备兼容性。保留行为、备份 V2、偏好、所有权、热路径、Dynamic Island、API 边界与 issue 回归测试；只有无 production subject、完全重复或锁死错误实现细节才可删除测试。

Python 套件验证工具、生成器、资源及必要的源码边界；Kotlin 行为由 JVM / Robolectric 测试直接执行。避免另写只检查 Kotlin 测试文件或方法名的门禁，也不以固定旧提交和不断增长的例外表约束当前设置。保留独立缺陷注入及 Hook 安装、所有权和类加载接入检查。

设置界面回归使用仅供测试的 Robolectric，覆盖首页四个分类及其设置页、关于页返回、关闭动画、取消返回手势和 Activity 重建。断言页面留在屏幕内、XML 加载成功且列表有可见选项；不仅检查 Fragment 存在。测试中的 `miui.os.Build` 仅提供控制选项可见性的 ROM 标志，不模拟 HyperOS 框架或 Hook。

`r14.23.1` 的白屏可在此模型中复现：旧动画创建时修改透明度和水平位置，使 Fragment 1.9 跳过入场动画，页面停在屏幕外。动画创建阶段必须保留框架正在准备的可见性状态。模型回归通过不能标为 HyperOS 实机验收；Fragment、动画或导航改动仍需目标设备检查。

设置 Activity 保留升级前的完成手势后返回行为，通过 Fragment 的公开控制入口关闭升级默认引入的预测预览。在 1.9.1 模型中，取消预览会保留部分水平位移；直接使用平台 AnimatorSet 反向播放能回到原位，失败发生在 Fragment 的取消流程。重新启用预测预览前，必须验证这一回归及目标 HyperOS 手势，而不是只测试普通返回。

## 分阶段验证与 PR 合并

- 先按 PR 的 exact head 审查 diff、官方变更与已有 CI；只补尚缺的证据。
- 同批相关 PR 整合后统一运行一次完整本地门禁和必要 APK 构建。运行库变化按影响做一次实机定向验收，不逐 PR 重跑全套。
- 源码未变且相同范围已通过时复用结果；只有新修改、失败或未解决问题才追加相关检查。纯文档变更只做对应静态检查。
- 正式 APK 在发布前完成本地签名、元数据与必要实机验收；发布后一次比对双仓 tag、notes、latest 和 asset 大小 / digest。可信 digest 一致时无需再次下载；缺失、异常或用户要求时再补下载核验。

## CI 分工

仅保留核心工作流 [a14-ci.yml](../.github/workflows/a14-ci.yml)，在 main push、PR、`r14.*` tag 和手动触发；[ci_scope.py](../tools/ci_scope.py) 根据完整 diff 选择追加检查。不安排定时构建或依赖自动更新任务。

- Fast 每次运行源码契约 / Python 工具测试和 `verify.py full`。工具、CI、catalog、matrix 改动及 tag / 手动执行还检查矩阵确定性与必需独立缺陷注入。
- Full 依赖同提交 Fast 通过；构建、依赖、混淆、工具、CI、catalog、matrix 改动，tag / 手动，或 main 提交含 `[full-ci]` 时触发。
- 未触发 Full 时，Fast 构建一次 develop 并运行 fatal lint；Full 不重复 JVM / debug lint / Python 套件，而增加两次 clean develop、内容与 mapping 比较和 develop fatal lint。
- 双构建禁用 build / configuration cache 与 Kotlin 增量编译，分别使用独立 Gradle 进程。下载缓存可复用。
- PR 新提交取消旧执行；main / tag 正常完成。诊断保留 7 天，Full develop / mapping 保留 30 天。

独立 mutation 的名单与数量以 [brutal_test_config.json](../tools/brutal_test_config.json) 为准。`ACTIVE_INDEPENDENT`、`BLOCKED_NO_INDEPENDENT_GATE` 与 `MUTATOR_STALE` 必须分开报告；self-detection 与未覆盖项不算独立通过。

## 依赖与构建变更

Actions 固定完整 commit SHA；JDK 下载保留 `force-download: true` 与 `verify-signature: true`，wrapper 保留 SHA-256。CI 契约使用固定 PyYAML 解析实际 YAML，允许合法别名、引号、内联与多行语法，继续检查无正式签名材料、平台对齐与构建独立性。

SDK package 与 Gradle 的 compileSdk / buildToolsVersion 必须一致，具体安装参数以 [ci_install_android_sdk.sh](../tools/ci_install_android_sdk.sh) 为准。主机构建 classpath 与 APK runtime 依赖分开审查；固定版本和安全约束保存在构建配置，避免在文档复制易失效清单。

依赖升级由维护任务按需处理，查看官方变更、实际依赖图与最终 APK：AGP 变动还核查内部资源压缩任务、AAPT2 输出、R8 mapping、fatal lint 和无缓存重复构建；运行库还需目标 ROM 验证。不能把转递冲突选择等同于 APK 重复类，也不能把已被 R8 去除的依赖推算为额外体积收益。

依赖校验见 [Gradle 官方说明](https://docs.gradle.org/9.6.1/userguide/dependency_verification.html)，编译器 / D8 / R8 要求见 [Android 官方说明](https://developer.android.com/build/kotlin-support)。允许以新版本 / API 的兼容性探索作为升级理由，无需先证明性能收益；仍须核对这些范围并执行相应回归，版本提高本身不是验证通过的证据。

## 实机边界

历史样本索引和未确认行为见 [COMPATIBILITY.md](../COMPATIBILITY.md)。记录 APK revision、设备 / ROM、开关、操作、日志与前后对照；重复稳定的设备连接和实际操作才可标为 DEVICE。主机 JVM 或独立 ART probe 应注明隔离环境，不能当作完整应用的帧率、内存或功耗改善。
