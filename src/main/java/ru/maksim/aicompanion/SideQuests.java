package ru.maksim.aicompanion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Side quests, apart from the main story and in no hurry: each has a card when it starts and when it's done, a line on
 * the notebook's "Cases" page and a place for Ryuk's arrow. Their state lives in the player's Kira.Case flags.
 * <ul>
 * <li>Sidoh's pages: a page of another notebook flutters down out of the sky; whoever catches it sees Sidoh, who has
 * scattered his pages again; four more are loose in the wind, and they dodge. Back in his claws, they buy his bandage.</li>
 * <li>The realm of the gods of death: one evening Ryuk offers to show his world in a dream (a golden apple, then bed).
 * Five sights to see there: a window down to the humans, the bone toss, Armonia, the King, the dead tree's apple.</li>
 * <li>The King's tribute: Armonia tells of it; thirteen apples in the bowl at the King's feet, over as many dreams as
 * it takes, and the King gives back the years the eyes took (or, to one who never paid, the eyes for nothing).</li>
 * </ul>
 */
public final class SideQuests {
    public static final int PAGES = 5;
    /** The realm's sights, bits of "q_realm_obj". */
    public static final int R_WINDOW = 0, R_BONES = 1, R_ARMONIA = 2, R_KING = 3, R_APPLE = 4, SIGHTS = 5;
    private static final String[] SIGHT = {"window", "bones", "armonia", "king", "apple"};
    public static final int TRIBUTE = 13; // apples the King wants
    private static final Map<UUID, Long> ARROW = new HashMap<>(); // the page the arrow points at now (index | pos)

    private SideQuests() {
    }

    /** Once a second for each player. */
    static void tick(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        if (c.sealed || Realm.dreaming(player) || player.level().dimension() != Level.OVERWORLD) {
            return;
        }
        long stage = c.flags.getOrDefault("q_sidoh", 0L);
        if (stage == 0 && sidohMayCome(player, c) && player.getRandom().nextInt(300) == 0) {
            startSidoh(player);
        } else if (stage == 2) {
            pages(player, c);
        }
        if (!c.flag("q_realm") && realmMayInvite(player, c) && player.getRandom().nextInt(120) == 0) {
            inviteRealm(player);
        }
    }

    // ------------------------------------------------------------------ Sidoh's pages

    /** Daylight, outdoors, Ryuk about, past the first day and the first news death, nothing else of the kind going on. */
    private static boolean sidohMayCome(ServerPlayer player, Kira.Case c) {
        long time = player.serverLevel().getDayTime() % 24000;
        return CompanionEntity.of(player) != null && Investigation.day(player) >= 1 && c.kills >= 1 && time > 1000 && time < 11000
                && player.serverLevel().canSeeSky(player.blockPosition().above());
    }

    /** The first page falls in front of the player, swaying like a leaf. */
    public static void startSidoh(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        c.flags.put("q_sidoh", 1L);
        Kira.changed(player);
        LostPage.drop(player);
        player.serverLevel().playSound(null, player.blockPosition(), Sounds.PAGE_GUST.get(), SoundSource.AMBIENT, 0.8F, 1.0F);
        Network.card(player, Network.Broadcast.QUEST, Component.translatable("aicompanion.quest.page"),
                Component.translatable("aicompanion.quest.page_text"), "", null);
        ryuk(player, Config.tr("с неба, кружась, падает лист чужой тетради смерти. Ты узнаёшь почерк: это Сидо, растяпа, опять растерял страницы. Тебе смешно",
                "a page of someone else's Death Note comes fluttering down out of the sky. You know the scrawl: Sidoh, that bungler, has lost his pages again. You find it funny"));
    }

