package ru.maksim.aicompanion.client;

import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import ru.maksim.aicompanion.Config;
import ru.maksim.aicompanion.Network;

/** Ryuk's game, put to the human once the notebook has killed (or from its last page): the four rules and what losing costs. */
public class PactScreen extends Screen {
    private static final int W = 320;
    @Nullable
    private final InteractionHand thenOpen; // the notebook that was being opened, opened right after

    private boolean offered;

    public PactScreen(@Nullable InteractionHand thenOpen) {
        super(Component.translatable("aicompanion.pact.title"));
        this.thenOpen = thenOpen;
    }

    @Override
    protected void init() {
        if (!offered && thenOpen == null) { // offered by Ryuk, not looked up in the notebook
            offered = true;
            Soundtrack.sting("sting.pact");
        }
        int x = (width - W) / 2, y = height / 2 + 84;
        addRenderableWidget(Button.builder(Component.translatable("aicompanion.pact.yes").withStyle(ChatFormatting.RED), b -> answer(true))
                .bounds(x, y, W / 2 - 4, 20).build());
        addRenderableWidget(Button.builder(Component.translatable(ClientKira.pact == 2 ? "aicompanion.pact.back" : "aicompanion.pact.no"),
                b -> answer(false)).bounds(x + W / 2 + 4, y, W / 2 - 4, 20).build());
    }

    private void answer(boolean yes) {
        if (yes || ClientKira.pact == 0) {
            Network.CHANNEL.sendToServer(new Network.Answer(Network.Choice.PACT, yes));
        }
        if (yes && ClientKira.pact != 2) {
            Soundtrack.sting("sting.pact_sealed");
        }
        if (thenOpen != null) {
            DeathNoteScreen.open(thenOpen);
        } else {
            onClose();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // a choice, not a popup
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0xF0050506, 0xF0160A0A);
        int x = (width - W) / 2, y = height / 2 - 112;
        Ink.title(g, font, Component.translatable("aicompanion.pact.title").withStyle(ChatFormatting.BOLD), width / 2, y, 2.0F, 0xFFE02020);
        y += 30;
        y = Ink.paragraph(g, font, Component.translatable("aicompanion.pact.intro"), x, y, W, 0xFFE8DDD5);
        for (int i = 1; i <= 4; i++) {
            y = Ink.paragraph(g, font, Component.literal(i + ". ").append(Component.translatable("aicompanion.pact.rule" + i)), x + 8, y - 2, W - 8, 0xFFF0E6DC);
        }
        boolean delete = Minecraft.getInstance().hasSingleplayerServer() && Config.PERMADEATH.get().strip().equalsIgnoreCase("delete");
        y = Ink.paragraph(g, font, Component.translatable(delete ? "aicompanion.pact.stakes_delete" : "aicompanion.pact.stakes_ghost"),
                x, y + 2, W, 0xFFFF5A4A);
        Ink.paragraph(g, font, Component.translatable("aicompanion.pact.note").withStyle(ChatFormatting.ITALIC), x, y, W, 0xFF9C8C84);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
