package ru.maksim.aicompanion;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Client → server: a name written in the Death Note (checked again on the server), answers to the deal and the pact.
 * Server → client: Ryuk says a line; the player's fate; what the shinigami eyes see; a choice to make.
 */
public final class Network {
    private static final String VERSION = "11";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(AiCompanion.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private Network() {
    }

    static void register() {
        CHANNEL.registerMessage(0, WriteName.class, WriteName::encode, WriteName::decode, WriteName::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Speak.class, Speak::encode, Speak::decode, Speak::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, Fate.class, Fate::encode, Fate::decode, Fate::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, Sight.class, Sight::encode, Sight::decode, Sight::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(4, Offer.class, Offer::encode, Offer::decode, Offer::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(5, Answer.class, Answer::encode, Answer::decode, Answer::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(6, TearPage.class, TearPage::encode, TearPage::decode, TearPage::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(7, AskCase.class, (m, b) -> { }, b -> new AskCase(), AskCase::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(8, Case.class, Case::encode, Case::decode, Case::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(9, Broadcast.class, Broadcast::encode, Broadcast::decode, Broadcast::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(10, DiceTable.class, DiceTable::encode, DiceTable::decode, DiceTable::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(11, DiceMove.class, DiceMove::encode, DiceMove::decode, DiceMove::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(12, Cue.class, Cue::encode, Cue::decode, Cue::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(13, Spoken.class, Spoken::encode, Spoken::decode, Spoken::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(14, Floor.class, Floor::encode, Floor::decode, Floor::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(15, Skip.class, (m, b) -> { }, b -> new Skip(), Skip::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    /** The story-test build's key (client/StorySkip): the story moves on to its next moment. Ignored by the normal build. */
    public record Skip() {
        void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().enqueueWork(() -> {
                if (player != null && StoryTest.ON) {
                    StoryTest.next(player);
                }
            });
            context.get().setPacketHandled(true);
        }
    }

    private static final Map<java.util.UUID, Long> BUSY = new java.util.concurrent.ConcurrentHashMap<>(); // whose screen has a card up, since when

    /** The player's screen has the story's floor taken (a card, the show) or free again: Ryuk's gestures wait for it. */
    public record Floor(boolean free) {
        void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(free);
        }

        static Floor decode(FriendlyByteBuf buf) {
            return new Floor(buf.readBoolean());
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().enqueueWork(() -> {
                if (player != null) {
                    if (free) {
                        BUSY.remove(player.getUUID());
                        SENT.remove(player.getUUID());
                    } else {
                        BUSY.put(player.getUUID(), player.server.overworld().getGameTime());
                    }
                }
            });
            context.get().setPacketHandled(true);
        }
    }

    private static final Map<java.util.UUID, Long> SENT = new java.util.concurrent.ConcurrentHashMap<>(); // a card just sent: till when it counts as up

    /**
     * Is this player reading something (a card, a film, the show, the notebook, an offer)? As their screen says, not for
     * longer than three minutes (a client that went quiet); a card sent this moment counts at once, before the screen
     * has had time to say so (3 seconds at most, if it never shows: TV cards wait while one dreams).
     */
    public static boolean floorBusy(ServerPlayer player) {
        long now = player.server.overworld().getGameTime();
        Long since = BUSY.get(player.getUUID());
        return since != null && now - since < 3600 || now < SENT.getOrDefault(player.getUUID(), 0L) || Films.busy(player);
    }

    /** Ryuk's line {@code id} is being said on this player's screen now: his mouth and what he does may start. */
    public record Spoken(int entity, int id) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entity);
            buf.writeVarInt(id);
        }

        static Spoken decode(FriendlyByteBuf buf) {
            return new Spoken(buf.readVarInt(), buf.readVarInt());
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().enqueueWork(() -> {
                if (player != null && player.level().getEntity(entity) instanceof CompanionEntity ryuk) {
                    ryuk.presented(id, player);
                }
            });
            context.get().setPacketHandled(true);
        }
    }

    /** A moment's music on one player's screen: a sting ("sting.*") or a theme played through once ("music.*"). */
    public record Cue(String id) {
        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(id);
        }

        static Cue decode(FriendlyByteBuf buf) {
            return new Cue(buf.readUtf(64));
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ru.maksim.aicompanion.client.Soundtrack.cue(id)));
            context.get().setPacketHandled(true);
        }
    }

    public static void cue(ServerPlayer player, String id) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Cue(id));
    }

    /** The dice table (DiceGame): its phase, the player's apples, and once called the dice and what they pay. */
    public record DiceTable(int ryuk, int phase, int apples, int stake, int d1, int d2, int payout, int special) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(ryuk);
            buf.writeVarInt(phase);
            buf.writeVarInt(apples);
            buf.writeVarInt(stake);
            buf.writeVarInt(d1);
            buf.writeVarInt(d2);
            buf.writeVarInt(payout);
            buf.writeVarInt(special);
        }

        static DiceTable decode(FriendlyByteBuf buf) {
            return new DiceTable(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ru.maksim.aicompanion.client.DiceScreen.receive(this)));
            context.get().setPacketHandled(true);
        }
    }

    /** A move at the dice table: BET (arg: the stake), CALL (arg 1: even, 0: odd), LEAVE. */
    public record DiceMove(int op, int arg) {
        public static final int BET = 0, CALL = 1, LEAVE = 2;

        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(op);
            buf.writeVarInt(arg);
        }

        static DiceMove decode(FriendlyByteBuf buf) {
            return new DiceMove(buf.readVarInt(), buf.readVarInt());
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().enqueueWork(() -> {
                if (player != null) {
                    switch (op) {
                        case BET -> DiceGame.bet(player, arg);
                        case CALL -> DiceGame.call(player, arg == 1);
                        default -> DiceGame.close(player);
                    }
                }
            });
            context.get().setPacketHandled(true);
        }
    }

    /**
     * A moment of the story on the player's screen (client/StoryHud), so it's seen and not lost in the chat: a TV
     * bulletin with a face, a death on the news, a chapter card. {@code at}: where it happens (an arrow points there),
     * NOWHERE for nowhere; {@code face}: a skin texture, "L" for his letter, "" for none; {@code cue}: the sting it
     * comes with ("" for its kind's own, see client/Soundtrack).
     */
    public record Broadcast(int kind, Component title, Component text, String face, long at, String cue) {
        public static final int NEWS = 0, DEATH = 1, SPECIAL = 2, CHAPTER = 3, WAY = 4, SHOW = 5, QUEST = 6; // WAY: only the arrow; SHOW: L's special, on a TV; QUEST: a side quest's card
        public static final long NOWHERE = Long.MIN_VALUE;

        public Broadcast(int kind, Component title, Component text, String face, long at) {
            this(kind, title, text, face, at, "");
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(kind);
            buf.writeComponent(title);
            buf.writeComponent(text);
            buf.writeUtf(face);
            buf.writeLong(at);
            buf.writeUtf(cue);
        }

        static Broadcast decode(FriendlyByteBuf buf) {
            return new Broadcast(buf.readVarInt(), buf.readComponent(), buf.readComponent(), buf.readUtf(256), buf.readLong(), buf.readUtf(64));
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ru.maksim.aicompanion.client.StoryHud.show(this)));
            context.get().setPacketHandled(true);
        }
    }

    /** A card on one player's screen; {@code at}: its place, or null. */
    public static void card(ServerPlayer player, int kind, Component title, Component text, String face, @Nullable BlockPos at) {
        card(player, kind, title, text, face, at, "");
    }

    /** ... with its own sting ({@code cue}: "sting.l_dead", "music.requiem"...). */
    public static void card(ServerPlayer player, int kind, Component title, Component text, String face, @Nullable BlockPos at, String cue) {
        if (kind != Broadcast.WAY) {
            SENT.put(player.getUUID(), player.server.overworld().getGameTime() + 60);
        }
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Broadcast(kind, title, text, face, at == null ? Broadcast.NOWHERE : at.asLong(), cue));
    }

    /** The same card on every screen: the news. */
    public static void cardAll(int kind, Component title, Component text, String face) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new Broadcast(kind, title, text, face, Broadcast.NOWHERE));
    }

    /** The face of one of the story's people, for a card. */
    public static String face(String person) {
        return AiCompanion.MODID + ":textures/entity/people/" + person + ".png";
    }

    private static final Map<ServerPlayer, Integer> ASKED = new WeakHashMap<>(); // the last AskCase per player (server thread only)

    /** The notebook's "Cases" page was opened: what does it say now? */
    public record AskCase() {
        void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().enqueueWork(() -> {
                if (player != null && player.server.getTickCount() - ASKED.getOrDefault(player, -100) >= 10) {
                    ASKED.put(player, player.server.getTickCount()); // twice a second at most
                    CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), new Case(CaseFile.lines(player)));
                }
            });
            context.get().setPacketHandled(true);
        }
    }

    /** The "Cases" page, line by line (translated on the client). */
    public record Case(java.util.List<net.minecraft.network.chat.Component> lines) {
        void encode(FriendlyByteBuf buf) {
            buf.writeCollection(lines, FriendlyByteBuf::writeComponent);
        }

        static Case decode(FriendlyByteBuf buf) {
            return new Case(buf.readList(FriendlyByteBuf::readComponent));
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ru.maksim.aicompanion.client.ClientKira.caseFile(lines)));
            context.get().setPacketHandled(true);
        }
    }

    /** A page torn out of the notebook in that hand (the manga's notebook never runs out of pages). */
    public record TearPage(InteractionHand hand) {
        void encode(FriendlyByteBuf buf) {
            buf.writeEnum(hand);
        }

        static TearPage decode(FriendlyByteBuf buf) {
            return new TearPage(buf.readEnum(InteractionHand.class));
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().enqueueWork(() -> {
                if (player != null && player.getItemInHand(hand).is(AiCompanion.DEATH_NOTE.get()) && !player.getCooldowns().isOnCooldown(AiCompanion.PAGE.get())) {
                    player.getCooldowns().addCooldown(AiCompanion.PAGE.get(), 5); // four pages a second at most: no flood of items
                    ItemStack page = new ItemStack(AiCompanion.PAGE.get());
                    if (DeathNoteItem.fake(player.getItemInHand(hand))) {
                        page.getOrCreateTag().putBoolean("Fake", true); // a page of the copy is a copy too
                    }
                    if (!player.getInventory().add(page)) {
                        player.drop(page, false);
                    }
                    player.serverLevel().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN,
                            net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.6F);
                }
            });
            context.get().setPacketHandled(true);
        }
    }

    /**
     * The player's own fate, for their screens: eye deals, Ryuk's game, whether it is over (and why); {@code story}
     * bits: 1 = has touched Sidoh's notebook (sees him), 2 = knows the two lies among the rules.
     */
    public record Fate(int eyes, int pact, boolean sealed, String why, boolean pactOffered, int story) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(eyes);
            buf.writeVarInt(pact);
            buf.writeBoolean(sealed);
            buf.writeUtf(why, 256);
            buf.writeBoolean(pactOffered);
            buf.writeVarInt(story);
        }

        static Fate decode(FriendlyByteBuf buf) {
            return new Fate(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readUtf(256), buf.readBoolean(), buf.readVarInt());
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ru.maksim.aicompanion.client.ClientKira.fate(this)));
            context.get().setPacketHandled(true);
        }
    }

    /** What the shinigami eyes see right now: name and seconds of life over each human in view (-1: a note owner). */
    public record Sight(java.util.List<Entry> entries) {
        public record Entry(int entity, String name, int secondsLeft) {
        }

        void encode(FriendlyByteBuf buf) {
            buf.writeCollection(entries, (b, e) -> {
                b.writeVarInt(e.entity());
                b.writeUtf(e.name(), 64);
                b.writeVarInt(e.secondsLeft() + 1);
            });
        }

        static Sight decode(FriendlyByteBuf buf) {
            return new Sight(buf.readList(b -> new Entry(b.readVarInt(), b.readUtf(64), b.readVarInt() - 1)));
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ru.maksim.aicompanion.client.ClientKira.sight(this)));
            context.get().setPacketHandled(true);
        }
    }

    /** Something to decide on a screen of its own: the eye deal, Ryuk's game; and, the deal made, how the eyes look. */
    public enum Choice { EYE_DEAL, PACT, EYE_LOOK, SETUP }

    public record Offer(Choice choice) {
        void encode(FriendlyByteBuf buf) {
            buf.writeEnum(choice);
        }

        static Offer decode(FriendlyByteBuf buf) {
            return new Offer(buf.readEnum(Choice.class));
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> ru.maksim.aicompanion.client.ClientKira.offer(choice)));
            context.get().setPacketHandled(true);
        }
    }

    /** The player's answer; the server checks it was really offered (and still makes sense). */
    public record Answer(Choice choice, boolean yes) {
        void encode(FriendlyByteBuf buf) {
            buf.writeEnum(choice);
            buf.writeBoolean(yes);
        }

        static Answer decode(FriendlyByteBuf buf) {
            return new Answer(buf.readEnum(Choice.class), buf.readBoolean());
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().enqueueWork(() -> {
                if (player != null) {
                    switch (choice) {
                        case EYE_DEAL -> Eyes.answer(player, yes);
                        case PACT -> Pact.answer(player, yes);
                        default -> {
                        }
                    }
                }
            });
            context.get().setPacketHandled(true);
        }
    }

    /** Ryuk (entity id) said a line: the client puts it in the chat (once a story card or the show is done) and reads it out loud. */
    public record Speak(int entity, int id, String text, String emotion, Component line) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entity);
            buf.writeVarInt(id);
            buf.writeUtf(text, 1024);
            buf.writeUtf(emotion, 32);
            buf.writeComponent(line);
        }

        static Speak decode(FriendlyByteBuf buf) {
            return new Speak(buf.readVarInt(), buf.readVarInt(), buf.readUtf(1024), buf.readUtf(32), buf.readComponent());
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ru.maksim.aicompanion.client.RyukLines.add(this)));
            context.get().setPacketHandled(true);
        }
    }

    public record WriteName(InteractionHand hand, String name, String cause) {
        void encode(FriendlyByteBuf buf) {
            buf.writeEnum(hand);
            buf.writeUtf(name, 64);
            buf.writeUtf(cause, 128);
        }

        static WriteName decode(FriendlyByteBuf buf) {
            return new WriteName(buf.readEnum(InteractionHand.class), buf.readUtf(64), buf.readUtf(128));
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            context.get().enqueueWork(() -> {
                if (player != null) {
                    ItemStack note = player.getItemInHand(hand);
                    DeathNotes.write(player, note, name, cause, DeathNoteItem.concealment(note));
                }
            });
            context.get().setPacketHandled(true);
        }
    }
}
