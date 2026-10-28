package ru.maksim.aicompanion;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A page of Sidoh's notebook loose in the wind (SideQuests, "Sidoh's pages"): it flutters round a spot a few blocks over
 * the ground, sparkling; when its human comes close it may be snatched away by a gust, once or twice, before it can be
 * caught (a right click, or just reaching it). Drawn as the page item, turned to the camera.
 */
public class LostPage extends Entity implements ItemSupplier {
    @Nullable
    private UUID owner;
    private Vec3 anchor = Vec3.ZERO;
    private int escapes, index, gust, gustLength = 24;
    private Vec3 gustFrom = Vec3.ZERO;

    public LostPage(EntityType<? extends LostPage> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public void setup(ServerPlayer owner, int index, Vec3 anchor, int escapes) {
        this.owner = owner.getUUID();
        this.index = index;
        this.anchor = anchor;
        this.escapes = escapes;
        setPos(anchor.x, anchor.y, anchor.z);
    }

    public int index() {
        return index;
    }

    public boolean ownedBy(UUID id) {
        return id.equals(owner);
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(AiCompanion.SIDOH_PAGE.get());
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (tickCount % 4 == 0) {
                level().addParticle(random.nextInt(3) == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.END_ROD,
                        getX() + (random.nextDouble() - 0.5) * 0.5, getY() + 0.2 + random.nextDouble() * 0.3,
                        getZ() + (random.nextDouble() - 0.5) * 0.5, 0, 0.01, 0);
            }
            return;
        }
        Vec3 goal;
        if (gust > 0) { // carried off by the wind: a quick arc to the new spot
            float k = 1 - --gust / (float) gustLength;
            float e = k * k * (3 - 2 * k);
            double sway = gustLength > 24 ? Math.sin(k * Math.PI * 5) * 1.5 * (1 - k) : 0; // falling from the sky: side to side
            goal = gustFrom.lerp(anchor, e).add(sway, gustLength > 24 ? 0 : Math.sin(k * Math.PI) * 3, sway * 0.6);
            setPos(goal.x, goal.y, goal.z);
        } else {
            double t = tickCount;
            goal = anchor.add(Math.sin(t * 0.05) * 1.3, Math.sin(t * 0.13) * 0.45 + 0.3, Math.cos(t * 0.043) * 1.3);
            Vec3 d = goal.subtract(position());
            setPos(getX() + d.x * 0.12, getY() + d.y * 0.12, getZ() + d.z * 0.12);
        }
        setYRot(getYRot() + 6);
        ServerPlayer player = owner == null ? null : (ServerPlayer) level().getPlayerByUUID(owner);
        if (player == null || gust > 0) {
            return;
        }
        double dist = player.distanceTo(this);
        if (dist < 5 && escapes > 0) {
            blowAway(player);
        } else if (dist < 1.6 && escapes == 0) {
            caught(player); // ran straight into it
        }
    }

    /** A gust takes it 8 to 14 blocks further, away from the human chasing it, a couple of blocks over the ground. */
    private void blowAway(ServerPlayer player) {
        ServerLevel level = (ServerLevel) level();
        Vec3 away = position().subtract(player.position()).multiply(1, 0, 1);
        double base = Math.atan2(away.z, away.x);
        for (int tries = 0; tries < 8; tries++) {
            double a = base + (random.nextDouble() - 0.5) * 1.4;
            double d = 8 + random.nextDouble() * 6;
            BlockPos column = BlockPos.containing(getX() + Math.cos(a) * d, getY(), getZ() + Math.sin(a) * d);
            if (!level.hasChunkAt(column)) {
                continue;
            }
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (Math.abs(ground.getY() - getY()) > 10) {
                continue;
            }
            escapes--;
            gust = gustLength = 24;
            gustFrom = position();
            anchor = Vec3.atBottomCenterOf(ground).add(0, 1.6 + random.nextDouble() * 1.4, 0);
            level.playSound(null, blockPosition(), Sounds.PAGE_GUST.get(), SoundSource.NEUTRAL, 0.9F, 1.0F);
            level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.3, getZ(), 8, 0.3, 0.2, 0.3, 0.05);
            SideQuests.pageBlown(player, this, anchor);
            return;
        }
        escapes = 0; // nowhere to blow to: let it be caught
    }

    private void caught(ServerPlayer player) {
        ServerLevel level = (ServerLevel) level();
        level.playSound(null, blockPosition(), Sounds.PAGE_CATCH.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 0.3, getZ(), 14, 0.25, 0.25, 0.25, 0.04);
        discard();
        SideQuests.pageCaught(player, index);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!level().isClientSide && player instanceof ServerPlayer p && ownedBy(p.getUUID()) && gust == 0) {
            if (escapes > 0) {
                blowAway(p); // not so fast
            } else {
                caught(p);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Player player) {
            interact(player, InteractionHand.MAIN_HAND); // a swipe at it counts as a grab
        }
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96 * 96;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        anchor = new Vec3(tag.getDouble("AX"), tag.getDouble("AY"), tag.getDouble("AZ"));
        escapes = tag.getInt("Escapes");
        index = tag.getInt("Index");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putDouble("AX", anchor.x);
        tag.putDouble("AY", anchor.y);
        tag.putDouble("AZ", anchor.z);
        tag.putInt("Escapes", escapes);
        tag.putInt("Index", index);
    }

    /** The first page, falling out of the sky in front of its human. */
    public static LostPage drop(ServerPlayer player) {
        LostPage page = AiCompanion.LOST_PAGE.get().create(player.serverLevel());
        Vec3 ahead = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(5));
        BlockPos ground = player.serverLevel().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, BlockPos.containing(ahead));
        page.setup(player, 0, Vec3.atBottomCenterOf(ground).add(0, 1.4, 0), 0);
        page.setPos(page.anchor.x, page.anchor.y + 18, page.anchor.z);
        page.gustFrom = page.position();
        page.gust = page.gustLength = 90; // a slow fall, swaying like a leaf
        player.serverLevel().addFreshEntity(page);
        return page;
    }
}
