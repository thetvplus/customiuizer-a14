# FIX-A14-BUILD-PROVENANCE-REVISION-STAMP

- Platform: A14
- Status: Done
- Priority: P0
- Owner: Devin
- Reviewer: ChatGPT / human
- Related version: A14
- Cross-repo task: no

## 目标

确保交付的 Debug APK 中的 `BUILD_REVISION`、BuildConfig 字段和构建报告中的 engineering SHA 完全一致，并可以自底向上验证 APK provenance。

## 根因

- `app/build.gradle.kts` 在 Gradle configuration 阶段直接执行 `git rev-parse --short=8 HEAD`。
- 实际工作流先在未提交工作区中构建，再提交 engineering/closure。
- `org.gradle.configuration-cache=true` 和 `org.gradle.caching=true` 使旧的 Git HEAD 没有作为受跟踪输入，导致 APK 打印的是构建时的父提交而不是 R4 engineering commit。

## 必须保持

- `system_statusbarheight` 等已有功能行为不变；
- 不修改 `MainModule.java`、`XposedHelpers.java`、`MemberUtilsX.java`；
- 不修改状态栏高度、Dark tint、锁屏充电等运行时逻辑；
- `.self-eval-scores.jsonl` 和 `DEVIN_LOCAL_A14_SKILLS_V2/` 保持未跟踪，不提交。

## 实现要求

1. Gradle 显式接收 `-PbuildRevision=<8-char-sha>` 或环境变量 `CUSTOMIUIZER_BUILD_REVISION`。
2. 值必须匹配 `^[0-9a-fA-F]{8}$`。
3. 交付构建缺失或非法时失败，不得写入 `unknown`。
4. 新增 `tools/build_debug_apk.py` 受控构建入口，强制 `git diff --quiet` 和 `git diff --cached --quiet`。
5. 交付构建使用 `--no-configuration-cache`。
6. 增加 APK provenance 文件（`assets/build-provenance.properties` 或等价方式），内容与 `BuildConfig.BUILD_REVISION` 一致。
7. 新增 `tools/verify_apk_provenance.py` 验证 APK 内 provenance。
8. 增加工具测试覆盖：合法/非法 SHA、dirty worktree、revision A/B 不复用、provenance 一致性。

## 验证

```powershell
python tools/verify.py fast --changed
python -m compileall tools
python -m unittest discover -s tools/tests -p "test_*.py"
.\gradlew.bat :app:testDebugUnitTest
python tools/verify.py full
git diff --check
```

engineering commit 后再执行受控构建并验证 provenance。

## 结果

- Engineering SHA: `1c0e839c`
- APK embedded revision: `1c0e839c`
- APK SHA-256: `C9781A9DB0872A69FC2ECEC5C451BD828FD5A54A1F987BDC96088FD22690776E`
- 验证结果：
  - `python tools/verify.py full` PASS
  - `python -m compileall tools` PASS
  - `python -m unittest tools.tests.test_build_revision tools.tests.test_verify_apk_provenance` PASS
  - `tools/build_debug_apk.py` tracked worktree clean, built with `--no-configuration-cache -PbuildRevision=1c0e839c`, APK SHA-256 `C978...776E`
  - `tools/verify_apk_provenance.py --apk ... --expected-revision 1c0e839c` PASS
  - `BuildConfig.BUILD_REVISION = "1c0e839c"`
  - `assets/build-provenance.properties` contains `revision=1c0e839c`

## 实机状态

待实机日志确认，当前为 NOT DEVICE_VERIFIED / BUILD VERIFIED。
