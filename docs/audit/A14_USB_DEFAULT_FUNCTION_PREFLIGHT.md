# A14 USB 默认功能架构 Preflight

## Scope

P1-A3 在 P1-A2 基础上，使用本地 `OS1.0.8.0.UMCTWXM` fastboot 镜像作为 engineering proxy，完成 HyperOS 1 `fuxi` system_server USB 字节码审计，输出最终架构决定。P1-B 仍未授权。

## 设备与 ROM 上下文

- 设备：Xiaomi 13 (`fuxi`)
- 目标运行时：`HyperOS 1 OS1.0.7.0.UMCTWXM`（MIUI 版本命名 `V816.0.7.0.UMCTWXM`）
- 工程字节码基线：`HyperOS 1 OS1.0.8.0.UMCTWXM`，Android 14 / SDK 34
- 本地 ROM 路径：`C:\Home\xiaomi\rom\fuxi_tw_global_images_OS1.0.8.0.UMCTWXM_14.0`

正确区分：

```text
TARGET_RUNTIME = OS1.0.7.0.UMCTWXM
ENGINEERING_BYTECODE_BASE = OS1.0.8.0.UMCTWXM
```

OS1.0.8.0 是同一设备、同一 region、同 HyperOS 1 major、同 Android 14 的小版本更新，可作为工程 proxy，但不等于 `OS1.0.7.0` 的 exact bytecode。

## Local ROM 提取

过程：

1. `super.img` 为 Android sparse image，total blocks 2,359,296，raw 大小 9.00 GiB。
2. 用 Python sparse 转换脚本得到 `super.raw.img`。
3. 用 `liblp` `lpunpack` 提取 `system_a.img`。
4. `system_a.img` 为 EROFS，用 `extract.erofs` (Cygwin build) 提取 `system/framework/services.jar`。
5. 用 `apktool 2.9.3` 反编译 `services.jar` 得到 smali（`services_smali`）。

USB 实际类容器：

```text
USB CLASS CONTAINER = system/framework/services.jar (classes3.dex)
USB CLASS CONTAINER PATH IN ROM = system_a/system/framework/services.jar
LOCAL EXTRACTED PATH = C:\Users\tv\AppData\Local\Temp\erofs_extract\system_a\system\framework\services.jar
CONTAINER SHA256 = 9341C22BF12F98D12607AF3B93EF7C597931309A32E388C3BDC394854840DB75
USB DEX = classes3.dex
DEX SHA256 = 6DB77BF1A5FABA4A0ED18D8241D73904637D257806F3FD842A954E28FC6C650A
```

没有 commit 任何 Xiaomi proprietary artifact。

## AOSP Android 14 默认 USB 调用链

### Settings → UsbBackend → UsbManager

```text
UsbDefaultFragment
→ UsbBackend.getDefaultUsbFunctions()
→ UsbManager.getScreenUnlockedFunctions()

UsbDefaultFragment.setDefaultKey(key)
→ UsbBackend.setDefaultUsbFunctions(functions)
→ UsbManager.setScreenUnlockedFunctions(functions)
```

### UsbManager → IUsbManager → UsbService

- `UsbService.setScreenUnlockedFunctions(long)` 校验 `MANAGE_USB` 与 `areSettableFunctions` 后交给 `UsbDeviceManager`。
- `UsbService.setCurrentFunctions(long, int)` 用于临时 current function，不走 default persistence。

### UsbService → UsbDeviceManager → UsbHandler

- `UsbDeviceManager.setScreenUnlockedFunctions(long)` 发送 `MSG_SET_SCREEN_UNLOCKED_FUNCTIONS` 给 `UsbHandler`。
- `UsbHandler` 处理屏幕解锁、USB 断开、boot、用户切换、用户限制等事件，最终调用 `setEnabledFunctions(...)`。

## HyperOS 1.0.8.0 字节码证据

### 类容器

```text
com/android/server/usb/UsbDeviceManager
com/android/server/usb/UsbDeviceManager$UsbHandler
com/android/server/usb/UsbDeviceManager$UsbHandlerHal
com/android/server/usb/UsbDeviceManager$UsbHandlerLegacy
```

`services.jar/classes3.dex` 中出现 `Lcom/android/server/usb/UsbDeviceManager;`、`Lcom/android/server/usb/UsbDeviceManager$UsbHandler;` 各 1 次，`UsbHandler` 出现 16 次，`UsbDeviceManager` 出现 17 次。Xiaomi 在该包中增加了 `MiuiUsbServiceStub` 等少量 vendor 类型，但 `UsbDeviceManager` 主类结构完整。

### 关键字段

