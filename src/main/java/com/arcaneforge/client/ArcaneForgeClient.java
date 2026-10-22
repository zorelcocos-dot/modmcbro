package com.arcaneforge.client;

import com.arcaneforge.ArcaneForge;
import com.arcaneforge.client.particle.ForgeSparkParticle;
import com.arcaneforge.client.particle.RuneGlyphParticle;
import com.arcaneforge.client.particle.SteamPuffParticle;
import com.arcaneforge.client.render.ForgeAnvilRenderer;
import com.arcaneforge.client.render.MoldTableRenderer;
import com.arcaneforge.client.render.QuenchingBarrelRenderer;
import com.arcaneforge.client.render.RuneAltarRenderer;
import com.arcaneforge.client.render.SmelteryRenderer;
import com.arcaneforge.forge.WeaponData;
import com.arcaneforge.network.ScreenShakePayload;
import com.arcaneforge.registry.ModBlockEntities;
import com.arcaneforge.registry.ModItems;
import com.arcaneforge.registry.ModParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.item.Item;

@Environment(EnvType.CLIENT)
public final class ArcaneForgeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BlockEntityRendererFactories.register(ModBlockEntities.SMELTERY, ctx -> new SmelteryRenderer());
        BlockEntityRendererFactories.register(ModBlockEntities.MOLD_TABLE, ctx -> new MoldTableRenderer());
        BlockEntityRendererFactories.register(ModBlockEntities.FORGE_ANVIL, ctx -> new ForgeAnvilRenderer());
        BlockEntityRendererFactories.register(ModBlockEntities.QUENCHING_BARREL, ctx -> new QuenchingBarrelRenderer());
        BlockEntityRendererFactories.register(ModBlockEntities.RUNE_ALTAR, ctx -> new RuneAltarRenderer());

        ParticleFactoryRegistry particles = ParticleFactoryRegistry.getInstance();
        particles.register(ModParticles.FORGE_SPARK, ForgeSparkParticle.Factory::new);
        particles.register(ModParticles.STEAM_PUFF, SteamPuffParticle.Factory::new);
        particles.register(ModParticles.RUNE_GLYPH, RuneGlyphParticle.Factory::new);

        for (Item blade : new Item[]{ModItems.HOT_BLADE, ModItems.COOLED_BLADE}) {
            FabricModelPredicateProviderRegistry.register(blade, ArcaneForge.id("weapon_type"),
                    (stack, world, entity, seed) -> WeaponData.type(stack).ordinal() / 4f);
        }
        ColorProviderRegistry.ITEM.register((stack, tintIndex) ->
                tintIndex == 0 ? 0xFF000000 | WeaponData.metal(stack).metalColor() : 0xFFFFFFFF, ModItems.COOLED_BLADE);

        ClientPlayNetworking.registerGlobalReceiver(ScreenShakePayload.ID,
                (payload, context) -> ScreenShake.start(payload.intensity(), payload.duration()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> ScreenShake.tick());

        ArcaneForge.LOGGER.info("Arcane Forge client ready.");
    }
}
