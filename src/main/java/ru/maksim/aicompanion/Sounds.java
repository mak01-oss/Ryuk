package ru.maksim.aicompanion;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The mod's own sounds, all synthesized for it (mod/tools/sound renders them; assets/aicompanion/sounds.json lists
 * the files). Effects are played in the world; stings and themes are cues the client plays by name
 * (client/Soundtrack, Network.Cue): "sting.chapter", "music.realm" and so on.
 */
public final class Sounds {
    static final DeferredRegister<SoundEvent> REG = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, AiCompanion.MODID);
    public static final RegistryObject<SoundEvent> NOTE_OPEN = reg("note.open"), NOTE_PAGE = reg("note.page"),
            NOTE_FALL = reg("note.fall"), HEART = reg("heart.beat"), RYUK_POOF = reg("ryuk.poof"),
            SIDOH_WING = reg("sidoh.wing"), CHOCOLATE = reg("sidoh.chocolate"), TV_ON = reg("tv.on"),
            TV_OFF = reg("tv.off"), DETECTOR = reg("hq.detector"), PAGE_GUST = reg("page.gust"), PAGE_CATCH = reg("page.catch"),
            DICE_SHAKE = reg("dice.shake"), DICE_SLAM = reg("dice.slam"), DICE_ROLL = reg("dice.roll"), DICE_BONES = reg("dice.bones"),
            REALM_VISION = reg("realm.vision"), REALM_BRANCH = reg("realm.branch"), REALM_KING = reg("realm.king"),
            REALM_OFFERING = reg("realm.offering");
    /** The cues, by name, and the realm's air (the biome names those). */
    public static final String[] CUES = {"ambient.realm", "ambient.realm_add", "ambient.realm_mood",
            "sting.chapter", "sting.quest", "sting.quest_done", "sting.news", "sting.breaking", "sting.special", "sting.l", "sting.verdict",
            "sting.eyes", "sting.pact", "sting.pact_sealed", "sting.suspicion", "sting.arrest", "sting.kira", "sting.realm_in",
            "sting.realm_out", "sting.tribute", "sting.sight", "sting.dice_win", "sting.dice_lose", "sting.l_dead", "sting.stare", "sting.god", "sting.person", "music.ryuk",
            "music.realm", "music.special", "music.l", "music.mafia", "music.near", "music.sidoh", "music.kira", "music.requiem", "music.mu"};

    static {
        for (String id : CUES) {
            reg(id);
        }
    }

    private Sounds() {
    }

    private static RegistryObject<SoundEvent> reg(String id) {
        return REG.register(id, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(AiCompanion.MODID, id)));
    }

    /** A sting or a theme by its name, or null if there's no such. */
    @Nullable
    public static SoundEvent cue(String id) {
        return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(AiCompanion.MODID, id));
    }

    /** An effect where something happens, for everyone near. */
    public static void at(Entity where, RegistryObject<SoundEvent> sound, SoundSource source, float volume, float pitch) {
        where.level().playSound(null, where.getX(), where.getY(), where.getZ(), sound.get(), source, volume, pitch);
    }

    public static void at(Level level, net.minecraft.core.BlockPos pos, RegistryObject<SoundEvent> sound, SoundSource source, float volume, float pitch) {
        level.playSound(null, pos, sound.get(), source, volume, pitch);
    }
}
