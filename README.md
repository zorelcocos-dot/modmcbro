# Arcane Forge: 3D Physical Blacksmithing & Modular Weapons

A Fabric 1.21.1 mod (GeckoLib 4) where every step of weapon crafting happens physically in the world.
There are no container GUIs: you right-click animated 3D stations and watch floating items, molten metal and holograms.

## Requirements

| | Version |
|---|---|
| Minecraft | 1.21.1 |
| Fabric Loader | 0.16.5+ |
| Fabric API | 0.102.0+1.21.1 or newer |
| GeckoLib (Fabric) | 4.5.8+ for 1.21.1 |
| Java | 21 |

GeckoLib is required on **both** the server and the client.

## The forging pipeline

1. **Smeltery Furnace.** Right-click with ores, raw metal, ingots or storage blocks to queue metal (one metal type at a time).
   Ores melt into 2 units each, and netherite ingots into 4.
   Right-click with any furnace fuel (coal, lava bucket and so on) to light the firebox. The bellows pump, flames and smoke
   pour out, and the molten pool rises inside the 3D model.
   Use an **empty hand** to pull the side lever, which pours a stream of molten metal into an adjacent Mold Table.
2. **Casting Mold Table.** Use an empty hand to cycle the mold (Greatsword, Katana, Warhammer, Warscythe or Twin Daggers; sneak to cycle backwards).
   Once filled, the metal steams and solidifies. Use an empty hand again to pry out the **Glowing Blank**.
3. **Forge Anvil.** Place the blank on the anvil, then hold a **Forging Hammer**. A target ring appears and a second ring
   shrinks toward it. Right-click when the rings meet:
   *PERFECT*, *GOOD*, *OK* or *MISS*, each with its own sparks and pitch-shifted anvil sounds.
   The blade visibly stretches with each strike. After the last strike your timing becomes a **1–5 star** quality rating.
4. **Quenching Barrel.** Fill it with a water bucket (4 quenches) or Quenching Oil bottles.
   Plunging the hot blade in releases a steam explosion and hiss.
   Water can crack the blade, which costs one star (the chance is configurable).
   Oil tempers the blade for bonus damage.
5. **Rune Awakening Altar.** Socket a **Blade, Hilt, Pommel and Rune** (any order; sneak with an empty hand to remove the last one).
   An empty-hand right-click starts the ritual: the parts orbit, converge and fire enchanting beams before merging into the final weapon.

## Weapons

The blade's mold shape decides the weapon. The rune decides its element.
A rune that matches the weapon's signature rune gives **Rune Resonance**: +50% skill damage and an empowered effect.

| Weapon | Signature rune | Right-click skill |
|---|---|---|
| Solaris Greatsword | Fire | **Solar Crescent**: an expanding crescent of fire that launches and ignites everything in a cone |
| Voidstep Katana | Shadow | **Voidstep**: a lightning dash with shadow after-images; everything passed through is slashed a moment later |
| Thunderlord Warhammer | Storm | **Thunderfall**: leap and slam, sending out a radial shockwave of shattered ground, lightning strikes, knockback and camera shake |
| Frostbite Warscythe | Ice | **Glacial Vortex**: a spiralling blizzard drags enemies in, then encases them in ice |
| Bloodmoon Daggers | Blood | Passive lifesteal with crimson slash trails. **Crimson Frenzy** trades health for speed, strength and double lifesteal |

Each rune also adds an on-hit effect:

- Fire ignites the target.
- Shadow weakens or blinds the target.
- Storm has a chance to call lightning.
- Ice slows and chills the target.
- Blood drains life.

Weapon damage scales with metal, star quality and oil tempering.
Stats refresh automatically on existing weapons after `/arcaneforge reload`.

## Commands (permission level 2)

| Command | Description |
|---|---|
| `/arcaneforge give <player> <weapon> [stars] [rune] [metal]` | Gives a pre-forged weapon. Defaults: 5 stars, the signature rune, netherite, oil-tempered. `weapon` accepts `greatsword`, `katana`, `warhammer`, `warscythe`, `daggers` or the full item name |
| `/arcaneforge reload` | Reloads `config/arcaneforge.json` live |
| `/arcaneforge spawnstation` | Builds all five stations in a row in front of you (barrel pre-filled) and hands you a starter kit |

## Configuration: `config/arcaneforge.json`

The file is generated on first start. New keys are merged in automatically when the mod updates.

- `general`
  - `cosmeticLightning`: when true, lightning is visual only (no fire or block damage)
  - `abilitiesAffectPlayers`: PvP toggle; team and PvP rules still apply
  - `showHolograms` and `hologramRange`: client-side hologram display
  - `screenShake`: client-side camera shake
- `forging`
  - Smeltery: capacity, melt, pour and cool times
  - Rhythm minigame difficulty: `beatPeriodTicks`, and the `perfectWindow`/`goodWindow`/`okWindow` timing windows (0–1 normalized ring radius)
  - `strikesRequired`, `waterCrackChance`, `oilTemperBonus` and `ritualTicks`
- `particles`
  - `densityMultiplier` (0–3) scales every particle effect
  - `maxParticlesPerBurst` and `ambientStationParticles`
- `metals`: damage and durability multipliers per metal
- `weapons`: per weapon `attackDamage`, `attackSpeedModifier`, `durability`, `abilityDamage`, `abilityRange`, `cooldownTicks` and `lifesteal`
- `messages`: every chat and action-bar message. Supports `&a`-style codes and hex colors as `&#FF8A00`.
  Placeholders in `{braces}` are filled in automatically.

If the file contains invalid JSON, it is backed up as `arcaneforge.json.broken` and the reload reports the error.

## Building

```bash
python3 generate_assets.py   # regenerates every model, animation, texture, lang and data file (needs Pillow)
./gradlew build              # jar is written to build/libs/
```

Every push also builds the mod on GitHub Actions (`.github/workflows/build.yml`).
The jar is attached to each run as the `arcaneforge-jar` artifact.

### Procedural assets

`generate_assets.py` builds all art from code:

- GeckoLib `.geo.json` models with multi-bone hierarchies, angled cubes and per-face UVs
- Vanilla multi-element item models generated from the same geometry
- Sine-sampled keyframe animations: bellows pumping, the lever pull, rune rings orbiting, steam, ritual spins and weapon idle pulses
- 64×64 material atlases with emissive `_glowmask` layers, 32×32 item sprites, particle frames, the lang file, recipes, loot tables and tags

Custom sound events point to vanilla sound files in `sounds.json`, so a resource pack can replace any of them.
