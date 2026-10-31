---
name: bug-fix
description: Use when fixing a reported or observed bug in Android code. Required order - reproduce, root cause analysis, characterization/regression test, minimal fix, full relevant tests, refactor only if justified. Never guess-fixes a bug.
metadata:
  version: "0.1.0"
---

# Bug Fix

## Required order

1. **Reproduce** — confirm the bug actually happens, and under what exact
   conditions (device state, input, sequence of actions). A bug fixed
   without ever reproducing it first is a guess wearing a fix's clothes —
   if it can't be reproduced (device-specific, timing-dependent), say so
   explicitly and treat the eventual fix as a hypothesis to verify more
   carefully in review, not as confirmed.
2. **Root cause analysis** — find the actual mechanism, not the first
   plausible-looking cause. Trace the failure back through the relevant
   skill's lens: a wrong value might be a `kotlin-coroutines-flow` race
   condition, a `compose-development` stale-`remember` issue, or an
   `android-architecture` mapping bug — identify which, with evidence
   (a log trace, a failing assertion, a read of the actual code path),
   not a guess.
3. **Characterization/regression test** — write a test that fails because
   of this exact bug, before touching the fix. This is what turns "we
   think we understand the bug" into a checkable claim, and it's what
   prevents the same bug from silently coming back later (see
   `android-testing`'s regression-test definition).
4. **Minimal fix** — the smallest change that makes the root cause (not
   just the symptom) go away. Resist bundling unrelated cleanup into the
   same change — that's what step 6 is for, and it's easier to review a
   fix that's obviously just the fix.
5. **Full relevant tests** — run the regression test plus the broader
   relevant suite (same standard as `implement`'s REGRESSION step) to
   confirm the fix didn't break anything else.
6. **Refactor only if justified** — if the root cause analysis revealed a
   structural problem that makes this class of bug likely to recur (e.g.
   the architecture violation that let a DTO leak in the first place),
   that's worth fixing too — but as a deliberate, separately-justified
   step, not an automatic add-on to every bug fix.

## Hard rule

**Never guess-fix.** A change that makes the reported symptom go away
without a root-cause explanation and a failing-then-passing test is not a
verified fix — it might have papered over the symptom while leaving the
actual defect in place, or fixed a different bug that happened to look
similar. If time pressure pushes toward a guess-fix, say so explicitly
rather than presenting it as a confirmed fix.

## After the fix

`workflows/code-review/SKILL.md` for an independent review pass, same as
`implement`.
