package net.stones.event;

import net.minecraft.server.level.ServerPlayer;

public class StonesActionDispatcher {

    @FunctionalInterface
    public interface ActionConsumer {
        void accept(ServerPlayer player, String runeId, int slot);
    }

    private static ActionConsumer consumer = null;

    public static void setConsumer(ActionConsumer c) {
        consumer = c;
    }

    public static void dispatch(ServerPlayer player, String runeId, int slot) {
        if (consumer != null) {
            try {
                consumer.accept(player, runeId, slot);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}