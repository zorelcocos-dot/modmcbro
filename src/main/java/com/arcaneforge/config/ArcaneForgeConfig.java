package com.arcaneforge.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Root of {@code config/arcaneforge.json}. Every field is public so Gson can (de)serialize it directly;
 * defaults are applied by field initializers and missing map entries are merged back in on load.
 */
public final class ArcaneForgeConfig {
    public String _comment = "Arcane Forge configuration. Colors in messages support &0-&f, &k-&o, &r and hex as &#RRGGBB. Use /arcaneforge reload to apply changes live.";

    public General general = new General();
    public Forging forging = new Forging();
    public Particles particles = new Particles();
    public LinkedHashMap<String, MetalSettings> metals = defaultMetals();
    public LinkedHashMap<String, WeaponSettings> weapons = defaultWeapons();
    public LinkedHashMap<String, String> messages = defaultMessages();

    public static final class General {
        /** When true, lightning from abilities and runes is purely visual (no fire, no block damage). */
        public boolean cosmeticLightning = true;
        /** Allow weapon abilities to hurt other players (still respects team/PvP rules). */
        public boolean abilitiesAffectPlayers = true;
        /** Client-side: draw floating hologram text above stations. */
        public boolean showHolograms = true;
        /** Client-side: allow camera shake from heavy abilities. */
        public boolean screenShake = true;
        /** Distance (blocks) at which station holograms become visible. */
        public double hologramRange = 6.0;
    }

    public static final class Forging {
        public int smelteryCapacity = 18;
        public int meltTicksPerUnit = 40;
        public int maxFuelTicks = 20 * 60 * 10;
        public int pourTicksPerUnit = 8;
        public int moldCoolTicks = 80;
        /** Duration in ticks of one shrinking-ring cycle of the hammer minigame. Lower is harder. */
        public int beatPeriodTicks = 30;
        /** Accepted distance between ring and target (0-1 normalized radius) for each rating. */
        public double perfectWindow = 0.06;
        public double goodWindow = 0.13;
        public double okWindow = 0.22;
        public int strikesRequired = 6;
        public int hammerCooldownTicks = 5;
        /** Chance that a water quench cracks the blade (-1 quality, never below 1 star). */
        public double waterCrackChance = 0.10;
        /** Bonus attack damage granted by oil tempering. */
        public double oilTemperBonus = 1.0;
        public int ritualTicks = 80;
    }

    public static final class Particles {
        /** Global multiplier for every particle effect spawned by the mod (0.0 - 3.0). */
        public double densityMultiplier = 1.0;
        /** Hard cap on particles in a single burst call. */
        public int maxParticlesPerBurst = 400;
        public boolean ambientStationParticles = true;
    }

    public static final class MetalSettings {
        public double damageMultiplier = 1.0;
        public double durabilityMultiplier = 1.0;

        public MetalSettings() {
        }

        public MetalSettings(double damageMultiplier, double durabilityMultiplier) {
            this.damageMultiplier = damageMultiplier;
            this.durabilityMultiplier = durabilityMultiplier;
        }
    }

    public static final class WeaponSettings {
        public double attackDamage = 8.0;
        /** Added to the player's base attack speed of 4.0 (e.g. -2.4 gives 1.6 attacks/second). */
        public double attackSpeedModifier = -2.4;
        public int durability = 1600;
        public double abilityDamage = 10.0;
        public double abilityRange = 6.0;
        public int cooldownTicks = 140;
        /** Fraction of attack damage returned as health on hit (used by the Bloodmoon Daggers). */
        public double lifesteal = 0.0;

        public WeaponSettings() {
        }

        public WeaponSettings(double attackDamage, double attackSpeedModifier, int durability, double abilityDamage,
                              double abilityRange, int cooldownTicks, double lifesteal) {
            this.attackDamage = attackDamage;
            this.attackSpeedModifier = attackSpeedModifier;
            this.durability = durability;
            this.abilityDamage = abilityDamage;
            this.abilityRange = abilityRange;
            this.cooldownTicks = cooldownTicks;
            this.lifesteal = lifesteal;
        }
    }

