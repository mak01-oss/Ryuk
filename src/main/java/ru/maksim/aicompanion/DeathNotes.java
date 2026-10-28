package ru.maksim.aicompanion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Names written in a Death Note and the deaths waiting for them, saved with the world. As in the manga: the true name
 * (not a name tag, not an alias) and the face in the writer's mind; no cause means a heart attack 40 seconds later; a
 * cause (fire, fall, lightning, drowning, a blast, frost, an illness; for a criminal far away anything at all) and a time ("in 10 minutes", up to 23 days) are honoured; humans younger than 780 days
 * can't be killed; four misspellings of one name make that person immune; what is written can't be undone.
 */
public final class DeathNotes extends SavedData {
    public static final int DELAY = 800; // 40 s
    static final UUID RYUK = new UUID(0x5279756BL, 0x4E6F7465L); // "Ryuk" / "Note": the writer of the final entries
    private static final long MAX_DELAY = 23 * Kira.DAY;
    private static final Pattern WHEN = Pattern.compile("(?iu)(?:через|in)\\s+(\\d{1,4})\\s*(сек|с\\b|мин|м\\b|час|ч\\b|дн|д\\b|sec|s\\b|min|m\\b|hour|h\\b|day|d\\b)");
    private static final ResourceKey<DamageType> BY_RYUK = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(AiCompanion.MODID, "ryuk"));
    private static final Map<UUID, Long> LAST_WRITE = new HashMap<>();
    private final List<Entry> entries = new ArrayList<>();

    public enum Cause {
        HEART("сердечный приступ", "a heart attack"), FIRE("сгорел заживо", "burned alive"),
        FALL("разбился, упав с высоты", "fell to death"), LIGHTNING("убит молнией", "struck by lightning"),
        DROWN("захлебнулся посреди суши", "drowned on dry land"), BLAST("погиб при взрыве", "blown up"),
        FREEZE("замёрз насмерть", "froze to death"), SICKNESS("умер от внезапной болезни", "taken by a sudden illness");

        private final String ru, en;
        final ResourceKey<DamageType> damage;

        Cause(String ru, String en) {
            this.ru = ru;
            this.en = en;
            damage = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(AiCompanion.MODID, "death_note_" + name().toLowerCase(Locale.ROOT)));
        }

        /** The cause is free text, like in the manga; anything this world can't do ends as a heart attack. */
        static Cause of(String text) {
            String t = text.toLowerCase(Locale.ROOT);
            if (t.matches(".*(молни|гром|lightning|thunder).*")) {
                return LIGHTNING;
            }
            if (t.matches(".*(взрыв|взорв|бомб|динамит|крипер|explo|bomb|blast|tnt|creeper|dynamite).*")) {
                return BLAST;
            }
            if (t.matches(".*(огон|огн|пожар|сгор|жар|fire|flame|burn).*")) {
                return FIRE;
            }
            if (t.matches(".*(тону|утон|утоп|захлеб|захлёб|drown).*")) {
                return DROWN;
            }
            if (t.matches(".*(замерз|замёрз|мороз|обморож|холод|freez|frost|frozen).*")) {
                return FREEZE;
            }
            if (t.matches("(?U).*(болезн|болел|заболе|инфекц|вирус|\\bрак\\b|\\bяд\\b|отрав|токсин|чум|sick|\\bill|disease|virus|cancer|poison|plague|toxin).*")) {
                return SICKNESS;
            }
            return t.matches(".*(пад|упал|высот|разби|fall|fell|height).*") ? FALL : HEART;
        }

        String text() {
            return Config.tr(ru, en);
        }
    }

    /**
     * {@code fin}: Ryuk's own entry (or the owner's own name) under his game: when it comes due, it's over for good.
     * {@code how}: the cause as written, word for word (the news reads it out; Ryuk hears it).
     */
    private record Entry(UUID target, String name, Cause cause, long due, UUID writer, boolean fin, String how) {
    }

    /** What a written name turned out to mean. */
    private record Found(@Nullable LivingEntity target, String problem) {
    }

    public static DeathNotes get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(DeathNotes::load, DeathNotes::new, AiCompanion.MODID + "_death_notes");
    }

    /** Server thread: {@code writer} wrote a line in the notebook in {@code hand} (concealment: see Investigation.witnessed). */
    public static void write(ServerPlayer writer, ItemStack note, String rawName, String rawCause, int concealment) {
        String name = rawName.strip();
        String causeText = rawCause.strip();
        long now = writer.server.overworld().getGameTime();
        if (!DeathNoteItem.writable(note) || name.isEmpty() || name.length() > 40 || causeText.length() > 60
                || Math.abs(now - LAST_WRITE.getOrDefault(writer.getUUID(), -100L)) < 20 || Kira.of(writer).sealed) {
            return;
        }
        LAST_WRITE.put(writer.getUUID(), now);
        if (note.getItem() instanceof ScrapItem scrap && scrap.kind == ScrapItem.Kind.WATCH) {
            writer.hurt(writer.damageSources().magic(), 4); // the needle: the name goes in blood
        }
        DeathNoteItem.addLine(note, name + (causeText.isEmpty() ? "" : " — " + causeText));
        writer.serverLevel().playSound(null, writer.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.6F, 0.7F);
        Investigation.witnessed(writer, concealment);
        if (DeathNoteItem.fake(note)) { // the copy: nothing happens, and only Ryuk knows why
            Successors.onFakeWrite(writer);
            CompanionEntity ryuk = CompanionEntity.of(writer);
            if (ryuk != null) {
                ryuk.remark(writer.getGameProfile().getName() + Config.tr(" вписал в тетрадь «" + name + "». Ты знаешь, что эта тетрадь — копия SPK и никто не умрёт, но не говоришь",
                        " wrote \"" + name + "\" in the notebook. You know this notebook is the SPK's copy and nobody will die, but you don't say"), "note", 0);
            }
            return;
        }

        Cause cause = Cause.of(causeText);
        String how = WHEN.matcher(causeText).replaceAll("").strip(); // the cause without its "in 10 minutes"
        long delay = delay(causeText);
        String who = writer.getGameProfile().getName();
        String when = delay == DELAY ? Config.tr("через 40 секунд", "in 40 seconds") : Config.tr("через ", "in ") + duration(delay);
        News.Criminal wanted = Kira.of(writer).flag("news") ? News.wanted(writer.server, name) : null; // their faces are on the screens only
        boolean tailor = wanted == null && Investigation.tailorWritten(writer, name);
        if (wanted != null || tailor) { // a face from the screens: far away, and it makes no difference
            DeathNotes data = get(writer.server);
            data.entries.add(tailor ? new Entry(Investigation.TAILOR, "Lind L. Tailor", cause, now + delay, writer.getUUID(), false, how)
                    : new Entry(wanted.id(), wanted.name(), cause, now + delay, writer.getUUID(), false, how));
            data.setDirty();
            writer.displayClientMessage(Component.literal("«" + name + "» — " + when).withStyle(ChatFormatting.DARK_RED), true);
            CompanionEntity ryuk = CompanionEntity.of(writer);
            if (ryuk != null) {
                ryuk.remark(tailor ? who + Config.tr(" вписал в тетрадь имя «" + name + "» — человека, который сейчас в прямом эфире называет себя L. Он умрёт " + when + ", прямо перед камерами",
                        " wrote the name \"" + name + "\" in the notebook: the man calling himself L live on air right now. He dies " + when + ", in front of the cameras")
                        : who + Config.tr(" вписал в тетрадь имя «" + name + "» — преступника, которого показывали в новостях. Он умрёт " + when + ", где бы ни был",
                        " wrote the name \"" + name + "\" in the notebook: the criminal from the news. He dies " + when + ", wherever he is"), "note", 0);
                ryuk.react("peek"); // over the shoulder, at the page
            }
            return;
        }
        Found found = find(writer, name);
        if (found.target() != null && written(writer.server, found.target().getUUID())) { // once is enough: no pile of entries
            found = new Found(null, Config.tr("это имя уже вписано, смерть и так придёт", "that name is already written, the death is coming anyway"));
        }
        LivingEntity target = found.target();
        String event;
        if (target == null) {
            event = who + Config.tr(" вписал в тетрадь «" + name + "», но ничего не случится: " + found.problem(),
                    " wrote \"" + name + "\" in the notebook, but nothing will happen: " + found.problem());
        } else {
            DeathNotes data = get(writer.server);
            boolean own = target == writer;
            boolean fin = own && Kira.of(writer).pact == Kira.Pact.ACCEPTED;
            data.entries.add(new Entry(target.getUUID(), Lifespans.trueName(target), cause, now + delay, writer.getUUID(), fin, how));
            data.setDirty();
            if (target instanceof ServerPlayer && !own) {
                AiCompanion.LOGGER.info("{} wrote the player {} in the Death Note", who, name); // for server admins
            }
            if (own) {
                Kira.of(writer).flags.put("finishing", now + delay); // dying any other way now is final too
                Kira.of(writer).sealedWhy = Pact.Reason.OWN_NAME.name();
                Kira.changed(writer);
            }
            event = own
                    ? who + Config.tr(" вписал в тетрадь СВОЁ СОБСТВЕННОЕ имя. Он умрёт " + when + (fin ? ", и по твоей игре это конец" : ""),
                    " wrote their OWN name in the notebook. They die " + when + (fin ? ", and by your game that's the end" : ""))
                    : Config.english()
                    ? who + " wrote the name \"" + name + "\" in the notebook" + (cause == Cause.HEART ? "" : " with the cause \"" + causeText + "\"")
                    + ". They die " + when + "."
                    : who + " вписал в тетрадь имя «" + name + "»" + (cause == Cause.HEART ? "" : " и причину «" + causeText + "»")
                    + ". Он умрёт " + when + ".";
        }
        // what came of it, at once and whatever Ryuk's AI does: the gist above the hotbar, the reason in the chat
        writer.displayClientMessage(Component.literal("«" + name + "» — " + (target == null ? Config.tr("ничего не случится", "nothing will happen") : when))
                .withStyle(target == null ? ChatFormatting.GRAY : ChatFormatting.DARK_RED), true);
        if (target == null) {
            writer.sendSystemMessage(Component.literal(Config.tr("Тетрадь: «", "Notebook: \"") + name + Config.tr("» — ", "\" — ") + found.problem() + ".")
                    .withStyle(ChatFormatting.GRAY));
        }
        CompanionEntity ryuk = CompanionEntity.of(writer);
        if (ryuk != null) {
            ryuk.remark(event, "note", 0);
            ryuk.react(target == null ? "shrug" : "peek"); // a name that won't die: "no idea, find out yourself"
        }
    }

    /** Ryuk writes the name in his own notebook (Pact.finish): due in 40 seconds, final under his game. */
    static void writeByRyuk(ServerPlayer player, long due) {
        DeathNotes data = get(player.server);
        data.entries.add(new Entry(player.getUUID(), player.getGameProfile().getName(), Cause.HEART, due, RYUK,
                Kira.of(player).pact == Kira.Pact.ACCEPTED, ""));
        data.setDirty();
    }

    /** One of the story's people wrote this player's name in their own notebook (the mafia boss with the eyes). */
    static void writeByNpc(ServerPlayer target, UUID writer) {
        DeathNotes data = get(target.server);
        data.entries.add(new Entry(target.getUUID(), target.getGameProfile().getName(), Cause.HEART,
                target.server.overworld().getGameTime() + DELAY, writer, false, ""));
        data.setDirty();
    }

    /** The story test (StoryTest): {@code target}'s name in {@code writer}'s notebook, as if by hand; a heart attack in {@code ticks}. */
    static void writeFor(ServerPlayer writer, LivingEntity target, long ticks) {
        DeathNotes data = get(writer.server);
        data.entries.add(new Entry(target.getUUID(), Lifespans.trueName(target), Cause.HEART, writer.server.overworld().getGameTime() + ticks,
                writer.getUUID(), false, ""));
        data.setDirty();
    }

    /** The story test: what this writer wrote comes due in {@code ticks} at the latest. How many names were hurried. */
    static int hurry(MinecraftServer server, UUID writer, long ticks) {
        DeathNotes data = get(server);
        long at = server.overworld().getGameTime() + ticks;
        int n = 0;
        for (java.util.ListIterator<Entry> it = data.entries.listIterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (e.writer().equals(writer) && e.due() > at) {
                it.set(new Entry(e.target(), e.name(), e.cause(), at, e.writer(), e.fin(), e.how()));
                n++;
            }
        }
        if (n > 0) {
            data.setDirty();
        }
        return n;
    }

    /** Will a name this writer wrote come due within {@code ticks}? */
    static boolean dueSoon(MinecraftServer server, UUID writer, long ticks) {
        long now = server.overworld().getGameTime();
        return get(server).entries.stream().anyMatch(e -> e.writer().equals(writer) && e.due() - now < ticks);
    }

    /** Is somebody already writing this player's name (or done)? */
    static boolean listed(MinecraftServer server, UUID target) {
        return get(server).entries.stream().anyMatch(e -> e.target().equals(target));
    }

    /** "In 10 minutes": the manga allows a time of death up to 23 days ahead, never sooner than the 40 seconds. */
    static long delay(String cause) {
        Matcher m = WHEN.matcher(cause);
        if (!m.find()) {
            return DELAY;
        }
        long n = Long.parseLong(m.group(1));
        String unit = m.group(2).toLowerCase(Locale.ROOT);
        long ticks = unit.startsWith("с") || unit.startsWith("s") ? n * 20 : unit.startsWith("м") || unit.startsWith("min") || unit.equals("m")
                ? n * 1200 : unit.startsWith("ч") || unit.startsWith("h") ? n * 72000 : n * Kira.DAY;
        return Math.max(DELAY, Math.min(MAX_DELAY, ticks));
    }

    private static String duration(long ticks) {
        long s = ticks / 20;
        if (s < 120) {
            return s + Config.tr(" сек", " seconds");
        }
        return s < 7200 ? s / 60 + Config.tr(" мин", " minutes") : s / 3600 + Config.tr(" ч", " hours");
    }

    static boolean written(MinecraftServer server, UUID target) {
        return get(server).entries.stream().anyMatch(e -> e.target().equals(target));
    }

    /** Other players and their pets where the server keeps players from hurting each other (pvp=false), and anyone in creative. */
    private static boolean shielded(ServerPlayer writer, LivingEntity target) {
        boolean pvp = writer.server.isPvpAllowed();
        if (target instanceof ServerPlayer p) {
            return p != writer && (!pvp || p.isCreative() || p.isSpectator());
        }
        return !pvp && target instanceof OwnableEntity pet && pet.getOwnerUUID() != null && !pet.getOwnerUUID().equals(writer.getUUID());
    }

    private static Found shield() {
        return new Found(null, Config.tr("на этом сервере тетрадь не берёт других игроков и их питомцев (PvP выключено или игрок в творческом режиме)",
                "on this server the notebook can't take other players or their pets (PvP is off, or the player is in creative)"));
    }

    /**
     * Who the written name belongs to. Players by nickname, humans by their true name, creatures by the name a human
     * gave them; in each case the writer must have seen the face. A near miss counts toward the four misspellings.
     */
    private static Found find(ServerPlayer writer, String name) {
        MinecraftServer server = writer.server;
        if (Names.matches(name, writer.getGameProfile().getName())) {
            return new Found(writer, "");
        }
        ServerPlayer player = server.getPlayerList().getPlayerByName(name);
        if (player != null && shielded(writer, player)) {
            return shield();
        }
        if (player != null) {
            return Lifespans.seen(writer, player) ? new Found(player, "")
                    : new Found(null, Config.tr("лица этого человека не видели", "that person's face hasn't been seen"));
        }
        LivingEntity nearMiss = null;
        boolean knownName = false;
        for (UUID id : Lifespans.faces(writer)) {
            LivingEntity e = Lifespans.byUuid(server, id);
            if (e == null || !e.isAlive()) {
                continue;
            }
            boolean human = Lifespans.human(e);
            boolean named = human ? Names.matches(name, Lifespans.trueName(e))
                    : e.getCustomName() != null && Names.matches(name, e.getCustomName().getString());
            if (named) {
                if (e instanceof CompanionEntity) {
                    return new Found(null, Config.tr("синигами тетрадью не убить", "a shinigami can't be killed with the notebook"));
                }
                if (shielded(writer, e)) {
                    return shield();
                }
                if (e.getPersistentData().getBoolean(Lifespans.IMMUNE)) {
                    return new Found(null, Config.tr("имя этого человека уже четырежды писали с ошибкой, тетрадь его не берёт",
                            "this person's name was misspelled four times, the notebook can't take them any more"));
                }
                if (human && e.isBaby()) {
                    return new Found(null, Config.tr("тетрадь не действует на людей младше 780 дней", "the notebook doesn't work on humans under 780 days old"));
                }
                if (human && !(e instanceof Player) && Lifespans.dies(e) - server.overworld().getGameTime() < 12 * 1200) {
                    return new Found(null, Config.tr("этому человеку и так осталось меньше 12 минут: на таких тетрадь не действует",
                            "that person has less than 12 minutes to live anyway: the notebook doesn't work on them"));
                }
                return new Found(e, "");
            }
            if (human && nearMiss == null && name.length() >= 4 && Names.distance(name, Lifespans.trueName(e)) <= 2) {
                nearMiss = e;
            }
            if (human && e instanceof NamedHuman h && !(Names.matches(name, h.trueName())) && name.equalsIgnoreCase(e.getName().getString())) {
                knownName = true; // an alias: the name they go by isn't their real one
            }
        }
        if (nearMiss != null) {
            int misses = nearMiss.getPersistentData().getInt("aicompanion_misses") + 1;
            nearMiss.getPersistentData().putInt("aicompanion_misses", misses);
            if (misses >= 4) {
                nearMiss.getPersistentData().putBoolean(Lifespans.IMMUNE, true);
            }
            return new Found(null, Config.tr("в имени ошибка (" + misses + "-я для этого человека; после 4-й тетрадь перестанет на него действовать)",
                    "the name is misspelled (miss number " + misses + " for that person; after the 4th the notebook stops working on them)"));
        }
        if (News.nearMiss(server, name)) {
            return new Found(null, Config.tr("в имени ошибка: в новостях оно написано иначе", "the name is misspelled: the news spelled it differently"));
        }
        return new Found(null, knownName ? Config.tr("это не настоящее имя, а прозвище", "that's an alias, not the real name")
                : Config.tr("человека с таким именем в лицо не видели", "nobody with that name has been seen face to face"));
    }

    /** Every server tick: the heartbeat, the flames, the fall, then death. */
    public static void tick(MinecraftServer server) {
        DeathNotes data = get(server);
        if (data.entries.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        List<Runnable> deaths = new ArrayList<>();
        for (Iterator<Entry> it = data.entries.iterator(); it.hasNext(); ) {
            Entry e = it.next();
            long left = e.due() - now;
            if (left > 80) {
                continue;
            }
            if (e.target().equals(Investigation.TAILOR)) { // the man from L's special: he dies on air, his writer sees L's answer
                ServerPlayer writer = server.getPlayerList().getPlayer(e.writer());
                if (left <= 0 && writer != null) {
                    it.remove();
                    data.setDirty();
                    deaths.add(() -> Investigation.tailorDied(writer));
                }
                continue;
            }
            if (News.is(e.target())) { // a man from the news, far away: only the screens will show it
                if (left <= 0) {
                    it.remove();
                    data.setDirty();
                    deaths.add(() -> News.died(server, e.target(), e.name(), e.cause(), e.how(), e.writer()));
                }
                continue;
            }
            LivingEntity target = Lifespans.byUuid(server, e.target());
            if (target == null || !target.isAlive()) {
                if (left < -24000 && !e.fin()) { // never showed up for a whole day (a final name waits forever)
                    it.remove();
                    data.setDirty();
                }
                continue; // offline or in an unloaded chunk: dies the moment it shows up
            }
            if (left > 0) {
                foreshadow(target, e.cause(), (int) left);
            } else {
                it.remove();
                data.setDirty();
                deaths.add(() -> kill(server, target, e));
            }
        }
        deaths.forEach(Runnable::run); // after the loop: a death can bring new names (an arrest under Ryuk's game)
    }

    /** It died some other way first: the line is spent. */
    public static void forget(MinecraftServer server, UUID dead) {
        DeathNotes data = get(server);
        if (data.entries.removeIf(e -> e.target().equals(dead))) {
            data.setDirty();
        }
    }

    private static void foreshadow(LivingEntity target, Cause cause, int left) {
        ServerLevel level = (ServerLevel) target.level();
        switch (cause) {
            case HEART -> {
                if (left == 80 || left == 58 || left == 42 || left == 30 || left == 21 || left == 14 || left == 8 || left == 3) {
                    level.playSound(null, target.getX(), target.getY(), target.getZ(), Sounds.HEART.get(), SoundSource.HOSTILE, 1.4F, 0.95F + (80 - left) * 0.002F); // quickening
                }
                if (left == 60 && target instanceof Player) {
                    target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2, false, false));
                }
            }
            case FIRE -> {
                if (left == 60) {
                    target.setSecondsOnFire(5);
                }
            }
            case FALL -> {
                if (left == 45) {
                    target.setDeltaMovement(0, 2.2, 0); // up it goes
                    target.hurtMarked = true;
                }
            }
            case LIGHTNING -> {
                if (left == 30) {
                    level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 2.0F, 1.3F);
                }
            }
            case DROWN -> { // water out of nowhere: it runs off the head, the air runs out
                if (left % 6 == 0) {
                    level.sendParticles(ParticleTypes.FALLING_WATER, target.getX(), target.getEyeY(), target.getZ(), 8, 0.25, 0.1, 0.25, 0);
                    level.sendParticles(ParticleTypes.BUBBLE_POP, target.getX(), target.getEyeY(), target.getZ(), 4, 0.2, 0.1, 0.2, 0.02);
                }
                if (target instanceof Player) {
                    target.setAirSupply(target.getMaxAirSupply() * left / 80);
                }
                if (left == 60 || left == 30 || left == 10) {
                    level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_HURT_DROWN, SoundSource.HOSTILE, 1.0F, 1.0F);
                }
            }
            case BLAST -> { // a fuse hisses somewhere close
                if (left == 40) {
                    level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 1.0F, 1.0F);
                }
                if (left <= 40 && left % 4 == 0) {
                    level.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY() + 0.2, target.getZ(), 3, 0.3, 0.1, 0.3, 0.01);
                }
            }
            case FREEZE -> { // the frost creeps in
                target.setTicksFrozen(target.getTicksRequiredToFreeze() * (80 - left) / 80 + 2);
                if (left % 6 == 0) {
                    level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + 1, target.getZ(), 6, 0.4, 0.6, 0.4, 0.01);
                }
            }
            case SICKNESS -> { // a cough, a sway
                if (left == 60 && target instanceof Player) {
                    target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false));
                }
                if (left % 10 == 0) {
                    level.sendParticles(ParticleTypes.SNEEZE, target.getX(), target.getEyeY(), target.getZ(), 4, 0.2, 0.1, 0.2, 0.02);
                }
                if (left == 50 || left == 25) {
                    level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PANDA_SNEEZE, SoundSource.HOSTILE, 1.0F, 0.6F);
                }
            }
        }
    }

    private static void kill(MinecraftServer server, LivingEntity target, Entry e) {
        ServerLevel level = (ServerLevel) target.level();
        if (e.cause() == Cause.LIGHTNING) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(target.position());
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }
        switch (e.cause()) { // what the moment itself looks like
            case BLAST -> {
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, target.getX(), target.getY() + 1, target.getZ(), 1, 0, 0, 0, 0);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 2.0F, 1.0F);
            }
            case DROWN -> level.sendParticles(ParticleTypes.SPLASH, target.getX(), target.getY() + 1, target.getZ(), 40, 0.4, 0.5, 0.4, 0.1);
            case FREEZE -> {
                level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + 1, target.getZ(), 40, 0.5, 0.7, 0.5, 0.05);
                level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1.0F, 0.7F);
            }
            case SICKNESS -> level.sendParticles(ParticleTypes.SNEEZE, target.getX(), target.getEyeY(), target.getZ(), 20, 0.3, 0.2, 0.3, 0.05);
            default -> {
            }
        }
        if (target instanceof ServerPlayer player && (e.fin() || e.writer().equals(RYUK) || e.writer().equals(player.getUUID()))) {
            Pact.seal(player); // before the death screen opens: the client must already know it's final
        }
        ResourceKey<DamageType> type = e.writer().equals(RYUK) ? BY_RYUK : e.cause().damage;
        target.hurt(new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type)), Float.MAX_VALUE);
        ServerPlayer writer = server.getPlayerList().getPlayer(e.writer());
        if (writer != null && writer != target) {
            Investigation.onNoteDeath(writer, target, e.cause());
            Pact.thrill(writer, Pact.THRILL_BIG);
            if (writer.level() == target.level() && writer.distanceTo(target) < 48) {
                Network.cue(writer, "sting.verdict"); // they saw it come true
            }
        }
        CompanionEntity ryuk = writer == null ? null : CompanionEntity.of(writer);
        if (ryuk != null && writer != target) {
            String written = e.how().isBlank() ? "" : Config.tr(" (в тетради было: «" + e.how() + "»" + (e.cause() == Cause.HEART
                            ? "; здесь так не умирают, и тетрадь обошлась сердцем)" : ")"),
                    " (the notebook said: \"" + e.how() + "\"" + (e.cause() == Cause.HEART ? "; no one dies like that here, so it took the heart)" : ")"));
            ryuk.remark(Config.tr("«" + e.name() + "» мёртв: " + e.cause().text() + written + ", точно в назначенный тетрадью миг.",
                    "\"" + e.name() + "\" is dead: " + e.cause().text() + written + ", exactly when the notebook said."), "note_death", 0);
        }
    }

    /** Pending deaths, for the context of the story's people: who is on the list and when. */
    public static List<String> pending(MinecraftServer server, UUID writer) {
        List<String> out = new ArrayList<>();
        long now = server.overworld().getGameTime();
        for (Entry e : get(server).entries) {
            if (e.writer().equals(writer) && out.size() < 12) { // a longer list would only eat the prompts it goes into
                out.add(Names.shown(e.name()) + " (" + Math.max(0, (e.due() - now) / 20) + Config.tr(" с)", " s)"));
            }
        }
        return out;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Entry e : entries) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Target", e.target());
            t.putString("Name", e.name());
            t.putString("Cause", e.cause().name());
            t.putLong("Due", e.due());
            t.putUUID("Writer", e.writer());
            t.putBoolean("Final", e.fin());
            t.putString("How", e.how());
            list.add(t);
        }
        tag.put("Entries", list);
        return tag;
    }

    static DeathNotes load(CompoundTag tag) {
        DeathNotes data = new DeathNotes();
        for (Tag t : tag.getList("Entries", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            data.entries.add(new Entry(c.getUUID("Target"), c.getString("Name"), Cause.valueOf(c.getString("Cause")),
                    c.getLong("Due"), c.getUUID("Writer"), c.getBoolean("Final"), c.getString("How")));
        }
        return data;
    }
}
