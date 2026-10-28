package ru.maksim.aicompanion;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The story's people, drawn with the player model and their own skins: FBI agents tailing a suspect, the Task Force, L and Watari, the mafia and its boss, L's successors Near (with the SPK) and Mello. Each
 * has a true name (what the eyes see and the
 * notebook needs) and often an alias they go by: writing the alias does nothing, as with "Ryuzaki". Mortal like
 * anyone: a sword works, and so does the notebook.
 */
public class HumanEntity extends PathfinderMob implements NamedHuman {
    public enum Role {
        TAILOR, AGENT, DETECTIVE, L, WATARI, MAFIA, BOSS, NEAR, MELLO, SPK;

        /** On Kira's trail: seeing the notebook in use is proof to them. */
        boolean investigator() {
            return this == AGENT || this == DETECTIVE || this == L || this == WATARI || this == NEAR || this == SPK;
        }
    }

    private static final EntityDataAccessor<String> ROLE = SynchedEntityData.defineId(HumanEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(HumanEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STANCE = SynchedEntityData.defineId(HumanEntity.class, EntityDataSerializers.INT);

    /** How they hold themselves at a post (client.Poses draws each). */
    public enum Stance {
        STAND, CHAIR, DESK, PERCH, FLOOR, SOFA, BEHIND, CROSSED;

        /** Down on something: a chair, a sofa, the floor. */
        public boolean seated() {
            return this == CHAIR || this == DESK || this == PERCH || this == FLOOR || this == SOFA;
        }

        /** On the seat of a chair or sofa (a stair block), not on the floor. */
        boolean onSeat() {
            return seated() && this != FLOOR;
        }
    }

    /**
     * A place in their day: the block they take ({@code at}: the chair's stair, or the floor block's air above), which
     * way they face there, how, and for about how many seconds before they get up and go somewhere else.
     */
    public record Post(BlockPos at, Direction facing, Stance stance, int seconds) {
        /** Where the body goes: on a seat, a little ahead of the stair's middle (its back half is the backrest). */
        Vec3 spot() {
            double ahead = stance.onSeat() ? 0.22 : 0, y = at.getY() + (stance.onSeat() ? 0.5 : 0);
            return new Vec3(at.getX() + 0.5 + facing.getStepX() * ahead, y, at.getZ() + 0.5 + facing.getStepZ() * ahead);
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("At", at.asLong());
            t.putInt("Facing", facing.get2DDataValue());
            t.putString("Stance", stance.name());
            t.putInt("Seconds", seconds);
            return t;
        }

        static Post load(CompoundTag t) {
            Stance s;
            try {
                s = Stance.valueOf(t.getString("Stance"));
            } catch (IllegalArgumentException e) {
                s = Stance.STAND;
            }
            return new Post(BlockPos.of(t.getLong("At")), Direction.from2DDataValue(t.getInt("Facing")), s, Math.max(5, t.getInt("Seconds")));
        }
    }

    private String trueName = "";
    private long dies;
    private boolean note;
    @Nullable
    private UUID about; // the human this one is here for: the suspect tailed, the owner whose house is searched
    @Nullable
    private BlockPos home; // where they belong: the headquarters, the hideout
    private boolean revealed; // an agent who already showed his badge
    private long leaveAt; // game time to go (the arrest's detectives), 0 = stays
    private boolean searcher; // a detective sent to search the house by the bed (home)
    private final List<Post> posts = new ArrayList<>(); // their day: where they sit, stand and walk (Routine)
    private int post = -1; // the one they're at (or walking to)
    private boolean settled; // there, and in its stance
    private ItemStack stashed = ItemStack.EMPTY; // the weapon put away while sitting (cards or a drink in hand instead)

    public HumanEntity(EntityType<? extends HumanEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 3.0).add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ROLE, Role.DETECTIVE.name());
        entityData.define(VARIANT, 0);
        entityData.define(STANCE, 0);
    }

