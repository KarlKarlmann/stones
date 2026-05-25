package net.stones.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;
import net.stones.StonesMod;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Ein spezieller Potion-Effekt für Cooldowns.
 * Nutzt Layered-Rendering für Icons und zeigt im Inventar Text-Informationen an.
 * Wurde von Client-Imports bereinigt, um Server-Abstürze zu verhindern.
 */
public class StonesCooldownEffect extends MobEffect {

    private final ResourceLocation skillIcon;
    private final String runeName; // Der interne Name der Rune für die Textanzeige
    private static final ResourceLocation CLOCK_OVERLAY = new ResourceLocation(StonesMod.MODID, "textures/gui/cooldown_overlay.png");

    public StonesCooldownEffect(String iconPath, String id) {
        super(MobEffectCategory.NEUTRAL, 0xFFFFFF);
        this.skillIcon = new ResourceLocation(iconPath);
        this.runeName = formatName(id);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return false;
    }

    @Override
    public List<ItemStack> getCurativeItems() {
        return new ArrayList<>(); // Milch-Resistenz
    }

    /**
     * Formatiert die ID (z.B. "master_prospector") in einen lesbaren Namen ("Master Prospector").
     */
    private String formatName(String id) {
        String name = id.replace("cooldown_", "");
        if (name.contains(":")) name = name.substring(name.indexOf(":") + 1);
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    @Override
    public void initializeClient(Consumer<IClientMobEffectExtensions> consumer) {
        consumer.accept(new IClientMobEffectExtensions() {
            
            @Override
            public boolean renderGuiIcon(MobEffectInstance instance, net.minecraft.client.gui.Gui gui, net.minecraft.client.gui.GuiGraphics guiGraphics, int x, int y, float z, float alpha) {
                renderCombinedIcon(guiGraphics, x + 3, y + 3, alpha);
                return true;
            }

            @Override
            public boolean renderInventoryIcon(MobEffectInstance instance, net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen<?> screen, net.minecraft.client.gui.GuiGraphics guiGraphics, int x, int y, int blitOffset) {
                renderCombinedIcon(guiGraphics, x + 6, y + 7, 1.0f);
                return true;
            }

            /**
             * Zeigt im Inventar-Screen den Namen des Skills und "Cooldown" an.
             */
            @Override
            public boolean renderInventoryText(MobEffectInstance instance, net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen<?> screen, net.minecraft.client.gui.GuiGraphics guiGraphics, int x, int y, int blitOffset) {
                // Den Namen der Rune fett in Gold zeichnen
                guiGraphics.drawString(screen.getMinecraft().font, "§6" + runeName, x+30, y+5, 0xFFFFFF);
                
                // Darunter "Abklingzeit" in Grau
                int seconds = instance.getDuration() / 20;
                String timeStr = "§7Abklingzeit: §f" + seconds + "s";
                guiGraphics.drawString(screen.getMinecraft().font, timeStr, x+30, y + 15, 0xFFFFFF);
                
                return true; // Verhindert das Zeichnen des Standard-Potion-Namens
            }

            private void renderCombinedIcon(net.minecraft.client.gui.GuiGraphics guiGraphics, int x, int y, float alpha) {
                com.mojang.blaze3d.systems.RenderSystem.enableBlend();
                com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
                
                // 1. Skill Icon
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
                guiGraphics.blit(skillIcon, x, y, 0, 0, 18, 18, 18, 18);
                
                // 2. Overlay
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 0.6F * alpha);
                guiGraphics.blit(CLOCK_OVERLAY, x, y, 0, 0, 18, 18, 18, 18);
                
                com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
        });
    }
}