package jp.piaris.discordbridgejp.link;

import jp.piaris.discordbridgejp.DiscordBridgeJP;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLiteによる永続化。連携情報・連携コード・プレイヤー単位フラグを扱う。
 * 単一サーバー用途のため接続は1本を保持して同期アクセスする。
 */
public class LinkDatabase {

    private final DiscordBridgeJP plugin;
    private Connection connection;

    public LinkDatabase(DiscordBridgeJP plugin) {
        this.plugin = plugin;
    }

    public synchronized void connect() {
        try {
            // shadeでもSQLiteドライバを確実にロード
            Class.forName("org.sqlite.JDBC");
            File dbFile = new File(plugin.getDataFolder(), "data.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            createTables();
        } catch (Exception e) {
            plugin.getLogger().severe("SQLiteの初期化に失敗しました: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (Statement st = connection.createStatement()) {
            // discord_idにUNIQUE制約を付けない(複数アカウント連携機能のため、
            // 1つのDiscord垢に複数のminecraft_uuidが紐づくケースを許容する)
            st.execute("CREATE TABLE IF NOT EXISTS links (" +
                    "minecraft_uuid TEXT PRIMARY KEY," +
                    "discord_id TEXT," +
                    "linked_at INTEGER)");
            st.execute("CREATE TABLE IF NOT EXISTS link_codes (" +
                    "code TEXT PRIMARY KEY," +
                    "minecraft_uuid TEXT," +
                    "expires_at INTEGER," +
                    "forced INTEGER DEFAULT 0)");
            st.execute("CREATE TABLE IF NOT EXISTS player_settings (" +
                    "minecraft_uuid TEXT PRIMARY KEY," +
                    "name_color_enabled INTEGER DEFAULT 1," +
                    "silent_login INTEGER DEFAULT 0)");
        }
        migrateRemoveDiscordIdUnique();
    }

    /**
     * 旧バージョン(discord_id UNIQUE制約あり)のDBを、複数アカウント連携機能に対応するため
     * UNIQUE制約なしのテーブルへ無破壊で移行する。
     */
    private void migrateRemoveDiscordIdUnique() {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT sql FROM sqlite_master WHERE type='table' AND name='links'")) {
            if (rs.next()) {
                String sql = rs.getString(1);
                if (sql != null && sql.toUpperCase().contains("DISCORD_ID TEXT UNIQUE")) {
                    plugin.getLogger().info("既存DBのlinksテーブルを複数アカウント連携対応形式へ移行します。");
                    try (Statement migrate = connection.createStatement()) {
                        migrate.execute("ALTER TABLE links RENAME TO links_old");
                        migrate.execute("CREATE TABLE links (" +
                                "minecraft_uuid TEXT PRIMARY KEY," +
                                "discord_id TEXT," +
                                "linked_at INTEGER)");
                        migrate.execute("INSERT INTO links SELECT * FROM links_old");
                        migrate.execute("DROP TABLE links_old");
                    }
                    plugin.getLogger().info("移行が完了しました。");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("linksテーブルの移行確認に失敗しました: " + e.getMessage());
        }
    }

    public synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
            connection = null;
        }
    }

    // ===== links =====

    public synchronized void addLink(UUID uuid, String discordId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO links(minecraft_uuid, discord_id, linked_at) VALUES(?,?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, discordId);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logErr("addLink", e);
        }
    }

