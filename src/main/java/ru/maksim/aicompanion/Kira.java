package ru.maksim.aicompanion;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.network.PacketDistributor;

/**
 * Everything the story knows about each human, saved with the world: their lifespan (counted on time actually played),
 * the eye deals, Ryuk's game (the pact) and how bored he is, whether it is over for them, and L's case against them.
 */
public final class Kira extends SavedData {
    public static final long DAY = 24000;
    private final Map<UUID, Case> cases = new HashMap<>();
    private long clock = -1, sky; // the story's own day time, and the sky's when last looked at

    public enum Pact { NONE, ACCEPTED, REFUSED }

    public static final class Case {
        public long life;                 // ticks of play left before the name writes itself (Ryuk does it)
        public int eyes;                  // eye deals made; each one halved what was left
        public Pact pact = Pact.NONE;
        public int boredom;               // seconds of dull play since the last thing Ryuk enjoyed
        public boolean sealed;            // Ryuk wrote the name under the pact: this world is over for them
        public String sealedWhy = "";
        public float suspicion;           // L's estimate that this human is Kira, in percent
        public int stage;                 // how far the investigation has come (Investigation.STAGE_*)
        public int kills, innocents, criminals;
        public long nextEvent;            // game time of the next move of the investigation
        public final Map<String, Long> flags = new HashMap<>(); // story progress: what happened (and when / where)

        public boolean flag(String name) {
            return flags.containsKey(name);
        }
    }

    /**
     * The story's clock: the sky's time of day, but it only ever moves on. Sleeping and "/time add" move it as they
     * move the sky; "/time set" back to an earlier hour counts as the clock running on to that hour (the next morning,
     * say), so "tomorrow" and "in two days" still come. Always the same hour as the sky.
     */
    public static long clock(MinecraftServer server) {
        Kira k = get(server);
        long now = server.overworld().getDayTime();
        if (k.clock < 0) {
            k.clock = now;
        } else if (now != k.sky) {
            k.clock += step(k.sky, now);
        } else {
            return k.clock;
        }
        k.sky = now;
        k.setDirty();
        return k.clock;
    }

    /** How far the story's clock moves when the sky goes from {@code last} to {@code now}: back means on to that hour. */
    static long step(long last, long now) {
        return now >= last ? now - last : Math.floorMod(now - last, DAY);
    }

    public static Kira get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(Kira::load, Kira::new, AiCompanion.MODID + "_kira");
    }

    /** Created on first use: a fresh human gets 100-140 days of life (each day = 20 minutes of play). */
    public static Case of(ServerPlayer player) {
        return get(player.server).cases.computeIfAbsent(player.getUUID(), k -> {
            Case c = new Case();
            c.life = ThreadLocalRandom.current().nextLong(100 * DAY, 140 * DAY);
            return c;
        });
    }

    /** The case of a human by id, online or not, if they have one. */
    @javax.annotation.Nullable
    static Case find(MinecraftServer server, UUID id) {
        return get(server).cases.get(id);
    }

    public static void changed(ServerPlayer player) {
        get(player.server).setDirty();
        sync(player);
    }

    /** What the player's own client needs: the eyes, the pact, whether it's over. Never the lifespan. */
    public static void sync(ServerPlayer player) {
        Case c = of(player);
        int story = (c.flag("sidoh_touched") ? 1 : 0) | (c.flag("fake_rules") ? 2 : 0);
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new Network.Fate(c.eyes, c.pact.ordinal(), c.sealed, c.sealedWhy, Config.PACT.get(), story));
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        cases.forEach((id, c) -> {
            CompoundTag t = new CompoundTag();
            t.putLong("Life", c.life);
            t.putInt("Eyes", c.eyes);
            t.putString("Pact", c.pact.name());
            t.putInt("Boredom", c.boredom);
            t.putBoolean("Sealed", c.sealed);
            t.putString("SealedWhy", c.sealedWhy);
            t.putFloat("Suspicion", c.suspicion);
            t.putInt("Stage", c.stage);
            t.putInt("Kills", c.kills);
            t.putInt("Innocents", c.innocents);
            t.putInt("Criminals", c.criminals);
            t.putLong("NextEvent", c.nextEvent);
            CompoundTag flags = new CompoundTag();
            c.flags.forEach(flags::putLong);
            t.put("Flags", flags);
            all.put(id.toString(), t);
        });
        tag.put("Cases", all);
        tag.putLong("Clock", clock);
        tag.putLong("Sky", sky);
        return tag;
    }

    static Kira load(CompoundTag tag) {
        Kira data = new Kira();
        data.clock = tag.contains("Clock") ? tag.getLong("Clock") : -1;
        data.sky = tag.getLong("Sky");
        CompoundTag all = tag.getCompound("Cases");
        for (String key : all.getAllKeys()) {
            CompoundTag t = all.getCompound(key);
            Case c = new Case();
            c.life = t.getLong("Life");
            c.eyes = t.getInt("Eyes");
            c.pact = Pact.valueOf(t.getString("Pact").isEmpty() ? "NONE" : t.getString("Pact"));
            c.boredom = t.getInt("Boredom");
            c.sealed = t.getBoolean("Sealed");
            c.sealedWhy = t.getString("SealedWhy");
            c.suspicion = t.getFloat("Suspicion");
            c.stage = t.getInt("Stage");
            c.kills = t.getInt("Kills");
            c.innocents = t.getInt("Innocents");
            c.criminals = t.getInt("Criminals");
            c.nextEvent = t.getLong("NextEvent");
            CompoundTag flags = t.getCompound("Flags");
            for (String f : flags.getAllKeys()) {
                if (flags.contains(f, Tag.TAG_LONG)) {
                    c.flags.put(f, flags.getLong(f));
                }
            }
            data.cases.put(UUID.fromString(key), c);
        }
        return data;
    }
}
