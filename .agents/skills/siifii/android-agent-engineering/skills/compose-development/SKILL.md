---
name: compose-development
description: Use when writing or reviewing Jetpack Compose UI - state hoisting, ViewModel ownership of state, immutable UI state, recomposition and stability, remember/rememberSaveable, derivedStateOf, side-effect APIs (LaunchedEffect/DisposableEffect/SideEffect), lifecycle-aware Flow collection, navigation, accessibility, previews, Compose performance, and Compose UI testing.
metadata:
  version: "0.1.0"
---

# Compose Development

Default patterns for Jetpack Compose screens and components. See
`android-architecture` for where UI state fits relative to `domain`, and
`kotlin-coroutines-flow` for the Flow-collection detail behind
"lifecycle-aware collection" below.

## State hoisting and ownership

A composable that needs state either **owns it locally** (pure UI state
with no business meaning, e.g. "is this dropdown expanded") via
`remember`, or **receives it hoisted** from a caller/ViewModel. A
composable should not do both for the same piece of state. The rule of
thumb: if a ViewModel, a use case, or persistence ever needs to know this
value, it's hoisted; if it's purely how-this-widget-looks-right-now, it's
local.

```kotlin
// Stateless, hoisted — reusable, previewable, testable in isolation
@Composable
fun SearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(value = query, onValueChange = onQueryChange, modifier = modifier)
}

// Stateful caller owns/hoists the value
@Composable
fun SearchScreen(viewModel: SearchViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SearchBar(query = uiState.query, onQueryChange = viewModel::onQueryChange)
}
```

## ViewModel owns state; the composable is a function of it

The `ViewModel` exposes one immutable `UiState` (a `data class` or sealed
hierarchy) via `StateFlow`. The composable never mutates that state
directly — every mutation goes through a ViewModel event method. Don't
split a screen's state across five separate `mutableStateOf` fields inside
the ViewModel; one cohesive, immutable state object makes "what can this
screen look like" reviewable at a glance and makes `rememberSaveable`/
process-death recovery tractable.

```kotlin
data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class SearchViewModel(private val search: SearchUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        viewModelScope.launch { runSearch(query) }
    }
    // ...
}
```

**Flag:** a composable holding business state in `remember` that survives
navigation or should survive process death (that belongs in the
ViewModel/`SavedStateHandle`), or a `MutableState`/`var` exposed as a
public property from a ViewModel instead of an immutable `StateFlow`.

## Immutability and stability

Compose's recomposition skipping depends on the compiler being able to
prove a parameter is stable. Use `data class` with `val` properties (never
`var`) for UI state, and prefer `List`/immutable collection types over
raw mutable collections in a composable's parameters — a `MutableList`
parameter is unstable and defeats skipping for that composable and
everything downstream of it. If the module uses the Compose compiler's
stability configuration or `@Immutable`/`@Stable` annotations, apply them
deliberately (they're a promise to the compiler, not a suppression) rather
than sprinkling them to silence a lint warning without checking the type
actually is immutable.

## `remember`, `rememberSaveable`, `derivedStateOf`

- `remember` survives recomposition, not configuration change or process
  death. Use `rememberSaveable` for anything that should survive a
  rotation or process death and is cheap/simple enough to put in a
  `Bundle` (primitives, `Parcelable`, or with a custom `Saver`).
- `derivedStateOf` is for a value computed from other state that should
  only trigger recomposition when *its own result* changes, not every time
  an input changes — typically a filtered/derived list or a boolean
  computed from a scroll position. Wrapping something in `derivedStateOf`
  that's already cheap and changes exactly when its inputs change adds
  overhead for no benefit; reach for it when the naive computation would
  cause recomposition more often than the derived value actually changes.
- Don't call `remember { mutableStateOf(...) }` with a key that should
  reset the value on change but is missing from `remember`'s keys — a
  stale `remember`ed value across an item's identity change (e.g. in a
  reused list item) is a common bug. Use `remember(key1, key2) { ... }`.

## Side effects

Pick the effect API by what it's for, not by habit:

- `LaunchedEffect(key)` — launch a coroutine tied to composition; restarts
  when `key` changes, cancels when the composable leaves composition. Use
  for one-shot suspend work triggered by entering composition or a value
  changing (e.g. triggering a scroll, a one-time analytics event).
