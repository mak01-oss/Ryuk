package ru.maksim.aicompanion.client;

import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.maksim.aicompanion.AiCompanion;

/**
 * The story's great moments as little films: Ryuk's arrival, the first dream in the realm of the gods of death,
 * meeting a god of death or one of the story's people for the first time. Black bars close in, the edges darken, the
 * view narrows then punches back with a shake at the moment of impact, the camera turns to whoever it's about, a great
 * title. While a film runs, the story's floor is its own (StoryHud.floorFree): cards and Ryuk's lines wait.
 * Started by a cue from the server: "cine.ryuk", "cine.realm", "cine.god.armonia@id", "cine.person.l@id", "cine.look@id";
 * the epilogue's "cine.epilogue", "cine.village.1@x,y,z", "cine.end".
 */
@Mod.EventBusSubscriber(modid = AiCompanion.MODID, value = Dist.CLIENT)
public final class Cinema {
    /**
     * A film's script (times in ms from its start): its length, title, colour; the darkened edges and the narrowed
     * view at their strongest, the impact (a flash and a shake), the window the camera turns to its subject, and the
     * moment the title comes up.
     */
    private record Film(String key, int length, int color, float vignette, float zoom, int impact, int lookFrom, int lookTo,
                        int titleAt, boolean fromBlack, float titleScale) {
    }

    @Nullable
    private static Film film;
    private static long start;
    private static int lookId = -1;
    @Nullable
    private static Vec3 lookAt;
    private static int seen; // films shown (the autotest's eyes)

    private Cinema() {
    }

    /** A cue from the server (Soundtrack.cue passes "cine.*" here). */
    public static void cue(String cue) {
        String id = cue.substring("cine.".length());
        String target = null;
        int at = id.indexOf('@');
        if (at >= 0) {
            target = id.substring(at + 1);
            id = id.substring(0, at);
        }
        if (id.equals("look")) { // the subject has come into the world now: the camera turns to it
            aim(target);
            return;
        }
        Film f = switch (id.contains(".") ? id.substring(0, id.indexOf('.')) : id) {
            case "ryuk" -> new Film("ryuk", 11000, 0xFFB51818, 0.92F, 0.76F, 3800, 3500, 5600, 4300, false, 6.0F);
            case "realm" -> new Film("realm", 9500, 0xFFE9DECB, 0.55F, 0.72F, -1, -1, -1, 2600, true, 3.4F);
            case "god" -> new Film(id, 6200, 0xFFD9A93E, 0.5F, 0.88F, 250, 0, 1500, 600, false, 3.4F);
            case "epilogue" -> new Film(id, 9000, 0xFFB51818, 0.8F, 0.8F, -1, -1, -1, 1800, true, 6.0F); // Kira has won (Epilogue)
            case "village" -> new Film(id, 8000, 0xFFE9DECB, 0.55F, 0.9F, -1, 0, 2500, 1600, true, 3.2F); // the world changing
            case "end" -> new Film(id, 10000, 0xFFB51818, 0.9F, 0.85F, -1, -1, -1, 1200, false, 6.0F);
            default -> new Film(id, 5800, 0xFFDDE6EE, 0.45F, 0.9F, 250, 0, 1400, 600, false, 3.4F);
        };
        film = f;
        start = Util.getMillis();
        seen++;
        lookId = -1;
        lookAt = null;
        aim(target);
        if (f.key.equals("ryuk")) {
            Soundtrack.scene("music.ryuk", false);
        } else if (f.key.startsWith("god.")) {
            Soundtrack.sting("sting.god");
        } else if (f.key.startsWith("person.")) {
            Soundtrack.sting("sting.person");
        }
    }

