# 米客 A14｜CustoMIUIzer A14

[简体中文](README.md) | English

A system UI and interaction customization module maintained for **HyperOS 1 / Android 14**, derived from CustoMIUIzer. It supports **arm64-v8a / libxposed API 102** and has an independent package, signing identity and release line.

- Current official release: `r14.25.1` (versionCode 246), requiring libxposed API 102.
- Development and maintenance: `thetvplus`
- Application ID: `tv.withaibuild.customiuizer.r14`
- [Download the official release](https://github.com/thetvplus/customiuizer-a14/releases/latest) · [Release history](https://github.com/thetvplus/customiuizer-a14/releases) · [Changelog](CHANGELOG.md)

This release rebuilds settings pages and navigation, adds Dynamic Island, Toast positioning and direct slider-value entry, expands full-width Dock and glass controls, and refines audio, vibration, System UI and maintenance recovery. Updates preserve the signing identity and app data.

The full repository, source and About-page name is 米客 A14｜CustoMIUIzer A14; short app labels retain 米客 A14.

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

Open System → Dynamic Island for app feedback and media integrations, disabled by default. Restart System UI after enabling. See the [Dynamic Island guide](docs/DYNAMIC_ISLAND.md) for behavior and pending device checks.

The working branch also adds a track-swipe switch enabled by default, prevents hidden paused music from flashing when returning Home, and refines settings wording and grouping. These changes are not yet part of the official release above. See the [release guide](docs/RELEASE.md) for the automated build and publication entry point.

Duo is disabled by default. Open System → Status bar → Duo, then restart System UI after changes. See [Duo notes](docs/DUO_STATUS_BAR.md) for sizing, signal and hint behavior.

Dock background is disabled by default. Open Home screen → Dock, then restart the launcher after changes. Background shrink adjusts all four edges: positive values contract, negative values expand, with a +1 dp default. Icon space and host clipping bound the effective adjustment; icons retain their native positions and sizes.

Availability depends on the ROM and system-app versions. Dynamic app-scope requests still require framework approval. Do not enable this module together with upstream or another CustoMIUIzer-derived module. Android 13 / 15 / 16 and other major MIUI / HyperOS versions are unsupported. See [COMPATIBILITY.md](COMPATIBILITY.md).

## Development and Maintenance

Builds use JDK 25, Java/Kotlin output target 25 and Kotlin language/API 2.4. See the [development guide](docs/DEVELOPMENT.md#环境) for the Android 14 runtime boundary. Full local verification:

```powershell
python tools/verify.py full
python -m unittest discover -s tools/tests -p "test_*.py"
git diff --check
```

Read [AGENTS.md](AGENTS.md) and the [documentation index](docs/README.md) first. Environment setup, builds, tests and releases are documented in [DEVELOPMENT.md](docs/DEVELOPMENT.md), [TESTING.md](docs/TESTING.md) and [RELEASE.md](docs/RELEASE.md). See [ARCHITECTURE.md](ARCHITECTURE.md) for runtime structure.

## Support and License

Support continued maintenance via WeChat or [PayPal](https://paypal.me/Jinjitv).

<img src="app/src/main/res/drawable-nodpi/wechat_donation_code.webp" alt="WeChat donation code" width="320">

Distributed under [GPL-3.0](LICENSE). Derived from [Mikanoshi/CustoMIUIzer](https://github.com/Mikanoshi/CustoMIUIzer), with Android 14 work referenced from [MonwF/customiuizer](https://github.com/MonwF/customiuizer). Duo references and bundled licenses are listed in the [Duo notes](docs/DUO_STATUS_BAR.md).
