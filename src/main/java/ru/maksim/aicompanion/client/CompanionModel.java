package ru.maksim.aicompanion.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import ru.maksim.aicompanion.AiCompanion;
import ru.maksim.aicompanion.CompanionEntity;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * Ryuk's face is the texture: textures/entity/ryuk_[expression][_talk|_blink].png from make_ryuk.py.
 * He never blinks (no eyelids in the anime); shut eyes only while savouring an apple or twisting in withdrawal.
 */
public class CompanionModel extends DefaultedEntityGeoModel<CompanionEntity> {
    private final Map<String, ResourceLocation> textures = new HashMap<>();

    public CompanionModel() {
        super(new ResourceLocation(AiCompanion.MODID, "ryuk"), "head"); // "head" turns to where he looks; animations move "neck"
    }

    @Override
    public ResourceLocation getTextureResource(CompanionEntity entity) {
        String name = "ryuk_" + expression(entity) + variant(entity);
        return textures.computeIfAbsent(name, n -> new ResourceLocation(AiCompanion.MODID, "textures/entity/" + n + ".png"));
    }

    private static String expression(CompanionEntity entity) {
        return switch (entity.getAction()) {
            case "eat", "wave", "bow", "backflip", "clap", "juggle", "rub_hands", "cackle", "dice_shake", "dice_slam", "point" -> "happy";
            case "withdrawal" -> "sad";
            case "sulk" -> "angry";
            case "peek", "shrug" -> "thinking";
            case "doze" -> "sleepy";
            case "stretch" -> entity.actionAge() >= 10 && entity.actionAge() < 40 ? "sleepy" : "neutral"; // eyes shut for the yawn
            default -> switch (entity.getEmotion()) {
                case "happy", "angry", "sad", "surprised", "thinking" -> entity.getEmotion();
                case "scared" -> "surprised";
                default -> "neutral";
            };
        };
    }

    private static String variant(CompanionEntity entity) {
        int t = entity.tickCount;
        switch (entity.getAction()) {
            case "stretch" -> {
                return entity.actionAge() >= 12 && entity.actionAge() < 38 ? "_talk" : ""; // the yawn
            }
            case "cackle" -> {
                return t / 3 % 2 == 0 ? "_talk" : ""; // ha! ha! ha!
            }
            case "doze" -> {
                return "";
            }
            default -> {
            }
        }
        if (entity.isEating()) {
            int age = entity.actionAge();
            if (age >= 38) {
                return "_blink"; // the last bite: eyes shut, savouring
            }
            return age >= 17 && age / 3 % 2 == 0 ? "_talk" : ""; // chewing between bites
        }
        if (entity.getAction().equals("withdrawal")) {
            return switch (t / 6 % 3) {
                case 0 -> "_blink";
                case 1 -> "_talk";
                default -> "";
            };
        }
        Boolean voiced = RyukVoice.mouthOpen(entity.getId()); // his voice is playing: the mouth follows its loudness
        if (voiced != null) {
            return voiced ? "_talk" : "";
        }
        if (entity.isTalking() && RyukVoice.silent()) {
            int h = (t / 2) * 0x9E3779B1; // no voice: irregular mouth flaps, about 60% open
            return ((h ^ h >>> 15) & 7) < 5 ? "_talk" : "";
        }
        return "";
    }

    @Override
    public void setCustomAnimations(CompanionEntity entity, long instanceId, AnimationState<CompanionEntity> state) {
        super.setCustomAnimations(entity, instanceId, state);
        CoreGeoBone apple = getAnimationProcessor().getBone("apple");
        if (apple != null) {
            apple.setHidden(!entity.isEating());
        }
        CoreGeoBone note = getAnimationProcessor().getBone("notebook"); // his own notebook: out only to write a name
        if (note != null) {
            note.setHidden(!entity.getAction().equals("write"));
        }
        CoreGeoBone cup = getAnimationProcessor().getBone("cup"); // the skull cup: out only for the dice
        if (cup != null) { // put down on the table at the slam (the dice screen draws it there from then on)
            String a = entity.getAction();
            cup.setHidden(!a.startsWith("dice_") || a.equals("dice_slam") && entity.actionAge() >= 7);
        }
        for (int i = 1; i <= 3; i++) { // three apples in the air, only while he juggles
            CoreGeoBone ball = getAnimationProcessor().getBone("juggle" + i);
            if (ball != null) {
                ball.setHidden(!entity.getAction().equals("juggle"));
            }
        }
    }
}
