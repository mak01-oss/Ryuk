package ru.maksim.aicompanion.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Right after the eye deal (and from the notebook later): where on this skin's face the red glows. The face, big, as
 * an 8 x 8 grid to paint on: a left click lights a pixel bright, a right click dim, the same click again (or with
 * Shift) puts it out; beside it the head itself in the light, turned by dragging, glowing as it will in the world.
 * The first look is found on the face by itself; "Find my eyes" finds it again. Kept per skin (EyeLook).
 */
public class EyeLookScreen extends Screen {
    private static final int CELL = 12, GRID = CELL * 8, BOX = 96, SCALE = 110, ERASE = 0, BRIGHT = 1, DIM = 2;
    private long bright, dim;
    private boolean deep; // the glow on the face itself, under a hat layer with relief
    private int gx, gy, bx, by, paint = -1;
    private float turnX = 0.5F, turnY = -0.2F; // a three-quarter view to start with
    private boolean turning;

    public EyeLookScreen() {
        super(Component.translatable("aicompanion.eyelook.title"));
        long[] look = EyeLook.of(Minecraft.getInstance().player);
        bright = look[0];
        dim = look[1];
        deep = look[2] != 0;
        EyeLook.editing = new long[]{bright, dim, deep ? 1 : 0};
    }

    @Override
    protected void init() {
        int w = GRID + 20 + BOX;
        gx = (width - w) / 2;
        gy = height / 2 - 58;
        bx = gx + GRID + 20;
        by = gy;
        int bw = 70, x = (width - (5 * bw + 12)) / 2, y = gy + GRID + 26;
        addRenderableWidget(Button.builder(Component.translatable("aicompanion.eyelook.auto"), b -> {
            long[] found = EyeLook.found(skin());
            if (found != null) {
                set(found[0], found[1]);
            }
        }).bounds(x, y, bw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("aicompanion.eyelook.mirror"), b -> mirror()).bounds(x + bw + 3, y, bw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("aicompanion.eyelook.clear"), b -> set(0, 0)).bounds(x + 2 * (bw + 3), y, bw, 18).build());
        addRenderableWidget(Button.builder(depth(), b -> {
            inset(!deep);
            b.setMessage(depth());
        }).bounds(x + 3 * (bw + 3), y, bw, 18).tooltip(Tooltip.create(Component.translatable("aicompanion.eyelook.depth_tip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("aicompanion.eyelook.done").withStyle(ChatFormatting.RED), b -> onClose())
                .bounds(x + 4 * (bw + 3), y, bw, 18).build());
    }

    /** The glow a pixel deeper, on the face itself (for the button and the autotest). */
    void inset(boolean on) {
        deep = on;
        set(bright, dim);
    }

    private Component depth() {
        return Component.translatable(deep ? "aicompanion.eyelook.deep" : "aicompanion.eyelook.outer");
    }

    private ResourceLocation skin() {
        return Minecraft.getInstance().player.getSkinTextureLocation();
    }

    private void set(long b, long d) {
        bright = b;
        dim = d & ~b;
        EyeLook.editing = new long[]{bright, dim, deep ? 1 : 0};
    }

    /** Each lit pixel lit on the other side of the face too (a pupil wins over a white). */
    private void mirror() {
        long b = bright, d = dim;
        for (int i = 0; i < 64; i++) {
            long other = EyeFinder.bit(7 - i % 8, i / 8);
            if ((bright >> i & 1) != 0) {
                b |= other;
            } else if ((dim >> i & 1) != 0 && (b & other) == 0) {
                d |= other;
            }
        }
        set(b, d);
    }

    /** Paints one pixel of the grid (for the mouse and the autotest). */
    void paint(int x, int y, int mode) {
        long bit = EyeFinder.bit(x, y);
        set(mode == BRIGHT ? bright | bit : bright & ~bit, mode == DIM ? dim | bit : dim & ~bit);
    }

    private int cell(double mx, double my) {
        int x = Mth.floor((mx - gx) / CELL), y = Mth.floor((my - gy) / CELL);
        return x >= 0 && x < 8 && y >= 0 && y < 8 ? y * 8 + x : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int c = cell(mx, my);
        if (c >= 0 && (button == 0 || button == 1)) {
            long bit = 1L << c;
            boolean already = button == 0 ? (bright & bit) != 0 : (dim & bit) != 0;
            paint = hasShiftDown() || already ? ERASE : button == 0 ? BRIGHT : DIM; // the whole stroke does what its first pixel did
            paint(c % 8, c / 8, paint);
            return true;
        }
        if (mx >= bx && mx < bx + BOX && my >= by && my < by + BOX) {
            turning = true;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (paint >= 0) {
            int c = cell(mx, my);
            if (c >= 0) {
                paint(c % 8, c / 8, paint);
            }
            return true;
        }
        if (turning) {
            turnX = Mth.clamp(turnX - (float) dx / 30.0F, -1.6F, 1.6F);
            turnY = Mth.clamp(turnY - (float) dy / 30.0F, -1.2F, 1.2F);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        paint = -1;
        turning = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0xFF060000, 0xFF280404); // opaque: no crosshair or chat showing through
        Ink.title(g, font, Component.translatable("aicompanion.eyelook.title").withStyle(ChatFormatting.BOLD), width / 2, gy - 44, 1.6F, 0xFFE02020);
        Ink.paragraph(g, font, Component.translatable("aicompanion.eyelook.text"), gx - 30, gy - 26, GRID + 20 + BOX + 60, 0xFFE8DDD5);
        // the face: the head, the hat layer over it, the lit pixels, the grid
        g.fill(gx - 2, gy - 2, gx + GRID + 2, gy + GRID + 2, 0xFF000000);
        g.blit(skin(), gx, gy, GRID, GRID, 8, 8, 8, 8, 64, 64);
        RenderSystem.enableBlend();
        g.blit(skin(), gx, gy, GRID, GRID, 40, 8, 8, 8, 64, 64);
        RenderSystem.disableBlend();
        for (int i = 0; i < 64; i++) {
            int x = gx + i % 8 * CELL, y = gy + i / 8 * CELL;
            if ((bright >> i & 1) != 0) {
                g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0xD0FF2818);
            } else if ((dim >> i & 1) != 0) {
                g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0xB0961010);
            }
        }
        for (int k = 1; k < 8; k++) {
            g.fill(gx + k * CELL, gy, gx + k * CELL + 1, gy + GRID, 0x30000000);
            g.fill(gx, gy + k * CELL, gx + GRID, gy + k * CELL + 1, 0x30000000);
        }
        int hover = cell(mouseX, mouseY);
        if (hover >= 0) {
            g.renderOutline(gx + hover % 8 * CELL, gy + hover / 8 * CELL, CELL, CELL, 0xFFFFFFFF);
        }
        // the head itself, as it glows in the world
        g.fill(bx - 2, by - 2, bx + BOX + 2, by + BOX + 2, 0xFF000000);
        g.fillGradient(bx, by, bx + BOX, by + BOX, 0xFF1C1C22, 0xFF08080A);
        g.enableScissor(bx, by, bx + BOX, by + BOX);
        InventoryScreen.renderEntityInInventoryFollowsAngle(g, bx + BOX / 2, by + BOX / 2 + (int) (1.75F * SCALE), SCALE, turnX, turnY,
                Minecraft.getInstance().player);
        g.disableScissor();
        Ink.paragraph(g, font, Component.translatable("aicompanion.eyelook.hint").withStyle(ChatFormatting.ITALIC), gx - 30, gy + GRID + 6,
                GRID + 20 + BOX + 60, 0xFF9C8C84);
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** However it closes (Done, Esc, another screen, a death): these eyes, for this skin. */
    @Override
    public void removed() {
        EyeLook.editing = null;
        if (Minecraft.getInstance().player != null) {
            EyeLook.save(Minecraft.getInstance().player, bright, dim, deep);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
