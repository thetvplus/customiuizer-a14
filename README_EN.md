# CustoMIUIzer A14

[简体中文](README.md) | English

CustoMIUIzer A14 is an Xposed module for customizing the system UI, launcher, and interactions on **HyperOS 1 / Android 14**. It supports **arm64-v8a** and requires **libxposed API 102 or later**. See the [compatibility matrix](COMPATIBILITY.md) for ROM and host-app requirements.

[Download the official release](https://github.com/thetvplus/customiuizer-a14/releases/latest) · [Changelog](CHANGELOG_EN.md)

[Telegram community](https://t.me/customiuizerA14)

## Feature overview

| Settings category | Main features |
| --- | --- |
| System | Status bar, Duo icon, Dynamic Island, notifications, Control Center, display and lock screen |
| Home screen | Layout, Dock materials, folders, DECK Recents, floating windows and launcher gestures |
| Sound & controls | Volume styles, vibration, hardware keys, fingerprint actions, full-screen gestures and navigation bar |
| Apps & tools | App management, installer, Wi-Fi and VPN automation, per-app location switching, maintenance and privacy |

See the [compact feature map](docs/FEATURE_MAP_SIMPLE.md) for page paths and the [detailed feature map](docs/FEATURE_MAP.md) for settings keys and visibility conditions.

## Installation

1. Download and install the official APK. Updates signed with the same certificate preserve app data.
2. Enable the module in your Xposed framework and grant the required scopes.
3. Restart the affected process or device, then enable the features you need. Hooks selected at installation time require restarting their target process after changing the switch.

Enable one CustoMIUIzer module per device. Feature-specific prerequisites and restart requirements are covered in [Dynamic Island](docs/features/island/README.md#设置与兼容), [DECK Recents](docs/features/ios18-recents-reference.md), and [Home Indicator](docs/features/ios18-home-indicator.md).

## Documentation and Development

Start maintenance tasks with the [engineering rules](AGENTS.md). Settings are declared in [app/settings](app/settings). See the [architecture](ARCHITECTURE.md#入口与进程路由) for host-process routing, hook installation, and source entry points, and the [roadmap](docs/maintenance/A14_REFINEMENT_ROADMAP.md) for device acceptance and outstanding work. Other references are listed in the [documentation index](docs/README.md).

The source version is `r14.27.7` (versionCode 307), with application ID `tv.withaibuild.customiuizer.r14`. See the [release record](docs/RELEASE.md#当前正式版本) for the published APK and its source identity.

## Support and License

Support continued maintenance through the code below or [online donations](https://paypal.me/Jinjitv).

<img src="app/src/main/res/drawable-nodpi/wechat_donation_code.webp" alt="Project donation code" width="320">

Distributed under GPL-3.0. The project derives from Mikanoshi's CustoMIUIzer and draws on MonwF's Android 14 work. [License](LICENSE) · [Mikanoshi/CustoMIUIzer](https://code.highspec.ru/Mikanoshi/CustoMIUIzer) · [MonwF/customiuizer](https://github.com/MonwF/customiuizer) · [Duo references and bundled licenses](docs/features/duo-status-bar.md)
