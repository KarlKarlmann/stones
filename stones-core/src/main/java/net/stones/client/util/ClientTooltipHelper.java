package net.stones.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;

public class ClientTooltipHelper {
    public static Integer getLevel() {
        Player player = Minecraft.getInstance().player;
        return player != null ? player.experienceLevel : 0;
    }

    public static Boolean isShiftDown() {
        return Screen.hasShiftDown();
    }
}