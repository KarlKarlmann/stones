package net.stones.client.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox; 
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.stones.StonesMod;
import net.stones.data.GlobalLeaderboardData;
import net.stones.network.PacketClaimReward;
import net.stones.network.PacketUpdateEpitaph;

import java.util.List;

/**
 * Der native Leaderboard-Screen.
 * Zeigt das Epitaph-Feld rechts auf der Zeile, wenn platziert.
 * Zeigt links ausstehende Belohnungen als gerenderte, skalierende Items.
 * Integriert einen dezenten Support-Link und Easter-Eggs.
 */
public class ResonanceLeaderboardScreen extends Screen {

    private final List<Integer> pendingScores;
    private final List<GlobalLeaderboardData.LeaderboardEntry> globalEntries;
    private final String currentPlayerName;
    private final int lastRunScore;

    private EditBox epitaphBox;
    private Button saveButton;
    private String currentEpitaph = "";
    private boolean isOnLeaderboard = false; 

    public ResonanceLeaderboardScreen(List<Integer> personal, List<GlobalLeaderboardData.LeaderboardEntry> global, int lastRunScore) {
        super(Component.translatable("gui.stones.leaderboard.title"));
        this.pendingScores = personal;
        this.globalEntries = global;
        this.currentPlayerName = Minecraft.getInstance().getUser().getName();
        this.lastRunScore = lastRunScore;

        boolean highlightedCurrent = false;
        for (GlobalLeaderboardData.LeaderboardEntry entry : global) {
            if (!highlightedCurrent && entry.name().equals(currentPlayerName) && entry.score() == lastRunScore) {
                this.currentEpitaph = entry.deathReason();
                this.isOnLeaderboard = true;
                highlightedCurrent = true;
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int startY = centerY - 105; 

        boolean highlightedCurrent = false;
        for (int i = 0; i < Math.min(globalEntries.size(), 10); i++) {
            GlobalLeaderboardData.LeaderboardEntry entry = globalEntries.get(i);
            boolean isCurrentRun = !highlightedCurrent && entry.name().equals(currentPlayerName) && entry.score() == lastRunScore;
            int entryY = startY + 15 + (i * 22);
            
            if (isCurrentRun && this.lastRunScore != -1) {
                highlightedCurrent = true;
                
                this.epitaphBox = this.addRenderableWidget(new EditBox(this.font, centerX + 25, entryY + 10, 110, 12, Component.translatable("gui.stones.leaderboard.epitaph_placeholder")));
                this.epitaphBox.setMaxLength(45);
                this.epitaphBox.setValue(this.currentEpitaph);

                this.saveButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.stones.leaderboard.save_btn"), (btn) -> {
                    String text = this.epitaphBox.getValue();
                    StonesMod.PACKET_HANDLER.sendToServer(new PacketUpdateEpitaph(text, this.lastRunScore));
                    btn.setMessage(Component.translatable("gui.stones.leaderboard.saved_marker")); 
                    btn.active = false;
                }).bounds(centerX + 140, entryY + 9, 22, 12).build());
                
            } else if (entry.deathReason().contains("{kofi}")) {
                // --- EASTER EGG: INLINE SUPPORT BUTTON ---
                // Ersetzt den {kofi} Tag durch ein hübsches Layout und macht die Zeile klickbar
                String cleanReason = entry.deathReason().replace("{kofi}", "");
                Component btnText = Component.literal("☕ ").withStyle(ChatFormatting.GOLD)
                                    .append(Component.literal(cleanReason).withStyle(ChatFormatting.WHITE));
                
                this.addRenderableWidget(Button.builder(btnText, (btn) -> {
                    Util.getPlatform().openUri("https://ko-fi.com/karlkarlmann");
                }).bounds(centerX + 25, entryY + 7, 130, 16).build());
            }
        }

