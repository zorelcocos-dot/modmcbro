package com.arcaneforge.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.particle.SpriteBillboardParticle;
import net.minecraft.client.particle.SpriteProvider;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;

/** Soft rising steam that swells and fades. */
@Environment(EnvType.CLIENT)
public class SteamPuffParticle extends SpriteBillboardParticle {
    private final SpriteProvider sprites;
    private final float startScale;

    protected SteamPuffParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, SpriteProvider sprites) {
        super(world, x, y, z, vx, vy, vz);
        this.sprites = sprites;
        this.velocityX = vx * 0.5 + (random.nextDouble() - 0.5) * 0.02;
        this.velocityY = 0.04 + Math.abs(vy) * 0.5 + random.nextDouble() * 0.03;
        this.velocityZ = vz * 0.5 + (random.nextDouble() - 0.5) * 0.02;
        this.gravityStrength = -0.02f;
        this.velocityMultiplier = 0.92f;
        this.maxAge = 30 + random.nextInt(25);
        this.startScale = 0.25f + random.nextFloat() * 0.2f;
        this.scale = startScale;
        this.collidesWithWorld = false;
        float grey = 0.85f + random.nextFloat() * 0.15f;
        this.red = grey;
        this.green = grey;
        this.blue = grey;
        this.alpha = 0.8f;
        setSpriteForAge(sprites);
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        float life = (float) age / maxAge;
        this.scale = startScale * (1.0f + life * 2.5f);
        this.alpha = 0.8f * (1.0f - life);
        setSpriteForAge(sprites);
    }

    @Environment(EnvType.CLIENT)
    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteProvider sprites;

        public Factory(SpriteProvider sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientWorld world, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new SteamPuffParticle(world, x, y, z, vx, vy, vz, sprites);
        }
    }
}
