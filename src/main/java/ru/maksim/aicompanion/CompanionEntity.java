package ru.maksim.aicompanion;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Ryuk: a shinigami floating after his owner. Eats apples, can't be hurt, talks through the LLM.
 * The server flies him (noPhysics) and keeps the state; clients only animate the synced EMOTION/TALKING/ACTION/LOUNGING.
 */
public class CompanionEntity extends PathfinderMob implements GeoEntity {
    /** Render scale of the generated model: the top of the head ends up at ~2.3 m, his height in the anime. */
    public static final float MODEL_SCALE = 0.88F;
    private static final EntityDataAccessor<String> EMOTION = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> TALKING = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> ACTION = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> LOUNGING = SynchedEntityData.defineId(CompanionEntity.class, EntityDataSerializers.BOOLEAN);

    /** Emotes on request (LLM action "emote"), in ticks: whole animation loops plus the blend in (make_ryuk.py lengths). */
    public static final Map<String, Integer> EMOTES = Map.ofEntries(Map.entry("handstand", 85), Map.entry("spin", 45),
            Map.entry("show_wings", 70), Map.entry("dance", 85), Map.entry("wave", 45), Map.entry("bow", 52), Map.entry("backflip", 30),
            Map.entry("clap", 53), Map.entry("shrug", 40), Map.entry("juggle", 101), Map.entry("stretch", 68), Map.entry("cackle", 53),
            Map.entry("sulk", 65), Map.entry("perch", 85), Map.entry("look_around", 64), Map.entry("peek", 65));
    public static final int EAT_TICKS = 46;
    private static final int[] BITE_TICKS = {17, 26, 34}; // make_ryuk.py BITES (0.6/1.05/1.45 s) + ~5 ticks of sync and blend
    private static final long HUNGRY_AFTER = 48000; // two Minecraft days (40 min) after the last apple
    private static final double TELEPORT_DISTANCE = 28;
    private static final double HAND_HEIGHT = 0.5, MOUTH_HEIGHT = 1.88, MOUTH_AHEAD = 0.48; // model units * MODEL_SCALE
    /** Comfort ring around the owner: inside it he stays put (no circling), outside it he moves to IDEAL. */
    private static final double NEAR = 2.2, IDEAL = 3.2, FAR = 6.0, FACE_DISTANCE = 3.4;

    private static final RawAnimation IDLE = loop("idle"), FLY = loop("fly"), THINK = loop("think"), TALK = loop("talk"),
            LAUGH = loop("laugh"), ANGRY = loop("angry"), SAD = loop("sad"), SURPRISED = loop("surprised"), LOUNGE = loop("lounge");
    private static final Map<String, RawAnimation> ACTIONS = new HashMap<>();
    private static final RawAnimation WINGS_IDLE = loop("wings_idle"), WINGS_FLY = loop("wings_fly"),
            WINGS_SPREAD = loop("wings_spread"), WINGS_TIGHT = loop("wings_tight"), WINGS_PERCH = loop("wings_perch");

    static {
        for (String a : List.of("eat", "withdrawal", "handstand", "spin", "show_wings", "dance", "write", "wave", "clap", "peek", "doze",
                "point", "rub_hands", "perch", "juggle", "look_around", "cackle", "sulk", "dice_shake")) {
            ACTIONS.put(a, loop(a));
        }
        for (String a : List.of("bow", "backflip", "stretch", "shrug", "dice_slam")) { // once, then the last frame holds till the action ends
            ACTIONS.put(a, RawAnimation.begin().thenPlayAndHold("animation.ryuk." + a));
        }
        ACTIONS.put("surprised", SURPRISED);
        ACTIONS.put("think", THINK);
    }

    private static final Map<UUID, Long> NOTE_FOUND = new HashMap<>(); // player -> game time they got the notebook

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    // server
    @Nullable
    private UUID owner;
    private boolean staying;
    @Nullable
    private Vec3 stayPos;
    @Nullable
    private Vec3 rest; // where he hovers while following
    private Vec3 ownerPrev = Vec3.ZERO, ownerVel = Vec3.ZERO;
    private float attentionYaw;
    private int talkTicks, emotionTicks, actionTicks, actionLength, attentionTicks, glanceTicks, ownerIdleTicks, scoutTicks, sweepTicks, leaveTicks;
    private long spentApple = -1; // the apple a favour was already paid with
    @Nullable
    private Entity glance;
    @Nullable
    private ItemEntity appleTarget;
    private ItemStack apple = ItemStack.EMPTY;
    private Vec3 ownerLastPos = Vec3.ZERO;
    private boolean thinking, frozen;
    @Nullable
    private Pending pending;
    private final List<String> held = new ArrayList<>(); // what happened while his human was reading (or he had a line going): one reply for it all, after
    @Nullable
    private Line awaiting; // a line sent, not yet said on his human's screen: his mouth, gestures and deeds wait for it
    @Nullable
    private String gesture; // a story gesture waiting for his human to finish reading a card
    private long gestureAt;
    private long awaitingSince;
    private int lineIds;
    private long nextRemark, nextFidget, nextRub, nextScan;
    private int ownerMissing; // seconds his human has been away (another world, far off, logged out)
    private boolean dozing; // he dozed off by himself (moods), so he wakes by himself too
    @Nullable
    private BlockPos pointAt; // show_way: where his claw points
    private final Map<String, Long> remarkCooldowns = new HashMap<>();
    // client
    private boolean flyAnim, wasTalking;
    private int talkStart, actionStart;
    private String lastAction = "";

    private record Pending(ServerPlayer player, String text) {
    }

    /** A line on its way to his human's screen (client/RyukLines): said only once it has the floor there. */
    private record Line(int id, String emotion, String message, List<Llm.Action> actions, UUID speaker) {
    }

