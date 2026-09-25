package net.stones.cap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class PlayerShrineLink implements IPlayerShrineLink {
    private UUID shrineId;
    private GlobalPos shrinePos;

    @Override
    public void setLinkedShrine(@Nullable UUID shrineId, @Nullable GlobalPos pos) {
        this.shrineId = shrineId;
        this.shrinePos = pos;
    }

    @Override
    public @Nullable UUID getLinkedShrine() { return shrineId; }

    @Override
    public @Nullable GlobalPos getShrinePos() { return shrinePos; }

    @Override
    public boolean isLinked() { return shrineId != null; }

    @Override
    public void copyFrom(IPlayerShrineLink source) {
        this.shrineId = source.getLinkedShrine();
        this.shrinePos = source.getShrinePos();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        if (shrineId != null) {
            tag.putUUID("shrineId", shrineId);
        }
        if (shrinePos != null) {
            tag.put("pos", NbtUtils.writeBlockPos(shrinePos.pos()));
            tag.putString("dim", shrinePos.dimension().location().toString());
        }
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.contains("shrineId")) {
            this.shrineId = nbt.getUUID("shrineId");
        } else {
            this.shrineId = null;
        }
        
        if (nbt.contains("pos") && nbt.contains("dim")) {
            BlockPos pos = NbtUtils.readBlockPos(nbt.getCompound("pos"));
            ResourceLocation dimLoc = new ResourceLocation(nbt.getString("dim"));
            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimLoc);
            this.shrinePos = GlobalPos.of(dimKey, pos);
        } else {
            this.shrinePos = null;
        }
    }
}