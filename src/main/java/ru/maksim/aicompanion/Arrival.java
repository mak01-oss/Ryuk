package ru.maksim.aicompanion;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Ryuk's arrival, the first time, as a little film (client/Cinema "cine.ryuk", music.ryuk). The world holds its breath:
 * the view narrows, the edges darken, ash falls, the heart beats faster; lightning cracks behind; he dives out of the
 * sky and lands right in front of his human, wings out, in a burst of black; his name comes up over him. Only when
 * the film is over do his first words come, and the chapter's card.
 */
public final class Arrival {
    private static final int LIGHTNING = 44, DIVE = 62, LAND = 76, END = 222;
    private static final Map<UUID, Film> FILMS = new HashMap<>();

    private record Film(long start, Vec3 land, float yaw) {
    }

    private Arrival() {
    }

    static boolean running(ServerPlayer player) {
        return FILMS.containsKey(player.getUUID());
    }

    /** The notebook has been held long enough: the film begins, Ryuk comes at its height. */
    static void start(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 fwd = new Vec3(look.x, 0, look.z).lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : new Vec3(look.x, 0, look.z).normalize();
        Vec3 land = null;
        for (double d : new double[]{3.4, 2.6, 1.8}) { // in front, where there's room for him
            Vec3 at = player.position().add(fwd.scale(d)).add(0, 0.35, 0);
            BlockPos b = BlockPos.containing(at);
            if (player.level().getBlockState(b).isAir() && player.level().getBlockState(b.above()).isAir() && player.level().getBlockState(b.above(2)).isAir()) {
                land = at;
                break;
            }
        }
        if (land == null) {
            land = player.position().add(0, 0.5, 0).add(fwd.scale(1.2));
        }
        float yaw = (float) (Math.atan2(-(player.getX() - land.x), player.getZ() - land.z) * 180 / Math.PI); // facing his human
        FILMS.put(player.getUUID(), new Film(player.serverLevel().getGameTime(), land, yaw));
        Films.showing(player, END);
        Network.cue(player, "cine.ryuk");
    }

    /** Every server tick: the film's beats for whoever is in one. */
    static void tick(MinecraftServer server) {
        if (FILMS.isEmpty()) {
            return;
        }
        for (Iterator<Map.Entry<UUID, Film>> it = FILMS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Film> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            Film f = e.getValue();
            if (player == null || !player.isAlive()) {
                it.remove(); // gone or dead: he comes the plain way next time the notebook is held
                continue;
            }
            ServerLevel level = player.serverLevel();
            long t = level.getGameTime() - f.start();
            if (t < LAND && t % 3 == 0) { // ash drifting down, thicker as he comes
                int n = 6 + (int) (t / 6);
                level.sendParticles(ParticleTypes.ASH, player.getX(), player.getY() + 5, player.getZ(), n, 5, 2.5, 5, 0.0);
                if (t > 30) {
                    level.sendParticles(ParticleTypes.SQUID_INK, f.land().x, f.land().y + 8, f.land().z, 2, 2.5, 3, 2.5, 0.01);
                }
            }
            if (t == LIGHTNING) {
                Vec3 at = f.land().add(f.land().subtract(player.position()).multiply(1, 0, 1).normalize().scale(7));
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(at.x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) at.x, (int) at.z), at.z);
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
            }
            if (t == DIVE) {
                CompanionEntity ryuk = CompanionEntity.summon(player, true);
                if (ryuk != null) {
                    ryuk.moveTo(f.land().x, f.land().y + 14, f.land().z, f.yaw(), 0);
                    ryuk.hold(f.land());
                    Network.cue(player, "cine.look@" + ryuk.getId());
                }
            }
            CompanionEntity ryuk = t >= DIVE ? CompanionEntity.of(player) : null;
            if (ryuk != null && t > DIVE && t <= LAND) { // the dive: slow at first, then falling like a stone
                double k = (t - DIVE) / (double) (LAND - DIVE);
                Vec3 at = f.land().add(0, 14 * (1 - k * k), 0);
                ryuk.teleportTo(at.x, at.y, at.z);
                ryuk.setYRot(f.yaw());
                ryuk.yBodyRot = f.yaw();
                ryuk.yHeadRot = f.yaw();
            }
            if (ryuk != null && t == LAND) {
                impact(level, f.land());
                ryuk.act("show_wings", 90);
                ryuk.attendTo(player, END - LAND);
                level.playSound(null, f.land().x, f.land().y, f.land().z, Sounds.RYUK_POOF.get(), SoundSource.NEUTRAL, 1.5F, 0.85F);
            }
            if (t >= END) {
                it.remove();
                if (ryuk != null) {
                    ryuk.hold(null);
                    ryuk.remark(Config.tr("ты только что эффектно спустился к человеку, который подобрал твою тетрадь, и впервые показался ему: он видел, как ты падаешь с неба и раскрываешь крылья",
                            "you just made a grand entrance to the human who picked up your notebook and showed yourself for the first time: they saw you fall out of the sky and spread your wings"), "intro", 0);
                    Network.card(player, Network.Broadcast.CHAPTER, Component.translatable("aicompanion.card.ryuk"), Component.translatable("aicompanion.card.ryuk_text"),
                            "", null, "none"); // the film had its music
                }
            }
        }
    }

    /** The landing: a ring of black smoke thrown out along the ground, ash and ink bursting up, souls rising. */
    private static void impact(ServerLevel level, Vec3 c) {
        for (int i = 0; i < 28; i++) { // low along the ground, not into his human's face
            double a = i * Math.PI * 2 / 28;
            level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x + Math.cos(a) * 1.1, c.y - 0.25, c.z + Math.sin(a) * 1.1, 0,
                    Math.cos(a), 0.0, Math.sin(a), 0.12);
        }
        level.sendParticles(ParticleTypes.ASH, c.x, c.y + 1.2, c.z, 120, 1.6, 1.2, 1.6, 0.05);
        level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y + 2.6, c.z, 18, 0.9, 0.5, 0.9, 0.05);
        level.sendParticles(ParticleTypes.SOUL, c.x, c.y + 0.5, c.z, 24, 1.2, 0.4, 1.2, 0.04);
        level.sendParticles(new DustParticleOptions(new Vector3f(0.55F, 0.02F, 0.02F), 1.6F), c.x, c.y + 2.2, c.z, 30, 0.6, 0.5, 0.6, 0.0);
    }
}
