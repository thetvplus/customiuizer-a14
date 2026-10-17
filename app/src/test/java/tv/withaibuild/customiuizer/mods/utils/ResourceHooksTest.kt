package tv.withaibuild.customiuizer.mods.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.SparseArray
import android.util.SparseIntArray
import io.github.libxposed.api.XposedInterface
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for [ResourceHooks] that do not rely on a production subclass.
 *
 * The Android JVM stubs for [SparseArray] and [SparseIntArray] do not actually store values, so
 * the production hot path is exercised with test-side [FakeSparseArray]/[FakeSparseIntArray]
 * instances injected through reflection.  The framework-boundary methods remain private and are
 * reached through the genuine public/internal APIs or through narrow reflection where necessary.
 */
class ResourceHooksTest {

    private lateinit var hooks: ResourceHooks
    private val testConfig = Configuration()

    @Before
    fun setUp() {
        hooks = ResourceHooks()
        setField("resourceIdReplacements", FakeSparseArray<ResourceHooks.ResourceValue>())
        setField("fakes", FakeSparseIntArray())
        setField("lastFailureLogTimes", LongArray(resourceFailureDomainCount()) { -1L })
    }

    @After
    fun tearDown() {
        ModuleHelper.mCachedContext = null
        ModuleHelper.cachedModuleRes = null
        ModuleHelper.cachedModuleConfig = null
        ModuleHelper.mModuleContext = null
    }

    // ---- getFakeResId ----

    @Test
    fun getFakeResIdIsStablePerName() {
        assertEquals(ResourceHooks.getFakeResId("test_tag"), ResourceHooks.getFakeResId("test_tag"))
    }

    @Test
    fun getFakeResIdDiffersByName() {
        assertNotEquals(ResourceHooks.getFakeResId("foo"), ResourceHooks.getFakeResId("bar"))
    }

    // ---- ResourceGetterKind dispatch (via ResourceReplacementResolver) ----

    @Test
    fun getterKindDispatchesToCorrectResourcesCall() {
        val calls = mutableListOf<String>()
        val fakeRes = FakeResources(calls)
        assertEquals("text", ResourceHooks.ResourceGetterKind.GET_TEXT.getValue(fakeRes, 100))
        assertEquals("string", ResourceHooks.ResourceGetterKind.GET_STRING.getValue(fakeRes, 200))
        assertNotNull(ResourceHooks.ResourceGetterKind.GET_LAYOUT.getValue(fakeRes, 300))
        val drawable = ResourceHooks.ResourceGetterKind.GET_DRAWABLE_FOR_DENSITY.getValue(fakeRes, 400, 2, null)
        assertTrue(drawable is Drawable)
        assertEquals(
            listOf("getText:100", "getString:200", "getLayout:300", "getDrawableForDensity:400:2:null"),
            calls
        )
    }

    @Test
    fun layoutGetterDoesNotSupportIdReplacement() {
        assertFalse(ResourceHooks.ResourceGetterKind.GET_LAYOUT.supportsIdReplacement)
    }

    @Test
    fun resourceReplacementResolverUsesDrawableDensityAndThemeArgs() {
        val calls = mutableListOf<String>()
        val expectedDrawable = ColorDrawable()
        val fakeRes = FakeResources(calls, mapOf(200 to expectedDrawable))
        val chain = fakeChain(resId = 7, density = 3, theme = null)

        val result = ResourceHooks.ResourceReplacementResolver.resolveModuleValue(
            ResourceHooks.ResourceGetterKind.GET_DRAWABLE_FOR_DENSITY,
            chain,
            fakeRes,
            200
        )

        assertSame(expectedDrawable, result)
        assertEquals("getDrawableForDensity:200:3:null", calls.single())
    }

    @Test
    fun resourceReplacementResolverDispatchesByKind() {
        val calls = mutableListOf<String>()
        val fakeRes = FakeResources(calls, mapOf(100 to "text", 200 to "string"))
        assertEquals(
            "text",
            ResourceHooks.ResourceReplacementResolver.resolveModuleValue(
                ResourceHooks.ResourceGetterKind.GET_TEXT,
                fakeChain(resId = 0),
                fakeRes,
                100
            )
        )
        assertEquals(
            "string",
            ResourceHooks.ResourceReplacementResolver.resolveModuleValue(
                ResourceHooks.ResourceGetterKind.GET_STRING,
                fakeChain(resId = 0),
                fakeRes,
                200
            )
        )
    }

