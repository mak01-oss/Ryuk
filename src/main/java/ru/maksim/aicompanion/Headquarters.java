package ru.maksim.aicompanion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The Task Force headquarters L had built for the Kira case: a glass tower (the manga's has 23 floors; this one has
 * 12), a metal check at the entrance, the main hall with its wall of monitors where L crouches on his chair among the
 * sweets, Watari's floor at the top with his files (L's true name is in them: only Watari knew it), a helipad on the
 * roof. L himself talks to the suspect through the LLM, with his own character, and his estimate moves with what he
 * hears.
 */
public final class Headquarters {
    private static final int FH = 5, R = 8, TOP = 6 * FH; // a storey every 5 blocks, walls at +-8, the roof at 30
    private static final Map<String, List<Llm.Msg>> TALKS = new HashMap<>(); // player + who they talk to
    private static final Map<UUID, Long> NEXT = new ConcurrentHashMap<>(); // when a player may next be answered (MAX_VALUE: being answered)
    private static final Map<UUID, Long> METAL_WARNED = new HashMap<>();
    private static final Pattern CHANGE = Pattern.compile("\"suspicion_change\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");

    private Headquarters() {
    }

    /** Stage 4: the tower goes up some 160 blocks away, then the Task Force moves in and L sends for the suspect. */
    static void raise(ServerPlayer player) {
        raise(player, null);
    }

    /** {@code at}: a fixed site (tests), else the flattest ground some 160 blocks away. */
    static void raise(ServerPlayer player, @Nullable BlockPos at) {
        Kira.Case c = Kira.of(player);
        if (c.flag("hq")) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos site = at != null ? at : site(level, player);
        c.flags.put("hq", site.asLong());
        Kira.get(player.server).setDirty();
        build(level, site);
        Builds.afterBuild(() -> people(level, player, site));
        player.sendSystemMessage(Component.literal("<Taro Matsui> ").withStyle(ChatFormatting.GRAY)
                .append(Component.translatable("aicompanion.hq.invite", site.getX(), site.getZ()).withStyle(ChatFormatting.WHITE)));
        Network.card(player, Network.Broadcast.CHAPTER, Component.translatable("aicompanion.card.hq"), Component.translatable("aicompanion.card.hq_text"), "", site);
        CompanionEntity ryuk = CompanionEntity.of(player);
        if (ryuk != null) {
            ryuk.remark(Config.tr("L построил штаб расследования (" + site.getX() + ", " + site.getZ() + ") и через полицейского пригласил туда твоего человека. Смелый ход: L хочет посмотреть Кире в лицо",
                    "L has put up his investigation headquarters (" + site.getX() + ", " + site.getZ() + ") and sent a policeman to invite your human there. A bold move: L wants to look Kira in the face"), "story", 0);
        }
    }

    /**
     * Who works here and where their day takes them: L on his chair before the screens (now and then the sweets
     * counter, the window, the sofa), Watari at his side or making tea, the three detectives at their desks, the screens,
     * the sofa, Matsuda at the reception downstairs and Soichiro up on the gallery.
     */
    private static void people(ServerLevel level, ServerPlayer player, BlockPos c) {
        HumanEntity.Stance perch = HumanEntity.Stance.PERCH, stand = HumanEntity.Stance.STAND, desk = HumanEntity.Stance.DESK,
                chair = HumanEntity.Stance.CHAIR, behind = HumanEntity.Stance.BEHIND;
        Direction n = Direction.NORTH, e = Direction.EAST, s = Direction.SOUTH, w = Direction.WEST;
        int h = FH + 1; // where one stands in the hall
        spawn(level, player, c, HumanEntity.Role.L, 0, c, post(c, 0, h, -2, n, perch, 300), post(c, 6, h, -2, e, stand, 30),
                post(c, -6, h, -5, w, stand, 25), post(c, -3, h, 7, n, perch, 60));
        spawn(level, player, c, HumanEntity.Role.WATARI, 0, c, post(c, -2, h, -2, n, behind, 150), post(c, 6, h, -3, e, stand, 40),
                post(c, 2, h, -1, n, behind, 60));
        spawn(level, player, c, HumanEntity.Role.DETECTIVE, 0, null, post(c, -5, h, 2, n, desk, 200), post(c, 0, 1, -2, s, chair, 90),
                post(c, 6, h, -4, e, stand, 30), post(c, -4, h, 7, n, chair, 60));
        spawn(level, player, c, HumanEntity.Role.DETECTIVE, 1, null, post(c, -3, h, 2, n, desk, 200), post(c, -3, h, -4, n, stand, 40),
                post(c, -2, h, 7, n, chair, 50));
        spawn(level, player, c, HumanEntity.Role.DETECTIVE, 2, null, post(c, -1, h, 2, n, desk, 200), post(c, 3, h, -4, n, stand, 40),
                post(c, -3, 2 * FH + 1, 6, n, stand, 40));
    }

    static HumanEntity.Post post(BlockPos c, int x, int y, int z, Direction facing, HumanEntity.Stance stance, int seconds) {
        return new HumanEntity.Post(c.offset(x, y, z), facing, stance, seconds);
    }

    static HumanEntity spawn(ServerLevel level, ServerPlayer player, BlockPos c, HumanEntity.Role role, int variant, @Nullable BlockPos home,
                             HumanEntity.Post... day) {
        return Investigation.spawn(level, role, variant, Vec3.atBottomCenterOf(day[0].at()), player, home).posts(java.util.List.of(day));
    }

