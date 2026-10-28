package ru.maksim.aicompanion;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.PacketDistributor;

/**
 * Chō-han with Ryuk, for apples: two dice under his skull cup, even or odd. The player stakes apples (they leave the
 * inventory at once), he shakes the cup and slams it down, the player calls; the dice were rolled when the stake came in
 * and the player learns them only after the call. A win pays the stake twice over; a loss feeds him. Two sixes are "the
 * shinigami's eyes" (three times the stake, whatever the call), two ones are "snake eyes" (he takes it, whatever the
 * call). Closing the table before the call gives the stake back. The table is the client's DiceScreen.
 */
public final class DiceGame {
    public static final int OPEN = 0, SHAKING = 1, CALL = 2, RESULT = 3, CLOSED = 4;
    public static final int MAX_STAKE = 10, SHAKE_TICKS = 34, REVEAL_TICKS = 26;
    public static final int PLAIN = 0, EYES = 1, SNAKE = 2;
    private static final Map<UUID, Table> TABLES = new HashMap<>();
    private static int[] rigged; // the autotest's next dice (rigNext); null in play

    private DiceGame() {
    }

    private static final class Table {
        final UUID ryuk;
        int stake, d1, d2;
        long slamAt = -1, payAt = -1; // the cup comes down; the dice are shown and the stake changes hands
        int payout;
        boolean win;
        int rounds, won, lost; // for his word on the session when the table closes

        Table(UUID ryuk) {
            this.ryuk = ryuk;
        }
    }

