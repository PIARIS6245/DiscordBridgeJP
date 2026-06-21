package jp.piaris.discordbridgejp.maintenance;

import jp.piaris.discordbridgejp.DiscordBridgeJP;
import jp.piaris.discordbridgejp.hook.LuckPermsHook;

import java.util.UUID;

/**
 * メンテナンスモードの状態管理とバイパス判定。
 * バイパス: vanilla OP は常に許可。bypass-permission 指定時はLuckPermsで確認(導入時のみ)。
 */
public class MaintenanceManager {

    private final DiscordBridgeJP plugin;

    public MaintenanceManager(DiscordBridgeJP plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfigManager().isMaintenanceMode();
    }

    public String getReason() {
        return plugin.getConfigManager().getMaintenanceReason();
    }

    public void enable(String reason) {
        plugin.getConfigManager().setMaintenanceAndSave(true, reason);
    }

    public void disable() {
        plugin.getConfigManager().setMaintenanceAndSave(false, "");
    }

    /**
     * 指定プレイヤーがメンテナンスをバイパスできるか。
     * @param isOp     vanilla OP かどうか(AsyncPreLoginでは別途判定して渡す)
     */
    public boolean canBypass(UUID uuid, boolean isOp) {
        if (isOp) return true;
        String node = plugin.getConfigManager().getMaintenanceBypassPermission();
        if (node == null || node.isBlank()) return false;
        if (LuckPermsHook.isAvailable() && plugin.getLuckPermsHook() != null) {
            return plugin.getLuckPermsHook().hasPermission(uuid, node);
        }
        return false;
    }
}
