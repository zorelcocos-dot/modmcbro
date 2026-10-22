package com.arcaneforge.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** A crafting component that explains its purpose in the tooltip (hilt, pommel, quenching oil). */
public class InfoItem extends Item {
    private final String tooltipKey;

    public InfoItem(Settings settings, String tooltipKey) {
        super(settings);
        this.tooltipKey = tooltipKey;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable(tooltipKey).formatted(Formatting.GRAY));
    }
}
