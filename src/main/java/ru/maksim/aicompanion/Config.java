package ru.maksim.aicompanion;

import net.minecraftforge.common.ForgeConfigSpec;

/** COMMON config (config/aicompanion-common.toml): never synced to clients, so the API key stays on the machine that calls the LLM. */
public final class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<String> API_URL;
    public static final ForgeConfigSpec.ConfigValue<String> API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> MODEL;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> FALLBACK_MODELS;
    public static final ForgeConfigSpec.ConfigValue<String> NAME;
    public static final ForgeConfigSpec.ConfigValue<String> EXTRA_PERSONA;
    public static final ForgeConfigSpec.ConfigValue<String> STT_URL;
    public static final ForgeConfigSpec.ConfigValue<String> STT_API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> STT_MODEL;
    public static final ForgeConfigSpec.ConfigValue<String> STT_LANGUAGE;
    public static final ForgeConfigSpec.BooleanValue TTS;
    public static final ForgeConfigSpec.ConfigValue<String> ENGLISH_VOICE;
    public static final ForgeConfigSpec.ConfigValue<String> RYUK_MODEL;
    public static final ForgeConfigSpec.DoubleValue RYUK_SPEED;
    public static final ForgeConfigSpec.DoubleValue RYUK_PITCH;
    public static final ForgeConfigSpec.DoubleValue RYUK_ROUGH;
    public static final ForgeConfigSpec.ConfigValue<String> ORPHEUS_URL;
    public static final ForgeConfigSpec.ConfigValue<String> ORPHEUS_MODEL;
    public static final ForgeConfigSpec.ConfigValue<String> ORPHEUS_VOICE;
    public static final ForgeConfigSpec.DoubleValue ORPHEUS_PITCH;
    public static final ForgeConfigSpec.DoubleValue ORPHEUS_RASP;
    public static final ForgeConfigSpec.ConfigValue<String> TTS_PIPER;
    public static final ForgeConfigSpec.DoubleValue TTS_VOLUME;
    public static final ForgeConfigSpec.BooleanValue PACT;
    public static final ForgeConfigSpec.BooleanValue NOTEBOOK_FALLS;
    public static final ForgeConfigSpec.ConfigValue<String> PERMADEATH;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("llm");
        API_URL = b.comment("OpenAI-compatible chat/completions endpoint. Groq by default; OpenRouter or a local Ollama (http://127.0.0.1:11434/v1/chat/completions) work too.")
                .define("apiUrl", "https://api.groq.com/openai/v1/chat/completions");
        API_KEY = b.comment("API key. Groq: https://console.groq.com/keys")
                .define("apiKey", "");
        MODEL = b.comment("Model id at the provider. On Groq qwen/qwen3.8-27b is the liveliest (openai/gpt-oss-120b works too)")
                .define("model", "qwen/qwen3.8-27b");
        FALLBACK_MODELS = b.comment("Who answers when the model above runs out of tokens for the day (on Groq every model has its own daily limit)")
                .defineListAllowEmpty(java.util.List.of("fallbackModels"), () -> java.util.List.of("openai/gpt-oss-120b", "openai/gpt-oss-20b"),
                        o -> o instanceof String s && !s.isBlank());
        b.pop().push("character");
        NAME = b.comment("Name given to a newly spawned companion. Empty = Ryuk")
                .define("name", "");
        EXTRA_PERSONA = b.comment("Added to the built-in character sheet (data/aicompanion/ryuk/persona_en.md in the mod jar). Empty = canon Ryuk")
                .define("extraPersona", "");
        b.pop().push("voice");
        STT_URL = b.comment("Speech to text (hold V to talk): OpenAI-compatible audio/transcriptions endpoint")
                .define("sttUrl", "https://api.groq.com/openai/v1/audio/transcriptions");
        STT_API_KEY = b.comment("Key for sttUrl and orpheusUrl. Empty = the llm apiKey (fine for Groq)")
                .define("sttApiKey", "");
        STT_MODEL = b.define("sttModel", "whisper-large-v3-turbo");
        STT_LANGUAGE = b.comment("Language you speak, ISO code. Empty = en (he always answers in English)")
                .define("sttLanguage", "");
        TTS = b.comment("Ryuk speaks his lines out loud: in his own voice (or Groq Orpheus)")
                .define("tts", true);
        ENGLISH_VOICE = b.comment("English voice: ryuk = his own, offline and free (the Piper model below); "
                        + "orpheus = Groq Orpheus, online, about 100 lines a day on the free tier")
                .define("englishVoice", "ryuk");
        RYUK_MODEL = b.comment("His own English voice: Piper model (.onnx with its .onnx.json next to it), relative to the game folder")
                .define("ryukModel", "aicompanion/voices/en_US-ryuk-medium.onnx");
        RYUK_SPEED = b.comment("His voice: Piper length_scale, above 1 = slower")
                .defineInRange("ryukSpeed", 1.0, 0.5, 2.0);
        RYUK_PITCH = b.comment("His voice: below 1 = lower and slower at once, like a slowed tape")
                .defineInRange("ryukPitch", 0.92, 0.6, 1.2);
        RYUK_ROUGH = b.comment("His voice: how gravelly and hoarse (1 = as tuned, 0 = clean)")
                .defineInRange("ryukRough", 1.0, 0.0, 1.5);
        ORPHEUS_URL = b.define("orpheusUrl", "https://api.groq.com/openai/v1/audio/speech");
        ORPHEUS_MODEL = b.comment("The terms of this model must be accepted once in the Groq console (see the play guide)")
                .define("orpheusModel", "canopylabs/orpheus-v1-english");
        ORPHEUS_VOICE = b.comment("Male voices: troy, daniel, austin")
                .define("orpheusVoice", "troy");
        ORPHEUS_PITCH = b.comment("Below 1 = lower and slower at once, like a slowed tape")
                .defineInRange("orpheusPitch", 0.94, 0.6, 1.2);
        ORPHEUS_RASP = b.comment("Hoarseness 0..1 added on top of the voice")
                .defineInRange("orpheusRasp", 0.12, 0.0, 1.0);
        TTS_PIPER = b.comment("piper.exe (his own voice), relative to the game folder")
                .define("ttsPiper", "aicompanion/piper/piper.exe");
        TTS_VOLUME = b.defineInRange("ttsVolume", 1.0, 0.0, 2.0);
        b.pop().push("rules");
        PACT = b.comment("Once the notebook has killed, Ryuk offers his game: break one of its rules and he writes your name, for good")
                .define("pact", true);
        NOTEBOOK_FALLS = b.comment("The notebook falls from the sky near each new player a couple of minutes in (and again if it's lost for good). "
                        + "false = it comes only from a command or the creative tab")
                .define("notebookFalls", true);
        PERMADEATH = b.comment("What a lost game takes in singleplayer: delete = the world is deleted as you leave it; "
                        + "spectator = it stays, and you are a ghost in it. On a server it's always spectator")
                .define("permadeath", "delete");
        b.pop();
        SPEC = b.build();
    }

    private Config() {
    }

    /**
     * The mod speaks English only (since 22.10.2026: the Russian voice's data was licensed for non-commercial use only).
     * shortcut: the Russian halves of tr() stay in the code, never used; strip them if the code gets a clean-up pass.
     */
    public static boolean english() {
        return true;
    }

    /** The same text for the model: tr("в руке", "holding") is "holding". */
    public static String tr(String ru, String en) {
        return en;
    }

    public static String name() {
        return NAME.get().isBlank() ? "Ryuk" : NAME.get();
    }

    /** The model, then its fallbacks, each once. */
    public static java.util.List<String> models() {
        java.util.Set<String> all = new java.util.LinkedHashSet<>();
        all.add(MODEL.get());
        all.addAll(FALLBACK_MODELS.get());
        return java.util.List.copyOf(all);
    }

    /** Ryuk's own voice (local Piper model) rather than Orpheus. */
    public static boolean ownVoice() {
        return !ENGLISH_VOICE.get().strip().equalsIgnoreCase("orpheus");
    }

    public static String sttLanguage() {
        return STT_LANGUAGE.get().isBlank() ? "en" : STT_LANGUAGE.get();
    }

    public static String speechKey() {
        return STT_API_KEY.get().isBlank() ? API_KEY.get() : STT_API_KEY.get();
    }
}
