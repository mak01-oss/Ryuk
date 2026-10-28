package ru.maksim.aicompanion;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The chat client (the OpenAI format, and Anthropic's own Messages API for Claude), the system prompt and reply parsing.
 * Plain Java (no Minecraft classes): unit-testable.
 */
public final class Llm {
    public static final List<String> EMOTIONS = List.of("neutral", "happy", "sad", "angry", "surprised", "scared", "thinking");
    /** What the model may answer with: "thinking" is the face while it is waiting for the model. */
    static final List<String> REPLY_EMOTIONS = EMOTIONS.stream().filter(e -> !e.equals("thinking")).toList();
    static final int MAX_MESSAGE = 400;
    /** "кукуку", "ку-ку-ку!": a bad transliteration of his Japanese snicker that the models love to repeat. */
    private static final Pattern KUKU = Pattern.compile("(?iu)(?<!\\p{L})(?:ку[\\s\\-–—.,!…]*){2,}(?!\\p{L})");
    /** Broken JSON (an unescaped quote inside the line, say): greedy up to the quote that ends the message field. */
    private static final Pattern LOOSE_MESSAGE = Pattern.compile("(?s)\"message\"\\s*:\\s*\"(.*)\"\\s*(?:,\\s*\"(?:actions|emotion)\"|\\})");
    private static final Pattern LOOSE_EMOTION = Pattern.compile("\"emotion\"\\s*:\\s*\"(\\w+)\"");

    // ponytail: one worker thread = requests are answered in order, one at a time; use a pool if many players talk at once
    public static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "aicompanion-llm");
        t.setDaemon(true);
        return t;
    });

    // HTTP/1.1: local OpenAI-compatible servers (LiteLLM/uvicorn) choke on the h2c upgrade of a POST
    static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public record Msg(String role, String content) {
    }

    public record Action(String name, Map<String, String> args) {
        public String arg(String key) {
            return args.getOrDefault(key, "");
        }
    }

    public record Reply(String emotion, String message, List<Action> actions) {
        public Reply(String emotion, String message) {
            this(emotion, message, List.of());
        }

        public String toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("emotion", emotion);
            o.addProperty("message", message);
            JsonArray list = new JsonArray();
            for (Action a : actions) {
                JsonObject ao = new JsonObject();
                ao.addProperty("name", a.name());
                JsonObject args = new JsonObject();
                a.args().forEach(args::addProperty);
                ao.add("args", args);
                list.add(ao);
            }
            o.add("actions", list);
            return o.toString();
        }
    }

    private static final Map<String, String> PERSONAS = new ConcurrentHashMap<>();

    private Llm() {
    }

    /** The character sheet for the language, data/aicompanion/ryuk/persona_en.md or persona_ru.md inside the mod jar. */
    public static String persona(boolean english) {
        return PERSONAS.computeIfAbsent(english ? "en" : "ru", lang -> {
            try (InputStream in = Llm.class.getResourceAsStream("/data/aicompanion/ryuk/persona_" + lang + ".md")) {
                return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public static final int MAX_NOTES = 24, MAX_NOTE = 140; // his memory of the human: every note goes out with every request

    /** The reply's "remember": one new thing about the human worth keeping for days, or "" (none, or no JSON). */
    public static String note(String content) {
        JsonObject o = jsonIn(content);
        if (o != null && o.has("remember") && o.get("remember").isJsonPrimitive()) {
            return o.get("remember").getAsString().replaceAll("§.?", "").replaceAll("\\s+", " ").strip();
        }
        return "";
    }

    /**
     * Keeps a note. One that says mostly the same as an older one (two thirds of its word stems) takes that one's place:
     * a fact told again stays fresh, a changed one is updated. Past MAX_NOTES the oldest fades.
     */
    public static boolean addNote(java.util.List<String> notes, String note) {
        return addNote(notes, note, MAX_NOTES);
    }

    public static boolean addNote(java.util.List<String> notes, String note, int max) {
        String n = note.length() > MAX_NOTE ? note.substring(0, MAX_NOTE - 1) + "…" : note;
        java.util.Set<String> mine = stems(n);
        if (mine.isEmpty()) {
            return false;
        }
        notes.removeIf(old -> { // two thirds of the longer of the two: a bare "Maks" doesn't wipe every note about Maks
            java.util.Set<String> theirs = stems(old);
            return mine.stream().filter(theirs::contains).count() * 3 >= Math.max(mine.size(), theirs.size()) * 2L;
        });
        notes.add(n);
        while (notes.size() > max) {
            notes.remove(0);
        }
        return true;
    }

    /** The words of a note, cut to five letters ("creepers", "creeper" -> "creep"), the short ones left out. */
    private static java.util.Set<String> stems(String s) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String w : s.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (w.length() >= 3) {
                out.add(w.length() > 5 ? w.substring(0, 5) : w);
            }
        }
        return out;
    }

    /** "[Event: ...]" lines are things he noticed himself; the word follows the language he speaks. */
    public static String eventLine(boolean english, String event) {
        return "[" + (english ? "Event" : "Событие") + ": " + event + "]";
    }

    /** The headings around the moment's context in the system prompt: Claude gets that part with the newest message instead. */
    static final String SEE = "WHAT YOU SEE RIGHT NOW (fresh, trust it):", ACTIONS = "ACTIONS, only when the player asks for one";

    /**
     * For a strong model (Claude, GPT, Gemini, the big ones via OpenRouter): notes on the craft that a small model would
     * only trip over. They go after the rules, before the reply format.
     */
    static final String CRAFT = """
            CRAFT (you run on a strong model: use it):
            - Be specific: react to the concrete thing at hand (what they just said or did, the story's state, something you remember), not to the situation in general.
            - Play the long game: call back to what you remember and to earlier lines of this talk; keep a running joke alive; let your mood carry over from one reply to the next.
            - Subtext over explanation: tease, imply, leave things unsaid; a dry understatement beats a speech.
            - You know Death Note well (the rules of "How to Use It", Light, L, Misa, Rem, Near, Mello): when it fits, a sharp parallel or a rule quoted exactly, never a plot summary and never what will happen in this story.
            - Rhythm: vary the length and the first words; never open two replies the same way; sometimes a single word or a gesture (emote) is the whole reply.
            - Brevity is part of him: even asked to describe or explain something, five short sentences at the very most; pick the best details, drop the rest.
            """;

    /** The actions and gestures Ryuk's reply may name (the system prompt's list): the schema holds Claude to them. */
    static final List<String> ACTION_NAMES = List.of("follow", "stay", "come", "take_apple", "emote", "eye_deal", "look_for_cameras", "scout",
            "dice", "show_way", "renounce", "handstand", "spin", "show_wings", "dance", "wave", "bow", "backflip", "clap", "shrug", "juggle",
            "stretch", "cackle", "sulk", "perch", "look_around");

    /** Ryuk's reply as a JSON schema (Anthropic's structured outputs): always one object, the emotion and actions from the lists. */
    static JsonObject replySchema() {
        JsonObject args = object();
        args.getAsJsonObject("properties").add("name", type("string"));
        JsonObject action = object();
        JsonObject name = type("string");
        JsonArray names = new JsonArray();
        ACTION_NAMES.forEach(names::add);
        name.add("enum", names);
        action.getAsJsonObject("properties").add("name", name);
        action.getAsJsonObject("properties").add("args", args);
        JsonArray needs = new JsonArray();
        needs.add("name");
        action.add("required", needs);
        JsonObject list = type("array");
        list.add("items", action);
        JsonObject emotion = type("string");
        JsonArray emotions = new JsonArray();
        REPLY_EMOTIONS.forEach(emotions::add);
        emotion.add("enum", emotions);
        JsonObject reply = object();
        JsonObject fields = reply.getAsJsonObject("properties");
        fields.add("emotion", emotion);
        fields.add("message", type("string"));
        fields.add("actions", list);
        fields.add("remember", type("string"));
        JsonArray all = new JsonArray();
        List.of("emotion", "message", "actions", "remember").forEach(all::add);
        reply.add("required", all);
        return reply;
    }

    private static JsonObject type(String type) {
        JsonObject o = new JsonObject();
        o.addProperty("type", type);
        return o;
    }

    private static JsonObject object() {
        JsonObject o = type("object");
        o.add("properties", new JsonObject());
        o.addProperty("additionalProperties", false);
        return o;
    }

    /** True for the providers whose models can carry the {@link #CRAFT} notes. */
    public static boolean strong(String chatUrl) {
        Provider p = Provider.of(chatUrl);
        return p == Provider.CLAUDE || p == Provider.OPENAI || p == Provider.GEMINI || p == Provider.OPENROUTER;
    }

    public static String systemPrompt(String name, String extraPersona, String context, boolean english) {
        return systemPrompt(name, extraPersona, context, english, false);
    }

    public static String systemPrompt(String name, String extraPersona, String context, boolean english, boolean strong) {
        return """
                %s
                %s
                Your name is %s. You live in the player's Minecraft world as a character, not an assistant.
                Every player message starts with their nickname. Lines like %s are things you noticed yourself: react briefly to that, in character.
                Don't bring up trivia from WHAT YOU SEE (what they hold or don't, the animals and monsters around, the weather) unless asked.

                WHAT YOU SEE RIGHT NOW (fresh, trust it):
                %s

                ACTIONS, only when the player asks for one (never invent others):
                - follow: fly after the player (what you do by default)
                - stay: hover right here and wait
                - come: fly to the player now
                - take_apple: take one apple from the player's inventory, only if they offer it right now and have one
                - emote, args {"name": "handstand" | "spin" | "show_wings" | "dance" | "wave" | "bow" | "backflip" | "clap" | "shrug" | "juggle" | "stretch" | "cackle" | "sulk" | "perch" | "look_around"}
                - eye_deal: put the shinigami eye deal to your human (half of their remaining life for the eyes); only if they ask for the eyes, or agree after you named the price
                - look_for_cameras: fly through the walls and find L's cameras and watchers; a favour that costs an apple (take_apple first, in the same reply)
                - scout: fly off through the walls and look around, then report; a favour that costs an apple (take_apple first, in the same reply)
                - dice: a game of chō-han (even or odd, two dice under your skull cup) for apples; when your human wants to play, gamble or bet; the table opens for them, the dice decide (don't ask about their apples first: the table shows what they have)
                - show_way: when the player asks where to go, what to do next or for coordinates: an arrow and the place's coordinates appear for them (free); say plainly where and why
                - renounce: your human gives up ownership of the notebook: they forget it and you, lose the eyes, and you leave; only if they clearly say they give it up

                MEMORY: what you noted about your human on earlier days is in the context ("What you remember"). Bring it up now and
                then, the way old acquaintances do, and let it colour how you treat them. Never make memories up: only what is there or
                in this conversation; what you don't remember, you don't. When your human tells or does something worth
                keeping for days (their name, what they like or fear, a promise, a joke between you, a big deed), put one short new note
                about it in "remember" (a few words, in your language). Only about your human: who they are, what they said, felt,
                promised or did; never the news, other people or what you just saw. If it changes something you remember, write the new
                version. Otherwise leave it empty; never note what you already remember.
                %s
                Reply ONLY with a JSON object, as plain text (no XML, no tool-call tags: actions go inside the JSON):
                {"emotion": "<one of: %s>", "message": "<your line, in %s>", "actions": [{"name": "<action>", "args": {}}], "remember": ""}
                No actions: "actions": [].
                """.formatted(persona(english), extraPersona.isBlank() ? "" : "Also: " + extraPersona, name,
                eventLine(english, "..."), context, strong ? "\n" + CRAFT : "", String.join(", ", REPLY_EMOTIONS), english ? "English" : "Russian");
    }

    /** A 429 that retrying soon won't fix (the free tier's tokens per day, say). */
    static final class RateLimited extends IllegalStateException {
        final long waitMillis;

        RateLimited(String message, double waitSeconds) {
            super(message);
            waitMillis = (long) (waitSeconds * 1000);
        }
    }

    /** Every reply's token count, as the provider reports it (model, usage JSON): the game logs it, the live eval adds it up. */
    public static volatile java.util.function.BiConsumer<String, JsonObject> onUsage = (model, usage) -> { };

    /** Models out of tokens, until when (ms): each has its own daily limit on Groq, so the next one in line answers. */
    private static final Map<String, Long> SPENT = new ConcurrentHashMap<>();

    /** Blocking call through the first model in line that still has tokens; the same request goes to the next on a long 429. */
    public static String complete(String url, String apiKey, List<String> models, List<Msg> messages) {
        if (System.getenv("AICOMPANION_OFFLINE") != null) { // long autotests: every path runs, no tokens spent
            return "{\"message\":\"(offline)\",\"emotion\":\"neutral\",\"actions\":[]}";
        }
        RateLimited last = null;
        for (String model : models) {
            if (SPENT.getOrDefault(model, 0L) > System.currentTimeMillis()) {
                continue;
            }
            try {
                return complete(url, apiKey, model, messages);
            } catch (RateLimited e) {
                SPENT.put(model, System.currentTimeMillis() + e.waitMillis);
                last = e;
            }
        }
        throw last != null ? last : new IllegalStateException("every model is out of tokens for now");
    }

    /**
     * Like {@link #complete(String, String, List, List)}, but a reply that breaks character (an assistant's refusal, the
     * other language) is asked of the next model in line, once; if that one fails too, the first reply stands.
     */
    public static String completeInCharacter(String url, String apiKey, List<String> models, List<Msg> messages, boolean english) {
        if (System.getenv("AICOMPANION_OFFLINE") != null) {
            return complete(url, apiKey, models, messages);
        }
        String first = null;
        RateLimited last = null;
        for (String model : models) {
            if (SPENT.getOrDefault(model, 0L) > System.currentTimeMillis()) {
                continue;
            }
            try {
                String content = complete(url, apiKey, model, messages);
                if (fits(parseReply(content).message(), english)) {
                    return content;
                }
                AiCompanion.LOGGER.info("{} broke character, asking the next model: {}", model, content);
                if (first != null) {
                    return first; // two tries are enough: better a reply than none
                }
                first = content;
            } catch (RateLimited e) {
                SPENT.put(model, System.currentTimeMillis() + e.waitMillis);
                last = e;
            }
        }
        if (first != null) {
            return first;
        }
        throw last != null ? last : new IllegalStateException("every model is out of tokens for now");
    }

    /** A line of the character: not an assistant's refusal, and in the language asked for (a name in Latin letters is fine). */
    public static boolean fits(String message, boolean english) {
        if (refusal(message) || MARKUP.matcher(message).find()) { // tags instead of a line: Claude's tool calls leak so
            return false;
        }
        int cyrillic = 0, latin = 0;
        for (char c : message.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC) {
                cyrillic++;
            } else if (c < 128 && Character.isLetter(c)) {
                latin++;
            }
        }
        return english ? cyrillic <= latin : cyrillic > 0 || latin < 4;
    }

    /** The models this key may use (GET .../models beside the chat URL): checks the key without spending a token. */
    public static List<String> models(Provider p, String chatUrl, String apiKey) {
        HttpRequest.Builder ask;
        if (p == Provider.CLAUDE) { // Anthropic's own listing wants its own headers
            ask = HttpRequest.newBuilder(URI.create("https://api.anthropic.com/v1/models?limit=1000"))
                    .header("x-api-key", apiKey).header("anthropic-version", "2023-06-01");
        } else if (p == Provider.OPENROUTER) { // the list is open to all: the key is checked on its own page first
            HttpResponse<String> key = send(HttpRequest.newBuilder(URI.create("https://openrouter.ai/api/v1/key")).timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (key.statusCode() == 401 || key.statusCode() == 403) {
                throw new KeyRefused(key.statusCode(), errorText(key.body()));
            }
            ask = HttpRequest.newBuilder(URI.create("https://openrouter.ai/api/v1/models"));
        } else {
            ask = HttpRequest.newBuilder(URI.create(chatUrl.replaceAll("/chat/completions/?$", "") + "/models"))
                    .header("Authorization", "Bearer " + apiKey);
        }
        HttpResponse<String> response = send(ask.timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 429) {
            throw new RateLimited("HTTP 429: " + errorText(response.body()), 60);
        }
        if (response.statusCode() / 100 != 2) {
            throw new KeyRefused(response.statusCode(), errorText(response.body()));
        }
        List<String> ids = new ArrayList<>();
        for (JsonElement e : JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("data")) {
            ids.add(e.getAsJsonObject().get("id").getAsString().replaceFirst("^models/", "")); // Gemini says "models/gemini-..."
        }
        return chatModels(p, ids);
    }

    /** The ones that chat, sorted; OpenRouter's hundreds cut down to the families the provider prefers. */
    public static List<String> chatModels(Provider p, List<String> ids) {
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            if (!id.matches("(?i).*(whisper|tts|orpheus|guard|embed|image|imagen|dall-e|audio|realtime|transcri|moderation|search|aqa|veo|lyria|babbage|davinci).*")
                    && (p != Provider.OPENROUTER || p.prefer.stream().anyMatch(re -> java.util.regex.Pattern.compile(re).matcher(id).find()))) {
                out.add(id);
            }
        }
        java.util.Collections.sort(out);
        return out.size() > 60 ? out.subList(0, 60) : out;
    }

    /** What to talk with, best first (up to three): for each favourite the newest model that fits it; else the first there is. */
    public static List<String> pick(Provider p, List<String> available) {
        List<String> out = new ArrayList<>();
        for (String pattern : p.prefer) {
            java.util.regex.Pattern re = java.util.regex.Pattern.compile(pattern);
            available.stream().filter(id -> re.matcher(id).find() && !out.contains(id)).max(java.util.Comparator.naturalOrder()).ifPresent(out::add);
            if (out.size() == 3) {
                break;
            }
        }
        if (out.isEmpty() && !available.isEmpty()) {
            out.add(available.get(0));
        }
        return out;
    }

    /**
     * A 400 naming a field this provider won't take (OpenAI's newer models: max_tokens, temperature): the request fixed
     * for one more try, max_tokens renamed, the others left out. False: nothing left to change.
     */
    public static boolean adapt(JsonObject body, String error) {
        String e = error.toLowerCase(Locale.ROOT);
        if (e.contains("max_tokens") && body.has("max_tokens")) {
            body.add("max_completion_tokens", body.remove("max_tokens"));
            return true;
        }
        for (String field : List.of("temperature", "response_format", "reasoning_effort")) {
            if (e.contains(field) && body.has(field)) {
                body.remove(field);
                return true;
            }
        }
        return false;
    }

    /** The provider turned the key away (401, 403) or the request was wrong. */
    public static final class KeyRefused extends RuntimeException {
        public final int status;

        KeyRefused(int status, String text) {
            super("HTTP " + status + ": " + text);
            this.status = status;
        }
    }

    /** What went wrong, as a lang key for the player: the key, the day's limit, the network, or something else. */
    public static String explain(Throwable error) {
        Throwable e = error;
        while (e.getCause() != null && !(e instanceof RateLimited) && !(e instanceof KeyRefused) && !(e instanceof IOException)) {
            e = e.getCause();
        }
        String text = String.valueOf(e.getMessage());
        if (e instanceof KeyRefused || text.startsWith("HTTP 401") || text.startsWith("HTTP 403")) {
            return "aicompanion.msg.key_refused";
        }
        if (e instanceof RateLimited || text.contains("out of tokens")) {
            return "aicompanion.msg.limit";
        }
        if (e instanceof IOException || e instanceof UncheckedIOException || e instanceof java.net.http.HttpTimeoutException) {
            return "aicompanion.msg.offline";
        }
        return "aicompanion.msg.llm_error";
    }

    /** Blocking call: returns the assistant's raw message content. Run it off the server thread. */
    public static String complete(String url, String apiKey, String model, List<Msg> messages) {
        if (Provider.of(url) == Provider.CLAUDE || url.endsWith("/v1/messages")) {
            return completeClaude(url.replaceFirst("/chat/completions/?$", "/messages"), apiKey, model, messages);
        }
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("temperature", 0.7); // higher made him drift off topic
        body.addProperty("max_tokens", 2048);
        // a quick answer matters more than deep thought in chat, and reasoning eats the free tier's tokens per minute
        if (model.startsWith("openai/gpt-oss")) {
            body.addProperty("reasoning_effort", "low");
        } else if (model.startsWith("qwen/qwen3")) {
            body.addProperty("reasoning_effort", "none");
        } else if (model.contains("gpt-5")) {
            body.addProperty("reasoning_effort", "minimal"); // a line in a chat, not a long think
        }
        JsonObject format = new JsonObject();
        format.addProperty("type", "json_object");
        body.add("response_format", format);
        JsonArray list = new JsonArray();
        for (Msg m : messages) {
            JsonObject o = new JsonObject();
            o.addProperty("role", m.role());
            o.addProperty("content", m.content());
            list.add(o);
        }
        body.add("messages", list);

        HttpResponse<String> response;
        do { // asked again without what a provider refuses (see adapt), a field at a time
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            response = sendWithRetry(request);
        } while (response.statusCode() == 400 && failedGeneration(response.body()) == null && adapt(body, errorText(response.body())));
        if (response.statusCode() == 429) {
            throw new RateLimited("HTTP 429: " + errorText(response.body()),
                    response.headers().firstValue("retry-after").map(Llm::seconds).orElse(60.0));
        }
        if (response.statusCode() / 100 != 2) {
            String rejected = failedGeneration(response.body());
            if (rejected != null) {
                return rejected; // Groq refused malformed JSON but sends what the model wrote: parseReply salvages the line
            }
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + errorText(response.body()));
        }
        JsonObject reply = JsonParser.parseString(response.body()).getAsJsonObject();
        if (reply.has("usage") && reply.get("usage").isJsonObject()) {
            onUsage.accept(model, reply.getAsJsonObject("usage"));
        }
        JsonElement content = reply.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").get("content");
        if (content == null || content.isJsonNull()) {
            throw new IllegalStateException("empty reply");
        }
        return content.getAsString();
    }

    /**
     * Claude through Anthropic's own Messages API: its OpenAI-format door can't cache. The character sheet and the rules
     * are cached, and so is the talk so far; the moment's context (WHAT YOU SEE) comes with the newest message, so each
     * request pays in full only for that and the new line.
     */
    static String completeClaude(String url, String apiKey, String model, List<Msg> messages) {
        JsonArray system = new JsonArray();
        String context = "";
        boolean ryuk = false; // his prompt (not a detective's, with a JSON of its own): his reply's schema goes with it
        List<Msg> turns = new ArrayList<>();
        for (Msg m : messages) {
            if (!m.role().equals("system")) {
                turns.add(m);
                continue;
            }
            String text = m.content();
            int see = text.indexOf(SEE), actions = text.indexOf(ACTIONS);
            if (see >= 0 && actions > see) {
                ryuk = true;
                context = text.substring(see, actions).trim();
                text = text.substring(0, see) + SEE + "\n(it comes at the top of the newest message)\n\n" + text.substring(actions);
            }
            system.add(textBlock(text, true));
        }
        if (turns.isEmpty() || !turns.get(0).role().equals("user")) {
            turns.add(0, new Msg("user", "...")); // the API wants the human first
        }
        JsonArray list = new JsonArray();
        for (int i = 0; i < turns.size(); i++) {
            Msg m = turns.get(i);
            boolean newest = i == turns.size() - 1;
            JsonObject turn = new JsonObject();
            turn.addProperty("role", m.role().equals("assistant") ? "assistant" : "user");
            JsonArray content = new JsonArray();
            content.add(textBlock(newest && !context.isEmpty() ? context + "\n\n" + m.content() : m.content(), i == turns.size() - 2));
            turn.add("content", content);
            list.add(turn);
        }
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", 1024); // no temperature: the newest Claude models refuse it (400), and their own is lively enough
        if (!system.isEmpty()) {
            body.add("system", system);
        }
        body.add("messages", list);
        if (ryuk) { // one JSON object, always: no text around it, no second try after it, no tool-call tags
            JsonObject format = new JsonObject();
            format.addProperty("type", "json_schema");
            format.add("schema", replySchema());
            JsonObject config = new JsonObject();
            config.add("format", format);
            body.add("output_config", config);
        }
        HttpResponse<String> response;
        do { // an older model without structured outputs: asked again without them
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            response = sendWithRetry(request);
        } while (response.statusCode() == 400 && body.has("output_config") && body.remove("output_config") != null);
        if (response.statusCode() == 429 || response.statusCode() == 529) { // the key's rate limit, or Anthropic overloaded: the next model
            throw new RateLimited("HTTP " + response.statusCode() + ": " + errorText(response.body()),
                    response.headers().firstValue("retry-after").map(Llm::seconds).orElse(30.0));
        }
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": " + errorText(response.body()));
        }
        JsonObject reply = JsonParser.parseString(response.body()).getAsJsonObject();
        if (reply.has("usage") && reply.get("usage").isJsonObject()) {
            onUsage.accept(model, reply.getAsJsonObject("usage"));
        }
        StringBuilder text = new StringBuilder();
        if (reply.has("content") && reply.get("content").isJsonArray()) {
            for (JsonElement block : reply.getAsJsonArray("content")) {
                if (block.isJsonObject() && block.getAsJsonObject().has("text")) {
                    text.append(block.getAsJsonObject().get("text").getAsString());
                }
            }
        }
        if (text.isEmpty()) {
            throw new IllegalStateException("empty reply");
        }
        return text.toString();
    }

    /** A text block for the Messages API; {@code cache}: the prompt up to here is worth keeping in Anthropic's cache. */
    private static JsonObject textBlock(String text, boolean cache) {
        JsonObject block = new JsonObject();
        block.addProperty("type", "text");
        block.addProperty("text", text.isBlank() ? "..." : text); // an empty block is refused
        if (cache) {
            JsonObject ephemeral = new JsonObject();
            ephemeral.addProperty("type", "ephemeral");
            block.add("cache_control", ephemeral);
        }
        return block;
    }

    static HttpResponse<String> sendWithRetry(HttpRequest request) {
        return withRetry(request, HttpResponse.BodyHandlers.ofString());
    }

    static HttpResponse<byte[]> sendBytesWithRetry(HttpRequest request) {
        return withRetry(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    /** Free tiers limit requests and tokens per minute: on 429 wait as told, once, instead of failing. */
    private static <T> HttpResponse<T> withRetry(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        HttpResponse<T> response = send(request, handler);
        if (response.statusCode() == 429) {
            double wait = response.headers().firstValue("retry-after").map(Llm::seconds).orElse(5.0);
            if (wait <= 20) {
                sleep((long) (wait * 1000) + 250);
                response = send(request, handler);
            }
        }
        return response;
    }

    private static double seconds(String header) {
        try {
            return Double.parseDouble(header.trim());
        } catch (NumberFormatException e) {
            return 5.0;
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        try {
            return HTTP.send(request, handler);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted", e);
        }
    }

    /**
     * The reply's JSON object, wherever it is: around it text, a code fence, tool-call tags; two of them (a model
     * correcting itself) give the last one with a message. Null when there is none.
     */
    @javax.annotation.Nullable
    static JsonObject jsonIn(String content) {
        String text = content.replaceAll("(?s)<think>.*?</think>", "");
        JsonObject found = null;
        for (int start = text.indexOf('{'); start >= 0; start = text.indexOf('{', start + 1)) {
            int depth = 0, end = -1;
            boolean quoted = false;
            for (int i = start; i < text.length() && end < 0; i++) {
                char ch = text.charAt(i);
                if (quoted) {
                    if (ch == '\\') {
                        i++;
                    } else if (ch == '"') {
                        quoted = false;
                    }
                } else if (ch == '"') {
                    quoted = true;
                } else if (ch == '{') {
                    depth++;
                } else if (ch == '}' && --depth == 0) {
                    end = i;
                }
            }
            if (end < 0) {
                continue;
            }
            try {
                JsonElement e = JsonParser.parseString(text.substring(start, end + 1));
                if (e.isJsonObject() && (found == null || !found.has("message") || e.getAsJsonObject().has("message"))) {
                    found = e.getAsJsonObject();
                }
                start = end; // the next one starts after this one
            } catch (RuntimeException ignored) {
                // not JSON from here: the next brace
            }
        }
        return found;
    }

    /** Tolerates <think> blocks, code fences and text around the JSON; unknown emotion -> neutral, non-JSON -> raw text. */
    public static Reply parseReply(String content) {
        String text = content.replaceAll("(?s)<think>.*?</think>", "").trim();
        JsonObject o = jsonIn(text);
        if (o != null && o.has("message")) {
            try {
                String emotion = o.has("emotion") ? o.get("emotion").getAsString().trim().toLowerCase(Locale.ROOT) : "neutral";
                String message = o.get("message").getAsString().trim();
                return new Reply(REPLY_EMOTIONS.contains(emotion) ? emotion : "neutral", message.isEmpty() ? "..." : message, actions(o));
            } catch (RuntimeException ignored) {
                // a field of the wrong kind: try to pull the fields out by hand below
            }
        }
        Matcher message = LOOSE_MESSAGE.matcher(text);
        if (message.find()) {
            Matcher emotion = LOOSE_EMOTION.matcher(text);
            String e = emotion.find() ? emotion.group(1).toLowerCase(Locale.ROOT) : "neutral";
            String m = message.group(1).replace("\\\"", "\"").replace("\\n", " ").replace("\\\\", "\\").trim();
            return new Reply(REPLY_EMOTIONS.contains(e) ? e : "neutral", m.isEmpty() ? "..." : m); // actions dropped: not trusted
        }
        return new Reply("neutral", text.isEmpty() ? "..." : text);
    }

    /** Groq answers 400 json_validate_failed with the rejected text in error.failed_generation. */
    static String failedGeneration(String body) {
        try {
            JsonElement text = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("error").get("failed_generation");
            return text == null || text.isJsonNull() || text.getAsString().isBlank() ? null : text.getAsString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Drops the "кукуку" tic and cuts a rambling reply at a sentence end. */
    private static final Pattern REFUSAL = Pattern.compile("(?iu)\\b(i\\s*(?:can['’]?t|cannot|won['’]?t|am not able to|'m not able to)\\s+(?:help\\s+(?:with|you|on)|assist|comply|provide|do that|answer)"
            + "|as an ai|as a language model|i['’]?m sorry,? but i|i apologi[sz]e,? but)|не могу (?:с этим )?помочь|как (?:ии|языковая модель)"
            + "|извините, но я не могу");

    /** "Let's play dice", plainly: a verb of playing or throwing, then dice (not "bones" in talk about his world). */
    private static final Pattern DICE = Pattern.compile("(?iu)(?:сыгра|поигра|игра[ейюя]|кин[еуь]|брос[иа]|метн[её])\\p{L}*\\s+(?:\\p{L}+\\s+){0,3}"
            + "(?:кост|кубик|чёт|чет|нечёт|нечет)|\\b(?:play|roll|throw|toss)\\b(?:\\s+\\w+){0,3}\\s+(?:dice|cho-?han)\\b");

    /** The player's line asks for a game of dice in so many words. */
    public static boolean wantsDice(String line) {
        return DICE.matcher(line).find();
    }

    /** An assistant's refusal (a fallback model forgetting who it plays), not a line of the character. */
    public static boolean refusal(String message) {
        return REFUSAL.matcher(message).find();
    }

    /** A tag, <invoke name="x"> or </invoke>: what a model's tool-call format looks like when it leaks into the text. */
    private static final Pattern MARKUP = Pattern.compile("</?[A-Za-z_][\\w:.-]*(\\s[^<>]*)?/?>");

    public static String cleanMessage(String message) {
        String m = KUKU.matcher(MARKUP.matcher(message.replaceAll("§.?", "")).replaceAll(" ").replace('\n', ' ').replace('\r', ' ')).replaceAll("")
                .replaceAll("\\s{2,}", " ").replaceAll("^[\\s,.!?…\\-—]+", "").trim(); // no colour codes, no fake extra lines
        if (m.length() > MAX_MESSAGE) {
            int end = MAX_MESSAGE - 1;
            int cut = Math.max(Math.max(m.lastIndexOf(". ", end), m.lastIndexOf("! ", end)), m.lastIndexOf("? ", end));
            m = cut > 40 ? m.substring(0, cut + 1) : m.substring(0, end) + "…";
        }
        return m.isEmpty() ? "..." : m;
    }

    private static List<Action> actions(JsonObject o) {
        List<Action> out = new ArrayList<>();
        if (!o.has("actions") || !o.get("actions").isJsonArray()) {
            return out;
        }
        for (JsonElement e : o.getAsJsonArray("actions")) {
            if (!e.isJsonObject()) {
                continue;
            }
            JsonObject a = e.getAsJsonObject();
            String name = a.has("name") ? a.get("name").getAsString() : a.has("action") ? a.get("action").getAsString() : "";
            Map<String, String> args = new LinkedHashMap<>();
            if (a.has("args") && a.get("args").isJsonObject()) {
                for (Map.Entry<String, JsonElement> arg : a.getAsJsonObject("args").entrySet()) {
                    if (arg.getValue().isJsonPrimitive()) {
                        args.put(arg.getKey(), arg.getValue().getAsString());
                    }
                }
            }
            if (!name.isBlank()) {
                out.add(new Action(name.trim().toLowerCase(Locale.ROOT), args));
            }
        }
        return out;
    }

    static String errorText(String body) {
        try {
            return JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("error").get("message").getAsString();
        } catch (RuntimeException e) {
            return body.length() > 200 ? body.substring(0, 200) : body;
        }
    }
}
