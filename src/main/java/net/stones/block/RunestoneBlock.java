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
                .noOcclusion()); // Ermöglicht Transparenz und das Rendern des Inhalts
        
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