package tv.withaibuild.customiuizer.mods.duostatusbar

import com.android.systemui.statusbar.SignalIcon
import com.android.systemui.statusbar.connectivity.MobileSignalController
import com.android.systemui.statusbar.policy.BatteryControllerImpl
import com.android.systemui.statusbar.policy.MiuiWifiSignalController
import org.junit.Assert.*
import org.junit.Test

class DuoAbiTest {
    private val loader = javaClass.classLoader!!

    @Test fun resolvesXiaomiControllerAndInheritedBatteryMembers() {
        val abi = requireNotNull(DuoAbi.resolve(loader))
        assertSame(MiuiWifiSignalController::class.java, abi.wifiController)
        assertSame(BatteryControllerImpl::class.java, abi.batteryLevel.declaringClass)
        assertSame(BatteryControllerImpl::class.java, abi.saverChanged.declaringClass)
        assertEquals(5, abi.mobileMaxLevel)
    }

    @Test fun erasedCleanStateReturnDoesNotHideConcreteMobileDataSim() {
        assertSame(SignalIcon.State::class.java,
            MobileSignalController::class.java.getMethod("cleanState").returnType)
        val abi = requireNotNull(DuoAbi.resolve(loader))
        val mobile = MobileSignalController()
        val state = mobile.mCurrentState as SignalIcon.MobileState
        state.dataSim = true
        state.level = 4
        assertTrue(abi.mobileDataSim.getBoolean(abi.mobileState.get(mobile)))
        assertEquals(4, abi.mobileLevel.getInt(abi.mobileState.get(mobile)))
        val wifi = MiuiWifiSignalController()
        wifi.mCurrentState.connected = true
        assertTrue(abi.wifiConnected.getBoolean(abi.wifiState.get(wifi)))
    }

    @Test fun missingControllerKeepsTheFeatureUnavailable() {
        val absent = object : ClassLoader(loader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name == MiuiWifiSignalController::class.java.name) throw ClassNotFoundException(name)
                return super.loadClass(name, resolve)
            }
        }
        assertNull(DuoAbi.resolve(absent))
    }

    @Test fun controlCenterHostIsOptionalAndUsesTheSystemUiLoader() {
        val name = "com.android.systemui.controlcenter.phone.widget.ControlCenterStatusBarIcon"
        assertEquals(name, requireNotNull(DuoAbi.resolve(loader)).controlCenter?.name)
        val absent = object : ClassLoader(loader) {
            override fun loadClass(type: String, resolve: Boolean): Class<*> {
                if (type == name) throw ClassNotFoundException(type)
                return super.loadClass(type, resolve)
            }
        }
        assertNull(requireNotNull(DuoAbi.resolve(absent)).controlCenter)
    }

    @Test fun audioFollowsTheNativeReceiverTypeAndIsAnIndependentCompatibilityBoundary() {
        val abi = requireNotNull(DuoAbi.resolve(loader))
        val audio = requireNotNull(DuoAudioAbi.resolve(abi))
        assertEquals("HeadsetReceiver", audio.headsetChanged.declaringClass.simpleName)
        val policy = com.android.systemui.statusbar.phone.MiuiPhoneStatusBarPolicy()
        assertSame(policy, audio.receiverOwner.get(policy.mIntentReceiver))
        assertTrue((audio.headsetMap.get(policy) as Map<*, *>).isEmpty())
        val absent = object : ClassLoader(loader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name.endsWith(".BluetoothControllerImpl")) throw ClassNotFoundException(name)
                return super.loadClass(name, resolve)
            }
        }
        assertNull(DuoAudioAbi.resolve(requireNotNull(DuoAbi.resolve(absent))))
    }

    @Test fun transitionHostIsResolvedWithoutMakingEitherControlCenterHostRequired() {
        val name = "com.android.systemui.controlcenter.phone.widget.ControlCenterFakeStatusIcons"
        assertEquals(name, requireNotNull(DuoAbi.resolve(loader)).transition?.name)
        val absent = object : ClassLoader(loader) {
            override fun loadClass(type: String, resolve: Boolean): Class<*> {
                if (type == name || type.endsWith(".ControlCenterStatusBarIcon")) throw ClassNotFoundException(type)
                return super.loadClass(type, resolve)
            }
        }
        val abi = requireNotNull(DuoAbi.resolve(absent))
        assertNull(abi.transition)
        assertNull(abi.controlCenter)
        assertSame(MiuiWifiSignalController::class.java, abi.wifiController)
    }

    @Test(expected = OutOfMemoryError::class)
    fun resolverDoesNotConvertFatalLoaderFailureToNativeFallback() {
        val fatal = object : ClassLoader(loader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name == MiuiWifiSignalController::class.java.name) throw OutOfMemoryError("fixture")
                return super.loadClass(name, resolve)
            }
        }
        DuoAbi.resolve(fatal)
    }

    @Test fun optionalBatteryMetadataCanBeDisabledWithoutDisablingHeadphones() {
        val abi = requireNotNull(DuoAbi.resolve(loader))
        assertNull(requireNotNull(DuoAudioAbi.resolve(abi, false)).battery)
        val fields = requireNotNull(requireNotNull(DuoAudioAbi.resolve(abi)).battery)
        val controller = com.android.systemui.statusbar.policy.BluetoothControllerImpl()
        fields.changed.invoke(controller, 2)
        assertEquals(2, fields.level.getInt(controller))
        assertTrue((fields.devices.get(controller) as Collection<*>).isEmpty())
        assertNull(DuoBluetoothBatteryAbi.resolve(String::class.java))
    }
}
