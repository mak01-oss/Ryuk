package ru.maksim.aicompanion;

import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Speech to text through an OpenAI-compatible /audio/transcriptions endpoint (Groq Whisper by default). Plain Java. */
public final class Stt {
    public static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "aicompanion-stt");
        t.setDaemon(true);
        return t;
    });
    /** Whisper fills silence and noise with credits from the subtitles it learned on. */
    private static final List<String> HALLUCINATIONS = List.of("продолжение следует", "субтитр", "спасибо за просмотр",
            "редактор", "корректор", "подписывайтесь", "amara.org", "dimatorzok", "thanks for watching");

    private Stt() {
    }

    /** 16-bit mono little-endian PCM to a WAV file. */
    public static byte[] wav(byte[] pcm, int sampleRate) {
        ByteBuffer b = ByteBuffer.allocate(44 + pcm.length).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + pcm.length).put("WAVE".getBytes(StandardCharsets.US_ASCII));
        b.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(sampleRate).putInt(sampleRate * 2).putShort((short) 2).putShort((short) 16);
        b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcm.length).put(pcm);
        return b.array();
    }

    /** Blocking: returns the recognised text, trimmed. Run it off the render thread. */
    public static String transcribe(String url, String apiKey, String model, String language, byte[] wav) {
        String boundary = "aicompanion" + System.nanoTime();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        field(body, boundary, "model", model);
        if (!language.isBlank()) {
            field(body, boundary, "language", language);
        }
        field(body, boundary, "response_format", "json");
        field(body, boundary, "temperature", "0");
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\n"
                + "Content-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(wav);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        HttpResponse<String> response = Llm.sendWithRetry(request);
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + Llm.errorText(response.body()));
        }
        return JsonParser.parseString(response.body()).getAsJsonObject().get("text").getAsString().strip();
    }

    private static void field(ByteArrayOutputStream body, String boundary, String name, String value) {
        body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    /** Empty when nothing was really said: no letters, or one of Whisper's stock phantom phrases. */
    public static String clean(String text) {
        String low = text.toLowerCase(Locale.ROOT);
        for (String h : HALLUCINATIONS) {
            if (low.contains(h)) {
                return "";
            }
        }
        return text.codePoints().anyMatch(Character::isLetter) ? text.strip() : "";
    }

    /** Loudness of 16-bit little-endian PCM, 0..1. */
    public static float rms(byte[] pcm, int length) {
        long sum = 0;
        int samples = length / 2;
        for (int i = 0; i + 1 < length; i += 2) {
            int s = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
            sum += (long) s * s;
        }
        return samples == 0 ? 0 : (float) (Math.sqrt((double) sum / samples) / 32768.0);
    }
}
