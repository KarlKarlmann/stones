package net.stones.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.stones.block.entity.RunestoneBlockEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.stones.data.ShrineSavedData;
import net.stones.data.ShrineInstance;

// Zusätzliche Imports für NBT-Erhaltung, Abbau-Restriktionen, Item-Erhalt und Explosionen
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.Containers;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Der Runenstein-Block (Void Altar).
 * Im inaktiven Zustand ein massiver schwarzer Monolith.
 * Nach der Aktivierung (Binding) wird er transparent und offenbart das innere "Logic Sheet".
 */
public class RunestoneBlock extends Block implements EntityBlock {

    // Eigenschaft, um den aktivierten Zustand (Glas-Look) anzuzeigen
    public static final BooleanProperty ACTIVE = BlockStateProperties.LIT;

    public RunestoneBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(50.0f, 50.0f) 
                .requiresCorrectToolForDrops()
                .sound(SoundType.GLASS) 
                .noOcclusion()
                .noLootTable()); // Deaktiviert die Standard-Loot-Table, da wir playerWillDestroy nutzen
        
        this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block(); 
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RunestoneBlockEntity(pos, state);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return Collections.emptyList();
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RunestoneBlockEntity runeBe) {
                UUID id = runeBe.getShrineId();
                if (id != null) {
                    ServerLevel serverLevel = (ServerLevel) level;
                    ShrineInstance shrine = ShrineSavedData.get(serverLevel).getShrine(id);
                    
                    if (shrine != null) {
                        // 1. SCANNAUFTRAG: Suchen nach "Fluch der Bindung" (Curse of Binding)
                        boolean hasBindingCurse = false;
                        IItemHandler inv = shrine.getInventory();
                        for (int i = 0; i < inv.getSlots(); i++) {
                            ItemStack stackInSlot = inv.getStackInSlot(i);
                            if (!stackInSlot.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BINDING_CURSE, stackInSlot) > 0) {
                                hasBindingCurse = true;
                                break;
                            }
                        }

                        // Falls ein Fluch aktiv ist, geht der Altar hoch!
                        if (hasBindingCurse) {
                            // Wir löschen den Schrein sofort aus dem System (absoluter Datenverlust)
                            ShrineSavedData.get(serverLevel).removeShrine(id);
                            
                            // BOOM! (Erzeugt eine mächtige Explosion, die auch Feuer legen kann)
                            level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4.0F, true, Level.ExplosionInteraction.BLOCK);
                        } 
                        else if (!player.isCreative()) {
                            // 2. CHECK AUF DIAMOND/NETHERITE PICKAXE + SILK TOUCH
                            ItemStack mainHandItem = player.getMainHandItem();
                            boolean isCorrectPickaxe = mainHandItem.is(Items.DIAMOND_PICKAXE) || mainHandItem.is(Items.NETHERITE_PICKAXE);
                            boolean hasSilkTouch = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, mainHandItem) > 0;

                            if (isCorrectPickaxe && hasSilkTouch) {
                                // Behutsamer Abbau geglückt: Altar wird mit seiner UUID als NBT gedroppt
                                ItemStack dropStack = new ItemStack(this);
                                CompoundTag tag = dropStack.getOrCreateTag();
                                tag.putUUID("shrineId", id);
                                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, dropStack);
                            } else {
                                // Kein Silk Touch: Der Altar zerbricht!
                                // Runen werden fallen gelassen, damit sie nicht im Nirgendwo verschwinden
                                for (int i = 0; i < inv.getSlots(); i++) {
                                    ItemStack stackInSlot = inv.getStackInSlot(i);
                                    if (!stackInSlot.isEmpty()) {
                                        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stackInSlot.copy());
                                    }
                                }
                                
                                // Wir löschen den Schrein aus der Registrierung
                                ShrineSavedData.get(serverLevel).removeShrine(id);
                                
                                // Lauter Glas-Zerbrechen-Sound
                                level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
                            }
                        }
                    }
                }
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RunestoneBlockEntity runeBe) {
                if (stack.hasTag() && stack.getTag().contains("shrineId")) {
                    UUID id = stack.getTag().getUUID("shrineId");
                    runeBe.setShrineId(id);
                    // Den Block beim Platzieren direkt wieder aktivieren (Lichtdurchlässigkeit & transparentes Rendern)
                    level.setBlock(pos, state.setValue(ACTIVE, true), 3);
                }
            }
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && hand == InteractionHand.MAIN_HAND) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RunestoneBlockEntity runeBe) {
                UUID id = runeBe.getShrineId();
                if (id == null) {
                    // Aktivierung: Schrein erstellen und Block-Zustand auf ACTIVE setzen
                    ShrineInstance newShrine = ShrineSavedData.get((ServerLevel)level).createShrine();
                    runeBe.setShrineId(newShrine.getId());
                    
                    level.setBlock(pos, state.setValue(ACTIVE, true), 3);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.SUCCESS;
    }

    // Sorgt dafür, dass Licht durch den Block fallen kann, wenn er aktiv ist
    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(ACTIVE) ? 0 : 15;
    }
}