    public synchronized void removeLink(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM links WHERE minecraft_uuid=?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logErr("removeLink", e);
        }
    }

    public synchronized boolean isLinked(UUID uuid) {
        return getDiscordId(uuid).isPresent();
    }

    public synchronized Optional<String> getDiscordId(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT discord_id FROM links WHERE minecraft_uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            logErr("getDiscordId", e);
        }
        return Optional.empty();
    }

    /** 指定Discord垢に連携している全MC垢(複数アカウント連携対応)。 */
    public synchronized java.util.List<UUID> getMinecraftUuids(String discordId) {
        java.util.List<UUID> list = new java.util.ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT minecraft_uuid FROM links WHERE discord_id=?")) {
            ps.setString(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        list.add(UUID.fromString(rs.getString(1)));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        } catch (Exception e) {
            logErr("getMinecraftUuids", e);
        }
        return list;
    }

    public synchronized Optional<UUID> getMinecraftUuid(String discordId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT minecraft_uuid FROM links WHERE discord_id=?")) {
            ps.setString(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(UUID.fromString(rs.getString(1)));
                }
            }
        } catch (Exception e) {
            logErr("getMinecraftUuid", e);
        }
        return Optional.empty();
    }

    /** 指定Discord垢に現在連携しているMC垢の件数。 */
    public synchronized int countLinksForDiscord(String discordId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM links WHERE discord_id=?")) {
            ps.setString(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            logErr("countLinksForDiscord", e);
        }
        return 0;
    }

    /** Discord垢ごとにグループ化した連携一覧(discord_id -> minecraft_uuidのリスト)。 */
    public synchronized Map<String, java.util.List<String>> getLinksGroupedByDiscord() {
        Map<String, java.util.List<String>> map = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT minecraft_uuid, discord_id FROM links ORDER BY linked_at");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String uuid = rs.getString(1);
                String discordId = rs.getString(2);
                map.computeIfAbsent(discordId, k -> new java.util.ArrayList<>()).add(uuid);
            }
        } catch (SQLException e) {
            logErr("getLinksGroupedByDiscord", e);
        }
        return map;
    }

    /** 連携済み一覧(minecraft_uuid -> discord_id)。 */
    public synchronized Map<String, String> getAllLinks() {
        Map<String, String> map = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT minecraft_uuid, discord_id FROM links");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException e) {
            logErr("getAllLinks", e);
        }
        return map;
    }

    // ===== link_codes =====

    public synchronized void saveCode(String code, UUID uuid, long expiresAt, boolean forced) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO link_codes(code, minecraft_uuid, expires_at, forced) VALUES(?,?,?,?)")) {
            ps.setString(1, code);
            ps.setString(2, uuid.toString());
            ps.setLong(3, expiresAt);
            ps.setInt(4, forced ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            logErr("saveCode", e);
        }
    }

    /** 既存の未失効コードを返す(あれば)。 */
    public synchronized Optional<String> getActiveCode(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT code, expires_at FROM link_codes WHERE minecraft_uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (rs.getLong(2) > System.currentTimeMillis()) {
                        return Optional.of(rs.getString(1));
                    }
                }
            }
        } catch (SQLException e) {
            logErr("getActiveCode", e);
        }
        return Optional.empty();
    }

    /** コードに対応するUUIDを返す(有効期限内のもののみ)。 */
    public synchronized Optional<UUID> consumeCode(String code) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT minecraft_uuid, expires_at FROM link_codes WHERE code=?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long expires = rs.getLong(2);
                    UUID uuid = UUID.fromString(rs.getString(1));
                    if (expires > System.currentTimeMillis()) {
                        deleteCode(code);
                        return Optional.of(uuid);
                    } else {
                        deleteCode(code);
                    }
                }
            }
        } catch (Exception e) {
            logErr("consumeCode", e);
        }
        return Optional.empty();
    }

    public synchronized void deleteCode(String code) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM link_codes WHERE code=?")) {
            ps.setString(1, code);
            ps.executeUpdate();
        } catch (SQLException e) {
            logErr("deleteCode", e);
        }
    }

    public synchronized void purgeExpiredCodes() {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM link_codes WHERE expires_at < ?")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logErr("purgeExpiredCodes", e);
        }
    }

    // ===== player_settings =====

    private synchronized void ensureSettingsRow(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO player_settings(minecraft_uuid) VALUES(?)")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logErr("ensureSettingsRow", e);
        }
    }

    public synchronized boolean isNameColorEnabled(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT name_color_enabled FROM player_settings WHERE minecraft_uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) != 0;
                }
            }
        } catch (SQLException e) {
            logErr("isNameColorEnabled", e);
        }
        return true; // デフォルト有効
    }

    public synchronized void setNameColorEnabled(UUID uuid, boolean enabled) {
        ensureSettingsRow(uuid);
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE player_settings SET name_color_enabled=? WHERE minecraft_uuid=?")) {
            ps.setInt(1, enabled ? 1 : 0);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logErr("setNameColorEnabled", e);
        }
    }

    public synchronized boolean isSilentLogin(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT silent_login FROM player_settings WHERE minecraft_uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) != 0;
                }
            }
        } catch (SQLException e) {
            logErr("isSilentLogin", e);
        }
        return false;
    }

    public synchronized void setSilentLogin(UUID uuid, boolean enabled) {
        ensureSettingsRow(uuid);
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE player_settings SET silent_login=? WHERE minecraft_uuid=?")) {
            ps.setInt(1, enabled ? 1 : 0);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logErr("setSilentLogin", e);
        }
    }

    public synchronized java.util.List<UUID> getSilentLoginPlayers() {
        java.util.List<UUID> list = new java.util.ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT minecraft_uuid FROM player_settings WHERE silent_login=1");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    list.add(UUID.fromString(rs.getString(1)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (SQLException e) {
            logErr("getSilentLoginPlayers", e);
        }
        return list;
    }

    private void logErr(String where, Exception e) {
        plugin.getLogger().warning("DB(" + where + ")でエラー: " + e.getMessage());
    }
}
