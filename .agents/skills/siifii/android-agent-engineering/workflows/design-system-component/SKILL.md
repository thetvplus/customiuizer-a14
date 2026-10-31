---
name: design-system-component
description: Use when creating or modifying a component inside the Design System repository itself (acme-design-system or equivalent) - not for consuming the Design System in an application (see skills/design-system/design-system-consumer for that). Sequences existing skills and workflows (requirements-grill, implement, code-review) rather than reimplementing them, plus the Design-System-specific steps in between.
metadata:
  version: "0.1.0"
---

# Design System Component (workflow)

The end-to-end process for building or changing a component in the
Design System repository. This workflow is a **sequence**, not a
reimplementation — most of its steps invoke an existing skill or workflow
from this repository rather than defining new process. Read the skills
named at each step for the actual guidance; this file only orders them.

```
Component Request
  ↓
Search Existing Components
  ↓
Boundary Evaluation
  ↓
Requirements
  ↓
Design Specification (Figma)
  ↓
Define Component API
  ↓
Map Tokens
  ↓
Define Variants + States
  ↓
Accessibility
  ↓
RTL / Localization
  ↓
Implementation
  ↓
Previews
  ↓
Tests
  ↓
Design Review
  ↓
Engineering Review
  ↓
Documentation
  ↓
Release
```

## Steps

1. **Search existing components** — before building anything, confirm
   this doesn't already exist. Apply `skills/design-system/design-system-consumer`'s
   search discipline even though you're inside the Design System repo
   itself: a near-duplicate component is exactly as costly to create here
   as it is for an application to fork one.
2. **Boundary evaluation** — confirm this genuinely belongs in the Design
   System rather than being product-specific. Apply
   `skills/design-system/design-system-boundary` in full. If the answer
   is "this is actually application-specific," stop here and hand it back
   — don't build product-specific UI inside the Design System repo.
3. **Requirements** — apply the existing `workflows/requirements-grill`,
   scoped to a component: what variants/sizes/states does it actually
   need (not every state applies to every component — see
   `skills/android-code-review`'s calibration on this same point, echoed
   in `skills/design-system/design-system-review`), what's the
   accessibility/RTL scope, what's genuinely out of scope for v1.
4. **Design specification (Figma)** — capture the visual spec. This
   repository does not yet have a Figma integration; use whatever
   reference mechanism is available today (a linked design file, exported
   assets) and record it in `templates/component-specification.md`'s
   Figma field. See `docs/design-system.md` for why this is a documented
   placeholder rather than a built integration in this version.
5. **Define component API, map tokens, define variants + states** —
   apply `skills/design-system/design-system-component-authoring` (API
   shape) and `skills/design-system/design-tokens` (which tokens this
   component consumes) together. Settle the public signature before
   writing internals, per that skill's first section.
6. **Accessibility** — apply `skills/design-system/design-system-accessibility`
   as part of the design, not as a pass added afterward.
7. **RTL / localization** — apply `skills/design-system/rtl-localization`
   as part of the design, same reasoning as accessibility above.
8. **Implementation** — apply the existing `workflows/implement`
   unchanged: RED → GREEN → REGRESSION → REFACTOR, informed by
   `skills/compose-development` for the Compose mechanics and
   `skills/design-system/compose-theming` for how the component draws
   its tokens from the theme.
9. **Previews** — per `skills/compose-development`'s preview guidance,
   covering every variant/state that differs visually, in light/dark
   theme and LTR/RTL where applicable (see step 6/7).
10. **Tests** — apply `skills/android-testing`: unit tests for any
    non-trivial logic, Compose UI tests for interaction and accessibility
    semantics.
11. **Design review** — a human gate (a product/design stakeholder
    checking the shipped component against the original design intent).
    Outside this repository's or any agent's scope beyond preparing the
    component for that review — don't skip it or treat an agent's own
    judgment as a substitute for it.
12. **Engineering review** — apply the existing `workflows/code-review`
    together with `skills/design-system/design-system-review`, the same
    independence principle as any other code review in this repository:
    do not assume the implementing agent's work is correct because it
    followed this workflow.
13. **Documentation** — fill in
    `templates/component-specification.md` completely and accurately to
    what actually shipped (see `design-system-review`'s Quality section
    for why an inaccurate spec is itself a review finding).
14. **Release** — the Design System library has its own versioning,
    separate from `android-agent-engineering`'s own `CHANGELOG.md`/`VERSION` —
    this step is a pointer to whatever release process that repository
    uses, not something this workflow defines.

## Future integration points (not built yet)

Figma MCP/automated design-to-code context, screenshot/visual regression
testing, automated design-token generation/sync, component catalog
generation, and accessibility automation would all attach to specific
steps above (4, 9, 6 respectively) without changing this workflow's
shape — see `docs/design-system.md` for the full list. None of them are
assumed or required by this version of the workflow.
