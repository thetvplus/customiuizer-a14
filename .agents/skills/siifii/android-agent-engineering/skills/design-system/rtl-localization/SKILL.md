---
name: rtl-localization
description: Use when authoring or reviewing Design System (or app) UI that must support Arabic, RTL layouts, and localization generally - start/end vs left/right, directional icons and mirroring, text alignment, Arabic typography, mixed Arabic/English content, numbers/dates/currencies, pluralization, text expansion, and bidirectional text. Treats Arabic/RTL as first-class, not an afterthought.
metadata:
  version: "0.1.0"
---

# RTL & Localization

Arabic and RTL are first-class requirements for this Design System, not
a follow-up pass. A component that only works correctly in LTR/English is
not done, the same way a component with no test coverage is not done.

## Start/end, not left/right

Use directional (`start`/`end`) APIs exclusively — `Modifier.padding(start = ..., end = ...)`,
`Arrangement.Start`/`Arrangement.End`, `TextAlign.Start`/`TextAlign.End` —
never physical left/right (`paddingStart`-as-always-left assumptions,
`Alignment.Left`, manual `if (isRtl) right else left` branching). Compose
and the Android layout system already flip `start`/`end` automatically
based on layout direction; reaching for left/right defeats that and has
to be manually fixed later, component by component.

```text
Avoid: leftPadding, rightPadding, leftIcon, rightIcon
Prefer: startPadding, endPadding, leadingIcon, trailingIcon
```

Note `leadingIcon`/`trailingIcon` (semantic, direction-aware) rather than
`startIcon`/`endIcon` for icon-slot naming specifically — "leading" is
the icon that reads first in either direction, which is the actual
intent, and it's the established Compose/Material naming convention.

## Directional icons and mirroring

An icon that conveys direction (a back arrow, a chevron, a
forward/next arrow) must mirror in RTL — use
`Modifier.mirror()`-equivalent handling or the platform's automatic
drawable mirroring (`autoMirrored` icon variants in
`androidx.compose.material.icons.automirrored.*`) rather than a single
fixed-direction icon. An icon that's direction-*neutral* (a checkmark, a
settings gear, a logo) should **not** mirror — mirroring something with
no inherent direction just looks wrong; know which category an icon is
in before deciding.

## Text alignment and mixed-direction content

Text alignment follows the current layout direction by default
(`TextAlign.Start`), but real content is often mixed-direction — an
Arabic sentence containing an English product name, or a number embedded
in Arabic text. Rely on Compose's/the platform's Unicode bidirectional
algorithm handling (don't manually reverse strings or manually interleave
LTR segments) and verify visually with actual mixed-content test strings,
not only pure-Arabic or pure-English fixtures — mixed content is where
naive RTL handling actually breaks.

## Arabic typography

Arabic script has different line-height, letter-spacing, and font
requirements than Latin script for the same visual density — the type
scale (`compose-theming`) should account for this rather than applying
identical `TextStyle` values to both scripts if the chosen typeface's
metrics differ meaningfully. Verify readability at the Design System's
standard text sizes with real Arabic text, not lorem-ipsum-style
placeholder Latin text that happens to be the right length.

## Numbers, dates, currencies

- Numerals: follow the product's locale decision for Eastern Arabic
  numeral display (١٢٣) vs. Western numerals (123) in Arabic locale — this
  is a product decision, not a default either way; don't assume one
  without checking, and keep it consistent across the Design System's
  components rather than letting each component decide independently.
- Dates and currencies: use locale-aware formatting APIs
  (`java.time` formatters with the current `Locale`, not manual string
  concatenation) so month names, date order, and currency symbol
  placement follow the active locale automatically.

## Localized strings, pluralization, text expansion

- No hardcoded UI string in a Design System component — every
  user-visible string is a parameter or a string resource, resolved by
  the consumer/locale, never baked into the component (a Design System
  component especially must not assume English, since it's shared across
  every consumer's locales).
- Use plural-aware resources (`plurals`/ICU `MessageFormat`-style rules)
  for any count-dependent string — Arabic has more plural forms than
  English (zero/one/two/few/many/other), so a naive `if (count == 1)`
  branch is insufficient; delegate to the platform's pluralization
  support rather than hand-rolling English-shaped logic.
- Design layouts to tolerate **text expansion** — German and Arabic text
  frequently run 30-50% longer than the equivalent English string for the
  same meaning. A component with fixed-width text containers that only
  fits English is a defect; test with a realistically long localized
  string, not just the English original.

## Bidirectional text and accessibility together

TalkBack/screen readers need correct reading order for bidirectional
content too — verify accessibility (`design-system-accessibility`) in
both LTR and RTL, not only visually. A layout that *looks* correct
mirrored but announces content in the wrong order for a screen-reader
user is still broken.

## What to test/preview

Every component with any text or directional layout gets previewed (and
ideally tested) in both:

```
English / LTR
Arabic / RTL
```

where applicable — a purely symmetric, non-directional visual element
(a plain colored square) doesn't need this, but almost every real
component does.
