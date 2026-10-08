# 测试

## 层

| 层 | 入口 | 用途 |
|---|---|---|
| 静态契约 | `tools/verify.py`、`tools/check-invariants.py` | SDK、作用域、API 101/102、热路径危险模式 |
| Python 工具 | `python -m unittest discover -s tools/tests -p "test_*.py"` | 工具、ROM matrix、CI 可移植性 |
| Android JVM | `testDebugUnitTest` | 行为、契约、生命周期、回归 |
| Brutal | `tools/brutal_test_runner.py` | 独立 kill：CI、catalog、fatal、observer、matrix |
| Full CI | GitHub Actions `a14-ci.yml` 的 Full CI job | 双 develop 构建、APK semantic diff、lintVital |

## CI 的职责与证据

`Fast CI` 和 `Full CI` 是本仓库维护的 GitHub Actions 工作流，名称不对应上游产品版本。

- 同一个 `a14-ci.yml` 编排 Fast / Full 两个 job。Fast 每次 PR / main 更新只执行一遍代码静态门禁、完整 JVM 测试和 debug lint。普通代码修改再构建 develop APK 并运行 fatal lint，省去冗余 debug APK。
- Python 测试和源码契约每次 Fast 执行一次，因为它们也验证生产源码、资源、版本和 feature matrix。工具、CI、feature catalog 或 matrix 改动，以及手动、每周和 tag，额外执行 matrix determinism 和 11 项独立缺陷注入；self-detection-only 项保留在本地诊断 suite。
- Full 使用 `needs: fast`，只在同一提交的 Fast 通过后运行，避免再次执行 JVM 测试、debug lint 和 Python 工具测试。构建、依赖（含 app/lib/framework.jar）、混淆、CI / 工具与 catalog 改动，以及手动、每周、发布 tag 或 `[full-ci]` 提交触发 Full。
- Full 增加两次禁止 build/configuration cache 的 clean develop 构建、APK 内容和 R8 mapping 对比、develop fatal lint 与必需产物检查。普通 PR 不做两遍 clean 构建。
- PR 新提交自动取消旧提交的执行；main / tag 的检查正常完成。诊断产物保留 7 天，Full develop APK / mapping 保留 30 天。Fast 失败报告和 Full 构建报告分别保存，不再为重复 verifier 搬运报告。
- Gradle 下载缓存仍可复用；可重复性构建禁用 Kotlin 增量编译，使用独立 Gradle 进程内编译，确保实际重新编译和混淆。

Brutal suite 当前要求 11 项独立缺陷注入被真实门禁拦截。配置中的其余覆盖状态需按 `ACTIVE_INDEPENDENT` / `BLOCKED_NO_INDEPENDENT_GATE` / `MUTATOR_STALE` 解读，不能把 self-detection 或未覆盖项算作通过的运行行为验证。

## 依赖升级

Actions 使用完整 commit SHA，并明确校验 JDK 下载签名；Gradle wrapper 保留发行包 SHA-256。
每个 runner 的 setup-java 保留 `force-download: true` 和 `verify-signature: true`，确保签名验证覆盖实际下载。它们是下载参数，不再添加单独的下载校验 job。
工作流契约通过固定版本的 PyYAML 标准解析器读取实际 Action / 输入，支持合法 YAML 引号、别名、内联及多行写法，移除自写格式限制。安全断言继续检查 JDK 输入、Action 完整 SHA、无正式签名配置、SDK 对齐及完整构建的独立性。本地工具测试前安装 `python -m pip install -r tools/requirements.txt`；该依赖只用于主机工具，不进入 APK。
CI 安装的 SDK build tools 必须与 Gradle 的显式 `buildToolsVersion` 相同；当前固定 36.0.0。整数 `compileSdk = 37` 实际选择 `android-37.0`，CI 必须安装同一平台，不能只匹配大版本号。安装脚本和构建选择漂移会被契约检查拒绝。
Dependabot 每周提交 Gradle 依赖候选 PR，最多同时 3 个；编译器、libxposed ABI 和 DexKit 原生引擎只自动提出补丁候选。每个升级仍需审查 diff、官方变更和完整构建结果。
AGP 补丁需检查 debug / develop、R8 mapping、无缓存可重复性和 lint；运行库的升级还需目标 HyperOS 1 / Android 14 验证。编译与 JVM 通过不代替实机兼容性结论。
本地 `verify.py fast --changed` / `--staged` 遇到版本清单、Gradle 配置、wrapper 或编译用 JAR 变化时也运行 JVM 测试；仅文档或 CI 工具变更仍可跳过 Gradle。

构建插件的依赖图与 APK 的运行依赖图需分别审查。当前 AGP 补丁仍默认带入旧 KGP，因此根构建脚本显式使用已修复缓存反序列化问题的 KGP 2.4.20，并对 Commons、jose4j、JDOM、Bouncy Castle 构建依赖设置安全版本约束。应用继续显式使用 Kotlin stdlib / BOM 2.3.21、语言/API 2.2 和 JVM 17，禁止编译器升级隐式抬升 APK 运行库。
根构建脚本的 buildscript classpath 与 settings 中的插件/运行依赖仓库分别配置；三者必须遵循同一个 `-PuseChinaMirrors=true` 选择。默认使用官方仓库，镜像模式使用已有 Aliyun / Huawei 地址与 content 过滤；CI 继续使用官方仓库。

优先保留行为测试、兼容契约、备份 V2、preference、lifecycle ownership、hot-path 回归、正式 Dynamic Island、API 边界和 issue 回归。

删除测试的唯一理由：无 production subject、完全重复、或锁死错误实现细节。不得靠删测试制造绿构建。

## 实机

静态通过不等于目标 ROM 可用。实机证据按 `STATIC` / `BUILD` / `LOG` / `DEVICE` 分级。无证据不得改成熟热路径。
