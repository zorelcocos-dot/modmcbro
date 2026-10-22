package com.arcaneforge.registry;

import com.arcaneforge.ArcaneForge;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/** Custom particle types. Client factories are registered in ArcaneForgeClient. */
public final class ModParticles {
    /** Bright, gravity-affected emissive spark thrown off hammer strikes. */
    public static final SimpleParticleType FORGE_SPARK = register("forge_spark", FabricParticleTypes.simple(true));
    /** Billowing steam from quenching and cooling molds. */
    public static final SimpleParticleType STEAM_PUFF = register("steam_puff", FabricParticleTypes.simple());
    /** Floating arcane glyph used by the Rune Altar ritual. */
    public static final SimpleParticleType RUNE_GLYPH = register("rune_glyph", FabricParticleTypes.simple(true));

    private ModParticles() {
    }

    private static SimpleParticleType register(String name, SimpleParticleType type) {
        return Registry.register(Registries.PARTICLE_TYPE, ArcaneForge.id(name), type);
    }

    public static void init() {
        ArcaneForge.LOGGER.debug("Registered Arcane Forge particles");
    }
}
