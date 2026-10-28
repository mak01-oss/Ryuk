package ru.maksim.aicompanion;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Sidoh's notebook ended up with a mafia gang (in the manga: Mello's men in Los Angeles; "Jack Neylon", real name Kal
 * Snydar, held it, with the eye deal made with Sidoh). Their hideout here is an underground factory of pipes and
 * ladders. The boss is the danger: he reads the name of any human whose face he can see for a few seconds and writes it
 * down. A carved pumpkin hides a face (the manga's helmets), but Sidoh, invisible, guards the place and pulls helmets
 * off, as he did for Neylon.
 */
public final class Mafia {
    private static final int READ_SECONDS = 4;

    private Mafia() {
    }

    /** Once a second for the boss: is there a face to read? */
    static void bossTick(HumanEntity boss) {
        ServerLevel level = (ServerLevel) boss.level();
        ServerPlayer target = level.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(20),
                p -> p.isAlive() && !p.isSpectator()).stream().min(java.util.Comparator.comparingDouble(boss::distanceToSqr)).orElse(null);
        if (target == null || !boss.hasLineOfSight(target) || !Lifespans.faceVisible(boss, target)
                || DeathNotes.listed(level.getServer(), target.getUUID())) {
            boss.getPersistentData().putInt("aicompanion_reading", 0);
            return;
        }
        int reading = boss.getPersistentData().getInt("aicompanion_reading") + 1;
        boss.getPersistentData().putInt("aicompanion_reading", reading);
        boss.getLookControl().setLookAt(target, 30, 30);
        if (reading == 1) {
            target.sendSystemMessage(Component.translatable("aicompanion.mafia.stare").withStyle(ChatFormatting.RED));
            Network.cue(target, "sting.stare");
            CompanionEntity ryuk = CompanionEntity.of(target);
            if (ryuk != null) {
                ryuk.remark(Config.tr("у главаря мафии глаза синигами (сделка с Сидо): он смотрит в лицо твоему человеку и читает его имя! Ещё пара секунд — и он его впишет",
                        "the mafia boss has the shinigami eyes (his deal with Sidoh): he's looking your human in the face, reading their name! A couple more seconds and he writes it"), "story", 0);
            }
        }
        if (reading >= READ_SECONDS) {
            boss.getPersistentData().putInt("aicompanion_reading", 0);
            DeathNotes.writeByNpc(target, boss.getUUID());
            target.sendSystemMessage(Component.translatable("aicompanion.mafia.wrote").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            level.playSound(null, boss.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.HOSTILE, 1.0F, 0.6F);
        }
    }

    // ------------------------------------------------------------------ Sidoh's lost notebook

    static boolean isSidohsNote(ItemStack stack) {
        return stack.is(AiCompanion.DEATH_NOTE.get()) && stack.hasTag() && stack.getTag().getBoolean("Sidoh");
    }

    static boolean holdsSidohsNote(ServerPlayer player) {
        return player.getInventory().hasAnyMatching(Mafia::isSidohsNote);
    }

    /** Only humans who have touched his notebook see and hear him. */
    static boolean seesSidoh(ServerPlayer player) {
        return Kira.of(player).flag("sidoh_touched");
    }

    /** Once a second: the story of Sidoh's notebook begins after the notebook has worked a while; touching his binds him. */
    static void story(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        if (c.sealed || player.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            return;
        }
        if (!c.flag("sidoh") && (c.kills >= 6 || c.stage >= Investigation.HEADQUARTERS) && Investigation.day(player) >= 2
                && CompanionEntity.of(player) != null) { // not before the third day: one story at a time
            start(player, 220);
        }
        if (c.flag("sidoh") && !c.flag("sidoh_bound") && holdsSidohsNote(player)) { // (seen already if they caught his page)
            c.flags.put("sidoh_bound", player.server.overworld().getGameTime());
            c.flags.putIfAbsent("sidoh_touched", player.server.overworld().getGameTime());
            Kira.changed(player);
            for (SidohEntity s : player.serverLevel().getEntitiesOfClass(SidohEntity.class, player.getBoundingBox().inflate(64))) {
                s.follow(player); // bound to the notebook's new owner
            }
            player.sendSystemMessage(Component.translatable("aicompanion.sidoh.visible").withStyle(ChatFormatting.DARK_PURPLE));
            story(player, Config.tr("твой человек поднял тетрадь Сидо и теперь видит и слышит его. Сидо трясётся и умоляет вернуть тетрадь",
                    "your human picked up Sidoh's notebook and now sees and hears him. Sidoh is shaking and begging to have it back"));
        }
    }

    static BlockPos start(ServerPlayer player, double distance) {
        Kira.Case c = Kira.of(player);
        c.flags.put("sidoh", player.server.overworld().getGameTime());
        ServerLevel level = player.serverLevel();
        BlockPos hall = raise(player, distance);
        BlockPos top = BlockPos.of(c.flags.get("hideout"));
        Builds.afterBuild(() -> {
            SidohEntity sidoh = AiCompanion.SIDOH.get().create(level);
            if (sidoh != null) {
                sidoh.setup(hall.offset(0, 2, 9));
                sidoh.moveTo(hall.getX() + 0.5, hall.getY() + 2, hall.getZ() + 9.5);
                level.addFreshEntity(sidoh);
            }
        });
        story(player, Config.tr("явился ещё один бог смерти — Сидо, трус и растяпа. Он опять потерял тетрадь, и она попала к банде мафии. Их главарь (он зовёт себя Джек Нейлон; настоящее имя ты знаешь, но имён синигами не называют) заключил с Сидо сделку на глаза: он читает имя по лицу. Сидо подсмотрел их логово через дыру в мире богов смерти: "
                        + Scouting.where(player.blockPosition(), top) + ". Сидо привязан к тетради и служит главарю, но умоляет, чтобы её вернули. Твой человек его не видит: он не касался его тетради",
                "another god of death has turned up: Sidoh, a coward and a bungler. He lost his notebook again and a mafia gang got it. Their boss (he calls himself Jack Neylon; you know his real name, but shinigami never tell names) made the eye deal with Sidoh: he reads names off faces. Sidoh spied their hideout through a hole in the shinigami realm: "
                        + Scouting.where(player.blockPosition(), top) + ". Bound to the notebook, Sidoh serves the boss, yet he begs to get it back. Your human can't see him: they never touched his notebook"));
        Network.card(player, Network.Broadcast.CHAPTER, Component.translatable("aicompanion.card.sidoh"), Component.translatable("aicompanion.card.sidoh_text"), "", top);
        return hall;
    }

    /** His notebook is back: he blurts out the one secret he has, then flies home. */
    static void returned(ServerPlayer player, SidohEntity sidoh) {
        Kira.Case c = Kira.of(player);
        c.flags.put("fake_rules", player.server.overworld().getGameTime());
        c.flags.put("sidoh_done", player.server.overworld().getGameTime());
        Kira.changed(player);
        Pact.thrill(player, Pact.THRILL_BIG);
        player.sendSystemMessage(Component.literal("<" + Config.tr("Сидо", "Sidoh") + "> ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.translatable("aicompanion.sidoh.thanks").withStyle(ChatFormatting.WHITE)));
        ServerLevel level = player.serverLevel();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, sidoh.getX(), sidoh.getY() + 1, sidoh.getZ(), 30, 0.4, 0.8, 0.4, 0.02);
        level.playSound(null, sidoh.blockPosition(), Sounds.SIDOH_WING.get(), SoundSource.NEUTRAL, 1.0F, 0.9F);
        sidoh.discard(); // home, to the realm
        story(player, Config.tr("твой человек вернул Сидо его тетрадь. Сидо на радостях выболтал, что два правила на последней странице тетради (про 13 дней и про сожжённую тетрадь) — ложь, которую когда-то вписал ты. Теперь твой человек это знает",
                "your human gave Sidoh his notebook back. Overjoyed, Sidoh blurted out that the two rules on the notebook's last page (13 days, the burned notebook) are lies you once wrote. Now your human knows"));
    }

    private static void story(ServerPlayer player, String event) {
        CompanionEntity ryuk = CompanionEntity.of(player);
        if (ryuk != null) {
            ryuk.remark(event, "story", 0);
        }
    }

    /** The boss is dead: the notebook he held lies on the floor (Sidoh's). */
    static void onBossDeath(ServerLevel level, HumanEntity boss) {
        ItemStack note = new ItemStack(AiCompanion.DEATH_NOTE.get());
        note.getOrCreateTag().putBoolean("Sidoh", true);
        note.setHoverName(Component.translatable("item.aicompanion.sidoh_note"));
        level.addFreshEntity(new ItemEntity(level, boss.getX(), boss.getY() + 0.5, boss.getZ(), note));
    }

    /** The hideout, under the ground {@code distance} blocks away; the gang moves in once it's built. Returns its hall. */
    static BlockPos raise(ServerPlayer player, double distance) {
        ServerLevel level = player.serverLevel();
        BlockPos top = Headquarters.site(level, player, (int) distance, 6); // the flattest dry ground of eight: the shack stands in the open
        Kira.of(player).flags.put("hideout", top.asLong());
        Kira.get(player.server).setDirty();
        BlockPos hall = hall(level, top);
        build(level, top, hall);
        Builds.afterBuild(() -> {
            Direction n = Direction.NORTH, e = Direction.EAST, s = Direction.SOUTH, w = Direction.WEST;
            HumanEntity.Stance stand = HumanEntity.Stance.STAND, desk = HumanEntity.Stance.DESK, chair = HumanEntity.Stance.CHAIR;
            Headquarters.spawn(level, player, hall, HumanEntity.Role.BOSS, 0, hall, Headquarters.post(hall, 0, 2, 0, s, chair, 600));
            HumanEntity.Post[][] days = {
                    {Headquarters.post(hall, -2, 0, 13, s, stand, 600)}, // the door's two guards
                    {Headquarters.post(hall, 2, 0, 13, s, stand, 600)},
                    {Headquarters.post(hall, -7, 0, 7, e, desk, 240), Headquarters.post(hall, 6, 0, 6, e, chair, 60)}, // cards, a drink
                    {Headquarters.post(hall, -5, 0, 7, w, desk, 240), Headquarters.post(hall, 8, 0, 12, w, stand, 60)},
                    {Headquarters.post(hall, -7, 0, 8, e, desk, 240), Headquarters.post(hall, 6, 0, 8, e, chair, 60)},
                    {Headquarters.post(hall, 0, 0, 9, n, stand, 30), Headquarters.post(hall, -3, 0, 4, e, stand, 30), // the round
                            Headquarters.post(hall, 4, 0, 11, w, stand, 30)}};
            for (int i = 0; i < days.length; i++) {
                Headquarters.spawn(level, player, hall, HumanEntity.Role.MAFIA, i, hall, days[i]);
            }
        });
        return hall;
    }

    /**
     * The hall at the bottom of the tunnel from the shack at {@code top}: 15 blocks down and 32 north. Too little ground
     * under the shack (flat worlds): the same hall as a bunker on the surface, its door where the tunnel would come in.
     */
    public static BlockPos hall(ServerLevel level, BlockPos top) {
        return top.getY() - 18 > level.getMinBuildHeight() ? top.offset(0, -15, -32) : top;
    }

    /** The gang's hall, built (with Sidoh's story begun) if there is none yet. */
    static BlockPos hideout(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        return c.flag("hideout") ? hall(player.serverLevel(), BlockPos.of(c.flags.get("hideout"))) : start(player, 220);
    }

    /** Where they keep a hostage: the cell in the hall's south-west corner (hall coordinates of its middle). */
    static final int CELL_X = -9, CELL_Z = 12;
    /** Mello's sofa before his screens, and where Sidoh hovers. */
    static final int MELLO_X = 7, MELLO_Z = 3;

    /**
     * Above, a shack nobody looks at twice; inside it the tunnel down, stairs all the way with a lantern every few steps,
     * to the hall's door. The hall (x -10..10, z 0..14, 7 high): the boss's platform and desk at the north under the
     * gang's banner, the card table, the bar, the weapons on the wall, crates, the cell, Mello's corner with his screens,
     * pipes and lamps on chains overhead.
     */
    private static void build(ServerLevel level, BlockPos top, BlockPos h) {
        BlockState wall = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState(), air = Blocks.AIR.defaultBlockState(),
                floor = Blocks.SMOOTH_STONE.defaultBlockState(), brick = Blocks.STONE_BRICKS.defaultBlockState();
        boolean underground = h.getY() < top.getY();
        Builds.Plan p = new Builds.Plan(level, h);
        p.fill(-11, -1, -1, 11, 7, 15, wall);
        p.fill(-10, 0, 0, 10, 6, 14, air);
        p.fill(-10, -1, 0, 10, -1, 14, floor);
        for (int[] patch : new int[][]{{-6, 5, -2, 9}, {2, 10, 6, 13}, {-9, 1, -6, 3}}) { // worn patches in the floor
            p.fill(patch[0], -1, patch[1], patch[2], -1, patch[3], Blocks.POLISHED_ANDESITE.defaultBlockState());
        }
        p.set(-3, -1, 11, Blocks.COAL_BLOCK.defaultBlockState()); // an oil stain
        // overhead: pipes along the ceiling, lamps on chains down the middle
        for (int z = 2; z <= 12; z += 5) {
            p.fill(-10, 6, z, 10, 6, z, Blocks.WAXED_CUT_COPPER.defaultBlockState());
        }
        for (int z = 3; z <= 12; z += 4) {
            for (int x = -6; x <= 6; x += 6) {
                p.set(x, 6, z, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
                p.set(x, 5, z, Builds.lantern(true));
            }
        }
        for (int x = -10; x <= 10; x += 20) { // pipes down the side walls
            for (int z = 1; z <= 13; z += 6) {
                p.fill(x, 0, z, x, 5, z, Blocks.IRON_BARS.defaultBlockState());
            }
        }
        // the door and its guards' lamps
        p.fill(0, 0, 15, 0, 1, 15, air);
        p.set(-1, 2, 14, Builds.lantern(false));
        p.set(-1, 1, 14, Blocks.BARREL.defaultBlockState());
        p.set(1, 1, 14, Blocks.BARREL.defaultBlockState());
        p.set(1, 2, 14, Builds.lantern(false));
        p.set(-1, 0, 14, Blocks.BARREL.defaultBlockState());
        p.set(1, 0, 14, Blocks.BARREL.defaultBlockState());
        // the boss's platform: two steps up, a rail, his desk and chair, the ledger, the gang's banner behind him
        BlockState deep = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        p.fill(-4, 0, 0, 4, 1, 3, deep);
        for (int x = -1; x <= 1; x++) {
            p.set(x, 0, 5, Builds.stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.NORTH));
            p.set(x, 1, 4, Builds.stair(Blocks.POLISHED_DEEPSLATE_STAIRS, Direction.NORTH));
            p.set(x, 0, 4, deep);
        }
        for (int x = -4; x <= 4; x++) {
            if (Math.abs(x) >= 2) {
                p.set(x, 2, 3, Blocks.DARK_OAK_FENCE.defaultBlockState());
            }
        }
        p.fill(-1, 2, 1, 1, 2, 1, Builds.trapdoor(Blocks.DARK_OAK_TRAPDOOR, Direction.SOUTH, false, true)); // his desk
        p.set(0, 2, 0, Builds.stair(Blocks.DARK_OAK_STAIRS, Direction.NORTH)); // his chair
        p.set(-1, 2, 0, Builds.trapdoor(Blocks.DARK_OAK_TRAPDOOR, Direction.WEST, true, false));
        p.set(1, 2, 0, Builds.trapdoor(Blocks.DARK_OAK_TRAPDOOR, Direction.EAST, true, false));
        p.set(-1, 3, 1, Builds.lantern(false));
        p.set(2, 2, 0, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.SOUTH), ledger());
        p.set(-2, 2, 0, Blocks.POTTED_WITHER_ROSE.defaultBlockState());
        CompoundTag skull = new CompoundTag();
        skull.put("Patterns", new BannerPattern.Builder().addPattern(BannerPatterns.SKULL, DyeColor.BLACK)
                .addPattern(BannerPatterns.BORDER, DyeColor.BLACK).toListTag());
        p.set(0, 4, 0, Blocks.RED_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH), skull);
        // the card table, a green cloth over it, a lamp low above it
        for (int z = 7; z <= 8; z++) {
            p.set(-6, 0, z, Builds.slab(Blocks.DARK_OAK_SLAB, true));
            p.set(-6, 1, z, Blocks.GREEN_CARPET.defaultBlockState());
            p.set(-7, 0, z, Builds.stair(Blocks.SPRUCE_STAIRS, Direction.WEST));
            p.set(-5, 0, z, Builds.stair(Blocks.SPRUCE_STAIRS, Direction.EAST));
        }
        p.set(-6, 5, 7, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
        p.set(-6, 4, 7, Builds.lantern(true));
        // the bar: a counter, the bottles on the shelf behind it, two stools
        for (int z = 5; z <= 9; z++) {
            p.set(7, 0, z, Blocks.DARK_OAK_PLANKS.defaultBlockState());
            p.set(9, 0, z, Builds.slab(Blocks.SPRUCE_SLAB, true));
            p.set(9, 1, z, z % 2 == 0 ? Blocks.BREWING_STAND.defaultBlockState() : Blocks.FLOWER_POT.defaultBlockState());
        }
        p.set(7, 1, 7, Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
        p.set(6, 0, 6, Builds.stair(Blocks.SPRUCE_STAIRS, Direction.WEST));
        p.set(6, 0, 8, Builds.stair(Blocks.SPRUCE_STAIRS, Direction.WEST));
        // the weapons on the west wall
        net.minecraft.world.item.Item[] arms = {net.minecraft.world.item.Items.IRON_SWORD, net.minecraft.world.item.Items.CROSSBOW,
                net.minecraft.world.item.Items.IRON_AXE, net.minecraft.world.item.Items.CROSSBOW};
        for (int i = 0; i < arms.length; i++) {
            Builds.frame(level, h.offset(-10, 2, 2 + i), Direction.EAST, new ItemStack(arms[i]));
        }
        // crates in the corners
        for (int[] c : new int[][]{{-10, 0}, {-9, 0}, {-10, 1}, {10, 13}, {9, 14}, {10, 14}, {8, 14}, {10, 12}}) {
            p.set(c[0], 0, c[1], (c[0] + c[1]) % 2 == 0 ? Blocks.BARREL.defaultBlockState() : Blocks.CHEST.defaultBlockState());
        }
        p.set(-10, 1, 0, Blocks.BARREL.defaultBlockState());
        p.set(10, 1, 14, Blocks.BARREL.defaultBlockState());
        p.fill(9, 0, 11, 9, 1, 11, Blocks.SCAFFOLDING.defaultBlockState());
        // the cell, south-west: bars, an iron door, a bed of straw
        p.fill(-10, 0, 10, -7, 2, 10, Blocks.IRON_BARS.defaultBlockState());
        p.fill(-7, 0, 10, -7, 2, 14, Blocks.IRON_BARS.defaultBlockState());
        p.door(-7, 0, 12, Blocks.IRON_DOOR, Direction.EAST);
        p.set(-10, 0, 14, Blocks.HAY_BLOCK.defaultBlockState());
        // Mello's corner, north-east: screens in the wall, his sofa before them, a box of chocolate
        for (int x = 6; x <= 9; x++) {
            for (int y = 1; y <= 3; y++) {
                p.set(x, y, -2, Blocks.SEA_LANTERN.defaultBlockState());
                p.set(x, y, -1, x == 6 || x == 9 || y == 3 ? Blocks.BLACK_CONCRETE.defaultBlockState() : Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState());
            }
        }
        for (int x = MELLO_X; x <= MELLO_X + 1; x++) {
            p.set(x, 0, MELLO_Z, Builds.stair(Blocks.BLACKSTONE_STAIRS, Direction.SOUTH));
        }
        p.set(MELLO_X - 1, 0, MELLO_Z, Builds.trapdoor(Blocks.DARK_OAK_TRAPDOOR, Direction.WEST, true, false));
        p.set(MELLO_X + 2, 0, MELLO_Z, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.FACING, Direction.WEST));
        if (!underground) {
            return;
        }
        // above: the shack, then the tunnel down to the door (last, so nothing above overwrites it)
        Builds.Plan s = new Builds.Plan(level, top);
        s.fill(-6, 0, -8, 6, 7, 7, air); // a yard round it, nothing to climb out of
        s.footing(-6, -8, 6, 7, 0, Blocks.DIRT.defaultBlockState());
        s.fill(-6, -1, -8, 6, -1, 7, Blocks.COARSE_DIRT.defaultBlockState());
        s.fill(-1, -1, 3, 1, -1, 7, Blocks.DIRT_PATH.defaultBlockState()); // the trodden way to the door
        s.set(4, 0, 4, Blocks.COMPOSTER.defaultBlockState());
        s.set(-4, 0, 3, Blocks.BARREL.defaultBlockState());
        s.fill(-2, -1, -5, 2, -1, 2, Blocks.COBBLESTONE.defaultBlockState());
        for (int y = 0; y <= 3; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -5; z <= 2; z++) {
                    if (Math.abs(x) == 2 || z == -5 || z == 2) {
                        s.set(x, y, z, (x * 7 + z * 3 + y) % 4 == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
                    }
                }
            }
        }
        s.fill(-2, 4, -5, 2, 4, 2, Builds.slab(Blocks.SPRUCE_SLAB, false));
        s.door(0, 0, 2, Blocks.SPRUCE_DOOR, Direction.NORTH);
        s.set(-2, 2, 0, Blocks.GLASS_PANE.defaultBlockState());
        s.set(1, 0, 1, Blocks.BARREL.defaultBlockState());
        s.set(-1, 3, 1, Blocks.COBWEB.defaultBlockState());
        s.set(1, 3, 0, Builds.lantern(true));
        for (int i = 0; i < 16; i++) { // the tunnel: three wide, its walls and roof of brick, a step down per block north
            int y = -1 - i, z = -1 - i;
            for (int x = -2; x <= 2; x++) {
                for (int dy = -1; dy <= 4; dy++) {
                    boolean shell = Math.abs(x) == 2 || dy == -1 || dy == 4;
                    if (i < 4 && y + dy >= 0) {
                        continue; // inside the shack: its own walls and air
                    }
                    s.set(x, y + dy, z, shell ? brick : air);
                }
            }
            for (int x = -1; x <= 1; x++) {
                s.set(x, y, z, i < 15 ? Builds.stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH) : brick);
            }
            if (i % 4 == 3 && i > 3) {
                s.set(0, y + 3, z, Builds.lantern(true));
            }
        }
        // the last stretch, level, to the hall's door
        for (int z = -17; z >= h.getZ() - top.getZ() + 15; z--) {
            for (int x = -2; x <= 2; x++) {
                for (int dy = -1; dy <= 3; dy++) {
                    boolean shell = Math.abs(x) == 2 || dy == -1 || dy == 3;
                    s.set(x, -16 + dy + 1, z, shell ? brick : air);
                }
            }
        }
        p.fill(-1, 0, 15, 1, 1, 15, air);
    }

    /** The gang's ledger: a fake passport in the name of Jack Neylon, and the pages signed with the real one. */
    private static net.minecraft.nbt.CompoundTag ledger() {
        return Builds.bookChest(Config.tr("Бухгалтерия", "Ledger"), "J. N.", "aicompanion.mafia.ledger1", "aicompanion.mafia.ledger2");
    }
}
