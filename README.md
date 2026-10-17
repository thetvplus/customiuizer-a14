# CustoMIUIzer A14

简体中文 | [English](README_EN.md)

面向 **HyperOS 1 / Android 14** 的系统界面与交互定制模块，基于 CustoMIUIzer 持续维护。使用独立包名、签名和版本线。

- 当前版本：`r14.22.7`（versionCode 230）
- 维护与开发：`thetvplus`
- 应用 ID：`tv.withaibuild.customiuizer.r14`
- [下载正式版](https://github.com/Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14/releases/latest) · [源码与发行版](https://github.com/thetvplus/customiuizer-a14) · [更新记录](CHANGELOG_CN.md)

## 功能

- 状态栏图标、电池、信号、网速、日期与温度；
- 可选 Duo 三合一状态栏：电量圆环、Wi-Fi / 蜂窝图案、默认数据卡信号、网络类型文字与短时耳机提示；
- 灵动额头 / 动态岛、USB 默认用途、音量与亮度面板；
- 控制中心、通知、锁屏、充电与媒体界面；
- Launcher、最近任务、文件夹、图标与桌面手势；
- 导航栏、按键、自定义动作、电源菜单与系统动画；
- 应用、权限、安装、分享、隐私应用与应用锁行为。

## 安装与兼容

| 项目 | 支持范围 |
| --- | --- |
| 系统 | HyperOS 1 / Android 14，SDK 34 |
| ABI | `arm64-v8a` |
| Xposed 框架 | libxposed API 101 为最低基线，API 102 提供隔离的可选能力 |
| 模块元数据 | `minApiVersion=101`、`targetApiVersion=102`、`staticScope=true` |

安装正式 APK，在框架中启用模块与所需作用域，然后重启相关进程或设备。设置页会提示需要重启的项目；安装时决定的 Hook 需要重启目标进程才能应用新开关。

Duo 默认关闭，入口为「系统 → 状态栏 → Duo 三合一状态栏」，修改后重启系统界面。大小、信号及提示规则见 [Duo 使用说明](docs/DUO_STATUS_BAR.md)。

功能可用性取决于具体 ROM 和系统应用版本。API 101 框架下，目标应用作用域需手动配置；API 102 的动态请求仍取决于框架授权。请勿与上游版或其他 CustoMIUIzer 派生模块同时启用。不支持 Android 13 / 15 / 16 或其他 MIUI / HyperOS 大版本，详见 [兼容矩阵](COMPATIBILITY.md)。

## 开发与维护

构建使用 JDK 25，Android 字节码目标保持 17。完整验证入口：

```powershell
python tools/verify.py full
python -m unittest discover -s tools/tests -p "test_*.py"
git diff --check
```

开始前阅读 [工程规则](AGENTS.md) 和 [文档索引](docs/README.md)。环境配置、构建、测试与发布分别见 [开发说明](docs/DEVELOPMENT.md)、[测试说明](docs/TESTING.md) 和 [发布流程](docs/RELEASE.md)。架构见 [ARCHITECTURE.md](ARCHITECTURE.md)。

## 支持与许可

可以通过微信赞赏或 [PayPal](https://paypal.me/Jinjitv) 支持后续开发与维护。

<img src="app/src/main/res/drawable-nodpi/wechat_donation_code.webp" alt="微信赞赏码" width="320">

依据 [GPL-3.0](LICENSE) 分发，派生自 [Mikanoshi/CustoMIUIzer](https://github.com/Mikanoshi/CustoMIUIzer)，参考 [MonwF/customiuizer](https://github.com/MonwF/customiuizer) 的 Android 14 工作。Duo 图形参考与随包许可见 [Duo 使用说明](docs/DUO_STATUS_BAR.md)。
