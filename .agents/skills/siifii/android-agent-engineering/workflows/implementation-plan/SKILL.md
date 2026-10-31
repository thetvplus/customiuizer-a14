---
name: implementation-plan
description: Use after a feature-to-spec (or bug-fix root-cause analysis, or refactor target architecture) has been approved by a human, to break it into small implementation tasks. Each task specifies expected files/modules, behavior, tests, dependencies, and verification. Produces the artifact approved at gate 2 before any implementation code is written.
metadata:
  version: "0.1.0"
---

# Implementation Plan

Turns an **approved** spec into `templates/implementation-plan.md`, a
sequence of small tasks. This is gate 2's artifact.

## Preconditions

The input spec (from `feature-to-spec`, or the root-cause analysis from
`bug-fix`, or the target architecture from `refactor`) must already be
human-approved. Planning against an unapproved spec risks building a
detailed plan for requirements that are about to change.

## Task sizing

Each task should be small enough to implement, test, and verify in one
sitting, and independently reviewable — if a task description needs more
than a few sentences to explain what "done" looks like, it's probably two
tasks. Order tasks so each one leaves the codebase in a working,
test-passing state; avoid a plan that only compiles/passes after every
task in the sequence is done, since that removes the ability to stop and
check in partway through.

## What each task specifies

Use `templates/implementation-plan.md`'s per-task structure:

- **Expected files/modules** — concrete paths, following the layering
  from `android-architecture` (a task touching `domain` and `data` and
  `presentation` all at once for one small behavior is a sign it should
  be split along those layer boundaries, or that the layers aren't
  actually separable for this change — worth flagging either way).
- **Behavior** — what the task makes true that wasn't true before, stated
  concretely enough that `implement`/`bug-fix`/`refactor` can write a
  failing test for it first.
- **Tests** — which test(s) this task adds or changes, and at which level
  of `android-testing`'s pyramid; a task with production-code changes and
  no corresponding test entry is a planning gap unless explicitly
  justified (e.g. a pure refactor covered by existing characterization
  tests).
- **Dependencies** — which other tasks in this plan must land first.
- **Verification** — the concrete command(s)/check(s) that confirm this
  task is done (a specific test class, a lint/build command, a manual
  check that can't be automated yet and is explicitly named as such).

## Sequencing conventions

- Data/domain layer tasks before the presentation tasks that consume
  them, when both are needed for one feature — this lets the domain-level
  tests run and pass before UI wiring begins.
  - A task adding a new capability behind an existing seam (a new use
  case method, a new repository method) before the task that wires a UI
  entry point to it.
- If the spec left an open question the human accepted as "resolve during
  implementation," the task that first needs the answer explicitly names
  the question and pauses for a check-in rather than assuming an answer.

## Gate

Present the task list to the human. Do not begin `implement`/`bug-fix`/
`refactor` work until they approve it.
