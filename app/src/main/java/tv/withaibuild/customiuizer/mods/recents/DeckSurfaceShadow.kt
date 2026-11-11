package tv.withaibuild.customiuizer.mods.recents

import android.graphics.Rect
import android.graphics.RectF
import java.lang.ref.WeakReference
import java.lang.reflect.Modifier
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.mods.utils.FatalErrors

/** Share projection between the thumbnail and its native transition leash.
 * One receipt belongs to that native target, not a window or animation manager.
 * Stable values need no extra Surface transaction; native target disposal releases
 * the receipt, and retired geometry clears the projection on its next transaction. */
internal class DeckSurfaceShadow(helper: Class<*>,remote: Class<*>,targets: Class<*>,transforms: Class<*>,times: Class<*>,surface: Class<*>) {
    constructor(loader: ClassLoader):this(
        XposedHelpers.findClass("com.miui.home.recents.util.ClipAnimationHelper",loader),
        XposedHelpers.findClass("com.android.systemui.shared.recents.system.RemoteAnimationTargetCompat",loader),
        XposedHelpers.findClass("com.miui.home.recents.util.RemoteAnimationTargetSet",loader),
        XposedHelpers.findClass("com.miui.home.recents.util.ClipAnimationHelper\$TransformParams",loader),
        XposedHelpers.findClass("com.miui.home.recents.util.ClipAnimationHelper\$TransformTimes",loader),
        XposedHelpers.findClass("com.android.systemui.shared.recents.system.SyncRtSurfaceTransactionApplierCompat\$SurfaceParams",loader))
    private val prefix=arrayOf(remote,remote,remote,targets,Int::class.javaPrimitiveType!!,transforms,transforms,
        Int::class.javaPrimitiveType!!,times,Boolean::class.javaPrimitiveType!!)
    val calculate=helper.declaredMethods.single { method -> method.name=="calculateSurfaceParams" &&
        (method.parameterTypes.contentEquals(prefix) || method.parameterTypes.contentEquals(prefix+Boolean::class.javaPrimitiveType!!)) }
        .apply { require(returnType==surface && !Modifier.isStatic(modifiers));isAccessible=true }
    private val task=remote.getDeclaredField("taskId").apply { isAccessible=true;require(type==Int::class.javaPrimitiveType) }
    private val mode=remote.getDeclaredField("mode").apply { isAccessible=true;require(type==Int::class.javaPrimitiveType) }
    private val source=helper.getDeclaredField("mSourceStackBounds").apply { isAccessible=true;require(type==Rect::class.java) }
    private val current=helper.getDeclaredField("mCurrentRectWithInsets").apply { isAccessible=true;require(type==RectF::class.java) }
    private val flags=surface.getDeclaredField("flags").apply { isAccessible=true;require(type==Int::class.javaPrimitiveType) }
    private val radius=surface.getDeclaredField("shadowRadius").apply { isAccessible=true;require(type==Float::class.javaPrimitiveType) }
    /** Main publishes a coherent pair; the native gesture thread reads no View. */
    class CardState(val taskId: Int,val width: Int,val height: Int,val ownerToken: Any) {
        @Volatile var values=0L
            private set
        fun publish(target: Float,level: Float) {
            val next=(target.toRawBits().toLong() shl 32) or (level.toRawBits().toLong() and 0xffffffffL)
            if(values!=next) values=next
        }
    }
    private class Projection(state: CardState) {
        val state=WeakReference(state)
        @Volatile var last=Float.NaN
    }
    fun apply(owner: DeckOwner?,helper: Any,remote: Any,targetMode: Int,params: Any,
        openingTask: Int = -1,openingWidth: Float = 0f) {
        var projection=XposedHelpers.getAdditionalInstanceField(remote,PROJECTION) as? Projection
        val original=flags.getInt(params)
        if(original and SHADOW!=0) { projection?.last=Float.NaN;return }
        val bounds=source.get(helper) as Rect
        val state=if(projection==null) owner?.surfaceCardState(task.getInt(remote))
            else projection.state.get()?.takeIf { it.ownerToken===owner?.surfaceToken }
        val values=state?.values ?: 0L
        val opening=targetMode==0 && openingTask>=0 && task.getInt(remote)==openingTask
        val target=if(opening) openingWidth else Float.fromBits((values ushr 32).toInt())
        val level=Float.fromBits(values.toInt())
        val admitted=state!=null && target>0f && level.isFinite() && mode.getInt(remote)==targetMode && (targetMode==1 || opening) &&
            state.width==bounds.width() && state.height==bounds.height()
        if(projection==null && !admitted) return
        if(projection==null) {
            projection=Projection(state!!)
            XposedHelpers.setAdditionalInstanceField(remote,PROJECTION,projection)
        }
        val value=if(admitted) {
            val width=(current.get(helper) as RectF).width()
            val full=bounds.width().toFloat()
            val progress=if(full>target && target>0f && width.isFinite()) ((full-width)/(full-target)).coerceIn(0f,1f) else 0f
            // Ease the native width fraction at both endpoints so takeover and
            // the fullscreen end do not introduce a sharp change of projection.
            level*progress*progress*(3f-2f*progress)
        } else 0f
        if(projection.last==value) return
        radius.setFloat(params,value)
        flags.setInt(params,original or SHADOW)
        projection.last=value
    }
    companion object {
        fun resolve(loader: ClassLoader): DeckSurfaceShadow? = try { DeckSurfaceShadow(loader) }
        catch(error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            XposedHelpers.log("Deck: native Surface projection ABI unavailable; retaining card shadows",error)
            null
        }
        // A14 SurfaceParams.Builder.withShadowRadius / applyTo contract.
        private const val SHADOW=0x100
        private const val PROJECTION="a14.recents.deck.surface.shadow"
    }
}
