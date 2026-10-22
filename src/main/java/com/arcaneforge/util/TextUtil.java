package com.arcaneforge.util;

import com.arcaneforge.config.ConfigManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

/** Converts config strings with legacy '&' codes and '&#RRGGBB' hex colors into styled {@link Text}. */
public final class TextUtil {
    private TextUtil() {
    }

    public static MutableText colorize(String input) {
        MutableText result = Text.empty();
        if (input == null || input.isEmpty()) {
            return result;
        }
        Style style = Style.EMPTY;
        StringBuilder buffer = new StringBuilder();
        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < input.length()) {
                char next = input.charAt(i + 1);
                if (next == '#' && i + 8 <= input.length() && isHex(input, i + 2, 6)) {
                    flush(result, buffer, style);
                    int rgb = Integer.parseInt(input.substring(i + 2, i + 8), 16);
                    style = Style.EMPTY.withColor(TextColor.fromRgb(rgb));
                    i += 8;
                    continue;
                }
                Formatting formatting = Formatting.byCode(Character.toLowerCase(next));
                if (formatting != null) {
                    flush(result, buffer, style);
                    if (formatting == Formatting.RESET) {
                        style = Style.EMPTY;
                    } else if (formatting.isColor()) {
                        style = Style.EMPTY.withFormatting(formatting);
                    } else {
                        style = style.withFormatting(formatting);
                    }
                    i += 2;
                    continue;
                }
            }
            buffer.append(c);
            i++;
        }
        flush(result, buffer, style);
        return result;
    }

    private static boolean isHex(String s, int start, int len) {
        for (int i = start; i < start + len; i++) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static void flush(MutableText target, StringBuilder buffer, Style style) {
        if (buffer.length() > 0) {
            target.append(Text.literal(buffer.toString()).setStyle(style));
            buffer.setLength(0);
        }
    }

    /** Looks up a config message and substitutes {key} placeholders from alternating key/value pairs. */
    public static String raw(String key, Object... placeholders) {
        String template = ConfigManager.get().messages.getOrDefault(key, key);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            template = template.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
        }
        return template;
    }

    public static MutableText message(String key, Object... placeholders) {
        return colorize(raw(key, placeholders));
    }

    public static MutableText prefixed(String key, Object... placeholders) {
        return colorize(raw("prefix") + raw(key, placeholders));
    }

    public static void actionBar(PlayerEntity player, String key, Object... placeholders) {
        player.sendMessage(message(key, placeholders), true);
    }

    public static void chat(PlayerEntity player, String key, Object... placeholders) {
        player.sendMessage(prefixed(key, placeholders), false);
    }

    public static String stars(int stars) {
        int s = Math.max(0, Math.min(5, stars));
        return "★".repeat(s) + "☆".repeat(5 - s);
    }
}