        // --- BUTTONS LINKS ---
        if (!pendingScores.isEmpty()) {
            this.addRenderableWidget(Button.builder(Component.translatable("gui.stones.leaderboard.claim_all"), (btn) -> {
                if (this.isOnLeaderboard && this.saveButton != null && this.saveButton.active) {
                    StonesMod.PACKET_HANDLER.sendToServer(new PacketUpdateEpitaph(this.epitaphBox.getValue(), this.lastRunScore));
                }
                StonesMod.PACKET_HANDLER.sendToServer(new PacketClaimReward());
                this.onClose();
            }).bounds(centerX - 150, centerY + 45, 140, 20).build());
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.stones.leaderboard.back"), (btn) -> {
            if (this.isOnLeaderboard && this.saveButton != null && this.saveButton.active) {
                StonesMod.PACKET_HANDLER.sendToServer(new PacketUpdateEpitaph(this.epitaphBox.getValue(), this.lastRunScore));
            }
            this.onClose();
        }).bounds(centerX - 150, centerY + 70, 140, 20).build());

        // --- SUPPORT BUTTON (KAFFEE) LINKS OBEN ---
        // Der kleine permanente Button neben den "Deine Belohnungen"
        Component leftSupportText = Component.literal("☕");
        this.addRenderableWidget(Button.builder(leftSupportText, (btn) -> {
            Util.getPlatform().openUri("https://ko-fi.com/karlkarlmann");
        }).bounds(centerX - 35, startY - 6, 20, 20).build());
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(gui);
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int startY = centerY - 105;

        gui.drawCenteredString(this.font, Component.translatable("gui.stones.leaderboard.header"), centerX, centerY - 120, 0xFFFFFF);

        ItemStack hoveredBox = null;

        // --- LINKS: DEINE BELOHNUNGEN ---
        gui.drawString(this.font, Component.translatable("gui.stones.leaderboard.personal_boxes"), centerX - 150, startY, 0xFFFFFF);
        if (pendingScores.isEmpty()) {
            gui.drawString(this.font, Component.translatable("gui.stones.leaderboard.no_rewards"), centerX - 140, startY + 15, 0xFFFFFF);
        } else {
            int boxStartX = centerX - 145;
            int boxStartY = startY + 15;
            int xOffset = 0;
            int yOffset = 0;

            for (int i = 0; i < Math.min(pendingScores.size(), 12); i++) {
                int score = pendingScores.get(pendingScores.size() - 1 - i);
                // Tier-Logik genau wie im ScoreRewardSystem
                int tier = Math.max(1, Math.min(10, (score / 1000) + 1));

                // Wir erzeugen einen Fake-Itemstack der Kiste, um ihn im GUI zu rendern
                ItemStack boxStack = new ItemStack(net.stones.init.StonesModItems.RESONANCE_BOX.get());
                boxStack.getOrCreateTag().putInt("ResonanceLootTier", tier);
                boxStack.setHoverName(Component.translatable("item.stones.resonance_gift", tier).withStyle(ChatFormatting.LIGHT_PURPLE));
                
                net.minecraft.nbt.ListTag lore = new net.minecraft.nbt.ListTag();
                lore.add(net.minecraft.nbt.StringTag.valueOf(
                    Component.Serializer.toJson(Component.translatable("tooltip.stones.score", score).withStyle(ChatFormatting.DARK_GRAY))
                ));
                boxStack.getOrCreateTagElement("display").put("Lore", lore);

                // Dynamische Skalierung (Tier 1 = 0.75x Größe, Tier 10 = 1.2x Größe)
                float scale = 0.7f + (tier * 0.05f); 
                int itemX = boxStartX + xOffset;
                int itemY = boxStartY + yOffset;

                gui.pose().pushPose();
                // Da der Ankerpunkt für die Skalierung links oben wäre, 
                // verschieben wir ihn in die Mitte (+8, +8) der Kiste für zentriertes Wachstum.
                gui.pose().translate(itemX + 8, itemY + 8, 0);
                gui.pose().scale(scale, scale, 1.0f);
                gui.pose().translate(-8, -8, 0);
                
                gui.renderItem(boxStack, 0, 0);
                gui.pose().popPose();

                // Hitbox für Hover-Tooltip berechnen (Berücksichtigt die Skalierung aus der Mitte)
                float halfSize = 8 * scale;
                float hitX = itemX + 8 - halfSize;
                float hitY = itemY + 8 - halfSize;
                float hitW = 16 * scale;
                
                if (mouseX >= hitX && mouseX <= hitX + hitW && mouseY >= hitY && mouseY <= hitY + hitW) {
                    hoveredBox = boxStack;
                }

                xOffset += 24; // Platz für die nächste Kiste in der Reihe
                if (xOffset >= 120) { // Nach 5 Kisten eine neue Zeile anfangen (Raster)
                    xOffset = 0;
                    yOffset += 24;
                }
            }
        }

        // Info-Text auf der linken Seite
        if (this.isOnLeaderboard && this.epitaphBox != null) {
            gui.drawString(this.font, Component.translatable("gui.stones.leaderboard.immortalized"), centerX - 150, centerY - 10, 0xFFFFFF);
            gui.drawString(this.font, Component.translatable("gui.stones.leaderboard.write_inscription_line1"), centerX - 150, centerY, 0x888888);
            gui.drawString(this.font, Component.translatable("gui.stones.leaderboard.write_inscription_line2"), centerX - 150, centerY + 10, 0x888888);
        } else if (this.lastRunScore > 0) {
            gui.drawString(this.font, Component.translatable("gui.stones.leaderboard.not_placed_title"), centerX - 150, centerY - 10, 0xFF5555); 
            gui.drawString(this.font, Component.translatable("gui.stones.leaderboard.your_score", this.lastRunScore), centerX - 150, centerY, 0xAAAAAA);
            
            int requiredScore = net.stones.init.StonesModConfig.REWARD_SCORE_THRESHOLD.get();
            gui.drawString(this.font, Component.translatable("gui.stones.leaderboard.hint_boxes_1", requiredScore), centerX - 150, centerY + 15, 0x666666);
            gui.drawString(this.font, Component.translatable("gui.stones.leaderboard.hint_boxes_2"), centerX - 150, centerY + 25, 0x666666);
        }

        // --- RECHTS: GLOBALE TOP 10 ---
        gui.drawString(this.font, Component.translatable("gui.stones.leaderboard.global_title"), centerX + 10, startY, 0xFFFFFF);
        
        boolean highlightedCurrent = false;

        for (int i = 0; i < Math.min(globalEntries.size(), 10); i++) {
            GlobalLeaderboardData.LeaderboardEntry entry = globalEntries.get(i);
            
            boolean isCurrentRun = !highlightedCurrent && entry.name().equals(currentPlayerName) && entry.score() == lastRunScore;
            if (isCurrentRun) highlightedCurrent = true;

            String prefix = isCurrentRun ? "§b§l>> " : "§7";
            String nameColor = isCurrentRun ? "§b" : (i == 0 ? "§e" : (i == 1 ? "§f" : (i == 2 ? "§6" : "§7")));
            
            int entryY = startY + 15 + (i * 22);
            gui.drawString(this.font, prefix + (i + 1) + ". " + nameColor + entry.name(), centerX + 15, entryY, 0xFFFFFF);
            
            gui.drawString(this.font, nameColor + entry.score(), centerX + 165, entryY, 0xFFFFFF);
            
            if (!isCurrentRun) {
                String reason = entry.deathReason();
                // Überprüfen ob der Text den {kofi} Tag enthält
                if (reason.contains("{kofi}")) {
                    // WICHTIG: Wenn der Tag da ist, rendern wir KEINEN Text. 
                    // Der Button wurde bereits in der init() Methode genau über diese Position gelegt!
                } else {
                    if (reason.length() > 30) reason = reason.substring(0, 27) + "...";
                    gui.drawString(this.font, Component.translatable("gui.stones.leaderboard.epitaph_display", reason), centerX + 25, entryY + 10, 0xFFFFFF);
                }
            }
        }

        super.render(gui, mouseX, mouseY, partialTicks);
        
        // Tooltip ganz am Ende über alles drüber rendern, damit er von nichts verdeckt wird
        if (hoveredBox != null) {
            gui.renderTooltip(this.font, hoveredBox, mouseX, mouseY);
        }
    }
}