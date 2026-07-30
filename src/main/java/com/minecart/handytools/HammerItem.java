package com.minecart.handytools;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
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

public final class HammerItem extends Item {
    public static final int PRESS_FALL_TICKS = 12;
    public static final double MAX_TARGET_DISTANCE = 1.5D;
    public static final double MIN_TARGET_TOP_ABOVE_FEET = 0.5D;
    public static final double MAX_TARGET_TOP_ABOVE_FEET = 1.5D;

    private static final int MAX_USE_DURATION = 72_000;
    private static final double MAX_TARGET_DISTANCE_SQUARED =
            MAX_TARGET_DISTANCE * MAX_TARGET_DISTANCE;
    private static final double MIN_VIEW_DOT = 0.5D;
    private static final Map<LivingEntity, PressTarget> PRESS_TARGETS =
            Collections.synchronizedMap(new WeakHashMap<>());

    public HammerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isUsingItem()) {
            return InteractionResult.FAIL;
        }

        Optional<PressTarget> target = findLockableTarget(
                context.getLevel(),
                player,
                context.getClickedPos()
        );
        if (target.isEmpty()) {
            return InteractionResult.PASS;
        }

        startPress(player, context.getHand(), target.get());
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

        if (hitResult.getType() != HitResult.Type.BLOCK || player.isUsingItem()) {
            return InteractionResultHolder.fail(stack);
        }

        Optional<PressTarget> target = findLockableTarget(
                level,
                player,
                hitResult.getBlockPos()
        );
        if (target.isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }

        startPress(player, hand, target.get());
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(
            ItemStack stack,
            Level level,
            LivingEntity entity,
            int timeLeft
    ) {
        PRESS_TARGETS.remove(entity);
    }

    @Override
    public ItemStack finishUsingItem(
            ItemStack stack,
            Level level,
            LivingEntity entity
    ) {
        PRESS_TARGETS.remove(entity);
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
        return linear * linear * linear;
    }

    public static boolean canStartPress(
            Level level,
            Player player,
            BlockPos blockPos
    ) {
        return findLockableTarget(level, player, blockPos).isPresent();
    }

    public static Optional<PressTarget> getPressTarget(LivingEntity entity) {
        if (!isPressing(entity)) {
            PRESS_TARGETS.remove(entity);
            return Optional.empty();
        }

        PressTarget existing = PRESS_TARGETS.get(entity);
        if (existing != null) {
            return Optional.of(existing);
        }

        if (!(entity instanceof Player player)) {
            return Optional.empty();
        }

        BlockHitResult hitResult = getPlayerPOVHitResult(
                player.level(),
                player,
                ClipContext.Fluid.NONE
        );
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return Optional.empty();
        }

        Optional<PressTarget> reconstructed = findLockableTarget(
                player.level(),
                player,
                hitResult.getBlockPos()
        );
        reconstructed.ifPresent(target -> PRESS_TARGETS.put(entity, target));
        return reconstructed;
    }

    public static float getTargetFacingYaw(LivingEntity entity) {
        return getPressTarget(entity)
                .map(target -> {
                    Vec3 difference = target.blockCenter()
                            .subtract(entity.position());
                    return (float) Mth.atan2(
                            -difference.x,
                            difference.z
                    ) * Mth.RAD_TO_DEG;
                })
                .orElse(entity.getYRot());
    }

    public static float getPressStopAngleDegrees(LivingEntity entity) {
        return getPressTarget(entity)
                .map(target -> {
                    Vec3 difference = target.contactPoint()
                            .subtract(entity.position());
                    double horizontal = Math.sqrt(
                            difference.x * difference.x
                                    + difference.z * difference.z
                    );
                    double vertical = Math.max(difference.y, 1.0E-4D);
                    return Mth.clamp(
                            (float) Math.toDegrees(
                                    Math.atan2(horizontal, vertical)
                            ),
                            0.0F,
                            80.0F
                    );
                })
                .orElse(0.0F);
    }

    private static Optional<PressTarget> findLockableTarget(
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
                new PressTarget(blockPos.immutable(), blockCenter, contactPoint)
        );
    }

    private static void startPress(
            Player player,
            InteractionHand hand,
            PressTarget target
    ) {
        PRESS_TARGETS.put(player, target);
        player.startUsingItem(hand);
    }

    public record PressTarget(
            BlockPos blockPos,
            Vec3 blockCenter,
            Vec3 contactPoint
    ) {
    }
}
