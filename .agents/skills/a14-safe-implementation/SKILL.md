---
name: a14-safe-implementation
description: Implement one A14 change on tomthenpc/customiuizer-a14. Read AGENTS.md first.
argument-hint: <task>
triggers: ["user"]
---

# A14 Safe Implementation

Follow root `AGENTS.md`. `main` is the stable line; work on a branch created from an exact SHA.

Do not force-push, rewrite public history, or change PrefMap / ResourceHooks / Dynamic Island visuals unless the task explicitly requires it.

Run `python tools/verify.py fast --changed` after production edits, and `python tools/verify.py full` before claiming the task is done.
