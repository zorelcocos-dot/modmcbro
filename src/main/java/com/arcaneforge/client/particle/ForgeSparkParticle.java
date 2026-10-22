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

/** Fullbright spark that arcs under gravity, bounces off blocks and cools from white-yellow to deep orange. */
@Environment(EnvType.CLIENT)
public class ForgeSparkParticle extends SpriteBillboardParticle {
    private final SpriteProvider sprites;

    protected ForgeSparkParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, SpriteProvider sprites) {
        super(world, x, y, z, vx, vy, vz);
        this.sprites = sprites;
        this.velocityX = vx;
        this.velocityY = vy;
        this.velocityZ = vz;
        this.gravityStrength = 0.9f;
        this.velocityMultiplier = 0.94f;
        this.maxAge = 12 + random.nextInt(14);
        this.scale = 0.05f + random.nextFloat() * 0.05f;
        this.collidesWithWorld = true;
        this.red = 1.0f;
        this.green = 0.95f;
        this.blue = 0.7f;
        setSpriteForAge(sprites);
    }

    @Override
    public ParticleTextureSheet getType() {
        return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getBrightness(float tint) {
        return 0xF000F0;
    }

    @Override
    public void tick() {
        super.tick();
        float life = (float) age / maxAge;
        this.green = 0.95f - life * 0.6f;
        this.blue = Math.max(0f, 0.7f - life * 1.2f);
        this.alpha = 1.0f - life * life;
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
            return new ForgeSparkParticle(world, x, y, z, vx, vy, vz, sprites);
        }
    }
}
