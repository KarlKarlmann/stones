package net.stones.cap;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;

public class PlayerShrineCapProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
    
    public static final Capability<IPlayerShrineLink> SHRINE_LINK = CapabilityManager.get(new CapabilityToken<>(){});

    private PlayerShrineLink backend = null;
    private final LazyOptional<IPlayerShrineLink> optional = LazyOptional.of(this::createPlayerShrineLink);

    private PlayerShrineLink createPlayerShrineLink() {
        if (this.backend == null) {
            this.backend = new PlayerShrineLink();
        }
        return this.backend;
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
        if (cap == SHRINE_LINK) {
            return optional.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return createPlayerShrineLink().serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        createPlayerShrineLink().deserializeNBT(nbt);
    }
}