package ru.maksim.aicompanion;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * The notebook's "Cases" page: what's on the news, whose names are waiting, where the story is right now (with the
 * coordinates) and how close the hunt has come. Built on the server from the story's flags, sent when the page opens.
 */
final class CaseFile {
    private CaseFile() {
    }

    static List<Component> lines(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("aicompanion.case.day", Investigation.day(player) + 1).withStyle(ChatFormatting.BOLD));
        out.add(Component.translatable("aicompanion.case.wanted").withStyle(ChatFormatting.UNDERLINE));
        List<News.Criminal> wanted = News.wanted(player.server);
        for (News.Criminal w : wanted) {
            out.add(Component.translatable("aicompanion.case.wanted_line", Names.shown(w.name()), Component.translatable("aicompanion.crime" + (w.crime() + 1))));
        }
        if (wanted.isEmpty()) {
            out.add(Component.translatable("aicompanion.case.wanted_none"));
        }
        List<String> pending = DeathNotes.pending(player.server, player.getUUID());
        if (!pending.isEmpty()) {
            out.add(Component.translatable("aicompanion.case.pending").withStyle(ChatFormatting.UNDERLINE));
            pending.forEach(p -> out.add(Component.literal("• " + p)));
        }
        out.add(Component.translatable("aicompanion.case.now").withStyle(ChatFormatting.UNDERLINE));
        out.addAll(now(player));
        if (c.kills > 0 && !c.flag("kira_wins")) {
            String who = c.flag("successor") ? "Near" : c.flag("l_dead") ? "-" : "L";
            out.add(Component.translatable("aicompanion.case.suspicion", who, Math.round(c.suspicion))
                    .withStyle(c.suspicion >= 80 ? ChatFormatting.DARK_RED : c.suspicion >= 40 ? ChatFormatting.RED : ChatFormatting.RESET));
        }
        out.addAll(SideQuests.lines(player)); // the side quests, last
        return out;
    }

    /** What's going on right now and where (with the coordinates): the page's middle, and what Ryuk knows when asked the way. */
    static List<Component> now(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        List<Component> out = new ArrayList<>();
        int before = out.size();
        if (c.kills == 0) {
            out.add(Component.translatable("aicompanion.case.first"));
        }
        if (c.flag("held_at") && !News.viewer(player)) {
            out.add(Component.translatable("aicompanion.case.tv"));
        }
        if (c.flag("kira_wins")) {
            out.add(Component.translatable("aicompanion.case.kira_wins"));
        }
        if (Investigation.specialOnAir(player)) { // Tailor alive, the bait not taken
            out.add(Component.translatable("aicompanion.case.broadcast"));
        }
        if (c.flag("hq") && !c.flag("l_dead")) {
            out.add(at(player, "aicompanion.case.hq", c.flags.get("hq")));
        }
        if (c.stage >= Investigation.AGENTS && c.stage < Investigation.SUCCESSORS) {
            out.add(Component.translatable("aicompanion.case.agents"));
        }
        if (c.flag("searched")) {
            out.add(Component.translatable("aicompanion.case.cameras"));
        }
        if (c.flag("hideout") && (c.flag("sidoh") && !c.flag("sidoh_done") || c.flag("mello") && !c.flag("mello_dead"))) {
            out.add(at(player, c.flag("mello") && !c.flag("mello_dead") ? "aicompanion.case.hideout_mello" : "aicompanion.case.hideout", c.flags.get("hideout")));
        }
        if (c.flag("hostage")) {
            out.add(Component.translatable("aicompanion.case.hostage"));
        }
        if (c.flag("spk") && !c.flag("kira_wins")) {
            out.add(at(player, "aicompanion.case.spk", c.flags.get("spk")));
        }
        if (c.flag("meeting") && c.flag("yellow_box") && !c.flag("kira_wins")) {
            out.add(at(player, "aicompanion.case.meeting", c.flags.get("yellow_box")));
        }
        if (c.flag("shrine")) {
            out.add(at(player, "aicompanion.case.shrine", c.flags.get("shrine")));
        }
        if (out.size() == before) {
            out.add(Component.translatable("aicompanion.case.quiet"));
        }
        return out;
    }

    /** Where to go now, the most pressing place first (card: the arrow's name; line: the page's line with the coordinates). */
    record Goal(String card, String line, BlockPos pos) {
    }

    @Nullable
    static Goal goal(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        if (c.flag("meeting") && c.flag("yellow_box") && !c.flag("kira_wins")) {
            return new Goal("aicompanion.card.box", "aicompanion.case.meeting", BlockPos.of(c.flags.get("yellow_box")));
        }
        boolean hideout = c.flag("hideout");
        if (hideout && c.flag("mello") && !c.flag("mello_dead")) { // the main story first, Sidoh's notebook last
            return new Goal("aicompanion.card.hideout_mello", "aicompanion.case.hideout_mello", BlockPos.of(c.flags.get("hideout")));
        }
        if (c.flag("hq") && !c.flag("l_dead")) {
            return new Goal("aicompanion.card.hq", "aicompanion.case.hq", BlockPos.of(c.flags.get("hq")));
        }
        if (c.flag("spk") && !c.flag("kira_wins")) {
            return new Goal("aicompanion.card.near", "aicompanion.case.spk", BlockPos.of(c.flags.get("spk")));
        }
        if (hideout && c.flag("sidoh") && !c.flag("sidoh_done")) {
            return new Goal("aicompanion.card.sidoh", "aicompanion.case.hideout", BlockPos.of(c.flags.get("hideout")));
        }
        if (c.flag("shrine")) {
            return new Goal("aicompanion.card.shrine", "aicompanion.case.shrine", BlockPos.of(c.flags.get("shrine")));
        }
        return SideQuests.goal(player); // nothing pressing in the main story: the side quest's place, if any
    }

    /** A place: its coordinates, and which way it is from here (not everyone keeps F3 open). */
    static MutableComponent at(ServerPlayer player, String key, long pos) {
        BlockPos p = BlockPos.of(pos);
        return Component.translatable(key, p.getX(), p.getZ()).append(Component.literal(" (" + Scouting.where(player.blockPosition(), p) + ")")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
