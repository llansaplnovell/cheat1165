package Magic.utils.render;

import Magic.utils.math.Timer;
import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * HSB color picker without an alpha strip.
 *
 * <p>Layout, relative to the (x, y) passed to {@link #draw}:
 * <pre>
 *   x .. x+width                  saturation/brightness square
 *   x+width+1  .. x+width+11      hue strip
 *   x+width+16 .. x+width+60      color preview + "#RRGGBB" label
 *   x+width+16 .. x+width+36      "C" (copy) and "P" (paste) buttons, y+17
 * </pre>
 *
 * <p>Use this one when the setting must stay fully opaque (ClickGui color, ...).
 * For a picker that also lets the user choose transparency use
 * {@link ColorPickerAlpha}, which keeps the exact same look and adds one more
 * strip next to the hue strip.
 */
public class ColorPicker {

    /** Edge length of the small "C" / "P" buttons. */
    protected static final int BUTTON_SIZE = 9;
    /** Distance between the top of the preview box and the top of the buttons. */
    protected static final int BUTTON_OFFSET_Y = 17;

    protected final Timer timer = new Timer();
    public int currentValue;
    public int x;
    public int y;
    public int width;
    public int height;
    public int color;
    protected boolean typing;
    public String hex;
    public Color currentColor;
    protected FontRenderer font;
    protected int lastMouseX;
    protected int lastMouseY;
    protected Consumer<ColorPicker> consumer;
    protected boolean dragging;

    /** Last color handed to {@link #draw} - i.e. the value the setting currently holds. */
    protected Color displayColor = Color.WHITE;
    /** Set by the owning value so the paste button can push a whole color back into it. */
    protected Consumer<Color> applier;

    public ColorPicker(Consumer<ColorPicker> consumer, int savedColor) {
        this.font = Minecraft.getMinecraft().fontRendererObj;
        this.consumer = consumer;
        this.color = savedColor;
        this.displayColor = new Color(savedColor, true);
    }

    /**
     * Installs the callback used by the paste button. The owning value passes its
     * own setter here so pasting updates the value, the hex label and the gradient.
     */
    public void setApplier(Consumer<Color> applier) {
        this.applier = applier;
    }

    private Color getHoverColor() {
        ByteBuffer rgb = BufferUtils.createByteBuffer(100);
        GL11.glReadPixels(Mouse.getX(), Mouse.getY(), 1, 1, 6407, 5121, rgb);
        Color read = new Color(rgb.get(0) & 0xFF, rgb.get(1) & 0xFF, rgb.get(2) & 0xFF);
        if (read.getRGB() == -2173) {
            return read.brighter();
        }
        return read;
    }

    public void draw(int x, int y, int width, int height, int mouseX, int mouseY, Color currentColor) {
        this.draw(x, y, width, height, mouseX, mouseY, currentColor, true);
    }

    public void drawRect(double left, double top, double right, double bottom, int color) {
        if (left < right) {
            double i = left;
            left = right;
            right = i;
        }
        if (top < bottom) {
            double j = top;
            top = bottom;
            bottom = j;
        }
        float f3 = (float) (color >> 24 & 0xFF) / 255.0f;
        float f = (float) (color >> 16 & 0xFF) / 255.0f;
        float f1 = (float) (color >> 8 & 0xFF) / 255.0f;
        float f2 = (float) (color & 0xFF) / 255.0f;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(f, f1, f2, f3);
        worldrenderer.begin(7, DefaultVertexFormats.POSITION);
        worldrenderer.pos(left, bottom, 0.0).endVertex();
        worldrenderer.pos(right, bottom, 0.0).endVertex();
        worldrenderer.pos(right, top, 0.0).endVertex();
        worldrenderer.pos(left, top, 0.0).endVertex();
        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.resetColor();
    }

    public void draw(int x, int y, int width, int height, int mouseX, int mouseY, Color currentColor, boolean isFront) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        if (currentColor != null) {
            this.displayColor = currentColor;
        }
        float f = (float) (this.color >> 16 & 0xFF) / 255.0f;
        float f1 = (float) (this.color >> 8 & 0xFF) / 255.0f;
        float f2 = (float) (this.color & 0xFF) / 255.0f;
        int i = 0;
        while (i < height) {
            this.drawRect(x + width + 1, (double) y + 1.0 * (double) i, x + width + 11, (double) y + 1.0 * (double) (i + 1), Color.HSBtoRGB((float) i / (float) height, 1.0f, 1.0f));
            if (isFront && Mouse.isButtonDown(0) && mouseX >= x + width + 1 && mouseX <= x + width + 11 && (double) mouseY >= (double) y + 1.0 * (double) i && (double) mouseY <= (double) y + 1.0 * (double) (i + 1)) {
                this.color = Color.HSBtoRGB((float) i / (float) height, 1.0f, 1.0f);
            }
            ++i;
        }
        i = 0;
        while (i < height) {
            if (this.color == Color.HSBtoRGB((float) i / (float) height, 1.0f, 1.0f)) {
                this.drawRect(x + width + 1, (double) y + 1.0 * (double) i + 1.0, x + width + 11, (double) y + 1.0 * (double) (i + 1) + 2.0, Color.black.getRGB());
                this.drawRect(x + width + 1, (double) y + 1.0 * (double) i - 2.0, x + width + 11, (double) y + 1.0 * (double) (i + 1) - 1.0, Color.black.getRGB());
            }
            ++i;
        }
        GlStateManager.enableBlend();
        GL11.glEnable(3042);
        GL11.glShadeModel(7425);
        GlStateManager.blendFunc(770, 771);
        GL11.glBlendFunc(770, 771);
        GL11.glDisable(3553);
        GL11.glBegin(7);
        ColorPicker.glColor(new Color(f, f1, f2));
        GL11.glVertex2d(x + width, y);
        ColorPicker.glColor(Color.white);
        GL11.glVertex2d(x, y);
        ColorPicker.glColor(Color.BLACK);
        GL11.glVertex2d(x, y + height);
        ColorPicker.glColor(Color.BLACK);
        GL11.glVertex2d(x + width, y + height);
        GL11.glEnd();
        GL11.glEnable(3553);

        // Strips owned by subclasses (the alpha strip) sit right after the hue strip.
        this.drawExtraStrips(x, y, width, height, mouseX, mouseY, isFront);

        if (isFront && Mouse.isButtonDown(0) && this.isHover(mouseX, mouseY)) {
            int hoverColor;
            this.dragging = true;
            this.currentValue = hoverColor = this.getHoverColor().getRGB();
            this.currentColor = this.getHoverColor();
            this.hex = ColorPicker.toHex(hoverColor);
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
        } else if (this.dragging) {
            this.fire();
            this.dragging = false;
        }
        if (this.hex == null) {
            this.hex = ColorPicker.toHex(this.currentValue);
        }
        RenderUtils.drawBorderedRect(this.lastMouseX - 2, this.lastMouseY - 2, this.lastMouseX + 2, this.lastMouseY + 2, 1.0, this.currentValue, Color.BLACK.getRGB(), true);

        int infoX = x + width + this.infoOffsetX();
        this.drawRect(infoX, y, infoX + this.infoWidth(), y + 14, this.displayColor.getRGB());
        this.font.drawStringWithShadow("#" + this.hexLabel() + (this.typing && !this.timer.delay(250.0f) ? "_" : ""), infoX + 1, y + 21 - this.font.FONT_HEIGHT * 2, Color.WHITE.getRGB());
        this.drawCopyPasteButtons(infoX, y + BUTTON_OFFSET_Y, mouseX, mouseY);
        this.timer.delay(500L, func -> {
        }, true);
        this.typing = false;
    }

    /** Hook for subclasses that render additional strips (see {@link ColorPickerAlpha}). */
    protected void drawExtraStrips(int x, int y, int width, int height, int mouseX, int mouseY, boolean isFront) {
    }

    /** Distance from the right edge of the gradient to the preview box. */
    protected int infoOffsetX() {
        return 16;
    }

    /** Width of the preview box - wide enough for the hex label it has to hold. */
    protected int infoWidth() {
        return 44;
    }

    /** Hex label shown in the preview box and put on the clipboard, without the leading '#'. */
    protected String hexLabel() {
        Color c = this.displayColor != null ? this.displayColor : new Color(this.color, true);
        return String.format("%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    /** Alpha kept when a pasted string carries none. Overridden by the alpha picker. */
    protected int pasteAlpha(int pastedAlpha, boolean pastedHasAlpha) {
        return this.displayColor != null ? this.displayColor.getAlpha() : 255;
    }

    protected void fire() {
        if (this.consumer != null) {
            this.consumer.accept(this);
        }
    }

    private void drawCopyPasteButtons(int buttonsX, int buttonsY, int mouseX, int mouseY) {
        this.drawButton(buttonsX, buttonsY, "C", mouseX, mouseY);
        this.drawButton(buttonsX + BUTTON_SIZE + 2, buttonsY, "P", mouseX, mouseY);
    }

    private void drawButton(int left, int top, String label, int mouseX, int mouseY) {
        boolean hovered = ColorPicker.inside(mouseX, mouseY, left, top, BUTTON_SIZE, BUTTON_SIZE);
        this.drawRect(left, top, left + BUTTON_SIZE, top + BUTTON_SIZE, hovered ? 0xFF5A5A5A : 0xFF2E2E2E);
        this.drawRect(left, top, left + BUTTON_SIZE, top + 1, 0xFF7A7A7A);
        int labelX = left + (BUTTON_SIZE - this.font.getStringWidth(label)) / 2 + 1;
        this.font.drawStringWithShadow(label, labelX, top + 1, hovered ? 0xFFFFFFFF : 0xFFBBBBBB);
    }

    /**
     * Handles a click on the "C" / "P" buttons.
     *
     * @return true when the click was consumed by one of the buttons
     */
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return false;
        }
        int buttonsX = this.x + this.width + this.infoOffsetX();
        int buttonsY = this.y + BUTTON_OFFSET_Y;
        if (ColorPicker.inside(mouseX, mouseY, buttonsX, buttonsY, BUTTON_SIZE, BUTTON_SIZE)) {
            this.copy();
            return true;
        }
        if (ColorPicker.inside(mouseX, mouseY, buttonsX + BUTTON_SIZE + 2, buttonsY, BUTTON_SIZE, BUTTON_SIZE)) {
            this.paste();
            return true;
        }
        return false;
    }

    /** Puts the current color on the system clipboard as "#RRGGBB" (or "#RRGGBBAA"). */
    public void copy() {
        GuiScreen.setClipboardString("#" + this.hexLabel());
    }

    /** Reads a hex color off the system clipboard and applies it to the owning value. */
    public void paste() {
        Color parsed = this.parseColor(GuiScreen.getClipboardString());
        if (parsed == null) {
            return;
        }
        if (this.applier != null) {
            this.applier.accept(parsed);
        }
        this.currentColor = parsed;
        this.currentValue = parsed.getRGB();
        this.color = parsed.getRGB();
        this.hex = ColorPicker.toHex(this.currentValue);
        this.displayColor = parsed;
    }

    /** Accepts "#RRGGBB", "RRGGBB", "0xRRGGBB" and the same three with a trailing "AA". */
    protected Color parseColor(String text) {
        if (text == null) {
            return null;
        }
        String s = text.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        } else if (s.length() > 2 && (s.startsWith("0x") || s.startsWith("0X"))) {
            s = s.substring(2);
        }
        if (s.length() != 6 && s.length() != 8) {
            return null;
        }
        for (int i = 0; i < s.length(); ++i) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return null;
            }
        }
        int r = Integer.parseInt(s.substring(0, 2), 16);
        int g = Integer.parseInt(s.substring(2, 4), 16);
        int b = Integer.parseInt(s.substring(4, 6), 16);
        boolean hasAlpha = s.length() == 8;
        int a = hasAlpha ? Integer.parseInt(s.substring(6, 8), 16) : 255;
        return new Color(r, g, b, this.pasteAlpha(a, hasAlpha));
    }

    protected static boolean inside(int mouseX, int mouseY, int left, int top, int width, int height) {
        return mouseX >= left && mouseX <= left + width && mouseY >= top && mouseY <= top + height;
    }

    protected static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    /** Null- and leading-zero-safe replacement for Integer.toHexString(rgb).substring(2). */
    protected static String toHex(int argb) {
        return String.format("%06X", argb & 0xFFFFFF);
    }

    public static void glColor(Color color) {
        GlStateManager.color((float) color.getRed() / 255.0f, (float) color.getGreen() / 255.0f, (float) color.getBlue() / 255.0f, (float) color.getAlpha() / 255.0f);
    }

    public void handleInput(char typedChar, int keyCode) {
        block12: {
            try {
                if (!this.typing) break block12;
                int digit = Character.digit(typedChar, 16);
                switch (keyCode) {
                    case 14: {
                        if (this.hex.length() > 0) {
                            this.hex = this.hex.substring(0, this.hex.length() - 1);
                            if (this.hex.replace("-", "").length() > 0) {
                                this.currentValue = Integer.parseInt(this.hex, 16);
                            }
                        }
                        break;
                    }
                    case 28:
                    case 207: {
                        this.typing = false;
                        if (this.hex.replace("-", "").length() > 0) {
                            this.currentValue = Integer.parseInt(this.hex, 16);
                        }
                        break;
                    }
                    default: {
                        if (typedChar == '-' || digit >= 0) {
                            this.hex = String.valueOf(this.hex) + typedChar;
                            if (this.hex.replace("-", "").length() > 0) {
                                this.currentValue = Integer.parseInt(this.hex, 16);
                            }
                        }
                        break;
                    }
                }
            } catch (NumberFormatException e) {
                if (this.hex.length() <= 0) break block12;
                this.hex = this.hex.substring(0, this.hex.length() - 1);
            }
        }
    }

    public boolean isHover(int mouseX, int mouseY) {
        return mouseX > this.x && mouseX < this.x + this.width && mouseY > this.y && mouseY < this.y + this.height;
    }
}
