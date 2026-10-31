---
name: siifii-android-architecture
description: Use when designing or reviewing the layering of an Android feature or module - Clean Architecture boundaries, presentation/domain/data separation, dependency direction, repository abstractions, use cases, DTO/domain/UI model mapping, dependency injection, and modularization (feature modules, core/shared modules, keeping domain code free of Android framework types).
metadata:
  version: "0.1.0"
---

# Android Architecture

Durable layering rules for Android features. This is the default; a
project's own `AGENTS.md`/`CLAUDE.md` may document a specific, reasoned
exception, which wins over this skill.

## The three layers and the one rule that matters

```
presentation  →  domain  →  data
```

**Dependencies point inward, never outward.** `domain` never imports
`presentation` or `data`. `data` never imports `presentation`. Compose,
`ViewModel`, `Activity`/`Fragment`, `Context`, `Intent`, `Bundle`, Android
`Parcelable` — none of these appear in `domain`. If a `domain` file needs
`import android.*` (outside `android.annotation`/pure Kotlin stdlib
equivalents), that's the first thing to flag.

- **presentation** — Compose UI, ViewModels, navigation, UI state. Talks
  only to `domain` (use cases), never directly to `data`.
- **domain** — pure Kotlin: use cases, domain models, repository
  *interfaces*. No Android SDK dependency, no framework annotations
  beyond `@Inject`/DI markers if the team's DI choice requires it in this
  layer (prefer even that at the edge, not scattered through domain
  logic). This is what makes domain code trivially unit-testable and, if
  the project ever adds KMP, directly shareable (see `kmp-development`).
- **data** — repository *implementations*, remote data sources (Retrofit/
  Ktor services returning DTOs), local data sources (Room entities,
  DataStore), and the mappers between DTO ⇄ domain model. Implements the
  interfaces `domain` defined, never the reverse.

## Repository abstraction

The interface lives in `domain` (e.g. `UserRepository`), the
implementation lives in `data` (e.g. `UserRepositoryImpl`), and DI binds
them at the edge. A repository returns **domain models**, never DTOs or
Room entities — the mapping happens inside the repository implementation
or a dedicated mapper it calls.

```kotlin
// domain
interface UserRepository {
    suspend fun getUser(id: UserId): Result<User>
    fun observeUser(id: UserId): Flow<User>
}

// data
class UserRepositoryImpl(
    private val api: UserApi,
    private val dao: UserDao,
) : UserRepository {
    override suspend fun getUser(id: UserId): Result<User> =
        runCatching { api.fetchUser(id.value).toDomain() }

    override fun observeUser(id: UserId): Flow<User> =
        dao.observeUser(id.value).map { it.toDomain() }
}
```

**Repository violation to flag:** a `ViewModel` or Compose screen
injecting `UserApi` or `UserDao` directly, or a use case reaching past a
repository interface into a data-source type. If it's happening because
"it's just one field," that's still a boundary violation — fix the
repository's return type instead.

## Use cases

A use case is a single, named operation with one public entry point
(`operator fun invoke(...)`), owning any business logic that combines or
transforms repository calls. Not every repository call needs a wrapping
use case — a trivial pass-through (`GetUserUseCase` that only calls
`repository.getUser(id)` with zero extra logic) is a judgment call the
team can skip, but the moment there's real logic (combining two
repositories, validating input, applying a business rule), it belongs in
a use case, not inline in the ViewModel.

```kotlin
class TransferFundsUseCase(
    private val accounts: AccountRepository,
    private val ledger: LedgerRepository,
) {
    suspend operator fun invoke(from: AccountId, to: AccountId, amount: Money): Result<Unit> {
        if (amount <= Money.ZERO) return Result.failure(IllegalArgumentException("amount must be positive"))
        return accounts.debit(from, amount).mapCatching {
            accounts.credit(to, amount).getOrThrow()
            ledger.record(from, to, amount)
        }
    }
}
```

## DTO / domain / UI model boundaries

Three distinct model shapes, each owned by its layer, each with its own
mapper at the boundary:

- **DTO** (`data`) — mirrors the wire format exactly (nullable fields the
  API can omit, string enums, raw timestamps). Never leaves `data`.
- **Domain model** (`domain`) — the business shape: non-null where the
  business guarantees non-null, real value types (`Money`, `UserId`),
  validated/normalized. This is what use cases and repositories exchange.
- **UI state / UI model** (`presentation`) — shaped for what the screen
  renders: pre-formatted strings, resolved display state
  (`isLoading`/`error`/`content`), never a raw domain model with
  optionality the UI has to re-derive on every recomposition.

**DTO-leak to flag:** a Compose screen or ViewModel referencing a
`@Serializable`/Moshi/Room-annotated class, or a domain model with a field
that only exists because the API happens to return it that way
(`userIdStr: String` instead of `UserId`). Fix at the mapper, not by
threading the DTO further up.

## Dependency injection

Constructor injection everywhere it's possible; field/property injection
only where the framework requires it (e.g. `Activity`/`Fragment` entry
points with Hilt). Bind interface → implementation at the module boundary
closest to `data` (a `data`-layer DI module), so `domain` never needs to
know which DI framework is in play. Scope lifetimes deliberately
(`@Singleton` for stateless repositories/use cases, screen/nav-graph scope
for anything holding UI-related state) — don't default everything to
singleton just to make it compile.

## Modularization

Default shape for anything beyond a small app:

```
:app                     wiring, DI graph assembly, navigation host
:core:*                  shared, feature-agnostic (design system, network client, database)
:feature:<name>:presentation
:feature:<name>:domain
:feature:<name>:data
```

- A feature module's `domain` can depend on `:core:*` domain-shaped
  modules but never on another feature's `presentation` or `data`.
  Cross-feature communication goes through a shared domain contract in
  `:core` or through navigation, not a direct module dependency between
  features.
- Split `presentation`/`domain`/`data` into separate Gradle modules once a
  feature is large enough that build-graph isolation and parallel
  compilation actually pay off; for a small feature, three packages inside
  one module with the same dependency-direction discipline is a
  legitimate, smaller-footprint choice — don't force module-per-layer on
  every feature reflexively. See `gradle-build` for the build-performance
  side of this trade-off.
- `:core` modules split by capability (`:core:network`, `:core:database`,
  `:core:designsystem`, `:core:analytics`), not by layer — a `:core:domain`
  grab-bag module tends to become an unscoped dumping ground.

## What to check in a review

See `references/architecture-checklist.md` for the full checklist this
skill's guidance distills into; the fast version:

1. Does anything in `domain` import Android framework types?
2. Does a repository interface live in `domain` and its implementation in
   `data`? Does anything bypass the interface?
3. Does a repository return domain models, never DTOs/entities?
4. Is there a DTO or Room entity visible above `data`?
5. Does real business logic live in a use case rather than inline in a
   ViewModel or, worse, in a composable?
6. Does a feature module reach into another feature's `presentation` or
   `data`?
