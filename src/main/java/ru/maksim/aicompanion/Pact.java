package ru.maksim.aicompanion;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Ryuk's game, offered once the notebook has killed (Onboarding), when the human knows it works; accepted once and for good. Ryuk promised Light he'd
 * write his name when his time came, and did when the game was lost. Here the mod keeps four rules, and breaking any
 * of them means Ryuk takes out his own notebook:
 * 1. your life runs out (counted on time played; every eye deal halves what is left),
 * 2. you write your own name,
 * 3. L proves you are Kira,
 * 4. Ryuk gets bored (apples and a good show keep him entertained).
 * Under the pact that death is final: in singleplayer the world is lost (deleted, or kept as a ghost world, see
 * Config.PERMADEATH); on a server you stay a spectator. Without the pact the same things happen, but you come back.
 */
public final class Pact {
    public enum Reason { TIME, OWN_NAME, CAUGHT, BORED }

    public static final int BORED = 3 * 3600;             // seconds of dull play (with Ryuk around) before he's had enough
    public static final int THRILL_APPLE = 20 * 60;        // an apple buys twenty minutes of patience
    public static final int THRILL_SMALL = 30 * 60;        // something worth watching
    public static final int THRILL_BIG = BORED;            // a death by the notebook, a deal, a story event: a fresh start

    private Pact() {
    }

    /** The answer on the pact screen. Refusing can be changed later (the rules page has the button); accepting can't. */
    public static void answer(ServerPlayer player, boolean yes) {
        Kira.Case c = Kira.of(player);
        if (!Config.PACT.get() || c.sealed || c.pact == Kira.Pact.ACCEPTED || !yes && c.pact != Kira.Pact.NONE) {
            return;
        }
        c.pact = yes ? Kira.Pact.ACCEPTED : Kira.Pact.REFUSED;
        c.boredom = 0;
        Kira.changed(player);
        AiCompanion.LOGGER.info("{} {} Ryuk's game", player.getGameProfile().getName(), yes ? "accepted" : "refused");
        CompanionEntity ryuk = CompanionEntity.of(player);
        if (ryuk != null) {
            ryuk.remark(yes ? Config.tr("твой человек принял твою игру: если его жизнь кончится, если он впишет своё имя, если L докажет, "
                                    + "что он Кира, или если тебе станет скучно, ты впишешь его имя, и он не вернётся, а этот мир исчезнет",
                            "your human accepted your game: if their life runs out, if they write their own name, if L proves they are Kira, "
                                    + "or if you get bored, you write their name, they won't come back and this world will be gone")
                            : Config.tr("твой человек отказался от твоей игры: смерть для него не будет окончательной. Скукота",
                            "your human refused your game: death won't be final for them. Boring"),
                    "pact", 0);
        }
    }

    /** A danger rising toward the end of the game: in short above the hotbar, in full in the chat. */
    static void warn(ServerPlayer player, Component brief, Component full, ChatFormatting color) {
        Network.cue(player, "sting.suspicion");
        player.displayClientMessage(brief.copy().withStyle(color), true);
        player.sendSystemMessage(full.copy().withStyle(color));
    }

    /** Something entertaining happened: Ryuk is less bored. */
    public static void thrill(ServerPlayer player, int seconds) {
        Kira.Case c = Kira.of(player);
        c.boredom = Math.max(0, c.boredom - seconds);
        Kira.get(player.server).setDirty();
    }

    /** Once a second for each player online: the lifespan runs, Ryuk's patience runs out. */
    public static void tick(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        if (c.sealed || player.isSpectator()) {
            return;
        }
        c.life -= 20;
        if (c.life <= 0) {
            finish(player, Reason.TIME);
        }
        CompanionEntity ryuk = CompanionEntity.of(player);
        if (ryuk == null) {
            return; // no shinigami around (no notebook yet): nobody to get bored
        }
        c.boredom = Math.min(BORED, c.boredom + 1);
        int percent = c.boredom * 100 / BORED;
        if (c.boredom % 60 == 0 && (percent == 50 || percent == 75 || percent == 90)) {
            warn(player, Component.translatable("aicompanion.warn.bored_short", percent),
                    Component.translatable(c.pact == Kira.Pact.ACCEPTED ? "aicompanion.warn.bored_pact" : "aicompanion.warn.bored", percent),
                    ChatFormatting.DARK_PURPLE);
            ryuk.remark(Config.tr("тебе становится скучно со своим человеком (скука " + percent + "%"
                            + (c.pact == Kira.Pact.ACCEPTED ? "; на 100% ты впишешь его имя" : "") + "). Яблоки или зрелище помогли бы",
                    "you're getting bored with your human (boredom " + percent + "%"
                            + (c.pact == Kira.Pact.ACCEPTED ? "; at 100% you write their name" : "") + "). Apples or a good show would help"),
                    "boredom", 0);
        }
        if (c.boredom >= BORED) {
            finish(player, Reason.BORED);
        }
        if (c.boredom % 10 == 0) {
            Kira.get(player.server).setDirty();
        }
    }