    /** Opens the table: his own human, close by, with an apple to stake. */
    public static boolean open(ServerPlayer player, CompanionEntity ryuk) {
        if (!ryuk.ownedBy(player) || ryuk.level() != player.level() || ryuk.distanceTo(player) > 10) {
            return false;
        }
        if (apples(player) == 0) {
            player.displayClientMessage(Component.translatable("aicompanion.dice.no_apples").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        TABLES.putIfAbsent(player.getUUID(), new Table(ryuk.getUUID()));
        ryuk.seat(player);
        send(player, ryuk, OPEN, 0, 0, 0, 0, PLAIN);
        return true;
    }

    /** "Shake": the stake leaves the inventory, the dice are rolled (unseen), he rattles the cup. */
    public static void bet(ServerPlayer player, int stake) {
        Table t = TABLES.get(player.getUUID());
        CompanionEntity ryuk = t == null ? null : ryuk(player, t);
        if (t == null || ryuk == null || t.slamAt >= 0 || t.payAt >= 0) {
            return;
        }
        stake = Mth.clamp(stake, 1, MAX_STAKE);
        if (apples(player) < stake) {
            send(player, ryuk, OPEN, 0, 0, 0, 0, PLAIN);
            return;
        }
        take(player, stake);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        t.stake = stake;
        t.d1 = rigged != null ? rigged[0] : 1 + r.nextInt(6);
        t.d2 = rigged != null ? rigged[1] : 1 + r.nextInt(6);
        rigged = null;
        t.slamAt = player.server.getTickCount() + SHAKE_TICKS;
        ryuk.deal("dice_shake", SHAKE_TICKS + 4);
        send(player, ryuk, SHAKING, stake, 0, 0, 0, PLAIN);
    }

    /** "Even" or "odd", once the cup is down: the dice are shown at once, the apples change hands as they settle. */
    public static void call(ServerPlayer player, boolean even) {
        Table t = TABLES.get(player.getUUID());
        CompanionEntity ryuk = t == null ? null : ryuk(player, t);
        if (t == null || ryuk == null || t.slamAt < 0 || player.server.getTickCount() < t.slamAt) {
            return;
        }
        int special = t.d1 == 6 && t.d2 == 6 ? EYES : t.d1 == 1 && t.d2 == 1 ? SNAKE : PLAIN;
        t.win = special == EYES || special == PLAIN && ((t.d1 + t.d2) % 2 == 0) == even;
        t.payout = t.win ? t.stake * (special == EYES ? 3 : 2) : 0;
        t.slamAt = -1;
        t.payAt = player.server.getTickCount() + REVEAL_TICKS; // when the dice have stopped rolling on the screen
        t.rounds++;
        if (t.win) {
            t.won += t.payout - t.stake;
        } else {
            t.lost += t.stake;
        }
        send(player, ryuk, RESULT, t.stake, t.d1, t.d2, t.payout, special);
    }

    /** The table closed: a stake not yet called comes back; he says a word about the session. */
    public static void close(ServerPlayer player) {
        Table t = TABLES.remove(player.getUUID());
        if (t == null) {
            return;
        }
        if (t.payAt >= 0) {
            settle(player, t); // closed while the dice were still rolling: the result stands
        } else if (t.stake > 0) {
            give(player, t.stake); // the bet is off
        }
        CompanionEntity ryuk = ryuk(player, t);
        if (ryuk != null) {
            ryuk.deal("", 0);
            if (t.rounds > 0) {
                int net = t.won - t.lost;
                ryuk.remark(Config.tr("вы с твоим человеком сыграли в чёт-нечет на яблоки: " + t.rounds + " конов, "
                                + (net > 0 ? "он в плюсе на " + net + " ябл." : net < 0 ? "ты выиграл у него " + (-net) + " ябл." : "вышли по нулям")
                                + ". Скажи пару слов про игру",
                        "you and your human played chō-han for apples: " + t.rounds + " rounds, "
                                + (net > 0 ? "they're up " + net + " apples" : net < 0 ? "you won " + (-net) + " apples off them" : "dead even")
                                + ". Say a word about the game"), "dice", 2400);
            }
        }
    }

    /** Every tick: the cup comes down on time; the stake changes hands as the dice settle; a table left alone closes. */
    static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        for (Iterator<Map.Entry<UUID, Table>> it = TABLES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Table> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            Table t = e.getValue();
            CompanionEntity ryuk = player == null ? null : ryuk(player, t);
            if (player == null || ryuk == null || ryuk.distanceTo(player) > 16) {
                it.remove();
                if (player != null) {
                    if (t.stake > 0 && t.payAt < 0) {
                        give(player, t.stake);
                    } else if (t.payAt >= 0) {
                        settle(player, t);
                    }
                    send(player, null, CLOSED, 0, 0, 0, 0, PLAIN);
                }
                continue;
            }
            if (now % 20 == 0) {
                ryuk.seat(player); // across the table from his human
            }
            if (t.slamAt == now) {
                ryuk.deal("dice_slam", 20 * 60); // the cup stays down, his claw on it, till the call
                send(player, ryuk, CALL, t.stake, 0, 0, 0, PLAIN);
            }
            if (t.payAt == now) {
                int stake = t.stake; // settle() clears it
                settle(player, t);
                if (t.win) {
                    ryuk.deal(ThreadLocalRandom.current().nextInt(3) == 0 ? "bow" : "sulk", 60); // "your win, my lord"... or a pout
                } else if (stake >= 5 || t.d1 + t.d2 == 2) { // a big take, or snake eyes: he laughs
                    ryuk.deal("cackle", 50);
                } else {
                    ryuk.eat(new ItemStack(Items.APPLE), null); // one of his winnings, right there at the table
                }
                Pact.thrill(player, Pact.THRILL_APPLE); // a game of chance: his favourite cure for boredom
                send(player, ryuk, OPEN, 0, 0, 0, 0, PLAIN);
            }
        }
    }

    private static void settle(ServerPlayer player, Table t) {
        if (t.win) {
            give(player, t.payout);
        }
        t.stake = 0;
        t.payAt = -1;
    }

    /** Autotest only: the next roll comes out as given (each case of the table, deterministically). */
    public static void rigNext(int d1, int d2) {
        rigged = new int[]{d1, d2};
    }

    public static boolean playing(ServerPlayer player) {
        return TABLES.containsKey(player.getUUID());
    }

    @Nullable
    private static CompanionEntity ryuk(ServerPlayer player, Table t) {
        CompanionEntity ryuk = CompanionEntity.find(player.server, t.ryuk);
        return ryuk != null && ryuk.isAlive() && ryuk.level() == player.level() ? ryuk : null;
    }

    /** Plain apples only: golden ones are for something else (the dream). */
    public static int apples(ServerPlayer player) {
        Inventory inv = player.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Items.APPLE)) {
                n += inv.getItem(i).getCount();
            }
        }
        return n;
    }

    private static void take(ServerPlayer player, int count) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && count > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.APPLE)) {
                int k = Math.min(count, s.getCount());
                s.shrink(k);
                count -= k;
            }
        }
        inv.setChanged();
    }

    /** Into the inventory; what doesn't fit lands at the player's feet, marked so Ryuk doesn't eat the winnings. */
    private static void give(ServerPlayer player, int count) {
        while (count > 0) {
            ItemStack s = new ItemStack(Items.APPLE, Math.min(count, 64));
            count -= s.getCount();
            if (!player.getInventory().add(s) && !s.isEmpty()) {
                ItemEntity drop = player.drop(s, false);
                if (drop != null) {
                    drop.setNoPickUpDelay();
                    drop.addTag(WON);
                }
            }
        }
        player.getInventory().setChanged();
    }

    public static final String WON = "aicompanion_dice_won";

    private static void send(ServerPlayer player, @Nullable CompanionEntity ryuk, int phase, int stake, int d1, int d2, int payout, int special) {
        Network.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new Network.DiceTable(ryuk == null ? -1 : ryuk.getId(), phase, apples(player), stake, d1, d2, payout, special));
    }
}