- `DisposableEffect(key)` — for non-suspend setup/teardown that needs an
  explicit cleanup (registering/unregistering a listener, a broadcast
  receiver). Always return `onDispose { ... }`.
- `SideEffect` — runs on every successful recomposition, no cancellation
  semantics; for publishing Compose state out to non-Compose code (e.g.
  updating an analytics library's current-screen property). Rare.
- Never launch a coroutine with `rememberCoroutineScope()` (or worse, a
  raw `CoroutineScope()`) for work that `LaunchedEffect` would model more
  simply and with correct cancellation — reach for
  `rememberCoroutineScope()` specifically for coroutines started from an
  event callback (a click handler), not from composition itself.

## Lifecycle-aware Flow collection

Always `collectAsStateWithLifecycle()` (from
`androidx.lifecycle:lifecycle-runtime-compose`) for collecting a
ViewModel's `StateFlow`/`Flow` in a composable, not plain `collectAsState()`
— the lifecycle-aware variant stops collecting when the UI goes to the
background and resumes on return, avoiding wasted work and, for `SharedFlow`
event streams, avoiding missed-or-duplicated delivery around
configuration change. `collectAsState()` without lifecycle awareness is a
correctness issue, not just an optimization, whenever the upstream Flow
has side effects tied to being actively collected (network subscriptions,
location updates). See `kotlin-coroutines-flow` for the ViewModel-side
half of this.

## Navigation

Navigation events (navigate, pop, show a snackbar) are one-shot — model
them as an event, not as a piece of `UiState` that a composable might
re-trigger on recomposition or configuration change. A `Channel`/
`SharedFlow` collected with `LaunchedEffect` in the screen, or a
dedicated one-shot event queue in `UiState` that's consumed-and-cleared,
are both acceptable; a `navigateTo: String?` field in `UiState` that never
gets cleared after firing is not — it re-fires on the next recomposition
after rotation.

## Accessibility

- Every actionable element needs a content description or visible text —
  `Icon`/`IconButton` with no label and no `contentDescription` is a
  finding, not a style nit.
  `contentDescription = null` is correct only for a purely decorative
  icon that's redundant with adjacent text.
- Respect minimum touch target size (48dp) for tappable elements.
  Respect the user's font-scale setting — avoid hardcoded `sp`-independent
  sizing that clips text at larger scale, and test at a large font scale,
  not just default.
- Group related content with `Modifier.semantics(mergeDescendants = true)`
  where a screen reader should announce a composite element (e.g. a list
  row) as one unit rather than reading every child separately.

## Previews

Every reusable composable gets at least one `@Preview` with representative
fake data (not a live ViewModel/DI call inside a preview — pass hoisted
parameters directly). Cover the states that actually differ visually:
loading, empty, error, populated, and (for anything text-heavy) a
long-content case, at minimum for screen-level composables.

## Performance

- Oversized composables — one function rendering an entire screen with no
  extraction — recompose as a unit and are hard to reason about for
  stability. Extract logical sections into their own composables with
  narrow, stable parameters (see `android-code-review` for the
  size/responsibility heuristic used when flagging this).
- Avoid allocating lambdas/objects inline in a hot composable path when a
  stable, hoisted reference would do (e.g. `onClick = { viewModel.onX() }`
  is fine; constructing a new list/formatter/regex inline on every
  recomposition is not — hoist or `remember` it).
- Use `LazyColumn`/`LazyRow` `key = { item.id }` for list items so Compose
  can track item identity across reordering/insertion instead of
  recomposing everything.

## Compose UI testing

Test composables through `createComposeRule()` / `createAndroidComposeRule()`
against semantics (`onNodeWithText`, `onNodeWithContentDescription`,
`onNodeWithTag` as a last resort when there's no accessible label to key
off), not implementation details. State hoisting makes this practical:
test a stateless composable directly with fake state and captured
lambdas; test a stateful screen's ViewModel logic separately as a plain
unit test (see `android-testing`), and reserve full-screen Compose tests
for the wiring between them plus genuinely UI-only behavior (layout,
interaction, accessibility).
