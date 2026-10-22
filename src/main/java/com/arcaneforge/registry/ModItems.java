package com.arcaneforge.registry;

import com.arcaneforge.ArcaneForge;
import com.arcaneforge.forge.Rune;
import com.arcaneforge.forge.WeaponType;
import com.arcaneforge.item.BloodmoonDaggersItem;
import com.arcaneforge.item.CooledBladeItem;
import com.arcaneforge.item.ForgingHammerItem;
import com.arcaneforge.item.FrostbiteWarscytheItem;
import com.arcaneforge.item.HotBladeItem;
import com.arcaneforge.item.InfoItem;
import com.arcaneforge.item.RuneItem;
import com.arcaneforge.item.SolarisGreatswordItem;
import com.arcaneforge.item.ThunderlordWarhammerItem;
import com.arcaneforge.item.VoidstepKatanaItem;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Rarity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModItems {
    private static final List<Item> ALL = new ArrayList<>();

    public static final Item SMELTERY = block(ModBlocks.SMELTERY);
    public static final Item MOLD_TABLE = block(ModBlocks.MOLD_TABLE);
    public static final Item FORGE_ANVIL = block(ModBlocks.FORGE_ANVIL);
    public static final Item QUENCHING_BARREL = block(ModBlocks.QUENCHING_BARREL);
    public static final Item RUNE_ALTAR = block(ModBlocks.RUNE_ALTAR);

    public static final Item FORGING_HAMMER = register("forging_hammer",
            new ForgingHammerItem(new Item.Settings().maxDamage(750).rarity(Rarity.UNCOMMON)));
    public static final Item HOT_BLADE = register("hot_blade",
            new HotBladeItem(new Item.Settings().maxCount(1).fireproof().rarity(Rarity.UNCOMMON)));
    public static final Item COOLED_BLADE = register("cooled_blade",
            new CooledBladeItem(new Item.Settings().maxCount(1).rarity(Rarity.UNCOMMON)));
    public static final Item HILT = register("hilt",
            new InfoItem(new Item.Settings().maxCount(16), "tooltip.arcaneforge.ready_for_altar"));
    public static final Item POMMEL = register("pommel",
            new InfoItem(new Item.Settings().maxCount(16), "tooltip.arcaneforge.ready_for_altar"));
    public static final Item QUENCHING_OIL = register("quenching_oil",
            new InfoItem(new Item.Settings().maxCount(16), "tooltip.arcaneforge.oil"));

    public static final Item FIRE_RUNE = rune(Rune.FIRE);
    public static final Item SHADOW_RUNE = rune(Rune.SHADOW);
    public static final Item STORM_RUNE = rune(Rune.STORM);
    public static final Item ICE_RUNE = rune(Rune.ICE);
    public static final Item BLOOD_RUNE = rune(Rune.BLOOD);

    public static final Item SOLARIS_GREATSWORD = register("solaris_greatsword",
            new SolarisGreatswordItem(weaponSettings(), WeaponType.GREATSWORD, "solaris_greatsword"));
    public static final Item VOIDSTEP_KATANA = register("voidstep_katana",
            new VoidstepKatanaItem(weaponSettings(), WeaponType.KATANA, "voidstep_katana"));
    public static final Item THUNDERLORD_WARHAMMER = register("thunderlord_warhammer",
            new ThunderlordWarhammerItem(weaponSettings(), WeaponType.WARHAMMER, "thunderlord_warhammer"));
    public static final Item FROSTBITE_WARSCYTHE = register("frostbite_warscythe",
            new FrostbiteWarscytheItem(weaponSettings(), WeaponType.WARSCYTHE, "frostbite_warscythe"));
    public static final Item BLOODMOON_DAGGERS = register("bloodmoon_daggers",
            new BloodmoonDaggersItem(weaponSettings(), WeaponType.DAGGERS, "bloodmoon_daggers"));

    private ModItems() {
    }

    private static Item.Settings weaponSettings() {
        return new Item.Settings().rarity(Rarity.EPIC).fireproof();
    }

    private static Item rune(Rune rune) {
        return register(rune.id() + "_rune", new RuneItem(new Item.Settings().maxCount(16).rarity(Rarity.RARE), rune));
    }

    private static Item block(Block block) {
        return register(Registries.BLOCK.getId(block).getPath(), new BlockItem(block, new Item.Settings().rarity(Rarity.UNCOMMON)));
    }

    private static Item register(String name, Item item) {
        Item registered = Registry.register(Registries.ITEM, ArcaneForge.id(name), item);
        ALL.add(registered);
        return registered;
    }

    /** Every Arcane Forge item in creative-tab order. */
    public static List<Item> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static void init() {
        ArcaneForge.LOGGER.debug("Registered {} Arcane Forge items", ALL.size());
    }
}
