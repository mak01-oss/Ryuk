package ru.maksim.aicompanion.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import ru.maksim.aicompanion.AiCompanion;
import ru.maksim.aicompanion.Config;
import ru.maksim.aicompanion.Stt;

/** Push to talk: hold V, speak, let go. The words go to chat as if typed, and Ryuk answers chat. */
@Mod.EventBusSubscriber(modid = AiCompanion.MODID, value = Dist.CLIENT)
public final class Voice {
    static final KeyMapping TALK = new KeyMapping("key.aicompanion.talk", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V, "key.categories.aicompanion");
    private static final int RATE = 16000, MAX_SECONDS = 30;
    private static final float SILENCE = 0.012F; // loudest 100 ms below this: the mic is muted or nothing was said
    private static volatile boolean recording;
    private static volatile float level, peak;
    @Nullable
    private static volatile String status;
    private static volatile long statusUntil;

    private Voice() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean held = TALK.isDown() && mc.player != null && mc.screen == null;
        if (held && !recording) {
            start();
        } else if (!held && recording) {
            recording = false; // the recorder thread finishes the take
        }
    }

    private static void start() {
        ru.maksim.aicompanion.Provider chat = ru.maksim.aicompanion.Provider.of(Config.API_URL.get());
        if (Config.STT_API_KEY.get().isBlank() && chat != ru.maksim.aicompanion.Provider.GROQ && chat != ru.maksim.aicompanion.Provider.OPENAI) {
            show(I18n.get("aicompanion.voice.needs_groq"), 7000); // Claude's key (or Gemini's...) can't hear: a free Groq key can
            return;
        }
        AudioFormat format = new AudioFormat(RATE, 16, 1, true, false);
        TargetDataLine line;
        try {
            line = AudioSystem.getTargetDataLine(format);
            line.open(format, RATE / 5);
            line.start();
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            show(I18n.get("aicompanion.voice.mic_unavailable", e.getMessage()), 5000);
            return;
        }
        recording = true;
        peak = 0;
        show(I18n.get("aicompanion.voice.speak"), Long.MAX_VALUE);
        Thread recorder = new Thread(() -> record(line), "aicompanion-mic");
        recorder.setDaemon(true);
        recorder.start();
    }

    private static void record(TargetDataLine line) {
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        byte[] chunk = new byte[RATE / 10 * 2]; // 100 ms
        while (recording && pcm.size() < RATE * 2 * MAX_SECONDS) {
            int n = line.read(chunk, 0, chunk.length);
            pcm.write(chunk, 0, n);
            level = Stt.rms(chunk, n);
            peak = Math.max(peak, level);
        }
        recording = false;
        line.stop();
        line.close();
        level = 0;
        if (pcm.size() < RATE * 2 * 4 / 10) { // under 0.4 s: a tap, not speech
            show(null, 0);
            return;
        }
        if (peak < SILENCE) {
            show(I18n.get("aicompanion.voice.silence"), 4000);
            return;
        }
        String key = Config.speechKey();
        if (key.isBlank()) {
            show(I18n.get("aicompanion.voice.no_key"), 5000);
            return;
        }
        show(I18n.get("aicompanion.voice.recognizing"), Long.MAX_VALUE);
        byte[] wav = Stt.wav(pcm.toByteArray(), RATE);
        String url = Config.STT_URL.get(), model = Config.STT_MODEL.get(), language = Config.sttLanguage();
        CompletableFuture.supplyAsync(() -> Stt.transcribe(url, key, model, language, wav), Stt.EXECUTOR)
                .whenComplete((text, error) -> Minecraft.getInstance().execute(() -> heard(text, error)));
    }

    private static void heard(@Nullable String text, @Nullable Throwable error) {
        Minecraft mc = Minecraft.getInstance();
        if (error != null) {
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            AiCompanion.LOGGER.warn("Speech to text failed", cause);
            show(ru.maksim.aicompanion.Llm.explain(cause).equals("aicompanion.msg.key_refused") ? I18n.get("aicompanion.voice.key_refused")
                    : I18n.get("aicompanion.voice.failed", cause.getMessage()), 7000);
            return;
        }
        String said = Stt.clean(text == null ? "" : text);
        if (said.isEmpty() || mc.player == null) {
            show(I18n.get("aicompanion.voice.unheard"), 2500);
            return;
        }
        show(null, 0);
        mc.player.connection.sendChat(said.length() > 256 ? said.substring(0, 256) : said);
    }

    private static void show(@Nullable String text, long millis) {
        status = text;
        statusUntil = millis == Long.MAX_VALUE ? Long.MAX_VALUE : System.currentTimeMillis() + millis;
    }

    private static void render(ForgeGui gui, GuiGraphics g, float partialTick, int width, int height) {
        String s = status;
        if (s == null || System.currentTimeMillis() > statusUntil) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        int w = font.width(s) + (recording ? 28 : 16), x = (width - w) / 2, y = 6;
        g.fill(x, y, x + w, y + 18, 0xA0000000);
        int textX = x + 8;
        if (recording) {
            boolean on = System.currentTimeMillis() / 400 % 2 == 0;
            g.fill(x + 7, y + 6, x + 13, y + 12, on ? 0xFFE53935 : 0xFF8E2421); // the red "rec" dot
            g.fill(x + 2, y + 16, x + 2 + (int) (Math.min(1F, level * 8) * (w - 4)), y + 17, 0xFFE53935); // loudness
            textX = x + 18;
        }
        g.drawString(font, s, textX, y + 5, 0xFFFFFFFF, false);
    }

    @Mod.EventBusSubscriber(modid = AiCompanion.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) {
            event.register(TALK);
        }

        @SubscribeEvent
        public static void overlay(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("voice", Voice::render);
        }
    }
}
