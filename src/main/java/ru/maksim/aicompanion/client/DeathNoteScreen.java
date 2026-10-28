package ru.maksim.aicompanion.client;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;
import ru.maksim.aicompanion.DeathNoteItem;
import ru.maksim.aicompanion.Network;
import ru.maksim.aicompanion.Sounds;

/**
 * The open notebook. Left page: the rules inside the cover (two pages of them; the last two lines in a different hand,
 * as in the manga), the "Cases" page (the news, the names waiting, where the story is now), then Ryuk's game. Right
 * page: the names and two lines to write on. Drawn, no textures. Opens where it was last left.
 */
public class DeathNoteScreen extends Screen {
    private static final int W = 320, H = 200;
    private static final int COVER = 0xFF0E0E12, PAPER = 0xFFEFEADF, RULE = 0x40705A44, INK = 0xFF1A1A1E, FADED = 0xFF6B6459,
            OTHER_HAND = 0xFF5A1010;
    static final int[][] RULE_PAGES = {{1, 2, 3, 4, 5}, {6, 7, 8, 9, 10, 11}}; // aicompanion.note.rule<n>; 10 and 11 are Ryuk's lies
    private static final int CASES = RULE_PAGES.length, PAGES = RULE_PAGES.length + 2; // + the cases, + Ryuk's game
    private static int lastPage;
    private final InteractionHand hand;
    private EditBox name, cause;
    private Button accept;
    private int left, top, page;

    public DeathNoteScreen(InteractionHand hand) {
        super(Component.literal("Death Note"));
        this.hand = hand;
        page = lastPage;
        Network.CHANNEL.sendToServer(new Network.AskCase());
    }

