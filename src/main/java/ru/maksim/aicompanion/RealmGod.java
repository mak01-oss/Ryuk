package ru.maksim.aicompanion;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The gods of death who live in the realm (Realm): Gook and Deridovely at their bowl of bones, Armonia on his throne.
 * They don't move from their places; they sit, laugh at a loser, talk when talked to (Realm.talk). make_realm.py
 * draws them; {@link #WHO} picks which.
 */
public class RealmGod extends PathfinderMob implements GeoEntity {
    public static final String[] WHO = {"gook", "deridovely", "armonia"};
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(RealmGod.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> MOOD = SynchedEntityData.defineId(RealmGod.class, EntityDataSerializers.STRING);
    private static final Map<String, RawAnimation> ANIMS = new HashMap<>();
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int moodTicks;
    private float facing;

    public RealmGod(EntityType<? extends RealmGod> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setPersistenceRequired();
        setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 100.0).add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    /** Which god, where he sits, which way he faces. */
    public void setup(int variant, Vec3 at, float yaw, Component name) {
        entityData.set(VARIANT, variant);
        facing = yaw;
        moveTo(at.x, at.y, at.z, yaw, 0);
        setYHeadRot(yaw);
        yBodyRot = yaw;
        setCustomName(name);
        setCustomNameVisible(true);
    }

    public String who() {
        return WHO[Math.floorMod(entityData.get(VARIANT), WHO.length)];
    }

    /** "laugh" or "talk" for a while; then back to sitting. */
    public void act(String mood, int ticks) {
        entityData.set(MOOD, mood);
        moodTicks = ticks;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(VARIANT, 0);
        entityData.define(MOOD, "sit");
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        setYRot(facing);
        yBodyRot = facing;
        if (!level().isClientSide && moodTicks > 0 && --moodTicks == 0) {
            entityData.set(MOOD, "sit");
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide && hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer p) {
            Realm.talk(p, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, amount);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Who", entityData.get(VARIANT));
        tag.putFloat("Facing", facing);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(VARIANT, tag.getInt("Who"));
        facing = tag.getFloat("Facing");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6, state -> {
            String name = "animation." + who() + "." + entityData.get(MOOD);
            return state.setAndContinue(ANIMS.computeIfAbsent(name, n -> RawAnimation.begin().thenLoop(n)));
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
