# 开发

任务认领、Git、安全边界和收口命令以 [AGENTS.md](../AGENTS.md) 为准；本页只维护环境、构建及生成输入。

## 环境

- JDK 25，JAVA_HOME 指向 JDK 根目录；Gradle daemon、Java toolchain 和 Java / Kotlin 输出均为 25。Android 14 不提供全部桌面 JDK API，需分别验证语言、DEX 和运行期能力。
- Python 3.12+：`python -m pip install -r tools/requirements.txt`。Windows 可设置 `$env:PYTHONUTF8='1'` 统一子进程编码。
- Android SDK 安装项以 [ci_install_android_sdk.sh](../tools/ci_install_android_sdk.sh) 为准；本机 `local.properties` 的 `sdk.dir` 指向 SDK 根目录，不入库。compileSdk 不改变 minSdk / targetSdk 34 的运行边界。
- 使用仓库 wrapper；版本以 [libs.versions.toml](../gradle/libs.versions.toml)、[根构建](../build.gradle.kts)、[app 构建](../app/build.gradle.kts) 和 [wrapper 配置](../gradle/wrapper/gradle-wrapper.properties) 为准。

默认使用官方仓库。`-PuseChinaMirrors=true` 选择已有镜像；libxposed 始终独占 Maven Central。保留 [verification-metadata.xml](../gradle/verification-metadata.xml) 中的产物校验，其范围不覆盖所有依赖。Gradle 宿主 Kotlin 与 APK 的 BOM / stdlib 属于不同 classpath，按实际解析图审查，不强行统一。

## 日常验证

[根规则](../AGENTS.md#工具链与验证)列出 Fast / Full、工具测试及 diff 检查命令；[TESTING](TESTING.md)解释各层证明范围。

`fast --changed` / `--staged` 仍运行静态门禁：文档 / 工具改动可跳过 Gradle，Android 测试或构建输入变化会运行 JVM 测试，普通生产源码变化先编译。需要行为回归时显式选类，例如 `python tools/verify.py fast --tests PreferenceBootstrapTest`。已提交改动使用不带 changed 的模式。

`verify.py` 不构建 APK、不连接设备，也不执行 R8 或 lintVital；需要时另跑下面的构建。升级可为安全、能力、兼容或维护；保留官方来源、差异、回退和相应回归，不自动授权合并或发布。性能证据见 [OPTIMIZATION_GATES](OPTIMIZATION_GATES.md)。

## 构建与生成物

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleDevelop :app:lintVitalDevelop
```

| 变体 | 用途 |
| --- | --- |
| debug | 诊断；带 `-debug`，不混淆、不收缩资源 |
| develop | R8 / 资源收缩与 CI 检查，默认未签名 |
| release | 正式候选，授权及外部签名按 [RELEASE](RELEASE.md)；无正式签名时带 `-unsigned` |

输出在 `app/build/outputs/`。设置源为 `res/xml/prefs_*.xml`；`generatePreferenceArtifacts` 生成分页、搜索索引和偏好目录到 `app/build/generated/`，不手改或提交生成物。

生成器完整跟踪 XML，Kotlin 声明输入限定 `mods/utils/feature/`。普通 Hook / Java / UI 改动不应重生成设置；新增偏好声明移出 feature 目录时同步生成依赖。回归须将限定输入结果与全源码扫描逐字节比较。

生成 res / Kotlin 和各变体 provenance assets 用 AGP `addGeneratedSourceDirectory` 声明消费依赖；独立 lint 的 provenance 依赖用公开 PreBuild 生命周期 API 注册，不匹配内部任务名。

Feature / routing 变化后运行 `python tools/extract_process_matrix.py`，检查 JSON / CSV diff 及 [确定性门禁](TESTING.md#独立故障验证)。ROM / APK / mapping / 日志与签名材料按根规则留在忽略目录或仓库外。
