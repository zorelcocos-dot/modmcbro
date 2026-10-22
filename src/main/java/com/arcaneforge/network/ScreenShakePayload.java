package com.arcaneforge.network;

import com.arcaneforge.ArcaneForge;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Server to client: shake the camera with the given intensity (degrees) for a number of ticks. */
public record ScreenShakePayload(float intensity, int duration) implements CustomPayload {
    public static final CustomPayload.Id<ScreenShakePayload> ID = new CustomPayload.Id<>(ArcaneForge.id("screen_shake"));
    public static final PacketCodec<RegistryByteBuf, ScreenShakePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.FLOAT, ScreenShakePayload::intensity,
            PacketCodecs.VAR_INT, ScreenShakePayload::duration,
            ScreenShakePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
