package tv.withaibuild.customiuizer.mods.utils.feature

/**
 * Unified Dynamic Island event consumed by the single shared renderer.
 *
 * ROM StrongToast (or its control path) supplies identity and content; the renderer never
 * guesses type from translated strings, drawable names or accidental View class differences.
 */
internal enum class DynamicIslandEventType {
    MUTE,
    DND,
    CHARGING,
    OTHER,
}

/**
 * Immutable per-event payload. [config] is frozen at event creation so an in-flight island
 * cannot half-apply a later preference change.
 *
 * @property rendererToken Stable token identifying the shared Dynamic Island renderer path.
 *   All event types share the same token so MUTE / DND / CHARGING cannot drift into forks.
 */
internal data class DynamicIslandEvent(
    val type: DynamicIslandEventType,
    val sourceToken: Any,
    val config: StrongToastRuntimeSnapshot,
    val durationMs: Long = DEFAULT_DURATION_MS,
    val rendererToken: String = SHARED_RENDERER_TOKEN,
) {
    companion object {
        const val SHARED_RENDERER_TOKEN = "DynamicIslandHost"
        const val DEFAULT_DURATION_MS = 2500L
    }
}
