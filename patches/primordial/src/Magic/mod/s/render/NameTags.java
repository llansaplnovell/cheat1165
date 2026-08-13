/*
 * Decompiled with CFR 0.152.
 */
package Magic.mod.s.render;

import Magic.ink.event.s.EventRender3D;
import Magic.mod.Category;
import Magic.mod.Module;
import Magic.mod.value.values.BoolValue;
import Magic.mod.value.values.NumberValue;
import Magic.utils.Friend.FriendManager;
import Magic.utils.player.ClientUtils;
import java.awt.Color;
import java.util.ArrayList;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemTool;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import pisi.unitedmeows.eventapi.event.listener.Listener;

public class NameTags
extends Module {
    private final BoolValue modeNew = new BoolValue("Mode New", (Module)this, true, "New nametag style.");
    private final BoolValue adaptiveHeight = new BoolValue("AdaptiveHeight", (Module)this, true, "Adjust height based on distance.");
    private final NumberValue<Double> heightPower = new NumberValue<Double>("HeightPower", this, 0.018, 0.0, 0.08, 0.001, "Power of adaptive height.");
    private final NumberValue<Double> maxExtraHeight = new NumberValue<Double>("MaxExtraHeight", this, 1.15, 0.0, 3.0, 0.1, "Max adaptive height.");
    private final NumberValue<Double> scaleValue = new NumberValue<Double>("Scale", this, 0.1, 0.01, 0.3, 0.01, "Scale of nametags.");
    private final BoolValue armor = new BoolValue("Armor", (Module)this, true, "Show player armor and held item.");
    private final BoolValue head = new BoolValue("Head", (Module)this, true, "Show player head near the name.");
    private final BoolValue enchants = new BoolValue("Enchants", (Module)this, true, "Show enchantments on items.");
    private final BoolValue durability = new BoolValue("Durability", (Module)this, true, "Show item durability.");
    private final BoolValue showRank = new BoolValue("Rank", (Module)this, true, "Show player rank.");
    private final BoolValue fishHookTag = new BoolValue("FishHookNametag", (Module)this, true, "Show the owner name when looking at a fishing hook.");
    private static final Color NEW_BG = new Color(10, 10, 10, 160);
    private static final Color NEW_BG_FADE = new Color(10, 10, 10, 0);
    private static final Color NEW_STRIPE = new Color(4, 4, 4, 255);
    private static final Color NEW_STR_FADE = new Color(4, 4, 4, 0);
    private static final Color WHITE = new Color(255, 255, 255, 255);
    private static final Color ACCENT = new Color(180, 255, 60, 255);
    private static final float NEW_TEXT_SCALE = 1.4f;
    private static final float NEW_HEAD_SIZE = 10.0f;
    private static final float CLASSIC_HEAD_SIZE = 9.0f;
    private static final float ELEMENT_GAP = 5.0f;
    private static final String FORMAT_RESET = "\u00a7r";
    private static final String FORMAT_GRAY = "\u00a77";
    private static final String FORMAT_WHITE = "\u00a7f";
    private static final String FORMAT_RED = "\u00a74";
    private static final String FORMAT_AQUA = "\u00a7b";
    private static final float HOOK_TAG_HEIGHT = 14.0f;
    private static final double HOOK_Y_OFFSET = 0.3;
    private static final double HOOK_REACH = 64.0;
    private static final long HOOK_HOVER_DELAY = 100L;
    private EntityFishHook hoveredHook;
    private long hoverStartTime;
    private final Listener<EventRender3D> onDraw = new Listener<EventRender3D>(event -> {
        if (this.mc.theWorld == null || this.mc.thePlayer == null) {
            return;
        }
        for (EntityPlayer player : ClientUtils.getPlayers()) {
            if (player == this.mc.thePlayer || player.isInvisible()) continue;
            this.renderNametag(player, event.partialTicks());
        }
        this.drawFishHookTag(event.partialTicks());
        GlStateManager.resetColor();
    });

    public NameTags() {
        super("NameTags", 0, Category.Render, "Better name tags with adaptive height.");
    }

    @Override
    public void onDisable() {
        this.hoveredHook = null;
        this.hoverStartTime = 0L;
        super.onDisable();
    }

    /** Tag over the fishing hook the player is looking at, naming whoever cast it. */
    private void drawFishHookTag(float partialTicks) {
        if (!this.fishHookTag.getValue().booleanValue()) {
            this.hoveredHook = null;
            return;
        }
        this.updateHoveredHook(partialTicks);
        EntityFishHook hook = this.hoveredHook;
        if (hook == null || hook.isDead || hook.angler == null) {
            return;
        }
        if (System.currentTimeMillis() - this.hoverStartTime < HOOK_HOVER_DELAY) {
            return;
        }
        this.renderHookNametag(hook, hook.angler, partialTicks);
    }

    private void updateHoveredHook(float partialTicks) {
        Vec3 eyes = this.mc.thePlayer.getPositionEyes(partialTicks);
        Vec3 look = this.mc.thePlayer.getLook(partialTicks);
        Vec3 end = eyes.addVector(look.xCoord * HOOK_REACH, look.yCoord * HOOK_REACH, look.zCoord * HOOK_REACH);
        EntityFishHook closestHook = null;
        double closestDistance = Double.MAX_VALUE;
        for (Entity entity : this.mc.theWorld.loadedEntityList) {
            double distance;
            AxisAlignedBB boundingBox;
            MovingObjectPosition intercept;
            if (!(entity instanceof EntityFishHook)) continue;
            EntityFishHook hook = (EntityFishHook)entity;
            if (hook.isDead || hook.angler == null || hook.angler == this.mc.thePlayer || (intercept = (boundingBox = hook.getEntityBoundingBox().expand(0.3, 0.3, 0.3)).calculateIntercept(eyes, end)) == null || intercept.hitVec == null || !((distance = eyes.distanceTo(intercept.hitVec)) < closestDistance)) continue;
            closestDistance = distance;
            closestHook = hook;
        }
        if (closestHook != this.hoveredHook) {
            this.hoveredHook = closestHook;
            this.hoverStartTime = System.currentTimeMillis();
        }
    }

    private void renderHookNametag(EntityFishHook hook, EntityPlayer owner, float partialTicks) {
        double renderX = hook.lastTickPosX + (hook.posX - hook.lastTickPosX) * (double)partialTicks - this.mc.getRenderManager().renderPosX;
        double renderY = hook.lastTickPosY + (hook.posY - hook.lastTickPosY) * (double)partialTicks - this.mc.getRenderManager().renderPosY + (double)hook.height + HOOK_Y_OFFSET;
        double renderZ = hook.lastTickPosZ + (hook.posZ - hook.lastTickPosZ) * (double)partialTicks - this.mc.getRenderManager().renderPosZ;
        float distance = this.mc.thePlayer.getDistanceToEntity(hook);
        float factor = distance <= 7.0f ? 0.7f : distance * 0.1f;
        float finalScale = 0.02672f * factor;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(renderX, renderY, renderZ);
            GL11.glNormal3f(0.0f, 1.0f, 0.0f);
            GlStateManager.rotate(-this.mc.getRenderManager().playerViewY, 0.0f, 1.0f, 0.0f);
            GlStateManager.rotate(this.mc.getRenderManager().playerViewX, 1.0f, 0.0f, 0.0f);
            GlStateManager.scale(-finalScale, -finalScale, finalScale);
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableTexture2D();
            this.drawHookTag(owner);
        }
        finally {
            GL11.glDepthFunc(515);
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.enableLighting();
            GlStateManager.disableBlend();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.popMatrix();
        }
    }

    private void drawHookTag(EntityPlayer owner) {
        FontRenderer font = this.mc.fontRendererObj;
        String name = owner.getName();
        String text = FORMAT_GRAY + name;
        if (owner.isSneaking()) {
            text = FORMAT_RED + name;
        }
        if (FriendManager.isFriend(name)) {
            String friendName = FriendManager.getName(name);
            text = FORMAT_AQUA + (friendName == null ? name : friendName);
        }
        float textWidth = font.getStringWidth(text);
        float tagWidth = textWidth * 1.4f + 10.0f;
        this.drawNewBackground(-tagWidth / 2.0f, -7.0f, tagWidth, HOOK_TAG_HEIGHT);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.pushMatrix();
        GlStateManager.scale(1.4f, 1.4f, 1.0f);
        font.drawStringWithShadow(text, -textWidth / 2.0f, -4.5f, WHITE.getRGB());
        GlStateManager.popMatrix();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void renderNametag(EntityPlayer player, float partialTicks) {
        double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * (double)partialTicks - this.mc.getRenderManager().renderPosX;
        double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * (double)partialTicks - this.mc.getRenderManager().renderPosY;
        double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * (double)partialTicks - this.mc.getRenderManager().renderPosZ;
        float distance = this.mc.thePlayer.getDistanceToEntity(player);
        double finalHeight = 0.5 + (this.adaptiveHeight.getValue() != false ? this.getAdaptiveNameTagHeight(distance) : 0.0);
        float scaleSetting = ((Double)this.scaleValue.getValue()).floatValue();
        float factor = distance <= 4.0f ? 4.0f * scaleSetting : distance * scaleSetting;
        float finalScale = 0.02672f * factor;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate(x, y + (double)player.height + finalHeight, z);
            GL11.glNormal3f(0.0f, 1.0f, 0.0f);
            GlStateManager.rotate(-this.mc.getRenderManager().playerViewY, 0.0f, 1.0f, 0.0f);
            GlStateManager.rotate(this.mc.getRenderManager().playerViewX, 1.0f, 0.0f, 0.0f);
            GlStateManager.scale(-finalScale, -finalScale, finalScale);
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.disableTexture2D();
            if (this.modeNew.getValue().booleanValue()) {
                this.drawNewStyle(player);
            } else {
                this.drawClassicStyle(player);
            }
        }
        finally {
            GL11.glDepthFunc(515);
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableTexture2D();
            GlStateManager.enableLighting();
            GlStateManager.disableBlend();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.popMatrix();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void drawNewStyle(EntityPlayer player) {
        FontRenderer font = this.mc.fontRendererObj;
        String privilegePrefix = this.getPrivilegePrefix(player);
        boolean hasPrivilege = privilegePrefix != null;
        boolean rankVisible = this.showRank.getValue() != false && hasPrivilege;
        boolean drawHead = this.head.getValue() != false && player instanceof AbstractClientPlayer;
        String rankText = rankVisible ? this.normalizePrivilegePrefix(privilegePrefix) + FORMAT_RESET : "";
        String playerName = this.getColoredPlayerName(player, hasPrivilege);
        String healthText = String.valueOf(Math.round(player.getHealth()));
        Color healthColor = this.getHpColor(player.getHealth());
        float rankWidth = rankVisible ? (float)font.getStringWidth(rankText) * 1.4f : 0.0f;
        float nameWidth = (float)font.getStringWidth(playerName) * 1.4f;
        float healthWidth = (float)font.getStringWidth(healthText) * 1.4f;
        float contentWidth = nameWidth + 5.0f + healthWidth;
        if (rankVisible) {
            contentWidth += rankWidth + 5.0f;
        }
        if (drawHead) {
            contentWidth += 15.0f;
        }
        float horizontalPadding = 5.0f;
        float tagHeight = 14.0f;
        float tagWidth = horizontalPadding + contentWidth + horizontalPadding;
        float tagX = -tagWidth / 2.0f;
        float tagY = -1.0f;
        this.drawNewBackground(tagX, tagY, tagWidth, tagHeight);
        GlStateManager.enableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        float cursorX = tagX + horizontalPadding;
        float rankX = 0.0f;
        float headX = 0.0f;
        if (rankVisible) {
            rankX = cursorX;
            cursorX += rankWidth + 5.0f;
        } else if (drawHead) {
            headX = cursorX;
            cursorX += 15.0f;
        }
        float nameX = cursorX;
        cursorX += nameWidth + 5.0f;
        if (rankVisible && drawHead) {
            headX = cursorX;
            cursorX += 15.0f;
        }
        float healthX = cursorX;
        if (drawHead) {
            float headY = tagY + (tagHeight - 10.0f) / 2.0f;
            this.drawPlayerHead((AbstractClientPlayer)player, headX, headY, 10.0f);
        }
        GL11.glPushMatrix();
        try {
            GL11.glScalef(1.4f, 1.4f, 1.0f);
            float textY = (tagY + (tagHeight - 12.6f) / 2.0f) / 1.4f;
            if (rankVisible) {
                font.drawStringWithShadow(rankText, rankX / 1.4f, textY, WHITE.getRGB());
            }
            font.drawStringWithShadow(playerName, nameX / 1.4f, textY, WHITE.getRGB());
            font.drawStringWithShadow(healthText, healthX / 1.4f, textY, healthColor.getRGB());
        }
        finally {
            GL11.glPopMatrix();
        }
        if (this.armor.getValue().booleanValue() && this.mc.thePlayer.getDistanceSqToEntity(player) <= 1600.0) {
            this.renderArmorThroughWalls(player);
        }
    }

    private void drawClassicStyle(EntityPlayer player) {
        FontRenderer font = this.mc.fontRendererObj;
        String privilegePrefix = this.getPrivilegePrefix(player);
        boolean hasPrivilege = privilegePrefix != null;
        boolean rankVisible = this.showRank.getValue() != false && hasPrivilege;
        boolean drawHead = this.head.getValue() != false && player instanceof AbstractClientPlayer;
        String rankText = rankVisible ? this.normalizePrivilegePrefix(privilegePrefix) + FORMAT_RESET : "";
        String playerName = this.getColoredPlayerName(player, hasPrivilege);
        String healthText = String.valueOf(Math.round(player.getHealth()));
        Color healthColor = this.getHpColor(player.getHealth());
        float rankWidth = rankVisible ? (float)font.getStringWidth(rankText) : 0.0f;
        float nameWidth = font.getStringWidth(playerName);
        float healthWidth = font.getStringWidth(healthText);
        float contentWidth = nameWidth + 5.0f + healthWidth;
        if (rankVisible) {
            contentWidth += rankWidth + 5.0f;
        }
        if (drawHead) {
            contentWidth += 14.0f;
        }
        float tagHeight = 12.0f;
        float tagX = -contentWidth / 2.0f - 2.0f;
        float tagWidth = contentWidth + 4.0f;
        float tagY = -2.0f;
        this.drawSolidRect(tagX, tagY, tagX + tagWidth, tagY + tagHeight, new Color(0, 0, 0, 150));
        GlStateManager.enableTexture2D();
        GlStateManager.disableDepth();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        float cursorX = tagX + 2.0f;
        float rankX = 0.0f;
        float headX = 0.0f;
        if (rankVisible) {
            rankX = cursorX;
            cursorX += rankWidth + 5.0f;
        } else if (drawHead) {
            headX = cursorX;
            cursorX += 14.0f;
        }
        float nameX = cursorX;
        cursorX += nameWidth + 5.0f;
        if (rankVisible && drawHead) {
            headX = cursorX;
            cursorX += 14.0f;
        }
        float healthX = cursorX;
        if (drawHead) {
            float headY = tagY + (tagHeight - 9.0f) / 2.0f;
            this.drawPlayerHead((AbstractClientPlayer)player, headX, headY, 9.0f);
        }
        if (rankVisible) {
            font.drawStringWithShadow(rankText, rankX, 0.0f, WHITE.getRGB());
        }
        font.drawStringWithShadow(playerName, nameX, 0.0f, WHITE.getRGB());
        font.drawStringWithShadow(healthText, healthX, 0.0f, healthColor.getRGB());
        if (this.armor.getValue().booleanValue() && this.mc.thePlayer.getDistanceSqToEntity(player) <= 1600.0) {
            this.renderArmorThroughWalls(player);
        }
    }

    private String getColoredPlayerName(EntityPlayer player, boolean hasPrivilege) {
        String nameColor = hasPrivilege ? FORMAT_WHITE : FORMAT_GRAY;
        String string = nameColor;
        if (player.isSneaking()) {
            nameColor = FORMAT_RED;
        } else if (FriendManager.isFriend(player.getName())) {
            nameColor = FORMAT_AQUA;
        }
        return FORMAT_RESET + nameColor + player.getName() + FORMAT_RESET;
    }

    private String getPrivilegePrefix(EntityPlayer player) {
        if (player == null) {
            return null;
        }
        try {
            String displayName = player.getDisplayName().getFormattedText();
            String playerName = player.getName();
            int nameIndex = displayName.indexOf(playerName);
            if (nameIndex > 0) {
                String prefix = displayName.substring(0, nameIndex);
                if (this.isPrivilegePrefix(prefix = this.normalizePrivilegePrefix(prefix))) {
                    return prefix;
                }
            }
        }
        catch (Exception displayName) {
            // empty catch block
        }
        try {
            String prefix;
            if (this.mc.theWorld == null) {
                return null;
            }
            ScorePlayerTeam team = this.mc.theWorld.getScoreboard().getPlayersTeam(player.getName());
            if (team != null && this.isPrivilegePrefix(prefix = this.normalizePrivilegePrefix(team.getColorPrefix()))) {
                return prefix;
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return null;
    }

    private String normalizePrivilegePrefix(String prefix) {
        boolean changed;
        if (prefix == null) {
            return null;
        }
        String result = prefix;
        do {
            String withoutFormatting;
            changed = false;
            String withoutSpaces = result.replaceAll("\\s+$", "");
            if (!withoutSpaces.equals(result)) {
                result = withoutSpaces;
                changed = true;
            }
            if ((withoutFormatting = result.replaceAll("\u00a7[0-9a-fk-orA-FK-OR]$", "")).equals(result)) continue;
            result = withoutFormatting;
            changed = true;
        } while (changed);
        return result.trim();
    }

    private boolean isPrivilegePrefix(String prefix) {
        if (prefix == null || prefix.trim().isEmpty()) {
            return false;
        }
        String cleanPrefix = prefix.replaceAll("\u00a7[0-9a-fk-orA-FK-OR]", "").trim();
        return !cleanPrefix.isEmpty();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void drawPlayerHead(AbstractClientPlayer player, float x, float y, float size) {
        GlStateManager.pushMatrix();
        try {
            ResourceLocation skin = player.getLocationSkin();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            this.mc.getTextureManager().bindTexture(skin);
            GlStateManager.translate(x, y, 0.0f);
            int drawSize = Math.round(size);
            Gui.drawScaledCustomSizeModalRect(0, 0, 8.0f, 8.0f, 8, 8, drawSize, drawSize, 64.0f, 64.0f);
            Gui.drawScaledCustomSizeModalRect(0, 0, 40.0f, 8.0f, 8, 8, drawSize, drawSize, 64.0f, 64.0f);
        }
        catch (Exception exception) {
        }
        finally {
            GlStateManager.popMatrix();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.enableTexture2D();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
        }
    }

    private void drawNewBackground(float x, float y, float width, float height) {
        float gradientWidth = Math.min(width / 3.0f, 10.0f);
        float centerX = x + gradientWidth;
        float centerWidth = width - gradientWidth * 2.0f;
        this.drawGradientH(x, y, gradientWidth, height, NEW_BG_FADE, NEW_BG);
        this.drawSolidRect(centerX, y, centerX + centerWidth, y + height, NEW_BG);
        this.drawGradientH(centerX + centerWidth, y, gradientWidth, height, NEW_BG, NEW_BG_FADE);
        this.drawGradientH(x, y, gradientWidth, 0.5f, NEW_STR_FADE, NEW_STRIPE);
        this.drawSolidRect(centerX, y, centerX + centerWidth, y + 0.5f, NEW_STRIPE);
        this.drawGradientH(centerX + centerWidth, y, gradientWidth, 0.5f, NEW_STRIPE, NEW_STR_FADE);
        float bottomY = y + height - 0.5f;
        this.drawGradientH(x, bottomY, gradientWidth, 0.5f, NEW_STR_FADE, NEW_STRIPE);
        this.drawSolidRect(centerX, bottomY, centerX + centerWidth, bottomY + 0.5f, NEW_STRIPE);
        this.drawGradientH(centerX + centerWidth, bottomY, gradientWidth, 0.5f, NEW_STRIPE, NEW_STR_FADE);
    }

    private void drawSolidRect(float x1, float y1, float x2, float y2, Color color) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GL11.glColor4f((float)color.getRed() / 255.0f, (float)color.getGreen() / 255.0f, (float)color.getBlue() / 255.0f, (float)color.getAlpha() / 255.0f);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(7, DefaultVertexFormats.POSITION);
        renderer.pos(x1, y2, 0.0).endVertex();
        renderer.pos(x2, y2, 0.0).endVertex();
        renderer.pos(x2, y1, 0.0).endVertex();
        renderer.pos(x1, y1, 0.0).endVertex();
        tessellator.draw();
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void drawGradientH(float x, float y, float width, float height, Color left, Color right) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer renderer = tessellator.getWorldRenderer();
        renderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        renderer.pos(x + width, y, 0.0).color((float)right.getRed() / 255.0f, (float)right.getGreen() / 255.0f, (float)right.getBlue() / 255.0f, (float)right.getAlpha() / 255.0f).endVertex();
        renderer.pos(x, y, 0.0).color((float)left.getRed() / 255.0f, (float)left.getGreen() / 255.0f, (float)left.getBlue() / 255.0f, (float)left.getAlpha() / 255.0f).endVertex();
        renderer.pos(x, y + height, 0.0).color((float)left.getRed() / 255.0f, (float)left.getGreen() / 255.0f, (float)left.getBlue() / 255.0f, (float)left.getAlpha() / 255.0f).endVertex();
        renderer.pos(x + width, y + height, 0.0).color((float)right.getRed() / 255.0f, (float)right.getGreen() / 255.0f, (float)right.getBlue() / 255.0f, (float)right.getAlpha() / 255.0f).endVertex();
        tessellator.draw();
        GlStateManager.shadeModel(7424);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void renderArmorThroughWalls(EntityPlayer player) {
        int itemCount = 0;
        if (player.getHeldItem() != null) {
            ++itemCount;
        }
        for (ItemStack stack : player.inventory.armorInventory) {
            if (stack == null) continue;
            ++itemCount;
        }
        if (itemCount == 0) {
            return;
        }
        int xOffset = -(itemCount * 16) / 2;
        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableTexture2D();
            GlStateManager.enableDepth();
            GL11.glDepthFunc(519);
            GlStateManager.depthMask(false);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            if (player.getHeldItem() != null) {
                this.renderItemStack(player.getHeldItem(), xOffset, -20);
                xOffset += 16;
            }
            for (int slot = 3; slot >= 0; --slot) {
                ItemStack stack = player.inventory.armorInventory[slot];
                if (stack == null) continue;
                this.renderItemStack(stack, xOffset, -20);
                xOffset += 16;
            }
        }
        finally {
            this.mc.getRenderItem().zLevel = 0.0f;
            RenderHelper.disableStandardItemLighting();
            GL11.glDepthFunc(515);
            GlStateManager.depthMask(false);
            GlStateManager.disableDepth();
            GlStateManager.disableLighting();
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.popMatrix();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void renderItemStack(ItemStack stack, int x, int y) {
        GlStateManager.pushMatrix();
        try {
            GlStateManager.enableTexture2D();
            GlStateManager.enableDepth();
            GlStateManager.enableRescaleNormal();
            GL11.glDepthFunc(519);
            GlStateManager.depthMask(false);
            RenderHelper.enableGUIStandardItemLighting();
            this.mc.getRenderItem().zLevel = -150.0f;
            this.mc.getRenderItem().renderItemAndEffectIntoGUI(stack, x, y);
            this.mc.getRenderItem().zLevel = 0.0f;
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableRescaleNormal();
            if (this.enchants.getValue().booleanValue() || this.durability.getValue().booleanValue()) {
                this.renderEnchantText(stack, x, y);
            }
        }
        finally {
            this.mc.getRenderItem().zLevel = 0.0f;
            RenderHelper.disableStandardItemLighting();
            GlStateManager.disableRescaleNormal();
            GlStateManager.enableTexture2D();
            GlStateManager.enableDepth();
            GL11.glDepthFunc(519);
            GlStateManager.depthMask(false);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.popMatrix();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void renderEnchantText(ItemStack stack, int x, int y) {
        GlStateManager.pushMatrix();
        try {
            GlStateManager.disableLighting();
            GlStateManager.disableDepth();
            GlStateManager.depthMask(false);
            GlStateManager.enableBlend();
            GlStateManager.enableTexture2D();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.scale(0.5f, 0.5f, 0.5f);
            FontRenderer font = this.mc.fontRendererObj;
            int textX = x * 2;
            ArrayList<String> enchantments = new ArrayList<String>();
            if (this.enchants.getValue().booleanValue()) {
                this.collectEnchantments(stack, enchantments);
            }
            int bottomY = (y + 16) * 2 - 28;
            for (int index = 0; index < enchantments.size(); ++index) {
                int textY = bottomY - index * 7;
                font.drawStringWithShadow("\u00a7l" + enchantments.get(index), textX, textY, 0xFFFFFF);
            }
            if (this.durability.getValue().booleanValue() && stack.isItemStackDamageable()) {
                int maxDamage = stack.getMaxDamage();
                int remaining = Math.max(0, maxDamage - stack.getItemDamage());
                float durabilityProgress = maxDamage <= 0 ? 1.0f : Math.max(0.0f, Math.min((float)remaining / (float)maxDamage, 1.0f));
                Color durabilityColor = this.getHpColor(durabilityProgress * 20.0f);
                int barLength = Math.round(durabilityProgress * 13.0f);
                float barX = (float)(x + 2) * 2.0f;
                float barY = (float)(y + 15) * 2.0f;
                this.drawSolidRect(barX, barY, barX + 26.0f, barY + 4.0f, new Color(0, 0, 0, 255));
                if (barLength > 0) {
                    this.drawSolidRect(barX, barY, barX + (float)(barLength * 2), barY + 2.0f, durabilityColor);
                }
                GlStateManager.enableTexture2D();
                String durabilityText = "\u00a7l" + remaining;
                int durabilityY = (y + 16) * 2 - 16;
                font.drawStringWithShadow(durabilityText, textX, durabilityY, durabilityColor.getRGB());
            }
        }
        finally {
            GlStateManager.enableTexture2D();
            GlStateManager.enableBlend();
            GlStateManager.enableDepth();
            GL11.glDepthFunc(519);
            GlStateManager.depthMask(false);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            GlStateManager.popMatrix();
        }
    }

    private void collectEnchantments(ItemStack stack, ArrayList<String> result) {
        if (stack.getItem() instanceof ItemArmor) {
            int protection = EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, stack);
            int thorns = EnchantmentHelper.getEnchantmentLevel(Enchantment.thorns.effectId, stack);
            int unbreaking = EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, stack);
            if (protection > 0) {
                result.add("p" + protection);
            }
            if (thorns > 0) {
                result.add("t" + thorns);
            }
            if (unbreaking > 0) {
                result.add("u" + unbreaking);
            }
            return;
        }
        if (stack.getItem() instanceof ItemSword) {
            int sharpness = EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, stack);
            int knockback = EnchantmentHelper.getEnchantmentLevel(Enchantment.knockback.effectId, stack);
            int fireAspect = EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, stack);
            int unbreaking = EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, stack);
            if (sharpness > 0) {
                result.add("s" + sharpness);
            }
            if (knockback > 0) {
                result.add("k" + knockback);
            }
            if (fireAspect > 0) {
                result.add("f" + fireAspect);
            }
            if (unbreaking > 0) {
                result.add("u" + unbreaking);
            }
            return;
        }
        if (stack.getItem() instanceof ItemBow) {
            int power = EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, stack);
            int punch = EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId, stack);
            int flame = EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, stack);
            int unbreaking = EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, stack);
            if (power > 0) {
                result.add("po" + power);
            }
            if (punch > 0) {
                result.add("kn" + punch);
            }
            if (flame > 0) {
                result.add("fl" + flame);
            }
            if (unbreaking > 0) {
                result.add("u" + unbreaking);
            }
            return;
        }
        if (stack.getItem() instanceof ItemTool) {
            int efficiency = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, stack);
            int unbreaking = EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, stack);
            int fortune = EnchantmentHelper.getEnchantmentLevel(Enchantment.fortune.effectId, stack);
            int silkTouch = EnchantmentHelper.getEnchantmentLevel(Enchantment.silkTouch.effectId, stack);
            if (efficiency > 0) {
                result.add("e" + efficiency);
            }
            if (unbreaking > 0) {
                result.add("u" + unbreaking);
            }
            if (fortune > 0) {
                result.add("fo" + fortune);
            }
            if (silkTouch > 0) {
                result.add("st" + silkTouch);
            }
        }
    }

    private Color getHpColor(float health) {
        float progress = Math.max(0.0f, Math.min(health / 20.0f, 1.0f));
        return new Color(this.lerp(255, ACCENT.getRed(), progress), this.lerp(50, ACCENT.getGreen(), progress), this.lerp(50, ACCENT.getBlue(), progress), 255);
    }

    private double getAdaptiveNameTagHeight(float distance) {
        double extraHeight = (double)distance * (Double)this.heightPower.getValue();
        return Math.max(0.0, Math.min(extraHeight, (Double)this.maxExtraHeight.getValue()));
    }

    private int lerp(int start, int end, float progress) {
        return (int)((float)start + (float)(end - start) * progress);
    }
}
