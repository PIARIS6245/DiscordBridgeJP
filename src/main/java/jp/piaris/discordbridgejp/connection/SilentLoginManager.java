package jp.piaris.discordbridgejp.connection;

import jp.piaris.discordbridgejp.DiscordBridgeJP;
import jp.piaris.discordbridgejp.link.LinkDatabase;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/**
 * 入退室サイレント判定。
 * - 権限(discordbridgejp.silentjoin/quit、互換でdiscordsrv.*): Discordのみ抑制
 * - 個別指定リスト(player_settings.silent_login): MCチャット+Discord両方抑制
 */
public class SilentLoginManager {

    private final DiscordBridgeJP plugin;
    private final LinkDatabase db;

    public SilentLoginManager(DiscordBridgeJP plugin, LinkDatabase db) {
        this.plugin = plugin;
        this.db = db;
    }

    /** 参加時、Discord通知を抑制すべきか(個別リスト or 権限)。 */
    public boolean isDiscordJoinSilent(Player player) {
        if (isFullSilent(player.getUniqueId())) return true;
        if (player.hasPermission("discordbridgejp.silentjoin")) return true;
        if (plugin.getConfigManager().recognizeLegacyDiscordSrvPermissions()
                && player.hasPermission("discordsrv.silentjoin")) return true;
        return false;
    }

    /** 退出時、Discord通知を抑制すべきか。 */
    public boolean isDiscordQuitSilent(Player player) {
        if (isFullSilent(player.getUniqueId())) return true;
        if (player.hasPermission("discordbridgejp.silentquit")) return true;
        if (plugin.getConfigManager().recognizeLegacyDiscordSrvPermissions()
                && player.hasPermission("discordsrv.silentquit")) return true;
        return false;
    }

    /** 個別指定リストによる完全サイレント(MCチャットも抑制)か。 */
    public boolean isFullSilent(UUID uuid) {
        return db.isSilentLogin(uuid);
    }

    public void add(UUID uuid) {
        db.setSilentLogin(uuid, true);
    }

    public void remove(UUID uuid) {
        db.setSilentLogin(uuid, false);
    }

    public List<UUID> list() {
        return db.getSilentLoginPlayers();
    }
}
