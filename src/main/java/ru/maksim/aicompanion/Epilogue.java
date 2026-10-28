package ru.maksim.aicompanion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The end of the story when Kira wins (Successors.kiraWins), as a film. The title over the warehouse where it ended;
 * the world changing before one's eyes: in each village around (three at most) a shrine to Kira and a crowd before it
 * chanting his name, the camera at each in turn; back where it ended, Kira's address on every screen; Ryuk's last words,
 * his wings, the end. While the camera travels the player watches as a spectator: their place and game mode come back
 * (on the next login too, if the game was quit midway).
 */
public final class Epilogue {
    private static final int TITLE = 180, VISIT = 160, PROBES = 5;
    private static final String CROWD = "aicompanion_worshipper";
    private static final Map<UUID, Film> FILMS = new HashMap<>();

    /** Ryuk's last words: his own, not the model's. */
    private static final String[][] LAST = {
            {"happy", "Хе-хе… Ну вот и всё. L, Near… никого не осталось.", "Heh heh... So that's it. L, Near... nobody's left."},
            {"neutral", "А знаешь, люди и правда интересные. Я не ошибся, когда уронил тетрадь именно тебе.",
                    "You know, humans really are interesting. I wasn't wrong to drop the notebook on you."},
            {"happy", "Только теперь станет скучно… Ну ничего. Яблоки-то у тебя ещё остались?", "Only now it'll get boring... Oh well. Got any apples left?"}};

    private static final class Film {
        final long start;
        final List<BlockPos> villages = new ArrayList<>(), shrines = new ArrayList<>();
        final List<Vec3> views = new ArrayList<>();
        final List<List<UUID>> crowds = new ArrayList<>();
        int probes;
        boolean back;
        long backAt, nextWord, end = -1;
        int words;

        Film(long start) {
            this.start = start;
        }
    }

    private Epilogue() {
    }

    static boolean running(@Nullable ServerPlayer player) {
        return player != null && FILMS.containsKey(player.getUUID());
    }

    /** Kira has won: the film begins where it ended. */
    static void start(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        c.flags.put("epi_from", player.blockPosition().asLong());
        c.flags.put("epi_mode", (long) player.gameMode.getGameModeForPlayer().getId());
        Kira.get(player.server).setDirty();
        FILMS.put(player.getUUID(), new Film(player.serverLevel().getGameTime()));
        Network.cue(player, "cine.epilogue");
    }

    /** Back in the game after quitting midway: back where it ended, as they were. */
    static void login(ServerPlayer player) {
        if (!running(player) && Kira.of(player).flag("epi_from")) {
            restore(player);
        }
    }