```text
com.android.server.usb.UsbDeviceManager$UsbHandler.mScreenUnlockedFunctions : J
com.android.server.usb.UsbDeviceManager$UsbHandler.mCurrentFunctions : J
com.android.server.usb.UsbDeviceManager$UsbHandler.mCurrentUser : I
com.android.server.usb.UsbDeviceManager$UsbHandler.mScreenLocked : Z
com.android.server.usb.UsbDeviceManager$UsbHandler.mSettings : Landroid/content/SharedPreferences;
com.android.server.usb.UsbDeviceManager$UsbHandler.mConnected : Z
com.android.server.usb.UsbDeviceManager$UsbHandler.mConfigured : Z
com.android.server.usb.UsbDeviceManager$UsbHandler.mCurrentFunctionsApplied : Z
```

### 关键 message 常量

从 `UsbDeviceManager.smali` 静态字段确认：

```text
MSG_UPDATE_STATE             = 0x0
MSG_SET_CURRENT_FUNCTIONS    = 0x2
MSG_SYSTEM_READY             = 0x3
MSG_BOOT_COMPLETED           = 0x4
MSG_USER_SWITCHED            = 0x5
MSG_UPDATE_USER_RESTRICTIONS = 0x6
MSG_SET_SCREEN_UNLOCKED_FUNCTIONS = 0xc
MSG_UPDATE_SCREEN_LOCK       = 0xd
```

实现时应通过反射/字段发现获取这些值，不应硬编码。

### 关键方法

1. `UsbDeviceManager$UsbHandler.setScreenUnlockedFunctions(I)V`

```smali
.method private setScreenUnlockedFunctions(I)V
    iget-wide v0, p0, mScreenUnlockedFunctions
    const/4 v2, 0x0
    invoke-virtual {p0, v0, v1, v2, p1}, setEnabledFunctions(JZI)V
    return-void
.end method
```

2. `UsbHandler.getAppliedFunctions(J)J`

```smali
if-eqz p1
    invoke-virtual {p0}, getChargingFunctions()J
    return
invoke-virtual {p0}, isAdbEnabled()Z
if-eqz ...
    const-wide/16 v0, 0x1
    or-long/2addr v0, p1
    return
return p1
```

3. `UsbHandler.getChargingFunctions()J`

```smali
if isAdbEnabled
    return 1     (FUNCTION_ADB)
else
    return 4     (FUNCTION_MTP)
```

4. `UsbHandlerHal.setEnabledFunctions(JZI)V`

- 接收 `functions`、`forceRestart`、`operationId`。
- 若 `functions == 0` 则标记 `chargingFunctions = true`。
- 调用 `getAppliedFunctions` 得到实际 gadget 函数，再调 `setUsbConfig`。
- 设置 `mCurrentFunctions = functions`。

5. `UsbDeviceManager$UsbHandler.handleMessage(Landroid/os/Message;)V`

`packed-switch` 按消息 ID 分发，default-application 分支：

- `0x0 MSG_UPDATE_STATE`：断开后若 `!mScreenLocked && mScreenUnlockedFunctions != 0` 调 `setScreenUnlockedFunctions`；否则调 `setEnabledFunctions(0, ...)`。
- `0xc MSG_SET_SCREEN_UNLOCKED_FUNCTIONS`：设置 `mScreenUnlockedFunctions = msg.obj`，persist `mSettings`，然后若 `!mScreenLocked && mScreenUnlockedFunctions != 0` 调 `setScreenUnlockedFunctions`；否则调 `setEnabledFunctions(0, ...)`。
- `0xd MSG_UPDATE_SCREEN_LOCK`：设置 `mScreenLocked`；锁定且无连接时 `setEnabledFunctions(0, ...)`；解锁且 `mScreenUnlockedFunctions != 0 && mCurrentFunctions == 0` 调 `setScreenUnlockedFunctions`；解锁且 `mScreenUnlockedFunctions == 0 && mCurrentFunctions == 0` 进入 `cond_27` 不调用。
- `0x4 MSG_BOOT_COMPLETED` / `0x3 MSG_SYSTEM_READY`：调 `finishBoot(I)`。
- `finishBoot(I)`：若 `!mScreenLocked && mScreenUnlockedFunctions != 0` 调 `setScreenUnlockedFunctions`；否则调 `setEnabledFunctions(0, ...)`。

非 default-application 分支：

- `0x2 MSG_SET_CURRENT_FUNCTIONS`：直接 `setEnabledFunctions(functions, false, operationId)` — manual current function 路径。
- `0x6 MSG_UPDATE_USER_RESTRICTIONS`：若数据受限则 `setEnabledFunctions(0, true, operationId)`。
- `0x5 MSG_USER_SWITCHED`：重置 `mScreenUnlockedFunctions=0`、`mScreenLocked=true`，然后 `setEnabledFunctions(0, ...)`。
- `0x1 MSG_ENABLE_ADB`：调 `setAdbEnabled`，不直接改 function。

### ADB 组合

- `getAppliedFunctions` 自动把 `FUNCTION_ADB`（`0x1`）OR 进非零 function。
- `getChargingFunctions` 在 ADB 启用时返回 `FUNCTION_ADB`，否则 `FUNCTION_MTP`。
- 模块不手动拼 ADB。

