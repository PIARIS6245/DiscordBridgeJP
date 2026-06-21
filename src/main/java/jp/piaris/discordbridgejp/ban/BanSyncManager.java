package jp.piaris.discordbridgejp.ban;

import jp.piaris.discordbridgejp.DiscordBridgeJP;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.kyori.adventure.text.Component;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * MC↔Discordのアカウント連携に基づくBAN/解除の双方向同期。
 * 同期によって発火する反対側のイベントで再同期しないよう、短時間のガードを設ける。
 */
public class BanSyncManager {

    private final DiscordBridgeJP plugin;

    // 同期処理中のIDを一時的に記録(反対側イベントの無視用)
    private final Set<String> guardDiscordIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> guardUuids = ConcurrentHashMap.newKeySet();

    public BanSyncManager(DiscordBridgeJP plugin) {
        this.plugin = plugin;
    }

    // ===== MC -> Discord =====

    public void onMinecraftBan(UUID uuid, String reason) {
        if (!plugin.getConfigManager().isBanSyncMcToDiscord()) return;
        if (guardUuids.contains(uuid)) return;

        Optional<String> discordId = plugin.getLinkDatabase().getDiscordId(uuid);
        if (discordId.isEmpty()) return;

        Guild guild = plugin.getBotManager().getGuild();
        if (guild == null) return;

        guardDiscordIds.add(discordId.get());
        try {
            String r = (reason == null || reason.isBlank()) ? "Banned in Minecraft" : reason;
            guild.ban(UserSnowflake.fromId(discordId.get()), 0, TimeUnit.SECONDS)
                    .reason(r)
                    .queue(
                            ok -> releaseDiscordGuard(discordId.get()),
                            err -> {
                                releaseDiscordGuard(discordId.get());
                                logFail("Discord BAN", err);
                            });
        } catch (Exception e) {
            releaseDiscordGuard(discordId.get());
            logFail("Discord BAN", e);
        }
    }

    public void onMinecraftUnban(UUID uuid) {
        if (!plugin.getConfigManager().isBanSyncMcToDiscord()) return;
        if (!plugin.getConfigManager().isBanSyncUnban()) return;
        if (guardUuids.contains(uuid)) return;

        Optional<String> discordId = plugin.getLinkDatabase().getDiscordId(uuid);
        if (discordId.isEmpty()) return;

        Guild guild = plugin.getBotManager().getGuild();
        if (guild == null) return;

        guardDiscordIds.add(discordId.get());
        try {
            guild.unban(UserSnowflake.fromId(discordId.get())).queue(
                    ok -> releaseDiscordGuard(discordId.get()),
                    err -> {
                        releaseDiscordGuard(discordId.get());
                        logFail("Discord 解除", err);
                    });
        } catch (Exception e) {
            releaseDiscordGuard(discordId.get());
            logFail("Discord 解除", e);
        }
    }

    // ===== Discord -> MC =====

    public void onDiscordBan(String discordId) {
        if (!plugin.getConfigManager().isBanSyncDiscordToMc()) return;
        if (guardDiscordIds.contains(discordId)) return;

        // 複数アカウント連携時は紐づく全MC垢をBANする
        java.util.List<UUID> uuids = plugin.getLinkDatabase().getMinecraftUuids(discordId);
        if (uuids.isEmpty()) return;

        for (UUID uuid : uuids) {
            guardUuids.add(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                    @SuppressWarnings("unchecked")
                    BanList<com.destroystokyo.paper.profile.PlayerProfile> banList =
                            (BanList<com.destroystokyo.paper.profile.PlayerProfile>)
                                    Bukkit.getBanList(BanList.Type.PROFILE);
                    banList.addBan(op.getPlayerProfile(), "Banned in Discord",
                            (java.util.Date) null, "DiscordBridgeJP");
                    if (op.isOnline() && op.getPlayer() != null) {
                        op.getPlayer().kick(Component.text("Banned in Discord"));
                    }
                } catch (Throwable t) {
                    logFail("MC BAN", t);
                } finally {
                    releaseUuidGuard(uuid);
                }
            });
        }
    }

    public void onDiscordUnban(String discordId) {
        if (!plugin.getConfigManager().isBanSyncDiscordToMc()) return;
        if (!plugin.getConfigManager().isBanSyncUnban()) return;
        if (guardDiscordIds.contains(discordId)) return;

        java.util.List<UUID> uuids = plugin.getLinkDatabase().getMinecraftUuids(discordId);
        if (uuids.isEmpty()) return;

        for (UUID uuid : uuids) {
            guardUuids.add(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                    @SuppressWarnings("unchecked")
                    BanList<com.destroystokyo.paper.profile.PlayerProfile> banList =
                            (BanList<com.destroystokyo.paper.profile.PlayerProfile>)
                                    Bukkit.getBanList(BanList.Type.PROFILE);
                    banList.pardon(op.getPlayerProfile());
                } catch (Throwable t) {
                    logFail("MC 解除", t);
                } finally {
                    releaseUuidGuard(uuid);
                }
            });
        }
    }

    private void releaseDiscordGuard(String discordId) {
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> guardDiscordIds.remove(discordId), 100L); // 約5秒後
    }

    private void releaseUuidGuard(UUID uuid) {
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> guardUuids.remove(uuid), 100L);
    }

    private void logFail(String what, Throwable t) {
        plugin.getLogger().warning("BAN同期(" + what + ")に失敗: " + t.getMessage());
    }
}
