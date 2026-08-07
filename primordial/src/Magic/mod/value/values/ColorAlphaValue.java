package Magic.mod.value.values;

import Magic.mod.Module;
import Magic.mod.value.Condition;
import Magic.mod.value.Type;
import Magic.utils.render.ColorPicker;
import Magic.utils.render.ColorPickerAlpha;
import java.awt.Color;

/**
 * Color setting rendered as an HSB picker <b>with</b> an alpha strip next to the hue
 * strip, so the user can pick transparency as well.
 *
 * <p>Declare it exactly like a {@link ColorValue}:
 * <pre>
 *   private final ColorAlphaValue color =
 *           new ColorAlphaValue("Color", this, new Color(255, 255, 255, 255), "ESP color.");
 * </pre>
 *
 * <p>It extends {@link ColorValue}, so the config writer/reader in {@code CGui}
 * (which already stores {@code r,g,b,a}) picks it up with no extra work.
 */
public class ColorAlphaValue extends ColorValue {

    public ColorAlphaValue(String name, Module module, Color color, String description) {
        super(name, module, color, description);
    }

    public ColorAlphaValue(String name, Module module, Color color, String description, Condition condition) {
        this(name, module, color, description);
        this.setCondition(condition);
    }

    @Override
    protected ColorPicker createPicker(Color initial) {
        return new ColorPickerAlpha(this::onPicked, initial.getRGB());
    }

    /**
     * Fired both when the gradient is released and while the alpha strip is dragged,
     * so the RGB part may be missing - in that case the stored one is reused.
     */
    @Override
    protected void onPicked(ColorPicker picker) {
        Color picked = picker.currentColor != null ? picker.currentColor : this.color;
        int alpha = picker instanceof ColorPickerAlpha ? ((ColorPickerAlpha) picker).getAlpha() : this.color.getAlpha();
        this.color = new Color(picked.getRed(), picked.getGreen(), picked.getBlue(), alpha);
        this.getModule().onSuffixChange();
    }

    @Override
    public void setValue(Color newValue) {
        super.setValue(newValue);
        if (newValue != null) {
            this.picker().setAlpha(newValue.getAlpha());
        }
    }

    @Override
    public Type getType() {
        return Type.COLOR_ALPHA;
    }

    @Override
    public ColorPickerAlpha picker() {
        return (ColorPickerAlpha) this.picker;
    }
}
