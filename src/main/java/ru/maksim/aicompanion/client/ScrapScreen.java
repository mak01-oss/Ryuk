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
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;
import ru.maksim.aicompanion.DeathNoteItem;
import ru.maksim.aicompanion.Network;
import ru.maksim.aicompanion.Sounds;
import ru.maksim.aicompanion.ScrapItem;

/** A scrap of the notebook to write on: a torn page, the watch's hidden piece, the page in the chips bag. */
public class ScrapScreen extends Screen {
    private static final int W = 170, H = 120, PAPER = 0xFFEDE6D6, INK = 0xFF1A1A1E, FADED = 0xFF6B6459;
    private static long lastPull;
    private static int pulls;
    private final InteractionHand hand;
    private final ScrapItem.Kind kind;
    private EditBox name, cause;

    private ScrapScreen(InteractionHand hand, ScrapItem.Kind kind) {
        super(Component.translatable("item.aicompanion.scrap." + kind.name().toLowerCase(java.util.Locale.ROOT)));
        this.hand = hand;
        this.kind = kind;
    }

    /** The watch opens on the fourth quick pull of its crown; the others open at once. */
    public static void use(InteractionHand hand, ScrapItem.Kind kind) {
        Minecraft mc = Minecraft.getInstance();
        if (kind == ScrapItem.Kind.WATCH) {
            long now = System.currentTimeMillis();
            pulls = now - lastPull < 700 ? pulls + 1 : 1;
            lastPull = now;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 2.0F, 0.15F)); // the crown clicks
            if (pulls < 4) {
                return;
            }
            pulls = 0;
        }
        mc.setScreen(new ScrapScreen(hand, kind));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(Sounds.NOTE_PAGE.get(), 1.1F));
    }

    @Override
    protected void init() {
        int left = (width - W) / 2, top = (height - H) / 2;
        name = field(left + 40, top + H - 52, W - 50, 40, I18n.get("aicompanion.note.name_hint"));
        cause = field(left + 52, top + H - 36, W - 62, 60, I18n.get("aicompanion.note.cause_hint"));
        if (kind == ScrapItem.Kind.WATCH) {
            cause.visible = false; // a needle and a drop of blood: a name is all there's room for
        }
        addRenderableWidget(Button.builder(Component.translatable("aicompanion.note.write"), b -> submit())
                .bounds(left + W - 62, top + H - 20, 56, 16).build());
        setInitialFocus(name);
    }

    private EditBox field(int x, int y, int w, int max, String hint) {
        EditBox box = new EditBox(font, x, y, w, 12, Component.literal(hint));
        box.setMaxLength(max);
        box.setBordered(false);
        box.setTextColor(INK & 0xFFFFFF);
        box.setHint(Component.literal(hint).withStyle(ChatFormatting.GRAY));
        return addRenderableWidget(box);
    }

    private void submit() {
        String n = name.getValue().strip();
        if (!n.isEmpty()) {
            Network.CHANNEL.sendToServer(new Network.WriteName(hand, n, kind == ScrapItem.Kind.WATCH ? "" : cause.getValue().strip()));
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
        int left = (width - W) / 2, top = (height - H) / 2;
        g.fill(left, top, left + W, top + H, PAPER);
        for (int x = left; x < left + W; x += 6) { // the torn edge
            g.fill(x, top - 2 + (x / 6 % 2), x + 3, top, PAPER);
        }
        g.drawString(font, title, left + 8, top + 6, FADED, false);
        List<String> written = DeathNoteItem.lines(minecraft.player.getItemInHand(hand));
        for (int i = 0; i < Math.min(3, written.size()); i++) {
            g.drawString(font, font.plainSubstrByWidth(written.get(written.size() - Math.min(3, written.size()) + i), W - 16),
                    left + 8, top + 20 + i * 11, INK, false);
        }
        g.drawString(font, I18n.get("aicompanion.note.name"), left + 8, top + H - 52, FADED, false);
        g.fill(left + 38, top + H - 41, left + W - 8, top + H - 40, 0x90705A44);
        if (kind != ScrapItem.Kind.WATCH) {
            g.drawString(font, I18n.get("aicompanion.note.cause"), left + 8, top + H - 36, FADED, false);
            g.fill(left + 50, top + H - 25, left + W - 8, top + H - 24, 0x90705A44);
        } else {
            g.drawString(font, Component.translatable("aicompanion.scrap.blood").withStyle(ChatFormatting.ITALIC), left + 8, top + H - 34, 0xFF8A1010, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
