package net.stones.client.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.stones.client.gui.toasts.SimpleLevelToast;
import net.stones.init.StonesModConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * REFAKTORIERTER HELPER
 * Nutzt nun den ClientShrineCache für architektonische Sauberkeit.
 * Toasts sind über die Config nun zu-/abschaltbar.
 */
public class ClientAdvancementHelper {

    private static final Set<String> knownMilestones = new HashSet<>();
    private static final List<String> lastKnownStats = new ArrayList<>();

    public static void showLevelUpToast(int level, List<Component> bonuses) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean isFirstRun = lastKnownStats.isEmpty() && knownMilestones.isEmpty();
        Set<String> currentActiveMilestones = new HashSet<>();

        for (Component bonusComponent : bonuses) {
            String rawText = bonusComponent.getString();
            boolean isMilestone = detectMilestone(bonusComponent);

            if (isMilestone) {
                currentActiveMilestones.add(rawText);
                if (!knownMilestones.contains(rawText)) {
                    knownMilestones.add(rawText);
                    // Config Check für Meilensteine
                    if (!isFirstRun && StonesModConfig.SHOW_MILESTONE_TOASTS.get()) {
                        showMilestoneToast(bonusComponent);
                    }
                }
            } else {
                if (!lastKnownStats.contains(rawText)) {
                    // Config Check für normale Stats
                    if (!isFirstRun && StonesModConfig.SHOW_STAT_TOASTS.get()) {
                        Component feedback = Component.literal("✦ ").withStyle(ChatFormatting.AQUA)
                            .append(bonusComponent.copy().withStyle(ChatFormatting.WHITE));
                        int color = parseHexColor(StonesModConfig.STAT_TOAST_COLOR.get(), 0xFF00FFFF);
                        long duration = StonesModConfig.STAT_TOAST_DURATION.get();
                        mc.getToasts().addToast(new SimpleLevelToast(feedback, color, duration));
                    }
                }
            }
        }

        // FIX: Veraltete (nicht mehr aktive) Meilensteine aus dem Cache werfen!
        // Dadurch triggert der Toast erneut, falls man die Rune entfernt und wieder einsetzt.
        knownMilestones.retainAll(currentActiveMilestones);

        lastKnownStats.clear();
        for (Component c : bonuses) {
            if (!detectMilestone(c)) {
                lastKnownStats.add(c.getString());
            }
        }
    }

    // Bombensichere Erkennung: Wir suchen einfach nach dem Text!
    private static boolean detectMilestone(Component c) {
        return c.getString().endsWith("(Aktiv)");
    }

    private static void showMilestoneToast(Component milestoneName) {
        Minecraft mc = Minecraft.getInstance();

        String cleanName = milestoneName.getString().replace(" ➤ ", "").replace(" (Aktiv)", "");

        Component feedback = Component.literal("✦ ").withStyle(ChatFormatting.LIGHT_PURPLE)
            .append(Component.literal(cleanName).withStyle(ChatFormatting.GOLD));
            
        int color = parseHexColor(StonesModConfig.MILESTONE_TOAST_COLOR.get(), 0xFFFF55FF);
        long duration = StonesModConfig.MILESTONE_TOAST_DURATION.get();
        mc.getToasts().addToast(new SimpleLevelToast(feedback, color, duration));

        if (mc.player != null) {
            mc.player.playSound(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
        }
    }

    private static int parseHexColor(String hex, int defaultColor) {
        try {
            if (hex.startsWith("#")) hex = hex.substring(1);
            if (hex.length() == 6) hex = "FF" + hex;
            return (int) Long.parseLong(hex, 16);
        } catch (Exception e) {
            return defaultColor;
        }
    }
}