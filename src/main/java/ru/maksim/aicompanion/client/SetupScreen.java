package ru.maksim.aicompanion.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import ru.maksim.aicompanion.AiCompanion;
import ru.maksim.aicompanion.Config;
import ru.maksim.aicompanion.Llm;
import ru.maksim.aicompanion.Provider;

/**
 * Setting Ryuk up without leaving the game. The first page picks the AI he talks through (Groq for free by default,
 * or the player's own key to OpenAI, Claude, Gemini, OpenRouter, or any server in the OpenAI format), with a few
 * words on it, its key site a click away, a field to paste the key into (hidden as dots, for streams) and a check that
 * asks the provider which models the key may use (a check that spends no tokens). A key that works is saved at once,
 * the best model it has is chosen, and Ryuk talks. The second page holds the language, the voice, the model and the
 * rules of the game. Opens by itself the first time a world is entered without a key, from Mods (Config), from the
 * "/ryuk" command and from the links in Ryuk's error messages.
 */
public class SetupScreen extends Screen {
    private static final String GROQ_STT = "https://api.groq.com/openai/v1/audio/transcriptions";
    private static final String OPENAI_STT = "https://api.openai.com/v1/audio/transcriptions";
    private static final int W = 300;
    @Nullable
    private final Screen parent;
    private boolean settings, shown;
    private int guideEnd; // where the guide's text ends: the buttons go under it, however it wraps
    private int modelY = -1, voiceY = -1; // the settings page's rows that have a label beside a field
    private Provider provider = Provider.of(Config.API_URL.get());
    @Nullable
    private EditBox key, address;
    private Component status = Component.empty();
    private int statusColor = 0xFF9C8C84;
    private static List<String> available = new ArrayList<>(); // from the last check, for the model button

    public SetupScreen(@Nullable Screen parent) {
        super(Component.translatable("aicompanion.setup.title"));
        this.parent = parent;
    }

