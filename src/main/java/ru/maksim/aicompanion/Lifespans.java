package ru.maksim.aicompanion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * True names and lifespans, the way a god of death sees them. The humans of this world: players, villagers, wandering
 * traders, witches, illagers and the story's own people. Zombies are already dead: no name, no numbers. A name tag is
 * only what somebody called a creature; the true name comes with the person, and only that one works in the notebook.
 * Also remembers which faces each notebook owner has seen: the notebook needs a face in the writer's mind.
 */
public final class Lifespans {
    private static final String NAME = "aicompanion_name", DIES = "aicompanion_dies";
    public static final String IMMUNE = "aicompanion_immune"; // its name was misspelled four times: the notebook can't take it
    static final ResourceKey<DamageType> TIME = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(AiCompanion.MODID, "time"));
    private static final int SIGHT = 40, FACE = 32;
    private static final long FACE_MEMORY = 3 * Kira.DAY; // a face seen days ago is still in mind
    private static final Map<UUID, LinkedHashMap<UUID, Long>> FACES = new HashMap<>(); // writer -> face -> last seen

    private Lifespans() {
    }

    public static boolean human(Entity e) {
        return e instanceof Player || e instanceof AbstractVillager || e instanceof Witch || e instanceof AbstractIllager
                || e instanceof NamedHuman;
    }

    /** The name a shinigami reads above the head, in Latin letters (Names.shown for the eyes). */
    public static String trueName(LivingEntity e) {
        if (e instanceof Player p) {
            return p.getGameProfile().getName();
        }
        if (e instanceof NamedHuman h) {
            return h.trueName();
        }
        CompoundTag tag = e.getPersistentData();
        if (!tag.contains(NAME)) {
            tag.putString(NAME, Names.of(e.getUUID(), e instanceof Witch || (e.getUUID().getLeastSignificantBits() & 1) == 0));
        }
        return tag.getString(NAME);
    }

    /** Game time (overworld clock) when this life runs out. Players: now + what's left of their play time. */
    public static long dies(LivingEntity e) {
        MinecraftServer server = e.getServer();
        long now = server.overworld().getGameTime();
        if (e instanceof ServerPlayer p) {
            return now + Kira.of(p).life;
        }
        if (e instanceof NamedHuman h) {
            return h.dies();
        }
        CompoundTag tag = e.getPersistentData();
        if (!tag.contains(DIES)) {
            Random r = new Random(e.getUUID().getLeastSignificantBits() ^ 0x5DEECE66DL);
            double days = r.nextFloat() < 0.06F ? 0.05 + r.nextFloat() * 2 : 20 + r.nextFloat() * 180; // a few are near the end
            tag.putLong(DIES, now + (long) (days * Kira.DAY));
        }
        return tag.getLong(DIES);
    }

    /**
     * The eyes and the notebook both need the face: more than half of it, from the front. Not the back of a head, and
     * not under a carved pumpkin or a skull (in the manga, helmets kept the mafia's eyes from reading names).
     */
    /** A carved pumpkin or a skull over the face: nobody reads it. */
    public static boolean masked(LivingEntity e) {
        ItemStack head = e.getItemBySlot(EquipmentSlot.HEAD);
        return head.is(Items.CARVED_PUMPKIN) || head.is(AiCompanion.SIDOH_BANDAGE.get())
                || head.getItem() instanceof BlockItem b && b.getBlock() instanceof AbstractSkullBlock;
    }

    public static boolean faceVisible(LivingEntity viewer, LivingEntity target) {
        if (masked(target)) {
            return false;
        }
        Vec3 face = Vec3.directionFromRotation(target.getXRot(), target.getYHeadRot());
        return face.dot(viewer.getEyePosition().subtract(target.getEyePosition()).normalize()) > 0.1;
    }

    /** Death Note owners: the eyes can't read their lifespan (that's how one Kira can spot another). */
    public static boolean ownsNote(LivingEntity e) {
        if (e instanceof Player p) {
            return p.getInventory().hasAnyMatching(s -> s.is(AiCompanion.DEATH_NOTE.get()));
        }
        return e instanceof NamedHuman h && h.ownsNote();
    }

    /** Has the writer seen this face (recently enough to picture it)? */
    public static boolean seen(ServerPlayer writer, LivingEntity target) {
        if (target == writer) {
            return true;
        }
        Long when = FACES.getOrDefault(writer.getUUID(), new LinkedHashMap<>()).get(target.getUUID());
        return when != null && writer.server.overworld().getGameTime() - when < FACE_MEMORY;
    }

    /** Every face the writer remembers, most recent first: the candidates for a name written in the notebook. */
    public static List<UUID> faces(ServerPlayer writer) {
        List<UUID> out = new ArrayList<>(FACES.getOrDefault(writer.getUUID(), new LinkedHashMap<>()).keySet());
        java.util.Collections.reverse(out);
        return out;
    }

    /** Once a second: faces seen, the eyes' view, lives that ran out on their own. */
    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }
            Kira.Case c = Kira.of(player);
            boolean owner = ownsNote(player) || c.eyes > 0;
            List<Network.Sight.Entry> sight = c.eyes > 0 ? new ArrayList<>() : null;
            ServerLevel level = player.serverLevel();
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SIGHT),
                    e -> e != player && e.isAlive() && (human(e) || e.hasCustomName()) && !e.isSpectator())) {
                boolean visible = owner && player.hasLineOfSight(e) && (!human(e) || faceVisible(player, e)); // a pig has no face to hide
                if (owner && visible && e.distanceTo(player) < FACE) {
                    remember(player, e, now); // humans, and creatures a human gave a name to
                }
                if (!human(e)) {
                    continue;
                }
                if (sight != null && visible) {
                    long left = (dies(e) - now) / 20;
                    sight.add(new Network.Sight.Entry(e.getId(), Names.shown(trueName(e)), ownsNote(e) ? -1 : (int) Math.max(0, Math.min(left, Integer.MAX_VALUE))));
                }
                if (!(e instanceof Player) && !(e instanceof NamedHuman) && now >= dies(e)) {
                    if (kept(e)) {
                        e.getPersistentData().remove(DIES); // somebody's villager: its life starts over instead
                    } else {
                        timeIsUp(level, e);
                    }
                }
            }
            if (sight != null) {
                Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Network.Sight(sight));
            }
        }
    }

    /** A face seen on a screen (the broadcast): in mind just the same. */
    static void saw(ServerPlayer player, LivingEntity e) {
        remember(player, e, player.server.overworld().getGameTime());
    }

    private static void remember(ServerPlayer player, LivingEntity e, long now) {
        LinkedHashMap<UUID, Long> faces = FACES.computeIfAbsent(player.getUUID(), k -> new LinkedHashMap<>(64, 0.75F, true));
        faces.put(e.getUUID(), now);
        while (faces.size() > 512) {
            faces.remove(faces.keySet().iterator().next()); // the face seen longest ago fades
        }
    }

    /** Somebody's villager (traded with, named, on a lead, in a cart) or an admin's NPC: the lifespans leave it be. */
    private static boolean kept(LivingEntity e) {
        return e.hasCustomName() || e.isInvulnerable() || e.isPassenger() || e instanceof Mob m && (m.isNoAi() || m.isLeashed())
                || e instanceof Villager v && v.getVillagerXp() > 0;
    }

    /** Nobody wrote it: the life simply ended (the eyes showed it coming). */
    private static void timeIsUp(ServerLevel level, LivingEntity e) {
        e.hurt(new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TIME)), Float.MAX_VALUE);
    }

    @Nullable
    public static LivingEntity byUuid(MinecraftServer server, UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null) {
            return player;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }
}
