---
name: refactor
description: Use when restructuring existing Android code without an intended behavior change. Required order - understand existing behavior, characterization tests, define target architecture, small migration steps, regression verification, cleanup. Preserves behavior unless the approved specification explicitly says otherwise.
metadata:
  version: "0.1.0"
---

# Refactor

## Required order

1. **Understand existing behavior** — read the code as it is, including
   its edge cases and any surprising-but-intentional behavior, before
   proposing a target shape. A refactor plan built on a guessed
   understanding of what the code does will "clean up" behavior that
   someone depended on.
2. **Characterization tests** — for any code path this refactor touches
   that doesn't already have adequate test coverage (per
   `android-testing`'s pyramid), write tests that pin down *current*
   behavior first, including the surprising edge cases found in step 1.
   These aren't a claim the current behavior is correct — only that it's
   the known baseline the refactor must preserve.
3. **Define target architecture** — the specific end state (applying
   `android-architecture`/`compose-development`/`kotlin-coroutines-flow`
   as appropriate), concrete enough to plan migration steps against, and
   approved the same way an `implementation-plan` is if the refactor is
   non-trivial.
4. **Small migration steps** — move toward the target in increments small
   enough that each one is independently verifiable and revertible;
   avoid a single large rewrite commit that's only checkable as a whole.
5. **Regression verification** — after each step (not just at the end),
   run the characterization tests plus the broader relevant suite to
   confirm behavior hasn't drifted.
6. **Cleanup** — once the target architecture is reached and verified,
   remove now-dead code/abstractions the migration left behind.

## Hard rule

**Preserve behavior unless the approved specification says otherwise.**
A refactor is not the place to also fix a bug noticed along the way or
change behavior that seems wrong — flag it, and either handle it as a
separate `bug-fix` or get explicit sign-off to fold a behavior change
into this refactor's scope. Mixing the two makes it impossible to tell,
from the diff, whether a test failure is "the refactor broke something"
or "the behavior intentionally changed."

## After the refactor

`workflows/code-review/SKILL.md`, same as `implement`/`bug-fix` — a
refactor gets an independent review pass too, specifically checking that
the characterization tests actually covered what changed.
