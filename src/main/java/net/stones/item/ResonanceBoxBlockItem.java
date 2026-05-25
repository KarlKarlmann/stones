package net.stones.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.stones.block.entity.ResonanceBoxBlockEntity;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;

public class ResonanceBoxBlockItem extends BlockItem {
    public ResonanceBoxBlockItem(Block block, Properties props) { super(block, props); }

    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack, BlockState state) {
        boolean res = super.updateCustomBlockEntityTag(pos, level, player, stack, state);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ResonanceBoxBlockEntity box) {
            CompoundTag tag = stack.getTag();
            if (tag != null && tag.contains("ResonanceLootTier")) {
                box.setTier(tag.getInt("ResonanceLootTier"));
            }
        }
        return res;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int tier = stack.getOrCreateTag().getInt("ResonanceLootTier");
        int displayTier = tier > 0 ? tier : 1;
        
        // Nutzt nun das Dictionary anstelle von Hardcoded-Strings
        tooltip.add(Component.translatable("tooltip.stones.resonance_tier", displayTier).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.stones.resonance_desc1").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.stones.resonance_desc2").withStyle(ChatFormatting.DARK_GRAY));
    }
}