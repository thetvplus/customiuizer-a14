---
name: requirements-grill
description: Use before starting any non-trivial Android feature or change request that has any ambiguity - interrogates happy path, edge cases, failure/loading behavior, retries, permissions, offline behavior, analytics, security, accessibility, API assumptions, and backwards compatibility before a spec or any code is written. Never invent product behavior silently.
metadata:
  version: "0.1.0"
---

# Requirements Grill

Run this before `feature-to-spec` whenever a request leaves real product
decisions unstated. Skip it only for a request so fully specified that
every question below already has an explicit answer in it — and say so
explicitly rather than skipping silently.

## Rule

**Never invent product behavior silently.** If the request doesn't say
what happens on failure, offline, or an edge case, that is a question to
ask, not a gap to fill with a plausible-sounding guess. An assumption
that turns out wrong costs far more once it's built than the one question
would have cost up front.

## What to interrogate

Ask about each of these that isn't already answered by the request.
Prefer specific, concrete questions ("what should the screen show if the
transfer API times out — an error state with retry, or silent retry up to
N times?") over open-ended ones ("what about error handling?"). Batch
related questions together rather than a long back-and-forth one at a
time, but don't bundle unrelated topics into one question.

1. **Happy path** — confirm the exact intended flow, including what
   triggers it and what "done" looks like, even when it seems obvious.
2. **Edge cases** — empty states, boundary values, unusual but valid
   input, concurrent user actions (double-tap, navigate away mid-action).
3. **Failure behavior** — what does the user see/what happens on each
   distinct failure mode (network error, server error, validation
   failure, timeout)? Is failure recoverable in-place or does it require
   leaving the screen?
4. **Loading behavior** — is there a loading state; can the user cancel
   it; what happens if they navigate away mid-load.
5. **Retries** — automatic or user-initiated; how many attempts, what
   backoff, is there a limit after which it gives up and shows an error.
6. **Permissions** — which runtime permissions are needed; what's the
   behavior when denied (once vs. permanently/"don't ask again"); is
   there a rationale UI.
7. **Offline behavior** — does the feature work offline at all; if not,
   what's shown; if partially, which parts degrade and how.
8. **Analytics** — what events, if any, does product/analytics expect
   from this flow; don't invent event names/properties not specified —
   flag as an open question if analytics requirements aren't given.
9. **Security** — does this flow touch anything in `android-security`'s
   scope (tokens, sensitive data, deep links, WebView) that needs an
   explicit decision now rather than a default assumption.
10. **Accessibility** — any accessibility requirement beyond the
    `compose-development` defaults (e.g. a specific interaction that needs
    a non-obvious accessible alternative).
11. **API assumptions** — does the flow assume a backend endpoint/field
    exists, is stable, or behaves a specific way that hasn't been
    confirmed? State the assumption explicitly and flag it as needing
    confirmation rather than silently building against a guess.
12. **Backwards compatibility** — does this change affect users on an
    older app version, a `minSdk` boundary, or existing persisted data
    (a migration)? What happens to an in-flight session/cached state from
    before this change.

## Output

A short list of resolved answers plus a **explicit open-questions list**
for anything not resolved after asking — carried forward into
`feature-to-spec`'s "Open questions" section rather than silently dropped
or guessed at. It's normal and expected for some items to remain open at
this stage; `feature-to-spec` and the human-approval gate that follows it
are where they get closed out, not invented.

## Next step

Once the essential ambiguity is resolved (or explicitly logged as open),
proceed to `workflows/feature-to-spec/SKILL.md`.
