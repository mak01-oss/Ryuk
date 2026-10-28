package ru.maksim.aicompanion.client;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.maksim.aicompanion.AiCompanion;
import ru.maksim.aicompanion.HumanEntity;
import ru.maksim.aicompanion.Realm;
import ru.maksim.aicompanion.SidohEntity;
import ru.maksim.aicompanion.Sounds;

/**
 * The mod's music on this client. Stings mark the story's moments (a card, a death, a deal). Themes belong to
 * places (the realm of the gods of death, near L, near the mafia, near Near, while Sidoh is about) and to scenes
 * (L's special, Ryuk writing your name, a new world, Mu), a scene over any place. While a theme of ours plays,
 * Minecraft's own music keeps quiet; when ours ends, Minecraft's comes back by itself.
 */
@Mod.EventBusSubscriber(modid = AiCompanion.MODID, value = Dist.CLIENT)
public final class Soundtrack {
    private static final int GRACE = 200; // ticks a place's theme lingers after one walks off
    @Nullable
    private static Track playing;
    @Nullable
    private static String scene, place;
    private static boolean sceneLoop;
    private static long placeSeen;

    private Soundtrack() {
    }

    /** From the server or a screen: "sting.x" plays now, "music.x" plays through once as a scene. */
    public static void cue(String id) {
        if (id.startsWith("cine.")) {
            Cinema.cue(id);
        } else if (id.startsWith("music.")) {
            scene(id, false);
        } else {
            sting(id);
        }
    }

    public static void sting(String id) {
        SoundEvent e = Sounds.cue(id);
        if (e != null) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, 1.0F, 1.0F));
        }
    }

    /** A scene's theme: over the place's, once or (loop) until {@link #endScene}; it starts at full volume. */
    public static void scene(String id, boolean loop) {
        scene = id;
        sceneLoop = loop;
        start(id, loop, 0);
    }

    public static void endScene(String id) {
        if (id.equals(scene)) {
            scene = null;
            if (playing != null && playing.id.equals(id)) {
                playing.fadeOut();
            }
        }
    }

    /** The theme playing now, or null (the autotest's ears). */
    @Nullable
    static String now() {
        return playing == null || playing.fading ? null : playing.id;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (playing != null) {
                mc.getSoundManager().stop(playing);
                playing = null;
            }
            scene = place = null;
            return;
        }
        if (scene != null && playing != null && playing.id.equals(scene) && playing.age > 40 && !mc.getSoundManager().isActive(playing)) {
            scene = null; // played through
        }
        String want = scene != null ? scene : place(mc);
        if (want == null) {
            if (playing != null) {
                playing.fadeOut();
            }
        } else if (playing == null || !playing.id.equals(want) || playing.fading) {
            start(want, scene == null || sceneLoop, scene == null ? 50 : 0);
        }
        if (playing != null && playing.fading && !mc.getSoundManager().isActive(playing)) {
            playing = null;
        }
    }

    /** Minecraft's music waits while ours plays. */
    @SubscribeEvent
    public static void hush(PlaySoundEvent event) {
        SoundInstance s = event.getSound();
        if (playing != null && !playing.fading && s != null && !(s instanceof Track) && s.getSource() == SoundSource.MUSIC) {
            event.setSound(null);
        }
    }

    private static void start(String id, boolean loop, int fadeIn) {
        SoundEvent e = Sounds.cue(id);
        Minecraft mc = Minecraft.getInstance();
        if (e == null) {
            return;
        }
        if (playing != null) {
            playing.fadeOut();
        }
        mc.getMusicManager().stopPlaying();
        playing = new Track(id, e, loop, fadeIn);
        mc.getSoundManager().play(playing);
    }

    /** Whose theme fits where the player is: the realm; then L, the mafia, Near close by; then Sidoh about. */
    @Nullable
    private static String place(Minecraft mc) {
        String now = null;
        if (mc.level.dimension() == Realm.KEY) {
            now = "music.realm";
        } else {
            AABB around = mc.player.getBoundingBox().inflate(40);
            int best = Integer.MAX_VALUE;
            for (HumanEntity h : mc.level.getEntitiesOfClass(HumanEntity.class, around, HumanEntity::isAlive)) {
                String theme = switch (h.role()) {
                    case L, WATARI -> "music.l";
                    case MELLO, BOSS, MAFIA -> "music.mafia";
                    case NEAR, SPK -> "music.near";
                    default -> null;
                };
                int rank = theme == null ? Integer.MAX_VALUE : theme.equals("music.l") ? 0 : theme.equals("music.mafia") ? 1 : 2;
                if (rank < best) {
                    best = rank;
                    now = theme;
                }
            }
            if (now == null && ClientHooks.seesSidoh() && !mc.level.getEntitiesOfClass(SidohEntity.class, mc.player.getBoundingBox().inflate(28)).isEmpty()) {
                now = "music.sidoh";
            }
        }
        long t = mc.level.getGameTime();
        if (now != null) {
            place = now;
            placeSeen = t;
            return now;
        }
        if (place != null && t - placeSeen > GRACE) {
            place = null;
        }
        return place;
    }

    /** One of our themes: fades in (unless a scene), fades out when let go. */
    static final class Track extends AbstractTickableSoundInstance {
        final String id;
        boolean fading;
        int age;
        private final float step;

        Track(String id, SoundEvent event, boolean loop, int fadeIn) {
            super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.id = id;
            this.looping = loop;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.step = fadeIn <= 0 ? 1.0F : 1.0F / fadeIn;
            this.volume = fadeIn <= 0 ? 1.0F : 0.02F;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            age++;
            if (fading) {
                volume -= 1.0F / 40;
                if (volume <= 0) {
                    volume = 0;
                    stop();
                }
            } else if (volume < 1.0F) {
                volume = Math.min(1.0F, volume + step);
            }
        }

        void fadeOut() {
            fading = true;
        }
    }
}
