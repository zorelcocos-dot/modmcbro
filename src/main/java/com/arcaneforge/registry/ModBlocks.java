package com.arcaneforge.registry;

import com.arcaneforge.ArcaneForge;
import com.arcaneforge.block.ForgeAnvilBlock;
import com.arcaneforge.block.MoldTableBlock;
import com.arcaneforge.block.QuenchingBarrelBlock;
import com.arcaneforge.block.RuneAltarBlock;
import com.arcaneforge.block.SmelteryBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

public final class ModBlocks {
    public static final Block SMELTERY = register("smeltery", new SmelteryBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.DEEPSLATE_GRAY).requiresTool()
            .strength(3.5f, 6.0f).sounds(BlockSoundGroup.DEEPSLATE_BRICKS).nonOpaque().luminance(state -> 9)));
    public static final Block MOLD_TABLE = register("mold_table", new MoldTableBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.STONE_GRAY).requiresTool().strength(2.5f, 6.0f).sounds(BlockSoundGroup.STONE).nonOpaque()));
    public static final Block FORGE_ANVIL = register("forge_anvil", new ForgeAnvilBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.IRON_GRAY).requiresTool().strength(5.0f, 1200.0f).sounds(BlockSoundGroup.ANVIL).nonOpaque()));
    public static final Block QUENCHING_BARREL = register("quenching_barrel", new QuenchingBarrelBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.OAK_TAN).strength(2.5f).sounds(BlockSoundGroup.WOOD).nonOpaque()));
    public static final Block RUNE_ALTAR = register("rune_altar", new RuneAltarBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.PURPLE).requiresTool().strength(5.0f, 1200.0f).sounds(BlockSoundGroup.AMETHYST_BLOCK)
            .nonOpaque().luminance(state -> 7)));

    private ModBlocks() {
    }

    private static Block register(String name, Block block) {
        return Registry.register(Registries.BLOCK, ArcaneForge.id(name), block);
    }

    public static void init() {
        ArcaneForge.LOGGER.debug("Registered Arcane Forge blocks");
    }
}
