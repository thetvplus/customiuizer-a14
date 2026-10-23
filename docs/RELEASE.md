# 发布

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
3. main CI 成功后自动唤起 [A14 Signed Build](../.github/workflows/a14-signed-build.yml)。普通版本已发布则结束；只有版本递增、元数据与双语发行条目齐备时才请求发布确认。失败 / PR / fork / 非 main / 过期 SHA 的 CI 不进入正式签名。
4. 维护者在 **release-signing → Review deployments → Approve** 确认该版本、完整 SHA 和双语说明。拒绝或取消即可保持未发布；这是自动路径唯一必需的人工确认。
5. 确认后重查 main 和 CI，使用历史证书只构建一次正式 Release，核验 APK、R8、fatal lint 与 GitHub 来源证明。复用同 SHA 的 main CI，不再重复 JVM、lint 或 develop 构建。
6. 默认先发布源码仓库，核对公开资产、标签和 Latest，再发布 Xposed 仓库。两端保持完全相同的 APK、摘要、构建元数据和 Sigstore bundle；不重建、不重签。任一检查失败即停止后续写入。

工作流不自动合并 PR、不变更分支保护，也不因每次合并都发布。分支保护建议只要求现有 **Fast CI**，保留一个明确合并动作，避免新增一套自动合并机器人。`workflow_run` 仅监听 `A14 CI` 的 main 完成事件，并复核 API 中的 workflow ID、原始 attempt、仓库、事件、SHA 与当前 main；不下载 PR 产物执行代码，也不接收任意 ref 输入。

### 手动入口

