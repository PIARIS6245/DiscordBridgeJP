package jp.piaris.discordbridgejp.lang;

import jp.piaris.discordbridgejp.DiscordBridgeJP;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多言語対応の中核。
 * - plugins/DiscordBridgeJP/lang/*.yml を全走査
 * - ja.yml を正本としてキーを比較、欠損は正本の値で補完
 * - 壊れたYAMLはスキップ
 * - 現在の言語のキー->値を保持し、Lang から参照される
 */
public class LanguageManager {

    private final DiscordBridgeJP plugin;
    private final File langDir;

    /** ロード成功した言語コード -> (フルキー -> 値) */
    private final Map<String, Map<String, String>> languages = new LinkedHashMap<>();
    /** 正本(ja)のフルキー一覧 */
    private final List<String> canonicalKeys = new ArrayList<>();

    private String currentLanguage = "ja";

    public LanguageManager(DiscordBridgeJP plugin) {
        this.plugin = plugin;
        this.langDir = new File(plugin.getDataFolder(), "lang");
    }

    /** 同梱のja.yml/en.ymlをlang/へ展開(存在しなければ)。 */
    public void saveDefaults() {
        if (!langDir.exists() && !langDir.mkdirs()) {
            plugin.getLogger().warning("lang フォルダを作成できませんでした。");
        }
        saveIfAbsent("lang/ja.yml");
        saveIfAbsent("lang/en.yml");
    }

    private void saveIfAbsent(String resourcePath) {
        File out = new File(plugin.getDataFolder(), resourcePath);
        if (!out.exists()) {
            try {
                plugin.saveResource(resourcePath, false);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("同梱リソースが見つかりません: " + resourcePath);
            }
        }
    }

    /** lang/ を再走査して全言語を読み込む。 */
    public void reload() {
        languages.clear();
        canonicalKeys.clear();

        // まず正本(ja)を読む。lang/ja.yml が無ければ同梱から。
        Map<String, String> canonical = loadCanonical();
        canonicalKeys.addAll(canonical.keySet());
        languages.put("ja", canonical);

        File[] files = langDir.listFiles((d, name) -> name.toLowerCase().endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                String code = f.getName().substring(0, f.getName().length() - 4).toLowerCase();
                if (code.equals("ja")) continue; // 正本は読込済み
                try {
                    YamlConfiguration yaml = YamlConfiguration.loadConfiguration(f);
                    Map<String, String> map = new LinkedHashMap<>();
                    int missing = 0;
                    boolean hasAny = false;
                    for (String key : canonicalKeys) {
                        if (yaml.contains(key)) {
                            map.put(key, yaml.getString(key));
                            hasAny = true;
                        } else {
                            map.put(key, canonical.get(key)); // 正本で補完
                            missing++;
                        }
                    }
                    if (!hasAny) {
                        plugin.getLogger().warning("言語ファイル " + f.getName()
                                + " に有効なキーがありません。スキップします。");
                        continue;
                    }
                    if (missing > 0) {
                        plugin.getLogger().warning("言語ファイル " + f.getName()
                                + " に " + missing + " 個のキーが欠けています。日本語の値で補完しました。");
                    }
                    languages.put(code, map);
                } catch (Exception e) {
                    plugin.getLogger().warning("言語ファイル " + f.getName()
                            + " の読み込みに失敗しました: " + e.getMessage());
                }
            }
        }

        // 現在の言語を確定
        String desired = plugin.getConfigManager().getLanguage().toLowerCase();
        if (languages.containsKey(desired)) {
            currentLanguage = desired;
        } else {
            plugin.getLogger().warning("language '" + desired + "' が読み込めなかったため ja を使用します。");
            currentLanguage = "ja";
        }
        plugin.getLogger().info("言語ファイルを読み込みました: " + String.join(", ", languages.keySet())
                + " (現在: " + currentLanguage + ")");
    }

    private Map<String, String> loadCanonical() {
        Map<String, String> map = new LinkedHashMap<>();
        File jaFile = new File(langDir, "ja.yml");
        YamlConfiguration yaml;
        if (jaFile.exists()) {
            yaml = YamlConfiguration.loadConfiguration(jaFile);
        } else {
            yaml = new YamlConfiguration();
        }
        // 同梱のja.ymlを必ずマージ(ユーザーのja.ymlにキー欠損があっても埋める)
        try (InputStream in = plugin.getResource("lang/ja.yml")) {
            if (in != null) {
                YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String key : bundled.getKeys(true)) {
                    if (bundled.isString(key)) {
                        // ユーザー側に値があればそちらを優先
                        map.put(key, yaml.isString(key) ? yaml.getString(key) : bundled.getString(key));
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("同梱ja.ymlの読み込みに失敗: " + e.getMessage());
        }
        // 同梱に無いがユーザー側にあるキーも拾う
        for (String key : yaml.getKeys(true)) {
            if (yaml.isString(key) && !map.containsKey(key)) {
                map.put(key, yaml.getString(key));
            }
        }
        return map;
    }

    /** 言語切替。成功すればtrue。 */
    public boolean setLanguage(String code) {
        String lower = code.toLowerCase();
        if (!languages.containsKey(lower)) {
            return false;
        }
        currentLanguage = lower;
        plugin.getConfigManager().setLanguageAndSave(lower);
        return true;
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    public List<String> getAvailableLanguages() {
        return new ArrayList<>(languages.keySet());
    }

    /** 現在の言語でフルキーを引く。無ければja、それも無ければキー自体を返す。 */
    public String get(String key) {
        Map<String, String> map = languages.get(currentLanguage);
        if (map != null && map.containsKey(key)) {
            return map.get(key);
        }
        Map<String, String> ja = languages.get("ja");
        if (ja != null && ja.containsKey(key)) {
            return ja.get(key);
        }
        return key;
    }
}
