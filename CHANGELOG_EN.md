# CustoMIUIzer A14 — Changelog

English | [简体中文](CHANGELOG.md)

Unreleased changes and retained releases; each release section consolidates changes since the previous retained version. Original source tags and APK provenance are preserved.

## Unreleased

- Prevent initial or bulk preference snapshots from overwriting concurrent updates or reporting readiness before publication. Retain the last valid setting after a temporary read failure, preserving acknowledged status-bar height overrides and restart-only switch semantics.

## r14.25.6 — 2026-10-25

versionCode 253. Changes since r14.25.3:

### User changes

This version is for locally installed, officially signed trials only and has not been published. It refines Dynamic Island, status-bar dimensions and native window interactions, combining valid changes from r14.25.4, the withdrawn r14.25.5 and this fix while preserving historical commits and binaries.

- Music direct-to-island now selects the native window-only return before desktop-icon lookup, then directs the validated app window to the island. Do not borrow or compress the desktop icon, or restore it only at the end. Keep the opt-in switch and explicit player selection. Prefetch the short-lived target during app opening and selected-player foreground changes for rapid Home returns.
- Reject premature media-return confirmation during PiP entry, cancellation and exit. Retain full hide / restore, edge stash / restore and independent center, left and right double tap actions. Recheck current PiP state before the first draw, cancel an unshown entrance and animate an already-visible island out. Whole-island suppression covers native PiP, system and application overlay windows; its switch requires a full device reboot. One bounded task query on the real Home edge recognizes declared auto-PiP before its window request; admission waits for that transition to settle. Disabling whole-island suppression allows a media island alongside PiP while leaving the video window under native control.
- Synchronize fixed status-bar height with system dimension resources, correcting stale-height layouts in third-party apps without expanding module scope. Keep the existing full-reboot requirement, application fullscreen visibility and native contrast protection.
- Expand island height to 32–64 dp, short width to 96–320 dp and long width to 120–400 dp, linking text, artwork, waveform and touch geometry. Manual wide mode lets time and battery yield or hide, with extra safety clearance for small icons. Also fix recycled settings descriptions and geometry when actual rotation has no resource callback.
- Strengthen notification-island keyguard, update and click identity checks while retaining native alerts on failed takeover. Stale media reads and old return confirmations cannot revive content.
- Refine experimental live-waveform amplitude, weak high frequencies and silence decay while keeping lightweight mode as the default. Fix obsolete wallpaper-capture failures retiring a replacement Dock glass backdrop, skip unchanged parameter writes and preserve the effect, motion and hidden-owner cleanup.

### Engineering notes

- Use NavStubView's native no-icon branch without desktop-icon lookup, snapshots or hiding. Retain the task Surface, native spring, callbacks and cancellation; never hijack a desktop icon already chosen by a late path. Tasks declaring auto-PiP stay native and cannot confirm an early island entrance. Refresh the short-lived return target during app opening and selected-player foreground changes so a quick Home gesture need not wait for the gesture-down request.
- Read only the published Shell scalar snapshot before drawing; coalesce rejected-frame handling into one controller-owned Main task and release it on disposal. Visible content follows the existing exit animation, while keyguard keeps immediate cleanup. Add no per-frame reflection, Binder, I/O or poller. The Home probe examines at most 16 tasks, with one controller-owned intent, serial query and expiry; cancellation, user changes, stale results and disposal invalidate it without polling. Retain at most one current-owner PiP request token and scalar identity; release on native state, cancellation or owner replacement, with a five-second deadline that rejects stale expiry callbacks.
- Manage one fixed overlay identity and four dimensions in system_server. The persisted enabled flag always remains false; only the running module enables its runtime state. Native state calculation restores stock dimensions on a full boot without the module. Replacement / removal retires only unreferenced module-owned files, with bounded retries and retirement records to prevent accumulation.
- Work around the confirmed stale system_server preference cache by retaining the existing authenticated, value-free invalidation. Read one acknowledged height through the non-exported, system-UID-only endpoint and include it in the same atomic preference publication. Do not mutate framework caches or recreate client listeners.
- Read native Session surface inventories only after visibility changes, session cleanup or an island-owner reconnect. Filter by user and default display, bound scans and snapshots, retain no windows, inject into no additional apps and add no poller. Preserve native PiP Surface, input, Shell-executor and task / user identity contracts.
- Keep Row / Sbn / binding transactions, pre-commit compensation and no alert replay after failed post-commit cleanup. Unknown or failing keyguard state blocks presentation. Bind geometry and gesture listeners to their real window / plugin owner, preserve empty native Insets frames and retain ordinary Home media timing.
- Reject stale Dock frames and failures by generation. Two fixed scalar buffers retain only current lens parameters and invalidate with shader replacement. Keep real wallpaper sampling and existing island animation. External algorithm research informed duplicate-update elimination; no third-party code or shader was copied.
- Base release review on stable asset digests, the exact source SHA, complete Git diff and bilingual content. Download documents read only published source, update in one non-forced commit and verify readback. Feature maps, process matrices and auxiliary summaries, filters, pagination and Schema / version checks retain existing generation and tool gates; do not restore deleted entries or automatically accept new risk fingerprints.
- Inherit valid device and performance evidence, target affected shared paths and validate source, Host, tools and APKs separately. Xiaomi 13 results do not establish all-ROM coverage. Fresh notification end-to-end events, Camera / Gallery preview appearance, Bluetooth, dual SIM, multiple users and long-term power remain separate checks; short-term PSS variation proves neither a leak nor memory savings.

