package ru.maksim.aicompanion.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.maksim.aicompanion.AiCompanion;

/** The notebook's tooltip: near-black, framed in dried-blood red, like nothing else in the inventory. */
@Mod.EventBusSubscriber(modid = AiCompanion.MODID, value = Dist.CLIENT)
public final class NoteLook {
    private NoteLook() {
    }

    @SubscribeEvent
    public static void tooltipColours(RenderTooltipEvent.Color event) {
        if (event.getItemStack().is(AiCompanion.DEATH_NOTE.get())) {
            event.setBackgroundStart(0xF20B0709);
            event.setBackgroundEnd(0xF2040203);
            event.setBorderStart(0xFFB0141E);
            event.setBorderEnd(0xFF3C060B);
        }
    }
}
