package com.arcaneforge.util;

import com.arcaneforge.config.ConfigManager;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/** Server-side particle math helpers. Every count is scaled by the configured density multiplier. */
public final class ParticleUtil {
    private ParticleUtil() {
    }

    public static int scaled(int count) {
        double density = ConfigManager.get().particles.densityMultiplier;
        if (density <= 0) {
            return 0;
        }
        int cap = ConfigManager.get().particles.maxParticlesPerBurst;
        return Math.min(cap, Math.max(1, (int) Math.round(count * density)));
    }

    public static DustParticleEffect dust(int rgb, float scale) {
        return new DustParticleEffect(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f), scale);
    }

    /** Random burst around a point. */
    public static void burst(ServerWorld world, ParticleEffect effect, Vec3d pos, int count, double spread, double speed) {
        int n = scaled(count);
        if (n > 0) {
            world.spawnParticles(effect, pos.x, pos.y, pos.z, n, spread, spread, spread, speed);
        }
    }

    public static void burst(ServerWorld world, ParticleEffect effect, Vec3d pos, int count, double sx, double sy, double sz, double speed) {
        int n = scaled(count);
        if (n > 0) {
            world.spawnParticles(effect, pos.x, pos.y, pos.z, n, sx, sy, sz, speed);
        }
    }

    /** One particle travelling with an exact velocity (count = 0 makes the offset act as a direction). */
    public static void directional(ServerWorld world, ParticleEffect effect, Vec3d pos, Vec3d velocity) {
        double len = velocity.length();
        if (len < 1.0E-4) {
            world.spawnParticles(effect, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            return;
        }
        Vec3d dir = velocity.multiply(1.0 / len);
        world.spawnParticles(effect, pos.x, pos.y, pos.z, 0, dir.x, dir.y, dir.z, len);
    }

    /** Sparks fanning out of {@code pos} around {@code normal}. */
    public static void sparkFan(ServerWorld world, ParticleEffect effect, Vec3d pos, Vec3d normal, int count, double speed) {
        int n = scaled(count);
        for (int i = 0; i < n; i++) {
            Vec3d rand = new Vec3d(world.random.nextGaussian(), world.random.nextGaussian(), world.random.nextGaussian()).multiply(0.6);
            Vec3d v = normal.add(rand).normalize().multiply(speed * (0.5 + world.random.nextDouble()));
            directional(world, effect, pos, v);
        }
    }

    public static void line(ServerWorld world, ParticleEffect effect, Vec3d from, Vec3d to, double spacing) {
        Vec3d delta = to.subtract(from);
        double length = delta.length();
        int steps = Math.max(1, scaled((int) Math.ceil(length / Math.max(0.05, spacing))));
        for (int i = 0; i <= steps; i++) {
            Vec3d p = from.add(delta.multiply((double) i / steps));
            world.spawnParticles(effect, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    /** Horizontal ring; {@code outward} gives each particle a radial velocity. */
    public static void ring(ServerWorld world, ParticleEffect effect, Vec3d center, double radius, int points, double outward, double up) {
        int n = scaled(points);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double cx = Math.cos(a);
            double cz = Math.sin(a);
            Vec3d p = center.add(cx * radius, 0, cz * radius);
            if (outward == 0 && up == 0) {
                world.spawnParticles(effect, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            } else {
                directional(world, effect, p, new Vec3d(cx * outward, up, cz * outward));
            }
        }
    }

    /** Double helix rising around a vertical axis. */
    public static void helix(ServerWorld world, ParticleEffect effect, Vec3d base, double radius, double height, int points, double phase) {
        int n = scaled(points);
        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            double a = phase + t * Math.PI * 4;
            for (int strand = 0; strand < 2; strand++) {
                double aa = a + strand * Math.PI;
                Vec3d p = base.add(Math.cos(aa) * radius, t * height, Math.sin(aa) * radius);
                world.spawnParticles(effect, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
    }

    /** Rotates a vector around the Y axis by {@code radians}. */
    public static Vec3d rotateY(Vec3d v, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3d(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
    }
}