    /** Once a second: the metal check beeps at whoever walks through it carrying metal. */
    static void tick(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        if (!c.flag("hq")) {
            return;
        }
        BlockPos hq = BlockPos.of(c.flags.get("hq"));
        if (!c.flag("hq_stairs") && player.serverLevel().isLoaded(hq.offset(5, 0, 4))) { // a tower raised before the fix: room for the head on every flight
            for (int k = 1; k <= 6; k++) {
                for (int x = 4; x <= 7; x++) {
                    BlockPos head = x <= 5 ? hq.offset(x, k * FH, 4) : hq.offset(x, k * FH + 1, 6); // over flight A's second step; off the landing onto flight B
                    if (k < 6 || x <= 5) {
                        player.serverLevel().setBlock(head, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
            c.flags.put("hq_stairs", player.server.overworld().getGameTime());
            Kira.get(player.server).setDirty();
        }
        BlockPos at = player.blockPosition();
        if (Math.abs(at.getX() - hq.getX()) <= 2 && at.getZ() - hq.getZ() >= 3 && at.getZ() - hq.getZ() <= 6
                && at.getY() - hq.getY() >= 1 && at.getY() - hq.getY() <= 3 && carriesMetal(player)
                && player.server.overworld().getGameTime() > METAL_WARNED.getOrDefault(player.getUUID(), 0L) + 600) {
            METAL_WARNED.put(player.getUUID(), player.server.overworld().getGameTime());
            player.serverLevel().playSound(null, at, Sounds.DETECTOR.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            player.sendSystemMessage(Component.translatable("aicompanion.hq.metal").withStyle(ChatFormatting.RED));
        }
    }

    private static boolean carriesMetal(ServerPlayer player) {
        return player.getInventory().hasAnyMatching(s -> s.getItem() instanceof TieredItem t && t.getTier() != Tiers.WOOD && t.getTier() != Tiers.STONE
                || s.getItem() instanceof ArmorItem a && a.getMaterial() != ArmorMaterials.LEATHER || s.is(Items.IRON_INGOT) || s.is(Items.GOLD_INGOT));
    }

    /** About 160 blocks off, on the flattest ground of eight tries, never on water. */
    private static BlockPos site(ServerLevel level, ServerPlayer player) {
        return site(level, player, 160, R + 3);
    }

    /** {@code distance} blocks off, the flattest ground for a footprint of {@code r} around the centre, never on water. */
    static BlockPos site(ServerLevel level, ServerPlayer player, int distance, int r) {
        BlockPos best = null;
        int bestSpread = Integer.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            int x = (int) (player.getX() + Math.sin(a) * distance), z = (int) (player.getZ() + Math.cos(a) * distance);
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            boolean water = false;
            for (int dx = -r; dx <= r; dx += r) {
                for (int dz = -r; dz <= r; dz += r) {
                    BlockPos top = Builds.ground(level, Heightmap.Types.WORLD_SURFACE, new BlockPos(x + dx, 0, z + dz));
                    water |= !level.getFluidState(top.below()).isEmpty();
                    min = Math.min(min, top.getY());
                    max = Math.max(max, top.getY());
                }
            }
            if (!water && max - min < bestSpread && !Builds.lived(level, new BlockPos(x, 0, z), r + 2)) {
                bestSpread = max - min;
                best = new BlockPos(x, min, z);
            }
        }
        if (best == null && distance < 400) {
            return site(level, player, distance * 2, r); // all under water or somebody's: further out
        }
        return best != null ? best : player.blockPosition().offset(distance, 0, 0);
    }

    // ------------------------------------------------------------------ the tower

    /**
     * Six storeys of glass on a plaza, 17 x 17: the lobby with the metal check (the only way in, then the stairs), the
     * main hall two storeys high (the wall of monitors, L's chair, the Task Force's desks, a gallery with the grand
     * stair up to it, a lounge under the gallery, the sweets counter), the war room, the living floor, Watari's floor
     * with the safe, the roof with the helipad. One stairwell in the south-east corner joins every floor: two flights
     * and a landing per storey, a doorway on each floor at its north end.
     */
    private static void build(ServerLevel level, BlockPos c) {
        Builds.Plan p = new Builds.Plan(level, c);
        BlockState air = Blocks.AIR.defaultBlockState(), white = Blocks.WHITE_CONCRETE.defaultBlockState(),
                gray = Blocks.GRAY_CONCRETE.defaultBlockState(), light = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(),
                glass = Blocks.LIGHT_BLUE_STAINED_GLASS_PANE.defaultBlockState(), lamp = Blocks.SEA_LANTERN.defaultBlockState();
        // the lot: cleared, levelled, a plaza round the tower
        p.fill(-11, 1, -11, 11, TOP + 8, 11, air);
        p.footing(-11, -11, 11, 11, 0, Blocks.STONE.defaultBlockState());
        p.fill(-11, 0, -11, 11, 0, 11, Blocks.POLISHED_ANDESITE.defaultBlockState());
        p.fill(-1, 0, 9, 1, 0, 11, Blocks.SMOOTH_STONE.defaultBlockState()); // the walk to the door
        for (int x : new int[]{-3, 3}) { // lamp posts by the walk
            p.fill(x, 1, 10, x, 2, 10, Blocks.DARK_OAK_FENCE.defaultBlockState());
            p.set(x, 3, 10, Builds.lantern(false));
        }
        for (int x = -7; x <= 7; x += 14) { // planters along the front
            p.fill(x - 1, 0, 10, x + 1, 0, 10, Blocks.GRASS_BLOCK.defaultBlockState());
            p.set(x, 1, 10, Blocks.AZALEA.defaultBlockState());
        }
        // floors and ceilings (the lights sit in the slab; the floor above hides them under carpet); the hall has no y=10 floor
        for (int k = 1; k <= 6; k++) {
            int y = k * FH;
            if (k == 2) {
                p.walls(-R, y, -R, R, y, R, gray); // only the ring and the gallery (hall)
                continue;
            }
            p.fill(-R, y, -R, R, y, R, switch (k) {
                case 1 -> gray; // under the hall's grey carpet
                case 3 -> light; // the war room's
                case 5 -> Blocks.DARK_OAK_PLANKS.defaultBlockState(); // Watari's floor is wood
                case 6 -> gray;
                default -> white;
            });
            if (k < 6) {
                for (int x = -4; x <= 4; x += 4) {
                    for (int z = -4; z <= 4; z += 4) {
                        if (!(x >= 3 && z >= 1)) {
                            p.set(x, y, z, lamp);
                        }
                    }
                }
            }
        }
        // the shell: pillars every four blocks, glass between, a band under each floor
        for (int k = 0; k < 6; k++) {
            int y = k * FH;
            for (int dy = 1; dy < FH; dy++) {
                for (int i = -R; i <= R; i++) {
                    for (int[] w : new int[][]{{i, -R}, {i, R}, {-R, i}, {R, i}}) {
                        boolean post = i % 4 == 0;
                        boolean hallNorth = w[1] == -R && (k == 1 || k == 2); // the monitor wall's back
                        BlockState s = post || hallNorth ? gray : dy == FH - 1 ? gray : dy == 1 && k > 0 ? light : glass;
                        p.set(w[0], y + dy, w[1], s);
                    }
                }
            }
        }
        lobby(p);
        hall(p);
        warRoom(p);
        quarters(p);
        watari(p);
        roof(p);
        for (int k = 0; k < 6; k++) { // last: the stairwell cuts its way through every floor
            stairwell(p, k);
        }
    }

    /**
     * Storey {@code k} of the stairwell (x 4..7, z 2..7 inside its walls): from the doorway at the north end, flight A
     * goes up south along x 4..5 to the landing at the south end, flight B comes back north along x 6..7 and arrives at
     * the next floor's doorway. The next floor's slab is open over the landing.
     */
    private static void stairwell(Builds.Plan p, int k) {
        BlockState wall = Blocks.WHITE_CONCRETE.defaultBlockState(), air = Blocks.AIR.defaultBlockState(),
                step = Blocks.QUARTZ_STAIRS.defaultBlockState(), landing = Blocks.POLISHED_ANDESITE.defaultBlockState();
        int y0 = k * FH + 1; // where one stands on this floor
        int top = k == 1 ? FH * 2 - 1 : FH - 1; // the hall's stairwell walls go up both storeys
        if (k != 2) {
            p.fill(3, y0, 1, 7, y0 + top - 1, 1, wall);
            p.fill(3, y0, 1, 3, y0 + top - 1, 7, wall);
            p.fill(4, y0, 2, 7, y0 + top - 1, 7, air);
        }
        if (k == 1) { // the gallery storey's floor inside the stairwell, cleared with the hall's double-height walls
            p.fill(4, 2 * FH, 2, 7, 2 * FH, 4, wall);
        }
        p.fill(3, y0, 2, 3, y0 + 1, 2, air); // the doorway
        p.set(3, y0 + 2, 2, Blocks.SMOOTH_QUARTZ.defaultBlockState()); // its lintel
        p.set(7, y0 + 3, 7, Builds.lantern(false)); // a light on the landing, in its corner
        p.fill(4, y0, 4, 5, y0, 4, wall); // under the flights and the landing
        p.fill(4, y0, 5, 5, y0 + 1, 5, wall);
        p.fill(4, y0, 6, 7, y0 + 1, 7, wall);
        p.fill(6, y0, 6, 7, y0, 6, air); // the flight below steps off its landing here: room for the head
        for (int x = 4; x <= 5; x++) {
            p.set(x, y0, 3, Builds.stair(Blocks.QUARTZ_STAIRS, Direction.SOUTH));
            p.set(x, y0 + 1, 4, Builds.stair(Blocks.QUARTZ_STAIRS, Direction.SOUTH));
            p.set(x, y0 + 2, 5, Builds.stair(Blocks.QUARTZ_STAIRS, Direction.SOUTH));
        }
        p.fill(4, y0 + 2, 6, 7, y0 + 2, 7, landing);
        p.fill(4, y0 + 4, 5, 7, y0 + 4, 7, air); // headroom: the next floor is open over the landing
        p.fill(4, y0 + 4, 4, 5, y0 + 4, 4, air); // and over flight A's second step: stepping up from it, the head rises past the slab
        p.fill(4, y0 + 3, 5, 5, y0 + 3, 5, air);
        for (int x = 6; x <= 7; x++) {
            p.set(x, y0 + 3, 5, Builds.stair(Blocks.QUARTZ_STAIRS, Direction.NORTH));
            p.set(x, y0 + 4, 4, Builds.stair(Blocks.QUARTZ_STAIRS, Direction.NORTH));
        }
        p.fill(6, y0 + 4, 5, 7, y0 + 5, 5, air);
    }

    /** Ground floor: the door, the lockers for metal, the check arch (the only way on), the reception, the waiting chairs. */
    private static void lobby(Builds.Plan p) {
        p.fill(-7, 0, -7, 7, 0, 7, Blocks.POLISHED_ANDESITE.defaultBlockState());
        p.fill(-1, 0, -6, 1, 0, 7, Blocks.POLISHED_DEEPSLATE.defaultBlockState()); // the runner from the door
        p.fill(-1, 1, R, 1, 3, R, Blocks.AIR.defaultBlockState()); // the doorway, three wide
        p.fill(-2, 4, R + 1, 2, 4, R + 2, Builds.slab(Blocks.SMOOTH_STONE_SLAB, true)); // the canopy
        // the metal check: an arch at z 5 and a glass screen either side, so nobody gets round it
        for (int y = 1; y <= 3; y++) {
            p.set(-2, y, 5, Blocks.IRON_BLOCK.defaultBlockState());
            p.set(2, y, 5, Blocks.IRON_BLOCK.defaultBlockState());
        }
        p.fill(-2, 4, 5, 2, 4, 5, Blocks.IRON_BLOCK.defaultBlockState());
        p.set(0, 4, 5, Blocks.REDSTONE_LAMP.defaultBlockState());
        p.fill(-7, 1, 5, -3, 3, 5, Blocks.GLASS_PANE.defaultBlockState());
        for (int x = -6; x <= -3; x++) { // the lockers, by the door
            p.set(x, 1, 6, Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.SOUTH));
            p.set(x, 2, 6, Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.SOUTH));
        }
        p.set(-5, 3, 6, Builds.wallSign(Blocks.SPRUCE_WALL_SIGN, Direction.SOUTH), Builds.signText(net.minecraft.world.item.DyeColor.WHITE,
                Config.tr("Металл", "Metal"), Config.tr("оставьте", "leave it"), Config.tr("здесь", "here")));
        p.set(2, 1, 7, Blocks.POTTED_BAMBOO.defaultBlockState());
        // the reception, facing the door; behind it the lifts (out of order, of course)
        p.fill(-2, 1, -1, 2, 1, -1, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        p.fill(-2, 2, -1, 2, 2, -1, Builds.slab(Blocks.SMOOTH_QUARTZ_SLAB, false));
        p.set(-1, 2, -1, Builds.trapdoor(Blocks.DARK_OAK_TRAPDOOR, Direction.NORTH, true, false)); // the screen, toward the clerk
        p.set(0, 1, -2, Builds.stair(Blocks.DARK_OAK_STAIRS, Direction.NORTH)); // the clerk's chair
        p.set(-3, 1, -1, Blocks.POTTED_FERN.defaultBlockState());
        p.fill(-3, 1, -R, 3, 4, -R, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState()); // the lift wall
        for (int x : new int[]{-1, 0}) {
            p.door(x, 1, -R + 1, Blocks.IRON_DOOR, Direction.SOUTH);
        }
        p.set(1, 2, -R + 1, Blocks.STONE_BUTTON.defaultBlockState().setValue(net.minecraft.world.level.block.ButtonBlock.FACING, Direction.SOUTH)
                .setValue(net.minecraft.world.level.block.ButtonBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL));
        p.fill(-2, 3, -R + 1, 2, 3, -R + 1, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        // the waiting corner: two armchairs and a low table
        p.set(-6, 1, -4, Builds.stair(Blocks.QUARTZ_STAIRS, Direction.NORTH));
        p.set(-6, 1, -2, Builds.stair(Blocks.QUARTZ_STAIRS, Direction.SOUTH));
        p.set(-6, 1, -3, Builds.slab(Blocks.DARK_OAK_SLAB, true));
        p.set(-6, 2, -3, Blocks.POTTED_WHITE_TULIP.defaultBlockState());
        p.set(-7, 1, -7, Blocks.POTTED_BAMBOO.defaultBlockState());
        p.set(6, 1, -7, Blocks.POTTED_BAMBOO.defaultBlockState());
    }

    /**
     * The main hall, two storeys (y 6..14): the monitor wall at the north, L's chair before it with his sweets table,
     * the Task Force's desks, the gallery along the south (the grand stair up the west wall, a walk to the stairwell's
     * upper door), the lounge under the gallery, the sweets counter and the fridge in the north-east.
     */
    private static void hall(Builds.Plan p) {
        int f = FH; // the hall's floor
        p.fill(-7, f + 1, -7, 7, f + 1, 7, Blocks.GRAY_CARPET.defaultBlockState());
        // the wall of monitors: six screens in black frames, lit from behind
        for (int x = -6; x <= 6; x++) {
            for (int y = f + 2; y <= f + 8; y++) {
                boolean frame = Math.abs(x) == 6 || Math.abs(x) == 2 || y == f + 2 || y == f + 5 || y == f + 8;
                p.set(x, y, -R + 1, Blocks.SEA_LANTERN.defaultBlockState());
                p.set(x, y, -R + 2, frame ? Blocks.BLACK_CONCRETE.defaultBlockState() : (x + y) % 3 == 0 ? Blocks.CYAN_STAINED_GLASS.defaultBlockState()
                        : Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
            }
        }
        p.fill(-5, f + 1, -R + 3, 5, f + 1, -R + 3, Builds.trapdoor(Blocks.IRON_TRAPDOOR, Direction.SOUTH, false, true)); // the console
        for (int x = -4; x <= 4; x += 2) {
            p.set(x, f + 2, -R + 3, Blocks.BLACK_CARPET.defaultBlockState());
        }
        // L's chair facing the screens, his table of sweets, Watari's tea
        p.set(0, f + 1, -2, Builds.stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
        p.set(-1, f + 1, -2, Builds.trapdoor(Blocks.DARK_OAK_TRAPDOOR, Direction.WEST, true, false));
        p.set(1, f + 1, -2, Builds.trapdoor(Blocks.DARK_OAK_TRAPDOOR, Direction.EAST, true, false));
        p.set(0, f + 1, -3, Builds.slab(Blocks.DARK_OAK_SLAB, true));
        p.set(0, f + 2, -3, Blocks.CAKE.defaultBlockState());
        p.set(1, f + 1, -3, Builds.slab(Blocks.DARK_OAK_SLAB, true));
        p.set(1, f + 2, -3, Blocks.BREWING_STAND.defaultBlockState()); // the teapot
        // the Task Force: three desks in a row facing the screens, a monitor and a cake between them
        p.fill(-5, f + 1, 1, -1, f + 1, 1, Builds.trapdoor(Blocks.SPRUCE_TRAPDOOR, Direction.SOUTH, false, true));
        for (int x = -5; x <= -1; x += 2) {
            p.set(x, f + 2, 1, Builds.trapdoor(Blocks.DARK_OAK_TRAPDOOR, Direction.SOUTH, true, false));
            p.set(x, f + 1, 2, Builds.stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
        }
        p.set(-4, f + 2, 1, Blocks.CAKE.defaultBlockState());
        p.set(-2, f + 2, 1, Blocks.FLOWER_POT.defaultBlockState());
        // the gallery: south strip and the walk to the stairwell's upper door, glass rail on its edge
        BlockState plank = Blocks.DARK_OAK_PLANKS.defaultBlockState(), rail = Blocks.GLASS_PANE.defaultBlockState();
        int g = 2 * FH; // its floor
        p.fill(-7, g, 5, 2, g, 7, plank);
        p.fill(0, g, 2, 2, g, 4, plank);
        p.fill(-5, g + 1, 5, -1, g + 1, 5, rail);
        p.fill(0, g + 1, 3, 0, g + 1, 4, rail);
        p.fill(0, g + 1, 2, 1, g + 1, 2, rail);
        p.fill(-7, g + 1, 6, 2, g + 1, 7, Blocks.BROWN_CARPET.defaultBlockState());
        p.set(-7, g + 1, 7, Blocks.BOOKSHELF.defaultBlockState());
        p.set(-6, g + 1, 7, Blocks.BOOKSHELF.defaultBlockState());
        p.set(1, g + 1, 7, Builds.stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH)); // a seat looking down into the hall
        p.set(-3, g - 1, 6, Builds.lantern(true)); // under the gallery, over the lounge
        // the grand stair up the west wall to the gallery, solid underneath
        for (int i = 0; i < 5; i++) {
            for (int x = -7; x <= -6; x++) {
                p.set(x, f + 1 + i, i, Builds.stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
                if (i > 0) {
                    p.fill(x, f + 1, i, x, f + i, i, Blocks.SMOOTH_QUARTZ.defaultBlockState());
                }
            }
        }
        // the lounge under the gallery: a sofa, its arms, a low table, a floor lamp
        for (int x = -4; x <= -2; x++) {
            p.set(x, f + 1, 7, Builds.stair(Blocks.DEEPSLATE_TILE_STAIRS, Direction.SOUTH));
        }
        p.set(-5, f + 1, 7, Builds.trapdoor(Blocks.DARK_OAK_TRAPDOOR, Direction.WEST, true, false));
        p.set(-1, f + 1, 7, Builds.trapdoor(Blocks.DARK_OAK_TRAPDOOR, Direction.EAST, true, false));
        p.set(-3, f + 1, 5, Builds.slab(Blocks.DARK_OAK_SLAB, true));
        p.set(-3, f + 2, 5, Blocks.POTTED_AZURE_BLUET.defaultBlockState());
        p.set(-6, f + 1, 7, Blocks.DARK_OAK_FENCE.defaultBlockState());
        p.set(-6, f + 2, 7, Builds.lantern(false));
        // the sweets corner: the counter with cakes, the sink, the fridge, a stool table
        for (int z = -5; z <= -1; z++) {
            p.set(R - 1, f + 1, z, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        }
        p.set(R - 1, f + 2, -5, Blocks.CAKE.defaultBlockState());
        p.set(R - 1, f + 2, -3, Blocks.CAKE.defaultBlockState());
        p.set(R - 1, f + 1, -4, Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
        p.set(R - 1, f + 2, -1, Blocks.FLOWER_POT.defaultBlockState());
        p.fill(R - 1, f + 1, 0, R - 1, f + 2, 0, Blocks.IRON_BLOCK.defaultBlockState());
        p.set(R - 2, f + 2, 0, Blocks.LEVER.defaultBlockState().setValue(net.minecraft.world.level.block.LeverBlock.FACING, Direction.WEST)
                .setValue(net.minecraft.world.level.block.LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL));
        p.set(4, f + 1, -4, Builds.slab(Blocks.DARK_OAK_SLAB, true));
        p.set(4, f + 2, -4, Blocks.CAKE.defaultBlockState());
        for (int[] q : new int[][]{{-7, -7}, {7, -7}, {-7, -3}}) {
            p.set(q[0], f + 1, q[1], Blocks.POTTED_BAMBOO.defaultBlockState());
        }
    }

    /** The war room: the case board, the meeting table, desks along the west windows, the files, the coffee. */
    private static void warRoom(Builds.Plan p) {
        int f = 3 * FH;
        p.fill(-7, f + 1, -7, 7, f + 1, 7, Blocks.LIGHT_GRAY_CARPET.defaultBlockState());
        p.fill(-6, f + 1, -R, 2, f + 4, -R, Blocks.GRAY_CONCRETE.defaultBlockState()); // the board's wall
        net.minecraft.world.item.Item[] pinned = {Items.PAPER, Items.MAP, Items.PAPER, Items.WRITABLE_BOOK, Items.PAPER, Items.CLOCK, Items.PAPER};
        for (int i = 0; i < pinned.length; i++) {
            Builds.frame(p.level, p.at(-6 + i, f + 2 + i % 2, -R + 1), Direction.SOUTH, new net.minecraft.world.item.ItemStack(pinned[i]));
        }
        p.fill(-3, f + 1, -3, 0, f + 1, -2, Builds.slab(Blocks.DARK_OAK_SLAB, true));
        for (int x = -3; x <= 0; x++) {
            p.set(x, f + 1, -4, Builds.stair(Blocks.SPRUCE_STAIRS, Direction.NORTH));
            p.set(x, f + 1, -1, Builds.stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
        }
        p.set(-2, f + 2, -3, Blocks.CANDLE.defaultBlockState());
        p.set(-1, f + 2, -2, Blocks.FLOWER_POT.defaultBlockState());
        for (int z = 0; z <= 4; z += 2) {
            p.set(-R + 1, f + 1, z, Builds.trapdoor(Blocks.SPRUCE_TRAPDOOR, Direction.EAST, false, true));
            p.set(-R + 1, f + 2, z, Builds.trapdoor(Blocks.DARK_OAK_TRAPDOOR, Direction.EAST, true, false));
            p.set(-R + 2, f + 1, z, Builds.stair(Blocks.SPRUCE_STAIRS, Direction.EAST));
        }
        for (int x = 4; x <= 7; x++) { // the case files
            p.fill(x, f + 1, 0, x, f + 2, 0, Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.NORTH));
        }
        p.set(R - 1, f + 1, -6, Blocks.SMOKER.defaultBlockState().setValue(net.minecraft.world.level.block.SmokerBlock.FACING, Direction.WEST));
        p.set(R - 1, f + 1, -5, Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3));
        p.set(R - 1, f + 1, -4, Builds.slab(Blocks.SMOOTH_QUARTZ_SLAB, true));
        p.set(R - 1, f + 2, -4, Blocks.FLOWER_POT.defaultBlockState());
        p.set(R - 1, f + 1, -7, Blocks.POTTED_BAMBOO.defaultBlockState());
    }

    /** The living floor: beds for the Task Force, L's corner (a chair and sweets, no bed), the kitchen and its table. */
    private static void quarters(Builds.Plan p) {
        int f = 4 * FH;
        p.fill(-7, f + 1, -7, 7, f + 1, 7, Blocks.WHITE_CARPET.defaultBlockState());
        for (int z = -6; z <= 0; z += 3) {
            BlockState foot = Blocks.LIGHT_BLUE_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.WEST);
            p.set(-R + 2, f + 1, z, foot.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
            p.set(-R + 1, f + 1, z, foot.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
            p.set(-R + 1, f + 1, z + 1, Blocks.BARREL.defaultBlockState());
            p.set(-R + 1, f + 2, z + 1, Builds.lantern(false));
        }
        p.set(-3, f + 1, 4, Builds.stair(Blocks.DARK_OAK_STAIRS, Direction.NORTH)); // L's: no bed, a chair before his sweets
        p.fill(-4, f + 1, 5, -2, f + 1, 5, Builds.slab(Blocks.DARK_OAK_SLAB, true));
        p.set(-4, f + 2, 5, Blocks.CAKE.defaultBlockState());
        p.set(-2, f + 2, 5, Blocks.CAKE.defaultBlockState());
        p.fill(-4, f + 1, 7, -2, f + 2, 7, Blocks.BOOKSHELF.defaultBlockState());
        for (int x = 3; x <= 7; x++) { // the kitchen along the north wall
            p.set(x, f + 1, -R + 1, x == 5 ? Blocks.SMOKER.defaultBlockState().setValue(net.minecraft.world.level.block.SmokerBlock.FACING, Direction.SOUTH)
                    : x == 4 ? Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3)
                    : Blocks.SMOOTH_QUARTZ.defaultBlockState());
        }
        p.fill(R - 1, f + 1, -5, R - 1, f + 2, -5, Blocks.IRON_BLOCK.defaultBlockState());
        p.fill(1, f + 1, -4, 2, f + 1, -3, Builds.slab(Blocks.SPRUCE_SLAB, true));
        for (int x = 1; x <= 2; x++) {
            p.set(x, f + 1, -5, Builds.stair(Blocks.SPRUCE_STAIRS, Direction.NORTH));
            p.set(x, f + 1, -2, Builds.stair(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
        }
        p.set(1, f + 2, -4, Blocks.CAKE.defaultBlockState());
    }

    /** The top floor: Watari's desk under his Gothic W, the server racks, the safe in the wall with the Wammy's House file. */
    private static void watari(Builds.Plan p) {
        int f = 5 * FH;
        p.fill(-4, f + 1, -4, 4, f + 1, 4, Blocks.RED_CARPET.defaultBlockState()); // a rug before the desk (over the lights of the floor below)
        p.fill(-2, f + 1, -5, 2, f + 1, -5, Builds.trapdoor(Blocks.DARK_OAK_TRAPDOOR, Direction.SOUTH, false, true));
        for (int x = -1; x <= 1; x++) {
            p.set(x, f + 2, -5, Builds.trapdoor(Blocks.DARK_OAK_TRAPDOOR, Direction.SOUTH, true, false));
        }
        p.set(0, f + 1, -4, Builds.stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
        p.fill(-3, f + 1, -R, 3, f + 4, -R, Blocks.DARK_OAK_PLANKS.defaultBlockState()); // the panelled wall behind the desk
        CompoundTag w = new CompoundTag();
        w.put("Patterns", new net.minecraft.world.level.block.entity.BannerPattern.Builder()
                .addPattern(net.minecraft.world.level.block.entity.BannerPatterns.STRIPE_LEFT, net.minecraft.world.item.DyeColor.BLACK)
                .addPattern(net.minecraft.world.level.block.entity.BannerPatterns.STRIPE_RIGHT, net.minecraft.world.item.DyeColor.BLACK)
                .addPattern(net.minecraft.world.level.block.entity.BannerPatterns.TRIANGLE_BOTTOM, net.minecraft.world.item.DyeColor.BLACK).toListTag());
        p.set(0, f + 3, -R + 1, Blocks.WHITE_WALL_BANNER.defaultBlockState().setValue(net.minecraft.world.level.block.WallBannerBlock.FACING, Direction.SOUTH), w);
        for (int z = -6; z <= -2; z++) { // the server racks
            for (int y = 1; y <= 3; y++) {
                p.set(-R + 1, f + y, z, (z + y) % 2 == 0 ? Blocks.OBSERVER.defaultBlockState().setValue(net.minecraft.world.level.block.ObserverBlock.FACING, Direction.WEST)
                        : Blocks.REDSTONE_LAMP.defaultBlockState());
            }
        }
        p.fill(3, f + 1, -R, 5, f + 3, -R + 1, Blocks.IRON_BLOCK.defaultBlockState()); // the safe, in the wall
        p.set(4, f + 1, -R + 1, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.SOUTH), safe());
        p.set(4, f + 2, -R + 1, Builds.trapdoor(Blocks.IRON_TRAPDOOR, Direction.SOUTH, false, false)); // a lid, not a block: the chest must open
        p.fill(-6, f + 1, R - 1, -2, f + 2, R - 1, Blocks.BOOKSHELF.defaultBlockState());
        p.set(5, f + 1, -2, Builds.stair(Blocks.SPRUCE_STAIRS, Direction.EAST));
        p.set(6, f + 1, -2, Builds.slab(Blocks.SPRUCE_SLAB, true));
        p.set(6, f + 2, -2, Blocks.POTTED_FERN.defaultBlockState());
    }

    /** The roof: a parapet, the stair hut, the helipad (an H in a yellow ring), the antenna. */
    private static void roof(Builds.Plan p) {
        p.walls(-R, TOP + 1, -R, R, TOP + 1, R, Blocks.GRAY_CONCRETE.defaultBlockState());
        p.walls(3, TOP + 1, 1, R, TOP + 3, R, Blocks.WHITE_CONCRETE.defaultBlockState()); // the hut over the stairs
        p.fill(3, TOP + 4, 1, R, TOP + 4, R, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
        p.fill(4, TOP + 1, 2, 7, TOP + 3, 7, Blocks.AIR.defaultBlockState());
        p.fill(3, TOP + 1, 2, 3, TOP + 2, 2, Blocks.AIR.defaultBlockState()); // its door onto the roof
        for (int x = -7; x <= 2; x++) {
            for (int z = -7; z <= 2; z++) {
                int dx = x + 2, dz = z + 2;
                double d = Math.sqrt(dx * dx + dz * dz);
                boolean h = Math.abs(dx) == 2 && Math.abs(dz) <= 2 || dz == 0 && Math.abs(dx) <= 2;
                if (h) {
                    p.set(x, TOP + 1, z, Blocks.WHITE_CARPET.defaultBlockState());
                } else if (Math.abs(d - 4.4) < 0.6) {
                    p.set(x, TOP + 1, z, Blocks.YELLOW_CARPET.defaultBlockState());
                }
            }
        }
        p.fill(6, TOP + 1, -6, 6, TOP + 5, -6, Blocks.IRON_BARS.defaultBlockState());
        p.set(6, TOP + 6, -6, Blocks.END_ROD.defaultBlockState());
    }

    private static CompoundTag safe() {
        return Builds.bookChest(Config.tr("Дом Вамми: личные дела", "Wammy's House: records"), "Q. W.", "aicompanion.hq.file1", "aicompanion.hq.file2");
    }

    // ------------------------------------------------------------------ L talks

    /** A right click on L (he looks up from his sweets) or on Near (he doesn't look up from his dice). */
    static void talkTo(ServerPlayer player, HumanEntity detective) {
        Kira.Case c = Kira.of(player);
        long now = player.server.overworld().getGameTime();
        if (now - c.flags.getOrDefault("l_looked", -999L) < 400) {
            return; // he looked up at you a moment ago: a held right click isn't a new conversation
        }
        c.flags.put("l_looked", now);
        boolean arrived = detective.role() == HumanEntity.Role.L && c.flags.putIfAbsent("l_met", now) == null; // the guest he sent for, the first time
        ask(player, detective, arrived ? Config.tr("(человек, которого ты пригласил, только что пришёл и подошёл к тебе)", "(the human you invited has just arrived and walks up to you)")
                : Config.tr("(человек подошёл к тебе)", "(the human walks up to you)"));
    }

    /** Somebody near L or Near says something in chat: he listens, answers, and his estimate moves. True: he was there to hear it. */
    public static boolean hear(ServerPlayer player, String text) {
        HumanEntity detective = player.serverLevel().getEntitiesOfClass(HumanEntity.class, player.getBoundingBox().inflate(8),
                h -> (h.role() == HumanEntity.Role.L || h.role() == HumanEntity.Role.NEAR) && h.isAlive()).stream().findFirst().orElse(null);
        if (detective != null) {
            ask(player, detective, text);
        }
        return detective != null;
    }

    private static void ask(ServerPlayer player, HumanEntity detective, String said) {
        if (Config.API_KEY.get().isBlank() || NEXT.getOrDefault(player.getUUID(), 0L) > System.currentTimeMillis()) {
            return; // one question at a time, then a breath: chat spam doesn't burn the key's quota
        }
        Kira.Case c = Kira.of(player);
        boolean near = detective.role() == HumanEntity.Role.NEAR;
        List<Llm.Msg> history = TALKS.computeIfAbsent(player.getUUID() + detective.role().name(), k -> new ArrayList<>());
        List<Llm.Msg> messages = new ArrayList<>();
        messages.add(new Llm.Msg("system", near ? Successors.persona(player, c) : persona(player, c)));
        messages.addAll(history);
        Llm.Msg question = new Llm.Msg("user", player.getGameProfile().getName() + ": " + said);
        messages.add(question);
        String url = Config.API_URL.get(), key = Config.API_KEY.get();
        List<String> models = Config.models();
        boolean english = Config.english();
        NEXT.put(player.getUUID(), Long.MAX_VALUE);
        CompletableFuture.supplyAsync(() -> Llm.completeInCharacter(url, key, models, messages, english), Llm.EXECUTOR)
                .whenComplete((content, error) -> NEXT.put(player.getUUID(), System.currentTimeMillis() + 2000))
                .whenComplete((content, error) -> player.server.execute(() -> {
                    if (error != null || content == null) {
                        AiCompanion.LOGGER.warn("{} could not answer", near ? "Near" : "L", error);
                        return;
                    }
                    String message = Llm.cleanMessage(Llm.parseReply(content).message());
                    if (Llm.refusal(message)) {
                        message = near ? "..." : Config.tr("…Я бы предпочёл не отвечать.", "...I'd rather not answer that.");
                    }
                    history.add(question);
                    history.add(new Llm.Msg("assistant", content));
                    while (history.size() > 10) {
                        history.remove(0);
                    }
                    player.sendSystemMessage(Component.literal(near ? "<Near> " : "<Ryuzaki> ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(message).withStyle(ChatFormatting.WHITE)));
                    Matcher m = CHANGE.matcher(content);
                    if (m.find()) {
                        float change = Math.max(-5, Math.min(10, Float.parseFloat(m.group(1))));
                        if (change != 0) {
                            Investigation.suspect(player, change, near ? "talking to Near" : "talking to L");
                        }
                    }
                }));
    }

    private static String persona(ServerPlayer player, Kira.Case c) {
        return """
                You are L (you go by Ryuzaki), the world's greatest detective, inside this player's Minecraft world. You are hunting Kira,
                who kills criminals and others by heart attack from afar, needing only a face and a name. You crouch barefoot on your chair,
                eat sweets constantly (cake, sugar cubes, strawberries), hold things between two fingers, speak politely, flatly and oddly
                precisely, and like to state probabilities ("I'd say there is a %d%% chance you are Kira"). You never accuse without proof,
                you test people with small traps and odd questions, you admit when you're wrong, you're childish about losing.
                The human in front of you is %s. Your current estimate that they are Kira: %.0f%%.
                They are here because you invited them: Matsuda, under his cover name Taro Matsui, brought them your invitation. You
                want to see them face to face and to ask them to help the Task Force with the Kira case (a suspect who helps is a
                suspect you can watch). When they have just arrived, greet them and tell them why you called them, then ask your questions.
                What you know: %s
                Answer what they said in 1-3 short sentences, in %s. Never mention game mechanics, AI or Minecraft as a game.
                Reply ONLY with JSON: {"message": "<your line>", "suspicion_change": <number from -5 to 10: up when they slip,
                know what only Kira could know, defend Kira, talk about the notebook, shinigami or apples oddly, or lie; down a little when
                they answer convincingly and calmly; 0 for silence, a greeting or small talk, and otherwise>}
                """.formatted((int) c.suspicion, player.getGameProfile().getName(), c.suspicion, facts(c), Config.english() ? "English" : "Russian");
    }

    private static String facts(Kira.Case c) {
        List<String> f = new ArrayList<>();
        f.add(c.kills + " deaths fit Kira's pattern, most of them heart attacks");
        if (c.flag("tailor_killed")) {
            f.add("Kira killed your decoy Lind L. Tailor live, so Kira is in this region and needs a face and a name");
        }
        if (c.flag("agents")) {
            f.add("FBI agents have been tailing this human");
        }
        if (c.flag("searched")) {
            f.add("the Task Force searched this human's house and put up cameras");
        }
        if (c.flag("watari_dead")) {
            f.add("Watari is dead and your files are gone");
        }
        return String.join("; ", f) + ".";
    }
}
