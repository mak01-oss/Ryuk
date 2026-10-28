package ru.maksim.aicompanion;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Sakura TV's news: criminals wanted in the region, name and face on air. That's how Light found his first names, and
 * how a new owner learns what the notebook does: write a name seen on the news, and 40 seconds later the news says that
 * man died of a heart attack. The news needs a television (TelevisionBlock, a small set one carries about): a minute
 * after the notebook is picked up Ryuk says so (and the recipe turns up in the book); the first edition comes the moment
 * one has a set (in the inventory, or one switched on somewhere), then one every morning (three wanted at most, each
 * for three days, then the police catch him anyway), only to viewers. None of them has a body in this world: they're
 * far away, on the run, so a death here gives nothing away but the pattern.
 */
public final class News extends SavedData {
    private static final long MARK = 0x4E657773L; // "News": the high bits of every wanted man's id
    private static final long WANTED_FOR = 3 * Kira.DAY, FIRST = 1200;
    private static final int MAX = 3, CRIMES = 8, PLACES = 8, TOLD = 3; // ... places a death happens, stories per cause
    private static final java.util.regex.Pattern HEART = java.util.regex.Pattern.compile("(?iu).*(сердц|сердеч|инфаркт|приступ|heart).*");
    private final List<Criminal> wanted = new ArrayList<>();
    private long edition = -1; // the day of the last morning edition

    /** {@code until}: day time when he's caught anyway and off the news. */
    public record Criminal(UUID id, String name, int crime, long until) {
    }