    // ---- ReplaceHook: zero argument materialization and fixed getter kind ----

    @Test
    fun replaceHookUsesGetArgAndDoesNotTouchGetArgsOrExecutableName() {
        val calls = mutableListOf<String>()
        val chain = fakeChain(
            resId = 42,
            proceedValue = "proceed",
            callLog = calls,
            throwOnGetArgs = true,
            throwOnExecutable = true
        )
        setResourceReplacement(42, ResourceHooks.ReplacementType.ID, 100)
        installFakeModuleRes(FakeResources(mapOf(100 to "replaced"), testConfig))

        val hook = createReplaceHook(ResourceHooks.ResourceGetterKind.GET_STRING)
        val result = hook.intercept(chain)

        assertEquals("replaced", result)
        assertTrue("getArg used", calls.any { it.startsWith("getArg:") })
        assertFalse("getArgs must not be used", calls.any { it == "getArgs" })
        assertFalse("executable must not be used", calls.any { it == "getExecutable" })
        assertFalse("arg1 must not be read for string", calls.any { it == "getArg:1" })
        assertFalse("arg2 must not be read for string", calls.any { it == "getArg:2" })
    }

    @Test
    fun replaceHookReturnsObjectReplacementDirectly() {
        val chain = fakeChain(resId = 10, proceedValue = "proceed")
        val expected = Any()
        setResourceReplacement(10, ResourceHooks.ReplacementType.OBJECT, expected)

        val hook = createReplaceHook(ResourceHooks.ResourceGetterKind.GET_TEXT)
        val result = hook.intercept(chain)

        assertSame(expected, result)
    }

    @Test
    fun replaceHookRespectsDrawableExtraArgs() {
        val calls = mutableListOf<String>()
        val expectedDrawable = ColorDrawable()
        val chain = fakeChain(resId = 7, density = 3, proceedValue = "proceed", callLog = calls)
        setResourceReplacement(7, ResourceHooks.ReplacementType.ID, 200)
        installFakeModuleRes(FakeResources(mapOf(200 to expectedDrawable), testConfig))

        val hook = createReplaceHook(ResourceHooks.ResourceGetterKind.GET_DRAWABLE_FOR_DENSITY)
        val result = hook.intercept(chain)

        assertSame(expectedDrawable, result)
        assertTrue(calls.contains("getArg:0"))
        assertTrue(calls.contains("getArg:1"))
        assertTrue(calls.contains("getArg:2"))
    }

    @Test
    fun replaceHookSkipsIdReplacementForLayout() {
        val chain = fakeChain(resId = 8, proceedValue = "proceed")
        setResourceReplacement(8, ResourceHooks.ReplacementType.ID, 300)
        installFakeModuleRes(FakeResources(mapOf(300 to "layout:300"), testConfig))

        val hook = createReplaceHook(ResourceHooks.ResourceGetterKind.GET_LAYOUT)
        val result = hook.intercept(chain)

        assertEquals("proceed", result)
    }

    @Test
    fun replaceHookFallsBackToFakesAndProceedsOnce() {
        val calls = mutableListOf<String>()
        val fakeResId = ResourceHooks.getFakeResId("my_fake")
        val chain = fakeChain(resId = fakeResId, proceedValue = "proceed", callLog = calls)
        setFakeResourceId(fakeResId, 400)
        installFakeModuleRes(FakeResources(mapOf(400 to "fake_value"), testConfig))

        val hook = createReplaceHook(ResourceHooks.ResourceGetterKind.GET_STRING)
        val result = hook.intercept(chain)

        assertEquals("fake_value", result)
        assertEquals(0, calls.count { it == "proceed" })
    }

    @Test
    fun replaceHookProceedsOnceWhenNoMatch() {
        val calls = mutableListOf<String>()
        val chain = fakeChain(resId = 99, proceedValue = "proceed", callLog = calls)

        val hook = createReplaceHook(ResourceHooks.ResourceGetterKind.GET_STRING)
        val result = hook.intercept(chain)

        assertEquals("proceed", result)
        assertEquals(1, calls.count { it == "proceed" })
    }

