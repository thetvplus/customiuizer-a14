# Architecture review checklist (extended)

Load this only when doing a deep architecture review or setting up a new
module — `SKILL.md`'s "What to check" section is enough for routine work.

## Dependency direction

- [ ] No `import android.*` (framework types) in any file under a
      `domain` source set, other than pure-Kotlin-safe exceptions the team
      has explicitly documented.
- [ ] No `presentation` → `data` import that skips `domain`.
- [ ] No `domain` → `presentation` or `domain` → `data` import.
- [ ] No feature module importing another feature module's
      `presentation` or `data` package.

## Repository layer

- [ ] Every repository has an interface in `domain` and exactly one
      production implementation in `data` (test doubles are fine, and
      expected, in test source sets).
- [ ] Repository methods return domain models or domain-shaped wrapper
      types (`Result<T>`, a sealed `Outcome`), never DTOs, Room entities,
      or raw `Response<T>`.
- [ ] Mapping DTO/entity → domain model happens in exactly one place
      (a mapper function/class colocated with the data source), not
      duplicated across call sites.
- [ ] A repository doesn't leak a platform type through its interface
      (`Flow<Cursor>`, `LiveData` in `domain` — prefer `Flow` at the
      `domain` boundary; if `LiveData` is used at the edge for legacy
      reasons, convert immediately).

## Use cases

- [ ] A use case has one public entry point.
- [ ] A use case doesn't reach past a repository interface into a data
      source directly.
- [ ] Business rules (validation, combining multiple repository calls,
      retries with business meaning) live in the use case, not the
      ViewModel.
- [ ] A trivial single-call pass-through use case is a deliberate,
      documented team choice, not an accident of "every repository call
      needs a use case."

## Models

- [ ] No `@Serializable`/Moshi/Gson/Room annotation on a type referenced
      from `presentation`.
- [ ] Domain models use value types for identifiers/money/etc. rather than
      raw `String`/`Long` where the codebase already has such value types
      established (don't introduce a wrapper type project-wide as part of
      an unrelated change, but do use existing ones consistently).
- [ ] UI state classes are immutable (see `compose-development`) and
      pre-resolve what the screen needs to render — no domain-model
      optionality left for the composable to re-derive.

## Dependency injection

- [ ] Constructor injection is the default; field injection only where the
      framework requires it.
- [ ] Bindings (interface → implementation) live at the `data`-layer DI
      module, not scattered.
- [ ] Scopes are deliberate, not defaulted to singleton everywhere.

## Modularization

- [ ] `:core:*` modules are split by capability, not by architectural
      layer.
- [ ] A feature's `domain`/`data` split (whether by package or by Gradle
      module) is consistent with the rest of the codebase's chosen
      granularity — don't introduce a one-off finer or coarser split
      without a stated reason.
