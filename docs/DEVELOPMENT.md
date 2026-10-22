# 开发

先读 [AGENTS.md](../AGENTS.md)，核对当前分支与 exact SHA。按任务授权沿用已有试验分支，或从约定 SHA 建工作分支。不要覆盖未知修改，也不要为了 Review 建平行分支。

## 环境

- JDK 25；`JAVA_HOME` 指向 JDK 根目录，不能指向 `bin`。Gradle daemon 与 Java toolchain 都固定 25；Java / Kotlin 输出目标同步为 25，由 R8/D8 转为 SDK 34 可运行的 DEX。Android 并不提供桌面 JDK 25 的全部 API。
- Python 3.12 或更新版本；安装主机工具依赖：
  ```powershell
  python -m pip install -r tools/requirements.txt
  ```
- Android SDK：当前使用 `platforms;android-37.0`、`build-tools;36.0.0` 与 `platform-tools`。通过 Android Studio / SDK Manager 安装，`local.properties` 的 `sdk.dir` 指向本机 SDK 根目录，不提交该文件。
- 使用仓库 Gradle wrapper。AGP / APK 依赖版本见 [版本清单](../gradle/libs.versions.toml)，KGP 与主机构建依赖约束见 [根构建脚本](../build.gradle.kts)，wrapper 校验见 [wrapper 配置](../gradle/wrapper/gradle-wrapper.properties)。

compileSdk 是编译工具输入；minSdk / targetSdk 仍为 34，运行支持限于 HyperOS 1 / Android 14。Kotlin language / API 使用 2.4，语言级别、编译器版本与 JVM 输出目标分别配置。依赖版本以构建脚本和版本清单为准，不在文档另存一份版本表。

AGP 的 KGP 覆盖和主机安全依赖约束按实际解析图核对；Gradle 宿主 Kotlin 与 APK 的 BOM / stdlib 属于不同 classpath，不强行统一。Windows 建议在当前终端设置 `$env:PYTHONUTF8='1'`，统一验证脚本及其子进程的输出编码。

默认使用官方仓库。`-PuseChinaMirrors=true` 为插件、buildscript 和普通依赖选择已有镜像；libxposed 始终独占 Maven Central，并按 [verification-metadata.xml](../gradle/verification-metadata.xml) 校验所列产物。这个校验范围不覆盖所有依赖。不要为解决下载故障删校验或降级 JDK。

## 安全升级与能力试验

工程选择以 [AGENTS.md](../AGENTS.md) 为统一入口；允许按需局部重构、成熟依赖、有界缓存与受控异步，不以绝对最低内存、最少代码或最小 APK 作独立目标。性能声明、资源交换及静态预算按 [OPTIMIZATION_GATES.md](OPTIMIZATION_GATES.md) 留下可复核证据。

允许为尝试新版本、API 能力、兼容性、安全性和维护流程而升级，不以明确的性能提升或 APK 缩小为前提。
每阶段保留来源、差异、兼容回退和相应回归结果；性能仅按真实测量报告，试验通过不自动授权推广到 main 或发布。
API 102 的安装能力与 [DexSQL 取证流程](rom-intelligence/DEXSQL.md) 协作：先核对目标声明与来源，再复核最终 R8 回调与类加载边界，最后做 API 102 框架实机验证。

## 日常验证

```powershell
python tools/verify.py fast --changed
python tools/verify.py fast --tests PreferenceBootstrapTest
```

`--changed` / `--staged` 的静态检查仍执行；纯文档或工具修改可跳过 Gradle，Android 测试或构建输入变化会运行 JVM 测试。普通源码变化以编译为快速反馈；需要行为验证时显式选测试或运行 full。

收口：

```powershell
python tools/verify.py full
python -m unittest discover -s tools/tests -p "test_*.py"
git diff --check
```

`verify.py` 检查 JDK、SDK / API 边界、编码、observer、Hook 偏好与分配预算、源码契约及 feature semantics，然后编译、测试和 lint。它不构建 APK、不连接设备。门禁职责见 [TESTING.md](TESTING.md)，优化证据见 [OPTIMIZATION_GATES.md](OPTIMIZATION_GATES.md)。

## 构建与生成物

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleDevelop
```

| 变体 | 用途 |
| --- | --- |
| debug | 显式诊断；版本名带 `-debug`，不混淆、不收缩资源 |
| develop | R8 / 资源收缩与 CI 可重复性验证；默认未签名 |
| release | 正式候选；只有用户明确要求且仓库外签名有效才作为正式发行 |

输出在 `app/build/outputs/`。设置 XML 的分页面、搜索索引与偏好目录由 `generatePreferenceArtifacts` 生成到 `app/build/generated/`，不要直接改生成文件。生成 res / Kotlin 与各变体独立的 provenance assets 通过 AGP 的 [addGeneratedSourceDirectory](https://developer.android.com/reference/tools/gradle-api/9.3/com/android/build/api/variant/SourceDirectories#addGeneratedSourceDirectory(org.gradle.api.tasks.TaskProvider,kotlin.Function1)) 注册，由声明的输出建立消费任务依赖。独立 lint 不继承 assets 的生成依赖，对应 provenance 另用 [公开 PreBuild 生命周期 API](https://developer.android.com/reference/tools/gradle-api/9.3/com/android/build/api/variant/LifecycleTasks) 注册，不匹配内部任务名。正式构建使用 [RELEASE.md](RELEASE.md) 中的外部签名与 exact revision 命令；未提供正式签名的 release 带 `-unsigned`，不能冒充正式版。

生成器的源码输入只跟踪 `mods/utils/feature/` 内的 Kotlin 声明，XML 仍完整跟踪；普通 Hook、Java 或 UI 源码变更不再触发设置资源重生成。Python 回归以全源码扫描生成的目录逐字节比对限定目录的结果；新增偏好键声明应放在该 feature 目录，移到其它位置必须同步生成依赖。增删 feature 文件及 XML 变更仍须触发生成。

ROM 样本、日志、mapping、profiler 数据、APK 和签名材料留在忽略目录或仓库外。整合分支逐项审查冲突，完整通过收口门禁后再按授权清理旧分支；普通开发授权不包含 push / merge main / tag / release。