## AOSP vs OS1.0.8.0 比较

| 项目 | AOSP `android-14.0.0_r1` | HyperOS 1.0.8.0 fuxi | 差异 |
|------|--------------------------|----------------------|------|
| `UNLOCKED_CONFIG_PREF` | `usb-screen-unlocked-config-%d` | 相同 | 无 |
| `mScreenUnlockedFunctions` 字段 | `J` | 相同 | 无 |
| `mSettings` per-user | `SharedPreferences` | 相同 | 无 |
| `getScreenUnlockedFunctions` | 返回 `mScreenUnlockedFunctions` | 相同 | 无 |
| `setScreenUnlockedFunctions(long)` | 发送 `MSG_SET_SCREEN_UNLOCKED_FUNCTIONS` | 相同 | 无 |
| `handleMessage` 分支结构 | 相同消息集 | 相同，包含新增 `MSG_UPDATE_USB_SPEED` 等 | 小版本增加 |
| `setEnabledFunctions` 参数 | `(JZI)V` | 相同 | 无 |
| `getAppliedFunctions` | `0 → getChargingFunctions` / ADB OR | 相同 | 无 |
| `getChargingFunctions` | ADB 返回 `1` / 否则 `4` | 相同 | 无 |
| `UsbHandlerHal` / `UsbHandlerLegacy` | 存在 | 存在 | 无 |
| USB state 广播更新 | `updateUsbStateBroadcastIfNeeded` | 相同 | 无 |
| Vendor 增加 | 无 | `MiuiUsbServiceStub` 等 | 小版本 |

```text
OS1.0.8_VS_AOSP_USB_CHAIN = MINOR_VENDOR_VARIATION
```

核心状态机、`mScreenUnlockedFunctions` 语义、`setEnabledFunctions` 边界、ADB 组合均与 AOSP 一致。

## OS1.0.7.0 实现兼容性

```text
OS1_0_7_IMPLEMENTATION_CONFIDENCE = HIGH
```

依据：

- 同 fuxi 设备。
- 同 Taiwan / HyperOS 1 / Android 14 / API 34。
- AOSP USB 链结构未被 Xiaomi 大幅改写。
- OS1.0.8.0 与 AOSP `android-14.0.0_r1` 在 USB 状态机上为 `MINOR_VENDOR_VARIATION`。
- 未发现 OS1.0.8.0 中新增 vendor-specific USB architecture。
- 实现采用签名/结构发现 + fail-open，不依赖 exact build offset。

但：

```text
TARGET_EXACT_BYTECODE = NOT_AUDITED
```

`OS1.0.7.0` 未在本地解包，P1-B 应先在实机/目标 build 上验证 class/method 签名。

## FUNCTION_NONE 语义

| 字段 | 值 / 含义 |
|------|----------|
| `FUNCTION_NONE_PRIMARY_MASK` | `0` |
| `FUNCTION_NONE_USER_SEMANTICS` | `CHARGING_ONLY_NO_DATA` |
| `FUNCTION_NONE_SCREEN_UNLOCKED_SENTINEL` | `DISABLE_AUTO_UNLOCK_FUNCTION` |
| `LOW_LEVEL_CHARGING_FUNCTIONS_OWNER` | `SYSTEM`（`getChargingFunctions`） |

## 模块功能偏好

| 模块偏好值 | 语义 | effective primary function |
|------------|------|----------------------------|
| `0` | `FOLLOW_SYSTEM` | 当前 native default (`mScreenUnlockedFunctions`) |
| `1` | `CHARGING` | `UsbManager.FUNCTION_NONE` (`0`) |
| `2` | `MTP` | `UsbManager.FUNCTION_MTP` (`4`) |
| `3` | `PTP` | `UsbManager.FUNCTION_PTP` (`16`) |

## 推荐架构

```text
RECOMMENDED_ARCHITECTURE = OPTION_B_NON_DESTRUCTIVE_RUNTIME_OVERRIDE
```

### Default application boundary

ROM 准备把 default 送进 `setEnabledFunctions(JZI)V` 的所有入口构成 true boundary。经 OS1.0.8.0 字节码确认，这些入口是：

1. `UsbHandler.setScreenUnlockedFunctions(I)V` → `setEnabledFunctions(mScreenUnlockedFunctions, false, opId)`
2. `UsbHandler.handleMessage(...)` default-application 分支直接调用 `setEnabledFunctions(0, false, opId)`
3. `UsbHandler.finishBoot(I)V` 直接调用 `setEnabledFunctions(0, false, opId)`

```text
DEFAULT_APPLICATION_BOUNDARY = com.android.server.usb.UsbDeviceManager$UsbHandler.setEnabledFunctions(JZI)V
DEFAULT_APPLICATION_CONTEXT = handleMessage / setScreenUnlockedFunctions / finishBoot
NATIVE_DEFAULT_FIELD = com.android.server.usb.UsbDeviceManager$UsbHandler.mScreenUnlockedFunctions
```

