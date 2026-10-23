# CustoMIUIzer A14 — Changelog

English | [简体中文](CHANGELOG_CN.md)

Unreleased changes and retained releases; each release section consolidates changes since the previous retained version. Original source tags and APK provenance are preserved.

## Unreleased

## r14.25.2 — 2026-10-23

versionCode 247. Changes since r14.25.1:

- Validate the work branch and merged main before release confirmation, then verify the source release before syncing the same APK to Xposed. Support manual runs, pre-releases and recovery; align bilingual changelogs and versions with one preparation command, and generate consistent release notes and provenance.
- Add configurable left, right, down, double-tap and long-press music gestures for the selected player, session volume and configured apps, shortcuts or activities. Keep tap-to-open and upward dismissal. The default-on track-swipe switch blocks only horizontal track changes when disabled.
- Prevent paused music from flashing after returning Home, during an unfinished entrance or after a hidden playback activation. Only an actually displayed activity retains its two-second pause exit; repeated states and old snapshots cannot extend it. Fix prepared-window cancellation, session changes and re-entry during exit.
- Add left, center and right placement with a safe horizontal offset. Coordinate automatic width, text and icons with height, preserve actual status-bar overlap handling and keep the native StrongToast forehead suppressed.
- Select ten system feedback categories independently with resources registered on demand. New installs stay silent and upgrades retain previously enabled feedback. Notification takeover requires an explicit, initially empty app allowlist; exclusions take priority and other notifications remain native.
- Refine five settings groups and action summaries, complete ten maintained languages and backup contracts, and align feature documents, page maps and CN/TW ROM static evidence. Device behavior, audio routing and power measurements remain separate validation work.
- Require the protected reviewer for both automatic and manual publication, recheck before signing, and stop when review protection is missing.

## r14.25.1 — 2026-10-22

versionCode 246. Changes since r14.24.1:

- Rebuild settings pages around System, Home screen, Buttons and gestures, and Apps and maintenance. Unify grouped cards, headings and descriptions, improve search navigation, and collect restart actions on the home page.
- Add Dynamic Island for music, system states, brief notices and optional message notifications, with short and long forms, secondary activities, Vertical/Center animations, native ROM icons and familiar gestures. Hide status icons only when actually covered.
- Add full-width Dock backgrounds, symmetric sizing and wallpaper-tinted glass. Refine background transitions for recents and app returns, with native-material and tint fallbacks.
- Add top, center and bottom Toast positioning, with spacing and vertical offsets constrained to the safe screen area.
- Allow direct numeric entry by tapping slider values, with range, step and unit validation for precise adjustments.
- Connect vibration intensity to A14 native scaling. Correct volume timers, panel auto-hide, high-volume confirmation and audio-focus behavior.
- Improve state recovery for Control Center, the status bar and visualization, plus exception handling, restore records and resource cleanup for maintenance and privacy operations.

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
