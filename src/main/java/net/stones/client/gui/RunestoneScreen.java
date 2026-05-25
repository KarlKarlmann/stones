package net.stones.client.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.items.IItemHandler;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec2;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.ItemStackHandler;
import net.stones.gui.RunestoneMenu; 
import net.stones.gui.layout.ShrineLayout;
import net.stones.data.ShrineInstance.SlotConfig;
import net.stones.data.ShrineInstance.SlotType;
import net.stones.enchantment.RuneEnchantment;
import net.stones.enchantment.AmplifyEnchantment;
import net.stones.item.ClusterJewelItem;
import net.stones.item.StoneItem;
import net.stones.logic.RuneCalculator;
import net.stones.util.ClusterTooltipHandler;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.*;

public class RunestoneScreen extends AbstractContainerScreen<RunestoneMenu> {

    // Texturen
    private static final ResourceLocation BG_NEBULA = new ResourceLocation("stones", "textures/gui/shrine_nebula.png");
    private static final ResourceLocation SLOT_MINOR = new ResourceLocation("stones", "textures/gui/slot_minor.png");
    private static final ResourceLocation SLOT_MAJOR = new ResourceLocation("stones", "textures/gui/slot_major.png");
    private static final ResourceLocation SLOT_MILESTONE = new ResourceLocation("stones", "textures/gui/slot_milestone.png");
    private static final ResourceLocation GLOW_ATLAS = new ResourceLocation("stones", "textures/gui/particle_glow.png");
    
    private static final ResourceLocation XP_BAR_LOCATION = new ResourceLocation("textures/gui/icons.png");

    private static final boolean AUTO_FIT_TO_SCREEN = true;
    private static final float DEFAULT_ZOOM = 1.0f;
    private static final int INVENTORY_TOP_Y = 140; 
    
    private static final int STAR_COUNT = 3000; 
    private static final float STAR_FIELD_RADIUS = 1200.0f; 

    private List<Vec2> cachedPositions;
    private List<Integer> sortedIndices;
    private double scrollX = 0;
    private double scrollY = 0;
    private float zoom = 1.0f;
    private boolean isDragging = false;
    
    private int lastHoveredVisualIndex = -1;
    private final Set<Integer> activeResonanceIndices = new HashSet<>();

    private record Star(float x, float y, float size, float blinkSpeed, float blinkPhase) {}
    private final List<Star> starField = new ArrayList<>();

    private DynamicTexture lightNoiseTexture;
    private ResourceLocation lightNoiseLocation;

    private SimpleSoundInstance menuMusic;

    public RunestoneScreen(RunestoneMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 256;
        this.imageHeight = 256;
    }

