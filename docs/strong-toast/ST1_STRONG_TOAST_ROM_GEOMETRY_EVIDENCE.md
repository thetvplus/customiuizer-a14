# ST1 StrongToast ROM Geometry Evidence

## 1. Authority / Scope

- 阶段：ST1（EVIDENCE PHASE）。
- 目标：建立 `com.android.systemui.toast.MIUIStrongToast` / `MIUIStrongToastControl` 在 HyperOS 1 / Android 14 上的真实几何依赖链。
- 禁止：ST1 不修改生产代码、测试、资源、Preference XML、feature 注册，不实施 Candidate 2 生产实验，不删除 legacy。
- 允许产出：`docs/strong-toast/ST1_STRONG_TOAST_ROM_GEOMETRY_EVIDENCE.md`。
- 基线：ST0 freeze `b9e9361c3872397100f9b27560458c952eecc467`。
- 参考平台：HyperOS 1 / Android 14 / SDK 34。

## 2. Evidence Search Methodology

为获取目标 ROM geometry evidence，执行了以下搜索：

| 搜索范围 | 结果 |
| --- | --- |
| 当前仓库 `customiuizer-a14-forDevin` 全树（含 `app/`, `docs/`, `tools/`, `feature-semantics/`） | 无 `MIUIStrongToast` / `MIUIStrongToastControl` 反编译源码、smali、布局 XML、drawable。 |
| 相邻仓库 `customiuizer-a14-r14.15.3`、`a14-runtime-audit`、`customiuizer-a13-forDevin` | 无相关 ROM evidence。 |
| `C:\Users\tv\Downloads\Peengeek` 下的 ROM firmware / `.img` / `.odex` / `.vdex` / `.apk` 文件 | 仅发现模块自身构建产物 `CustoMIUIzer-A14-r14.20.0-C6B1-RC.apk` 与旧 release APK；无 `SystemUI.apk` 或 HyperOS firmware。 |
| `tools/tests/fixtures/rom-smali/` | 仅含 `Sample.smali` / `Other.smali` 等占位 fixture，不含 `MIUIStrongToast`。 |
| `docs/rom-intelligence/A14_PROCESS_MATRIX.md/.json/.csv` | 仅含 feature 路由矩阵，不含 ROM 类/方法实现。 |
| `feature-semantics/a14.json` | 含项目生成的 `strong_toast_height` 元数据（见第 3 节），但不是 ROM 反编译源码。 |
| `adb devices`（使用 `C:\Users\tv\Downloads\Peengeek\.tools\android-sdk\platform-tools\adb.exe`） | 无 device attached。无法 pull `/system/priv-app/MiuiSystemUI/MiuiSystemUI.apk` 或相关 odex/vdex。 |
| Web search：`MIUIStrongToast getWindowParam smali`、`"MIUIStrongToast" "getWindowParam"`、`strong_toast_height HyperOS SystemUI` | 未找到可下载或可引用的 HyperOS 1 A14 `com.android.systemui` 反编译 evidence。 |

结论：**当前工作区没有与 HyperOS 1 A14 匹配的 `SystemUI` 反编译产物，也没有可连接的实机供 pull/decompile。**

## 3. Repository Metadata Only

### 3.1 `feature-semantics/a14.json`

`feature-semantics/a14.json` 中关于 `strong_toast_height` 的项目元数据：

```json
{
  "featureId": "theme_com_android_systemui_dimen_strong_toast_height",
  "featureName": "strong_toast_height",
  "defaultValue": "48dp",
  "sourceFile": "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUIStrongToastHooks.kt",
  "installer": "SystemUIStrongToastHooks.installHeightMatch",
  "targetPackage": "com.android.systemui",
  "runtimeReadMode": "RESOURCE_REPLACEMENT",
  "enableEffect": "Matches the StrongToast content height to the configured status bar height",
  "disableEffect": "Keeps the ROM StrongToast content height",
  "confidence": "CODE_VERIFIED",
  "evidence": "setThemeValueReplacement in SystemUIStrongToastHooks.installHeightMatch",
  "notes": "Paired with the StrongToastView window LayoutParams height hook"
}
```

