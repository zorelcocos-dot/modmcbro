package com.arcaneforge.forge;

import com.arcaneforge.registry.ModItems;
import net.minecraft.item.Item;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.Locale;

/** Arcane runes socketed at the altar. Each grants an on-hit effect and empowers its matching weapon. */
public enum Rune {
    FIRE("fire", 0xFF7A1A),
    SHADOW("shadow", 0xA040FF),
    STORM("storm", 0x58B8FF),
    ICE("ice", 0x9EE8FF),
    BLOOD("blood", 0xE01030);

    private final String id;
    private final int color;

    Rune(String id, int color) {
        this.id = id;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public int color() {
        return color;
    }

    public Item item() {
        return switch (this) {
            case FIRE -> ModItems.FIRE_RUNE;
            case SHADOW -> ModItems.SHADOW_RUNE;
            case STORM -> ModItems.STORM_RUNE;
            case ICE -> ModItems.ICE_RUNE;
            case BLOOD -> ModItems.BLOOD_RUNE;
        };
    }

    public MutableText displayName() {
        return Text.translatable("arcaneforge.rune." + id).styled(s -> s.withColor(TextColor.fromRgb(color)));
    }

    public MutableText adjective() {
        return Text.translatable("arcaneforge.rune." + id + ".adjective");
    }

    public static Rune byId(String id) {
        if (id != null) {
            String key = id.toLowerCase(Locale.ROOT);
            for (Rune r : values()) {
                if (r.id.equals(key)) {
                    return r;
                }
            }
        }
        return null;
    }
}
