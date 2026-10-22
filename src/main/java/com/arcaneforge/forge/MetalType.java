package com.arcaneforge.forge;

import com.arcaneforge.config.ArcaneForgeConfig;
import com.arcaneforge.config.ConfigManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Metals that can be melted in the smeltery and forged into blades. */
public enum MetalType {
    IRON("iron", 0xD8DCE4, 0xFF8A2A, Items.IRON_INGOT),
    GOLD("gold", 0xFFD447, 0xFFC23A, Items.GOLD_INGOT),
    COPPER("copper", 0xE5875A, 0xFF6A30, Items.COPPER_INGOT),
    NETHERITE("netherite", 0x6E5A66, 0xD0306A, Items.NETHERITE_INGOT);

    private static final Map<Item, Smeltable> SMELTABLES = new HashMap<>();

    static {
        register(IRON, 1, Items.IRON_INGOT, Items.RAW_IRON);
        register(IRON, 2, Items.IRON_ORE, Items.DEEPSLATE_IRON_ORE);
        register(IRON, 9, Items.IRON_BLOCK, Items.RAW_IRON_BLOCK);
        register(GOLD, 1, Items.GOLD_INGOT, Items.RAW_GOLD, Items.NETHER_GOLD_ORE);
        register(GOLD, 2, Items.GOLD_ORE, Items.DEEPSLATE_GOLD_ORE);
        register(GOLD, 9, Items.GOLD_BLOCK, Items.RAW_GOLD_BLOCK);
        register(COPPER, 1, Items.COPPER_INGOT, Items.RAW_COPPER);
        register(COPPER, 2, Items.COPPER_ORE, Items.DEEPSLATE_COPPER_ORE);
        register(COPPER, 9, Items.COPPER_BLOCK, Items.RAW_COPPER_BLOCK);
        register(NETHERITE, 1, Items.NETHERITE_SCRAP, Items.ANCIENT_DEBRIS);
        register(NETHERITE, 4, Items.NETHERITE_INGOT);
        register(NETHERITE, 36, Items.NETHERITE_BLOCK);
    }

    private final String id;
    private final int metalColor;
    private final int moltenColor;
    private final Item displayItem;

    MetalType(String id, int metalColor, int moltenColor, Item displayItem) {
        this.id = id;
        this.metalColor = metalColor;
        this.moltenColor = moltenColor;
        this.displayItem = displayItem;
    }

    private static void register(MetalType metal, int units, Item... items) {
        for (Item item : items) {
            SMELTABLES.put(item, new Smeltable(metal, units));
        }
    }

    public String id() {
        return id;
    }

    /** Solid metal tint (RGB). */
    public int metalColor() {
        return metalColor;
    }

    /** Glowing molten tint (RGB). */
    public int moltenColor() {
        return moltenColor;
    }

    public Item displayItem() {
        return displayItem;
    }

    public MutableText displayName() {
        return Text.translatable("arcaneforge.metal." + id).styled(s -> s.withColor(TextColor.fromRgb(metalColor)));
    }

    public ArcaneForgeConfig.MetalSettings settings() {
        ArcaneForgeConfig.MetalSettings s = ConfigManager.get().metals.get(id);
        return s != null ? s : new ArcaneForgeConfig.MetalSettings();
    }

    public static MetalType byId(String id) {
        if (id != null) {
            String key = id.toLowerCase(Locale.ROOT);
            for (MetalType m : values()) {
                if (m.id.equals(key)) {
                    return m;
                }
            }
        }
        return null;
    }

    public static MetalType byIdOrDefault(String id) {
        MetalType m = byId(id);
        return m != null ? m : IRON;
    }

    public static Smeltable smeltable(ItemStack stack) {
        return stack.isEmpty() ? null : SMELTABLES.get(stack.getItem());
    }

    public record Smeltable(MetalType metal, int units) {
    }
}
