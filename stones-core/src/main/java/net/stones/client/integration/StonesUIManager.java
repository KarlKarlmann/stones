package net.stones.client.integration;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.stones.client.gui.RuneInfoScreen;
import net.stones.client.gui.RunestoneScreen;
import net.stones.gui.RunestoneMenu;

import java.util.function.Function;

public class StonesUIManager {

    @FunctionalInterface
    public interface ScreenFactory {
        AbstractContainerScreen<RunestoneMenu> create(RunestoneMenu menu, Inventory inventory, Component title);
    }

    /**
     * Hook für den Haupt-Screen des Runensteins. Standardmäßig der Tabellen-Screen.
     * Das Skinpack 'stones_visuals' kann diese Factory einfach auf seinen eigenen Screen setzen:
     * StonesUIManager.RUNESTONE_SCREEN = net.stones.visuals.client.gui.RunestoneScreen::new;
     */
    public static ScreenFactory RUNESTONE_SCREEN = RunestoneScreen::new;

    /**
     * Hook für den Info-Screen einer einzelnen Rune.
     */
    public static Function<ItemStack, Screen> RUNE_INFO_SCREEN = RuneInfoScreen::new;

    /**
     * Konkrete Delegat-Methode für MenuScreens.register (nicht-generisch, verhindert Inferenz-Fehler).
     */
    public static AbstractContainerScreen<RunestoneMenu> createRunestoneScreen(RunestoneMenu menu, Inventory inventory, Component title) {
        return RUNESTONE_SCREEN.create(menu, inventory, title);
    }
}