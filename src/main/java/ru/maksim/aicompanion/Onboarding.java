package ru.maksim.aicompanion;

import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.network.PacketDistributor;

/**
 * How the notebook reaches a human, the way it reached Light: it falls from the sky a few blocks away, in daylight,
 * a couple of minutes into the game (once for each human). Lost for good (the void, an explosion) it comes again two
 * minutes later; taken (the arrest, the trade with Mello) it comes the next morning: Ryuk tricks the old King out of
 * another one, as he once did. Also here: Ryuk's game is put to the human only after the notebook has killed once.
 */
public final class Onboarding {
    private static final int FIRST_FALL = 150;   // seconds of play before it falls
    private static final long LOST_AGAIN = 2400; // day time: two minutes after it was lost
    private static final int REMIND = 300;       // seconds before the reminder of where it lies

    private Onboarding() {
    }

    /** Once a second per player. */
    static void tick(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        if (c.sealed || c.flag("renounced") || player.isSpectator() || player.level().dimension() != Level.OVERWORLD) {
            return; // over, given up (he has left), or somewhere the sky can't drop anything
        }
        long now = player.server.overworld().getGameTime(), day = Kira.clock(player.server);
        boolean holds = holds(player);
        if (holds && !c.flag("day0")) {
            c.flags.put("day0", day); // the story's first day
            c.flags.put("held_at", now); // a minute later Ryuk tells of the TV and its news (News)
            Kira.get(player.server).setDirty();
        }
        if (Config.PACT.get() && c.pact == Kira.Pact.NONE && c.kills > 0 && !c.flag("pact_asked")
                && now > c.flags.getOrDefault("last_kill", now) + 100 && calm(player)) { // it works: now the stakes (not in the middle of a fight)
            offerPact(player);
        }
        if (!Config.NOTEBOOK_FALLS.get()) {
            return;
        }
        if (!c.flag("note_fell")) {
            if (holds) {
                c.flags.put("note_fell", now); // it came another way (creative, a command)
            } else {
                long played = c.flags.merge("played", 1L, Long::sum);
                Kira.get(player.server).setDirty();
                if (played >= FIRST_FALL && canFall(player, played > FIRST_FALL + 1200)) {
                    fall(player, false);
                }
            }
            return;
        }
        if (c.flag("note_again") && day >= c.flags.get("note_again")) {
            if (holds) {
                c.flags.remove("note_again");
            } else if (canFall(player, true)) {
                c.flags.remove("note_again");
                fall(player, true);
            }
        }
        if (!holds && c.flag("note_lies") && now - c.flags.getOrDefault("note_lies_at", now) > REMIND * 20L) {
            BlockPos at = BlockPos.of(c.flags.remove("note_lies"));
            player.sendSystemMessage(Component.translatable("aicompanion.note.still_lies", at.getX(), at.getZ())
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        } else if (holds && c.flag("note_lies")) {
            c.flags.remove("note_lies");
        }
    }

    /** Ryuk's game is put to the human: the screen with its rules, and he says why now. */
    static void offerPact(ServerPlayer player) {
        Kira.of(player).flags.put("pact_asked", player.server.overworld().getGameTime());
        Kira.get(player.server).setDirty();
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Network.Offer(Network.Choice.PACT));
        CompanionEntity ryuk = CompanionEntity.of(player);
        if (ryuk != null) {
            ryuk.remark(Config.tr("тетрадь убила, твой человек видел, что она работает. Теперь ты предлагаешь ему свою игру (перед ним её правила)",
                    "the notebook has killed and your human has seen it work. Now you put your game to them (its rules are in front of them)"), "pact", 0);
        }
    }

    /** Nothing hit them lately and no monster is close: a moment to read a screen. */
    private static boolean calm(ServerPlayer player) {
        return player.tickCount - player.getLastHurtByMobTimestamp() > 200 && player.serverLevel().getEntitiesOfClass(
                net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(12), m -> m instanceof net.minecraft.world.entity.monster.Enemy).isEmpty();
    }

    static boolean holds(ServerPlayer player) {
        return player.getInventory().hasAnyMatching(s -> s.is(AiCompanion.DEATH_NOTE.get()));
    }

    /** Under the open sky (and in daylight, the first time, unless the human has kept to caves and nights for 20 min). */
    private static boolean canFall(ServerPlayer player, boolean anyTime) {
        long t = player.serverLevel().getDayTime() % Kira.DAY;
        return (anyTime || t < 12000) && player.serverLevel().canSeeSky(player.blockPosition().above()) && !player.isInWater()
                && !player.isSleeping() && player.isAlive();
    }

