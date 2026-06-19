package net.stones.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.stones.client.gui.RuneInfoScreen;
import net.stones.client.gui.ResonanceLeaderboardScreen;
import net.stones.network.PacketOpenLeaderboard;

public class StonesClientProxy {
    
    // Für das StoneItem
    public static void openRuneInfoScreen(ItemStack stack) {
        Minecraft.getInstance().setScreen(new RuneInfoScreen(stack));
    }

    // Für das PacketOpenLeaderboard
    public static void handleLeaderboardPacket(PacketOpenLeaderboard msg) {
        Minecraft.getInstance().setScreen(new ResonanceLeaderboardScreen(msg.personalScores, msg.globalEntries, msg.lastRunScore));
    }
}