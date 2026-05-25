package net.stones.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox; 
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.stones.StonesMod;
import net.stones.data.GlobalLeaderboardData;
import net.stones.network.PacketClaimReward;
import net.stones.network.PacketUpdateEpitaph;

import java.util.List;

/**
 * Der native Leaderboard-Screen.
 * Zeigt das Epitaph-Feld rechts auf der Zeile, wenn platziert.
 * Zeigt links Infos zur Benötigten Punktzahl an, wenn nicht platziert.
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

        if (this.isOnLeaderboard && this.lastRunScore != -1) {
            boolean highlightedCurrent = false;
            for (int i = 0; i < Math.min(globalEntries.size(), 10); i++) {
                GlobalLeaderboardData.LeaderboardEntry entry = globalEntries.get(i);
                boolean isCurrentRun = !highlightedCurrent && entry.name().equals(currentPlayerName) && entry.score() == lastRunScore;
                
                if (isCurrentRun) {
                    highlightedCurrent = true;
                    int entryY = startY + 15 + (i * 22);
                    
                    this.epitaphBox = this.addRenderableWidget(new EditBox(this.font, centerX + 25, entryY + 10, 110, 12, Component.translatable("gui.stones.leaderboard.epitaph_placeholder")));
                    this.epitaphBox.setMaxLength(45);
                    this.epitaphBox.setValue(this.currentEpitaph);

                    this.saveButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.stones.leaderboard.save_btn"), (btn) -> {
                        String text = this.epitaphBox.getValue();
                        StonesMod.PACKET_HANDLER.sendToServer(new PacketUpdateEpitaph(text, this.lastRunScore));
                        btn.setMessage(Component.translatable("gui.stones.leaderboard.saved_marker")); 
                        btn.active = false;
                    }).bounds(centerX + 140, entryY + 9, 22, 12).build());
                    
                    break;
                }
            }
        }

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
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(gui);
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int startY = centerY - 105;

        gui.drawCenteredString(this.font, Component.translatable("gui.stones.leaderboard.header"), centerX, centerY - 120, 0xFFFFFF);

        // --- LINKS: DEINE BELOHNUNGEN ---
        gui.drawString(this.font, Component.translatable("gui.stones.leaderboard.personal_boxes"), centerX - 150, startY, 0xFFFFFF);
        if (pendingScores.isEmpty()) {
            gui.drawString(this.font, Component.translatable("gui.stones.leaderboard.no_rewards"), centerX - 140, startY + 15, 0xFFFFFF);
        } else {
            for (int i = 0; i < Math.min(pendingScores.size(), 6); i++) {
                int score = pendingScores.get(pendingScores.size() - 1 - i);
                String prefix = (i == 0) ? "§b§l> " : "§7- ";
                gui.drawString(this.font, Component.translatable("gui.stones.leaderboard.score_entry", prefix, score, (Math.min(5, (score / 1000) + 1))), centerX - 140, startY + 15 + (i * 10), 0xFFFFFF);
            }
        }

        // Info-Text auf der linken Seite
        if (this.isOnLeaderboard && this.epitaphBox != null) {
            // Spieler ist auf dem Leaderboard
            gui.drawString(this.font, Component.translatable("gui.stones.leaderboard.immortalized"), centerX - 150, centerY - 10, 0xFFFFFF);
            gui.drawString(this.font, Component.translatable("gui.stones.leaderboard.write_inscription_line1"), centerX - 150, centerY, 0x888888);
            gui.drawString(this.font, Component.translatable("gui.stones.leaderboard.write_inscription_line2"), centerX - 150, centerY + 10, 0x888888);
        } else if (this.lastRunScore > 0) {
            // Spieler hat einen Score, ist aber NICHT auf dem Leaderboard
            gui.drawString(this.font, Component.translatable("gui.stones.leaderboard.not_placed_title"), centerX - 150, centerY - 10, 0xFF5555); 
            gui.drawString(this.font, Component.translatable("gui.stones.leaderboard.your_score", this.lastRunScore), centerX - 150, centerY, 0xAAAAAA);
            gui.drawString(this.font, Component.translatable("gui.stones.leaderboard.hint_boxes_1"), centerX - 150, centerY + 15, 0x666666);
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
                if (reason.length() > 30) reason = reason.substring(0, 27) + "...";
                gui.drawString(this.font, Component.translatable("gui.stones.leaderboard.epitaph_display", reason), centerX + 25, entryY + 10, 0xFFFFFF);
            }
        }

        super.render(gui, mouseX, mouseY, partialTicks);
    }
}