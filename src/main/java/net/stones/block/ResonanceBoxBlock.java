package net.stones.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult; // Added missing import
import net.minecraftforge.network.NetworkHooks;
import net.stones.block.entity.ResonanceBoxBlockEntity;
import net.stones.init.StonesModBlockEntities;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class ResonanceBoxBlock extends ChestBlock {

    public ResonanceBoxBlock(Properties props) {
        super(props.strength(0.6f)
                .sound(SoundType.WOOD)
                .noOcclusion()
                .noLootTable(),
              () -> StonesModBlockEntities.RESONANCE_BOX.get());
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return Collections.emptyList();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ResonanceBoxBlockEntity(pos, state);
    }

    // WICHTIG: Hier generieren wir den Loot sofort beim Platzieren!
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ResonanceBoxBlockEntity box) {
                // Tier vom Item übernehmen (wird oft schon von BlockItem gemacht, aber sicher ist sicher)
                if (stack.hasTag() && stack.getTag().contains("ResonanceLootTier")) {
                    box.setTier(stack.getTag().getInt("ResonanceLootTier"));
                }
                
                // SOFORT LOOT WÜRFELN!
                box.generateLootNow();
            }
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ResonanceBoxBlockEntity box) {
                NetworkHooks.openScreen((ServerPlayer) player, box, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative()) {
            // Pinata-Effekt (immer, da Loot ja jetzt immer drin ist)
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.2F);
            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5F, 1.0F);
            
            int amount = 2 + level.random.nextInt(4);
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(Items.LAPIS_LAZULI, amount));
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ResonanceBoxBlockEntity box) {
                // Einfach droppen. Inventar ist garantiert voll.
                Containers.dropContents(level, pos, box);
                level.updateNeighbourForOutputSignal(pos, this);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}