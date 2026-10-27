package com.soulsvault.network;

import com.soulsvault.api.SoulsVaultApi;
import com.soulsvault.core.BuildEcho;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.platform.Services;
import com.soulsvault.world.inventory.SoulsVaultMenu;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-to-Server Build Echo edit from the Echo options popover: rename, and which extra slot groups (e.g. the
 * hotbar) the Echo restores.
 */
public final class PacketC2SEchoEdit {
    private static final int MAX_TOGGLES = 64;
    private static final int MAX_TYPE_ID_LENGTH = 128;

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
            buf.writeUtf(entry.getKey(), MAX_TYPE_ID_LENGTH);
            buf.writeBoolean(entry.getValue());
        }
    }

    public static PacketC2SEchoEdit decode(FriendlyByteBuf buf) {
        int index = buf.readVarInt();
        String name = buf.readUtf(BuildEcho.MAX_NAME_LENGTH * 4);
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_TOGGLES) {
            throw new DecoderException("Too many Build Echo toggles: " + count);
        }
        Map<String, Boolean> toggles = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            toggles.put(buf.readUtf(MAX_TYPE_ID_LENGTH), buf.readBoolean());
        }
        return new PacketC2SEchoEdit(index, name, toggles);
    }

    public void handleServer(ServerPlayer player) {
        if (player == null || player.isSpectator() || !(player.containerMenu instanceof SoulsVaultMenu)
                || !RequestThrottle.tryAcquire(player, RequestThrottle.Kind.ECHO_UPDATE)) {
            return;
        }
        PlayerVaultData vault = Services.PLATFORM.getPlayerVaultData(player);
        if (this.index < 0 || this.index >= vault.getEchoSlots()) {
            return;
        }
        vault.renameBuildEcho(this.index, this.name);
        vault.setBuildEchoTypeToggles(this.index, knownToggles());
        Services.PLATFORM.syncFullVaultToClient(player, vault);
    }

    /**
     * Only toggles for registered Build Echo slot types are saved.
     */
    private Map<String, Boolean> knownToggles() {
        Map<String, Boolean> known = new LinkedHashMap<>();
        for (Map.Entry<String, Boolean> entry : this.toggles.entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
            if (id != null && SoulsVaultApi.getEchoSlotType(id) != null) {
                known.put(id.toString(), entry.getValue());
            }
        }
        return known;
    }
}
