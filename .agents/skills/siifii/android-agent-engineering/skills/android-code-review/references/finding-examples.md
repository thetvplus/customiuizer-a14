# Worked finding examples by severity

Load this when calibrating severity is genuinely unclear from
`SKILL.md`'s definitions alone.

## Critical

> **Critical — hardcoded API credential.**
> `NetworkModule.kt:42` — `const val API_KEY = "sk_live_..."` is compiled
> into the APK and committed to version control. Extractable from the
> binary by anyone, and permanently in git history even after removal.
> Fix: load from a non-committed local properties file or CI-injected
> environment variable at build time (see `android-security`); rotate the
> credential regardless of the code fix, since it's already been
> committed.

> **Critical — repository returns wrong user's data under concurrency.**
> `UserRepositoryImpl.kt:58` — a single shared `MutableStateFlow<User?>`
> is reused across concurrent calls to `getUser(id)` for different `id`s,
> so a fast request for user A can observe a value written by an
> in-flight request for user B. Fix: key the cache by `id`, or scope the
> flow per call.

## Major

> **Major — DTO leaks into presentation.**
> `ProfileScreen.kt:31` — `ProfileUiState.user: UserDto` exposes a
> `@Serializable` data class directly to Compose. Couples the UI to the
> wire format (a backend field rename breaks the UI layer directly) and
> bypasses the domain-model boundary in `android-architecture`. Fix: map
> `UserDto` → `User` (domain) in the repository, and have
> `ProfileUiState` hold `User` or a UI-shaped projection of it.

> **Major — missing regression test on bug fix.**
> The PR fixes the "search crashes on empty query" bug (`SearchViewModel.kt:70`)
> but adds no test reproducing the empty-query crash. Per
> `workflows/bug-fix`, a fix without a characterization/regression test is
> incomplete — the same class of bug can reappear silently on a future
> refactor.

## Minor

> **Minor — inconsistent error-state pattern.**
> `OrderDetailsViewModel.kt:88` models a failed load as
> `OrderUiState(error = "Something went wrong")` (a raw string), while
> every other ViewModel in this module uses a sealed `UiError` type
> mapped to a string resource at the Compose layer. Not incorrect, but
> diverges from the established pattern and loses localization support.

## Suggestion

> **Suggestion — collapsible `when` branches.**
> `PaymentStatusMapper.kt:15` — three `when` branches each return the same
> value; could combine with `,` for a slightly shorter, equally clear
> mapping. Non-blocking.

## Positive

> **Positive — well-scoped characterization tests.**
> `LegacyPricingCalculatorTest.kt` adds characterization tests covering
> every existing branch of `LegacyPricingCalculator` before the refactor
> in this PR touches it, including two undocumented edge cases (negative
> quantity, zero-price item) that turned out to matter for the refactor's
> correctness. This is exactly the `workflows/refactor` process working as
> intended.
