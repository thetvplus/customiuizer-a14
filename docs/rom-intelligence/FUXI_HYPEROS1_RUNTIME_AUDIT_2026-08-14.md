# fuxi / HyperOS 1 实机知识与兼容审计（2026-08-14）

## 设备基线

- 设备：Xiaomi 13（`fuxi` / `2211133G`），1080 × 2400。
- ROM：HyperOS `V816.0.7.0.UMCTWXM`，Android 14 / SDK 34。
- 状态栏运行时 frame：`[0,0][1080,129]`。
- SystemUI 目标：`com.android.systemui.toast.MIUIStrongToast` 与
  `MIUIStrongToastControl`。本页结论不外推到 Android 13、HyperOS 2/3 或其它 OEM。

## StrongToast / 灵动岛

目标 ROM 的 `ll_strong_toast` 是固定高度 `LinearLayout`，消息胶囊
`cl_strong_toast_msg` 是其子 View，另有 `strong_toast_bottom_view` 兄弟 View。
底部动画若直接对胶囊或窗口使用 `translationY` / `scaleY`，渲染范围会在窗口和父
容器的旧裁剪边界之间移动，表现为入场前约 30% 胶囊顶部缺失。

当前实现保持底部宿主总高度不变，在父容器内部将等量空间从 bottom padding 转移到
top padding。正偏移让完整胶囊位于更下方，偏移归零即从下向上进入；退出和手势则增加
正偏移，使其向下离场。父容器、胶囊和祖先不做纵向缩放，胶囊不依赖 outline clip。

实机 60 fps 逐帧样本：首个可见帧约 `top=2098, bottom=2236`，随后上下边同步移动到
`top=2065, bottom=2205`；可见高度仅有抗锯齿阈值导致的 1–2 px 变化，没有顶部锁死或
高度坍缩。底部向下滑动后 StrongToast 窗口消失；顶部模式保持向上关闭。中心弹出和
纵向弹出不是重复选项：前者还包含宿主水平扩张，后者只做纵向运动。

锁屏兼容只在一次原生 StrongToast 同步调用期间临时绕过
`KeyguardStateControllerImpl.mShowing`，并在 `finally` 恢复；其它原生资格、事件来源和
调用次数不变。连续事件复用同一 View 时只提供一次轻量刷新反馈，不创造新的触发源。

## HyperOS 更新提醒

本机 Updater 是系统包 `com.android.updater`。Thanox 停用服务后，设置页仍可显示更新，
因为 `Settings.Global.miui_new_version` 保留了 `OS1.0.8.0.UMCTWXM`；
`miui_update_ready` 已是 `0`。一次性清理通过受签名权限与发送者身份双重约束的
system_server bridge 执行：请求清除 Updater 用户数据，并清除上述版本缓存。实机执行后
`miui_new_version=null`、`miui_update_ready=0`，Updater 仍保持 stopped；不会重新启用、
下载或安装更新。

## `com.miui.daemon`（系统质量服务）

包属性为 system、shared UID 1000、`persistent=true`。其声明组件显示它承担：

- 性能与系统调优：`MiuiPerfService`、`SysoptService`、`GcBoosterService`、
  `MemCompactService`、`DefragService`、`PerfTurboProvider`；
- 质量采集/上报：MQS provider、事件/文件/心跳上报、内存泄漏分析；
- 诊断：Atrace、图形、ION、meminfo、碎片 dump；
- 故障救援：`BrokenScreenRescueService`。

因此只停用若干组件不能阻止 persistent 主进程继续存在。实机 `services.jar` 进一步确认：
`ActivityManagerService.startPersistentApps()` 仍把它交给 `addAppLocked()`，后者建立
`ProcessRecord`、设置 persistent OOM 优先级并启动进程；调用方明确允许空返回。

新开关保存应用原状态、写入 `COMPONENT_ENABLED_STATE_DISABLED_USER`，再由 system_server
结束属于此包名的现有进程；任一步异常会恢复原应用状态。后续 persistent 冷启动只在包名
精确为 `com.miui.daemon` 且应用仍为 `DISABLED_USER` 时于 `addAppLocked()` 返回空，不轮询、
不周期杀进程，也不影响同为 UID 1000 的其它进程。恢复开关只还原保存的应用状态，不改
Thanox 已有的逐组件状态。停用可能失去性能调优、故障救援和厂商诊断能力，故默认关闭。

## 权限弹窗

Android 14 的原生授权页位于 PermissionController。本机使用
`com.google.android.permissioncontroller`，AOSP 设备通常使用
`com.android.permissioncontroller`，scope 与路由同时包含二者。目标方法经本机 APEX
APK 验证为
`com.android.permissioncontroller.permission.ui.GrantPermissionsActivity.onRequestInfoLoad(List)`；
原生空请求会调用 `setResultAndFinish()`。

策略仅在本次请求非空、且所有权限完全属于已启用类别时结束原生授权页：通知仅
`POST_NOTIFICATIONS`；定位仅 coarse/fine/background location。混合 CAMERA 等其它权限的
请求保持原生行为，授权状态不被修改。API 102 可在设置开关启用时请求两个
PermissionController 包的作用域；API 101 保持手动加 scope 的兼容路径，API 102 专属
类型隔离在独立文件。应用自行绘制的授权说明弹窗不是系统权限页，无法安全全局识别，
需要按应用适配。

## MIUI 遗留与性能复盘

- 已删除无调用链的旧 `TweakStrongToastHook`。它依赖 `mStrongToastBottomView`、`mRLLeft`
  与旧宽度资源，已从 UI/备份恢复中淘汰，继续保留只会增加错误 ROM 假设。
- 保留受限旧备份解码器：它是持久化兼容能力，有大小、类型和对象图上限，不属于 ROM
  兼容债务。
- 保留明确受控的 ABI fallback（状态栏/通知/电量等）：只在冷路径解析，运行热路径使用
  已发布 effect/state；没有证据支持为了代码整齐删除。
- 新维护功能只安装一个 system_server receiver，按用户点击做冷路径 Binder/PackageManager
  操作；权限策略只在精确 PermissionController 进程安装一个方法 Hook；StrongToast 只在
  事件生命周期创建有界 Animator，不轮询、不做磁盘 I/O、不静态持有 Activity/View。
- Hook ownership 扫描、feature semantics、完整单测、lint 和 assemble 是静态门禁；实机
  结论仅限本文明确列出的 fuxi 运行证据。

## 多设备边界

- 底部位置使用运行时 navigation/mandatory-gesture safe inset，并提供 0–80 dp 用户偏移；
  不硬编码 fuxi 的底部尺寸。
- PermissionController 同时支持 Google 与 AOSP 包名，目标类保持 AOSP namespace。
- StrongToast ROM 类或资源缺失时安装失败关闭，不猜测其它 OEM/新 HyperOS 的字段。
- Android 15/16、HyperOS 2/3 以及其它分辨率设备仍需独立实机证据。
