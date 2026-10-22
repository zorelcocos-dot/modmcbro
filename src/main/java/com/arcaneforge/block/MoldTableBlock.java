package com.arcaneforge.block;

import com.arcaneforge.block.entity.MoldTableBlockEntity;
import com.arcaneforge.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

/** Casting Mold Table: empty hand cycles the mold shape (sneak = backwards) or takes out the solid blank. */
public class MoldTableBlock extends StationBlock {
    public static final MapCodec<MoldTableBlock> CODEC = createCodec(MoldTableBlock::new);
    private static final VoxelShape SHAPE = VoxelShapes.union(
            createCuboidShape(1, 0, 1, 15, 8, 15),
            createCuboidShape(0, 8, 0, 16, 12.5, 16));

    public MoldTableBlock(Settings settings) {
        super(settings, "tooltip.arcaneforge.mold_table");
    }

    @Override
    public MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected VoxelShape shape() {
        return SHAPE;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MoldTableBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : validateTicker(type, ModBlockEntities.MOLD_TABLE, MoldTableBlockEntity::tick);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!player.getMainHandStack().isEmpty()) {
            return ActionResult.PASS;
        }
        if (!world.isClient && world.getBlockEntity(pos) instanceof MoldTableBlockEntity be) {
            be.use(player);
        }
        return ActionResult.success(world.isClient);
    }
}
