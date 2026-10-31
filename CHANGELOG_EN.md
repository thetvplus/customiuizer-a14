# CustoMIUIzer A14 — Changelog

English | [简体中文](CHANGELOG.md)

Changes for maintained releases. Valid r14.26.1 and r14.26.3 changes are consolidated into r14.26.5, and the superseded releases have been retired. Valid changes from the retired r14.25.1 and r14.25.3 releases are consolidated into r14.25.5; the original records remain in Git history.

## Unreleased

- Tighten sender boundaries for system actions, screenshot notifications and battery previews while retaining the A14 screenshot sender's signature-permission contract. Restart the host or device after updating to register the new boundaries.
- Address a known Guava vulnerability in build and test dependencies without adding production runtime dependencies.

## r14.26.7 — 2026-10-31

versionCode 267. Changes since r14.26.5:

### User changes

- Trusted Wi-Fi networks now use cached scans, system broadcasts and on-demand refresh, with request cooldowns across rapid navigation, rejected scans and wait timeouts. Trusted Bluetooth devices replace the 15-second loop with page entry, system events and manual cache requests; empty results also end the wait.
- Move backup filtering, encoding and file writes off the UI thread and share a single operation slot with restore. Finish started file work and close streams when a page is destroyed, preventing overlapping writes and stale success messages while retaining the backup format and system document picker.
- Defer language refresh while Fragment state is saved and publish search indexes only after preparation completes. Restore editor drafts by stable preference keys and allow complete text and numeric forms to scroll when the landscape keyboard occupies most of the screen.
- Continue opening trusted-network lists after their permissions are granted, and resume adding the current network in Wi-Fi automatic settings. Directory selection accepts confirmed local storage folders; cancellation or invalid selection preserves the previous setting.
- Report credential-confirmation success only from the system result and restore pending requests without relaunching them. Ordinary saved Activity state no longer implies authentication is pending.
- Foreground monitoring no longer installs global Hooks from each network-speed controller constructor. Base initialization and Application.attach share ownership of the actual target, while audio, alarm and brightness callbacks follow their current instances. Correct the nullable Boolean signature that could hide Control Center tiles with custom columns, and rebind each actual left adapter after recreation. Restart the device after updating.
- Validate screenshot folders against their actual storage volume and MediaStore image-directory rules, letting the system create child folders. Unsupported custom folders retain the native destination. Preserve existing privileged host paths and keep filename extensions and MIME types consistent.

### Engineering notes

- Retain Android 14 / HyperOS 1, SDK 34, arm64-v8a, libxposed API 102 and the historical signing identity. Preserve compact-resource processing and existing failure isolation.
- Bound successful-build diagnostic summaries, prioritize entries requiring review and report omitted counts. Failed commands still retain full output; zero warnings or deprecations is not a goal.
- Add regressions for request counts, file-operation ownership and method installation. Host results and reduced source work do not establish device CPU, GPU or power gains.

## r14.26.5 — 2026-10-31

versionCode 265. Changes since r14.25.9:

### User changes

