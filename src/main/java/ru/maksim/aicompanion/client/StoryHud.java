package ru.maksim.aicompanion.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.maksim.aicompanion.AiCompanion;
import ru.maksim.aicompanion.Network;
import ru.maksim.aicompanion.Realm;
import org.lwjgl.glfw.GLFW;

/**
 * The story on the screen, so a player sees it happen and knows what it means: a TV bulletin (the face, the name, a
 * ticker), a death on the news, a chapter card between cinema bars, and an arrow to the place the story moved to.
 * The chat keeps the same lines for the record. A card stays until the player has read it and pressed "next" (Enter).
 */
@Mod.EventBusSubscriber(modid = AiCompanion.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class StoryHud {
    static final KeyMapping NEXT = new KeyMapping("key.aicompanion.story_next", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_ENTER, "key.categories.aicompanion");
    private static final Deque<Network.Broadcast> QUEUE = new ArrayDeque<>();
    private static long leaving = -1; // when "next" was pressed: the card slides away
    /** Till when the screen has the floor (a card up, the show on, and a breath after): Ryuk's lines wait their turn. */
    static volatile long busyUntil;
    @Nullable
    private static Network.Broadcast shown;
    private static long elapsed, last; // ms the card has been up: it waits while the notebook or a menu is open
    @Nullable
    private static BlockPos goal;
    private static Component goalName = Component.empty();
    @Nullable
    private static ResourceKey<Level> goalLevel;
    @Nullable
    private static Object session; // the connection the cards came over: a new world starts clean
    private static final Deque<Component> ON_AIR = new ArrayDeque<>(); // the TV's chat lines while the player dreams: no TV in the realm

    private StoryHud() {
    }

    @SubscribeEvent
    public static void registerStoryHud(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("story", (gui, g, partialTick, width, height) -> render(g, width, height));
        MinecraftForge.EVENT_BUS.addListener(StoryHud::heldOffAir);
    }

    /** Asleep in the realm of the gods of death: the TV doesn't reach there; its bulletins wait for the waking. */
    private static boolean dreaming(Minecraft mc) {
        return mc.level != null && mc.level.dimension().equals(Realm.KEY);
    }

    private static void heldOffAir(ClientChatReceivedEvent.System event) {
        if (!event.isOverlay() && dreaming(Minecraft.getInstance()) && event.getMessage().getString().startsWith("[TV] ")) {
            ON_AIR.add(event.getMessage());
            event.setCanceled(true);
        }
    }

    /** The next card in line; in a dream only the story's own (the TV's keep their places for the morning). */
    @Nullable
    private static Network.Broadcast take(Minecraft mc) {
        if (!dreaming(mc)) {
            return QUEUE.poll();
        }
        for (java.util.Iterator<Network.Broadcast> it = QUEUE.iterator(); it.hasNext(); ) {
            Network.Broadcast b = it.next();
            if (b.kind() == Network.Broadcast.CHAPTER || b.kind() == Network.Broadcast.QUEST) {
                it.remove();
                return b;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void registerStoryKeys(RegisterKeyMappingsEvent event) {
        event.register(NEXT);
    }

    /** From the server: the card waits its turn (its place becomes the arrow's target once it's on screen); a way is only the arrow. */
    public static void show(Network.Broadcast b) {
        Minecraft mc = Minecraft.getInstance();
        fresh(mc);
        if (b.kind() == Network.Broadcast.WAY) {
            aim(mc, b);
        } else if (QUEUE.size() < 12) {
            QUEUE.add(b);
        }
    }

    /**
     * Nothing of the story on the screen now or about to come up (a card that can't show in a dream doesn't count):
     * Ryuk may say his line, an offer may open (RyukLines).
     */
    public static boolean floorFree(Minecraft mc) {
        if (shown != null || Util.getMillis() < busyUntil || mc.screen instanceof TvShowScreen || Cinema.playing()) {
            return false;
        }
        boolean dream = dreaming(mc);
        return QUEUE.stream().noneMatch(b -> !dream || b.kind() == Network.Broadcast.CHAPTER || b.kind() == Network.Broadcast.QUEST);
    }

    /** Filming (AutoTest): nothing of the story on the screen, nothing waiting; the hidden HUD would never turn the cards. */
    static void clear() {
        QUEUE.clear();
        shown = null;
        busyUntil = 0;
    }

    private static void aim(Minecraft mc, Network.Broadcast b) {
        if (b.at() != Network.Broadcast.NOWHERE && mc.level != null) {
            goal = BlockPos.of(b.at());
            goalName = b.title();
            goalLevel = mc.level.dimension();
        }
    }

    private static void fresh(Minecraft mc) {
        if (session != mc.getConnection()) {
            session = mc.getConnection();
            QUEUE.clear();
            ON_AIR.clear();
            shown = null;
            goal = null;
        }
    }


    private static void render(GuiGraphics g, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        fresh(mc);
        long now = Util.getMillis();
        if (mc.screen == null) {
            elapsed += Math.min(now - last, 100);
        }
        last = now;
        while (NEXT.consumeClick()) { // read: off it goes (a press in the first moment, or with no card up, doesn't count)
            if (shown != null && leaving < 0 && elapsed > 500) {
                leaving = elapsed;
            }
        }
        if (shown != null && leaving >= 0 && elapsed - leaving > 300) {
            shown = null;
        }
        if (!ON_AIR.isEmpty() && !dreaming(mc)) {
            ON_AIR.forEach(mc.gui.getChat()::addMessage);
            ON_AIR.clear();
        }
        if (shown == null && !QUEUE.isEmpty() && mc.screen == null && !Cinema.playing() && (shown = take(mc)) != null) { // a film has the screen: cards wait
            elapsed = 0;
            leaving = -1;
            aim(mc, shown);
            if (shown.kind() == Network.Broadcast.SHOW) { // L's special: the whole screen, next tick (not mid-frame)
                Network.Broadcast show = shown;
                shown = null;
                mc.tell(() -> mc.setScreen(new TvShowScreen(show)));
            } else {
                sound(shown);
            }
        }
        if (shown != null || mc.screen instanceof TvShowScreen) {
            busyUntil = now + 1200;
        }
        int top = 4;
        if (shown != null && !Cinema.playing()) { // a film over the card: the card steps aside till it's over
            float k = Mth.clamp(leaving >= 0 ? 1F - (elapsed - leaving) / 300F : elapsed / 350F, 0F, 1F); // slides in, and out
            top = shown.kind() == Network.Broadcast.CHAPTER || shown.kind() == Network.Broadcast.QUEST
                    ? chapter(g, mc.font, shown, width, height, k) : bulletin(g, mc.font, shown, width, k);
        }
        if (shown == null || shown.kind() != Network.Broadcast.CHAPTER && shown.kind() != Network.Broadcast.QUEST) {
            arrow(g, mc, width, top); // a chapter's or a quest's card has the screen to itself; the arrow comes back after
        }
    }

    /** A TV bulletin at the top of the screen: the channel's band, the face, the words, a ticker. Returns the y under it. */
    private static int bulletin(GuiGraphics g, Font font, Network.Broadcast b, int width, float k) {
        boolean death = b.kind() == Network.Broadcast.DEATH;
        int w = Math.min(320, width - 16), x = (width - w) / 2, size = 40, tx = x + size + 14, tw = w - size - 20;
        Component hint = b.kind() == Network.Broadcast.NEWS && b.face().contains(":") ? Component.translatable("aicompanion.card.hint_news").withStyle(ChatFormatting.ITALIC) : null;
        int words = Ink.height(font, b.text(), tw) + (hint == null ? 0 : Ink.height(font, hint, tw));
        int h = 14 + Math.max(size + 8, words + 2) + 22;
        int y = (int) (-h - 2 + (h + 8) * k);
        int band = death ? 0xFFB01E1E : b.kind() == Network.Broadcast.SPECIAL ? 0xFF303036 : 0xFF1E5AA8;
        g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF050506); // the set
        g.fill(x, y, x + w, y + h, 0xF0141418);
        g.fill(x, y, x + w, y + 12, band);
        g.drawString(font, b.title(), x + 4, y + 2, 0xFFFFFFFF, false);
        if (Util.getMillis() / 500 % 2 == 0) {
            Component live = Component.translatable("aicompanion.card.live");
            g.drawString(font, live, x + w - 4 - font.width(live), y + 2, death ? 0xFFFFFFFF : 0xFFFF4848, false);
        }
        face(g, font, b.face(), x + 7, y + 17, size, death);
        int ty = Ink.paragraph(g, font, b.text(), tx, y + 17, tw, 0xFFF2F2F2);
        if (hint != null) {
            Ink.paragraph(g, font, hint, tx, ty - 3, tw, 0xFF9CC0EA);
        }
        Component next = next();
        g.drawString(font, next, x + w - 6 - font.width(next), y + h - 22, 0xFFB8B8C0, false);
        int ky = y + h - 11; // the ticker along the bottom
        g.fill(x, ky - 1, x + w, y + h, band);
        String tick = b.title().getString() + "  •  " + b.text().getString() + "   ";
        int tickW = Math.max(1, font.width(tick)), off = (int) (Util.getMillis() / 22 % tickW);
        g.enableScissor(x, ky - 1, x + w, y + h);
        for (int sx = x - off; sx < x + w; sx += tickW) {
            g.drawString(font, tick, sx, ky + 1, 0xFFFFFFFF, false);
        }
        g.disableScissor();
        return Math.max(4, y + h + 6);
    }

    /** A face on the screen: a skin's face and hat, L's letter on white, or the channel's own card; static as it comes on. */
    private static void face(GuiGraphics g, Font font, String face, int x, int y, int size, boolean dead) {
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF000000);
        if (face.equals("L") || face.isEmpty()) {
            g.fill(x, y, x + size, y + size, face.equals("L") ? 0xFFF4F4F4 : 0xFF1E5AA8);
            Ink.title(g, font, Component.literal(face.equals("L") ? "L" : "TV"), x + size / 2, y + (face.equals("L") ? 6 : 13),
                    face.equals("L") ? 3.6F : 1.8F, face.equals("L") ? 0xFF000000 : 0xFFFFFFFF);
        } else {
            ResourceLocation skin = new ResourceLocation(face);
            RenderSystem.enableBlend();
            if (dead) {
                g.setColor(0.42F, 0.42F, 0.42F, 1.0F); // in mourning grey
            }
            g.blit(skin, x, y, size, size, 8, 8, 8, 8, 64, 64);
            g.blit(skin, x, y, size, size, 40, 8, 8, 8, 64, 64);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (elapsed < 350) { // the set warming up
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int i = 0; i < size; i += 4) {
                for (int j = 0; j < size; j += 4) {
                    int v = r.nextInt(40, 220);
                    g.fill(x + i, y + j, x + i + 4, y + j + 4, 0xFF000000 | v << 16 | v << 8 | v);
                }
            }
        }
    }

    /** A chapter of the story: cinema bars close in, a red title, a line under it, a few words. Returns the y under the top bar. */
    private static int chapter(GuiGraphics g, Font font, Network.Broadcast b, int width, int height, float k) {
        int bar = (int) (height * 0.11F * k);
        g.fill(0, 0, width, bar, 0xFF000000);
        g.fill(0, height - bar, width, height, 0xFF000000);
        int alpha = Math.max(8, (int) (255 * k)) << 24, cy = height / 5; // above the crosshair
        int lines = font.split(b.text(), Math.min(320, width - 40)).size();
        g.fill(0, cy - 8, width, cy + 56 + lines * 11, (int) (0x78 * k) << 24); // a shade: the words read on any sky
        boolean quest = b.kind() == Network.Broadcast.QUEST; // a side quest: gold, and labelled as one
        if (quest) {
            Component tag = Component.translatable("aicompanion.card.quest_tag");
            g.drawString(font, tag, (width - font.width(tag)) / 2, cy - 6, alpha | 0xC8A050, true);
        }
        float size = Math.min(quest ? 2.4F : 3.0F, (width - 24) / (float) Math.max(1, font.width(b.title()))); // a long one shrinks to fit
        Ink.title(g, font, b.title(), width / 2, cy + (quest ? 4 : 0), size, alpha | (quest ? 0xE8C25A : 0xE02020));
        int line = (int) (180 * k);
        g.fill(width / 2 - line / 2, cy + 31, width / 2 + line / 2, cy + 32, alpha | (quest ? 0xA88838 : 0xA01818));
        int ly = cy + 38;
        for (FormattedCharSequence s : font.split(b.text(), Math.min(320, width - 40))) {
            g.drawString(font, s, (width - font.width(s)) / 2, ly, alpha | 0xF0E6DC, true);
            ly += 11;
        }
        Component next = next();
        g.drawString(font, next, (width - font.width(next)) / 2, ly + 4, alpha | 0xB8B8C0, true);
        return bar + 4;
    }

    /** Where the story moved to: an arrow, the place, how far. Gone once you're there. */
    private static void arrow(GuiGraphics g, Minecraft mc, int width, int top) {
        LocalPlayer p = mc.player;
        if (goal == null || p == null || mc.level.dimension() != goalLevel) {
            return;
        }
        double dx = goal.getX() + 0.5 - p.getX(), dz = goal.getZ() + 0.5 - p.getZ(), dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 12) {
            goal = null; // arrived
            return;
        }
        float rel = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)) - p.getYRot()); // 0: straight ahead
        Component text = Component.literal("").append(goalName).append("  " + (int) dist + " ").append(Component.translatable("aicompanion.card.meters"));
        int w = mc.font.width(text) + 30, x = (width - w) / 2;
        g.fill(x, top, x + w, top + 16, 0xB0000000);
        g.pose().pushPose(); // a compass needle: turns smoothly as you turn
        g.pose().translate(x + 11, top + 8, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(rel));
        g.pose().scale(1.4F, 1.4F, 1.0F);
        g.drawString(mc.font, "▲", -mc.font.width("▲") / 2, -4, 0xFFFFE27A, false);
        g.pose().popPose();
        g.drawString(mc.font, text, x + 24, top + 4, 0xFFFFE27A, false);
    }

    /** "[Enter] next" (the key as bound), and how many more cards wait. */
    private static Component next() {
        Component more = QUEUE.isEmpty() ? Component.empty() : Component.translatable("aicompanion.card.more", QUEUE.size());
        return Component.translatable("aicompanion.card.next", NEXT.getTranslatedKeyMessage()).append(more);
    }

    /** The card's sting: its own cue, or its kind's ("none": the moment brings its own music). */
    private static void sound(Network.Broadcast card) {
        String cue = !card.cue().isEmpty() ? card.cue() : switch (card.kind()) {
            case Network.Broadcast.NEWS -> "sting.news";
            case Network.Broadcast.DEATH -> "sting.breaking";
            case Network.Broadcast.SPECIAL -> "sting.special";
            case Network.Broadcast.CHAPTER -> "sting.chapter";
            case Network.Broadcast.QUEST -> "sting.quest";
            default -> "none";
        };
        if (!cue.equals("none")) {
            Soundtrack.cue(cue);
        }
    }
}
