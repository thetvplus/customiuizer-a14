package tv.withaibuild.customiuizer.mods.recents

import java.lang.reflect.Modifier
import tv.withaibuild.customiuizer.mods.utils.CleanupActions

/** Parameter storage varies by Launcher; scenes and lifetime do not. */
internal interface DeckSpringTuning {
    class Snapshot(val original: FloatArray,val applied: FloatArray)
    fun apply(spring: Any,scene: DeckSpringScene): Snapshot
    fun restore(spring: Any,saved: Snapshot)
}

/** The native setter owns response conversion and system duration ratio.
 * Keep fields and forces coherent through retargeting; release owned fields at
 * native end/cancel, without overwriting a subsequent native scene's settings. */
internal class DeckSpringParameters(type: Class<*>): DeckSpringTuning {
    private val enumType=type.declaredClasses.singleOrNull { it.simpleName=="RectAnimType" && it.isEnum }
    private val axisType=enumType ?: Class.forName("androidx.dynamicanimation.animation.FloatPropertyCompat",false,type.classLoader)
    private val setter=type.getDeclaredMethod("setAnimParam",axisType,Float::class.javaPrimitiveType,
        Float::class.javaPrimitiveType).apply { require(returnType==Void.TYPE && !Modifier.isStatic(modifiers)); isAccessible=true }
    // Older A14 uses native property singletons; newer legacy hosts use enums.
    // Both setters perform the ROM's response/duration conversion before init.
    private val axes=if(enumType!=null) arrayOf("RECT_CENTER_X","RECT_CENTER_Y","RECT_WIDTH","RECT_RATIO").map { name ->
        enumType.enumConstants!!.single { (it as Enum<*>).name==name }
    } else arrayOf("RECT_CENTERX","RECT_CENTERY","RECT_WIDTH","RECT_RATIO").map { name ->
        type.getDeclaredField(name).apply {
            require(axisType.isAssignableFrom(this.type) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers))
            isAccessible=true
        }.get(null).also { require(it!=null) }
    }
    private val fields=arrayOf("CenterX","CenterY","Width","Ratio").flatMap { axis ->
        arrayOf("m${axis}Damping","m${axis}Stiffness").map { type.getDeclaredField(it).apply {
            require(this.type==Float::class.javaPrimitiveType && !Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers)); isAccessible=true
        } }
    }
    override fun apply(spring: Any,scene: DeckSpringScene): DeckSpringTuning.Snapshot {
        val saved=FloatArray(fields.size) { fields[it].getFloat(spring) }
        try { for(axis in axes) setter.invoke(spring,axis,scene.damping,scene.response) }
        catch(error: Throwable) {
            val cleanup=CleanupActions(error)
            for(i in fields.indices) cleanup.run { fields[i].setFloat(spring,saved[i]) }
            cleanup.finish(); throw error
        }
        return DeckSpringTuning.Snapshot(saved,FloatArray(fields.size) { fields[it].getFloat(spring) })
    }
    override fun restore(spring: Any,saved: DeckSpringTuning.Snapshot) {
        val cleanup=CleanupActions()
        for(i in fields.indices step 2) cleanup.run {
            // A native scene owns the pair even if it changes only one member.
            if(fields[i].getFloat(spring)==saved.applied[i] && fields[i+1].getFloat(spring)==saved.applied[i+1]) {
                fields[i].setFloat(spring,saved.original[i]); fields[i+1].setFloat(spring,saved.original[i+1])
            }
        }
        cleanup.finish()
    }
}
