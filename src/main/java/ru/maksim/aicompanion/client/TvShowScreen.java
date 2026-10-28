package ru.maksim.aicompanion.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;
import ru.maksim.aicompanion.Network;

/**
 * L's special on every channel, as a small show one can't miss: the moment it airs the game stops (in singleplayer)
 * and the screen is a TV. The studio, the man at the desk who calls himself L, his name plate, his words one by one as
 * subtitles typing out; only Enter (or a click) moves on. The real L cutting in after Tailor's death is the same show
 * (face "L"): a big letter L on a white screen. When Kira has won, his own address (face "K"): a red K on black.
 * Closed, the show leaves all its words in the chat.
 */
public class TvShowScreen extends Screen {
    static int transcripts; // shows that left their words in the chat (the autotest's eyes)
    private final Network.Broadcast show;
    private final boolean l, kira; // the real L, not Tailor; Kira's address
    private final int lines; // aicompanion.tv.tailor1..8, aicompanion.tv.l1..5, aicompanion.tv.kira1..6
    private final long opened = Util.getMillis();
    private long lineStart = opened;
    private int line; // lines: the closing card

    public TvShowScreen(Network.Broadcast show) {
        super(Component.translatable("aicompanion.show.channel"));
        this.show = show;
        l = "L".equals(show.face());
        kira = "K".equals(show.face());
        lines = l ? 5 : kira ? 6 : 8;
    }

    @Override
    protected void init() {
        if (line == 0 && Util.getMillis() - opened < 100) { // the set comes on: the special's sting, its music under the words
            Soundtrack.sting(l ? "sting.l" : kira ? "sting.kira" : "sting.special");
            Soundtrack.scene(theme(), true);
        }
    }

    private String theme() {
        return l ? "music.l" : kira ? "music.kira" : "music.special";
    }

    private Component words(int i) {
        return Component.translatable((l ? "aicompanion.tv.l" : kira ? "aicompanion.tv.kira" : "aicompanion.tv.tailor") + (i + 1));
    }

    /** On the closing card (the autotest's eyes). */
    boolean ending() {
        return line >= lines;
    }

    int line() {
        return line;
    }

    /** Next line (a line still typing out shows whole first); past the last, the closing card; past that, closed. */
    void next() {
        if (line >= lines) {
            onClose();
            return;
        }
        int length = words(line).getString().length();
        if ((Util.getMillis() - lineStart) / 30 < length) {
            lineStart = Util.getMillis() - 30L * length;
            return;
        }
        line++;
        lineStart = Util.getMillis();
    }

