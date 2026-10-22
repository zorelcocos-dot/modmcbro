package com.arcaneforge.block;

import com.arcaneforge.block.entity.RuneAltarBlockEntity;
import com.arcaneforge.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

/** Rune Awakening Altar: socket Blade, Hilt, Pommel and Rune, then right-click with an empty hand. */
public class RuneAltarBlock extends StationBlock {
    public static final MapCodec<RuneAltarBlock> CODEC = createCodec(RuneAltarBlock::new);
    private static final VoxelShape SHAPE = VoxelShapes.union(
            createCuboidShape(0, 0, 0, 16, 4, 16),
            createCuboidShape(4.5, 4, 4.5, 11.5, 10, 11.5),
            createCuboidShape(2.5, 10, 2.5, 13.5, 12, 13.5));

    public RuneAltarBlock(Settings settings) {
        super(settings, "tooltip.arcaneforge.rune_altar");
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
        return new RuneAltarBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : validateTicker(type, ModBlockEntities.RUNE_ALTAR, RuneAltarBlockEntity::tick);
    }

    @Override
    public ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player,
                                          Hand hand, BlockHitResult hit) {
        if (RuneAltarBlockEntity.slotFor(stack) < 0) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (world.isClient) {
            return ItemActionResult.SUCCESS;
        }
        if (world.getBlockEntity(pos) instanceof RuneAltarBlockEntity be) {
            return be.socket(player, stack);
        }
        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!player.getMainHandStack().isEmpty()) {
            return ActionResult.PASS;
        }
        if (!world.isClient && world.getBlockEntity(pos) instanceof RuneAltarBlockEntity be) {
            be.use(player);
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(3) == 0) {
            world.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5 + (random.nextDouble() - 0.5),
                    pos.getY() + 1.4 + random.nextDouble() * 0.5, pos.getZ() + 0.5 + (random.nextDouble() - 0.5),
                    (random.nextDouble() - 0.5) * 0.5, -0.6, (random.nextDouble() - 0.5) * 0.5);
        }
    }
}
