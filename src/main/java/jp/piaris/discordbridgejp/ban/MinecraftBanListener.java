package jp.piaris.discordbridgejp.ban;

import jp.piaris.discordbridgejp.DiscordBridgeJP;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;

import java.util.UUID;

/**
 * バニラ/標準Bukkit経由のBANを検知するフォールバック。
 * AdvancedBan導入時はそちらのイベントを優先する(本リスナーは AdvancedBan 不在時のみ有効化)。
 *
 * 注: Bukkitには「BANされた瞬間」の専用イベントが無いため、
 * KICK(ban kick)とBanListのポーリングで補う簡易実装。確実な検知はAdvancedBan側で行う。
 */
public class MinecraftBanListener implements Listener {

    private final DiscordBridgeJP plugin;

    public MinecraftBanListener(DiscordBridgeJP plugin) {
        this.plugin = plugin;
    }

    /**
     * /ban により対象がキックされたタイミングを捕捉する。
     * KICK後にBanListへ登録されているか確認し、登録済みなら同期する。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();
        // 1tick後にBanList登録状況を確認(banコマンドはkick後に登録するケースがある)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                @SuppressWarnings("unchecked")
                BanList<com.destroystokyo.paper.profile.PlayerProfile> banList =
                        (BanList<com.destroystokyo.paper.profile.PlayerProfile>)
                                Bukkit.getBanList(BanList.Type.PROFILE);
                boolean banned = banList.isBanned(op.getPlayerProfile());
                if (banned) {
                    plugin.getBanSyncManager().onMinecraftBan(uuid, "Banned in Minecraft");
                }
            } catch (Throwable t) {
                if (plugin.getConfigManager().isDebug()) {
                    plugin.getLogger().warning("BAN検知(kick)に失敗: " + t.getMessage());
                }
            }
        }, 2L);
    }
}
