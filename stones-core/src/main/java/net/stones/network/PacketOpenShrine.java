package net.stones.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.stones.block.entity.RunestoneBlockEntity;

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
                        
                        // HIER IST DIE MAGIE!
                        // Statt die Daten hier manuell und fehleranfällig zusammenzubauen,
                        // lassen wir das BlockEntity das Menü öffnen. 
                        // openMenu() kümmert sich um das Layout UND schreibt die UUID in den Buffer!
                        runeBe.openMenu(player);
                        
                    }
                }
            }
        });
        return true;
    }
}