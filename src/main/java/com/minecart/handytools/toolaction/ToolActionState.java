package com.minecart.handytools.toolaction;

import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;

/**
 * Read-only action state for gameplay and future animation consumers.
 * Renderers should use {@link #smoothProgress(float)} with partial ticks.
 */
public record ToolActionState(
        ToolActionPhase phase,
        int elapsedTicks,
        int durationTicks,
        int completedCycles,
        boolean releaseRequested,
        InteractionHand hand,
        ToolActionTarget target
) {
    public float linearProgress(float partialTick) {
        return Mth.clamp(
                (elapsedTicks + Mth.clamp(partialTick, 0.0F, 1.0F))
                        / durationTicks,
                0.0F,
                1.0F
        );
    }

    public float smoothProgress(float partialTick) {
        float progress = linearProgress(partialTick);
        return progress * progress * (3.0F - 2.0F * progress);
    }
}
