# 测试

## 层

| 层 | 入口 | 用途 |
|---|---|---|
| 静态契约 | `tools/verify.py`、`tools/check-invariants.py` | SDK、作用域、API 101/102、热路径危险模式 |
| Python 工具 | `python -m unittest discover -s tools/tests -p "test_*.py"` | 工具、ROM matrix、CI 可移植性 |
| Android JVM | `testDebugUnitTest` | 行为、契约、生命周期、回归 |
| Brutal | `tools/brutal_test_runner.py` | 独立 kill：CI、catalog、fatal、observer、matrix |
| Full CI | GitHub Actions `a14-full-ci.yml` | 双 develop 构建、APK semantic diff、lintVital |

## CI 的职责与证据

`Fast CI` 和 `Full CI` 是本仓库维护的 GitHub Actions 工作流，名称不对应上游产品版本。

- Fast CI 每次 PR / main 更新运行 Python 工具测试、治理检查、hermeticity、matrix determinism、完整 JVM 测试与 debug lint，再构建 debug / develop。工具测试和治理命令在 hermeticity 内只执行一次，失败日志保留；两种 APK 在同一次 Gradle 调用中构建。
- Full CI 在构建、依赖、混淆规则或 CI 工具的 PR，以及手动、每周、发布 tag 和 `[full-ci]` main 提交运行。它增加独立缺陷注入、两次禁止 build/configuration cache 的 clean develop 构建、APK 内容与 R8 mapping 对比、develop fatal lint 和必需产物检查。
- Full CI 的 clean 会清除 JVM / lint 报告，因此先将报告保存在 runner 临时目录；诊断产物保留 7 天，develop APK / mapping 保留 30 天。Fast CI 只在失败时上传诊断。
- Gradle 下载缓存可复用；两次可重复性构建禁用 Kotlin 增量编译，并使用进程内编译随独立 Gradle 进程退出，必须实际重新编译和混淆，不能用缓存命中充当独立构建。

Brutal suite 当前要求 11 项独立缺陷注入被真实门禁拦截。配置中的其余覆盖状态需按 `ACTIVE_INDEPENDENT` / `BLOCKED_NO_INDEPENDENT_GATE` / `MUTATOR_STALE` 解读，不能把 self-detection 或未覆盖项算作通过的运行行为验证。

## 依赖升级

Actions 使用完整 commit SHA，并明确校验 JDK 下载签名；Gradle wrapper 保留发行包 SHA-256。
CI 安装的 SDK build tools 必须与 Gradle 的显式 `buildToolsVersion` 相同；当前固定 36.0.0，compile SDK 固定安装 `android-37.1`。安装脚本和构建选择漂移会被契约检查拒绝。
Dependabot 每周提交 Gradle 依赖候选 PR，最多同时 3 个；编译器、libxposed ABI 和 DexKit 原生引擎只自动提出补丁候选。每个升级仍需审查 diff、官方变更和完整构建结果。
AGP 补丁需检查 debug / develop、R8 mapping、无缓存可重复性和 lint；运行库的升级还需目标 HyperOS 1 / Android 14 验证。编译与 JVM 通过不代替实机兼容性结论。
本地 `verify.py fast --changed` / `--staged` 遇到版本清单、Gradle 配置、wrapper 或编译用 JAR 变化时也运行 JVM 测试；仅文档或 CI 工具变更仍可跳过 Gradle。

构建插件的依赖图与 APK 的运行依赖图需分别审查。当前 AGP 补丁仍默认带入旧 KGP，因此根构建脚本显式使用已修复缓存反序列化问题的 KGP 2.4.20，并对 Commons、jose4j、JDOM、Bouncy Castle 构建依赖设置安全版本约束。应用继续显式使用 Kotlin stdlib / BOM 2.3.21、语言/API 2.2 和 JVM 17，禁止编译器升级隐式抬升 APK 运行库。

优先保留行为测试、兼容契约、备份 V2、preference、lifecycle ownership、hot-path 回归、正式 Dynamic Island、API 边界和 issue 回归。

删除测试的唯一理由：无 production subject、完全重复、或锁死错误实现细节。不得靠删测试制造绿构建。

## 实机

静态通过不等于目标 ROM 可用。实机证据按 `STATIC` / `BUILD` / `LOG` / `DEVICE` 分级。无证据不得改成熟热路径。
