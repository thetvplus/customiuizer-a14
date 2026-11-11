# 开发与构建

工程入口与授权边界见 [AGENTS.md](../AGENTS.md)；本页维护协作、环境、构建及生成输入，验证选择只见 [TESTING](TESTING.md#执行分级)。

## Git 与协作

`master` 的仓库 Ruleset 要求 Squash PR、线性历史、解决对话及 GitHub Actions 的 `Fast CI`。禁止强推和删除，审批人数为零。签名提交、部署检查和锁分支保持关闭。迁移时先保留经典保护，确认新规则生效再撤去重叠配置。正式签名与发布仍须满足 [RELEASE](RELEASE.md) 的 exact master CI、来源和环境门禁。

日常开发、修复与实验在任务分支进行，保留有意义的阶段提交。单个完整任务可直接通过 Squash PR 整合。多个任务确需一起验收时，使用短期 `release/<version>` 分支，逐项核对纳入范围、冲突、其他协作者的成果和验证证据，再由该分支发起 Squash PR。`master` 合并、Tag 和正式发布各自需要明确授权。

默认一个正式版本对应一个整合提交，标题采用 `release: r14.27.5 — <核心变化>`。细节和证据写入 PR 与双语 Changelog。独立安全、CI、文档维护或必要修复可使用 `security:`、`ci:`、`docs:`、`fix:`，在 PR 说明独立整合的原因。小改动无需单独升版。Squash 后以最终提交及 PR 追溯来源，通过树、有效差异与验证核对成果等价性，不能以开发提交是否为祖先判断是否遗漏。合并后结束已完成的任务分支，仍按下述安全条件清理。未发布整合发现问题时可暂停发布或经 PR 修正，公开 Tag 保留原 SHA。

首次初始化或明确切换独立任务、实际写入冲突、关键上下文失效时，定向核对任务归属、相关 Issue / PR、工作树、HEAD、Worktree 和目标远端；不扫描全部历史任务。新聊天优先接续已有任务。同一聊天的追加要求、纠正、状态询问及压缩恢复沿用分支、授权和可信证据，只刷新失效部分。

独立任务原则上一个 Issue / 工作分支。首次写入或归属变化时更新分支、HEAD、负责范围和下一步，之后复用；认领不是原子锁，冲突只停止受影响范围。同一 Worktree 只有一个写入者，只读审查可共享，不另建 Agent 状态系统。

共享写入只核对相关归属与状态；push / merge 增量核对目标、来源、祖先关系、diff、工作区和授权，发布另按 [RELEASE](RELEASE.md)。远端不可用则标明未知，不推定无人协作。交接保留 Issue / Branch / HEAD / Changes / Verification / Remaining 及推送状态。

分支清理须有当前授权、成果等价纳入和可恢复来源，确认 HEAD 未变化且无开放 PR、活动 Worktree 或协作依赖；先保存远端成果，再逐支处理。

### 默认分支切换

从 `main` 切换到 `master` 时，先确认没有同名目标引用，盘点仍打开的 PR、运行中的签名 / 发布，以及需要保留原 ref 的候选。当前未获发布确认的版本留在任务分支。切换按以下顺序进行：

1. 暂停 A14 Signed Build 和 A14 Promote Release，并核查没有进行中的签名 / 发布。先单独 Squash 合入只允许 PR CI 同时接受 `main` / `master` 的过渡修改。此时 push CI 与可信发布来源仍是 `main`。
2. 保留经典保护，在 GitHub 重命名默认分支，核对 `master` 的 SHA 与切换前 `main` 相同、保护生效、原 PR 基线正确。局部克隆更新原有稳定分支的 upstream 和 `origin/HEAD`，工作分支不改名。
3. 向 `master` 提交切换 PR，使 PR / push CI、手动依赖图、发布工作流和源码策略改用 `master`。过渡配置能为该 PR 运行 `Fast CI`。两套发布 Environment 的唯一分支策略由 `main` 改为 `master`，下载仓库配置不变。
4. 核对 GitHub Actions 提供的 `Fast CI` 及规则目标后，启用 Ruleset。验证普通 PR、对话解决和 Squash 路径，再撤下重复的经典保护。验证期间可恢复经典规则或退回过渡提交。正式发布工作流在版本验收和明确授权后启用，并以当时 `master` 的完整 SHA 重新验证。

### 提交、推送与确认

已授权实施的开发任务通过必要验证并审阅完整 diff 后，默认 commit / push 到当前任务对应的工作分支，不再询问是否保存到远端；用户明确要求只读、暂不提交 / 推送或停止时优先遵守。长任务可按已验证且可独立回退的阶段成果提交、推送，不为每次改文件创建碎片提交。默认授权只覆盖任务工作分支；若当前在 master，先建立或切换到所属任务分支，不能据此直推 master、创建 Tag 或发布 Release。

推送前增量核对工作树、完整任务 diff、HEAD、分支归属、upstream / 目标引用及真实远端变化，不夹带未知修改。只 fetch 受影响的分支；远端已前进时先确认祖先关系和差异，按当前授权安全整合并补验受影响部分，不能 force push、覆盖其他 Agent 或跳过必要回归。首次推送显式指定任务分支并设置 upstream，不依赖可能指向其它分支的默认目标。

推送后使用 `git ls-remote --heads origin refs/heads/<任务分支>` 读回远端完整 SHA，与本地 HEAD 比较；只看 push 退出成功、本地 `origin/*` 或提交记录不足以确认送达。若命令超时、连接中断或返回不确定，先读回远端，确认是否已保存，再决定是否重试。收口默认要求有效成果已推送且工作树干净，并报告 HEAD、真实远端 SHA 及同步状态；未完成的推送必须明确标为未保存到远端，不把本地提交误报为成功，也不为凑干净状态丢弃未知改动。

### 本地 Git 网络与失败恢复

本机访问 GitHub 优先检测 `127.0.0.1:4573`，先确认端口可达及代理协议；端口开放本身不能证明代理可用。HTTP 以 CONNECT 代理响应确认，SOCKS 以相应握手确认，不假定端口类型；两种均支持时优先 HTTP。HTTP 代理使用 `http://127.0.0.1:4573`，确认 SOCKS5 后使用 `socks5h://127.0.0.1:4573`，其它 SOCKS 版本按实际协议配置。

确认可用后优先设置当前仓库、仅 GitHub URL 生效的本地配置，例如 HTTP 模式的 `git config --local http.https://github.com/.proxy http://127.0.0.1:4573`；SOCKS 模式只替换为已确认的协议。此配置属于本仓库的 Git 元数据（同仓库 Worktree 可共享），不写 global 配置或环境变量，不修改远端 URL、Git 身份、凭据或 TLS 校验，不加入跟踪的配置文件，也不影响 GitHub Actions 云端环境。保留原有配置以便回退；没有网络失败证据时不反复检测或改写已经确认有效的设置。

本机代理不可用时，单次命令恢复原有网络路径再尝试；若原先无代理，可用 `git -c http.https://github.com/.proxy= <原 Git 命令参数>` 临时绕过失效的本机代理，不影响其它项目。连接和传输使用短超时 / 低速退出并限制总等待；同一路径失败后先区分代理、网络、权限和远端拒绝，仅在条件改变或明确瞬时错误时重试一次，不能长时间原样重连。认证或权限拒绝不靠重试解决，也不改凭据或放宽安全限制；保留本地提交并报告实际阻塞。

GitHub CLI / API 仅作为已有 Git 网络故障证据下的备用路径。先核对远端引用和提交是否已经存在，复用已上传对象，只补缺失对象；重试不能盲目重新上传或重建提交。确需通过 Git data API 保存时，必须保持原 tree / commit 完整 SHA，核对父提交和目标引用，仅作非强制更新，最后独立读回；无法保持来源或安全确认时停止远端写入并明确未完成。不另建推送系统或持久化状态数据库。

## 环境

- JDK 27，JAVA_HOME 指向 JDK 根目录；Gradle daemon 与 Java toolchain 使用 27；稳定版 Kotlin 2.4.21 的 Java / Kotlin 字节码目标统一为 26。D8 / R8、Host 测试和 A14 设备分别验证，不因构建 JDK 升级而扩展运行期 API。Android 14 不提供全部桌面 JDK API，需分别验证语言、DEX 和运行期能力。
- Python 3.12+：`python -m pip install -r tools/requirements.txt`。Windows 可设置 `$env:PYTHONUTF8='1'` 统一子进程编码。
- Android SDK 安装项以 [ci_install_android_sdk.sh](../tools/ci_install_android_sdk.sh) 为准；本机 `local.properties` 的 `sdk.dir` 指向 SDK 根目录，不入库。compileSdk 不改变 minSdk / targetSdk 34 的运行边界。
- 使用仓库 wrapper；版本以 [libs.versions.toml](../gradle/libs.versions.toml)、[根构建](../build.gradle.kts)、[app 构建](../app/build.gradle.kts) 和 [wrapper 配置](../gradle/wrapper/gradle-wrapper.properties) 为准。
- PowerShell 按目录与 `rg -g '*.kt'` 等过滤器检索，不向工具传入未展开的路径通配符。经 Python / 受管执行器启动 Gradle 时使用绝对 `gradlew.bat` 路径（`verify.py` 已处理），避免把 POSIX 的 `./gradlew` 传入 Windows 子进程。

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

输出在 `app/build/outputs/`。设置唯一来源为 `app/settings/prefs_*.json`；`generatePreferenceArtifacts` 生成按需 Compose 模型工厂、非 UI 搜索索引和偏好目录到 `app/build/generated/`，不手改或提交生成物。

保持单一 app 模块，只有独立构建 / 分发合同才新增模块。生产辅助 API 按 A14 实际消费者维护；开发采样不嵌入生产源码，测试与替身只放 test 源码集。三个变体的 provenance 由同一注册函数维护，仍各有独立目录、任务及消费依赖，release 独有完整 sourceCommit。构建配置变化后用实际合并 assets 验证隔离，不能仅看脚本文本。

生成器完整跟踪 settings JSON、schema / Kotlin 生成器脚本，Kotlin 声明输入限定 `mods/utils/feature/`。普通 Hook / Java / UI 改动不应重生成设置；新增偏好声明移出 feature 目录时同步生成依赖。回归须将限定输入结果与全源码扫描逐字节比较。

生成 res / Kotlin 和各变体 provenance assets 用 AGP `addGeneratedSourceDirectory` 声明消费依赖；独立 lint 的 provenance 依赖用公开 PreBuild 生命周期 API 注册，不匹配内部任务名。

Feature / routing 变化后运行 `python tools/extract_process_matrix.py`，检查 JSON / CSV diff 及 [确定性门禁](TESTING.md#独立故障验证)。ROM / APK / mapping / 日志与签名材料按根规则留在忽略目录或仓库外。

页面导航文档按需运行 `python tools/generate_feature_map.py`，过期检查用 `--check`；`python tools/check_docs.py` 检查仓库 Markdown 的本地链接、锚点和图片。两者独立于 Gradle，不改变分页 / 搜索 / 偏好目录生成物。职责与更新标准见 [DOCS_STYLE](DOCS_STYLE.md)。