    /** Every server tick: each film's beats. */
    static void tick(MinecraftServer server) {
        if (FILMS.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, Film>> it = FILMS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Film> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null || !player.isAlive() || step(player, e.getValue())) {
                it.remove();
            }
        }
    }

    /** One tick of the film; true when it's over. */
    private static boolean step(ServerPlayer player, Film f) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime(), t = now - f.start;
        CompanionEntity ryuk = CompanionEntity.of(player);
        if (t < TITLE) { // the title; meanwhile the villages around are found, one look at a time, and their shrines go up
            if (t % 10 == 0 && f.probes < PROBES) {
                probe(player, f);
            }
            if (t == PROBES * 10) {
                prepare(level, player, f);
            }
            return false;
        }
        int v = (int) ((t - TITLE) / VISIT);
        if (v < f.shrines.size()) { // the camera at each village in turn
            long in = (t - TITLE) % VISIT;
            if (in == 0) {
                visit(player, f, v, ryuk);
            } else if (in == 8) {
                f.crowds.add(crowd(level, f.shrines.get(v), f.views.get(v)));
            } else if (in > 30 && in % 30 == 0) {
                cheer(player, level, f.crowds.get(v), in == 60);
            } else if (in == VISIT - 1) {
                for (UUID id : f.crowds.get(v)) { // the camera leaves: they're villagers again
                    if (level.getEntity(id) instanceof Villager villager) {
                        villager.setNoAi(false);
                    }
                }
            }
            return false;
        }
        if (!f.back) { // back where it ended: Kira's address on every screen
            f.back = true;
            f.backAt = now;
            restore(player);
            if (ryuk != null) {
                Vec3 look = player.getLookAngle().multiply(1, 0, 1).normalize();
                Vec3 front = player.position().add(look.scale(2.6)).add(0, 0.3, 0);
                ryuk.teleportTo(front.x, front.y, front.z);
                ryuk.hold(front);
                ryuk.attendTo(player, 2400);
            }
            Network.card(player, Network.Broadcast.SHOW, Component.translatable("aicompanion.card.special"), Component.empty(), "K", null);
            return false;
        }
        if (Network.floorBusy(player) || now < f.backAt + 40) {
            return false; // the address still being read
        }
        if (f.words < LAST.length) { // Ryuk's last words, each when the one before is said
            if (ryuk == null) {
                f.words = LAST.length;
            } else if (!ryuk.saying() && now >= f.nextWord) {
                String[] w = LAST[f.words++];
                ryuk.script(w[0], Config.tr(w[1], w[2]));
                f.nextWord = now + 40;
            }
            return false;
        }
        if (f.end < 0) { // the end: his wings, the title
            if (ryuk != null && ryuk.saying()) {
                return false;
            }
            f.end = now;
            if (ryuk != null) {
                ryuk.act("show_wings", 120);
            }
            Network.cue(player, "cine.end");
            Network.cue(player, "music.kira");
            return false;
        }
        if (now < f.end + 200) {
            return false;
        }
        if (ryuk != null) {
            ryuk.hold(null);
        }
        Kira.of(player).flags.put("epilogue", now);
        Kira.get(player.server).setDirty();
        return true;
    }

    /** A village around: the nearest, then the ones beyond, 400 blocks off each way (within 900, a different one each). */
    private static void probe(ServerPlayer player, Film f) {
        BlockPos at = BlockPos.of(Kira.of(player).flags.getOrDefault("epi_from", player.blockPosition().asLong()));
        int[][] dirs = {{0, 0}, {400, 0}, {-400, 0}, {0, 400}, {0, -400}};
        int[] d = dirs[f.probes++];
        BlockPos v = player.serverLevel().findNearestMapStructure(StructureTags.VILLAGE, at.offset(d[0], 0, d[1]), 2, false);
        if (v != null && v.distSqr(at) < 900 * 900 && f.villages.stream().noneMatch(o -> o.distSqr(v) < 150 * 150)) {
            f.villages.add(v);
        }
    }

    /** The three nearest villages: a shrine in each (one there already stays), and where the camera looks at it from. */
    private static void prepare(ServerLevel level, ServerPlayer player, Film f) {
        BlockPos at = BlockPos.of(Kira.of(player).flags.getOrDefault("epi_from", player.blockPosition().asLong()));
        f.villages.sort(Comparator.comparingDouble(v -> v.distSqr(at)));
        Kira.Case c = Kira.of(player);
        BlockPos old = c.flag("shrine") ? BlockPos.of(c.flags.get("shrine")) : null;
        for (BlockPos v : f.villages.subList(0, Math.min(3, f.villages.size()))) {
            BlockPos shrine = old != null && old.distSqr(v) < 120 * 120 ? old : Builds.flatNear(level, v, 9, 2);
            if (shrine != old) {
                Worship.build(level, shrine);
            }
            f.shrines.add(shrine);
            f.views.add(view(level, shrine));
        }
        AiCompanion.LOGGER.info("Epilogue for {}: shrines at {}", player.getGameProfile().getName(), f.shrines);
    }

    /** Ten blocks off the shrine, up over the roofs, on the side with the least in the way. */
    private static Vec3 view(ServerLevel level, BlockPos shrine) {
        int[][] dirs = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        int best = 0, low = Integer.MAX_VALUE;
        for (int i = 0; i < dirs.length; i++) {
            int top = 0;
            for (int s = 2; s <= 10; s++) {
                top = Math.max(top, Builds.ground(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, shrine.offset(dirs[i][0] * s, 0, dirs[i][1] * s)).getY());
            }
            if (top < low) {
                low = top;
                best = i;
            }
        }
        double y = Math.max(low + 2, shrine.getY() + 6);
        return new Vec3(shrine.getX() + 0.5 + dirs[best][0] * 10, y, shrine.getZ() + 0.5 + dirs[best][1] * 10);
    }

    /** The camera at village {@code v}: the player a spectator up there looking at the shrine, Ryuk out of the shot behind. */
    private static void visit(ServerPlayer player, Film f, int v, @Nullable CompanionEntity ryuk) {
        ServerLevel level = player.serverLevel();
        BlockPos shrine = f.shrines.get(v);
        Vec3 view = f.views.get(v), target = Vec3.atBottomCenterOf(shrine).add(0, 1.5, 0), d = target.subtract(view);
        float yaw = (float) (Math.atan2(d.z, d.x) * 180 / Math.PI) - 90, pitch = (float) -(Math.atan2(d.y, d.horizontalDistance()) * 180 / Math.PI);
        if (v == 0) {
            player.setGameMode(GameType.SPECTATOR);
        }
        if (ryuk != null) { // first, so he never has to catch up (with a puff of smoke in the shot)
            Vec3 behind = view.subtract(d.multiply(1, 0, 1).normalize().scale(3));
            ryuk.teleportTo(behind.x, behind.y, behind.z);
            ryuk.hold(behind);
        }
        player.teleportTo(level, view.x, view.y, view.z, yaw, pitch);
        Network.cue(player, String.format(Locale.ROOT, "cine.village.%d@%.1f,%.1f,%.1f", v + 1, target.x, target.y, target.z));
    }

    /** Eight of the village before its shrine, between it and the camera, facing it. */
    private static List<UUID> crowd(ServerLevel level, BlockPos shrine, Vec3 view) {
        List<UUID> out = new ArrayList<>();
        Vec3 c = Vec3.atBottomCenterOf(shrine);
        double toward = Math.atan2(view.z - c.z, view.x - c.x);
        for (int i = 0; i < 8; i++) {
            double a = toward + (i - 3.5) * 0.32, r = 3.6 + (i % 2) * 1.3;
            BlockPos ground = Builds.ground(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r));
            Villager villager = EntityType.VILLAGER.create(level);
            if (villager == null) {
                continue;
            }
            float yaw = (float) (Math.atan2(c.z - (ground.getZ() + 0.5), c.x - (ground.getX() + 0.5)) * 180 / Math.PI) - 90;
            villager.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, yaw, 0);
            villager.setYHeadRot(yaw);
            villager.setYBodyRot(yaw);
            villager.setNoAi(true);
            villager.setPersistenceRequired();
            villager.addTag(CROWD);
            level.addFreshEntity(villager);
            out.add(villager.getUUID());
        }
        return out;
    }

    /** A few of them jump, cheer, the name goes up (in the chat, once). */
    private static void cheer(ServerPlayer player, ServerLevel level, List<UUID> crowd, boolean chant) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (UUID id : crowd) {
            if (r.nextInt(3) == 0 && level.getEntity(id) instanceof Villager villager) {
                villager.setDeltaMovement(0, 0.42, 0);
                villager.hurtMarked = true;
                level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.2F, 0.9F + r.nextFloat() * 0.3F);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 2.2, villager.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
            }
        }
        if (chant) {
            player.sendSystemMessage(Component.literal("<").append(Component.translatable("aicompanion.epilogue.crowd")).append("> ")
                    .withStyle(ChatFormatting.GOLD).append(Component.translatable("aicompanion.epilogue.chant").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD)));
        }
    }

    /** Where it ended, in the game mode they had. */
    private static void restore(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        Long from = c.flags.remove("epi_from"), mode = c.flags.remove("epi_mode");
        Kira.get(player.server).setDirty();
        if (mode != null) {
            player.setGameMode(GameType.byId(mode.intValue()));
        }
        if (from != null) {
            BlockPos at = BlockPos.of(from);
            player.teleportTo(player.server.overworld(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot(), 0);
        }
    }
}