也可在 main CI 通过后，手动运行 A14 Signed Build。手动选择是本次操作授权；已配置的 Environment 审核仍会生效。预发行和仅候选通过此入口选择。

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
# 试用明确选择 actions-only；可使用已授权的受信分支。
gh workflow run a14-signed-build.yml --ref main -f destination=actions-only
```

仅候选在没有可复用 CI 时会补齐源码、工具、JVM、lint、develop 与 R8 门禁。未授权分支只做无密钥核验。新发布一律要求同 SHA 的 main CI 成功；分支候选晋升时，该 SHA 必须已通过 main CI。普通 CI 不接触正式密钥，develop 产物保留 unsigned / develop 标识。

### 一次性配置

在源码仓库配置两个 Environment，均选择 **Selected branches and tags**，逐一添加 `tools/release-policy.json` 中授权的 branch，不添加通配符或 tag。新增受信任分支必须先审查其工作流、工具和 Gradle，再同步更新策略及 Environment。

| Environment | 配置 |
| --- | --- |
| `release-signing` | 至少一个 Required reviewer；四个签名 Secrets：`A14_KEYSTORE_BASE64`、`A14_STORE_PASSWORD`、`A14_KEY_ALIAS`、`A14_KEY_PASSWORD` |
| `release-publishing` | `A14_XPOSED_RELEASE_TOKEN`；推荐不重复设置 Required reviewer，沿用前一阶段的确认。若保留第二次审核，流程会尊重该保护 |

自动路径会先检查 release-signing 确实有审核人及显式分支限制，配置缺失则停止，不把空 Environment 当作确认。单维护者仓库可允许本人审批自己触发的运行。工作流不代改管理员保护设置；本任务 GitHub 插件也不提供 Environment / Secrets 管理能力，不能声称远端设置已配置或凭据已验证。

签名 Secret 必须是现有 PKCS#12 `.p12`。证书固定为正式证书 SHA-256 `c0eff2dc4e662717195490da78b12a984c6f2e6bd38acf4edad14d53e3d22e70`，不生成替代密钥。临时签名材料只在受保护构建步骤恢复，先核对证书，结束时清理；证明生成与发布前已移除密钥。

跨仓库凭据仅放在 release-publishing，使用现有、限定到 `Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14` 的凭据。细粒度 PAT / GitHub App 需要 **Contents: write 和 Workflows: write**：新 tag 携带源码提交，其 `.github/workflows/` 与下载仓库 metadata main 不同；经典 PAT 对应 `repo` / `public_repo` 和 `workflow`。组织要求审批时先完成审批，并留意凭据到期。参见 [GitHub Release 权限说明](https://docs.github.com/en/rest/releases/releases#create-a-release)。源码发布使用本仓库 GITHUB_TOKEN 的 contents:write；不扩大普通 CI 权限。

发布前检查跨仓库凭据存在、仓库写权限、两端既有版本 / 草稿 / tag / 资产冲突；权限不足不先发布主仓库。账号 push 权限检查不能证明细粒度 token 的全部权限或未来网络可用性，实际服务器拒绝会停止并保留可恢复状态。Xposed 仅增加新源码 tag 和 Release，metadata main 独立维护。

### Changelog、说明与命名

日常只在 `CHANGELOG_CN.md` 的“未发布”和 `CHANGELOG.md` 的“Unreleased”写已确认的用户变化。准备发版时执行一次：

```sh
python tools/prepare_release.py --version r14.26.1 --code 247 --date 2026-10-23
# 仅预览涉及文件，可追加 --dry-run。
python tools/check_release_metadata.py
```

上例是下一版本的示范，不代表已决定或发布该版本。工具先验证全部输入，再同步 Gradle、双语 README 与两个 Changelog，将已审核条目移入带日期的新版本节，保留历史并清空未发布节。空翻译、版本重复、版本倒退均拒绝；工具不提交、不推送、不打 tag，也不凭提交消息编造更新内容。将这五个文件与代码一并审查、提交到分支，然后走上述合并和发布流程。

Release Notes 自动取候选 exact SHA 的本版中英文节，不另维护第三份说明；附源码 SHA、原始构建链接、APK 哈希、证书、GitHub Attestations 入口和精确验证命令。两端 Notes 相同，Summary 给出发布链接与核验结果。

| 对象 | 格式 |
| --- | --- |
| Release 标题 | `CustoMIUIzer A14 · <versionName>`（两仓库统一使用英文软件名） |
| 源码 tag | `<versionName>` |
| Xposed tag | `<versionCode>-<versionName>` |
| APK（候选及两端发布同名） | `CustoMIUIzer-A14-<versionName>-<sha6>.apk` |
| 其余三个资产 | `SHA256SUMS`、`build-metadata.json`、`attestation.sigstore.json` |

版本必须高于所有已发布版本（包含预发行）；两端均不能回退 Latest。当前已发布 `r14.25.1` / `246` 保留，本次流程修改归入 Unreleased，不重发旧版。旧无 SHA 文件名候选继续按原证明验证，不改名、不重建。

正式 APK 检查包名、版本、SDK 34、arm64-v8a、不可调试、16 KB zipalign、V2 签名、历史证书及内嵌完整 sourceCommit。固定 SHA 的官方 actions/attest 证明 APK 和元数据，独立核验仓库、workflow、commit、ref、run / attempt 和摘要。产物 `a14-signed-<run-id>-<attempt>` 保留 30 天。

```sh
gh attestation verify candidate/<apk> --repo thetvplus/customiuizer-a14 --signer-workflow thetvplus/customiuizer-a14/.github/workflows/a14-signed-build.yml --source-digest <full-sha> --signer-digest <full-sha> --source-ref refs/heads/<branch> --deny-self-hosted-runners
```

也可传 `--bundle candidate/attestation.sigstore.json`；GitHub 来源证明不能替代 Android 签名和设备验收。

### 结束与监控

Agent 完成实现、分支核验、按任务授权合并及 main 核验后，记录 exact SHA 和 Actions 链接即可结束，不创建定时轮询。后续确认、签名、按序发布、资产读回和失败状态由 Actions 自行管理；只有失败或用户要求时再介入。用户明确只要触发签名试用且自行查看时，传 actions-only，确认 run 绑定 SHA 后即可结束，标明“已触发，尚未确认成功”。不能用“已触发”冒充“已成功”，也不能保证远端权限、网络和实机永不出错。

签名受信分支仍由 release-policy 与 Environment 同时限制；普通工作分支不因整理、PR 或文档变更取得正式签名权限。临时分支注册触发已移除，正式入口仅保留 main CI 后续事件和手动操作。

### GitHub 认证标记

- **APK 来源证明**：使用官方 `actions/attest`，以 OIDC 身份生成、保存并验证。证明对应 APK 摘要与构建身份，可在 [Attestations](https://github.com/thetvplus/customiuizer-a14/attestations) 查看；此能力已在流程中接通。
- **绿色 Verified**：这是 Git commit / tag 的签名验证标记，与 APK 来源证明不同。需要 GitHub 可验证的 GPG / SSH / S/MIME 签名；工作流不生成替代签名身份、不改写历史标签，也不伪造徽标。若要让新发行的 tag 自身显示 Verified，应先用已登记公钥签署指向 exact candidate SHA 的新 tag；已有同名 tag 仅在指向同一 SHA 时允许发布，原签名得以保留。参见 [GitHub 签名标签说明](https://docs.github.com/en/authentication/managing-commit-signature-verification/signing-tags)。
- **Immutable**：仓库设置中开启 release immutability 后，新发行的 tag 和资产锁定，GitHub 自动生成 release attestation；与绿色 Verified 也不同。本流程使用“草稿→上传→校验→发布”，可兼容不可变发行；本次代码修改不变更仓库全局设置。参见 [GitHub 不可变发行](https://docs.github.com/en/code-security/concepts/supply-chain-security/immutable-releases)。

## 同一 APK 按需晋升与故障恢复

[A14 Promote Release](../.github/workflows/a14-promote-release.yml) 保留为候选验收、延后发布和失败恢复入口，默认 actions-only。正常接受已完成成功的 A14 Signed Build；唯一失败例外是同一次 attempt 中 Validate / Sign 都成功、只有 Publish 失败，仍重新验证原 artifact、Android 签名与 GitHub 来源证明。验证或签名失败、取消、过期、重跑后身份变化的候选不能借此发布。

实际发布选择 source / both，输入 `publish <versionName> from <run-id>`；预发行还要选择与原版本一致的 prerelease。固定 source SHA、run / attempt、artifact ID 和 APK 哈希，发布步骤再次核验收据。

```sh
gh workflow run a14-promote-release.yml --ref main -f build_run_id=<original-run-id> -f destination=both -f 'authorization=publish <versionName> from <original-run-id>'
```

两条发布入口共用 a14-release-publish 锁。默认顺序严格为“源码草稿 → 四资产校验 → 源码公开与读回 → Xposed tag / 草稿 → 同四资产校验 → Xposed 公开与读回”。不并行跨仓库发布。

若 Xposed 失败，保留主仓库已成功版本；使用原 run ID 晋升恢复，不重跑整条构建。恢复先比对已发布版本的 title、notes、target SHA、tag、预发行状态及全部资产哈希 / 大小，完全匹配则只读跳过。归属同一候选的草稿仅补缺失资产，已有资产必须逐项一致；不覆盖、不删除、不静默接受篡改。主仓库已公开并核验的同一候选可以在 main 后续前进后补发 Xposed，但必须仍有原 SHA 的成功 main CI，且两端不存在更高已发布版本。尚未公开的源码草稿仍要求当前 main 未变。

跨仓库没有原子事务，网络中断时可能出现主仓库成功、Xposed 待恢复；流程明确失败并保留证据，不为伪装成功回删主仓库。最终成功要求两个目标均完成公开资产和标签 / Latest 核验。不存在既有候选且版本未递增时不能覆盖 r14.25.1。
