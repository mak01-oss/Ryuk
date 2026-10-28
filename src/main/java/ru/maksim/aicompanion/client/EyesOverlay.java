package ru.maksim.aicompanion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import ru.maksim.aicompanion.AiCompanion;
import ru.maksim.aicompanion.Network;

/**
 * The shinigami eyes: over each human in view, their true name in red and the life they have left counting down
 * (nothing under a Death Note owner's name). Drawn in the world, so walls hide them: the eyes need to see the face.
 * The player's own eyes glow red in third person.
 */
@Mod.EventBusSubscriber(modid = AiCompanion.MODID, value = Dist.CLIENT)
public final class EyesOverlay {
    private EyesOverlay() {
    }

    @SubscribeEvent
    public static void onRender(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        Network.Sight.Entry seen = ClientKira.seen(entity.getId());
        if (seen == null || entity.isInvisible()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        boolean tagged = entity.hasCustomName() || entity instanceof AbstractClientPlayer; // stay above the vanilla name tag
        pose.pushPose();
        pose.translate(0, entity.getBbHeight() + (tagged ? 0.85 : 0.6), 0);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f m = pose.last().pose();
        String name = seen.name();
        font.drawInBatch(name, -font.width(name) / 2.0F, seen.secondsLeft() < 0 ? 0 : -10, 0xFFFF3030, false, m, buffers,
                Font.DisplayMode.NORMAL, 0x50000000, LightTexture.FULL_BRIGHT);
        if (seen.secondsLeft() >= 0) {
            String life = life(Math.max(0, seen.secondsLeft() - ClientKira.sinceSight()));
            font.drawInBatch(life, -font.width(life) / 2.0F, 0, 0xFFF2E6E6, false, m, buffers,
                    Font.DisplayMode.NORMAL, 0x50000000, LightTexture.FULL_BRIGHT);
        }
        pose.popPose();
    }

    /** Under an hour: minutes and seconds ticking; then hours; far off, days (of play). */
    static String life(long s) {
        if (s < 3600) {
            return String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60);
        }
        if (s < 100 * 3600) {
            return String.format(Locale.ROOT, "%dh %02dm", s / 3600, s % 3600 / 60);
        }
        return String.format(Locale.ROOT, "%dd %02dh", s / 86400, s % 86400 / 3600);
    }

    @Mod.EventBusSubscriber(modid = AiCompanion.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Layers {
        private Layers() {
        }

        @SubscribeEvent
        public static void add(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                if (event.getSkin(skin) instanceof PlayerRenderer renderer) {
                    renderer.addLayer(new RedEyes(renderer));
                }
            }
        }
    }

    /**
     * Glowing red over the eyes of the face, like the spider's: only on the local player, only after the deal, on the
     * pixels their look says (EyeLook: found on the skin, set in EyeLookScreen). Drawn on the hat layer's surface, so
     * glasses or hair worn on the second layer don't hide it, even with that layer switched off.
     */
    private static final class RedEyes extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        RedEyes(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                           float limbSwingAmount, float partialTick, float age, float headYaw, float headPitch) {
            if (ClientKira.eyes > 0 && player == Minecraft.getInstance().player && !player.isInvisible()) {
                long[] look = EyeLook.of(player);
                if ((look[0] | look[1]) == 0) {
                    return;
                }
                net.minecraft.client.model.geom.ModelPart part = look[2] != 0 ? getParentModel().head : getParentModel().hat; // deep: on the face
                boolean shown = part.visible;
                part.visible = true;
                part.render(pose, buffers.getBuffer(RenderType.eyes(EyeLook.texture(look[0], look[1]))), LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
                part.visible = shown;
            }
        }
    }
}
