package tv.withaibuild.customiuizer.mods.recents

import java.lang.reflect.Modifier

import android.graphics.Matrix
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.floor
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.mods.utils.FatalErrors

/** Opening app leashes sit above the entire Launcher surface on this A14 build.
 * Keep its native matrix/leash/clock, cropping only the portion covered by front
 * Deck cards. Rounded-edge occlusion is conservatively rectangular. */
internal class DeckSurfaceOcclusion(helper: Class<*>, remote: Class<*>, targets: Class<*>,
    transforms: Class<*>, times: Class<*>, surface: Class<*>) {
    constructor(helper: Class<*>):this(helper,
        XposedHelpers.findClass("com.android.systemui.shared.recents.system.RemoteAnimationTargetCompat",helper.classLoader),
        XposedHelpers.findClass("com.miui.home.recents.util.RemoteAnimationTargetSet",helper.classLoader),
        XposedHelpers.findClass("com.miui.home.recents.util.ClipAnimationHelper\$TransformParams",helper.classLoader),
        XposedHelpers.findClass("com.miui.home.recents.util.ClipAnimationHelper\$TransformTimes",helper.classLoader),
        XposedHelpers.findClass("com.android.systemui.shared.recents.system.SyncRtSurfaceTransactionApplierCompat\$SurfaceParams",helper.classLoader))
    private val prefix=arrayOf(remote,remote,remote,targets,Int::class.javaPrimitiveType!!,
        transforms,transforms,Int::class.javaPrimitiveType!!,times,Boolean::class.javaPrimitiveType!!)
    val calculate=(try { helper.getDeclaredMethod("calculateSurfaceParams",*prefix) }
        catch(_: NoSuchMethodException) { helper.getDeclaredMethod("calculateSurfaceParams",*(prefix+Boolean::class.javaPrimitiveType!!)) })
        .apply { require(returnType==surface && !Modifier.isStatic(modifiers)); isAccessible=true }
    private fun field(type: Class<*>,name: String)=type.getDeclaredField(name).apply { require(!Modifier.isStatic(modifiers)); isAccessible=true }
    private val target=calculate.parameterTypes[0]
    private val taskId=field(target,"taskId")
    private val mode=field(target,"mode")
    private val container=field(target,"sourceContainerBounds")
    private val position=field(target,"position")
    private val source=field(helper,"mSourceStackBounds")
    private val current=field(helper,"mCurrentRectWithInsets")
    private val result=calculate.returnType
    private val matrix=field(result,"matrix")
    private val crop=field(result,"windowCrop")
    private val names=arrayOf("surface","flags","alpha","matrix","windowCrop","layer","relativeTo",
        "relativeLayer","homeSurface","cornerRadius","backgroundBlurRadius","visible","shadowRadius","isShow")
    private val fields=names.map { field(result,it) }
    private val constructor=result.getDeclaredConstructor(*fields.map { it.type }.toTypedArray()).apply { require(!Modifier.isStatic(modifiers)); isAccessible=true }
    init {
        require(taskId.type==Int::class.javaPrimitiveType && mode.type==Int::class.javaPrimitiveType &&
            container.type==Rect::class.java && position.type==Point::class.java && source.type==Rect::class.java &&
            current.type==RectF::class.java && matrix.type==Matrix::class.java && crop.type==Rect::class.java)
        for(i in intArrayOf(1,5,7,10)) require(fields[i].type==Int::class.javaPrimitiveType)
        for(i in intArrayOf(2,9,12)) require(fields[i].type==Float::class.javaPrimitiveType)
        for(i in intArrayOf(11,13)) require(fields[i].type==Boolean::class.javaPrimitiveType)
    }
    private val mapped=RectF()
    private val values=FloatArray(9)

    fun apply(owner: DeckOwner,helper: Any,remote: Any,targetMode: Int,params: Any): Any {
        if(!owner.allowed || !map(helper,remote,targetMode,params,owner.openingTaskId,owner.current.fullWidth,owner.current.fullHeight)) return params
        val edge=owner.openingBoundary(mapped) ?: return params
        return clip(params,values[0],values[2],edge)
    }
    fun apply(presentation: DeckOpenAlignment.Surface,helper: Any,remote: Any,targetMode: Int,params: Any): Any {
        if(!presentation.active || !map(helper,remote,targetMode,params,
                presentation.taskId,presentation.width,presentation.height,presentation.nativeBounds,presentation.nativeMatrix)) return params
        val edge=presentation.boundary(presentation.nativeBounds)
        return if(edge.isFinite()) clip(params,presentation.nativeMatrix[0],presentation.nativeMatrix[2],edge) else params
    }
    private fun map(helper: Any,remote: Any,targetMode: Int,params: Any,selected: Int,width: Float,height: Float,
        mapped: RectF=this.mapped,values: FloatArray=this.values): Boolean {
        if(mode.getInt(remote)!=0 || targetMode!=0 || taskId.getInt(remote)!=selected) return false
        // Native Surface bounds follow the visual display, while a fixed phone
        // Window remains portrait. Compare with the prepared visual viewport.
        val bounds=source.get(helper) as Rect
        val targetBounds=container.get(remote) as Rect
        val targetPosition=position.get(remote) as Point
        if(bounds.left!=0 || bounds.top!=0 || targetBounds.left!=0 || targetBounds.top!=0 ||
            targetPosition.x!=0 || targetPosition.y!=0 ||
            width!=bounds.width().toFloat() || height!=bounds.height().toFloat()) return false
        val nativeCrop=crop.get(params) as? Rect ?: return false
        val nativeMatrix=matrix.get(params) as? Matrix ?: return false
        nativeMatrix.getValues(values)
        if(!axisAligned(values) || nativeCrop.isEmpty) return false
        mapped.set(nativeCrop); nativeMatrix.mapRect(mapped)
        val anchor=current.get(helper) as RectF
        // Native roundOut can expand a crop by one buffer pixel per edge.
        val tolerance=maxOf(values[0],values[4])+1f
        if(abs(mapped.left-anchor.left)>tolerance || abs(mapped.top-anchor.top)>tolerance ||
            abs(mapped.right-anchor.right)>tolerance || abs(mapped.bottom-anchor.bottom)>tolerance) return false
        return true
    }
    internal fun clip(params: Any,scaleX: Float,translateX: Float,edge: Float): Any {
        val nativeCrop=crop.get(params) as Rect
        val cut=bufferCut(nativeCrop,scaleX,translateX,edge)
        if(cut>=nativeCrop.right) return params
        if(cut<=nativeCrop.left) {
            // Empty native crops are ignored by SurfaceParams.applyTo. Copy this
            // frame with alpha zero instead, preserving every native owner/flag.
            val args=fields.map { it.get(params) }.toTypedArray()
            args[2]=0f
            return constructor.newInstance(*args)
        }
        // Builder.withWindowCrop already copied this Rect for this target/frame.
        nativeCrop.right=cut
        return params
    }
    companion object {
        fun resolve(helper: Class<*>): DeckSurfaceOcclusion? = try { DeckSurfaceOcclusion(helper) }
        catch(error: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(error)
            XposedHelpers.log("Deck: native opening crop ABI unavailable; retaining native Surface",error)
            null
        }
        internal fun axisAligned(v: FloatArray)=v.all { it.isFinite() } && v[0]>0f && v[4]>0f &&
            v[1]==0f && v[3]==0f && v[6]==0f && v[7]==0f && v[8]==1f
        internal fun bufferCut(crop: Rect,scaleX: Float,translateX: Float,screenEdge: Float): Int =
            floor((screenEdge-translateX)/scaleX).toInt().coerceAtMost(crop.right)
    }
}
