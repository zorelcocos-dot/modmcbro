package com.arcaneforge.command;

import com.arcaneforge.block.StationBlock;
import com.arcaneforge.block.entity.MoldTableBlockEntity;
import com.arcaneforge.block.entity.QuenchingBarrelBlockEntity;
import com.arcaneforge.config.ConfigManager;
import com.arcaneforge.forge.MetalType;
import com.arcaneforge.forge.Rune;
import com.arcaneforge.forge.WeaponFactory;
import com.arcaneforge.forge.WeaponType;
import com.arcaneforge.registry.ModBlocks;
import com.arcaneforge.registry.ModItems;
import com.arcaneforge.util.ParticleUtil;
import com.arcaneforge.util.TextUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * {@code /arcaneforge give <player> <weapon> [stars] [rune] [metal]}, {@code /arcaneforge reload},
 * {@code /arcaneforge spawnstation}. Requires permission level 2.
 */
public final class ArcaneForgeCommand {
    private static final SuggestionProvider<ServerCommandSource> WEAPONS = (ctx, builder) -> CommandSource.suggestMatching(
            Stream.concat(Arrays.stream(WeaponType.values()).map(WeaponType::id),
                    Stream.of("solaris_greatsword", "voidstep_katana", "thunderlord_warhammer", "frostbite_warscythe", "bloodmoon_daggers")),
            builder);
    private static final SuggestionProvider<ServerCommandSource> RUNES = (ctx, builder) ->
            CommandSource.suggestMatching(Arrays.stream(Rune.values()).map(Rune::id), builder);
    private static final SuggestionProvider<ServerCommandSource> METALS = (ctx, builder) ->
            CommandSource.suggestMatching(Arrays.stream(MetalType.values()).map(MetalType::id), builder);