    public static LinkedHashMap<String, MetalSettings> defaultMetals() {
        LinkedHashMap<String, MetalSettings> map = new LinkedHashMap<>();
        map.put("iron", new MetalSettings(1.0, 1.0));
        map.put("gold", new MetalSettings(0.9, 0.7));
        map.put("copper", new MetalSettings(0.85, 0.85));
        map.put("netherite", new MetalSettings(1.3, 1.6));
        return map;
    }

    public static LinkedHashMap<String, WeaponSettings> defaultWeapons() {
        LinkedHashMap<String, WeaponSettings> map = new LinkedHashMap<>();
        map.put("greatsword", new WeaponSettings(10.0, -3.0, 1800, 12.0, 7.0, 140, 0.0));
        map.put("katana", new WeaponSettings(8.0, -2.2, 1600, 10.0, 9.0, 100, 0.0));
        map.put("warhammer", new WeaponSettings(12.0, -3.3, 2200, 14.0, 6.0, 200, 0.0));
        map.put("warscythe", new WeaponSettings(9.0, -2.8, 1800, 6.0, 9.0, 160, 0.0));
        map.put("daggers", new WeaponSettings(6.0, -1.6, 1400, 4.0, 0.0, 240, 0.15));
        return map;
    }

    public static LinkedHashMap<String, String> defaultMessages() {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("prefix", "&#FF8A00&lArcane Forge &8» ");
        m.put("reload_success", "&aConfiguration reloaded in &f{ms}ms&a.");
        m.put("reload_failed", "&cConfig reload failed: &f{error}");
        m.put("give_success", "&fGave &#FFD36E{weapon} &f(&6{stars}&f) to &e{player}&f.");
        m.put("give_unknown_weapon", "&cUnknown weapon '&f{weapon}&c'. Try: greatsword, katana, warhammer, warscythe, daggers.");
        m.put("give_unknown_rune", "&cUnknown rune '&f{rune}&c'. Try: fire, shadow, storm, ice, blood.");
        m.put("give_unknown_metal", "&cUnknown metal '&f{metal}&c'. Try: iron, gold, copper, netherite.");
        m.put("station_spawned", "&fForge station assembled. &7Starter kit added to your inventory - happy smithing!");
        m.put("station_blocked", "&cNot enough room in front of you to build the station.");
        m.put("smeltery_inserted", "&7Added &f{item} &8→ &6{queued} &7units melting");
        m.put("smeltery_full", "&cThe smeltery cannot hold any more metal.");
        m.put("smeltery_mismatch", "&cThe smeltery already holds &f{metal}&c. Pour it out first.");
        m.put("smeltery_fuel", "&6Fuel added &7({seconds}s of burn time)");
        m.put("smeltery_fuel_full", "&cThe firebox is already packed with fuel.");
        m.put("smeltery_no_mold", "&cPlace a Casting Mold Table next to the smeltery to pour into.");
        m.put("smeltery_empty", "&cThere is no molten metal to pour.");
        m.put("smeltery_pour_start", "&6You pull the lever - molten {metal} pours into the mold.");
        m.put("smeltery_pour_stop", "&7You close the spout.");
        m.put("mold_cycled", "&7Mold set to &f{type} &7(needs &6{units} &7units)");
        m.put("mold_busy", "&cThe mold is still filled with metal.");
        m.put("mold_taken", "&6You pry a glowing {type} blank from the mold. &7Take it to the anvil!");
        m.put("anvil_placed", "&6The blank glows on the anvil. &7Strike with a Forging Hammer when the ring meets the target!");
        m.put("anvil_need_blank", "&cOnly a glowing, unworked blank can be forged here.");
        m.put("anvil_occupied", "&cThe anvil already holds a blade.");
        m.put("anvil_need_hammer", "&eStrike the blade with a Forging Hammer!");
        m.put("anvil_empty", "&7Place a glowing blank on the anvil first.");
        m.put("anvil_rating", "{rating} &8| &7Strike &f{strike}&7/&f{total}");
        m.put("anvil_complete", "&6&lForging complete! &fQuality: &6{stars}");
        m.put("anvil_take_first", "&eTake the finished blade before forging another.");
        m.put("barrel_filled_water", "&bThe barrel fills with cold water.");
        m.put("barrel_filled_oil", "&6Quenching oil added &7({level}/{max})");
        m.put("barrel_full", "&cThe barrel is already full.");
        m.put("barrel_empty", "&cThe barrel is empty! Fill it with water or quenching oil.");
        m.put("barrel_wrong_fluid", "&cYou can't mix water and oil.");
        m.put("quench_water", "&bThe blade hardens with a violent hiss!");
        m.put("quench_crack", "&cThe rapid water quench cracked the blade! &7(-1 quality)");
        m.put("quench_oil", "&6The oil-tempered blade gains resilience! &7(+{bonus} damage)");
        m.put("quench_need_forged", "&cThis blank has not been forged yet - work it on an anvil first.");
        m.put("altar_socketed", "&dSocketed &f{item}&d.");
        m.put("altar_slot_full", "&cThat socket is already filled.");
        m.put("altar_missing", "&cThe altar still needs: &f{missing}");
        m.put("altar_start", "&dThe runes begin to awaken...");
        m.put("altar_complete", "&d&lAWAKENED! &fYour &#FFD36E{weapon} &fis ready.");
        m.put("altar_busy", "&cThe ritual is in progress.");
        m.put("altar_removed", "&7Removed &f{item}&7 from the altar.");
        m.put("frenzy_too_weak", "&cYou are too weak to fuel the Crimson Frenzy!");
        m.put("ability_blocked", "&cThere is no room to perform that technique.");
        m.put("rating_perfect", "&#FFD700&lPERFECT!");
        m.put("rating_good", "&a&lGOOD");
        m.put("rating_ok", "&b&lOK");
        m.put("rating_miss", "&c&lMISS");
        return m;
    }

