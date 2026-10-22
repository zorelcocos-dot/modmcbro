package com.arcaneforge.block.entity;

import com.arcaneforge.config.ArcaneForgeConfig;
import com.arcaneforge.config.ConfigManager;
import com.arcaneforge.forge.MetalType;
import com.arcaneforge.registry.ModBlockEntities;
import com.arcaneforge.registry.ModParticles;
import com.arcaneforge.registry.ModSounds;
import com.arcaneforge.util.ParticleUtil;
import com.arcaneforge.util.TextUtil;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.List;

/**
 * Melts ores and ingots into a single pool of molten metal using solid fuel, then pours it into an
 * adjacent {@link MoldTableBlockEntity} when the side lever is pulled.
 */
public class SmelteryBlockEntity extends StationBlockEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.smeltery.idle");
    private static final RawAnimation ACTIVE = RawAnimation.begin().thenLoop("animation.smeltery.active");
    private static final RawAnimation POUR = RawAnimation.begin().thenLoop("animation.smeltery.pour");

    private MetalType metal;
    private int molten;
    private int queued;
    private int meltProgress;
    private int fuelTicks;
    private boolean pouring;
    private Direction pourDirection = Direction.NORTH;
    private int pourProgress;
    private int capacity = 18;

    public SmelteryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMELTERY, pos, state);
    }

    // ------------------------------------------------------------------ accessors

    public MetalType getMetal() {
        return metal;
    }

    public int getMolten() {
        return molten;
    }

    public int getQueued() {
        return queued;
    }

    public int getFuelTicks() {
        return fuelTicks;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isPouring() {
        return pouring;
    }

    public Direction getPourDirection() {
        return pourDirection;
    }

    public boolean isBurning() {
        return fuelTicks > 0 && queued > 0;
    }

    @Override
    public int comparatorOutput() {
        return capacity <= 0 ? 0 : Math.round(15f * molten / capacity);
    }

    @Override
    public List<ItemStack> getDrops() {
        return List.of();
    }

    // ------------------------------------------------------------------ interaction

    public static boolean isRelevant(ItemStack stack) {
        if (MetalType.smeltable(stack) != null) {
            return true;
        }
        Integer fuel = FuelRegistry.INSTANCE.get(stack.getItem());
        return fuel != null && fuel > 0;
    }

    public ItemActionResult insert(PlayerEntity player, Hand hand, ItemStack stack) {
        ArcaneForgeConfig.Forging cfg = ConfigManager.get().forging;
        capacity = cfg.smelteryCapacity;
        MetalType.Smeltable smeltable = MetalType.smeltable(stack);
        if (smeltable != null) {
            if (metal != null && metal != smeltable.metal() && (molten + queued) > 0) {
                TextUtil.actionBar(player, "smeltery_mismatch", "metal", metal.displayName().getString());
                return ItemActionResult.FAIL;
            }
            if (molten + queued + smeltable.units() > capacity) {
                TextUtil.actionBar(player, "smeltery_full");
                return ItemActionResult.FAIL;
            }
            metal = smeltable.metal();
            queued += smeltable.units();
            String name = stack.getName().getString();
            stack.decrementUnlessCreative(1, player);
            World w = getWorld();
            if (w instanceof ServerWorld sw) {
                w.playSound(null, pos, ModSounds.SMELTERY_INSERT, SoundCategory.BLOCKS, 0.8f, 0.8f + w.random.nextFloat() * 0.4f);
                ParticleUtil.burst(sw, ParticleTypes.LAVA, center(0.85), 4, 0.2, 0.0);
                ParticleUtil.burst(sw, ParticleTypes.SMOKE, center(0.95), 6, 0.2, 0.02);
            }
            TextUtil.actionBar(player, "smeltery_inserted", "item", name, "queued", queued);
            sync();
            return ItemActionResult.SUCCESS;
        }
        Integer fuel = FuelRegistry.INSTANCE.get(stack.getItem());
        if (fuel != null && fuel > 0) {
            if (fuelTicks >= cfg.maxFuelTicks) {
                TextUtil.actionBar(player, "smeltery_fuel_full");
                return ItemActionResult.FAIL;
            }
            fuelTicks = Math.min(cfg.maxFuelTicks, fuelTicks + fuel);
            if (stack.isOf(Items.LAVA_BUCKET)) {
                player.setStackInHand(hand, ItemUsage.exchangeStack(stack, player, new ItemStack(Items.BUCKET)));
            } else {
                stack.decrementUnlessCreative(1, player);
            }
            World w = getWorld();
            if (w instanceof ServerWorld sw) {
                w.playSound(null, pos, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.BLOCKS, 0.6f, 1.2f);
                ParticleUtil.burst(sw, ParticleTypes.FLAME, center(0.35).add(Vec3d.of(facing().getVector()).multiply(0.45)), 10, 0.15, 0.03);
            }
            TextUtil.actionBar(player, "smeltery_fuel", "seconds", fuelTicks / 20);
            sync();
            return ItemActionResult.SUCCESS;
        }
        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Lever pull: toggles pouring into an adjacent mold table. */
    public void pullLever(PlayerEntity player) {
        World w = getWorld();
        if (w == null) {
            return;
        }
        w.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.6f, pouring ? 0.5f : 0.6f);
        if (pouring) {
            pouring = false;
            TextUtil.actionBar(player, "smeltery_pour_stop");
            sync();
            return;
        }
        if (molten <= 0 || metal == null) {
            TextUtil.actionBar(player, "smeltery_empty");
            return;
        }
        Direction target = findMold(w);
        if (target == null) {
            TextUtil.actionBar(player, "smeltery_no_mold");
            return;
        }
        pourDirection = target;
        pouring = true;
        pourProgress = 0;
        TextUtil.actionBar(player, "smeltery_pour_start", "metal", metal.displayName().getString());
        sync();
    }

    private Direction findMold(World w) {
        Direction facing = facing();
        Direction[] order = {facing.rotateYClockwise(), facing.rotateYCounterclockwise(), facing, facing.getOpposite()};
        for (Direction d : order) {
            if (w.getBlockEntity(pos.offset(d)) instanceof MoldTableBlockEntity mold && mold.canReceive(metal)) {
                return d;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ ticking

    public static void tick(World world, BlockPos pos, BlockState state, SmelteryBlockEntity be) {
        if (!(world instanceof ServerWorld sw)) {
            return;
        }
        ArcaneForgeConfig.Forging cfg = ConfigManager.get().forging;
        boolean dirty = false;
        if (be.capacity != cfg.smelteryCapacity) {
            be.capacity = cfg.smelteryCapacity;
            dirty = true;
        }

        if (be.isBurning()) {
            be.fuelTicks--;
            be.meltProgress++;
            if (be.meltProgress >= cfg.meltTicksPerUnit) {
                be.meltProgress = 0;
                be.queued--;
                be.molten++;
                dirty = true;
                world.playSound(null, pos, SoundEvents.BLOCK_LAVA_POP, SoundCategory.BLOCKS, 0.5f, 0.8f + world.random.nextFloat() * 0.3f);
                ParticleUtil.burst(sw, ParticleTypes.LAVA, be.center(0.8), 2, 0.25, 0.0);
            }
            if (world.getTime() % 30 == 0) {
                world.playSound(null, pos, ModSounds.SMELTERY_BELLOWS, SoundCategory.BLOCKS, 0.5f, 0.8f + world.random.nextFloat() * 0.3f);
                Direction left = be.facing().rotateYCounterclockwise();
                Vec3d nozzle = be.center(0.3).add(Vec3d.of(left.getVector()).multiply(0.55));
                ParticleUtil.sparkFan(sw, ParticleTypes.FLAME, nozzle, Vec3d.of(left.getOpposite().getVector()), 6, 0.08);
                ParticleUtil.burst(sw, ParticleTypes.LARGE_SMOKE, be.center(1.15), 3, 0.15, 0.02);
            }
            if (!be.isBurning() || world.getTime() % 20 == 0) {
                dirty = true;
            }
        }

        if (be.pouring) {
            dirty |= be.tickPour(sw, cfg);
        }

        if (be.molten <= 0 && be.queued <= 0 && be.metal != null) {
            be.metal = null;
            dirty = true;
        }
        if (dirty) {
            be.sync();
        }
    }

    private boolean tickPour(ServerWorld world, ArcaneForgeConfig.Forging cfg) {
        BlockPos targetPos = pos.offset(pourDirection);
        if (!(world.getBlockEntity(targetPos) instanceof MoldTableBlockEntity mold) || molten <= 0 || metal == null
                || !mold.canReceive(metal)) {
            pouring = false;
            world.playSound(null, pos, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 0.3f, 1.4f);
            return true;
        }
        spawnPourStream(world, targetPos);
        pourProgress++;
        if (pourProgress < cfg.pourTicksPerUnit) {
            return false;
        }
        pourProgress = 0;
        mold.receive(metal, 1);
        molten--;
        world.playSound(null, pos, ModSounds.SMELTERY_POUR, SoundCategory.BLOCKS, 0.7f, 0.85f + world.random.nextFloat() * 0.3f);
        if (molten <= 0 || !mold.canReceive(metal)) {
            pouring = false;
        }
        return true;
    }

    private void spawnPourStream(ServerWorld world, BlockPos targetPos) {
        Vec3d dir = Vec3d.of(pourDirection.getVector());
        Vec3d start = center(0.78).add(dir.multiply(0.45));
        Vec3d end = Vec3d.ofBottomCenter(targetPos).add(0, 0.8, 0);
        int color = metal.moltenColor();
        int segments = ParticleUtil.scaled(8);
        for (int i = 0; i <= segments; i++) {
            double t = (double) i / segments;
            Vec3d p = start.lerp(end, t).add(0, Math.sin(Math.PI * t) * 0.25 - t * 0.05, 0);
            world.spawnParticles(ParticleUtil.dust(color, 1.1f), p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0);
        }
        if (world.random.nextInt(3) == 0) {
            world.spawnParticles(ParticleTypes.FALLING_LAVA, end.x, end.y + 0.1, end.z, 1, 0.05, 0, 0.05, 0);
        }
        if (world.random.nextInt(4) == 0) {
            world.spawnParticles(ParticleTypes.LAVA, end.x, end.y, end.z, 1, 0.1, 0, 0.1, 0);
            world.spawnParticles(ModParticles.FORGE_SPARK, end.x, end.y + 0.05, end.z, 2, 0.1, 0.05, 0.1, 0.05);
        }
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        if (metal != null) {
            nbt.putString("Metal", metal.id());
        }
        nbt.putInt("Molten", molten);
        nbt.putInt("Queued", queued);
        nbt.putInt("MeltProgress", meltProgress);
        nbt.putInt("Fuel", fuelTicks);
        nbt.putBoolean("Pouring", pouring);
        nbt.putInt("PourDir", pourDirection.getId());
        nbt.putInt("PourProgress", pourProgress);
        nbt.putInt("Capacity", capacity);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        metal = nbt.contains("Metal") ? MetalType.byId(nbt.getString("Metal")) : null;
        molten = nbt.getInt("Molten");
        queued = nbt.getInt("Queued");
        meltProgress = nbt.getInt("MeltProgress");
        fuelTicks = nbt.getInt("Fuel");
        pouring = nbt.getBoolean("Pouring");
        pourDirection = Direction.byId(nbt.getInt("PourDir"));
        pourProgress = nbt.getInt("PourProgress");
        capacity = nbt.contains("Capacity") ? nbt.getInt("Capacity") : 18;
    }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6, state -> {
            if (pouring) {
                return state.setAndContinue(POUR);
            }
            return state.setAndContinue(isBurning() ? ACTIVE : IDLE);
        }));
    }
}
