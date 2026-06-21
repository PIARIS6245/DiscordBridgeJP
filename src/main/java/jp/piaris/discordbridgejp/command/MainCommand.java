package jp.piaris.discordbridgejp.command;

import jp.piaris.discordbridgejp.DiscordBridgeJP;
import jp.piaris.discordbridgejp.lang.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /discordbridgejp の全サブコマンド処理とタブ補完。
 */
public class MainCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "discordbridgejp.admin";

    private final DiscordBridgeJP plugin;

    public MainCommand(DiscordBridgeJP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Lang.mc("mc.command.usage"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            case "language" -> handleLanguage(sender, args);
            case "namecolor" -> handleNameColor(sender, args);
            case "silentlogin" -> handleSilentLogin(sender, args);
            case "maintenance" -> handleMaintenance(sender, args);
            case "link" -> handleLink(sender);
            case "unlink" -> handleUnlink(sender);
            default -> sender.sendMessage(Lang.mc("mc.command.usage"));
        }
        return true;
    }

    private boolean requireAdmin(CommandSender sender) {
        if (!sender.hasPermission(ADMIN)) {
            sender.sendMessage(Lang.mc("mc.command.no-permission"));
            return false;
        }
        return true;
    }

    // ===== reload =====
    private void handleReload(CommandSender sender) {
        if (!requireAdmin(sender)) return;
        boolean botOk = plugin.reloadAll();
        if (botOk) {
            sender.sendMessage(Lang.mc("mc.command.reload-success"));
        } else {
            sender.sendMessage(Lang.mc("mc.command.reload-bot-failed"));
        }
    }

    // ===== status =====
    private void handleStatus(CommandSender sender) {
        if (!requireAdmin(sender)) return;
        if (plugin.getBotManager().isConnected()) {
            String tag = plugin.getBotManager().getJda().getSelfUser().getName();
            Map<String, String> ph = new HashMap<>();
            ph.put("tag", tag);
            sender.sendMessage(Lang.mc("mc.command.status-connected", ph));
        } else {
            sender.sendMessage(Lang.mc("mc.command.status-disconnected"));
        }
    }

    // ===== language =====
    private void handleLanguage(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 2) {
            sender.sendMessage(Lang.mc("mc.command.language-unknown",
                    Map.of("language", "", "available",
                            String.join(", ", plugin.getLanguageManager().getAvailableLanguages()))));
            return;
        }
        String code = args[1].toLowerCase();
        if (plugin.getLanguageManager().setLanguage(code)) {
            sender.sendMessage(Lang.mc("mc.command.language-changed", Map.of("language", code)));
        } else {
            sender.sendMessage(Lang.mc("mc.command.language-unknown",
                    Map.of("language", code, "available",
                            String.join(", ", plugin.getLanguageManager().getAvailableLanguages()))));
        }
    }

    // ===== namecolor =====
    private void handleNameColor(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 3 || !(args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) {
            sender.sendMessage(Lang.mc("mc.command.namecolor-usage"));
            return;
        }
        boolean enable = args[1].equalsIgnoreCase("on");
        OfflinePlayer target = resolvePlayer(args[2]);
        if (target == null) {
            sender.sendMessage(Lang.mc("mc.command.player-not-found", Map.of("player", args[2])));
            return;
        }
        plugin.getLinkDatabase().setNameColorEnabled(target.getUniqueId(), enable);
        plugin.getRoleColorManager().invalidate(target.getUniqueId());
        if (target.isOnline() && target.getPlayer() != null) {
            final org.bukkit.entity.Player tp = target.getPlayer();
            plugin.getRoleColorManager().refreshAsync(target.getUniqueId(),
                    () -> plugin.getNameColorManager().apply(tp));
        }
        Map<String, String> ph = Map.of("player", args[2]);
        sender.sendMessage(Lang.mc(enable ? "mc.command.namecolor-on" : "mc.command.namecolor-off", ph));
    }

    // ===== silentlogin =====
    private void handleSilentLogin(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 2) {
            sender.sendMessage(Lang.mc("mc.command.silentlogin-usage"));
            return;
        }
        String sub = args[1].toLowerCase();
        if (sub.equals("list")) {
            List<UUID> list = plugin.getSilentLoginManager().list();
            if (list.isEmpty()) {
                sender.sendMessage(Lang.mc("mc.command.silentlogin-list-empty"));
                return;
            }
            sender.sendMessage(Lang.mc("mc.command.silentlogin-list-header",
                    Map.of("count", String.valueOf(list.size()))));
            for (UUID u : list) {
                String name = Bukkit.getOfflinePlayer(u).getName();
                sender.sendMessage(Component.text(" - " + (name != null ? name : u.toString())));
            }
            return;
        }
        if (args.length < 3 || !(sub.equals("add") || sub.equals("remove"))) {
            sender.sendMessage(Lang.mc("mc.command.silentlogin-usage"));
            return;
        }
        OfflinePlayer target = resolvePlayer(args[2]);
        if (target == null) {
            sender.sendMessage(Lang.mc("mc.command.player-not-found", Map.of("player", args[2])));
            return;
        }
        if (sub.equals("add")) {
            plugin.getSilentLoginManager().add(target.getUniqueId());
            sender.sendMessage(Lang.mc("mc.command.silentlogin-added", Map.of("player", args[2])));
        } else {
            plugin.getSilentLoginManager().remove(target.getUniqueId());
            sender.sendMessage(Lang.mc("mc.command.silentlogin-removed", Map.of("player", args[2])));
        }
    }

    // ===== maintenance =====
    private void handleMaintenance(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 2 || !(args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) {
            sender.sendMessage(Lang.mc("mc.command.maintenance-usage"));
            return;
        }
        if (args[1].equalsIgnoreCase("on")) {
            String reason = args.length > 2
                    ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                    : "";
            plugin.getMaintenanceManager().enable(reason);
            if (reason.isBlank()) {
                sender.sendMessage(Lang.mc("mc.command.maintenance-on"));
            } else {
                sender.sendMessage(Lang.mc("mc.command.maintenance-on-reason", Map.of("reason", reason)));
            }
        } else {
            plugin.getMaintenanceManager().disable();
            sender.sendMessage(Lang.mc("mc.command.maintenance-off"));
        }
    }

    // ===== link =====
    private void handleLink(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.mc("mc.command.player-only"));
            return;
        }
        if (!player.hasPermission("discordbridgejp.link")) {
            player.sendMessage(Lang.mc("mc.command.no-permission"));
            return;
        }
        UUID uuid = player.getUniqueId();
        if (plugin.getLinkManager().isLinked(uuid)) {
            player.sendMessage(Lang.mc("mc.link.already-linked"));
            return;
        }
        String code = plugin.getLinkManager().issueCode(uuid, false);
        String codeText = "!" + code;
        player.sendMessage(Lang.mc("mc.link.code-issued"));
        Component codeComponent = Component.text(codeText)
                .color(NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.copyToClipboard(codeText))
                .hoverEvent(HoverEvent.showText(Lang.mc("mc.link.code-hint")));
        player.sendMessage(codeComponent);
    }

    // ===== unlink =====
    private void handleUnlink(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Lang.mc("mc.command.player-only"));
            return;
        }
        if (!player.hasPermission("discordbridgejp.unlink")) {
            player.sendMessage(Lang.mc("mc.command.no-permission"));
            return;
        }
        UUID uuid = player.getUniqueId();
        if (!plugin.getLinkManager().isLinked(uuid)) {
            player.sendMessage(Lang.mc("mc.link.not-linked"));
            return;
        }

        boolean isAdmin = player.hasPermission(ADMIN);
        boolean requireLink = plugin.getConfigManager().isRequireLinkForNewPlayers();
        String mode = plugin.getConfigManager().getUnlinkMode();

        if (requireLink && !isAdmin) {
            if (mode.equalsIgnoreCase("block")) {
                player.sendMessage(Lang.mc("mc.link.unlink-blocked"));
                return;
            }
            // kickモード: 解除して即KICK
            plugin.getLinkManager().unlink(uuid);
            plugin.getRoleColorManager().invalidate(uuid);
            player.kick(Lang.mc("mc.link.unlink-success"));
            return;
        }

        plugin.getLinkManager().unlink(uuid);
        plugin.getRoleColorManager().invalidate(uuid);
        plugin.getNameColorManager().apply(player); // 連携解除のため即座に無着色化
        player.sendMessage(Lang.mc("mc.link.unlink-success"));
    }

    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore() || offline.isOnline()) {
            return offline;
        }
        return null;
    }

    // ===== タブ補完 =====
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (sender.hasPermission(ADMIN)) {
                subs.addAll(Arrays.asList("reload", "status", "language",
                        "namecolor", "silentlogin", "maintenance"));
            }
            if (sender.hasPermission("discordbridgejp.link")) subs.add("link");
            if (sender.hasPermission("discordbridgejp.unlink")) subs.add("unlink");
            return filter(subs, args[0]);
        }

        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            switch (sub) {
                case "language" -> {
                    return filter(plugin.getLanguageManager().getAvailableLanguages(), args[1]);
                }
                case "namecolor" -> {
                    return filter(Arrays.asList("on", "off"), args[1]);
                }
                case "silentlogin" -> {
                    return filter(Arrays.asList("add", "remove", "list"), args[1]);
                }
                case "maintenance" -> {
                    return filter(Arrays.asList("on", "off"), args[1]);
                }
            }
        }
        if (args.length == 3 && (sub.equals("namecolor") || sub.equals("silentlogin"))) {
            return filter(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName).collect(Collectors.toList()), args[2]);
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String current) {
        String lower = current.toLowerCase();
        return options.stream()
                .filter(o -> o.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}