    private static void aim(@Nullable String target) {
        if (target == null || target.isEmpty()) {
            return;
        }
        if (target.contains(",")) {
            String[] p = target.split(",");
            lookAt = new Vec3(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
        } else {
            try {
                lookId = Integer.parseInt(target);
            } catch (NumberFormatException ignored) {
                lookId = -1;
            }
        }
    }

    /** A film is on: the story waits for it. */
    public static boolean playing() {
        return film != null && Util.getMillis() - start < film.length;
    }

    static int seen() {
        return seen;
    }

    @Nullable
    static String now() {
        return playing() ? film.key : null;
    }

    private static float ms() {
        return Util.getMillis() - start;
    }

    private static float ease(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    /** 0..1: how far the bars and the darkness have closed in (in at the start, out at the end). */
    private static float presence() {
        float t = ms();
        return ease(t / 700F) * (1 - ease((t - (film.length - 900)) / 900F));
    }

    private static float vignette() {
        float t = ms();
        float v = film.vignette;
        if (film.impact > 0 && film.key.equals("ryuk")) { // closing in until he lands, then easing off
            v = t < film.impact ? film.vignette * ease(t / film.impact) : Mth.lerp(ease((t - film.impact) / 2200F), film.vignette, 0.38F);
        }
        return v * presence();
    }

    private static float zoom() {
        float t = ms();
        if (film.key.equals("ryuk")) { // a slow push in while the world holds its breath, a punch back at the landing
            if (t < film.impact) {
                return Mth.lerp(ease(t / film.impact), 1.0F, film.zoom);
            }
            float k = (t - film.impact) / 900F;
            return k < 0.15F ? Mth.lerp(k / 0.15F, film.zoom, 1.1F) : Mth.lerp(ease((k - 0.15F) / 0.85F), 1.1F, 1.0F);
        }
        if (film.fromBlack) { // the realm opens up: wide from narrow
            return Mth.lerp(ease(t / 5500F), film.zoom, 1.0F);
        }
        return t < 450 ? Mth.lerp(ease(t / 450F), 1.0F, film.zoom) : Mth.lerp(ease((t - 450) / 1300F), film.zoom, 1.0F);
    }

    private static float shake() {
        if (film.impact < 0) {
            return 0;
        }
        float k = (ms() - film.impact) / 750F;
        return k < 0 || k > 1 ? 0 : (1 - k) * (1 - k) * (film.key.equals("ryuk") ? 1.8F : 0.6F);
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov event) {
        if (playing()) {
            event.setFOV(event.getFOV() * zoom());
        }
    }

    /** While a film is on, the chat keeps out of the shot (what came meanwhile is there after, and in its history). */
    @SubscribeEvent
    public static void chat(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre event) {
        if (playing() && event.getOverlay().id().equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CHAT_PANEL.id())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void angles(ViewportEvent.ComputeCameraAngles event) {
        float s = playing() ? shake() : 0;
        if (s > 0) {
            float t = ms() / 1000F;
            event.setPitch(event.getPitch() + s * Mth.sin(t * 47F));
            event.setYaw(event.getYaw() + s * 0.7F * Mth.sin(t * 61F + 1.3F));
            event.setRoll(event.getRoll() + s * 1.4F * Mth.sin(t * 39F + 2.1F));
        }
    }

    /** The camera turns to the film's subject while its window lasts (gently: a quarter of the way each tick). */
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || film == null || mc.player == null || mc.level == null) {
            return;
        }
        if (!playing()) {
            film = null;
            return;
        }
        float t = ms();
        if (t < film.lookFrom || t > film.lookTo) {
            return;
        }
        Vec3 target = lookAt;
        if (lookId >= 0) {
            Entity e = mc.level.getEntity(lookId);
            target = e == null ? null : e.getEyePosition();
        }
        if (target == null) {
            return;
        }
        Vec3 d = target.subtract(mc.player.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
        mc.player.setYRot(mc.player.getYRot() + Mth.wrapDegrees(yaw - mc.player.getYRot()) * 0.25F);
        mc.player.setXRot(Mth.clamp(mc.player.getXRot() + (pitch - mc.player.getXRot()) * 0.25F, -90, 90));
    }

    @Mod.EventBusSubscriber(modid = AiCompanion.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void overlay(RegisterGuiOverlaysEvent event) {
            event.registerAboveAll("cinema", (gui, g, partialTick, width, height) -> render(g, width, height));
        }
    }

    private static void render(GuiGraphics g, int width, int height) {
        if (!playing()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float t = ms();
        float p = presence();
        if (film.fromBlack) { // out of the dark, slowly
            int a = (int) (255 * (1 - ease(t / 3200F)));
            if (a > 0) {
                g.fill(0, 0, width, height, a << 24);
            }
        }
        float v = vignette();
        if (v > 0.01F) { // the edges darken: concentric frames, darker outward
            for (int i = 0; i < 24; i++) {
                float k = i / 24F;
                int a = (int) (255 * v * (1 - k) * (1 - k) * 0.22F);
                int ix = (int) (width * 0.5F * k * 0.9F), iy = (int) (height * 0.5F * k * 0.9F);
                int c = a << 24;
                g.fill(ix, iy, width - ix, iy + Math.max(1, height / 48), c);
                g.fill(ix, height - iy - Math.max(1, height / 48), width - ix, height - iy, c);
                g.fill(ix, iy, ix + Math.max(1, width / 64), height - iy, c);
                g.fill(width - ix - Math.max(1, width / 64), iy, width - ix, height - iy, c);
            }
        }
        if (film.impact > 0 && t >= film.impact && t < film.impact + 380) { // the flash of the landing
            int a = (int) (150 * (1 - (t - film.impact) / 380F));
            g.fill(0, 0, width, height, a << 24 | (film.key.equals("ryuk") ? 0x500808 : 0x302010));
        }
        int bar = (int) (height * 0.12F * p);
        if (bar > 0) {
            g.fill(0, 0, width, bar, 0xFF000000);
            g.fill(0, height - bar, width, height, 0xFF000000);
        }
        float shown = ease((t - film.titleAt) / 500F) * (1 - ease((t - (film.length - 1100)) / 700F));
        if (t >= film.titleAt && shown > 0.01F) {
            int alpha = (int) (255 * shown);
            Component title = Component.translatable("aicompanion.cine." + film.key + ".title").withStyle(ChatFormatting.BOLD);
            Component sub = Component.translatable("aicompanion.cine." + film.key + ".sub");
            float scale = Math.min(film.titleScale, width * 0.86F / Math.max(1, mc.font.width(title))); // a long name fits the screen
            int ty = (int) (height * (film.key.equals("ryuk") ? 0.73F : 0.66F)); // under his face, not over it
            Ink.title(g, mc.font, title, width / 2, ty - (int) (9 * scale) + (int) ((1 - shown) * 6), scale, alpha << 24 | (film.color & 0xFFFFFF));
            int sw = mc.font.width(sub);
            g.drawString(mc.font, sub, (width - sw) / 2, ty + 6, alpha << 24 | 0xD8D2C8, true);
            int line = (int) (Math.min(width * 0.36F, sw + 60) * shown);
            g.fill(width / 2 - line / 2, ty + 1, width / 2 + line / 2, ty + 2, (alpha * 3 / 4) << 24 | (film.color & 0xFFFFFF));
        }
    }
}
