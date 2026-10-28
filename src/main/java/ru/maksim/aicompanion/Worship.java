package ru.maksim.aicompanion;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatterns;

/**
 * Kira's followers. Each criminal the notebook punishes (raiders, witches, the mob) adds to Kira's glory, each innocent
 * takes more away. With glory the villages' talk turns to praise; a shrine with candles and a black K banner goes up
 * in the nearest village and the faithful leave apples in its barrel every dawn (Ryuk's favourite kind of worship);
 * village witnesses cover for Kira; and Sakura TV airs "Kira's Kingdom". In fear the offerings stop. Flag "glory".
 */
public final class Worship {
    private Worship() {
    }

    static long glory(Kira.Case c) {
        return c.flags.getOrDefault("glory", 0L);
    }

    /** A death by the notebook, as the villages see it. */
    static void onNoteDeath(ServerPlayer writer, LivingEntity victim) {
        add(Kira.of(writer), criminal(victim) ? 4 : victim instanceof AbstractVillager ? -6 : 0);
    }

    static void add(Kira.Case c, int by) {
        if (by != 0) {
            c.flags.put("glory", Math.max(-100, Math.min(100, glory(c) + by)));
        }
    }

    static boolean criminal(LivingEntity e) {
        return e instanceof AbstractIllager || e instanceof Witch || e instanceof HumanEntity h
                && (h.role() == HumanEntity.Role.MAFIA || h.role() == HumanEntity.Role.BOSS || h.role() == HumanEntity.Role.MELLO);
    }

    /** A villager who saw the notebook in use but believes in Kira says less about it. */
    static boolean covers(ServerPlayer writer, LivingEntity witness) {
        return witness instanceof AbstractVillager && glory(Kira.of(writer)) >= 40;
    }

    /** What the villages whisper: praise, fear, or the old talk. */
    static String rumour(ServerPlayer player) {
        long g = glory(Kira.of(player));
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return g >= 20 ? "aicompanion.worship.rumour" + (1 + r.nextInt(4)) : g <= -20 ? "aicompanion.fear.rumour" + (1 + r.nextInt(3))
                : "aicompanion.rumour" + (1 + r.nextInt(6));
    }

    /** Once a second: the shrine, the dawn offerings, the show. */
    static void tick(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        if (c.kills == 0 || player.level().dimension() != Level.OVERWORLD) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long g = glory(c), now = level.getServer().overworld().getGameTime(), day = Kira.clock(level.getServer()) / 24000, time = level.getDayTime() % 24000;
        if (g >= 40 && !c.flag("shrine")) {
            BlockPos at = site(level, player);
            c.flags.put("shrine", at.asLong());
            Kira.get(player.server).setDirty();
            build(level, at);
            player.sendSystemMessage(Component.translatable("aicompanion.worship.shrine", at.getX(), at.getZ()).withStyle(ChatFormatting.GOLD));
            Network.card(player, Network.Broadcast.CHAPTER, Component.translatable("aicompanion.card.shrine"), Component.translatable("aicompanion.card.shrine_text"), "", at);
            story(player, Config.tr("люди начали поклоняться Кире: в деревне (" + at.getX() + ", " + at.getZ() + ") поставили алтарь со свечами и носят туда яблоки. Яблоки! Ты в восторге",
                    "people have started to worship Kira: in the village (" + at.getX() + ", " + at.getZ() + ") they built a shrine with candles and bring apples to it. Apples! You're delighted"));
        }
        if (c.flag("shrine") && g >= 40 && time < 2000 && c.flags.getOrDefault("offered", -1L) != day) {
            c.flags.put("offered", day);
            Kira.get(player.server).setDirty();
            offer(level, BlockPos.of(c.flags.get("shrine")), g);
        }
        if (g >= 60 && time > 6000 && time < 12000 && now > c.flags.getOrDefault("kingdom", 0L) + 2 * Kira.DAY) {
            c.flags.put("kingdom", now);
            Kira.get(player.server).setDirty();
            Investigation.tv(player, "aicompanion.tv.kingdom" + (1 + ThreadLocalRandom.current().nextInt(4)));
        }
    }

