package com.minecart.handytools.network;

import com.minecart.handytools.toolaction.ToolActionState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Registers and distributes the server-authoritative tool-action snapshots. */
public final class ToolActionNetworking {
    private static final String NETWORK_VERSION = "1";

    private ToolActionNetworking() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(NETWORK_VERSION).playToClient(
                ToolActionStatePayload.TYPE,
                ToolActionStatePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> SyncedToolActionStates.accept(
                                payload,
                                context.player()
                        )
                )
        );
    }

    public static void sendState(Player player, ToolActionState state) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                ToolActionStatePayload.active(player.getUUID(), state)
        );
    }

    public static void sendStateTo(
            ServerPlayer receivingPlayer,
            Player actingPlayer,
            ToolActionState state
    ) {
        PacketDistributor.sendToPlayer(
                receivingPlayer,
                ToolActionStatePayload.active(actingPlayer.getUUID(), state)
        );
    }

    public static void sendInactive(Player player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                player,
                ToolActionStatePayload.inactive(player.getUUID())
        );
    }

    public static void sendInactiveTo(
            ServerPlayer receivingPlayer,
            Player actingPlayer
    ) {
        PacketDistributor.sendToPlayer(
                receivingPlayer,
                ToolActionStatePayload.inactive(actingPlayer.getUUID())
        );
    }
}