    @Test(expected = OutOfMemoryError::class)
    fun replaceHookRethrowsOutOfMemoryError() {
        val chain = fakeChain(resId = 0, throwOnGetArg = true)
        val hook = createReplaceHook(ResourceHooks.ResourceGetterKind.GET_TEXT)
        hook.intercept(chain)
    }

    @Test
    fun replaceHookRateLimitsExceptionLogging() {
        val chain = fakeChain(resId = 0, throwRuntimeOnGetArg = true)
        val hook = createReplaceHook(ResourceHooks.ResourceGetterKind.GET_TEXT)

        // First exception should log (update timestamp).
        setLastFailureLogTime(domainOrdinal(ResourceHooks.ResourceGetterKind.GET_TEXT), -1L)
        hook.intercept(chain)
        val first = getLastFailureLogTime(domainOrdinal(ResourceHooks.ResourceGetterKind.GET_TEXT))
        assertTrue("first exception should log", first >= 0)

        // Second exception within throttle window should not update.
        setLastFailureLogTime(domainOrdinal(ResourceHooks.ResourceGetterKind.GET_TEXT), first + 100)
        hook.intercept(chain)
        val second = getLastFailureLogTime(domainOrdinal(ResourceHooks.ResourceGetterKind.GET_TEXT))
        assertEquals("second exception within window must not re-log", first + 100, second)

        // After clearing the throttle window, the next exception logs again.
        setLastFailureLogTime(domainOrdinal(ResourceHooks.ResourceGetterKind.GET_TEXT), -1L)
        hook.intercept(chain)
        val third = getLastFailureLogTime(domainOrdinal(ResourceHooks.ResourceGetterKind.GET_TEXT))
        assertTrue("third exception after clear should log again", third >= first)
        assertNotEquals("third call must not leave the pre-call value", first + 100, third)
    }

    @Test
    fun exceptionThrottleUsesFixedDomainPerKind() {
        val chain = fakeChain(resId = 0, throwRuntimeOnGetArg = true)
        val hookText = createReplaceHook(ResourceHooks.ResourceGetterKind.GET_TEXT)
        val hookString = createReplaceHook(ResourceHooks.ResourceGetterKind.GET_STRING)

        // GET_TEXT and GET_STRING are in different fixed domains, so each logs once.
        setLastFailureLogTime(domainOrdinal(ResourceHooks.ResourceGetterKind.GET_TEXT), -1L)
        setLastFailureLogTime(domainOrdinal(ResourceHooks.ResourceGetterKind.GET_STRING), -1L)
        hookText.intercept(chain)
        hookString.intercept(chain)
        val firstText = getLastFailureLogTime(domainOrdinal(ResourceHooks.ResourceGetterKind.GET_TEXT))
        val firstString = getLastFailureLogTime(domainOrdinal(ResourceHooks.ResourceGetterKind.GET_STRING))
        assertTrue("GET_TEXT logged", firstText >= 0)
        assertTrue("GET_STRING logged", firstString >= 0)

        // Same kind again within window does not log; different domain is unchanged.
        setLastFailureLogTime(domainOrdinal(ResourceHooks.ResourceGetterKind.GET_TEXT), firstText + 100)
        hookText.intercept(fakeChain(resId = 0, throwRuntimeOnGetArg = true))
        assertEquals(
            "same kind within throttle window must not re-log",
            firstText + 100,
            getLastFailureLogTime(domainOrdinal(ResourceHooks.ResourceGetterKind.GET_TEXT))
        )
        assertEquals(
            "GET_STRING domain must not be touched by GET_TEXT",
            firstString,
            getLastFailureLogTime(domainOrdinal(ResourceHooks.ResourceGetterKind.GET_STRING))
        )
    }

    // ---- GetterInstaller: retry, concurrency, OOM ----

    @Test
    fun installGetterRecordsPartialSuccessForStringGetters() {
        val text = ResourceHooks.GetterInstaller()
        val string = ResourceHooks.GetterInstaller()

        assertNotNull(text.install { null })
        assertEquals(ResourceHooks.HookStatus.FAILED, text.status)
        assertEquals(1, text.attempts)

        assertNull(string.install { TestUnhooker() })
        assertEquals(ResourceHooks.HookStatus.HOOKED, string.status)
    }

