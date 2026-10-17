package tv.withaibuild.customiuizer.utils;

import android.content.SharedPreferences;

import io.github.libxposed.service.RemotePreferences;
import io.github.libxposed.service.XposedService;
import kotlinx.coroutines.CoroutineExceptionHandler;
import kotlin.coroutines.EmptyCoroutineContext;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Exercises the real manager boundaries without a Binder connection or background work. */
public class XposedServiceManagerFatalBoundaryTest {

    @Test
    public void workerBoundaryPropagatesEveryFatalKind() throws Exception {
        CoroutineExceptionHandler handler = workerFailureHandler();
        for (Throwable fatal : fatalErrors()) {
            assertFatalEscapes(fatal, () -> {
                handler.handleException(EmptyCoroutineContext.INSTANCE, fatal);
                return null;
            });
        }
    }

    @Test
    public void workerBoundaryPropagatesWrappedFatalCauses() throws Exception {
        CoroutineExceptionHandler handler = workerFailureHandler();
        for (Throwable fatal : fatalErrors()) {
            Throwable wrapped = new ExecutionException(new InvocationTargetException(fatal));
            assertFatalEscapes(fatal, () -> {
                handler.handleException(EmptyCoroutineContext.INSTANCE, wrapped);
                return null;
            });
        }
    }

    @Test
    public void workerBoundaryStillHandlesOrdinaryFailure() throws Exception {
        workerFailureHandler().handleException(EmptyCoroutineContext.INSTANCE,
                new IllegalStateException("test worker failure"));
    }

    @Test
    public void mirrorPassPropagatesFatalInsteadOfSchedulingRecovery() throws Throwable {
        for (Throwable fatal : fatalErrors()) {
            withMirrorFailure(fatal, (state, generation, pass) -> {
                assertFatalEscapes(fatal, pass);
                assertFalse("fatal failure must not be converted into undelivered settings",
                        state.getHasUndeliveredChanges());
                assertTrue("fatal failure must not spend the ordinary recovery retry",
                        state.claimRetry(generation));
            });
        }
    }

    @Test
    public void mirrorPassPropagatesReflectionWrappedFatalCause() throws Throwable {
        Throwable fatal = new InternalError("test VM failure");
        withMirrorFailure(new InvocationTargetException(fatal), (state, generation, pass) -> {
            assertFatalEscapes(fatal, pass);
            assertFalse(state.getHasUndeliveredChanges());
            assertTrue(state.claimRetry(generation));
        });
    }

    @Test
    public void ordinaryMirrorFailureStillMarksUndeliveredAndClaimsOnlyOneRetry() throws Throwable {
        withMirrorFailure(new IllegalStateException("test remote snapshot failure"),
                (state, generation, pass) -> {
                    assertFalse((Boolean) pass.run());
                    assertTrue(state.getHasUndeliveredChanges());
                    assertFalse("the existing recovery already claimed this generation's retry",
                            state.claimRetry(generation));
                    assertFalse((Boolean) pass.run());
                    assertFalse(state.claimRetry(generation));
                });
    }

    @Test
    public void everyManagerThrowableCatchChecksFatalBeforeRecoveryOrFallback() throws Exception {
        Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        String relative = "app/src/main/java/tv/withaibuild/customiuizer/utils/XposedServiceManager.kt";
        while (directory != null && !Files.isRegularFile(directory.resolve(relative))) {
            directory = directory.getParent();
        }
        if (directory == null) throw new AssertionError("manager source not found");
        String source = Files.readString(directory.resolve(relative), StandardCharsets.UTF_8);
        Matcher catches = Pattern.compile("catch\\s*\\(\\s*(\\w+)\\s*:\\s*Throwable\\s*\\)\\s*\\{")
                .matcher(source);
        int count = 0;
        while (catches.find()) {
            count++;
            String firstStatement = source.substring(catches.end()).stripLeading();
            assertTrue("fatal must escape before any recovery or state change at " + catches.start(),
                    firstStatement.startsWith("FatalErrors.unwrapAndRethrowIfFatal(" + catches.group(1) + ")"));
        }
        assertTrue("cover the write, broadcast, reflection, pass, unregister and registration boundaries",
                count >= 6);
    }

