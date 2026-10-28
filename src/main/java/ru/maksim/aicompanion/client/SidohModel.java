package ru.maksim.aicompanion.client;

import net.minecraft.resources.ResourceLocation;
import ru.maksim.aicompanion.AiCompanion;
import ru.maksim.aicompanion.SidohEntity;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/** Sidoh (make_sidoh.py): the bar of chocolate shows only while he eats it. */
public class SidohModel extends DefaultedEntityGeoModel<SidohEntity> {
    public SidohModel() {
        super(new ResourceLocation(AiCompanion.MODID, "sidoh"), "head");
    }

    @Override
    public void setCustomAnimations(SidohEntity entity, long instanceId, AnimationState<SidohEntity> state) {
        super.setCustomAnimations(entity, instanceId, state);
        CoreGeoBone chocolate = getAnimationProcessor().getBone("chocolate");
        if (chocolate != null) {
            chocolate.setHidden(!entity.mood().equals("eat"));
        }
    }
}
