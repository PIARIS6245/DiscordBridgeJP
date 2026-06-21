package jp.piaris.discordbridgejp;

import jp.piaris.discordbridgejp.ban.AdvancedBanListener;
import jp.piaris.discordbridgejp.ban.BanSyncManager;
import jp.piaris.discordbridgejp.ban.DiscordBanListener;
import jp.piaris.discordbridgejp.ban.MinecraftBanListener;
import jp.piaris.discordbridgejp.bot.BotManager;
import jp.piaris.discordbridgejp.chat.DiscordListener;
import jp.piaris.discordbridgejp.chat.MinecraftChatListener;
import jp.piaris.discordbridgejp.chat.StickyMessageManager;
import jp.piaris.discordbridgejp.chat.WebhookManager;
import jp.piaris.discordbridgejp.color.NameColorManager;
import jp.piaris.discordbridgejp.color.RoleColorManager;
import jp.piaris.discordbridgejp.command.MainCommand;
import jp.piaris.discordbridgejp.config.ConfigManager;
import jp.piaris.discordbridgejp.connection.PlayerConnectionListener;
import jp.piaris.discordbridgejp.connection.SilentLoginManager;
import jp.piaris.discordbridgejp.console.CommandLogListener;
import jp.piaris.discordbridgejp.console.ConsoleRelayManager;
import jp.piaris.discordbridgejp.hook.LuckPermsHook;
import jp.piaris.discordbridgejp.lang.LanguageManager;
import jp.piaris.discordbridgejp.lang.VanillaLangTranslator;
import jp.piaris.discordbridgejp.link.JoinBlockListener;
import jp.piaris.discordbridgejp.link.LinkDatabase;
import jp.piaris.discordbridgejp.link.LinkManager;
import jp.piaris.discordbridgejp.maintenance.MaintenanceManager;
import jp.piaris.discordbridgejp.message.MessagesManager;
import jp.piaris.discordbridgejp.notify.AdvancementListener;
import jp.piaris.discordbridgejp.notify.DeathListener;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;

/**
 * DiscordBridgeJP プラグイン本体。
 * 各マネージャの初期化・破棄と、他プラグイン向けの公開APIを提供する。
 */
public class DiscordBridgeJP extends JavaPlugin {

    private static DiscordBridgeJP instance;

    private ConfigManager configManager;
    private MessagesManager messagesManager;
    private LanguageManager languageManager;
    private VanillaLangTranslator vanillaTranslator;
    private BotManager botManager;
    private LinkDatabase linkDatabase;
    private LinkManager linkManager;
    private SilentLoginManager silentLoginManager;
    private MaintenanceManager maintenanceManager;
    private RoleColorManager roleColorManager;
    private NameColorManager nameColorManager;
    private WebhookManager webhookManager;
    private StickyMessageManager stickyMessageManager;
    private ConsoleRelayManager consoleRelayManager;
    private BanSyncManager banSyncManager;
    private LuckPermsHook luckPermsHook;

    public static DiscordBridgeJP getPlugin() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // --- 設定・言語 ---
        configManager = new ConfigManager(this);
        configManager.mergeDefaults(); // 既存config.ymlに新しい設定項目を自動補完
        messagesManager = new MessagesManager(this);
        messagesManager.load();
        languageManager = new LanguageManager(this);
        languageManager.saveDefaults();
        languageManager.reload();

        vanillaTranslator = new VanillaLangTranslator(this);
        vanillaTranslator.register();

        // --- LuckPerms(任意) ---
        if (LuckPermsHook.isAvailable()) {
            luckPermsHook = new LuckPermsHook(this);
            if (!luckPermsHook.init()) {
                luckPermsHook = null;
            } else {
                getLogger().info("LuckPerms連携を有効化しました。");
            }
        }

        // --- DB・各マネージャ ---
        linkDatabase = new LinkDatabase(this);
        linkDatabase.connect();
        linkDatabase.purgeExpiredCodes();

        linkManager = new LinkManager(this, linkDatabase);
        silentLoginManager = new SilentLoginManager(this, linkDatabase);
        maintenanceManager = new MaintenanceManager(this);
        roleColorManager = new RoleColorManager(this);
        nameColorManager = new NameColorManager(this);
        webhookManager = new WebhookManager(this);
        stickyMessageManager = new StickyMessageManager(this);
        consoleRelayManager = new ConsoleRelayManager(this);
        banSyncManager = new BanSyncManager(this);

        // --- Bot起動(JDAリスナー登録 → start) ---
        botManager = new BotManager(this);
        botManager.addListener(new DiscordListener(this));
        botManager.addListener(new DiscordBanListener(this));
        boolean botStarted = botManager.start();

        // --- Bukkitリスナー登録 ---
        registerListeners();

        // --- コマンド ---
        MainCommand mainCommand = new MainCommand(this);
        if (getCommand("discordbridgejp") != null) {
            getCommand("discordbridgejp").setExecutor(mainCommand);
            getCommand("discordbridgejp").setTabCompleter(mainCommand);
        }

        // --- Bot接続後の準備(Webhook/コンソール中継/起動通知) ---
        if (botStarted) {
            webhookManager.prepare();
            consoleRelayManager.start();
            sendServerNotification("startup", false);
        }

