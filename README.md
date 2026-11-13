# 米客 A14

简体中文 | [English](README_EN.md)

面向 **HyperOS 1 / Android 14** 的系统界面与交互定制模块，基于 CustoMIUIzer 持续维护。支持 **arm64-v8a**，需要 **libxposed API 102 或更高版本**。

[下载正式版](https://github.com/thetvplus/customiuizer-a14/releases/latest) · [更新记录](CHANGELOG.md)

[Telegram 交流群](https://t.me/customiuizerA14)

## 首页四个入口

- **系统**：状态栏、Duo 三合一图标、动态岛、通知、控制中心、屏幕与锁屏。
- **桌面**：Dock 背景与材质、最近任务堆叠、布局、文件夹、应用标题和桌面手势。
- **声音与操作**：音量面板、振动、电源键、音量键、指纹键、全面屏手势与导航栏。
- **应用与工具**：应用行为、安装器、应用管理、Wi-Fi 和 VPN 自动化、自动定位、维护与隐私。

全部页面入口见[功能地图简版](docs/FEATURE_MAP_SIMPLE.md)。具体使用规则见[动态岛](docs/features/island/README.md)、[Duo](docs/features/duo-status-bar.md)和[Dock](docs/features/dock/README.md)。

## 安装

1. 从下载页获取正式 APK 并安装；同签名覆盖安装可保留应用数据。
2. 在 Xposed 框架中启用模块，授予需要的作用域。
3. 重启相关进程或设备，按页面提示启用所需功能；安装型开关修改后需要重启目标进程。

升级后如动态岛未显示，检查提示模式、媒体 / 通知名单及上岛效果；空媒体名单不接入应用。设置条件与重启要求见[动态岛说明](docs/features/island/README.md#设置与兼容)。

功能可用性取决于具体 ROM 和系统应用版本。请勿与上游版或其他 CustoMIUIzer 派生模块同时启用。其他 Android 或 HyperOS 大版本不在支持范围内，详见[兼容矩阵](COMPATIBILITY.md)。

iOS 手势指示条需要全面屏手势及可见的系统指示条，同时关闭米客“隐藏手势提示线”，重启系统界面生效。[iOS 最近任务堆叠](docs/features/ios18-recents-reference.md)按完整原生接口适配 MIUI / POCO 桌面，需要“横向”最近任务排列，修改后重启桌面；全面屏手势为建议条件。

## 文档与开发

[文档目录](docs/README.md)提供开发、测试、发布和专项说明；参与维护先读[共同工程规则](AGENTS.md)。

当前源码版本：`r14.27.7`（versionCode 307）。维护者：`thetvplus`。应用 ID：`tv.withaibuild.customiuizer.r14`。

正式版本与附件以[发布页](https://github.com/thetvplus/customiuizer-a14/releases/latest)为准，变化见[更新记录](CHANGELOG.md)，未决问题见[当前精修状态](docs/maintenance/A14_REFINEMENT_ROADMAP.md)。

Git 历史已匿名整合；既有签名 APK 与原始构建 metadata 保持不变，来源关系见[发行说明](docs/RELEASE.md#历史匿名化与已签名版本)。

## 支持与许可

可以通过下方赞赏码或[在线捐赠](https://paypal.me/Jinjitv)支持后续开发与维护。

<img src="app/src/main/res/drawable-nodpi/wechat_donation_code.webp" alt="项目赞赏码" width="320">

依据 [GPL-3.0](LICENSE) 分发，派生自 [Mikanoshi/CustoMIUIzer](https://github.com/Mikanoshi/CustoMIUIzer)，参考 [MonwF/customiuizer](https://github.com/MonwF/customiuizer) 的 Android 14 工作。Duo 图形参考与随包许可见 [Duo 使用说明](docs/features/duo-status-bar.md)。
