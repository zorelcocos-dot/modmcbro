package com.arcaneforge.registry;

import com.arcaneforge.ArcaneForge;
import com.arcaneforge.block.entity.ForgeAnvilBlockEntity;
import com.arcaneforge.block.entity.MoldTableBlockEntity;
import com.arcaneforge.block.entity.QuenchingBarrelBlockEntity;
import com.arcaneforge.block.entity.RuneAltarBlockEntity;
import com.arcaneforge.block.entity.SmelteryBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModBlockEntities {
    public static final BlockEntityType<SmelteryBlockEntity> SMELTERY =
            register("smeltery", SmelteryBlockEntity::new, ModBlocks.SMELTERY);
    public static final BlockEntityType<MoldTableBlockEntity> MOLD_TABLE =
            register("mold_table", MoldTableBlockEntity::new, ModBlocks.MOLD_TABLE);
    public static final BlockEntityType<ForgeAnvilBlockEntity> FORGE_ANVIL =
            register("forge_anvil", ForgeAnvilBlockEntity::new, ModBlocks.FORGE_ANVIL);
    public static final BlockEntityType<QuenchingBarrelBlockEntity> QUENCHING_BARREL =
            register("quenching_barrel", QuenchingBarrelBlockEntity::new, ModBlocks.QUENCHING_BARREL);
    public static final BlockEntityType<RuneAltarBlockEntity> RUNE_ALTAR =
            register("rune_altar", RuneAltarBlockEntity::new, ModBlocks.RUNE_ALTAR);

    private ModBlockEntities() {
    }

    private static <T extends BlockEntity> BlockEntityType<T> register(String name, BlockEntityType.BlockEntityFactory<? extends T> factory, Block block) {
        return Registry.register(Registries.BLOCK_ENTITY_TYPE, ArcaneForge.id(name),
                BlockEntityType.Builder.<T>create(factory, block).build(null));
    }

    public static void init() {
        ArcaneForge.LOGGER.debug("Registered Arcane Forge block entities");
    }
}
