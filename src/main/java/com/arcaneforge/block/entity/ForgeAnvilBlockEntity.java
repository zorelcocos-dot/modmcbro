package com.arcaneforge.block.entity;

import com.arcaneforge.config.ArcaneForgeConfig;
import com.arcaneforge.config.ConfigManager;
import com.arcaneforge.forge.ForgeRating;
import com.arcaneforge.forge.WeaponData;
import com.arcaneforge.item.HotBladeItem;
import com.arcaneforge.registry.ModBlockEntities;
import com.arcaneforge.registry.ModParticles;
import com.arcaneforge.registry.ModSounds;
import com.arcaneforge.util.ParticleUtil;
import com.arcaneforge.util.TextUtil;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.List;

/**
 * Holds a glowing blank and runs the hammer rhythm minigame. A ring shrinks towards a fixed target radius every
 * {@code beatPeriod} ticks; striking when the ring overlaps the target yields better ratings and a higher final
 * star quality.
 */
public class ForgeAnvilBlockEntity extends StationBlockEntity {
    /** Normalized radius of the glowing target circle the ring must meet. */
    public static final float TARGET_RADIUS = 0.3f;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.forge_anvil.idle");
    private static final RawAnimation HOT = RawAnimation.begin().thenLoop("animation.forge_anvil.hot");

    private ItemStack workpiece = ItemStack.EMPTY;
    private int strikes;
    private int scoreSum;
    private long cycleStart;
    private int lastRating = -1;
    private long lastRatingTime;
    // Rhythm settings are captured from the server config and synced so client rendering matches judgement.
    private int beatPeriod = 30;
    private int strikesRequired = 6;
    private float perfectWindow = 0.06f;
    private float goodWindow = 0.13f;
    private float okWindow = 0.22f;

