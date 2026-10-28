package ru.maksim.aicompanion.client;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.maksim.aicompanion.AiCompanion;
import ru.maksim.aicompanion.SidohEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Sidoh (make_sidoh.py): drawn only for whoever has touched his notebook, as the manga's rules have it. Subscribes
 * itself, not through a nested Registration class: EventBus names its generated handlers after the simple class name,
 * method and event, so a second Registration.register(RegisterRenderers) silently runs HumanRenderer's instead.
 */
@Mod.EventBusSubscriber(modid = AiCompanion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class SidohRenderer extends GeoEntityRenderer<SidohEntity> {
    public SidohRenderer(EntityRendererProvider.Context context) {
        super(context, new SidohModel());
        shadowRadius = 0;
    }

    @Override
    public boolean shouldRender(SidohEntity entity, Frustum frustum, double x, double y, double z) {
        return (ClientHooks.seesSidoh() || entity.level().dimension() == ru.maksim.aicompanion.Realm.KEY) // at home in the realm, any dreamer sees him
                && super.shouldRender(entity, frustum, x, y, z);
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AiCompanion.SIDOH.get(), SidohRenderer::new);
        event.registerEntityRenderer(AiCompanion.LOST_PAGE.get(), c -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(c, 1.3F, true)); // his loose pages, glowing faintly
    }
}
