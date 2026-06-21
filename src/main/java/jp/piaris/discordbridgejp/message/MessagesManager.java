package jp.piaris.discordbridgejp.message;

import jp.piaris.discordbridgejp.DiscordBridgeJP;
import jp.piaris.discordbridgejp.util.ColorNames;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Map;

/**
 * messages.yml(チャット関連の表示設定)の読込とアクセス。
 * lang/*.ymlとは別。多言語切替の対象外で、サーバー主が自分の言語で書く。
 */
public class MessagesManager {

    private final DiscordBridgeJP plugin;
    private final File file;
    private FileConfiguration yaml;

    public MessagesManager(DiscordBridgeJP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "messages.yml");
    }

    public void load() {
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        reload();
        mergeDefaults();
    }

    public void reload() {
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * 同梱の messages.yml(デフォルト値)と既存ファイルを比較し、
     * 既存ファイルに無いキーだけを自動で補完して保存する。
     * 既存の値・コメントは上書きしない。
     */
    public void mergeDefaults() {
        try (java.io.InputStream in = plugin.getResource("messages.yml")) {
            if (in == null) return;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));

            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(key)) continue;
                if (!yaml.contains(key)) {
                    yaml.set(key, defaults.get(key));
                    changed = true;
                }
            }
            if (changed) {
                yaml.save(file);
                plugin.getLogger().info("messages.ymlに新しい設定項目を自動補完しました。");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("messages.ymlの自動補完に失敗しました: " + e.getMessage());
        }
    }

    // ----- Discord -> MC 形式 -----
    public String getDiscordToMinecraftFormat() {
        return yaml.getString("discord-to-minecraft.format", "C");
    }

    // ----- 添付 -----
    public String getAttachmentImageMode() {
        return yaml.getString("attachments.image", "link");
    }

    public String getAttachmentImageText() {
        return yaml.getString("attachments.attachment-image-text", "[添付画像]");
    }

    public String getAttachmentFileText() {
        return yaml.getString("attachments.attachment-file-text", "[添付ファイル]");
    }

    // ----- プレイヤー通知 -----
    public boolean isNotifyEnabled(String key) {
        return yaml.getBoolean("notifications." + key + ".enabled", true);
    }

    public boolean isNotifyEmbed(String key) {
        return yaml.getBoolean("notifications." + key + ".embed", true);
    }

    public int getNotifyColor(String key, int def) {
        return ColorNames.resolve(yaml.getString("notifications." + key + ".embed-color"), def);
    }

    public String getNotifyMessage(String key, Map<String, String> placeholders) {
        String raw = yaml.getString("notifications." + key + ".message", "");
        return apply(raw, placeholders);
    }

    // ----- サーバー通知 -----
    public boolean isServerEnabled(String key) {
        return yaml.getBoolean("server." + key + ".enabled", true);
    }

    public boolean isServerEmbed(String key) {
        return yaml.getBoolean("server." + key + ".embed", true);
    }

    public int getServerColor(String key, int def) {
        return ColorNames.resolve(yaml.getString("server." + key + ".embed-color"), def);
    }

    public String getServerMessage(String key) {
        return yaml.getString("server." + key + ".message", "");
    }

    /** {key} 形式のプレースホルダを置換。 */
    private String apply(String template, Map<String, String> placeholders) {
        if (template == null) return "";
        String result = template;
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                result = result.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }
        return result;
    }
}
