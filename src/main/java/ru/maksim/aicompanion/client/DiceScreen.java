package ru.maksim.aicompanion.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import ru.maksim.aicompanion.CompanionEntity;
import ru.maksim.aicompanion.DiceGame;
import ru.maksim.aicompanion.Network;
import ru.maksim.aicompanion.Sounds;

/**
 * The dice table (DiceGame): Ryuk himself across it, the real model with his real animations (he shakes the skull cup,
 * slams it, laughs or sulks); a crimson felt between two candles; the cup, the dice and the apples on it. The server
 * deals; this screen only shows, times and asks. Stake with the wheel or arrows, Enter to have him roll, ← even, → odd.
 */
public class DiceScreen extends Screen {
    private static final int BONE = 0xFFE9E2CC, BONE_D = 0xFFB9AE92, INK = 0xFF15110F, CRIMSON = 0xFF6A1220, GOLD = 0xFFE8C25A;
    private static final long SLAM_DELAY = 360, LIFT = 420, TUMBLE_FROM = 150, SETTLE = 1150, FLY = 750;
    private final int ryukId;
    private int phase = DiceGame.OPEN, apples, stake = 1, betStake;
    private int d1 = 1, d2 = 1, payout, special;
    private boolean shown; // a result is on the felt (dice out, cup aside)
    private boolean answered; // the call was made this round
    private boolean verdict; // the dice have stopped and the result was announced
    private long phaseAt = Util.getMillis(), slamAt = -1, resultAt, bannerUntil, shookAt;
    private final long[] dieSeed = new long[2];
    private final List<int[]> history = new ArrayList<>(); // {d1, d2, won ? 1 : 0}
    private int net, rattle;
    private Component line = Component.empty();
    private long lineAt;
    private final List<float[]> dust = new ArrayList<>(); // x, y, vx, vy, born
    private boolean sentLeave;

    public DiceScreen(int ryukId) {
        super(Component.translatable("aicompanion.dice.title"));
        this.ryukId = ryukId;
    }

    /** From the server: open the table, or move it on. */
    public static void receive(Network.DiceTable t) {
        Minecraft mc = Minecraft.getInstance();
        if (t.phase() == DiceGame.CLOSED) {
            if (mc.screen instanceof DiceScreen s) {
                s.sentLeave = true;
                mc.setScreen(null);
            }
            return;
        }
        if (mc.screen instanceof DiceScreen s) {
            s.update(t);
        } else if (t.phase() == DiceGame.OPEN) {
            DiceScreen s = new DiceScreen(t.ryuk());
            mc.setScreen(s);
            s.update(t);
            s.say("hi", 4, "happy", true);
        }
    }

    private void update(Network.DiceTable t) {
        apples = t.apples();
        long now = Util.getMillis();
        switch (t.phase()) {
            case DiceGame.SHAKING -> {
                phase = DiceGame.SHAKING;
                phaseAt = now;
                betStake = t.stake();
                shown = false;
                answered = false;
                verdict = false;
                say("shake", 3, "happy", false);
                playOwn(Sounds.DICE_SHAKE.get());
                shookAt = now;
            }
            case DiceGame.CALL -> {
                phase = DiceGame.CALL;
                phaseAt = now;
                slamAt = now + SLAM_DELAY; // his claw reaches the felt
            }
            case DiceGame.RESULT -> {
                phase = DiceGame.RESULT;
                phaseAt = now;
                d1 = t.d1();
                d2 = t.d2();
                payout = t.payout();
                special = t.special();
                shown = true;
                verdict = false;
                resultAt = now;
                dieSeed[0] = ThreadLocalRandom.current().nextLong();
                dieSeed[1] = ThreadLocalRandom.current().nextLong();
                play(SoundEvents.ARMOR_EQUIP_GENERIC, 1.5F, 0.5F); // the cup comes up
                playOwn(Sounds.DICE_ROLL.get()); // and the dice tumble out
            }
            default -> {
                if (phase == DiceGame.RESULT) {
                    history.add(0, new int[]{d1, d2, payout > 0 ? 1 : 0});
                    if (history.size() > 9) {
                        history.remove(history.size() - 1);
                    }
                }
                phase = DiceGame.OPEN;
                stake = Mth.clamp(stake, 1, Math.max(1, Math.min(apples, DiceGame.MAX_STAKE)));
                if (apples == 0 && shown) {
                    say("broke", 1, "sad", true);
                }
            }
        }
    }

