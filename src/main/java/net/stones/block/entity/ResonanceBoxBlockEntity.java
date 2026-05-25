package net.stones.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.stones.init.StonesModBlockEntities;
import net.stones.util.RuneGenerator;

import java.util.List;

public class ResonanceBoxBlockEntity extends ChestBlockEntity {

    private int tier = 1;

    public ResonanceBoxBlockEntity(BlockPos pos, BlockState state) {
        super(StonesModBlockEntities.RESONANCE_BOX.get(), pos, state);
    }

    public void setTier(int tier) { 
        this.tier = tier; 
        setChanged(); 
    }
    
    public int getTier() { return tier; }

    @Override
    public Component getDisplayName() {
        return Component.translatable("item.stones.resonance_gift", tier);
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return ChestMenu.threeRows(id, playerInventory, this);
    }

    public void generateLootNow() {
        if (this.level == null || this.level.isClientSide) return;

        List<ItemStack> loot = RuneGenerator.generateBoxLoot(this.tier, (ServerLevel) this.level, Vec3.atCenterOf(this.worldPosition));
        
        for (ItemStack stack : loot) {
            for (int i = 0; i < this.getContainerSize(); i++) {
                if (this.getItem(i).isEmpty()) {
                    this.setItem(i, stack);
                    break;
                }
            }
        }
        
        this.setChanged();

        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.ENCHANT, 
                worldPosition.getX() + 0.5, worldPosition.getY() + 1.2, worldPosition.getZ() + 0.5, 
                5, 0.2, 0.2, 0.2, 0.05);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.tier = tag.getInt("ResonanceLootTier");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("ResonanceLootTier", this.tier);
    }
}