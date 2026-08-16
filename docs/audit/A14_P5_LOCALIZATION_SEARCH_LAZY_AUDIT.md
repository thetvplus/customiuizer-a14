# A14 P5-A0/B/C — Localization / Search / Lazy Loading Final Audit

> Scope: docs/test/resources. No production source or lazy/search runtime changes. No APK built or installed.

P5-A0 base: `ba504526d9e65299959d9b66eee52780bc3c2642`
P5-C base: `79c9143d3144f8444d180ba573c0095a9fbb9216`
Final SHA: `04c8f7a658ea2b36aa7b4d74720c65060ab2f6dd`

## 1. Base / scope

- Branch: `devin/a14-final-polish-r14.20.0`
- Authoritative canonical XML: `app/src/main/res/xml/prefs_{system,launcher,controls,various}.xml`
- Generator: `tools/generate_preference_artifacts.py`
- Generated output: build-time `app/build/generated/res/preference-artifacts/main` (not committed)
- Resolver: `app/src/main/java/tv/withaibuild/customiuizer/utils/PreferenceResourceResolver.kt`
- Search runtime: `app/src/main/java/tv/withaibuild/customiuizer/utils/SearchNavigation.kt`, `Helpers.parseModSearchIndex`
- Formal string sets: `values` (base) + 9 localized (`values-zh-rCN`, `values-zh-rTW`, `values-cs-rCZ`, `values-es-rES`, `values-ja-rJP`, `values-pt-rBR`, `values-ru-rRU`, `values-tr-rTR`, `values-vi-rVN`)

## 2. Canonical / generated parity

| Metric | Value |
|---|---|
| CANONICAL_FUNCTIONAL_PREF_COUNT | 347 |
| GENERATED_LAZY_PAGE_COUNT | 36 (32 split pages + 4 category selectors) |
| EXPLICIT_SUB_ROUTE_COUNT | 32 |
| CATEGORY_SELECTOR_ROUTE_COUNT | 4 |
| CANONICAL_FALLBACK_ROOT_COUNT | 4 |
| GENERATED_MISMATCH_COUNT | 0 |
| `test_generate_preference_artifacts` | 7/7 PASS |

Top-level category selectors are derived directly from canonical categories. Every generated lazy page contains one category slice. No sibling leakage.

## 3. Lazy routing matrix

| Metric | Value |
|---|---|
| LAZY_ROUTE_COUNT | 32 explicit sub routes |
| LAZY_ROUTE_MISSING | 0 |
| CATEGORY_SELECTOR_INFLATES_FULL_PAGE | NO |
| SUBFRAGMENT_LOADS_ONLY_RESOLVED_XML | YES |

`PreferenceResourceResolver` covers all 32 generated category/sub routes (14 system, 6 launcher, 5 controls, 7 various) plus the 4 fallback roots. No missing or stale route.

## 4. Search inventory

| Metric | Value |
|---|---|
| SEARCH_EXPECTED_COUNT | 322 |
| SEARCH_INDEX_COUNT | 322 |
| SEARCH_MISSING_COUNT | 0 |
| SEARCH_EXTRA_COUNT | 0 |
| SEARCH_DUPLICATE_KEY_COUNT | 0 |
| SEARCH_WRONG_ROUTE_COUNT | 0 |
| SEARCH_REACHABLE_COUNT | 322 |
| SEARCH_UNREACHABLE_COUNT | 0 |
| SEARCH_HARDCODED_TITLE_COUNT | 0 |

All search titles, category titles, and breadcrumb titles in `mod_search_index.xml` are `@string/...` references. No literal text.

`SearchRouteResolver` only accepts the four known root categories and normalizes blank sub to `null`; `SearchStateMachine` clears search state on return.

`SEARCH_RUNTIME_INFLATES_ALL_PAGES = NO`.

## 5. Localization

