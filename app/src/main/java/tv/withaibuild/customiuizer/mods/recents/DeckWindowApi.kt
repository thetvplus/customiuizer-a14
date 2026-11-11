package tv.withaibuild.customiuizer.mods.recents

import java.lang.reflect.Modifier

import android.view.View
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/** 4.39.30 WindowElement replaces the older Quickstep spring return value.
 * Copy only the owned card's radius; native leashes, geometry, listeners,
 * HyperSpring parameters and finish policy retain their original values. */
internal class DeckWindowApi(element: Class<*>, private val params: Class<*>, provider: Class<*>,
    launcher: Class<*>, targets: Class<*>, closingResult: Class<*>) {
    constructor(loader: ClassLoader):this(
        XposedHelpers.findClass("com.miui.home.recents.anim.WindowElement",loader),
        XposedHelpers.findClass("com.miui.home.recents.anim.RectFParams",loader),
        XposedHelpers.findClass("com.miui.home.recents.anim.WindowAnimParamsProvider",loader),
        XposedHelpers.findClass("com.miui.home.launcher.Launcher",loader),
        Class.forName("[Lcom.android.systemui.shared.recents.system.RemoteAnimationTargetCompat;",false,loader),
        XposedHelpers.findClass("com.miui.home.recents.anim.AllElementParams",loader))
    val animate=element.getDeclaredMethod("animTo",Any::class.java).apply { require(returnType==Void.TYPE && !Modifier.isStatic(modifiers)) }
    val update=element.getDeclaredMethod("updateElementValue",Float::class.javaPrimitiveType,
        android.graphics.RectF::class.java,Float::class.javaPrimitiveType,Float::class.javaPrimitiveType,
        Boolean::class.javaPrimitiveType,android.graphics.Rect::class.java,android.graphics.RectF::class.java)
        .apply { require(returnType==Void.TYPE && !Modifier.isStatic(modifiers)) }
    val closing=provider.getDeclaredMethod("getClosingWindowToRecentAnimParams",launcher,targets,targets)
        .apply { require(returnType==closingResult && !Modifier.isStatic(modifiers)); isAccessible=true }
    private val current=element.getDeclaredField("currentRectFParams").apply { require(type==params && !Modifier.isStatic(modifiers)); isAccessible=true }
    private val animation=element.getDeclaredField("mAnim").apply { require(!Modifier.isStatic(modifiers)); isAccessible=true }
    private val start=animation.type.getDeclaredMethod("getStartRect").apply { require(returnType==android.graphics.RectF::class.java && !Modifier.isStatic(modifiers)) }
    private val fields=listOf("targetApps","startRect","targetRect","startRadius","endRadius",
        "startAlpha","endAlpha","animType","ignoreIcon","showTask","isQuickSwitchMode","targetView",
        "animListener","clipAnimationHelper","clearLastListener","currentDisplayRotation","homeRotation",
        "needFinishOnAnimEnd","currentTaskIndex","isSplitModeBack","isUseTranslucentAnim")
        .map { params.getDeclaredField(it).apply { require(!Modifier.isStatic(modifiers)); isAccessible=true } }
    private val constructor=params.getDeclaredConstructor(*fields.map { it.type }.toTypedArray())
    init {
        for(i in intArrayOf(1,2)) require(fields[i].type==android.graphics.RectF::class.java)
        for(i in intArrayOf(3,4,5,6)) require(fields[i].type==Float::class.javaPrimitiveType)
        for(i in intArrayOf(8,9,10,14,17,19,20)) require(fields[i].type==Boolean::class.javaPrimitiveType)
        for(i in intArrayOf(15,16,18)) require(fields[i].type==Int::class.javaPrimitiveType)
        require(fields[7].type.isEnum && View::class.java.isAssignableFrom(fields[11].type))
    }
    fun scene(value: Any): Boolean? {
        // Native homeRotation defaults to zero. Fixed-window phone landscape
        // legitimately updates currentDisplayRotation to 1/3 after animTo starts.
        if(!params.isInstance(value) || fields[10].getBoolean(value) || fields[19].getBoolean(value)) return null
        val rotation=fields[15].getInt(value)
        if(rotation!=0 && rotation!=1 && rotation!=3) return null
        return when((fields[7].get(value) as? Enum<*>)?.name) {
            "OPEN_FROM_RECENTS" -> true
            "CLOSE_TO_RECENTS" -> false
            else -> null
        }
    }
    fun view(value: Any)=fields[11].get(value) as? View
    fun index(value: Any)=fields[18].getInt(value)
    fun target(value: Any)=fields[2].get(value) as android.graphics.RectF
    fun scaled(value: Any,scale: Float,opening: Boolean,nativeScale: Float=1f): Any {
        // Opening leaves the current presentation; closing arrives at the frozen
        // endpoint with native root scale 1, not its temporary gesture scale.
        val factor=if(opening) scale*nativeScale else scale
        require(factor.isFinite() && factor>0f)
        val values=fields.map { it.get(value) }.toTypedArray()
        val radius=if(opening) 3 else 4
        values[radius]=(values[radius] as Float)*factor
        return constructor.newInstance(*values)
    }
    fun accepted(value: Any,element: Any)=current.get(element)===value
    fun spring(element: Any): Any?=animation.get(element)
    fun current(element: Any): Any?=current.get(element)
    fun start(element: Any)=start.invoke(animation.get(element)) as android.graphics.RectF
}