    public ForgeAnvilBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FORGE_ANVIL, pos, state);
    }

    public ItemStack getWorkpiece() {
        return workpiece;
    }

    public int getStrikes() {
        return strikes;
    }

    public int getStrikesRequired() {
        return strikesRequired;
    }

    public boolean isFinished() {
        return !workpiece.isEmpty() && WeaponData.quality(workpiece) > 0;
    }

    public boolean isWorking() {
        return !workpiece.isEmpty() && !isFinished();
    }

    public float progress() {
        if (workpiece.isEmpty()) {
            return 0f;
        }
        return isFinished() ? 1f : Math.min(1f, (float) strikes / Math.max(1, strikesRequired));
    }

    public ForgeRating getLastRating() {
        return ForgeRating.byOrdinal(lastRating);
    }

    public long getLastRatingTime() {
        return lastRatingTime;
    }

    public float getPerfectWindow() {
        return perfectWindow;
    }

    public float getGoodWindow() {
        return goodWindow;
    }

    public float getOkWindow() {
        return okWindow;
    }

    /** Current normalized ring radius (1 = outer edge, 0 = centre). */
    public float ringRadius(long worldTime, float tickDelta) {
        int period = Math.max(1, beatPeriod);
        double elapsed = (worldTime - cycleStart) + tickDelta;
        double phase = ((elapsed % period) + period) % period / period;
        return (float) (1.0 - phase);
    }

    @Override
    public int comparatorOutput() {
        return workpiece.isEmpty() ? 0 : Math.max(1, Math.round(15 * progress()));
    }

    @Override
    public List<ItemStack> getDrops() {
        return workpiece.isEmpty() ? List.of() : List.of(workpiece.copy());
    }

    /** Places a glowing blank from the player's hand. */
    public boolean placeBlank(PlayerEntity player, ItemStack stack) {
        if (!workpiece.isEmpty()) {
            TextUtil.actionBar(player, "anvil_occupied");
            return false;
        }
        if (!(stack.getItem() instanceof HotBladeItem) || WeaponData.quality(stack) > 0) {
            TextUtil.actionBar(player, "anvil_need_blank");
            return false;
        }
        World w = getWorld();
        if (w == null) {
            return false;
        }
        ArcaneForgeConfig.Forging cfg = ConfigManager.get().forging;
        workpiece = stack.copyWithCount(1);
        stack.decrementUnlessCreative(1, player);
        strikes = 0;
        scoreSum = 0;
        lastRating = -1;
        cycleStart = w.getTime();
        beatPeriod = cfg.beatPeriodTicks;
        strikesRequired = cfg.strikesRequired;
        perfectWindow = (float) cfg.perfectWindow;
        goodWindow = (float) cfg.goodWindow;
        okWindow = (float) cfg.okWindow;
        w.playSound(null, pos, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.4f, 1.6f);
        if (w instanceof ServerWorld sw) {
            ParticleUtil.burst(sw, ParticleTypes.SMOKE, center(0.75), 8, 0.2, 0.02);
        }
        TextUtil.actionBar(player, "anvil_placed");
        sync();
        return true;
    }

    /** Empty hand: retrieve the workpiece (finished or not). */
    public void take(PlayerEntity player) {
        if (workpiece.isEmpty()) {
            TextUtil.actionBar(player, "anvil_empty");
            return;
        }
        give(player, workpiece);
        workpiece = ItemStack.EMPTY;
        strikes = 0;
        scoreSum = 0;
        lastRating = -1;
        World w = getWorld();
        if (w != null) {
            w.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.5f, 0.8f);
        }
        sync();
    }

    /** A hammer strike. Rates timing, deforms the blade and finalizes quality after the last strike. */
    public void strike(PlayerEntity player, Hand hand, ItemStack hammer) {
        World w = getWorld();
        if (!(w instanceof ServerWorld sw)) {
            return;
        }
        if (workpiece.isEmpty()) {
            TextUtil.actionBar(player, "anvil_empty");
            return;
        }
        if (isFinished()) {
            TextUtil.actionBar(player, "anvil_take_first");
            return;
        }
        if (player.getItemCooldownManager().isCoolingDown(hammer.getItem())) {
            return;
        }
        ArcaneForgeConfig.Forging cfg = ConfigManager.get().forging;
        long now = w.getTime();
        float radius = ringRadius(now, 0f);
        double distance = Math.abs(radius - TARGET_RADIUS);
        ForgeRating rating = ForgeRating.fromDistance(distance, perfectWindow, goodWindow, okWindow);

        strikes++;
        scoreSum += rating.score();
        lastRating = rating.ordinal();
        lastRatingTime = now;
        cycleStart = now;

        player.swingHand(hand, true);
        player.getItemCooldownManager().set(hammer.getItem(), cfg.hammerCooldownTicks);
        hammer.damage(1, player, hand == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);

        strikeEffects(sw, rating);
        TextUtil.actionBar(player, "anvil_rating", "rating", TextUtil.raw("rating_" + rating.key()),
                "strike", strikes, "total", strikesRequired);

        if (strikes >= strikesRequired) {
            int stars = ForgeRating.starsFor(scoreSum, strikes);
            WeaponData.setQuality(workpiece, stars);
            Vec3d top = center(0.8);
            w.playSound(null, pos, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 0.8f, 1.2f);
            w.playSound(null, pos, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.7f, 0.6f + stars * 0.15f);
            ParticleUtil.burst(sw, ParticleTypes.TOTEM_OF_UNDYING, top, 10 + stars * 8, 0.3, 0.25);
            ParticleUtil.ring(sw, ParticleTypes.ELECTRIC_SPARK, top, 0.6, 24, 0.15, 0.05);
            TextUtil.chat(player, "anvil_complete", "stars", TextUtil.stars(stars));
        }
        sync();
    }

    private void strikeEffects(ServerWorld world, ForgeRating rating) {
        Vec3d impact = center(0.75);
        float pitch = 0.55f + rating.score() * 0.17f + world.random.nextFloat() * 0.08f;
        switch (rating) {
            case PERFECT -> {
                world.playSound(null, pos, ModSounds.HAMMER_PERFECT, SoundCategory.BLOCKS, 1.0f, pitch);
                ParticleUtil.sparkFan(world, ModParticles.FORGE_SPARK, impact, new Vec3d(0, 1, 0), 26, 0.35);
                ParticleUtil.sparkFan(world, ParticleTypes.ELECTRIC_SPARK, impact, new Vec3d(0, 0.6, 0), 14, 0.4);
                ParticleUtil.burst(world, ParticleTypes.LAVA, impact, 6, 0.1, 0.0);
                ParticleUtil.ring(world, ParticleTypes.CRIT, impact, 0.2, 18, 0.45, 0.1);
                ParticleUtil.burst(world, ParticleTypes.FLASH, impact, 1, 0, 0);
            }
            case GOOD -> {
                world.playSound(null, pos, ModSounds.HAMMER_STRIKE, SoundCategory.BLOCKS, 1.0f, pitch);
                ParticleUtil.sparkFan(world, ModParticles.FORGE_SPARK, impact, new Vec3d(0, 1, 0), 16, 0.28);
                ParticleUtil.burst(world, ParticleTypes.LAVA, impact, 3, 0.1, 0.0);
                ParticleUtil.burst(world, ParticleTypes.CRIT, impact, 10, 0.15, 0.3);
            }
            case OK -> {
                world.playSound(null, pos, ModSounds.HAMMER_STRIKE, SoundCategory.BLOCKS, 0.8f, pitch);
                ParticleUtil.sparkFan(world, ModParticles.FORGE_SPARK, impact, new Vec3d(0, 1, 0), 8, 0.2);
                ParticleUtil.burst(world, ParticleTypes.CRIT, impact, 5, 0.15, 0.2);
            }
            case MISS -> {
                world.playSound(null, pos, ModSounds.HAMMER_MISS, SoundCategory.BLOCKS, 0.8f, 0.6f + world.random.nextFloat() * 0.1f);
                ParticleUtil.burst(world, ParticleTypes.SMOKE, impact, 6, 0.15, 0.02);
            }
        }
    }

    public static void tick(World world, BlockPos pos, BlockState state, ForgeAnvilBlockEntity be) {
        if (!(world instanceof ServerWorld sw) || !be.isWorking()) {
            return;
        }
        if (ConfigManager.get().particles.ambientStationParticles && world.getTime() % 8 == 0) {
            Vec3d p = be.center(0.75);
            sw.spawnParticles(ParticleTypes.SMALL_FLAME, p.x, p.y, p.z, 1, 0.2, 0.02, 0.2, 0.0);
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        writeStack(nbt, "Workpiece", workpiece, lookup);
        nbt.putInt("Strikes", strikes);
        nbt.putInt("Score", scoreSum);
        nbt.putLong("CycleStart", cycleStart);
        nbt.putInt("LastRating", lastRating);
        nbt.putLong("LastRatingTime", lastRatingTime);
        nbt.putInt("BeatPeriod", beatPeriod);
        nbt.putInt("StrikesRequired", strikesRequired);
        nbt.putFloat("Perfect", perfectWindow);
        nbt.putFloat("Good", goodWindow);
        nbt.putFloat("Ok", okWindow);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        workpiece = readStack(nbt, "Workpiece", lookup);
        strikes = nbt.getInt("Strikes");
        scoreSum = nbt.getInt("Score");
        cycleStart = nbt.getLong("CycleStart");
        lastRating = nbt.contains("LastRating") ? nbt.getInt("LastRating") : -1;
        lastRatingTime = nbt.getLong("LastRatingTime");
        beatPeriod = nbt.contains("BeatPeriod") ? Math.max(1, nbt.getInt("BeatPeriod")) : 30;
        strikesRequired = nbt.contains("StrikesRequired") ? Math.max(1, nbt.getInt("StrikesRequired")) : 6;
        perfectWindow = nbt.contains("Perfect") ? nbt.getFloat("Perfect") : 0.06f;
        goodWindow = nbt.contains("Good") ? nbt.getFloat("Good") : 0.13f;
        okWindow = nbt.contains("Ok") ? nbt.getFloat("Ok") : 0.22f;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 10,
                state -> state.setAndContinue(isWorking() ? HOT : IDLE)));
    }
}
