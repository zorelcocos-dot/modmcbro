package com.arcaneforge.block.entity;

import com.arcaneforge.config.ConfigManager;
import com.arcaneforge.forge.Rune;
import com.arcaneforge.forge.WeaponFactory;
import com.arcaneforge.item.CooledBladeItem;
import com.arcaneforge.item.RuneItem;
import com.arcaneforge.registry.ModBlockEntities;
import com.arcaneforge.registry.ModItems;
import com.arcaneforge.registry.ModParticles;
import com.arcaneforge.registry.ModSounds;
import com.arcaneforge.util.ParticleUtil;
import com.arcaneforge.util.TextUtil;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/** Four-socket altar that fuses Blade + Hilt + Pommel + Rune into an awakened modular weapon. */
public class RuneAltarBlockEntity extends StationBlockEntity {
    public static final int SLOT_BLADE = 0;
    public static final int SLOT_HILT = 1;
    public static final int SLOT_POMMEL = 2;
    public static final int SLOT_RUNE = 3;
    public static final String[] SLOT_KEYS = {"blade", "hilt", "pommel", "rune"};

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.rune_altar.idle");
    private static final RawAnimation RITUAL = RawAnimation.begin().thenLoop("animation.rune_altar.ritual");

    private final ItemStack[] slots = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private ItemStack output = ItemStack.EMPTY;
    private long ritualStart = -1;
    private int ritualDuration = 80;

