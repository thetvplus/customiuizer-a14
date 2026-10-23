package tv.withaibuild.customiuizer.mods.island

import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/** Existing causal markers, now owned by the window and only present when correlation needs them. */
internal class IslandSystemActionMarkers(private val install: (String, IslandSystemKind) -> AutoCloseable?) : AutoCloseable {
    private val handles = linkedMapOf<String, AutoCloseable>() // At most six existing targets.
    private var requested = emptySet<String>()
    private var closed = false

    fun update(kinds: Set<IslandSystemKind>) {
        if (closed) return
        val wanted = targets.filterValues { kind -> kind in kinds && when (kind) {
            IslandSystemKind.RINGER -> IslandSystemKind.DND in kinds
            else -> IslandSystemKind.AIRPLANE in kinds
        } }
        if (wanted.keys == requested) return
        requested = wanted.keys.toSet()
        val iterator = handles.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in wanted) { ModuleHelper.guarded { entry.value.close() }; iterator.remove() }
        }
        for ((target, kind) in wanted) if (target !in handles)
            ModuleHelper.guarded<AutoCloseable?>(null) { install(target, kind) }?.let { handles[target] = it }
    }

    override fun close() {
        if (closed) return
        closed = true
        for (handle in handles.values.toList().asReversed()) ModuleHelper.guarded { handle.close() }
        handles.clear(); requested = emptySet()
    }

    companion object {
        private val targets = linkedMapOf(
            "setRingerMode" to IslandSystemKind.RINGER, "setRingerModeInternal" to IslandSystemKind.RINGER,
            "MuteTile" to IslandSystemKind.RINGER, "MiuiWifiTile" to IslandSystemKind.WIFI,
            "BluetoothTile" to IslandSystemKind.BLUETOOTH, "MiuiCellularTile" to IslandSystemKind.DATA)

        fun native(loader: ClassLoader, owner: WeakReference<IslandActivityController>) = IslandSystemActionMarkers { target, kind ->
            val live = AtomicBoolean(true)
            val callback = object : MethodHook() {
                override fun before(callback: BeforeHookCallback) = ModuleHelper.guarded {
                    if (live.get()) owner.get()?.independentSystemAction(kind)
                }
            }
            val handle = if (target.startsWith("setRinger"))
                ModuleHelper.findAndHookMethod(android.media.AudioManager::class.java, target, Int::class.javaPrimitiveType, callback)
            else ModuleHelper.findAndHookMethod("com.android.systemui.qs.tiles.$target", loader,
                "handleClick", android.view.View::class.java, callback)
            handle?.let { AutoCloseable { live.set(false); it.unhook() } }
        }
    }
}
