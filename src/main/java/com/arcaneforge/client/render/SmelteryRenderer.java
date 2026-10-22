package com.arcaneforge.client.render;

import com.arcaneforge.ArcaneForge;
import com.arcaneforge.block.entity.SmelteryBlockEntity;
import com.arcaneforge.forge.MetalType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/** Draws the rising molten metal pool, the bobbing ore being melted and the status hologram. */
@Environment(EnvType.CLIENT)
public class SmelteryRenderer extends StationRenderer<SmelteryBlockEntity> {
    public static final Identifier MOLTEN = ArcaneForge.id("textures/misc/molten_metal.png");
    private static final float POOL_MIN = 3f / 16f;
    private static final float POOL_MAX = 13f / 16f;
    private static final float FLOOR = 4.05f / 16f;
    private static final float TOP = 12.5f / 16f;

    public SmelteryRenderer() {
        super("smeltery");
    }

    @Override
    protected void renderExtras(SmelteryBlockEntity be, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers,
                                int light, int overlay) {
        float time = RenderHelper.time(be.getWorld(), tickDelta);
        MetalType metal = be.getMetal();
        int capacity = Math.max(1, be.getCapacity());

        if (metal != null && be.getMolten() > 0) {
            float fill = MathHelper.clamp((float) be.getMolten() / capacity, 0f, 1f);
            float wobble = (float) Math.sin(time * 0.15f) * 0.004f;
            float y = FLOOR + (TOP - FLOOR) * fill + wobble;
            int pulse = 200 + (int) (Math.sin(time * 0.2f) * 40);
            RenderHelper.liquidSurface(matrices, buffers, MOLTEN, true, POOL_MIN, POOL_MIN, POOL_MAX, POOL_MAX, y,
                    metal.moltenColor(), Math.min(255, pulse + 15), light, time);
            // Hot crust layer slightly above for depth.
            RenderHelper.liquidSurface(matrices, buffers, MOLTEN, true, POOL_MIN + 0.02f, POOL_MIN + 0.02f, POOL_MAX - 0.02f,
                    POOL_MAX - 0.02f, y + 0.003f, 0xFFF0B0, 70, light, -time * 1.7f);
        }

        if (metal != null && be.getQueued() > 0) {
            float bob = (float) Math.sin(time * 0.12f) * 0.04f;
            float baseY = metal != null && be.getMolten() > 0
                    ? FLOOR + (TOP - FLOOR) * MathHelper.clamp((float) be.getMolten() / capacity, 0f, 1f) : FLOOR;
            RenderHelper.floatingItem(matrices, buffers, be.getWorld(), new ItemStack(metal.displayItem()), 0.5,
                    baseY + 0.12 + bob, 0.5, time * 2f, 0.35f, RenderHelper.FULL_BRIGHT, (int) be.getPos().asLong());
        }

        if (RenderHelper.hologramsVisible(be.getPos())) {
            List<Text> lines = new ArrayList<>();
            if (metal == null || (be.getMolten() <= 0 && be.getQueued() <= 0)) {
                lines.add(Text.translatable("hologram.arcaneforge.smeltery.empty").formatted(Formatting.GRAY));
            } else {
                lines.add(Text.translatable("hologram.arcaneforge.smeltery.molten", metal.displayName(), be.getMolten(), capacity)
                        .formatted(Formatting.GOLD));
                if (be.getQueued() > 0) {
                    lines.add(Text.translatable("hologram.arcaneforge.smeltery.melting", be.getQueued()).formatted(Formatting.YELLOW));
                }
            }
            if (be.isPouring()) {
                lines.add(Text.translatable("hologram.arcaneforge.smeltery.pouring").formatted(Formatting.RED));
            } else if (be.getFuelTicks() > 0) {
                lines.add(Text.translatable("hologram.arcaneforge.smeltery.fuel", be.getFuelTicks() / 20).formatted(Formatting.GRAY));
            } else if (be.getQueued() > 0) {
                lines.add(Text.translatable("hologram.arcaneforge.smeltery.no_fuel").formatted(Formatting.RED));
            }
            RenderHelper.hologram(matrices, buffers, lines, 1.65, 0xFFFFFFFF);
        }
    }
}