| Metric | P5-A0 Reported | P5-B Corrected Scope | P5-C Pre-Fix | P5-C Final |
|---|---|---|---|---|
| FORMAL_LOCALIZED_LOCALE_COUNT | 9 | 9 | 9 | 9 |
| FORMAL_STRING_SET_COUNT | 10 | 10 | 10 | 10 |
| USER_VISIBLE_REQUIRED_STRING_COUNT | 755 | 751 | 815 | 815 |
| UNIQUE_MISSING_KEY_COUNT | 117 | 114 | 124 | 0 |
| TOTAL_MISSING_KEY_LOCALE_PAIRS | 414 | 351 | 420 | 0 |
| FORMAT_MISMATCH_COUNT | 0 | 0 | 0 | 0 |
| HARD_CODED_VISIBLE_TEXT_COUNT | 3 | 0 | 0 | 0 |
| SUSPICIOUS_ENGLISH_COPY_COUNT | - | - | - | 0 |

P5-A0 originally reported 755 required strings, 117 unique missing, 414 missing pairs. P5-C reconciliation re-derived the A0 set as 758 referenced strings due to differences in code-side inclusion, but the original report numbers above are preserved for audit traceability.

### 5.1 A0 → P5-B scope reconciliation

P5-A0 reported 414 missing pairs (117 unique keys). P5-B's corrected XML/layout/menu test reported 351 missing pairs (114 unique keys).
The 63 dropped pairs are exclusively `translatable="false"` base resources multiplied by 9 formal locales:

- `app_name`
- `array_global_toggle_nfc`
- `array_global_toggle_wifi`
- `system_statusbaricons_gps_title`
- `system_statusbaricons_vpn_title`
- `system_statusbaricons_vowifi_title`
- `system_statusbaricons_nfc_title`

`DROPPED_FALSE_POSITIVE = 63`.
`DROPPED_REAL_MISSING = 0`.

### 5.2 P5-B completed localization hygiene

1. `app/src/main/res/xml/prefs_system_hideicons.xml` `SIM 1` / `SIM 2` moved to `@string/system_hideicons_sim1_title` / `system_hideicons_sim2_title`.
2. `app/src/main/res/layout/fragment_selectcolor.xml` `HSV` moved to `@string/selectcolor_hsv`.
3. `values-ru-rRU/strings.xml` `controls_hide_ime_dismiss_button_title` improved to `Скрыть кнопку скрытия клавиатуры`.

### 5.3 P5-C pre-fix scope expansion

P5-C expanded the required set with Kotlin UI-facing `R.string.*` references.

- P5-B corrected missing: **351** pairs
- ADDED_CODE_UI_MISSING: **69** pairs
- P5_C_PRE_FIX_MISSING: **420** pairs (124 unique keys)

### 5.4 P5-C final closure

All 420 pairs were filled from the base string, page context, and existing locale terminology. No English placeholders were used. Translations were generated per-locale and then applied to the 9 formal `strings.xml` files.

Per-locale additions:

| Locale | Added pairs |
|---|---|
| values-zh-rCN | 9 |
| values-zh-rTW | 118 |
| values-cs-rCZ | 31 |
| values-es-rES | 71 |
| values-ja-rJP | 17 |
| values-pt-rBR | 17 |
| values-ru-rRU | 35 |
| values-tr-rTR | 105 |
| values-vi-rVN | 17 |

Total added: **420**.

### 5.5 Placeholder contract

`FORMAT_MISMATCH_COUNT = 0`. The P5 localization test now normalizes placeholders by index, supports `%%` literal percent, skips `formatted="false"` strings, and correctly rejects missing, swapped, or type-changed placeholders.

## 6. Text-layout static risk

| Metric | Value |
|---|---|
| STATIC_CLIP_RISK_COUNT | 0 |

No `singleLine=true`, `maxLines=1`, or `lines=1` found on user-visible `PreferenceEx` rows. Longest base strings are summaries displayed in scrollable/multi-line areas.

## 7. Device smoke

| Metric | Value |
|---|---|
| ADB state | no devices/emulators found |
| DEVICE_LAYOUT_SMOKE | DEFERRED_NO_DEVICE |
| DEVICE_CONFIRMED_CLIP_COUNT | 0 |
| DEVICE_VISUAL_UNCERTAIN_COUNT | N/A |

`adb devices -l` returned no attached devices. Static layout risk is 0; runtime smoke not performed. This is a **deferred acceptance item**, not a localization blocker.

