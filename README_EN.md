# CustoMIUIzer A14

[简体中文](README.md) | English

A system UI and interaction customization module maintained for **HyperOS 1 / Android 14**, derived from CustoMIUIzer. It has an independent package, signing identity and release line.

- Current version: `r14.22.7` (versionCode 230)
- Development and maintenance: `thetvplus`
- Application ID: `tv.withaibuild.customiuizer.r14`
- [Download the official release](https://github.com/Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14/releases/latest) · [Source and releases](https://github.com/thetvplus/customiuizer-a14) · [Changelog](CHANGELOG.md)

## Features

- Status bar icons, battery, signal, network speed, date and temperature;
- Optional Duo status icon: battery ring, Wi-Fi / cellular glyph, default data SIM signal, network type text and brief headphone hints;
- Status capsule / Dynamic Island, USB default purpose, volume and brightness panels;
- Control center, notifications, lock screen, charging and media UI;
- Launcher, recents, folders, icons and home-screen gestures;
- Navigation bar, buttons, custom actions, power menu and system animations;
- App, permission, installer, sharing, privacy-app and app-lock behavior.

## Installation and Compatibility

| Item | Supported range |
| --- | --- |
| System | HyperOS 1 / Android 14, SDK 34 |
| ABI | `arm64-v8a` |
| Xposed framework | libxposed API 101 minimum; isolated optional API 102 capabilities |
| Module metadata | `minApiVersion=101`, `targetApiVersion=102`, `staticScope=true` |

Install the official APK, enable the module and required scopes in your framework, then restart the affected process or device. Settings indicate which changes require a restart. Hooks selected at installation time require restarting their target process after changing the switch.

Duo is disabled by default. Open System → Status bar → Duo, then restart System UI after changes. See [Duo notes](docs/DUO_STATUS_BAR.md) for sizing, signal and hint behavior.

Availability depends on the ROM and system-app versions. On API 101 frameworks, selected app scopes need manual configuration; dynamic API 102 requests still require framework approval. Do not enable this module together with upstream or another CustoMIUIzer-derived module. Android 13 / 15 / 16 and other major MIUI / HyperOS versions are unsupported. See [COMPATIBILITY.md](COMPATIBILITY.md).

## Development and Maintenance

Builds use JDK 25 and retain Android bytecode target 17. Full local verification:

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
