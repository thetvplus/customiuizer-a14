package tv.withaibuild.customiuizer.mods.volume

import tv.withaibuild.customiuizer.mods.SystemUIToastPresentation
import tv.withaibuild.customiuizer.mods.ToastCapsuleLease
import tv.withaibuild.customiuizer.mods.island.DynamicIslandActivities
import tv.withaibuild.customiuizer.mods.island.IslandActivityState
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper

/** One native volume-window operation, borrowing existing surfaces and their own deadlines. */
internal class VolumePercentageFeedback(private val displayId: Int) {
    private var lease: Any? = null
    private var closed = false

    fun update(text: String, stream: Int): Boolean {
        if (closed) return false
        val previous = lease
        val updated = when (previous) {
            is IslandActivityState.Lease -> DynamicIslandActivities.updateVolume(text, stream, displayId, previous)
            is ToastCapsuleLease -> SystemUIToastPresentation.updateVolumeCapsule(previous, text)
            else -> false
        }
        if (updated) return true
        closeLease()
        lease = DynamicIslandActivities.showVolume(text, stream, displayId)
            ?: SystemUIToastPresentation.showVolumeCapsule(text, displayId)
        return lease != null
    }

    fun close() { closed = true; closeLease() }
    fun release() { closeLease() }
    fun isPresented(): Boolean = when (val current = lease) {
        is IslandActivityState.Lease -> DynamicIslandActivities.ownsVolume(current)
        is ToastCapsuleLease -> SystemUIToastPresentation.ownsVolumeCapsule(current)
        else -> false
    }
    private fun closeLease() {
        val value = lease
        lease = null
        ModuleHelper.guarded {
            when (value) {
                is IslandActivityState.Lease -> DynamicIslandActivities.releaseToast(value)
                is ToastCapsuleLease -> SystemUIToastPresentation.releaseVolumeCapsule(value)
            }
        }
    }

    companion object { const val KEY = "system_volume_pct_presentation" }
}