- Migrate the settings interface to Compose with consistent lists, search, selectors, sliders, color and action editors, and state feedback. Preserve settings keys, backups and feature behavior. Group app behavior, Wi-Fi and VPN automation under “System → Apps & automation”.
- Keep home search collapsed by default, with a dedicated view, automatic focus and highlighted matches. Fix retained focus and keyboard flashing across back navigation and background recovery. App selectors use prepared rows directly when the query is empty, avoiding unnecessary background dispatch and empty-list handoffs.
- Use consistent neutral light / dark pages, toolbars and cards, with Material 3 dynamic colors for buttons, switches and sliders. Match system bars to the page background, align overflow typography and colors, add a restrained restart-submenu transition, and clear pressed feedback as pages or menus leave so a dark click state does not travel with them or linger.
- Fix “null” titles in language, Settings-entry and other selection dialogs. The shared title fallback also covers text and multi-selection editors. Use 16sp primary text for choices, with system font scaling and clearer contrast.
- Select the current numeric value when manual input opens, leave space above the keyboard, and allow long dialog content to scroll. Cancel continues to discard edits.
- Add the missing section heading for Apps & automation and align settings grouping. Restore native edge stretching and use motion-aware text rendering to reduce glyph and spacing jumps during scrolling, rebound and navigation.
- Restore native Dock blur opacity after hiding or locking, and stop resubmitting identical opacity in a stable state.
- Add “System → Apps & automation → Wi-Fi automatic settings”: use SSID, optional BSSID and priority to control Do Not Disturb, ringer mode, auto rotation and VPN. Restore only owned state on exit and respect observed manual changes. Grant the network-identity permissions and restart the device after first enabling.
- Add “System → Apps & automation → App behavior”: enable sensor rotation, media-volume memory, fixed or system-controlled brightness, and limits on or suppression of app brightness requests for selected apps, with exact Activity exceptions. Each option is off by default; restart the device after first enabling.
- Remove Dock “Light glass” while retaining Solid panel and Frosted glass. Retired selections fall back to the panel. Remove its gradient rim, extra drawing and blur / power observers while preserving layout, editing, dragging and unlock behavior. Restart Launcher after updating.
- Retain bounded Wi-Fi setting restoration records after a failed scene exit until readback confirms success; respect manual changes and user switches. Recover network tracking after overflow through a complete trusted snapshot, and prepare rotation and brightness Hook interfaces independently. Fix app-behavior and Wi-Fi changes remaining stale on frameworks that cache old settings.
- Restore smoother Island waveforms with display updates synchronized to screen frames and the earlier music / recorder sampling cadence. Preserve hidden-state capture release, measured-silence / stable-state frame suspension, pause and power-saving guards. Fix lightweight waveforms remaining frozen after system animations are re-enabled.
- Maintain Island opacity only for occluded nodes, skip repeated occlusion queries when geometry is unchanged, and tighten the texture budget for full transition surfaces. Preserve native gestures and animation handoffs.
- Stop weather-specific subscriptions and queries when no clock uses weather content, and skip updates for a hidden second clock.
- Coalesce duplicate status-bar network-speed mirror deliveries and update charging-text layout only when content or layout conditions change. Avoid unrelated brightness reads and argument-array copies in touch handling.
- Clear stale recording activity levels after silence and reset the previous envelope after a failed read instead of retaining an incorrect peak.

### Engineering notes

- Use stable Kotlin 2.4.21, JDK 27 and Java / Kotlin bytecode 26, with updated Gradle / AGP and compileSdk 37. Runtime support remains Android 14 / HyperOS 1 without raising minSdk or targetSdk.
- Remove settings layout XML, legacy list adapters, View binding and custom color widgets. Generate typed settings models from validated JSON while preserving AndroidX Preference persistence and dependency semantics.
- Commit authentication state only after native success. Preserve native exception boundaries and activate screenshot and input-method Hook groups atomically so failures do not leave partial installations.
- Remove installation sampling, unused reflection helpers, an obsolete threshold implementation and unreachable resources. Keep test helpers and temporary samples in test sources, preserve useful behavior regressions. Only settings activate Compose; host Hooks retain native View / Canvas rendering, and Release excludes test dependencies.
- Keep Android 14 / HyperOS 1 support, the existing app identity and signing certificate; new rules are off by default. Performance conclusions are limited to measured scenarios; Host tests and reduced source work do not establish device CPU / GPU or power gains.
- Remove duplicate search state, the obsolete state machine and four unused backgrounds. Preserve AndroidX persistence, dependency notifications and host resources.
- Use API 102 Chain directly for Dock measurement and content-layout hooks, removing per-call before / after wrappers while preserving single native execution, exception identity, fatal propagation and installation lifetime.
- Remove lightweight-glass paths, unused G4 path construction and tests of the retired behavior, while retaining the shared Island curvature integral. Use native TextMotion and release ripple nodes with Fragment lifecycle changes; add no persistent work, extra layers or third-party UI library.

## r14.25.9 — 2026-10-29

versionCode 258. Changes since r14.25.7:

### User changes

- Add “System → Advanced settings → VPN auto switching”: select multiple trigger apps and one VPN client or saved system IKEv2 profile. Automatic disconnect has its own switch and a 0–30 second delay, defaulting to 3 seconds. Preserve existing, manual, always-on and lockdown connections. Restart the device after first enabling; later rule changes apply live. Actual connection support depends on the chosen client.
- Correct lock-screen artwork centering, content/options invalidation, stale screen-off requests, reference release and publication-size checks. Static Dock wallpaper reuses its image during movement and refreshes once after settling.
- Validate the complete native keypad before scrambling lock-screen or app-lock PIN keys. Preserve native construction/layout failures, original digit clicks, fixed controls and authentication policy.
- Stabilize Island activity handoffs, repeated taps and native returns; reuse valid content and drawing preparation to avoid duplicate submissions.
- Prevent an immediate clock correction from fading the entire live-activity layer out again during Island entry. Running stopwatches, timers and similar clocks retain their entry progress and prepared icon; pauses and source/session changes keep their existing handoff.
- Fix folder-icon blur installation on POCO Launcher 5.39.40.11641 / 11739, whose native interfaces differ from the older implementation. Connect the native background-sampling container for verified 4.39.14.7603 and the above 5.39 builds. Use the native 1×1 / 2×2 folder policy and a darker black blend layer with native opacity for closed folder icons. Preserve system-blur and default-folder restrictions and native interactions; the exact 7601 build and final appearance still require device testing.