        // --- ロール色の定期リフレッシュ(ネームタグ/タブリスト) ---
        long interval = Math.max(60L, configManager.getNameColorCacheSeconds() * 20L);
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (botManager.isConnected()) {
                nameColorManager.applyAll();
            }
        }, interval, interval);

        getLogger().info("DiscordBridgeJP を有効化しました。");
    }

    private void registerListeners() {
        var pm = getServer().getPluginManager();
        pm.registerEvents(new MinecraftChatListener(this), this);
        pm.registerEvents(new PlayerConnectionListener(this), this);
        pm.registerEvents(new JoinBlockListener(this), this);
        pm.registerEvents(new DeathListener(this), this);
        pm.registerEvents(new AdvancementListener(this), this);
        pm.registerEvents(new CommandLogListener(this), this);

        // BAN: AdvancedBan導入時はそちら、未導入時はバニラフォールバック
        if (Bukkit.getPluginManager().isPluginEnabled("AdvancedBan")) {
            try {
                pm.registerEvents(new AdvancedBanListener(this), this);
                getLogger().info("AdvancedBan連携(BAN同期)を有効化しました。");
            } catch (Throwable t) {
                getLogger().warning("AdvancedBanリスナー登録に失敗、バニラ検知に切替: " + t.getMessage());
                pm.registerEvents(new MinecraftBanListener(this), this);
            }
        } else {
            pm.registerEvents(new MinecraftBanListener(this), this);
        }
    }

    @Override
    public void onDisable() {
        if (nameColorManager != null) nameColorManager.cleanup();
        // サーバー停止通知(送信完了を待ってからBotを止める)
        if (botManager != null && botManager.isConnected()) {
            sendServerNotification("shutdown", true);
        }
        if (consoleRelayManager != null) consoleRelayManager.stop();
        if (webhookManager != null) webhookManager.shutdown();
        if (vanillaTranslator != null) vanillaTranslator.unregister();
        if (botManager != null) botManager.stop();
        if (linkDatabase != null) linkDatabase.close();
        getLogger().info("DiscordBridgeJP を無効化しました。");
    }

    /**
     * /discordbridgejp reload の本体。config/lang再読込、Bot再起動、各種準備のやり直し。
     * @return Botが正常に起動したか
     */
    public boolean reloadAll() {
        consoleRelayManager.stop();
        webhookManager.shutdown(); // 古いJDA接続に紐づくWebhookクライアントを破棄(再生成させる)
        configManager.reload();
        messagesManager.reload();
        messagesManager.mergeDefaults();
        languageManager.reload();
        vanillaTranslator.register();

        boolean botStarted = botManager.start();
        if (botStarted) {
            webhookManager.prepare();
            consoleRelayManager.start();
        }
        return botStarted;
    }

    /** サーバー起動/停止通知を messages.yml の設定に従って送る。 */
    private void sendServerNotification(String key, boolean blocking) {
        if (!messagesManager.isServerEnabled(key)) return;
        String text = messagesManager.getServerMessage(key);
        if (text == null || text.isBlank()) return;
        boolean embed = messagesManager.isServerEmbed(key);
        int def = key.equals("shutdown") ? 0xFF5555 : 0x55FF55;
        int color = messagesManager.getServerColor(key, def);
        botManager.sendServerNotification(embed, color, text, blocking);
    }

    // ===================================================================
    // 公開API(他プラグイン向け。SuperVanishJP互換シグネチャを含む)
    // ===================================================================

    /** 偽の参加メッセージ等をDiscordへ送る(SuperVanishJP互換)。messageが空ならデフォルト通知。 */
    public void sendJoinMessage(Player player, String message) {
        if (!botManager.isConnected()) return;
        if (silentLoginManager.isDiscordJoinSilent(player)) return;
        String text = (message == null || message.isBlank())
                ? jp.piaris.discordbridgejp.lang.Lang.discord("discord.notify.join",
                        java.util.Map.of("player", player.getName()))
                : message;
        botManager.sendToChatChannel(text);
    }

    /** 偽の退出メッセージ等をDiscordへ送る(SuperVanishJP互換)。 */
    public void sendLeaveMessage(Player player, String message) {
        if (!botManager.isConnected()) return;
        if (silentLoginManager.isDiscordQuitSilent(player)) return;
        String text = (message == null || message.isBlank())
                ? jp.piaris.discordbridgejp.lang.Lang.discord("discord.notify.leave",
                        java.util.Map.of("player", player.getName()))
                : message;
        botManager.sendToChatChannel(text);
    }

    public net.dv8tion.jda.api.JDA getJda() {
        return botManager.getJda();
    }

    public Optional<String> getLinkedDiscordId(UUID uuid) {
        return linkDatabase.getDiscordId(uuid);
    }

    public Optional<UUID> getLinkedMinecraftUuid(String discordId) {
        return linkDatabase.getMinecraftUuid(discordId);
    }

    public boolean isSilent(Player player) {
        return silentLoginManager.isFullSilent(player.getUniqueId());
    }

    // ===================================================================
    // 各マネージャ getter
    // ===================================================================
    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessagesManager getMessagesManager() {
        return messagesManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public VanillaLangTranslator getVanillaLangTranslator() {
        return vanillaTranslator;
    }

    public BotManager getBotManager() {
        return botManager;
    }

    public LinkDatabase getLinkDatabase() {
        return linkDatabase;
    }

    public LinkManager getLinkManager() {
        return linkManager;
    }

    public SilentLoginManager getSilentLoginManager() {
        return silentLoginManager;
    }

    public MaintenanceManager getMaintenanceManager() {
        return maintenanceManager;
    }

    public RoleColorManager getRoleColorManager() {
        return roleColorManager;
    }

    public NameColorManager getNameColorManager() {
        return nameColorManager;
    }

    public WebhookManager getWebhookManager() {
        return webhookManager;
    }

    public StickyMessageManager getStickyMessageManager() {
        return stickyMessageManager;
    }

    public BanSyncManager getBanSyncManager() {
        return banSyncManager;
    }

    public ConsoleRelayManager getConsoleRelayManager() {
        return consoleRelayManager;
    }

    public LuckPermsHook getLuckPermsHook() {
        return luckPermsHook;
    }
}