    /** It drops out of the sky a few blocks in front of the human, glowing, and stays until it's picked up. */
    static void fall(ServerPlayer player, boolean again) {
        ServerLevel level = player.serverLevel();
        Kira.Case c = Kira.of(player);
        BlockPos spot = player.blockPosition(); // nowhere better (a cliff, water, trees): at the human's feet
        for (int i = 0; i < 8; i++) { // a few blocks ahead, under the open sky, on dry ground about level with them
            double a = Math.toRadians(player.getYRot() + (i % 2 == 0 ? 1 : -1) * (12 + i * 4)), r = 4 + i % 3;
            BlockPos p = Builds.ground(level, Heightmap.Types.MOTION_BLOCKING,
                    BlockPos.containing(player.getX() - Math.sin(a) * r, player.getY(), player.getZ() + Math.cos(a) * r));
            if (Math.abs(p.getY() - player.getBlockY()) <= 2 && level.getFluidState(p.below()).isEmpty() && level.canSeeSky(p)
                    && !level.getBlockState(p.below()).is(net.minecraft.world.level.block.Blocks.CACTUS)) {
                spot = p;
                break;
            }
        }
        ItemStack stack = new ItemStack(AiCompanion.DEATH_NOTE.get());
        mark(stack, player.getUUID()); // theirs from the moment it falls: if it's lost before it's picked up, another comes
        ItemEntity note = new ItemEntity(level, spot.getX() + 0.5, spot.getY() + 22, spot.getZ() + 0.5, stack, 0, -0.2, 0);
        note.setUnlimitedLifetime();
        note.setGlowingTag(true);
        level.addFreshEntity(note);
        level.sendParticles(ParticleTypes.SMOKE, note.getX(), note.getY(), note.getZ(), 12, 0.2, 0.2, 0.2, 0.01);
        level.playSound(null, spot, Sounds.NOTE_FALL.get(), SoundSource.AMBIENT, 1.0F, 1.0F); // the whistle down and the thud as it lands
        c.flags.put("note_fell", level.getGameTime());
        c.flags.put("note_lies", spot.asLong());
        c.flags.put("note_lies_at", level.getGameTime());
        Kira.get(player.server).setDirty();
        player.sendSystemMessage(Component.translatable(again ? "aicompanion.note.fell_again" : "aicompanion.note.fell")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        Network.card(player, Network.Broadcast.CHAPTER, Component.translatable("aicompanion.card.note"),
                Component.translatable(again ? "aicompanion.note.fell_again" : "aicompanion.card.note_text"), "", null);
        CompanionEntity ryuk = CompanionEntity.of(player);
        if (again && ryuk != null) {
            ryuk.remark(Config.tr("тетради у твоего человека больше нет, и ты бросил ему новую: снова выманил её у старика-Короля, как когда-то. Она только что упала рядом",
                    "your human has no notebook any more, so you dropped them a new one: you tricked the old King out of it again, as you once did. It just fell nearby"), "story", 0);
        }
    }

    /** A notebook item was destroyed (the void, an explosion): if it was its human's only one, another comes. */
    static void destroyed(MinecraftServer server, ItemStack stack) {
        if (server == null || !Config.NOTEBOOK_FALLS.get() || DeathNoteItem.fake(stack) || Mafia.isSidohsNote(stack)
                || !stack.hasTag() || !stack.getTag().hasUUID("Owner")) {
            return;
        }
        UUID id = stack.getTag().getUUID("Owner");
        Kira.Case c = Kira.find(server, id);
        ServerPlayer owner = server.getPlayerList().getPlayer(id);
        if (c == null || c.sealed || c.flag("renounced") || owner != null && holds(owner)) {
            return;
        }
        c.flags.put("note_again", Kira.clock(server) + LOST_AGAIN);
        Kira.get(server).setDirty();
        CompanionEntity ryuk = owner == null ? null : CompanionEntity.of(owner);
        if (ryuk != null) {
            ryuk.remark(Config.tr("тетрадь твоего человека пропала навсегда. Ты никуда не улетаешь: скоро бросишь ему другую, тебе любопытно, что дальше",
                    "your human's notebook is gone for good. You're not going anywhere: soon you'll drop them another, you want to see what comes next"), "story", 0);
        }
    }

    /** The notebook was taken away (the arrest, the trade): if none is left, another one falls the next morning. */
    static boolean takenAway(ServerPlayer player) {
        if (!Config.NOTEBOOK_FALLS.get() || holds(player)) {
            return false;
        }
        long day = Kira.clock(player.server);
        Kira.of(player).flags.put("note_again", day / Kira.DAY * Kira.DAY + Kira.DAY + 1000);
        Kira.get(player.server).setDirty();
        return true;
    }

    /** Whose notebook this is: the last human who held it (for {@link #destroyed}). */
    static void mark(ItemStack stack, UUID holder) {
        if (!stack.hasTag() || !stack.getTag().hasUUID("Owner") || !stack.getTag().getUUID("Owner").equals(holder)) {
            stack.getOrCreateTag().putUUID("Owner", holder);
        }
    }
}
