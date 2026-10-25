package tv.withaibuild.customiuizer.mods

import java.util.concurrent.Executor
import java.util.function.Consumer
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Resetter
import org.robolectric.shadows.ShadowWindowManagerImpl

/** Model the public system blur setting and its event, independently of the MIUI material. */
@Implements(className = "android.view.WindowManagerImpl", isInAndroidSdk = false)
class DockWindowManagerShadow : ShadowWindowManagerImpl() {
    @Implementation fun isCrossWindowBlurEnabled() = enabled
    @Implementation fun addCrossWindowBlurEnabledListener(executor: Executor, listener: Consumer<Boolean>) {
        listeners[listener] = executor
        executor.execute { listener.accept(enabled) }
    }
    @Implementation fun removeCrossWindowBlurEnabledListener(listener: Consumer<Boolean>) {
        listeners.remove(listener)
    }

    companion object {
        private val listeners = LinkedHashMap<Consumer<Boolean>, Executor>()
        var enabled = true
            private set
        fun change(enabled: Boolean) {
            this.enabled = enabled
            listeners.toList().forEach { (listener, executor) -> executor.execute { listener.accept(enabled) } }
        }
        fun listenerCount() = listeners.size
        @JvmStatic @Resetter fun resetBlur() { enabled = true; listeners.clear() }
    }
}
