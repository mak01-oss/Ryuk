package ru.maksim.aicompanion;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * L's hidden cameras: sixty-four of them in Light's room. Here the Task Force mounts a few small ones on the walls by
 * the bed. A camera that sees the notebook in use catches Kira red-handed (a page in a bag of chips it can't see);
 * breaking one makes L wonder why it went dark. Ryuk can find them for an apple, as he did for Light.
 */
public final class Cameras {
    private static final double RANGE = 12;

    private Cameras() {
    }

    static List<BlockPos> near(ServerLevel level, BlockPos center, int radius) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -radius / 2, -radius), center.offset(radius, radius / 2, radius))) {
            if (level.getBlockState(p).is(AiCompanion.CAMERA.get())) {
                out.add(p.immutable());
            }
        }
        return out;
    }

    /** In range and nothing solid between the lens and the player's hands. */
    static boolean sees(ServerLevel level, BlockPos camera, ServerPlayer player) {
        Direction facing = level.getBlockState(camera).getValue(CameraBlock.FACING);
        Vec3 lens = Vec3.atCenterOf(camera).add(facing.getStepX() * 0.45, 0, facing.getStepZ() * 0.45);
        Vec3 hands = player.position().add(0, 1.1, 0);
        Vec3 to = hands.subtract(lens);
        if (to.length() > RANGE || to.normalize().dot(Vec3.atLowerCornerOf(facing.getNormal())) < -0.2) {
            return false; // too far, or behind the camera
        }
        return !Investigation.blocked(level, lens, hands, player);
    }

    /** {@code count} cameras on the walls around {@code bed}: head height, facing into the room. */
    static void install(ServerLevel level, BlockPos bed, int count) {
        List<BlockPos[]> spots = new ArrayList<>(); // [where, wall]
        for (BlockPos p : BlockPos.betweenClosed(bed.offset(-6, 1, -6), bed.offset(6, 2, 6))) {
            if (!level.isEmptyBlock(p)) {
                continue;
            }
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (level.getBlockState(p.relative(d)).isSolidRender(level, p.relative(d))) {
                    spots.add(new BlockPos[]{p.immutable(), p.relative(d).immutable()});
                    break;
                }
            }
        }
        Collections.shuffle(spots);
        int placed = 0;
        for (BlockPos[] s : spots) {
            if (placed >= count) {
                break;
            }
            if (near(level, s[0], 3).isEmpty()) { // spread out
                Direction away = Direction.getNearest(s[0].getX() - s[1].getX(), 0, s[0].getZ() - s[1].getZ());
                level.setBlockAndUpdate(s[0], AiCompanion.CAMERA.get().defaultBlockState().setValue(CameraBlock.FACING, away));
                placed++;
            }
        }
        AiCompanion.LOGGER.info("The Task Force put up {} cameras around {}", placed, bed);
    }

    /** Ryuk's sweep: every camera within {@code radius} glows through the walls for a while; returns how many. */
    static int show(ServerLevel level, BlockPos center, int radius, int ticks) {
        List<BlockPos> found = near(level, center, radius);
        for (BlockPos p : found) {
            marker(level, Vec3.atLowerCornerOf(p).add(0.3, 0.3, 0.3), ticks);
        }
        return found.size();
    }

    /** A small glowing block that shows through walls (a block display, like /summon makes one). */
    static void marker(ServerLevel level, Vec3 at, int ticks) {
        try {
            CompoundTag tag = TagParser.parseTag("{id:\"minecraft:block_display\",block_state:{Name:\"minecraft:redstone_block\"},"
                    + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.4f,0.4f,0.4f]},"
                    + "Glowing:1b,Tags:[\"aicompanion_marker\"]}");
            Entity marker = EntityType.loadEntityRecursive(tag, level, e -> {
                e.moveTo(at.x, at.y, at.z);
                return e;
            });
            if (marker != null) {
                marker.getPersistentData().putLong("aicompanion_until", level.getGameTime() + ticks);
                level.addFreshEntity(marker);
            }
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Once a second: markers whose time is up go. */
    static void tick(ServerLevel level) {
        long now = level.getGameTime();
        for (Display.BlockDisplay d : level.getEntities(EntityType.BLOCK_DISPLAY, e -> e.getTags().contains("aicompanion_marker"))) {
            if (now > d.getPersistentData().getLong("aicompanion_until")) {
                d.discard();
            }
        }
    }
}
