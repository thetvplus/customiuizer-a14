# 测试

## 本地入口

环境与收口命令见 [DEVELOPMENT.md](DEVELOPMENT.md)。

| 层 | 入口 | 证明范围 |
| --- | --- | --- |
| 静态契约 | `tools/verify.py`、`tools/check-invariants.py` | SDK / API、偏好、Feature、生命周期和热路径源码规则 |
| Python 工具 | `python -m unittest discover -s tools/tests -p "test_*.py"` | 工具行为、源码/资源契约、ROM matrix、CI 配置 |
| Android JVM | `testDebugUnitTest` | 行为、契约与回归；不运行真实 ROM Hook |
| 独立缺陷注入 | `tools/brutal_test_runner.py` | 注入缺陷是否被真实独立门禁拒绝 |
| Full CI | `a14-ci.yml` 的 Full job | 无缓存 develop 双构建、APK 内容、R8 mapping 与 fatal lint |
| 实机 | 指定 APK / ROM 下的操作与日志 | 实际安装、Hook、UI、生命周期和性能，限于已测场景 |

静态命中数、用例数或一次绿构建不代表已知问题数量与全设备兼容性。保留行为、备份 V2、偏好、所有权、热路径、Dynamic Island、API 边界与 issue 回归测试；只有无 production subject、完全重复或锁死错误实现细节才可删除测试。

## CI 分工

工作流 [a14-ci.yml](../.github/workflows/a14-ci.yml) 在 main push、PR、`r14.*` tag、每周和手动触发；[ci_scope.py](../tools/ci_scope.py) 根据完整 diff 选择追加检查。

- Fast 每次运行源码契约 / Python 工具测试和 `verify.py full`。工具、CI、catalog、matrix 改动及定期 / tag / 手动执行还检查矩阵确定性与必需独立缺陷注入。
- Full 依赖同提交 Fast 通过；构建、依赖、混淆、工具、CI、catalog、matrix 改动，定期 / tag / 手动，或 main 提交含 `[full-ci]` 时触发。
- 未触发 Full 时，Fast 构建一次 develop 并运行 fatal lint；Full 不重复 JVM / debug lint / Python 套件，而增加两次 clean develop、内容与 mapping 比较和 develop fatal lint。
- 双构建禁用 build / configuration cache 与 Kotlin 增量编译，分别使用独立 Gradle 进程。下载缓存可复用。
- PR 新提交取消旧执行；main / tag 正常完成。诊断保留 7 天，Full develop / mapping 保留 30 天。

独立 mutation 的名单与数量以 [brutal_test_config.json](../tools/brutal_test_config.json) 为准。`ACTIVE_INDEPENDENT`、`BLOCKED_NO_INDEPENDENT_GATE` 与 `MUTATOR_STALE` 必须分开报告；self-detection 与未覆盖项不算独立通过。

## 依赖与构建变更

Actions 固定完整 commit SHA；JDK 下载保留 `force-download: true` 与 `verify-signature: true`，wrapper 保留 SHA-256。CI 契约使用固定 PyYAML 解析实际 YAML，允许合法别名、引号、内联与多行语法，继续检查无正式签名材料、平台对齐与构建独立性。

SDK package 与 Gradle 的 compileSdk / buildToolsVersion 必须一致，具体安装参数以 [ci_install_android_sdk.sh](../tools/ci_install_android_sdk.sh) 为准。主机构建 classpath 与 APK runtime 依赖分开审查；固定版本和安全约束保存在构建配置，避免在文档复制易失效清单。

Dependabot 仅提出候选 PR，范围见 [.github/dependabot.yml](../.github/dependabot.yml)。升级应查看官方变更、实际依赖图与最终 APK：AGP 变动还核查内部资源压缩任务、AAPT2 输出、R8 mapping、fatal lint 和无缓存重复构建；运行库还需目标 ROM 验证。不能把转递冲突选择等同于 APK 重复类，也不能把已被 R8 去除的依赖推算为额外体积收益。

依赖校验见 [Gradle 官方说明](https://docs.gradle.org/9.6.1/userguide/dependency_verification.html)，编译器 / D8 / R8 要求见 [Android 官方说明](https://developer.android.com/build/kotlin-support)。升级时重新核对这些范围，不把上游“最新”当作本项目的升级理由。

## 实机边界

历史样本索引和未确认行为见 [COMPATIBILITY.md](../COMPATIBILITY.md)。记录 APK revision、设备 / ROM、开关、操作、日志与前后对照；重复稳定的设备连接和实际操作才可标为 DEVICE。主机 JVM 或独立 ART probe 应注明隔离环境，不能当作完整应用的帧率、内存或功耗改善。
