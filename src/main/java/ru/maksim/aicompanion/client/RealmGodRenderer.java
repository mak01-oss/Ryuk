package ru.maksim.aicompanion.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.maksim.aicompanion.AiCompanion;
import ru.maksim.aicompanion.RealmGod;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** The realm's gods (make_realm.py): each his own geo, texture and animations; eyes and jewels glow in the dark. */
@Mod.EventBusSubscriber(modid = AiCompanion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class RealmGodRenderer extends GeoEntityRenderer<RealmGod> {
    public RealmGodRenderer(EntityRendererProvider.Context context) {
        super(context, new GeoModel<>() {
            @Override
            public ResourceLocation getModelResource(RealmGod god) {
                return new ResourceLocation(AiCompanion.MODID, "geo/entity/" + god.who() + ".geo.json");
            }

            @Override
            public ResourceLocation getTextureResource(RealmGod god) {
                return new ResourceLocation(AiCompanion.MODID, "textures/entity/" + god.who() + ".png");
            }

            @Override
            public ResourceLocation getAnimationResource(RealmGod god) {
                return new ResourceLocation(AiCompanion.MODID, "animations/entity/" + god.who() + ".animation.json");
            }
        });
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        shadowRadius = 0.5F;
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AiCompanion.REALM_GOD.get(), RealmGodRenderer::new);
    }
}
