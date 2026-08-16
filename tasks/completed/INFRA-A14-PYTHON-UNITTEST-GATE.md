# INFRA-A14-PYTHON-UNITTEST-GATE

A14 Python unittest gate is closed.

- `python -m unittest discover -s tools/tests -p "test_*.py"`:
  - `Ran 392 tests`
  - `OK`
  - `failures=0`
  - `errors=0`
  - no new `skip`

## History

| Stage | Failures/errors | Notes |
|-------|-----------------|-------|
| Baseline | 78 | legacy doc-oracle tests and generated artifact dependencies |
| R5.1 | 62 | control-plane / portability / staged-snapshot alignment |
| R5.2 | 20 | six document gates migrated to source invariants; only APK size delta remained |
| R5.3 | 0 | `apk_size_delta.py` decoupled from generated artifacts; `test_apk_size_delta.py` uses TemporaryDirectory fixtures |

## R5.3 closure

- `tools/apk_size_delta.py`:
  - explicit `--inputs-manifest` with relative variant JSONs
  - explicit `--gradle-file`, `--baseline-commit`, `--current-commit`, `--out-json`, `--out-md`
  - no default `docs/performance` inputs or outputs
  - no `TASK_STATE.md` or active/completed task state reading
  - no Gradle, Git, APK build, network, or cache access
  - deterministic variant sorting and pure computation API
- `tools/tests/test_apk_size_delta.py`:
  - TemporaryDirectory fixtures only
  - synthetic develop/release measurements
  - no real APK, no assemble command, no hardcoded commit as HEAD

## Constraints preserved

- No `app/src/main/**` changes.
- No `docs/performance/A14_APK_SIZE_DELTA.*` restored.
- No `TASK_STATE.md` restored.
- No APK/AAB built.
- Status-bar device task `tasks/active/FIX-A14-STATUS-BAR-HEIGHT-LIVE-APPLICATION.md` remains active/parked.
