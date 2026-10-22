package com.arcaneforge.block.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/**
 * Base for every forge station: GeckoLib animation cache, full-NBT client sync and helpers
 * for storing item stacks.
 */
public abstract class StationBlockEntity extends BlockEntity implements GeoBlockEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    protected StationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Items that should drop when the station is broken. */
    public abstract List<ItemStack> getDrops();

    public int comparatorOutput() {
        return 0;
    }

    /** Persists and pushes the full state to tracking clients. */
    public void sync() {
        markDirty();
        if (world != null && !world.isClient) {
            BlockState state = getCachedState();
            world.updateListeners(pos, state, state, Block.NOTIFY_ALL);
        }
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        return createNbt(registryLookup);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    public Direction facing() {
        BlockState state = getCachedState();
        return state.contains(com.arcaneforge.block.StationBlock.FACING)
                ? state.get(com.arcaneforge.block.StationBlock.FACING) : Direction.NORTH;
    }

    public Vec3d center(double yOffset) {
        return Vec3d.ofBottomCenter(pos).add(0, yOffset, 0);
    }

    protected static void writeStack(NbtCompound nbt, String key, ItemStack stack, RegistryWrapper.WrapperLookup lookup) {
        if (!stack.isEmpty()) {
            nbt.put(key, stack.encode(lookup));
        }
    }

    protected static ItemStack readStack(NbtCompound nbt, String key, RegistryWrapper.WrapperLookup lookup) {
        return nbt.contains(key) ? ItemStack.fromNbtOrEmpty(lookup, nbt.getCompound(key)) : ItemStack.EMPTY;
    }

    protected static void give(PlayerEntity player, ItemStack stack) {
        player.getInventory().offerOrDrop(stack.copy());
    }
}
