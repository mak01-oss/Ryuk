package ru.maksim.aicompanion.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.maksim.aicompanion.AiCompanion;
import ru.maksim.aicompanion.CompanionEntity;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Ryuk body: assets/aicompanion/{geo,textures,animations}/entity/ryuk*, made by tools/ryuk/make_ryuk.py. */
@Mod.EventBusSubscriber(modid = AiCompanion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CompanionRenderer extends GeoEntityRenderer<CompanionEntity> {
    public CompanionRenderer(EntityRendererProvider.Context context) {
        super(context, new CompanionModel());
        withScale(CompanionEntity.MODEL_SCALE);
        shadowRadius = 0.5F;
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AiCompanion.COMPANION.get(), CompanionRenderer::new);
    }
}
