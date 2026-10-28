package ru.maksim.aicompanion;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The realm of the gods of death, seen in a dream. No living human ever went there (the dead go to Mu), but Ryuk can
 * show it to his human while they sleep: feed him a golden apple and go to bed with him near. A dim waste of ash and
 * bone under a dying sun (client/RealmSky), chains hanging out of the sky, a giant's skull and ribs half sunk in the
 * ground, rusted steel lying about (Obata saw it as an abandoned building); holes in the ground that look down on the
 * human world far below (fall into one and you wake up); a dead tree with the realm's own apples, shrivelled and tasting
 * of sand; Gook and Deridovely tossing bones into a bowl for apples; Armonia, who knows every rule, on his throne; the
 * King hanging in his chains. Five minutes, then you wake in your bed. The side quests SideQuests.REALM and KING live here.
 */
public final class Realm {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(AiCompanion.MODID, "shinigami_realm"));
    public static final int GROUND = 50; // standing height; the flat generator's surface (gravel) is the layer below
    private static final long DREAM = 6000;
    private static final BlockPos HOLE = new BlockPos(0, GROUND, -14), KING = new BlockPos(0, GROUND + 18, 42),
            BOWL = new BlockPos(-16, GROUND + 1, -4), THRONE = new BlockPos(16, GROUND + 1, -6), TREE = new BlockPos(14, GROUND, 12),
            SKULL = new BlockPos(-46, GROUND + 4, 24), OFFERING = new BlockPos(0, GROUND + 1, 33), VERSION = new BlockPos(0, GROUND - 2, 0);
    /** The holes down to the human world: the first one is the old exit, all of them wake you if you jump in. */
    private static final BlockPos[] WINDOWS = {HOLE, new BlockPos(-30, GROUND, -26), new BlockPos(34, GROUND, -22), new BlockPos(-6, GROUND, -48)};
    private static final double[] WINDOW_R = {3, 6.5, 3.5, 4.5};
    private static final BlockPos[] OLD_ORBS = {new BlockPos(-22, GROUND + 4, 14), new BlockPos(22, GROUND + 4, 14), new BlockPos(0, GROUND + 4, 26)};
    private static final String APPLE_TAG = "aicompanion_realm_apple", LABEL_TAG = "aicompanion_realm_label";
    /** Sidoh's place at home: on the lip of the big hole, staring down after his pages. */
    private static final BlockPos SIDOH_HOME = new BlockPos(-35, GROUND + 1, -31);

    private Realm() {
    }

    public static boolean dreaming(ServerPlayer player) {
        return Kira.of(player).flag("dreaming");
    }

    /** Once a second: falling asleep after a golden apple, the dream itself, waking up. */
    static void tick(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        long now = player.server.overworld().getGameTime();
        if (!c.flag("dreaming")) {
            CompanionEntity ryuk = CompanionEntity.of(player);
            if (player.isSleeping() && player.getSleepTimer() > 30 && now - c.flags.getOrDefault("golden", -99999L) < 6000
                    && ryuk != null && ryuk.distanceTo(player) < 16) {
                enter(player);
            }
            return;
        }
        ServerLevel realm = player.server.getLevel(KEY);
        if (realm == null || player.level() != realm) {
            if (player.isAlive()) { // out of the dream some other way (died in the void): awake, and mortal, again
                c.flags.remove("dreaming");
                if (!c.sealed) {
                    player.setGameMode(GameType.byId(c.flags.getOrDefault("dream_mode", 0L).intValue()));
                }
                Kira.get(player.server).setDirty();
            }
            return;
        }
        if (now > c.flags.get("dreaming") || player.getY() < GROUND - 3 && inWindow(player.blockPosition())) {
            wake(player);
            return;
        }
        if (!c.flag("dream_ripe") && now > c.flags.get("dreaming") - DREAM + 60) {
            c.flags.put("dream_ripe", now);
            ripen(realm);
            tidy(realm);
            gods(realm);
            sidoh(realm);
            labels(realm);
            RealmDecor.ornaments(realm);
        }
        if (player.blockPosition().closerThan(KING, 26) && !c.flag("met_king")) {
            c.flags.put("met_king", now);
            player.sendSystemMessage(Component.translatable("aicompanion.realm.king").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
            realm.playSound(null, player.blockPosition(), Sounds.REALM_KING.get(), SoundSource.AMBIENT, 1.0F, 1.0F);
            SideQuests.realmDone(player, SideQuests.R_KING);
        }
        Films.gods(player, realm, Vec3.atCenterOf(KING));
        SideQuests.realmArrow(player);
    }

    private static boolean inWindow(BlockPos p) {
        for (int i = 0; i < WINDOWS.length; i++) {
            if (Math.pow(p.getX() - WINDOWS[i].getX(), 2) + Math.pow(p.getZ() - WINDOWS[i].getZ(), 2) < Math.pow(WINDOW_R[i] + 1, 2)) {
                return true;
            }
        }
        return false;
    }

    static void enter(ServerPlayer player) {
        ServerLevel realm = player.server.getLevel(KEY);
        if (realm == null) {
            return;
        }
        Kira.Case c = Kira.of(player);
        long now = player.server.overworld().getGameTime();
        BlockPos bed = player.getSleepingPos().orElse(player.blockPosition());
        c.flags.put("dreaming", now + DREAM);
        c.flags.put("dream_from", bed.asLong());
        c.flags.put("dream_mode", (long) player.gameMode.getGameModeForPlayer().getId());
        c.flags.remove("golden");
        c.flags.remove("met_king");
        c.flags.remove("dream_apple");
        c.flags.remove("dream_ripe");
        c.flags.remove("dream_sidoh");
        Kira.get(player.server).setDirty();
        if (!realm.getBlockState(BlockPos.ZERO.atY(GROUND)).is(Blocks.CRYING_OBSIDIAN)) {
            build(realm);
        } else if (!realm.getBlockState(VERSION).is(Blocks.REINFORCED_DEEPSLATE)) {
            upgrade(realm); // a realm built by an older version of the mod: the new sights, the old figures out
        }
        if (!RealmDecor.built(realm)) {
            RealmDecor.build(realm); // the details: a new realm, or one from before they were there
        }
        player.stopSleepInBed(false, false);
        player.setGameMode(GameType.ADVENTURE); // a dreamer touches nothing
        player.teleportTo(realm, 0.5, GROUND + 1, 4.5, 0, -8); // facing the road of ribs, and the King at its end
        player.sendSystemMessage(Component.translatable("aicompanion.realm.enter").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        Network.cue(player, "sting.realm_in");
        if (!c.flag("film_realm")) { // the first dream: out of the dark, the realm opens up under its name
            c.flags.put("film_realm", now);
            Films.showing(player, 200);
            Network.cue(player, "cine.realm");
        }
        SideQuests.realmEntered(player);
        CompanionEntity ryuk = CompanionEntity.of(player);
        if (ryuk != null) {
            ryuk.remark(Config.tr("твой человек уснул, и ты показываешь ему во сне свой мир, мир богов смерти: пепельная пустошь под умирающим солнцем, кости, цепи с неба, туман. Живым сюда нельзя — это только сон. Расскажи ему, где тут что: дыры в земле, через которые вы смотрите на людей далеко внизу; Гук и Деридовели играют в кости на яблоки; Армония на троне знает все правила; старик-Король висит на цепях, у его ног чаша для дани; на мёртвом дереве растут местные яблоки — гадость, на вкус как песок; а у большой дыры трясётся Сидо, высматривает свои листы",
                    "your human fell asleep, and you're showing them your world in a dream, the realm of the gods of death: an ashen waste under a dying sun, bones, chains hanging from the sky, fog. The living can't come here: it's only a dream. Tell them what's what: the holes in the ground you watch humans through, far below; Gook and Deridovely gambling bones for apples; Armonia on his throne knows every rule; the old man, the King, hangs in his chains with a bowl for tribute at his feet; the dead tree grows the realm's own apples, vile things that taste of sand; and by the big hole Sidoh shakes, looking down after his pages"), "story", 0);
        }
    }

    static void wake(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        if (!c.flag("dreaming")) {
            return;
        }
        BlockPos bed = BlockPos.of(c.flags.getOrDefault("dream_from", player.blockPosition().asLong()));
        GameType mode = GameType.byId(c.flags.getOrDefault("dream_mode", 0L).intValue());
        c.flags.remove("dreaming");
        c.flags.put("dreamt", player.server.overworld().getGameTime());
        Kira.get(player.server).setDirty();
        ServerLevel back = player.server.getLevel(player.getRespawnDimension());
        player.teleportTo(back != null ? back : player.server.overworld(), bed.getX() + 0.5, bed.getY() + 0.6, bed.getZ() + 0.5, player.getYRot(), 0);
        player.setGameMode(mode);
        if (player.server.getPlayerCount() == 1 && player.server.overworld().isNight()) {
            player.server.overworld().setDayTime(player.server.overworld().getDayTime() / 24000 * 24000 + 24000); // slept till morning
        }
        player.sendSystemMessage(Component.translatable("aicompanion.realm.wake").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        Network.cue(player, "sting.realm_out");
        Pact.thrill(player, Pact.THRILL_BIG);
        SideQuests.realmLeft(player);
    }

    /** Right click on a block in the realm: a window's lodestone (look for a human), the bowl (a bet), the dead tree, the King's offering bowl. */
    public static boolean use(ServerPlayer player, BlockPos pos, InteractionHand hand) {
        if (!dreaming(player) || player.level().dimension() != KEY) {
            return false;
        }
        BlockState state = player.level().getBlockState(pos);
        if (state.is(Blocks.LODESTONE)) {
            vision(player);
            return true;
        }
        if (state.is(Blocks.CAULDRON) && pos.closerThan(BOWL, 2)) {
            gamble(player, hand);
            return true;
        }
        if ((state.is(Blocks.CAULDRON) || state.is(Blocks.LAVA_CAULDRON)) && pos.closerThan(OFFERING, 2)) {
            SideQuests.tribute(player, hand);
            return true;
        }
        if ((state.is(Blocks.DARK_OAK_LOG) || state.is(Blocks.STRIPPED_DARK_OAK_LOG)) && pos.closerThan(TREE, 8)) {
            pickApple(player);
            return true;
        }
        return false;
    }

    /** One of the realm's gods, talked to: Armonia recites a rule, the gamblers grumble. */
    public static void talk(ServerPlayer player, RealmGod god) {
        String who = god.getCustomName() == null ? "?" : god.getCustomName().getString();
        god.act("talk", 50);
        say(player, who, line(player, god.who()));
        if (god.who().equals("armonia")) {
            SideQuests.realmDone(player, SideQuests.R_ARMONIA);
            SideQuests.armoniaTalked(player);
        }
    }

    /** The old armour-stand figures (a realm from before 0.2): they still answer, until the realm is rebuilt. */
    public static boolean talk(ServerPlayer player, ArmorStand figure) {
        if (!figure.getTags().contains("aicompanion_realm")) {
            return false;
        }
        String who = figure.getCustomName() == null ? "?" : figure.getCustomName().getString();
        say(player, who, line(player, figure.getTags().contains("armonia") ? "armonia" : "gook"));
        return true;
    }

    private static String line(ServerPlayer player, String who) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (who.equals("armonia")) {
            return SideQuests.armoniaLine(player, "aicompanion.realm.armonia" + (1 + r.nextInt(5)));
        }
        return "aicompanion.realm.gambler" + (1 + r.nextInt(4));
    }

    private static void say(ServerPlayer player, String who, String key) {
        player.sendSystemMessage(Component.literal("<" + who + "> ").withStyle(ChatFormatting.DARK_GRAY).append(Component.translatable(key)
                .withStyle(ChatFormatting.GRAY)));
    }

    /** An observation hole: think of a human you have seen, and you see where they are now. */
    private static void vision(ServerPlayer player) {
        List<UUID> faces = Lifespans.faces(player);
        player.serverLevel().playSound(null, player.blockPosition(), Sounds.REALM_VISION.get(), SoundSource.AMBIENT, 1.0F, 1.0F);
        java.util.Set<String> shown = new java.util.HashSet<>();
        for (UUID id : faces) {
            LivingEntity e = Lifespans.byUuid(player.server, id);
            if (e == null || !e.isAlive() || !Lifespans.human(e) || e == player || !shown.add(Lifespans.trueName(e))) {
                continue;
            }
            BlockPos from = BlockPos.of(Kira.of(player).flags.getOrDefault("dream_from", 0L));
            player.sendSystemMessage(Component.translatable("aicompanion.realm.vision", Names.shown(Lifespans.trueName(e)),
                    Scouting.where(from, e.blockPosition()), e.level().dimension().location().getPath()).withStyle(ChatFormatting.AQUA));
            if (shown.size() == 5) {
                break;
            }
        }
        if (shown.isEmpty()) {
            player.sendSystemMessage(Component.translatable("aicompanion.realm.vision_none").withStyle(ChatFormatting.GRAY));
        }
        SideQuests.realmDone(player, SideQuests.R_WINDOW);
    }

    /** The bone toss: an apple on the line, bones into the bowl. Double or nothing; the loser gets laughed at. */
    private static void gamble(ServerPlayer player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(Items.APPLE)) {
            player.sendSystemMessage(Component.translatable("aicompanion.realm.bet").withStyle(ChatFormatting.GRAY));
            return;
        }
        held.shrink(1);
        boolean win = ThreadLocalRandom.current().nextBoolean();
        ServerLevel level = player.serverLevel();
        level.playSound(null, BOWL, Sounds.DICE_BONES.get(), SoundSource.AMBIENT, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.ASH, BOWL.getX() + 0.5, BOWL.getY() + 1, BOWL.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.01);
        if (win) {
            player.getInventory().add(new ItemStack(Items.APPLE, 2));
        }
        for (RealmGod god : level.getEntitiesOfClass(RealmGod.class, new net.minecraft.world.phys.AABB(BOWL).inflate(6))) {
            god.act(win ? "talk" : "laugh", 50); // they grumble at a winner, and laugh at a loser
        }
        player.sendSystemMessage(Component.translatable(win ? "aicompanion.realm.win" : "aicompanion.realm.lose").withStyle(ChatFormatting.GOLD));
        Pact.thrill(player, Pact.THRILL_APPLE);
        SideQuests.realmDone(player, SideQuests.R_BONES);
    }

    /** The dead tree's fruit: one apple of the realm a dream, shrivelled, dark red, tasting of sand. */
    private static void pickApple(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        if (c.flag("dream_apple")) {
            player.displayClientMessage(Component.translatable("aicompanion.realm.apple_again").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        ServerLevel level = player.serverLevel();
        Entity fruit = level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(TREE).inflate(8), e -> e.getTags().contains(APPLE_TAG))
                .stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
        if (fruit != null) {
            level.sendParticles(ParticleTypes.ASH, fruit.getX(), fruit.getY(), fruit.getZ(), 10, 0.1, 0.1, 0.1, 0.01);
            fruit.discard();
        }
        c.flags.put("dream_apple", player.server.overworld().getGameTime());
        Kira.get(player.server).setDirty();
        SideQuests.give(player, new ItemStack(AiCompanion.REALM_APPLE.get()));
        level.playSound(null, TREE, Sounds.REALM_BRANCH.get(), SoundSource.AMBIENT, 1.0F, 1.0F);
        player.sendSystemMessage(Component.translatable("aicompanion.realm.apple").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        SideQuests.realmDone(player, SideQuests.R_APPLE);
    }

    /** The King takes the tribute: the sound of it, and the realm trembling. */
    static void kingRoars(ServerLevel level, ServerPlayer player) {
        level.playSound(null, player.blockPosition(), Sounds.REALM_KING.get(), SoundSource.AMBIENT, 1.0F, 0.9F);
        Network.cue(player, "sting.tribute");
        level.sendParticles(new DustParticleOptions(new Vector3f(0.9F, 0.15F, 0.1F), 2.0F), KING.getX() + 0.5, KING.getY(), KING.getZ() - 6.5,
                60, 3, 3, 1, 0.0);
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
    }

    static BlockPos offering() {
        return OFFERING;
    }

    /** Where the realm quest's sights are, for the arrow: window, bowl, throne, King, tree. */
    static BlockPos sight(int bit) {
        return switch (bit) {
            case 0 -> WINDOWS[1];
            case 1 -> BOWL;
            case 2 -> THRONE;
            case 3 -> KING.atY(GROUND);
            default -> TREE;
        };
    }

    // ------------------------------------------------------------------ the place

    private static void build(ServerLevel level) {
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState(), deep = Blocks.POLISHED_DEEPSLATE.defaultBlockState(),
                black = Blocks.POLISHED_BLACKSTONE.defaultBlockState(), chain = Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y);
        Random rnd = new Random(7);
        // the plaza where dreamers arrive, marked at its heart (first: it's what they land on)
        for (BlockPos p : BlockPos.betweenClosed(-7, GROUND, -7, 7, GROUND, 7)) {
            double d = Math.sqrt(p.getX() * p.getX() + p.getZ() * p.getZ());
            if (d <= 7.5) {
                Builds.later(level, p, d > 6.5 ? bone : deep);
            }
        }
        Builds.later(level, BlockPos.ZERO.atY(GROUND), Blocks.CRYING_OBSIDIAN.defaultBlockState());
        ground(level, rnd);
        posts(level);
        gamblers(level);
        throne(level);
        king(level);
        wastes(level, bone, black, chain);
        sights(level, rnd);
        Builds.later(level, VERSION, Blocks.REINFORCED_DEEPSLATE.defaultBlockState());
    }

    /** The plaza's posts round its edge, bone with a skull on top, off the axes: the way to the King and the hole stays open. */
    private static void posts(ServerLevel level) {
        for (int i = 0; i < 8; i++) {
            double a = (i + 0.5) * Math.PI / 4;
            BlockPos post = new BlockPos((int) Math.round(Math.sin(a) * 7), GROUND + 1, (int) Math.round(Math.cos(a) * 7));
            Builds.box(level, post, post.above(), Blocks.BONE_BLOCK.defaultBlockState());
            Builds.later(level, post.above(2), Blocks.SKELETON_SKULL.defaultBlockState());
        }
    }

    /** A realm from an older mod version: the gold orbs and the armour-stand figures go, the new sights come. */
    private static void upgrade(ServerLevel level) {
        for (BlockPos orb : OLD_ORBS) {
            for (BlockPos p : BlockPos.betweenClosed(orb.offset(-5, -5, -5), orb.offset(5, 5, 5))) {
                if (p.getY() >= GROUND && !level.getBlockState(p).isAir()) {
                    Builds.later(level, p, Blocks.AIR.defaultBlockState());
                }
            }
        }
        Builds.box(level, THRONE.offset(-3, -1, -3), THRONE.offset(3, 8, 3), Blocks.AIR.defaultBlockState()); // the old throne faced south
        for (int i = 0; i < 8; i++) { // the old posts stood on the axes: one right in the way of the view down the road to the King
            BlockPos post = new BlockPos((int) Math.round(Math.sin(i * Math.PI / 4) * 7), GROUND + 1, (int) Math.round(Math.cos(i * Math.PI / 4) * 7));
            Builds.box(level, post, post.above(2), Blocks.AIR.defaultBlockState());
        }
        posts(level);
        for (int i = 0; i < WINDOWS.length; i++) {
            int r = (int) Math.ceil(WINDOW_R[i]) + 2;
            Builds.box(level, WINDOWS[i].offset(-r, 0, -r), WINDOWS[i].offset(r, 14, r), Blocks.AIR.defaultBlockState());
        }
        Random rnd = new Random(7);
        ground(level, rnd);
        gamblers(level);
        throne(level);
        sights(level, rnd);
        Builds.later(level, VERSION, Blocks.REINFORCED_DEEPSLATE.defaultBlockState());
    }

    /**
     * The armour-stand figures of a realm built before 0.2: gone, once the dreamer is there (a realm's entities load
     * only with someone in it, so not when the realm is upgraded, just before the dreamer arrives; the gods are put in
     * their places then too, for the same reason).
     */
    private static void tidy(ServerLevel level) {
        if (level.getBlockState(OFFERING).is(Blocks.CAULDRON)) { // a bowl from before it glowed
            level.setBlock(OFFERING, Blocks.LAVA_CAULDRON.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, new net.minecraft.world.phys.AABB(-48, 0, -48, 48, 128, 48),
                s -> s.getTags().contains("aicompanion_realm"))) {
            stand.discard();
        }
    }

    /** Everything new in 0.2: the windows, the cracks, the skull and ribs, chains from the sky, steel, rocks, the tree, the offering bowl. */
    private static void sights(ServerLevel level, Random rnd) {
        for (int i = 0; i < WINDOWS.length; i++) {
            window(level, WINDOWS[i], WINDOW_R[i], rnd);
        }
        cracks(level, rnd);
        skull(level);
        ribs(level);
        skyChains(level, rnd);
        steel(level, rnd);
        floatingRocks(level, rnd);
        tree(level);
        offeringBowl(level);
        kingsEyes(level);
    }

    /** The ash fields: the flat surface broken into patches of grey, dark and pale, a shard of bone here and there. */
    private static void ground(ServerLevel level, Random rnd) {
        BlockState[] palette = {Blocks.GRAVEL.defaultBlockState(), Blocks.TUFF.defaultBlockState(), Blocks.SOUL_SOIL.defaultBlockState(),
                Blocks.SMOOTH_BASALT.defaultBlockState(), Blocks.COBBLED_DEEPSLATE.defaultBlockState()};
        for (int x = -96; x <= 96; x++) {
            for (int z = -96; z <= 96; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 96 || d < 8.5) {
                    continue;
                }
                double n = noise(x / 11.0, z / 11.0) * 0.7 + noise(x / 4.0 + 50, z / 4.0) * 0.3;
                int k = n < 0.30 ? 0 : n < 0.48 ? 1 : n < 0.60 ? 2 : n < 0.72 ? 3 : 4;
                BlockState s = (x * 31 + z * 17) % 53 == 0 ? Blocks.BONE_BLOCK.defaultBlockState() : palette[k];
                Builds.later(level, new BlockPos(x, GROUND - 1, z), s);
            }
        }
    }

    /** Smooth value noise in 0..1. */
    private static double noise(double x, double z) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double fx = x - x0, fz = z - z0;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash(x0, z0), b = hash(x0 + 1, z0), c = hash(x0, z0 + 1), d = hash(x0 + 1, z0 + 1);
        return a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz;
    }

    private static double hash(int x, int z) {
        long h = x * 374761393L + z * 668265263L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
    }

    /**
     * A window down to the human world: a crumbling lip of bone round a hole, and far below, in daylight, the world of
     * the living: fields in strips, a river, a little town by a road, a wood; clouds drifting over it all. A lodestone on
     * the lip, to look through for someone; jumping in wakes you up.
     */
    private static void window(ServerLevel level, BlockPos c, double r, Random rnd) {
        int bottom = GROUND - 24, reach = (int) Math.ceil(r) + 2;
        double turn = rnd.nextDouble() * Math.PI, sin = Math.sin(turn), cos = Math.cos(turn); // which way the river runs
        BlockState air = Blocks.AIR.defaultBlockState(), light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-reach, bottom - GROUND - 1, -reach), c.offset(reach, 0, reach))) {
            int dx = p.getX() - c.getX(), dz = p.getZ() - c.getZ(), y = p.getY() - bottom;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d >= r) {
                if (d < r + 1.2 && p.getY() >= GROUND - 1) {
                    Builds.later(level, p, rnd.nextInt(5) == 0 ? Blocks.TUFF.defaultBlockState() : Blocks.BONE_BLOCK.defaultBlockState());
                }
                continue;
            }
            double across = dx * cos + dz * sin, along = -dx * sin + dz * cos;
            boolean river = Math.abs(across - Math.sin(along * 0.7) * 1.2) < 0.85, road = !river && Math.abs(along - 1) < 0.5;
            boolean town = !river && across > 1.4 && across < 4.6 && Math.abs(along - 1) < 3.2, wood = !river && across < -1.8;
            int h = Math.floorMod(p.getX() * 73 + p.getZ() * 19, 7);
            BlockState s = air;
            if (y == -1) {
                s = river ? Blocks.BLUE_CONCRETE.defaultBlockState() : null; // the riverbed; elsewhere the rock stays
            } else if (y == 0) {
                s = river ? Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState() : road ? Blocks.DIRT_PATH.defaultBlockState()
                        : town ? Blocks.STONE.defaultBlockState() : wood ? Blocks.MOSS_BLOCK.defaultBlockState()
                        : Math.floorMod((int) Math.floor(along * 1.5), 3) == 0 ? Blocks.GREEN_TERRACOTTA.defaultBlockState() // fields in strips
                        : Math.floorMod((int) Math.floor(along * 1.5), 3) == 1 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.LIME_TERRACOTTA.defaultBlockState();
            } else if (y == 1 && town && !road && h < 3) {
                s = Blocks.WHITE_CONCRETE.defaultBlockState(); // a house
            } else if (y == 2 && town && !road && h < 3) {
                s = h == 0 ? Blocks.BRICKS.defaultBlockState() : Blocks.RED_TERRACOTTA.defaultBlockState(); // its roof
            } else if ((y == 1 || y == 2 && h < 4) && wood && h != 6) {
                s = Blocks.AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true); // a wood
            } else if (y == 3 && Math.floorMod(dx, 3) == 0 && Math.floorMod(dz, 3) == 0) {
                s = light; // daylight, down there
            } else if (y == 8 && noise(p.getX() / 2.6 + c.getX(), p.getZ() / 2.6) > 0.6) {
                s = Blocks.WHITE_STAINED_GLASS.defaultBlockState(); // a cloud drifting over it
            }
            if (s != null) {
                Builds.later(level, p, s);
            }
        }
        Builds.later(level, c.offset((int) Math.round(r) + 1, 0, 0), Blocks.LODESTONE.defaultBlockState());
        Builds.later(level, c.offset(-(int) Math.round(r) - 1, 0, 0), Blocks.SKELETON_SKULL.defaultBlockState());
    }

    /** Cracks in the waste, with a violet glow coming up out of them. */
    private static void cracks(ServerLevel level, Random rnd) {
        for (int i = 0; i < 6; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = 16 + rnd.nextDouble() * 50;
            double x = Math.sin(a) * d, z = Math.cos(a) * d, dir = rnd.nextDouble() * Math.PI * 2;
            for (int step = 0; step < 22 + rnd.nextInt(14); step++) {
                dir += (rnd.nextDouble() - 0.5) * 0.9;
                x += Math.cos(dir);
                z += Math.sin(dir);
                BlockPos p = new BlockPos((int) Math.floor(x), GROUND - 1, (int) Math.floor(z));
                if (near(p, 9)) {
                    break;
                }
                Builds.later(level, p, Blocks.AIR.defaultBlockState()); // one deep: whoever steps in can jump out
                Builds.later(level, p.below(), Blocks.CRYING_OBSIDIAN.defaultBlockState());
            }
        }
    }

    /** Too close to one of the realm's places to build anything else there. */
    private static boolean near(BlockPos p, double r) {
        BlockPos flat = p.atY(GROUND);
        for (BlockPos q : new BlockPos[]{BlockPos.ZERO.atY(GROUND), BOWL.atY(GROUND), THRONE.atY(GROUND), TREE, SKULL.atY(GROUND), OFFERING.atY(GROUND)}) {
            if (flat.closerThan(q, r)) {
                return true;
            }
        }
        for (int i = 0; i < WINDOWS.length; i++) {
            if (flat.closerThan(WINDOWS[i], r + WINDOW_R[i])) {
                return true;
            }
        }
        return Math.abs(p.getX()) < 9 && p.getZ() > 6 && p.getZ() < 50; // the road of ribs to the King
    }

    /** A giant's skull half sunk in the ash, its sockets toward the plaza, a cold blue light deep inside each. */
    private static void skull(ServerLevel level) {
        int r = 9;
        BlockPos c = SKULL;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -6, -r), c.offset(r, r, r))) {
            double dx = p.getX() - c.getX(), dy = (p.getY() - c.getY()) * 1.1, dz = p.getZ() - c.getZ();
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (p.getY() < GROUND) {
                continue;
            }
            if (d <= r && d > r - 1.3) {
                boolean socket = dx > 4 && Math.abs(dy - 1.5) < 2.6 && Math.abs(Math.abs(dz) - 3.6) < 2.4;
                boolean nose = dx > 6 && dy < -0.5 && dy > -3.5 && Math.abs(dz) < 1.2 - dy * 0.15;
                boolean crack = (p.getX() * 7 + p.getY() * 13 + p.getZ() * 5) % 23 == 0;
                Builds.later(level, p, socket || nose || crack ? Blocks.AIR.defaultBlockState() : Blocks.BONE_BLOCK.defaultBlockState());
            } else if (d <= r - 1.3) {
                Builds.later(level, p, Blocks.AIR.defaultBlockState());
            }
        }
        for (int s : new int[]{-1, 1}) {
            Builds.later(level, c.offset(5, 1, s * 4), Blocks.SOUL_LANTERN.defaultBlockState()); // the light in its sockets
            Builds.later(level, c.offset(4, 0, s * 4), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        }
        for (int z = -5; z <= 5; z += 2) { // a row of teeth along the sunken jaw
            Builds.box(level, c.offset(8, -5, z), c.offset(8, -3, z), Blocks.QUARTZ_PILLAR.defaultBlockState());
        }
    }

    /** The road to the King runs under ribs: a dead giant's, arching over it. */
    private static void ribs(ServerLevel level) {
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
        for (int z = 12; z <= 32; z += 4) {
            int h = 9 - Math.abs(z - 22) / 4; // taller in the middle of the ribcage
            for (int side : new int[]{-1, 1}) {
                for (int i = 0; i <= 14; i++) {
                    double k = i / 14.0;
                    int x = (int) Math.round(side * (6.4 - 4.5 * k * k));
                    int y = GROUND + (int) Math.round(h * Math.sin(k * Math.PI / 2));
                    Builds.later(level, new BlockPos(x, y, z), bone);
                }
            }
        }
        for (int z = 10; z <= 34; z++) { // the spine, sunk in the road
            Builds.later(level, new BlockPos(0, GROUND - 1, z), z % 3 == 0 ? Blocks.BONE_BLOCK.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState());
        }
    }

    /** Chains hanging out of the haze overhead: some reach the ground, some end in a blue lantern or a hook. */
    private static void skyChains(ServerLevel level, Random rnd) {
        BlockState chain = Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y);
        for (int i = 0; i < 30; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = 12 + rnd.nextDouble() * 75;
            BlockPos base = new BlockPos((int) (Math.sin(a) * d), GROUND, (int) (Math.cos(a) * d));
            if (near(base, 6)) {
                continue;
            }
            int end = i % 3 == 0 ? GROUND : GROUND + 8 + rnd.nextInt(40);
            for (int y = end; y < 127; y++) {
                Builds.later(level, base.atY(y), chain);
            }
            if (end > GROUND) {
                Builds.later(level, base.atY(end - 1), i % 2 == 0 ? Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true)
                        : Blocks.SKELETON_SKULL.defaultBlockState());
            }
        }
    }

    /** Rusted steel lying about, as if an old building had fallen here: beams, bars, a broken anvil. */
    private static void steel(ServerLevel level, Random rnd) {
        BlockState[] rust = {Blocks.WEATHERED_CUT_COPPER.defaultBlockState(), Blocks.OXIDIZED_CUT_COPPER.defaultBlockState(),
                Blocks.EXPOSED_CUT_COPPER.defaultBlockState()};
        for (int i = 0; i < 8; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = 22 + rnd.nextDouble() * 55;
            BlockPos base = new BlockPos((int) (Math.sin(a) * d), GROUND, (int) (Math.cos(a) * d));
            if (near(base, 8)) {
                continue;
            }
            Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(net.minecraft.util.RandomSource.create(rnd.nextLong()));
            int len = 6 + rnd.nextInt(5);
            for (int k = 0; k < len; k++) { // a beam, leaning on the ground
                Builds.later(level, base.relative(dir, k).above(k / 3), rust[rnd.nextInt(rust.length)]);
            }
            Builds.later(level, base.relative(dir.getClockWise(), 2), Blocks.IRON_BARS.defaultBlockState());
            Builds.later(level, base.relative(dir.getClockWise(), 2).above(), Blocks.IRON_BARS.defaultBlockState());
            if (i % 3 == 0) {
                Builds.later(level, base.relative(dir.getCounterClockWise(), 2), Blocks.DAMAGED_ANVIL.defaultBlockState().setValue(AnvilBlock.FACING, dir));
            }
        }
    }

    /** Rocks floating in the haze, chained down to the ground. */
    private static void floatingRocks(ServerLevel level, Random rnd) {
        BlockPos[] rocks = {new BlockPos(26, GROUND + 32, 40), new BlockPos(-36, GROUND + 40, -10), new BlockPos(12, GROUND + 46, -46)};
        BlockState chain = Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y);
        for (BlockPos c : rocks) {
            int r = 3 + rnd.nextInt(3);
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -r * 2, -r), c.offset(r, r / 2, r))) {
                double dx = p.getX() - c.getX(), dz = p.getZ() - c.getZ(), dy = p.getY() - c.getY();
                double w = r * (dy >= 0 ? 1 - dy / (r / 2.0 + 1) : 1 + dy / (r * 2.2)); // flat on top, a point underneath
                if (dx * dx + dz * dz <= w * w + rnd.nextDouble()) {
                    Builds.later(level, p, rnd.nextInt(4) == 0 ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.TUFF.defaultBlockState());
                }
            }
            for (int s : new int[]{-1, 1}) {
                for (int y = GROUND; y <= c.getY() - 2; y++) {
                    Builds.later(level, new BlockPos(c.getX() + s * (r - 1), y, c.getZ()), chain);
                }
            }
        }
    }

    /** The dead tree: a gnarled black trunk, bare branches, and the realm's apples hanging from them. */
    private static void tree(ServerLevel level) {
        BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState(), bark = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
        int[][] trunk = {{0, 0}, {0, 0}, {0, 0}, {1, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int y = 0; y < trunk.length; y++) {
            Builds.later(level, TREE.offset(trunk[y][0], y, trunk[y][1]), y % 3 == 1 ? bark : log);
        }
        for (Direction d : Direction.Plane.HORIZONTAL) { // roots
            Builds.later(level, TREE.relative(d), log.setValue(RotatedPillarBlock.AXIS, d.getAxis()));
        }
        int[][] branches = {{1, 0, 3}, {-1, 0, 3}, {0, 1, 3}, {0, -1, 2}, {1, 1, 2}}; // direction x, z, length
        BlockPos top = TREE.offset(0, trunk.length, 1);
        for (int[] b : branches) {
            BlockPos p = top.offset(0, -1, 0);
            for (int k = 1; k <= b[2]; k++) {
                p = p.offset(b[0], k % 2 == 0 ? 1 : 0, b[1]);
                Builds.later(level, p, log.setValue(RotatedPillarBlock.AXIS, b[0] != 0 ? Direction.Axis.X : Direction.Axis.Z));
            }
        }
    }

    /**
     * Apples on the dead tree again: one under each branch's end, a little display of the item. Only once the dreamer
     * is there, a few seconds in, when the realm's saved entities have loaded (else the old ones wouldn't be counted).
     */
    private static void ripen(ServerLevel level) {
        List<Entity> have = level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(TREE).inflate(8), e -> e.getTags().contains(APPLE_TAG));
        double[][] at = {{3.5, 6.65, 1.5}, {-2.5, 6.65, 1.5}, {0.5, 6.65, 4.5}, {0.5, 6.65, -0.5}, {2.5, 6.65, 3.5}, {-1.5, 6.65, 1.5}};
        for (double[] a : at) {
            Vec3 spot = new Vec3(TREE.getX() + a[0], TREE.getY() + a[1], TREE.getZ() + a[2]);
            if (have.stream().anyMatch(e -> e.position().distanceToSqr(spot) < 0.25)) {
                continue;
            }
            CompoundTag tag = new CompoundTag();
            tag.putString("id", "minecraft:item_display");
            tag.put("item", new ItemStack(AiCompanion.REALM_APPLE.get()).save(new CompoundTag()));
            CompoundTag tr = new CompoundTag();
            tr.put("left_rotation", floats(0, 0, 0, 1));
            tr.put("right_rotation", floats(0, 0, 0, 1));
            tr.put("translation", floats(0, 0, 0));
            tr.put("scale", floats(0.55F, 0.55F, 0.55F));
            tag.put("transformation", tr);
            tag.putString("billboard", "vertical");
            Entity apple = EntityType.loadEntityRecursive(tag, level, e -> {
                e.moveTo(spot.x, spot.y, spot.z, 0, 0);
                return e;
            });
            if (apple instanceof Display) {
                apple.addTag(APPLE_TAG);
                level.addFreshEntity(apple);
            }
        }
    }

    private static ListTag floats(float... v) {
        ListTag list = new ListTag();
        for (float f : v) {
            list.add(FloatTag.valueOf(f));
        }
        return list;
    }

    /** The bowl at the King's feet, where a dreamer may leave a tribute of apples (SideQuests.KING). */
    private static void offeringBowl(ServerLevel level) {
        Builds.box(level, OFFERING.below(), OFFERING.below(), Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState());
        Builds.later(level, OFFERING, Blocks.LAVA_CAULDRON.defaultBlockState()); // glowing red at his feet: seen from the plaza
        for (int[] d : new int[][]{{2, 2}, {2, -2}, {-2, 2}, {-2, -2}}) { // on the diagonals: the way to the bowl stays open
            Builds.later(level, OFFERING.offset(d[0], -1, d[1]), Blocks.BONE_BLOCK.defaultBlockState());
            Builds.later(level, OFFERING.offset(d[0], 0, d[1]), Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(CampfireBlock.SIGNAL_FIRE, false));
        }
    }

    /** The King's eyes, smouldering deep in their sockets. */
    private static void kingsEyes(ServerLevel level) {
        for (int x : new int[]{-2, -1, 1, 2}) {
            for (int y = 1; y <= 2; y++) {
                Builds.later(level, KING.offset(x, y, -7), Blocks.AIR.defaultBlockState());
                Builds.later(level, KING.offset(x, y, -6), Blocks.SHROOMLIGHT.defaultBlockState());
            }
        }
    }

    private static void gamblers(ServerLevel level) {
        Builds.later(level, BOWL.below(), Blocks.POLISHED_BLACKSTONE.defaultBlockState()); // a low table between them
        Builds.later(level, BOWL, Blocks.CAULDRON.defaultBlockState());
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Builds.later(level, BOWL.relative(d, 2).below(), Blocks.BONE_BLOCK.defaultBlockState());
        }
        Builds.later(level, BOWL.offset(0, 0, 2), Blocks.SKELETON_SKULL.defaultBlockState());
        Builds.later(level, BOWL.offset(0, 0, -2), Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.CANDLES, 3)
                .setValue(net.minecraft.world.level.block.CandleBlock.LIT, true));
    }

    /** Armonia's throne, facing the plaza: steps of blackstone, a seat, a back of bone and skulls set with amethyst. */
    private static void throne(ServerLevel level) {
        for (int i = 0; i < 3; i++) {
            Builds.box(level, THRONE.offset(-3 + i, -1 + i, -3 + i), THRONE.offset(3 - i, -1 + i, 3 - i), Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        }
        BlockPos seat = THRONE.above(2);
        Builds.later(level, seat, Blocks.GILDED_BLACKSTONE.defaultBlockState());
        for (int z = -2; z <= 2; z++) { // the back, on the east side: he faces west, the plaza
            for (int y = 0; y <= 4; y++) {
                if (Math.abs(z) == 2 || y >= 1) {
                    BlockState s = y == 4 || Math.abs(z) == 2 && y == 3 ? Blocks.SKELETON_SKULL.defaultBlockState()
                            : Math.floorMod(y + z, 3) == 0 ? Blocks.GILDED_BLACKSTONE.defaultBlockState() : Blocks.BONE_BLOCK.defaultBlockState();
                    Builds.later(level, seat.offset(1, y, z), s);
                }
            }
        }
        for (int z : new int[]{-1, 1}) {
            Builds.later(level, seat.offset(1, 5, z), Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP));
        }
        for (int z : new int[]{-3, 3}) { // braziers on the lowest step, either side
            Builds.later(level, THRONE.offset(-3, 0, z), Blocks.SOUL_CAMPFIRE.defaultBlockState());
        }
    }

    /** Sidoh at home by the big hole: there once (put in place, or the spare ones gone). */
    private static void sidoh(ServerLevel level) {
        List<SidohEntity> there = level.getEntitiesOfClass(SidohEntity.class, new net.minecraft.world.phys.AABB(-64, 0, -64, 64, 128, 64),
                SidohEntity::inRealm);
        SidohEntity sidoh = there.isEmpty() ? AiCompanion.SIDOH.get().create(level) : there.get(0);
        if (sidoh == null) {
            return;
        }
        there.stream().skip(1).forEach(Entity::discard);
        sidoh.realmHome(SIDOH_HOME);
        if (there.isEmpty()) {
            sidoh.moveTo(SIDOH_HOME.getX() + 0.5, SIDOH_HOME.getY() + 0.4, SIDOH_HOME.getZ() + 0.5, 135, 0);
            level.addFreshEntity(sidoh);
        }
    }

    /** Over everything a dreamer can use, what it is and what to do: the realm explains itself (once; they stay). */
    private static void labels(ServerLevel level) {
        if (!level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(-64, 0, -64, 64, 128, 64), e -> e.getTags().contains(LABEL_TAG)).isEmpty()) {
            return;
        }
        label(level, new Vec3(-21.5, 52.0, -25.5), "window");
        label(level, new Vec3(-15.5, 53.0, -3.5), "dice");
        label(level, new Vec3(12.4, 52.4, -5.5), "armonia");
        label(level, new Vec3(14.5, 52.6, 10.4), "tree");
        label(level, new Vec3(0.5, 53.3, 33.5), "tribute");
    }

    private static void label(ServerLevel level, Vec3 at, String what) {
        Component text = Component.translatable("aicompanion.realm.label." + what).withStyle(ChatFormatting.GOLD).append("\n")
                .append(Component.translatable("aicompanion.realm.label." + what + "_how").withStyle(ChatFormatting.GRAY));
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:text_display");
        tag.putString("text", Component.Serializer.toJson(text));
        tag.putString("billboard", "center");
        tag.putString("alignment", "center"); // read unconditionally in 1.20.1: missing, it logs an error
        tag.putInt("background", 0x9A000000);
        tag.putInt("line_width", 170);
        CompoundTag tr = new CompoundTag();
        tr.put("left_rotation", floats(0, 0, 0, 1));
        tr.put("right_rotation", floats(0, 0, 0, 1));
        tr.put("translation", floats(0, 0, 0));
        tr.put("scale", floats(0.7F, 0.7F, 0.7F));
        tag.put("transformation", tr);
        Entity label = EntityType.loadEntityRecursive(tag, level, e -> {
            e.moveTo(at.x, at.y, at.z, 0, 0);
            return e;
        });
        if (label != null) {
            label.addTag(LABEL_TAG);
            level.addFreshEntity(label);
        }
    }

    /** Gook and Deridovely at their bowl, Armonia on his throne: each there once (put in place, or the spare ones gone). */
    private static void gods(ServerLevel level) {
        god(level, 0, Vec3.atBottomCenterOf(BOWL.offset(-2, 0, 0)), -90, "Gook");
        god(level, 1, Vec3.atBottomCenterOf(BOWL.offset(2, 0, 0)), 90, "Deridovely");
        god(level, 2, Vec3.atBottomCenterOf(THRONE.above(3)), 90, "Armonia Justin Beyondormason");
    }

    private static void god(ServerLevel level, int who, Vec3 at, float yaw, String name) {
        List<RealmGod> there = level.getEntitiesOfClass(RealmGod.class, new net.minecraft.world.phys.AABB(-64, 0, -64, 64, 128, 64),
                g -> g.who().equals(RealmGod.WHO[who]));
        RealmGod god = there.isEmpty() ? AiCompanion.REALM_GOD.get().create(level) : there.get(0);
        if (god == null) {
            return;
        }
        there.stream().skip(1).forEach(Entity::discard);
        god.setup(who, at, yaw, Component.literal(name));
        if (there.isEmpty()) {
            level.addFreshEntity(god);
        }
    }

    /** The King: a huge ball hanging from chains, a skull within a skull for a face, four arms dangling with three claws. */
    private static void king(ServerLevel level) {
        int r = 6;
        for (BlockPos p : BlockPos.betweenClosed(KING.offset(-r, -r, -r), KING.offset(r, r, r))) {
            if (p.distSqr(KING) <= r * r) {
                Builds.later(level, p, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
            }
        }
        for (BlockPos p : BlockPos.betweenClosed(KING.offset(-4, -4, -r - 1), KING.offset(4, 4, -r))) {
            int x = p.getX() - KING.getX(), y = p.getY() - KING.getY();
            boolean socket = (Math.abs(x) == 2 || Math.abs(x) == 1) && y >= 1 && y <= 2 || x == 0 && y == 0;
            boolean teeth = y == -3 && Math.abs(x) <= 3 && x % 2 == 0;
            if (x * x + y * y <= 17) {
                Builds.later(level, p, socket ? Blocks.BLACK_CONCRETE.defaultBlockState() : teeth ? Blocks.QUARTZ_BLOCK.defaultBlockState()
                        : Blocks.BONE_BLOCK.defaultBlockState());
            }
        }
        for (int[] c : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) { // the chains he hangs from, up into the fog
            for (int y = 3; y < 127 - KING.getY(); y++) {
                Builds.later(level, KING.offset(c[0], y, c[1]), Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
            }
        }
        for (int[] a : new int[][]{{-4, -3}, {4, -3}, {-3, 3}, {3, 3}}) { // four arms, three claws each
            BlockPos shoulder = KING.offset(a[0], -r + 1, a[1]);
            Builds.box(level, shoulder, shoulder.below(8), Blocks.BONE_BLOCK.defaultBlockState());
            for (int[] claw : new int[][]{{-1, 0}, {1, 0}, {0, 1}}) {
                Builds.box(level, shoulder.offset(claw[0], -9, claw[1]), shoulder.offset(claw[0], -11, claw[1]), Blocks.BONE_BLOCK.defaultBlockState());
            }
        }
    }

    /** Bone spires, boulders and chains scattered over the waste around the plaza. */
    private static void wastes(ServerLevel level, BlockState bone, BlockState black, BlockState chain) {
        Random rnd = new Random(7);
        for (int i = 0; i < 70; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = 18 + rnd.nextDouble() * 60;
            BlockPos base = new BlockPos((int) (Math.sin(a) * d), GROUND + 1, (int) (Math.cos(a) * d));
            if (base.closerThan(KING.atY(base.getY()), 14) || near(base, 8)) {
                continue;
            }
            switch (i % 3) {
                case 0 -> { // a bone spire, leaning
                    int h = 4 + rnd.nextInt(8), lean = rnd.nextInt(3) - 1;
                    for (int y = 0; y < h; y++) {
                        Builds.later(level, base.offset(y / 3 * lean, y, 0), bone);
                    }
                    Builds.later(level, base.offset(h / 3 * lean, h, 0), Blocks.SKELETON_SKULL.defaultBlockState());
                }
                case 1 -> { // a boulder
                    int r = 1 + rnd.nextInt(3);
                    for (BlockPos p : BlockPos.betweenClosed(base.offset(-r, -1, -r), base.offset(r, r, r))) {
                        if (p.distSqr(base) <= r * r + rnd.nextInt(2)) {
                            Builds.later(level, p, rnd.nextInt(3) == 0 ? Blocks.TUFF.defaultBlockState() : black);
                        }
                    }
                }
                default -> { // a gallows of chains
                    Builds.box(level, base, base.above(5), black);
                    for (int y = 1; y <= 3; y++) {
                        Builds.later(level, base.offset(1, 5 - y, 0), chain);
                    }
                    Builds.later(level, base.offset(1, 5, 0), black);
                }
            }
        }
    }

    @Nullable
    static RealmGod armonia(ServerLevel level) {
        return level.getEntitiesOfClass(RealmGod.class, new net.minecraft.world.phys.AABB(THRONE).inflate(6), g -> g.who().equals("armonia"))
                .stream().findFirst().orElse(null);
    }
}
