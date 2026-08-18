# CustoMIUIzer A14

简体中文 | [English](README_EN.md)

CustoMIUIzer A14 是面向 **HyperOS 1 / Android 14（SDK 34）** 的系统界面与交互定制模块，基于 CustoMIUIzer 项目持续维护。它使用独立包名和版本线，不是上游官方版本。

- 当前正式版：`r14.20.7`
- 应用 ID：`tv.withaibuild.customiuizer.r14`
- 源码仓库：<https://github.com/tomthenpc/customiuizer-a14>
- 用户下载：<https://github.com/Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14/releases>

## 核心功能

- 状态栏图标、电池、信号、网速、日期与温度；
- 灵动额头 / 动态岛、USB 默认用途、音量与亮度面板；
- 控制中心、通知、锁屏、充电和媒体界面；
- Launcher、最近任务、文件夹、图标与桌面手势；
- 导航栏、按键、自定义动作、电源菜单和系统动画；
- 应用、权限、安装、分享、隐私应用和应用锁行为。

功能是否可用取决于具体 ROM 与系统应用版本。请勿与上游版或其他 CustoMIUIzer 派生模块同时启用。

## 兼容范围

| 项目 | 支持范围 |
| --- | --- |
| 系统 | HyperOS 1 / Android 14 |
| SDK | minSdk 34 / targetSdk 34 |
| ABI | `arm64-v8a` |
| Xposed 框架 | libxposed API 101/102 |
| 模块元数据 | `minApiVersion=101`、`targetApiVersion=102`、`staticScope=true` |

不支持 Android 15、Android 16 或其他 MIUI / HyperOS 大版本。API 102 能力保持隔离，未接入 API 101 必经的生产 Hook 路径。

## 运行期框架

- 按目标进程和功能开关延迟安装 Feature，关闭功能不创建业务 Hook、Receiver、Observer 或任务；
- Feature 采用进程内稳定 ID 和一次安装状态，偏好变化不会重复安装已存在 Hook；
- Receiver、Observer、View 和控制器注册绑定所有者，并具备替换、失效与释放闭环；
- 反射缓存按 ClassLoader 隔离且有界，反射、DexKit、磁盘 I/O 保留在冷路径；
- Hook 和回调隔离普通异常，`OutOfMemoryError`、`ThreadDeath` 和 `VirtualMachineError` 继续抛出；
- 模块加载日志与构建 provenance 包含版本和 Git revision，便于确认构建来源。

架构与兼容边界详见 [ARCHITECTURE.md](ARCHITECTURE.md) 和 [COMPATIBILITY.md](COMPATIBILITY.md)。

## 构建与验证

```bash
python tools/verify.py full
```

完整流程见 [DEVELOPMENT.md](docs/DEVELOPMENT.md) 和 [RELEASE.md](docs/RELEASE.md)。

## 支持与联系

如果这个项目对你有帮助，可以通过微信赞赏或 [PayPal](https://paypal.me/Jinjitv) 支持后续开发与维护。

<img src="app/src/main/res/drawable-nodpi/wechat_donation_code.png" alt="微信赞赏码" width="320">

- 仓库主页：<https://github.com/tomthenpc/customiuizer-a14>
- 联系方式（Telegram）：<https://t.me/Jinji_Kiko>

## 开发说明

- 稳定与行为保持优先；兼容逻辑限制在 ROM / ClassLoader 边界；
- 高频 Hook 避免临时数组、集合、Regex、格式化、重复反射和远程偏好读取；
- Java 到 Kotlin 采用行为等价迁移，并配套测试和静态门禁；
- 保留 `MainModule.java`、`XposedHelpers.java`、`MemberUtilsX.java` 的 JVM / 框架边界；
- 细粒度历史见 Git commits 和 tags，发布变化见 [CHANGELOG_CN.md](CHANGELOG_CN.md)。

项目依据 GPL-3.0 分发，派生自 Mikanoshi/CustoMIUIzer，并参考 MonwF/customiuizer 的 Android 14 工作。
