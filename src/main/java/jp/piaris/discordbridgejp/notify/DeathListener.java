package jp.piaris.discordbridgejp.notify;

import jp.piaris.discordbridgejp.DiscordBridgeJP;
import jp.piaris.discordbridgejp.lang.VanillaLangTranslator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 死亡メッセージをDiscordへ通知(messages.yml の death 設定に従う)。
 * 言語がjaなら ja_jp.json で日本語化、失敗時は英語のまま。
 */
public class DeathListener implements Listener {

    private final DiscordBridgeJP plugin;

    public DeathListener(DiscordBridgeJP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getMessagesManager().isNotifyEnabled("death")) return;
        if (!plugin.getBotManager().isConnected()) return;

        Component deathComponent = event.deathMessage();
        if (deathComponent == null) return;

        String englishFallback = PlainTextComponentSerializer.plainText().serialize(deathComponent);
        String deathText;
        if ("ja".equalsIgnoreCase(plugin.getLanguageManager().getCurrentLanguage())) {
            deathText = VanillaLangTranslator.renderOrFallback(
                    deathComponent, Locale.JAPANESE, englishFallback);
        } else {
            deathText = englishFallback;
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("playername", event.getPlayer().getName());
        ph.put("deathmessage", deathText);

        var msg = plugin.getMessagesManager();
        boolean embed = msg.isNotifyEmbed("death");
        int color = msg.getNotifyColor("death", 0x000000);
        String text = msg.getNotifyMessage("death", ph);
        String avatar = "https://mc-heads.net/avatar/" + event.getPlayer().getUniqueId() + "/64";
        plugin.getBotManager().sendNotification(embed, color, text, embed ? avatar : null);
    }
}
