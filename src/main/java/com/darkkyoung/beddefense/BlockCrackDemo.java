package com.darkkyoung.beddefense;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/**
 * Shows vanilla progressive block-destruction cracks.
 *
 * Prototype only: its progress source is a debug task, not a real monster.
 * No additional texture assets are required. (-1 clears the overlay.)
 */
final class BlockCrackDemo {
    private static final int DURATION_TICKS = 80;
    private static final List<BreakTask> TASKS = new ArrayList<>();
    private static int nextBreakerId = -62000;

    private BlockCrackDemo() {
    }

    static boolean begin(ServerLevel level, BlockPos target) {
        for (BreakTask task : TASKS) {
            if (task.level == level && task.target.equals(target)) {
                return false;
            }
        }
        TASKS.add(new BreakTask(level, target.immutable(), nextBreakerId--));
        return true;
    }

    static void tick(MinecraftServer server) {
        Iterator<BreakTask> iterator = TASKS.iterator();
        while (iterator.hasNext()) {
            BreakTask task = iterator.next();
            if (!task.level.getBlockState(task.target).is(Blocks.COBBLESTONE)) {
                task.level.destroyBlockProgress(task.breakerId, task.target, -1);
                iterator.remove();
                continue;
            }

            task.elapsed++;
            int stage = Math.min(9, (task.elapsed * 10) / DURATION_TICKS);
            if (stage != task.lastStage) {
                task.level.destroyBlockProgress(task.breakerId, task.target, stage);
                task.lastStage = stage;
            }

            if (task.elapsed >= DURATION_TICKS) {
                task.level.destroyBlockProgress(task.breakerId, task.target, -1);
                task.level.destroyBlock(task.target, true);
                iterator.remove();
            }
        }
    }

    private static final class BreakTask {
        final ServerLevel level;
        final BlockPos target;
        final int breakerId;
        int elapsed;
        int lastStage = -1;

        BreakTask(ServerLevel level, BlockPos target, int breakerId) {
            this.level = level;
            this.target = target;
            this.breakerId = breakerId;
        }
    }
}
