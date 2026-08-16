# ST1-E1 Target ROM Acquisition

## 1. Authority / Scope

- 阶段：ST1-E1（TARGET ROM ACQUISITION）。
- 目标：在分析 ROM geometry 之前，先冻结用于 StrongToast runtime testing 的 HyperOS 1 / Android 14 SystemUI 的真实身份与来源。
- 禁止：ST1-E1 不修改生产代码、测试、资源、Preference XML、feature 注册，不实施 Candidate 2 生产实验，不删除 legacy。
- 允许产出：`docs/strong-toast/ST1_E1_TARGET_ROM_ACQUISITION.md`。
- 基线：ST1 freeze `1affaedf61324b8c594a939a7aba59f5af30415e`。
- 参考平台：HyperOS 1 / Android 14 / SDK 34。

## 2. ROM Identity Freeze Requirement

在反编译任何 ROM 代码之前，必须先建立以下身份字段：

```text
DEVICE_MODEL
DEVICE_CODENAME
ANDROID_VERSION
HYPEROS_VERSION
BUILD_INCREMENTAL
BUILD_FINGERPRINT
SYSTEMUI_VERSION_CODE
SYSTEMUI_VERSION_NAME
SYSTEMUI_APK_PATH
```

证据必须来自：

1. 已连接的 target device；或
2. 精确匹配的官方 firmware package。

如果两者皆无：

```text
E1_RESULT = HOLD
E1_BLOCKER = EXACT_TARGET_ROM_NOT_AVAILABLE
```

## 3. E1-A — ROM Identity Status

| 字段 | 来源 | 值 |
| --- | --- | --- |
| `DEVICE_MODEL` | 无设备、无 firmware | `NOT_ACQUIRED` |
| `DEVICE_CODENAME` | 无设备、无 firmware | `NOT_ACQUIRED` |
| `ANDROID_VERSION` | 无设备、无 firmware | `NOT_ACQUIRED` |
| `HYPEROS_VERSION` | 无设备、无 firmware | `NOT_ACQUIRED` |
| `BUILD_INCREMENTAL` | 无设备、无 firmware | `NOT_ACQUIRED` |
| `BUILD_FINGERPRINT` | 无设备、无 firmware | `NOT_ACQUIRED` |
| `SYSTEMUI_VERSION_CODE` | 无 device pull / firmware 提取 | `NOT_ACQUIRED` |
| `SYSTEMUI_VERSION_NAME` | 无 device pull / firmware 提取 | `NOT_ACQUIRED` |
| `SYSTEMUI_APK_PATH` | 无 device / firmware | `NOT_ACQUIRED` |

结论：当前环境无法冻结任何 ROM 身份字段。

## 4. E1-B — Device Route

### 4.1 ADB availability check

执行命令：

```text
C:\Users\tv\Downloads\Peengeek\.tools\android-sdk\platform-tools\adb.exe devices
```

输出：

```text
List of devices attached

```

### 4.2 状态

- 没有 Android 设备通过 USB / 网络连接到当前环境。
- 因此无法执行 `adb shell getprop ro.product.device`、`ro.product.model`、`ro.build.version.release`、`ro.build.version.incremental`、`ro.build.fingerprint`。
- 无法执行 `adb shell pm path com.android.systemui` 以定位 SystemUI package。
- 无法 pull `SystemUI.apk`、odex、vdex 或 oat。

## 5. E1-C — Firmware Route

### 5.1 本地 firmware 搜索

| 搜索路径 | 搜索内容 | 结果 |
| --- | --- | --- |
| `C:\Users\tv\Downloads\Peengeek` | `*.zip`, `*.tgz`, `*.tar`, `*.img`, `*.ofp`, `*.br`, `*.apk`, `*.odex`, `*.vdex` | 仅有模块自身构建产物 `CustoMIUIzer-A14-r14.20.0-C6B1-RC.apk`、`release/r14.18.8/CustoMIUIzer-A14-r14.18.8.apk` 以及 Android SDK / JDK 压缩包；无官方 ROM firmware。 |
| `C:\Users\tv\Downloads` | 文件名包含 `SystemUI`、`Miui`、`hyperos`、`firmware`、`rom` | 仅命中模块源码/工具、文档、测试 fixture 与提示词文件；无官方 ROM firmware 或 SystemUI APK。 |
| `app/src/main/assets/test4.zip` | 解压检查 | 仅含 `truth.txt` 一个测试 fixture 文件；非 ROM。 |
| `LSPosed_log/` | 日志归档 | 仅有历史运行时日志 (`full.log`, `dmesg.log`, `modules_config.db`, `scopes.txt` 等)，不包含 `SystemUI.apk` 或可提取的 ROM 固件。 |

### 5.2 搜索说明

- 未找到 `.ofp`、`.tgz`、`.tar`、`.img`、`.br` 等官方 ROM 包。
- 未找到从 `/system/priv-app/MiuiSystemUI/` 或 `/system/system_ext/priv-app/MiuiSystemUI/` 提取的 `MiuiSystemUI.apk` / `SystemUI.apk` / odex / vdex。
- 项目自身 APK (`CustoMIUIzer-A14-r14.20.0-C6B1-RC.apk`) 是 Xposed 模块，不是 SystemUI，不能作为 ROM geometry evidence。

### 5.3 项目合约文件

