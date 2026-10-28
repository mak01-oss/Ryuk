package ru.maksim.aicompanion.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import ru.maksim.aicompanion.CompanionEntity;

/** Client-only answers for common code; only ever called behind a level().isClientSide check. */
public final class ClientHooks {
    private ClientHooks() {
    }

    public static boolean holdsApple() {
        Player player = Minecraft.getInstance().player;
        return player != null && (CompanionEntity.isApple(player.getMainHandItem()) || CompanionEntity.isApple(player.getOffhandItem()));
    }

    /** Has the local player ever touched Sidoh's notebook? Only then do they see him. */
    public static boolean seesSidoh() {
        return (ClientKira.story & 1) != 0;
    }
}
