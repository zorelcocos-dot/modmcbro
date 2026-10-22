package com.arcaneforge.client.render;

import com.arcaneforge.block.entity.MoldTableBlockEntity;
import com.arcaneforge.forge.MetalType;
import com.arcaneforge.forge.WeaponFactory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.List;

/** Draws the metal filling the mold channel, solidifying into a glowing blank, plus the hologram. */
@Environment(EnvType.CLIENT)
public class MoldTableRenderer extends StationRenderer<MoldTableBlockEntity> {
    private static final float SURFACE = 12.15f / 16f;

    public MoldTableRenderer() {
        super("mold_table");
    }

    @Override
    protected void renderExtras(MoldTableBlockEntity be, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers,
                                int light, int overlay) {
        float time = RenderHelper.time(be.getWorld(), tickDelta);
        MetalType metal = be.getMetal();
        matrices.push();
        RenderHelper.alignToFacing(matrices, be.facing());
        if (metal != null && be.getFilled() > 0) {
            float fill = MathHelper.clamp((float) be.getFilled() / be.getRequired(), 0f, 1f);
            float halfLen = (6f / 16f) * fill;
            float cool = be.isFull() ? be.coolProgress() : 0f;
            int color = lerpColor(metal.moltenColor(), darken(metal.metalColor()), cool);
            int alpha = (int) (235 - cool * 80);
            RenderHelper.liquidSurface(matrices, buffers, SmelteryRenderer.MOLTEN, cool < 0.95f,
                    0.5f - halfLen, 6.6f / 16f, 0.5f + halfLen, 9.4f / 16f, SURFACE, color, alpha, light, time);
            if (be.isFull()) {
                // The blank emerging from the solidifying metal.
                matrices.push();
                matrices.translate(0.5, SURFACE + 0.01, 0.5);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45));
                float s = 0.55f;
                matrices.scale(s, s, s);
                ItemStack blank = WeaponFactory.hotBlank(be.getMoldType(), metal);
                int itemLight = cool < 1f ? RenderHelper.FULL_BRIGHT : light;
                net.minecraft.client.MinecraftClient.getInstance().getItemRenderer().renderItem(blank,
                        net.minecraft.client.render.model.json.ModelTransformationMode.FIXED, itemLight,
                        net.minecraft.client.render.OverlayTexture.DEFAULT_UV, matrices, buffers, be.getWorld(), (int) be.getPos().asLong());
                matrices.pop();
            }
        }
        matrices.pop();

        if (RenderHelper.hologramsVisible(be.getPos())) {
            List<Text> lines = new ArrayList<>();
            lines.add(Text.translatable("hologram.arcaneforge.mold.type", be.getMoldType().displayName()).formatted(Formatting.AQUA));
            if (be.isReady()) {
                lines.add(Text.translatable("hologram.arcaneforge.mold.ready").formatted(Formatting.GREEN));
            } else if (be.isCooling()) {
                lines.add(Text.translatable("hologram.arcaneforge.mold.cooling", (int) (be.coolProgress() * 100)).formatted(Formatting.YELLOW));
            } else {
                Text metalName = metal != null ? metal.displayName() : Text.literal("-");
                lines.add(Text.translatable("hologram.arcaneforge.mold.fill", metalName, be.getFilled(), be.getRequired())
                        .formatted(Formatting.GOLD));
            }
            RenderHelper.hologram(matrices, buffers, lines, 1.3, 0xFFFFFFFF);
        }
    }

    private static int darken(int rgb) {
        return lerpColor(rgb, 0x202020, 0.35f);
    }

    static int lerpColor(int a, int b, float t) {
        int r = (int) MathHelper.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) MathHelper.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) MathHelper.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }
}
