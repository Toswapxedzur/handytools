package com.minecart.handytools;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class HammerItem extends Item {
    public static final int PRESS_FALL_TICKS = 12;
    private static final int MAX_USE_DURATION = 72_000;

    public HammerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isUsingItem()) {
            return InteractionResult.FAIL;
        }

        player.startUsingItem(context.getHand());
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(
            Level level,
            Player player,
            InteractionHand hand
    ) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hitResult =
                getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);

        if (hitResult.getType() != HitResult.Type.BLOCK
                || level.getBlockState(hitResult.getBlockPos()).isAir()
                || player.isUsingItem()) {
            return InteractionResultHolder.fail(stack);
        }

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return MAX_USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    public static boolean isPressing(LivingEntity entity) {
        return entity.isUsingItem()
                && entity.getUseItem().getItem() instanceof HammerItem;
    }

    public static float getPressProgress(LivingEntity entity, float partialTick) {
        if (!isPressing(entity)) {
            return 0.0F;
        }

        float linear = Math.min(
                (entity.getTicksUsingItem() + partialTick) / PRESS_FALL_TICKS,
                1.0F
        );
        return linear * linear * (3.0F - 2.0F * linear);
    }
}