    // ------------------------------------------------------------------ moves

    private void roll() {
        if (phase != DiceGame.OPEN || apples < 1 || stake < 1) {
            return;
        }
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.6F);
        Network.CHANNEL.sendToServer(new Network.DiceMove(Network.DiceMove.BET, Math.min(stake, apples)));
        phase = -1; // waiting for the table to answer
    }

    private void call(boolean even) {
        if (phase != DiceGame.CALL || answered || Util.getMillis() < slamAt) {
            return;
        }
        answered = true;
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.6F);
        Network.CHANNEL.sendToServer(new Network.DiceMove(Network.DiceMove.CALL, even ? 1 : 0));
    }

    private void bump(int by) {
        if (phase == DiceGame.OPEN) {
            int max = Math.max(1, Math.min(apples, DiceGame.MAX_STAKE));
            int next = Mth.clamp(stake + by, 1, max);
            if (next != stake) {
                stake = next;
                play(SoundEvents.WOODEN_BUTTON_CLICK_ON, 1.4F, 0.4F);
            }
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_KP_ENTER -> roll();
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> call(true);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> call(false);
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> bump(1);
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> bump(-1);
            default -> {
                return super.keyPressed(key, scan, modifiers);
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        bump(delta > 0 ? 1 : -1);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Layout l = layout();
        if (phase == DiceGame.OPEN) {
            if (inside(mx, my, l.minus())) {
                bump(-1);
            } else if (inside(mx, my, l.plus())) {
                bump(1);
            } else if (inside(mx, my, l.main())) {
                roll();
            }
        } else if (phase == DiceGame.CALL) {
            if (inside(mx, my, l.even())) {
                call(true);
            } else if (inside(mx, my, l.odd())) {
                call(false);
            }
        }
        if (inside(mx, my, l.leave())) {
            onClose();
        }
        return true;
    }

    @Override
    public void onClose() {
        leave();
        super.onClose();
    }

    @Override
    public void removed() {
        leave();
    }

    private void leave() {
        if (!sentLeave) {
            sentLeave = true;
            Network.CHANNEL.sendToServer(new Network.DiceMove(Network.DiceMove.LEAVE, 0));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false; // the table is in the world: he moves, the server deals
    }

    @Override
    public void tick() {
        if (phase == DiceGame.SHAKING && Util.getMillis() - shookAt > 1150) { // still shaking: the dice rattle on
            shookAt = Util.getMillis();
            playOwn(Sounds.DICE_SHAKE.get());
        }
    }

    // ------------------------------------------------------------------ drawing

    private record Box(int x, int y, int w, int h) {
    }

    private record Layout(int tableY, int feltCx, int feltCy, int feltRx, int feltRy, int scale, Box minus, Box plus, Box main,
                          Box even, Box odd, Box leave) {
    }

    private Layout layout() {
        int tableY = (int) (height * 0.56F);
        int scale = (int) Math.min(110, Math.min((tableY - 8) / 1.78F, width / 4.6F)); // hair spikes and a raised claw stay in
        int feltCy = tableY + (height - tableY - 30) / 2 + 2, feltRy = Math.max(18, (height - tableY - 30) / 2 - 2);
        int feltRx = Math.min(width / 2 - 70, 150);
        int by = height - 25;
        int cx = width / 2;
        return new Layout(tableY, cx, feltCy, feltRx, feltRy, scale,
                new Box(14, by, 16, 18), new Box(68, by, 16, 18), new Box(cx - 62, by - 1, 124, 20),
                new Box(cx - 128, by - 6, 118, 26), new Box(cx + 10, by - 6, 118, 26), new Box(width - 58, by, 48, 18));
    }

    private static boolean inside(double x, double y, Box b) {
        return x >= b.x && x < b.x + b.w && y >= b.y && y < b.y + b.h;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        Layout l = layout();
        // the den: darkness, a red glow behind him, ash drifting up
        g.fillGradient(0, 0, width, height, 0xFF0A080D, 0xFF17101A);
        glow(g, width / 2, l.tableY - l.scale, l.scale * 2, l.scale * 3 / 2, 0x801020, 0x09);
        ash(g, now);
        // Ryuk, the real one, across the table
        Entity e = minecraft.level == null ? null : minecraft.level.getEntity(ryukId);
        if (e instanceof CompanionEntity ryuk) {
            Entity picked = minecraft.getEntityRenderDispatcher().crosshairPickEntity;
            minecraft.getEntityRenderDispatcher().crosshairPickEntity = null; // no name tag over him in here
            InventoryScreen.renderEntityInInventoryFollowsAngle(g, width / 2, l.tableY + (int) (l.scale * 0.95F), l.scale, -0.35F, 0.12F, ryuk);
            minecraft.getEntityRenderDispatcher().crosshairPickEntity = picked;
            g.flush();
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX); // the table goes over him
        }
        float shake = slamAt > 0 && now >= slamAt && now - slamAt < 260 ? (1 - (now - slamAt) / 260F) * 3 : 0;
        g.pose().pushPose();
        g.pose().translate((float) Math.sin(now * 0.09) * shake, (float) Math.cos(now * 0.11) * shake, 0);
        table(g, l, now);
        cupAndDice(g, l, now);
        flyingApples(g, l, now);
        g.pose().popPose();
        controls(g, l, mouseX, mouseY, now);
        bubble(g, l, now);
        history(g);
        banner(g, l, now);
    }

    /** A soft round light: nested translucent ellipses, each ring adding a little: bright in the middle, fading out. */
    private static void glow(GuiGraphics g, int cx, int cy, int rx, int ry, int rgb, int step) {
        for (int i = 8; i >= 1; i--) {
            ellipse(g, cx, cy, rx * i / 8, ry * i / 8, step << 24 | rgb);
        }
    }

    private void ash(GuiGraphics g, long now) {
        for (int i = 0; i < 26; i++) {
            long h = i * 2654435761L;
            float x = (h % 1000) / 1000F * width + (float) Math.sin(now / 1700.0 + i) * 6;
            float speed = 0.006F + (h >>> 8) % 100 / 25000F;
            float y = height - ((now * speed + (h >>> 16) % height) % (height + 20));
            int a = 40 + (int) ((h >>> 4) % 50);
            g.fill((int) x, (int) y, (int) x + 1, (int) y + 1, a << 24 | 0xC8C0C8);
        }
    }

    private void table(GuiGraphics g, Layout l, long now) {
        g.fillGradient(0, l.tableY, width, height, 0xFF2B1B13, 0xFF100906); // old dark wood
        g.fill(0, l.tableY, width, l.tableY + 1, 0xFF6B4A33);
        g.fill(0, l.tableY + 1, width, l.tableY + 3, 0xFF1A0F0A);
        for (int x = 0; x < width; x += 37) { // the grain
            g.fill(x, l.tableY + 6, x + 22, l.tableY + 7, 0x18FFFFFF);
        }
        ellipse(g, l.feltCx, l.feltCy, l.feltRx + 4, l.feltRy + 3, 0xFF9C8458); // a bone rim
        ellipse(g, l.feltCx, l.feltCy, l.feltRx + 2, l.feltRy + 1, 0xFF2A060C);
        ellipse(g, l.feltCx, l.feltCy, l.feltRx, l.feltRy, CRIMSON);
        ellipse(g, l.feltCx, l.feltCy - 2, l.feltRx - 10, l.feltRy - 6, 0xFF781828); // the lamp's sheen on the cloth
        Ink.title(g, font, Component.literal("丁"), l.feltCx - l.feltRx + 26, l.feltCy - 9, 1.6F, 0x40FFE7C0);
        Ink.title(g, font, Component.literal("半"), l.feltCx + l.feltRx - 26, l.feltCy - 9, 1.6F, 0x40FFE7C0);
        candle(g, 26, l.tableY - 2, now, 0);
        candle(g, width - 30, l.tableY - 2, now, 7);
    }

    private static void ellipse(GuiGraphics g, int cx, int cy, int rx, int ry, int color) {
        for (int dy = -ry; dy <= ry; dy++) {
            int hw = (int) Math.round(rx * Math.sqrt(1 - (double) dy * dy / (ry * ry)));
            g.fill(cx - hw, cy + dy, cx + hw, cy + dy + 1, color);
        }
    }

    private void candle(GuiGraphics g, int x, int base, long now, int seed) {
        glow(g, x + 3, base - 20, 22, 18, 0xFF9A30, 0x06);
        g.fill(x, base - 16, x + 7, base, 0xFFE4DCC6);
        g.fill(x + 5, base - 16, x + 7, base, 0xFFB8AE96);
        g.fill(x - 2, base - 1, x + 9, base + 1, 0xFF3A2A1E);
        int flick = (int) ((now / 90 + seed) % 3) - 1;
        g.fill(x + 3, base - 18, x + 4, base - 16, 0xFF201510); // the wick
        g.fill(x + 2 + flick, base - 23, x + 5 + flick, base - 18, 0xFFFFB13A);
        g.fill(x + 3 + flick, base - 25, x + 4 + flick, base - 20, 0xFFFFF0A0);
    }

    // ------------------------------------------------------------------ the cup and the dice

    private void cupAndDice(GuiGraphics g, Layout l, long now) {
        int cx = l.feltCx, cy = l.feltCy;
        if (phase == DiceGame.CALL || phase == DiceGame.RESULT || shown) {
            if (phase == DiceGame.CALL) {
                long t = now - slamAt;
                if (t < 0) {
                    return; // still coming down in his claw
                }
                if (t < 30 && dust.isEmpty()) { // the thud
                    playOwn(Sounds.DICE_SLAM.get());
                    for (int i = 0; i < 14; i++) {
                        float a = (float) (i / 14.0 * Math.PI * 2);
                        dust.add(new float[]{cx, cy + 10, Mth.cos(a) * (0.6F + i % 3 * 0.3F), Mth.sin(a) * 0.25F - 0.1F, now});
                    }
                }
                float drop = t < 120 ? 1 - (t / 120F) * (t / 120F) : 0;
                cup(g, cx, cy + 12 - (int) (drop * 40), 1.0F);
                dust(g, now);
                return;
            }
            long t = now - resultAt;
            for (int i = 0; i < 2; i++) {
                die(g, l, i, t);
            }
            if (t < LIFT) { // the cup going up and away
                float k = t / (float) LIFT;
                cup(g, cx + (int) (k * 34), cy + 12 - (int) (k * k * 64), k < 0.6F ? 1 : 1 - (k - 0.6F) / 0.4F);
            }
            if (t >= SETTLE) {
                int sum = d1 + d2;
                Component verdict = Component.literal(sum + " · " + (sum % 2 == 0 ? "丁 " : "半 "))
                        .append(Component.translatable(sum % 2 == 0 ? "aicompanion.dice.even" : "aicompanion.dice.odd"));
                Ink.title(g, font, verdict, cx, cy + 13, 1.2F, sum % 2 == 0 ? 0xFFFFE7C0 : 0xFFF0C8FF);
            }
        } else {
            dust.clear();
        }
    }

    /** A die rolls out from under the cup, tumbling, its face flicking, and settles beside the other one. */
    private void die(GuiGraphics g, Layout l, int i, long t) {
        int size = 18;
        int fx = l.feltCx + (i == 0 ? -20 : 20), fy = l.feltCy + (i == 0 ? 2 : -3);
        int value = i == 0 ? d1 : d2;
        float rot = 0, k = 1;
        int x = fx, y = fy;
        if (t < SETTLE) {
            k = Mth.clamp((t - TUMBLE_FROM) / (float) (SETTLE - TUMBLE_FROM - 150), 0, 1);
            float e = 1 - (1 - k) * (1 - k) * (1 - k); // out fast, then slower
            long seed = dieSeed[i];
            float spin = 260 + seed % 180;
            rot = (1 - e) * spin * (i == 0 ? 1 : -1);
            x = (int) Mth.lerp(e, l.feltCx, fx);
            float hop = (float) Math.abs(Math.sin(e * Math.PI * 2.5)) * (1 - e) * 12;
            y = (int) (Mth.lerp(e, l.feltCy + 4, fy) - hop);
            if (t < SETTLE - 200) {
                value = 1 + (int) Math.floorMod(seed + t / 70 * 7919L, 6L); // a blur of faces
            }
        }
        g.fill(x - size / 2 + 2, y + size / 2 - 1, x + size / 2 + 3, y + size / 2 + 2, 0x60000000); // shadow
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(rot));
        int h = size / 2;
        g.fill(-h + 1, -h, h - 1, h, 0xFFF6F1E4);
        g.fill(-h, -h + 1, h, h - 1, 0xFFF6F1E4);
        g.fill(-h + 1, h - 2, h - 1, h, 0xFFD2C9B4); // its lower edge in shade
        g.fill(h - 2, -h + 1, h - 1, h - 1, 0xFFD2C9B4);
        pips(g, value, size);
        g.pose().popPose();
    }

    private static void pips(GuiGraphics g, int value, int size) {
        int s = size / 4;
        int[][] at = switch (value) {
            case 1 -> new int[][]{{0, 0}};
            case 2 -> new int[][]{{-1, -1}, {1, 1}};
            case 3 -> new int[][]{{-1, -1}, {0, 0}, {1, 1}};
            case 4 -> new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
            case 5 -> new int[][]{{-1, -1}, {1, -1}, {0, 0}, {-1, 1}, {1, 1}};
            default -> new int[][]{{-1, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {1, 1}};
        };
        for (int[] p : at) {
            int px = p[0] * s, py = p[1] * s;
            if (value == 1) { // the one: big and red, as on Japanese dice
                g.fill(px - 3, py - 2, px + 3, py + 2, 0xFFC0141E);
                g.fill(px - 2, py - 3, px + 2, py + 3, 0xFFC0141E);
            } else {
                g.fill(px - 1, py - 1, px + 2, py + 2, INK);
            }
        }
    }

    /** The skull cup, mouth down: an old bleached dome with its eye holes to the player. */
    private void cup(GuiGraphics g, int cx, int bottom, float alpha) {
        if (alpha <= 0.02F) {
            return;
        }
        int a = (int) (alpha * 255) << 24;
        int w = 40, h = 34;
        g.fill(cx - w / 2 + 3, bottom - 1, cx + w / 2 + 5, bottom + 2, (int) (alpha * 0x70) << 24); // its shadow on the felt
        for (int y = 0; y < h; y++) {
            float k = y < 12 ? (float) Math.sqrt(1 - Math.pow((12 - y) / 12.0, 2)) : 1; // the round crown
            int hw = (int) (w / 2 * (0.82F + 0.18F * k) * (y < 12 ? k : 1));
            int row = bottom - h + y;
            g.fill(cx - hw, row, cx + hw, row + 1, a | BONE & 0xFFFFFF);
            g.fill(cx + hw - Math.max(2, hw / 4), row, cx + hw, row + 1, a | BONE_D & 0xFFFFFF); // the shaded side
        }
        g.fill(cx - w / 2, bottom - 4, cx + w / 2, bottom, a | 0x8E8268); // the cut rim
        g.fill(cx - 11, bottom - 22, cx - 3, bottom - 14, a | 0x1A1210); // eye holes
        g.fill(cx + 3, bottom - 22, cx + 11, bottom - 14, a | 0x1A1210);
        g.fill(cx - 10, bottom - 21, cx - 4, bottom - 15, a | 0x2C1A18);
        g.fill(cx + 4, bottom - 21, cx + 10, bottom - 15, a | 0x2C1A18);
        g.fill(cx - 1, bottom - 12, cx + 2, bottom - 8, a | 0x2A1E1A); // the nose
        g.fill(cx + 6, bottom - 31, cx + 7, bottom - 24, a | 0x6E6450); // a crack
        g.fill(cx + 7, bottom - 25, cx + 8, bottom - 20, a | 0x6E6450);
    }

    private void dust(GuiGraphics g, long now) {
        for (float[] d : dust) {
            float age = (now - d[4]) / 16F;
            if (age > 30) {
                continue;
            }
            int x = (int) (d[0] + d[2] * age * 1.4F), y = (int) (d[1] + d[3] * age * 1.4F);
            int a = (int) (160 * (1 - age / 30));
            g.fill(x, y, x + 2, y + 2, a << 24 | 0xBFB4A0);
        }
    }

    // ------------------------------------------------------------------ apples changing hands

    private void flyingApples(GuiGraphics g, Layout l, long now) {
        ItemStack apple = new ItemStack(Items.APPLE);
        if (phase == DiceGame.SHAKING || phase == DiceGame.CALL) { // the stake out on the felt
            long t = now - (phase == DiceGame.SHAKING ? phaseAt : phaseAt - 700);
            for (int i = 0; i < Math.min(betStake, 10); i++) {
                float k = Mth.clamp((t - i * 60) / 400F, 0, 1);
                float e = 1 - (1 - k) * (1 - k);
                int x = (int) Mth.lerp(e, 20, l.feltCx - l.feltRx + 30 + (i % 5) * 7);
                int y = (int) Mth.lerp(e, l.tableY + 14, l.feltCy - 4 + (i / 5) * 6) - (int) (Math.sin(e * Math.PI) * 18);
                g.renderItem(apple, x - 8, y - 8);
            }
            return;
        }
        if (!shown) {
            return;
        }
        long t = now - resultAt - SETTLE;
        if (t < 0) {
            for (int i = 0; i < Math.min(betStake, 10); i++) { // still on the felt while the dice roll
                g.renderItem(apple, l.feltCx - l.feltRx + 30 + (i % 5) * 7 - 8, l.feltCy - 4 + (i / 5) * 6 - 8);
            }
            return;
        }
        boolean won = payout > 0;
        int count = Math.min(won ? payout : betStake, 12);
        for (int i = 0; i < count; i++) {
            float k = Mth.clamp((t - i * 45) / (float) FLY, 0, 1);
            if (k >= 1) {
                continue;
            }
            float e = k * k * (3 - 2 * k);
            int sx = won ? width / 2 : l.feltCx - l.feltRx + 30 + (i % 5) * 7, sy = won ? l.tableY - 10 : l.feltCy - 4;
            int tx = won ? 20 : width / 2, ty = won ? l.tableY + 14 : l.tableY - l.scale;
            int x = (int) Mth.lerp(e, sx, tx), y = (int) (Mth.lerp(e, sy, ty) - Math.sin(e * Math.PI) * 26);
            g.pose().pushPose();
            g.pose().translate(0, 0, 50);
            g.renderItem(apple, x - 8, y - 8);
            g.pose().popPose();
        }
    }

    // ------------------------------------------------------------------ the words, the buttons, the record

    private void controls(GuiGraphics g, Layout l, int mx, int my, long now) {
        // apples left, and the tally of this sitting
        g.renderItem(new ItemStack(Items.APPLE), 12, l.tableY + 6); // his pile, in the table's near corner
        g.drawString(font, Component.literal("× " + apples), 30, l.tableY + 11, 0xFFF2E8D8, true);
        if (!history.isEmpty() || net != 0) {
            Component tally = Component.translatable("aicompanion.dice.net", (net > 0 ? "+" : "") + net);
            g.drawString(font, tally, width - 12 - font.width(tally), l.tableY + 11, net > 0 ? 0xFF9BE07A : net < 0 ? 0xFFE07A7A : 0xFFD8D0C0, true);
        }
        if (phase == DiceGame.CALL) {
            boolean ready = now >= slamAt && !answered;
            plaque(g, l.even(), "丁", Component.translatable("aicompanion.dice.even"), ready, inside(mx, my, l.even()), now);
            plaque(g, l.odd(), "半", Component.translatable("aicompanion.dice.odd"), ready, inside(mx, my, l.odd()), now);
        } else {
            boolean open = phase == DiceGame.OPEN && apples > 0;
            g.drawString(font, Component.translatable("aicompanion.dice.stake"), 14, l.minus().y() - 11, 0xFFB8A890, true);
            button(g, l.minus(), Component.literal("−"), open && stake > 1, inside(mx, my, l.minus()));
            g.renderItem(new ItemStack(Items.APPLE), 34, l.minus().y() + 1);
            g.drawString(font, Component.literal(String.valueOf(open ? Math.min(stake, apples) : betStake)), 52, l.minus().y() + 5, 0xFFFFF4D8, true);
            button(g, l.plus(), Component.literal("+"), open && stake < Math.min(apples, DiceGame.MAX_STAKE), inside(mx, my, l.plus()));
            Component label = phase == DiceGame.OPEN ? Component.translatable("aicompanion.dice.roll")
                    : Component.literal(phase == DiceGame.SHAKING ? "· · ·" : "");
            button(g, l.main(), label, open, inside(mx, my, l.main()));
        }
        button(g, l.leave(), Component.translatable("aicompanion.dice.leave"), true, inside(mx, my, l.leave()));
        Component hint = Component.translatable(phase == DiceGame.CALL ? "aicompanion.dice.hint_call" : "aicompanion.dice.hint_open");
        g.drawString(font, hint, width - font.width(hint) - 8, 7, 0xA0B8B0A0, false);
    }

    private void button(GuiGraphics g, Box b, Component text, boolean active, boolean hover) {
        int top = !active ? 0xFF2A2420 : hover ? 0xFF7A5A3A : 0xFF5A4030, bottom = !active ? 0xFF1C1814 : hover ? 0xFF4A3220 : 0xFF33231A;
        g.fill(b.x() - 1, b.y() - 1, b.x() + b.w() + 1, b.y() + b.h() + 1, active ? 0xFFB0946A : 0xFF4A4038);
        g.fillGradient(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), top, bottom);
        g.drawString(font, text, b.x() + (b.w() - font.width(text)) / 2, b.y() + (b.h() - 8) / 2, active ? 0xFFFFF0D0 : 0xFF7A7068, true);
    }

    /** A wooden plaque with the kanji burnt in: 丁 even, 半 odd. */
    private void plaque(GuiGraphics g, Box b, String kanji, Component word, boolean ready, boolean hover, long now) {
        int glowA = ready ? (int) (40 + 30 * Math.sin(now / 180.0)) : 0;
        if (ready) {
            g.fill(b.x() - 4, b.y() - 4, b.x() + b.w() + 4, b.y() + b.h() + 4, glowA << 24 | 0xE8C25A);
        }
        g.fill(b.x() - 1, b.y() - 1, b.x() + b.w() + 1, b.y() + b.h() + 1, ready ? GOLD : 0xFF4A4038);
        g.fillGradient(b.x(), b.y(), b.x() + b.w(), b.y() + b.h(), hover && ready ? 0xFF8A6238 : 0xFF6A4A2C, 0xFF3A2616);
        Ink.title(g, font, Component.literal(kanji), b.x() + 18, b.y() + 3, 2.0F, ready ? 0xFF1A0E08 : 0xFF2A1E16);
        g.drawString(font, word.copy().withStyle(ChatFormatting.BOLD), b.x() + 38, b.y() + 9, ready ? 0xFFFFF0D0 : 0xFF8A7A68, true);
    }

    private void history(GuiGraphics g) {
        int x = 8;
        for (int[] h : history) {
            int sum = h[0] + h[1];
            String s = (sum % 2 == 0 ? "丁" : "半") + sum;
            int w = font.width(s) + 6;
            g.fill(x - 1, 5, x + w + 1, 19, h[2] == 1 ? 0xFF4E9A3A : 0xFF9A3A3A);
            g.fill(x, 6, x + w, 18, 0xFF15110F);
            g.drawString(font, s, x + 3, 8, 0xFFE8DCC8, false);
            x += w + 4;
        }
    }

    /** His words over the table, typing out; also spoken in his voice. */
    private void say(String kind, int variants, String emotion, boolean voice) {
        String key = "aicompanion.dice.say." + kind + (1 + ThreadLocalRandom.current().nextInt(variants));
        line = Component.translatable(key);
        lineAt = Util.getMillis();
        if (voice) {
            RyukVoice.speak(ryukId, line.getString(), emotion);
        }
    }

    private void bubble(GuiGraphics g, Layout l, long now) {
        String all = line.getString();
        if (all.isEmpty() || now - lineAt > 6500) {
            return;
        }
        String typed = all.substring(0, (int) Math.min(all.length(), (now - lineAt) / 28));
        int w = Math.min(150, width - (width / 2 + l.scale / 2 + 18) - 8);
        List<net.minecraft.util.FormattedCharSequence> rows = font.split(Component.literal(typed), w - 10);
        int x = width / 2 + l.scale / 2 + 14, y = 26, h = Math.max(1, rows.size()) * 10 + 8;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF8C1A22);
        g.fill(x, y, x + w, y + h, 0xF0120C0E);
        g.fill(x - 5, y + 6, x, y + 9, 0xFF8C1A22); // the tail, toward his face
        int ry = y + 5;
        for (net.minecraft.util.FormattedCharSequence row : rows) {
            g.drawString(font, row, x + 5, ry, 0xFFF4E8DC, false);
            ry += 10;
        }
    }

    /** The verdict, when the dice have stopped: win, loss, or one of the two specials. */
    private void banner(GuiGraphics g, Layout l, long now) {
        long t = now - resultAt - SETTLE;
        if (!shown || t < 0 || verdict && now >= bannerUntil) {
            return;
        }
        if (!verdict) { // the moment they stop: the verdict lands
            verdict = true;
            bannerUntil = now + 4200;
            boolean won = payout > 0;
            net += won ? payout - betStake : -betStake;
            Soundtrack.sting(won ? "sting.dice_win" : "sting.dice_lose");
            if (special == DiceGame.EYES) {
                Soundtrack.sting("sting.sight"); // double six: the gods' own throw
            }
            say(special == DiceGame.EYES ? "eyes" : special == DiceGame.SNAKE ? "snake" : won ? "win" : "lose",
                    special == DiceGame.PLAIN ? 4 : 1, won ? "sad" : "happy", true);
        }
        boolean won = payout > 0;
        String key = special == DiceGame.EYES ? "aicompanion.dice.eyes" : special == DiceGame.SNAKE ? "aicompanion.dice.snake"
                : won ? "aicompanion.dice.win" : "aicompanion.dice.lose";
        Component head = Component.translatable(key).withStyle(ChatFormatting.BOLD);
        Component amount = Component.literal((won ? "+" : "−") + (won ? payout - betStake : betStake));
        float pop = Mth.clamp(t / 180F, 0, 1);
        float scale = 1.4F + (1 - pop) * 0.6F;
        int x = 10, y = 32, w = Math.max((int) (font.width(head) * scale), 60) + 14, h = (int) (9 * scale) + 30;
        int color = won ? GOLD : 0xFFFF5A5A;
        g.pose().pushPose();
        g.pose().translate(0, 0, 100);
        g.fill(x, y, x + w, y + h, won ? 0xD8142410 : 0xD828080C);
        g.fill(x, y, x + 2, y + h, won ? GOLD : 0xFFC0141E);
        g.pose().pushPose();
        g.pose().translate(x + 8, y + 5, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, head, 0, 0, color, true);
        g.pose().popPose();
        g.renderItem(new ItemStack(Items.APPLE), x + 8, y + h - 20);
        g.pose().pushPose();
        g.pose().translate(x + 28, y + h - 18, 0);
        g.pose().scale(1.6F, 1.6F, 1);
        g.drawString(font, amount, 0, 0, color, true);
        g.pose().popPose();
        g.pose().popPose();
    }

    private void play(SoundEvent sound, float pitch, float volume) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private void playOwn(SoundEvent sound) {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F, 1.0F));
    }

    /** For the autotest's eyes: where the table is. */
    @Nullable
    static DiceScreen current() {
        return Minecraft.getInstance().screen instanceof DiceScreen s ? s : null;
    }

    int phase() {
        return phase;
    }
}
