package com.minecart.handytools.network;

import com.minecart.handytools.toolaction.ToolActionPhase;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/** Client-side mirror of server phase snapshots, kept free of client-only types. */
public final class SyncedToolActionStates {
    private static final Map<UUID, SyncedState> STATES = new HashMap<>();

    private SyncedToolActionStates() {
    }

    public static void accept(
            ToolActionStatePayload payload,
            Player receivingPlayer
    ) {
        if (!payload.active()) {
            STATES.remove(payload.playerId());
            return;
        }

        STATES.put(
                payload.playerId(),
                new SyncedState(
                        payload.phase(),
                        payload.elapsedTicks(),
                        payload.durationTicks(),
                        payload.completedCycles(),
                        payload.releaseRequested(),
                        payload.hand(),
                        receivingPlayer.level().getGameTime()
                )
        );
    }

    public static Optional<SyncedState> get(Player player) {
        return Optional.ofNullable(STATES.get(player.getUUID()));
    }

    public static void clear() {
        STATES.clear();
    }

    public record SyncedState(
            ToolActionPhase phase,
            int elapsedTicks,
            int durationTicks,
            int completedCycles,
            boolean releaseRequested,
            InteractionHand hand,
            long receivedGameTime
    ) {
        public int estimatedElapsedTicks(Player player) {
            long age = Math.max(
                    0L,
                    player.level().getGameTime() - receivedGameTime
            );
            return Mth.clamp(
                    elapsedTicks + (int) Math.min(Integer.MAX_VALUE, age),
                    0,
                    durationTicks
            );
        }
    }
}
