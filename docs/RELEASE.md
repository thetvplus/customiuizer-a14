# 发布与恢复

main 合并、正式签名、tag 与公开发行以当前任务授权为准。普通 Debug / Develop、工作分支验证与本地签名试用分别记录，不能作为已经正式发布的结论。长期发布规则只维护在本页。

## 历史匿名化与已签名版本

2026-10-31 经明确授权，将公开开发历史整合为各保留正式版的匿名源码快照；这是一次性隐私整理，不放宽日常禁止 force push、移动发行标签或改写公开历史的规则。上游许可与署名保留。历史 APK、证书、大小、SHA-256 和原始 build-metadata.json 不修改、不重签。

旧 metadata 的 source_sha 是原始构建标识，不能改称匿名快照的提交。`tools/release-policy.json` 的 archived_source_snapshots 只列四个已发行版本：固定原源码标识、原始 metadata 摘要、APK 摘要、新快照及原 app 树。读回必须同时匹配这些身份、快照 app 树和双仓标签；后续新发行仍绑定真实构建 SHA，不自动增加例外。累计说明与发布安全基线的祖先检查使用已核验的快照，签名 APK 和原始 metadata 继续提供二进制身份。

公开来源说明链接匿名快照，并单独保留原构建标识。原工作流日志与必要发行证据私有归档后撤下旧 Actions，新的 main 另做一次 exact CI；不会为整理历史重复构建或签发同版 APK。旧候选在线恢复不再适用于已撤下的运行。

GitHub 管理的 PR 引用、缓存及外部克隆可能仍保留旧对象；主线 / 标签改写不能证明这些副本已清除，必要时由仓库所有者联系官方支持。见 [GitHub 敏感数据清理说明](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository)。

## 版本与记录

- 仅发行 HyperOS 1 / Android 14 / SDK 34 / arm64-v8a，保持既有 applicationId、libxposed API 102 与历史签名证书。
- `versionName` 为 `r14.x.y`；`versionCode` 单调增加。Gradle、双语 README 与双语 Changelog 一起准备和检查。
- 源码 tag 为 `<versionName>`，下载仓库 tag 为 `<versionCode>-<versionName>`；新 tag 均绑定构建 APK 的完整 source SHA。下载仓库 metadata main 单独维护。
- 保留真实历史发行身份、APK 来源和源码提交；不移动历史 tag，不重写旧源码。被替代版本经授权清理后，可将有效变化并入累计版本并移除独立展示章节，原文保留在 Git 历史中。用户明确授权替代发行清理时，仅在双仓新版验收后删除被替代的公开页面、附件与标签，保留 r14.24.1 及更早正式版。
- README 的源码版本不代表公开状态；以两仓库 Release 及实际资产读回判断是否完成发行。

## Actions 构建与自动发布

启用后只有一条正常路径：**main A14 CI 成功 → 核验来源、版本和说明 → 正式签名一次 → 验收源码仓库 → 同步并验收 Xposed 仓库 → 更新下载文档**。无需人工审批或 Agent 常驻。

1. 普通 [A14 CI](../.github/workflows/a14-ci.yml) 保持无正式密钥的现有验证；PR、fork、失败 CI、非 main 或已过期 SHA 不能触发正式签名。纯 Markdown 的现有跳过策略不变。
2. [A14 Signed Build](../.github/workflows/a14-signed-build.yml) 只接受原生 main 事件。`workflow_run` 复查原 CI 的仓库、workflow ID / path、事件、SHA、完成状态与 attempt，并要求仍为当前 main。手动入口也只信任原生 ref / SHA，不接收源码选择输入。
3. 已公开的当前版本正常跳过，不初始化 JDK / SDK 或签名。未公开版本先核对指定正式目标、版本递增、完整双语记录、两种基线及 Environment 分支限制。
4. 签名前重查 exact main 与成功 CI，复用该 CI 的源码、Host、工具、lint、Develop / R8 结果。签名作业只补一次正式 Release 构建、fatal lint、R8 产物及 APK 检查，不再自调度第二条签名运行。
5. 签名与发布分别使用 `release-signing`、`release-publishing`。两环境均仅允许 main branch，无 Required reviewer、等待计时或外部人工批准；脚本发现配置漂移即失败。普通 CI 不取得正式密钥。
6. 发布先检查两端现有版本、tag、草稿、资产和跨仓凭据；源码公开并核验后才写入下载仓库。两个发布入口共用 `a14-release-publish` 锁。

