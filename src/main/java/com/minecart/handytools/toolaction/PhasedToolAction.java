package com.minecart.handytools.toolaction;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Common contract for tools with preparation, repeated operation, and release.
 *
 * <p>The action manager owns phase timing on both logical sides and invokes all
 * physically meaningful callbacks only on the server. A renderer can consume
 * the same phase state without becoming the source of gameplay truth.</p>
 */
public interface PhasedToolAction {
    ToolActionDurations actionDurations(ItemStack stack);

    Optional<ToolActionTarget> findActionTarget(
            Level level,
            Player player,
            BlockPos blockPos
    );

    default void onServerPhaseStarted(
            ToolActionContext context,
            ToolActionPhase phase
    ) {
    }

    /** Called exactly when an operation descent reaches the contact pose. */
    default void onServerImpact(ToolActionContext context) {
    }

    /**
     * Terminal teardown hook, called once when the action ends for any reason:
     * a normal release, an explicit cancel, an item swap, a dimension change,
     * or the acting player's death. Use it to release any per-action state
     * allocated in {@link #onServerPhaseStarted}.
     */
    default void onServerActionFinished(ToolActionContext context) {
    }
}