    @Test
    fun installGetterBoundedByMaxAttempts() {
        val installer = ResourceHooks.GetterInstaller()

        repeat(4) { installer.install { null } }

        assertEquals(ResourceHooks.HookStatus.FAILED, installer.status)
        assertEquals(3, installer.attempts)
    }

    @Test
    fun installGetterSuccessResetsAttempts() {
        val installer = ResourceHooks.GetterInstaller()

        installer.install { null }
        assertEquals(1, installer.attempts)

        installer.install { TestUnhooker() }
        assertEquals(0, installer.attempts)
        assertEquals(ResourceHooks.HookStatus.HOOKED, installer.status)
    }

    @Test
    fun installGetterInstallsOnlyOnceConcurrently() {
        val installCount = AtomicInteger(0)
        val entered = CountDownLatch(1)
        val proceed = CountDownLatch(1)

        val installer = ResourceHooks.GetterInstaller()

        val t1 = Thread {
            installer.install {
                entered.countDown()
                proceed.await(2, TimeUnit.SECONDS)
                installCount.incrementAndGet() > 0
                TestUnhooker()
            }
        }
        t1.start()
        entered.await(2, TimeUnit.SECONDS)

        val t2 = Thread {
            installer.install { TestUnhooker() }
        }
        t2.start()
        t2.join(500)

        proceed.countDown()
        t1.join(2000)
        t2.join(2000)

        assertEquals(1, installCount.get())
        assertEquals(ResourceHooks.HookStatus.HOOKED, installer.status)
    }

    @Test
    fun installGetterNullUnhookerIsFailedAndRetryable() {
        val installer = ResourceHooks.GetterInstaller()

        assertNotNull(installer.install { null })
        assertEquals(ResourceHooks.HookStatus.FAILED, installer.status)
        assertEquals(1, installer.attempts)

        assertNull(installer.install { TestUnhooker() })
        assertEquals(ResourceHooks.HookStatus.HOOKED, installer.status)
        assertEquals(0, installer.attempts)
    }

    @Test
    fun installGetterOutOfMemoryDoesNotLeavePending() {
        val installer = ResourceHooks.GetterInstaller()

        try {
            installer.install { throw OutOfMemoryError("oom") }
            fail("OutOfMemoryError expected")
        } catch (_: OutOfMemoryError) {
            assertEquals(ResourceHooks.HookStatus.FAILED, installer.status)
        }
    }

    @Test
    fun installGetterStatesAreIndependent() {
        val installers = ResourceHooks.ResourceGetterKind.entries.associateWith {
            ResourceHooks.GetterInstaller()
        }

        for (kind in ResourceHooks.ResourceGetterKind.entries) {
            installers[kind]!!.install { if (kind == ResourceHooks.ResourceGetterKind.GET_DRAWABLE_FOR_DENSITY) TestUnhooker() else null }
        }

        assertEquals(
            ResourceHooks.HookStatus.HOOKED,
            installers[ResourceHooks.ResourceGetterKind.GET_DRAWABLE_FOR_DENSITY]!!.status
        )
        assertEquals(
            ResourceHooks.HookStatus.FAILED,
            installers[ResourceHooks.ResourceGetterKind.GET_LAYOUT]!!.status
        )
        assertEquals(
            ResourceHooks.HookStatus.FAILED,
            installers[ResourceHooks.ResourceGetterKind.GET_TEXT]!!.status
        )
        assertEquals(
            ResourceHooks.HookStatus.FAILED,
            installers[ResourceHooks.ResourceGetterKind.GET_STRING]!!.status
        )
    }

    // ---- helpers ----

