package com.minecart.handytools.client;

import com.minecart.handytools.HandyTools;
import com.minecart.handytools.HammerItem;
import com.minecart.handytools.network.SyncedToolActionStates;
import com.minecart.handytools.toolaction.ToolActionManager;
import com.minecart.handytools.toolaction.ToolActionPhase;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.AnimationController;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.MirrorModifier;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.enums.PlayState;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Registers the server-synchronized hammer action layer with PAL. */
public final class HammerAnimationClientSetup {
    public static final ResourceLocation LAYER_ID =
            HandyTools.id("hammer_action");
    private static final int LAYER_PRIORITY = 2_000;
    private static final boolean FIRST_PERSON_MODEL_LOADED =
            ModList.get().isLoaded("firstperson");

    private HammerAnimationClientSetup() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(
                HammerAnimationClientSetup::registerHammerAnimation
        );
    }

    private static void registerHammerAnimation(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                    LAYER_ID,
                    LAYER_PRIORITY,
                    HammerAnimationClientSetup::createController
            );
            HandyTools.LOGGER.info(
                    "Registered PAL hammer animation layer; FirstPerson compatibility: {}",
                    FIRST_PERSON_MODEL_LOADED
            );
        });
    }

    private static PlayerAnimationController createController(
            AbstractClientPlayer player
    ) {
        MirrorModifier mirror = new MirrorModifier();
        mirror.enabled = false;
        HammerStateHandler stateHandler = new HammerStateHandler(player, mirror);
        PlayerAnimationController controller = new PlayerAnimationController(
                player,
                stateHandler::handle
        );
        controller.addModifierLast(mirror);
        // PAL's partial-model first-person pass distorts the camera/body view.
        // First-person Model, when installed, renders this normal PAL pose.
        controller.setFirstPersonMode(FirstPersonMode.NONE);
        return controller;
    }

    private static final class HammerStateHandler {
        // Fade length for the return-to-rest; must be <= releaseTicks (10) so
        // the fade finishes before the action ends and the controller resets.
        private static final int RETURN_FADE_TICKS = 8;

        private final AbstractClientPlayer player;
        private final MirrorModifier mirror;
        private final Map<ToolActionPhase, CachedAnimation> animationCache =
                new EnumMap<>(ToolActionPhase.class);
        private boolean acting;
        private boolean returnFadeStarted;
        private RawAnimation restCache;

        private HammerStateHandler(
                AbstractClientPlayer player,
                MirrorModifier mirror
        ) {
            this.player = player;
            this.mirror = mirror;
        }

        private PlayState handle(
                AnimationController controller,
                com.zigythebird.playeranimcore.animation.AnimationData data,
                AnimationController.AnimationSetter animationSetter
        ) {
            Optional<ActionView> view = actionView();
            if (view.isEmpty()) {
                if (acting) {
                    controller.stopTriggeredAnimation();
                    controller.forceAnimationReset();
                }
                acting = false;
                mirror.enabled = false;
                returnFadeStarted = false;
                return PlayState.STOP;
            }

            ActionView action = view.get();
            acting = true;
            mirror.enabled = actionArm(action.hand()) == HumanoidArm.LEFT;

            if (action.phase() == ToolActionPhase.RELEASE) {
                // Return: snapshot the live (possibly deeply body-dragged) pose
                // and fade it to the neutral rest clip. replaceAnimationWithFade
                // suspends this scrubbing handler until the fade self-removes,
                // so fire it exactly once and yield the pose to the trigger.
                if (!returnFadeStarted) {
                    RawAnimation rest = restAnimation();
                    if (rest != null) {
                        controller.replaceAnimationWithFade(
                                AbstractFadeModifier.standardFadeIn(
                                        RETURN_FADE_TICKS,
                                        EasingType.EASE_IN_OUT_SINE
                                ),
                                rest
                        );
                    }
                    returnFadeStarted = true;
                }
                return PlayState.CONTINUE;
            }

            returnFadeStarted = false;
            RawAnimation animation = animationFor(action.phase());
            if (animation == null) {
                return PlayState.STOP;
            }
            return animationSetter.setAnimation(
                    animation,
                    animationElapsedTicks(action)
            );
        }

        private RawAnimation restAnimation() {
            if (restCache == null) {
                Animation source = PlayerAnimResources.getAnimation(
                        HandyTools.id("hammer_rest")
                );
                if (source == null) {
                    return null;
                }
                restCache = RawAnimation.begin().then(
                        source, Animation.LoopType.PLAY_ONCE
                );
            }
            return restCache;
        }

        private int animationElapsedTicks(ActionView action) {
            return switch (action.phase()) {
                case OPERATION_DESCEND ->
                        HammerItem.OPERATION_RAISE_TICKS + action.elapsedTicks();
                case OPERATION_DWELL ->    // freeze on the landed contact frame
                        HammerItem.OPERATION_RAISE_TICKS
                                + HammerItem.OPERATION_DESCEND_TICKS;
                default -> action.elapsedTicks();
            };
        }

        private Optional<ActionView> actionView() {
            if (player instanceof LocalPlayer) {
                return ToolActionManager.getState(player)
                        .filter(state -> player.getItemInHand(state.hand()).getItem()
                                instanceof HammerItem)
                        .map(state -> new ActionView(
                                state.phase(),
                                state.hand(),
                                state.elapsedTicks()
                        ));
            }

            Optional<SyncedToolActionStates.SyncedState> synced =
                    SyncedToolActionStates.get(player);
            if (synced.isPresent()) {
                SyncedToolActionStates.SyncedState state = synced.get();
                if (!(player.getItemInHand(state.hand()).getItem()
                        instanceof HammerItem)) {
                    return Optional.empty();
                }
                return Optional.of(new ActionView(
                        state.phase(),
                        state.hand(),
                        state.estimatedElapsedTicks(player)
                ));
            }

            return Optional.empty();
        }

        private RawAnimation animationFor(ToolActionPhase phase) {
            ToolActionPhase animationPhase =
                    (phase == ToolActionPhase.OPERATION_DESCEND
                            || phase == ToolActionPhase.OPERATION_DWELL)
                            ? ToolActionPhase.OPERATION_RAISE
                            : phase;
            ResourceLocation animationId = HandyTools.id(switch (animationPhase) {
                case PREPARATION -> "hammer_preparation";
                case OPERATION_RAISE -> "hammer_operation";
                case OPERATION_DESCEND, OPERATION_DWELL -> throw new IllegalStateException(
                        "Operation sub-phases must share the operation animation"
                );
                case RELEASE -> "hammer_release";
            });
            Animation source = PlayerAnimResources.getAnimation(animationId);
            if (source == null) {
                return null;
            }

            CachedAnimation cached = animationCache.get(animationPhase);
            if (cached == null || cached.source() != source) {
                Animation.LoopType loopType =
                        animationPhase == ToolActionPhase.OPERATION_RAISE
                                ? Animation.LoopType.LOOP
                                : Animation.LoopType.HOLD_ON_LAST_FRAME;
                cached = new CachedAnimation(
                        source,
                        RawAnimation.begin().then(
                                source,
                                loopType
                        )
                );
                animationCache.put(animationPhase, cached);
            }
            return cached.raw();
        }

        private HumanoidArm actionArm(InteractionHand hand) {
            return hand == InteractionHand.MAIN_HAND
                    ? player.getMainArm()
                    : player.getMainArm().getOpposite();
        }

    }

    private record ActionView(
            ToolActionPhase phase,
            InteractionHand hand,
            int elapsedTicks
    ) {
    }

    private record CachedAnimation(
            Animation source,
            RawAnimation raw
    ) {
    }
}
