package ru.maksim.aicompanion.client;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.maksim.aicompanion.AiCompanion;
import ru.maksim.aicompanion.Network;

/** The local player's fate as the server tells it (eyes, Ryuk's game, the end), and what their shinigami eyes see. */
@Mod.EventBusSubscriber(modid = AiCompanion.MODID, value = Dist.CLIENT)
public final class ClientKira {
    static int eyes, pact; // pact: Kira.Pact ordinal (0 none, 1 accepted, 2 refused)
    static int story; // bits: 1 = sees Sidoh, 2 = knows the two lies in the rules
    static boolean sealed, pactOffered, pactPending, eyesPending, dealPending, setupPending, setupAsked;
    private static long offeredAt; // when the last offer came: its line is waited for, not forever
    static String why = "";
    private static final Map<Integer, Network.Sight.Entry> SIGHT = new HashMap<>();
    private static long sightTime;
    static java.util.List<net.minecraft.network.chat.Component> caseFile = java.util.List.of(); // the notebook's "Cases" page

    private ClientKira() {
    }

    public static void fate(Network.Fate f) {
        eyes = f.eyes();
        pact = f.pact();
        sealed = f.sealed();
        why = f.why();
        pactOffered = f.pactOffered();
        story = f.story();
        Minecraft mc = Minecraft.getInstance();
        if (sealed && mc.player != null && mc.player.isAlive() && !(mc.screen instanceof MuScreen)) {
            mc.setScreen(new MuScreen()); // came back into a world where it's already over
        }
    }

    public static void caseFile(java.util.List<net.minecraft.network.chat.Component> lines) {
        caseFile = lines;
    }

    public static void sight(Network.Sight s) {
        SIGHT.clear();
        for (Network.Sight.Entry e : s.entries()) {
            SIGHT.put(e.entity(), e);
        }
        sightTime = System.currentTimeMillis();
    }

    /** What the eyes see over this entity, or null; stale after 3 s without news (out of view, eyes gone). */
    @Nullable
    static Network.Sight.Entry seen(int entity) {
        return eyes == 0 || System.currentTimeMillis() - sightTime > 3000 ? null : SIGHT.get(entity);
    }

    /** The names in view right now (for the autotest). */
    /** The true names in sight now of the entities that pass {@code which}, notebook owners too (the autotest's eyes). */
    static java.util.List<String> sightNames(java.util.function.Predicate<net.minecraft.world.entity.Entity> which) {
        Minecraft mc = Minecraft.getInstance();
        return SIGHT.values().stream().filter(e -> mc.level != null && mc.level.getEntity(e.entity()) != null && which.test(mc.level.getEntity(e.entity())))
                .map(Network.Sight.Entry::name).toList();
    }

    static java.util.List<String> sightNames() {
        return SIGHT.values().stream().filter(e -> e.secondsLeft() >= 0).map(Network.Sight.Entry::name).toList();
    }

    /** Seconds since the last view: the numbers keep counting down between packets. */
    static long sinceSight() {
        return (System.currentTimeMillis() - sightTime) / 1000;
    }

    /**
     * Offers wait for the floor (RyukLines.floor): no other screen (not over the notebook being written in), no unread
     * card, and Ryuk done saying the line that comes with them. Then his game, the eye deal, the eyes' look.
     */
    public static void offer(Network.Choice choice) {
        offeredAt = net.minecraft.Util.getMillis();
        if (choice == Network.Choice.EYE_DEAL) {
            dealPending = true;
        } else if (choice == Network.Choice.EYE_LOOK) {
            eyesPending = true;
        } else if (choice == Network.Choice.SETUP) {
            setupPending = true;
        } else {
            pactPending = true;
        }
    }

    public static void openNote(InteractionHand hand) {
        DeathNoteScreen.open(hand);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        boolean free = event.phase == TickEvent.Phase.END && mc.screen == null && mc.player != null && mc.player.isAlive()
                && RyukLines.floor(mc, offeredAt);
        if (free && dealPending) {
            dealPending = false;
            mc.setScreen(new EyeDealScreen());
        } else if (free && pactPending) {
            pactPending = false;
            if (pact == 0 && !sealed) {
                mc.setScreen(new PactScreen(null));
            }
        } else if (free && eyesPending) {
            eyesPending = false;
            mc.setScreen(new EyeLookScreen());
        }
        if (event.phase == TickEvent.Phase.END && setupPending && mc.screen == null && mc.player != null) {
            setupPending = false;
            mc.setScreen(new SetupScreen(null));
        }
    }

    /** Into a world of one's own with no key yet: the setup with its mini guide, once a session. */
    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!setupAsked && ru.maksim.aicompanion.Config.API_KEY.get().isBlank() && Minecraft.getInstance().hasSingleplayerServer()) {
            setupAsked = true;
            setupPending = true;
        }
    }

    @SubscribeEvent
    public static void onScreen(ScreenEvent.Opening event) {
        if (sealed && event.getNewScreen() instanceof DeathScreen) {
            event.setNewScreen(new MuScreen()); // no respawn button: the name was written for good
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        eyes = pact = story = 0;
        sealed = pactOffered = pactPending = eyesPending = dealPending = false;
        why = "";
        caseFile = java.util.List.of();
        SIGHT.clear();
    }
}
