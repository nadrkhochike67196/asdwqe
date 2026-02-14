package com.example.companion.entity.ai;

import com.example.companion.entity.CompanionEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.regex.Pattern;

/**
 * Processes player chat commands — teleport, stay, build, etc.
 * Only "ко мне / come here" triggers instant teleport.
 * Everything else goes through the AI brain.
 */
public class PlayerCommandSystem {

    public enum CommandType {
        TELEPORT,
        STAY,
        BUILD,
        CHAT // everything else → AI decides
    }

    // ── Regex patterns (case-insensitive + unicode) ─────────────────────────

    private static final Pattern TP_PATTERN = Pattern.compile(
            "ко мне|иди сюда|телепорт(?:нись)?|тп(?:\\s|$)|come here|tp(?:\\s|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern STAY_PATTERN = Pattern.compile(
            "стой|жди|останься|stay|wait|стоять",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern BUILD_PATTERN = Pattern.compile(
            "постро[йи]|build|строй|запили дом|сделай дом|сделай домик|сделай убежище|сделай башню",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Classify a chat message into a command type.
     */
    public static CommandType parseCommand(String message) {
        if (message == null) return CommandType.CHAT;
        if (TP_PATTERN.matcher(message).find())    return CommandType.TELEPORT;
        if (STAY_PATTERN.matcher(message).find())   return CommandType.STAY;
        if (BUILD_PATTERN.matcher(message).find())  return CommandType.BUILD;
        return CommandType.CHAT;
    }

    /**
     * Teleport companion to the player with particles + sound.
     */
    public static void teleportToPlayer(CompanionEntity companion, PlayerEntity player) {
        BlockPos target = findSafeSpot(companion.getWorld(), player.getBlockPos());

        companion.requestTeleport(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);

        // Effects
        if (companion.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(
                    ParticleTypes.PORTAL,
                    companion.getX(), companion.getY() + 1, companion.getZ(),
                    20, 0.5, 0.5, 0.5, 0.1);
        }
        companion.playSound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Find a safe landing spot near `center` (non-solid above, solid below).
     */
    private static BlockPos findSafeSpot(World world, BlockPos center) {
        // Try offsets around the center
        int[][] offsets = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {-1, 1}, {1, -1}, {-1, -1},
                {2, 0}, {-2, 0}, {0, 2}, {0, -2}
        };

        for (int[] off : offsets) {
            BlockPos candidate = center.add(off[0], 0, off[1]);
            if (isSafe(world, candidate)) return candidate;
            // Try one block up/down
            if (isSafe(world, candidate.up())) return candidate.up();
            if (isSafe(world, candidate.down())) return candidate.down();
        }
        // Fallback: directly on top of player
        return center;
    }

    private static boolean isSafe(World world, BlockPos pos) {
        return !world.getBlockState(pos).isOpaque()
                && !world.getBlockState(pos.up()).isOpaque()
                && world.getBlockState(pos.down()).isOpaque();
    }
}
