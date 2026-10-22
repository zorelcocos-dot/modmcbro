package com.arcaneforge.forge;

import com.arcaneforge.registry.ModComponents;
import net.minecraft.item.ItemStack;

/** Typed accessors for the Arcane Forge data components on an {@link ItemStack}. */
public final class WeaponData {
    private WeaponData() {
    }

    public static WeaponType type(ItemStack stack) {
        return WeaponType.byIdOrDefault(stack.get(ModComponents.WEAPON_TYPE));
    }

    public static MetalType metal(ItemStack stack) {
        return MetalType.byIdOrDefault(stack.get(ModComponents.METAL));
    }

    public static Rune rune(ItemStack stack) {
        return Rune.byId(stack.get(ModComponents.RUNE));
    }

    public static int quality(ItemStack stack) {
        Integer q = stack.get(ModComponents.QUALITY);
        return q == null ? 0 : Math.max(0, Math.min(5, q));
    }

    public static boolean tempered(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(ModComponents.TEMPERED));
    }

    public static void setType(ItemStack stack, WeaponType type) {
        stack.set(ModComponents.WEAPON_TYPE, type.id());
    }

    public static void setMetal(ItemStack stack, MetalType metal) {
        stack.set(ModComponents.METAL, metal.id());
    }

    public static void setRune(ItemStack stack, Rune rune) {
        stack.set(ModComponents.RUNE, rune.id());
    }

    public static void setQuality(ItemStack stack, int stars) {
        stack.set(ModComponents.QUALITY, Math.max(0, Math.min(5, stars)));
    }

    public static void setTempered(ItemStack stack, boolean tempered) {
        stack.set(ModComponents.TEMPERED, tempered);
    }

    /** Damage/ability multiplier from forge quality: 1 star = 0.9x, 5 stars = 1.3x. */
    public static double qualityMultiplier(int stars) {
        return 0.8 + 0.1 * Math.max(1, Math.min(5, stars));
    }
}
