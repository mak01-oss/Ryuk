package ru.maksim.aicompanion;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.LivingEntity;

/**
 * What Ryuk sees when he flies off through the walls: the story's places, the nearest village, who is around. Never
 * other notebooks or their owners: a shinigami may not reveal those (the manga's rules).
 */
final class Scouting {
    private Scouting() {
    }

    static String report(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos at = player.blockPosition();
        Kira.Case c = Kira.of(player);
        List<String> seen = new ArrayList<>();
        BlockPos village = level.findNearestMapStructure(StructureTags.VILLAGE, at, 4, false);
        if (village != null) {
            seen.add(Config.tr("деревня ", "a village ") + where(at, village));
        }
        if (c.flag("hq")) {
            seen.add(Config.tr("штаб L ", "L's headquarters ") + where(at, BlockPos.of(c.flags.get("hq"))));
        }
        if (c.flag("hideout")) {
            seen.add(Config.tr("логово мафии ", "the mafia's hideout ") + where(at, BlockPos.of(c.flags.get("hideout"))));
        }
        int agents = level.getEntitiesOfClass(HumanEntity.class, player.getBoundingBox().inflate(96),
                h -> h.role() == HumanEntity.Role.AGENT && player.getUUID().equals(h.about())).size();
        if (agents > 0) {
            seen.add(Config.tr("за твоим человеком тайком следят агенты: " + agents, "agents secretly tailing your human: " + agents));
        }
        int humans = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(64), e -> e != player && Lifespans.human(e)).size();
        seen.add(Config.tr("людей в округе: " + humans, "humans around: " + humans));
        return String.join("; ", seen);
    }

    /** "about 300 blocks to the north-east". */
    static String where(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        int dist = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz) / 10) * 10;
        String[] en = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        String[] ru = {"на юг", "на юго-запад", "на запад", "на северо-запад", "на север", "на северо-восток", "на восток", "на юго-восток"};
        int sector = Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(-dx, dz)) / 45), 8); // Minecraft yaw: 0 = south
        if (dist == 0) {
            return Config.tr("совсем рядом, " + ru[sector], "close by, to the " + en[sector]);
        }
        return Config.tr("примерно в " + dist + " блоках " + ru[sector], "about " + dist + " blocks to the " + en[sector]);
    }
}
