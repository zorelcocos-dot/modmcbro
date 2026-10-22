package com.arcaneforge.item;

import com.arcaneforge.forge.Rune;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.List;

/** An elemental rune socketed at the Rune Awakening Altar. */
public class RuneItem extends Item {
    private final Rune rune;

    public RuneItem(Settings settings, Rune rune) {
        super(settings);
        this.rune = rune;
    }

    public Rune getRune() {
        return rune;
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("tooltip.arcaneforge.rune_effect." + rune.id())
                .styled(s -> s.withColor(TextColor.fromRgb(rune.color()))));
        tooltip.add(Text.translatable("tooltip.arcaneforge.rune_item").formatted(Formatting.GRAY));
    }
}