    public RuneAltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RUNE_ALTAR, pos, state);
    }

    public ItemStack getSlot(int index) {
        return slots[index];
    }

    public ItemStack getOutput() {
        return output;
    }

    public boolean isRitualActive() {
        return ritualStart >= 0;
    }

    public boolean isComplete() {
        for (ItemStack s : slots) {
            if (s.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** 0..1 progress of the running ritual, interpolated for rendering. */
    public float ritualProgress(long worldTime, float tickDelta) {
        if (ritualStart < 0) {
            return 0f;
        }
        return Math.min(1f, (worldTime - ritualStart + tickDelta) / Math.max(1, ritualDuration));
    }

    public Rune socketedRune() {
        return slots[SLOT_RUNE].getItem() instanceof RuneItem rune ? rune.getRune() : null;
    }

    public List<String> missingSlots() {
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < slots.length; i++) {
            if (slots[i].isEmpty()) {
                missing.add(SLOT_KEYS[i]);
            }
        }
        return missing;
    }

    @Override
    public int comparatorOutput() {
        if (!output.isEmpty()) {
            return 15;
        }
        int filled = 4 - missingSlots().size();
        return filled * 3;
    }

    @Override
    public List<ItemStack> getDrops() {
        List<ItemStack> drops = new ArrayList<>();
        for (ItemStack s : slots) {
            if (!s.isEmpty()) {
                drops.add(s.copy());
            }
        }
        if (!output.isEmpty()) {
            drops.add(output.copy());
        }
        return drops;
    }

    public static int slotFor(ItemStack stack) {
        if (stack.getItem() instanceof CooledBladeItem) return SLOT_BLADE;
        if (stack.isOf(ModItems.HILT)) return SLOT_HILT;
        if (stack.isOf(ModItems.POMMEL)) return SLOT_POMMEL;
        if (stack.getItem() instanceof RuneItem) return SLOT_RUNE;
        return -1;
    }

    public ItemActionResult socket(PlayerEntity player, ItemStack stack) {
        int slot = slotFor(stack);
        if (slot < 0) {
            return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (isRitualActive()) {
            TextUtil.actionBar(player, "altar_busy");
            return ItemActionResult.FAIL;
        }
        if (!slots[slot].isEmpty() || !output.isEmpty()) {
            TextUtil.actionBar(player, "altar_slot_full");
            return ItemActionResult.FAIL;
        }
        slots[slot] = stack.copyWithCount(1);
        String name = stack.getName().getString();
        stack.decrementUnlessCreative(1, player);
        World w = getWorld();
        if (w instanceof ServerWorld sw) {
            w.playSound(null, pos, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.BLOCKS, 0.8f, 1.0f + slot * 0.15f);
            ParticleUtil.burst(sw, ParticleTypes.ENCHANT, center(1.2), 20, 0.3, 0.5);
        }
        TextUtil.actionBar(player, "altar_socketed", "item", name);
        sync();
        return ItemActionResult.SUCCESS;
    }

    /** Empty hand: collect the output, start the ritual, or (sneaking) remove the last socketed part. */
    public void use(PlayerEntity player) {
        World w = getWorld();
        if (w == null) {
            return;
        }
        if (!output.isEmpty()) {
            give(player, output);
            output = ItemStack.EMPTY;
            w.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.7f, 1.0f);
            sync();
            return;
        }
        if (isRitualActive()) {
            TextUtil.actionBar(player, "altar_busy");
            return;
        }
        if (player.isSneaking()) {
            for (int i = slots.length - 1; i >= 0; i--) {
                if (!slots[i].isEmpty()) {
                    String name = slots[i].getName().getString();
                    give(player, slots[i]);
                    slots[i] = ItemStack.EMPTY;
                    TextUtil.actionBar(player, "altar_removed", "item", name);
                    w.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.5f, 0.7f);
                    sync();
                    return;
                }
            }
            return;
        }
        if (!isComplete()) {
            StringJoiner joiner = new StringJoiner(", ");
            for (String key : missingSlots()) {
                joiner.add(Text.translatable("hologram.arcaneforge.slot." + key).getString());
            }
            TextUtil.actionBar(player, "altar_missing", "missing", joiner.toString());
            return;
        }
        ritualStart = w.getTime();
        ritualDuration = ConfigManager.get().forging.ritualTicks;
        w.playSound(null, pos, ModSounds.ALTAR_CHARGE, SoundCategory.BLOCKS, 1.0f, 0.8f);
        TextUtil.actionBar(player, "altar_start");
        sync();
    }

    public static void tick(World world, BlockPos pos, BlockState state, RuneAltarBlockEntity be) {
        if (!(world instanceof ServerWorld sw)) {
            return;
        }
        if (!be.isRitualActive()) {
            if (ConfigManager.get().particles.ambientStationParticles && world.getTime() % 10 == 0) {
                Vec3d c = be.center(1.1);
                sw.spawnParticles(ModParticles.RUNE_GLYPH, c.x, c.y, c.z, 1, 0.4, 0.3, 0.4, 0.01);
            }
            return;
        }
        long elapsed = world.getTime() - be.ritualStart;
        float progress = be.ritualProgress(world.getTime(), 0f);
        Rune rune = be.socketedRune();
        int color = rune != null ? rune.color() : 0xB070FF;
        Vec3d core = be.center(1.35 + progress * 0.4);

        if (elapsed % 2 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = Math.PI / 2 * i + elapsed * 0.08;
                double radius = 0.45 * (1 - progress) + 0.05;
                Vec3d itemPos = be.center(1.1 + progress * 0.5).add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                ParticleUtil.line(sw, ParticleUtil.dust(color, 0.7f), itemPos, core, 0.12);
                sw.spawnParticles(ParticleTypes.ENCHANT, itemPos.x, itemPos.y + 0.6, itemPos.z, 2, 0.1, 0.1, 0.1, 0.6);
            }
            ParticleUtil.helix(sw, ParticleTypes.END_ROD, be.center(0.8), 0.8 * (1 - progress) + 0.15, 1.2, 10, elapsed * 0.2);
        }
        if (elapsed % 20 == 0) {
            world.playSound(null, pos, ModSounds.ALTAR_CHARGE, SoundCategory.BLOCKS, 0.8f, 0.8f + progress * 0.8f);
        }
        if (elapsed >= be.ritualDuration) {
            be.finishRitual(sw, core, color);
        }
    }

    private void finishRitual(ServerWorld world, Vec3d core, int color) {
        Rune rune = socketedRune();
        output = WeaponFactory.awaken(slots[SLOT_BLADE], rune);
        for (int i = 0; i < slots.length; i++) {
            slots[i] = ItemStack.EMPTY;
        }
        ritualStart = -1;

        world.playSound(null, pos, ModSounds.ALTAR_AWAKEN, SoundCategory.BLOCKS, 1.2f, 1.0f);
        ParticleUtil.burst(world, ParticleTypes.TOTEM_OF_UNDYING, core, 60, 0.3, 0.6);
        ParticleUtil.burst(world, ParticleTypes.FLASH, core, 1, 0, 0);
        ParticleUtil.burst(world, ModParticles.RUNE_GLYPH, core, 30, 0.5, 0.08);
        ParticleUtil.ring(world, ParticleUtil.dust(color, 1.5f), center(0.2), 1.2, 40, 0.15, 0.02);
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt != null) {
            bolt.refreshPositionAfterTeleport(Vec3d.ofBottomCenter(pos.up()));
            bolt.setCosmetic(true);
            world.spawnEntity(bolt);
        }
        PlayerEntity nearest = world.getClosestPlayer(core.x, core.y, core.z, 8, false);
        if (nearest != null) {
            TextUtil.chat(nearest, "altar_complete", "weapon", output.getName().getString());
        }
        sync();
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.writeNbt(nbt, lookup);
        for (int i = 0; i < slots.length; i++) {
            writeStack(nbt, "Slot" + i, slots[i], lookup);
        }
        writeStack(nbt, "Output", output, lookup);
        nbt.putLong("RitualStart", ritualStart);
        nbt.putInt("RitualDuration", ritualDuration);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        super.readNbt(nbt, lookup);
        for (int i = 0; i < slots.length; i++) {
            slots[i] = readStack(nbt, "Slot" + i, lookup);
        }
        output = readStack(nbt, "Output", lookup);
        ritualStart = nbt.contains("RitualStart") ? nbt.getLong("RitualStart") : -1;
        ritualDuration = nbt.contains("RitualDuration") ? Math.max(1, nbt.getInt("RitualDuration")) : 80;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 8,
                state -> state.setAndContinue(isRitualActive() ? RITUAL : IDLE)));
    }
}
