package jp.piaris.discordbridgejp.lang;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import jp.piaris.discordbridgejp.DiscordBridgeJP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 同梱の lang/vanilla/ja_jp.json を読み込み、Discord通知用にComponentを日本語化する。
 *
 * 重要: 以前はAdventureのGlobalTranslatorへ登録していたが、そうすると
 * 「サーバーが全プレイヤーへ送る全メッセージ(バニラの進捗/死亡ブロードキャスト含む)」が
 * 横取りされてしまい、本来クライアント側で行われるはずの翻訳・スタイル継承が壊れる
 * (実機検証で進捗ブロードキャストの色が白色に化ける不具合が発生)。
 * そのため本クラスはグローバル登録せず、DeathListener/AdvancementListenerから
 * 明示的に呼び出される「ローカル変換」専用とする。これによりMC内のバニラ表示には
 * 一切影響を与えない。
 */
public class VanillaLangTranslator {

    private final DiscordBridgeJP plugin;
    private final Map<String, String> jaMap = new HashMap<>();

    public VanillaLangTranslator(DiscordBridgeJP plugin) {
        this.plugin = plugin;
    }

    /** ja_jp.jsonを読み込む(GlobalTranslatorへの登録はしない)。 */
    public void register() {
        jaMap.clear();
        try (InputStream in = plugin.getResource("lang/vanilla/ja_jp.json")) {
            if (in == null) {
                plugin.getLogger().warning("lang/vanilla/ja_jp.json が同梱されていません。"
                        + "進捗/死亡通知は英語のままになります。");
                return;
            }
            Gson gson = new Gson();
            Map<String, Object> raw = gson.fromJson(
                    new InputStreamReader(in, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, Object>>() {}.getType());
            if (raw != null) {
                for (Map.Entry<String, Object> e : raw.entrySet()) {
                    if (e.getValue() instanceof String s) {
                        jaMap.put(e.getKey(), s);
                    }
                }
            }
            plugin.getLogger().info("ja_jp.json を読み込みました(" + jaMap.size() + " エントリ)。");
        } catch (Exception e) {
            plugin.getLogger().warning("ja_jp.json の読み込みに失敗しました: " + e.getMessage());
        }
    }

    /** 互換用(何もしない。以前のGlobalTranslator登録解除に相当する処理は不要)。 */
    public void unregister() {
        // no-op: グローバル登録をしなくなったため何もする必要がない
    }

    /**
     * Componentを再帰的に走査し、ja_jp.jsonで変換できるTranslatableComponentを
     * 日本語の文字列(プレーンテキスト)へ差し替えてレンダリングする。
     * 変換できないキーが残っていた場合は englishFallback を返す。
     */
    public static String renderOrFallback(Component source, Locale locale, String englishFallback) {
        VanillaLangTranslator self = DiscordBridgeJP.getPlugin().getVanillaLangTranslator();
        try {
            Component rendered = self.renderComponent(source, locale);
            if (containsTranslatable(rendered)) {
                return englishFallback;
            }
            return PlainTextComponentSerializer.plainText().serialize(rendered);
        } catch (Throwable t) {
            return englishFallback;
        }
    }

    /** Componentツリーを再帰的に変換する(ローカル処理、GlobalTranslator不使用)。 */
    private Component renderComponent(Component component, Locale locale) {
        if (component instanceof TranslatableComponent tc) {
            Component translated = translateOne(tc, locale);
            if (translated != null) {
                // 子(兄弟として追加されているchildren)があれば引き継ぐ
                if (!component.children().isEmpty()) {
                    List<Component> newChildren = new ArrayList<>();
                    for (Component child : component.children()) {
                        newChildren.add(renderComponent(child, locale));
                    }
                    translated = translated.children(newChildren);
                }
                return translated.style(component.style());
            }
            // 変換できない場合はそのまま返す(英語フォールバック判定用にキーは残る)
            return component;
        }
        if (!component.children().isEmpty()) {
            List<Component> newChildren = new ArrayList<>();
            for (Component child : component.children()) {
                newChildren.add(renderComponent(child, locale));
            }
            return component.children(newChildren);
        }
        return component;
    }

    private boolean isJapanese(Locale locale) {
        return locale != null && "ja".equalsIgnoreCase(locale.getLanguage());
    }

    private Component translateOne(TranslatableComponent component, Locale locale) {
        if (!isJapanese(locale)) return null;
        String pattern = jaMap.get(component.key());
        if (pattern == null) return null;
        List<Component> args = extractArguments(component, locale);
        return buildFromPattern(pattern, args);
    }

    /** TranslatableComponentの引数を取り出し、各引数も再帰的に変換する。 */
    private List<Component> extractArguments(TranslatableComponent component, Locale locale) {
        List<Component> result = new ArrayList<>();
        try {
            for (var arg : component.arguments()) {
                Component c = arg.asComponent();
                result.add(renderComponent(c, locale));
            }
        } catch (Throwable t) {
            try {
                for (Component c : component.args()) {
                    result.add(renderComponent(c, locale));
                }
            } catch (Throwable ignored) {
            }
        }
        return result;
    }

    /**
     * Mojang形式のパターン(%s / %1$s / %%)に引数を埋め込みComponentを構築する。
     * 引数はComponentのままなので、名前色などのスタイルを保持できる。
     */
    private Component buildFromPattern(String pattern, List<Component> args) {
        TextComponent.Builder builder = Component.text();
        StringBuilder literal = new StringBuilder();
        int autoIndex = 0;
        int i = 0;
        int len = pattern.length();
        while (i < len) {
            char ch = pattern.charAt(i);
            if (ch == '%' && i + 1 < len) {
                char next = pattern.charAt(i + 1);
                if (next == '%') {
                    literal.append('%');
                    i += 2;
                    continue;
                }
                if (next == 's') {
                    flushLiteral(builder, literal);
                    appendArg(builder, args, autoIndex++);
                    i += 2;
                    continue;
                }
                int j = i + 1;
                int num = 0;
                boolean hasNum = false;
                while (j < len && Character.isDigit(pattern.charAt(j))) {
                    num = num * 10 + (pattern.charAt(j) - '0');
                    hasNum = true;
                    j++;
                }
                if (hasNum && j + 1 < len && pattern.charAt(j) == '$' && pattern.charAt(j + 1) == 's') {
                    flushLiteral(builder, literal);
                    appendArg(builder, args, num - 1);
                    i = j + 2;
                    continue;
                }
            }
            literal.append(ch);
            i++;
        }
        flushLiteral(builder, literal);
        return builder.build();
    }

    private void flushLiteral(TextComponent.Builder builder, StringBuilder literal) {
        if (literal.length() > 0) {
            builder.append(Component.text(literal.toString()));
            literal.setLength(0);
        }
    }

    private void appendArg(TextComponent.Builder builder, List<Component> args, int index) {
        if (index >= 0 && index < args.size()) {
            builder.append(args.get(index));
        }
    }

    /** レンダリング後もTranslatableComponentが残っているか(=未翻訳)を判定。 */
    private static boolean containsTranslatable(Component component) {
        if (component instanceof TranslatableComponent) {
            return true;
        }
        for (Component child : component.children()) {
            if (containsTranslatable(child)) {
                return true;
            }
        }
        return false;
    }
}
