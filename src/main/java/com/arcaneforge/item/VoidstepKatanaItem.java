package com.arcaneforge.item;

import com.arcaneforge.ability.AbilityContext;
import com.arcaneforge.ability.Combat;
import com.arcaneforge.forge.WeaponType;
import com.arcaneforge.registry.ModSounds;
import com.arcaneforge.util.ParticleUtil;
import com.arcaneforge.util.Scheduler;
import com.arcaneforge.util.TextUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Voidstep Katana - "Voidstep": an instant lightning dash that leaves shadow after-images and, a heartbeat later,
 * slashes every enemy the wielder passed through.
 */
public class VoidstepKatanaItem extends ModularWeaponItem {
    private static final int SLASH_DELAY = 10;
    private static final int SHADOW = 0x3A0B5C;
    private static final int VOID_PURPLE = 0xA040FF;

    public VoidstepKatanaItem(Settings settings, WeaponType type, String modelName) {
        super(settings, type, modelName);
    }

    @Override
    protected boolean useAbility(ServerWorld world, ServerPlayerEntity player, ItemStack stack, AbilityContext ctx) {
        Vec3d look = player.getRotationVector();
        Vec3d dir = new Vec3d(look.x, Math.max(-0.35, Math.min(0.35, look.y)), look.z).normalize();
        Vec3d start = player.getPos();
        Box box = player.getBoundingBox();

        double travelled = 0;
        for (double d = 0.5; d <= ctx.range(); d += 0.5) {
            if (!world.isSpaceEmpty(player, box.offset(dir.multiply(d)))) {
                break;
            }
            travelled = d;
        }
        if (travelled < 1.0) {
            TextUtil.actionBar(player, "ability_blocked");
            return false;
        }
        Vec3d end = start.add(dir.multiply(travelled));
        Vec3d chestStart = start.add(0, 1.0, 0);
        Vec3d chestEnd = end.add(0, 1.0, 0);

        Box sweep = box.union(box.offset(dir.multiply(travelled))).expand(1.2);
        List<LivingEntity> victims = Combat.targetsInBox(world, player, sweep).stream()
                .filter(e -> Combat.distanceToSegment(e.getPos().add(0, e.getHeight() * 0.5, 0), chestStart, chestEnd) <= 1.8)
                .toList();

        // After-images: dark silhouettes and lightning along the dash path.
        double spacing = 0.7;
        int images = (int) (travelled / spacing);
        for (int i = 0; i <= images; i++) {
            Vec3d p = start.add(dir.multiply(i * spacing));
            for (double h = 0.1; h <= 1.8; h += 0.3) {
                world.spawnParticles(ParticleUtil.dust(i % 2 == 0 ? SHADOW : VOID_PURPLE, 1.3f), p.x, p.y + h, p.z, 1, 0.12, 0.04, 0.12, 0);
            }
            ParticleUtil.burst(world, ParticleTypes.REVERSE_PORTAL, p.add(0, 1.0, 0), 3, 0.2, 0.02);
        }
        Vec3d prev = chestStart;
        int bolts = Math.max(3, (int) (travelled * 1.5));
        for (int i = 1; i <= bolts; i++) {
            Vec3d next = chestStart.lerp(chestEnd, (double) i / bolts)
                    .add(world.random.nextGaussian() * 0.25, world.random.nextGaussian() * 0.3, world.random.nextGaussian() * 0.25);
            ParticleUtil.line(world, ParticleTypes.ELECTRIC_SPARK, prev, next, 0.15);
            prev = next;
        }
        ParticleUtil.burst(world, ParticleTypes.SQUID_INK, chestStart, 12, 0.3, 0.05);

        player.requestTeleport(end.x, end.y, end.z);
        player.fallDistance = 0;
        player.setVelocity(dir.multiply(0.3));
        player.velocityModified = true;
        world.playSound(null, start.x, start.y, start.z, ModSounds.VOID_DASH, SoundCategory.PLAYERS, 1.0f, 1.3f);
        world.playSound(null, end.x, end.y, end.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.6f, 1.6f);

        Scheduler.schedule(SLASH_DELAY, () -> {
            if (victims.isEmpty()) {
                return;
            }
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                    SoundCategory.PLAYERS, 1.2f, 0.7f);
            for (LivingEntity victim : victims) {
                if (!victim.isAlive()) {
                    continue;
                }
                Vec3d mid = victim.getPos().add(0, victim.getHeight() * 0.5, 0);
                victim.timeUntilRegen = 0;
                victim.damage(world.getDamageSources().playerAttack(player), (float) ctx.damage());
                if (ctx.resonance()) {
                    victim.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 60, 0), player);
                }
                ParticleUtil.burst(world, ParticleTypes.SWEEP_ATTACK, mid, 3, 0.4, 0.0);
                for (int k = -4; k <= 4; k++) {
                    Vec3d a = mid.add(k * 0.12, k * 0.12, 0);
                    Vec3d b = mid.add(k * 0.12, -k * 0.12, 0);
                    world.spawnParticles(ParticleUtil.dust(VOID_PURPLE, 0.9f), a.x, a.y, a.z, 1, 0, 0, 0, 0);
                    world.spawnParticles(ParticleUtil.dust(SHADOW, 0.9f), b.x, b.y, b.z, 1, 0, 0, 0, 0);
                }
                ParticleUtil.burst(world, ParticleTypes.CRIT, mid, 10, 0.3, 0.3);
            }
        });
        return true;
    }
}
