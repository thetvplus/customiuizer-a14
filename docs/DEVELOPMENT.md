# 开发

从 `main` 的 exact SHA 建工作分支。不要直接在 `main` 上做功能实验。

## 日常

```powershell
python tools/verify.py fast --changed
```

针对性测试：

```powershell
python tools/verify.py fast --tests <TestClassName>
```

## 收口

```powershell
python tools/verify.py full
python -m unittest discover -s tools/tests -p "test_*.py"
git diff --check
```

`verify.py` 会检查 JDK 25、静态规则、EOL、observer 契约、hook-body PrefMap 上限、热路径分配预算、源码清洁度、不变量和 feature semantics，然后编译并跑 Android 单元测试与 lint。它不构建正式 APK。

优化相关门禁详见 [docs/OPTIMIZATION_GATES.md](OPTIMIZATION_GATES.md)。

## 分支整合

- 需要整合多条工作分支时，从最新 `origin/main` 新建整合分支。
- 逐分支合并并保留可追溯提交信息，遇到冲突必须在整合分支修复并重新跑“收口”门禁。
- 仅在整合分支完整通过 `full verify + Python tools tests + git diff --check` 后，才允许清理旧分支。

## 构建变体

- `debug`：仅显式诊断。
- `develop`：CI / R8 / 可复现性。
- `release`：用户明确要求且仓库外正式签名配置有效时才构建。

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleDevelop
```

正式包：

```powershell
.\gradlew.bat clean :app:assembleRelease -PofficialRelease=true -PrequireBuildRevision=true -PbuildRevision=<HEAD前8位>
```

签名材料在仓库外。不要把 keystore、密码或 APK 提交进 Git。
