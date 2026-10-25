# 开发与构建

工程入口与授权边界见 [AGENTS.md](../AGENTS.md)；本页维护协作、环境、构建及生成输入，验证选择只见 [TESTING](TESTING.md#执行分级)。

## Git 与协作

首次初始化才核对任务归属和相关聊天，一次取得 `git status --short --branch`、HEAD、`git worktree list` 及当前任务相关 Issue / PR / 真实远端 SHA；只查询需要确认的范围，不扫描全部历史任务。新聊天优先接续已有任务。

同一聊天的后续消息默认续接，继承已确认的目标、授权范围、base/head、分支、Worktree、归属和验证证据。追加要求、纠正、状态询问及上下文压缩后的恢复不重新列聊天、搜索历史任务、扫描全部 Issues 或读取未变化的文档。只有明确创建 / 切换独立任务、发现实际写入冲突，或关键上下文无法可靠恢复时才重新发现，并先限定到失效范围。正常 commit、main 前进或分支状态变化先用定向 Git 检查更新有关事实，不自动判定整个上下文失效。

共享写入前只核对受影响的归属和工作区；push / merge main 只刷新目标与来源 SHA、必要 diff、祖先关系、工作区及授权，完成后读回真实远端 SHA。发布另外核对 [RELEASE](RELEASE.md) 要求的实时来源、候选、签名与门禁；破坏性操作保留受影响引用、活动 Worktree、协作依赖与可恢复来源检查。这些是操作前的增量核验，不是重跑任务发现。远端不可用要报告相关状态未知，不能推定无人工作或已完成远端操作。

独立任务原则上对应一个 Issue 和工作分支。首次写入或归属 / 范围变化时，在相关 Issue 更新分支、HEAD、负责文件 / 共享状态和下一步；后续直接复用，不为每个文件或每条消息重复认领。认领不是原子锁，竞争时只停止冲突范围并协调。同一 Worktree 只有一个写入者，只读审查可共享；已授权操作直接执行，仅在确有未授权风险或无法安全判断时询问，不另建 Agent 状态管理机制。

交接只保留 `Issue / Branch / HEAD SHA / Changes / Verification / Remaining`，标明已推送提交。push 完成要读回真实远端 SHA；合并必须在当前授权内。分支清理前确认成果等价纳入、HEAD 未变化、无开放 PR / 活动 Worktree / 协作依赖并有可恢复来源；先远端保存成果，再按授权逐支删除。

### 提交、推送与确认

已授权实施的开发任务通过必要验证并审阅完整 diff 后，默认 commit / push 到当前任务对应的工作分支，不再询问是否保存到远端；用户明确要求只读、暂不提交 / 推送或停止时优先遵守。长任务可按已验证且可独立回退的阶段成果提交、推送，不为每次改文件创建碎片提交。默认授权只覆盖任务工作分支；若当前在 main，先建立或切换到所属任务分支，不能据此直推 main、创建 Tag 或发布 Release。

推送前增量核对工作树、完整任务 diff、HEAD、分支归属、upstream / 目标引用及真实远端变化，不夹带未知修改。只 fetch 受影响的分支；远端已前进时先确认祖先关系和差异，按当前授权安全整合并补验受影响部分，不能 force push、覆盖其他 Agent 或跳过必要回归。首次推送显式指定任务分支并设置 upstream，不依赖可能指向其它分支的默认目标。

推送后使用 `git ls-remote --heads origin refs/heads/<任务分支>` 读回远端完整 SHA，与本地 HEAD 比较；只看 push 退出成功、本地 `origin/*` 或提交记录不足以确认送达。若命令超时、连接中断或返回不确定，先读回远端，确认是否已保存，再决定是否重试。收口默认要求有效成果已推送且工作树干净，并报告 HEAD、真实远端 SHA 及同步状态；未完成的推送必须明确标为未保存到远端，不把本地提交误报为成功，也不为凑干净状态丢弃未知改动。

### 本地 Git 网络与失败恢复

本机访问 GitHub 优先检测 `127.0.0.1:4573`，先确认端口可达及代理协议；端口开放本身不能证明代理可用。HTTP 以 CONNECT 代理响应确认，SOCKS 以相应握手确认，不假定端口类型；两种均支持时优先 HTTP。HTTP 代理使用 `http://127.0.0.1:4573`，确认 SOCKS5 后使用 `socks5h://127.0.0.1:4573`，其它 SOCKS 版本按实际协议配置。

确认可用后优先设置当前仓库、仅 GitHub URL 生效的本地配置，例如 HTTP 模式的 `git config --local http.https://github.com/.proxy http://127.0.0.1:4573`；SOCKS 模式只替换为已确认的协议。此配置属于本仓库的 Git 元数据（同仓库 Worktree 可共享），不写 global 配置或环境变量，不修改远端 URL、Git 身份、凭据或 TLS 校验，不加入跟踪的配置文件，也不影响 GitHub Actions 云端环境。保留原有配置以便回退；没有网络失败证据时不反复检测或改写已经确认有效的设置。

本机代理不可用时，单次命令恢复原有网络路径再尝试；若原先无代理，可用 `git -c http.https://github.com/.proxy= <原 Git 命令参数>` 临时绕过失效的本机代理，不影响其它项目。连接和传输使用短超时 / 低速退出并限制总等待；同一路径失败后先区分代理、网络、权限和远端拒绝，仅在条件改变或明确瞬时错误时重试一次，不能长时间原样重连。认证或权限拒绝不靠重试解决，也不改凭据或放宽安全限制；保留本地提交并报告实际阻塞。

GitHub CLI / API 仅作为已有 Git 网络故障证据下的备用路径。先核对远端引用和提交是否已经存在，复用已上传对象，只补缺失对象；重试不能盲目重新上传或重建提交。确需通过 Git data API 保存时，必须保持原 tree / commit 完整 SHA，核对父提交和目标引用，仅作非强制更新，最后独立读回；无法保持来源或安全确认时停止远端写入并明确未完成。不另建推送系统或持久化状态数据库。

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
