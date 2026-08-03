package com.minecart.handytools.toolaction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** A target captured when a tool action starts and held stable for its cycle. */
public record ToolActionTarget(
        BlockPos blockPos,
        Vec3 blockCenter,
        Vec3 contactPoint
) {
    public ToolActionTarget {
        blockPos = blockPos.immutable();
    }
}