**证据性质声明**：
- 这是项目内部生成的语义矩阵，不是 ROM 源码或 smali。
- `defaultValue: 48dp` 可作为关于 ROM 默认值**来源不明的假设**，但不能作为 ST1 的 ROM 证明。
- `enableEffect` 中的 "StrongToast content height" 措辞暗示 `strong_toast_height` 被用于胶囊**内容高度**，但未指明是 root View height、background bounds、Window height 还是其它层。该措辞可能来自开发者观察，而非 ROM 代码分析。
- 该元数据不能替代 `MIUIStrongToast.getWindowParam()` / `showCustomStrongToast()` / layout XML / drawable 的实际反编译。

### 3.2 模块代码中的 ROM 目标字符串

模块生产代码仅包含目标 FQCN 与方法名字符串：

```kotlin
private const val STRONG_TOAST_CLASS = "com.android.systemui.toast.MIUIStrongToast"
private const val STRONG_TOAST_CONTROL_CLASS = "com.android.systemui.toast.MIUIStrongToastControl"
```

- `SystemUIStrongToastHooks.installHeightMatch` 调用了 `setThemeValueReplacement("com.android.systemui", "dimen", "strong_toast_height", statusBarHeightDp)` (`SystemUIStrongToastHooks.kt:44-49`)。
- `SystemUIStrongToastHooks.installHeightMatch` hook 了 `MIUIStrongToast.getWindowParam()` 并在 `after` 中设置 `WindowManager.LayoutParams.height` (`SystemUIStrongToastHooks.kt:51-71`)。
- `SystemUIStrongToastHooks.installHide` hook 了 `MIUIStrongToastControl.showCustomStrongToast()` (`SystemUIStrongToastHooks.kt:75-84`)。

这些字符串证明模块**意图 hook 的类/方法/资源名**，但不能证明 ROM 内部几何依赖。

### 3.3 旧 `TweakStrongToastHook` 中的历史字段名

`SystemUI.kt:128-152` 的 `TweakStrongToastHook`（当前 DEAD_LEGACY）引用了历史字段名：

- `mStrongToastBottomView`
- `mRLLeft`
- 资源 `strong_toast_width_window`
- 资源 `strong_toast_width`

这些是旧 ROM 版本的字段/资源名，**不能作为 HyperOS 1 A14 的证据**。ST1 明确将其排除在 A14 证明之外。

## 4. Target ROM Evidence — NOT FOUND

### 4.1 `MIUIStrongToast` class

| 需要的内容 | 状态 |
| --- | --- |
| 完整字段列表 | NOT FOUND |
| `getWindowParam()` 方法实现 | NOT FOUND |
| `showCustomStrongToast()` 方法实现 | NOT FOUND |
| 构造器 / 初始化方法 | NOT FOUND |
| 是否继承 View / FrameLayout / LinearLayout | NOT FOUND |
| 是否使用 `LayoutInflater` 及具体 layout XML | NOT FOUND |
| root View 的字段名与类型 | NOT FOUND |

### 4.2 `MIUIStrongToastControl` class

| 需要的内容 | 状态 |
| --- | --- |
| 完整字段列表 | NOT FOUND |
| `showCustomStrongToast()` 重载与参数 | NOT FOUND |
| 与 `MIUIStrongToast` 的调用关系 | NOT FOUND |
| 是否维护队列 / 单例 / 工厂 | NOT FOUND |

### 4.3 ROM resources

| 需要的内容 | 状态 |
| --- | --- |
| `com.android.systemui:dimen/strong_toast_height` 在 ROM 中的声明值（默认 48dp 仅来自项目元数据） | NOT PROVEN |
| `com.android.systemui:dimen/strong_toast_width` 声明 | NOT FOUND |
| `com.android.systemui:dimen/strong_toast_width_window` 声明 | NOT FOUND |
| 与 `MIUIStrongToast` 相关的 layout XML | NOT FOUND |
| 与 `MIUIStrongToast` 相关的 drawable / shape / nine-patch XML | NOT FOUND |
| 与 `MIUIStrongToast` 相关的 values / styles / themes | NOT FOUND |

### 4.4 ROM layouts / drawables / animations

| 需要的内容 | 状态 |
| --- | --- |
| 胶囊 root View 的 layout XML | NOT FOUND |
| 胶囊背景 drawable XML 或 nine-patch | NOT FOUND |
| `ViewOutlineProvider` / `clipToOutline` 配置 | NOT FOUND |
| enter / exit / morph animator XML 或代码 | NOT FOUND |
| 是否使用 `scaleX` / `scaleY` / `Matrix` / `pivot` | NOT FOUND |

## 5. Required Geometry Graph for `strong_toast_height`