## r14.25.3 — 2026-10-23

versionCode 248. Changes since r14.25.1:

### User changes

Refine Dynamic Island, Dock and notification interactions while preserving native animations and system security boundaries.

- Add five customizable music gestures for track controls, volume and shortcuts. Fix unwanted appearances after pausing and refine opening the current player and upward dismissal.
- Add left, center and right placement with safe horizontal adjustment, better cutout avoidance and touch edges. Select system feedback by category and notification takeover by app allowlist; keep lightweight waves by default with an experimental live mode.
- Synchronize Dock backgrounds with native unlock animations, improving movement, scale, opacity and wallpaper-sampling transitions in normal and full-width modes.
- Refine normal and floating-window notification clicks, expansion and auto-removal, withdrawal and rebinding, folding and colors. Preserve native lockscreen, user and critical-channel protections.
- Fix back-gesture area compatibility and status-bar gesture actions; refine dimension changes, screen-off seconds updates and state recovery.
- Refine island settings, configuration migration and backups. Save original system states before maintenance changes, verify results and retain recovery records on failure.

### Engineering notes

- Consolidate r14.25.2 and the subsequent island, quality and M03 branches against retained r14.25.1. Preserve Git history and the existing engineering roadmap without rewriting older provenance or validation facts.
- Retain five music actions, the track-swipe switch, pause-visibility leases, ten on-demand feedback categories, silent new installs, upgrade migration and allowlist/exclusion precedence. Align ten maintained languages, backups and settings subpages. Lightweight waves remain default; experimental capture requires the current user, confirmed media identity and one active audio track, releasing resources on failure.
- Contain island insets failures, release input owners, reuse unchanged layouts and invalidate density changes. Reliably journal maintenance recovery before system operations, validate package/user identity and actual state, strictly decode records and compensate failures without adding restriction targets.
- M03 retains native notification views, pipeline attachment, timers and refresh boundaries. Scope floating-window options to one launch and validate delayed callback identity. Retire global channel-protection overrides and the system_server feature identity while retaining legacy keys and backups. Keep one invalidatable palette per notification.
- Preserve native Dock geometry, animated opacity and interruption recovery in both modes. Fix no-argument back-width resolution and native side/bottom height branches; reuse the host Context for status-bar actions.
- Trust only main for signing and require protected review for candidates and publication. End automatic/manual requests cheaply for published versions; reuse exact main CI while retaining signatures, digests, provenance, sequential dual publication and recovery from the original candidate.
- Generate public notes from this User changes section and retain engineering details here. Cumulative preparation explicitly selects the superseded section and previous retained release. The ADB driver attempts and verifies locking on success, failure and interruption, retaining only relevant crash events.
- Keep host, static ROM and device conclusions separate in the existing roadmap. Claim no unmeasured performance or battery gains. First acceptance after removing old system_server hooks requires a full reboot; do not guess or reset historical notification permissions.

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
