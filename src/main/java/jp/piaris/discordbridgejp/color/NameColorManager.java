package jp.piaris.discordbridgejp.color;

import jp.piaris.discordbridgejp.DiscordBridgeJP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.UUID;

/**
 * 連携済みプレイヤーのDiscordロール色を、頭上ネームタグ(スコアボードTeamのcolor、16色近似)と
 * タブリスト(playerListName、フルカラー)へ反映する。
 *
 * 注: 頭上ネームタグはMinecraftの仕様上Teamの色=バニラ16色しか使えないため近似する。
 * タブリストはplayerListNameでフルカラー指定できる。
 * 他にスコアボードTeamを使うプラグインと競合する可能性がある(その場合はnametagトグルをoff)。
 */
public class NameColorManager {

    private static final String TEAM_PREFIX = "dbjp_";

    private final DiscordBridgeJP plugin;

    public NameColorManager(DiscordBridgeJP plugin) {
        this.plugin = plugin;
    }

    /** 指定プレイヤーのネームタグ/タブリスト色を最新のロール色に更新する(メインスレッドで呼ぶこと)。 */
    public void apply(Player player) {
        if (player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();

        // マスタースイッチ(個別namecolor)と連携状態の確認
        boolean enabled = plugin.getLinkDatabase().isNameColorEnabled(uuid)
                && plugin.getLinkDatabase().isLinked(uuid);

        Integer rgb = enabled ? plugin.getRoleColorManager().getCachedColor(uuid).orElse(null) : null;

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[debug][NameColor] apply(" + player.getName()
                    + ") enabled=" + enabled + " rgb=" + (rgb == null ? "null" : Integer.toHexString(rgb)));
        }

        // --- タブリスト(フルカラー) ---
        if (plugin.getConfigManager().isNameColorTablist() && rgb != null) {
            player.playerListName(Component.text(player.getName()).color(TextColor.color(rgb)));
        } else {
            player.playerListName(null); // デフォルトに戻す
        }

        // --- 頭上ネームタグ(Team色, 16色近似) ---
        try {
            if (plugin.getConfigManager().isNameColorNametag() && rgb != null) {
                NamedTextColor near = ColorUtil.nearest(rgb);
                assignTeam(player, near);
            } else {
                removeFromTeams(player);
            }
        } catch (Throwable t) {
            if (plugin.getConfigManager().isDebug()) {
                plugin.getLogger().warning("ネームタグ色の適用に失敗: " + t.getMessage());
            }
        }
    }

    /** 全オンラインプレイヤーを更新(定期リフレッシュ用)。 */
    public void applyAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            apply(p);
        }
    }

    /** プレイヤーを該当色のTeamに割り当てる。 */
    private void assignTeam(Player player, NamedTextColor color) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = TEAM_PREFIX + color.toString();
        Team team = board.getTeam(teamName);
        if (team == null) {
            team = board.registerNewTeam(teamName);
            team.color(color);
        }
        // 他のdbjpチームから外す
        removeFromTeams(player, teamName);
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
    }

    private void removeFromTeams(Player player) {
        removeFromTeams(player, null);
    }

    private void removeFromTeams(Player player, String keep) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Team team : board.getTeams()) {
            if (!team.getName().startsWith(TEAM_PREFIX)) continue;
            if (keep != null && team.getName().equals(keep)) continue;
            if (team.hasEntry(player.getName())) {
                team.removeEntry(player.getName());
            }
        }
    }

    /** プラグイン無効化時、作成したTeamを掃除する。 */
    public void cleanup() {
        try {
            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            for (Team team : board.getTeams()) {
                if (team.getName().startsWith(TEAM_PREFIX)) {
                    team.unregister();
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
