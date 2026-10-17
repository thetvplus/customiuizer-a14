# 开发

先读 [AGENTS.md](../AGENTS.md)，从 `main` 的 exact SHA 建工作分支。不要覆盖未知修改，也不要为了 Review 建平行分支。

## 环境

- JDK 25；`JAVA_HOME` 指向 JDK 根目录，不能指向 `bin`。Gradle daemon 与 Java toolchain 都固定 25；Android Java / Kotlin 字节码目标保持 17。
- Python 3.12 或更新版本；安装主机工具依赖：
  ```powershell
  python -m pip install -r tools/requirements.txt
  ```
- Android SDK：当前使用 `platforms;android-37.0`、`build-tools;36.0.0` 与 `platform-tools`。通过 Android Studio / SDK Manager 安装，`local.properties` 的 `sdk.dir` 指向本机 SDK 根目录，不提交该文件。
- 使用仓库 Gradle wrapper。AGP / APK 依赖版本见 [版本清单](../gradle/libs.versions.toml)，KGP 与主机构建依赖约束见 [根构建脚本](../build.gradle.kts)，wrapper 校验见 [wrapper 配置](../gradle/wrapper/gradle-wrapper.properties)。

compileSdk 是编译工具输入；minSdk / targetSdk 仍为 34，运行支持限于 HyperOS 1 / Android 14。主机 KGP、Kotlin language / API 2.2 和 APK Kotlin BOM / stdlib 是不同配置，不能只升级编译器就隐式抬升运行依赖。

默认使用官方仓库。`-PuseChinaMirrors=true` 为插件、buildscript 和普通依赖选择已有镜像；libxposed 始终独占 Maven Central，并按 [verification-metadata.xml](../gradle/verification-metadata.xml) 校验所列产物。这个校验范围不覆盖所有依赖。不要为解决下载故障删校验或降级 JDK。

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

输出在 `app/build/outputs/`。设置 XML 的分页面、搜索索引与偏好目录由 `generatePreferenceArtifacts` 生成到 `app/build/generated/`，不要直接改生成文件。正式构建使用 [RELEASE.md](RELEASE.md) 中的外部签名与 exact revision 命令；未提供正式签名的 release 带 `-unsigned`，不能冒充正式版。

ROM 样本、日志、mapping、profiler 数据、APK 和签名材料留在忽略目录或仓库外。整合分支逐项审查冲突，完整通过收口门禁后再按授权清理旧分支；普通开发授权不包含 push / merge main / tag / release。
