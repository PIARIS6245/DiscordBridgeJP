package jp.piaris.discordbridgejp.color;

import jp.piaris.discordbridgejp.DiscordBridgeJP;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import java.awt.Color;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 連携済みプレイヤーのDiscord最上位ロール色を取得・キャッシュする。
 * retrieveMemberById のREST取得を使うため GUILD_MEMBERS 特権インテントは不要。
 */
public class RoleColorManager {

    private final DiscordBridgeJP plugin;

    private record CachedColor(Integer rgb, long expiresAt) {
    }

    private final Map<UUID, CachedColor> cache = new ConcurrentHashMap<>();

    public RoleColorManager(DiscordBridgeJP plugin) {
        this.plugin = plugin;
    }

    /**
     * 連携済みかつnamecolor有効なプレイヤーのロール色(RGB)を返す。
     * キャッシュにあればそれを返し、無ければ非同期で取得を開始して今回はemptyを返す。
     */
    public Optional<Integer> getCachedColor(UUID uuid) {
        CachedColor cached = cache.get(uuid);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            return Optional.ofNullable(cached.rgb);
        }
        refreshAsync(uuid);
        return cached != null ? Optional.ofNullable(cached.rgb) : Optional.empty();
    }

    /** 非同期にロール色を取得してキャッシュ更新。 */
    public void refreshAsync(UUID uuid) {
        refreshAsync(uuid, null);
    }

    /**
     * 非同期にロール色を取得してキャッシュ更新し、完了時(成功/失敗問わず)に onComplete を呼ぶ。
     * onComplete はメインスレッドで呼ばれる(呼び出し側でNameColorManager.applyを安全に呼べる)。
     */
    public void refreshAsync(UUID uuid, Runnable onComplete) {
        if (!plugin.getLinkDatabase().isNameColorEnabled(uuid)) {
            putCache(uuid, null);
            runOnMain(onComplete);
            return;
        }
        Optional<String> discordId = plugin.getLinkDatabase().getDiscordId(uuid);
        if (discordId.isEmpty()) {
            putCache(uuid, null);
            runOnMain(onComplete);
            return;
        }
        Guild guild = plugin.getBotManager().getGuild();
        if (guild == null) {
            runOnMain(onComplete);
            return;
        }
        try {
            guild.retrieveMemberById(discordId.get()).queue(
                    member -> {
                        putCache(uuid, extractColor(member));
                        runOnMain(onComplete);
                    },
                    err -> {
                        putCache(uuid, null);
                        runOnMain(onComplete);
                    });
        } catch (Exception e) {
            runOnMain(onComplete);
        }
    }

    private void runOnMain(Runnable onComplete) {
        if (onComplete == null) return;
        org.bukkit.Bukkit.getScheduler().runTask(plugin, onComplete);
    }

    private Integer extractColor(Member member) {
        if (member == null) return null;
        Color color = member.getColor();
        return color == null ? null : color.getRGB() & 0xFFFFFF;
    }

    private void putCache(UUID uuid, Integer rgb) {
        long ttl = plugin.getConfigManager().getNameColorCacheSeconds() * 1000L;
        cache.put(uuid, new CachedColor(rgb, System.currentTimeMillis() + ttl));
    }

    public void invalidate(UUID uuid) {
        cache.remove(uuid);
    }
}
