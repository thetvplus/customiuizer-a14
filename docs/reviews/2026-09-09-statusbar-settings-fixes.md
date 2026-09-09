# Status bar placement and settings fixes

## Scope and provenance

- Baseline: `850cbe03378474ef299a667990052d59afa67f82` (`r14.21.5`, versionCode 212).
- Branch: `codex/fix-statusbar-settings-20260909`.
- Upstream comparison: [MonwF/customiuizer, SystemUI.java](https://github.com/MonwF/customiuizer/blob/dfd3a4de5201d058357c692250e35b1cb74edfb6/app/src/main/java/name/monwf/customiuizer/mods/SystemUI.java#L1479).
- Connected-device identity was read successfully: Xiaomi 13 / fuxi, Android 14 / SDK 34, increment `V816.0.7.0.UMCTWXM`, root shell.
- The supplied source directory has no Git metadata and differs from remote main. It was preserved; implementation uses a separate clone from the baseline above.

## Changes

1. **Move mobile/Wi-Fi icons left.** The preference was read but never applied to `leftIcons`. Restore the omitted conditional addition of `signalRelatedIcons`, also present upstream. Keep the A14 class names, existing controller registration/cleanup, right/left block lists, swap ordering and lockscreen handling.
2. **Control-center settings transition.** The international-ROM visibility adjustment previously ran in `onActivityCreated`, after the preference list was bound. This could make the preference adapter and group decoration disagree during the horizontal transition and trigger a vertical relayout. Apply that initial visibility in `onCreatePreferences` before the first bind. Keep the horizontal animator unchanged. Visual confirmation on the phone is still required.
3. **Switch-row feedback.** Bind a switch-specific row selector without the pressed background. The Switch still receives the parent pressed state; selected/activated/focused states and the existing search-highlight overlay remain available.
4. **Search coverage and navigation.** Generate entries for all 24 standalone preference pages as well as the four main categories. Coverage grows from 322 to 603 entries. Preserve each page's actual controller, resource, title, dynamic marker and target key, so a search result opens the correct page and existing target highlighting/scrolling. Include child settings and cache normalized title/path text. Match whitespace-separated terms across title and path, treating Wi-Fi and WIFI alike. Filtering preserves the existing display order.

The standalone-page table is explicit and build-time validated: every entry must have a real parent navigation item; every standalone preference XML is covered; each titled/keyed setting must appear exactly once on its page. The index size check still requires less than half the size of its source XMLs, now accounting for the newly indexed standalone sources.

## Validation and boundary

- JDK 25; Java/Kotlin Android target remains 17.
- `python tools/verify.py fast --changed`: passed.
- `python tools/verify.py full`: passed (compilation, unit tests, lint and repository gates).
- Additional `SearchPreferencePageTest` and `ModSearchIndexTest`: passed, including fresh controller instances, unsupported-controller rejection, context terms and Wi-Fi spelling.
- `python -m unittest discover -s tools/tests -p "test_*.py"`: 420 tests, passed with 5 existing skips.
- Search generator tests cover all 24 standalone pages and all 281 added entries; legacy entries retain their metadata/order/routes.
- `git diff --check`: passed.

ADB lost its USB transport immediately after the identity read. Reconnection attempts with the installed clients and an isolated Google Platform Tools 35.0.2 client did not establish a stable transport. Windows continued to enumerate an ADB interface intermittently; a targeted PnP restart was denied by Windows permissions. No module installation, settings mutation, SystemUI restart, ROM APK pull or post-patch device sampling succeeded. Do not interpret local tests or earlier ROM samples as current visual acceptance.

## Remaining device acceptance

The user requested a signed APK and will perform these checks on the phone. The artifact remains an explicitly marked debug build; formal release/publication was not requested.

1. Back up the installed APK and current module settings, then install the explicitly marked debug artifact with the supplied external signing key.
2. Enable moving mobile/Wi-Fi left, restart SystemUI, and verify left/right placement with Wi-Fi on/off, mobile signal, swap enabled/disabled, screen unlock and repeated status-bar attachment. Check hook errors and lifecycle cleanup.
3. Record entry/back navigation for System → Control center, confirming row Y positions remain stable throughout the horizontal animation.
4. Verify switch taps and long presses show widget feedback without a row press fill, and search highlighting still settles correctly.
5. Search Wi-Fi, Bluetooth and individual hide-icon options, plus multi-term path/title queries; open results, check their exact page/control, toggle and return to search.

ROM files, device logs, APKs and signing material stay outside Git. No push, main merge, tag or formal release is included in this task.
