package ru.maksim.aicompanion.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import ru.maksim.aicompanion.AiCompanion;
import ru.maksim.aicompanion.Network;
import ru.maksim.aicompanion.StoryTest;

/** The story-test build's key (N): the story moves on to its next moment (StoryTest). Not even registered in the normal build. */
@Mod.EventBusSubscriber(modid = AiCompanion.MODID, value = Dist.CLIENT)
public final class StorySkip {
    static final KeyMapping KEY = new KeyMapping("key.aicompanion.story_skip", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N, "key.categories.aicompanion");

    private StorySkip() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !StoryTest.ON) {
            return;
        }
        while (KEY.consumeClick()) {
            if (Minecraft.getInstance().getConnection() != null) {
                Network.CHANNEL.sendToServer(new Network.Skip());
            }
        }
    }

    @Mod.EventBusSubscriber(modid = AiCompanion.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        private Registration() {
        }

        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) {
            if (StoryTest.ON) {
                event.register(KEY);
            }
        }
    }
}