    /** Everyone is set up through here: who they are, what they're called, what they do. */
    public HumanEntity setup(Role role, int variant, String trueName, @Nullable String alias, long dies, @Nullable UUID about,
                             @Nullable BlockPos home) {
        entityData.set(ROLE, role.name());
        entityData.set(VARIANT, variant);
        this.trueName = trueName;
        this.dies = dies;
        this.about = about;
        this.home = home;
        this.note = role == Role.BOSS;
        setCustomName(alias == null ? null : Component.literal(alias));
        setCustomNameVisible(false); // you see the alias only when you look right at them
        if (role == Role.MAFIA) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(variant % 2 == 0 ? Items.IRON_SWORD : Items.STONE_AXE));
        }
        if (role == Role.BOSS) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AiCompanion.DEATH_NOTE.get()));
            setDropChance(EquipmentSlot.MAINHAND, 0); // the notebook comes out of the hideout another way (Mafia)
        }
        if (role == Role.MELLO) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AiCompanion.CHOCOLATE.get())); // never without a bar
        }
        if (role == Role.NEAR) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TOTEM_OF_UNDYING)); // a finger puppet
            setDropChance(EquipmentSlot.MAINHAND, 0);
        }
        goals();
        return this;
    }

    public Role role() {
        try {
            return Role.valueOf(entityData.get(ROLE));
        } catch (IllegalArgumentException e) {
            return Role.DETECTIVE;
        }
    }

    public int variant() {
        return entityData.get(VARIANT);
    }

    public Stance stance() {
        int i = entityData.get(STANCE);
        return i >= 0 && i < Stance.values().length ? Stance.values()[i] : Stance.STAND;
    }

    private void setStance(Stance stance) {
        entityData.set(STANCE, stance.ordinal());
        noPhysics = stance.seated(); // a seat holds them: no falling off it, no being shoved
        setNoGravity(stance.seated());
    }

    public boolean isSitting() {
        return stance().seated();
    }

    /** Their day, first post first; they go straight to it. */
    HumanEntity posts(List<Post> day) {
        posts.clear();
        posts.addAll(day);
        if (!posts.isEmpty()) {
            settle(0);
        }
        goals();
        return this;
    }

    /** At post {@code i}, in its stance, facing its way. */
    private void settle(int i) {
        post = i;
        settled = true;
        Post p = posts.get(i);
        Vec3 s = p.spot();
        getNavigation().stop();
        setDeltaMovement(Vec3.ZERO);
        float yaw = p.facing().toYRot();
        moveTo(s.x, s.y, s.z, yaw, 0);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
        setStance(p.stance());
        ItemStack prop = role() == Role.MAFIA && p.stance() == Stance.DESK ? new ItemStack(Items.PAPER) // the cards
                : role() == Role.MAFIA && p.stance() == Stance.CHAIR ? new ItemStack(Items.POTION) // a drink at the bar
                : role() == Role.L && p.stance() == Stance.PERCH ? new ItemStack(Items.SUGAR) : ItemStack.EMPTY;
        if (!prop.isEmpty()) {
            if (stashed.isEmpty()) {
                stashed = getMainHandItem().copy();
            }
            setItemSlot(EquipmentSlot.MAINHAND, prop);
            setDropChance(EquipmentSlot.MAINHAND, 0);
        }
    }

    private void standUp() {
        settled = false;
        setStance(Stance.STAND);
        if (!stashed.isEmpty() || role() == Role.L) { // the weapon back in hand (L's sugar back in his pocket)
            setItemSlot(EquipmentSlot.MAINHAND, stashed);
            stashed = ItemStack.EMPTY;
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STANCE.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
        return stance().seated() ? EntityDimensions.scalable(0.6F, stance() == Stance.FLOOR ? 1.3F : 1.4F) : super.getDimensions(pose);
    }

    /** At a post the body stays as it was put; only the head turns (and not all the way round). */
    @Override
    protected float tickHeadTurn(float yaw, float walked) {
        if (settled && stance() != Stance.STAND) {
            yBodyRot = getYRot();
            return walked;
        }
        return super.tickHeadTurn(yaw, walked);
    }

    @Override
    public int getMaxHeadYRot() {
        return stance().seated() ? 55 : super.getMaxHeadYRot();
    }

    @Override
    public boolean isPushable() {
        return !stance().seated() && super.isPushable();
    }

    @Nullable
    public UUID about() {
        return about;
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    @Override
    public String trueName() {
        return trueName;
    }

    @Override
    public long dies() {
        return dies;
    }

    @Override
    public boolean ownsNote() {
        return note;
    }

    /** Sent to search the house by the bed, his home (Investigation.Search). */
    HumanEntity searcher() {
        searcher = true;
        return this;
    }

    boolean isSearcher() {
        return searcher;
    }

    /** Here for a scene only: gone {@code ticks} from now. */
    HumanEntity leaveAfter(long ticks) {
        leaveAt = level().getGameTime() + ticks;
        return this;
    }

    /** Mello took a notebook for his hostage: now he owns one (the eyes stop showing his lifespan). */
    void takeNote(ItemStack notebook) {
        note = true;
        setItemSlot(EquipmentSlot.MAINHAND, notebook);
        setDropChance(EquipmentSlot.MAINHAND, 1);
    }

    /** The skin under textures/entity/people/: role and variant (agents and mafia repeat their few looks). */
    public String skin() {
        int looks = switch (role()) {
            case AGENT -> 2;
            case DETECTIVE, MAFIA, SPK -> 3;
            default -> 1;
        };
        return role().name().toLowerCase(Locale.ROOT) + "_" + Math.floorMod(variant(), looks);
    }

    // ------------------------------------------------------------------ behaviour

    private void goals() {
        goalSelector.removeAllGoals(g -> true);
        targetSelector.removeAllGoals(g -> true);
        goalSelector.addGoal(0, new FloatGoal(this));
        if (!posts.isEmpty()) {
            goalSelector.addGoal(3, new Routine(this));
        }
        switch (role()) {
            case TAILOR, L, WATARI, NEAR, MELLO, SPK -> {
                goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 16.0F, 1.0F));
                goalSelector.addGoal(6, new RandomLookAroundGoal(this));
            }
            case AGENT -> {
                goalSelector.addGoal(2, new Tail(this));
                goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 24.0F, 0.6F));
            }
            case DETECTIVE -> {
                goalSelector.addGoal(1, new OpenDoorGoal(this, true));
                goalSelector.addGoal(3, new Investigation.Search(this));
                goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
                if (home == null && posts.isEmpty()) { // the arrest's men mill about; everyone else has a post or an errand
                    goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.6));
                }
            }
            case MAFIA, BOSS -> {
                goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
                goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 16.0F));
                if (posts.isEmpty()) {
                    goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.5));
                }
                targetSelector.addGoal(1, new HurtByTargetGoal(this));
                if (role() == Role.MAFIA) { // the boss keeps to his desk, reading faces: he only hits back
                    targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::onTurf));
                }
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || tickCount % 20 != 0) {
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (!Successors.stillHere(this) || leaveAt > 0 && level.getGameTime() > leaveAt) {
            discard(); // moved on to the Yellow Box, or the scene is over
            return;
        }
        if (role() == Role.AGENT && !revealed) {
            showBadge(level);
        }
        if (getTarget() != null && !onTurf(getTarget())) { // out of the hideout: the chase is off, back to their posts (Routine)
            targetSelector.getRunningGoals().forEach(WrappedGoal::stop); // a running target goal would only set the target again
        }
        if (role() == Role.BOSS) {
            Mafia.bossTick(this);
        }
        Films.people(this); // L, Near, Mello: met for the first time
        if (role() == Role.TAILOR) {
            discard(); // a stage's Tailor from an older version: the special is on the screens only now
        }
    }

    /** The gang guards its hideout, not the world: whoever is within this many blocks of it (the hall, the bottom of the tunnel). */
    private static final int TURF = 24;

    private boolean onTurf(LivingEntity e) {
        return home == null || e.distanceToSqr(Vec3.atCenterOf(home)) < TURF * TURF;
    }

    /**
     * Raye Penber's mistake on the bus: with danger around, an agent shows his FBI badge to the civilian he's
     * guarding, and with it his real name.
     */
    private void showBadge(ServerLevel level) {
        ServerPlayer suspect = about == null ? null : level.getServer().getPlayerList().getPlayer(about);
        if (suspect == null || suspect.distanceTo(this) > 16 || level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(8),
                m -> m instanceof Enemy && m.isAlive() && !(m instanceof HumanEntity)).isEmpty()) {
            return;
        }
        revealed = true;
        suspect.sendSystemMessage(Component.translatable("aicompanion.agent.badge", trueName));
        CompanionEntity ryuk = CompanionEntity.of(suspect);
        if (ryuk != null) {
            ryuk.remark(Config.tr("агент ФБР, который следил за твоим человеком, испугался монстров и сам показал удостоверение: его настоящее имя "
                    + trueName, "the FBI agent tailing your human got scared of the monsters and showed his badge himself: his real name is "
                    + trueName), "story", 0);
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide && player instanceof ServerPlayer p) {
            if (role() == Role.MELLO && player.getItemInHand(hand).is(AiCompanion.DEATH_NOTE.get())) {
                Successors.giveToMello(p, this, hand);
            } else {
                Investigation.talkTo(p, this);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            if (role() == Role.BOSS) {
                Mafia.onBossDeath(level, this);
            }
            Investigation.onHumanDeath(level, this, source);
        }
    }

    /** Struck by a human: an investigator takes it as an assault (Investigation.assaulted); anyone dies all the same. */
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && isAlive() && source.getEntity() instanceof ServerPlayer p && role().investigator()) {
            Investigation.assaulted(p, this);
        }
        return hit;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Role", role().name());
        tag.putInt("Variant", variant());
        tag.putString("TrueName", trueName);
        tag.putLong("Dies", dies);
        tag.putBoolean("Note", note);
        tag.putBoolean("Revealed", revealed);
        tag.putLong("LeaveAt", leaveAt);
        tag.putBoolean("Searcher", searcher);
        tag.putString("Stance", stance().name());
        ListTag day = new ListTag();
        posts.forEach(p -> day.add(p.save()));
        tag.put("Posts", day);
        tag.putInt("Post", post);
        tag.putBoolean("Settled", settled);
        if (!stashed.isEmpty()) {
            tag.put("Stashed", stashed.save(new CompoundTag()));
        }
        if (about != null) {
            tag.putUUID("About", about);
        }
        if (home != null) {
            tag.put("Home", NbtUtils.writeBlockPos(home));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(ROLE, tag.getString("Role").isEmpty() ? Role.DETECTIVE.name() : tag.getString("Role"));
        entityData.set(VARIANT, tag.getInt("Variant"));
        posts.clear();
        for (Tag t : tag.getList("Posts", Tag.TAG_COMPOUND)) {
            posts.add(Post.load((CompoundTag) t));
        }
        post = tag.getInt("Post");
        settled = tag.getBoolean("Settled");
        stashed = tag.contains("Stashed") ? ItemStack.of(tag.getCompound("Stashed")) : ItemStack.EMPTY;
        Stance stance;
        try {
            stance = Stance.valueOf(tag.getString("Stance"));
        } catch (IllegalArgumentException e) { // saved before stances: L crouched, Near on the floor
            stance = !tag.getBoolean("Sitting") ? Stance.STAND : "NEAR".equals(tag.getString("Role")) ? Stance.FLOOR : Stance.PERCH;
            settled = stance != Stance.STAND;
            if (stance == Stance.PERCH) {
                setPos(getX(), Math.floor(getY()) + 0.5, getZ()); // he used to stand inside his chair's stair: up onto its seat
            }
        }
        setStance(stance);
        trueName = tag.getString("TrueName");
        dies = tag.getLong("Dies");
        note = tag.getBoolean("Note");
        revealed = tag.getBoolean("Revealed");
        leaveAt = tag.getLong("LeaveAt");
        searcher = tag.getBoolean("Searcher");
        about = tag.hasUUID("About") ? tag.getUUID("About") : null;
        home = tag.contains("Home") ? NbtUtils.readBlockPos(tag.getCompound("Home")) : null;
        goals();
    }

    /**
     * A day of posts: they stay at one a while (its seconds, give or take), then get up and walk to another, and settle
     * there in its stance. One post: they keep it. A fight or an errand comes first; a talk keeps them where they are.
     */
    static final class Routine extends Goal {
        private final HumanEntity h;
        private int stay, walking, tries;

        Routine(HumanEntity h) {
            this.h = h;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !h.posts.isEmpty() && h.getTarget() == null;
        }

        @Override
        public void start() {
            if (h.post < 0 || h.post >= h.posts.size()) {
                h.post = 0;
                h.settled = false;
            }
            stay = h.settled ? h.stayFor(h.posts.get(h.post)) / 2 : 0;
        }

        @Override
        public void stop() {
            if (h.getTarget() != null && h.settled) {
                h.standUp(); // trouble: up and at them
            }
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Post p = h.posts.get(h.post);
            if (h.settled) {
                boolean talked = h.level().getNearestPlayer(h, 4) != null; // nobody walks off in the middle of a talk
                if (h.posts.size() > 1 && !talked && --stay <= 0) {
                    h.post = h.nextPost();
                    h.standUp();
                    walking = tries = 0;
                    if (p.stance().onSeat()) { // off the chair, onto the free floor beside it
                        BlockPos b = h.besideSeat(p);
                        h.moveTo(b.getX() + 0.5, b.getY() + 0.07, b.getZ() + 0.5, h.getYRot(), 0);
                    }
                }
                return;
            }
            Vec3 s = p.spot();
            double dx = h.getX() - s.x, dz = h.getZ() - s.z;
            if (dx * dx + dz * dz < 0.8 && Math.abs(h.getY() - s.y) < 1.1 || ++walking > 600) {
                h.settle(h.post); // there (or lost for half a minute: they find their way all the same)
                stay = h.stayFor(p);
                return;
            }
            if (h.getNavigation().isDone() && walking % 20 == 1) {
                BlockPos goal = p.stance().onSeat() ? h.besideSeat(p) : p.at(); // walk up to the chair, then sit
                if (dx * dx + dz * dz < 2.5 || ++tries > 6) {
                    h.settle(h.post);
                    stay = h.stayFor(p);
                } else {
                    h.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 0.55);
                }
            }
        }
    }

    /** Where one stands by a seat: ahead of it if that's free floor, else to a side, else behind (L's table is in front of his chair). */
    private BlockPos besideSeat(Post p) {
        Direction f = p.facing();
        for (Direction d : new Direction[]{f, f.getClockWise(), f.getCounterClockWise(), f.getOpposite()}) {
            BlockPos b = p.at().relative(d);
            if (free(b) && free(b.above()) && !level().getBlockState(b.below()).getCollisionShape(level(), b.below()).isEmpty()) {
                return b;
            }
        }
        return p.at().relative(f);
    }

    /** Room to stand in: nothing there, or no more than a carpet. */
    private boolean free(BlockPos b) {
        net.minecraft.world.phys.shapes.VoxelShape shape = level().getBlockState(b).getCollisionShape(level(), b);
        return shape.isEmpty() || shape.max(Direction.Axis.Y) <= 0.1;
    }

    /** About as long as the post says, never the same twice. */
    private int stayFor(Post p) {
        return (int) (p.seconds() * 20 * (0.7 + random.nextDouble() * 0.6));
    }

    /** The next post: the first one (their usual place) half the time, else any other. */
    private int nextPost() {
        if (post != 0 && random.nextBoolean()) {
            return 0;
        }
        int next = 1 + random.nextInt(posts.size() - 1);
        return next == post ? 0 : next;
    }

    /** An FBI agent's tailing: never closer than 10 blocks, never farther than 24, never quite out of sight. */
    static final class Tail extends Goal {
        private final HumanEntity agent;

        Tail(HumanEntity agent) {
            this.agent = agent;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Nullable
        private LivingEntity suspect() {
            return agent.about == null || agent.getServer() == null ? null : agent.getServer().getPlayerList().getPlayer(agent.about);
        }

        @Override
        public boolean canUse() {
            LivingEntity s = suspect();
            return s != null && s.level() == agent.level();
        }

        @Override
        public void tick() {
            LivingEntity s = suspect();
            if (s == null) {
                return;
            }
            double d = agent.distanceTo(s);
            if (d > 24) {
                agent.getNavigation().moveTo(s, 1.0);
            } else if (d < 10) {
                agent.getNavigation().moveTo(agent.getX() * 2 - s.getX(), agent.getY(), agent.getZ() * 2 - s.getZ(), 0.8); // steps back
            } else if (agent.getNavigation().isDone() && agent.getRandom().nextInt(40) == 0) {
                agent.getNavigation().moveTo(s.getX() + agent.getRandom().nextInt(17) - 8, s.getY(), s.getZ() + agent.getRandom().nextInt(17) - 8, 0.6);
            }
        }
    }
}
