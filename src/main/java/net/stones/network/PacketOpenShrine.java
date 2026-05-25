package net.stones.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import net.stones.block.entity.RunestoneBlockEntity;
import net.stones.data.ShrineInstance;
import net.stones.data.ShrineSavedData;
import net.stones.data.ShrineInstance.SlotConfig;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class PacketOpenShrine {

    private final BlockPos pos;

    public PacketOpenShrine(BlockPos pos) {
        this.pos = pos;
    }

    public PacketOpenShrine(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ServerLevel level = player.serverLevel();
                if (level.isLoaded(pos)) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof RunestoneBlockEntity runeBe) {
                        UUID shrineId = runeBe.getShrineId();
                        if (shrineId != null) {
                            ShrineInstance shrine = ShrineSavedData.get(level).getShrine(shrineId);
                            if (shrine != null) {
                                // Hier ist die Logik, die früher im Block war
                                NetworkHooks.openScreen(player, runeBe, buf -> {
                                    buf.writeInt(shrine.getInventory().getSlots());
                                    List<SlotConfig> layout = shrine.getLayout();
                                    buf.writeInt(layout.size());
                                    for (SlotConfig cfg : layout) {
                                        buf.writeEnum(cfg.type);
                                        buf.writeInt(cfg.requiredLevel);
                                        buf.writeInt(cfg.inventoryIndex);
                                    }
                                });
                            }
                        }
                    }
                }
            }
        });
        return true;
    }
}