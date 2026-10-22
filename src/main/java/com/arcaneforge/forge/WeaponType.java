package com.arcaneforge.forge;

import com.arcaneforge.config.ArcaneForgeConfig;
import com.arcaneforge.config.ConfigManager;
import com.arcaneforge.registry.ModItems;
import net.minecraft.item.Item;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.Locale;

/** The five weapon forms. Each has its own mold size, signature rune and flagship item. */
public enum WeaponType {
    GREATSWORD("greatsword", 4, Rune.FIRE),
    KATANA("katana", 3, Rune.SHADOW),
    WARHAMMER("warhammer", 5, Rune.STORM),
    WARSCYTHE("warscythe", 4, Rune.ICE),
    DAGGERS("daggers", 2, Rune.BLOOD);

    private final String id;
    private final int moldUnits;
    private final Rune signatureRune;

    WeaponType(String id, int moldUnits, Rune signatureRune) {
        this.id = id;
        this.moldUnits = moldUnits;
        this.signatureRune = signatureRune;
    }

    public String id() {
        return id;
    }

    /** Units of molten metal needed to fill this mold. */
    public int moldUnits() {
        return moldUnits;
    }

    public Rune signatureRune() {
        return signatureRune;
    }

    public Item weaponItem() {
        return switch (this) {
            case GREATSWORD -> ModItems.SOLARIS_GREATSWORD;
            case KATANA -> ModItems.VOIDSTEP_KATANA;
            case WARHAMMER -> ModItems.THUNDERLORD_WARHAMMER;
            case WARSCYTHE -> ModItems.FROSTBITE_WARSCYTHE;
            case DAGGERS -> ModItems.BLOODMOON_DAGGERS;
        };
    }

    public MutableText displayName() {
        return Text.translatable("arcaneforge.weapon_type." + id);
    }

    public ArcaneForgeConfig.WeaponSettings settings() {
        ArcaneForgeConfig.WeaponSettings s = ConfigManager.get().weapons.get(id);
        return s != null ? s : new ArcaneForgeConfig.WeaponSettings();
    }

    public WeaponType next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public WeaponType previous() {
        return values()[(ordinal() + values().length - 1) % values().length];
    }

    /** Accepts both the form id ("katana") and the flagship item path ("voidstep_katana"). */
    public static WeaponType byId(String id) {
        if (id != null) {
            String key = id.toLowerCase(Locale.ROOT);
            if (key.contains(":")) {
                key = key.substring(key.indexOf(':') + 1);
            }
            for (WeaponType t : values()) {
                if (t.id.equals(key) || key.endsWith("_" + t.id)) {
                    return t;
                }
            }
        }
        return null;
    }

    public static WeaponType byIdOrDefault(String id) {
        WeaponType t = byId(id);
        return t != null ? t : GREATSWORD;
    }
}
