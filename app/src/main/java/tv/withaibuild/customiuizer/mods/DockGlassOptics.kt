package tv.withaibuild.customiuizer.mods

import android.graphics.RuntimeShader
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/** Native-material coating fallback and a separately sample-backed SDF lens.
 * The coating cannot sample a SurfaceFlinger blur region. The lens requires an explicit
 * wallpaper-only texture from DockGlassBackdrop; no texture means no refraction claim.
 * References: QWEA0 Liquid-Glass-Android, Kyant0 Backdrop, Android AGSL documentation.
 */
internal object DockGlassOptics {
    fun create(): RuntimeShader? = try {
        RuntimeShader(SOURCE)
    } catch (error: Throwable) {
        FatalErrors.unwrapAndRethrowIfFatal(error)
        XposedHelpers.log("Dock glass: using gradient coating", error)
        null
    }

    fun createLens(): RuntimeShader? = try {
        RuntimeShader(LENS_SOURCE).apply { setInputBuffer("cornerField", DockCornerField.shader) }
    } catch (error: Throwable) {
        FatalErrors.unwrapAndRethrowIfFatal(error)
        XposedHelpers.log("Dock lens: using native material", error)
        null
    }

    // SDF bevel / inward refraction model informed by QWEA0 Liquid-Glass-Android (MIT).
    // Adapted to one wallpaper BitmapShader, screen-space sampling and bounded dispersion.
    // Attribution and license: assets/licenses/dock-liquid-glass.txt.
    internal const val LENS_SOURCE = """
        uniform shader content;
        uniform shader cornerField;
        uniform float2 origin;
        uniform float2 size;
        uniform float radius;
        uniform float density;
        uniform float3 sampleX;
        uniform float3 sampleY;
        uniform float2 sampleSize;
        uniform float motion;

        float sd(float2 p) {
            float2 q = abs(p) - size * 0.5 + radius;
            return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
        }
        float2 uv(float2 p) {
            float3 point = float3(p, 1.0);
            return clamp(float2(dot(sampleX, point), dot(sampleY, point)),
                float2(0.5), sampleSize - 0.5);
        }
        half4 main(float2 coord) {
            float2 local = coord - origin;
            float2 p = local - size * 0.5;
            float2 q = abs(p) - size * 0.5 + radius;
            float2 edge = abs(p) - size * 0.5;
            float d = max(edge.x, edge.y);
            float2 n = edge.x > edge.y ? float2(sign(p.x), 0.0) : float2(0.0, sign(p.y));
            if (radius > 0.5 && q.x > 0.0 && q.y > 0.0) {
                half4 field = cornerField.eval(clamp(q / radius, 0.0, 1.0) * 63.0 + 0.5);
                d = (float(field.r) * 2.0 - 1.0) * radius;
                n = sign(p) * (float2(field.gb) * 2.0 - 1.0);
                n /= max(length(n), 0.001);
            }
            // Coverage comes from the same cached G4 Path, once. A second circular
            // alpha mask would create the visibly different inner/outer corner outlines.
            float coverage = 1.0;
            float inside = max(-d, 0.0);
            float bevel = max(1.0, min(min(size.x, size.y) * 0.34, 22.0 * density));
            float t = clamp(inside / bevel, 0.0, 1.0);
            // Snell-law surface mapping, informed by AyuGram's LiquidGlassEffect.
            // Use our shared G4 normal, one cached texture and a bounded optical depth.
            float slope = 1.0 - t;
            float vertical = sqrt(max(1.0 - slope * slope, 0.0));
            float3 surfaceNormal = float3(n * slope, vertical);
            float3 ray = refract(float3(0.0, 0.0, -1.0), surfaceNormal, 1.0 / 1.36);
            float height = sqrt(max(inside * (2.0 * bevel - inside), 0.0));
            float travel = (height + 6.0 * bevel) * 0.18 / max(-ray.z, 0.25);
            float2 offset = ray.xy * min(travel, 30.0 * density) * (1.0 + 0.08 * motion);
            // Flat interior stays clear; the curved band compresses / folds actual background
            // features. RGB offsets follow the SAME normal and remain under one screen pixel.
            float dispersion = 0.008 * slope;
            float2 samplePoint = local - p * (0.004 * (1.0 - slope));
            half4 g = content.eval(uv(samplePoint + offset));
            half3 col = g.rgb;
            if (slope > 0.02) col = half3(content.eval(uv(samplePoint + offset * (1.0 - dispersion))).r,
                g.g, content.eval(uv(samplePoint + offset * (1.0 + dispersion))).b);
            float lum = dot(col, half3(0.2126, 0.7152, 0.0722));
            float adaptive = smoothstep(0.25, 0.85, lum);
            col = mix(col, half3(1.0 - adaptive), half(0.035 + 0.025 * adaptive));
            float light = dot(n, float2(-0.6, -0.8));
            float front = pow(max(light, 0.0), 4.5);
            float back = pow(max(-light, 0.0), 4.5);
            float hairDistance = (inside - 0.65 * density) / max(0.65 * density, 0.6);
            // Contributions below exp(-16) are invisible. Bound the exponent explicitly to
            // avoid driver/Skia fast-exp underflow and the resulting dark contour artifacts.
            float hair = exp(-min(hairDistance * hairDistance, 16.0));
            float glow = exp(-min(inside / max(2.0 * density, 1.0), 16.0));
            float spec = (hair * (front + 0.65 * back) * 0.42 + glow * front * 0.055);
            col += half3(spec * (1.0 - 0.45 * lum));
            col *= half(1.0 - 0.025 * back * exp(-min(abs(inside - 2.5 * density) / max(density, 0.5), 16.0)));
            return half4(clamp(col, half3(0.0), half3(1.0)) * half(coverage), half(coverage));
        }
    """

    private const val SOURCE = """
        uniform float2 origin;
        uniform float2 size;
        uniform float radius;
        uniform float density;
        uniform float dark;
        half4 main(float2 coord) {
            float2 p = coord - origin - size * 0.5;
            float2 q = abs(p) - size * 0.5 + radius;
            float distance = min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - radius;
            float inside = max(-distance, 0.0) / density;
            float2 direction = max(q, float2(0.001)) * sign(p);
            float2 normal = direction / max(length(direction), 0.001);
            float light = dot(normal, float2(-0.6, -0.8));
            float rim = 1.0 - smoothstep(0.15, 1.1, inside);
            float bevel = exp(-inside / 2.8);
            float innerShadow = exp(-abs(inside - 2.8) / 1.7) * max(-light, 0.0);
            float highlight = rim * (0.24 + 0.48 * max(light, 0.0)) + bevel * 0.12;
            float shade = innerShadow * mix(0.20, 0.12, dark);
            // A restrained cool/warm edge coating, never an RGB split of captured content.
            half3 cool = half3(0.88, 0.95, 1.0);
            half3 warm = half3(1.0, 0.95, 0.88);
            half3 color = mix(warm, cool, half(light * 0.5 + 0.5));
            float alpha = clamp(highlight + shade, 0.0, 0.85);
            color *= half(highlight / max(alpha, 0.001));
            return half4(color * half(alpha), half(alpha));
        }
    """
}
