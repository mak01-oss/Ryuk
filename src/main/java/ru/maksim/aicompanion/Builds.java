package ru.maksim.aicompanion;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.BedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The story's places, built from vanilla blocks where and when they are needed: Lind L. Tailor's stage, L's
 * headquarters, the mafia's hideout, the shinigami realm's monuments. Big builds go through a queue, a few thousand
 * blocks per tick, so the server doesn't stall.
 */
public final class Builds {
    private static final int PER_TICK = 4000;
    private static final Deque<Placement> QUEUE = new ArrayDeque<>();
    private static final List<Placement> JOIN = new ArrayList<>(); // panes, bars, fences: joined to their neighbours once all is up

    private record Placement(ResourceKey<Level> level, BlockPos pos, BlockState state, CompoundTag tile) {
    }

    private Builds() {
    }

    public static void tick(MinecraftServer server) {
        for (int i = 0; i < PER_TICK && !QUEUE.isEmpty(); i++) {
            Placement p = QUEUE.poll();
            ServerLevel level = server.getLevel(p.level());
            if (level != null && !(level.getBlockEntity(p.pos()) instanceof Container)) { // a chest somebody filled stays, whatever the plan says
                level.setBlock(p.pos(), p.state(), Block.UPDATE_CLIENTS);
                if (p.tile() != null && level.getBlockEntity(p.pos()) != null) {
                    level.getBlockEntity(p.pos()).load(p.tile());
                }
                if (p.state().getBlock() instanceof IronBarsBlock || p.state().getBlock() instanceof FenceBlock
                        || p.state().getBlock() instanceof WallBlock) {
                    JOIN.add(p);
                }
            }
        }
        if (QUEUE.isEmpty() && !JOIN.isEmpty()) {
            for (Placement p : JOIN) {
                ServerLevel level = server.getLevel(p.level());
                if (level != null && level.getBlockState(p.pos()).getBlock() == p.state().getBlock()) {
                    BlockState now = level.getBlockState(p.pos()), joined = Block.updateFromNeighbourShapes(now, level, p.pos());
                    if (joined != now) {
                        level.setBlock(p.pos(), joined, Block.UPDATE_CLIENTS);
                    }
                }
            }
            JOIN.clear();
        }
        if (QUEUE.isEmpty() && !AFTER.isEmpty()) {
            List<Runnable> run = new ArrayList<>(AFTER);
            AFTER.clear();
            run.forEach(Runnable::run);
        }
    }

    /** The server stopped: a build left half done mustn't spill into the next world opened. */
    public static void clear() {
        QUEUE.clear();
        JOIN.clear();
        AFTER.clear();
    }

