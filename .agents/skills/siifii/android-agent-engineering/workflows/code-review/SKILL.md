---
name: siifii-code-review
description: Use to perform an independent AI code review after implement, bug-fix, or refactor work is complete, before human review. Applies the skills/android-code-review taxonomy. Never assumes that because another agent wrote the code it is correct.
metadata:
  version: "0.1.0"
---

# Code Review (workflow)

The process for running a review. For the finding taxonomy and what to
inspect, see `skills/android-code-review/SKILL.md` — this workflow applies
that skill; it doesn't restate it.

## Independence

**Do not assume the implementing agent's work is correct because it
followed `implement`/`bug-fix`/`refactor`.** Review the diff exactly as
you would a human's PR: read the actual changed code, not just the
implementing agent's summary of what it did. Re-derive whether the spec's
acceptance criteria are actually met by reading the code and tests, not
by trusting a claim that they are.

## Process

1. **Gather the diff** — the full set of changed files against the base
   the work started from, plus the spec/plan/root-cause-analysis that
   motivated it (`feature-to-spec` / `bug-fix` step 2 / `refactor` step 3
   output).
2. **Check spec/plan conformance** — does the diff implement what was
   approved, no more (scope creep) and no less (missing acceptance
   criteria)? A change that does something reasonable but different from
   what was approved is a finding, not a silent pass.
3. **Apply the taxonomy** — walk `android-code-review`'s "what to
   inspect" list against the actual diff: correctness, architecture,
   Kotlin, Compose, concurrency, lifecycle, security, performance,
   accessibility, testing, maintainability, API compatibility. Pull in
   the specific skill (`android-architecture`, `compose-development`,
   `kotlin-coroutines-flow`, `android-security`, `android-testing`) for
   any area the diff actually touches.
4. **Check the tests, not just the code** — for `implement`, confirm a
   RED test genuinely existed and covers the behavior; for `bug-fix`,
   confirm a regression test exists and would have failed before the fix;
   for `refactor`, confirm characterization tests actually covered what
   changed. A plausible-looking test that wouldn't actually have caught
   the bug/regression is itself a finding.
5. **Produce the report** — `templates/code-review.md`, findings ranked
   Critical → Major → Minor → Suggestion, with at least one Positive
   finding when one genuinely applies (see `android-code-review` for why).
6. **Hand to human review** — the AI review is not the merge gate. It's
   what the human reviewer reads before making the actual merge decision
   (gate 3 in the README's workflow diagram) — present it as input to
   their review, not as a substitute for it.

## Re-reviewing after fixes

If Critical/Major findings are addressed and the diff changes, re-run the
relevant parts of this process against the new diff rather than assuming
the fix was applied correctly — a fix for a Critical finding is exactly
the kind of change that deserves the same "don't assume it's correct"
treatment as the original code.
