package com.soulsvault.network;

import com.soulsvault.core.ItemKey;
import com.soulsvault.world.inventory.CraftingGridPlanner;
import com.soulsvault.world.inventory.SoulsVaultMenu;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Client-to-Server request from a recipe viewer (JEI / REI / EMI) to fill the Casket's 3x3 crafting grid.
 * <p>
 * Carries, per grid slot, the candidate items the client resolved via {@link CraftingGridPlanner} ({@code null}
 * for slots without an ingredient). The server re-plans against its own inventory and Vault state before moving
 * anything, so a stale or modified client can never conjure items.
 */
public final class PacketC2SFillCraftingGrid {
    public static final int MAX_REQUESTED_CRAFTS = 64;

    private final List<List<ItemKey>> slotCandidates;
    private final int requestedCrafts;

    public PacketC2SFillCraftingGrid(List<List<ItemKey>> slotCandidates, int requestedCrafts) {
        this.slotCandidates = slotCandidates;
        this.requestedCrafts = Mth.clamp(requestedCrafts, 1, MAX_REQUESTED_CRAFTS);
    }

    public void encode(FriendlyByteBuf buf) {
        for (int slot = 0; slot < CraftingGridPlanner.GRID_SIZE; slot++) {
            @Nullable List<ItemKey> candidates = slot < this.slotCandidates.size() ? this.slotCandidates.get(slot) : null;
            int size = candidates == null ? 0 : Math.min(candidates.size(), CraftingGridPlanner.MAX_CANDIDATES_PER_SLOT);
            buf.writeVarInt(size);
            for (int i = 0; i < size; i++) {
                candidates.get(i).toNetwork(buf);
            }
        }
        buf.writeVarInt(this.requestedCrafts);
    }

    public static PacketC2SFillCraftingGrid decode(FriendlyByteBuf buf) {
        List<List<ItemKey>> slotCandidates = new ArrayList<>(CraftingGridPlanner.GRID_SIZE);
        for (int slot = 0; slot < CraftingGridPlanner.GRID_SIZE; slot++) {
            int size = buf.readVarInt();
            if (size < 0 || size > CraftingGridPlanner.MAX_CANDIDATES_PER_SLOT) {
                throw new DecoderException("Too many crafting grid candidates: " + size);
            }
            if (size == 0) {
                slotCandidates.add(null);
                continue;
            }
            List<ItemKey> candidates = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                ItemKey key = ItemKey.fromNetwork(buf);
                if (!key.isEmpty()) {
                    candidates.add(key);
                }
            }
            slotCandidates.add(candidates);
        }
        int requestedCrafts = buf.readVarInt();
        return new PacketC2SFillCraftingGrid(slotCandidates, requestedCrafts);
    }

    public void handleServer(ServerPlayer player) {
        if (player == null || player.isSpectator()) {
            return;
        }
        if (player.containerMenu instanceof SoulsVaultMenu vaultMenu) {
            vaultMenu.fillCraftingGridForRecipe(player, this.slotCandidates, this.requestedCrafts);
        }
    }
}
