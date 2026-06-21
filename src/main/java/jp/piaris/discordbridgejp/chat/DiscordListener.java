package jp.piaris.discordbridgejp.chat;

import jp.piaris.discordbridgejp.DiscordBridgeJP;
import jp.piaris.discordbridgejp.lang.Lang;
import jp.piaris.discordbridgejp.link.LinkManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Discordからの受信メッセージを各チャンネルの役割に応じて処理する。
 */
public class DiscordListener extends ListenerAdapter {

    private final DiscordBridgeJP plugin;

    public DiscordListener(DiscordBridgeJP plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (!event.isFromGuild()) return;

        long channelId = event.getChannel().getIdLong();
        String content = event.getMessage().getContentDisplay();

        if (channelId == plugin.getConfigManager().getVerifyChannelId()) {
            handleVerifyChannel(event, content);
            return;
        }
        if (channelId == plugin.getConfigManager().getChatChannelId()) {
            handleChatChannel(event, content);
            return;
        }
        if (channelId == plugin.getConfigManager().getConsoleChannelId()) {
            handleConsoleChannel(event, content);
        }
    }

    // ===== 認証チャンネル =====
    private void handleVerifyChannel(MessageReceivedEvent event, String content) {
        String prefix = plugin.getConfigManager().getChatCommandPrefix();
        String trimmed = content.trim();

        // 管理者向け: !list で連携一覧
        if (trimmed.equalsIgnoreCase(prefix + "list")) {
            handleVerifyListCommand(event);
            return;
        }

        // コード照合
        LinkManager.LinkResult result = plugin.getLinkManager()
                .verifyAndLink(trimmed, event.getAuthor().getId());
        switch (result.status()) {
            case SUCCESS -> {
                event.getMessage().reply(Lang.discord("discord.link.verify-success")).queue();
                if (plugin.getConfigManager().isDeleteCodeMessage()) {
                    event.getMessage().delete().queueAfter(3, java.util.concurrent.TimeUnit.SECONDS,
                            null, err -> {
                            });
                }
            }
            case LIMIT_REACHED ->
                    event.getMessage().reply(Lang.discord("discord.link.limit-reached")).queue();
            case INVALID_CODE -> {
                if (trimmed.startsWith(prefix) && trimmed.length() == 9) {
                    // !XXXXXXXX 形式だが無効
                    event.getMessage().reply(Lang.discord("discord.link.verify-invalid")).queue();
                }
            }
        }
    }

    private void handleVerifyListCommand(MessageReceivedEvent event) {
        Member member = event.getMember();
        if (member == null || !member.hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)) {
            event.getMessage().reply(Lang.discord("discord.chat-command.no-permission")).queue();
            return;
        }
        Map<String, java.util.List<String>> grouped = plugin.getLinkDatabase().getLinksGroupedByDiscord();
        if (grouped.isEmpty()) {
            event.getMessage().reply(Lang.discord("discord.chat-command.links-empty")).queue();
            return;
        }

        int totalAccounts = 0;
        StringBuilder sb = new StringBuilder();
        java.util.List<String> mentionIds = new java.util.ArrayList<>();
        for (Map.Entry<String, java.util.List<String>> e : grouped.entrySet()) {
            String discordId = e.getKey();
            java.util.List<String> uuids = e.getValue();
            totalAccounts += uuids.size();
            sb.append("- ");
            for (int i = 0; i < uuids.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(resolveName(uuids.get(i)));
            }
            sb.append("  <@").append(discordId).append(">\n");
            mentionIds.add(discordId);
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("count", String.valueOf(totalAccounts));
        String header = Lang.discord("discord.chat-command.links-header", ph) + "\n";
        String body = header + sb;

        // <@id> はそのまま見た目はメンション表示にしつつ、実際の通知(ping)は出さない
        TextChannel channel = (TextChannel) event.getChannel();
        for (String chunk : splitForChunks(body, 1900)) {
            channel.sendMessage(chunk)
                    .setAllowedMentions(java.util.Collections.emptyList())
                    .queue(null, err -> {
                    });
        }
    }

