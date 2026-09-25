package net.stones.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.stones.client.integration.StonesUIManager;

public class StonesClientProxy {

    public static void openRuneInfoScreen(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            Minecraft.getInstance().setScreen(StonesUIManager.RUNE_INFO_SCREEN.apply(stack));
        }
    }
}