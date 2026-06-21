package jp.piaris.discordbridgejp.chat;

import jp.piaris.discordbridgejp.DiscordBridgeJP;
import jp.piaris.discordbridgejp.lang.Lang;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * チャットチャンネル最下部に「利用可能コマンド」を常時表示する。
 * 新規メッセージ検知時、スティッキーが最新でなければ旧を消して再送(通知抑制)。
 */
public class StickyMessageManager {

    private final DiscordBridgeJP plugin;
    private final File dataFile;
    private volatile long stickyMessageId = 0;
    private volatile long lastRepostAt = 0;
    private static final long MIN_REPOST_INTERVAL_MS = 10_000L;

    public StickyMessageManager(DiscordBridgeJP plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
        load();
    }

    private void load() {
        if (dataFile.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
            stickyMessageId = yaml.getLong("sticky-message-id", 0);
        }
    }

    private void save() {
        try {
            YamlConfiguration yaml = dataFile.exists()
                    ? YamlConfiguration.loadConfiguration(dataFile)
                    : new YamlConfiguration();
            yaml.set("sticky-message-id", stickyMessageId);
            yaml.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("data.ymlの保存に失敗: " + e.getMessage());
        }
    }

    /** 新規メッセージを受けて、必要ならスティッキーを再送する。 */
    public void onNewMessage(TextChannel channel) {
        if (!plugin.getConfigManager().isStickyMessageEnabled()) return;
        if (channel == null) return;
        long now = System.currentTimeMillis();
        if (now - lastRepostAt < MIN_REPOST_INTERVAL_MS) return;
        lastRepostAt = now;
        repost(channel);
    }

    private void repost(TextChannel channel) {
        String text = Lang.discord("discord.sticky.text");
        // 旧スティッキーを削除
        if (stickyMessageId != 0) {
            try {
                channel.deleteMessageById(stickyMessageId).queue(null, err -> {
                });
            } catch (Exception ignored) {
            }
        }
        // 通知抑制で再送
        try {
            channel.sendMessage(text).setSuppressedNotifications(true).queue(msg -> {
                stickyMessageId = msg.getIdLong();
                save();
            }, err -> {
            });
        } catch (Exception ignored) {
        }
    }
}
