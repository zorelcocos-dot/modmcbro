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
import net.minecraft.util.math.MathHelper;

/** Glowing arcane glyph that drifts upward, flickers through glyph frames and fades out. */
@Environment(EnvType.CLIENT)
public class RuneGlyphParticle extends SpriteBillboardParticle {
    private final SpriteProvider sprites;

    protected RuneGlyphParticle(ClientWorld world, double x, double y, double z, double vx, double vy, double vz, SpriteProvider sprites) {
        super(world, x, y, z, vx, vy, vz);
        this.sprites = sprites;
        this.velocityX = vx * 0.3;
        this.velocityY = 0.01 + Math.abs(vy) * 0.3;
        this.velocityZ = vz * 0.3;
        this.gravityStrength = 0f;
        this.velocityMultiplier = 0.96f;
        this.maxAge = 30 + random.nextInt(20);
        this.scale = 0.08f + random.nextFloat() * 0.06f;
        this.collidesWithWorld = false;
        this.red = 0.6f + random.nextFloat() * 0.4f;
        this.green = 0.8f + random.nextFloat() * 0.2f;
        this.blue = 1.0f;
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
        this.alpha = MathHelper.clamp(life < 0.2f ? life * 5f : 1.0f - (life - 0.2f) / 0.8f, 0f, 1f);
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
            return new RuneGlyphParticle(world, x, y, z, vx, vy, vz, sprites);
        }
    }
}
