package net.stones.client.util;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.FrameType;
import net.minecraft.advancements.critereon.ImpossibleTrigger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.stones.init.StonesModItems;
import net.stones.client.gui.toasts.SimpleLevelToast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * REFAKTORIERTER HELPER
 * Nutzt nun den ClientShrineCache für architektonische Sauberkeit.
 */
public class ClientAdvancementHelper {

    private static final Set<String> knownMilestones = new HashSet<>();
    private static final List<String> lastKnownStats = new ArrayList<>();

    public static void showLevelUpToast(int level, List<Component> bonuses) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean isFirstRun = lastKnownStats.isEmpty() && knownMilestones.isEmpty();

        for (Component bonusComponent : bonuses) {
            String rawText = bonusComponent.getString();
            boolean isMilestone = detectMilestone(bonusComponent);

            if (isMilestone) {
                if (!knownMilestones.contains(rawText)) {
                    knownMilestones.add(rawText);
                    if (!isFirstRun) showMilestoneToast(bonusComponent);
                }
            } else {
                if (!lastKnownStats.contains(rawText)) {
                    if (!isFirstRun) {
                        Component feedback = Component.literal("✦ ").withStyle(ChatFormatting.AQUA)
                            .append(bonusComponent.copy().withStyle(ChatFormatting.WHITE));
                        mc.getToasts().addToast(new SimpleLevelToast(level, feedback));
                    }
                }
            }
        }

        lastKnownStats.clear();
        for (Component c : bonuses) if (!detectMilestone(c)) lastKnownStats.add(c.getString());
    }

    private static boolean detectMilestone(Component c) {
        if (c.getStyle().getColor() != null && c.getStyle().getColor().getValue() == ChatFormatting.LIGHT_PURPLE.getColor()) return true;
        for (Component sib : c.getSiblings()) {
            if (sib.getStyle().getColor() != null && sib.getStyle().getColor().getValue() == ChatFormatting.LIGHT_PURPLE.getColor()) return true;
        }
        return false;
    }

    private static void showMilestoneToast(Component milestoneName) {
        Minecraft mc = Minecraft.getInstance();
        Component description = Component.literal("Meilenstein erreicht!").withStyle(ChatFormatting.LIGHT_PURPLE);
        String cleanName = milestoneName.getString().replace(" ➤ ", "").replace(" (Aktiv)", "");
        Component title = Component.literal(cleanName).withStyle(ChatFormatting.GRAY);

        DisplayInfo displayInfo = new DisplayInfo(
            new ItemStack(StonesModItems.RUNE_MILESTONE.get()), 
            title, description, null, FrameType.CHALLENGE, true, false, true
        );

        ResourceLocation dummyId = new ResourceLocation("stones", "toast_" + System.currentTimeMillis());
        Advancement advancement = Advancement.Builder.advancement()
            .display(displayInfo)
            .rewards(AdvancementRewards.EMPTY)
            .addCriterion("dummy", new Criterion(new ImpossibleTrigger.TriggerInstance()))
            .build(dummyId);

        mc.getToasts().addToast(new AdvancementToast(advancement));
    }
}