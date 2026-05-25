package net.stones.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.stones.features.ScoreRewardSystem;
import java.util.function.Supplier;

/**
 * C2S Paket: Der Spieler möchte alle ausstehenden Belohnungen aus der Liste abholen.
 */
public class PacketClaimReward {
    public PacketClaimReward() {}
    public PacketClaimReward(FriendlyByteBuf buf) {}
    public void toBytes(FriendlyByteBuf buf) {}

    public static void handle(PacketClaimReward msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ScoreRewardSystem.claimReward(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}