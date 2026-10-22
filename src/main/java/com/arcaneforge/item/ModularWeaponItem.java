package com.arcaneforge.item;

import com.arcaneforge.ability.AbilityContext;
import com.arcaneforge.client.render.ArcaneWeaponRenderer;
import com.arcaneforge.config.ArcaneForgeConfig;
import com.arcaneforge.config.ConfigManager;
import com.arcaneforge.forge.MetalType;
import com.arcaneforge.forge.Rune;
import com.arcaneforge.forge.WeaponData;
import com.arcaneforge.forge.WeaponType;
import com.arcaneforge.registry.ModComponents;
import com.arcaneforge.util.ParticleUtil;
import com.arcaneforge.util.TextUtil;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

/**
 * Base class of the five flagship weapons. Stats are computed per stack from the configured base values, the
 * forged metal, the star quality and oil tempering. The socketed rune adds an on-hit effect, and when it matches
 * the weapon's signature rune the right-click skill is empowered ("Rune Resonance").
 */
public abstract class ModularWeaponItem extends SwordItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final WeaponType type;
    private final String modelName;
    private final RawAnimation idle;

    protected ModularWeaponItem(Settings settings, WeaponType type, String modelName) {
        super(ToolMaterials.NETHERITE, settings.attributeModifiers(
                SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, (int) Math.round(type.settings().attackDamage - 5), -2.4f)));
        this.type = type;
        this.modelName = modelName;
        this.idle = RawAnimation.begin().thenLoop("animation." + modelName + ".idle");
    }

    public WeaponType getType() {
        return type;
    }

    public String getModelName() {
        return modelName;
    }

    /**
     * Executes the right-click skill on the server.
     *
     * @return false when the skill could not be performed (no cooldown is applied)
     */
    protected abstract boolean useAbility(ServerWorld world, ServerPlayerEntity player, ItemStack stack, AbilityContext ctx);

    /** Hook for weapon-specific passives on every melee hit (server side only). */
    protected void onMeleeHit(ServerWorld world, ItemStack stack, LivingEntity target, PlayerEntity attacker, AbilityContext ctx) {
    }

    // ------------------------------------------------------------------ stats

    /** (Re)computes attribute modifiers and durability for a weapon stack from the current config. */
    public static void applyStats(ItemStack stack) {
        if (!(stack.getItem() instanceof ModularWeaponItem weapon)) {
            return;
        }
        ArcaneForgeConfig.WeaponSettings settings = weapon.type.settings();
        MetalType metal = WeaponData.metal(stack);
        int stars = Math.max(1, WeaponData.quality(stack));
        double damage = settings.attackDamage * metal.settings().damageMultiplier * WeaponData.qualityMultiplier(stars);
        if (WeaponData.tempered(stack)) {
            damage += ConfigManager.get().forging.oilTemperBonus;
        }
        AttributeModifiersComponent modifiers = AttributeModifiersComponent.builder()
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE,
                        new EntityAttributeModifier(BASE_ATTACK_DAMAGE_MODIFIER_ID, Math.max(0.0, damage - 1.0),
                                EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND)
                .add(EntityAttributes.GENERIC_ATTACK_SPEED,
                        new EntityAttributeModifier(BASE_ATTACK_SPEED_MODIFIER_ID, settings.attackSpeedModifier,
                                EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND)
                .build();
        stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, modifiers);
        int durability = Math.max(1, (int) Math.round(settings.durability * metal.settings().durabilityMultiplier));
        stack.set(DataComponentTypes.MAX_DAMAGE, durability);
        if (stack.getDamage() >= durability) {
            stack.setDamage(durability - 1);
        }
        stack.set(ModComponents.STATS_REVISION, ConfigManager.revision());
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!world.isClient) {
            Integer rev = stack.get(ModComponents.STATS_REVISION);
            if (rev == null || rev != ConfigManager.revision()) {
                applyStats(stack);
            }
        }
    }

    // ------------------------------------------------------------------ skill

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (hand != Hand.MAIN_HAND) {
            return TypedActionResult.pass(stack);
        }
        if (user.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.fail(stack);
        }
        if (world instanceof ServerWorld serverWorld && user instanceof ServerPlayerEntity player) {
            AbilityContext ctx = AbilityContext.of(stack, type);
            if (!useAbility(serverWorld, player, stack, ctx)) {
                return TypedActionResult.fail(stack);
            }
            if (ctx.cooldownTicks() > 0) {
                player.getItemCooldownManager().set(this, ctx.cooldownTicks());
            }
            stack.damage(2, player, EquipmentSlot.MAINHAND);
            player.incrementStat(Stats.USED.getOrCreateStat(this));
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    // ------------------------------------------------------------------ melee

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.postHit(stack, target, attacker);
        if (attacker.getWorld() instanceof ServerWorld world && attacker instanceof PlayerEntity player) {
            AbilityContext ctx = AbilityContext.of(stack, type);
            applyRuneEffect(world, ctx.rune(), target, player, ctx);
            onMeleeHit(world, stack, target, player, ctx);
        }
        return result;
    }

    protected void applyRuneEffect(ServerWorld world, Rune rune, LivingEntity target, PlayerEntity attacker, AbilityContext ctx) {
        Vec3d mid = target.getPos().add(0, target.getHeight() * 0.5, 0);
        switch (rune) {
            case FIRE -> {
                target.setOnFireFor(ctx.resonance() ? 6 : 4);
                ParticleUtil.burst(world, ParticleTypes.FLAME, mid, 10, 0.3, 0.05);
            }
            case SHADOW -> {
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 0), attacker);
                if (world.random.nextFloat() < 0.25f) {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0), attacker);
                }
                ParticleUtil.burst(world, ParticleTypes.REVERSE_PORTAL, mid, 14, 0.3, 0.05);
            }
            case STORM -> {
                if (world.random.nextFloat() < (ctx.resonance() ? 0.25f : 0.15f)) {
                    strikeLightning(world, target.getPos(), attacker);
                    if (ConfigManager.get().general.cosmeticLightning) {
                        target.timeUntilRegen = 0;
                        target.damage(world.getDamageSources().lightningBolt(), 3.0f);
                    }
                }
                ParticleUtil.burst(world, ParticleTypes.ELECTRIC_SPARK, mid, 12, 0.3, 0.2);
            }
            case ICE -> {
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, ctx.resonance() ? 1 : 0), attacker);
                target.setFrozenTicks(Math.max(target.getFrozenTicks(), target.getMinFreezeDamageTicks() - 20));
                ParticleUtil.burst(world, ParticleTypes.SNOWFLAKE, mid, 12, 0.3, 0.05);
            }
            case BLOOD -> {
                attacker.heal(ctx.resonance() ? 1.5f : 1.0f);
                ParticleUtil.burst(world, ParticleUtil.dust(0xB00020, 1.0f), mid, 10, 0.3, 0.0);
            }
        }
    }

    protected static void strikeLightning(ServerWorld world, Vec3d pos, PlayerEntity attacker) {
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt == null) {
            return;
        }
        bolt.refreshPositionAfterTeleport(pos);
        bolt.setCosmetic(ConfigManager.get().general.cosmeticLightning);
        if (attacker instanceof ServerPlayerEntity serverPlayer) {
            bolt.setChanneler(serverPlayer);
        }
        world.spawnEntity(bolt);
    }

    // ------------------------------------------------------------------ presentation

    @Override
    public Text getName(ItemStack stack) {
        Rune rune = WeaponData.rune(stack);
        MutableText base = Text.translatable(getTranslationKey());
        if (rune != null && rune != type.signatureRune()) {
            base = Text.translatable("item.arcaneforge.weapon.variant", rune.adjective(), base);
        }
        int color = rune != null ? rune.color() : type.signatureRune().color();
        return base.styled(s -> s.withColor(TextColor.fromRgb(color)));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType tooltipType) {
        int stars = Math.max(1, WeaponData.quality(stack));
        Rune rune = WeaponData.rune(stack);
        if (rune == null) {
            rune = type.signatureRune();
        }
        tooltip.add(Text.translatable("tooltip.arcaneforge.quality", Text.literal(TextUtil.stars(stars)).formatted(Formatting.GOLD))
                .formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.arcaneforge.type", type.displayName()).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.arcaneforge.metal", WeaponData.metal(stack).displayName()).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.arcaneforge.rune", rune.displayName()).formatted(Formatting.GRAY));
        if (WeaponData.tempered(stack)) {
            tooltip.add(Text.translatable("tooltip.arcaneforge.tempered", ConfigManager.get().forging.oilTemperBonus).formatted(Formatting.GOLD));
        }
        tooltip.add(Text.empty());
        tooltip.add(Text.translatable("tooltip.arcaneforge.ability." + type.id()).formatted(Formatting.AQUA));
        if (type == WeaponType.DAGGERS) {
            tooltip.add(Text.translatable("tooltip.arcaneforge.passive.daggers").formatted(Formatting.RED));
        }
        Rune finalRune = rune;
        tooltip.add(Text.translatable("tooltip.arcaneforge.rune_effect." + rune.id())
                .styled(s -> s.withColor(TextColor.fromRgb(finalRune.color()))));
        if (rune == type.signatureRune()) {
            tooltip.add(Text.translatable("tooltip.arcaneforge.resonance").formatted(Formatting.LIGHT_PURPLE, Formatting.ITALIC));
        }
        tooltip.add(Text.translatable("tooltip.arcaneforge.cooldown", String.format("%.1f", type.settings().cooldownTicks / 20.0))
                .formatted(Formatting.DARK_GRAY));
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return WeaponData.quality(stack) >= 5 || super.hasGlint(stack);
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 5, state -> state.setAndContinue(idle)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private ArcaneWeaponRenderer renderer;

            @Override
            public BuiltinModelItemRenderer getGeoItemRenderer() {
                if (renderer == null) {
                    renderer = new ArcaneWeaponRenderer(modelName);
                }
                return renderer;
            }
        });
    }
}