    private fun fakeChain(
        resId: Int,
        density: Int = 0,
        theme: Resources.Theme? = null,
        proceedValue: Any? = null,
        callLog: MutableList<String>? = null,
        throwOnGetArgs: Boolean = false,
        throwOnExecutable: Boolean = false,
        throwOnGetArg: Boolean = false,
        throwRuntimeOnGetArg: Boolean = false,
    ): XposedInterface.Chain {
        val handler = java.lang.reflect.InvocationHandler { _, method, methodArgs ->
            val name = method.name
            if ((name == "getArgs" || name == "getArguments") && throwOnGetArgs) {
                throw IllegalStateException("getArgs must not be called")
            }
            if ((name == "getExecutable" || name == "executable") && throwOnExecutable) {
                throw IllegalStateException("executable must not be called")
            }
            when (name) {
                "getArg", "getArgument" -> {
                    when {
                        throwOnGetArg -> throw OutOfMemoryError("oom")
                        throwRuntimeOnGetArg -> throw RuntimeException("runtime boom")
                    }
                    val index = methodArgs?.get(0) as? Int ?: 0
                    callLog?.add("getArg:$index")
                    when (index) {
                        0 -> resId
                        1 -> density
                        2 -> theme
                        else -> null
                    }
                }
                "getArgs", "getArguments" -> {
                    callLog?.add("getArgs")
                    listOf(resId, density, theme)
                }
                "proceed" -> {
                    callLog?.add("proceed")
                    proceedValue
                }
                "thisObject", "getThisObject" -> null
                "executable", "getExecutable" -> null
                "toString" -> "FakeChain"
                "hashCode" -> resId
                "equals" -> methodArgs?.get(0) === this
                else -> null
            }
        }
        return Proxy.newProxyInstance(
            ResourceHooksTest::class.java.classLoader,
            arrayOf(XposedInterface.Chain::class.java),
            handler,
        ) as XposedInterface.Chain
    }

    private fun createReplaceHook(kind: ResourceHooks.ResourceGetterKind): HookerClassHelper.MethodHook {
        val replaceClass = ResourceHooks::class.java.declaredClasses.single { it.simpleName == "ReplaceHook" }
        val ctor = replaceClass.getDeclaredConstructor(ResourceHooks::class.java, ResourceHooks.ResourceGetterKind::class.java)
        ctor.isAccessible = true
        return ctor.newInstance(hooks, kind) as HookerClassHelper.MethodHook
    }

    private fun setResourceReplacement(resId: Int, type: ResourceHooks.ReplacementType, value: Any?) {
        val method = ResourceHooks::class.java.getDeclaredMethod(
            "setResourceReplacement",
            Int::class.javaPrimitiveType,
            ResourceHooks.ReplacementType::class.java,
            Any::class.java
        )
        method.isAccessible = true
        method.invoke(hooks, resId, type, value)
    }

    private fun setFakeResourceId(resId: Int, modResId: Int) {
        val method = ResourceHooks::class.java.getDeclaredMethod(
            "setFakeResourceId",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        method.invoke(hooks, resId, modResId)
    }

    private fun installFakeModuleRes(res: Resources) {
        // The JVM Android stub Configuration.equals() always returns false, so
        // ModuleHelper.getModuleRes() never takes the cached early-return path.
        // Pre-populate the module context so the fallback path still resolves
        // to the supplied fake Resources.
        ModuleHelper.mCachedContext = FakeContext(FakeResources(mapOf(), testConfig))
        ModuleHelper.cachedModuleRes = res
        ModuleHelper.cachedModuleConfig = testConfig
        ModuleHelper.mModuleContext = FakeModuleContext(res)
    }

    private fun setField(name: String, value: Any?) {
        ResourceHooks::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(hooks, value)
        }
    }

    private fun resourceFailureDomainCount(): Int {
        val clazz = ResourceHooks::class.java.declaredClasses.single { it.simpleName == "ResourceFailureDomain" }
        @Suppress("UNCHECKED_CAST")
        val constants = clazz.enumConstants as Array<Enum<*>>
        return constants.size
    }

    private fun domainOrdinal(kind: ResourceHooks.ResourceGetterKind): Int {
        val hook = createReplaceHook(kind)
        val method = hook.javaClass.getDeclaredMethod("failureDomain")
        method.isAccessible = true
        return (method.invoke(hook) as Enum<*>).ordinal
    }

    private fun getLastFailureLogTime(domainOrdinal: Int): Long {
        val field = ResourceHooks::class.java.getDeclaredField("lastFailureLogTimes")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val array = field.get(hooks) as LongArray
        return array[domainOrdinal]
    }

