package ru.maksim.aicompanion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.maksim.aicompanion.AiCompanion;
import ru.maksim.aicompanion.HumanEntity;

/**
 * The story's people with their own skins (make_people.py) on the jointed model: seated, the body comes down onto the
 * seat (or the floor) and leans about the hips as the stance asks (Poses).
 */
public class HumanRenderer extends HumanoidMobRenderer<HumanEntity, PeopleModel> {
    private final Map<String, ResourceLocation> skins = new HashMap<>();

    public HumanRenderer(EntityRendererProvider.Context context) {
        super(context, new PeopleModel(context.bakeLayer(PeopleModel.LAYER)), 0.5F);
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(HumanEntity entity) {
        return skins.computeIfAbsent(entity.skin(), s -> new ResourceLocation(AiCompanion.MODID, "textures/entity/people/" + s + ".png"));
    }

    @Override
    protected void setupRotations(HumanEntity entity, PoseStack pose, float bob, float bodyYaw, float partialTick) {
        super.setupRotations(entity, pose, bob, bodyYaw, partialTick);
        HumanEntity.Stance stance = entity.stance();
        float lean = stance == HumanEntity.Stance.STAND && entity.role() == HumanEntity.Role.L ? 0.22F : Poses.lean(stance);
        pose.translate(0, -(24 - Poses.seat(stance)) / 16.0F, 0); // the seat (or the floor) under the hips, not under the feet
        if (lean != 0 && entity.deathTime == 0) {
            pose.translate(0, 0.75F, 0);
            pose.mulPose(Axis.XP.rotation(-lean)); // about the hips; the model makes up for it in the legs
            pose.translate(0, -0.75F, 0);
        }
    }

    @Mod.EventBusSubscriber(modid = AiCompanion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void register(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(AiCompanion.HUMAN.get(), HumanRenderer::new);
        }

        @SubscribeEvent
        public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
            event.registerLayerDefinition(PeopleModel.LAYER, PeopleModel::create);
        }
    }
}
