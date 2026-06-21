package jp.piaris.discordbridgejp.chat;

import io.papermc.paper.event.player.AsyncChatEvent;
import jp.piaris.discordbridgejp.DiscordBridgeJP;
import jp.piaris.discordbridgejp.api.ChatMessagePreProcessEvent;
import jp.piaris.discordbridgejp.lang.Lang;
import jp.piaris.discordbridgejp.util.TextUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * MCチャットをDiscordへ中継する。
 * 連携済みプレイヤーはWebhookでアバター+名前表示、未連携はBotの通常メッセージ。
 */
public class MinecraftChatListener implements Listener {

    private final DiscordBridgeJP plugin;

    public MinecraftChatListener(DiscordBridgeJP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.getBotManager().isConnected()) return;
        if (plugin.getBotManager().getChatChannel() == null) return;

        Player player = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        // 独自イベントを発火(書き換え/キャンセル可)
        ChatMessagePreProcessEvent pre = new ChatMessagePreProcessEvent(player, message);
        Bukkit.getPluginManager().callEvent(pre);
        if (pre.isCancelled()) return;

        String finalMessage = TextUtil.escapeDiscord(pre.getMessage());
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();

        Optional<String> discordId = plugin.getLinkDatabase().getDiscordId(uuid);
        if (discordId.isPresent()) {
            sendAsLinkedUser(uuid, playerName, discordId.get(), finalMessage);
        } else {
            sendPlain(playerName, finalMessage);
        }
    }

    /**
     * MCチャット欄の表示を <名前> メッセージ 形式にし、連携済みプレイヤーの名前にDiscordロール色を反映する。
     * 山括弧は白、名前のみ色付け(未連携や色なしは白)。
     * name-color.chat が false の場合は何もしない(他チャットプラグインに任せる)。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChatRender(AsyncChatEvent event) {
        if (!plugin.getConfigManager().isNameColorChat()) return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Integer rgb = null;
        if (plugin.getLinkDatabase().isNameColorEnabled(uuid)
                && plugin.getLinkDatabase().isLinked(uuid)) {
            rgb = plugin.getRoleColorManager().getCachedColor(uuid).orElse(null);
        }

        final net.kyori.adventure.text.format.TextColor nameColor =
                rgb != null ? net.kyori.adventure.text.format.TextColor.color(rgb)
                        : net.kyori.adventure.text.format.NamedTextColor.WHITE;
        final net.kyori.adventure.text.format.NamedTextColor white =
                net.kyori.adventure.text.format.NamedTextColor.WHITE;

        event.renderer((source, sourceDisplayName, msg, viewer) ->
                net.kyori.adventure.text.Component.text("<", white)
                        .append(sourceDisplayName.colorIfAbsent(nameColor))
                        .append(net.kyori.adventure.text.Component.text("> ", white))
                        .append(msg));
    }

    /** 連携済み: Discordプロフィール(名前+アバター)を取得してWebhook送信。 */
    private void sendAsLinkedUser(UUID uuid, String mcName, String discordId, String message) {
        Guild guild = plugin.getBotManager().getGuild();
        if (guild == null) {
            debug("guild=null (guild-id設定 or Bot未参加を確認)");
            sendPlain(mcName, message);
            return;
        }
        try {
            guild.retrieveMemberById(discordId).queue(member -> {
                String displayName = member != null ? member.getEffectiveName() : mcName;
                String avatar = member != null ? member.getUser().getEffectiveAvatarUrl() : null;
                plugin.getWebhookManager().sendAsUser(displayName, avatar, message,
                        () -> {
                            debug("Webhook送信失敗によりプレーン表示にフォールバック (discordId=" + discordId + ")");
                            sendPlain(mcName, message);
                        });
            }, err -> {
                debug("retrieveMemberById失敗 (discordId=" + discordId + "): " + err.getMessage());
                sendPlain(mcName, message);
            });
        } catch (Exception e) {
            debug("Webhookルート全体で例外: " + e.getMessage());
            sendPlain(mcName, message);
        }
    }

    private void debug(String msg) {
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[debug][Webhook] " + msg);
        }
    }

    /** 未連携: Botの通常メッセージで送信。 */
    private void sendPlain(String playerName, String message) {
        Map<String, String> ph = new HashMap<>();
        ph.put("player", playerName);
        ph.put("message", message);
        String formatted = Lang.discord("discord.chat.minecraft-to-discord", ph);
        plugin.getBotManager().sendToChatChannel(formatted);
    }
}
