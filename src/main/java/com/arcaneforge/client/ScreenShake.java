package com.arcaneforge.client;

import com.arcaneforge.config.ConfigManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.random.Random;

/** Client-side camera shake state, applied by {@code CameraMixin}. */
@Environment(EnvType.CLIENT)
public final class ScreenShake {
    private static final Random RANDOM = Random.create();
    private static float intensity;
    private static int remaining;
    private static int total;
    private static float prevYaw;
    private static float prevPitch;
    private static float yaw;
    private static float pitch;

    private ScreenShake() {
    }

    public static void start(float newIntensity, int duration) {
        if (!ConfigManager.get().general.screenShake) {
            return;
        }
        if (newIntensity >= intensity * remaining / Math.max(1f, total) || remaining <= 0) {
            intensity = Math.min(10f, newIntensity);
            remaining = Math.max(1, duration);
            total = remaining;
        }
    }

    public static void tick() {
        prevYaw = yaw;
        prevPitch = pitch;
        if (remaining <= 0) {
            yaw = 0;
            pitch = 0;
            return;
        }
        float strength = intensity * remaining / (float) total;
        yaw = (RANDOM.nextFloat() * 2f - 1f) * strength;
        pitch = (RANDOM.nextFloat() * 2f - 1f) * strength * 0.7f;
        remaining--;
    }

    public static boolean active() {
        return remaining > 0 || yaw != 0 || prevYaw != 0;
    }

    public static float yawOffset(float tickDelta) {
        return prevYaw + (yaw - prevYaw) * tickDelta;
    }

    public static float pitchOffset(float tickDelta) {
        return prevPitch + (pitch - prevPitch) * tickDelta;
    }
}
