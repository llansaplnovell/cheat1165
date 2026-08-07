package Magic.utils.render;

import java.awt.Color;
import java.util.function.Consumer;
import org.lwjgl.input.Mouse;

/**
 * Same HSB color picker as {@link ColorPicker} plus a second strip, right next to
 * the hue strip, that drives the alpha (transparency) channel.
 *
 * <p>Layout, relative to the (x, y) passed to draw:
 * <pre>
 *   x .. x+width                  saturation/brightness square
 *   x+width+1  .. x+width+11      hue strip
 *   x+width+12 .. x+width+22      alpha strip      &lt;-- the extra one
 *   x+width+27 .. x+width+83      color preview + "#RRGGBBAA" label
 *   x+width+27 .. x+width+47      "C" (copy) and "P" (paste) buttons, y+17
 * </pre>
 *
 * <p>The square keeps the same size it has in the plain picker; the alpha strip and
 * the preview box are simply pushed 11px further right to make room for it.
 *
 * <p>Pick this class (through {@code ColorAlphaValue}) for settings where partial
 * transparency makes sense - ESP boxes, HUD backgrounds, tracers. Keep the plain
 * {@link ColorPicker} (through {@code ColorValue}) where it does not, e.g. ClickGui.
 */
public class ColorPickerAlpha extends ColorPicker {

    /** Gap between the hue strip and the alpha strip is 1px, the strip itself is 10px. */
    protected static final int ALPHA_STRIP_OFFSET = 12;
    protected static final int ALPHA_STRIP_WIDTH = 10;
    /** Size of one checkerboard cell drawn behind the alpha gradient. */
    private static final int CHECKER = 5;

    protected int alpha = 255;
    protected boolean alphaDragging;

    public ColorPickerAlpha(Consumer<ColorPicker> consumer, int savedColor) {
        super(consumer, savedColor);
        this.alpha = savedColor >> 24 & 0xFF;
    }

    public int getAlpha() {
        return this.alpha;
    }

    public void setAlpha(int alpha) {
        this.alpha = ColorPicker.clamp(alpha, 0, 255);
    }

    @Override
    protected int infoOffsetX() {
        return 27;
    }

    @Override
    protected int infoWidth() {
        return 56;
    }

    @Override
    protected String hexLabel() {
        Color c = this.displayColor != null ? this.displayColor : new Color(this.color, true);
        return String.format("%02X%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
    }

    /**
     * A color copied from a picker without an alpha strip carries no alpha, and lands
     * here fully opaque - the transparency is not inherited from whatever was set
     * before. A copy from another alpha picker keeps its own alpha.
     */
    @Override
    protected int pasteAlpha(int pastedAlpha, boolean pastedHasAlpha) {
        return pastedHasAlpha ? pastedAlpha : 255;
    }

    @Override
    public void setFromColor(Color color) {
        super.setFromColor(color);
        if (color != null) {
            this.alpha = color.getAlpha();
        }
    }

    @Override
    protected void drawExtraStrips(int x, int y, int width, int height, int mouseX, int mouseY, boolean isFront) {
        if (height <= 1) {
            return;
        }
        int left = x + width + ALPHA_STRIP_OFFSET;
        int right = left + ALPHA_STRIP_WIDTH;
        int rgb = (this.displayColor != null ? this.displayColor.getRGB() : this.color) & 0xFFFFFF;

        // Checkerboard so a fully transparent bottom end is still visible as a strip.
        for (int row = 0; row < height; row += CHECKER) {
            int bottom = Math.min(row + CHECKER, height);
            for (int col = 0; col * CHECKER < ALPHA_STRIP_WIDTH; ++col) {
                boolean dark = (row / CHECKER + col) % 2 == 0;
                this.drawRect(left + col * CHECKER, y + row, Math.min(left + (col + 1) * CHECKER, right), y + bottom, dark ? 0xFF9E9E9E : 0xFFE3E3E3);
            }
        }
        // Opaque at the top, fully transparent at the bottom.
        for (int row = 0; row < height; ++row) {
            int rowAlpha = 255 - Math.round(255.0f * (float) row / (float) (height - 1));
            this.drawRect(left, y + row, right, y + row + 1, rowAlpha << 24 | rgb);
        }
        // Selection marker, drawn like the one on the hue strip.
        int markerY = y + Math.round((float) (255 - this.alpha) / 255.0f * (float) (height - 1));
        this.drawRect(left, markerY - 2, right, markerY - 1, Color.BLACK.getRGB());
        this.drawRect(left, markerY + 1, right, markerY + 2, Color.BLACK.getRGB());

        if (isFront && Mouse.isButtonDown(0) && mouseX >= left && mouseX <= right && mouseY >= y && mouseY <= y + height) {
            int picked = ColorPicker.clamp(255 - Math.round((float) (mouseY - y) * 255.0f / (float) (height - 1)), 0, 255);
            this.alphaDragging = true;
            if (picked != this.alpha) {
                this.alpha = picked;
                this.fire();
            }
        } else if (this.alphaDragging) {
            this.alphaDragging = false;
            this.fire();
        }
    }
}
