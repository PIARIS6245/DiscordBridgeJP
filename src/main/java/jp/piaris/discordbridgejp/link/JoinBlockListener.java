package jp.piaris.discordbridgejp.link;

import jp.piaris.discordbridgejp.DiscordBridgeJP;
import jp.piaris.discordbridgejp.lang.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ログイン時のブロック判定。
 * 1) メンテナンスモード: バイパス権限/OP以外をKICK
 * 2) 連携必須(require-link-for-new-players): 初参加かつ未連携をコード表示でKICK
 */
public class JoinBlockListener implements Listener {

    private final DiscordBridgeJP plugin;

    public JoinBlockListener(DiscordBridgeJP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(org.bukkit.event.player.AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        boolean isOp = offline.isOp();

        // 1) メンテナンス
        if (plugin.getMaintenanceManager().isEnabled()) {
            if (!plugin.getMaintenanceManager().canBypass(uuid, isOp)) {
                Component kick = buildMaintenanceMessage();
                event.disallow(
                        org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        kick);
                return;
            }
        }

        // 2) 連携必須(初参加のみ)
        if (plugin.getConfigManager().isRequireLinkForNewPlayers()) {
            boolean playedBefore = offline.hasPlayedBefore();
            boolean linked = plugin.getLinkDatabase().isLinked(uuid);
            if (!playedBefore && !linked) {
                String code = plugin.getLinkManager().issueCode(uuid, true);
                Component kick = buildLinkBlockMessage(code);
                event.disallow(
                        org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        kick);
            }
        }
    }

    private Component buildMaintenanceMessage() {
        String reason = plugin.getMaintenanceManager().getReason();
        if (reason != null && !reason.isBlank()) {
            Map<String, String> ph = new HashMap<>();
            ph.put("reason", reason);
            return Lang.mc("mc.maintenance.kick-message-with-reason", ph);
        }
        return Lang.mc("mc.maintenance.kick-message-default");
    }

    private Component buildLinkBlockMessage(String code) {
        String codeText = "!" + code;
        int minutes = Math.max(1, plugin.getConfigManager().getCodeExpirySeconds() / 60);

        Map<String, String> ph = new HashMap<>();
        ph.put("minutes", String.valueOf(minutes));

        Component title = Lang.mc("mc.link.block-title");
        Component instructions = Lang.mc("mc.link.block-instructions", ph);
        Component codeComponent = Component.text(codeText)
                .color(NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.copyToClipboard(codeText))
                .hoverEvent(HoverEvent.showText(Lang.mc("mc.link.code-hint")));

        return Component.empty()
                .append(title)
                .append(Component.newline())
                .append(Component.newline())
                .append(instructions)
                .append(Component.newline())
                .append(Component.newline())
                .append(codeComponent);
    }
}
