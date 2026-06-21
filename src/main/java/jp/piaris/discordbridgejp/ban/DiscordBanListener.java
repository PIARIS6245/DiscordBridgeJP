package jp.piaris.discordbridgejp.ban;

import jp.piaris.discordbridgejp.DiscordBridgeJP;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

/**
 * Discord側のBAN/解除を検知してMCへ同期する(JDAリスナー)。
 * GUILD_MODERATION インテントが必要(BotManagerで有効化済み)。
 */
public class DiscordBanListener extends ListenerAdapter {

    private final DiscordBridgeJP plugin;

    public DiscordBanListener(DiscordBridgeJP plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onGuildBan(@NotNull GuildBanEvent event) {
        if (event.getGuild().getIdLong() != plugin.getConfigManager().getGuildId()) return;
        plugin.getBanSyncManager().onDiscordBan(event.getUser().getId());
    }

    @Override
    public void onGuildUnban(@NotNull GuildUnbanEvent event) {
        if (event.getGuild().getIdLong() != plugin.getConfigManager().getGuildId()) return;
        plugin.getBanSyncManager().onDiscordUnban(event.getUser().getId());
    }
}
