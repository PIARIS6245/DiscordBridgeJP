package jp.piaris.discordbridgejp.ban;

import jp.piaris.discordbridgejp.DiscordBridgeJP;
import me.leoko.advancedban.bukkit.event.PunishmentEvent;
import me.leoko.advancedban.bukkit.event.RevokePunishmentEvent;
import me.leoko.advancedban.utils.Punishment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * AdvancedBanの独自イベントからBAN/IPBAN/解除を検知して同期する。
 * このクラスはAdvancedBan導入時のみ登録される(未導入環境ではロードされない)。
 */
public class AdvancedBanListener implements Listener {

    private final DiscordBridgeJP plugin;

    public AdvancedBanListener(DiscordBridgeJP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPunishment(PunishmentEvent event) {
        Punishment punishment = event.getPunishment();
        if (punishment == null) return;
        if (!isBanType(punishment)) return;

        UUID uuid = parseUuid(punishment.getUuid());
        if (uuid == null) return;
        String reason = safeReason(punishment);
        plugin.getBanSyncManager().onMinecraftBan(uuid, reason);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRevoke(RevokePunishmentEvent event) {
        Punishment punishment = event.getPunishment();
        if (punishment == null) return;
        if (!isBanType(punishment)) return;

        UUID uuid = parseUuid(punishment.getUuid());
        if (uuid == null) return;
        plugin.getBanSyncManager().onMinecraftUnban(uuid);
    }

    /** BAN/IPBAN/TEMP_BAN/TEMP_IP_BAN のみ対象(MUTE/WARN等は無視)。 */
    private boolean isBanType(Punishment punishment) {
        try {
            String type = punishment.getType().name();
            return type.contains("BAN");
        } catch (Throwable t) {
            return false;
        }
    }

    private String safeReason(Punishment punishment) {
        try {
            String r = punishment.getReason();
            return r == null ? "" : r;
        } catch (Throwable t) {
            return "";
        }
    }

    /** AdvancedBanのuuid文字列(ダッシュ無しの場合あり)をUUIDへ。IPなどは無視(null)。 */
    private UUID parseUuid(String raw) {
        if (raw == null) return null;
        try {
            if (raw.length() == 32 && !raw.contains("-")) {
                String dashed = raw.replaceFirst(
                        "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
                return UUID.fromString(dashed);
            }
            return UUID.fromString(raw);
        } catch (Exception e) {
            return null; // IPアドレス等
        }
    }
}
