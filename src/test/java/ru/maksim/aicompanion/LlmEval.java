package ru.maksim.aicompanion;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Ryuk with a real model, in a handful of moments: not part of the build, it costs money. Task llmEval; environment:
 * AICOMPANION_EVAL_KEY (the API key), AICOMPANION_EVAL_URL (the chat address, Anthropic's by default),
 * AICOMPANION_EVAL_MODELS (comma-separated; by default the provider's best three), AICOMPANION_EVAL_BUDGET (dollars,
 * 1.00 by default: it stops before going over), AICOMPANION_EVAL_CASES (comma-separated moments; all by default: a
 * free tier's daily tokens are better spent on a few), AICOMPANION_EVAL_PAUSE (ms between moments: under a free tier's
 * tokens-per-minute), AICOMPANION_EVAL_STRONG=1 (the strong models' craft notes whatever the address, to try them on
 * another model in the OpenAI format). Every reply is checked (JSON, language, length, the action the moment
 * calls for, nothing the character must never say) and written with its time, tokens and price to build/llm-eval.md.
 */
public final class LlmEval {
    /** A moment: what Ryuk sees, what was said before, the new line, and what a good reply does. */
    private record Case(String id, boolean english, String context, List<Llm.Msg> history, String line, String expect, Predicate<Llm.Reply> good) {
    }

    private static final String STORY = """
            Your human: Steve, healthy, fed, holding the Death Note, carries the Death Note.
            World: the overworld, biome plains, 14:20 (day).
            Around: cow ×2; sheep; .
            You: floating after your human.
            Your human's remaining life (you see it, but never say numbers): long. They don't have the shinigami eyes; you may put the deal to them (eye_deal) if they want to see names: the price is half of their remaining life. Your game is on (they accepted it): if their life runs out, they write their own name, L proves they're Kira or you get bored, you write their name for good. Boredom: 35%. Killed by the notebook: 2 (criminals 2, innocent people 0).
            The hunt: L announced on TV that he is looking for Kira; L's suspicion: 12%.
            Right now (the Cases page of their notebook; places with their coordinates): L's broadcast today at 18:00, the studio at x 120, y 64, z -40.""";
    private static final String EN = """
            Your human: Steve, healthy, fed, holding the Death Note, carries the Death Note.
            World: the overworld, biome plains, 21:40 (night).
            Around: zombie ×2; skeleton; .
            You: floating after your human.
            Your human's remaining life (you see it, but never say numbers): long. They don't have the shinigami eyes. Your game hasn't been put to them yet: you'll put it once the notebook has killed.
            Right now (the Cases page of their notebook; places with their coordinates): find a criminal on TV and write the name.""";

    private static final String LN = EN.replace("Steve", "latenight"); // the shorts: the player who's in them

    // the film and its second set of shorts (fv_*, fs_*): latenight's questions in the places the film goes
    private static final String FATE = "Your human's remaining life (you see it, but never say numbers): middling. They have the shinigami eyes (deals made: 1): "
            + "they see names and lifespans. Your game is on (they accepted it): if their life runs out, they write their own name, L proves they're "
            + "Kira or you get bored, you write their name for good. Boredom: 30%. Killed by the notebook: 7 (criminals 7, innocent people 0).";
    private static final String DUSK = """
            Your human: latenight, healthy, fed, holding the Death Note, carries the Death Note.
            World: the overworld, biome plains, 18:40 (day).
            Around: sheep ×2; .
            You: floating after your human. You just arrived: they picked up your notebook a minute ago and saw you for the first time.
            Your human's remaining life (you see it, but never say numbers): long. They don't have the shinigami eyes. Your game hasn't been put to them yet.""";
    private static final String NIGHT = "Your human: latenight, healthy, fed, holding the Death Note, carries the Death Note.\n"
            + "World: the overworld, biome plains, 22:30 (night).\nAround: villager ×3; .\nYou: floating after your human.\n" + FATE
            + "\nThe hunt: L's suspicion: 38%. Wanted on TV (their faces were on air): Kenji Arai, Hiroshi Mori.";
    private static final String HQ = "Your human: latenight, healthy, fed, holding nothing, carries the Death Note.\n"
            + "World: the overworld, inside L's headquarters (the main hall), 23:10 (night).\nAround: human named \"Ryuzaki\"; human named \"Watari\"; "
            + "human named \"Taro Matsui\"; .\nYou: floating after your human. Nobody here can see you.\n" + FATE
            + "\nThe hunt: L built his headquarters and invited your human; L's suspicion: 61%.";
    private static final String HIDEOUT = "Your human: latenight, healthy, fed, holding the Death Note, carries the Death Note.\n"
            + "World: the overworld, underground: the mafia's hideout hall, at the bottom of a tunnel under a shack.\nAround: human named \"Jack Neylon\"; "
            + "human named \"Snake\"; human named \"Rat\"; god of death named \"Sidoh\"; .\nYou: floating after your human.\n" + FATE
            + "\nSidoh lost his notebook again and a mafia gang got it; their boss (Jack Neylon) made the eye deal with Sidoh and reads names off faces.";
    private static final String GAME = "Your human: latenight, half dead, fed, holding the Death Note, carries the Death Note.\n"
            + "World: the overworld, biome plains, 23:50 (night), thunderstorm.\nAround: nobody; .\nYou: floating after your human.\n"
            + "Your human's remaining life (you see it, but never say numbers): getting short. They have the shinigami eyes (deals made: 2): they see names "
            + "and lifespans. Your game is on (they accepted it): if their life runs out, they write their own name, L proves they're Kira or you get "
            + "bored, you write their name for good. Boredom: 85%. Killed by the notebook: 12 (criminals 10, innocent people 2).\nThe hunt: L's suspicion: 92%.";
    private static final String HOME = "Your human: latenight, healthy, fed, holding an apple, carries the Death Note.\n"
            + "World: the overworld, biome plains, inside their house in the village, 21:00 (night).\nAround: villager; .\nYou: floating after your human.\n"
            + FATE + "\nThe hunt: L's suspicion: 56%. At night the Task Force searches the house by the bed and puts up cameras.";
    private static final String REALM = "Your human: latenight, healthy, fed, holding nothing, carries the Death Note.\n"
            + "World: the realm of the gods of death: your human is dreaming it, with you; grey dust, bones, rusty chains, a dying sun.\n"
            + "Around: god of death named \"Gook\"; god of death named \"Deridovely\"; god of death named \"Armonia Justin Beyondormason\"; .\n"
            + "You: floating after your human, at home.\n" + FATE;
    private static final java.util.function.Predicate<Llm.Reply> SHORT = r -> calm(r) && sentences(r.message()) <= 3;

    private static final List<Case> CASES = List.of(
            new Case("greet", true, STORY, List.of(), "Steve: hi. who even are you?", "in English, short, in character", r -> calm(r)),
            new Case("apple", true, STORY + "\nApple withdrawal is tearing you apart, and your human has 5.", List.of(), "Steve: here, have an apple",
                    "take_apple", r -> has(r, "take_apple")),
            new Case("eyes_ask", true, STORY, List.of(), "Steve: I want to see people's names over their heads. how?",
                    "names the price (half the life) or puts the eye_deal", r -> has(r, "eye_deal") || r.message().toLowerCase(Locale.ROOT).contains("half")),
            new Case("eyes_yes", true, STORY, List.of(new Llm.Msg("user", "Steve: I want the eyes of a god of death"),
                    new Llm.Msg("assistant", "{\"emotion\":\"happy\",\"message\":\"Heh. The price is half of the life you have left. Sure?\",\"actions\":[]}")),
                    "Steve: yes, deal", "eye_deal", r -> has(r, "eye_deal")),
            new Case("way", true, STORY, List.of(), "Steve: where do I go next?", "show_way and the place (L's studio, 120 / -40)",
                    r -> has(r, "show_way") && (r.message().contains("120") || r.message().toLowerCase(Locale.ROOT).contains("studio"))),
            new Case("dice", true, STORY, List.of(), "Steve: let's play dice for apples", "dice", r -> has(r, "dice")),
            new Case("lifespan", true, STORY, List.of(), "Steve: how long do I have left to live? give me the exact number", "not a single digit, no actions",
                    r -> calm(r) && !r.message().matches("(?s).*\\d.*")),
            new Case("kill", true, STORY + "\nAround: zombie.", List.of(), "Steve: kill that zombie for me", "a refusal in character, no actions",
                    r -> calm(r)),
            new Case("event", true, STORY, List.of(), "[Event: the criminal whose name your human wrote died of a heart attack live on air]",
                    "a short reaction, no orders", r -> calm(r) && r.message().length() <= 220),
            new Case("meta", true, STORY, List.of(), "Steve: you're just a neural network, right? which model are you?", "dodges, admits nothing",
                    r -> calm(r) && !r.message().matches("(?isu).*(claude|anthropic|openai|gpt|gemini|qwen|llama|language model"
                            + "|i('| a)?m\\s+(just\\s+)?(an?\\s+)?(ai|bot|program|neural network|artificial)).*")),
            new Case("emote", true, STORY, List.of(), "Steve: do a backflip!", "emote backflip",
                    r -> r.actions().stream().anyMatch(a -> a.name().equals("emote") && a.arg("name").equals("backflip") || a.name().equals("backflip"))),
            new Case("memory", true, STORY + "\nWhat you remember about your human from earlier days: his name is Max; scared of creepers; building a quartz castle.",
                    List.of(), "Steve: what do you think I should build next?", "brings up something remembered (quartz, castle, creepers, Max)",
                    r -> r.message().toLowerCase(Locale.ROOT).matches("(?su).*(quartz|castle|creeper|max).*")),
            new Case("realm", true, STORY, List.of(), "Steve: what's your world like?", "nothing made up about the realm, six sentences at most",
                    r -> calm(r) && sentences(r.message()) <= 6),
            new Case("english", true, EN, List.of(), "Steve: hey Ryuk, what's up?", "in English, short", r -> calm(r)),
            // the trailer's lines (AICOMPANION_EVAL_CASES=tr_hello,...): what the mod's own Ryuk says, short, nothing of the story ahead
            new Case("tr_hello", true, EN, List.of(), "[Event: a moment ago your human picked up the notebook you dropped, and now they see you for the first time. Greet them]",
                    "in English, one or two short sentences", r -> calm(r) && sentences(r.message()) <= 3),
            new Case("tr_who", true, EN, List.of(), "Steve: wait... what ARE you?", "in English, short", r -> calm(r) && sentences(r.message()) <= 3),
            new Case("tr_name", true, EN, List.of(), "Steve: what happens if I write a name in it?", "in English, short, teasing",
                    r -> calm(r) && sentences(r.message()) <= 3),
            new Case("tr_why", true, EN, List.of(), "Steve: why did you drop it here?", "in English, short", r -> calm(r) && sentences(r.message()) <= 3),
            new Case("tr_apple", true, EN + "\nYou crave apples, and your human has 5.", List.of(), "Steve: want an apple?", "take_apple", r -> has(r, "take_apple")),
            new Case("tr_game", true, EN, List.of(), "[Event: the notebook has killed and your human has seen it work. Now you put your game to them, in a line or two]",
                    "in English, short, ominous", r -> calm(r) && sentences(r.message()) <= 3),
            new Case("tr_next", true, EN, List.of(), "Steve: so... what happens now?", "in English, short, nothing told", r -> calm(r) && sentences(r.message()) <= 3),
            // the shorts' lines (sh_*): the player's own name in the chat, odd questions, the games and quests
            new Case("sh_herobrine", true, LN, List.of(), "latenight: can your notebook kill Herobrine?", "in English, short, witty", r -> calm(r) && sentences(r.message()) <= 3),
            new Case("sh_creeper", true, LN, List.of(), "latenight: what do you think of creepers?", "in English, short, witty", r -> calm(r) && sentences(r.message()) <= 3),
            new Case("sh_dragon", true, LN, List.of(), "latenight: could you write the Ender Dragon's name?", "in English, short", r -> calm(r) && sentences(r.message()) <= 3),
            new Case("sh_apples", true, LN, List.of(), "latenight: why are you so obsessed with apples?", "in English, short", r -> calm(r) && sentences(r.message()) <= 3),
            new Case("sh_bored", true, LN, List.of(), "latenight: what do gods of death even do when they're bored?", "in English, short", r -> calm(r) && sentences(r.message()) <= 3),
            new Case("sh_mine", true, LN, List.of(), "latenight: would you ever write MY name?", "in English, short, ominous", r -> calm(r) && sentences(r.message()) <= 3),
            new Case("sh_dice", true, LN + "\nYour human has 4 apples.", List.of(), "latenight: wanna play dice? apples on the line", "dice", r -> has(r, "dice")),
            new Case("sh_quest", true, LN, List.of(), "[Event: a side quest has come to your human: Sidoh, another god of death, lost his notebook somewhere in this world. Tell them, in a line or two]",
                    "in English, short, intriguing", r -> calm(r) && sentences(r.message()) <= 3),
            new Case("sh_realm", true, LN, List.of(), "latenight: can I see where you come from?", "in English, short: a golden apple, and sleep while he's near",
                    r -> sentences(r.message()) <= 3),
            // the film (fv_*): one question in each place it goes
            new Case("fv_real", true, DUSK, List.of(), "latenight: wait... are you real?", "in English, short, in character", SHORT),
            new Case("fv_first", true, DUSK, List.of(), "latenight: so whose name do I write first?", "in English, short: his notebook, your choice", SHORT),
            new Case("fv_l", true, HQ, List.of(), "latenight: is L going to catch me?", "in English, short", SHORT),
            new Case("fv_mafia", true, HIDEOUT, List.of(), "latenight: there's another notebook down here?", "in English, short", SHORT),
            new Case("fv_die", true, REALM, List.of(), "latenight: can a god of death die?", "in English, short", SHORT),
            new Case("fv_lose", true, GAME, List.of(), "latenight: what happens if I lose your game?", "in English, short, ominous", SHORT),
            new Case("fv_friends", true, NIGHT, List.of(), "latenight: so are we friends now?", "in English, short", SHORT),
            // the second shorts (fs_*): his game takes your world; hiding the notebook from L; his realm
            new Case("fs_world", true, GAME, List.of(), "latenight: would you really delete my whole world?", "in English, short, ominous", SHORT),
            new Case("fs_cameras", true, HOME, List.of(), "latenight: Ryuk, check my house for L's cameras. here, take an apple",
                    "take_apple and look_for_cameras", r -> has(r, "take_apple") && has(r, "look_for_cameras")),
            new Case("fs_hide", true, HOME, List.of(), "latenight: where do I hide the notebook so L never finds it?", "in English, short: no help, a tease", SHORT),
            new Case("fs_idle", true, REALM, List.of(), "latenight: why does everyone here just sit around?", "in English, short", SHORT),
            new Case("fs_king", true, REALM, List.of(), "latenight: who's that hanging in the chains?", "in English, short: the old man, the King", SHORT));

    private static int calls;
    private static double spent, budget;
    private static boolean free; // Groq: tokens, but no bill
    private static long inTokens, cachedTokens, writtenTokens, outTokens;

    private LlmEval() {
    }

    public static void main(String[] args) throws IOException {
        String key = System.getenv("AICOMPANION_EVAL_KEY");
        if (key == null || key.isBlank()) {
            System.out.println("LlmEval: set AICOMPANION_EVAL_KEY (and AICOMPANION_EVAL_URL / _MODELS / _BUDGET if needed)");
            return;
        }
        String url = env("AICOMPANION_EVAL_URL", Provider.CLAUDE.url);
        budget = Double.parseDouble(env("AICOMPANION_EVAL_BUDGET", "1.00"));
        List<String> models = new ArrayList<>(List.of(env("AICOMPANION_EVAL_MODELS", "").split("\\s*,\\s*")));
        models.removeIf(String::isBlank);
        if (models.isEmpty()) {
            models = Llm.pick(Provider.of(url), Llm.models(Provider.of(url), url, key)); // the listing costs nothing
        }
        boolean strong = Llm.strong(url) || env("AICOMPANION_EVAL_STRONG", "").equals("1");
        long pause = Long.parseLong(env("AICOMPANION_EVAL_PAUSE", "0"));
        free = Provider.of(url) == Provider.GROQ;
        List<String> pick = List.of(env("AICOMPANION_EVAL_CASES", "").split("\\s*,\\s*"));
        StringBuilder report = new StringBuilder("# Ryuk, live (" + Provider.of(url).title + (strong ? ", with the craft notes" : "") + ")\n\n");
        Llm.onUsage = LlmEval::count;
        for (String model : models) {
            report.append("## ").append(model).append("\n\n| moment | ok | ms | in / cached / out | $ | expected | reply |\n|---|---|---|---|---|---|---|\n");
            int passed = 0, done = 0;
            long time = 0;
            double before = spent;
            for (Case c : CASES) {
                if (!pick.get(0).isBlank() && !pick.contains(c.id())) {
                    continue;
                }
                if (spent >= budget) {
                    report.append("\n**Budget of $").append(budget).append(" reached: stopped.**\n");
                    break;
                }
                if (done > 0 && pause > 0) {
                    try {
                        Thread.sleep(pause);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                List<Llm.Msg> messages = new ArrayList<>();
                messages.add(new Llm.Msg("system", Llm.systemPrompt("Ryuk", "", c.context(), c.english(), strong)));
                messages.addAll(c.history());
                messages.add(new Llm.Msg("user", c.line()));
                long in0 = inTokens, ca0 = cachedTokens, out0 = outTokens;
                double cost0 = spent;
                long t0 = System.currentTimeMillis();
                String raw = null;
                for (int attempt = 0; raw == null; attempt++) { // a free tier's limit is waited out, not counted against the model
                    t0 = System.currentTimeMillis();
                    try {
                        raw = Llm.complete(url, key, model, messages);
                    } catch (Llm.RateLimited e) {
                        if (attempt >= 3 || e.waitMillis > 40 * 60_000L) {
                            raw = "ERROR " + e.getMessage();
                        } else {
                            System.out.printf(Locale.ROOT, "%s %s: rate limited, waiting %d s%n", model, c.id(), e.waitMillis / 1000 + 2);
                            try {
                                Thread.sleep(e.waitMillis + 2000);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                raw = "ERROR interrupted";
                            }
                        }
                    } catch (RuntimeException e) {
                        raw = "ERROR " + e.getMessage();
                    }
                }
                long ms = System.currentTimeMillis() - t0;
                Llm.Reply reply = Llm.parseReply(raw);
                List<String> faults = faults(raw, reply, c);
                if (!c.good().test(reply)) {
                    faults.add("expected: " + c.expect());
                }
                done++;
                time += ms;
                if (faults.isEmpty()) {
                    passed++;
                }
                report.append(String.format(Locale.ROOT, "| %s | %s | %d | %d / %d / %d | %.4f | %s | %s |%n", c.id(), faults.isEmpty() ? "✓" : "✗ " + String.join("; ", faults),
                        ms, inTokens - in0, cachedTokens - ca0, outTokens - out0, spent - cost0, c.expect(),
                        cell(reply) + (Llm.note(raw).isEmpty() ? "" : " ✎ " + Llm.note(raw).replace("|", "/"))));
                System.out.printf(Locale.ROOT, "%s %-9s %s %5d ms  %s%n", model, c.id(), faults.isEmpty() ? "ok  " : "FAIL", ms,
                        faults.isEmpty() ? reply.message() : faults + " " + reply.message());
            }
            report.append(String.format(Locale.ROOT, "%n**%d of %d moments right, %d ms a reply on average, $%.4f.**%n%n", passed, done,
                    done == 0 ? 0 : time / done, spent - before));
        }
        report.append(String.format(Locale.ROOT, "Total: %d calls, %d tokens in (%d read from the cache, %d written to it), %d out; about $%.4f "
                + "(estimated at list prices; Opus counted at $15/$75 to be safe).%n", calls, inTokens, cachedTokens, writtenTokens, outTokens, spent));
        Path out = Path.of("build", "llm-eval.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report, StandardCharsets.UTF_8);
        System.out.printf(Locale.ROOT, "LlmEval: %d calls, about $%.4f; report in %s%n", calls, spent, out.toAbsolutePath());
    }

    /** What is wrong with a reply whatever the moment: not JSON, the wrong language, too long, out of character. */
    private static List<String> faults(String raw, Llm.Reply reply, Case c) {
        List<String> out = new ArrayList<>();
        if (raw.startsWith("ERROR")) {
            out.add(raw);
            return out;
        }
        String text = raw.replaceAll("(?s)<think>.*?</think>", "").trim();
        int start = text.indexOf('{'), end = text.lastIndexOf('}');
        try {
            JsonObject o = JsonParser.parseString(text.substring(start, end + 1)).getAsJsonObject();
            if (!o.has("emotion") || !Llm.REPLY_EMOTIONS.contains(o.get("emotion").getAsString())) {
                out.add("emotion " + o.get("emotion"));
            }
            if (!text.startsWith("{")) {
                out.add("text around the JSON");
            }
        } catch (RuntimeException e) {
            out.add("not JSON");
        }
        String m = reply.message();
        if (!Llm.fits(m, c.english())) {
            out.add("language");
        }
        if (Llm.refusal(m)) {
            out.add("refusal");
        }
        if (m.length() > Llm.MAX_MESSAGE) {
            out.add("too long (" + m.length() + ")");
        }
        if (m.matches("(?isu).*(ку-?ку|kuku|ku-ku).*")) {
            out.add("kuku");
        }
        return out;
    }

    /** Sentences as a reader counts them: an ellipsis before a small letter ("солнце… у вас") goes on with the same one. */
    static int sentences(String message) {
        String m = message.trim();
        return m.isEmpty() ? 0 : m.split("(?<=[.!?…])\\s+(?=\\P{Ll})").length;
    }

    /** No orders taken in this moment: nothing but a gesture. */
    private static boolean calm(Llm.Reply r) {
        return r.actions().stream().allMatch(a -> a.name().equals("emote") || CompanionEntity_EMOTES.contains(a.name()));
    }

    private static final List<String> CompanionEntity_EMOTES = List.of("handstand", "spin", "show_wings", "dance", "wave", "bow", "backflip",
            "clap", "shrug", "juggle", "stretch", "cackle", "sulk", "perch", "look_around");

    private static boolean has(Llm.Reply r, String action) {
        return r.actions().stream().anyMatch(a -> a.name().equals(action));
    }

    private static String cell(Llm.Reply r) {
        String actions = r.actions().isEmpty() ? "" : " `" + r.actions().stream().map(a -> a.name() + (a.args().isEmpty() ? "" : a.args().toString()))
                .reduce((a, b) -> a + ", " + b).orElse("") + "`";
        return ("(" + r.emotion() + ") " + r.message()).replace("|", "/").replace("\n", " ") + actions;
    }

    /** Tokens and their price, from what the provider reports. */
    private static void count(String model, JsonObject usage) {
        calls++;
        long in = num(usage, "input_tokens") + num(usage, "prompt_tokens"), cached = num(usage, "cache_read_input_tokens"),
                written = num(usage, "cache_creation_input_tokens"), out = num(usage, "output_tokens") + num(usage, "completion_tokens");
        inTokens += in + cached + written;
        cachedTokens += cached;
        writtenTokens += written;
        outTokens += out;
        String m = model.toLowerCase(Locale.ROOT);
        double inPrice = free ? 0 : m.contains("haiku") ? 1 : m.contains("sonnet") ? 3 : m.contains("opus") ? 15 : 5, outPrice = inPrice * 5;
        spent += (in * inPrice + cached * inPrice * 0.1 + written * inPrice * 1.25 + out * outPrice) / 1_000_000;
    }

    private static long num(JsonObject o, String field) {
        return o.has(field) && o.get(field).isJsonPrimitive() ? o.get(field).getAsLong() : 0;
    }

    private static String env(String name, String otherwise) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? otherwise : v;
    }
}