### Engineering notes

- Integrate VPN, ROM retrieval tooling and cumulative refinement branches. Offline source/ROM lookup remains explicit and bounded, with A14 and reference-only samples separated; it is not part of module runtime. Branch integration does not establish public release or device-performance acceptance.
- Add separate POCO ABI routing, rollback, native state-publication, size-specific theme and material-argument regressions, with bounded gate and native-render-request diagnostics. Handle inlining only for verified native callers during installation; open-folder desktop blur keeps its native behavior.

## r14.25.7 — 2026-10-28

versionCode 256. Changes since r14.25.5:

### User changes

- Select calls, alarms, timers, stopwatch, recording and privacy activity together using native events. SMS and calendar retain notification privacy rules. Clocks update while visible; ending source processes restores media or the idle island.
- Present one current activity, entering directly as a short or long island according to its content. Reuse the window across sources; one upward swipe dismisses the source and downward swipes do not expand details. An optional still idle island remains when no activity is shown.
- Refine content fades, local text blur, track artwork flips and damped horizontal feedback. Music return responds subtly to native swipe strength while preserving configured music actions.
- Use a short island for music and all ongoing system activities, and a long island for messages with distinct body text. Derive a 36–40 dp automatic height from the camera cutout; idle width starts at 100 dp, with content widths and offsets bounded by the cutout and safe area.
- Follow the ROM audio capture limit, interpolate the waveform at about 30 Hz, and reject small noise changes. Support mirrored Telegram / HyperOS media sessions, retain one same-app audio track while it remains active, and reduce handoff flicker.
- Exclude confirmed floating-app media sources rather than hiding every activity. Other music, approved messages and system feedback remain eligible.
- Add optional charging and low-battery feedback. Suppress the native low-battery dialog only after the replacement is visibly acknowledged.
- Keep one visible Dock material with a shared native rounded-rectangle contour and analytic optical edges; reserve G4 for inset decoration and reduce repeated static-wallpaper captures. Add another 0.5 dp to Duo automatic sizing and balance native row height with optical centering.
- Fix asymmetric morphs and diagonal unlock motion with one stable canvas, preserving current position and velocity. Similar sizes exchange content without extra bounce; the notification shade retains a still idle island with its touch region disabled.
- Resolve upward intent before clicks; short upward drags only rebound. Tapping the idle island no longer implicitly opens notifications; its documented downward gesture remains.
- Preserve current artwork and a still waveform while the same player buffers or loads a track. Commit new content after playback starts, with bounded grace for ambiguous pauses.
- Simplify Island names and its main page, with dedicated size, music, message and system-feedback pages. Return the global prompt style to the system directory; app and action selection follow native components and view lifecycles.

- Unify continuous dragging and velocity handoff, presenting current content directly without a manually expanded detail step. Refine text hierarchy for notifications, ongoing activities and feedback. Group return gestures together.
- Rebalance Island entry, content handoff, kneading and exit. Absorption responds immediately while its receiving body completes a full settling gesture independently of page speed. Allow more elastic travel within physical safe bounds, retaining the smaller lower intake and continuity across first entry and reopening. Kneading keeps its anchor, resistance, momentum and camera clearance; reveal artwork earlier and balance short two-line captions.
- When rapidly returning to a source that is still visible, cancel its unseen replacement and recover from the current opacity, avoiding a repeated fade-out and content rebind. Artwork flips retain their existing handoff.
- Support landscape presentation with side-cutout and system-safe edges, a tighter width cap and stale native-return wait cleanup after rotation. Retain lockscreen and notification-shade safety.
- Open notifications with distinct body text directly in the long form. Preserve the current window and visibility across Home, media and system-activity handoffs; fix recursion when the shared progress clock ends and clear stale timer activity when its service is destroyed.
- Add an independent folder-icon background blur switch, off by default and requiring a Launcher restart after changes. MIUI and POCO use their verified native folder paths while retaining hardware, system-blur and default-icon restrictions; appearance on each ROM still requires device acceptance.
- Stop invalid Dock captures outside the visible region and prepare again on return. Refresh capture policy when wallpaper type changes, and remove stable transparent full-width backgrounds from frame observation.
- Prevent vertically dominant diagonal swipes from triggering Dock horizontal actions. Roll back a partially installed Hook group and preserve native touch calls when an observer fails.
- Refine independent notification-shade and Control Center date formats, app-lock timeout, installer app details, volume-key and IME dismiss-button boundaries, retaining native behavior on failure.
- Keep preference descriptions visible beside current values and fix recycled warning colors and separators. Unify subpage arrows, search routes, page return and numeric-input boundaries.
- Restore native widget spacing and title parameters at default settings. Refresh only relevant content for app renaming and theme or configuration updates, retaining icon and touch bounds.

