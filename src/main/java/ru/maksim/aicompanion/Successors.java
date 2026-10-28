package ru.maksim.aicompanion;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * After L, his successors from Wammy's House (the manga's second part):
 * - Near takes the case with the SPK, from a white hall full of toys some 180 blocks away; he talks through the LLM
 *   as L did, and his estimate moves the same way;
 * - Mello goes over to the mafia and trades a hostage from the nearest village for a notebook (the manga's trade
 *   that brought Sidoh's notebook to the gang); his death hands Near a last lead;
 * - Gevanni swaps the notebooks he finds by the bed, at night while their owner is away, for perfect copies;
 * - at 80% Near calls Kira to the Yellow Box warehouse at dawn, and forty seconds decide it there: names written in
 *   a real notebook kill (Near dead: Kira wins for good), in a copy they don't (Near has his proof, and under Ryuk's
 *   game Ryuk writes the name, as he wrote Light's).
 * The story's days (the next day, sunset, dawn) are days of the sky, overworld day time, so a night slept through counts.
 */
public final class Successors {
    private static final int SHOWDOWN = 60; // seconds Near gives Kira in the warehouse before he plays his own card
    private static final int BOX_X = 13, BOX_Z = 8; // the warehouse's half sizes

    private Successors() {
    }

    // ------------------------------------------------------------------ once a second

    static void tick(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        if (c.sealed || c.flag("kira_wins") || player.level().dimension() != Level.OVERWORLD) {
            return;
        }
        long now = player.server.overworld().getGameTime(), day = Kira.clock(player.server);
        hostageFate(player, c);
        if (c.stage == Investigation.SUCCESSORS && !c.flag("successor") && day > c.flags.getOrDefault("l_dead_day", 0L) + Kira.DAY) {
            arrive(player);
            return;
        }
        if (c.stage != Investigation.NEAR) {
            return;
        }
        if (c.flag("mello") && !c.flag("mello_dead") && !c.flag("hostage") && !c.flag("hostage_done") && day > c.flags.get("mello") + Kira.DAY) {
            kidnap(player);
        }
        if (c.flag("hostage") && day > c.flags.get("hostage")) {
            doom(player, c, now);
        }
        if (night(player.serverLevel()) && day / Kira.DAY != c.flags.getOrDefault("gevanni", -1L)) {
            gevanni(player, c, day / Kira.DAY);
        }
        if (!c.flag("meeting") && c.suspicion >= 80 && !c.flag("arrest") && day >= c.flags.getOrDefault("near_day", 0L) + 2 * Kira.DAY) {
            invite(player); // Near takes his time: two days at least on the case before the warehouse
        }
        if (c.flag("meeting")) {
            meeting(player, c, now);
        }
    }

    private static boolean night(ServerLevel level) {
        long t = level.getDayTime() % 24000;
        return t > 13000 && t < 23000;
    }

    // ------------------------------------------------------------------ Near takes over

    /** A day after L's death: Near takes the case, and Mello goes his own way, to the mafia. */
    static void arrive(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        ServerLevel level = player.serverLevel();
        long now = player.server.overworld().getGameTime();
        BlockPos site = Headquarters.site(level, player, 180, 14);
        c.flags.put("successor", now);
        c.flags.put("near_day", Kira.clock(level.getServer()));
        c.flags.put("spk", site.asLong());
        c.flags.put("mello", Kira.clock(level.getServer()));
        c.stage = Investigation.NEAR;
        Kira.changed(player);
        buildSpk(level, site);
        Builds.afterBuild(() -> spkPeople(level, player, site));
        BlockPos hall = Mafia.hideout(player);
        Builds.afterBuild(() -> Headquarters.spawn(level, player, hall, HumanEntity.Role.MELLO, 0, hall, // his sofa before his screens
                Headquarters.post(hall, Mafia.MELLO_X, 0, Mafia.MELLO_Z, Direction.NORTH, HumanEntity.Stance.SOFA, 300),
                Headquarters.post(hall, Mafia.MELLO_X + 1, 0, 1, Direction.NORTH, HumanEntity.Stance.STAND, 40),
                Headquarters.post(hall, 0, 0, 7, Direction.SOUTH, HumanEntity.Stance.STAND, 30)));
        player.sendSystemMessage(Component.literal("N ").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
                .append(Component.translatable("aicompanion.near.arrive", site.getX(), site.getZ()).withStyle(ChatFormatting.WHITE)));
        Network.card(player, Network.Broadcast.CHAPTER, Component.translatable("aicompanion.card.near"), Component.translatable("aicompanion.card.near_text"), "", site);
        Pact.thrill(player, Pact.THRILL_BIG);
        story(player, Config.tr("у L были преемники из Дома Вамми. Дело взял Near, глава SPK: его база с игрушками стоит в " + site.getX() + ", " + site.getZ()
                        + ". Второй, Mello, работать с Near не стал и ушёл к мафии, в логово. Тебе стало ещё интереснее",
                "L had successors from Wammy's House. Near, head of the SPK, has taken the case: his base full of toys is at " + site.getX() + ", "
                        + site.getZ() + ". The other one, Mello, wouldn't work with Near and went over to the mafia, to their hideout. This just got better"));
    }

    /**
     * The SPK's hall: white, windowless but for a high strip, a vestibule behind glass, the wall of monitors under the
     * Gothic N, the SPK's desks along the east wall, and the floor given over to Near's toys: the blank white puzzle,
     * towers of dice, a toy city, a train going round, a robot.
     */
    private static void buildSpk(ServerLevel level, BlockPos c) {
        Builds.Plan p = new Builds.Plan(level, c);
        BlockState white = Blocks.WHITE_CONCRETE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        p.fill(-14, 1, -12, 14, 12, 14, air);
        p.footing(-13, -11, 13, 13, 0, Blocks.STONE.defaultBlockState());
        p.fill(-13, 0, -11, 13, 0, 13, Blocks.SMOOTH_STONE.defaultBlockState());
        p.fill(-12, 0, -10, 12, 0, 10, white);
        p.walls(-12, 1, -10, 12, 7, 10, white);
        p.walls(-12, 7, -10, 12, 7, 10, Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState());
        for (int z = -8; z <= 8; z += 2) { // the high strip of tinted windows
            p.set(-12, 5, z, Blocks.GRAY_STAINED_GLASS_PANE.defaultBlockState());
            p.set(12, 5, z, Blocks.GRAY_STAINED_GLASS_PANE.defaultBlockState());
        }
        p.fill(-12, 8, -10, 12, 8, 10, white);
        for (int x = -8; x <= 8; x += 4) {
            for (int z = -6; z <= 6; z += 4) {
                p.set(x, 8, z, Blocks.SEA_LANTERN.defaultBlockState());
            }
        }
        // the way in: glass doors, a canopy, a vestibule behind a glass wall
        p.fill(-1, 1, 10, 1, 3, 10, air);
        p.fill(-2, 4, 11, 2, 4, 12, Builds.slab(Blocks.SMOOTH_QUARTZ_SLAB, true));
        p.fill(-1, 0, 11, 1, 0, 13, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        for (int x : new int[]{-3, 3}) {
            p.fill(x, 1, 12, x, 2, 12, Blocks.BIRCH_FENCE.defaultBlockState());
            p.set(x, 3, 12, Builds.lantern(false));
        }
        p.fill(-5, 1, 7, 5, 3, 7, Blocks.GLASS_PANE.defaultBlockState());
        p.fill(-1, 1, 7, 1, 3, 7, air);
        p.set(-4, 1, 9, Blocks.POTTED_WHITE_TULIP.defaultBlockState());
        p.set(4, 1, 9, Builds.stair(Blocks.QUARTZ_STAIRS, Direction.EAST));
        // the monitors, the N above them
        for (int x = -9; x <= 9; x++) {
            for (int y = 2; y <= 6; y++) {
                boolean frame = Math.abs(x) == 9 || x % 3 == 0 || y == 2 || y == 6;
                p.set(x, y, -9, Blocks.SEA_LANTERN.defaultBlockState());
                p.set(x, y, -8, frame ? Blocks.BLACK_CONCRETE.defaultBlockState()
                        : (x + y) % 2 == 0 ? Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState() : Blocks.CYAN_STAINED_GLASS.defaultBlockState());
            }
        }
        p.set(0, 7, -9, Blocks.WHITE_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH),
                banner(new BannerPattern.Builder().addPattern(BannerPatterns.STRIPE_LEFT, DyeColor.BLACK)
                        .addPattern(BannerPatterns.STRIPE_RIGHT, DyeColor.BLACK).addPattern(BannerPatterns.STRIPE_DOWNRIGHT, DyeColor.BLACK))); // Near's Gothic N
        // the SPK's desks along the east wall, each with its screen and chair
        for (int z = -5; z <= 3; z += 4) {
            p.set(11, 1, z, Builds.trapdoor(Blocks.IRON_TRAPDOOR, Direction.WEST, false, true));
            p.set(11, 2, z, Builds.trapdoor(Blocks.DARK_OAK_TRAPDOOR, Direction.WEST, true, false));
            p.set(10, 1, z, Builds.stair(Blocks.QUARTZ_STAIRS, Direction.WEST));
            p.set(11, 1, z + 1, Blocks.POTTED_FERN.defaultBlockState());
        }
        for (int z = -9; z <= -6; z++) { // files
            p.fill(-11, 1, z, -11, 2, z, Blocks.BARREL.defaultBlockState().setValue(net.minecraft.world.level.block.BarrelBlock.FACING, Direction.EAST));
        }
        // the blank white puzzle on the floor, two pieces still missing, one lying beside it
        p.fill(-2, 1, -1, 2, 1, 2, Blocks.WHITE_CARPET.defaultBlockState());
        p.set(1, 1, 0, air);
        p.set(-1, 1, 2, air);
        p.set(3, 1, 1, Blocks.WHITE_CARPET.defaultBlockState());
        // towers of dice
        int[][] towers = {{-6, -3, 4}, {-7, -1, 6}, {-5, 0, 3}, {6, -5, 5}, {7, -3, 3}};
        for (int[] t : towers) {
            for (int y = 1; y <= t[2]; y++) {
                p.set(t[0], y, t[1], y % 2 == 0 ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState() : Blocks.QUARTZ_BLOCK.defaultBlockState());
            }
        }
        // a toy city of coloured blocks
        BlockState[] toys = {Blocks.RED_CONCRETE.defaultBlockState(), Blocks.BLUE_CONCRETE.defaultBlockState(), Blocks.YELLOW_CONCRETE.defaultBlockState(),
                Blocks.LIME_CONCRETE.defaultBlockState()};
        for (int i = 0; i < 9; i++) {
            int x = -9 + i % 3, z = 3 + i / 3;
            for (int y = 1; y <= 1 + (i * 7) % 3; y++) {
                p.set(x, y, z, toys[(i + y) % toys.length]);
            }
        }
        // the train going round, a cart on it
        for (int x = 4; x <= 8; x++) {
            for (int z = 2; z <= 5; z++) {
                boolean edgeX = x == 4 || x == 8, edgeZ = z == 2 || z == 5;
                if (!edgeX && !edgeZ) {
                    continue;
                }
                net.minecraft.world.level.block.state.properties.RailShape shape = edgeX && edgeZ
                        ? (x == 4 ? (z == 2 ? net.minecraft.world.level.block.state.properties.RailShape.SOUTH_EAST : net.minecraft.world.level.block.state.properties.RailShape.NORTH_EAST)
                        : (z == 2 ? net.minecraft.world.level.block.state.properties.RailShape.SOUTH_WEST : net.minecraft.world.level.block.state.properties.RailShape.NORTH_WEST))
                        : edgeX ? net.minecraft.world.level.block.state.properties.RailShape.NORTH_SOUTH : net.minecraft.world.level.block.state.properties.RailShape.EAST_WEST;
                p.set(x, 1, z, Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE, shape));
            }
        }
        Builds.afterBuild(() -> {
            net.minecraft.world.entity.vehicle.Minecart cart = net.minecraft.world.entity.EntityType.MINECART.create(level);
            if (cart != null) {
                cart.moveTo(c.getX() + 6.5, c.getY() + 1, c.getZ() + 2.5);
                level.addFreshEntity(cart);
            }
        });
        // a robot by the monitors
        p.fill(-3, 1, -6, -3, 2, -6, Blocks.IRON_BLOCK.defaultBlockState());
        p.set(-3, 3, -6, Blocks.OBSERVER.defaultBlockState().setValue(net.minecraft.world.level.block.ObserverBlock.FACING, Direction.SOUTH));
        p.set(-4, 2, -6, Blocks.LEVER.defaultBlockState().setValue(net.minecraft.world.level.block.LeverBlock.FACING, Direction.WEST)
                .setValue(net.minecraft.world.level.block.LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL));
        p.set(-2, 2, -6, Blocks.LEVER.defaultBlockState().setValue(net.minecraft.world.level.block.LeverBlock.FACING, Direction.EAST)
                .setValue(net.minecraft.world.level.block.LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL));
        for (int[] q : new int[][]{{-11, 9}, {11, 9}, {-11, -1}}) {
            p.set(q[0], 1, q[1], Blocks.POTTED_BAMBOO.defaultBlockState());
        }
    }

    /** Near on the floor at his puzzle (now and then by the dice or the train), Rester behind him, Gevanni and Lidner at their desks. */
    private static void spkPeople(ServerLevel level, ServerPlayer player, BlockPos c) {
        Direction n = Direction.NORTH, e = Direction.EAST, w = Direction.WEST;
        HumanEntity.Stance floor = HumanEntity.Stance.FLOOR, desk = HumanEntity.Stance.DESK, stand = HumanEntity.Stance.STAND;
        Headquarters.spawn(level, player, c, HumanEntity.Role.NEAR, 0, c, Headquarters.post(c, 0, 1, 3, n, floor, 300),
                Headquarters.post(c, -4, 1, -2, w, floor, 120), Headquarters.post(c, 6, 1, 7, n, floor, 100));
        Headquarters.spawn(level, player, c, HumanEntity.Role.SPK, 0, c, Headquarters.post(c, -2, 1, 4, n, HumanEntity.Stance.BEHIND, 200),
                Headquarters.post(c, 0, 1, -6, n, stand, 60));
        Headquarters.spawn(level, player, c, HumanEntity.Role.SPK, 1, c, Headquarters.post(c, 10, 1, -5, e, desk, 200),
                Headquarters.post(c, -6, 1, -7, n, stand, 40));
        Headquarters.spawn(level, player, c, HumanEntity.Role.SPK, 2, c, Headquarters.post(c, 10, 1, -1, e, desk, 200),
                Headquarters.post(c, 3, 1, -6, n, HumanEntity.Stance.CROSSED, 40));
    }

    // ------------------------------------------------------------------ Mello

    /** Mello's trade: a girl from the nearest village for a notebook, by the next sunset. */
    static void kidnap(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        ServerLevel level = player.serverLevel();
        long now = level.getServer().overworld().getGameTime();
        List<UUID> seen = Lifespans.faces(player);
        AbstractVillager girl = level.getEntitiesOfClass(AbstractVillager.class, player.getBoundingBox().inflate(160), v -> v.isAlive() && !v.isBaby())
                .stream().min(Comparator.comparingInt((AbstractVillager v) -> (v.getUUID().getLeastSignificantBits() & 1) == 0 ? 0 : 1) // a girl, if any (Lifespans.trueName)
                        .thenComparingInt(v -> seen.contains(v.getUUID()) ? 0 : 1).thenComparingDouble(player::distanceToSqr))
                .orElse(null);
        if (girl == null || !c.flag("hideout")) {
            c.flags.put("hostage_done", now); // nobody to take: Mello drops the idea
            return;
        }
        BlockPos hall = Mafia.hall(level, BlockPos.of(c.flags.get("hideout")));
        c.flags.put("hostage_home", girl.blockPosition().asLong());
        c.flags.put("hostage_m", girl.getUUID().getMostSignificantBits());
        c.flags.put("hostage_l", girl.getUUID().getLeastSignificantBits());
        c.flags.put("hostage", Kira.clock(level.getServer()) / Kira.DAY * Kira.DAY + Kira.DAY + 12000); // the next sunset
        girl.setPersistenceRequired();
        Builds.afterBuild(() -> {
            girl.teleportTo(hall.getX() + Mafia.CELL_X + 0.5, hall.getY(), hall.getZ() + Mafia.CELL_Z + 0.5); // in the cell
            girl.setNoAi(true);
        });
        Kira.changed(player);
        String name = Names.shown(Lifespans.trueName(girl));
        player.sendSystemMessage(Component.literal("<Mello> ").withStyle(ChatFormatting.GOLD)
                .append(Component.translatable("aicompanion.mello.demand", name, Scouting.where(player.blockPosition(), hall)).withStyle(ChatFormatting.WHITE)));
        story(player, Config.tr("Mello похитил жителя деревни по имени " + name + " и держит в логове мафии. Отпустит за тетрадь смерти до следующего заката, иначе заложника убьют. Тебе любопытно, что выберет твой человек",
                "Mello has kidnapped a villager called " + name + " and holds them in the mafia hideout. He'll trade the hostage for a Death Note by the next sunset, or they die. You're curious what your human will choose"));
    }

    /** A notebook in hand, a right click on Mello: the girl goes home, he keeps the notebook. */
    static void giveToMello(ServerPlayer player, HumanEntity mello, InteractionHand hand) {
        Kira.Case c = Kira.of(player);
        if (!c.flag("hostage")) {
            Investigation.talkTo(player, mello);
            return;
        }
        long now = player.server.overworld().getGameTime();
        ItemStack note = player.getItemInHand(hand).copy();
        player.setItemInHand(hand, ItemStack.EMPTY);
        mello.takeNote(note);
        if (Mafia.isSidohsNote(note)) {
            for (SidohEntity s : player.serverLevel().getEntitiesOfClass(SidohEntity.class, player.getBoundingBox().inflate(64))) {
                s.unbind(mello.blockPosition().above(2)); // his notebook has a new keeper
            }
        }
        c.flags.remove("hostage");
        c.flags.put("hostage_done", now);
        c.flags.put("hostage_fate", 1L);
        player.sendSystemMessage(Component.literal("<Mello> ").withStyle(ChatFormatting.GOLD)
                .append(Component.translatable("aicompanion.mello.deal").withStyle(ChatFormatting.WHITE)));
        boolean real = !DeathNoteItem.fake(note);
        if (real) { // the gang turns it on the SPK, as in the manga
            c.suspicion = Math.max(0, c.suspicion - 10);
            player.sendSystemMessage(Component.translatable("aicompanion.mello.spk_hit").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        boolean none = !player.getInventory().hasAnyMatching(s -> s.is(AiCompanion.DEATH_NOTE.get()) && !DeathNoteItem.fake(s));
        boolean again = Onboarding.takenAway(player);
        Kira.changed(player);
        story(player, Config.tr("твой человек отдал Mello тетрадь за заложника, и того отпустили домой." + (real ? " Мафия тут же пустила тетрадь в ход против людей SPK." : "")
                        + (none ? " Настоящей тетради у твоего человека больше нет. Ха!" : "") + (again ? " Завтра утром ты бросишь ему новую" : ""),
                "your human gave Mello a notebook for the hostage, and the hostage was let go." + (real ? " The gang turned it on the SPK at once." : "")
                        + (none ? " Your human has no real notebook left. Ha!" : "") + (again ? " Tomorrow morning you'll drop them a new one" : "")));
    }

    private static void doom(ServerPlayer player, Kira.Case c, long now) {
        c.flags.remove("hostage");
        c.flags.put("hostage_done", now);
        c.flags.put("hostage_fate", 2L);
        Kira.get(player.server).setDirty();
        player.sendSystemMessage(Component.literal("<Mello> ").withStyle(ChatFormatting.GOLD)
                .append(Component.translatable("aicompanion.mello.too_late").withStyle(ChatFormatting.WHITE)));
        story(player, Config.tr("заложницу Mello убили: твой человек не принёс тетрадь до заката", "Mello's hostage is dead: your human didn't bring a notebook by sunset"));
    }

    /** The hostage, once she is loaded: home (fate 1) or dead (fate 2). */
    private static void hostageFate(ServerPlayer player, Kira.Case c) {
        Long fate = c.flags.get("hostage_fate");
        if (fate == null) {
            return;
        }
        LivingEntity girl = Lifespans.byUuid(player.server, new UUID(c.flags.get("hostage_m"), c.flags.get("hostage_l")));
        if (girl == null) {
            return; // not loaded right now: later
        }
        if (fate == 1) {
            BlockPos home = BlockPos.of(c.flags.get("hostage_home"));
            girl.teleportTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
            if (girl instanceof Mob m) {
                m.setNoAi(false);
            }
        } else {
            girl.kill();
        }
        for (String f : new String[]{"hostage_fate", "hostage_m", "hostage_l", "hostage_home"}) {
            c.flags.remove(f);
        }
        Kira.get(player.server).setDirty();
    }

    // ------------------------------------------------------------------ Gevanni

    /** Once a night ({@code night}: the day's number), while the owner is out. */
    private static void gevanni(ServerPlayer player, Kira.Case c, long night) {
        BlockPos bed = player.getRespawnPosition();
        if (bed == null || player.getRespawnDimension() != Level.OVERWORLD || bed.distSqr(player.blockPosition()) < 24 * 24) {
            return; // nobody slips into a house with its owner in it
        }
        c.flags.put("gevanni", night);
        Kira.get(player.server).setDirty();
        if (swap(player.serverLevel(), bed) > 0) {
            c.flags.putIfAbsent("swapped", player.server.overworld().getGameTime());
            story(player, Config.tr("пока твоего человека не было, Джеванни из SPK пробрался в дом и подменил тетрадь в сундуке точной копией, строчка в строчку. Запись в копии ничего не делает. Ты всё видел и ничего не скажешь",
                    "while your human was out, Gevanni of the SPK slipped into the house and swapped the notebook in the chest for a perfect copy, line for line. Writing in the copy does nothing. You saw it all; you'll say nothing"));
        }
    }

    /** Every real notebook in the containers by the bed becomes a perfect copy; the false bottom stays unfound. */
    static int swap(ServerLevel level, BlockPos bed) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(bed.offset(-8, -3, -8), bed.offset(8, 3, 8))) {
            if (Hideouts.isHidden(level, p) || !(level.getBlockEntity(p) instanceof Container box)) {
                continue;
            }
            for (int i = 0; i < box.getContainerSize(); i++) {
                ItemStack s = box.getItem(i);
                if (s.is(AiCompanion.DEATH_NOTE.get()) && !DeathNoteItem.fake(s)) {
                    ItemStack copy = s.copy();
                    copy.getOrCreateTag().putBoolean("Fake", true);
                    box.setItem(i, copy);
                    n++;
                }
            }
        }
        return n;
    }

    /** Somebody wrote in a copy; in the warehouse Near counts forty seconds from it. */
    static void onFakeWrite(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        if (c.flag("showdown") && !c.flag("fake_write")) {
            c.flags.put("fake_write", player.server.overworld().getGameTime());
        }
    }

    // ------------------------------------------------------------------ the Yellow Box

    /** At 80%: Near calls Kira to the Yellow Box warehouse at dawn; he and his people wait there. */
    static void invite(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        ServerLevel level = player.serverLevel();
        long now = player.server.overworld().getGameTime();
        BlockPos box;
        if (c.flag("yellow_box")) {
            box = BlockPos.of(c.flags.get("yellow_box"));
        } else {
            box = Headquarters.site(level, player, 120, BOX_X + 2);
            c.flags.put("yellow_box", box.asLong());
            buildWarehouse(level, box);
            Builds.afterBuild(() -> people(level, player, box));
        }
        c.flags.put("meeting", Kira.clock(level.getServer()) / Kira.DAY * Kira.DAY + Kira.DAY); // at dawn
        Kira.changed(player);
        player.sendSystemMessage(Component.literal("N ").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)
                .append(Component.translatable("aicompanion.near.invite", box.getX(), box.getZ()).withStyle(ChatFormatting.WHITE)));
        Network.card(player, Network.Broadcast.CHAPTER, Component.translatable("aicompanion.card.box"), Component.translatable("aicompanion.card.box_text"), "", box);
        story(player, Config.tr("Near вызвал Киру на встречу: на рассвете, склад Yellow Box (" + box.getX() + ", " + box.getZ() + "). Он уверен, что всё решится там. Ты в предвкушении: это как финал",
                "Near has called Kira to a meeting: at dawn, the Yellow Box warehouse (" + box.getX() + ", " + box.getZ() + "). He's sure it ends there. You can't wait: it feels like a finale"));
    }

    /** Near on the floor in the middle, facing the door, a die and a puzzle piece by him; the SPK behind; Matsuda and Aizawa at the sides. */
    private static void people(ServerLevel level, ServerPlayer player, BlockPos box) {
        Direction s = Direction.SOUTH;
        Headquarters.spawn(level, player, box, HumanEntity.Role.NEAR, 0, box, Headquarters.post(box, 0, 1, -3, s, HumanEntity.Stance.FLOOR, 600));
        int[][] spk = {{-2, -5}, {2, -5}, {0, -6}};
        HumanEntity.Stance[] how = {HumanEntity.Stance.BEHIND, HumanEntity.Stance.CROSSED, HumanEntity.Stance.BEHIND};
        for (int i = 0; i < 3; i++) {
            Headquarters.spawn(level, player, box, HumanEntity.Role.SPK, i, box, Headquarters.post(box, spk[i][0], 1, spk[i][1], s, how[i], 600));
        }
        for (int i = 0; i < 2; i++) {
            Headquarters.spawn(level, player, box, HumanEntity.Role.DETECTIVE, i, box,
                    Headquarters.post(box, i == 0 ? -6 : 6, 1, -2, i == 0 ? Direction.EAST : Direction.WEST, HumanEntity.Stance.CROSSED, 600));
        }
    }

    /** The meeting: early, on time (the showdown), or not at all. */
    private static void meeting(ServerPlayer player, Kira.Case c, long now) {
        BlockPos box = BlockPos.of(c.flags.get("yellow_box"));
        long meeting = c.flags.get("meeting"), day = Kira.clock(player.server);
        boolean inside = Math.abs(player.getX() - box.getX() - 0.5) < BOX_X && Math.abs(player.getZ() - box.getZ() - 0.5) < BOX_Z
                && player.getY() >= box.getY() && player.getY() < box.getY() + 9;
        if (!c.flag("showdown")) {
            if (inside && day < meeting && !c.flag("early")) {
                c.flags.put("early", now);
                near(player, "aicompanion.near.early");
            } else if (inside && day >= meeting) {
                c.flags.put("showdown", now);
                c.flags.remove("early");
                near(player, "aicompanion.near.showdown1");
                player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.6F);
                story(player, Config.tr("твой человек пришёл на склад Yellow Box. Near, SPK и полиция ждали его. Сейчас решится, кто кого: Near уверен в себе. Ты знаешь, какая тетрадь у твоего человека настоящая, но не скажешь",
                        "your human walked into the Yellow Box warehouse. Near, the SPK and the police were waiting. Now it's decided who wins: Near is sure of himself. You know whether your human's notebook is the real one, but you won't tell"));
            } else if (day > meeting + 12000) { // sunset, and nobody came
                c.flags.remove("meeting");
                c.flags.remove("early");
                near(player, "aicompanion.near.no_show");
                Investigation.suspect(player, 100, "Yellow Box");
            }
            return;
        }
        long t = (now - c.flags.get("showdown")) / 20;
        if (t == 8) {
            near(player, "aicompanion.near.showdown2");
        } else if (t == 20) {
            near(player, "aicompanion.near.showdown3");
        }
        if (c.flag("fake_write") && now >= c.flags.get("fake_write") + DeathNotes.DELAY) {
            expose(player, c, "aicompanion.near.copy");
        } else if (t >= SHOWDOWN && !DeathNotes.dueSoon(player.server, player.getUUID(), 60 * 20)) {
            if (c.flag("showdown_deaths")) {
                expose(player, c, "aicompanion.near.deaths");
            } else if (c.flag("swapped")) {
                expose(player, c, "aicompanion.near.proof");
            } else { // no proof today
                near(player, "aicompanion.near.not_today");
                c.flags.remove("showdown");
                c.flags.put("meeting", day / Kira.DAY * Kira.DAY + 3 * Kira.DAY);
                Kira.get(player.server).setDirty();
            }
        }
    }

    /** Near has his proof. */
    private static void expose(ServerPlayer player, Kira.Case c, String key) {
        for (String f : new String[]{"showdown", "fake_write", "showdown_deaths", "meeting", "early"}) {
            c.flags.remove(f);
        }
        near(player, key);
        story(player, Config.tr("на складе Yellow Box Near доказал, что твой человек — Кира. Ты так и знал", "at the Yellow Box Near proved your human is Kira. You saw it coming"));
        Investigation.suspect(player, 100, "Yellow Box");
    }

    private static void near(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.literal("<Near> ").withStyle(ChatFormatting.GRAY)
                .append(Component.translatable(key).withStyle(ChatFormatting.WHITE)));
    }

    /** A story person's death that matters here: Near (Kira wins), Mello (his last lead), anyone at the showdown. */
    static void onDeath(ServerPlayer player, HumanEntity human, boolean byNote) {
        Kira.Case c = Kira.of(player);
        long now = player.server.overworld().getGameTime();
        if (human.role() == HumanEntity.Role.NEAR) {
            if (!c.sealed && c.stage == Investigation.NEAR) {
                kiraWins(player);
            }
        } else if (human.role() == HumanEntity.Role.MELLO) {
            c.flags.put("mello_dead", now);
            if (c.flag("hostage")) {
                c.flags.remove("hostage");
                c.flags.put("hostage_done", now);
                c.flags.put("hostage_fate", 1L);
                player.sendSystemMessage(Component.translatable("aicompanion.mello.freed").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
            if (c.stage == Investigation.NEAR && !c.flag("kira_wins")) {
                near(player, "aicompanion.near.mello_lead");
                Investigation.suspect(player, 15, "Mello's last lead");
            }
            story(player, Config.tr("Mello мёртв. Перед смертью он успел сделать свой ход: Near стал на шаг ближе к Кире",
                    "Mello is dead. He made his move before he went: Near is a step closer to Kira"));
        } else if (byNote && c.flag("showdown") && human.home() != null && human.home().asLong() == c.flags.get("yellow_box")) {
            c.flags.put("showdown_deaths", now); // they died in front of Near: Kira is in this room
        }
        Kira.get(player.server).setDirty();
    }

    /** Near is dead: nobody is left to hunt Kira. The new world. */
    static void kiraWins(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        c.flags.put("kira_wins", player.server.overworld().getGameTime());
        c.suspicion = 0;
        c.flags.put("glory", 100L);
        for (String f : new String[]{"showdown", "meeting", "fake_write", "showdown_deaths", "early"}) {
            c.flags.remove(f);
        }
        Kira.changed(player);
        player.sendSystemMessage(Component.translatable("aicompanion.kira.wins").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        Network.cue(player, "music.kira"); // a new world
        Pact.thrill(player, Pact.THRILL_BIG);
        Epilogue.start(player); // the film of it, Ryuk's last words at its end
    }

    /** Near and the SPK moved to the Yellow Box (the Task Force with them): the ones left at their old posts go. */
    static boolean stillHere(HumanEntity h) {
        if (h.about() == null || h.getServer() == null) {
            return true;
        }
        Kira.Case c = Kira.find(h.getServer(), h.about());
        if (c == null || !c.flag("yellow_box")) {
            return true;
        }
        long box = c.flags.get("yellow_box");
        return switch (h.role()) {
            case NEAR, SPK -> h.home() != null && h.home().asLong() == box;
            case DETECTIVE -> h.home() != null || !c.flag("hq") // the HQ's went to the warehouse with Near; the rest stay
                    || !h.blockPosition().closerThan(BlockPos.of(c.flags.get("hq")), 40);
            default -> true;
        };
    }

    // ------------------------------------------------------------------ the warehouse

    /** A hangar of corrugated grey with the yellow band that gives it its name, containers inside, the floor bare in the middle. */
    /**
     * The Yellow Box: a corrugated warehouse on a concrete apron, its big door open on the south, a small door beside
     * it, the sign above. Inside it's dim: containers along the walls, crates and pallets, lamps on chains, and the
     * middle of the floor left clear, where Near waits.
     */
    private static void buildWarehouse(ServerLevel level, BlockPos c) {
        Builds.Plan p = new Builds.Plan(level, c);
        BlockState air = Blocks.AIR.defaultBlockState(), light = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState(), dark = Blocks.GRAY_CONCRETE.defaultBlockState();
        p.fill(-BOX_X - 2, 1, -BOX_Z - 2, BOX_X + 2, 12, BOX_Z + 6, air);
        p.footing(-BOX_X - 2, -BOX_Z - 2, BOX_X + 2, BOX_Z + 6, 0, Blocks.STONE.defaultBlockState());
        p.fill(-BOX_X - 2, 0, -BOX_Z - 2, BOX_X + 2, 0, BOX_Z + 6, Blocks.SMOOTH_STONE.defaultBlockState());
        p.fill(-BOX_X, 0, -BOX_Z, BOX_X, 0, BOX_Z, dark);
        for (int x = -BOX_X; x <= BOX_X; x++) {
            for (int z = -BOX_Z; z <= BOX_Z; z++) {
                if (Math.abs(x) != BOX_X && Math.abs(z) != BOX_Z) {
                    continue;
                }
                for (int y = 1; y <= 8; y++) {
                    p.set(x, y, z, y == 7 ? Blocks.YELLOW_CONCRETE.defaultBlockState() : (x + z) % 2 == 0 ? light : dark); // corrugated
                }
            }
        }
        p.fill(-BOX_X, 9, -BOX_Z, BOX_X, 9, BOX_Z, dark);
        for (int x = -9; x <= 9; x += 6) {
            p.set(x, 9, 0, Blocks.GLASS.defaultBlockState()); // skylights
            p.set(x, 9, -5, Blocks.GLASS.defaultBlockState());
            p.set(x, 8, -3, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
            p.set(x, 7, -3, Builds.lantern(true));
            p.set(x, 8, 4, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
            p.set(x, 7, 4, Builds.lantern(true));
        }
        // the big door, open; the small one beside it; the sign; the apron outside
        p.fill(-2, 1, BOX_Z, 2, 4, BOX_Z, air);
        p.door(6, 1, BOX_Z, Blocks.IRON_DOOR, Direction.NORTH);
        p.set(6, 3, BOX_Z + 1, Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, Direction.SOUTH));
        p.set(0, 6, BOX_Z + 1, Builds.wallSign(Blocks.DARK_OAK_WALL_SIGN, Direction.SOUTH), sign("YELLOW BOX"));
        for (int x : new int[]{-4, 4}) {
            p.set(x, 1, BOX_Z + 2, Blocks.STONE_BRICK_WALL.defaultBlockState()); // bollards
        }
        p.fill(9, 1, BOX_Z + 3, 9, 3, BOX_Z + 3, Blocks.DARK_OAK_FENCE.defaultBlockState());
        p.set(9, 4, BOX_Z + 3, Builds.lantern(false));
        p.set(-7, 1, BOX_Z + 2, Builds.trapdoor(Blocks.SPRUCE_TRAPDOOR, Direction.NORTH, false, false));
        p.set(-8, 1, BOX_Z + 2, Builds.trapdoor(Blocks.SPRUCE_TRAPDOOR, Direction.NORTH, false, false));
        p.set(-8, 1, BOX_Z + 3, Blocks.BARREL.defaultBlockState());
        // containers along the side walls, their doors (bars) toward the aisle
        BlockState[] colors = {Blocks.BLUE_CONCRETE.defaultBlockState(), Blocks.RED_CONCRETE.defaultBlockState(), Blocks.GREEN_CONCRETE.defaultBlockState(),
                Blocks.ORANGE_CONCRETE.defaultBlockState()};
        int[][] cont = {{-BOX_X + 1, -6}, {-BOX_X + 1, -1}, {BOX_X - 3, -6}, {BOX_X - 3, 2}};
        for (int i = 0; i < cont.length; i++) {
            int x0 = cont[i][0], z0 = cont[i][1];
            p.fill(x0, 1, z0, x0 + 2, 3, z0 + 3, colors[i]);
            int doorX = x0 < 0 ? x0 + 3 : x0 - 1;
            p.fill(doorX, 1, z0 + 1, doorX, 3, z0 + 2, Blocks.IRON_BARS.defaultBlockState());
        }
        // crates, pallets
        for (int[] q : new int[][]{{-11, 5}, {-10, 5}, {-11, 6}, {-11, 4}, {11, -2}, {10, -2}, {11, -1}, {-5, -7}, {-4, -7}, {5, -7}}) {
            p.set(q[0], 1, q[1], (q[0] + q[1]) % 2 == 0 ? Blocks.BARREL.defaultBlockState() : Blocks.CHEST.defaultBlockState());
        }
        p.set(-11, 2, 5, Blocks.BARREL.defaultBlockState());
        p.set(11, 2, -2, Blocks.BARREL.defaultBlockState());
        p.set(-4, 2, -7, Blocks.BARREL.defaultBlockState());
        for (int[] q : new int[][]{{8, 6}, {9, 6}, {-8, 2}, {7, -5}}) {
            p.set(q[0], 1, q[1], Builds.trapdoor(Blocks.SPRUCE_TRAPDOOR, Direction.NORTH, false, false));
        }
        p.fill(10, 1, 6, 10, 2, 6, Blocks.SCAFFOLDING.defaultBlockState());
        // by Near: a die and a puzzle piece
        p.set(1, 1, -3, Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState());
        p.set(-1, 1, -4, Blocks.WHITE_CARPET.defaultBlockState());
    }

    private static CompoundTag banner(ListTag patterns) {
        CompoundTag tag = new CompoundTag();
        tag.put("Patterns", patterns);
        return tag;
    }

    private static CompoundTag banner(BannerPattern.Builder patterns) {
        return banner(patterns.toListTag());
    }

    private static CompoundTag sign(String line) {
        ListTag messages = new ListTag();
        for (String s : new String[]{"", line, "", ""}) {
            messages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(s))));
        }
        CompoundTag front = new CompoundTag();
        front.put("messages", messages);
        front.putString("color", "yellow");
        front.putBoolean("has_glowing_text", true);
        CompoundTag tag = new CompoundTag();
        tag.put("front_text", front);
        return tag;
    }

    // ------------------------------------------------------------------ what Near and Ryuk know

    /** Near's character for the LLM (Headquarters.ask), like L's. */
    static String persona(ServerPlayer player, Kira.Case c) {
        List<String> f = new java.util.ArrayList<>();
        f.add("Kira killed L; you took over the case with the SPK");
        f.add(c.kills + " deaths fit Kira's pattern");
        if (c.flag("mello") && !c.flag("mello_dead")) {
            f.add("Mello, the other successor, works with the mafia and wants the notebook for himself");
        }
        if (c.flag("swapped")) {
            f.add("your agent Gevanni swapped a notebook found in this human's house for a copy; never reveal it before the Yellow Box");
        }
        if (c.flag("meeting")) {
            f.add("you have called Kira to the Yellow Box warehouse at dawn");
        }
        return """
                You are Near, L's successor and the head of the SPK, inside this player's Minecraft world (nobody knows your real name).
                You hunt Kira, who killed L. You sit on the floor with one knee up, twist a lock of your white hair, build towers of dice,
                do a blank white puzzle or move finger puppets of L and Kira. You speak calmly and flatly, with cold logic and a little
                arrogance; you never shout; you respect L and mean to finish what he started; you never accuse without proof and you
                set traps instead. The human in front of you is %s. Your estimate that they are Kira: %.0f%%.
                What you know: %s.
                Answer what they said in 1-3 short sentences, in %s. Never mention game mechanics, AI or Minecraft as a game.
                Reply ONLY with JSON: {"message": "<your line>", "suspicion_change": <number from -5 to 10: up when they slip, know what
                only Kira could know, defend Kira, talk about the notebook, shinigami or apples oddly, or lie; down a little when they answer
                convincingly and calmly; 0 otherwise>}
                """.formatted(player.getGameProfile().getName(), c.suspicion, String.join("; ", f), Config.english() ? "English" : "Russian");
    }

    /** For Ryuk's context: what the successors are up to. */
    static String describe(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        StringBuilder sb = new StringBuilder();
        if (c.flag("kira_wins")) {
            return Config.tr("Кира победил: L и Near мертвы, охотиться на твоего человека больше некому. ",
                    "Kira has won: L and Near are dead, nobody hunts your human any more. ");
        }
        if (c.flag("spk")) {
            BlockPos spk = BlockPos.of(c.flags.get("spk"));
            sb.append(Config.tr("Дело ведёт Near, преемник L, со своей SPK (база в " + spk.getX() + ", " + spk.getZ() + "). ",
                    "Near, L's successor, runs the case with his SPK (base at " + spk.getX() + ", " + spk.getZ() + "). "));
        }
        if (c.flag("mello") && !c.flag("mello_dead")) {
            sb.append(Config.tr("Mello, второй преемник, заодно с мафией, сидит в их логове. ", "Mello, the other successor, sides with the mafia and sits in their hideout. "));
        }
        if (c.flag("hostage")) {
            sb.append(Config.tr("Mello держит заложника и ждёт тетрадь до заката. ", "Mello holds a hostage and waits for a notebook until sunset. "));
        }
        if (c.flag("swapped")) {
            sb.append(Config.tr("Джеванни подменил тетрадь в доме твоего человека копией: в копии ничего не работает. Ты это знаешь и не скажешь. ",
                    "Gevanni swapped the notebook in your human's house for a copy: nothing written in it works. You know and won't tell. "));
        }
        if (c.flag("meeting")) {
            BlockPos box = BlockPos.of(c.flags.get("yellow_box"));
            sb.append(Config.tr("Near ждёт Киру на складе Yellow Box (" + box.getX() + ", " + box.getZ() + ") на рассвете. ",
                    "Near waits for Kira at the Yellow Box warehouse (" + box.getX() + ", " + box.getZ() + ") at dawn. "));
        }
        return sb.toString();
    }

    private static void story(ServerPlayer player, String event) {
        CompanionEntity ryuk = CompanionEntity.of(player);
        if (ryuk != null) {
            ryuk.remark(event, "story", 0);
        }
    }
}