### Hook 设计

```text
HOOK CLASS = com.android.server.usb.UsbDeviceManager$UsbHandler (and concrete UsbHandlerHal/UsbHandlerLegacy)
HOOK METHOD = setEnabledFunctions(JZI)V
HOOK TYPE = BEFORE + context-guarded parameter rewrite
```

#### 上下文守卫

1. 安装一个 `BEFORE` hook 在 `UsbHandler.handleMessage(Landroid/os/Message;)V`。
2. 在 `before` 中，通过反射/字段发现读取 `MSG_UPDATE_STATE`、`MSG_SET_SCREEN_UNLOCKED_FUNCTIONS`、`MSG_UPDATE_SCREEN_LOCK`、`MSG_BOOT_COMPLETED`、`MSG_SYSTEM_READY`。
3. 若 `msg.what` 属于上述 default-application 消息，压入一个 `ThreadLocal<UsbDefaultContext>`，包含 `operationId`、`msg.what`、`mScreenUnlockedFunctions`、`mScreenLocked`。
4. 在 `handleMessage` `after` 中弹出。

#### setEnabledFunctions 参数重写

在 `UsbHandlerHal.setEnabledFunctions` 与 `UsbHandlerLegacy.setEnabledFunctions` 的 `BEFORE` hook 中：

1. 检查 `ThreadLocal` 上下文是否存在。
2. 若不存在 → 直接放行（manual、policy、user switch、ADB、accessory 等）。
3. 若存在但 `mScreenLocked == true` 或 `forceRestart == true` → 直接放行。
4. 否则从 `mScreenUnlockedFunctions` 读取 `nativeDefault`。
5. 用 `resolveUsbDefaultFunction(modulePreference, nativeDefault)` 计算 `effective`。
6. 改写 `param.args[0] = effective`。
7. 其它参数 (`forceRestart`, `operationId`) 不变。

#### 辅助 narrow hook（可选）

仅 hook `UsbHandler.setScreenUnlockedFunctions(I)V` 的 `BEFORE` 即可覆盖 native 非 none 路径，但无法覆盖 native none 的 `setEnabledFunctions(0, ...)` 调用。因此单独 `setScreenUnlockedFunctions` hook 不足；最终采用 `setEnabledFunctions` 上下文守卫作为完整 boundary。

### 映射函数

```text
resolveUsbDefaultFunction(modulePreference, nativeDefault):
    0 (FOLLOW_SYSTEM) -> nativeDefault
    1 (CHARGING)      -> 0
    2 (MTP)           -> 4
    3 (PTP)           -> 16
```

### Native state invariants

```text
NATIVE_PERSISTED_STATE_MUTATED_BY_MODULE = NO
NATIVE_IN_MEMORY_STATE_MUTATED_BY_MODULE = NO
GET_SCREEN_UNLOCKED_FUNCTIONS_RETURNS_NATIVE = YES
```

- `mScreenUnlockedFunctions` 保持 ROM native 值。
- `mSettings` 不被模块写入。
- `getScreenUnlockedFunctions()` 保持返回 native。

### Native NONE 支持

```text
NATIVE_NONE_CUSTOM_MTP = SUPPORTED
NATIVE_NONE_CUSTOM_PTP = SUPPORTED
```

当 `nativeDefault == 0` 且模块偏好为 MTP/PTP 时：

- `handleMessage` 的 `MSG_UPDATE_STATE` 断开分支、`MSG_SET_SCREEN_UNLOCKED_FUNCTIONS` else 分支、`finishBoot` else 分支都会调用 `setEnabledFunctions(0, false, opId)`。
- 上下文守卫识别为 default application，`setEnabledFunctions` 参数被改写成 `MTP`/`PTP`。
- `MSG_UPDATE_SCREEN_LOCK` 解锁分支中，当 `mScreenUnlockedFunctions == 0 && mCurrentFunctions == 0` 时原逻辑不调用任何 `setEnabledFunctions`。
  - 该情况需在同一 `handleMessage` hook 中补充判断：若解锁且模块偏好为数据功能，主动调用 `setEnabledFunctions(effective, false, opId)`。
  - 或者在 `handleMessage` `after` 中检测：`mScreenUnlockedFunctions == 0`、`mScreenLocked == false`、`mCurrentFunctions == 0`、`mConnected == false`？该分支条件在原始代码中不会触发应用，因此需要显式补充。

### Manual current function 排除

```text
MANUAL_CURRENT_FUNCTION_EXCLUDED = YES
```

- `MSG_SET_CURRENT_FUNCTIONS`（`0x2`）不在上下文守卫的 default-application 集合中。
- 其 `setEnabledFunctions(functions, false, opId)` 调用不会触发参数重写。
- 用户通过 USB notification 临时选择的 MTP/PTP/MIDI/RNDIS 等保持原样。