    /** Somebody's place: a chest, a furnace, a bed or a sign within {@code r} of {@code at}, at any height. */
    static boolean lived(ServerLevel level, BlockPos at, int r) {
        for (int cx = (at.getX() - r) >> 4; cx <= (at.getX() + r) >> 4; cx++) {
            for (int cz = (at.getZ() - r) >> 4; cz <= (at.getZ() + r) >> 4; cz++) {
                for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    BlockPos p = be.getBlockPos();
                    if (Math.abs(p.getX() - at.getX()) <= r && Math.abs(p.getZ() - at.getZ()) <= r
                            && (be instanceof Container || be instanceof BedBlockEntity || be instanceof SignBlockEntity)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** The heightmap at {@code at}, the chunk generated first: one nobody has loaded yet reports the bottom of the world. */
    static BlockPos ground(ServerLevel level, net.minecraft.world.level.levelgen.Heightmap.Types type, BlockPos at) {
        level.getChunk(at.getX() >> 4, at.getZ() >> 4);
        return level.getHeightmapPos(type, at);
    }

    /**
     * The flattest dry patch of twelve on a ring {@code distance} around {@code centre} (a village's well, say, stays
     * untouched), {@code half} blocks each way: the ground block under its middle.
     */
    static BlockPos flatNear(ServerLevel level, BlockPos centre, int distance, int half) {
        net.minecraft.world.level.levelgen.Heightmap.Types surface = net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
        BlockPos best = null;
        int bestSpread = Integer.MAX_VALUE;
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            BlockPos p = centre.offset((int) (Math.sin(a) * distance), 0, (int) (Math.cos(a) * distance));
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            boolean water = false;
            for (int dx = -half; dx <= half; dx += half) {
                for (int dz = -half; dz <= half; dz += half) {
                    BlockPos top = ground(level, surface, p.offset(dx, 0, dz));
                    water |= !level.getFluidState(top.below()).isEmpty();
                    min = Math.min(min, top.getY());
                    max = Math.max(max, top.getY());
                }
            }
            if (!water && max - min < bestSpread && !lived(level, p, half + 2)) {
                bestSpread = max - min;
                best = new BlockPos(p.getX(), min - 1, p.getZ());
            }
        }
        if (best == null && distance < 60) {
            return flatNear(level, centre, distance * 3, half); // every patch is under water or somebody's: further out
        }
        return best != null ? best : ground(level, surface, centre).below();
    }

    /**
     * A building in its own coordinates: x east, y up, z south from an origin (its ground block, or its floor), every
     * block through the queue.
     */
    static final class Plan {
        final ServerLevel level;
        final BlockPos o;

        Plan(ServerLevel level, BlockPos origin) {
            this.level = level;
            this.o = origin.immutable();
        }

        BlockPos at(int x, int y, int z) {
            return o.offset(x, y, z);
        }

        void set(int x, int y, int z, BlockState s) {
            later(level, o.offset(x, y, z), s);
        }

        void set(int x, int y, int z, BlockState s, CompoundTag tile) {
            later(level, o.offset(x, y, z), s, tile);
        }

        void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
            box(level, o.offset(x0, y0, z0), o.offset(x1, y1, z1), s);
        }

        void walls(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
            Builds.walls(level, o.offset(x0, y0, z0), o.offset(x1, y1, z1), s);
        }

        /** A door, both halves, opening toward {@code facing}. */
        void door(int x, int y, int z, Block door, Direction facing) {
            BlockState d = door.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
            set(x, y, z, d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            set(x, y + 1, z, d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        }

        /** Ground under the whole footprint: {@code s} from {@code y} down to wherever the land is (no floating corners). */
        void footing(int x0, int z0, int x1, int z1, int y, BlockState s) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    for (int d = 1; d <= 16; d++) {
                        BlockPos p = o.offset(x, y - d, z);
                        BlockState here = level.getBlockState(p);
                        if (!here.canBeReplaced() && here.getFluidState().isEmpty()) {
                            break;
                        }
                        later(level, p, s);
                    }
                }
            }
        }
    }

    /** A stair: its tall back toward {@code back} (a chair or a sofa seat faces the other way). */
    static BlockState stair(Block b, Direction back) {
        return b.defaultBlockState().setValue(StairBlock.FACING, back);
    }

    static BlockState stairTop(Block b, Direction back) {
        return stair(b, back).setValue(StairBlock.HALF, Half.TOP);
    }

    static BlockState slab(Block b, boolean top) {
        return b.defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    /** A trapdoor: shut in the top half of its block it's a desk or table top; open, it stands against its block's far side. */
    static BlockState trapdoor(Block b, Direction facing, boolean open, boolean top) {
        return b.defaultBlockState().setValue(TrapDoorBlock.FACING, facing).setValue(TrapDoorBlock.OPEN, open)
                .setValue(TrapDoorBlock.HALF, top ? Half.TOP : Half.BOTTOM);
    }

    static BlockState lantern(boolean hanging) {
        return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, hanging);
    }

    /** A sign's text: up to four lines, glowing in {@code color}. */
    static CompoundTag signText(DyeColor color, String... lines) {
        ListTag messages = new ListTag();
        for (int i = 0; i < 4; i++) {
            messages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(i < lines.length ? lines[i] : ""))));
        }
        CompoundTag front = new CompoundTag();
        front.put("messages", messages);
        front.putString("color", color.getName());
        front.putBoolean("has_glowing_text", true);
        CompoundTag tag = new CompoundTag();
        tag.put("front_text", front);
        return tag;
    }

    static BlockState wallSign(Block sign, Direction facing) {
        return sign.defaultBlockState().setValue(WallSignBlock.FACING, facing);
    }

    /** An item frame hung at {@code at} on the wall behind it, facing {@code facing}, holding {@code item} (once the walls are up). */
    static void frame(ServerLevel level, BlockPos at, Direction facing, ItemStack item) {
        afterBuild(() -> {
            net.minecraft.world.entity.decoration.ItemFrame f = new net.minecraft.world.entity.decoration.ItemFrame(level, at, facing);
            f.setItem(item, false);
            if (f.survives()) {
                level.addFreshEntity(f);
            }
        });
    }

    /** Runs once everything queued so far is built (people move in when the floors exist). */
    static void afterBuild(Runnable run) {
        AFTER.add(run);
    }

    private static final List<Runnable> AFTER = new ArrayList<>();

    static void later(ServerLevel level, BlockPos pos, BlockState state) {
        QUEUE.add(new Placement(level.dimension(), pos.immutable(), state, null));
    }

    static void later(ServerLevel level, BlockPos pos, BlockState state, CompoundTag tile) {
        QUEUE.add(new Placement(level.dimension(), pos.immutable(), state, tile));
    }

    /** Every block of the box from {@code a} to {@code b} (inclusive), through the queue. */
    static void box(ServerLevel level, BlockPos a, BlockPos b, BlockState state) {
        for (BlockPos p : BlockPos.betweenClosed(a, b)) {
            later(level, p, state);
        }
    }

    /** Only the shell of the box. */
    static void walls(ServerLevel level, BlockPos a, BlockPos b, BlockState state) {
        int x0 = Math.min(a.getX(), b.getX()), x1 = Math.max(a.getX(), b.getX()), z0 = Math.min(a.getZ(), b.getZ()), z1 = Math.max(a.getZ(), b.getZ());
        for (BlockPos p : BlockPos.betweenClosed(a, b)) {
            if (p.getX() == x0 || p.getX() == x1 || p.getZ() == z0 || p.getZ() == z1) {
                later(level, p, state);
            }
        }
    }

    /**
     * A row of the story's people in every stance, facing the player, 4 blocks ahead (a debug command, and the
     * autotest's look at the poses): chair, desk, L on his chair, Near on the floor, Mello on a sofa, hands behind,
     * arms folded, then L and a detective walking up and down.
     */
    static void gallery(net.minecraft.server.level.ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos b = player.blockPosition().offset(-13, 0, 4);
        box(level, b.offset(-2, -1, -4), b.offset(28, -1, 9), Blocks.SMOOTH_STONE.defaultBlockState());
        box(level, b.offset(-2, 0, -4), b.offset(28, 4, 9), Blocks.AIR.defaultBlockState());
        Object[][] row = { // role, variant, stance, x
                {HumanEntity.Role.DETECTIVE, 0, HumanEntity.Stance.CHAIR, 0}, {HumanEntity.Role.DETECTIVE, 1, HumanEntity.Stance.DESK, 3},
                {HumanEntity.Role.L, 0, HumanEntity.Stance.PERCH, 6}, {HumanEntity.Role.NEAR, 0, HumanEntity.Stance.FLOOR, 9},
                {HumanEntity.Role.MELLO, 0, HumanEntity.Stance.SOFA, 12}, {HumanEntity.Role.WATARI, 0, HumanEntity.Stance.BEHIND, 15},
                {HumanEntity.Role.MAFIA, 0, HumanEntity.Stance.CROSSED, 18}};
        BlockState chair = Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.SOUTH);
        afterBuild(() -> {
            for (Object[] r : row) {
                HumanEntity.Stance stance = (HumanEntity.Stance) r[2];
                BlockPos at = b.offset((int) r[3], 0, 0);
                if (stance.seated() && stance != HumanEntity.Stance.FLOOR) {
                    level.setBlockAndUpdate(at, chair);
                }
                if (stance == HumanEntity.Stance.DESK) {
                    level.setBlockAndUpdate(at.north(), Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP));
                }
                Investigation.spawn(level, (HumanEntity.Role) r[0], (int) r[1], net.minecraft.world.phys.Vec3.atBottomCenterOf(at), null, null)
                        .posts(List.of(new HumanEntity.Post(at, Direction.NORTH, stance, 600)));
            }
            for (int i = 0; i < 2; i++) { // walking: up and down, a few seconds at each end
                BlockPos at = b.offset(22 + i * 3, 0, -2);
                Investigation.spawn(level, i == 0 ? HumanEntity.Role.L : HumanEntity.Role.DETECTIVE, 2, net.minecraft.world.phys.Vec3.atBottomCenterOf(at), null, null)
                        .posts(List.of(new HumanEntity.Post(at, Direction.NORTH, HumanEntity.Stance.STAND, 4),
                                new HumanEntity.Post(at.south(8), Direction.SOUTH, HumanEntity.Stance.STAND, 4)));
            }
        });
    }

    /** A chest's contents (for {@link #later} with a tile): one written book, its pages from lang keys. */
    static CompoundTag bookChest(String title, String author, String... pageKeys) {
        CompoundTag book = new CompoundTag();
        book.putString("title", title);
        book.putString("author", author);
        ListTag pages = new ListTag();
        for (String key : pageKeys) {
            pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.translatable(key))));
        }
        book.put("pages", pages);
        CompoundTag item = new CompoundTag();
        item.putByte("Slot", (byte) 13);
        item.putString("id", "minecraft:written_book");
        item.putByte("Count", (byte) 1);
        item.put("tag", book);
        ListTag items = new ListTag();
        items.add(item);
        CompoundTag tag = new CompoundTag();
        tag.put("Items", items);
        return tag;
    }
}
