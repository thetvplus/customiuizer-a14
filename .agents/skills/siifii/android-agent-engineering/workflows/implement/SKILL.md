---
name: siifii-implement
description: Use when implementing new Android behavior from an approved implementation plan task. Follows RED -> GREEN -> REGRESSION -> REFACTOR. Never modifies a test merely to make an implementation pass. Runs relevant verification after each meaningful step.
metadata:
  version: "0.1.0"
---

# Implement

Executes one `implementation-plan` task for genuinely new behavior. For a
bug in existing behavior, use `workflows/bug-fix` instead; for changing
structure without changing behavior, use `workflows/refactor` instead —
this workflow is specifically for building something that doesn't exist
yet.

## RED → GREEN → REGRESSION → REFACTOR

1. **RED** — write the test(s) the task specifies (`android-testing` for
   how, at the right level of the pyramid) before writing the
   implementation. Run it. Confirm it fails for the *expected* reason —
   the behavior genuinely doesn't exist yet, not a typo, wrong import, or
   test targeting the wrong thing. A red test that fails for the wrong
   reason isn't validated yet as a real test.
2. **GREEN** — write the minimum implementation code that makes the test
   pass, following the applicable skills (`android-architecture` for
   layering, `compose-development`/`kotlin-coroutines-flow` for how,
   `android-security` if the task touches sensitive data). Run the test
   again; confirm it passes.
3. **REGRESSION** — run the broader relevant test suite (not just the one
   new test) to confirm nothing else broke. "Relevant" means at minimum
   the module touched and anything that depends on it; when in doubt, run
   more rather than less.
4. **REFACTOR** — with tests green, clean up: naming, duplication,
   structure, applying `android-architecture`/`compose-development`
   defaults properly rather than just making it compile. Re-run tests
   after refactoring, since "just a rename/cleanup" is exactly the kind of
   change that quietly breaks something.

## Hard rules

- **Never modify a test merely to make an implementation pass.** If a
  test seems wrong once you're implementing against it, that's a stop-and
  -ask moment (the spec or plan may be wrong), not a silent test edit.
  The only acceptable reason to change a test during `implement` is that
  the test itself has a genuine bug (asserts the wrong thing per the
  approved spec) — and that's worth calling out explicitly when it
  happens, not folded silently into the same commit as unrelated changes.
- **Run relevant verification after each meaningful step**, not only at
  the very end of the task — catching a break immediately after
  introducing it is cheap; catching it after five more steps is not.
- If a task turns out to need product decisions the plan didn't cover,
  stop and surface it rather than guessing — same principle as
  `requirements-grill`.

## After the task

Move to the next `implementation-plan` task, or, once all tasks for the
plan are done, to `workflows/code-review/SKILL.md` for an independent
review pass before this goes to the human.
