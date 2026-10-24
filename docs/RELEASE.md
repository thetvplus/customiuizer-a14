# 发布与恢复

正式构建、push / merge main、tag 与发布以当前任务授权为准。debug / develop 的验证结果不等于正式发行。

## 版本与记录

- `versionName` 形如 `r14.22.7`，`versionCode` 单调增加。
- Gradle、双语 README、双语 CHANGELOG 与发行说明同步。
- 双仓库 About、主题、主页链接及下载仓库 `SUMMARY` 按当前兼容范围维护；现行文档不得继续声称支持已移除的 API 或平台，历史更新记录保留当时事实。
- 个人源码仓库 tag：`<versionName>`；下载仓库 tag：`<versionCode>-<versionName>`。
- 新发行的两仓库 tag 都绑定构建 APK 的 exact source SHA；下载仓库的 metadata main 独立维护，不要求与源码 main 同 SHA。历史发行保留原始映射，不能因整理 main 而移动标签。
- 记录只写前一保留发行版到本版的最终变化，合并同系列内容时去重，排除调试交接与反复调整。发行说明写变化和 APK SHA-256，支持入口留在 README。

## 门禁与正式签名

先在最终候选提交运行 [完整门禁](DEVELOPMENT.md#日常验证)。普通 A14 CI 使用 Ubuntu、JDK 25 与未签名 develop，永不使用正式密钥。独立正式构建与晋升流程如下；本地构建入口继续保留。

签名 properties 和 keystore 在仓库外。通过 `-PcustomiuizerA14KeystoreProperties` 显式传入 properties 绝对路径，或使用 `CUSTOMIUIZER_A14_KEYSTORE_PROPERTIES` 环境变量；不要提交密钥、密码或本地路径配置。properties 包含 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`；路径建议用正斜杠，避免 Java Properties 反斜杠转义。相对路径以 Gradle 的 `app/` 项目目录解析。

Windows 预检：

```powershell
.\scripts\check-signing-config.ps1 -RequireSigning -KeystoreProperties "<external-absolute-path>/keystore.properties"
```

它只检查字段与文件存在，不验证密码或签名证书。普通开发无配置可跳过签名，`-RequireSigning` 无配置或无效配置必须失败。

本地手动发行也先核验工作分支、合并 main，再核验实际 main 提交。在工作区干净、该 main source SHA 已确认后构建，设备验收后使用同一 APK 发布：

```powershell
$releaseRevision = git rev-parse --short=8 HEAD
.\gradlew.bat clean :app:assembleRelease :app:lintVitalRelease -PofficialRelease=true -PrequireBuildRevision=true "-PbuildRevision=$releaseRevision" "-PcustomiuizerA14KeystoreProperties=<external-absolute-path>/keystore.properties"
```

## APK 与双仓库验收

1. 检查 applicationId、SDK / ABI、versionName / versionCode、`debuggable=false` 与 build provenance 的 revision / buildType。
2. 检查 zipalign、V2 签名、证书指纹、文件大小与 SHA-256；签名证书必须与上一正式版一致。
3. 设备连接稳定时以 `adb install -r` 做 smoke，保留用户数据；明确记录验证 APK、ROM、已测功能与未测范围。
4. 同一个已验收 APK binary 发布到 [源码仓库](https://github.com/thetvplus/customiuizer-a14/releases) 和 [下载仓库](https://github.com/Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14/releases)。
5. 一次读回两端 tag、notes、`draft=false`、`prerelease=false`、latest 状态与唯一预期 APK asset，将大小和可信 digest 与发布前已验收的本地 APK 比对。一致时无需再次下载；元数据缺失、异常或用户要求时再下载核验，不能只以“上传成功”收口。
6. 只有本版两端发布和资产验收完成后，才按明确授权移除被替代的 release / tag；保留其他发行版，重新核对列表、latest 与 changelog。发布清理不授权改写 Git 历史。

`tools/check_release_metadata.py --require-tag` 校验源码 tag 与版本元数据；`tools/verify_apk_provenance.py --help` 提供 APK provenance 检查参数。构建通过、ROM 样本与主机 benchmark 都不能替代当前正式 APK 的实机结论。

## Actions 构建与自动发布

默认流程由 GitHub Actions 自行完成，不需要 Agent 常驻监控：

1. 在工作分支提交，PR 到 main 自动运行无密钥的 A14 CI；未开 PR 时可手动选择该分支运行 A14 CI。合并前检查最终 head 的结果与 diff，不能复用旧提交的绿灯。
2. 核验通过后合并 main。main 的代码推送再次运行 A14 CI，检查实际合并提交和配置；直接提交 main 也走同一入口。纯 Markdown 推送沿用现有跳过策略，不产生新 APK。
3. main CI 成功后自动唤起 [A14 Signed Build](../.github/workflows/a14-signed-build.yml)。自动或手动入口发现版本已发布即正常跳过，不初始化 JDK / SDK，也不重新签名；只有版本递增、元数据与双语发行条目齐备时才请求发布确认。失败 / PR / fork / 非 main / 过期 SHA 的 CI 不进入正式签名。
4. 维护者在 **release-signing → Review deployments → Approve** 确认该版本、完整 SHA 和双语说明。拒绝或取消即可保持未发布；这是签名阶段必需的确认。发布环境若配置了设备验收检查点，则在同一正式候选完成验收后另行确认公开；Agent 只有获本轮用户明确委托时才能代为调用审核接口。
5. 确认后重查 main 和 CI，使用历史证书只构建一次正式 Release，核验 APK、R8、fatal lint 与 GitHub 来源证明。复用同 SHA 的 main CI，不再重复 JVM、lint 或 develop 构建。
6. 默认先发布源码仓库，核对公开资产、标签和 Latest，再发布 Xposed 仓库。两端保持完全相同的 APK、摘要和构建元数据；校验文件、Sigstore bundle 和验证报告留在内部 Actions Artifact，不重建、不重签。任一检查失败即停止后续写入。

工作流不自动合并 PR、不变更分支保护，也不因每次合并都发布。分支保护建议只要求现有 **Fast CI**，保留一个明确合并动作，避免新增一套自动合并机器人。`workflow_run` 仅监听 `A14 CI` 的 main 完成事件，并复核 API 中的 workflow ID、原始 attempt、仓库、事件、SHA 与当前 main；不下载 PR 产物执行代码，也不接收任意 ref 输入。

### 手动入口

也可在 main CI 通过后，手动运行 A14 Signed Build。手动选择表达操作意图；实际发布仍须配置并经过 release-signing 的 Required reviewer，不能以手动入口绕过。缺少审核保护时在签名前失败。预发行和仅候选通过此入口选择。

| 选项 | 默认值 | 结果 |
| --- | --- | --- |
| `destination` | `both` | 源码仓库验收成功后自动同步 Xposed 仓库 |
| `destination=source` | 手动选择 | 仅发布源码仓库 |
| `destination=actions-only` | 手动选择 | 只生成 Actions 签名候选，不创建 tag / Release |
| `prerelease` | `false` | 正式版设为 Latest；开启后两端均为预发行，不抢占 Latest |

```sh
# 发布双仓库，先要求当前 main 的 A14 CI 成功。
gh workflow run a14-signed-build.yml --ref main
# 只发布源码仓库 / 预发行。
gh workflow run a14-signed-build.yml --ref main -f destination=source
gh workflow run a14-signed-build.yml --ref main -f prerelease=true
# 试用明确选择 actions-only；正式签名只信任 main。
gh workflow run a14-signed-build.yml --ref main -f destination=actions-only
```

仅候选同样要求 release-signing 的审核配置及审核；没有可复用 CI 时补齐源码、工具、JVM、lint、develop 与 R8 门禁。未授权分支只做无密钥核验。新发布一律要求同 SHA 的 main CI 成功；分支候选晋升时，该 SHA 必须已通过 main CI。普通 CI 不接触正式密钥，develop 产物保留 unsigned / develop 标识。

### 一次性配置

在源码仓库配置两个 Environment，均选择 **Selected branches and tags**，只添加 `tools/release-policy.json` 中的 `main` branch，不添加临时分支、通配符或 tag。

| Environment | 配置 |
| --- | --- |
| `release-signing` | 至少一个 Required reviewer；四个签名 Secrets：`A14_KEYSTORE_BASE64`、`A14_STORE_PASSWORD`、`A14_KEY_ALIAS`、`A14_KEY_PASSWORD` |
| `release-publishing` | `A14_XPOSED_RELEASE_TOKEN`；需要正式 APK 本地验收时设置 Required reviewer，在同一运行的签名候选通过设备 smoke 后批准公开；不需本地验收时可沿用前一阶段的确认 |

自动路径会先检查 release-signing 确实有审核人及显式分支限制，配置缺失则停止，不把空 Environment 当作确认。单维护者仓库可允许本人审批自己触发的运行。工作流不代改管理员保护设置；远端 Environment / Secrets 配置及凭据必须实际核对，不能由文档推断已有效。

签名 Secret 必须是现有 PKCS#12 `.p12`。证书固定为正式证书 SHA-256 `c0eff2dc4e662717195490da78b12a984c6f2e6bd38acf4edad14d53e3d22e70`，不生成替代密钥。临时签名材料只在受保护构建步骤恢复，先核对证书，结束时清理；证明生成与发布前已移除密钥。

跨仓库凭据仅放在 release-publishing，使用现有、限定到 `Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14` 的凭据。细粒度 PAT / GitHub App 需要 **Contents: write 和 Workflows: write**：新 tag 携带源码提交，其 `.github/workflows/` 与下载仓库 metadata main 不同；经典 PAT 对应 `repo` / `public_repo` 和 `workflow`。组织要求审批时先完成审批，并留意凭据到期。参见 [GitHub Release 权限说明](https://docs.github.com/en/rest/releases/releases#create-a-release)。源码发布使用本仓库 GITHUB_TOKEN 的 contents:write；不扩大普通 CI 权限。

发布前检查跨仓库凭据存在、仓库写权限、两端既有版本 / 草稿 / tag / 资产冲突；权限不足不先发布主仓库。账号 push 权限检查不能证明细粒度 token 的全部权限或未来网络可用性，实际服务器拒绝会停止并保留可恢复状态。Xposed 仅增加新源码 tag 和 Release，metadata main 独立维护。

### 更新记录、说明与命名

日常只在 `CHANGELOG.md` 的“未发布”和 `CHANGELOG_EN.md` 的“Unreleased”写已确认的用户变化。准备发版时执行一次：

```sh
python tools/prepare_release.py --version r14.26.1 --code 249 --date 2026-10-23
# 仅预览涉及文件，可追加 --dry-run。
python tools/check_release_metadata.py
```

上例是下一版本的示范，不代表已决定或发布该版本。工具先验证全部输入，再同步 Gradle、双语 README 与两个 Changelog，将已审核条目移入带日期的新版本节，保留历史并清空未发布节。空翻译、版本重复、版本倒退均拒绝；工具不提交、不推送、不打 tag，也不凭提交消息编造更新内容。README 标明源码版本；是否已经正式发行以 Release 为准，等待确认时不会把新源码误称为已发布正式版。将这五个文件与代码一并审查、提交到分支，然后走上述合并和发布流程。

累计替代当前发行时，先把仍有效的旧版变化与本轮成果去重写入双语 Unreleased，分别组织用户与工程内容，再显式运行：

```sh
python tools/prepare_release.py --version r14.25.3 --code 248 --date 2026-10-23 --supersede r14.25.2 --base-version r14.25.1
```

两个参数必须一起给出；只能替代当前版本章节，基线必须是紧邻的上一保留发行版，两个语言必须具备结构化的累计内容。默认模式仍保留所有历史。该操作只准备源码，不删除 GitHub Release / tag；旧版工程证据仍在 Git 历史与既有路线图中。发布后的清理必须先重新确认两仓库资产一致且旧 Release 非 immutable，失败保留旧版。

新版本的双语 Changelog 依次使用“用户变化 / User changes”和“工程说明 / Engineering notes”两节：用户节包含简短概述和 4～6 项变化，工程节保留方法、限制与技术记录。Release Notes 自动取候选 exact SHA 的用户节及对比基线，旧版无结构化小节时保持原格式，不另维护第三份说明；附源码 SHA、原始构建链接、APK 哈希、证书、GitHub Attestations 入口和精确验证命令。两端 Notes 相同，Summary 给出发布链接与核验结果。

| 对象 | 格式 |
| --- | --- |
| Release 标题（两仓库） | `<versionName>` |
| 源码 tag | `<versionName>` |
| Xposed tag | `<versionCode>-<versionName>` |
| APK（候选及两端发布同名） | `CustoMIUIzer-A14-<versionName>.apk`，不追加 SHA、Run ID 或日期 |
| 其余公开附件 | `build-metadata.json` |
| 内部 Actions Artifact | `a14-signed-<run-id>-<attempt>`，可保留校验文件、证明 bundle 和验证报告 |

版本必须高于所有已发布版本（包含预发行）；两端均不能回退 Latest。当前版本取 Gradle 与双语 Changelog，实际发行状态取 GitHub Release。新生成的候选在认证之前即使用最终 APK 文件名；历史带 SHA 或无 SHA 候选继续按原文件名、元数据和证明验证，不改名、不重建。纯发布工程维护不升级应用版本号；已发布版本在构建前跳过。

正式 APK 检查包名、版本、SDK 34、arm64-v8a、不可调试、16 KB zipalign、V2 签名、历史证书及内嵌完整 sourceCommit。固定 SHA 的官方 actions/attest 先为最终 APK 生成在线证明；APK 在线核验通过后才在 `build-metadata.json` 写入实际认证状态，再为定稿元数据生成证明，最后重新在线核验两个文件。核验仓库、workflow、commit、ref、run / attempt、认证主体文件名与摘要，任何一步失败均不能上传候选或发布。

`build-metadata.json` 沿用原字段，记录包名、版本、SDK / ABI、APK 名称 / SHA-256 / 大小、历史签名证书、已通过的构建检查、完整源码 SHA、源码仓库 / workflow / run / attempt 及链接。新增 `recorded_at` 是生成候选记录的 UTC 时间，`release_assets` 明确公开附件契约；`attestation.apk_verified` 仅在 APK 已通过在线验证后写入。它不自称已验证自身，元数据自身的核验结果留在内部 `attestation-verification.json` 和 Actions 日志中，定稿后不再修改元数据。产物保留 30 天，无 Secret、私钥或本地签名配置。

公开附件只有 APK 和上述元数据，GitHub 自动生成的源码 ZIP / TAR.GZ 不受影响。内部仍计算并校验 `SHA256SUMS`，Release Notes 仍包含 APK SHA-256 和双语更新内容；不公开校验文件或独立 Sigstore bundle，不影响 GitHub 官方在线证明的生成和查询。在线验证按摘要取回证明，无需下载独立 bundle，参见 [GitHub 官方认证与 CLI 验证](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations)。

```sh
gh attestation verify candidate/<apk> --repo thetvplus/customiuizer-a14 --signer-workflow thetvplus/customiuizer-a14/.github/workflows/a14-signed-build.yml --source-digest <full-sha> --signer-digest <full-sha> --source-ref refs/heads/<branch> --deny-self-hosted-runners
```

内部离线核验可对 APK 传 `--bundle candidate/apk-attestation.sigstore.json`，对元数据传 `--bundle candidate/attestation.sigstore.json`；旧候选的同一 bundle 可含两个主体。GitHub 来源证明不能替代 Android 签名和设备验收。

### 结束与监控

Agent 完成实现、分支核验、按任务授权合并及 main 核验后，记录 exact SHA 和 Actions 链接即可结束，不创建定时轮询。后续确认、签名、按序发布、资产读回和失败状态由 Actions 自行管理；只有失败或用户要求时再介入。用户明确只要触发签名试用且自行查看时，传 actions-only，确认 run 绑定 SHA 后即可结束，标明“已触发，尚未确认成功”。不能用“已触发”冒充“已成功”，也不能保证远端权限、网络和实机永不出错。

签名受信分支仍由 release-policy 与 Environment 同时限制；普通工作分支不因整理、PR 或文档变更取得正式签名权限。临时分支注册触发已移除，正式入口仅保留 main CI 后续事件和手动操作。

### GitHub 认证标记

- **APK 来源证明**：使用官方 `actions/attest`，以 OIDC 身份生成、保存并验证。证明对应 APK 摘要与构建身份，可在 [Attestations](https://github.com/thetvplus/customiuizer-a14/attestations) 查看；此能力已在流程中接通。
- **Android APK 签名**：验证 APK 内容完整性与历史签名证书一致性，维持安装更新身份；它不代表代码安全审计通过，也不等同于 GitHub commit/tag 的 Verified。
- **绿色 Verified**：这是 Git commit / tag 的签名验证标记，与 APK 来源证明不同。需要 GitHub 可验证的 GPG / SSH / S/MIME 签名；工作流不生成替代签名身份、不改写历史标签，也不伪造徽标。若要让新发行的 tag 自身显示 Verified，应先用已登记公钥签署指向 exact candidate SHA 的新 tag；已有同名 tag 仅在指向同一 SHA 时允许发布，原签名得以保留。参见 [GitHub 签名标签说明](https://docs.github.com/en/authentication/managing-commit-signature-verification/signing-tags)。
- **Immutable**：仓库设置中开启 release immutability 后，新发行的 tag 和资产锁定，GitHub 自动生成 release attestation；与绿色 Verified 也不同。本流程使用“草稿→上传→校验→发布”，可兼容不可变发行；本次代码修改不变更仓库全局设置。参见 [GitHub 不可变发行](https://docs.github.com/en/code-security/concepts/supply-chain-security/immutable-releases)。
- **GitHub 资产 SHA-256**：Release Asset API 的 `digest` 是上传文件的官方摘要；发布前逐项比对大小和本地 SHA-256，不把摘要本身称为签名或安全审计。参见 [Release Assets API](https://docs.github.com/en/rest/releases/assets)。

整理既有 Release 时先核对两端 tag、immutable、原始资产、元数据及在线证明。只编辑标题不会更改 APK 或证明；[GitHub 允许不可变 Release 编辑标题和说明](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository#editing-a-release)，但不可变资产和 tag 不得改动。若旧元数据及证明已绑定带 SHA 文件名，保留原 APK 与元数据，统一命名从下一次新构建开始。非 immutable Release 的多余校验 / bundle 附件仅在在线核验实际下载的 APK 与元数据成功后按明确授权移除，并读回验证；不重签、替换 APK、移动 tag 或改写发布源码。

## 同一 APK 按需晋升与故障恢复

[A14 Promote Release](../.github/workflows/a14-promote-release.yml) 保留为候选验收、延后发布和失败恢复入口，默认 actions-only。正常接受已完成成功的 A14 Signed Build；唯一失败例外是同一次 attempt 中 Validate / Sign 都成功、只有 Publish 失败，仍重新验证原 artifact、Android 签名与 GitHub 来源证明。验证或签名失败、取消、过期、重跑后身份变化的候选不能借此发布。

实际发布选择 source / both，输入 `publish <versionName> from <run-id>`；预发行还要选择与原版本一致的 prerelease。固定 source SHA、run / attempt、artifact ID 和 APK 哈希，发布步骤再次核验收据。

```sh
gh workflow run a14-promote-release.yml --ref main -f build_run_id=<original-run-id> -f destination=both -f 'authorization=publish <versionName> from <original-run-id>'
```

两条发布入口共用 a14-release-publish 锁。默认顺序严格为“源码草稿 → APK 与元数据两资产校验 → 源码公开与读回 → Xposed tag / 草稿 → 同两资产校验 → Xposed 公开与读回”。不并行跨仓库发布，已有 immutable Release 只读验收，异常 immutable 草稿拒绝任何资产写入。

若 Xposed 失败，保留主仓库已成功版本；使用原 run ID 晋升恢复，不重跑整条构建。恢复先比对已发布版本的 title、notes、target SHA、tag、预发行状态及全部资产哈希 / 大小，完全匹配则只读跳过。归属同一候选的草稿仅补缺失资产，已有资产必须逐项一致；不覆盖、不删除、不静默接受篡改。主仓库已公开并核验的同一候选可以在 main 后续前进后补发 Xposed，但必须仍有原 SHA 的成功 main CI，且两端不存在更高已发布版本。尚未公开的源码草稿仍要求当前 main 未变。

历史候选保留原始内部四文件结构及校验门禁；已存在的历史四资产 Release 或含旧 sidecar 的部分草稿按原四资产逐项验收和补齐。历史候选新建 Release 默认只上传 APK 与原始元数据；已精简为两资产的历史版本也可只读验收。历史标题可为旧格式或已修正的版本名，发布旧草稿时统一为版本名；新候选严格执行两资产和版本名标题。自动恢复不整理已发布版本的附件，也不改写其元数据来迁就新格式。

更新记录以默认中文 `CHANGELOG.md`、英文 `CHANGELOG_EN.md` 命名。恢复候选时从原始 source SHA 的根目录识别新旧双语文件布局，按当时的语言归属读取；旧布局仍支持，不改历史记录。布局缺失、冲突或读取失败即停止，不回退到最新分支的说明。

跨仓库没有原子事务，网络中断时可能出现主仓库成功、Xposed 待恢复；流程明确失败并保留证据，不为伪装成功回删主仓库。最终成功要求两个目标均完成公开资产和标签 / Latest 核验。不存在既有候选且版本未递增时不能覆盖任何已发布版本。
