# fuxi / HyperOS 1 AI Hook 知识库（2026-08-14）

本文是给没有实机的模型使用的入口。目标是回答三个问题：目标代码属于哪个
系统包、目标类/方法是否确实存在、证据是静态 APK 还是本轮实机运行时。

## 适用边界

- 设备：Xiaomi 13，`fuxi` / `2211133G`。
- ROM：`V816.0.7.0.UMCTWXM`，HyperOS `OS1.0`，Android 14 / API 34。
- fingerprint：`Xiaomi/fuxi_global/fuxi:14/UKQ1.230804.001/V816.0.7.0.UMCTWXM:user/release-keys`。
- 屏幕：1080 x 2400；physical density 440，override density 469。
- 结论不得外推至其他 ROM、HyperOS 2/3、Android 13/15/16。

## 证据等级

| 等级 | 含义 | 可用于什么结论 |
| --- | --- | --- |
| RUNTIME | 本轮 ADB 触发后由窗口、图层或 logcat 直接观察 | 证明当前设备确实经过该运行路径 |
| ARTIFACT | 从本机拉取的 APK/JAR 经哈希和 DEX 清单验证 | 证明类、字段、方法签名存在 |
| SOURCE | 当前项目源码引用或 Hook | 证明模块的意图，不单独证明 ROM ABI |
| CANDIDATE | 相邻类或高概率未来 Hook 点 | 仅供继续取证，禁止直接当作兼容合同 |

## 首要结论：侧边音量条 Hook 目标正确

音量窗口由宿主 `com.android.systemui` 创建，但具体实现类来自动态插件
`miui.systemui.plugin`。因此代码使用插件 `ClassLoader` 并 Hook
`com.android.systemui.miui.volume.*` 是正确边界；改 Hook 宿主 APK 的同名猜测类
反而会错。

| 层 | 已验证目标 | 证据 |
| --- | --- | --- |
| 窗口 | `MiuiVolumeDialogImpl`, type `VOLUME_OVERLAY` | RUNTIME：音量键后的 WindowManager/SurfaceFlinger |
| 面板入口 | `MiuiVolumeDialogImpl.showVolumeDialogH(I)` | RUNTIME 日志 + ARTIFACT 签名 |
| 两个快捷项容器 | `MiuiRingerModeLayout` | ARTIFACT |
| 快捷项实例 | `MiuiRingerModeLayout$RingerButtonHelper` | ARTIFACT |
| 角色字段 | `mIsZen:Z`；false=Mute，true=DND | ARTIFACT 构造链 + RUNTIME 两条状态日志 |
| 安全更新点 | `RingerButtonHelper.updateState()V` | ARTIFACT + RUNTIME |
| 当前项目 Hook | all constructors + `updateState` after | SOURCE |

当前实现使用构造器参数 2 的完整 root View，以 `mIsZen` 绑定角色，并把 root
保存为弱引用；隐藏时对 root 设置 `GONE`。这一点与本机 ABI 相符。颜色修改使用
`mStandardView`、`mBlurView`、`mIcon`，这些都是内部表现层，不被误当成完整 root。

插件中确认存在的相邻音量 ABI：

- `MiuiVolumeDialogImpl.computeTimeoutH()I`，字段 `mHovering:Z`、`mExpanded:Z`；
- `MiuiVolumeDialogImpl.showVolumeDialogH(I)`、`dismissH(I)`；
- `MiuiRingerModeLayout.setRingerModeByUser(Z)`、`setZenModeByUser(Z)`、
  `updateExpandedH(Z)`、`cleanUp()`；
- `RingerButtonHelper.setRingerMode(Z)`、`onExpanded(Z)`、`setIcon()`。

最后一组只是 CANDIDATE。除非功能需要且调用链再次取证，不要绕过原生点击流程
直接 Hook `set*ByUser`。

## 音量键实机采样摘要

一次 `KEYCODE_VOLUME_UP` 观察到：

```text
AudioService.adjustSuggestedStreamVolume(stream=USE_DEFAULT_STREAM_TYPE, flags=4116)
AudioService.adjustStreamVolume(stream=3, flags=4112)
MiuiVolumeDialogImpl.showVolumeDialogH(reason=1, activeStream=3)
RingerModeLayout.updateState(isZen=false, state=false)
RingerModeLayout.updateState(isZen=true, state=true)
MiuiVolumeDialogImpl.rescheduleTimeout(2000)
```

窗口归属 UID 1000，进程 PID 在本次采样中为 18240。PID 是瞬时值，不得写入
长期 Hook 逻辑。`uiautomator` 看不到非 focusable 的音量 overlay，不能靠 UI 树
定位这两个按钮。

## 已提取组件及用途

完整原件保存在 Git 忽略目录
`local-rom-samples/fuxi-V816.0.7.0-20260814/`，不提交厂商 APK/JAR。目录内的
`inventory.json` / `inventory.csv` 由 `tools/rom_inventory.py` 离线生成，共识别
10 个样本、118378 个类、691841 个方法、1054053 个字段。

