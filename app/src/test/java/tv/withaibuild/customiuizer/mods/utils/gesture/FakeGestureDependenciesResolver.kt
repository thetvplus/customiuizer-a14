package tv.withaibuild.customiuizer.mods.utils.gesture

class FakeGestureDependenciesResolver : GestureDependenciesResolver {

    private val results = mutableMapOf<Pair<Int, String>, GestureDependenciesResult>()

    fun set(ownerId: Int, classLoaderIdentity: String, result: GestureDependenciesResult) {
        results[ownerId to classLoaderIdentity] = result
    }

    override fun prepare(
        ownerId: Int,
        classLoaderIdentity: String,
        context: Any,
    ): GestureDependenciesResult {
        return results[ownerId to classLoaderIdentity] ?: GestureDependenciesResult.NotReady
    }
}
