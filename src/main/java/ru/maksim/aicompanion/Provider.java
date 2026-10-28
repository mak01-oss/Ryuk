package ru.maksim.aicompanion;

import java.util.List;
import java.util.Locale;

/**
 * Where Ryuk's words come from: the AI services the mod knows by name, all spoken to in the OpenAI chat format. Groq is
 * the free default; the others take the player's own API key (a ChatGPT or Claude subscription is not one). CUSTOM is
 * any other address in that format, a local Ollama for one. The one in use is told by the configured chat address.
 */
public enum Provider {
    GROQ("Groq", "https://api.groq.com/openai/v1/chat/completions", "https://console.groq.com/keys", "gsk_...",
            "qwen/qwen3.8-27b", List.of("^qwen/qwen3\\.8-27b$", "^qwen/qwen3-32b$", "^openai/gpt-oss-120b$", "^llama-3\\.3-70b-versatile$",
            "^openai/gpt-oss-20b$")),
    OPENAI("OpenAI (GPT)", "https://api.openai.com/v1/chat/completions", "https://platform.openai.com/api-keys", "sk-...",
            "gpt-5-mini", List.of("^gpt-[0-9.]+-mini$", "^gpt-[0-9.]+$", "^gpt-4o-mini$", "^gpt-4o$")),
    CLAUDE("Claude (Anthropic)", "https://api.anthropic.com/v1/chat/completions", "https://platform.claude.com/settings/keys", "sk-ant-...",
            "claude-sonnet-5-5", List.of("^claude-sonnet-", "^claude-haiku-", "^claude-opus-")),
    GEMINI("Gemini (Google)", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", "https://aistudio.google.com/app/apikey",
            "AIza...", "gemini-2.5-flash", List.of("^gemini-[0-9.]+-flash$", "^gemini-[0-9.]+-pro$", "^gemini-.*flash")),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1/chat/completions", "https://openrouter.ai/keys", "sk-or-...",
            "openai/gpt-5-mini", List.of("^anthropic/claude-sonnet-[0-9.]+$", "^openai/gpt-[0-9.]+-mini$", "^google/gemini-[0-9.]+-flash$",
            "^deepseek/deepseek-chat", "^anthropic/claude-haiku-[0-9.]+$", "^meta-llama/llama-.*instruct$", "^x-ai/grok-")),
    CUSTOM("Other (OpenAI format)", "http://127.0.0.1:11434/v1/chat/completions", "", "", "", List.of());

    public final String title, url, keysPage, hint, guess;
    /** Favourite models in order, as patterns: the newest match of the first that matches wins (see Llm.pick). */
    public final List<String> prefer;

    Provider(String title, String url, String keysPage, String hint, String guess, List<String> prefer) {
        this.title = title;
        this.url = url;
        this.keysPage = keysPage;
        this.hint = hint;
        this.guess = guess;
        this.prefer = prefer;
    }

    /** The service behind a chat address; an address the mod doesn't know by name is CUSTOM. */
    public static Provider of(String chatUrl) {
        String u = chatUrl.toLowerCase(Locale.ROOT);
        for (Provider p : values()) {
            if (p != CUSTOM && u.contains(java.net.URI.create(p.url).getHost())) {
                return p;
            }
        }
        return CUSTOM;
    }

    /** The lang key's tail: "groq", "openai"... */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
