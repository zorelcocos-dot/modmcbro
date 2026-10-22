package com.arcaneforge.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registers the mod's custom payload types (both logical sides). */
public final class ModNetworking {
    private ModNetworking() {
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(ScreenShakePayload.ID, ScreenShakePayload.CODEC);
    }
}
