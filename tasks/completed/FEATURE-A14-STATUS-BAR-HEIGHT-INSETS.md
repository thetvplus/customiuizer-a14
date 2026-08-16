# FEATURE-A14-STATUS-BAR-HEIGHT-INSETS

- Platform: A14
- Status: Done
- Priority: P0
- Owner: Devin
- Reviewer: ChatGPT / human
- Related version: A14
- Cross-repo task: no

## 目标

完整实现 A14 状态栏高度与 WindowInsets 的同步：

```text
用户配置的状态栏高度
= Android framework 状态栏资源高度
= SystemUI 实际状态栏高度
= WindowManager statusBars InsetsSource 高度
= 应用收到的 statusBars WindowInsets 高度
```

同时不伪造或错误修改 displayCutout、导航栏或其他 Insets 类型。

## 当前问题

- `system_statusbarheight` 已能修改 `status_bar_height*` 资源；
- SystemUI 状态栏视觉高度可能已变高；
- WindowManager 下发的 `WindowInsets.Type.statusBars()` InsetsSource 仍可能保持系统原始高度；
- 状态栏增高后，部分应用顶部被挤压、遮挡或布局拥挤。

## 允许修改

- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/StatusBarHeightConfig.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemStatusBarInsetsHooks.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/System.kt`（StatusBarHeightHook 改为使用共享配置）
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/FeatureIds.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt`
- `app/src/test/...` 下的偏好、路由与 Insets 几何/决策/resolver 测试
- `tasks/completed/` 中的完成记录

## 必须保持

- preference key `system_statusbarheight`、默认值 11、sentinel 11 -> disabled 语义；
- `ResourceHooks` 的 `ID`、`OBJECT`、fake resource、theme value replacement 能力；
- Hook 时序、参数改写和 `chain.proceed()` 次数；
- 不修改导航栏、IME、captionBar、displayCutout 等其他 Insets source；
- `MainModule.java`、`XposedHelpers.java`、`MemberUtilsX.java` 默认不改动；
- API 101/102 边界隔离。

## 实现要求

1. framework 资源边界：继续使用 `android` package-ready 路由的 `setThemeValueReplacement`，保持现有 `status_bar_height*` 资源替换；
2. system_server Insets 边界：Hook `android.view.InsetsSource.setFrame`，对 status bar 源按配置高度调整 `Rect.bottom`；
3. SystemUI View 边界：暂不新增 View Hook，除非实机证据证明资源替换无法驱动；
4. 配置高度在冷路径解析并缓存，热路径只读；
5. 默认或未启用时零额外开销；
6. ROM target 无法解析时记录 unsupported 日志并跳过，异常不逃出。

## 非目标

- 不重建通用资源 Hook 框架；
- 不顺便修改其他状态栏功能（图标、背景、通知等）；
- 不做按包名白名单的应用侧 Insets 修补；
- 不强求无需重启的即时 preference 切换。

## 验收标准

- [ ] 默认值下行为与原系统一致
- [ ] 自定义状态栏高度实际改变
- [ ] 应用收到的 `statusBars()` Insets 与实际状态栏高度一致
- [ ] `statusBarsIgnoringVisibility()` 与配置高度一致
- [ ] displayCutout 几何未被伪造或覆盖
- [ ] 横竖屏切换后高度正确
- [ ] 未修改导航栏或其他 InsetsSource
- [ ] 功能关闭时不安装额外 Hook
- [ ] 完整验证通过
- [ ] Debug APK 构建成功
- [ ] 没有未解释的无关改动

## 验证

```powershell
python tools/verify.py fast --changed
python tools/verify.py fast --tests StatusBarHeightConfigTest,StatusBarInsetsGeometryTest,StatusBarInsetsDecisionTest,StatusBarInsetsResolverTest
python tools/verify.py full
python -m compileall tools
python -m unittest discover -s tools/tests -p "test_*.py"
git diff --check
.\gradlew.bat :app:assembleDebug
```

## 构建产物

- `app/build/outputs/apk/debug/CustoMIUIzer-A14-r14.16.1-debug.apk`

## Closure timeline

| Revision | Engineering SHA | Description |
|---|---|---|
| Original base | `ebe0ba03` | 状态栏高度资源替换 + InsetsSource.setFrame 初始 Hook |
| R1 corrective closure | `08cfa116` | 修复 enabled 语义与几何；`originalTop + configuredPx`；安装/命中诊断 |
| R2 callback-boundary closure | `758b1c0f` | `SetFrameDecision` 拆分；`chain.proceed()` 只调用一次；Rect 输入不可变；type encoding 初版；诊断有界；`raw/resolved` 区分；新增 `StatusBarInsetsDecisionTest` |
| R3 ABI resolver closure | `54701f15` | `InsetsSourceAbi` + `selectTypeEncoding()`；纯 ABI 驱动 MODERN_PUBLIC / LEGACY_INTERNAL / UNSUPPORTED；诊断分桶（critical / rejection）；新增 `StatusBarInsetsResolverTest`；任务记录清理 |
| R4 resolver sentinel normalization closure | `50a0ad4f` | `RawTypeInfo` 可空；`safePublicType()`/`getStaticInt()` 失败返回 `null`；`selectTypeEncoding()` 拒绝负值 sentinel；扩展 `StatusBarInsetsResolverTest` 覆盖 sentinel/null 适配层 |

