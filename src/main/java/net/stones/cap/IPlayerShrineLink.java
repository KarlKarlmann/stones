package net.stones.cap;

import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;
import java.util.UUID;

public interface IPlayerShrineLink extends INBTSerializable<CompoundTag> {
    void setLinkedShrine(UUID shrineId, GlobalPos pos); // Update: ID + Position
    
    UUID getLinkedShrine();
    GlobalPos getShrinePos(); // NEU
    
    boolean isLinked();
    
    void copyFrom(IPlayerShrineLink source);
}