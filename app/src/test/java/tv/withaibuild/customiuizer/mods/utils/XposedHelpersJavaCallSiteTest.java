package tv.withaibuild.customiuizer.mods.utils;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Java-side call-site compilation and behavior tests for the XposedHelpers reflection ABI.
 *
 * This file intentionally calls the public Java API from Java source to ensure the
 * signatures remain compilable from Java after Kotlin refactors elsewhere in the app.
 */
public class XposedHelpersJavaCallSiteTest {

    @SuppressWarnings("unused")
    private static final class ReflectionTarget {
        public String publicField = "initial";
        private int privateField = 42;

        public String concat(String a, String b) {
            return a + " " + b;
        }

        public int add(int a, int b) {
            return a + b;
        }

        public static String staticEcho(String value) {
            return value;
        }

        public String throwsChecked() throws Exception {
            throw new Exception("checked");
        }
    }

    @Test
    public void javaCallSitesCompileAndRun_getAndSetObjectField() {
        ReflectionTarget obj = new ReflectionTarget();

        // Java call-site compilation + runtime behavior.
        String before = (String) XposedHelpers.getObjectField(obj, "publicField");
        assertEquals("initial", before);

        XposedHelpers.setObjectField(obj, "publicField", "updated");
        assertEquals("updated", obj.publicField);

        int privateValue = XposedHelpers.getIntField(obj, "privateField");
        assertEquals(42, privateValue);
    }

    @Test
    public void javaCallSitesCompileAndRun_callMethodWithVarargs() {
        ReflectionTarget obj = new ReflectionTarget();

        String result = (String) XposedHelpers.callMethod(obj, "concat", "hello", "world");
        assertEquals("hello world", result);

        int sum = (int) XposedHelpers.callMethod(obj, "add", 2, 3);
        assertEquals(5, sum);
    }

    @Test
    public void javaCallSitesCompileAndRun_callMethodWithParameterTypes() {
        ReflectionTarget obj = new ReflectionTarget();

        // Force exact overload selection with Class<?>[] form.
        String result = (String) XposedHelpers.callMethod(
                obj,
                "concat",
                new Class<?>[] { String.class, String.class },
                "a",
                "b"
        );
        assertEquals("a b", result);
    }

    @Test
    public void javaCallSitesCompileAndRun_callStaticMethod() {
        String result = (String) XposedHelpers.callStaticMethod(
                ReflectionTarget.class,
                "staticEcho",
                "ok"
        );
        assertEquals("ok", result);
    }

    @Test
    public void javaCallSitesCompileAndRun_findMethodExact() {
        Method method = XposedHelpers.findMethodExact(
                ReflectionTarget.class,
                "concat",
                String.class,
                String.class
        );
        assertNotNull(method);
        assertEquals("concat", method.getName());
    }

    @Test
    public void javaCallSitesCompileAndRun_findField() {
        java.lang.reflect.Field field = XposedHelpers.findField(ReflectionTarget.class, "publicField");
        assertNotNull(field);
        assertEquals("publicField", field.getName());
    }

    @Test
    public void javaCallSitesCompileAndRun_newInstance() {
        Object instance = XposedHelpers.newInstance(ReflectionTarget.class);
        assertTrue(instance instanceof ReflectionTarget);
    }

    @Test
    public void invocationTargetExceptionUnwrapsCause() {
        ReflectionTarget obj = new ReflectionTarget();
        try {
            XposedHelpers.callMethod(obj, "throwsChecked");
            fail("Expected InvocationTargetError");
        } catch (XposedHelpers.InvocationTargetError e) {
            assertTrue(e.getCause() instanceof Exception);
            assertEquals("checked", e.getCause().getMessage());
        }
    }

    @Test
    public void overloadResolutionPicksMostSpecific() {
        ReflectionTarget obj = new ReflectionTarget();

        // Vararg call with (String, String) should select concat(String, String), not add(int, int).
        String varargResult = (String) XposedHelpers.callMethod(obj, "concat", "x", "y");
        assertEquals("x y", varargResult);

        // Integer varargs should select add(int, int).
        int sum = (int) XposedHelpers.callMethod(obj, "add", 10, 20);
        assertEquals(30, sum);
    }

    @Test
    public void cacheHitReturnsSameMethodInstance() {
        Method first = XposedHelpers.findMethodBestMatch(ReflectionTarget.class, "add", 1, 2);
        Method second = XposedHelpers.findMethodBestMatch(ReflectionTarget.class, "add", 3, 4);
        assertSame(first, second);
    }

    @Test
    public void additionalInstanceFieldIsWeaklyHeld() {
        Object key = new Object();
        String value = "value";

        XposedHelpers.setAdditionalInstanceField(key, "test", value);
        assertEquals(value, XposedHelpers.getAdditionalInstanceField(key, "test"));

        // The reference must be identity-based (equals would match a different object with same hash).
        Object impostor = new Object();
        assertEquals(null, XposedHelpers.getAdditionalInstanceField(impostor, "test"));

        // Removing returns the previous value.
        Object removed = XposedHelpers.removeAdditionalInstanceField(key, "test");
        assertEquals(value, removed);
        assertEquals(null, XposedHelpers.getAdditionalInstanceField(key, "test"));
    }
}
