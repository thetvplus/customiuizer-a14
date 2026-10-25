# 米客 A14

简体中文 | [English](README_EN.md)

面向 **HyperOS 1 / Android 14** 的系统界面与交互定制模块，基于 CustoMIUIzer 持续维护。支持 **arm64-v8a**，需要 **libxposed API 102 或更高版本**。

[下载正式版](https://github.com/thetvplus/customiuizer-a14/releases/latest) · [更新记录](CHANGELOG.md) · [反馈问题](https://github.com/thetvplus/customiuizer-a14/issues)

## 主要功能

- **系统**：状态栏、Duo 三合一图标、动态岛、通知、控制中心、锁屏、声音与振动。
- **桌面**：Dock 背景与材质、布局、文件夹、应用标题和桌面手势。
- **按键与手势**：电源键、音量键、指纹键、导航栏及自定义动作。
- **应用与维护**：安装器、应用管理、权限弹窗及可恢复的维护与隐私操作。

全部页面入口见[功能地图简版](docs/FEATURE_MAP_SIMPLE.md)。具体使用规则见[动态岛](docs/features/island/README.md)、[Duo](docs/features/duo-status-bar.md)和[Dock](docs/features/dock/README.md)。

## 安装

1. 从下载页获取正式 APK 并安装；同签名覆盖安装可保留应用数据。
2. 在 Xposed 框架中启用模块，授予需要的作用域。
3. 重启相关进程或设备，按页面提示启用所需功能；安装型开关修改后需要重启目标进程。

功能可用性取决于具体 ROM 和系统应用版本。请勿与上游版或其他 CustoMIUIzer 派生模块同时启用。其他 Android 或 HyperOS 大版本不在支持范围内，详见[兼容矩阵](COMPATIBILITY.md)。

## 文档与开发

[文档目录](docs/README.md)提供开发、测试、发布和专项说明；参与维护先读[共同工程规则](AGENTS.md)。

当前源码版本：`r14.25.6`（versionCode 252）。维护者：`thetvplus`。应用 ID：`tv.withaibuild.customiuizer.r14`。

源码分支的后续修改不一定包含在同版本正式 APK 中；未发行改动与待实机事项见[当前精修状态](docs/maintenance/A14_REFINEMENT_ROADMAP.md)。

## 支持与许可

可以通过微信赞赏或 [PayPal](https://paypal.me/Jinjitv) 支持后续开发与维护。

<img src="app/src/main/res/drawable-nodpi/wechat_donation_code.webp" alt="微信赞赏码" width="320">

依据 [GPL-3.0](LICENSE) 分发，派生自 [Mikanoshi/CustoMIUIzer](https://github.com/Mikanoshi/CustoMIUIzer)，参考 [MonwF/customiuizer](https://github.com/MonwF/customiuizer) 的 Android 14 工作。Duo 图形参考与随包许可见 [Duo 使用说明](docs/features/duo-status-bar.md)。
