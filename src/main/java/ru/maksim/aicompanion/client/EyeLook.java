package ru.maksim.aicompanion.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import ru.maksim.aicompanion.AiCompanion;

/**
 * The local player's red eyes: which pixels of their face glow (bright and dim, a bit per pixel of the 8 x 8 face),
 * for the skin they wear. Found on the face the first time (EyeFinder), then as set in EyeLookScreen, and kept per
 * skin in config/aicompanion-eyes.properties, so a skin keeps its eyes in every world. Also the small texture the
 * glow is drawn from: the hat layer's front, so it shows over glasses or hair worn on the second layer.
 */
public final class EyeLook {
    private static final int BRIGHT = 0xFF1E28FF, DIM = 0xFF141496; // ABGR: (255, 40, 30) and (150, 20, 20)
    private static final Properties SAVED = new Properties();
    private static boolean loaded;
    private static String skin = "";
    private static long bright = EyeFinder.STEVE[0], dim = EyeFinder.STEVE[1];
    private static boolean deep; // the glow on the face itself, a pixel under the hat layer (skins with relief on that layer)
    @Nullable
    static long[] editing; // the editor's look while it is open: the preview shows it
    @Nullable
    private static DynamicTexture texture;
    private static ResourceLocation location;
    private static long drawnBright = -1, drawnDim = -1;

    private EyeLook() {
    }

    /** The look for this player's skin now ({bright, dim, deep 0/1}): the one being edited, the saved one, or the one found on the face. */
    static long[] of(AbstractClientPlayer player) {
        if (editing != null) {
            return editing;
        }
        String key = player.getSkinTextureLocation().toString();
        if (!key.equals(skin)) {
            load();
            String saved = SAVED.getProperty(key);
            long[] look = saved != null ? parse(saved) : found(player.getSkinTextureLocation());
            if (look == null) {
                return new long[]{bright, dim, deep ? 1 : 0}; // the skin isn't on the GPU yet: look again next frame
            }
            skin = key;
            bright = look[0];
            dim = look[1];
            deep = look.length > 2 && look[2] != 0;
        }
        return new long[]{bright, dim, deep ? 1 : 0};
    }

    /** Kept for this player's skin from now on. */
    static void save(AbstractClientPlayer player, long b, long d, boolean inset) {
        skin = player.getSkinTextureLocation().toString();
        bright = b;
        dim = d;
        deep = inset;
        load();
        SAVED.setProperty(skin, Long.toHexString(b) + "," + Long.toHexString(d) + (inset ? ",deep" : ""));
        try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
            SAVED.store(w, "Ryuk's eye deal: which face pixels glow, per skin (bright,dim: a bit per pixel, row by row; deep: on the face, under the hat layer)");
        } catch (IOException e) {
            AiCompanion.LOGGER.warn("Could not save the eyes", e);
        }
    }

    /** Where the eyes are on this skin, by EyeFinder; null until the texture is on the GPU. */
    @Nullable
    static long[] found(ResourceLocation skinTexture) {
        int[] face = face(skinTexture);
        return face == null ? null : EyeFinder.find(face);
    }

    /** The face as one sees it (8 x 8 RGB, the hat layer over the head), read back from the skin texture. */
    @Nullable
    private static int[] face(ResourceLocation skinTexture) {
        RenderSystem.assertOnRenderThread();
        AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(skinTexture);
        GlStateManager._bindTexture(t.getId());
        int w = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int h = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (w < 64 || w != h || w % 64 != 0) {
            return null; // still the missing texture (the download isn't in), or not a skin
        }
        try (NativeImage img = new NativeImage(w, h, false)) {
            img.downloadTexture(0, false);
            int s = w / 64; // an HD skin: the same layout, bigger
            int[] out = new int[64];
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int head = img.getPixelRGBA((8 + x) * s, (8 + y) * s), hat = img.getPixelRGBA((40 + x) * s, (8 + y) * s);
                    int c = (hat >>> 24) >= 128 ? hat : head; // ABGR
                    out[y * 8 + x] = (c & 255) << 16 | (c >> 8 & 255) << 8 | c >> 16 & 255;
                }
            }
            return out;
        }
    }

    /** The glow's texture for a look, redrawn only when the look changes. */
    static ResourceLocation texture(long b, long d) {
        if (texture == null) {
            texture = new DynamicTexture(64, 64, true);
            location = Minecraft.getInstance().getTextureManager().register("aicompanion_eyes", texture);
        }
        if (b != drawnBright || d != drawnDim) {
            NativeImage img = texture.getPixels();
            img.fillRect(0, 0, 64, 64, 0);
            for (int i = 0; i < 64; i++) {
                if ((b >> i & 1) != 0 || (d >> i & 1) != 0) {
                    int c = (b >> i & 1) != 0 ? BRIGHT : DIM;
                    img.setPixelRGBA(40 + i % 8, 8 + i / 8, c); // the hat layer's front
                    img.setPixelRGBA(8 + i % 8, 8 + i / 8, c); // and the face's, for the deep glow
                }
            }
            texture.upload();
            drawnBright = b;
            drawnDim = d;
        }
        return location;
    }

    private static long[] parse(String v) {
        String[] p = v.split(",");
        try {
            return new long[]{Long.parseUnsignedLong(p[0], 16), p.length > 1 ? Long.parseUnsignedLong(p[1], 16) : 0,
                    p.length > 2 && p[2].equals("deep") ? 1 : 0};
        } catch (NumberFormatException e) {
            return EyeFinder.STEVE.clone();
        }
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (Files.exists(file())) {
            try (Reader r = Files.newBufferedReader(file(), StandardCharsets.UTF_8)) {
                SAVED.load(r);
            } catch (IOException e) {
                AiCompanion.LOGGER.warn("Could not read the eyes", e);
            }
        }
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("aicompanion-eyes.properties");
    }
}
