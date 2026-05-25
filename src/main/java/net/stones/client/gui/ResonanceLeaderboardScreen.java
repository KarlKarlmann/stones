package net.stones.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.stones.StonesMod;
import net.stones.data.GlobalLeaderboardData;
import net.stones.network.PacketClaimReward;

import java.util.List;

/**
 * Der native Leaderboard-Screen.
 * Markiert den AKTUELLEN Run (den letzten Tod) im globalen Vergleich.
 */
public class ResonanceLeaderboardScreen extends Screen {

    private final List<Integer> pendingScores;
    private final List<GlobalLeaderboardData.LeaderboardEntry> globalEntries;
    private final String currentPlayerName;
    private final int lastRunScore;

    public ResonanceLeaderboardScreen(List<Integer> personal, List<GlobalLeaderboardData.LeaderboardEntry> global) {
        super(Component.literal("Hall of Resonance"));
        this.pendingScores = personal;
        this.globalEntries = global;
        this.currentPlayerName = Minecraft.getInstance().getUser().getName();
        
        // Der letzte Eintrag in der persönlichen Liste ist der Score des gerade beendeten Runs
        this.lastRunScore = personal.isEmpty() ? -1 : personal.get(personal.size() - 1);
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        if (!pendingScores.isEmpty()) {
            this.addRenderableWidget(Button.builder(Component.literal("§d§lALLES BEANSPRUCHEN"), (btn) -> {
                StonesMod.PACKET_HANDLER.sendToServer(new PacketClaimReward());
                this.onClose();
            }).bounds(centerX - 100, centerY + 80, 200, 20).build());
        }

        this.addRenderableWidget(Button.builder(Component.literal("Zurück"), (btn) -> this.onClose())
            .bounds(centerX - 100, centerY + 105, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(gui);
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        gui.drawCenteredString(this.font, "§6§l=== HALL OF RESONANCE ===", centerX, centerY - 100, 0xFFFFFF);

        // --- LINKS: DEINE BELOHNUNGEN ---
        int startY = centerY - 60;
        gui.drawString(this.font, "§dDeine Legacy-Boxen:", centerX - 140, startY, 0xFFFFFF);
        if (pendingScores.isEmpty()) {
            gui.drawString(this.font, "§8Keine Belohnungen.", centerX - 130, startY + 15, 0xFFFFFF);
        } else {
            for (int i = 0; i < Math.min(pendingScores.size(), 8); i++) {
                int score = pendingScores.get(pendingScores.size() - 1 - i);
                // Markierung für den allerneuesten Run in der persönlichen Liste
                String prefix = (i == 0) ? "§b§l> " : "§7- ";
                gui.drawString(this.font, prefix + score + " Pkt. §8(T" + (Math.min(5, (score/1000)+1)) + ")", centerX - 130, startY + 15 + (i * 10), 0xFFFFFF);
            }
        }

        // --- RECHTS: GLOBALE TOP 10 ---
        gui.drawString(this.font, "§6Ewige Helden (Top 10):", centerX + 10, startY, 0xFFFFFF);
        
        // Variable um sicherzustellen, dass wir nur EINEN Eintrag als "Current Run" markieren
        boolean highlightedCurrent = false;

        for (int i = 0; i < Math.min(globalEntries.size(), 10); i++) {
            GlobalLeaderboardData.LeaderboardEntry entry = globalEntries.get(i);
            
            // Prüfung: Ist dies der Run, den wir gerade eben erzielt haben?
            // (Name muss stimmen UND der Score muss exakt der des letzten Todes sein)
            boolean isCurrentRun = !highlightedCurrent && entry.name().equals(currentPlayerName) && entry.score() == lastRunScore;
            if (isCurrentRun) highlightedCurrent = true;

            String prefix = isCurrentRun ? "§b§l>> " : "§7";
            String nameColor = isCurrentRun ? "§b" : (i == 0 ? "§e" : (i == 1 ? "§f" : (i == 2 ? "§6" : "§7")));
            
            // Name und Rang (Current Run blinkt oder ist hellblau)
            gui.drawString(this.font, prefix + (i + 1) + ". " + nameColor + entry.name(), centerX + 15, startY + 15 + (i * 12), 0xFFFFFF);
            
            // Score
            gui.drawString(this.font, nameColor + entry.score(), centerX + 120, startY + 15 + (i * 12), 0xFFFFFF);
            
            // Todesgrund (kleiner drunter)
            String reason = entry.deathReason();
            if (reason.length() > 30) reason = reason.substring(0, 27) + "...";
            gui.drawString(this.font, "§8" + reason, centerX + 25, startY + 23 + (i * 12), 0xFFFFFF);
        }

        super.render(gui, mouseX, mouseY, partialTicks);
    }
}