## 当前最终有效实现

- `system_statusbarheight` 默认值 `11` 为 sentinel，解析后 `27dp` 且 `enabled=false`；任何 `>= 12` 的值为 enabled。
- 配置高度解析为 `configuredDp`，再转换为 `configuredPx = round(configuredDp * density)`。
- Status bar source 几何调整：`newBottom = originalTop + configuredPx`。
- Rect 输入对象不被修改；`setFrame(Rect)` 和 `setFrame(int,int,int,int)` 均产生新的参数副本。
- `chain.proceed()` 在每次 `intercept()` 中只调用一次，由 `SetFrameDecision` 驱动。
- Insets type encoding 通过 `InsetsSourceAbi` 在安装时冻结：
  - `MODERN_PUBLIC`：检测到现代 `(int id, int type)` 构造函数、`getId()`、`getType()` 且 `WindowInsets.Type.statusBars()` 可解析。
  - `LEGACY_INTERNAL`：检测到 legacy `(int type)` 构造函数、无现代构造函数、`getType()` 且 `ITYPE_STATUS_BAR` / `ITYPE_NAVIGATION_BAR` 均可解析。
  - `UNSUPPORTED`：无法可靠确定 ABI 时完全放行。
- `InsetsTypeInfo` 不混用 public mask 与 internal index；缺失辅助 type 填 `-1`。
- 诊断日志分桶：
  - `criticalKeys`（上限 16）记录 `status-source-changed`、`status-source-no-change`、`preprocessing-reflection-failed`、`invalid-argument-shape`。
  - `rejectionKeys`（上限 16）聚合记录 `non-status-type`、`disabled`；使用不含 `sourceId` 的 key，避免非状态栏 source 挤占关键日志。
- 原方法普通异常和 fatal 异常均原样向上传播。
- resolver 中间结果使用可空 `Int?`：`RawTypeInfo`、`safePublicType()`、`getStaticInt()` 解析失败返回 `null`，仅在 `InsetsTypeInfo` 最终输出中用 `-1` 表示缺失的辅助 type。负值 sentinel 不会进入已安装 callback。

## R4 resolver sentinel normalization closure

### 修正范围
1. 将 resolver 中间结果统一为 nullable，失败返回 `null` 而不是 `-1`。
   - `RawTypeInfo.statusBarType`、`navigationType`、`displayCutoutType` 全部改为 `Int?`。
   - `safePublicType()` 与 `getStaticInt()` 失败时返回 `null`，普通异常仍先调用 `FatalErrors.unwrapAndRethrowIfFatal(t)`。
   - `resolveLegacyTypes()` 找不到 `InsetsState` 时返回 `RawTypeInfo(null, null, null)`。
2. `selectTypeEncoding()` 增加防御性校验，拒绝负值 sentinel：
   - Modern 要求 `publicStatusType >= 0`。
   - Legacy 要求 `legacyStatusType >= 0` 且 `legacyNavigationType >= 0`。
   - `publicStatusType = -1` 或 `legacyStatusType = -1` 或 `legacyNavigationType = -1` 必须导致 `UNSUPPORTED`。
3. 提取并测试解析结果规范化辅助函数（或直接让解析函数返回 nullable）。
4. 扩展 `StatusBarInsetsResolverTest`：
   - modern ABI + `publicStatusType = -1` → `UNSUPPORTED`
   - legacy ABI + `legacyStatusType = -1` → `UNSUPPORTED`
   - legacy ABI + `legacyNavigationType = -1` → `UNSUPPORTED`
   - modern ABI + `publicStatusType = null` → `UNSUPPORTED`
   - legacy ABI + status/nav 均为 `null` → `UNSUPPORTED`
   - modern status=1 / nav=2 / cutout=128 → `MODERN_PUBLIC`
   - legacy status=0 / nav=1 → `LEGACY_INTERNAL`
   - 生产解析适配层返回 nullable 的覆盖
5. 整理 closure timeline，记录 R4 engineering SHA。

## 测试

- `StatusBarHeightConfigTest`
- `StatusBarInsetsGeometryTest`
- `StatusBarInsetsDecisionTest`
- `StatusBarInsetsResolverTest`
- `SystemServerFeaturesWiringTest`

## 实机状态

`NOT DEVICE_VERIFIED`

R3 构建产物尚未在实机运行。实机验证前无法确认 ABI resolver 在 Xiaomi 13 / fuxi 上是否正确选择 MODERN_PUBLIC 或 LEGACY_INTERNAL。
