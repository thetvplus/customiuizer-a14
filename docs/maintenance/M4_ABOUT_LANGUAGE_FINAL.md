# M4 About / Language — Final Gate Record

## 1. Gate

- M4_GATE = PASS
- AUDITED_AT = `4c0d18c9025a0202626f461527806c8c83852000`
- M4_TYPE = UI_OWNER_MOVE_ONLY

## 2. What changed (and what did not)

The language preference row was moved from the legacy About screen onto the
Main Settings home. No logic was removed or re-architected; ownership was
clarified so that `AboutFragment` is a plain non-Preference `Fragment` and the
`MainFragment` owns the user-facing locale control.

## 3. Persisted key

- `pref_key_miuizer_locale`
- `KEY_CHANGED = NO`

This remains the only user-facing locale preference key. The legacy
`pref_key_miuizer_locale_applied` marker is an internal derived value, not a
user setting.

## 4. UI location

- Resource: `app/src/main/res/xml/prefs_main.xml`
- Category: Main Settings
- Placement order:
  1. `pref_key_miuizer_settingsiconpos`
  2. `pref_key_miuizer_locale`
  3. `pref_key_miuizer_launchericon`

## 5. UI controller

`MainFragment.bindLocalePreference()` is responsible for:

- Building the locale display list with `AppLocaleController.buildLocaleDisplayData(context)`
- Showing the current normalized value as the preference summary
- Popping a confirmation dialog before persisting a change
- Calling `AppLocaleController.setUserLocale(prefs, newTag)` synchronously
- Rolling the preference summary back to the previous value if the commit fails
- Calling `AppLocaleController.exitApplicationAfterLocaleSave(act)` after a
  successful commit so the new language takes effect on the next launch

`MainFragment.onDestroyView()` dismisses and clears `localeConfirmationDialog`
to avoid a leaked window on configuration change or navigation.

## 6. Runtime owner

`AppLocaleController` (singleton object) remains the single source of truth for:

- `normalizeLocaleTag` — maps `null`, blank, legacy `"1"`, and unknown values to
  `"auto"`; supports the explicit tag list.
- The supported locale list (`auto`, `en`, `zh-CN`, `zh-TW`, `ru-RU`, `ja-JP`,
  `vi-VN`, `cs-CZ`, `pt-BR`, `tr-TR`, `es-ES`).
- `getUserLocale` / `setUserLocale` / `getUserLocaleForUi` preference access.
- `buildLocaleDisplayData` for the ListPreference entries.
- `apply()` / `apply(prefs, gateway)` — framework `LocaleManager` application,
  `APPLIED_LOCALE_PREF_KEY` fast path, and restore reconcile marker handling.
- The untouched-`auto` zero-work fast path.

`MainFragment` drives the ListPreference UI; `AppLocaleController` does not
import `ListPreferenceEx` and does not own the View layer.

## 7. About screen

- `AboutFragment` is a plain `Fragment`.
- It no longer loads a preference XML screen and does not reference
  `pref_key_miuizer_locale`.
- It owns donation / repository / contact rows and the version header.
- `AboutFragment.onDestroyView()` dismisses and clears `donateDialog`.

## 8. app_name

`<string name="app_name" translatable="false">` is preserved. Product name does
not change per locale.

## 9. Test coverage

- `AboutMigrationTest` asserts:
  - `AboutFragment` is a plain `Fragment`
  - `AboutFragment` does not use `findPreference`, `ListPreferenceEx`,
    `PreferenceScreen`, or `R.xml.prefs_about`
  - `prefs_about.xml` is removed
  - `MainFragment` uses `AppLocaleController.buildLocaleDisplayData`
  - `MainFragment` uses `AppLocaleController.setUserLocale`
  - The language row sits between settings icon and launcher icon in
    `prefs_main.xml`
  - `MainActivity` handles the AboutFragment toolbar back navigation
  - No Compose dependencies were introduced

## 10. Volume status

- `VOLUME_MUTE_DND_HIDE_ENGINEERING = PASS`
- `VOLUME_MODE_COLOR_ENGINEERING = PASS`
- `VOLUME_SHARED_VISIBILITY_ENGINEERING = PASS`

The whole-root reversible ownership, shared divider/container visibility, and
actual visual-owner color corrective for the A14 volume panel are engineering
complete. They are recorded in:

- `docs/audit/A14_VOLUME_MODE_SHORTCUT_IDENTITY.md`
- `docs/rom-intelligence/FUXI_HYPEROS1_AI_HOOK_CORPUS_2026-08-14.md`
- `docs/rom-intelligence/FUXI_HYPEROS1_HOOK_TARGETS_2026-08-14.json`

`VOLUME_DEVICE_ACCEPTANCE = PENDING_FINAL_APK`

Final real-device visual verification (hide states, color, no divider/empty
shell/shadow) requires the signed release APK built from this maintenance tree.

## 11. Authorization

- M4_SELF_ASSESSMENT = PASS
- M4_INDEPENDENT_GATE = PASS
- VOLUME_MUTE_DND_HIDE_ENGINEERING = PASS
- VOLUME_MODE_COLOR_ENGINEERING = PASS
- VOLUME_SHARED_VISIBILITY_ENGINEERING = PASS
