package com.arcaneforge.client.render;

import com.arcaneforge.block.entity.RuneAltarBlockEntity;
import com.arcaneforge.forge.Rune;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Floats the socketed parts above the altar, orbiting and converging during the ritual with enchanting beams,
 * then presents the awakened weapon spinning at the centre.
 */
@Environment(EnvType.CLIENT)
public class RuneAltarRenderer extends StationRenderer<RuneAltarBlockEntity> {
    private static final double CORE_Y = 1.45;

    public RuneAltarRenderer() {
        super("rune_altar");
    }

    @Override
    protected void renderExtras(RuneAltarBlockEntity be, float tickDelta, MatrixStack matrices, VertexConsumerProvider buffers,
                                int light, int overlay) {
        float time = RenderHelper.time(be.getWorld(), tickDelta);
        long worldTime = be.getWorld() != null ? be.getWorld().getTime() : 0L;
        float progress = be.ritualProgress(worldTime, tickDelta);
        boolean ritual = be.isRitualActive();
        Rune rune = be.socketedRune();
        int color = rune != null ? rune.color() : 0xB070FF;
        int seed = (int) be.getPos().asLong();

        double orbitSpeed = ritual ? 2.0 + progress * 14.0 : 1.2;
        double radius = ritual ? 0.42 * (1 - progress) + 0.04 : 0.42;
        double height = ritual ? 1.05 + progress * 0.45 : 1.05;
        Vec3d core = new Vec3d(0.5, CORE_Y + progress * 0.1, 0.5);

        for (int i = 0; i < 4; i++) {
            ItemStack stack = be.getSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            double angle = Math.toRadians(i * 90 + 45 + time * orbitSpeed);
            double bob = Math.sin(time * 0.1 + i * 1.7) * 0.05;
            double x = 0.5 + Math.cos(angle) * radius;
            double z = 0.5 + Math.sin(angle) * radius;
            double y = height + bob;
            float scale = i == RuneAltarBlockEntity.SLOT_BLADE ? 0.55f : 0.4f;
            RenderHelper.floatingItem(matrices, buffers, be.getWorld(), stack, x, y, z, time * 3f + i * 90, scale,
                    RenderHelper.FULL_BRIGHT, seed + i);
            if (ritual) {
                int alpha = (int) (110 + 120 * progress + Math.sin(time * 0.6 + i) * 25);
                RenderHelper.beam(matrices, buffers, new Vec3d(x, y, z), core, 0.04f + progress * 0.05f,
                        (Math.min(255, Math.max(0, alpha)) << 24) | color);
            }
        }

        if (ritual) {
            float pulse = 0.08f + progress * 0.25f + (float) Math.sin(time * 0.8f) * 0.02f;
            RenderHelper.ring(matrices, buffers, 0.5f, 12.2f / 16f, 0.5f, 0.35f + progress * 0.25f, 0.04f, 48,
                    (0xC0 << 24) | color);
            RenderHelper.ring(matrices, buffers, (float) core.x, (float) core.y, (float) core.z, pulse, pulse, 24,
                    (0xA0 << 24) | 0xFFFFFF);
            RenderHelper.beam(matrices, buffers, new Vec3d(0.5, 12f / 16f, 0.5), core, 0.06f + progress * 0.1f,
                    (0x90 << 24) | color);
        }

        ItemStack output = be.getOutput();
        if (!output.isEmpty()) {
            double bob = Math.sin(time * 0.08) * 0.06;
            RenderHelper.floatingItem(matrices, buffers, be.getWorld(), output, 0.5, 1.2 + bob, 0.5, time * 2.5f, 0.9f,
                    RenderHelper.FULL_BRIGHT, seed);
            RenderHelper.ring(matrices, buffers, 0.5f, 12.2f / 16f, 0.5f, 0.3f + (float) Math.sin(time * 0.1) * 0.03f,
                    0.03f, 40, (0x90 << 24) | 0xFFD36E);
        }

        if (RenderHelper.hologramsVisible(be.getPos())) {
            List<Text> lines = new ArrayList<>();
            if (!output.isEmpty()) {
                lines.add(output.getName().copy());
                lines.add(Text.translatable("hologram.arcaneforge.altar.done").formatted(Formatting.GREEN));
            } else if (ritual) {
                lines.add(Text.translatable("hologram.arcaneforge.altar.ritual", (int) (progress * 100)).formatted(Formatting.LIGHT_PURPLE));
            } else if (be.isComplete()) {
                lines.add(Text.translatable("hologram.arcaneforge.altar.ready").formatted(Formatting.LIGHT_PURPLE));
            } else {
                StringJoiner joiner = new StringJoiner(", ");
                for (String key : be.missingSlots()) {
                    joiner.add(Text.translatable("hologram.arcaneforge.slot." + key).getString());
                }
                lines.add(Text.translatable("hologram.arcaneforge.altar.missing", joiner.toString()).formatted(Formatting.GRAY));
            }
            RenderHelper.hologram(matrices, buffers, lines, 2.0, 0xFFFFFFFF);
        }
    }
}
