package ru.maksim.aicompanion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A television: five iron ingots and a black dye. Sakura TV's news is how a notebook's owner learns the names and faces
 * of the wanted, so the news reaches only a viewer (News.viewer): someone who carries a set (it's a small one) or has
 * switched one on. Placed, a click switches it on and shows what is on air now: the wanted men, and L's special while
 * it lasts; sneaking, a click switches it off. In hand, a click into the air does the same (Portable).
 */
public class TelevisionBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty ON = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 13, 15);

    public TelevisionBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ON);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); // the screen toward whoever puts it down
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        boolean on = !player.isShiftKeyDown();
        if (state.getValue(ON) != on) {
            level.setBlock(pos, state.setValue(ON, on), Block.UPDATE_ALL);
        }
        level.playSound(null, pos, (on ? Sounds.TV_ON : Sounds.TV_OFF).get(), SoundSource.BLOCKS, 0.9F, 1.0F);
        if (on && player instanceof ServerPlayer viewer) {
            News.watch(viewer);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** The set as an item: it works in the pocket; a click into the air turns it on in the hand. */
    public static class Portable extends net.minecraft.world.item.BlockItem {
        public Portable(Block block, Properties properties) {
            super(block, properties);
        }

        @Override
        public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(Level level, Player player, InteractionHand hand) {
            if (!level.isClientSide && player instanceof ServerPlayer viewer) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(), Sounds.TV_ON.get(), SoundSource.PLAYERS, 0.6F, 1.15F);
                News.watch(viewer);
            }
            return net.minecraft.world.InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        }
    }
}
