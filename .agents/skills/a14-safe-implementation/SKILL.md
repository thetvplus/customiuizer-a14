---
name: a14-safe-implementation
description: Implement exactly one approved atomic slice in tomthenpc/customiuizer-a14 on devin/a14-rom-intelligence-audit.
argument-hint: <task-slice-path>
triggers: ["user"]
---

# A14 Safe Implementation

## Invocation contract

This skill runs only when the user explicitly invokes:

```text
@skills:a14-safe-implementation <task-slice-path>
```

`$ARGUMENTS` must identify one filled A14 Task Slice. If it is empty, ambiguous, or names more than one task, stop before editing.

## Repository lock

```text
Repository: tomthenpc/customiuizer-a14
Authorized branch: devin/a14-rom-intelligence-audit
Platform: HyperOS 1 / Android 14
```

This skill never applies to another repository.

Live repository facts at invocation:

```text
Origin: !`git remote get-url origin`
Branch: !`git branch --show-current`
HEAD: !`git rev-parse HEAD`
Status: !`git status --short`
```

Reject the task before editing unless origin, branch, and upstream exactly match the authorized repository and branch.

Never create or switch branches. Never merge, rebase, force-push, reset --hard, clean, tag, release, push main, or modify another worktree.

## Precedence at slice boundaries

The repository's long-term goal remains continuous project completion across multiple sessions.

For the current invoked session, this skill is the owner's explicit session-scoping instruction:

```text
one approved slice
one implementer context
one qualifying engineering checkpoint
one handoff
then stop this session
```

Repository instructions saying “automatically enter the next task”, “do not voluntarily stop”, or “continue maintenance” apply across sessions, not inside this context after the current slice is complete.

Do not select a second objective in this session.

## Scope budget

Default limit:

```text
High-risk runtime Hook/lifecycle objective: 1
Low-risk objectives: at most 2 only when one Task Slice explicitly groups them
Production files: at most 3
Net production LOC: at most 200
Context handoff threshold: approximately 70%
```

Do not mix a runtime Hook change with a progress/hazard tool rewrite, CI workflow rewrite, large Java-to-Kotlin migration, or control-plane governance rewrite unless the Task Slice explicitly declares that single governance objective.

## Required workflow

1. Read the filled Task Slice from `$ARGUMENTS`.
2. Read `GOAL.md`, `AGENTS.md`, `TASK_STATE.md`, and the risk matrix.
3. Verify repository, branch, upstream, working tree, unfinished Git operations, and base commit.
4. Record the original behavior and independent test oracle before changing production code.
5. Add a failing regression/contract/callback test first when technically possible.
6. Prove the test fails for the intended reason.
7. Implement the smallest complete change.
8. Run focused tests after each logical edit.
9. Execute every mutation required by the risk tier.
10. Review all changed `catch(Throwable)`, Hook targets, callback argument indexes, reflection boundaries, static Android owners, and ClassLoader owners.
11. Run the risk-tier verification.
12. Inspect the complete base-to-head diff and `git diff --check`.
13. Update state documents only with facts proven by the same checkpoint.
14. Commit one real engineering checkpoint and push only the authorized branch.
15. Inspect CI for that qualifying engineering commit.
16. Write the standard handoff.
17. End this session without starting another task.

## Hook safety

For every `hookAllMethods`, `hookAllConstructors`, `param.args[n]`, or `getArg(n)`:

```text
check argument count first
use safe type conversion
make incompatible overloads return safely
test short arguments and wrong types
```

Exact method and constructor contracts include complete parameter types.

`AnyOfRequirement` is verified as a group, including all candidates and selection semantics.

```text
hard install -> REQUIRED
silent/findIfExists -> OPTIONAL only when degraded behavior remains correct
```

Xposed Hook installation is not rollbackable. Partial installation requires terminal partial-failure state or an equivalent generation/lease design; do not clear local variables and blindly retry the same loader.

## Fatal boundary

Every `catch(Throwable)` must explicitly preserve:

```text
VirtualMachineError
ThreadDeath
```

Handling only `OutOfMemoryError` is insufficient. Ordinary compatibility failures require bounded diagnostics or an explicit degraded result.

## Test independence

Do not derive expected behavior only from the implementation just written.

Proof priority:

```text
device behavior
main/release differential
exact JVM ABI
real callback/integration behavior
unit behavior
structural contract
source-string check
```

Source-string checks are auxiliary only.

## State and CI

Do not create a state-only commit to chase the previous commit's CI run.

A PASS claim requires either:

```text
CI commit == qualifying engineering commit
```

or an explicitly validated metadata-only descendant allowlist with an unchanged production-relevant tree digest.

Use full 40-character commit and tree SHAs. Do not use symbolic `HEAD`, short SHA, or guessed tree values.

## Completion

Full 后还必须按风险运行 Final、debug/develop-R8 和 artifact 检查。

At completion, write:

```text
TaskId
BaseCommit
QualifyingCommit
VerifiedTree
VerificationMode
CI run/job/commit
Mutation results
Known residual risk
First action for the next session
```

Then stop the session.
