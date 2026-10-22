package com.arcaneforge.client.render;

import com.arcaneforge.block.entity.ForgeAnvilBlockEntity;
import com.arcaneforge.forge.ForgeRating;
import com.arcaneforge.forge.WeaponData;
import com.arcaneforge.item.ForgingHammerItem;
import com.arcaneforge.util.TextUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the glowing workpiece deforming stage by stage, the hammer-rhythm target ring (only while the local
 * player holds a Forging Hammer), floating strike ratings and the progress hologram.
 */
@Environment(EnvType.CLIENT)
public class ForgeAnvilRenderer extends StationRenderer<ForgeAnvilBlockEntity> {
    private static final float ANVIL_TOP = 11.45f / 16f;
    private static final float RING_Y = 13.5f / 16f;
    private static final float RING_MAX = 0.75f;
    private static final int RATING_TICKS = 24;

    public ForgeAnvilRenderer() {
        super("forge_anvil");
    }

    @Override
    protected void renderExtras(ForgeAnvilBlockEntity be, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers,
                                int light, int overlay) {
        ItemStack work = be.getWorkpiece();
        long worldTime = be.getWorld() != null ? be.getWorld().getTime() : 0L;
        float time = RenderHelper.time(be.getWorld(), tickDelta);

        if (!work.isEmpty()) {
            float progress = be.progress();
            matrices.push();
            RenderHelper.alignToFacing(matrices, be.facing());
            matrices.translate(0.5, ANVIL_TOP + 0.01, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
            float width = 1.3f - 0.35f * progress;
            float length = 0.72f + 0.28f * progress;
            matrices.scale(width, length, 1f);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45));
            matrices.scale(0.62f, 0.62f, 0.62f);
            MinecraftClient.getInstance().getItemRenderer().renderItem(work, ModelTransformationMode.FIXED,
                    RenderHelper.FULL_BRIGHT, OverlayTexture.DEFAULT_UV, matrices, buffers, be.getWorld(),
                    (int) be.getPos().asLong());
            matrices.pop();
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        boolean holdingHammer = mc.player != null && mc.player.getMainHandStack().getItem() instanceof ForgingHammerItem;
        if (be.isWorking() && holdingHammer && mc.player.squaredDistanceTo(be.getPos().toCenterPos()) < 64) {
            float radius = be.ringRadius(worldTime, tickDelta);
            float distance = Math.abs(radius - ForgeAnvilBlockEntity.TARGET_RADIUS);
            float target = ForgeAnvilBlockEntity.TARGET_RADIUS * RING_MAX;
            // Timing windows as soft bands around the target.
            RenderHelper.ring(matrices, buffers, 0.5f, RING_Y - 0.002f, 0.5f, target, be.getOkWindow() * 2 * RING_MAX, 48, 0x2A3FA9FF);
            RenderHelper.ring(matrices, buffers, 0.5f, RING_Y - 0.001f, 0.5f, target, be.getGoodWindow() * 2 * RING_MAX, 48, 0x3A7CFC00);
            RenderHelper.ring(matrices, buffers, 0.5f, RING_Y, 0.5f, target, be.getPerfectWindow() * 2 * RING_MAX, 48, 0x90FFD700);
            int ringColor;
            if (distance <= be.getPerfectWindow()) {
                ringColor = 0xF0FFF4B0;
            } else if (distance <= be.getGoodWindow()) {
                ringColor = 0xE07CFC00;
            } else if (distance <= be.getOkWindow()) {
                ringColor = 0xD07FD8FF;
            } else {
                ringColor = 0xC0FF7A30;
            }
            RenderHelper.ring(matrices, buffers, 0.5f, RING_Y + 0.002f, 0.5f, radius * RING_MAX, 0.035f, 64, ringColor);
            // Rotating rune ticks for extra readability.
            float spin = time * 3f;
            matrices.push();
            matrices.translate(0.5, 0, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
            matrices.translate(-0.5, 0, -0.5);
            for (int i = 0; i < 4; i++) {
                double a = Math.PI / 2 * i;
                float px = 0.5f + (float) Math.cos(a) * radius * RING_MAX;
                float pz = 0.5f + (float) Math.sin(a) * radius * RING_MAX;
                RenderHelper.ring(matrices, buffers, px, RING_Y + 0.003f, pz, 0.025f, 0.03f, 10, ringColor);
            }
            matrices.pop();
        }

        ForgeRating rating = be.getLastRating();
        if (rating != null && be.getWorld() != null) {
            float age = (worldTime - be.getLastRatingTime()) + tickDelta;
            if (age >= 0 && age < RATING_TICKS) {
                float alpha = 1f - age / RATING_TICKS;
                Text label = TextUtil.colorize(TextUtil.raw("rating_" + rating.key()));
                RenderHelper.popupText(matrices, buffers, label, 1.05 + age * 0.02, rating.color(), alpha,
                        rating == ForgeRating.PERFECT ? 1.4f : 1.1f);
            }
        }

        if (RenderHelper.hologramsVisible(be.getPos())) {
            List<Text> lines = new ArrayList<>();
            if (work.isEmpty()) {
                lines.add(Text.translatable("hologram.arcaneforge.anvil.empty").formatted(Formatting.GRAY));
            } else if (be.isFinished()) {
                lines.add(Text.translatable("hologram.arcaneforge.anvil.done").formatted(Formatting.GOLD));
                lines.add(Text.literal(TextUtil.stars(WeaponData.quality(work))).formatted(Formatting.YELLOW));
            } else {
                lines.add(work.getName().copy());
                lines.add(Text.translatable("hologram.arcaneforge.anvil.progress", be.getStrikes(), be.getStrikesRequired())
                        .formatted(Formatting.YELLOW));
            }
            RenderHelper.hologram(matrices, buffers, lines, 1.55, 0xFFFFFFFF);
        }
    }
}
