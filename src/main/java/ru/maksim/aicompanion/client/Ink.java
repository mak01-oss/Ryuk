package ru.maksim.aicompanion.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/** Text on the story's screens: wrapped paragraphs and big centred titles, no textures. */
final class Ink {
    private Ink() {
    }

    /** Wraps {@code text} into {@code width} and returns the y after it (plus a small gap). */
    static int paragraph(GuiGraphics g, Font font, Component text, int x, int y, int width, int color) {
        for (FormattedCharSequence line : font.split(text, width)) {
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y + 4;
    }

    /** How tall {@link #paragraph} will be, to lay buttons out under the text before drawing it. */
    static int height(Font font, Component text, int width) {
        return font.split(text, width).size() * 10 + 4;
    }

    /** Centred and scaled: the screen's headline. */
    static void title(GuiGraphics g, Font font, Component text, int centerX, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(centerX, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, -font.width(text) / 2, 0, color, false);
        g.pose().popPose();
    }
}
