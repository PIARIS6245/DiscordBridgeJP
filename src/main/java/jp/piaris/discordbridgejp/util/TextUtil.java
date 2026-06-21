package jp.piaris.discordbridgejp.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Map;

/**
 * 文字列ユーティリティ。
 * - &色コード付き文字列 -> Adventure Component
 * - %key% 形式のプレースホルダ置換
 */
public final class TextUtil {

    private TextUtil() {
    }

    private static final LegacyComponentSerializer LEGACY_AMP =
            LegacyComponentSerializer.legacyAmpersand();

    /** "&a..." のような&色コード文字列をComponentへ変換する。 */
    public static Component legacy(String input) {
        if (input == null) return Component.empty();
        return LEGACY_AMP.deserialize(input);
    }

    /** %key% を values の対応する値で置換する。 */
    public static String placeholders(String template, Map<String, String> values) {
        if (template == null) return "";
        String result = template;
        for (Map.Entry<String, String> e : values.entrySet()) {
            result = result.replace("%" + e.getKey() + "%", e.getValue() == null ? "" : e.getValue());
        }
        return result;
    }

    /** 単一プレースホルダの簡易置換。 */
    public static String placeholder(String template, String key, String value) {
        if (template == null) return "";
        return template.replace("%" + key + "%", value == null ? "" : value);
    }

    /** "#RRGGBB" または "RRGGBB" を 0xRRGGBB の int へ。失敗時は引数のデフォルト。 */
    public static int parseHexColor(String hex, int def) {
        if (hex == null) return def;
        String s = hex.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * DiscordのMarkdown特殊文字をエスケープする(MC->Discordでの意図しない装飾を防ぐ)。
     * メンション(@everyone等)の無害化も行う。
     */
    public static String escapeDiscord(String input) {
        if (input == null) return "";
        String escaped = input
                .replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("~", "\\~")
                .replace("`", "\\`")
                .replace(">", "\\>")
                .replace("|", "\\|");
        // @everyone / @here をゼロ幅スペース挿入で無害化
        escaped = escaped.replace("@everyone", "@\u200beveryone")
                .replace("@here", "@\u200bhere");
        return escaped;
    }
}