| 原件 | 主要用途 | SHA-256 |
| --- | --- | --- |
| `MIUISystemUIPlugin.apk` | 音量面板、控制中心 | `3dafd9e068ebee7e88344ae1c7d146c7e2d41e79b5c52b7736cd3e58be0cc999` |
| `MiuiSystemUI.apk` | 状态栏、通知、锁屏、宿主插件 | `5d8f2fe0b65d8a1a947b4280f8053b524f8c5de73f48a74f8792d415ae76e513` |
| `MiuiHome.apk` | Launcher、手势、最近任务 | `a6546d51d9220039ed7ac143de249e9014202cfafeafd715237eb49f8f5a3f7b` |
| `Settings.apk` | 设置页、通知/Wi-Fi 入口 | `faec3546b79b1a99b18ae1f902c1baf5694fa80667d780a44cb4486bc7a9ea22` |
| `MIUISecurityCenterGlobal.apk` | 安全中心、应用管理、权限/网络入口 | `5e454601a0135fd4b4517b862a1652ebbc213c4899b316fc0433b251f6cfc55b` |
| `GooglePermissionController.apk` | Android 14 系统授权页 | `e91db9ba40f3002277ad7ab353342578686e6d04318805266a86ead5ca63845f` |
| `framework.jar` | framework 名义样本 | `8997e7a2d0ff62c024bf438539209ebb20a31977f3790d9a42884ebc1663a8af` |
| `services.jar` | system_server 名义样本 | `0223a12b5e1ab36828e3ae23af55210b5394ae90e0f9ca67291e7fcccd92d835` |
| `miui-framework.jar` | MIUI framework 名义样本 | `38d7a74c9bf9b7039d30d26c52a56e8dbe9443640bd6d222414ae8727cf97428` |
| `miui-services.jar` | MIUI system services 名义样本 | `be409f51114cfb676ab5c1210e77f566e4a0a228bcbd6d7d09d3c0ee913fd5e2` |

四个 JAR 在文件内没有可被当前离线解析器读取的 DEX 类；它们通常依赖设备的
boot/system_server ART 镜像。这里的哈希只证明取样文件身份，不证明项目中的
system_server 方法签名。后续若要冻结 system_server ABI，需要 root 可读的
对应 oat/vdex/art 或同版本完整 ROM payload。

## 项目相关组件优先级

以下类都同时满足“项目源码有引用”和“本机 APK DEX 中存在”。出现次数只用于
排序取证价值，不代表方法合同已经全部验证。

### P1：当前项目高频组件

- SystemUI plugin：`MiuiVolumeDialogImpl`、`MainPanelAnimController`、
  `StatusHeaderController`、`MainPanelContentDistributor`、`QSTileItemView`、
  `QSTileItemIconView`、`ControlCenterWindowViewImpl`。
- Host SystemUI：`StatusBarMobileView`、`MiuiPhoneStatusBarView`、
  `HeadsUpManager`、`MiuiNotificationPanelViewController`、`CentralSurfacesImpl`、
  `PhoneStatusBarView`、`NetworkSpeedController`、`MiuiClock`、
  `ExpandableNotificationRow`、`MiuiBatteryMeterView`。
- Launcher：`Launcher`、`DeviceConfig`、`GestureStubView`、`ItemIcon`、
  `ShortcutInfo`、`Workspace`、`NavStubView`、`RecentsContainer`。
- Security Center：`ApplicationsDetailsActivity`、`AppManageUtils`、
  `FirewallService`、`ShowAppDetailFragment`、`InterceptBaseFragment`。
- Permission Controller：
  `com.android.permissioncontroller.permission.ui.GrantPermissionsActivity`。

### P2：最值得继续补实机调用链

1. 音量面板展开/收起：围绕 `updateExpandedH`、`updateDialogWindowH`、
   `MiuiRingerModeLayout.updateExpandedH` 采样，保持原生动画和 timeout。
2. 控制中心窗口：`ControlCenterWindowViewImpl` 与主面板 controller 的 attach、
   configuration、detach 生命周期。
3. 状态栏：`MiuiPhoneStatusBarView`、`StatusBarMobileView` 的实际 inflate 和
   dark/tint 回调，避免仅靠资源名猜测。
4. Launcher：手势入口、最近任务容器及 `DeviceConfig` 的 ROM 分支。
5. 权限页：只验证系统 `GrantPermissionsActivity`，不要把应用自绘弹窗混入。

## 给后续模型的使用规则

1. 先查同目录的机器可读 JSON，再查本文件和
   `docs/audit/A14_VOLUME_MODE_SHORTCUT_IDENTITY.md`。
2. 类存在不等于方法被调用；ARTIFACT 证据与 RUNTIME 证据必须分开写。
3. 音量/控制中心类从 plugin ClassLoader 解析；宿主 SystemUI 类从宿主
   ClassLoader 解析，禁止混用。
4. 原始 APK/JAR 不提交、不上传；需要复核时按 SHA-256 找本地样本。
5. 找不到字段/方法时 fail-open，不用资源名或整数常量制造宽泛 fallback。
6. 本轮 `adb root` 返回 production build 拒绝，且 `su` 不可调用；因此没有采集
   `/data/adb`、LSPosed 私有日志或 ART 私有映像。不得把 shell 权限描述成 root。