    /** Ryuk takes out his own notebook and writes the name; it comes due in 40 seconds, like any other. */
    public static void finish(ServerPlayer player, Reason reason) {
        Kira.Case c = Kira.of(player);
        if (c.sealed || c.flag("finishing")) {
            return;
        }
        boolean pact = c.pact == Kira.Pact.ACCEPTED;
        CompanionEntity ryuk = CompanionEntity.of(player);
        if (!pact && reason == Reason.BORED) {
            c.boredom = BORED / 2; // without the game he only sulks
            if (ryuk != null) {
                ryuk.remark(Config.tr("тебе смертельно скучно с этим человеком, но твоей игры он не принял, так что ты только ворчишь",
                        "you're bored to death with this human, but they never took your game, so you only grumble"), "bored", 0);
            }
            return;
        }
        long due = player.server.overworld().getGameTime() + DeathNotes.DELAY;
        c.flags.put("finishing", due);
        c.sealedWhy = reason.name();
        Kira.changed(player);
        DeathNotes.writeByRyuk(player, due);
        Network.card(player, Network.Broadcast.CHAPTER, Component.translatable("aicompanion.card.ryuk_writes"),
                Component.translatable("aicompanion.card.why." + reason.name().toLowerCase(java.util.Locale.ROOT))
                        .append(" ").append(Component.translatable(pact ? "aicompanion.card.final" : "aicompanion.card.not_final")), "", null,
                "music.requiem"); // forty seconds of it
        AiCompanion.LOGGER.info("Ryuk writes {}'s name: {}{}", player.getGameProfile().getName(), reason, pact ? " (final)" : "");
        if (ryuk != null) {
            ryuk.writeOwnersName(why(reason) + Config.tr(pact
                            ? " Ты достаёшь свою тетрадь и вписываешь его имя, как обещал. Через 40 секунд он умрёт и больше не вернётся. Попрощайся по-своему."
                            : " Ты достаёшь свою тетрадь и вписываешь его имя, как обещал. Через 40 секунд он умрёт (без твоей игры он, увы, вернётся).",
                    pact ? " You take out your own notebook and write their name, as you promised. In 40 seconds they die and never come back. Say goodbye your way."
                            : " You take out your own notebook and write their name, as you promised. In 40 seconds they die (without your game, sadly, they'll be back)."));
        }
    }

    private static String why(Reason reason) {
        return switch (reason) {
            case TIME -> Config.tr("Срок жизни твоего человека вышел.", "Your human's lifespan has run out.");
            case OWN_NAME -> Config.tr("Твой человек вписал в тетрадь своё собственное имя.", "Your human wrote their own name in the notebook.");
            case CAUGHT -> Config.tr("L доказал, что твой человек — Кира. Игра проиграна.", "L proved your human is Kira. The game is lost.");
            case BORED -> Config.tr("Тебе стало невыносимо скучно со своим человеком.", "You got unbearably bored with your human.");
        };
    }

    /** Ryuk's name came due (DeathNotes): under the pact it's over for good; without it a new life begins after this one. */
    static void seal(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        c.flags.remove("finishing");
        if (c.pact == Kira.Pact.ACCEPTED) {
            c.sealed = true;
        } else {
            c.life = ThreadLocalRandom.current().nextLong(100 * Kira.DAY, 140 * Kira.DAY);
            c.boredom = 0;
        }
        Kira.changed(player);
    }

    /** Back in a world where it's already over (or the name came due while away). */
    public static void onLogin(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        Kira.sync(player);
        if (c.sealed && player.isAlive() && !player.isSpectator()) {
            player.setGameMode(GameType.SPECTATOR); // the Mu screen takes it from here on the client
        }
    }

    /** Somebody got up again after a final death (another mod, a command): they stay a ghost. */
    public static void onRespawn(ServerPlayer player) {
        if (Kira.of(player).sealed) {
            player.setGameMode(GameType.SPECTATOR);
            Kira.sync(player);
        }
    }
}