    /** Re-adds missing defaults (e.g. after an update added new keys) and clamps unsafe values. */
    public void sanitize() {
        if (general == null) general = new General();
        if (forging == null) forging = new Forging();
        if (particles == null) particles = new Particles();
        if (metals == null) metals = new LinkedHashMap<>();
        if (weapons == null) weapons = new LinkedHashMap<>();
        if (messages == null) messages = new LinkedHashMap<>();
        for (Map.Entry<String, MetalSettings> e : defaultMetals().entrySet()) metals.putIfAbsent(e.getKey(), e.getValue());
        for (Map.Entry<String, WeaponSettings> e : defaultWeapons().entrySet()) weapons.putIfAbsent(e.getKey(), e.getValue());
        for (Map.Entry<String, String> e : defaultMessages().entrySet()) messages.putIfAbsent(e.getKey(), e.getValue());

        forging.smelteryCapacity = Math.max(1, forging.smelteryCapacity);
        forging.meltTicksPerUnit = Math.max(1, forging.meltTicksPerUnit);
        forging.maxFuelTicks = Math.max(20, forging.maxFuelTicks);
        forging.pourTicksPerUnit = Math.max(1, forging.pourTicksPerUnit);
        forging.moldCoolTicks = Math.max(1, forging.moldCoolTicks);
        forging.beatPeriodTicks = Math.max(8, forging.beatPeriodTicks);
        forging.strikesRequired = Math.max(1, forging.strikesRequired);
        forging.hammerCooldownTicks = Math.max(0, forging.hammerCooldownTicks);
        forging.ritualTicks = Math.max(10, forging.ritualTicks);
        forging.perfectWindow = clamp(forging.perfectWindow, 0.005, 1.0);
        forging.goodWindow = clamp(forging.goodWindow, forging.perfectWindow, 1.0);
        forging.okWindow = clamp(forging.okWindow, forging.goodWindow, 1.0);
        forging.waterCrackChance = clamp(forging.waterCrackChance, 0.0, 1.0);
        particles.densityMultiplier = clamp(particles.densityMultiplier, 0.0, 3.0);
        particles.maxParticlesPerBurst = Math.max(1, particles.maxParticlesPerBurst);
        general.hologramRange = clamp(general.hologramRange, 0.0, 64.0);
        for (WeaponSettings w : weapons.values()) {
            w.durability = Math.max(1, w.durability);
            w.cooldownTicks = Math.max(0, w.cooldownTicks);
            w.lifesteal = clamp(w.lifesteal, 0.0, 1.0);
            w.attackDamage = Math.max(1.0, w.attackDamage);
        }
        for (MetalSettings m : metals.values()) {
            m.damageMultiplier = Math.max(0.05, m.damageMultiplier);
            m.durabilityMultiplier = Math.max(0.05, m.durabilityMultiplier);
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
