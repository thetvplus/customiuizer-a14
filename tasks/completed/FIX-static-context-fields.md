# FIX-static-context-fields

- Platform: A14
- Status: Done
- Priority: P2
- Owner: Devin
- Reviewer: ChatGPT / human
- Related version: optional
- Cross-repo task: optional

## 目标

处理 A14 `lintDebug` 报告的 7 处 `StaticFieldLeak` 命中（`Controls.kt`、
`DeviceInfoMonitor.kt`、`StepCounterController.kt`、`WeatherDataController.kt`），
在保留现有所有权和释放闭环的前提下消除 Lint 警告，不产生行为回归。

## 当前问题

`app/build/reports/lint-results-debug.xml` 中 `StaticFieldLeak` 命中 7 处：

| 文件 | 字段/位置 | 实际情况 |
|---|---|---|
| `Controls.kt:43` | `basePWMContext` | `PhoneWindowManager.mContext`，`system_server` 进程内单例；已有字段级 `@SuppressLint`；Lint 额外对 `object Controls` 类声明报错 |
| `DeviceInfoMonitor.kt:43` | `activeContext` | SystemUI 状态栏 monitor context；已用 `applicationContext`/`startScreenReceiverLocked`/`stopScreenReceiverLocked`/`destroy` 形成完整生命周期闭环 |
| `DeviceInfoMonitor.kt:114` | `activeContext` | 字段声明处 |
| `StepCounterController.kt:24` | `context` | 已使用 `context.applicationContext`，重新初始化时取消旧 scope、反注册旧 receiver；`stepViewList` 用 `WeakReference<TextView>` |
| `StepCounterController.kt:40` | `context` | 字段声明处 |
| `WeatherDataController.kt:21` | `context` | 已使用 `applicationContext`，重新初始化时取消旧 scope、反注册旧 receiver；`updateTarget` 用 `WeakReference` |
| `WeatherDataController.kt:41` | `context` | 字段声明处 |

所有命中均不是未管理的 Activity/View 强引用，而是`applicationContext`/system
process 单例 Context，并有明确的替换/反注册/释放闭环。Android Lint 无法识别这
些所有权语义，因此对 `object` 声明补充 `@SuppressLint("StaticFieldLeak")` 与说明
注释，而非改动运行时行为。

## 允许修改

- `app/src/main/java/tv/withaibuild/customiuizer/mods/Controls.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/DeviceInfoMonitor.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/StepCounterController.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/WeatherDataController.kt`
- `docs/JAVA_BOUNDARY_ALLOWLIST.md`（因 doc v2 迁移被误删，导致 `InstallerJvmAbiTest` 失败，按 v2 架构风格重建）

## 必须保持

- 不引入新的静态 Activity/View 强引用；
- 不改动现有 `applicationContext` 获取、scope 取消、receiver 反注册、弱引用持有
  等释放闭环；
- Hook 时序、参数语义、回调次数不变；
- `OutOfMemoryError` 继续抛出。

## 实现要求

1. `Controls.kt`：在 `object Controls` 声明上补充 `@SuppressLint("StaticFieldLeak")`
   与注释，说明 `basePWMContext` 是 `PhoneWindowManager.mContext`（system_server
   单例）且仅初始化一次。
2. `DeviceInfoMonitor.kt`：在 `object DeviceInfoMonitor` 声明上补充
   `@SuppressLint("StaticFieldLeak")` 与注释，说明 `activeContext` 为
   `applicationContext`，有 `startScreenReceiverLocked`/`stopScreenReceiverLocked`
   的所有权闭环。
3. `StepCounterController.kt`：在 `object StepCounterController` 声明上补充
   `@SuppressLint("StaticFieldLeak")` 与注释，说明使用 `applicationContext`，
   并已有 receiver 反注册 / scope 取消 / `WeakReference` View 列表。
4. `WeatherDataController.kt`：在 `object WeatherDataController` 声明上补充
   `@SuppressLint("StaticFieldLeak")` 与注释，说明使用 `applicationContext`，
   并已有 receiver 反注册 / scope 取消 / `WeakReference` 目标。

## 非目标

- 不重构 Context 持有方式；
- 不处理其它 Lint 类别；
- 不跨版本回移到 A13。

## 验收标准

- [x] `app/build/reports/lint-results-debug.xml` 中 `StaticFieldLeak` 命中数为 0
- [x] `compileDebugKotlin`/`compileDebugJavaWithJavac` 通过
- [x] `testDebugUnitTest` 通过
- [x] `check-invariants.py` 无新增 violation
- [x] 修复 `testDebugUnitTest` 因 doc v2 迁移误删 `docs/JAVA_BOUNDARY_ALLOWLIST.md` 导致的 `InstallerJvmAbiTest` 失败（该文件属于长期 Java 边界记录，已按 v2 架构风格重建）
- [x] 最终 diff 已审查
- [x] 工作区没有未解释改动

## 验证

```powershell
.\gradlew.bat :app:lintDebug :app:compileDebugKotlin :app:compileDebugJavaWithJavac :app:testDebugUnitTest
python tools/check-invariants.py
git diff --check
```

## 构建产物

未要求 APK。

## 完成记录

- Base SHA: fb205a0d
- Final SHA: （收口 commit）
- Commits: 1
- Behavior changed: 否，仅补充说明性 lint 抑制
- Verification: lintDebug / compileDebugKotlin / compileDebugJavaWithJavac / testDebugUnitTest / check-invariants.py
- Device evidence: 无（STATIC_VERIFIED）
- Known limits: 无