依据 ST1 要求，对 `strong_toast_height` 分别冻结其真实 ROM geometry 层影响。

| 几何层 | `strong_toast_height` 影响 | 证据状态 | 说明 |
| --- | --- | --- | --- |
| OUTER_WINDOW_GEOMETRY | UNKNOWN | NOT PROVEN | 无法证明 ROM 的 `getWindowParam()` 是否从 `strong_toast_height` 读取 `LayoutParams.height`。模块 `after` hook 会覆盖 `lp.height`，但该行为与 `strong_toast_height` 无关。 |
| CAPSULE_ROOT_GEOMETRY | UNKNOWN | NOT PROVEN | 无 ROM layout / View hierarchy 证据。项目元数据 `a14.json` 中的 "StrongToast content height" 仅为来源不明的假设。 |
| BACKGROUND_GEOMETRY | UNKNOWN | NOT PROVEN | 无 ROM background drawable 或 bounds 代码。 |
| CORNER_GEOMETRY | UNKNOWN | NOT PROVEN | 无 ROM corner radius / outline / `clipToOutline` 代码。 |
| ANIMATION_GEOMETRY | UNKNOWN | NOT PROVEN | 无 ROM enter/exit animator 代码。无法区分 `scaleX/scaleY` 与 layout/drawable stretch。 |

**核心原则**：不得根据资源名或项目元数据推断 consumer。所有层均因缺少 ROM 源码而保持 `UNKNOWN`。

## 6. `getWindowParam()` Trace

### 6.1 需要证明的问题

- `MIUIStrongToast.getWindowParam()` 如何构造 `WindowManager.LayoutParams`。
- `LayoutParams.width`、`LayoutParams.height`、`gravity`、`x`、`y` 的具体来源。
- `strong_toast_height` 是否直接参与这些字段的计算。

### 6.2 状态

- `MIUIStrongToast.getWindowParam()` 实现：**NOT FOUND**。
- `WindowManager.LayoutParams` 各字段来源：**NOT PROVEN**。
- `strong_toast_height` 是否被 `getWindowParam()` 读取：**NOT PROVEN**。

模块代码在 `getWindowParam()` 的 `after` hook 中显式覆盖 `layoutParams.height = targetHeightPx`，因此可以确认模块对 OUTER_WINDOW 高度的最终控制。但 ROM 原本的 `getWindowParam()` 是否读取 `strong_toast_height` 无法确认。

## 7. Visual Capsule Trace

### 7.1 需要证明的问题

- 真正 visual capsule root 的 class / field / resource ID。
- parent 是什么。
- layout width / layout height 的来源。
- measured height source。
- background source。
- corner source。

### 7.2 状态

| 项 | 状态 |
| --- | --- |
| Visual capsule root class / field | UNKNOWN |
| Parent | UNKNOWN |
| `layout_width` source | UNKNOWN |
| `layout_height` source | UNKNOWN |
| `measuredHeight` source | UNKNOWN |
| Background source | UNKNOWN |
| Corner source | UNKNOWN |

无法区分 `WINDOW_HEIGHT` 与 `VISUAL_CAPSULE_HEIGHT`。

## 8. Animation Trace

### 8.1 需要证明的问题

视觉上的 "整体变大" 是否来自：

- `scaleX` / `scaleY`
- `Matrix` scaling
- layout stretch
- drawable stretch
- bounds change
- corner recomputation
- clip effect

### 8.2 状态

| 项 | 状态 |
| --- | --- |
| 真实 `scaleX` / `scaleY` 使用 | NOT PROVEN |
| `Matrix` / `Camera` / `pivot` 缩放 | NOT PROVEN |
| Layout stretch | NOT PROVEN |
| Drawable stretch | NOT PROVEN |
| Bounds change | NOT PROVEN |
| Corner recomputation | NOT PROVEN |
| Clip / outline 影响 | NOT PROVEN |

在没有 ROM animator / `ObjectAnimator` / `ValueAnimator` 代码或 runtime View dump 前，不能将视觉 scaling 直接称为 transform scaling。

## 9. Semantic Freeze Retention

保持 ST0 语义结论：

```text
NOTIFICATION_STRONG_TOAST = KEEP_SEPARATE_JUSTIFIED
```

- `DisableStrongToastFeature` 仍然保留；DND-only semantics 尚未迁移。
- `TweakStrongToastHook` / `system_notif_strong_toast_width` 继续记录为 `DEAD_LEGACY`；ST1 不删除。

