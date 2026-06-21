package jp.piaris.discordbridgejp.hook;

import jp.piaris.discordbridgejp.DiscordBridgeJP;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;

import java.util.UUID;

/**
 * LuckPermsのAPI呼び出しを隔離するクラス。
 * LuckPerms未導入の環境ではこのクラスを一切ロードしないことで
 * NoClassDefFoundErrorを避ける(呼び出し側でisAvailable()を先に確認すること)。
 */
public class LuckPermsHook {

    private final DiscordBridgeJP plugin;
    private LuckPerms luckPerms;

    public LuckPermsHook(DiscordBridgeJP plugin) {
        this.plugin = plugin;
    }

    /** LuckPermsが導入され利用可能か。 */
    public static boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("LuckPerms");
    }

    public boolean init() {
        try {
            this.luckPerms = LuckPermsProvider.get();
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("LuckPerms連携の初期化に失敗しました: " + t.getMessage());
            return false;
        }
    }

    /**
     * オフラインのプレイヤーが指定の権限ノードを持つか確認する。
     * 非同期スレッドからの呼び出しを想定(内部でブロックする)。
     */
    public boolean hasPermission(UUID uuid, String node) {
        if (luckPerms == null || node == null || node.isBlank()) {
            return false;
        }
        try {
            User user = luckPerms.getUserManager().getUser(uuid);
            boolean loaded = user != null;
            if (user == null) {
                user = luckPerms.getUserManager().loadUser(uuid).join();
            }
            if (user == null) {
                return false;
            }
            boolean result = user.getCachedData()
                    .getPermissionData(QueryOptions.defaultContextualOptions())
                    .checkPermission(node)
                    .asBoolean();
            if (!loaded) {
                luckPerms.getUserManager().cleanupUser(user);
            }
            return result;
        } catch (Throwable t) {
            plugin.getLogger().warning("LuckPerms権限確認に失敗: " + t.getMessage());
            return false;
        }
    }
}
