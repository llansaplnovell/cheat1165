package Magic.mod.value.values;

import Magic.mod.Module;
import Magic.mod.value.Condition;
import Magic.mod.value.Type;
import Magic.mod.value.Value;
import Magic.mod.value.ValueManager;
import Magic.utils.render.ColorPicker;
import java.awt.Color;

/**
 * Color setting rendered as an HSB picker <b>without</b> an alpha strip - the alpha
 * of the stored color is kept as-is and the user cannot change it.
 *
 * <p>Use this for settings where transparency makes no sense (ClickGui color, ...).
 * For settings that should expose transparency use {@link ColorAlphaValue}, which
 * has the same name and the same GUI, plus one alpha strip.
 */
public class ColorValue extends Value<Color> {

    protected Color color;
    protected final ColorPicker picker;

    public ColorValue(String name, Module module, Color color, String description) {
        super(name, module, description);
        this.color = color;
        this.picker = this.createPicker(color);
        this.picker.setApplier(this::setValue);
        ValueManager.addToList(this);
    }

    public ColorValue(String name, Module module, Color color, String description, Condition condition) {
        this(name, module, color, description);
        this.setCondition(condition);
    }

    /**
     * Builds the picker backing this value. Called from the constructor, so it must
     * not touch subclass state.
     */
    protected ColorPicker createPicker(Color initial) {
        return new ColorPicker(this::onPicked, initial.getRGB());
    }

    /**
     * Called by the picker once the user finishes dragging inside the gradient. The
     * picked color carries no alpha of its own, so the current one is kept.
     */
    protected void onPicked(ColorPicker picker) {
        Color picked = picker.currentColor;
        if (picked == null) {
            return;
        }
        this.color = new Color(picked.getRed(), picked.getGreen(), picked.getBlue(), this.color.getAlpha());
        this.getModule().onSuffixChange();
    }

    @Override
    public Color getValue() {
        return this.color;
    }

    @Override
    public void setValue(Color newValue) {
        if (newValue == null) {
            return;
        }
        this.color = newValue;
        // Moves the markers and rebuilds the gradient's hue, so a pasted or loaded
        // color shows up on the picker itself and not just in the preview box.
        this.picker.setFromColor(newValue);
        this.getModule().onSuffixChange();
    }

    @Override
    public Type getType() {
        return Type.COLOR;
    }

    public ColorPicker picker() {
        return this.picker;
    }
}
