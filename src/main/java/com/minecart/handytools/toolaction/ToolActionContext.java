package com.minecart.handytools.toolaction;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Context supplied to server-side phase and impact hooks. */
public record ToolActionContext(
        Player player,
        InteractionHand hand,
        ItemStack stack,
        ToolActionTarget target,
        int completedCycles
) {
    public Level level() {
        return player.level();
    }
}
