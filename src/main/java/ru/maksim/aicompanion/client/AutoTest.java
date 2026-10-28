package ru.maksim.aicompanion.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import javax.annotation.Nullable;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import ru.maksim.aicompanion.AiCompanion;
import ru.maksim.aicompanion.CompanionEntity;
import ru.maksim.aicompanion.Config;
import ru.maksim.aicompanion.HumanEntity;
import ru.maksim.aicompanion.Kira;
import ru.maksim.aicompanion.Network;
import ru.maksim.aicompanion.RealmGod;
import ru.maksim.aicompanion.SidohEntity;
import ru.maksim.aicompanion.SideQuests;
import ru.maksim.aicompanion.TelevisionBlock;
import ru.maksim.aicompanion.LostPage;
import org.lwjgl.glfw.GLFW;

/**
 * Dev-only scripted look from the side: env AICOMPANION_AUTOTEST=1 (poses) or =chat (also talks to the real LLM);
 * =lore, =kira and =world play the Death Note story systems.
 * Creates a flat creative world, poses Ryuk through /companion, saves run/screenshots/autotest_*.png and quits.
 */
@Mod.EventBusSubscriber(modid = AiCompanion.MODID, value = Dist.CLIENT)
public final class AutoTest {
    private static final String MODE = System.getenv("AICOMPANION_AUTOTEST");
    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static boolean started;
    private static int wait;
    private static int reader; // client ticks, paused or not: the "reader" keeps its pace while L's show holds the game
    private static Vec3 mark = Vec3.ZERO;
    private static final List<String> NAMES = new ArrayList<>(); // true names seen with the eyes, to write down
    private static final List<String> HEARD = new ArrayList<>(); // the chat's system lines, as they came
    private static BlockPos tvAt = BlockPos.ZERO;

    private record Step(int delay, Runnable run) {
    }

    private AutoTest() {
    }

    @SubscribeEvent(receiveCanceled = true) // held back during a dream or not, it was said
    public static void onChat(ClientChatReceivedEvent.System event) {
        if (MODE != null) {
            HEARD.add(event.getMessage().getString());
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (MODE == null || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!started) {
            if (mc.screen instanceof TitleScreen) {
                started = true;
                if (MODE.equals("setup")) { // a player who has no key yet (in memory only: the file keeps the real one)
                    savedKey = Config.API_KEY.get();
                    Config.API_KEY.set("");
                }
                createWorld(mc);
                if (MODE.equals("lore")) {
                    lore();
                } else if (MODE.equals("kira")) {
                    kira();
                } else if (MODE.equals("world")) {
                    world();
                } else if (MODE.equals("hideout")) {
                    hideout();
                } else if (MODE.equals("talks")) {
                    talks();
                } else if (MODE.equals("english")) {
                    englishOnly();
                } else if (MODE.equals("film")) {
                    filmShoot();
                } else if (MODE.equals("film2") || MODE.equals("film4")) {
                    filmShoot2(MODE.equals("film2"));
                } else if (MODE.equals("film3")) {
                    filmRetake();
                } else if (MODE.equals("film5")) {
                    filmRetake2();
                } else if (MODE.equals("renounce")) {
                    renounce();
                } else if (MODE.equals("favours")) {
                    favours();
                } else if (MODE.equals("search")) {
                    search();
                } else if (MODE.equals("finale")) {
                    finale();
                } else if (MODE.equals("survival")) {
                    survival();
                } else if (MODE.equals("poses")) {
                    poses();
                } else if (MODE.equals("buildings")) {
                    buildings();
                } else if (MODE.equals("eyes")) {
                    eyes();
                } else if (MODE.equals("setup")) {
                    setup();
                } else if (MODE.equals("cards")) {
                    cards();
                } else if (MODE.equals("sounds")) {
                    soundsTest();
                } else if (MODE.equals("talk")) {
                    talkTest();
                } else if (MODE.equals("arrival")) {
                    arrivalTest();
                } else if (MODE.equals("storytest")) {
                    storyTestRun();
                } else if (MODE.equals("hqstairs")) {
                    hqStairsTest();
                } else if (MODE.equals("epilogue")) {
                    epilogueTest();
                } else if (MODE.equals("trailer")) {
                    trailerShoot();
                } else if (MODE.equals("trailer2")) {
                    trailerClips();
                } else if (MODE.equals("trailer3")) {
                    trailerPlayer();
                } else if (MODE.equals("shortstest")) {
                    shortsTest();
                } else if (MODE.equals("shorts")) {
                    shortsShoot();
                } else if (MODE.equals("shorts2")) {
                    shortsRetake();
                } else if (MODE.equals("gallery")) {
                    galleryShoot();
                } else if (MODE.equals("gallery2")) {
                    galleryRetake();
                } else if (MODE.equals("gallery3")) {
                    galleryRetake2();
                } else if (MODE.equals("modlist")) {
                    modList();
                } else if (MODE.equals("memory")) {
                    memory();
                } else if (MODE.equals("deaths")) {
                    deaths();
                } else if (MODE.equals("anims")) {
                    anims();
                } else if (MODE.equals("dice")) {
                    dice();
                } else if (MODE.equals("note")) {
                    note();
                } else if (MODE.equals("sidoh")) {
                    sidohQuest();
                } else if (MODE.equals("realm")) {
                    realmTour();
                } else {
                    script(MODE.equals("chat"));
                }
            }
            return;
        }
        if (mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen && mc.player != null) {
            mc.player.respawn(); // an ordinary death (no game): get up and go on with the script
            mc.setScreen(null);
        }
        if (!MODE.equals("cards") && !MODE.equals("talk") && !MODE.equals("arrival") && mc.player != null && ++reader % 60 == 0) { // not the player's ticks: they stop while the game is paused
            KeyMapping.click(StoryHud.NEXT.getKey()); // a reader: three seconds a story card, then "next"
            if (mc.screen instanceof TvShowScreen show) {
                show.next(); // and the show, a line every three seconds
            }
        }
        if (MODE.startsWith("film") && mc.player != null) { // on film: no story cards (they'd hold his lines back), no toasts
            StoryHud.clear();
            mc.getToasts().clear();
        }
        if (mc.screen instanceof EyeLookScreen && !MODE.equals("eyes")) {
            mc.setScreen(null); // the eyes' editor after the first deal: "Done" (only the eyes test looks into it)
        }
        boolean ours = mc.screen instanceof DeathNoteScreen || mc.screen instanceof PactScreen || mc.screen instanceof EyeDealScreen
                || mc.screen instanceof EyeLookScreen || mc.screen instanceof SetupScreen || mc.screen instanceof TvShowScreen
                || mc.screen instanceof MuScreen || mc.screen instanceof ScrapScreen || mc.screen instanceof DiceScreen
                || mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
                || mc.screen instanceof net.minecraftforge.client.gui.ModListScreen
                || MODE.startsWith("film") && mc.screen instanceof net.minecraft.client.gui.screens.InBedChatScreen; // the film goes on through falling asleep
        if (mc.player == null || mc.level == null || mc.screen != null && !ours || STEPS.isEmpty() || wait-- > 0) {
            return;
        }
        Step step = STEPS.poll();
        try {
            step.run().run();
        } catch (RuntimeException e) {
            AiCompanion.LOGGER.error("AUTOTEST step failed", e);
        }
        wait = STEPS.isEmpty() ? 0 : STEPS.peek().delay();
    }

    private static void createWorld(Minecraft mc) {
        mc.options.pauseOnLostFocus = false; // runs in the background, unfocused
        Path saves = mc.gameDirectory.toPath().resolve("saves");
        try (Stream<Path> old = Files.exists(saves) ? Files.list(saves) : Stream.empty()) {
            old.filter(p -> p.getFileName().toString().startsWith("autotest-")).forEach(AutoTest::delete);
        } catch (IOException e) {
            AiCompanion.LOGGER.warn("AUTOTEST could not list saves", e);
        }
        String name = "autotest-" + System.currentTimeMillis();
        boolean survival = MODE.equals("survival"); // an ordinary world with villages and hills, played in survival
        boolean real = survival || MODE.equals("storytest") || MODE.equals("epilogue") || MODE.equals("hideout") || MODE.startsWith("trailer") || MODE.startsWith("shorts") || MODE.startsWith("gallery") || MODE.startsWith("film"); // the same world (villages about), in creative
        LevelSettings settings = new LevelSettings(name, survival ? GameType.SURVIVAL : GameType.CREATIVE, false,
                survival ? Difficulty.EASY : Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(real ? 20261019L : 0L, real, false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(real ? WorldPresets.NORMAL : WorldPresets.FLAT)
                        .value().createWorldDimensions());
    }

    private static void delete(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            AiCompanion.LOGGER.warn("AUTOTEST could not delete {}", dir, e);
        }
    }

    private static void script(boolean chat) {
        Minecraft mc = Minecraft.getInstance();
        add(60, () -> {
            mc.options.hideGui = true;
            for (String c : new String[]{"time set 6000", "weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobSpawning false", "tp @s 0 -60 0 0 0"}) {
                cmd(c);
            }
        });
        add(20, () -> cmd("give @s aicompanion:death_note")); // whoever finds the notebook gets its shinigami
        add(10, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        add(165, () -> shot("00_arrival")); // drops in from the sky ~8 s after the pickup
        add(25, () -> shot("00b_arrival"));
        add(90, () -> {
        });
        add(10, () -> shot("01_follow_front"));
        add(2, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        add(10, () -> shot("02_follow_back"));
        for (int i = 1; i <= 5; i++) { // the owner dashes off: he has to fly after
            int z = i * 4;
            add(6, () -> cmd("tp @s 0 -60 " + z + " 0 0"));
        }
        add(1, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        add(2, () -> shot("03_flying"));
        // turning on the spot must not make him circle; idle he faces the player; the crosshair passes through him
        add(80, () -> mark = ryuk().position());
        for (int yaw = 90; yaw <= 360; yaw += 90) {
            int y = yaw;
            add(12, () -> cmd("tp @s 0 -60 20 " + y + " 0"));
        }
        add(40, () -> {
            CompanionEntity r = ryuk();
            float facing = Mth.degreesDifferenceAbs(r.getYRot(),
                    (float) (Mth.atan2(mc.player.getZ() - r.getZ(), mc.player.getX() - r.getX()) * Mth.RAD_TO_DEG) - 90.0F);
            AiCompanion.LOGGER.info(String.format(Locale.ROOT, "AUTOTEST orbit: moved %.2f, distance %.2f, facing off by %.0f deg",
                    r.position().distanceTo(mark), Math.sqrt(r.distanceToSqr(mc.player.getX(), r.getY(), mc.player.getZ())), facing));
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            lookAt(r);
        });
        add(5, () -> AiCompanion.LOGGER.info("AUTOTEST crosshair without apple hits him: {}", aimedAtRyuk(mc)));
        add(2, () -> cmd("item replace entity @s weapon.mainhand with minecraft:apple"));
        add(5, () -> AiCompanion.LOGGER.info("AUTOTEST crosshair with apple hits him: {}", aimedAtRyuk(mc)));
        add(2, () -> cmd("item replace entity @s weapon.mainhand with aicompanion:death_note"));
        add(60, () -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            cmd("companion stay");
        });
        add(10, () -> face(3.4, 0));
        add(40, () -> shot("10_lounge"));
        boolean en = Config.english();
        pose("say happy " + (en ? "Heh heh... Interesting!!" : "Хе-хе… Занятно!!"), 8, "11_laugh", 30, "12_talk");
        pose("say angry " + (en ? "Do that again and I take out my own notebook." : "Ещё раз так сделаешь — и я достану свою тетрадь."),
                12, "13_angry");
        pose("say sad " + (en ? "Out of apples... How dull." : "Яблоки кончились… Тоска."), 20, "14_sad");
        pose("say surprised " + (en ? "What?! Are you serious?" : "Чего?! Ты это серьёзно?"), 8, "15_surprised");
        pose("emotion thinking", 25, "16_thinking");
        pose("action eat", 12, "20_eat_hold", 12, "21_eat_bite1", 12, "22_eat_bite3", 4, "23_eat_savour");
        pose("action withdrawal", 20, "24_withdrawal");
        pose("action handstand", 35, "25_handstand");
        pose("action spin", 14, "26_spin");
        pose("action show_wings", 30, "27_show_wings");
        pose("action dance", 26, "28_dance");
        add(20, () -> cmd("companion hold"));
        add(40, () -> face(3.4, 90));
        pose("action eat", 25, "29_eat_side");
        add(50, () -> face(3.0, 200));
        add(30, () -> shot("30_back"));
        if (chat) {
            add(20, () -> cmd("companion follow")); // first person: he should fly into view to talk
            say(en ? "Hey Ryuk. Who are you?" : "Рюк, привет. Кто ты такой?", "40_chat_hello");
            say(en ? "Show me your wings!" : "Покажи свои крылья!", "41_chat_wings");
            say(en ? "Wait for me here, okay?" : "Подожди меня тут, ладно?", "42_chat_stay");
            deathNote();
        }
        add(40, () -> {
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /**
     * The story systems (env AICOMPANION_AUTOTEST=lore): Ryuk's game accepted, the eye deal, names and lifespans over
     * villagers, one of them written down by its true name, then Ryuk writes the owner's name, the Mu screen, and the
     * test world deleted on the way out (only this autotest world: it's the one being played).
     */
    private static void lore() {
        Minecraft mc = Minecraft.getInstance();
        add(60, () -> {
            for (String c : new String[]{"time set 6000", "weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobSpawning false", "tp @s 0 -60 0 0 0", "give @s aicompanion:death_note"}) {
                cmd(c);
            }
        });
        add(200, () -> ClientKira.openNote(InteractionHand.MAIN_HAND)); // Ryuk is here by now; the first opening offers his game
        add(20, () -> shot("60_pact"));
        add(2, () -> {
            Network.CHANNEL.sendToServer(new Network.Answer(Network.Choice.PACT, true));
            mc.setScreen(null);
        });
        add(40, () -> {
            for (int i = 0; i < 3; i++) { // three adults and a child face the player, plus a witch
                cmd("summon minecraft:villager " + (i * 2 - 2) + " -60 5 {NoAI:1b,Rotation:[180f,0f]}");
            }
            cmd("summon minecraft:villager 3 -60 4 {NoAI:1b,Age:-24000,Rotation:[200f,0f]}");
            cmd("summon minecraft:witch -3 -60 4 {NoAI:1b,Rotation:[160f,0f]}");
            cmd("companion kira eyes");
        });
        waitFor(600, () -> mc.screen instanceof EyeDealScreen, () -> shot("62_deal"));
        dealEyes();
        add(120, () -> shot("63_eyes_names")); // blindness and nausea are over by now
        add(2, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        add(10, () -> shot("64_red_eyes"));
        add(2, () -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        add(10, () -> {
            String victim = ClientKira.sightNames().stream().findFirst().orElse("nobody");
            AiCompanion.LOGGER.info("AUTOTEST writing the true name {} (seen: {})", victim, ClientKira.sightNames());
            Network.CHANNEL.sendToServer(new Network.WriteName(InteractionHand.MAIN_HAND, victim, ""));
        });
        add(30, () -> shot("65_written"));
        add(820, () -> {
            shot("66_villager_died");
            cmd("companion kira status");
        });
        add(20, () -> cmd("companion kira life 3")); // the owner's time runs out in 3 seconds: Ryuk writes the name
        add(200, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        add(10, () -> shot("67_ryuk_writes"));
        add(700, () -> shot("68_mu"));
        add(20, () -> {
            Path world = mc.getSingleplayerServer() == null ? null
                    : mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize();
            if (mc.screen instanceof MuScreen mu) {
                mu.leave();
            }
            AiCompanion.LOGGER.info("AUTOTEST after Mu: world folder {} exists: {}", world, world != null && Files.exists(world));
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /**
     * The investigation (env AICOMPANION_AUTOTEST=kira), without Ryuk's game so nothing is final: Lind L. Tailor's
     * broadcast and his death on air, an FBI agent who shows his badge when a zombie comes, L's headquarters with L
     * on his chair (and a word with him through the LLM), the notebook used in front of the Task Force (caught: the
     * notebook is taken), then the mafia hideout, where the boss reads the player's name off their face.
     */
    private static void kira() {
        Minecraft mc = Minecraft.getInstance();
        add(60, () -> {
            for (String c : new String[]{"time set 6000", "weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobSpawning false", "difficulty easy", "tp @s 0 -60 0 0 0", "give @s aicompanion:death_note"}) {
                cmd(c);
            }
        });
        add(200, () -> ClientKira.openNote(InteractionHand.MAIN_HAND));
        add(10, () -> {
            Network.CHANNEL.sendToServer(new Network.Answer(Network.Choice.PACT, false)); // no game: an arrest only costs the notebook
            mc.setScreen(null);
        });
        add(30, () -> cmd("companion kira eyes"));
        dealEyes();
        television(60); // L's special comes only to a set
        add(40, () -> cmd("companion kira broadcast"));
        tailorOnAir("71");
        add(20, () -> cmd("companion kira suspicion 20"));
        add(300, () -> {
            HumanEntity agent = person("agent");
            AiCompanion.LOGGER.info("AUTOTEST agent: {}", agent == null ? "none" : agent.position());
            if (agent != null) {
                lookAt(agent);
                cmd(String.format(Locale.ROOT, "summon minecraft:zombie %.1f %.1f %.1f", agent.getX() + 2, agent.getY(), agent.getZ()));
            }
        });
        add(20, () -> shot("73_agent"));
        add(80, () -> shot("74_badge"));
        add(20, () -> cmd("kill @e[type=minecraft:zombie]"));
        add(10, () -> cmd("companion kira hq")); // at the player + 30 blocks south: z 48
        add(220, () -> cmd("tp @s 0.5 -54 51.5 180 0")); // the main hall, facing L's chair and the monitors
        add(60, () -> shot("75_hq_hall"));
        add(2, () -> mc.player.connection.sendChat("Hello Ryuzaki. I'm just a student, but I want to help you catch Kira."));
        add(100, () -> shot("76_l_talk"));
        add(2, () -> cmd("tp @s 0.5 -52 92.5 180 -22"));
        add(30, () -> shot("77_hq_outside"));
        add(2, () -> cmd("tp @s 8.5 6 56.5 135 50"));
        add(30, () -> shot("78_helipad"));
        add(2, () -> cmd("tp @s 0.5 -54 49.5 180 0"));
        add(20, () -> Network.CHANNEL.sendToServer(new Network.WriteName(InteractionHand.MAIN_HAND, "Taro Matsui", ""))); // in front of them
        add(60, () -> {
            shot("79_caught");
            cmd("companion kira status");
        });
        add(20, () -> cmd("companion kira hideout"));
        add(240, () -> shot("80_hideout"));
        add(100, () -> shot("81_boss_reads"));
        add(900, () -> shot("82_after_boss"));
        add(20, () -> {
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /**
     * The side story and the owner's tricks (env AICOMPANION_AUTOTEST=world), without Ryuk's game: the mafia hideout,
     * Sidoh pulling the mask off, the boss's alias and then his real name written, Sidoh's notebook picked up and
     * handed back (the two lies marked among the rules); a torn page, the watch's needle, chips under L's cameras; the
     * dream after a golden apple (the realm, the King, a window's vision, the bone toss, Armonia, the hole back down); last, the
     * notebook itself in front of a camera.
     */
    private static void world() {
        Minecraft mc = Minecraft.getInstance();
        add(40, () -> {
            for (net.minecraft.world.entity.EntityType<?> t : List.of(AiCompanion.SIDOH.get(), AiCompanion.HUMAN.get(), AiCompanion.COMPANION.get())) {
                net.minecraft.world.entity.Entity e = t.create(mc.level);
                AiCompanion.LOGGER.info("AUTOTEST renderer for {}: {}", t, e == null ? "no entity" : mc.getEntityRenderDispatcher().getRenderer(e));
            }
        });
        add(20, () -> {
            for (String c : new String[]{"time set 6000", "weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobSpawning false", "difficulty easy", "tp @s 0 -60 0 0 0", "give @s aicompanion:death_note"}) {
                cmd(c);
            }
        });
        add(200, () -> ClientKira.openNote(InteractionHand.MAIN_HAND));
        add(10, () -> {
            Network.CHANNEL.sendToServer(new Network.Answer(Network.Choice.PACT, false));
            mc.setScreen(null);
        });
        add(30, () -> cmd("companion kira eyes"));
        dealEyes();
        // the hideout, a pumpkin over the face; the boss stands at the far end, facing the door
        add(120, () -> {
            cmd("item replace entity @s armor.head with minecraft:carved_pumpkin");
            cmd("companion kira sidoh");
        });
        add(20, () -> cmd("tp @s ~ ~ ~ 0 0")); // in the hall, back to him (he sits at the north end): he can't read a face he can't see
        add(40, () -> { // a glance at his face, too short for him to read ours (4 s) if the mask is gone
            HumanEntity boss = person("boss");
            AiCompanion.LOGGER.info("AUTOTEST the boss: {}", boss == null ? "missing" : boss.position());
            if (boss != null) {
                lookAt(boss); // wherever his post put him, and whoever walks the hall
            } else {
                cmd("tp @s ~ ~ ~ 0 0");
            }
        });
        add(25, () -> {
            shot("90_masked_boss");
            AiCompanion.LOGGER.info("AUTOTEST seen with the eyes: {}", ClientKira.sightNames());
            Network.CHANNEL.sendToServer(new Network.WriteName(InteractionHand.MAIN_HAND, "Jack Neylon", "")); // his alias: nothing happens
        });
        add(5, () -> cmd("tp @s ~ ~ ~ 0 0"));
        add(25, () -> Network.CHANNEL.sendToServer(new Network.WriteName(InteractionHand.MAIN_HAND, "Kal Snydar", "")));
        add(5, () -> cmd("tp @s ~ ~ ~9 0 0")); // out of the door (to the south), back to it
        add(400, () -> shot("91_mask_torn"));
        add(450, () -> {
            shot("92_boss_dead");
            ItemEntity drop = mc.level.getEntitiesOfClass(ItemEntity.class, mc.player.getBoundingBox().inflate(64),
                    i -> i.getItem().hasTag() && i.getItem().getTag().getBoolean("Sidoh")).stream().findFirst().orElse(null);
            AiCompanion.LOGGER.info("AUTOTEST Sidoh's notebook on the floor: {}", drop == null ? "none" : drop.position());
            if (drop != null) {
                cmd(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f 180 10", drop.getX(), drop.getY(), drop.getZ()));
            }
        });
        add(60, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        add(20, () -> shot("93_sidoh_visible"));
        add(2, () -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            select(slotOf(s -> s.hasTag() && s.getTag().getBoolean("Sidoh")));
            SidohEntity sidoh = mc.level.getEntitiesOfClass(SidohEntity.class, mc.player.getBoundingBox().inflate(16)).stream().findFirst().orElse(null);
            AiCompanion.LOGGER.info("AUTOTEST Sidoh: {}", sidoh == null ? "none" : sidoh.position());
            if (sidoh != null) {
                mc.gameMode.interact(mc.player, sidoh, InteractionHand.MAIN_HAND);
            }
        });
        add(30, () -> shot("94_sidoh_thanks"));
        add(10, () -> {
            select(0);
            DeathNoteScreen.open(InteractionHand.MAIN_HAND);
        });
        add(5, () -> ((DeathNoteScreen) mc.screen).turn(1));
        add(10, () -> {
            shot("95_lies");
            mc.setScreen(null);
        });
        // a room far from the gang, four villagers at the far wall, out of a page's sight
        add(10, () -> {
            cmd("fill 95 -61 -5 105 -56 5 minecraft:stone_bricks hollow");
            cmd("fill 97 -56 -3 103 -56 3 minecraft:glowstone"); // a lit ceiling, for the shots
            cmd("tp @s 100.5 -60 -3.5 0 0");
            for (int i = 0; i < 4; i++) {
                cmd("summon minecraft:villager " + (97.5 + i * 2) + " -60 3.5 {NoAI:1b,Rotation:[180f,0f]}");
            }
        });
        add(60, () -> {
            NAMES.clear();
            NAMES.addAll(ClientKira.sightNames());
            AiCompanion.LOGGER.info("AUTOTEST villagers: {}", NAMES);
            Network.CHANNEL.sendToServer(new Network.TearPage(InteractionHand.MAIN_HAND));
        });
        add(10, () -> {
            select(slotOf(s -> s.is(AiCompanion.PAGE.get())));
            write(0);
        });
        add(30, () -> {
            shot("100_page");
            cmd("gamemode survival");
            cmd("give @s aicompanion:kira_watch");
        });
        add(10, () -> select(slotOf(s -> s.is(AiCompanion.WATCH.get()))));
        for (int i = 0; i < 4; i++) { // four quick pulls of the crown
            add(1, () -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
        }
        add(5, () -> {
            shot("101_watch");
            mc.setScreen(null);
            AiCompanion.LOGGER.info("AUTOTEST health before the needle: {}", mc.player.getHealth());
            write(1);
        });
        add(30, () -> {
            AiCompanion.LOGGER.info("AUTOTEST health after the needle: {}", mc.player.getHealth());
            cmd("gamemode creative");
            cmd("companion kira cameras");
            cmd("give @s aicompanion:chips_with_page");
        });
        add(20, () -> {
            shot("102_cameras");
            select(slotOf(s -> s.is(AiCompanion.CHIPS_SECRET.get())));
            write(2); // a camera sees a snack
        });
        add(30, () -> cmd("companion kira status"));
        add(800, () -> shot("103_three_died"));
        // the dream: a golden apple for Ryuk, a bed, sleep
        add(10, () -> {
            cmd("time set 18000");
            cmd("setblock 100 -60 0 minecraft:red_bed[facing=south,part=foot]");
            cmd("setblock 100 -60 1 minecraft:red_bed[facing=south,part=head]");
            cmd("give @s minecraft:golden_apple");
            cmd("tp @s 100.5 -60 -1.5 0 0");
        });
        add(20, () -> {
            select(slotOf(s -> s.is(Items.GOLDEN_APPLE)));
            CompanionEntity ryuk = ryuk();
            cmd(String.format(Locale.ROOT, "tp @e[type=aicompanion:companion] %.1f -60 %.1f", 99.5, -1.5));
            AiCompanion.LOGGER.info("AUTOTEST feeding Ryuk at {}", ryuk.position());
        });
        add(2, () -> mc.gameMode.interact(mc.player, ryuk(), InteractionHand.MAIN_HAND));
        add(100, () -> {
            select(0);
            BlockPos bed = new BlockPos(100, -60, 0);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(bed), Direction.UP, bed, false));
        });
        add(100, () -> shot("110_realm"));
        add(60, () -> {
            cmd("tp @s 0.5 50 24.5 0 -28"); // under the ribs, looking up at the King
            AiCompanion.LOGGER.info("AUTOTEST in {}", mc.level.dimension().location());
        });
        add(40, () -> shot("111_king"));
        add(2, () -> cmd("tp @s -19.5 50 -25.5 90 30")); // by a window down to the humans
        add(20, () -> useOn(new BlockPos(-22, 50, -26)));
        add(20, () -> shot("112_window_vision"));
        add(2, () -> {
            cmd("give @s minecraft:apple 3");
            cmd("tp @s -15.5 50 1.5 180 12");
        });
        add(20, () -> {
            select(slotOf(s -> s.is(Items.APPLE)));
            useOn(new BlockPos(-16, 51, -4));
        });
        add(20, () -> shot("113_bone_toss"));
        add(2, () -> cmd("tp @s 13.5 51 -5.5 -90 -20")); // up the throne's first step
        add(20, () -> {
            RealmGod armonia = god("armonia");
            AiCompanion.LOGGER.info("AUTOTEST Armonia: {}", armonia == null ? "none" : armonia.position());
            if (armonia != null) {
                mc.gameMode.interact(mc.player, armonia, InteractionHand.MAIN_HAND);
            }
        });
        add(20, () -> shot("114_armonia"));
        add(2, () -> cmd("tp @s 0.5 51 -13.5 180 60")); // into the bright hole
        add(120, () -> {
            shot("115_woke");
            AiCompanion.LOGGER.info("AUTOTEST after the hole: {} at {}", mc.level.dimension().location(), mc.player.position());
        });
        // last: the notebook itself, in front of a camera
        add(20, () -> {
            cmd("time set 6000");
            cmd("tp @s 100.5 -60 -3.5 0 0");
        });
        add(20, () -> {
            select(0);
            write(3);
        });
        add(40, () -> {
            shot("116_camera_caught");
            cmd("companion kira status");
        });
        add(20, () -> {
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /**
     * Giving up the notebook (env AICOMPANION_AUTOTEST=renounce): with the notebook put away, ownership renounced: the
     * eyes go, Ryuk says goodbye and leaves, the suspicion cools fast; then a notebook touched again brings it all back.
     */
    private static void renounce() {
        Minecraft mc = Minecraft.getInstance();
        add(60, () -> {
            for (String c : new String[]{"time set 6000", "weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobSpawning false", "tp @s 0 -60 0 0 0", "give @s aicompanion:death_note"}) {
                cmd(c);
            }
        });
        add(200, () -> ClientKira.openNote(InteractionHand.MAIN_HAND));
        add(10, () -> {
            Network.CHANNEL.sendToServer(new Network.Answer(Network.Choice.PACT, false));
            mc.setScreen(null);
        });
        add(30, () -> cmd("companion kira eyes"));
        dealEyes();
        add(60, () -> cmd("companion kira suspicion 40"));
        add(20, () -> cmd("companion kira renounce")); // still carrying it: he refuses
        add(60, () -> {
            cmd("clear @s aicompanion:death_note"); // put away
            cmd("companion kira renounce");
        });
        add(40, () -> shot("120_goodbye"));
        add(160, () -> {
            shot("121_forgot");
            AiCompanion.LOGGER.info("AUTOTEST after renouncing: eyes {}, Ryuk here: {}", ClientKira.eyes,
                    !mc.level.getEntitiesOfClass(CompanionEntity.class, mc.player.getBoundingBox().inflate(48)).isEmpty());
            cmd("companion kira status");
        });
        add(700, () -> cmd("companion kira status")); // 35 s later: cooler
        add(20, () -> cmd("give @s aicompanion:death_note"));
        add(260, () -> {
            shot("122_remembered");
            AiCompanion.LOGGER.info("AUTOTEST after touching it again: Ryuk here: {}",
                    !mc.level.getEntitiesOfClass(CompanionEntity.class, mc.player.getBoundingBox().inflate(48)).isEmpty());
            cmd("companion kira status");
        });
        add(40, () -> {
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /** Ryuk's favours for apples (env AICOMPANION_AUTOTEST=favours): none without an apple, one per apple: L's cameras, a scouting flight. */
    private static void favours() {
        Minecraft mc = Minecraft.getInstance();
        add(60, () -> {
            for (String c : new String[]{"time set 6000", "weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobSpawning false", "tp @s 0 -60 0 0 0", "give @s aicompanion:death_note",
                    "fill -7 -61 -7 7 -56 7 minecraft:stone_bricks hollow", "fill -4 -56 -4 4 -56 4 minecraft:glowstone"}) {
                cmd(c);
            }
        });
        add(200, () -> ClientKira.openNote(InteractionHand.MAIN_HAND));
        add(10, () -> {
            Network.CHANNEL.sendToServer(new Network.Answer(Network.Choice.PACT, false));
            mc.setScreen(null);
        });
        add(40, () -> cmd("companion kira cameras"));
        add(20, () -> cmd("companion favour look_for_cameras")); // no apple yet
        add(60, () -> cmd("companion action eat"));
        add(20, () -> cmd("companion favour look_for_cameras"));
        add(30, () -> shot("130a_sweep_off")); // off round the walls
        add(90, () -> shot("130_sweep")); // back: the cameras glow
        add(20, () -> cmd("companion favour scout")); // that apple is spent
        add(60, () -> cmd("companion action eat"));
        add(20, () -> cmd("companion favour scout"));
        add(40, () -> shot("131_scouting"));
        add(160, () -> shot("132_scout_back"));
        add(80, () -> {
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /**
     * The house search (env AICOMPANION_AUTOTEST=search): the notebook under a drawer's false bottom (opened plainly it
     * shows a diary, with an ink sac the false bottom), the Task Force searches by the bed while the owner is away and
     * finds nothing, leaves cameras; a page left in a chest is found the second time; the drawer broken open burns.
     */
    private static void search() {
        Minecraft mc = Minecraft.getInstance();
        BlockPos drawer = new BlockPos(37, -60, 3);
        add(60, () -> {
            for (String c : new String[]{"time set 6000", "weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobSpawning false", "tp @s 0 -60 0 0 0", "give @s aicompanion:death_note"}) {
                cmd(c);
            }
        });
        add(200, () -> ClientKira.openNote(InteractionHand.MAIN_HAND));
        add(10, () -> {
            Network.CHANNEL.sendToServer(new Network.Answer(Network.Choice.PACT, false));
            mc.setScreen(null);
        });
        add(20, () -> {
            for (String c : new String[]{"fill 30 -61 -5 40 -56 5 minecraft:stone_bricks hollow", "fill 32 -56 -3 38 -56 3 minecraft:glowstone",
                    "fill 35 -60 -5 35 -59 -5 minecraft:air", "setblock 35 -60 0 minecraft:red_bed[facing=south,part=foot]",
                    "setblock 35 -60 1 minecraft:red_bed[facing=south,part=head]", "spawnpoint @s 35 -60 0",
                    "setblock 33 -60 3 minecraft:chest[facing=north]{Items:[{Slot:0b,id:\"minecraft:apple\",Count:3b}]}",
                    "setblock 37 -60 3 aicompanion:hidden_drawer[facing=north]{Items:[{Slot:4b,id:\"aicompanion:death_note\",Count:1b}]}",
                    "clear @s aicompanion:death_note", "give @s minecraft:ink_sac", "tp @s 36.5 -60 0.5 -18 25"}) {
                cmd(c);
            }
        });
        add(40, () -> {
            select(slotOf(ItemStack::isEmpty));
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(drawer), Direction.NORTH, drawer, false));
        });
        add(20, () -> {
            select(slotOf(s -> s.is(Items.INK_SAC)));
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(drawer), Direction.NORTH, drawer, false));
        });
        add(20, () -> {
            shot("140_false_bottom");
            mc.player.closeContainer();
        });
        add(10, () -> {
            cmd("tp @s 35.5 -60 40 180 0"); // far from home: they search while nobody's in
            cmd("companion kira search");
        });
        add(500, () -> {
            int cameras = 0;
            for (BlockPos p : BlockPos.betweenClosed(29, -61, -6, 41, -55, 6)) {
                cameras += mc.level.getBlockState(p).is(AiCompanion.CAMERA.get()) ? 1 : 0;
            }
            AiCompanion.LOGGER.info("AUTOTEST after the first search: {} cameras in the house", cameras);
            cmd("companion kira status");
            cmd("data modify block 33 -60 3 Items append value {Slot:5b,id:\"aicompanion:death_note_page\",Count:1b}");
        });
        add(20, () -> cmd("companion kira search"));
        add(500, () -> cmd("companion kira status"));
        add(10, () -> {
            cmd("tp @s 36.5 -60 0.5 -18 25");
            cmd("setblock 37 -60 3 minecraft:air destroy"); // forced open
        });
        add(30, () -> {
            boolean saved = !mc.level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(drawer).inflate(3),
                    i -> i.getItem().is(AiCompanion.DEATH_NOTE.get())).isEmpty();
            AiCompanion.LOGGER.info("AUTOTEST drawer broken: the notebook {}", saved ? "dropped out (WRONG)" : "burned");
            shot("141_burned");
        });
        add(20, () -> {
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /**
     * After L (env AICOMPANION_AUTOTEST=finale), without Ryuk's game: Near's SPK base and a word with him, Mello's hostage
     * traded for a notebook, Gevanni's copy of the notebook in the chest, the Yellow Box twice: names written in the copy
     * (nobody dies, Near has his proof: the arrest), then in a real notebook (they die: Kira wins); last, Kira's shrine
     * and its dawn offering.
     */
    private static void finale() {
        Minecraft mc = Minecraft.getInstance();
        add(60, () -> {
            for (String c : new String[]{"time set 6000", "weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobSpawning false", "tp @s 0 -60 0 0 0", "give @s aicompanion:death_note"}) {
                cmd(c);
            }
        });
        add(200, () -> ClientKira.openNote(InteractionHand.MAIN_HAND));
        add(10, () -> {
            Network.CHANNEL.sendToServer(new Network.Answer(Network.Choice.PACT, false));
            mc.setScreen(null);
        });
        add(30, () -> cmd("companion kira eyes"));
        dealEyes();
        add(40, () -> cmd("companion kira successors"));
        add(200, () -> {
            BlockPos spk = flag("spk");
            cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 180 10", spk.getX() + 0.5, spk.getY() + 1, spk.getZ() + 5.5));
        });
        add(60, () -> {
            shot("150_spk");
            mc.player.connection.sendChat("You must be Near. I'm just a student. Why would anyone suspect me?");
        });
        add(100, () -> shot("151_near_talk"));
        add(2, () -> cmd("summon minecraft:villager ~3 ~ ~ {Rotation:[90f,0f]}")); // the girl Mello's men will take
        add(40, () -> cmd("companion kira hostage"));
        add(60, () -> {
            BlockPos hall = flag("hideout");
            cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 180 15", hall.getX() - 1.5, hall.getY(), hall.getZ() + 9.5)); // back to the boss
            cmd("give @s aicompanion:death_note");
        });
        add(20, () -> cmd("kill @e[type=aicompanion:human,name=\"Jack Neylon\"]")); // the eye-holder, before he reads a face
        add(20, () -> cmd("tp @s ~ ~ ~ 0 10"));
        add(40, () -> shot("152_mello_hostage"));
        add(2, () -> {
            select(1);
            HumanEntity mello = person("mello");
            AiCompanion.LOGGER.info("AUTOTEST Mello: {}", mello == null ? "none" : mello.position());
            if (mello != null) {
                mc.gameMode.interact(mc.player, mello, InteractionHand.MAIN_HAND);
            }
        });
        add(40, () -> {
            shot("153_mello_deal");
            cmd("companion kira status");
        });
        // the copy: the notebook in a chest by the bed, Gevanni at work, the copy back in hand
        add(20, () -> {
            select(0);
            for (String c : new String[]{"tp @s 0.5 -60 0.5 0 0", "setblock 3 -60 0 minecraft:red_bed[facing=south,part=foot]",
                    "setblock 3 -60 1 minecraft:red_bed[facing=south,part=head]", "spawnpoint @s 3 -60 0", "setblock 2 -60 3 minecraft:chest",
                    "item replace block 2 -60 3 container.0 from entity @s weapon.mainhand", "item replace entity @s weapon.mainhand with minecraft:air"}) {
                cmd(c);
            }
        });
        add(20, () -> cmd("companion kira swap"));
        add(20, () -> {
            cmd("item replace entity @s weapon.mainhand from block 2 -60 3 container.0");
            cmd("setblock 2 -60 3 minecraft:air");
        });
        add(20, () -> AiCompanion.LOGGER.info("AUTOTEST notebook in hand after Gevanni: {} {}", mc.player.getMainHandItem(), mc.player.getMainHandItem().getTag()));
        // the Yellow Box, with the copy
        add(2, () -> cmd("companion kira suspicion 85"));
        add(20, () -> cmd("companion kira yellowbox"));
        yellowBox("16");
        add(900, () -> {
            shot("162_copy_exposed");
            cmd("companion kira status");
        });
        // again, with a real notebook, once the arresting detectives have gone (30 s)
        add(700, () -> {
            cmd("give @s aicompanion:death_note");
            cmd("companion kira suspicion 85");
        });
        add(20, () -> {
            select(slotOf(s -> s.is(AiCompanion.DEATH_NOTE.get())));
            cmd("companion kira yellowbox");
        });
        yellowBox("17");
        waitFor(4000, () -> flagOrNull("epilogue") != null, () -> AiCompanion.LOGGER.info("AUTOTEST finale: the ending's film is over"));
        add(40, () -> {
            shot("172_kira_wins");
            cmd("companion kira status");
        });
        // Kira's followers
        add(40, () -> cmd("companion kira glory 50"));
        add(60, () -> {
            BlockPos shrine = flag("shrine");
            AiCompanion.LOGGER.info("AUTOTEST shrine at {}", shrine);
            cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 180 25", shrine.getX() + 0.5, shrine.getY() + 1, shrine.getZ() + 4.5));
        });
        add(40, () -> {
            shot("180_shrine");
            cmd("time set 24100"); // a new dawn: the faithful come with apples
        });
        add(60, () -> {
            BlockPos shrine = flag("shrine");
            cmd(String.format(Locale.ROOT, "data get block %d %d %d Items", shrine.getX() + 1, shrine.getY() + 1, shrine.getZ()));
        });
        add(40, () -> {
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /** Into the warehouse (the showdown begins, the eyes take in the faces), out round the corner, every name written. */
    private static void yellowBox(String shots) {
        add(240, () -> {
            BlockPos box = flag("yellow_box");
            cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 180 5", box.getX() + 0.5, box.getY() + 1, box.getZ() + 5.5));
        });
        add(60, () -> {
            shot(shots + "0_yellow_box");
            NAMES.clear();
            NAMES.addAll(ClientKira.sightNames());
            AiCompanion.LOGGER.info("AUTOTEST in the warehouse: {}", NAMES);
            BlockPos box = flag("yellow_box");
            cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 90 0", box.getX() + 17.5, box.getY() + 1, box.getZ() + 0.5)); // outside the blind east wall
        });
        for (int i = 0; i < 6; i++) {
            int n = i;
            add(25, () -> write(n));
        }
        add(20, () -> shot(shots + "1_written"));
    }

    private static BlockPos village = BlockPos.ZERO, hall = BlockPos.ZERO, home = BlockPos.ZERO;

    /**
     * The whole story in survival on an ordinary world (env AICOMPANION_AUTOTEST=survival), the way a player meets it:
     * the notebook falls from the sky, Ryuk comes down, the eyes asked for in chat, raiders and a villager written
     * down in the nearest village, L's special on the TV and Tailor's death on air, an FBI agent's badge, L's tower and L, the mafia
     * hideout underground, Near and Mello, the hostage, Gevanni's copy, the Yellow Box, the shrine, the dream.
     * Teleports and the clock stand in for walking and waiting; the few commands skip only what would take hours.
     */
    private static void survival() {
        Minecraft mc = Minecraft.getInstance();
        add(100, () -> {
            for (String c : new String[]{"gamerule doWeatherCycle false", "weather clear", "time set 1000", "item replace entity @s hotbar.8 with minecraft:iron_sword",
                    "item replace entity @s armor.head with minecraft:iron_helmet", "item replace entity @s armor.chest with minecraft:iron_chestplate",
                    "item replace entity @s armor.legs with minecraft:iron_leggings", "item replace entity @s armor.feet with minecraft:iron_boots",
                    "effect give @s minecraft:resistance 1000000 3 true", "effect give @s minecraft:saturation 1000000 0 true",
                    "effect give @s minecraft:regeneration 1000000 1 true"}) {
                cmd(c);
            }
            onServer(() -> ru.maksim.aicompanion.Kira.of(me()).flags.put("played", 145L)); // the first minutes of play, skipped
        });
        // the notebook falls out of the sky a few blocks ahead, glowing; picked up, Ryuk comes down after it
        waitFor(600, () -> fallenNote() != null, () -> AiCompanion.LOGGER.info("AUTOTEST the notebook falls: {}", fallenNote()));
        add(8, () -> shot("200a_note_falls"));
        waitFor(200, () -> fallenNote() != null && fallenNote().onGround(), () -> shot("200b_note_landed"));
        add(10, () -> {
            ItemEntity note = fallenNote();
            if (note != null) {
                cmd(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", note.getX(), note.getY(), note.getZ()));
            }
        });
        waitFor(600, () -> !mc.level.getEntitiesOfClass(CompanionEntity.class, mc.player.getBoundingBox().inflate(32)).isEmpty(),
                () -> AiCompanion.LOGGER.info("AUTOTEST Ryuk came down"));
        add(60, () -> {
            shot("200_ryuk");
            ClientKira.openNote(InteractionHand.MAIN_HAND); // the rules, not his game: nothing has died yet
        });
        add(20, () -> {
            AiCompanion.LOGGER.info("AUTOTEST notebook opened: {}, game offered before a kill: {}", mc.screen, ClientKira.pactPending);
            shot("200c_note_open");
            mc.setScreen(null);
        });
        // no set, no news: a minute in, Ryuk tells of the TV and the recipe is in the book; one put down and switched on
        waitFor(1800, () -> flagLong("tv_hint") != null, () -> AiCompanion.LOGGER.info("AUTOTEST tv hint, the recipe: {}", tvRecipe()));
        add(30, () -> shot("200d_tv_hint"));
        television(40);
        // the first news: a wanted man's name and face on air; his name in the notebook; his death on the news
        waitFor(1800, () -> !onServer(() -> ru.maksim.aicompanion.News.wanted(mc.getSingleplayerServer())).isEmpty(), () -> {
        });
        add(20, () -> {
            java.util.List<ru.maksim.aicompanion.News.Criminal> wanted = onServer(() -> ru.maksim.aicompanion.News.wanted(mc.getSingleplayerServer()));
            AiCompanion.LOGGER.info("AUTOTEST on the news: {}", wanted);
            shot("201a_news");
            if (!wanted.isEmpty()) {
                select(slotOf(s -> s.is(AiCompanion.DEATH_NOTE.get()))); // the notebook in hand, whatever the hotbar was on
                Network.CHANNEL.sendToServer(new Network.WriteName(InteractionHand.MAIN_HAND, wanted.get(0).name(), "choked on a fish bone at a banquet")); // off screen: anything goes
            }
        });
        waitFor(1000, () -> onServer(() -> ru.maksim.aicompanion.Kira.of(me()).kills) > 0, () -> {
            AiCompanion.LOGGER.info("AUTOTEST the news death counted: kills {}, on TV: {}", onServer(() -> ru.maksim.aicompanion.Kira.of(me()).kills),
                    HEARD.stream().filter(l -> l.startsWith("[TV]")).reduce((a, b) -> b).orElse("nothing"));
            shot("201b_news_death");
        });
        waitFor(600, () -> mc.screen instanceof PactScreen, () -> {
            AiCompanion.LOGGER.info("AUTOTEST Ryuk's game offered after the first death: {}", mc.screen instanceof PactScreen);
            shot("201c_pact");
            Network.CHANNEL.sendToServer(new Network.Answer(Network.Choice.PACT, false));
            mc.setScreen(null);
        });
        add(60, () -> mc.player.connection.sendChat("Ryuk, I want the shinigami eyes. Make the eye deal with me."));
        waitFor(400, () -> mc.screen instanceof EyeDealScreen, () -> {
            if (!(mc.screen instanceof EyeDealScreen)) {
                AiCompanion.LOGGER.warn("AUTOTEST Ryuk never put the deal: by command");
                cmd("companion kira eyes");
            }
        });
        dealEyes();
        // the nearest village: three raiders and one villager in the notebook
        add(100, () -> {
            BlockPos v = onServer(() -> overworld().findNearestMapStructure(net.minecraft.tags.StructureTags.VILLAGE, mc.player.blockPosition(), 100, false));
            village = v != null ? v : mc.player.blockPosition();
            AiCompanion.LOGGER.info("AUTOTEST nearest village {}", v);
            tp(surface(village.getX(), village.getZ() + 6), 180, 0);
        });
        add(100, () -> {
            for (int i = 0; i < 3; i++) {
                cmd(String.format(Locale.ROOT, "summon minecraft:pillager ^%d ^ ^6 {NoAI:1b,PersistenceRequired:1b,Rotation:[%.0ff,0f]}",
                        i * 2 - 2, mc.player.getYRot() + 180));
            }
        });
        add(60, () -> {
            shot("201_raiders");
            names(e -> e instanceof net.minecraft.world.entity.monster.Pillager);
        });
        writeNames(3);
        add(30, () -> names(e -> e instanceof net.minecraft.world.entity.npc.Villager v && !v.isBaby()));
        writeNames(1);
        add(900, () -> {
            shot("202_deaths");
            cmd("companion kira status");
            cmd("time add 24000"); // the next day: L answers with his broadcast
        });
        // L's special on every screen: Lind L. Tailor, his name written, his death on air, the real L
        tailorOnAir("203");
        add(20, () -> cmd("companion kira status"));
        // a day later an FBI agent tails the suspect, and shows his badge when a zombie comes; his name on a torn page
        add(20, () -> cmd("time add 24000"));
        waitFor(2400, () -> person("agent") != null, () -> AiCompanion.LOGGER.info("AUTOTEST agent: {}", person("agent")));
        add(20, () -> {
            HumanEntity agent = person("agent");
            if (agent != null) {
                lookAt(agent);
                cmd(String.format(Locale.ROOT, "summon minecraft:zombie %.1f %.1f %.1f", agent.getX() + 2, agent.getY(), agent.getZ()));
            }
        });
        add(60, () -> {
            shot("205_badge");
            Network.CHANNEL.sendToServer(new Network.TearPage(InteractionHand.MAIN_HAND));
            names(e -> e instanceof HumanEntity h && h.skin().startsWith("agent"));
        });
        add(20, () -> {
            select(slotOf(s -> s.is(AiCompanion.PAGE.get())));
            write(0);
        });
        add(20, () -> select(0));
        add(900, () -> {
            shot("206_agent_dead");
            cmd("companion kira status");
        });
        // L's tower (two days after the broadcast): a word with L, his face seen, his name written outside
        add(20, () -> cmd("time add 24000"));
        waitFor(3600, () -> flagOrNull("hq") != null, () -> {
            if (flagOrNull("hq") == null) {
                AiCompanion.LOGGER.warn("AUTOTEST no headquarters yet: pushing the case");
                cmd("companion kira suspicion 45");
            }
        });
        waitFor(600, () -> flagOrNull("hq") != null, () -> {
        });
        add(80, () -> cmd("companion favour show_way"));
        add(30, () -> shot("206c_way"));
        add(190, () -> tp(flag("hq").offset(0, 6, 3), 180, 0));
        add(60, () -> {
            shot("207_l");
            mc.player.connection.sendChat("Hello, Ryuzaki. I heard you wanted to see me.");
        });
        add(140, () -> {
            shot("208_l_talk");
            tp(surface(flag("hq").getX() + 13, flag("hq").getZ() + 13), 0, 0); // out of the tower
        });
        add(40, () -> Network.CHANNEL.sendToServer(new Network.WriteName(InteractionHand.MAIN_HAND, "L Lawliet", "")));
        add(900, () -> {
            shot("209_l_dead");
            cmd("companion kira status");
        });
        // the mafia hideout underground: one look at the gang, then all their names from outside
        waitFor(1200, () -> flagOrNull("hideout") != null, () -> {
        });
        add(300, () -> {
            BlockPos top = flag("hideout");
            hall = onServer(() -> ru.maksim.aicompanion.Mafia.hall(overworld(), top));
            tp(top.offset(0, 0, 6), 180, 5); // before the shack
        });
        add(40, () -> shot("209b_shack"));
        add(5, () -> tp(flag("hideout").offset(0, -7, -8), 180, 25)); // halfway down the tunnel
        add(40, () -> {
            shot("209c_tunnel");
            tp(hall.offset(0, 0, 12), 180, 0); // just inside the door, facing the hall
        });
        add(30, () -> {
            shot("210_hideout");
            names(e -> e instanceof HumanEntity h && (h.skin().startsWith("mafia") || h.skin().startsWith("boss")));
            tp(surface(flag("hideout").getX() + 6, flag("hideout").getZ() + 6), 0, 0);
        });
        writeNames(7);
        add(900, () -> tp(hall.offset(0, 0, 10), 180, 10));
        add(40, () -> {
            shot("211_hideout_after");
            ItemEntity drop = mc.level.getEntitiesOfClass(ItemEntity.class, mc.player.getBoundingBox().inflate(32),
                    i -> i.getItem().hasTag() && i.getItem().getTag().getBoolean("Sidoh")).stream().findFirst().orElse(null);
            AiCompanion.LOGGER.info("AUTOTEST Sidoh's notebook: {}", drop == null ? "none" : drop.position());
            if (drop != null) {
                cmd(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f 180 10", drop.getX(), drop.getY(), drop.getZ()));
            }
        });
        add(60, () -> shot("212_sidoh"));
        // the next day: Near and Mello
        add(20, () -> cmd("time add 24000"));
        waitFor(600, () -> flagOrNull("spk") != null, () -> {
        });
        add(300, () -> tp(flag("spk").offset(0, 1, 5), 180, 10));
        add(60, () -> {
            shot("213_near");
            mc.player.connection.sendChat("Near, I'm not Kira. Why are you watching me?");
        });
        add(140, () -> shot("214_near_talk"));
        // back in the village, another day: Mello's men take someone
        add(20, () -> tp(surface(village.getX(), village.getZ() + 6), 180, 0));
        add(100, () -> cmd("time add 24000"));
        waitFor(600, () -> flagOrNull("hostage") != null, () -> {
        });
        add(200, () -> tp(hall.offset(7, 0, 5), 180, 10)); // by Mello's sofa
        add(60, () -> {
            shot("215_hostage");
            select(slotOf(s -> s.hasTag() && s.getTag().getBoolean("Sidoh")));
            HumanEntity mello = person("mello");
            AiCompanion.LOGGER.info("AUTOTEST Mello: {}", mello == null ? "none" : mello.position());
            if (mello != null) {
                mc.gameMode.interact(mc.player, mello, InteractionHand.MAIN_HAND); // Sidoh's notebook for the hostage
            }
        });
        add(40, () -> {
            shot("216_traded");
            select(0);
            cmd("companion kira status");
        });
        // a home by the village with a decoy notebook in the chest; at night, while away, Gevanni comes
        add(20, () -> {
            home = surface(village.getX() + 24, village.getZ() + 24);
            cmd(String.format(Locale.ROOT, "setblock %d %d %d minecraft:red_bed[facing=south,part=foot]", home.getX(), home.getY(), home.getZ()));
            cmd(String.format(Locale.ROOT, "setblock %d %d %d minecraft:red_bed[facing=south,part=head]", home.getX(), home.getY(), home.getZ() + 1));
            cmd(String.format(Locale.ROOT, "setblock %d %d %d minecraft:chest", home.getX() + 2, home.getY(), home.getZ()));
            cmd(String.format(Locale.ROOT, "spawnpoint @s %d %d %d", home.getX(), home.getY(), home.getZ()));
            cmd("give @s aicompanion:death_note");
        });
        add(20, () -> {
            for (int i = 1; i < 9; i++) {
                if (mc.player.getInventory().getItem(i).is(AiCompanion.DEATH_NOTE.get())) {
                    select(i);
                    break;
                }
            }
            cmd(String.format(Locale.ROOT, "item replace block %d %d %d container.0 from entity @s weapon.mainhand", home.getX() + 2, home.getY(), home.getZ()));
            cmd("item replace entity @s weapon.mainhand with minecraft:air");
        });
        add(20, () -> {
            select(0);
            tp(surface(home.getX() + 40, home.getZ()), 0, 0);
            cmd("time set 14000");
        });
        add(100, () -> cmd(String.format(Locale.ROOT, "data get block %d %d %d Items", home.getX() + 2, home.getY(), home.getZ())));
        add(20, () -> cmd("time set 23500"));
        // Near's case is nearly complete (the clock stands in for his weeks of work): the Yellow Box at dawn
        add(40, () -> {
            cmd("time add 24000");
            cmd("companion kira suspicion 85");
        });
        waitFor(600, () -> flagOrNull("meeting") != null, () -> {
        });
        add(300, () -> {
            long meeting = flagLong("meeting"), day = onServer(() -> ru.maksim.aicompanion.Kira.clock(mc.getSingleplayerServer()));
            if (meeting > day) {
                cmd("time add " + (meeting - day));
            }
        });
        add(40, () -> tp(flag("yellow_box").offset(0, 1, 5), 180, 5));
        add(60, () -> {
            shot("217_yellow_box");
            names(e -> e instanceof HumanEntity);
            tp(flag("yellow_box").offset(17, 1, 0), 90, 0); // outside the blind east wall
        });
        writeNames(6);
        waitFor(4000, () -> flagOrNull("epilogue") != null, () -> AiCompanion.LOGGER.info("AUTOTEST survival: the ending's film is over"));
        add(40, () -> shot("218_kira_wins"));
        add(200, () -> {
            shot("219_after");
            cmd("companion kira status");
        });
        // Kira's shrine and its offerings at dawn
        waitFor(400, () -> flagOrNull("shrine") != null, () -> {
        });
        add(100, () -> tp(flag("shrine").offset(0, 1, 4), 180, 25));
        add(60, () -> {
            shot("220_shrine");
            long day = mc.level.getDayTime();
            cmd("time add " + (24000 - day % 24000 + 100)); // the next dawn
        });
        add(60, () -> {
            BlockPos s = flag("shrine");
            cmd(String.format(Locale.ROOT, "data get block %d %d %d Items", s.getX() + 1, s.getY() + 1, s.getZ()));
        });
        // a golden apple for Ryuk and a night in the bed: his world in a dream
        add(40, () -> {
            cmd("give @s minecraft:golden_apple");
            tp(home.offset(1, 0, -1), 0, 0);
        });
        add(20, () -> {
            select(slotOf(s -> s.is(Items.GOLDEN_APPLE)));
            cmd(String.format(Locale.ROOT, "tp @e[type=aicompanion:companion] %.1f %d %.1f", home.getX() + 2.5, home.getY(), home.getZ() - 0.5));
        });
        add(2, () -> mc.gameMode.interact(mc.player, ryuk(), InteractionHand.MAIN_HAND));
        add(100, () -> {
            select(0);
            cmd("time set 14000");
        });
        add(20, () -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(home), Direction.UP, home, false)));
        waitFor(600, () -> mc.level.dimension().location().getPath().equals("shinigami_realm"), () -> {
        });
        add(100, () -> {
            shot("221_realm");
            cmd("tp @s 0.5 51 -13.5 180 60"); // into the bright hole
        });
        add(160, () -> {
            shot("222_woke");
            AiCompanion.LOGGER.info("AUTOTEST after the dream: {}", mc.level.dimension().location());
            cmd("companion kira status");
        });
        add(40, () -> {
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /**
     * The people's stances (env AICOMPANION_AUTOTEST=poses): the gallery (/companion kira gallery) shot from the front
     * and the side, person by person, then the two walking up and down.
     */
    private static void poses() {
        Minecraft mc = Minecraft.getInstance();
        String[] who = {"chair", "desk", "perch", "floor", "sofa", "behind", "crossed"};
        add(60, () -> {
            cmd("gamerule doDaylightCycle false");
            cmd("time set noon");
            cmd("weather clear");
            cmd("tp @s 0.5 -60 0.5 0 0");
            mc.options.hideGui = true;
        });
        add(20, () -> cmd("companion kira gallery"));
        add(60, () -> cmd("tp @s -3.5 -58.5 -4.5 0 12"));
        add(30, () -> shot("p00_row"));
        for (int i = 0; i < who.length; i++) {
            double x = -13 + i * 3 + 0.5;
            String name = who[i];
            add(10, () -> cmd(String.format(Locale.ROOT, "tp @s %.1f -60 1.2 0 10", x)));
            add(25, () -> shot("p" + name + "_front"));
            add(5, () -> cmd(String.format(Locale.ROOT, "tp @s %.1f -60 2.2 -35 8", x - 1.6)));
            add(25, () -> shot("p" + name + "_three"));
            add(5, () -> cmd(String.format(Locale.ROOT, "tp @s %.1f -60 4.6 -90 6", x - 2.6)));
            add(25, () -> shot("p" + name + "_side"));
        }
        add(10, () -> cmd("tp @s 19.5 -59 6.5 90 10")); // from the side, out of talking range
        for (int i = 0; i < 16; i++) {
            int k = i;
            add(15, () -> shot("pwalk" + k));
        }
        add(20, () -> mc.stop());
    }

    /**
     * The story's places (env AICOMPANION_AUTOTEST=buildings), each built and walked through with the camera: L's tower
     * (outside, the lobby, the stairs, the hall from three sides and the gallery, the upper floors, the roof), the
     * gang's hall, the SPK, the Yellow Box.
     */
    private static void buildings() {
        Minecraft mc = Minecraft.getInstance();
        add(60, () -> {
            cmd("gamerule doDaylightCycle false");
            cmd("gamerule doMobSpawning false");
            cmd("time set noon");
            cmd("weather clear");
            cmd("tp @s 0.5 -60 0.5 0 0");
            mc.options.hideGui = true;
        });
        add(20, () -> cmd("companion kira hq"));
        // x, y, z from the place's flag, yaw, pitch, name
        Object[][] hq = {{18, 14, 30, 145, -8, "b_hq_out"}, {0, 1, 7, 180, 5, "b_hq_lobby"}, {-5, 1, -4, -45, 5, "b_hq_lobby2"},
                {4, 1, 2, 0, -25, "b_hq_stairs"}, {5, 4, 6, 180, -20, "b_hq_landing"}, {0, 6, 4, 180, 0, "b_hq_hall"},
                {-3, 11, 6, 180, 28, "b_hq_gallery"}, {5, 6, -1, 90, 5, "b_hq_hall_side"}, {-3, 6, 3, 0, 5, "b_hq_lounge"},
                {5, 6, 0, 135, 5, "b_hq_hall_ne"}, {0, 16, 5, 180, 5, "b_hq_war"}, {0, 21, 2, 180, 5, "b_hq_quarters"},
                {-3, 21, 0, 0, 5, "b_hq_quarters2"}, {0, 26, 2, 180, 5, "b_hq_watari"}, {0, 31, 6, 180, 25, "b_hq_roof"}};
        tour("hq", hq, 260);
        add(20, () -> cmd("tp @s 0.5 -60 0.5 0 0"));
        add(20, () -> cmd("companion kira hideout"));
        Object[][] hide = {{0, 2, 22, 180, 5, "b_hide_out"}, {0, 0, 12, 180, 5, "b_hide_in"}, {8, 0, 1, 45, 5, "b_hide_ne"},
                {-3, 0, 10, 140, 10, "b_hide_cards"}, {3, 2, 3, -30, 10, "b_hide_boss"}, {-5, 0, 13, -135, 5, "b_hide_cell"}};
        tour("hideout", hide, 200);
        add(20, () -> cmd("companion kira successors"));
        Object[][] spk = {{0, 3, 20, 180, 5, "b_spk_out"}, {0, 1, 6, 180, 8, "b_spk_in"}, {8, 1, 6, 145, 8, "b_spk_ne"},
                {-8, 1, -6, -45, 8, "b_spk_sw"}};
        tour("spk", spk, 300);
        add(20, () -> cmd("companion kira yellowbox"));
        Object[][] box = {{0, 3, 20, 180, 5, "b_box_out"}, {0, 1, 6, 180, 5, "b_box_in"}, {-9, 1, 6, 150, 5, "b_box_corner"}};
        tour("yellow_box", box, 300);
        add(20, () -> mc.stop());
    }

    /** Waits {@code wait} ticks for the place to be built, then a shot from each spot (relative to the place's flag). */
    private static void tour(String flag, Object[][] spots, int wait) {
        add(wait, () -> AiCompanion.LOGGER.info("AUTOTEST {} at {}", flag, flagOrNull(flag)));
        for (Object[] q : spots) {
            add(5, () -> {
                BlockPos at = flag(flag);
                cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f %d %d", at.getX() + (int) q[0] + 0.5, at.getY() + (int) q[1], at.getZ() + (int) q[2] + 0.5,
                        (int) q[3], (int) q[4]));
            });
            add(40, () -> shot((String) q[5]));
        }
    }

    /**
     * Ryuk waits for the floor (env AICOMPANION_AUTOTEST=talk): a line, a story gesture and his game's offer all come
     * while a news card is up; none of them may start (no mouth, no chat line, no laugh, no screen) until the card is
     * read; then the line is said, the gesture made, and the offer opens after his line, in that order.
     */
    private static void talkTest() {
        Minecraft mc = Minecraft.getInstance();
        int[] saidBefore = new int[1];
        add(60, () -> {
            for (String c : new String[]{"time set 6000", "weather clear", "gamerule doDaylightCycle false", "gamerule doMobSpawning false",
                    "tp @s 0 -60 0 0 0", "give @s aicompanion:death_note"}) {
                cmd(c);
            }
        });
        waitFor(600, () -> ryukNear() != null, () -> { });
        add(200, () -> cmd("companion stay")); // he has come down and said hello by now
        add(60, () -> {
            KeyMapping.click(StoryHud.NEXT.getKey()); // whatever card his arrival brought: read
        });
        add(60, () -> KeyMapping.click(StoryHud.NEXT.getKey()));
        add(80, () -> {
            saidBefore[0] = RyukLines.said();
            StoryHud.show(new Network.Broadcast(Network.Broadcast.NEWS, Component.translatable("aicompanion.card.wanted"),
                    Component.translatable("aicompanion.tv.wanted", "Kenji Arai", Component.translatable("aicompanion.crime3")),
                    Network.face("mafia_1"), Network.Broadcast.NOWHERE));
        });
        add(10, () -> {
            cmd("companion say happy Хе-хе, вот это новости.");
            onServer(() -> {
                CompanionEntity r = CompanionEntity.of(me());
                if (r != null) {
                    r.reactLater("cackle");
                }
                return 0;
            });
            ClientKira.offer(Network.Choice.PACT);
        });
        add(100, () -> {
            CompanionEntity r = ryukNear();
            boolean early = RyukLines.said() != saidBefore[0] || r != null && (r.isTalking() || r.getAction().equals("cackle")) || mc.screen instanceof PactScreen;
            if (early) {
                AiCompanion.LOGGER.error("AUTOTEST talk: Ryuk went ahead of the unread card: said {}, talking {}, action {}, screen {}",
                        RyukLines.said() - saidBefore[0], r != null && r.isTalking(), r == null ? "-" : r.getAction(), mc.screen);
            } else {
                AiCompanion.LOGGER.info("AUTOTEST talk: the card unread, Ryuk waits: no line, no mouth, no laugh, no offer");
            }
            shot("tk1_card_up");
        });
        add(5, () -> KeyMapping.click(StoryHud.NEXT.getKey())); // read
        waitFor(200, () -> RyukLines.said() > saidBefore[0], () -> {
            CompanionEntity r = ryukNear();
            AiCompanion.LOGGER.info("AUTOTEST talk: card read, the line said ({}), offer open already: {}", RyukLines.said() - saidBefore[0],
                    mc.screen instanceof PactScreen);
            if (mc.screen instanceof PactScreen) {
                AiCompanion.LOGGER.error("AUTOTEST talk: the offer opened over his line");
            }
        });
        waitFor(60, () -> ryukNear() != null && ryukNear().isTalking(), () -> AiCompanion.LOGGER.info("AUTOTEST talk: his mouth goes now: {}",
                ryukNear() != null && ryukNear().isTalking()));
        add(2, () -> AiCompanion.LOGGER.info("AUTOTEST talk: gesture state {}", onServer(() -> CompanionEntity.of(me()) == null ? "-" : CompanionEntity.of(me()).gestureState())));
        waitFor(100, () -> ryukNear() != null && ryukNear().getAction().equals("cackle"), () -> AiCompanion.LOGGER.info("AUTOTEST talk: the laugh now: {} ({})",
                ryukNear() != null ? ryukNear().getAction() : "-", onServer(() -> CompanionEntity.of(me()) == null ? "-" : CompanionEntity.of(me()).gestureState())));
        add(20, () -> shot("tk2_line_said"));
        waitFor(400, () -> mc.screen instanceof PactScreen, () -> {
            AiCompanion.LOGGER.info("AUTOTEST talk: the offer after his line: {}", mc.screen instanceof PactScreen);
            shot("tk3_offer");
            mc.setScreen(null);
        });
        // while his human reads, what happens (and what they say to him) waits: one reply for it all, after
        int[] mark = new int[1];
        add(200, () -> {
            mark[0] = RyukLines.said();
            StoryHud.show(new Network.Broadcast(Network.Broadcast.NEWS, Component.translatable("aicompanion.card.wanted"),
                    Component.translatable("aicompanion.tv.wanted", "Kenji Arai", Component.translatable("aicompanion.crime3")),
                    Network.face("mafia_1"), Network.Broadcast.NOWHERE));
        });
        add(40, () -> onServer(() -> {
            CompanionEntity r = CompanionEntity.of(me());
            r.remark("a criminal died on the news", "story", 0);
            r.remark("your human wrote a name", "note", 0);
            r.remark("L's estimate went up", "story", 0);
            return 0;
        }));
        add(10, () -> mc.player.connection.sendChat("Ryuk, what was that?"));
        add(80, () -> quiet(mark[0], "a card", 3, true));
        for (int i = 0; i < 4; i++) { // read: this card and whatever came after it (a minute in, the TV's chapter)
            add(20, () -> KeyMapping.click(StoryHud.NEXT.getKey()));
        }
        oneReply(mark, "the card");
        // the notebook open: the same
        add(20, () -> {
            mark[0] = RyukLines.said();
            ClientKira.openNote(InteractionHand.MAIN_HAND);
        });
        add(40, () -> onServer(() -> {
            CompanionEntity r = CompanionEntity.of(me());
            r.remark("your human wrote another name", "note", 0);
            r.remark("a criminal died on the news", "story", 0);
            return 0;
        }));
        add(80, () -> {
            quiet(mark[0], "the notebook", 2, false);
            shot("tk4_notebook_quiet");
            mc.setScreen(null);
        });
        oneReply(mark, "the notebook");
        add(20, () -> {
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /** Something is being read: not a line of his said meanwhile, the events (and the words) kept on the server. */
    private static void quiet(int said, String what, int events, boolean words) {
        String state = onServer(() -> CompanionEntity.of(me()) == null ? "-" : CompanionEntity.of(me()).gestureState());
        boolean silent = RyukLines.said() == said, kept = state.matches(".*held=[" + events + "-4].*") && state.contains("words=" + words);
        AiCompanion.LOGGER.info("AUTOTEST talk: reading {}: Ryuk silent {}, all kept {} ({})", what, silent, kept, state);
        if (!silent || !kept) {
            AiCompanion.LOGGER.error("AUTOTEST talk: Ryuk didn't wait for {} to be read: said {}, {}", what, RyukLines.said() - said, state);
        }
    }

    /** Done reading: his reply comes, and it's one, however much waited. */
    private static void oneReply(int[] said, String what) {
        waitFor(300, () -> RyukLines.said() > said[0], () -> { });
        add(200, () -> {
            int n = RyukLines.said() - said[0];
            Minecraft mc = Minecraft.getInstance();
            AiCompanion.LOGGER.info("AUTOTEST talk: after {}: replies {} (client: free {}, story floor {}, screen {}, film {}; server: {})", what, n,
                    RyukLines.free(mc), StoryHud.floorFree(mc), mc.screen, Cinema.now(),
                    onServer(() -> CompanionEntity.of(me()) == null ? "-" : CompanionEntity.of(me()).gestureState()));
            if (n != 1) {
                AiCompanion.LOGGER.error("AUTOTEST talk: after {} {} replies instead of one", what, n);
            }
            shot("tk5_after_" + what.replace(' ', '_'));
        });
    }

    @Nullable
    private static CompanionEntity ryukNear() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null || mc.player == null ? null : mc.level.getEntitiesOfClass(CompanionEntity.class, mc.player.getBoundingBox().inflate(64))
                .stream().findFirst().orElse(null);
    }

    /**
     * Ryuk's first arrival as a film (env AICOMPANION_AUTOTEST=arrival), at night: the held breath (the view narrowing,
     * ash), lightning, the landing right in front with his wings out, his name over him, the film over, then his first
     * words and the chapter's card.
     */
    private static void arrivalTest() {
        Minecraft mc = Minecraft.getInstance();
        add(60, () -> {
            for (String c : new String[]{"time set 18000", "weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobSpawning false", "gamerule sendCommandFeedback false", "tp @s 0 -60 0 0 0", "give @s aicompanion:death_note"}) {
                cmd(c);
            }
        });
        waitFor(600, () -> Cinema.now() != null, () -> AiCompanion.LOGGER.info("AUTOTEST arrival: the film {} with {}", Cinema.now(), Soundtrack.now()));
        add(30, () -> shot("ar1_breath"));
        add(36, () -> shot("ar2_lightning"));
        add(9, () -> {
            CompanionEntity r = ryukNear();
            AiCompanion.LOGGER.info("AUTOTEST arrival: landing: Ryuk {} at {} blocks, in front {}, action {}", r != null,
                    r == null ? -1 : String.format(Locale.ROOT, "%.1f", r.distanceTo(mc.player)),
                    r != null && mc.player.getLookAngle().dot(r.position().subtract(mc.player.position()).normalize()) > 0.7,
                    r == null ? "-" : r.getAction());
            shot("ar3_landing");
        });
        add(30, () -> shot("ar4_title"));
        add(70, () -> shot("ar5_title_late"));
        waitFor(260, () -> Cinema.now() == null, () -> AiCompanion.LOGGER.info("AUTOTEST arrival: the film over, films seen {}", Cinema.seen()));
        add(50, () -> {
            AiCompanion.LOGGER.info("AUTOTEST arrival: after the film the chapter's card holds the floor: {}, his words wait: {}",
                    !StoryHud.floorFree(mc), RyukLines.said() == 0);
            shot("ar6_card");
        });
        add(5, () -> KeyMapping.click(StoryHud.NEXT.getKey())); // read
        waitFor(400, () -> RyukLines.said() > 0, () -> AiCompanion.LOGGER.info("AUTOTEST arrival: his first words after the card: {}", RyukLines.said()));
        add(30, () -> shot("ar6_after"));
        // the set in the pocket: never put down, never switched on, and the news comes all the same (News.viewer)
        add(10, () -> cmd("give @s aicompanion:television"));
        waitFor(200, () -> flagLong("news") != null, () -> AiCompanion.LOGGER.info("AUTOTEST pocket tv: the first edition with the set in the pocket: {}, ever switched on {}",
                flagLong("news") != null, flagLong("tv") != null));
        add(40, () -> shot("ar7_pocket_news"));
        add(5, () -> KeyMapping.click(StoryHud.NEXT.getKey()));
        add(5, () -> select(slotOf(st -> st.is(AiCompanion.TELEVISION_ITEM.get()))));
        add(5, () -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND)); // in the hand, a click into the air: what's on now
        add(20, () -> AiCompanion.LOGGER.info("AUTOTEST pocket tv: a click in the hand switched it on: {}", flagLong("tv") != null));
        add(20, () -> {
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /** The eye deal's screen, once it has the floor (after any card, film or line of Ryuk's): made, and closed. */
    private static void dealEyes() {
        Minecraft mc = Minecraft.getInstance();
        waitFor(600, () -> mc.screen instanceof EyeDealScreen, () -> {
            Network.CHANNEL.sendToServer(new Network.Answer(Network.Choice.EYE_DEAL, true));
            mc.setScreen(null);
        });
    }

    /**
     * The story-test build (env AICOMPANION_AUTOTEST=storytest with AICOMPANION_STORYTEST=1): its key pressed through the
     * whole story in a world with a village, by the bed a chest (a notebook goes in after the search: Gevanni's). Each press waits for the
     * moment to play out (a film, a show, a card read, an offer answered); the chat's line for it is logged, the screen
     * shot; at the end, every moment must have left its mark.
     */
    private static void storyTestRun() {
        Minecraft mc = Minecraft.getInstance();
        add(60, () -> {
            AiCompanion.LOGGER.info("AUTOTEST storytest: the test build on: {}", ru.maksim.aicompanion.StoryTest.ON);
            if (!ru.maksim.aicompanion.StoryTest.ON) {
                AiCompanion.LOGGER.error("AUTOTEST storytest: run it with AICOMPANION_STORYTEST=1");
            }
            for (String c : new String[]{"time set 1000", "weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobSpawning false", "gamerule sendCommandFeedback false", "tp @s -838.5 72 -355.5 180 0",
                    "setblock -840 72 -360 minecraft:red_bed[facing=south,part=foot]", "setblock -840 72 -359 minecraft:red_bed[facing=south,part=head]",
                    "spawnpoint @s -840 72 -360", "setblock -837 72 -360 minecraft:chest"}) {
                cmd(c);
            }
        });
        int presses = ru.maksim.aicompanion.StoryTest.size() + 4; // a moment or two may ask for a second press (a chunk still loading)
        for (int i = 1; i <= presses; i++) {
            int k = i;
            waitFor(1500, () -> settled(mc), () -> { });
            add(10, () -> {
                if (onServer(() -> Kira.of(me()).flag("test_search") && !Kira.of(me()).flag("test_gevanni"))) { // the search is over: a notebook for Gevanni
                    cmd("item replace block -837 72 -360 container.0 with aicompanion:death_note");
                }
                HEARD.clear();
                KeyMapping.click(StorySkip.KEY.getKey());
            });
            add(120, () -> { });
            waitFor(1500, () -> settled(mc), () -> {
                AiCompanion.LOGGER.info("AUTOTEST storytest press {}: {}", k, HEARD.stream().filter(l -> l.contains("Story test") || l.startsWith("   "))
                        .toList());
                shot(String.format(Locale.ROOT, "st%02d", k));
            });
        }
        add(40, () -> {
            List<String> missing = onServer(() -> ru.maksim.aicompanion.StoryTest.missing(me()));
            if (missing.isEmpty()) {
                AiCompanion.LOGGER.info("AUTOTEST storytest: every moment left its mark ({})", ru.maksim.aicompanion.StoryTest.size());
            } else {
                AiCompanion.LOGGER.error("AUTOTEST storytest: moments without their mark: {}", missing);
            }
            AiCompanion.LOGGER.info("AUTOTEST storytest: {}", onServer(() -> { Kira.Case c = Kira.of(me()); return String.format(Locale.ROOT,
                    "kills %d, eyes %d, pact %s, suspicion %.0f, stage %d, sealed %s", c.kills, c.eyes, c.pact, c.suspicion, c.stage, c.sealed); }));
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /**
     * The ending's film (env AICOMPANION_AUTOTEST=epilogue), on the world with villages: Kira wins by command, then the
     * title, the camera at each village (a spectator there, a shrine and a crowd before it), Kira's address on the screen
     * (the reader moves it on), Ryuk's three last lines, the closing title; at the end the player back where it ended, in
     * the game mode they had.
     */
    private static void epilogueTest() {
        Minecraft mc = Minecraft.getInstance();
        Object[] before = new Object[2];
        add(60, () -> {
            for (String c : new String[]{"time set 6000", "weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobSpawning false", "gamerule sendCommandFeedback false", "tp @s -838.5 72 -355.5 180 0", "companion summon"}) {
                cmd(c);
            }
        });
        add(60, () -> {
            before[0] = mc.player.blockPosition();
            before[1] = mc.gameMode.getPlayerMode();
            cmd("companion kira newworld");
        });
        waitFor(200, () -> "epilogue".equals(Cinema.now()), () -> AiCompanion.LOGGER.info("AUTOTEST epilogue: the film {}", Cinema.now()));
        add(60, () -> shot("ep1_title"));
        int[] said = new int[1];
        for (int v = 1; v <= 3; v++) {
            int n = v;
            waitFor(400, () -> ("village." + n).equals(Cinema.now()) || mc.screen instanceof TvShowScreen, () -> { });
            add(90, () -> {
                if (!("village." + n).equals(Cinema.now())) {
                    return; // fewer villages around
                }
                long crowd = onServer(() -> overworld().getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, me().getBoundingBox().inflate(24),
                        e -> e.getTags().contains("aicompanion_worshipper")).size()); // tags live on the server only
                AiCompanion.LOGGER.info("AUTOTEST epilogue: village {}: at {} as {}, the crowd {} (the camera {} blocks from where it ended)", n,
                        mc.player.blockPosition(), mc.gameMode.getPlayerMode(), crowd, Math.round(Math.sqrt(mc.player.blockPosition().distSqr((BlockPos) before[0]))));
                shot("ep2_village" + n);
            });
        }
        waitFor(600, () -> mc.screen instanceof TvShowScreen, () -> {
            said[0] = RyukLines.said();
            AiCompanion.LOGGER.info("AUTOTEST epilogue: Kira's address on the screen: {}, back where it ended: {} as {}", mc.screen instanceof TvShowScreen,
                    mc.player.blockPosition(), mc.gameMode.getPlayerMode());
        });
        add(100, () -> shot("ep3_address"));
        waitFor(1200, () -> RyukLines.said() >= said[0] + 3, () -> AiCompanion.LOGGER.info("AUTOTEST epilogue: Ryuk's last words: {}", RyukLines.said() - said[0]));
        add(10, () -> shot("ep4_last_words"));
        waitFor(600, () -> "end".equals(Cinema.now()), () -> AiCompanion.LOGGER.info("AUTOTEST epilogue: the closing film {}", Cinema.now()));
        add(60, () -> shot("ep5_end"));
        waitFor(600, () -> flagOrNull("epilogue") != null, () -> {
            boolean home = mc.player.blockPosition().distSqr((BlockPos) before[0]) < 9, mode = mc.gameMode.getPlayerMode() == before[1];
            AiCompanion.LOGGER.info("AUTOTEST epilogue: over: back where it ended {}, game mode as before {} ({})", home, mode, mc.gameMode.getPlayerMode());
            if (!home || !mode) {
                AiCompanion.LOGGER.error("AUTOTEST epilogue: the player wasn't put back: at {}, {}", mc.player.blockPosition(), mc.gameMode.getPlayerMode());
            }
        });
        add(40, () -> {
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    private static boolean cleanHud; // the trailer's UI shots: our cards and screens, none of the vanilla HUD

    @SubscribeEvent
    public static void hud(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre event) {
        if (cleanHud && "minecraft".equals(event.getOverlay().id().getNamespace())) {
            event.setCanceled(true);
        }
    }

    /**
     * Footage for the trailer (env AICOMPANION_AUTOTEST=trailer), full screen, no HUD: the notebook falling at sunset,
     * Ryuk's arrival at night, the two of them on a hill, his poses against the sky, the mod's screens (the notebook, the
     * news, the shows, his game, the eyes, the dice), names over villagers' heads, L's tower at night, the realm, and a
     * green studio where he poses for cut-outs. Shots: run/screenshots/autotest_tr_*.png.
     */
    private static void trailerShoot() {
        Minecraft mc = Minecraft.getInstance();
        add(40, () -> {
            if (!mc.getWindow().isFullscreen()) {
                mc.getWindow().toggleFullScreen();
            }
            mc.options.hideGui = true;
            mc.options.fov().set(70);
            for (String c : new String[]{"weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
                    "gamerule sendCommandFeedback false", "time set 12600", "tp @s -840.5 72 -352.5 90 -12"}) {
                cmd(c);
            }
        });
        add(80, () -> AiCompanion.LOGGER.info("AUTOTEST trailer: screen {}x{}", mc.getWindow().getWidth(), mc.getWindow().getHeight()));
        // 1. the notebook falls out of the sunset
        add(10, () -> cmd("summon minecraft:item -849.5 94 -352.5 {Item:{id:\"aicompanion:death_note\",Count:1b},Glowing:1b,PickupDelay:32767,Age:-32768}"));
        for (int i = 0; i < 16; i++) {
            int n = i;
            add(3, () -> shot(String.format(Locale.ROOT, "tr_fall_%02d", n)));
        }
        add(60, () -> cmd("tp @s -846.5 72.4 -352.5 90 32"));
        add(20, () -> shot("tr_fall_ground"));
        add(5, () -> cmd("tp @s -853.5 72.2 -355.5 -60 22"));
        add(20, () -> shot("tr_fall_ground2"));
        add(5, () -> cmd("kill @e[type=minecraft:item]"));
        // 2. night: the notebook in hand, and he comes
        add(10, () -> {
            cmd("time set 18000");
            cmd("tp @s -840.5 72 -352.5 90 -4");
            cmd("give @s aicompanion:death_note");
        });
        waitFor(600, () -> "ryuk".equals(Cinema.now()), () -> AiCompanion.LOGGER.info("AUTOTEST trailer: the arrival"));
        int[] arrival = {30, 14, 4, 16, 4, 4, 4, 2, 2, 4, 6, 10, 20, 30};
        for (int i = 0; i < arrival.length; i++) {
            int n = i;
            add(arrival[i], () -> shot(String.format(Locale.ROOT, "tr_arrive_%02d", n)));
        }
        waitFor(400, () -> Cinema.now() == null, () -> { });
        add(60, () -> {
            for (int i = 0; i < 4; i++) {
                KeyMapping.click(StoryHud.NEXT.getKey());
            }
        });
        // 3. the two of them at golden hour: from behind, and from the front (he's always over your shoulder)
        add(40, () -> {
            cmd("time set 11800");
            cmd("tp @s -832.5 73 -372.5 135 2");
        });
        add(100, () -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
        add(30, () -> shot("tr_duo_back"));
        add(5, () -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
        add(30, () -> shot("tr_duo_front"));
        add(5, () -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            cmd("tp @s -832.5 73 -372.5 -45 0");
        });
        add(60, () -> shot("tr_duo_view"));
        // 4. his poses against the sunset sky, from a little below
        add(10, () -> {
            cmd("time set 12300");
            cmd("companion stay");
        });
        add(40, () -> cmd("companion hold"));
        add(10, () -> frame(4.2, 0, 0.6));
        String[] poses = {"cackle", "show_wings", "juggle", "rub_hands", "point", "peek", "wave", "dice_shake"};
        for (String p : poses) {
            add(30, () -> cmd("companion action " + p));
            add(12, () -> shot("tr_pose_" + p + "_1"));
            add(14, () -> shot("tr_pose_" + p + "_2"));
            add(40, () -> { });
        }
        add(10, () -> frame(3.0, 35, 0.2));
        add(30, () -> cmd("companion action cackle"));
        add(14, () -> shot("tr_pose_cackle_side"));
        add(60, () -> cmd("companion follow"));
        // 5. the mod's screens, with no HUD under them
        add(40, () -> {
            cmd("time set 6000");
            cmd("tp @s -840.5 72 -352.5 90 0");
            for (String name : new String[]{"Kenji Arai", "Taro Weber", "Lucas Kato"}) {
                Network.CHANNEL.sendToServer(new Network.WriteName(InteractionHand.MAIN_HAND, name, ""));
            }
        });
        add(40, () -> {
            select(slotOf(st -> st.is(AiCompanion.DEATH_NOTE.get())));
            ClientKira.openNote(InteractionHand.MAIN_HAND);
        });
        add(30, () -> shot("tr_ui_note"));
        add(5, () -> {
            mc.setScreen(null);
            cleanHud = true;
            mc.options.hideGui = false;
            StoryHud.show(new Network.Broadcast(Network.Broadcast.NEWS, Component.translatable("aicompanion.card.wanted"),
                    Component.translatable("aicompanion.tv.wanted", "Kenji Arai", Component.translatable("aicompanion.crime3")),
                    Network.face("mafia_1"), Network.Broadcast.NOWHERE));
        });
        add(40, () -> shot("tr_ui_news"));
        add(5, () -> KeyMapping.click(StoryHud.NEXT.getKey()));
        add(30, () -> mc.setScreen(new TvShowScreen(new Network.Broadcast(Network.Broadcast.SHOW, Component.translatable("aicompanion.card.special"),
                Component.empty(), Network.face("tailor_0"), Network.Broadcast.NOWHERE))));
        add(70, () -> shot("tr_ui_tailor"));
        add(5, () -> mc.setScreen(new TvShowScreen(new Network.Broadcast(Network.Broadcast.SHOW, Component.translatable("aicompanion.card.special"),
                Component.empty(), "L", Network.Broadcast.NOWHERE))));
        add(60, () -> shot("tr_ui_L"));
        add(5, () -> mc.setScreen(new PactScreen(null)));
        add(30, () -> shot("tr_ui_pact"));
        add(5, () -> mc.setScreen(new EyeDealScreen()));
        add(30, () -> shot("tr_ui_eyedeal"));
        add(5, () -> {
            mc.setScreen(null);
            cmd("companion dice");
        });
        add(60, () -> shot("tr_ui_dice"));
        add(5, () -> mc.setScreen(null));
        // 6. the eyes: names and years over the villagers' heads
        add(20, () -> cmd("companion kira eyes"));
        waitFor(600, () -> mc.screen instanceof EyeDealScreen, () -> {
            Network.CHANNEL.sendToServer(new Network.Answer(Network.Choice.EYE_DEAL, true));
            mc.setScreen(null);
        });
        add(60, () -> {
            if (mc.screen instanceof EyeLookScreen) {
                mc.setScreen(null);
            }
            cmd("time set 11000");
            BlockPos ground = surface(-864, -378);
            cmd(String.format(Locale.ROOT, "tp @s -864.5 %d -370.5 180 8", ground.getY() + 1));
        });
        add(80, () -> shot("tr_eyes_village"));
        add(5, () -> {
            BlockPos ground = surface(-870, -390);
            cmd(String.format(Locale.ROOT, "tp @s -870.5 %d -382.5 160 6", ground.getY() + 1));
        });
        add(60, () -> shot("tr_eyes_village2"));
        add(5, () -> {
            cleanHud = false;
            mc.options.hideGui = true;
        });
        // 7. L's tower by night: from afar, then inside, behind him at his screens
        add(10, () -> {
            cmd("time set 18000");
            cmd("tp @s -840.5 72 -352.5 0 0");
            cmd("companion kira hq");
        });
        add(200, () -> {
            BlockPos hq = flag("hq");
            cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 180 -14", hq.getX() + 0.5, hq.getY() + 6, hq.getZ() + 38.5));
        });
        add(80, () -> shot("tr_hq_far"));
        add(5, () -> {
            BlockPos hq = flag("hq");
            cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 160 -24", hq.getX() - 6.5, hq.getY() + 2, hq.getZ() + 20.5));
        });
        add(60, () -> shot("tr_hq_low"));
        add(5, () -> {
            BlockPos hq = flag("hq");
            cmd(String.format(Locale.ROOT, "tp @s %.1f %.1f %.1f 180 12", hq.getX() + 0.5, hq.getY() + 7.2, hq.getZ() + 3.5));
        });
        add(60, () -> shot("tr_hq_L"));
        // 8. the realm, its films already seen (they'd take the camera)
        add(10, () -> onServer(() -> {
            for (String f : new String[]{"film_realm", "film_god.gamblers", "film_god.armonia", "film_god.king", "film_god.sidoh", "film_person.l"}) {
                Kira.of(me()).flags.put(f, 0L);
            }
            return 0;
        }));
        add(10, () -> cmd("companion kira dream"));
        waitFor(400, () -> mc.level.dimension() == ru.maksim.aicompanion.Realm.KEY, () -> { });
        add(200, () -> { });
        String[] realm = {"0.5 51 4.5 0 -6 tr_realm_road", "0.5 51 -3.5 0 -14 tr_realm_road2", "0.5 56 24.5 0 -28 tr_realm_king",
                "-44.5 51 -2.5 90 -12 tr_realm_note", "-16.5 52 3.5 180 10 tr_realm_gods", "16.5 52 2.5 180 -12 tr_realm_armonia",
                "20.5 51 14.5 -45 -12 tr_realm_hourglass", "0.5 51 12.5 0 -16 tr_realm_scythes", "0.5 52 22.5 0 -12 tr_realm_obelisks",
                "-41.5 51 -31.5 -80 5 tr_realm_sidoh", "4.5 58 -8.5 160 62 tr_realm_window", "-62.5 56 -42.5 180 26 tr_realm_circle"};
        for (String r : realm) {
            String[] q = r.split(" ");
            add(15, () -> cmd("tp @s " + q[0] + " " + q[1] + " " + q[2] + " " + q[3] + " " + q[4]));
            add(30, () -> shot(q[5]));
        }
        add(10, () -> cmd("companion kira wake"));
        waitFor(300, () -> mc.level.dimension() != ru.maksim.aicompanion.Realm.KEY, () -> { });
        // 9. the green studio: an open-topped box of lime, noon, no shadows; he poses for cut-outs
        add(40, () -> {
            cmd("time set 6000");
            for (String c : new String[]{"fill 2990 195 2990 3010 195 3010 minecraft:lime_concrete", "fill 2990 196 2990 3010 216 2990 minecraft:lime_concrete",
                    "fill 2990 196 3010 3010 216 3010 minecraft:lime_concrete", "fill 2990 196 2990 2990 216 3010 minecraft:lime_concrete",
                    "fill 3010 196 2990 3010 216 3010 minecraft:lime_concrete", "tp @s 3000.5 199 3008.5 180 0"}) {
                cmd(c);
            }
            mc.options.entityShadows().set(false);
            mc.options.fov().set(40);
        });
        add(60, () -> {
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
            cmd("companion stay");
            cmd("tp @e[type=aicompanion:companion] 3000.5 198.5 3000.5 0 0");
        });
        add(30, () -> cmd("companion hold"));
        add(20, () -> cmd("tp @s 3000.5 199 3008.5 180 0"));
        add(30, () -> shot("tr_gs_idle"));
        String[] studio = {"show_wings", "cackle", "juggle", "point", "rub_hands", "peek", "wave", "dice_shake", "backflip", "clap", "shrug", "bow"};
        for (String p : studio) {
            add(30, () -> cmd("companion action " + p));
            add(10, () -> shot("tr_gs_" + p + "_1"));
            add(12, () -> shot("tr_gs_" + p + "_2"));
            add(40, () -> { });
        }
        add(40, () -> {
            mc.options.fov().set(70);
            mc.options.entityShadows().set(true);
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    private static double fovOverride; // the trailer's long lenses: the options stop at 30

    @SubscribeEvent
    public static void fov(net.minecraftforge.client.event.ViewportEvent.ComputeFov event) {
        if (fovOverride > 0 && event.usedConfiguredFov()) {
            event.setFOV(fovOverride);
        }
    }

    /**
     * Moving footage for the trailer (env AICOMPANION_AUTOTEST=trailer2): clips of a frame every client tick, mostly from
     * a spectator's camera driven from here: the notebook falling out of the sunset and landing in the grass, Ryuk's
     * arrival (his own film), a walk with him talking, his moods against the sunset, L's tower at night, the realm, the
     * player's red eyes, the green studio for cut-outs, and a name being written. Frames: run/screenshots/autotest_tc_*.png.
     */
    private static void trailerClips() {
        Minecraft mc = Minecraft.getInstance();
        Vec3[] spot = new Vec3[1], aim = new Vec3[1], eye = new Vec3[1], r = new Vec3[1];
        add(40, () -> {
            if (!mc.getWindow().isFullscreen()) {
                mc.getWindow().toggleFullScreen();
            }
            mc.options.hideGui = true;
            mc.options.bobView().set(false);
            fovOverride = 50;
            for (String c : new String[]{"weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
                    "gamerule sendCommandFeedback false", "time set 12600", "gamemode spectator", "tp @s -843.5 74 -350.5 90 -45"}) {
                cmd(c);
            }
        });
        add(100, () -> {
            AiCompanion.LOGGER.info("AUTOTEST trailer2: screen {}x{}", mc.getWindow().getWidth(), mc.getWindow().getHeight());
            BlockPos g = surface(-850, -353);
            spot[0] = new Vec3(-849.5, g.getY(), -352.5); // where the notebook comes to rest
            eye[0] = new Vec3(-843.2, Math.max(g.getY(), surface(-844, -351).getY()) + 1.5, -350.2);
            aim[0] = spot[0].add(0, 10, 0);
            for (String plant : new String[]{"minecraft:grass", "minecraft:tall_grass"}) { // a clear view of where it lands
                cmd(String.format(Locale.ROOT, "fill -851 %d -354 -846 %d -350 air replace %s", g.getY(), g.getY() + 1, plant));
            }
        });
        // 1. out of the sunset sky: the camera low in the grass follows it down
        clip("fall", 72, i -> {
            if (i == 6) {
                summonNote(spot[0].add(0, 10, 0));
            }
            ItemEntity note = fallenNote();
            if (note != null) {
                aim[0] = aim[0].lerp(note.position().add(0, 0.2, 0), 0.35);
            }
            camAt(eye[0].add(-0.012 * i, 0, 0), aim[0]);
        });
        add(4, () -> cmd("kill @e[type=minecraft:item]"));
        // 2. it lands in the grass, right in front of the lens
        clip("land", 60, i -> {
            if (i == 8) {
                summonNote(spot[0].add(0, 4.5, 0));
            }
            camAt(spot[0].add(2.4 - 0.01 * i, 0.7, 1.3 - 0.005 * i), spot[0].add(-0.1, 0.12, 0));
        });
        // 3. night: the notebook in hand, and he comes (his own film: the camera is his)
        add(6, () -> {
            cmd("kill @e[type=minecraft:item]");
            cmd("time set 18000");
            cmd("gamemode creative");
            fovOverride = 0;
        });
        add(20, () -> cmd("tp @s -840.5 72 -352.5 90 -4"));
        add(40, () -> cmd("give @s aicompanion:death_note"));
        waitFor(600, () -> "ryuk".equals(Cinema.now()), () -> AiCompanion.LOGGER.info("AUTOTEST trailer2: the arrival"));
        clip("arrive", 240, i -> { });
        waitFor(400, () -> Cinema.now() == null, () -> { });
        add(160, () -> { }); // his first words, the chapter's card (the reader turns it)
        // 4. a walk with him at golden hour: he hangs in front of you, talking
        add(10, () -> {
            cmd("time set 11800");
            cmd(String.format(Locale.ROOT, "tp @s -864.5 %d -366.5 180 4", surface(-865, -367).getY()));
            mc.player.getAbilities().flying = false;
            mc.player.onUpdateAbilities();
        });
        add(60, () -> cmd("companion say happy Boredom. So I let a notebook slip, just to see who'd pick it up. Heh. You did."));
        add(20, () -> { });
        clip("talk", 110, i -> {
            if (i == 6) {
                mc.options.keyUp.setDown(true);
            } else if (i == 90) {
                mc.options.keyUp.setDown(false);
            }
        });
        // 5. his moods against the sunset: he holds still facing east, the camera goes round him
        add(30, () -> {
            cmd("time set 12500");
            cmd(String.format(Locale.ROOT, "tp @s -834.5 %d -371.5 -90 0", surface(-835, -372).getY()));
        });
        add(60, () -> {
            cmd(String.format(Locale.ROOT, "tp @e[type=aicompanion:companion] -828.5 %.1f -371.5 -90 0", surface(-829, -372).getY() + 0.4));
            cmd("companion hold");
        });
        add(20, () -> {
            cmd("gamemode spectator");
            fovOverride = 50;
            r[0] = ryuk().position();
            AiCompanion.LOGGER.info("AUTOTEST trailer2: Ryuk at {}", r[0]);
        });
        mood("cackle", 64, "cackle", 4.6, 3.8, -18, -8, 1.3, 1.4, 1.75, r);
        mood("wings", 64, "show_wings", 5.0, 4.3, 24, 14, 0.5, 0.6, 1.9, r);
        mood("juggle", 56, "juggle", 3.7, 3.3, 4, -4, 1.6, 1.6, 1.8, r);
        mood("eat", 70, "eat", 2.9, 2.6, 14, 8, 1.9, 1.9, 2.0, r);
        mood("rub", 48, "rub_hands", 3.0, 2.7, -10, -4, 1.8, 1.8, 1.95, r);
        mood("dance", 56, "dance", 4.4, 4.4, 0, 0, 1.3, 1.3, 1.5, r);
        mood("handstand", 60, "handstand", 4.6, 4.6, 30, 30, 1.0, 1.0, 1.0, r);
        mood("spin", 44, "spin", 4.2, 4.2, -30, -30, 1.4, 1.4, 1.7, r);
        mood("orbit", 100, null, 4.2, 4.2, -70, 40, 1.5, 1.9, 1.7, r);
        // 6. L's tower at night: from afar, from below, and inside behind him
        add(10, () -> {
            cmd("time set 18000");
            cmd("gamemode creative");
            cmd("companion follow");
            cmd("tp @s -840.5 72 -352.5 0 0");
            cmd("companion kira hq");
        });
        add(200, () -> {
            cmd("gamemode spectator");
            fovOverride = 60;
        });
        java.util.function.Supplier<Vec3> hq = () -> Vec3.atLowerCornerOf(flag("hq"));
        dolly("hq_far", 110, hq, new double[]{0.5, 7.6, 42.5, 180, -12}, new double[]{0.5, 9.1, 35.5, 180, -15});
        dolly("hq_low", 90, hq, new double[]{-7.5, 3.6, 23.5, 158, -22}, new double[]{-5.5, 4.1, 20.5, 162, -26});
        dolly("hq_L", 90, hq, new double[]{0.5, 8.8, 5.0, 180, 11}, new double[]{0.5, 8.8, 3.2, 180, 13});
        // 7. the realm, its films already seen (they'd take the camera)
        add(10, () -> {
            cmd("gamemode creative");
            onServer(() -> {
                for (String f : new String[]{"film_realm", "film_god.gamblers", "film_god.armonia", "film_god.king", "film_god.sidoh", "film_person.l"}) {
                    Kira.of(me()).flags.put(f, 0L);
                }
                return 0;
            });
        });
        add(10, () -> cmd("companion kira dream"));
        waitFor(400, () -> mc.level.dimension() == ru.maksim.aicompanion.Realm.KEY, () -> { });
        add(200, () -> cmd("gamemode spectator"));
        add(20, () -> { });
        java.util.function.Supplier<Vec3> realm = () -> Vec3.ZERO;
        dolly("realm_road", 140, realm, new double[]{0.5, 52.62, -9.5, 0, -8}, new double[]{0.5, 53.2, 11.5, 0, -13});
        dolly("realm_king", 90, realm, new double[]{0.5, 53.6, 14.5, 0, -10}, new double[]{0.5, 57.6, 21.5, 0, -28});
        dolly("realm_note", 80, realm, new double[]{-36.5, 52.62, -2.5, 90, -10}, new double[]{-42.5, 52.62, -2.5, 90, -13});
        dolly("realm_gods", 70, realm, new double[]{-14.5, 53.62, 3.5, 180, 10}, new double[]{-18.5, 53.62, 3.5, 180, 10});
        dolly("realm_glass", 70, realm, new double[]{22.5, 52.62, 16.5, -45, -12}, new double[]{18.5, 52.62, 16.5, -30, -12});
        dolly("realm_sidoh", 60, realm, new double[]{-41.5, 52.62, -31.5, -80, 5}, new double[]{-41.5, 52.62, -31.5, -80, 5});
        add(10, () -> {
            cmd("gamemode creative");
            cmd("companion kira wake");
        });
        waitFor(300, () -> mc.level.dimension() != ru.maksim.aicompanion.Realm.KEY, () -> { });
        // 8. the eyes: the deal, then your own eyes burning red in the dark (third person from the front, a long lens, turning)
        add(40, () -> {
            cmd("time set 18000");
            cmd("companion kira eyes");
        });
        waitFor(600, () -> mc.screen instanceof EyeDealScreen, () -> {
            Network.CHANNEL.sendToServer(new Network.Answer(Network.Choice.EYE_DEAL, true));
            mc.setScreen(null);
        });
        add(60, () -> {
            if (mc.screen instanceof EyeLookScreen) {
                mc.setScreen(null);
            }
            cmd(String.format(Locale.ROOT, "tp @s -845.5 %d -359.5 90 0", surface(-846, -360).getY()));
            mc.player.getAbilities().flying = false;
            mc.player.onUpdateAbilities();
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            fovOverride = 22;
        });
        add(60, () -> { });
        clip("eyes", 90, i -> {
            mc.player.setYRot(70 + 40 * i / 89F);
            mc.player.setXRot(4);
        });
        // 9. the green studio for cut-outs: lime all round, noon, no shadows, a long lens
        add(10, () -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            cmd("time set 6000");
            cmd("forceload add 2990 2990 3010 3010"); // filled before anyone's there: "that position is not loaded" otherwise
        });
        add(60, () -> {
            for (String c : new String[]{"fill 2990 195 2990 3010 195 3010 minecraft:lime_concrete", "fill 2990 196 2990 3010 216 2990 minecraft:lime_concrete",
                    "fill 2990 196 3010 3010 216 3010 minecraft:lime_concrete", "fill 2990 196 2990 2990 216 3010 minecraft:lime_concrete",
                    "fill 3010 196 2990 3010 216 3010 minecraft:lime_concrete", "gamemode spectator", "tp @s 3000.5 198.2 3005.7 180 0"}) {
                cmd(c);
            }
            mc.options.entityShadows().set(false);
            fovOverride = 40;
        });
        add(60, () -> { // he stays where he's put: tp and hold in one go (an old "stay" would call him home)
            cmd("tp @e[type=aicompanion:companion] 3000.5 198.5 3000.5 0 0");
            cmd("companion hold");
        });
        add(40, () -> AiCompanion.LOGGER.info("AUTOTEST trailer2: studio, Ryuk at {}", ryuk().position()));
        String[][] studio = {{"gs_idle", "1", ""}, {"gs_cackle", "50", "cackle"}, {"gs_wings", "50", "show_wings"}, {"gs_rub", "40", "rub_hands"},
                {"gs_point", "30", "point"}, {"gs_juggle", "40", "juggle"}};
        for (String[] s : studio) {
            clip(s[0], Integer.parseInt(s[1]), i -> {
                if (i == 3 && !s[2].isEmpty()) {
                    cmd("companion action " + s[2]);
                }
                cam(new Vec3(3000.5, 199.85, 3005.7), 180, 0);
            });
            add(50, () -> { });
        }
        // 10. a name, written (last: what it sets going isn't for the camera)
        add(10, () -> {
            cmd("forceload remove 2990 2990 3010 3010");
            cmd("gamemode creative");
            cmd("time set 11000");
            cmd("tp @s -840.5 72 -352.5 90 4");
            mc.options.entityShadows().set(true);
            fovOverride = 0;
        });
        add(60, () -> {
            select(slotOf(st -> st.is(AiCompanion.DEATH_NOTE.get())));
            ClientKira.openNote(InteractionHand.MAIN_HAND);
        });
        add(20, () -> { });
        String written = "Kenji Arai";
        clip("write", 76, i -> {
            int c = (i - 10) / 2;
            if (mc.screen != null && i >= 10 && i % 2 == 0 && c < written.length()) {
                mc.screen.charTyped(written.charAt(c), 0);
            } else if (mc.screen != null && i == 46) {
                mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
            }
        });
        add(10, () -> {
            mc.setScreen(null);
            mc.options.bobView().set(true);
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /** A clip of {@code frames} frames, one a client tick: {@code at} sets frame i up (the camera, a cue), the next tick takes it. */
    private static void clip(String name, int frames, java.util.function.IntConsumer at) {
        add(0, () -> at.accept(0));
        for (int i = 0; i < frames; i++) {
            int n = i;
            add(0, () -> {
                shot(String.format(Locale.ROOT, "tc_%s_%03d", name, n));
                if (n + 1 < frames) {
                    at.accept(n + 1);
                }
            });
        }
    }

    /** One of Ryuk's moods on film: {@code action} at frame 4, the camera going from (d0, a0, u0) to (d1, a1, u1) round him. */
    private static void mood(String name, int frames, @Nullable String action, double d0, double d1, double a0, double a1, double u0, double u1,
                             double look, Vec3[] r) {
        clip(name, frames, i -> {
            if (action != null && i == 4) {
                cmd("companion action " + action);
            }
            double k = i / (frames - 1.0), a = Math.toRadians(-90 + Mth.lerp(k, a0, a1)), d = Mth.lerp(k, d0, d1); // he faces east
            camAt(r[0].add(-Math.sin(a) * d, Mth.lerp(k, u0, u1), Math.cos(a) * d), r[0].add(0, look, 0));
        });
        add(50, () -> { });
    }

    /** A straight move of the eyes from {@code a} to {@code b} (x, y, z, yaw, pitch, the place relative to {@code origin}). */
    private static void dolly(String name, int frames, java.util.function.Supplier<Vec3> origin, double[] a, double[] b) {
        Vec3[] o = new Vec3[1];
        clip(name, frames, i -> {
            if (i == 0) {
                o[0] = origin.get();
            }
            double k = i / (frames - 1.0);
            cam(o[0].add(Mth.lerp(k, a[0], b[0]), Mth.lerp(k, a[1], b[1]), Mth.lerp(k, a[2], b[2])), (float) Mth.lerp(k, a[3], b[3]),
                    (float) Mth.lerp(k, a[4], b[4]));
        });
        add(20, () -> { });
    }

    /** The camera: the player (a spectator, flying) with its eyes at {@code eye}; set on the client, the server takes the move. */
    private static void cam(Vec3 eye, float yaw, float pitch) {
        var p = Minecraft.getInstance().player;
        p.getAbilities().flying = true;
        p.setDeltaMovement(Vec3.ZERO);
        p.moveTo(eye.x, eye.y - p.getEyeHeight(), eye.z, yaw, pitch);
    }

    private static void camAt(Vec3 eye, Vec3 at) {
        Vec3 d = at.subtract(eye);
        cam(eye, (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F, (float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG));
    }

    /** The notebook as an item at {@code p}, still, spinning the way dropped things do, nobody to pick it up. */
    private static void summonNote(Vec3 p) {
        cmd(String.format(Locale.ROOT, "summon minecraft:item %.2f %.2f %.2f {Item:{id:\"aicompanion:death_note\",Count:1b},PickupDelay:32767,Motion:[0.0,0.0,0.0]}",
                p.x, p.y, p.z));
    }

    /**
     * The trailer's player shots again, in a real player's skin and cape (env AICOMPANION_AUTOTEST=trailer3, the skin from
     * AICOMPANION_SKIN...): night, his arrival, the eyes taken; then from behind (the cape, the notebook in hand, Ryuk
     * hanging in front, facing you) and the face with the eyes burning. Frames: autotest_tc_eyes_back_*, autotest_tc_eyes_*.
     */
    private static void trailerPlayer() {
        Minecraft mc = Minecraft.getInstance();
        add(40, () -> {
            if (!mc.getWindow().isFullscreen()) {
                mc.getWindow().toggleFullScreen();
            }
            mc.options.hideGui = true;
            mc.options.bobView().set(false);
            for (String c : new String[]{"weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
                    "gamerule sendCommandFeedback false", "time set 18000", "tp @s -840.5 72 -352.5 90 -4"}) {
                cmd(c);
            }
        });
        add(60, AutoTest::wearSkin);
        add(20, () -> cmd("give @s aicompanion:death_note"));
        waitFor(600, () -> "ryuk".equals(Cinema.now()), () -> AiCompanion.LOGGER.info("AUTOTEST trailer3: the arrival"));
        waitFor(400, () -> Cinema.now() == null, () -> { });
        add(160, () -> { }); // his first words, the chapter's card (the reader turns it)
        add(10, () -> cmd("companion kira eyes"));
        waitFor(600, () -> mc.screen instanceof EyeDealScreen, () -> {
            Network.CHANNEL.sendToServer(new Network.Answer(Network.Choice.EYE_DEAL, true));
            mc.setScreen(null);
        });
        add(60, () -> {
            if (mc.screen instanceof EyeLookScreen) {
                mc.setScreen(null);
            }
            cmd(String.format(Locale.ROOT, "tp @s -845.5 %d -359.5 90 -6", surface(-846, -360).getY()));
            mc.player.getAbilities().flying = false;
            mc.player.onUpdateAbilities();
            select(slotOf(st -> st.is(AiCompanion.DEATH_NOTE.get())));
        });
        add(40, () -> { // Ryuk a few steps ahead and to the side (not behind the head the camera looks past), facing the player
            cmd(String.format(Locale.ROOT, "tp @e[type=aicompanion:companion] -849.1 %.2f -357.7 -116.6 0", mc.player.getY() + 0.5));
            cmd("companion hold");
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            fovOverride = 46;
        });
        add(40, () -> AiCompanion.LOGGER.info("AUTOTEST trailer3: skin {}, cape {}, model {}", mc.player.getSkinTextureLocation(),
                mc.player.getCloakTextureLocation(), mc.player.getModelName()));
        clip("eyes_back", 80, i -> {
            mc.player.setYRot(84 + 12 * i / 79F);
            mc.player.setXRot(-8 + 2 * i / 79F);
        });
        add(10, () -> { // he looms behind the player's shoulder, a little lower than his head
            cmd(String.format(Locale.ROOT, "tp @e[type=aicompanion:companion] %.2f %.2f %.2f 90 0", mc.player.getX() + 1.6, mc.player.getY() - 0.6,
                    mc.player.getZ() + 0.6));
            cmd("companion hold");
        });
        add(40, () -> {
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            fovOverride = 22;
        });
        add(40, () -> { });
        clip("eyes", 90, i -> {
            mc.player.setYRot(70 + 40 * i / 89F);
            mc.player.setXRot(4);
        });
        add(10, () -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.bobView().set(true);
            fovOverride = 0;
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    // ------------------------------------------------------------------ the shorts: portrait frames from the biggest screen

    /** The window onto the monitor with the most pixels (fullscreen there next, at its own resolution). */
    private static void toBiggestMonitor() {
        var w = Minecraft.getInstance().getWindow();
        if (w.isFullscreen()) {
            w.toggleFullScreen();
        }
        org.lwjgl.PointerBuffer ms = GLFW.glfwGetMonitors();
        long area = 0;
        int bx = 0, by = 0;
        for (int i = 0; ms != null && i < ms.limit(); i++) {
            long m = ms.get(i);
            org.lwjgl.glfw.GLFWVidMode v = GLFW.glfwGetVideoMode(m);
            if (v != null && (long) v.width() * v.height() > area) {
                area = (long) v.width() * v.height();
                int[] x = new int[1], y = new int[1];
                GLFW.glfwGetMonitorPos(m, x, y);
                bx = x[0];
                by = y[0];
            }
        }
        GLFW.glfwSetWindowPos(w.getWindow(), bx + 60, by + 60);
        AiCompanion.LOGGER.info("AUTOTEST shorts: to the monitor at {},{} ({} px)", bx, by, area);
    }

    private static boolean jpegReady;

    /**
     * Frame {@code name} as a JPEG (run/screenshots/autotest_{name}.jpg): the whole frame, or only the 9:16 column in its
     * middle (a short's frame, straight from the GPU: the rest is never read). Written on an IO worker.
     */
    private static void grab(String name, boolean column) {
        com.mojang.blaze3d.pipeline.RenderTarget t = Minecraft.getInstance().getMainRenderTarget();
        int w = column ? Math.min(t.width, Math.round(t.height * 9F / 16F)) : t.width;
        readJpeg(t.frameBufferId, (t.width - w) / 2, w, t.height, name);
    }

    private static com.mojang.blaze3d.pipeline.TextureTarget half; // the frame halved, on the GPU

    /**
     * The whole frame at half its size (a 4K screen's 3840x2160 into 1920x1080): a linear blit on the GPU averages each
     * 2x2 block, so the film gets supersampled frames, and the encoder a quarter of the pixels.
     */
    private static void grabHalf(String name) {
        com.mojang.blaze3d.pipeline.RenderTarget t = Minecraft.getInstance().getMainRenderTarget();
        int hw = t.width / 2, hh = t.height / 2;
        if (half == null || half.width != hw || half.height != hh) {
            if (half != null) {
                half.destroyBuffers();
            }
            half = new com.mojang.blaze3d.pipeline.TextureTarget(hw, hh, false, Minecraft.ON_OSX);
        }
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, t.frameBufferId);
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_DRAW_FRAMEBUFFER, half.frameBufferId);
        com.mojang.blaze3d.platform.GlStateManager._glBlitFrameBuffer(0, 0, t.width, t.height, 0, 0, hw, hh, org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT,
                org.lwjgl.opengl.GL11.GL_LINEAR);
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, 0);
        readJpeg(half.frameBufferId, 0, hw, hh, name);
    }

    /** Columns x0..x0+w of framebuffer {@code fbo}'s {@code fh} rows as run/screenshots/autotest_{name}.jpg, written on an IO worker. */
    private static void readJpeg(int fbo, int x0, int w, int fh, String name) {
        Minecraft mc = Minecraft.getInstance();
        if (!jpegReady) {
            javax.imageio.ImageIO.setUseCache(false);
            jpegReady = true;
        }
        java.nio.ByteBuffer px = org.lwjgl.system.MemoryUtil.memAlloc(w * fh * 4);
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, fbo);
        org.lwjgl.opengl.GL11.glPixelStorei(org.lwjgl.opengl.GL11.GL_PACK_ALIGNMENT, 1);
        org.lwjgl.opengl.GL11.glReadPixels(x0, 0, w, fh, org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, px);
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(org.lwjgl.opengl.GL30.GL_READ_FRAMEBUFFER, 0);
        Path file = mc.gameDirectory.toPath().resolve("screenshots").resolve("autotest_" + name + ".jpg");
        net.minecraft.Util.ioPool().execute(() -> {
            javax.imageio.ImageWriter jw = javax.imageio.ImageIO.getImageWritersByFormatName("jpg").next();
            try {
                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, fh, java.awt.image.BufferedImage.TYPE_INT_RGB);
                int[] row = new int[w];
                for (int y = 0; y < fh; y++) {
                    int base = (fh - 1 - y) * w * 4; // GL rows run bottom up
                    for (int x = 0; x < w; x++) {
                        int i = base + x * 4;
                        row[x] = (px.get(i) & 0xFF) << 16 | (px.get(i + 1) & 0xFF) << 8 | px.get(i + 2) & 0xFF;
                    }
                    img.setRGB(0, y, w, 1, row, 0, w);
                }
                javax.imageio.ImageWriteParam p = jw.getDefaultWriteParam();
                p.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                p.setCompressionQuality(0.93F);
                Files.createDirectories(file.getParent());
                try (javax.imageio.stream.ImageOutputStream out = javax.imageio.ImageIO.createImageOutputStream(file.toFile())) {
                    jw.setOutput(out);
                    jw.write(null, new javax.imageio.IIOImage(img, null, null), p);
                }
            } catch (IOException e) {
                AiCompanion.LOGGER.warn("AUTOTEST could not write {}", file, e);
            } finally {
                jw.dispose();
                org.lwjgl.system.MemoryUtil.memFree(px);
            }
        });
    }

    /** A short's clip: {@code frames} JPEGs, one a client tick (autotest_sv_{name}_{nnn}.jpg); {@code at} sets frame i up. */
    private static void vclip(String name, int frames, boolean column, java.util.function.IntConsumer at) {
        add(0, () -> at.accept(0));
        for (int i = 0; i < frames; i++) {
            int n = i;
            add(0, () -> {
                grab(String.format(Locale.ROOT, "sv_%s_%03d", name, n), column);
                if (n + 1 < frames) {
                    at.accept(n + 1);
                }
            });
        }
    }

    /** {@link #mood} for a short: the portrait column. */
    private static void vmood(String name, int frames, @Nullable String action, double d0, double d1, double a0, double a1, double u0, double u1,
                              double look, Vec3[] r) {
        vclip(name, frames, true, i -> {
            if (action != null && i == 4) {
                cmd(action.startsWith("say ") ? "companion " + action : "companion action " + action);
            }
            double k = i / (frames - 1.0), a = Math.toRadians(-90 + Mth.lerp(k, a0, a1)), d = Mth.lerp(k, d0, d1); // he faces east
            camAt(r[0].add(-Math.sin(a) * d, Mth.lerp(k, u0, u1), Math.cos(a) * d), r[0].add(0, look, 0));
        });
        add(50, () -> { });
    }

    /** {@link #dolly} for a short: the portrait column. */
    private static void vdolly(String name, int frames, java.util.function.Supplier<Vec3> origin, double[] a, double[] b) {
        Vec3[] o = new Vec3[1];
        vclip(name, frames, true, i -> {
            if (i == 0) {
                o[0] = origin.get();
            }
            double k = i / (frames - 1.0);
            cam(o[0].add(Mth.lerp(k, a[0], b[0]), Mth.lerp(k, a[1], b[1]), Mth.lerp(k, a[2], b[2])), (float) Mth.lerp(k, a[3], b[3]),
                    (float) Mth.lerp(k, a[4], b[4]));
        });
        add(20, () -> { });
    }

    /** The player's eyes turning a part of the way onto {@code at} (a camera operator's lag, not a snap). */
    private static void follow(Vec3 at, float k) {
        var p = Minecraft.getInstance().player;
        Vec3 d = at.subtract(p.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F, pitch = (float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
        p.setYRot(p.getYRot() + Mth.wrapDegrees(yaw - p.getYRot()) * k);
        p.setXRot(Mth.lerp(k, p.getXRot(), pitch));
    }

    /**
     * Portrait footage for the YouTube shorts (env AICOMPANION_AUTOTEST=shorts, the player in AICOMPANION_SKIN...): on the
     * biggest screen, fullscreen, the 9:16 column of every frame as a JPEG, a frame a tick (autotest_sv_{clip}_{nnn}.jpg),
     * whole frames for the wide screens (the dice table, the quest cards). The notebook out of the sunset, landing, picked
     * up; his arrival; you with your cape, your eyes; his moods; him talking; the dice; Sidoh's page and Sidoh; the realm;
     * L's tower.
     */
    private static void shortsShoot() {
        Minecraft mc = Minecraft.getInstance();
        Vec3[] spot = new Vec3[1], aim = new Vec3[1], eye = new Vec3[1], r = new Vec3[1];
        add(20, AutoTest::toBiggestMonitor);
        add(30, () -> mc.getWindow().toggleFullScreen());
        add(40, () -> {
            mc.options.hideGui = true;
            mc.options.bobView().set(false);
            fovOverride = 60;
            for (String c : new String[]{"weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
                    "gamerule sendCommandFeedback false", "time set 12600", "gamemode spectator", "tp @s -843.5 74 -350.5 90 -45"}) {
                cmd(c);
            }
        });
        add(60, () -> {
            wearSkin();
            AiCompanion.LOGGER.info("AUTOTEST shorts: framebuffer {}x{}", mc.getMainRenderTarget().width, mc.getMainRenderTarget().height);
            BlockPos g = surface(-850, -353);
            spot[0] = new Vec3(-849.5, g.getY(), -352.5); // where the notebook comes to rest
            eye[0] = new Vec3(-843.2, Math.max(g.getY(), surface(-844, -351).getY()) + 1.5, -350.2);
            aim[0] = spot[0].add(0, 10, 0);
            for (String plant : new String[]{"minecraft:grass", "minecraft:tall_grass"}) { // a clear view of where it lands, and a clear walk to it
                cmd(String.format(Locale.ROOT, "fill -851 %d -354 -844 %d -350 air replace %s", g.getY(), g.getY() + 1, plant));
            }
        });
        // 1. the notebook out of the sunset sky, the camera following it down; then it lands in the grass
        vclip("fall", 72, true, i -> {
            if (i == 6) {
                summonNote(spot[0].add(0, 10, 0));
            }
            ItemEntity note = fallenNote();
            if (note != null) {
                aim[0] = aim[0].lerp(note.position().add(0, 0.2, 0), 0.35);
            }
            camAt(eye[0].add(-0.012 * i, 0, 0), aim[0]);
        });
        add(4, () -> cmd("kill @e[type=minecraft:item]"));
        add(2, () -> fovOverride = 52);
        vclip("land", 60, true, i -> {
            if (i == 8) {
                summonNote(spot[0].add(0, 4.5, 0));
            }
            camAt(spot[0].add(2.4 - 0.01 * i, 0.85, 1.1 - 0.005 * i), spot[0].add(-0.1, 0.25, 0));
        });
        // 2. picked up: through your own eyes, your hand
        add(6, () -> {
            cmd("kill @e[type=minecraft:item]");
            cmd("gamemode creative");
            cmd("clear @s");
            fovOverride = 62;
            cleanHud = true;
            mc.options.hideGui = false;
        });
        add(10, () -> {
            cmd(String.format(Locale.ROOT, "summon minecraft:item %.2f %.2f %.2f {Item:{id:\"aicompanion:death_note\",Count:1b},PickupDelay:20,Motion:[0.0,0.0,0.0]}",
                    spot[0].x, spot[0].y, spot[0].z));
            cmd(String.format(Locale.ROOT, "tp @s %.2f %d %.2f 90 32", spot[0].x + 3.6, surface((int) Math.floor(spot[0].x + 3.6), (int) Math.floor(spot[0].z)).getY(),
                    spot[0].z));
            select(0);
        });
        add(10, () -> {
            mc.player.getAbilities().flying = false;
            mc.player.onUpdateAbilities();
        });
        add(30, () -> { });
        vclip("pickup", 76, true, i -> {
            if (i == 10) {
                mc.options.keyUp.setDown(true);
            } else if (i == 40) {
                mc.options.keyUp.setDown(false);
            }
            mc.player.setYRot(90);
            mc.player.setXRot(i < 40 ? 32 : Math.max(6, 32 - (i - 40) * 1.1F));
        });
        // 3. dusk: lightning, and he comes (his own film: the camera is his)
        add(6, () -> {
            cmd("time set 13200");
            mc.options.hideGui = true;
            cleanHud = false;
            fovOverride = 0;
        });
        waitFor(400, () -> "ryuk".equals(Cinema.now()), () -> {
            if (!"ryuk".equals(Cinema.now())) {
                AiCompanion.LOGGER.warn("AUTOTEST shorts: no arrival from the pickup, giving the notebook");
                cmd("give @s aicompanion:death_note");
            }
        });
        waitFor(600, () -> "ryuk".equals(Cinema.now()), () -> AiCompanion.LOGGER.info("AUTOTEST shorts: the arrival"));
        vclip("arrive", 240, true, i -> { });
        waitFor(400, () -> Cinema.now() == null, () -> { });
        add(160, () -> { }); // his first words, the chapter's card (the reader turns it)
        // 4. you, in your skin and cape, from behind: he hangs above, facing you
        add(10, () -> {
            cmd("time set 13400");
            cmd(String.format(Locale.ROOT, "tp @s -845.5 %d -359.5 90 -14", surface(-846, -360).getY()));
            mc.player.getAbilities().flying = false;
            mc.player.onUpdateAbilities();
            select(slotOf(st -> st.is(AiCompanion.DEATH_NOTE.get())));
        });
        add(40, () -> {
            cmd(String.format(Locale.ROOT, "tp @e[type=aicompanion:companion] -848.9 %.2f -358.9 -100 0", mc.player.getY() + 1.3));
            cmd("companion hold");
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            fovOverride = 52;
        });
        add(40, () -> { });
        vclip("back", 80, true, i -> {
            mc.player.setYRot(86 + 8 * i / 79F);
            mc.player.setXRot(-14 + 3 * i / 79F);
        });
        // 5. your eyes, burning; he looms over your shoulder
        add(10, () -> cmd("companion kira eyes"));
        waitFor(100, () -> mc.screen instanceof EyeDealScreen, () -> {
            if (mc.screen instanceof EyeDealScreen) {
                Network.CHANNEL.sendToServer(new Network.Answer(Network.Choice.EYE_DEAL, true));
                mc.setScreen(null);
            }
        });
        add(40, () -> {
            if (mc.screen instanceof EyeLookScreen) {
                mc.setScreen(null);
            }
            cmd(String.format(Locale.ROOT, "tp @e[type=aicompanion:companion] %.2f %.2f %.2f 90 0", mc.player.getX() + 1.3, mc.player.getY() + 0.5,
                    mc.player.getZ() + 0.4));
            cmd("companion hold");
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            fovOverride = 36;
        });
        add(40, () -> { });
        vclip("eyes", 90, true, i -> {
            mc.player.setYRot(78 + 24 * i / 89F);
            mc.player.setXRot(2);
        });
        // 6. his moods at sunset: he holds still facing east, the camera close round him
        add(10, () -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            cmd("time set 12550");
            cmd(String.format(Locale.ROOT, "tp @s -834.5 %d -371.5 -90 0", surface(-835, -372).getY()));
        });
        add(40, () -> {
            cmd(String.format(Locale.ROOT, "tp @e[type=aicompanion:companion] -828.5 %.1f -371.5 -90 0", surface(-829, -372).getY() + 0.4));
            cmd("companion hold");
        });
        add(20, () -> {
            cmd("gamemode spectator");
            fovOverride = 58;
            r[0] = ryuk().position();
        });
        vmood("cackle", 64, "cackle", 3.6, 3.1, -14, -6, 1.1, 1.2, 1.5, r);
        vmood("wings", 64, "show_wings", 4.2, 3.6, 20, 12, 0.4, 0.5, 1.6, r);
        vmood("juggle", 56, "juggle", 3.2, 2.9, 4, -4, 1.3, 1.3, 1.6, r);
        vmood("eat", 70, "eat", 2.5, 2.2, 12, 6, 1.7, 1.7, 1.9, r);
        vmood("rub", 48, "rub_hands", 2.6, 2.3, -10, -4, 1.6, 1.6, 1.8, r);
        vmood("handstand", 60, "handstand", 4.0, 4.0, 25, 25, 0.9, 0.9, 1.0, r);
        vmood("spin", 44, "spin", 3.6, 3.6, -25, -25, 1.2, 1.2, 1.5, r);
        vmood("dice", 56, "dice_shake", 3.0, 2.7, 8, 2, 1.4, 1.4, 1.7, r);
        vmood("slam", 50, "dice_slam", 3.0, 3.0, -6, -6, 1.4, 1.4, 1.6, r);
        vmood("peek", 48, "peek", 2.4, 2.4, 0, 0, 1.8, 1.8, 2.0, r);
        vmood("point", 40, "point", 3.0, 3.0, 10, 10, 1.5, 1.5, 1.8, r);
        vmood("backflip", 50, "backflip", 4.2, 4.2, 15, 15, 1.0, 1.0, 1.3, r);
        // 7. him talking at twilight, close, from a few sides (his voice is laid on later)
        add(10, () -> cmd("time set 13050"));
        add(30, () -> { });
        vmood("talk1", 130, "say happy Heh. Name in hand, face in your head. Ever actually seen Herobrine's face?", 2.3, 2.1, -12, -8, 1.95, 2.0, 2.15, r);
        vmood("talk2", 130, "say happy Obsessed? Heh. Earth apples are juicy. Ours taste like sand.", 2.1, 1.9, 16, 10, 2.0, 2.05, 2.2, r);
        vmood("talk3", 170, "say neutral Sleep. Toss bones on skulls. Stare down holes at you lot. Me? I dropped a notebook.", 2.5, 2.2, -24, -16, 1.8, 1.85,
                2.1, r);
        vmood("talk4", 180, "say happy Heh. Ever? I already promised it. When your time's up, it's my pen on the page. Until then, you'd better keep me "
                + "entertained.", 2.0, 1.7, 4, 2, 1.55, 1.6, 2.25, r);
        vmood("talk5", 130, "say happy Heh. Word from my side: Sidoh dropped his notebook somewhere down here. Again.", 2.4, 2.2, 10, 4, 1.9, 1.9, 2.1, r);
        vmood("talk6", 120, "say happy Oh? Gambling? Fine. Even or odd, the skull cup doesn't cheat. Heh.", 2.6, 2.3, -16, -10, 1.75, 1.8, 2.05, r);
        vmood("talk7", 170, "say neutral My place? Heh. Humans don't just stroll in. Though a golden apple, and a good night's sleep with me hovering "
                + "nearby. Things get strange.", 2.3, 2.0, 22, 14, 1.9, 1.95, 2.15, r);
        // 8. the dice table: he shakes the skull cup, slams it, you call, the cup comes up (whole frames: the table is wide)
        add(10, () -> {
            cmd("gamemode creative");
            cmd("give @s minecraft:apple 16");
            fovOverride = 0;
        });
        add(20, () -> cmd("companion dice"));
        waitFor(100, () -> mc.screen instanceof DiceScreen, () -> AiCompanion.LOGGER.info("AUTOTEST shorts: the dice table {}", mc.screen));
        add(20, () -> {
            if (mc.screen != null) {
                mc.screen.keyPressed(GLFW.GLFW_KEY_UP, 0, 0);
            }
        });
        add(6, () -> {
            if (mc.screen != null) {
                mc.screen.keyPressed(GLFW.GLFW_KEY_UP, 0, 0);
            }
        });
        add(20, () -> { });
        vclip("dice_ui", 180, false, i -> {
            if (mc.screen == null) {
                return;
            }
            if (i == 8) {
                mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            } else if (i >= 60 && i <= 150 && i % 10 == 0) {
                mc.screen.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0); // "even": taken the moment the cup is down
            }
        });
        add(20, () -> mc.setScreen(null));
        // 9. by day: a page of Sidoh's notebook flutters down; caught, and Sidoh comes (his film), then Sidoh himself
        add(20, () -> {
            cmd("time set 4000");
            cmd("companion follow");
            cmd(String.format(Locale.ROOT, "tp @s -834.5 %d -371.5 90 -12", surface(-835, -372).getY()));
            mc.options.hideGui = false;
            cleanHud = true; // the card, none of the vanilla HUD
        });
        add(80, () -> cmd("companion quest sidoh"));
        add(16, () -> grab("sf_card_sidoh", false));
        add(4, () -> {
            mc.options.hideGui = true;
            cleanHud = false;
        });
        vclip("page", 90, true, i -> {
            LostPage p = nearestPage();
            if (p != null) {
                follow(p.position(), 0.2F);
            }
        });
        add(5, () -> {
            LostPage p = nearestPage();
            if (p != null) {
                mc.gameMode.interact(mc.player, p, InteractionHand.MAIN_HAND);
            }
        });
        waitFor(100, () -> Cinema.now() != null, () -> AiCompanion.LOGGER.info("AUTOTEST shorts: Sidoh's film {}", Cinema.now()));
        vclip("sidoh_film", 130, true, i -> { });
        waitFor(300, () -> Cinema.now() == null, () -> { });
        add(30, () -> {
            cmd("gamemode spectator");
            fovOverride = 50;
            SidohEntity s = nearestSidoh();
            r[0] = s == null ? mc.player.position() : s.position();
            AiCompanion.LOGGER.info("AUTOTEST shorts: Sidoh at {}", s == null ? "nowhere" : s.position());
        });
        vclip("sidoh", 90, true, i -> {
            SidohEntity s = nearestSidoh();
            Vec3 c = s == null ? r[0] : s.position();
            double a = Math.toRadians(-60 + 50 * i / 89.0), d = 3.2;
            camAt(new Vec3(c.x - Math.sin(a) * d, c.y + 1.3, c.z + Math.cos(a) * d), c.add(0, 1.1, 0));
        });
        // 10. the realm: its invitation card, then the realm itself (its films already seen: they'd take the camera)
        add(10, () -> {
            cmd("gamemode creative");
            mc.options.hideGui = false;
            cleanHud = true;
            cmd("companion quest realm");
        });
        add(16, () -> grab("sf_card_realm", false));
        add(4, () -> {
            mc.options.hideGui = true;
            cleanHud = false;
        });
        add(10, () -> onServer(() -> {
            for (String f : new String[]{"film_realm", "film_god.gamblers", "film_god.armonia", "film_god.king", "film_god.sidoh", "film_person.l"}) {
                Kira.of(me()).flags.put(f, 0L);
            }
            return 0;
        }));
        add(10, () -> cmd("companion kira dream"));
        waitFor(400, () -> mc.level.dimension() == ru.maksim.aicompanion.Realm.KEY, () -> { });
        add(200, () -> {
            cmd("gamemode spectator");
            fovOverride = 62;
        });
        add(20, () -> { });
        java.util.function.Supplier<Vec3> realm = () -> Vec3.ZERO;
        vdolly("realm_road", 140, realm, new double[]{0.5, 51.9, -10.5, 0, -2}, new double[]{0.5, 52.4, 9.5, 0, -8});
        vdolly("realm_king", 90, realm, new double[]{0.5, 53.0, 13.5, 0, -16}, new double[]{0.5, 56.6, 19.5, 0, -32});
        vdolly("realm_gods", 70, realm, new double[]{-14.0, 53.62, 3.5, 180, 4}, new double[]{-18.0, 53.62, 3.5, 180, 4});
        vdolly("realm_note", 80, realm, new double[]{-36.5, 52.4, -2.5, 90, -14}, new double[]{-41.0, 52.4, -2.5, 90, -18});
        add(10, () -> {
            cmd("gamemode creative");
            cmd("companion kira wake");
        });
        waitFor(300, () -> mc.level.dimension() != ru.maksim.aicompanion.Realm.KEY, () -> { });
        // 11. L's tower by night: the camera rising up its face, then inside behind him
        add(20, () -> {
            cmd("time set 18000");
            cmd("companion follow");
            cmd("tp @s -840.5 72 -352.5 0 0");
            cmd("companion kira hq");
        });
        add(200, () -> {
            cmd("gamemode spectator");
            fovOverride = 64;
        });
        java.util.function.Supplier<Vec3> hq = () -> Vec3.atLowerCornerOf(flag("hq"));
        vdolly("hq", 120, hq, new double[]{0.5, 2.6, 31.5, 180, -8}, new double[]{0.5, 10.0, 25.5, 180, -24});
        vdolly("hq_in", 90, hq, new double[]{0.5, 8.8, 5.0, 180, 11}, new double[]{0.5, 8.8, 3.2, 180, 13});
        add(20, () -> {
            mc.options.bobView().set(true);
            fovOverride = 0;
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    @SubscribeEvent
    public static void highlight(net.minecraftforge.client.event.RenderHighlightEvent.Block event) {
        if (cleanHud) {
            event.setCanceled(true); // the black outline round the block looked at: the game's, not the film's
        }
    }

    /**
     * The shorts' retakes (env AICOMPANION_AUTOTEST=shorts2, the skin as for =shorts): the notebook picked up through your
     * eyes (a few steps, then the view lifts away from the house's wall, no block outline), his arrival from the open
     * village (not against that wall), your eyes burning (the deal made on the server: the offer waits for a free screen).
     */
    private static void shortsRetake() {
        Minecraft mc = Minecraft.getInstance();
        Vec3[] spot = new Vec3[1];
        add(20, AutoTest::toBiggestMonitor);
        add(30, () -> mc.getWindow().toggleFullScreen());
        add(40, () -> {
            mc.options.hideGui = true;
            mc.options.bobView().set(false);
            for (String c : new String[]{"weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
                    "gamerule sendCommandFeedback false", "time set 12600", "gamemode creative", "clear @s", "tp @s -843.5 72 -352.5 90 30"}) {
                cmd(c);
            }
        });
        add(60, () -> {
            wearSkin();
            BlockPos g = surface(-850, -353);
            spot[0] = new Vec3(-849.5, g.getY(), -352.5);
            for (String plant : new String[]{"minecraft:grass", "minecraft:tall_grass"}) {
                cmd(String.format(Locale.ROOT, "fill -851 %d -354 -844 %d -350 air replace %s", g.getY(), g.getY() + 1, plant));
            }
            cmd("kill @e[type=minecraft:rabbit]");
        });
        add(10, () -> {
            cmd(String.format(Locale.ROOT, "summon minecraft:item %.2f %.2f %.2f {Item:{id:\"aicompanion:death_note\",Count:1b},PickupDelay:20,Motion:[0.0,0.0,0.0]}",
                    spot[0].x, spot[0].y, spot[0].z));
            cmd(String.format(Locale.ROOT, "tp @s %.2f %d %.2f 90 32", spot[0].x + 2.8, surface((int) Math.floor(spot[0].x + 2.8), (int) Math.floor(spot[0].z)).getY(),
                    spot[0].z));
            select(0);
            fovOverride = 62;
            cleanHud = true;
            mc.options.hideGui = false;
        });
        add(10, () -> {
            mc.player.getAbilities().flying = false;
            mc.player.onUpdateAbilities();
        });
        add(30, () -> { });
        vclip("pickup", 80, true, i -> {
            if (i == 10) {
                mc.options.keyUp.setDown(true);
            } else if (i == 20) {
                mc.options.keyUp.setDown(false);
            }
            float k = (float) smooth01((i - 26) / 50.0); // picked up: the view lifts and turns away from the wall, the notebook in hand
            mc.player.setYRot(Mth.lerp(k, 90F, 38F));
            mc.player.setXRot(Mth.lerp(k, 32F, 3F));
        });
        add(2, () -> { // the open village, for his film
            cleanHud = false;
            mc.options.hideGui = true;
            fovOverride = 0;
            cmd("time set 13200");
            cmd("tp @s -840.5 72 -352.5 90 -4");
        });
        waitFor(400, () -> "ryuk".equals(Cinema.now()), () -> {
            if (!"ryuk".equals(Cinema.now())) {
                AiCompanion.LOGGER.warn("AUTOTEST shorts2: no arrival from the pickup, giving the notebook");
                cmd("give @s aicompanion:death_note");
            }
        });
        waitFor(600, () -> "ryuk".equals(Cinema.now()), () -> AiCompanion.LOGGER.info("AUTOTEST shorts2: the arrival"));
        vclip("arrive", 240, true, i -> { });
        waitFor(400, () -> Cinema.now() == null, () -> { });
        add(160, () -> { });
        // your eyes: the deal made on the server (the offer would wait for a free screen), then the face, him over the shoulder
        add(10, () -> onServer(() -> {
            Kira.Case c = Kira.of(me());
            c.eyes = Math.max(1, c.eyes);
            Kira.changed(me());
            return 0;
        }));
        add(20, () -> {
            cmd("time set 13400");
            cmd(String.format(Locale.ROOT, "tp @s -845.5 %d -359.5 90 2", surface(-846, -360).getY()));
            mc.player.getAbilities().flying = false;
            mc.player.onUpdateAbilities();
            select(slotOf(st -> st.is(AiCompanion.DEATH_NOTE.get())));
        });
        add(40, () -> {
            cmd(String.format(Locale.ROOT, "tp @e[type=aicompanion:companion] %.2f %.2f %.2f 90 0", mc.player.getX() + 1.3, mc.player.getY() + 0.5,
                    mc.player.getZ() + 0.4));
            cmd("companion hold");
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            fovOverride = 36;
            AiCompanion.LOGGER.info("AUTOTEST shorts2: the eyes {}", ClientKira.eyes);
        });
        add(40, () -> { });
        vclip("eyes", 90, true, i -> {
            mc.player.setYRot(78 + 24 * i / 89F);
            mc.player.setXRot(2);
        });
        add(20, () -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.bobView().set(true);
            fovOverride = 0;
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    // ------------------------------------------------------------------ the film and the second shorts: one 4K frame, two pictures

    private static boolean noHand; // first person on film: no arm in the corner of the frame

    @SubscribeEvent
    public static void hand(net.minecraftforge.client.event.RenderHandEvent event) {
        if (noHand) {
            event.setCanceled(true);
        }
    }

    private static java.util.Map<String, String> filmLines;

    /** Ryuk's line {@code id} as the edit voices it (trailer/voice/lines4.txt: the mod's own Ryuk, trimmed). */
    private static String line(String id) {
        if (filmLines == null) {
            filmLines = new java.util.HashMap<>();
            Path file = Minecraft.getInstance().gameDirectory.toPath().resolve("../../trailer/voice/lines4.txt").normalize();
            try {
                for (String row : Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8)) {
                    String[] f = row.split("\\|", 3);
                    if (f.length == 3 && !row.startsWith("#")) {
                        filmLines.put(f[0].trim(), f[2].trim());
                    }
                }
            } catch (IOException e) {
                AiCompanion.LOGGER.error("AUTOTEST film: no lines in {}", file, e);
            }
        }
        return filmLines.getOrDefault(id, "Heh.");
    }

    /**
     * A clip for the film and its shorts: {@code frames} frames, one a client tick, each taken as {@code which} says: "w" the
     * whole frame halved (autotest_fw_{name}_{nnn}.jpg, 1920x1080), "v" the upright column at full size (autotest_fv_..., 1215x2160).
     */
    private static void fclip(String name, int frames, String which, java.util.function.IntConsumer at) {
        add(0, () -> at.accept(0));
        for (int i = 0; i < frames; i++) {
            int n = i;
            add(0, () -> {
                String f = String.format(Locale.ROOT, "%s_%03d", name, n);
                if (which.contains("w")) {
                    grabHalf("fw_" + f);
                }
                if (which.contains("v")) {
                    grab("fv_" + f, true);
                }
                if (n + 1 < frames) {
                    at.accept(n + 1);
                }
            });
        }
    }

    /** The camera round a point: from (d0, a0, u0) to (d1, a1, u1) (distance, angle from its east side, height over it), looking at it raised by look. */
    private static void forbit(String name, int frames, String which, java.util.function.Supplier<Vec3> centre, @Nullable String cue,
                               double d0, double d1, double a0, double a1, double u0, double u1, double look) {
        Vec3[] c = new Vec3[1];
        fclip(name, frames, which, i -> {
            if (i == 0) {
                c[0] = centre.get();
            }
            if (cue != null && i == 4) {
                cmd(cue);
            }
            double k = i / (frames - 1.0), a = Math.toRadians(-90 + Mth.lerp(k, a0, a1)), d = Mth.lerp(k, d0, d1);
            camAt(c[0].add(-Math.sin(a) * d, Mth.lerp(k, u0, u1), Math.cos(a) * d), c[0].add(0, look, 0));
        });
        add(40, () -> { });
    }

    /** {@link #dolly} for the film: from a to b (x, y, z of the eyes, yaw, pitch; relative to {@code origin}). */
    private static void fdolly(String name, int frames, String which, java.util.function.Supplier<Vec3> origin, double[] a, double[] b) {
        Vec3[] o = new Vec3[1];
        fclip(name, frames, which, i -> {
            if (i == 0) {
                o[0] = origin.get();
            }
            double k = i / (frames - 1.0);
            cam(o[0].add(Mth.lerp(k, a[0], b[0]), Mth.lerp(k, a[1], b[1]), Mth.lerp(k, a[2], b[2])), (float) Mth.lerp(k, a[3], b[3]),
                    (float) Mth.lerp(k, a[4], b[4]));
        });
        add(20, () -> { });
    }

    /** Ryuk, held, says {@code id}'s line to the camera as it moves round him; the frame his voice starts on goes to the log for the edit. */
    private static void ftalk(String name, int frames, String which, String emotion, String id, double d0, double d1, double a0, double a1,
                              double u0, double u1, double look, Vec3[] r) {
        boolean[] heard = {false};
        fclip(name, frames, which, i -> {
            if (i == 4) {
                cmd("companion say " + emotion + " " + line(id));
            }
            CompanionEntity ry = ryukNear();
            if (!heard[0] && ry != null && RyukVoice.mouthOpen(ry.getId()) != null) {
                heard[0] = true;
                AiCompanion.LOGGER.info("AUTOTEST film: {} voice from frame {}", name, i);
            }
            double k = i / (frames - 1.0), a = Math.toRadians(-90 + Mth.lerp(k, a0, a1)), d = Mth.lerp(k, d0, d1);
            camAt(r[0].add(-Math.sin(a) * d, Mth.lerp(k, u0, u1), Math.cos(a) * d), r[0].add(0, look, 0));
        });
        add(30, () -> { }); // a breath before the next (the clip runs a second past his last word)
    }

    /** Ryuk to {@code at} (his feet), facing {@code yaw}, held there; then r[0] is where he is. */
    private static void holdAt(Vec3[] r, java.util.function.Supplier<Vec3> at, float yaw) {
        add(5, () -> {
            Vec3 p = at.get();
            cmd(String.format(Locale.ROOT, "tp @e[type=aicompanion:companion] %.2f %.2f %.2f %.0f 0", p.x, p.y, p.z, yaw));
            cmd("companion hold");
        });
        add(30, () -> r[0] = ryuk().position());
    }

    /** One of the mod's own films (a first meeting) as the player sees it: titles and all, no arm, none of the vanilla HUD. */
    private static void ffilm(String name, int frames, String which) {
        Minecraft mc = Minecraft.getInstance();
        add(2, () -> {
            noHand = true;
            cleanHud = true;
            mc.options.hideGui = false;
            fovOverride = 0;
        });
        waitFor(400, () -> Cinema.now() != null, () -> AiCompanion.LOGGER.info("AUTOTEST film: {} plays {}", name, Cinema.now()));
        fclip(name, frames, which, i -> { });
        waitFor(300, () -> Cinema.now() == null, () -> { });
        add(2, () -> {
            noHand = false;
            cleanHud = false;
            mc.options.hideGui = true;
        });
    }

    /** The player standing at {@code at} (feet), facing yaw/pitch, in creative, on the ground. */
    private static void standAt(java.util.function.Supplier<Vec3> at, float yaw, float pitch) {
        add(5, () -> {
            Minecraft mc = Minecraft.getInstance();
            cmd("gamemode creative");
            Vec3 p = at.get();
            cmd(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f %.0f %.0f", p.x, p.y, p.z, yaw, pitch));
            mc.player.getAbilities().flying = false;
            mc.player.onUpdateAbilities();
        });
        add(20, () -> { });
    }

    /**
     * The film and the second shorts, part one (env AICOMPANION_AUTOTEST=film, AICOMPANION_OFFLINE=silent, the player in
     * AICOMPANION_SKIN...): on the biggest screen, every frame halved (fw_) and as the upright column (fv_). The village at
     * sunset from above; the notebook out of the sky and into the grass; his arrival; the walk at golden hour; him at sunset
     * (who he is, his wings, a laugh); dusk (whose name first); the village by night; L's tower rising, its hall from the
     * gallery, L's own film, Ryuk's line in L's hall; the mafia's shack, the tunnel down, their hall, Sidoh, Ryuk's line
     * there; the SPK's white hall and Near's film; the Yellow Box at dawn; Kira's shrine by candlelight; the storm (him under
     * the lightning, his line about losing), your red eyes in the rain.
     */
    private static void filmShoot() {
        Minecraft mc = Minecraft.getInstance();
        Vec3[] spot = new Vec3[1], aim = new Vec3[1], eye = new Vec3[1], r = new Vec3[1];
        int[] gy = new int[1];
        add(20, AutoTest::toBiggestMonitor);
        add(30, () -> mc.getWindow().toggleFullScreen());
        add(40, () -> {
            mc.options.hideGui = true;
            mc.options.bobView().set(false);
            fovOverride = 60;
            for (String c : new String[]{"weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
                    "gamerule doFireTick false", "gamerule sendCommandFeedback false", "time set 12700", "gamemode spectator", "tp @s -843.5 74 -350.5 90 -45"}) {
                cmd(c);
            }
        });
        add(60, () -> {
            wearSkin();
            AiCompanion.LOGGER.info("AUTOTEST film: framebuffer {}x{}", mc.getMainRenderTarget().width, mc.getMainRenderTarget().height);
            BlockPos g = surface(-850, -353);
            spot[0] = new Vec3(-849.5, g.getY(), -352.5); // where the notebook comes to rest
            eye[0] = new Vec3(-843.2, Math.max(g.getY(), surface(-844, -351).getY()) + 1.5, -350.2);
            aim[0] = spot[0].add(0, 10, 0);
            for (String plant : new String[]{"minecraft:grass", "minecraft:tall_grass"}) {
                cmd(String.format(Locale.ROOT, "fill -851 %d -354 -844 %d -350 air replace %s", g.getY(), g.getY() + 1, plant));
            }
            gy[0] = surface(-826, -361).getY();
        });
        // 1. the village at sunset: the camera rising over the roofs, toward the sun
        fdolly("vista", 140, "wv", () -> new Vec3(-825.5, gy[0], -360.5), new double[]{0, 2.5, 0, 90, -2}, new double[]{0, 19, 6, 96, 16});
        // 2. the notebook out of the sunset sky, the camera low in the grass following it down; it lands in front of the lens
        add(4, () -> fovOverride = 56);
        fclip("fall", 72, "wv", i -> {
            if (i == 6) {
                summonNote(spot[0].add(0, 10, 0));
            }
            ItemEntity note = fallenNote();
            if (note != null) {
                aim[0] = aim[0].lerp(note.position().add(0, 0.2, 0), 0.35);
            }
            camAt(eye[0].add(-0.012 * i, 0, 0), aim[0]);
        });
        add(4, () -> cmd("kill @e[type=minecraft:item]"));
        add(2, () -> fovOverride = 52);
        fclip("land", 60, "wv", i -> {
            if (i == 8) {
                summonNote(spot[0].add(0, 4.5, 0));
            }
            camAt(spot[0].add(2.4 - 0.01 * i, 0.85, 1.1 - 0.005 * i), spot[0].add(-0.1, 0.25, 0));
        });
        // 3. dusk: the notebook in hand, and he comes (his own film: the camera is his)
        add(6, () -> {
            cmd("kill @e[type=minecraft:item]");
            cmd("time set 13200");
            cmd("gamemode creative");
            fovOverride = 0;
            cmd("tp @s -840.5 72 -352.5 90 -4");
        });
        add(40, () -> cmd("give @s aicompanion:death_note"));
        waitFor(600, () -> "ryuk".equals(Cinema.now()), () -> AiCompanion.LOGGER.info("AUTOTEST film: the arrival"));
        fclip("arrive", 240, "wv", i -> { });
        waitFor(400, () -> Cinema.now() == null, () -> { });
        add(160, () -> { }); // his first words, the chapter's card (the reader turns it)
        // 4. golden hour: you walk, he floats along; from behind, the cape and the notebook
        standAt(() -> new Vec3(-864.5, surface(-865, -367).getY(), -366.5), 180, 6);
        add(10, () -> {
            cmd("time set 12050");
            cmd("companion follow");
            select(slotOf(st -> st.is(AiCompanion.DEATH_NOTE.get())));
        });
        add(60, () -> {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            fovOverride = 58;
        });
        fclip("walk", 150, "wv", i -> {
            if (i == 6) {
                mc.options.keyUp.setDown(true);
            } else if (i == 140) {
                mc.options.keyUp.setDown(false);
            }
            mc.player.setYRot(180);
            mc.player.setXRot(6);
        });
        add(10, () -> {
            mc.options.keyUp.setDown(false);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        });
        // 5. sunset: he holds still facing east, the camera close round him (who he is); his wings, wide; a laugh, close
        standAt(() -> new Vec3(-834.5, surface(-835, -372).getY(), -371.5), -90, 0);
        add(5, () -> cmd("time set 12550"));
        holdAt(r, () -> new Vec3(-828.5, surface(-829, -372).getY() + 0.4, -371.5), -90);
        add(20, () -> {
            cmd("gamemode spectator");
            fovOverride = 56;
        });
        ftalk("t_real", 290, "wv", "happy", "f_real", 2.4, 2.1, -16, -8, 1.9, 1.95, 2.15, r);
        forbit("wings", 140, "wv", () -> r[0], "companion action show_wings", 6.4, 5.4, -60, 30, 0.9, 1.5, 1.8);
        forbit("cackle", 70, "wv", () -> r[0], "companion action cackle", 3.3, 2.9, -14, -6, 1.2, 1.3, 1.6);
        // 6. the dusk going blue: whose name first
        add(10, () -> cmd("time set 13700"));
        add(40, () -> { });
        ftalk("t_first", 240, "wv", "happy", "f_first", 2.3, 2.0, 18, 8, 1.95, 2.0, 2.2, r);
        // 7. the village by night, its windows lit
        add(10, () -> {
            cmd("time set 18000");
            fovOverride = 60;
        });
        add(40, () -> { });
        fdolly("village_night", 140, "wv", () -> new Vec3(-825.5, gy[0], -368.5), new double[]{0, 9, 0, 100, 16}, new double[]{-3, 10, 7, 112, 19});
        // 8. L's tower by night: up its face from the ground; the hall from the gallery; L (his film); Ryuk's line in L's hall
        standAt(() -> new Vec3(-840.5, 72, -352.5), 0, 0);
        add(10, () -> {
            cmd("companion follow");
            cmd("companion kira hq");
        });
        add(220, () -> {
            cmd("gamemode spectator");
            fovOverride = 62;
        });
        java.util.function.Supplier<Vec3> hq = () -> Vec3.atLowerCornerOf(flag("hq"));
        fdolly("hq_rise", 130, "wv", hq, new double[]{0.5, 2.6, 31.5, 180, -10}, new double[]{0.5, 15.0, 24.5, 180, -24});
        fdolly("hq_side", 110, "w", hq, new double[]{19.5, 5.5, 31.5, 145, -12}, new double[]{13.5, 8.5, 34.5, 160, -16});
        fdolly("hq_hall", 120, "wv", hq, new double[]{-3.5, 12.6, 6.5, 180, 26}, new double[]{3.5, 12.6, 6.5, 180, 22});
        standAt(() -> hq.get().add(0.5, 6, 4.5), 180, 0);
        add(2, () -> select(8));
        ffilm("l_film", 150, "wv");
        holdAt(r, () -> hq.get().add(0.5, 6.3, 1.2), 0);
        add(20, () -> {
            cmd("gamemode spectator");
            fovOverride = 56;
        });
        ftalk("t_l", 270, "wv", "happy", "f_l", 2.6, 2.3, 80, 100, 1.7, 1.8, 2.1, r);
        // 9. the mafia's hideout: the shack by night, the tunnel down, their hall (Sidoh hovering), Ryuk's line among them
        standAt(() -> new Vec3(-840.5, 72, -352.5), 90, 0);
        add(10, () -> {
            cmd("companion follow");
            cmd("companion kira sidoh");
        });
        add(300, () -> {
            onServer(() -> {
                Kira.of(me()).flags.put("sidoh_touched", overworld().getGameTime()); // the camera sees him, as one who touched his notebook
                Kira.changed(me());
                return 0;
            });
            cmd("gamemode spectator");
            fovOverride = 62;
        });
        java.util.function.Supplier<Vec3> top = () -> Vec3.atLowerCornerOf(flag("hideout"));
        java.util.function.Supplier<Vec3> den = () -> Vec3.atLowerCornerOf(onServer(() -> ru.maksim.aicompanion.Mafia.hall(overworld(), flag("hideout"))));
        fdolly("shack", 110, "wv", top, new double[]{0.5, 1.7, 13.5, 180, 3}, new double[]{0.5, 1.7, 8.0, 180, 5});
        fdolly("tunnel", 160, "wv", top, new double[]{0.5, 1.6, 0.5, 180, 34}, new double[]{0.5, -11.4, -14.0, 180, 22});
        fdolly("den", 130, "wv", den, new double[]{0.5, 2.6, 14.0, 180, 8}, new double[]{0.5, 3.6, 10.0, 180, 13});
        forbit("sidoh_den", 100, "wv", () -> {
            SidohEntity s = nearestSidoh();
            return s == null ? den.get().add(0.5, 4, 9.5) : s.position();
        }, null, 3.6, 3.0, 110, 160, 0.8, 1.0, 1.3);
        holdAt(r, () -> den.get().add(3.5, 2.0, 11.0), -45);
        add(10, () -> fovOverride = 56);
        ftalk("t_mafia", 280, "wv", "neutral", "f_mafia", 2.6, 2.3, 30, 45, 1.6, 1.7, 2.0, r);
        // 10. the SPK: Near's white hall and his toys; Near himself (his film)
        standAt(() -> new Vec3(-840.5, 72, -352.5), 90, 0);
        add(10, () -> {
            cmd("companion follow");
            cmd("time set 6000");
            cmd("companion kira successors");
        });
        add(320, () -> {
            cmd("gamemode spectator");
            fovOverride = 62;
        });
        java.util.function.Supplier<Vec3> spk = () -> Vec3.atLowerCornerOf(flag("spk"));
        fdolly("spk_in", 130, "wv", spk, new double[]{0.5, 2.8, 9.5, 180, 14}, new double[]{0.5, 2.6, 5.8, 180, 17});
        fdolly("spk_side", 110, "w", spk, new double[]{9.5, 2.6, 7.5, 140, 10}, new double[]{6.5, 2.6, 2.5, 155, 12});
        standAt(() -> spk.get().add(0.5, 1, 8.5), 180, 10);
        ffilm("near_film", 150, "wv");
        // 11. the Yellow Box at dawn, from outside (inside, at the meeting, Near would begin)
        add(10, () -> {
            cmd("companion follow");
            cmd("time set 23300");
            cmd("companion kira yellowbox");
        });
        add(320, () -> {
            cmd("gamemode spectator");
            fovOverride = 60;
        });
        fdolly("box", 120, "wv", () -> Vec3.atLowerCornerOf(flag("yellow_box")), new double[]{-6.5, 2.4, 26.5, 165, 2}, new double[]{0.5, 3.4, 20.5, 180, 4});
        // 12. Kira's shrine by candlelight
        standAt(() -> new Vec3(-840.5, 72, -352.5), 90, 0);
        add(10, () -> {
            cmd("time set 18000");
            cmd("companion kira glory 50");
        });
        waitFor(600, () -> flagOrNull("shrine") != null, () -> AiCompanion.LOGGER.info("AUTOTEST film: the shrine at {}", flagOrNull("shrine")));
        add(100, () -> {
            cmd("gamemode spectator");
            fovOverride = 56;
        });
        forbit("shrine", 130, "wv", () -> Vec3.atLowerCornerOf(flag("shrine")).add(0.5, 1, 0.5), null, 4.4, 3.6, 60, 120, 1.0, 1.5, 1.0);
        // 13. the storm: him under the lightning, from the grass; his line on losing; your red eyes in the rain
        standAt(() -> new Vec3(-834.5, surface(-835, -372).getY(), -371.5), -90, 0);
        add(10, () -> {
            cmd("companion follow");
            cmd("weather thunder");
            cmd("time set 18000");
        });
        holdAt(r, () -> new Vec3(-828.5, surface(-829, -372).getY() + 2.4, -371.5), -90);
        add(20, () -> {
            cmd("gamemode spectator");
            fovOverride = 60;
        });
        fclip("storm", 150, "wv", i -> {
            if (i == 28 || i == 104) {
                Vec3 b = r[0].add(i == 28 ? -12 : -14, -2.4, i == 28 ? -6 : 7);
                cmd(String.format(Locale.ROOT, "summon minecraft:lightning_bolt %.1f %.1f %.1f", b.x, b.y, b.z));
            }
            double k = i / 149.0, a = Math.toRadians(-90 + Mth.lerp(k, -30, 12)), d = Mth.lerp(k, 5.2, 4.4);
            camAt(r[0].add(-Math.sin(a) * d, Mth.lerp(k, -0.9, -0.6), Math.cos(a) * d), r[0].add(0, 2.0, 0));
        });
        add(40, () -> { });
        holdAt(r, () -> new Vec3(-828.5, surface(-829, -372).getY() + 0.5, -371.5), -90);
        ftalk("t_lose", 260, "wv", "neutral", "f_lose", 2.5, 2.2, -12, -4, 1.7, 1.8, 2.1, r);
        add(10, () -> onServer(() -> {
            Kira.Case c = Kira.of(me());
            c.eyes = Math.max(1, c.eyes);
            Kira.changed(me());
            return 0;
        }));
        standAt(() -> new Vec3(-845.5, surface(-846, -360).getY(), -359.5), 90, 2);
        add(10, () -> select(slotOf(st -> st.is(AiCompanion.DEATH_NOTE.get()))));
        add(30, () -> {
            cmd(String.format(Locale.ROOT, "tp @e[type=aicompanion:companion] %.2f %.2f %.2f 90 0", mc.player.getX() + 1.3, mc.player.getY() + 0.5,
                    mc.player.getZ() + 0.4));
            cmd("companion hold");
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            fovOverride = 36;
        });
        add(40, () -> { });
        fclip("eyes", 100, "wv", i -> {
            mc.player.setYRot(78 + 24 * i / 99F);
            mc.player.setXRot(2);
        });
        add(20, () -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            cmd("weather clear");
            mc.options.bobView().set(true);
            fovOverride = 0;
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /**
     * Part one again where it failed (env AICOMPANION_AUTOTEST=film3, as for =film): the walk, west into the sunset over
     * the open field; his lines with his voice playing as he says them (at sunset, at dusk, a new one by night, in L's
     * hall, among the mafia, in the storm); his wings from closer (the hill behind the first take); L's film without the
     * game's toasts; the storm lighter and closer.
     */
    private static void filmRetake() {
        Minecraft mc = Minecraft.getInstance();
        Vec3[] r = new Vec3[1];
        add(20, AutoTest::toBiggestMonitor);
        add(30, () -> mc.getWindow().toggleFullScreen());
        add(40, () -> {
            mc.options.hideGui = true;
            mc.options.bobView().set(false);
            fovOverride = 60;
            for (String c : new String[]{"weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
                    "gamerule doFireTick false", "gamerule sendCommandFeedback false", "time set 13200", "gamemode creative", "tp @s -840.5 72 -352.5 90 -4"}) {
                cmd(c);
            }
        });
        add(60, AutoTest::wearSkin);
        add(20, () -> cmd("give @s aicompanion:death_note"));
        waitFor(600, () -> "ryuk".equals(Cinema.now()), () -> { });
        waitFor(400, () -> Cinema.now() == null, () -> { });
        add(100, () -> { });
        // the walk: west, the sun ahead, from behind (the cape, the notebook in hand), him floating along
        standAt(() -> new Vec3(-829.5, surface(-830, -361).getY(), -360.5), 90, 6);
        add(10, () -> {
            cmd("time set 12050");
            cmd("companion follow");
            select(slotOf(st -> st.is(AiCompanion.DEATH_NOTE.get())));
            mc.options.autoJump().set(true);
        });
        add(60, () -> {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            fovOverride = 58;
        });
        fclip("walk", 110, "wv", i -> {
            if (i == 6) {
                mc.options.keyUp.setDown(true);
            } else if (i == 100) {
                mc.options.keyUp.setDown(false);
            }
            mc.player.setYRot(90);
            mc.player.setXRot(6);
        });
        add(10, () -> {
            mc.options.keyUp.setDown(false);
            mc.options.autoJump().set(false);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        });
        // sunset, dusk, night: who he is, his wings, whose name first, friends
        standAt(() -> new Vec3(-834.5, surface(-835, -372).getY(), -371.5), -90, 0);
        add(5, () -> cmd("time set 12550"));
        holdAt(r, () -> new Vec3(-828.5, surface(-829, -372).getY() + 0.4, -371.5), -90);
        add(20, () -> {
            cmd("gamemode spectator");
            fovOverride = 56;
        });
        ftalk("t_real", 290, "wv", "happy", "f_real", 2.4, 2.1, -16, -8, 1.9, 1.95, 2.15, r);
        forbit("wings", 140, "wv", () -> r[0], "companion action show_wings", 4.2, 3.6, -35, 25, 1.0, 1.6, 1.8);
        add(10, () -> cmd("time set 13700"));
        add(40, () -> { });
        ftalk("t_first", 240, "wv", "happy", "f_first", 2.3, 2.0, 18, 8, 1.95, 2.0, 2.2, r);
        add(10, () -> cmd("time set 18000"));
        add(40, () -> { });
        ftalk("t_friends", 220, "wv", "happy", "f_friends", 2.4, 2.1, -6, 6, 1.9, 1.95, 2.15, r);
        // L's tower: his film, then Ryuk's line in his hall
        standAt(() -> new Vec3(-840.5, 72, -352.5), 0, 0);
        add(10, () -> {
            cmd("companion follow");
            cmd("companion kira hq");
        });
        add(220, () -> { });
        java.util.function.Supplier<Vec3> hq = () -> Vec3.atLowerCornerOf(flag("hq"));
        standAt(() -> hq.get().add(0.5, 6, 4.5), 180, 0);
        add(2, () -> select(8));
        ffilm("l_film", 150, "wv");
        holdAt(r, () -> hq.get().add(0.5, 6.3, 1.2), 0);
        add(20, () -> {
            cmd("gamemode spectator");
            fovOverride = 56;
        });
        ftalk("t_l", 270, "wv", "happy", "f_l", 2.6, 2.3, 80, 100, 1.7, 1.8, 2.1, r);
        // the mafia's hall: the camera down there first (Ryuk is found near it), then his line among them
        standAt(() -> new Vec3(-840.5, 72, -352.5), 90, 0);
        add(10, () -> {
            cmd("companion follow");
            cmd("companion kira sidoh");
        });
        add(300, () -> {
            onServer(() -> {
                Kira.of(me()).flags.put("sidoh_touched", overworld().getGameTime());
                Kira.changed(me());
                return 0;
            });
            cmd("gamemode spectator");
            fovOverride = 56;
        });
        java.util.function.Supplier<Vec3> den = () -> Vec3.atLowerCornerOf(onServer(() -> ru.maksim.aicompanion.Mafia.hall(overworld(), flag("hideout"))));
        add(10, () -> cam(den.get().add(0.5, 3.6, 10.0), 180, 13));
        add(40, () -> { });
        holdAt(r, () -> den.get().add(3.5, 2.0, 11.0), -45);
        ftalk("t_mafia", 280, "wv", "neutral", "f_mafia", 2.6, 2.3, 30, 45, 1.6, 1.7, 2.0, r);
        // the storm, at the last of the dusk: him over the field under the lightning, close; his line on losing
        add(10, () -> cmd("gamemode creative"));
        standAt(() -> new Vec3(-834.5, surface(-835, -372).getY(), -371.5), -90, 0);
        add(10, () -> {
            cmd("companion follow");
            cmd("weather thunder");
            cmd("time set 13000");
        });
        holdAt(r, () -> new Vec3(-828.5, surface(-829, -372).getY() + 2.4, -371.5), -90);
        add(20, () -> {
            cmd("gamemode spectator");
            fovOverride = 58;
        });
        fclip("storm", 150, "wv", i -> {
            if (i == 20 || i == 70 || i == 118) {
                Vec3 b = r[0].add(i == 70 ? -10 : -13, -2.4, i == 20 ? -5 : i == 70 ? 6 : -1);
                cmd(String.format(Locale.ROOT, "summon minecraft:lightning_bolt %.1f %.1f %.1f", b.x, b.y, b.z));
            }
            double k = i / 149.0, a = Math.toRadians(-90 + Mth.lerp(k, -24, 10)), d = Mth.lerp(k, 4.2, 3.5);
            camAt(r[0].add(-Math.sin(a) * d, Mth.lerp(k, -1.0, -0.7), Math.cos(a) * d), r[0].add(0, 2.0, 0));
        });
        add(40, () -> { });
        holdAt(r, () -> new Vec3(-828.5, surface(-829, -372).getY() + 0.5, -371.5), -90);
        ftalk("t_lose", 260, "wv", "neutral", "f_lose", 2.5, 2.2, -12, -4, 1.7, 1.8, 2.1, r);
        add(20, () -> {
            cmd("weather clear");
            mc.options.bobView().set(true);
            fovOverride = 0;
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /**
     * The film's house by the village (x -856..-848, z -360..-352): spruce walls, a dark floor, windows, a door; the bed by
     * the north wall, a chest, the drawer with the false bottom (the notebook already inside), lanterns, L's cameras put up;
     * the player inside with an ink sac, Kira's watch, the chips, apples and a golden one. {@code searchable}: barrels and
     * chests about the room as well, so L's men go round opening them. h: the west wall's x, the floor's y, the north wall's z.
     */
    private static void buildHouse(int[] h, boolean searchable) {
        java.util.function.Supplier<Vec3> house = () -> new Vec3(h[0], h[1], h[2]);
        // the house: spruce walls, a dark floor, windows, a door; the bed by the north wall, a chest, the drawer, lanterns
        add(10, () -> {
            BlockPos g = surface(-852, -356);
            h[0] = -856;
            h[1] = g.getY();
            h[2] = -360;
            int x0 = h[0], y = h[1], z0 = h[2], x1 = x0 + 8, z1 = z0 + 8;
            for (String c : new String[]{
                    String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", x0 - 1, y, z0 - 1, x1 + 1, y + 6, z1 + 1),
                    String.format(Locale.ROOT, "fill %d %d %d %d %d %d spruce_planks hollow", x0, y - 1, z0, x1, y + 4, z1),
                    String.format(Locale.ROOT, "fill %d %d %d %d %d %d dark_oak_planks", x0 + 1, y - 1, z0 + 1, x1 - 1, y - 1, z1 - 1),
                    String.format(Locale.ROOT, "fill %d %d %d %d %d %d stripped_dark_oak_log", x0, y - 1, z0, x0, y + 4, z0),
                    String.format(Locale.ROOT, "fill %d %d %d %d %d %d stripped_dark_oak_log", x1, y - 1, z0, x1, y + 4, z0),
                    String.format(Locale.ROOT, "fill %d %d %d %d %d %d stripped_dark_oak_log", x0, y - 1, z1, x0, y + 4, z1),
                    String.format(Locale.ROOT, "fill %d %d %d %d %d %d stripped_dark_oak_log", x1, y - 1, z1, x1, y + 4, z1),
                    String.format(Locale.ROOT, "fill %d %d %d %d %d %d glass_pane", x0 + 3, y + 1, z0, x0 + 5, y + 2, z0),
                    String.format(Locale.ROOT, "fill %d %d %d %d %d %d glass_pane", x1, y + 1, z0 + 3, x1, y + 2, z0 + 5),
                    String.format(Locale.ROOT, "fill %d %d %d %d %d %d glass_pane", x0, y + 1, z0 + 3, x0, y + 2, z0 + 5),
                    String.format(Locale.ROOT, "fill %d %d %d %d %d %d glass_pane", x0 + 1, y + 1, z1, x0 + 2, y + 2, z1),
                    String.format(Locale.ROOT, "setblock %d %d %d spruce_door[half=lower,facing=north]", x0 + 5, y, z1),
                    String.format(Locale.ROOT, "setblock %d %d %d spruce_door[half=upper,facing=north]", x0 + 5, y + 1, z1),
                    String.format(Locale.ROOT, "setblock %d %d %d red_bed[facing=north,part=head]", x0 + 2, y, z0 + 1),
                    String.format(Locale.ROOT, "setblock %d %d %d red_bed[facing=north,part=foot]", x0 + 2, y, z0 + 2),
                    String.format(Locale.ROOT, "setblock %d %d %d chest[facing=south]", x0 + 4, y, z0 + 1),
                    String.format(Locale.ROOT, "setblock %d %d %d aicompanion:hidden_drawer[facing=south]", x0 + 6, y, z0 + 1),
                    String.format(Locale.ROOT, "setblock %d %d %d bookshelf", x0 + 7, y, z0 + 3),
                    String.format(Locale.ROOT, "setblock %d %d %d bookshelf", x0 + 7, y + 1, z0 + 3),
                    String.format(Locale.ROOT, "setblock %d %d %d potted_red_tulip", x0 + 7, y, z0 + 5),
                    String.format(Locale.ROOT, "fill %d %d %d %d %d %d red_carpet", x0 + 3, y, z0 + 4, x0 + 5, y, z0 + 6),
                    String.format(Locale.ROOT, "setblock %d %d %d lantern[hanging=true]", x0 + 2, y + 3, z0 + 5),
                    String.format(Locale.ROOT, "setblock %d %d %d lantern[hanging=true]", x0 + 6, y + 3, z0 + 5),
                    String.format(Locale.ROOT, "setblock %d %d %d lantern", x0 + 7, y + 2, z0 + 3),
                    String.format(Locale.ROOT, "spawnpoint @s %d %d %d", x0 + 2, y, z0 + 2), "time set 18000"}) {
                cmd(c);
            }
        });
        standAt(() -> house.get().add(4.5, 0, 5.5), 180, 10);
        add(20, () -> {
            cmd("companion kira cameras");
            for (String item : new String[]{"minecraft:ink_sac", "aicompanion:kira_watch", "aicompanion:chips_with_page", "minecraft:apple 4",
                    "minecraft:golden_apple"}) {
                cmd("give @s " + item);
            }
            onServer(() -> { // the notebook already in the false bottom
                BlockPos d = BlockPos.containing(house.get().add(6, 0, 1));
                if (overworld().getBlockEntity(d) instanceof ru.maksim.aicompanion.Hideouts.Drawer drawer) {
                    drawer.setItem(0, new ItemStack(AiCompanion.DEATH_NOTE.get()));
                }
                return 0;
            });
        });
        if (searchable) {
            add(5, () -> {
                int x0 = h[0], y = h[1], z0 = h[2];
                for (int[] c : new int[][]{{1, 6}, {7, 7}, {1, 4}, {7, 1}, {6, 7}}) {
                    cmd(String.format(Locale.ROOT, "setblock %d %d %d %s", x0 + c[0], y, z0 + c[1], c[0] == 1 && c[1] == 4 ? "chest[facing=east]" : "barrel[facing=up]"));
                }
            });
        }
    }

    /**
     * Retakes after a look at the first cuts (env AICOMPANION_AUTOTEST=film5, as for =film): at the last of the dusk in the
     * rain him under the lightning (no tree in the way), his line on your world and your red eyes (the night takes were
     * too dark for a phone), his line on being friends; the Death Note with "latenight" going into it; L's men searching
     * your house with the camera already in the room (barrels and chests about, so they go round opening them; the drawer
     * with the false bottom they pass by); his world with its labels taken down and no HUD (the plaza, the road, the King,
     * the gamblers, his two lines there); L at his desk, the mafia's boss and Sidoh, close.
     */
    private static void filmRetake2() {
        Minecraft mc = Minecraft.getInstance();
        Vec3[] r = new Vec3[1];
        int[] h = new int[3];
        java.util.function.Supplier<Vec3> house = () -> new Vec3(h[0], h[1], h[2]);
        add(20, AutoTest::toBiggestMonitor);
        add(30, () -> mc.getWindow().toggleFullScreen());
        add(40, () -> {
            mc.options.hideGui = true;
            mc.options.bobView().set(false);
            fovOverride = 60;
            for (String c : new String[]{"weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
                    "gamerule doFireTick false", "gamerule sendCommandFeedback false", "time set 13200", "gamemode creative", "tp @s -840.5 72 -352.5 90 -4"}) {
                cmd(c);
            }
        });
        add(60, AutoTest::wearSkin);
        add(20, () -> cmd("give @s aicompanion:death_note"));
        waitFor(600, () -> "ryuk".equals(Cinema.now()), () -> { });
        waitFor(400, () -> Cinema.now() == null, () -> { });
        add(100, () -> { });
        // the last of the dusk in the rain: him under the lightning again (the bolts further off, the camera clear of the tree);
        // his line on your world, from the other side of him than the film's line on losing
        standAt(() -> new Vec3(-834.5, surface(-835, -372).getY(), -371.5), -90, 0);
        add(10, () -> {
            cmd("companion follow");
            cmd("weather thunder");
            cmd("time set 13000");
        });
        holdAt(r, () -> new Vec3(-828.5, surface(-829, -372).getY() + 2.4, -371.5), -90);
        add(20, () -> {
            cmd("gamemode spectator");
            fovOverride = 58;
        });
        fclip("storm2", 150, "wv", i -> {
            if (i == 20 || i == 70 || i == 118) {
                Vec3 b = r[0].add(i == 70 ? -18 : -20, -2.4, i == 20 ? -7 : i == 70 ? 8 : -2);
                cmd(String.format(Locale.ROOT, "summon minecraft:lightning_bolt %.1f %.1f %.1f", b.x, b.y, b.z));
            }
            double k = i / 149.0, a = Math.toRadians(-90 + Mth.lerp(k, -26, -6)), d = Mth.lerp(k, 4.0, 3.4);
            camAt(r[0].add(-Math.sin(a) * d, Mth.lerp(k, -0.9, -0.6), Math.cos(a) * d), r[0].add(0, 2.0, 0));
        });
        add(30, () -> { });
        holdAt(r, () -> new Vec3(-828.5, surface(-829, -372).getY() + 0.4, -371.5), -90);
        add(20, () -> {
            cmd("gamemode spectator");
            fovOverride = 56;
        });
        ftalk("t_world2", 255, "wv", "neutral", "s_world", 2.4, 2.1, 6, 14, 1.85, 1.9, 2.15, r);
        // your red eyes in the rain, him over your shoulder
        add(10, () -> onServer(() -> {
            Kira.Case c = Kira.of(me());
            c.eyes = Math.max(1, c.eyes);
            Kira.changed(me());
            return 0;
        }));
        standAt(() -> new Vec3(-845.5, surface(-846, -360).getY(), -359.5), 90, 2);
        add(10, () -> select(slotOf(st -> st.is(AiCompanion.DEATH_NOTE.get()))));
        add(30, () -> {
            cmd(String.format(Locale.ROOT, "tp @e[type=aicompanion:companion] %.2f %.2f %.2f 90 0", mc.player.getX() + 1.3, mc.player.getY() + 0.5,
                    mc.player.getZ() + 0.4));
            cmd("companion hold");
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            fovOverride = 36;
        });
        add(40, () -> { });
        fclip("eyes2", 100, "wv", i -> {
            mc.player.setYRot(78 + 24 * i / 99F);
            mc.player.setXRot(2);
        });
        add(10, () -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            cmd("weather clear");
            cmd("companion follow");
            fovOverride = 0;
        });
        // the very last of the dusk, the rain gone: his line on being friends (the night take was too dark)
        standAt(() -> new Vec3(-834.5, surface(-835, -372).getY(), -371.5), -90, 0);
        add(10, () -> cmd("time set 13400"));
        holdAt(r, () -> new Vec3(-828.5, surface(-829, -372).getY() + 0.4, -371.5), -90);
        add(20, () -> {
            cmd("gamemode spectator");
            fovOverride = 56;
        });
        ftalk("t_friends2", 220, "wv", "happy", "f_friends", 2.4, 2.1, -6, 6, 1.9, 1.95, 2.15, r);
        add(10, () -> {
            cmd("gamemode creative");
            cmd("companion follow");
            fovOverride = 0;
        });
        // the Death Note open, "latenight" going into it (never written: no Enter)
        add(20, () -> {
            select(slotOf(st -> st.is(AiCompanion.DEATH_NOTE.get())));
            ClientKira.openNote(InteractionHand.MAIN_HAND);
        });
        add(20, () -> { });
        String written = "latenight";
        fclip("write_name", 70, "wv", i -> {
            int c = (i - 10) / 3;
            if (mc.screen != null && i >= 10 && (i - 10) % 3 == 0 && c < written.length()) {
                mc.screen.charTyped(written.charAt(c), 0);
            }
        });
        add(10, () -> mc.setScreen(null));
        // L's men in your house: you far off when they come, the camera in the room at once
        buildHouse(h, true);
        standAt(() -> {
            Vec3 o = house.get();
            return new Vec3(o.x + 4.5, surface((int) Math.floor(o.x + 4.5), (int) Math.floor(o.z + 40.5)).getY(), o.z + 40.5);
        }, 180, 0);
        add(20, () -> cmd("companion follow"));
        add(5, () -> cmd("companion kira search"));
        add(1, () -> {
            cmd("gamemode spectator");
            fovOverride = 62;
            cam(house.get().add(7.3, 2.7, 7.4), 140, 20);
        });
        fdolly("search2", 220, "wv", house, new double[]{7.3, 2.7, 7.4, 140, 20}, new double[]{6.7, 2.6, 7.5, 155, 18});
        // a golden apple for him, sleep: his world with no labels in it and no HUD (the titles go on in the edit)
        add(10, () -> cmd("gamemode creative"));
        standAt(() -> house.get().add(3.5, 0, 4.5), 180, 10);
        add(20, () -> {
            cmd("time set 18000");
            cmd("kill @e[type=aicompanion:human]");
            cmd("kill @e[type=minecraft:villager,distance=..64]"); // (one took the bed in the first take)
            cmd(String.format(Locale.ROOT, "tp @e[type=aicompanion:companion] %.2f %.2f %.2f 0 0", house.get().x + 3.5, house.get().y + 0.4, house.get().z + 2.6));
            cmd("companion hold");
            select(slotOf(st -> st.is(Items.GOLDEN_APPLE)));
        });
        add(30, () -> mc.gameMode.interact(mc.player, ryuk(), InteractionHand.MAIN_HAND));
        add(40, () -> {
            cmd("companion follow");
            onServer(() -> {
                for (String f : new String[]{"film_god.armonia", "film_god.sidoh", "film_god.king", "film_god.gamblers", "film_person.l"}) {
                    Kira.of(me()).flags.put(f, 0L); // no film takes the camera mid-shot
                }
                return 0;
            });
        });
        add(10, () -> {
            BlockPos bed = BlockPos.containing(house.get().add(2, 0, 1));
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(bed), Direction.UP, bed, false));
        });
        waitFor(900, () -> mc.level.dimension() == ru.maksim.aicompanion.Realm.KEY, () -> AiCompanion.LOGGER.info("AUTOTEST film5: in the realm"));
        add(300, () -> {
            cmd("kill @e[tag=aicompanion_realm_label]");
            cmd("gamemode spectator");
            fovOverride = 62;
        });
        add(20, () -> { });
        java.util.function.Supplier<Vec3> realm = () -> Vec3.ZERO;
        fdolly("realm_open", 150, "wv", realm, new double[]{0.5, 51.8, 0.5, 0, -6}, new double[]{0.5, 57.5, -6.5, 0, 2});
        fdolly("r_road2", 140, "wv", realm, new double[]{0.5, 51.9, -10.5, 0, -2}, new double[]{0.5, 52.4, 9.5, 0, -8});
        fdolly("r_king2", 110, "wv", realm, new double[]{0.5, 53.0, 13.5, 0, -16}, new double[]{0.5, 56.6, 19.5, 0, -32});
        fdolly("r_gods2", 100, "wv", realm, new double[]{-14.0, 53.62, 3.5, 180, 4}, new double[]{-18.5, 53.62, 3.5, 180, 4});
        holdAt(r, () -> new Vec3(0.5, ru.maksim.aicompanion.Realm.GROUND + 1.4, 22.5), 180);
        ftalk("t_king2", 260, "wv", "neutral", "s_king", 3.2, 2.9, 255, 275, 1.0, 1.1, 3.0, r);
        holdAt(r, () -> new Vec3(-13.0, ru.maksim.aicompanion.Realm.GROUND + 1.4, 3.5), -90);
        ftalk("t_idle2", 295, "wv", "neutral", "s_idle", 2.5, 2.2, -10, 10, 1.7, 1.8, 2.0, r);
        add(10, () -> {
            cmd("companion follow");
            cmd("gamemode creative");
            cmd("companion kira wake");
        });
        waitFor(400, () -> mc.level.dimension() != ru.maksim.aicompanion.Realm.KEY, () -> AiCompanion.LOGGER.info("AUTOTEST film5: awake"));
        // L at his desk, close (no film, no name tag: the edit puts his title on)
        standAt(() -> new Vec3(-840.5, 72, -352.5), 0, 0);
        add(10, () -> {
            cmd("time set 18000");
            cmd("companion follow");
            cmd("companion kira hq");
        });
        add(220, () -> {
            cmd("gamemode spectator");
            fovOverride = 56;
        });
        java.util.function.Supplier<Vec3> hq = () -> Vec3.atLowerCornerOf(flag("hq"));
        add(10, () -> cam(hq.get().add(0.5, 7.6, 4.5), 180, 10));
        add(40, () -> { });
        faceOrbit("l_close", 120, "wv", () -> human(HumanEntity.Role.L), 2.6, 2.1, 40, 1.45, 1.35);
        // the mafia's boss reading faces at his table, Sidoh over the hall (the camera can't be read: a spectator)
        add(10, () -> cmd("gamemode creative"));
        standAt(() -> new Vec3(-840.5, 72, -352.5), 90, 0);
        add(10, () -> {
            cmd("companion follow");
            cmd("companion kira sidoh");
        });
        add(300, () -> {
            onServer(() -> {
                Kira.of(me()).flags.put("sidoh_touched", overworld().getGameTime()); // the camera sees him, as one who touched his notebook
                Kira.changed(me());
                return 0;
            });
            cmd("gamemode spectator");
            fovOverride = 56;
        });
        java.util.function.Supplier<Vec3> den = () -> Vec3.atLowerCornerOf(onServer(() -> ru.maksim.aicompanion.Mafia.hall(overworld(), flag("hideout"))));
        add(10, () -> cam(den.get().add(0.5, 3.6, 10.0), 180, 13));
        add(40, () -> { });
        faceOrbit("boss_eyes", 100, "wv", () -> human(HumanEntity.Role.BOSS), 2.2, 1.7, 30, 1.25, 1.2);
        faceOrbit("sidoh2", 100, "wv", AutoTest::nearestSidoh, 3.4, 2.8, 36, 0.4, 0.9);
        add(20, () -> {
            mc.options.bobView().set(true);
            fovOverride = 0;
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /**
     * The camera round someone's face: in front of it (their head's turn at the first frame), from d0 to d1 away,
     * swinging {@code spread} degrees across, at height u over their feet, looking at their eyes (height {@code look}).
     */
    private static void faceOrbit(String name, int frames, String which, java.util.function.Supplier<net.minecraft.world.entity.Entity> who,
                                  double d0, double d1, double spread, double u, double look) {
        net.minecraft.world.entity.Entity[] e = new net.minecraft.world.entity.Entity[1];
        double[] yaw = new double[1];
        fclip(name, frames, which, i -> {
            if (i == 0) {
                e[0] = who.get();
                yaw[0] = e[0] == null ? 0 : e[0].getYHeadRot();
                AiCompanion.LOGGER.info("AUTOTEST film: {} on {}", name, e[0]);
            }
            if (e[0] == null) {
                return;
            }
            double k = i / (frames - 1.0), a = Math.toRadians(yaw[0] + Mth.lerp(k, -spread / 2, spread / 2)), d = Mth.lerp(k, d0, d1);
            Vec3 c = e[0].position();
            camAt(c.add(-Math.sin(a) * d, u, Math.cos(a) * d), c.add(0, look, 0));
        });
        add(20, () -> { });
    }

    /** The story's person of that role nearest the player (found on the server, where the role lives), as the client sees them. */
    @Nullable
    private static net.minecraft.world.entity.Entity human(HumanEntity.Role role) {
        Integer id = onServer(() -> {
            net.minecraft.server.level.ServerPlayer sp = me();
            return sp.serverLevel().getEntitiesOfClass(HumanEntity.class, sp.getBoundingBox().inflate(64), h -> h.role() == role).stream()
                    .min(Comparator.comparingDouble(h -> h.distanceToSqr(sp))).map(net.minecraft.world.entity.Entity::getId).orElse(null);
        });
        return id == null ? null : Minecraft.getInstance().level.getEntity(id);
    }

    /**
     * Part two (env AICOMPANION_AUTOTEST=film2, as for =film): a house of your own by the village at night (the bed, a
     * chest, the drawer with the false bottom, L's cameras put up): an apple for Ryuk, his look for the cameras through the
     * walls; the false bottom opened with an ink sac; the watch's secret; the chips; his line on hiding it; L's men
     * searching the house; then a golden apple, sleep, and his realm (its film, the road under the ribs, the King's film,
     * the gamblers' film, the giant notebook, a hole down to the human world, his lines there, a fall into a hole); last,
     * his game: its rules taken, his line on your world, the eyes again, his pen on your name, and Mu. With {@code all}
     * false (=film4) only the golden apple on, without the game's screens: the realm and the pen again.
     */
    private static void filmShoot2(boolean all) {
        Minecraft mc = Minecraft.getInstance();
        Vec3[] r = new Vec3[1];
        int[] h = new int[3]; // the house: its west wall's x, the floor's y (the first air), its north wall's z
        java.util.function.Supplier<Vec3> house = () -> new Vec3(h[0], h[1], h[2]);
        add(20, AutoTest::toBiggestMonitor);
        add(30, () -> mc.getWindow().toggleFullScreen());
        add(40, () -> {
            mc.options.hideGui = true;
            mc.options.bobView().set(false);
            fovOverride = 60;
            for (String c : new String[]{"weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
                    "gamerule doFireTick false", "gamerule sendCommandFeedback false", "time set 13200", "gamemode creative", "tp @s -840.5 72 -352.5 90 -4"}) {
                cmd(c);
            }
        });
        add(60, AutoTest::wearSkin);
        add(20, () -> cmd("give @s aicompanion:death_note"));
        waitFor(600, () -> "ryuk".equals(Cinema.now()), () -> { });
        waitFor(400, () -> Cinema.now() == null, () -> { });
        add(160, () -> { });
        buildHouse(h, false);
        if (all) { // (=film4 retakes only what failed the first time: from the golden apple on, and the pen)
            holdAt(r, () -> house.get().add(4.5, 0.4, 3.6), 0);
            add(20, () -> {
                cmd("gamemode spectator");
                fovOverride = 64;
            });
            // the room by lantern light, the cameras on its walls
            fdolly("house", 130, "wv", house, new double[]{7.3, 2.6, 7.3, 135, 18}, new double[]{6.8, 2.5, 7.4, 158, 16});
            // "check my house for cameras, here, an apple": his yes, the apple taken from your hand, his stroll through the walls, the cameras lit
            ftalk("t_cam_a", 140, "wv", "happy", "s_cam_a", 2.4, 2.2, 80, 92, 1.7, 1.75, 2.0, r);
            standAt(() -> house.get().add(4.5, 0, 6.2), 180, 2);
            add(5, () -> {
                select(slotOf(st -> st.is(Items.APPLE)));
                cleanHud = true;
                mc.options.hideGui = false;
                fovOverride = 0;
            });
            add(20, () -> { });
            fclip("apple", 80, "wv", i -> {
                if (i == 12) {
                    mc.gameMode.interact(mc.player, ryuk(), InteractionHand.MAIN_HAND);
                }
            });
            add(5, () -> {
                cleanHud = false;
                mc.options.hideGui = true;
                cmd("gamemode spectator");
                fovOverride = 60;
            });
            ftalk("t_cam_b", 170, "wv", "happy", "s_cam_b", 2.4, 2.2, 92, 104, 1.7, 1.75, 2.0, r);
            add(5, () -> cmd("companion follow"));
            fdolly("sweep", 230, "wv", house, new double[]{7.3, 2.7, 7.4, 140, 16}, new double[]{6.9, 2.6, 7.4, 152, 14});
            add(5, () -> cmd("companion favour look_for_cameras")); // (the favour was paid for: the apple is in him)
            add(1, () -> { });
            fclip("sweep2", 160, "wv", i -> cam(house.get().add(1.3, 2.8, 7.3), 210, 18));
            // the drawer: with an ink sac in hand, the false bottom; the watch's secret; the chips with a page inside
            standAt(() -> house.get().add(6.5, 0, 3.4), 180, 32);
            add(5, () -> {
                select(slotOf(st -> st.is(Items.INK_SAC)));
                cleanHud = true;
                mc.options.hideGui = false;
                fovOverride = 0;
            });
            add(20, () -> { });
            fclip("drawer", 110, "wv", i -> {
                if (i == 30) {
                    BlockPos d = BlockPos.containing(house.get().add(6, 0, 1));
                    mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(d), Direction.SOUTH, d, false));
                }
            });
            add(10, () -> mc.setScreen(null));
            add(5, () -> select(slotOf(st -> st.is(AiCompanion.WATCH.get()))));
            add(20, () -> { });
            fclip("watch", 90, "wv", i -> {
                if (i == 10 || i == 13 || i == 16 || i == 19) {
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                }
            });
            add(10, () -> mc.setScreen(null));
            add(5, () -> select(slotOf(st -> st.is(AiCompanion.CHIPS_SECRET.get()))));
            add(20, () -> { });
            fclip("chips", 70, "wv", i -> {
                if (i == 10) {
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                }
            });
            add(10, () -> {
                mc.setScreen(null);
                cleanHud = false;
                mc.options.hideGui = true;
            });
            holdAt(r, () -> house.get().add(4.5, 0.4, 3.6), 0);
            add(10, () -> {
                cmd("gamemode spectator");
                fovOverride = 60;
            });
            ftalk("t_hide", 300, "wv", "happy", "s_hide", 2.5, 2.2, 82, 98, 1.7, 1.8, 2.05, r);
            // L's men search the house by the bed (started with you far away; they don't stop for a camera)
            standAt(() -> house.get().add(4.5, 0, 70.5), 180, 0);
            add(10, () -> {
                cmd("companion follow");
                cmd("companion kira search");
            });
            add(60, () -> {
                cmd("gamemode spectator");
                fovOverride = 62;
            });
            fdolly("search", 240, "wv", house, new double[]{7.3, 2.7, 7.4, 140, 20}, new double[]{6.6, 2.6, 7.5, 160, 18});
        }
        // a golden apple for him, sleep in your bed: his realm
        standAt(() -> house.get().add(3.5, 0, 4.5), 180, 10);
        add(60, () -> {
            cmd("kill @e[type=aicompanion:human]");
            cmd("kill @e[type=minecraft:villager,distance=..64]"); // the first take: a villager came in and took the bed
            cmd(String.format(Locale.ROOT, "tp @e[type=aicompanion:companion] %.2f %.2f %.2f 0 0", house.get().x + 3.5, house.get().y + 0.4, house.get().z + 2.6));
            cmd("companion hold");
            select(slotOf(st -> st.is(Items.GOLDEN_APPLE)));
            cleanHud = true;
            mc.options.hideGui = false;
            fovOverride = 0;
        });
        add(20, () -> { });
        fclip("golden", 80, "wv", i -> {
            if (i == 12) {
                mc.gameMode.interact(mc.player, ryuk(), InteractionHand.MAIN_HAND);
            }
        });
        add(40, () -> {
            cmd("companion follow");
            onServer(() -> {
                for (String f : new String[]{"film_god.armonia", "film_god.sidoh", "film_person.l"}) {
                    Kira.of(me()).flags.put(f, 0L); // these two films would take the camera mid-shot; the King's and the gamblers' are filmed
                }
                return 0;
            });
        });
        add(10, () -> {
            noHand = true;
            BlockPos bed = BlockPos.containing(house.get().add(2, 0, 1));
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(bed), Direction.UP, bed, false));
        });
        fclip("sleep", 60, "wv", i -> { });
        waitFor(900, () -> mc.level.dimension() == ru.maksim.aicompanion.Realm.KEY, () -> AiCompanion.LOGGER.info("AUTOTEST film2: in the realm"));
        fclip("realm_film", 200, "wv", i -> { });
        waitFor(300, () -> Cinema.now() == null, () -> { });
        add(40, () -> {
            noHand = false;
            cleanHud = false;
            mc.options.hideGui = true;
        });
        // the King (his film, from the plaza's end of the road), then the road and the throne of chains
        standAt(() -> new Vec3(0.5, ru.maksim.aicompanion.Realm.GROUND + 1, 20.5), 0, -25); // within 30 of him: his film
        ffilm("king_film", 150, "wv");
        add(20, () -> {
            cmd("gamemode spectator");
            fovOverride = 62;
        });
        java.util.function.Supplier<Vec3> realm = () -> Vec3.ZERO;
        fdolly("r_road", 140, "wv", realm, new double[]{0.5, 51.9, -10.5, 0, -2}, new double[]{0.5, 52.4, 9.5, 0, -8});
        fdolly("r_king", 110, "wv", realm, new double[]{0.5, 53.0, 13.5, 0, -16}, new double[]{0.5, 56.6, 19.5, 0, -32});
        // the gamblers (their film), their bones
        standAt(() -> new Vec3(-10.5, ru.maksim.aicompanion.Realm.GROUND + 1, 3.5), 90, 0);
        ffilm("gods_film", 150, "wv");
        add(20, () -> {
            cmd("gamemode spectator");
            fovOverride = 62;
        });
        fdolly("r_gods", 90, "wv", realm, new double[]{-14.0, 53.62, 3.5, 180, 4}, new double[]{-18.0, 53.62, 3.5, 180, 4});
        fdolly("r_note", 90, "wv", realm, new double[]{-36.5, 52.4, -2.5, 90, -14}, new double[]{-41.0, 52.4, -2.5, 90, -18});
        fdolly("r_hole", 120, "wv", realm, new double[]{0.5, 58.0, -9.5, 180, 62}, new double[]{0.5, 56.2, -12.0, 180, 82});
        // his lines at home: on dying (the road and the King behind him), on the idle gods (the gamblers behind), on the King
        holdAt(r, () -> new Vec3(0.5, ru.maksim.aicompanion.Realm.GROUND + 1.4, 6.5), 180);
        ftalk("t_die", 285, "wv", "neutral", "f_die", 2.5, 2.2, 255, 275, 1.7, 1.8, 2.0, r);
        holdAt(r, () -> new Vec3(-13.0, ru.maksim.aicompanion.Realm.GROUND + 1.4, 3.5), -90);
        ftalk("t_idle", 295, "wv", "neutral", "s_idle", 2.5, 2.2, -10, 10, 1.7, 1.8, 2.0, r);
        holdAt(r, () -> new Vec3(0.5, ru.maksim.aicompanion.Realm.GROUND + 1.4, 22.5), 180);
        ftalk("t_king", 260, "wv", "neutral", "s_king", 3.2, 2.9, 255, 275, 1.0, 1.1, 3.0, r);
        // a fall into the big hole: you wake
        add(5, () -> cmd("companion follow"));
        standAt(() -> new Vec3(0.5, ru.maksim.aicompanion.Realm.GROUND + 1, -8.5), 180, 40);
        add(5, () -> noHand = true);
        fclip("hole_fall", 110, "wv", i -> {
            if (i == 8) {
                mc.options.keyUp.setDown(true);
            } else if (i == 40) {
                mc.options.keyUp.setDown(false);
            }
        });
        add(5, () -> {
            mc.options.keyUp.setDown(false);
            noHand = false;
        });
        waitFor(600, () -> mc.level.dimension() != ru.maksim.aicompanion.Realm.KEY, () -> AiCompanion.LOGGER.info("AUTOTEST film2: awake"));
        add(100, () -> { });
        // his game: its rules (taken), his line on your world; the eyes again (half of what's left); his pen on your name; Mu
        standAt(() -> new Vec3(-834.5, surface(-835, -372).getY(), -371.5), -90, 0);
        if (all) {
            add(10, () -> {
                cmd("time set 18000");
                cmd("companion follow");
                ClientKira.offer(Network.Choice.PACT);
            });
            add(40, () -> { });
            fclip("pact_ui", 80, "wv", i -> { });
            add(5, () -> {
                Network.CHANNEL.sendToServer(new Network.Answer(Network.Choice.PACT, true));
                mc.setScreen(null);
            });
            holdAt(r, () -> new Vec3(-828.5, surface(-829, -372).getY() + 0.4, -371.5), -90);
            add(10, () -> {
                cmd("gamemode spectator");
                fovOverride = 56;
            });
            ftalk("t_world", 255, "wv", "neutral", "s_world", 2.4, 2.1, -14, -6, 1.85, 1.9, 2.15, r);
            standAt(() -> new Vec3(-834.5, surface(-835, -372).getY(), -371.5), -90, 0);
            add(10, () -> {
                cmd("companion follow");
                cmd("companion kira eyes");
            });
            waitFor(300, () -> mc.screen instanceof EyeDealScreen, () -> { });
            add(10, () -> { });
            fclip("deal_ui", 70, "wv", i -> { });
            add(5, () -> {
                Network.CHANNEL.sendToServer(new Network.Answer(Network.Choice.EYE_DEAL, true));
                mc.setScreen(null);
            });
            add(60, () -> {
                if (mc.screen instanceof EyeLookScreen) {
                    mc.setScreen(null);
                }
            });
        }
        // his pen on your name: the last of the dusk, you facing what's left of the light, him come close to write it
        add(10, () -> {
            cmd("gamemode creative");
            cmd("time set 13000");
            cmd("companion follow");
            onServer(() -> {
                Kira.Case c = Kira.of(me());
                c.pact = Kira.Pact.ACCEPTED;
                Kira.changed(me());
                return 0;
            });
        });
        add(40, () -> {
            noHand = true;
            fovOverride = 44;
            mc.player.setYRot(90);
            mc.player.setXRot(0);
            cmd("companion kira finish BORED");
        });
        fclip("writes", 200, "wv", i -> {
            CompanionEntity ry = ryukNear();
            if (i > 20 && ry != null) {
                lookAt(ry);
            }
        });
        waitFor(1400, () -> mc.screen instanceof MuScreen, () -> AiCompanion.LOGGER.info("AUTOTEST film2: Mu {}", mc.screen));
        add(20, () -> { });
        fclip("mu", 150, "wv", i -> { });
        add(20, () -> {
            noHand = false;
            cleanHud = false;
            mc.options.bobView().set(true);
            fovOverride = 0;
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /**
     * Pictures for the mod's pages (env AICOMPANION_AUTOTEST=gallery, the player in AICOMPANION_SKIN...): whole 4K
     * frames as PNG (autotest_g_*.png) on the biggest screen. His arrival; the player and Ryuk at sunset (from behind,
     * the cape; from the front); his moods; the eyes; the mod's screens (the notebook, the news, the specials, his game,
     * the deal, the dice); every recipe on a crafting table and the television set itself; Sidoh; the realm; L's tower;
     * the green studio for cut-outs and the icon (his face, close).
     */
    private static void galleryShoot() {
        Minecraft mc = Minecraft.getInstance();
        Vec3[] r = new Vec3[1];
        add(20, AutoTest::toBiggestMonitor);
        add(30, () -> mc.getWindow().toggleFullScreen());
        add(40, () -> {
            mc.options.hideGui = true;
            mc.options.bobView().set(false);
            for (String c : new String[]{"weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
                    "gamerule sendCommandFeedback false", "time set 13200", "gamemode creative", "tp @s -840.5 72 -352.5 90 -4"}) {
                cmd(c);
            }
        });
        add(60, () -> {
            wearSkin();
            AiCompanion.LOGGER.info("AUTOTEST gallery: framebuffer {}x{}, gui scale {}", mc.getMainRenderTarget().width, mc.getMainRenderTarget().height,
                    mc.getWindow().getGuiScale());
        });
        // 1. the notebook in hand, and he comes: his film, around the landing (tick ~79 of it)
        add(20, () -> cmd("give @s aicompanion:death_note"));
        waitFor(600, () -> "ryuk".equals(Cinema.now()), () -> AiCompanion.LOGGER.info("AUTOTEST gallery: the arrival"));
        int[] arrival = {72, 5, 4, 5, 6, 10, 14, 25};
        for (int i = 0; i < arrival.length; i++) {
            int n = i;
            add(arrival[i], () -> shot("g_arrive_" + n));
        }
        waitFor(400, () -> Cinema.now() == null, () -> { });
        add(160, () -> { }); // his first words, the chapter's card (the reader turns it)
        // 2. the two of them at sunset: from behind (the cape, the notebook in hand, him hanging over you), from the front
        add(10, () -> {
            cmd("time set 12350");
            cmd(String.format(Locale.ROOT, "tp @s -845.5 %d -359.5 90 -12", surface(-846, -360).getY()));
            mc.player.getAbilities().flying = false;
            mc.player.onUpdateAbilities();
            select(slotOf(st -> st.is(AiCompanion.DEATH_NOTE.get())));
        });
        add(40, () -> {
            cmd(String.format(Locale.ROOT, "tp @e[type=aicompanion:companion] -848.9 %.2f -358.9 -100 0", mc.player.getY() + 1.3));
            cmd("companion hold");
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            fovOverride = 50;
        });
        add(50, () -> shot("g_hero_back"));
        add(4, () -> {
            mc.player.setYRot(104);
            mc.player.setXRot(-4);
        });
        add(12, () -> shot("g_hero_back2"));
        add(4, () -> {
            cmd(String.format(Locale.ROOT, "tp @e[type=aicompanion:companion] %.2f %.2f %.2f 90 0", mc.player.getX() + 1.4, mc.player.getY() + 0.6,
                    mc.player.getZ() + 0.5));
            cmd("companion hold");
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            fovOverride = 40;
            mc.player.setYRot(84);
            mc.player.setXRot(4);
        });
        add(40, () -> shot("g_hero_front"));
        // 3. his moods at sunset, a spectator's camera close round him (he faces east, the sun behind him)
        add(10, () -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            cmd("time set 12450");
            cmd(String.format(Locale.ROOT, "tp @s -834.5 %d -371.5 -90 0", surface(-835, -372).getY()));
        });
        add(40, () -> {
            cmd(String.format(Locale.ROOT, "tp @e[type=aicompanion:companion] -828.5 %.1f -371.5 -90 0", surface(-829, -372).getY() + 0.4));
            cmd("companion hold");
        });
        add(20, () -> {
            cmd("gamemode spectator");
            fovOverride = 55;
            r[0] = ryuk().position();
        });
        String[][] moods = {{"cackle", "3.6", "-12", "1.2", "1.55", "18"}, {"show_wings", "4.4", "16", "0.5", "1.6", "30"},
                {"juggle", "3.2", "0", "1.3", "1.6", "24"}, {"eat", "2.5", "10", "1.7", "1.9", "24"}, {"rub_hands", "2.6", "-8", "1.6", "1.8", "20"},
                {"handstand", "4.0", "25", "0.9", "1.0", "30"}, {"peek", "2.3", "0", "1.8", "2.0", "22"}, {"point", "3.0", "10", "1.5", "1.8", "18"}};
        for (String[] m : moods) {
            add(10, () -> {
                double a = Math.toRadians(-90 + Double.parseDouble(m[2])), d = Double.parseDouble(m[1]);
                camAt(r[0].add(-Math.sin(a) * d, Double.parseDouble(m[3]), Math.cos(a) * d), r[0].add(0, Double.parseDouble(m[4]), 0));
                cmd("companion action " + m[0]);
            });
            add(Integer.parseInt(m[5]), () -> shot("g_mood_" + m[0]));
            add(14, () -> shot("g_mood_" + m[0] + "_b"));
            add(40, () -> { });
        }
        // 4. the eyes: the deal made (on the server: the offer waits for a free screen), night, his shape over your shoulder
        add(10, () -> {
            cmd("gamemode creative");
            cmd("time set 18000");
            onServer(() -> {
                Kira.Case c = Kira.of(me());
                c.eyes = Math.max(1, c.eyes);
                Kira.changed(me());
                return 0;
            });
            cmd(String.format(Locale.ROOT, "tp @s -845.5 %d -359.5 90 2", surface(-846, -360).getY()));
        });
        add(30, () -> {
            mc.player.getAbilities().flying = false;
            mc.player.onUpdateAbilities();
            cmd(String.format(Locale.ROOT, "tp @e[type=aicompanion:companion] %.2f %.2f %.2f 90 0", mc.player.getX() + 1.3, mc.player.getY() + 0.5,
                    mc.player.getZ() + 0.4));
            cmd("companion hold");
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            fovOverride = 34;
            mc.player.setYRot(92);
            mc.player.setXRot(2);
        });
        add(50, () -> shot("g_eyes"));
        // the names and the years over the villagers' heads, in the evening light
        add(5, () -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            fovOverride = 0;
            cmd("companion follow");
            cmd("time set 11000");
            cleanHud = true;
            mc.options.hideGui = false;
            BlockPos ground = surface(-864, -378);
            cmd(String.format(Locale.ROOT, "tp @s -864.5 %d -370.5 180 8", ground.getY() + 1));
        });
        add(80, () -> shot("g_eyes_village"));
        add(5, () -> {
            BlockPos ground = surface(-870, -390);
            cmd(String.format(Locale.ROOT, "tp @s -870.5 %d -382.5 160 6", ground.getY() + 1));
        });
        add(60, () -> shot("g_eyes_village2"));
        // 5. the mod's screens over the world, none of the vanilla HUD
        add(5, () -> {
            cmd("time set 6000");
            cmd("tp @s -840.5 72 -352.5 90 0");
        });
        add(40, () -> {
            select(slotOf(st -> st.is(AiCompanion.DEATH_NOTE.get())));
            ClientKira.openNote(InteractionHand.MAIN_HAND);
        });
        String name = "Kenji Arai";
        for (int i = 0; i < name.length(); i++) {
            int k = i;
            add(1, () -> {
                if (mc.screen != null) {
                    mc.screen.charTyped(name.charAt(k), 0);
                }
            });
        }
        add(20, () -> shot("g_ui_note"));
        add(5, () -> {
            mc.setScreen(null);
            StoryHud.show(new Network.Broadcast(Network.Broadcast.NEWS, Component.translatable("aicompanion.card.wanted"),
                    Component.translatable("aicompanion.tv.wanted", "Kenji Arai", Component.translatable("aicompanion.crime3")),
                    Network.face("mafia_1"), Network.Broadcast.NOWHERE));
        });
        add(40, () -> shot("g_ui_news"));
        add(5, () -> KeyMapping.click(StoryHud.NEXT.getKey()));
        add(30, () -> mc.setScreen(new TvShowScreen(new Network.Broadcast(Network.Broadcast.SHOW, Component.translatable("aicompanion.card.special"),
                Component.empty(), Network.face("tailor_0"), Network.Broadcast.NOWHERE))));
        add(70, () -> shot("g_ui_tailor"));
        add(5, () -> mc.setScreen(new TvShowScreen(new Network.Broadcast(Network.Broadcast.SHOW, Component.translatable("aicompanion.card.special"),
                Component.empty(), "L", Network.Broadcast.NOWHERE))));
        add(60, () -> shot("g_ui_L"));
        add(5, () -> mc.setScreen(new PactScreen(null)));
        add(30, () -> shot("g_ui_pact"));
        add(5, () -> mc.setScreen(new EyeDealScreen()));
        add(30, () -> shot("g_ui_eyedeal"));
        // the dice table: the cup shaken, slammed, the call, the dice, the payout, him eating your apples
        add(5, () -> {
            mc.setScreen(null);
            cmd("give @s minecraft:apple 16");
        });
        add(20, () -> cmd("companion dice"));
        waitFor(100, () -> mc.screen instanceof DiceScreen, () -> AiCompanion.LOGGER.info("AUTOTEST gallery: the dice table {}", mc.screen));
        add(20, () -> key(GLFW.GLFW_KEY_UP));
        add(6, () -> key(GLFW.GLFW_KEY_UP));
        add(20, () -> key(GLFW.GLFW_KEY_ENTER));
        add(20, () -> shot("g_dice_shake"));
        add(24, () -> shot("g_dice_slam"));
        for (int i = 0; i < 8; i++) {
            add(i == 0 ? 8 : 5, () -> key(GLFW.GLFW_KEY_LEFT)); // "even", once the cup is down
        }
        add(2, () -> shot("g_dice_roll"));
        add(22, () -> shot("g_dice_result"));
        add(12, () -> shot("g_dice_eat"));
        add(20, () -> mc.setScreen(null));
        // 6. every recipe of the mod on a crafting table, the result's name shown; then the television itself, switched on
        add(10, () -> {
            cmd("clear @s");
            cmd("tp @s -840.5 72 -352.5 90 30");
        });
        add(20, () -> {
            BlockPos at = mc.player.blockPosition().relative(net.minecraft.core.Direction.WEST, 2);
            cmd(String.format(Locale.ROOT, "setblock %d %d %d minecraft:crafting_table", at.getX(), at.getY(), at.getZ()));
        });
        craft("g_craft_tv", new String[][]{{"minecraft:iron_ingot", "1,2,3,4,6"}, {"minecraft:black_dye", "5"}});
        craft("g_craft_chocolate", new String[][]{{"minecraft:cocoa_beans", "1,2"}, {"minecraft:sugar", "4"}, {"minecraft:milk_bucket", "5"}});
        craft("g_craft_chips", new String[][]{{"minecraft:baked_potato", "1,2"}, {"minecraft:paper", "5"}});
        craft("g_craft_chips_page", new String[][]{{"aicompanion:potato_chips", "4"}, {"aicompanion:death_note_page", "5"}});
        craft("g_craft_watch", new String[][]{{"minecraft:clock", "4"}, {"aicompanion:death_note_page", "5"}, {"minecraft:gold_ingot", "6"}});
        craft("g_craft_drawer", new String[][]{{"minecraft:oak_planks", "1,2,3,4,6,7,9"}, {"minecraft:book", "5"}, {"minecraft:flint_and_steel", "8"}});
        add(10, () -> {
            cleanHud = false;
            mc.options.hideGui = true;
            cmd("clear @s");
            cmd("time set 13000");
            BlockPos at = mc.player.blockPosition().relative(net.minecraft.core.Direction.WEST, 2);
            cmd(String.format(Locale.ROOT, "setblock %d %d %d aicompanion:television[facing=east,lit=true]", at.getX(), at.getY(), at.getZ()));
            r[0] = Vec3.atBottomCenterOf(at);
        });
        add(20, () -> {
            cmd("gamemode spectator");
            fovOverride = 40;
            camAt(r[0].add(1.9, 0.9, 0.7), r[0].add(0, 0.42, 0));
        });
        add(40, () -> shot("g_tv_set"));
        add(5, () -> camAt(r[0].add(2.6, 0.55, -1.2), r[0].add(0, 0.45, 0)));
        add(30, () -> shot("g_tv_set2"));
        // 7. by day: a page of Sidoh's notebook flutters down (its card), caught; his film; Sidoh himself
        add(10, () -> {
            cmd("gamemode creative");
            cmd("time set 4000");
            cmd(String.format(Locale.ROOT, "tp @s -834.5 %d -371.5 90 -14", surface(-835, -372).getY()));
            fovOverride = 0;
            mc.options.hideGui = false;
            cleanHud = true;
        });
        add(60, () -> cmd("companion quest sidoh"));
        add(30, () -> shot("g_sidoh_card"));
        add(30, () -> {
            LostPage p = nearestPage();
            if (p != null) {
                lookAt(p);
            }
        });
        add(10, () -> shot("g_sidoh_page"));
        add(5, () -> {
            LostPage p = nearestPage();
            if (p != null) {
                mc.gameMode.interact(mc.player, p, InteractionHand.MAIN_HAND);
            }
        });
        waitFor(100, () -> Cinema.now() != null, () -> AiCompanion.LOGGER.info("AUTOTEST gallery: Sidoh's film {}", Cinema.now()));
        add(60, () -> shot("g_sidoh_film"));
        add(30, () -> shot("g_sidoh_film2"));
        waitFor(300, () -> Cinema.now() == null, () -> { });
        add(30, () -> {
            mc.options.hideGui = true;
            cleanHud = false;
            cmd("gamemode spectator");
            fovOverride = 50;
            SidohEntity s = nearestSidoh();
            r[0] = s == null ? mc.player.position() : s.position();
        });
        add(5, () -> {
            SidohEntity s = nearestSidoh();
            Vec3 c = s == null ? r[0] : s.position();
            double a = Math.toRadians(-40);
            camAt(new Vec3(c.x - Math.sin(a) * 3.4, c.y + 1.4, c.z + Math.cos(a) * 3.4), c.add(0, 1.2, 0));
        });
        add(30, () -> shot("g_sidoh"));
        // 8. the realm, its films already seen (they'd take the camera)
        add(10, () -> {
            cmd("gamemode creative");
            onServer(() -> {
                for (String f : new String[]{"film_realm", "film_god.gamblers", "film_god.armonia", "film_god.king", "film_god.sidoh", "film_person.l"}) {
                    Kira.of(me()).flags.put(f, 0L);
                }
                return 0;
            });
        });
        add(10, () -> cmd("companion kira dream"));
        waitFor(400, () -> mc.level.dimension() == ru.maksim.aicompanion.Realm.KEY, () -> { });
        add(200, () -> {
            cmd("gamemode spectator");
            fovOverride = 62;
        });
        String[] realm = {"0.5 51.9 -9.5 0 -5 g_realm_road", "0.5 55.0 17.5 0 -26 g_realm_king", "16.5 52 2.5 180 -12 g_realm_armonia",
                "-38.5 52.4 -2.5 90 -14 g_realm_note", "-16.0 53.62 3.5 180 4 g_realm_gods", "20.5 51 14.5 -45 -12 g_realm_hourglass",
                "4.5 58 -8.5 160 62 g_realm_window", "-41.5 51 -31.5 -80 5 g_realm_sidoh"};
        for (String s : realm) {
            String[] q = s.split(" ");
            add(15, () -> cam(new Vec3(Double.parseDouble(q[0]), Double.parseDouble(q[1]), Double.parseDouble(q[2])), Float.parseFloat(q[3]),
                    Float.parseFloat(q[4])));
            add(30, () -> shot(q[5]));
        }
        add(10, () -> {
            cmd("gamemode creative");
            cmd("companion kira wake");
        });
        waitFor(300, () -> mc.level.dimension() != ru.maksim.aicompanion.Realm.KEY, () -> { });
        // 9. L's tower by night: from the street, and inside behind him at his screens
        add(20, () -> {
            cmd("time set 18000");
            cmd("companion follow");
            cmd("tp @s -840.5 72 -352.5 0 0");
            cmd("companion kira hq");
        });
        add(200, () -> {
            cmd("gamemode spectator");
            fovOverride = 60;
        });
        add(10, () -> {
            BlockPos hq = flag("hq");
            cam(Vec3.atLowerCornerOf(hq).add(0.5, 7.6, 40.5), 180, -14);
        });
        add(60, () -> shot("g_hq"));
        add(5, () -> {
            BlockPos hq = flag("hq");
            cam(Vec3.atLowerCornerOf(hq).add(0.5, 8.8, 4.2), 180, 12);
        });
        add(40, () -> shot("g_hq_L"));
        // 10. the green studio: lime all round, noon, no shadows, long lenses; him whole, then his face close for the icon
        add(10, () -> {
            cmd("gamemode creative");
            cmd("time set 6000");
            cmd("forceload add 2990 2990 3010 3010");
        });
        add(60, () -> {
            for (String c : new String[]{"fill 2990 195 2990 3010 195 3010 minecraft:lime_concrete", "fill 2990 196 2990 3010 216 2990 minecraft:lime_concrete",
                    "fill 2990 196 3010 3010 216 3010 minecraft:lime_concrete", "fill 2990 196 2990 2990 216 3010 minecraft:lime_concrete",
                    "fill 3010 196 2990 3010 216 3010 minecraft:lime_concrete", "gamemode spectator", "tp @s 3000.5 198.2 3005.7 180 0"}) {
                cmd(c);
            }
            mc.options.entityShadows().set(false);
            fovOverride = 40;
        });
        add(60, () -> {
            cmd("tp @e[type=aicompanion:companion] 3000.5 198.5 3000.5 0 0");
            cmd("companion hold");
        });
        add(40, () -> cam(new Vec3(3000.5, 199.85, 3005.7), 180, 0));
        add(20, () -> shot("g_gs_idle"));
        for (String p : new String[]{"show_wings", "cackle", "rub_hands", "point"}) {
            add(10, () -> cmd("companion action " + p));
            add(22, () -> shot("g_gs_" + p));
            add(40, () -> { });
        }
        add(10, () -> {
            fovOverride = 30;
            cam(new Vec3(3000.5, 200.55, 3002.4), 180, 0); // his face: about 1.9 blocks off, a long lens
        });
        add(30, () -> shot("g_gs_face"));
        add(5, () -> cmd("companion action cackle"));
        add(16, () -> shot("g_gs_face_cackle"));
        add(50, () -> cam(new Vec3(2999.6, 200.5, 3002.5), 200, 2)); // three quarters
        add(30, () -> shot("g_gs_face_side"));
        add(20, () -> {
            cmd("forceload remove 2990 2990 3010 3010");
            mc.options.entityShadows().set(true);
            mc.options.bobView().set(true);
            fovOverride = 0;
            cleanHud = false;
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /**
     * The gallery's retakes (env AICOMPANION_AUTOTEST=gallery2, the skin as for =gallery): the recipes without the
     * game's toasts over them (recipes unlocked first, toasts cleared before each), the names and the years over three
     * villagers standing close, the red eyes at dusk, Sidoh's page caught once it has come down within reach (then his
     * film and Sidoh close), L at his screens from behind and from the front, and Ryuk's whole head for the icon.
     */
    private static void galleryRetake() {
        Minecraft mc = Minecraft.getInstance();
        Vec3[] r = new Vec3[1];
        add(20, AutoTest::toBiggestMonitor);
        add(30, () -> mc.getWindow().toggleFullScreen());
        add(40, () -> {
            mc.options.hideGui = true;
            mc.options.bobView().set(false);
            for (String c : new String[]{"weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
                    "gamerule sendCommandFeedback false", "gamerule announceAdvancements false", "time set 6000", "gamemode creative",
                    "tp @s -840.5 72 -352.5 90 30", "recipe give @s *", "advancement grant @s everything"}) {
                cmd(c);
            }
        });
        add(60, () -> {
            wearSkin();
            mc.getToasts().clear();
        });
        add(20, () -> cmd("give @s aicompanion:death_note")); // Ryuk comes (his film, unfilmed)
        waitFor(600, () -> "ryuk".equals(Cinema.now()), () -> { });
        waitFor(400, () -> Cinema.now() == null, () -> { });
        add(160, () -> mc.getToasts().clear());
        // 1. the recipes, clean
        add(10, () -> {
            cleanHud = true;
            mc.options.hideGui = false;
            cmd("clear @s");
            cmd("tp @s -840.5 72 -352.5 90 30");
        });
        add(20, () -> {
            BlockPos at = mc.player.blockPosition().relative(net.minecraft.core.Direction.WEST, 2);
            cmd(String.format(Locale.ROOT, "setblock %d %d %d minecraft:crafting_table", at.getX(), at.getY(), at.getZ()));
        });
        craft("g_craft_tv", new String[][]{{"minecraft:iron_ingot", "1,2,3,4,6"}, {"minecraft:black_dye", "5"}});
        craft("g_craft_chocolate", new String[][]{{"minecraft:cocoa_beans", "1,2"}, {"minecraft:sugar", "4"}, {"minecraft:milk_bucket", "5"}});
        craft("g_craft_chips", new String[][]{{"minecraft:baked_potato", "1,2"}, {"minecraft:paper", "5"}});
        craft("g_craft_chips_page", new String[][]{{"aicompanion:potato_chips", "4"}, {"aicompanion:death_note_page", "5"}});
        craft("g_craft_watch", new String[][]{{"minecraft:clock", "4"}, {"aicompanion:death_note_page", "5"}, {"minecraft:gold_ingot", "6"}});
        craft("g_craft_drawer", new String[][]{{"minecraft:oak_planks", "1,2,3,4,6,7,9"}, {"minecraft:book", "5"}, {"minecraft:flint_and_steel", "8"}});
        // 2. the eyes: three villagers close, facing you, their names and years over their heads
        add(10, () -> {
            cmd("clear @s");
            cmd("time set 11400");
            onServer(() -> {
                Kira.Case c = Kira.of(me());
                c.eyes = Math.max(1, c.eyes);
                Kira.changed(me());
                return 0;
            });
            int y = surface(-846, -360).getY();
            cmd(String.format(Locale.ROOT, "tp @s -845.5 %d -359.5 90 4", y));
            String[] prof = {"farmer", "librarian", "cleric"};
            double[][] at = {{-850.0, -361.2}, {-851.2, -359.2}, {-849.6, -357.4}};
            for (int i = 0; i < 3; i++) {
                int gy = surface((int) Math.floor(at[i][0]), (int) Math.floor(at[i][1])).getY();
                cmd(String.format(Locale.ROOT, "summon minecraft:villager %.1f %d %.1f {NoAI:1b,Rotation:[-90f,0f],VillagerData:{profession:\"minecraft:%s\",level:2,type:\"minecraft:plains\"}}",
                        at[i][0], gy, at[i][1], prof[i]));
            }
        });
        add(20, () -> {
            mc.player.getAbilities().flying = false;
            mc.player.onUpdateAbilities();
            cleanHud = true;
            mc.options.hideGui = true;
            mc.getToasts().clear();
        });
        add(80, () -> shot("g_eyes_names"));
        add(5, () -> mc.options.hideGui = false);
        add(20, () -> shot("g_eyes_names_hud"));
        // 3. the red eyes at dusk, his shape over your shoulder
        add(5, () -> {
            mc.options.hideGui = true;
            cleanHud = false;
            cmd("kill @e[type=minecraft:villager,distance=..12]");
            cmd("time set 13050");
            cmd(String.format(Locale.ROOT, "tp @e[type=aicompanion:companion] %.2f %.2f %.2f 90 0", mc.player.getX() + 1.3, mc.player.getY() + 0.5,
                    mc.player.getZ() + 0.4));
            cmd("companion hold");
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            fovOverride = 34;
            mc.player.setYRot(92);
            mc.player.setXRot(2);
        });
        add(50, () -> shot("g_eyes_dusk"));
        add(5, () -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            fovOverride = 0;
            cmd("companion follow");
        });
        // 4. Sidoh: the page followed down until it's within reach, caught; his film; Sidoh close
        add(10, () -> {
            cmd("time set 4000");
            cmd(String.format(Locale.ROOT, "tp @s -834.5 %d -371.5 90 -14", surface(-835, -372).getY()));
        });
        add(60, () -> cmd("companion quest sidoh"));
        add(4, () -> {
            for (int i = 0; i < 3; i++) {
                KeyMapping.click(StoryHud.NEXT.getKey());
            }
        });
        for (int i = 0; i < 110; i++) {
            add(1, () -> {
                LostPage p = nearestPage();
                if (p != null) {
                    follow(p.position(), 0.25F);
                }
            });
        }
        add(1, () -> shot("g_sidoh_page"));
        add(2, () -> {
            LostPage p = nearestPage();
            AiCompanion.LOGGER.info("AUTOTEST gallery2: the page {} at {} blocks", p, p == null ? -1 : p.distanceTo(mc.player));
            if (p != null) {
                mc.gameMode.interact(mc.player, p, InteractionHand.MAIN_HAND);
            }
        });
        waitFor(100, () -> Cinema.now() != null, () -> AiCompanion.LOGGER.info("AUTOTEST gallery2: Sidoh's film {}", Cinema.now()));
        add(40, () -> shot("g_sidoh_film"));
        add(30, () -> shot("g_sidoh_film2"));
        waitFor(300, () -> Cinema.now() == null, () -> { });
        add(30, () -> {
            cmd("gamemode spectator");
            fovOverride = 46;
        });
        add(5, () -> {
            SidohEntity s = nearestSidoh();
            AiCompanion.LOGGER.info("AUTOTEST gallery2: Sidoh {}", s == null ? "nowhere" : s.position());
            if (s != null) {
                double a = Math.toRadians(s.getYRot());
                Vec3 c = s.position();
                camAt(new Vec3(c.x - Math.sin(a) * 3.2, c.y + 1.5, c.z + Math.cos(a) * 3.2), c.add(0, 1.3, 0)); // in front of him
            }
        });
        add(20, () -> shot("g_sidoh"));
        add(5, () -> {
            SidohEntity s = nearestSidoh();
            if (s != null) {
                double a = Math.toRadians(s.getYRot() + 35);
                Vec3 c = s.position();
                camAt(new Vec3(c.x - Math.sin(a) * 2.6, c.y + 1.2, c.z + Math.cos(a) * 2.6), c.add(0, 1.4, 0));
            }
        });
        add(20, () -> shot("g_sidoh2"));
        // 5. L at his screens: from behind him, then his face
        add(10, () -> {
            cmd("gamemode creative");
            cmd("time set 18000");
            cmd("tp @s -840.5 72 -352.5 0 0");
            cmd("companion kira hq");
        });
        add(200, () -> {
            cmd("gamemode spectator");
            fovOverride = 58;
            BlockPos hq = flag("hq");
            cam(Vec3.atLowerCornerOf(hq).add(0.5, 8.8, 4.2), 180, 12);
        });
        add(40, () -> {
            HumanEntity l = person("l");
            AiCompanion.LOGGER.info("AUTOTEST gallery2: L {}", l == null ? "nowhere" : l.position() + " yaw " + l.getYRot());
            if (l != null) {
                double a = Math.toRadians(l.getYRot());
                Vec3 fwd = new Vec3(-Math.sin(a), 0, Math.cos(a)), c = l.position();
                camAt(c.subtract(fwd.scale(2.6)).add(0.6, 1.9, 0.6), c.add(fwd.scale(4.0)).add(0, 1.2, 0));
            }
        });
        add(30, () -> shot("g_hq_L_behind"));
        add(5, () -> {
            HumanEntity l = person("l");
            if (l != null) {
                double a = Math.toRadians(l.getYRot());
                Vec3 fwd = new Vec3(-Math.sin(a), 0, Math.cos(a)), c = l.position();
                camAt(c.add(fwd.scale(2.4)).add(0, 1.3, 0), c.add(0, 1.1, 0));
            }
        });
        add(30, () -> shot("g_hq_L_front"));
        // 6. the green studio: his whole head, for the icon
        add(10, () -> {
            cmd("gamemode creative");
            cmd("time set 6000");
            cmd("forceload add 2990 2990 3010 3010");
        });
        add(60, () -> {
            for (String c : new String[]{"fill 2990 195 2990 3010 195 3010 minecraft:lime_concrete", "fill 2990 196 2990 3010 216 2990 minecraft:lime_concrete",
                    "fill 2990 196 3010 3010 216 3010 minecraft:lime_concrete", "fill 2990 196 2990 2990 216 3010 minecraft:lime_concrete",
                    "fill 3010 196 2990 3010 216 3010 minecraft:lime_concrete", "gamemode spectator", "tp @s 3000.5 198.2 3005.7 180 0"}) {
                cmd(c);
            }
            mc.options.entityShadows().set(false);
            fovOverride = 30;
        });
        add(60, () -> {
            cmd("tp @e[type=aicompanion:companion] 3000.5 198.5 3000.5 0 0");
            cmd("companion hold");
        });
        add(40, () -> cam(new Vec3(3000.5, 200.45, 3003.6), 180, 0));
        add(30, () -> shot("g_gs_head"));
        add(5, () -> cam(new Vec3(3001.6, 200.45, 3003.3), 162, 0));
        add(30, () -> shot("g_gs_head_side"));
        add(20, () -> {
            cmd("forceload remove 2990 2990 3010 3010");
            mc.options.entityShadows().set(true);
            mc.options.bobView().set(true);
            fovOverride = 0;
            cleanHud = false;
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /**
     * The gallery's last retakes (env AICOMPANION_AUTOTEST=gallery3, the skin as for =gallery): the recipes bare (no
     * tooltip, no toasts), L at his screens with nobody in the way, and the green studio in night vision (his face lit).
     */
    private static void galleryRetake2() {
        Minecraft mc = Minecraft.getInstance();
        add(20, AutoTest::toBiggestMonitor);
        add(30, () -> mc.getWindow().toggleFullScreen());
        add(40, () -> {
            mc.options.hideGui = true;
            mc.options.bobView().set(false);
            for (String c : new String[]{"weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false",
                    "gamerule sendCommandFeedback false", "gamerule announceAdvancements false", "time set 6000", "gamemode creative",
                    "tp @s -840.5 72 -352.5 90 30", "recipe give @s *", "advancement grant @s everything"}) {
                cmd(c);
            }
        });
        add(60, () -> {
            wearSkin();
            mc.getToasts().clear();
        });
        add(20, () -> cmd("give @s aicompanion:death_note"));
        waitFor(600, () -> "ryuk".equals(Cinema.now()), () -> { });
        waitFor(400, () -> Cinema.now() == null, () -> { });
        add(160, () -> mc.getToasts().clear());
        add(10, () -> {
            cleanHud = true;
            mc.options.hideGui = false;
            cmd("clear @s");
            cmd("tp @s -840.5 72 -352.5 90 30");
        });
        add(20, () -> {
            BlockPos at = mc.player.blockPosition().relative(net.minecraft.core.Direction.WEST, 2);
            cmd(String.format(Locale.ROOT, "setblock %d %d %d minecraft:crafting_table", at.getX(), at.getY(), at.getZ()));
        });
        craft("g_bare_tv", new String[][]{{"minecraft:iron_ingot", "1,2,3,4,6"}, {"minecraft:black_dye", "5"}}, false);
        craft("g_bare_chocolate", new String[][]{{"minecraft:cocoa_beans", "1,2"}, {"minecraft:sugar", "4"}, {"minecraft:milk_bucket", "5"}}, false);
        craft("g_bare_chips", new String[][]{{"minecraft:baked_potato", "1,2"}, {"minecraft:paper", "5"}}, false);
        craft("g_bare_chips_page", new String[][]{{"aicompanion:potato_chips", "4"}, {"aicompanion:death_note_page", "5"}}, false);
        craft("g_bare_watch", new String[][]{{"minecraft:clock", "4"}, {"aicompanion:death_note_page", "5"}, {"minecraft:gold_ingot", "6"}}, false);
        craft("g_bare_drawer", new String[][]{{"minecraft:oak_planks", "1,2,3,4,6,7,9"}, {"minecraft:book", "5"}, {"minecraft:flint_and_steel", "8"}}, false);
        // L at his screens: from behind him, then his face
        add(10, () -> {
            cleanHud = false;
            mc.options.hideGui = true;
            cmd("clear @s");
            cmd("time set 18000");
            cmd("tp @s -840.5 72 -352.5 0 0");
            cmd("companion kira hq");
        });
        add(200, () -> {
            cmd("gamemode spectator");
            fovOverride = 58;
            BlockPos hq = flag("hq");
            cam(Vec3.atLowerCornerOf(hq).add(0.5, 8.8, 4.2), 180, 12);
        });
        add(40, () -> {
            HumanEntity l = person("l");
            AiCompanion.LOGGER.info("AUTOTEST gallery3: L {}", l == null ? "nowhere" : l.position() + " yaw " + l.getYRot());
            if (l != null) {
                double a = Math.toRadians(l.getYRot());
                Vec3 fwd = new Vec3(-Math.sin(a), 0, Math.cos(a)), c = l.position();
                camAt(c.subtract(fwd.scale(2.4)).add(0.9, 1.8, 0.0), c.add(fwd.scale(4.0)).add(0, 1.3, 0));
            }
        });
        add(30, () -> shot("g_hq_L_behind"));
        add(5, () -> {
            HumanEntity l = person("l");
            if (l != null) {
                double a = Math.toRadians(l.getYRot());
                Vec3 fwd = new Vec3(-Math.sin(a), 0, Math.cos(a)), c = l.position();
                camAt(c.add(fwd.scale(2.2)).add(0.5, 1.25, 0), c.add(0, 1.05, 0));
            }
        });
        add(30, () -> shot("g_hq_L_front"));
        // the green studio, in night vision: his face lit
        add(10, () -> {
            cmd("gamemode creative");
            cmd("time set 6000");
            cmd("effect give @s minecraft:night_vision 99999 0 true");
            cmd("forceload add 2990 2990 3010 3010");
        });
        add(60, () -> {
            for (String c : new String[]{"fill 2990 195 2990 3010 195 3010 minecraft:lime_concrete", "fill 2990 196 2990 3010 216 2990 minecraft:lime_concrete",
                    "fill 2990 196 3010 3010 216 3010 minecraft:lime_concrete", "fill 2990 196 2990 2990 216 3010 minecraft:lime_concrete",
                    "fill 3010 196 2990 3010 216 3010 minecraft:lime_concrete", "gamemode spectator", "tp @s 3000.5 198.2 3005.7 180 0"}) {
                cmd(c);
            }
            mc.options.entityShadows().set(false);
            fovOverride = 40;
        });
        add(60, () -> {
            cmd("tp @e[type=aicompanion:companion] 3000.5 198.5 3000.5 0 0");
            cmd("companion hold");
        });
        add(40, () -> cam(new Vec3(3000.5, 199.85, 3005.7), 180, 0));
        add(20, () -> shot("g_gs_idle"));
        for (String p : new String[]{"show_wings", "cackle", "rub_hands", "point"}) {
            add(10, () -> cmd("companion action " + p));
            add(22, () -> shot("g_gs_" + p));
            add(40, () -> { });
        }
        add(10, () -> {
            fovOverride = 30;
            cam(new Vec3(3000.5, 200.45, 3003.6), 180, 0);
        });
        add(30, () -> shot("g_gs_head"));
        add(5, () -> cam(new Vec3(3001.6, 200.45, 3003.3), 162, 0));
        add(30, () -> shot("g_gs_head_side"));
        add(5, () -> {
            cam(new Vec3(3000.5, 200.45, 3003.6), 180, 0);
            cmd("companion action cackle");
        });
        add(16, () -> shot("g_gs_head_cackle"));
        add(20, () -> {
            cmd("forceload remove 2990 2990 3010 3010");
            mc.options.entityShadows().set(true);
            mc.options.bobView().set(true);
            fovOverride = 0;
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /**
     * The mod as the mods list shows it (env AICOMPANION_AUTOTEST=modlist): Forge's list opened, this mod selected (its
     * name, logo, description and credits on the right). Shot autotest_modlist.png.
     */
    private static void modList() {
        Minecraft mc = Minecraft.getInstance();
        net.minecraftforge.client.gui.ModListScreen[] screen = new net.minecraftforge.client.gui.ModListScreen[1];
        add(60, () -> {
            screen[0] = new net.minecraftforge.client.gui.ModListScreen(null);
            mc.setScreen(screen[0]);
        });
        add(20, () -> {
            try {
                java.lang.reflect.Field f = net.minecraftforge.client.gui.ModListScreen.class.getDeclaredField("modList");
                f.setAccessible(true);
                var list = (net.minecraftforge.client.gui.widget.ModListWidget) f.get(screen[0]);
                for (var e : list.children()) {
                    if (e.getInfo().getModId().equals(AiCompanion.MODID)) {
                        screen[0].setSelected(e);
                        AiCompanion.LOGGER.info("AUTOTEST modlist: {} {}", e.getInfo().getDisplayName(), e.getInfo().getVersion());
                    }
                }
            } catch (ReflectiveOperationException e) {
                AiCompanion.LOGGER.error("AUTOTEST modlist: no list", e);
            }
            GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 4, 4);
        });
        add(30, () -> shot("modlist"));
        add(10, () -> {
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    private static void key(int glfwKey) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            mc.screen.keyPressed(glfwKey, 0, 0);
        }
    }

    /**
     * A recipe laid out on the crafting table two blocks west of the player and photographed with the result's name over
     * it: {@code items} = {item id, grid slots 1-9 (row by row), comma-separated}; each item goes to its own hotbar slot,
     * is picked up as a stack and right-clicked into its cells one by one, the rest put back. Closed after: the grid
     * goes back to the inventory, which is cleared for the next one.
     */
    private static void craft(String shot, String[][] items) {
        craft(shot, items, true);
    }

    /** {@link #craft(String, String[][])}; {@code hover} false: the mouse in the window's corner, no tooltip over the grid. */
    private static void craft(String shot, String[][] items, boolean hover) {
        Minecraft mc = Minecraft.getInstance();
        add(10, () -> {
            cmd("clear @s");
            for (int i = 0; i < items.length; i++) {
                cmd("item replace entity @s hotbar." + i + " with " + items[i][0] + " " + Math.max(1, items[i][1].split(",").length));
            }
        });
        add(20, () -> {
            BlockPos at = mc.player.blockPosition().relative(net.minecraft.core.Direction.WEST, 2);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                    new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(at), net.minecraft.core.Direction.EAST, at, false));
        });
        waitFor(60, () -> mc.screen instanceof net.minecraft.client.gui.screens.inventory.CraftingScreen,
                () -> AiCompanion.LOGGER.info("AUTOTEST gallery: crafting table {}", mc.screen));
        add(10, () -> {
            if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> s)) {
                return;
            }
            int id = s.getMenu().containerId;
            for (int i = 0; i < items.length; i++) {
                mc.gameMode.handleInventoryMouseClick(id, 37 + i, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
                for (String cell : items[i][1].split(",")) {
                    mc.gameMode.handleInventoryMouseClick(id, Integer.parseInt(cell.trim()), 1, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
                }
                mc.gameMode.handleInventoryMouseClick(id, 37 + i, 0, net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
            }
        });
        add(10, () -> {
            if (hover) {
                hoverSlot(0);
            } else {
                GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 4, 4);
            }
            mc.getToasts().clear(); // "new recipes", "advancement made": the game's, not the picture's
        });
        add(10, () -> {
            if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> s) {
                AiCompanion.LOGGER.info("AUTOTEST gallery: {} gives {}", shot, s.getMenu().getSlot(0).getItem());
            }
            mc.getToasts().clear();
            shot(shot);
        });
        add(5, () -> {
            if (mc.player != null) {
                mc.player.closeContainer();
            }
        });
    }

    private static double smooth01(double k) {
        k = Math.max(0, Math.min(1, k));
        return k * k * (3 - 2 * k);
    }

    /** The portrait pipeline tried on its own (env AICOMPANION_AUTOTEST=shortstest): the biggest screen, 40 frames, the timing. */
    private static void shortsTest() {
        Minecraft mc = Minecraft.getInstance();
        long[] t0 = new long[1];
        add(20, AutoTest::toBiggestMonitor);
        add(30, () -> mc.getWindow().toggleFullScreen());
        add(60, () -> {
            mc.options.hideGui = true;
            for (String c : new String[]{"time set 12600", "weather clear", "gamerule doDaylightCycle false", "tp @s -840.5 72 -352.5 90 -4"}) {
                cmd(c);
            }
        });
        add(80, () -> {
            var t = mc.getMainRenderTarget();
            AiCompanion.LOGGER.info("AUTOTEST shortstest: framebuffer {}x{}, window {}x{}, fullscreen {}", t.width, t.height, mc.getWindow().getWidth(),
                    mc.getWindow().getHeight(), mc.getWindow().isFullscreen());
            t0[0] = net.minecraft.Util.getMillis();
        });
        vclip("test", 40, true, i -> mc.player.setYRot(90 + i));
        add(60, () -> {
            AiCompanion.LOGGER.info("AUTOTEST shortstest: 40 frames in {} ms, fps {}", net.minecraft.Util.getMillis() - t0[0], mc.getFps());
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /**
     * The player in a real player's skin and cape (env AICOMPANION_SKIN, _SKIN_ID, _CAPE, _CAPE_ID, _SKIN_MODEL): the
     * textures under the names the game itself gives them (skins/ + sha1 of the texture's hash), so the eyes saved for
     * that skin apply as they do in the player's game. Dev client only: the player info's fields by their Mojang names.
     */
    @SuppressWarnings("unchecked")
    private static void wearSkin() {
        String skin = System.getenv("AICOMPANION_SKIN");
        if (skin == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        try {
            net.minecraft.client.multiplayer.PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
            Class<?> c = net.minecraft.client.multiplayer.PlayerInfo.class;
            java.lang.reflect.Field locations = c.getDeclaredField("textureLocations"), pending = c.getDeclaredField("pendingTextures"),
                    model = c.getDeclaredField("skinModel");
            locations.setAccessible(true);
            pending.setAccessible(true);
            model.setAccessible(true);
            pending.setBoolean(info, true); // the offline profile's own (default) textures aren't looked up over these
            var map = (java.util.Map<com.mojang.authlib.minecraft.MinecraftProfileTexture.Type, net.minecraft.resources.ResourceLocation>) locations.get(info);
            map.put(com.mojang.authlib.minecraft.MinecraftProfileTexture.Type.SKIN, texture(skin, System.getenv("AICOMPANION_SKIN_ID"), true));
            String cape = System.getenv("AICOMPANION_CAPE");
            if (cape != null) {
                map.put(com.mojang.authlib.minecraft.MinecraftProfileTexture.Type.CAPE, texture(cape, System.getenv("AICOMPANION_CAPE_ID"), false));
            }
            String m = System.getenv("AICOMPANION_SKIN_MODEL");
            model.set(info, m == null ? "default" : m);
        } catch (ReflectiveOperationException | IOException e) {
            AiCompanion.LOGGER.error("AUTOTEST could not put the skin on", e);
        }
    }

    private static net.minecraft.resources.ResourceLocation texture(String file, String id, boolean skin) throws IOException {
        com.mojang.blaze3d.platform.NativeImage img;
        try (java.io.InputStream in = Files.newInputStream(Path.of(file))) {
            img = com.mojang.blaze3d.platform.NativeImage.read(in);
        }
        if (skin && img.getHeight() == 64) { // what the game does to a downloaded skin: the base layer made opaque
            int[][] opaque = {{0, 0, 32, 16}, {0, 16, 64, 32}, {16, 48, 48, 64}};
            for (int[] r : opaque) {
                for (int x = r[0]; x < r[2]; x++) {
                    for (int y = r[1]; y < r[3]; y++) {
                        img.setPixelRGBA(x, y, img.getPixelRGBA(x, y) | 0xFF000000);
                    }
                }
            }
        }
        net.minecraft.resources.ResourceLocation at = new net.minecraft.resources.ResourceLocation("minecraft", "skins/" + id);
        Minecraft.getInstance().getTextureManager().register(at, new net.minecraft.client.renderer.texture.DynamicTexture(img));
        return at;
    }

    /** The camera {@code dist} blocks from Ryuk, {@code angle} degrees round from his front, {@code dy} over his feet, on his chest. */
    private static void frame(double dist, double angle, double dy) {
        CompanionEntity r = ryuk();
        double a = Math.toRadians(r.getYRot() + angle);
        Vec3 eye = new Vec3(r.getX() - Math.sin(a) * dist, r.getY() + dy + 1.62, r.getZ() + Math.cos(a) * dist);
        Vec3 d = new Vec3(r.getX(), r.getY() + 1.7, r.getZ()).subtract(eye);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
        Minecraft.getInstance().player.getAbilities().flying = true;
        Minecraft.getInstance().player.onUpdateAbilities();
        cmd(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f %.1f %.1f", eye.x, eye.y - 1.62, eye.z, yaw, pitch));
    }

    /** The story test may go on: nothing on the screen (an offer on the way answered: no game, yes to the eyes), no film, no card. */
    private static boolean settled(Minecraft mc) {
        if (mc.screen instanceof PactScreen) {
            Network.CHANNEL.sendToServer(new Network.Answer(Network.Choice.PACT, false));
            mc.setScreen(null);
        } else if (mc.screen instanceof EyeDealScreen) {
            Network.CHANNEL.sendToServer(new Network.Answer(Network.Choice.EYE_DEAL, true));
            mc.setScreen(null);
        } else if (mc.screen instanceof EyeLookScreen) {
            mc.setScreen(null);
        }
        return mc.screen == null && Cinema.now() == null && StoryHud.floorFree(mc);
    }

    /**
     * L's tower, the way up (env AICOMPANION_AUTOTEST=hqstairs): built on the flat world, its stairwell laid out in the
     * log layer by layer, then climbed the way a player would, the forward key held: from the lobby up to L's hall, on to
     * the gallery and every floor to the roof. Where the climb stops, it says so.
     */
    private static void hqStairsTest() {
        Minecraft mc = Minecraft.getInstance();
        BlockPos[] hq = new BlockPos[1];
        add(60, () -> {
            for (String c : new String[]{"time set 6000", "gamerule doDaylightCycle false", "gamerule doMobSpawning false",
                    "gamerule sendCommandFeedback false", "tp @s 0 -60 0 180 0", "companion kira hq"}) {
                cmd(c);
            }
        });
        waitFor(600, () -> onServer(() -> Kira.of(me()).flag("hq")), () -> { });
        add(200, () -> {
            hq[0] = BlockPos.of(onServer(() -> Kira.of(me()).flags.get("hq")));
            mc.player.getAbilities().flying = false;
            StringBuilder out = new StringBuilder();
            for (int y = 0; y <= 16; y++) { // x 2..8 across, z 0..8 down: . air, n/s/e/w stairs (their back), W white, A andesite, G grey, c carpet
                out.append("\n y+").append(y).append(':');
                for (int z = 0; z <= 8; z++) {
                    out.append("\n   z").append(z).append(' ');
                    for (int x = 2; x <= 8; x++) {
                        out.append(cell(mc.level.getBlockState(hq[0].offset(x, y, z))));
                    }
                }
            }
            AiCompanion.LOGGER.info("AUTOTEST hqstairs: the stairwell at {} (x 2..8):{}", hq[0], out);
        });
        // a tower raised before the fix: its slabs over the flights put back, the fix's mark off; the tower's tick must put it right
        java.util.function.Supplier<List<BlockPos>> old = () -> {
            List<BlockPos> at = new ArrayList<>();
            for (int k = 1; k <= 6; k++) {
                for (int x = 4; x <= 5; x++) {
                    at.add(hq[0].offset(x, k * 5, 4)); // the slab over flight A's second step
                }
                for (int x = 6; x <= 7 && k < 6; x++) {
                    at.add(hq[0].offset(x, k * 5 + 1, 6)); // the wall over the step off the landing onto flight B
                }
            }
            return at;
        };
        add(5, () -> onServer(() -> {
            old.get().forEach(b -> overworld().setBlock(b, net.minecraft.world.level.block.Blocks.WHITE_CONCRETE.defaultBlockState(), 2));
            Kira.of(me()).flags.remove("hq_stairs");
            return 0;
        }));
        add(60, () -> {
            long left = onServer(() -> old.get().stream().filter(b -> !overworld().getBlockState(b).isAir()).count());
            AiCompanion.LOGGER.info("AUTOTEST hqstairs: an old tower put right: {} (slabs left over the flights: {})", left == 0, left);
            if (left > 0) {
                AiCompanion.LOGGER.error("AUTOTEST hqstairs: the old tower's slabs are still there: {}", left);
            }
        });
        // in at the front door, through the metal check's arch, to the stairwell's doorway
        add(10, () -> cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 180 0", hq[0].getX() + 0.5, hq[0].getY() + 1, hq[0].getZ() + 10.5)));
        walkTo(hq, 0.5, 6.5, 80);
        walkTo(hq, 0.5, 3.5, 60);
        walkTo(hq, 2.0, 2.5, 60);
        add(5, () -> shot("hq_doorway"));
        walkTo(hq, 4.5, 2.5, 60);
        add(2, () -> {
            BlockPos c = hq[0];
            AiCompanion.LOGGER.info("AUTOTEST hqstairs: into the stairwell: at {} ({}, {}); people near: {}", mc.player.blockPosition().subtract(c),
                    String.format(Locale.ROOT, "%.2f", mc.player.getX() - c.getX()), String.format(Locale.ROOT, "%.2f", mc.player.getZ() - c.getZ()),
                    mc.level.getEntitiesOfClass(HumanEntity.class, mc.player.getBoundingBox().inflate(8)).stream()
                            .map(h -> h.getName().getString() + "@" + h.blockPosition().subtract(c)).toList());
            mc.player.setYRot(-90);
            shot("hq_into_stairwell");
        });
        // a storey's climb: in through the doorway, up flight A (south), along the landing, up flight B (north), out west
        for (int k = 0; k < 6; k++) {
            int storey = k;
            add(5, () -> mc.player.getAbilities().flying = false);
            walkTo(hq, 4.5, 2.5, 60);
            walkTo(hq, 4.5, 7.2, 80);
            add(2, () -> {
                AiCompanion.LOGGER.info("AUTOTEST hqstairs: storey {} flight A: at {} ({}, {}, {})", storey, mc.player.blockPosition().subtract(hq[0]),
                        String.format(Locale.ROOT, "%.2f", mc.player.getX() - hq[0].getX()), String.format(Locale.ROOT, "%.2f", mc.player.getY() - hq[0].getY()),
                        String.format(Locale.ROOT, "%.2f", mc.player.getZ() - hq[0].getZ()));
                shot("hq_flightA_" + storey);
            });
            walkTo(hq, 6.5, 7.2, 60);
            walkTo(hq, 6.5, 2.4, 80);
            add(2, () -> AiCompanion.LOGGER.info("AUTOTEST hqstairs: storey {} flight B: at {} (y+{})", storey, mc.player.blockPosition().subtract(hq[0]),
                    String.format(Locale.ROOT, "%.2f", mc.player.getY() - hq[0].getY())));
            walkTo(hq, 2.0, 2.4, 60);
            add(5, () -> {
                BlockPos c = hq[0];
                double up = mc.player.getY() - c.getY();
                boolean there = Math.abs(up - ((storey + 1) * 5 + 1)) < 0.6;
                AiCompanion.LOGGER.info("AUTOTEST hqstairs: storey {} -> {}: at {} (y+{}), {}", storey, storey + 1,
                        mc.player.blockPosition().subtract(c), String.format(Locale.ROOT, "%.2f", up), there ? "made it" : "STUCK");
                if (!there) {
                    AiCompanion.LOGGER.error("AUTOTEST hqstairs: the climb from storey {} stopped at {}", storey, mc.player.blockPosition().subtract(c));
                }
                shot("hq_storey" + (storey + 1));
            });
        }
        add(20, () -> {
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /** The forward key held, steering each tick toward x, z (from {@code c[0]}), till there or {@code max} ticks (stuck). */
    private static void walkTo(BlockPos[] c, double x, double z, int max) {
        Minecraft mc = Minecraft.getInstance();
        int[] left = {max};
        Runnable[] step = new Runnable[1];
        step[0] = () -> {
            double dx = c[0].getX() + x - mc.player.getX(), dz = c[0].getZ() + z - mc.player.getZ();
            if (Math.sqrt(dx * dx + dz * dz) < 0.3 || --left[0] <= 0) {
                KeyMapping.set(mc.options.keyUp.getKey(), false);
                return;
            }
            mc.player.setYRot((float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90);
            mc.player.setXRot(10);
            KeyMapping.set(mc.options.keyUp.getKey(), true);
            STEPS.addFirst(new Step(1, step[0]));
        };
        add(1, step[0]);
    }

    private static char cell(net.minecraft.world.level.block.state.BlockState s) {
        if (s.isAir()) {
            return '.';
        }
        if (s.getBlock() instanceof net.minecraft.world.level.block.StairBlock) {
            return s.getValue(net.minecraft.world.level.block.StairBlock.FACING).getName().charAt(0);
        }
        String id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(s.getBlock()).getPath();
        return id.equals("white_concrete") ? 'W' : id.equals("polished_andesite") ? 'A' : id.equals("gray_concrete") ? 'G'
                : id.equals("light_gray_concrete") ? 'g' : id.endsWith("carpet") ? 'c' : id.endsWith("glass_pane") ? '|' : id.equals("lantern") ? 'l'
                : id.equals("smooth_quartz") ? 'Q' : id.equals("sea_lantern") ? '*' : id.equals("dark_oak_planks") ? 'P' : '#';
    }

    /** Which of our themes plays now: logged, an error if not the expected one. */
    private static void theme(String where, String want) {
        String now = Soundtrack.now();
        if (want.equals(String.valueOf(now))) {
            AiCompanion.LOGGER.info("AUTOTEST music in {}: {}", where, now);
        } else {
            AiCompanion.LOGGER.error("AUTOTEST music in {}: {} instead of {}", where, now, want);
        }
    }

    /**
     * The mod's sounds (env AICOMPANION_AUTOTEST=sounds): every event of ours known to the game with every file in
     * place; each sting played; the effects played at the player; the director: a scene's theme on and off, the
     * realm's theme in the dream, L's near his tower, and quiet once away.
     */
    private static void soundsTest() {
        Minecraft mc = Minecraft.getInstance();
        add(60, () -> {
            for (String c : new String[]{"time set 6000", "weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobSpawning false", "gamerule sendCommandFeedback false", "tp @s 0 -60 0 0 0", "give @s aicompanion:death_note"}) {
                cmd(c);
            }
        });
        add(20, () -> {
            int events = 0, files = 0, missing = 0;
            for (net.minecraft.resources.ResourceLocation id : net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getKeys()) {
                if (!id.getNamespace().equals(AiCompanion.MODID)) {
                    continue;
                }
                net.minecraft.client.sounds.WeighedSoundEvents w = mc.getSoundManager().getSoundEvent(id);
                if (w == null) {
                    AiCompanion.LOGGER.error("AUTOTEST sounds: {} has no sounds.json entry", id);
                    missing++;
                    continue;
                }
                events++;
                java.util.Set<net.minecraft.resources.ResourceLocation> seen = new java.util.HashSet<>();
                for (int k = 0; k < 24; k++) {
                    net.minecraft.client.resources.sounds.Sound snd = w.getSound(net.minecraft.util.RandomSource.create(k));
                    if (snd != null && seen.add(snd.getPath())) {
                        if (mc.getResourceManager().getResource(snd.getPath()).isEmpty()) {
                            AiCompanion.LOGGER.error("AUTOTEST sounds: {} -> {} missing", id, snd.getPath());
                            missing++;
                        } else {
                            files++;
                        }
                    }
                }
            }
            AiCompanion.LOGGER.info("AUTOTEST sounds: {} events of ours, {} files found, {} missing", events, files, missing);
        });
        for (String id : ru.maksim.aicompanion.Sounds.CUES) {
            if (id.startsWith("sting.")) {
                add(30, () -> {
                    Soundtrack.sting(id);
                    AiCompanion.LOGGER.info("AUTOTEST sounds: sting {}", id);
                });
            }
        }
        for (String fx : new String[]{"heart.beat", "ryuk.poof", "tv.on", "tv.off", "page.gust",
                "page.catch", "realm.vision", "realm.branch", "realm.king", "realm.offering", "sidoh.wing", "sidoh.chocolate", "hq.detector"}) {
            add(25, () -> cmd("playsound aicompanion:" + fx + " master @s ~ ~ ~ 1 1"));
        }
        add(40, () -> Soundtrack.scene("music.special", true));
        add(60, () -> theme("a scene", "music.special"));
        add(5, () -> Soundtrack.endScene("music.special"));
        add(80, () -> theme("after the scene", "null"));
        add(20, () -> cmd("companion kira dream"));
        waitFor(400, () -> mc.level.dimension() == ru.maksim.aicompanion.Realm.KEY, () -> { });
        add(120, () -> theme("the realm", "music.realm"));
        add(20, () -> shot("snd_realm"));
        add(10, () -> cmd("tp @s 0.5 51 -13.5 180 60")); // a jump into the window: awake
        waitFor(300, () -> mc.level.dimension() != ru.maksim.aicompanion.Realm.KEY, () -> { });
        add(320, () -> theme("awake, far from anyone", "null"));
        add(20, () -> cmd("companion kira hq"));
        waitFor(1200, () -> person("l") != null, () -> {
            HumanEntity l = person("l");
            if (l != null) {
                tp(l.blockPosition().offset(0, 0, 4), 180, 0);
            }
        });
        add(120, () -> theme("L's tower", "music.l"));
        add(20, () -> {
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /**
     * A show on the screen: nothing pressed for ten seconds, still the first line; then line by line to the closing card,
     * closed, and its words in the chat.
     */
    private static void show(String n, String face) {
        Minecraft mc = Minecraft.getInstance();
        int[] before = new int[1];
        add(5, () -> {
            before[0] = TvShowScreen.transcripts;
            StoryHud.show(new Network.Broadcast(Network.Broadcast.SHOW, Component.translatable("aicompanion.card.special"), Component.empty(), face,
                    Network.Broadcast.NOWHERE));
        });
        add(40, () -> {
            shot(n + "_show");
            theme(n + " show", face.equals("L") ? "music.l" : "music.special");
        });
        add(200, () -> {
            int line = mc.screen instanceof TvShowScreen show ? show.line() : -1;
            if (line == 0) {
                AiCompanion.LOGGER.info("AUTOTEST {} show after ten idle seconds: still line {}", n, line);
            } else {
                AiCompanion.LOGGER.error("AUTOTEST {} show moved on by itself (or closed): line {}", n, line);
            }
            shot(n + "b_show_waits");
        });
        add(5, () -> {
            for (int i = 0; i < 5 && mc.screen instanceof TvShowScreen show; i++) {
                show.next();
            }
        });
        add(60, () -> shot(n + "c_show_later"));
        add(5, () -> {
            for (int i = 0; i < 20 && mc.screen instanceof TvShowScreen show && !show.ending(); i++) {
                show.next(); // on to the closing card
            }
        });
        add(20, () -> shot(n + "d_show_end"));
        add(5, () -> {
            if (mc.screen instanceof TvShowScreen show) {
                show.next(); // past the closing card: closed
            }
        });
        add(10, () -> {
            boolean told = !(mc.screen instanceof TvShowScreen) && TvShowScreen.transcripts == before[0] + 1;
            if (told) {
                AiCompanion.LOGGER.info("AUTOTEST {} show closed, its words in the chat", n);
            } else {
                AiCompanion.LOGGER.error("AUTOTEST {} show: open {}, transcripts {} (was {})", n, mc.screen instanceof TvShowScreen, TvShowScreen.transcripts, before[0]);
            }
            shot(n + "e_show_chat");
        });
    }

    /**
     * L's special on the set (the reader moves it on, a line every three seconds), its words in the chat; Tailor's name
     * written once it's over, his death on air and the real L's answer, the same way.
     */
    private static void tailorOnAir(String n) {
        Minecraft mc = Minecraft.getInstance();
        int[] before = new int[1];
        waitFor(600, () -> mc.screen instanceof TvShowScreen, () -> {
            before[0] = TvShowScreen.transcripts;
            shot(n + "a_tailor_show");
            theme("the special", "music.special");
        });
        waitFor(1500, () -> !(mc.screen instanceof TvShowScreen), () -> {
            AiCompanion.LOGGER.info("AUTOTEST the special watched: transcripts {} (was {}), the case: {}", TvShowScreen.transcripts, before[0],
                    onServer(() -> ru.maksim.aicompanion.Investigation.specialOnAir(me())));
            shot(n + "b_tailor_chat");
            select(slotOf(st -> st.is(AiCompanion.DEATH_NOTE.get())));
        });
        add(5, () -> Network.CHANNEL.sendToServer(new Network.WriteName(InteractionHand.MAIN_HAND, "Lind L. Tailor", "")));
        waitFor(1200, () -> flagLong("tailor_killed") != null, () -> AiCompanion.LOGGER.info("AUTOTEST Tailor died on air: {}, L knows the region: {}",
                flagLong("tailor_killed") != null, flagLong("region") != null));
        waitFor(400, () -> mc.screen instanceof TvShowScreen, () -> {
            before[0] = TvShowScreen.transcripts;
            shot(n + "c_l_answers");
        });
        waitFor(1200, () -> !(mc.screen instanceof TvShowScreen), () -> {
            AiCompanion.LOGGER.info("AUTOTEST L's answer watched: transcripts {} (was {})", TvShowScreen.transcripts, before[0]);
            shot(n + "d_l_chat");
        });
    }

    /** A television put down two blocks ahead by hand and switched on with a click: the news and L's special come only to a set. */
    private static void television(int delay) {
        Minecraft mc = Minecraft.getInstance();
        add(delay, () -> {
            cmd("give @s aicompanion:television");
            BlockPos ahead = mc.player.blockPosition().relative(mc.player.getDirection(), 2);
            tvAt = surface(ahead.getX(), ahead.getZ());
        });
        add(5, () -> select(slotOf(st -> st.is(AiCompanion.TELEVISION_ITEM.get()))));
        add(5, () -> useOn(tvAt.below())); // put down on the ground
        add(5, () -> {
            if (!(mc.level.getBlockState(tvAt).getBlock() instanceof TelevisionBlock)) { // somebody stood in the way (Ryuk hovers close)
                AiCompanion.LOGGER.warn("AUTOTEST the television didn't go down by hand at {}: {}", tvAt, mc.level.getBlockState(tvAt));
                cmd(String.format(Locale.ROOT, "setblock %d %d %d aicompanion:television", tvAt.getX(), tvAt.getY(), tvAt.getZ()));
            }
        });
        add(5, () -> useOn(tvAt)); // and switched on
        add(5, () -> {
            net.minecraft.world.level.block.state.BlockState tv = mc.level.getBlockState(tvAt);
            boolean on = tv.getBlock() instanceof TelevisionBlock && tv.getValue(TelevisionBlock.ON);
            if (on && flagLong("tv") != null) {
                AiCompanion.LOGGER.info("AUTOTEST television at {} is on, facing {}", tvAt, tv.getValue(TelevisionBlock.FACING));
            } else {
                AiCompanion.LOGGER.error("AUTOTEST television at {}: {}, watching {}", tvAt, tv, flagLong("tv") != null);
            }
            Vec3 d = Vec3.atCenterOf(tvAt).subtract(mc.player.getEyePosition()); // a look at it
            cmd(String.format(Locale.ROOT, "tp @s ~ ~ ~ %.1f %.1f", (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F,
                    (float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG)));
        });
        add(10, () -> shot(MODE + "_tv_on"));
    }

    /** The television's recipe as the server has it: what goes in, what comes out, and whether the player's book has it. */
    private static String tvRecipe() {
        return onServer(() -> me().server.getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation(AiCompanion.MODID, "television"))
                .map(r -> r.getIngredients().stream().filter(i -> !i.isEmpty()).map(i -> i.getItems()[0].getItem().toString()).toList()
                        + " -> " + r.getResultItem(me().server.registryAccess()) + ", in the book: " + me().getRecipeBook().contains(r))
                .orElse("missing"));
    }

    /**
     * The story's cards (env AICOMPANION_AUTOTEST=cards): a wanted man on the news, his death, Tailor live, a chapter
     * with a place, then the arrow to that place, turning as the player turns; L's special as a show, and the real L's.
     */
    private static void cards() {
        Minecraft mc = Minecraft.getInstance();
        add(60, () -> {
            cmd("time set 6000");
            cmd("weather clear");
            StoryHud.show(new Network.Broadcast(Network.Broadcast.NEWS, Component.translatable("aicompanion.card.wanted"),
                    Component.translatable("aicompanion.tv.wanted", "Kenji Arai", Component.translatable("aicompanion.crime3")),
                    Network.face("mafia_1"), Network.Broadcast.NOWHERE));
            StoryHud.show(new Network.Broadcast(Network.Broadcast.DEATH, Component.translatable("aicompanion.card.breaking"),
                    Component.translatable("aicompanion.tv.died_heart", "Kenji Arai"), Network.face("mafia_1"), Network.Broadcast.NOWHERE));
            StoryHud.show(new Network.Broadcast(Network.Broadcast.SPECIAL, Component.translatable("aicompanion.card.special"),
                    Component.translatable("aicompanion.tv.tailor1"), Network.face("tailor_0"), Network.Broadcast.NOWHERE));
            StoryHud.show(new Network.Broadcast(Network.Broadcast.CHAPTER, Component.translatable("aicompanion.card.hq"),
                    Component.translatable("aicompanion.card.hq_text"), "", mc.player.blockPosition().offset(90, 0, -60).asLong()));
        });
        add(40, () -> shot("c1_news"));
        add(160, () -> shot("c1b_news_waits")); // eight seconds on and nothing pressed: still there
        add(5, () -> KeyMapping.click(StoryHud.NEXT.getKey()));
        add(25, () -> shot("c2_death"));
        add(5, () -> KeyMapping.click(StoryHud.NEXT.getKey()));
        add(25, () -> shot("c3_special"));
        add(5, () -> KeyMapping.click(StoryHud.NEXT.getKey()));
        add(25, () -> shot("c4_chapter"));
        add(5, () -> KeyMapping.click(StoryHud.NEXT.getKey()));
        add(25, () -> shot("c5_arrow"));
        add(5, () -> mc.player.setYRot(mc.player.getYRot() + 90));
        add(10, () -> shot("c6_arrow_turned"));
        show("c7", Network.face("tailor_0"));
        show("c11", "L"); // the real L cutting in: the letter on a white screen
        add(10, () -> mc.stop());
    }

    /**
     * Ryuk's memory (env AICOMPANION_AUTOTEST=memory; a real model, about four requests): things told in chat land in his
     * notes ("Ryuk remembers" in the log), and asked later, he knows them.
     */
    private static void memory() {
        Minecraft mc = Minecraft.getInstance();
        add(60, () -> {
            cmd("time set 6000");
            cmd("give @s aicompanion:death_note");
        });
        waitFor(600, () -> !mc.level.getEntitiesOfClass(CompanionEntity.class, mc.player.getBoundingBox().inflate(32)).isEmpty(), () -> {
        });
        add(240, () -> mc.player.connection.sendChat("Ryuk, listen: my name is Maks, I'm scared of creepers, and I promise you an apple every morning."));
        add(400, () -> mc.player.connection.sendChat("Ryuk, what do you remember about me?"));
        add(400, () -> {
            shot("m1_memory");
            mc.stop();
        });
    }

    /**
     * The new ways to die (env AICOMPANION_AUTOTEST=deaths): four named pigs, four causes in the notebook; the signs in the
     * last seconds and the moment itself ("Named entity ... died: ..." in the log says which death it was).
     */
    private static void deaths() {
        Minecraft mc = Minecraft.getInstance();
        String[] how = {"утонул", "взорвался", "замёрз", "отравился ядом"};
        add(60, () -> {
            cmd("time set 6000");
            cmd("gamerule doMobSpawning false");
            cmd("give @s aicompanion:death_note");
            for (int i = 0; i < 4; i++) {
                cmd(String.format(Locale.ROOT, "summon minecraft:pig ~%d ~ ~5 {CustomName:'\"Pig%d\"',NoAI:1b,Rotation:[180f,0f]}", i * 3 - 4, i));
            }
        });
        television(40); // a criminal on the news, written with a bare "огонь": the screens tell it as a story
        waitFor(400, () -> !onServer(() -> ru.maksim.aicompanion.News.wanted(mc.getSingleplayerServer())).isEmpty(), () -> { });
        add(5, () -> {
            java.util.List<ru.maksim.aicompanion.News.Criminal> wanted = onServer(() -> ru.maksim.aicompanion.News.wanted(mc.getSingleplayerServer()));
            AiCompanion.LOGGER.info("AUTOTEST deaths: on the news {}", wanted);
            select(slotOf(s -> s.is(AiCompanion.DEATH_NOTE.get())));
            if (!wanted.isEmpty()) {
                Network.CHANNEL.sendToServer(new Network.WriteName(InteractionHand.MAIN_HAND, wanted.get(0).name(), "огонь"));
            }
        });
        add(40, () -> select(slotOf(s -> s.is(AiCompanion.DEATH_NOTE.get())))); // their faces seen, the notebook in hand
        for (int i = 0; i < 4; i++) {
            int k = i;
            add(25, () -> {
                AiCompanion.LOGGER.info("AUTOTEST writing Pig{}: {}", k, how[k]);
                Network.CHANNEL.sendToServer(new Network.WriteName(InteractionHand.MAIN_HAND, "Pig" + k, how[k]));
            });
        }
        add(700, () -> shot("d1_signs")); // the last seconds of the first ones
        add(50, () -> shot("d2_moment"));
        add(80, () -> shot("d3_after"));
        add(60, () -> AiCompanion.LOGGER.info("AUTOTEST pigs left: {}", mc.level.getEntitiesOfClass(net.minecraft.world.entity.animal.Pig.class,
                mc.player.getBoundingBox().inflate(16)).size()));
        waitFor(400, () -> HEARD.stream().anyMatch(AutoTest::told), () -> {
            String told = HEARD.stream().filter(AutoTest::told).findFirst().orElse("");
            if (told.isEmpty()) {
                AiCompanion.LOGGER.error("AUTOTEST deaths: the news death never told as a story; on TV: {}", HEARD.stream().filter(l -> l.startsWith("[TV]")).toList());
            } else {
                AiCompanion.LOGGER.info("AUTOTEST deaths: the news told it: {}", told);
            }
            shot("d4_news_story");
        });
        add(20, () -> mc.stop());
    }

    /** A line from the screens that tells a death with its place (News.story), not a bare cause. */
    private static boolean told(String line) {
        return line.startsWith("[TV]") && java.util.stream.IntStream.rangeClosed(1, 8)
                .anyMatch(i -> line.contains(net.minecraft.client.resources.language.I18n.get("aicompanion.tv.place" + i)));
    }

    /**
     * Ryuk's added animations in the game (env AICOMPANION_AUTOTEST=anims): a statue in a pale yard, each animation
     * caught at two moments from the front, the trickier ones from the side as well.
     */
    private static void anims() {
        Minecraft mc = Minecraft.getInstance();
        add(60, () -> {
            mc.options.hideGui = true;
            for (String c : new String[]{"time set 6000", "weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobSpawning false", "tp @s 0 -60 0 0 0", "fill -16 -61 -16 16 -61 16 minecraft:smooth_quartz"}) {
                cmd(c);
            }
        });
        add(20, () -> cmd("give @s aicompanion:death_note"));
        add(260, () -> cmd("companion stay")); // he has come down by now
        add(40, () -> cmd("companion hold"));
        add(20, () -> face(3.8, 0));
        String[] all = {"wave", "bow", "backflip", "stretch", "peek", "clap", "shrug", "doze", "point", "rub_hands", "perch", "juggle",
                "look_around", "cackle", "sulk", "dice_shake", "dice_slam"};
        for (String n : all) {
            add(30, () -> cmd("companion action " + n));
            add(14, () -> shot("an_" + n + "_1"));
            add(16, () -> shot("an_" + n + "_2"));
            add(60, () -> {
            });
        }
        add(20, () -> face(3.8, 90));
        for (String n : new String[]{"bow", "backflip", "peek", "perch", "juggle", "dice_slam", "sulk", "point", "doze", "stretch"}) {
            add(30, () -> cmd("companion action " + n));
            add(16, () -> shot("an_side_" + n));
            add(60, () -> {
            });
        }
        add(40, () -> {
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /**
     * The dice table (env AICOMPANION_AUTOTEST=dice): five rounds with rigged dice, one of each case (a win on even, a
     * loss at stake 5, two sixes, two ones, a round left before the call), every phase on a screenshot and the apples
     * counted after each round: they must come out exactly as the rules say.
     */
    private static void dice() {
        Minecraft mc = Minecraft.getInstance();
        add(60, () -> {
            for (String c : new String[]{"time set 6000", "weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobSpawning false", "tp @s 0 -60 0 0 0", "fill -16 -61 -16 16 -61 16 minecraft:smooth_quartz"}) {
                cmd(c);
            }
        });
        add(20, () -> cmd("give @s aicompanion:death_note"));
        add(240, () -> cmd("give @s minecraft:apple 20"));
        add(10, () -> { // the player's way in: an apple in hand, sneak, right click on him
            select(slotOf(st -> st.is(net.minecraft.world.item.Items.APPLE)));
            lookAt(ryuk());
        });
        add(10, () -> {
            mc.options.keyShift.setDown(true);
        });
        add(5, () -> mc.gameMode.interact(mc.player, ryuk(), InteractionHand.MAIN_HAND));
        add(20, () -> {
            mc.options.keyShift.setDown(false);
            AiCompanion.LOGGER.info("AUTOTEST dice table opened by sneak + right click: {}, apples before: {}", DiceScreen.current() != null, applesHeld());
            if (DiceScreen.current() == null) {
                cmd("companion dice");
            }
        });
        add(40, () -> shot("dc1_open"));
        diceRound(6, 2, 3, true, "a"); // 8, even, called even: +3
        diceRound(3, 4, 5, true, "b"); // 7, odd, called even: -5 (he laughs)
        diceRound(6, 6, 2, false, "c"); // two sixes, called odd: still a win, x3 = +4
        diceRound(1, 1, 1, false, "d"); // two ones, called odd: lost anyway, -1
        add(20, () -> AiCompanion.LOGGER.info("AUTOTEST dice apples after 4 rounds: {} (expected 20+3-5+4-1 = 21)", applesHeld()));
        add(10, () -> diceKey(GLFW.GLFW_KEY_ENTER)); // a bet of 1, then the table is left before the call
        add(20, () -> AiCompanion.LOGGER.info("AUTOTEST dice apples with a bet out: {}", applesHeld()));
        add(10, () -> mc.setScreen(null));
        add(20, () -> {
            AiCompanion.LOGGER.info("AUTOTEST dice apples after leaving mid-round: {} (expected 21), table open: {}", applesHeld(), DiceScreen.current() != null);
            shot("dc9_after");
        });
        // asked in words: offline his reply names no action, so it is the plain request itself that opens the table
        add(80, () -> mc.player.connection.sendChat("Ryuk, давай сыграем в кости на яблоки"));
        waitFor(200, () -> DiceScreen.current() != null, () -> {
            AiCompanion.LOGGER.info("AUTOTEST dice opened by a request in chat: {}", DiceScreen.current() != null);
            shot("dc10_asked");
        });
        add(10, () -> mc.setScreen(null));
        add(40, () -> {
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /** One round: stake set with the arrows, rolled, called; screenshots of each phase on the way. */
    private static void diceRound(int d1, int d2, int stake, boolean even, String tag) {
        add(10, () -> {
            onServer(() -> {
                ru.maksim.aicompanion.DiceGame.rigNext(d1, d2);
                return 0;
            });
            for (int i = 0; i < 12; i++) {
                diceKey(GLFW.GLFW_KEY_DOWN);
            }
            for (int i = 1; i < stake; i++) {
                diceKey(GLFW.GLFW_KEY_UP);
            }
        });
        add(6, () -> shot("dc_" + tag + "1_stake"));
        add(2, () -> diceKey(GLFW.GLFW_KEY_ENTER));
        add(16, () -> shot("dc_" + tag + "2_shaking"));
        add(30, () -> shot("dc_" + tag + "3_cup_down"));
        add(4, () -> diceKey(even ? GLFW.GLFW_KEY_LEFT : GLFW.GLFW_KEY_RIGHT));
        add(7, () -> shot("dc_" + tag + "4_lift"));
        add(9, () -> shot("dc_" + tag + "5_tumble"));
        add(10, () -> shot("dc_" + tag + "6_settled"));
        add(8, () -> shot("dc_" + tag + "7_apples"));
        add(20, () -> shot("dc_" + tag + "8_after"));
        add(30, () -> AiCompanion.LOGGER.info("AUTOTEST dice round {} ({}+{}, stake {}, called {}): apples now {}", tag, d1, d2, stake,
                even ? "even" : "odd", applesHeld()));
    }

    private static void diceKey(int key) {
        DiceScreen s = DiceScreen.current();
        if (s != null) {
            s.keyPressed(key, 0, 0);
        } else {
            AiCompanion.LOGGER.error("AUTOTEST no dice table on screen");
        }
    }

    private static int applesHeld() {
        net.minecraft.world.entity.player.Inventory inv = Minecraft.getInstance().player.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(net.minecraft.world.item.Items.APPLE)) {
                n += inv.getItem(i).getCount();
            }
        }
        return n;
    }

    /**
     * The Death Note as an item (env AICOMPANION_AUTOTEST=note): in the hand (first and third person), in the inventory
     * with its tooltip, lying on the ground, in an item frame; the icon's gleam caught at two moments.
     */
    private static void note() {
        Minecraft mc = Minecraft.getInstance();
        add(60, () -> {
            for (String c : new String[]{"time set 6000", "weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobSpawning false", "gamerule sendCommandFeedback false", "gamemode survival", "tp @s 0 -60 0 0 10",
                    "fill -8 -61 -8 8 -61 8 minecraft:dark_oak_planks", "fill -2 -60 4 2 -57 4 minecraft:stripped_dark_oak_log", "clear @s",
                    "give @s aicompanion:death_note"}) {
                cmd(c);
            }
        });
        add(40, () -> select(slotOf(st -> st.is(AiCompanion.DEATH_NOTE.get()))));
        add(30, () -> mc.gui.getChat().clearMessages(false));
        add(10, () -> shot("nt1_hand"));
        add(5, () -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
        add(10, () -> hoverSlot(36 + mc.player.getInventory().selected));
        add(10, () -> shot("nt2_inventory"));
        add(3, () -> shot("nt2b_inventory_gleam"));
        add(5, () -> mc.setScreen(null));
        add(5, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        add(20, () -> shot("nt3_third_person"));
        add(5, () -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            cmd("summon item 0.5 -60 1.6 {Item:{id:\"aicompanion:death_note\",Count:1b},PickupDelay:32767,Age:-32768}");
            cmd("tp @s 0.5 -60 0.2 0 55");
        });
        for (int i = 0; i < 4; i++) {
            add(15, () -> KeyMapping.click(StoryHud.NEXT.getKey())); // Ryuk's arrival card, out of the way
        }
        add(30, () -> {
            mc.gui.getChat().clearMessages(false);
            shot("nt4_ground");
        });
        add(5, () -> {
            cmd("kill @e[type=item]");
            cmd("summon item_frame 0.5 -58.5 3.5 {Facing:2b,Fixed:1b,Item:{id:\"aicompanion:death_note\",Count:1b}}");
            cmd("tp @s 0.5 -60 1.6 0 0");
        });
        add(30, () -> shot("nt5_frame"));
        add(20, () -> {
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /** Puts the mouse over a slot of the open container screen (its tooltip shows). */
    private static void hoverSlot(int index) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> s) {
            net.minecraft.world.inventory.Slot slot = s.getMenu().getSlot(index);
            double scale = mc.getWindow().getGuiScale();
            GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), (s.getGuiLeft() + slot.x + 8) * scale, (s.getGuiTop() + slot.y + 8) * scale);
        }
    }

    /**
     * The side quest "Sidoh's pages" (env AICOMPANION_AUTOTEST=sidoh): the first page falls and is caught, Sidoh shows up,
     * the four loose pages are chased down (they dodge), chocolate buys a hint, the pages go back, the bandage is worn.
     */
    private static void sidohQuest() {
        Minecraft mc = Minecraft.getInstance();
        add(60, () -> {
            for (String c : new String[]{"time set 3000", "weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobSpawning false", "gamerule sendCommandFeedback false", "gamemode survival", "tp @s 0 -60 0 0 0",
                    "give @s aicompanion:death_note", "give @s aicompanion:chocolate 2"}) {
                cmd(c);
            }
        });
        add(260, () -> cmd("companion quest sidoh")); // Ryuk has come down by now
        add(12, () -> shot("sq0_card"));
        add(18, () -> shot("sq1_page_falls"));
        add(80, () -> {
            LostPage page = nearestPage();
            AiCompanion.LOGGER.info("AUTOTEST sidoh: first page {}", page == null ? "missing" : page.position());
            if (page != null) {
                lookAt(page);
            }
        });
        add(10, () -> shot("sq2_page_low"));
        add(5, () -> {
            LostPage page = nearestPage();
            if (page != null) {
                mc.gameMode.interact(mc.player, page, InteractionHand.MAIN_HAND);
            }
        });
        add(60, () -> shot("sq3_sidoh_arrives"));
        add(40, () -> {
            SidohEntity s = nearestSidoh();
            AiCompanion.LOGGER.info("AUTOTEST sidoh: stage {}, sees Sidoh: {}, Sidoh {}", onServer(() -> SideQuests.stage(me(), "sidoh")),
                    ClientHooks.seesSidoh(), s == null ? "missing" : s.position());
        });
        add(2, () -> cmd("tp @s ~ ~ ~ 0 0"));
        add(2, () -> faceSidoh("sq4_sidoh"));
        // he stays where he can be looked at and clicked: the player looks away both ways and back, he's still there, in reach
        Vec3[] was = new Vec3[1];
        add(40, () -> {
            SidohEntity s = nearestSidoh();
            if (s != null) {
                was[0] = s.position();
                lookAt(s);
            }
        });
        add(5, () -> AiCompanion.LOGGER.info("AUTOTEST sidoh: in the crosshair {} at {} blocks", sidohAimed(mc), nearestSidoh() == null ? -1
                : String.format(Locale.ROOT, "%.1f", nearestSidoh().distanceTo(mc.player))));
        add(2, () -> cmd("tp @s ~ ~ ~ ~100 0"));
        add(60, () -> cmd("tp @s ~ ~ ~ ~-200 0"));
        add(60, () -> {
            SidohEntity s = nearestSidoh();
            if (s != null && was[0] != null) {
                AiCompanion.LOGGER.info("AUTOTEST sidoh: the player looked away both ways, Sidoh moved {} blocks", String.format(Locale.ROOT, "%.1f", s.position().distanceTo(was[0])));
                lookAt(s);
            }
        });
        add(5, () -> {
            if (sidohAimed(mc)) {
                AiCompanion.LOGGER.info("AUTOTEST sidoh: still in the crosshair, clickable");
            } else {
                AiCompanion.LOGGER.error("AUTOTEST sidoh: out of reach or out of sight: {}", mc.hitResult);
            }
            shot("sq4b_sidoh_in_reach");
        });
        for (int i = 0; i < 40; i++) {
            int k = i;
            add(18, () -> chasePage(k));
            if (i == 8) { // chocolate: he eats it and points the way
                add(10, () -> {
                    SidohEntity s = nearestSidoh();
                    if (s != null) {
                        select(slotOf(st -> st.is(AiCompanion.CHOCOLATE.get())));
                        lookAt(s);
                    }
                });
                add(5, () -> {
                    SidohEntity s = nearestSidoh();
                    if (s != null) {
                        mc.gameMode.interact(mc.player, s, InteractionHand.MAIN_HAND);
                    }
                });
                add(3, () -> faceSidoh("sq6_sidoh_eats"));
                add(25, () -> faceSidoh("sq7_sidoh_points"));
            }
        }
        add(20, () -> {
            AiCompanion.LOGGER.info("AUTOTEST sidoh: after the chase stage {}, pages held {}", onServer(() -> SideQuests.stage(me(), "sidoh")),
                    mc.player.getInventory().countItem(AiCompanion.SIDOH_PAGE.get()));
            SidohEntity s = nearestSidoh();
            if (s != null) {
                lookAt(s);
            }
        });
        add(2, () -> cmd("tp @s ~ ~ ~ 0 0"));
        add(4, () -> faceSidoh("sq8_all_pages"));
        add(5, () -> {
            SidohEntity s = nearestSidoh();
            if (s != null) {
                select(slotOf(st -> st.is(AiCompanion.SIDOH_PAGE.get())));
                mc.gameMode.interact(mc.player, s, InteractionHand.MAIN_HAND);
            }
        });
        add(2, () -> faceSidoh("sq9_thanks"));
        add(40, () -> AiCompanion.LOGGER.info("AUTOTEST sidoh: stage {}, bandage held {}, case lines {}",
                onServer(() -> SideQuests.stage(me(), "sidoh")), mc.player.getInventory().countItem(AiCompanion.SIDOH_BANDAGE.get()),
                onServer(() -> SideQuests.describe(me()))));
        add(90, () -> AiCompanion.LOGGER.info("AUTOTEST sidoh: gone home: {}", nearestSidoh() == null));
        add(5, () -> select(slotOf(st -> st.is(AiCompanion.SIDOH_BANDAGE.get()))));
        add(5, () -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
        add(10, () -> {
            AiCompanion.LOGGER.info("AUTOTEST sidoh: bandage on the head: {}, face hidden: {}", mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD),
                    onServer(() -> ru.maksim.aicompanion.Lifespans.masked(me())));
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        });
        add(20, () -> shot("sq10_bandage"));
        add(20, () -> {
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /** One step of the chase: go to the next page's place, or right up to the page, or grab it. */
    private static void chasePage(int step) {
        Minecraft mc = Minecraft.getInstance();
        LostPage page = nearestPage();
        if (page == null) {
            BlockPos next = onServer(() -> SideQuests.nextPage(me()));
            if (next != null) {
                AiCompanion.LOGGER.info("AUTOTEST sidoh chase {}: off to {}", step, next);
                cmd(String.format(Locale.ROOT, "tp @s %d %d %d", next.getX() + 6, next.getY(), next.getZ()));
            }
            return;
        }
        double d = mc.player.distanceTo(page);
        if (d > 3.0) { // right onto it: a gust takes it if it can still dodge, else it's caught on touch
            cmd(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", page.getX(), Math.max(page.getY() - 1.0, -60), page.getZ()));
            if (step % 4 == 0) {
                STEPS.addFirst(new Step(6, () -> shot("sq5_chase_" + step)));
            }
            return;
        }
        lookAt(page);
        mc.gameMode.interact(mc.player, page, InteractionHand.MAIN_HAND);
        AiCompanion.LOGGER.info("AUTOTEST sidoh chase {}: grabbing (pages now {})", step, mc.player.getInventory().countItem(AiCompanion.SIDOH_PAGE.get()));
    }

    /** Sidoh keeps to his human's right shoulder: the camera turned round to face the player shows them both. */
    private static void faceSidoh(String shotName) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        STEPS.addFirst(new Step(2, () -> mc.options.setCameraType(CameraType.FIRST_PERSON)));
        STEPS.addFirst(new Step(30, () -> { // right after this step, not at the end of the script
            SidohEntity s = nearestSidoh();
            AiCompanion.LOGGER.info("AUTOTEST {}: Sidoh {} ({})", shotName, s == null ? "missing" : s.position(), s == null ? "" : s.mood());
            shot(shotName);
        }));
    }

    /**
     * The hideout's guards (env AICOMPANION_AUTOTEST=hideout, the ordinary world, so the hall is underground): Sidoh leaves
     * a mask alone behind the hall's wall and tears it off in the hall; the gang goes for whoever comes in, gives up the
     * chase out of the tunnel and goes back in; the boss never leaves his desk; Sidoh's notebook, picked up far from him
     * (the boss died out of the hideout), and Sidoh clinging to it once it comes into the hall.
     */
    private static void hideout() {
        Minecraft mc = Minecraft.getInstance();
        BlockPos[] at = new BlockPos[2]; // the hall, the shack above it
        add(60, () -> {
            for (String c : new String[]{"time set 6000", "weather clear", "gamerule doDaylightCycle false", "gamerule doMobSpawning false",
                    "difficulty easy", "effect give @s minecraft:resistance infinite 4 true", "companion kira sidoh"}) {
                cmd(c);
            }
        });
        add(300, () -> {
            at[1] = onServer(() -> BlockPos.of(Kira.of(me()).flags.get("hideout")));
            at[0] = ru.maksim.aicompanion.Mafia.hall(overworld(), at[1]);
            BlockPos h = at[0];
            AiCompanion.LOGGER.info("AUTOTEST hideout: shack {}, hall {}, gang {}, boss {}", at[1], h, gang(h, g -> true), boss(h));
            cmd(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", h.getX() + 12, h.getY(), h.getZ() + 9, h.getX() + 12, h.getY() + 1, h.getZ() + 9));
            cmd("item replace entity @s armor.head with minecraft:carved_pumpkin");
            cmd(String.format(Locale.ROOT, "tp @s %d.5 %d %d.5 90 0", h.getX() + 12, h.getY(), h.getZ() + 9)); // a pocket behind the east wall, in his reach
        });
        add(400, () -> { // 20 s: in sight, a quarter's chance a second
            AiCompanion.LOGGER.info("AUTOTEST hideout: behind the wall, the mask still on: {}", mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN));
            cmd(String.format(Locale.ROOT, "tp @s %d.5 %d %d.5 0 0", at[0].getX(), at[0].getY(), at[0].getZ() + 10)); // under him, back to the boss
        });
        add(400, () -> {
            AiCompanion.LOGGER.info("AUTOTEST hideout: in the hall, the mask torn off: {}", !mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(Items.CARVED_PUMPKIN));
            cmd("gamemode survival"); // nobody's target in creative
            BlockPos h = at[0]; // a straight corridor east out of the hall: they see us all along it, so only the leash ends a chase
            cmd(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", h.getX() + 11, h.getY() - 1, h.getZ() + 9, h.getX() + 44, h.getY() - 1, h.getZ() + 10));
            cmd(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", h.getX() + 11, h.getY(), h.getZ() + 9, h.getX() + 44, h.getY() + 2, h.getZ() + 10));
        });
        add(100, () -> {
            AiCompanion.LOGGER.info("AUTOTEST hideout: in the hall, the gang after us: {} of {}; boss {}", gang(at[0], g -> g.getTarget() == me()), gang(at[0], g -> true), boss(at[0]));
            cmd(String.format(Locale.ROOT, "tp @s %d.5 %d %d.0 -90 0", at[0].getX() + 19, at[0].getY(), at[0].getZ() + 10)); // down the corridor: still theirs
        });
        add(80, () -> {
            AiCompanion.LOGGER.info("AUTOTEST hideout: in the corridor, the gang after us and in sight of us: {}", gang(at[0], g -> g.getTarget() == me() && g.hasLineOfSight(me())));
            cmd(String.format(Locale.ROOT, "tp @s %d.5 %d %d.0 -90 0", at[0].getX() + 38, at[0].getY(), at[0].getZ() + 10)); // off their turf, in plain sight
        });
        add(50, () -> AiCompanion.LOGGER.info("AUTOTEST hideout: off their turf, still chasing: {} (in sight of us: {}), past the turf: {}; boss {}",
                gang(at[0], g -> g.getTarget() != null), gang(at[0], g -> g.hasLineOfSight(me())),
                gang(at[0], g -> g.distanceToSqr(Vec3.atCenterOf(at[0])) > 26 * 26), boss(at[0])));
        add(700, () -> AiCompanion.LOGGER.info("AUTOTEST hideout: 35 s on, out of the hall: {} of {}", gang(at[0],
                g -> g.distanceToSqr(Vec3.atCenterOf(at[0])) > 18 * 18), gang(at[0], g -> true)));
        add(20, () -> { // the boss dies; his notebook turns up in the hands of someone far away (as when he chased out and died there)
            onServer(() -> {
                overworld().getEntitiesOfClass(HumanEntity.class, new AABB(at[0]).inflate(64), g -> g.role() == HumanEntity.Role.BOSS).forEach(HumanEntity::kill);
                return null;
            });
            BlockPos far = surface(at[0].getX(), at[0].getZ() + 140);
            cmd(String.format(Locale.ROOT, "tp @s %d.5 %d %d.5 0 0", far.getX(), far.getY(), far.getZ()));
        });
        add(40, () -> onServer(() -> {
            overworld().getEntitiesOfClass(ItemEntity.class, new AABB(at[0]).inflate(64), i -> i.getItem().hasTag() && i.getItem().getTag().getBoolean("Sidoh"))
                    .forEach(ItemEntity::discard);
            ItemStack note = new ItemStack(AiCompanion.DEATH_NOTE.get());
            note.getOrCreateTag().putBoolean("Sidoh", true);
            return me().getInventory().add(note);
        }));
        add(60, () -> {
            AiCompanion.LOGGER.info("AUTOTEST hideout: far off with his notebook, Sidoh {}", onServer(() -> overworld().getEntitiesOfClass(SidohEntity.class,
                    new AABB(at[0]).inflate(64)).stream().map(s -> s.position() + " " + s.mood()).toList()));
            cmd(String.format(Locale.ROOT, "tp @s %d.5 %d %d.5 0 0", at[0].getX(), at[0].getY(), at[0].getZ() + 10));
        });
        add(120, () -> {
            SidohEntity s = nearestSidoh();
            AiCompanion.LOGGER.info("AUTOTEST hideout: back in the hall, Sidoh {} {}, {} blocks off", s == null ? "missing" : s.position(), s == null ? "" : s.mood(),
                    s == null ? -1 : s.distanceTo(mc.player));
            select(slotOf(st -> st.hasTag() && st.getTag().getBoolean("Sidoh")));
            if (s != null) {
                mc.gameMode.interact(mc.player, s, InteractionHand.MAIN_HAND);
            }
        });
        add(40, () -> {
            AiCompanion.LOGGER.info("AUTOTEST hideout: notebook handed back, Sidoh gone: {}, done: {}", nearestSidoh() == null, onServer(() -> Kira.of(me()).flag("sidoh_done")));
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /**
     * The story's people talking (env AICOMPANION_AUTOTEST=talks; L answers through the key, so not offline): the drawer's
     * tooltip; L greeting the guest he invited and knowing "Taro Matsui" for Matsuda; a detective's and Watari's lines
     * while L lives, after his death, with Near on the case; Mello taking a chocolate bar for a tip; a creeper killed by
     * hand and not a word from Ryuk about it.
     */
    private static void talks() {
        Minecraft mc = Minecraft.getInstance();
        add(60, () -> {
            for (String c : new String[]{"time set 6000", "weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobSpawning false", "difficulty easy", "tp @s 0 -60 0 0 0", "give @s aicompanion:death_note"}) {
                cmd(c);
            }
            AiCompanion.LOGGER.info("AUTOTEST talks: the drawer's tooltip: {}", new ItemStack(AiCompanion.DRAWER_ITEM.get())
                    .getTooltipLines(mc.player, net.minecraft.world.item.TooltipFlag.Default.NORMAL).stream().map(Component::getString).toList());
        });
        add(200, () -> ClientKira.openNote(InteractionHand.MAIN_HAND));
        add(10, () -> {
            Network.CHANNEL.sendToServer(new Network.Answer(Network.Choice.PACT, false));
            mc.setScreen(null);
            select(8); // empty hands: the notebook held out to Mello would be his
        });
        add(40, () -> cmd("companion kira hq")); // at the player + 30 blocks south
        add(220, () -> {
            HumanEntity l = person("l_");
            AiCompanion.LOGGER.info("AUTOTEST talks: L {}, suspicion {}", l == null ? "missing" : l.position(), onServer(() -> Kira.of(me()).suspicion));
            if (l != null) { // within earshot: he hears chat from 8 blocks
                cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f 180 0", l.getX(), l.getBlockY(), l.getZ() + 3));
            }
        });
        add(40, () -> talkTo(HumanEntity.Role.L)); // the first time: the guest he sent for
        add(300, () -> mc.player.connection.sendChat("Taro Matsui said you wanted to see me."));
        add(300, () -> {
            AiCompanion.LOGGER.info("AUTOTEST talks: two lines with L, suspicion {}", onServer(() -> Kira.of(me()).suspicion));
            talkTo(HumanEntity.Role.DETECTIVE);
        });
        add(10, () -> talkTo(HumanEntity.Role.DETECTIVE));
        add(10, () -> talkTo(HumanEntity.Role.DETECTIVE));
        add(10, () -> talkTo(HumanEntity.Role.DETECTIVE)); // round again: the first line
        add(10, () -> talkTo(HumanEntity.Role.WATARI));
        add(10, () -> talkTo(HumanEntity.Role.WATARI));
        add(10, () -> {
            onServer(() -> Kira.of(me()).flags.put("l_dead", overworld().getGameTime()));
            talkTo(HumanEntity.Role.DETECTIVE);
        });
        add(10, () -> talkTo(HumanEntity.Role.DETECTIVE));
        add(10, () -> talkTo(HumanEntity.Role.WATARI));
        add(10, () -> {
            onServer(() -> Kira.of(me()).flags.put("successor", overworld().getGameTime()));
            talkTo(HumanEntity.Role.DETECTIVE);
        });
        add(10, () -> {
            cmd("summon aicompanion:human ~2 ~ ~ {Role:\"MELLO\",CustomName:'\"Mello\"'}");
            cmd("give @s aicompanion:chocolate 2");
        });
        add(20, () -> talkTo(HumanEntity.Role.MELLO)); // empty-handed
        add(10, () -> select(slotOf(s -> s.is(AiCompanion.CHOCOLATE.get()))));
        add(10, () -> talkTo(HumanEntity.Role.MELLO));
        add(10, () -> {
            AiCompanion.LOGGER.info("AUTOTEST talks: chocolate left {} of 2", mc.player.getInventory().countItem(AiCompanion.CHOCOLATE.get()));
            cmd("summon minecraft:creeper ~2 ~ ~2 {Health:1f,NoAI:1b}"); // no AI: it won't blow; and it doesn't burn in the sun
        });
        add(400, () -> { // 20 s of quiet first: his last line mustn't be what keeps him quiet
            net.minecraft.world.entity.monster.Creeper z = mc.level.getEntitiesOfClass(net.minecraft.world.entity.monster.Creeper.class,
                    mc.player.getBoundingBox().inflate(8)).stream().findFirst().orElse(null);
            AiCompanion.LOGGER.info("AUTOTEST talks: killing a creeper by hand: {}", z != null);
            if (z != null) {
                mc.gameMode.attack(mc.player, z);
            }
        });
        add(300, () -> {
            AiCompanion.LOGGER.info("AUTOTEST talks: 15 s after the creeper, it's dead: {}", mc.level.getEntitiesOfClass(
                    net.minecraft.world.entity.monster.Creeper.class, mc.player.getBoundingBox().inflate(8)).isEmpty());
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /**
     * The mod is English only (env AICOMPANION_AUTOTEST=english): the game switched to Russian, the mod's texts stay
     * English, its settings page has no language left, Ryuk's voice speaks; the dev client's own language back after.
     */
    private static void englishOnly() {
        Minecraft mc = Minecraft.getInstance();
        add(20, () -> {
            mc.options.languageCode = "ru_ru";
            mc.getLanguageManager().setSelected("ru_ru");
            mc.reloadResourcePacks();
        });
        add(200, () -> {
            AiCompanion.LOGGER.info("AUTOTEST english: the game in {} ({}), the mod: {} | {} | {}", mc.getLanguageManager().getSelected(),
                    net.minecraft.client.resources.language.I18n.get("menu.singleplayer"),
                    net.minecraft.client.resources.language.I18n.get("item.aicompanion.death_note"),
                    net.minecraft.client.resources.language.I18n.get("aicompanion.talk.mello1"),
                    net.minecraft.client.resources.language.I18n.get("aicompanion.setup.english_voice"));
            RyukVoice.speak(mc.player.getId(), "Heh. Humans are so interesting.", "neutral");
            SetupScreen s = new SetupScreen(null);
            mc.setScreen(s);
            s.page(true);
        });
        add(80, () -> shot("en_settings"));
        add(20, () -> {
            mc.setScreen(null);
            mc.options.languageCode = "en_us"; // the dev client's own language back
            mc.getLanguageManager().setSelected("en_us");
            mc.options.save();
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /** A right click on the nearest of the story's people in {@code role}, made on the server (no walking up to them). */
    private static void talkTo(HumanEntity.Role role) {
        onServer(() -> overworld().getEntitiesOfClass(HumanEntity.class, me().getBoundingBox().inflate(96), h -> h.role() == role).stream()
                .min(Comparator.comparingDouble(h -> h.distanceToSqr(me()))).map(h -> h.mobInteract(me(), InteractionHand.MAIN_HAND)).orElse(null));
    }

    /** How many of the gang (server side) around the hall are {@code which}. */
    private static long gang(BlockPos hall, java.util.function.Predicate<HumanEntity> which) {
        return onServer(() -> (long) overworld().getEntitiesOfClass(HumanEntity.class, new AABB(hall).inflate(64),
                g -> g.role() == HumanEntity.Role.MAFIA && which.test(g)).size());
    }

    /** The boss (server side): where he is against his chair, and whom he's after. */
    private static String boss(BlockPos hall) {
        return onServer(() -> overworld().getEntitiesOfClass(HumanEntity.class, new AABB(hall).inflate(64), g -> g.role() == HumanEntity.Role.BOSS).stream()
                .map(g -> String.format(Locale.ROOT, "%.1f from his chair, target %s", Math.sqrt(g.distanceToSqr(hall.getX() + 0.5, hall.getY() + 2.5, hall.getZ() + 0.72)),
                        g.getTarget() == null ? "none" : g.getTarget().getName().getString())).findFirst().orElse("dead"));
    }

    @Nullable
    private static LostPage nearestPage() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level.getEntitiesOfClass(LostPage.class, mc.player.getBoundingBox().inflate(160)).stream()
                .min(Comparator.comparingDouble(p -> p.distanceToSqr(mc.player))).orElse(null);
    }

    private static boolean sidohAimed(Minecraft mc) {
        return mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof SidohEntity;
    }

    @Nullable
    private static SidohEntity nearestSidoh() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level.getEntitiesOfClass(SidohEntity.class, mc.player.getBoundingBox().inflate(64)).stream().findFirst().orElse(null);
    }

    /**
     * The realm of the gods of death and its side quests (env AICOMPANION_AUTOTEST=realm): Ryuk's invitation, the dream;
     * the plaza's views; the five sights one at a time (a window's vision, the bone toss, Armonia telling of the
     * tribute, the King, the dead tree's apple) and the quest done; thirteen apples in the King's bowl; a jump into a
     * window and awake; the realm's apple eaten (every life around glows) and offered to Ryuk (he sulks).
     */
    private static void realmTour() {
        Minecraft mc = Minecraft.getInstance();
        add(60, () -> {
            for (String c : new String[]{"time set 12000", "weather clear", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobSpawning false", "gamerule sendCommandFeedback false", "gamemode survival", "give @s aicompanion:death_note"}) {
                cmd(c);
            }
        });
        add(260, () -> cmd("companion quest realm")); // Ryuk has come down by now: his evening offer
        add(14, () -> shot("rm0_invite"));
        add(60, () -> {
            AiCompanion.LOGGER.info("AUTOTEST realm: invited, stage {}", onServer(() -> SideQuests.stage(me(), "realm")));
            cmd("give @s minecraft:apple 20");
            cmd("companion kira dream");
        });
        waitFor(400, () -> mc.level.dimension().location().getPath().equals("shinigami_realm"), () -> { });
        add(50, () -> shot("rm1_arrived"));
        add(150, () -> { // the realm is built by now
            theme("the realm", "music.realm");
            AiCompanion.LOGGER.info("AUTOTEST realm: in the dream, stage {}, gods {}", onServer(() -> SideQuests.stage(me(), "realm")),
                    mc.level.getEntitiesOfClass(RealmGod.class, mc.player.getBoundingBox().inflate(64)).stream().map(RealmGod::who).toList());
            AiCompanion.LOGGER.info("AUTOTEST realm: labels {}, the bowl {}, Sidoh at home {}",
                    mc.level.getEntitiesOfClass(net.minecraft.world.entity.Display.TextDisplay.class, mc.player.getBoundingBox().inflate(64)).size(),
                    mc.level.getBlockState(new BlockPos(0, 51, 33)).getBlock(), mc.level.getEntitiesOfClass(SidohEntity.class,
                            mc.player.getBoundingBox().inflate(64)).stream().map(SidohEntity::position).toList());
            mc.options.hideGui = true;
        });
        String[] look = {"180 -2", "180 -35", "270 0", "0 -8", "90 0", "200 25"};
        for (int i = 0; i < look.length; i++) {
            int k = i;
            add(30, () -> cmd("tp @s 0.5 51 4.5 " + look[k]));
            add(20, () -> shot("rm_view" + k));
        }
        // the realm's details, one by one (RealmDecor): x y z yaw pitch name
        String[] decor = {"-44.5 51 -2.5 90 -12 rmd01_notebook", "20.5 51 14.5 -45 -12 rmd02_hourglass", "-14.5 53 10.5 45 24 rmd03_dust",
                "31.5 52 -2.5 -90 14 rmd04_graveyard", "0.5 51 12.5 0 -16 rmd05_scythes_cages", "-10.5 54 -23.5 180 32 rmd06_pool",
                "0.5 52 22.5 0 -12 rmd07_obelisks", "-34.5 51 37.5 0 -8 rmd08_colonnade", "-20.5 55 -19.5 120 8 rmd09_pages",
                "30.5 51 -35.5 180 12 rmd10_cairn", "-62.5 55 -37.5 180 16 rmd11_circle"};
        add(5, () -> mc.options.hideGui = true);
        for (String d : decor) {
            String[] q = d.split(" ");
            add(15, () -> cmd("tp @s " + q[0] + " " + q[1] + " " + q[2] + " " + q[3] + " " + q[4]));
            add(25, () -> shot(q[5]));
        }
        add(5, () -> {
            mc.options.hideGui = false;
            AiCompanion.LOGGER.info("AUTOTEST realm: details: displays {}, marks {} {}",
                    mc.level.getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(-96, 0, -96, 96, 160, 96),
                            e -> e instanceof net.minecraft.world.entity.Display && !e.getTags().contains("aicompanion_realm_label")).size(),
                    mc.level.getBlockState(new BlockPos(1, 48, 0)).getBlock(), mc.level.getBlockState(new BlockPos(2, 48, 0)).getBlock());
        });
        // Sidoh at home on the lip of the big hole: he greets the dreamer, and talks when clicked
        add(10, () -> cmd("tp @s -36.5 50 -32.5 -45 -10"));
        waitFor(100, () -> HEARD.stream().anyMatch(l -> l.contains("Сидо") || l.contains("Sidoh")), () -> {
            SidohEntity s = nearestSidoh();
            if (s != null) {
                lookAt(s);
            }
        });
        add(10, () -> {
            SidohEntity s = nearestSidoh();
            if (s != null) {
                mc.gameMode.interact(mc.player, s, InteractionHand.MAIN_HAND);
            }
        });
        add(15, () -> {
            java.util.List<String> said = HEARD.stream().filter(l -> l.contains("Сидо") || l.contains("Sidoh")).toList();
            if (said.size() >= 2) {
                AiCompanion.LOGGER.info("AUTOTEST realm: Sidoh says {}", said);
            } else {
                AiCompanion.LOGGER.error("AUTOTEST realm: Sidoh at home said {}", said);
            }
            shot("rm1b_sidoh_home");
        });
        // 1. a window down to the humans, and a look through it
        add(10, () -> cmd("tp @s -18.5 50 -25.5 90 22"));
        add(30, () -> shot("rm2_window"));
        add(5, () -> cmd("tp @s -22.8 51 -25.5 90 62")); // on the lip, looking down at the world of the living
        add(30, () -> shot("rm3_window_down"));
        add(5, () -> {
            mc.options.hideGui = false;
            cmd("tp @s -19.5 50 -25.5 90 30");
        });
        add(10, () -> useOn(new BlockPos(-22, 50, -26)));
        add(15, () -> shot("rm4_vision"));
        // 2. the bone toss with Gook and Deridovely
        add(5, () -> cmd("tp @s -15.5 50 1.5 180 12"));
        add(10, () -> {
            select(slotOf(st -> st.is(Items.APPLE)));
            mc.options.hideGui = true;
        });
        add(5, () -> useOn(new BlockPos(-16, 51, -4)));
        add(12, () -> shot("rm5_bones"));
        add(30, () -> shot("rm6_bones_after"));
        // 3. Armonia on his throne: the rules, and the King's tribute
        add(5, () -> cmd("tp @s 8.5 50 -5.5 -90 -18"));
        add(30, () -> shot("rm7_throne"));
        add(5, () -> cmd("tp @s 13.5 51 -5.5 -90 -20")); // up the first step: within reach
        add(5, () -> {
            mc.options.hideGui = false;
            RealmGod armonia = god("armonia");
            AiCompanion.LOGGER.info("AUTOTEST realm: Armonia {}", armonia == null ? "missing" : armonia.position());
            if (armonia != null) {
                mc.gameMode.interact(mc.player, armonia, InteractionHand.MAIN_HAND);
            }
        });
        add(15, () -> shot("rm8_armonia_talks"));
        // 4. the King, from under the ribs
        add(5, () -> {
            mc.options.hideGui = true;
            cmd("tp @s 0.5 50 24.5 0 -28");
        });
        add(40, () -> shot("rm9_king"));
        add(5, () -> cmd("tp @s 0.5 50 9.5 0 -12"));
        add(30, () -> shot("rm10_ribs"));
        // 5. the dead tree and its apple
        add(5, () -> cmd("tp @s 14.5 50 5.5 0 -24"));
        add(30, () -> shot("rm11_tree"));
        add(5, () -> cmd("tp @s 14.5 50 8.5 0 0"));
        add(5, () -> {
            mc.options.hideGui = false;
            useOn(new BlockPos(14, 51, 12));
        });
        add(20, () -> {
            shot("rm12_realm_done");
            AiCompanion.LOGGER.info("AUTOTEST realm: after the five sights stage {}, seen {}, king {}, realm apples held {}",
                    onServer(() -> SideQuests.stage(me(), "realm")), onServer(() -> Long.bitCount(Kira.of(me()).flags.getOrDefault("q_realm_obj", 0L))),
                    onServer(() -> SideQuests.stage(me(), "king")), mc.player.getInventory().countItem(AiCompanion.REALM_APPLE.get()));
        });
        // the King's tribute: thirteen apples into the bowl at his feet
        add(60, () -> {
            cmd("give @s minecraft:apple 13");
            cmd("tp @s 0.5 50 29.5 0 10");
        });
        add(10, () -> select(slotOf(st -> st.is(Items.APPLE))));
        add(5, () -> useOn(new BlockPos(0, 51, 33)));
        add(10, () -> {
            shot("rm13_tribute");
            AiCompanion.LOGGER.info("AUTOTEST realm: apples left after the bowl {}", mc.player.getInventory().countItem(Items.APPLE));
        });
        add(5, () -> {
            mc.options.hideGui = true;
            cmd("tp @s 0.5 50 26.5 0 -30");
        });
        add(10, () -> shot("rm14_king_roars"));
        add(5, () -> AiCompanion.LOGGER.info("AUTOTEST realm: films seen {}: {}", Cinema.seen(), onServer(() -> Kira.of(me()).flags.keySet().stream()
                .filter(k -> k.startsWith("film_")).sorted().toList())));
        add(30, () -> AiCompanion.LOGGER.info("AUTOTEST realm: tribute {} of {}, king stage {}, eyes {}",
                onServer(() -> Kira.of(me()).flags.getOrDefault("q_king_n", 0L)), SideQuests.TRIBUTE, onServer(() -> SideQuests.stage(me(), "king")),
                onServer(() -> Kira.of(me()).eyes)));
        add(5, () -> cmd("tp @s 20.5 52 -40.5 125 12")); // a window and the waste from the north-east
        add(30, () -> shot("rm15_east"));
        add(5, () -> cmd("tp @s -27.5 50 31.5 113 -6")); // the giant's skull
        add(30, () -> shot("rm16_skull"));
        // a jump into the window by the plaza: awake
        add(5, () -> {
            mc.options.hideGui = false;
            cmd("tp @s 0.5 51 -13.5 180 60");
        });
        waitFor(300, () -> !mc.level.dimension().location().getPath().equals("shinigami_realm"), () -> { });
        add(60, () -> {
            shot("rm17_woke");
            AiCompanion.LOGGER.info("AUTOTEST realm: woke in {}, mode {}", mc.level.dimension().location(), mc.gameMode.getPlayerMode());
            cmd("summon minecraft:pig ~3 ~ ~2");
            cmd("summon minecraft:villager ~-3 ~ ~3");
            cmd("summon minecraft:cow ~ ~ ~5");
            cmd("tp @s ~ ~ ~ 0 -70");
        });
        // the realm's apple: eaten, and every life around shows
        add(20, () -> select(slotOf(st -> st.is(AiCompanion.REALM_APPLE.get()))));
        add(5, () -> KeyMapping.set(mc.options.keyUse.getKey(), true));
        add(45, () -> KeyMapping.set(mc.options.keyUse.getKey(), false));
        add(10, () -> {
            cmd("tp @s ~ ~ ~ 0 12"); // toward the animals: every life outlined
            AiCompanion.LOGGER.info("AUTOTEST realm: ate the apple: held {}, glowing around {}", mc.player.getInventory().countItem(AiCompanion.REALM_APPLE.get()),
                    onServer(() -> me().serverLevel().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, me().getBoundingBox().inflate(32),
                            e -> e.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING)).size()));
        });
        add(20, () -> shot("rm18_lives_glow"));
        // another one, held out to Ryuk: he sulks
        add(5, () -> cmd("give @s aicompanion:realm_apple"));
        add(10, () -> {
            select(slotOf(st -> st.is(AiCompanion.REALM_APPLE.get())));
            lookAt(ryuk());
        });
        add(5, () -> mc.gameMode.interact(mc.player, ryuk(), InteractionHand.MAIN_HAND));
        add(15, () -> {
            shot("rm19_ryuk_refuses");
            AiCompanion.LOGGER.info("AUTOTEST realm: Ryuk offered the realm's apple: action {}, still held {}, case lines {}", ryuk().getAction(),
                    mc.player.getInventory().countItem(AiCompanion.REALM_APPLE.get()), onServer(() -> SideQuests.describe(me())));
        });
        // a second dream, the realm taken for one from an older version (its mark gone): rebuilt over itself, nothing doubled
        add(20, () -> {
            cmd("execute in aicompanion:shinigami_realm run setblock 0 48 0 minecraft:air");
            cmd("companion kira dream");
        });
        waitFor(400, () -> mc.level.dimension().location().getPath().equals("shinigami_realm"), () -> { });
        add(200, () -> {
            java.util.Map<String, Long> gods = mc.level.getEntitiesOfClass(RealmGod.class, mc.player.getBoundingBox().inflate(64)).stream()
                    .collect(java.util.stream.Collectors.groupingBy(RealmGod::who, java.util.stream.Collectors.counting()));
            long apples = mc.level.getEntitiesOfClass(net.minecraft.world.entity.Display.ItemDisplay.class,
                    new net.minecraft.world.phys.AABB(new BlockPos(14, 50, 12)).inflate(8)).size();
            AiCompanion.LOGGER.info("AUTOTEST realm: second dream over a rebuilt realm: gods {}, apples on the tree {}, marked again {}", gods, apples,
                    onServer(() -> me().server.getLevel(ru.maksim.aicompanion.Realm.KEY).getBlockState(new BlockPos(0, 48, 0)).getBlock()));
            shot("rm20_second_dream");
            cmd("companion kira wake");
        });
        add(40, () -> {
            AiCompanion.LOGGER.info("AUTOTEST DONE");
            mc.stop();
        });
    }

    /** One of the realm's gods, as the client sees him. */
    @Nullable
    private static RealmGod god(String who) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level.getEntitiesOfClass(RealmGod.class, mc.player.getBoundingBox().inflate(48), g -> g.who().equals(who)).stream()
                .findFirst().orElse(null);
    }

    /** A right click on the top of a block, with whatever is in the main hand. */
    private static void useOn(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static String savedKey = "";

    /**
     * Setting Ryuk up in the game (env AICOMPANION_AUTOTEST=setup): into a world with no key, the setup with its guide
     * opens by itself; a made-up key is turned away (and not saved), the real one works; the settings page; "/ryuk".
     */
    private static void setup() {
        Minecraft mc = Minecraft.getInstance();
        waitFor(400, () -> mc.screen instanceof SetupScreen, () -> AiCompanion.LOGGER.info("AUTOTEST setup opened by itself: {}", mc.screen));
        add(20, () -> shot("s1_first_join"));
        add(5, () -> {
            if (mc.screen instanceof SetupScreen s) {
                s.typeKey("gsk_" + "0".repeat(52)); // not a key anybody has
                s.check();
            }
        });
        add(100, () -> {
            shot("s2_refused");
            AiCompanion.LOGGER.info("AUTOTEST after a made-up key the config holds a key: {}", !Config.API_KEY.get().isBlank());
        });
        add(5, () -> {
            if (mc.screen instanceof SetupScreen s) {
                s.typeKey(savedKey);
                s.check();
            }
        });
        add(100, () -> {
            shot("s3_works");
            AiCompanion.LOGGER.info("AUTOTEST the real key is back in the config: {}", Config.API_KEY.get().equals(savedKey));
        });
        add(5, () -> {
            if (mc.screen instanceof SetupScreen s) {
                s.page(true);
            }
        });
        add(10, () -> shot("s4_settings"));
        add(5, () -> { // the other AIs, as shown (nothing saved without a key that checks out)
            if (mc.screen instanceof SetupScreen s) {
                s.page(false);
                s.choose(ru.maksim.aicompanion.Provider.CLAUDE);
            }
        });
        add(10, () -> shot("s6_claude"));
        add(5, () -> {
            if (mc.screen instanceof SetupScreen s) {
                s.choose(ru.maksim.aicompanion.Provider.CUSTOM);
            }
        });
        add(10, () -> shot("s7_custom"));
        add(5, () -> mc.setScreen(null));
        add(20, () -> cmd("ryuk"));
        waitFor(100, () -> mc.screen instanceof SetupScreen, () -> shot("s5_ryuk_command"));
        add(10, () -> {
            mc.setScreen(null);
            mc.stop();
        });
    }

    /**
     * The eyes on one's own skin (env AICOMPANION_AUTOTEST=eyes): the deal, the editor with the eyes found on the face,
     * a pixel lit by hand, the glow from the front in third person, and the editor again from the notebook.
     */
    private static void eyes() {
        Minecraft mc = Minecraft.getInstance();
        add(60, () -> {
            cmd("gamerule doDaylightCycle false");
            cmd("time set 18000"); // night: the glow shows best
            cmd("tp @s 0.5 -60 0.5 0 0");
            cmd("give @s aicompanion:death_note");
        });
        add(40, () -> cmd("companion kira eyes"));
        dealEyes();
        waitFor(300, () -> mc.screen instanceof EyeLookScreen, () -> AiCompanion.LOGGER.info("AUTOTEST eye editor: {}", mc.screen));
        add(20, () -> shot("e1_editor_found"));
        add(5, () -> {
            if (mc.screen instanceof EyeLookScreen s) {
                s.paint(3, 2, 2); // a dim pixel between the brows, by hand
            }
        });
        add(10, () -> shot("e2_editor_painted"));
        add(5, () -> mc.setScreen(null)); // done: saved for this skin
        add(20, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        add(40, () -> shot("e3_front"));
        add(5, () -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            DeathNoteScreen.open(InteractionHand.MAIN_HAND);
        });
        add(20, () -> shot("e4_notebook_button"));
        add(5, () -> mc.setScreen(new EyeLookScreen()));
        add(20, () -> shot("e5_editor_again"));
        add(5, () -> {
            if (mc.screen instanceof EyeLookScreen s) {
                s.inset(true);
            }
        });
        add(10, () -> shot("e6_editor_inset"));
        add(5, () -> {
            mc.setScreen(null);
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        });
        add(140, () -> { // the reader has put Ryuk's card away by now
            shot("e7_front_inset");
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        });
        add(20, () -> mc.stop());
    }

    /** The notebook lying (or falling) in the world near the player, if any. */
    @javax.annotation.Nullable
    private static ItemEntity fallenNote() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level.getEntitiesOfClass(ItemEntity.class, mc.player.getBoundingBox().inflate(16, 32, 16),
                i -> i.getItem().is(AiCompanion.DEATH_NOTE.get())).stream().findFirst().orElse(null);
    }

    private static net.minecraft.server.level.ServerPlayer me() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
    }

    /** Every tick until {@code done} holds (at most {@code max} ticks), then {@code then}. */
    private static void waitFor(int max, java.util.function.BooleanSupplier done, Runnable then) {
        int[] left = {max};
        Runnable[] poll = new Runnable[1];
        poll[0] = () -> {
            if (done.getAsBoolean() || --left[0] <= 0) {
                if (left[0] <= 0) {
                    AiCompanion.LOGGER.warn("AUTOTEST waited {} ticks in vain", max);
                }
                then.run();
            } else {
                STEPS.addFirst(new Step(1, poll[0]));
            }
        };
        add(1, poll[0]);
    }

    /** The true names the eyes see right now on the entities that pass {@code which}, ready for {@link #write}. */
    private static void names(java.util.function.Predicate<net.minecraft.world.entity.Entity> which) {
        NAMES.clear();
        NAMES.addAll(ClientKira.sightNames(which));
        AiCompanion.LOGGER.info("AUTOTEST seen: {}", NAMES);
    }

    private static void writeNames(int n) {
        for (int i = 0; i < n; i++) {
            int k = i;
            add(25, () -> write(k));
        }
    }

    private static net.minecraft.server.level.ServerLevel overworld() {
        return Minecraft.getInstance().getSingleplayerServer().overworld();
    }

    /** Runs on the server thread and waits for it (the test's view into the world it plays). */
    private static <T> T onServer(java.util.function.Supplier<T> work) {
        return Minecraft.getInstance().getSingleplayerServer().submit(work).join();
    }

    /** The ground at x, z: where a player can stand. */
    private static BlockPos surface(int x, int z) {
        return onServer(() -> {
            overworld().getChunk(x >> 4, z >> 4); // generated first: an unloaded chunk says "the bottom of the world"
            return overworld().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        });
    }

    private static void tp(BlockPos p, float yaw, float pitch) {
        cmd(String.format(Locale.ROOT, "tp @s %.1f %d %.1f %.0f %.0f", p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw, pitch));
    }

    @javax.annotation.Nullable
    private static Long flagLong(String name) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.server.level.ServerPlayer sp = mc.getSingleplayerServer() == null ? null
                : mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        return sp == null ? null : ru.maksim.aicompanion.Kira.of(sp).flags.get(name);
    }

    @javax.annotation.Nullable
    private static BlockPos flagOrNull(String name) {
        Long v = flagLong(name);
        return v == null ? null : BlockPos.of(v);
    }

    /** A place of the story, by its flag in the server's case file (the test runs in the same game). */
    private static BlockPos flag(String name) {
        BlockPos p = flagOrNull(name);
        if (p == null) {
            AiCompanion.LOGGER.error("AUTOTEST no flag {}", name);
            return BlockPos.ZERO;
        }
        return p;
    }

    /** Writes the n-th name seen in the room with whatever is in the main hand. */
    private static void write(int n) {
        String name = n < NAMES.size() ? NAMES.get(n) : "nobody";
        if (Minecraft.getInstance().player.getMainHandItem().isEmpty()) { // a stray scroll over the window moved the hotbar
            select(slotOf(s -> s.is(AiCompanion.DEATH_NOTE.get())));
        }
        AiCompanion.LOGGER.info("AUTOTEST writing {} with {}", name, Minecraft.getInstance().player.getMainHandItem());
        Network.CHANNEL.sendToServer(new Network.WriteName(InteractionHand.MAIN_HAND, name, ""));
    }

    private static int slotOf(java.util.function.Predicate<ItemStack> what) {
        Inventory inv = Minecraft.getInstance().player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (what.test(inv.getItem(i))) {
                return i;
            }
        }
        AiCompanion.LOGGER.error("AUTOTEST nothing like that on the hotbar");
        return inv.selected;
    }

    /** Takes a hotbar slot in hand, on both sides (the server learns of it before the next packet). */
    private static void select(int slot) {
        Minecraft mc = Minecraft.getInstance();
        mc.player.getInventory().selected = slot;
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
    }

    private static HumanEntity person(String skinPrefix) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level.getEntitiesOfClass(HumanEntity.class, mc.player.getBoundingBox().inflate(96), h -> h.skin().startsWith(skinPrefix))
                .stream().findFirst().orElse(null);
    }

    /** Runs a /companion command, then takes (delay, name) shots. */
    private static void pose(String command, Object... shots) {
        add(20, () -> cmd("companion " + command));
        for (int i = 0; i < shots.length; i += 2) {
            String name = (String) shots[i + 1];
            add((Integer) shots[i], () -> shot(name));
        }
    }

    private static void say(String text, String shotName) {
        add(20, () -> Minecraft.getInstance().player.connection.sendChat(text));
        add(45, () -> shot(shotName)); // the LLM answers in about a second
    }

    /** The notebook: its screen, then two named pigs written down (heart attack, lightning) and their deaths 40 s later. */
    private static void deathNote() {
        Minecraft mc = Minecraft.getInstance();
        boolean en = Config.english();
        String pig1 = en ? "Oinky" : "Хрюн", pig2 = en ? "Bacon" : "Бекон";
        add(20, () -> {
            cmd("companion follow");
            cmd("summon minecraft:pig ^1 ^ ^5 {CustomName:'\"" + pig1 + "\"',NoAI:1b}");
            cmd("summon minecraft:pig ^-1 ^ ^5 {CustomName:'\"" + pig2 + "\"',NoAI:1b}");
        });
        add(20, () -> DeathNoteScreen.open(InteractionHand.MAIN_HAND));
        add(10, () -> shot("50_note_open"));
        add(2, () -> {
            mc.setScreen(null);
            Network.CHANNEL.sendToServer(new Network.WriteName(InteractionHand.MAIN_HAND, pig1, ""));
        });
        add(30, () -> Network.CHANNEL.sendToServer(new Network.WriteName(InteractionHand.MAIN_HAND, pig2, en ? "lightning" : "молния")));
        add(20, () -> DeathNoteScreen.open(InteractionHand.MAIN_HAND));
        add(10, () -> shot("51_note_written"));
        add(2, () -> mc.setScreen(null));
        add(700, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        add(44, () -> shot("52_heart_attack")); // the pig written first falls over 40 s after the packet
        add(24, () -> shot("53_lightning"));
        add(3, () -> shot("53b_lightning"));
        add(40, () -> shot("54_after"));
    }

    private static void add(int delay, Runnable run) {
        STEPS.add(new Step(delay, run));
    }

    private static void cmd(String command) {
        AiCompanion.LOGGER.info("AUTOTEST /{}", command);
        Minecraft.getInstance().player.connection.sendCommand(command);
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "autotest_" + name + ".png", mc.getMainRenderTarget(),
                msg -> AiCompanion.LOGGER.info("AUTOTEST shot {}", name));
    }

    private static CompanionEntity ryuk() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level.getEntitiesOfClass(CompanionEntity.class, mc.player.getBoundingBox().inflate(48)).stream().findFirst().orElseThrow();
    }

    private static boolean aimedAtRyuk(Minecraft mc) {
        return mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof CompanionEntity;
    }

    /** Turns the player's view onto someone's chest without moving. */
    private static void lookAt(net.minecraft.world.entity.Entity r) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 d = r.position().add(0, 1.2, 0).subtract(mc.player.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
        cmd(String.format(Locale.ROOT, "tp @s ~ ~ ~ %.1f %.1f", yaw, pitch));
    }

    /** Puts the camera on the ground {@code dist} blocks from Ryuk, {@code angle} degrees around him, looking at his face. */
    private static void face(double dist, double angle) {
        Minecraft mc = Minecraft.getInstance();
        CompanionEntity ryuk = mc.level.getEntitiesOfClass(CompanionEntity.class, mc.player.getBoundingBox().inflate(48)).stream()
                .findFirst().orElse(null);
        if (ryuk == null) {
            AiCompanion.LOGGER.error("AUTOTEST no companion to face");
            return;
        }
        double a = Math.toRadians(ryuk.getYRot() + angle);
        Vec3 eye = new Vec3(ryuk.getX() - Math.sin(a) * dist, -60 + 1.62, ryuk.getZ() + Math.cos(a) * dist);
        Vec3 d = new Vec3(ryuk.getX(), ryuk.getY() + 1.6, ryuk.getZ()).subtract(eye);
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);
        cmd(String.format(Locale.ROOT, "tp @s %.2f -60 %.2f %.1f %.1f", eye.x, eye.z, yaw, pitch));
    }
}
