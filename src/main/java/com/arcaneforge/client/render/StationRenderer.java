package com.arcaneforge.client.render;

import com.arcaneforge.ArcaneForge;
import com.arcaneforge.block.entity.StationBlockEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * GeckoLib renderer for a station with an emissive glowmask layer. Subclasses draw dynamic extras
 * (liquids, floating items, holograms) in block-local space after the animated model.
 */
@Environment(EnvType.CLIENT)
public abstract class StationRenderer<T extends StationBlockEntity> extends GeoBlockRenderer<T> {
    protected StationRenderer(String name) {
        super(new DefaultedBlockGeoModel<>(ArcaneForge.id(name)));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(T entity, float partialTick, MatrixStack matrices, VertexConsumerProvider buffers, int light, int overlay) {
        super.render(entity, partialTick, matrices, buffers, light, overlay);
        matrices.push();
        renderExtras(entity, partialTick, matrices, buffers, light, overlay);
        matrices.pop();
    }

    protected abstract void renderExtras(T entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers,
                                         int light, int overlay);
}
