# M4.1 Feature catalog 实机测量证据

## 结论

M4.1 的实机触发条件未满足，不实施 Feature 安装表压缩、直接分发代码生成或
`FeatureInstallState` 容器替换。

在 Android 14 / HyperOS 1 的 fuxi 设备上重复采样 5 次后，最大的 SystemUI catalog
创建与 registry 注册中位数合计为 `1.732 ms`；system_server 为 `1.284 ms`，Launcher
两个阶段按同一次进程启动配对后合计为 `0.980 ms`。这些路径每个目标进程只执行一次，
不足以支持增加生成代码、稠密 ID 容器或改变现有安装合同。

## 测量构建

- 工程 revision：`5624720634de3c4fd9df8bf3310da958fda59e90`；
- build type：签名 Develop/R8；
- APK：`CustoMIUIzer-A14-r14.18.2-develop.apk`；
- APK SHA-256：`C20578C0FF4976C2D692DF792848088AFD0C8470496AA429E313F313259A04D1`；
- versionCode：`195`；
- 设备：`fuxi`，Android `14` / API `34`。

测量代码只在 `BuildConfig.BUILD_TYPE == "develop"` 时读取
`art.gc.bytes-allocated` 并写入一行 `FeaturePerf`。同一源码构建的 Release/R8 DEX 中，
`FeaturePerf`、ART 分配统计键及四个测量标签均不存在，因此正式版没有采样、日志或
运行时分支成本。

## 方法

每个入口分别记录：

1. 调用 `Features.all(...)` 前后的 `System.nanoTime()` 与 ART 已分配字节计数；
2. 向 `FeatureInstallRegistry` 注册全部 Spec 前后的相同计数；
3. Spec 数量、真实进程名与入口标签；
4. 同轮 HookSummary，排除偏好不可用、缺类、缺成员或安装失败样本。

Launcher 使用 `force-stop` 后重新启动，SystemUI 使用受控 `am crash` 后由系统重新拉起，
system_server 使用完整重启。SystemUI 样本间隔为 12 秒，避免触发项目既有的 10 秒重启
保护；6 秒压力间隔会按设计只安装 2/3 个基础 Hook，因此不计入性能样本。

## 结果

单位：时间为微秒，分配量为 bytes。

| 入口 | Spec | catalog 5 次 | catalog 中位数 | registry 5 次 | registry 中位数 |
|---|---:|---|---:|---|---:|
| SystemUI package-ready | 96 | 1703, 1485, 1641, 1644, 1496 | 1641 | 95, 81, 84, 91, 96 | 91 |
| Launcher package-ready | 12 | 535, 404, 430, 498, 487 | 487 | 13, 11, 13, 10, 14 | 13 |
| Launcher post-attach | 43 | 555, 527, 451, 440, 536 | 527 | 31, 31, 24, 30, 32 | 31 |
| system_server starting | 51 | 1218, 1318, 1228, 1043, 1349 | 1228 | 56, 56, 61, 43, 61 | 56 |

Launcher 两阶段按同一轮相加后的 catalog 为 `1090, 931, 881, 938, 1023`，中位数
`938 us`；registry 为 `44, 42, 37, 40, 46`，中位数 `42 us`。

ART 分配计数以块状更新，不能解释为精确的对象大小：

- SystemUI catalog 每轮报告 `32,768`，registry 为 `0`；
- Launcher 的约 `32,768` 主要落在 post-attach registry 区间，个别 catalog 样本为
  `64` 或 `32,768`，体现计数块边界漂移；
- system_server catalog 为 `40,960–65,536`，registry 为 `0`。

因此只能判断一次性分配处于几十 KiB 量级，不能把块状计数写成每个 Spec 的精确大小。

## Hook 与稳定性证据

- 5 个完整 SystemUI 样本均为 `onPackageReady installed=51`、`post-init installed=52`；
- 5 个 system_server 样本均为 `onSystemServerStarting installed=43`；
- 所有样本的 `classMissing`、`memberMissing`、`failed`、`silentSkipped`、
  `dexkitFailed`、`dexkitNoMatch`、`prefsUnavailable` 均为 `0`；
- 快速 SystemUI 重启保护触发时没有把受保护样本误计入正常启动数据。

## 决策

1. 保留现有 `LazyFeatureSpec`、稳定 FeatureId、target/phase/enabled/failure 语义；
2. 不把 `HashMap<Int, FeatureState>` 改为 ByteArray 或稠密 ID 表；
3. 不因文件长度拆分巨型 Hook 单例；
4. 保留 Develop-only 测量点，供 ROM 或功能规模明显变化后复测；
5. 下一阶段只接受新的 retained heap、`<clinit>` 或可重复启动回归证据，不继续理论优化。