    private fun setLastFailureLogTime(domainOrdinal: Int, value: Long) {
        val field = ResourceHooks::class.java.getDeclaredField("lastFailureLogTimes")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val array = field.get(hooks) as LongArray
        array[domainOrdinal] = value
    }

    private class FakeContext(res: Resources) : ContextWrapper(null) {
        private val mRes = res
        override fun getResources(): Resources = mRes
        override fun getPackageName(): String = "tv.withaibuild.customiuizer.r14"
    }

    private class FakeModuleContext(private val mRes: Resources) : ContextWrapper(null) {
        override fun getResources(): Resources = mRes
        override fun createConfigurationContext(overrideConfiguration: Configuration): Context = this
    }

    private class FakeResources : Resources {
        private val callLog: MutableList<String>
        private val values = mutableMapOf<Int, Any?>()
        private val configuration: Configuration

        constructor(callLog: MutableList<String>, config: Configuration = Configuration())
            : super(newAssetManager(), DisplayMetrics(), config) {
            this.callLog = callLog
            this.configuration = config
            values[100] = "text"
            values[200] = "string"
            values[300] = newXmlResourceParser()
            values[400] = ColorDrawable()
        }

        constructor(callLog: MutableList<String>, map: Map<Int, Any>, config: Configuration = Configuration())
            : super(newAssetManager(), DisplayMetrics(), config) {
            this.callLog = callLog
            this.configuration = config
            values.putAll(map)
        }

        constructor(map: Map<Int, Any>, config: Configuration = Configuration())
            : super(newAssetManager(), DisplayMetrics(), config) {
            this.callLog = mutableListOf()
            this.configuration = config
            values.putAll(map)
        }

        override fun getConfiguration(): Configuration = configuration

        override fun getText(id: Int): CharSequence {
            callLog.add("getText:$id")
            return values[id] as? CharSequence ?: throw NotFoundException()
        }

        override fun getString(id: Int): String {
            callLog.add("getString:$id")
            return values[id] as? String ?: throw NotFoundException()
        }

        override fun getLayout(id: Int): XmlResourceParser {
            callLog.add("getLayout:$id")
            @Suppress("UNCHECKED_CAST")
            return values[id] as? XmlResourceParser ?: throw NotFoundException()
        }

        override fun getDrawableForDensity(id: Int, density: Int, theme: Resources.Theme?): Drawable? {
            callLog.add("getDrawableForDensity:$id:$density:${theme}")
            return values[id] as? Drawable
        }
    }

    private class FakeSparseArray<E> : SparseArray<E>() {
        private val map = mutableMapOf<Int, E?>()

        override fun get(key: Int): E? = map[key]
        override fun get(key: Int, valueIfKeyNotFound: E?): E? = map[key] ?: valueIfKeyNotFound
        override fun put(key: Int, value: E?) { map[key] = value }
        override fun clone(): SparseArray<E> = FakeSparseArray<E>().also { it.map.putAll(map) }
        override fun size(): Int = map.size
    }

    private class FakeSparseIntArray : SparseIntArray() {
        private val map = mutableMapOf<Int, Int>()

        override fun get(key: Int): Int = map[key] ?: 0
        override fun get(key: Int, valueIfKeyNotFound: Int): Int = map[key] ?: valueIfKeyNotFound
        override fun put(key: Int, value: Int) { map[key] = value }
        override fun clone(): SparseIntArray = FakeSparseIntArray().also { it.map.putAll(map) }
        override fun size(): Int = map.size
    }

    private class TestUnhooker : HookerClassHelper.CustomMethodUnhooker {
        override fun unhook() {}
    }

    companion object {
        private fun newAssetManager(): AssetManager {
            val constructor = AssetManager::class.java.getDeclaredConstructor()
            constructor.isAccessible = true
            return constructor.newInstance()
        }

        private fun newXmlResourceParser(): XmlResourceParser {
            return Proxy.newProxyInstance(
                ResourceHooksTest::class.java.classLoader,
                arrayOf(XmlResourceParser::class.java)
            ) { _, method, _ ->
                when (method.name) {
                    "toString" -> "FakeXmlResourceParser"
                    else -> null
                }
            } as XmlResourceParser
        }
    }
}