## 8. Final-polish feature audit

| Feature | Canonical | Lazy page | Search | Locales |
|---|---|---|---|---|
| USB Default Function | `pref_key_system_usb_default_function` | `prefs_system_other.xml` | indexed | n/a |
| P4 Hide IME Dismiss Button | `pref_key_controls_hide_ime_dismiss_button` | `prefs_controls_navbar.xml` | indexed | all 10 locales (Russian title and summary wording improved) |

| Metric | Value |
|---|---|
| FINAL_POLISH_LAZY_LEAK_COUNT | 0 |
| USB_DEFAULT_LAZY | PASS |
| P4_IME_DISMISS_LAZY | PASS |
| P4_IME_DISMISS_SEARCH | PASS |
| P4_IME_DISMISS_LOCALES | PASS |

## 9. Findings

### P0

None.

### P1

None blocking.

### SKIP

- No incorrect lazy-page runtime inflation.
- No search runtime loading of all `PreferenceScreen` pages.
- No device-confirmed clipping.
- `DEVICE_LAYOUT_SMOKE = DEFERRED_NO_DEVICE` is a deferred acceptance item, not a localization blocker.

## 10. Historical P5-B status — CLOSED BY P5-C

P5-B originally **HOLD** because 351 key-locale pairs were missing and no reliable translation source existed in the repository, A13, or maintenance branch.

P5-C closed this by:

1. Correcting the test scope (no false positives, no non-translatable leakage).
2. Expanding the required set to include code-side `R.string.*` UI references.
3. Generating and applying 420 missing translations across all 9 formal locales.

| Check | P5-B state | P5-C final |
|---|---|---|
| Hard-coded visible text | 0 | 0 |
| Russian P4 title | fixed | fixed |
| Russian P4 summary | not addressed | fixed |
| P2 structural test | PASS | PASS |
| P2 test drift for P4 | exempted | exempted |
| P5 localization contract test | added, FAIL on missing | PASS |
| `MISSING_TRANSLATION_COUNT` | 351 | 0 |

## 11. P5-C final status

| Check | State |
|---|---|
| P5_A0 | COMPLETE |
| P5_B | CLOSED_BY_P5_C |
| P5_C | PASS_CANDIDATE |
| LOCALIZATION_MISSING | 0 |
| FORMAT_MISMATCH | 0 |
| HARD_CODED_VISIBLE_TEXT | 0 |
| SEARCH | PASS |
| LAZY_LOADING | PASS |
| LOCALIZATION | PASS_CANDIDATE |
| DEVICE_LAYOUT_SMOKE | DEFERRED_NO_DEVICE |
| P5_SELF_ASSESSMENT | PASS_CANDIDATE |
| FINAL_GATE | PENDING_CHATGPT |

## 12. Validation commands

P5-C evidence:

- `python tools/verify.py fast --changed` — **PASS**
- `python tools/verify.py full` — **PASS**
- `python tools/audit-feature-semantics.py --validate` — **PASS**
- `python -m compileall tools` — **PASS**
- `python -m unittest discover -s tools/tests -p "test_*.py"` — **PASS** (486 tests, skipped=5)
- `python -m unittest tools.tests.test_p5_localization_contract` — **PASS**
- `python -m unittest tools.tests.test_p2_settings_information_architecture` — **PASS**
- `python -m unittest tools.tests.test_generate_preference_artifacts` — **PASS**
- `git diff --check` — **PASS**

## 13. Final Gate

```text
P5_A0 = COMPLETE
P5_B = CLOSED_BY_P5_C
P5_C = PASS_CANDIDATE

LOCALIZATION_MISSING = 0
FORMAT_MISMATCH = 0
HARD_CODED_VISIBLE_TEXT = 0

SEARCH = PASS
LAZY_LOADING = PASS
LOCALIZATION = PASS_CANDIDATE

DEVICE_LAYOUT_SMOKE = DEFERRED_NO_DEVICE
P5_SELF_ASSESSMENT = PASS_CANDIDATE
FINAL_GATE = PENDING_CHATGPT
```

APK generated: NO.
P6 not started.
