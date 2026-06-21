package jp.piaris.discordbridgejp.config;

import jp.piaris.discordbridgejp.DiscordBridgeJP;

/**
 * config.ymlへのアクセスをまとめる。値の取得のみを担当し、
 * 変更の即時保存が必要な箇所は setAndSave 系を使う。
 */
public class ConfigManager {

    private final DiscordBridgeJP plugin;

    public ConfigManager(DiscordBridgeJP plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        plugin.reloadConfig();
        mergeDefaults();
    }

    /**
     * 同梱の config.yml(デフォルト値)と既存ファイルを比較し、
     * 既存ファイルに無いキーだけを自動で補完して保存する。
     * 既存の値・コメントは上書きしない(新規追加分にはコメントは付かない点に注意)。
     */
    public void mergeDefaults() {
        try (java.io.InputStream in = plugin.getResource("config.yml")) {
            if (in == null) return;
            org.bukkit.configuration.file.YamlConfiguration defaults =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                            new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));

            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
                if (defaults.isConfigurationSection(key)) continue; // セクション自体はスキップ、葉キーのみ補完
                if (!plugin.getConfig().contains(key)) {
                    plugin.getConfig().set(key, defaults.get(key));
                    changed = true;
                }
            }
            if (changed) {
                plugin.saveConfig();
                plugin.getLogger().info("config.ymlに新しい設定項目を自動補完しました。"
                        + "内容を確認したい場合はconfig.ymlを開いて確認してください。");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("config.ymlの自動補完に失敗しました: " + e.getMessage());
        }
    }

    // ----- Discord接続 -----
    public String getBotToken() {
        return plugin.getConfig().getString("bot-token", "");
    }

    public long getGuildId() {
        return parseId(plugin.getConfig().getString("guild-id"));
    }

    public long getChatChannelId() {
        return parseId(plugin.getConfig().getString("chat-channel-id"));
    }

    public long getConsoleChannelId() {
        return parseId(plugin.getConfig().getString("console-channel-id"));
    }

    public long getVerifyChannelId() {
        return parseId(plugin.getConfig().getString("verify-channel-id"));
    }

    // ----- コンソール連携 -----
    public boolean isConsoleCommandEnabled() {
        return plugin.getConfig().getBoolean("console-command.enabled", false);
    }

    public long getConsoleCommandRoleId() {
        return parseId(plugin.getConfig().getString("console-command.role-id"));
    }

    public String getConsoleLogLevel() {
        return plugin.getConfig().getString("console-log-level", "ALL");
    }

    public long getCommandLogChannelId() {
        return parseId(plugin.getConfig().getString("command-log-channel-id"));
    }

    // ----- 言語 -----
    public String getLanguage() {
        return plugin.getConfig().getString("language", "ja");
    }

    public void setLanguageAndSave(String code) {
        plugin.getConfig().set("language", code);
        plugin.saveConfig();
    }

    // ----- ロール色 -----
    public boolean isNameColorChat() {
        return plugin.getConfig().getBoolean("name-color.chat", true);
    }

    public boolean isNameColorNametag() {
        return plugin.getConfig().getBoolean("name-color.nametag", true);
    }

    public boolean isNameColorTablist() {
        return plugin.getConfig().getBoolean("name-color.tablist", true);
    }

    public int getNameColorCacheSeconds() {
        return plugin.getConfig().getInt("name-color.cache-seconds", 300);
    }

    // ----- チャットコマンド / スティッキー -----
    public boolean isChatCommandEnabled() {
        return plugin.getConfig().getBoolean("chat-command.enabled", true);
    }

    public String getChatCommandPrefix() {
        return plugin.getConfig().getString("chat-command.prefix", "!");
    }

    public boolean isStickyMessageEnabled() {
        return plugin.getConfig().getBoolean("sticky-message.enabled", true);
    }

    // ----- アカウント連携 -----
    public int getCodeExpirySeconds() {
        return plugin.getConfig().getInt("link.code-expiry-seconds", 600);
    }

    public boolean isDeleteCodeMessage() {
        return plugin.getConfig().getBoolean("link.delete-code-message", false);
    }

    public String getUnlinkMode() {
        return plugin.getConfig().getString("link.unlink-mode", "block");
    }

    public boolean isMultiAccountEnabled() {
        return plugin.getConfig().getBoolean("link.multi-account.enabled", false);
    }

    public int getMultiAccountMax() {
        return plugin.getConfig().getInt("link.multi-account.max-per-discord", 2);
    }

    public boolean isRequireLinkForNewPlayers() {
        return plugin.getConfig().getBoolean("require-link-for-new-players", false);
    }

    // ----- メンテナンス -----
    public boolean isMaintenanceMode() {
        return plugin.getConfig().getBoolean("maintenance-mode", false);
    }

    public String getMaintenanceBypassPermission() {
        return plugin.getConfig().getString("maintenance.bypass-permission", "");
    }

    public String getMaintenanceReason() {
        return plugin.getConfig().getString("maintenance.reason", "");
    }

    public void setMaintenanceAndSave(boolean enabled, String reason) {
        plugin.getConfig().set("maintenance-mode", enabled);
        plugin.getConfig().set("maintenance.reason", reason == null ? "" : reason);
        plugin.saveConfig();
    }

    // ----- BAN同期 -----
    public boolean isBanSyncMcToDiscord() {
        return plugin.getConfig().getBoolean("ban-sync.mc-to-discord", true);
    }

    public boolean isBanSyncDiscordToMc() {
        return plugin.getConfig().getBoolean("ban-sync.discord-to-mc", true);
    }

    public boolean isBanSyncUnban() {
        return plugin.getConfig().getBoolean("ban-sync.unban-sync", true);
    }

    // ----- 互換 -----
    public boolean recognizeLegacyDiscordSrvPermissions() {
        return plugin.getConfig().getBoolean("recognize-legacy-discordsrv-permissions", true);
    }

    // ----- デバッグ -----
    public boolean isDebug() {
        return plugin.getConfig().getBoolean("debug", false);
    }

    private long parseId(String raw) {
        if (raw == null) return 0L;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
