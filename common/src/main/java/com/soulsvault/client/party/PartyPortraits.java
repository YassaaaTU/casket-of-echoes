package com.soulsvault.client.party;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.soulsvault.Constants;
import com.soulsvault.party.PartyPresence;
import com.soulsvault.party.PartySnapshot;
import com.soulsvault.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Static full-body party HUD portraits: a client-only stand-in player per teammate, wearing their skin (from the
 * tab list), armor and held items, drawn in a fixed pose. Equipment comes from the real player when they are
 * loaded nearby, else from the server's party equipment updates, so modded armor and weapons render through their
 * normal renderers. The stand-in's capabilities are dropped so combat mods (Epic Fight) don't swap in animated
 * models, and any render failure turns portraits back into faces for the session.
 */
public final class PartyPortraits {
    private static final Map<UUID, RemotePlayer> STAND_INS = new HashMap<>();
    private static final float BODY_YAW = 200.0F;
    /** Keeps the stand-in far from the camera so vanilla never draws its name tag. */
    private static final double FAR_AWAY = 1.0E6D;
    private static ClientLevel standInLevel;
    private static boolean failed;

    private PartyPortraits() {
    }

    public static boolean isAvailable() {
        return !failed;
    }

    /**
     * Draws a teammate's full-body portrait with its feet at {@code feetY}.
     *
     * @return {@code false} when the portrait could not be drawn (draw a face instead)
     */
    public static boolean render(GuiGraphics graphics, PartySnapshot.Member member, int centerX, int feetY, int scale) {
        Minecraft mc = Minecraft.getInstance();
        if (failed || mc.level == null) {
            return false;
        }
        RemotePlayer standIn;
        try {
            standIn = standIn(mc, member);
            dress(standIn, mc.level.getPlayerByUUID(member.id()), ClientPartyCache.getEquipment(member.id()));
        } catch (RuntimeException e) {
            disable(e);
            return false;
        }

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(centerX, feetY, 50.0F);
            graphics.pose().mulPoseMatrix(new Matrix4f().scaling(scale, scale, -scale));
            graphics.pose().mulPose(pose);
            Lighting.setupForEntityInInventory();
            camera.conjugate();
            dispatcher.overrideCameraOrientation(camera);
            dispatcher.setRenderShadow(false);
            RenderSystem.runAsFancy(() -> dispatcher.render(standIn, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F,
                    graphics.pose(), graphics.bufferSource(), 15728880));
            graphics.flush();
            return true;
        } catch (RuntimeException e) {
            disable(e);
            return false;
        } finally {
            dispatcher.setRenderShadow(true);
            graphics.pose().popPose();
            Lighting.setupFor3DItems();
        }
    }

    private static RemotePlayer standIn(Minecraft mc, PartySnapshot.Member member) {
        if (mc.level != standInLevel) {
            STAND_INS.clear();
            standInLevel = mc.level;
        }
        return STAND_INS.computeIfAbsent(member.id(), id -> {
            RemotePlayer player = new RemotePlayer(mc.level, new GameProfile(id, member.name()));
            Services.PLATFORM.detachCapabilities(player);
            player.setPos(FAR_AWAY, 0.0D, FAR_AWAY);
            player.yBodyRot = BODY_YAW;
            player.yBodyRotO = BODY_YAW;
            player.setYRot(BODY_YAW);
            player.yRotO = BODY_YAW;
            player.yHeadRot = BODY_YAW;
            player.yHeadRotO = BODY_YAW;
            player.setXRot(0.0F);
            return player;
        });
    }

    private static void dress(RemotePlayer standIn, Player live, List<ItemStack> reported) {
        for (int i = 0; i < PartyPresence.SLOTS.length; i++) {
            EquipmentSlot slot = PartyPresence.SLOTS[i];
            ItemStack wanted = live != null ? live.getItemBySlot(slot) : (i < reported.size() ? reported.get(i) : ItemStack.EMPTY);
            if (!ItemStack.matches(standIn.getItemBySlot(slot), wanted)) {
                standIn.setItemSlot(slot, wanted.copy());
            }
        }
    }

    private static void disable(RuntimeException e) {
        failed = true;
        STAND_INS.clear();
        Constants.LOG.error("Party HUD model portraits failed to render; showing faces for this session", e);
    }
}
