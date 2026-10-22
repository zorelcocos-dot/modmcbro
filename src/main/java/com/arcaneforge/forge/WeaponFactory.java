package com.arcaneforge.forge;

import com.arcaneforge.item.ModularWeaponItem;
import com.arcaneforge.registry.ModItems;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.random.Random;

/** Creates every intermediate and final item of the forging pipeline. */
public final class WeaponFactory {
    private WeaponFactory() {
    }

    /** A freshly cast, glowing, unworked blank straight out of the mold. */
    public static ItemStack hotBlank(WeaponType type, MetalType metal) {
        ItemStack stack = new ItemStack(ModItems.HOT_BLADE);
        WeaponData.setType(stack, type);
        WeaponData.setMetal(stack, metal);
        WeaponData.setQuality(stack, 0);
        return stack;
    }

    /** Result of quenching a hot forged blade; may crack (water) or temper (oil). */
    public static QuenchResult quench(ItemStack hot, boolean oil, double crackChance, Random random) {
        ItemStack cooled = new ItemStack(ModItems.COOLED_BLADE);
        WeaponData.setType(cooled, WeaponData.type(hot));
        WeaponData.setMetal(cooled, WeaponData.metal(hot));
        int stars = Math.max(1, WeaponData.quality(hot));
        boolean cracked = false;
        if (!oil && stars > 1 && random.nextDouble() < crackChance) {
            stars--;
            cracked = true;
        }
        WeaponData.setQuality(cooled, stars);
        WeaponData.setTempered(cooled, oil);
        return new QuenchResult(cooled, cracked);
    }

    /** Fuses a cooled blade with a rune into the final weapon (hilt and pommel are consumed by the altar). */
    public static ItemStack awaken(ItemStack blade, Rune rune) {
        return createWeapon(WeaponData.type(blade), WeaponData.metal(blade), Math.max(1, WeaponData.quality(blade)),
                rune, WeaponData.tempered(blade));
    }

    public static ItemStack createWeapon(WeaponType type, MetalType metal, int stars, Rune rune, boolean tempered) {
        ItemStack stack = new ItemStack(type.weaponItem());
        WeaponData.setType(stack, type);
        WeaponData.setMetal(stack, metal);
        WeaponData.setQuality(stack, Math.max(1, Math.min(5, stars)));
        WeaponData.setRune(stack, rune != null ? rune : type.signatureRune());
        WeaponData.setTempered(stack, tempered);
        ModularWeaponItem.applyStats(stack);
        return stack;
    }

    public record QuenchResult(ItemStack stack, boolean cracked) {
    }
}
