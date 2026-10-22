package com.arcaneforge.registry;

import com.arcaneforge.ArcaneForge;
import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import java.util.function.UnaryOperator;

/** Custom item data components carried by blades and forged weapons. */
public final class ModComponents {
    /** Weapon form id, see {@link com.arcaneforge.forge.WeaponType}. */
    public static final ComponentType<String> WEAPON_TYPE = register("weapon_type",
            b -> b.codec(Codec.STRING).packetCodec(PacketCodecs.STRING));
    /** Metal id, see {@link com.arcaneforge.forge.MetalType}. */
    public static final ComponentType<String> METAL = register("metal",
            b -> b.codec(Codec.STRING).packetCodec(PacketCodecs.STRING));
    /** Rune id, see {@link com.arcaneforge.forge.Rune}. */
    public static final ComponentType<String> RUNE = register("rune",
            b -> b.codec(Codec.STRING).packetCodec(PacketCodecs.STRING));
    /** Forge quality in stars: 0 = unworked, 1-5 = forged. */
    public static final ComponentType<Integer> QUALITY = register("quality",
            b -> b.codec(Codec.intRange(0, 5)).packetCodec(PacketCodecs.VAR_INT));
    /** Whether the blade was quenched in oil. */
    public static final ComponentType<Boolean> TEMPERED = register("tempered",
            b -> b.codec(Codec.BOOL).packetCodec(PacketCodecs.BOOL));
    /** Config revision the weapon's attribute modifiers were last computed with. */
    public static final ComponentType<Integer> STATS_REVISION = register("stats_revision",
            b -> b.codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT));

    private ModComponents() {
    }

    private static <T> ComponentType<T> register(String name, UnaryOperator<ComponentType.Builder<T>> builder) {
        return Registry.register(Registries.DATA_COMPONENT_TYPE, ArcaneForge.id(name), builder.apply(ComponentType.builder()).build());
    }

    public static void init() {
        ArcaneForge.LOGGER.debug("Registered Arcane Forge data components");
    }
}