    public static void open(InteractionHand hand) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new DeathNoteScreen(hand));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(Sounds.NOTE_OPEN.get(), 1.0F));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int x = left + W / 2 + 14, w = W / 2 - 28;
        name = line(x + 30, top + H - 66, w - 30, 40, I18n.get("aicompanion.note.name_hint"));
        cause = line(x + 50, top + H - 48, w - 50, 60, I18n.get("aicompanion.note.cause_hint"));
        addRenderableWidget(Button.builder(Component.translatable("aicompanion.note.write"), b -> submit())
                .bounds(x + w - 56, top + H - 28, 56, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("aicompanion.note.tear"), b -> Network.CHANNEL.sendToServer(new Network.TearPage(hand)))
                .bounds(x - 4, top + H - 28, 76, 18).build());
        addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1)).bounds(left + 8, top + H - 22, 16, 14).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1)).bounds(left + W / 2 - 24, top + H - 22, 16, 14).build());
        accept = addRenderableWidget(Button.builder(Component.translatable("aicompanion.note.game_accept").withStyle(ChatFormatting.RED),
                b -> minecraft.setScreen(new PactScreen(hand))).bounds(left + 30, top + H - 42, W / 2 - 60, 16).build());
        accept.visible = false;
        addRenderableWidget(Button.builder(Component.translatable("aicompanion.note.eyes"), b -> minecraft.setScreen(new EyeLookScreen()))
                .bounds(left + W - 50, top + 3, 44, 13).build()).visible = ClientKira.eyes > 0;
        setInitialFocus(name);
    }

    void turn(int by) {
        page = lastPage = (page + by + PAGES) % PAGES;
        if (page == CASES) {
            Network.CHANNEL.sendToServer(new Network.AskCase()); // fresh: the seconds count down
        }
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(Sounds.NOTE_PAGE.get(), 1.0F));
    }

    private EditBox line(int x, int y, int w, int max, String hint) {
        EditBox box = new EditBox(font, x, y, w, 12, Component.literal(hint));
        box.setMaxLength(max);
        box.setBordered(false); // ink on paper, not a text field
        box.setTextColor(INK & 0xFFFFFF);
        box.setHint(Component.literal(hint).withStyle(ChatFormatting.GRAY));
        return addRenderableWidget(box);
    }

    private void submit() {
        String n = name.getValue().strip();
        if (!n.isEmpty()) {
            Network.CHANNEL.sendToServer(new Network.WriteName(hand, n, cause.getValue().strip()));
            onClose();
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int mid = left + W / 2;
        g.fill(left - 6, top - 6, left + W + 6, top + H + 6, COVER);
        g.fill(left, top, left + W, top + H, PAPER);
        g.fill(mid - 2, top, mid + 2, top + H, 0x30000000); // the fold
        g.fill(mid, top, mid + 1, top + H, 0x50000000);

        accept.visible = page == PAGES - 1 && ClientKira.pactOffered && ClientKira.pact != 1 && !ClientKira.sealed;
        if (page < RULE_PAGES.length) {
            rules(g, RULE_PAGES[page]);
        } else if (page == CASES) {
            cases(g);
        } else {
            game(g);
        }
        g.drawString(font, (page + 1) + "/" + PAGES, left + W / 4 - 6, top + H - 19, FADED, false);

        // right page: ruled lines, what has been written, then the pen
        int x = mid + 14, w = W / 2 - 28;
        for (int ry = top + 18; ry < top + H - 80; ry += 18) { // six lines of names above the pen
            g.fill(x, ry + 10, x + w, ry + 11, RULE);
        }
        List<String> written = DeathNoteItem.lines(minecraft.player.getItemInHand(hand));
        int shown = Math.min(written.size(), 6);
        for (int i = 0; i < shown; i++) {
            String line = written.get(written.size() - shown + i);
            g.drawString(font, font.plainSubstrByWidth(line, w), x, top + 18 + i * 18, INK, false);
        }
        if (written.isEmpty()) {
            g.drawString(font, Component.translatable("aicompanion.note.empty").withStyle(ChatFormatting.ITALIC), x, top + 18, FADED, false);
        }
        g.drawString(font, I18n.get("aicompanion.note.name"), x, top + H - 66, FADED, false);
        g.drawString(font, I18n.get("aicompanion.note.cause"), x, top + H - 48, FADED, false);
        g.fill(x + 28, top + H - 55, x + w, top + H - 54, 0x90705A44);
        g.fill(x + 48, top + H - 37, x + w, top + H - 36, 0x90705A44);
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** The rules, as if printed inside the cover; Ryuk's two lies are in his own clumsy hand. */
    private void rules(GuiGraphics g, int[] which) {
        Component title = Component.literal("DEATH NOTE").withStyle(ChatFormatting.BOLD);
        g.drawString(font, title, left + (W / 2 - font.width(title)) / 2, top + 10, INK, false);
        Component how = Component.translatable("aicompanion.note.how");
        g.drawString(font, how, left + (W / 2 - font.width(how)) / 2, top + 22, FADED, false);
        g.pose().pushPose();
        g.pose().translate(left + 10, top + 36, 0);
        g.pose().scale(0.7F, 0.7F, 1);
        int y = 0;
        for (int i : which) {
            boolean lie = i >= 10;
            Component rule = Component.literal(i + ". ").append(Component.translatable("aicompanion.note.rule" + i));
            if (lie && (ClientKira.story & 2) != 0) { // Sidoh let it slip: Ryuk made these two up
                rule = rule.copy().append(Component.translatable("aicompanion.note.lie").withStyle(ChatFormatting.BOLD));
            }
            for (FormattedCharSequence part : font.split(lie ? rule.copy().withStyle(ChatFormatting.ITALIC) : rule, (int) ((W / 2 - 20) / 0.7F))) {
                g.drawString(font, part, 0, y, lie ? OTHER_HAND : INK, false);
                y += 10;
            }
            y += 3;
        }
        g.pose().popPose();
    }

    /** What's going on, as the server tells it: the news, the names waiting, the places, the hunt. */
    private void cases(GuiGraphics g) {
        Component title = Component.translatable("aicompanion.case.title").withStyle(ChatFormatting.BOLD);
        g.drawString(font, title, left + (W / 2 - font.width(title)) / 2, top + 10, INK, false);
        g.pose().pushPose();
        g.pose().translate(left + 10, top + 24, 0);
        g.pose().scale(0.7F, 0.7F, 1);
        int y = 0, bottom = (int) ((H - 50) / 0.7F);
        for (Component line : ClientKira.caseFile) {
            for (FormattedCharSequence part : font.split(line, (int) ((W / 2 - 20) / 0.7F))) {
                if (y < bottom) {
                    g.drawString(font, part, 0, y, INK, false);
                }
                y += 10;
            }
            y += 2;
        }
        g.pose().popPose();
    }

    /** Ryuk's game: its four rules and where this human stands. */
    private void game(GuiGraphics g) {
        Component title = Component.translatable("aicompanion.pact.title").withStyle(ChatFormatting.BOLD);
        g.drawString(font, title, left + (W / 2 - font.width(title)) / 2, top + 10, OTHER_HAND, false);
        Component state = Component.translatable(ClientKira.sealed ? "aicompanion.note.game_over" : ClientKira.pact == 1
                ? "aicompanion.note.game_on" : ClientKira.pactOffered ? "aicompanion.note.game_off" : "aicompanion.note.game_disabled");
        g.drawString(font, state, left + (W / 2 - font.width(state)) / 2, top + 22, ClientKira.pact == 1 ? OTHER_HAND : FADED, false);
        g.pose().pushPose();
        g.pose().translate(left + 10, top + 36, 0);
        g.pose().scale(0.7F, 0.7F, 1);
        int y = 0;
        for (int i = 1; i <= 4; i++) {
            for (FormattedCharSequence part : font.split(Component.literal(i + ". ").append(Component.translatable("aicompanion.pact.rule" + i)),
                    (int) ((W / 2 - 20) / 0.7F))) {
                g.drawString(font, part, 0, y, INK, false);
                y += 10;
            }
            y += 3;
        }
        g.pose().popPose();
    }

    @Override
    public boolean isPauseScreen() {
        return false; // the 40 seconds keep running while you write
    }
}