    /** The "Config" button in the Mods list (called from the mod's constructor, client side only). */
    public static void registerInModList() {
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new SetupScreen(parent)));
    }

    @Override
    protected void init() {
        int x = (width - W) / 2, bottom = height - 26;
        if (!settings) {
            addRenderableWidget(CycleButton.<Provider>builder(p -> p == Provider.GROQ
                            ? Component.literal(p.title + " · ").append(Component.translatable("aicompanion.setup.free")) : Component.literal(p.title))
                    .withValues(Provider.values()).withInitialValue(provider)
                    .create(x, 28, W, 18, Component.translatable("aicompanion.setup.provider"), (b, v) -> choose(v)));
            guideEnd = 50;
            for (Component c : guide()) {
                guideEnd += Ink.height(font, c, W);
            }
            int y = guideEnd + 24;
            if (provider == Provider.CUSTOM) { // any server in the OpenAI format: its address instead of a key site
                String at = address != null ? address.getValue()
                        : Provider.of(Config.API_URL.get()) == Provider.CUSTOM ? Config.API_URL.get() : Provider.CUSTOM.url;
                address = new EditBox(font, x, guideEnd, W, 16, Component.translatable("aicompanion.setup.address"));
                address.setMaxLength(300);
                address.setValue(at);
                addRenderableWidget(address);
            } else {
                addRenderableWidget(Button.builder(Component.translatable("aicompanion.setup.open_site", URI.create(provider.keysPage).getHost())
                                .withStyle(ChatFormatting.AQUA),
                        b -> Util.getPlatform().openUri(URI.create(provider.keysPage))).bounds(x, guideEnd, W, 18).build());
            }
            String typed = key != null ? key.getValue() : Provider.of(Config.API_URL.get()) == provider ? Config.API_KEY.get() : "";
            key = new EditBox(font, x + 34, y, W - 34 - 98, 16, Component.translatable("aicompanion.setup.key"));
            key.setMaxLength(200);
            key.setValue(typed);
            key.setHint(provider == Provider.CUSTOM ? Component.translatable("aicompanion.setup.optional").withStyle(ChatFormatting.DARK_GRAY)
                    : Component.literal(provider.hint).withStyle(ChatFormatting.DARK_GRAY));
            key.setFormatter((text, start) -> FormattedCharSequence.forward(shown ? text : masked(text, start), Style.EMPTY));
            addRenderableWidget(key);
            addRenderableWidget(Button.builder(Component.translatable("aicompanion.setup.paste"), b -> {
                key.setValue(minecraft.keyboardHandler.getClipboard().strip());
                check();
            }).bounds(x + W - 94, y - 1, 64, 18).build());
            addRenderableWidget(Button.builder(Component.literal(shown ? "**" : "Aa"), b -> {
                shown = !shown;
                rebuildWidgets();
            }).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("aicompanion.setup.show")))
                    .bounds(x + W - 26, y - 1, 26, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("aicompanion.setup.check").withStyle(ChatFormatting.RED), b -> check())
                    .bounds(x, y + 22, 96, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("aicompanion.setup.more"), b -> page(true)).bounds(x, bottom, 120, 20).build());
        } else {
            settingsPage(x);
            addRenderableWidget(Button.builder(Component.translatable("aicompanion.setup.back"), b -> page(false)).bounds(x, bottom, 120, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("aicompanion.setup.done"), b -> onClose()).bounds(x + W - 120, bottom, 120, 20).build());
    }

    /** A few words on the provider shown: Groq's step-by-step guide, or what the other one is and costs, and what to do. */
    private List<Component> guide() {
        if (provider == Provider.GROQ) {
            return List.of(Component.translatable("aicompanion.setup.intro"), Component.translatable("aicompanion.setup.step1"),
                    Component.translatable("aicompanion.setup.step2"), Component.translatable("aicompanion.setup.step3"));
        }
        return List.of(Component.translatable("aicompanion.setup.about." + provider.id()),
                Component.translatable(provider == Provider.CUSTOM ? "aicompanion.setup.steps_custom" : "aicompanion.setup.steps_any"));
    }

    /** Another provider shown (nothing saved until its key checks out): its own key, if it's the one in use. */
    void choose(Provider p) {
        provider = p;
        key = null;
        address = null;
        status(Component.empty(), 0xFF9C8C84);
        rebuildWidgets();
    }

    /** The voice, the model, the rules: each saved as it changes. */
    private void settingsPage(int x) {
        int y = 46, w = W / 2 - 4, h = 20;
        Provider used = Provider.of(Config.API_URL.get());
        addRenderableWidget(CycleButton.onOffBuilder(Config.TTS.get())
                .create(x, y, W, 18, Component.translatable("aicompanion.setup.voice"), (b, v) -> set(Config.TTS, v)));
        y += h + 4;
        addRenderableWidget(CycleButton.<String>builder(v -> Component.translatable(v.equals("orpheus") ? "aicompanion.setup.voice_orpheus"
                        : "aicompanion.setup.voice_own")).withValues("ryuk", "orpheus")
                .withInitialValue(Config.ENGLISH_VOICE.get().strip().equalsIgnoreCase("orpheus") ? "orpheus" : "ryuk")
                .create(x, y, W, 18, Component.translatable("aicompanion.setup.english_voice"), (b, v) -> set(Config.ENGLISH_VOICE, v)));
        y += h + 4;
        if (used == Provider.CUSTOM) { // a server of one's own: the model by name, as it calls it
            modelY = y;
            EditBox model = new EditBox(font, x + 60, y, W - 60, 16, Component.translatable("aicompanion.setup.model"));
            model.setMaxLength(200);
            model.setValue(Config.MODEL.get());
            model.setHint(Component.translatable("aicompanion.setup.model_hint").withStyle(ChatFormatting.DARK_GRAY));
            model.setResponder(v -> {
                if (!v.isBlank()) {
                    set(Config.MODEL, v.strip());
                    set(Config.FALLBACK_MODELS, List.of());
                }
            });
            addRenderableWidget(model);
        } else {
            modelY = -1;
            List<String> models = new ArrayList<>(available.isEmpty() ? Config.models() : available);
            if (!models.contains(Config.MODEL.get())) {
                models.add(0, Config.MODEL.get());
            }
            addRenderableWidget(CycleButton.<String>builder(Component::literal).withValues(models).withInitialValue(Config.MODEL.get())
                    .create(x, y, W, 18, Component.translatable("aicompanion.setup.model"), (b, v) -> set(Config.MODEL, v)));
        }
        y += h + 4;
        addRenderableWidget(CycleButton.onOffBuilder(Config.PACT.get())
                .create(x, y, w, 18, Component.translatable("aicompanion.setup.pact"), (b, v) -> set(Config.PACT, v)));
        addRenderableWidget(CycleButton.onOffBuilder(Config.NOTEBOOK_FALLS.get())
                .create(x + W - w, y, w, 18, Component.translatable("aicompanion.setup.falls"), (b, v) -> set(Config.NOTEBOOK_FALLS, v)));
        y += h + 4;
        addRenderableWidget(CycleButton.<String>builder(v -> Component.translatable(v.equals("spectator") ? "aicompanion.setup.ghost" : "aicompanion.setup.delete"))
                .withValues("delete", "spectator").withInitialValue(Config.PERMADEATH.get().strip().equalsIgnoreCase("spectator") ? "spectator" : "delete")
                .create(x, y, W, 18, Component.translatable("aicompanion.setup.permadeath"), (b, v) -> set(Config.PERMADEATH, v)));
        voiceY = -1;
        if (used != Provider.GROQ && used != Provider.OPENAI) { // speech to text is Groq's (or OpenAI's): a Groq key of its own
            y += h + 4;
            voiceY = y;
            EditBox voice = new EditBox(font, x + 130, y, W - 130, 16, Component.translatable("aicompanion.setup.voice_key"));
            voice.setMaxLength(200);
            voice.setValue(Config.STT_API_KEY.get());
            voice.setHint(Component.literal("gsk_...").withStyle(ChatFormatting.DARK_GRAY));
            voice.setFormatter((text, start) -> FormattedCharSequence.forward(masked(text, start), Style.EMPTY));
            voice.setResponder(v -> { // after setValue: only what's typed saves
                set(Config.STT_API_KEY, v.strip());
                set(Config.STT_URL, GROQ_STT);
                set(Config.STT_MODEL, "whisper-large-v3-turbo");
            });
            addRenderableWidget(voice);
        }
    }

    private static <T> void set(ForgeConfigSpec.ConfigValue<T> value, T v) {
        value.set(v);
        value.save();
    }

    /** The field typed into (the autotest's hands). */
    void typeKey(String k) {
        key.setValue(k);
    }

    void page(boolean toSettings) {
        settings = toSettings;
        rebuildWidgets();
    }

    /** The first characters as they are (so it can be told for a key), the rest as dots. */
    private static String masked(String text, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sb.append(start + i < 4 ? text.charAt(i) : '•');
        }
        return sb.toString();
    }

    /** Asks the provider which models this key may use; a key that works (or can't be checked offline) is saved. */
    void check() {
        Provider p = provider;
        String k = key.getValue().strip(), chat = p == Provider.CUSTOM && address != null ? address.getValue().strip() : p.url;
        if (k.isEmpty() && p != Provider.CUSTOM) {
            status(Component.translatable("aicompanion.setup.empty"), 0xFFFF6A5A);
            return;
        }
        if (chat.isEmpty()) {
            status(Component.translatable("aicompanion.setup.no_address"), 0xFFFF6A5A);
            return;
        }
        String useKey = k.isEmpty() ? "local" : k; // a server on this computer may want none
        status(Component.translatable("aicompanion.setup.checking"), 0xFFE8DDD5);
        CompletableFuture.supplyAsync(() -> Llm.models(p, chat, useKey), Llm.EXECUTOR).whenComplete((models, error) -> Minecraft.getInstance().execute(() -> {
            if (error == null) {
                available = models;
                save(p, chat, useKey, models);
                status(Component.translatable("aicompanion.setup.works", models.size()), 0xFF7CE07C);
                return;
            }
            String why = Llm.explain(error);
            if (why.equals("aicompanion.msg.key_refused")) {
                status(Component.translatable("aicompanion.setup.refused"), 0xFFFF6A5A); // not saved: a broken key helps nobody
            } else if (why.equals("aicompanion.msg.limit")) {
                save(p, chat, useKey, List.of());
                status(Component.translatable("aicompanion.setup.limit"), 0xFFFFC060);
            } else {
                save(p, chat, useKey, List.of());
                status(Component.translatable("aicompanion.setup.offline"), 0xFFFFC060);
                AiCompanion.LOGGER.warn("Could not check the key", error);
            }
        }));
    }

    /**
     * Ryuk talks through this provider from now on: its address, the key, the best of its models (the player's own pick
     * stays if it's still there) with the next best as spares, and the voice input along with it where it can go.
     */
    private static void save(Provider p, String chat, String k, List<String> models) {
        boolean same = Provider.of(Config.API_URL.get()) == p;
        set(Config.API_URL, chat);
        set(Config.API_KEY, k);
        if (!(same && models.contains(Config.MODEL.get()))) {
            List<String> best = Llm.pick(p, models);
            if (best.isEmpty() && !same && !p.guess.isEmpty()) {
                best = List.of(p.guess); // couldn't ask (offline, the day's limit): the likely name, to change in the settings
            }
            if (!best.isEmpty()) {
                set(Config.MODEL, best.get(0));
                set(Config.FALLBACK_MODELS, new ArrayList<>(best.subList(1, best.size())));
            }
        }
        if (Config.STT_API_KEY.get().isBlank()) { // speech to text follows where it can: Groq's Whisper, or OpenAI's
            boolean openai = p == Provider.OPENAI;
            set(Config.STT_URL, openai ? OPENAI_STT : GROQ_STT);
            set(Config.STT_MODEL, openai ? "whisper-1" : "whisper-large-v3-turbo");
        }
    }

    private void status(Component text, int color) {
        status = text;
        statusColor = color;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0xFF0A0606, 0xFF200808);
        int x = (width - W) / 2;
        Ink.title(g, font, Component.translatable(settings ? "aicompanion.setup.title_settings" : "aicompanion.setup.title").withStyle(ChatFormatting.BOLD),
                width / 2, 10, 1.6F, 0xFFE02020);
        if (!settings) {
            int y = 50;
            boolean first = true;
            for (Component c : guide()) {
                y = Ink.paragraph(g, font, c, x, y, W, first ? 0xFFE8DDD5 : 0xFFD8CCC4);
                first = false;
            }
            int ky = guideEnd + 24;
            g.drawString(font, Component.translatable("aicompanion.setup.key"), x, ky + 4, 0xFFE8DDD5, false);
            g.drawString(font, status, x + 104, ky + 27, statusColor, false);
            Component note = Component.translatable("aicompanion.setup.private").withStyle(ChatFormatting.ITALIC);
            if (ky + 46 + Ink.height(font, note, W) <= height - 28) { // only where it fits above the bottom buttons
                Ink.paragraph(g, font, note, x, ky + 46, W, 0xFF7C6C64);
            }
        } else {
            if (!Minecraft.getInstance().hasSingleplayerServer()) {
                Ink.paragraph(g, font, Component.translatable("aicompanion.setup.server").withStyle(ChatFormatting.ITALIC), x, 28, W, 0xFFFFC060);
            }
            if (modelY >= 0) {
                g.drawString(font, Component.translatable("aicompanion.setup.model").append(":"), x, modelY + 4, 0xFFE8DDD5, false);
            }
            if (voiceY >= 0) {
                g.drawString(font, Component.translatable("aicompanion.setup.voice_key"), x, voiceY + 4, 0xFFE8DDD5, false);
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true; // a pause while one sorts out keys: nothing to miss
    }
}
