package ru.maksim.aicompanion;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** The side quests' things: a line of description; the ones worn on the head go there on a right click. */
public class QuestItem extends Item implements Equipable {
    private final String tooltip;
    private final boolean head;

    public QuestItem(Properties properties, String tooltip, boolean head) {
        super(properties);
        this.tooltip = tooltip;
        this.head = head;
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return head ? EquipmentSlot.HEAD : EquipmentSlot.MAINHAND;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return head ? swapWithEquipmentSlot(this, level, player, hand) : super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(tooltip).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
