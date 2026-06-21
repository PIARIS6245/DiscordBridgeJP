package jp.piaris.discordbridgejp.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 色名(messages.ymlで使用)および#RRGGBBをRGB intへ解決する。
 */
public final class ColorNames {

    private ColorNames() {
    }

    private static final Map<String, Integer> NAMES = new HashMap<>();

    static {
        // バニラ16色
        NAMES.put("black", 0x000000);
        NAMES.put("dark_blue", 0x0000AA);
        NAMES.put("dark_green", 0x00AA00);
        NAMES.put("dark_aqua", 0x00AAAA);
        NAMES.put("dark_red", 0xAA0000);
        NAMES.put("dark_purple", 0xAA00AA);
        NAMES.put("gold", 0xFFAA00);
        NAMES.put("gray", 0xAAAAAA);
        NAMES.put("dark_gray", 0x555555);
        NAMES.put("blue", 0x5555FF);
        NAMES.put("green", 0x55FF55);
        NAMES.put("aqua", 0x55FFFF);
        NAMES.put("red", 0xFF5555);
        NAMES.put("light_purple", 0xFF55FF);
        NAMES.put("yellow", 0xFFFF55);
        NAMES.put("white", 0xFFFFFF);
        // 追加の一般色
        NAMES.put("lime", 0x00FF00);
        NAMES.put("orange", 0xFFA500);
        NAMES.put("pink", 0xFFC0CB);
        NAMES.put("purple", 0x800080);
        NAMES.put("dark_magenta", 0x8B008B);
    }

    /** 色名 or #RRGGBB を解決。失敗時はdef。 */
    public static int resolve(String value, int def) {
        if (value == null) return def;
        String s = value.trim();
        if (s.isEmpty()) return def;
        if (s.startsWith("#") || s.length() == 6 && s.matches("[0-9a-fA-F]{6}")) {
            return TextUtil.parseHexColor(s, def);
        }
        Integer rgb = NAMES.get(s.toLowerCase());
        return rgb != null ? rgb : def;
    }
}
