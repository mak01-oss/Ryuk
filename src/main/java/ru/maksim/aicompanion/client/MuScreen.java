package ru.maksim.aicompanion.client;

import java.io.IOException;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import ru.maksim.aicompanion.AiCompanion;
import ru.maksim.aicompanion.Config;

/**
 * After Ryuk wrote the name under his game: no respawn. Whoever used the notebook goes to neither heaven nor hell, but
 * to Mu, nothing. In singleplayer the way out deletes the world (Config.PERMADEATH = delete) or leaves it lost; on a
 * server, or if the player wants to, they stay and watch as a ghost (spectator).
 */
public class MuScreen extends Screen {
    private final boolean single = Minecraft.getInstance().hasSingleplayerServer();
    private final boolean delete = single && Config.PERMADEATH.get().strip().equalsIgnoreCase("delete");

    private boolean scored;

    public MuScreen() {
        super(Component.translatable("aicompanion.mu.title"));
    }

    @Override
    protected void init() {
        if (!scored) {
            scored = true;
            Soundtrack.scene("music.mu", false); // nothing, and a far choir
        }
        int y = height / 2 + 40;
        if (single) {
            addRenderableWidget(Button.builder(Component.translatable(delete ? "aicompanion.mu.leave_delete" : "aicompanion.mu.leave_keep")
                    .withStyle(ChatFormatting.DARK_RED), b -> leave()).bounds(width / 2 - 110, y, 220, 20).build());
            y += 24;
        }
        if (!delete) {
            addRenderableWidget(Button.builder(Component.translatable("aicompanion.mu.ghost"), b -> ghost())
                    .bounds(width / 2 - 110, y, 220, 20).build());
        }
    }

    /** Stay as a spectator: the server keeps a sealed player in spectator mode. */
    private void ghost() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.player.isAlive()) {
            mc.player.respawn();
        }
        mc.setScreen(null);
    }

    /** Out to the title screen; under "delete" the world folder goes too, exactly as the world list's Delete does. */
    void leave() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        String id = server == null ? null : server.getWorldPath(LevelResource.ROOT).normalize().getFileName().toString();
        if (mc.level != null) {
            mc.level.disconnect();
        }
        mc.clearLevel(new GenericDirtMessageScreen(Component.translatable("aicompanion.mu.leaving"))); // stops and saves the server
        if (delete && id != null) {
            try (LevelStorageSource.LevelStorageAccess access = mc.getLevelSource().createAccess(id)) {
                access.deleteLevel();
                AiCompanion.LOGGER.info("Ryuk's game is lost: world '{}' deleted", id);
            } catch (IOException e) {
                AiCompanion.LOGGER.warn("Couldn't delete the lost world '{}'", id, e);
            }
        }
        mc.setScreen(new TitleScreen());
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF000000);
        int w = Math.min(320, width - 40), x = (width - w) / 2, y = height / 2 - 78;
        Ink.title(g, font, Component.translatable("aicompanion.mu.title"), width / 2, y, 4.0F, 0xFFEDEDED);
        y += 46;
        String why = ClientKira.why.isEmpty() ? "TIME" : ClientKira.why;
        y = Ink.paragraph(g, font, Component.translatable("aicompanion.mu.why." + why), x, y, w, 0xFFE02020);
        Ink.paragraph(g, font, Component.translatable("aicompanion.mu.text"), x, y, w, 0xFFA8A8A8);
        super.render(g, mouseX, mouseY, partialTick);
    }
}
