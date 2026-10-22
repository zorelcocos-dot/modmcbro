package com.arcaneforge.block.entity;

import com.arcaneforge.config.ArcaneForgeConfig;
import com.arcaneforge.config.ConfigManager;
import com.arcaneforge.forge.WeaponData;
import com.arcaneforge.forge.WeaponFactory;
import com.arcaneforge.item.HotBladeItem;
import com.arcaneforge.registry.ModBlockEntities;
import com.arcaneforge.registry.ModItems;
import com.arcaneforge.registry.ModParticles;
import com.arcaneforge.registry.ModSounds;
import com.arcaneforge.util.ParticleUtil;
import com.arcaneforge.util.TextUtil;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.List;
import java.util.Locale;

/** Water or oil filled barrel that cools hot forged blades into usable parts. */
public class QuenchingBarrelBlockEntity extends StationBlockEntity {
    public static final int MAX_LEVEL = 4;
    private static final int STEAM_TICKS = 40;
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.quenching_barrel.idle");
    private static final RawAnimation STEAM = RawAnimation.begin().thenLoop("animation.quenching_barrel.steam");

    public enum Liquid {
        NONE, WATER, OIL;

        public static Liquid byName(String name) {
            try {
                return valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return NONE;
            }
        }
    }

    private Liquid liquid = Liquid.NONE;
    private int level;
    private long lastQuench = -1000;

