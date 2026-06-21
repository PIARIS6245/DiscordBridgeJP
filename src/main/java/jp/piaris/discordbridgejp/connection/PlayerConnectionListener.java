package jp.piaris.discordbridgejp.connection;

import jp.piaris.discordbridgejp.DiscordBridgeJP;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;

/**
 * 参加/退出/初参加をDiscordへ通知(messages.ymlの設定に従う)。
 * 個別サイレント対象はMCチャットメッセージも抑制する。
 * 参加時にロール色(ネームタグ/タブリスト)を適用する。
 */
public class PlayerConnectionListener implements Listener {

    private final DiscordBridgeJP plugin;

    public PlayerConnectionListener(DiscordBridgeJP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        SilentLoginManager silent = plugin.getSilentLoginManager();

        if (silent.isFullSilent(uuid)) {
            event.joinMessage(null);
        }

        // ロール色を取得して即座に反映(固定遅延に頼らずREST完了をコールバックで待つ)
        if (plugin.getLinkDatabase().isLinked(uuid)) {
            plugin.getRoleColorManager().refreshAsync(uuid,
                    () -> plugin.getNameColorManager().apply(player));
        }

        if (!plugin.getBotManager().isConnected()) return;
        if (silent.isDiscordJoinSilent(player)) return;

        String avatar = headUrl(uuid);
        Map<String, String> ph = Map.of("playername", player.getName());

        boolean firstJoin = !player.hasPlayedBefore();
        if (firstJoin && plugin.getMessagesManager().isNotifyEnabled("first-join")) {
            sendNotify("first-join", ph, avatar);
        } else if (plugin.getMessagesManager().isNotifyEnabled("join")) {
            sendNotify("join", ph, avatar);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        SilentLoginManager silent = plugin.getSilentLoginManager();

        if (silent.isFullSilent(uuid)) {
            event.quitMessage(null);
        }

        if (!plugin.getBotManager().isConnected()) return;
        if (silent.isDiscordQuitSilent(player)) return;
        if (!plugin.getMessagesManager().isNotifyEnabled("leave")) return;

        sendNotify("leave", Map.of("playername", player.getName()), headUrl(uuid));
    }

    private void sendNotify(String key, Map<String, String> ph, String avatar) {
        var msg = plugin.getMessagesManager();
        boolean embed = msg.isNotifyEmbed(key);
        int color = msg.getNotifyColor(key, 0x55FF55);
        String text = msg.getNotifyMessage(key, ph);
        plugin.getBotManager().sendNotification(embed, color, text, embed ? avatar : null);
    }

    private String headUrl(UUID uuid) {
        return "https://mc-heads.net/avatar/" + uuid + "/64";
    }

    /** 連携完了時、そのプレイヤーがオンラインなら色を即座に取得・適用する。 */
    @EventHandler
    public void onLinked(jp.piaris.discordbridgejp.api.AccountLinkedEvent event) {
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[debug][NameColor] AccountLinkedEvent発火: uuid="
                    + event.getMinecraftUuid() + " discordId=" + event.getDiscordId());
        }
        var player = Bukkit.getPlayer(event.getMinecraftUuid());
        if (player == null || !player.isOnline()) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().info("[debug][NameColor] プレイヤーがオンラインでないため色反映スキップ");
            }
            return;
        }
        long start = System.currentTimeMillis();
        plugin.getRoleColorManager().refreshAsync(event.getMinecraftUuid(), () -> {
            if (plugin.getConfigManager().isDebug()) {
                long ms = System.currentTimeMillis() - start;
                plugin.getLogger().info("[debug][NameColor] ロール色REST取得完了(" + ms + "ms) → 即座に反映します");
            }
            plugin.getNameColorManager().apply(player);
        });
    }
}
