package miui.os;

/**
 * Test-only ROM flag read while inflating settings. This models row visibility,
 * not the HyperOS framework or any Hook behavior.
 */
public final class Build {
    private Build() {}

    public static boolean IS_INTERNATIONAL_BUILD = false;
}