### Policy 路径排除

```text
POLICY_PATH_EXCLUDED = YES
```

- `MSG_UPDATE_USER_RESTRICTIONS`（`0x6`）不在上下文集合。
- 其 `setEnabledFunctions(0, true, opId)`（`forceRestart=true`）不会被重写。
- `mScreenLocked == true` 的分支也不重写，保持充电。

### Accessory / Tethering / MIDI / OEM 排除

```text
ACCESSORY_TETHERING_MIDI_EXCLUDED = YES
```

- `MSG_ACCESSORY_MODE_ENTER_TIMEOUT`（`0x8`）、`MSG_UPDATE_HOST_STATE`（`0xa`）、`MSG_UPDATE_PORT_STATE`（`0x7`）不在上下文集合。
- `RNDIS`、`MIDI`、`NCM` 等通过 `setCurrentFunctions` 设置，走 `MSG_SET_CURRENT_FUNCTIONS`，不受重写。
- ADB 由 `getAppliedFunctions` 自动组合，模块不干预。

### Live preference change

```text
LIVE_REAPPLY_ENTRY = UsbHandler.sendMessage(MSG_SET_SCREEN_UNLOCKED_FUNCTIONS, current mScreenUnlockedFunctions)
```

- `PreferenceObserver` 变化后，通过 `UsbHandler` 的 Looper 发送 `MSG_SET_SCREEN_UNLOCKED_FUNCTIONS`，`obj` 为当前 `mScreenUnlockedFunctions` 原生值。
- 该消息会触发原 default 路径，上下文守卫识别后由 `setEnabledFunctions` hook 计算 `effective`。
- 不要直接调用 `setEnabledFunctions` 或 `setCurrentFunctions`；不要新建 USB listener/Service/polling。

## Message ID 处理

```text
HANDLEMESSAGE_REQUIRED = YES
MESSAGE_ID_HARDCODE = NO
MESSAGE_ID_DISCOVERY = REFLECTION / DEXKIT on cold path
```

原因：`MSG_UPDATE_SCREEN_LOCK` 解锁、`MSG_SET_SCREEN_UNLOCKED_FUNCTIONS` else、`finishBoot` 等分支为 native none 所必需。实现时应从 `UsbDeviceManager` 的 `private static final int` 字段（如 `MSG_UPDATE_STATE`、`MSG_SET_SCREEN_UNLOCKED_FUNCTIONS`、`MSG_UPDATE_SCREEN_LOCK`、`MSG_BOOT_COMPLETED`、`MSG_SYSTEM_READY`）通过反射/结构发现取值，不要硬编码 `0xc` 等数字。

## 性能与失败模型

- 热路径：每次 `handleMessage`/`setEnabledFunctions` 只读 `MainModule.mPrefs` int、一个 `ThreadLocal` 栈、一个 `when`。
- 无 Binder、disk、SharedPreferences file IO、DexKit、Field search、String parsing per event。
- DexKit/反射仅在 `SYSTEM_SERVER_STARTING` 安装时使用。
- Fail-open：class/method/field 不匹配 → 不安装 USB override，ROM USB 行为原样继续。

## 多用户

```text
NATIVE USB DEFAULT = PER_USER
MODULE system_usb_default_function = DEVICE_GLOBAL
MODULE_OVERRIDE_SCOPE = DEVICE_GLOBAL
MULTI_USER_LIMITATION = YES
```

## 进程与生命周期

- 进程：`system_server`
- 安装点：`SystemServerFeatures` / `InstallPhase.SYSTEM_SERVER_STARTING`
- 偏好键：`system_usb_default_function`（0-3），使用 `MainModule.mPrefs.getInt`。
- 无 SystemUI / Settings / 模块 app 侧 hook。

## 拒绝的替代方案

1. **改写 `mSettings` 或 `mScreenUnlockedFunctions`**：污染 native ownership。
2. **Hook `UsbManager.setScreenUnlockedFunctions` / `IUsbManager.setScreenUnlockedFunctions` 公开 API**：只覆盖 Settings 显式写入，不覆盖 boot/用户切换/解锁/断开。
3. **无条件 Hook 所有 `setEnabledFunctions`**：会覆盖 manual current function、policy、user switch、accessory。
4. **写 `sys.usb.config` / `persist.sys.usb.config`**：破坏政策、多用户、OEM override、ADB 组合。
5. **模块 app 内调用 `UsbManager.setScreenUnlockedFunctions`**：无 `MANAGE_USB`。
6. **USB 插拔 BroadcastReceiver / 轮询 / Service**：违反无 listener 约束。

## 最终报告字段

