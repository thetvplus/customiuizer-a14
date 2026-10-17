# 发布

正式构建、push / merge main、tag 与发布以当前任务授权为准。debug / develop 的验证结果不等于正式发行。

## 版本与记录

- `versionName` 形如 `r14.22.7`，`versionCode` 单调增加。
- Gradle、双语 README、双语 CHANGELOG 与发行说明同步。
- 个人源码仓库 tag：`<versionName>`；下载仓库 tag：`<versionCode>-<versionName>`。
- 两仓库发布 tag 都绑定构建 APK 的 exact source SHA；下载仓库的 metadata main 独立维护，不要求与源码 main 同 SHA。
- 记录只写前一保留发行版到本版的最终变化，合并同系列内容时去重，排除调试交接与反复调整。发行说明写变化和 APK SHA-256，支持入口留在 README。

## 门禁与正式签名

先在最终候选提交运行 [完整本地门禁](DEVELOPMENT.md#日常验证)。GitHub Actions 的职责见 [TESTING.md](TESTING.md)：发布 tag 触发 Full CI，CI 使用 Ubuntu、JDK 25 与未签名 develop，不使用正式密钥。

签名 properties 和 keystore 在仓库外。通过 `-PcustomiuizerA14KeystoreProperties` 显式传入 properties 绝对路径，或使用 `CUSTOMIUIZER_A14_KEYSTORE_PROPERTIES` 环境变量；不要提交密钥、密码或本地路径配置。properties 包含 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`；路径建议用正斜杠，避免 Java Properties 反斜杠转义。相对路径以 Gradle 的 `app/` 项目目录解析。

Windows 预检：

```powershell
.\scripts\check-signing-config.ps1 -RequireSigning -KeystoreProperties "<external-absolute-path>/keystore.properties"
```

它只检查字段与文件存在，不验证密码或签名证书。普通开发无配置可跳过签名，`-RequireSigning` 无配置或无效配置必须失败。

在工作区干净、最终 main SHA 已确认后构建：

```powershell
$releaseRevision = git rev-parse --short=8 HEAD
.\gradlew.bat clean :app:assembleRelease :app:lintVitalRelease -PofficialRelease=true -PrequireBuildRevision=true "-PbuildRevision=$releaseRevision" "-PcustomiuizerA14KeystoreProperties=<external-absolute-path>/keystore.properties"
```

## APK 与双仓库验收

1. 检查 applicationId、SDK / ABI、versionName / versionCode、`debuggable=false` 与 build provenance 的 revision / buildType。
2. 检查 zipalign、V2 签名、证书指纹、文件大小与 SHA-256；签名证书必须与上一正式版一致。
3. 设备连接稳定时以 `adb install -r` 做 smoke，保留用户数据；明确记录验证 APK、ROM、已测功能与未测范围。
4. 同一个已验收 APK binary 发布到 [源码仓库](https://github.com/thetvplus/customiuizer-a14/releases) 和 [下载仓库](https://github.com/Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14/releases)。
5. 读回两端 tag、notes、`draft=false`、`prerelease=false`、latest 状态与唯一预期 APK asset；下载核对大小、SHA-256 和签名，不能只以“上传成功”收口。
6. 只有本版两端发布和资产验收完成后，才按明确授权移除被替代的 release / tag；保留其他发行版，重新核对列表、latest 与 changelog。发布清理不授权改写 Git 历史。

`tools/check_release_metadata.py --require-tag` 校验源码 tag 与版本元数据；`tools/verify_apk_provenance.py --help` 提供 APK provenance 检查参数。构建通过、ROM 样本与主机 benchmark 都不能替代当前正式 APK 的实机结论。
