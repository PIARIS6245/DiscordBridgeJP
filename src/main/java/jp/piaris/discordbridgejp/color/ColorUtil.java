package jp.piaris.discordbridgejp.color;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * フルカラーRGBを、Minecraftバニラの16色(NamedTextColor)のうち
 * 最も近いものへユークリッド距離で近似する。
 * (頭上ネームタグはTeam機能の都合で16色しか使えないため)
 */
public final class ColorUtil {

    private ColorUtil() {
    }

    private static final NamedTextColor[] PALETTE = {
            NamedTextColor.BLACK, NamedTextColor.DARK_BLUE, NamedTextColor.DARK_GREEN,
            NamedTextColor.DARK_AQUA, NamedTextColor.DARK_RED, NamedTextColor.DARK_PURPLE,
            NamedTextColor.GOLD, NamedTextColor.GRAY, NamedTextColor.DARK_GRAY,
            NamedTextColor.BLUE, NamedTextColor.GREEN, NamedTextColor.AQUA,
            NamedTextColor.RED, NamedTextColor.LIGHT_PURPLE, NamedTextColor.YELLOW,
            NamedTextColor.WHITE
    };

    /** 0xRRGGBB に最も近いNamedTextColorを返す。 */
    public static NamedTextColor nearest(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        NamedTextColor best = NamedTextColor.WHITE;
        long bestDist = Long.MAX_VALUE;
        for (NamedTextColor c : PALETTE) {
            int cr = (c.value() >> 16) & 0xFF;
            int cg = (c.value() >> 8) & 0xFF;
            int cb = c.value() & 0xFF;
            long dr = r - cr, dg = g - cg, db = b - cb;
            long dist = dr * dr + dg * dg + db * db;
            if (dist < bestDist) {
                bestDist = dist;
                best = c;
            }
        }
        return best;
    }
}
