package Magic.clickgui.panels;

import Magic.clickgui.GuiSettings;
import Magic.clickgui.NewClickGui;
import Magic.ink.event.EventManager;
import Magic.ink.event.s.EventTick;
import Magic.mod.Module;
import Magic.mod.Modules;
import Magic.mod.s.render.CGui;
import Magic.mod.value.Type;
import Magic.mod.value.Value;
import Magic.mod.value.ValueManager;
import Magic.utils.font.FontUtil;
import Magic.utils.render.RenderUtils;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Mouse;
import pisi.unitedmeows.eventapi.event.listener.Listener;

public class ModulePanel extends Panel {

    /** Row height a color picker needs - gradient, hex box and the copy/paste buttons. */
    public static final double COLOR_ROW_HEIGHT = 86.0;

    private Module module;
    private int count;
    public float x;
    public float y;
    public float width;
    public float height;
    public float valueHeight;
    private FontUtil font = GuiSettings.getNormalFont();
    private boolean last;
    public CategoryPanel panel;
    public List<ValuePanel> values = new ArrayList<ValuePanel>();
    public boolean openedValues;
    private long lastCheck;
    private int hoverY;
    private int prevHoverY;
    private float infoAlpha;
    private float prevInfoAlpha;
    private boolean infoListen;
    private boolean hovered;
    private boolean binding;
    private static ModulePanel currentBinding;
    private Listener<EventTick> onTick = new Listener<EventTick>(event -> {
        CGui cGui = Modules.CLICK_GUI;
        if (cGui.anim.getValue().booleanValue()) {
            this.prevInfoAlpha = this.infoAlpha;
            if (this.infoListen) {
                if (this.infoAlpha < 1.0f) {
                    this.infoAlpha += 0.05f;
                }
            } else if (this.infoAlpha > 0.0f) {
                this.infoAlpha -= 0.05f;
            }
            this.infoAlpha = MathHelper.clamp_float(this.infoAlpha, 0.0f, 1.0f);
            int increase = 2;
            int height = 6;
            this.prevHoverY = this.hoverY;
            if (this.hovered && this.hoverY <= height) {
                this.hoverY += increase;
            } else if (!this.hovered && this.hoverY >= 0) {
                this.hoverY -= increase;
            }
            this.hoverY = MathHelper.clamp_int(this.hoverY, 0, height);
        } else {
            this.prevHoverY = 6;
            this.hoverY = 6;
            this.prevInfoAlpha = this.infoAlpha = (float) (this.infoListen ? 1 : 0);
        }
        if (this.binding && this.module != null) {
            for (int i = 3; i < Mouse.getButtonCount(); ++i) {
                if (!Mouse.isButtonDown(i)) continue;
                int mouseKey = -100 + (i - 3);
                this.module.setKey(mouseKey);
                this.binding = false;
                if (currentBinding == this) {
                    currentBinding = null;
                }
                CGui.saveNew();
                break;
            }
        }
    }).filter(f -> Minecraft.getMinecraft().currentScreen instanceof NewClickGui);

    public ModulePanel(Module module, int count, CategoryPanel panel, boolean last) {
        this.panel = panel;
        this.module = module;
        this.count = count;
        this.x = panel.x;
        this.y = panel.y + (float) (15 * count) + 20.0f;
        this.width = panel.width;
        this.height = 15.0f;
        this.last = last;
        for (Value<?> value : ValueManager.getValuesFromModule(module)) {
            ValuePanel panel1 = new ValuePanel(value, this);
            this.values.add(panel1);
            if (!value.isOpen()) continue;
            this.valueHeight += panel1.height;
        }
        EventManager.eventSystem.subscribeAll(this);
    }

    public static boolean isBindingNow() {
        return currentBinding != null;
    }

