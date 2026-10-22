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

在工作区干净、最终候选 source SHA 已确认后构建；设备验收通过后将同一提交快进至 main，使用同一 APK 发布：

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

## Actions 正式候选

[A14 Signed Build](../.github/workflows/a14-signed-build.yml) 手动执行，默认选择 main。也可使用原生分支选择器或 Agent 接口：

```sh
gh workflow run a14-signed-build.yml --ref main
gh workflow run a14-signed-build.yml --ref codex/a14-feature-refinement-20261019
gh run list --workflow a14-signed-build.yml
gh run view <run-id>
gh run download <run-id> --name a14-signed-<run-id>-<attempt> --dir candidate
```

首次启用需要管理员配置两个 Environment：`release-signing` 和 `release-publishing`，均使用 **Selected branches and tags**，只添加 `tools/release-policy.json` 中逐一授权的 branch 规则，不添加通配符或 tag 规则。新增受信任分支前必须先审查其工作流、工具和 Gradle，再同步更改策略和 Environment；从该分支原生触发，禁止通过输入任意 ref 后 checkout 获取密钥。撤销授权时同步移除两处规则。

`release-signing` 仅包含四个签名 Secrets：`A14_KEYSTORE_BASE64`（原 PKCS#12 `.p12` 的 Base64）、`A14_STORE_PASSWORD`、`A14_KEY_ALIAS`、`A14_KEY_PASSWORD`。证书固定为现有正式证书 SHA-256 `c0eff2dc4e662717195490da78b12a984c6f2e6bd38acf4edad14d53e3d22e70`；更换证书不属于普通工作流维护。不得新建密钥或将本地 properties 上传到仓库。

工作流固定触发时的完整 SHA。先复用同 SHA、同仓库、受信分支且成功的 A14 CI；缺少时执行一次源码、Python、JVM 和 lint 门禁。无密钥验证完成后，受保护任务在临时目录恢复签名材料，先验证证书，再只构建一次正式 Release。所有结果结束时清理签名材料。未授权分支只验证，不进入签名任务。

正式 APK 检查包名、版本、SDK 34、arm64-v8a、不可调试、16 KB zipalign、V2 签名和历史证书，以及 APK 内嵌短 revision 和完整 `sourceCommit`。R8 检查和 fatal lint 必须通过。最终 APK 和构建元数据使用固定 SHA 的官方 [actions/attest](https://github.com/actions/attest) 生成来源证明，再校验仓库、工作流身份、完整提交、ref、原始 run / attempt 和摘要。Android 签名与 GitHub 来源证明独立验证。

未发布的正式候选命名为 `CustoMIUIzer-A14-<versionName>-<source SHA 前六位>.apk`，例如 `CustoMIUIzer-A14-r14.24.1-502d9a.apk`。版本来自该提交的 Gradle 配置，SHA 来自触发时的完整提交；改名不改 APK 内容，校验、元数据、摘要、证明与后续晋升均使用这一文件名。旧的无 SHA 文件名候选仍按原始证明验证，不重命名或重建。普通 CI 的 develop 产物也带短 SHA，并保留 develop / unsigned 标识，始终出现在 Artifacts。

成功产物 `a14-signed-<run-id>-<attempt>` 保留 30 天，包含唯一 APK、`SHA256SUMS`、`build-metadata.json`、Sigstore bundle 和非敏感验证结果。Summary 提供版本、提交、APK 哈希及下载链接。失败时没有正式候选；不会创建 tag / Release 或改变版本号。

用户只要求通过 Actions 生成签名试用 APK，并明确表示自行查看、无需等待时：完成必要本地验证，复用相同源码 / 范围的通过结果；从已授权受信分支触发，确认运行绑定预期 exact SHA 后给出链接即可收口。不轮询至完成、不为交付重复下载或本地验证产物、不自动创建后续监控；如实标为“已触发，尚未确认构建成功”。Actions 自身的验证、签名、证书与来源证明门禁继续执行。公开发布和 main 合并仍按各自授权处理。

下载后的按需校验：

```sh
gh attestation verify candidate/<apk> --repo thetvplus/customiuizer-a14 --signer-workflow thetvplus/customiuizer-a14/.github/workflows/a14-signed-build.yml --source-digest <full-sha> --signer-digest <full-sha> --source-ref refs/heads/<branch> --deny-self-hosted-runners
```

也可传 `--bundle candidate/attestation.sigstore.json` 验证已保存的证明。CLI 的身份约束见[官方说明](https://cli.github.com/manual/gh_attestation_verify)。摘要相同不代替 Android 签名校验。

GitHub 手动入口通常要求工作流已在默认分支注册。本任务分支为两个工作流保留各自仅匹配本文件的窄 push 引导：正式构建只检查契约；晋升跳过所有 job，绝不签名或发布。注册后可用 CLI 指定授权分支手动执行。合入 main 仍需单独授权；不能为了显示手动按钮擅自改默认分支或合并。

## 同一 APK 按需晋升

[A14 Promote Release](../.github/workflows/a14-promote-release.yml) 默认 `actions-only`，只验证已完成的正式候选。选择 run ID；不会下载普通 CI 产物、过期产物或失败构建，也不会自动重建、重签或修改 APK。原始 run、workflow、source SHA、版本、Android 签名、SHA-256 与 GitHub Attestation 全部重新核验。验证任务将原始 attempt、不可变 artifact ID、source SHA 与 APK 摘要传给发布任务；期间若运行被重跑或产物被替换，明确失败，不能切换候选。

实际发布必须单独获得用户授权，再明确选择 `source` 或 `both`，并输入 `publish <versionName> from <run-id>`。Agent 触发构建不构成发布授权。例如以下是**发布操作**，不得当作构建命令使用：

```sh
gh workflow run a14-promote-release.yml --ref main -f build_run_id=<run-id> -f destination=source -f 'authorization=publish <versionName> from <run-id>'
```

发布任务使用单独的 `release-publishing` Environment；建议按维护方式增加必需审核人。源码发布仅使用本仓库 `GITHUB_TOKEN` 的 contents:write。`both` 另外需要该 Environment 中现有的 `A14_XPOSED_RELEASE_TOKEN`，必须只授予现有下载仓库 contents:write；未配置或权限不足明确失败，不申请或生成替代凭据。会先核对全部目标及权限，再执行写入。

两个 tag 分别为 `<versionName>` 与 `<versionCode>-<versionName>`，均指向候选完整 source SHA；下载仓库仅推送相应 source tag，不改变 metadata main。已有 Release 拒绝覆盖；已有同名 tag 必须已指向同一提交。发布后读取唯一 APK asset 的 GitHub digest / size 与候选比较。禁止移动历史 tag、删除旧版本或使用过期候选的重建文件。版本未递增的试用候选可以保留在 Actions，但不能覆盖已有同名正式版。