    private static Throwable[] fatalErrors() {
        return new Throwable[] {
                new OutOfMemoryError("test allocation failure"),
                new ThreadDeath(),
                new InternalError("test VM failure"),
        };
    }

    private static CoroutineExceptionHandler workerFailureHandler() throws Exception {
        // Initialize the object before reading a Kotlin object's private static field.
        XposedServiceManager manager = XposedServiceManager.INSTANCE;
        Field field = XposedServiceManager.class.getDeclaredField("mirrorFailureHandler");
        field.setAccessible(true);
        return (CoroutineExceptionHandler) field.get(manager);
    }

    private static void withMirrorFailure(Throwable failure, MirrorAssertion assertion) throws Throwable {
        SharedPreferences local = (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class<?>[] { SharedPreferences.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("getAll")) return Collections.emptyMap();
                    throw new AssertionError("unexpected local preference operation: " + method.getName());
                });
        Constructor<RemotePreferences> constructor = RemotePreferences.class
                .getDeclaredConstructor(XposedService.class, String.class);
        constructor.setAccessible(true);
        RemotePreferences remote = constructor.newInstance(null, "test-fatal-boundary");
        Field mapField = RemotePreferences.class.getDeclaredField("mMap");
        mapField.setAccessible(true);
        mapField.set(remote, new AbstractMap<String, Object>() {
            @Override public Set<Entry<String, Object>> entrySet() {
                XposedServiceManagerFatalBoundaryTest.<RuntimeException>throwUnchecked(failure);
                return Collections.emptySet();
            }
        });
        withMirrorPreferences(local, remote, assertion);
    }

    static void withMirrorPreferences(SharedPreferences local, RemotePreferences remote,
                                      MirrorAssertion assertion) throws Throwable {
        XposedServiceManager manager = XposedServiceManager.INSTANCE;
        Field stateField = XposedServiceManager.class.getDeclaredField("mirrorState");
        stateField.setAccessible(true);
        PrefsMirrorState state = (PrefsMirrorState) stateField.get(manager);
        Map<Field, Object> originalState = new LinkedHashMap<>();
        for (Field field : PrefsMirrorState.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) && field.getType().isPrimitive()) {
                field.setAccessible(true);
                originalState.put(field, field.get(state));
            }
        }
        SharedPreferences originalLocal = AppHelper.appPrefs;
        RemotePreferences originalRemote = XposedServiceManager.remotePrefs;
        try {
            long generation = state.onBind();
            state.clearUndelivered(generation);
            AppHelper.appPrefs = local;
            XposedServiceManager.remotePrefs = remote;

            Method method = XposedServiceManager.class.getDeclaredMethod("mirrorPass", long.class, String.class);
            method.setAccessible(true);
            assertion.check(state, generation, () -> {
                try {
                    return method.invoke(manager, generation, "test boundary");
                } catch (InvocationTargetException invocation) {
                    throw invocation.getCause();
                }
            });
        } finally {
            AppHelper.appPrefs = originalLocal;
            XposedServiceManager.remotePrefs = originalRemote;
            for (Map.Entry<Field, Object> entry : originalState.entrySet()) {
                entry.getKey().set(state, entry.getValue());
            }
        }
    }

    private static void assertFatalEscapes(Throwable expected, ThrowingCall call) throws Exception {
        try {
            call.run();
        } catch (Throwable actual) {
            assertSame("preserve the exact fatal object", expected, actual);
            return;
        }
        fail("fatal error was swallowed: " + expected.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwUnchecked(Throwable failure) throws T {
        throw (T) failure;
    }

    interface ThrowingCall {
        Object run() throws Throwable;
    }

    interface MirrorAssertion {
        void check(PrefsMirrorState state, long generation, ThrowingCall pass) throws Throwable;
    }
}
