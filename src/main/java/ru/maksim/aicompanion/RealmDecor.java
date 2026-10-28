package ru.maksim.aicompanion;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The realm's details: nothing to use, only to look at, what the gods of death leave lying about in their waste.
 * A giant Death Note leaning on a rock, the hourglass of a human life, the dust two gods turned to for saving a human,
 * a graveyard of gods, scythes taller than a house, cages hanging from the sky, black pools with drowned lights, two
 * obelisks with burning eyes behind the King, a ruined colonnade, Sidoh's pages floating over his hole, a cairn of
 * skulls, a circle of standing stones. Blocks go up with the realm (or into an older one, once: the sculk mark);
 * the leaning and floating things are display entities, put in once a dreamer has the realm loaded.
 */
final class RealmDecor {
    private static final int G = Realm.GROUND;
    static final BlockPos MARK = new BlockPos(1, G - 2, 0); // sculk: this realm has its details; a catalyst beside it: and its displays
    private static final String TAG = "aicompanion_realm_decor";
    private static final BlockState AIR = Blocks.AIR.defaultBlockState(), BRICKS = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(),
            CRACKED = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), CHISELED = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState(),
            STONE = Blocks.POLISHED_BLACKSTONE.defaultBlockState(), BONE = Blocks.BONE_BLOCK.defaultBlockState(),
            CHAIN = Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y);

    private RealmDecor() {
    }

    static boolean built(ServerLevel level) {
        return level.getBlockState(MARK).is(Blocks.SCULK);
    }

    /** The blocks, through the build queue like the rest of the realm. */
    static void build(ServerLevel level) {
        Random rnd = new Random(13);
        hourglass(level, new BlockPos(28, G, 22));
        dust(level, new BlockPos(-20, G, 16));
        graveyard(level, new BlockPos(40, G, -2), rnd);
        for (int[] c : new int[][]{{-13, 16, 20}, {13, 13, 21}, {-13, 12, 29}, {13, 15, 28}}) {
            cage(level, new BlockPos(c[0], G + c[1], c[2]));
        }
        pool(level, new BlockPos(-10, G, -32), rnd);
        obelisk(level, new BlockPos(14, G, 62));
        obelisk(level, new BlockPos(-14, G, 62));
        colonnade(level, new BlockPos(-34, G, 46), rnd);
        cairn(level, new BlockPos(30, G, -42));
        circle(level, new BlockPos(-62, G, -52));
        rock(level, new BlockPos(-61, G, -2), rnd);
        Builds.later(level, MARK, Blocks.SCULK.defaultBlockState());
    }

    // ================================================================== blocks

    private static void set(ServerLevel level, BlockPos p, BlockState s) {
        Builds.later(level, p, s);
    }

    private static void clear(ServerLevel level, BlockPos a, BlockPos b) {
        Builds.box(level, a, b, AIR);
    }

    private static BlockState candles(Block candle, int n) {
        return candle.defaultBlockState().setValue(CandleBlock.CANDLES, n).setValue(CandleBlock.LIT, true);
    }

    /** A skull turned toward the plaza (where dreamers stand). */
    private static BlockState skull(Block skull, BlockPos at) {
        double yaw = Math.toDegrees(Math.atan2(at.getX(), -at.getZ()));
        return skull.defaultBlockState().setValue(SkullBlock.ROTATION, (int) Math.round(yaw / 22.5) & 15);
    }

    /** The hourglass of a human life: red sand run mostly down, glass bulbs between black pillars, hung from the sky. */
    private static void hourglass(ServerLevel level, BlockPos c) {
        clear(level, c.offset(-3, 0, -3), c.offset(3, 12, 3));
        Builds.box(level, c.offset(-3, -1, -3), c.offset(3, -1, 3), BRICKS); // a footing in the ash
        Builds.box(level, c.offset(-3, 0, -3), c.offset(3, 0, 3), BRICKS);
        Builds.box(level, c.offset(-2, 1, -2), c.offset(2, 1, 2), STONE);
        Builds.box(level, c.offset(-2, 10, -2), c.offset(2, 10, 2), STONE);
        for (int dx : new int[]{-2, 2}) {
            for (int dz : new int[]{-2, 2}) {
                set(level, c.offset(dx, 1, dz), CHISELED);
                set(level, c.offset(dx, 10, dz), CHISELED);
                Builds.box(level, c.offset(dx, 2, dz), c.offset(dx, 9, dz), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
                set(level, c.offset(dx, 11, dz), Blocks.SOUL_LANTERN.defaultBlockState());
            }
        }
        BlockState glass = Blocks.GLASS.defaultBlockState(), sand = Blocks.RED_SAND.defaultBlockState(), packed = Blocks.SMOOTH_RED_SANDSTONE.defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean ring = dx != 0 || dz != 0;
                set(level, c.offset(dx, 2, dz), sand); // the lower bulb: most of a life already run down
                set(level, c.offset(dx, 3, dz), ring ? glass : sand);
                set(level, c.offset(dx, 4, dz), ring ? glass : AIR);
                set(level, c.offset(dx, 7, dz), packed); // the upper bulb: what's left
                set(level, c.offset(dx, 8, dz), ring ? glass : AIR);
                set(level, c.offset(dx, 9, dz), ring ? glass : AIR);
            }
        }
        set(level, c.offset(0, 5, 0), Blocks.TINTED_GLASS.defaultBlockState()); // the neck, the last grains in it
        set(level, c.offset(0, 6, 0), Blocks.TINTED_GLASS.defaultBlockState());
        for (int y = c.getY() + 11; y < 127; y++) {
            set(level, c.atY(y), CHAIN);
        }
    }

    /** Two gods of death turned to dust for saving a human (Gelus, Rem): two grey heaps, candles, black roses. */
    private static void dust(ServerLevel level, BlockPos c) {
        clear(level, c.offset(-8, 0, -7), c.offset(8, 6, 5));
        BlockState grey = Blocks.LIGHT_GRAY_CONCRETE_POWDER.defaultBlockState(), pale = Blocks.WHITE_CONCRETE_POWDER.defaultBlockState();
        for (int s : new int[]{-2, 2}) {
            BlockPos h = c.offset(s, 0, 0);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    set(level, h.offset(dx, 0, dz), Math.abs(dx) + Math.abs(dz) == 2 ? pale : grey);
                }
            }
            set(level, h.above(), grey);
            set(level, h.offset(0, 0, 2), candles(Blocks.WHITE_CANDLE, 3));
            set(level, h.offset(s, 0, -2), candles(Blocks.WHITE_CANDLE, 1));
        }
        for (int dx : new int[]{-4, 0, 4}) { // black roses where the dust fell
            set(level, c.offset(dx, -1, -2), Blocks.SOUL_SOIL.defaultBlockState());
            set(level, c.offset(dx, 0, -2), Blocks.WITHER_ROSE.defaultBlockState());
        }
    }

    /** The graveyard of gods: black headstones in rows, red candles, skulls, an iron fence with a gap, a dead tree. */
    private static void graveyard(ServerLevel level, BlockPos c, Random rnd) {
        clear(level, c.offset(-6, 0, -5), c.offset(6, 6, 5));
        BlockState bars = Blocks.IRON_BARS.defaultBlockState();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                boolean edge = Math.abs(dx) == 5 || Math.abs(dz) == 4;
                if (edge && !(dx == -5 && Math.abs(dz) <= 1)) { // the gate faces the plaza
                    set(level, c.offset(dx, 0, dz), bars);
                }
                if (!edge && rnd.nextInt(3) == 0) {
                    set(level, c.offset(dx, -1, dz), Blocks.SOUL_SOIL.defaultBlockState());
                }
            }
        }
        for (int dz : new int[]{-2, 0, 2}) {
            for (int dx : new int[]{-2, 0, 2, 4}) {
                BlockPos g = c.offset(dx, 0, dz);
                int kind = rnd.nextInt(4);
                set(level, g, kind == 0 ? CRACKED : BRICKS);
                set(level, g.above(), kind == 1 ? Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState() : CHISELED);
                if (kind == 2) {
                    set(level, g.above(2), skull(Blocks.WITHER_SKELETON_SKULL, g));
                } else if (kind == 3) {
                    set(level, g.above(2), Blocks.POLISHED_BLACKSTONE_BUTTON.defaultBlockState()
                            .setValue(net.minecraft.world.level.block.ButtonBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
                }
                set(level, g.west(), rnd.nextInt(3) == 0 ? Blocks.WITHER_ROSE.defaultBlockState() : candles(Blocks.RED_CANDLE, 1 + rnd.nextInt(3)));
                if (!level.getBlockState(g.west().below()).isSolid()) {
                    set(level, g.west().below(), Blocks.SOUL_SOIL.defaultBlockState());
                }
            }
        }
        BlockPos tree = c.offset(4, 0, 3); // a dead tree in the corner
        Builds.box(level, tree, tree.above(3), Blocks.DARK_OAK_LOG.defaultBlockState());
        BlockState fence = Blocks.DARK_OAK_FENCE.defaultBlockState();
        set(level, tree.offset(-1, 3, 0), fence);
        set(level, tree.offset(-2, 3, 0), fence);
        set(level, tree.offset(-2, 4, 0), fence);
        set(level, tree.offset(0, 4, 0), fence);
        set(level, tree.offset(0, 4, -1), fence);
        set(level, tree.offset(0, 5, -1), fence);
        BlockPos post = c.offset(-6, 0, 2); // a lantern by the gate
        set(level, post, Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
        set(level, post.above(), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
        set(level, post.above(2), Blocks.SOUL_LANTERN.defaultBlockState());
    }

    /** A cage hanging out of the haze on its chain: whoever it held is only a skull now. */
    private static void cage(ServerLevel level, BlockPos top) {
        BlockState slab = Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockState bars = Blocks.IRON_BARS.defaultBlockState();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(level, top.offset(dx, -4, dz), slab);
                set(level, top.offset(dx, 0, dz), slab);
                if (dx != 0 || dz != 0) {
                    for (int y = -3; y <= -1; y++) {
                        set(level, top.offset(dx, y, dz), bars);
                    }
                }
            }
        }
        set(level, top.offset(0, -3, 0), skull(Blocks.SKELETON_SKULL, top));
        set(level, top.offset(0, -1, 0), CHAIN);
        for (int y = top.getY() + 1; y < 127; y++) {
            set(level, top.atY(y), CHAIN);
        }
    }

    /** A black pool, still as glass: lights glowing under the water like drowned candles, dry spikes and dead brush round it. */
    private static void pool(ServerLevel level, BlockPos c, Random rnd) {
        clear(level, c.offset(-7, 0, -6), c.offset(7, 3, 6));
        BlockState water = Blocks.WATER.defaultBlockState();
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                double e = Math.pow(dx / 5.6, 2) + Math.pow(dz / 3.8, 2) + (Math.sin(dx * 1.7 + dz) * 0.12);
                BlockPos p = c.offset(dx, -1, dz);
                if (e < 1.0) {
                    set(level, p.below(), Blocks.BLACK_CONCRETE.defaultBlockState());
                    set(level, p, rnd.nextInt(7) == 0 ? Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + rnd.nextInt(3))
                            .setValue(SeaPickleBlock.WATERLOGGED, true) : water);
                } else if (e < 1.45) {
                    set(level, p, Blocks.SMOOTH_BASALT.defaultBlockState());
                    if (rnd.nextInt(4) == 0) {
                        set(level, p.above(), Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.UP)
                                .setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP));
                    } else if (rnd.nextInt(4) == 0) {
                        set(level, p, Blocks.COARSE_DIRT.defaultBlockState());
                        set(level, p.above(), Blocks.DEAD_BUSH.defaultBlockState());
                    }
                }
            }
        }
    }

    /** An obelisk behind the King, its burning eye turned on the plaza (the eyes of a god of death). */
    private static void obelisk(ServerLevel level, BlockPos c) {
        clear(level, c.offset(-3, 0, -3), c.offset(3, 22, 3));
        Builds.box(level, c.offset(-2, -1, -2), c.offset(2, 0, 2), BRICKS);
        Builds.box(level, c.offset(-1, 1, -1), c.offset(1, 15, 1), BRICKS);
        for (int y = 1; y <= 15; y += 3) {
            set(level, c.offset(1, y, 1), CRACKED);
            set(level, c.offset(-1, y + 1, -1), CRACKED);
        }
        for (Direction d : Direction.Plane.HORIZONTAL) { // the top narrows to a point
            set(level, c.relative(d).above(16), STONE);
        }
        set(level, c.above(16), STONE);
        set(level, c.above(17), CHISELED);
        set(level, c.above(18), Blocks.POLISHED_BLACKSTONE_WALL.defaultBlockState());
        set(level, c.above(19), Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.UP));
        BlockState lid = Blocks.RED_NETHER_BRICKS.defaultBlockState(), iris = Blocks.MAGMA_BLOCK.defaultBlockState();
        BlockPos e = c.offset(0, 12, -2); // the eye stands out of the face toward the plaza
        for (int dx = -1; dx <= 1; dx++) {
            set(level, e.offset(dx, 1, 0), lid);
            set(level, e.offset(dx, -1, 0), lid);
        }
        set(level, e.offset(-2, 0, 0), lid);
        set(level, e.offset(2, 0, 0), lid);
        set(level, e.offset(-1, 0, 0), iris);
        set(level, e.offset(1, 0, 0), iris);
        set(level, e, Blocks.CRYING_OBSIDIAN.defaultBlockState());
    }

    /** A ruined colonnade: pointed arches of black stone, the last one fallen, lanterns hanging from the whole ones. */
    private static void colonnade(ServerLevel level, BlockPos c, Random rnd) {
        clear(level, c.offset(-8, 0, -2), c.offset(8, 9, 2));
        int[] pillars = {-6, -2, 2, 6};
        for (int i = 0; i < pillars.length; i++) {
            int h = i == 3 ? 3 : 5; // the last pillar broke off
            for (int y = 0; y <= h; y++) {
                set(level, c.offset(pillars[i], y, 0), y == 0 ? CHISELED : rnd.nextInt(5) == 0 ? CRACKED : BRICKS);
            }
        }
        for (int a = 0; a < 3; a++) {
            int xa = pillars[a], xb = pillars[a + 1];
            boolean broken = a == 2;
            set(level, c.offset(xa + 1, 6, 0), Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.WEST).setValue(StairBlock.HALF, Half.TOP));
            set(level, c.offset(xa, 6, 0), BRICKS);
            set(level, c.offset(xa + 1, 7, 0), BRICKS);
            if (!broken) {
                set(level, c.offset(xb, 6, 0), BRICKS);
                set(level, c.offset(xb - 1, 6, 0), Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState()
                        .setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.TOP));
                set(level, c.offset(xb - 1, 7, 0), BRICKS);
                set(level, c.offset(xa + 2, 7, 0), CHISELED);
                set(level, c.offset(xa + 2, 8, 0), BRICKS);
                set(level, c.offset(xa + 2, 6, 0), CHAIN);
                set(level, c.offset(xa + 2, 5, 0), CHAIN);
                set(level, c.offset(xa + 2, 4, 0), Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            }
        }
        BlockPos fall = c.offset(5, 0, 2); // what fell: blocks and halves of blocks in the ash
        set(level, fall, CRACKED);
        set(level, fall.offset(1, 0, 1), BRICKS);
        set(level, fall.offset(-1, 0, 1), Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState());
        set(level, fall.offset(2, 0, -1), Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
        set(level, fall.offset(0, 1, 0), Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.defaultBlockState());
    }

    /** A cairn of bone and skulls, candles burning on it, a blue fire smoking at its foot. */
    private static void cairn(ServerLevel level, BlockPos c) {
        clear(level, c.offset(-4, 0, -4), c.offset(4, 5, 4));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(level, c.offset(dx, 0, dz), BONE);
                if (Math.abs(dx) + Math.abs(dz) <= 1) {
                    set(level, c.offset(dx, 1, dz), BONE);
                } else {
                    set(level, c.offset(dx, 1, dz), skull(Blocks.SKELETON_SKULL, c.offset(dx, 1, dz)));
                }
            }
        }
        set(level, c.above(2), BONE);
        set(level, c.above(3), skull(Blocks.WITHER_SKELETON_SKULL, c));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            set(level, c.relative(d).above(2), candles(d.getAxis() == Direction.Axis.X ? Blocks.RED_CANDLE : Blocks.BLACK_CANDLE, 2 + d.ordinal() % 2));
            set(level, c.relative(d, 2), skull(Blocks.SKELETON_SKULL, c.relative(d, 2)));
        }
        set(level, c.offset(3, 0, 1), Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
    }

    /** Standing stones in a ring round a black altar with a cold fire: where the gods of death once sat in council. */
    private static void circle(ServerLevel level, BlockPos c) {
        clear(level, c.offset(-8, 0, -8), c.offset(8, 8, 8));
        for (int i = 0; i < 7; i++) {
            double a = i * Math.PI * 2 / 7;
            BlockPos s = c.offset((int) Math.round(Math.sin(a) * 6), 0, (int) Math.round(Math.cos(a) * 6));
            int h = 3 + i % 3;
            for (int y = 0; y < h; y++) {
                set(level, s.above(y), y == 0 ? Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState() : Blocks.DEEPSLATE_TILES.defaultBlockState());
            }
            set(level, s.above(h), Blocks.CHISELED_DEEPSLATE.defaultBlockState());
            if (i % 2 == 0) {
                set(level, s.above(h + 1), skull(Blocks.WITHER_SKELETON_SKULL, s.subtract(c)));
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(level, c.offset(dx, -1, dz), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            }
        }
        set(level, c, Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
    }

    /** The rock the giant notebook leans on. */
    private static void rock(ServerLevel level, BlockPos c, Random rnd) {
        clear(level, c.offset(-3, 0, -6), c.offset(10, 12, 6));
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-2, 0, -5), c.offset(1, 4, 5))) {
            double dx = p.getX() - c.getX() + 0.5, dy = p.getY() - c.getY(), dz = p.getZ() - c.getZ();
            if (dx * dx / 4.5 + dy * dy / 18 + dz * dz / 26 < 1 + rnd.nextDouble() * 0.3) {
                set(level, p, rnd.nextInt(3) == 0 ? Blocks.TUFF.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
            }
        }
    }

    // ================================================================== displays

    static void ornaments(ServerLevel level) {
        BlockPos done = MARK.east();
        if (level.getBlockState(done).is(Blocks.SCULK_CATALYST)
                || !level.getEntities((Entity) null, new AABB(-96, 0, -96, 96, 160, 96), e -> e.getTags().contains(TAG)).isEmpty()) {
            return;
        }
        for (BlockPos p : new BlockPos[]{new BlockPos(-58, G, -2), new BlockPos(15, G, 34), new BlockPos(-15, G, 34), new BlockPos(-30, G, -26), new BlockPos(-20, G, 16)}) {
            if (!level.isLoaded(p)) {
                return; // not all of it loaded yet: next dream
            }
        }
        notebook(level, new Vec3(-58.0, G - 0.3, -1.5));
        scythe(level, new Vec3(15.5, G, 34.5), 1);
        scythe(level, new Vec3(-14.5, G, 34.5), -1);
        pages(level, new Vec3(-29.5, G, -25.5));
        for (int s : new int[]{-2, 2}) { // the notebooks they left on their dust
            item(level, new Vec3(-19.5 + s, G + 2.03, 16.5), new ItemStack(AiCompanion.DEATH_NOTE.get()), 0.85F,
                    new Quaternionf().rotateY(s * 0.4F).rotateX((float) (-Math.PI / 2)));
        }
        Builds.later(level, done, Blocks.SCULK_CATALYST.defaultBlockState());
    }

    /** The giant Death Note leaning back on its rock, its title to the plaza; pages blown about its foot, a quill stuck in the ash. */
    private static void notebook(ServerLevel level, Vec3 pivot) {
        Quaternionf lean = new Quaternionf().rotateZ((float) Math.toRadians(14));
        BlockState black = Blocks.BLACK_CONCRETE.defaultBlockState(), paper = Blocks.WHITE_CONCRETE.defaultBlockState();
        part(level, pivot, lean, new Vector3f(0.15F, 5.0F, 0F), new Vector3f(0.3F, 10F, 7F), black);
        part(level, pivot, lean, new Vector3f(0.55F, 4.95F, 0.08F), new Vector3f(0.52F, 9.6F, 6.6F), paper);
        part(level, pivot, lean, new Vector3f(0.95F, 5.0F, 0F), new Vector3f(0.3F, 10F, 7F), black);
        part(level, pivot, lean, new Vector3f(0.55F, 5.0F, -3.56F), new Vector3f(1.1F, 10F, 0.24F), black);
        Vector3f title = new Vector3f(1.13F, 7.4F, 0F).rotate(lean);
        text(level, pivot.add(title.x, title.y, title.z), Component.literal("DEATH NOTE").withStyle(net.minecraft.ChatFormatting.BOLD),
                new Quaternionf(lean).rotateY((float) (Math.PI / 2)), 4.2F);
        Random rnd = new Random(5);
        for (int i = 0; i < 4; i++) { // loose giant pages on the ground before it
            Vec3 at = new Vec3(-54.5 + rnd.nextDouble() * 3, G + 0.03 + i * 0.02, -5 + rnd.nextDouble() * 7);
            Quaternionf rot = new Quaternionf().rotateY((float) (rnd.nextDouble() * Math.PI)).rotateX((float) ((rnd.nextDouble() - 0.5) * 0.15));
            box(level, at, paper, new Vector3f(2.2F, 0.04F, 3.0F), rot);
        }
        item(level, new Vec3(-55.0, G + 2.0, 2.2), new ItemStack(Items.FEATHER), 5.0F,
                new Quaternionf().rotateY((float) Math.toRadians(30)).rotateZ((float) Math.toRadians(135)));
    }

    /** A scythe taller than a house, stuck in the ash and leaning outward, its blade drooping toward the plaza. */
    private static void scythe(ServerLevel level, Vec3 base, int side) {
        Quaternionf tilt = new Quaternionf().rotateZ((float) Math.toRadians(-9 * side));
        BlockState shaft = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState(), blade = Blocks.NETHERITE_BLOCK.defaultBlockState();
        part(level, base, tilt, new Vector3f(0, 5.5F, 0), new Vector3f(0.32F, 11F, 0.32F), shaft);
        part(level, base, tilt, new Vector3f(0, 10.85F, -0.1F), new Vector3f(0.5F, 0.7F, 0.55F), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        // the blade reaches back over the road, flat to whoever walks it, its tip drooping: {height, reach, length, width, droop}
        float[][] segs = {{10.75F, 1.55F, 3.1F, 0.62F, 6}, {10.38F, 3.75F, 1.7F, 0.5F, 24}, {9.78F, 4.95F, 1.1F, 0.36F, 46}, {9.18F, 5.55F, 0.6F, 0.22F, 68}};
        for (float[] s : segs) {
            Quaternionf rot = new Quaternionf(tilt).rotateZ((float) Math.toRadians(s[4] * side));
            Vector3f c = new Vector3f(-side * s[1], s[0], 0).rotate(tilt);
            box(level, base.add(c.x, c.y, c.z), blade, new Vector3f(s[2], s[3], 0.09F), rot);
        }
    }

    /** Sidoh's lost pages, floating over his hole as if the wind there never quite let them fall. */
    private static void pages(ServerLevel level, Vec3 hole) {
        Random rnd = new Random(9);
        for (int i = 0; i < 16; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 2 + rnd.nextDouble() * 8;
            Vec3 at = hole.add(Math.sin(a) * r, 2.5 + rnd.nextDouble() * 8, Math.cos(a) * r);
            Quaternionf rot = new Quaternionf().rotateXYZ((float) (rnd.nextDouble() * 6.28), (float) (rnd.nextDouble() * 6.28), (float) (rnd.nextDouble() * 6.28));
            item(level, at, new ItemStack(AiCompanion.PAGE.get()), 0.9F + rnd.nextFloat() * 0.5F, rot);
        }
    }

    /** One piece of a bigger thing: its centre {@code c} in the thing's frame (from {@code pivot}, turned by {@code frame}). */
    private static void part(ServerLevel level, Vec3 pivot, Quaternionf frame, Vector3f c, Vector3f size, BlockState state) {
        Vector3f at = new Vector3f(c).rotate(frame);
        box(level, pivot.add(at.x, at.y, at.z), state, size, frame);
    }

    /** A block stretched to {@code size}, its centre at {@code c}, turned by {@code rot} about that centre. */
    private static void box(ServerLevel level, Vec3 c, BlockState state, Vector3f size, Quaternionf rot) {
        Vector3f t = new Vector3f(size).mul(0.5F).rotate(rot).negate();
        CompoundTag tag = new CompoundTag();
        tag.put("block_state", NbtUtils.writeBlockState(state));
        display(level, "minecraft:block_display", c, tag, rot, size, t);
    }

    private static void item(ServerLevel level, Vec3 c, ItemStack stack, float scale, Quaternionf rot) {
        CompoundTag tag = new CompoundTag();
        tag.put("item", stack.save(new CompoundTag()));
        tag.putString("item_display", "fixed");
        display(level, "minecraft:item_display", c, tag, rot, new Vector3f(scale), new Vector3f());
    }

    private static void text(ServerLevel level, Vec3 c, Component text, Quaternionf rot, float scale) {
        CompoundTag tag = new CompoundTag();
        tag.putString("text", Component.Serializer.toJson(text.copy().withStyle(net.minecraft.ChatFormatting.WHITE)));
        tag.putString("billboard", "fixed");
        tag.putString("alignment", "center");
        tag.putInt("background", 0);
        tag.putInt("line_width", 400);
        display(level, "minecraft:text_display", c, tag, rot, new Vector3f(scale), new Vector3f());
    }

    private static void display(ServerLevel level, String id, Vec3 at, CompoundTag tag, Quaternionf rot, Vector3f scale, Vector3f translation) {
        tag.putString("id", id);
        CompoundTag tr = new CompoundTag();
        tr.put("left_rotation", floats(rot.x, rot.y, rot.z, rot.w));
        tr.put("right_rotation", floats(0, 0, 0, 1));
        tr.put("translation", floats(translation.x, translation.y, translation.z));
        tr.put("scale", floats(scale.x, scale.y, scale.z));
        tag.put("transformation", tr);
        tag.putFloat("view_range", 3.0F); // seen across the waste, not only up close
        Entity e = EntityType.loadEntityRecursive(tag, level, ent -> {
            ent.moveTo(at.x, at.y, at.z, 0, 0);
            return ent;
        });
        if (e != null) {
            e.addTag(TAG);
            level.addFreshEntity(e);
        }
    }

    private static ListTag floats(float... v) {
        ListTag list = new ListTag();
        for (float f : v) {
            list.add(FloatTag.valueOf(f));
        }
        return list;
    }
}
