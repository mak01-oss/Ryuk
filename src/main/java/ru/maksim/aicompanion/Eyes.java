package ru.maksim.aicompanion;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

/**
 * The eye deal (the manga's 目の取引): half of the life you have left, for the eyes of a god of death. From then on the
 * true name and the remaining lifespan float over every human you look at, except Death Note owners' lifespans (and
 * you never see your own). It can be made again; each time halves what is left. Nobody can undo it.
 */
public final class Eyes {
    private static final Map<UUID, Long> OFFERED = new HashMap<>(); // player -> game time the deal was put to them
    private static final long OFFER_VALID = 36000; // half an hour: its screen waits for the floor (an unread card, his line) first

    private Eyes() {
    }

    /** Ryuk puts the deal on the table (his eye_deal action); the human decides on a screen of its own. */
    public static void offer(ServerPlayer player) {
        if (Kira.of(player).sealed) {
            return;
        }
        OFFERED.put(player.getUUID(), player.server.overworld().getGameTime());
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Network.Offer(Network.Choice.EYE_DEAL));
    }

    public static void answer(ServerPlayer player, boolean yes) {
        Long when = OFFERED.remove(player.getUUID());
        long now = player.server.overworld().getGameTime();
        Kira.Case c = Kira.of(player);
        if (when == null || now - when > OFFER_VALID || c.sealed) {
            return; // never offered, or long ago: a stale packet decides nothing
        }
        CompanionEntity ryuk = CompanionEntity.of(player);
        if (!yes) {
            if (ryuk != null) {
                ryuk.remark(Config.tr("твой человек отказался от сделки на глаза", "your human turned down the eye deal"), "deal", 0);
            }
            return;
        }
        c.life /= 2;
        c.eyes++;
        Pact.thrill(player, Pact.THRILL_BIG);
        Kira.changed(player);
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0, false, false));
        Network.cue(player, "sting.eyes");
        player.serverLevel().sendParticles(new DustParticleOptions(new Vector3f(0.9F, 0.05F, 0.05F), 1.2F),
                player.getX(), player.getEyeY(), player.getZ(), 40, 0.25, 0.15, 0.25, 0.0);
        AiCompanion.LOGGER.info("{} made eye deal #{}", player.getGameProfile().getName(), c.eyes);
        if (c.eyes == 1) { // the first deal: now the red is put where this skin's eyes are
            Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Network.Offer(Network.Choice.EYE_LOOK));
        }
        if (ryuk != null) {
            ryuk.remark(Config.tr("сделка заключена" + (c.eyes > 1 ? " снова (уже " + c.eyes + "-й раз)" : "")
                            + ": ты забрал половину оставшейся жизни своего человека, теперь он видит имена и сроки жизни людей "
                            + "(кроме сроков владельцев тетрадей). Своего срока он не видит.",
                    "the deal is done" + (c.eyes > 1 ? " again (deal number " + c.eyes + ")" : "")
                            + ": you took half of your human's remaining life; now they see the names and lifespans of humans "
                            + "(not the lifespans of notebook owners). They can't see their own."), "deal", 0);
        }
    }
}