```text
P1_A2_BASE_SHA = 44c10be4ddd8600ef864330def2762ae2136307e
ANDROID14_NATIVE_DEFAULT_API = android.hardware.usb.UsbManager.setScreenUnlockedFunctions / getScreenUnlockedFunctions
USB SERVICE OWNER = system_server / com.android.server.usb.UsbService + UsbDeviceManager
NATIVE DEFAULT PERSISTED = YES

FUNCTION_NONE_PRIMARY_MASK = 0
FUNCTION_NONE_USER_SEMANTICS = CHARGING_ONLY_NO_DATA
FUNCTION_NONE_SCREEN_UNLOCKED_SENTINEL = DISABLE_AUTO_UNLOCK_FUNCTION
LOW_LEVEL_CHARGING_FUNCTIONS_OWNER = SYSTEM

FUNCTION_MTP = 1 << 2 = 4
FUNCTION_PTP = 1 << 4 = 16

ADB_COMBINATION_OWNER = SYSTEM
USB_CONNECTION_LISTENER = NONE

FOLLOW_SYSTEM_REVERSIBLE = YES

OPTION_A = REJECT
OPTION_B = ACCEPT

RECOMMENDED_ARCHITECTURE = OPTION_B_NON_DESTRUCTIVE_RUNTIME_OVERRIDE

NATIVE_PERSISTED_STATE_MUTATED_BY_MODULE = NO
NATIVE_IN_MEMORY_STATE_MUTATED_BY_MODULE = NO
GET_SCREEN_UNLOCKED_FUNCTIONS_RETURNS_NATIVE = YES

DEFAULT_APPLICATION_BOUNDARY = com.android.server.usb.UsbDeviceManager$UsbHandler.setEnabledFunctions(JZI)V
DEFAULT_APPLICATION_CONTEXT = handleMessage / setScreenUnlockedFunctions / finishBoot
NATIVE_DEFAULT_FIELD = com.android.server.usb.UsbDeviceManager$UsbHandler.mScreenUnlockedFunctions

HOOK TYPE = BEFORE + context-guarded parameter rewrite
HANDLEMESSAGE_REQUIRED = YES

NATIVE_NONE_CUSTOM_MTP = SUPPORTED
NATIVE_NONE_CUSTOM_PTP = SUPPORTED

MANUAL_CURRENT_FUNCTION_EXCLUDED = YES
POLICY_PATH_EXCLUDED = YES
ACCESSORY_TETHERING_MIDI_EXCLUDED = YES

LIVE_REAPPLY_ENTRY = UsbHandler.sendMessage(MSG_SET_SCREEN_UNLOCKED_FUNCTIONS, current mScreenUnlockedFunctions)

MODULE_OVERRIDE_SCOPE = DEVICE_GLOBAL
MULTI_USER_LIMITATION = YES

HYPEROS1_FUXI_USB_CHAIN = VERIFIED_ON_OS1.0.8
OS1.0.8_VS_AOSP_USB_CHAIN = MINOR_VENDOR_VARIATION
OS1_0_7_IMPLEMENTATION_CONFIDENCE = HIGH
TARGET_EXACT_BYTECODE = NOT_AUDITED

USB CLASS CONTAINER = system/framework/services.jar (classes3.dex)
CONTAINER_SHA256 = 9341C22BF12F98D12607AF3B93EF7C597931309A32E388C3BDC394854840DB75
DEX/ODEX/VDEX = classes3.dex (extracted from services.jar)
DEX_SHA256 = 6DB77BF1A5FABA4A0ED18D8241D73904637D257806F3FD842A954E28FC6C650A

P1_B_SAFE_TO_IMPLEMENT = YES

PRODUCTION CHANGE = NO
fast --changed = PASS
diff --check = PASS

P1_B_PRODUCTION_CHANGE = YES
P1_B_INITIAL_IMPLEMENTATION = 73b0146054cd2d35c602e4a9350a61859de57395
P1_B_INITIAL_GATE = HOLD
P1_B_BLOCKER = JZI_ARGUMENT_CONTRACT

P1_B_JZI_CORRECTIVE = 7d4e97071a4d8835e5fb7e17b7178af5ba68539d
P1_B_FAILURE_BOUNDARY_INITIAL_GATE = HOLD
P1_B_BLOCKER = SUPPLEMENT_AFTER_ORIGINAL_FAILURE

P1_B_COMMIT = 4047422f
P1_B_ARGUMENT_CONTRACT = PASS_CANDIDATE
P1_B_FAILURE_BOUNDARY = PASS_CANDIDATE
P1_B_VERIFY_FAST = PASS
P1_B_VERIFY_FULL = PASS
P1_B_DIFF_CHECK = PASS
P1_B_PUSHED = YES
```

## P1-B Final Failure-Boundary Corrective

### 修正原因

`HookerClassHelper.MethodHook.intercept()` 的 after 回调在 `chain.proceed()` 抛出异常后仍会执行。`HandleMessageHook.after()` 当时没有检查 `callback.getThrowable()`，导致即使 ROM `handleMessage()` 本身失败，模块仍会执行 `maybeSupplementScreenUnlock()`，即 ROM 失败后继续执行 USB mutation，违反 fail-open 原则。

### 修正项

