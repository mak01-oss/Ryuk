package ru.maksim.aicompanion;

import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** English speech: Ryuk's own Piper voice, or Groq's Orpheus (OpenAI-compatible /audio/speech). Plain Java, unit-testable. */
public final class Tts {
    /** Orpheus refuses longer input. */
    public static final int MAX_CHUNK = 200;

    private Tts() {
    }

    /**
     * Ryuk's voice from the mod's jar ({@code packed}: its aicompanion_voice folder) into {@code dir}, once per mod
     * {@code version}: the mark file says which one is there already. A dev run only has the notices to unpack.
     */
    public static void unpack(Path packed, Path dir, String version) throws IOException {
        Path mark = dir.resolve("voice.version");
        if (!Files.isDirectory(packed) || Files.isRegularFile(mark) && Files.readString(mark).equals(version)) {
            return;
        }
        try (Stream<Path> files = Files.walk(packed)) { // parents first
            for (Path p : (Iterable<Path>) files::iterator) {
                Path to = dir.resolve(packed.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(to);
                } else {
                    Files.copy(p, to, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        Files.writeString(mark, version);
    }

    /** Ryuk's stage directions become Orpheus vocal directions; the emotion sets the opening one. */
    public static String direct(String text, String emotion) {
        String t = text.replaceAll("(?i)\\*\\s*(chuckles?|snickers?|heh|hehe|laughs?|grins?|smirks?)\\s*\\*", " [dark chuckle] ")
                .replaceAll("(?i)\\*\\s*whispers?\\s*\\*", " [gravelly whisper] ")
                .replaceAll("(?i)\\*\\s*sighs?\\s*\\*", " [exasperated sigh] ");
        t = VoiceFx.speakable(t); // other *directions* and emoji are not read out
        String lead = switch (emotion) {
            case "happy" -> "[dark chuckle]";
            case "sad" -> "[exasperated sigh]";
            case "angry" -> "[gravelly whisper]";
            case "surprised" -> "[dramatic gasp]";
            case "scared" -> "[whisper]";
            default -> "";
        };
        return lead.isEmpty() || t.startsWith("[") ? t : lead + " " + t;
    }

    /**
     * English for Ryuk's own Piper voice; its dataset went through the same rules (check_dataset.py piper_text).
     * Laughs, sighs and crunches become the sounds he learned, other *directions* go. espeak drops an ellipsis and
     * glues the words around it (a full stop keeps the pause), and spells "Hm" and "Mmm" letter by letter.
     */
    public static String piper(String text) {
        String t = text.replaceAll("(?i)\\*\\s*(chuckles?|snickers?|heh|hehe|laughs?|grins?|smirks?)\\s*\\*", " Heh heh heh. ")
                .replaceAll("(?i)\\*\\s*sighs?\\s*\\*", " Haaah. ")
                .replaceAll("(?i)\\*\\s*crunch(es)?\\s*\\*", " Crunch. ");
        t = VoiceFx.speakable(t).replaceAll("\\.{2,}|…", ".");
        return HUM.matcher(t).replaceAll(m -> Character.isUpperCase(m.group().charAt(0)) ? "Hmm" : "hmm");
    }

    private static final Pattern HUM = Pattern.compile("\\b[HhMm]m+\\b");

    /** Cuts at sentence ends, then at spaces, so that each piece fits one request. */
    public static List<String> chunks(String text, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        for (String sentence : text.split("(?<=[.!?…])\\s+")) {
            for (String part : splitLong(sentence, max)) {
                if (piece.length() > 0 && piece.length() + 1 + part.length() > max) {
                    out.add(piece.toString());
                    piece.setLength(0);
                }
                piece.append(piece.length() > 0 ? " " : "").append(part);
            }
        }
        if (piece.length() > 0) {
            out.add(piece.toString());
        }
        return out;
    }

    private static List<String> splitLong(String sentence, int max) {
        List<String> parts = new ArrayList<>();
        String rest = sentence.strip();
        while (rest.length() > max) {
            int cut = rest.lastIndexOf(' ', max);
            cut = cut > 0 ? cut : max;
            parts.add(rest.substring(0, cut).strip());
            rest = rest.substring(cut).strip();
        }
        if (!rest.isEmpty()) {
            parts.add(rest);
        }
        return parts;
    }

    /** Blocking: one request, the WAV back. Run it off the render thread. */
    public static byte[] speech(String url, String apiKey, String model, String voice, String input) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("voice", voice);
        body.addProperty("input", input);
        body.addProperty("response_format", "wav");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<byte[]> response = Llm.sendBytesWithRetry(request);
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": "
                    + Llm.errorText(new String(response.body(), StandardCharsets.UTF_8)));
        }
        return response.body();
    }
}
