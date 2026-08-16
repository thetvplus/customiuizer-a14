# M3.1 状态栏设置按需加载证据（2026-08-09）

## 结论

“状态栏”分类已从“解析完整系统设置树后删除其他分类”改为只加载自己的独立资源。
普通分类点击和搜索结果直达使用同一资源选择逻辑；其他分类继续走原路径。

- 完整 `prefs_system.xml` 有 `210` 个 XML 元素；状态栏独立资源有 `33` 个；
- 独立资源保留原状态栏分类的全部 `31` 个子项、顺序、属性、依赖和 preference key；
- 同机 10 次往返的 janky frames 从旧版 `24 / 860`（`2.79%`）变为新版
  `19 / 865`（`2.20%`），新版重复轮为 `21 / 854`（`2.46%`）；
- 普通入口、搜索直达和未拆分的锁屏分类均通过实机回归，操作期间 PID 保持且没有
  FATAL 或 ANR。

结果证明本切片没有页面回归，并减少了状态栏页面的 Preference 创建边界。单台设备的
两轮帧数据只作为改善迹象，不扩大解释为长期或跨设备性能收益。

## 改动边界

1. `SystemPreferenceResourceResolver` 只把
   `pref_key_system_cat_statusbar` 映射到 `prefs_system_statusbar.xml`；
2. `CategorySelector` 的普通分类点击和 `MainFragment` 的搜索直达都调用该解析器；
3. 其他系统分类仍回退到 `prefs_system.xml`；
4. 搜索索引继续解析原总表，所以搜索内容和显示顺序没有改变；
5. 没有修改任何 Hook、目标进程代码、持久化 key、默认值或导入导出格式；
6. 没有修改按 owner 要求暂缓的 AudioVisualizer。

## 合同测试

- JVM 测试验证状态栏命中独立资源、空值与锁屏等其他分类仍命中完整资源；
- Python 合同测试逐节点比较原状态栏块与独立资源的标签、属性和顺序；
- Python 合同测试同时约束普通入口与搜索入口必须使用同一解析器；
- 测试先在旧实现上因资源和解析器不存在而失败，实施后通过。

## 实机对象与方法

| 项目 | 值 |
| --- | --- |
| 设备 | Xiaomi `fuxi_global` / `2211133G` |
| ROM | HyperOS `V816.0.7.0.UMCTWXM` |
| Android | Android 14 / SDK 34 |
| 旧版 APK SHA-256 | `835FEA72B0ED4F8E1334071C50792E1BFFD9BEAA2A0F25A4AEBFDBD7B40BD348` |
| 新版工程提交 | `6e2fad7be3fd775b1f8228443c06ff68c0a264c5` |
| 新版 APK SHA-256 | `1D3EC9FBCDCB4E333B83DB0AC06AE3BC1D1038E70221C9685ED3B9DE733E6FC2` |
| 包名 / 版本 | `tv.withaibuild.customiuizer.r14` / `r14.18.2`（195） |

方法：

1. 在旧版中冷启动模块，打开“系统 → 状态栏”，核对页面内容；
2. 清空该包的 gfxinfo 统计，连续执行 10 次“返回分类 → 打开状态栏”；
3. 使用同证书 `adb install -r -d` 覆盖安装新版，不清除数据和 preference；
4. 对新版执行完全相同的 10 次往返，并额外重复一轮；
5. 从搜索框输入 `W`，点击“显示 Wi-Fi 标准”，确认直接进入状态栏分类；
6. 冷启动后打开未拆分的“锁屏”分类，确认完整资源回退路径仍正常；
7. 每轮核对 PID、UI 文本、FATAL/ANR 和最终设备 APK SHA-256。

## 帧结果

| 指标 | 旧版 | 新版首轮 | 新版重复轮 |
| --- | ---: | ---: | ---: |
| Total frames | 860 | 865 | 854 |
| Janky frames | 24（2.79%） | 19（2.20%） | 21（2.46%） |
| 50th percentile | 6 ms | 6 ms | 5 ms |
| 90th percentile | 13 ms | 14 ms | 12 ms |
| 95th percentile | 15 ms | 15 ms | 15 ms |
| 99th percentile | 57 ms | 46 ms | 53 ms |
| Slow UI thread | 22 | 16 | 16 |

三轮 PID 均在各自测试开始到结束期间保持不变。由于过渡动画、输入调度和系统负载都会
影响 gfxinfo，本表用于排除明显回归，不单独证明确定的百分比收益。

## 静态、构建与最终状态

- 针对性 JVM/Python 测试通过；
- `python -m compileall tools` 通过；
- 全部 `423` 个工具测试通过，`5` 个按环境跳过；
- `python tools/verify.py fast --changed` 通过；
- `python tools/verify.py full` 的静态规则、Debug 编译、全部 JVM 测试和 lint 通过；
- Release APK 为非 debuggable、min/target SDK 34、arm64-v8a、v2 签名；
- 构建溯源为 `6e2fad7b`，签名证书 SHA-256 为
  `C0EFF2DC4E662717195490DA78B12A984C6F2E6BD38ACF4EDAD14D53E3D22E70`；
- 最终设备端 APK SHA-256 与本地一致，`sys.boot_completed=1`。

## 决策

保留状态栏独立资源。后续分类仍须作为独立切片逐个迁移和验证；本结果不授权一次性拆分
其余分类，也不触发 RecyclerView、Compose 或 Feature 安装架构重写。
