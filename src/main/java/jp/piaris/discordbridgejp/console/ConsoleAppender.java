package jp.piaris.discordbridgejp.console;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.io.Serializable;

/**
 * サーバーログを横取りして ConsoleRelayManager へ渡すLog4j2 Appender。
 * 無限ループを避けるため、JDA/自プラグイン由来のログは中継しない。
 */
public class ConsoleAppender extends AbstractAppender {

    private final ConsoleRelayManager manager;

    protected ConsoleAppender(ConsoleRelayManager manager) {
        super("DiscordBridgeJPConsoleAppender", (Filter) null, null, true, Property.EMPTY_ARRAY);
        this.manager = manager;
    }

    @Override
    public void append(LogEvent event) {
        try {
            String loggerName = event.getLoggerName() == null ? "" : event.getLoggerName();
            // JDA(リロケート後含む)や自分自身のログは中継しない(ループ防止)
            if (loggerName.contains("dv8tion")
                    || loggerName.contains("discordbridgejp")
                    || loggerName.contains("okhttp")
                    || loggerName.contains("WebSocketClient")) {
                return;
            }
            if (!manager.shouldAccept(event.getLevel())) {
                return;
            }
            String formatted = formatLine(event);
            if (formatted != null && !formatted.isBlank()) {
                manager.enqueue(formatted);
            }
        } catch (Throwable ignored) {
            // Appender内で例外を投げるとサーバーログ機構を壊すため握りつぶす
        }
    }

    private String formatLine(LogEvent event) {
        String level = event.getLevel() != null ? event.getLevel().name() : "INFO";
        String msg = event.getMessage() != null ? event.getMessage().getFormattedMessage() : "";
        if (msg == null) msg = "";
        // ANSIカラーコードを除去
        msg = msg.replaceAll("\u001B\\[[;\\d]*m", "");
        return "[" + level + "] " + msg;
    }
}
