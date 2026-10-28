package ru.maksim.aicompanion.client;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.maksim.aicompanion.AiCompanion;
import ru.maksim.aicompanion.CompanionEntity;
import ru.maksim.aicompanion.Network;

/**
 * Ryuk's lines wait for the floor: nothing being read (a story card on the screen or coming up, a film, the TV show, the
 * notebook, an offer: any screen but a chest, the inventory or the chat) and a breath after, the line he is still saying.
 * Then the line is said all at once: in the chat, out loud, and the server is told (Network.Spoken), so only now do
 * his mouth, his laugh and what he does with it start, never over a card the player hasn't read yet. Offers (his
 * game, the eyes) wait for the same floor, and for the line that explains them ({@link #floor}).
 */
@Mod.EventBusSubscriber(modid = AiCompanion.MODID, value = Dist.CLIENT)
public final class RyukLines {
    private static final Deque<Network.Speak> WAITING = new ArrayDeque<>();
    private static long heldSince, nextAt, quietUntil;
    private static int said; // lines said so far (the autotest's ears)

    private RyukLines() {
    }

    public static void add(Network.Speak line) {
        if (WAITING.isEmpty()) {
            heldSince = Util.getMillis();
        }
        WAITING.add(line);
    }

    /**
     * Is the floor his? Nothing on the screen to read, for a breath now: the server waits for it too (Network.Floor) and
     * keeps what happened meanwhile for one reply after.
     */
    static boolean free(Minecraft mc) {
        long now = Util.getMillis();
        if (!StoryHud.floorFree(mc) || mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)
                && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
            quietUntil = now + 1500;
        }
        return now >= quietUntil;
    }

    /** A line of his waiting, or being said right now. */
    public static boolean busy() {
        return !WAITING.isEmpty() || RyukVoice.busy() || Util.getMillis() < nextAt;
    }

    /**
     * May something of the story open now (an offer's screen)? No card, no show, no line of his waiting or being said,
     * and he isn't in the middle of thinking up the line that comes with it (that is waited for, but not forever).
     */
    public static boolean floor(Minecraft mc, long waitingSince) {
        if (!StoryHud.floorFree(mc) || busy()) {
            return false;
        }
        boolean thinking = mc.level != null && mc.player != null && !mc.level.getEntitiesOfClass(CompanionEntity.class,
                mc.player.getBoundingBox().inflate(48), e -> e.getEmotion().equals("thinking")).isEmpty();
        return !thinking || Util.getMillis() - waitingSince > 8000;
    }

    private static boolean toldFree = true; // what the server last heard about this screen's floor

    /** Tells the server when the floor is taken or free again (Ryuk's story gestures wait for it; StoryHud is on the mod bus). */
    @SubscribeEvent
    public static void tellFloor(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (mc.getConnection() == null || mc.player == null) {
            toldFree = true; // a new world starts with the floor free, as the server thinks too
            return;
        }
        boolean free = free(mc);
        if (free != toldFree) {
            toldFree = free;
            Network.CHANNEL.sendToServer(new Network.Floor(free));
        }
    }

    static int said() {
        return said;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            WAITING.clear();
            return;
        }
        long now = Util.getMillis();
        if (WAITING.isEmpty() || RyukVoice.busy() || now < nextAt || !free(mc) && now - heldSince < 180_000) {
            return;
        }
        Network.Speak line = WAITING.poll();
        heldSince = now;
        nextAt = now + Math.max(1200, line.text().length() * 35L); // a line without a voice still takes a moment to read
        said++;
        mc.gui.getChat().addMessage(line.line());
        RyukVoice.speak(line.entity(), line.text(), line.emotion());
        Network.CHANNEL.sendToServer(new Network.Spoken(line.entity(), line.id()));
    }
}
