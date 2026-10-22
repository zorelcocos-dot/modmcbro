package com.arcaneforge.client.render;

import com.arcaneforge.config.ConfigManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.List;

/** Low-level drawing helpers for the station renderers (liquids, rings, beams, floating items, holograms). */
@Environment(EnvType.CLIENT)
public final class RenderHelper {
    public static final int FULL_BRIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;

    private RenderHelper() {
    }

    /** Horizontal textured quad (visible from above and below) on a translucent entity layer. */
    public static void horizontalQuad(MatrixStack matrices, VertexConsumer vc, float x1, float z1, float x2, float z2, float y,
                                      int rgb, int alpha, int light, float u1, float v1, float u2, float v2) {
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f m = entry.getPositionMatrix();
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        vc.vertex(m, x1, y, z1).color(r, g, b, alpha).texture(u1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 1, 0);
        vc.vertex(m, x1, y, z2).color(r, g, b, alpha).texture(u1, v2).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 1, 0);
        vc.vertex(m, x2, y, z2).color(r, g, b, alpha).texture(u2, v2).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 1, 0);
        vc.vertex(m, x2, y, z1).color(r, g, b, alpha).texture(u2, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 1, 0);

        vc.vertex(m, x2, y, z1).color(r, g, b, alpha).texture(u2, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, -1, 0);
        vc.vertex(m, x2, y, z2).color(r, g, b, alpha).texture(u2, v2).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, -1, 0);
        vc.vertex(m, x1, y, z2).color(r, g, b, alpha).texture(u1, v2).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, -1, 0);
        vc.vertex(m, x1, y, z1).color(r, g, b, alpha).texture(u1, v1).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, -1, 0);
    }

    /** Scrolling liquid surface using a tintable greyscale texture. */
    public static void liquidSurface(MatrixStack matrices, VertexConsumerProvider buffers, Identifier texture, boolean emissive,
                                     float x1, float z1, float x2, float z2, float y, int rgb, int alpha, int light, float time) {
        VertexConsumer vc = buffers.getBuffer(emissive ? RenderLayer.getEntityTranslucentEmissive(texture)
                : RenderLayer.getEntityTranslucent(texture));
        float scroll = (time * 0.01f) % 1f;
        horizontalQuad(matrices, vc, x1, z1, x2, z2, y, rgb, alpha, emissive ? FULL_BRIGHT : light,
                x1 + scroll, z1, x2 + scroll, z2);
    }

    /** Flat glowing annulus in the XZ plane. */
    public static void ring(MatrixStack matrices, VertexConsumerProvider buffers, float cx, float y, float cz,
                            float radius, float thickness, int segments, int argb) {
        VertexConsumer vc = buffers.getBuffer(RenderLayer.getLightning());
        Matrix4f m = matrices.peek().getPositionMatrix();
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        float inner = Math.max(0f, radius - thickness / 2f);
        float outer = radius + thickness / 2f;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments;
            double a1 = Math.PI * 2 * (i + 1) / segments;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float ox0 = cx + c0 * outer, oz0 = cz + s0 * outer;
            float ox1 = cx + c1 * outer, oz1 = cz + s1 * outer;
            float ix0 = cx + c0 * inner, iz0 = cz + s0 * inner;
            float ix1 = cx + c1 * inner, iz1 = cz + s1 * inner;
            vc.vertex(m, ox0, y, oz0).color(r, g, b, a);
            vc.vertex(m, ox1, y, oz1).color(r, g, b, a);
            vc.vertex(m, ix1, y, iz1).color(r, g, b, a);
            vc.vertex(m, ix0, y, iz0).color(r, g, b, a);
            vc.vertex(m, ix0, y, iz0).color(r, g, b, a);
            vc.vertex(m, ix1, y, iz1).color(r, g, b, a);
            vc.vertex(m, ox1, y, oz1).color(r, g, b, a);
            vc.vertex(m, ox0, y, oz0).color(r, g, b, a);
        }
    }

    /** Glowing beam made of two crossed ribbons between two points (block-local coordinates). */
    public static void beam(MatrixStack matrices, VertexConsumerProvider buffers, Vec3d from, Vec3d to, float width, int argb) {
        Vec3d dir = to.subtract(from);
        if (dir.lengthSquared() < 1.0E-6) {
            return;
        }
        dir = dir.normalize();
        Vec3d up = Math.abs(dir.y) > 0.95 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        Vec3d p1 = dir.crossProduct(up).normalize().multiply(width / 2.0);
        Vec3d p2 = dir.crossProduct(p1).normalize().multiply(width / 2.0);
        VertexConsumer vc = buffers.getBuffer(RenderLayer.getLightning());
        Matrix4f m = matrices.peek().getPositionMatrix();
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        for (Vec3d p : new Vec3d[]{p1, p2}) {
            Vec3d a0 = from.add(p), a1 = from.subtract(p), b0 = to.add(p), b1 = to.subtract(p);
            vertex(vc, m, a0, r, g, b, a);
            vertex(vc, m, b0, r, g, b, a / 3);
            vertex(vc, m, b1, r, g, b, a / 3);
            vertex(vc, m, a1, r, g, b, a);
            vertex(vc, m, a1, r, g, b, a);
            vertex(vc, m, b1, r, g, b, a / 3);
            vertex(vc, m, b0, r, g, b, a / 3);
            vertex(vc, m, a0, r, g, b, a);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Vec3d p, int r, int g, int b, int a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a);
    }

    /** Renders an item spinning around Y at a block-local position. */
    public static void floatingItem(MatrixStack matrices, VertexConsumerProvider buffers, World world, ItemStack stack,
                                    double x, double y, double z, float spinDegrees, float scale, int light, int seed) {
        if (stack.isEmpty()) {
            return;
        }
        matrices.push();
        matrices.translate(x, y, z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spinDegrees));
        matrices.scale(scale, scale, scale);
        MinecraftClient.getInstance().getItemRenderer().renderItem(stack, ModelTransformationMode.FIXED, light,
                OverlayTexture.DEFAULT_UV, matrices, buffers, world, seed);
        matrices.pop();
    }

    /** Rotates local space so that the model's Z axis follows the block facing axis. */
    public static void alignToFacing(MatrixStack matrices, Direction facing) {
        if (facing.getAxis() == Direction.Axis.X) {
            matrices.translate(0.5, 0, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90));
            matrices.translate(-0.5, 0, -0.5);
        }
    }

    public static boolean hologramsVisible(BlockPos pos) {
        if (!ConfigManager.get().general.showHolograms) {
            return false;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) {
            return false;
        }
        double range = ConfigManager.get().general.hologramRange;
        return mc.player.squaredDistanceTo(Vec3d.ofCenter(pos)) <= range * range;
    }

    /** Camera-facing multi-line text centred above the block at block-local height {@code y}. */
    public static void hologram(MatrixStack matrices, VertexConsumerProvider buffers, List<Text> lines, double y, int color) {
        if (lines.isEmpty()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer font = mc.textRenderer;
        matrices.push();
        matrices.translate(0.5, y, 0.5);
        matrices.multiply(mc.getEntityRenderDispatcher().getRotation());
        matrices.scale(0.02f, -0.02f, 0.02f);
        Matrix4f m = matrices.peek().getPositionMatrix();
        int background = (int) (mc.options.getTextBackgroundOpacity(0.35f) * 255f) << 24;
        float lineY = -lines.size() * 10f;
        for (Text line : lines) {
            float x = -font.getWidth(line) / 2f;
            font.draw(line, x, lineY, color, false, m, buffers, TextRenderer.TextLayerType.SEE_THROUGH, background, FULL_BRIGHT);
            font.draw(line, x, lineY, color, false, m, buffers, TextRenderer.TextLayerType.NORMAL, 0, FULL_BRIGHT);
            lineY += 10f;
        }
        matrices.pop();
    }

    /** Single line of floating text with alpha (used for strike ratings). */
    public static void popupText(MatrixStack matrices, VertexConsumerProvider buffers, Text text, double y, int rgb, float alpha, float scale) {
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer font = mc.textRenderer;
        int a = Math.max(4, Math.min(255, (int) (alpha * 255f)));
        matrices.push();
        matrices.translate(0.5, y, 0.5);
        matrices.multiply(mc.getEntityRenderDispatcher().getRotation());
        float s = 0.025f * scale;
        matrices.scale(s, -s, s);
        float x = -font.getWidth(text) / 2f;
        font.draw(text, x, 0, (a << 24) | (rgb & 0xFFFFFF), true, matrices.peek().getPositionMatrix(), buffers,
                TextRenderer.TextLayerType.SEE_THROUGH, 0, FULL_BRIGHT);
        matrices.pop();
    }

    public static float time(World world, float tickDelta) {
        return world == null ? 0f : (world.getTime() % 24000L) + tickDelta;
    }
}