工作流不自动合并分支，不修改分支保护，不按每次 main 提交重新发行。正常发布作业使用本仓库 `contents:write` 与 `actions:read`；签名仅 `contents:read`、`actions:read`。不需要 OIDC 或 Attestations 权限。

仓库简介与交流群入口统一维护在 `tools/release-policy.json`。双仓库 About 使用短简介与群链接；源码双语 README、下载仓库文档生成器和应用关于页保持相同入口，文档反馈不再链接 Issues。新发布说明在来源信息之后附一行普通群链接，不使用标题或独立大区块；历史原候选恢复仍接受原文，并继续严格核对版本、来源、正文和资产。不为补群链接重建 APK 或修改旧版功能说明。

### 当前正式版本

当前已公开正式版为 **r14.26.7 / 267**，说明基线为 **r14.26.5**；双仓稳定版、Latest、两份附件与下载文档均已读回核验。源码为 `8a9ce0d8dcbd3c16601b3e543247674677c69f3e`，对应 [exact main CI](https://github.com/thetvplus/customiuizer-a14/actions/runs/41195474626) 与 [自动签名 / 发布](https://github.com/thetvplus/customiuizer-a14/actions/runs/41196550714)。APK 为 5,997,909 字节，SHA-256 为 `80f32bd50fec08a8a0ffbdfea46878fc2a9a5e0d54a15b883b95d4286824b031`；历史证书、SDK / ABI、签名、对齐和来源门禁通过。Develop 实机验收范围与未追加边界见[原路线图](maintenance/A14_REFINEMENT_ROADMAP.md)；正式 APK 已覆盖安装，安装文件摘要与发布附件一致，未追加功能测试。

上一轮 **r14.26.5 / 265** 的说明基线为 **r14.25.9**；当时双仓稳定版、Latest、两份附件与下载文档均已读回核验。原构建源码标识为 `2037b6da9195fbf406113ded89698a3812be81d2`，对应匿名快照 `313defbd7b1f50892d3cbd48e72f228844ded5d5`；原 CI / 签名证据私有归档，APK 保持原签名和二进制。APK 为 5,979,909 字节，SHA-256 为 `37d0a445199083f4ff56ab3562c0200c5ba4db03dda3d2abbef90b5dc05581ca`；历史证书、对齐、来源与最终 DEX 边界通过。Develop 实机验收已通过；正式 APK 的身份、证书、对齐和 DEX 已离线验收，未做最终安装复验；r14.26.3 的旧页面、附件与标签已按授权从两端删除。验收范围见[原路线图](maintenance/A14_REFINEMENT_ROADMAP.md)。

2026-10-29 的 r14.25.9 双仓发行已完成；r14.25.8 / 257 仅为未公开本地候选，其有效变化已累计进入 r14.25.9，不发布或晋升旧候选。旧 r14.25.5 / 250 候选仍不得晋升，不移动或覆盖历史标签。

2026-10-31 的 r14.26.1、r14.26.3 有效变化已累计进入 r14.26.5。新版完成双仓与同一生产实现的实机验收后，已按授权删除旧版两端页面、附件与标签；旧源码保留在 Git 历史，r14.24.1 及更早正式版保持不变。当前版本的标签与产物不移动或覆盖，设备持续成本的未测范围仍单独保留。

### 手动与恢复入口

手动 A14 Signed Build 默认 `both`，可选 `source` 或 `actions-only`；`prerelease` 默认 false。所有签名包括仅候选都要求当前 main 的 exact CI 通过。`actions-only` 不创建 tag / Release；不能用该选项绕过签名环境或 CI。

```sh
# 仅在正式发行已获授权、配置核对并启用后使用。
gh workflow run a14-signed-build.yml --ref main
# 仅构建签名候选，不公开。
gh workflow run a14-signed-build.yml --ref main -f destination=actions-only
```

预发行两端均标为 prerelease，不抢占 Latest。正式版本必须高于全部已发布版本（包括预发行），code 也必须递增。新版本按 `release-policy.json` 更新正式目标；纯发布工程维护不自动升版。

### 一次性配置

两 Environment 选择 **Selected branches and tags**，只添加 `main`、类型 **branch**；不能添加通配符、同名 tag 或临时分支。脚本同时核对策略类型、完整策略数量及名称。

| Environment | Secrets | 保护规则 |
| --- | --- | --- |
| `release-signing` | `A14_KEYSTORE_BASE64`、`A14_STORE_PASSWORD`、`A14_KEY_ALIAS`、`A14_KEY_PASSWORD` | 仅 main；无人工审批或等待规则 |
| `release-publishing` | `A14_XPOSED_RELEASE_TOKEN` | 仅 main；无人工审批或等待规则 |

签名材料为既有 PKCS#12 `.p12`，证书 SHA-256 固定为 `c0eff2dc4e662717195490da78b12a984c6f2e6bd38acf4edad14d53e3d22e70`。只在受限签名步骤恢复临时密钥，先核对证书，结束时始终清理；候选上传和发布时没有私钥。配置步骤不读取或输出 Secret 值。

跨仓凭据限定到 `Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14`，只放在 publishing Environment。细粒度 PAT / GitHub App 需要 Contents:write 与 Workflows:write；新源码 tag 包含 workflow 文件，普通仓库 push 权限不能证明此权限。经典 PAT 对应 repo / public_repo 与 workflow。凭据到期或服务器拒绝时保留可恢复状态，不能先声称两仓成功。[GitHub Release API](https://docs.github.com/en/rest/releases/releases#create-a-release)

## 两种基线与累计说明

**发行说明基线**决定累计展示哪些有效变化；**发布安全基线**固定取最近实际公开成功的稳定版。两者独立核验，累计写作不能跳过版本、证书、资产或源码安全检查。

- 安全基线读取实际 Latest、正式状态、tag / source SHA、APK 与构建元数据摘要、历史证书及 A14 平台契约。只发布源码的一次真实正式发行也可成为源码安全基线。
- 累计基线由 `cumulative_releases.<version>.notes_base` 指定。早期版本没有现代 metadata 附件时，仍必须核验实际稳定 Release、tag 解析出的完整 SHA 和该源码声明的版本。
- 校验“说明基线 → 当前稳定安全基线 → 最终候选”的 Git 祖先关系，并查看说明基线到候选 exact SHA 的完整 diff。双语 `自 … 起的变更 / Changes since …` 使用说明基线。
- r14.25.5 统一从 r14.24.1 累计，整合 r14.25.1、r14.25.3 及后续有效修改，去重并排除撤销方案、调试过程与尚未实现内容。r14.25.1、r14.25.3 的有效变化统一收进 r14.25.5；原始记录保留在 Git 历史中，r14.24.1 及更早章节保持原文。替代页面与标签按上文授权清理。

日常维护 `CHANGELOG.md` 的“未发布”和 `CHANGELOG_EN.md` 的“Unreleased”。用户内容与工程记录分别放在 `用户变化 / User changes`、`工程说明 / Engineering notes`。首次准备可使用 `prepare_release.py --dry-run` 预览；当前版本已准备，检查一致性即可：

```sh
python tools/check_release_metadata.py
```

准备工具先验证全部输入，再统一更新五个版本文件；不 commit、push、打 tag 或发布。当前 r14.26.5 / 265 已正式发布，双语记录按 r14.25.9 对照；不要对同一版本重复升版或重新签名，也不把元数据准备当成签名或发行成功。准备工具保留真实历史；后续版本按发布策略递增，不创建额外试用版名称。旧 `--supersede / --base-version` 仅兼容旧的源码整理用途，不能与 `--notes-base` 混用。

### Release Notes 质量

- 每条突出一个核心功能或用户能感知的结果，必要时补使用方法及重启要求。同类小修可合并，不强行拼接不同重要能力。
- 不限制条目数量，不凑数、不为压缩条数删掉有效变化。禁止重复条目；双语内容须完整对应，语言自然。
- 每项描述须有最终源码依据，并结合完整累计 diff 核实仍然生效。结构检查只能核对身份、双语覆盖数量、基线及重复，不能代替语义审核或设备验收。
- 不将仅构建 / 测试通过描述为已经正式发布，不夸大未测性能、功耗、ROM 兼容或稳定性。已实现、已有验证与仍待确认分别表述。
- 不公开无必要的具体第三方应用、私人场景、设备身份或本地证据路径；工程细节保留在必要维护资料，遵守 [文档规范](DOCS_STYLE.md)。

Release Notes 从候选 exact source SHA 的双语用户节生成，两仓内容一致；工程节只留在 Changelog。附完整 source SHA、原始构建链接、APK SHA-256 与历史签名证书。旧版布局从原始 SHA 识别，不读取最新分支来替代原版说明。

## APK、候选与双仓验收

| 对象 | 契约 |
| --- | --- |
| Release 标题 | `<versionName>` |
| APK | `CustoMIUIzer-A14-<versionName>.apk`，两仓完全同一二进制 |
| 其他公开附件 | `build-metadata.json` |
| 内部候选 | `a14-signed-<run-id>-<attempt>`，保留 30 天 |

APK 检查 applicationId、版本、SDK 34、arm64-v8a、不可调试、16 KB zipalign、V2 签名、历史证书及内嵌 sourceCommit。metadata 记录 APK 名称 / SHA-256 / 大小、平台身份、构建检查、完整 source SHA、仓库 / ref / workflow / run / attempt 及链接、UTC 记录时间和两项公开资产契约；不再生成 Attestation 字段。

签名作业的 APK、SHA、attempt 与不可变 Artifact ID 形成作业输出收据。下载使用原始 Artifact ID 的官方归档接口，先比对 GitHub API 的归档 digest，再解压；限制归档 32 MiB、16 个平面普通文件及总展开大小，拒绝重复路径、目录和符号链接。之后独立核验原运行身份、metadata、APK hash、源码版本、Android 签名与 provenance，并复查 attempt 未变；发布步骤重做核验并匹配收据。不能用一份可改 metadata 中的 hash 自证可信。

不生成或在线验证 GitHub Attestations，不需要 `actions/attest`、Sigstore、OIDC 或证明查询权限。旧候选 metadata / sidecar 只按原文件只读兼容，不在线查询证明、不修改旧 metadata；历史四附件 Release 的已有资产仍按原 digest 校验，新候选与新建 Release 仅两附件。

严格顺序为“源码草稿 → 上传缺失资产 → 两资产核验 → 源码公开与读回 → 下载仓 tag / 草稿 → 同资产核验 → 下载仓公开与读回”。每端检查 tag / source、标题、Notes、draft / prerelease、Latest、唯一预期资产、官方 digest 和大小。可信 digest 完整且匹配时无需重复下载；缺失或异常则失败或下载核验。上传退出成功不能作为发布成功。

两仓核验后由 `release_docs.py` 从已公开的最新稳定源码生成下载仓 README、Changelog、SUMMARY。一次非强制 Git 提交更新，提交前重查 Latest / main，提交后核对树内实际 blob；无变化即不写。较旧发行不能覆盖较新文档。

## 同一 APK 按需晋升与故障恢复

[A14 Promote Release](../.github/workflows/a14-promote-release.yml) 是原候选验收、延后发布与恢复入口，默认 `actions-only`。只接受完整成功的签名运行；唯一失败例外是同一 attempt 的 Validate、Sign 成功且只有 Publish 失败。签名失败、验证失败、取消、产物过期或身份变化均拒绝。

```sh
# 已核对原候选，并获本次恢复发布授权后使用；没有额外审批文本。
gh workflow run a14-promote-release.yml --ref main -f build_run_id=<original-run-id> -f destination=both
# 两端发行已完成，仅重试下载文档，不重新签名、上传或创建 Release。
gh workflow run a14-promote-release.yml --ref main -f destination=docs-only -f version=<published-version>
```

原生 main workflow、run ID、destination、prerelease 必须匹配实际 dispatch；不接受额外授权字符串或任意源码输入。恢复使用原 artifact ID / attempt 与完整收据，保持签名和二进制不变。

- 已公开的同一源码发行完全匹配 title、Notes、target SHA、tag、状态和资产时只读跳过；同一候选草稿仅补缺失附件，已有内容不符即失败，不覆盖或删除资产。
- 首次公开或未公开源码草稿仍要求当前 main 未变且 exact CI 成功。源码已公开后即使 main 前进，也可恢复同一候选的下载仓发布，但原 SHA 成功 main CI 必须保留，两端不能有更高发行。
- Xposed 发布失败保留源码端成功状态；凭据或网络修好后用原 run ID 恢复，不重跑签名、不回删成功发行。跨仓没有原子事务，部分成功必须报告为待恢复。
- 下载文档失败只重试 docs-only；必须是两端共同最新稳定版，不能借旧候选回退文档。

## Actions、产物与证明清理

清理先盘点并固定具体 ID，写入前重新查询活动状态和引用，删除后重新查询实际结果。使用已有 `gh` 与官方 API，不添加常驻清理服务，也不给正常发布作业增添删除权限。

必须保留当前运行、其他 Agent 的活跃构建证据、当前 exact main CI、有效设备验收 CI、实际正式发行仍引用的构建及可恢复候选。恢复完成前保留原运行、attempt、Artifact、metadata、对应 CI 和恢复运行。不得按“失败”或“时间较旧”一刀切删除。

只有正式双仓完成核验后，或候选已明确撤回且无恢复依赖时，才按授权清理失去价值的临时候选、重复 / 失效 / 无诊断价值的运行及关联产物和日志。当前运行不能自删；需要时在后续维护执行。正式 APK、签名身份、SHA-256、必要构建来源元数据和有效 Git 历史始终保留。

本仓既有 GitHub Attestations 使用官方用户级 [批量查询与删除 API](https://docs.github.com/en/rest/users/attestations) 清理：先枚举本仓证明及主体摘要，批量查询确认每条 repository_id 都属于本仓，防止相同摘要命中其他仓库，再申请删除并重新查询；权限不足明确报告，不能以请求退出成功替代读回。公开历史 Notes 只移除失效证明链接 / 验证命令，不篡改旧功能、源码、证书或 APK。仓库外证据可保存清理前身份与必要记录。

仓库 release immutability 会由 GitHub 自动生成 release attestation，独立于工作流的显式证明步骤。核对仓库可控配置及实际来源；若无法彻底移除，报告限制，不声称零证明。正式构建不依赖此功能。Git commit / tag 的 Verified 与 APK 签名、资产摘要各有含义，不伪造徽标或改写历史签名。[GitHub 不可变发行](https://docs.github.com/en/code-security/concepts/supply-chain-security/immutable-releases)

## 本地正式签名与设备证据

本地正式构建另需任务明确授权，密钥和 properties 放在仓库外，保持当前 JDK 27 与构建工具；不在工作流写入本机路径。使用 `-PcustomiuizerA14KeystoreProperties` 或 `CUSTOMIUIZER_A14_KEYSTORE_PROPERTIES` 指定绝对路径，properties 包含 storeFile、storePassword、keyAlias、keyPassword；相对 storeFile 按 app/ 解析，Windows 路径建议正斜杠。

```powershell
.\scripts\check-signing-config.ps1 -RequireSigning -KeystoreProperties "<external-absolute-path>/keystore.properties"
# 预检只检查配置与文件，不验证密码或证书。
$releaseRevision = git rev-parse --short=8 HEAD
.\gradlew.bat clean :app:assembleRelease :app:lintVitalRelease -PofficialRelease=true -PrequireBuildRevision=true "-PbuildRevision=$releaseRevision" "-PcustomiuizerA14KeystoreProperties=<external-absolute-path>/keystore.properties"
```

`check_release_metadata.py --require-tag` 校验源码 tag / 版本，`verify_apk_provenance.py` 检查 APK 来源。需要设备验收时对明确 APK 使用保留数据的覆盖安装，分别记录 ROM、已测功能与未测范围，空闲锁屏并结束临时采集。Host、静态 ROM、Develop、R8 / D8 与正式签名分别报告，不能互相替代。
