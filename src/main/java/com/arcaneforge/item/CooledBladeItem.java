package com.arcaneforge.item;

import com.arcaneforge.config.ConfigManager;
import com.arcaneforge.forge.WeaponData;
import com.arcaneforge.util.TextUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** A quenched, hardened blade ready to be awakened at the Rune Altar. */
public class CooledBladeItem extends Item {
    public CooledBladeItem(Settings settings) {
        super(settings);
    }

    @Override
    public Text getName(ItemStack stack) {
        return Text.translatable("item.arcaneforge.cooled_blade.typed", WeaponData.metal(stack).displayName(),
                WeaponData.type(stack).displayName());
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.arcaneforge.quality",
                Text.literal(TextUtil.stars(Math.max(1, WeaponData.quality(stack)))).formatted(Formatting.GOLD)).formatted(Formatting.GRAY));
        if (WeaponData.tempered(stack)) {
            tooltip.add(Text.translatable("tooltip.arcaneforge.tempered", ConfigManager.get().forging.oilTemperBonus)
                    .formatted(Formatting.GOLD));
        }
        tooltip.add(Text.translatable("tooltip.arcaneforge.ready_for_altar").formatted(Formatting.LIGHT_PURPLE));
    }
}
