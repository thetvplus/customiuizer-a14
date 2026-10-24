# 米客 A14｜CustoMIUIzer A14

简体中文 | [English](README_EN.md)

面向 **HyperOS 1 / Android 14** 的系统界面与交互定制模块，基于 CustoMIUIzer 持续维护。仅支持 **arm64-v8a / libxposed API 102**，使用独立包名、签名和版本线。

- 当前源码版本：`r14.25.3`（versionCode 248），要求 libxposed API 102。同名正式版已发布；当前集成分支另含尚未发行的后续修改。
- 维护与开发：`thetvplus`
- 应用 ID：`tv.withaibuild.customiuizer.r14`
- [下载正式版](https://github.com/thetvplus/customiuizer-a14/releases/latest) · [发行记录](https://github.com/thetvplus/customiuizer-a14/releases) · [更新记录](CHANGELOG_CN.md)

本版重构设置页面与分类导航，新增动态岛、Toast 位置调节和滑杆精确输入，扩展 Dock 全宽与玻璃材质，并完善声音、振动、系统界面及维护恢复。同签名覆盖安装保留应用数据。

软件中文名称为「米客 A14」，英文名称为「CustoMIUIzer A14」。应用首页、应用内关于页及文档按语言使用对应名称；仓库主页和 GitHub About 的双语展示使用「米客 A14｜CustoMIUIzer A14」。

## 功能

- 状态栏图标、电池、信号、网速、日期与温度；
- 可选 Duo 三合一图标：电量圆环、Wi-Fi / 蜂窝图案、默认数据卡信号、网络类型文字与短时耳机提示；
- 动态岛的应用反馈与音乐活动、USB 默认用途、音量与亮度面板；
- 控制中心、通知、锁屏、充电与媒体界面；
- Launcher、最近任务、文件夹、图标与桌面手势；
- 导航栏、按键、自定义动作、电源菜单与系统动画；
- 应用、权限、安装、分享、隐私应用与应用锁行为。

## 安装与兼容

| 项目 | 支持范围 |
| --- | --- |
| 系统 | HyperOS 1 / Android 14，SDK 34 |
| ABI | `arm64-v8a` |
| Xposed 框架 | libxposed API 102 为最低基线 |
| 模块元数据 | `minApiVersion=102`、`targetApiVersion=102`、`staticScope=true` |

安装正式 APK，在框架中启用模块与所需作用域，然后重启相关进程或设备。设置页会提示需要重启的项目；安装时决定的 Hook 需要重启目标进程才能应用新开关。

动态岛入口为「系统 → 动态岛」，应用反馈与媒体接入默认关闭；开启后重启系统界面。行为与待验收项见 [动态岛说明](docs/features/island/README.md)。

集成分支新增手势所有权、音乐波形与可选原生返回、通知锁屏门禁、媒体快照及状态栏 / Insets 修复；Launcher 图标持续消失仍待实机确认。分支内容及待验项见[当前精修状态](docs/maintenance/A14_REFINEMENT_ROADMAP.md)，不能只凭相同版本名判定正式 APK 已包含。自动构建与发布入口见 [发布流程](docs/RELEASE.md)。

Duo 默认关闭，入口为「系统 → 状态栏 → Duo 三合一图标」，修改后重启系统界面。大小、信号及提示规则见 [Duo 使用说明](docs/features/duo-status-bar.md)。

Dock 背景默认关闭，入口为「桌面 → Dock」，修改后重启桌面。背景收缩同时调整上下和左右：正值内收、负值外扩，默认 +1 dp；图标空间或容器裁剪会限制实际调节幅度，图标仍使用原生位置与大小。

功能可用性取决于具体 ROM 和系统应用版本。目标应用作用域的动态请求仍取决于框架授权。请勿与上游版或其他 CustoMIUIzer 派生模块同时启用。不支持 Android 13 / 15 / 16 或其他 MIUI / HyperOS 大版本，详见 [兼容矩阵](COMPATIBILITY.md)。

## 开发与维护

从 [AGENTS.md](AGENTS.md) 核对任务和分支，按需查看[文档索引](docs/README.md)。环境与构建见 [DEVELOPMENT](docs/DEVELOPMENT.md)，测试职责见 [TESTING](docs/TESTING.md)，签名 / 发布见 [RELEASE](docs/RELEASE.md)，路由与所有权见 [ARCHITECTURE](ARCHITECTURE.md)。

## 支持与许可

可以通过微信赞赏或 [PayPal](https://paypal.me/Jinjitv) 支持后续开发与维护。

<img src="app/src/main/res/drawable-nodpi/wechat_donation_code.webp" alt="微信赞赏码" width="320">

依据 [GPL-3.0](LICENSE) 分发，派生自 [Mikanoshi/CustoMIUIzer](https://github.com/Mikanoshi/CustoMIUIzer)，参考 [MonwF/customiuizer](https://github.com/MonwF/customiuizer) 的 Android 14 工作。Duo 图形参考与随包许可见 [Duo 使用说明](docs/features/duo-status-bar.md)。
