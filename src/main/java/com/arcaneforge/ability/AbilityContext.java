package com.arcaneforge.ability;

import com.arcaneforge.config.ArcaneForgeConfig;
import com.arcaneforge.forge.MetalType;
import com.arcaneforge.forge.Rune;
import com.arcaneforge.forge.WeaponData;
import com.arcaneforge.forge.WeaponType;
import net.minecraft.item.ItemStack;

/**
 * Snapshot of everything an ability needs: scaled damage/range, cooldown and whether the socketed rune
 * resonates with the weapon (matches its signature rune) for an empowered effect.
 */
public record AbilityContext(WeaponType type, MetalType metal, Rune rune, int stars, boolean resonance,
                             double damage, double range, int cooldownTicks, double attackDamage, double lifesteal) {

    public static final double RESONANCE_MULTIPLIER = 1.5;

    public static AbilityContext of(ItemStack stack, WeaponType type) {
        ArcaneForgeConfig.WeaponSettings settings = type.settings();
        MetalType metal = WeaponData.metal(stack);
        Rune rune = WeaponData.rune(stack);
        if (rune == null) {
            rune = type.signatureRune();
        }
        int stars = Math.max(1, WeaponData.quality(stack));
        boolean resonance = rune == type.signatureRune();
        double quality = WeaponData.qualityMultiplier(stars);
        double metalMult = metal.settings().damageMultiplier;
        double damage = settings.abilityDamage * quality * metalMult * (resonance ? RESONANCE_MULTIPLIER : 1.0);
        double range = settings.abilityRange * (resonance ? 1.15 : 1.0);
        double attack = settings.attackDamage * quality * metalMult;
        return new AbilityContext(type, metal, rune, stars, resonance, damage, range, settings.cooldownTicks, attack,
                settings.lifesteal);
    }
}
