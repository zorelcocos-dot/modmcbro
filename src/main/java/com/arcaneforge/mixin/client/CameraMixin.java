package com.arcaneforge.mixin.client;

import com.arcaneforge.client.ScreenShake;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies Arcane Forge screen shake on top of the vanilla camera rotation. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    public abstract float getYaw();

    @Shadow
    public abstract float getPitch();

    @Inject(method = "update", at = @At("TAIL"))
    private void arcaneforge$applyShake(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView,
                                        float tickDelta, CallbackInfo ci) {
        if (ScreenShake.active()) {
            setRotation(getYaw() + ScreenShake.yawOffset(tickDelta), getPitch() + ScreenShake.pitchOffset(tickDelta));
        }
    }
}
