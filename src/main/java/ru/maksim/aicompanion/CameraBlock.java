package ru.maksim.aicompanion;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One of L's cameras: a small box on the wall looking into the room (FACING is where it looks). */
public class CameraBlock extends HorizontalDirectionalBlock {
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.SOUTH, Block.box(5, 6, 0, 11, 10, 7), Direction.NORTH, Block.box(5, 6, 9, 11, 10, 16),
            Direction.EAST, Block.box(0, 6, 5, 7, 10, 11), Direction.WEST, Block.box(9, 6, 5, 16, 10, 11));

    public CameraBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Broken by the suspect: the picture goes dark, and L takes note of exactly that. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        super.playerWillDestroy(level, pos, state, player);
        if (player instanceof ServerPlayer p && Kira.of(p).kills > 0) {
            Investigation.suspect(p, 6, "a camera went dark");
        }
    }
}
