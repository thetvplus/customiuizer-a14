---
name: a14-independent-review
description: Independently red-team one completed A14 atomic slice without modifying production code.
argument-hint: <base-sha> <head-sha> <task-slice-path>
triggers: ["user"]
---

# A14 Independent Review

## Invocation contract

Invoke explicitly:

```text
@skills:a14-independent-review <base-sha> <head-sha> <task-slice-path>
```

This skill is read-only with respect to production code. It never applies to another repository.

```text
Repository: tomthenpc/customiuizer-a14
Authorized branch: devin/a14-rom-intelligence-audit
```

## Reviewer independence

Do not continue the implementation. Do not fix production code in the review context.

Read in this order:

1. approved Task Slice;
2. base-to-head diff;
3. changed tests and tools;
4. current repository rules;
5. verification and CI evidence.

Judge the implementation against the predeclared oracle, not against its own tests alone.

## Mandatory review

Check:

```text
scope and LOC budget
unmentioned behavior changes
process and install phase
preference defaults
exact Hook targets and parameter types
AnyOf candidate semantics
hard/optional criticality
callback argument count and safe types
fatal propagation
partial installation and retry behavior
lifecycle and ClassLoader ownership
exact Java/Kotlin JVM ABI
disabled-path cost
state/CI/tree transaction integrity
```

Reject source-string-only proof, fixed-count-only tests, expected values changed merely to match the patch, or tests that do not fail on a plausible wrong implementation.

## Mutation requirement

Perform at least one temporary mutation relevant to the task. R3 candidates include:

```text
remove an args-length guard
replace a safe cast with a forced cast
change Int to Long
remove an AnyOf candidate
duplicate a contract target
mark a hard Hook OPTIONAL
swallow ThreadDeath
retry a partially installed loader
invoke an old callback after cleanup
```

Restore the working tree after the mutation.

## Output

```text
Decision: APPROVE | REJECT

P0:
P1:
P2:
P3:

Mutation:
Expected failure:
Observed result:

CI and provenance:
Required repair:
```

Any open P0 or P1 requires `REJECT`.
