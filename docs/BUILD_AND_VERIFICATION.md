# A14 构建与验证

## 快速门禁

```powershell
python tools/verify.py fast --changed
```

## 针对性测试

```powershell
python tools/verify.py fast --tests <TestClassName>
```

## 完整门禁

```powershell
python tools/verify.py full
git diff --check
```

工具目录变更：

```powershell
python -m compileall tools
python -m unittest discover -s tools/tests -p "test_*.py"
```

## 构建策略

- 默认的静态、单元、lint 门禁不构建任何 APK。`verify.py` 只执行编译、单元测试与 lint。
- 最小安装验证使用 `develop` 变体，并必须启用仓库外 A14 专用签名配置（`officialRelease=true`）。未签名的 `develop` 构建仅用于 R8/shrinker/构建可复现性验证。
- 日常安装与正式候选包使用官方 Release 签名包。
- `debug` 变体仅用于显式授权的诊断场景；它不是 `develop`，不是 release candidate，也不得作为日常安装默认输出。仅在任务明确要求且授权后执行。

## Debug APK

仅在明确授权的诊断场景：

```powershell
.\gradlew.bat :app:assembleDebug
```

该 APK 是 diagnostic build，不用于安装、不替代 develop、不作为 release candidate。记录 APK 路径、大小、签名类型、SHA-256 和 Final SHA。

## Release

仅用户明确要求且仓库外 A14 专用签名配置有效时执行。禁止 Debug 冒充正式版，
禁止提交 APK、密钥、密码、令牌或本地签名配置。

## 证据等级

- `STATIC_VERIFIED`
- `BUILD_VERIFIED`
- `LOG_VERIFIED`
- `DEVICE_VERIFIED`
- `UNVERIFIED`

APK 构建、签名和静态测试不能替代目标 HyperOS ROM 实机验证。
