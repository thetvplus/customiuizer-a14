# 发布

## 版本

- `versionName` 形如 `r14.20.5`
- `versionCode` 必须单调增加
- LSPosed tag：`<versionCode>-<versionName>`
- 个人仓库 tag：`<versionName>`
- tag 必须指向构建该 APK 的 exact source SHA

`README.md`、`README_EN.md`、`CHANGELOG.md`、`CHANGELOG_CN.md` 与 Gradle `versionName` 必须一致。

## GitHub Actions

- Fast CI：push `main`、PR → `main`、`workflow_dispatch`
- Full CI：`workflow_dispatch`、每周、`r14.*` tag、main 提交含 `[full-ci]`

Actions 在 fresh Ubuntu runner 上从 clone 运行。禁止本机路径、本机 keystore、本机 JAVA_HOME。正式签名只在本地完成。

## 正式 APK

1. 从 exact main SHA clean build，`officialRelease=true`，`requireBuildRevision=true`，`buildRevision` 为该 SHA 前 8 位。
2. 检查 applicationId、versionName、versionCode、debuggable=false、V2 签名、signer、APK SHA-256。
3. signer 必须与上一正式版相同。
4. 已连接设备可 `adb install -r` 做 smoke，不要清用户数据。
5. 同一 APK binary 发布到个人仓库和 LSPosed 仓库。
6. `CHANGELOG`、`CHANGELOG_CN` 与 GitHub Release 说明只写版本变更与 SHA-256，不写赞赏或支持内容；支持方式仅在 `README.md` / `README_EN.md`。

## 密钥

keystore 与 `keystore.properties` 永远在仓库外。不要 commit，不要写入 Actions。
