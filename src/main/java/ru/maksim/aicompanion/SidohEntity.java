package ru.maksim.aicompanion;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Sidoh, the god of death who lost his notebook. Bound to whoever owns it (the mafia boss, at first), he trembles near
 * them and, scared, does as told: in the hideout he tears masks off intruders so the boss can read their names. Only
 * humans who have touched his notebook can see or hear him; to everyone else he's air (Ryuk passes on his whining).
 * Hand him his notebook and he tells the one secret he knows about the rules, then flies home. Home is the realm of the
 * gods of death: a dreamer finds him there by the big hole, staring down after his pages, and he talks to them as far as
 * they got with him (a stranger, a helper with pages still loose, the one who brought them back).
 */
public class SidohEntity extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<String> MOOD = SynchedEntityData.defineId(SidohEntity.class, EntityDataSerializers.STRING);
    private static final RawAnimation IDLE = loop("idle"), FLY = loop("fly"), BEG = loop("beg"), SCARED = loop("scared"),
            SEARCH = loop("search"), JOY = loop("joy"), EAT = loop("eat"), THANK = loop("thank"), POINT = loop("point");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private BlockPos home;
    @Nullable
    private UUID follow; // the human who now owns his notebook
    private int nextLine;
    private boolean quest; // the side quest "Sidoh's pages" (SideQuests), not the mafia's story
    private String actMood = "";
    private int actTicks, leaveTicks;
    private boolean pendingHint; // chocolate first, then he shows the way
    @Nullable
    private Vec3 pointAt;
    private boolean realm; // at home in the realm of the gods of death (Realm), by the big hole
    private int realmLine;

    public SidohEntity(EntityType<? extends SidohEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100.0).add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    private static RawAnimation loop(String name) {
        return RawAnimation.begin().thenLoop("animation.sidoh." + name);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(MOOD, "idle");
    }

    public void setup(BlockPos home) {
        this.home = home;
    }

    public void follow(ServerPlayer owner) {
        follow = owner.getUUID();
    }

    /** The side quest: he trails the human who caught his page, searching, shaking. */
    public void questFor(ServerPlayer player) {
        follow = player.getUUID();
        quest = true;
    }

    public boolean questOf(ServerPlayer player) {
        return quest && player.getUUID().equals(follow);
    }

    /** A moment of his own: scared, joy, thank, eat, beg, point... for this many ticks. */
    public void act(String mood, int ticks) {
        actMood = mood;
        actTicks = ticks;
    }

    /** "There! It flew that way!" */
    public void pointAt(Vec3 where) {
        pointAt = where;
        act("point", 60);
    }

    /** At home in the realm: he hovers at {@code at}, staring down the hole, and talks to whoever comes to him. */
    public void realmHome(BlockPos at) {
        home = at;
        realm = true;
        follow = null;
        quest = false;
        setCustomName(Component.translatable("entity.aicompanion.sidoh"));
        setCustomNameVisible(true);
    }

    public boolean inRealm() {
        return realm;
    }

    /**
     * Where he hangs near his human: where he is, while that's 1.5 to 3 blocks off, so they can turn, look at him and
     * click him; farther, he comes back to 2.5 blocks on the side he's on; closer, he backs off a little. Never steered
     * by where the human looks: that only ever sent him round behind them, out of sight.
     */
    private Vec3 beside(ServerPlayer owner) {
        Vec3 at = owner.position().add(0, 0.3, 0);
        Vec3 off = position().subtract(at).multiply(1, 0, 1);
        if (off.lengthSqr() < 0.01) {
            off = owner.getLookAngle().multiply(1, 0, 1);
        }
        double d = off.length(), want = d > 3 ? 2.5 : d < 1.5 ? 2 : d; // within a hand's reach: survival clicks no further than 3 blocks
        return at.add(off.normalize().scale(want));
    }

    /** Off home (up and away) after this many ticks. */
    public void leaveIn(int ticks) {
        leaveTicks = ticks;
    }

    public String mood() {
        return entityData.get(MOOD);
    }

    /** His notebook went to someone the story keeps (Mello): back to hovering where it is. */
    void unbind(BlockPos where) {
        follow = null;
        home = where;
    }

    // ------------------------------------------------------------------ moving and acting

    @Override
    public void tick() {
        noPhysics = true;
        setNoGravity(true);
        super.tick();
        if (level().isClientSide) {
            return;
        }
        if (leaveTicks > 0 && --leaveTicks == 0) {
            SideQuests.puff((ServerLevel) level(), position());
            level().playSound(null, blockPosition(), Sounds.SIDOH_WING.get(), getSoundSource(), 1.0F, 1.0F + random.nextFloat() * 0.15F);
            discard(); // home, to the realm
            return;
        }
        boolean hint = false;
        if (actTicks > 0 && --actTicks == 0) {
            pointAt = null;
            hint = pendingHint;
            pendingHint = false;
        }
        ServerPlayer owner = follow == null ? null : getServer().getPlayerList().getPlayer(follow);
        if (hint && owner != null) {
            SideQuests.sidohHint(owner, this);
        }
        Vec3 beside = owner != null && owner.level() == level() ? beside(owner) : position();
        if (leaveTicks > 0 && leaveTicks < 40) {
            beside = position().add(0, 0.6, 0); // up and away
        }
        Vec3 goal = owner != null && owner.level() == level() ? beside
                : realm && home != null ? Vec3.atBottomCenterOf(home).add(0, 0.4 + Math.sin(tickCount / 30.0) * 0.12, 0) // bobbing by the hole
                : home != null ? Vec3.atCenterOf(home).add(Math.sin(tickCount / 40.0) * 3, 1.5, Math.cos(tickCount / 40.0) * 3) : position();
        if (owner != null && owner.level() != level() || goal.distanceToSqr(position()) > 64 * 64) {
            teleportTo(goal.x, goal.y, goal.z);
        }
        Vec3 d = goal.subtract(position());
        setDeltaMovement(getDeltaMovement().lerp(d.length() > 0.05 ? d.scale(Math.min(0.5, d.length() * 0.15) / d.length()) : Vec3.ZERO, 0.2));
        move(MoverType.SELF, getDeltaMovement());
        Player near = quest && owner != null ? owner : level().getNearestPlayer(this, realm ? 9 : 6);
        Vec3 look = pointAt != null ? pointAt : near != null ? near.position() : null;
        if (look != null) {
            if (pointAt == null) {
                getLookControl().setLookAt(near);
            }
            setYRot((float) (Math.atan2(look.z - getZ(), look.x - getX()) * 180 / Math.PI) - 90);
            yBodyRot = getYRot();
        }
        String mood = actTicks > 0 ? actMood : getDeltaMovement().length() > 0.15 ? "fly"
                : realm ? (near != null ? "idle" : "search") // staring down the hole after his pages, till someone comes
                : owner != null && Mafia.holdsSidohsNote(owner) ? "beg"
                : quest ? (owner != null && SideQuests.stage(owner, "sidoh") == 3 ? "beg" : "search") // all found: claws out for them
                : near != null && near.distanceTo(this) < 3 ? "scared" : "idle";
        entityData.set(MOOD, mood);
        if (tickCount % 20 == 0) {
            everySecond((ServerLevel) level(), owner);
        }
    }

    private void everySecond(ServerLevel level, @Nullable ServerPlayer owner) {
        if (realm) {
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(7))) {
                Kira.Case c = Kira.of(p);
                if (!c.flag("dream_sidoh")) { // the first time this dream: he notices the dreamer
                    c.flags.put("dream_sidoh", level.getGameTime());
                    say(p, realmGreeting(p));
                    act("scared", 30);
                }
            }
            return;
        }
        if (follow == null) { // only whoever he can see: no reaching through the ground at the shack above
            for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(14), this::hasLineOfSight)) {
                if (Mafia.holdsSidohsNote(p)) {
                    follow(p); // his notebook, picked up far from him (the boss died away from the hideout): he clings to it now
                } else if (Lifespans.masked(p) && random.nextInt(4) == 0) { // only what hides a face: an ordinary helmet doesn't
                    tearOffMask(level, p, p.getItemBySlot(EquipmentSlot.HEAD));
                }
            }
        }
        if (owner != null && --nextLine <= 0 && owner.distanceTo(this) < 12 && Mafia.seesSidoh(owner) && leaveTicks == 0) {
            nextLine = 25 + random.nextInt(20);
            String key = quest ? "aicompanion.sidoh.q.line" + (1 + random.nextInt(6)) : "aicompanion.sidoh.line" + (1 + random.nextInt(6));
            owner.sendSystemMessage(Component.literal("<" + Config.tr("Сидо", "Sidoh") + "> ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.translatable(key).withStyle(ChatFormatting.GRAY)));
        }
    }

    /** The manga's raid: he pulled the helmets off so Neylon could read the names. */
    private void tearOffMask(ServerLevel level, ServerPlayer p, ItemStack head) {
        p.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        level.addFreshEntity(new ItemEntity(level, p.getX(), p.getY() + 1.5, p.getZ(), head.copy()));
        level.playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER, getSoundSource(), 1.0F, 0.6F);
        p.sendSystemMessage(Component.translatable(Mafia.seesSidoh(p) ? "aicompanion.sidoh.mask_seen" : "aicompanion.sidoh.mask")
                .withStyle(ChatFormatting.RED));
        CompanionEntity ryuk = CompanionEntity.of(p);
        if (ryuk != null) {
            ryuk.remark(Config.tr("Сидо (твой человек его не видит), трясясь, сорвал маску с головы ТВОЕГО ЧЕЛОВЕКА: он служит главарю, у которого его тетрадь. Теперь главарь видит лицо твоего человека и может прочитать его имя",
                    "Sidoh (your human can't see him), shaking, tore the mask off YOUR HUMAN's head: he serves the boss who holds his notebook. Now the boss can see your human's face and read their name"), "story", 0);
        }
    }

    /** In the realm: how he greets a dreamer, by how far they got with him. */
    private static String realmGreeting(ServerPlayer p) {
        return SideQuests.stage(p, "sidoh") >= 4 || Kira.of(p).flag("sidoh_done") ? "aicompanion.sidoh.realm.hello_friend"
                : SideQuests.stage(p, "sidoh") >= 1 ? "aicompanion.sidoh.realm.hello_helper" : "aicompanion.sidoh.realm.hello_stranger";
    }

    /** In the realm, talked to: a line by how far they got with him, a new one each time. */
    private void realmTalk(ServerPlayer p) {
        long stage = SideQuests.stage(p, "sidoh");
        String kind = stage >= 4 ? "friend" : stage >= 1 ? "helper" : Kira.of(p).flag("sidoh_done") ? "notebook" : "stranger";
        say(p, "aicompanion.sidoh.realm." + kind + (1 + realmLine++ % (kind.equals("notebook") ? 2 : 4)));
        act(kind.equals("stranger") || kind.equals("helper") ? "beg" : realmLine % 2 == 0 ? "thank" : "joy", 50);
        if (kind.equals("helper")) {
            p.sendSystemMessage(Component.translatable("aicompanion.sidoh.realm.pages_left", SideQuests.PAGES
                    - Long.bitCount(Kira.of(p).flags.getOrDefault("q_sidoh_got", 0L)), SideQuests.PAGES).withStyle(ChatFormatting.GOLD));
        }
    }

    private static void say(ServerPlayer p, String key) {
        p.sendSystemMessage(Component.literal("<" + Config.tr("Сидо", "Sidoh") + "> ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.translatable(key).withStyle(ChatFormatting.GRAY)));
    }

    /** His notebook, handed back by whoever holds it: the secret about the rules, then home. */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (realm) {
            if (!level().isClientSide && hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer p) {
                realmTalk(p);
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        if (!level().isClientSide && player instanceof ServerPlayer p && Mafia.isSidohsNote(p.getItemInHand(hand))) {
            p.setItemInHand(hand, ItemStack.EMPTY);
            Mafia.returned(p, this);
            return InteractionResult.CONSUME;
        }
        if (!level().isClientSide && player instanceof ServerPlayer p && quest && questOf(p) && SideQuests.returnPages(p, this)) {
            return InteractionResult.CONSUME; // his pages, back in his claws
        }
        if (!level().isClientSide && player instanceof ServerPlayer p && p.getItemInHand(hand).is(AiCompanion.CHOCOLATE.get()) && Mafia.seesSidoh(p)) {
            p.getItemInHand(hand).shrink(1); // the way to his heart, as Mello knew
            level().playSound(null, blockPosition(), Sounds.CHOCOLATE.get(), getSoundSource(), 1.0F, 1.0F);
            p.sendSystemMessage(Component.literal("<" + Config.tr("Сидо", "Sidoh") + "> ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.translatable("aicompanion.sidoh.chocolate").withStyle(ChatFormatting.GRAY)));
            act("eat", 50);
            pendingHint = questOf(p); // and then: "it flew that way!"
            return InteractionResult.CONSUME;
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean isPickable() { // in the realm every dreamer sees him: it's his home, not the human world
        return !level().isClientSide || level().dimension() == Realm.KEY || ru.maksim.aicompanion.client.ClientHooks.seesSidoh();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, amount); // swords pass through
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) {
            tag.put("Home", NbtUtils.writeBlockPos(home));
        }
        if (follow != null) {
            tag.putUUID("Follow", follow);
        }
        tag.putBoolean("Quest", quest);
        tag.putBoolean("Realm", realm);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        home = tag.contains("Home") ? NbtUtils.readBlockPos(tag.getCompound("Home")) : null;
        follow = tag.hasUUID("Follow") ? tag.getUUID("Follow") : null;
        quest = tag.getBoolean("Quest");
        realm = tag.getBoolean("Realm");
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> state.setAndContinue(switch (entityData.get(MOOD)) {
            case "fly" -> FLY;
            case "beg" -> BEG;
            case "scared" -> SCARED;
            case "search" -> SEARCH;
            case "joy" -> JOY;
            case "eat" -> EAT;
            case "thank" -> THANK;
            case "point" -> POINT;
            default -> IDLE;
        })));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
