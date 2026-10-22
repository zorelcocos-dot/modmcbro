package com.arcaneforge.registry;

import com.arcaneforge.ArcaneForge;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** Sound events. Definitions live in assets/arcaneforge/sounds.json and can be replaced by resource packs. */
public final class ModSounds {
    public static final SoundEvent HAMMER_STRIKE = register("forge.hammer_strike");
    public static final SoundEvent HAMMER_PERFECT = register("forge.hammer_perfect");
    public static final SoundEvent HAMMER_MISS = register("forge.hammer_miss");
    public static final SoundEvent SMELTERY_INSERT = register("smeltery.insert");
    public static final SoundEvent SMELTERY_BELLOWS = register("smeltery.bellows");
    public static final SoundEvent SMELTERY_POUR = register("smeltery.pour");
    public static final SoundEvent QUENCH_HISS = register("quench.hiss");
    public static final SoundEvent ALTAR_CHARGE = register("altar.charge");
    public static final SoundEvent ALTAR_AWAKEN = register("altar.awaken");
    public static final SoundEvent FIRE_CRESCENT = register("weapon.fire_crescent");
    public static final SoundEvent VOID_DASH = register("weapon.void_dash");
    public static final SoundEvent THUNDER_SLAM = register("weapon.thunder_slam");
    public static final SoundEvent FROST_VORTEX = register("weapon.frost_vortex");
    public static final SoundEvent BLOOD_DRAIN = register("weapon.blood_drain");

    private ModSounds() {
    }

    private static SoundEvent register(String name) {
        Identifier id = ArcaneForge.id(name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void init() {
        ArcaneForge.LOGGER.debug("Registered Arcane Forge sounds");
    }
}
