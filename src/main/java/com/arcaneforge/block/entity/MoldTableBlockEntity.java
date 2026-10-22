package com.arcaneforge.block.entity;

import com.arcaneforge.config.ConfigManager;
import com.arcaneforge.forge.MetalType;
import com.arcaneforge.forge.WeaponFactory;
import com.arcaneforge.forge.WeaponType;
import com.arcaneforge.registry.ModBlockEntities;
import com.arcaneforge.registry.ModParticles;
import com.arcaneforge.util.ParticleUtil;
import com.arcaneforge.util.TextUtil;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.List;

/** Receives molten metal from a smeltery, solidifies it, and yields a glowing weapon blank. */
public class MoldTableBlockEntity extends StationBlockEntity {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.mold_table.idle");
    private static final RawAnimation COOLING = RawAnimation.begin().thenLoop("animation.mold_table.cooling");

    private WeaponType moldType = WeaponType.GREATSWORD;
    private MetalType metal;
    private int filled;
    private int coolTicks;
    private int coolTotal = 80;

    public MoldTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MOLD_TABLE, pos, state);
    }

    public WeaponType getMoldType() {
        return moldType;
    }

    public MetalType getMetal() {
        return metal;
    }

    public int getFilled() {
        return filled;
    }

    public int getRequired() {
        return moldType.moldUnits();
    }

    public boolean isFull() {
        return filled >= getRequired();
    }

    public boolean isReady() {
        return isFull() && coolTicks >= coolTotal;
    }

    public boolean isCooling() {
        return isFull() && coolTicks < coolTotal;
    }

    /** Solidification progress, 0..1. */
    public float coolProgress() {
        return coolTotal <= 0 ? 1f : Math.min(1f, (float) coolTicks / coolTotal);
    }

    public boolean canReceive(MetalType incoming) {
        return incoming != null && !isFull() && (metal == null || filled == 0 || metal == incoming);
    }

    public void receive(MetalType incoming, int units) {
        metal = incoming;
        filled = Math.min(getRequired(), filled + units);
        if (isFull()) {
            coolTicks = 0;
            coolTotal = ConfigManager.get().forging.moldCoolTicks;
        }
        sync();
    }

    @Override
    public int comparatorOutput() {
        if (isReady()) {
            return 15;
        }
        return Math.round(14f * filled / Math.max(1, getRequired()));
    }

    @Override
    public List<ItemStack> getDrops() {
        return List.of();
    }

    /** Empty-hand interaction: take the finished blank or cycle the mold shape. */
    public void use(PlayerEntity player) {
        World w = getWorld();
        if (w == null) {
            return;
        }
        if (isReady()) {
            ItemStack blank = WeaponFactory.hotBlank(moldType, metal != null ? metal : MetalType.IRON);
            give(player, blank);
            TextUtil.actionBar(player, "mold_taken", "type", moldType.displayName().getString());
            w.playSound(null, pos, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 0.8f, 0.6f);
            if (w instanceof ServerWorld sw) {
                ParticleUtil.burst(sw, ModParticles.FORGE_SPARK, center(0.85), 12, 0.3, 0.05);
            }
            filled = 0;
            coolTicks = 0;
            metal = null;
            sync();
            return;
        }
        if (filled > 0) {
            TextUtil.actionBar(player, "mold_busy");
            return;
        }
        moldType = player.isSneaking() ? moldType.previous() : moldType.next();
        w.playSound(null, pos, SoundEvents.BLOCK_STONE_PLACE, SoundCategory.BLOCKS, 0.6f, 1.3f);
        TextUtil.actionBar(player, "mold_cycled", "type", moldType.displayName().getString(), "units", getRequired());
        sync();
    }

    public void setMoldType(WeaponType type) {
        moldType = type;
        sync();
    }

    public static void tick(World world, BlockPos pos, BlockState state, MoldTableBlockEntity be) {
        if (!(world instanceof ServerWorld sw) || !be.isFull() || be.coolTicks >= be.coolTotal) {
            return;
        }
        be.coolTicks++;
        if (world.getTime() % 4 == 0) {
            ParticleUtil.burst(sw, ModParticles.STEAM_PUFF, be.center(0.85), 2, 0.3, 0.1, 0.1, 0.01);
        }
        if (world.getTime() % 10 == 0) {
            world.playSound(null, pos, SoundEvents.BLOCK_LAVA_AMBIENT, SoundCategory.BLOCKS, 0.2f, 1.5f);
        }
        if (be.coolTicks >= be.coolTotal) {
            world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.7f, 1.2f);
            ParticleUtil.burst(sw, ParticleTypes.CLOUD, be.center(0.9), 8, 0.3, 0.02);
            be.sync();
        } else if (be.coolTicks % 10 == 0) {
            be.sync();
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        nbt.putString("Mold", moldType.id());
        if (metal != null) {
            nbt.putString("Metal", metal.id());
        }
        nbt.putInt("Filled", filled);
        nbt.putInt("Cool", coolTicks);
        nbt.putInt("CoolTotal", coolTotal);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        moldType = WeaponType.byIdOrDefault(nbt.getString("Mold"));
        metal = nbt.contains("Metal") ? MetalType.byId(nbt.getString("Metal")) : null;
        filled = nbt.getInt("Filled");
        coolTicks = nbt.getInt("Cool");
        coolTotal = nbt.contains("CoolTotal") ? Math.max(1, nbt.getInt("CoolTotal")) : 80;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 8,
                state -> state.setAndContinue(isCooling() ? COOLING : IDLE)));
    }
}