    public QuenchingBarrelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.QUENCHING_BARREL, pos, state);
    }

    public Liquid getLiquid() {
        return liquid;
    }

    public int getLevel() {
        return level;
    }

    public boolean isSteaming(long worldTime) {
        return worldTime - lastQuench < STEAM_TICKS;
    }

    public void fill(Liquid newLiquid, int newLevel) {
        liquid = newLevel <= 0 ? Liquid.NONE : newLiquid;
        level = Math.max(0, Math.min(MAX_LEVEL, newLevel));
        sync();
    }

    @Override
    public int comparatorOutput() {
        return level * 15 / MAX_LEVEL;
    }

    @Override
    public List<ItemStack> getDrops() {
        return List.of();
    }

    public static boolean isRelevant(ItemStack stack) {
        return stack.isOf(Items.WATER_BUCKET) || stack.isOf(Items.BUCKET) || stack.isOf(ModItems.QUENCHING_OIL)
                || stack.getItem() instanceof HotBladeItem;
    }

    public ItemActionResult interact(PlayerEntity player, Hand hand, ItemStack stack) {
        World w = getWorld();
        if (w == null) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.isOf(Items.WATER_BUCKET)) {
            if (liquid == Liquid.OIL && level > 0) {
                TextUtil.actionBar(player, "barrel_wrong_fluid");
                return ItemActionResult.FAIL;
            }
            if (liquid == Liquid.WATER && level >= MAX_LEVEL) {
                TextUtil.actionBar(player, "barrel_full");
                return ItemActionResult.FAIL;
            }
            player.setStackInHand(hand, ItemUsage.exchangeStack(stack, player, new ItemStack(Items.BUCKET)));
            fill(Liquid.WATER, MAX_LEVEL);
            w.playSound(null, pos, SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 1f, 1f);
            TextUtil.actionBar(player, "barrel_filled_water");
            return ItemActionResult.SUCCESS;
        }
        if (stack.isOf(Items.BUCKET)) {
            if (liquid != Liquid.WATER || level < MAX_LEVEL) {
                return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            player.setStackInHand(hand, ItemUsage.exchangeStack(stack, player, new ItemStack(Items.WATER_BUCKET)));
            fill(Liquid.NONE, 0);
            w.playSound(null, pos, SoundEvents.ITEM_BUCKET_FILL, SoundCategory.BLOCKS, 1f, 1f);
            return ItemActionResult.SUCCESS;
        }
        if (stack.isOf(ModItems.QUENCHING_OIL)) {
            if (liquid == Liquid.WATER && level > 0) {
                TextUtil.actionBar(player, "barrel_wrong_fluid");
                return ItemActionResult.FAIL;
            }
            if (level >= MAX_LEVEL) {
                TextUtil.actionBar(player, "barrel_full");
                return ItemActionResult.FAIL;
            }
            player.setStackInHand(hand, ItemUsage.exchangeStack(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
            fill(Liquid.OIL, level + 1);
            w.playSound(null, pos, SoundEvents.ITEM_BOTTLE_EMPTY, SoundCategory.BLOCKS, 1f, 0.8f);
            TextUtil.actionBar(player, "barrel_filled_oil", "level", level, "max", MAX_LEVEL);
            return ItemActionResult.SUCCESS;
        }
        if (stack.getItem() instanceof HotBladeItem) {
            if (WeaponData.quality(stack) <= 0) {
                TextUtil.actionBar(player, "quench_need_forged");
                return ItemActionResult.FAIL;
            }
            if (level <= 0) {
                TextUtil.actionBar(player, "barrel_empty");
                return ItemActionResult.FAIL;
            }
            quench(player, hand, stack);
            return ItemActionResult.SUCCESS;
        }
        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private void quench(PlayerEntity player, Hand hand, ItemStack hot) {
        World w = getWorld();
        if (!(w instanceof ServerWorld sw)) {
            return;
        }
        ArcaneForgeConfig.Forging cfg = ConfigManager.get().forging;
        boolean oil = liquid == Liquid.OIL;
        WeaponFactory.QuenchResult result = WeaponFactory.quench(hot, oil, cfg.waterCrackChance, w.random);
        ItemStack cooled = result.stack();
        if (hot.getCount() > 1) {
            hot.decrement(1);
            give(player, cooled);
        } else {
            player.setStackInHand(hand, cooled);
        }
        level--;
        if (level <= 0) {
            level = 0;
            liquid = Liquid.NONE;
        }
        lastQuench = w.getTime();

        Vec3d surface = center(0.85);
        w.playSound(null, pos, ModSounds.QUENCH_HISS, SoundCategory.BLOCKS, 1.2f, oil ? 0.6f : 0.9f + w.random.nextFloat() * 0.2f);
        w.playSound(null, pos, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 1.0f, 0.7f);
        ParticleUtil.burst(sw, ModParticles.STEAM_PUFF, surface, 40, 0.3, 0.2, 0.3, 0.08);
        ParticleUtil.burst(sw, ParticleTypes.CLOUD, surface.add(0, 0.3, 0), 25, 0.35, 0.5, 0.35, 0.06);
        ParticleUtil.burst(sw, oil ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SPLASH, surface, 20, 0.3, 0.05, 0.3, 0.2);
        ParticleUtil.burst(sw, ParticleTypes.BUBBLE_POP, surface, 12, 0.3, 0.05, 0.3, 0.05);
        if (oil) {
            ParticleUtil.burst(sw, ParticleTypes.FLAME, surface, 14, 0.25, 0.05, 0.25, 0.04);
            TextUtil.actionBar(player, "quench_oil", "bonus", cfg.oilTemperBonus);
        } else if (result.cracked()) {
            ParticleUtil.burst(sw, ParticleTypes.CRIT, surface, 10, 0.2, 0.2, 0.2, 0.3);
            w.playSound(null, pos, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.BLOCKS, 0.6f, 1.6f);
            TextUtil.actionBar(player, "quench_crack");
        } else {
            TextUtil.actionBar(player, "quench_water");
        }
        sync();
    }

    public static void tick(World world, BlockPos pos, BlockState state, QuenchingBarrelBlockEntity be) {
        if (!(world instanceof ServerWorld sw) || !be.isSteaming(world.getTime())) {
            return;
        }
        if (world.getTime() % 3 == 0) {
            ParticleUtil.burst(sw, ModParticles.STEAM_PUFF, be.center(0.95), 2, 0.25, 0.05, 0.25, 0.02);
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        nbt.putString("Liquid", liquid.name());
        nbt.putInt("Level", level);
        nbt.putLong("LastQuench", lastQuench);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        liquid = Liquid.byName(nbt.getString("Liquid"));
        level = nbt.getInt("Level");
        lastQuench = nbt.contains("LastQuench") ? nbt.getLong("LastQuench") : -1000;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 5, state -> {
            World w = getWorld();
            return state.setAndContinue(w != null && isSteaming(w.getTime()) ? STEAM : IDLE);
        }));
    }
}
