package jp.piaris.discordbridgejp.link;

import jp.piaris.discordbridgejp.DiscordBridgeJP;
import jp.piaris.discordbridgejp.api.AccountLinkedEvent;
import org.bukkit.Bukkit;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.UUID;

/**
 * 連携コードの発行・照合の共通ロジック。
 * コードは8文字の英大文字+数字。Discord側では "!" を付けた形(!XXXXXXXX)で扱う。
 */
public class LinkManager {

    // 視認性のため紛らわしい文字(0/O, 1/I/L)を除外した英大文字+数字
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** verifyAndLinkの結果。INVALID_CODE/LIMIT_REACHEDでDiscord側の応答メッセージを出し分ける。 */
    public enum LinkStatus { SUCCESS, INVALID_CODE, LIMIT_REACHED }

    public record LinkResult(LinkStatus status, UUID uuid) {
        public static LinkResult success(UUID uuid) {
            return new LinkResult(LinkStatus.SUCCESS, uuid);
        }
        public static LinkResult invalid() {
            return new LinkResult(LinkStatus.INVALID_CODE, null);
        }
        public static LinkResult limitReached() {
            return new LinkResult(LinkStatus.LIMIT_REACHED, null);
        }
    }

    private final DiscordBridgeJP plugin;
    private final LinkDatabase db;

    public LinkManager(DiscordBridgeJP plugin, LinkDatabase db) {
        this.plugin = plugin;
        this.db = db;
    }

    /**
     * UUIDに対する連携コードを取得する。未失効のものがあれば再利用、なければ新規発行。
     * 返り値は "!" なしの本体8文字。
     */
    public String issueCode(UUID uuid, boolean forced) {
        Optional<String> existing = db.getActiveCode(uuid);
        if (existing.isPresent()) {
            return existing.get();
        }
        String code = generateCode();
        long expires = System.currentTimeMillis()
                + plugin.getConfigManager().getCodeExpirySeconds() * 1000L;
        db.saveCode(code, uuid, expires, forced);
        return code;
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * 受け取ったコード文字列("!XXXXXXXX" もしくは "XXXXXXXX")を照合し連携する。
     * multi-account.enabled が false の場合は1Discord垢につき1MC垢まで(従来通り)。
     * trueの場合は multi-account.max-per-discord 件まで許容する。
     */
    public LinkResult verifyAndLink(String rawCode, String discordId) {
        if (rawCode == null) return LinkResult.invalid();
        String code = rawCode.trim().toUpperCase();
        if (code.startsWith("!")) {
            code = code.substring(1);
        }
        if (!code.matches("[A-Z0-9]{8}")) {
            return LinkResult.invalid();
        }
        Optional<UUID> uuidOpt = db.consumeCode(code);
        if (uuidOpt.isEmpty()) {
            return LinkResult.invalid();
        }
        UUID uuid = uuidOpt.get();

        boolean multi = plugin.getConfigManager().isMultiAccountEnabled();
        int max = multi ? plugin.getConfigManager().getMultiAccountMax() : 1;
        int current = db.countLinksForDiscord(discordId);
        if (current >= max) {
            return LinkResult.limitReached();
        }

        db.addLink(uuid, discordId);

        // 連携完了イベントをメインスレッドで発火
        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.getPluginManager().callEvent(new AccountLinkedEvent(uuid, discordId)));
        return LinkResult.success(uuid);
    }

    public boolean isLinked(UUID uuid) {
        return db.isLinked(uuid);
    }

    public void unlink(UUID uuid) {
        db.removeLink(uuid);
    }
}
