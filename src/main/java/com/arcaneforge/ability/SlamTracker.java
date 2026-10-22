package com.arcaneforge.ability;

import com.arcaneforge.network.ScreenShakePayload;
import com.arcaneforge.registry.ModSounds;
import com.arcaneforge.util.ParticleUtil;
import com.arcaneforge.util.Scheduler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks Thunderlord leaps and resolves the ground slam when the wielder lands. */
public final class SlamTracker {
    private static final int MIN_AIR_TICKS = 4;
    private static final int MAX_AIR_TICKS = 80;
    private static final int DIVE_AFTER_TICKS = 9;
    private static final int SHOCKWAVE_STEPS = 8;
    /** Players stay immune to fall damage for a moment after landing. */
    private static final int LANDING_GRACE_TICKS = 10;

    private static final Map<UUID, Leap> LEAPS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> GRACE = new ConcurrentHashMap<>();

    private SlamTracker() {
    }

    public static void start(ServerPlayerEntity player, AbilityContext ctx) {
        LEAPS.put(player.getUuid(), new Leap(ctx, player.getServerWorld().getTime()));
    }

    public static boolean isProtected(LivingEntity entity) {
        if (LEAPS.containsKey(entity.getUuid())) {
            return true;
        }
        Long until = GRACE.get(entity.getUuid());
        return until != null && until >= entity.getWorld().getTime();
    }

    public static void clear() {
        LEAPS.clear();
        GRACE.clear();
    }

    public static void tick(MinecraftServer server) {
        if (LEAPS.isEmpty()) {
            if (!GRACE.isEmpty()) {
                GRACE.entrySet().removeIf(e -> {
                    ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
                    return p == null || e.getValue() < p.getWorld().getTime();
                });
            }
            return;
        }
        Iterator<Map.Entry<UUID, Leap>> it = LEAPS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Leap> entry = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }
            Leap leap = entry.getValue();
            ServerWorld world = player.getServerWorld();
            long airTicks = world.getTime() - leap.startTick;
            player.fallDistance = 0;
            if (airTicks % 2 == 0) {
                Vec3d p = player.getPos();
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y + 0.5, p.z, 3, 0.3, 0.4, 0.3, 0.05);
            }
            if (airTicks > DIVE_AFTER_TICKS && !player.isOnGround()) {
                player.addVelocity(0, -0.22, 0);
                player.velocityModified = true;
            }
            boolean landed = airTicks >= MIN_AIR_TICKS && (player.isOnGround() || player.isTouchingWater() || player.isClimbing());
            if (landed || airTicks > MAX_AIR_TICKS) {
                it.remove();
                GRACE.put(player.getUuid(), world.getTime() + LANDING_GRACE_TICKS);
                slam(world, player, leap.ctx);
            }
        }
    }

    private static void slam(ServerWorld world, ServerPlayerEntity player, AbilityContext ctx) {
        Vec3d center = player.getPos();
        double radius = ctx.range();
        world.playSound(null, center.x, center.y, center.z, ModSounds.THUNDER_SLAM, SoundCategory.PLAYERS, 1.5f, 0.7f);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 0.8f, 1.2f);
        ParticleUtil.burst(world, ParticleTypes.EXPLOSION_EMITTER, center, 1, 0, 0);
        ParticleUtil.burst(world, ParticleTypes.FLASH, center.add(0, 0.5, 0), 1, 0, 0);

        // Expanding radial shockwave of cracked ground.
        Scheduler.repeat(SHOCKWAVE_STEPS, 1, step -> {
            double r = radius * (step + 1.0) / SHOCKWAVE_STEPS;
            int points = ParticleUtil.scaled((int) (r * 10) + 8);
            for (int i = 0; i < points; i++) {
                double a = Math.PI * 2 * i / points;
                Vec3d p = center.add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
                BlockState ground = world.getBlockState(BlockPos.ofFloored(p.x, p.y - 0.5, p.z));
                if (ground.isAir()) {
                    ground = Blocks.STONE.getDefaultState();
                }
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), p.x, p.y, p.z, 2,
                        0.1, 0.05, 0.1, 0.15);
                if (i % 3 == 0) {
                    ParticleUtil.directional(world, ParticleTypes.CLOUD, p, new Vec3d(Math.cos(a) * 0.25, 0.05, Math.sin(a) * 0.25));
                }
                if (i % 5 == 0) {
                    ParticleUtil.directional(world, ParticleTypes.ELECTRIC_SPARK, p.add(0, 0.3, 0), new Vec3d(0, 0.3, 0));
                }
            }
        });

        List<LivingEntity> victims = new ArrayList<>(Combat.targetsInRadius(world, player, center, radius));
        for (LivingEntity victim : victims) {
            Vec3d offset = victim.getPos().subtract(center);
            double distance = offset.length();
            double falloff = 1.0 - 0.5 * Math.min(1.0, distance / radius);
            victim.damage(world.getDamageSources().playerAttack(player), (float) (ctx.damage() * falloff));
            Vec3d push = distance > 1.0E-3 ? new Vec3d(offset.x, 0, offset.z).normalize() : Vec3d.ZERO;
            victim.addVelocity(push.x * 1.4 * falloff, 0.6 * falloff + 0.2, push.z * 1.4 * falloff);
            victim.velocityModified = true;
        }

        int bolts = ctx.resonance() ? 5 : 3;
        victims.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(center)));
        for (int i = 0; i < bolts; i++) {
            Vec3d at;
            if (i < victims.size()) {
                at = victims.get(i).getPos();
            } else {
                double a = world.random.nextDouble() * Math.PI * 2;
                double r = radius * (0.4 + world.random.nextDouble() * 0.6);
                at = center.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            }
            final Vec3d strikeAt = at;
            Scheduler.schedule(2 + i * 2, () -> {
                LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
                if (bolt != null) {
                    bolt.refreshPositionAfterTeleport(strikeAt);
                    bolt.setCosmetic(com.arcaneforge.config.ConfigManager.get().general.cosmeticLightning);
                    bolt.setChanneler(player);
                    world.spawnEntity(bolt);
                }
            });
        }

        ScreenShakePayload shake = new ScreenShakePayload(ctx.resonance() ? 3.5f : 2.5f, 14);
        for (ServerPlayerEntity nearby : world.getPlayers(p -> p.squaredDistanceTo(center) < 24 * 24)) {
            float scale = (float) (1.0 - Math.sqrt(nearby.squaredDistanceTo(center)) / 24.0);
            ServerPlayNetworking.send(nearby, new ScreenShakePayload(shake.intensity() * Math.max(0.2f, scale), shake.duration()));
        }
    }

    private record Leap(AbilityContext ctx, long startTick) {
    }
}
