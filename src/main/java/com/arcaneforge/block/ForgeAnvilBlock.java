package com.arcaneforge.block;

import com.arcaneforge.block.entity.ForgeAnvilBlockEntity;
import com.arcaneforge.item.ForgingHammerItem;
import com.arcaneforge.item.HotBladeItem;
import com.arcaneforge.registry.ModBlockEntities;
import com.arcaneforge.util.TextUtil;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;

/** Forge Anvil: place a glowing blank, then strike it with a Forging Hammer in rhythm. */
public class ForgeAnvilBlock extends StationBlock {
    public static final MapCodec<ForgeAnvilBlock> CODEC = createCodec(ForgeAnvilBlock::new);
    private static final VoxelShape SHAPE = VoxelShapes.union(
            createCuboidShape(1, 0, 1, 15, 4, 15),
            createCuboidShape(4, 4, 4, 12, 8, 12),
            createCuboidShape(3, 7, 1, 13, 11.5, 15));

    public ForgeAnvilBlock(Settings settings) {
        super(settings, "tooltip.arcaneforge.forge_anvil");
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
        return new ForgeAnvilBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : validateTicker(type, ModBlockEntities.FORGE_ANVIL, ForgeAnvilBlockEntity::tick);
    }

    @Override
    public ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player,
                                          Hand hand, BlockHitResult hit) {
        boolean hammer = stack.getItem() instanceof ForgingHammerItem;
        boolean blade = stack.getItem() instanceof HotBladeItem;
        if (!hammer && !blade) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (world.isClient) {
            return ItemActionResult.SUCCESS;
        }
        if (!(world.getBlockEntity(pos) instanceof ForgeAnvilBlockEntity be)) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (hammer) {
            be.strike(player, hand, stack);
            return ItemActionResult.SUCCESS;
        }
        if (be.getWorkpiece().isEmpty()) {
            return be.placeBlank(player, stack) ? ItemActionResult.SUCCESS : ItemActionResult.FAIL;
        }
        TextUtil.actionBar(player, be.isFinished() ? "anvil_take_first" : "anvil_need_hammer");
        return ItemActionResult.FAIL;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!player.getMainHandStack().isEmpty()) {
            return ActionResult.PASS;
        }
        if (!world.isClient && world.getBlockEntity(pos) instanceof ForgeAnvilBlockEntity be) {
            be.take(player);
        }
        return ActionResult.success(world.isClient);
    }
}
