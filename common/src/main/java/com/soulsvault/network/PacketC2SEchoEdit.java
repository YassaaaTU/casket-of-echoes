package com.soulsvault.network;

import com.soulsvault.core.BuildEcho;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.platform.Services;
import com.soulsvault.world.inventory.SoulsVaultMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-to-Server Build Echo edit from the Echo options popover: rename, and which extra slot groups (e.g. the
 * hotbar) the Echo restores.
 */
public final class PacketC2SEchoEdit {
    private static final int MAX_TOGGLES = 64;

    private final int index;
    private final String name;
    private final Map<String, Boolean> toggles;

    public PacketC2SEchoEdit(int index, String name, Map<String, Boolean> toggles) {
        this.index = index;
        this.name = name == null ? "" : name;
        this.toggles = Map.copyOf(toggles);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.index);
        buf.writeUtf(this.name, BuildEcho.MAX_NAME_LENGTH * 4);
        buf.writeVarInt(this.toggles.size());
        for (Map.Entry<String, Boolean> entry : this.toggles.entrySet()) {
            buf.writeUtf(entry.getKey(), 128);
            buf.writeBoolean(entry.getValue());
        }
    }

    public static PacketC2SEchoEdit decode(FriendlyByteBuf buf) {
        int index = buf.readVarInt();
        String name = buf.readUtf(BuildEcho.MAX_NAME_LENGTH * 4);
        int count = Math.min(MAX_TOGGLES, buf.readVarInt());
        Map<String, Boolean> toggles = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            toggles.put(buf.readUtf(128), buf.readBoolean());
        }
        return new PacketC2SEchoEdit(index, name, toggles);
    }

    public void handleServer(ServerPlayer player) {
        if (player == null || player.isSpectator() || !(player.containerMenu instanceof SoulsVaultMenu)) {
            return;
        }
        PlayerVaultData vault = Services.PLATFORM.getPlayerVaultData(player);
        if (this.index < 0 || this.index >= vault.getEchoSlots()) {
            return;
        }
        vault.renameBuildEcho(this.index, this.name);
        vault.setBuildEchoTypeToggles(this.index, this.toggles);
        Services.PLATFORM.syncFullVaultToClient(player, vault);
    }
}