    private ArcaneForgeCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("arcaneforge")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("give")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .then(CommandManager.argument("weapon", StringArgumentType.word()).suggests(WEAPONS)
                                        .executes(ctx -> give(ctx, 5, null, null))
                                        .then(CommandManager.argument("stars", IntegerArgumentType.integer(1, 5))
                                                .executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "stars"), null, null))
                                                .then(CommandManager.argument("rune", StringArgumentType.word()).suggests(RUNES)
                                                        .executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "stars"),
                                                                StringArgumentType.getString(ctx, "rune"), null))
                                                        .then(CommandManager.argument("metal", StringArgumentType.word()).suggests(METALS)
                                                                .executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "stars"),
                                                                        StringArgumentType.getString(ctx, "rune"),
                                                                        StringArgumentType.getString(ctx, "metal")))))))))
                .then(CommandManager.literal("reload").executes(ArcaneForgeCommand::reload))
                .then(CommandManager.literal("spawnstation").executes(ArcaneForgeCommand::spawnStation)));
    }

    private static int give(CommandContext<ServerCommandSource> ctx, int stars, String runeId, String metalId) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
        String weaponId = StringArgumentType.getString(ctx, "weapon");
        WeaponType type = WeaponType.byId(weaponId);
        if (type == null) {
            source.sendError(TextUtil.prefixed("give_unknown_weapon", "weapon", weaponId));
            return 0;
        }
        Rune rune = type.signatureRune();
        if (runeId != null) {
            rune = Rune.byId(runeId);
            if (rune == null) {
                source.sendError(TextUtil.prefixed("give_unknown_rune", "rune", runeId));
                return 0;
            }
        }
        MetalType metal = MetalType.NETHERITE;
        if (metalId != null) {
            metal = MetalType.byId(metalId);
            if (metal == null) {
                source.sendError(TextUtil.prefixed("give_unknown_metal", "metal", metalId));
                return 0;
            }
        }
        ItemStack weapon = WeaponFactory.createWeapon(type, metal, stars, rune, true);
        String weaponName = weapon.getName().getString();
        target.getInventory().offerOrDrop(weapon);
        target.getWorld().playSound(null, target.getBlockPos(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.4f, 1.2f);
        source.sendFeedback(() -> TextUtil.prefixed("give_success", "weapon", weaponName, "stars", TextUtil.stars(stars),
                "player", target.getName().getString()), true);
        return 1;
    }

    private static int reload(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        long start = System.nanoTime();
        try {
            ConfigManager.reload();
        } catch (Exception e) {
            source.sendError(TextUtil.prefixed("reload_failed", "error", e.getMessage()));
            return 0;
        }
        long ms = (System.nanoTime() - start) / 1_000_000L;
        source.sendFeedback(() -> TextUtil.prefixed("reload_success", "ms", ms), true);
        return 1;
    }

    /**
     * Builds the complete station row in front of the player, facing them:
     * Smeltery, Mold Table, Forge Anvil, Quenching Barrel (pre-filled with water) and Rune Altar.
     */
    private static int spawnStation(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayerOrThrow();
        ServerWorld world = player.getServerWorld();
        Direction forward = player.getHorizontalFacing();
        Direction right = forward.rotateYClockwise();
        BlockPos base = player.getBlockPos().offset(forward, 3);
        Block[] row = {ModBlocks.SMELTERY, ModBlocks.MOLD_TABLE, ModBlocks.FORGE_ANVIL, ModBlocks.QUENCHING_BARREL, ModBlocks.RUNE_ALTAR};

        for (int i = 0; i < row.length; i++) {
            BlockPos pos = base.offset(right, i - 2);
            for (int up = 0; up <= 1; up++) {
                BlockState existing = world.getBlockState(pos.up(up));
                if (existing.getHardness(world, pos.up(up)) < 0) {
                    source.sendError(TextUtil.prefixed("station_blocked"));
                    return 0;
                }
            }
        }
        for (int i = 0; i < row.length; i++) {
            BlockPos pos = base.offset(right, i - 2);
            world.setBlockState(pos.up(), net.minecraft.block.Blocks.AIR.getDefaultState());
            world.setBlockState(pos, row[i].getDefaultState().with(StationBlock.FACING, forward.getOpposite()));
            if (world.getBlockState(pos.down()).isAir()) {
                world.setBlockState(pos.down(), net.minecraft.block.Blocks.POLISHED_DEEPSLATE.getDefaultState());
            }
            Vec3d c = Vec3d.ofCenter(pos);
            ParticleUtil.burst(world, ParticleTypes.CLOUD, c, 10, 0.4, 0.02);
            ParticleUtil.burst(world, ParticleTypes.ENCHANT, c.add(0, 0.6, 0), 20, 0.4, 0.4);
        }
        if (world.getBlockEntity(base.offset(right, 1)) instanceof QuenchingBarrelBlockEntity barrel) {
            barrel.fill(QuenchingBarrelBlockEntity.Liquid.WATER, QuenchingBarrelBlockEntity.MAX_LEVEL);
        }
        if (world.getBlockEntity(base.offset(right, -1)) instanceof MoldTableBlockEntity mold) {
            mold.setMoldType(WeaponType.KATANA);
        }
        world.playSound(null, base, SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.BLOCKS, 0.8f, 0.9f);

        player.getInventory().offerOrDrop(new ItemStack(ModItems.FORGING_HAMMER));
        player.getInventory().offerOrDrop(new ItemStack(Items.IRON_INGOT, 8));
        player.getInventory().offerOrDrop(new ItemStack(Items.COAL, 4));
        player.getInventory().offerOrDrop(new ItemStack(ModItems.HILT));
        player.getInventory().offerOrDrop(new ItemStack(ModItems.POMMEL));
        player.getInventory().offerOrDrop(new ItemStack(ModItems.SHADOW_RUNE));
        source.sendFeedback(() -> TextUtil.prefixed("station_spawned"), false);
        return 1;
    }
}
