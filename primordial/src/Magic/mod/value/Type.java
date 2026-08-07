package Magic.mod.value;

public enum Type {
    NUMBER,
    BOOLEAN,
    MODE,
    ENUM,
    TEXT,
    POSITION,
    /** HSB picker without an alpha strip - {@code ColorValue}. */
    COLOR,
    /** HSB picker with an alpha strip - {@code ColorAlphaValue}. */
    COLOR_ALPHA;
}