### Engineering notes

- Native window events publish bounded current snapshots. Audio, notification and animation resources retain explicit owners and release paths; no new recording permission, polling service or animation library.
- Power saver stops Island capture and optional motion. Static Dock capture stops after its bounded native-animation catch-up period.
- r14.25.7 / 256 is released; the APK and build metadata in both Releases identify the official binary. Earlier local trials retain their own identities. Unverified landscape, POCO appearance, other-ROM and sustained-performance coverage remains documented separately.

## r14.25.5 — 2026-10-25

versionCode 254. Changes since r14.24.1:

### User changes

- Reorganize settings into System, Home screen, Buttons and gestures, and Apps and maintenance. Unify groups and descriptions, improve search navigation, and collect restart actions on the home page.
- Enter slider values by tapping the number, with range, step and unit validation.
- Add Dynamic Island for music, system feedback, brief notices and optional message notifications, with short and long forms, secondary activities, and Vertical or Center animations.
- Add left, center and right placement, horizontal adjustment and cutout avoidance. Set height to 32–64 dp, short width to 96–320 dp and long width to 120–400 dp, with matching text, artwork, waves and touch areas.
- Improve short/long transitions and content handoffs. Fix blank gaps, first-frame shrink jumps, abrupt position changes and manual short widths incorrectly selecting the long form.
- Add five customizable music gestures for tracks, volume and shortcuts. Refine pause visibility, opening the current player and upward dismissal.
- Use the native icon-free animation for direct music return to the island, fixing rapid returns and desktop-icon issues. This option is off by default and requires selecting a player.
- Refine picture-in-picture entry, cancellation, exit, edge stashing and restoration. The floating-window hiding option hides the whole island; turning it off allows music and PiP to coexist. A full device restart is required after changing this switch.
- Select system feedback by category and message notifications by app allowlist. Refine lockscreen restrictions, update and click checks, retaining native alerts when takeover fails.
- Keep lightweight music waves by default and offer an experimental live mode with improved amplitude, quiet high frequencies and silence decay.
- Add full-width Dock backgrounds, symmetric sizing and wallpaper-tinted glass, with liquid glass and a frosted-glass fallback.
- Follow unlock, recents and app-return animations for Dock movement, scale, opacity and wallpaper sampling. Prevent an old sampling failure from degrading a new background.
- Position Toasts at the top, center or bottom with spacing and offsets inside the safe area. Brief notices can use the island; complex or long content retains ordinary Toasts.
- Apply fixed status-bar height to system dimension resources to correct outdated-height layouts. Preserve independent content offset, app fullscreen visibility and native contrast protection. A full device restart is required after changing the height.
- Refine normal and floating-window notification clicks, expansion, auto-removal, withdrawal and rebinding, folding and colors. Preserve native lockscreen, user and critical-channel protections.
- Connect vibration strength to A14 native scaling. Fix volume timers, panel auto-hide, high-volume confirmation and audio-focus handling.
- Fix back-gesture area compatibility and status-bar actions. Improve recovery after rotation, dimension changes and interrupted gestures.
- Improve state recovery and resource cleanup for Control Center, the status bar, screenshot-hidden overlays and visualization, preserving screen-off update boundaries.
- Refine configuration migration, backups and maintenance recovery. Save original states before maintenance, verify results and retain recovery records on failure.
- Prevent startup or bulk preference refreshes from overwriting newer values with an old snapshot. Keep the last valid settings when a temporary read fails.

### Engineering notes

- Reduce whole-array copies in read-only hooks while preserving argument writes, original calls and exception behavior. Host allocation comparisons establish no device performance or power gain.
- Invalidate, cancel and release animation, media, PiP, notification and Dock state with the owning window or controller so stale callbacks cannot restore old content.
- Automate dual-repository publication without human approval. Publish only the official APK and build-metadata.json, retaining the historical certificate, source identity, matching assets and recovery from the original candidate.

See the [current refinement state](docs/maintenance/A14_REFINEMENT_ROADMAP.md) for verified scopes and pending device checks.

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