    /** The faithful leave apples in the barrel, now and then a golden one. */
    private static void offer(ServerLevel level, BlockPos shrine, long glory) {
        if (!(level.getBlockEntity(shrine.offset(1, 1, 0)) instanceof Container barrel)) {
            return; // broken or not built yet
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int apples = 1 + r.nextInt(glory >= 80 ? 4 : 2);
        for (int i = 0; i < barrel.getContainerSize() && apples > 0; i++) {
            ItemStack s = barrel.getItem(i);
            if (s.isEmpty()) {
                barrel.setItem(i, new ItemStack(glory >= 80 && r.nextInt(5) == 0 ? Items.GOLDEN_APPLE : Items.APPLE, apples));
                apples = 0;
            } else if (s.is(Items.APPLE) && s.getCount() < s.getMaxStackSize()) {
                int add = Math.min(apples, s.getMaxStackSize() - s.getCount());
                s.grow(add);
                apples -= add;
                barrel.setChanged();
            }
        }
    }

    /** Next to the nearest village's centre, on the flattest patch of a few tries (else near the player). */
    private static BlockPos site(ServerLevel level, ServerPlayer player) {
        BlockPos village = level.findNearestMapStructure(StructureTags.VILLAGE, player.blockPosition(), 2, false);
        BlockPos centre = village != null && village.distSqr(player.blockPosition()) < 400 * 400 ? village : player.blockPosition().offset(20, 0, 20);
        return Builds.flatNear(level, centre, 9, 2);
    }

    /** A stone platform, an altar under a black banner with a white K, four candles, flowers in pots, the offering barrel. */
    static void build(ServerLevel level, BlockPos g) {
        Builds.box(level, g.offset(-2, 1, -2), g.offset(2, 4, 2), Blocks.AIR.defaultBlockState());
        Builds.box(level, g.offset(-2, 0, -2), g.offset(2, 0, 2), Blocks.STONE_BRICKS.defaultBlockState());
        Builds.later(level, g.offset(0, 1, 0), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        CompoundTag banner = new CompoundTag();
        banner.put("Patterns", new BannerPattern.Builder() // the right half of an X and a left stripe make the K
                .addPattern(BannerPatterns.STRIPE_DOWNLEFT, DyeColor.WHITE).addPattern(BannerPatterns.STRIPE_DOWNRIGHT, DyeColor.WHITE)
                .addPattern(BannerPatterns.HALF_VERTICAL, DyeColor.BLACK).addPattern(BannerPatterns.STRIPE_LEFT, DyeColor.WHITE).toListTag());
        Builds.later(level, g.offset(0, 2, 0), Blocks.BLACK_BANNER.defaultBlockState(), banner);
        for (int[] d : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            Builds.later(level, g.offset(d[0], 1, d[1]), Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true));
        }
        for (int[] d : new int[][]{{-1, -2}, {1, 2}, {-2, 1}, {2, -1}}) {
            Builds.later(level, g.offset(d[0], 1, d[1]), Blocks.POTTED_POPPY.defaultBlockState());
        }
        Builds.later(level, g.offset(1, 1, 0), Blocks.BARREL.defaultBlockState(),
                Builds.bookChest(Config.tr("Письма Кире", "Letters to Kira"), "?", "aicompanion.worship.letter1", "aicompanion.worship.letter2"));
    }

    /** For Ryuk's context. */
    static String describe(ServerPlayer player) {
        Kira.Case c = Kira.of(player);
        long g = glory(c);
        if (c.kills == 0 || Math.abs(g) < 20) {
            return "";
        }
        if (g < 0) {
            return Config.tr("Люди боятся Киры: он убивает невинных. ", "People fear Kira: he kills the innocent. ");
        }
        String shrine = "";
        if (c.flag("shrine")) {
            BlockPos at = BlockPos.of(c.flags.get("shrine"));
            shrine = Config.tr(" У них алтарь в " + at.getX() + ", " + at.getZ() + ", туда каждый рассвет носят яблоки.",
                    " They have a shrine at " + at.getX() + ", " + at.getZ() + " and bring apples to it every dawn.");
        }
        return Config.tr("Люди славят Киру как бога справедливости." + shrine + " ", "People praise Kira as a god of justice." + shrine + " ");
    }

    private static void story(ServerPlayer player, String event) {
        CompanionEntity ryuk = CompanionEntity.of(player);
        if (ryuk != null) {
            ryuk.remark(event, "story", 0);
        }
    }
}
