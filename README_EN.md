# CustoMIUIzer A14

[简体中文](README.md) | English

A system UI and interaction customization module maintained for **HyperOS 1 / Android 14**, derived from CustoMIUIzer. Supports **arm64-v8a** and requires **libxposed API 102 or later**.

[Download the official release](https://github.com/thetvplus/customiuizer-a14/releases/latest) · [Changelog](CHANGELOG_EN.md)

[Telegram community](https://t.me/customiuizerA14)

## Main Features

- **System**: status bar, Duo icon, Dynamic Island, notifications, Control Center, lock screen, audio and vibration.
- **Home screen**: Dock backgrounds and materials, layout, folders, app titles and gestures.
- **Buttons and gestures**: power, volume and fingerprint keys, navigation bar and custom actions.
- **Apps and maintenance**: installer, app management, permission prompts, and recoverable maintenance and privacy operations.

See the [compact feature map](docs/FEATURE_MAP_SIMPLE.md) for all pages. Detailed behavior is documented in the [Dynamic Island](docs/features/island/README.md), [Duo](docs/features/duo-status-bar.md) and [Dock](docs/features/dock/README.md) guides.

## Installation

1. Download and install the official APK. Updates signed with the same certificate preserve app data.
2. Enable the module in your Xposed framework and grant the required scopes.
3. Restart the affected process or device, then enable the features you need. Hooks selected at installation time require restarting their target process after changing the switch.

Availability depends on the ROM and system-app versions. Do not enable this module together with upstream or another CustoMIUIzer-derived module. Other major Android or HyperOS versions are unsupported; see the [compatibility matrix](COMPATIBILITY.md).

## Documentation and Development

The [documentation index](docs/README.md) covers development, testing, releases and feature contracts. Start with the [shared engineering rules](AGENTS.md) before contributing.

Current source version: `r14.26.7` (versionCode 267). Maintainer: `thetvplus`. Application ID: `tv.withaibuild.customiuizer.r14`.

r14.26.5 combines the Compose and API 102 refinements with motion-aware text, clearer choices and page feedback cleanup, and removes Dock light glass; see the [changelog](CHANGELOG_EN.md). Full CI, official signing and both releases are verified. Develop device checks passed; further ADB installation checks are outside the completed scope. Valid r14.26.3 changes are included, and its superseded release has been retired. See the [current refinement state](docs/maintenance/A14_REFINEMENT_ROADMAP.md) for validation boundaries.

Git history has been consolidated with anonymous commit metadata. Existing signed APKs and original build metadata are unchanged; see the [source correspondence](docs/RELEASE.md#历史匿名化与已签名版本).

## Support and License

Support continued maintenance through the code below or [online donations](https://paypal.me/Jinjitv).

<img src="app/src/main/res/drawable-nodpi/wechat_donation_code.webp" alt="Project donation code" width="320">

Distributed under [GPL-3.0](LICENSE). Derived from [Mikanoshi/CustoMIUIzer](https://github.com/Mikanoshi/CustoMIUIzer), with Android 14 work referenced from [MonwF/customiuizer](https://github.com/MonwF/customiuizer). Duo references and bundled licenses are listed in the [Duo notes](docs/features/duo-status-bar.md).
