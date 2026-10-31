---
name: feature-to-spec
description: Use after requirements-grill to convert a feature request into a structured engineering specification - problem, scope, non-goals, requirements, user flows, architecture, affected components, state model, error states, API/data implications, testing strategy, security implications, acceptance criteria, and open questions. Produces the document a human approves before implementation planning starts.
metadata:
  version: "0.1.0"
---

# Feature to Spec

Turns a grilled, mostly-de-ambiguated request into
`templates/feature-spec.md`, filled in. This is gate 1's artifact — the
human approves this spec before `implementation-plan` runs.

## Preconditions

Run `workflows/requirements-grill/SKILL.md` first for any request with
real ambiguity. If it surfaced open questions that block writing a
specific requirement, list them in the spec's "Open questions" section
rather than guessing an answer while writing the spec — don't let
spec-writing become a second, quieter place where behavior gets invented.

## Filling in the template

Use `templates/feature-spec.md` verbatim as the structure. Guidance per
section:

- **Problem** — the user/business problem, not the solution. One or two
  sentences someone unfamiliar with the request would understand.
- **Scope** — what this spec covers, stated concretely enough to draw a
  line around it.
- **Non-goals** — adjacent things this spec deliberately does *not* cover,
  named explicitly so nobody assumes they're included. A spec with no
  non-goals for a feature that obviously touches adjacent concerns (e.g.
  "add search" next to existing filter functionality) is under-specified.
- **Requirements** — the resolved answers from `requirements-grill`,
  written as testable statements ("the screen shows a retry button after
  a network failure, and retrying re-runs the same request"), not vague
  intentions.
- **User flows** — the concrete step sequence(s), including the
  edge-case/failure branches from the grill, not just the happy path.
- **Architecture** — which layers (`android-architecture`) this touches:
  new/changed use cases, repository methods, modules. Point at
  `android-architecture`/`compose-development`/`kotlin-coroutines-flow`
  for the applicable defaults rather than restating them.
- **Components/modules affected** — concrete file/module list, as far as
  it's known at spec time (refined further in `implementation-plan`).
- **State model** — the UI states this feature introduces or changes
  (loading/content/error/empty, and any feature-specific states), as a
  named enumeration a reader can check the flows against.
- **Error states** — one entry per distinct failure mode from the grill,
  each with its user-visible behavior and (where relevant) its retry/
  recovery behavior.
- **API/data implications** — new/changed endpoints or fields the feature
  depends on, explicitly marked as *assumed* vs. *confirmed*; a migration
  needed for persisted data; anything from `requirements-grill`'s API-
  assumptions question.
- **Testing strategy** — which layers get unit vs. integration vs. Compose
  UI coverage per `android-testing`'s pyramid, and any test that must
  exist for this spec's acceptance criteria to be verifiable.
- **Security implications** — anything touching `android-security`'s
  scope; explicitly state "none" if genuinely none, rather than omitting
  the section.
- **Acceptance criteria** — a checklist a human (or the eventual
  `code-review` pass) can verify directly against the shipped feature.
  Each criterion should be independently checkable — not "works well",
  but "shows an error state with a retry button within 1 UI frame of the
  request failing."
- **Open questions** — anything unresolved from the grill, or anything
  that surfaced while writing the spec itself. Not a failure state — an
  honest spec often has one or two of these, explicitly flagged for the
  human approver to weigh in on rather than silently resolved by
  assumption.

## Gate

Present the filled template to the human. Do not proceed to
`implementation-plan` until they approve it — including explicitly
resolving any open questions, or explicitly accepting that they stay open
and will be resolved during implementation with another check-in.
