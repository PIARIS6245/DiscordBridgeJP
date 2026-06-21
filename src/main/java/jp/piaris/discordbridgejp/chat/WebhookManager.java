package jp.piaris.discordbridgejp.chat;

import jp.piaris.discordbridgejp.DiscordBridgeJP;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.WebhookClient;

/**
 * チャット中継チャンネルのWebhookを使い、連携済みプレイヤーの
 * アバター+表示名でメッセージを送る(枠なし表示)。
 *
 * Webhookが使えない場合はBotManager経由の通常送信にフォールバックする。
 */
public class WebhookManager {

    private static final String WEBHOOK_NAME = "MCChatRelay";

    private final DiscordBridgeJP plugin;
    private volatile WebhookClient<?> client;
    private volatile long cachedChannelId = 0;
    private volatile boolean preparing = false;

    public WebhookManager(DiscordBridgeJP plugin) {
        this.plugin = plugin;
    }

    /** チャットチャンネルのWebhookクライアントを準備する(非同期)。 */
    public void prepare() {
        TextChannel channel = plugin.getBotManager().getChatChannel();
        if (channel == null) {
            log("prepare中止: チャットチャンネルが取得できません(chat-channel-id未設定の可能性)");
            return;
        }
        if (client != null && cachedChannelId == channel.getIdLong()) return;
        if (preparing) return; // 多重実行防止(チャットの度に毎回叩かないように)
        preparing = true;

        try {
            channel.retrieveWebhooks().queue(hooks -> {
                Webhook found = hooks.stream()
                        .filter(h -> WEBHOOK_NAME.equals(h.getName()))
                        .findFirst()
                        .orElse(null);
                if (found != null) {
                    log("既存Webhookを使用します: " + found.getId());
                    buildClient(found, channel.getIdLong());
                } else {
                    channel.createWebhook(WEBHOOK_NAME).queue(
                            created -> {
                                log("Webhookを新規作成しました: " + created.getId());
                                buildClient(created, channel.getIdLong());
                            },
                            err -> {
                                logAlways("Webhook作成に失敗(『ウェブフックの管理』権限を確認してください): "
                                        + err.getMessage());
                                preparing = false;
                            });
                }
            }, err -> {
                logAlways("Webhook一覧取得に失敗(『ウェブフックの管理』権限を確認してください): "
                        + err.getMessage());
                preparing = false;
            });
        } catch (Throwable t) {
            logAlways("prepare()で例外: " + t);
            preparing = false;
        }
    }

    private void buildClient(Webhook webhook, long channelId) {
        try {
            this.client = WebhookClient.createClient(webhook.getJDA(), webhook.getUrl());
            this.cachedChannelId = channelId;
            log("Webhookクライアントの準備が完了しました。");
        } catch (Throwable t) {
            // NoSuchMethodError等のErrorも含めて必ず捕捉する
            logAlways("Webhookクライアント構築に失敗(JDA APIの不整合の可能性): " + t);
        } finally {
            preparing = false;
        }
    }

    /**
     * 連携済みプレイヤーとして送信。送信に失敗した場合は onFailure を実行する
     * (呼び出し側で通常メッセージにフォールバックさせるため)。
     */
    public void sendAsUser(String username, String avatarUrl, String content, Runnable onFailure) {
        if (client == null) {
            log("sendAsUser: client未準備のためprepare()を呼びフォールバックします");
            prepare();
            if (onFailure != null) onFailure.run();
            return;
        }
        if (content == null || content.isBlank()) return;
        try {
            var action = client.sendMessage(content);
            action.setUsername(sanitizeUsername(username));
            if (avatarUrl != null) {
                action.setAvatarUrl(avatarUrl);
            }
            action.queue(null, err -> {
                logAlways("Webhookメッセージ送信に失敗: " + err.getMessage());
                if (onFailure != null) onFailure.run();
            });
        } catch (Throwable t) {
            logAlways("Webhookメッセージ送信で例外: " + t);
            if (onFailure != null) onFailure.run();
        }
    }

    /**
     * DiscordのWebhook username制約に合わせて無害化する。
     * "discord"/"clyde" を含む名前、"everyone"/"here" は拒否されるため、
     * 該当語にゼロ幅スペースを挿入してそのままでも弾かれないようにする。
     */
    private String sanitizeUsername(String name) {
        if (name == null || name.isBlank()) return "Player";
        String s = name;
        s = s.replaceAll("(?i)discord", "d\u200biscord");
        s = s.replaceAll("(?i)clyde", "c\u200blyde");
        if (s.equalsIgnoreCase("everyone") || s.equalsIgnoreCase("here")) {
            s = s + "\u200b";
        }
        if (s.length() > 80) {
            s = s.substring(0, 80);
        }
        if (s.isBlank()) return "Player";
        return s;
    }

    /** debug=true の時だけ出る詳細ログ。 */
    private void log(String msg) {
        if (plugin.getConfigManager().isDebug()) {
            plugin.getLogger().info("[Webhook] " + msg);
        }
    }

    /** 設定ミス等、ユーザーが気づくべき失敗は debug 設定に関わらず常に出す。 */
    private void logAlways(String msg) {
        plugin.getLogger().warning("[Webhook] " + msg);
    }

    public void shutdown() {
        client = null;
        preparing = false;
    }
}
