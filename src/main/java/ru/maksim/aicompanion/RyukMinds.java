package ru.maksim.aicompanion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * What a companion remembers about its owner, kept in the world save instead of the body: a body that gets unloaded,
 * sent to another dimension or replaced keeps the conversation. One mind per owner; the newest body claims it.
 */
public final class RyukMinds extends SavedData {
    public static final int MAX_HISTORY = 12; // even: user/assistant pairs; each one is resent with every request
    public static final int MAX_HISTORY_STRONG = 30, MAX_NOTES_STRONG = 48; // a paid model (or one's own): room for more

    /** Groq's free tier counts tokens per minute: there he keeps less; on the player's own model, more. */
    static boolean roomy() {
        return Provider.of(Config.API_URL.get()) != Provider.GROQ;
    }
    private final Map<UUID, Mind> minds = new HashMap<>();

    public static final class Mind {
        public UUID body;
        public final List<Llm.Msg> history = new ArrayList<>();
        public final List<String> notes = new ArrayList<>(); // what he remembers about the human for days (any language, kept)
        public long lastApple;
        public int apples;
        public String language = ""; // the history is in this language
        int missingChecks; // owner online, body nowhere to be found (see AiCompanion.onPlayerTick)
    }

    public static RyukMinds get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(RyukMinds::load, RyukMinds::new, AiCompanion.MODID + "_minds");
    }

    @Nullable
    public Mind get(UUID owner) {
        return minds.get(owner);
    }

    /** Makes {@code body} the owner's companion; an older body notices it lost the mind and leaves. */
    public Mind claim(UUID owner, UUID body, long gameTime) {
        Mind mind = minds.computeIfAbsent(owner, k -> {
            Mind m = new Mind();
            m.lastApple = gameTime - 12000; // arrives hungry: withdrawal starts in ~10 minutes without an apple
            return m;
        });
        mind.body = body;
        mind.missingChecks = 0;
        setDirty();
        return mind;
    }

    /** A conversation in the other language would drag him back into it: start over when the language changes. */
    public void speakIn(Mind mind, String language) {
        if (!language.equals(mind.language)) {
            mind.history.clear();
            mind.language = language;
            setDirty();
        }
    }

    public void forget(UUID owner) {
        if (minds.remove(owner) != null) {
            setDirty();
        }
    }

    public void remember(Mind mind, Llm.Msg question, Llm.Msg answer) {
        mind.history.add(question);
        mind.history.add(answer);
        while (mind.history.size() > (roomy() ? MAX_HISTORY_STRONG : MAX_HISTORY)) {
            mind.history.remove(0);
        }
        setDirty();
    }

    /** Something about the human worth keeping for days (Llm.addNote: no repeats, the oldest fades). */
    public void note(Mind mind, String note) {
        if (Llm.addNote(mind.notes, note, roomy() ? MAX_NOTES_STRONG : Llm.MAX_NOTES)) {
            setDirty();
        }
    }

    public void ateApple(Mind mind, long gameTime) {
        mind.lastApple = gameTime;
        mind.apples++;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        minds.forEach((owner, mind) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("Owner", owner);
            t.putUUID("Body", mind.body);
            t.putLong("LastApple", mind.lastApple);
            t.putInt("Apples", mind.apples);
            t.putString("Language", mind.language);
            ListTag history = new ListTag();
            for (Llm.Msg m : mind.history) {
                CompoundTag h = new CompoundTag();
                h.putString("role", m.role());
                h.putString("content", m.content());
                history.add(h);
            }
            t.put("History", history);
            ListTag notes = new ListTag();
            mind.notes.forEach(n -> notes.add(StringTag.valueOf(n)));
            t.put("Notes", notes);
            list.add(t);
        });
        tag.put("Minds", list);
        return tag;
    }

    static RyukMinds load(CompoundTag tag) {
        RyukMinds data = new RyukMinds();
        for (Tag t : tag.getList("Minds", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Mind mind = new Mind();
            mind.body = c.getUUID("Body");
            mind.lastApple = c.getLong("LastApple");
            mind.apples = c.getInt("Apples");
            mind.language = c.getString("Language");
            for (Tag h : c.getList("History", Tag.TAG_COMPOUND)) {
                CompoundTag m = (CompoundTag) h;
                mind.history.add(new Llm.Msg(m.getString("role"), m.getString("content")));
            }
            for (Tag n : c.getList("Notes", Tag.TAG_STRING)) {
                mind.notes.add(n.getAsString());
            }
            data.minds.put(c.getUUID("Owner"), mind);
        }
        return data;
    }
}
