package jp.piaris.discordbridgejp.lang;

import jp.piaris.discordbridgejp.DiscordBridgeJP;
import jp.piaris.discordbridgejp.util.TextUtil;
import net.kyori.adventure.text.Component;

import java.util.Map;

/**
 * 言語キー取得の静的ショートカット。
 * mc(...) は &色コードをComponent化、raw(...) は生文字列、
 * discord(...) はDiscord用プレーンテキスト。
 */
public final class Lang {

    private Lang() {
    }

    private static LanguageManager manager() {
        return DiscordBridgeJP.getPlugin().getLanguageManager();
    }

    /** 生文字列取得(色コード未変換)。 */
    public static String raw(String key) {
        return manager().get(key);
    }

    /** 生文字列 + プレースホルダ置換。 */
    public static String raw(String key, Map<String, String> placeholders) {
        return TextUtil.placeholders(manager().get(key), placeholders);
    }

    /** mc.* を Component 化(MC送信用)。 */
    public static Component mc(String key) {
        return TextUtil.legacy(manager().get(key));
    }

    public static Component mc(String key, Map<String, String> placeholders) {
        return TextUtil.legacy(TextUtil.placeholders(manager().get(key), placeholders));
    }

    /** discord.* を取得(Discord送信用、プレーン文字列)。 */
    public static String discord(String key) {
        return manager().get(key);
    }

    public static String discord(String key, Map<String, String> placeholders) {
        return TextUtil.placeholders(manager().get(key), placeholders);
    }
}
