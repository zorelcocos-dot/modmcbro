package com.arcaneforge.item;

import com.arcaneforge.ability.AbilityContext;
import com.arcaneforge.ability.Combat;
import com.arcaneforge.forge.WeaponType;
import com.arcaneforge.registry.ModSounds;
import com.arcaneforge.util.ParticleUtil;
import com.arcaneforge.util.Scheduler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Frostbite Warscythe - "Glacial Vortex": a spiralling blizzard drags nearby enemies to the wielder, then encases
 * them in ice (heavy slowness, freezing and crystal shards).
 */
public class FrostbiteWarscytheItem extends ModularWeaponItem {
    private static final int PULL_TICKS = 12;

    public FrostbiteWarscytheItem(Settings settings, WeaponType type, String modelName) {
        super(settings, type, modelName);
    }

    @Override
    protected boolean useAbility(ServerWorld world, ServerPlayerEntity player, ItemStack stack, AbilityContext ctx) {
        final double radius = ctx.range();
        final List<LivingEntity> victims = Combat.targetsInRadius(world, player, player.getPos(), radius);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.FROST_VORTEX, SoundCategory.PLAYERS, 1.2f, 1.0f);
        player.swingHand(net.minecraft.util.Hand.MAIN_HAND, true);

        Scheduler.repeat(PULL_TICKS, 1, step -> {
            if (!player.isAlive()) {
                return;
            }
            Vec3d center = player.getPos();
            double progress = (double) step / PULL_TICKS;
            double r = radius * (1.0 - progress * 0.85);
            int arms = 3;
            int perArm = ParticleUtil.scaled(10);
            for (int arm = 0; arm < arms; arm++) {
                for (int i = 0; i < perArm; i++) {
                    double t = (double) i / perArm;
                    double angle = step * 0.55 + arm * (Math.PI * 2 / arms) + t * 2.2;
                    double rr = r * (0.25 + 0.75 * t);
                    Vec3d p = center.add(Math.cos(angle) * rr, 0.2 + t * 1.6, Math.sin(angle) * rr);
                    Vec3d inward = center.add(0, 1, 0).subtract(p).normalize().multiply(0.18);
                    ParticleUtil.directional(world, ParticleTypes.SNOWFLAKE, p, inward);
                    if (i % 3 == 0) {
                        ParticleUtil.directional(world, ParticleTypes.ITEM_SNOWBALL, p, inward.multiply(0.5));
                    }
                }
            }
            for (LivingEntity victim : victims) {
                if (!victim.isAlive()) {
                    continue;
                }
                Vec3d pull = center.subtract(victim.getPos());
                double distance = pull.length();
                if (distance > 1.6) {
                    Vec3d v = pull.normalize().multiply(Math.min(0.9, 0.3 + distance * 0.08));
                    victim.setVelocity(v.x, Math.max(0.05, v.y * 0.5 + 0.08), v.z);
                    victim.velocityModified = true;
                }
            }
            if (step % 4 == 0) {
                world.playSound(null, center.x, center.y, center.z, SoundEvents.BLOCK_POWDER_SNOW_STEP, SoundCategory.PLAYERS, 0.8f, 0.6f);
            }
        });

        Scheduler.schedule(PULL_TICKS + 1, () -> {
            BlockStateParticleEffect ice = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.BLUE_ICE.getDefaultState());
            BlockStateParticleEffect packed = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.PACKED_ICE.getDefaultState());
            int duration = ctx.resonance() ? 160 : 100;
            for (LivingEntity victim : victims) {
                if (!victim.isAlive()) {
                    continue;
                }
                victim.timeUntilRegen = 0;
                victim.damage(world.getDamageSources().freeze(), (float) ctx.damage());
                victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, duration, ctx.resonance() ? 4 : 3), player);
                victim.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, duration, 1), player);
                victim.setFrozenTicks(victim.getMinFreezeDamageTicks() + duration);
                victim.setVelocity(Vec3d.ZERO);
                victim.velocityModified = true;
                Vec3d base = victim.getPos();
                double h = victim.getHeight();
                double w = victim.getWidth() * 0.6 + 0.2;
                ParticleUtil.burst(world, ice, base.add(0, h * 0.5, 0), 30, w, h * 0.5, w, 0.1);
                ParticleUtil.burst(world, packed, base.add(0, h * 0.3, 0), 15, w, h * 0.3, w, 0.05);
                ParticleUtil.helix(world, ParticleTypes.SNOWFLAKE, base, w + 0.2, h + 0.3, 14, world.random.nextDouble() * Math.PI);
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI * 2 * i / 6;
                    Vec3d spikeBase = base.add(Math.cos(a) * w, 0, Math.sin(a) * w);
                    ParticleUtil.line(world, ParticleTypes.END_ROD, spikeBase, spikeBase.add(Math.cos(a) * 0.3, h * 0.9, Math.sin(a) * 0.3), 0.2);
                }
                world.playSound(null, base.x, base.y, base.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.9f, 1.4f);
            }
            ParticleUtil.ring(world, ParticleTypes.SNOWFLAKE, player.getPos().add(0, 0.2, 0), 1.0, 30, 0.35, 0.05);
        });
        return true;
    }
}