    public void preRender(int mouseX, int mouseY, float partialTicks) {
        List<ModulePanel> modules = this.panel.modules;
        this.rePositionValues();
        if (this.openedValues) {
            this.panel.totalHeight += this.valueHeight;
            for (int i = this.count + 1; i < this.panel.modules.size(); ++i) {
                this.panel.modules.get(i).y += this.valueHeight;
            }
        }
        if (this.infoAlpha > 0.0f) {
            float alpha = this.prevInfoAlpha + (this.infoAlpha - this.prevInfoAlpha) * partialTicks;
            double infoX = this.x + this.width;
            double infoWidth = this.font.getWidth(this.module.getDescription()) + 2.0f;
            RenderUtils.drawRoundedRect(infoX + 3.0, this.y - 5.0f, infoWidth + 2.0, this.height, 5.0, GuiSettings.CLIENT_COLOR, alpha);
            RenderUtils.drawRoundedRect(infoX + 4.0, this.y - 4.0f, infoWidth, this.height - 2.0f, 5.0, GuiSettings.CATEGORY_COLOR, alpha);
            this.font.drawString(this.module.getDescription(), (float) ((int) infoX + 6), (float) ((int) this.y - 1), new Color(GuiSettings.CLIENT_COLOR.getRed(), GuiSettings.CLIENT_COLOR.getGreen(), GuiSettings.CLIENT_COLOR.getBlue(), MathHelper.clamp_int((int) (alpha * 255.0f), 0, 255)));
        }
        if (this.isHovered(mouseX, mouseY)) {
            this.startListen();
            float animatedHoverY = (float) this.prevHoverY + (float) (this.hoverY - this.prevHoverY) * partialTicks;
            int maxHeight = 6;
            if (this.count == 0) {
                if (!this.openedValues) {
                    RenderUtils.drawFixedRect(this.x + 2.0f, this.y - 8.0f, this.x + this.width - 2.0f, this.y + this.height - 2.0f, GuiSettings.CATEGORY_COLOR);
                } else {
                    RenderUtils.drawFixedRect(this.x + 2.0f, this.y - 8.0f, this.x + this.width - 2.0f, this.y + this.height - 6.0f, GuiSettings.CATEGORY_COLOR);
                }
            } else if (this.last) {
                RenderUtils.drawFixedRect(this.x + 2.0f, this.y - 8.0f, this.x + this.width - 2.0f, this.y + this.height - 4.0f, GuiSettings.CATEGORY_COLOR);
                RenderUtils.drawRoundedRect(this.x + 2.0f, this.y - 14.0f, this.width - 4.0f, 10.0f + ((float) maxHeight - animatedHoverY), 5.0, modules.get(this.count - 1).getModule().isEnabled() && !modules.get(this.count - 1).openedValues ? GuiSettings.CLIENT_COLOR : GuiSettings.CATEGORY_COLOR2);
            } else {
                RenderUtils.drawFixedRect(this.x + 2.0f, this.y - 8.0f, this.x + this.width - 2.0f, this.y + this.height - 2.0f, GuiSettings.CATEGORY_COLOR);
                RenderUtils.drawRoundedRect(this.x + 2.0f, this.y - 14.0f, this.width - 4.0f, 10.0f + ((float) maxHeight - animatedHoverY), 5.0, modules.get(this.count - 1).getModule().isEnabled() && !modules.get(this.count - 1).openedValues ? GuiSettings.CLIENT_COLOR : GuiSettings.CATEGORY_COLOR2);
                RenderUtils.drawRoundedRect(this.x + 2.0f, this.y + 10.0f - ((float) maxHeight - animatedHoverY), this.width - 4.0f, 10.0f + ((float) maxHeight - animatedHoverY), 5.0, modules.get(this.count + 1).getModule().isEnabled() && !this.openedValues ? GuiSettings.CLIENT_COLOR : GuiSettings.CATEGORY_COLOR2);
            }
            this.hovered = true;
        } else {
            this.stopListen();
            this.hovered = false;
        }
        if (this.module.isEnabled() && !this.isHovered(mouseX, mouseY)) {
            if (this.count == 0) {
                RenderUtils.drawRoundedRect(this.x + 2.0f, this.y - 5.0f, this.width - 4.0f, this.height, 5.0, GuiSettings.CLIENT_COLOR);
                RenderUtils.drawFixedRect(this.x + 2.0f, this.y + this.height - 10.0f, this.x + this.width - 2.0f, this.y + this.height - 5.0f, GuiSettings.CLIENT_COLOR);
            } else if (this.last) {
                RenderUtils.drawRoundedRect(this.x + 2.0f, this.y - 5.0f, this.width - 4.0f, this.height, 5.0, GuiSettings.CLIENT_COLOR);
                if (!modules.get(this.count - 1).isHovered(mouseX, mouseY)) {
                    RenderUtils.drawFixedRect(this.x + 2.0f, this.y - 5.0f, this.x + this.width - 2.0f, this.y, GuiSettings.CLIENT_COLOR);
                }
            } else {
                boolean gaming = this.count > 0 && modules.get(this.count - 1).isHovered(mouseX, mouseY) && !modules.get(this.count - 1).openedValues;
                RenderUtils.drawFixedRect(this.x + 2.0f, gaming ? (double) this.y : (double) (this.y - 5.0f), this.x + this.width - 2.0f, this.y + this.height - 5.0f, GuiSettings.CLIENT_COLOR);
            }
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        String moduleText = this.binding ? "Press key..." : this.module.getName();
        if (this.isHovered(mouseX, mouseY) && this.module.isEnabled()) {
            if (this.module.visible) {
                this.font.drawString(moduleText, (float) ((int) (this.x + 4.0f)), this.y, GuiSettings.CLIENT_COLOR);
            } else {
                this.font.drawStringWithShadow(moduleText, (float) ((int) (this.x + 4.0f)), this.y, GuiSettings.CLIENT_COLOR);
            }
        } else if (this.module.visible) {
            this.font.drawString(moduleText, (float) ((int) (this.x + 4.0f)), this.y, this.module.isEnabled() ? GuiSettings.MODULE_ENABLED : GuiSettings.MODULE_DISABLED);
        } else {
            this.font.drawStringWithShadow(moduleText, (float) ((int) (this.x + 4.0f)), this.y, this.module.isEnabled() ? GuiSettings.MODULE_ENABLED : GuiSettings.MODULE_DISABLED);
        }
        if (this.module.isBound()) {
            String bindName = this.module.getKeyName();
            String modeChar = this.module.isHoldMode() ? "H" : "T";
            String bindDisplay = modeChar + " [" + bindName + "]";
            float bindWidth = this.font.getWidth(bindDisplay);
            float bindX = this.x + this.width - bindWidth - 5.0f;
            float modeCharWidth = this.font.getWidth(modeChar);
            float modeX = bindX;
            float bracketX = bindX + modeCharWidth + 2.0f;
            boolean modeHovered = (float) mouseX >= modeX && (float) mouseX <= modeX + modeCharWidth + 2.0f && (float) mouseY >= this.y - 3.0f && (float) mouseY <= this.y + 10.0f;
            Color modeColor = modeHovered ? GuiSettings.CLIENT_COLOR : GuiSettings.MODULE_DISABLED;
            this.font.drawString(modeChar, modeX, this.y, modeColor);
            String bracketText = "[" + bindName + "]";
            this.font.drawString(bracketText, bracketX, this.y, GuiSettings.MODULE_DISABLED);
        }
        if (this.openedValues) {
            for (ValuePanel valuePanel : this.values) {
                valuePanel.render(mouseX, mouseY, partialTicks);
            }
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0) {
            if (this.module.isBound()) {
                String modeChar = this.module.isHoldMode() ? "H" : "T";
                float modeCharWidth = this.font.getWidth(modeChar);
                String bindName = this.module.getKeyName();
                String bindDisplay = modeChar + " [" + bindName + "]";
                float bindWidth = this.font.getWidth(bindDisplay);
                float modeX = this.x + this.width - bindWidth - 5.0f;
                boolean modeHovered = (float) mouseX >= modeX && (float) mouseX <= modeX + modeCharWidth + 2.0f && (float) mouseY >= this.y - 3.0f && (float) mouseY <= this.y + 10.0f;
                if (modeHovered) {
                    this.module.setHoldMode(!this.module.isHoldMode());
                    CGui.saveNew();
                    return;
                }
            }
            this.module.toggle();
        } else if (button == 1) {
            if (ValueManager.getValuesFromModule(this.module).size() > 0) {
                this.openedValues = !this.openedValues;
            }
        } else if (button == 2) {
            if (currentBinding != null) {
                ModulePanel.currentBinding.binding = false;
            }
            currentBinding = this;
            this.binding = true;
        }
    }

    public void keyTyped(char charCode, int keyCode) {
        if (!this.binding) {
            return;
        }
        if (keyCode == 211) {
            this.module.setKey(0);
            if (this.module.isEnabled() && this.module.isHoldMode()) {
                this.module.toggle();
            }
        } else if (keyCode != 1) {
            this.module.setKey(keyCode);
        }
        this.binding = false;
        if (currentBinding == this) {
            currentBinding = null;
        }
        CGui.saveNew();
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int button) {
    }

    @Override
    public boolean isHovered(int mouseX, int mouseY) {
        return (float) mouseX >= this.x && (float) mouseX <= this.x + this.width && (float) mouseY > this.y - 5.0f && (float) mouseY <= this.y + this.height - 5.0f;
    }

    public Module getModule() {
        return this.module;
    }

    public void rePositionValues() {
        this.valueHeight = 0.0f;
        this.x = this.panel.x;
        for (ValuePanel valuePanel : this.values) {
            if (!valuePanel.value.isOpen()) continue;
            double insane = 14.0;
            if (valuePanel.value.getType() == Type.NUMBER) {
                insane = 24.0;
            } else if (valuePanel.value.getType() == Type.POSITION) {
                insane = 100.0;
            } else if (valuePanel.value.getType() == Type.COLOR || valuePanel.value.getType() == Type.COLOR_ALPHA) {
                insane = COLOR_ROW_HEIGHT;
            }
            valuePanel.x = this.x;
            valuePanel.y = this.y + this.valueHeight + 15.0f;
            this.valueHeight = (float) ((double) this.valueHeight + insane);
        }
    }

    private void startListen() {
        this.infoListen = System.currentTimeMillis() - this.lastCheck >= 750L;
    }

    private void stopListen() {
        this.infoListen = false;
        this.lastCheck = System.currentTimeMillis();
    }
}
