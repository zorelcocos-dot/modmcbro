package com.arcaneforge.item;

import com.arcaneforge.ability.AbilityContext;
import com.arcaneforge.ability.SlamTracker;
import com.arcaneforge.forge.WeaponType;
import com.arcaneforge.util.ParticleUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/**
 * Thunderlord Warhammer - "Thunderfall": leap into the air and crash down, releasing an expanding shockwave of
 * shattered ground, lightning and knockback. The landing is resolved by {@link SlamTracker}.
 */
public class ThunderlordWarhammerItem extends ModularWeaponItem {
    public ThunderlordWarhammerItem(Settings settings, WeaponType type, String modelName) {
        super(settings, type, modelName);
    }

    @Override
    protected boolean useAbility(ServerWorld world, ServerPlayerEntity player, ItemStack stack, AbilityContext ctx) {
        if (SlamTracker.isProtected(player)) {
            return false;
        }
        Vec3d look = player.getRotationVector();
        Vec3d horizontal = new Vec3d(look.x, 0, look.z);
        if (horizontal.lengthSquared() > 1.0E-4) {
            horizontal = horizontal.normalize().multiply(0.65);
        }
        player.setVelocity(horizontal.x, 1.15, horizontal.z);
        player.velocityModified = true;
        player.fallDistance = 0;

        Vec3d feet = player.getPos();
        ParticleUtil.ring(world, ParticleTypes.CLOUD, feet.add(0, 0.1, 0), 0.6, 20, 0.2, 0.02);
        ParticleUtil.burst(world, ParticleTypes.ELECTRIC_SPARK, feet.add(0, 0.5, 0), 20, 0.4, 0.3);
        world.playSound(null, feet.x, feet.y, feet.z, SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 1.0f, 0.6f);
        world.playSound(null, feet.x, feet.y, feet.z, SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.PLAYERS, 0.8f, 1.2f);

        SlamTracker.start(player, ctx);
        return true;
    }
}
