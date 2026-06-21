package jp.piaris.discordbridgejp.console;

import jp.piaris.discordbridgejp.DiscordBridgeJP;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * サーバーコンソールのログをDiscordのコンソールチャンネルへ中継する。
 * Log4j2のカスタムAppenderで行を収集し、一定間隔でまとめて送信する。
 *
 * console-log-level: "ALL" なら全ログ、"WARN_ERROR" ならWARN/ERRORのみ中継する。
 * ただし、Discordから実行されたコンソールコマンドの直後はレベルに関わらず
 * 一時的にフィルタをバイパスする(コマンドの実行結果を確実に見せるため)。
 */
public class ConsoleRelayManager {

    private final DiscordBridgeJP plugin;
    private final Deque<String> buffer = new ArrayDeque<>();
    private final Object lock = new Object();

    private ConsoleAppender appender;
    private BukkitTask flushTask;
    private boolean active = false;
    private volatile long commandWindowUntil = 0L;

    private static final int MAX_BUFFER_LINES = 500;
    private static final long FLUSH_INTERVAL_TICKS = 40L; // 約2秒
    private static final long COMMAND_WINDOW_MS = 3000L; // コマンド実行後にフィルタをバイパスする時間

    public ConsoleRelayManager(DiscordBridgeJP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (active) return;
        if (plugin.getConfigManager().getConsoleChannelId() == 0) {
            return; // コンソールチャンネル未設定
        }
        try {
            appender = new ConsoleAppender(this);
            appender.start();
            Logger root = (Logger) LogManager.getRootLogger();
            root.addAppender(appender);
            active = true;
        } catch (Throwable t) {
            plugin.getLogger().warning("コンソール中継Appenderの登録に失敗: " + t.getMessage());
            return;
        }

        flushTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin, this::flush, FLUSH_INTERVAL_TICKS, FLUSH_INTERVAL_TICKS);
    }

    public void stop() {
        active = false;
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        if (appender != null) {
            try {
                Logger root = (Logger) LogManager.getRootLogger();
                root.removeAppender(appender);
                appender.stop();
            } catch (Throwable ignored) {
            }
            appender = null;
        }
        synchronized (lock) {
            buffer.clear();
        }
    }

    /** Appenderから1行受け取る。無限ループ防止のフィルタ済み行のみが渡される想定。 */
    public void enqueue(String line) {
        if (!active || line == null || line.isEmpty()) return;
        synchronized (lock) {
            if (buffer.size() >= MAX_BUFFER_LINES) {
                buffer.pollFirst();
            }
            buffer.addLast(line);
        }
    }

    /**
     * このログレベルを中継対象とすべきか判定する。
     * console-log-level=ALL なら常にtrue。WARN_ERRORの場合はWARN以上のみtrue、
     * ただしDiscordコンソールチャンネルからのコマンド実行直後(数秒間)は常にtrue。
     */
    public boolean shouldAccept(Level level) {
        String mode = plugin.getConfigManager().getConsoleLogLevel();
        if (!"WARN_ERROR".equalsIgnoreCase(mode)) {
            return true; // ALL
        }
        if (System.currentTimeMillis() < commandWindowUntil) {
            return true; // コマンド実行直後はバイパス
        }
        return level != null && level.isMoreSpecificThan(Level.WARN);
    }

    /** Discordコンソールチャンネルからコマンドを実行した直後に呼ぶ。一定時間レベルフィルタをバイパスする。 */
    public void markCommandWindow() {
        commandWindowUntil = System.currentTimeMillis() + COMMAND_WINDOW_MS;
    }

    private void flush() {
        if (!plugin.getBotManager().isConnected()) return;
        StringBuilder sb = new StringBuilder();
        synchronized (lock) {
            if (buffer.isEmpty()) return;
            while (!buffer.isEmpty()) {
                String line = buffer.pollFirst();
                // コードブロック内に収めるため1900字目安で切る
                if (sb.length() + line.length() + 1 > 1800) {
                    sendBlock(sb.toString());
                    sb.setLength(0);
                }
                sb.append(line).append("\n");
            }
        }
        if (sb.length() > 0) {
            sendBlock(sb.toString());
        }
    }

    private void sendBlock(String content) {
        if (content.isBlank()) return;
        String wrapped = "```\n" + content + "```";
        plugin.getBotManager().sendToConsoleChannel(wrapped);
    }
}