    @Override
    protected void init() {
        super.init();
        
        if (this.minecraft != null) {
            this.minecraft.getMusicManager().stopPlaying();
        }

        int runeCount = this.menu.layoutData.size();
        this.cachedPositions = ShrineLayout.generateSpiralPositions(runeCount);
        
        this.sortedIndices = new ArrayList<>(runeCount);
        for (int i = 0; i < runeCount; i++) this.sortedIndices.add(i);
        
        this.sortedIndices.sort((idxA, idxB) -> {
            SlotConfig a = this.menu.layoutData.get(idxA);
            SlotConfig b = this.menu.layoutData.get(idxB);
            int levelComp = Integer.compare(a.requiredLevel, b.requiredLevel);
            if (levelComp != 0) return levelComp;
            return a.type.compareTo(b.type);
        });

        this.starField.clear();
        RandomSource rand = RandomSource.create();
        for (int i = 0; i < STAR_COUNT; i++) {
            double r = STAR_FIELD_RADIUS * Math.sqrt(rand.nextFloat());
            double theta = rand.nextFloat() * 2 * Math.PI;
            float x = (float) (r * Math.cos(theta));
            float y = (float) (r * Math.sin(theta));
            
            float size = rand.nextFloat() < 0.99 ? (0.3f + rand.nextFloat() * 0.4f) : 1.2f;
            float speed = 0.001f + rand.nextFloat() * 0.002f; 
            float phase = rand.nextFloat() * 100.0f; 
            starField.add(new Star(x, y, size, speed, phase));
        }

        if (this.lightNoiseTexture == null) {
            NativeImage noiseImg = new NativeImage(64, 64, false);
            Random r = new Random();
            for (int x = 0; x < 64; x++) {
                for (int y = 0; y < 64; y++) {
                    int val = 100 + r.nextInt(156); 
                    int color = (0xFF << 24) | (val << 16) | (val << 8) | val;
                    noiseImg.setPixelRGBA(x, y, color);
                }
            }
            this.lightNoiseTexture = new DynamicTexture(noiseImg);
            this.lightNoiseLocation = this.minecraft.getTextureManager().register("shrine_light_noise", this.lightNoiseTexture);
        }

        if (this.menuMusic == null) {
            this.minecraft.getSoundManager().stop(null, SoundSource.MUSIC);
            this.menuMusic = new SimpleSoundInstance(
                new ResourceLocation("stones", "music.shrine_ambient"),
                SoundSource.MUSIC,
                1.0f, 1.0f,
                RandomSource.create(),
                true, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true 
            );
            this.minecraft.getSoundManager().play(this.menuMusic);
        }

        this.scrollX = 0;
        this.scrollY = 0;
        this.lastHoveredVisualIndex = -1;

        if (AUTO_FIT_TO_SCREEN && runeCount > 0) {
            double maxRadius = 24.0 * Math.sqrt(runeCount);
            float availableHeight = INVENTORY_TOP_Y - 40; 
            float availableWidth = this.imageWidth - 40; 
            float minDimension = Math.min(availableWidth, availableHeight);
            
            float requiredDiameter = (float)(2 * maxRadius); 
            this.zoom = minDimension / requiredDiameter;
            this.zoom = Mth.clamp(this.zoom, 0.4f, 1.3f);

            double pixelOffset = (INVENTORY_TOP_Y / 2.0) - (this.imageHeight / 2.0);
            this.scrollY = pixelOffset / this.zoom;
        } else {
            this.zoom = DEFAULT_ZOOM;
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (this.lightNoiseTexture != null) {
            this.lightNoiseTexture.close();
        }
        if (this.menuMusic != null) {
            this.minecraft.getSoundManager().stop(this.menuMusic);
            this.menuMusic = null;
        }
    }
    
    @Override
    public void containerTick() {
        super.containerTick();
        if (this.minecraft != null && this.minecraft.player != null && this.minecraft.player.tickCount % 20 == 0) {
             this.minecraft.getMusicManager().stopPlaying();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        
        this.hoveredSlot = null;
        this.updateResonance(mouseX, mouseY);

        this.renderBackground(guiGraphics); 

        // 1. Runen Slots
        this.renderRuneSlotsComplete(guiGraphics, mouseX, mouseY, partialTick);

        // 2. Player Inventory
        this.renderPlayerInventory(guiGraphics, mouseX, mouseY);
        
        // 3. Experience Bar
        this.renderExperienceBar(guiGraphics);

        // 4. Tooltips
        this.renderCustomTooltip(guiGraphics, mouseX, mouseY);

        // 5. Carried Item
        ItemStack carried = this.menu.getCarried();
        if (!carried.isEmpty()) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0F, 0.0F, 232.0F);
            guiGraphics.renderItem(carried, mouseX - 8, mouseY - 8);
            guiGraphics.renderItemDecorations(this.font, carried, mouseX - 8, mouseY - 8);
            guiGraphics.pose().popPose();
        }

        // 6. Stat Panel
        this.renderStatsPanel(guiGraphics);
    }

    private void renderExperienceBar(GuiGraphics guiGraphics) {
        if (this.minecraft.player == null) return;

        int experience = this.minecraft.player.experienceLevel;
        float progress = this.minecraft.player.experienceProgress;
        
        int barX = (this.width - 182) / 2;
        int barY = this.topPos + 130;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 200.0F); 

