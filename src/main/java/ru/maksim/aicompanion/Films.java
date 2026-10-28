package ru.maksim.aicompanion;

import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Meeting someone of the story for the first time, as a little film on that player's screen (client/Cinema): a god of
 * death (Gook, Deridovely, Armonia, the King, Sidoh) or one of the people hunting Kira (L, Near, Mello). Their name
 * comes up big, the camera turns to them, a sting; once per player each ("film_" flags).
 */
final class Films {
    private static final java.util.Map<java.util.UUID, Long> SHOWING = new java.util.HashMap<>(); // till when a film is on that screen

    private Films() {
    }

    /** A film of {@code ticks} starts on this player's screen: another waits till it's over. */
    static void showing(ServerPlayer player, int ticks) {
        SHOWING.put(player.getUUID(), player.server.overworld().getGameTime() + ticks);
    }

    static boolean busy(ServerPlayer player) {
        return player.server.overworld().getGameTime() < SHOWING.getOrDefault(player.getUUID(), 0L);
    }

    /** {@code who}: the camera turns to it; or {@code at}, a place (the King hangs in his chains, no entity). */
    static void meet(ServerPlayer player, String key, @Nullable Entity who, @Nullable Vec3 at) {
        Kira.Case c = Kira.of(player);
        if (c.flag("film_" + key) || busy(player)) {
            return; // seen already, or another film is on: this one comes when that's over
        }
        showing(player, 130);
        c.flags.put("film_" + key, player.server.overworld().getGameTime());
        Kira.get(player.server).setDirty();
        String target = who != null ? "@" + who.getId() : at != null ? String.format(Locale.ROOT, "@%.1f,%.1f,%.1f", at.x, at.y, at.z) : "";
        Network.cue(player, "cine." + key + target);
    }

    /** In the realm, once a second: a god in sight close by that this dreamer has never met. */
    static void gods(ServerPlayer player, ServerLevel realm, Vec3 king) {
        for (RealmGod g : realm.getEntitiesOfClass(RealmGod.class, player.getBoundingBox().inflate(11), player::hasLineOfSight)) {
            meet(player, g.who().equals("armonia") ? "god.armonia" : "god.gamblers", g, null); // Gook and Deridovely: the pair, one film
        }
        for (SidohEntity s : realm.getEntitiesOfClass(SidohEntity.class, player.getBoundingBox().inflate(9), SidohEntity::inRealm)) {
            meet(player, "god.sidoh", s, null);
        }
        if (player.position().distanceTo(king) < 30) {
            meet(player, "god.king", null, king);
        }
    }

    /** One of the hunters, close and in sight, for the first time (from HumanEntity's tick). */
    static void people(HumanEntity h) {
        if (!(h.level() instanceof ServerLevel level) || h.tickCount % 20 != 0) {
            return;
        }
        String key = switch (h.role()) {
            case L -> "person.l";
            case NEAR -> "person.near";
            case MELLO -> "person.mello";
            default -> null;
        };
        if (key == null) {
            return;
        }
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, h.getBoundingBox().inflate(12), p -> p.hasLineOfSight(h) && !p.isSpectator())) {
            meet(p, key, h, null);
        }
    }
}