    static News get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(News::load, News::new, AiCompanion.MODID + "_news");
    }

    /** Once a second: the morning edition (once anyone watches), each owner's first one when they first switch a set on. */
    static void tick(MinecraftServer server) {
        News news = get(server);
        long day = Kira.clock(server), now = server.overworld().getGameTime();
        if (news.wanted.removeIf(c -> day > c.until())) {
            news.setDirty();
        }
        List<ServerPlayer> first = new ArrayList<>();
        boolean any = false;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Kira.Case c = Kira.of(p);
            if (c.sealed || !c.flag("held_at")) {
                continue;
            }
            if (!viewer(p)) { // no set yet: a minute in, Ryuk says where the names are
                if (!c.flag("tv_hint") && now >= c.flags.get("held_at") + FIRST) {
                    c.flags.put("tv_hint", now);
                    Kira.get(server).setDirty();
                    hint(p);
                }
                continue;
            }
            any |= c.flag("news"); // the mornings start after one's own first edition, not over Ryuk's hello
            if (!c.flag("news")) {
                c.flags.put("news", now);
                Kira.get(server).setDirty();
                first.add(p);
            }
        }
        long today = day / Kira.DAY, time = day % Kira.DAY;
        boolean morning = time >= 1000 && time < 12000 && today != news.edition;
        Criminal aired = null;
        if ((any && morning || !first.isEmpty()) && news.wanted.size() < MAX) {
            news.edition = today;
            aired = news.air(server, day);
        }
        for (ServerPlayer p : first) {
            if (aired == null) { // the screens are full of faces already: these are the ones they catch first
                news.wanted.forEach(c -> p.sendSystemMessage(line(c)));
            }
            Criminal shown = aired != null ? aired : news.wanted.isEmpty() ? null : news.wanted.get(0);
            if (aired == null && shown != null) { // the bulletin the others saw this morning
                Network.card(p, Network.Broadcast.NEWS, Component.translatable("aicompanion.card.wanted"), bulletin(shown), face(shown.id()), null);
            }
            CompanionEntity ryuk = CompanionEntity.of(p);
            if (shown != null && ryuk != null) {
                ryuk.remark(Config.tr("по Сакура ТВ показывают разыскиваемого преступника: " + Names.shown(shown.name()) + ", имя и лицо на весь экран. Тебе любопытно, догадается ли твой человек, что с этим делать",
                        "Sakura TV is showing a wanted criminal: " + Names.shown(shown.name()) + ", name and face full screen. You're curious whether your human will work out what to do with that"), "story", 0);
            }
        }
    }

    Criminal air(MinecraftServer server, long day) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        UUID id = new UUID(MARK, r.nextLong());
        Criminal c = new Criminal(id, Names.of(id, false), r.nextInt(CRIMES), day + WANTED_FOR);
        wanted.add(c);
        setDirty();
        toViewers(server, line(c), Network.Broadcast.NEWS, Component.translatable("aicompanion.card.wanted"), bulletin(c), face(c.id()));
        return c;
    }

    /** Watches the news: carries a set (it's a small one, it goes along in a pocket) or has switched one on somewhere. */
    static boolean viewer(ServerPlayer p) {
        return Kira.of(p).flag("tv") || p.getInventory().hasAnyOf(java.util.Set.of(AiCompanion.TELEVISION_ITEM.get()));
    }

    /** What's on the screens reaches the viewers (and only them). */
    private static void toViewers(MinecraftServer server, Component line, int kind, Component title, Component text, String face) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (viewer(p)) {
                p.sendSystemMessage(line);
                Network.card(p, kind, title, text, face, null);
            }
        }
    }

    /** No set yet: Ryuk tells where the names are, the card says how to make one, the recipe book has it. */
    static void hint(ServerPlayer player) {
        Network.card(player, Network.Broadcast.CHAPTER, Component.translatable("aicompanion.card.tv_hint"), Component.translatable("aicompanion.card.tv_hint_text"), "", null);
        player.server.getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation(AiCompanion.MODID, "television"))
                .ifPresent(recipe -> player.awardRecipes(List.of(recipe)));
        CompanionEntity ryuk = CompanionEntity.of(player);
        if (ryuk != null) {
            ryuk.remark(Config.tr("твой человек держит тетрадь, но не знает, чьи имена писать. Люди показывают своих преступников по телевизору: подскажи ему сделать телевизор — пять слитков железа и чёрный краситель: он маленький, его носят с собой, и новости приходят сами",
                    "your human holds the notebook but has no names to write. Humans show their criminals on TV: tell them to make a television, five iron ingots and a black dye: it's a small one, they carry it and the news comes by itself"), "story", 0);
        }
    }

    /**
     * A set switched on (placed, or the one in hand): from now on the news reaches this viewer. What's on air now,
     * they see at once (the first edition, if this is their first set, comes with the next tick); L's special, if on.
     */
    static void watch(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        if (!c.flag("tv")) {
            c.flags.put("tv", player.server.overworld().getGameTime());
            Kira.get(player.server).setDirty();
        }
        Investigation.watching(player);
        if (!c.flag("news") && c.flag("held_at") && !c.sealed) {
            return; // the first edition, any second now
        }
        List<Criminal> on = get(player.server).wanted;
        if (on.isEmpty()) {
            player.displayClientMessage(Component.translatable("aicompanion.tv.quiet").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        for (Criminal w : on) {
            player.sendSystemMessage(line(w));
            Network.card(player, Network.Broadcast.NEWS, Component.translatable("aicompanion.card.wanted"), bulletin(w), face(w.id()), null);
        }
    }

    private static Component line(Criminal c) {
        return tv(bulletin(c));
    }

    private static Component bulletin(Criminal c) {
        return Component.translatable("aicompanion.tv.wanted", Names.shown(c.name()), Component.translatable("aicompanion.crime" + (c.crime() + 1)));
    }

    /** The wanted man's face on the screens: one of a few, by his id. */
    static String face(UUID id) {
        String[] faces = {Network.face("mafia_0"), Network.face("mafia_1"), Network.face("mafia_2"),
                "minecraft:textures/entity/player/wide/steve.png", "minecraft:textures/entity/player/wide/kai.png"};
        return faces[Math.floorMod(id.getLeastSignificantBits(), faces.length)];
    }

    static Component tv(Component text) {
        return Component.literal("[TV] ").withStyle(ChatFormatting.AQUA).append(text.copy().withStyle(ChatFormatting.WHITE));
    }

    /** The wanted man this name belongs to (his face was on air), or null. */
    @Nullable
    static Criminal wanted(MinecraftServer server, String name) {
        return get(server).wanted.stream().filter(c -> Names.matches(name, c.name())).findFirst().orElse(null);
    }

    /** One letter off a name from the news. */
    static boolean nearMiss(MinecraftServer server, String name) {
        return name.length() >= 4 && get(server).wanted.stream().anyMatch(c -> Names.distance(name, c.name()) <= 2);
    }

    /** Who's on the screens now, for the notebook's case page and Ryuk. */
    public static List<Criminal> wanted(MinecraftServer server) {
        return List.copyOf(get(server).wanted);
    }

    static boolean is(UUID id) {
        return id.getMostSignificantBits() == MARK;
    }

    /**
     * What the screens tell of a death: a short story, never a bare word. A plain cause in the notebook ("fire",
     * "heart attack", or none) picks one of that cause's stories, with the crime and the place; anything longer is the
     * circumstance itself, and the report tells it as the witnesses do.
     */
    static Component story(String name, int crime, DeathNotes.Cause cause, String how, long seed) {
        java.util.Random r = new java.util.Random(seed);
        Component who = Component.literal(Names.shown(name)), what = Component.translatable("aicompanion.crime" + (Math.floorMod(crime, CRIMES) + 1)),
                where = Component.translatable("aicompanion.tv.place" + (1 + r.nextInt(PLACES)));
        String told = how.strip();
        boolean plain = told.isEmpty() || told.split("\\s+").length <= 3 && (DeathNotes.Cause.of(told) != DeathNotes.Cause.HEART || HEART.matcher(told).matches());
        return plain ? Component.translatable("aicompanion.tv.story." + cause.name().toLowerCase(Locale.ROOT) + (1 + r.nextInt(TOLD)), who, what, where)
                : Component.translatable("aicompanion.tv.story.how" + (1 + r.nextInt(TOLD)), who, what, told);
    }

    /** A name from the news came due: the screens report the death, and the writer's case has one more. */
    static void died(MinecraftServer server, UUID id, String name, DeathNotes.Cause cause, String how, UUID writer) {
        News news = get(server);
        int crime = news.wanted.stream().filter(c -> c.id().equals(id)).mapToInt(Criminal::crime).findFirst()
                .orElse(Math.floorMod(id.getLeastSignificantBits(), CRIMES));
        if (news.wanted.removeIf(c -> c.id().equals(id))) {
            news.setDirty();
        }
        Component report = story(name, crime, cause, how, id.getLeastSignificantBits() ^ server.overworld().getGameTime());
        toViewers(server, tv(report), Network.Broadcast.DEATH, Component.translatable("aicompanion.card.breaking"), report, face(id));
        ServerPlayer p = server.getPlayerList().getPlayer(writer);
        if (p != null) {
            Investigation.onNewsDeath(p, name, cause);
            Pact.thrill(p, Pact.THRILL_BIG);
            CompanionEntity ryuk = CompanionEntity.of(p);
            if (ryuk != null) {
                ryuk.reactLater("clap"); // a slow, mocking applause for his human's work
                String what = how.isBlank() ? cause.text() : how; // the cause as written, the stranger the better: react to it
                ryuk.remark(Config.tr("«" + Names.shown(name) + "», преступник из новостей, мёртв: " + what + ", точно в назначенный тетрадью миг. Об этом уже говорят по ТВ",
                        "\"" + name + "\", the criminal from the news, is dead: " + what + ", exactly when the notebook said. It's already on TV"), "note_death", 0);
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Criminal c : wanted) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", c.id());
            t.putString("Name", c.name());
            t.putInt("Crime", c.crime());
            t.putLong("Until", c.until());
            list.add(t);
        }
        tag.put("Wanted", list);
        tag.putLong("Edition", edition);
        return tag;
    }

    static News load(CompoundTag tag) {
        News news = new News();
        for (Tag t : tag.getList("Wanted", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            news.wanted.add(new Criminal(c.getUUID("Id"), c.getString("Name"), c.getInt("Crime"), c.getLong("Until")));
        }
        news.edition = tag.getLong("Edition");
        return news;
    }
}
