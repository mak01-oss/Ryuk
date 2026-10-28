package ru.maksim.aicompanion.client;

import com.sun.jna.platform.win32.Kernel32;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.BufferUtils;
import ru.maksim.aicompanion.AiCompanion;
import ru.maksim.aicompanion.Config;
import ru.maksim.aicompanion.Tts;
import ru.maksim.aicompanion.VoiceFx;

/**
 * Ryuk says his lines out loud: Piper (local neural TTS, kept running as a separate process; in English his own voice
 * model) or Groq Orpheus → VoiceFx (his timbre) → Minecraft's sound engine, positioned at him. His mouth follows the
 * loudness of what is actually playing.
 */
public final class RyukVoice {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "aicompanion-tts");
        t.setDaemon(true);
        return t;
    });
    private static final Map<Integer, Speech> SPEAKING = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicInteger QUEUED = new java.util.concurrent.atomic.AtomicInteger(); // lines waiting for the voice
    private static final java.util.concurrent.atomic.AtomicInteger IN_FLIGHT = new java.util.concurrent.atomic.AtomicInteger(); // being made into sound
    private static volatile long playingUntil; // System.nanoTime() when the line now playing (and its breath) ends
    private static volatile boolean failing; // the last line couldn't be voiced: his mouth flaps instead
    private static final long GAP = 450_000_000L; // the pause between two lines, ns
    private static long free; // when the line now playing ends (the voice thread's own)
    @Nullable
    private static Process piper;
    private static BufferedWriter piperIn;
    private static BufferedReader piperOut;
    @Nullable
    private static String piperVoice; // model@speed the running piper was started with
    private static boolean warned;

    private record Speech(float[] envelope, long start) {
    }

    private RyukVoice() {
    }

    /** Network or render thread: Ryuk (entity id) said {@code text}. */
    public static void speak(int entityId, String text, String emotion) {
        boolean own = Config.ownVoice();
        String words = own ? Tts.piper(text) : Tts.direct(text, emotion);
        if (!Config.TTS.get() || words.isEmpty()) {
            return;
        }
        QUEUED.incrementAndGet();
        IN_FLIGHT.incrementAndGet();
        EXECUTOR.execute(() -> {
            if (QUEUED.decrementAndGet() >= 2) {
                IN_FLIGHT.decrementAndGet();
                return; // two newer lines are waiting: this one stays in the chat, so he doesn't fall behind
            }
            try {
                long t0 = System.nanoTime();
                VoiceFx.Pcm raw = own ? synthesize(words) : orpheus(words);
                short[] voice = own ? VoiceFx.rough(raw.samples(), raw.rate(), Config.RYUK_PITCH.get() + pitchOffset(emotion), Config.RYUK_ROUGH.get())
                        : VoiceFx.ryuk(raw.samples(), raw.rate(), Config.ORPHEUS_PITCH.get() + pitchOffset(emotion), Config.ORPHEUS_RASP.get());
                float[] mouth = VoiceFx.envelope(voice, raw.rate());
                long wait = free - System.nanoTime(); // one line at a time: the next waits for the end of this one, and a breath
                AiCompanion.LOGGER.info("Ryuk's voice: {} chars in {} ms, waits {} ms", words.length(), (System.nanoTime() - t0) / 1_000_000,
                        Math.max(0, wait / 1_000_000));
                if (wait > 0) {
                    Thread.sleep(wait / 1_000_000);
                }
                free = System.nanoTime() + (long) (voice.length * 1e9 / raw.rate()) + GAP;
                playingUntil = free; // RyukLines said it when it had the floor: it plays now, whatever comes up
                failing = false;
                Minecraft.getInstance().execute(() -> play(entityId, voice, raw.rate(), mouth));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException | RuntimeException e) {
                failing = true;
                if (warned) {
                    AiCompanion.LOGGER.debug("Ryuk's voice failed again: {}", e.getMessage());
                    return; // told once already, don't flood the log
                }
                AiCompanion.LOGGER.warn("Ryuk's voice failed", e);
                if (!warned) {
                    warned = true;
                    Minecraft.getInstance().execute(() -> {
                        if (Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.sendSystemMessage(
                                    Component.translatable("aicompanion.msg.voice_error", String.valueOf(e.getMessage())));
                        }
                    });
                }
            } finally {
                IN_FLIGHT.decrementAndGet();
            }
        });
    }

    /** A line of his is being made into sound or is playing. */
    public static boolean busy() {
        return QUEUED.get() > 0 || IN_FLIGHT.get() > 0 || System.nanoTime() < playingUntil;
    }

    /** No voice to follow (switched off, or it failed): his mouth flaps at reading pace instead. */
    public static boolean silent() {
        return !Config.TTS.get() || failing;
    }

    /** Render thread, for the face: true/false while his voice plays, null when it doesn't. */
    @Nullable
    public static Boolean mouthOpen(int entityId) {
        Speech s = SPEAKING.get(entityId);
        if (s == null) {
            return null;
        }
        int window = (int) ((System.nanoTime() - s.start()) / 50_000_000L);
        if (window >= s.envelope().length) {
            SPEAKING.remove(entityId);
            return null;
        }
        return s.envelope()[window] > 0.3F;
    }

    private static double pitchOffset(String emotion) {
        return switch (emotion) {
            case "happy" -> 0.03;
            case "sad" -> -0.04;
            case "angry" -> -0.02;
            case "surprised", "scared" -> 0.06;
            default -> 0;
        };
    }

    /** English: Groq Orpheus, at most 200 characters per request, the pieces glued with a short pause. */
    private static VoiceFx.Pcm orpheus(String words) {
        List<short[]> parts = new ArrayList<>();
        int rate = 24000;
        for (String chunk : Tts.chunks(words, Tts.MAX_CHUNK)) {
            VoiceFx.Pcm pcm = VoiceFx.readWav(Tts.speech(Config.ORPHEUS_URL.get(), Config.speechKey(), Config.ORPHEUS_MODEL.get(),
                    Config.ORPHEUS_VOICE.get(), chunk));
            rate = pcm.rate();
            parts.add(pcm.samples());
        }
        return new VoiceFx.Pcm(VoiceFx.join(parts, rate / 8), rate);
    }

    /** One line in, one WAV out: Piper prints the path of each file it writes. */
    private static synchronized VoiceFx.Pcm synthesize(String words) throws IOException {
        Path model = FMLPaths.GAMEDIR.get().resolve(Config.RYUK_MODEL.get());
        double speed = Config.RYUK_SPEED.get();
        if (piper == null || !piper.isAlive() || !(model + "@" + speed).equals(piperVoice)) {
            if (piper != null) {
                piper.destroy(); // the voice settings changed
            }
            startPiper(model, speed);
            piperVoice = model + "@" + speed;
        }
        piperIn.write(words);
        piperIn.newLine();
        piperIn.flush();
        String path = piperOut.readLine();
        if (path == null) {
            piper = null;
            throw new IOException("piper exited, see logs/piper.log");
        }
        Path wav = Path.of(path.strip());
        byte[] bytes = Files.readAllBytes(wav);
        Files.deleteIfExists(wav);
        return VoiceFx.readWav(bytes);
    }

    private static void startPiper(Path model, double speed) throws IOException {
        Path game = FMLPaths.GAMEDIR.get();
        if (System.getProperty("os.name", "").startsWith("Windows")) { // the voice inside the jar: Piper for Windows
            net.minecraftforge.forgespi.language.IModFileInfo mod = net.minecraftforge.fml.ModList.get().getModFileById(AiCompanion.MODID);
            Tts.unpack(mod.getFile().findResource("aicompanion_voice"), game.resolve("aicompanion"), mod.getMods().get(0).getVersion().toString());
        }
        Path exe = game.resolve(Config.TTS_PIPER.get());
        if (!Files.isRegularFile(exe) || !Files.isRegularFile(model)) {
            throw new FileNotFoundException("missing " + (Files.isRegularFile(exe) ? model : exe));
        }
        Path out = Path.of(System.getProperty("java.io.tmpdir"), "aicompanion-tts");
        Files.createDirectories(out);
        // the voice's config is passed explicitly: next to an 8.3 name like EN_US-~1.ONN piper wouldn't find "<model>.json"
        ProcessBuilder pb = new ProcessBuilder(plain(exe), "--model", plain(model), "--config", plain(Path.of(model + ".json")),
                "--espeak_data", plain(exe.resolveSibling("espeak-ng-data")), "--output_dir", plain(out),
                "--length_scale", String.valueOf(speed), "--sentence_silence", "0.15");
        pb.directory(exe.getParent().toFile());
        pb.redirectError(ProcessBuilder.Redirect.appendTo(game.resolve("logs").resolve("piper.log").toFile()));
        Process p = pb.start();
        Charset ansi = Charset.forName(System.getProperty("sun.jnu.encoding", "UTF-8"));
        piperIn = new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8)); // espeak wants UTF-8 text
        piperOut = new BufferedReader(new InputStreamReader(p.getInputStream(), ansi));
        piper = p;
    }

    /**
     * piper.exe and espeak-ng read paths in the ANSI code page: Cyrillic folders ("Проекты") make them crash or fail.
     * Windows keeps an all-ASCII 8.3 alias for every path (C:\Users\MSIGAM~1\...), so they get that one.
     */
    private static String plain(Path path) {
        String p = path.toAbsolutePath().toString();
        if (p.chars().allMatch(c -> c < 128) || !System.getProperty("os.name", "").startsWith("Windows")) {
            return p;
        }
        char[] buf = new char[2048];
        int n = Kernel32.INSTANCE.GetShortPathName(p, buf, buf.length);
        String shortPath = n > 0 && n < buf.length ? new String(buf, 0, n) : p;
        if (!shortPath.chars().allMatch(c -> c < 128)) {
            throw new IllegalStateException("piper can't read non-Latin paths and this disk has no 8.3 short names: "
                    + "move the aicompanion folder to a Latin-only path (" + p + ")");
        }
        return shortPath;
    }

    private static void play(int entityId, short[] voice, int rate, float[] mouth) {
        Minecraft mc = Minecraft.getInstance();
        Entity ryuk = mc.level == null ? null : mc.level.getEntity(entityId);
        if (ryuk == null) {
            return;
        }
        mc.getSoundManager().play(new VoiceSound(ryuk, voice, rate));
        SPEAKING.put(entityId, new Speech(mouth, System.nanoTime()));
    }

    /** Streams the finished PCM from memory and follows him as he flies. */
    private static final class VoiceSound extends AbstractTickableSoundInstance {
        private final Entity source;
        private final short[] samples;
        private final int rate;

        VoiceSound(Entity source, short[] samples, int rate) {
            super(AiCompanion.RYUK_VOICE.get(), SoundSource.VOICE, SoundInstance.createUnseededRandom());
            this.source = source;
            this.samples = samples;
            this.rate = rate;
            this.volume = Config.TTS_VOLUME.get().floatValue();
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            tick();
        }

        @Override
        public void tick() {
            if (source.isRemoved()) {
                stop();
                return;
            }
            x = source.getX();
            y = source.getY() + 1.9;
            z = source.getZ();
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
            ByteBuffer data = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            data.asShortBuffer().put(samples);
            return CompletableFuture.completedFuture(new PcmStream(data, rate));
        }
    }

    private record PcmStream(ByteBuffer data, int rate) implements AudioStream {
        @Override
        public AudioFormat getFormat() {
            return new AudioFormat(rate, 16, 1, true, false);
        }

        @Override
        public ByteBuffer read(int size) {
            int n = Math.min(size, data.remaining()) & ~1;
            ByteBuffer out = BufferUtils.createByteBuffer(n); // OpenAL wants direct memory
            for (int i = 0; i < n; i++) {
                out.put(data.get());
            }
            out.flip();
            return out;
        }

        @Override
        public void close() {
        }
    }
}
