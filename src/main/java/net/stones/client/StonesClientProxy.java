package net.stones.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.stones.client.gui.RuneInfoScreen;

public class StonesClientProxy {
    
    // Für das StoneItem
    public static void openRuneInfoScreen(ItemStack stack) {
        Minecraft.getInstance().setScreen(new RuneInfoScreen(stack));
    }
}