## 10. Candidate 2 Diagnostic Justification

```text
CANDIDATE_2_DIAGNOSTIC_JUSTIFIED = YES
```

理由：
- ST0 已确认 MATCH 同时改动了 `strong_toast_height` ThemeResources 替换与 `getWindowParam().height` 覆盖。
- ST1 确认**没有 ROM 证据**能区分这两条路径对 CAPSULE_ROOT / BACKGROUND / CORNER / ANIMATION 的各自贡献。
- Candidate 2（移除 `strong_toast_height` 替换，仅保留 `getWindowParam().height` 覆盖）仍是变量隔离的最小诊断实验，用于判断资源替换是否造成 visual scaling。
- 但 Candidate 2 仍不是 production fix，也尚未在 ST1 实施。

## 11. What Would Be Needed for ST2

为在 ST2 中给出可信的 `MATCH_HEIGHT_FIX_DIRECTION`，必须补充以下至少一类证据：

1. **HyperOS 1 A14 设备的 `com.android.systemui` apk / odex / vdex / smali**：
   - `MIUIStrongToast.smali` / `MIUIStrongToastControl.smali`
   - 相关 layout XML / drawable XML / values XML
   - 相关 `getWindowParam()` / `showCustomStrongToast()` 反编译实现
2. **运行时 View dump**：
   - `SYSTEM_DEFAULT` vs `MATCH` 模式下 `WindowManager.LayoutParams`、`capsule root View`、`background`、`scaleX/scaleY`、`ViewOutline` 的实测差异。
3. **Candidate 2 实验结果**：
   - 移除 `strong_toast_height` 替换后单独测试 `getWindowParam().height` 覆盖，记录 visual scaling 是否消失。

## 12. ST1 Final Freeze

```text
ST1_RESULT = COMPLETE

STRONG_TOAST_HEIGHT_CONSUMERS = NOT_PROVEN

OUTER_WINDOW_ROOT_RELATION = NOT_PROVEN

CORNER_SOURCE = UNKNOWN

BACKGROUND_SOURCE = UNKNOWN

ANIMATION_SCALE_SOURCE = UNKNOWN

CANDIDATE_2_DIAGNOSTIC_JUSTIFIED = YES

MATCH_HEIGHT_ROOT_CAUSE = NOT_PROVEN

MATCH_HEIGHT_FIX_DIRECTION = NONE

ST2_AUTHORIZATION = NO

PRODUCTION_CHANGE = NO
TEST_CHANGE = NO
TOOLS_CHANGE = NO
```

### 12.1 结论说明

- **STRONG_TOAST_HEIGHT_CONSUMERS = NOT_PROVEN**：仓库中无 ROM 源码或 smali 可证明 `strong_toast_height` 被哪些 `MIUIStrongToast` / `MIUIStrongToastControl` 代码路径消费。
- **OUTER_WINDOW_ROOT_RELATION = NOT_PROVEN**：无法证明 `WindowManager.LayoutParams.height` 与 visual capsule root `LayoutParams.height` 之间的关系。
- **CORNER_SOURCE / BACKGROUND_SOURCE / ANIMATION_SCALE_SOURCE = UNKNOWN**：缺少 ROM drawable / animator 实现。
- **CANDIDATE_2_DIAGNOSTIC_JUSTIFIED = YES**：在缺少 ROM 证据的情况下，变量隔离仍是唯一可推进诊断的方向。
- **MATCH_HEIGHT_ROOT_CAUSE = NOT_PROVEN**：与 ST0 保持一致；ST1 没有找到足以升级 `NOT_PROVEN` 的 ROM 证据。
- **MATCH_HEIGHT_FIX_DIRECTION = NONE**：在缺少 ROM geometry dependency 前不能选择具体修复实现。

### 12.2 最小 Blocker

1. `HYPEROS1_A14_SYSTEMUI_DECOMPILE_NOT_AVAILABLE`：缺少 `com.android.systemui` 反编译产物。
2. `RUNTIME_VIEW_DUMP_NOT_PERFORMED`：没有设备可采集 `MATCH` 与 `SYSTEM_DEFAULT` 的 View/Window 实测差异。
3. `CANDIDATE_2_EXPERIMENT_NOT_EXECUTED`：未在受控环境下单独移除 `strong_toast_height` 替换进行诊断。

---

*ST1 evidence document; no production, test, or tool changes.*
