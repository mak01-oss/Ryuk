package ru.maksim.aicompanion.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Random;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import ru.maksim.aicompanion.AiCompanion;
import ru.maksim.aicompanion.Realm;

/**
 * The sky over the realm of the gods of death: a dome going from near-black overhead to a dusty grey-violet at the
 * horizon, a huge dying sun low in the south, dull red, cut by a dark band, its halo bleeding into the haze; long dark
 * streaks of cloud wheeling slowly round; specks of ash for stars. No vanilla clouds, rain or sunrise. The haze is thick
 * enough to swallow the far wastes.
 */
@Mod.EventBusSubscriber(modid = AiCompanion.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class RealmSky extends DimensionSpecialEffects {
    public static final ResourceLocation ID = new ResourceLocation(AiCompanion.MODID, "shinigami_realm");
    private static final float R = 100;
    private static final double SUN_AZ = Math.toRadians(160), SUN_EL = Math.toRadians(9);
    private static final float[] HORIZON = {0.33F, 0.28F, 0.34F}, LOW = {0.20F, 0.17F, 0.21F}, ZENITH = {0.06F, 0.05F, 0.08F};
    private final float[][] ash = new float[140][];

    public RealmSky() {
        super(Float.NaN, false, SkyType.NONE, false, false);
        Random r = new Random(13);
        for (int i = 0; i < ash.length; i++) {
            double az = r.nextDouble() * Math.PI * 2, el = Math.toRadians(14 + r.nextDouble() * 70);
            ash[i] = new float[]{(float) (Math.cos(el) * Math.sin(az)), (float) Math.sin(el), (float) (Math.cos(el) * Math.cos(az)),
                    0.15F + r.nextFloat() * 0.35F, 0.25F + r.nextFloat() * 0.5F};
        }
    }

    @SubscribeEvent
    public static void register(RegisterDimensionSpecialEffectsEvent event) {
        event.register(ID, new RealmSky());
        MinecraftForge.EVENT_BUS.addListener(RealmSky::fog);
    }

    /** The haze: close enough to hide where the world ends, far enough to see the King hanging in it. */
    private static void fog(ViewportEvent.RenderFog event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.dimension() == Realm.KEY) {
            event.setNearPlaneDistance(event.getMode() == FogRenderer.FogMode.FOG_SKY ? 0 : 10);
            event.setFarPlaneDistance(Math.min(event.getFarPlaneDistance(), 130));
            event.setCanceled(true);
        }
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return new Vec3(HORIZON[0], HORIZON[1], HORIZON[2]); // the haze is the horizon's colour: no seam
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        return null; // no overworld dusk glowing orange on one side of the haze: the realm's light doesn't change
    }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack pose, double x, double y, double z, Matrix4f projection) {
        return true; // none
    }

    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, net.minecraft.client.renderer.LightTexture light, double x, double y, double z) {
        return true; // none
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack pose, Camera camera, Matrix4f projection, boolean foggy, Runnable setupFog) {
        Matrix4f m = pose.last().pose();
        float t = ticks + partialTick;
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int lat = -8; lat < 18; lat++) { // the dome, in 5 degree bands
            double e0 = Math.toRadians(lat * 5), e1 = Math.toRadians((lat + 1) * 5);
            float[] c0 = dome(e0), c1 = dome(e1);
            for (int lon = 0; lon < 36; lon++) {
                double a0 = Math.toRadians(lon * 10), a1 = Math.toRadians(lon * 10 + 10);
                tri(b, m, dir(e0, a0), c0, 1, dir(e0, a1), c0, 1, dir(e1, a1), c1, 1);
                tri(b, m, dir(e0, a0), c0, 1, dir(e1, a1), c1, 1, dir(e1, a0), c1, 1);
            }
        }
        for (int s = 0; s < 6; s++) { // dark streaks of cloud, wheeling slowly
            double az = Math.toRadians(s * 61 + t * 0.004 * (s % 2 == 0 ? 1 : -0.7)), el = Math.toRadians(12 + s * 5 % 23);
            double len = Math.toRadians(40 + s * 7), thick = Math.toRadians(2.2 + s % 3);
            float[] dark = {0.09F, 0.075F, 0.10F};
            for (int k = 0; k < 8; k++) {
                double u0 = az + len * k / 8, u1 = az + len * (k + 1) / 8;
                float a0 = 0.65F * (float) Math.sin(Math.PI * k / 8), a1 = 0.65F * (float) Math.sin(Math.PI * (k + 1) / 8);
                tri(b, m, dir(el - thick, u0), dark, a0, dir(el - thick, u1), dark, a1, dir(el + thick, u1), dark, a1 * 0.5F);
                tri(b, m, dir(el - thick, u0), dark, a0, dir(el + thick, u1), dark, a1 * 0.5F, dir(el + thick, u0), dark, a0 * 0.5F);
            }
        }
        BufferUploader.drawWithShader(b.end());
        // the dying sun and its halo, added on: light through haze
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        disc(b, m, Math.toRadians(32), new float[]{0.24F, 0.05F, 0.05F}, 0.45F, 0F); // the halo, bleeding into the haze
        disc(b, m, Math.toRadians(15), new float[]{0.32F, 0.07F, 0.06F}, 0.45F, 0F);
        disc(b, m, Math.toRadians(9.5), new float[]{0.56F, 0.12F, 0.10F}, 0.95F, 0.85F); // a dull, dying red
        for (float[] a : ash) { // specks of ash, flickering
            float k = a[3] * (0.6F + 0.4F * (float) Math.sin(t * 0.05 * a[4] + a[0] * 50));
            Vec3 c = new Vec3(a[0], a[1], a[2]).scale(R - 2);
            Vec3 u = c.cross(new Vec3(0, 1, 0)).normalize().scale(0.18), v = c.cross(u).normalize().scale(0.18);
            float[] w = {0.85F, 0.82F, 0.88F};
            tri(b, m, c.add(u), w, k, c.add(v), w, k, c.subtract(u), w, k);
            tri(b, m, c.add(u), w, k, c.subtract(u), w, k, c.subtract(v), w, k);
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.defaultBlendFunc();
        b.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        band(b, m); // the dark band across the sun's face
        BufferUploader.drawWithShader(b.end());
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        return true;
    }

    private static float[] dome(double e) {
        double d = Math.toDegrees(e);
        if (d < 0) {
            return mix(HORIZON, LOW, (float) Math.min(1, -d / 30));
        }
        float k = (float) Math.pow(Math.min(1, d / 75), 0.6);
        return mix(HORIZON, ZENITH, k);
    }

    private static float[] mix(float[] a, float[] b, float k) {
        return new float[]{a[0] + (b[0] - a[0]) * k, a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k};
    }

    private static Vec3 dir(double el, double az) {
        return new Vec3(Math.cos(el) * Math.sin(az), Math.sin(el), Math.cos(el) * Math.cos(az)).scale(R);
    }

    /** A round glow centred on the sun: {@code centre} alpha in the middle, {@code edge} alpha at the rim. */
    private static void disc(BufferBuilder b, Matrix4f m, double radius, float[] c, float centre, float edge) {
        Vec3 sun = dir(SUN_EL, SUN_AZ);
        Vec3 n = sun.normalize(), u = n.cross(new Vec3(0, 1, 0)).normalize(), v = u.cross(n).normalize();
        double rr = Math.tan(radius) * R;
        for (int i = 0; i < 40; i++) {
            double a0 = i * Math.PI * 2 / 40, a1 = (i + 1) * Math.PI * 2 / 40;
            Vec3 p0 = sun.add(u.scale(Math.cos(a0) * rr)).add(v.scale(Math.sin(a0) * rr));
            Vec3 p1 = sun.add(u.scale(Math.cos(a1) * rr)).add(v.scale(Math.sin(a1) * rr));
            tri(b, m, sun, c, centre, p0, c, edge, p1, c, edge);
        }
    }

    private static void band(BufferBuilder b, Matrix4f m) {
        Vec3 sun = dir(SUN_EL, SUN_AZ);
        Vec3 n = sun.normalize(), u = n.cross(new Vec3(0, 1, 0)).normalize(), v = u.cross(n).normalize();
        double w = Math.tan(Math.toRadians(10.5)) * R, h = Math.tan(Math.toRadians(0.7)) * R;
        Vec3 o = sun.subtract(n.scale(0.5)).add(v.scale(-Math.tan(Math.toRadians(2.6)) * R));
        float[] dark = {0.08F, 0.05F, 0.07F};
        Vec3 p0 = o.add(u.scale(-w)), p1 = o.add(u.scale(w)), p2 = p1.add(v.scale(h)), p3 = p0.add(v.scale(h));
        tri(b, m, p0, dark, 0.7F, p1, dark, 0.7F, p2, dark, 0.7F); // a thin dark scar across its face
        tri(b, m, p0, dark, 0.7F, p2, dark, 0.7F, p3, dark, 0.7F);
    }

    private static void tri(BufferBuilder b, Matrix4f m, Vec3 p0, float[] c0, float a0, Vec3 p1, float[] c1, float a1, Vec3 p2, float[] c2, float a2) {
        b.vertex(m, (float) p0.x, (float) p0.y, (float) p0.z).color(c0[0], c0[1], c0[2], a0).endVertex();
        b.vertex(m, (float) p1.x, (float) p1.y, (float) p1.z).color(c1[0], c1[1], c1[2], a1).endVertex();
        b.vertex(m, (float) p2.x, (float) p2.y, (float) p2.z).color(c2[0], c2[1], c2[2], a2).endVertex();
    }
}
