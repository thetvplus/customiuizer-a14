package tv.withaibuild.customiuizer.mods.utils

import android.view.View
import java.lang.ref.WeakReference

/**
 * Per-View dark receiver registration for module-created type 91/92 text icons.
 *
 * The route uses [View.OnAttachStateChangeListener] to mirror the View attach/detach
 * lifecycle, but splits it into three independent phases:
 * - `attach/register` — add the View as a [DarkReceiver] and receive the current tint;
 * - `detach/unregister` — remove the receiver, but keep the listener for reattach;
 * - `terminal dispose` — remove the listener and any route tracking, regardless of
 *   whether the receiver was ever successfully registered.
 *
 * Callers that have a per-generation owner (for example the right-hand
 * `StatusBarDisplayRegistry`) must use the returned [DarkTintRegistrationHandle] to
 * dispose the registration when the generation is replaced. Callers without such an
 * owner (the left-hand `DeviceInfoMonitor`) can rely on View detach/re-attach alone.
 *
 * Registrations are exact-once per View per generation. A failed `addDarkReceiver`
 * does not leak the View: terminal dispose removes the listener and tracking even when
 * the receiver was never added.
 *
 * All operations are expected to run on the SystemUI main looper.
 */
internal object CustomTextIconTintRoute {

    /**
     * Opaque handle returned by [register]. Calling [release] disposes the registration
     * and is idempotent: repeated calls are no-ops.
     */
    interface DarkTintRegistrationHandle {
        fun release(reason: String)
    }

    internal val registrations = mutableListOf<DarkTintRegistration>()

    /**
     * Register [view] with the ROM [DarkIconDispatcher] when attached, release the
     * receiver on normal detach, and allow reattach to re-register.
     *
     * The [route] string is used only for diagnostics ("left" / "right").
     * [classLoader] is used to look up the ROM `DarkIconDispatcher` plugin instance.
     * A non-null [darkIconDispatcher] is used directly by callers that already have
     * the dispatcher.
     */
    @JvmOverloads
    fun register(
        view: View,
        classLoader: ClassLoader,
        route: String,
        darkIconDispatcher: Any? = null,
    ): DarkTintRegistrationHandle {
        val registration: DarkTintRegistration
        val handle: DarkTintRegistrationHandle
        synchronized(registrations) {
            prune()
            val existing = findLiveRegistration(view)
            if (existing != null) {
                if (existing.state.isActive) {
                    XposedHelpers.log("CustomTextIconTintRoute: reusing active ${existing.describe()}")
                    return existing.handle
                }
                // Re-registering a previously failed/detached view: terminal dispose the
                // old registration before creating a new one so the listener is not leaked.
                XposedHelpers.log("CustomTextIconTintRoute: superseding stale ${existing.describe()}")
                existing.dispose("superseded")
            }
            registration = DarkTintRegistration(view, classLoader, route, darkIconDispatcher)
            registrations.add(registration)
            handle = registration.handle
        }

        view.addOnAttachStateChangeListener(registration.listener)

        // If the View is already attached when we register, the listener will not
        // fire for the current attach; trigger it manually.
        if (view.isAttachedToWindow) {
            registration.attach(view)
        }

        if (registration.state.isActive) {
            XposedHelpers.log("CustomTextIconTintRoute: registered ${registration.describe()}")
        }

        return handle
    }

    /**
     * Release every outstanding registration. This is a cold-path helper for feature
     * stop or process-level teardown. The listener is removed and the list is cleared.
     */
    fun releaseAll() {
        val snapshot: List<DarkTintRegistration>
        synchronized(registrations) {
            snapshot = registrations.toList()
            registrations.clear()
        }
        for (registration in snapshot) {
            registration.dispose("release-all")
        }
    }

    private fun findLiveRegistration(view: View): DarkTintRegistration? {
        return registrations.find { it.viewRef.get() === view && !it.state.isDisposed }
    }

    private fun prune() {
        val iterator = registrations.iterator()
        while (iterator.hasNext()) {
            val registration = iterator.next()
            if (registration.viewRef.get() == null || registration.state.isDisposed) {
                iterator.remove()
            }
        }
    }

    internal class DarkTintRegistration(
        view: View,
        private val classLoader: ClassLoader,
        val route: String,
        initialDispatcher: Any?,
    ) {
        val viewRef = WeakReference(view)
        val state = DarkTintRegistrationState(identityHash(view), route)
        private var darkIconDispatcher: Any? = initialDispatcher

        val handle: DarkTintRegistrationHandle = object : DarkTintRegistrationHandle {
            override fun release(reason: String) {
                dispose(reason)
            }
        }

        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                CallbackGuard.guarded { attach(v) }
            }

            override fun onViewDetachedFromWindow(v: View) {
                CallbackGuard.guarded { detach(v, "view-detached") }
                // Normal detach does NOT remove the listener. Re-attach must be able
                // to re-register. Terminal dispose is the only path that removes it.
            }
        }

        fun attach(view: View) {
            if (!state.canRegister()) return
            if (darkIconDispatcher == null) {
                darkIconDispatcher = try {
                    ModuleHelper.getDepInstance(classLoader, "com.android.systemui.plugins.DarkIconDispatcher")
                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    null
                }
            }
            val dispatcher = darkIconDispatcher
            if (dispatcher == null) {
                XposedHelpers.log("CustomTextIconTintRoute: no DarkIconDispatcher for ${describe(view)}")
                return
            }

            val registered = try {
                XposedHelpers.callMethod(dispatcher, "addDarkReceiver", view)
                true
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                false
            }

            state.register(registerFn = { registered })

            if (registered) {
                XposedHelpers.log("CustomTextIconTintRoute: attached ${describe(view)}")
            } else {
                XposedHelpers.log("CustomTextIconTintRoute: attach-failed ${describe(view)}")
            }
        }

        fun detach(view: View, reason: String) {
            val released = state.release {
                val dispatcher = darkIconDispatcher
                if (dispatcher != null) {
                    try {
                        XposedHelpers.callMethod(dispatcher, "removeDarkReceiver", view)
                    } catch (t: Throwable) {
                        FatalErrors.unwrapAndRethrowIfFatal(t)
                    }
                }
            }
            if (released) {
                XposedHelpers.log("CustomTextIconTintRoute: detached ${describe(view)}; reason=$reason")
            }
        }

        fun dispose(reason: String) {
            val view = viewRef.get()
            val disposed = state.dispose { wasRegistered ->
                if (view != null) {
                    if (wasRegistered) {
                        val dispatcher = darkIconDispatcher
                        if (dispatcher != null) {
                            try {
                                XposedHelpers.callMethod(dispatcher, "removeDarkReceiver", view)
                            } catch (t: Throwable) {
                                FatalErrors.unwrapAndRethrowIfFatal(t)
                            }
                        }
                    }
                    try {
                        view.removeOnAttachStateChangeListener(listener)
                    } catch (t: Throwable) {
                        FatalErrors.unwrapAndRethrowIfFatal(t)
                    }
                }
                synchronized(registrations) { registrations.remove(this) }
            }
            if (disposed) {
                XposedHelpers.log("CustomTextIconTintRoute: disposed ${describe(view)}; reason=$reason")
            }
        }

        fun describe(view: View? = null): String {
            val v = view ?: viewRef.get()
            val viewName = v?.javaClass?.name ?: "gc-ed"
            val identity = identityHash(v)
            return "$route/$viewName@$identity"
        }

        fun identityHash(view: View?): Int {
            return view?.let { System.identityHashCode(it) } ?: 0
        }
    }
}
