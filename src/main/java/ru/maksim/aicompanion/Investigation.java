package ru.maksim.aicompanion;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * L's side of the story: who is Kira? The investigation reads the pattern the notebook leaves (heart attacks are Kira's
 * signature; deaths near the writer, of people he was close to, of investigators; witnesses) and answers in stages:
 * 1. rumours: the villages whisper about "Kira";
 * 2. the Lind L. Tailor broadcast: a special on every channel (TelevisionBlock), a decoy "L" calls Kira evil (write his
 *    name while he's on air, and L knows Kira is in this region and needs only a face and a name);
 * 3. FBI agents tail the suspect (one may show his badge, and with it his name, when monsters come close);
 * 4. L's headquarters goes up, L invites the suspect; at night the Task Force searches the house near the bed and puts
 *    cameras up (a camera that sees the notebook in use catches Kira red-handed);
 * 5. at 100% L has his proof: the arrest, and under Ryuk's game, the end.
 * After L's death his successors take over: Near with the SPK, Mello with the mafia, the Yellow Box (see Successors).
 * Suspicion ("the probability that you are Kira", as L would say) slowly fades while the notebook rests.
 */
public final class Investigation {
    public static final int RUMOURS = 1, BROADCAST = 2, AGENTS = 3, HEADQUARTERS = 4, SUCCESSORS = 6, NEAR = 7;
    private static final long BROADCAST_LENGTH = 12000; // ten minutes on air
    static final UUID TAILOR = new UUID(0x5461696C6F72L, 1); // "Tailor": his line in the notebook (he's on the screens, not here)

    private Investigation() {
    }

    // ------------------------------------------------------------------ evidence

    /**
     * Raises (or lowers) L's estimate; at 100 he has his proof (true: the arrest). Right after an arrest the case has to
     * be built again: for a day only proof (caught red-handed, the Yellow Box) brings a new one.
     */
    static boolean suspect(ServerPlayer player, float by, String why) {
        Kira.Case c = Kira.of(player);
        boolean proof = why.equals("red-handed") || why.equals("Yellow Box");
        if (c.flag("kira_wins") || c.flag("showdown") && by > 0 && !proof) {
            return false; // Kira won and nobody looks; in the warehouse only the verdict counts
        }
        if (c.flag("l_dead") && !c.flag("successor")) {
            by *= 0.3F; // nobody is looking right now
        }
        float before = c.suspicion;
        c.suspicion = Math.max(0, Math.min(100, c.suspicion + by));
        for (int mark : new int[]{90, 75, 50}) { // the highest mark passed, once
            if (before < mark && c.suspicion >= mark && c.suspicion < 100) {
                Pact.warn(player, Component.translatable("aicompanion.warn.suspicion_short", mark),
                        Component.translatable("aicompanion.warn.suspicion", mark), ChatFormatting.RED);
                break;
            }
        }
        long now = player.server.overworld().getGameTime();
        if (c.suspicion >= 100 && !proof && now < c.flags.getOrDefault("arrested", -2 * Kira.DAY) + Kira.DAY) {
            c.suspicion = 99; // freshly arrested: the case starts over
        }
        Kira.get(player.server).setDirty();
        AiCompanion.LOGGER.info("Suspicion of {} {} {} ({}): {}%", player.getGameProfile().getName(), by >= 0 ? "+" : "", by, why,
                String.format(java.util.Locale.ROOT, "%.1f", c.suspicion));
        if (c.suspicion >= 100 && !c.flag("arrest")) {
            arrest(player, why);
            return true;
        }
        return false;
    }

    /**
     * Somebody may have seen the notebook in use. {@code concealment}: 0 = the open notebook, 1 = a torn page, 2 = the
     * watch's scrap, 3 = a page inside a bag of chips (the cameras see a boy eating chips).
     */
    public static void witnessed(ServerPlayer writer, int concealment) {
        ServerLevel level = writer.serverLevel();
        double radius = switch (concealment) {
            case 0 -> 16;
            case 1 -> 6;
            case 2 -> 3;
            default -> 2;
        };
        long now = writer.server.overworld().getGameTime();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, writer.getBoundingBox().inflate(radius),
                e -> e != writer && e.isAlive() && Lifespans.human(e) && !(e instanceof Player) && !Worship.criminal(e) // crooks don't go to the police
                        && !(e instanceof HumanEntity h && h.role() == HumanEntity.Role.TAILOR))) { // nor does a man under the studio lights
            if (!e.hasLineOfSight(writer) || !Lifespans.faceVisible(writer, e)) { // their face toward you: they're looking
                continue;
            }
            if (e instanceof HumanEntity h && h.role().investigator()) {
                caughtRedHanded(writer, Config.tr("сотрудник расследования видел, как ты пишешь в тетради", "an investigator saw you write in the notebook"));
                return;
            }
            if (e.getPersistentData().contains("aicompanion_reported") && now < e.getPersistentData().getLong("aicompanion_reported") + Kira.DAY) {
                continue; // told the police what they saw already today
            }
            e.getPersistentData().putLong("aicompanion_reported", now);
            if (suspect(writer, Worship.covers(writer, e) ? 4 : 8, "witness " + Lifespans.trueName(e))) { // a follower of Kira says less
                return;
            }
            CompanionEntity ryuk = CompanionEntity.of(writer);
            if (ryuk != null) {
                ryuk.remark(Config.tr("человек по имени " + Lifespans.trueName(e) + " видел, как твой человек пишет в тетради. Люди болтают",
                        "a human called " + Lifespans.trueName(e) + " saw your human write in the notebook. Humans talk"), "witness", 600);
            }
        }
        if (concealment < 3) {
            for (BlockPos camera : Cameras.near(level, writer.blockPosition(), 12)) {
                if (Cameras.sees(level, camera, writer)) {
                    caughtRedHanded(writer, Config.tr("камера L засняла, как ты пишешь в тетради", "one of L's cameras filmed you writing in the notebook"));
                    return;
                }
            }
        }
    }

    private static void caughtRedHanded(ServerPlayer writer, String how) {
        writer.sendSystemMessage(Component.literal("» " + how).withStyle(ChatFormatting.DARK_RED));
        suspect(writer, 100, "red-handed");
    }

    /** A death by the writer's notebook: the world notices a pattern. */
    public static void onNoteDeath(ServerPlayer writer, LivingEntity victim, DeathNotes.Cause cause) {
        Kira.Case c = Kira.of(writer);
        c.kills++;
        float by = 1; // one more death in the pattern: a long game, so each counts for little alone
        if (victim instanceof AbstractIllager || victim instanceof Witch) {
            c.criminals++;
        } else if (victim instanceof AbstractVillager) {
            c.innocents++;
            by += 2; // nobody looks away from an innocent's death
        }
        if (cause == DeathNotes.Cause.HEART) {
            by += 1; // Kira's signature; an "accident" says less
        }
        if (victim.level() == writer.level() && victim.distanceTo(writer) < 64) {
            by += 2; // he died near the same human again
        }
        if (victim instanceof HumanEntity h && (h.role() == HumanEntity.Role.AGENT || h.role() == HumanEntity.Role.DETECTIVE
                || h.role() == HumanEntity.Role.SPK)) {
            by += 10; // Kira knows who investigates him: the manga's twelve FBI agents
        }
        Worship.onNoteDeath(writer, victim);
        if (c.flag("region")) {
            by *= 1.5F; // L is watching this region
        }
        counted(writer, c);
        suspect(writer, by, "note death " + Lifespans.trueName(victim));
    }

    /** A criminal off the news, dead far away: one more heart attack in the pattern, but nothing points at this human. */
    static void onNewsDeath(ServerPlayer writer, String name, DeathNotes.Cause cause) {
        Kira.Case c = Kira.of(writer);
        c.kills++;
        c.criminals++;
        Worship.add(c, 4);
        float by = cause == DeathNotes.Cause.HEART ? 2 : 1;
        if (c.flag("region")) {
            by *= 1.5F;
        }
        counted(writer, c);
        suspect(writer, by, "news death " + name);
    }

    /** After every death by the notebook: the clock of the case, and the name the world gives the killer. */
    private static void counted(ServerPlayer writer, Kira.Case c) {
        c.flags.put("last_kill", writer.server.overworld().getGameTime());
        if (c.stage < RUMOURS) {
            c.stage = RUMOURS;
        }
        if (c.kills >= 2 && !c.flag("kira_named")) {
            c.flags.put("kira_named", writer.server.overworld().getGameTime());
            tv(writer, "aicompanion.tv.kira_named");
        }
    }

    /** Days of this human's story: 0 on the day they first held the notebook (day time: sleeping moves it on). */
    static long day(ServerPlayer player) {
        long now = Kira.clock(player.server);
        return now / Kira.DAY - Kira.of(player).flags.getOrDefault("day0", now) / Kira.DAY;
    }

    /** Did another of the hunters see {@code killer} just now (in sight, within 16 blocks)? */
    private static boolean seen(ServerLevel level, ServerPlayer killer, HumanEntity victim) {
        return !level.getEntitiesOfClass(HumanEntity.class, killer.getBoundingBox().inflate(16),
                h -> h != victim && h.isAlive() && h.role().investigator() && h.hasLineOfSight(killer)).isEmpty();
    }

    /** A human struck one of the hunters: they don't forget it (once a minute at most counts). */
    static void assaulted(ServerPlayer player, HumanEntity victim) {
        long now = player.server.overworld().getGameTime();
        if (now < victim.getPersistentData().getLong("aicompanion_hit") + 1200) {
            return;
        }
        victim.getPersistentData().putLong("aicompanion_hit", now);
        String alias = victim.getCustomName() == null ? Lifespans.trueName(victim) : victim.getCustomName().getString();
        player.sendSystemMessage(Component.literal("<" + alias + "> ").withStyle(ChatFormatting.GRAY)
                .append(Component.translatable("aicompanion.hurt.investigator").withStyle(ChatFormatting.WHITE)));
        suspect(player, 4, "attacked " + alias);
    }

    /** Somebody of the story died: a decoy, an agent, Watari, L himself. */
    static void onHumanDeath(ServerLevel level, HumanEntity human, DamageSource source) {
        boolean byNote = source.typeHolder().unwrapKey().map(k -> k.location().getPath().startsWith("death_note")).orElse(false);
        ServerPlayer about = human.about() == null ? null : level.getServer().getPlayerList().getPlayer(human.about());
        ServerPlayer killer = source.getEntity() instanceof ServerPlayer p ? p : null;
        ServerPlayer player = killer != null ? killer : about;
        if (player == null) {
            return;
        }
        Kira.Case c = Kira.of(player);
        String alias = human.getCustomName() == null ? Lifespans.trueName(human) : human.getCustomName().getString();
        if (!byNote && killer != null && human.role().investigator() && seen(level, killer, human)) {
            caughtRedHanded(killer, Config.tr("ты убил " + alias + " на глазах у его людей", "you killed " + alias + " in front of his people"));
        } else if (!byNote && killer != null && (human.role() == HumanEntity.Role.L || human.role() == HumanEntity.Role.WATARI
                || human.role() == HumanEntity.Role.NEAR)) {
            suspect(killer, 30, "murdered " + alias); // nobody saw, but who else would want him dead
        }
        switch (human.role()) {
            case WATARI -> {
                c.flags.put("watari_dead", level.getGameTime());
                c.suspicion *= 0.5F; // his death wipes the case files
                story(player, Config.tr("Ватари мёртв. Его смерть стёрла данные расследования L", "Watari is dead. His death wiped L's case files"));
            }
            case L -> {
                c.flags.put("l_dead", level.getGameTime());
                c.flags.put("l_dead_day", Kira.clock(level.getServer())); // his successors come the next day
                c.suspicion *= 0.3F;
                c.stage = SUCCESSORS;
                story(player, Config.tr("L мёртв. Твой человек победил величайшего детектива. Но у L были преемники…",
                        "L is dead. Your human beat the greatest detective. But L had successors..."));
                CompanionEntity ryuk = CompanionEntity.of(player);
                if (ryuk != null) {
                    ryuk.reactLater("cackle"); // the show of the century
                }
                Network.card(player, Network.Broadcast.CHAPTER, Component.translatable("aicompanion.card.l_dead"), Component.translatable("aicompanion.card.l_dead_text"), "", null,
                        "sting.l_dead");
            }
            case AGENT, DETECTIVE, SPK -> {
                if (!byNote && killer != null) { // struck down by the suspect himself
                    suspect(player, 25, "an investigator killed");
                }
            }
            default -> {
            }
        }
        Successors.onDeath(player, human, byNote);
        Kira.get(level.getServer()).setDirty();
    }

    /** At 100%: the Task Force closes in, L names himself. The game is lost (Ryuk's rule 3), or the notebook is taken. */
    static void arrest(ServerPlayer player, String why) {
        Kira.Case c = Kira.of(player);
        c.flags.put("arrest", player.server.overworld().getGameTime());
        c.flags.put("arrested", player.server.overworld().getGameTime());
        ServerLevel level = player.serverLevel();
        for (int i = 0; i < 3; i++) { // Matsuda, Aizawa and the chief: each of them once
            double a = i * Math.PI * 2 / 3;
            Vec3 at = player.position().add(Math.sin(a) * 4, 0, Math.cos(a) * 4);
            BlockPos p = BlockPos.containing(at);
            if (!level.getBlockState(p).isAir() || !level.getBlockState(p.above()).isAir()) {
                at = player.position(); // no room there (a wall): right beside the suspect
            }
            spawn(level, HumanEntity.Role.DETECTIVE, i, at, player, null).leaveAfter(600);
        }
        player.sendSystemMessage(Component.translatable("aicompanion.l.arrest").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        Network.card(player, Network.Broadcast.CHAPTER, Component.translatable("aicompanion.card.arrest"), Component.translatable("aicompanion.l.arrest"), "", null,
                "sting.arrest");
        if (c.pact == Kira.Pact.ACCEPTED) {
            Pact.finish(player, Pact.Reason.CAUGHT);
            return;
        }
        int taken = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (DeathNoteItem.writable(player.getInventory().getItem(i))) {
                taken += player.getInventory().getItem(i).getCount();
                player.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
        c.suspicion = 60;
        c.flags.remove("arrest");
        if (taken > 0) {
            player.sendSystemMessage(Component.translatable("aicompanion.l.confiscated", taken).withStyle(ChatFormatting.GRAY));
        }
        boolean again = Onboarding.takenAway(player);
        story(player, Config.tr("L доказал, что твой человек — Кира. Твоей игры он не принимал, поэтому его только арестовали и забрали тетрадь"
                        + (again ? ". Завтра утром ты бросишь ему новую: спектакль слишком хорош, чтобы кончиться так" : ""),
                "L proved your human is Kira. They never took your game, so they were only arrested and lost the notebook"
                        + (again ? ". Tomorrow morning you'll drop them a new one: the show is too good to end like this" : "")));
        Kira.changed(player);
    }

    // ------------------------------------------------------------------ the stages

    /** Once a second per player online. */
    public static void tick(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        if (c.sealed || player.isSpectator() || c.kills == 0 || c.flag("kira_wins")) {
            return; // Kira won: nobody is looking any more
        }
        long now = player.server.overworld().getGameTime();
        long sinceKill = now - c.flags.getOrDefault("last_kill", now);
        boolean innocent = c.flag("renounced"); // gave up the notebook: no memory of it, nothing to give them away
        if (now % (innocent ? 600 : 1200) < 20 && (innocent || sinceKill > Kira.DAY) && c.suspicion > 0 && !c.flag("arrest")) {
            c.suspicion = Math.max(0, c.suspicion - 1); // a quiet day: the trail goes cold, slowly (an innocent's, fast)
            Kira.get(player.server).setDirty();
        }
        if (innocent || now < c.nextEvent || player.level().dimension() != Level.OVERWORLD) {
            return;
        }
        c.nextEvent = now + 200;
        if (c.flag("stage_pos")) {
            offAir(player); // a stage from before the special went on the screens only
        }
        long sinceBroadcast = Kira.clock(player.server) - c.flags.getOrDefault("broadcast_day", 0L); // a day at least between the acts
        if (c.stage == RUMOURS && day(player) >= 1 && (c.kills >= 3 || c.suspicion >= 10)) {
            broadcast(player);
        } else if (c.stage == BROADCAST && c.flag("broadcast_end") && now > c.flags.get("broadcast_end") && !DeathNotes.written(player.server, TAILOR)) {
            c.flags.remove("broadcast_end");
            if (!c.flag("tailor_killed")) {
                tv(player, "aicompanion.tv.no_bait");
            }
            advance(player, AGENTS);
        } else if (c.stage >= AGENTS && c.stage < SUCCESSORS && c.suspicion >= 15 && sinceBroadcast >= Kira.DAY) {
            agents(player, now);
        }
        if (c.stage == AGENTS && c.suspicion >= 40 && sinceBroadcast >= 2 * Kira.DAY) {
            Headquarters.raise(player);
            advance(player, HEADQUARTERS);
        }
        if (c.stage == HEADQUARTERS && c.suspicion >= 50 && isNight(player.serverLevel()) && now > c.flags.getOrDefault("searched", 0L) + 3 * Kira.DAY) {
            search(player, now);
        }
        if (c.stage >= RUMOURS && now % 6000 < 200 && ThreadLocalRandom.current().nextInt(3) == 0) {
            rumour(player);
        }
    }

    private static void advance(ServerPlayer player, int stage) {
        Kira.Case c = Kira.of(player);
        if (c.stage < stage) {
            c.stage = stage;
            Kira.get(player.server).setDirty();
            Pact.thrill(player, Pact.THRILL_BIG); // the show goes on: Ryuk loves this
        }
    }

    private static boolean isNight(ServerLevel level) {
        long t = level.getDayTime() % 24000;
        return t > 13000 && t < 23000;
    }

    /** A villager near the player whispers about Kira (a different line each time). */
    private static void rumour(ServerPlayer player) {
        AbstractVillager v = player.serverLevel().getEntitiesOfClass(AbstractVillager.class, player.getBoundingBox().inflate(16)).stream()
                .findFirst().orElse(null);
        if (v == null) {
            return;
        }
        player.sendSystemMessage(Component.literal("<" + Names.shown(Lifespans.trueName(v)) + "> ").withStyle(ChatFormatting.GRAY)
                .append(Component.translatable(Worship.rumour(player)).withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY)));
    }

    /** The Lind L. Tailor broadcast: a special on every channel, ten minutes on air; whoever has a set sees it at once. */
    static void broadcast(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        c.flags.put("broadcast_end", player.server.overworld().getGameTime() + BROADCAST_LENGTH);
        c.flags.put("broadcast_day", Kira.clock(player.server));
        advance(player, BROADCAST);
        boolean tv = News.viewer(player);
        if (tv) {
            show(player);
        }
        story(player, Config.tr("по всем каналам идёт спецвыпуск: человек, назвавшийся L, обращается к Кире. Подпись в кадре: Линд Л. Тейлор. "
                        + (tv ? "Ты чуешь подвох, но молчишь: интереснее посмотреть" : "Телевизора у твоего человека нет, и он ничего не видит: можешь намекнуть, что сейчас самое время его включить"),
                "a special airs on every channel: a man calling himself L addresses Kira. The caption says Lind L. Tailor. "
                        + (tv ? "You smell a trap but keep quiet: it's more fun to watch" : "Your human has no TV and sees none of it: you might hint that now is the time to switch one on")));
    }

    /** The special on this viewer's screen (client/TvShowScreen): his face and name, seen. */
    private static void show(ServerPlayer player) {
        Kira.of(player).flags.put("tailor_seen", player.server.overworld().getGameTime());
        Kira.get(player.server).setDirty();
        player.sendSystemMessage(Component.translatable("aicompanion.tv.special").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        Network.card(player, Network.Broadcast.SHOW, Component.translatable("aicompanion.card.special"), Component.empty(), Network.face("tailor_0"), null);
    }

    /** L's special is on air: the ten minutes aren't over and Tailor is alive. */
    public static boolean specialOnAir(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        return c.stage == BROADCAST && !c.flag("tailor_killed") && player.server.overworld().getGameTime() < c.flags.getOrDefault("broadcast_end", 0L);
    }

    /** A set switched on (News.watch): L's special, if it's on, from the start. */
    static void watching(ServerPlayer player) {
        if (specialOnAir(player)) {
            show(player);
        }
    }

    /** The name from L's special, written while he's on this human's screen: the notebook takes it (DeathNotes). */
    static boolean tailorWritten(ServerPlayer writer, String name) {
        return specialOnAir(writer) && Kira.of(writer).flag("tailor_seen") && Names.matches(name, "Lind L. Tailor");
    }

    /** Tailor dies on air, and the real L cuts in: Kira is in this region and kills with a face and a name. */
    static void tailorDied(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        if (c.flag("tailor_killed")) {
            return;
        }
        long now = player.server.overworld().getGameTime();
        c.flags.put("tailor_killed", now);
        c.flags.put("region", now);
        c.flags.remove("broadcast_end");
        c.kills++;
        c.criminals++; // a criminal due to be executed that day, as L tells
        counted(player, c);
        player.sendSystemMessage(News.tv(Component.translatable("aicompanion.tv.tailor_died")));
        Network.card(player, Network.Broadcast.DEATH, Component.translatable("aicompanion.card.breaking"), Component.translatable("aicompanion.tv.tailor_died"),
                Network.face("tailor_0"), null);
        Network.card(player, Network.Broadcast.SHOW, Component.translatable("aicompanion.card.special"), Component.empty(), "L", null); // the letter on a white screen
        advance(player, AGENTS);
        suspect(player, 20, "Lind L. Tailor");
        story(player, Config.tr("твой человек вписал Линда Л. Тейлора, и тот умер прямо в эфире. Тут же из экрана заговорил настоящий L: теперь он знает, что Кира в этом регионе и что ему нужны лицо и имя",
                "your human wrote Lind L. Tailor's name and he died live on air. At once the real L spoke from the screen: now he knows Kira is in this region and needs a face and a name"));
    }

    /** A stage from before the special went on the screens only: its chunk goes back to normal. */
    private static void offAir(ServerPlayer player) {
        Long at = Kira.of(player).flags.remove("stage_pos");
        if (at != null) {
            BlockPos stage = BlockPos.of(at);
            player.server.overworld().setChunkForced(stage.getX() >> 4, stage.getZ() >> 4, false);
        }
    }

    /** Words from the screen: whoever has a set hears them (TelevisionBlock). */
    static void tv(ServerPlayer player, String key) {
        if (!News.viewer(player)) {
            return;
        }
        player.sendSystemMessage(News.tv(Component.translatable(key)));
        Network.card(player, Network.Broadcast.NEWS, Component.translatable("aicompanion.card.tv"), Component.translatable(key), "", null,
                key.equals("aicompanion.tv.kira_named") ? "sting.kira" : "");
    }

    /** Up to two agents tail the suspect, each for a couple of days; a new one shows up out of sight. */
    static void agents(ServerPlayer player, long now) {
        Kira.Case c = Kira.of(player);
        if (now < c.flags.getOrDefault("next_agent", 0L)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long tailing = level.getEntitiesOfClass(HumanEntity.class, player.getBoundingBox().inflate(96),
                h -> h.role() == HumanEntity.Role.AGENT && player.getUUID().equals(h.about())).size();
        c.flags.put("next_agent", now + Kira.DAY);
        if (tailing >= 2) {
            return;
        }
        Vec3 at = outOfSight(level, player, 32);
        if (at == null) {
            return;
        }
        int n = c.flags.getOrDefault("agents", 0L).intValue();
        c.flags.put("agents", n + 1L);
        spawn(level, HumanEntity.Role.AGENT, n, at, player, null);
        if (n == 0) {
            story(player, Config.tr("за твоим человеком увязался незнакомец в плаще, держится метрах в двадцати. Ты знаешь, кто это, но не скажешь",
                    "a stranger in a long coat has started following your human, always about twenty blocks back. You know what he is, but you won't tell"));
        }
    }

    /** The Task Force searches the house by the bed (the spawn point) and leaves cameras behind. */
    static void search(ServerPlayer player, long now) {
        BlockPos bed = player.getRespawnPosition();
        if (bed == null || player.getRespawnDimension() != Level.OVERWORLD || bed.distSqr(player.blockPosition()) < 24 * 24) {
            return; // nobody searches a house with its owner right there
        }
        Kira.Case c = Kira.of(player);
        c.flags.put("searched", now);
        ServerLevel level = player.serverLevel();
        for (int i = 0; i < 2; i++) {
            spawn(level, HumanEntity.Role.DETECTIVE, i, Vec3.atBottomCenterOf(bed.offset(i * 2 - 1, 0, 2)), player, bed).searcher();
        }
        story(player, Config.tr("пока твой человек далеко, оперативная группа L обыскивает его дом у кровати и ставит камеры. Ты видишь это сквозь стены",
                "while your human is away, L's Task Force is searching their house by the bed and putting up cameras. You can see it through the walls"));
    }

    /**
     * Somebody walks up to one of the story's people and talks: they answer under the name they go by, a line of what
     * the story is at now (L alive, Watari dead, L dead, Near on the case), the next one each time.
     */
    static void talkTo(ServerPlayer player, HumanEntity human) {
        if (human.role() == HumanEntity.Role.L || human.role() == HumanEntity.Role.NEAR) {
            Headquarters.talkTo(player, human);
            return;
        }
        Kira.Case c = Kira.of(player);
        String alias = human.getCustomName() == null ? "?" : human.getCustomName().getString();
        String[] keys = switch (human.role()) {
            case TAILOR -> keys("tailor", 1);
            case AGENT -> keys("agent", 1);
            case DETECTIVE -> c.flag("successor") ? keys("detective.near", 3) : c.flag("l_dead") ? keys("detective.l_dead", 3)
                    : c.flag("watari_dead") ? keys("detective.watari_dead", 2) : c.flag("hq") ? keys("detective.hq", 3) : keys("detective", 3);
            case WATARI -> c.flag("l_dead") ? keys("watari.l_dead", 2) : keys("watari", 3);
            case SPK -> keys("spk", 3);
            case MELLO -> player.getMainHandItem().is(AiCompanion.CHOCOLATE.get()) ? keys("mello.chocolate", 1)
                    : c.flag("hostage") && !c.flag("hostage_done") ? keys("mello.hostage", 2) : keys("mello", 3);
            default -> keys("mafia", 3);
        };
        if (keys[0].endsWith("chocolate")) {
            player.getMainHandItem().shrink(1); // he did ask: a bar buys a tip
            human.level().playSound(null, human.blockPosition(), Sounds.CHOCOLATE.get(), human.getSoundSource(), 1.0F, 1.0F);
        }
        int said = human.getPersistentData().getInt("aicompanion_said");
        human.getPersistentData().putInt("aicompanion_said", said + 1);
        player.sendSystemMessage(Component.literal("<" + alias + "> ").withStyle(ChatFormatting.GRAY)
                .append(Component.translatable(keys[said % keys.length], alias).withStyle(ChatFormatting.WHITE)));
    }

    /** aicompanion.talk.<kind>1..n, the next one each time; one line: just aicompanion.talk.<kind>. */
    private static String[] keys(String kind, int n) {
        return n == 1 ? new String[]{"aicompanion.talk." + kind}
                : java.util.stream.IntStream.rangeClosed(1, n).mapToObj(i -> "aicompanion.talk." + kind + i).toArray(String[]::new);
    }

    // ------------------------------------------------------------------ helpers

    static HumanEntity spawn(ServerLevel level, HumanEntity.Role role, int variant, Vec3 at, @Nullable ServerPlayer about,
                             @Nullable BlockPos home) {
        HumanEntity h = AiCompanion.HUMAN.get().create(level);
        People.Person p = People.of(role, variant);
        long now = level.getServer().overworld().getGameTime();
        h.setup(role, variant, p.trueName(), p.alias(), now + (long) (p.lifeDays() * Kira.DAY), about == null ? null : about.getUUID(), home);
        h.moveTo(at.x, at.y, at.z, ThreadLocalRandom.current().nextFloat() * 360, 0);
        level.addFreshEntity(h);
        return h;
    }

    /** A free spot {@code dist} blocks from the player that they can't see right now. */
    @Nullable
    static Vec3 outOfSight(ServerLevel level, ServerPlayer player, double dist) {
        for (int i = 0; i < 16; i++) {
            double a = player.getYRot() * Math.PI / 180 + Math.PI + (ThreadLocalRandom.current().nextDouble() - 0.5) * 2.4; // behind them
            BlockPos p = Builds.ground(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(player.getX() - Math.sin(a) * dist, player.getY(), player.getZ() + Math.cos(a) * dist));
            if (Math.abs(p.getY() - player.getY()) < 12 && level.getBlockState(p.below()).isSolid()) {
                return Vec3.atBottomCenterOf(p);
            }
        }
        return null;
    }

    private static void story(ServerPlayer player, String event) {
        CompanionEntity ryuk = CompanionEntity.of(player);
        if (ryuk != null) {
            ryuk.remark(event, "story", 0);
        }
    }

    /** What Ryuk knows about the hunt for his human, for his context. */
    static String describe(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        if (c.kills == 0) {
            return "";
        }
        String level = c.suspicion < 10 ? Config.tr("почти никого", "barely anyone") : c.suspicion < 30 ? Config.tr("кое-кого", "someone")
                : c.suspicion < 60 ? Config.tr("всерьёз", "seriously") : c.suspicion < 85 ? Config.tr("очень всерьёз", "very seriously")
                : Config.tr("вот-вот докажет", "is about to prove it");
        StringBuilder sb = new StringBuilder(Config.tr("Расследование L: подозревает твоего человека " + level, "L's investigation: suspects your human " + level)
                + String.format(java.util.Locale.ROOT, " (%.0f%%). ", c.suspicion));
        if (c.flag("tailor_killed")) {
            sb.append(Config.tr("L знает регион Киры (Тейлор). ", "L knows Kira's region (Tailor). "));
        }
        if (c.stage >= AGENTS && c.stage < SUCCESSORS) {
            sb.append(Config.tr("За ним следят агенты ФБР под чужими именами. ", "FBI agents under false names are tailing them. "));
        }
        if (c.flag("hq")) {
            sb.append(Config.tr("Штаб L стоит в мире; в доме у кровати могут быть камеры (ты можешь найти их за яблоко). ",
                    "L's headquarters stands in the world; there may be cameras in the house by the bed (you can find them for an apple). "));
        }
        if (c.flag("l_dead")) {
            sb.append(Config.tr("L мёртв. ", "L is dead. "));
        }
        sb.append(Successors.describe(player));
        return sb.toString();
    }

    // ------------------------------------------------------------------ the house search

    /** A detective's errand: every container near the owner's bed gets opened; then the cameras go up and he leaves. */
    static final class Search extends Goal {
        private final HumanEntity detective;
        private final List<BlockPos> todo = new ArrayList<>();
        private int timer;
        private boolean planned, done;

        Search(HumanEntity detective) {
            this.detective = detective;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return detective.isSearcher() && detective.home() != null && !done && !(detective.level().isClientSide);
        }

        @Override
        public boolean canContinueToUse() {
            return !done;
        }

        @Override
        public void tick() {
            ServerLevel level = (ServerLevel) detective.level();
            if (!planned) {
                planned = true;
                BlockPos.betweenClosedStream(detective.home().offset(-8, -3, -8), detective.home().offset(8, 3, 8))
                        .filter(p -> level.getBlockEntity(p) instanceof Container && !Hideouts.isHidden(level, p))
                        .map(BlockPos::immutable).limit(24).forEach(todo::add);
            }
            if (todo.isEmpty()) {
                finish(level);
                return;
            }
            BlockPos next = todo.get(0);
            detective.getLookControl().setLookAt(Vec3.atCenterOf(next));
            if (detective.distanceToSqr(Vec3.atCenterOf(next)) > 6) {
                if (++timer % 20 == 1) {
                    detective.getNavigation().moveTo(next.getX() + 0.5, next.getY(), next.getZ() + 0.5, 0.7);
                }
                if (timer < 400) {
                    return; // can't reach it: give up on this one after 20 s
                }
            }
            timer = 0;
            todo.remove(0);
            BlockEntity be = level.getBlockEntity(next);
            level.blockEvent(next, level.getBlockState(next).getBlock(), 1, 1); // the lid opens
            level.playSound(null, next, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5F, 1.0F);
            if (be instanceof Container container && seize(container)) {
                ServerPlayer owner = level.getServer().getPlayerList().getPlayer(detective.about());
                if (owner != null) {
                    caughtRedHanded(owner, Config.tr("при обыске твоего дома нашли тетрадь смерти", "the house search turned up the Death Note"));
                }
                finish(level);
            }
        }

        /** Takes every notebook and page out of it as evidence: was there any? */
        private static boolean seize(Container c) {
            boolean found = false;
            for (int i = 0; i < c.getContainerSize(); i++) {
                if (DeathNoteItem.writable(c.getItem(i))) {
                    c.setItem(i, ItemStack.EMPTY);
                    found = true;
                }
            }
            return found;
        }

        /** Cameras on the walls around the bed, then off he goes. */
        private void finish(ServerLevel level) {
            done = true;
            if (detective.variant() == 0) {
                Cameras.install(level, detective.home(), 3);
            }
            detective.discard();
        }
    }

    /** True if something solid stands between the two points (for the cameras' view). */
    static boolean blocked(Level level, Vec3 from, Vec3 to, Entity viewer) {
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, viewer));
        return hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceToSqr(to) > 1;
    }
}
