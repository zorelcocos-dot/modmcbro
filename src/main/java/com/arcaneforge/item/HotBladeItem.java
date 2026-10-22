package com.arcaneforge.item;

import com.arcaneforge.forge.WeaponData;
import com.arcaneforge.util.TextUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.List;

/** A glowing blade: an unworked blank (quality 0) or a hot forged blade awaiting quenching (quality 1-5). */
public class HotBladeItem extends Item {
    private static final int GLOW = 0xFF9A3C;

    public HotBladeItem(Settings settings) {
        super(settings);
    }

    @Override
    public Text getName(ItemStack stack) {
        String key = WeaponData.quality(stack) > 0 ? "item.arcaneforge.hot_blade.forged" : "item.arcaneforge.hot_blade.blank";
        return Text.translatable(key, WeaponData.type(stack).displayName()).styled(s -> s.withColor(TextColor.fromRgb(GLOW)));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.arcaneforge.metal", WeaponData.metal(stack).displayName()).formatted(Formatting.GRAY));
        int quality = WeaponData.quality(stack);
        if (quality > 0) {
            tooltip.add(Text.translatable("tooltip.arcaneforge.quality", Text.literal(TextUtil.stars(quality)).formatted(Formatting.GOLD))
                    .formatted(Formatting.GRAY));
            tooltip.add(Text.translatable("tooltip.arcaneforge.needs_quench").formatted(Formatting.DARK_AQUA));
        } else {
            tooltip.add(Text.translatable("tooltip.arcaneforge.unworked").formatted(Formatting.GOLD));
        }
    }
}