    private java.util.List<String> splitForChunks(String text, int max) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        int idx = 0;
        while (idx < text.length()) {
            int end = Math.min(idx + max, text.length());
            parts.add(text.substring(idx, end));
            idx = end;
        }
        if (parts.isEmpty()) parts.add(text);
        return parts;
    }

    private String resolveName(String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            return name != null ? name : uuidStr;
        } catch (Exception e) {
            return uuidStr;
        }
    }

    // ===== チャットチャンネル(Discord->MC + !list + スティッキー) =====
    private void handleChatChannel(MessageReceivedEvent event, String content) {
        String prefix = plugin.getConfigManager().getChatCommandPrefix();

        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[debug] chat-channel受信: author="
                    + event.getAuthor().getName() + " content='" + content + "'"
                    + (content.isBlank() ? " (空=MESSAGE CONTENT INTENT未設定の可能性)" : ""));
        }

        // !list 等のチャットコマンド
        if (plugin.getConfigManager().isChatCommandEnabled()
                && content.trim().equalsIgnoreCase(prefix + "list")) {
            sendOnlineList(event);
            // コマンドはMCへ中継しない
            plugin.getStickyMessageManager().onNewMessage((TextChannel) event.getChannel());
            return;
        }

        boolean hasAttachments = !event.getMessage().getAttachments().isEmpty();
        if (content.isBlank() && !hasAttachments) {
            plugin.getStickyMessageManager().onNewMessage((TextChannel) event.getChannel());
            return;
        }

        // Discord -> MC 中継
        Member member = event.getMember();
        String userName = member != null ? member.getEffectiveName() : event.getAuthor().getName();

        // 添付ファイルの処理(画像=リンクor固定文字、その他=固定文字)
        String relayContent = appendAttachments(event, content);
        if (relayContent.isBlank()) {
            plugin.getStickyMessageManager().onNewMessage((TextChannel) event.getChannel());
            return;
        }
        relayToMinecraft(member, userName, relayContent);

        // スティッキー再送判定
        plugin.getStickyMessageManager().onNewMessage((TextChannel) event.getChannel());
    }

    /** Discordメッセージの添付をテキスト化してcontentの末尾に足す。 */
    private String appendAttachments(MessageReceivedEvent event, String content) {
        var attachments = event.getMessage().getAttachments();
        if (attachments.isEmpty()) return content;

        StringBuilder sb = new StringBuilder(content == null ? "" : content);
        var msg = plugin.getMessagesManager();
        String imageMode = msg.getAttachmentImageMode();
        for (var att : attachments) {
            String piece;
            if (att.isImage()) {
                piece = "link".equalsIgnoreCase(imageMode) ? att.getUrl() : msg.getAttachmentImageText();
            } else {
                piece = msg.getAttachmentFileText();
            }
            if (sb.length() > 0) sb.append(" ");
            sb.append(piece);
        }
        return sb.toString();
    }

    private void relayToMinecraft(Member member, String userName, String content) {
        String format = plugin.getMessagesManager().getDiscordToMinecraftFormat().toUpperCase();

        // Discord最上位ロールの名前と色
        String roleName = null;
        Integer rgb = null;
        if (member != null) {
            var roles = member.getRoles();
            if (!roles.isEmpty()) {
                roleName = roles.get(0).getName(); // JDAは上位ロールが先頭
            }
            java.awt.Color color = member.getColor();
            if (color != null) {
                rgb = color.getRGB() & 0xFFFFFF;
            }
        }

        net.kyori.adventure.text.format.NamedTextColor aqua =
                net.kyori.adventure.text.format.NamedTextColor.AQUA;
        net.kyori.adventure.text.format.NamedTextColor gray =
                net.kyori.adventure.text.format.NamedTextColor.GRAY;
        net.kyori.adventure.text.format.NamedTextColor white =
                net.kyori.adventure.text.format.NamedTextColor.WHITE;

        // 接頭辞 [Discord] または [Discord | ロール名]
        Component prefix = Component.text("[Discord", aqua);
        if (format.equals("B") && roleName != null && !roleName.isBlank()
                && !roleName.equals("@everyone")) {
            prefix = prefix.append(Component.text(" | " + roleName, aqua));
        }
        prefix = prefix.append(Component.text("] ", aqua));

        // 名前(Cのときだけロール色)
        Component nameComp;
        if (format.equals("C") && rgb != null) {
            nameComp = Component.text(userName)
                    .color(net.kyori.adventure.text.format.TextColor.color(rgb));
        } else {
            nameComp = Component.text(userName, white);
        }

        Component out = Component.empty()
                .append(prefix)
                .append(nameComp)
                .append(Component.text(" > ", gray))
                .append(Component.text(content, white));

        final Component finalOut = out;
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(finalOut));
    }

    private void sendOnlineList(MessageReceivedEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            var players = Bukkit.getOnlinePlayers();
            StringBuilder sb = new StringBuilder();
            Map<String, String> ph = new HashMap<>();
            ph.put("count", String.valueOf(players.size()));
            if (players.isEmpty()) {
                sb.append(Lang.discord("discord.chat-command.list-empty"));
            } else {
                sb.append(Lang.discord("discord.chat-command.list-header", ph)).append("\n");
                for (var p : players) {
                    sb.append("- ").append(p.getName()).append("\n");
                }
            }
            plugin.getBotManager().sendChunked((TextChannel) event.getChannel(), sb.toString());
        });
    }

    // ===== コンソールチャンネル(MCコマンド実行) =====
    private void handleConsoleChannel(MessageReceivedEvent event, String content) {
        if (!plugin.getConfigManager().isConsoleCommandEnabled()) return;
        if (content.isBlank()) return;

        Member member = event.getMember();
        long roleId = plugin.getConfigManager().getConsoleCommandRoleId();
        boolean allowed = member != null && member.getRoles().stream()
                .anyMatch(r -> r.getIdLong() == roleId);
        if (!allowed) {
            return;
        }

        final String command = content.startsWith("/") ? content.substring(1) : content;
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                plugin.getConsoleRelayManager().markCommandWindow();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                event.getMessage().addReaction(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("✅")).queue(null, e -> {
                });
            } catch (Exception e) {
                plugin.getLogger().warning("Discordからのコマンド実行に失敗: " + e.getMessage());
            }
        });
    }
}
