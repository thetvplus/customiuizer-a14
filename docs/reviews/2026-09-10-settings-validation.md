# Settings and left status-bar icon follow-up

## Baseline and scope

This follows `ba7b60d8423c5974ca2aa4e5a8e4d80d2dbf9d5e` on the existing
`codex/fix-statusbar-settings-20260909` branch. Remote main was rechecked and
remains `850cbe03378474ef299a667990052d59afa67f82` (`r14.21.5`). The supplied
directory contains an unversioned `r14.21.6` snapshot; it is preserved separately.
This artifact is `r14.21.7-debug`, versionCode 214, for manual device validation.

## Changes and evidence

- The light/dark palette and styles match the remote `r14.21.5` resources exactly.
- Preference group decoration now follows the displayed adapter count and uses
  the public `PreferenceGroup.PreferencePositionCallback` to place categories.
  Ordinary rows share the same card chrome, so only categories need lookups.
  The previous comparison with the changing preference tree could temporarily
  return no decoration, removing every inset during navigation. Initial
  control-center visibility is still configured before the first bind. No
  restricted library API or additional observer/cache is used.
- The left icon container now explicitly uses wrap-content width, match-parent
  height, centered layout gravity and unclipped children, matching the native
  container layout. It copies native container padding and native manager icon
  height before registering the new icon group.
- Two controls below the mobile/Wi-Fi-left toggle adjust the whole left icon
  group: size 75–125% in 5% steps, and vertical offset -6–6 dp in 0.5 dp steps.
  Defaults preserve native size and zero offset. Values restored from settings
  backups are bounded. Changes require restarting SystemUI. No new hook,
  observer or listener is introduced.
- Search includes standalone pages and child switches, while excluding six
  statically hidden unavailable settings. With the two new controls, the
  generated index contains 599 entries. Path/title keyword combinations and
  Wi-Fi/WIFI normalization are retained.
- Switch rows retain switch feedback and search highlighting without a pressed
  row fill, as implemented in the preceding commit.

Layout and field names were checked against the previously captured fuxi
HyperOS 1 / Android 14 SystemUI APK: `system_icons.xml`,
`MiuiStatusIconContainer`, `MiuiPhoneStatusBarView`, `MiuiIconManagerFactory`,
`StatusBarIconController`, `StatusBarMobileView` and `StatusBarWifiView`.
The earlier sample and local checks do not establish current device acceptance.
The latest successful device identity read reported the same ROM increment,
`V816.0.7.0.UMCTWXM`; subsequent ADB transport failed.

## Validation

- `python tools/verify.py full`: passed with JDK 25, including repository gates,
  compilation, lint and 2,406 JVM tests (zero failures/errors, two existing skips).
- Python tool suite: 420 tests, zero failures and five existing skips. The 15
  localization contract tests were rerun after the final literal-percent resource
  correction and passed.
- Generated searchable entries: 599; all 24 standalone pages are covered.
- Light/dark colors and styles: exact match against the `r14.21.5` baseline.
- `git diff --check` and staged diff check: passed.

The final handoff records debug APK provenance, package metadata and signature
verification separately. The release APK task is not used. External signing
material, APKs and ROM evidence remain outside Git.

## Manual acceptance

1. Install the signed debug APK and verify both light and dark settings colors.
2. Enter and return from System → Control center several times, checking that
   row positions stay stable throughout the horizontal transition.
3. Search individual hidden-icon switches and multi-term path/title queries;
   open results and confirm the intended control is highlighted.
4. Move mobile/Wi-Fi left and restart SystemUI. Check native defaults, then size
   and offset controls with Wi-Fi on/off, mobile signal, swap mode and unlock.
   These controls affect all icons placed in the left icon group.
5. Check switch feedback without a pressed row background.

No push, merge, tag or formal release is part of this task.
