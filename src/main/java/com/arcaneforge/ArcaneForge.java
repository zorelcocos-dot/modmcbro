package com.arcaneforge;

import com.arcaneforge.ability.SlamTracker;
import com.arcaneforge.command.ArcaneForgeCommand;
import com.arcaneforge.config.ConfigManager;
import com.arcaneforge.network.ModNetworking;
import com.arcaneforge.registry.ModBlockEntities;
import com.arcaneforge.registry.ModBlocks;
import com.arcaneforge.registry.ModComponents;
import com.arcaneforge.registry.ModItemGroups;
import com.arcaneforge.registry.ModItems;
import com.arcaneforge.registry.ModParticles;
import com.arcaneforge.registry.ModSounds;
import com.arcaneforge.util.Scheduler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ArcaneForge implements ModInitializer {
    public static final String MOD_ID = "arcaneforge";
    public static final Logger LOGGER = LoggerFactory.getLogger("Arcane Forge");

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ConfigManager.load();

        ModComponents.init();
        ModSounds.init();
        ModParticles.init();
        ModBlocks.init();
        ModItems.init();
        ModBlockEntities.init();
        ModItemGroups.init();
        ModNetworking.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                ArcaneForgeCommand.register(dispatcher));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Scheduler.tick();
            SlamTracker.tick(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            Scheduler.clear();
            SlamTracker.clear();
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(source.isIn(DamageTypeTags.IS_FALL) && SlamTracker.isProtected(entity)));

        LOGGER.info("Arcane Forge initialized - the forge fires are lit.");
    }
}
