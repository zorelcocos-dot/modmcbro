package com.arcaneforge.item;

import com.arcaneforge.ability.AbilityContext;
import com.arcaneforge.ability.Combat;
import com.arcaneforge.forge.WeaponType;
import com.arcaneforge.registry.ModParticles;
import com.arcaneforge.registry.ModSounds;
import com.arcaneforge.util.ParticleUtil;
import com.arcaneforge.util.Scheduler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Solaris Greatsword - "Solar Crescent": a sweeping fire wave that launches and ignites everything in a cone. */
public class SolarisGreatswordItem extends ModularWeaponItem {
    private static final double HALF_ANGLE = Math.toRadians(60);
    private static final int WAVE_STEPS = 7;

    public SolarisGreatswordItem(Settings settings, WeaponType type, String modelName) {
        super(settings, type, modelName);
    }

    @Override
    protected boolean useAbility(ServerWorld world, ServerPlayerEntity player, ItemStack stack, AbilityContext ctx) {
        Vec3d look = player.getRotationVector();
        Vec3d dir = new Vec3d(look.x, 0, look.z);
        if (dir.lengthSquared() < 1.0E-4) {
            dir = Vec3d.fromPolar(0, player.getYaw());
        }
        final Vec3d forward = dir.normalize();
        final Vec3d origin = player.getPos().add(0, 1.0, 0);
        final double range = ctx.range();

        world.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.FIRE_CRESCENT, SoundCategory.PLAYERS, 1.2f, 0.9f);
        player.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);

        // Expanding crescent of fire, drawn as layered arcs that travel outward over several ticks.
        Scheduler.repeat(WAVE_STEPS, 1, step -> {
            double t = (step + 1.0) / WAVE_STEPS;
            double dist = 0.8 + (range - 0.8) * t;
            int points = ParticleUtil.scaled(10 + (int) (dist * 5));
            for (int i = 0; i <= points; i++) {
                double a = -HALF_ANGLE + 2 * HALF_ANGLE * i / points;
                Vec3d d = ParticleUtil.rotateY(forward, a);
                double lift = Math.cos(a / HALF_ANGLE * Math.PI / 2) * 0.35;
                Vec3d p = origin.add(d.multiply(dist)).add(0, lift - 0.2, 0);
                ParticleUtil.directional(world, ParticleTypes.FLAME, p, d.multiply(0.08).add(0, 0.02, 0));
                if (i % 2 == 0) {
                    ParticleUtil.directional(world, ModParticles.FORGE_SPARK, p.add(0, 0.25, 0), d.multiply(0.15).add(0, 0.08, 0));
                }
                if (i % 4 == 0) {
                    ParticleUtil.directional(world, ParticleTypes.LAVA, p, Vec3d.ZERO);
                }
                if (ctx.resonance() && i % 3 == 0) {
                    ParticleUtil.directional(world, ParticleTypes.SOUL_FIRE_FLAME, p.add(0, -0.25, 0), d.multiply(0.05));
                }
            }
            if (step == WAVE_STEPS - 1) {
                Vec3d tip = origin.add(forward.multiply(range));
                ParticleUtil.burst(world, ParticleTypes.EXPLOSION, tip, 2, 0.6, 0.0);
                ParticleUtil.burst(world, ParticleTypes.LARGE_SMOKE, tip, 16, 0.8, 0.03);
            }
        });

        Box area = player.getBoundingBox().expand(range, 2.0, range);
        for (LivingEntity target : Combat.targetsInBox(world, player, area)) {
            Vec3d to = target.getPos().subtract(player.getPos());
            Vec3d flat = new Vec3d(to.x, 0, to.z);
            double distance = flat.length();
            if (distance > range) {
                continue;
            }
            if (distance > 1.0E-3) {
                double angle = Math.acos(Math.max(-1, Math.min(1, flat.normalize().dotProduct(forward))));
                if (angle > HALF_ANGLE) {
                    continue;
                }
            }
            float falloff = (float) (1.0 - 0.4 * Math.min(1.0, distance / range));
            target.damage(world.getDamageSources().playerAttack(player), (float) ctx.damage() * falloff);
            target.setOnFireFor(ctx.resonance() ? 8 : 5);
            Vec3d push = forward.multiply(0.9 * falloff);
            target.addVelocity(push.x, 0.55, push.z);
            target.velocityModified = true;
            Vec3d mid = target.getPos().add(0, target.getHeight() * 0.5, 0);
            ParticleUtil.burst(world, ParticleTypes.FLAME, mid, 16, 0.3, 0.06);
        }
        return true;
    }
}
