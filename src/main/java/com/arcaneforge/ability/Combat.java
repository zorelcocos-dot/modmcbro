package com.arcaneforge.ability;

import com.arcaneforge.config.ConfigManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/** Target selection rules shared by every ability so allies, pets and spectators are never hit. */
public final class Combat {
    private Combat() {
    }

    public static boolean canHit(PlayerEntity attacker, LivingEntity target) {
        if (target == attacker || !target.isAlive() || target.isSpectator() || !target.isAttackable()) {
            return false;
        }
        if (target instanceof ArmorStandEntity stand && stand.isMarker()) {
            return false;
        }
        if (target instanceof TameableEntity pet && pet.isOwner(attacker)) {
            return false;
        }
        if (target instanceof PlayerEntity other) {
            if (!ConfigManager.get().general.abilitiesAffectPlayers || other.isCreative()) {
                return false;
            }
            return attacker.shouldDamagePlayer(other);
        }
        return !attacker.isTeammate(target);
    }

    public static List<LivingEntity> targetsInBox(ServerWorld world, PlayerEntity attacker, Box box) {
        return world.getEntitiesByClass(LivingEntity.class, box, e -> canHit(attacker, e));
    }

    public static List<LivingEntity> targetsInRadius(ServerWorld world, PlayerEntity attacker, Vec3d center, double radius) {
        Box box = new Box(center.subtract(radius, radius, radius), center.add(radius, radius, radius));
        double r2 = radius * radius;
        return world.getEntitiesByClass(LivingEntity.class, box,
                e -> canHit(attacker, e) && e.getPos().squaredDistanceTo(center) <= r2);
    }

    /** Distance from a point to the segment a-b. */
    public static double distanceToSegment(Vec3d p, Vec3d a, Vec3d b) {
        Vec3d ab = b.subtract(a);
        double len2 = ab.lengthSquared();
        if (len2 < 1.0E-6) {
            return p.distanceTo(a);
        }
        double t = Math.max(0, Math.min(1, p.subtract(a).dotProduct(ab) / len2));
        return p.distanceTo(a.add(ab.multiply(t)));
    }
}
