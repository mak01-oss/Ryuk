package ru.maksim.aicompanion.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import ru.maksim.aicompanion.Network;

/** Ryuk puts the eye deal to his human: the price in plain words, and the choice. */
public class EyeDealScreen extends Screen {
    private static final int W = 300;
    private boolean answered;

    public EyeDealScreen() {
        super(Component.translatable("aicompanion.deal.title"));
    }

    @Override
    protected void init() {
        int x = (width - W) / 2, y = height / 2 + 62;
        addRenderableWidget(Button.builder(Component.translatable("aicompanion.deal.yes").withStyle(ChatFormatting.RED), b -> answer(true))
                .bounds(x, y, W / 2 - 4, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("aicompanion.deal.no"), b -> answer(false))
                .bounds(x + W / 2 + 4, y, W / 2 - 4, 20).build());
        Soundtrack.sting("sting.pact"); // Ryuk offers something
    }

    private void answer(boolean yes) {
        answered = true;
        Network.CHANNEL.sendToServer(new Network.Answer(Network.Choice.EYE_DEAL, yes));
        onClose();
    }

    @Override
    public void removed() {
        if (!answered) {
            Network.CHANNEL.sendToServer(new Network.Answer(Network.Choice.EYE_DEAL, false)); // closed = not now
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0xF0060000, 0xF0280404);
        int x = (width - W) / 2, y = height / 2 - 92;
        Ink.title(g, font, Component.translatable("aicompanion.deal.title").withStyle(ChatFormatting.BOLD), width / 2, y, 2.0F, 0xFFE02020);
        y += 32;
        y = Ink.paragraph(g, font, Component.translatable("aicompanion.deal.text1"), x, y, W, 0xFFE8DDD5);
        y = Ink.paragraph(g, font, Component.translatable("aicompanion.deal.text2"), x, y, W, 0xFFFF6A5A);
        if (ClientKira.eyes > 0) {
            y = Ink.paragraph(g, font, Component.translatable("aicompanion.deal.again"), x, y, W, 0xFFFF6A5A);
        }
        Ink.paragraph(g, font, Component.translatable("aicompanion.deal.note").withStyle(ChatFormatting.ITALIC), x, y, W, 0xFF9C8C84);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
