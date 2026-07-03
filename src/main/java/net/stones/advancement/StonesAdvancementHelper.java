package net.stones.advancement;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.stones.StonesMod;

public class StonesAdvancementHelper {

    /**
     * Schaltet ein Advancement für einen Spieler per Code frei.
     * Ideal für "Impossible" Triggers, die durch Mod-Logik (Pakete, Events) ausgelöst werden.
     * * @param player Der Spieler
     * @param advancementName Der interne Name (z.B. "root/soul_bond")
     */
    public static void grantAdvancement(ServerPlayer player, String advancementName) {
        if (player == null || player.server == null) return;

        ResourceLocation advId = new ResourceLocation(StonesMod.MODID, advancementName);
        Advancement advancement = player.server.getAdvancements().getAdvancement(advId);

        if (advancement != null) {
            AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
            if (!progress.isDone()) {
                // Wir gewähren alle noch fehlenden Kriterien (meistens nur "dummy" oder "impossible")
                for (String criterion : progress.getRemainingCriteria()) {
                    player.getAdvancements().award(advancement, criterion);
                }
            }
        }
    }
}