    public CompanionEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setPersistenceRequired();
        blocksBuilding = false; // a ghost: blocks can be placed right through him
    }

    private static RawAnimation loop(String name) {
        return RawAnimation.begin().thenLoop("animation.ryuk." + name);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(EMOTION, "neutral");
        entityData.define(TALKING, false);
        entityData.define(ACTION, "");
        entityData.define(LOUNGING, false);
    }

    public String getEmotion() {
        return entityData.get(EMOTION);
    }

    public boolean isTalking() {
        return entityData.get(TALKING);
    }

    public String getAction() {
        return entityData.get(ACTION);
    }

    public boolean isEating() {
        return getAction().equals("eat");
    }

    /** Client: ticks since the current action started. */
    public int actionAge() {
        return tickCount - actionStart;
    }

    /** Client: ticks since he started the current line. */
    public int talkAge() {
        return tickCount - talkStart;
    }

    public static boolean isApple(ItemStack stack) {
        return stack.is(Items.APPLE) || stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE);
    }

    // ------------------------------------------------------------------ shinigami traits

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD) && !level().isClientSide) { // he flies: the void only means "fly back up"
            ServerPlayer player = owner();
            Vec3 to = player != null && player.level() == level() && player.getY() > level().getMinBuildHeight() ? besideOwner(player)
                    : position().with(net.minecraft.core.Direction.Axis.Y, level().getMinBuildHeight() + 80);
            teleportTo(to.x, to.y, to.z);
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) { // /kill
            return super.hurt(source, amount);
        }
        if (!level().isClientSide && source.getEntity() instanceof ServerPlayer player) {
            playSound(SoundEvents.WITCH_CELEBRATE, 0.5F, 0.5F);
            remark(name(player) + Config.tr(" ударил тебя. Оружие проходит сквозь синигами — тебе смешно.",
                    " hit you. Weapons pass right through a shinigami, and you find it funny."), "hit", 600);
        }
        return false; // swords and arrows pass through a god of death
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level && owner != null) {
            RyukMinds minds = RyukMinds.get(level.getServer());
            RyukMinds.Mind mind = minds.get(owner);
            if (mind != null && getUUID().equals(mind.body)) {
                minds.forget(owner); // killed on purpose: don't respawn him at the owner
            }
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** The crosshair passes through him unless you hold an apple for him: no stray hits, no blocked building. */
    @Override
    public boolean isPickable() {
        return !level().isClientSide || ru.maksim.aicompanion.client.ClientHooks.holdsApple();
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    /** Lit by the air around his chest, not by the ceiling his head pokes into. */
    @Override
    public Vec3 getLightProbePosition(float partialTick) {
        return getPosition(partialTick).add(0, 1.2, 0);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        if (!hasCustomName()) {
            setCustomName(Component.literal(Config.name()));
        }
        stayPos = position();
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }

    // ------------------------------------------------------------------ owner, mind, body

    @Nullable
    private ServerPlayer owner() {
        MinecraftServer server = level().getServer();
        return owner == null || server == null ? null : server.getPlayerList().getPlayer(owner);
    }

    @Nullable
    private RyukMinds.Mind mind() {
        if (owner == null || !(level() instanceof ServerLevel level)) {
            return null;
        }
        RyukMinds minds = RyukMinds.get(level.getServer());
        RyukMinds.Mind mind = minds.get(owner);
        return mind != null ? mind : minds.claim(owner, getUUID(), level.getGameTime());
    }

    private void adopt(ServerPlayer player) {
        owner = player.getUUID();
        staying = false;
        RyukMinds.get(player.server).claim(owner, getUUID(), level().getGameTime());
    }

    @Nullable
    public static CompanionEntity find(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof CompanionEntity companion) {
                return companion;
            }
        }
        return null;
    }

    /** A new body for the player's mind behind their shoulder; {@code fromAbove} drops him in from the sky. */
    @Nullable
    public static CompanionEntity summon(ServerPlayer player, boolean fromAbove) {
        CompanionEntity companion = AiCompanion.COMPANION.get().create(player.serverLevel());
        if (companion == null) {
            return null;
        }
        Vec3 at = besideOwner(player).add(0, fromAbove ? 8 : 0, 0);
        companion.moveTo(at.x, at.y, at.z, player.getYRot(), 0);
        companion.finalizeSpawn(player.serverLevel(), player.serverLevel().getCurrentDifficultyAt(companion.blockPosition()),
                MobSpawnType.EVENT, null, null);
        companion.adopt(player);
        player.serverLevel().addFreshEntity(companion);
        companion.poof();
        return companion;
    }

    /** The player's shinigami, if his body is loaded somewhere. */
    @Nullable
    public static CompanionEntity of(ServerPlayer player) {
        RyukMinds.Mind mind = RyukMinds.get(player.server).get(player.getUUID());
        return mind == null ? null : find(player.server, mind.body);
    }

    /** Carrying his notebook: a player without a shinigami gets one, dropping in from the sky 8 s later. */
    public static void noticeNote(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        if (c.flag("renounced")) { // a notebook touched again: it all comes back, and so does he
            c.flags.remove("renounced");
            Kira.changed(player);
            player.sendSystemMessage(Component.translatable("aicompanion.renounce.remember").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            CompanionEntity ryuk = summon(player, true);
            if (ryuk != null) {
                ryuk.remark(Config.tr("твой человек снова коснулся тетради, и память вернулась: тетрадь, ты, все имена. Ты снова с ним",
                        "your human touched a notebook again and it all came back: the notebook, you, every name. You're back with them"), "story", 0);
            }
            return;
        }
        if (RyukMinds.get(player.server).get(player.getUUID()) != null || Arrival.running(player)) {
            NOTE_FOUND.remove(player.getUUID());
            return;
        }
        long now = player.serverLevel().getGameTime();
        long since = NOTE_FOUND.computeIfAbsent(player.getUUID(), k -> now);
        if (now - since >= 160 || now < since) {
            NOTE_FOUND.remove(player.getUUID());
            Arrival.start(player); // the first time: a little film, he comes at its height
        }
    }

    /** The arrival film's hold: he stays put at {@code at}; null lets him go back to following. */
    void hold(@Nullable Vec3 at) {
        staying = at != null;
        stayPos = at;
        rest = null;
        frozen = false;
    }

    /** A gesture the story itself plays out right now (the arrival's wings), whatever else is on screen. */
    void act(String name, int ticks) {
        playAction(name, ticks);
    }

    void attendTo(ServerPlayer player, int ticks) {
        attend(player, ticks);
    }

    /** For the autotest's log: what holds a story gesture back. */
    public String gestureState() {
        ServerPlayer player = owner();
        return "gesture=" + gesture + " busy=" + (player != null && Network.floorBusy(player)) + " held=" + held.size() + " words=" + (pending != null)
                + " thinking=" + thinking + " awaiting=" + (awaiting != null) + " action=" + getAction() + "/" + actionTicks
                + " apple=" + (appleTarget != null) + " scout=" + scoutTicks + " sweep=" + sweepTicks + " owner=" + (player != null);
    }

    /** Catches up with the owner after a portal, an ender pearl or /tp. Also called from the owner's tick. */
    public void keepUp(ServerPlayer player) {
        boolean otherLevel = player.level() != level();
        double far = staying ? 64 : TELEPORT_DISTANCE;
        if (!otherLevel && distanceToSqr(player) < far * far) {
            return;
        }
        staying = false; // got bored waiting
        rest = null;
        Vec3 to = besideOwner(player);
        poof();
        if (otherLevel) {
            UUID id = getUUID();
            teleportTo(player.serverLevel(), to.x, to.y, to.z, Set.of(), player.getYRot(), 0); // the copy keeps the UUID
            if (player.serverLevel().getEntity(id) instanceof CompanionEntity copy) {
                copy.poof();
            }
        } else {
            teleportTo(to.x, to.y, to.z);
            setDeltaMovement(Vec3.ZERO);
            poof();
        }
    }

    private void poof() {
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1.2, getZ(), 24, 0.35, 0.7, 0.35, 0.01);
            level.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.2, getZ(), 6, 0.3, 0.6, 0.3, 0.02);
            level.playSound(null, getX(), getY(), getZ(), Sounds.RYUK_POOF.get(), getSoundSource(), 0.8F, 1.0F);
        }
    }

    // ------------------------------------------------------------------ ticking

    @Override
    public void tick() {
        noPhysics = true;
        setNoGravity(true);
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (talkTicks > 0 && --talkTicks == 0) {
            entityData.set(TALKING, false);
        }
        if (awaiting != null && level().getGameTime() - awaitingSince > 1200) {
            presented(awaiting.id(), null); // never heard of again (the screen is gone, or stuck): said all the same
        }
        if (tickCount % 10 == 0 && (!held.isEmpty() || pending != null)) {
            speakUp(); // his human has finished reading: what waited, in one go
        }
        if (gesture != null) {
            ServerPlayer player = owner();
            if (player == null || level().getGameTime() - gestureAt > 1200) {
                gesture = null; // the moment has passed
            } else if (!Network.floorBusy(player) && (actionTicks == 0 || getAction().equals("doze")) && appleTarget == null && scoutTicks == 0 && sweepTicks == 0) {
                playAction(gesture, EMOTES.getOrDefault(gesture, 65));
                gesture = null;
            }
        }
        if (emotionTicks > 0 && --emotionTicks == 0 && !thinking) {
            entityData.set(EMOTION, "neutral");
        }
        if (actionTicks > 0) {
            tickAction();
        }
        if (attentionTicks > 0) {
            attentionTicks--;
        }
        if (scoutTicks > 0 && --scoutTicks == 0) {
            finishScout();
        }
        if (sweepTicks > 0 && --sweepTicks == 0) {
            finishSweep();
        }
        if (leaveTicks > 0 && --leaveTicks == 0) { // his human gave up the notebook: off he goes
            ServerPlayer human = owner();
            if (human != null) {
                human.sendSystemMessage(Component.translatable("aicompanion.renounce.forget").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
            poof();
            discard();
            return;
        }
        if (tickCount % 20 == 0) {
            everySecond();
        }
    }

    private void clientTick() {
        double dx = getX() - xo, dz = getZ() - zo, speed = dx * dx + dz * dz;
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double forward = -dx * Mth.sin(yaw) + dz * Mth.cos(yaw);
        flyAnim = flyAnim ? speed > 0.003 : speed > 0.012 && forward > 0.05; // hysteresis: no flicker at the threshold
        if (isTalking() && !wasTalking) {
            talkStart = tickCount;
        }
        wasTalking = isTalking();
        if (!getAction().equals(lastAction)) {
            lastAction = getAction();
            actionStart = tickCount;
        }
    }

    /** Adoption, duplicate bodies, catching up, hunger and idle fidgets. */
    private void everySecond() {
        ServerLevel level = (ServerLevel) level();
        long now = level.getGameTime();
        if (owner == null) {
            if (level.getNearestPlayer(this, 24) instanceof ServerPlayer player) {
                adopt(player);
            }
            return;
        }
        RyukMinds minds = RyukMinds.get(level.getServer());
        RyukMinds.Mind mind = minds.get(owner);
        if (mind == null) {
            mind = minds.claim(owner, getUUID(), now); // a body from an old save
        } else if (!getUUID().equals(mind.body)) {
            poof(); // a newer body took over this mind
            discard();
            return;
        }
        String name = getCustomName() == null ? "" : getCustomName().getString();
        if ((name.equals("Рюк") || name.equals("Ryuk")) && !name.equals(Config.name())) {
            setCustomName(Component.literal(Config.name())); // the language was switched
        }
        ServerPlayer player = owner();
        if (player != null) {
            keepUp(player);
            if (isRemoved()) {
                return;
            }
            ownerIdleTicks = player.position().distanceToSqr(ownerLastPos) < 0.01 ? ownerIdleTicks + 20 : 0;
            ownerLastPos = player.position();
        }
        entityData.set(LOUNGING, staying || ownerIdleTicks > 600);
        if (player != null && player.level() == level()) {
            observe(player, level);
            moods(player, level, now);
        } else {
            ownerMissing++;
        }

        boolean free = actionTicks == 0 && !isTalking() && !thinking && appleTarget == null;
        long hungry = now - mind.lastApple - HUNGRY_AFTER;
        if (hungry > 0 && free && random.nextInt(240) == 0) { // whining, then twisting, then handstands: every few minutes, not more
            int minutes = (int) (hungry / 1200);
            if (minutes >= 5) {
                playAction(minutes >= 10 && random.nextBoolean() ? "handstand" : "withdrawal", 80);
            }
            remark(Config.tr("ты без яблок уже " + (minutes + HUNGRY_AFTER / 1200) + " мин, тебя ломает",
                    "no apples for " + (minutes + HUNGRY_AFTER / 1200) + " minutes now, the withdrawal is killing you"), "hunger", 12000);
        } else if (free && now >= nextFidget) {
            if (nextFidget > 0) {
                fidget(now - mind.lastApple < 12000);
            }
            nextFidget = now + 1200 + random.nextInt(1200);
        }
    }

    /** Something to do with himself now and then, so he's never a statue: juggling only when he's been fed lately. */
    private void fidget(boolean fed) {
        String[] pool = fed ? new String[]{"think", "show_wings", "stretch", "look_around", "juggle", "juggle", "backflip"}
                : new String[]{"think", "show_wings", "stretch", "look_around", "perch"};
        String pick = pool[random.nextInt(pool.length)];
        playAction(pick, EMOTES.getOrDefault(pick, 60));
    }

    /**
     * His body answering his human's, once a second: a wave when they come back after a while away; a gargoyle's squat
     * while they crouch; dozing off in the air at night while they stand still (or sleep), till they move; rubbing his
     * claws at an apple in their hand.
     */
    private void moods(ServerPlayer player, ServerLevel level, long now) {
        double d = distanceTo(player);
        String act = getAction();
        boolean busy = isTalking() || thinking || appleTarget != null || scoutTicks > 0 || sweepTicks > 0;
        if (d < 12 && ownerMissing >= 15 && actionTicks == 0 && !busy) {
            playAction("wave", EMOTES.get("wave"));
        }
        ownerMissing = d < 40 ? 0 : ownerMissing + 1;
        if (player.isCrouching() && d < 8 && !busy && (actionTicks == 0 || act.equals("perch"))) {
            playAction("perch", 45);
            return;
        }
        boolean drowsy = player.isSleeping() || level.isNight() && ownerIdleTicks >= 1200;
        if (drowsy && !busy && (actionTicks == 0 || act.equals("doze"))) {
            playAction("doze", 60);
            dozing = true;
            return;
        }
        if (dozing && (!drowsy || !act.equals("doze"))) {
            if (act.equals("doze")) {
                actionTicks = 1; // woken up
            }
            dozing = false;
        }
        if (actionTicks == 0 && !busy && d < 6 && now >= nextRub && (isApple(player.getMainHandItem()) || isApple(player.getOffhandItem()))) {
            playAction("rub_hands", 40);
            nextRub = now + 600;
        }
    }

    public boolean ownedBy(Player player) {
        return owner != null && owner.equals(player.getUUID());
    }

    /** At the dice table: hovering in front of his human's face, not wandering off. */
    public void seat(ServerPlayer player) {
        staying = false;
        attend(player, 60);
    }

    /** The dealer's hands (DiceGame): shake, slam, a win or a loss; "" puts the cup away. */
    public void deal(String action, int ticks) {
        if (action.isEmpty()) {
            if (getAction().startsWith("dice_")) {
                actionTicks = 1;
            }
            return;
        }
        playAction(action, ticks);
    }

    /** A gesture in answer to what his human just did (a peek at the page, a shrug, a sulk): now, if his claws are free. */
    public void react(String name) {
        if (actionTicks == 0 && appleTarget == null && scoutTicks == 0 && sweepTicks == 0 && !level().isClientSide) {
            playAction(name, EMOTES.getOrDefault(name, 65));
        }
    }

    /**
     * A gesture for a moment of the story that comes with a card (a slow clap at a death on the news, a cackle at L's):
     * it waits until his human has read the card (the latest one only), then wakes him if he dozed off.
     */
    public void reactLater(String name) {
        if (!level().isClientSide) {
            gesture = name;
            gestureAt = level().getGameTime();
        }
    }

    /**
     * Things he notices by himself and may comment on (each kind has its own long cooldown). Not the night or the
     * monsters about: his human sees those, and a line about them every few minutes was noise.
     */
    private void observe(ServerPlayer player, ServerLevel level) {
        int monsters = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(10), m -> m instanceof Enemy && m.isAlive()).size();
        if (monsters >= 3 && actionTicks == 0 && level.getGameTime() >= nextScan) {
            playAction("look_around", EMOTES.get("look_around")); // a claw over the eyes: how many, where from (without a word)
            nextScan = level.getGameTime() + 1200;
        }
        if (player.getHealth() <= 6 && player.isAlive()) {
            remark(Config.tr("твой человек " + health(player) + ", ещё чуть-чуть, и он умрёт",
                    "your human is " + health(player) + ", one more hit and they die"), "danger", 3600);
        }
        if (ownerIdleTicks == 3600) {
            remark(Config.tr("твой человек уже три минуты стоит и ничего не делает, тебе скучно",
                    "your human has stood doing nothing for three minutes, you are bored"), "bored", 12000);
            if (actionTicks == 0) {
                playAction("stretch", EMOTES.get("stretch")); // a long, bored stretch and a yawn
            }
        }
    }

    private void tickAction() {
        int elapsed = actionLength - actionTicks;
        if (isEating()) {
            for (int bite : BITE_TICKS) {
                if (elapsed == bite) {
                    crunch();
                }
            }
        }
        if (--actionTicks == 0) {
            entityData.set(ACTION, "");
        }
    }

    private void playAction(String name, int ticks) {
        entityData.set(ACTION, name);
        actionTicks = actionLength = ticks;
    }

    private void setEmotion(String emotion, int ticks) {
        entityData.set(EMOTION, emotion);
        emotionTicks = ticks;
    }

    // ------------------------------------------------------------------ flight

    @Override
    protected BodyRotationControl createBodyControl() {
        // the body is steered through yRot; the head may look up to 70 degrees aside (vanilla drags the body after the head)
        return new BodyRotationControl(this) {
            @Override
            public void clientTick() {
                yBodyRot = getYRot();
                yHeadRot = yBodyRot + Mth.clamp(Mth.wrapDegrees(yHeadRot - yBodyRot), -70.0F, 70.0F);
            }
        };
    }

    @Override
    public void travel(Vec3 input) {
        if (isControlledByLocalInstance()) {
            move(MoverType.SELF, getDeltaMovement()); // noPhysics: straight through blocks, no friction or gravity
        }
    }

    @Override
    protected void customServerAiStep() {
        ServerPlayer player = owner();
        if (stayPos == null) {
            stayPos = position();
        }
        seekApple(player);
        Vec3 diff = target(player).subtract(position());
        double dist = diff.length();
        Vec3 want = dist > 0.02 ? diff.scale(Math.min(1.4, dist * 0.18) / dist) : Vec3.ZERO;
        setDeltaMovement(getDeltaMovement().lerp(want, 0.25)); // a little inertia: floaty, slight overshoot
        steer(player);
    }

    /** The point {@code dist} blocks from the player in the direction of {@code yaw} (Minecraft yaw: 0 = south). */
    private static Vec3 toward(Player player, float yaw, double dist, double up) {
        float r = yaw * Mth.DEG_TO_RAD;
        return player.position().add(-Mth.sin(r) * dist, up, Mth.cos(r) * dist);
    }

    /** Behind the player's left shoulder at the usual distance: where he appears and where he catches up to. */
    private static Vec3 besideOwner(Player player) {
        return toward(player, player.yBodyRot - 135, IDEAL, 0.6);
    }

    private Vec3 target(@Nullable ServerPlayer player) {
        if (scoutTicks > 0 && player != null) {
            return player.position().add(0, 40, 0); // off on a look around, straight up through anything in the way
        }
        if (sweepTicks > 0 && player != null) { // round the walls nearby, through them, looking for L's cameras
            double a = sweepTicks * 0.05;
            return player.position().add(Math.cos(a) * 12, 3, Math.sin(a) * 12);
        }
        Vec3 hover = hoverTarget(player);
        return getAction().equals("handstand") ? floorUnder(hover) : hover;
    }

    // ------------------------------------------------------------------ favours, for apples (canon: he found L's cameras for them)

    /** Did his human just pay? An apple eaten in the last minute buys one favour, and it's spent on it. */
    private boolean paid() {
        RyukMinds.Mind mind = mind();
        if (mind == null || mind.lastApple == spentApple || level().getGameTime() - mind.lastApple >= 1200) {
            return false;
        }
        spentApple = mind.lastApple;
        return true;
    }

    private void unpaid() {
        remark(Config.tr("твой человек просит об услуге, но яблока ты за неё не получил: без яблока никаких услуг",
                "your human wants a favour, but you didn't get an apple for it: no apple, no favours"), "favor", 600);
    }

    /** Off through the walls around his human for five seconds, looking for L's cameras (see {@link #finishSweep}). */
    private void sweep(ServerPlayer player) {
        if (!paid()) {
            unpaid();
            return;
        }
        poof();
        staying = false;
        sweepTicks = 100;
        player.displayClientMessage(Component.translatable("aicompanion.errand.cameras").withStyle(ChatFormatting.GRAY), true);
    }

    /** Back from the walls: L's cameras and the people watching glow for half a minute, and he tells what he found. */
    private void finishSweep() {
        ServerPlayer player = owner();
        if (player == null) {
            return;
        }
        ServerLevel level = (ServerLevel) level();
        int cameras = Cameras.show(level, player.blockPosition(), 32, 600);
        List<HumanEntity> watchers = level.getEntitiesOfClass(HumanEntity.class, player.getBoundingBox().inflate(48),
                h -> h.role() != HumanEntity.Role.MAFIA && h.role() != HumanEntity.Role.BOSS);
        for (HumanEntity h : watchers) {
            h.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0, false, false));
        }
        Vec3 to = besideOwner(player);
        teleportTo(to.x, to.y, to.z);
        setDeltaMovement(Vec3.ZERO);
        poof();
        playAction("spin", EMOTES.get("spin"));
        player.displayClientMessage((cameras + watchers.size() == 0 ? Component.translatable("aicompanion.errand.cameras_none")
                : Component.translatable("aicompanion.errand.cameras_found", cameras, watchers.size())).withStyle(ChatFormatting.GOLD), true);
        remark(Config.tr("ты пролетел сквозь стены вокруг своего человека и нашёл камер L: " + cameras + ", и людей, что следят или работают на L: "
                        + watchers.size() + ". Они светятся сквозь стены полминуты",
                "you flew through the walls around your human and found " + cameras + " of L's cameras and " + watchers.size()
                        + " people watching or working for L. They glow through the walls for half a minute"), "story", 0);
    }

    /**
     * Asked the way: where the story is now, plainly, whatever his words were. A line with the coordinates (and which way
     * from here) in the chat, and the arrow on the screen until they get there. Free: a shinigami sees it all from above.
     */
    private void showWay(ServerPlayer player) {
        CaseFile.Goal goal = CaseFile.goal(player);
        if (goal == null) {
            player.sendSystemMessage(Component.translatable("aicompanion.way.none").withStyle(ChatFormatting.GRAY));
            return;
        }
        player.sendSystemMessage(Component.translatable("aicompanion.way.to").withStyle(ChatFormatting.GOLD)
                .append(CaseFile.at(player, goal.line(), goal.pos().asLong()).withStyle(ChatFormatting.YELLOW)));
        Network.card(player, Network.Broadcast.WAY, Component.translatable(goal.card()), Component.empty(), "", goal.pos());
        pointAt = goal.pos();
        playAction("point", 60); // "that way": he turns to the place and points (see steer)
    }

    /** Off over the roofs for a few seconds; on his return he tells what he saw (see {@link #finishScout}). */
    private void scout() {
        if (!paid()) {
            unpaid();
            return;
        }
        poof();
        staying = false;
        scoutTicks = 120;
        ServerPlayer player = owner();
        if (player != null) {
            player.displayClientMessage(Component.translatable("aicompanion.errand.scout").withStyle(ChatFormatting.GRAY), true);
        }
    }

    private void finishScout() {
        ServerPlayer player = owner();
        if (player == null) {
            return;
        }
        Vec3 to = besideOwner(player);
        teleportTo(to.x, to.y, to.z);
        setDeltaMovement(Vec3.ZERO);
        poof();
        player.displayClientMessage(Component.translatable("aicompanion.errand.scout_back").withStyle(ChatFormatting.GOLD), true);
        remark(Config.tr("ты слетал на разведку сквозь стены и вернулся. Что ты видел: ", "you flew off to look around through the walls and came back. What you saw: ")
                + Scouting.report(player), "story", 0);
    }

    /**
     * Ownership given up, as Light did in the Yotsuba gambit: the human forgets the notebook, him and all they did
     * with it, and loses the eyes (not the years they cost). He finishes his line and leaves; touching a notebook again
     * brings it all back ({@link #noticeNote}), six times at most. The notebook has to be put away first.
     */
    void renounce(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        long times = c.flags.getOrDefault("renounces", 0L);
        if (player.getInventory().hasAnyMatching(DeathNoteItem::writable)) {
            remark(Config.tr("твой человек хочет отказаться от тетради, но носит её при себе: пусть сначала спрячет её, иначе память тут же вернётся",
                    "your human wants to give up the notebook but still carries it: they must put it away first, or the memories come straight back"), "story", 0);
            return;
        }
        if (times >= 6) {
            remark(Config.tr("твой человек хочет снова отказаться от тетради, но шесть раз — предел: в седьмой раз память уже не вернётся, так что нет",
                    "your human wants to give up the notebook again, but six times is the limit: a seventh time the memories wouldn't come back, so no"), "story", 0);
            return;
        }
        c.flags.put("renounces", times + 1);
        c.flags.put("renounced", player.server.overworld().getGameTime());
        c.eyes = 0;
        Kira.changed(player);
        leaveTicks = talkTicks + 40; // the line that came with this action is his goodbye: he leaves once it's said
    }

    /** Handstands happen on the ground, not in mid-air. */
    private Vec3 floorUnder(Vec3 p) {
        BlockHitResult hit = level().clip(new ClipContext(p.add(0, 1, 0), p.add(0, -4, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS ? p : new Vec3(p.x, hit.getLocation().y, p.z);
    }

    private Vec3 hoverTarget(@Nullable ServerPlayer player) {
        if (appleTarget != null) {
            return appleTarget.position().add(0, -HAND_HEIGHT + 0.05, 0);
        }
        if (player == null || staying || player.level() != level()) {
            return stayPos;
        }
        Vec3 step = player.position().subtract(ownerPrev);
        ownerPrev = player.position();
        ownerVel = ownerVel.lerp(step.lengthSqr() > 4 ? Vec3.ZERO : step, 0.15); // ignore teleports
        boolean walking = ownerVel.horizontalDistance() > 0.06;
        if (attentionTicks > 0) { // talking: in front of the player's eyes, a few steps away
            if (rest == null || tickCount % 10 == 0) {
                rest = freeSpot(player, attentionYaw, FACE_DISTANCE, new float[]{-25, 25, 0, -50, 50});
            }
        } else {
            double d = Math.sqrt(distanceToSqr(player.getX(), getY(), player.getZ()));
            boolean uncomfortable = d < NEAR || d > FAR || Math.abs(getY() - player.getY()) > 3;
            // standing still and turning around doesn't move him: no circling around the player
            if (rest == null || (walking || uncomfortable) && (tickCount % 10 == 0 || d > FAR)) {
                float base = walking ? (float) (Mth.atan2(ownerVel.z, ownerVel.x) * Mth.RAD_TO_DEG) - 90.0F : player.yBodyRot;
                float r = base * Mth.DEG_TO_RAD;
                boolean onLeft = (getX() - player.getX()) * Mth.cos(r) + (getZ() - player.getZ()) * Mth.sin(r) >= 0;
                rest = freeSpot(player, base, IDEAL, onLeft ? new float[]{-135, -100, -160, 135, 100, 180}
                        : new float[]{135, 100, 160, -135, -100, 180}); // behind the walk, on the side he is already on
            }
        }
        double t = tickCount * 0.05;
        return rest.add(Math.sin(t * 0.9) * 0.15, Math.sin(t * 1.3) * 0.08, Math.cos(t * 0.7) * 0.15);
    }

    /** The first spot around the player where his body doesn't stick into blocks; lower under low ceilings. */
    private Vec3 freeSpot(ServerPlayer player, float base, double dist, float[] bearings) {
        for (float bearing : bearings) {
            for (double up : new double[]{0.6, 0.2, 0}) {
                Vec3 p = toward(player, base + bearing, dist, up);
                if (level().noCollision(this, getDimensions(getPose()).makeBoundingBox(p).deflate(0.05))) {
                    return p;
                }
            }
        }
        return toward(player, base + bearings[0], dist, 0); // a tunnel: float through the rock like a shinigami does
    }

    private void steer(@Nullable ServerPlayer player) {
        if (frozen) {
            yBodyRot = getYRot();
            return;
        }
        float yaw = getYRot();
        Vec3 v = getDeltaMovement();
        boolean together = player != null && player.level() == level();
        boolean social = together && (isTalking() || thinking || attentionTicks > 0);
        if (pointAt != null && getAction().equals("point")) {
            yaw = yawTo(Vec3.atCenterOf(pointAt));
            getLookControl().setLookAt(pointAt.getX() + 0.5, getEyeY(), pointAt.getZ() + 0.5);
        } else if (appleTarget != null) {
            yaw = yawTo(appleTarget.position());
            getLookControl().setLookAt(appleTarget.getX(), appleTarget.getY(), appleTarget.getZ());
        } else if (isEating()) {
            getLookControl().setLookAt(getX() - Mth.sin(yaw * Mth.DEG_TO_RAD) * 3, getEyeY() - 0.4, getZ() + Mth.cos(yaw * Mth.DEG_TO_RAD) * 3);
        } else if (social) {
            yaw = yawTo(player.position());
            getLookControl().setLookAt(player.getX(), player.getEyeY(), player.getZ());
        } else if (v.horizontalDistance() > 0.22) {
            yaw = (float) (Mth.atan2(v.z, v.x) * Mth.RAD_TO_DEG) - 90.0F; // flies head first
            lookAround(player);
        } else if (together) {
            yaw = yawTo(player.position()); // idle: faces his human, never shows them his back
            lookAround(player);
        } else {
            Player near = level().getNearestPlayer(this, 10);
            if (near != null) {
                yaw = yawTo(near.position());
                getLookControl().setLookAt(near.getX(), near.getEyeY(), near.getZ());
            }
        }
        setYRot(Mth.approachDegrees(getYRot(), yaw, 8.0F));
        yBodyRot = getYRot();
    }

    /** Mostly watches the owner, now and then glances at whatever moves nearby (monsters first). */
    private void lookAround(@Nullable ServerPlayer player) {
        if (--glanceTicks <= 0) {
            glanceTicks = 60 + random.nextInt(100);
            List<LivingEntity> near = level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(12),
                    e -> e != player && e != this && e.isAlive() && !e.isSpectator());
            glance = near.isEmpty() || random.nextInt(3) == 0 ? null
                    : near.stream().filter(e -> e instanceof Enemy).findAny().orElse(near.get(random.nextInt(near.size())));
        }
        Entity at = glance != null && glance.isAlive() && glanceTicks < 45 ? glance : player;
        if (at != null) {
            getLookControl().setLookAt(at.getX(), at.getEyeY(), at.getZ());
        }
    }

    private float yawTo(Vec3 p) {
        return (float) (Mth.atan2(p.z - getZ(), p.x - getX()) * Mth.RAD_TO_DEG) - 90.0F;
    }

    private void attend(ServerPlayer player, int ticks) {
        if (attentionTicks == 0) {
            attentionYaw = player.getYHeadRot(); // comes where the player is looking right now
            rest = null;
        }
        attentionTicks = Math.max(attentionTicks, ticks);
    }

    // ------------------------------------------------------------------ apples

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && stack.is(Items.APPLE) && player instanceof ServerPlayer gambler && ownedBy(gambler)) {
            DiceGame.open(gambler, this); // sneak + an apple: a game of dice instead of a snack
            return InteractionResult.CONSUME;
        }
        if (stack.is(AiCompanion.REALM_APPLE.get())) { // the stuff he left his world to get away from
            if (!level().isClientSide) {
                react("sulk");
                remark(name(player) + Config.tr(" протягивает тебе яблоко из твоего же мира. Сухое, как песок: ты из-за них и сбежал к людям. Откажись",
                        " holds out an apple from your own world. Dry as sand: they're why you ran off to the humans. Refuse it"), "realm_apple", 600);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!isApple(stack) || isEating()) {
            return super.mobInteract(player, hand);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (owner == null) {
                adopt(serverPlayer);
            }
            ItemStack food = stack.copyWithCount(1);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            eat(food, name(player) + Config.tr(" угостил тебя: ", " gave you this: ") + itemName(food)
                    + Config.tr(". Ты ешь его прямо сейчас.", ". You are eating it right now."));
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Picks up apples lying around (thrown to him or dropped by leaves) and eats them. */
    private void seekApple(@Nullable ServerPlayer player) {
        if (isEating()) {
            appleTarget = null;
            return;
        }
        if (appleTarget != null) {
            if (!appleTarget.isAlive() || appleTarget.distanceToSqr(this) > 20 * 20) {
                appleTarget = null;
            } else if (appleTarget.position().distanceToSqr(getX(), getY() + HAND_HEIGHT, getZ()) < 1.0) {
                ItemStack stack = appleTarget.getItem().copy();
                ItemStack food = stack.split(1);
                Entity thrower = appleTarget.getOwner();
                if (stack.isEmpty()) {
                    appleTarget.discard();
                } else {
                    appleTarget.setItem(stack);
                }
                appleTarget = null;
                playSound(SoundEvents.ITEM_PICKUP, 0.4F, 0.8F);
                eat(food, thrower instanceof Player p
                        ? name(p) + Config.tr(" бросил тебе ", " threw you this: ") + itemName(food)
                        + Config.tr(", ты его поймал и ешь.", ", you caught it and eat it.")
                        : Config.tr("ты нашёл на земле ", "you found this on the ground: ") + itemName(food)
                        + Config.tr(" и ешь его.", ", and you eat it."));
            }
            return;
        }
        if (tickCount % 10 != 0 || actionTicks > 0) {
            return;
        }
        Vec3 center = player != null && !staying && player.level() == level() ? player.position() : position();
        appleTarget = level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(8),
                        e -> isApple(e.getItem()) && e.getAge() > 8 && e.position().distanceToSqr(center) < 14 * 14
                                && !e.getTags().contains(DiceGame.WON)).stream() // not the apples his human just won off him
                .min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
    }

    /** Starts eating; false while he is still busy with the previous apple. */
    public boolean eat(ItemStack food, @Nullable String event) {
        if (isEating() || level().isClientSide) {
            return false;
        }
        apple = food.copyWithCount(1);
        playAction("eat", EAT_TICKS);
        setEmotion(food.is(Items.APPLE) ? "happy" : "surprised", EAT_TICKS + 60);
        RyukMinds.Mind mind = mind();
        if (mind != null) {
            RyukMinds.get(level().getServer()).ateApple(mind, level().getGameTime());
        }
        ServerPlayer feeder = owner();
        if (feeder != null) {
            Pact.thrill(feeder, Pact.THRILL_APPLE); // apples are the surest cure for his boredom
            if (food.is(Items.GOLDEN_APPLE) || food.is(Items.ENCHANTED_GOLDEN_APPLE)) {
                Kira.of(feeder).flags.put("golden", level().getGameTime()); // so generous: tonight he'll show his world in a dream
            }
        }
        if (event != null) {
            remark(event, "apple", 1200); // a whole stack fed in a row gets one remark a minute, not one per apple
        }
        return true;
    }

    private void crunch() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double x = getX() - Mth.sin(yaw) * MOUTH_AHEAD, y = getY() + MOUTH_HEIGHT, z = getZ() + Mth.cos(yaw) * MOUTH_AHEAD;
        level().playSound(null, x, y, z, SoundEvents.GENERIC_EAT, getSoundSource(), 0.9F, 0.8F + random.nextFloat() * 0.25F);
        ((ServerLevel) level()).sendParticles(new ItemParticleOption(ParticleTypes.ITEM, apple.isEmpty() ? new ItemStack(Items.APPLE) : apple),
                x, y, z, 5, 0.05, 0.05, 0.05, 0.03);
    }

    private void takeApple(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isApple(stack)) {
                if (eat(stack.copyWithCount(1), null) && !player.getAbilities().instabuild) { // not while still chewing
                    stack.shrink(1);
                }
                return;
            }
        }
    }

    private static int countApples(Player player) {
        Inventory inventory = player.getInventory();
        int n = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isApple(inventory.getItem(i))) {
                n += inventory.getItem(i).getCount();
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ talking

    /** Server thread: a player near this companion wrote in chat. */
    public void hear(ServerPlayer player, String text) {
        if (owner == null) {
            adopt(player);
        }
        if (player.getUUID().equals(owner)) {
            attend(player, 120);
        }
        pending = new Pending(player, text); // while they read, or he has a line going, it waits; only the latest is kept
        speakUp();
    }

    /**
     * Something worth a remark happened. Rate-limited: he shouldn't chatter or burn the API quota. The story's big moments
     * ({@link #URGENT} kinds) skip the limits and wait only for the reply he is thinking about right now.
     */
    public void remark(String event, String kind, int cooldown) {
        ServerPlayer player = owner();
        long now = level().getGameTime();
        boolean urgent = URGENT.contains(kind), reading = player != null && Network.floorBusy(player);
        if (player == null || Config.API_KEY.get().isBlank() || SILENT || Epilogue.running(player) || !urgent && now < remarkCooldowns.getOrDefault(kind, 0L)
                || !urgent && !reading && (thinking || awaiting != null || now < nextRemark)) {
            return;
        }
        remarkCooldowns.put(kind, now + cooldown);
        if (reading || thinking || awaiting != null) { // his human is reading (or he has a line going): it waits, with whatever else comes
            held.add(event);
            if (held.size() > 4) {
                held.remove(0);
            }
            return;
        }
        nextRemark = now + 300;
        ask(player, Llm.eventLine(Config.english(), event), true);
    }

    /**
     * What waited (things that happened while his human was reading, their words to him): all of it in one reply, once
     * he has no line going and they have nothing on the screen to read. Not a pile of lines one after another.
     */
    private void speakUp() {
        Pending words = pending;
        ServerPlayer speaker = words != null && !words.player().hasDisconnected() ? words.player() : owner();
        if (thinking || awaiting != null || speaker == null || held.isEmpty() && words == null || Network.floorBusy(speaker) || Epilogue.running(owner())) {
            return; // (in the epilogue he says his own last words)
        }
        pending = null;
        String happened = held.isEmpty() ? "" : held.size() == 1 ? held.get(0)
                : Config.tr("пока твой человек читал, случилось несколько вещей: ", "while your human was reading, a few things happened: ")
                + String.join("; ", held) + Config.tr(". Отзовись на это одной короткой репликой, о главном, не по пунктам",
                ". Answer it all in one short line, about what matters most, not point by point");
        held.clear();
        nextRemark = level().getGameTime() + 300;
        if (words == null) {
            ask(speaker, Llm.eventLine(Config.english(), happened), true);
        } else {
            ask(speaker, name(speaker) + ": " + words.text() + (happened.isEmpty() ? "" : " " + Llm.eventLine(Config.english(), happened)), false);
        }
    }

    private static final Set<String> URGENT = Set.of("final", "deal", "pact", "story", "favor"); // "favor": the answer to a request
    /** Filming (env AICOMPANION_OFFLINE=silent): he remarks on nothing by himself; only lines given with /companion say. */
    private static final boolean SILENT = "silent".equals(System.getenv("AICOMPANION_OFFLINE"));

    /** Ryuk's promise kept: he flies up in front of his human, takes out his own notebook and writes their name. */
    public void writeOwnersName(String event) {
        ServerPlayer player = owner();
        if (player == null) {
            return;
        }
        staying = false;
        attend(player, 600);
        playAction("write", 160);
        playSound(SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.5F);
        if (Config.API_KEY.get().isBlank()) {
            String line = Config.tr("Хе-хе… Было весело. Я ведь обещал, что впишу твоё имя.", "Heh heh... It was fun. I did promise I'd write your name.");
            deliver("happy", line, List.of(), player);
        } else {
            remark(event, "final", 0);
        }
    }

    private void ask(ServerPlayer speaker, String line, boolean event) {
        String apiKey = Config.API_KEY.get();
        if (apiKey.isBlank()) {
            if (!event) {
                speaker.sendSystemMessage(Component.translatable("aicompanion.msg.no_key").append(" ").append(CompanionCommand.setupLink()));
            }
            return;
        }
        RyukMinds.Mind mind = mind();
        if (mind == null) {
            return;
        }
        RyukMinds.get(speaker.server).speakIn(mind, Config.english() ? "en" : "ru");
        List<Llm.Msg> messages = new ArrayList<>();
        messages.add(new Llm.Msg("system", Llm.systemPrompt(getName().getString(), Config.EXTRA_PERSONA.get(), context(speaker, mind),
                Config.english(), Llm.strong(Config.API_URL.get()))));
        messages.addAll(mind.history);
        Llm.Msg question = new Llm.Msg("user", line);
        messages.add(question);

        String url = Config.API_URL.get();
        List<String> models = Config.models();
        MinecraftServer server = speaker.server;
        UUID self = getUUID();
        String before = getEmotion();
        thinking = true;
        setEmotion("thinking", 0);
        boolean english = Config.english();
        CompletableFuture.supplyAsync(() -> Llm.completeInCharacter(url, apiKey, models, messages, english), Llm.EXECUTOR)
                .whenComplete((content, error) -> server.execute(() -> {
                    thinking = false;
                    CompanionEntity body = isRemoved() ? find(server, self) : this; // may have changed dimension meanwhile
                    if (body != null) {
                        body.answer(speaker, mind, question, content, error, event, before);
                    }
                }));
    }

    private void answer(ServerPlayer speaker, RyukMinds.Mind mind, Llm.Msg question, @Nullable String content,
                        @Nullable Throwable error, boolean event, String before) {
        thinking = false;
        if (error != null) {
            setEmotion(before.equals("thinking") ? "neutral" : before, 40);
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            AiCompanion.LOGGER.warn("LLM request failed", cause);
            if (!event) { // what went wrong in plain words, and the way to fix it
                speaker.sendSystemMessage(Component.translatable(Llm.explain(cause), String.valueOf(cause.getMessage())).append(" ")
                        .append(CompanionCommand.setupLink()));
            }
        } else {
            Llm.Reply raw = Llm.parseReply(content);
            String said = Llm.cleanMessage(raw.message());
            if (Llm.refusal(said)) { // a fallback model breaking character: he just shrugs it off
                AiCompanion.LOGGER.info("Ryuk's model refused; shrugged instead: {}", said);
                String[] shrugs = Config.english() ? new String[]{"Heh... not my business.", "Hm? Figure it out yourself.", "Boring question. Next."}
                        : new String[]{"Хе-хе… не моё дело.", "Хм? Сам разбирайся.", "Скучный вопрос. Дальше."};
                said = shrugs[random.nextInt(shrugs.length)];
                raw = new Llm.Reply("neutral", said, List.of());
            }
            Llm.Reply reply = new Llm.Reply(raw.emotion(), said, raw.actions());
            RyukMinds.get(speaker.server).remember(mind, question, new Llm.Msg("assistant", reply.toJson()));
            String note = Llm.note(content);
            if (!note.isEmpty() && speaker.getUUID().equals(owner)) { // only his own human goes into his memory, not what others claim
                RyukMinds.get(speaker.server).note(mind, note);
                AiCompanion.LOGGER.info("Ryuk remembers: {}", note);
            }
            List<Llm.Action> acts = new ArrayList<>();
            for (Llm.Action action : reply.actions()) {
                if (!event || EMOTES.containsKey(action.name()) || action.name().equals("emote")) {
                    acts.add(action); // a remark may come with a gesture, never with orders nobody gave
                }
            }
            if (!event && reply.actions().stream().noneMatch(a -> a.name().equals("dice")) && Llm.wantsDice(question.content())) {
                acts.add(new Llm.Action("dice", Map.of())); // asked plainly; a model that only talked about it (some do) still deals
            }
            deliver(reply.emotion(), reply.message(), acts, speaker);
            return; // what came meanwhile waits until this line has been said
        }
        next();
    }

    /**
     * Sends a line to his human's screen. It's said there when it has the floor (no unread card, no show), and only
     * then (Network.Spoken -> {@link #presented}) do his mouth, his laugh, his gestures and what he does go.
     */
    private void deliver(String emotion, String message, List<Llm.Action> actions, ServerPlayer speaker) {
        Line line = new Line(++lineIds, emotion, message, List.copyOf(actions), speaker.getUUID());
        awaiting = line;
        awaitingSince = level().getGameTime();
        say(message, emotion, speaker, line.id());
        if (speaker.level() != level()) {
            presented(line.id(), null); // he isn't there to be heard: the chat has it now
        }
    }

    /** The line is being said on its human's screen ({@code by}; null: the timeout or no screen to wait for). */
    public void presented(int id, @Nullable ServerPlayer by) {
        Line line = awaiting;
        if (line == null || line.id() != id || by != null && !by.getUUID().equals(line.speaker())) {
            return;
        }
        awaiting = null;
        startTalking(line.emotion(), line.message());
        ServerPlayer speaker = getServer() == null ? null : getServer().getPlayerList().getPlayer(line.speaker());
        if (speaker != null) {
            for (Llm.Action a : line.actions()) {
                perform(a, speaker);
            }
        }
        next();
    }

    /** A line of his own, not the model's (the epilogue's last words): said like any other, once the screen is free. */
    void script(String emotion, String text) {
        ServerPlayer player = owner();
        if (player != null) {
            deliver(emotion, text, List.of(), player);
        }
    }

    /** Thinking up a line, waiting for it to be said, or saying it. */
    boolean saying() {
        return thinking || awaiting != null || talkTicks > 0;
    }

    /** After a line was said (or none came): what waited meanwhile, in one reply (now, or once the screen is free). */
    private void next() {
        speakUp();
    }

    private void startTalking(String emotion, String message) {
        talkTicks = Mth.clamp((int) (message.length() * 1.4F) + 20, 40, 260); // about reading speed
        entityData.set(TALKING, true);
        setEmotion(emotion, talkTicks + 100);
        if (emotion.equals("happy")) {
            playSound(SoundEvents.WITCH_CELEBRATE, 0.45F, 0.5F + random.nextFloat() * 0.1F); // a low, hoarse snicker
        }
    }

    private void say(String message, String emotion, ServerPlayer speaker, int id) {
        Component line = Component.literal("<" + getName().getString() + "> ").withStyle(ChatFormatting.DARK_RED)
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE));
        Network.Speak voice = new Network.Speak(getId(), id, message, emotion, line); // their client says it when it has the floor
        for (ServerPlayer p : ((ServerLevel) level()).players()) {
            if (p == speaker || p.distanceToSqr(this) < 48 * 48) {
                Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), voice);
            }
        }
        if (speaker.level() != level()) {
            speaker.sendSystemMessage(line);
        }
    }

    private void perform(Llm.Action action, ServerPlayer speaker) {
        String emote = EMOTES.containsKey(action.name()) ? action.name() : action.arg("name"); // also takes {"name":"spin"}
        switch (action.name()) {
            case "follow" -> {
                staying = false;
                rest = null;
            }
            case "stay" -> {
                staying = true;
                stayPos = position();
                attentionTicks = 0;
            }
            case "come" -> {
                staying = false;
                if (speaker.getUUID().equals(owner)) {
                    attend(speaker, 200);
                }
            }
            case "take_apple" -> takeApple(speaker);
            case "eye_deal" -> {
                if (speaker.getUUID().equals(owner)) {
                    Eyes.offer(speaker); // the human still has to say yes on the deal screen
                }
            }
            case "look_for_cameras" -> {
                if (speaker.getUUID().equals(owner)) {
                    sweep(speaker);
                }
            }
            case "renounce" -> {
                if (speaker.getUUID().equals(owner)) {
                    renounce(speaker);
                }
            }
            case "scout" -> {
                if (speaker.getUUID().equals(owner)) {
                    scout();
                }
            }
            case "show_way" -> showWay(speaker);
            case "dice" -> {
                if (!DiceGame.open(speaker, this)) {
                    remark(Config.tr("твой человек хочет сыграть в кости, но ставить ему нечего: у него нет яблок",
                            "your human wants a game of dice but has nothing to stake: no apples"), "favor", 600);
                }
            }
            default -> {
                if (EMOTES.containsKey(emote)) {
                    playAction(emote, EMOTES.get(emote));
                } else {
                    AiCompanion.LOGGER.debug("Unknown companion action {}", action);
                }
            }
        }
    }

    /** What he sees right now, for the system prompt. Short on purpose: it is sent with every request. */
    private String context(ServerPlayer speaker, RyukMinds.Mind mind) {
        ServerLevel level = (ServerLevel) level();
        ServerPlayer player = owner();
        StringBuilder sb = new StringBuilder();
        if (player != null) {
            ItemStack held = player.getMainHandItem();
            sb.append(Config.tr("Твой человек: ", "Your human: ")).append(name(player))
                    .append(", ").append(health(player)).append(", ").append(hunger(player))
                    .append(held.isEmpty() ? "" : Config.tr(", в руке ", ", holding ") + itemName(held)) // an empty hand isn't news: he kept saying so
                    .append(", ").append(notebook(player)).append(".\n");
        }
        if (speaker != player) {
            sb.append(Config.tr("С тобой говорит другой человек: ", "Another human talks to you: ")).append(name(speaker)).append(".\n");
        }
        long day = level.getDayTime() % 24000;
        String biome = level.getBiome(blockPosition()).unwrapKey().map(k -> k.location().getPath()).orElse("?");
        sb.append(Config.tr("Мир: ", "World: ")).append(dimension(level)).append(Config.tr(", биом ", ", biome ")).append(biome).append(", ")
                .append(String.format(Locale.ROOT, "%02d:%02d", (day / 1000 + 6) % 24, day % 1000 * 60 / 1000))
                .append(day >= 13000 && day < 23000 ? Config.tr(" (ночь)", " (night)") : Config.tr(" (день)", " (day)"))
                .append(level.isThundering() ? Config.tr(", гроза", ", thunderstorm") : level.isRaining() ? Config.tr(", дождь", ", rain") : "")
                .append(".\n");
        Map<String, Integer> near = new TreeMap<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(16),
                e -> e != this && e != player && e.isAlive() && !e.isSpectator())) {
            String name = e instanceof Player p ? Config.tr("игрок ", "player ") + name(p) : creatureName(e)
                    + (e.getCustomName() != null ? Config.tr(" по имени «", " named \"") + e.getCustomName().getString() + Config.tr("»", "\"") : "");
            near.merge(name, 1, Integer::sum);
        }
        sb.append(Config.tr("Рядом: ", "Around: "));
        if (near.isEmpty()) {
            sb.append(Config.tr("никого", "nobody"));
        }
        near.forEach((name, n) -> sb.append(name).append(n > 1 ? " ×" + n : "").append("; "));
        int ground = level.getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(16), e -> isApple(e.getItem())).stream()
                .mapToInt(e -> e.getItem().getCount()).sum();
        if (ground > 0) {
            sb.append(Config.tr("на земле лежат яблоки: ", "apples lying on the ground: ")).append(ground);
        }
        long since = level.getGameTime() - mind.lastApple;
        sb.append(".\n").append(Config.tr("Ты: ", "You: "))
                .append(staying ? Config.tr("ждёшь на месте, как попросили", "waiting in place, as asked")
                        : Config.tr("летаешь за своим человеком", "floating after your human")).append('.');
        if (since < 1200) {
            sb.append(Config.tr(" Ты только что съел яблоко.", " You just ate an apple."));
        } else if (since >= HUNGRY_AFTER) {
            int apples = player == null ? 0 : countApples(player);
            sb.append(Config.tr(" Тебя ломает без яблок", " Apple withdrawal is tearing you apart"))
                    .append(apples > 0 ? Config.tr(", а у твоего человека их " + apples, ", and your human has " + apples) : "").append('.');
        }
        if (player != null) {
            sb.append('\n').append(fate(player));
        }
        if (!mind.notes.isEmpty()) {
            sb.append('\n').append(Config.tr("Что ты помнишь о своём человеке с прошлых дней: ", "What you remember about your human from earlier days: "))
                    .append(String.join("; ", mind.notes)).append('.');
        }
        return sb.toString();
    }

    /** Where his notebook is: on them, fallen nearby, lost (a new one coming), put away. He stays with them in every case. */
    private static String notebook(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        if (Onboarding.holds(player)) {
            return Config.tr("тетрадь смерти у него", "carries the Death Note");
        }
        if (c.flag("note_again")) {
            return Config.tr("его тетрадь пропала или её забрали; скоро ты бросишь ему новую (ты никуда не улетаешь)",
                    "their notebook is lost or taken; soon you'll drop them a new one (you're not going anywhere)");
        }
        if (c.flag("note_lies")) {
            return Config.tr("тетрадь упала рядом, но он её ещё не поднял", "the notebook fell nearby, they haven't picked it up yet");
        }
        return Config.tr("тетради при нём сейчас нет (где-то спрятана)", "no notebook on them right now (put away somewhere)");
    }

    /** The story so far, as only a shinigami knows it: lifespan (never to be told), eyes, his game, the notebook's work. */
    private static String fate(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        long days = c.life / Kira.DAY;
        String life = days > 100 ? Config.tr("долгая", "long") : days > 50 ? Config.tr("средняя", "middling")
                : days > 20 ? Config.tr("уже недолгая", "getting short") : days > 5 ? Config.tr("короткая", "short")
                : Config.tr("совсем на исходе", "almost over");
        StringBuilder sb = new StringBuilder(Config.tr("Оставшаяся жизнь твоего человека (ты её видишь, но цифр не называешь никогда): ",
                "Your human's remaining life (you see it, but never say numbers): ")).append(life).append(". ");
        sb.append(c.eyes > 0 ? Config.tr("У него глаза синигами (сделок: " + c.eyes + "): он видит имена и сроки людей.",
                        "They have the shinigami eyes (deals made: " + c.eyes + "): they see names and lifespans.")
                : Config.tr("Глаз синигами у него нет; сделку (eye_deal) можно предложить, если он хочет видеть имена: цена — половина оставшейся жизни.",
                "They don't have the shinigami eyes; you may put the deal to them (eye_deal) if they want to see names: the price is half of their remaining life."));
        sb.append(' ').append(switch (c.pact) {
            case ACCEPTED -> Config.tr("Твоя игра идёт (он её принял): если его жизнь кончится, он впишет своё имя, L докажет, что он Кира, или тебе станет скучно, ты впишешь его имя навсегда. Скука: ",
                    "Your game is on (they accepted it): if their life runs out, they write their own name, L proves they're Kira or you get bored, you write their name for good. Boredom: ")
                    + c.boredom * 100 / Pact.BORED + "%.";
            case REFUSED -> Config.tr("От твоей игры он отказался: смерть для него не окончательна.", "They refused your game: death isn't final for them.");
            case NONE -> Config.tr("Твою игру ему ещё не предлагали: ты предложишь её, когда тетрадь впервые убьёт.",
                    "Your game hasn't been put to them yet: you'll put it once the notebook has killed.");
        });
        if (c.kills > 0) {
            sb.append(Config.tr(" Убито тетрадью: ", " Killed by the notebook: ")).append(c.kills)
                    .append(Config.tr(" (преступников " + c.criminals + ", невинных людей " + c.innocents + ").",
                            " (criminals " + c.criminals + ", innocent people " + c.innocents + ")."));
        }
        List<String> pending = DeathNotes.pending(player.server, player.getUUID());
        if (!pending.isEmpty()) {
            sb.append(Config.tr(" Ждут смерти: ", " Waiting to die: ")).append(String.join(", ", pending)).append('.');
        }
        List<News.Criminal> wanted = News.wanted(player.server);
        if (!wanted.isEmpty() && News.viewer(player)) { // no set, no faces
            sb.append(Config.tr(" По ТВ разыскивают (лица были в эфире): ", " Wanted on TV (their faces were on air): "))
                    .append(String.join(", ", wanted.stream().map(w -> Names.shown(w.name())).toList())).append('.');
        }
        String hunt = Investigation.describe(player) + Worship.describe(player);
        if (!hunt.isEmpty()) {
            sb.append('\n').append(hunt);
        }
        sb.append(Config.tr("\nСейчас (страница «Дела» в его тетради; места с координатами): ",
                        "\nRight now (the Cases page of their notebook; places with their coordinates): "))
                .append(String.join("; ", CaseFile.now(player).stream().map(Component::getString).toList())).append('.');
        List<Component> quests = SideQuests.lines(player);
        if (!quests.isEmpty()) { // the header first, then a line a quest
            sb.append(Config.tr("\nПобочные поручения: ", "\nSide quests: "))
                    .append(String.join("; ", quests.stream().skip(1).map(q -> q.getString().replaceFirst("^•\\s*", "")).toList())).append('.');
        }
        if (c.flag("sidoh_done")) {
            sb.append(Config.tr("\nСидо получил свою тетрадь назад и улетел домой.", "\nSidoh has his notebook back and has flown home."));
        } else if (c.flag("sidoh_touched")) {
            sb.append(Config.tr("\nТвой человек касался тетради Сидо и видит его", "\nYour human has touched Sidoh's notebook and sees him"))
                    .append(Mafia.holdsSidohsNote(player) ? Config.tr("; тетрадь у него, и Сидо умоляет её вернуть.", "; they have it now, and Sidoh begs for it back.") : ".");
        } else if (c.flag("sidoh") && c.flags.containsKey("hideout")) {
            sb.append(Config.tr("\nТетрадь Сидо у главаря мафии, в логове ", "\nSidoh's notebook is with the mafia boss, in a hideout "))
                    .append(Scouting.where(player.blockPosition(), BlockPos.of(c.flags.get("hideout"))))
                    .append(Config.tr(": у главаря глаза синигами, он читает имена по лицам; невидимый Сидо служит ему и срывает маски.",
                            ": the boss has the shinigami eyes and reads names off faces; Sidoh, invisible, serves him and pulls masks off."));
        }
        if (c.flag("fake_rules")) {
            sb.append(Config.tr("\nТвой человек знает, что два последних правила на обложке — твоя ложь.", "\nYour human knows the last two rules on the cover are your lies."));
        }
        if (Realm.dreaming(player)) {
            sb.append(Config.tr("\nТвой человек спит: ты показываешь ему во сне мир богов смерти.", "\nYour human is asleep: you're showing them the shinigami realm in a dream."));
        } else if (player.server.overworld().getGameTime() - c.flags.getOrDefault("golden", -99999L) < 6000) {
            sb.append(Config.tr("\nТы только что съел золотое яблоко: если твой человек уснёт этой ночью рядом с тобой, ты покажешь ему во сне свой мир.",
                    "\nYou just had a golden apple: if your human sleeps tonight with you near, you'll show them your realm in a dream."));
        } else if (c.flag("dreamt")) {
            sb.append(Config.tr("\nТвой человек уже видел твой мир во сне.", "\nYour human has seen your realm in a dream."));
        }
        return sb.toString();
    }

    /** In words, not points: he sees a human, not a health bar. */
    private static String health(Player player) {
        float f = player.getHealth() / player.getMaxHealth();
        return f > 0.8F ? Config.tr("цел и невредим", "unhurt") : f > 0.5F ? Config.tr("слегка потрёпан", "a bit battered")
                : f > 0.25F ? Config.tr("ранен", "wounded") : Config.tr("еле жив", "barely alive");
    }

    private static String hunger(Player player) {
        int food = player.getFoodData().getFoodLevel();
        return food >= 17 ? Config.tr("сыт", "well fed") : food >= 8 ? Config.tr("проголодался", "hungry") : Config.tr("умирает с голоду", "starving");
    }

    /** English names come from the registry ("iron_golem" -> "iron golem"): the integrated server speaks the client's language. */
    private static String creatureName(Entity e) {
        return Config.english() ? BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().replace('_', ' ')
                : e.getType().getDescription().getString();
    }

    static String itemName(ItemStack stack) {
        return Config.english() && !stack.hasCustomHoverName() ? BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().replace('_', ' ')
                : stack.getHoverName().getString();
    }

    private static String dimension(ServerLevel level) {
        if (level.dimension() == Level.OVERWORLD) {
            return Config.tr("Верхний мир", "the Overworld");
        }
        if (level.dimension() == Level.NETHER) {
            return Config.tr("Незер", "the Nether");
        }
        if (level.dimension() == Realm.KEY) {
            return Config.tr("мир богов смерти (это сон твоего человека)", "the shinigami realm (your human's dream)");
        }
        return level.dimension() == Level.END ? Config.tr("Энд", "the End") : level.dimension().location().toString();
    }

    private static String name(Player player) {
        return player.getGameProfile().getName();
    }

    // ------------------------------------------------------------------ /companion (tests, autotest)

    void debugEmotion(String emotion) {
        setEmotion(emotion, 200);
    }

    void debugAction(String action) {
        if (action.equals("eat")) {
            eat(new ItemStack(Items.APPLE), null);
        } else {
            playAction(action, EMOTES.getOrDefault(action, 80));
        }
    }

    void debugFavour(ServerPlayer player, String name) {
        perform(new Llm.Action(name, Map.of()), player);
    }

    void debugSay(ServerPlayer player, String emotion, String text) {
        deliver(emotion, text, List.of(), player);
    }

    void debugStay(boolean stay) {
        staying = stay;
        stayPos = position();
        rest = null;
        frozen = false;
    }

    /** Stays and stops turning: a statue for side and back screenshots. */
    void debugHold() {
        debugStay(true);
        frozen = true;
    }

    // ------------------------------------------------------------------ animation (client)

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 4, state -> state.setAndContinue(mainAnimation())));
        controllers.add(new AnimationController<>(this, "wings", 6, state -> state.setAndContinue(wingsAnimation())));
    }

    private RawAnimation mainAnimation() {
        RawAnimation action = ACTIONS.get(getAction());
        if (action != null) {
            return action;
        }
        String emotion = getEmotion();
        if (emotion.equals("thinking")) {
            return THINK;
        }
        if (isTalking()) {
            boolean opening = talkAge() < 30; // bursts out laughing / jumps, then talks
            return switch (emotion) {
                case "happy" -> opening ? LAUGH : TALK;
                case "angry" -> ANGRY;
                case "sad" -> SAD;
                case "surprised", "scared" -> opening ? SURPRISED : TALK;
                default -> TALK;
            };
        }
        if (flyAnim) {
            return FLY;
        }
        return entityData.get(LOUNGING) ? LOUNGE : IDLE;
    }

    private RawAnimation wingsAnimation() {
        String action = getAction();
        String emotion = getEmotion();
        if (action.equals("show_wings") || action.equals("bow") || action.equals("stretch") || action.equals("cackle")
                || isTalking() && (emotion.equals("angry") || emotion.equals("surprised"))) {
            return WINGS_SPREAD;
        }
        if (action.equals("perch")) {
            return WINGS_PERCH;
        }
        if (flyAnim || action.equals("spin") || action.equals("dance") || action.equals("backflip")) {
            return WINGS_FLY;
        }
        if (emotion.equals("sad") || emotion.equals("scared") || action.equals("withdrawal") || action.equals("doze") || action.equals("sulk")) {
            return WINGS_TIGHT;
        }
        return WINGS_IDLE;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }

    // ------------------------------------------------------------------ saving (the conversation lives in RyukMinds)

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putBoolean("Staying", staying);
        if (stayPos != null) {
            tag.putDouble("StayX", stayPos.x);
            tag.putDouble("StayY", stayPos.y);
            tag.putDouble("StayZ", stayPos.z);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        staying = tag.getBoolean("Staying");
        stayPos = tag.contains("StayX") ? new Vec3(tag.getDouble("StayX"), tag.getDouble("StayY"), tag.getDouble("StayZ")) : null;
    }
}
