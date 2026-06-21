package jp.piaris.discordbridgejp.notify;

import io.papermc.paper.advancement.AdvancementDisplay;
import jp.piaris.discordbridgejp.DiscordBridgeJP;
import jp.piaris.discordbridgejp.lang.VanillaLangTranslator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.advancement.Advancement;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 進捗達成をDiscordへ通知(messages.yml の advancement 設定に従う)。
 * チャットにアナウンスされる進捗のみ対象。進捗名はja_jp.jsonで日本語化、失敗時は英語。
 */
public class AdvancementListener implements Listener {

    private final DiscordBridgeJP plugin;

    public AdvancementListener(DiscordBridgeJP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (!plugin.getMessagesManager().isNotifyEnabled("advancement")) return;
        if (!plugin.getBotManager().isConnected()) return;

        Advancement advancement = event.getAdvancement();
        AdvancementDisplay display = advancement.getDisplay();
        if (display == null || !display.doesAnnounceToChat()) {
            return;
        }

        String advName = resolveAdvancementName(display);
        if (advName == null || advName.isBlank()) return;

        Map<String, String> ph = new HashMap<>();
        ph.put("playername", event.getPlayer().getName());
        ph.put("advancement", advName);

        var msg = plugin.getMessagesManager();
        boolean embed = msg.isNotifyEmbed("advancement");
        int color = msg.getNotifyColor("advancement", 0xFFAA00);
        String text = msg.getNotifyMessage("advancement", ph);
        String avatar = "https://mc-heads.net/avatar/" + event.getPlayer().getUniqueId() + "/64";
        plugin.getBotManager().sendNotification(embed, color, text, embed ? avatar : null);
    }

    private String resolveAdvancementName(AdvancementDisplay display) {
        Component titleComponent;
        try {
            titleComponent = display.title();
        } catch (Throwable t) {
            return "";
        }
        String fallback = PlainTextComponentSerializer.plainText().serialize(titleComponent);
        if (!"ja".equalsIgnoreCase(plugin.getLanguageManager().getCurrentLanguage())) {
            return fallback;
        }
        return VanillaLangTranslator.renderOrFallback(titleComponent, Locale.JAPANESE, fallback);
    }
}
