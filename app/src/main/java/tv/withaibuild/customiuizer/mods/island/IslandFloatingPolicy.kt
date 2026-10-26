package tv.withaibuild.customiuizer.mods.island

import android.os.UserHandle

/** Published scalar identities only. No task lookup or Binder call from presentation/draw. */
internal object IslandFloatingPolicy {
    fun blocksMedia(enabled: Boolean, media: IslandMediaContent?, user: UserHandle?,
        freeformKnown: Boolean, freeform: Boolean, packages: Set<String>?, pip: IslandPipSnapshot,
        applicationOverlay: Boolean): Boolean {
        if (media == null) return false
        return excludes(enabled, media.packageName, media.user, user, freeformKnown, freeform, packages, pip, applicationOverlay)
    }

    fun excludes(enabled: Boolean, packageName: String, sourceUser: UserHandle?, user: UserHandle?,
        freeformKnown: Boolean, freeform: Boolean, packages: Set<String>?, pip: IslandPipSnapshot,
        applicationOverlay: Boolean): Boolean {
        if (!enabled || sourceUser == null || sourceUser != user) return false
        val pipActive = pip.pendingEntry || pip.state in 1..5
        return applicationOverlay || freeformKnown && freeform && packages?.contains(packageName) == true ||
            pipActive && pip.belongsTo(packageName, sourceUser)
    }
}
