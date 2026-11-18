# CustoMIUIzer A14 — Changelog

English | [简体中文](CHANGELOG.md)

## Unreleased

## r14.27.10 — 2026-11-18

versionCode 311. Changes since r14.27.9:

### Release summary

- **Control Center**. Fix swapped pull-down gestures and refine custom tile columns and panel layout in portrait and landscape.
- **Status bar**. Dual-row signal indicators refresh the second SIM independently. Network speed follows the system refresh and reset.
- **Volume and brightness**. Percentages follow native slider progress, and the extended Do Not Disturb timer uses the system control.
- **Home screen**. Refine icon titles, folder layout and double-tap handling in the native launcher.

Restart Launcher and System UI after upgrading.

### User changes

- With notification shade and Control Center gestures swapped, the notification shade can continue a pull-down when Control Center declines it.
- Custom Control Center tile columns adapt to portrait and landscape. If the tile layout hook is unavailable, the system panel layout and column count are retained. The edit entry and security notice can still be hidden independently.
- The Control Center carrier text follows system visibility. Step counts start refreshing when the header attaches, move as it expands and follow the system privacy indicator's visibility rules.
- Dual-row indicators update the second SIM when the primary SIM state is unchanged and restore the single-SIM display after switching back. Detailed network speed uses the system refresh and reset, and battery styles use the system battery state.
- Volume and brightness percentages use native slider progress. Extended timer intervals and labels line up with the native slider in portrait and landscape.
- Refine the native entry points for home-screen icon title size, folder width and double-tap gestures on the verified MiuiHome and POCO Launcher versions.

### Engineering notes

- Control Center tile layout hooks install as a group and bind spans per panel instance. Step-count observers are released when the last view detaches. Build and test dependencies were updated.
- Device verification of layouts, SIMs, theme changes and host performance is pending. See the [refinement roadmap](docs/maintenance/A14_REFINEMENT_ROADMAP.md) for checked ROMs and remaining scenarios.

## r14.27.9 — 2026-11-16

versionCode 310. A stable milestone for HyperOS 1 / Android 14.

### Release summary

- **Home and navigation**. DECK, Dock and the iOS gesture indicator integrate with native windows and gestures.
- **System experience**. Dynamic Island brings activities and alerts together, with optional styles for the status bar, notification-shade media and volume.
- **Automation and settings**. Configure rules for location, Wi-Fi, VPN and app behavior. Existing preferences and backups remain usable.

Restart Launcher and System UI after upgrading.

### User changes

- DECK keeps native scrolling, task dismissal and quick switching. Card size and window animation are adjustable. Landscape openings use native window coordinates, and Dock blur is restored on return to Home.
- The iOS gesture indicator follows native visibility and color. Keep the system handle visible with full-screen gestures and restart Launcher and System UI after changing the setting.
- Dynamic Island offers adjustable absorption width and leaves room for native landscape alarm and timer windows. Check mode, app lists and the return toggle if notifications or return animations stop working.
- The status bar offers Duo, iOS battery and five-dot cellular styles. The notification-shade media card uses native media sessions and playback controls.
- Use a narrow default with side volume, or adjust center volume placement and expand it to access native mute and Do Not Disturb. Both styles use native volume and routing. Show percentage in an overlay, Toast or Dynamic Island.
- Configure location automation under Apps and Tools and reboot Android after first enabling it. The allow list can enable location for a matching app. With auto-close enabled, removing the last matching card can turn off module-enabled location. The block list can turn location off when a matching app enters the foreground. Both support a 0–30 second delay.
- Settings categories and search paths are aligned. Existing preferences and backups remain usable, and an update with the same signature preserves app data.

### Engineering notes

- Consolidated Hook installation and fallback, preference publication and window material ownership. Delayed location shutoff retains its originating task and checks current tasks and persisted ownership before acting.
- The official APK source and signature are bound to the release tag. Device, animation and performance coverage are tracked in the [refinement roadmap](docs/maintenance/A14_REFINEMENT_ROADMAP.md#待实机验收).