    /** Whatever the way out (Enter on the closing card, Esc), the chat keeps what was said; the music fades. */
    @Override
    public void removed() {
        Soundtrack.endScene(theme());
        Component who = l ? Component.literal("L") : Component.translatable(kira ? "aicompanion.show.kira" : "aicompanion.show.speaker");
        for (int i = 0; i < lines; i++) {
            minecraft.gui.getChat().addMessage(Component.literal("[TV] ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal("<").append(who).append("> ").withStyle(ChatFormatting.GOLD))
                    .append(words(i).copy().withStyle(ChatFormatting.WHITE)));
        }
        transcripts++;
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_KP_ENTER || StoryHud.NEXT.matches(key, scan)) {
            next();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        next();
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return Util.getMillis() - opened > 2500; // not by a stray Esc the moment it airs
    }

    @Override
    public boolean isPauseScreen() {
        return true; // singleplayer: the world waits while you watch
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        g.fill(0, 0, width, height, 0xF0040406);
        int tw = Math.min(width - 40, 380), th = Math.min(tw * 9 / 16, height - 70), tx = (width - tw) / 2, ty = (height - th) / 2 - 10;
        g.fill(tx - 9, ty - 9, tx + tw + 9, ty + th + 9, 0xFF26262C); // the set
        g.fill(tx - 2, ty - 2, tx + tw + 2, ty + th + 2, 0xFF000000);
        g.fillGradient(tx, ty, tx + tw, ty + th, 0xFF1A2E4C, 0xFF070B16); // the studio
        g.enableScissor(tx, ty, tx + tw, ty + th);
        if (line >= lines) {
            closing(g, tx, ty, tw, th);
        } else if (l || kira) {
            letter(g, tx, ty, tw, th);
        } else {
            anchor(g, tx, ty, tw, th);
        }
        for (int y = ty; y < ty + th; y += 3) {
            g.fill(tx, y, tx + tw, y + 1, 0x16000000); // scan lines
        }
        if (now - opened < 500) { // the set warming up
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (int x = tx; x < tx + tw; x += 6) {
                for (int y = ty; y < ty + th; y += 6) {
                    int v = r.nextInt(20, 200);
                    g.fill(x, y, x + 6, y + 6, 0xFF000000 | v << 16 | v << 8 | v);
                }
            }
        }
        g.disableScissor();
        // the channel's band and the live sign
        g.fill(tx, ty, tx + tw, ty + 12, 0xE0B01E1E);
        g.drawString(font, Component.translatable("aicompanion.show.channel"), tx + 5, ty + 2, 0xFFFFFFFF, false);
        if (now / 500 % 2 == 0) {
            Component live = Component.translatable("aicompanion.card.live");
            g.drawString(font, live, tx + tw - 5 - font.width(live), ty + 2, 0xFFFFFFFF, false);
        }
        Component hint = line < lines ? Component.translatable("aicompanion.show.next", StoryHud.NEXT.getTranslatedKeyMessage(), line + 1, lines)
                : Component.translatable("aicompanion.show.close", StoryHud.NEXT.getTranslatedKeyMessage());
        g.drawString(font, hint, (width - font.width(hint)) / 2, ty + th + 16, 0xFFB8B8C0, false);
    }

    /** The man at the desk: face and suit, the name plate, the line typing out under him. */
    private void anchor(GuiGraphics g, int tx, int ty, int tw, int th) {
        Ink.title(g, font, Component.literal("L"), tx + tw - 40, ty + 22, 5.0F, 0x22FFFFFF); // the letter behind him, faint
        int fs = th * 2 / 5, fx = tx + (tw - fs) / 2, fy = ty + th / 6;
        g.fill(fx - fs / 2, fy + fs, fx + fs + fs / 2, ty + th, 0xFF15151B); // the suit
        g.fill(fx + fs / 2 - fs / 8, fy + fs, fx + fs / 2 + fs / 8, ty + th, 0xFFE8E8EC); // the shirt
        g.fill(fx + fs / 2 - fs / 20, fy + fs + 2, fx + fs / 2 + fs / 20, ty + th, 0xFFA01818); // the tie
        ResourceLocation skin = new ResourceLocation(show.face().contains(":") ? show.face() : "aicompanion:textures/entity/people/tailor_0.png");
        RenderSystem.enableBlend();
        g.blit(skin, fx, fy, fs, fs, 8, 8, 8, 8, 64, 64);
        g.blit(skin, fx, fy, fs, fs, 40, 8, 8, 8, 64, 64);
        g.fill(tx, ty + th - 26, tx + tw, ty + th, 0xFF3A2A1C); // the desk
        int px = tx + 10, py = ty + th - 64; // the name plate, lower third
        Component name = Component.translatable("aicompanion.show.name").withStyle(ChatFormatting.BOLD);
        Component role = Component.translatable("aicompanion.show.role");
        int pw = Math.max(font.width(name), font.width(role)) + 12;
        g.fill(px, py, px + pw, py + 12, 0xF0F2F2F2);
        g.drawString(font, name, px + 6, py + 2, 0xFF101010, false);
        g.fill(px, py + 12, px + pw, py + 22, 0xF0B01E1E);
        g.drawString(font, role, px + 6, py + 13, 0xFFFFFFFF, false);
        subtitles(g, tx, ty, tw, th);
    }

    /** The real L: a big letter on a white screen, his voice typing out under it. Kira: a red K on black. */
    private void letter(GuiGraphics g, int tx, int ty, int tw, int th) {
        g.fill(tx, ty, tx + tw, ty + th, kira ? 0xFF050505 : 0xFFF2F2F2);
        Ink.title(g, font, Component.literal(kira ? "K" : "L"), tx + tw / 2, ty + th / 2 - 52, 8.0F, kira ? 0xFFB51818 : 0xFF000000);
        subtitles(g, tx, ty, tw, th);
    }

    /** The current line typing out at the bottom of the screen, the last three rows of it. */
    private void subtitles(GuiGraphics g, int tx, int ty, int tw, int th) {
        String all = words(line).getString();
        String typed = all.substring(0, (int) Math.min(all.length(), (Util.getMillis() - lineStart) / 30));
        int sy = ty + th - 36;
        java.util.List<FormattedCharSequence> rows = font.split(Component.literal(typed), tw - 24);
        int sh = Math.max(1, Math.min(3, rows.size())) * 10 + 6;
        g.fill(tx + 6, sy - sh + 22, tx + tw - 6, sy + 24, 0xC8000000);
        int y = sy - sh + 26;
        for (FormattedCharSequence row : rows.subList(Math.max(0, rows.size() - 3), rows.size())) {
            g.drawString(font, row, tx + 12, y, 0xFFFFF4C8, false);
            y += 10;
        }
    }

    /** Off the screen: what it meant. */
    private void closing(GuiGraphics g, int tx, int ty, int tw, int th) {
        String end = l ? "aicompanion.show.l_end" : kira ? "aicompanion.show.k_end" : "aicompanion.show.end";
        Ink.title(g, font, Component.translatable(end + "_title").withStyle(ChatFormatting.BOLD),
                tx + tw / 2, ty + th / 4, 2.0F, 0xFFE02020);
        Ink.paragraph(g, font, Component.translatable(end), tx + 20, ty + th / 4 + 30, tw - 40, 0xFFE8DDD5);
    }
}
