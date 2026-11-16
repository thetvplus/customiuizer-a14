# CustoMIUIzer A14 — Changelog

English | [简体中文](CHANGELOG.md)

The cumulative notes for r14.25, r14.26 and r14.27 are in r14.25.9, r14.26.11 and r14.27.9 respectively. The original r14.27.5 and r14.27.7 releases retain their notes, APKs, signatures and build provenance.

## Unreleased

## r14.27.9 — 2026-11-16

versionCode 310. Changes since r14.26.11:

### Release summary

This summary compares the shipped r14.27.7 implementation. See the [changelog](https://github.com/thetvplus/customiuizer-a14/blob/r14.27.9/CHANGELOG_EN.md#r14279--2026-11-16) for cumulative changes.

- Refined DECK's window coordinates and occlusion when opening a card in landscape. Opening the front card skips unnecessary window clipping.
- The iOS gesture indicator now styles native landscape handles in Launcher and System UI. Native drawing continues while the window size settles.
- Improved Dock blur preparation when returning from Recents and reduced the work Dynamic Island does to update status-bar occlusion.
- Delayed location shutoff keeps the identity of the task that scheduled it so that unrelated task removals cannot interfere with the last-card check.
- Simplified volume auto-hide and notification auto-expand while preserving their settings and behavior.
- Restart Launcher and System UI after upgrading. Reboot Android after first enabling location automation.

### User changes

- DECK shares its card layout across Home and app entry. Card size is adjustable, titles and icons fade by depth, and the window animation can be turned off separately.
- Refined DECK handoff during held swipes, front and rear card openings, rapid cancellation, backfill after removal and landscape resizing. Native cards render thumbnail shadows. Quick switching along the gesture indicator keeps native geometry.
- The iOS gesture indicator follows native visibility, light and dark transitions, and gesture input, including native landscape handles. Enable full-screen gestures, keep the system indicator visible and restart Launcher and System UI.
- The side volume style supports a narrow default, expansion while dragging and recoil. The center style supports vertical adjustment, hides shortcuts when collapsed and restores native mute and Do Not Disturb when expanded. Both retain native volume steps, routing and expansion controls.
- Show volume percentage in a floating overlay, Toast capsule or Dynamic Island. Consecutive changes update the same indicator.
- Refined the Island's first appearance, activity changes, press feedback, rapid reversals and recoil. Wide layouts align content to the outside edges in either layout direction. Notifications update their actions while remaining visible, and landscape alarms and timers give the native window room.
- The Island can size the absorption head automatically or use the width set in Motion and Waveform. Media return, recall and picture-in-picture retain native handoff.
- Optional iOS battery, five-dot cellular signal and notification-shade media styles preserve native status, media sessions and controls. Duo retains priority and has updated wireless glyphs, digits, spacing and transitions.
- Fixed Dock background blur after reattachment and return from Recents. Launcher folders fade blur on open and close.
- Location automation under Apps and Tools supports a 0–30 second shutoff delay. The allow list can enable location and turn off module-enabled location after the last matching task card is removed. The block list turns off location while a matching app enters the foreground.
- Updated settings categories, labels and search paths while retaining the four home entries. Fixed settings categories that could become inaccessible after restoring an older backup.
- Reduced duplicate Control Center feedback and improved cleanup for device monitoring, key actions and the Recents menu after cancellation or host recreation.
- If Island notices or return animations stop working, check mode, app lists and the return toggle.

### Engineering notes

- Consolidated host Hook installation and fallback, preference publication and observers, and Dock material and window styling ownership. Narrowed R8 missing-class rules for private HyperOS types.
- A delayed location shutoff retains the task ID that scheduled it. Location mode, app list and delay are read from the same published snapshot.
- DECK first composition and sustained landscape tracking, full Dynamic Island visual handoff and long-term resource costs remain on the device acceptance roadmap.

## r14.27.7 — 2026-11-13

versionCode 307. Changes since r14.27.5:

### User changes

- The [r14.27.9 cumulative changelog](https://github.com/thetvplus/customiuizer-a14/blob/r14.27.9/CHANGELOG_EN.md#r14279--2026-11-16) covers the changes still in effect.

### Engineering notes

- Original notes and signed assets remain in the [source repository](https://github.com/thetvplus/customiuizer-a14/releases/tag/r14.27.7) and [download repository](https://github.com/Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14/releases/tag/307-r14.27.7).

## r14.27.5 — 2026-11-11

versionCode 305. Changes since r14.26.11:

### User changes

- The [r14.27.9 cumulative changelog](https://github.com/thetvplus/customiuizer-a14/blob/r14.27.9/CHANGELOG_EN.md#r14279--2026-11-16) covers the changes still in effect.

### Engineering notes

- Original notes and signed assets remain in the [source repository](https://github.com/thetvplus/customiuizer-a14/releases/tag/r14.27.5) and [download repository](https://github.com/Xposed-Modules-Repo/tv.withaibuild.customiuizer.r14/releases/tag/305-r14.27.5).

## r14.26.11 — 2026-11-02

versionCode 281. Changes since r14.25.9 (cumulative):

### Release summary

- Refresh settings, search and editors, and add Wi-Fi automation and per-app behavior rules.
- Reorganize island media, notifications, activities and return effects, with improved older-launcher returns, positioning and waveforms.
- Fix Dock corner crashes and leftover blur, plus Control Center, backup, permission and system-entry issues.
- Reduce unnecessary updates and background work. After updating, check island mode, app lists and return effects, and follow restart prompts.

### User changes

- Migrate settings to Compose with consistent lists, search, selectors, sliders, colors and action editors, preserving preferences, backups and behavior. Group app behavior, Wi-Fi and VPN automation under System → Apps and automation.
- Keep home search collapsed until opened, then focus and highlight matches. Improve keyboard handling, navigation, background restoration, language changes and index publication; reuse indexes by language and existing app lists without a query.
- Unify neutral light/dark surfaces and system dynamic colors. Refine rows, menus, current values and spacing; fix empty dialog titles, pressed-state residue and text jitter. Select numeric input automatically and support scrolling around long content and landscape keyboards.
- Add Wi-Fi automation using network names, optional BSSID and priority to adjust DND, ringer, rotation and VPN. Restore only owned state on exit, respecting manual changes and user switches with bounded recovery after failures.
- Add opt-in per-app rules for sensor rotation, media-volume memory, fixed or system brightness, and restrictions on app brightness requests, with Activity exceptions. Restart the device after first enabling.
- Reorganize island media, notifications, live activities and system feedback. Share manually selected media apps between display and return, use selected notification apps, and preserve settings, backups and search routes.
- Unify return effects as None, Instant and Absorption: native return, capture-free reception, or continuous absorption using native window springs. Support adapted older HyperOS 1 launchers with task, PiP, transparency and interruption safeguards.
- Refine island entry, reception, recoil, positioning and offsets. Retain same-session media briefly during track changes, fix stale recording levels after silence, and clear expired clock and activity state.
- Use bounded waveform sampling with display-frame interpolation and stop on settled silence, preserving hidden, paused and power-saving limits. Restore lightweight waveforms after system animations are re-enabled.
- Keep solid and frosted Dock materials, falling back from retired lightweight glass. Fix blur restoration, corner crashes and leftover areas across hiding, unlock and Recents. Avoid blur for solid panels and stop frame synchronization when stable.
- Apply global window-blur changes promptly while retaining developer, power-saving and thermal limits. Separate Recents blur from local island transitions by their actual callers.
- Use caches, system events and on-demand refresh for trusted Wi-Fi and Bluetooth lists, respecting permissions and scan cooldown. Move backup I/O off the UI thread, prevent duplicate operations and resume after permissions; preserve settings on cancellation or invalid choices.
- Add system-app updater disable/restore and options to hide updater and enterprise-mode entries. Preserve state snapshots, result checks and Settings search consistency; follow restart prompts after enabling or updating.
- Fix Control Center columns, screenshot folders and formats, foreground-app observation, nullable interfaces and recovery. Strengthen access checks for system actions, screenshot notifications and battery previews while retaining native failure feedback.
- Correct credential shortcut targets and results, unify keyboard spacing names with explicit Gboard support, refine Duo airplane icons and sizing, and reduce repeated clock, network-speed and charging updates.
- After updating, some island settings may need reselection. If the island or returns are missing, check mode, media/notification lists, return switches and None/Instant/Absorption; reselect as needed and restart affected processes.

### Engineering notes

- Consolidate valid changes from r14.26.1, r14.26.3, r14.26.5, r14.26.7 and r14.26.9 through r14.26.11. Retired lightweight glass, old return queues and withdrawn experiments are not retained features; original records and release evidence are archived.
- Generate pages, search and preference catalogs from shared settings definitions while keeping native View/Canvas hosts. Preserve grouped Hook installation, native exceptions, window/source ownership, background-file lifetimes and test/production boundaries.
- Keep original APKs, certificate, build identity, Android 14 / HyperOS 1, arm64-v8a and libxposed API 102. Toolchain maintenance and reduced calls do not establish new device frame-rate, startup or power measurements.

## r14.25.9 — 2026-10-29

versionCode 258. Changes since r14.24.1 (cumulative):

### Release summary

- Add Dynamic Island for music, notifications and system activities, with gestures, rotation support and camera clearance.
- Refine Dock materials, Duo icons and folder-icon blur, plus launcher and System UI handoffs.
- Add VPN automation, reorganize settings and search, and allow direct numeric slider input.
- Fix notifications, lock-screen artwork, live activities and settings recovery. Follow restart prompts when first enabling features.

### User changes

- Reorganize System, Launcher, Buttons and Gestures, and App Maintenance settings. Improve grouping, search, navigation, descriptions and current values; keep restart actions on the home page. Sliders accept validated numeric input.
- Add Dynamic Island for music, notifications, system feedback, calls, alarms, timers, stopwatch, recording and privacy status. Return to media or an idle island when a source ends; retain notification privacy and lock-screen rules.
- Use one island window and one active source, choosing long or short form from content. Notifications with a separate body use the long form; music and system activities use the short form. Swipe up to dismiss; keep an optional idle island without an expandable details panel.
- Support left, center and right positioning and horizontal adjustment. Fit dimensions, text, artwork, waveforms and touch areas to camera clearance, system insets and screen space, including stricter landscape limits.
- Add configurable music gestures for tracks, volume and shortcuts, plus opening the current player. Retain artwork and a static waveform while loading; improve duplicate sessions, multiple tracks, pause handling and track changes.
- Use native window returns for opt-in music-to-island transitions with manually selected players. Refine resizing, recoil and velocity handoffs; fix gaps, first-frame jumps, diagonal unlock motion, asymmetric deformation and repeated fades on quick returns.
- Offer lightweight and live waveforms with smoothed levels, noise filtering and silence decay, respecting privacy, pause and power-saving rules. Update clocks only while visible and clear state when sources or clock services end.
- Use app allowlists for notifications and category selection for feedback. Add optional charging and low-battery prompts; suppress the native low-battery warning only after the replacement is visible. Keep native feedback when takeover fails or content does not fit.
- Support top, center and bottom Toast placement with safe-area offsets. Short messages can use the island; notification shade keeps an idle island without touch interception, and idle taps no longer implicitly open the shade.
- Refine picture-in-picture entry, cancellation, exit, stashing and restoration. Floating-window exclusion affects only identifiable floating media while other allowed content remains visible; follow restart prompts after changes.
- Refine full-width Dock backgrounds, sizing, wallpaper colors and glass materials across unlock, Recents and app returns. Use one visible base and consistent corners; stop redundant sampling offscreen and prepare again on return.
- Refine Duo automatic size and optical centering. Add opt-in folder-icon background blur for supported MIUI and POCO interfaces, retaining system blur, default-icon and hardware limits. Restart the launcher after changes.
- Add VPN automation for multiple trigger apps, a VPN client or saved system IKEv2 profile. Configure delayed automatic disconnect independently, preserving manual, existing, always-on and lockdown connections. Restart after first enabling.
- Fix lock-screen artwork scaling, updates, stale screen-off requests and release. Validate complete native PIN keypads before scrambling, preserving click handling, fixed controls and authentication policy.
- Refine notification clicks, floating-window actions, expansion, collapse, rebinding, grouping and coloring. Fix shade and Control Center dates, app-lock timeout, installer details, volume keys and keyboard-dismiss boundaries.
- Synchronize fixed status-bar height with system resources while preserving fullscreen and contrast behavior. Refine A14 vibration strength, volume timers, panel hiding, loud-volume confirmation and audio focus. Restart after system-dimension changes.
- Fix vertical-dominant gestures triggering Dock swipes, default widget parameters and app-renaming refreshes. Improve back gestures, rotation, interruption, screenshot hiding, visualization and screen-off restoration.
- Improve migration, backup and maintenance recovery with saved state and result checks. Prevent stale startup or bulk-refresh snapshots from overwriting settings, retaining the last valid configuration on temporary read failures.

### Engineering notes

- Consolidate valid changes from r14.25.1, r14.25.3, r14.25.5 and r14.25.7 through r14.25.9. Superseded secondary activities and manual details panels are not listed as current features; original per-version records remain in Git and private release archives.
- Preserve Android 14 / HyperOS 1, arm64-v8a, libxposed API 102, original APKs, certificate and build metadata. Reduced allocation or sampling does not establish unmeasured device frame-rate or power gains.

## r14.24.1 — 2026-10-19

versionCode 245. Changes since r14.23.5:

- Add an optional background inside the native Launcher Dock, disabled by default. Reuse system glass or Gaussian blur, fit the background to native icon bounds and device corners, and expose opacity, radius, horizontal spacing and signed four-edge shrink controls without moving or resizing icons.
- Keep Dock background visibility tied to the native recent-tasks state, retire hidden blur regions, and preserve native icon reorder and app-return animation coordinates. Use one shared corner radius for native material and outline; retain cached G4 geometry when native material is unavailable.
- Fix Dock size adjustment when one edge reaches its limit, and ease background width changes over 180 ms when the icon count changes. Raise common slider number-entry dialogs slightly and fit them above the visible keyboard.
- Select Duo cellular signal from the active data subscription instead of ambiguous per-controller flags. Preserve all native signal levels, distinguish Wi-Fi levels 3 and 4, and add adjustable horizontal spacing.
- Coalesce weather refreshes and discard cancelled provider results, recover step-view state after reattachment, and restore screenshot-hidden overlays across repeated events and detachments.
- Propagate direct and wrapped fatal JVM errors from receiver registration, replacement, cleanup, logging, callbacks and ROM fallbacks. Roll back failed registrations before propagation; release the screen controller's context even if unregister fails, and allow initialization to recover after a failed registration. Preserve ordinary optional-feature fallbacks.
- Mark the static Duo Bluetooth battery summaries as non-formatted text so literal percentages are not parsed as AAPT format tokens.
- Detach stale receiver and listener cleanup batches before calling framework or user cleanup. Keep bounded retry queues while avoiding recursive retries and map locks held across cleanup callbacks.
- Limit preference generation inputs to Kotlin feature declarations and preference XML. Check the generated catalog against a full-source scan so ordinary Hook, Java and UI edits no longer invalidate settings resources without losing preference keys.
- Include non-ignored untracked inputs in local change-based verification and preserve UTF-8 diagnostics on Windows.
- Retain the validated Gradle, AGP, JDK 25, Kotlin and DexKit versions, API 102 baseline, runtime behavior and release gates. Device performance and target-ROM acceptance require separate verification.

## r14.23.5 — 2026-10-17

versionCode 243. Changes since r14.22.7:

- Reuse one displayed settings-group snapshot between adapter updates instead of rebuilding it for every row and draw. Release observers and cached page references when replacing the adapter or destroying the page; retain existing cards, dividers, clipping and day/night colours.
- Restrict the Open With sample provider to explicit temporary read grants. Reject write and unknown URI requests; store the two tiny text/ZIP samples uncompressed so all six choices remain readable through seekable file descriptors. Remove the obsolete Bluetooth permission while retaining Bluetooth Connect.
- Reject unrelated Tasker actions and unidentified senders before reading extras. Preserve token/host verification, propagate fatal failures and throttle malformed-broadcast logs without exposing payloads.
- Handle native API 102 scope-read/request failures when the service dies or denies access: report unavailability without crashing settings, retrying or claiming approval; retain fatal-error propagation and one scope snapshot per selection.
- Protect the native Service Binder provider with the existing signature permission, blocking ordinary apps from replacing the settings connection while retaining privileged-framework access.
- Make official DexSQL CLI/MCP and shared queries the primary development path for ROM targets and final DEX evidence. Add definition-source and Hook API boundary queries without adding runtime dependencies or default CI work. Retain the validated toolchain, dependencies and compact-resource task.
- Require libxposed API 102. Share one native hook installation policy and remove API 101 capability flags, fallbacks and isolation bridges. Retain the three infrastructure hook IDs, readback, callback semantics and lifecycle; hot reload and explicit hook replacement remain disabled.
- Raise Java/Kotlin output to 25 and Kotlin language/API to 2.4; adopt AGP 9.4.1 (bundled R8/D8), Kotlin BOM/stdlib 2.4.21, DexKit 2.3.0 and Fragment 1.9.1. Retain JDK 25, Gradle 9.8.1 and Android SDK 34 runtime support; use public AGP APIs for generated tasks.
- Propagate wrapped fatal failures from original methods and before/after callbacks while preserving ordinary exception recovery, argument changes and proceed counts. Reuse the empty argument array.
- Check actual preference-mirror commits; optimistic service caches no longer confirm rejected writes. Preserve one retry per connection and recovery on a fresh connection.
- Read scope once per selection instead of repeating Binder queries for each app; asynchronous callbacks retain only Application Context. Adopt app-list filter results without a second list copy.
- Fix blank settings pages and the blank screen on returning from About by preserving page position and alpha while collecting animators; retain back animations and state restoration. Add Android 14 UI-model regression tests for all four categories and their settings pages, return and recreation.
- Add an independent Duo charging-bolt switch, enabled by default; percentage and charging colour remain independent.
- Refine silent and DND shortcut colours while preserving the native appearance and animation; restore original colours when custom styling is disabled.
- Fix navigation long presses, key consumption and Control Center gesture cleanup. Restart the phone when first enabling navigation long presses.
- Remove ineffective A14 features and old settings, refine native network-speed and international-ROM options, and improve settings-sync error handling with fewer startup-diagnostics objects. Remove obsolete API-specific R8 barriers and scope helpers.
- Consolidate APK tools and remove test-only production sources and duplicate configuration while retaining resource compression, Hook compatibility boundaries, diagnostics and optional DexSQL tools.

## r14.22.7 — 2026-10-09

versionCode 230. Changes since r14.21.9:

- Add optional Duo battery, Wi-Fi and cellular icons, disabled by default, with lock screen, Control Center, double rows and size adjustments; refine network text, headphone hints, automatic sizing, handoffs and signal recovery.
- Preserve fatal errors in resource hooks, theme merging, logging and settings initialization; let the native Control Center icon manager tint custom text, avoiding conflicting wallpaper callbacks and duplicate listeners.
- Copy wallpaper and drawer-blur callback arguments only when rewriting is needed, reduce repeated thread-state reads and compute only selected temperatures. Preserve original calls, input ownership and exception propagation.
- Check backup CRC over the original byte slice; reduce search and locale-list intermediates while preserving backup format, matching and ordering.
- Use lossless WebP for the bundled QR image and Android 14-compatible compact resource entries; remove unused JVM coroutine-agent payload while retaining dynamic resources and Hook/JNI rules.
- Pin libxposed 102 to official Maven Central with SHA-256 verification and align Fragment KTX with Fragment 1.5.4; include validated dependency and build-tool security updates, retaining DexKit 2.2.0, the API 101 minimum runtime, JDK 25 and JVM 17 output.
- Coordinate Fast and Full CI while retaining mutation gates, clean builds without caches, and APK/R8 mapping reproducibility checks; remove unused resources and alias placeholders, obsolete API checks and resource qualifiers; strengthen signing/version checks and consolidate maintenance documentation and release history.

## r14.21.9 — 2026-09-30

versionCode 218. Changes since r14.20.9:

- Coalesce Control Center step refreshes to prevent slow-query backlogs; cancel obsolete work on screen-off, view removal or controller replacement, preserving serial queries and recovery.
- Optimize app-lock and privacy-list ordering and search, preserving selected-first order and dual-user identity.
- Initialize icon cache keys and reuse shortcut metadata, reducing repeated reads and list copies during scrolling, search and result publication.
- Fix left-side mobile/Wi-Fi icons and add left-icon size and vertical-offset controls.
- Fix Control Center settings transitions; cover standalone pages, combined route queries, Wi-Fi/WIFI matching and result navigation in search.
- Remove duplicate whole-row switch feedback and update the About maintainer to thetvplus.
- Refine settings; fix notification channel navigation, importance settings on both page variants, and clipping of the final extended-menu item.

## r14.20.9 — 2026-09-05

versionCode 206. Earliest retained release; older history remains in Git.

- Defer system_server business features until the preference snapshot is ready; retain the Hook catalog across rapid SystemUI restarts.
- Isolate WindowManager argument-type errors and scope status-bar resource overrides to the system, SystemUI and launcher; system_server still owns window geometry.
