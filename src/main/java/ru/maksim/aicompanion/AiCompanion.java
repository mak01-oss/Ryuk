package ru.maksim.aicompanion;

import com.mojang.logging.LogUtils;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(AiCompanion.MODID)
public class AiCompanion {
    public static final String MODID = "aicompanion";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final double HEAR_RADIUS = 32.0;
    private static final Map<UUID, String> LAST_DEATH = new HashMap<>(); // player -> how they died, for the respawn remark

    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    /** His synthesized voice; the sounds.json entry is a placeholder, the audio is streamed from memory. */
    public static final RegistryObject<SoundEvent> RYUK_VOICE = SOUNDS.register("ryuk.voice",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "ryuk.voice")));
    public static final RegistryObject<EntityType<CompanionEntity>> COMPANION = ENTITIES.register("companion",
            () -> EntityType.Builder.of(CompanionEntity::new, MobCategory.MISC).sized(0.7F, 2.6F).fireImmune()
                    .clientTrackingRange(10).updateInterval(2) // smooth flight next to a running player, like the allay
                    .build("companion"));
    public static final RegistryObject<Item> DEATH_NOTE = ITEMS.register("death_note",
            () -> new DeathNoteItem(new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC)));
    public static final RegistryObject<EntityType<HumanEntity>> HUMAN = ENTITIES.register("human",
            () -> EntityType.Builder.of(HumanEntity::new, MobCategory.MISC).sized(0.6F, 1.8F).clientTrackingRange(10).build("human"));
    public static final RegistryObject<EntityType<SidohEntity>> SIDOH = ENTITIES.register("sidoh",
            () -> EntityType.Builder.of(SidohEntity::new, MobCategory.MISC).sized(0.8F, 2.4F).fireImmune().clientTrackingRange(10)
                    .build("sidoh"));
    public static final RegistryObject<Item> PAGE = ITEMS.register("death_note_page",
            () -> new ScrapItem(ScrapItem.Kind.PAGE, new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> WATCH = ITEMS.register("kira_watch",
            () -> new ScrapItem(ScrapItem.Kind.WATCH, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> CHIPS_SECRET = ITEMS.register("chips_with_page",
            () -> new ScrapItem(ScrapItem.Kind.CHIPS, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> CHOCOLATE = ITEMS.register("chocolate", // Mello's, and Sidoh's weakness
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(4).saturationMod(0.4F).fast().build())));
    public static final RegistryObject<Item> CHIPS = ITEMS.register("potato_chips",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(3).saturationMod(0.3F).fast().build())));
    public static final RegistryObject<Block> CAMERA = BLOCKS.register("camera",
            () -> new CameraBlock(BlockBehaviour.Properties.of().strength(0.5F).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> DRAWER = BLOCKS.register("hidden_drawer",
            () -> new Hideouts.DrawerBlock(BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.WOOD)));
    public static final RegistryObject<BlockEntityType<Hideouts.Drawer>> DRAWER_ENTITY = BLOCK_ENTITIES.register("hidden_drawer",
            () -> BlockEntityType.Builder.of(Hideouts.Drawer::new, DRAWER.get()).build(null));
    public static final RegistryObject<Item> SIDOH_PAGE = ITEMS.register("sidoh_page", // side quest "Sidoh's pages"
            () -> new QuestItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON), "item.aicompanion.sidoh_page.desc", false));
    public static final RegistryObject<Item> SIDOH_BANDAGE = ITEMS.register("sidoh_bandage", // its reward: no face to read
            () -> new QuestItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), "item.aicompanion.sidoh_bandage.desc", true));
    public static final RegistryObject<EntityType<LostPage>> LOST_PAGE = ENTITIES.register("lost_page",
            () -> EntityType.Builder.<LostPage>of(LostPage::new, MobCategory.MISC).sized(0.6F, 0.6F).clientTrackingRange(8).updateInterval(2)
                    .build("lost_page"));
    public static final RegistryObject<EntityType<RealmGod>> REALM_GOD = ENTITIES.register("realm_god", // the realm's gods: Gook, Deridovely, Armonia
            () -> EntityType.Builder.of(RealmGod::new, MobCategory.MISC).sized(0.8F, 2.4F).fireImmune().clientTrackingRange(10).build("realm_god"));
    public static final RegistryObject<Item> REALM_APPLE = ITEMS.register("realm_apple", // the dead tree's fruit: sand, and a moment of a god's senses
            () -> new QuestItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)
                    .food(new FoodProperties.Builder().nutrition(1).saturationMod(0.1F).alwaysEat().build()), "item.aicompanion.realm_apple.desc", false));
    public static final RegistryObject<Block> TELEVISION = BLOCKS.register("television", // the news reaches only a set switched on
            () -> new TelevisionBlock(BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.METAL).noOcclusion()
                    .lightLevel(s -> s.getValue(TelevisionBlock.ON) ? 8 : 0)));
    public static final RegistryObject<Item> TELEVISION_ITEM = ITEMS.register("television", () -> new TelevisionBlock.Portable(TELEVISION.get(), new Item.Properties()));
    public static final RegistryObject<Item> CAMERA_ITEM = ITEMS.register("camera", () -> new BlockItem(CAMERA.get(), new Item.Properties()));
    public static final RegistryObject<Item> DRAWER_ITEM = ITEMS.register("hidden_drawer", () -> new BlockItem(DRAWER.get(), new Item.Properties()));

    public AiCompanion(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        Sounds.REG.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent e) -> {
            e.put(COMPANION.get(), CompanionEntity.createAttributes().build());
            e.put(HUMAN.get(), HumanEntity.createAttributes().build());
            e.put(SIDOH.get(), SidohEntity.createAttributes().build());
            e.put(REALM_GOD.get(), RealmGod.createAttributes().build());
        });
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                for (RegistryObject<Item> item : List.of(DEATH_NOTE, PAGE, WATCH, CHIPS_SECRET, DRAWER_ITEM, CAMERA_ITEM, TELEVISION_ITEM, SIDOH_BANDAGE, REALM_APPLE)) {
                    e.accept(item);
                }
            } else if (e.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
                e.accept(CHIPS);
                e.accept(CHOCOLATE);
            }
        });
        Network.register();
        Llm.onUsage = (model, usage) -> LOGGER.info("{} tokens: {}", model, usage); // with Claude: how much came from the cache
        MinecraftForge.EVENT_BUS.addListener(AiCompanion::onChat);
        MinecraftForge.EVENT_BUS.addListener(AiCompanion::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(AiCompanion::onDeath);
        MinecraftForge.EVENT_BUS.addListener(AiCompanion::onRespawn);
        MinecraftForge.EVENT_BUS.addListener(AiCompanion::onAte);
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) {
                DeathNotes.tick(e.getServer());
                Arrival.tick(e.getServer());
                Epilogue.tick(e.getServer());
                Builds.tick(e.getServer());
                DiceGame.tick(e.getServer());
                if (StoryTest.ON) {
                    StoryTest.tick(e.getServer());
                }
                if (e.getServer().getTickCount() % 20 == 0) {
                    Lifespans.tick(e.getServer());
                    News.tick(e.getServer());
                    Cameras.tick(e.getServer().overworld());
                    for (ServerPlayer player : e.getServer().getPlayerList().getPlayers()) {
                        Onboarding.tick(player);
                        Pact.tick(player);
                        Investigation.tick(player);
                        Headquarters.tick(player);
                        Mafia.story(player);
                        Successors.tick(player);
                        Worship.tick(player);
                        Realm.tick(player);
                        SideQuests.tick(player);
                    }
                }
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) {
                Realm.wake(player); // a dream doesn't survive leaving the game
                Pact.onLogin(player);
                Epilogue.login(player); // a film quit midway: back where it ended, as they were
                if (StoryTest.ON) {
                    StoryTest.login(player);
                }
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock e) -> {
            if (e.getEntity() instanceof ServerPlayer player && Realm.use(player, e.getPos(), e.getHand())) {
                e.setCanceled(true);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteractSpecific e) -> {
            if (e.getTarget() instanceof ArmorStand stand && stand.getTags().contains("aicompanion_realm")) {
                e.setCanceled(true); // the figures keep their clothes
                if (e.getEntity() instanceof ServerPlayer player && e.getHand() == InteractionHand.MAIN_HAND) {
                    Realm.talk(player, stand);
                }
            }
        });
        MinecraftForge.EVENT_BUS.addListener((LivingAttackEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player && Realm.dreaming(player)
                    && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
                e.setCanceled(true); // nothing hurts in a dream, except a name in a notebook
            }
        });
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> CompanionCommand.register(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.server.ServerStoppedEvent e) -> Builds.clear());
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> ru.maksim.aicompanion.client.SetupScreen::registerInModList); // the Mods list's "Config" button
    }

    /**
     * The nearest companion within HEAR_RADIUS answers; chat far from any companion is ignored. A talk with L or Near is
     * theirs: Ryuk stays out of it unless called by name.
     */
    private static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        if (Headquarters.hear(player, event.getRawText()) // L listens to whoever talks next to him
                && !event.getRawText().toLowerCase(java.util.Locale.ROOT).contains(Config.name().toLowerCase(java.util.Locale.ROOT))) {
            return;
        }
        player.serverLevel().getEntitiesOfClass(CompanionEntity.class, player.getBoundingBox().inflate(HEAR_RADIUS)).stream()
                .min(Comparator.comparingDouble(player::distanceToSqr))
                .ifPresent(companion -> companion.hear(player, event.getRawText()));
    }

    /**
     * The owner's side of the bond, once a second: brings the body along when it can't tick by itself (left behind in an
     * unloaded chunk or another dimension) and grows a new one if it's gone for 3 s. The mind and the chat history stay.
     */
    private static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 20 != 0 || !player.isAlive()) {
            return;
        }
        RyukMinds.Mind mind = RyukMinds.get(player.server).get(player.getUUID());
        if (mind == null || Kira.of(player).flag("renounced")) {
            return; // no shinigami yet, or the human gave up the notebook and he has left
        }
        CompanionEntity body = CompanionEntity.find(player.server, mind.body);
        if (body != null) {
            mind.missingChecks = 0;
            body.keepUp(player);
        } else if (++mind.missingChecks >= 3) {
            CompanionEntity.summon(player, false);
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        MinecraftServer server = dead.level().getServer();
        if (server == null) {
            return; // client side
        }
        if (dead instanceof ServerPlayer player && Kira.of(player).flag("finishing")) {
            Pact.seal(player); // the name is already written: dying any other way first changes nothing
        }
        DeathNotes.forget(server, dead.getUUID()); // died some other way first: that line is spent
        if (dead instanceof ServerPlayer player) {
            Pact.thrill(player, Pact.THRILL_SMALL); // he does enjoy watching his human die
            LAST_DEATH.put(player.getUUID(), event.getSource().getLocalizedDeathMessage(player).getString());
        } else if (event.getSource().getEntity() instanceof ServerPlayer killer
                && (dead instanceof net.minecraft.world.entity.npc.AbstractVillager || dead instanceof HumanEntity)) { // a person, by hand; a creeper is no news
            CompanionEntity ryuk = CompanionEntity.of(killer);
            if (ryuk != null) {
                ryuk.remark(Config.tr("твой человек " + killer.getGameProfile().getName() + " только что убил существо: ",
                        "your human " + killer.getGameProfile().getName() + " just killed a creature: ") + dead.getName().getString(), "kill", 2400);
            }
        }
    }

    /** Death is final for a shinigami, and here the human just gets up again. */
    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Pact.onRespawn(player);
        if (Kira.of(player).sealed) {
            return; // a ghost now: nothing to remark on
        }
        CompanionEntity ryuk = CompanionEntity.of(player);
        String how = LAST_DEATH.remove(player.getUUID());
        if (ryuk != null) {
            ryuk.remark(Config.tr(how == null ? "твой человек погиб" : "твой человек погиб: " + how,
                    how == null ? "your human died" : "your human died: " + how)
                    + Config.tr(" и тут же снова встал живым. Для тебя смерть необратима.",
                    " and got right back up alive. For you death is final."), "respawn", 1200);
        }
    }

    /** The owner eats an apple right in front of him. */
    private static void onAte(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getItem().is(REALM_APPLE.get())) {
            SideQuests.ateRealmApple(player);
        }
        if (event.getEntity() instanceof ServerPlayer player && CompanionEntity.isApple(event.getItem())) {
            CompanionEntity ryuk = CompanionEntity.of(player);
            if (ryuk != null) {
                ryuk.remark(Config.tr("твой человек сам съел ", "your human ate this right in front of you: ")
                        + CompanionEntity.itemName(event.getItem()) + Config.tr(" у тебя на глазах", ""), "owner_apple", 2400);
            }
        }
    }
}
