package com.arcaneforge.item;

import com.arcaneforge.ability.AbilityContext;
import com.arcaneforge.forge.WeaponType;
import com.arcaneforge.registry.ModSounds;
import com.arcaneforge.util.ParticleUtil;
import com.arcaneforge.util.Scheduler;
import com.arcaneforge.util.TextUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustColorTransitionParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bloodmoon Daggers - passive lifesteal with crimson slash trails on every hit. Right-click "Crimson Frenzy" trades
 * health for speed, strength and doubled lifesteal.
 */
public class BloodmoonDaggersItem extends ModularWeaponItem {
    private static final int CRIMSON = 0xC00020;
    private static final int FRENZY_TICKS = 120;
    private static final float FRENZY_COST = 4.0f;
    private static final Map<UUID, Long> FRENZY_UNTIL = new ConcurrentHashMap<>();

    public BloodmoonDaggersItem(Settings settings, WeaponType type, String modelName) {
        super(settings, type, modelName);
    }

    public static boolean inFrenzy(PlayerEntity player) {
        Long until = FRENZY_UNTIL.get(player.getUuid());
        if (until == null) {
            return false;
        }
        if (until < player.getWorld().getTime()) {
            FRENZY_UNTIL.remove(player.getUuid());
            return false;
        }
        return true;
    }

    @Override
    protected boolean useAbility(ServerWorld world, ServerPlayerEntity player, ItemStack stack, AbilityContext ctx) {
        if (player.getHealth() <= FRENZY_COST + 2.0f && !player.getAbilities().creativeMode) {
            TextUtil.actionBar(player, "frenzy_too_weak");
            return false;
        }
        if (!player.getAbilities().creativeMode) {
            player.setHealth(player.getHealth() - FRENZY_COST);
        }
        int amplifier = ctx.resonance() ? 1 : 0;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, FRENZY_TICKS, 1));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, FRENZY_TICKS, amplifier));
        FRENZY_UNTIL.put(player.getUuid(), world.getTime() + FRENZY_TICKS);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.BLOOD_DRAIN, SoundCategory.PLAYERS, 1.2f, 1.0f);

        DustColorTransitionParticleEffect blood = new DustColorTransitionParticleEffect(
                new Vector3f(0.9f, 0.05f, 0.1f), new Vector3f(0.25f, 0f, 0.02f), 1.2f);
        Scheduler.repeat(20, 1, step -> {
            if (!player.isAlive()) {
                return;
            }
            ParticleUtil.helix(world, blood, player.getPos(), 0.7, 2.0, 8, step * 0.45);
            if (step % 5 == 0) {
                ParticleUtil.burst(world, ParticleTypes.DAMAGE_INDICATOR, player.getPos().add(0, 1.0, 0), 3, 0.3, 0.1);
            }
        });
        ParticleUtil.ring(world, ParticleUtil.dust(CRIMSON, 1.5f), player.getPos().add(0, 0.1, 0), 1.2, 30, 0.2, 0.0);
        return true;
    }

    @Override
    protected void onMeleeHit(ServerWorld world, ItemStack stack, LivingEntity target, PlayerEntity attacker, AbilityContext ctx) {
        double steal = ctx.lifesteal() * ctx.attackDamage() * (inFrenzy(attacker) ? 2.0 : 1.0);
        if (steal > 0) {
            attacker.heal((float) steal);
        }
        // Crimson slash trail: a diagonal arc carved across the target.
        Vec3d mid = target.getPos().add(0, target.getHeight() * 0.55, 0);
        Vec3d look = attacker.getRotationVector();
        Vec3d side = new Vec3d(-look.z, 0, look.x).normalize();
        int points = ParticleUtil.scaled(14);
        for (int i = 0; i <= points; i++) {
            double t = (double) i / points - 0.5;
            double curve = Math.cos(t * Math.PI) * 0.25;
            Vec3d p = mid.add(side.multiply(t * 1.6)).add(0, t * 1.0 + curve, 0).subtract(look.multiply(curve));
            world.spawnParticles(ParticleUtil.dust(i % 2 == 0 ? CRIMSON : 0x600010, 1.1f), p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        Vec3d chest = attacker.getPos().add(0, 1.0, 0);
        ParticleUtil.line(world, ParticleUtil.dust(0xFF2040, 0.6f), mid, chest, 0.35);
    }
}
