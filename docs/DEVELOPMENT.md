# 开发与构建

工程入口与授权边界见 [AGENTS.md](../AGENTS.md)；本页维护协作、环境、构建及生成输入，验证选择只见 [TESTING](TESTING.md#执行分级)。

## Git 与协作

一次取得 `git status --short --branch`、HEAD、`git worktree list` 及相关 Issue / PR / 真实远端 SHA，复用本轮快照。只读解释和局部检查不反复查询 GitHub；共享认领前、push / merge / 清理前，或已知 main / 分支 / 所有者变化后刷新相关范围。远端不可用要报告未知状态，不能推定无人工作。

独立任务原则上对应一个 Issue 和工作分支；新聊天优先继续现有任务。写入前核对归属，在 Issue 中记录分支、HEAD、负责文件 / 共享状态和下一步；未发生所有权变化时无需每个文件重复认领。认领不是原子锁，竞争时只停止冲突范围并协调。同一 Worktree 只有一个写入者，只读审查可共享，不另建 Agent 任务数据库。

交接只保留 `Issue / Branch / HEAD SHA / Changes / Verification / Remaining`，标明已推送提交。push 完成要读回真实远端 SHA；合并必须在当前授权内。分支清理前确认成果等价纳入、HEAD 未变化、无开放 PR / 活动 Worktree / 协作依赖并有可恢复来源；先远端保存成果，再按授权逐支删除。

## 环境

- JDK 25，JAVA_HOME 指向 JDK 根目录；Gradle daemon、Java toolchain 和 Java / Kotlin 输出均为 25。Android 14 不提供全部桌面 JDK API，需分别验证语言、DEX 和运行期能力。
- Python 3.12+：`python -m pip install -r tools/requirements.txt`。Windows 可设置 `$env:PYTHONUTF8='1'` 统一子进程编码。
- Android SDK 安装项以 [ci_install_android_sdk.sh](../tools/ci_install_android_sdk.sh) 为准；本机 `local.properties` 的 `sdk.dir` 指向 SDK 根目录，不入库。compileSdk 不改变 minSdk / targetSdk 34 的运行边界。
- 使用仓库 wrapper；版本以 [libs.versions.toml](../gradle/libs.versions.toml)、[根构建](../build.gradle.kts)、[app 构建](../app/build.gradle.kts) 和 [wrapper 配置](../gradle/wrapper/gradle-wrapper.properties) 为准。

默认使用官方仓库。`-PuseChinaMirrors=true` 选择已有镜像；libxposed 始终独占 Maven Central。保留 [verification-metadata.xml](../gradle/verification-metadata.xml) 中的产物校验，其范围不覆盖所有依赖。Gradle 宿主 Kotlin 与 APK 的 BOM / stdlib 属于不同 classpath，按实际解析图审查，不强行统一。

## 日常验证

[TESTING](TESTING.md#执行分级)统一维护 Quick / Standard / Deep / Release 的命令和复用条件；其余章节解释各层证明范围。

升级可为安全、能力、兼容或维护；保留官方来源、差异、回退和相应回归，不自动授权合并或发布。性能证据见 [OPTIMIZATION_GATES](OPTIMIZATION_GATES.md)。

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

页面导航文档按需运行 `python tools/generate_feature_map.py`，过期检查用 `--check`；`python tools/check_docs.py` 检查仓库 Markdown 的本地链接、锚点和图片。两者独立于 Gradle，不改变分页 / 搜索 / 偏好目录生成物。职责与更新标准见 [DOCS_STYLE](DOCS_STYLE.md)。
