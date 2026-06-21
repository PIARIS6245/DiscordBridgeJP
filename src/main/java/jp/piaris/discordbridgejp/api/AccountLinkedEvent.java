package jp.piaris.discordbridgejp.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Discordアカウントの連携が完了したときに発火する(キャンセル不可)。
 */
public class AccountLinkedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID minecraftUuid;
    private final String discordId;

    public AccountLinkedEvent(UUID minecraftUuid, String discordId) {
        super(false); // メインスレッド(同期)から発火するため false
        this.minecraftUuid = minecraftUuid;
        this.discordId = discordId;
    }

    public UUID getMinecraftUuid() {
        return minecraftUuid;
    }

    public String getDiscordId() {
        return discordId;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
