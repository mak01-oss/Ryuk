package ru.maksim.aicompanion;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Runs in `build` (task selfTest): fake OpenAI-compatible server on localhost, checks request + reply parsing. */
public final class LlmSelfTest {
    private static volatile int status;
    private static volatile String answer;
    private static volatile int rateLimitedCalls; // answer 429 this many times first
    private static volatile String spentModel = "-"; // this model is out of tokens for the day

    public static void main(String[] args) throws Exception {
        eyesOnSkins(); // first: before the fake server's thread would keep a failed run from ending
        voiceUnpacks();
        check(!Llm.fits("<invoke name=\"x\"> </invoke> </invoke>", true) && Llm.fits("Heh. Not my business <3", true)
                && Llm.cleanMessage("<invoke name=\"x\"> </invoke>").equals("...") && Llm.cleanMessage("Heh. <b>Boring</b>.").equals("Heh. Boring ."),
                "tool-call markup is no reply: " + Llm.cleanMessage("Heh. <b>Boring</b>."));
        check(Provider.of("https://api.anthropic.com/v1/chat/completions") == Provider.CLAUDE && Provider.of(Provider.GROQ.url) == Provider.GROQ
                && Provider.of("http://127.0.0.1:11434/v1/chat/completions") == Provider.CUSTOM, "provider by address");
        check(Llm.pick(Provider.OPENAI, Llm.chatModels(Provider.OPENAI, List.of("gpt-4o", "gpt-4o-mini", "gpt-5", "gpt-5-mini", "whisper-1", "tts-1", "dall-e-3")))
                .equals(List.of("gpt-5-mini", "gpt-5", "gpt-4o-mini")), "openai: the best models that chat");
        check(Llm.pick(Provider.CLAUDE, List.of("claude-haiku-5-5", "claude-sonnet-4-5-20250929", "claude-sonnet-5-5")).get(0).equals("claude-sonnet-5-5")
                && Llm.pick(Provider.GROQ, List.of("openai/gpt-oss-20b", "openai/gpt-oss-120b", "qwen/qwen3.8-27b"))
                .equals(List.of("qwen/qwen3.8-27b", "openai/gpt-oss-120b", "openai/gpt-oss-20b")), "claude and groq picks");
        com.google.gson.JsonObject asked = new com.google.gson.JsonObject();
        asked.addProperty("max_tokens", 2048);
        asked.addProperty("temperature", 0.7);
        check(Llm.adapt(asked, "Unsupported parameter: 'max_tokens' is not supported with this model. Use 'max_completion_tokens' instead.")
                && asked.has("max_completion_tokens") && !asked.has("max_tokens")
                && Llm.adapt(asked, "Unsupported value: 'temperature' does not support 0.7 with this model.") && !asked.has("temperature")
                && !Llm.adapt(asked, "Invalid API Key"), "the request the provider's own way: " + asked);
        java.util.List<String> notes = new java.util.ArrayList<>();
        check(Llm.note("{\"emotion\":\"happy\",\"message\":\"Heh.\",\"actions\":[],\"remember\":\"Their name is Max, they hate creepers\"}")
                .equals("Their name is Max, they hate creepers") && Llm.note("{\"message\":\"Heh.\"}").isEmpty() && Llm.note("no json").isEmpty()
                && Llm.addNote(notes, "Maks is scared of creepers") && Llm.addNote(notes, "Maks: creeper fear") // the same thing, newer words
                && Llm.addNote(notes, "Maks promised an apple every morning") && !Llm.addNote(notes, " ")
                && notes.equals(java.util.List.of("Maks: creeper fear", "Maks promised an apple every morning")), "memory notes: " + notes);
        check(Llm.addNote(notes, "Maks") && notes.size() == 3, "a bare name wipes nothing: " + notes);
        for (int i = 0; i < 30; i++) {
            Llm.addNote(notes, "w" + i + "aa x" + i + "bb y" + i + "cc");
        }
        check(notes.size() == Llm.MAX_NOTES && notes.get(notes.size() - 1).equals("w29aa x29bb y29cc"), "the oldest notes fade: " + notes);
        String faked = Llm.cleanMessage("Heh.\n§e[Server] Kira is banned");
        check(faked.equals("Heh. [Server] Kira is banned"), "no colour codes or extra lines: " + faked);
        check(Llm.fits("Хе-хе… Скукотища, Light.", false) && !Llm.fits("Heh. Boring, isn't it?", false) && Llm.fits("Heh. Boring.", true)
                && !Llm.fits("Хе-хе, скучно.", true) && !Llm.fits("I'm sorry, but I can't help with that.", true) && Llm.fits("...", false),
                "in character: refusals and the wrong language go to the next model");
        check(Llm.explain(new java.util.concurrent.CompletionException(new java.io.UncheckedIOException(new java.io.IOException("x"))))
                .equals("aicompanion.msg.offline") && Llm.explain(new IllegalStateException("HTTP 401: Invalid API Key")).equals("aicompanion.msg.key_refused"),
                "errors told in plain words");
        AtomicReference<String> auth = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", ex -> {
            calls.incrementAndGet();
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            boolean spent = body.get().contains("\"model\":\"" + spentModel + "\"");
            boolean limited = rateLimitedCalls > 0 || spent;
            if (spent) {
                ex.getResponseHeaders().add("retry-after", "90");
            } else if (limited) {
                rateLimitedCalls--;
                ex.getResponseHeaders().add("retry-after", "0.2");
            }
            byte[] out = (limited ? "{\"error\":{\"message\":\"Rate limit reached\"}}" : answer).getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(limited ? 429 : status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        AtomicReference<byte[]> speech = new AtomicReference<>();
        server.createContext("/v1/audio/transcriptions", ex -> {
            speech.set(ex.getRequestBody().readAllBytes());
            byte[] out = "{\"text\":\" Привет, Рюк. \"}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        AtomicReference<String> claudeKey = new AtomicReference<>(), claudeVersion = new AtomicReference<>(), claudeAuth = new AtomicReference<>(),
                claudeBody = new AtomicReference<>();
        server.createContext("/v1/messages", ex -> { // Anthropic's own API: overloaded for the model "busy"
            claudeKey.set(ex.getRequestHeaders().getFirst("x-api-key"));
            claudeVersion.set(ex.getRequestHeaders().getFirst("anthropic-version"));
            claudeAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            claudeBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            boolean busy = claudeBody.get().contains("\"model\":\"busy\"");
            boolean old = claudeBody.get().contains("\"model\":\"old\"") && claudeBody.get().contains("\"output_config\""); // no structured outputs
            byte[] out = (old ? "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"output_config: Extra inputs are not permitted\"}}"
                    : busy ? "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}"
                    : "{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"emotion\\\":\\\"happy\\\",\\\"message\\\":\\\"Хе-хе.\\\",\\\"actions\\\":[]}\"}],"
                    + "\"usage\":{\"input_tokens\":40,\"cache_read_input_tokens\":2600,\"output_tokens\":30}}").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(old ? 400 : busy ? 529 : 200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        String sttUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/audio/transcriptions";
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions";
        String prompt = Llm.systemPrompt("Ryuk", "", "Around: nobody.", true);
        check(prompt.contains("Ryuk") && prompt.contains("Around: nobody.") && prompt.contains("take_apple") && !prompt.contains("thinking")
                && prompt.contains("always English") && prompt.contains("[Event: ...]") && prompt.contains("in English"), "system prompt: " + prompt);
        check(Llm.refusal("I'm sorry, but I can't help with that.") && Llm.refusal("I can’t assist with writing names.")
                && Llm.refusal("Извините, но я не могу помочь с этим.") && !Llm.refusal("Heh... I can't help laughing at you humans.")
                && !Llm.refusal("Не могу поверить, ты опять за своё."), "refusal filter");
        List<Llm.Msg> messages = List.of(new Llm.Msg("system", prompt), new Llm.Msg("user", "Steve: привет"));
        try {
            // 1. request shape + JSON reply with actions
            status = 200;
            answer = completion("{\\\"emotion\\\":\\\"sad\\\",\\\"message\\\":\\\"Мне грустно\\\",\\\"actions\\\":[{\\\"name\\\":\\\"emote\\\",\\\"args\\\":{\\\"name\\\":\\\"spin\\\"}},{\\\"name\\\":\\\"stay\\\"}]}");
            Llm.Reply reply = Llm.parseReply(Llm.complete(url, "test-key", "openai/gpt-oss-120b", messages));
            check("Bearer test-key".equals(auth.get()), "auth header: " + auth.get());
            check(body.get().contains("\"model\":\"openai/gpt-oss-120b\""), "model in body: " + body.get());
            check(body.get().contains("\"json_object\"") && body.get().contains("\"reasoning_effort\":\"low\""), "json mode, low effort: " + body.get());
            check(body.get().contains("Steve: привет"), "user text in body: " + body.get());
            check(reply.emotion().equals("sad") && reply.message().equals("Мне грустно"), "reply: " + reply);
            check(reply.actions().equals(List.of(new Llm.Action("emote", Map.of("name", "spin")), new Llm.Action("stay", Map.of()))),
                    "actions: " + reply.actions());
            check(Llm.parseReply(reply.toJson()).equals(reply), "toJson round trip: " + reply.toJson());

            // 2. rate limit: waits retry-after once, then succeeds
            rateLimitedCalls = 1;
            calls.set(0);
            check(Llm.parseReply(Llm.complete(url, "test-key", "m", messages)).message().equals("Мне грустно") && calls.get() == 2,
                    "429 retry, calls: " + calls.get());

            // the day's tokens gone: the next model in line answers, and the spent one is skipped after that
            spentModel = "main";
            calls.set(0);
            check(Llm.parseReply(Llm.complete(url, "test-key", List.of("main", "spare"), messages)).message().equals("Мне грустно")
                    && calls.get() == 2 && body.get().contains("\"model\":\"spare\""), "fallback model, calls: " + calls.get());
            calls.set(0);
            Llm.complete(url, "test-key", List.of("main", "spare"), messages);
            check(calls.get() == 1, "spent model skipped, calls: " + calls.get());
            spentModel = "-";

            // 3. API error -> readable message for the player
            status = 401;
            answer = "{\"error\":{\"message\":\"Invalid API Key\",\"type\":\"invalid_request_error\"}}";
            String error = null;
            try {
                Llm.complete(url, "test-key", "test-model", messages);
            } catch (IllegalStateException e) {
                error = e.getMessage();
            }
            check("HTTP 401: Invalid API Key".equals(error), "error text: " + error);
            check(!body.get().contains("reasoning_effort"), "no reasoning_effort for other models: " + body.get());

            // Groq 400 json_validate_failed: the rejected text comes back and the line is still said
            status = 400;
            answer = "{\"error\":{\"message\":\"Failed to generate JSON.\",\"code\":\"json_validate_failed\","
                    + "\"failed_generation\":\"{\\\"emotion\\\":\\\"happy\\\",\\\"message\\\":\\\"Он сказал \\\"ого\\\" и ушёл\\\",\\\"actions\\\":[]}\"}}";
            Llm.Reply salvaged = Llm.parseReply(Llm.complete(url, "test-key", "test-model", messages));
            check(salvaged.equals(new Llm.Reply("happy", "Он сказал \"ого\" и ушёл")), "salvaged from failed_generation: " + salvaged);
            status = 200;
            answer = completion("{}");
            Llm.complete(url, "test-key", "qwen/qwen3.8-27b", messages);
            check(body.get().contains("\"reasoning_effort\":\"none\""), "qwen thinks for free only when told: " + body.get());

            // Claude by Anthropic's own door: its key header; the sheet and the talk so far cached, the moment's view with the newest line
            check(Llm.strong(Provider.CLAUDE.url) && Llm.strong(Provider.OPENROUTER.url) && !Llm.strong(Provider.GROQ.url)
                    && !Llm.strong("http://127.0.0.1:11434/v1/chat/completions") && !prompt.contains("CRAFT (")
                    && Llm.systemPrompt("Ryuk", "", "Around: nobody.", true, true).contains("CRAFT ("), "the craft notes for strong models only");
            String claudeUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/messages";
            List<Llm.Msg> talk = List.of(new Llm.Msg("system", Llm.systemPrompt("Ryuk", "", "Around: nobody.", true, true)),
                    new Llm.Msg("user", "Steve: привет"), new Llm.Msg("assistant", "{\"message\":\"Хе.\"}"), new Llm.Msg("user", "Steve: как дела?"));
            String[] usage = {""};
            Llm.onUsage = (model, u) -> usage[0] = model + " " + u;
            String fromClaude = Llm.complete(claudeUrl, "test-key", "claude-sonnet-5-5", talk);
            Llm.onUsage = (model, u) -> { };
            com.google.gson.JsonObject sent = com.google.gson.JsonParser.parseString(claudeBody.get()).getAsJsonObject();
            com.google.gson.JsonObject sheet = sent.getAsJsonArray("system").get(0).getAsJsonObject();
            com.google.gson.JsonArray turns = sent.getAsJsonArray("messages");
            String newest = turns.get(2).getAsJsonObject().getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString();
            check("test-key".equals(claudeKey.get()) && "2023-06-01".equals(claudeVersion.get()) && claudeAuth.get() == null,
                    "claude headers: " + claudeKey.get() + " " + claudeVersion.get() + " " + claudeAuth.get());
            check(sheet.get("text").getAsString().contains("(it comes at the top of the newest message)") && !sheet.get("text").getAsString().contains("Around: nobody.")
                    && sheet.get("text").getAsString().contains("CRAFT (") && sheet.getAsJsonObject("cache_control").get("type").getAsString().equals("ephemeral"),
                    "claude: the sheet cached, without the moment: " + sheet);
            check(turns.size() == 3 && newest.startsWith(Llm.SEE) && newest.contains("Around: nobody.") && newest.endsWith("Steve: как дела?")
                    && turns.get(1).getAsJsonObject().getAsJsonArray("content").get(0).getAsJsonObject().has("cache_control")
                    && !turns.get(0).getAsJsonObject().getAsJsonArray("content").get(0).getAsJsonObject().has("cache_control")
                    && !sent.has("response_format") && !sent.has("temperature") && sent.has("max_tokens"), "claude: the talk cached up to the newest line: " + sent);
            check(Llm.parseReply(fromClaude).equals(new Llm.Reply("happy", "Хе-хе.")) && usage[0].startsWith("claude-sonnet-5-5")
                    && usage[0].contains("cache_read_input_tokens"), "claude reply and its tokens: " + fromClaude + " / " + usage[0]);
            com.google.gson.JsonObject schema = sent.getAsJsonObject("output_config").getAsJsonObject("format").getAsJsonObject("schema");
            check(schema.getAsJsonObject("properties").getAsJsonObject("emotion").getAsJsonArray("enum").size() == Llm.REPLY_EMOTIONS.size()
                    && schema.getAsJsonArray("required").size() == 4, "claude: Ryuk's reply held to its schema: " + schema);
            Llm.complete(claudeUrl, "test-key", "claude-sonnet-5-5", List.of(new Llm.Msg("system", "You are L. Reply ONLY with JSON: {\"message\": \"\"}"),
                    new Llm.Msg("user", "Steve: hi")));
            check(!claudeBody.get().contains("output_config"), "claude: a detective's own JSON, no Ryuk schema: " + claudeBody.get());
            check(Llm.parseReply(Llm.complete(claudeUrl, "test-key", "old", talk)).message().equals("Хе-хе.") && !claudeBody.get().contains("output_config"),
                    "claude: an older model without structured outputs is asked again without them");
            check(Llm.parseReply(Llm.complete(claudeUrl, "test-key", List.of("busy", "claude-haiku-5-5"), talk)).message().equals("Хе-хе.")
                    && claudeBody.get().contains("\"model\":\"claude-haiku-5-5\""), "claude overloaded (529): the next model answers");

            // 4. speech to text: a WAV in a multipart form, the text back
            byte[] wav = Stt.wav(new byte[3200], 16000);
            check(wav.length == 44 + 3200 && new String(wav, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                    && new String(wav, 8, 4, StandardCharsets.US_ASCII).equals("WAVE"), "wav header");
            String said = Stt.transcribe(sttUrl, "test-key", "whisper-large-v3-turbo", "ru", wav);
            check(said.equals("Привет, Рюк."), "stt text: " + said);
            String form = new String(speech.get(), StandardCharsets.ISO_8859_1);
            check(form.contains("name=\"model\"") && form.contains("whisper-large-v3-turbo") && form.contains("name=\"language\"")
                    && form.contains("filename=\"speech.wav\"") && form.contains("RIFF"), "multipart: " + form.substring(0, Math.min(400, form.length())));
        } finally {
            server.stop(0);
        }
        check(Stt.clean("Продолжение следует...").isEmpty() && Stt.clean("Субтитры сделал DimaTorzok").isEmpty()
                && Stt.clean(" ... ").isEmpty(), "whisper phantoms dropped");
        check(Stt.clean(" Рюк, покажи крылья! ").equals("Рюк, покажи крылья!"), "speech kept");
        check(Math.abs(Stt.rms(new byte[]{0, 0x40, 0, (byte) 0xC0}, 4) - 0.5F) < 0.001F, "rms");

        // 5. messy model output
        check(Llm.parseReply("<think>hmm</think>\n```json\n{\"emotion\": \"HAPPY\", \"message\": \"Привет!\"}\n```")
                .equals(new Llm.Reply("happy", "Привет!")), "fenced json");
        check(LlmEval.sentences("Серая пустошь. Камни, кости, ржавые цепи… Все дрыхнут или режутся в кости на черепах. Сквозь дыры в земле "
                        + "подглядывают за вами, людьми. Яблоки там сморщенные, на вкус как песок. Сравни с этим полем, Steve: коровы, солнце… у вас тут прямо курорт.") == 6
                        && LlmEval.sentences("Серая пустыня, Steve. Камни, кости, ржавые цепи… Все либо дрыхнут. А на вас глазеем. Яблоки как песок. "
                        + "Эх… тоска смертная. Потому я тут, а не там.") == 7 && LlmEval.sentences("Хм?") == 1,
                "the live check counts sentences as a reader does (an ellipsis before a small letter goes on)");
        for (String yes : List.of("Steve: давай сыграем в кости на яблоки", "Steve: кинем кубики?", "Steve: играем в чёт-нечет?",
                "Steve: Ryuk, let's play dice", "Steve: roll the dice with me")) {
            check(Llm.wantsDice(yes), "a plain request for dice: " + yes);
        }
        for (String no : List.of("Steve: а в твоём мире правда кости повсюду?", "Steve: расскажи про кости", "Steve: сыграем в карты",
                "Steve: игра в кости — это скучно?", "Steve: what's with the dice in your realm?")) {
            check(!Llm.wantsDice(no), "not a request for dice: " + no);
        }
        check(Llm.parseReply("{\"emotion\":\"bored\",\"message\":\"x\"}").emotion().equals("neutral"), "unknown emotion -> neutral");
        check(Llm.parseReply("Хм. {\"emotion\":\"happy\",\"message\":\"Первый {раз}\",\"actions\":[]} Стоп, не так. "
                + "{\"emotion\":\"sad\",\"message\":\"Второй\",\"actions\":[{\"name\":\"show_way\",\"args\":{}}],\"remember\":\"r2\"}")
                .equals(new Llm.Reply("sad", "Второй", List.of(new Llm.Action("show_way", Map.of())))), "two replies: the corrected one");
        check(Llm.parseReply("{\"emotion\":\"happy\",\"message\":\"Да.\",\"actions\":[]} <invoke name=\"eye_deal\">{\"x\": 1}</invoke>")
                .equals(new Llm.Reply("happy", "Да.")) && Llm.note("{\"message\":\"a\",\"remember\":\"r1\"} {\"message\":\"b\",\"remember\":\"r2\"}").equals("r2"),
                "tags after the reply, and the corrected reply's note");
        check(Llm.parseReply("just text").equals(new Llm.Reply("neutral", "just text")), "plain text");
        check(Llm.parseReply("{\"message\":\"x\",\"actions\":[\"spin\", {\"action\":\"come\"}]}").actions()
                .equals(List.of(new Llm.Action("come", Map.of()))), "loose actions");

        // 6. the "кукуку" tic goes, real words stay
        check(Llm.cleanMessage("Кукуку! Яблоко? Ку-ку-ку…").equals("Яблоко?"), "kuku: " + Llm.cleanMessage("Кукуку! Яблоко? Ку-ку-ку…"));
        check(Llm.cleanMessage("Хе-хе… кукуруза и кукушка").equals("Хе-хе… кукуруза и кукушка"), "words kept");
        check(Llm.cleanMessage("Кукуку").equals("..."), "only kuku");
        String rambling = "Слово. ".repeat(100);
        check(Llm.cleanMessage(rambling).length() <= Llm.MAX_MESSAGE && Llm.cleanMessage(rambling).endsWith("."), "long reply cut");

        // 7. Ryuk's voice effect: lower and longer, no clipping, envelope for the mouth
        short[] tone = new short[22050 / 2];
        for (int i = 0; i < tone.length; i++) {
            tone[i] = (short) (12000 * Math.sin(2 * Math.PI * 220 * i / 22050.0));
        }
        short[] voice = VoiceFx.ryuk(tone, 22050, 0.86, 0.3);
        int peak = 0;
        for (short s : voice) {
            peak = Math.max(peak, Math.abs(s));
        }
        check(voice.length > tone.length / 0.86 && voice.length < tone.length / 0.86 + 22050 / 4, "voice length: " + voice.length);
        check(peak > 25000 && peak <= 32767 * 0.9, "voice peak: " + peak);
        float[] mouth = VoiceFx.envelope(voice, 22050);
        check(mouth.length > 10 && mouth[mouth.length / 3] > 0.5F && mouth[mouth.length - 1] < 0.3F, "envelope: open while talking, closed at the end");
        check(VoiceFx.resample(new float[100], 0.5).length == 200, "resample 0.5 doubles the length");
        VoiceFx.Pcm back = VoiceFx.readWav(Stt.wav(new byte[640], 16000));
        check(back.rate() == 16000 && back.samples().length == 320, "wav read back: " + back.rate() + " Hz, " + back.samples().length);
        byte[] streamed = Stt.wav(new byte[960], 24000); // Orpheus writes 0xFFFFFFFF as the RIFF and data sizes
        java.nio.ByteBuffer.wrap(streamed).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(4, -1).putInt(40, -1);
        VoiceFx.Pcm orpheus = VoiceFx.readWav(streamed);
        check(orpheus.rate() == 24000 && orpheus.samples().length == 480, "streamed wav: " + orpheus.samples().length + " samples");
        check(VoiceFx.speakable("*хрум* Во-от. 😀 Вкусно!").equals("Во-от. Вкусно!"),
                "no stage directions or emoji out loud: " + VoiceFx.speakable("*хрум* Во-от. 😀 Вкусно!"));

        // 8. English voice: stage directions to Orpheus vocal directions, pieces of at most 200 characters
        check(Tts.direct("Heh. *chuckles* Apples! *crunch*", "neutral").equals("Heh. [dark chuckle] Apples!"),
                "directions: " + Tts.direct("Heh. *chuckles* Apples! *crunch*", "neutral"));
        check(Tts.direct("So you came back.", "sad").equals("[exasperated sigh] So you came back."), "emotion lead");
        check(Tts.direct("[dark chuckle] Hm.", "happy").equals("[dark chuckle] Hm."), "no double lead");
        String longLine = ("This is one sentence of moderate length, and it keeps going. ").repeat(9).strip();
        List<String> pieces = Tts.chunks(longLine, Tts.MAX_CHUNK);
        check(pieces.size() >= 3 && pieces.stream().allMatch(p -> p.length() <= Tts.MAX_CHUNK && p.endsWith("."))
                && String.join(" ", pieces).equals(longLine), "chunks: " + pieces);
        check(Tts.chunks("x".repeat(450), 200).stream().allMatch(p -> p.length() <= 200), "an endless word is still cut");

        // 9. Ryuk's own voice: rough1 after the tape; period doubling shows up between the harmonics of a 100 Hz buzz
        short[] buzz = new short[22050];
        for (int i = 0; i < buzz.length; i++) {
            double s = 0;
            for (int k = 1; k <= 40; k++) {
                s += Math.sin(2 * Math.PI * 100 * k * i / 22050.0) / k;
            }
            buzz[i] = (short) (6000 * s);
        }
        short[] dry = VoiceFx.rough(buzz, 22050, 1.0, 0), wet = VoiceFx.rough(buzz, 22050, 1.0, 1);
        check(wet.length == buzz.length && loudest(wet) > 28000 && loudest(wet) <= 32767 * 0.9, "rough level: " + loudest(wet));
        double dryRatio = tone(dry, 150) / tone(dry, 100), wetRatio = tone(wet, 150) / tone(wet, 100);
        check(dryRatio < 0.01 && wetRatio > 0.05, "growl between the harmonics: " + dryRatio + " -> " + wetRatio);
        check(VoiceFx.rough(buzz, 22050, 0.92, 1).length == (int) (buzz.length / 0.92), "tape 0.92 drawls");
        String said = Tts.piper("Heh heh... *chuckles* Hm? An apple! 😀 *crunch*");
        check(said.equals("Heh heh. Heh heh heh. Hmm? An apple! Crunch."), "piper text: " + said);

        System.out.println("LlmSelfTest: all checks passed");
    }

    private static int loudest(short[] pcm) {
        int peak = 0;
        for (short s : pcm) {
            peak = Math.max(peak, Math.abs(s));
        }
        return peak;
    }

    /** Goertzel: how much of one frequency is in the middle half of a 22050 Hz clip. */
    private static double tone(short[] pcm, double hz) {
        double c = 2 * Math.cos(2 * Math.PI * hz / 22050), s1 = 0, s2 = 0;
        for (int i = pcm.length / 4; i < 3 * pcm.length / 4; i++) {
            double s0 = pcm[i] + c * s1 - s2;
            s2 = s1;
            s1 = s0;
        }
        return Math.sqrt(s1 * s1 + s2 * s2 - c * s1 * s2);
    }

    /** The red eyes find the eyes of real skins: Steve's (from the game's jar, when it's here), L's, Watari's behind his glasses. */
    private static void eyesOnSkins() throws java.io.IOException {
        java.nio.file.Path people = java.nio.file.Path.of("src/main/resources/assets/aicompanion/textures/entity/people");
        long pupils = ru.maksim.aicompanion.client.EyeFinder.bit(2, 4) | ru.maksim.aicompanion.client.EyeFinder.bit(5, 4);
        long whites = ru.maksim.aicompanion.client.EyeFinder.bit(1, 4) | ru.maksim.aicompanion.client.EyeFinder.bit(6, 4);
        check(java.util.Arrays.equals(eyes(javax.imageio.ImageIO.read(people.resolve("l_0.png").toFile())), new long[]{pupils, 0}), "L's eyes, not the rings under them");
        check(java.util.Arrays.equals(eyes(javax.imageio.ImageIO.read(people.resolve("watari_0.png").toFile())), new long[]{pupils, 0}),
                "Watari's eyes behind his glasses (his whites are near his skin: the pupils only)");
        java.io.File jar = new java.io.File(System.getProperty("user.home"), ".gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar");
        if (jar.exists()) {
            try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(jar)) {
                for (String name : new String[]{"steve", "alex", "kai", "zuri"}) {
                    java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(z.getInputStream(z.getEntry("assets/minecraft/textures/entity/player/wide/" + name + ".png")));
                    check(java.util.Arrays.equals(eyes(img), new long[]{pupils, whites}), "the eyes of " + name);
                }
            }
        }
    }

    private static long[] eyes(java.awt.image.BufferedImage skin) {
        int[] face = new int[64];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int hat = skin.getRGB(40 + x, 8 + y);
                face[y * 8 + x] = ((hat >>> 24) >= 128 ? hat : skin.getRGB(8 + x, 8 + y)) & 0xFFFFFF;
            }
        }
        return ru.maksim.aicompanion.client.EyeFinder.find(face);
    }

    private static String completion(String escapedContent) {
        return "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"" + escapedContent + "\"}}]}";
    }

    /** The voice comes out of a jar (a zip) with its folders, once per version: a file changed since stays as it is. */
    private static void voiceUnpacks() throws Exception {
        java.nio.file.Path zip = java.nio.file.Files.createTempDirectory("voice").resolve("mod.jar");
        try (java.nio.file.FileSystem jar = java.nio.file.FileSystems.newFileSystem(zip, Map.of("create", "true"))) {
            java.nio.file.Files.createDirectories(jar.getPath("aicompanion_voice/piper/espeak-ng-data"));
            java.nio.file.Files.writeString(jar.getPath("aicompanion_voice/piper/espeak-ng-data/en_dict"), "dict");
        }
        java.nio.file.Path out = zip.resolveSibling("game").resolve("aicompanion"), dict = out.resolve("piper/espeak-ng-data/en_dict");
        try (java.nio.file.FileSystem jar = java.nio.file.FileSystems.newFileSystem(zip)) {
            Tts.unpack(jar.getPath("aicompanion_voice"), out, "0.1.0");
            check(java.nio.file.Files.readString(dict).equals("dict") && java.nio.file.Files.readString(out.resolve("voice.version")).equals("0.1.0"),
                    "voice unpacked from the jar");
            java.nio.file.Files.writeString(dict, "edited");
            Tts.unpack(jar.getPath("aicompanion_voice"), out, "0.1.0");
            check(java.nio.file.Files.readString(dict).equals("edited"), "the same version isn't unpacked twice");
            Tts.unpack(jar.getPath("aicompanion_voice"), out, "0.2.0");
            check(java.nio.file.Files.readString(dict).equals("dict"), "a new version replaces the voice");
        }
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError("LlmSelfTest failed: " + what);
        }
    }
}
