# 米客 A14｜CustoMIUIzer A14

[简体中文](README.md) | English

A system UI and interaction customization module maintained for **HyperOS 1 / Android 14**, derived from CustoMIUIzer. It supports **arm64-v8a / libxposed API 102** and has an independent package, signing identity and release line.

- Current source version: `r14.25.3` (versionCode 248), requiring libxposed API 102. The official version is published; this integration branch also contains later, unreleased changes.
- Development and maintenance: `thetvplus`
- Application ID: `tv.withaibuild.customiuizer.r14`
- [Download the official release](https://github.com/thetvplus/customiuizer-a14/releases/latest) · [Release history](https://github.com/thetvplus/customiuizer-a14/releases) · [Changelog](CHANGELOG.md)

This release rebuilds settings pages and navigation, adds Dynamic Island, Toast positioning and direct slider-value entry, expands full-width Dock and glass controls, and refines audio, vibration, System UI and maintenance recovery. Updates preserve the signing identity and app data.

The English app name is CustoMIUIzer A14; the Chinese app name is 米客 A14. App screens, the in-app About page and documentation use the name for their language. The combined name 米客 A14｜CustoMIUIzer A14 is used for bilingual display on the repository homepage and GitHub About.

## Features

- Status bar icons, battery, signal, network speed, date and temperature;
- Optional Duo status icon: battery ring, Wi-Fi / cellular glyph, default data SIM signal, network type text and brief headphone hints;
- Dynamic Island app feedback and music activities, USB default purpose, volume and brightness panels;
- Control center, notifications, lock screen, charging and media UI;
- Launcher, recents, folders, icons and home-screen gestures;
- Navigation bar, buttons, custom actions, power menu and system animations;
- App, permission, installer, sharing, privacy-app and app-lock behavior.

## Installation and Compatibility

| Item | Supported range |
| --- | --- |
| System | HyperOS 1 / Android 14, SDK 34 |
| ABI | `arm64-v8a` |
| Xposed framework | libxposed API 102 minimum |
| Module metadata | `minApiVersion=102`, `targetApiVersion=102`, `staticScope=true` |

Install the official APK, enable the module and required scopes in your framework, then restart the affected process or device. Settings indicate which changes require a restart. Hooks selected at installation time require restarting their target process after changing the switch.

Open System → Dynamic Island for app feedback and media integrations, disabled by default. Restart System UI after enabling. See the [Dynamic Island guide](docs/features/island/README.md) for behavior and pending device checks.

The integration branch adds gesture ownership, waveform and optional native music return, keyguard notification checks, media snapshot and status bar / Insets fixes. Persistent Launcher icon loss still needs device investigation. See the [current refinement state](docs/maintenance/A14_REFINEMENT_ROADMAP.md); an identical version name does not mean the official APK includes these changes. See the [release guide](docs/RELEASE.md) for the automated build and publication entry point.

Duo is disabled by default. Open System → Status bar → Duo, then restart System UI after changes. See [Duo notes](docs/features/duo-status-bar.md) for sizing, signal and hint behavior.

Dock background is disabled by default. Open Home screen → Dock, then restart the launcher after changes. Background shrink adjusts all four edges: positive values contract, negative values expand, with a +1 dp default. Icon space and host clipping bound the effective adjustment; icons retain their native positions and sizes.

Availability depends on the ROM and system-app versions. Dynamic app-scope requests still require framework approval. Do not enable this module together with upstream or another CustoMIUIzer-derived module. Android 13 / 15 / 16 and other major MIUI / HyperOS versions are unsupported. See [COMPATIBILITY.md](COMPATIBILITY.md).

## Development and Maintenance

Start with [AGENTS.md](AGENTS.md) to identify the task and branch, then use the [documentation index](docs/README.md) as needed. See [DEVELOPMENT](docs/DEVELOPMENT.md) for setup and builds, [TESTING](docs/TESTING.md) for validation, [RELEASE](docs/RELEASE.md) for signing and publication, and [ARCHITECTURE](ARCHITECTURE.md) for routing and ownership.

## Support and License

Support continued maintenance via WeChat or [PayPal](https://paypal.me/Jinjitv).

<img src="app/src/main/res/drawable-nodpi/wechat_donation_code.webp" alt="WeChat donation code" width="320">

Distributed under [GPL-3.0](LICENSE). Derived from [Mikanoshi/CustoMIUIzer](https://github.com/Mikanoshi/CustoMIUIzer), with Android 14 work referenced from [MonwF/customiuizer](https://github.com/MonwF/customiuizer). Duo references and bundled licenses are listed in the [Duo notes](docs/features/duo-status-bar.md).
