package jp.piaris.discordbridgejp.bot;

import jp.piaris.discordbridgejp.DiscordBridgeJP;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import java.util.ArrayList;
import java.util.List;

/**
 * JDA(Discord Bot)のライフサイクルとDiscordへの送信処理をまとめる。
 */
public class BotManager {

    private final DiscordBridgeJP plugin;
    private final List<ListenerAdapter> listeners = new ArrayList<>();
    private JDA jda;

    public BotManager(DiscordBridgeJP plugin) {
        this.plugin = plugin;
    }

    /** start()前にリスナーを登録しておく。 */
    public void addListener(ListenerAdapter listener) {
        listeners.add(listener);
    }

    /** Botを起動する(再起動時は一度停止してから)。 */
    public boolean start() {
        stop();

        String token = plugin.getConfigManager().getBotToken();
        if (token == null || token.isBlank() || token.equals("YOUR_BOT_TOKEN_HERE")) {
            plugin.getLogger().severe("config.ymlにbot-tokenが設定されていません。Bot機能を無効化します。");
            return false;
        }

        try {
            JDABuilder builder = JDABuilder.createDefault(token,
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.GUILD_MODERATION)
                    .setActivity(Activity.playing("Minecraft"))
                    .setMemberCachePolicy(MemberCachePolicy.NONE)
                    .disableCache(CacheFlag.VOICE_STATE, CacheFlag.ACTIVITY,
                            CacheFlag.CLIENT_STATUS, CacheFlag.EMOJI,
                            CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS,
                            CacheFlag.SOUNDBOARD_SOUNDS);

            for (ListenerAdapter l : listeners) {
                builder.addEventListeners(l);
            }

            jda = builder.build();
            jda.awaitReady();
            plugin.getLogger().info("Discord Botへの接続に成功しました: " + jda.getSelfUser().getName());
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Bot起動に失敗しました: "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
            jda = null;
            return false;
        }
    }

    public void stop() {
        if (jda != null) {
            try {
                jda.shutdownNow();
            } catch (Exception ignored) {
            }
            jda = null;
        }
    }

    public boolean isConnected() {
        return jda != null;
    }

    public JDA getJda() {
        return jda;
    }

    public Guild getGuild() {
        if (jda == null) return null;
        return jda.getGuildById(plugin.getConfigManager().getGuildId());
    }

    public TextChannel getChatChannel() {
        if (jda == null) return null;
        return jda.getTextChannelById(plugin.getConfigManager().getChatChannelId());
    }

    public TextChannel getConsoleChannel() {
        if (jda == null) return null;
        return jda.getTextChannelById(plugin.getConfigManager().getConsoleChannelId());
    }

    public TextChannel getVerifyChannel() {
        if (jda == null) return null;
        return jda.getTextChannelById(plugin.getConfigManager().getVerifyChannelId());
    }

    public TextChannel getCommandLogChannel() {
        if (jda == null) return null;
        long id = plugin.getConfigManager().getCommandLogChannelId();
        if (id == 0) return null;
        return jda.getTextChannelById(id);
    }

    /** チャットチャンネルへプレーンテキスト送信。 */
    public void sendToChatChannel(String message) {
        TextChannel ch = getChatChannel();
        if (ch == null || message == null || message.isBlank()) return;
        sendChunked(ch, message);
    }

    /** コンソールチャンネルへ送信(コードブロック等は呼び出し側で付与)。 */
    public void sendToConsoleChannel(String message) {
        TextChannel ch = getConsoleChannel();
        if (ch == null || message == null || message.isBlank()) return;
        sendChunked(ch, message);
    }

    /** コマンド専用ログチャンネルへ送信。 */
    public void sendToCommandLogChannel(String message) {
        TextChannel ch = getCommandLogChannel();
        if (ch == null || message == null || message.isBlank()) return;
        sendChunked(ch, message);
    }

    /** Discordの2000文字制限に合わせて分割送信。 */
    public void sendChunked(TextChannel channel, String message) {
        if (channel == null || message == null || message.isEmpty()) return;
        final int max = 1900; // 余裕を持たせる
        try {
            if (message.length() <= max) {
                channel.sendMessage(message).queue(null, t -> logSendError(t));
                return;
            }
            int idx = 0;
            while (idx < message.length()) {
                int end = Math.min(idx + max, message.length());
                String part = message.substring(idx, end);
                channel.sendMessage(part).queue(null, t -> logSendError(t));
                idx = end;
            }
        } catch (Exception e) {
            logSendError(e);
        }
    }

    /** サーバー起動/停止などの通知(アイコンなし、descriptionに文言)。blocking=trueでcomplete待ち。 */
    public void sendServerNotification(boolean embed, int rgb, String text, boolean blocking) {
        TextChannel ch = getChatChannel();
        if (ch == null || text == null || text.isBlank()) return;
        try {
            net.dv8tion.jda.api.requests.RestAction<?> action;
            if (embed) {
                net.dv8tion.jda.api.EmbedBuilder eb = new net.dv8tion.jda.api.EmbedBuilder();
                eb.setColor(new java.awt.Color(rgb));
                eb.setDescription(text.length() > 4000 ? text.substring(0, 4000) : text);
                action = ch.sendMessageEmbeds(eb.build());
            } else {
                action = ch.sendMessage("**" + text + "**");
            }
            if (blocking) {
                action.complete();
            } else {
                action.queue(null, t -> logSendError(t));
            }
        } catch (Exception e) {
            logSendError(e);
        }
    }

    /** Embedかプレーンかを切り替えて通知を送る。iconUrlはembed時のみ使用(nullで非表示)。 */
    public void sendNotification(boolean embed, int rgb, String text, String iconUrl) {
        if (text == null || text.isBlank()) return;
        if (embed) {
            sendEmbedToChatChannel(rgb, text, iconUrl);
        } else {
            sendToChatChannel("**" + text + "**");
        }
    }

    /** チャットチャンネルへEmbedを送信(通知用、SRV風)。 */
    public void sendEmbedToChatChannel(int rgb, String authorText, String authorIconUrl) {
        TextChannel ch = getChatChannel();
        if (ch == null || authorText == null) return;
        try {
            net.dv8tion.jda.api.EmbedBuilder eb = new net.dv8tion.jda.api.EmbedBuilder();
            eb.setColor(new java.awt.Color(rgb));
            // authorTextは256文字制限
            String name = authorText.length() > 256 ? authorText.substring(0, 256) : authorText;
            eb.setAuthor(name, null, authorIconUrl);
            ch.sendMessageEmbeds(eb.build()).queue(null, t -> logSendError(t));
        } catch (Exception e) {
            logSendError(e);
        }
    }

    private void logSendError(Throwable t) {
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().warning("Discord送信に失敗: " + t.getMessage());
        }
    }
}
