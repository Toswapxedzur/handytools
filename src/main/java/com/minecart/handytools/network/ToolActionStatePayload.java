package com.minecart.handytools.network;

import com.minecart.handytools.HandyTools;
import com.minecart.handytools.toolaction.ToolActionPhase;
import com.minecart.handytools.toolaction.ToolActionState;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

/** Server-owned phase snapshot used to drive PAL on every tracking client. */
public record ToolActionStatePayload(
        UUID playerId,
        boolean active,
        ToolActionPhase phase,
        int elapsedTicks,
        int durationTicks,
        int completedCycles,
        boolean releaseRequested,
        InteractionHand hand
) implements CustomPacketPayload {
    public static final Type<ToolActionStatePayload> TYPE =
            new Type<>(HandyTools.id("tool_action_state"));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            ToolActionStatePayload
    > STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ToolActionStatePayload decode(RegistryFriendlyByteBuf buffer) {
            UUID playerId = buffer.readUUID();
            if (!buffer.readBoolean()) {
                return inactive(playerId);
            }

            return new ToolActionStatePayload(
                    playerId,
                    true,
                    buffer.readEnum(ToolActionPhase.class),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readEnum(InteractionHand.class)
            );
        }

        @Override
        public void encode(
                RegistryFriendlyByteBuf buffer,
                ToolActionStatePayload payload
        ) {
            buffer.writeUUID(payload.playerId());
            buffer.writeBoolean(payload.active());
            if (!payload.active()) {
                return;
            }

            buffer.writeEnum(payload.phase());
            buffer.writeVarInt(payload.elapsedTicks());
            buffer.writeVarInt(payload.durationTicks());
            buffer.writeVarInt(payload.completedCycles());
            buffer.writeBoolean(payload.releaseRequested());
            buffer.writeEnum(payload.hand());
        }
    };

    public static ToolActionStatePayload active(
            UUID playerId,
            ToolActionState state
    ) {
        return new ToolActionStatePayload(
                playerId,
                true,
                state.phase(),
                state.elapsedTicks(),
                state.durationTicks(),
                state.completedCycles(),
                state.releaseRequested(),
                state.hand()
        );
    }

    public static ToolActionStatePayload inactive(UUID playerId) {
        return new ToolActionStatePayload(
                playerId,
                false,
                ToolActionPhase.PREPARATION,
                0,
                1,
                0,
                false,
                InteractionHand.MAIN_HAND
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