    /** A page in the hand: the first one shows its owner, every one brings the end closer. */
    public static void pageCaught(ServerPlayer player, int index) {
        Kira.Case c = Kira.of(player);
        long got = c.flags.getOrDefault("q_sidoh_got", 0L) | 1L << index;
        c.flags.put("q_sidoh_got", got);
        give(player, new ItemStack(AiCompanion.SIDOH_PAGE.get()));
        int n = Long.bitCount(got);
        if (index == 0) { // touching a piece of his notebook: now the human sees him, as the rules have it
            c.flags.put("q_sidoh", 2L);
            if (!c.flag("sidoh_touched")) {
                c.flags.put("sidoh_touched", player.server.overworld().getGameTime());
            }
            scatter(player, c);
            Kira.changed(player);
            SidohEntity sidoh = sidoh(player);
            if (sidoh == null) {
                sidoh = AiCompanion.SIDOH.get().create(player.serverLevel());
                if (sidoh != null) {
                    Vec3 from = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(9)).add(0, 9, 0);
                    sidoh.moveTo(from.x, from.y, from.z);
                    sidoh.questFor(player);
                    player.serverLevel().addFreshEntity(sidoh);
                }
            }
            if (sidoh != null) {
                sidoh.act("scared", 60);
                Films.meet(player, "god.sidoh", sidoh, null); // another god of death, the first time
            }
            say(player, "aicompanion.sidoh.q.hello");
            Network.card(player, Network.Broadcast.QUEST, Component.translatable("aicompanion.quest.sidoh"),
                    Component.translatable("aicompanion.quest.sidoh_text", PAGES - 1), "", null);
            ryuk(player, Config.tr("твой человек поймал лист и теперь видит Сидо: трясущийся бог смерти в бинтах, с клювиком. Он снова рассыпал листы своей тетради, ветер разнёс их по округе, и он умоляет помочь. Подразни его",
                    "your human caught the page and now sees Sidoh: a shaking, bandaged god of death with a little beak. He's scattered his notebook's pages again, the wind has carried them all over, and he begs for help. Tease him"));
            arrow(player, c, true);
            return;
        }
        player.displayClientMessage(Component.translatable("aicompanion.quest.pages", n, PAGES).withStyle(ChatFormatting.GOLD), true);
        SidohEntity sidoh = sidoh(player);
        if (n >= PAGES) {
            c.flags.put("q_sidoh", 3L);
            Kira.changed(player);
            if (sidoh != null) {
                sidoh.act("beg", 200);
            }
            say(player, "aicompanion.sidoh.q.all");
            player.sendSystemMessage(Component.translatable("aicompanion.quest.give_back").withStyle(ChatFormatting.GOLD));
            ARROW.remove(player.getUUID());
            return;
        }
        if (sidoh != null) {
            sidoh.act("joy", 40);
        }
        say(player, "aicompanion.sidoh.q.got" + (1 + player.getRandom().nextInt(3)));
        arrow(player, c, true);
    }

    /** A gust carried a page off: the arrow follows it. */
    static void pageBlown(ServerPlayer player, LostPage page, Vec3 to) {
        Kira.Case c = Kira.of(player);
        if (page.index() > 0) {
            c.flags.put("q_sidoh_p" + page.index(), BlockPos.containing(to).asLong());
        }
        player.displayClientMessage(Component.translatable("aicompanion.quest.gust").withStyle(ChatFormatting.GRAY), true);
    }

    /** All five in his claws: his thanks, his spare bandage, and off he flies home before Armonia notices. */
    public static boolean returnPages(ServerPlayer player, SidohEntity sidoh) {
        Kira.Case c = Kira.of(player);
        if (c.flags.getOrDefault("q_sidoh", 0L) != 3L) {
            return false;
        }
        int have = player.getInventory().countItem(AiCompanion.SIDOH_PAGE.get());
        if (have < PAGES) {
            say(player, "aicompanion.sidoh.q.short");
            return true;
        }
        player.getInventory().clearOrCountMatchingItems(s -> s.is(AiCompanion.SIDOH_PAGE.get()), PAGES, player.inventoryMenu.getCraftSlots());
        c.flags.put("q_sidoh", 4L);
        c.flags.put("q_sidoh_done", player.server.overworld().getGameTime());
        Kira.changed(player);
        give(player, new ItemStack(AiCompanion.SIDOH_BANDAGE.get()));
        sidoh.act("thank", 60);
        sidoh.leaveIn(110);
        say(player, "aicompanion.sidoh.q.thanks");
        Network.card(player, Network.Broadcast.QUEST, Component.translatable("aicompanion.quest.sidoh_done"),
                Component.translatable("aicompanion.quest.sidoh_done_text"), "", null, "sting.quest_done");
        Pact.thrill(player, Pact.THRILL_BIG);
        ryuk(player, Config.tr("твой человек собрал Сидо все листы. Сидо на радостях отдал ему свой запасной бинт: намотаешь на голову — и лица не видно, имени по лицу не прочесть. Сидо улетает домой, пока Армония не заметил",
                "your human got all of Sidoh's pages back. Overjoyed, Sidoh gave them his spare bandage: wound round the head it hides the face, so no one reads their name off it. Sidoh flies home before Armonia notices"));
        CompanionEntity ryuk = CompanionEntity.of(player);
        if (ryuk != null) {
            ryuk.reactLater("clap");
        }
        return true;
    }

    /** Chocolate! For that he remembers which way one of them flew. */
    public static void sidohHint(ServerPlayer player, SidohEntity sidoh) {
        BlockPos page = nearestPage(player, Kira.of(player));
        if (page == null) {
            return;
        }
        sidoh.pointAt(Vec3.atCenterOf(page));
        say(player, "aicompanion.sidoh.q.hint");
        player.sendSystemMessage(CaseFile.at(player, "aicompanion.case.page", page.asLong()).withStyle(ChatFormatting.YELLOW));
        Network.card(player, Network.Broadcast.WAY, Component.translatable("aicompanion.card.page"), Component.empty(), "", page);
    }

    /** Four more pages, in four directions, 40 to 100 blocks off, over the ground. */
    private static void scatter(ServerPlayer player, Kira.Case c) {
        ServerLevel level = player.serverLevel();
        double base = player.getRandom().nextDouble() * Math.PI * 2;
        for (int i = 1; i < PAGES; i++) {
            double a = base + (i - 1) * Math.PI / 2 + (player.getRandom().nextDouble() - 0.5) * 0.9;
            double d = 40 + player.getRandom().nextDouble() * 60;
            BlockPos column = BlockPos.containing(player.getX() + Math.cos(a) * d, player.getY(), player.getZ() + Math.sin(a) * d);
            BlockPos ground = Builds.ground(level, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            c.flags.put("q_sidoh_p" + i, ground.above(2 + player.getRandom().nextInt(2)).asLong());
        }
    }

    /** Pages come into the world when their human is near enough; a lost one (killed off, say) comes back. */
    private static void pages(ServerPlayer player, Kira.Case c) {
        ServerLevel level = player.serverLevel();
        long got = c.flags.getOrDefault("q_sidoh_got", 0L), spawned = c.flags.getOrDefault("q_sidoh_spawned", 0L);
        for (int i = 1; i < PAGES; i++) {
            if ((got & 1L << i) != 0 || !c.flag("q_sidoh_p" + i)) {
                continue;
            }
            BlockPos at = BlockPos.of(c.flags.get("q_sidoh_p" + i));
            double far = Math.sqrt(player.blockPosition().distToCenterSqr(at.getX(), player.getY(), at.getZ()));
            int index = i;
            boolean exists = !level.getEntitiesOfClass(LostPage.class, player.getBoundingBox().inflate(far + 16),
                    p -> p.index() == index && p.ownedBy(player.getUUID())).isEmpty();
            if (!exists && far < 72 && level.hasChunkAt(at) && ((spawned & 1L << i) == 0 || far < 24)) {
                LostPage page = AiCompanion.LOST_PAGE.get().create(level);
                if (page != null) {
                    page.setup(player, i, Vec3.atBottomCenterOf(at), 1 + player.getRandom().nextInt(2));
                    level.addFreshEntity(page);
                    spawned |= 1L << i;
                    c.flags.put("q_sidoh_spawned", spawned);
                }
            }
        }
        if (player.tickCount % 100 == 0) {
            arrow(player, c, false);
        }
    }

    /** The arrow on the nearest page still loose; only resent when it's another page or it has moved. */
    private static void arrow(ServerPlayer player, Kira.Case c, boolean force) {
        BlockPos page = nearestPage(player, c);
        if (page == null) {
            return;
        }
        long key = page.asLong();
        Long last = ARROW.get(player.getUUID());
        if (force || last == null || last != key && BlockPos.of(last).distSqr(page) > 36) {
            ARROW.put(player.getUUID(), key);
            Network.card(player, Network.Broadcast.WAY, Component.translatable("aicompanion.card.page"), Component.empty(), "", page);
        }
    }

    @Nullable
    private static BlockPos nearestPage(ServerPlayer player, Kira.Case c) {
        long got = c.flags.getOrDefault("q_sidoh_got", 0L);
        BlockPos best = null;
        for (int i = 1; i < PAGES; i++) {
            if ((got & 1L << i) == 0 && c.flag("q_sidoh_p" + i)) {
                BlockPos p = BlockPos.of(c.flags.get("q_sidoh_p" + i));
                if (best == null || p.distSqr(player.blockPosition()) < best.distSqr(player.blockPosition())) {
                    best = p;
                }
            }
        }
        return best;
    }

    /** Autotest: where the next loose page is (null when none is left). */
    @Nullable
    public static BlockPos nextPage(ServerPlayer player) {
        return nearestPage(player, Kira.of(player));
    }

    /** Autotest: the side quests' lines on the "Cases" page, as text. */
    public static List<String> describe(ServerPlayer player) {
        return lines(player).stream().map(Component::getString).toList();
    }

    public static long stage(ServerPlayer player, String quest) {
        return Kira.of(player).flags.getOrDefault("q_" + quest, 0L);
    }

    @Nullable
    static SidohEntity sidoh(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(SidohEntity.class, player.getBoundingBox().inflate(96), s -> s.questOf(player))
                .stream().findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ the realm of the gods of death

    /** Dusk from the second day on, Ryuk close by, no page of Sidoh's falling at the moment. */
    private static boolean realmMayInvite(ServerPlayer player, Kira.Case c) {
        CompanionEntity ryuk = CompanionEntity.of(player);
        long time = player.serverLevel().getDayTime() % 24000;
        return ryuk != null && ryuk.distanceTo(player) < 16 && Investigation.day(player) >= 1 && time > 11500 && time < 13500
                && c.flags.getOrDefault("q_sidoh", 0L) != 1L;
    }

    /** Bored of an evening, Ryuk offers to show his world: a golden apple for him, then bed. */
    public static void inviteRealm(ServerPlayer player) {
        Kira.of(player).flags.put("q_realm", 1L);
        Kira.get(player.server).setDirty();
        Network.card(player, Network.Broadcast.QUEST, Component.translatable("aicompanion.quest.realm"),
                Component.translatable("aicompanion.quest.realm_text"), "", null);
        ryuk(player, Config.tr("вечереет, скучно. Ты предлагаешь своему человеку показать твой мир, мир богов смерти — во сне: живым туда нельзя, а сон — другое дело. Цена: золотое яблоко тебе, и потом лечь спать, пока ты рядом",
                "evening's coming, and you're bored. You offer to show your human your world, the realm of the gods of death, in a dream: the living can't go there, but a dream is another matter. The price: a golden apple for you, then bed while you're near"));
        CompanionEntity ryuk = CompanionEntity.of(player);
        if (ryuk != null) {
            ryuk.reactLater("show_wings");
        }
    }

    /** Asleep in the realm: the first time, the five sights to see. */
    static void realmEntered(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        ARROW.remove(player.getUUID());
        if (c.flags.getOrDefault("q_realm", 0L) < 2) {
            c.flags.put("q_realm", 2L);
            Kira.get(player.server).setDirty();
            Network.card(player, Network.Broadcast.QUEST, Component.translatable("aicompanion.quest.realm_in"),
                    Component.translatable("aicompanion.quest.realm_in_text", SIGHTS), "", null);
        }
    }

    /** Awake again with something left unseen: how to get back. */
    static void realmLeft(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        int seen = Long.bitCount(c.flags.getOrDefault("q_realm_obj", 0L));
        if (c.flags.getOrDefault("q_realm", 0L) == 2L && seen < SIGHTS) {
            player.sendSystemMessage(Component.translatable("aicompanion.quest.realm_again", SIGHTS - seen).withStyle(ChatFormatting.GOLD));
        }
    }

    /** One of the sights seen; all five finish the quest. */
    static void realmDone(ServerPlayer player, int sight) {
        Kira.Case c = Kira.of(player);
        long seen = c.flags.getOrDefault("q_realm_obj", 0L);
        if ((seen & 1L << sight) != 0) {
            return;
        }
        seen |= 1L << sight;
        c.flags.put("q_realm_obj", seen);
        Kira.get(player.server).setDirty();
        ARROW.remove(player.getUUID()); // on to the next one
        int n = Long.bitCount(seen);
        player.displayClientMessage(Component.translatable("aicompanion.quest.realm_seen", Component.translatable("aicompanion.sight." + SIGHT[sight]),
                n, SIGHTS).withStyle(ChatFormatting.GOLD), true);
        Network.cue(player, "sting.sight");
        if (n < SIGHTS || c.flags.getOrDefault("q_realm", 0L) != 2L) {
            return;
        }
        c.flags.put("q_realm", 3L);
        Network.card(player, Network.Broadcast.QUEST, Component.translatable("aicompanion.quest.realm_done"),
                Component.translatable("aicompanion.quest.realm_done_text"), "", null, "sting.quest_done");
        Pact.thrill(player, Pact.THRILL_BIG);
        ryuk(player, Config.tr("твой человек увидел во сне весь твой мир: дыры к людям, кости Гука, Армонию, Короля, мёртвое дерево. Теперь понимает, почему тебе там было так скучно",
                "in the dream your human has now seen all of your world: the windows to the humans, Gook's bones, Armonia, the King, the dead tree. Now they get why you were so bored there"));
    }

    /** In the realm, once a second: the arrow on the nearest sight not seen yet, or on the King's bowl. */
    static void realmArrow(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        BlockPos to = null;
        String name = "aicompanion.sight.tribute";
        if (c.flags.getOrDefault("q_realm", 0L) == 2L) {
            long seen = c.flags.getOrDefault("q_realm_obj", 0L);
            for (int i = 0; i < SIGHTS; i++) {
                BlockPos p = Realm.sight(i);
                if ((seen & 1L << i) == 0 && (to == null || p.distSqr(player.blockPosition()) < to.distSqr(player.blockPosition()))) {
                    to = p;
                    name = "aicompanion.sight." + SIGHT[i] + "_how";
                }
            }
        } else if (c.flags.getOrDefault("q_king", 0L) == 1L) {
            to = Realm.offering();
        }
        Long last = ARROW.get(player.getUUID());
        if (to != null && (last == null || last != to.asLong())) {
            ARROW.put(player.getUUID(), to.asLong());
            Network.card(player, Network.Broadcast.WAY, Component.translatable(name), Component.empty(), "", to);
        }
    }

    /** What Armonia says first: of the tribute, while nobody has told this human yet. */
    static String armoniaLine(ServerPlayer player, String otherwise) {
        return Kira.of(player).flags.getOrDefault("q_king", 0L) == 0L ? "aicompanion.realm.armonia_tribute" : otherwise;
    }

    /** Armonia told of the tribute: the King's quest is on. */
    static void armoniaTalked(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        if (c.flags.getOrDefault("q_king", 0L) != 0L) {
            return;
        }
        c.flags.put("q_king", 1L);
        Kira.get(player.server).setDirty();
        ARROW.remove(player.getUUID());
        Network.card(player, Network.Broadcast.QUEST, Component.translatable("aicompanion.quest.king"),
                Component.translatable("aicompanion.quest.king_text", TRIBUTE), "", null);
        ryuk(player, Config.tr("Армония рассказал твоему человеку о дани Королю: тринадцать яблок в чашу у его ног, и Король может вернуть годы, отданные за глаза. Тринадцать яблок! Отдать их, а не тебе",
                "Armonia told your human about the tribute to the King: thirteen apples in the bowl at his feet, and the King may give back the years paid for the eyes. Thirteen apples! Given to him, not to you"));
    }

    /** Apples into the bowl at the King's feet, as many as it takes over as many dreams; at thirteen, the King's gift. */
    static void tribute(ServerPlayer player, InteractionHand hand) {
        Kira.Case c = Kira.of(player);
        long stage = c.flags.getOrDefault("q_king", 0L);
        int n = c.flags.getOrDefault("q_king_n", 0L).intValue();
        ItemStack held = player.getItemInHand(hand);
        if (stage != 1L || !held.is(Items.APPLE)) {
            player.displayClientMessage(Component.translatable(stage == 0L ? "aicompanion.realm.bowl_empty" : stage == 1L ? "aicompanion.realm.bowl_apples"
                    : "aicompanion.realm.bowl_done", n, TRIBUTE).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int take = Math.min(held.getCount(), TRIBUTE - n);
        held.shrink(take);
        n += take;
        c.flags.put("q_king_n", (long) n);
        Kira.get(player.server).setDirty();
        ServerLevel level = player.serverLevel();
        BlockPos bowl = Realm.offering();
        level.playSound(null, bowl, Sounds.REALM_OFFERING.get(), SoundSource.AMBIENT, 1.2F, 1.0F);
        level.sendParticles(ParticleTypes.SOUL, bowl.getX() + 0.5, bowl.getY() + 1, bowl.getZ() + 0.5, 6 + take, 0.3, 0.4, 0.3, 0.03);
        if (n < TRIBUTE) {
            player.displayClientMessage(Component.translatable("aicompanion.quest.tribute", n, TRIBUTE).withStyle(ChatFormatting.GOLD), true);
            return;
        }
        c.flags.put("q_king", 2L);
        boolean paid = c.eyes > 0;
        if (paid) {
            c.life *= 2; // the years the last eye deal took
        } else {
            c.eyes++; // the eyes, and not a day of life for them
            Network.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), new Network.Offer(Network.Choice.EYE_LOOK));
        }
        Kira.changed(player);
        Realm.kingRoars(level, player);
        Network.card(player, Network.Broadcast.QUEST, Component.translatable("aicompanion.quest.king_done"),
                Component.translatable(paid ? "aicompanion.quest.king_life" : "aicompanion.quest.king_eyes"), "", null, "none"); // the tribute's own bells ring
        Pact.thrill(player, Pact.THRILL_BIG);
        ryuk(player, paid ? Config.tr("Король принял дань твоего человека и вернул ему годы, отданные за глаза. Такого не бывает! Армония будет в бешенстве",
                        "the King took your human's tribute and gave back the years they paid for the eyes. That never happens! Armonia will be livid")
                : Config.tr("Король принял дань твоего человека и даровал ему глаза бога смерти даром, не взяв ни дня жизни. Ты такого не видел ни разу",
                        "the King took your human's tribute and granted them the eyes of a god of death for nothing, without taking a single day of life. You've never seen that before"));
    }

    /** An apple of the realm, eaten: sand in the mouth, and for a moment every life around shows, as a god of death sees it. */
    static void ateRealmApple(ServerPlayer player) {
        for (LivingEntity e : player.serverLevel().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(32),
                e -> e != player && e.isAlive() && !(e instanceof CompanionEntity))) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
        }
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, false, false));
        player.sendSystemMessage(Component.translatable("aicompanion.realm.apple_eaten").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        ryuk(player, Config.tr("твой человек съел яблоко из твоего мира. Гадость, сухое, как песок, но на минуту он чувствует каждую жизнь вокруг, как бог смерти. Посмейся",
                "your human ate an apple from your realm. Vile, dry as sand, but for a moment they sense every life around them, like a god of death. Laugh"));
    }

    // ------------------------------------------------------------------ for the notebook's "Cases" page and Ryuk's arrow

    static List<Component> lines(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        List<Component> out = new ArrayList<>();
        long sidoh = c.flags.getOrDefault("q_sidoh", 0L);
        if (sidoh > 0) {
            int n = Long.bitCount(c.flags.getOrDefault("q_sidoh_got", 0L));
            out.add(Component.translatable(sidoh >= 4 ? "aicompanion.case.q_sidoh_done" : sidoh == 3 ? "aicompanion.case.q_sidoh_back"
                    : sidoh == 1 ? "aicompanion.case.q_sidoh_first" : "aicompanion.case.q_sidoh", n, PAGES));
        }
        long realm = c.flags.getOrDefault("q_realm", 0L);
        if (realm > 0) {
            out.add(Component.translatable(realm >= 3 ? "aicompanion.case.q_realm_done" : realm == 1 ? "aicompanion.case.q_realm_first"
                    : "aicompanion.case.q_realm", Long.bitCount(c.flags.getOrDefault("q_realm_obj", 0L)), SIGHTS));
        }
        long king = c.flags.getOrDefault("q_king", 0L);
        if (king > 0) {
            out.add(Component.translatable(king >= 2 ? "aicompanion.case.q_king_done" : "aicompanion.case.q_king",
                    c.flags.getOrDefault("q_king_n", 0L), TRIBUTE));
        }
        if (!out.isEmpty()) {
            out.add(0, Component.translatable("aicompanion.case.quests").withStyle(ChatFormatting.UNDERLINE));
        }
        return out;
    }

    @Nullable
    static CaseFile.Goal goal(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        if (c.flags.getOrDefault("q_sidoh", 0L) == 2L) {
            BlockPos page = nearestPage(player, c);
            if (page != null) {
                return new CaseFile.Goal("aicompanion.card.page", "aicompanion.case.page", page);
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ helpers

    private static void say(ServerPlayer player, String key) {
        if (Mafia.seesSidoh(player)) {
            player.sendSystemMessage(Component.literal("<" + Config.tr("Сидо", "Sidoh") + "> ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.translatable(key).withStyle(ChatFormatting.GRAY)));
        }
    }

    private static void ryuk(ServerPlayer player, String event) {
        CompanionEntity ryuk = CompanionEntity.of(player);
        if (ryuk != null) {
            ryuk.remark(event, "story", 0);
        }
    }

    static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    static void puff(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1, at.z, 24, 0.4, 0.7, 0.4, 0.02);
    }
}