        RenderSystem.setShaderTexture(0, XP_BAR_LOCATION);
        
        guiGraphics.blit(XP_BAR_LOCATION, barX, barY, 0, 64, 182, 5);
        
        if (progress > 0) {
            int progressWidth = (int)(progress * 182.0F);
            guiGraphics.blit(XP_BAR_LOCATION, barX, barY, 0, 69, progressWidth, 5);
        }

        if (experience > 0) {
            String levelStr = String.valueOf(experience);
            int strX = (this.width - this.font.width(levelStr)) / 2;
            int strY = barY - 6; 
            
            guiGraphics.drawString(this.font, levelStr, strX + 1, strY, 0, false);
            guiGraphics.drawString(this.font, levelStr, strX - 1, strY, 0, false);
            guiGraphics.drawString(this.font, levelStr, strX, strY + 1, 0, false);
            guiGraphics.drawString(this.font, levelStr, strX, strY - 1, 0, false);
            guiGraphics.drawString(this.font, levelStr, strX, strY, 8453920, false);
        }

        guiGraphics.pose().popPose();
    }

    private void renderStatsPanel(GuiGraphics gui) {
        int playerLevel = this.minecraft.player.experienceLevel;
        
        Map<Attribute, Double> totals = new HashMap<>();
        Map<Attribute, Boolean> isPercentage = new HashMap<>();
        List<Component> activeMilestones = new ArrayList<>();
        
        // Wir erstellen einen temporären ItemHandler-Wrapper um die Slots des Menüs
        // Damit können wir die zentrale Logik im RuneCalculator nutzen
        IItemHandler menuWrapper = new IItemHandler() {
            @Override public int getSlots() { return menu.slots.size(); }
            @Override public ItemStack getStackInSlot(int slot) { 
                // Wir müssen aufpassen: menu.slots enthält auch Player Inventory!
                // Das Layout Data definiert, welche Slots zum Schrein gehören.
                if (slot < menu.layoutData.size()) return menu.slots.get(slot).getItem();
                return ItemStack.EMPTY;
            }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
            @Override public int getSlotLimit(int slot) { return 64; }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }
        };

        // ZENTRALE LOGIK AUFRUFEN
        RuneCalculator.collectActiveRunes(menuWrapper, this.menu.layoutData, playerLevel, 
            (runeEnch, runeLevel, socketLevel, mult, mainSlot, subSlot) -> {
                
                // FALL A: Attribut
                if (runeEnch.targetAttribute != null) {
                    try {
                        double val = RuneCalculator.calculateAttributeBonus(runeEnch, runeLevel, playerLevel, socketLevel, mult);
                        totals.put(runeEnch.targetAttribute, totals.getOrDefault(runeEnch.targetAttribute, 0.0) + val);
                        
                        if (runeEnch.operation != AttributeModifier.Operation.ADDITION) {
                            isPercentage.put(runeEnch.targetAttribute, true);
                        }
                    } catch (Exception ignored) {}
                }
                // FALL B: Milestone
                else {
                    activeMilestones.add(runeEnch.getFullname(runeLevel));
                }
            }
        );
        
        if (totals.isEmpty() && activeMilestones.isEmpty()) return;

        int x = this.leftPos + this.imageWidth + 6; 
        int y = this.topPos + 10;
        
        gui.drawString(this.font, Component.translatable("tooltip.stones.active_effects").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), x, y, 0xFFFFFF);
        y += 12;
        
        // 1. Attribute rendern
        for (Map.Entry<Attribute, Double> entry : totals.entrySet()) {
            double val = entry.getValue();
            boolean percent = isPercentage.getOrDefault(entry.getKey(), false);
            
            String valStr;
            if (percent) valStr = String.format("+%.0f%%", val * 100);
            else valStr = String.format("+%.1f", val);
            
            Component name = Component.translatable(entry.getKey().getDescriptionId());
            
            gui.drawString(this.font, "§a" + valStr + " §7" + name.getString(), x, y, 0xFFFFFF);
            y += 10;
        }
        
        // 2. Milestones rendern
        if (!activeMilestones.isEmpty()) {
            if (!totals.isEmpty()) y += 4;
            for (Component milestoneName : activeMilestones) {
                gui.drawString(this.font, "§d✦ " + milestoneName.getString(), x, y, 0xFFFFFF);
                y += 10;
            }
        }
    }

    // --- HELPER FÜR STATS (Rekursiv) ---
    
    private void processClusterForStats(ItemStack stack, int playerLevel, int socketLevel, Map<Attribute, Double> totals, Map<Attribute, Boolean> isPercentage, List<Component> activeMilestones) {
        // Lese Cluster Inventar (NBT oder Capability)
        // Im GUI haben wir direkten Zugriff auf den Client-Stack, der hoffentlich gesynct ist (ClusterInventory NBT)
        if (stack.hasTag() && stack.getTag().contains("ClusterInventory")) {
            CompoundTag invTag = stack.getTag().getCompound("ClusterInventory");
            ItemStackHandler handler = new ItemStackHandler();
            handler.deserializeNBT(invTag);
            
            // Check Cluster Level Requirement
            int clusterReq = getClusterRequirement(handler);
            if (playerLevel < clusterReq) return;

            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack sub = handler.getStackInSlot(i);
                if (!sub.isEmpty()) {
                    processRuneForStats(sub, playerLevel, socketLevel, totals, isPercentage, activeMilestones);
                }
            }
        }
    }
    
    private int getClusterRequirement(ItemStackHandler handler) {
        int maxLvl = 0;
        int count = 0;
        for(int i=0; i < handler.getSlots(); i++) {
            ItemStack s = handler.getStackInSlot(i);
            if(!s.isEmpty()) {
                count++;
                maxLvl = Math.max(maxLvl, RuneCalculator.getRequiredLevel(s));
            }
        }
        return maxLvl + (count * 2);
    }

    private void processRuneForStats(ItemStack stack, int playerLevel, int socketLevel, Map<Attribute, Double> totals, Map<Attribute, Boolean> isPercentage, List<Component> activeMilestones) {
        Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
        double mult = RuneCalculator.getAmplifyMultiplier(stack);

        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            if (entry.getKey() instanceof RuneEnchantment runeEnch) {
                if (runeEnch.targetAttribute != null) {
                    try {
                        double val = RuneCalculator.calculateAttributeBonus(runeEnch, entry.getValue(), playerLevel, socketLevel, mult);
                        if (val != 0) {
                            totals.put(runeEnch.targetAttribute, totals.getOrDefault(runeEnch.targetAttribute, 0.0) + val);
                            if (runeEnch.operation != AttributeModifier.Operation.ADDITION) {
                                isPercentage.put(runeEnch.targetAttribute, true);
                            }
                        }
                    } catch (Exception ignored) {}
                } else {
                    activeMilestones.add(runeEnch.getFullname(entry.getValue()));
                }
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
    }

    private void renderRuneSlotsComplete(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        renderCosmos(gui, mouseX, mouseY);
        
        PoseStack pose = gui.pose();
        pose.pushPose();
        
        float centerX = this.width / 2.0f;
        float centerY = this.height / 2.0f;
        
        pose.translate(centerX, centerY, 0);
        pose.scale(zoom, zoom, 1.0f);
        pose.translate(scrollX, scrollY, 0);

        int runeCount = this.menu.layoutData.size();
        long time = System.currentTimeMillis();
        double timeLoop = (time % 10000000L); 

        double vx = (mouseX - centerX) / zoom - scrollX;
        double vy = (mouseY - centerY) / zoom - scrollY;

        for (int i = 0; i < runeCount; ++i) {
            int logicIndex = sortedIndices.get(i); 
            Vec2 pos = cachedPositions.get(i); 
            Slot slot = this.menu.slots.get(logicIndex);
            SlotConfig cfg = this.menu.layoutData.get(logicIndex);
            
            boolean isHovered = (vx >= pos.x - 9 && vx <= pos.x + 9 && vy >= pos.y - 9 && vy <= pos.y + 9);
            if (isHovered) {
                this.hoveredSlot = slot;
            }

            boolean isResonating = activeResonanceIndices.contains(i);
            boolean isMilestone = (cfg.type == SlotType.MILESTONE);

            ResourceLocation tex = SLOT_MINOR;
            float baseScale = 1.0f;
            switch (cfg.type) {
                case MINOR -> { tex = SLOT_MINOR; baseScale = 1.0f; }
                case MAJOR -> { tex = SLOT_MAJOR; baseScale = 1.2f; }
                case MILESTONE -> { tex = SLOT_MILESTONE; baseScale = 1.5f; }
            }

            float baseBreath = (float) Math.sin(timeLoop * 0.002 + logicIndex * 0.1); 
            float baseScaleMod = baseBreath * 0.02f; 
            double wavePhase = timeLoop * 0.0006 - (logicIndex * 0.15);
            double flareSine = Math.sin(wavePhase); 
            float flare = (float) Math.pow((flareSine + 1.0) / 2.0, 60.0); 
            float flareScaleMod = flare * 0.25f; 

            float twinkleScale = 1.0f + baseScaleMod + flareScaleMod;
            float resonanceScale = isHovered ? 1.15f : (isResonating ? 1.05f : 1.0f);
            float finalScale = baseScale * resonanceScale * twinkleScale;

            pose.pushPose();
            pose.translate(pos.x, pos.y, 0.2f); 
            pose.scale(finalScale, finalScale, 1.0f);

            if (isHovered || isResonating || slot.hasItem() || isMilestone) {
                float glowIntensity = isMilestone ? 1.5f : 1.0f;
                if (isHovered) glowIntensity += 0.8f;
                if (isResonating) glowIntensity += 0.4f;
                if (flare > 0.1f) glowIntensity += flare * 1.0f;

                float r = 0.0f, g = 0.8f, b = 1.0f;
                if (isHovered || isResonating) {
                    float hue = 0.5f + 0.15f * (float)Math.sin(time * 0.001); 
                    int rgb = Color.HSBtoRGB(hue, 0.8f, 1.0f);
                    r = ((rgb >> 16) & 0xFF) / 255f;
                    g = ((rgb >> 8) & 0xFF) / 255f;
                    b = (rgb & 0xFF) / 255f;
                }
                renderGlow(gui, 0, 0, glowIntensity, r, g, b);
            }

            gui.blit(tex, -9, -9, 0, 0, 18, 18, 18, 18);
            
            if (slot.hasItem()) {
                ItemStack stack = slot.getItem();
                gui.renderItem(stack, -8, -8);
                gui.renderItemDecorations(this.font, stack, -8, -8);
            }

            if (isHovered) {
                RenderSystem.disableDepthTest();
                gui.fill(-8, -8, 8, 8, 0x80FFFFFF);
                RenderSystem.enableDepthTest();
            }

            pose.popPose();
        }
        pose.popPose();
    }
    
    private void renderPlayerInventory(GuiGraphics gui, int mx, int my) {
        int start = this.menu.slots.size() - 36;
        for(int i = start; i < this.menu.slots.size(); ++i) {
            Slot slot = this.menu.slots.get(i);
            int px = this.leftPos + slot.x;
            int py = this.topPos + slot.y;
            
            gui.fill(px, py, px + 16, py + 16, 0x40000000); 
            gui.renderItem(slot.getItem(), px, py);
            gui.renderItemDecorations(this.font, slot.getItem(), px, py);
            
            if (isHovering(slot.x, slot.y, 16, 16, mx, my)) {
                this.renderSlotHighlight(gui, px, py, 0);
                this.hoveredSlot = slot; 
            }
        }
    }
    
    private void renderCosmos(GuiGraphics gui, int mx, int my) {
        PoseStack pose = gui.pose();
        float cx = this.width / 2.0f;
        float cy = this.height / 2.0f;
        long time = System.currentTimeMillis();
        
        pose.pushPose();
        float nebulaAngle = (time % 2400000L) / 2400000.0f * 360.0f;
        pose.translate(cx, cy, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(nebulaAngle));
        pose.translate(-cx, -cy, 0);
        
        float speed = 0.02f;
        int offX = (int)((mx - width/2) * speed);
        int offY = (int)((my - height/2) * speed);
        int bgSize = Math.max(this.width, this.height) * 2;
        int bgX = (this.width - bgSize) / 2;
        int bgY = (this.height - bgSize) / 2;
        
        RenderSystem.setShaderColor(0.6f, 0.4f, 0.9f, 1.0f);
        gui.blit(BG_NEBULA, bgX - offX, bgY - offY, 0, 0, bgSize, bgSize, 256, 256);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        pose.popPose();
        
        pose.pushPose();
        float starAngle = (time % 2400000L) / 2400000.0f * 360.0f * 1.5f;
        pose.translate(cx, cy, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(starAngle));
        pose.translate(-cx, -cy, 0); 
        float starSpeed = 0.05f;
        pose.translate(-(mx - width/2) * starSpeed, -(my - height/2) * starSpeed, 0);
        renderStarField(gui, time);
        pose.popPose();
    }
    
    private void renderStarField(GuiGraphics gui, long time) {
        if (this.lightNoiseLocation == null) return;
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        Matrix4f mat = gui.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, this.lightNoiseLocation);
        double noiseTime = time * 0.0002;
        float uGlobalOffset = (float)Math.sin(noiseTime);
        float vGlobalOffset = (float)Math.cos(noiseTime * 0.7);
        float hue = (time * 0.00005f) % 1.0f;
        int rgb = Color.HSBtoRGB(hue, 0.15f, 1.0f); 
        float red = ((rgb >> 16) & 0xFF) / 255f;
        float green = ((rgb >> 8) & 0xFF) / 255f;
        float blue = (rgb & 0xFF) / 255f;
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (Star star : starField) {
            float slowBreath = Mth.sin(time * star.blinkSpeed + star.blinkPhase);
            float brightness = 0.6f + 0.4f * slowBreath;
            float u = (star.x / STAR_FIELD_RADIUS) + uGlobalOffset;
            float v = (star.y / STAR_FIELD_RADIUS) + vGlobalOffset;
            float s = star.size;
            buf.vertex(mat, star.x - s, star.y - s, 0).uv(u, v).color(red, green, blue, brightness).endVertex(); 
            buf.vertex(mat, star.x - s, star.y + s, 0).uv(u, v).color(red, green, blue, brightness).endVertex(); 
            buf.vertex(mat, star.x + s, star.y + s, 0).uv(u, v).color(red, green, blue, brightness).endVertex(); 
            buf.vertex(mat, star.x + s, star.y - s, 0).uv(u, v).color(red, green, blue, brightness).endVertex(); 
        }
        tess.end();
        RenderSystem.defaultBlendFunc();
    }

    private void renderGlow(GuiGraphics gui, float x, float y, float scale, float r, float g, float b) {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderColor(r, g, b, 0.6f);
        PoseStack p = gui.pose();
        p.pushPose();
        p.translate(x, y, 0);
        p.scale(scale, scale, 1);
        gui.blit(GLOW_ATLAS, -16, -16, 0, 0, 32, 32, 32, 32);
        p.popPose();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
    }

    private void updateResonance(int mx, int my) {
        double cx = this.width / 2.0;
        double cy = this.height / 2.0;
        double vx = (mx - cx) / zoom - scrollX;
        double vy = (my - cy) / zoom - scrollY;
        
        int currentHover = -1;
        for (int i = 0; i < cachedPositions.size(); i++) {
            Vec2 pos = cachedPositions.get(i);
            if (vx >= pos.x - 9 && vx <= pos.x + 9 && vy >= pos.y - 9 && vy <= pos.y + 9) {
                currentHover = i;
                break;
            }
        }
        
        if (currentHover != lastHoveredVisualIndex) {
            lastHoveredVisualIndex = currentHover;
            activeResonanceIndices.clear();
            if (currentHover != -1) {
                if (currentHover > 0) activeResonanceIndices.add(currentHover - 1);
                if (currentHover < cachedPositions.size() - 1) activeResonanceIndices.add(currentHover + 1);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) { 
            isDragging = true;
            return true;
        }

        double centerX = this.width / 2.0;
        double centerY = this.height / 2.0;
        double vx = (mouseX - centerX) / zoom - scrollX;
        double vy = (mouseY - centerY) / zoom - scrollY;

        int runeCount = this.menu.layoutData.size();
        for (int i = runeCount - 1; i >= 0; i--) {
            Vec2 pos = cachedPositions.get(i);
            if (vx >= pos.x - 9 && vx <= pos.x + 9 && vy >= pos.y - 9 && vy <= pos.y + 9) {
                int logicIndex = sortedIndices.get(i);
                Slot slot = this.menu.slots.get(logicIndex);
                this.slotClicked(slot, slot.index, button, net.minecraft.world.inventory.ClickType.PICKUP);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 1) isDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDragging) {
            this.scrollX += dragX / zoom;
            this.scrollY += dragY / zoom;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        float zoomSpeed = 0.1f;
        this.zoom += (delta * zoomSpeed);
        this.zoom = Mth.clamp(this.zoom, 0.25f, 3.0f);
        return true;
    }

    private void renderCustomTooltip(GuiGraphics gui, int mx, int my) {
        Vec2 vm = transformMouse(mx, my);
        int runeCount = this.menu.layoutData.size();
        
        for (int i = 0; i < runeCount; ++i) {
             if (isMouseOverSlot(cachedPositions.get(i), vm.x, vm.y)) {
                 int logicIndex = sortedIndices.get(i);
                 Slot slot = this.menu.slots.get(logicIndex);
                 SlotConfig cfg = this.menu.layoutData.get(logicIndex);
                 
                 List<Component> tooltip = new ArrayList<>();
                 
                 if (slot.hasItem()) {
                     ItemStack stack = slot.getItem();
                     // FIX: Cluster Jewels speziell behandeln
                     if (stack.getItem() instanceof ClusterJewelItem) {
                         ClusterTooltipHandler.appendClusterInfo(stack, tooltip);
                     } else {
                         // DELEGATION: Wir übergeben den spezifischen Sockel-Level.
                         StoneItem.addFullRuneTooltip(stack, tooltip, cfg.requiredLevel);
                     }
                 }
                 
                 // Slot Informationen (immer sichtbar)
                 tooltip.add(Component.literal("Slot: ").withStyle(net.minecraft.ChatFormatting.GRAY)
                     .append(Component.literal(cfg.type.name()).withStyle(net.minecraft.ChatFormatting.GOLD))
                     .append(Component.literal(" (Lvl " + cfg.requiredLevel + ")").withStyle(net.minecraft.ChatFormatting.AQUA)));
                 
                 gui.renderComponentTooltip(this.font, tooltip, mx, my);
                 return;
             }
        }
        super.renderTooltip(gui, mx, my); 
    }

    private Vec2 transformMouse(double mx, double my) {
        double cx = this.width / 2.0;
        double cy = this.height / 2.0;
        double vx = (mx - cx) / zoom - scrollX;
        double vy = (my - cy) / zoom - scrollY;
        return new Vec2((float)vx, (float)vy);
    }
    
    private boolean isMouseOverSlot(Vec2 slotPos, double vmX, double vmY) {
        return vmX >= slotPos.x - 9 && vmX <= slotPos.x + 9 &&
               vmY >= slotPos.y - 9 && vmY <= slotPos.y + 9;
    }
}