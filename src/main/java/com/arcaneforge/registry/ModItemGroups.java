package com.arcaneforge.registry;

import com.arcaneforge.ArcaneForge;
import com.arcaneforge.forge.MetalType;
import com.arcaneforge.forge.WeaponFactory;
import com.arcaneforge.forge.WeaponType;
import com.arcaneforge.item.ModularWeaponItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

public final class ModItemGroups {
    public static final ItemGroup MAIN = Registry.register(Registries.ITEM_GROUP, ArcaneForge.id("main"),
            FabricItemGroup.builder()
                    .icon(() -> new ItemStack(ModItems.FORGE_ANVIL))
                    .displayName(Text.translatable("itemGroup.arcaneforge.main"))
                    .entries((context, entries) -> {
                        for (Item item : ModItems.all()) {
                            if (item == ModItems.HOT_BLADE || item == ModItems.COOLED_BLADE || item instanceof ModularWeaponItem) {
                                continue;
                            }
                            entries.add(item);
                        }
                        for (WeaponType type : WeaponType.values()) {
                            entries.add(WeaponFactory.hotBlank(type, MetalType.IRON));
                        }
                        for (WeaponType type : WeaponType.values()) {
                            entries.add(WeaponFactory.createWeapon(type, MetalType.IRON, 3, type.signatureRune(), false));
                        }
                        for (WeaponType type : WeaponType.values()) {
                            entries.add(WeaponFactory.createWeapon(type, MetalType.NETHERITE, 5, type.signatureRune(), true));
                        }
                    })
                    .build());

    private ModItemGroups() {
    }

    public static void init() {
        ArcaneForge.LOGGER.debug("Registered Arcane Forge item group");
    }
}
