package com.minecart.handytools;

import com.minecart.handytools.toolaction.PhasedToolAction;
import com.minecart.handytools.toolaction.ToolActionDurations;
import com.minecart.handytools.toolaction.ToolActionManager;
import com.minecart.handytools.toolaction.ToolActionTarget;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class HammerItem extends Item implements PhasedToolAction {
    public static final double MAX_TARGET_DISTANCE = 1.5D;
    public static final double MIN_TARGET_TOP_ABOVE_FEET = 0.5D;
    public static final double MAX_TARGET_TOP_ABOVE_FEET = 1.5D;

    private static final int MAX_USE_DURATION = 72_000;
    private static final double MAX_TARGET_DISTANCE_SQUARED =
            MAX_TARGET_DISTANCE * MAX_TARGET_DISTANCE;
    private static final double MIN_VIEW_DOT = 0.5D;
    private static final ToolActionDurations ACTION_DURATIONS =
            new ToolActionDurations(10, 14, 8, 10);

    public HammerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isUsingItem()
                || ToolActionManager.isActive(player)) {
            return InteractionResult.FAIL;
        }

        Optional<ToolActionTarget> target = findActionTarget(
                context.getLevel(),
                player,
                context.getClickedPos()
        );
        if (target.isEmpty()
                || !startAction(player, context.getHand(), target.get())) {
            return InteractionResult.PASS;
        }

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
                || player.isUsingItem()
                || ToolActionManager.isActive(player)) {
            return InteractionResultHolder.fail(stack);
        }

        Optional<ToolActionTarget> target = findActionTarget(
                level,
                player,
                hitResult.getBlockPos()
        );
        if (target.isEmpty() || !startAction(player, hand, target.get())) {
            return InteractionResultHolder.fail(stack);
        }

        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(
            ItemStack stack,
            Level level,
            LivingEntity entity,
            int timeLeft
    ) {
        ToolActionManager.requestRelease(entity);
    }

    @Override
    public ItemStack finishUsingItem(
            ItemStack stack,
            Level level,
            LivingEntity entity
    ) {
        ToolActionManager.requestRelease(entity);
        return stack;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return MAX_USE_DURATION;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public ToolActionDurations actionDurations(ItemStack stack) {
        return ACTION_DURATIONS;
    }

    @Override
    public Optional<ToolActionTarget> findActionTarget(
            Level level,
            Player player,
            BlockPos blockPos
    ) {
        BlockState state = level.getBlockState(blockPos);
        if (state.isAir()) {
            return Optional.empty();
        }

        VoxelShape shape = state.getShape(
                level,
                blockPos,
                CollisionContext.of(player)
        );
        if (shape.isEmpty()) {
            return Optional.empty();
        }

        Vec3 blockCenter = Vec3.atCenterOf(blockPos);
        if (player.position().distanceToSqr(blockCenter)
                > MAX_TARGET_DISTANCE_SQUARED) {
            return Optional.empty();
        }

        Vec3 eyeToCenter = blockCenter.subtract(player.getEyePosition());
        if (eyeToCenter.lengthSqr() < 1.0E-8D
                || player.getLookAngle().dot(eyeToCenter.normalize())
                < MIN_VIEW_DOT) {
            return Optional.empty();
        }

        AABB bounds = shape.bounds().move(blockPos);
        double topAboveFeet = bounds.maxY - player.getY();
        if (topAboveFeet < MIN_TARGET_TOP_ABOVE_FEET
                || topAboveFeet > MAX_TARGET_TOP_ABOVE_FEET) {
            return Optional.empty();
        }

        Vec3 contactPoint = new Vec3(
                Mth.clamp(player.getX(), bounds.minX, bounds.maxX),
                bounds.maxY,
                Mth.clamp(player.getZ(), bounds.minZ, bounds.maxZ)
        );
        return Optional.of(
                new ToolActionTarget(blockPos, blockCenter, contactPoint)
        );
    }

    private boolean startAction(
            Player player,
            InteractionHand hand,
            ToolActionTarget target
    ) {
        if (!ToolActionManager.start(player, hand, this, target)) {
            return false;
        }
        player.startUsingItem(hand);
        return true;
    }
}