`rom-contracts/hyperos1-a14-core.json` 是 hook target contract，描述模块希望 hook 的类/方法 smali 描述符。它**不包含任何 ROM 二进制或反编译代码**，也不能替代实际 SystemUI evidence。

## 6. E1-D — Decompile Attempt

由于缺少 E1-A 与 E1-B/C 的输入，无法执行以下步骤：

- 反编译 `com.android.systemui.toast.MIUIStrongToast`。
- 反编译 `com.android.systemui.toast.MIUIStrongToastControl`。
- 追踪 `getWindowParam()` / `showCustomStrongToast()`。
- 追踪 `strong_toast_height`、`strong_toast_width`、`strong_toast_width_window` 的真实 consumer。
- 识别 capsule root View、background、corner radius、animation、scaleX/scaleY、matrix、pivot。

## 7. E1-E — Evidence Artifact Rule

由于没有任何 ROM 证据被成功获取，`docs/strong-toast/evidence/<build-id>/` 目录当前为空，不提交任何文件。

计划中的证据 artifact 路径（待 ROM 获取后填充）：

```text
docs/strong-toast/evidence/<build-fingerprint>-
  ROM_IDENTITY.md
  HASHES.txt
  MIUIStrongToast_relevant.smali.txt
  MIUIStrongToastControl_relevant.smali.txt
  relevant_resource_refs.txt
```

当前不创建该目录，因为无真实 build-id 可用。

## 8. E1-F — Geometry Dependency Graph

因 ROM 未获取，所有 geometry 依赖仍冻结为 `UNKNOWN` / `NOT_PROVEN`，与 ST1 一致：

```text
STRONG_TOAST_HEIGHT_CONSUMERS = NOT_PROVEN
OUTER_WINDOW_GEOMETRY = UNKNOWN
CAPSULE_ROOT_GEOMETRY = UNKNOWN
BACKGROUND_GEOMETRY = UNKNOWN
CORNER_GEOMETRY = UNKNOWN
ANIMATION_GEOMETRY = UNKNOWN
```

没有任何 `YES` / `NO` / `INDIRECT` 结论，因为无 concrete ROM class/method/resource/smali evidence。

## 9. E1-G — Candidate 2 Status

- `PREFERRED_DIAGNOSTIC_EXPERIMENT = YES`：Candidate 2 仍是变量隔离的首选诊断实验。
- `PRODUCTION_FIX = NO`：ST1-E1 不实施 Candidate 2，也不实施任何 geometry fix。

## 10. Acquisition Triage with Log Analyzer

为确认现有 `LSPosed_log` 归档是否能提供 ROM 身份或 SystemUI path 信息，运行了：

```text
python tools/analyze_lsposed_log.py "LSPosed_log\r14\r14.16.1-debug\Vector-logs-release-20260805-205329\full.log" --profile a14 --repo-root . --output "build/log-analysis/st1-e1"
```

结果：

- 该工具是 LSPosed log triage 工具，用于分析崩溃、missing class、preference 等运行时问题。
- 输出目录 `build/log-analysis/st1-e1/` 被 `.gitignore` 的 `/build` 排除，不进入版本控制。
- 分析结果未包含 `ro.product.model`、`ro.build.fingerprint` 等 build 身份字段，也未包含 `MIUIStrongToast` / `strong_toast_height` 相关运行时证据。
- 因此该 log 不能替代 ROM acquisition。

## 11. Final Freeze

```text
E1_RESULT = HOLD
E1_BLOCKER = EXACT_TARGET_ROM_NOT_AVAILABLE

DEVICE_MODEL = NOT_ACQUIRED
DEVICE_CODENAME = NOT_ACQUIRED
ANDROID_VERSION = NOT_ACQUIRED
HYPEROS_VERSION = NOT_ACQUIRED
BUILD_INCREMENTAL = NOT_ACQUIRED
BUILD_FINGERPRINT = NOT_ACQUIRED
SYSTEMUI_VERSION_CODE = NOT_ACQUIRED
SYSTEMUI_VERSION_NAME = NOT_ACQUIRED
SYSTEMUI_APK_PATH = NOT_ACQUIRED

STRONG_TOAST_HEIGHT_CONSUMERS = NOT_PROVEN
OUTER_WINDOW_GEOMETRY = UNKNOWN
CAPSULE_ROOT_GEOMETRY = UNKNOWN
BACKGROUND_GEOMETRY = UNKNOWN
CORNER_GEOMETRY = UNKNOWN
ANIMATION_GEOMETRY = UNKNOWN

PREFERRED_DIAGNOSTIC_EXPERIMENT = YES
PRODUCTION_FIX = NO

ST2_AUTHORIZATION = NO
PRODUCTION_FIX_AUTHORIZATION = NO

PRODUCTION_CHANGE = NO
TEST_CHANGE = NO
TOOLS_CHANGE = NO
```

## 12. Next Step for ST1-E2

E1-E2 只能由以下任一事件解锁：

1. **Target device 连接**：`adb devices` 显示设备，可执行 `getprop` 与 `pm path` / `pull`。
2. **官方 firmware package 提供**：用户或 CI 提供与目标设备 build fingerprint 精确匹配的 `.zip` / `.tgz` / `.ofp` / `.img`。

在此之前 ST1-E1 保持 `HOLD`。

---

*ST1-E1 target ROM acquisition document; no production, test, or tool changes.*
