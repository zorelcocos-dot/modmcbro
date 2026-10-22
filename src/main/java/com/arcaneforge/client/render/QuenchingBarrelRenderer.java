package com.arcaneforge.client.render;

import com.arcaneforge.ArcaneForge;
import com.arcaneforge.block.entity.QuenchingBarrelBlockEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Draws the water or oil surface at the current level (churning while steaming) and the hologram. */
@Environment(EnvType.CLIENT)
public class QuenchingBarrelRenderer extends StationRenderer<QuenchingBarrelBlockEntity> {
    private static final Identifier LIQUID = ArcaneForge.id("textures/misc/liquid_surface.png");
    private static final int WATER = 0x3F76E4;
    private static final int OIL = 0x9A6418;

    public QuenchingBarrelRenderer() {
        super("quenching_barrel");
    }

    @Override
    protected void renderExtras(QuenchingBarrelBlockEntity be, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers,
                                int light, int overlay) {
        float time = RenderHelper.time(be.getWorld(), tickDelta);
        boolean steaming = be.getWorld() != null && be.isSteaming(be.getWorld().getTime());
        if (be.getLevel() > 0) {
            boolean oil = be.getLiquid() == QuenchingBarrelBlockEntity.Liquid.OIL;
            float fill = (float) be.getLevel() / QuenchingBarrelBlockEntity.MAX_LEVEL;
            float churn = steaming ? (float) Math.sin(time * 1.3f) * 0.012f : (float) Math.sin(time * 0.1f) * 0.002f;
            float y = (2.2f + 10.3f * fill) / 16f + churn;
            RenderHelper.liquidSurface(matrices, buffers, LIQUID, false, 3f / 16f, 3f / 16f, 13f / 16f, 13f / 16f, y,
                    oil ? OIL : WATER, oil ? 235 : 190, light, steaming ? time * 6f : time);
        }
        if (RenderHelper.hologramsVisible(be.getPos())) {
            List<Text> lines = new ArrayList<>();
            switch (be.getLiquid()) {
                case WATER -> lines.add(Text.translatable("hologram.arcaneforge.barrel.water", be.getLevel(),
                        QuenchingBarrelBlockEntity.MAX_LEVEL).formatted(Formatting.AQUA));
                case OIL -> lines.add(Text.translatable("hologram.arcaneforge.barrel.oil", be.getLevel(),
                        QuenchingBarrelBlockEntity.MAX_LEVEL).formatted(Formatting.GOLD));
                default -> lines.add(Text.translatable("hologram.arcaneforge.barrel.empty").formatted(Formatting.GRAY));
            }
            RenderHelper.hologram(matrices, buffers, lines, 1.35, 0xFFFFFFFF);
        }
    }
}
