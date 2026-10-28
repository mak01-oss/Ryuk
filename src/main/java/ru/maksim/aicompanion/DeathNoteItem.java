package ru.maksim.aicompanion;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** Ryuk's spare notebook. Right click opens it; whoever carries it gets the shinigami at their shoulder. */
public class DeathNoteItem extends Item {
    private static final int MAX_LINES = 40;

    public DeathNoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ru.maksim.aicompanion.client.ClientKira.openNote(hand));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    /** What a name can be written in and still kill: the notebook, or any piece of it. */
    /** Gevanni's copy (Successors.swap): the same cover, the same lines, and nothing written in it kills. */
    public static boolean fake(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean("Fake");
    }

    public static boolean writable(ItemStack stack) {
        return stack.is(AiCompanion.DEATH_NOTE.get()) || stack.getItem() instanceof ScrapItem;
    }

    /** How well the writing hides from witnesses and cameras (Investigation.witnessed). */
    public static int concealment(ItemStack stack) {
        return stack.getItem() instanceof ScrapItem scrap ? scrap.kind.concealment : 0;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!level.isClientSide && entity.tickCount % 20 == 0 && entity instanceof ServerPlayer player) {
            Onboarding.mark(stack, player.getUUID());
            CompanionEntity.noticeNote(player);
        }
    }

    /** Dropped, it lies where it fell until someone picks it up: a notebook doesn't rot away in five minutes. */
    @Override
    public int getEntityLifespan(ItemStack stack, Level level) {
        return Integer.MAX_VALUE;
    }

    /** Its name in dark red, whatever the rarity colour says. */
    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId(stack)).withStyle(ChatFormatting.DARK_RED);
    }

    /** Fell out of the world: gone for good (Onboarding sends another). Lying about, it smoulders a little. */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (entity.level().isClientSide && entity.tickCount % 7 == 0) {
            net.minecraft.util.RandomSource r = entity.level().random;
            entity.level().addParticle(r.nextInt(5) == 0 ? net.minecraft.core.particles.ParticleTypes.SOUL : net.minecraft.core.particles.ParticleTypes.SMOKE,
                    entity.getX() + (r.nextDouble() - 0.5) * 0.4, entity.getY() + 0.15, entity.getZ() + (r.nextDouble() - 0.5) * 0.4, 0, 0.015, 0);
        }
        if (!entity.level().isClientSide && entity.getY() < entity.level().getMinBuildHeight() - 16) {
            Onboarding.destroyed(entity.getServer(), stack);
            entity.discard();
            return true;
        }
        return false;
    }

    /** Blown up (or caught on a cactus): gone for good. Fire and lava can't touch it. */
    @Override
    public void onDestroyed(ItemEntity entity) {
        if (!entity.level().isClientSide) {
            Onboarding.destroyed(entity.getServer(), entity.getItem());
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.aicompanion.death_note.howto").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("  ").append(Component.translatable("item.aicompanion.death_note.tooltip"))
                .withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC));
        int written = lines(stack).size();
        if (written > 0) {
            tooltip.add(Component.translatable("aicompanion.note.written", written).withStyle(ChatFormatting.RED));
        }
        tooltip.add(Component.translatable("item.aicompanion.death_note.open").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** What has been written in this copy, oldest first. */
    public static List<String> lines(ItemStack stack) {
        List<String> out = new ArrayList<>();
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            for (Tag t : tag.getList("Lines", Tag.TAG_STRING)) {
                out.add(t.getAsString());
            }
        }
        return out;
    }

    static void addLine(ItemStack stack, String line) {
        CompoundTag tag = stack.getOrCreateTag();
        ListTag lines = tag.getList("Lines", Tag.TAG_STRING);
        lines.add(StringTag.valueOf(line));
        while (lines.size() > MAX_LINES) {
            lines.remove(0);
        }
        tag.put("Lines", lines);
    }
}
