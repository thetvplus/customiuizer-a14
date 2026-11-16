# CustoMIUIzer A14 — Changelog

English | [简体中文](CHANGELOG.md)

## Unreleased

## r14.27.9 — 2026-11-16

versionCode 310. A stable milestone for HyperOS 1 / Android 14.

### Release summary

- **Home and navigation**. DECK recents, Dock blur and the iOS gesture indicator work with the native launcher, task windows and gestures across supported portrait and landscape entry points.
- **Notifications and media**. Dynamic Island brings activities, media and system feedback into one surface, with refined content changes and return animations. The notification-shade media card uses native sessions and controls.
- **System appearance and feedback**. Choose Duo, iOS battery and cellular styles, and side or classic center volume panels. Show volume percentage in a floating overlay, Toast or Dynamic Island.
- **Per-app automation**. Configure location, Wi-Fi, VPN and app behavior with rules. Location automation supports app lists and delayed shutoff, with task and location ownership checks before shutoff.
- **Settings and upgrades**. The four settings entry points, search and existing backups remain available. Restart Launcher and System UI after upgrading. Reboot Android after first enabling location automation.

### User changes

- DECK keeps native scrolling, task dismissal and quick switching. Card size and window animation are adjustable. Landscape openings use native window coordinates, and Dock blur is restored on return to Home.
- The iOS gesture indicator follows native visibility and color. Keep the system handle visible with full-screen gestures and restart Launcher and System UI after changing the setting.
- Dynamic Island offers adjustable absorption width and leaves room for native landscape alarm and timer windows. Check mode, app lists and the return toggle if notifications or return animations stop working.
- Use a narrow default with side volume, or adjust center volume placement and expand it to access native mute and Do Not Disturb. Both styles use native volume and routing.
- Configure location automation under Apps and Tools. The allow list can enable location for a matching app and turn off module-enabled location after the last matching card is removed. The block list can turn location off when a matching app enters the foreground. Both support a 0–30 second delay.
- Settings categories and search paths are aligned. Existing preferences and backups remain usable, and an update with the same signature preserves app data.

### Engineering notes

- Consolidated Hook installation and fallback, preference publication and window material ownership. Delayed location shutoff retains its originating task and checks current tasks and persisted ownership before acting.
- The official APK source and signature are bound to the release tag. Device, animation and performance coverage are tracked in the [refinement roadmap](docs/maintenance/A14_REFINEMENT_ROADMAP.md#待实机验收).
