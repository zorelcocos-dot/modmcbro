package com.arcaneforge.block;

import com.arcaneforge.block.entity.SmelteryBlockEntity;
import com.arcaneforge.config.ConfigManager;
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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

/** Smeltery Furnace: insert ores/ingots and fuel by right-clicking; empty hand pulls the pour lever. */
public class SmelteryBlock extends StationBlock {
    public static final MapCodec<SmelteryBlock> CODEC = createCodec(SmelteryBlock::new);
    private static final VoxelShape SHAPE = VoxelShapes.union(
            createCuboidShape(0, 0, 0, 16, 3, 16),
            createCuboidShape(1, 3, 1, 15, 14, 15));

    public SmelteryBlock(Settings settings) {
        super(settings, "tooltip.arcaneforge.smeltery");
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
        return new SmelteryBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : validateTicker(type, ModBlockEntities.SMELTERY, SmelteryBlockEntity::tick);
    }

    @Override
    public ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player,
                                          Hand hand, BlockHitResult hit) {
        if (stack.isEmpty() || !SmelteryBlockEntity.isRelevant(stack)) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (world.isClient) {
            return ItemActionResult.SUCCESS;
        }
        if (world.getBlockEntity(pos) instanceof SmelteryBlockEntity be) {
            return be.insert(player, hand, stack);
        }
        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!player.getMainHandStack().isEmpty()) {
            return ActionResult.PASS;
        }
        if (!world.isClient && world.getBlockEntity(pos) instanceof SmelteryBlockEntity be) {
            be.pullLever(player);
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (!ConfigManager.get().particles.ambientStationParticles) {
            return;
        }
        if (!(world.getBlockEntity(pos) instanceof SmelteryBlockEntity be)) {
            return;
        }
        Direction facing = state.get(FACING);
        double fx = pos.getX() + 0.5 + facing.getOffsetX() * 0.52;
        double fz = pos.getZ() + 0.5 + facing.getOffsetZ() * 0.52;
        if (be.getFuelTicks() > 0) {
            double side = (random.nextDouble() - 0.5) * 0.35;
            world.addParticle(ParticleTypes.FLAME, fx + facing.getOffsetZ() * side, pos.getY() + 0.3, fz + facing.getOffsetX() * side, 0, 0.01, 0);
            if (random.nextInt(6) == 0) {
                world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE,
                        SoundCategory.BLOCKS, 0.6f, 1.0f, false);
            }
        }
        if (be.getMolten() > 0) {
            if (random.nextInt(3) == 0) {
                world.addParticle(ParticleTypes.LAVA, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5, pos.getY() + 0.85,
                        pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5, 0, 0, 0);
            }
            world.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6, pos.getY() + 0.95,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
        }
        if (be.isBurning()) {
            world.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5 + facing.rotateYClockwise().getOffsetX() * 0.28 - facing.getOffsetX() * 0.28,
                    pos.getY() + 1.15, pos.getZ() + 0.5 + facing.rotateYClockwise().getOffsetZ() * 0.28 - facing.getOffsetZ() * 0.28, 0, 0.05, 0);
        }
    }
}
