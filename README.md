# 米客 A14

简体中文 | [English](README_EN.md)

米客 A14 是基于 CustoMIUIzer 的 Xposed 模块，用于定制 **HyperOS 1 / Android 14** 的系统界面、桌面和交互。支持 **arm64-v8a**，框架最低要求为 **libxposed API 102**。具体功能的 ROM 和宿主版本要求见[兼容矩阵](COMPATIBILITY.md)。

[下载正式版](https://github.com/thetvplus/customiuizer-a14/releases/latest) · [更新记录](CHANGELOG.md)

[Telegram 交流群](https://t.me/customiuizerA14)

## 功能概览

| 设置分类 | 主要功能 |
| --- | --- |
| 系统 | 状态栏、Duo 三合一图标、动态岛、通知、控制中心、屏幕与锁屏 |
| 桌面 | 布局、Dock 材质、文件夹、最近任务 DECK、浮动窗口与桌面手势 |
| 声音与操作 | 音量样式、振动、按键、指纹操作、全面屏手势与导航栏 |
| 应用与工具 | 应用管理、安装器、Wi-Fi 与 VPN 自动化、按应用自动切换定位、维护与隐私 |

页面路径见[功能地图简版](docs/FEATURE_MAP_SIMPLE.md)，设置键与显示条件见[详细功能地图](docs/FEATURE_MAP.md)。

## 安装

1. 从下载页获取正式 APK 并安装；同签名覆盖安装可保留应用数据。
2. 在 Xposed 框架中启用模块，授予需要的作用域。
3. 重启相关进程或设备，按页面提示启用所需功能；安装型开关修改后需要重启目标进程。

同一设备仅启用一个 CustoMIUIzer 模块。专项启用条件与重启要求见[动态岛](docs/features/island/README.md#设置与兼容)、[最近任务 DECK](docs/features/ios18-recents-reference.md)及[手势指示条](docs/features/ios18-home-indicator.md)。

## 文档与开发

维护任务从[工程规则](AGENTS.md)进入。设置声明位于 [app/settings](app/settings)，宿主进程路由、Hook 安装与源码入口见[架构](ARCHITECTURE.md#入口与进程路由)，实机待验与证据见[当前精修状态](docs/maintenance/A14_REFINEMENT_ROADMAP.md)。其他专题见[文档目录](docs/README.md)。

当前源码版本为 `r14.27.7`（versionCode 307），应用 ID 为 `tv.withaibuild.customiuizer.r14`。正式 APK 和源码身份见[发布记录](docs/RELEASE.md#当前正式版本)。

## 支持与许可

可以通过下方赞赏码或[在线捐赠](https://paypal.me/Jinjitv)支持后续开发与维护。

<img src="app/src/main/res/drawable-nodpi/wechat_donation_code.webp" alt="项目赞赏码" width="320">

依据 GPL-3.0 分发。项目派生自 Mikanoshi 的 CustoMIUIzer，并参考 MonwF 的 Android 14 工作。[许可证](LICENSE) · [Mikanoshi/CustoMIUIzer](https://code.highspec.ru/Mikanoshi/CustoMIUIzer) · [MonwF/customiuizer](https://github.com/MonwF/customiuizer) · [Duo 图形来源与随包许可](docs/features/duo-status-bar.md)
