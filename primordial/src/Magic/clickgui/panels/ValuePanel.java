package Magic.clickgui.panels;

import Magic.Magic;
import Magic.clickgui.GuiSettings;
import Magic.clickgui.NewClickGui;
import Magic.clickgui.SliderRenderFix;
import Magic.ink.event.EventManager;
import Magic.ink.event.s.EventTick;
import Magic.mod.Modules;
import Magic.mod.s.render.CGui;
import Magic.mod.value.Mode;
import Magic.mod.value.Type;
import Magic.mod.value.Value;
import Magic.mod.value.values.BoolValue;
import Magic.mod.value.values.ColorAlphaValue;
import Magic.mod.value.values.ColorValue;
import Magic.mod.value.values.EnumValue;
import Magic.mod.value.values.ModeValue;
import Magic.mod.value.values.NumberValue;
import Magic.mod.value.values.PositionValue;
import Magic.mod.value.values.TextValue;
import Magic.utils.math.MathUtils;
import Magic.utils.render.RenderUtils;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;
import pisi.unitedmeows.eventapi.event.listener.Listener;

@SuppressWarnings({"rawtypes", "unchecked"})
public class ValuePanel extends Panel {

    /** Left inset of the gradient inside a color row. */
    public static final int COLOR_PICKER_X = 3;
    /** Gradient width of a plain color picker - hue strip and hex box follow it. */
    public static final int COLOR_PICKER_WIDTH = 68;
    /**
     * Gradient width of a picker that also shows the alpha strip - the same square as
     * the plain one. The alpha strip and the hex box are pushed 11px further right by
     * {@code ColorPickerAlpha} to make room for it.
     */
    public static final int COLOR_ALPHA_PICKER_WIDTH = COLOR_PICKER_WIDTH;
    /** Height of both color gradients. */
    public static final int COLOR_PICKER_HEIGHT = 70;

    public Value<?> value;
    public float x;
    public float y;
    public float width;
    public float height;
    private boolean numberDrag;
    private boolean positionDrag;
    public ModulePanel panel;
    private int boolAlpha;
    private int prevBoolAlpha;
    private double numberWidth;
    private double prevNumberWidth;
    private float hoverX;
    private float prevHoverX;
    private boolean hovered;
    private Listener<EventTick> onTick = new Listener<EventTick>(event -> {
        CGui cGui = Modules.CLICK_GUI;
        float m = 10.0f;
        this.prevHoverX = this.hoverX;
        if (this.hovered && this.hoverX < this.width / 2.0f) {
            this.hoverX += m;
        } else if (!this.hovered && this.hoverX > 2.0f) {
            this.hoverX -= m;
        }
        this.hoverX = MathHelper.clamp_float(this.hoverX, 2.0f, this.width / 2.0f);
        Value<?> value = this.value;
        if (value instanceof BoolValue) {
            BoolValue boolValue = (BoolValue) value;
            if (cGui.anim.getValue().booleanValue()) {
                int increase = 20;
                this.prevBoolAlpha = this.boolAlpha;
                if (this.boolAlpha < 255 && boolValue.getValue().booleanValue()) {
                    this.boolAlpha += increase;
                } else if (this.boolAlpha > 0 && !boolValue.getValue().booleanValue()) {
                    this.boolAlpha -= increase;
                }
                this.boolAlpha = MathHelper.clamp_int(this.boolAlpha, 0, 255);
            } else {
                this.prevBoolAlpha = this.boolAlpha = boolValue.getValue().booleanValue() ? GuiSettings.CLIENT_COLOR.getAlpha() : 0;
            }
        } else {
            Value<?> value2 = this.value;
            if (value2 instanceof NumberValue) {
                NumberValue numberValue = (NumberValue) value2;
                double value3 = (((Number) numberValue.getValue()).doubleValue() - ((Number) numberValue.getMin()).doubleValue()) / (((Number) numberValue.getMax()).doubleValue() - ((Number) numberValue.getMin()).doubleValue());
                double valueWidth = value3 * ((double) this.width - 8.0);
                if (cGui.anim.getValue().booleanValue()) {
                    double increase = 5.0;
                    this.prevNumberWidth = this.numberWidth;
                    if (this.numberWidth > valueWidth) {
                        this.numberWidth -= increase;
                        this.numberWidth = Math.max(this.numberWidth, valueWidth);
                    } else if (this.numberWidth < valueWidth) {
                        this.numberWidth += increase;
                        this.numberWidth = Math.min(this.numberWidth, valueWidth);
                    }
                } else {
                    this.prevNumberWidth = this.numberWidth = valueWidth;
                }
            }
        }
    }).filter(f -> Minecraft.getMinecraft().currentScreen instanceof NewClickGui);

