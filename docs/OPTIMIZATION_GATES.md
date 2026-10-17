# Optimization Gates

All performance/memory optimization changes must satisfy these requirements.

## Phased Strategy

| Phase | Scope | Risk | Gate |
|-------|-------|------|------|
| P0 | Safety gates and docs | None | verify fast |
| P1 | Low-risk hot-path cuts | Low | verify full + hotpath-alloc ceiling must not increase |
| P2 | Memory/lifecycle hardening | Medium | verify full + allocation tests + lifecycle tests |
| P3 | Targeted architecture refactor | High | verify full + ROM evidence bundle |

## Per-Change Evidence Template

Every optimization commit must include (in commit message or linked doc):

1. **Hot/cold boundary**: which hook callback or path is affected.
2. **Before state**: current allocation count, pattern, or measured cost.
3. **After state**: new count or measurement.
4. **Invariants preserved**: `chain.proceed()` count/timing unchanged, hook not reinstalled, fatal propagation intact.
5. **Rollback path**: how to revert without breaking other changes.
6. **Tests**: which test(s) pin the improvement.

## Quantitative Gates

### Hotpath Allocation Budget

```powershell
python tools/check_hotpath_alloc_budget.py --check
```

Detects allocation-suspect patterns (`HashMap`, `ArrayList`, `File`, reflection, `Parcel.obtain`, etc.) inside `MethodHook` bodies. The ceiling in `tools/HOTPATH_ALLOC_BASELINE.json` can only decrease.

### Hook-Body PrefMap Ceiling

```powershell
python tools/hook_body_prefmap_scan.py --check
```

Frozen at zero. No new `MainModule.mPrefs` reads in hook callbacks.

## Design Principles (Telegram-aligned)

- **Direct path**: hot logic takes the shortest route; no multi-layer dispatch.
- **Minimal state**: prefer immutable snapshots over shared mutable containers.
- **Failure isolation**: ordinary exceptions stay local; fatal errors propagate immediately.
- **Observable**: every optimization must have before/after evidence; no "feels faster" changes.
- **No framework for micro-optimization**: do not add abstractions that cost more than they save.

## Prohibited in Hot Paths

- Reflection (`getDeclaredMethod`, `getDeclaredField`, `Class.forName`)
- Disk I/O (`File`, `FileInputStream`, sysfs reads)
- Synchronous Binder (`contentResolver.query`, `Parcel.obtain/transact`)
- Unbounded allocations (`HashMap()`, `ArrayList()`, `Properties()`)
- Regex compilation (`Regex(...)`)
- PrefMap reads (`MainModule.mPrefs`)

These are acceptable in cold paths (install, snapshot builders, preference observers).
