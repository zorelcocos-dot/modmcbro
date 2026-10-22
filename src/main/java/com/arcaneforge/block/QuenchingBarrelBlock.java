package com.arcaneforge.block;

import com.arcaneforge.block.entity.QuenchingBarrelBlockEntity;
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
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

/** Quenching Barrel: fill with water or quenching oil, then plunge hot forged blades in. */
public class QuenchingBarrelBlock extends StationBlock {
    public static final MapCodec<QuenchingBarrelBlock> CODEC = createCodec(QuenchingBarrelBlock::new);
    private static final VoxelShape SHAPE = VoxelShapes.combineAndSimplify(
            createCuboidShape(1, 0, 1, 15, 13, 15),
            createCuboidShape(3, 2, 3, 13, 13, 13),
            net.minecraft.util.function.BooleanBiFunction.ONLY_FIRST);
    private static final VoxelShape OUTLINE = createCuboidShape(1, 0, 1, 15, 13, 15);

    public QuenchingBarrelBlock(Settings settings) {
        super(settings, "tooltip.arcaneforge.quenching_barrel");
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
    public VoxelShape getOutlineShape(BlockState state, net.minecraft.world.BlockView world, BlockPos pos,
                                      net.minecraft.block.ShapeContext context) {
        return OUTLINE;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new QuenchingBarrelBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : validateTicker(type, ModBlockEntities.QUENCHING_BARREL, QuenchingBarrelBlockEntity::tick);
    }

    @Override
    public ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player,
                                          Hand hand, BlockHitResult hit) {
        if (!QuenchingBarrelBlockEntity.isRelevant(stack)) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (world.isClient) {
            return ItemActionResult.SUCCESS;
        }
        if (world.getBlockEntity(pos) instanceof QuenchingBarrelBlockEntity be) {
            return be.interact(player, hand, stack);
        }
        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (world.getBlockEntity(pos) instanceof QuenchingBarrelBlockEntity be && be.getLevel() > 0 && random.nextInt(4) == 0) {
            double y = pos.getY() + (2 + 10.0 * be.getLevel() / QuenchingBarrelBlockEntity.MAX_LEVEL) / 16.0;
            world.addParticle(be.getLiquid() == QuenchingBarrelBlockEntity.Liquid.OIL ? ParticleTypes.SMOKE : ParticleTypes.BUBBLE_POP,
                    pos.getX() + 0.3 + random.nextDouble() * 0.4, y, pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.01, 0);
        }
    }
}