    public ValuePanel(Value<?> value, ModulePanel panel) {
        this.value = value;
        this.height = 14.0f;
        if (value.getType() == Type.NUMBER) {
            this.height = 24.0f;
        } else if (value.getType() == Type.POSITION) {
            this.height = 100.0f;
        } else if (value.getType() == Type.COLOR || value.getType() == Type.COLOR_ALPHA) {
            this.height = COLOR_PICKER_HEIGHT;
        }
        this.x = panel.x;
        this.y = panel.y + panel.valueHeight + 15.0f;
        this.width = panel.width;
        this.panel = panel;
        EventManager.eventSystem.subscribeAll(this);
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        if (!this.value.isOpen()) {
            return;
        }
        this.hovered = this.isHovered(mouseX, mouseY);
        switch (this.value.getType()) {
            case COLOR: {
                ColorValue v = (ColorValue) this.value;
                GuiSettings.getNormalFont().drawString(this.value.getName() + " : ", (float) ((int) this.x + 2), (float) ((int) this.y - 2), v.getValue());
                v.picker().draw((int) this.x + COLOR_PICKER_X, (int) this.y + 7, COLOR_PICKER_WIDTH, COLOR_PICKER_HEIGHT, mouseX, mouseY, v.getValue());
                break;
            }
            case COLOR_ALPHA: {
                ColorAlphaValue v = (ColorAlphaValue) this.value;
                Color label = v.getValue();
                // The label keeps full opacity, otherwise a low alpha would hide the name.
                GuiSettings.getNormalFont().drawString(this.value.getName() + " : ", (float) ((int) this.x + 2), (float) ((int) this.y - 2), new Color(label.getRed(), label.getGreen(), label.getBlue(), 255));
                v.picker().draw((int) this.x + COLOR_PICKER_X, (int) this.y + 7, COLOR_ALPHA_PICKER_WIDTH, COLOR_PICKER_HEIGHT, mouseX, mouseY, v.getValue());
                break;
            }
            case POSITION: {
                PositionValue v = (PositionValue) this.value;
                if (this.positionDrag && this.hovered) {
                    v.setX((float) mouseX - this.x);
                    v.setY((float) mouseY - this.y - 12.0f);
                }
                GuiSettings.getNormalFont().drawString(v.getName() + " : ", (float) ((int) this.x + 2), (float) ((int) this.y - 1), GuiSettings.MODULE_DISABLED);
                RenderUtils.drawFixedRect(this.x + 2.0f, this.y + 9.0f, this.x + this.width - 2.0f, this.y + 95.0f, GuiSettings.CLIENT_COLOR);
                RenderUtils.drawFixedRect(this.x + 3.0f, this.y + 10.0f, this.x + this.width - 3.0f, this.y + 94.0f, GuiSettings.COL_VALUE1);
                double i = this.y + 20.0f;
                while (i < (double) (this.y + 95.0f)) {
                    RenderUtils.drawFixedRect(this.x + 3.0f, i, this.x + this.width - 3.0f, i + 1.0, GuiSettings.COL_VALUE2);
                    i += 10.0;
                }
                i = this.x + 10.0f;
                while (i < (double) (this.x + this.width - 3.0f)) {
                    RenderUtils.drawFixedRect(i, this.y + 10.0f, i + 1.0, this.y + 94.0f, GuiSettings.COL_VALUE2);
                    i += 10.1;
                }
                RenderUtils.drawFixedRect((double) (this.x + 2.0f) + v.getX() - 1.0, (double) (this.y + 12.0f) + v.getY(), (double) (this.x + 1.0f) + v.getX() + 3.0, (double) (this.y + 13.0f) + v.getY(), GuiSettings.COL_VALUE3);
                RenderUtils.drawFixedRect((double) (this.x + 2.0f) + v.getX() - 5.0, (double) (this.y + 12.0f) + v.getY(), (double) (this.x + 1.0f) + v.getX() - 1.0, (double) (this.y + 13.0f) + v.getY(), GuiSettings.COL_VALUE3);
                RenderUtils.drawFixedRect((double) (this.x + 2.0f) + v.getX() - 1.0, (double) (this.y + 12.0f) + v.getY() - 3.0, (double) (this.x + 1.0f) + v.getX() - 1.0, (double) (this.y + 12.0f) + v.getY(), GuiSettings.COL_VALUE3);
                RenderUtils.drawFixedRect((double) (this.x + 2.0f) + v.getX() - 1.0, (double) (this.y + 12.0f) + v.getY() + 1.0, (double) (this.x + 1.0f) + v.getX() - 1.0, (double) (this.y + 12.0f) + v.getY() + 4.0, GuiSettings.COL_VALUE3);
                break;
            }
            case MODE: {
                ModeValue modeValue = (ModeValue) this.value;
                float animX = this.prevHoverX + (this.hoverX - this.prevHoverX) * partialTicks;
                RenderUtils.drawFixedRect(this.x + 2.0f + this.width / 2.0f - animX, this.y - 5.0f, this.x + this.width - 2.0f - this.width / 2.0f + animX, this.y + this.height - 5.0f, GuiSettings.CATEGORY_COLOR);
                if (this.hovered) {
                    RenderUtils.drawRoundedRect((double) (this.x + this.width) - 0.5, (double) this.y - 5.5, GuiSettings.getNormalFont().getWidth(this.value.getName() + ": " + this.value.getDescription()) + 1.0f, this.height + 1.0f, 2.0, GuiSettings.CLIENT_COLOR);
                    RenderUtils.drawRoundedRect(this.x + this.width - 1.0f, this.y - 5.0f, GuiSettings.getNormalFont().getWidth(this.value.getName() + ": " + this.value.getDescription()) + 1.0f, this.height, 2.0, GuiSettings.CATEGORY_COLOR);
                    GuiSettings.getNormalFont().drawString(this.value.getName() + ": " + this.value.getDescription(), (float) ((int) (this.x + this.width - 1.0f)), (float) ((int) (this.y - 1.0f)), GuiSettings.MODULE_DISABLED);
                }
                GuiSettings.getNormalFont().drawString("<" + ((Mode) modeValue.getValue()).getName() + ">", (float) ((int) (this.x + (this.width / 2.0f - GuiSettings.getNormalFont().getWidth("<" + ((Mode) modeValue.getValue()).getName() + ">") / 2.0f))), (float) ((int) this.y - 1), GuiSettings.CLIENT_COLOR);
                GuiSettings.getNormalFont().drawString("|", (float) ((int) this.x + 2), (float) ((int) this.y - 1), GuiSettings.CLIENT_COLOR);
                GuiSettings.getNormalFont().drawString("|", (float) ((int) this.x + (int) this.width - 6), (float) ((int) this.y - 1), GuiSettings.CLIENT_COLOR);
                break;
            }
            case ENUM: {
                EnumValue modeValue = (EnumValue) this.value;
                float animX = this.prevHoverX + (this.hoverX - this.prevHoverX) * partialTicks;
                RenderUtils.drawFixedRect(this.x + 2.0f + this.width / 2.0f - animX, this.y - 5.0f, this.x + this.width - 2.0f - this.width / 2.0f + animX, this.y + this.height - 5.0f, GuiSettings.CATEGORY_COLOR);
                if (this.hovered) {
                    RenderUtils.drawRoundedRect((double) (this.x + this.width) - 0.5, (double) this.y - 5.5, GuiSettings.getNormalFont().getWidth(this.value.getName() + ": " + this.value.getDescription()) + 1.0f, this.height + 1.0f, 2.0, GuiSettings.CLIENT_COLOR);
                    RenderUtils.drawRoundedRect(this.x + this.width - 1.0f, this.y - 5.0f, GuiSettings.getNormalFont().getWidth(this.value.getName() + ": " + this.value.getDescription()) + 1.0f, this.height, 2.0, GuiSettings.CATEGORY_COLOR);
                    GuiSettings.getNormalFont().drawString(this.value.getName() + ": " + this.value.getDescription(), (float) ((int) (this.x + this.width - 1.0f)), (float) ((int) (this.y - 1.0f)), GuiSettings.MODULE_DISABLED);
                }
                GuiSettings.getNormalFont().drawString("<" + ((Enum) modeValue.getValue()).name() + ">", (float) ((int) (this.x + (this.width / 2.0f - GuiSettings.getNormalFont().getWidth("<" + ((Enum) modeValue.getValue()).name() + ">") / 2.0f))), (float) ((int) this.y - 1), GuiSettings.CLIENT_COLOR);
                GuiSettings.getNormalFont().drawString("|", (float) ((int) this.x + 2), (float) ((int) this.y - 1), GuiSettings.CLIENT_COLOR);
                GuiSettings.getNormalFont().drawString("|", (float) ((int) this.x + (int) this.width - 6), (float) ((int) this.y - 1), GuiSettings.CLIENT_COLOR);
                break;
            }
            case TEXT: {
                TextValue textValue = (TextValue) this.value;
                float animX = this.prevHoverX + (this.hoverX - this.prevHoverX) * partialTicks;
                RenderUtils.drawFixedRect(this.x + 2.0f + this.width / 2.0f - animX, this.y - 5.0f, this.x + this.width - 2.0f - this.width / 2.0f + animX, this.y + this.height - 5.0f, GuiSettings.CATEGORY_COLOR);
                if (this.hovered) {
                    RenderUtils.drawRoundedRect((double) (this.x + this.width) - 0.5, (double) this.y - 5.5, GuiSettings.getNormalFont().getWidth(this.value.getDescription()) + 1.0f, this.height + 1.0f, 2.0, GuiSettings.CLIENT_COLOR);
                    RenderUtils.drawRoundedRect(this.x + this.width - 1.0f, this.y - 5.0f, GuiSettings.getNormalFont().getWidth(this.value.getDescription()) + 1.0f, this.height, 2.0, GuiSettings.CATEGORY_COLOR);
                    GuiSettings.getNormalFont().drawString(this.value.getDescription(), (float) ((int) this.x + (int) this.width - 1), this.y - 1.0f, GuiSettings.MODULE_DISABLED);
                }
                GuiSettings.getNormalFont().drawString(textValue.getName() + ": " + textValue.getValue(), (float) ((int) this.x + 2), (float) ((int) this.y), GuiSettings.MODULE_DISABLED);
                if (Magic.newClickGui.textField == null || !Magic.newClickGui.textField.isFocused()) break;
                textValue.setValue(Magic.newClickGui.textField.getText());
                break;
            }
            case NUMBER: {
                NumberValue numberValue = (NumberValue) this.value;
                float animX = this.prevHoverX + (this.hoverX - this.prevHoverX) * partialTicks;
                RenderUtils.drawFixedRect(this.x + 2.0f + this.width / 2.0f - animX, this.y - 5.0f, this.x + this.width - 2.0f - this.width / 2.0f + animX, this.y + this.height - 5.0f, GuiSettings.CATEGORY_COLOR);
                if (this.hovered) {
                    RenderUtils.drawRoundedRect((double) (this.x + this.width) - 0.5, (double) this.y - 1.5, GuiSettings.getNormalFont().getWidth(this.value.getDescription()) + 1.0f, 15.0, 2.0, GuiSettings.CLIENT_COLOR);
                    RenderUtils.drawRoundedRect(this.x + this.width - 1.0f, this.y - 1.0f, GuiSettings.getNormalFont().getWidth(this.value.getDescription()) + 1.0f, 14.0, 2.0, GuiSettings.CATEGORY_COLOR);
                    GuiSettings.getNormalFont().drawString(this.value.getDescription(), (float) ((int) this.x + (int) this.width - 1), (float) ((int) this.y + 3), GuiSettings.MODULE_DISABLED);
                }
                if (this.numberDrag) {
                    float min = ((Number) numberValue.getMin()).floatValue();
                    float max = ((Number) numberValue.getMax()).floatValue();
                    float inc = ((Number) numberValue.getIncrement()).floatValue();
                    float valAbs = (float) mouseX - (this.x + 4.0f);
                    float perc = valAbs / (this.width - 8.0f);
                    perc = Math.min(Math.max(0.0f, perc), 1.0f);
                    float valRel = (max - min) * perc;
                    float val1 = min + valRel;
                    val1 = (float) Math.round(val1 * (1.0f / inc)) / (1.0f / inc);
                    if (numberValue.getIncrement() instanceof Float) {
                        numberValue.setValue(Float.valueOf(val1));
                    } else if (numberValue.getIncrement() instanceof Double) {
                        numberValue.setValue(MathUtils.fixFormat((double) val1, 6));
                    } else if (numberValue.getIncrement() instanceof Integer) {
                        numberValue.setValue((int) val1);
                    }
                }
                GuiSettings.getNormalFont().drawString(numberValue.getName(), (float) ((int) (this.x + (this.width / 2.0f - GuiSettings.getNormalFont().getWidth(numberValue.getName()) / 2.0f))), (float) ((int) (this.y - 2.0f)), GuiSettings.MODULE_DISABLED);
                RenderUtils.drawRoundedRect(this.x + 3.0f, this.y + 6.0f, this.width - 6.0f, 10.0, 1.0, GuiSettings.MODULE_DISABLED);
                RenderUtils.drawRoundedRect((double) this.x + 3.5, (double) this.y + 6.5, this.width - 7.0f, 9.0, 1.0, GuiSettings.CATEGORY_COLOR);
                RenderUtils.drawRoundedRect(this.x + 4.0f, this.y + 7.0f, this.prevNumberWidth + (this.numberWidth - this.prevNumberWidth) * (double) partialTicks, 8.0, 1.5, GuiSettings.CLIENT_COLOR);
                SliderRenderFix.drawValue(this, numberValue, this.prevNumberWidth + (this.numberWidth - this.prevNumberWidth) * (double) partialTicks);
                break;
            }
            case BOOLEAN: {
                float animX = this.prevHoverX + (this.hoverX - this.prevHoverX) * partialTicks;
                RenderUtils.drawFixedRect(this.x + 2.0f + this.width / 2.0f - animX, this.y - 5.0f, this.x + this.width - 2.0f - this.width / 2.0f + animX, this.y + this.height - 5.0f, GuiSettings.CATEGORY_COLOR);
                if (this.hovered) {
                    RenderUtils.drawRoundedRect((double) (this.x + this.width) - 0.5, (double) this.y - 5.5, GuiSettings.getNormalFont().getWidth(this.value.getDescription()) + 1.0f, this.height + 1.0f, 2.0, GuiSettings.CLIENT_COLOR);
                    RenderUtils.drawRoundedRect(this.x + this.width - 1.0f, this.y - 5.0f, GuiSettings.getNormalFont().getWidth(this.value.getDescription()) + 1.0f, this.height, 2.0, GuiSettings.CATEGORY_COLOR);
                    GuiSettings.getNormalFont().drawString(this.value.getDescription(), (float) ((int) this.x + (int) this.width - 1), (float) ((int) this.y - 1), GuiSettings.MODULE_DISABLED);
                }
                GuiSettings.getNormalFont().drawString(this.value.getName(), (float) ((int) this.x + 2), (float) ((int) this.y), GuiSettings.MODULE_DISABLED);
                RenderUtils.drawRoundedRect(this.x + this.width - 15.0f, this.y - 3.0f, 10.0, 10.0, 1.0, GuiSettings.MODULE_DISABLED);
                RenderUtils.drawRoundedRect((double) (this.x + this.width) - 14.5, (double) this.y - 2.5, 9.0, 9.0, 1.0, GuiSettings.CATEGORY_COLOR);
                this.drawCheck(partialTicks);
            }
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (this.value.getType() == Type.NUMBER) {
            this.numberDrag = true;
        } else if (this.value.getType() == Type.BOOLEAN) {
            BoolValue boolValue = (BoolValue) this.value;
            boolValue.setValue(!boolValue.getValue().booleanValue());
        } else if (this.value.getType() == Type.MODE) {
            ModeValue modeValue = (ModeValue) this.value;
            if (button == 0) {
                modeValue.setNext();
            } else if (button == 1) {
                modeValue.setPrevious();
            }
        } else if (this.value.getType() == Type.ENUM) {
            EnumValue modeValue = (EnumValue) this.value;
            if (button == 0) {
                modeValue.setNext();
            } else if (button == 1) {
                modeValue.setPrevious();
            }
        } else if (this.value.getType() == Type.POSITION) {
            this.positionDrag = true;
        } else if (this.value.getType() == Type.COLOR || this.value.getType() == Type.COLOR_ALPHA) {
            // Only the copy/paste buttons need a real click - the gradient, the hue
            // strip and the alpha strip poll the mouse themselves while rendering.
            ((ColorValue) this.value).picker().mouseClicked(mouseX, mouseY, button);
        } else if (this.value.getType() == Type.TEXT) {
            TextValue v = (TextValue) this.value;
            if (Magic.newClickGui.textField == null) {
                Magic.newClickGui.textField = new GuiTextField(1, Minecraft.getMinecraft().fontRendererObj, 1, 1, 1, 1);
            }
            Magic.newClickGui.textField.setFocused(true);
            Magic.newClickGui.textField.setMaxStringLength(2173);
            Magic.newClickGui.textField.setText("");
        }
    }

    public void keyTyped(char charCode, int keyCode) {
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
        this.numberDrag = false;
        this.positionDrag = false;
    }

    @Override
    public boolean isHovered(int mouseX, int mouseY) {
        if (this.value.getType() == Type.NUMBER) {
            return (float) mouseX >= this.x + 3.0f && (float) mouseX <= this.x + this.width - 3.0f && (float) mouseY > this.y + 6.0f && (float) mouseY <= this.y + 16.0f;
        }
        if (this.value.getType() == Type.POSITION) {
            return (float) mouseX >= this.x + 3.0f && (float) mouseX <= this.x + this.width - 3.0f && (float) mouseY > this.y + 10.0f && (float) mouseY <= this.y + 94.0f;
        }
        if (this.value.getType() == Type.COLOR || this.value.getType() == Type.COLOR_ALPHA) {
            // The hex box and its buttons hang past the panel, so the row has to reach
            // further right than the panel width - otherwise "C"/"P" never get clicked.
            return (float) mouseX >= this.x + 2.0f && (float) mouseX <= this.colorWidgetRight() && (float) mouseY > this.y + 10.0f && (float) mouseY <= this.y + 109.0f;
        }
        return (float) mouseX >= this.x && (float) mouseX <= this.x + this.width && (float) mouseY > this.y - 5.0f && (float) mouseY <= this.y + this.height - 5.0f;
    }

    /** Right edge of a color row: gradient, strips and the preview box with its buttons. */
    private float colorWidgetRight() {
        int gradient = this.value.getType() == Type.COLOR_ALPHA ? COLOR_ALPHA_PICKER_WIDTH : COLOR_PICKER_WIDTH;
        return this.x + COLOR_PICKER_X + ((ColorValue) this.value).picker().totalWidth(gradient);
    }

    public boolean isHovered(double x, double y, double width, double height, int mouseX, int mouseY) {
        return (double) mouseX >= x && (double) mouseX <= x + width && (double) mouseY > y - 5.0 && (double) mouseY <= y + height - 5.0;
    }

    public void drawCheck(float partialTicks) {
        if (this.prevBoolAlpha > 0 || this.boolAlpha > 0) {
            GL11.glPushMatrix();
            GlStateManager.enableBlend();
            GL11.glBlendFunc(770, 771);
            GL11.glDisable(3553);
            GL11.glEnable(2848);
            GL11.glBlendFunc(770, 771);
            GL11.glLineWidth(1.5f);
            GL11.glBegin(3);
            float animate = ((float) this.prevBoolAlpha + (float) (this.boolAlpha - this.prevBoolAlpha) * partialTicks) / 255.0f;
            double m = 4.0;
            double posAnimate = (double) animate * (m + 1.0);
            GL11.glColor4f((float) GuiSettings.CLIENT_COLOR.getRed() / 255.0f, (float) GuiSettings.CLIENT_COLOR.getGreen() / 255.0f, (float) GuiSettings.CLIENT_COLOR.getBlue() / 255.0f, animate);
            GL11.glVertex2d((double) (this.x + this.width) - 6.5 + posAnimate - m - 1.0, (double) this.y - posAnimate + m);
            GL11.glVertex2d((double) (this.x + this.width) - 11.5, this.y + 6.0f);
            GL11.glVertex2d((double) (this.x + this.width) - 13.5, this.y + 4.0f);
            GL11.glEnd();
            GL11.glEnable(3553);
            GlStateManager.disableBlend();
            GL11.glPopMatrix();
            GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }
}
