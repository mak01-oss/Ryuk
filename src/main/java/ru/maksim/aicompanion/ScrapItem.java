package ru.maksim.aicompanion;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/**
 * A piece of the notebook does everything the notebook does, and it hides better (the manga's best tricks):
 * PAGE - a torn-out page (witnesses notice it only up close);
 * WATCH - Light's watch: pull the crown four times fast, the scrap and a needle drop out, the name goes in blood;
 * CHIPS - a bag of chips with a mini TV and a scrap inside: the cameras film a boy eating chips.
 */
public class ScrapItem extends Item {
    public enum Kind {
        PAGE(1), WATCH(2), CHIPS(3);

        public final int concealment;

        Kind(int concealment) {
            this.concealment = concealment;
        }
    }

    public final Kind kind;

    public ScrapItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (kind == Kind.CHIPS) {
            player.playSound(SoundEvents.GENERIC_EAT, 0.6F, 1.3F); // just a snack, officer
        }
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ru.maksim.aicompanion.client.ScrapScreen.use(hand, kind));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.aicompanion.scrap." + kind.name().toLowerCase(java.util.Locale.ROOT) + ".tooltip")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        int written = DeathNoteItem.lines(stack).size();
        if (written > 0) {
            tooltip.add(Component.translatable("aicompanion.note.written", written).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
