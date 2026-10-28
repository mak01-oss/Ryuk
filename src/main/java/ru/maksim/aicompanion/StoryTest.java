package ru.maksim.aicompanion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * The story-test build (aicompanion-*-storytest.jar, or run-storytest.cmd): a key (client/StorySkip, N by default)
 * moves the story on to its next moment, so the whole story can be seen in an evening instead of days of play. Each
 * moment forces only what it needs (L's estimate, the days between the acts, a name written) and calls the same code the
 * story does; a moment already lived through is passed over; where the story goes somewhere far (L's tower, the
 * hideout, Near's base, the warehouse, the realm), the player is taken there. The normal build has none of it: no key,
 * and the packet is ignored.
 */
public final class StoryTest {
    public static final boolean ON = StoryTest.class.getResource("/aicompanion_storytest.txt") != null
            || "1".equals(System.getenv("AICOMPANION_STORYTEST"));

    private record Beat(String id, String ru, String en, Predicate<ServerPlayer> done, Consumer<ServerPlayer> act) {
    }

    private record Later(long at, UUID player, Consumer<ServerPlayer> run) {
    }

    private static final List<Later> LATER = new ArrayList<>();
    private static final Map<UUID, Long> PRESSED = new HashMap<>();
    @Nullable
    private static String current; // the moment being played out now (retry)

    private static final List<Beat> BEATS = List.of(
            new Beat("note", "Тетрадь падает с неба", "The notebook falls from the sky",
                    p -> c(p).flag("note_fell") || Onboarding.holds(p), p -> Onboarding.fall(p, false)),
            new Beat("ryuk", "Явление Рюка", "Ryuk's arrival",
                    p -> CompanionEntity.of(p) != null || Arrival.running(p), StoryTest::arrival),
            new Beat("tv_hint", "Глава «Телевизор»", "Chapter: the television",
                    p -> c(p).flag("tv_hint") || News.viewer(p), StoryTest::tvHint),
            new Beat("news", "Первая новость: телевизор в кармане", "The first news: a set in the pocket",
                    p -> c(p).flag("news"), StoryTest::television),
            new Beat("kill", "Вписал преступника из новостей: его смерть в эфире", "A criminal from the news written: his death on air",
                    p -> c(p).kills >= 1, StoryTest::writeWanted),
            new Beat("pact", "Игра Рюка", "Ryuk's game",
                    p -> !Config.PACT.get() || c(p).flag("pact_asked") || c(p).pact != Kira.Pact.NONE, Onboarding::offerPact),
            new Beat("kira", "Вторая смерть: ТВ называет убийцу Кирой", "A second death: the TV names the killer Kira",
                    p -> c(p).flag("kira_named"), StoryTest::writeWanted),
            new Beat("shrine", "Поклонники Киры: алтарь у деревни", "Kira's worshippers: a shrine by the village",
                    p -> c(p).flag("shrine"), StoryTest::shrine),
            new Beat("eyes", "Рюк предлагает сделку на глаза", "Ryuk offers the eye deal",
                    p -> c(p).eyes > 0, Eyes::offer),
            new Beat("pages", "Поручение: с неба падает лист чужой тетради", "Side quest: a page of someone else's notebook falls",
                    p -> quest(p) >= 1, SideQuests::startSidoh),
            new Beat("pages_sidoh", "Лист пойман: появляется Сидо", "The page caught: Sidoh appears",
                    p -> quest(p) >= 2, p -> pages(p, 0, 1)),
            new Beat("pages_all", "Все листы Сидо собраны", "All of Sidoh's pages caught",
                    p -> quest(p) >= 3, p -> pages(p, 1, SideQuests.PAGES)),
            new Beat("pages_back", "Листы отданы Сидо: его бинт тебе", "The pages back to Sidoh: his bandage for you",
                    p -> quest(p) >= 4, StoryTest::returnPages),
            new Beat("realm", "Рюк зовёт в мир богов смерти", "Ryuk offers to show his realm",
                    p -> c(p).flag("q_realm"), SideQuests::inviteRealm),
            new Beat("dream", "Сон: мир богов смерти", "The dream: the realm of the gods of death",
                    p -> c(p).flag("film_realm"), Realm::enter),
            new Beat("gamblers", "Мир богов: Гук и Деридовели", "The realm: Gook and Deridovely",
                    p -> c(p).flag("film_god.gamblers"), p -> realm(p, -16.5, 3.5, 180, 10)),
            new Beat("armonia", "Мир богов: Армония", "The realm: Armonia",
                    p -> c(p).flag("film_god.armonia"), p -> realm(p, 16.5, 2.5, 180, -12)),
            new Beat("king", "Мир богов: Король богов смерти", "The realm: the King of the gods of death",
                    p -> c(p).flag("film_god.king"), p -> realm(p, 0.5, 20.5, 0, -30)),
            new Beat("sidoh_home", "Мир богов: Сидо дома, у большой дыры", "The realm: Sidoh at home by the big hole",
                    p -> c(p).flag("dream_sidoh"), p -> realm(p, -41.5, -31.5, -80, 5)),
            new Beat("wake", "Пробуждение", "Waking up",
                    p -> !Realm.dreaming(p), Realm::wake),
            new Beat("special", "Ловушка L: спецвыпуск Линда Л. Тейлора", "L's trap: the Lind L. Tailor special",
                    p -> c(p).stage >= Investigation.BROADCAST, StoryTest::special),
            new Beat("tailor", "Тейлор вписан: умер в эфире, ответил настоящий L", "Tailor written: he dies on air, the real L answers",
                    p -> c(p).flag("tailor_killed") || c(p).stage >= Investigation.AGENTS, StoryTest::tailor),
            new Beat("agent", "Слежка: за тобой ходит агент ФБР", "Surveillance: an FBI agent tails you",
                    p -> c(p).flags.getOrDefault("agents", 0L) > 0, StoryTest::agent),
            new Beat("hq", "Штаб L: приглашение", "L's headquarters: the invitation",
                    p -> c(p).flag("hq"), StoryTest::headquarters),
            new Beat("l_meet", "В штабе: L перед стеной мониторов", "At the headquarters: L before his screens",
                    p -> c(p).flag("film_person.l"), StoryTest::meetL),
            new Beat("search", "Пока тебя нет, твой дом обыскивают", "While you're away, your house is searched",
                    p -> c(p).flag("searched"), StoryTest::search),
            new Beat("sidoh", "Тетрадь Сидо: логово мафии", "Sidoh's notebook: the mafia's hideout",
                    p -> c(p).flag("sidoh"), p -> Mafia.start(p, 220)),
            new Beat("boss", "Главарь мафии мёртв: тетрадь Сидо у тебя", "The mafia boss is dead: Sidoh's notebook is yours",
                    p -> c(p).flag("sidoh_bound") || c(p).flag("sidoh_done"), StoryTest::boss),
            new Beat("sidoh_back", "Тетрадь отдана Сидо: правда о двух ложных правилах", "Sidoh's notebook back to him: the truth about two false rules",
                    p -> c(p).flag("sidoh_done"), StoryTest::sidohBack),
            new Beat("l_dead", "Смерть L", "L dies",
                    p -> c(p).flag("l_dead"), StoryTest::killL),
            new Beat("near", "Преемники L: Near и Mello", "L's successors: Near and Mello",
                    p -> c(p).flag("successor"), StoryTest::successors),
            new Beat("near_meet", "База SPK: Near за своим пазлом", "The SPK base: Near at his puzzle",
                    p -> c(p).flag("film_person.near"), StoryTest::meetNear),
            new Beat("hostage", "Mello берёт заложницу", "Mello takes a hostage",
                    p -> c(p).flag("hostage") || c(p).flag("hostage_done"), StoryTest::hostage),
            new Beat("gevanni", "Джеванни подменяет тетрадь у кровати", "Gevanni swaps the notebook by the bed",
                    p -> c(p).flag("swapped"), StoryTest::gevanni),
            new Beat("box", "Near зовёт на склад Yellow Box", "Near calls you to the Yellow Box",
                    p -> c(p).flag("yellow_box"), StoryTest::box),
            new Beat("showdown", "Склад Yellow Box: встреча на рассвете", "The Yellow Box: the meeting at dawn",
                    p -> c(p).flag("showdown") || c(p).flag("kira_wins"), StoryTest::showdown),
            new Beat("kira_wins", "Финал: Near мёртв, НОВЫЙ МИР", "The end: Near is dead, a NEW WORLD",
                    p -> c(p).flag("kira_wins"), StoryTest::kiraWins));

    private StoryTest() {
    }

    // ------------------------------------------------------------------ the key

    /** The key was pressed (Network.Skip): a name still on its way comes now; else the next moment of the story. */
    static void next(ServerPlayer player) {
        long now = now(player);
        if (now - PRESSED.getOrDefault(player.getUUID(), -100L) < 20) {
            return; // a press a second: a held key doesn't run through the story
        }
        PRESSED.put(player.getUUID(), now);
        Kira.Case c = c(player);
        if (c.sealed) {
            tell(player, Config.tr("Сюжет окончен: Рюк вписал твоё имя. Начни новый мир", "The story is over: Ryuk wrote your name. Start a new world"));
            return;
        }
        if (DeathNotes.hurry(player.server, player.getUUID(), 60) > 0) {
            tell(player, Config.tr("Вписанное имя: смерть через 3 секунды (в обычной игре — через 40)", "The name written: death in 3 seconds (40 in the normal game)"));
            return;
        }
        int i = upcoming(player);
        if (i < 0) {
            tell(player, Config.tr("Сюжет пройден. Другая концовка: на складе подожди минуту, и Near докажет, что ты Кира; или /companion kira finish CAUGHT",
                    "The story is done. The other ending: wait a minute in the warehouse and Near proves you're Kira; or /companion kira finish CAUGHT"));
            return;
        }
        Beat b = BEATS.get(i);
        c.flags.put("test_" + b.id(), now);
        Kira.get(player.server).setDirty();
        player.sendSystemMessage(Component.literal(Config.tr("[Тест сюжета] ", "[Story test] ")).withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal((i + 1) + "/" + BEATS.size() + " · ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(title(b)).withStyle(ChatFormatting.AQUA)));
        AiCompanion.LOGGER.info("Story test: {} ({}/{})", b.id(), i + 1, BEATS.size());
        current = b.id();
        b.act().accept(player);
        current = null;
        int after = upcoming(player);
        if (after >= 0) {
            player.sendSystemMessage(Component.literal(Config.tr("   дальше (N): ", "   next (N): ") + title(BEATS.get(after))).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** Into the world: what the key does, and what comes. */
    static void login(ServerPlayer player) {
        int i = upcoming(player);
        player.sendSystemMessage(Component.literal(Config.tr("[Тест сюжета] ", "[Story test] ")).withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(Config.tr("Тестовая сборка: N — следующий момент сюжета (клавиша — в Управлении). ",
                        "Test build: N moves the story to its next moment (rebind it in Controls). ")).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(i < 0 ? Config.tr("Сюжет уже пройден.", "The story is done.")
                        : Config.tr("Дальше: ", "Next: ") + (i + 1) + "/" + BEATS.size() + " " + title(BEATS.get(i))).withStyle(ChatFormatting.AQUA)));
    }

    /** Every server tick: what was put off for a moment (a chunk to load, a heart to stop). */
    static void tick(MinecraftServer server) {
        if (LATER.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        List<Later> due = LATER.stream().filter(l -> now >= l.at()).toList();
        LATER.removeAll(due);
        for (Later l : due) {
            ServerPlayer p = server.getPlayerList().getPlayer(l.player());
            if (p != null) {
                l.run().accept(p);
            }
        }
    }

    /** Autotest: how many moments there are, and the ones whose mark the story hasn't left (pressed or not). */
    public static int size() {
        return BEATS.size();
    }

    public static List<String> missing(ServerPlayer player) {
        return BEATS.stream().filter(b -> !b.done().test(player)).map(Beat::id).toList();
    }

    private static int upcoming(ServerPlayer player) {
        Kira.Case c = c(player);
        for (int i = 0; i < BEATS.size(); i++) {
            if (!c.flag("test_" + BEATS.get(i).id()) && !BEATS.get(i).done().test(player)) {
                return i;
            }
        }
        return -1;
    }

    private static String title(Beat b) {
        return Config.tr(b.ru(), b.en());
    }

    // ------------------------------------------------------------------ the moments

    /** The notebook that fell into the hand (the one lying out there, else a new one), and he comes at once. */
    private static void arrival(ServerPlayer p) {
        if (!Onboarding.holds(p)) {
            ItemEntity lying = p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(64),
                    e -> e.getItem().is(AiCompanion.DEATH_NOTE.get()) && !Mafia.isSidohsNote(e.getItem())).stream().findFirst().orElse(null);
            ItemStack note = lying != null ? lying.getItem().copy() : new ItemStack(AiCompanion.DEATH_NOTE.get());
            if (lying != null) {
                lying.discard();
            }
            Onboarding.mark(note, p.getUUID());
            give(p, note);
        }
        Arrival.start(p);
    }

    private static void tvHint(ServerPlayer p) {
        c(p).flags.put("tv_hint", now(p));
        Kira.get(p.server).setDirty();
        News.hint(p);
    }

    /** A set into the pocket (never put down, never switched on): the first edition comes with the next second. */
    private static void television(ServerPlayer p) {
        if (!News.viewer(p)) {
            give(p, new ItemStack(AiCompanion.TELEVISION_ITEM.get()));
        }
    }

    /** A face from the screens written in the notebook (one goes on air if none is on); the death comes in 3 seconds. */
    private static void writeWanted(ServerPlayer p) {
        if (News.wanted(p.server).isEmpty()) {
            News.get(p.server).air(p.server, Kira.clock(p.server));
        }
        ItemStack note = note(p);
        List<News.Criminal> on = News.wanted(p.server);
        if (note == null || on.isEmpty()) {
            retry(p, Config.tr("в инвентаре нет тетради смерти", "no Death Note in the inventory"));
            return;
        }
        DeathNotes.write(p, note, on.get(0).name(), "", 0);
        if (DeathNotes.hurry(p.server, p.getUUID(), 60) > 0) {
            tell(p, Config.tr("Вписан " + on.get(0).name() + ": смерть через 3 секунды (в обычной игре — через 40)",
                    on.get(0).name() + " written: death in 3 seconds (40 in the normal game)"));
        }
    }

    private static void shrine(ServerPlayer p) {
        c(p).flags.merge("glory", 45L, Math::max);
        Kira.changed(p);
        tell(p, Config.tr("Алтарь встанет у ближайшей деревни, по карточке — стрелка", "The shrine goes up by the nearest village; the card's arrow points there"));
    }

    /** Sidoh's pages {@code from}..{@code to}-1 caught, as if by hand; the ones still drifting about go. */
    private static void pages(ServerPlayer p, int from, int to) {
        for (int i = from; i < to; i++) {
            if ((c(p).flags.getOrDefault("q_sidoh_got", 0L) & 1L << i) == 0) {
                SideQuests.pageCaught(p, i);
            }
        }
        long got = c(p).flags.getOrDefault("q_sidoh_got", 0L);
        p.serverLevel().getEntitiesOfClass(LostPage.class, p.getBoundingBox().inflate(160), l -> l.ownedBy(p.getUUID()) && (got & 1L << l.index()) != 0)
                .forEach(LostPage::discard);
    }

    private static void returnPages(ServerPlayer p) {
        SidohEntity sidoh = SideQuests.sidoh(p);
        if (sidoh == null) {
            retry(p, Config.tr("Сидо не рядом: подожди, пока прилетит", "Sidoh isn't near: wait till he flies up"));
            return;
        }
        SideQuests.returnPages(p, sidoh);
    }

    /** Somewhere in the realm, facing someone (asleep first, if need be). */
    private static void realm(ServerPlayer p, double x, double z, float yaw, float pitch) {
        ServerLevel realm = p.server.getLevel(Realm.KEY);
        if (realm == null) {
            return;
        }
        if (!Realm.dreaming(p)) {
            Realm.enter(p);
            later(p, 40, q -> realm(q, x, z, yaw, pitch));
            return;
        }
        p.teleportTo(realm, x, Realm.GROUND + 1, z, yaw, pitch);
    }

    private static void special(ServerPlayer p) {
        Kira.Case c = c(p);
        c.stage = Math.max(c.stage, Investigation.RUMOURS);
        Investigation.broadcast(p);
    }

    /** "Lind L. Tailor" in the notebook while he's on the screens (he dies on air in 3 seconds), else his death outright. */
    private static void tailor(ServerPlayer p) {
        ItemStack note = note(p);
        if (Investigation.specialOnAir(p) && note != null) {
            DeathNotes.write(p, note, "Lind L. Tailor", "", 0);
        }
        if (DeathNotes.written(p.server, Investigation.TAILOR)) {
            DeathNotes.hurry(p.server, p.getUUID(), 60);
            tell(p, Config.tr("Имя вписано: Тейлор умрёт в эфире через 3 секунды", "The name written: Tailor dies on air in 3 seconds"));
        } else {
            Investigation.tailorDied(p);
        }
    }

    private static void agent(ServerPlayer p) {
        Kira.Case c = c(p);
        atLeast(p, 15);
        c.stage = Math.max(c.stage, Investigation.AGENTS);
        c.flags.put("broadcast_day", Kira.clock(p.server) - Kira.DAY);
        c.flags.remove("next_agent");
        Investigation.agents(p, now(p));
        tell(p, Config.tr("Агент появляется вне поля зрения и идёт за тобой; монстр рядом с ним — и он покажет удостоверение",
                "The agent turns up out of sight and follows you; a monster near him and he shows his badge"));
    }

    private static void headquarters(ServerPlayer p) {
        Kira.Case c = c(p);
        atLeast(p, 40);
        c.stage = Math.max(c.stage, Investigation.HEADQUARTERS);
        c.flags.put("broadcast_day", Kira.clock(p.server) - 2 * Kira.DAY);
        Headquarters.raise(p);
    }

    /** Upstairs in the hall, beside L on his chair before the screens, his sweets table out of the way (once the tower stands). */
    private static void meetL(ServerPlayer p) {
        BlockPos hq = at(p, "hq");
        if (hq == null) {
            retry(p, Config.tr("штаба ещё нет", "there's no headquarters yet"));
            return;
        }
        afterBuild(p, q -> go(q, hq.getX() + 3.5, hq.getY() + 6.1, hq.getZ() - 2.5, 72, 8));
    }

    /** L's Task Force at the bed: only while its owner is 24 blocks away at least (here, at L's tower). */
    private static void search(ServerPlayer p) {
        BlockPos bed = p.getRespawnPosition();
        if (bed == null || p.getRespawnDimension() != Level.OVERWORLD) {
            tell(p, Config.tr("Обыскивают дом у кровати, а кровати нет: поспи в своей, и обыск придёт ночью сам", "They search the house by the bed, and there is no bed: sleep in one and the search comes by night"));
            return;
        }
        if (bed.distSqr(p.blockPosition()) < 24 * 24) {
            retry(p, Config.tr("отойди от кровати хотя бы на 24 блока: обыскивают, пока тебя нет", "go 24 blocks from your bed at least: they search while you're away"));
            return;
        }
        Investigation.search(p, now(p));
    }

    /**
     * To the shack over the hideout; the boss below dies (a heart attack: his name in your notebook); his notebook
     * comes into your hands, and Sidoh, bound to it, now sees you.
     */
    private static void boss(ServerPlayer p) {
        BlockPos top = at(p, "hideout");
        if (top == null) {
            Mafia.start(p, 220);
            top = at(p, "hideout");
        }
        BlockPos hall = Mafia.hall(p.serverLevel(), top);
        BlockPos stand = Builds.ground(p.serverLevel(), Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, top.offset(0, 0, 8));
        afterBuild(p, q -> {
            go(q, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 180, 0);
            later(q, 40, r -> {
                HumanEntity boss = human(r, hall, HumanEntity.Role.BOSS, 24);
                if (boss != null) {
                    DeathNotes.writeFor(r, boss, 20);
                }
                later(r, 80, s -> {
                    ItemEntity note = s.serverLevel().getEntitiesOfClass(ItemEntity.class, new AABB(hall).inflate(24), e -> Mafia.isSidohsNote(e.getItem()))
                            .stream().findFirst().orElse(null);
                    if (note != null) {
                        give(s, note.getItem().copy());
                        note.discard();
                    } else {
                        tell(s, Config.tr("Тетради Сидо в логове нет: главарь жив? Нажми N ещё раз", "Sidoh's notebook isn't in the hideout: is the boss alive? Press N again"));
                        c(s).flags.remove("test_boss");
                    }
                });
            });
        });
    }

    private static void sidohBack(ServerPlayer p) {
        int slot = -1;
        for (int i = 0; i < p.getInventory().getContainerSize() && slot < 0; i++) {
            if (Mafia.isSidohsNote(p.getInventory().getItem(i))) {
                slot = i;
            }
        }
        SidohEntity sidoh = p.serverLevel().getEntitiesOfClass(SidohEntity.class, p.getBoundingBox().inflate(64), s -> !s.inRealm() && !s.questOf(p))
                .stream().min(java.util.Comparator.comparingDouble(p::distanceToSqr)).orElse(null);
        if (slot < 0 || sidoh == null) {
            retry(p, Config.tr(slot < 0 ? "тетради Сидо у тебя нет" : "Сидо не рядом: подожди, пока прилетит",
                    slot < 0 ? "you don't have Sidoh's notebook" : "Sidoh isn't near: wait till he flies up"));
            return;
        }
        p.getInventory().setItem(slot, ItemStack.EMPTY);
        Mafia.returned(p, sidoh);
    }

    /** In front of L in his hall; his name in your notebook: a heart attack before your eyes. */
    private static void killL(ServerPlayer p) {
        if (at(p, "hq") == null) {
            headquarters(p);
        }
        BlockPos hq = at(p, "hq");
        afterBuild(p, q -> {
            go(q, hq.getX() + 3.5, hq.getY() + 6.1, hq.getZ() - 2.5, 72, 8);
            later(q, 40, r -> {
                HumanEntity l = human(r, hq.above(6), HumanEntity.Role.L, 24);
                if (l == null) {
                    tell(r, Config.tr("L нет в зале: нажми N ещё раз", "L isn't in the hall: press N again"));
                    c(r).flags.remove("test_l_dead");
                    return;
                }
                DeathNotes.writeFor(r, l, 60);
                tell(r, Config.tr("Настоящее имя L вписано: 3 секунды", "L's true name written: 3 seconds"));
            });
        });
    }

    private static void successors(ServerPlayer p) {
        Kira.Case c = c(p);
        c.flags.putIfAbsent("l_dead", now(p));
        c.flags.putIfAbsent("l_dead_day", Kira.clock(p.server) - Kira.DAY - 1);
        c.stage = Math.max(c.stage, Investigation.SUCCESSORS);
        Successors.arrive(p);
    }

    /** Into the SPK's vestibule, Near on the floor at his puzzle before you (once the base stands). */
    private static void meetNear(ServerPlayer p) {
        BlockPos spk = at(p, "spk");
        if (spk == null) {
            retry(p, Config.tr("базы SPK ещё нет", "there's no SPK base yet"));
            return;
        }
        afterBuild(p, q -> go(q, spk.getX() + 0.5, spk.getY() + 1, spk.getZ() + 8.5, 180, 25));
    }

    /** Home first (Mello takes a girl from the village nearest to you), then his demand. */
    private static void hostage(ServerPlayer p) {
        BlockPos home = p.getRespawnPosition() != null && p.getRespawnDimension() == Level.OVERWORLD ? p.getRespawnPosition()
                : p.server.overworld().getSharedSpawnPos();
        go(p, home.getX() + 0.5, home.getY() + 1, home.getZ() + 0.5, p.getYRot(), 0);
        later(p, 40, q -> {
            Successors.kidnap(q);
            if (!c(q).flag("hostage")) {
                tell(q, Config.tr("Рядом нет деревни: Mello отказался от затеи", "No village near: Mello dropped the idea"));
            }
        });
    }

    /** Every real notebook in the chests by the bed becomes a perfect copy (in the game you'd only learn it in the warehouse). */
    private static void gevanni(ServerPlayer p) {
        BlockPos bed = p.getRespawnPosition();
        int n = bed == null ? 0 : Successors.swap(p.serverLevel(), bed);
        if (n > 0) {
            c(p).flags.putIfAbsent("swapped", now(p));
            Kira.get(p.server).setDirty();
        }
        tell(p, n > 0 ? Config.tr("Джеванни подменил тетрадей у кровати: " + n + ". Запись в копии ничего не делает",
                        "Gevanni swapped notebooks by the bed: " + n + ". Writing in a copy does nothing")
                : Config.tr("У кровати нет сундука с тетрадью: подменять нечего (положи тетрадь в сундук у кровати — и /companion kira swap)",
                "No chest with a notebook by the bed: nothing to swap (put one in a chest by the bed, then /companion kira swap)"));
    }

    private static void box(ServerPlayer p) {
        atLeast(p, 80);
        Successors.invite(p);
    }

    /** Dawn has come, and you're in the warehouse, Near on the floor before you. */
    private static void showdown(ServerPlayer p) {
        BlockPos box = at(p, "yellow_box");
        if (box == null) {
            retry(p, Config.tr("склада ещё нет", "there's no warehouse yet"));
            return;
        }
        c(p).flags.put("meeting", Kira.clock(p.server));
        Kira.get(p.server).setDirty();
        afterBuild(p, q -> go(q, box.getX() + 0.5, box.getY() + 1, box.getZ() + 4.5, 180, 15));
        tell(p, Config.tr("Минута, и Near сыграет свою карту; N — его имя в тетрадь", "A minute and Near plays his card; N writes his name"));
    }

    /** Near's name in your notebook, there in the warehouse: Kira wins. */
    private static void kiraWins(ServerPlayer p) {
        BlockPos box = at(p, "yellow_box");
        HumanEntity near = box == null ? null : human(p, box, HumanEntity.Role.NEAR, 32);
        if (near != null) {
            DeathNotes.writeFor(p, near, 60);
            tell(p, Config.tr("Настоящее имя Near вписано: 3 секунды", "Near's true name written: 3 seconds"));
        } else {
            Successors.kiraWins(p);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static Kira.Case c(ServerPlayer p) {
        return Kira.of(p);
    }

    private static long now(ServerPlayer p) {
        return p.server.overworld().getGameTime();
    }

    private static long quest(ServerPlayer p) {
        return c(p).flags.getOrDefault("q_sidoh", 0L);
    }

    @Nullable
    private static BlockPos at(ServerPlayer p, String flag) {
        Long at = c(p).flags.get(flag);
        return at == null ? null : BlockPos.of(at);
    }

    private static void atLeast(ServerPlayer p, float suspicion) {
        Kira.Case c = c(p);
        if (c.suspicion < suspicion) {
            c.suspicion = suspicion;
            Kira.changed(p);
        }
    }

    /** The first real notebook of the player's own in the inventory. */
    @Nullable
    private static ItemStack note(ServerPlayer p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(AiCompanion.DEATH_NOTE.get()) && DeathNoteItem.writable(s) && !DeathNoteItem.fake(s) && !Mafia.isSidohsNote(s)) {
                return s;
            }
        }
        return null;
    }

    @Nullable
    private static HumanEntity human(ServerPlayer p, BlockPos near, HumanEntity.Role role, int r) {
        return p.serverLevel().getEntitiesOfClass(HumanEntity.class, new AABB(near).inflate(r), h -> h.role() == role && h.isAlive())
                .stream().findFirst().orElse(null);
    }

    private static void give(ServerPlayer p, ItemStack stack) {
        if (!p.getInventory().add(stack)) {
            p.drop(stack, false);
        }
    }

    /** Out of a dream first, then there. */
    private static void go(ServerPlayer p, double x, double y, double z, float yaw, float pitch) {
        if (Realm.dreaming(p)) {
            Realm.wake(p);
        }
        p.teleportTo(p.server.overworld(), x, y, z, yaw, pitch);
    }

    /** {@code run} once every block queued by the story is placed (a tower, a hideout), for whoever is still here. */
    private static void afterBuild(ServerPlayer p, Consumer<ServerPlayer> run) {
        UUID id = p.getUUID();
        MinecraftServer server = p.server;
        Builds.afterBuild(() -> {
            ServerPlayer q = server.getPlayerList().getPlayer(id);
            if (q != null) {
                run.accept(q);
            }
        });
    }

    private static void later(ServerPlayer p, int ticks, Consumer<ServerPlayer> run) {
        LATER.add(new Later(now(p) + ticks, p.getUUID(), run));
    }

    private static void tell(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal("   " + text).withStyle(ChatFormatting.GRAY));
    }

    /** This moment couldn't happen now: why, and the key tries it again. */
    private static void retry(ServerPlayer p, String why) {
        c(p).flags.remove("test_" + current);
        tell(p, Config.tr("Не вышло: ", "Not now: ") + why + Config.tr(". N — ещё раз", ". N to try again"));
    }
}
