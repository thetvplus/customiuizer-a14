---
name: compose-theming
description: Use when implementing or reviewing Compose theming architecture for the Design System - MaterialTheme/Material 3 integration, CompositionLocal usage, light/dark theme, dynamic color, typography and shapes, component-level tokens, theme propagation, and the theme entry point. Covers how design-tokens values actually flow through Compose, not the token taxonomy itself.
metadata:
  version: "0.1.0"
---

# Compose Theming

How `design-tokens`' semantic/component tokens actually get delivered
into Compose UI. This skill is Compose-mechanics; `design-tokens` is the
taxonomy — read both together when building or changing the theme layer.

## Material 3 integration

Build on `MaterialTheme`/Material 3's `ColorScheme`/`Typography`/`Shapes`
rather than reinventing a parallel theming system — map semantic tokens
into Material 3's color roles (`primary`, `onPrimary`, `surface`,
`onSurfaceVariant`, etc.) so standard Material components and custom
Design System components share one coherent theme, and so
Material-aware tooling (previews, accessibility contrast checks) keeps
working.

## The theme entry point

Provide one clear, top-level entry point a consuming app wraps its
content in — conceptually:

```kotlin
AcmeTheme {
    App()
}
```

**Do not assume this exact API already exists** — inspect the actual
`acme-design-system` project when it exists and this skill is
being applied there; this is illustrative of the *pattern* (one
composable, applied once near the root, providing the full theme via
`MaterialTheme` + any Design-System-specific `CompositionLocal`s beneath
it), not a claim about a real, current API.

## `CompositionLocal` — use sparingly, deliberately

`CompositionLocal` is appropriate for genuinely ambient, theme-shaped
data that many descendants need without explicit threading: the current
color scheme, typography, content-alignment direction. It is **not** a
generic dependency-injection mechanism — don't reach for a
`CompositionLocal` to pass a repository, a use case, or anything with
business meaning down through the composition tree; that's what
constructor/parameter injection and `android-architecture`'s DI
guidance are for. Every additional `CompositionLocal` is implicit,
harder-to-trace state — introduce one only for something that's
genuinely theme-shaped and needed broadly, not as a shortcut around
threading a parameter.

## Light theme, dark theme, dynamic color

- Both light and dark `ColorScheme`s are defined from the same semantic
  token set (see `design-tokens`) — a semantic token's *value* differs
  between the two schemes, but the *token name* a component references
  never changes, which is the entire point of the semantic layer.
- Dynamic color (Material You, per-device wallpaper-derived theming) is
  opt-in per the Design System's own product decision, not a default to
  assume — apply it only where the Design System has explicitly approved
  it, since it can conflict with brand-color requirements a product needs
  guaranteed.

## Typography and shapes

Map the Design System's type scale and corner-radius scale into Material
3's `Typography`/`Shapes` objects the same way colors map into
`ColorScheme` — components reference `MaterialTheme.typography.*`/
`MaterialTheme.shapes.*` (or a Design-System-specific equivalent layered
on top), never a raw `TextStyle`/`RoundedCornerShape` construction with
inline values that duplicates what the scale already defines.

## Component-level tokens

Where `design-tokens` calls for a component token (e.g.
`buttonPrimaryBackground`), expose it the same way as a semantic token —
resolved from the current theme, not hardcoded per component — so a
theme change (rebrand, dark mode, a future dynamic-color rollout)
propagates to every component automatically.

## Theme propagation and previews

Every component preview wraps its content in the theme entry point (so a
preview reflects real theming, not Compose's bare defaults), and should
include both a light and dark preview where the component's appearance
meaningfully differs between them — see `android-testing`'s preview
guidance in `compose-development`, applied here with the added
requirement of covering both theme modes.
