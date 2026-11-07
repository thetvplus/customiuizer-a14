package androidx.dynamicanimation.animation;

/** Test-only ABI fixture for the older host's property-based spring setter. */
public abstract class FloatPropertyCompat<T> {
    public FloatPropertyCompat(String name) {}
    public abstract float getValue(T object);
    public abstract void setValue(T object, float value);
}