1. `HandleMessageHook.after()` 首先检查 `callback.getThrowable() != null`；如果原方法失败，直接 return，只由 `finally` 完成 `UsbDefaultContext.pop()`。
2. 保留所有已通过审计的 JZI contract、exact Method hook、mode mapping、native state ownership、policy guard、native NONE supplement eligibility 等不变。
3. 新增回归测试：在 `MSG_UPDATE_SCREEN_LOCK` + native NONE + mode MTP + transfer allowed 的条件下，若 `AfterHookCallback` 携带原方法异常，则 `setEnabledFunctions` 不被调用，且 context 被清理。

## P1-B Corrective

### 修正原因

初始实现 `73b01460` 中 `SetEnabledFunctionsHook` 错误地把 `setEnabledFunctions(JZI)V` 当成四参数调用处理：

```text
if (args.size < 4) return
val forceRestart = args[2] as? Boolean
```

实际的 ROM signature 是：

```text
setEnabledFunctions(J Z I)V
args[0] = long  functions
args[1] = boolean forceRestart
args[2] = int operationId
```

这会导致所有正常 `setEnabledFunctions` 调用被直接 `return`，无法改写。

### 修正项

1. **JZI production parser**
   - 新增 `SetEnabledCall` / `parseSetEnabledCall(...)`
   - 严格检查 `args.size == 3`、类型为 `Long / Boolean / Int`
2. **Exact signature hook**
   - 不再 `hookAllMethodsSilently`
   - `resolveTargets()` 在 cold install 阶段解析 `setEnabledFunctions(long, boolean, int)` 的精确 `Method`
   - 安装时使用 `ModuleHelper.hookMethod(method, callback)` 只挂具体 `UsbHandlerHal/UsbHandlerLegacy` 实现
3. **参数改写边界**
   - 只读取 `args[0]` / `args[1]` / `args[2]`
   - 只改写 `args[0]`
   - `forceRestart` 与 `operationId` 原样保留
4. **Mode sanitization**
   - `getMode()` 对任何非 0-3 值返回 `FOLLOW_SYSTEM`
5. ** narrowed handleMessage context**
   - `isDefaultMessage()` 仅对 `MSG_UPDATE_STATE / MSG_SET_SCREEN_UNLOCKED_FUNCTIONS / MSG_UPDATE_SCREEN_LOCK` push context
   - `MSG_BOOT_COMPLETED` / `MSG_SYSTEM_READY` 由独立的 `FinishBootHook` 拥有
6. **Unconditional cleanup**
   - `HandleMessageHook.after()` 只要 peek 到 `HANDLE_MESSAGE` frame 就在 `finally` 中 pop
   - 即使 `thisObject == null`、supplement 抛异常或原方法抛异常，也不会留下 stale frame
7. **ThreadLocal lazy cleanup**
   - `UsbDefaultContext` 改为 `ThreadLocal<ArrayDeque?>`
   - `peek()` 不创建 deque
   - 最后一个 frame pop 后调用 `ThreadLocal.remove()`
8. **Cold method cache**
   - `resolveTargets()` 预先解析并缓存：
     - `isUsbTransferAllowed()`
     - `setEnabledFunctions(JZI)`
   - runtime 使用 `Method.invoke`，不再按名称 lookup
9. **Install failure boundary / rollback**
   - `resolveTargets()` 缺任何 Class/Method/Field/constant 返回 `FAILED_TRANSIENT`
   - 安装阶段收集 `CustomMethodUnhooker`，任一失败 `unhook()` 已安装的 hooks
10. **测试补充**
    - `parseSetEnabledCall` JZI 合同测试
    - `applySetEnabledFunctionsOverride` 实际参数改写边界测试
    - `SetEnabledFunctionsHook` / `HandleMessageHook` end-to-end 测试
    - `getMode()` 非法值测试
    - `UsbDefaultContext` last-pop 清理、thread isolation、嵌套测试
    - `HandleMessageHook` `MSG_BOOT_COMPLETED` / `MSG_SYSTEM_READY` 不 push context 测试
    - native-none supplement 路径测试（allowed / blocked）

## P1-B 实现记录

### 改动文件

- `app/src/main/java/tv/withaibuild/customiuizer/mods/SystemUsbDefaultHooks.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/FeatureIds.kt`
- `app/src/main/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeatures.kt`
- `app/src/main/res/xml/prefs_system.xml`
- `app/src/main/res/values/arrays.xml`
- `app/src/main/res/values*/strings.xml`
- `app/src/test/java/tv/withaibuild/customiuizer/mods/SystemUsbDefaultHooksTest.kt`
- `app/src/test/java/tv/withaibuild/customiuizer/mods/utils/feature/SystemServerFeaturesWiringTest.kt`

### 设计核对

