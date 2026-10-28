package ru.maksim.aicompanion;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

/** /companion ... (op level 2): pokes the nearest companion by hand, no LLM involved. For testing and the autotest. */
final class CompanionCommand {
    private static final List<String> ACTIONS = List.of("eat", "withdrawal", "handstand", "spin", "show_wings", "dance", "think", "surprised",
            "wave", "bow", "backflip", "stretch", "peek", "clap", "shrug", "doze", "point", "rub_hands", "perch", "juggle", "look_around",
            "cackle", "sulk", "dice_shake", "dice_slam", "write");

    private CompanionCommand() {
    }

    /** A link in chat that opens the setup ("/ryuk"). */
    static Component setupLink() {
        return Component.translatable("aicompanion.msg.setup_link").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/ryuk")));
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ryuk").executes(c -> { // for everyone, no cheats needed: the setup screen
            ServerPlayer p = c.getSource().getPlayerOrException();
            Network.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), new Network.Offer(Network.Choice.SETUP));
            return 1;
        }));
        dispatcher.register(Commands.literal("companion").requires(s -> s.hasPermission(2))
                .then(Commands.literal("emotion").then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Llm.EMOTIONS, b))
                        .executes(c -> nearest(c, e -> e.debugEmotion(StringArgumentType.getString(c, "name"))))))
                .then(Commands.literal("action").then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(ACTIONS, b))
                        .executes(c -> nearest(c, e -> e.debugAction(StringArgumentType.getString(c, "name"))))))
                .then(Commands.literal("say").then(Commands.argument("emotion", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Llm.EMOTIONS, b))
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(c -> nearest(c, e -> e.debugSay(player(c), StringArgumentType.getString(c, "emotion"),
                                        StringArgumentType.getString(c, "text")))))))
                .then(Commands.literal("favour").then(Commands.argument("name", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("look_for_cameras", "scout"), b))
                        .executes(c -> nearest(c, e -> e.debugFavour(player(c), StringArgumentType.getString(c, "name"))))))
                .then(Commands.literal("stay").executes(c -> nearest(c, e -> e.debugStay(true))))
                .then(Commands.literal("follow").executes(c -> nearest(c, e -> e.debugStay(false))))
                .then(Commands.literal("hold").executes(c -> nearest(c, CompanionEntity::debugHold)))
                .then(Commands.literal("dice").executes(c -> nearest(c, e -> DiceGame.open(player(c), e))))
                .then(Commands.literal("quest").then(Commands.literal("sidoh").executes(c -> {
                    SideQuests.startSidoh(player(c));
                    return 1;
                })).then(Commands.literal("realm").executes(c -> {
                    SideQuests.inviteRealm(player(c));
                    return 1;
                })))
                .then(Commands.literal("summon").executes(c -> {
                    CompanionEntity.summon(c.getSource().getPlayerOrException(), false);
                    return 1;
                }))
                .then(Commands.literal("kira")
                        .then(Commands.literal("eyes").executes(c -> {
                            Eyes.offer(c.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("finish").then(Commands.argument("reason", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("TIME", "OWN_NAME", "CAUGHT", "BORED"), b))
                                .executes(c -> {
                                    Pact.finish(c.getSource().getPlayerOrException(),
                                            Pact.Reason.valueOf(StringArgumentType.getString(c, "reason").toUpperCase(Locale.ROOT)));
                                    return 1;
                                })))
                        .then(Commands.literal("life").then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                                .executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    Kira.of(p).life = IntegerArgumentType.getInteger(c, "seconds") * 20L;
                                    Kira.changed(p);
                                    return 1;
                                })))
                        .then(Commands.literal("bored").then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                .executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    Kira.of(p).boredom = Pact.BORED * IntegerArgumentType.getInteger(c, "percent") / 100;
                                    Kira.changed(p);
                                    return 1;
                                })))
                        .then(Commands.literal("suspicion").then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                .executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    Kira.Case k = Kira.of(p);
                                    k.kills = Math.max(k.kills, 1);
                                    Investigation.suspect(p, IntegerArgumentType.getInteger(c, "percent") - k.suspicion, "debug");
                                    return 1;
                                })))
                        .then(Commands.literal("broadcast").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Kira.of(p).kills = Math.max(Kira.of(p).kills, 1);
                            Investigation.broadcast(p);
                            return 1;
                        }))
                        .then(Commands.literal("gallery").executes(c -> {
                            Builds.gallery(c.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("hq").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Headquarters.raise(p, p.blockPosition().offset(0, 0, 30));
                            return 1;
                        }))
                        .then(Commands.literal("hideout").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            BlockPos hall = Mafia.raise(p, 24);
                            Builds.afterBuild(() -> p.teleportTo(hall.getX() + 0.5, hall.getY(), hall.getZ() + 6.5)); // into the hall, at the foot of the boss's steps
                            c.getSource().sendSuccess(() -> Component.literal("hideout hall at " + hall.toShortString()), false);
                            return 1;
                        }))
                        .then(Commands.literal("sidoh").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            BlockPos hall = Mafia.start(p, 24);
                            Builds.afterBuild(() -> p.teleportTo(hall.getX() + 0.5, hall.getY(), hall.getZ() + 6.5)); // at the foot of the boss's steps
                            return 1;
                        }))
                        .then(Commands.literal("renounce").executes(c -> nearest(c, e -> e.renounce(player(c)))))
                        .then(Commands.literal("dream").executes(c -> {
                            Realm.enter(c.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("wake").executes(c -> {
                            Realm.wake(c.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("successors").executes(c -> { // as if L died a day ago
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Kira.Case k = Kira.of(p);
                            k.kills = Math.max(k.kills, 1);
                            k.flags.put("l_dead", p.server.overworld().getGameTime());
                            k.stage = Investigation.SUCCESSORS;
                            Successors.arrive(p);
                            return 1;
                        }))
                        .then(Commands.literal("hostage").executes(c -> {
                            Successors.kidnap(c.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("swap").executes(c -> { // Gevanni, now, at the bed
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            BlockPos bed = p.getRespawnPosition() != null ? p.getRespawnPosition() : p.blockPosition();
                            int n = Successors.swap(p.serverLevel(), bed);
                            if (n > 0) {
                                Kira.of(p).flags.putIfAbsent("swapped", p.server.overworld().getGameTime());
                            }
                            c.getSource().sendSuccess(() -> Component.literal("swapped " + n), false);
                            return 1;
                        }))
                        .then(Commands.literal("newworld").executes(c -> { // Kira has won: the ending's film, now
                            Successors.kiraWins(c.getSource().getPlayerOrException());
                            return 1;
                        }))
                        .then(Commands.literal("yellowbox").executes(c -> { // the meeting, right now
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Successors.invite(p);
                            Kira.of(p).flags.put("meeting", Kira.clock(p.server)); // now
                            return 1;
                        }))
                        .then(Commands.literal("glory").then(Commands.argument("value", IntegerArgumentType.integer(-100, 100))
                                .executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    Kira.Case k = Kira.of(p);
                                    k.kills = Math.max(k.kills, 1);
                                    k.flags.put("glory", (long) IntegerArgumentType.getInteger(c, "value"));
                                    Kira.changed(p);
                                    return 1;
                                })))
                        .then(Commands.literal("search").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Investigation.search(p, p.server.overworld().getGameTime());
                            return 1;
                        }))
                        .then(Commands.literal("cameras").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Cameras.install(p.serverLevel(), p.blockPosition(), 3);
                            return 1;
                        }))
                        .then(Commands.literal("status").executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Kira.Case k = Kira.of(p);
                            c.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                                    "life %ds, eyes %d, pact %s, boredom %d%%, sealed %s, suspicion %.1f%%, stage %d, kills %d, flags %s",
                                    k.life / 20, k.eyes, k.pact, k.boredom * 100 / Pact.BORED, k.sealed, k.suspicion, k.stage, k.kills, k.flags)), false);
                            return 1;
                        }))));
    }

    private static int nearest(CommandContext<CommandSourceStack> c, Consumer<CompanionEntity> f) {
        CommandSourceStack source = c.getSource();
        CompanionEntity companion = source.getLevel().getEntitiesOfClass(CompanionEntity.class,
                        new AABB(source.getPosition(), source.getPosition()).inflate(32)).stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(source.getPosition()))).orElse(null);
        if (companion == null) {
            source.sendFailure(Component.translatable("aicompanion.cmd.no_companion"));
            return 0;
        }
        f.accept(companion);
        return 1;
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> c) {
        try {
            return c.getSource().getPlayerOrException();
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("run it as a player", e);
        }
    }
}
