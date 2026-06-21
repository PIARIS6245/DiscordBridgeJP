package jp.piaris.discordbridgejp.console;

import jp.piaris.discordbridgejp.DiscordBridgeJP;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * バニラOPを持たない一般プレイヤーが実行したコマンドを、
 * command-log-channel-id 専用チャンネルへ記録する(管理者のコマンドは除外)。
 */
public class CommandLogListener implements Listener {

    private final DiscordBridgeJP plugin;

    public CommandLogListener(DiscordBridgeJP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (plugin.getConfigManager().getCommandLogChannelId() == 0) return;
        if (!plugin.getBotManager().isConnected()) return;

        // バニラOPを持つプレイヤーは記録対象外(管理者除外)
        if (event.getPlayer().isOp()) return;

        String line = "**" + event.getPlayer().getName() + "**: `" + event.getMessage() + "`";
        plugin.getBotManager().sendToCommandLogChannel(line);
    }
}