```text
PREFERENCE_KEY = system_usb_default_function
PREFERENCE_STORAGE_READ = getStringAsInt
FEATURE_INSTALL = ALWAYS_ON
FOLLOW_SYSTEM FAST PATH = YES
NATIVE PERSISTED MUTATION = NO
NATIVE IN-MEMORY MUTATION = NO
GET_SCREEN_UNLOCKED_FUNCTIONS HOOKED = NO
NATIVE_NONE_MTP = SUPPORTED
NATIVE_NONE_PTP = SUPPORTED
MANUAL CURRENT SESSION OVERRIDDEN = NO
ACCESSORY/TETHERING/MIDI OVERRIDDEN = NO
ADB OWNER = SYSTEM
LIVE PREF CHANGE = NEXT_DEFAULT_EVENT
LIVE FORCE REAPPLY = NO
USB CONNECTION LISTENER = NONE
```

### 实现要点

1. `UsbDefaultFunctionFeature` always returns `isEnabled = true` and installs `SystemUsbDefaultHooks.hook()`.
2. Hook target: exact `com.android.server.usb.UsbDeviceManager$UsbHandlerHal/UsbHandlerLegacy.setEnabledFunctions(JZI)V`.
3. `resolveTargets()` resolves all classes, message ids, fields and the exact methods at cold install time. Missing target/member returns `FAILED_TRANSIENT` before any hook is installed.
4. Installation uses pre-resolved `Method` objects with `ModuleHelper.hookMethod()` and rolls back (`unhook()`) if any step fails.
5. `SetEnabledCall` / `parseSetEnabledCall(...)` enforce the exact JZI contract (`args.size == 3`, `Long/Boolean/Int`).
6. Context guard: before/after hooks on `UsbHandler.handleMessage`, `setScreenUnlockedFunctions`, and `finishBoot` push/pop a `ThreadLocal<ArrayDeque<ContextFrame>>`.
7. `isDefaultMessage()` only recognizes `MSG_UPDATE_STATE / MSG_SET_SCREEN_UNLOCKED_FUNCTIONS / MSG_UPDATE_SCREEN_LOCK`; `finishBoot` has its own `FINISH_BOOT` context.
8. The `setEnabledFunctions` hook only rewrites `args[0]` when a context frame is present, mode is not `FOLLOW_SYSTEM`, `!mScreenLocked`, `!forceRestart` and (for data modes) transfer is allowed.
9. MTP/PTP calls the ROM's pre-resolved `isUsbTransferAllowed()` and falls back to `FUNCTION_NONE` when disallowed.
10. In `MSG_UPDATE_SCREEN_LOCK` unlock branch where `mScreenUnlockedFunctions == 0 && mCurrentFunctions == 0` the ROM does not call `setEnabledFunctions`; `handleMessage.after` supplements the override using the pre-resolved `setEnabledFunctions` `Method.invoke()`.
11. `getScreenUnlockedFunctions` is not hooked; `mSettings` and `mCurrentFunctions` are not modified; no USB connection listener is registered; preference changes take effect on the next default event without forced reapply.

### 测试覆盖

- `SystemUsbDefaultHooksTest`
  - `getStringAsInt` parsing for string, number, and invalid values
  - `getMode` sanitization (valid / invalid / negative)
  - `resolveEffective` mapping for FOLLOW_SYSTEM / CHARGING / MTP / PTP / invalid
  - `computeEffectiveUsbFunctions` policy guards (screenLocked, forceRestart, transferAllowed, already-correct, charging override)
  - `parseSetEnabledCall` JZI contract (arity / types / valid / invalid)
  - `applySetEnabledFunctionsOverride` argument-rewrite boundary (MTP/PTP/CHARGING/FOLLOW_SYSTEM/forceRestart/screenLocked/transferAllowed/alreadyCorrect)
  - `SetEnabledFunctionsHook` end-to-end rewrite and no-rewrite-without-context
  - `HandleMessageHook` context exclusion for `MSG_BOOT_COMPLETED` / `MSG_SYSTEM_READY` and cleanup with null `thisObject`
  - `HandleMessageHook` native-none supplement (allowed / blocked)
  - `HandleMessageHook` no supplement when original `handleMessage` failed
  - `UsbDefaultContext` thread-local, nested, and last-pop cleanup
- `SystemServerFeaturesWiringTest`
  - `UsbDefaultFunctionFeature` always-on wiring and catalog membership

### 验证

```text
python tools/verify.py fast --tests SystemUsbDefaultHooksTest SystemServerFeaturesWiringTest
# -> BUILD SUCCESSFUL

python tools/verify.py full
# -> BUILD SUCCESSFUL (compileDebugKotlin, compileDebugJavaWithJavac, testDebugUnitTest, lintDebug)

git diff --check
# -> (no output)

python tools/audit-feature-semantics.py --validate
# -> Validation passed
```

### 验收边界

```text
DEVICE_ACCEPTANCE = PENDING NEXT SIGNED APK
```

Real-device acceptance is pending a signed APK on `fuxi / OS1.0.7.0.UMCTWXM`.
