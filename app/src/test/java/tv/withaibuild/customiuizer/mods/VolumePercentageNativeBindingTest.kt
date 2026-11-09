package tv.withaibuild.customiuizer.mods

import android.app.Application
import android.text.Editable
import android.text.TextWatcher
import android.widget.SeekBar
import android.widget.TextView
import com.android.systemui.miui.volume.MiuiVolumeDialogImpl
import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Executable
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class VolumePercentageNativeBindingTest {
    private val hooks = LinkedHashMap<Executable, XposedInterface.Hooker>()
    private val previous = XposedHelpers.moduleInst
    private val savedPrefs = MainModule.mPrefs.getAll()
    private val context = RuntimeEnvironment.getApplication()

    @Before fun install() {
        MainModule.mPrefs.clear()
        XposedHelpers.moduleInst = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface::class.java)) { _, call, args ->
            if (call.name != "hook") return@newProxyInstance null
            val target = args!![0] as Executable
            object : XposedInterface.HookBuilder {
                override fun setPriority(priority: Int): XposedInterface.HookBuilder = this
                override fun setExceptionMode(mode: XposedInterface.ExceptionMode): XposedInterface.HookBuilder = this
                override fun setId(id: String?): XposedInterface.HookBuilder = error("No new ID")
                override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle {
                    hooks[target] = hooker
                    return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(XposedInterface.HookHandle::class.java)) { _, _, _ -> null } as XposedInterface.HookHandle
                }
            }
        } as XposedInterface
        SystemUIControlCenterHooks.ShowVolumePctHook(requireNotNull(javaClass.classLoader))
    }

    @After fun restore() {
        XposedHelpers.moduleInst = previous
        MainModule.mPrefs.replaceSnapshot(savedPrefs)
        for (name in listOf("mPctRef", "mPctOwnerRef", "volumePctFeedback")) {
            SystemUIControlCenterHooks::class.java.getDeclaredField(name).apply { isAccessible = true }.set(null, null)
        }
    }

    private fun call(owner: Any, method: String, vararg args: Any) {
        val target = hooks.keys.single { it.name == method }
        val chain = object : XposedInterface.Chain {
            override fun getExecutable() = target
            override fun getThisObject() = owner
            override fun getArgs() = args.toList()
            override fun getArg(index: Int) = args[index]
            override fun proceed(): Any? = (target as java.lang.reflect.Method).invoke(owner, *args)
            override fun proceed(args: Array<Any?>) = proceed()
            override fun proceedWith(thisObject: Any) = proceed()
            override fun proceedWith(thisObject: Any, args: Array<Any?>) = proceed()
        }
        hooks.getValue(target).intercept(chain)
    }

    private fun label(dialog: MiuiVolumeDialogImpl): TextView =
        (0 until dialog.window.childCount).map { dialog.window.getChildAt(it) }.filterIsInstance<TextView>().single()

    @Test fun initialAndSameLevelShowsReadNativeStateWithoutAProgressCallback() {
        val dialog = MiuiVolumeDialogImpl(context)
        call(dialog, "showVolumeDialogH", 1)
        assertEquals("33%", label(dialog).text.toString())
        call(dialog, "dismissH", 1)
        assertEquals(1, dialog.window.childCount)
        call(dialog, "showVolumeDialogH", 1)
        assertEquals("33%", label(dialog).text.toString())
        assertEquals(android.view.View.VISIBLE, label(dialog).visibility)
    }

    @Test fun nativeStateStreamSwitchAndRepeatedFramesOnlyChangeTheCurrentLabelOnce() {
        val dialog = MiuiVolumeDialogImpl(context)
        call(dialog, "showVolumeDialogH", 1)
        var textChanges = 0
        label(dialog).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { textChanges++ }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        val column = dialog.mColumns[0]
        val listener = dialog.VolumeSeekBarChangeListener(column)
        repeat(100) { call(listener, "onProgressChanged", column.slider, 5000, true) }
        assertEquals(0, textChanges)
        dialog.mActiveStream = 0
        dialog.mColumns[1].ss.level = 12
        call(dialog, "updateColumnH", dialog.mColumns[1])
        assertEquals("80%", label(dialog).text.toString())
        call(dialog, "updateColumnH", column)
        assertEquals("80%", label(dialog).text.toString())
        assertEquals(1, textChanges)
    }

    @Test fun animationUsesItsFinalTargetAndOldOwnersCannotChangeOrDestroyTheNewPanel() {
        val old = MiuiVolumeDialogImpl(context)
        call(old, "showVolumeDialogH", 1)
        val current = MiuiVolumeDialogImpl(context)
        call(current, "showVolumeDialogH", 1)
        val column = current.mColumns[0]
        column.anim = android.animation.ObjectAnimator.ofInt(column.slider, "progress", 0, 15000).apply { duration = 10000; start() }
        try {
            column.animTargetProgress = 12000
            call(current.VolumeSeekBarChangeListener(column), "onProgressChanged", column.slider, 1000, false)
            assertEquals("80%", label(current).text.toString())
            call(old, "updateColumnH", old.mColumns[0])
            call(old, "destroy")
            assertEquals("80%", label(current).text.toString())
            call(current, "destroy")
            assertEquals(1, current.window.childCount)
        } finally { column.anim?.cancel() }
    }
}
