# CustoMIUIzer A14 — Changelog

English | [简体中文](CHANGELOG.md)

Valid r14.25 changes are consolidated in r14.25.9 and r14.26 changes in r14.26.11. Superseded releases are retired; original records remain in Git and private archives. Editorial consolidation does not change APKs, signatures or build identities.

## Unreleased

### User changes

- Add an optional iOS 12 central volume HUD while retaining native volume, routing and expanded controls. Upgrades keep the side style and the saved narrow-bar choice.
- Add default-off iOS 10 five-dot cellular signals using native network state, hiding and no-service fallback, with Duo taking priority.
- Add a default-off iOS media presentation using the notification shade's native media sessions, actions and progress controls. Its display lifecycle stays independent of island media. Control Center media is not replaced.
- Add a default-off iOS battery presentation retaining native charging and power-saving states. Duo, battery hiding and existing battery styles take priority.
- Reorganize settings within pages and update search routes while preserving the four home entries, saved keys, defaults and backup format.
- Fix shared-hook and plugin-loading isolation and fallback after partial initialization. Improve cancellation and resource release for monitoring, key combinations and Recents menus.
- Refine DECK interception and window restoration while retaining native calls, skips and exceptions. Identical status-icon updates avoid redundant invalidation requests.

### Engineering notes

- Trim preparation for disabled features, verification routing and Agent Skills while retaining build, compatibility and release gates.
- Add independent behavior regressions, safe imported-log parsing and CodeQL source-extraction checks. Java/Kotlin analysis is available on demand; continuous Security alerts await a main-line setup transition.
- New presentations are partial adaptations. Real-SIM/dual-SIM/roaming, sustained landscape DECK and first composition, intermittent Dock flashes and long-term resource costs retain their acceptance limits. No unmeasured frame-rate, power or multi-ROM benefit is claimed.

## r14.27.3 — 2026-11-08

versionCode 304. Changes since r14.26.11:

### Release summary

- Add independently enabled iOS DECK task stacking for portrait and landscape on supported launchers.
- Refine DECK scaling, shadows and task reopening, and fix enlarged cards during quick app switching.
- Add the iOS home indicator with smoother black/white transitions and an easier-to-find switch.
- Add the iOS volume bar and an optional narrow default, with touch expansion, overscroll feedback and full controls.
- Refine island resizing, content transitions and app returns. Restore outer-aligned long-island captions while keeping camera clearance.
- Update Duo icons, fonts and layout, and simplify settings descriptions while preserving search and upgrades.
- Fix notification prompts, Control Center feedback, volume buttons and restoration of older settings pages.
- Restart the launcher and System UI after updating. DECK requires horizontal Recents; the home indicator requires gesture navigation and a visible system handle.

### User changes

