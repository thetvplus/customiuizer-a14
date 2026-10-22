package tv.withaibuild.customiuizer.mods;

import android.graphics.RuntimeShader;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowNativeRuntimeShader;
import org.robolectric.nativeruntime.RuntimeShaderNatives;
import java.util.Arrays;

/**
 * Robolectric's bundled host Skia still uses sample(child, point); Android 14 AGSL uses
 * child.eval(point). Translate ONLY that spelling for host pixels, keeping the production
 * optical math and texture input unchanged. Also bridge the API 34 scalar uniform overload,
 * whose default host shadow is a no-op, to its working array overload. This is not a
 * target-driver test; both adaptations are test-only backend compatibility.
 */
@Implements(value = RuntimeShader.class, minSdk = 33, callNativeMethodsByDefault = true)
public class DockRuntimeShaderShadow extends ShadowNativeRuntimeShader {
    @Implementation
    protected static long nativeCreateBuilder(String source) {
        return ShadowNativeRuntimeShader.nativeCreateBuilder(source.replace("content.eval(", "sample(content, "));
    }

    @Implementation
    protected static void nativeUpdateUniforms(long builder, String name, float x, float y,
            float z, float w, int count) {
        RuntimeShaderNatives.nativeUpdateUniforms(builder, name,
                Arrays.copyOf(new float[] {x, y, z, w}, count));
    }
}
