package ru.maksim.aicompanion;

import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Light's desk drawer, built from the manga's trick: opened, it shows a decoy diary; the false bottom lifts only the
 * secret way, a pen's ink cartridge pushed through a little hole (here: an ink sac in hand). Forced (the drawer
 * broken), the spark meets the bag of gasoline and whatever lay inside burns. The Task Force searches every chest by
 * the bed and never finds the false bottom.
 */
public final class Hideouts {
    private Hideouts() {
    }

    /** Containers the house search skips: the false bottom of a drawer. */
    static boolean isHidden(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(AiCompanion.DRAWER.get());
    }

    public static class DrawerBlock extends BaseEntityBlock {
        public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

        public DrawerBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
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
        public RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        }

        @Nullable
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new Drawer(pos, state);
        }

        @Override
        public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            if (level.isClientSide || !(level.getBlockEntity(pos) instanceof Drawer d) || !(player instanceof ServerPlayer p)) {
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (player.getItemInHand(hand).is(Items.INK_SAC)) { // the cartridge through the hole: the false bottom lifts
                p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new ChestMenu(MenuType.GENERIC_9x1, id, inv, d, 1),
                        Component.translatable("aicompanion.drawer.secret")));
            } else {
                p.sendSystemMessage(Component.translatable("aicompanion.drawer.decoy").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
            return InteractionResult.CONSUME;
        }

        /** The item's tooltip (BlockItem asks its block): how the false bottom opens. */
        @Override
        public void appendHoverText(ItemStack stack, @Nullable net.minecraft.world.level.BlockGetter level, java.util.List<Component> tooltip,
                                    net.minecraft.world.item.TooltipFlag flag) {
            tooltip.add(Component.translatable("block.aicompanion.hidden_drawer.tooltip").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }

        @Override
        public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof Drawer d && !d.isEmpty()) {
                player.sendSystemMessage(Component.translatable("aicompanion.drawer.burned").withStyle(ChatFormatting.GOLD));
            }
            super.playerWillDestroy(level, pos, state, player);
        }

        @Override
        public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
            if (!state.is(next.getBlock()) && level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof Drawer d) {
                d.burn(server, pos); // broken open: the trap goes off too
            }
            super.onRemove(state, level, pos, next, moved);
        }
    }

    /** The false bottom's nine slots. */
    public static class Drawer extends BlockEntity implements Container {
        private final NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);

        public Drawer(BlockPos pos, BlockState state) {
            super(AiCompanion.DRAWER_ENTITY.get(), pos, state);
        }

        void burn(ServerLevel level, BlockPos pos) {
            boolean any = !isEmpty();
            items.replaceAll(s -> ItemStack.EMPTY);
            setChanged();
            if (any) {
                level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 30, 0.3, 0.2, 0.3, 0.02);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 12, 0.2, 0.2, 0.2, 0.01);
                level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }

        @Override
        public void load(CompoundTag tag) {
            super.load(tag);
            ContainerHelper.loadAllItems(tag, items);
        }

        @Override
        protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            ContainerHelper.saveAllItems(tag, items);
        }

        @Override
        public int getContainerSize() {
            return items.size();
        }

        @Override
        public boolean isEmpty() {
            return items.stream().allMatch(ItemStack::isEmpty);
        }

        @Override
        public ItemStack getItem(int slot) {
            return items.get(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            ItemStack s = ContainerHelper.removeItem(items, slot, count);
            setChanged();
            return s;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ContainerHelper.takeItem(items, slot);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            items.set(slot, stack);
            setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(this, player);
        }

        @Override
        public void clearContent() {
            items.clear();
        }
    }
}