- Fix DECK scaling interfering with app switching along the Home Indicator. Quick switch keeps system geometry; entering the deck and opening its cards retain DECK motion.
- Give DECK, Duo and iOS Home Indicator switches their full feature names for easier discovery and search. Shorten descriptions while preserving setup and restart requirements.
- Fix position and size jumps when a neighboring card fills the gap after dismissing the leftmost recents deck card.
- Reject stale card and window geometry during repeated orientation changes to improve position, size and direction handoff.
- Restore the launcher's native rotation policy, including landscape task containers inside portrait windows. Convert opening-window and neighboring-card coordinates consistently, with shared iOS motion in both orientations. Restart the launcher after updating.
- Align card and window scaling centers when native entry changes the pivot. Complete handoff checks for both landscape directions and unprepared layouts.
- Fix double scaling and window misalignment during swipe-up entry on newer launchers. Fold neighboring cards using the current native window, reducing extra spring lag and stepped position changes.
- Fix rear-card title and icon opacity on swipe-up deck entry without requiring another scroll.
- Follow native thumbnail opacity and fullscreen progress for card shadows, reducing premature shadows during swipe-up entry. Keep HyperOS 1 corners, screenshot clipping and shadow colors.
- Reuse the system's retargetable black/white gesture-handle tint animation, with a 250 ms ordinary gesture-mode transition. Preserve explicit window-handoff timing.
- Restore right alignment for long-island trailing content, mirrored at the outer edge in RTL layouts. Camera clearance constrains the available lane and clipping; resizing and translation preserve the outer margin. Keep existing measured or manual capsule widths without changing text alignment to fill space.
- Add visible horizontal expansion and recoil on first island appearance, music returning home, and new activity reception. Keep same-size handoffs from ending early, reduce vertical expansion and press enlargement, and preserve interrupted and reversed transitions.
- Add an opt-in iOS recents deck for Horizontal Recents on supported HyperOS 1 launchers, in portrait and landscape. Preserve native scrolling, task dismissal and window handoff.
- Add card sizing, independently scaled titles and icons with depth-based fading, and optional window motion. Select between two native window implementations by their interfaces instead of exact version numbers, including the older POCO spring interface; retain native timing when optional motion interfaces are unavailable.
- Fix card depth during dragging, dismissal, cancellation and return, window-to-card corner alignment, and premature card recycling. Add directional fling snapping; unsupported layouts restore native hit testing, input and scroll bounds.
- Add an opt-in iOS gesture indicator using native visibility, color and input. Admission now follows native drawing capabilities rather than device or build lists. Enable full-screen gestures, keep the system handle visible, and restart System UI.
- Add an iOS volume appearance with portrait and landscape support, continuous corners and native shadows. The thin bar moves toward the screen edge, stays thin during repeated key presses and expands for touch adjustment. Preserve native placement, volume steps, routing, mute state and expanded controls.
- Add “Start with thin bar” to appear thin and return to thin after touch adjustment. The footer transitions from three dots to one; fix the first tap taking focus instead of opening expanded controls.
- Follow native volume-fill animation and remove dark edge overpainting. Add maximum/minimum key feedback, touch overdrag and release recoil, with smoothly rounded thin-bar ends.
- Refine island resizing, idle forms, content changes and press feedback, including mid-animation reversals. Shorten activity handoff waits, retain existing content until the next activity makes contact, and add lateral shake and recoil when restoring ringing.
- Refine absorption entry and island acceptance. Remove the liquid dent, translucent leading edge and extra vertical compression, and avoid exciting the entire island again during acceptance. Preserve native return trajectories, springs, recall and picture-in-picture handoff.
- Prevent top cutout reservations from inflating automatic height and the camera mask. Restore the music island’s final exit timing to reduce translucent residue, and improve ordinary Toast fitting at larger font sizes and shorter island heights.
- Prioritize the actual function or mode in system feedback and show secondary state when space permits. Reveal text through its available area without an extra font-size pulse. Same-notification updates retain visible content while invalidating old click actions and continuing native privacy checks.
- Catch up initial foreground state for live activities; keep native foreground-change observation when singleton lookup is unavailable. Yield to native alarm/timer ringing in landscape and restore the island afterwards. Complete status-bar unbinding even if controller cleanup fails.
- Update Duo cellular, Wi-Fi, airplane and headphone arrangements, percentage/lightning spacing and four-dot arc symmetry. Preserve existing size, offset, spacing and headphone-scale settings. Handle small HD/roaming status icons by available fields, skip missing icons locally, and remove the unnecessary region restriction.
- Use a licensed rounded substitute for Duo numbers and network labels; make network-label weight follow the existing bold switch. Refine automatic size, central Wi-Fi proportions and state transitions.
- Avoid duplicate state messages after Control Center automatically collapses for a tile click, while preserving native feedback on failure. Keep the hide-mute/DND options effective after native volume-footer refreshes.
- Fix empty pages when restoring old settings categories and route volume and Recents options to their current pages. Simplify some feature names and complete translations while preserving preference keys, search, backups and upgrade compatibility.

### Engineering notes

- Recheck local iOS 18.6.2 binaries and A14 native call chains. Preserve native scrolling, window springs and lifecycle without adding animation clocks, listeners or threads.
- Prioritize energy use and response by removing dynamic blur and its effect cache from island content transitions. Keep transforms, clipping, crossfades and one outgoing display list on the existing clock, without additional filters or sampling tasks.
- Retire cached gesture endpoints with orientation ownership, remove unused island wave/strength handoffs, and reuse immutable receive profiles. Related behavior regressions and Develop / R8 / vital Lint passed, with daily use accepted by the user; pixel equivalence to iOS is not claimed.
- Recheck launcher mounting and landscape-container paths directly in four A14 ROMs, with master-switch, dependent-setting and search coverage. Daily use has been accepted by the user; no further device pass is requested. No new independent frame-rate or power measurements are claimed.
- Include effective changes from unpublished r14.26.13 and subsequent candidates, retaining Android 14 / SDK 34, arm64-v8a and libxposed API 102.
- Retain one island, finite animation clocks, native window springs, system input and safe fallback; resolve modern launcher window interfaces by exact signature. Private iOS materials, complete solvers and pixel parity remain incomplete.
- Publication requires main / exact CI, the historical certificate and dual-repository verification. Device coverage, measurements and unresolved issues are tracked in the refinement roadmap; Host tests do not establish device acceptance.
- Replace the withdrawn r14.27.1 / 301 and original r14.27.3 / 303 with internal version number 304, cumulative from r14.26.11. Original binaries, metadata and source tags are privately archived; withdrawn candidates are not promoted.

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
