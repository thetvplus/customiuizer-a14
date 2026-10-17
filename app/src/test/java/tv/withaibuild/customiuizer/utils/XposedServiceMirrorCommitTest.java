package tv.withaibuild.customiuizer.utils;

import android.content.SharedPreferences;
import android.os.RemoteException;
import io.github.libxposed.service.RemotePreferences;
import io.github.libxposed.service.XposedService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Uses the real service SDK: its optimistic cache changes even when Binder rejects a commit. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class XposedServiceMirrorCommitTest {
    @Test
    public void failedCommitStaysUndeliveredAcrossCachedRetryAndRecoversAfterFreshBind() throws Throwable {
        AtomicBoolean reject = new AtomicBoolean(true);
        AtomicInteger commits = new AtomicInteger();
        Class<?> binderType = Class.forName("io.github.libxposed.service.IXposedService");
        Object binder = Proxy.newProxyInstance(binderType.getClassLoader(), new Class<?>[] {binderType},
                (proxy, method, args) -> {
                    if (method.getName().equals("updateRemotePreferences")) {
                        commits.incrementAndGet();
                        if (reject.get()) throw new RemoteException("simulated transport failure");
                        return null;
                    }
                    throw new AssertionError("unexpected service operation: " + method.getName());
                });
        Constructor<XposedService> constructor = XposedService.class.getDeclaredConstructor(binderType);
        constructor.setAccessible(true);
        XposedService service = constructor.newInstance(binder);
        RemotePreferences remote = remote(service);
        Map<String, Object> values = Map.of("pref_key_commit_probe", true);
        SharedPreferences local = (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class<?>[] {SharedPreferences.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getAll")) return values;
                    throw new AssertionError(method.getName());
                });
        XposedServiceManagerFatalBoundaryTest.withMirrorPreferences(local, remote, (state, generation, pass) -> {
            assertFalse("failed commit must fail the mirror pass", (Boolean) pass.run());
            assertEquals("the SDK cache is optimistic, not proof of delivery", values, remote.getAll());
            assertTrue(state.getHasUndeliveredChanges());
            assertEquals(1, commits.get());

            Method run = XposedServiceManager.class.getDeclaredMethod("runMirror", long.class, String.class);
            run.setAccessible(true);
            run.invoke(XposedServiceManager.INSTANCE, generation, "cached retry");
            assertEquals("an empty cached diff performs no transport", 1, commits.get());
            assertTrue("a cached no-op cannot confirm a rejected write", state.getHasUndeliveredChanges());
            assertFalse("keep the existing one-retry budget", state.claimRetry(generation));

            state.onUnbind();
            long fresh = state.onBind();
            reject.set(false);
            XposedServiceManager.remotePrefs = remote(service);
            run.invoke(XposedServiceManager.INSTANCE, fresh, "fresh connection snapshot");
            assertEquals(2, commits.get());
            assertEquals(values, XposedServiceManager.remotePrefs.getAll());
            assertFalse("a confirmed pass on a fresh snapshot recovers", state.getHasUndeliveredChanges());
        });
    }

    private static RemotePreferences remote(XposedService service) throws Exception {
        Constructor<RemotePreferences> constructor = RemotePreferences.class
                .getDeclaredConstructor(XposedService.class, String.class);
        constructor.setAccessible(true);
        RemotePreferences remote = constructor.newInstance(service, "test-commit-boundary");
        Field map = RemotePreferences.class.getDeclaredField("mMap");
        map.setAccessible(true);
        map.set(remote, new LinkedHashMap<String, Object>());
        return remote;
    }
}
